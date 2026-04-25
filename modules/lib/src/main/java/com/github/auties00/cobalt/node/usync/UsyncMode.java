package com.github.auties00.cobalt.node.usync;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Enumerates the {@code mode} values accepted by the {@code <usync>} stanza.
 *
 * <p>The mode tells the WhatsApp relay how to interpret the request. The default
 * is {@link #QUERY} (the client wants protocol data); {@link #NOTIFY} indicates
 * a side-effect-only request that does not need a body in the response;
 * {@link #DELTA} is used by long-running listeners to receive incremental
 * updates.
 *
 * <p>Each constant carries the literal wire string emitted on the
 * {@code mode="..."} attribute via {@link #wireValue()}.
 *
 * @implNote WAWebUsync.USyncQuery: the default JS instance uses
 *     {@code this.mode = "query"} and exposes {@code withMode(mode)} that takes
 *     the wire string directly. Cobalt models the legal values explicitly so
 *     callers cannot pass an arbitrary string.
 */
@WhatsAppWebModule(moduleName = "WAWebUsync")
public enum UsyncMode {
    /**
     * Standard query mode. The relay returns the protocol data inline in the
     * response and the request is one-shot.
     *
     * @implNote WAWebUsync.USyncQuery: default value of {@code this.mode}.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery", adaptation = WhatsAppAdaptation.DIRECT)
    QUERY("query"),

    /**
     * Notify mode. Used when the client wants the relay to perform a
     * side-effect (e.g. subscribe to updates) without returning a protocol
     * payload.
     *
     * @implNote WAWebUsync.USyncQuery.withMode("notify"): notify-only request.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery", adaptation = WhatsAppAdaptation.DIRECT)
    NOTIFY("notify"),

    /**
     * Delta mode. The relay returns only the entries that have changed since
     * the timestamp the client carried in its per-user device hash info.
     *
     * @implNote WAWebUsync.USyncQuery.withMode("delta"): incremental device
     *     refresh.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery", adaptation = WhatsAppAdaptation.DIRECT)
    DELTA("delta"),

    /**
     * Result mode. Used internally when the relay echoes back a response
     * already obtained from a peer notification.
     *
     * @implNote WAWebUsync.USyncQuery.withMode("result"): result echo.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery", adaptation = WhatsAppAdaptation.DIRECT)
    RESULT("result");

    /**
     * The literal value emitted on the wire for the {@code mode} attribute.
     */
    private final String wireValue;

    /**
     * Creates a new {@code UsyncMode} bound to the given wire string.
     *
     * @param wireValue the literal value the relay expects on the
     *                  {@code mode} attribute
     */
    UsyncMode(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Returns the literal string emitted on the {@code mode} attribute of
     * the {@code <usync>} stanza.
     *
     * @return the wire value
     */
    public String wireValue() {
        return wireValue;
    }
}
