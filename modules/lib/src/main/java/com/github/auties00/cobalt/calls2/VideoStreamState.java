package com.github.auties00.cobalt.calls2;

/**
 * Enumerates the per-participant video stream states tracked by the wa-voip engine.
 *
 * <p>This is the {@code kVideoState*} machine the native engine drives for every
 * participant's video stream: it covers the simple on/off lifecycle (disabled,
 * enabled, paused, stopped), the full video-upgrade negotiation (request, accept,
 * reject, cancel, and their timeout-driven variants, plus the v2 request used by
 * newer peers), the avatar-codec enablement signalled on XR2D devices, and the
 * terminal error sentinel. The engine projects a participant's video state to one of
 * these values when reading it back through the participant view; a participant with
 * no resolved detail block reads as {@link #UNKNOWN_PEER}.
 *
 * <p>Each constant carries the exact {@link #wireOrdinal() wire ordinal} the engine
 * stores and transmits. The ordinals are NOT contiguous: they occupy {@code 0..12}
 * densely and then jump to {@code 20} for {@link #ERROR}, leaving the {@code 13..19}
 * slots unused (they are padding in the native lookup table). {@link #ENABLED} is the
 * video-on state and {@link #DISABLED} the video-off state; {@link #PAUSED} and
 * {@link #STOPPED} are both treated as "not actively sending" by the engine, which
 * tests them together via the bit pattern {@code (value & ~4) == 2}. This enum is the
 * real video-upgrade mechanism and supersedes the deprecated
 * {@code CallInteraction.VideoUpgradeRequest} string facade.
 *
 * @implNote This implementation ports the 21-slot {@code kVideoState*} name table at
 * data segment offset {@code 0x109214} of the wa-voip WASM module {@code ff-tScznZ8P}
 * (the array dereferenced by the video-state stringifier in {@code video_state.cc}).
 * The recovered table maps ordinal {@code 0} to {@code kVideoStateDisabled} through
 * ordinal {@code 12} to {@code kVideoStateXr2dCodecAvatarEnabled}, fills slots
 * {@code 13..19} with the {@code kVideoStateDisabled} pointer as unused padding, and
 * places {@code kVideoStateError} at ordinal {@code 20}; the paused-or-stopped test
 * {@code (value & ~4) == 2} is taken from the participant-view predicate in the same
 * translation unit, and the {@code 20}-valued unknown default from the view's
 * video-state getter.
 */
public enum VideoStreamState {
    /**
     * Indicates the participant's camera is off and no video stream is active.
     *
     * <p>This is the default state for a participant that has not enabled video. It is
     * also the value the native name table assigns to every unused padding slot, so it
     * is the only state with multiple table entries pointing at its name.
     */
    DISABLED(0),

    /**
     * Indicates the participant's camera is on and a video stream is active.
     *
     * <p>This is the state read back for a call extension (a bot or avatar stream),
     * which the engine always treats as video-enabled.
     */
    ENABLED(1),

    /**
     * Indicates the participant's video stream is temporarily paused.
     *
     * <p>The engine groups this with {@link #STOPPED} as a non-sending state through the
     * predicate {@code (value & ~4) == 2}; a paused stream can resume without a fresh
     * upgrade negotiation.
     */
    PAUSED(2),

    /**
     * Indicates a request to upgrade an audio-only call to video has been issued.
     *
     * <p>This is the initiating state of the video-upgrade handshake; the peer answers
     * with {@link #UPGRADE_ACCEPT} or {@link #UPGRADE_REJECT}, and the request expires
     * via {@link #UPGRADE_REJECT_BY_TIMEOUT} if unanswered.
     */
    UPGRADE_REQUEST(3),

    /**
     * Indicates a peer accepted a pending video-upgrade request.
     *
     * <p>Acceptance transitions both sides toward an active video stream.
     */
    UPGRADE_ACCEPT(4),

    /**
     * Indicates a peer declined a pending video-upgrade request.
     */
    UPGRADE_REJECT(5),

    /**
     * Indicates the participant's video stream has been stopped.
     *
     * <p>The engine groups this with {@link #PAUSED} as a non-sending state through the
     * predicate {@code (value & ~4) == 2}; it is the value set when a peer's video is
     * observed to have stopped.
     */
    STOPPED(6),

