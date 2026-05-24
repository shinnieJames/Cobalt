package com.github.auties00.cobalt.registration.push.apns.plist.value;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;

/**
 * Plist {@code <dict>} node holding ordered string-keyed entries, the
 * binary plist {@code 0xD0..0xDF} marker family.
 *
 * @apiNote
 * Used wherever an APNS plist payload nests a keyed structure (the
 * connect handshake, the FairPlay descriptor, every notification
 * envelope); iteration order matches the source order so signature
 * recomputation over a re-encoded tree stays bit-identical with the
 * captured wire bytes.
 *
 * @implNote
 * This implementation backs the entries with a {@link SequencedMap}
 * (typically a {@link LinkedHashMap} produced by the parser) and
 * exposes an unmodifiable view via {@link #entries()}, avoiding the
 * per-construction defensive copy that would otherwise inflate parsing
 * of large dictionaries.
 *
 * @param entries the ordered entries
 */
public record PlistDictionaryValue(SequencedMap<String, PlistValue> entries) implements PlistValue {
    /**
     * Canonical constructor that rejects a {@code null} backing map.
     *
     * @param entries the ordered entries
     * @throws NullPointerException if {@code entries} is {@code null}
     */
    public PlistDictionaryValue {
        Objects.requireNonNull(entries, "entries");
    }

    /**
     * Returns an unmodifiable view of the backing map.
     *
     * @apiNote
     * Iteration order matches the order the parser observed in the
     * source bytes; mutating the underlying map is not supported and
     * is not exposed through this view.
     *
     * @return an unmodifiable sequenced map of the entries
     */
    @Override
    public SequencedMap<String, PlistValue> entries() {
        return Collections.unmodifiableSequencedMap(entries);
    }

    /**
     * Returns the value stored under {@code key}, if any.
     *
     * @apiNote
     * Convenience over {@link SequencedMap#get(Object)} that hides the
     * {@code null}-vs-absent distinction behind {@link Optional}.
     *
     * @param key the lookup key
     * @return an {@link Optional} containing the value, or empty when
     *         absent
     */
    public Optional<PlistValue> get(String key) {
        return Optional.ofNullable(entries.get(key));
    }

    /**
     * Returns a new {@link Builder} for assembling dictionaries
     * incrementally.
     *
     * @return a fresh builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Mutable builder that produces an immutable
     * {@link PlistDictionaryValue}.
     *
     * @apiNote
     * Used by call sites that synthesise an APNS request payload one
     * field at a time; each {@code put} returns {@code this} so calls
     * chain naturally.
     *
     * @implNote
     * This implementation backs the in-progress entries with a
     * {@link LinkedHashMap} so insertion order survives into the
     * resulting dictionary; {@link #build()} snapshots the map so
     * later mutations of the builder do not bleed into the returned
     * value.
     */
    public static final class Builder {
        /**
         * Insertion-ordered backing map populated by every {@code put}
         * overload until {@link #build()} is called.
         */
        private final LinkedHashMap<String, PlistValue> map = new LinkedHashMap<>();

        /**
         * Hidden constructor.
         *
         * @apiNote
         * Instances are obtained from
         * {@link PlistDictionaryValue#builder()}; the constructor is
         * private so the only entry point is the factory method.
         */
        private Builder() {
        }

        /**
         * Stores a {@link PlistValue} under {@code key}.
         *
         * @param key   the key
         * @param value the value
         * @return this builder
         * @throws NullPointerException if either argument is {@code null}
         */
        public Builder put(String key, PlistValue value) {
            map.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
            return this;
        }

        /**
         * Wraps {@code value} in a {@link PlistStringValue} and stores
         * it under {@code key}.
         *
         * @param key   the key
         * @param value the string
         * @return this builder
         * @throws NullPointerException if either argument is {@code null}
         */
        public Builder put(String key, String value) {
            return put(key, new PlistStringValue(value));
        }

        /**
         * Wraps {@code value} in a {@link PlistDataValue} and stores
         * it under {@code key}.
         *
         * @apiNote
         * The bytes are retained by reference for zero-copy
         * serialization; the caller must not mutate {@code value}
         * after the call.
         *
         * @param key   the key
         * @param value the bytes
         * @return this builder
         */
        public Builder put(String key, byte[] value) {
            return put(key, new PlistDataValue(value));
        }

        /**
         * Wraps {@code value} in a {@link PlistBooleanValue} and
         * stores it under {@code key}.
         *
         * @param key   the key
         * @param value the boolean
         * @return this builder
         */
        public Builder put(String key, boolean value) {
            return put(key, new PlistBooleanValue(value));
        }

        /**
         * Wraps {@code value} in a {@link PlistIntegerValue} and
         * stores it under {@code key}.
         *
         * @param key   the key
         * @param value the integer
         * @return this builder
         */
        public Builder put(String key, long value) {
            return put(key, new PlistIntegerValue(value));
        }

        /**
         * Snapshots the accumulated entries into an immutable
         * {@link PlistDictionaryValue}.
         *
         * @apiNote
         * Safe to call multiple times; each call returns a new
         * dictionary built from the current builder state.
         *
         * @implNote
         * This implementation copies the in-progress
         * {@link LinkedHashMap} so subsequent {@code put} calls on the
         * builder do not affect dictionaries already returned.
         *
         * @return the resulting dictionary
         */
        public PlistDictionaryValue build() {
            return new PlistDictionaryValue(new LinkedHashMap<>(map));
        }
    }
}
