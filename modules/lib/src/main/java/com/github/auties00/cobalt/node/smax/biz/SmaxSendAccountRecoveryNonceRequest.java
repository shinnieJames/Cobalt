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
 * The outbound stanza that asks the relay to email an account
 * recovery code to the active CTWA biz user.
 *
 * @apiNote
 * Used by the CTWA recovery flow in
 * {@code WAWebRequestAdAccountRecoveryCode.requestAdAccountRecoveryCode},
 * which is triggered when
 * {@link SmaxRequestSilentNonceResponse.RecoveryRequired} forces the
 * user to confirm account ownership before a silent nonce can be
 * issued. The request carries no payload; the optional {@code from}
 * attribute is the only knob and is normally left {@code null}.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutBizCtwaAdAccountSendAccountRecoveryNonceRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutBizCtwaAdAccountHackBaseIQGetRequestMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutBizCtwaAdAccountBaseIQGetRequestMixin")
public final class SmaxSendAccountRecoveryNonceRequest implements SmaxOperation.Request {
    /**
     * The optional user JID echoed onto the outbound IQ's
     * {@code from} attribute; {@code null} omits the attribute.
     */
    private final Jid fromUserJid;

    /**
     * Constructs a request with no {@code from} echo.
     *
     * @apiNote
     * The default form expected by
     * {@code requestAdAccountRecoveryCode}, which calls
     * {@code sendSendAccountRecoveryNonceRPC({})} with no arguments.
     */
    public SmaxSendAccountRecoveryNonceRequest() {
        this(null);
    }

    /**
     * Constructs a request optionally echoing the supplied user JID
     * onto the {@code from} attribute.
     *
     * @apiNote
     * Reserved for multi-device callers that need the outbound IQ
     * to look like it originated from a specific linked user JID;
     * standard CTWA callers pass {@code null}.
     *
     * @param fromUserJid the optional user JID; may be {@code null}
     */
    public SmaxSendAccountRecoveryNonceRequest(Jid fromUserJid) {
        this.fromUserJid = fromUserJid;
    }

    /**
     * Returns the optional {@code from} echo.
     *
     * @apiNote
     * Returns {@link Optional#empty()} when the request was built
     * without a {@code from} echo (the standard
     * {@code requestAdAccountRecoveryCode} case).
     *
     * @return an {@link Optional} carrying the user JID
     */
    public Optional<Jid> fromUserJid() {
        return Optional.ofNullable(fromUserJid);
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Stamps {@code xmlns="fb:thrift_iq"}, {@code type="get"},
     * {@code to="s.whatsapp.net"} and the optional {@code from}
     * echo. The IQ {@code id} is assigned by the dispatcher.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutBizCtwaAdAccountSendAccountRecoveryNonceRequest",
            exports = "makeSendAccountRecoveryNonceRequest", adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WASmaxOutBizCtwaAdAccountHackBaseIQGetRequestMixin",
            exports = "mergeHackBaseIQGetRequestMixin", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxOutBizCtwaAdAccountBaseIQGetRequestMixin",
            exports = "mergeBaseIQGetRequestMixin", adaptation = WhatsAppAdaptation.ADAPTED)
    public NodeBuilder toNode() {
        var builder = new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "fb:thrift_iq")
                .attribute("to", JidServer.user())
                .attribute("type", "get");
        if (fromUserJid != null) {
            builder.attribute("from", fromUserJid);
        }
        return builder;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxSendAccountRecoveryNonceRequest) obj;
        return Objects.equals(this.fromUserJid, that.fromUserJid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromUserJid);
    }

    @Override
    public String toString() {
        return "SmaxSendAccountRecoveryNonceRequest[fromUserJid=" + fromUserJid + ']';
    }
}
