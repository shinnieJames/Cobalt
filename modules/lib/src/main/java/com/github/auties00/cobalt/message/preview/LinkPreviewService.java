package com.github.auties00.cobalt.message.preview;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.media.LinkPreviewThumbnail;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessage;
import com.github.auties00.cobalt.message.preview.gate.DomainPreviewableGate;
import com.github.auties00.cobalt.message.preview.gate.SuspiciousLinks;
import com.github.auties00.cobalt.message.preview.linkify.DeepLinkParser;
import com.github.auties00.cobalt.message.preview.linkify.Linkify;
import com.github.auties00.cobalt.message.preview.model.LinkDetails;
import com.github.auties00.cobalt.message.preview.model.LinkThumbnail;
import com.github.auties00.cobalt.message.preview.model.PaymentLinkDetails;
import com.github.auties00.cobalt.message.preview.source.CatalogPreviewResolver;
import com.github.auties00.cobalt.message.preview.source.GroupInvitePreviewResolver;
import com.github.auties00.cobalt.message.preview.source.NewsletterPreviewResolver;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessageBuilder;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import it.auties.linkpreview.LinkPreview;
import it.auties.linkpreview.LinkPreviewMedia;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * Orchestrates the rich link-preview pipeline for outgoing
 * {@link ExtendedTextMessage} bodies.
 *
 * <p>Mirrors {@code WAWebLinkPreviewChatAction.getLinkPreview}: detects
 * URLs in the body, consults the user's privacy settings, dispatches
 * the four WhatsApp deep-link types
 * ({@link DeepLinkParser.DeepLink}), routes newsletter chats
 * through the server-mediated branch, and otherwise issues a direct
 * HTTP fetch via the embedded {@link LinkPreview} library to extract
 * the page title, description, and preview thumbnail.
 *
 * <p>Cobalt is the primary device, so the JS branch that delegates the
 * fetch to the paired phone via the
 * {@code GENERATE_LINK_PREVIEW} peer-data-operation does not apply;
 * the direct fetch path is the canonical implementation here.
 *
 * <p>Instances are owned by the caller (typically the
 * {@code MessageSendingService}) and re-used across sends so the
 * preview cache and HTTP client amortise their setup cost.
 */
@WhatsAppWebModule(moduleName = "WAWebLinkPreviewChatAction")
public final class LinkPreviewService {
    /**
     * The owning client, used for store access and newsletter MEX
     * round-trips.
     */
    private final WhatsAppClient client;

    /**
     * The AB-props service consulted to gate the rich fetch and to
     * derive the per-request HTTP timeout.
     */
    private final ABPropsService abPropsService;

    /**
     * Per-session preview cache.
     */
    private final LinkPreviewCache cache;

    /**
     * HTTP client used to download the preview thumbnail. The
     * {@code LinkPreview} library supplies its own client for HTML
     * fetches; this one is used only for the image GET.
     */
    private final HttpClient httpClient;

