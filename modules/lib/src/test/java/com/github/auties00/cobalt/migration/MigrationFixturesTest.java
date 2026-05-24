package com.github.auties00.cobalt.migration;

import com.github.auties00.cobalt.model.jid.Jid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Smoke tests for {@link MigrationFixtures} that exercise only the
 * helpers that do not require a fixture corpus on disk.
 *
 * @apiNote
 * The migration-fixture directory may be empty for a fresh checkout
 * (the live-oracle captures are committed independently); these tests
 * pin {@link MigrationFixtures#isAvailable(String)} discrimination
 * and {@link MigrationFixtures#temporaryStore(Jid, Jid)} round-tripping
 * so the corpus-less subset of the helper is always exercised.
 *
 * @implNote
 * This test class is Cobalt-internal: no WA Web counterpart exists
 * for these helpers and MCP grounding is not applicable.
 */
class MigrationFixturesTest {

    /**
     * Verifies that a missing fixture topic reports as unavailable.
     */
    @Test
    void isAvailableReturnsFalseForMissingTopic() {
        assertFalse(MigrationFixtures.isAvailable("does-not-exist"));
    }

    /**
     * Verifies that a temporary store reflects both the caller's
     * self-PN and self-LID.
     */
    @Test
    void temporaryStoreReflectsCallerJids() {
        var pn = Jid.of("19254863482@s.whatsapp.net");
        var lid = Jid.of("83116928594056@lid");
        var store = MigrationFixtures.temporaryStore(pn, lid);
        assertEquals(pn, store.jid().orElseThrow());
        assertEquals(lid, store.lid().orElseThrow());
    }

    /**
     * Verifies that omitting the self-LID leaves the store in a
     * pre-LID-migration shape.
     */
    @Test
    void temporaryStoreAllowsNullLid() {
        var pn = Jid.of("19254863482@s.whatsapp.net");
        var store = MigrationFixtures.temporaryStore(pn, null);
        assertEquals(pn, store.jid().orElseThrow());
        assertFalse(store.lid().isPresent());
    }
}
