package com.github.auties00.cobalt.node.smax.clientexpiration;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Objects;
import java.util.Optional;

/**
 * The closed family of inbound projections of the server-pushed
 * client-expiration broadcast.
 *
 * @apiNote
 * Surfaces the {@code <ib from="s.whatsapp.net"><client_expiration t?/></ib>}
 * push that WhatsApp sends when an obsolete client build approaches its
 * forced-update deadline. WA Web's {@code WAWebHandleInfoBulletin}
 * surfaces this as the {@code CLIENT_EXPIRATION} info-bulletin variant
 * and renders an in-app upgrade prompt; Cobalt's
 * {@link com.github.auties00.cobalt.stream.control.InfoBulletinStreamHandler}
 * consumes the same parser and exposes the deadline to listeners. The
 * receive-only RPC has a single {@link Inbound} permit.
 */
public sealed interface SmaxClientExpirationResponse extends SmaxOperation.Response
        permits SmaxClientExpirationResponse.Inbound {

    /**
     * Parses the given stanza into the sealed family's single permit.
     *
     * @apiNote
     * Delegates to {@link Inbound#of(Node)}; returns
     * {@link Optional#empty()} when the stanza does not match the
     * documented {@code <ib><client_expiration/></ib>} shape.
     *
     * @param node the inbound stanza; never {@code null}
     * @return an {@link Optional} carrying the parsed projection, or
     *         empty when the stanza shape does not match the schema
     * @throws NullPointerException if {@code node} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxClientExpirationClientExpirationRPC",
            exports = "receiveClientExpirationRPC",
            adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxClientExpirationResponse> of(Node node) {
        Objects.requireNonNull(node, "node cannot be null");
        return Inbound.of(node);
    }

    /**
     * The parsed projection of a single
     * {@code <ib from="s.whatsapp.net"><client_expiration t?/></ib>}
     * push.
     *
     * @apiNote
     * Carries the optional Unix-epoch cutoff after which the local
     * client build will no longer be allowed to connect; consumers
     * compare against the wall clock and route to the upgrade-prompt
     * UI when the cutoff is in the past or imminent.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInClientExpirationClientExpirationRequest")
    final class Inbound implements SmaxClientExpirationResponse {
        /**
         * The {@code from} JID echoed on the {@code <ib>} envelope.
         *
         * @implNote
         * This implementation pins the value to the literal
         * {@code s.whatsapp.net} server JID; the parser rejects any
         * other {@code from} via the
         * {@code attrDomainJid("from", "s.whatsapp.net")} check so
         * the field is reduced to a constant-shaped {@link Jid}.
         */
        private final Jid from;

        /**
         * The optional non-negative Unix-epoch expiration cutoff.
         *
         * @implNote
         * This implementation models the WA Web {@code attrIntRange}
         * predicate by storing the value as a boxed {@link Long} and
         * exposing it through {@link #clientExpirationT()} as
         * {@code Optional<Long>}; the stanza may legitimately omit
         * {@code t}, in which case the field is {@code null}.
         */
        private final Long clientExpirationT;

        /**
         * Constructs a new inbound projection.
         *
         * @apiNote
         * Used by {@link #of(Node)} after the stanza shape has been
         * validated; embedders typically do not instantiate this
         * directly.
         *
         * @param from the {@code from} JID; never {@code null}
         * @param clientExpirationT the optional cutoff timestamp;
         *                          may be {@code null}
         * @throws NullPointerException if {@code from} is {@code null}
         */
        public Inbound(Jid from, Long clientExpirationT) {
            this.from = Objects.requireNonNull(from, "from cannot be null");
            this.clientExpirationT = clientExpirationT;
        }

        /**
         * Returns the {@code from} JID echoed on the {@code <ib>}
         * envelope.
         *
         * @apiNote
         * Always equal to {@link Jid#userServer()} after a successful
         * parse; exposed as a field rather than a constant so embedders
         * can route the broadcast through generic
         * {@link com.github.auties00.cobalt.client.WhatsAppClientListener}
         * paths that treat any sender JID uniformly.
         *
         * @return the JID; never {@code null}
         */
        public Jid from() {
            return from;
        }

        /**
         * Returns the optional expiration cutoff.
         *
         * @apiNote
         * Carries the value of the {@code <client_expiration t="..."/>}
         * attribute in seconds since the Unix epoch; embedders compare
         * against the wall clock to decide whether to surface the
         * upgrade-prompt UI. WA Web treats an absent {@code t} as "no
         * deadline yet known" rather than "no expiration", so Cobalt
         * preserves the {@link Optional#empty()} signal verbatim.
         *
         * @return an {@link Optional} carrying the cutoff in epoch
         *         seconds, or empty when the relay omitted it
         */
        public Optional<Long> clientExpirationT() {
            return Optional.ofNullable(clientExpirationT);
        }

        /**
         * Parses an {@link Inbound} projection from the given stanza.
         *
         * @apiNote
         * Returns {@link Optional#empty()} when any of the WA Web
         * parser's preconditions fail: the envelope must be an
         * {@code <ib>}, must carry {@code from="s.whatsapp.net"}, must
         * have exactly one {@code <client_expiration/>} child, and
         * when {@code t} is present it must be a non-negative integer.
         *
         * @implNote
         * This implementation mirrors WA Web's
         * {@code parseClientExpirationRequest}: the
         * {@code <client_expiration>} child is located via
         * {@link Node#getChild(String)} rather than the
         * {@code flattenedChildWithTag} helper, which is acceptable
         * here because Cobalt's node tree is already flat at this
         * depth.
         *
         * @param node the inbound stanza; never {@code null}
         * @return an {@link Optional} carrying the parsed projection,
         *         or empty when the stanza shape does not match the
         *         schema
         * @throws NullPointerException if {@code node} is {@code null}
         */
        @WhatsAppWebExport(moduleName = "WASmaxInClientExpirationClientExpirationRequest",
                exports = "parseClientExpirationRequest",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Inbound> of(Node node) {
            Objects.requireNonNull(node, "node cannot be null");
            if (!node.hasDescription("ib")) {
                return Optional.empty();
            }
            var from = node.getAttributeAsJid("from").orElse(null);
            if (from == null || !Jid.userServer().equals(from)) {
                return Optional.empty();
            }
            var expirationNode = node.getChild("client_expiration").orElse(null);
            if (expirationNode == null) {
                return Optional.empty();
            }
            var clientExpirationT = expirationNode.getAttributeAsLong("t");
            if (clientExpirationT.isPresent() && clientExpirationT.getAsLong() < 0) {
                return Optional.empty();
            }
            var clientExpirationValue = clientExpirationT.isPresent()
                    ? Long.valueOf(clientExpirationT.getAsLong())
                    : null;
            return Optional.of(new Inbound(from, clientExpirationValue));
        }

        /**
         * Returns whether the given object is an {@link Inbound} with
         * equal {@code from} and cutoff timestamp.
         *
         * @param obj the candidate; may be {@code null}
         * @return {@code true} when both fields match
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (Inbound) obj;
            return Objects.equals(this.from, that.from)
                    && Objects.equals(this.clientExpirationT, that.clientExpirationT);
        }

        /**
         * Returns a hash code derived from {@code from} and the cutoff
         * timestamp.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return Objects.hash(from, clientExpirationT);
        }

        /**
         * Returns a debug-friendly textual representation of this
         * projection.
         *
         * @return the textual representation
         */
        @Override
        public String toString() {
            return "SmaxClientExpirationResponse.Inbound[from=" + from
                    + ", clientExpirationT=" + clientExpirationT + ']';
        }
    }
}