    /**
     * Creates a fresh service bound to {@code client}.
     *
     * @param client         the owning client
     * @param abPropsService the AB-props service used for feature
     *                       gating and timeout configuration
     */
    public LinkPreviewService(WhatsAppClient client, ABPropsService abPropsService) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService cannot be null");
        this.cache = new LinkPreviewCache();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
    }

    /**
     * Returns the per-request HTTP timeout derived from the
     * {@code link_preview_wait_time} AB-prop (in seconds).
     *
     * <p>WhatsApp Web uses this AB-prop value to bound the wait on the
     * primary device's PDO response; Cobalt is the primary, so the same
     * value is repurposed as the bound on the direct HTTP fetch.
     *
     * @return the timeout duration
     */
    @WhatsAppWebExport(moduleName = "WAWebABProps", exports = "getABPropConfigValue",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private Duration linkPreviewTimeout() {
        var seconds = abPropsService.getInt(ABProp.LINK_PREVIEW_WAIT_TIME);
        return Duration.ofSeconds(Math.max(1, seconds));
    }

    /**
     * Decorates the outgoing {@code message} with a link preview
     * derived from the first URL in its body, when one is present and
     * the user/chat-level gates allow it.
     *
     * @param chatJid the target chat JID
     * @param message the outgoing extended-text message; mutated in
     *                place
     */
    @WhatsAppWebExport(moduleName = "WAWebLinkPreviewChatAction", exports = "getLinkPreview",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void decorate(Jid chatJid, ExtendedTextMessage message) {
        if (message == null) {
            return;
        }
        if (client.store().disableLinkPreviews()) {
            return;
        }
        var text = message.text().orElse(null);
        if (text == null || text.isEmpty()) {
            return;
        }
        var match = Linkify.findLink(text, false).orElse(null);
        if (match == null) {
            return;
        }
        // The JS pipeline only consults the suspicious gate after the deep link branches.
        // In practice deep link patterns are restricted to canonical WhatsApp domains
        // that always pass the gate, so applying the check up front is equivalent.
        if (SuspiciousLinks.isSuspicious(client, chatJid, match)) {
            return;
        }
        if (!DomainPreviewableGate.isPreviewable(client, abPropsService, chatJid, match.domain())) {
            return;
        }
        var newsletterChat = chatJid != null && chatJid.hasNewsletterServer();
        var cached = cache.get(match.href(), newsletterChat).orElse(null);
        if (cached != null) {
            if (!LinkPreviewCache.isNegative(cached)) {
                copyPreviewFields(cached, message);
            }
            return;
        }
        // Catalog, Product and GroupInvite return non-null when the resolution succeeds.
        // In that case the deep link branch wins and the rich fetch is skipped.
        // PaymentLink threads through previewType and paymentDetails and continues into
        // the rich fetch so the page title and description populate the card. Anything
        // else goes straight to rich fetch, or to the newsletter MEX for newsletter chats.
        var command = DeepLinkParser.parse(client, abPropsService, match.href());
        var previewType = ExtendedTextMessage.PreviewType.NONE;
        PaymentLinkDetails paymentDetails = null;
        var attached = false;
        switch (command) {
            case DeepLinkParser.DeepLink.GroupInvite groupInvite -> {
                var resolved = GroupInvitePreviewResolver.resolve(client, groupInvite.code(), httpClient, linkPreviewTimeout()).orElse(null);
                if (resolved != null) {
                    LinkPreviewUtils.attach(message, resolved.details(), resolved.thumbnail(), null);
                    attached = true;
                }
            }
            case DeepLinkParser.DeepLink.Catalog catalog -> {
                var resolved = CatalogPreviewResolver.resolve(client, catalog.catalogOwnerJid(), null, httpClient, linkPreviewTimeout()).orElse(null);
                if (resolved != null) {
                    LinkPreviewUtils.attach(message, resolved.details(), resolved.thumbnail(), null);
                    attached = true;
                }
            }
            case DeepLinkParser.DeepLink.Product product -> {
                var resolved = CatalogPreviewResolver.resolve(client, product.businessOwnerJid(), product.productId(), httpClient, linkPreviewTimeout()).orElse(null);
                if (resolved != null) {
                    LinkPreviewUtils.attach(message, resolved.details(), resolved.thumbnail(), null);
                    attached = true;
                }
            }
            case DeepLinkParser.DeepLink.PaymentLink payment -> {
                // shouldDetectInComposer is SMB only. Non SMB clients still see the URL but
                // do not produce a payment card.
                if (payment.shouldDetectInComposer()) {
                    previewType = ExtendedTextMessage.PreviewType.PAYMENT_LINKS;
                    paymentDetails = new PaymentLinkDetails(payment.psp());
                }
            }
            case DeepLinkParser.DeepLink.NotApplicable ignored -> {
            }
        }
        if (!attached) {
            if (newsletterChat) {
                var newsletter = NewsletterPreviewResolver.resolve(client, match.href()).orElse(null);
                if (newsletter != null) {
                    LinkPreviewUtils.attach(message, newsletter.details(), newsletter.thumbnail(), null);
                    attached = true;
                }
            } else {
                attached = attachRichPreview(match, message, previewType, paymentDetails);
            }
        }
        cacheCurrentMessage(match.href(), newsletterChat, attached, message);
    }

    /**
     * Issues the HTTP fetch via {@link LinkPreview} and attaches the
     * resulting metadata to {@code message}, falling back to a minimal
     * preview on any failure so the recipient still sees a clickable
     * card.
     *
     * <p>The fetch path is gated behind
     * {@link ABProp#WEB_LINK_PREVIEW_SYNC_ENABLED}: when the AB-prop
     * is off, the rich fetch is skipped entirely and the minimal
     * fallback is attached, mirroring WA Web's
     * {@code !web_link_preview_sync_enabled || !PrimaryFeatures.linkPreview}
     * branch.
     *
     * @param match            the detected URL
     * @param message          the outgoing message to enrich
     * @param baselinePreviewType the preview-type seed selected by the
     *                         API-cmd dispatch (NONE for ordinary URLs,
     *                         PAYMENT_LINKS for payment links)
     * @param paymentDetails   the resolved payment-link details when
     *                         the URL is a payment link, or {@code null}
     * @return whether a preview was attached (always {@code true} since
     *         the minimal-fallback path always succeeds)
     */
    private boolean attachRichPreview(Linkify.Match match,
                                      ExtendedTextMessage message,
                                      ExtendedTextMessage.PreviewType baselinePreviewType,
                                      PaymentLinkDetails paymentDetails) {
        // When web_link_preview_sync_enabled is off the rich fetch is skipped and the
        // minimal fallback is attached, mirroring the JS genMinimalLinkPreview branch.
        if (!abPropsService.getBool(ABProp.WEB_LINK_PREVIEW_SYNC_ENABLED)) {
            attachMinimal(match, message, baselinePreviewType, paymentDetails);
            return true;
        }
        URI uri;
        try {
            uri = URI.create(match.href());
        } catch (IllegalArgumentException malformed) {
            attachMinimal(match, message, baselinePreviewType, paymentDetails);
            return true;
        }
        var preview = LinkPreview.createPreview(uri).orElse(null);
        if (preview == null) {
            attachMinimal(match, message, baselinePreviewType, paymentDetails);
            return true;
        }
        var thumbnailBytes = downloadThumbnail(preview.images());
        var previewType = resolvePreviewType(preview.mediaType(), baselinePreviewType);
        var details = new LinkDetails(
                preview.title(),
                preview.siteDescription(),
                previewType,
                Boolean.TRUE);
        var thumbnail = buildThumbnail(thumbnailBytes);
        LinkPreviewUtils.attach(message, details, thumbnail, paymentDetails);
        return true;
    }

    /**
     * Encrypts the inline thumbnail bytes and uploads them to the
     * media CDN, mirroring WhatsApp Web's
     * {@code WAWebLinkPreviewUtils.getThumbnailDetails} which produces
     * an HQ thumbnail accessible to the recipient via direct path.
     *
     * <p>When the upload succeeds, the resulting CDN coordinates
     * (direct path, encrypted SHA-256, media key, key timestamp) are
     * threaded onto the {@link LinkThumbnail} together
     * with the inline JPEG bytes for receivers that render the LQ
     * placeholder while the HQ download is in flight.
     *
     * <p>When the upload fails (no media connection, network error,
     * unsupported codec), only the inline JPEG bytes are kept so the
     * preview still renders.
     *
     * @param thumbnailBytes the JPEG bytes to embed inline and upload
     * @return the assembled thumbnail, or {@code null} when the bytes
     *         are unavailable
     */
    @WhatsAppWebExport(moduleName = "WAWebLinkPreviewUtils", exports = "getThumbnailDetails",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private LinkThumbnail buildThumbnail(byte[] thumbnailBytes) {
        if (thumbnailBytes == null) {
            return null;
        }
        var provider = new LinkPreviewThumbnail();
        try {
            var connection = client.store().awaitMediaConnection();
            var uploaded = connection.upload(provider, new ByteArrayInputStream(thumbnailBytes));
            if (!uploaded) {
                return new LinkThumbnail(thumbnailBytes, null,
                        sha256(thumbnailBytes), null, null, null, null, null);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new LinkThumbnail(thumbnailBytes, null,
                    sha256(thumbnailBytes), null, null, null, null, null);
        } catch (Throwable _) {
            return new LinkThumbnail(thumbnailBytes, null,
                    sha256(thumbnailBytes), null, null, null, null, null);
        }
        return new LinkThumbnail(
                thumbnailBytes,
                provider.mediaDirectPath().orElse(null),
                provider.mediaSha256().orElse(null),
                provider.mediaEncryptedSha256().orElse(null),
                provider.mediaKey().orElse(null),
                provider.mediaKeyTimestamp().orElse(null),
                null,
                null);
    }

    /**
     * Attaches the minimal {@code {title=domain, description=url,
     * doNotPlayInline=true}} card produced by
     * {@code WAWebGenMinimalLinkPreviewChatAction.genMinimalLinkPreview}.
     *
     * @param match           the detected URL
     * @param message         the outgoing message to enrich
     * @param previewType     the resolved preview-type, defaulting to
     *                        {@link ExtendedTextMessage.PreviewType#NONE}
     *                        for plain URLs
     * @param paymentDetails  the payment-link details for payment URLs,
     *                        or {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebGenMinimalLinkPreviewChatAction", exports = "genMinimalLinkPreview",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void attachMinimal(Linkify.Match match,
                               ExtendedTextMessage message,
                               ExtendedTextMessage.PreviewType previewType,
                               PaymentLinkDetails paymentDetails) {
        var minimal = LinkPreviewUtils.minimalDetails(match.url(), match.domain(), previewType);
        LinkPreviewUtils.attach(message, minimal, null, paymentDetails);
    }

    /**
     * Resolves the wire-format preview type from the page's
     * {@code og:type} hint, preserving any baseline that the API-cmd
     * dispatch already selected (e.g. PAYMENT_LINKS).
     *
     * @param mediaType the {@code og:type}/{@code og:medium} value
     * @param baseline  the baseline preview-type seed
     * @return the resolved preview-type
     */
    @WhatsAppWebExport(moduleName = "WAWebLinkPreviewChatAction", exports = "getLinkPreview",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static ExtendedTextMessage.PreviewType resolvePreviewType(String mediaType,
                                                                      ExtendedTextMessage.PreviewType baseline) {
        if (baseline != null && baseline != ExtendedTextMessage.PreviewType.NONE) {
            return baseline;
        }
        if (mediaType != null && mediaType.toLowerCase().startsWith("video")) {
            return ExtendedTextMessage.PreviewType.VIDEO;
        }
        return ExtendedTextMessage.PreviewType.NONE;
    }

    /**
     * Downloads the first image referenced by {@code images} and
     * returns the raw bytes for embedding as the JPEG thumbnail,
     * routed through {@link PreviewThumbnailFetcher#download} so the
     * payload is sized-bounded and (when {@code java.desktop} is on
     * the runtime path) resized to 100×100.
     *
     * @param images the image set returned by the linkpreview library
     * @return the raw bytes, or {@code null} when the set is empty or
     *         the download failed
     */
    private byte[] downloadThumbnail(Set<LinkPreviewMedia> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        var firstImage = images.iterator().next();
        if (firstImage == null || firstImage.uri() == null) {
            return null;
        }
        return PreviewThumbnailFetcher.download(httpClient, firstImage.uri(), linkPreviewTimeout());
    }

    /**
     * Computes the SHA-256 of {@code bytes}, used as the plaintext
     * thumbnail digest.
     *
     * @param bytes the bytes to hash
     * @return the SHA-256 digest, or {@code null} when SHA-256 is
     *         unavailable
     */
    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            return null;
        }
    }

    /**
     * Stores the resolved preview in the cache so subsequent sends of
     * the same URL short-circuit.
     *
     * @param url            the URL key
     * @param newsletterChat whether the resolution happened in a
     *                       newsletter chat
     * @param attached       whether a preview was attached
     * @param message        the message that just had the preview
     *                       attached, used as the cache value
     */
    private void cacheCurrentMessage(String url, boolean newsletterChat, boolean attached, ExtendedTextMessage message) {
        if (!attached) {
            cache.put(url, newsletterChat, null);
            return;
        }
        // Newsletter previews are only cached when the HQ thumbnail is present, so
        // subsequent sends of the same URL re-query the server while a LQ only result
        // is still considered transient.
        if (newsletterChat && message.thumbnailDirectPath().isEmpty()) {
            return;
        }
        var snapshotBuilder = new ExtendedTextMessageBuilder()
                .title(message.title().orElse(null))
                .description(message.description().orElse(null))
                .previewType(message.previewType().orElse(null))
                .doNotPlayInline(message.doNotPlayInline())
                .jpegThumbnail(message.jpegThumbnail().orElse(null))
                .thumbnailDirectPath(message.thumbnailDirectPath().orElse(null))
                .thumbnailSha256(message.thumbnailSha256().orElse(null))
                .thumbnailEncSha256(message.thumbnailEncSha256().orElse(null))
                .mediaKey(message.mediaKey().orElse(null))
                .mediaKeyTimestamp(message.mediaKeyTimestamp().orElse(null))
                .paymentLinkMetadata(message.paymentLinkMetadata().orElse(null));
        message.thumbnailWidth().ifPresent(width -> snapshotBuilder.thumbnailWidth(width));
        message.thumbnailHeight().ifPresent(height -> snapshotBuilder.thumbnailHeight(height));
        cache.put(url, newsletterChat, snapshotBuilder.build());
    }

    /**
     * Replays the preview fields of a cached snapshot onto a fresh
     * outgoing message, so two messages sharing the same URL render
     * the same preview without re-fetching.
     *
     * @param cached  the cached snapshot
     * @param message the outgoing message to enrich
     */
    private static void copyPreviewFields(ExtendedTextMessage cached, ExtendedTextMessage message) {
        cached.title().ifPresent(message::setTitle);
        cached.description().ifPresent(message::setDescription);
        cached.previewType().ifPresent(message::setPreviewType);
        message.setDoNotPlayInline(cached.doNotPlayInline());
        cached.jpegThumbnail().ifPresent(message::setJpegThumbnail);
        cached.thumbnailDirectPath().ifPresent(message::setThumbnailDirectPath);
        cached.thumbnailSha256().ifPresent(message::setThumbnailSha256);
        cached.thumbnailEncSha256().ifPresent(message::setThumbnailEncSha256);
        cached.mediaKey().ifPresent(message::setMediaKey);
        cached.mediaKeyTimestamp().ifPresent(message::setMediaKeyTimestamp);
        cached.thumbnailWidth().ifPresent(message::setThumbnailWidth);
        cached.thumbnailHeight().ifPresent(message::setThumbnailHeight);
        cached.paymentLinkMetadata().ifPresent(message::setPaymentLinkMetadata);
    }
}
