package com.github.auties00.cobalt.registration.push.apns.plist.value;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Plist {@code <array>} node holding an ordered list of nested
 * {@link PlistValue} entries.
 *
 * @apiNote
 * Used wherever an APNS plist payload nests a sequence of homogeneous
 * or mixed values; in binary plists this is the {@code 0xA0..0xAF}
 * type marker. Iteration order matches the wire order so signature
 * recomputation over the decoded tree stays bit-identical.
 *
 * @implNote
 * This implementation stores the supplied {@link List} by reference
 * and exposes an unmodifiable view through {@link #items()}, avoiding
 * the per-construction defensive copy that would otherwise inflate
 * parsing of large arrays.
 *
 * @param items the contained values
 */
public record PlistArrayValue(List<PlistValue> items) implements PlistValue {
    /**
     * Canonical constructor that rejects a {@code null} backing list.
     *
     * @apiNote
     * The list itself must be non-{@code null}; individual entries may
     * be any concrete {@link PlistValue}.
     *
     * @param items the items
     * @throws NullPointerException if {@code items} is {@code null}
     */
    public PlistArrayValue {
        Objects.requireNonNull(items, "items");
    }

    /**
     * Returns an unmodifiable view of the backing list.
     *
     * @apiNote
     * Callers receive a read-only window onto the stored list; mutating
     * the underlying list (held privately by the parser that built this
     * record) is not supported and is not exposed through this view.
     *
     * @return an unmodifiable list of the items
     */
    @Override
    public List<PlistValue> items() {
        return Collections.unmodifiableList(items);
    }
}
