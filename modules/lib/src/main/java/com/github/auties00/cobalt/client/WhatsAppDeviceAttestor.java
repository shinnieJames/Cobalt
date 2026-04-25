package com.github.auties00.cobalt.client;

import com.github.auties00.cobalt.store.WhatsAppStore;

/**
 * Produces the platform-specific attestation payloads that the mobile
 * registration flow embeds into every outgoing request body and request
 * header.
 *
 * <p>The native WhatsApp mobile clients attach two distinct
 * hardware-rooted proofs to each registration request:
 * <ol>
 *   <li>a <b>verdict token</b> minted by the platform vendor's attestation
 *       service (Google Play Integrity on Android, Apple App Attest on
 *       iOS), carried as form fields alongside the outer encrypted body;
 *       and</li>
 *   <li>a <b>device-held signature</b> over the outer encrypted body
 *       produced by a key that lives in the platform's trusted execution
 *       environment (Android Keystore on Android, Apple Secure Enclave on
 *       iOS), together with the attestation certificate chain for that
 *       key.</li>
 * </ol>
 * Both proofs transitively chain back to a Google- or Apple-controlled
 * root certificate and therefore cannot be produced off-device: the
 * trusted execution environment holds the private key material, and the
 * vendor's backend signs the verdict. Cobalt consequently cannot mint
 * either payload itself. This sealed interface is the seam through which
 * an embedding application delegates both jobs to a real device it
 * controls.
 *
 * <p>The sealed hierarchy pins each attestor to a single platform at
 * compile time:
 * <ul>
 *   <li>{@link Android} is used with any
 *       {@link com.github.auties00.cobalt.model.device.pairing.ClientPlatformType#ANDROID}
 *       or {@link com.github.auties00.cobalt.model.device.pairing.ClientPlatformType#ANDROID_BUSINESS}
 *       device and produces {@link Android.PlayIntegrityData} plus a
 *       {@link Android.KeystoreAttestation};</li>
 *   <li>{@link Ios} is used with any
 *       {@link com.github.auties00.cobalt.model.device.pairing.ClientPlatformType#IOS}
 *       or {@link com.github.auties00.cobalt.model.device.pairing.ClientPlatformType#IOS_BUSINESS}
 *       device and produces {@link Ios.AppAttestData}.</li>
 * </ul>
 * Both sub-interfaces are {@code non-sealed} so embedders can supply
 * freely. Neither is a {@link java.lang.FunctionalInterface} — Android
 * attestors expose two methods, and future platform parity on iOS may
 * add additional ones.
 *
 * <p>The record types each sub-interface consumes or produces are
 * nested directly under that sub-interface: the Android records live
 * under {@link Android}, the iOS records live under {@link Ios}. This
 * mirrors the natural one-platform-per-record ownership and keeps
 * platform-specific types out of the generic parent's namespace.
 *
 * @apiNote Implementations are not required to be stateless or
 *          thread-safe: the registration code calls each method
 *          sequentially from the thread that drives the registration
 *          ceremony and never concurrently.
 */
