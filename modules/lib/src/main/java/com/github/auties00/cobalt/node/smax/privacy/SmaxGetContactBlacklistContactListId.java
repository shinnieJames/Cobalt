package com.github.auties00.cobalt.node.smax.privacy;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxIqErrorResponseMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The sealed family discriminating each {@code <user/>} child in a LID-addressed disallowed-list reply.
 *
 * @apiNote
 * Consumed by {@code WAWebQueryPrivacyDisallowedListLidJob.queryPrivacyDisallowedListLid}: the
 * {@link Username} arm seeds {@code WAWebSetUsernameJob.setUsernamesJob} for username-only contacts; the
 * {@link PnJid} arm primes the LID-to-PN mapping store via
 * {@code WAWebDBCreateLidPnMappings.createLidPnMappingsInBatches}; the {@link Empty} arm is logged as a
 * benign LID entry the relay knows nothing else about.
 *
 * @implNote
 * This implementation collapses the WA Web wire discriminator (the {@code Username} / {@code PnJid} /
 * {@code EmptyContactListIdentifier} tagged-union) into a sealed interface whose variant type is the
 * discriminator. Pattern matching replaces WA Web's {@code contactListIds.name === "..."} branches.
 */
public sealed interface SmaxGetContactBlacklistContactListId
        permits SmaxGetContactBlacklistContactListId.Username, SmaxGetContactBlacklistContactListId.PnJid, SmaxGetContactBlacklistContactListId.Empty {

    /**
     * The {@code Username} arm of the discriminator carrying a WhatsApp username string.
     *
     * @apiNote
     * Surfaced for LID entries the relay tracks by username (the username-displayed feature). Routed to
     * {@code WAWebSetUsernameJob.setUsernamesJob} so the local username cache is primed before the user opens
     * the privacy settings.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInPrivacyUsernameMixin")
    final class Username implements SmaxGetContactBlacklistContactListId {
        /**
         * The WhatsApp username string echoed by the relay.
         */
        private final String username;

        /**
         * Constructs a {@code Username} arm.
         *
         * @apiNote
         * Built by {@link SmaxGetContactBlacklistResponse} during the {@code <user/>} disjunction dispatch when
         * the {@code username} attribute is present.
         *
         * @param username the username; never {@code null}
         * @throws NullPointerException if {@code username} is {@code null}
         */
        public Username(String username) {
            this.username = Objects.requireNonNull(username, "username cannot be null");
        }

        /**
         * Returns the WhatsApp username.
         *
         * @apiNote
         * Use to drive the username-resolution path; the value is the literal echo from the relay and is not
         * further validated by the parser.
         *
         * @return the username; never {@code null}
         */
        public String username() {
            return username;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (Username) obj;
            return Objects.equals(this.username, that.username);
        }

        @Override
        public int hashCode() {
            return Objects.hash(username);
        }

        @Override
        public String toString() {
            return "SmaxGetContactBlacklistContactListId.Username[username=" + username + ']';
        }
    }

    /**
     * The {@code PnJid} arm of the discriminator carrying the legacy phone-number JID paired with the LID.
     *
     * @apiNote
     * Surfaced when the relay still remembers the original phone-number JID for the LID-addressed entry; routed
     * to {@code WAWebDBCreateLidPnMappings.createLidPnMappingsInBatches} so the LID-to-PN cache is primed for
     * later contact-resolution lookups.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInPrivacyPnJidMixin")
    final class PnJid implements SmaxGetContactBlacklistContactListId {
        /**
         * The legacy phone-number JID echoed alongside the LID entry.
         */
        private final Jid pnJid;

        /**
         * Constructs a {@code PnJid} arm.
         *
         * @apiNote
         * Built by {@link SmaxGetContactBlacklistResponse} when the {@code <user/>} child carries the
         * {@code pn_jid} attribute and the {@code username} attribute is absent.
         *
         * @param pnJid the echoed PN JID; never {@code null}
         * @throws NullPointerException if {@code pnJid} is {@code null}
         */
        public PnJid(Jid pnJid) {
            this.pnJid = Objects.requireNonNull(pnJid, "pnJid cannot be null");
        }

        /**
         * Returns the legacy phone-number JID.
         *
         * @return the PN JID; never {@code null}
         */
        public Jid pnJid() {
            return pnJid;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (PnJid) obj;
            return Objects.equals(this.pnJid, that.pnJid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pnJid);
        }

        @Override
        public String toString() {
            return "SmaxGetContactBlacklistContactListId.PnJid[pnJid=" + pnJid + ']';
        }
    }

    /**
     * The {@code Empty} arm of the discriminator for entries the relay has no PN echo for.
     *
     * @apiNote
     * Surfaced for LID-addressed entries the relay knows by LID only; logged as a benign signal by
     * {@code WAWebQueryPrivacyDisallowedListLidJob.queryPrivacyDisallowedListLid} and otherwise ignored.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInPrivacyEmptyContactListIdentifierMixin")
    final class Empty implements SmaxGetContactBlacklistContactListId {
        /**
         * Constructs an {@code Empty} arm.
         *
         * @apiNote
         * Built by {@link SmaxGetContactBlacklistResponse} when neither {@code username} nor {@code pn_jid} is
         * present on the {@code <user/>} child; carries no state of its own.
         */
        public Empty() {
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            return obj != null && obj.getClass() == this.getClass();
        }

        @Override
        public int hashCode() {
            return Empty.class.hashCode();
        }

        @Override
        public String toString() {
            return "SmaxGetContactBlacklistContactListId.Empty[]";
        }
    }
}
