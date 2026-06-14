package com.github.auties00.cobalt.call.interaction;

import com.github.auties00.cobalt.model.call.CallInteraction;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.call.datachannel.AppDataMessage;
import com.github.auties00.cobalt.model.call.datachannel.AppDataMessageBuilder;
import com.github.auties00.cobalt.model.call.datachannel.AppDataMessageSpec;
import com.github.auties00.cobalt.model.call.datachannel.AppDataPayloads;
import com.github.auties00.cobalt.model.call.datachannel.AppDataPayloadsBuilder;
import com.github.auties00.cobalt.model.call.datachannel.AppDataPayloadsSpec;
import com.github.auties00.cobalt.model.call.datachannel.ReactionInfo;
import com.github.auties00.cobalt.model.call.datachannel.ReactionInfoBuilder;

import java.util.List;
import java.util.Objects;

/**
 * Encodes a {@link CallInteraction} into the protobuf-serialised
 * {@link com.github.auties00.cobalt.model.call.datachannel.AppDataMessage AppDataMessage} bytes the call's
 * pre-negotiated SCTP DataChannel carries.
 *
 * <p>In-call actions (reactions, raise-hand, peer-mute, key-frame and video-upgrade requests) are NOT RTP packets and
 * NOT {@code <call>} signaling stanzas: they are {@code AppDataMessage} protobuf messages, batched in an
 * {@link AppDataPayloads} envelope, sent directly over the DataChannel (which itself rides DTLS-encrypted SCTP, so no
 * SRTP wrap is applied). The wire shape is verified byte-exact against a live WhatsApp Web sender:
 * {@code sendReaction(heart)} produces {@code 0a0c0a0a08021206e29da4efb88f}, that is
 * {@code AppDataPayloads{ messages: [ AppDataMessage{ reactionInfo{ transaction_id, reaction } } ] }}.
 *
 * <p>{@link #encode(CallInteraction, InteractionStreamState)} is the data-plane entry point: it returns the ready-to-send
 * batch bytes for a {@link CallInteraction.Reaction Reaction}, drawing the reaction's {@code transaction_id} from the
 * per-call {@link InteractionStreamState}. The reaction is the only in-call action the voip stack carries as a
 * DataChannel {@code AppDataMessage}; the other interactions are not this encoder's responsibility and are rejected (see
 * {@link #encode(CallInteraction, InteractionStreamState)} for the exhaustive disposition).
 *
 * @implNote The raise-hand, peer-mute, and video-upgrade actions are {@code <call>} signaling stanzas built by their own
 * WASM helpers (for example {@code make_and_send_raise_hand_msg} emits the {@code raise-hand-state} stanza attribute, not
 * a protobuf), and a key-frame request is an RTCP PLI/FIR; none is a protobuf message. The voip wasm's protobuf-c
 * descriptor table registers exactly two AppData payload types, {@code reactionInfo} and {@code transcriptionInfo}, and
 * no descriptor for any of those four actions, so they are routed to the signaling/RTCP plane by the caller rather than
 * encoded here.
 */
@WhatsAppWebModule(moduleName = "WAWebVoipStackInterfaceWeb")
public final class CallInteractionEncoder {
    /**
     * Prevents instantiation of this stateless utility class.
     */
    private CallInteractionEncoder() {
    }

    /**
     * Encodes a data-plane {@link CallInteraction} into the {@link AppDataPayloads} batch bytes ready to send over the
     * call's AppData {@link com.github.auties00.cobalt.call.transport.sctp.datachannel.DataChannel DataChannel}.
     *
     * <p>A {@link CallInteraction.Reaction Reaction} is encoded as a single-entry
     * {@code AppDataPayloads{ messages: [ AppDataMessage{ reactionInfo } ] }}, the byte-exact-verified wire shape, with
     * the reaction's {@code transaction_id} taken from {@link InteractionStreamState#nextTransactionId()} and the
     * reaction string the emoji UTF-8.
     *
     * <p>Every other variant ({@link CallInteraction.RaiseHand RaiseHand}, {@link CallInteraction.LowerHand LowerHand},
     * {@link CallInteraction.PeerMuteRequest PeerMuteRequest}, {@link CallInteraction.KeyFrameRequest KeyFrameRequest},
     * {@link CallInteraction.VideoUpgradeRequest VideoUpgradeRequest}) is not a DataChannel AppData message at all: the
     * first three and the video-upgrade are {@code <call>} signaling stanzas and a key-frame request is an RTCP PLI/FIR.
     * Routing one of them here is a caller error, so it is rejected with {@link IllegalArgumentException}; the caller must
     * dispatch it on the signaling or RTCP plane instead (see
     * {@link com.github.auties00.cobalt.call.signaling.CallStanza CallStanza}).
     *
     * @param interaction the interaction to encode; must not be {@code null}
     * @param state       the per-call stream state from which the reaction transaction id is drawn; must not be
     *                    {@code null}
     * @return the protobuf-serialised {@link AppDataPayloads} batch bytes, ready for the DataChannel
     * @throws NullPointerException     if {@code interaction} or {@code state} is {@code null}
     * @throws IllegalArgumentException if {@code interaction} is not a DataChannel AppData interaction (anything other
     *                                  than a {@link CallInteraction.Reaction Reaction})
     */
    public static byte[] encode(CallInteraction interaction, InteractionStreamState state) {
        Objects.requireNonNull(interaction, "interaction cannot be null");
        Objects.requireNonNull(state, "state cannot be null");
        return switch (interaction) {
            case CallInteraction.Reaction r -> encodeReactionBatch(r, state.nextTransactionId());
            case CallInteraction.RaiseHand _, CallInteraction.LowerHand _,
                 CallInteraction.PeerMuteRequest _, CallInteraction.KeyFrameRequest _,
                 CallInteraction.VideoUpgradeRequest _ -> throw new IllegalArgumentException(
                    interaction.wireKind() + " is not a DataChannel AppData interaction: raise-hand, lower-hand, "
                            + "peer-mute, and video-upgrade are <call> signaling stanzas and a key-frame request is an "
                            + "RTCP PLI/FIR; route it to the signaling or RTCP plane, not this encoder");
        };
    }

