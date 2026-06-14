package com.github.auties00.cobalt.call.interaction;

import com.github.auties00.cobalt.model.call.CallInteraction;
import com.github.auties00.cobalt.model.call.datachannel.AppDataMessageSpec;
import com.github.auties00.cobalt.model.call.datachannel.AppDataPayloadsSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers {@link CallInteractionEncoder}: the byte-exact reaction wire shape, the {@link CallInteractionEncoder#encode}
 * data-plane path that emits an {@code AppDataPayloads} batch, the per-call reaction transaction-id increment, and the
 * rejection of variants with no modeled {@code AppDataMessage} field.
 *
 * <p>The expected reaction bytes are taken from a live WhatsApp Web {@code sendReaction} capture (MEDIA-CRYPTO-SPEC
 * part E.4): {@code sendReaction(heart) -> 0a0c0a0a08021206e29da4efb88f}.
 */
public class CallInteractionEncoderTest {
    // heart = U+2764 U+FE0F (e29da4 efb88f); thumb = U+1F44D (f09f918d)
    private static final String HEART = new String(
            new byte[]{(byte) 0xe2, (byte) 0x9d, (byte) 0xa4, (byte) 0xef, (byte) 0xb8, (byte) 0x8f},
            StandardCharsets.UTF_8);
    private static final String THUMB = new String(
            new byte[]{(byte) 0xf0, (byte) 0x9f, (byte) 0x91, (byte) 0x8d}, StandardCharsets.UTF_8);

    private static String hex(byte[] bytes) {
        var sb = new StringBuilder(bytes.length * 2);
        for (var b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Test
    @DisplayName("encode(reaction): byte-exact AppDataPayloads batch matching the live sendReaction capture")
    public void reactionBytesAreByteExact() {
        var state = new InteractionStreamState();
        state.nextTransactionId(); // burn id 1 so the next reaction is transaction_id 2, matching the captured heart
        var bytes = CallInteractionEncoder.encode(new CallInteraction.Reaction(HEART), state);
        assertEquals("0a0c0a0a08021206e29da4efb88f", hex(bytes));
    }

    @Test
    @DisplayName("encode(reaction): emits a single-entry AppDataPayloads batch carrying the reaction")
    public void encodeReactionRoundTripsAsBatch() {
        var state = new InteractionStreamState();
        var bytes = CallInteractionEncoder.encode(new CallInteraction.Reaction(THUMB), state);
        var batch = AppDataPayloadsSpec.decode(bytes);
        assertEquals(1, batch.messages().size());
        var info = batch.messages().getFirst().reactionInfo().orElseThrow();
        assertEquals(THUMB, info.reaction().orElseThrow());
        assertEquals(1L, info.transactionId().orElseThrow(), "first reaction uses transaction_id 1");
    }

    @Test
    @DisplayName("encode(reaction): the transaction id increments per reaction")
    public void transactionIdIncrements() {
        var state = new InteractionStreamState();
        var first = AppDataPayloadsSpec.decode(
                CallInteractionEncoder.encode(new CallInteraction.Reaction(THUMB), state));
        var second = AppDataPayloadsSpec.decode(
                CallInteractionEncoder.encode(new CallInteraction.Reaction(THUMB), state));
        var firstId = first.messages().getFirst().reactionInfo().orElseThrow().transactionId().orElseThrow();
        var secondId = second.messages().getFirst().reactionInfo().orElseThrow().transactionId().orElseThrow();
        assertEquals(firstId + 1, secondId);
    }

    @Test
    @DisplayName("encode: rejects every interaction that is not a DataChannel AppData reaction")
    public void nonDataPlaneVariantsRejected() {
        var state = new InteractionStreamState();
        assertThrows(IllegalArgumentException.class,
                () -> CallInteractionEncoder.encode(new CallInteraction.RaiseHand(), state));
        assertThrows(IllegalArgumentException.class,
                () -> CallInteractionEncoder.encode(new CallInteraction.LowerHand(), state));
        assertThrows(IllegalArgumentException.class,
                () -> CallInteractionEncoder.encode(
                        new CallInteraction.PeerMuteRequest("19153544650@lid", Optional.empty()), state));
        assertThrows(IllegalArgumentException.class,
                () -> CallInteractionEncoder.encode(new CallInteraction.KeyFrameRequest(), state));
        assertThrows(IllegalArgumentException.class,
                () -> CallInteractionEncoder.encode(new CallInteraction.VideoUpgradeRequest(), state));
    }

    @Test
    @DisplayName("encode: rejects null arguments")
    public void nullArgsRejected() {
        var state = new InteractionStreamState();
        assertThrows(NullPointerException.class,
                () -> CallInteractionEncoder.encode(null, state));
        assertThrows(NullPointerException.class,
                () -> CallInteractionEncoder.encode(new CallInteraction.Reaction(THUMB), null));
    }

    @Test
    @DisplayName("encodeReactionAsAppData: bare AppDataMessage round-trips")
    public void reactionAsAppDataRoundTrips() {
        var bytes = CallInteractionEncoder.encodeReactionAsAppData(
                new CallInteraction.Reaction(THUMB), 42L);
        var decoded = AppDataMessageSpec.decode(bytes);
        assertNotNull(decoded);
        var info = decoded.reactionInfo().orElseThrow();
        assertEquals(42L, info.transactionId().orElseThrow());
        assertEquals(THUMB, info.reaction().orElseThrow());
        assertTrue(decoded.transcriptionInfo().isEmpty(),
                "single-reaction message must not carry a transcription payload");
    }

    @Test
    @DisplayName("encodeAppDataBatch: coalesces multiple messages and round-trips")
    public void appDataBatchRoundTrips() {
        var thumb = AppDataMessageSpec.decode(
                CallInteractionEncoder.encodeReactionAsAppData(new CallInteraction.Reaction(THUMB), 1L));
        var heart = AppDataMessageSpec.decode(
                CallInteractionEncoder.encodeReactionAsAppData(new CallInteraction.Reaction(HEART), 2L));
        var batch = CallInteractionEncoder.encodeAppDataBatch(List.of(thumb, heart));
        var decoded = AppDataPayloadsSpec.decode(batch);
        var messages = decoded.messages();
        assertEquals(2, messages.size());
        assertEquals(THUMB, messages.get(0).reactionInfo().orElseThrow().reaction().orElseThrow());
        assertEquals(2L, messages.get(1).reactionInfo().orElseThrow().transactionId().orElseThrow());
    }

    @Test
    @DisplayName("encodeAppDataBatch: rejects an empty list")
    public void encodeAppDataBatchRejectsEmptyList() {
        assertThrows(IllegalArgumentException.class,
                () -> CallInteractionEncoder.encodeAppDataBatch(List.of()));
    }
}
