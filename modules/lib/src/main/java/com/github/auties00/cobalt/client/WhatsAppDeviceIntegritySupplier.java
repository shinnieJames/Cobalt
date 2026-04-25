package com.github.auties00.cobalt.client;

import com.github.auties00.cobalt.store.WhatsAppStore;

/**
 * Produces a platform-specific device-attestation payload that the mobile
 * registration flow embeds into the outgoing request body.
 *
 * <p>Cobalt cannot mint attestations itself: both Google Play Integrity
 * and Apple App Attest require cryptographic material that only the
 * platform vendor's hardware and operating system can produce. This
 * sealed interface is the seam through which an embedding application
 * delegates the work to a real device it controls.
 *
 * <p>The two permitted sub-interfaces nail down a supplier's platform at
 * compile time:
 * <ul>
 *   <li>{@link Android} produces Google Play Integrity results;</li>
 *   <li>{@link Ios} produces Apple App Attest results.</li>
 * </ul>
 * Both sub-interfaces are {@code non-sealed} so embedders can implement
 * them freely, and both are {@link java.lang.FunctionalInterface
 * functional interfaces} so a target-typed lambda expression is enough
 * to supply one. Because each sub-interface narrows the return type of
 * {@link #mint(WhatsAppStore)} covariantly, a lambda that returns a
 * {@link WhatsAppDeviceIntegrityResult.PlayIntegrity} can only satisfy
 * {@link Android}, and a lambda that returns a
 * {@link WhatsAppDeviceIntegrityResult.AppAttest} can only satisfy
 * {@link Ios}.
 *
 * @apiNote Implementations are not required to be stateless or
 *          thread-safe: the registration code calls
 *          {@link #mint(WhatsAppStore)} sequentially from the thread
 *          that drives the registration ceremony and never
 *          concurrently.
 * @see WhatsAppDeviceIntegrityResult
 */
public sealed interface WhatsAppDeviceIntegritySupplier
        permits WhatsAppDeviceIntegritySupplier.Android,
                WhatsAppDeviceIntegritySupplier.Ios {

    /**
     * Produces an attestation result bound to the current registration
     * request.
     *
     * <p>Implementations read the phone number, identity keys, device
     * identifier, FDID, and any other stable credential they need from
     * the supplied store and return a freshly-minted token. The
     * registration code appends the token components to the request
     * body immediately after this call returns, so implementations that
     * talk to a remote minter should prefer short-lived, per-request
     * tokens over cached ones.
     *
     * @param store the live registration store carrying the identity
     *              keys and phone number the attestation is bound to;
     *              never {@code null}
     * @return the platform-specific attestation result; never
     *         {@code null}
     * @throws RuntimeException if the supplier cannot produce a token;
     *                          the registration code treats any throw
     *                          as a fatal registration failure
     */
    WhatsAppDeviceIntegrityResult mint(WhatsAppStore store);

    /**
     * Supplier that mints Google Play Integrity verdicts for the
     * Android mobile registration flow.
     *
     * <p>The narrowed return type of {@link #mint(WhatsAppStore)}
     * guarantees at compile time that an Android supplier can only
     * produce a {@link WhatsAppDeviceIntegrityResult.PlayIntegrity}.
     */
    @FunctionalInterface
    non-sealed interface Android extends WhatsAppDeviceIntegritySupplier {
        /**
         * Produces a Play Integrity verdict bound to the current
         * registration request.
         *
         * @param store the live registration store; never {@code null}
         * @return the Play Integrity attestation result; never
         *         {@code null}
         * @throws RuntimeException if the supplier cannot produce a
         *                          token
         */
        @Override
        WhatsAppDeviceIntegrityResult.PlayIntegrity mint(WhatsAppStore store);
    }

    /**
     * Supplier that mints Apple App Attest assertions for the iOS
     * mobile registration flow.
     *
     * <p>The narrowed return type of {@link #mint(WhatsAppStore)}
     * guarantees at compile time that an iOS supplier can only produce
     * a {@link WhatsAppDeviceIntegrityResult.AppAttest}.
     */
    @FunctionalInterface
    non-sealed interface Ios extends WhatsAppDeviceIntegritySupplier {
        /**
         * Produces an App Attest assertion bound to the current
         * registration request.
         *
         * @param store the live registration store; never {@code null}
         * @return the App Attest attestation result; never {@code null}
         * @throws RuntimeException if the supplier cannot produce a
         *                          token
         */
        @Override
        WhatsAppDeviceIntegrityResult.AppAttest mint(WhatsAppStore store);
    }
}
