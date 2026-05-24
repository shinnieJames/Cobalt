package com.github.auties00.cobalt.client;

import com.github.auties00.cobalt.store.WhatsAppStore;

/**
 * Produces a platform-specific device-attestation payload that the
 * mobile registration flow embeds into the outgoing request body.
 *
 * @apiNote
 * Cobalt cannot mint attestations itself: both Google Play Integrity
 * and Apple App Attest require cryptographic material that only the
 * platform vendor's hardware and operating system can produce. This
 * sealed interface is the seam through which an embedding application
 * delegates the work to a real device it controls. Pick the permitted
 * sub-interface that matches the registering device:
 * <ul>
 *   <li>{@link Android} produces Google Play Integrity results;</li>
 *   <li>{@link Ios} produces Apple App Attest results.</li>
 * </ul>
 * Both sub-interfaces are {@code non-sealed} and
 * {@link FunctionalInterface functional}, so a target-typed lambda is
 * enough to supply one. The covariantly narrowed return types ensure
 * an {@link Android} supplier can only produce a
 * {@link WhatsAppDeviceIntegrityResult.PlayIntegrity} and an
 * {@link Ios} supplier can only produce a
 * {@link WhatsAppDeviceIntegrityResult.AppAttest}.
 *
 * @implNote
 * This implementation does not require thread-safety: the registration
 * code calls {@link #mint(WhatsAppStore)} sequentially from the
 * thread that drives the registration ceremony.
 *
 * @see WhatsAppDeviceIntegrityResult
 */
public sealed interface WhatsAppDeviceIntegritySupplier
        permits WhatsAppDeviceIntegritySupplier.Android,
                WhatsAppDeviceIntegritySupplier.Ios {

    /**
     * Produces an attestation result bound to the current registration
     * request.
     *
     * @apiNote
     * Implementations read the phone number, identity keys, device
     * identifier, FDID, and any other stable credential they need from
     * the supplied store and return a freshly-minted token. The
     * registration code appends the token components to the request
     * body immediately after this call returns, so implementations
     * that talk to a remote minter should prefer short-lived,
     * per-request tokens over cached ones.
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
     * A supplier that mints Google Play Integrity verdicts for the
     * Android mobile registration flow.
     *
     * @apiNote
     * Wired in via the Android registration variants on
     * {@link WhatsAppClientBuilder}. The narrowed return type of
     * {@link #mint(WhatsAppStore)} guarantees at compile time that an
     * Android supplier can only produce a
     * {@link WhatsAppDeviceIntegrityResult.PlayIntegrity}.
     */
    @FunctionalInterface
    non-sealed interface Android extends WhatsAppDeviceIntegritySupplier {
        /**
         * {@inheritDoc}
         *
         * @apiNote
         * Override this to mint a Play Integrity verdict bound to the
         * current registration request, typically by calling out to a
         * physical Android device.
         *
         * @return the Play Integrity attestation result; never
         *         {@code null}
         */
        @Override
        WhatsAppDeviceIntegrityResult.PlayIntegrity mint(WhatsAppStore store);
    }

    /**
     * A supplier that mints Apple App Attest assertions for the iOS
     * mobile registration flow.
     *
     * @apiNote
     * Wired in via the iOS registration variants on
     * {@link WhatsAppClientBuilder}. The narrowed return type of
     * {@link #mint(WhatsAppStore)} guarantees at compile time that an
     * iOS supplier can only produce a
     * {@link WhatsAppDeviceIntegrityResult.AppAttest}.
     */
    @FunctionalInterface
    non-sealed interface Ios extends WhatsAppDeviceIntegritySupplier {
        /**
         * {@inheritDoc}
         *
         * @apiNote
         * Override this to mint an App Attest assertion bound to the
         * current registration request, typically by calling out to a
         * physical iOS device.
         *
         * @return the App Attest attestation result; never
         *         {@code null}
         */
        @Override
        WhatsAppDeviceIntegrityResult.AppAttest mint(WhatsAppStore store);
    }
}
