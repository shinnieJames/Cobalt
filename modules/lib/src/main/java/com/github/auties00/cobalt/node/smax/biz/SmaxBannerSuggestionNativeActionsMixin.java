package com.github.auties00.cobalt.node.smax.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code <native_actions_mixin/>} projection, gathering the 0..50
 * {@link SmaxBannerSuggestionNativeAction} entries harvested from a parent
 * node's {@code <native_action/>} children.
 *
 * @apiNote
 * Mixed into the CTWA banner-suggestion {@link SmaxBannerSuggestionBanner}
 * to deliver the per-platform deep-link list; the mixin exists so the
 * 0..50 cardinality bound is enforced once at this level instead of
 * scattered across every consumer.
 *
 * @implNote
 * This implementation matches WA Web's
 * {@code mapChildrenWithTag(t, "native_action", 0, 50, e)} ordering: the
 * cardinality check fires before any individual
 * {@link SmaxBannerSuggestionNativeAction#of(Node)} call, so a
 * relay-side over-quota notification is rejected without ever touching
 * the individual entries.
 */
@WhatsAppWebModule(moduleName = "WASmaxInBizCtwaActionNativeActionsMixinMixin")
public final class SmaxBannerSuggestionNativeActionsMixin {
    /**
     * The harvested {@code <native_action/>} entries (0..50).
     */
    private final List<SmaxBannerSuggestionNativeAction> nativeAction;

    /**
     * Constructs a projection from already-validated entries.
     *
     * @apiNote
     * Cobalt callers normally obtain a projection by parsing a parent
     * node via {@link #of(Node)}; this constructor is exposed for tests
     * and for hand-built fixtures.
     *
     * @param nativeAction the native-action entries; may be {@code null} (treated as empty)
     */
    public SmaxBannerSuggestionNativeActionsMixin(List<SmaxBannerSuggestionNativeAction> nativeAction) {
        this.nativeAction = nativeAction == null ? List.of() : List.copyOf(nativeAction);
    }

    /**
     * Returns the harvested entries.
     *
     * @apiNote
     * Bounded to 0..50 by the parser; consumers filter by
     * {@link SmaxBannerSuggestionNativeAction#platform()} to pick the
     * entry matching their runtime.
     *
     * @return an unmodifiable list of 0..50 entries; never {@code null}
     */
    public List<SmaxBannerSuggestionNativeAction> nativeAction() {
        return nativeAction;
    }

    /**
     * Parses the projection from a parent node by harvesting its
     * {@code <native_action/>} children.
     *
     * @apiNote
     * Returns empty when the {@code <native_action/>} cardinality
     * exceeds the 0..50 bound or when any individual entry fails to
     * parse. A parent with zero {@code <native_action/>} children
     * succeeds and yields an empty list.
     *
     * @implNote
     * This implementation enforces the upper cardinality bound BEFORE
     * walking individual children, matching WA Web's
     * {@code mapChildrenWithTag(t, "native_action", 0, 50, e)} semantics
     * exactly so a malformed over-quota stanza is rejected without ever
     * delegating to {@link SmaxBannerSuggestionNativeAction#of(Node)}.
     *
     * @param node the parent node hosting the {@code <native_action/>} children; never {@code null}
     * @return an {@link Optional} carrying the projection, or empty when
     *         parsing fails
     * @throws NullPointerException if {@code node} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxInBizCtwaActionNativeActionsMixinMixin",
            exports = "parseNativeActionsMixinMixin",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<SmaxBannerSuggestionNativeActionsMixin> of(Node node) {
        Objects.requireNonNull(node, "node cannot be null");
        var nativeActionNodes = node.streamChildren("native_action").toList();
        if (nativeActionNodes.size() > 50) {
            return Optional.empty();
        }
        var nativeActions = new ArrayList<SmaxBannerSuggestionNativeAction>(nativeActionNodes.size());
        for (var nativeActionNode : nativeActionNodes) {
            var parsed = SmaxBannerSuggestionNativeAction.of(nativeActionNode);
            if (parsed.isEmpty()) {
                return Optional.empty();
            }
            nativeActions.add(parsed.get());
        }
        return Optional.of(new SmaxBannerSuggestionNativeActionsMixin(nativeActions));
    }

    /**
     * Compares this projection to {@code obj} for structural equality on
     * the harvested entries.
     *
     * @param obj the candidate; may be {@code null}
     * @return {@code true} when {@code obj} is a {@link SmaxBannerSuggestionNativeActionsMixin}
     *         with matching {@link #nativeAction()}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxBannerSuggestionNativeActionsMixin) obj;
        return Objects.equals(this.nativeAction, that.nativeAction);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return the hash of the harvested entries
     */
    @Override
    public int hashCode() {
        return Objects.hash(nativeAction);
    }

    /**
     * Returns a debug-friendly rendering naming the harvested entries.
     *
     * @return a record-style string with the entries list
     */
    @Override
    public String toString() {
        return "SmaxBannerSuggestionNativeActionsMixin[nativeAction=" + nativeAction + ']';
    }
}
