package com.github.auties00.cobalt.client;

/**
 * Platform-specific device-attestation payload produced by a
 * {@link WhatsAppDeviceIntegritySupplier}.
 *
 * <p>The mobile WhatsApp client proves to the registration server that the
 * request originates from a real, Google- or Apple-certified device by
 * attaching a signed attestation token. The token's form differs per
 * platform: Android clients embed a Google Play Integrity JWS under the
 * {@code gpia} form field plus three adjacent Google Mobile Services tokens
 * under {@code _gg}, {@code _gi}, and {@code _gp}; iOS clients embed an
 * Apple App Attest assertion together with the key identifier it was
 * produced under.
 *
 * <p>The sealed hierarchy guarantees that every result is one of the two
 * shapes the WhatsApp registration API understands, and that
 * platform-specific registration code can {@code switch} over the result
 * exhaustively without a default branch.
 *
 * @apiNote Cobalt does not produce attestation tokens itself: Play Integrity
 *          and App Attest both require platform-controlled cryptographic
 *          material that is unavailable to a plain JVM. Instances of this
 *          record are created by a
 *          {@link WhatsAppDeviceIntegritySupplier} the embedding
 *          application provides, typically by delegating the minting to a
 *          real device it controls.
 * @see WhatsAppDeviceIntegritySupplier
 */
public sealed interface WhatsAppDeviceIntegrityResult
        permits WhatsAppDeviceIntegrityResult.PlayIntegrity,
                WhatsAppDeviceIntegrityResult.AppAttest {

    /**
     * Android Play Integrity verdict and the three adjacent Google Mobile
     * Services tokens the native WhatsApp Android client submits alongside
     * it.
     *
     * <p>Each component maps one-to-one to a form field the Android
     * registration code path appends to every {@code /v2/exist},
     * {@code /v2/code}, {@code /v2/register}, {@code /v2/security}, and
     * {@code /v2/challenge} request body:
     * <ul>
     *   <li>{@code gpia} carries the Play Integrity JWS, base64url-encoded,
     *       signed by Google over a nonce the server re-derives from the
     *       request body;</li>
     *   <li>{@code _gg}, {@code _gi}, and {@code _gp} carry the Gaia,
     *       Instance, and Package Manager tokens the native client reads
     *       from Google Play Services.</li>
     * </ul>
     * Each component is never {@code null}: a supplier that cannot produce
     * a particular token returns the empty string for it, which the
     * registration server tolerates but treats as a low-trust signal.
     *
     * @param gpia the Play Integrity verdict token or empty string
     * @param gg the value for the {@code _gg} form field, or empty string
     * @param gi the value for the {@code _gi} form field, or empty string
     * @param gp the value for the {@code _gp} form field, or empty string
     */
    record PlayIntegrity(String gpia, String gg, String gi, String gp)
            implements WhatsAppDeviceIntegrityResult {
        /**
         * Compact canonical constructor that rejects {@code null} components
         * to keep the contract uniform with its iOS counterpart.
         *
         * @throws NullPointerException if any component is {@code null}
         */
        public PlayIntegrity {
            if (gpia == null || gg == null || gi == null || gp == null) {
                throw new NullPointerException("PlayIntegrity components must not be null");
            }
        }
    }

    /**
     * Apple App Attest assertion and the key identifier under which it was
     * produced.
     *
     * <p>The assertion binds the outgoing registration request to a key
     * pair that Apple's DeviceCheck attestation service has previously
     * certified as originating from a genuine iOS device. The server
     * rebuilds the assertion nonce from the request body and validates the
     * assertion against Apple's attestation root, so both fields must come
     * from the same attested key.
     *
     * @param attestation the base64-encoded App Attest assertion
     * @param keyId the base64-encoded App Attest key identifier the
     *              assertion was produced under
     */
    record AppAttest(String attestation, String keyId)
            implements WhatsAppDeviceIntegrityResult {
        /**
         * Compact canonical constructor that rejects {@code null} components
         * to keep the contract uniform with its Android counterpart.
         *
         * @throws NullPointerException if any component is {@code null}
         */
        public AppAttest {
            if (attestation == null || keyId == null) {
                throw new NullPointerException("AppAttest components must not be null");
            }
        }
    }
}
