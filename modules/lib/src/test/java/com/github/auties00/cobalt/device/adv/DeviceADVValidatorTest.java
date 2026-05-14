package com.github.auties00.cobalt.device.adv;

import com.github.auties00.cobalt.device.DeviceFixtures;
import com.github.auties00.cobalt.model.device.identity.ADVEncryptionType;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.libsignal.SignalProtocolAddress;
import com.github.auties00.libsignal.key.SignalIdentityPublicKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Byte-equality KAT against WA Web's
 * {@code WAWebHandleAdvDeviceNotificationUtils.decodeSignedKeyIndexBytes}.
 *
 * <p>The triple captured live (primary identity key, signed-key-index
 * bytes, and the decoded ValidatedKeyIndexListResult) lives in
 * {@code fixtures/device/adv-decode-self-oracle.expected.json}. The test
 * plants the primary identity into a temporary store and asserts
 * Cobalt's {@link DeviceADVValidator#decodeSignedKeyIndexBytes} produces
 * the same {@code rawId} / {@code timestamp} / {@code validIndexes} /
 * {@code currentIndex} / {@code accountType} the WA Web JS bundle did on
 * the same wire bytes.
 *
 * <p>Re-capture if WA Web changes the ADV protobuf schema or the
 * signature-verification algorithm.
 */
@DisplayName("DeviceADVValidator")
class DeviceADVValidatorTest {
    private static final Jid SELF_PN = Jid.of("393495089819@s.whatsapp.net");
    private static final Jid SELF_LID = Jid.of("258252122116273@lid");

    @Test
    @DisplayName("decodeSignedKeyIndexBytes byte-equality against WA Web oracle")
    void decodeMatchesWaWebOracle() {
        var oracleDoc = DeviceFixtures.loadExpected("adv-decode-self-oracle");
        var inner = com.alibaba.fastjson2.JSON.parseObject(
                oracleDoc.getJSONObject("result").getString("value"));

        var primaryKeyB64 = inner.getString("primaryIdentityKeyBase64");
        var signedB64 = inner.getString("signedKeyIndexBytesBase64");
        var expected = inner.getJSONObject("decoded");

        var primaryKey = Base64.getDecoder().decode(primaryKeyB64);
        var signedBytes = Base64.getDecoder().decode(signedB64);
        assertEquals(32, primaryKey.length, "primary identity key is exactly 32 bytes");

        var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        // Set the store's own JID to a companion device form so the validator's
        // findIdentityByAddress lookup at (user, 0) falls through to the
        // remoteIdentities map rather than short-circuiting to the temp store's
        // auto-generated own-identity-key-pair.
        store.setJid(Jid.of("393495089819:75@s.whatsapp.net"));

        var address = new SignalProtocolAddress(SELF_PN.user(), 0);
        store.saveIdentity(address, SignalIdentityPublicKey.ofDirect(primaryKey));

        // Sanity: round-trip the planted identity.
        var retrieved = store.findIdentityByAddress(address);
        assertTrue(retrieved.isPresent(), "planted identity should be retrievable");
        org.junit.jupiter.api.Assertions.assertArrayEquals(primaryKey, retrieved.get().toEncodedPoint(),
                "retrieved identity bytes should match planted bytes");

        var validator = new DeviceADVValidator(store, TestABPropsService.builder().build());
        var actual = validator.decodeSignedKeyIndexBytes(SELF_PN, signedBytes)
                .orElseThrow(() -> new AssertionError(
                        "Cobalt's decodeSignedKeyIndexBytes should succeed for the captured triple"));

        assertEquals(expected.getLongValue("rawId"), actual.rawId(),
                "rawId must match WA Web's oracle byte-for-byte");
        assertEquals(Instant.ofEpochSecond(expected.getLongValue("timestamp")), actual.timestamp(),
                "timestamp must match");
        assertEquals(expected.getIntValue("currentIndex"), actual.currentIndex(),
                "currentIndex must match");
        // accountType: 0 == E2EE in WA Web's enum
        assertEquals(ADVEncryptionType.E2EE, actual.accountType(),
                "captured account is E2EE (oracle reported accountType=0)");

        var oracleIndexes = expected.getJSONArray("validIndexes");
        assertEquals(oracleIndexes.size(), actual.validIndexes().size(),
                "validIndexes size must match");
        for (var i = 0; i < oracleIndexes.size(); i++) {
            assertTrue(actual.validIndexes().contains(oracleIndexes.getIntValue(i)),
                    "validIndex " + oracleIndexes.getIntValue(i) + " missing from Cobalt result");
        }
    }

