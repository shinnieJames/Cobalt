package com.github.auties00.cobalt.node.iq.usync;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

/**
 * Enumerates the usync {@code mode} attribute values that drive the relay's per-protocol
 * caching strategy.
 *
 * @apiNote
 * Pick a value when building an {@link IqUsyncRequest}: {@link #QUERY} fetches the
 * current per-protocol state, {@link #DELTA} fetches only the entries that have
 * changed since the prior query (used by interactive contact-sync delta refreshes
 * and the LID-delta path), and {@link #FULL} bypasses any per-protocol cache and
 * forces a full recompute on the relay (used by background account-sync warmups).
 *
 * @implNote
 * This implementation enumerates the same three modes WA Web's {@code USyncQuery}
 * builder accepts via {@code withMode(...)}.
 */
@WhatsAppWebModule(moduleName = "WAWebUsync")
public enum IqUsyncMode {
    /**
     * Default {@code "query"} mode that fetches the per-protocol state.
     *
     * @apiNote
     * Used by WA Web for the standard contact-sync and device-sync interactive
     * fetches; the per-protocol parsers project the relay reply into typed state.
     */
    QUERY("query"),
    /**
     * {@code "delta"} mode that fetches only the per-protocol entries that have
     * changed since the prior query.
     *
     * @apiNote
     * Used by WA Web's contact-delta and LID-delta paths to avoid re-shipping the
     * whole contact graph on every refresh; the relay diffs against the last
     * snapshot it served the device.
     */
    DELTA("delta"),
    /**
     * {@code "full"} mode that forces a complete per-protocol recompute on the
     * relay.
     *
     * @apiNote
     * Used by WA Web's background account-sync warmup and post-reconnect resync
     * paths when the relay's per-protocol cache must be discarded.
     */
    FULL("full");

    /**
     * Holds the literal wire-side string emitted on the {@code mode} attribute.
     */
    private final String wireValue;

    /**
     * Constructs a mode constant bound to the given wire string.
     *
     * @apiNote
     * Not part of the public surface; the constants are constructed by the enum
     * declarations above.
     *
     * @param wireValue the literal wire-side string
     */
    IqUsyncMode(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Returns the literal wire-side string for the {@code mode} attribute.
     *
     * @return the wire string; never {@code null}
     */
    public String wireValue() {
        return wireValue;
    }
}
