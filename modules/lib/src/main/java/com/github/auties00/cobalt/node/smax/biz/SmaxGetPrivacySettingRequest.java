package com.github.auties00.cobalt.node.smax.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code GetPrivacySetting} IQ request stanza for the
 * SMB data-sharing-with-Meta consent bridge.
 *
 * @apiNote
 * Used by Cobalt clients that mirror WA Web's
 * {@code WAWebCTWABizDataSharingJob.getCtwaBizDataSharingSettingJob}
 * flow, consulted from
 * {@code WAWebCommonCTWADataSharing.fetchDataSharingSettingAndUpdateModel}
 * to refresh the SMB data-sharing-with-Meta consent value before
 * surfacing the consent banner on the CTWA settings page.
 *
 * @implNote
 * This implementation mirrors WA Web's
 * {@code makeGetPrivacySettingRequest} by stamping the static
 * {@code xmlns="w:biz"} envelope and emitting a bare
 * {@code <privacy/>} child; the {@code id} attribute is appended by
 * Cobalt's send path, matching WA's {@code generateId()} insertion
 * point.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutBizSettingsGetPrivacySettingRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutBizSettingsBaseIQGetRequestMixin")
public final class SmaxGetPrivacySettingRequest implements SmaxOperation.Request {
    /**
     * Constructs a new request.
     *
     * @apiNote
     * The shape is parameter-free because the relay enumerates the
     * single documented consent surface on the active user's
     * account; no client-side selectors are accepted.
     */
    public SmaxGetPrivacySettingRequest() {
    }

    /**
     * Builds the outbound IQ stanza ready for dispatch.
     *
     * @implNote
     * This implementation composes two WA Web mixins in a single
     * pass: {@code makeGetPrivacySettingRequest} stamps the
     * {@code xmlns="w:biz"} envelope and the bare
     * {@code <privacy/>} child, and
     * {@code mergeBaseIQGetRequestMixin} stamps {@code type="get"};
     * the {@code id} attribute is appended by Cobalt's send path.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope and
     *         the bare {@code <privacy/>} child
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutBizSettingsGetPrivacySettingRequest",
            exports = "makeGetPrivacySettingRequest", adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WASmaxOutBizSettingsBaseIQGetRequestMixin",
            exports = "mergeBaseIQGetRequestMixin",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public NodeBuilder toNode() {
        var privacyNode = new NodeBuilder()
                .description("privacy")
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:biz")
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(privacyNode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        return obj != null && obj.getClass() == this.getClass();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return SmaxGetPrivacySettingRequest.class.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "SmaxGetPrivacySettingRequest[]";
    }
}
