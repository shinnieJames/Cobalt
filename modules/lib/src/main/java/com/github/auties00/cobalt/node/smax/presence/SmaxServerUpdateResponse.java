package com.github.auties00.cobalt.node.smax.presence;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Objects;
import java.util.Optional;

/**
 * The sealed family of inbound {@code <presence/>} server-pushed
 * update variants.
 *
 * @apiNote
 * Surfaced by WA Web's
 * {@code WASmaxPresenceServerUpdateRPC.receiveServerUpdateRPC},
 * consumed by {@code WAWebHandlePresence} to drive the
 * {@code WAWebChangeGroupPresenceHandlerAction} and
 * {@code WAWebChangePresenceHandlerAction} dispatchers; Cobalt
 * embedders pattern-match on the variant to update their local view
 * of peer online state and last-seen timestamps.
 */
@WhatsAppWebModule(moduleName = "WASmaxInPresenceServerUpdateRequest")
@WhatsAppWebModule(moduleName = "WASmaxInPresencePresenceUpdates")
public sealed interface SmaxServerUpdateResponse extends SmaxOperation.Response
        permits SmaxServerUpdateResponse.GroupAvailable, SmaxServerUpdateResponse.GroupUnavailable,
        SmaxServerUpdateResponse.LastSeenWithOtherValue, SmaxServerUpdateResponse.UserUnavailable,
        SmaxServerUpdateResponse.Available {

    /**
     * Tries each {@link SmaxServerUpdateResponse} variant in the WA
     * Web declared order.
     *
     * @apiNote
     * Models the disjunction in
     * {@code WASmaxInPresencePresenceUpdates.parsePresenceUpdates}:
     * {@link GroupAvailable} first, then {@link GroupUnavailable},
     * then {@link LastSeenWithOtherValue}, then {@link UserUnavailable},
     * then {@link Available}; embedders pass the inbound stanza and
     * pattern-match on the returned variant.
     *
     * @implNote
     * This implementation returns {@link Optional#empty()} when no
     * documented variant matches the stanza shape; WA Web instead
     * raises an {@code errorMixinDisjunction} so the upstream
     * {@code WAWebHandlePresence} promise rejects through its catch
     * block. Cobalt's dispatcher routes the empty Optional through
     * the configurable error handler instead.
     *
     * @param node the inbound {@code <presence/>} stanza; never
     *             {@code null}
     * @return an {@link Optional} carrying the parsed variant
     * @throws NullPointerException if {@code node} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxPresenceServerUpdateRPC",
            exports = "receiveServerUpdateRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxInPresencePresenceUpdates",
            exports = "parsePresenceUpdates", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxServerUpdateResponse> of(Node node) {
        Objects.requireNonNull(node, "node cannot be null");
        var groupAvailable = GroupAvailable.of(node);
        if (groupAvailable.isPresent()) {
            return groupAvailable;
        }
        var groupUnavailable = GroupUnavailable.of(node);
        if (groupUnavailable.isPresent()) {
            return groupUnavailable;
        }
        var lastSeenWithOtherValue = LastSeenWithOtherValue.of(node);
        if (lastSeenWithOtherValue.isPresent()) {
            return lastSeenWithOtherValue;
        }
        var userUnavailable = UserUnavailable.of(node);
        if (userUnavailable.isPresent()) {
            return userUnavailable;
        }
        return Available.of(node);
    }

    /**
     * The variant reporting how many members of a group are currently
     * online.
     *
     * @apiNote
     * Drives {@code WAWebChangeGroupPresenceHandlerAction} with a
     * positive count; embedders surface the value in the group's
     * online-member badge.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInPresenceGroupAvailableMixin")
    final class GroupAvailable implements SmaxServerUpdateResponse {
        /**
         * The group JID the count applies to.
         */
        private final Jid from;

        /**
         * The number of currently-online members, in {@code [1, 1024]}.
         *
         * @apiNote
         * The {@code 1024} ceiling matches
         * {@code WASmaxParseUtils.attrIntRange(e, "count", 1, 1024)}.
         */
        private final int count;

        /**
         * Constructs a new {@code GroupAvailable} projection.
         *
         * @apiNote
         * Called by {@link #of(Node)} after the stanza passes the
         * group-JID and {@code count} range validation.
         *
         * @param from  the group JID; never {@code null}
         * @param count the online-member count
         * @throws NullPointerException if {@code from} is {@code null}
         */
        public GroupAvailable(Jid from, int count) {
            this.from = Objects.requireNonNull(from, "from cannot be null");
            this.count = count;
        }

        /**
         * Returns the group JID.
         *
         * @apiNote
         * Embedders convert this to a chat WID via
         * {@code WAWebJidToWid.chatJidToChatWid} before driving the
         * presence handler action, matching the WA Web pipeline.
         *
         * @return the group JID; never {@code null}
         */
        public Jid from() {
            return from;
        }

        /**
         * Returns the online-member count.
         *
         * @apiNote
         * In {@code [1, 1024]}; values outside the range cause
         * {@link #of(Node)} to reject the stanza.
         *
         * @return the count
         */
        public int count() {
            return count;
        }

        /**
         * Tries to parse a {@link GroupAvailable} variant.
         *
         * @apiNote
         * Mirrors
         * {@code WASmaxInPresenceGroupAvailableMixin.parseGroupAvailableMixin};
         * empty when the stanza is not a {@code <presence/>}, when
         * {@code from} is not a {@code g.us} JID, or when
         * {@code count} is missing or outside {@code [1, 1024]}.
         *
         * @param node the inbound presence stanza
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInPresenceGroupAvailableMixin",
                exports = "parseGroupAvailableMixin",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<GroupAvailable> of(Node node) {
            if (!node.hasDescription("presence")) {
                return Optional.empty();
            }
            var from = node.getAttributeAsJid("from").orElse(null);
            if (from == null) {
                return Optional.empty();
            }
            if (!"g.us".equals(from.server().toString())) {
                return Optional.empty();
            }
            var count = node.getAttributeAsInt("count");
            if (count.isEmpty()) {
                return Optional.empty();
            }
            if (count.getAsInt() < 1 || count.getAsInt() > 1024) {
                return Optional.empty();
            }
            return Optional.of(new GroupAvailable(from, count.getAsInt()));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (GroupAvailable) obj;
            return this.count == that.count && Objects.equals(this.from, that.from);
        }

        @Override
        public int hashCode() {
            return Objects.hash(from, count);
        }

        @Override
        public String toString() {
            return "SmaxServerUpdateResponse.GroupAvailable[from=" + from
                    + ", count=" + count + ']';
        }
    }

    /**
     * The variant reporting a group has dropped to zero online
     * members.
     *
     * @apiNote
     * Drives {@code WAWebChangeGroupPresenceHandlerAction} with
     * {@code count=0}; embedders clear the group's online-member
     * badge.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInPresenceGroupUnavailableMixin")
    final class GroupUnavailable implements SmaxServerUpdateResponse {
        /**
         * The group JID gone idle.
         */
        private final Jid from;

        /**
         * Constructs a new {@code GroupUnavailable} projection.
         *
         * @apiNote
         * Called by {@link #of(Node)} once the stanza is confirmed as
         * a group-scoped {@code type="unavailable"} presence.
         *
         * @param from the group JID; never {@code null}
         * @throws NullPointerException if {@code from} is {@code null}
         */
        public GroupUnavailable(Jid from) {
            this.from = Objects.requireNonNull(from, "from cannot be null");
        }

        /**
         * Returns the group JID.
         *
         * @apiNote
         * Embedders convert this to a chat WID via
         * {@code WAWebJidToWid.chatJidToChatWid}.
         *
         * @return the group JID; never {@code null}
         */
        public Jid from() {
            return from;
        }

        /**
         * Tries to parse a {@link GroupUnavailable} variant.
         *
         * @apiNote
         * Mirrors
         * {@code WASmaxInPresenceGroupUnavailableMixin.parseGroupUnavailableMixin};
         * empty when {@code from} is not a {@code g.us} JID or when
         * {@code type} is not {@code "unavailable"}.
         *
         * @param node the inbound presence stanza
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInPresenceGroupUnavailableMixin",
                exports = "parseGroupUnavailableMixin",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<GroupUnavailable> of(Node node) {
            if (!node.hasDescription("presence")) {
                return Optional.empty();
            }
            var from = node.getAttributeAsJid("from").orElse(null);
            if (from == null) {
                return Optional.empty();
            }
            if (!"g.us".equals(from.server().toString())) {
                return Optional.empty();
            }
            if (!node.hasAttribute("type", "unavailable")) {
                return Optional.empty();
            }
            return Optional.of(new GroupUnavailable(from));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (GroupUnavailable) obj;
            return Objects.equals(this.from, that.from);
        }

        @Override
        public int hashCode() {
            return Objects.hash(from);
        }

        @Override
        public String toString() {
            return "SmaxServerUpdateResponse.GroupUnavailable[from=" + from + ']';
        }
    }

    /**
     * The variant reporting a peer is offline with a
     * privacy-suppressed last-seen sentinel.
     *
     * @apiNote
     * Surfaces when the peer's privacy settings forbid the
     * subscriber from seeing a real last-seen timestamp; WA Web's
     * {@code WAWebHandlePresence} maps {@code "deny"} to the
     * {@code deny} attribute on the dispatched presence change and
     * derives the {@code t} field from the current time via
     * {@code WATimeUtils.unixTime()}.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInPresenceLastSeenWithOtherValueMixin")
    @WhatsAppWebModule(moduleName = "WASmaxInPresenceEnums")
    final class LastSeenWithOtherValue implements SmaxServerUpdateResponse {
        /**
         * The user JID gone offline.
         */
        private final Jid from;

        /**
         * The optional sentinel value.
         *
         * @apiNote
         * One of {@code "deny"} (privacy block), {@code "error"}
         * (relay-side fault), or {@code "none"} (no last-seen
         * recorded) per
         * {@code WASmaxInPresenceEnums.ENUM_DENY_ERROR_NONE}.
         */
        private final String last;

        /**
         * Constructs a new {@code LastSeenWithOtherValue} projection.
         *
         * @apiNote
         * Called by {@link #of(Node)} after the stanza passes the
         * user-JID and sentinel-set validation.
         *
         * @param from the user JID; never {@code null}
         * @param last the optional sentinel; may be {@code null}
         * @throws NullPointerException if {@code from} is {@code null}
         */
        public LastSeenWithOtherValue(Jid from, String last) {
            this.from = Objects.requireNonNull(from, "from cannot be null");
            this.last = last;
        }

        /**
         * Returns the user JID.
         *
         * @return the user JID; never {@code null}
         */
        public Jid from() {
            return from;
        }

        /**
         * Returns the optional sentinel value.
         *
         * @apiNote
         * Empty when the relay omitted the {@code last} attribute;
         * otherwise one of {@code "deny"}, {@code "error"},
         * {@code "none"}.
         *
         * @return an {@link Optional} carrying the sentinel
         */
        public Optional<String> last() {
            return Optional.ofNullable(last);
        }

        /**
         * Tries to parse a {@link LastSeenWithOtherValue} variant.
         *
         * @apiNote
         * Mirrors
         * {@code WASmaxInPresenceLastSeenWithOtherValueMixin.parseLastSeenWithOtherValueMixin};
         * empty when {@code from} is not a user JID (either
         * {@code s.whatsapp.net} or {@code c.us}), when {@code type}
         * is not {@code "unavailable"}, or when {@code last} carries
         * a value outside the {@code deny/error/none} enum.
         *
         * @param node the inbound presence stanza
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInPresenceLastSeenWithOtherValueMixin",
                exports = "parseLastSeenWithOtherValueMixin",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<LastSeenWithOtherValue> of(Node node) {
            if (!node.hasDescription("presence")) {
                return Optional.empty();
            }
            var from = node.getAttributeAsJid("from").orElse(null);
            if (from == null) {
                return Optional.empty();
            }
            var server = from.server().toString();
            if (!"s.whatsapp.net".equals(server) && !"c.us".equals(server)) {
                return Optional.empty();
            }
            if (!node.hasAttribute("type", "unavailable")) {
                return Optional.empty();
            }
            var last = node.getAttributeAsString("last").orElse(null);
            if (last != null && !"deny".equals(last) && !"error".equals(last) && !"none".equals(last)) {
                return Optional.empty();
            }
            return Optional.of(new LastSeenWithOtherValue(from, last));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (LastSeenWithOtherValue) obj;
            return Objects.equals(this.from, that.from)
                    && Objects.equals(this.last, that.last);
        }

        @Override
        public int hashCode() {
            return Objects.hash(from, last);
        }

        @Override
        public String toString() {
            return "SmaxServerUpdateResponse.LastSeenWithOtherValue[from=" + from
                    + ", last=" + last + ']';
        }
    }

    /**
     * The variant reporting a peer is offline with a free-form
     * last-seen timestamp.
     *
     * @apiNote
     * Surfaces when the peer's privacy settings permit a real
     * last-seen; WA Web's {@code WAWebHandlePresence} runs the
     * value through {@code WATimeUtils.castToUnixTime(Number(last))}
     * before stamping it on the dispatched presence change.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInPresenceUserUnavailableMixin")
    final class UserUnavailable implements SmaxServerUpdateResponse {
        /**
         * The user JID gone offline.
         */
        private final Jid from;

        /**
         * The optional free-form {@code last} attribute, a Unix
         * timestamp as text.
         */
        private final String last;

        /**
         * Constructs a new {@code UserUnavailable} projection.
         *
         * @apiNote
         * Called by {@link #of(Node)} after the stanza passes the
         * user-JID and {@code type="unavailable"} validation.
         *
         * @param from the user JID; never {@code null}
         * @param last the optional {@code last} attribute; may be
         *             {@code null}
         * @throws NullPointerException if {@code from} is {@code null}
         */
        public UserUnavailable(Jid from, String last) {
            this.from = Objects.requireNonNull(from, "from cannot be null");
            this.last = last;
        }

        /**
         * Returns the user JID.
         *
         * @return the user JID; never {@code null}
         */
        public Jid from() {
            return from;
        }

        /**
         * Returns the optional {@code last} value.
         *
         * @apiNote
         * Empty when the relay omitted the attribute; otherwise a
         * Unix-timestamp-as-text the embedder converts to an
         * {@code Instant}.
         *
         * @return an {@link Optional} carrying the value
         */
        public Optional<String> last() {
            return Optional.ofNullable(last);
        }

        /**
         * Tries to parse a {@link UserUnavailable} variant.
         *
         * @apiNote
         * Mirrors
         * {@code WASmaxInPresenceUserUnavailableMixin.parseUserUnavailableMixin};
         * empty when {@code from} is not a user JID or when
         * {@code type} is not {@code "unavailable"}.
         *
         * @param node the inbound presence stanza
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInPresenceUserUnavailableMixin",
                exports = "parseUserUnavailableMixin",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<UserUnavailable> of(Node node) {
            if (!node.hasDescription("presence")) {
                return Optional.empty();
            }
            var from = node.getAttributeAsJid("from").orElse(null);
            if (from == null) {
                return Optional.empty();
            }
            var server = from.server().toString();
            if (!"s.whatsapp.net".equals(server) && !"c.us".equals(server)) {
                return Optional.empty();
            }
            if (!node.hasAttribute("type", "unavailable")) {
                return Optional.empty();
            }
            var last = node.getAttributeAsString("last").orElse(null);
            return Optional.of(new UserUnavailable(from, last));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (UserUnavailable) obj;
            return Objects.equals(this.from, that.from)
                    && Objects.equals(this.last, that.last);
        }

        @Override
        public int hashCode() {
            return Objects.hash(from, last);
        }

        @Override
        public String toString() {
            return "SmaxServerUpdateResponse.UserUnavailable[from=" + from
                    + ", last=" + last + ']';
        }
    }

    /**
     * The variant reporting a peer is online.
     *
     * @apiNote
     * Drives {@code WAWebChangePresenceHandlerAction} with
     * {@code type="available"}; the optional {@code last} attribute,
     * when present, carries the timestamp of the last activity that
     * triggered the presence push.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInPresenceAvailableMixin")
    final class Available implements SmaxServerUpdateResponse {
        /**
         * The peer JID; either a group or a user.
         */
        private final Jid from;

        /**
         * The optional literal {@code "available"} type tag.
         */
        private final String type;

        /**
         * The optional free-form {@code last} attribute.
         */
        private final String last;

        /**
         * Constructs a new {@code Available} projection.
         *
         * @apiNote
         * Called by {@link #of(Node)} once the stanza passes the
         * peer-JID and optional {@code type="available"} validation.
         *
         * @param from the peer JID; never {@code null}
         * @param type the optional literal {@code "available"}; may
         *             be {@code null}
         * @param last the optional {@code last} attribute; may be
         *             {@code null}
         * @throws NullPointerException if {@code from} is {@code null}
         */
        public Available(Jid from, String type, String last) {
            this.from = Objects.requireNonNull(from, "from cannot be null");
            this.type = type;
            this.last = last;
        }

        /**
         * Returns the peer JID.
         *
         * @return the JID; never {@code null}
         */
        public Jid from() {
            return from;
        }

        /**
         * Returns the optional {@code type} attribute.
         *
         * @apiNote
         * Empty when the relay omitted it; otherwise the literal
         * {@code "available"}.
         *
         * @return an {@link Optional} carrying the type
         */
        public Optional<String> type() {
            return Optional.ofNullable(type);
        }

        /**
         * Returns the optional {@code last} value.
         *
         * @apiNote
         * Empty when the relay omitted the attribute; otherwise a
         * Unix-timestamp-as-text.
         *
         * @return an {@link Optional} carrying the value
         */
        public Optional<String> last() {
            return Optional.ofNullable(last);
        }

        /**
         * Tries to parse an {@link Available} variant.
         *
         * @apiNote
         * Mirrors
         * {@code WASmaxInPresenceAvailableMixin.parseAvailableMixin};
         * empty when {@code from} is not a peer JID (group or user)
         * or when {@code type} carries a non-{@code "available"}
         * literal.
         *
         * @param node the inbound presence stanza
         * @return an {@link Optional} carrying the parsed variant
         */
        @WhatsAppWebExport(moduleName = "WASmaxInPresenceAvailableMixin",
                exports = "parseAvailableMixin",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Available> of(Node node) {
            if (!node.hasDescription("presence")) {
                return Optional.empty();
            }
            var from = node.getAttributeAsJid("from").orElse(null);
            if (from == null) {
                return Optional.empty();
            }
            var server = from.server().toString();
            if (!"g.us".equals(server)
                    && !"s.whatsapp.net".equals(server)
                    && !"c.us".equals(server)) {
                return Optional.empty();
            }
            var type = node.getAttributeAsString("type").orElse(null);
            if (type != null && !"available".equals(type)) {
                return Optional.empty();
            }
            var last = node.getAttributeAsString("last").orElse(null);
            return Optional.of(new Available(from, type, last));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (Available) obj;
            return Objects.equals(this.from, that.from)
                    && Objects.equals(this.type, that.type)
                    && Objects.equals(this.last, that.last);
        }

        @Override
        public int hashCode() {
            return Objects.hash(from, type, last);
        }

        @Override
        public String toString() {
            return "SmaxServerUpdateResponse.Available[from=" + from
                    + ", type=" + type
                    + ", last=" + last + ']';
        }
    }
}
