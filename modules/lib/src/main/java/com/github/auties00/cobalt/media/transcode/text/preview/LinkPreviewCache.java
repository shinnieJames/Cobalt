package com.github.auties00.cobalt.media.transcode.text.preview;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessage;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessageBuilder;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The per-session URL-keyed cache of resolved link previews used by
 * {@link com.github.auties00.cobalt.media.transcode.text.TextPipeline} to short-circuit repeated sends of the
 * same URL.
 *
 * @apiNote
 * Mirrors {@code WAWebLinkPreviewCache}, which exposes two parallel
 * {@code Map} stores so a preview rendered inside a newsletter chat
 * (where the previewability of a URL is gated by server-side rules)
 * does not leak into ordinary 1:1 chats and vice versa.
 *
 * @implNote
 * This implementation collapses both stores onto one instance with two
 * {@link ConcurrentMap} fields selected at call time. A negative
 * sentinel is used so URLs that resolved without producing a preview
 * still short-circuit subsequent lookups; the
 * {@link #isNegative(ExtendedTextMessage)} helper lets
 * {@link com.github.auties00.cobalt.media.transcode.text.TextPipeline} branch on the sentinel without exposing
 * it. The caches are unbounded because session lifetimes are short and
 * the JS counterpart is also unbounded.
 */
@WhatsAppWebModule(moduleName = "WAWebLinkPreviewCache")
public final class LinkPreviewCache {
    /**
     * The sentinel value stored when a URL was resolved but produced
     * no preview.
     *
     * @implNote
     * This implementation uses a fresh empty
     * {@link ExtendedTextMessage} so identity comparison
     * (via {@link #isNegative(ExtendedTextMessage)}) distinguishes the
     * sentinel from any caller-supplied value.
     */
    private static final ExtendedTextMessage NEGATIVE = new ExtendedTextMessageBuilder().build();

    /**
     * The cache for non-newsletter chats.
     *
     * @apiNote
     * Looked up when {@link #get(String, boolean)} or
     * {@link #put(String, boolean, ExtendedTextMessage)} is called with
     * {@code newsletterChat == false}.
     */
    private final ConcurrentMap<String, ExtendedTextMessage> regular;

    /**
     * The cache for newsletter chats.
     *
     * @apiNote
     * Looked up when {@link #get(String, boolean)} or
     * {@link #put(String, boolean, ExtendedTextMessage)} is called with
     * {@code newsletterChat == true}.
     */
    private final ConcurrentMap<String, ExtendedTextMessage> newsletter;

    /**
     * Creates a fresh cache pair.
     *
     * @apiNote
     * Invoked once per {@link com.github.auties00.cobalt.media.transcode.text.TextPipeline} instance; the cache
     * is not shared across sessions.
     */
    public LinkPreviewCache() {
        this.regular = new ConcurrentHashMap<>();
        this.newsletter = new ConcurrentHashMap<>();
    }

    /**
     * Returns the cached preview for {@code url} when one is available.
     *
     * @apiNote
     * Called from {@link com.github.auties00.cobalt.media.transcode.text.TextPipeline#run} on every outgoing
     * URL to skip the rich fetch / MEX round-trip when the same URL
     * has already been resolved in this session. The presence of a
     * value indicates the URL has been resolved at least once; a
     * cached negative sentinel is returned unchanged so the caller can
     * detect "resolved but produced no preview" and bypass attaching.
     *
     * @param url            the URL whose preview is requested
     * @param newsletterChat whether the lookup is for a newsletter chat
     * @return the cached preview, or {@link Optional#empty()} when the
     *         URL has not been resolved yet
     */
    @WhatsAppWebExport(moduleName = "WAWebLinkPreviewCache", exports = "getPreviewCache",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebLinkPreviewCache", exports = "getNewsletterPreviewCache",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Optional<ExtendedTextMessage> get(String url, boolean newsletterChat) {
        var pick = newsletterChat ? newsletter : regular;
        var cached = pick.get(url);
        if (cached == null) {
            return Optional.empty();
        }
        if (cached == NEGATIVE) {
            return Optional.of(NEGATIVE);
        }
        return Optional.of(cached);
    }

    /**
     * Returns whether {@code preview} is the negative sentinel stored
     * by {@link #put(String, boolean, ExtendedTextMessage)} for URLs
     * that resolved without producing a card.
     *
     * @apiNote
     * Called by {@link com.github.auties00.cobalt.media.transcode.text.TextPipeline#run} on the value
     * returned from {@link #get(String, boolean)} to distinguish
     * "resolved but produced no preview" from "resolved successfully".
     *
     * @param preview the preview returned from {@link #get}
     * @return {@code true} when {@code preview} is the negative
     *         sentinel
     */
    public static boolean isNegative(ExtendedTextMessage preview) {
        return preview == NEGATIVE;
    }

    /**
     * Stores {@code preview} as the resolved value for {@code url}.
     *
     * @apiNote
     * Called by {@link com.github.auties00.cobalt.media.transcode.text.TextPipeline#run} once the resolver
     * has answered. A {@code null} preview is stored as the negative
     * sentinel so subsequent lookups short-circuit instead of issuing
     * the same network round-trip again.
     *
     * @param url            the URL being cached
     * @param newsletterChat whether the resolution was for a
     *                       newsletter chat
     * @param preview        the preview to cache; {@code null} stores
     *                       the negative sentinel
     */
    public void put(String url, boolean newsletterChat, ExtendedTextMessage preview) {
        var pick = newsletterChat ? newsletter : regular;
        pick.put(url, preview != null ? preview : NEGATIVE);
    }

    /**
     * Clears every cached entry across both stores.
     *
     * @apiNote
     * Mirrors the {@code clearPreviewCache} /
     * {@code clearNewsletterPreviewCache} exports; invoked when the
     * user opts out of link previews or when the owning session is
     * recycled.
     */
    @WhatsAppWebExport(moduleName = "WAWebLinkPreviewCache", exports = "clearPreviewCache",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebLinkPreviewCache", exports = "clearNewsletterPreviewCache",
            adaptation = WhatsAppAdaptation.DIRECT)
    void clear() {
        regular.clear();
        newsletter.clear();
    }
}
