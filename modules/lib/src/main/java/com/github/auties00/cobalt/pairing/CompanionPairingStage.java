package com.github.auties00.cobalt.pairing;

/**
 * Phase tag describing where the handshake is in its lifecycle. The
 * state machine advances monotonically from {@link #NOT_STARTED} to
 * {@link #AFTER_SEND_COMPANION_FINISH}.
 *
 * @implNote WAWebAltDeviceLinkingApi: $InternalEnum.Mirrored(["NotStarted",
 * "Initialized", "AfterSendCompanionHello", "AfterSendCompanionFinish"])
 */
enum CompanionPairingStage {
    NOT_STARTED,
    INITIALIZED,
    AFTER_SEND_COMPANION_HELLO,
    AFTER_SEND_COMPANION_FINISH
}
