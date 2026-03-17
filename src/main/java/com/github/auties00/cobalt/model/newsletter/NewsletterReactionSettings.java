package com.github.auties00.cobalt.model.newsletter;

import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The reaction settings for a newsletter, controlling which emoji
 * reactions are allowed on messages.
 *
 * <p>The {@link Type} determines the overall reaction policy (all,
 * basic set, none, or a custom blocklist). When the type is
 * {@link Type#BLOCKLIST}, the {@link #blockedCodes()} list contains
 * the specific emoji codes that are not permitted.
 */
@ProtobufMessage
public final class NewsletterReactionSettings {
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    Type value;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    List<String> blockedCodes;

    @ProtobufProperty(index = 3, type = ProtobufType.UINT64, mixins = InstantSecondsMixin.class)
    Instant enabledTimestamp;

    /**
     * Constructs a new {@code NewsletterReactionSettings} with the specified
     * type, blocked codes, and enabled timestamp.
     *
     * @param value            the reaction type policy, defaults to {@link Type#UNKNOWN}
     *                         if {@code null}
     * @param blockedCodes     the list of blocked emoji codes, may be {@code null}
     * @param enabledTimestamp the timestamp when reactions were enabled, may be {@code null}
     */
    NewsletterReactionSettings(Type value, List<String> blockedCodes, Instant enabledTimestamp) {
        this.value = Objects.requireNonNullElse(value, Type.UNKNOWN);
        this.blockedCodes = Objects.requireNonNullElseGet(blockedCodes, ArrayList::new);
        this.enabledTimestamp = enabledTimestamp;
    }

    /**
     * Returns the reaction type policy.
     *
     * @return the reaction type, never {@code null}
     */
    public Type value() {
        return value;
    }

    /**
     * Sets the reaction type policy.
     *
     * @param value the reaction type, defaults to {@link Type#UNKNOWN} if {@code null}
     */
    public void setValue(Type value) {
        this.value = Objects.requireNonNullElse(value, Type.UNKNOWN);
    }

    /**
     * Returns the list of blocked emoji codes.
     *
     * @return an unmodifiable list of blocked codes, never {@code null}
     */
    public List<String> blockedCodes() {
        return blockedCodes == null ? List.of(): Collections.unmodifiableList(blockedCodes);
    }

    /**
     * Sets the list of blocked emoji codes.
     *
     * @param blockedCodes the blocked codes list
     */
    public void setBlockedCodes(List<String> blockedCodes) {
        this.blockedCodes = blockedCodes;
    }

    /**
     * Returns the timestamp when reactions were enabled, if available.
     *
     * @return an {@link Optional} containing the enabled timestamp,
     *         or empty if not set
     */
    public Optional<Instant> enabledTimestampSeconds() {
        return Optional.ofNullable(enabledTimestamp);
    }

    /**
     * Sets the timestamp when reactions were enabled.
     *
     * @param enabledTimestamp the enabled timestamp
     */
    public void setEnabledTimestamp(Instant enabledTimestamp) {
        this.enabledTimestamp = enabledTimestamp;
    }

    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof NewsletterReactionSettings that
                            && value == that.value
                            && Objects.equals(blockedCodes, that.blockedCodes)
                            && Objects.equals(enabledTimestamp, that.enabledTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, blockedCodes, enabledTimestamp);
    }

    /**
     * The reaction policy type for a newsletter.
     *
     * @since 0.1.0
     */
    @ProtobufEnum
    public enum Type {
        /**
         * The reaction policy is not known.
         */
        UNKNOWN(0),

        /**
         * All emoji reactions are allowed.
         */
        ALL(1),

        /**
         * Only a basic set of emoji reactions is allowed.
         */
        BASIC(2),

        /**
         * No reactions are allowed.
         */
        NONE(3),

        /**
         * All reactions except those in the blocklist are allowed.
         */
        BLOCKLIST(4);

        private static final Map<String, Type> BY_NAME = Arrays.stream(values())
                .collect(Collectors.toUnmodifiableMap(key -> key.name().toLowerCase(), Function.identity()));

        /**
         * Returns the {@code Type} constant matching the given
         * case-insensitive name, or {@link #UNKNOWN} if no match is found.
         *
         * @param name the type name, may be {@code null}
         * @return the matching type constant, never {@code null}
         */
        static Type of(String name) {
            return name == null ? UNKNOWN : BY_NAME.getOrDefault(name.toLowerCase(), UNKNOWN);
        }

        final int index;

        Type(@ProtobufEnumIndex int index) {
            this.index = index;
        }
    }
}
