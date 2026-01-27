package com.github.auties00.cobalt.device.info;

import com.github.auties00.cobalt.model.jid.Jid;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

/**
 * Represents a cached device list for a user.
 *
 * @param userJid   the user JID (without device component)
 * @param devices   the list of devices for this user
 * @param timestamp when this list was fetched from the server
 * @param expiresAt when this cache entry expires
 */
public record DeviceList(
        Jid userJid,
        List<DeviceInfo> devices,
        Instant timestamp,
        Instant expiresAt
) {
    private static final Duration DEFAULT_TTL = Duration.ofDays(1);

    /**
     * Creates a new DeviceList with default TTL.
     */
    public static DeviceList of(Jid userJid, List<DeviceInfo> devices) {
        return of(userJid, devices, DEFAULT_TTL);
    }

    /**
     * Creates a new DeviceList with custom TTL.
     */
    public static DeviceList of(Jid userJid, List<DeviceInfo> devices, Duration ttl) {
        var now = Instant.now();
        return new DeviceList(userJid, List.copyOf(devices), now, now.plus(ttl));
    }

    /**
     * Returns true if this cache entry has expired.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Returns the primary device if present.
     */
    public Optional<DeviceInfo> getPrimaryDevice() {
        return devices.stream()
                .filter(DeviceInfo::isPrimary)
                .findFirst();
    }

    /**
     * Returns all non-hosted devices.
     */
    public List<DeviceInfo> e2eeDevices() {
        return devices.stream()
                .filter(d -> !d.isHosted())
                .toList();
    }

    /**
     * Returns the hosted device if present.
     */
    public Optional<DeviceInfo> hostedDevices() {
        return devices.stream()
                .filter(DeviceInfo::isHosted)
                .findFirst();
    }

    /**
     * Converts all devices to their full JIDs.
     */
    public List<Jid> deviceJids() {
        return devices.stream()
                .map(d -> d.toDeviceJid(userJid.user(), userJid.server()))
                .toList();
    }

    /**
     * Returns the number of devices.
     */
    public int size() {
        return devices.size();
    }

    /**
     * Returns true if there are no devices.
     */
    public boolean isEmpty() {
        return devices.isEmpty();
    }

    /**
     * Compares this device list to another and returns a detailed change report.
     *
     * @param other the previous device list
     * @return change report
     */
    public DeviceChanges mismatch(DeviceList other) {
        if (other == null) {
            return new DeviceChanges(deviceJids(), List.of(), List.of());
        }

        var otherDevices = new HashMap<Integer, DeviceInfo>();
        for (var device : other.devices) {
            otherDevices.put(device.id(), device);
        }

        var added = new ArrayList<Jid>();
        var identityChanged = new ArrayList<Jid>();

        for (var device : devices) {
            var otherDevice = otherDevices.remove(device.id());
            var deviceJid = device.toDeviceJid(userJid.user(), userJid.server());

            if (otherDevice == null) {
                added.add(deviceJid);
            } else if (otherDevice.keyIndex() >= 0 && otherDevice.keyIndex() != device.keyIndex()) {
                identityChanged.add(deviceJid);
            }
        }

        var removed = otherDevices.values().stream()
                .map(d -> d.toDeviceJid(userJid.user(), userJid.server()))
                .toList();

        return new DeviceChanges(added, removed, identityChanged);
    }
}