public sealed interface WhatsAppDeviceAttestor
        permits WhatsAppDeviceAttestor.Android, WhatsAppDeviceAttestor.Ios {

    /**
     * Attestor for an Android mobile registration.
     *
     * <p>Android requires the two attestation legs the native client
     * sends today: the Play Integrity verdict triple, and a TEE-backed
     * signature over the encrypted body together with that key's
     * certificate chain. The platform-specific record types
     * {@link PlayIntegrityData} and {@link KeystoreAttestation} live
     * directly under this interface.
     */
    non-sealed interface Android extends WhatsAppDeviceAttestor {
        /**
         * Produces the Google Play Integrity verdict bound to the current
         * registration request.
         *
         * <p>Called by the Android registration code exactly once per
         * outgoing {@code /v2/*} request that needs attestation (that is,
         * every endpoint except the two funnel-log endpoints). The four
         * fields of the result map directly to the {@code gpia},
         * {@code _gg}, {@code _gi}, and {@code _gp} form fields of the
         * outgoing body, before the body is AES-GCM-sealed into the outer
         * {@code ENC=} envelope.
         *
         * @param store the live registration store carrying the identity
         *              keys and phone number the verdict is bound to;
         *              never {@code null}
         * @return the Play Integrity data tuple; never {@code null}
         * @throws RuntimeException if the attestor cannot produce a
         *                          verdict; the registration code treats
         *                          any throw as a fatal registration
         *                          failure
         */
        PlayIntegrityData playIntegrity(WhatsAppStore store);

        /**
         * Signs the outer AES-GCM-encrypted body with a TEE-backed
         * Android Keystore key and returns both the signature and the
         * key's attestation certificate chain.
         *
         * <p>Called by the Android registration code after the outer
         * encryption step, on every request that needs attestation. The
         * signature bytes are hex-encoded and appended to the body as the
         * {@code H=} form field; the certificate chain is base64-encoded
         * and sent as the {@code Authorization:} request header. The
         * server re-derives the chain's expected attestation structure
         * and verifies that its root is the Google hardware attestation
         * root CA.
         *
         * @param store the live registration store; never {@code null}
         * @param encBody the base64 bytes of the outer {@code ENC=}
         *                envelope that the signature must cover; never
         *                {@code null}
         * @return the signature and certificate chain produced by the
         *         device's hardware-backed key; never {@code null}
         * @throws RuntimeException if the attestor cannot produce a
         *                          signature
         */
        KeystoreAttestation sign(WhatsAppStore store, byte[] encBody);

        /**
         * Google Play Integrity verdict triple the Android registration
         * code appends to every attested request body.
         *
         * <p>Each component maps one-to-one to a form field the Android
         * registration code appends before AES-GCM sealing:
         * <ul>
         *   <li>{@code gpia} carries the Play Integrity JWS,
         *       base64url-encoded, signed by Google over a nonce the
         *       server re-derives from the request body;</li>
         *   <li>{@code _gg}, {@code _gi}, and {@code _gp} carry the
         *       Gaia, Instance, and Package Manager tokens the native
         *       client reads from Google Play Services.</li>
         * </ul>
         * Each component is never {@code null}; attestors that cannot
         * produce a particular token should return the empty string for
         * it, which the registration server tolerates but treats as a
         * low-trust signal.
         *
         * @param gpia the Play Integrity verdict token or empty string
         * @param gg the value for the {@code _gg} form field, or empty
         *           string
         * @param gi the value for the {@code _gi} form field, or empty
         *           string
         * @param gp the value for the {@code _gp} form field, or empty
         *           string
         */
        record PlayIntegrityData(String gpia, String gg, String gi, String gp) {
            /**
             * Compact canonical constructor that rejects {@code null}
             * components so the registration code never emits literal
             * {@code null} strings on the wire.
             *
             * @throws NullPointerException if any component is {@code null}
             */
            public PlayIntegrityData {
                if (gpia == null || gg == null || gi == null || gp == null) {
                    throw new NullPointerException("PlayIntegrityData components must not be null");
                }
            }
        }

        /**
         * Result of {@link Android#sign(WhatsAppStore, byte[])}: the
         * TEE-backed signature over the outer encrypted body together
         * with the attestation certificate chain for the signing key.
         *
         * @param signature the raw signature bytes produced by the
         *                  keystore; hex-encoded by the registration
         *                  code and appended as the {@code H=} form
         *                  field
         * @param certificateChain the raw certificate chain bytes for
         *                         the signing key; base64-encoded by
         *                         the registration code and sent as the
         *                         {@code Authorization:} header
         */
        record KeystoreAttestation(byte[] signature, byte[] certificateChain) {
            /**
             * Compact canonical constructor that rejects {@code null}
             * components.
             *
             * @throws NullPointerException if either component is
             *                              {@code null}
             */
            public KeystoreAttestation {
                if (signature == null || certificateChain == null) {
                    throw new NullPointerException("KeystoreAttestation components must not be null");
                }
            }
        }
    }

    /**
     * Attestor for an iOS mobile registration.
     *
     * <p>iOS registration attaches a single App Attest assertion. The
     * App Attest key identifier and the Secure-Enclave-produced
     * assertion together constitute the proof that the request
     * originates from a genuine Apple-certified device. The
     * platform-specific record type {@link AppAttestData} lives
     * directly under this interface.
     */
    non-sealed interface Ios extends WhatsAppDeviceAttestor {
        /**
         * Produces the Apple App Attest assertion bound to the current
         * registration request.
         *
         * <p>Called by the iOS registration code exactly once per
         * outgoing {@code /v2/*} request that needs attestation. The
         * fields of the result map directly to the iOS-specific form
         * fields the native client appends to the body.
         *
         * @param store the live registration store; never {@code null}
         * @return the App Attest data tuple; never {@code null}
         * @throws RuntimeException if the attestor cannot produce an
         *                          assertion
         */
        AppAttestData appAttest(WhatsAppStore store);

        /**
         * Pair of Apple App Attest payloads the iOS registration code
         * appends to every attested request body: the CBOR attestation
         * object produced by
         * {@code DCAppAttestService.attestKey:clientDataHash:}, and the
         * CBOR assertion object produced by
         * {@code DCAppAttestService.generateAssertion:clientDataHash:}
         * under the same {@code keyId}.
         *
         * <p>Both pieces are shipped as base64-encoded strings and map
         * one-to-one to the form fields the native iOS client appends
         * to the outgoing body before the outer AES-GCM envelope step:
         * <ul>
         *   <li>{@code attestation} — base64 of the CBOR attestation
         *       object. The CBOR structure carries the {@code keyId}
         *       internally in {@code authData.credentialId}, so the
         *       server does not expect a separate key-id form field.
         *       The native iOS client caches this value across requests
         *       in the same registration session (Keychain item
         *       {@code app-attestation-reg-<keyId>}) and only re-mints
         *       via {@code attestKey:} when the cached entry has
         *       expired.</li>
         *   <li>{@code assertion} — base64 of the CBOR assertion object.
         *       Freshly produced per request by signing a
         *       {@code clientDataHash} (on the native iOS client, the
         *       SHA-256 of the outgoing {@code authkey} form-field
         *       bytes, which is a stable per-installation proof of
         *       identity-key possession).</li>
         * </ul>
         * Both components are never {@code null}; attestors that cannot
         * produce a particular payload should return the empty string
         * for it, which the registration server tolerates but treats as
         * a low-trust signal.
         *
         * @param attestation the base64-encoded CBOR attestation object
         *                    produced by
         *                    {@code DCAppAttestService.attestKey}
         * @param assertion the base64-encoded CBOR assertion object
         *                  produced by
         *                  {@code DCAppAttestService.generateAssertion}
         */
        record AppAttestData(String attestation, String assertion) {
            /**
             * Compact canonical constructor that rejects {@code null}
             * components.
             *
             * @throws NullPointerException if either component is
             *                              {@code null}
             */
            public AppAttestData {
                if (attestation == null || assertion == null) {
                    throw new NullPointerException("AppAttestData components must not be null");
                }
            }
        }
    }
}
