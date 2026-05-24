package com.github.auties00.cobalt.node.smax.chatstate;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Objects;
import java.util.Optional;

/**
 * Sealed disjunction over the two state-source mixins identifying the
 * origin of an inbound {@code <chatstate>} event: a 1:1 peer
 * ({@link FromUser}) or a group participant ({@link FromGroup}).
 *
 * @apiNote
 * The Cobalt analogue of {@code WACreateHandleChatState.createHandleChatState}
 * branches on {@link #of(Node)}: a {@link FromUser} result routes the
 * indicator to the {@code individualMessage.handleIndividualChatState}
 * handler, while a {@link FromGroup} result routes it to
 * {@code groupMessage.handleGroupChatState} with the participant attached.
 */
@WhatsAppWebModule(moduleName = "WASmaxInChatstateStateSource")
public sealed interface SmaxServerNotificationStateSource permits SmaxServerNotificationStateSource.FromUser, SmaxServerNotificationStateSource.FromGroup {

    /**
     * Parses an inbound stanza into the first matching variant.
     *
     * @implNote
     * This implementation mirrors {@code parseStateSource}: it tries
     * {@link FromUser} first and falls back to {@link FromGroup}, returning
     * the first successful parse.
     *
     * @param node the inbound {@code <chatstate/>} stanza
     * @return an {@link Optional} carrying the parsed variant, or empty on no-match
     */
    @WhatsAppWebExport(moduleName = "WASmaxInChatstateStateSource",
            exports = "parseStateSource", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxServerNotificationStateSource> of(Node node) {
        Objects.requireNonNull(node, "node cannot be null");
        var fromUser = FromUser.of(node);
        if (fromUser.isPresent()) {
            return fromUser;
        }
        return FromGroup.of(node);
    }

    /**
     * Reports whether the JID's server is one of the user-JID server
     * domains admitted by {@code WAJids.validateUserJid}.
     *
     * @apiNote
     * Centralises the {@code USERJID_USERJID_USERJID} and
     * {@code USERJID_USERJID_USERJID_USERJID} enum admit set from
     * {@code WASmaxInChatstateEnums}: each validator slot resolves to
     * {@code WAJids.validateUserJid}, whose regular expressions accept the
     * {@code @s.whatsapp.net}, {@code @interop}, {@code @msgr}, {@code @lid},
     * and {@code @bot} server domains.
     *
     * @param jid the JID to test; never {@code null}
     * @return {@code true} when {@code jid.server()} is one of the
     *         user-JID server domains, {@code false} otherwise
     */
    @WhatsAppWebExport(moduleName = "WAJids",
            exports = "validateUserJid", adaptation = WhatsAppAdaptation.ADAPTED)
    private static boolean isUserJidServer(Jid jid) {
        var server = jid.server().toString();
        return "s.whatsapp.net".equals(server)
                || "interop".equals(server)
                || "msgr".equals(server)
                || "lid".equals(server)
                || "bot".equals(server);
    }

    /**
     * The variant raised by a 1:1 peer.
     *
     * @apiNote
     * Identifies the chat-state event as originating from a direct
     * conversation; the {@link #from()} JID is the peer's user JID.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInChatstateFromUserMixin")
    final class FromUser implements SmaxServerNotificationStateSource {
        /**
         * The user JID raising the event.
         */
        private final Jid from;

        /**
         * Constructs a {@link FromUser} variant.
         *
         * @param from the user JID; never {@code null}
         * @throws NullPointerException if {@code from} is {@code null}
         */
        public FromUser(Jid from) {
            this.from = Objects.requireNonNull(from, "from cannot be null");
        }

        /**
         * Returns the user JID raising the event.
         *
         * @return the JID; never {@code null}
         */
        public Jid from() {
            return from;
        }

        /**
         * Parses an inbound stanza into a {@link FromUser} variant.
         *
         * @implNote
         * This implementation mirrors {@code parseFromUserMixin}: it
         * asserts the {@code <chatstate>} tag and requires the {@code from}
         * attribute to resolve to a user-JID server domain admitted by
         * {@link #isUserJidServer(Jid)}.
         *
         * @param node the inbound chatstate stanza
         * @return an {@link Optional} carrying the parsed variant, or empty on schema mismatch
         */
        @WhatsAppWebExport(moduleName = "WASmaxInChatstateFromUserMixin",
                exports = "parseFromUserMixin", adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<FromUser> of(Node node) {
            if (!node.hasDescription("chatstate")) {
                return Optional.empty();
            }
            var from = node.getAttributeAsJid("from").orElse(null);
            if (from == null) {
                return Optional.empty();
            }
            if (!isUserJidServer(from)) {
                return Optional.empty();
            }
            return Optional.of(new FromUser(from));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (FromUser) obj;
            return Objects.equals(this.from, that.from);
        }

        @Override
        public int hashCode() {
            return Objects.hash(from);
        }

        @Override
        public String toString() {
            return "SmaxServerNotificationStateSource.FromUser[from=" + from + ']';
        }
    }

    /**
     * The variant raised by a participant inside a group chat.
     *
     * @apiNote
     * Identifies the chat-state event as originating from a group
     * conversation; the {@link #from()} JID is the group JID and
     * {@link #participant()} identifies which group member raised the
     * event.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInChatstateFromGroupMixin")
    final class FromGroup implements SmaxServerNotificationStateSource {
        /**
         * The group JID hosting the chat.
         */
        private final Jid from;

        /**
         * The participant raising the event.
         */
        private final Jid participant;

        /**
         * The optional phone-number-form participant JID.
         *
         * @apiNote
         * Supplied when the relay can attach the PN-form identity for a
         * participant whose primary identifier is a LID; absent for
         * participants without an associated PN.
         */
        private final Jid participantPn;

        /**
         * Constructs a {@link FromGroup} variant.
         *
         * @param from          the group JID; never {@code null}
         * @param participant   the participant JID; never {@code null}
         * @param participantPn the optional pn-form participant JID; may be {@code null}
         * @throws NullPointerException if {@code from} or {@code participant} is {@code null}
         */
        public FromGroup(Jid from, Jid participant, Jid participantPn) {
            this.from = Objects.requireNonNull(from, "from cannot be null");
            this.participant = Objects.requireNonNull(participant, "participant cannot be null");
            this.participantPn = participantPn;
        }

        /**
         * Returns the group JID hosting the chat.
         *
         * @return the JID; never {@code null}
         */
        public Jid from() {
            return from;
        }

        /**
         * Returns the participant JID raising the event.
         *
         * @return the JID; never {@code null}
         */
        public Jid participant() {
            return participant;
        }

        /**
         * Returns the optional pn-form participant JID.
         *
         * @return an {@link Optional} carrying the JID, or empty when omitted
         */
        public Optional<Jid> participantPn() {
            return Optional.ofNullable(participantPn);
        }

        /**
         * Parses an inbound stanza into a {@link FromGroup} variant.
         *
         * @implNote
         * This implementation mirrors {@code parseFromGroupMixin}: it
         * asserts the {@code <chatstate>} tag, requires the {@code from}
         * attribute to resolve to a {@code @g.us} group JID, the
         * {@code participant} attribute to resolve to a user-JID server
         * domain admitted by {@link #isUserJidServer(Jid)}, and applies the
         * same admit-set to the optional {@code participant_pn} attribute
         * when present. The WA Web {@code optional()} contract treats an
         * absent attribute as success-with-null but rejects a present
         * attribute that fails the inner validator.
         *
         * @param node the inbound chatstate stanza
         * @return an {@link Optional} carrying the parsed variant, or empty on schema mismatch
         */
        @WhatsAppWebExport(moduleName = "WASmaxInChatstateFromGroupMixin",
                exports = "parseFromGroupMixin", adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<FromGroup> of(Node node) {
            if (!node.hasDescription("chatstate")) {
                return Optional.empty();
            }
            var from = node.getAttributeAsJid("from").orElse(null);
            if (from == null || !"g.us".equals(from.server().toString())) {
                return Optional.empty();
            }
            var participant = node.getAttributeAsJid("participant").orElse(null);
            if (participant == null) {
                return Optional.empty();
            }
            if (!isUserJidServer(participant)) {
                return Optional.empty();
            }
            Jid participantPn = null;
            if (node.getAttribute("participant_pn").isPresent()) {
                participantPn = node.getAttributeAsJid("participant_pn").orElse(null);
                if (participantPn == null || !isUserJidServer(participantPn)) {
                    return Optional.empty();
                }
            }
            return Optional.of(new FromGroup(from, participant, participantPn));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (FromGroup) obj;
            return Objects.equals(this.from, that.from)
                    && Objects.equals(this.participant, that.participant)
                    && Objects.equals(this.participantPn, that.participantPn);
        }

        @Override
        public int hashCode() {
            return Objects.hash(from, participant, participantPn);
        }

        @Override
        public String toString() {
            return "SmaxServerNotificationStateSource.FromGroup[from=" + from
                    + ", participant=" + participant
                    + ", participantPn=" + participantPn + ']';
        }
    }
}
