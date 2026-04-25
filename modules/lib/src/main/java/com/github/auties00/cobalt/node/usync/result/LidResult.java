package com.github.auties00.cobalt.node.usync.result;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.jid.Jid;

import java.util.Optional;

/**
 * Success result of {@code WAWebUsyncLid.lidParser}.
 *
 * @implNote WAWebUsyncLid.lidParser: returns
 *     {@code e.maybeAttrLidUserJid("val")}.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncLid")
public final class LidResult implements UsyncProtocolResponse {
    /**
     * The resolved LID, or {@code null} when the relay omitted the
     * {@code val} attribute (e.g. the peer has not yet been migrated to
     * LID).
     */
    private final Jid lid;

    /**
     * Creates a new LID result.
     *
     * @param lid the resolved LID, or {@code null}
     */
    public LidResult(Jid lid) {
        this.lid = lid;
    }

    /**
     * Returns the resolved LID, when present.
     *
     * @return the LID
     */
    public Optional<Jid> lid() {
        return Optional.ofNullable(lid);
    }
}
