package com.github.auties00.cobalt.node.smax.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code GetAccountNonce} IQ request stanza for the
 * SMB business-linking nonce bridge.
 *
 * @apiNote
 * Used by Cobalt clients that mirror WA Web's
 * {@code WAWebQueryLinkedAccountNonceJob.queryNonce} flow, which
 * runs from {@code WAWebBusinessAdCreationUtils} as the
 * Click-to-WhatsApp ads page-binding handshake; the relay returns a
 * one-shot account-binding nonce that the CTWA ad-creation surface
 * forwards to Meta's ads backend to prove ownership of the linked
 * Facebook page.
 *
 * @implNote
 * This implementation mirrors WA Web's
 * {@code makeGetAccountNonceRequest} by stamping the static
 * {@code xmlns="fb:thrift_iq"} envelope; the {@code id} attribute is
 * not stamped here because Cobalt's send-path delegates id
 * generation to {@link com.github.auties00.cobalt.WhatsAppClient}'s
 * {@code sendNode} dispatcher, matching the WA Web
 * {@code generateId()} insertion point.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutBizLinkingGetAccountNonceRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutBizLinkingHackBaseIQGetRequestMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutBizLinkingBaseIQGetRequestMixin")
public final class SmaxGetAccountNonceRequest implements SmaxOperation.Request {
    /**
     * The optional {@code scope} attribute of the
     * {@code <identifier>} child; {@code null} omits the child
     * entirely.
     */
    private final String identifierScope;

    /**
     * The optional {@code from} attribute echoed onto the outbound
     * IQ via the {@code HackBaseIQGetRequestMixin}; the active user
     * {@link Jid} is the only legal value and {@code null} omits the
     * attribute.
     */
    private final Jid fromUserJid;

    /**
     * Constructs a request without an {@code <identifier>} child and
     * with no {@code from} echo.
     *
     * @apiNote
     * The default shape invoked by
     * {@code WAWebQueryLinkedAccountNonceJob.queryNonce}: a bare
     * IQ-get with no payload, suitable for the standard CTWA
     * ad-creation nonce fetch.
     */
    public SmaxGetAccountNonceRequest() {
        this(null, null);
    }

    /**
     * Constructs a request optionally carrying an
     * {@code <identifier scope="..."/>} child and no {@code from}
     * echo.
     *
     * @apiNote
     * Use to scope the nonce request to a specific identifier
     * domain; the scope literal is passed through verbatim to the
     * relay.
     *
     * @param identifierScope the {@code scope} attribute; may be
     *                        {@code null} to omit the child
     */
    public SmaxGetAccountNonceRequest(String identifierScope) {
        this(identifierScope, null);
    }

    /**
     * Constructs a request with the optional scope and an optional
     * {@code from} echo.
     *
     * @apiNote
     * Use the {@code fromUserJid} overload when a companion linked
     * device proxies the request on behalf of the active user; the
     * relay validates that the echoed JID matches the authenticated
     * session.
     *
     * @param identifierScope the {@code scope} attribute; may be
     *                        {@code null} to omit the child
     * @param fromUserJid     the optional user {@link Jid} to echo
     *                        onto the {@code from} attribute; may
     *                        be {@code null}
     */
    public SmaxGetAccountNonceRequest(String identifierScope, Jid fromUserJid) {
        this.identifierScope = identifierScope;
        this.fromUserJid = fromUserJid;
    }

    /**
     * Returns the optional identifier scope.
     *
     * @apiNote
     * The returned {@link Optional} is empty when the request was
     * constructed without an {@code <identifier>} child, matching
     * the default {@code queryNonce} path.
     *
     * @return an {@link Optional} carrying the scope, or empty when
     *         the child is omitted
     */
    public Optional<String> identifierScope() {
        return Optional.ofNullable(identifierScope);
    }

    /**
     * Returns the optional {@code from} echo.
     *
     * @apiNote
     * Empty unless a companion-device proxy supplied the active
     * user's {@link Jid}.
     *
     * @return an {@link Optional} carrying the user {@link Jid}, or
     *         empty when no echo was supplied
     */
    public Optional<Jid> fromUserJid() {
        return Optional.ofNullable(fromUserJid);
    }

    /**
     * Builds the outbound IQ stanza ready for dispatch.
     *
     * @implNote
     * This implementation composes three WA Web mixins in a single
     * pass: {@code makeGetAccountNonceRequest} stamps the
     * {@code xmlns="fb:thrift_iq"} envelope,
     * {@code mergeHackBaseIQGetRequestMixin} stamps {@code to} and
     * the optional {@code from}, and
     * {@code mergeBaseIQGetRequestMixin} stamps {@code type="get"};
     * the {@code id} attribute is appended by Cobalt's send path,
     * matching WA's {@code generateId()} insertion point.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope and
     *         the optional {@code <identifier/>} payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutBizLinkingGetAccountNonceRequest",
            exports = "makeGetAccountNonceRequest", adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WASmaxOutBizLinkingHackBaseIQGetRequestMixin",
            exports = "mergeHackBaseIQGetRequestMixin",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxOutBizLinkingBaseIQGetRequestMixin",
            exports = "mergeBaseIQGetRequestMixin",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public NodeBuilder toNode() {
        var builder = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "fb:thrift_iq")
                .attribute("to", JidServer.user())
                .attribute("type", "get");
        if (fromUserJid != null) {
            builder.attribute("from", fromUserJid);
        }
        if (identifierScope != null) {
            var identifierNode = new NodeBuilder()
                    .description("identifier")
                    .attribute("scope", identifierScope)
                    .build();
            builder.content(identifierNode);
        }
        return builder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxGetAccountNonceRequest) obj;
        return Objects.equals(this.identifierScope, that.identifierScope)
                && Objects.equals(this.fromUserJid, that.fromUserJid);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(identifierScope, fromUserJid);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "SmaxGetAccountNonceRequest[identifierScope=" + identifierScope
                + ", fromUserJid=" + fromUserJid + ']';
    }
}
