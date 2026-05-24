package com.github.auties00.cobalt.node.iq.privacy;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

/**
 * Per-user action enum carried by each {@link IqSetPrivacyUserEntry} on the user-list payload of an
 * {@link IqSetPrivacyRequest}.
 *
 * @apiNote
 * Use {@link #ADD} when extending the per-category exclusion list (e.g. blocking someone from
 * seeing the user's last-seen) and {@link #REMOVE} when retracting a previous block. The relay
 * applies the mutations in-place and echoes the new {@link IqSetPrivacyResponse.CategoryOutcome}
 * back.
 *
 * @implNote
 * This implementation mirrors the {@code PrivacyUserAction} enum exported by
 * {@code WAWebSetPrivacyJob} (values {@code "add"} / {@code "remove"}).
 */
@WhatsAppWebModule(moduleName = "WAWebSetPrivacyJob")
public enum IqSetPrivacyUserAction {
    /**
     * The {@code add} action.
     *
     * @apiNote
     * Adds the user to the per-category exclusion list.
     */
    ADD("add"),

    /**
     * The {@code remove} action.
     *
     * @apiNote
     * Removes the user from the per-category exclusion list.
     */
    REMOVE("remove");

    /**
     * The wire token emitted in the {@code action} attribute of each {@code <user>} child.
     */
    private final String wire;

    /**
     * Constructs a user-action constant from its wire token.
     *
     * @apiNote
     * Constructor is package-private as enum constants are the only producers.
     *
     * @param wire the wire token; never {@code null}
     */
    IqSetPrivacyUserAction(String wire) {
        this.wire = wire;
    }

    /**
     * Returns the wire token for this action.
     *
     * @apiNote
     * Use when serialising a {@code <user action=...>} attribute on an
     * {@link IqSetPrivacyUserEntry}.
     *
     * @return the wire token; never {@code null}
     */
    public String wire() {
        return wire;
    }
}
