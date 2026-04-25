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
 * Controls which emoji reactions subscribers are allowed to post on messages
 * published in a newsletter.
 *
 * <p>A newsletter admin may choose between four policies exposed by
 * {@link Type}:
 * <ul>
 *   <li>{@link Type#ALL} admits every emoji</li>
 *   <li>{@link Type#BASIC} restricts reactions to a small curated set</li>
 *   <li>{@link Type#NONE} disables reactions entirely</li>
 *   <li>{@link Type#BLOCKLIST} admits every emoji except those listed in
 *       {@link #blockedCodes()}</li>
 * </ul>
 *
 * <p>The {@linkplain #enabledTimestampSeconds() enabled timestamp} records
 * when the current policy was activated, allowing clients to invalidate
 * locally cached reaction aggregates that predate the change.
 */
@ProtobufMessage
public final class NewsletterReactionSettings {
    /**
     * The active reaction policy. Defaults to {@link Type#UNKNOWN} when not
     * set.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    Type value;

    /**
     * The emoji codes that are explicitly blocked when the policy is
     * {@link Type#BLOCKLIST}. Ignored for all other policies.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    List<String> blockedCodes;

    /**
     * The moment at which the current policy was last activated, used to
     * invalidate stale client-side caches.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.UINT64, mixins = InstantSecondsMixin.class)
    Instant enabledTimestamp;

    /**
     * Constructs a new {@code NewsletterReactionSettings}. Invoked by the
     * generated protobuf deserializer.
     *
     * @param value            the reaction policy, defaulted to
     *                         {@link Type#UNKNOWN} when {@code null}
     * @param blockedCodes     the blocked emoji codes, defaulted to an
     *                         empty mutable list when {@code null}
     * @param enabledTimestamp the moment the current policy was activated,
     *                         may be {@code null}
     */
    NewsletterReactionSettings(Type value, List<String> blockedCodes, Instant enabledTimestamp) {
        this.value = Objects.requireNonNullElse(value, Type.UNKNOWN);
        this.blockedCodes = Objects.requireNonNullElseGet(blockedCodes, ArrayList::new);
        this.enabledTimestamp = enabledTimestamp;
    }

    /**
     * Returns the active reaction policy.
     *
     * @return the current policy, never {@code null}
     */
    public Type value() {
        return value;
    }

    /**
     * Sets the active reaction policy.
     *
     * @param value the new policy, defaulted to {@link Type#UNKNOWN} when
     *              {@code null}
     */
    public void setValue(Type value) {
        this.value = Objects.requireNonNullElse(value, Type.UNKNOWN);
    }

    /**
     * Returns the emoji codes that are currently blocked.
     *
     * <p>The returned list is meaningful only when the policy is
     * {@link Type#BLOCKLIST}; for every other policy clients should ignore
     * it.
     *
     * @return an unmodifiable list of blocked emoji codes, never
     *         {@code null}
     */
    public List<String> blockedCodes() {
        return blockedCodes == null ? List.of(): Collections.unmodifiableList(blockedCodes);
    }

    /**
     * Sets the emoji codes that are blocked when the policy is
     * {@link Type#BLOCKLIST}.
     *
     * @param blockedCodes the new blocked codes list, or {@code null}
     */
    public void setBlockedCodes(List<String> blockedCodes) {
        this.blockedCodes = blockedCodes;
    }

    /**
     * Returns the moment at which the current policy was last activated.
     *
     * @return an {@link Optional} holding the activation instant, or empty
     *         if it is unknown
     */
    public Optional<Instant> enabledTimestampSeconds() {
        return Optional.ofNullable(enabledTimestamp);
    }

    /**
     * Sets the moment at which the current policy was activated.
     *
     * @param enabledTimestamp the new activation instant, or {@code null}
     */
    public void setEnabledTimestamp(Instant enabledTimestamp) {
        this.enabledTimestamp = enabledTimestamp;
    }

    /**
     * Returns whether these settings equal the supplied object.
     *
     * @param o the object to compare against
     * @return {@code true} if {@code o} is a
     *         {@code NewsletterReactionSettings} whose fields are all equal
     *         to this one's
     */
    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof NewsletterReactionSettings that
                            && value == that.value
                            && Objects.equals(blockedCodes, that.blockedCodes)
                            && Objects.equals(enabledTimestamp, that.enabledTimestamp);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return the hash code for these settings
     */
    @Override
    public int hashCode() {
        return Objects.hash(value, blockedCodes, enabledTimestamp);
    }

    /**
     * Enumerates the four reaction policies that may be configured for a
     * newsletter.
     *
     * @implNote WA Web's
     *           {@code WAWebCommonNewsletterEnums.NewsletterReactionCodesSetting}
     *           assigns {@code All:0}, {@code Basic:1}, {@code Blocklist:2},
     *           {@code None:3}. Cobalt preserves these wire indices exactly
     *           and appends an {@code UNKNOWN} sentinel at {@code -1}-like
     *           wire index {@code 4} to cover unrecognised server values
     *           without disturbing existing indices.
     */
    @ProtobufEnum
    public enum Type {
        /**
         * Every emoji is accepted as a reaction.
         */
        ALL(0),

        /**
         * Only a curated set of basic emoji is accepted as a reaction.
         */
        BASIC(1),

        /**
         * Every emoji is accepted except those listed in
         * {@link NewsletterReactionSettings#blockedCodes()}.
         */
        BLOCKLIST(2),

        /**
         * No reactions are accepted.
         */
        NONE(3),

        /**
         * The policy was not reported by the server or is unrecognized by
         * this version of the client.
         *
         * @implNote NO_WA_BASIS: defensive sentinel added by Cobalt; not
         *           present in
         *           {@code WAWebCommonNewsletterEnums.NewsletterReactionCodesSetting}.
         */
        UNKNOWN(4);

        /**
         * Lookup table from the lowercase enum name to the constant, used
         * by {@link #of(String)} for case-insensitive parsing.
         */
        private static final Map<String, Type> BY_NAME = Arrays.stream(values())
                .collect(Collectors.toUnmodifiableMap(key -> key.name().toLowerCase(), Function.identity()));

        /**
         * Returns the constant whose name matches the supplied string,
         * case-insensitively.
         *
         * @param name the policy name as reported by the server, may be
         *             {@code null}
         * @return the matching policy constant, or {@link #UNKNOWN} when
         *         {@code name} is {@code null} or does not match any
         *         constant
         */
        static Type of(String name) {
            return name == null ? UNKNOWN : BY_NAME.getOrDefault(name.toLowerCase(), UNKNOWN);
        }

        /**
         * The protobuf wire index associated with this constant.
         */
        final int index;

        /**
         * Constructs a new enum constant bound to the supplied protobuf
         * wire index.
         *
         * @param index the protobuf wire index
         */
        Type(@ProtobufEnumIndex int index) {
            this.index = index;
        }
    }
}
