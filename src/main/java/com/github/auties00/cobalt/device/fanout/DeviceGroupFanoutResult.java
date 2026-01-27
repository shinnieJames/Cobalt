package com.github.auties00.cobalt.device.fanout;

import com.github.auties00.cobalt.device.info.DeviceList;
import com.github.auties00.cobalt.model.jid.Jid;

import java.util.List;

/**
 * Result of group fanout calculation.
 *
 * @param devices     the list of device JIDs to send to
 * @param phash       the calculated participant hash
 * @param deviceLists the device lists used for calculation
 */
public record DeviceGroupFanoutResult(
        List<Jid> devices,
        String phash,
        List<DeviceList> deviceLists
) {
    public boolean isSingleDevice() {
        return devices.size() == 1;
    }

    public int deviceCount() {
        return devices.size();
    }
}
