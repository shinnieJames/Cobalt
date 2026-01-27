package com.github.auties00.cobalt.device.fanout;

import com.github.auties00.cobalt.device.info.DeviceInfo;
import com.github.auties00.cobalt.device.info.DeviceList;
import com.github.auties00.cobalt.model.jid.Jid;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * Calculates which devices should receive a message (fanout list).
 */
public final class DeviceFanoutCalculator {
    private DeviceFanoutCalculator() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Calculates the fanout list for a device list.
     *
     * @param deviceList the user's device list
     * @param options    fanout calculation options
     * @return list of device JIDs to send to
     */
    public static List<Jid> calculate(DeviceList deviceList, DeviceFanoutOptions options) {
        var userJid = deviceList.userJid();
        var filtered = new ArrayList<DeviceInfo>();

        for (var device : deviceList.devices()) {
            if (device.isHosted() && !options.includeHosted()) {
                continue;
            }

            var deviceJid = device.toDeviceJid(userJid.user(), userJid.server());
            if (isOwnDevice(deviceJid, options.myDeviceJid())) {
                continue;
            }

            filtered.add(device);
        }

        if (options.mergeAlternate()) {
            filtered = mergeAlternateDevices(filtered);
        }

        // Step 4: If no devices remain, fall back to primary device
        if (filtered.isEmpty()) {
            deviceList.getPrimaryDevice()
                    .filter(primary -> !isOwnDevice(
                            primary.toDeviceJid(userJid.user(), userJid.server()),
                            options.myDeviceJid())
                    )
                    .ifPresent(filtered::add);
        }

        // Convert to JIDs
        return filtered.stream()
                .map(d -> d.toDeviceJid(userJid.user(), userJid.server()))
                .toList();
    }

    /**
     * Calculates fanout for multiple device lists (e.g., group message).
     *
     * @param deviceLists list of device lists for all participants
     * @param options     fanout calculation options
     * @return combined list of device JIDs to send to
     */
    public static List<Jid> calculateMultiple(List<DeviceList> deviceLists, DeviceFanoutOptions options) {
        var result = new ArrayList<Jid>();
        for (var deviceList : deviceLists) {
            result.addAll(calculate(deviceList, options));
        }
        return result;
    }

    private static boolean isOwnDevice(Jid targetDevice, Jid myDevice) {
        if (myDevice == null) {
            return false;
        }
        return Objects.equals(targetDevice.user(), myDevice.user())
                && Objects.equals(targetDevice.server(), myDevice.server())
                && targetDevice.device() == myDevice.device();
    }

    private static ArrayList<DeviceInfo> mergeAlternateDevices(ArrayList<DeviceInfo> devices) {
        var seen = new HashSet<Integer>();
        var result = new ArrayList<DeviceInfo>();
        for (var device : devices) {
            if (seen.add(device.id())) {
                result.add(device);
            }
        }
        return result;
    }
}
