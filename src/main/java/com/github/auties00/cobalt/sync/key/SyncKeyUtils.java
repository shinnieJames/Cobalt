package com.github.auties00.cobalt.sync.key;

import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKey;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyId;

import java.nio.ByteBuffer;

/**
 * Utility methods for extracting metadata from sync key identifiers.
 *
 * <p>Per WhatsApp Web {@code WASyncdKeyManagementUtils}: a sync key ID
 * is a 6-byte buffer structured as:
 * <ul>
 * <li>Bytes 0–1 (big-endian {@code uint16}): device ID of the key creator
 * <li>Bytes 2–5 (big-endian {@code uint32}): key epoch (monotonically increasing)
 * </ul>
 */
public final class SyncKeyUtils {
    /**
     * The expected length of a sync key ID in bytes.
     */
    private static final int KEY_ID_LENGTH = 6;

    private SyncKeyUtils() {
    }

    /**
     * Extracts the device ID (creator device) from a sync key ID.
     *
     * <p>Per WhatsApp Web {@code WASyncdKeyManagementUtils.getKeyDeviceId}:
     * reads the first two bytes as a big-endian unsigned 16-bit integer.
     *
     * @param keyId the raw key ID bytes (must be at least 6 bytes)
     * @return the device ID, or {@code -1} if the key ID is malformed
     */
    public static int getKeyDeviceId(byte[] keyId) {
        if (keyId == null || keyId.length < KEY_ID_LENGTH) {
            return -1;
        }
        return ByteBuffer.wrap(keyId).getShort(0) & 0xFFFF;
    }

    /**
     * Extracts the key epoch from a sync key ID.
     *
     * <p>Per WhatsApp Web {@code WASyncdKeyManagementUtils.getKeyEpoch}:
     * reads bytes 2–5 as a big-endian unsigned 32-bit integer.
     *
     * @param keyId the raw key ID bytes (must be at least 6 bytes)
     * @return the key epoch, or {@code -1} if the key ID is malformed
     */
    public static int getKeyEpoch(byte[] keyId) {
        if (keyId == null || keyId.length < KEY_ID_LENGTH) {
            return -1;
        }
        return ByteBuffer.wrap(keyId).getInt(2);
    }

    /**
     * Extracts the key epoch from an {@link AppStateSyncKey}.
     *
     * @param key the sync key
     * @return the key epoch, or {@code -1} if the key or key ID is absent/malformed
     */
    public static int getKeyEpoch(AppStateSyncKey key) {
        return key.keyId()
                .flatMap(AppStateSyncKeyId::keyId)
                .map(SyncKeyUtils::getKeyEpoch)
                .orElse(-1);
    }

    /**
     * Generates the next key epoch value, incrementing the given key's epoch by one.
     *
     * <p>Per WhatsApp Web {@code WASyncdKeyManagementUtils.generateNewKeyEpoch}.
     *
     * @param keyId the current key ID bytes
     * @return the next epoch value
     */
    public static int generateNewKeyEpoch(byte[] keyId) {
        return getKeyEpoch(keyId) + 1;
    }

    /**
     * Builds a new sync key ID from the given device ID and key epoch.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdRotateKey}: the key ID is constructed
     * by concatenating the device ID (2 bytes, big-endian) with the key epoch
     * (4 bytes, big-endian).
     *
     * @param deviceId the device ID of the creator
     * @param keyEpoch the key epoch
     * @return the 6-byte key ID
     */
    public static byte[] buildKeyId(int deviceId, int keyEpoch) {
        var buffer = ByteBuffer.allocate(KEY_ID_LENGTH);
        buffer.putShort((short) deviceId);
        buffer.putInt(keyEpoch);
        return buffer.array();
    }
}
