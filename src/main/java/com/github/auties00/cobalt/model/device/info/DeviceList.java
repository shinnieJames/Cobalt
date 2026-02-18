package com.github.auties00.cobalt.model.device.info;

import com.github.auties00.cobalt.model.device.DeviceListBuilder;
import com.github.auties00.cobalt.model.device.identity.ADVEncryptionType;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a cached device list for a user.
 */
@ProtobufMessage
public final class DeviceList {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    final Jid userJid;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    final List<DeviceInfo> devices;

    @ProtobufProperty(index = 3, type = ProtobufType.UINT64, mixins = InstantMillisMixin.class)
    final Instant timestamp;

    @ProtobufProperty(index = 5, type = ProtobufType.STRING)
    final String rawId;

    @ProtobufProperty(index = 6, type = ProtobufType.BOOL)
    final boolean deleted;

    @ProtobufProperty(index = 7, type = ProtobufType.BOOL)
    final boolean deletedChangedToHost;

    @ProtobufProperty(index = 8, type = ProtobufType.ENUM)
    final ADVEncryptionType advAccountType;

    @ProtobufProperty(index = 9, type = ProtobufType.UINT64, mixins = InstantMillisMixin.class)
    final Instant expectedTimestamp;

    @ProtobufProperty(index = 10, type = ProtobufType.UINT64, mixins = InstantMillisMixin.class)
    final Instant expectedTimestampLastDeviceJobTimestamp;

    @ProtobufProperty(index = 11, type = ProtobufType.UINT64, mixins = InstantMillisMixin.class)
    final Instant expectedTimestampUpdateTimestamp;

    @ProtobufProperty(index = 12, type = ProtobufType.UINT32)
    final int currentIndex;

    @ProtobufProperty(index = 13, type = ProtobufType.UINT32)
    final SequencedSet<Integer> validIndexes;

    DeviceList(
            Jid userJid,
            List<DeviceInfo> devices,
            Instant timestamp,
            String rawId,
            boolean deleted,
            boolean deletedChangedToHost,
            ADVEncryptionType advAccountType,
            Instant expectedTimestamp,
            Instant expectedTimestampLastDeviceJobTimestamp,
            Instant expectedTimestampUpdateTimestamp,
            int currentIndex,
            SequencedSet<Integer> validIndexes
    ) {
        this.userJid = userJid;
        this.devices = devices != null ? List.copyOf(devices) : List.of();
        this.timestamp = timestamp;
        this.rawId = rawId;
        this.deleted = deleted;
        this.deletedChangedToHost = deletedChangedToHost;
        this.advAccountType = advAccountType;
        this.expectedTimestamp = expectedTimestamp;
        this.expectedTimestampLastDeviceJobTimestamp = expectedTimestampLastDeviceJobTimestamp;
        this.expectedTimestampUpdateTimestamp = expectedTimestampUpdateTimestamp;
        this.currentIndex = currentIndex;
        this.validIndexes = Objects.requireNonNullElseGet(validIndexes, LinkedHashSet::new);
    }

    public Jid userJid() {
        return userJid;
    }

