package com.github.auties00.cobalt.node.iq.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.iq.IqOperation;
import java.util.Objects;

/**
 * The outbound {@code <iq xmlns="w:biz:catalog" type="get">} stanza that
 * fetches the signed user-info bundle of a single merchant.
 *
 * @apiNote
 * Use this request to obtain the merchant's signed phone number, TTL
 * timestamp, signature blob and claimed business domain so the
 * buyer-side direct-connection flow can validate that the
 * out-of-band-supplied phone number belongs to the merchant; the cart
 * UI ships this stanza right after the public-key fetch.
 */
@WhatsAppWebModule(moduleName = "WAWebQueryGetSignedUserInfoJob")
public final class IqQueryGetSignedUserInfoRequest implements IqOperation.Request {
    /**
     * The merchant JID stamped into the {@code biz_jid} attribute of
     * the {@code <signed_user_info/>} child.
     */
    private final Jid businessJid;

    /**
     * Constructs a request.
     *
     * @apiNote
     * Pass the merchant JID whose signed user-info bundle should be
     * fetched; any other field on the wire is fixed by the relay
     * schema.
     *
     * @param businessJid the merchant JID; never {@code null}
     * @throws NullPointerException if {@code businessJid} is {@code null}
     */
    public IqQueryGetSignedUserInfoRequest(Jid businessJid) {
        this.businessJid = Objects.requireNonNull(businessJid, "businessJid cannot be null");
    }

    /**
     * Returns the merchant JID.
     *
     * @apiNote
     * Use this getter to read back the merchant JID the stanza will
     * name; the value is routed verbatim into the {@code biz_jid}
     * attribute of the resulting {@code <signed_user_info/>} child.
     *
     * @return the merchant JID; never {@code null}
     */
    public Jid businessJid() {
        return businessJid;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation materialises the WAP envelope produced by the
     * {@code WAWebQueryGetSignedUserInfoJob} export: a single
     * {@code <signed_user_info biz_jid/>} child wrapped in the
     * {@code w:biz:catalog get} IQ frame routed to the WhatsApp service.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebQueryGetSignedUserInfoJob",
            exports = "QueryGetSignedUserInfo", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var signedUserInfo = new NodeBuilder()
                .description("signed_user_info")
                .attribute("biz_jid", businessJid)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:biz:catalog")
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(signedUserInfo);
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
        var that = (IqQueryGetSignedUserInfoRequest) obj;
        return Objects.equals(this.businessJid, that.businessJid);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(businessJid);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "IqQueryGetSignedUserInfoRequest[businessJid=" + businessJid + ']';
    }
}
