package com.github.auties00.cobalt.message.preview.gate;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.message.preview.LinkPreviewService;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;

/**
 * Decides whether a domain is allowed to render a rich link preview
 * for the supplied chat.
 *
 * <p>For everything except newsletter chats the answer is always yes.
 * For newsletter chats, when the
 * {@code channels_hide_news_url_preview} AB-prop is on, the caller has
 * to consult the server through the newsletter is-domain-previewable
 * action; that action is implemented inside the newsletter branch of
 * {@link LinkPreviewService}, so this class only covers the synchronous
 * gate.
 */
@WhatsAppWebModule(moduleName = "WAWebCheckIfDomainIsPreviewable")
public final class DomainPreviewableGate {
    /**
     * Hidden constructor for the utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private DomainPreviewableGate() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Returns whether {@code domain} is allowed to render a rich link
     * preview for {@code chatJid}.
     *
     * @param client         the WhatsApp client whose newsletter
     *                       allow-list is consulted on the server
     *                       branch
     * @param abPropsService the AB-props service used to read the
     *                       newsletter URL-preview gate
     * @param chatJid        the target chat JID, used to detect
     *                       newsletter chats
     * @param domain         the link's domain (informational; the gate
     *                       is currently AB-prop driven, not domain
     *                       driven)
     * @return {@code true} when previews are allowed
     */
    @WhatsAppWebExport(moduleName = "WAWebCheckIfDomainIsPreviewable", exports = "checkIfDomainIsPreviewable",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static boolean isPreviewable(WhatsAppClient client, ABPropsService abPropsService, Jid chatJid, String domain) {
        if (chatJid == null || !chatJid.hasNewsletterServer()) {
            return true;
        }
        if (!abPropsService.getBool(ABProp.CHANNELS_HIDE_NEWS_URL_PREVIEW)) {
            return true;
        }
        // When channels_hide_news_url_preview is on, defer to the server side allow list
        // via mexFetchNewsletterIsDomainPreviewable. The action returns false for any
        // malformed response or error, so the gate closes if the round trip fails.
        try {
            return domain != null && client.isNewsletterDomainPreviewable(domain);
        } catch (RuntimeException _) {
            return false;
        }
    }
}
