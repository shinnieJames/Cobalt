package com.github.auties00.cobalt.device.icdc;

import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.device.identity.ADVEncryptionType;
import com.github.auties00.cobalt.model.device.info.DeviceInfo;
import com.github.auties00.cobalt.model.device.info.DeviceList;
import com.github.auties00.cobalt.model.device.info.DeviceListBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.libsignal.key.SignalIdentityPublicKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link IcdcComputer}.
 *
 * @apiNote
 * Exercises the algorithm contract that holds regardless of which exact bytes
 * the live bundle emits: the static {@link IcdcComputer#computeIdentityHash}
 * helper is purely functional and is validated with property-style assertions
 * (determinism, order-independence, truncation, distinctness); the
 * instance-level {@link IcdcComputer#compute} flow is exercised against a
 * temporary store populated with synthetic {@link DeviceList} entries and
 * identity keys.
 *
 * @implNote
 * This implementation seeds identity keys with deterministic
 * {@link Random#nextBytes(byte[])} runs so the tests are reproducible across
 * environments without committing fixture binaries. Byte-equality KAT vectors
 * against WA Web's {@code WAWebIdentityIcdcApi.getICDCMetaFromDeviceRecord}
 * will be added once the live corpus includes an ICDC oracle capture.
 */
@DisplayName("IcdcComputer")
class IcdcComputerTest {
    private static final Jid SELF_PN = Jid.of("19254863482@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594056@lid");
    private static final Jid SELF_PN_DEVICE = Jid.of("19254863482:1@s.whatsapp.net");
    private static final Jid PEER = Jid.of("393495089819@s.whatsapp.net");

    /**
     * Returns 32 deterministic bytes seeded from {@code seed}.
     *
     * @apiNote
     * Local test helper; used wherever a synthetic identity-key payload is
     * needed.
     *
     * @param seed the {@link Random} seed
     * @return the 32 bytes
     */
    private static byte[] key(int seed) {
        var rng = new Random(seed);
        var bytes = new byte[32];
        rng.nextBytes(bytes);
        return bytes;
    }

    /**
     * Returns a {@link SignalIdentityPublicKey} wrapping {@link #key(int)}.
     *
     * @apiNote
     * Local test helper used to plant synthetic identities into the store.
     *
     * @param seed the {@link Random} seed
     * @return the identity key
     */
    private static SignalIdentityPublicKey identity(int seed) {
        return SignalIdentityPublicKey.ofDirect(key(seed));
    }

    /**
     * Builds a device list with {@link Instant#now()} as the timestamp, no
     * account type, and not deleted.
     *
     * @apiNote
     * Local test helper used by most cases.
     *
     * @param userJid the user JID
     * @param devices the devices
     * @return the device list
     */
    private static DeviceList list(Jid userJid, List<DeviceInfo> devices) {
        return list(userJid, devices, Instant.now(), null, false);
    }

    /**
     * Builds a device list with the supplied timestamp, account type, and
     * deleted flag.
     *
     * @apiNote
     * Local test helper for cases that need to control account type or the
     * deleted marker.
     *
     * @param userJid     the user JID
     * @param devices     the devices
     * @param timestamp   the timestamp
     * @param accountType the account type, or {@code null}
     * @param deleted     the deleted flag
     * @return the device list
     */
    private static DeviceList list(Jid userJid, List<DeviceInfo> devices, Instant timestamp,
                                   ADVEncryptionType accountType, boolean deleted) {
        return new DeviceListBuilder()
                .userJid(userJid)
                .devices(devices)
                .timestamp(timestamp)
                .deleted(deleted)
                .advAccountType(accountType)
                .currentIndex(0)
                .validIndexes(new LinkedHashSet<>())
                .build();
    }

    /**
     * Constructs a fresh {@link IcdcComputer} bound to the supplied store and
     * AB props.
     *
     * @apiNote
     * Local test helper centralising the constructor call so each test reads
     * the assertion contract straight away.
     *
     * @param store the store
     * @param props the AB props
     * @return the constructed computer
     */
    private static IcdcComputer newComputer(WhatsAppStore store, TestABPropsService props) {
        return new IcdcComputer(store, props);
    }

    /**
     * Cases for {@link IcdcComputer#compute}.
     *
     * @apiNote
     * Grouped so the test report keeps each branch visible at a glance.
     */
    @Nested
    @DisplayName("compute(userJid)")
    class Compute {
        /**
         * Verifies the computer returns empty when no device list is cached
         * for the user.
         */
        @Test
        @DisplayName("returns empty when no device list is cached for the user")
        void emptyWhenNoDeviceList() {
            var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
            var props = TestABPropsService.builder().build();
            var icdc = newComputer(store, props);

            assertTrue(icdc.compute(PEER).isEmpty());
        }

        /**
         * Verifies the computer returns empty when the cached device list is
         * marked as deleted.
         */
        @Test
        @DisplayName("returns empty when the cached device list is marked as deleted")
        void emptyWhenDeleted() {
            var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
            var props = TestABPropsService.builder().build();
            var icdc = newComputer(store, props);

            store.addDeviceList(list(PEER, List.of(), Instant.now(), null, true));

            assertTrue(icdc.compute(PEER).isEmpty());
        }

        /**
         * Verifies a primary-only device list yields a timestamp but no
         * key hash.
         */
        @Test
        @DisplayName("returns a non-null timestamp but no keyHash for a primary-only device list")
        void primaryOnly() {
            var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
            var props = TestABPropsService.builder().build();
            var icdc = newComputer(store, props);

            var timestamp = Instant.now();
            store.addDeviceList(list(PEER, List.of(DeviceInfo.ofE2EE(0, 0)), timestamp, null, false));

            var result = icdc.compute(PEER).orElseThrow();
            assertTrue(result.keyHash().isEmpty(), "primary-only list has no companion identity keys to hash");
            assertEquals(timestamp, result.timestamp().orElseThrow(),
                    "timestamp should be propagated when it's the only signal");
        }

        /**
         * Verifies a device list with companions and a known identity key
         * yields a populated hash.
         *
         * @implNote
         * Only the companion device id 2 has its identity planted; the
         * primary contributes nothing because primaries are not part of the
         * hash for non-self users.
         */
        @Test
        @DisplayName("returns a populated keyHash when the device list has companions with known identity keys")
        void withCompanions() {
            var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
            var props = TestABPropsService.builder().build();
            var icdc = newComputer(store, props);

            var devices = List.of(DeviceInfo.ofE2EE(0, 0), DeviceInfo.ofE2EE(2, 1));
            store.addDeviceList(list(PEER, devices));

            var companion = Jid.of("393495089819:2@s.whatsapp.net");
            store.saveIdentity(companion.toSignalAddress(), identity(0xCAFE));

            var result = icdc.compute(PEER).orElseThrow();
            var keyHash = result.keyHash().orElseThrow(
                    () -> new AssertionError("should produce a keyHash when at least one identity is available"));
            assertTrue(keyHash.length >= 8,
                    "hash length should respect the MIN_HASH_LENGTH=8 floor");
        }

        /**
         * Verifies self ICDC includes the local identity-key-pair public key
         * even when no remote identity is cached.
         */
        @Test
        @DisplayName("self-ICDC always includes the local identity-key-pair public key")
        void selfIcdcIncludesOwnKey() {
            var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
            var props = TestABPropsService.builder().build();
            var icdc = newComputer(store, props);

            var devices = List.of(DeviceInfo.ofE2EE(0, 0), DeviceInfo.ofE2EE(2, 1));
            store.addDeviceList(list(SELF_PN, devices));

            var result = icdc.compute(SELF_PN).orElseThrow();
            assertTrue(result.keyHash().isPresent(),
                    "self ICDC should include the local identity-key-pair public key even if no remote identity is cached");
        }

        /**
         * Verifies the {@code MD_ICDC_HASH_LENGTH} AB prop selects the hash
         * length when above the eight-byte floor.
         */
        @Test
        @DisplayName("respects the MD_ICDC_HASH_LENGTH AB prop above the 8-byte floor")
        void respectsHashLengthProp() {
            var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
            var props = TestABPropsService.builder()
                    .with(ABProp.MD_ICDC_HASH_LENGTH, 16)
                    .build();
            var icdc = newComputer(store, props);

            var devices = List.of(DeviceInfo.ofE2EE(0, 0), DeviceInfo.ofE2EE(2, 1));
            store.addDeviceList(list(PEER, devices));
            var companion = Jid.of("393495089819:2@s.whatsapp.net");
            store.saveIdentity(companion.toSignalAddress(), identity(0xBEEF));

            var result = icdc.compute(PEER).orElseThrow();
            assertEquals(16, result.keyHash().orElseThrow().length,
                    "AB prop should select the hash length when it's above the floor");
        }

        /**
         * Verifies the hash length clamps up to the eight-byte floor when the
         * AB prop is smaller.
         */
        @Test
        @DisplayName("clamps the hash length up to the 8-byte floor when the AB prop is smaller")
        void clampsToFloor() {
            var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
            var props = TestABPropsService.builder()
                    .with(ABProp.MD_ICDC_HASH_LENGTH, 4)
                    .build();
            var icdc = newComputer(store, props);

            var devices = List.of(DeviceInfo.ofE2EE(0, 0), DeviceInfo.ofE2EE(2, 1));
            store.addDeviceList(list(PEER, devices));
            var companion = Jid.of("393495089819:2@s.whatsapp.net");
            store.saveIdentity(companion.toSignalAddress(), identity(0xBABE));

            var result = icdc.compute(PEER).orElseThrow();
            assertEquals(8, result.keyHash().orElseThrow().length,
                    "hash length should clamp up to the 8-byte floor");
        }

        /**
         * Verifies the hosted account type propagates when the
         * {@code adv_accept_hosted_devices} AB prop is on.
         */
        @Test
        @DisplayName("propagates the hosted account type when biz-hosted-devices is enabled")
        void hostedAccountType() {
            var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
            var props = TestABPropsService.builder()
                    .with(ABProp.ADV_ACCEPT_HOSTED_DEVICES, true)
                    .build();
            var icdc = newComputer(store, props);

            store.addDeviceList(list(PEER, List.of(DeviceInfo.ofE2EE(0, 0)), Instant.now(),
                    ADVEncryptionType.HOSTED, false));

            var result = icdc.compute(PEER).orElseThrow();
            assertEquals(ADVEncryptionType.HOSTED, result.accountType().orElse(null));
        }

        /**
         * Verifies the hosted account type is dropped when the
         * {@code adv_accept_hosted_devices} AB prop is off.
         */
        @Test
        @DisplayName("drops the hosted account type when biz-hosted-devices is disabled")
        void hostedAccountTypeDisabled() {
            var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
            var props = TestABPropsService.builder()
                    .with(ABProp.ADV_ACCEPT_HOSTED_DEVICES, false)
                    .build();
            var icdc = newComputer(store, props);

            store.addDeviceList(list(PEER, List.of(DeviceInfo.ofE2EE(0, 0)), Instant.now(),
                    ADVEncryptionType.HOSTED, false));

            var result = icdc.compute(PEER).orElseThrow();
            assertTrue(result.accountType().isEmpty());
        }
    }

    /**
     * Property-style cases for {@link IcdcComputer#computeIdentityHash}.
     *
     * @apiNote
     * Validates the pure-function contract: determinism, order-independence,
     * truncation, distinctness, prefix consistency.
     */
    @Nested
    @DisplayName("computeIdentityHash")
    class ComputeIdentityHash {
        /**
         * Verifies the hash is deterministic for identical input.
         */
        @Test
        @DisplayName("is deterministic for identical input")
        void deterministic() {
            var keys = List.of(key(1), key(2), key(3));
            var first = IcdcComputer.computeIdentityHash(keys, 16);
            var second = IcdcComputer.computeIdentityHash(keys, 16);
            assertArrayEquals(first, second);
        }

        /**
         * Verifies the hash is order-independent because the inputs are
         * sorted before hashing.
         */
        @Test
        @DisplayName("is order-independent (inputs are sorted before hashing)")
        void orderIndependent() {
            var a = key(1);
            var b = key(2);
            var c = key(3);
            var inOrder = IcdcComputer.computeIdentityHash(List.of(a, b, c), 16);
            var reversed = IcdcComputer.computeIdentityHash(List.of(c, b, a), 16);
            assertArrayEquals(inOrder, reversed);
        }

        /**
         * Verifies different input sets yield different hashes.
         */
        @Test
        @DisplayName("produces different hashes for different input sets")
        void differsForDifferentInputs() {
            var setA = IcdcComputer.computeIdentityHash(List.of(key(1), key(2)), 16);
            var setB = IcdcComputer.computeIdentityHash(List.of(key(1), key(3)), 16);
            assertFalse(Arrays.equals(setA, setB));
        }

        /**
         * Verifies the hash is truncated to the requested length.
         */
        @Test
        @DisplayName("truncates to the requested length")
        void truncates() {
            var keys = List.of(key(0xDEAD));
            assertEquals(8, IcdcComputer.computeIdentityHash(keys, 8).length);
            assertEquals(16, IcdcComputer.computeIdentityHash(keys, 16).length);
            assertEquals(32, IcdcComputer.computeIdentityHash(keys, 32).length);
        }

        /**
         * Verifies the hash length never exceeds the underlying SHA-256
         * length even when a larger length is requested.
         */
        @Test
        @DisplayName("never exceeds the underlying SHA-256 length even when a larger length is requested")
        void cannotExceedSha256Length() {
            var keys = List.of(key(0xFACE));
            assertEquals(32, IcdcComputer.computeIdentityHash(keys, 64).length);
        }

        /**
         * Verifies an empty input set still produces a fixed-length hash.
         */
        @Test
        @DisplayName("the empty input set still produces a fixed hash")
        void emptyInput() {
            var hash = IcdcComputer.computeIdentityHash(List.of(), 16);
            assertEquals(16, hash.length);
        }

        /**
         * Verifies longer hashes contain shorter ones as a prefix.
         */
        @Test
        @DisplayName("the truncated prefix is consistent across lengths (longer hashes contain shorter ones)")
        void prefixConsistency() {
            var keys = List.of(key(0xABCD));
            var short_ = IcdcComputer.computeIdentityHash(keys, 8);
            var long_ = IcdcComputer.computeIdentityHash(keys, 32);
            var prefix = Arrays.copyOf(long_, 8);
            assertArrayEquals(short_, prefix);
        }
    }

    /**
     * Cases for the recent-timestamp gating in
     * {@link IcdcComputer#computeFromDeviceList}.
     *
     * @apiNote
     * A primary-only list propagates its timestamp only when the timestamp
     * is within {@link IcdcComputer}'s 720-hour recent window.
     */
    @Nested
    @DisplayName("recent-timestamp gating")
    class RecentTimestampGating {
        /**
         * Verifies a primary-only list with a recent timestamp keeps the
         * timestamp on the result.
         */
        @Test
        @DisplayName("primary-only list with a recent timestamp keeps the timestamp on the result")
        void recentTimestampKept() {
            var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
            var props = TestABPropsService.builder().build();
            var icdc = newComputer(store, props);

            var recent = Instant.now();
            store.addDeviceList(list(PEER, List.of(DeviceInfo.ofE2EE(0, 0)), recent, null, false));

            var result = icdc.compute(PEER).orElseThrow();
            assertEquals(recent, result.timestamp().orElseThrow());
        }

        /**
         * Verifies a primary-only list with a stale timestamp drops the
         * timestamp.
         */
        @Test
        @DisplayName("primary-only list with a stale (>720h) timestamp drops the timestamp")
        void staleTimestampDropped() {
            var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
            var props = TestABPropsService.builder().build();
            var icdc = newComputer(store, props);

            var stale = Instant.now().minus(Duration.ofDays(40));
            store.addDeviceList(list(PEER, List.of(DeviceInfo.ofE2EE(0, 0)), stale, null, false));

            var result = icdc.compute(PEER).orElseThrow();
            assertTrue(result.timestamp().isEmpty(),
                    "stale primary-only list should produce an empty timestamp on the ICDC result");
        }
    }
}
