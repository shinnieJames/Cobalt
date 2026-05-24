package com.github.auties00.cobalt.sync.key;

import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKey;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyBuilder;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyDataBuilder;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyIdBuilder;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.TestABPropsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the byte-level translations and AB-prop accessors of {@link SyncKeyUtils} against
 * the WA Web reference behaviour.
 *
 * @apiNote
 * Every method in {@link SyncKeyUtils} is a pure function of its arguments, so every test
 * here uses deterministic input vectors:
 * <ul>
 * <li>buffer concat/split byte invariants
 * <li>hex round-trip in space-separated and zero-padded forms
 * <li>{@code to64BitNetworkOrder} layout (8 bytes, upper 4 zero, lower 4 big-endian)
 * <li>constant-time sync-key-id comparison
 * <li>sync-key-id structure: bytes 0-1 = device id, bytes 2-5 = epoch
 * <li>{@code findNewestKey} ordering: highest epoch first, then lowest device id
 * <li>AB-prop accessors propagate the configured value verbatim
 * </ul>
 *
 * @implNote
 * This implementation does not exercise any timer or executor; every test runs to
 * completion synchronously without any setup or teardown.
 */
@DisplayName("SyncKeyUtils")
class SyncKeyUtilsTest {

    /**
     * Tests for {@link SyncKeyUtils#combine(byte[]...)}.
     */
    @Nested
    @DisplayName("combine - concatenate ArrayBuffer-style")
    class Combine {
        /**
         * Asserts that an empty argument list raises the {@code "buffers length is zero"}
         * exception.
         */
        @Test
        @DisplayName("rejects empty input (\"buffers length is zero\")")
        void rejectsEmpty() {
            assertThrows(IllegalArgumentException.class, SyncKeyUtils::combine);
        }

        /**
         * Asserts that a single-buffer argument list returns the input array verbatim
         * (identity short-circuit, no copy).
         */
        @Test
        @DisplayName("single-buffer input is returned verbatim")
        void singleBufferReturnedVerbatim() {
            var input = new byte[]{1, 2, 3};
            assertTrue(input == SyncKeyUtils.combine(input),
                    "WAWebSyncdCryptoUtils.combine returns the sole element when length===1");
        }

        /**
         * Asserts that multi-buffer input is concatenated in order.
         */
        @Test
        @DisplayName("multi-buffer input is concatenated in order")
        void multiBufferConcatenation() {
            assertArrayEquals(
                    new byte[]{1, 2, 3, 4, 5, 6, 7, 8},
                    SyncKeyUtils.combine(new byte[]{1, 2}, new byte[]{3, 4, 5}, new byte[]{6, 7, 8}));
        }

        /**
         * Asserts that an empty middle buffer contributes no bytes to the output.
         */
        @Test
        @DisplayName("empty buffers in the middle contribute nothing")
        void emptyMiddleBufferAllowed() {
            assertArrayEquals(
                    new byte[]{1, 2, 3, 4},
                    SyncKeyUtils.combine(new byte[]{1, 2}, new byte[0], new byte[]{3, 4}));
        }
    }

    /**
     * Tests for {@link SyncKeyUtils#split(byte[], int, int)}.
     */
    @Nested
    @DisplayName("split - three-way slice at (offset, length)")
    class Split {
        /**
         * Asserts that the output ordering is prefix, middle, suffix.
         */
        @Test
        @DisplayName("returns prefix, middle, suffix in that order")
        void threeWaySplit() {
            var parts = SyncKeyUtils.split(new byte[]{1, 2, 3, 4, 5, 6}, 1, 3);
            assertArrayEquals(new byte[]{1},       parts[0], "prefix");
            assertArrayEquals(new byte[]{2, 3, 4}, parts[1], "middle");
            assertArrayEquals(new byte[]{5, 6},    parts[2], "suffix");
        }

