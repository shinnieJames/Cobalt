package com.github.auties00.cobalt.node.smax.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code <config/>} child of the CTWA banner-suggestion {@code <banner/>},
 * carrying the banner lifecycle attributes (expiry, visual style, revocation
 * marker).
 *
 * @apiNote
 * Consumed by {@code WAWebCTWAParseSuggestion.parseCTWASuggestion} to
 * drive the WhatsApp Business "suggested banner" panel: the parser
 * short-circuits to a {@code "revokedBanner"} result when {@link #revoked()}
 * is {@link SmaxBannerSuggestionFalseTrueFlag#TRUE}, otherwise it forwards
 * {@link #expiresAt()} (cast to a unix timestamp) and {@link #display()}
 * to the banner-view component.
 */
@WhatsAppWebModule(moduleName = "WASmaxInBizCtwaActionBannerSuggestionRequest")
public final class SmaxBannerSuggestionConfig {
    /**
     * The {@code expires_at} attribute in epoch seconds (validated as
     * &gt;= 1).
     */
    private final long expiresAt;

    /**
     * The {@code display} attribute (info or warning).
     */
    private final SmaxBannerSuggestionBannerDisplay display;

    /**
     * The {@code revoked} attribute (false when the banner is still
     * active).
     */
    private final SmaxBannerSuggestionFalseTrueFlag revoked;

    /**
     * Constructs a projection from already-validated wire values.
     *
     * @apiNote
     * Cobalt callers normally obtain a projection by parsing a node via
     * {@link #of(Node)}; this constructor is exposed for tests and for
     * hand-built fixtures.
     *
     * @param expiresAt the expiration timestamp in epoch seconds; must be &gt;= 1
     * @param display   the {@link SmaxBannerSuggestionBannerDisplay} style; never {@code null}
     * @param revoked   the {@link SmaxBannerSuggestionFalseTrueFlag} revocation marker; never {@code null}
     * @throws NullPointerException     if {@code display} or {@code revoked} is {@code null}
     * @throws IllegalArgumentException if {@code expiresAt < 1}
     */
    public SmaxBannerSuggestionConfig(long expiresAt, SmaxBannerSuggestionBannerDisplay display, SmaxBannerSuggestionFalseTrueFlag revoked) {
        if (expiresAt < 1) {
            throw new IllegalArgumentException("expiresAt must be >= 1");
        }
        this.expiresAt = expiresAt;
        this.display = Objects.requireNonNull(display, "display cannot be null");
        this.revoked = Objects.requireNonNull(revoked, "revoked cannot be null");
    }

    /**
     * Returns the expiration timestamp.
     *
     * @apiNote
     * Epoch seconds; WA Web feeds this directly into
     * {@code WATimeUtils.castToUnixTime} when synthesising the
     * banner-view value object.
     *
     * @return the timestamp in epoch seconds
     */
    public long expiresAt() {
        return expiresAt;
    }

    /**
     * Returns the visual style.
     *
     * @apiNote
     * Selects between informational and warning-styled chrome for the
     * banner panel.
     *
     * @return the style; never {@code null}
     */
    public SmaxBannerSuggestionBannerDisplay display() {
        return display;
    }

    /**
     * Returns the revocation marker.
     *
     * @apiNote
     * {@link SmaxBannerSuggestionFalseTrueFlag#TRUE} short-circuits the
     * caller into the "revoked" branch which dismisses the banner by ID
     * without rendering any copy.
     *
     * @return the marker; never {@code null}
     */
    public SmaxBannerSuggestionFalseTrueFlag revoked() {
        return revoked;
    }

    /**
     * Parses the projection from a {@code <config/>} node.
     *
     * @apiNote
     * Returns empty when the node tag is wrong, when {@code expires_at}
     * is missing or below the {@code 1} floor enforced by WA Web's
     * {@code attrIntRange(..., 1, undefined)} guard, or when either of
     * the two literal-tuple attributes fails validation.
     *
     * @implNote
     * This implementation matches the WA Web bound exactly: {@code expires_at}
     * is validated as {@code >= 1} (any value below the floor yields empty
     * even though the underlying type is a {@code long}). The {@code display}
     * and {@code revoked} attributes are case-sensitive dictionary lookups
     * against {@link SmaxBannerSuggestionBannerDisplay} and
     * {@link SmaxBannerSuggestionFalseTrueFlag} respectively.
     *
     * @param node the candidate {@code <config/>} node; never {@code null}
     * @return an {@link Optional} carrying the projection, or empty when
     *         parsing fails at any step
     * @throws NullPointerException if {@code node} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxInBizCtwaActionBannerSuggestionRequest",
            exports = "parseBannerSuggestionRequestCtwaSuggestionBanner",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<SmaxBannerSuggestionConfig> of(Node node) {
        Objects.requireNonNull(node, "node cannot be null");
        if (!node.hasDescription("config")) {
            return Optional.empty();
        }
        var expiresAt = node.getAttributeAsLong("expires_at");
        if (expiresAt.isEmpty() || expiresAt.getAsLong() < 1) {
            return Optional.empty();
        }
        var displayStr = node.getAttributeAsString("display").orElse(null);
        var display = SmaxBannerSuggestionBannerDisplay.of(displayStr).orElse(null);
        if (display == null) {
            return Optional.empty();
        }
        var revokedStr = node.getAttributeAsString("revoked").orElse(null);
        var revoked = SmaxBannerSuggestionFalseTrueFlag.of(revokedStr).orElse(null);
        if (revoked == null) {
            return Optional.empty();
        }
        return Optional.of(new SmaxBannerSuggestionConfig(expiresAt.getAsLong(), display, revoked));
    }

    /**
     * Compares this projection to {@code obj} for structural equality on
     * the three wire attributes.
     *
     * @param obj the candidate; may be {@code null}
     * @return {@code true} when {@code obj} is a {@link SmaxBannerSuggestionConfig}
     *         with matching {@link #expiresAt()}, {@link #display()}, and
     *         {@link #revoked()}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxBannerSuggestionConfig) obj;
        return this.expiresAt == that.expiresAt
                && this.display == that.display
                && this.revoked == that.revoked;
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return the hash of the three wire attributes
     */
    @Override
    public int hashCode() {
        return Objects.hash(expiresAt, display, revoked);
    }

    /**
     * Returns a debug-friendly rendering naming the three wire attributes.
     *
     * @return a record-style string with the three attribute values
     */
    @Override
    public String toString() {
        return "SmaxBannerSuggestionConfig[expiresAt=" + expiresAt
                + ", display=" + display
                + ", revoked=" + revoked + ']';
    }
}
