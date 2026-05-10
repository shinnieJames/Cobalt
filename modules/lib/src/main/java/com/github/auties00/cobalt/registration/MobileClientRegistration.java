package com.github.auties00.cobalt.registration;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.client.WhatsAppClientVerificationHandler;
import com.github.auties00.cobalt.client.WhatsAppDevicePushClient;
import com.github.auties00.cobalt.client.info.WhatsAppMobileClientInfo;
import com.github.auties00.cobalt.client.WhatsAppDeviceAttestor;
import com.github.auties00.cobalt.exception.WhatsAppRegistrationException;
import com.github.auties00.cobalt.model.business.*;
import com.github.auties00.cobalt.model.device.pairing.ClientPlatformType;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.util.DataUtils;
import com.github.auties00.curve25519.Curve25519;
import com.github.auties00.libsignal.key.SignalIdentityKeyPair;
import com.github.auties00.libsignal.key.SignalIdentityPublicKey;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.*;

/**
 * Drives the mobile phone-number registration flow against WhatsApp's
 * {@code v.whatsapp.net/v2} endpoint.
 *
 * <p>Registering a phone number with WhatsApp as a native Android or iOS
 * client involves three sequential calls against the legacy mobile
 * registration API:
 * <ol>
 *   <li>{@code /exist} asks the server whether an account already exists
 *       for the exact Signal identity/noise keys that Cobalt is about to
 *       claim. The Cobalt logic treats a {@code "reason": "incorrect"}
 *       response as confirmation that the keys are free and proceeds,
 *       otherwise it aborts.</li>
 *   <li>{@code /code} asks the server to send a verification code via the
 *       method the user chose (SMS, voice call, or WhatsApp OTP).</li>
 *   <li>{@code /register} submits the verification code the user provided
 *       and completes the claim.</li>
 * </ol>
 * The concrete subclasses {@link AndroidClientRegistration} and
 * {@link IosClientRegistration} plug in the platform-specific request
 * parameters, User-Agent, device identifier format, and HTTP headers.
 *
 * <p>All request bodies are end-to-end encrypted with AES-GCM under a
 * Curve25519 shared key derived between a freshly generated ephemeral key
 * pair and a hardcoded server registration key, and then Base64-encoded and
 * wrapped in a {@code ENC=...} form field, mirroring the scheme the native
 * mobile apps use.
 *
 * @apiNote This class has no WA Web counterpart. WA Web clients pair with
 *          an already-registered phone via the QR or link-code flow rather
 *          than driving the mobile registration API themselves, so this
 *          class exists purely to let Cobalt take the role of a native
 *          Android or iOS client.
 * @see AndroidClientRegistration
 * @see IosClientRegistration
 */
