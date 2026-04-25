package com.github.auties00.cobalt.client.registration;

import com.github.auties00.cobalt.client.WhatsAppClientVerificationHandler;
import com.github.auties00.cobalt.client.WhatsAppDeviceAttestor;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Locale;
import java.util.Objects;

/**
 * Mobile registration driver that impersonates the native iOS WhatsApp
 * application.
 *
 * <p>Adds iOS-specific behaviour on top of
 * {@link WhatsAppMobileClientRegistration}:
 * <ul>
 *   <li>{@link #createRequest(String, String, String)} uses the iOS User-Agent
 *       derived from the store's device description and sends only the
 *       minimal HTTP headers the iOS app sends (no {@code WaMsysRequest},
 *       no explicit {@code Accept}, no per-request token header).</li>
 *   <li>{@link #getRequestVerificationCodeParameters(String)} populates
 *       only the short set of form fields the iOS app sends: method, SIM
 *       MCC/MNC, reason, and cellular signal strength.</li>
 *   <li>{@link #generateFdid()} formats the device family UUID in upper
 *       case, matching the iOS behaviour.</li>
 * </ul>
 *
 * <p>Each {@code /v2/code} request consults the configured
 * {@link WhatsAppDeviceAttestor.Ios} for an
 * {@link WhatsAppDeviceAttestor.Ios.AppAttestData}, and emits its two
 * base64-encoded CBOR payloads as the {@code attestation} and
 * {@code assertion} form fields before the outer {@code ENC=} envelope
 * wraps the body. The {@code H=} body suffix and {@code Authorization}
 * request header that the Android flow attaches are both absent on
 * iOS: the App Attest assertion is itself a device-held signature over
 * the {@code authkey} bytes, so it plays the role both Android legs
 * play together.
 *
 * @apiNote iOS-specific driver for the native mobile registration
 *          protocol. Not present in WA Web.
 * @see WhatsAppMobileClientRegistration
 */
public final class WhatsAppIosClientRegistration extends WhatsAppMobileClientRegistration {
    /**
     * Fallback attestor used when the builder's caller did not provide
     * one.
     */
    private static final WhatsAppDeviceAttestor.Ios NOOP =
            _ -> new WhatsAppDeviceAttestor.Ios.AppAttestData("", "");

    /**
     * The iOS attestor the registration consults before each outgoing
     * request. Never {@code null}: the constructor substitutes
     * {@link #NOOP} when the caller supplies {@code null}.
     */
    private final WhatsAppDeviceAttestor.Ios deviceAttestor;

    /**
     * Constructs a new iOS registration bound to the given store,
     * verification handler, and iOS device attestor.
     *
     * @param store the store carrying identity keys and phone number
     * @param verification the verification handler supplying the method
     *                     and the user-entered code
     * @param deviceAttestor the iOS device attestor, or {@code null} to
     *                       use the low-trust NOOP fallback
     */
    public WhatsAppIosClientRegistration(
            WhatsAppStore store,
            WhatsAppClientVerificationHandler.Mobile verification,
            WhatsAppDeviceAttestor.Ios deviceAttestor) {
        super(store, verification);
        this.deviceAttestor = Objects.requireNonNullElse(deviceAttestor, NOOP);
    }

    /**
     * Builds an HTTP POST request to the registration endpoint with the
     * pre-assembled body and the minimal iOS headers.
     *
     * <p>{@code authorizationHeader} is always {@code null} in the iOS
     * flow because the current iOS registration does not send an
     * {@code Authorization} header, but the parameter is still accepted
     * so the shared base-class contract stays platform-neutral.
     *
     * @param path the API sub-path ({@code /exist}, {@code /code},
     *             {@code /register})
     * @param body the fully-assembled request body
     * @param authorizationHeader ignored by the iOS flow; retained for
     *                            interface compatibility
     * @return a ready-to-send HTTP request
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
     * Returns {@link BodyAttestation#EMPTY} — the iOS flow does not
     * currently attach an {@code H=} suffix or an {@code Authorization}
     * header to the request.
     *
     * <p>When the iOS App Attest wire names are reverse-engineered and
     * the field is added to the body, this method will switch to
     * consulting {@link #deviceAttestor} and returning a populated
     * {@link BodyAttestation}.
     *
     * @param encBodyBytes the UTF-8 bytes of the base64 ENC body; unused
     * @return {@link BodyAttestation#EMPTY}
     */
    @Override
    protected BodyAttestation attestBody(byte[] encBodyBytes) {
        return BodyAttestation.EMPTY;
    }

    /**
     * Returns the short set of iOS-specific form fields that the
     * {@code /code} endpoint expects in addition to the shared
     * registration parameters.
     *
     * <p>The iOS client sends the verification method, empty SIM MCC/MNC,
     * an empty reason, and a cellular signal strength of {@code 1}. The
     * App Attest pair is not emitted here because it is added on every
     * attested endpoint by {@link #attestationFields()}.
     *
     * @param method the verification method chosen by the user
     * @return the alternating name/value form parameters
     */
    @Override
    protected String[] getRequestVerificationCodeParameters(String method) {
        return new String[]{
                "method", method,
                "sim_mcc", "000",
                "sim_mnc", "000",
                "reason", "",
                "cellular_strength", "1"
        };
    }

    /**
     * Returns the App Attest pair ({@code attestation}, {@code assertion})
     * produced by the configured {@link WhatsAppDeviceAttestor.Ios},
     * ready for injection into every attested request body.
     *
     * <p>{@code attestation} is the base64-encoded CBOR attestation
     * object produced by
     * {@code DCAppAttestService.attestKey:clientDataHash:};
     * {@code assertion} is the base64-encoded CBOR assertion object
     * produced by
     * {@code DCAppAttestService.generateAssertion:clientDataHash:}. The
     * server extracts the keyId from the attestation object's CBOR
     * {@code authData.credentialId}, so no separate key-id field is
     * emitted.
     *
     * <p>The attestor is called once per request so embedders that talk
     * to a remote App Attest minter may want to cache the attestation
     * inside their attestor for the lifetime of the registration
     * session. The NOOP attestor returns two empty strings, which the
     * registration server tolerates as a low-trust signal.
     *
     * @return the alternating name/value form parameters
     */
    @Override
    protected String[] attestationFields() {
        var attest = deviceAttestor.appAttest(store);
        return new String[]{
                "attestation", attest.attestation(),
                "assertion", attest.assertion()
        };
    }

    /**
     * Returns the device family identifier formatted as an upper-case UUID,
     * matching the iOS client's {@code fdid} scheme.
     *
     * @return the upper-case UUID string
     */
    @Override
    protected String generateFdid() {
        return store.fdid().toString().toUpperCase(Locale.ROOT);
    }
}
