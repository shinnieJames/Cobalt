package com.github.auties00.cobalt.sync.key;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKey;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyId;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collection;

/**
 * Pure-static helper for the byte-level conversions and gating-AB-prop accessors used
 * across the syncd subsystem.
 *
 * <p>Two responsibilities sit here. Buffer-level helpers (combine, split, hex round-trip,
 * 64-bit network-order encoding, key-id structure parsing) translate the
 * {@code WAWebSyncdCryptoUtils} and {@code WASyncdKeyManagementUtils} primitives into Java.
 * AB-prop accessors expose the syncd gating values
 * ({@code syncd_key_max_use_days}, {@code syncd_inline_mutations_max_count}, etc.) used by
 * the rotation, request-builder, and timeout flows.
 *
 * <p>A sync key id is a 6-byte big-endian buffer:
 * <ul>
 * <li>Bytes 0-1 ({@code uint16}): device id of the key creator
 * <li>Bytes 2-5 ({@code uint32}): key epoch (monotonically increasing)
 * </ul>
 *
 * @apiNote
 * No state is held; every method is a pure function of its arguments. The class is
 * deliberately {@code final} with a private constructor to make that property explicit.
 */
@WhatsAppWebModule(moduleName = "WAWebSyncdCryptoUtils")
@WhatsAppWebModule(moduleName = "WASyncdKeyManagementUtils")
@WhatsAppWebModule(moduleName = "WAWebSyncdGatingUtils")
@WhatsAppWebModule(moduleName = "WAWebSyncdRotateKey")
@WhatsAppWebModule(moduleName = "WAWebSyncdKeyManagement")
public final class SyncKeyUtils {
    /**
     * The byte length of a sync key id ({@code uint16} device id + {@code uint32} epoch).
     */
    private static final int KEY_ID_LENGTH = 6;

    /**
     * Prevents instantiation of the static helper.
     */
    private SyncKeyUtils() {
    }

