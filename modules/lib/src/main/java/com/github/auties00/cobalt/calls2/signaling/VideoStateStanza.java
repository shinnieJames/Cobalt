package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.calls2.VideoStreamState;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.StanzaBuilder;

import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Represents a {@code <video>} in-call action: the sender broadcasts a change in its video stream state.
 *
 * <p>A video-state action reports the sender's current {@link VideoStreamState}: the simple camera
 * on/off lifecycle (enabled, disabled, paused, stopped) as well as every step of the video-upgrade
 * negotiation (request, accept, reject, cancel, and their timeout variants). It carries the universal
 * call header and a numeric {@code state} attribute equal to the
 * {@linkplain VideoStreamState#wireOrdinal() wire ordinal} of the broadcast state.
 *
 * <p>On the wire the element is {@code <video call-id="..." call-creator="..." state="N"
 * device_orientation="0"/>}, with a {@code dec="H264"} attribute added while the camera is on.
 *
 * @implNote This implementation models the type-{@code 15} VideoState built by
 * {@code serialize_video_state} in the wa-voip WASM module {@code ff-tScznZ8P}
 * ({@code stanzas/media.cc}) ({@link Calls2SignalingType#VIDEO_STATE}); despite the internal
 * {@code video_state} name, the serialized {@code <call>} child is {@code <video>} (live-capture
 * confirmed), and the matching ack is taxonomy ordinal {@code 20}. The {@code state} attribute is the
 * engine {@code kVideoState*} ordinal carried by {@link VideoStreamState}, decoded through
 * {@link VideoStreamState#ofWireOrdinal(int)} so an unrecognized ordinal collapses to
 * {@link VideoStreamState#UNKNOWN_PEER}; {@code device_orientation} is always written and {@code dec}
 * carries the live decode codec while the camera is on. Attributes are stamped over the common header
 * written by {@code populate_common_call_attr} (fn11591): {@code call-id} (data offset {@code 0x888f9})
 * and {@code call-creator} (data offset {@code 0x45ea5}).
 *
 * @param callId      the call identifier; never {@code null}
 * @param callCreator the call creator's device JID; never {@code null}
 * @param state       the broadcast video stream state; never {@code null}
 * @see Calls2SignalingType#VIDEO_STATE
 * @see VideoStreamState
 */
public record VideoStateStanza(String callId, Jid callCreator, VideoStreamState state)
        implements InCallActionStanza {
    /**
     * The wire element tag for a video-state action; the type's internal name is {@code video_state} but
     * the serialized {@code <call>} child is {@code <video>}.
     */
    public static final String ELEMENT = "video";

    /**
     * The wire attribute naming the video stream state ordinal.
     */
    private static final String STATE_ATTRIBUTE = "state";

    /**
     * The wire attribute naming the camera orientation.
     */
    private static final String DEVICE_ORIENTATION_ATTRIBUTE = "device_orientation";

    /**
     * The wire attribute naming the active video decode codec, written only while the camera is on.
     */
    private static final String DEC_ATTRIBUTE = "dec";

    /**
     * The camera orientation advertised in every announcement; a Web client has no orientation sensor, so
     * the value is always {@code 0}.
     */
    private static final int DEFAULT_DEVICE_ORIENTATION = 0;

    /**
     * The decode-codec token advertised while the camera is on, matching the offered video format's
     * {@code dec} token.
     */
    private static final String CAMERA_ON_DECODE_CODEC = "H264";

    /**
     * Validates the record components.
     *
     * @throws NullPointerException if {@code callId}, {@code callCreator}, or {@code state} is
     *                              {@code null}
     */
    public VideoStateStanza {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callCreator, "callCreator cannot be null");
        Objects.requireNonNull(state, "state cannot be null");
    }

    /**
     * {@inheritDoc}
     *
     * @return {@link Calls2SignalingType#VIDEO_STATE}
     */
    @Override
    public Calls2SignalingType type() {
        return Calls2SignalingType.VIDEO_STATE;
    }

    /**
     * Builds the {@code <video call-id call-creator state device_orientation [dec]/>} action stanza.
     *
     * <p>The {@code state} attribute is the {@linkplain VideoStreamState#wireOrdinal() wire ordinal} of
     * {@link #state()}, not its Java {@link Enum#ordinal()}. The {@code device_orientation} attribute is
     * always written; the {@code dec} attribute carries the active decode codec and is written only while
     * the camera is on (the {@link VideoStreamState#ENABLED} state).
     *
     * @return the video-state action stanza
     */
    @Override
    public Stanza toStanza() {
        var builder = CallMessages.stampHeader(new StanzaBuilder().description(ELEMENT), callId, callCreator)
                .attribute(STATE_ATTRIBUTE, state.wireOrdinal())
                .attribute(DEVICE_ORIENTATION_ATTRIBUTE, DEFAULT_DEVICE_ORIENTATION);
        if (state == VideoStreamState.ENABLED) {
            builder.attribute(DEC_ATTRIBUTE, CAMERA_ON_DECODE_CODEC);
        }
        return builder.build();
    }

    /**
     * Decodes a {@code <video_state>} action stanza into a {@link VideoStateStanza}.
     *
     * <p>The {@code state} attribute is resolved through {@link VideoStreamState#ofWireOrdinal(int)};
     * an absent attribute decodes to ordinal {@code 0} ({@link VideoStreamState#DISABLED}).
     *
     * @param stanza the {@code <video_state>} stanza
     * @return the decoded video-state action
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code call-id} or {@code call-creator} attribute
     *                                is absent
     */
    public static VideoStateStanza of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var callId = stanza.getRequiredAttributeAsString(CallMessages.CALL_ID_ATTRIBUTE);
        var callCreator = stanza.getRequiredAttributeAsJid(CallMessages.CALL_CREATOR_ATTRIBUTE);
        var state = VideoStreamState.ofWireOrdinal(stanza.getAttributeAsInt(STATE_ATTRIBUTE, 0));
        return new VideoStateStanza(callId, callCreator, state);
    }
}
