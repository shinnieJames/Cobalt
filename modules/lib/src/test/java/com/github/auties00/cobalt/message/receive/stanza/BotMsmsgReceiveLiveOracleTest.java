package com.github.auties00.cobalt.message.receive.stanza;

import com.github.auties00.cobalt.message.MessageFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wire-shape oracle for inbound MSMSG (multi-side encrypted message)
 * streams from the Meta AI bot, anchored to
 * {@code fixtures/message/receive/template-or-buttons-from-biz.jsonl}.
 *
 * <p>The captured stream models a complete bot reply: a single user
 * question dispatched via {@code send/bot-text} is answered by the bot
 * with a multi-chunk MSMSG stream. Each chunk is a separate inbound
 * {@code <message from="…@bot">} stanza with:
 *
 * <ul>
 *   <li>{@code type="text"}</li>
 *   <li>a {@code <bot>} child with one of {@code edit="first"},
 *       {@code edit="inner"}, or {@code edit="last"} — the streaming
 *       marker, equivalent to SSE-style segmentation;</li>
 *   <li>a {@code <meta target_id="…">} child binding every chunk back to
 *       the user's original outgoing question;</li>
 *   <li>an {@code <enc v="2" type="msmsg">} child carrying the bot's
 *       AES-GCM ciphertext under the WAWebBotMessageSecret HKDF key.</li>
 * </ul>
 *
 * <p>This is the receive-side counterpart to {@code send/bot-text}: the
 * outgoing user question (an {@code msmsg}-wrapped peer fanout) gets
 * answered with this stream, and Cobalt's {@code ChatMessageReceiver}
 * is responsible for stitching the {@code first}/{@code inner}/{@code last}
 * chunks back into a single {@code AIRichResponseMessage}.
 */
@DisplayName("Bot MSMSG receive live wire oracle")
class BotMsmsgReceiveLiveOracleTest {

    private static final String TOPIC = "receive/template-or-buttons-from-biz";
    private static final String BOT_JID = "867051314767696@bot";

    @Test
    @DisplayName("captured bot stream contains at least one first + one last chunk")
    void streamHasFirstAndLastMarkers() {
        if (!MessageFixtures.isAvailable(TOPIC)) return;

        var chunks = MessageFixtures.loadEvents(TOPIC).stream()
                .filter(e -> "in".equals(e.getString("direction")))
                .filter(e -> "message".equals(e.getString("tag")))
                .map(MessageFixtures::buildNodeFromEvent)
                .filter(n -> BOT_JID.equals(n.getAttributeAsString("from").orElse(null)))
                .toList();
        assertFalse(chunks.isEmpty(), "bot stream must contain at least one chunk");

        var editValues = chunks.stream()
                .map(c -> c.getChild("bot").orElseThrow().getAttributeAsString("edit").orElse(null))
                .toList();
        assertTrue(editValues.contains("first"),
                "bot stream must contain an edit=\"first\" chunk to mark the stream start, got " + editValues);
        assertTrue(editValues.contains("last"),
                "bot stream must contain an edit=\"last\" chunk to mark the stream end, got " + editValues);
    }