    /**
     * Concatenates the supplied byte arrays into a single fresh array.
     *
     * @apiNote
     * Translation of WA Web's {@code WAWebSyncdCryptoUtils.combine}, used to build the HMAC
     * input for the {@code valueMac}/{@code patchMac}/{@code snapshotMac} computations and
     * the IV-prefixed ciphertext layouts. Throws on an empty argument list (mirroring the
     * WA Web {@code throw err("buffers length is zero")} branch).
     *
     * @implNote
     * This implementation returns the sole input verbatim when {@code buffers.length == 1}
     * (no allocation), matching WA Web's identity short-circuit. For multi-input calls a
     * single pass computes the total length and a second copies in.
     *
     * @param buffers the byte arrays to concatenate
     * @return a single byte array containing the inputs in order
     * @throws IllegalArgumentException when {@code buffers} is empty
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoUtils", exports = "combine", adaptation = WhatsAppAdaptation.DIRECT)
    public static byte[] combine(byte[]... buffers) {
        if (buffers.length == 0) {
            throw new IllegalArgumentException("buffers length is zero");
        }

        if (buffers.length == 1) {
            return buffers[0];
        }

        var totalLength = 0;
        for (var buffer : buffers) {
            totalLength += buffer.length;
        }

        var result = new byte[totalLength];
        var offset = 0;
        for (var buffer : buffers) {
            System.arraycopy(buffer, 0, result, offset, buffer.length);
            offset += buffer.length;
        }
        return result;
    }

    /**
     * Slices a byte array three ways at the given offsets.
     *
     * @apiNote
     * Translation of WA Web's {@code WAWebSyncdCryptoUtils.split}, used to peel apart the
     * IV-then-ciphertext-then-MAC layout in the syncd record decrypt path. The returned
     * array is always length 3 in {@code [prefix, middle, suffix]} order.
     *
     * @implNote
     * This implementation uses {@link Arrays#copyOfRange(byte[], int, int)} so each slice is
     * an independent copy; WA Web's slice variant returns aliasing
     * {@code ArrayBuffer.slice} views that are also detached from the original.
     *
     * @param buffer the byte array to split
     * @param offset the start offset of the middle segment
     * @param length the length of the middle segment
     * @return a three-element array in {@code [prefix, middle, suffix]} order
     * @throws IllegalArgumentException when {@code offset} or {@code length} is negative
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoUtils", exports = "split", adaptation = WhatsAppAdaptation.DIRECT)
    public static byte[][] split(byte[] buffer, int offset, int length) {
        if (offset < 0 || length < 0) {
            throw new IllegalArgumentException("buffers length is zero");
        }

        var result = new byte[3][];
        result[0] = Arrays.copyOfRange(buffer, 0, offset);
        result[1] = Arrays.copyOfRange(buffer, offset, offset + length);
        result[2] = Arrays.copyOfRange(buffer, offset + length, buffer.length);
        return result;
    }

    /**
     * Parses a space-separated hex string back into a byte array.
     *
     * @apiNote
     * The reverse direction of {@link #syncKeyIdToHex(byte[])}; both are kept in sync so a
     * round-trip {@code hexToUint8Array(syncKeyIdToHex(x))} preserves the bytes exactly.
     *
     * @implNote
     * This implementation parses each token with radix 16 via
     * {@link Integer#parseInt(String, int)}; WA Web uses the equivalent
     * {@code parseInt(e, 16)}.
     *
     * @param hex the space-separated hex string (e.g. {@code "a 1f 0"})
     * @return the decoded byte array
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoUtils", exports = "hexToUint8Array", adaptation = WhatsAppAdaptation.DIRECT)
    public static byte[] hexToUint8Array(String hex) {
        var parts = hex.split(" ");
        var result = new byte[parts.length];
        for (var i = 0; i < parts.length; i++) {
            result[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return result;
    }

    /**
     * Renders a sync key id as a space-separated unpadded hex string for diagnostics.
     *
     * @apiNote
     * Used by every {@code syncd: ...} log line that mentions a key id. Returns the
     * sentinel {@code "unknown"} for {@code null} input so the diagnostic call sites can
     * stay short and the hex render never NPEs.
     *
     * @implNote
     * This implementation does not zero-pad each byte: the canonical WA Web format is the
     * unpadded form (e.g. {@code "a 1f 0"}, not {@code "0a 1f 00"}); for the padded form
     * use {@link #arrayBufferToHexPadded(byte[])} instead. Empty input renders as the empty
     * string.
     *
     * @param keyId the raw key id bytes
     * @return the space-separated unpadded hex string, or {@code "unknown"} for {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoUtils", exports = "syncKeyIdToHex", adaptation = WhatsAppAdaptation.DIRECT)
    public static String syncKeyIdToHex(byte[] keyId) {
        if (keyId == null) {
            return "unknown";
        }

        var sb = new StringBuilder();
        for (var i = 0; i < keyId.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(Integer.toHexString(keyId[i] & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Renders the key id of an {@link AppStateSyncKey} as a space-separated unpadded hex
     * string.
     *
     * @apiNote
     * Convenience overload used by call sites that already hold the
     * {@link AppStateSyncKey} wrapper rather than the raw bytes; unwraps the
     * {@link Optional} chain and delegates to {@link #syncKeyIdToHex(byte[])}.
     *
     * @param key the sync key
     * @return the hex string, or {@code "unknown"} when the key id is absent
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoUtils", exports = "syncKeyIdToHex", adaptation = WhatsAppAdaptation.ADAPTED)
    public static String syncKeyIdToHex(AppStateSyncKey key) {
        var keyIdBytes = key.keyId()
                .flatMap(AppStateSyncKeyId::keyId)
                .orElse(null);
        return syncKeyIdToHex(keyIdBytes);
    }

    /**
     * Renders a byte array as a zero-padded two-character-per-byte hex string with no
     * separator.
     *
     * @apiNote
     * The padded form used by HMAC diagnostic logs (the
     * {@code WAWebSyncdAntiTamperingLtHash} hex dumps) where adjacent bytes must remain
     * unambiguous; for the space-separated diagnostic form use
     * {@link #syncKeyIdToHex(byte[])}.
     *
     * @implNote
     * This implementation pre-sizes the {@link StringBuilder} to {@code data.length * 2} to
     * avoid a re-allocation; null and empty inputs render as the empty string.
     *
     * @param data the byte array to render
     * @return the zero-padded hex string (e.g. {@code "0a1f00"})
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoUtils", exports = "arrayBufferToHexPadded", adaptation = WhatsAppAdaptation.DIRECT)
    public static String arrayBufferToHexPadded(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }

        var sb = new StringBuilder(data.length * 2);
        for (var b : data) {
            var hex = Integer.toHexString(b & 0xFF);
            if (hex.length() < 2) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    /**
     * Encodes a 32-bit unsigned value as an 8-byte big-endian buffer with the upper 4 bytes
     * left zero.
     *
     * @apiNote
     * Used to build the {@code valueMac} HMAC input for syncd patches: the
     * {@code WAWebSyncdAntiTamperingValueMac} algorithm requires the version field to occupy
     * the lower 4 bytes of an 8-byte big-endian slot.
     *
     * @implNote
     * This implementation writes only the low four bytes; the high four are left at the
     * zero-initialised default rather than re-zeroed, which is also what WA Web's
     * {@code DataView.setUint32(4, e, false)} achieves on a fresh {@code ArrayBuffer(8)}.
     *
     * @param value the value to encode (treated as unsigned 32-bit)
     * @return the 8-byte big-endian buffer
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoUtils", exports = "to64BitNetworkOrder", adaptation = WhatsAppAdaptation.DIRECT)
    public static byte[] to64BitNetworkOrder(long value) {
        var buffer = new byte[8];
        buffer[4] = (byte) (value >> 24);
        buffer[5] = (byte) (value >> 16);
        buffer[6] = (byte) (value >> 8);
        buffer[7] = (byte) value;
        return buffer;
    }

    /**
     * Compares two sync key ids in constant time.
     *
     * @apiNote
     * Used wherever the rotation flow gates an action on whether two key references match
     * (e.g. "should this entry be rotated to the latest key?"). Two {@code null} inputs
     * compare equal, mirroring the WA Web {@code Optional.equals} short-circuit at the
     * caller site.
     *
     * @implNote
     * This implementation delegates to {@link MessageDigest#isEqual(byte[], byte[])} for
     * the constant-time guarantee; WA Web routes through
     * {@code WACryptoPrimitives.verify} which provides the same property in the browser.
     *
     * @param keyId1 the first key id bytes
     * @param keyId2 the second key id bytes
     * @return {@code true} when the byte arrays are byte-equal
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCryptoUtils", exports = "syncKeyIdsEqual", adaptation = WhatsAppAdaptation.DIRECT)
    public static boolean syncKeyIdsEqual(byte[] keyId1, byte[] keyId2) {
        if (keyId1 == null && keyId2 == null) {
            return true;
        }

        if (keyId1 == null || keyId2 == null) {
            return false;
        }

        return MessageDigest.isEqual(keyId1, keyId2);
    }

    /**
     * Returns the device id encoded in bytes 0-1 of a sync key id.
     *
     * @apiNote
     * Reads the {@code uint16} that identifies which device originally minted the key;
     * used by the rotation flow's "ties broken by lowest device id" rule in
     * {@link #findNewestKey(Collection)}.
     *
     * @param keyId the raw key id bytes (must be at least 6 bytes)
     * @return the device id, or {@code -1} when {@code keyId} is {@code null} or too short
     */
    @WhatsAppWebExport(moduleName = "WASyncdKeyManagementUtils", exports = "getKeyDeviceId", adaptation = WhatsAppAdaptation.ADAPTED)
    public static int getKeyDeviceId(byte[] keyId) {
        if (keyId == null || keyId.length < KEY_ID_LENGTH) {
            return -1;
        }
        return ByteBuffer.wrap(keyId).getShort(0) & 0xFFFF;
    }