    /**
     * Encodes a {@link CallInteraction.Reaction Reaction} into a single-entry {@link AppDataPayloads} batch.
     *
     * <p>This produces the byte-exact wire shape a live WhatsApp Web {@code sendReaction} emits:
     * {@code AppDataPayloads{ messages: [ AppDataMessage{ reactionInfo{ transaction_id, reaction } } ] }}.
     *
     * @param reaction      the reaction whose emoji is encoded
     * @param transactionId the reaction transaction id
     * @return the protobuf-serialised batch bytes
     */
    private static byte[] encodeReactionBatch(CallInteraction.Reaction reaction, long transactionId) {
        var info = new ReactionInfoBuilder()
                .transactionId(transactionId)
                .reaction(reaction.emoji())
                .build();
        var message = new AppDataMessageBuilder()
                .reactionInfo(info)
                .build();
        return encodeAppDataBatch(List.of(message));
    }

    /**
     * Encodes a {@link CallInteraction.Reaction} as a single
     * {@link AppDataMessage} carrying a {@link ReactionInfo}.
     *
     * <p>The returned bytes are the bare {@code AppDataMessage}, NOT the {@link AppDataPayloads} batch the DataChannel
     * actually carries. Callers sending a reaction on the wire must wrap it via {@link #encodeAppDataBatch(List)} (or use
     * {@link #encode(CallInteraction, InteractionStreamState)}, which does so); this method exists for callers that
     * coalesce several messages into one batch themselves.
     *
     * @apiNote The {@code transactionId} is a sender-side monotonic identifier. Callers typically
     * source it from {@link InteractionStreamState#nextTransactionId()}; the receiver displays the
     * reaction as a transient UI overlay and uses the id to deduplicate retransmissions.
     *
     * @param reaction      the reaction whose emoji is encoded; must not be {@code null}
     * @param transactionId the sender-side transaction id for this reaction
     * @return the protobuf-serialised bytes of the {@link AppDataMessage}
     * @throws NullPointerException if {@code reaction} is {@code null}
     */
    public static byte[] encodeReactionAsAppData(CallInteraction.Reaction reaction, long transactionId) {
        Objects.requireNonNull(reaction, "reaction cannot be null");
        var info = new ReactionInfoBuilder()
                .transactionId(transactionId)
                .reaction(reaction.emoji())
                .build();
        var message = new AppDataMessageBuilder()
                .reactionInfo(info)
                .build();
        return AppDataMessageSpec.encode(message);
    }

    /**
     * Wraps one or more {@link AppDataMessage} payloads in an {@link AppDataPayloads} batch
     * envelope and serialises the batch for the AppData {@link com.github.auties00.cobalt.call.transport.sctp.datachannel.DataChannel
     * DataChannel}.
     *
     * <p>The runtime can coalesce multiple application-data messages into a single batched send
     * so the receiver applies them atomically; a producer with a single message still wraps it
     * in a one-entry {@link AppDataPayloads}, which is the shape live captures show on the wire.
     *
     * @param messages the batched payloads; must not be {@code null} or empty
     * @return the protobuf-serialised bytes of the {@link AppDataPayloads} batch
     * @throws NullPointerException     if {@code messages} is {@code null}
     * @throws IllegalArgumentException if {@code messages} is empty
     */
    public static byte[] encodeAppDataBatch(List<AppDataMessage> messages) {
        Objects.requireNonNull(messages, "messages cannot be null");
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages cannot be empty");
        }
        var payloads = new AppDataPayloadsBuilder()
                .messages(messages)
                .build();
        return AppDataPayloadsSpec.encode(payloads);
    }
}
