package com.github.auties00.cobalt.message.preview.source;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessage;
import com.github.auties00.cobalt.model.newsletter.NewsletterLinkPreview;
import com.github.auties00.cobalt.message.preview.model.LinkDetails;
import com.github.auties00.cobalt.message.preview.model.LinkThumbnail;
import com.github.auties00.cobalt.message.preview.model.ResolvedPreview;

import java.util.Base64;
import java.util.Optional;

/**
 * Fetches a newsletter link preview by querying the server through
 * {@link WhatsAppClient#queryNewsletterLinkPreview(String)}, mirroring
 * the JS {@code fetchPlaintextLinkPreviewAction}.
 *
 * <p>Newsletter chats route through this server-mediated MEX query
 * because the previewability of an arbitrary URL inside a channel is
 * gated by server-side rules that cannot be evaluated client-side.
 */
@WhatsAppWebModule(moduleName = "WAWebNewsletterFetchLinkPreviewAction")
public final class NewsletterPreviewResolver {
    /**
     * Hidden constructor for the utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private NewsletterPreviewResolver() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Returns the preview details for {@code url} when the server
     * answers with a non-empty result, or empty when the lookup failed
     * or the server declined to render a preview.
     *
     * @param client the WhatsApp client used to query the server
     * @param url    the URL whose preview is requested
     * @return the preview details and matching thumbnail, or empty
     */
    @WhatsAppWebExport(moduleName = "WAWebNewsletterFetchLinkPreviewAction", exports = "fetchPlaintextLinkPreviewAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<ResolvedPreview> resolve(WhatsAppClient client, String url) {
        if (client == null || url == null) {
            return Optional.empty();
        }
        Optional<NewsletterLinkPreview> response;
        try {
            response = client.queryNewsletterLinkPreview(url);
        } catch (RuntimeException _) {
            return Optional.empty();
        }
        if (response.isEmpty()) {
            return Optional.empty();
        }
        var resolved = response.get();
        var details = new LinkDetails(
                resolved.title().orElse(null),
                resolved.description().orElse(null),
                ExtendedTextMessage.PreviewType.NONE,
                true
        );
        // When the server returns directPath, hash and dimensions, surface them on the
        // outgoing message so receivers can download the HQ thumbnail on demand. The
        // base64 thumbData stays as the inline JPEG fallback rendered while the HQ
        // download is in flight.
        var thumbHash = resolved.thumbnailHash().map(NewsletterPreviewResolver::decodeBase64).orElse(null);
        var width = resolved.thumbnailWidth().isPresent() ? resolved.thumbnailWidth().getAsInt() : null;
        var height = resolved.thumbnailHeight().isPresent() ? resolved.thumbnailHeight().getAsInt() : null;
        var thumbnail = new LinkThumbnail(
                resolved.thumbnailData().map(NewsletterPreviewResolver::decodeBase64).orElse(null),
                resolved.thumbnailDirectPath().orElse(null),
                thumbHash,
                null,
                null,
                null,
                width,
                height
        );
        return Optional.of(new ResolvedPreview(details, thumbnail));
    }

    /**
     * Decodes a base64-encoded thumbnail string. Returns {@code null}
     * when the input is malformed so the caller can fall back to a
     * minimal preview.
     *
     * @param base64 the base64 string
     * @return the decoded bytes, or {@code null}
     */
    private static byte[] decodeBase64(String base64) {
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
