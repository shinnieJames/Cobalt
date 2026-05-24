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
 * Drives the native-mobile phone-number registration flow against
 * WhatsApp's {@code v.whatsapp.net/v2} endpoint.
 *
 * <p>Registering a phone number with WhatsApp as a native Android or
 * iOS client involves three sequential calls against the legacy
 * mobile registration API:
 * <ol>
 *   <li>{@code /exist} asks the server whether an account already
 *       exists for the exact Signal identity and Noise keys this
 *       instance is about to claim. A {@code "reason": "incorrect"}
 *       response confirms the keys are free and the flow proceeds;
 *       any other response aborts.</li>
 *   <li>{@code /code} asks the server to deliver a verification code
 *       through the channel the user picked (SMS, voice call, or
 *       WhatsApp OTP).</li>
 *   <li>{@code /register} submits the code the user typed back into
 *       the verification handler. On success the store is marked as
 *       registered and its JID is set to the phone-number JID.</li>
 * </ol>
 * Two CAPTCHA / 2FA branches ({@code /challenge}, {@code /security})
 * are taken on demand when the server responds with the matching
 * fields. All request bodies are end-to-end encrypted with AES-GCM
 * under a Curve25519 shared key derived between a freshly minted
 * ephemeral keypair and a hardcoded server registration key, then
 * Base64-URL encoded and wrapped in an {@code ENC=...} form field.
 *
 * @apiNote
 * The entry point for Cobalt embedders that want a Cobalt instance to
 * claim a phone number itself rather than pair against an
 * already-registered phone. Embedders construct an instance through
 * {@link #newRegistration(WhatsAppStore,
 * WhatsAppClientVerificationHandler.Mobile, WhatsAppDeviceAttestor,
 * WhatsAppDevicePushClient)} (which selects
 * {@link AndroidClientRegistration} or {@link IosClientRegistration}
 * based on the configured platform), then call {@link #register} once.
 * The class has no WA Web counterpart: WA Web clients always pair
 * against an existing phone via the QR or link-code flow, so this
 * surface exists purely to let Cobalt take the role of a native
 * Android or iOS client.
 *
 * @implNote
 * This implementation tracks the native client's full funnel-event
 * tape (the {@code /v2/pre_pn_client_log} and {@code /v2/client_log}
 * endpoints WhatsApp uses to capture the screen-by-screen registration
 * journey) so the server's anti-abuse heuristics see a connected
 * sequence. Funnel sends are best-effort and intentionally swallow
 * every throwable so a logging failure can never abort a registration.
 *
 * @see AndroidClientRegistration
 * @see IosClientRegistration
 */
public abstract sealed class MobileClientRegistration implements AutoCloseable
        permits AndroidClientRegistration, IosClientRegistration {
    /**
     * Base URL shared by every endpoint used during registration
     * ({@code /exist}, {@code /code}, {@code /register},
     * {@code /challenge}, {@code /security}, {@code /client_log},
     * {@code /pre_pn_client_log}).
     *
     * @apiNote
     * Embedders that need to intercept registration traffic for
     * testing match against this prefix; the value is intentionally
     * not overridable because the cryptographic envelope is bound to
     * WhatsApp's hardcoded server key.
     */
    public static final String MOBILE_REGISTRATION_ENDPOINT = "https://v.whatsapp.net/v2";

    /**
     * Curve25519 public key the WhatsApp registration server
     * advertises, used as the peer for the per-request ECDH that
     * derives the AES-GCM body-encryption key.
     *
     * @apiNote
     * The value is hex-decoded once at class load and reused for
     * every outbound request. It was extracted from the native
     * mobile apps and is checked against the server's response on
     * every call by the AES-GCM tag verification: a wrong key would
     * fail the authentication tag and the server would reject the
     * request.
     */
    private static final byte[] REGISTRATION_PUBLIC_KEY = HexFormat.of().parseHex("8e8c0f74c3ebc5d7a6865c6c3c843856b06121cce8ea774d22fb6f122512302d");

    /**
     * URL-safe base64 encoding of the single byte that identifies
     * Signal identity keys, sent verbatim as the {@code e_keytype}
     * form field.
     *
     * @apiNote
     * Constant for the lifetime of the class because the Signal
     * identity public key type marker never changes.
     */
    private static final String SIGNAL_PUBLIC_KEY_TYPE = Base64.getUrlEncoder().encodeToString(new byte[]{SignalIdentityPublicKey.type()});

    /**
     * HTTP client backing every registration request.
     *
     * @apiNote
     * Created once at construction with redirect following enabled
     * and closed by {@link #close}. Subclasses do not normally need
     * to reach into the client; they build {@link HttpRequest}
     * instances via {@link #createRequest(String, String, String)}.
     */
    protected final HttpClient httpClient;

    /**
     * Store carrying the Signal identity keys, Noise keys,
     * registration id, FDID, device id, phone number, and other
     * persistent credentials referenced on every request.
     *
     * @apiNote
     * Mutated in place when registration succeeds (the
     * {@code registered} flag and the local JID are written via
     * {@link WhatsAppStore#setRegistered} and
     * {@link WhatsAppStore#setJid}).
     */
    protected final WhatsAppStore store;

    /**
     * Callback the registration consults to pick the verification
     * method, retrieve the user-entered code, solve a CAPTCHA, and
     * supply the 2FA PIN when required.
     *
     * @apiNote
     * Supplied by the embedder at builder time; never {@code null}
     * once construction has completed.
     */
    protected final WhatsAppClientVerificationHandler.Mobile verification;

    /**
     * One-based count of {@code /v2/code} attempts driven by the
     * retry loop inside {@link #requestVerificationCode(String)}.
     *
     * @apiNote
     * Read by Android's {@code client_metrics.attempts} field so
     * each retry advertises its position in the loop. Reset to
     * {@code 1} every time {@link #requestVerificationCode(String)}
     * starts.
     */
    protected int attempt = 1;

    /**
     * Screen-name string reported on the most recent funnel event.
     *
     * @apiNote
     * Echoed into the {@code previous_screen} field of subsequent
     * funnel events so the server's anti-abuse pipeline sees a
     * connected walk through the registration UI screens.
     *
     * @implNote
     * This implementation initialises the value to
     * {@code "enter_number"} because the very first funnel event
     * Cobalt emits is the {@code exist_check} attempt, which happens
     * after the phone number has already been committed to the store.
     * The native client's pre-phone-number screens (EULA, idle entry
     * screen) have no Cobalt analogue.
     */
    private String previousFunnelScreen = "enter_number";

    /**
     * Last verification method string passed to
     * {@link #requestVerificationCode(String)}.
     *
     * @apiNote
     * Drives the {@code current_screen} value reported by
     * {@link #sendVerificationCode} and the 2FA handler so funnel
     * events fired after the code exchange carry the same
     * {@code verify_<method>} screen name the native client would
     * have reported. Remains {@code null} until the first call to
     * {@link #requestVerificationCode(String)}.
     */
    private String lastRequestedMethod;

    /**
     * Stable per-registration session identifier sent as the
     * {@code access_session_id} form field on every attested
     * endpoint and every funnel event.
     *
     * @apiNote
     * Generated once at construction time and reused for the entire
     * registration ceremony, so the server can stitch
     * {@code /v2/exist}, {@code /v2/code}, {@code /v2/register},
     * {@code /v2/client_log}, and every other call together as one
     * session.
     *
     * @implNote
     * This implementation mirrors the 22-character URL-safe base64
     * encoding of a random 16-byte UUID that the native Android
     * client emits.
     */
    private final String accessSessionId = newAccessSessionId();

    /**
     * Mints a fresh {@code access_session_id} matching the
     * 22-character URL-safe base64 shape the native client uses.
     *
     * @apiNote
     * Internal helper for the field initialiser of
     * {@link #accessSessionId}; not meant for external use.
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
     * Constructs a registration for the given store and verification
     * handler.
     *
     * @apiNote
     * Called only from the concrete subclasses and from
     * {@link #newRegistration(WhatsAppStore,
     * WhatsAppClientVerificationHandler.Mobile,
     * WhatsAppDeviceAttestor, WhatsAppDevicePushClient)}; embedders
     * never invoke this directly.
     *
     * @param store        the store carrying identity keys and the
     *                     phone number; never {@code null}
     * @param verification the verification handler supplying the
     *                     method and the user-entered code; never
     *                     {@code null}
     * @throws NullPointerException if either argument is
     *                              {@code null}
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
     * Returns the concrete registration implementation matching the
     * platform configured on the given store.
     *
     * @apiNote
     * The standard entry point for embedders: dispatches to either
     * {@link AndroidClientRegistration} or
     * {@link IosClientRegistration} based on
     * {@link WhatsAppStore#device}'s
     * {@link ClientPlatformType platform}. A {@code null} attestor
     * falls back to the platform's
     * {@link WhatsAppDeviceAttestor.Android#NONE} or
     * {@link WhatsAppDeviceAttestor.Ios#NONE} low-trust default. A
     * {@code null} push client falls back to
     * {@link WhatsAppDevicePushClient#noop()}.
     *
     * @implNote
     * This implementation re-validates that the attestor's platform
     * matches the device's platform even though the builder already
     * checks at call site, because the builder's check happens before
     * construction; a defensive re-check here turns a misconfigured
     * driver into an immediate
     * {@link IllegalArgumentException} rather than a confusing
     * runtime cast error.
     *
     * @param store        the store whose device drives the
     *                     selection
     * @param verification the verification handler passed to the new
     *                     registration
     * @param attestor     the device attestor, or {@code null} to
     *                     fall back to the platform's
     *                     {@code NONE} attestor
     * @param pushClient   the device push client, or {@code null} to
     *                     fall back to
     *                     {@link WhatsAppDevicePushClient#noop()}
     * @return a concrete {@code MobileClientRegistration}
     * @throws IllegalArgumentException if the store's device platform
     *                                  is not a supported mobile
     *                                  platform, or if
     *                                  {@code attestor} does not
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
     * {@code /code} request.
     *
     * @apiNote
     * Subclasses populate the long Android field list (SIM MCC/MNC,
     * advertising id, backup token, etc.) or the short iOS field
     * list (method, SIM MCC/MNC, jailbroken flag, APNS push code,
     * cellular strength) the native clients emit on a
     * {@code method=<channel>} run.
     *
     * @implSpec
     * Overriders must return an alternating array of name/value
     * strings of even length, in the order the native client emits.
     * The Play Integrity sextuple (Android) and the APNS
     * {@code push_token} (iOS) must not be included here because
     * they ship on every attested endpoint via
     * {@link #attestationFields()}.
     *
     * @param method the verification method chosen by the user (for
     *               example {@code "sms"}, {@code "voice"},
     *               {@code "wa_old"})
     * @return the additional alternating name/value form parameters
     */
    protected abstract String[] getRequestVerificationCodeParameters(String method);

    /**
     * Returns the device family identifier used as the {@code fdid}
     * form field.
     *
     * @apiNote
     * Subclasses format the same underlying UUID differently:
     * Android emits the lowercase form, iOS emits the uppercase
     * form, matching the respective native apps.
     *
     * @implSpec
     * Overriders must read {@link WhatsAppStore#fdid} and return a
     * non-null UUID string in the per-platform casing.
     *
     * @return the formatted device family identifier
     */
    protected abstract String generateFdid();

    /**
     * Returns the platform-specific device-attestation form fields
     * appended to every attested registration endpoint.
     *
     * @apiNote
     * Android returns the Play Integrity sextuple ({@code gpia},
     * {@code _gg}, {@code _gi}, {@code _gp}, {@code _ge},
     * {@code _ga}) produced by the configured
     * {@link WhatsAppDeviceAttestor.Android} plus the FCM
     * {@code push_token} produced by the configured
     * {@link WhatsAppDevicePushClient}. iOS returns just the APNS
     * {@code push_token}, because its App Attest payloads ride
     * outside the encrypted body (in the {@code H=} suffix and the
     * {@code Authorization} header) rather than inside it. The
     * funnel endpoints {@code /client_log} and
     * {@code /pre_pn_client_log} build their bodies directly without
     * going through {@link #getRegistrationOptions(boolean,
     * String...)} and therefore never reach this method, matching
     * the native client's per-endpoint
     * {@code sendAttestationPayload=false} configuration for them.
     *
     * @implSpec
     * Overriders must return an alternating array of name/value
     * strings of even length. Each invocation may trigger a fresh
     * call into the attestor and the push client; embedders that
     * talk to a remote minter should cache the per-session payload
     * inside their attestor implementation to avoid one round-trip
     * per registration step.
     *
     * @return the alternating name/value attestation fields
     */
    protected abstract String[] attestationFields();

    /**
     * Builds an {@link HttpRequest} for the given sub-path of the
     * mobile registration endpoint.
     *
     * @apiNote
     * Called by {@link #sendRequest(String, String)} after the body
     * has been assembled in its final {@code ENC=<base64>[&H=<x>]}
     * form. Subclasses set only the headers the native client
     * advertises for that platform (User-Agent, Content-Type, plus
     * platform-specific extras) and attach the
     * {@code Authorization} header when one is supplied.
     *
     * @implSpec
     * Overriders must POST {@code body} verbatim, set the
     * {@code User-Agent} matching
     * {@link WhatsAppStore#device}'s user-agent string, and attach
     * {@code authorizationHeader} as the {@code Authorization}
     * header when it is non-{@code null}. Other headers are
     * platform-specific.
     *
     * @param path                the sub-path, starting with a slash
     *                            (for example {@code "/exist"})
     * @param body                the fully-assembled body to POST
     * @param authorizationHeader the value of the
     *                            {@code Authorization} header, or
     *                            {@code null} to omit it
     * @return a ready-to-send HTTP request
     */
    protected abstract HttpRequest createRequest(String path, String body, String authorizationHeader);

    /**
     * Signs the outer {@code ENC=<base64>} body with the
     * platform-specific attestor.
     *
     * @apiNote
     * Called by {@link #sendRequest(String, String)} once per
     * outgoing request. Implementations that have a real attestor
     * (Android Keystore HMAC, iOS App Attest) return a populated
     * pair; the {@code NONE} fallbacks and other low-trust attestors
     * return {@link BodyAttestation#EMPTY} and the base class skips
     * both the {@code &H=} suffix and the {@code Authorization}
     * header.
     *
     * @implSpec
     * Overriders must derive the {@code H=} suffix value and the
     * {@code Authorization} header value from {@code encBodyBytes}
     * (Android) or from store-derived material (iOS). When no real
     * signature is available, return {@link BodyAttestation#EMPTY}.
     *
     * @param encBodyBytes the UTF-8 bytes of the base64 ENC body,
     *                     i.e. the value that follows the
     *                     {@code ENC=} prefix on the wire. Never
     *                     {@code null}
     * @return the suffix and header pair, or
     *         {@link BodyAttestation#EMPTY} when none was produced
     */
    protected abstract BodyAttestation attestBody(byte[] encBodyBytes);

    /**
     * Pair carrying the body suffix and the {@code Authorization}
     * header value produced by {@link #attestBody(byte[])}.
     *
     * @apiNote
     * Internal handshake intermediate; embedders do not see this
     * type. The semantics of each component are platform-specific:
     * <ul>
     *   <li>Android: {@link #bodyAttestation} is the lowercase hex
     *       of the HMAC-SHA1 over the base64 ENC body produced by
     *       the Keystore-backed key; {@link #authorizationHeader}
     *       is the base64 certificate chain.</li>
     *   <li>iOS: {@link #bodyAttestation} is the JSON envelope
     *       {@code {"assertion":"<base64 CBOR>"}} wrapping the
     *       per-request App Attest assertion;
     *       {@link #authorizationHeader} is
     *       {@code <base64 attestation>|<base64 keyId>}.</li>
     * </ul>
     *
     * @param bodyAttestation     the value appended after
     *                            {@code &H=}, or {@code null} when
     *                            no attestation was produced
     * @param authorizationHeader the value of the
     *                            {@code Authorization} header, or
     *                            {@code null} when no header should
     *                            be sent
     */
    protected record BodyAttestation(String bodyAttestation, String authorizationHeader) {
        /**
         * Sentinel returned by {@link #attestBody(byte[])} when no
         * attestation output is available.
         *
         * @apiNote
         * Used both for the {@code NONE} attestor fallbacks and
         * for any attestor that returns empty output (for example
         * a remote minter that is unreachable). The base class
         * treats this value as a signal to skip both the
         * {@code &H=} suffix and the {@code Authorization}
         * header; the registration server accepts the resulting
         * request as a low-trust downgrade.
         */
        public static final BodyAttestation EMPTY = new BodyAttestation(null, null);
    }

    /**
     * Executes the three-step registration flow and persists the
     * result on success.
     *
     * @apiNote
     * The single public entry point. On a fresh phone number the
     * flow visits {@code /exist}, {@code /code}, and
     * {@code /register} in order, optionally branching into
     * {@code /challenge} (CAPTCHA) or {@code /security} (2FA) if the
     * server requests either. On a phone number where the server
     * recognises the local keys the flow may short-circuit before
     * requesting a code.
     *
     * @implNote
     * This implementation wraps every {@link IOException} or
     * {@link InterruptedException} raised by the underlying HTTP
     * client in a
     * {@link WhatsAppRegistrationException} so the caller only ever
     * has one exception type to catch.
     *
     * @throws WhatsAppRegistrationException if any server response
     *                                       indicates a
     *                                       non-recoverable failure
     *                                       or if the underlying
     *                                       HTTP exchange fails
     */
    public void register() {
        try {
            sendPrePnFunnelLog("session_start", "registration_session_start");

            assertRegistrationKeys();

            requestVerificationCodeIfNecessary();

            sendVerificationCode();
        } catch (IOException | InterruptedException exception) {
            throw new WhatsAppRegistrationException(exception);
        }
    }

    /**
     * Probes {@code /v2/exist} to confirm that the account slot for
     * the local keys is still free.
     *
     * @apiNote
     * Invoked exactly once at the top of {@link #register}. A
     * {@code "reason": "incorrect"} response paradoxically means the
     * number is free from the perspective of these keys (the server
     * is reporting that whatever keys it has on file for this number
     * differ from what Cobalt offered), which is the success signal
     * the rest of the flow needs.
     *
     * @implNote
     * This implementation retries once to tolerate a transient
     * non-{@code "incorrect"} response, then aborts if the second
     * attempt also fails. The retry mirrors the native client's
     * own one-shot retry inside {@code /v2/exist} processing.
     *
     * @throws IOException                if the HTTP call fails
     * @throws InterruptedException       if the sending thread is
     *                                    interrupted
     * @throws WhatsAppRegistrationException if neither attempt
     *                                       returns
     *                                       {@code "incorrect"}
     */
    private void assertRegistrationKeys() throws IOException, InterruptedException {
        sendFunnelLog("enter_number", "exist_check", "exist_attempt");

        var attrs = getRegistrationOptions(false);

        var result = sendRequest("/exist", attrs);
        var response = JSON.parseObject(result);
        if (Objects.equals(response.getString("reason"), "incorrect")) {
            sendFunnelLog("enter_number", "exist_check", "exist_success");
            return;
        }

        result = sendRequest("/exist", attrs);
        response = JSON.parseObject(result);
        if (Objects.equals(response.getString("reason"), "incorrect")) {
            sendFunnelLog("enter_number", "exist_check", "exist_success");
            return;
        }

        sendFunnelLog("enter_number", "exist_check", "exist_failure");

        throw new WhatsAppRegistrationException("Cannot get account data", new String(result));
    }


    /**
     * Invokes the verification handler to find out which channel to
     * request a code through and, if any, calls {@code /code}.
     *
     * @apiNote
     * A handler that returns an empty optional from
     * {@link WhatsAppClientVerificationHandler.Mobile#requestMethod}
     * is understood to mean that a code has already been requested
     * outside Cobalt (for example through another device), so this
     * step is skipped and the flow proceeds directly to
     * {@link #sendVerificationCode}.
     *
     * @throws IOException          if the HTTP call fails
     * @throws InterruptedException if the sending thread is
     *                              interrupted
     */
    private void requestVerificationCodeIfNecessary() throws IOException, InterruptedException {
        var codeResult = verification.requestMethod();
        if (codeResult.isEmpty()) {
            return;
        }

        requestVerificationCode(codeResult.get());
        saveRegistrationStatus(false);
    }

    /**
     * Calls {@code /v2/code} in a retry loop until the server
     * confirms or definitively rejects the request.
     *
     * @apiNote
     * Internal helper for {@link #requestVerificationCodeIfNecessary}.
     * The retry loop translates server-side rate-limit and
     * route-block reasons into targeted
     * {@link WhatsAppRegistrationException} messages so embedders
     * can present actionable feedback to the user.
     *
     * @implNote
     * This implementation aborts when the same error reason appears
     * twice in a row (the simplest viable retry policy), captures
     * the per-method or maximum {@code _wait} hint from the response
     * and folds it into the rate-limit message, and treats
     * {@code wa_old} specially when {@code no_routes} comes back
     * (suggesting a platform change rather than a method change).
     *
     * @param method the verification method the user asked for
     * @throws IOException                   if the HTTP call fails
     * @throws InterruptedException          if the sending thread is
     *                                       interrupted
     * @throws WhatsAppRegistrationException if the server reports a
     *                                       blocking error or the
     *                                       same error twice
     */
    private void requestVerificationCode(String method) throws IOException, InterruptedException {
        String lastError = null;
        attempt = 1;
        lastRequestedMethod = method;
        var verifyScreen = "verify_" + method;
        sendFunnelLog(verifyScreen, "request_code", "request_code_attempt");
        while (true) {
            var params = getRequestVerificationCodeParameters(method);
            var attrs = getRegistrationOptions(true, params);

            var result = sendRequest("/code", attrs);
            var response = JSON.parseObject(result);
            var status = response.getString("status");
            if (isSuccessful(status)) {
                sendFunnelLog(verifyScreen, "request_code", "request_code_success");
                return;
            }

            var reason = response.getString("reason");
            if(isTooRecent(reason)) {
                throw new WhatsAppRegistrationException(
                        "Please wait before trying to register this phone value again. Don't spam!"
                                + formatWaitSuffix(response, method),
                        new String(result));
            }

            if(isRegistrationBlocked(reason)) {
                var resultJson = new String(result);
                if(method.equals("wa_old")) {
                    throw new WhatsAppRegistrationException("The registration attempt was blocked by Whatsapp: you might want to change platform(iOS/Android) or try using a residential proxy (don't spam)", resultJson);
                }else {
                    throw new WhatsAppRegistrationException("The registration attempt was blocked by Whatsapp: please try using a Whatsapp OTP as a verification method", resultJson);
                }
            }

            if (Objects.equals(reason, lastError)) {
                throw new WhatsAppRegistrationException("An error occurred while registering: " + reason, new String(result));
            }

            lastError = reason;
            attempt++;
        }
    }

    /**
     * Formats the server's rate-limit {@code _wait} hint as a
     * trailing parenthesised suffix for an error message.
     *
     * @apiNote
     * Internal helper for
     * {@link #requestVerificationCode(String)}. Returns an empty
     * string when no usable hint is present so the caller can
     * concatenate unconditionally.
     *
     * @implNote
     * This implementation reads {@code <method>_wait} first (the
     * server's per-channel suggestion for the channel the client
     * asked for) and falls back to {@link #maxWait(JSONObject)} so
     * the user always sees the most restrictive hint the server
     * provided.
     *
     * @param response the parsed {@code /v2/code} JSON response
     * @param method   the method string the client requested
     * @return the formatted suffix, or an empty string if no
     *         usable hint was supplied
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
     * Returns the largest non-negative {@code *_wait} value carried
     * by the response.
     *
     * @apiNote
     * Internal helper for
     * {@link #formatWaitSuffix(JSONObject, String)}. The set of
     * keys scanned ({@code sms_wait}, {@code voice_wait},
     * {@code wa_old_wait}, {@code flash_wait},
     * {@code email_otp_wait}, {@code send_sms_wait},
     * {@code silent_auth_wait}) matches the channels the
     * registration server is known to advertise.
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
     * Tests whether a server {@code reason} string means the caller
     * is being rate limited.
     *
     * @apiNote
     * Internal helper for
     * {@link #requestVerificationCode(String)}. The four reason
     * strings recognised here cover the per-channel rate limit, the
     * cross-channel limit, the per-IP guess limit, and the
     * all-methods limit.
     *
     * @param reason the reason string from a {@code /code} JSON
     *               response
     * @return {@code true} if the reason is a known rate-limit
     *         keyword
     */
    private boolean isTooRecent(String reason) {
        return reason.equalsIgnoreCase("too_recent")
               || reason.equalsIgnoreCase("too_many")
               || reason.equalsIgnoreCase("too_many_guesses")
               || reason.equalsIgnoreCase("too_many_all_methods");
    }

    /**
     * Tests whether a server {@code reason} string means WhatsApp
     * refused to deliver the verification code.
     *
     * @apiNote
     * Internal helper for
     * {@link #requestVerificationCode(String)}. The
     * {@code no_routes} response means the server cannot route the
     * code through the chosen channel and the client should try a
     * different channel (or a different platform / proxy).
     *
     * @param reason the reason string from a {@code /code} JSON
     *               response
     * @return {@code true} if the reason means routing is
     *         unavailable
     */
    private boolean isRegistrationBlocked(String reason) {
        return reason.equalsIgnoreCase("no_routes");
    }


    /**
     * Submits the user-entered verification code to
     * {@code /v2/register} and marks the store as registered on
     * success.
     *
     * @apiNote
     * Final step of the flow. May branch into
     * {@link #handleChallenge} when the server responds with a
     * CAPTCHA or into {@link #handle2FA} when the account is
     * protected by a 2FA PIN.
     *
     * @implNote
     * This implementation strips whitespace and dashes from the
     * code via {@link #normalizeCodeResult(String)} so common
     * user-entered formats (for example {@code "123-456"}) work
     * without further preprocessing by the embedder.
     *
     * @throws IOException                   if the HTTP call fails
     * @throws InterruptedException          if the sending thread is
     *                                       interrupted
     * @throws WhatsAppRegistrationException if the server refuses
     *                                       the submitted code
     */
    public void sendVerificationCode() throws IOException, InterruptedException {
        var code = verification.verificationCode();

        var verifyScreen = currentVerifyScreen();
        sendFunnelLog(verifyScreen, "submit_code", "submit_code_attempt");

        var attrs = getRegistrationOptions(true, "code", normalizeCodeResult(code));
        var result = sendRequest("/register", attrs);
        var response = JSON.parseObject(result);
        var status = response.getString("status");
        if (isSuccessful(status)) {
            sendFunnelLog("account_verification_complete", "submit_code", "submit_code_success");
            saveRegistrationStatus(true);
            return;
        }

        if (hasChallenge(response)) {
            handleChallenge(response);
            return;
        }

        if (is2FARequired(response)) {
            handle2FA();
            return;
        }

        throw new WhatsAppRegistrationException("Cannot confirm registration", new String(result));
    }

    /**
     * Tests whether a server response carries a CAPTCHA challenge
     * blob.
     *
     * @apiNote
     * Internal helper for {@link #sendVerificationCode} and
     * {@link #handleChallenge(JSONObject)}. Returns {@code true}
     * when either the image or the audio variant is present, so the
     * caller can route the response into the challenge flow.
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
     * Tests whether a server response signals that 2FA is required
     * to finalise the registration.
     *
     * @apiNote
     * Internal helper for {@link #sendVerificationCode} and
     * {@link #handleChallenge(JSONObject)}. The three reason
     * strings cover the canonical {@code 2fa_required}, the legacy
     * {@code security_code} alias, and the newer
     * {@code two_factor_required} variant.
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
     * Decodes the CAPTCHA blobs, asks the verification handler to
     * solve them, and submits the answer to {@code /v2/challenge}.
     *
     * @apiNote
     * Internal helper for {@link #sendVerificationCode}. A handler
     * that returns an empty optional from
     * {@link WhatsAppClientVerificationHandler.Mobile#solveCaptcha}
     * aborts the registration with a
     * {@link WhatsAppRegistrationException}.
     *
     * @implNote
     * This implementation loops because the server may chain
     * multiple CAPTCHAs (a wrong answer is replied to with another
     * challenge); the loop exits on success, on 2FA branch, or on a
     * non-challenge non-success response.
     *
     * @param initialResponse the response carrying the initial
     *                        challenge
     * @throws IOException                   if the HTTP call fails
     * @throws InterruptedException          if the sending thread is
     *                                       interrupted
     * @throws WhatsAppRegistrationException if the handler refuses
     *                                       to solve the challenge
     *                                       or the server refuses
     *                                       the answer
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
     * Asks the verification handler for the 2FA PIN and submits it
     * to {@code /v2/security}.
     *
     * @apiNote
     * Internal helper for {@link #sendVerificationCode} and
     * {@link #handleChallenge(JSONObject)}. A handler that returns
     * an empty optional from
     * {@link WhatsAppClientVerificationHandler.Mobile#twoFactorPin}
     * aborts the registration.
     *
     * @throws IOException                   if the HTTP call fails
     * @throws InterruptedException          if the sending thread is
     *                                       interrupted
     * @throws WhatsAppRegistrationException if the handler refuses
     *                                       to supply a PIN or the
     *                                       server refuses the
     *                                       submitted PIN
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
     * Decodes a base64 string defensively, accepting either the
     * standard or URL-safe alphabet.
     *
     * @apiNote
     * Internal helper for the CAPTCHA branch; the server has been
     * observed to switch between the two alphabets across releases,
     * so this method tries the standard decoder first and falls
     * back to the URL-safe decoder before giving up.
     *
     * @param base64 the base64-encoded string, or {@code null}
     * @return the decoded bytes, or {@code null} if the input is
     *         absent or undecodable in both alphabets
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
     * Persists the registration state and, on success, the derived
     * JID.
     *
     * @apiNote
     * Internal helper called from
     * {@link #requestVerificationCodeIfNecessary}, from
     * {@link #sendVerificationCode}, from {@link #handle2FA}, and
     * from {@link #handleChallenge(JSONObject)}. Embedders never
     * call this directly; the store is mutated in place.
     *
     * @param registered whether the flow has completed successfully
     * @throws IOException                   if the store save fails
     * @throws WhatsAppRegistrationException if the phone number is
     *                                       missing from the store
     *                                       when registration
     *                                       succeeds
     */
    private void saveRegistrationStatus(boolean registered) throws IOException {
        store.setRegistered(registered);
        if (registered) {
            var phoneNumber = store.phoneNumber()
                    .orElseThrow(() -> new WhatsAppRegistrationException("Phone number wasn't set"));
            var jid = Jid.of(phoneNumber);
            store.setJid(jid);
        }
        store.save();
    }

    /**
     * Strips dashes and whitespace from a user-entered verification
     * code.
     *
     * @apiNote
     * Internal helper called from every code-submitting branch so
     * common user formats ({@code "123-456"}, {@code "123 456"})
     * are accepted without extra preprocessing in the embedder.
     *
     * @param code the raw code from the verification handler
     * @return the digits-only code
     */
    private String normalizeCodeResult(String code) {
        return code.replaceAll("-", "")
                .trim();
    }

    /**
     * Tests whether a registration-API {@code status} string
     * indicates success.
     *
     * @apiNote
     * Internal helper called after every server call. The three
     * accepted values cover the three success synonyms observed on
     * different endpoints: {@code ok} for {@code /exist},
     * {@code sent} for {@code /code}, and {@code verified} for
     * {@code /register}, {@code /challenge}, and {@code /security}.
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
     * Encrypts the given form body and sends it as an HTTP request
     * to the given sub-path.
     *
     * @apiNote
     * Internal helper for every server call. The server decrypts
     * the payload with its own half of the ECDH, using the
     * hardcoded {@link #REGISTRATION_PUBLIC_KEY} as the other peer.
     *
     * @implNote
     * This implementation mints a fresh ephemeral Curve25519
     * keypair for every request, runs ECDH against the server key,
     * and encrypts the body with AES-256-GCM under a zero-byte IV
     * (matching the native mobile protocol, which relies on the
     * per-request ephemeral key for freshness). The ENC payload is
     * the concatenation of the ephemeral public key and the
     * ciphertext, URL-base64 encoded. The subclass-supplied
     * attestation suffix and authorization header are attached
     * after the {@code ENC=} envelope is assembled. A non-200
     * status code surfaces as a runtime exception so the caller can
     * see which endpoint failed and with what code.
     *
     * @param path   the API sub-path
     * @param params the unencrypted form body
     * @return the raw response bytes
     * @throws IOException                   if the HTTP call fails
     * @throws InterruptedException          if the sending thread is
     *                                       interrupted
     * @throws RuntimeException              if encryption fails or
     *                                       the HTTP status is not
     *                                       200
     */
    private byte[] sendRequest(String path, String params) throws IOException, InterruptedException {
        try {
            var keypair = SignalIdentityKeyPair.random();
            var key = Curve25519.sharedKey(keypair.privateKey().toEncodedPoint(), REGISTRATION_PUBLIC_KEY);

            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, new byte[12])
            );
            var result = cipher.doFinal(params.getBytes(StandardCharsets.UTF_8));

            var cipheredParameters = Base64.getUrlEncoder().encodeToString(DataUtils.concatByteArrays(keypair.publicKey().toEncodedPoint(), result));

            var attestation = attestBody(cipheredParameters.getBytes(StandardCharsets.UTF_8));

            var body = new StringBuilder("ENC=").append(cipheredParameters);
            if (attestation.bodyAttestation() != null && !attestation.bodyAttestation().isEmpty()) {
                body.append("&H=").append(attestation.bodyAttestation());
            }
            var requestBuilder = createRequest(path, body.toString(), attestation.authorizationHeader());

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
     * Assembles the shared registration form body and appends any
     * caller-supplied extra fields.
     *
     * @apiNote
     * Internal helper for every attested endpoint
     * ({@code /exist}, {@code /code}, {@code /register},
     * {@code /security}, {@code /challenge}). The returned string
     * carries every shared field the server expects: country code
     * and national number, locale, release channel, the Signal
     * identity / pre-key / signed pre-key trio with its signature,
     * the Noise key, the FDID, the per-session
     * {@code access_session_id}, the optional registration token,
     * the business verified-name certificate (only on business
     * platforms), and the platform-specific attestation and push
     * fields returned by {@link #attestationFields()}. The funnel
     * endpoints ({@code /client_log}, {@code /pre_pn_client_log})
     * build their own body and bypass this method, matching the
     * native client's {@code sendAttestationPayload=false}
     * configuration for them.
     *
     * @implNote
     * This implementation skips {@code null}-valued fields silently
     * so subclasses can pass conditional values (the registration
     * token, the business certificate) without sentinel checks at
     * the call site.
     *
     * @param useToken   whether to compute and include the
     *                   registration token
     * @param attributes optional additional alternating name/value
     *                   pairs appended after the shared fields
     * @return the URL-encoded form body
     */
    private String getRegistrationOptions(boolean useToken, String... attributes) {
        var phoneNumber = getPhoneNumber(store);

        var token = getToken(phoneNumber, useToken);

        var certificate = generateBusinessCertificate();
        var fdid = generateFdid();

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

        var attestationParams = toFormParams(attestationFields());

        var additionalParams = toFormParams(attributes);

        return joinNonEmpty(registrationParams, attestationParams, additionalParams);
    }

    /**
     * Concatenates form-parameter fragments with ampersand
     * separators, skipping empty or {@code null} fragments.
     *
     * @apiNote
     * Internal helper for
     * {@link #getRegistrationOptions(boolean, String...)}; lets the
     * caller compose the body out of three groups (shared,
     * attestation, additional) without worrying about whether any
     * one of them is empty.
     *
     * @param fragments the fragments to join, each already in
     *                  {@code name=value&name=value} form
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
     * Computes the registration token unless the caller asks for it
     * to be omitted.
     *
     * @apiNote
     * Internal helper for
     * {@link #getRegistrationOptions(boolean, String...)}. The
     * token is derived from the national number via the
     * platform-specific
     * {@link WhatsAppMobileClientInfo#computeRegistrationToken(long)}
     * implementation, which adapts the native client's per-platform
     * obfuscation step.
     *
     * @param phoneNumber the parsed phone number
     * @param useToken    whether a token should be computed
     * @return the token, or {@code null} when {@code useToken} is
     *         {@code false}
     */
    private String getToken(PhoneNumber phoneNumber, boolean useToken) {
        if (!useToken) {
            return null;
        }

        var info = WhatsAppMobileClientInfo.of(store.device().platform());
        return info.computeRegistrationToken(phoneNumber.getNationalNumber());
    }

    /**
     * Generates a WhatsApp Business verified-name certificate when
     * the configured platform is a business flavour.
     *
     * @apiNote
     * Internal helper for
     * {@link #getRegistrationOptions(boolean, String...)}; returns
     * {@code null} on consumer platforms so the {@code vname} form
     * field is omitted from the body entirely.
     *
     * @implNote
     * This implementation issues a placeholder certificate with an
     * empty verified name and a random serial number, signed with
     * the local Signal identity private key. WhatsApp's business
     * platforms expect the field to be present even for
     * unverified merchants; the real verified-name flow runs
     * post-registration and replaces this placeholder.
     *
     * @return the base64-URL encoded certificate, or {@code null}
     *         on consumer platforms
     */
    protected String generateBusinessCertificate() {
        var platform = store.device().platform();
        if(platform != ClientPlatformType.ANDROID_BUSINESS && platform != ClientPlatformType.IOS_BUSINESS) {
            return null;
        }

        var details = new BusinessVerifiedNameCertificateDetailsBuilder()
                .verifiedName("")
                .issuer(BusinessVerifiedNameCertificate.CertificateIssuer.SMALL_BUSINESS)
                .serial(Math.abs(new SecureRandom().nextLong()))
                .build();
        var encodedDetails = BusinessVerifiedNameCertificateDetailsSpec.encode(details);

        var certificate = new BusinessVerifiedNameCertificateBuilder()
                .details(encodedDetails)
                .signature(Curve25519.sign(store.identityKeyPair().privateKey().toEncodedPoint(), encodedDetails))
                .build();
        return Base64.getUrlEncoder().encodeToString(BusinessVerifiedNameCertificateSpec.encode(certificate));
    }

    /**
     * Reads the phone number from the store and parses it into a
     * {@link PhoneNumber}.
     *
     * @apiNote
     * Internal helper for
     * {@link #getRegistrationOptions(boolean, String...)} and for
     * the funnel-event senders. The parsed form is needed to split
     * the country code and the national number into the {@code cc}
     * and {@code in} form fields.
     *
     * @implNote
     * This implementation feeds the number through
     * {@link PhoneNumberUtil} after prepending {@code "+"} so
     * libphonenumber treats it as E.164. A malformed number surfaces
     * as a
     * {@link WhatsAppRegistrationException}, never as a raw
     * {@link NumberParseException}.
     *
     * @param store the store carrying the registered phone number
     * @return the parsed phone number
     * @throws WhatsAppRegistrationException if the store has no
     *                                       phone number or it
     *                                       cannot be parsed
     */
    protected static PhoneNumber getPhoneNumber(WhatsAppStore store) {
        var phoneNumber = store.phoneNumber()
                .orElseThrow(() -> new WhatsAppRegistrationException("Phone number wasn't set"));
        try {
            return PhoneNumberUtil.getInstance()
                    .parse("+" + phoneNumber, null);
        }catch (NumberParseException exception) {
            throw new WhatsAppRegistrationException("Malformed phone number: " + phoneNumber);
        }
    }

    /**
     * Percent-encodes every byte of a buffer as {@code %XX} in
     * uppercase.
     *
     * @apiNote
     * Internal helper that produces the value of the mobile API's
     * {@code id} form field (over the local identity id) and the
     * Android {@code backup_token} field. The mobile registration
     * server expects opaque byte blobs to be percent-encoded
     * regardless of whether each byte is URL-safe on its own.
     *
     * @param buffer the byte buffer to format
     * @return the percent-encoded uppercase hex string
     */
    protected String toUrlHex(byte[] buffer) {
        var id = new StringBuilder();
        for (var x : buffer) {
            id.append(String.format("%%%02x", x));
        }
        return id.toString().toUpperCase(Locale.ROOT);
    }

    /**
     * Joins alternating name / value pairs into a
     * {@code name1=value1&name2=value2} form body.
     *
     * @apiNote
     * Internal helper used by every form-body assembler in this
     * class. Pairs whose value is {@code null} are dropped silently
     * so callers can pass conditionals without sentinel checks.
     *
     * @param entries alternating name / value pairs, totalling an
     *                even count
     * @return the joined form body
     * @throws IllegalArgumentException if {@code entries.length} is
     *                                  odd
     */
    private String toFormParams(String... entries) {
        if (entries == null) {
            return "";
        }

        var length = entries.length;
        if ((length & 1) != 0) {
            throw new IllegalArgumentException("Odd form patches");
        }

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
     * Fires a pre-phone-number funnel event against
     * {@code /v2/pre_pn_client_log}.
     *
     * @apiNote
     * Internal helper for the registration funnel telemetry. The
     * native client uses this endpoint for events that originate on
     * screens the user sees before entering their phone number
     * (EULA, idle phone-number entry screen). Cobalt has no such UI
     * stage, so the only event ever sent here is the single
     * {@code registration_session_start} fired at the top of
     * {@link #register}.
     *
     * @implNote
     * This implementation deliberately omits the {@code cc} and
     * {@code in} fields even when the store already has a phone
     * number on file, to match the shape the native client emits.
     * Every throwable is swallowed because funnel telemetry must
     * never abort a registration.
     *
     * @param actionTaken the short action string (for example
     *                    {@code "session_start"})
     * @param eventName   the long event identifier the server uses
     *                    to bucket the event
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
     * Fires a funnel event against {@code /v2/client_log}.
     *
     * @apiNote
     * Internal helper for the registration funnel telemetry,
     * invoked once per UI transition in the flow
     * ({@code exist_check}, {@code request_code},
     * {@code submit_code}, {@code challenge_shown}, etc.). Each
     * call advances {@link #previousFunnelScreen} so subsequent
     * events report the correct {@code previous_screen} attribute.
     *
     * @implNote
     * This implementation includes the {@code cc}/{@code in} pair
     * because the phone number has been committed by the time this
     * runs, but does not include the Play Integrity attestation
     * triple: the native client's per-endpoint configuration
     * disables attestation for {@code /v2/client_log}. Every
     * throwable is swallowed because funnel telemetry must never
     * abort a registration.
     *
     * @param currentScreen the screen name this event originates
     *                      from (for example {@code "enter_number"},
     *                      {@code "verify_sms"},
     *                      {@code "verify_twofac"})
     * @param actionTaken   the short action string describing what
     *                      the user or client just did
     * @param eventName     the long event identifier the server
     *                      uses to bucket the event
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
     * Returns the funnel screen name that corresponds to the most
     * recently requested verification method.
     *
     * @apiNote
     * Internal helper used by every post-{@code /v2/code} funnel
     * sender. Falls back to {@code "enter_number"} when no method
     * has yet been recorded so the {@code current_screen} attribute
     * stays populated even on degenerate flows where a funnel event
     * fires before the first {@code /v2/code} call.
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
     * Closes the shared {@link HttpClient} backing this
     * registration.
     *
     * @apiNote
     * Embedders that hold a registration instance for the duration
     * of a single ceremony can call this via try-with-resources.
     * Once closed, further calls to {@link #register} on the same
     * instance fail at the first HTTP send.
     */
    @Override
    public void close() {
        httpClient.close();
    }
}
