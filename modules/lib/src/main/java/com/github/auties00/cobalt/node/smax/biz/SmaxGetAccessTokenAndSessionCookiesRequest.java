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
 * The outbound stanza for the CTWA ad-account access-token-and-session-cookies
 * SMAX RPC, wrapping the user-supplied verification code in the canonical
 * {@code <iq xmlns="fb:thrift_iq" type="get" to="s.whatsapp.net">} envelope.
 *
 * @apiNote
 * Cobalt callers drive this request from the click-to-WhatsApp ad-creation
 * "verify email code" flow surfaced by WA Web in
 * {@code WAWebBizAdCreationVerifyEmailCode.verifyEmailCodeAndPersistToken}
 * and {@code WAWebFetchAdAccountToken}; the user types the recovery code
 * sent to their Facebook business account into the modal, the code is
 * forwarded here, and the relay replies with a
 * {@link SmaxGetAccessTokenAndSessionCookiesResponse} carrying the Graph
 * API bearer token plus the session cookies needed to open the Facebook
 * Ads Manager web UI.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutBizCtwaAdAccountGetAccessTokenAndSessionCookiesRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutBizCtwaAdAccountHackBaseIQGetRequestMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutBizCtwaAdAccountBaseIQGetRequestMixin")
public final class SmaxGetAccessTokenAndSessionCookiesRequest implements SmaxOperation.Request {
    /**
     * The user-supplied verification code embedded as the text content of
     * the {@code <code/>} child under the {@code <parameters/>} payload.
     */
    private final String code;

    /**
     * The optional {@code from} attribute echoed onto the outbound IQ
     * envelope via {@code HackBaseIQGetRequestMixin}.
     */
    private final Jid fromUserJid;

    /**
     * Constructs a request for the given verification code without echoing
     * a {@code from} attribute.
     *
     * @apiNote
     * The common entry point for the verify-email-code flow; the relay
     * derives the user identity from the authenticated socket. Use the
     * two-arg constructor when echoing the local user JID is required
     * (rare on Web, reserved for the hack-mixin path).
     *
     * @param code the verification code; never {@code null}
     * @throws NullPointerException if {@code code} is {@code null}
     */
    public SmaxGetAccessTokenAndSessionCookiesRequest(String code) {
        this(code, null);
    }

    /**
     * Constructs a request for the given verification code, optionally
     * echoing the supplied user JID onto the {@code from} attribute.
     *
     * @apiNote
     * The {@code from} echo is the documented {@code HackBaseIQGetRequestMixin}
     * extension point; pass {@code null} to skip it.
     *
     * @param code        the verification code; never {@code null}
     * @param fromUserJid the optional user JID to echo onto the {@code from} attribute; may be {@code null}
     * @throws NullPointerException if {@code code} is {@code null}
     */
    public SmaxGetAccessTokenAndSessionCookiesRequest(String code, Jid fromUserJid) {
        this.code = Objects.requireNonNull(code, "code cannot be null");
        this.fromUserJid = fromUserJid;
    }

    /**
     * Returns the verification code.
     *
     * @apiNote
     * Identical to the code typed by the user in the verify-email-code
     * modal; the relay validates it against the nonce previously marked
     * used by {@code WAWebCTWABizAccessTokenNonceManager}.
     *
     * @return the verification code; never {@code null}
     */
    public String code() {
        return code;
    }

    /**
     * Returns the optional {@code from} echo.
     *
     * @apiNote
     * Empty when no {@code from} echo was supplied to the constructor;
     * present when the caller opted into the hack-mixin path.
     *
     * @return an {@link Optional} carrying the user JID, or empty
     */
    public Optional<Jid> fromUserJid() {
        return Optional.ofNullable(fromUserJid);
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Builds the canonical
     * {@code <iq xmlns="fb:thrift_iq" type="get" to="s.whatsapp.net">}
     * envelope wrapping a {@code <parameters>} body that itself holds a
     * single {@code <code>} element carrying the verification code.
     *
     * @implNote
     * This implementation hard-codes {@code xmlns="fb:thrift_iq"} and
     * {@code type="get"} per the {@code mergeBaseIQGetRequestMixin} and
     * {@code mergeHackBaseIQGetRequestMixin} composition order; the
     * {@code id} attribute is delegated to
     * {@code WhatsAppClient.sendNode} (matching WA Web's
     * {@code WAComms.sendSmaxStanza} which lets the comms layer assign
     * the id). The optional {@link #fromUserJid()} echo is appended last
     * to match the WA Web {@code OPTIONAL(USER_JID, ...)} merge.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope and the
     *         {@code <parameters/>} payload; never {@code null}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutBizCtwaAdAccountGetAccessTokenAndSessionCookiesRequest",
            exports = "makeGetAccessTokenAndSessionCookiesRequest",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WASmaxOutBizCtwaAdAccountHackBaseIQGetRequestMixin",
            exports = "mergeHackBaseIQGetRequestMixin",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxOutBizCtwaAdAccountBaseIQGetRequestMixin",
            exports = "mergeBaseIQGetRequestMixin",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public NodeBuilder toNode() {
        var codeNode = new NodeBuilder()
                .description("code")
                .content(code)
                .build();
        var parametersNode = new NodeBuilder()
                .description("parameters")
                .content(codeNode)
                .build();
        var builder = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "fb:thrift_iq")
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(parametersNode);
        if (fromUserJid != null) {
            builder.attribute("from", fromUserJid);
        }
        return builder;
    }

    /**
     * Compares this request to {@code obj} for structural equality on the
     * code and the optional {@code from} echo.
     *
     * @param obj the candidate; may be {@code null}
     * @return {@code true} when {@code obj} is a {@link SmaxGetAccessTokenAndSessionCookiesRequest}
     *         with matching {@link #code()} and {@link #fromUserJid()}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxGetAccessTokenAndSessionCookiesRequest) obj;
        return Objects.equals(this.code, that.code)
                && Objects.equals(this.fromUserJid, that.fromUserJid);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return the hash of the code and the optional {@code from} echo
     */
    @Override
    public int hashCode() {
        return Objects.hash(code, fromUserJid);
    }

    /**
     * Returns a debug-friendly rendering naming the code and the optional
     * {@code from} echo.
     *
     * @return a record-style string with the code and {@code from} echo
     */
    @Override
    public String toString() {
        return "SmaxGetAccessTokenAndSessionCookiesRequest[code=" + code
                + ", fromUserJid=" + fromUserJid + ']';
    }
}
