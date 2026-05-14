package com.github.auties00.cobalt.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link LidMigrationState}.
 *
 * <p>Covers the {@link LidMigrationState#isTerminal()} truth table over
 * every value, and pins the protobuf wire indices so the persisted form
 * cannot drift.
 *
 * <p>Indices 1–5 correspond one-to-one with WA Web's
 * {@code LidThreadMigrationStatus} enum exposed by
 * {@code WAWebLid1X1ThreadAccountMigrations.flow}; indices 0, 6, 7 are
 * Cobalt-only states ({@code NOT_STARTED}, {@code FAILED},
 * {@code DISABLED}) introduced because Cobalt drives the migration
 * through an explicit state machine rather than ad-hoc UserPrefs flags.
 */
@DisplayName("LidMigrationState")
class LidMigrationStateTest {

    @Test
    @DisplayName("isTerminal truth table")
    void isTerminalTruthTable() {
        assertFalse(LidMigrationState.NOT_STARTED.isTerminal());
        assertFalse(LidMigrationState.WAITING_PROP.isTerminal());
        assertFalse(LidMigrationState.WAITING_MAPPINGS.isTerminal());
        assertFalse(LidMigrationState.READY.isTerminal());
        assertFalse(LidMigrationState.IN_PROGRESS.isTerminal());
        assertTrue(LidMigrationState.COMPLETE.isTerminal());
        assertTrue(LidMigrationState.FAILED.isTerminal());
        assertTrue(LidMigrationState.DISABLED.isTerminal());
    }

    @Test
    @DisplayName("protobuf indices are stable and contiguous 0..7")
    void protobufIndicesAreStable() {
        assertEquals(0, LidMigrationState.NOT_STARTED.index);
        assertEquals(1, LidMigrationState.WAITING_PROP.index);
        assertEquals(2, LidMigrationState.WAITING_MAPPINGS.index);
        assertEquals(3, LidMigrationState.READY.index);
        assertEquals(4, LidMigrationState.IN_PROGRESS.index);
        assertEquals(5, LidMigrationState.COMPLETE.index);
        assertEquals(6, LidMigrationState.FAILED.index);
        assertEquals(7, LidMigrationState.DISABLED.index);
    }

    @Test
    @DisplayName("ordinal matches index for every value")
    void ordinalMatchesIndex() {
        for (var state : LidMigrationState.values()) {
            assertEquals(state.ordinal(), state.index,
                    "ordinal must equal index for " + state);
        }
    }

    @Test
    @DisplayName("exactly 8 values declared")
    void valueCount() {
        assertEquals(8, LidMigrationState.values().length);
    }
}
