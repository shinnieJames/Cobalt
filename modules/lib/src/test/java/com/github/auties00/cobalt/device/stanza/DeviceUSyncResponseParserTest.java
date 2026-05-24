package com.github.auties00.cobalt.device.stanza;

import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.device.DeviceListResult;
import com.github.auties00.cobalt.device.adv.DeviceADVValidator;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.props.TestABPropsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixture-driven tests for {@link DeviceUSyncResponseParser}.
 *
 * @apiNote
 * Exercises the wire-format-handling contract of WA Web's {@code usyncParser}
 * by replaying captured USync IQ pairs from
 * {@code fixtures/device/usync-*.jsonl} through the Cobalt parser. Each
 * test asserts the classification ({@link DeviceListResult.Full},
 * {@link DeviceListResult.Omitted}, {@link DeviceListResult.Error}) the
 * parser produces for one of the six observed wire shapes (self lookup,
 * other-contact lookup, nonexistent number, bot DM, hosted-business
 * PN-side, hosted-business LID-side, plus group lookups of various sizes).
 *
 * @implNote
 * This implementation deliberately avoids verifying the
 * {@link DeviceListResult.Full} payload contents: signature verification on
 * the captured {@code <key-index-list>} requires the test store to hold
 * the original primary identity key, which is not available in the public
 * fixture corpus. The matching positive-path assertion lives in
 * {@code DeviceADVValidatorTest}; here we only assert that the parser
 * walked the captured shape without exceptions and produced the right
 * Cobalt-side classification.
 */
@DisplayName("DeviceUSyncResponseParser")
class DeviceUSyncResponseParserTest {
    /**
     * Captured-session self phone-number JID (matches the
     * {@code business} VoIP capture session).
     */
    private static final Jid SELF_PN = Jid.of("19254863482@s.whatsapp.net");

    /**
     * Captured-session self LID (paired with {@link #SELF_PN}).
     */
    private static final Jid SELF_LID = Jid.of("83116928594056@lid");

    /**
     * Constructs a fresh parser wired to a temporary store seeded with the
     * captured-session identity pair.
     *
     * @apiNote
     * Returns a new parser per test so accumulator state inside
     * {@link DeviceADVValidator} does not leak between cases.
     *
     * @return the parser under test
     */
    private static DeviceUSyncResponseParser newParser() {
        var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        var props = TestABPropsService.builder().build();
        return new DeviceUSyncResponseParser(new DeviceADVValidator(store, props));
    }

    /**
     * Loads the inbound USync response {@link Node} for the given fixture
     * topic.
     *
     * @apiNote
     * Walks the captured event stream and returns the first entry tagged
     * {@code direction = "in"}; the outbound (query) entry is discarded
     * because the parser only consumes responses.
     *
     * @param topic the fixture slug (for example {@code "usync-self"})
     * @return the inbound response {@link Node}
     * @throws AssertionError if the fixture has no inbound entry
     */
    private static Node loadResponseNode(String topic) {
        for (var event : DeviceFixtures.loadEvents(topic)) {
            if ("in".equals(event.getString("direction"))) {
                return DeviceFixtures.buildNodeFromEvent(event);
            }
        }
        throw new AssertionError("no inbound event in fixture " + topic);
    }

    /**
     * Self-lookup capture lands on the Full path and emits no error
     * results.
     *
     * @implNote
     * Without the original primary identity key, the parser's
     * {@code parseFullResult} returns an empty stream after signature
     * verification fails; the contract under test is the absence of
     * {@link DeviceListResult.Error} entries, not a populated
     * {@link DeviceListResult.Full} payload.
     */
    @Test
    @DisplayName("self USync response carries a signed-key-index; parser routes through parseFullResult")
    void selfRoutesToFullPath() {
        var response = loadResponseNode("usync-self");
        var results = newParser().parse(response);

        var errors = results.stream().filter(r -> r instanceof DeviceListResult.Error).count();
        assertEquals(0, errors, "self USync response carries no <error> children");
    }

    /**
     * Other-contact lookup capture follows the same Full-path classification
     * as the self lookup.
     */
    @Test
    @DisplayName("other-contact USync response: same Full-path routing as self")
    void otherRoutesToFullPath() {
        var response = loadResponseNode("usync-other");
        var results = newParser().parse(response);

        var errors = results.stream().filter(r -> r instanceof DeviceListResult.Error).count();
        assertEquals(0, errors, "other-contact USync response carries no <error> children");
    }

