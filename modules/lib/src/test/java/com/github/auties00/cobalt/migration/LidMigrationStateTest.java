package com.github.auties00.cobalt.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link LidMigrationState}.
 *
 * @apiNote
 * Pins the {@link LidMigrationState#isTerminal()} truth table over every
 * value and pins the protobuf wire indices so the persisted form cannot
 * drift; indices 1 to 5 round-trip with WA Web's
 * {@code LidThreadMigrationStatus} enum exported from
 * {@code WAWebLid1X1ThreadAccountMigrations.flow}, and indices 0, 6, 7
 * are the Cobalt-only {@code NOT_STARTED} / {@code FAILED} /
 * {@code DISABLED} sinks.
 *
 * @implNote
 * This implementation pins the indices explicitly rather than relying on
 * {@link Enum#ordinal()} so a future reordering that left ordinals stable
 * but changed the meaning of an index would still be caught.
 */
@DisplayName("LidMigrationState")
class LidMigrationStateTest {

    /**
     * {@link LidMigrationState#isTerminal()} returns the expected boolean
     * for every value: only {@code COMPLETE} / {@code FAILED} /
     * {@code DISABLED} are terminal.
     */
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

    /**
     * The protobuf wire indices are stable, contiguous, and identical to
     * the {@link Enum#ordinal()} of every value.
     */
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

    /**
     * Every value's {@link Enum#ordinal()} matches its declared protobuf
     * index.
     *
     * @apiNote
     * Catches a future reorder that would otherwise produce a silent index
     * vs ordinal drift.
     */
    @Test
    @DisplayName("ordinal matches index for every value")
    void ordinalMatchesIndex() {
        for (var state : LidMigrationState.values()) {
            assertEquals(state.ordinal(), state.index,
                    "ordinal must equal index for " + state);
        }
    }

    /**
     * The enum has exactly eight values.
     */
    @Test
    @DisplayName("exactly 8 values declared")
    void valueCount() {
        assertEquals(8, LidMigrationState.values().length);
    }
}