        /**
         * Asserts that an offset of zero leaves the prefix empty.
         */
        @Test
        @DisplayName("offset=0 makes the prefix empty")
        void emptyPrefix() {
            var parts = SyncKeyUtils.split(new byte[]{1, 2, 3}, 0, 2);
            assertEquals(0, parts[0].length);
            assertArrayEquals(new byte[]{1, 2}, parts[1]);
            assertArrayEquals(new byte[]{3},     parts[2]);
        }

        /**
         * Asserts that an offset+length equal to the buffer length leaves the suffix
         * empty.
         */
        @Test
        @DisplayName("offset+length=buffer.length makes the suffix empty")
        void emptySuffix() {
            var parts = SyncKeyUtils.split(new byte[]{1, 2, 3}, 1, 2);
            assertArrayEquals(new byte[]{1},    parts[0]);
            assertArrayEquals(new byte[]{2, 3}, parts[1]);
            assertEquals(0, parts[2].length);
        }

        /**
         * Asserts that negative offset or length is rejected.
         */
        @Test
        @DisplayName("negative offset or length is rejected")
        void rejectsNegative() {
            assertThrows(IllegalArgumentException.class, () -> SyncKeyUtils.split(new byte[]{0}, -1, 1));
            assertThrows(IllegalArgumentException.class, () -> SyncKeyUtils.split(new byte[]{0}, 0, -1));
        }
    }

    /**
     * Tests for the hex round-trip helpers.
     */
    @Nested
    @DisplayName("hex round-trip - space-separated and zero-padded variants")
    class Hex {
        /**
         * Asserts that {@link SyncKeyUtils#hexToUint8Array(String)} parses
         * space-separated tokens.
         */
        @Test
        @DisplayName("hexToUint8Array parses space-separated tokens")
        void hexToUint8ArrayParses() {
            assertArrayEquals(new byte[]{0x0A, 0x1F, 0x00},
                    SyncKeyUtils.hexToUint8Array("a 1f 0"));
        }

        /**
         * Asserts that {@link SyncKeyUtils#syncKeyIdToHex(byte[])} emits the unpadded form.
         */
        @Test
        @DisplayName("syncKeyIdToHex emits space-separated, unpadded hex")
        void syncKeyIdToHexUnpadded() {
            assertEquals("a 1f 0",
                    SyncKeyUtils.syncKeyIdToHex(new byte[]{0x0A, 0x1F, 0x00}));
        }

        /**
         * Asserts that the {@code null} input renders as the {@code "unknown"} sentinel.
         */
        @Test
        @DisplayName("syncKeyIdToHex on null returns the \"unknown\" sentinel")
        void syncKeyIdToHexNull() {
            assertEquals("unknown", SyncKeyUtils.syncKeyIdToHex((byte[]) null));
        }

        /**
         * Asserts that empty bytes render as the empty string.
         */
        @Test
        @DisplayName("syncKeyIdToHex on empty bytes returns the empty string")
        void syncKeyIdToHexEmpty() {
            assertEquals("", SyncKeyUtils.syncKeyIdToHex(new byte[0]));
        }

        /**
         * Asserts that {@link SyncKeyUtils#arrayBufferToHexPadded(byte[])} emits zero-
         * padded two-character-per-byte hex with no separator.
         */
        @Test
        @DisplayName("arrayBufferToHexPadded emits zero-padded 2-char-per-byte hex with no separator")
        void arrayBufferToHexPaddedZeroPads() {
            assertEquals("0a1f00",
                    SyncKeyUtils.arrayBufferToHexPadded(new byte[]{0x0A, 0x1F, 0x00}));
        }

        /**
         * Asserts that null and empty inputs render as the empty string.
         */
        @Test
        @DisplayName("arrayBufferToHexPadded on null/empty returns the empty string")
        void arrayBufferToHexPaddedNullEmpty() {
            assertEquals("", SyncKeyUtils.arrayBufferToHexPadded(null));
            assertEquals("", SyncKeyUtils.arrayBufferToHexPadded(new byte[0]));
        }
    }

