package com.github.auties00.cobalt.ack;

import com.github.auties00.cobalt.message.MessageFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Live wire oracle for {@link AckParser}.
 *
 * @apiNote
 * Feeds every captured inbound {@code <ack>} stanza through the parser
 * and asserts that the success-side attribute slots (timestamp, optional
 * {@code count} on group acks, optional {@code server_id} on newsletter
 * acks) round-trip identically. Each fixture {@code <topic>.ack.jsonl}
 * corresponds to a successful server ack of an outgoing send, so the
 * captured shape carries no {@code error} attribute and no {@code phash};
 * the error and phash branches are exercised by the synthetic
 * {@link AckParserTest}.
 *
 * @implNote
 * This implementation loads ack fixtures via
 * {@link MessageFixtures#loadEvents}
 * and topic-filters on
 * {@link MessageFixtures#isAvailable}
 * so cells skip cleanly when a particular topic's capture is missing.
 */
@DisplayName("AckParser live wire oracle")
class AckParserLiveOracleTest {

    /**
     * Returns the topic identifiers whose {@code <topic>.ack.jsonl}
     * fixture is available in the test resources.
     *
     * @apiNote
     * Feeds {@link #successAckParses(String)} and
     * {@link #allAcksAreMessageClass()}; topics whose capture is missing
     * are filtered out via
     * {@link MessageFixtures#isAvailable}.
     *
     * @return the topic {@link Stream}
     */
    static Stream<String> ackTopics() {
        return Stream.of(
                "send/peer-text",
                "send/group-small-text",
                "send/group-large-text",
                "send/cag-text",
                "send/peer-edit-revoke",
                "send/bot-text",
                "send/hosted-biz-text",
                "send/newsletter-text"
        ).filter(t -> MessageFixtures.isAvailable(t + ".ack"));
    }

    /**
     * Asserts that the captured ack parses with the success shape (no
     * {@code error}, no {@code phash}, no {@code refresh_lid}).
     */
    @ParameterizedTest(name = "ack for {0}")
    @MethodSource("ackTopics")
    @DisplayName("captured server ack parses with the success shape")
    void successAckParses(String topic) {
        var events = MessageFixtures.loadEvents(topic + ".ack");
        assertFalse(events.isEmpty(), "ack fixture must have at least one event for " + topic);

        for (var event : events) {
            assertEquals("ack", event.getString("tag"), "fixture only contains <ack> events");
            assertEquals("in", event.getString("direction"), "all captured acks are inbound");

            var ack = MessageFixtures.buildNodeFromEvent(event);
            var result = AckParser.parse(ack);

            assertTrue(result.timestamp().isPresent(),
                    "server ack must carry t= timestamp for topic " + topic);
            assertTrue(result.timestamp().orElseThrow().getEpochSecond() > 0,
                    "ack timestamp must be a positive epoch second");
            assertTrue(result.isSuccess(),
                    "captured fixture only contains success acks; got error " + (result.error().isPresent() ? result.error().getAsInt() : null));
            assertTrue(result.error().isEmpty(),
                    "success ack must not have an error code");
            assertFalse(result.refreshLid(),
                    "success ack does not signal refresh_lid");
            assertTrue(result.phash().isEmpty(),
                    "success ack does not carry phash");
        }
    }

    /**
     * Asserts that group acks carry a positive {@code count} attribute.
     */
    @ParameterizedTest(name = "group ack count for {0}")
    @MethodSource("groupAckTopics")
    @DisplayName("group acks carry count=<int> attribute")
    void groupAckHasCount(String topic) {
        var events = MessageFixtures.loadEvents(topic + ".ack");
        for (var event : events) {
            var ack = MessageFixtures.buildNodeFromEvent(event);
            var result = AckParser.parse(ack);
            assertTrue(result.count().isPresent(),
                    "group ack must carry count= attribute for topic " + topic);
            assertTrue(result.count().getAsInt() > 0,
                    "count must be positive, got " + result.count().getAsInt());
        }
    }

    /**
     * Returns the group-ack topic identifiers whose fixture is available.
     *
     * @apiNote
     * Feeds {@link #groupAckHasCount(String)}; the topic filter mirrors
     * {@link #ackTopics()}.
     *
     * @return the topic {@link Stream}
     */
    static Stream<String> groupAckTopics() {
        return Stream.of("send/group-small-text", "send/group-large-text", "send/cag-text")
                .filter(t -> MessageFixtures.isAvailable(t + ".ack"));
    }

    /**
     * Asserts that the newsletter ack carries a {@code server_id}
     * attribute on the raw node even though {@link AckParser} does not
     * surface it.
     *
     * @apiNote
     * The newsletter publish-receipt id is read by higher-level newsletter
     * handlers off the raw node rather than off the parsed
     * {@link AckResult}.
     */
    @Test
    @DisplayName("newsletter ack carries server_id (not a parsed attribute; preserved on the node)")
    void newsletterAckCarriesServerId() {
        var topic = "send/newsletter-text";
        if (!MessageFixtures.isAvailable(topic + ".ack")) return;
        var events = MessageFixtures.loadEvents(topic + ".ack");
        assertFalse(events.isEmpty(), "newsletter ack fixture must have an event");
        for (var event : events) {
            var ack = MessageFixtures.buildNodeFromEvent(event);
            // server_id is the newsletter publish receipt id; AckParser
            // doesn't surface it but it must be on the captured node so
            // higher-level newsletter handlers can read it.
            assertTrue(ack.getAttributeAsString("server_id").isPresent(),
                    "newsletter ack must carry server_id attribute");
        }
    }

    /**
     * Asserts that every captured ack carries {@code class="message"}.
     */
    @Test
    @DisplayName("every captured ack uses class=\"message\"")
    void allAcksAreMessageClass() {
        var topics = ackTopics().toList();
        for (var topic : topics) {
            var events = MessageFixtures.loadEvents(topic + ".ack");
            for (var event : events) {
                var ack = MessageFixtures.buildNodeFromEvent(event);
                assertEquals("message",
                        ack.getAttributeAsString("class").orElse(null),
                        topic + ": ack class must be \"message\"");
            }
        }
        // sanity: we did exercise the loop
        assertFalse(topics.isEmpty());
    }
}
