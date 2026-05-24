package com.github.auties00.cobalt.node.iq.privacy;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.iq.IqOperation;
import java.util.Objects;

/**
 * Outbound legacy {@code <iq xmlns="privacy" type="set"><privacy><category name="readreceipts" value="all|none"/></privacy></iq>}
 * stanza that toggles the user's read-receipts visibility.
 *
 * @apiNote
 * Cobalt embedders dispatch this when the user flips the read-receipts switch in the Settings UI;
 * it is the single-row dedicated counterpart of the multi-row {@link IqSetPrivacyRequest} and
 * always targets the {@link IqQueryPrivacySettingsCategoryName#READ_RECEIPTS} category. Disabling
 * read receipts also suppresses outbound delivery notifications for the user (the relay drops
 * receipts on send when the value is {@code "none"}).
 *
 * @implNote
 * This implementation maps directly to WA Web's
 * {@code WAWebSetReadReceiptJob}'s default export, which always emits the
 * {@code readreceipts} category and toggles between {@code "all"} and {@code "none"}; no other
 * category is reachable through this stanza.
 */
@WhatsAppWebModule(moduleName = "WAWebSetReadReceiptJob")
public final class IqSetReadReceiptRequest implements IqOperation.Request {
    /**
     * The new toggle state; {@code true} serialises to the wire value {@code "all"} and
     * {@code false} to {@code "none"}.
     */
    private final boolean enabled;

    /**
     * Constructs a new request.
     *
     * @apiNote
     * Pass {@code true} to enable read receipts (the relay accepts and replays them to peers) or
     * {@code false} to disable them (the relay drops outbound receipts and the peer's UI no
     * longer shows the double-tick).
     *
     * @param enabled {@code true} to enable read receipts, {@code false} to disable
     */
    public IqSetReadReceiptRequest(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the requested toggle state.
     *
     * @return {@code true} when read receipts are being enabled, {@code false} when disabled
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation wraps a single {@code <category name="readreceipts" value="all|none"/>}
     * marker in a {@code <privacy>} envelope inside the canonical
     * {@code <iq xmlns="privacy" to="s.whatsapp.net" type="set">} stanza; the value attribute is
     * selected by {@link #enabled} as {@code "all"} / {@code "none"} matching WA Web's
     * {@code t?"all":"none"} branch.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSetReadReceiptJob",
            exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var value = enabled ? "all" : "none";
        var categoryNode = new NodeBuilder()
                .description("category")
                .attribute("name", "readreceipts")
                .attribute("value", value)
                .build();
        var privacyNode = new NodeBuilder()
                .description("privacy")
                .content(categoryNode)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "privacy")
                .attribute("to", JidServer.user())
                .attribute("type", "set")
                .content(privacyNode);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation compares the toggle state by value.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (IqSetReadReceiptRequest) obj;
        return this.enabled == that.enabled;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation hashes the toggle state consistently with {@link #equals(Object)}.
     */
    @Override
    public int hashCode() {
        return Objects.hash(enabled);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation emits a debug-only representation; the format is not stable and must
     * not be parsed.
     */
    @Override
    public String toString() {
        return "IqSetReadReceiptRequest[enabled=" + enabled + ']';
    }
}
