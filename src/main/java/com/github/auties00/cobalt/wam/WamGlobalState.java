package com.github.auties00.cobalt.wam;

import com.github.auties00.cobalt.wam.binary.WamGlobalEncoder;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks previously-written WAM global attribute values so that only
 * changed (dirty) globals are re-serialized into subsequent buffers.
 *
 * <p>In the WhatsApp Web implementation, globals are written
 * incrementally: only fields whose value differs from the last write
 * are emitted. The per-event {@code commitTime} (field 47) and
 * per-private-channel {@code psId} (field 6005) are always considered
 * dirty because they change with every event or buffer respectively.
 * The {@code beaconSessionId} (field 3433) is also per-event when
 * beaconing is active.
 *
 * <p>This class is not thread-safe; all calls must be made from the
 * single WAM flush thread.
 *
 * @apiNote WAWebWamLibContext: tracks prevGlobals and dirtyGlobals,
 * only writes globals whose value changed since the last write.
 */
final class WamGlobalState {
    /**
     * The previously-written value for each global field id. Values are
     * boxed {@code Long} for integers and {@code String} for strings.
     */
    private final Map<Integer, Object> previousValues;

    /**
     * Constructs a new {@code WamGlobalState} with no recorded values.
     */
    WamGlobalState() {
        this.previousValues = new HashMap<>();
    }

    /**
     * Returns whether the given integer global value differs from the
     * previously recorded value and should be written. If dirty, the
     * new value is recorded.
     *
     * @param fieldId the global field identifier
     * @param value   the current value
     * @return {@code true} if the value changed since the last write
     */
    boolean isDirtyInt(int fieldId, long value) {
        var prev = previousValues.get(fieldId);
        if (prev instanceof Long l && l == value) {
            return false;
        }
        previousValues.put(fieldId, value);
        return true;
    }

    /**
     * Returns whether the given string global value differs from the
     * previously recorded value and should be written. If dirty, the
     * new value is recorded.
     *
     * @param fieldId the global field identifier
     * @param value   the current value, must not be {@code null}
     * @return {@code true} if the value changed since the last write
     */
    boolean isDirtyString(int fieldId, String value) {
        var prev = previousValues.get(fieldId);
        if (value.equals(prev)) {
            return false;
        }
        previousValues.put(fieldId, value);
        return true;
    }

    /**
     * Returns whether the given boolean global value (encoded as
     * {@code 0} or {@code 1}) differs from the previously recorded
     * value and should be written. If dirty, the new value is recorded.
     *
     * @param fieldId the global field identifier
     * @param value   the current boolean value
     * @return {@code true} if the value changed since the last write
     */
    boolean isDirtyBool(int fieldId, boolean value) {
        return isDirtyInt(fieldId, value ? 1 : 0);
    }

    /**
     * Resets all tracked values, causing every global to be considered
     * dirty on the next check. This should be called when starting a
     * new buffer.
     */
    void reset() {
        previousValues.clear();
    }
}