    public List<DeviceInfo> devices() {
        return devices;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public String rawId() {
        return rawId;
    }

    public boolean deleted() {
        return deleted;
    }

    public boolean deletedChangedToHost() {
        return deletedChangedToHost;
    }

    public ADVEncryptionType advAccountType() {
        return advAccountType;
    }

    public Instant expectedTimestamp() {
        return expectedTimestamp;
    }

    public Instant expectedTimestampLastDeviceJobTimestamp() {
        return expectedTimestampLastDeviceJobTimestamp;
    }

    public Instant expectedTimestampUpdateTimestamp() {
        return expectedTimestampUpdateTimestamp;
    }

    public int currentIndex() {
        return currentIndex;
    }

    public SequencedSet<Integer> validIndexes() {
        return Collections.unmodifiableSequencedSet(validIndexes);
    }

    /**
     * Returns the primary device if present.
     */
    public Optional<DeviceInfo> primaryDevice() {
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
    public Set<Jid> deviceJids() {
        return devices.stream()
                .map(d -> d.toDeviceJid(userJid.user(), userJid.server()))
                .collect(Collectors.toUnmodifiableSet());
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
     * Merges this device list with another, deduplicating by device ID.
     * This device list's devices take precedence over the other's.
     *
     * @param other the other device list to merge with
     * @return a new merged device list
     */
    public DeviceList merge(DeviceList other) {
        if (other == null || other.devices.isEmpty()) {
            return this;
        }

        var mergedDevices = new LinkedHashMap<Integer, DeviceInfo>();
        for (var device : other.devices) {
            mergedDevices.put(device.id(), device);
        }
        for (var device : devices) {
            mergedDevices.put(device.id(), device);
        }

        var useThis = timestamp.isAfter(other.timestamp);
        return new DeviceListBuilder()
                .userJid(userJid)
                .devices(List.copyOf(mergedDevices.values()))
                .timestamp(useThis ? timestamp : other.timestamp)
                .rawId(rawId != null ? rawId : other.rawId)
                .deleted(deleted)
                .deletedChangedToHost(deletedChangedToHost)
                .advAccountType(advAccountType != null ? advAccountType : other.advAccountType)
                .expectedTimestamp(expectedTimestamp != null ? expectedTimestamp : other.expectedTimestamp)
                .expectedTimestampLastDeviceJobTimestamp(expectedTimestampLastDeviceJobTimestamp)
                .expectedTimestampUpdateTimestamp(expectedTimestampUpdateTimestamp)
                .currentIndex(currentIndex != 0 ? currentIndex : other.currentIndex)
                .validIndexes(!validIndexes.isEmpty() ? validIndexes : other.validIndexes)
                .build();
    }

    /**
     * Returns true if the account type has changed compared to another device list.
     *
     * @param other the other device list to compare
     * @return true if account types differ and both are non-null
     */
    public boolean hasAccountTypeChanged(DeviceList other) {
        return other != null
                && advAccountType != null
                && other.advAccountType != null
                && advAccountType != other.advAccountType;
    }

    /**
     * Compares this device list to another and returns a detailed change report.
     *
     * @param other the previous device list
     * @return change report
     */
    public DeviceChanges mismatch(DeviceList other) {
        if (other == null) {
            return new DeviceChanges(deviceJids(), Set.of(), Set.of());
        }

        var otherDevices = new HashMap<Integer, DeviceInfo>();
        for (var device : other.devices) {
            otherDevices.put(device.id(), device);
        }

        var added = new HashSet<Jid>();
        var identityChanged = new HashSet<Jid>();

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
                .collect(Collectors.toUnmodifiableSet());

        return new DeviceChanges(added, removed, identityChanged);
    }

    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof DeviceList other
                && Objects.equals(userJid, other.userJid)
                && Objects.equals(devices, other.devices)
                && Objects.equals(timestamp, other.timestamp)
                && Objects.equals(rawId, other.rawId)
                && deleted == other.deleted
                && deletedChangedToHost == other.deletedChangedToHost
                && advAccountType == other.advAccountType
                && Objects.equals(expectedTimestamp, other.expectedTimestamp)
                && Objects.equals(expectedTimestampLastDeviceJobTimestamp, other.expectedTimestampLastDeviceJobTimestamp)
                && Objects.equals(expectedTimestampUpdateTimestamp, other.expectedTimestampUpdateTimestamp)
                && currentIndex == other.currentIndex
                && Objects.equals(validIndexes, other.validIndexes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userJid, devices, timestamp, rawId, deleted,
                deletedChangedToHost, advAccountType, expectedTimestamp,
                expectedTimestampLastDeviceJobTimestamp, expectedTimestampUpdateTimestamp,
                currentIndex, validIndexes);
    }

    @Override
    public String toString() {
        return "DeviceList[userJid=" + userJid + ", devices=" + devices.size() + ", deleted=" + deleted + "]";
    }
}
