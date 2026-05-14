package com.github.auties00.cobalt.device.icdc;

import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.device.identity.ADVEncryptionType;
import com.github.auties00.cobalt.model.device.info.DeviceInfo;
import com.github.auties00.cobalt.model.device.info.DeviceList;
import com.github.auties00.cobalt.model.device.info.DeviceListBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.libsignal.key.SignalIdentityPublicKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link IcdcComputer}.
 *
 * <p>The static {@link IcdcComputer#computeIdentityHash} helper is purely
 * functional and is validated with property-style assertions (determinism,
 * order-independence, truncation, distinctness). The instance-level
 * {@link IcdcComputer#compute} flow is exercised against a temporary store
 * populated with synthetic {@link DeviceList} entries and identity keys.
 *
 * <p>Byte-equality KAT vectors against WA Web's
 * {@code WAWebIdentityIcdcApi.getICDCMetaFromDeviceRecord} will be added
 * once the live corpus includes an ICDC oracle capture; the structural
 * cases here cover the algorithm contract that holds regardless of which
 * exact bytes the live bundle emits.
 */
@DisplayName("IcdcComputer")
class IcdcComputerTest {
    private static final Jid SELF_PN = Jid.of("19254863482@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("83116928594056@lid");
    private static final Jid SELF_PN_DEVICE = Jid.of("19254863482:1@s.whatsapp.net");
    private static final Jid PEER = Jid.of("393495089819@s.whatsapp.net");

    private static byte[] key(int seed) {
        var rng = new Random(seed);
        var bytes = new byte[32];
        rng.nextBytes(bytes);
        return bytes;
    }

    private static SignalIdentityPublicKey identity(int seed) {
        return SignalIdentityPublicKey.ofDirect(key(seed));
    }

    private static DeviceList list(Jid userJid, List<DeviceInfo> devices) {
        return list(userJid, devices, Instant.now(), null, false);
    }

    private static DeviceList list(Jid userJid, List<DeviceInfo> devices, Instant timestamp,
                                   ADVEncryptionType accountType, boolean deleted) {
        return new DeviceListBuilder()
                .userJid(userJid)
                .devices(devices)
                .timestamp(timestamp)
                .deleted(deleted)
                .advAccountType(accountType)
                .currentIndex(0)
                .validIndexes(new java.util.LinkedHashSet<>())
                .build();
    }

    private static IcdcComputer newComputer(WhatsAppStore store, TestABPropsService props) {
        return new IcdcComputer(store, props);
    }

    @Nested
    @DisplayName("compute(userJid)")
    class Compute {
        @Test
        @DisplayName("returns empty when no device list is cached for the user")
        void emptyWhenNoDeviceList() {
            var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
            var props = TestABPropsService.builder().build();
            var icdc = newComputer(store, props);

            assertTrue(icdc.compute(PEER).isEmpty());
        }

        @Test
        @DisplayName("returns empty when the cached device list is marked as deleted")
        void emptyWhenDeleted() {
            var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
            var props = TestABPropsService.builder().build();
            var icdc = newComputer(store, props);

            store.addDeviceList(list(PEER, List.of(), Instant.now(), null, true));

            assertTrue(icdc.compute(PEER).isEmpty());
        }

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

        @Test
        @DisplayName("returns a populated keyHash when the device list has companions with known identity keys")
        void withCompanions() {
            var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
            var props = TestABPropsService.builder().build();
            var icdc = newComputer(store, props);

            var devices = List.of(DeviceInfo.ofE2EE(0, 0), DeviceInfo.ofE2EE(2, 1));
            store.addDeviceList(list(PEER, devices));

            // Register the identity key for the companion device only.
            var companion = Jid.of("393495089819:2@s.whatsapp.net");
            store.saveIdentity(companion.toSignalAddress(), identity(0xCAFE));

            var result = icdc.compute(PEER).orElseThrow();
            var keyHash = result.keyHash().orElseThrow(
                    () -> new AssertionError("should produce a keyHash when at least one identity is available"));
            assertTrue(keyHash.length >= 8,
                    "hash length should respect the MIN_HASH_LENGTH=8 floor");
        }

        @Test
        @DisplayName("self-ICDC always includes the local identity-key-pair public key")
        void selfIcdcIncludesOwnKey() {
            var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
            var props = TestABPropsService.builder().build();
            var icdc = newComputer(store, props);

            // Two devices, the second is "another one of my own devices".
            var devices = List.of(DeviceInfo.ofE2EE(0, 0), DeviceInfo.ofE2EE(2, 1));
            store.addDeviceList(list(SELF_PN, devices));

            // Don't register any remote identity. The self own-identity branch should still kick in.
            var result = icdc.compute(SELF_PN).orElseThrow();
            assertTrue(result.keyHash().isPresent(),
                    "self ICDC should include the local identity-key-pair public key even if no remote identity is cached");
        }

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

    @Nested
    @DisplayName("computeIdentityHash")
    class ComputeIdentityHash {
        @Test
        @DisplayName("is deterministic for identical input")
        void deterministic() {
            var keys = List.of(key(1), key(2), key(3));
            var first = IcdcComputer.computeIdentityHash(keys, 16);
            var second = IcdcComputer.computeIdentityHash(keys, 16);
            assertArrayEquals(first, second);
        }

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

        @Test
        @DisplayName("produces different hashes for different input sets")
        void differsForDifferentInputs() {
            var setA = IcdcComputer.computeIdentityHash(List.of(key(1), key(2)), 16);
            var setB = IcdcComputer.computeIdentityHash(List.of(key(1), key(3)), 16);
            assertFalse(java.util.Arrays.equals(setA, setB));
        }

        @Test
        @DisplayName("truncates to the requested length")
        void truncates() {
            var keys = List.of(key(0xDEAD));
            assertEquals(8, IcdcComputer.computeIdentityHash(keys, 8).length);
            assertEquals(16, IcdcComputer.computeIdentityHash(keys, 16).length);
            assertEquals(32, IcdcComputer.computeIdentityHash(keys, 32).length);
        }

        @Test
        @DisplayName("never exceeds the underlying SHA-256 length even when a larger length is requested")
        void cannotExceedSha256Length() {
            var keys = List.of(key(0xFACE));
            assertEquals(32, IcdcComputer.computeIdentityHash(keys, 64).length);
        }

        @Test
        @DisplayName("the empty input set still produces a fixed hash")
        void emptyInput() {
            var hash = IcdcComputer.computeIdentityHash(List.of(), 16);
            assertEquals(16, hash.length);
        }

        @Test
        @DisplayName("the truncated prefix is consistent across lengths (longer hashes contain shorter ones)")
        void prefixConsistency() {
            var keys = List.of(key(0xABCD));
            var short_ = IcdcComputer.computeIdentityHash(keys, 8);
            var long_ = IcdcComputer.computeIdentityHash(keys, 32);
            var prefix = java.util.Arrays.copyOf(long_, 8);
            assertArrayEquals(short_, prefix);
        }
    }

    @Nested
    @DisplayName("recent-timestamp gating")
    class RecentTimestampGating {
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

        @Test
        @DisplayName("primary-only list with a stale (>720h) timestamp drops the timestamp")
        void staleTimestampDropped() {
            var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
            var props = TestABPropsService.builder().build();
            var icdc = newComputer(store, props);

            var stale = Instant.now().minus(java.time.Duration.ofDays(40));
            store.addDeviceList(list(PEER, List.of(DeviceInfo.ofE2EE(0, 0)), stale, null, false));

            var result = icdc.compute(PEER).orElseThrow();
            assertTrue(result.timestamp().isEmpty(),
                    "stale primary-only list should produce an empty timestamp on the ICDC result");
        }
    }
}
