package com.github.auties00.cobalt.node.smax.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import java.util.Objects;
import java.util.Optional;

/**
 * The optional {@code <action/>} sub-element of the CTWA banner-suggestion
 * payload, carrying the cross-platform plus per-platform-fallback deep-link
 * triple.
 *
 * @apiNote
 * Surfaces the deep-link the banner CTA navigates to when the user taps
 * it from the WhatsApp Business surface produced by
 * {@code WAWebCTWAParseSuggestion.parseCTWASuggestion}. All three URLs
 * are optional; per-platform link selection is performed by the consumer
 * (WA Web prefers a {@code platform="web"} {@code <native_action/>} entry
 * and falls back to the cross-platform {@code deep_link} value here).
 *
 * @implNote
 * This implementation models the action as a flat triple of optional
 * strings; WA Web's parser is equally lax and never rejects on a missing
 * URL.
 */
@WhatsAppWebModule(moduleName = "WASmaxInBizCtwaActionBannerSuggestionRequest")
public final class SmaxBannerSuggestionAction {
    /**
     * The optional cross-platform deep-link URL.
     */
    private final String deepLink;

    /**
     * The optional iOS-flavoured local deep-link URL.
     */
    private final String localLink;

    /**
     * The optional Android-flavoured local deep-link URL.
     */
    private final String localAndroidLink;

    /**
     * Constructs a projection from already-validated attribute strings.
     *
     * @apiNote
     * Cobalt callers normally obtain a projection by parsing a node via
     * {@link #of(Node)}; this constructor is exposed for tests and for
     * hand-built fixtures.
     *
     * @param deepLink         the optional cross-platform deep link; may be {@code null}
     * @param localLink        the optional iOS-local deep link; may be {@code null}
     * @param localAndroidLink the optional Android-local deep link; may be {@code null}
     */
    public SmaxBannerSuggestionAction(String deepLink, String localLink, String localAndroidLink) {
        this.deepLink = deepLink;
        this.localLink = localLink;
        this.localAndroidLink = localAndroidLink;
    }

    /**
     * Returns the optional cross-platform deep link.
     *
     * @apiNote
     * Used as the platform-agnostic fallback when no
     * {@link SmaxBannerSuggestionNativeAction native action} matches the
     * client's platform.
     *
     * @return an {@link Optional} carrying the URL, or empty when the relay
     *         omitted the {@code deep_link} attribute
     */
    public Optional<String> deepLink() {
        return Optional.ofNullable(deepLink);
    }

    /**
     * Returns the optional iOS-local deep link.
     *
     * @apiNote
     * iOS-specific override carried for parity with WA Mobile clients;
     * Cobalt does not consume this on the Web surface.
     *
     * @return an {@link Optional} carrying the URL, or empty when the relay
     *         omitted the {@code local_link} attribute
     */
    public Optional<String> localLink() {
        return Optional.ofNullable(localLink);
    }

    /**
     * Returns the optional Android-local deep link.
     *
     * @apiNote
     * Android-specific override carried for parity with WA Mobile clients;
     * Cobalt does not consume this on the Web surface.
     *
     * @return an {@link Optional} carrying the URL, or empty when the relay
     *         omitted the {@code local_android_link} attribute
     */
    public Optional<String> localAndroidLink() {
        return Optional.ofNullable(localAndroidLink);
    }

    /**
     * Parses the projection from an {@code <action/>} node.
     *
     * @apiNote
     * Returns empty when the node tag is not {@code "action"}. All three
     * attributes are optional and any combination of present/absent values
     * is accepted; a node bearing no attributes still parses cleanly.
     *
     * @implNote
     * This implementation mirrors WA Web's
     * {@code assertTag + optional(attrString, ...)} chain exactly. A
     * wrong tag short-circuits to empty before any attribute lookup.
     *
     * @param node the candidate {@code <action/>} node; never {@code null}
     * @return an {@link Optional} carrying the projection, or empty when the
     *         tag does not match
     * @throws NullPointerException if {@code node} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxInBizCtwaActionBannerSuggestionRequest",
            exports = "parseBannerSuggestionRequestCtwaSuggestionBannerAction",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<SmaxBannerSuggestionAction> of(Node node) {
        Objects.requireNonNull(node, "node cannot be null");
        if (!node.hasDescription("action")) {
            return Optional.empty();
        }
        var deepLink = node.getAttributeAsString("deep_link").orElse(null);
        var localLink = node.getAttributeAsString("local_link").orElse(null);
        var localAndroidLink = node.getAttributeAsString("local_android_link").orElse(null);
        return Optional.of(new SmaxBannerSuggestionAction(deepLink, localLink, localAndroidLink));
    }

    /**
     * Compares this projection to {@code obj} for structural equality on
     * all three optional URL strings.
     *
     * @param obj the candidate; may be {@code null}
     * @return {@code true} when {@code obj} is a {@link SmaxBannerSuggestionAction}
     *         with matching {@link #deepLink()}, {@link #localLink()}, and
     *         {@link #localAndroidLink()}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxBannerSuggestionAction) obj;
        return Objects.equals(this.deepLink, that.deepLink)
                && Objects.equals(this.localLink, that.localLink)
                && Objects.equals(this.localAndroidLink, that.localAndroidLink);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return the hash of all three optional URL strings
     */
    @Override
    public int hashCode() {
        return Objects.hash(deepLink, localLink, localAndroidLink);
    }

    /**
     * Returns a debug-friendly rendering naming all three URL slots.
     *
     * @return a record-style string with the three URL values
     */
    @Override
    public String toString() {
        return "SmaxBannerSuggestionAction[deepLink=" + deepLink
                + ", localLink=" + localLink
                + ", localAndroidLink=" + localAndroidLink + ']';
    }
}
