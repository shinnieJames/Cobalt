package com.github.auties00.cobalt.migration;

import com.github.auties00.cobalt.model.jid.Jid;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Smoke tests for {@link MigrationFixtures}.
 *
 * <p>The migration fixtures directory may be empty for a fresh checkout
 * (live-oracle captures are committed independently), so these tests
 * exercise only the parts of the helper that do not require a corpus on
 * disk: {@link MigrationFixtures#isAvailable} discrimination and
 * {@link MigrationFixtures#temporaryStore} round-tripping the caller's
 * self-PN and self-LID.
 */
class MigrationFixturesTest {

    @Test
    void isAvailableReturnsFalseForMissingTopic() {
        assertFalse(MigrationFixtures.isAvailable("does-not-exist"));
    }

    @Test
    void temporaryStoreReflectsCallerJids() {
        var pn = Jid.of("19254863482@s.whatsapp.net");
        var lid = Jid.of("83116928594056@lid");
        var store = MigrationFixtures.temporaryStore(pn, lid);
        assertEquals(pn, store.jid().orElseThrow());
        assertEquals(lid, store.lid().orElseThrow());
    }

    @Test
    void temporaryStoreAllowsNullLid() {
        var pn = Jid.of("19254863482@s.whatsapp.net");
        var store = MigrationFixtures.temporaryStore(pn, null);
        assertEquals(pn, store.jid().orElseThrow());
        assertFalse(store.lid().isPresent());
    }
}
