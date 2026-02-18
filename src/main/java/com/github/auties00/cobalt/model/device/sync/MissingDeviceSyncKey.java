package com.github.auties00.cobalt.model.device.sync;

import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks a missing Syncd key and which companion devices have been queried for it.
 * <p>
 * Per WhatsApp Web WAWebSyncdStoreMissingKeys: when a sync key is missing,
 * the client asks companion devices for the key. If all devices respond
 * that they don't have it, this is a fatal sync error.
 * <p>
 * Lifecycle:
 * <ol>
 *   <li>Key detected as missing - entry created with askedDevices populated</li>
 *   <li>Device responds without key - device ID added to respondedWithoutKey</li>
 *   <li>Device responds with key - entry removed (no longer missing)</li>
 *   <li>Device removed - device ID removed from both sets via {@link #removeDevice}</li>
 * </ol>
 */
@ProtobufMessage
public final class MissingDeviceSyncKey {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    final byte[] keyId;

    @ProtobufProperty(index = 2, type = ProtobufType.UINT64, mixins = InstantMillisMixin.class)
    final Instant timestamp;

    @ProtobufProperty(index = 3, type = ProtobufType.INT32, packed = true)
    final Set<Integer> askedDevices;

    @ProtobufProperty(index = 4, type = ProtobufType.INT32, packed = true)
    final Set<Integer> respondedWithoutKey;

    MissingDeviceSyncKey(byte[] keyId, Instant timestamp, Set<Integer> askedDevices, Set<Integer> respondedWithoutKey) {
        this.keyId = keyId;
        this.timestamp = timestamp;
        this.askedDevices = ConcurrentHashMap.newKeySet();
        this.respondedWithoutKey = ConcurrentHashMap.newKeySet();
        if (askedDevices != null) {
            this.askedDevices.addAll(askedDevices);
        }
        if (respondedWithoutKey != null) {
            this.respondedWithoutKey.addAll(respondedWithoutKey);
        }
    }

    public byte[] keyId() {
        return keyId;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public void markDeviceAsked(int deviceId) {
        askedDevices.add(deviceId);
    }

    public void markDeviceRespondedWithoutKey(int deviceId) {
        respondedWithoutKey.add(deviceId);
    }

    public void removeDevice(int deviceId) {
        askedDevices.remove(deviceId);
        respondedWithoutKey.remove(deviceId);
    }

    public void retainDevices(Set<Integer> currentDeviceIds) {
        askedDevices.retainAll(currentDeviceIds);
        respondedWithoutKey.retainAll(currentDeviceIds);
    }

    public boolean isMissingOnAllDevices() {
        return !askedDevices.isEmpty()
                && askedDevices.equals(respondedWithoutKey);
    }

    public boolean hasPendingResponses() {
        return askedDevices.size() > respondedWithoutKey.size();
    }
}
