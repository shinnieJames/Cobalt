package com.github.auties00.cobalt.registration;

import com.github.auties00.cobalt.client.WhatsAppClientVerificationHandler;
import com.github.auties00.cobalt.client.WhatsAppDeviceAttestor;
import com.github.auties00.cobalt.client.WhatsAppDevicePushClient;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Locale;
import java.util.Objects;

/**
 * Mobile registration driver impersonating the native iOS WhatsApp
 * application.
 *
 * <p>Adds iOS-specific behaviour on top of
 * {@link MobileClientRegistration}:
 * <ul>
 *   <li>{@link #createRequest(String, String, String)} uses the iOS
 *       User-Agent and sends only the minimal headers the native iOS
 *       app emits (no {@code WaMsysRequest}, no explicit
 *       {@code Accept}, no {@code request_token}).</li>
 *   <li>{@link #getRequestVerificationCodeParameters(String)}
 *       populates the short set of form fields the iOS app sends
 *       (method, SIM MCC/MNC, jailbroken flag, APNS silent-push
 *       code, cellular strength).</li>
 *   <li>{@link #attestBody(byte[])} mints an Apple App Attest
 *       assertion + attestation pair via the configured
 *       {@link WhatsAppDeviceAttestor.Ios} and packages it into the
 *       {@code H=} body suffix plus the {@code Authorization}
 *       header.</li>
 *   <li>{@link #attestationFields()} ships just the APNS
 *       {@code push_token} inside the encrypted body; the App
 *       Attest payloads ride outside it.</li>
 *   <li>{@link #generateFdid()} formats the device family UUID in
 *       uppercase, matching the native client.</li>
 * </ul>
 *
 * @apiNote
 * iOS-specific subclass of {@link MobileClientRegistration}.
 * Package-private because embedders construct this through
 * {@link MobileClientRegistration#newRegistration(WhatsAppStore,
 * WhatsAppClientVerificationHandler.Mobile, WhatsAppDeviceAttestor,
 * WhatsAppDevicePushClient)} rather than directly.
 *
 * @implNote
 * This implementation reproduces the wire shape confirmed by Frida
 * runtime tracing of
 * {@code -[RegistrationAttestationManagerImpl assertionFor:path:]}
 * and {@code attestationPayloadForRegistrationFor:} together with
 * {@code -[NSMutableURLRequest setHTTPBody:]} and
 * {@code setValue:forHTTPHeaderField:} on a live iOS WhatsApp
 * registration. The per-request CBOR assertion goes into the
 * {@code H=} suffix wrapped in a {@code {"assertion":"<base64>"}}
 * JSON envelope; the session-stable CBOR attestation goes into the
 * {@code Authorization} header alongside the
 * {@code keyId}, joined by a literal {@code "|"}. Both wire slots are
 * skipped on the funnel endpoints
 * ({@code /v2/client_log}, {@code /v2/pre_pn_client_log}), matching
 * the native client's behaviour where {@code attestationFor:path:}
 * returns {@code nil} for those paths.
 *
 * @see MobileClientRegistration
 */
final class IosClientRegistration extends MobileClientRegistration {
    /**
     * The iOS attestor consulted before each outgoing request.
     *
     * @apiNote
     * Never {@code null}: the constructor substitutes
     * {@link WhatsAppDeviceAttestor.Ios#NONE} when the caller
     * supplies {@code null}. The {@code NONE} fallback returns
     * empty App Attest output, which the server tolerates as a
     * low-trust downgrade signal.
     */
    private final WhatsAppDeviceAttestor.Ios attestor;

    /**
     * The push client consulted for the APNS {@code push_token} and
     * the silent-push verification code form fields.
     *
     * @apiNote
     * Never {@code null}: the constructor substitutes
     * {@link WhatsAppDevicePushClient#noop()} when the caller
     * supplies {@code null}.
     */
    private final WhatsAppDevicePushClient pushClient;

