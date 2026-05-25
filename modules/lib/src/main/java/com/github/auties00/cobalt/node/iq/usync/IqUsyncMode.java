package com.github.auties00.cobalt.node.iq.usync;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

/**
 * Enumerates the usync {@code mode} attribute values that drive the relay's per-protocol caching
 * strategy.
 * <p>
 * One value is chosen when building an {@link IqUsyncRequest}. {@link #QUERY} fetches the current
 * per-protocol state, {@link #DELTA} fetches only the entries that changed since the prior query,
 * and {@link #FULL} bypasses any per-protocol cache and forces a full recompute on the relay. The
 * chosen constant is emitted verbatim on the {@code mode} attribute of the outbound
 * {@code <usync>} envelope.
 */
@WhatsAppWebModule(moduleName = "WAWebUsync")
public enum IqUsyncMode {
    /**
     * Fetches the current per-protocol state.
     * <p>
     * Default mode emitting {@code "query"}, used for the standard interactive contact-sync and
     * device-sync fetches; the per-protocol parsers project the relay reply into typed state.
     */
    QUERY("query"),
    /**
     * Fetches only the per-protocol entries that have changed since the prior query.
     * <p>
     * Emits {@code "delta"} and is used by the contact-delta and LID-delta refresh paths to avoid
     * re-shipping the whole contact graph on every refresh; the relay diffs against the last
     * snapshot it served the device.
     */
    DELTA("delta"),
    /**
     * Forces a complete per-protocol recompute on the relay.
     * <p>
     * Emits {@code "full"} and is used by background account-sync warmup and post-reconnect resync
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