    /**
     * Returns the key epoch encoded in bytes 2-5 of a sync key id.
     *
     * @apiNote
     * The epoch is the rotation counter; the newest key is the one with the highest epoch.
     * Cobalt's rotation logic compares epochs to pick the active key and to gate the
     * "rotate this entry to the latest key" decision in
     * {@link com.github.auties00.cobalt.sync.exchange.MutationRequestBuilder}.
     *
     * @param keyId the raw key id bytes (must be at least 6 bytes)
     * @return the key epoch, or {@code -1} when {@code keyId} is {@code null} or too short
     */
    public static int getKeyEpoch(byte[] keyId) {
        if (keyId == null || keyId.length < KEY_ID_LENGTH) {
            return -1;
        }
        return ByteBuffer.wrap(keyId).getInt(2);
    }

    /**
     * Returns the epoch of an {@link AppStateSyncKey}, unwrapping the
     * {@link Optional} chain.
     *
     * @apiNote
     * Convenience overload for call sites that already hold the {@link AppStateSyncKey}
     * wrapper.
     *
     * @param key the sync key
     * @return the epoch, or {@code -1} when the key id is absent or too short
     */
    public static int getKeyEpoch(AppStateSyncKey key) {
        return key.keyId()
                .flatMap(AppStateSyncKeyId::keyId)
                .map(SyncKeyUtils::getKeyEpoch)
                .orElse(-1);
    }

