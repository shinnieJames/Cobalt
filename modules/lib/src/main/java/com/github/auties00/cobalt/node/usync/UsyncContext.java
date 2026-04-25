package com.github.auties00.cobalt.node.usync;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Enumerates the {@code context} values accepted by the {@code <usync>} stanza.
 *
 * <p>The context is a free-form discriminator the relay logs and uses to
 * triage backoff: a USync that fails with {@link #INTERACTIVE} (a user just
 * tapped something) is retried sooner than one that fails with
 * {@link #BACKGROUND}. The {@link #MESSAGE} and {@link #VOIP} contexts also
 * change backoff semantics — a {@code devices} protocol failure in one of
 * those contexts is exempt from per-protocol backoff because the resulting
 * stanza can no longer be encrypted.
 *
 * <p>WhatsApp Web hardcodes a small set of contexts; Cobalt models them
 * explicitly so callers cannot accidentally typo a context that the relay
 * silently ignores.
 *
 * @implNote WAWebUsync.USyncQuery: {@code this.context = "interactive"}
 *     default; {@code withContext(context)} takes a free-form string.
 *     WAWebUsyncBackoff.waitForBackoff: branches on
 *     {@code context==="interactive"} to skip backoff entirely and on
 *     {@code context==="message"||context==="voip"} together with the
 *     {@code "devices"} protocol to bypass the per-protocol backoff timer.
 */
@WhatsAppWebModule(moduleName = "WAWebUsync")
@WhatsAppWebModule(moduleName = "WAWebUsyncBackoff")
public enum UsyncContext {
    /**
     * Interactive context. The user is waiting on the result of this query;
     * backoff is skipped entirely.
     *
     * @implNote WAWebUsyncBackoff.waitForBackoff: if context is
     *     {@code interactive} the function returns a resolved promise without
     *     consulting the per-protocol backoff map.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery", adaptation = WhatsAppAdaptation.DIRECT)
    INTERACTIVE("interactive"),

    /**
     * Outbound-message context. Used when the query is needed to encrypt
     * an outgoing message; the {@code devices} protocol is exempt from
     * backoff because failing here would block the send.
     *
     * @implNote WAWebUsyncBackoff.waitForBackoff: combination of
     *     {@code context="message"} and the {@code devices} protocol bypasses
     *     the backoff timer.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncBackoff",
            exports = "waitForBackoff", adaptation = WhatsAppAdaptation.DIRECT)
    MESSAGE("message"),

    /**
     * VoIP signalling context. Same backoff exemption as {@link #MESSAGE}
     * for the {@code devices} protocol.
     *
     * @implNote WAWebUsyncBackoff.waitForBackoff: identical exemption logic
     *     to the {@code message} context.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncBackoff",
            exports = "waitForBackoff", adaptation = WhatsAppAdaptation.DIRECT)
    VOIP("voip"),

    /**
     * Background context. The query was issued by an idle task and the
     * client is willing to wait through any per-protocol backoff.
     *
     * @implNote WAWebUsync.USyncQuery: emitted by background syncs such as
     *     {@code WAWebSyncContactsJob} during periodic refreshes.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery", adaptation = WhatsAppAdaptation.DIRECT)
    BACKGROUND("background"),

    /**
     * Notification context. Used for queries triggered by an inbound
     * notification stanza (e.g. account-sync).
     *
     * @implNote WAWebUsync.USyncQuery: emitted by
     *     {@code WAWebHandleAccountSyncNotification} when a notification
     *     forces a re-fetch.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsync",
            exports = "USyncQuery", adaptation = WhatsAppAdaptation.DIRECT)
    NOTIFICATION("notification");

    /**
     * The literal value emitted on the wire for the {@code context}
     * attribute.
     */
    private final String wireValue;

    /**
     * Creates a new {@code UsyncContext} bound to the given wire string.
     *
     * @param wireValue the literal value the relay expects
     */
    UsyncContext(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * Returns the literal string emitted on the {@code context} attribute
     * of the {@code <usync>} stanza.
     *
     * @return the wire value
     */
    public String wireValue() {
        return wireValue;
    }
}
