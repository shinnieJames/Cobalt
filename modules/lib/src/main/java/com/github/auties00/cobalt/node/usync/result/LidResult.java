package com.github.auties00.cobalt.node.usync.result;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.jid.Jid;

import java.util.Optional;

/**
 * Success result of the {@code WAWebUsyncLid.lidParser} parser.
 *
 * @apiNote
 * Surfaced by USync queries that include
 * {@code UsyncQuery.withLidProtocol()}; WA Web callers include the background
 * contact sync, {@code WAWebContactSyncUtils} delta sync,
 * {@code WAWebQueryExistsJob}, and the contact-import verifier. The result
 * carries the LID alias resolved for the peer's phone-number JID; absent
 * when the peer has not been migrated to LID yet.
 *
 * @implNote
 * This implementation models the JS parser's return type as a nullable
 * {@link Jid}: WA Web returns the bare {@code val} attribute when present and
 * {@code undefined} otherwise. The Cobalt {@link Optional}-typed accessor
 * preserves the same tristate (success-with-lid, success-without-lid, error)
 * because the error variant is hoisted into {@link UsyncProtocolError}.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncLid")
public final class LidResult implements UsyncProtocolResponse {
    /**
     * The resolved LID, or {@code null} when the relay omitted the
     * {@code val} attribute.
     */
    private final Jid lid;

    /**
     * Creates a new LID result.
     *
     * @apiNote
     * Instantiated by the LID parser; embedders do not call this directly.
     *
     * @param lid the resolved LID, or {@code null}
     */
    public LidResult(Jid lid) {
        this.lid = lid;
    }

    /**
     * Returns the resolved LID, when present.
     *
     * @apiNote
     * Empty when the peer has not been migrated to LID yet; the LID-based
     * contact sync uses that condition to keep the local mapping pinned to
     * the phone-number JID.
     *
     * @return the LID, or empty when absent
     */
    public Optional<Jid> lid() {
        return Optional.ofNullable(lid);
    }
}
