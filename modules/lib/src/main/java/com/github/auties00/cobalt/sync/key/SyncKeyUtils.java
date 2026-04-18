package com.github.auties00.cobalt.sync.key;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKey;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyId;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collection;

/**
 * Utility methods for extracting metadata from sync key identifiers and
 * performing byte-level conversions used by the app state sync subsystem.
 *
 * <p>Per WhatsApp Web {@code WASyncdKeyManagementUtils}: a sync key ID
 * is a 6-byte buffer structured as:
 * <ul>
 * <li>Bytes 0-1 (big-endian {@code uint16}): device ID of the key creator
 * <li>Bytes 2-5 (big-endian {@code uint32}): key epoch (monotonically increasing)
 * </ul>
 *
 * <p>Additionally mirrors utility functions from {@code WAWebSyncdCryptoUtils}
 * for buffer concatenation/splitting, hex conversions, 64-bit network order
 * encoding, and key ID comparison.
 *
 * @implNote WAWebSyncdCryptoUtils, WASyncdKeyManagementUtils, WAWebSyncdGatingUtils
 */
@WhatsAppWebModule(moduleName = "WAWebSyncdCryptoUtils")
@WhatsAppWebModule(moduleName = "WASyncdKeyManagementUtils")
@WhatsAppWebModule(moduleName = "WAWebSyncdGatingUtils")
@WhatsAppWebModule(moduleName = "WAWebSyncdRotateKey")
@WhatsAppWebModule(moduleName = "WAWebSyncdKeyManagement")
public final class SyncKeyUtils {
    /**
     * The expected length of a sync key ID in bytes.
     */
    private static final int KEY_ID_LENGTH = 6;

    /**
     * Private constructor to prevent instantiation.
     *
     * @implNote NO_WA_BASIS
     */
    private SyncKeyUtils() {
    }

