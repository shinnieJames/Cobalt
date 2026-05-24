package com.github.auties00.cobalt.sync.factory;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionValueSpec;
import com.github.auties00.cobalt.sync.SyncFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises the outgoing-mutation wire shape produced by
 * {@link NoteEditMutationFactory}.
 *
 * @apiNote
 * Pairs with
 * {@link com.github.auties00.cobalt.sync.handler.NoteEditHandler} whose
 * incoming-side coverage lives in {@code NoteEditHandlerTest}; the
 * parity target is {@code WAWebNoteSync.getNoteMutation}.
 *
 * @implNote
 * This implementation calls the deterministic-timestamp overload of
 * {@link NoteEditMutationFactory#getNoteEditMutation(String, Jid, String, boolean, Instant)}
 * so the captured oracle's pinned {@code timestampSeconds} matches the
 * re-encoded bytes; the public overload that stamps
 * {@link Instant#now()} is not exercised here because it cannot be
 * pinned without injecting a clock.
 */
@DisplayName("NoteEditMutationFactory")
class NoteEditMutationFactoryTest {
    /**
     * The chat JID used as the {@link NoteEditMutationFactory#getNoteEditMutation}
     * recipient for every test in this class; chosen as a stable user JID
     * the protobuf encoder will emit without further normalisation.
     */
    private static final Jid CHAT_JID = Jid.of("12345@s.whatsapp.net");

    /**
     * The factory under test, freshly constructed per test method.
     */
    private NoteEditMutationFactory factory;

    /**
     * Constructs the per-test factory instance.
     *
     * @apiNote
     * Required by JUnit's per-method fixture lifecycle; refreshing the
     * factory ensures no state leaks between tests even though the
     * production class is currently stateless.
     */
    @BeforeEach
    void setUp() {
        factory = new NoteEditMutationFactory();
    }

    /**
     * Verifies that the encoded {@link SyncActionValueSpec} bytes match
     * the captured WA Web oracle when the fixture is present.
     */
    @Test
    @DisplayName("captured SyncActionValue bytes match Cobalt's encoded output when the fixture is present")
    void byteEqualityWithOracle() {
        if (!SyncFixtures.isOracleAvailable("handler/note-edit/encode")) return;
        var oracle = SyncFixtures.loadOracle("handler/note-edit/encode");
        var expected = SyncFixtures.decodeOracleBytes(oracle, "encoded");

        var pending = factory.getNoteEditMutation(
                "note-oracle", CHAT_JID, "body", false,
                Instant.ofEpochSecond(oracle.getLong("timestampSeconds")));
        var actual = SyncActionValueSpec.encode(pending.mutation().value());

        assertNotNull(actual);
        assertArrayEquals(expected, actual);
    }
}