    /**
     * Indicates a pending video-upgrade request was rejected because it timed out.
     *
     * <p>This is the timeout-driven counterpart of {@link #UPGRADE_REJECT}: the engine
     * reaches it when the requester receives no answer within the upgrade window.
     */
    UPGRADE_REJECT_BY_TIMEOUT(7),

    /**
     * Indicates the requester cancelled a pending video-upgrade request.
     *
     * <p>This is the requester-side counterpart of {@link #UPGRADE_REJECT}: the side that
     * issued {@link #UPGRADE_REQUEST} withdraws it before the peer answers.
     */
    UPGRADE_CANCEL(8),

    /**
     * Indicates a pending video-upgrade request was cancelled because it timed out.
     *
     * <p>This is the timeout-driven counterpart of {@link #UPGRADE_CANCEL}.
     */
    UPGRADE_CANCEL_BY_TIMEOUT(9),

    /**
     * Indicates the video state belongs to a peer the engine cannot resolve.
     *
     * <p>This is the value read back for a participant whose detail block is absent; the
     * native participant-view video-state getter returns this ordinal as its default.
     */
    UNKNOWN_PEER(10),

    /**
     * Indicates a video-upgrade request issued through the newer v2 upgrade flow.
     *
     * <p>Peers that advertise the v2 capability use this in place of
     * {@link #UPGRADE_REQUEST}; the rest of the handshake is unchanged.
     */
    UPGRADE_REQUEST_V2(11),

    /**
     * Indicates the participant's video stream is an XR2D codec avatar stream.
     *
     * <p>This is the enabled state for a participant rendering through the XR2D
     * avatar-codec path rather than a standard camera capture.
     */
    XR2D_CODEC_AVATAR_ENABLED(12),

    /**
     * Indicates the participant's video stream is in a terminal error state.
     *
     * <p>This is the only state whose ordinal lies outside the dense {@code 0..12} band;
     * the native name table places it at ordinal {@code 20}.
     */
    ERROR(20);

    /**
     * Caches the constant array so the {@link #ofWireOrdinal(int)} decode scan does not pay the
     * defensive-clone cost of {@link #values()} on every video-state lookup.
     */
    private static final VideoStreamState[] VALUES = values();

    /**
     * The integer value the wa-voip engine stores and transmits for this state.
     */
    private final int wireOrdinal;

    /**
     * Constructs a state constant bound to its engine wire ordinal.
     *
     * @param wireOrdinal the integer value the engine uses for this state
     */
    VideoStreamState(int wireOrdinal) {
        this.wireOrdinal = wireOrdinal;
    }

    /**
     * Returns the integer value the wa-voip engine stores and transmits for this state.
     *
     * <p>The returned value is the engine ordinal, NOT the Java {@link #ordinal()}; the
     * two diverge for {@link #ERROR}, whose wire ordinal is {@code 20} while its Java
     * ordinal is {@code 13}.
     *
     * @return the engine wire ordinal for this state
     */
    public int wireOrdinal() {
        return wireOrdinal;
    }

    /**
     * Returns the state whose {@linkplain #wireOrdinal() wire ordinal} equals the given
     * value.
     *
     * <p>Any value that does not correspond to a defined state, including the unused
     * {@code 13..19} padding slots, resolves to {@link #UNKNOWN_PEER}, mirroring the
     * engine's treatment of an unresolved or out-of-range video state.
     *
     * @param wireOrdinal the engine wire ordinal to resolve
     * @return the matching state, or {@link #UNKNOWN_PEER} if no state matches
     */
    public static VideoStreamState ofWireOrdinal(int wireOrdinal) {
        for (var state : VALUES) {
            if (state.wireOrdinal == wireOrdinal) {
                return state;
            }
        }
        return UNKNOWN_PEER;
    }

    /**
     * Returns whether this state represents a stream that is paused or stopped.
     *
     * <p>The result is {@code true} for exactly {@link #PAUSED} and {@link #STOPPED},
     * matching the engine predicate {@code (wireOrdinal & ~4) == 2} that treats both as
     * non-sending video states.
     *
     * @return {@code true} if this state is {@link #PAUSED} or {@link #STOPPED},
     *         {@code false} otherwise
     */
    public boolean isPausedOrStopped() {
        return (wireOrdinal & ~4) == 2;
    }
}