    /**
     * Concatenates multiple byte arrays into a single byte array.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdCryptoUtils.combine}: accepts an array
     * of {@code ArrayBuffer} values and returns a single concatenated buffer.
     * Throws if the input is empty; returns the sole element if length is 1.
     *
     * @param buffers the byte arrays to concatenate
     * @return a single byte array containing all input bytes in order
     * @throws IllegalArgumentException if {@code buffers} is empty
     * @implNote WAWebSyncdCryptoUtils.combine (function e)
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoUtils", exports = "combine", adaptation = WhatsAppAdaptation.DIRECT)
    public static byte[] combine(byte[]... buffers) {
        if (buffers.length == 0) { // WAWebSyncdCryptoUtils.combine: if (e.length === 0) throw err("buffers length is zero")
            throw new IllegalArgumentException("buffers length is zero");
        }

        if (buffers.length == 1) { // WAWebSyncdCryptoUtils.combine: if (e.length === 1) return e[0]
            return buffers[0];
        }

        var totalLength = 0; // WAWebSyncdCryptoUtils.combine: t = e.map(e => e.byteLength), n = t.reduce((e, t) => e + t)
        for (var buffer : buffers) {
            totalLength += buffer.length;
        }

        var result = new byte[totalLength]; // WAWebSyncdCryptoUtils.combine: o = new Uint8Array(n)
        var offset = 0; // WAWebSyncdCryptoUtils.combine: a = 0
        for (var buffer : buffers) { // WAWebSyncdCryptoUtils.combine: for (i = 0; i < e.length; i++)
            System.arraycopy(buffer, 0, result, offset, buffer.length); // WAWebSyncdCryptoUtils.combine: o.set(new Uint8Array(e[i]), a)
            offset += buffer.length; // WAWebSyncdCryptoUtils.combine: a += e[i].byteLength
        }
        return result; // WAWebSyncdCryptoUtils.combine: return o.buffer
    }

    /**
     * Splits a byte array into three parts at the given offsets.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdCryptoUtils.split}: given buffer {@code e},
     * offset {@code t}, and length {@code n}, returns:
     * <ul>
     * <li>{@code [0, t)} - prefix
     * <li>{@code [t, t+n)} - middle
     * <li>{@code [t+n, end)} - suffix
     * </ul>
     *
     * @param buffer the byte array to split
     * @param offset the start offset of the middle segment
     * @param length the length of the middle segment
     * @return a three-element array of byte arrays: prefix, middle, suffix
     * @throws IllegalArgumentException if {@code offset} or {@code length} is negative
     * @implNote WAWebSyncdCryptoUtils.split (function s)
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoUtils", exports = "split", adaptation = WhatsAppAdaptation.DIRECT)
    public static byte[][] split(byte[] buffer, int offset, int length) {
        if (offset < 0 || length < 0) { // WAWebSyncdCryptoUtils.split: if (t < 0 || n < 0) throw err("buffers length is zero")
            throw new IllegalArgumentException("buffers length is zero");
        }

        var result = new byte[3][]; // WAWebSyncdCryptoUtils.split: o = new Array(3)
        result[0] = Arrays.copyOfRange(buffer, 0, offset); // WAWebSyncdCryptoUtils.split: a.slice(0, t).buffer
        result[1] = Arrays.copyOfRange(buffer, offset, offset + length); // WAWebSyncdCryptoUtils.split: a.slice(t, t + n).buffer
        result[2] = Arrays.copyOfRange(buffer, offset + length, buffer.length); // WAWebSyncdCryptoUtils.split: a.slice(t + n).buffer
        return result; // WAWebSyncdCryptoUtils.split: return o
    }

    /**
     * Converts a space-separated hex string to a byte array.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdCryptoUtils.hexToUint8Array}: splits
     * the input string on spaces and parses each token as a hexadecimal byte value.
     *
     * @param hex the space-separated hex string (e.g., {@code "a 1f 0"})
     * @return the decoded byte array
     * @implNote WAWebSyncdCryptoUtils.hexToUint8Array (function u)
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoUtils", exports = "hexToUint8Array", adaptation = WhatsAppAdaptation.DIRECT)
    public static byte[] hexToUint8Array(String hex) {
        var parts = hex.split(" "); // WAWebSyncdCryptoUtils.hexToUint8Array: e.split(" ")
        var result = new byte[parts.length]; // WAWebSyncdCryptoUtils.hexToUint8Array: Uint8Array.from(...)
        for (var i = 0; i < parts.length; i++) {
            result[i] = (byte) Integer.parseInt(parts[i], 16); // WAWebSyncdCryptoUtils.hexToUint8Array: parseInt(e, 16)
        }
        return result; // WAWebSyncdCryptoUtils.hexToUint8Array: return result
    }

    /**
     * Converts a sync key's key ID bytes to a space-separated hex string.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdCryptoUtils.syncKeyIdToHex}:
     * converts each byte to its hexadecimal representation (without zero-padding)
     * and joins with spaces.
     *
     * @param keyId the raw key ID bytes
     * @return the hex string (e.g., {@code "a 1f 0"})
     * @implNote WAWebSyncdCryptoUtils.syncKeyIdToHex (function c);
     *           {@code WASyncdKeyTypes.fromSyncKeyId} is the identity function,
     *           so it reduces to a direct byte iteration here.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoUtils", exports = "syncKeyIdToHex", adaptation = WhatsAppAdaptation.DIRECT)
    public static String syncKeyIdToHex(byte[] keyId) {
        if (keyId == null || keyId.length == 0) { // ADAPTED: Java null-safety guard
            return "unknown";
        }

        // WAWebSyncdCryptoUtils.syncKeyIdToHex: Array.from(new Uint8Array(...), e => e.toString(16)).toString().replace(/,/g, " ")
        var sb = new StringBuilder();
        for (var i = 0; i < keyId.length; i++) {
            if (i > 0) {
                sb.append(' '); // WAWebSyncdCryptoUtils.syncKeyIdToHex: .replace(/,/g, " ")
            }
            sb.append(Integer.toHexString(keyId[i] & 0xFF)); // WAWebSyncdCryptoUtils.syncKeyIdToHex: e.toString(16) - no padding
        }
        return sb.toString();
    }

    /**
     * Converts a sync key's key ID bytes to a space-separated hex string,
     * extracting the raw bytes from an {@link AppStateSyncKey}.
     *
     * <p>This is a convenience overload that unwraps the key ID from the
     * {@code AppStateSyncKey} protobuf wrapper before delegating to
     * {@link #syncKeyIdToHex(byte[])}.
     *
     * @param key the sync key
     * @return the hex string, or {@code "unknown"} if the key ID is absent
     * @implNote ADAPTED: WAWebSyncdCryptoUtils.syncKeyIdToHex with Optional unwrapping
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoUtils", exports = "syncKeyIdToHex", adaptation = WhatsAppAdaptation.ADAPTED)
    public static String syncKeyIdToHex(AppStateSyncKey key) {
        var keyIdBytes = key.keyId() // ADAPTED: Optional unwrapping for Java protobuf model
                .flatMap(AppStateSyncKeyId::keyId)
                .orElse(null);
        return syncKeyIdToHex(keyIdBytes);
    }

    /**
     * Converts a byte array to a hex string with zero-padded 2-character
     * representation per byte, with no separator between bytes.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdCryptoUtils.arrayBufferToHexPadded}:
     * maps each byte to {@code e.toString(16).padStart(2, "0")} and joins
     * with empty string.
     *
     * @param data the byte array to convert
     * @return the zero-padded hex string (e.g., {@code "0a1f00"})
     * @implNote WAWebSyncdCryptoUtils.arrayBufferToHexPadded (function d).
     *           The JDK-native equivalent is {@code java.util.HexFormat.of().formatHex(data)};
     *           the explicit loop is retained only to keep the {@code null}/empty guard
     *           that returns the empty string instead of throwing.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoUtils", exports = "arrayBufferToHexPadded", adaptation = WhatsAppAdaptation.DIRECT)
    public static String arrayBufferToHexPadded(byte[] data) {
        if (data == null || data.length == 0) { // ADAPTED: Java null-safety guard
            return "";
        }

        var sb = new StringBuilder(data.length * 2); // WAWebSyncdCryptoUtils.arrayBufferToHexPadded: Array.from(...).join("")
        for (var b : data) {
            var hex = Integer.toHexString(b & 0xFF); // WAWebSyncdCryptoUtils.arrayBufferToHexPadded: e.toString(16)
            if (hex.length() < 2) { // WAWebSyncdCryptoUtils.arrayBufferToHexPadded: .padStart(2, "0")
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    /**
     * Converts a number to a 64-bit big-endian byte array (network order).
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdCryptoUtils.to64BitNetworkOrder}: creates
     * an 8-byte buffer, writes the value as a big-endian unsigned 32-bit integer
     * at offset 4 (upper 4 bytes remain zero).
     *
     * @param value the value to convert (treated as unsigned 32-bit)
     * @return an 8-byte array in big-endian network order
     * @implNote WAWebSyncdCryptoUtils.to64BitNetworkOrder (function m).
     *           The JDK-native equivalent is
     *           {@code ByteBuffer.allocate(8).putInt(4, (int) value).array()};
     *           the manual byte writes are retained for clarity and to avoid allocating
     *           a wrapper {@link ByteBuffer} per invocation.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoUtils", exports = "to64BitNetworkOrder", adaptation = WhatsAppAdaptation.DIRECT)
    public static byte[] to64BitNetworkOrder(long value) {
        var buffer = new byte[8]; // WAWebSyncdCryptoUtils.to64BitNetworkOrder: new ArrayBuffer(8)
        // WAWebSyncdCryptoUtils.to64BitNetworkOrder: new DataView(t).setUint32(4, e, false)
        // Upper 4 bytes remain zero (from array initialization)
        buffer[4] = (byte) (value >> 24); // WAWebSyncdCryptoUtils.to64BitNetworkOrder: big-endian byte 0 of uint32
        buffer[5] = (byte) (value >> 16); // WAWebSyncdCryptoUtils.to64BitNetworkOrder: big-endian byte 1 of uint32
        buffer[6] = (byte) (value >> 8); // WAWebSyncdCryptoUtils.to64BitNetworkOrder: big-endian byte 2 of uint32
        buffer[7] = (byte) value; // WAWebSyncdCryptoUtils.to64BitNetworkOrder: big-endian byte 3 of uint32
        return buffer;
    }

    /**
     * Compares two sync key IDs for equality using constant-time comparison.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdCryptoUtils.syncKeyIdsEqual}: extracts
     * the raw bytes from each key ID via {@code WASyncdKeyTypes.fromSyncKeyId}
     * (identity function) and compares using
     * {@code WACryptoUtils.arrayBuffersEqual} which delegates to
     * {@code WACryptoPrimitives.verify} (constant-time comparison).
     *
     * @param keyId1 the first key ID bytes
     * @param keyId2 the second key ID bytes
     * @return {@code true} if both key IDs contain the same bytes
     * @implNote WAWebSyncdCryptoUtils.syncKeyIdsEqual (function p);
     *           {@code WASyncdKeyTypes.fromSyncKeyId} is the identity function and
     *           {@code WACryptoUtils.arrayBuffersEqual} is a constant-time byte comparison,
     *           mapped to {@link MessageDigest#isEqual(byte[], byte[])} here.
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoUtils", exports = "syncKeyIdsEqual", adaptation = WhatsAppAdaptation.DIRECT)
    public static boolean syncKeyIdsEqual(byte[] keyId1, byte[] keyId2) {
        if (keyId1 == null && keyId2 == null) { // ADAPTED: Java null-safety guard
            return true;
        }

        if (keyId1 == null || keyId2 == null) { // ADAPTED: Java null-safety guard
            return false;
        }

        // WAWebSyncdCryptoUtils.syncKeyIdsEqual: WACryptoUtils.arrayBuffersEqual(fromSyncKeyId(e), fromSyncKeyId(t))
        // WACryptoUtils.arrayBuffersEqual delegates to WACryptoPrimitives.verify (constant-time)
        return MessageDigest.isEqual(keyId1, keyId2); // ADAPTED: Java constant-time comparison via MessageDigest.isEqual
    }

    /**
     * Extracts the device ID (creator device) from a sync key ID.
     *
     * <p>Per WhatsApp Web {@code WASyncdKeyManagementUtils.getKeyDeviceId}:
     * reads the first two bytes as a big-endian unsigned 16-bit integer.
     *
     * @param keyId the raw key ID bytes (must be at least 6 bytes)
     * @return the device ID, or {@code -1} if the key ID is malformed
     * @implNote WASyncdKeyManagementUtils.getKeyDeviceId
     */
    public static int getKeyDeviceId(byte[] keyId) {
        if (keyId == null || keyId.length < KEY_ID_LENGTH) { // ADAPTED: Java null-safety guard
            return -1;
        }
        return ByteBuffer.wrap(keyId).getShort(0) & 0xFFFF; // WASyncdKeyManagementUtils.getKeyDeviceId: DataView.getUint16(0)
    }

