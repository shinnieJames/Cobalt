package com.github.auties00.cobalt.device.stanza;

import com.github.auties00.cobalt.model.device.info.DeviceListHashInfo;
import com.github.auties00.cobalt.model.device.info.DeviceListHashInfoBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural tests for {@link DeviceUSyncQueryBuilder}.
 *
 * @apiNote
 * Pins the wire-format of the USync IQ stanzas Cobalt sends against WA Web's
 * {@code WAWebUsync.USyncQuery} envelope plus the
 * {@code WAWebUsyncDevice.USyncDeviceProtocol} and
 * {@code WAWebUsyncUsername.USyncUsernameProtocol} sub-protocols. The
 * builder is a pure static factory so every assertion is structural over
 * the returned {@link Node}; no captured fixtures are required.
 *
 * @implNote
 * This implementation drives the builder with synthetic JID sets generated
 * by {@link #generateUsers(int)} to exercise the batching path; the
 * {@link Node} tree is walked using the same helpers as production code so
 * the assertions match the format consumers (the socket transport) will
 * read.
 */
@DisplayName("DeviceUSyncQueryBuilder")
class DeviceUSyncQueryBuilderTest {
    /**
     * Synthetic user-A JID; matches the {@code business} session used by
     * the VoIP capture suite so the test inputs round-trip with the
     * fixture machinery elsewhere.
     */
    private static final Jid USER_A = Jid.of("19254863482@s.whatsapp.net");

    /**
     * Synthetic user-B JID drawn from the Italian number block to avoid
     * collision with {@link #USER_A} on length-sensitive batching checks.
     */
    private static final Jid USER_B = Jid.of("393495089819@s.whatsapp.net");

    /**
     * Public-service announcements account; used by the
     * {@code filtersPsa} test to verify the
     * {@link DeviceUSyncQueryBuilder} drops this special JID before
     * emitting the {@code <user>} list.
     */
    private static final Jid PSA_ACCOUNT = Jid.announcementsAccount();

    /**
     * Asserts the {@code <iq>} attribute set and child shape under the
     * {@code <usync>} envelope.
     */
    @Nested
    @DisplayName("attribute and child shape")
    class AttributeAndChildShape {
        /**
         * Single-user input lands as one IQ with the expected
         * {@code to}/{@code xmlns}/{@code type} attributes.
         */
        @Test
        @DisplayName("produces a single IQ with the canonical usync attribute order")
        void singleIq() {
            var builders = DeviceUSyncQueryBuilder.build(linked(USER_A), "message", null, false);
            assertEquals(1, builders.size());

            var iq = builders.getFirst().build();
            assertEquals("iq", iq.description());
            assertEquals(JidServer.user().toString(), iq.getAttributeAsString("to").orElseThrow());
            assertEquals("usync", iq.getAttributeAsString("xmlns").orElseThrow());
            assertEquals("get", iq.getAttributeAsString("type").orElseThrow());
        }

        /**
         * The {@code <usync>} child carries the
         * {@code sid}/{@code index}/{@code last}/{@code mode}/{@code context}
         * attributes the JS envelope writes.
         */
        @Test
        @DisplayName("emits <usync> with sid, index, last, mode, context attributes")
        void usyncEnvelope() {
            var iq = DeviceUSyncQueryBuilder.build(linked(USER_A), "message", null, false)
                    .getFirst()
                    .build();

            var usync = childByDescription(iq, "usync");
            assertNotNull(usync.getAttributeAsString("sid").orElse(null));
            assertEquals("0", usync.getAttributeAsString("index").orElseThrow());
            assertEquals("true", usync.getAttributeAsString("last").orElseThrow());
            assertEquals("query", usync.getAttributeAsString("mode").orElseThrow());
            assertEquals("message", usync.getAttributeAsString("context").orElseThrow());
        }

        /**
         * With {@code includeUsernameProtocol = false} the only
         * {@code <query>} child is {@code <devices version="2"/>}.
         */
        @Test
        @DisplayName("query has only <devices version=\"2\"/> when username protocol is disabled")
        void devicesOnly() {
            var iq = DeviceUSyncQueryBuilder.build(linked(USER_A), "query", null, false)
                    .getFirst()
                    .build();

            var query = childByDescription(childByDescription(iq, "usync"), "query");
            var children = query.streamChildren().toList();
            assertEquals(1, children.size());
            assertEquals("devices", children.getFirst().description());
            assertEquals("2", children.getFirst().getAttributeAsString("version").orElseThrow());
        }

        /**
         * With {@code includeUsernameProtocol = true} both
         * {@code <devices/>} and {@code <username/>} probes are present.
         */
        @Test
        @DisplayName("query has both <devices/> and <username/> when username protocol is enabled")
        void devicesAndUsername() {
            var iq = DeviceUSyncQueryBuilder.build(linked(USER_A), "query", null, true)
                    .getFirst()
                    .build();

            var query = childByDescription(childByDescription(iq, "usync"), "query");
            var children = query.streamChildren().toList();
            assertEquals(2, children.size());
            assertEquals("devices", children.getFirst().description());
            assertEquals("username", children.get(1).description());
        }

        /**
         * Each input JID appears as a {@code <user jid="...">} entry under
         * {@code <list>}.
         */
        @Test
        @DisplayName("each user appears as a <user jid=...> entry inside <list>")
        void userEntries() {
            var iq = DeviceUSyncQueryBuilder.build(linked(USER_A, USER_B), "message", null, false)
                    .getFirst()
                    .build();

            var list = childByDescription(childByDescription(iq, "usync"), "list");
            var users = list.streamChildren()
                    .filter(child -> "user".equals(child.description()))
                    .toList();
            assertEquals(2, users.size());

            var jids = users.stream()
                    .map(user -> user.getAttributeAsString("jid").orElseThrow())
                    .collect(Collectors.toSet());
            assertTrue(jids.contains(USER_A.toString()));
            assertTrue(jids.contains(USER_B.toString()));
        }

        /**
         * The PSA announcements account is filtered out, matching the WA
         * Web {@code USyncQuery.$3} {@code isServer} filter.
         */
        @Test
        @DisplayName("filters out the PSA announcements account")
        void filtersPsa() {
            var iq = DeviceUSyncQueryBuilder.build(linked(USER_A, PSA_ACCOUNT), "message", null, false)
                    .getFirst()
                    .build();

            var list = childByDescription(childByDescription(iq, "usync"), "list");
            var users = list.streamChildren()
                    .filter(child -> "user".equals(child.description()))
                    .toList();
            assertEquals(1, users.size());
            assertEquals(USER_A.toString(), users.getFirst().getAttributeAsString("jid").orElseThrow());
        }
    }

    /**
     * Asserts how the per-user dhash map drives the optional
     * {@code <devices>} child under each {@code <user>}.
     */
    @Nested
    @DisplayName("hash info delta updates")
    class HashInfoDeltaUpdates {
        /**
         * Canonical {@code ts} value for the dhash fixtures; arbitrary but
         * stable so the assertions stay byte-comparable.
         */
        private final Instant ts = Instant.parse("2026-01-01T00:00:00Z");

        /**
         * Canonical {@code expected_ts} value, one day after {@link #ts}.
         */
        private final Instant expectedTs = Instant.parse("2026-01-02T00:00:00Z");

        /**
         * No hash map means no per-user {@code <devices>} wrapper.
         */
        @Test
        @DisplayName("omits the per-user <devices> element when hash info is null")
        void noHashInfo() {
            var iq = DeviceUSyncQueryBuilder.build(linked(USER_A), "message", null, false)
                    .getFirst()
                    .build();

            var user = firstUser(iq);
            assertEquals(0, user.streamChildren().count());
        }

        /**
         * Populated dhash map emits {@code device_hash}, {@code ts}, and
         * {@code expected_ts} attributes on the inner {@code <devices>}.
         */
        @Test
        @DisplayName("emits <devices device_hash ts expected_ts> when all three are known")
        void fullHashInfo() {
            var hashInfo = new DeviceListHashInfoBuilder()
                    .hash("dGVzdA==")
                    .timestamp(ts)
                    .expectedTimestamp(expectedTs)
                    .build();

            var iq = DeviceUSyncQueryBuilder.build(
                    linked(USER_A), "message", Map.of(USER_A.toUserJid(), hashInfo), false)
                    .getFirst()
                    .build();

            var devices = childByDescription(firstUser(iq), "devices");
            assertEquals("dGVzdA==", devices.getAttributeAsString("device_hash").orElseThrow());
            assertEquals(String.valueOf(ts.getEpochSecond()), devices.getAttributeAsString("ts").orElseThrow());
            assertEquals(String.valueOf(expectedTs.getEpochSecond()), devices.getAttributeAsString("expected_ts").orElseThrow());
        }

        /**
         * A {@link DeviceListHashInfo} with no fields set means no inner
         * {@code <devices>} wrapper is emitted at all.
         */
        @Test
        @DisplayName("omits the inner <devices> element when every hash field is null")
        void allHashFieldsAbsent() {
            var hashInfo = new DeviceListHashInfoBuilder().build();

            var iq = DeviceUSyncQueryBuilder.build(
                    linked(USER_A), "message", Map.of(USER_A.toUserJid(), hashInfo), false)
                    .getFirst()
                    .build();

            var user = firstUser(iq);
            assertFalse(user.hasChild("devices"));
        }

        /**
         * Per-user {@code <devices>} is attached only to users that have a
         * matching dhash map entry.
         */
        @Test
        @DisplayName("only attaches per-user <devices> for users with a matching hash info entry")
        void mixedHashInfo() {
            var hashInfo = new DeviceListHashInfoBuilder()
                    .hash("dGVzdA==")
                    .timestamp(ts)
                    .build();

            var iq = DeviceUSyncQueryBuilder.build(
                    linked(USER_A, USER_B), "message",
                    Map.of(USER_A.toUserJid(), hashInfo), false)
                    .getFirst()
                    .build();

            var list = childByDescription(childByDescription(iq, "usync"), "list");
            var users = list.streamChildren()
                    .filter(child -> "user".equals(child.description()))
                    .toList();
            assertEquals(2, users.size());

            var withDevices = users.stream()
                    .filter(user -> user.getAttributeAsString("jid").orElseThrow().equals(USER_A.toString()))
                    .findFirst()
                    .orElseThrow();
            assertTrue(withDevices.hasChild("devices"));

            var withoutDevices = users.stream()
                    .filter(user -> user.getAttributeAsString("jid").orElseThrow().equals(USER_B.toString()))
                    .findFirst()
                    .orElseThrow();
            assertFalse(withoutDevices.hasChild("devices"));
        }
    }

    /**
     * Asserts the 500-user batching rule inside
     * {@link DeviceUSyncQueryBuilder#build(Set, String, Map, boolean)}.
     */
    @Nested
    @DisplayName("batching")
    class Batching {
        /**
         * Below the cap returns a single batch.
         */
        @Test
        @DisplayName("returns a single batch for fewer than 500 users")
        void singleBatch() {
            var users = generateUsers(250);
            var builders = DeviceUSyncQueryBuilder.build(users, "message", null, false);
            assertEquals(1, builders.size());
            assertEquals(250, countUsers(builders.getFirst().build()));
        }

        /**
         * Exactly at the cap stays as a single batch.
         */
        @Test
        @DisplayName("returns a single batch at exactly 500 users")
        void exactlyAtBoundary() {
            var users = generateUsers(500);
            var builders = DeviceUSyncQueryBuilder.build(users, "message", null, false);
            assertEquals(1, builders.size());
            assertEquals(500, countUsers(builders.getFirst().build()));
        }

        /**
         * One over the cap produces a 500-and-1 split.
         */
        @Test
        @DisplayName("splits into two batches when the user list exceeds the 500-cap")
        void splitsAtBoundary() {
            var users = generateUsers(501);
            var builders = DeviceUSyncQueryBuilder.build(users, "message", null, false);
            assertEquals(2, builders.size());

            var total = builders.stream()
                    .mapToInt(b -> countUsers(b.build()))
                    .sum();
            assertEquals(501, total);
        }

        /**
         * Large inputs slice into 500-user chunks plus a tail.
         */
        @Test
        @DisplayName("issues one batch per 500-user slice and a tail for the remainder")
        void multipleBatches() {
            var users = generateUsers(1234);
            var builders = DeviceUSyncQueryBuilder.build(users, "message", null, false);
            assertEquals(3, builders.size());

            var sizes = builders.stream().map(b -> countUsers(b.build())).toList();
            assertEquals(500, sizes.get(0));
            assertEquals(500, sizes.get(1));
            assertEquals(234, sizes.get(2));
        }

        /**
         * Each batch carries its own {@code sid}; reuse would let the
         * server collapse the batches into one in-flight session.
         */
        @Test
        @DisplayName("each batch carries its own sid (no reuse across batches)")
        void distinctSids() {
            var users = generateUsers(1234);
            var sids = new HashSet<String>();
            for (var b : DeviceUSyncQueryBuilder.build(users, "message", null, false)) {
                var sid = childByDescription(b.build(), "usync").getAttributeAsString("sid").orElseThrow();
                sids.add(sid);
            }
            assertEquals(3, sids.size());
        }
    }

    /**
     * Asserts the precondition checks on the public factory.
     */
    @Nested
    @DisplayName("input validation")
    class InputValidation {
        /**
         * Empty input still yields one IQ (with an empty {@code <list/>}).
         */
        @Test
        @DisplayName("returns an empty-list batch when userJids is empty")
        void emptyUserJids() {
            var builders = DeviceUSyncQueryBuilder.build(Set.of(), "message", null, false);
            assertEquals(1, builders.size());
            assertEquals(0, countUsers(builders.getFirst().build()));
        }

        /**
         * Null user set is rejected with {@link NullPointerException}.
         */
        @Test
        @DisplayName("rejects null userJids")
        void nullUserJids() {
            try {
                DeviceUSyncQueryBuilder.build(null, "message", null, false);
                throw new AssertionError("expected NullPointerException");
            } catch (NullPointerException expected) {
            }
        }

        /**
         * Null context string is rejected with {@link NullPointerException}.
         */
        @Test
        @DisplayName("rejects null context")
        void nullContext() {
            try {
                DeviceUSyncQueryBuilder.build(Set.of(USER_A), null, null, false);
                throw new AssertionError("expected NullPointerException");
            } catch (NullPointerException expected) {
            }
        }
    }

    /**
     * Wraps an array of JIDs in an insertion-ordered {@link LinkedHashSet}.
     *
     * @apiNote
     * Used by every shape test so the builder receives a deterministic
     * iteration order, which is required for the structural assertions.
     *
     * @param jids the JIDs to wrap
     * @return a {@link LinkedHashSet} containing the JIDs in insertion
     *         order
     */
    private static LinkedHashSet<Jid> linked(Jid... jids) {
        var set = new LinkedHashSet<Jid>();
        for (var jid : jids) {
            set.add(jid);
        }
        return set;
    }

    /**
     * Synthesises a {@link Set} of {@code count} synthetic user JIDs.
     *
     * @apiNote
     * Used by the batching tests; the resulting JIDs start at
     * {@code 10_000_000_000} and stay in insertion order so the batches
     * partition the input deterministically.
     *
     * @param count the number of synthetic users to generate
     * @return an insertion-ordered set of synthetic user JIDs
     */
    private static Set<Jid> generateUsers(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> Jid.of((10_000_000_000L + i) + "@s.whatsapp.net"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Counts the {@code <user>} entries inside the {@code <usync><list>}
     * envelope of the given IQ.
     *
     * @apiNote
     * Used by the batching tests to assert how many users landed in each
     * batch without forcing the test to know about the envelope layout.
     *
     * @param iq the built IQ {@link Node}
     * @return the number of {@code <user>} children
     */
    private static int countUsers(Node iq) {
        var list = childByDescription(childByDescription(iq, "usync"), "list");
        return (int) list.streamChildren()
                .filter(child -> "user".equals(child.description()))
                .count();
    }

    /**
     * Returns the first {@code <user>} child of the given IQ's
     * {@code <usync><list>} envelope.
     *
     * @apiNote
     * Used by the hash-info tests to walk past the envelope and assert on
     * the {@code <user>} attributes and {@code <devices>} child directly.
     *
     * @param iq the built IQ {@link Node}
     * @return the first {@code <user>} child
     * @throws AssertionError if no {@code <user>} child exists
     */
    private static Node firstUser(Node iq) {
        var list = childByDescription(childByDescription(iq, "usync"), "list");
        return list.streamChildren()
                .filter(child -> "user".equals(child.description()))
                .findFirst()
                .orElseThrow();
    }

    /**
     * Returns the child of {@code parent} whose description matches.
     *
     * @apiNote
     * Used as a structural accessor by every assertion in this class;
     * throws when the expected child is missing so the test fails fast at
     * the point of access rather than at a downstream
     * {@link java.util.Optional#orElseThrow()}.
     *
     * @param parent      the parent {@link Node}
     * @param description the description string to match
     * @return the matching child {@link Node}
     * @throws AssertionError if no child has the requested description
     */
    private static Node childByDescription(Node parent, String description) {
        var hit = parent.streamChildren()
                .filter(child -> description.equals(child.description()))
                .findFirst();
        if (hit.isEmpty()) {
            throw new AssertionError("no <" + description + "> child under <" + parent.description() + ">");
        }
        return hit.get();
    }
}