    /**
     * Constructs an iOS registration bound to the given
     * collaborators.
     *
     * @apiNote
     * Called only from
     * {@link MobileClientRegistration#newRegistration(WhatsAppStore,
     * WhatsAppClientVerificationHandler.Mobile,
     * WhatsAppDeviceAttestor, WhatsAppDevicePushClient)}.
     *
     * @param store        the store carrying identity keys and the
     *                     phone number
     * @param verification the verification handler supplying the
     *                     method and the user-entered code
     * @param attestor     the iOS device attestor, or {@code null}
     *                     to fall back to
     *                     {@link WhatsAppDeviceAttestor.Ios#NONE}
     * @param pushClient   the push client, or {@code null} to fall
     *                     back to
     *                     {@link WhatsAppDevicePushClient#noop()}
     */
    IosClientRegistration(
            WhatsAppStore store,
            WhatsAppClientVerificationHandler.Mobile verification,
            WhatsAppDeviceAttestor.Ios attestor,
            WhatsAppDevicePushClient pushClient) {
        super(store, verification);
        this.attestor = Objects.requireNonNullElse(attestor, WhatsAppDeviceAttestor.Ios.NONE);
        this.pushClient = Objects.requireNonNullElse(pushClient, WhatsAppDevicePushClient.noop());
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation sets only the two headers the native iOS
     * registration stack advertises ({@code User-Agent} and
     * {@code Content-Type}), then attaches the
     * {@code Authorization} header carrying
     * {@code <base64 attestation>|<base64 keyId>} when one was
     * produced. The native iOS client omits {@code Accept},
     * {@code WaMsysRequest}, and {@code request_token} entirely.
     */
    @Override
    protected HttpRequest createRequest(String path, String body, String authorizationHeader) {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create("%s%s".formatted(MOBILE_REGISTRATION_ENDPOINT, path)))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("User-Agent", store.device().toUserAgent(store.clientVersion()))
                .header("Content-Type", "application/x-www-form-urlencoded");
        if (authorizationHeader != null) {
            builder.header("Authorization", authorizationHeader);
        }
        return builder.build();
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Concretely on iOS: asks the configured
     * {@link WhatsAppDeviceAttestor.Ios} to mint an App Attest
     * payload and packages it into the
     * {@link BodyAttestation} pair:
     * <ul>
     *   <li>The {@code H=} body suffix carries
     *       {@code {"assertion":"<base64 CBOR>"}} (the per-request
     *       CBOR assertion wrapped in a JSON envelope).</li>
     *   <li>The {@code Authorization} header carries
     *       {@code <base64 attestation>|<base64 keyId>} (the
     *       cached CBOR attestation joined to the App Attest
     *       {@code keyId} by a literal {@code "|"}).</li>
     * </ul>
     *
     * @implNote
     * This implementation returns
     * {@link BodyAttestation#EMPTY} whenever any of the three
     * payload components (attestation, assertion, keyId) comes back
     * empty, signalling either the
     * {@link WhatsAppDeviceAttestor.Ios#NONE} fallback or an
     * attestor that cannot mint App Attest (a simulator or a
     * jailbroken device). The JSON envelope is emitted verbatim
     * with {@code "/"} JSON-escaped to {@code "\/"} (matching the
     * native client's emission) and otherwise unmodified; the
     * resulting raw {@code =}, {@code +}, and {@code /} characters
     * are passed through the form value untouched because the
     * server's parser is known to tolerate them.
     *
     * <p>Unlike Android's body attestation, iOS's assertion does
     * not sign {@code encBodyBytes}: the
     * {@code clientDataHash} the attestor binds the assertion to is
     * the SHA-256 of the Noise public key, derived from the store.
     * The {@code encBodyBytes} parameter is accepted only because
     * the base-class contract is platform-neutral.
     */
    @Override
    protected BodyAttestation attestBody(byte[] encBodyBytes) {
        var data = attestor.attest(store);
        if (data.attestation().isEmpty() || data.assertion().isEmpty() || data.keyId().isEmpty()) {
            return BodyAttestation.EMPTY;
        }
        var bodyAttestation = "{\"assertion\":\"" + data.assertion() + "\"}";
        var authorizationHeader = data.attestation() + "|" + data.keyId();
        return new BodyAttestation(bodyAttestation, authorizationHeader);
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Concretely on iOS, the returned fields mirror what
     * {@code -[WARegistrationURLBuilder
     * verificationCodeRequestURLWithBaseURL:method:mcc:mnc:jailbroken:
     * context:oldPhoneNumber:silentPushNotifRegCode:iosDeviceRegistrationUUID:cellularStrength:]}
     * emits on the native client: method, empty SIM MCC/MNC,
     * {@code jailbroken=0}, the APNS silent-push verification code
     * received via
     * {@code application:didReceiveRemoteNotification:} (sourced
     * from {@link WhatsAppDevicePushClient#getPushCode}), and a
     * cellular signal strength of {@code 1}.
     *
     * @implNote
     * This implementation does not include the APNS device token
     * here because it ships on every attested endpoint via
     * {@link #attestationFields()}.
     */
    @Override
    protected String[] getRequestVerificationCodeParameters(String method) {
        return new String[]{
                "method", method,
                "sim_mcc", "000",
                "sim_mnc", "000",
                "jailbroken", "0",
                "push_code", pushClient.getPushCode(),
                "cellular_strength", "1"
        };
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Concretely on iOS, returns only the APNS {@code push_token}.
     * The App Attest payloads do not appear here because they ride
     * outside the encrypted body, in the {@code H=} suffix
     * appended after the {@code ENC=} envelope and in the
     * {@code Authorization} request header, both built by
     * {@link #attestBody(byte[])}.
     *
     * @implNote
     * This implementation mirrors the native iOS client's split
     * between {@code attestationPayloadForRegistrationFor:} and
     * {@code assertionFor:path:} hooks (called at request-build
     * time, outside the form body) and the per-endpoint form-field
     * assembly (which adds only the push token). The
     * {@code push_token} field carries the APNS device token the
     * iOS app receives via
     * {@code -[UIApplication
     * application:didRegisterForRemoteNotificationsWithDeviceToken:]}
     * and is advertised on every attested endpoint so the server
     * knows where to silent-push the verification code.
     */
    @Override
    protected String[] attestationFields() {
        return new String[]{
                "push_token", pushClient.getPushToken()
        };
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Concretely on iOS, the UUID is formatted in uppercase with
     * hyphens, matching the native client's {@code fdid} scheme.
     */
    @Override
    protected String generateFdid() {
        return store.fdid().toString().toUpperCase(Locale.ROOT);
    }
}
