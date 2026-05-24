package com.github.auties00.cobalt.node.smax.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.node.Node;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code <content/>} child of the CTWA banner-suggestion {@code <banner/>},
 * carrying the banner copy (locale plus heading, body, highlight) and the
 * three optional localised parallels.
 *
 * @apiNote
 * Drives the headline, body, and highlight text rendered by the WhatsApp
 * Business "suggested banner" panel. WA Web's
 * {@code WAWebCTWAParseSuggestion.parseCTWASuggestion} forwards
 * {@link #heading()}, {@link #body()}, {@link #highlight()}, and
 * {@link #locale()} straight to the banner-view value object; the three
 * {@code localised_*} parallels are passed to translation telemetry by
 * downstream consumers.
 */
@WhatsAppWebModule(moduleName = "WASmaxInBizCtwaActionBannerSuggestionRequest")
public final class SmaxBannerSuggestionContent {
    /**
     * The mandatory {@code locale} attribute.
     */
    private final String locale;

    /**
     * The text content of the mandatory {@code <heading/>} child.
     */
    private final String heading;

    /**
     * The text content of the mandatory {@code <body/>} child.
     */
    private final String body;

    /**
     * The text content of the mandatory {@code <highlight/>} child.
     */
    private final String highlight;

    /**
     * The optional {@code <localised_heading/>} parallel.
     */
    private final SmaxBannerSuggestionLocalisedString localisedHeading;

    /**
     * The optional {@code <localised_body/>} parallel.
     */
    private final SmaxBannerSuggestionLocalisedString localisedBody;

    /**
     * The optional {@code <localised_highlight/>} parallel.
     */
    private final SmaxBannerSuggestionLocalisedString localisedHighlight;

    /**
     * Constructs a projection from already-validated wire values.
     *
     * @apiNote
     * Cobalt callers normally obtain a projection by parsing a node via
     * {@link #of(Node)}; this constructor is exposed for tests and for
     * hand-built fixtures.
     *
     * @param locale             the locale identifier; never {@code null}
     * @param heading            the heading copy; never {@code null}
     * @param body               the body copy; never {@code null}
     * @param highlight          the highlight copy; never {@code null}
     * @param localisedHeading   the optional {@code <localised_heading/>} parallel; may be {@code null}
     * @param localisedBody      the optional {@code <localised_body/>} parallel; may be {@code null}
     * @param localisedHighlight the optional {@code <localised_highlight/>} parallel; may be {@code null}
     * @throws NullPointerException if any of the four mandatory arguments is {@code null}
     */
    public SmaxBannerSuggestionContent(String locale, String heading, String body, String highlight,
                   SmaxBannerSuggestionLocalisedString localisedHeading, SmaxBannerSuggestionLocalisedString localisedBody,
                   SmaxBannerSuggestionLocalisedString localisedHighlight) {
        this.locale = Objects.requireNonNull(locale, "locale cannot be null");
        this.heading = Objects.requireNonNull(heading, "heading cannot be null");
        this.body = Objects.requireNonNull(body, "body cannot be null");
        this.highlight = Objects.requireNonNull(highlight, "highlight cannot be null");
        this.localisedHeading = localisedHeading;
        this.localisedBody = localisedBody;
        this.localisedHighlight = localisedHighlight;
    }

    /**
     * Returns the locale identifier.
     *
     * @apiNote
     * Forwarded to the banner-view component as the {@code bannerLocale}
     * field; downstream CTWA understand-banner telemetry compares it to
     * the client locale to flag translation mismatches.
     *
     * @return the locale; never {@code null}
     */
    public String locale() {
        return locale;
    }

    /**
     * Returns the heading copy.
     *
     * @apiNote
     * Rendered as the bold heading line of the banner panel.
     *
     * @return the heading text; never {@code null}
     */
    public String heading() {
        return heading;
    }

    /**
     * Returns the body copy.
     *
     * @apiNote
     * Rendered as the main paragraph below the heading.
     *
     * @return the body text; never {@code null}
     */
    public String body() {
        return body;
    }

    /**
     * Returns the highlight copy.
     *
     * @apiNote
     * Rendered as the accented call-to-action snippet inside the body.
     *
     * @return the highlight text; never {@code null}
     */
    public String highlight() {
        return highlight;
    }

    /**
     * Returns the optional {@code <localised_heading/>} parallel.
     *
     * @apiNote
     * Carries the heading copy with full
     * {@link SmaxBannerSuggestionLocalisationMetadata translation metadata}
     * (translation uid plus parameters). Empty when the relay omitted
     * the parallel.
     *
     * @return an {@link Optional} carrying the parallel, or empty
     */
    public Optional<SmaxBannerSuggestionLocalisedString> localisedHeading() {
        return Optional.ofNullable(localisedHeading);
    }

    /**
     * Returns the optional {@code <localised_body/>} parallel.
     *
     * @apiNote
     * Carries the body copy with full
     * {@link SmaxBannerSuggestionLocalisationMetadata translation metadata}.
     * Empty when the relay omitted the parallel.
     *
     * @return an {@link Optional} carrying the parallel, or empty
     */
    public Optional<SmaxBannerSuggestionLocalisedString> localisedBody() {
        return Optional.ofNullable(localisedBody);
    }

    /**
     * Returns the optional {@code <localised_highlight/>} parallel.
     *
     * @apiNote
     * Carries the highlight copy with full
     * {@link SmaxBannerSuggestionLocalisationMetadata translation metadata}.
     * Empty when the relay omitted the parallel.
     *
     * @return an {@link Optional} carrying the parallel, or empty
     */
    public Optional<SmaxBannerSuggestionLocalisedString> localisedHighlight() {
        return Optional.ofNullable(localisedHighlight);
    }

    /**
     * Parses the projection from a {@code <content/>} node.
     *
     * @apiNote
     * Returns empty when the node tag is wrong, when any mandatory copy
     * element ({@code locale}, {@code <heading>}, {@code <body>},
     * {@code <highlight>}) is missing or empty-content, or when an
     * optional {@code <localised_*>} child is present but fails parsing.
     *
     * @implNote
     * This implementation walks the children in WA Web's documented
     * order; each {@code <localised_*>} parallel is delegated to
     * {@link SmaxBannerSuggestionLocalisedString#of(Node, String)} with
     * the matching expected tag so a mis-shaped parallel aborts the
     * whole content parse (matching the WA Web error propagation).
     *
     * @param node the candidate {@code <content/>} node; never {@code null}
     * @return an {@link Optional} carrying the projection, or empty when
     *         parsing fails at any step
     * @throws NullPointerException if {@code node} is {@code null}
     */
    public static Optional<SmaxBannerSuggestionContent> of(Node node) {
        Objects.requireNonNull(node, "node cannot be null");
        if (!node.hasDescription("content")) {
            return Optional.empty();
        }
        var locale = node.getAttributeAsString("locale").orElse(null);
        if (locale == null) {
            return Optional.empty();
        }
        var headingNode = node.getChild("heading").orElse(null);
        if (headingNode == null) {
            return Optional.empty();
        }
        var heading = headingNode.toContentString().orElse(null);
        if (heading == null) {
            return Optional.empty();
        }
        var bodyNode = node.getChild("body").orElse(null);
        if (bodyNode == null) {
            return Optional.empty();
        }
        var body = bodyNode.toContentString().orElse(null);
        if (body == null) {
            return Optional.empty();
        }
        var highlightNode = node.getChild("highlight").orElse(null);
        if (highlightNode == null) {
            return Optional.empty();
        }
        var highlight = highlightNode.toContentString().orElse(null);
        if (highlight == null) {
            return Optional.empty();
        }
        SmaxBannerSuggestionLocalisedString localisedHeading = null;
        var localisedHeadingNode = node.getChild("localised_heading").orElse(null);
        if (localisedHeadingNode != null) {
            var parsed = SmaxBannerSuggestionLocalisedString.of(localisedHeadingNode, "localised_heading");
            if (parsed.isEmpty()) {
                return Optional.empty();
            }
            localisedHeading = parsed.get();
        }
        SmaxBannerSuggestionLocalisedString localisedBody = null;
        var localisedBodyNode = node.getChild("localised_body").orElse(null);
        if (localisedBodyNode != null) {
            var parsed = SmaxBannerSuggestionLocalisedString.of(localisedBodyNode, "localised_body");
            if (parsed.isEmpty()) {
                return Optional.empty();
            }
            localisedBody = parsed.get();
        }
        SmaxBannerSuggestionLocalisedString localisedHighlight = null;
        var localisedHighlightNode = node.getChild("localised_highlight").orElse(null);
        if (localisedHighlightNode != null) {
            var parsed = SmaxBannerSuggestionLocalisedString.of(localisedHighlightNode, "localised_highlight");
            if (parsed.isEmpty()) {
                return Optional.empty();
            }
            localisedHighlight = parsed.get();
        }
        return Optional.of(new SmaxBannerSuggestionContent(locale, heading, body, highlight,
                localisedHeading, localisedBody, localisedHighlight));
    }

    /**
     * Compares this projection to {@code obj} for structural equality on
     * all seven slots.
     *
     * @param obj the candidate; may be {@code null}
     * @return {@code true} when {@code obj} is a {@link SmaxBannerSuggestionContent}
     *         with matching {@link #locale()}, mandatory copy fields,
     *         and the three localised parallels
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxBannerSuggestionContent) obj;
        return Objects.equals(this.locale, that.locale)
                && Objects.equals(this.heading, that.heading)
                && Objects.equals(this.body, that.body)
                && Objects.equals(this.highlight, that.highlight)
                && Objects.equals(this.localisedHeading, that.localisedHeading)
                && Objects.equals(this.localisedBody, that.localisedBody)
                && Objects.equals(this.localisedHighlight, that.localisedHighlight);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return the hash of all seven slots
     */
    @Override
    public int hashCode() {
        return Objects.hash(locale, heading, body, highlight,
                localisedHeading, localisedBody, localisedHighlight);
    }

    /**
     * Returns a debug-friendly rendering naming all seven slots.
     *
     * @return a record-style string with the seven slot values
     */
    @Override
    public String toString() {
        return "SmaxBannerSuggestionContent[locale=" + locale
                + ", heading=" + heading
                + ", body=" + body
                + ", highlight=" + highlight
                + ", localisedHeading=" + localisedHeading
                + ", localisedBody=" + localisedBody
                + ", localisedHighlight=" + localisedHighlight + ']';
    }
}