    @Test
    @DisplayName("every bot chunk carries <bot> + <meta target_id=…> + <enc type=msmsg>")
    void everyChunkShape() {
        if (!MessageFixtures.isAvailable(TOPIC)) return;

        var chunks = MessageFixtures.loadEvents(TOPIC).stream()
                .filter(e -> "in".equals(e.getString("direction")))
                .filter(e -> "message".equals(e.getString("tag")))
                .map(MessageFixtures::buildNodeFromEvent)
                .filter(n -> BOT_JID.equals(n.getAttributeAsString("from").orElse(null)))
                .toList();
        assertFalse(chunks.isEmpty());

        for (var chunk : chunks) {
            assertEquals("text", chunk.getAttributeAsString("type").orElseThrow(),
                    "all bot chunks must carry type=text");
            assertNotNull(chunk.getAttributeAsString("notify").orElse(null),
                    "every bot chunk has a notify= attribute (display name)");

            var bot = chunk.getChild("bot").orElseThrow(
                    () -> new AssertionError("every bot chunk has a <bot> marker"));
            var edit = bot.getAttributeAsString("edit").orElseThrow();
            assertTrue(List.of("first", "inner", "last").contains(edit),
                    "<bot edit=…> must be first/inner/last, got " + edit);
            // edit_target_id is the outgoing user-question id; inner/last must point
            // at the same chunk that started the stream (the first stanza's id).
            // The "first" chunk itself has an empty edit_target_id.
            bot.getAttributeAsString("edit_target_id").orElseThrow();

            var meta = chunk.getChild("meta").orElseThrow(
                    () -> new AssertionError("every bot chunk has a <meta> child"));
            assertTrue(meta.getAttributeAsString("target_id").isPresent(),
                    "<meta target_id=…> must bind chunk to user's question");

            var enc = chunk.getChild("enc").orElseThrow();
            assertEquals("2", enc.getAttributeAsString("v").orElseThrow(),
                    "<enc> must be v=2");
            assertEquals("msmsg", enc.getAttributeAsString("type").orElseThrow(),
                    "<enc type=…> must be msmsg for bot replies");
            assertTrue(enc.hasContent(),
                    "<enc> must carry MSMSG ciphertext bytes");
        }
    }

    @Test
    @DisplayName("every chunk's <meta target_id=…> points back at the same user question")
    void everyChunkBindsToSameUserQuestion() {
        if (!MessageFixtures.isAvailable(TOPIC)) return;

        var chunks = MessageFixtures.loadEvents(TOPIC).stream()
                .filter(e -> "in".equals(e.getString("direction")))
                .filter(e -> "message".equals(e.getString("tag")))
                .map(MessageFixtures::buildNodeFromEvent)
                .filter(n -> BOT_JID.equals(n.getAttributeAsString("from").orElse(null)))
                .toList();
        assertFalse(chunks.isEmpty());

        var targetIds = chunks.stream()
                .map(c -> c.getChild("meta").orElseThrow().getAttributeAsString("target_id").orElseThrow())
                .distinct()
                .toList();
        assertEquals(1, targetIds.size(),
                "every chunk in a single bot reply must bind to the same target_id; got " + targetIds);
    }

    @Test
    @DisplayName("inner/last chunks carry edit_target_id pointing at the first chunk's id")
    void innerAndLastReferenceFirstChunk() {
        if (!MessageFixtures.isAvailable(TOPIC)) return;

        var chunks = MessageFixtures.loadEvents(TOPIC).stream()
                .filter(e -> "in".equals(e.getString("direction")))
                .filter(e -> "message".equals(e.getString("tag")))
                .map(MessageFixtures::buildNodeFromEvent)
                .filter(n -> BOT_JID.equals(n.getAttributeAsString("from").orElse(null)))
                .toList();

        var firstChunk = chunks.stream()
                .filter(c -> "first".equals(c.getChild("bot").orElseThrow().getAttributeAsString("edit").orElse(null)))
                .findFirst()
                .orElseThrow();
        var firstId = firstChunk.getAttributeAsString("id").orElseThrow();

        for (var chunk : chunks) {
            var edit = chunk.getChild("bot").orElseThrow().getAttributeAsString("edit").orElseThrow();
            var editTargetId = chunk.getChild("bot").orElseThrow()
                    .getAttributeAsString("edit_target_id").orElseThrow();
            if ("first".equals(edit)) {
                assertEquals("", editTargetId,
                        "<bot edit=\"first\"> must have empty edit_target_id");
            } else {
                assertEquals(firstId, editTargetId,
                        "<bot edit=\"" + edit + "\"> edit_target_id must reference the first chunk's id");
            }
        }
    }
}
