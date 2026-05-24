package com.github.auties00.cobalt.pairing;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

/**
 * Lifecycle phase of the companion-side alt-device-linking handshake.
 *
 * @apiNote
 * Internal state-machine label for {@link CompanionPairingService}.
 * Embedders do not observe this enum; transitions are guarded
 * internally and surface only as success or as a
 * {@link com.github.auties00.cobalt.exception.WhatsAppRegistrationException}
 * raised from {@link CompanionPairingService#start} or
 * {@link CompanionPairingService#handlePrimaryHello}.
 *
 * @implNote
 * This implementation mirrors WA Web's internal
 * {@code AltPairingStage} enum ({@code NotStarted},
 * {@code Initialized}, {@code AfterSendCompanionHello},
 * {@code AfterSendCompanionFinish}). The state machine advances
 * monotonically except for one regression from
 * {@link #AFTER_SEND_COMPANION_FINISH} back to
 * {@link #AFTER_SEND_COMPANION_HELLO} when a repeated
 * {@code primary_hello} forces the ADV secret to be regenerated, which
 * matches WA Web's {@code regenerateAdvSecret} branch in
 * {@code handlePrimaryHelloInternal}.
 */
@WhatsAppWebModule(moduleName = "WAWebAltDeviceLinkingApi")
enum CompanionPairingStage {
    /**
     * No pairing flow has been started, or the cached state has been
     * cleared after a completed or aborted handshake.
     *
     * @apiNote
     * The constructor-initial value and the value left behind by
     * {@link CompanionPairingService#start} when the pre-flight checks
     * fail.
     */
    NOT_STARTED,

    /**
     * Pairing has been initialized with a phone JID and generation
     * timestamp, but the {@code companion_hello} IQ has not yet been
     * transmitted.
     *
     * @apiNote
     * Transient stage held only during
     * {@link CompanionPairingService#start} between cache reset and
     * IQ dispatch; observable from another thread only as a brief
     * window since the operation is synchronized.
     */
    INITIALIZED,

    /**
     * {@code companion_hello} has been sent and the server has returned
     * the {@code link_code_pairing_ref}.
     *
     * @apiNote
     * Steady-state phase while the companion waits for the primary
     * device to scan or type the pairing code and deliver the
     * {@code primary_hello} notification consumed by
     * {@link CompanionPairingService#handlePrimaryHello}.
     */
    AFTER_SEND_COMPANION_HELLO,

    /**
     * {@code companion_finish} has been sent and acknowledged.
     *
     * @apiNote
     * Terminal stage from the companion's perspective; the ADV secret
     * has been persisted via
     * {@link com.github.auties00.cobalt.store.WhatsAppStore#setAdvSecretKey}.
     * A repeat {@code primary_hello} can still rewind the state back
     * to {@link #AFTER_SEND_COMPANION_HELLO} after the ADV secret is
     * regenerated, up to
     * {@code MAX_PRIMARY_HELLO_ATTEMPTS} times per code.
     */
    AFTER_SEND_COMPANION_FINISH
}
