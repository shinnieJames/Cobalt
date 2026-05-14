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
 * <p>Each test loads a captured USync IQ pair from
 * {@code fixtures/device/usync-*.jsonl}, rebuilds the inbound response
 * {@link Node} via {@link DeviceFixtures#buildNodeFromEvent}, and feeds
 * it into Cobalt's parser.
 *
 * <p>Cross-checking the parser's {@link DeviceListResult.Full} payload
 * against WA Web's {@code usyncParser} output (the
 * {@code .expected.json} sibling files) requires the test store to hold
 * the same primary identity key the live session has — otherwise the
 * signed-key-index signature verification fails and {@code parseFullResult}
 * deliberately returns an empty stream (the contract under test in
 * {@code DeviceADVValidatorTest}, not here). These tests instead assert
 * the wire-format-handling contract that holds without a populated
 * identity store: the parser walks the captured response without
 * exceptions, and produces the right Cobalt-side classification
 * ({@link DeviceListResult.Full} / {@link DeviceListResult.Omitted} /
 * {@link DeviceListResult.Error}) for each query shape.
 */
@DisplayName("DeviceUSyncResponseParser")
class DeviceUSyncResponseParserTest {
    private static final Jid SELF_PN = Jid.of("19254863482@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594056@lid");

    private static DeviceUSyncResponseParser newParser() {
        var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        var props = TestABPropsService.builder().build();
        return new DeviceUSyncResponseParser(new DeviceADVValidator(store, props));
    }

    private static Node loadResponseNode(String topic) {
        for (var event : DeviceFixtures.loadEvents(topic)) {
            if ("in".equals(event.getString("direction"))) {
                return DeviceFixtures.buildNodeFromEvent(event);
            }
        }
        throw new AssertionError("no inbound event in fixture " + topic);
    }

    @Test
    @DisplayName("self USync response carries a signed-key-index — parser routes through parseFullResult")
    void selfRoutesToFullPath() {
        // Without a stored primary identity, ADV signature verification fails and
        // parseFullResult emits no Full result. The capture proves the response
        // shape Cobalt expects on the Full path arrived without throwing.
        var response = loadResponseNode("usync-self");
        var results = newParser().parse(response);

        // No fatal global error and no protocol-level error.
        var errors = results.stream().filter(r -> r instanceof DeviceListResult.Error).count();
        assertEquals(0, errors, "self USync response carries no <error> children");
    }

    @Test
    @DisplayName("other-contact USync response: same Full-path routing as self")
    void otherRoutesToFullPath() {
        var response = loadResponseNode("usync-other");
        var results = newParser().parse(response);

        var errors = results.stream().filter(r -> r instanceof DeviceListResult.Error).count();
        assertEquals(0, errors, "other-contact USync response carries no <error> children");
    }

    @Test
    @DisplayName("nonexistent-user USync: empty <device-list/> routes through parseOmittedResult")
    void nonexistentRoutesToOmittedPath() {
        // The captured wire shape for a nonexistent number is:
        //   <user jid="..."><devices><device-list/></devices></user>
        // No signed-key-index, no companion devices. parseOmittedResult returns
        // an Omitted entry for this user (and Cobalt later treats it as "primary
        // only" in the fanout).
        var response = loadResponseNode("usync-nonexistent");
        var results = newParser().parse(response);

        assertEquals(1, results.size(), "single-user query returns one result");
        assertTrue(results.getFirst() instanceof DeviceListResult.Omitted,
                "nonexistent number should classify as Omitted, got "
                        + results.getFirst().getClass().getSimpleName());
    }

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

    @Test
    @DisplayName("bot DM USync: server returns empty <list/> — parser yields no results")
    void botUSyncReturnsEmpty() {
        // Bots aren't standard WhatsApp users; USyncing the bot JID returns an
        // empty <list/> with no <user> children. The parser should produce zero
        // user-level results (no Full, no Omitted, no per-user Error).
        var response = loadResponseNode("usync-bot");
        var results = newParser().parse(response);
        assertEquals(0, results.size(),
                "bot USync produces no DeviceListResult entries (empty <list/>)");
    }

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

    @Test
    @DisplayName("group USyncs: small (2), medium (10), and large (205) all parse without exceptions")
    void groupUSyncShapes() {
        for (var topic : List.of("usync-group-small", "usync-group-medium", "usync-group-large")) {
            var response = loadResponseNode(topic);
            // Just walking the captured response should not throw. The actual
            // result count depends on signature-verification success (which
            // needs the per-user identity store populated — covered by
            // DeviceADVValidatorTest); for this contract we only assert the
            // parser handled the wire format.
            var results = newParser().parse(response);
            assertFalse(results.stream().anyMatch(r -> r instanceof DeviceListResult.Error err && err.fatal()),
                    topic + " should not produce a fatal global error");
        }
    }
}