    /**
     * Returns the next epoch value for a key derived from the supplied predecessor.
     *
     * @apiNote
     * Called by {@link SyncKeyRotationService} to seed the new key's epoch slot during
     * rotation; the epoch is the only field the new key id changes monotonically so the
     * server can order rotations.
     *
     * @param keyId the current key id bytes
     * @return the next epoch value
     */
    public static int generateNewKeyEpoch(byte[] keyId) {
        return getKeyEpoch(keyId) + 1;
    }

    /**
     * Builds a 6-byte sync key id from a device id and key epoch.
     *
     * @apiNote
     * The inverse of {@link #getKeyDeviceId(byte[])} plus {@link #getKeyEpoch(byte[])}; used
     * by {@link SyncKeyRotationService} to mint the key id for a freshly rotated key.
     *
     * @implNote
     * This implementation uses {@link ByteBuffer} (default big-endian) so the wire layout
     * matches WA Web's {@code DataView.setUint16(0, deviceId)} followed by
     * {@code DataView.setUint32(2, epoch)}.
     *
     * @param deviceId the device id of the creator
     * @param keyEpoch the key epoch
     * @return the 6-byte key id
     */
    public static byte[] buildKeyId(int deviceId, int keyEpoch) {
        var buffer = ByteBuffer.allocate(KEY_ID_LENGTH);
        buffer.putShort((short) deviceId);
        buffer.putInt(keyEpoch);
        return buffer.array();
    }

    /**
     * Returns the newest key from the supplied collection: highest epoch first, then lowest
     * device id when epochs tie.
     *
     * @apiNote
     * Drives both {@link SyncKeyRotationService#getNewestKeyPair()} and
     * {@link com.github.auties00.cobalt.sync.exchange.MutationRequestBuilder}'s active-key
     * lookup. The lowest-device-id tiebreaker matches WA Web's
     * {@code Math.min(...getKeyDeviceId)} so independently-rotating clients converge on the
     * same winner deterministically.
     *
     * @implNote
     * This implementation skips entries without a key id rather than throwing, mirroring
     * WA Web's behaviour (the {@code .map(getKeyEpoch)} chain returns {@code NaN} for
     * malformed entries which is then excluded by the {@code Math.max} comparison).
     *
     * @param keys the available sync keys
     * @return the newest key, or {@code null} when the input is null/empty
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdKeyManagement",
            exports = "getNewestKeyPair",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static AppStateSyncKey findNewestKey(Collection<AppStateSyncKey> keys) {
        if (keys == null || keys.isEmpty()) {
            return null;
        }

        var maxEpoch = Integer.MIN_VALUE;
        for (var key : keys) {
            var epoch = getKeyEpoch(key);
            if (epoch > maxEpoch) {
                maxEpoch = epoch;
            }
        }

        AppStateSyncKey bestKey = null;
        var bestDeviceId = Integer.MAX_VALUE;
        for (var key : keys) {
            if (getKeyEpoch(key) != maxEpoch) {
                continue;
            }

            var keyIdBytes = key.keyId()
                    .flatMap(AppStateSyncKeyId::keyId)
                    .orElse(null);
            if (keyIdBytes == null) {
                continue;
            }

            var deviceId = getKeyDeviceId(keyIdBytes);
            if (deviceId < bestDeviceId) {
                bestDeviceId = deviceId;
                bestKey = key;
            }
        }

        return bestKey;
    }

    /**
     * Returns the configured maximum number of days a sync key may be used before
     * {@link SyncKeyRotationService} rotates it.
     *
     * @apiNote
     * The raw AB-prop value is returned without clamping; the rotation flow clamps it to
     * the {@code [1, 90]} window inline. The default value when the AB prop is unset is
     * {@code 30}.
     *
     * @param abPropsService the AB prop source
     * @return the configured {@code syncd_key_max_use_days} value
     */
    public static int getSyncdKeyMaxUseDays(ABPropsService abPropsService) {
        return abPropsService.getInt(ABProp.SYNCD_KEY_MAX_USE_DAYS);
    }

