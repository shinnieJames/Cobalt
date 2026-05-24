package com.github.auties00.cobalt.node.smax.passivemode;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Objects;
import java.util.Optional;

/**
 * Sealed family of inbound reply variants for
 * {@link SmaxPassiveModeActiveIQRequest}.
 *
 * @apiNote
 * Only {@link Success} is documented by WA Web's
 * {@code WASmaxPassiveModeActiveIQRPC} dispatcher; the dispatcher throws
 * a {@code SmaxParsingFailure} for every other shape. Embedders that
 * pattern-match on this disjunction only need to handle the success
 * variant.
 */
public sealed interface SmaxPassiveModeActiveIQResponse extends SmaxOperation.Response
        permits SmaxPassiveModeActiveIQResponse.Success {

    /**
     * Tries the {@link Success} variant and returns it when it parses
     * cleanly.
     *
     * @apiNote
     * The single entry point used by Cobalt's SMAX dispatcher to lift an
     * inbound stanza into the sealed disjunction. Returning
     * {@link Optional#empty()} signals that the relay did not produce a
     * documented active-mode acknowledgement; callers should surface that
     * to their error handler.
     *
     * @implNote
     * This implementation delegates straight to
     * {@link Success#of(Node, Node)} because the RPC has only one valid
     * reply shape.
     *
     * @param node    the inbound IQ stanza received from the relay
     * @param request the original outbound stanza, used to validate
     *                echoed identifiers
     * @return an {@link Optional} carrying the parsed variant, or
     *         {@link Optional#empty()} on no-match
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxPassiveModeActiveIQRPC",
            exports = "sendActiveIQRPC", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxPassiveModeActiveIQResponse> of(Node node, Node request) {
        Objects.requireNonNull(node, "node cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        return Success.of(node, request);
    }

    /**
     * The {@code Success} reply variant projecting the echoed
     * {@code <iq type="result" from="s.whatsapp.net">} envelope.
     *
     * @apiNote
     * Signals that the relay flipped the connection out of passive mode
     * and will now stream live deliveries to this socket.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInPassiveModeActiveIQResponseSuccess")
    final class Success implements SmaxPassiveModeActiveIQResponse {
        /**
         * The {@code from} JID echoed by the relay.
         *
         * @apiNote
         * Always {@link JidServer#user()} ({@code s.whatsapp.net}); the
         * WA Web parser asserts the literal via the
         * {@code literalJid(attrDomainJid, "s.whatsapp.net")} check.
         */
        private final Jid from;

        /**
         * Constructs a successful reply.
         *
         * @apiNote
         * Used by {@link #of(Node, Node)} after the envelope passes
         * validation; embedders typically obtain instances through the
         * static factory rather than construct directly.
         *
         * @param from the echoed {@code from} JID
         * @throws NullPointerException if {@code from} is {@code null}
         */
        public Success(Jid from) {
            this.from = Objects.requireNonNull(from, "from cannot be null");
        }

        /**
         * Returns the {@code from} JID echoed by the relay.
         *
         * @apiNote
         * Useful for embedders that log or audit which server endpoint
         * acknowledged the active-mode flip; for normal control flow the
         * mere presence of a {@link Success} instance is enough.
         *
         * @return the {@link Jid}
         */
        public Jid from() {
            return from;
        }

        /**
         * Tries to parse a {@link Success} variant from the given inbound
         * stanza.
         *
         * @apiNote
         * Returns {@link Optional#empty()} when the stanza is not a
         * well-formed {@code <iq type="result">} echoing the request id
         * and carrying both a non-empty {@code from=s.whatsapp.net} and a
         * nested {@code <active/>} marker. Callers should treat an empty
         * result as a protocol-level rejection.
         *
         * @implNote
         * This implementation walks the WA Web fixture in order: tag
         * check, type check, id echo check, {@code <active/>} presence
         * check, {@code from} JID echo check. Any failure short-circuits
         * to {@link Optional#empty()}.
         *
         * @param node    the inbound IQ stanza
         * @param request the original outbound request
         * @return an {@link Optional} carrying the parsed variant, or
         *         empty on no-match
         * @throws NullPointerException if either argument is {@code null}
         */
        @WhatsAppWebExport(moduleName = "WASmaxInPassiveModeActiveIQResponseSuccess",
                exports = "parseActiveIQResponseSuccess",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Success> of(Node node, Node request) {
            Objects.requireNonNull(node, "node cannot be null");
            Objects.requireNonNull(request, "request cannot be null");
            if (!node.hasDescription("iq")) {
                return Optional.empty();
            }
            if (!node.hasAttribute("type", "result")) {
                return Optional.empty();
            }
            var requestId = request.getAttributeAsString("id").orElse(null);
            if (requestId == null || !node.hasAttribute("id", requestId)) {
                return Optional.empty();
            }
            if (node.getChild("active").isEmpty()) {
                return Optional.empty();
            }
            var from = node.getAttributeAsJid("from").orElse(null);
            if (from == null || !from.hasServer(JidServer.user())) {
                return Optional.empty();
            }
            return Optional.of(new Success(from));
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation compares only the {@link #from} field
         * because it is the sole carried attribute.
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (Success) obj;
            return Objects.equals(this.from, that.from);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation hashes only the {@link #from} field to
         * stay consistent with {@link #equals(Object)}.
         */
        @Override
        public int hashCode() {
            return Objects.hash(from);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation mirrors the record-like rendering used
         * across the {@code Smax*} response family.
         */
        @Override
        public String toString() {
            return "SmaxPassiveModeActiveIQResponse.Success[from=" + from + ']';
        }
    }
}
