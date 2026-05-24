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
 * checks whether a buyer-supplied postcode falls inside a merchant's
 * service area.
 *
 * @apiNote
 * Use this request from the cart-postcode entry surface after the
 * buyer types or pastes their postcode; the
 * {@code WAWebBusinessDirectConnectionCollection.verifyAndSavePostcode}
 * delegate first encrypts the postcode with the merchant's
 * direct-connection cypher and then ships this stanza to ask the relay
 * whether the encrypted address resolves to a serviceable location.
 */
@WhatsAppWebModule(moduleName = "WAWebVerifyPostcodeJob")
public final class IqVerifyPostcodeRequest implements IqOperation.Request {
    /**
     * The merchant JID stamped into the {@code biz_jid} attribute of
     * the {@code <verify_postcode/>} child.
     */
    private final Jid businessJid;

    /**
     * The opaque encrypted-postcode blob produced by the buyer-side
     * direct-connection encryption flow, stamped as the body of the
     * {@code <direct_connection_encrypted_info/>} grandchild.
     */
    private final String directConnectionEncryptedInfo;

    /**
     * Constructs a request.
     *
     * @apiNote
     * Pass the merchant JID and the encrypted-postcode blob already
     * produced by the
     * {@code WAWebDirectConnectionCypher.cypherStringToString} flow;
     * the relay never sees the plaintext postcode.
     *
     * @param businessJid                   the merchant JID; never
     *                                      {@code null}
     * @param directConnectionEncryptedInfo the encrypted-postcode
     *                                      blob; never {@code null}
     * @throws NullPointerException if either argument is {@code null}
     */
    public IqVerifyPostcodeRequest(Jid businessJid, String directConnectionEncryptedInfo) {
        this.businessJid = Objects.requireNonNull(businessJid, "businessJid cannot be null");
        this.directConnectionEncryptedInfo = Objects.requireNonNull(
                directConnectionEncryptedInfo, "directConnectionEncryptedInfo cannot be null");
    }

    /**
     * Returns the merchant JID.
     *
     * @apiNote
     * Use this getter to read back the merchant JID the stanza will
     * name; the value is routed verbatim into the {@code biz_jid}
     * attribute of the resulting {@code <verify_postcode/>} child.
     *
     * @return the merchant JID; never {@code null}
     */
    public Jid businessJid() {
        return businessJid;
    }

    /**
     * Returns the encrypted-postcode blob.
     *
     * @apiNote
     * Use this getter to read back the encrypted blob the stanza will
     * stamp; the relay treats it as opaque and forwards it to the
     * merchant's direct-connection service for decryption.
     *
     * @return the blob; never {@code null}
     */
    public String directConnectionEncryptedInfo() {
        return directConnectionEncryptedInfo;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation materialises the WAP envelope produced by
     * the {@code WAWebVerifyPostcodeJob} export: a
     * {@code <direct_connection_encrypted_info/>} grandchild wrapped
     * in a {@code <verify_postcode biz_jid/>} child and the
     * {@code w:biz:catalog get} IQ frame routed to the WhatsApp
     * service. WA Web routes the same call through
     * {@code WAWebGraphQLVerifyPostcodeJob.verifyPostcode} when the
     * {@code isGraphQLForVerifyPostcodeEnabled} gating flag is on,
     * but Cobalt keeps the WAP-IQ payload as the single transport.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebVerifyPostcodeJob",
            exports = "VerifyPostcode", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var encryptedInfo = new NodeBuilder()
                .description("direct_connection_encrypted_info")
                .content(directConnectionEncryptedInfo)
                .build();
        var verifyPostcode = new NodeBuilder()
                .description("verify_postcode")
                .attribute("biz_jid", businessJid)
                .content(encryptedInfo)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:biz:catalog")
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(verifyPostcode);
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
        var that = (IqVerifyPostcodeRequest) obj;
        return Objects.equals(this.businessJid, that.businessJid)
                && Objects.equals(this.directConnectionEncryptedInfo, that.directConnectionEncryptedInfo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(businessJid, directConnectionEncryptedInfo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "IqVerifyPostcodeRequest[businessJid=" + businessJid
                + ", directConnectionEncryptedInfo=" + directConnectionEncryptedInfo + ']';
    }
}
