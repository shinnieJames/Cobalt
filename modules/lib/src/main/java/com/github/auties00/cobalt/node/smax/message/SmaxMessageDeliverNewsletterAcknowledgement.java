package com.github.auties00.cobalt.node.smax.message;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code <ack class="message">} stanza the client emits
 * to confirm or NACK an inbound newsletter delivery; either the
 * positive {@link SuccessAck} or the negative {@link ErrorAck}.
 *
 * @apiNote
 * Used by callers replying to a delivered
 * {@link SmaxMessageDeliverNewsletterResponse} stanza. {@link SuccessAck}
 * confirms the message was decoded and persisted; {@link ErrorAck}
 * stamps the fixed {@code error="406"} marker to signal a decryption,
 * shape, or schema failure that the relay should treat as
 * not-acknowledged.
 */
public sealed interface SmaxMessageDeliverNewsletterAcknowledgement extends SmaxOperation.Request
        permits SmaxMessageDeliverNewsletterAcknowledgement.SuccessAck, SmaxMessageDeliverNewsletterAcknowledgement.ErrorAck {

    /**
     * The positive ack variant the client emits after successfully
     * consuming a newsletter delivery.
     *
     * @apiNote
     * Pick this variant when the inbound message decoded and
     * persisted cleanly; the relay treats it as a successful delivery
     * receipt.
     */
    @WhatsAppWebModule(moduleName = "WASmaxOutMessageDeliverNewsletterResponseSuccess")
    @WhatsAppWebModule(moduleName = "WASmaxOutMessageDeliverCommonAckMixin")
    final class SuccessAck implements SmaxMessageDeliverNewsletterAcknowledgement {
        /**
         * The {@code id} of the inbound message being acknowledged.
         */
        private final String stanzaId;

        /**
         * The {@code from} of the inbound message; becomes the
         * ack's {@code to}.
         */
        private final Jid notificationFrom;

        /**
         * The {@code type} of the inbound message; echoed back as the
         * ack's {@code type}.
         */
        private final String stanzaType;

        /**
         * Constructs a positive ack carrying the inbound stanza's
         * correlation triplet.
         *
         * @apiNote
         * Use this when assembling a {@link SuccessAck} to confirm a
         * newsletter delivery the client decoded successfully.
         *
         * @param stanzaId         the inbound message id; never
         *                         {@code null}
         * @param notificationFrom the inbound sender JID; never
         *                         {@code null}
         * @param stanzaType       the inbound message type; never
         *                         {@code null}
         * @throws NullPointerException if any argument is {@code null}
         */
        public SuccessAck(String stanzaId, Jid notificationFrom, String stanzaType) {
            this.stanzaId = Objects.requireNonNull(stanzaId, "stanzaId cannot be null");
            this.notificationFrom = Objects.requireNonNull(notificationFrom, "notificationFrom cannot be null");
            this.stanzaType = Objects.requireNonNull(stanzaType, "stanzaType cannot be null");
        }

        /**
         * Returns the inbound message id this ack confirms.
         *
         * @return the id; never {@code null}
         */
        public String stanzaId() {
            return stanzaId;
        }

        /**
         * Returns the inbound sender JID this ack is routed back to.
         *
         * @return the JID; never {@code null}
         */
        public Jid notificationFrom() {
            return notificationFrom;
        }

        /**
         * Returns the inbound message type this ack echoes.
         *
         * @return the type; never {@code null}
         */
        public String stanzaType() {
            return stanzaType;
        }

        /**
         * Builds the outbound {@code <ack class="message">} stanza
         * ready for dispatch.
         *
         * @apiNote
         * The stanza has shape
         * {@snippet lang=xml :
         * <ack to="<notificationFrom>" class="message" id="<stanzaId>" type="<stanzaType>"/>
         * }
         *
         * @return a {@link NodeBuilder} carrying the ack stanza
         */
        @WhatsAppWebExport(moduleName = "WASmaxOutMessageDeliverNewsletterResponseSuccess",
                exports = "makeNewsletterResponseSuccess",
                adaptation = WhatsAppAdaptation.DIRECT)
        @Override
        public NodeBuilder toNode() {
            return new NodeBuilder()
                    .description("ack")
                    .attribute("to", notificationFrom)
                    .attribute("class", "message")
                    .attribute("id", stanzaId)
                    .attribute("type", stanzaType);
        }

        /**
         * Compares this ack to another for value equality.
         *
         * @param obj the object to compare against
         * @return {@code true} when {@code obj} is a {@link SuccessAck}
         *         with identical fields
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (SuccessAck) obj;
            return Objects.equals(this.stanzaId, that.stanzaId)
                    && Objects.equals(this.notificationFrom, that.notificationFrom)
                    && Objects.equals(this.stanzaType, that.stanzaType);
        }

        /**
         * Returns a hash code consistent with {@link #equals(Object)}.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(stanzaId, notificationFrom, stanzaType);
        }

        /**
         * Returns a debug-friendly representation of this ack.
         *
         * @apiNote
         * Intended for logging; the format is not part of the public
         * contract.
         *
         * @return the string form
         */
        @Override
        public String toString() {
            return "SmaxMessageDeliverNewsletterAcknowledgement.SuccessAck[stanzaId=" + stanzaId
                    + ", notificationFrom=" + notificationFrom
                    + ", stanzaType=" + stanzaType + ']';
        }
    }

    /**
     * The negative ack variant the client emits when it could not
     * consume a newsletter delivery.
     *
     * @apiNote
     * Pick this variant when the inbound message failed to decrypt,
     * was malformed, or did not match the expected schema; the relay
     * treats the fixed {@code error="406"} marker as a definitive
     * not-acknowledged signal.
     */
    @WhatsAppWebModule(moduleName = "WASmaxOutMessageDeliverNewsletterResponseError")
    @WhatsAppWebModule(moduleName = "WASmaxOutMessageDeliverCommonAckMixin")
    final class ErrorAck implements SmaxMessageDeliverNewsletterAcknowledgement {
        /**
         * The {@code id} of the inbound message being NACK'd.
         */
        private final String stanzaId;

        /**
         * The {@code from} of the inbound message; becomes the
         * ack's {@code to}.
         */
        private final Jid notificationFrom;

        /**
         * The {@code type} of the inbound message; echoed back as the
         * ack's {@code type}.
         */
        private final String stanzaType;

        /**
         * Constructs a negative ack carrying the inbound stanza's
         * correlation triplet.
         *
         * @apiNote
         * Use this when assembling an {@link ErrorAck} to reject a
         * newsletter delivery the client could not consume.
         *
         * @param stanzaId         the inbound message id; never
         *                         {@code null}
         * @param notificationFrom the inbound sender JID; never
         *                         {@code null}
         * @param stanzaType       the inbound message type; never
         *                         {@code null}
         * @throws NullPointerException if any argument is {@code null}
         */
        public ErrorAck(String stanzaId, Jid notificationFrom, String stanzaType) {
            this.stanzaId = Objects.requireNonNull(stanzaId, "stanzaId cannot be null");
            this.notificationFrom = Objects.requireNonNull(notificationFrom, "notificationFrom cannot be null");
            this.stanzaType = Objects.requireNonNull(stanzaType, "stanzaType cannot be null");
        }

        /**
         * Returns the inbound message id this ack rejects.
         *
         * @return the id; never {@code null}
         */
        public String stanzaId() {
            return stanzaId;
        }

        /**
         * Returns the inbound sender JID this ack is routed back to.
         *
         * @return the JID; never {@code null}
         */
        public Jid notificationFrom() {
            return notificationFrom;
        }

        /**
         * Returns the inbound message type this ack echoes.
         *
         * @return the type; never {@code null}
         */
        public String stanzaType() {
            return stanzaType;
        }

        /**
         * Builds the outbound {@code <ack error="406">} stanza ready
         * for dispatch.
         *
         * @apiNote
         * The stanza has shape
         * {@snippet lang=xml :
         * <ack error="406" to="<notificationFrom>" class="message" id="<stanzaId>" type="<stanzaType>"/>
         * }
         *
         * @return a {@link NodeBuilder} carrying the negative-ack
         *         stanza
         */
        @WhatsAppWebExport(moduleName = "WASmaxOutMessageDeliverNewsletterResponseError",
                exports = "makeNewsletterResponseError",
                adaptation = WhatsAppAdaptation.DIRECT)
        @Override
        public NodeBuilder toNode() {
            return new NodeBuilder()
                    .description("ack")
                    .attribute("error", "406")
                    .attribute("to", notificationFrom)
                    .attribute("class", "message")
                    .attribute("id", stanzaId)
                    .attribute("type", stanzaType);
        }

        /**
         * Compares this ack to another for value equality.
         *
         * @param obj the object to compare against
         * @return {@code true} when {@code obj} is an {@link ErrorAck}
         *         with identical fields
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (ErrorAck) obj;
            return Objects.equals(this.stanzaId, that.stanzaId)
                    && Objects.equals(this.notificationFrom, that.notificationFrom)
                    && Objects.equals(this.stanzaType, that.stanzaType);
        }

        /**
         * Returns a hash code consistent with {@link #equals(Object)}.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(stanzaId, notificationFrom, stanzaType);
        }

        /**
         * Returns a debug-friendly representation of this ack.
         *
         * @apiNote
         * Intended for logging; the format is not part of the public
         * contract.
         *
         * @return the string form
         */
        @Override
        public String toString() {
            return "SmaxMessageDeliverNewsletterAcknowledgement.ErrorAck[stanzaId=" + stanzaId
                    + ", notificationFrom=" + notificationFrom
                    + ", stanzaType=" + stanzaType + ']';
        }
    }
}