    /**
     * Returns the configured sentinel-mutation flush timeout in seconds.
     *
     * @apiNote
     * Used by the syncd logout path to bound how long the sentinel mutation flush waits
     * before giving up. The default value when the AB prop is unset is {@code 3}.
     *
     * @param abPropsService the AB prop source
     * @return the configured {@code syncd_sentinel_timeout_seconds} value
     */
    public static int getSyncdSentinelTimeoutSeconds(ABPropsService abPropsService) {
        return abPropsService.getInt(ABProp.SYNCD_SENTINEL_TIMEOUT_SECONDS);
    }

    /**
     * Returns the configured maximum number of mutations that may be sent inline in a single
     * patch before the request builder switches to an MMS upload.
     *
     * @apiNote
     * Read by {@link com.github.auties00.cobalt.sync.exchange.MutationRequestBuilder} and
     * clamped inline to {@code [100, 2000]}. The default value when the AB prop is unset
     * is {@code 100}.
     *
     * @param abPropsService the AB prop source
     * @return the configured {@code syncd_inline_mutations_max_count} value
     */
    public static int getSyncdInlineMutationsMaxCount(ABPropsService abPropsService) {
        return abPropsService.getInt(ABProp.SYNCD_INLINE_MUTATIONS_MAX_COUNT);
    }

    /**
     * Returns the configured maximum patch protobuf size in kilobytes before the request
     * builder switches to an MMS upload.
     *
     * @apiNote
     * Read by {@link com.github.auties00.cobalt.sync.exchange.MutationRequestBuilder} and
     * clamped inline to {@code [10, 100]} kilobytes (then multiplied by 1000 for bytes).
     * The default value when the AB prop is unset is {@code 10}.
     *
     * @param abPropsService the AB prop source
     * @return the configured {@code syncd_patch_protobuf_max_size} value
     */
    public static int getSyncdPatchProtobufMaxSize(ABPropsService abPropsService) {
        return abPropsService.getInt(ABProp.SYNCD_PATCH_PROTOBUF_MAX_SIZE);
    }

    /**
     * Returns the configured number of days
     * {@link MissingSyncKeyTimeoutScheduler} waits for a missing key before raising a fatal
     * sync exception.
     *
     * @apiNote
     * Read by {@link MissingSyncKeyTimeoutScheduler} for the wait-for-key timeout. The
     * default value when the AB prop is unset is {@code 7}.
     *
     * @param abPropsService the AB prop source
     * @return the configured {@code syncd_wait_for_key_timeout_days} value
     */
    public static int getSyncdWaitForKeyTimeoutDays(ABPropsService abPropsService) {
        return abPropsService.getInt(ABProp.SYNCD_WAIT_FOR_KEY_TIMEOUT_DAYS);
    }

    /**
     * Returns whether {@link SyncKeyRotationService} should wait for a server acknowledgment
     * before persisting a freshly rotated key.
     *
     * @apiNote
     * Gates the order of the rotation steps: when {@code true} the rotation broadcast is
     * sent first and the local store update only happens after the share returns. The
     * default value when the AB prop is unset is {@code false}; the server default is
     * {@code true}.
     *
     * @param abPropsService the AB prop source
     * @return {@code true} when key persistence should wait for server ACK
     */
    public static boolean getEnableSyncdKeyPersistenceOnlyAfterServerAck(ABPropsService abPropsService) {
        return abPropsService.getBool(ABProp.WA_WEB_ENABLE_SYNCD_KEY_PERSISTENCE_ONLY_AFTER_SERVER_ACK);
    }
}