    /**
     * Tests for {@link SyncKeyUtils#to64BitNetworkOrder(long)}.
     */
    @Nested
    @DisplayName("to64BitNetworkOrder - 8 bytes, upper 4 zero, lower 4 big-endian uint32")
    class To64BitNetworkOrder {
        /**
         * Asserts that the zero value produces an all-zero buffer.
         */
        @Test
        @DisplayName("value=0 produces all zeros")
        void zero() {
            assertArrayEquals(new byte[8], SyncKeyUtils.to64BitNetworkOrder(0L));
        }

        /**
         * Asserts that the upper four bytes are zero even for non-zero values.
         */
        @Test
        @DisplayName("upper 4 bytes are always zero even for non-zero value")
        void upperFourBytesZero() {
            var bytes = SyncKeyUtils.to64BitNetworkOrder(0x01020304L);
            assertEquals(0, bytes[0]);
            assertEquals(0, bytes[1]);
            assertEquals(0, bytes[2]);
            assertEquals(0, bytes[3]);
        }

        /**
         * Asserts that the lower four bytes encode the value big-endian.
         */
        @Test
        @DisplayName("lower 4 bytes encode the value big-endian")
        void lowerFourBytesBigEndian() {
            assertArrayEquals(
                    new byte[]{0, 0, 0, 0, 0x01, 0x02, 0x03, 0x04},
                    SyncKeyUtils.to64BitNetworkOrder(0x01020304L));
        }

        /**
         * Asserts that {@code 0xFFFFFFFF} saturates the lower four bytes.
         */
        @Test
        @DisplayName("value 0xFFFFFFFF saturates the lower 4 bytes")
        void saturated() {
            assertArrayEquals(
                    new byte[]{0, 0, 0, 0,
                            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF},
                    SyncKeyUtils.to64BitNetworkOrder(0xFFFFFFFFL));
        }
    }

    /**
     * Tests for {@link SyncKeyUtils#syncKeyIdsEqual(byte[], byte[])}.
     */
    @Nested
    @DisplayName("syncKeyIdsEqual - constant-time byte comparison")
    class SyncKeyIdsEqual {
        /**
         * Asserts that two {@code null} arguments compare equal.
         */
        @Test
        @DisplayName("null + null is equal (both absent)")
        void bothNullEqual() {
            assertTrue(SyncKeyUtils.syncKeyIdsEqual(null, null));
        }

        /**
         * Asserts that mixing {@code null} with non-{@code null} compares unequal.
         */
        @Test
        @DisplayName("null + non-null is not equal")
        void oneNullNotEqual() {
            assertFalse(SyncKeyUtils.syncKeyIdsEqual(null, new byte[]{1}));
            assertFalse(SyncKeyUtils.syncKeyIdsEqual(new byte[]{1}, null));
        }

        /**
         * Asserts that byte-equal inputs compare equal.
         */
        @Test
        @DisplayName("equal bytes are equal")
        void equalBytes() {
            assertTrue(SyncKeyUtils.syncKeyIdsEqual(new byte[]{1, 2, 3}, new byte[]{1, 2, 3}));
        }

        /**
         * Asserts that differing-length inputs compare unequal.
         */
        @Test
        @DisplayName("differing length is not equal")
        void differentLength() {
            assertFalse(SyncKeyUtils.syncKeyIdsEqual(new byte[]{1, 2}, new byte[]{1, 2, 3}));
        }

        /**
         * Asserts that a single byte difference compares unequal.
         */
        @Test
        @DisplayName("differing single byte is not equal")
        void singleByteDifference() {
            assertFalse(SyncKeyUtils.syncKeyIdsEqual(new byte[]{1, 2, 3}, new byte[]{1, 2, 4}));
        }
    }

