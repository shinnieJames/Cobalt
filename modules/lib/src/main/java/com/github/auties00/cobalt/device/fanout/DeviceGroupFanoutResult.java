package com.github.auties00.cobalt.device.fanout;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Bundles the two outputs of a group-message fanout: the recipient device {@link Jid} set and
 * the {@code phash} string attached to the outgoing stanza.
 *
 * @apiNote
 * Produced by {@link com.github.auties00.cobalt.device.DeviceService#getGroupFanout(Jid, Jid)}.
 * The {@link #phash()} value goes on the {@code <message phash="...">} attribute so the server
 * can detect a stale view of the group's device membership and reject the stanza or instruct
 * the client to resync; pairing the two values in one object prevents callers from using one
 * without the other.
 */
@WhatsAppWebModule(moduleName = "WAWebDBDeviceListFanout")
@WhatsAppWebModule(moduleName = "WAWebPhashUtils")
public final class DeviceGroupFanoutResult {

    /**
     * The recipient device {@link Jid} set the group message is encrypted for.
     */
    private final Set<Jid> devices;

    /**
     * The participant hash placed on the outgoing stanza's {@code phash} attribute.
     */
    private final String phash;

    /**
     * Constructs a result from a recipient set and the matching {@code phash}.
     *
     * @apiNote
     * Constructed by {@link com.github.auties00.cobalt.device.DeviceService}; callers do not
     * normally instantiate this directly.
     *
     * @param devices the device JIDs to encrypt to
     * @param phash   the participant hash computed from {@code devices} via
     *                {@link DevicePhashCalculator}
     * @throws NullPointerException if either argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebDBDeviceListFanout",
            exports = "getFanOutList",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebPhashUtils",
            exports = "phashV2",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public DeviceGroupFanoutResult(Set<Jid> devices, String phash) {
        this.devices = Objects.requireNonNull(devices, "devices cannot be null");
        this.phash = Objects.requireNonNull(phash, "phash cannot be null");
    }

    /**
     * Returns the recipient device {@link Jid} set.
     *
     * @apiNote
     * The returned view is the set the group message must be encrypted for; iterate to drive
     * the per-device send loop in {@link com.github.auties00.cobalt.device.DeviceService}.
     *
     * @return an unmodifiable view of the underlying device JID set
     */
    @WhatsAppWebExport(moduleName = "WAWebDBDeviceListFanout",
            exports = "getFanOutList",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Set<Jid> devices() {
        return Collections.unmodifiableSet(devices);
    }

    /**
     * Returns the {@code phash} attribute value to place on the outgoing stanza.
     *
     * @apiNote
     * The string is already prefixed with the version tag ({@code "1:"} for V1, {@code "2:"}
     * for V2) and ready to set on the {@code <message phash="...">} stanza attribute; the
     * server uses it to confirm both ends agree on the group's device membership.
     *
     * @return the participant hash string
     */
    @WhatsAppWebExport(moduleName = "WAWebPhashUtils",
            exports = "phashV2",
            adaptation = WhatsAppAdaptation.DIRECT)
    public String phash() {
        return phash;
    }
}
