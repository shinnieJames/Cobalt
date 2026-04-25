package com.github.auties00.cobalt.client.registration;

import com.github.auties00.cobalt.client.WhatsAppClientVerificationHandler;
import com.github.auties00.cobalt.client.WhatsAppDeviceAttestor;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Mobile registration driver that impersonates the native Android
 * WhatsApp application.
 *
 * <p>Adds Android-specific behaviour on top of
 * {@link WhatsAppMobileClientRegistration}:
 * <ul>
 *   <li>{@link #createRequest(String, String, String)} attaches the Android
 *       User-Agent derived from the store's device description plus the
 *       {@code WaMsysRequest} and {@code request_token} headers the
 *       Android app sends.</li>
 *   <li>{@link #getRequestVerificationCodeParameters(String)} populates
 *       the long list of Android-only form fields that the
 *       {@code /code} endpoint expects (SIM MCC/MNC, advertising ID,
 *       backup token, cellular signal strength, client metrics, and so
 *       on) and now appends the Play Integrity attestation triple
 *       ({@code gpia}, {@code _gg}, {@code _gi}, {@code _gp}) produced
 *       by the configured
 *       {@link WhatsAppDeviceAttestor.Android}.</li>
 *   <li>{@link #generateFdid()} formats the device family UUID in lower
 *       case, matching the Android behaviour.</li>
 * </ul>
 *
 * @apiNote Android-specific driver for the native mobile registration
 *          protocol. Not present in WA Web.
 * @see WhatsAppMobileClientRegistration
 */
public final class WhatsAppAndroidClientRegistration extends WhatsAppMobileClientRegistration {
    /**
     * Fallback attestor used when the builder's caller did not provide
     * one. Emits the empty-attestation payload that the registration
     * server tolerates as a low-trust signal, and produces a
     * zero-length signature plus empty certificate chain when the
     * keystore leg is invoked.
     */
    private static final WhatsAppDeviceAttestor.Android NOOP = new WhatsAppDeviceAttestor.Android() {
        @Override
        public WhatsAppDeviceAttestor.Android.PlayIntegrityData playIntegrity(WhatsAppStore store) {
            return new WhatsAppDeviceAttestor.Android.PlayIntegrityData("", "", "", "");
        }

        @Override
        public WhatsAppDeviceAttestor.Android.KeystoreAttestation sign(WhatsAppStore store, byte[] encBody) {
            return new WhatsAppDeviceAttestor.Android.KeystoreAttestation(new byte[0], new byte[0]);
        }
    };

    /**
     * The Android attestor the registration consults before each
     * outgoing request. Never {@code null}: the constructor substitutes
     * {@link #NOOP} when the caller supplies {@code null}.
     */
    private final WhatsAppDeviceAttestor.Android deviceAttestor;

    /**
     * Constructs a new Android registration bound to the given store,
     * verification handler, and device attestor.
     *
     * @param store the store carrying identity keys and phone number
     * @param verification the verification handler supplying the method
     *                     and the user-entered code
     * @param deviceAttestor the Android device attestor, or {@code null}
     *                       to use the low-trust NOOP fallback
     */
    public WhatsAppAndroidClientRegistration(
            WhatsAppStore store,
            WhatsAppClientVerificationHandler.Mobile verification,
            WhatsAppDeviceAttestor.Android deviceAttestor) {
        super(store, verification);
        this.deviceAttestor = Objects.requireNonNullElse(deviceAttestor, NOOP);
    }

    /**
     * Builds an HTTP POST request to the registration endpoint with the
     * pre-assembled body and the Android-specific headers.
     *
     * <p>The {@code body} argument is already in its final wire form:
     * the base class has prepended the {@code ENC=} envelope marker and,
     * when an Android keystore signature was produced, appended the
     * {@code &H=<hex>} fragment. When {@code authorizationHeader} is
     * non-{@code null} the {@code Authorization} header carrying the
     * keystore attestation certificate chain is attached as well.
     *
     * @param path the API sub-path ({@code /exist}, {@code /code},
     *             {@code /register}, {@code /challenge}, {@code /security})
     * @param body the fully-assembled request body
     * @param authorizationHeader the {@code Authorization} header value,
     *                            or {@code null} to omit the header
     * @return a ready-to-send HTTP request
     */
    @Override
    protected HttpRequest createRequest(String path, String body, String authorizationHeader) {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create("%s%s".formatted(MOBILE_REGISTRATION_ENDPOINT, path)))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .header("User-Agent", store.device().toUserAgent(store.clientVersion()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "text/json")
                .header("WaMsysRequest", "1")
                .header("request_token", UUID.randomUUID().toString());
        if (authorizationHeader != null) {
            builder.header("Authorization", authorizationHeader);
        }
        return builder.build();
    }

    /**
     * Signs the base64 ENC body with the configured
     * {@link WhatsAppDeviceAttestor.Android} and packages the result
     * into the {@code H=} suffix and {@code Authorization} header.
     *
     * <p>The attestor's {@link WhatsAppDeviceAttestor.Android#sign sign}
     * call returns raw signature bytes and a raw certificate chain; this
     * method hex-encodes the signature (lowercase, no separator, the
     * format the WhatsApp server expects) and URL-safe-base64-encodes the
     * certificate chain without padding. When either component comes back
     * empty the low-trust NOOP attestor is in effect and
     * {@link BodyAttestation#EMPTY} is returned, which tells the base
     * class to skip both the {@code &H=} fragment and the header.
     *
     * @param encBodyBytes the UTF-8 bytes of the base64 ENC body to sign
     * @return the packaged signature and header value, or
     *         {@link BodyAttestation#EMPTY} when no real signature is
     *         available
     */
    @Override
    protected BodyAttestation attestBody(byte[] encBodyBytes) {
        var signed = deviceAttestor.sign(store, encBodyBytes);
        var signature = signed.signature();
        var chain = signed.certificateChain();
        if (signature.length == 0) {
            return BodyAttestation.EMPTY;
        }
        var hex = HexFormat.of().formatHex(signature);
        var auth = chain.length == 0
                ? null
                : Base64.getUrlEncoder().withoutPadding().encodeToString(chain);
        return new BodyAttestation(hex, auth);
    }

    /**
     * Returns the large set of Android-specific form fields that the
     * {@code /code} endpoint expects in addition to the shared
     * registration parameters.
     *
     * <p>The values simulate a plausible Android handset: empty SIM MCC/MNC,
     * network radio type {@code 1} (GSM), no roaming, 3.57 GB RAM, strong
     * cellular signal, and client metrics pretending the app has attempted
     * registration 20 times. The advertising ID and backup token are read
     * from the store so that they remain stable across the registration
     * flow. The Play Integrity quadruple is not emitted here because it is
     * added on every attested endpoint by
     * {@link #attestationFields()}.
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
                "mcc", "000",
                "mnc", "000",
                "feo2_query_status", "error_security_exception",
                "db", "1",
                "sim_type", "0",
                "recaptcha", "%7B%22stage%22%3A%22ABPROP_DISABLED%22%7D",
                "network_radio_type", "1",
                "prefer_sms_over_flash", "false",
                "simnum", "0",
                "airplane_mode_type", "0",
                "client_metrics", buildClientMetrics(),
                "mistyped", "7",
                "advertising_id", store.advertisingId().toString(),
                "hasinrc", "1",
                "roaming_type", "0",
                "device_ram", "3.57",
                "education_screen_displayed", "false",
                "pid", String.valueOf(ProcessHandle.current().pid()),
                "cellular_strength", "5",
                "backup_token", toUrlHex(store.backupToken()),
                "hasav", "2"
        };
    }

    /**
     * Returns the Play Integrity quadruple ({@code gpia}, {@code _gg},
     * {@code _gi}, {@code _gp}) produced by the configured
     * {@link WhatsAppDeviceAttestor.Android}, ready for injection into
     * every attested request body.
     *
     * <p>The attestor is called once per request so embedders that talk
     * to a remote Play Integrity minter may want to cache the verdict
     * inside their attestor for the lifetime of the registration
     * session. The NOOP attestor returns four empty strings, which the
     * registration server tolerates as a low-trust signal.
     *
     * @return the alternating name/value form parameters
     */
    @Override
    protected String[] attestationFields() {
        var attestation = deviceAttestor.playIntegrity(store);
        return new String[]{
                "gpia", attestation.gpia(),
                "_gg", attestation.gg(),
                "_gi", attestation.gi(),
                "_gp", attestation.gp()
        };
    }

    /**
     * Returns the device family identifier formatted as a lower-case UUID,
     * matching the Android client's {@code fdid} scheme.
     *
     * @return the lower-case UUID string
     */
    @Override
    protected String generateFdid() {
        return store.fdid().toString().toLowerCase(Locale.ROOT);
    }

    /**
     * Builds the percent-encoded {@code client_metrics} JSON payload
     * tracking the real attempt count driven by the base class's retry
     * loop.
     *
     * <p>Produces the same shape the native Android client emits: an
     * {@code attempts} integer and an {@code app_campaign_download_source}
     * string. Further fields the native client sometimes includes
     * (session identifier, onboarding gate state) are omitted.
     *
     * @return the percent-encoded JSON string
     */
    private String buildClientMetrics() {
        var json = "{\"attempts\":" + attempt
                + ",\"app_campaign_download_source\":\"google-play|unknown\"}";
        return URLEncoder.encode(json, StandardCharsets.UTF_8);
    }
}