    /**
     * Extracts the key epoch from a sync key ID.
     *
     * <p>Per WhatsApp Web {@code WASyncdKeyManagementUtils.getKeyEpoch}:
     * reads bytes 2-5 as a big-endian unsigned 32-bit integer.
     *
     * @param keyId the raw key ID bytes (must be at least 6 bytes)
     * @return the key epoch, or {@code -1} if the key ID is malformed
     * @implNote WASyncdKeyManagementUtils.getKeyEpoch
     */
    public static int getKeyEpoch(byte[] keyId) {
        if (keyId == null || keyId.length < KEY_ID_LENGTH) { // ADAPTED: Java null-safety guard
            return -1;
        }
        return ByteBuffer.wrap(keyId).getInt(2); // WASyncdKeyManagementUtils.getKeyEpoch: DataView.getUint32(2)
    }

    /**
     * Extracts the key epoch from an {@link AppStateSyncKey}.
     *
     * @param key the sync key
     * @return the key epoch, or {@code -1} if the key or key ID is absent/malformed
     * @implNote ADAPTED: convenience overload wrapping WASyncdKeyManagementUtils.getKeyEpoch with Optional unwrapping
     */
    public static int getKeyEpoch(AppStateSyncKey key) {
        return key.keyId() // ADAPTED: WASyncdKeyManagementUtils.getKeyEpoch - Optional unwrapping
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
     * @implNote WASyncdKeyManagementUtils.generateNewKeyEpoch
     */
    public static int generateNewKeyEpoch(byte[] keyId) {
        return getKeyEpoch(keyId) + 1; // WASyncdKeyManagementUtils.generateNewKeyEpoch: getKeyEpoch(e) + 1
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
     * @implNote WAWebSyncdRotateKey.rotateKey (key ID construction)
     */
    public static byte[] buildKeyId(int deviceId, int keyEpoch) {
        var buffer = ByteBuffer.allocate(KEY_ID_LENGTH); // WAWebSyncdRotateKey.rotateKey: new Uint8Array(6)
        buffer.putShort((short) deviceId); // WAWebSyncdRotateKey.rotateKey: DataView.setUint16(0, deviceId)
        buffer.putInt(keyEpoch); // WAWebSyncdRotateKey.rotateKey: DataView.setUint32(2, epoch)
        return buffer.array();
    }

    /**
     * Finds the newest sync key using the same ordering as WA Web:
     * highest epoch first, then lowest device id among ties.
     *
     * @param keys the available sync keys
     * @return the newest key, or {@code null} if none exist
     * @implNote WAWebSyncdKeyManagement.getNewestKeyPair
     */
    public static AppStateSyncKey findNewestKey(Collection<AppStateSyncKey> keys) {
        if (keys == null || keys.isEmpty()) { // ADAPTED: Java null-safety guard
            return null; // WAWebSyncdKeyManagement.getNewestKeyPair: if (e.length === 0) return null
        }

        // WAWebSyncdKeyManagement.getNewestKeyPair: Math.max(...e.map(getKeyEpoch))
        var maxEpoch = Integer.MIN_VALUE;
        for (var key : keys) {
            var epoch = getKeyEpoch(key); // WAWebSyncdKeyManagement.getNewestKeyPair: getKeyEpoch(e.keyId)
            if (epoch > maxEpoch) {
                maxEpoch = epoch;
            }
        }

        // WAWebSyncdKeyManagement.getNewestKeyPair: filter by maxEpoch, then Math.min(...getKeyDeviceId)
        AppStateSyncKey bestKey = null;
        var bestDeviceId = Integer.MAX_VALUE;
        for (var key : keys) {
            if (getKeyEpoch(key) != maxEpoch) { // WAWebSyncdKeyManagement.getNewestKeyPair: e.filter(epoch === n)
                continue;
            }

            var keyIdBytes = key.keyId()
                    .flatMap(AppStateSyncKeyId::keyId)
                    .orElse(null);
            if (keyIdBytes == null) { // ADAPTED: Java null-safety guard
                continue;
            }

            var deviceId = getKeyDeviceId(keyIdBytes); // WAWebSyncdKeyManagement.getNewestKeyPair: getKeyDeviceId(e.keyId)
            if (deviceId < bestDeviceId) { // WAWebSyncdKeyManagement.getNewestKeyPair: Math.min + indexOf
                bestDeviceId = deviceId;
                bestKey = key;
            }
        }

        return bestKey; // WAWebSyncdKeyManagement.getNewestKeyPair: return r[l]
    }

    /**
     * Returns the maximum number of days a sync key can be used before
     * rotation is required.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdGatingUtils.getSyncdKeyMaxUseDays}:
     * reads the {@code syncd_key_max_use_days} AB prop via
     * {@code getABPropConfigValue}. The AB prop is of type {@code int}
     * with a default value of {@code 30}.
     *
     * @param abPropsService the AB props service to read the configuration from
     * @return the maximum key use days from the AB prop
     * @implNote WAWebSyncdGatingUtils.getSyncdKeyMaxUseDays (function e)
     */
    public static int getSyncdKeyMaxUseDays(ABPropsService abPropsService) {
        return abPropsService.getInt(ABProp.SYNCD_KEY_MAX_USE_DAYS); // WAWebSyncdGatingUtils.getSyncdKeyMaxUseDays
    }

    /**
     * Returns the sentinel timeout in seconds.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdGatingUtils.getSyncdSentinelTimeoutSeconds}:
     * reads the {@code syncd_sentinel_timeout_seconds} AB prop via
     * {@code getABPropConfigValue}. The AB prop is of type {@code int}
     * with a default value of {@code 3}. Used by {@code WAWebSocketModel}
     * to timeout the sentinel mutation flush during logout.
     *
     * @param abPropsService the AB props service to read the configuration from
     * @return the sentinel timeout in seconds from the AB prop
     * @implNote WAWebSyncdGatingUtils.getSyncdSentinelTimeoutSeconds (function s)
     */
    public static int getSyncdSentinelTimeoutSeconds(ABPropsService abPropsService) {
        return abPropsService.getInt(ABProp.SYNCD_SENTINEL_TIMEOUT_SECONDS); // WAWebSyncdGatingUtils.getSyncdSentinelTimeoutSeconds
    }

    /**
     * Returns the maximum number of mutations that can be sent inline
     * before requiring external blob upload.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdGatingUtils.getSyncdInlineMutationsMaxCount}:
     * reads the {@code syncd_inline_mutations_max_count} AB prop via
     * {@code getABPropConfigValue}. The AB prop is of type {@code int}
     * with a default value of {@code 100}. Used by
     * {@code WAWebSyncdMMSUpload.exceedInlineMutationCount} with clamping
     * between 100 and 2000.
     *
     * @param abPropsService the AB props service to read the configuration from
     * @return the maximum inline mutation count from the AB prop
     * @implNote WAWebSyncdGatingUtils.getSyncdInlineMutationsMaxCount (function u)
     */
    public static int getSyncdInlineMutationsMaxCount(ABPropsService abPropsService) {
        return abPropsService.getInt(ABProp.SYNCD_INLINE_MUTATIONS_MAX_COUNT); // WAWebSyncdGatingUtils.getSyncdInlineMutationsMaxCount
    }

    /**
     * Returns the maximum size in kilobytes of a patch protobuf before
     * requiring external blob upload.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdGatingUtils.getSyncdPatchProtobufMaxSize}:
     * reads the {@code syncd_patch_protobuf_max_size} AB prop via
     * {@code getABPropConfigValue}. The AB prop is of type {@code int}
     * with a default value of {@code 10}. Used by
     * {@code WAWebSyncdMMSUpload.exceedPatchProtobufSize} with clamping
     * between 10 and 100, then multiplied by 1000 for bytes.
     *
     * @param abPropsService the AB props service to read the configuration from
     * @return the maximum patch protobuf size (in kilobytes) from the AB prop
     * @implNote WAWebSyncdGatingUtils.getSyncdPatchProtobufMaxSize (function c)
     */
    public static int getSyncdPatchProtobufMaxSize(ABPropsService abPropsService) {
        return abPropsService.getInt(ABProp.SYNCD_PATCH_PROTOBUF_MAX_SIZE); // WAWebSyncdGatingUtils.getSyncdPatchProtobufMaxSize
    }

    /**
     * Returns how many days to wait for a missing sync key before giving up.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdGatingUtils.getSyncdWaitForKeyTimeoutDays}:
     * reads the {@code syncd_wait_for_key_timeout_days} AB prop via
     * {@code getABPropConfigValue}. The AB prop is of type {@code int}
     * with a default value of {@code 7}. Used by
     * {@code WAWebSyncdStoreMissingKeys} to calculate the missing key timeout.
     *
     * @param abPropsService the AB props service to read the configuration from
     * @return the wait-for-key timeout in days from the AB prop
     * @implNote WAWebSyncdGatingUtils.getSyncdWaitForKeyTimeoutDays (function d)
     */
    public static int getSyncdWaitForKeyTimeoutDays(ABPropsService abPropsService) {
        return abPropsService.getInt(ABProp.SYNCD_WAIT_FOR_KEY_TIMEOUT_DAYS); // WAWebSyncdGatingUtils.getSyncdWaitForKeyTimeoutDays
    }

    /**
     * Returns whether sync key persistence should wait for server
     * acknowledgment before storing the key locally.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdGatingUtils.getEnableSyncdKeyPersistenceOnlyAfterServerAck}:
     * reads the {@code wa_web_enable_syncd_key_persistence_only_after_server_ack}
     * AB prop via {@code getABPropConfigValue}. The AB prop is of type
     * {@code bool} with a default value of {@code false} (server default
     * {@code true}). Used by {@code WAWebSyncdKeyManagement} to determine
     * the order of key storage vs key sharing.
     *
     * @param abPropsService the AB props service to read the configuration from
     * @return {@code true} if key persistence should wait for server ACK
     * @implNote WAWebSyncdGatingUtils.getEnableSyncdKeyPersistenceOnlyAfterServerAck (function m)
     */
    public static boolean getEnableSyncdKeyPersistenceOnlyAfterServerAck(ABPropsService abPropsService) {
        return abPropsService.getBool(ABProp.WA_WEB_ENABLE_SYNCD_KEY_PERSISTENCE_ONLY_AFTER_SERVER_ACK); // WAWebSyncdGatingUtils.getEnableSyncdKeyPersistenceOnlyAfterServerAck
    }
}
