package com.github.auties00.cobalt.media.transcode.text.preview;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessage;
import com.github.auties00.cobalt.model.newsletter.NewsletterLinkPreview;

import java.util.Base64;

/**
 * The per-source resolver that builds preview cards for URLs sent
 * inside a newsletter chat.
 *
 * @apiNote
 * Mirrors {@code WAWebNewsletterFetchLinkPreviewAction.fetchPlaintextLinkPreviewAction};
 * called from {@link com.github.auties00.cobalt.media.transcode.text.TextPipeline#run} for newsletter recipients
 * because the previewability of a URL inside a channel is gated by
 * server-side rules that cannot be evaluated client-side, so the
 * rich og-tag fetch must happen on the server.
 */
@WhatsAppWebModule(moduleName = "WAWebNewsletterFetchLinkPreviewAction")
public final class NewsletterPreviewResolver {
    /**
     * The hidden constructor of the utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private NewsletterPreviewResolver() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Resolves the preview for a newsletter URL and stamps the
     * result onto {@code message}.
     *
     * @apiNote
     * Issues the MEX {@code mexFetchPlaintextLinkPreview} request
     * via {@link WhatsAppClient#queryNewsletterLinkPreview(String)}
     * and writes every populated field of the response onto
     * {@code message} in place: {@code title}, {@code description},
     * {@code previewType}, {@code doNotPlayInline},
     * {@code jpegThumbnail} (LQ placeholder),
     * {@code thumbnailDirectPath} (HQ download), the matching
     * SHA-256, and the {@code thumbnailWidth} / {@code thumbnailHeight}
     * advisory dimensions.
     *
     * @implNote
     * This implementation returns {@code false} on any
     * {@link RuntimeException} from the server round-trip; the
     * link-preview pipeline then falls back to the minimal preview
     * card so the recipient still sees a clickable URL.
     *
     * @param client  the WhatsApp client used to query the server
     * @param url     the URL whose preview is requested
     * @param message the outgoing message to enrich; mutated in
     *                place
     * @return {@code true} when a preview was applied
     */
    @WhatsAppWebExport(moduleName = "WAWebNewsletterFetchLinkPreviewAction", exports = "fetchPlaintextLinkPreviewAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static boolean resolve(WhatsAppClient client, String url, ExtendedTextMessage message) {
        if (client == null || url == null || message == null) {
            return false;
        }
        var response = querySafely(client, url);
        if (response == null) {
            return false;
        }
        response.title().ifPresent(message::setTitle);
        response.description().ifPresent(message::setDescription);
        message.setPreviewType(ExtendedTextMessage.PreviewType.NONE);
        message.setDoNotPlayInline(Boolean.TRUE);
        response.thumbnailData()
                .map(NewsletterPreviewResolver::decodeBase64)
                .ifPresent(message::setJpegThumbnail);
        response.thumbnailDirectPath().ifPresent(message::setThumbnailDirectPath);
        response.thumbnailHash()
                .map(NewsletterPreviewResolver::decodeBase64)
                .ifPresent(message::setThumbnailSha256);
        if (response.thumbnailWidth().isPresent()) {
            message.setThumbnailWidth(response.thumbnailWidth().getAsInt());
        }
        if (response.thumbnailHeight().isPresent()) {
            message.setThumbnailHeight(response.thumbnailHeight().getAsInt());
        }
        return true;
    }

    /**
     * Issues the newsletter link-preview query and swallows any
     * transport-level error.
     *
     * @apiNote
     * Called from {@link #resolve}; on failure the link-preview
     * pipeline falls back to the minimal preview card.
     *
     * @param client the WhatsApp client used to query the server
     * @param url    the URL to resolve
     * @return the server response, or {@code null} when the query
     *         failed
     */
    private static NewsletterLinkPreview querySafely(
            WhatsAppClient client, String url) {
        try {
            return client.queryNewsletterLinkPreview(url).orElse(null);
        } catch (RuntimeException _) {
            return null;
        }
    }

    /**
     * Decodes a base64-encoded thumbnail string.
     *
     * @apiNote
     * Returns {@code null} on malformed input so the link-preview
     * pipeline can fall back to a minimal preview instead of
     * propagating the {@link IllegalArgumentException}.
     *
     * @param base64 the base64 string
     * @return the decoded bytes, or {@code null} when the input is
     *         malformed
     */
    private static byte[] decodeBase64(String base64) {
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