    /**
     * Tests for the key-id structure helpers
     * ({@link SyncKeyUtils#buildKeyId(int, int)},
     * {@link SyncKeyUtils#getKeyDeviceId(byte[])},
     * {@link SyncKeyUtils#getKeyEpoch(byte[])}, etc.).
     */
    @Nested
    @DisplayName("key id structure - 2-byte deviceId | 4-byte epoch")
    class KeyIdStructure {
        /**
         * Asserts the 6-byte big-endian layout produced by
         * {@link SyncKeyUtils#buildKeyId(int, int)}.
         */
        @Test
        @DisplayName("buildKeyId(deviceId, epoch) produces a 6-byte big-endian key id")
        void buildKeyIdLayout() {
            var keyId = SyncKeyUtils.buildKeyId(0x0102, 0x03040506);
            assertEquals(6, keyId.length);
            assertEquals((byte) 0x01, keyId[0]);
            assertEquals((byte) 0x02, keyId[1]);
            assertEquals((byte) 0x03, keyId[2]);
            assertEquals((byte) 0x04, keyId[3]);
            assertEquals((byte) 0x05, keyId[4]);
            assertEquals((byte) 0x06, keyId[5]);
        }

        /**
         * Asserts that {@link SyncKeyUtils#getKeyDeviceId(byte[])} reads bytes 0-1 as a
         * big-endian {@code uint16}.
         */
        @Test
        @DisplayName("getKeyDeviceId reads bytes [0,2) as a big-endian uint16")
        void getKeyDeviceIdReadsBytesZeroOne() {
            var keyId = SyncKeyUtils.buildKeyId(0xABCD, 0x00000001);
            assertEquals(0xABCD, SyncKeyUtils.getKeyDeviceId(keyId));
        }

        /**
         * Asserts that {@link SyncKeyUtils#getKeyEpoch(byte[])} reads bytes 2-5 as a
         * big-endian {@code uint32}.
         */
        @Test
        @DisplayName("getKeyEpoch reads bytes [2,6) as a big-endian uint32")
        void getKeyEpochReadsBytesTwoToFive() {
            var keyId = SyncKeyUtils.buildKeyId(0x0001, 0x12345678);
            assertEquals(0x12345678, SyncKeyUtils.getKeyEpoch(keyId));
        }

        /**
         * Asserts that {@link SyncKeyUtils#getKeyDeviceId(byte[])} returns {@code -1} on
         * {@code null} and short input.
         */
        @Test
        @DisplayName("getKeyDeviceId on a malformed (short) key id returns -1")
        void getKeyDeviceIdMalformed() {
            assertEquals(-1, SyncKeyUtils.getKeyDeviceId(null));
            assertEquals(-1, SyncKeyUtils.getKeyDeviceId(new byte[5]));
        }

        /**
         * Asserts that {@link SyncKeyUtils#getKeyEpoch(byte[])} returns {@code -1} on
         * {@code null} and short input.
         */
        @Test
        @DisplayName("getKeyEpoch on a malformed (short) key id returns -1")
        void getKeyEpochMalformed() {
            assertEquals(-1, SyncKeyUtils.getKeyEpoch((byte[]) null));
            assertEquals(-1, SyncKeyUtils.getKeyEpoch(new byte[5]));
        }

        /**
         * Asserts that {@link SyncKeyUtils#generateNewKeyEpoch(byte[])} adds one to the
         * parsed epoch.
         */
        @Test
        @DisplayName("generateNewKeyEpoch increments the parsed epoch by one")
        void generateNewKeyEpochIncrements() {
            var keyId = SyncKeyUtils.buildKeyId(1, 42);
            assertEquals(43, SyncKeyUtils.generateNewKeyEpoch(keyId));
        }

        /**
         * Asserts that the {@link AppStateSyncKey} overload of
         * {@link SyncKeyUtils#getKeyEpoch(AppStateSyncKey)} unwraps the {@link java.util.Optional}
         * chain.
         */
        @Test
        @DisplayName("getKeyEpoch on an AppStateSyncKey unwraps via the Optional<byte[]> chain")
        void getKeyEpochOnSyncKey() {
            var key = syncKey(SyncKeyUtils.buildKeyId(7, 99), new byte[32]);
            assertEquals(99, SyncKeyUtils.getKeyEpoch(key));
        }