public abstract sealed class MobileClientRegistration implements AutoCloseable
        permits AndroidClientRegistration, IosClientRegistration {
    /**
     * Base URL of the mobile registration API shared by all three
     * operations ({@code /exist}, {@code /code}, {@code /register}).
     */
    public static final String MOBILE_REGISTRATION_ENDPOINT = "https://v.whatsapp.net/v2";

    /**
     * WhatsApp's hardcoded Curve25519 public key used as the peer in the
     * request-encryption ECDH, extracted from the native mobile apps.
     */
    private static final byte[] REGISTRATION_PUBLIC_KEY = HexFormat.of().parseHex("8e8c0f74c3ebc5d7a6865c6c3c843856b06121cce8ea774d22fb6f122512302d");

    /**
     * Single-byte Signal identity key type marker, URL-safe Base64 encoded,
     * sent as the {@code e_keytype} form field.
     */
    private static final String SIGNAL_PUBLIC_KEY_TYPE = Base64.getUrlEncoder().encodeToString(new byte[]{SignalIdentityPublicKey.type()});

    /**
     * Shared HTTP client used for every registration request.
     *
     * <p>Created once per registration instance and closed together with
     * the instance via {@link #close()}.
     */
    protected final HttpClient httpClient;

    /**
     * Cobalt store providing the Signal identity keys, Noise keys,
     * registration ID, FDID, device ID, phone number, and other persistent
     * credentials that the registration requests rely on.
     */
    protected final WhatsAppStore store;

    /**
     * Callback used to ask the calling code which verification method to
     * request (SMS / voice / WhatsApp OTP) and what verification code the
     * user entered.
     */
    protected final WhatsAppClientVerificationHandler.Mobile verification;

    /**
     * One-based attempt counter driven by the {@code /v2/code} retry
     * loop. Read by subtypes that embed a {@code client_metrics.attempts}
     * field into the outgoing body.
     */
    protected int attempt = 1;

    /**
     * The screen-name string most recently reported to
     * {@code /v2/client_log}. Carried into subsequent funnel events as
     * the {@code previous_screen} field so the server sees a connected
     * sequence.
     *
     * <p>Initialised to {@code "enter_number"} because the very first
     * post-phone-number funnel event in Cobalt's flow happens after the
     * phone number has already been committed to the store.
     */
    private String previousFunnelScreen = "enter_number";

    /**
     * The verification method string most recently requested via
     * {@code /v2/code} (for example {@code "sms"} or {@code "voice"}).
     * Read by {@link #sendVerificationCode()} and the 2FA handler to
     * derive the {@code current_screen} value passed to funnel events,
     * so that events fired after the code exchange correlate to the
     * same screen name the native client would have reported.
     *
     * <p>Remains {@code null} until the first call to
     * {@link #requestVerificationCode(String)}.
     */
    private String lastRequestedMethod;

    /**
     * Stable per-registration session identifier sent as the
     * {@code access_session_id} form field on every attested endpoint
     * and every funnel event. The native Android client uses a
     * 22-character base64url-encoded random 16-byte UUID. We mirror that
     * shape (no padding, URL-safe alphabet). Generated once at
     * construction and reused for the whole registration ceremony so
     * the server can correlate {@code /v2/exist}, {@code /v2/code},
     * {@code /v2/register}, {@code /v2/client_log}, etc. As one
     * session.
     */
    private final String accessSessionId = newAccessSessionId();

    /**
     * Mints a fresh {@code access_session_id} matching the 22-char
     * URL-safe base64 shape the native Android client uses.
     *
     * @return the generated session identifier
     */
    private static String newAccessSessionId() {
        var u = UUID.randomUUID();
        var bb = ByteBuffer.allocate(16);
        bb.putLong(u.getMostSignificantBits());
        bb.putLong(u.getLeastSignificantBits());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bb.array());
    }

    /**
     * Constructs a new registration for the given store and verification
     * callbacks.
     *
     * @param store the store carrying identity keys and the phone number
     * @param verification the verification handler supplying the method
     *                     and the user-entered code
     * @throws NullPointerException if either argument is {@code null}
     */
    protected MobileClientRegistration(WhatsAppStore store, WhatsAppClientVerificationHandler.Mobile verification) {
        Objects.requireNonNull(store, "store cannot be null");
        Objects.requireNonNull(verification, "verification cannot be null");
        this.store = store;
        this.verification = verification;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    /**
     * Returns the concrete registration implementation that matches the
     * platform configured on the given store, binding the attestor and
     * push client the caller attached at the builder.
     *
     * <p>The attestor is delivered as the {@link WhatsAppDeviceAttestor}
     * sealed parent, and dispatch is done by pattern-matching on the
     * permitted sub-interface so the correct concrete registration
     * receives a narrowly-typed attestor. The builder's
     * {@link com.github.auties00.cobalt.client.WhatsAppClientBuilder.Options.Mobile#deviceAttestor}
     * setter has already validated at call time that the attestor's
     * platform matches the device's platform. This factory re-checks
     * defensively and throws when the two disagree.
     *
     * <p>Passing a {@code null} attestor falls back to
     * {@link WhatsAppDeviceAttestor.Android#NONE} or
     * {@link WhatsAppDeviceAttestor.Ios#NONE} depending on the device
     * platform. Passing a {@code null} {@code pushClient} falls back to
     * {@link WhatsAppDevicePushClient#noop()}. Both are the low-trust-lane
     * defaults.
     *
     * @param store the store whose device drives the selection
     * @param verification the verification handler passed to the new
     *                     registration
     * @param attestor the device attestor, or {@code null} to fall back
     *                 to the platform's {@code NONE} attestor
     * @param pushClient the device push client, or {@code null} to fall
     *                   back to {@link WhatsAppDevicePushClient#noop()}
     * @return a concrete {@code MobileClientRegistration}
     * @throws IllegalArgumentException if the store's device platform is
     *                                  not a supported mobile platform,
     *                                  or if {@code attestor} does not
     *                                  match that platform
     */
    public static MobileClientRegistration newRegistration(
            WhatsAppStore store,
            WhatsAppClientVerificationHandler.Mobile verification,
            WhatsAppDeviceAttestor attestor,
            WhatsAppDevicePushClient pushClient) {
        var platform = store.device().platform();
        return switch (platform) {
            case ANDROID, ANDROID_BUSINESS -> {
                var androidAttestor = switch (attestor) {
                    case null -> null;
                    case WhatsAppDeviceAttestor.Android a -> a;
                    case WhatsAppDeviceAttestor.Ios ignored -> throw new IllegalArgumentException(
                            "Android device requires an Android attestor, got: " + attestor.getClass().getName());
                };
                yield new AndroidClientRegistration(store, verification, androidAttestor, pushClient);
            }
            case IOS, IOS_BUSINESS -> {
                var iosAttestor = switch (attestor) {
                    case null -> null;
                    case WhatsAppDeviceAttestor.Ios i -> i;
                    case WhatsAppDeviceAttestor.Android ignored -> throw new IllegalArgumentException(
                            "iOS device requires an iOS attestor, got: " + attestor.getClass().getName());
                };
                yield new IosClientRegistration(store, verification, iosAttestor, pushClient);
            }
            default -> throw new IllegalArgumentException("Unsupported platform: " + platform);
        };
    }

    /**
     * Returns the platform-specific form parameters that accompany a
     * {@code /code} request in addition to the shared registration
     * parameters.
     *
     * @param method the verification method chosen by the user
     *               (for example {@code "sms"}, {@code "voice"},
     *               {@code "wa_old"})
     * @return an alternating name/value array of additional parameters
     */
    protected abstract String[] getRequestVerificationCodeParameters(String method);

    /**
     * Returns the device family identifier used as the {@code fdid} form
     * field.
     *
     * <p>Android uses the lower-case UUID form, iOS uses the upper-case
     * UUID form, matching the behaviour of the respective native apps.
     *
     * @return the formatted device family identifier
     */
    protected abstract String generateFdid();

    /**
     * Returns the platform-specific device-attestation form fields that
     * are appended to <em>every</em> attested registration endpoint body
     * ({@code /exist}, {@code /code}, {@code /register}, {@code /security},
     * {@code /challenge}, and any future endpoint that uses
     * {@link #getRegistrationOptions(boolean, String...)}).
     *
     * <p>On Android this returns the Play Integrity sextuple
     * ({@code gpia}, {@code _gg}, {@code _gi}, {@code _gp},
     * {@code _ge}, {@code _ga}) produced by the configured
     * {@link WhatsAppDeviceAttestor.Android Android attestor} plus the
     * FCM {@code push_token} produced by the configured
     * {@link WhatsAppDevicePushClient}. On iOS this returns just the APNS
     * {@code push_token} produced by the configured push client. Each
     * call triggers both the attestor and the push client. Embedders
     * that talk to a remote attestation minter should cache the
     * per-session payload inside their attestor implementation to avoid
     * one round-trip per registration step.
     *
     * <p>The funnel endpoints ({@code /client_log},
     * {@code /pre_pn_client_log}) do <em>not</em> reach this method
     * because they build their own body without going through
     * {@link #getRegistrationOptions(boolean, String...)}, matching the
     * native client's {@code sendAttestationPayload=false} configuration
     * for those two paths.
     *
     * @return an alternating name/value array of attestation fields
     *         suitable for direct inclusion in the form body
     */
    protected abstract String[] attestationFields();

    /**
     * Builds an {@link HttpRequest} for the given sub-path of the mobile
     * registration endpoint with the fully-assembled body and the
     * correct set of platform-specific headers.
     *
     * <p>The body handed to the subclass is already in its final wire
     * form: the outer {@code ENC=<base64>} envelope, optionally followed
     * by {@code &H=<hex>} when an Android keystore signature has been
     * produced. Subclasses POST the body verbatim and add only the
     * headers the native client advertises for that platform, plus the
     * {@code Authorization} header when {@code authorizationHeader} is
     * non-{@code null}.
     *
     * @param path the sub-path, starting with a slash (for example
     *             {@code "/exist"})
     * @param body the fully-assembled body string to POST
     * @param authorizationHeader the value for the {@code Authorization}
     *                            request header, or {@code null} to
     *                            omit the header
     * @return a ready-to-send HTTP request
     */
    protected abstract HttpRequest createRequest(String path, String body, String authorizationHeader);

    /**
     * Signs the outer {@code ENC=<base64>} body bytes with the
     * platform-specific attestor and returns the resulting signature
     * hex and {@code Authorization}-header value.
     *
     * <p>Called by {@link #sendRequest(String, String)} on every
     * outgoing request so subclasses that have a real attestor (not the
     * empty fallback) can attach the Android Keystore signature or App
     * Attest proof. Implementations that cannot produce a signature
     * return {@link BodyAttestation#EMPTY}.
     *
     * @param encBodyBytes the UTF-8 bytes of the base64 ENC body, i.e.
     *                     the value that follows the {@code ENC=}
     *                     prefix on the wire. Never {@code null}
     * @return the hex-encoded signature and base64-encoded cert chain,
     *         or {@link BodyAttestation#EMPTY} when no attestor is
     *         configured or the attestor returned empty output
     */
    protected abstract BodyAttestation attestBody(byte[] encBodyBytes);

    /**
     * Pair of values produced by {@link #attestBody(byte[])}: the
     * platform-specific value that becomes the {@code &H=} suffix on
     * the request body, and the value that becomes the
     * {@code Authorization} request header.
     *
     * <p>The contents of {@link #bodyAttestation} differ per platform:
     * <ul>
     *   <li>On Android it is the lower-case hex of the HMAC-SHA1 over
     *       the base64 ENC body produced by the device's
     *       Keystore-backed key.</li>
     *   <li>On iOS it is a JSON envelope
     *       {@code {"assertion":"<base64 CBOR>"}} wrapping the
     *       per-request App Attest assertion, which signs a
     *       {@code clientDataHash} derived from the noise public key
     *       rather than the request body.</li>
     * </ul>
     * The {@link #authorizationHeader} is similarly platform-specific:
     * the base64 certificate chain on Android, and
     * {@code <base64 attestation>|<base64 keyId>} on iOS.
     *
     * <p>{@link #EMPTY} is used whenever no attestor is configured or
     * the configured attestor returned empty output. In that case
     * neither piece is appended and the request goes out without the
     * {@code H=} field and without the header.
     *
     * @param bodyAttestation     the value to append after
     *                            {@code &H=}, or {@code null} when no
     *                            attestation was produced
     * @param authorizationHeader the value of the {@code Authorization}
     *                            request header, or {@code null} when
     *                            no header should be sent
     */
    protected record BodyAttestation(String bodyAttestation, String authorizationHeader) {
        /**
         * Sentinel used when no attestation output is available, either
         * because no attestor is configured or because the configured
         * attestor returned empty bytes.
         */
        public static final BodyAttestation EMPTY = new BodyAttestation(null, null);
    }

    /**
     * Executes the entire three-step registration flow and saves the
     * resulting state on success.
     *
     * <p>The flow short-circuits when the account already exists with the
     * exact keys being claimed, in which case no verification code is
     * requested. On success the store is marked as registered and its JID
     * is set to the phone-number JID.
     *
     * @throws WhatsAppRegistrationException if any server response
     *                                       indicates a non-recoverable
     *                                       failure or if the underlying
     *                                       HTTP exchange fails
     */
    public void register() {
        try {
            // Emits a session-start funnel event that mirrors the native
            // client's very first pre-phone-number log entry
            sendPrePnFunnelLog("session_start", "registration_session_start");

            // Checks that the account slot for the local keys is still free
            assertRegistrationKeys();

            // Asks the server to send a verification code unless one was already requested
            requestVerificationCodeIfNecessary();

            // Submits the code the user entered and finalises the registration
            sendVerificationCode();
        } catch (IOException | InterruptedException exception) {
            throw new WhatsAppRegistrationException(exception);
        }
    }

    /**
     * Probes {@code /exist} twice to confirm that no account already holds
     * the exact Signal / Noise keys the store is about to register.
     *
     * <p>The server answers {@code "reason": "incorrect"} when the keys
     * differ from whatever the phone number currently has on file, which
     * paradoxically means the number is free from the perspective of these
     * specific keys. The probe is retried once to tolerate transient
     * oddities.
     *
     * @throws IOException if the HTTP call fails
     * @throws InterruptedException if the sending thread is interrupted
     * @throws WhatsAppRegistrationException if neither attempt returns
     *                                       {@code "incorrect"}
     */
    private void assertRegistrationKeys() throws IOException, InterruptedException {
        // Records the first post-phone-number event so the server sees
        // the enter_number screen transition the native client would
        // emit at this point
        sendFunnelLog("enter_number", "exist_check", "exist_attempt");

        // Builds the registration parameters without the registration token,
        // since /exist does not require it
        var attrs = getRegistrationOptions(false);

        // Sends the first probe and accepts the "incorrect" reply
        var result = sendRequest("/exist", attrs);
        var response = JSON.parseObject(result);
        if (Objects.equals(response.getString("reason"), "incorrect")) {
            sendFunnelLog("enter_number", "exist_check", "exist_success");
            return;
        }

        // Retries once in case the first response was a transient anomaly
        result = sendRequest("/exist", attrs);
        response = JSON.parseObject(result);
        if (Objects.equals(response.getString("reason"), "incorrect")) {
            sendFunnelLog("enter_number", "exist_check", "exist_success");
            return;
        }

        // Records a funnel failure before raising so the server sees the
        // flow terminating at the enter_number screen
        sendFunnelLog("enter_number", "exist_check", "exist_failure");

        // Raises a registration failure carrying the raw server payload for diagnostics
        throw new WhatsAppRegistrationException("Cannot get account data", new String(result));
    }


    /**
     * Invokes the verification handler to find out which code method to
     * request and, if one is chosen, calls {@code /code}.
     *
     * <p>If the handler returns an empty optional it is understood to mean
     * that a code has already been requested outside Cobalt, so this step
     * is skipped and the flow proceeds directly to
     * {@link #sendVerificationCode()}.
     *
     * @throws IOException if the HTTP call fails
     * @throws InterruptedException if the sending thread is interrupted
     */
    private void requestVerificationCodeIfNecessary() throws IOException, InterruptedException {
        // Asks the user-supplied handler for the desired verification method
        var codeResult = verification.requestMethod();
        if (codeResult.isEmpty()) {
            return;
        }

        // Requests the code from the server and persists an in-progress marker
        requestVerificationCode(codeResult.get());
        saveRegistrationStatus(false);
    }

    /**
     * Calls {@code /code} in a retry loop until the server confirms the
     * request with a successful status or a definite error.
     *
     * <p>The loop tracks the last error so that two consecutive identical
     * errors abort the flow. Transient errors are retried once. Specific
     * conditions map to user-facing exceptions: {@code too_recent}/{@code
     * too_many} variants mean the caller is spamming, and {@code no_routes}
     * means WhatsApp refused to deliver the code to the given number via
     * the chosen method.
     *
     * @param method the verification method the user asked for
     * @throws IOException if the HTTP call fails
     * @throws InterruptedException if the sending thread is interrupted
     * @throws WhatsAppRegistrationException if the server reports a
     *                                       blocking error
     */
    private void requestVerificationCode(String method) throws IOException, InterruptedException {
        String lastError = null;
        attempt = 1;
        lastRequestedMethod = method;
        var verifyScreen = "verify_" + method;
        sendFunnelLog(verifyScreen, "request_code", "request_code_attempt");
        while (true) {
            // Rebuilds the parameters each iteration because the registration token depends on state
            var params = getRequestVerificationCodeParameters(method);
            var attrs = getRegistrationOptions(true, params);

            // Sends the /code request and parses its JSON response
            var result = sendRequest("/code", attrs);
            var response = JSON.parseObject(result);
            var status = response.getString("status");
            if (isSuccessful(status)) {
                sendFunnelLog(verifyScreen, "request_code", "request_code_success");
                return;
            }

            // Rejects the flow if the server signalled rate limiting, surfacing the wait hint it sent
            var reason = response.getString("reason");
            if(isTooRecent(reason)) {
                throw new WhatsAppRegistrationException(
                        "Please wait before trying to register this phone value again. Don't spam!"
                                + formatWaitSuffix(response, method),
                        new String(result));
            }

            // Raises a targeted message when the server blocks the method entirely
            if(isRegistrationBlocked(reason)) {
                var resultJson = new String(result);
                if(method.equals("wa_old")) {
                    throw new WhatsAppRegistrationException("The registration attempt was blocked by Whatsapp: you might want to change platform(iOS/Android) or try using a residential proxy (don't spam)", resultJson);
                }else {
                    throw new WhatsAppRegistrationException("The registration attempt was blocked by Whatsapp: please try using a Whatsapp OTP as a verification method", resultJson);
                }
            }

            // Aborts if the same error reason appears twice in a row
            if (Objects.equals(reason, lastError)) {
                throw new WhatsAppRegistrationException("An error occurred while registering: " + reason, new String(result));
            }

            lastError = reason;
            attempt++;
        }
    }

    /**
     * Formats the {@code *_wait} hint that the server attached to a
     * rate-limit response, when present, as a trailing
     * {@code " (retry after N seconds)"} fragment for inclusion in an
     * error message.
     *
     * <p>Reads whichever {@code _wait} field matches the caller's
     * requested method first, falling back to the most restrictive
     * non-negative value on the response otherwise.
     *
     * @param response the parsed {@code /v2/code} JSON response
     * @param method the method string the client requested
     * @return the formatted suffix, or an empty string if no wait hint
     *         was supplied
     */
    private String formatWaitSuffix(JSONObject response, String method) {
        var preferred = response.getLong(method + "_wait");
        var wait = preferred != null ? preferred : maxWait(response);
        if (wait == null || wait <= 0) {
            return "";
        }
        return " (retry after " + wait + " seconds)";
    }

    /**
     * Returns the largest non-negative {@code *_wait} value present on
     * the given response, or {@code null} if none is set.
     *
     * @param response the parsed JSON response
     * @return the maximum wait hint in seconds, or {@code null}
     */
    private Long maxWait(JSONObject response) {
        Long max = null;
        for (var key : new String[]{"sms_wait", "voice_wait", "wa_old_wait",
                "flash_wait", "email_otp_wait", "send_sms_wait", "silent_auth_wait"}) {
            var value = response.getLong(key);
            if (value != null && value > 0 && (max == null || value > max)) {
                max = value;
            }
        }
        return max;
    }

    /**
     * Returns {@code true} when the given {@code reason} string indicates
     * that the client is being rate limited.
     *
     * @param reason the reason string from a {@code /code} JSON response
     * @return {@code true} if the reason is a known rate-limit keyword
     */
    private boolean isTooRecent(String reason) {
        return reason.equalsIgnoreCase("too_recent")
               || reason.equalsIgnoreCase("too_many")
               || reason.equalsIgnoreCase("too_many_guesses")
               || reason.equalsIgnoreCase("too_many_all_methods");
    }

    /**
     * Returns {@code true} when the given {@code reason} string indicates
     * that WhatsApp has refused to deliver the verification code to the
     * number.
     *
     * @param reason the reason string from a {@code /code} JSON response
     * @return {@code true} if the reason means routing is unavailable
     */
    private boolean isRegistrationBlocked(String reason) {
        return reason.equalsIgnoreCase("no_routes");
    }


    /**
     * Submits the verification code that the user entered to the
     * {@code /register} endpoint and marks the store as registered on
     * success.
     *
     * <p>The entered code is normalised to strip whitespace and dashes
     * before being embedded in the request so that common user formats
     * (for example {@code "123-456"}) are accepted.
     *
     * @throws IOException if the HTTP call fails
     * @throws InterruptedException if the sending thread is interrupted
     * @throws WhatsAppRegistrationException if the server refuses the
     *                                       submitted code
     */
    public void sendVerificationCode() throws IOException, InterruptedException {
        // Reads the code the user entered from the verification handler
        var code = verification.verificationCode();

        var verifyScreen = currentVerifyScreen();
        sendFunnelLog(verifyScreen, "submit_code", "submit_code_attempt");

        // Appends the normalised code to the registration parameters and sends the request
        var attrs = getRegistrationOptions(true, "code", normalizeCodeResult(code));
        var result = sendRequest("/register", attrs);
        var response = JSON.parseObject(result);
        var status = response.getString("status");
        if (isSuccessful(status)) {
            // Persists the successful registration and the derived JID
            sendFunnelLog("account_verification_complete", "submit_code", "submit_code_success");
            saveRegistrationStatus(true);
            return;
        }

        // If the server asks for an image/audio CAPTCHA, dispatch to the
        // verification handler and resubmit via /v2/challenge.
        if (hasChallenge(response)) {
            handleChallenge(response);
            return;
        }

        // If the server demands the account's 2FA PIN, dispatch to the
        // verification handler and resubmit via /v2/security.
        if (is2FARequired(response)) {
            handle2FA();
            return;
        }

        throw new WhatsAppRegistrationException("Cannot confirm registration", new String(result));
    }

    /**
     * Returns {@code true} when the server response carries a CAPTCHA
     * challenge (either an image or an audio blob) that must be solved
     * before the registration can proceed.
     *
     * @param response the parsed JSON response
     * @return {@code true} if a challenge is embedded
     */
    private boolean hasChallenge(JSONObject response) {
        var image = response.getString("image_blob");
        var audio = response.getString("audio_blob");
        return (image != null && !image.isEmpty()) || (audio != null && !audio.isEmpty());
    }

    /**
     * Returns {@code true} when the server response indicates that
     * two-factor authentication is required to finalise the
     * registration.
     *
     * @param response the parsed JSON response
     * @return {@code true} if the response's reason is a known
     *         2FA-required marker
     */
    private boolean is2FARequired(JSONObject response) {
        var reason = response.getString("reason");
        if (reason == null) {
            return false;
        }
        return reason.equalsIgnoreCase("2fa_required")
                || reason.equalsIgnoreCase("security_code")
                || reason.equalsIgnoreCase("two_factor_required");
    }

    /**
     * Decodes the server-supplied CAPTCHA blobs, asks the verification
     * handler to solve them, and submits the answer to {@code /v2/challenge}.
     *
     * <p>The server may respond with another chained challenge. This
     * method recurses in that case. A handler that returns an empty
     * optional aborts the registration with a
     * {@link WhatsAppRegistrationException}.
     *
     * @param initialResponse the response carrying the initial challenge
     * @throws IOException if the HTTP call fails
     * @throws InterruptedException if the sending thread is interrupted
     * @throws WhatsAppRegistrationException if the handler refuses to
     *                                       solve the challenge or the
     *                                       server refuses the answer
     */
    private void handleChallenge(JSONObject initialResponse)
            throws IOException, InterruptedException {
        var verifyScreen = currentVerifyScreen();
        sendFunnelLog(verifyScreen, "challenge_shown", "captcha_shown");
        var response = initialResponse;
        for (;;) {
            var image = decodeOrNull(response.getString("image_blob"));
            var audio = decodeOrNull(response.getString("audio_blob"));
            var answer = verification.solveCaptcha(image, audio);
            if (answer.isEmpty()) {
                sendFunnelLog(verifyScreen, "challenge_abandoned", "captcha_abandoned");
                throw new WhatsAppRegistrationException(
                        "Server requires a CAPTCHA that the verification handler cannot solve",
                        response.toString());
            }

            sendFunnelLog(verifyScreen, "challenge_submitted", "captcha_submitted");
            var attrs = getRegistrationOptions(true, "code", normalizeCodeResult(answer.get()));
            var result = sendRequest("/challenge", attrs);
            response = JSON.parseObject(result);
            var status = response.getString("status");
            if (isSuccessful(status)) {
                sendFunnelLog("account_verification_complete", "challenge_submitted", "captcha_success");
                saveRegistrationStatus(true);
                return;
            }
            if (hasChallenge(response)) {
                sendFunnelLog(verifyScreen, "challenge_retry", "captcha_retry");
                continue;
            }
            if (is2FARequired(response)) {
                handle2FA();
                return;
            }
            throw new WhatsAppRegistrationException("Cannot complete challenge", new String(result));
        }
    }

    /**
     * Asks the verification handler for the 2FA PIN and submits it to
     * {@code /v2/security}.
     *
     * @throws IOException if the HTTP call fails
     * @throws InterruptedException if the sending thread is interrupted
     * @throws WhatsAppRegistrationException if the handler refuses to
     *                                       supply a PIN or the server
     *                                       refuses the submitted PIN
     */
    private void handle2FA() throws IOException, InterruptedException {
        sendFunnelLog("verify_twofac", "twofac_shown", "twofac_prompt_shown");
        var pin = verification.twoFactorPin();
        if (pin.isEmpty()) {
            sendFunnelLog("verify_twofac", "twofac_abandoned", "twofac_abandoned");
            throw new WhatsAppRegistrationException(
                    "Server requires a 2FA PIN that the verification handler cannot supply", "");
        }

        sendFunnelLog("verify_twofac", "twofac_submitted", "twofac_submitted");
        var attrs = getRegistrationOptions(true, "code", normalizeCodeResult(pin.get()));
        var result = sendRequest("/security", attrs);
        var response = JSON.parseObject(result);
        var status = response.getString("status");
        if (isSuccessful(status)) {
            sendFunnelLog("account_verification_complete", "twofac_submitted", "twofac_success");
            saveRegistrationStatus(true);
            return;
        }
        throw new WhatsAppRegistrationException("Cannot confirm 2FA", new String(result));
    }

    /**
     * Base64-decodes the given string if it is non-null and non-empty,
     * otherwise returns {@code null}.
     *
     * @param base64 the Base64-encoded string, or {@code null}
     * @return the decoded bytes, or {@code null}
     */
    private byte[] decodeOrNull(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException _) {
            try {
                return Base64.getUrlDecoder().decode(base64);
            } catch (IllegalArgumentException still) {
                return null;
            }
        }
    }

    /**
     * Persists the registration state to the store and, on success, the
     * JID derived from the phone number.
     *
     * @param registered whether the flow has completed successfully
     * @throws IOException if the store save fails
     * @throws WhatsAppRegistrationException if the phone number is not set
     *                                       on the store when registration
     *                                       succeeds
     */
    private void saveRegistrationStatus(boolean registered) throws IOException {
        store.setRegistered(registered);
        if (registered) {
            // Derives the phone-number JID and stores it as the local JID
            var phoneNumber = store.phoneNumber()
                    .orElseThrow(() -> new WhatsAppRegistrationException("Phone number wasn't set"));
            var jid = Jid.of(phoneNumber);
            store.setJid(jid);
        }
        store.save();
    }

    /**
     * Strips dashes and whitespace from the user-entered verification code
     * so common formats such as {@code "123-456"} are accepted.
     *
     * @param code the raw code from the verification handler
     * @return the digits-only code
     */
    private String normalizeCodeResult(String code) {
        return code.replaceAll("-", "")
                .trim();
    }

    /**
     * Returns {@code true} when the registration API status string
     * indicates success.
     *
     * @param status the {@code status} field from a JSON response
     * @return {@code true} for {@code ok}, {@code sent}, or
     *         {@code verified}
     */
    private boolean isSuccessful(String status) {
        return status.equalsIgnoreCase("ok")
               || status.equalsIgnoreCase("sent")
               || status.equalsIgnoreCase("verified");
    }

    /**
     * Encrypts the given form body with a fresh ephemeral Curve25519 key,
     * prepends the ephemeral public key, Base64-URL encodes the result, and
     * sends it as an HTTP request against the given sub-path.
     *
     * <p>The server decrypts the payload with its own half of the ECDH,
     * using the hardcoded {@link #REGISTRATION_PUBLIC_KEY} as the other
     * peer.
     *
     * @param path the API sub-path ({@code /exist}, {@code /code},
     *             {@code /register})
     * @param params the unencrypted form body
     * @return the raw response bytes
     * @throws IOException if the HTTP call fails
     * @throws InterruptedException if the sending thread is interrupted
     * @throws RuntimeException if encryption fails or the HTTP status is
     *                          not 200
     */
    private byte[] sendRequest(String path, String params) throws IOException, InterruptedException {
        try {
            // Generates a per-request ephemeral key pair for the outgoing ECDH
            var keypair = SignalIdentityKeyPair.random();
            var key = Curve25519.sharedKey(keypair.privateKey().toEncodedPoint(), REGISTRATION_PUBLIC_KEY);

            // Encrypts the body with AES-256-GCM using a zero IV per the mobile protocol
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, new byte[12])
            );
            var result = cipher.doFinal(params.getBytes(StandardCharsets.UTF_8));

            // Concatenates the ephemeral public key with the ciphertext and URL-base64 encodes it
            var cipheredParameters = Base64.getUrlEncoder().encodeToString(DataUtils.concatByteArrays(keypair.publicKey().toEncodedPoint(), result));

            // Asks the subclass to sign the base64 ENC body with its attestor.
            // The native client's HMAC covers exactly the base64 string, not
            // the "ENC=" prefix and not the trailing "&H=" fragment
            var attestation = attestBody(cipheredParameters.getBytes(StandardCharsets.UTF_8));

            // Assembles the final body: "ENC=<base64>" optionally followed by "&H=<hex>"
            var body = new StringBuilder("ENC=").append(cipheredParameters);
            if (attestation.bodyAttestation() != null && !attestation.bodyAttestation().isEmpty()) {
                body.append("&H=").append(attestation.bodyAttestation());
            }
            var requestBuilder = createRequest(path, body.toString(), attestation.authorizationHeader());

            // Sends the request via the shared HTTP client and validates the status code
            var response = httpClient.send(requestBuilder, HttpResponse.BodyHandlers.ofByteArray());
            if(response.statusCode() != 200) {
                throw new RuntimeException("Cannot send request to " + path + ": status code" + response.statusCode());
            }
            return response.body();
        } catch (GeneralSecurityException exception) {
            throw new RuntimeException("Cannot encrypt request", exception);
        }
    }

    /**
     * Assembles the common registration form body, optionally including a
     * registration token and any extra attributes the caller wants to
     * append.
     *
     * <p>The resulting string contains every shared field the server
     * expects: country code, national number, release channel, Signal
     * identity public key, pre-key, signed pre-key and its signature,
     * noise key, FDID, business verified-name certificate when
     * applicable, plus the platform-specific attestation and push
     * fields returned by {@link #attestationFields()}. Fields whose
     * value is {@code null} are omitted from the output without error.
     *
     * <p>The attestation fields are appended once per call, so every
     * attested endpoint that flows through this method ({@code /exist},
     * {@code /code}, {@code /register}, {@code /security},
     * {@code /challenge}) carries the same shape the native client
     * sends. Funnel endpoints build their bodies directly, bypassing
     * this method, which matches the native client's
     * {@code sendAttestationPayload=false} configuration for them.
     *
     * @param useToken whether to compute and include the registration
     *                 token in the {@code token} field
     * @param attributes optional additional alternating name/value pairs
     *                   appended after the shared fields
     * @return the URL-encoded form body
     */
    private String getRegistrationOptions(boolean useToken, String... attributes) {
        // Parses the store's phone number into a structured object for CC/NN extraction
        var phoneNumber = getPhoneNumber(store);

        // Computes the registration token on demand; null disables the token
        var token = getToken(phoneNumber, useToken);

        // Builds the verified-name certificate only for business platforms
        var certificate = generateBusinessCertificate();
        var fdid = generateFdid();

        // Assembles the set of shared form fields expected by the registration API
        var registrationParams = toFormParams(
                "cc", String.valueOf(phoneNumber.getCountryCode()),
                "in", String.valueOf(phoneNumber.getNationalNumber()),
                "rc", String.valueOf(store.releaseChannel().index()),
                "lg", "en",
                "lc", "US",
                "authkey", Base64.getUrlEncoder().encodeToString(store.noiseKeyPair().publicKey().toEncodedPoint()),
                "vname", certificate,
                "e_regid", Base64.getUrlEncoder().encodeToString(DataUtils.intToBytes(store.registrationId(), 4)),
                "e_keytype", SIGNAL_PUBLIC_KEY_TYPE,
                "e_ident", Base64.getUrlEncoder().encodeToString(store.identityKeyPair().publicKey().toEncodedPoint()),
                "e_skey_id", Base64.getUrlEncoder().encodeToString(DataUtils.intToBytes(store.signedKeyPair().id(), 3)),
                "e_skey_val", Base64.getUrlEncoder().encodeToString(store.signedKeyPair().publicKey().toEncodedPoint()),
                "e_skey_sig", Base64.getUrlEncoder().encodeToString(store.signedKeyPair().signature()),
                "fdid", fdid,
                "expid", Base64.getUrlEncoder().encodeToString(store.deviceId()),
                "id", toUrlHex(store.identityId()),
                "access_session_id", accessSessionId,
                "token", useToken ? token : null
        );

        // Appends the platform-specific attestation + push fields produced by
        // the configured attestor and push client
        var attestationParams = toFormParams(attestationFields());

        // Merges any caller-supplied extra parameters into the final body
        var additionalParams = toFormParams(attributes);

        return joinNonEmpty(registrationParams, attestationParams, additionalParams);
    }

    /**
     * Concatenates the given form-parameter fragments with ampersand
     * separators, skipping any empty or {@code null} fragments.
     *
     * @param fragments the fragments to join, each already in
     *                  {@code "name=value&name=value"} form
     * @return the joined form body, possibly empty
     */
    private static String joinNonEmpty(String... fragments) {
        var out = new StringBuilder();
        for (var fragment : fragments) {
            if (fragment == null || fragment.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append('&');
            }
            out.append(fragment);
        }
        return out.toString();
    }

    /**
     * Computes the registration token for the given phone number via the
     * platform-specific algorithm, unless the caller requests that the
     * token be omitted.
     *
     * @param phoneNumber the parsed phone number
     * @param useToken whether a token should be computed
     * @return the token, or {@code null} if {@code useToken} is
     *         {@code false}
     */
    private String getToken(PhoneNumber phoneNumber, boolean useToken) {
        if (!useToken) {
            return null;
        }

        // Delegates to the platform-specific WhatsAppMobileClientInfo
        var info = WhatsAppMobileClientInfo.of(store.device().platform());
        return info.computeRegistrationToken(phoneNumber.getNationalNumber());
    }

    /**
     * Generates a dummy WhatsApp Business verified-name certificate when
     * the current platform is a business flavour.
     *
     * <p>The certificate carries an empty verified name and a random serial
     * number, and is signed with the local Signal identity private key.
     * Consumer platforms return {@code null}.
     *
     * @return the Base64-URL encoded certificate or {@code null} for
     *         consumer platforms
     */
    protected String generateBusinessCertificate() {
        // Skips certificate generation for non-business platforms
        var platform = store.device().platform();
        if(platform != ClientPlatformType.ANDROID_BUSINESS && platform != ClientPlatformType.IOS_BUSINESS) {
            return null;
        }

        // Builds the certificate details with an empty name and a random serial
        var details = new BusinessVerifiedNameCertificateDetailsBuilder()
                .verifiedName("")
                .issuer(BusinessVerifiedNameCertificate.CertificateIssuer.SMALL_BUSINESS)
                .serial(Math.abs(new SecureRandom().nextLong()))
                .build();
        var encodedDetails = BusinessVerifiedNameCertificateDetailsSpec.encode(details);

        // Signs the details with the local identity private key and packages them
        var certificate = new BusinessVerifiedNameCertificateBuilder()
                .details(encodedDetails)
                .signature(Curve25519.sign(store.identityKeyPair().privateKey().toEncodedPoint(), encodedDetails))
                .build();
        return Base64.getUrlEncoder().encodeToString(BusinessVerifiedNameCertificateSpec.encode(certificate));
    }

    /**
     * Reads the phone number from the store and parses it into a
     * {@link PhoneNumber} usable for CC/NN extraction.
     *
     * @param store the store carrying the registered phone number
     * @return the parsed phone number
     * @throws WhatsAppRegistrationException if the store has no phone
     *                                       number or it cannot be parsed
     */
    protected static PhoneNumber getPhoneNumber(WhatsAppStore store) {
        var phoneNumber = store.phoneNumber()
                .orElseThrow(() -> new WhatsAppRegistrationException("Phone number wasn't set"));
        try {
            // Parses the number using libphonenumber with E.164 leading plus
            return PhoneNumberUtil.getInstance()
                    .parse("+" + phoneNumber, null);
        }catch (NumberParseException exception) {
            throw new WhatsAppRegistrationException("Malformed phone number: " + phoneNumber);
        }
    }

    /**
     * Percent-encodes every byte of the given buffer as {@code %XX} in
     * upper case, producing the mobile API's {@code id} field value.
     *
     * @param buffer the byte buffer to format
     * @return the percent-encoded upper-case hex string
     */
    protected String toUrlHex(byte[] buffer) {
        var id = new StringBuilder();
        for (var x : buffer) {
            id.append(String.format("%%%02x", x));
        }
        return id.toString().toUpperCase(Locale.ROOT);
    }

    /**
     * Joins the given alternating name/value pairs into a
     * {@code name1=value1&name2=value2} form body, skipping pairs whose
     * value is {@code null}.
     *
     * @param entries alternating name/value pairs, totalling an even count
     * @return the joined form body
     * @throws IllegalArgumentException if {@code entries.length} is odd
     */
    private String toFormParams(String... entries) {
        if (entries == null) {
            return "";
        }

        var length = entries.length;
        if ((length & 1) != 0) {
            throw new IllegalArgumentException("Odd form patches");
        }

        // Walks every name/value pair, skipping entries whose value is null
        var result = new StringJoiner("&");
        for (var i = 0; i < length; i += 2) {
            if (entries[i + 1] == null) {
                continue;
            }
            result.add(entries[i] + "=" + entries[i + 1]);
        }

        return result.toString();
    }

    /**
     * Fires a pre-phone-number funnel event at {@code /v2/pre_pn_client_log}.
     *
     * <p>The native client uses this endpoint for funnel events that
     * originate on screens the user sees before entering their phone
     * number (the EULA screen, the phone-number entry screen before any
     * digits are typed). Cobalt has no such UI stage, so the only event
     * this method emits today is a single {@code registration_session_start}
     * fired at the top of {@link #register()}. The body intentionally
     * omits the {@code cc}/{@code in} fields even when the store already
     * has a phone number on file so that it matches the shape the native
     * client sends.
     *
     * <p>Failures are swallowed: funnel telemetry is best-effort and must
     * never abort a registration. The caller receives no indication of
     * whether the log was accepted.
     *
     * @param actionTaken the short action string ({@code "session_start"})
     * @param eventName the long event identifier the server uses to bucket
     *                  the event
     */
    private void sendPrePnFunnelLog(String actionTaken, String eventName) {
        try {
            var fdid = generateFdid();
            var body = toFormParams(
                    "lg", "en",
                    "lc", "US",
                    "expid", Base64.getUrlEncoder().encodeToString(store.deviceId()),
                    "fdid", fdid,
                    "id", toUrlHex(store.identityId()),
                    "current_screen", "enter_number",
                    "previous_screen", "",
                    "action_taken", actionTaken,
                    "event_name", eventName
            );
            sendRequest("/pre_pn_client_log", body);
        } catch (Throwable _) {
            // Funnel telemetry is best-effort and never blocks registration
        }
    }

    /**
     * Fires a funnel event at {@code /v2/client_log} with the current
     * phone number, advancing the {@link #previousFunnelScreen} marker.
     *
     * <p>Each call carries the full {@code cc}/{@code in}/{@code lg}/
     * {@code lc}/{@code expid}/{@code fdid}/{@code id} tuple plus the
     * four event-specific fields ({@code current_screen},
     * {@code previous_screen}, {@code action_taken}, {@code event_name})
     * so the server can reconstruct the client's walk through the
     * registration funnel. The Play Integrity attestation triple is not
     * included because the native client's per-endpoint configuration
     * disables attestation for {@code /v2/client_log}.
     *
     * <p>Failures are swallowed so the registration flow cannot fail on
     * a funnel-log error.
     *
     * @param currentScreen the screen name this event originates from
     *                      ({@code "enter_number"}, {@code "verify_sms"},
     *                      {@code "verify_twofac"}, etc.)
     * @param actionTaken the short action string describing what the
     *                    user or client just did
     * @param eventName the long event identifier the server uses to
     *                  bucket the event
     */
    private void sendFunnelLog(String currentScreen, String actionTaken, String eventName) {
        try {
            var phoneNumber = getPhoneNumber(store);
            var fdid = generateFdid();
            var body = toFormParams(
                    "cc", String.valueOf(phoneNumber.getCountryCode()),
                    "in", String.valueOf(phoneNumber.getNationalNumber()),
                    "lg", "en",
                    "lc", "US",
                    "expid", Base64.getUrlEncoder().encodeToString(store.deviceId()),
                    "fdid", fdid,
                    "id", toUrlHex(store.identityId()),
                    "current_screen", currentScreen,
                    "previous_screen", Objects.requireNonNullElse(previousFunnelScreen, ""),
                    "action_taken", actionTaken,
                    "event_name", eventName
            );
            sendRequest("/client_log", body);
            previousFunnelScreen = currentScreen;
        } catch (Throwable _) {
            // Funnel telemetry is best-effort and never blocks registration
        }
    }

    /**
     * Returns the funnel screen name that corresponds to the
     * verification method last requested through
     * {@link #requestVerificationCode(String)}.
     *
     * <p>Falls back to {@code "enter_number"} when no method has yet
     * been recorded, which keeps the {@code current_screen} field
     * populated even in degenerate flows where a funnel event fires
     * before the first {@code /v2/code} call.
     *
     * @return the funnel screen name
     */
    private String currentVerifyScreen() {
        if (lastRequestedMethod == null) {
            return "enter_number";
        }
        return "verify_" + lastRequestedMethod;
    }

    /**
     * Closes the shared {@link HttpClient} backing this registration.
     *
     * <p>Further calls to {@link #register()} on the same instance will
     * fail once this has been called.
     */
    @Override
    public void close() {
        httpClient.close();
    }
}