    @Test
    @DisplayName("verifySKeyIndexWithAccSigKey: hosted-path verification using the embedded key")
    void hostedPathUsesEmbeddedKey() {
        var oracleDoc = DeviceFixtures.loadExpected("adv-decode-self-oracle");
        var inner = com.alibaba.fastjson2.JSON.parseObject(
                oracleDoc.getJSONObject("result").getString("value"));
        var signedBytes = Base64.getDecoder().decode(inner.getString("signedKeyIndexBytesBase64"));

        var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        var validator = new DeviceADVValidator(store, TestABPropsService.builder().build());

        // Hosted path: no need to plant an identity; the key comes from the outer protobuf.
        // For our captured non-hosted self bytes, accountSignatureKey is empty so this returns empty.
        var result = validator.verifySKeyIndexWithAccSigKey(signedBytes);
        // The captured self self-USync bytes don't carry an embedded accountSignatureKey
        // (that's a hosted-only field), so this returns empty.
        assertTrue(result.isEmpty(),
                "non-hosted signed-key-index has no embedded accountSignatureKey to verify against");
    }

    @Test
    @DisplayName("decodeSignedKeyIndexBytes returns empty when no identity is stored")
    void decodeMissingIdentityReturnsEmpty() {
        var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        store.setJid(Jid.of("393495089819:75@s.whatsapp.net"));
        // Do NOT plant an identity for (393495089819, 0) — the validator's lookup
        // should fall through to remoteIdentities (empty) and return Optional.empty().
        var validator = new DeviceADVValidator(store, TestABPropsService.builder().build());

        var oracleDoc = DeviceFixtures.loadExpected("adv-decode-self-oracle");
        var inner = com.alibaba.fastjson2.JSON.parseObject(
                oracleDoc.getJSONObject("result").getString("value"));
        var signedBytes = Base64.getDecoder().decode(inner.getString("signedKeyIndexBytesBase64"));

        var result = validator.decodeSignedKeyIndexBytes(SELF_PN, signedBytes);
        assertTrue(result.isEmpty(),
                "without a stored primary identity, decode must return Optional.empty() (signature can't be verified)");
    }

    @Test
    @DisplayName("decodeSignedKeyIndexBytes returns empty when the bytes are tampered")
    void decodeTamperedBytesReturnEmpty() {
        var oracleDoc = DeviceFixtures.loadExpected("adv-decode-self-oracle");
        var inner = com.alibaba.fastjson2.JSON.parseObject(
                oracleDoc.getJSONObject("result").getString("value"));
        var primaryKey = Base64.getDecoder().decode(inner.getString("primaryIdentityKeyBase64"));
        var signedBytes = Base64.getDecoder().decode(inner.getString("signedKeyIndexBytesBase64"));

        // Flip a single byte in the middle of the signature area
        signedBytes[signedBytes.length / 2] ^= 0x42;

        var store = DeviceFixtures.temporaryStore(SELF_PN, SELF_LID);
        store.setJid(Jid.of("393495089819:75@s.whatsapp.net"));
        store.saveIdentity(
                new SignalProtocolAddress(SELF_PN.user(), 0),
                SignalIdentityPublicKey.ofDirect(primaryKey));

        var validator = new DeviceADVValidator(store, TestABPropsService.builder().build());
        var result = validator.decodeSignedKeyIndexBytes(SELF_PN, signedBytes);
        assertTrue(result.isEmpty(),
                "tampered signed-key-index bytes must fail verification and return empty");
    }
}