        /**
         * Asserts that the {@link AppStateSyncKey} overload returns {@code -1} when the
         * key id is absent.
         */
        @Test
        @DisplayName("getKeyEpoch on an AppStateSyncKey without an id returns -1")
        void getKeyEpochOnSyncKeyMissingId() {
            var key = new AppStateSyncKeyBuilder().build();
            assertEquals(-1, SyncKeyUtils.getKeyEpoch(key));
        }
    }

    /**
     * Tests for {@link SyncKeyUtils#findNewestKey(java.util.Collection)}.
     */
    @Nested
    @DisplayName("findNewestKey - highest epoch first, then lowest device id")
    class FindNewestKey {
        /**
         * Asserts that {@code null} or empty input returns {@code null}.
         */
        @Test
        @DisplayName("returns null on null/empty input")
        void emptyReturnsNull() {
            assertNull(SyncKeyUtils.findNewestKey(null));
            assertNull(SyncKeyUtils.findNewestKey(List.of()));
        }

        /**
         * Asserts that a single-key collection returns that key.
         */
        @Test
        @DisplayName("single-key collection returns that key")
        void singleKeyReturnsItself() {
            var key = syncKey(SyncKeyUtils.buildKeyId(1, 1), new byte[32]);
            assertNotNull(SyncKeyUtils.findNewestKey(List.of(key)));
        }

        /**
         * Asserts that the higher epoch wins when epochs differ.
         */
        @Test
        @DisplayName("higher epoch wins")
        void higherEpochWins() {
            var older = syncKey(SyncKeyUtils.buildKeyId(1, 1), new byte[32]);
            var newer = syncKey(SyncKeyUtils.buildKeyId(1, 5), new byte[32]);
            var newest = SyncKeyUtils.findNewestKey(List.of(older, newer));
            assertEquals(5, SyncKeyUtils.getKeyEpoch(newest));
        }

        /**
         * Asserts that the lowest device id wins on an epoch tie.
         */
        @Test
        @DisplayName("on epoch tie, lowest device id wins")
        void epochTieLowestDeviceWins() {
            var high = syncKey(SyncKeyUtils.buildKeyId(5, 10), new byte[32]);
            var low  = syncKey(SyncKeyUtils.buildKeyId(1, 10), new byte[32]);
            var winner = SyncKeyUtils.findNewestKey(List.of(high, low));
            var winnerBytes = winner.keyId().orElseThrow().keyId().orElseThrow();
            assertEquals(1, SyncKeyUtils.getKeyDeviceId(winnerBytes));
        }

        /**
         * Asserts that an entry with no key id is silently skipped.
         */
        @Test
        @DisplayName("a key without an id is skipped (no exception, contributes nothing)")
        void keyWithoutIdSkipped() {
            var orphan = new AppStateSyncKeyBuilder().build();
            var key    = syncKey(SyncKeyUtils.buildKeyId(1, 1), new byte[32]);
            var winner = SyncKeyUtils.findNewestKey(List.of(orphan, key));
            assertNotNull(winner);
            assertEquals(1, SyncKeyUtils.getKeyEpoch(winner));
        }
    }

    /**
     * Tests for the AB-prop accessor helpers.
     */
    @Nested
    @DisplayName("AB-prop accessors - pass-through to ABPropsService")
    class AbPropAccessors {
        /**
         * Asserts that the {@code SYNCD_KEY_MAX_USE_DAYS} accessor returns the configured
         * value.
         */
        @Test
        @DisplayName("getSyncdKeyMaxUseDays reads SYNCD_KEY_MAX_USE_DAYS")
        void keyMaxUseDays() {
            var props = TestABPropsService.builder().build()
                    .set(ABProp.SYNCD_KEY_MAX_USE_DAYS, 60);
            assertEquals(60, SyncKeyUtils.getSyncdKeyMaxUseDays(props));
        }