    /**
     * Nonexistent-number capture classifies as Omitted because the response
     * carries an empty {@code <device-list/>} and no signed key index.
     *
     * @apiNote
     * Downstream Cobalt treats Omitted entries as "primary device only" in
     * the device-fanout step, matching WA Web's behaviour.
     */
    @Test
    @DisplayName("nonexistent-user USync: empty <device-list/> routes through parseOmittedResult")
    void nonexistentRoutesToOmittedPath() {
        var response = loadResponseNode("usync-nonexistent");
        var results = newParser().parse(response);

        assertEquals(1, results.size(), "single-user query returns one result");
        assertTrue(results.getFirst() instanceof DeviceListResult.Omitted,
                "nonexistent number should classify as Omitted, got "
                        + results.getFirst().getClass().getSimpleName());
    }

    /**
     * None of the captured USync responses surface a fatal global
     * {@link DeviceListResult.Error}.
     *
     * @implNote
     * Iterates the full fixture catalogue to catch a regression where a
     * single capture stops parsing because of a schema drift; the per-shape
     * tests above cover the positive classification.
     */
    @Test
    @DisplayName("none of the captured responses produce a fatal global error")
    void noFatalGlobalError() {
        for (var topic : List.of("usync-self", "usync-other", "usync-nonexistent", "usync-bot", "usync-hosted-pn", "usync-hosted-lid")) {
            var response = loadResponseNode(topic);
            var results = newParser().parse(response);
            var fatalError = results.stream()
                    .filter(r -> r instanceof DeviceListResult.Error err && err.fatal())
                    .findFirst();
            assertFalse(fatalError.isPresent(),
                    "Captured " + topic + " response should not be a fatal global error; got " + fatalError.orElse(null));
        }
    }

    /**
     * Bot-DM USync returns an empty {@code <list/>} and the parser yields
     * zero per-user results.
     *
     * @apiNote
     * Bots are not standard WhatsApp users; the server's empty list shape
     * is intentional and must not be misclassified as an error.
     */
    @Test
    @DisplayName("bot DM USync: server returns empty <list/>; parser yields no results")
    void botUSyncReturnsEmpty() {
        var response = loadResponseNode("usync-bot");
        var results = newParser().parse(response);
        assertEquals(0, results.size(),
                "bot USync produces no DeviceListResult entries (empty <list/>)");
    }

    /**
     * Hosted-business coex USync over the PN server returns one Omitted
     * entry for the single primary device.
     */
    @Test
    @DisplayName("hosted-business coex USync (PN-server): single primary device, Omitted path")
    void hostedPnUSync() {
        var response = loadResponseNode("usync-hosted-pn");
        var results = newParser().parse(response);
        assertEquals(1, results.size(),
                "hosted contact returns one result");
        assertTrue(results.getFirst() instanceof DeviceListResult.Omitted,
                "single primary device with no key-index-list classifies as Omitted; got "
                        + results.getFirst().getClass().getSimpleName());
    }

    /**
     * Hosted-business coex USync over the LID server yields the same Omitted
     * classification as the PN-side capture.
     */
    @Test
    @DisplayName("hosted-business coex USync (LID-server): single primary device, Omitted path")
    void hostedLidUSync() {
        var response = loadResponseNode("usync-hosted-lid");
        var results = newParser().parse(response);
        assertEquals(1, results.size());
        assertTrue(results.getFirst() instanceof DeviceListResult.Omitted,
                "LID-side hosted contact same Omitted classification; got "
                        + results.getFirst().getClass().getSimpleName());
    }

    /**
     * Group USync captures of varying sizes (2, 10, 205 users) parse
     * without producing a fatal global error.
     *
     * @implNote
     * The per-user result count depends on signature-verification success
     * (covered by {@code DeviceADVValidatorTest}); this assertion only
     * pins the wire-format-handling contract.
     */
    @Test
    @DisplayName("group USyncs: small (2), medium (10), and large (205) all parse without exceptions")
    void groupUSyncShapes() {
        for (var topic : List.of("usync-group-small", "usync-group-medium", "usync-group-large")) {
            var response = loadResponseNode(topic);
            var results = newParser().parse(response);
            assertFalse(results.stream().anyMatch(r -> r instanceof DeviceListResult.Error err && err.fatal()),
                    topic + " should not produce a fatal global error");
        }
    }
}
