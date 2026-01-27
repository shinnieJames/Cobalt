package com.github.auties00.cobalt.device.fanout;

import com.github.auties00.cobalt.model.jid.Jid;

/**
 * Options for fanout calculation.
 *
 * @param myDeviceJid    the current device's JID (to exclude from fanout)
 * @param includeHosted  whether to include hosted devices (device ID 99)
 * @param mergeAlternate whether to merge alternate device lists
 */
public record DeviceFanoutOptions(
        Jid myDeviceJid,
        boolean includeHosted,
        boolean mergeAlternate
) {
    /**
     * Creates default options with just the current device JID.
     */
    public static DeviceFanoutOptions of(Jid myDeviceJid) {
        return new DeviceFanoutOptions(myDeviceJid, false, false);
    }

    /**
     * Creates options including hosted devices.
     */
    public static DeviceFanoutOptions withHosted(Jid myDeviceJid) {
        return new DeviceFanoutOptions(myDeviceJid, true, false);
    }
}
