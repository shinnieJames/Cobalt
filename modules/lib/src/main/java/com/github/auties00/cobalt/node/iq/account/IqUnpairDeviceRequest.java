package com.github.auties00.cobalt.node.iq.account;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.iq.IqOperation;
import java.util.Objects;

/**
 * Outbound {@code <iq xmlns="md" type="set">} stanza requesting that the relay forget the
 * pairing record for a companion device.
 *
 * @apiNote
 * Used by the logout pipeline ({@code WAWebSocketModel.sendCurrentLogout}) to tear down the
 * server-side multi-device record before the local socket is closed, and by any caller that
 * wants to unpair a specific secondary device while remaining connected. The relay echoes
 * back the {@code <remove-companion-device>} payload with a {@code <iq type="result">}
 * envelope on success.
 */
@WhatsAppWebModule(moduleName = "WAWebUnpairDeviceJob")
public final class IqUnpairDeviceRequest implements IqOperation.Request {
    /**
     * Companion-device JID being unpaired.
     *
     * @apiNote
     * Routed verbatim into the {@code <remove-companion-device jid=...>} attribute. WA Web
     * fills this with {@code WAWebCommsWapMd.DEVICE_JID(getMeDevicePnOrThrow_DO_NOT_USE())}
     * for the self-logout path; Cobalt accepts any JID so a primary client may unpair an
     * arbitrary companion.
     */
    private final Jid deviceJid;

    /**
     * Free-form caller-supplied reason string.
     *
     * @apiNote
     * Routed verbatim into the {@code <remove-companion-device reason=...>} attribute and
     * surfaced server-side as telemetry; the relay does not validate the contents.
     */
    private final String reason;

    /**
     * Constructs a new unpair-device request.
     *
     * @apiNote
     * Both arguments are routed verbatim into the outbound stanza; no client-side validation
     * other than the {@code null} check is performed.
     *
     * @param deviceJid the companion-device JID to unpair
     * @param reason the caller-supplied free-form reason string
     * @throws NullPointerException if either argument is {@code null}
     */
    public IqUnpairDeviceRequest(Jid deviceJid, String reason) {
        this.deviceJid = Objects.requireNonNull(deviceJid, "deviceJid cannot be null");
        this.reason = Objects.requireNonNull(reason, "reason cannot be null");
    }

    /**
     * Returns the companion-device JID being unpaired.
     *
     * @return the device JID, never {@code null}
     */
    public Jid deviceJid() {
        return deviceJid;
    }

    /**
     * Returns the free-form unpair reason.
     *
     * @return the reason string, never {@code null}
     */
    public String reason() {
        return reason;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Produces a {@code <iq xmlns="md" type="set">} envelope addressed to
     * {@link Jid#userServer()} and wrapping a single {@code <remove-companion-device>} child
     * carrying {@code jid} and {@code reason} attributes.
     *
     * @return a {@link NodeBuilder} carrying the {@code <iq>} envelope and the
     *         {@code <remove-companion-device>} payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUnpairDeviceJob",
            exports = "unpairDevice", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var removePayload = new NodeBuilder()
                .description("remove-companion-device")
                .attribute("jid", deviceJid)
                .attribute("reason", reason)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "md")
                .attribute("to", Jid.userServer())
                .attribute("type", "set")
                .content(removePayload);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (IqUnpairDeviceRequest) obj;
        return Objects.equals(this.deviceJid, that.deviceJid)
                && Objects.equals(this.reason, that.reason);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(deviceJid, reason);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "IqUnpairDeviceRequest[deviceJid=" + deviceJid
                + ", reason=" + reason + ']';
    }
}