        /**
         * Asserts that the {@code SYNCD_SENTINEL_TIMEOUT_SECONDS} accessor returns the
         * configured value.
         */
        @Test
        @DisplayName("getSyncdSentinelTimeoutSeconds reads SYNCD_SENTINEL_TIMEOUT_SECONDS")
        void sentinelTimeout() {
            var props = TestABPropsService.builder().build()
                    .set(ABProp.SYNCD_SENTINEL_TIMEOUT_SECONDS, 12);
            assertEquals(12, SyncKeyUtils.getSyncdSentinelTimeoutSeconds(props));
        }

        /**
         * Asserts that the {@code SYNCD_INLINE_MUTATIONS_MAX_COUNT} accessor returns the
         * configured value.
         */
        @Test
        @DisplayName("getSyncdInlineMutationsMaxCount reads SYNCD_INLINE_MUTATIONS_MAX_COUNT")
        void inlineMaxCount() {
            var props = TestABPropsService.builder().build()
                    .set(ABProp.SYNCD_INLINE_MUTATIONS_MAX_COUNT, 250);
            assertEquals(250, SyncKeyUtils.getSyncdInlineMutationsMaxCount(props));
        }

        /**
         * Asserts that the {@code SYNCD_PATCH_PROTOBUF_MAX_SIZE} accessor returns the
         * configured value.
         */
        @Test
        @DisplayName("getSyncdPatchProtobufMaxSize reads SYNCD_PATCH_PROTOBUF_MAX_SIZE")
        void protobufMaxSize() {
            var props = TestABPropsService.builder().build()
                    .set(ABProp.SYNCD_PATCH_PROTOBUF_MAX_SIZE, 50);
            assertEquals(50, SyncKeyUtils.getSyncdPatchProtobufMaxSize(props));
        }

        /**
         * Asserts that the {@code SYNCD_WAIT_FOR_KEY_TIMEOUT_DAYS} accessor returns the
         * configured value.
         */
        @Test
        @DisplayName("getSyncdWaitForKeyTimeoutDays reads SYNCD_WAIT_FOR_KEY_TIMEOUT_DAYS")
        void waitForKeyTimeoutDays() {
            var props = TestABPropsService.builder().build()
                    .set(ABProp.SYNCD_WAIT_FOR_KEY_TIMEOUT_DAYS, 14);
            assertEquals(14, SyncKeyUtils.getSyncdWaitForKeyTimeoutDays(props));
        }

        /**
         * Asserts that the
         * {@code WA_WEB_ENABLE_SYNCD_KEY_PERSISTENCE_ONLY_AFTER_SERVER_ACK} accessor
         * returns the configured boolean.
         */
        @Test
        @DisplayName("getEnableSyncdKeyPersistenceOnlyAfterServerAck reads the boolean prop")
        void persistenceOnlyAfterAck() {
            var t = TestABPropsService.builder().build()
                    .set(ABProp.WA_WEB_ENABLE_SYNCD_KEY_PERSISTENCE_ONLY_AFTER_SERVER_ACK, true);
            var f = TestABPropsService.builder().build()
                    .set(ABProp.WA_WEB_ENABLE_SYNCD_KEY_PERSISTENCE_ONLY_AFTER_SERVER_ACK, false);
            assertTrue(SyncKeyUtils.getEnableSyncdKeyPersistenceOnlyAfterServerAck(t));
            assertFalse(SyncKeyUtils.getEnableSyncdKeyPersistenceOnlyAfterServerAck(f));
        }
    }

    /**
     * Builds an {@link AppStateSyncKey} from the supplied id and key data, with a default
     * timestamp.
     *
     * @apiNote
     * Helper used across the {@code KeyIdStructure} and {@code FindNewestKey} test groups.
     *
     * @param id the key id bytes
     * @param data the 32-byte key data
     * @return the built {@link AppStateSyncKey}
     */
    private static AppStateSyncKey syncKey(byte[] id, byte[] data) {
        return new AppStateSyncKeyBuilder()
                .keyId(new AppStateSyncKeyIdBuilder().keyId(id).build())
                .keyData(new AppStateSyncKeyDataBuilder().keyData(data).build())
                .build();
    }
}
