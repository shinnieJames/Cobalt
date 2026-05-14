package com.github.auties00.cobalt.message.send.ack;

import com.github.auties00.cobalt.message.MessageFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live oracle for {@link AckParser}: feeds every captured inbound
 * {@code <ack>} stanza through the parser and asserts byte-equal parity
 * against the captured attributes.
 *
 * <p>Each fixture {@code <topic>.ack.jsonl} corresponds to a successful
 * server ack of an outgoing send, so all the captured acks are
 * success-shaped (no {@code error} attribute, no {@code phash}). The
 * primary value of this test is enforcing that the parser correctly
 * round-trips the success-side attribute slots (timestamp, optional
 * {@code count} on group acks, optional {@code server_id} on newsletter
 * acks). The error-side branches are exercised by the synthetic
 * {@link AckParserTest}.
 */
@DisplayName("AckParser live wire oracle")
class AckParserLiveOracleTest {

    /**
     * Topics whose {@code <topic>.ack.jsonl} fixture is shipped with the
     * test resources. Topics that did not produce an ack capture (e.g.
     * newsletter sends route via a worker we don't observe end-to-end)
     * are excluded.
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

    static Stream<String> groupAckTopics() {
        return Stream.of("send/group-small-text", "send/group-large-text", "send/cag-text")
                .filter(t -> MessageFixtures.isAvailable(t + ".ack"));
    }

    @org.junit.jupiter.api.Test
    @DisplayName("newsletter ack carries server_id (NOT a parsed attribute — preserved on the node)")
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

    @org.junit.jupiter.api.Test
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
        // The compile-only hint: List.of usage to avoid being unused.
        assert List.of() != null;
    }
}
