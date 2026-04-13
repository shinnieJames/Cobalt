package com.github.auties00.cobalt.device.icdc;

import com.github.auties00.cobalt.device.DeviceConstants;
import com.github.auties00.cobalt.model.device.identity.ADVEncryptionType;
import com.github.auties00.cobalt.model.device.info.DeviceInfo;
import com.github.auties00.cobalt.model.device.info.DeviceList;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Computes Identity Change Detection Consistency (ICDC) metadata for
 * a given user's device list.
 *
 * <p>ICDC metadata is attached to every outgoing message so that
 * recipients can detect changes in the sender's or recipient's device
 * list since the last key exchange.  The metadata includes a truncated
 * SHA-256 hash of all known identity keys, the device list timestamp,
 * and the key indexes of devices whose identity keys were available.
 *
 * @implNote WAWebIdentityIcdcApi: getICDCMeta, getICDCMetaFromDeviceRecord,
 * computeIdentityHash.
 */
public final class IcdcComputer {

    /**
     * Minimum hash length in bytes.
     *
     * @implNote WAWebIdentityIcdcApi: {@code var e = 8} used as lower bound
     * for {@code Math.max(configValue, e)}.
     */
    private static final int MIN_HASH_LENGTH = 8;

    /**
     * A device list timestamp is considered "recent" if it is within
     * this duration from the current time.
     *
     * @implNote WAWebIdentityIcdcApi: {@code var s = 720 * 60 * 60} seconds.
     */
    private static final Duration RECENT_THRESHOLD = Duration.ofHours(720);

    /**
     * The store providing access to device lists, identity keys, and session state.
     *
     * @implNote WAWebIdentityIcdcApi: accesses WAWebApiDeviceList, WAWebSignalProtocolStore,
     * and WAWebUserPrefsMeUser via module imports.
     */
    private final WhatsAppStore store;

    /**
     * The AB props service for reading feature flag configuration.
     *
     * @implNote WAWebIdentityIcdcApi: accesses WAWebABProps and WAWebBizCoexGatingUtils
     * via module imports.
     */
    private final ABPropsService abPropsService;

    /**
     * Constructs an ICDC computer with the given store and AB props service.
     *
     * @param store          the store providing access to device lists and identity keys
     * @param abPropsService the AB props service for reading feature flag configuration
     * @throws NullPointerException if any argument is {@code null}
     * @implNote WAWebIdentityIcdcApi: module-level imports of WAWebApiDeviceList,
     * WAWebSignalProtocolStore, WAWebUserPrefsMeUser, WAWebABProps, WAWebBizCoexGatingUtils.
     */
    public IcdcComputer(WhatsAppStore store, ABPropsService abPropsService) {
        this.store = Objects.requireNonNull(store, "store");
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService");
    }

    /**
     * Computes ICDC metadata for the given user.
     *
     * <p>Retrieves the device record for the user and delegates to
     * {@link #computeFromDeviceList(Jid, DeviceList)}.
     *
     * @param userJid the user JID (will be normalised to a user-level JID)
     * @return the ICDC result, or {@code Optional.empty()} if no device list is cached
     *         or the list is marked as deleted
     * @implNote WAWebIdentityIcdcApi.getICDCMeta: retrieves the device record
     * via {@code WAWebApiDeviceList.getDeviceRecord(e)} and delegates to
     * {@code getICDCMetaFromDeviceRecord}.
     */
    public Optional<IcdcResult> compute(Jid userJid) {
        // WAWebIdentityIcdcApi.getICDCMeta
        return store.findDeviceList(userJid.toUserJid())
                .filter(deviceList -> !deviceList.deleted())
                .map(deviceList -> computeFromDeviceList(userJid, deviceList));
    }

    /**
     * Computes ICDC metadata from an already-resolved device list.
     *
     * <p>Determines whether the user has companion devices, retrieves identity
     * keys for remote devices, optionally includes the sender's own identity key,
     * computes a truncated SHA-256 hash, and resolves hosted account type.
     *
     * @param userJid    the user JID
     * @param deviceList the resolved device list
     * @return the computed ICDC result, or {@code null} if the identity key pair
     *         is unavailable for the self user
     * @implNote WAWebIdentityIcdcApi.getICDCMetaFromDeviceRecord.
     */
    IcdcResult computeFromDeviceList(Jid userJid, DeviceList deviceList) {
        // WAWebIdentityIcdcApi.getICDCMetaFromDeviceRecord
        var devices = deviceList.devices();
        var timestamp = deviceList.timestamp();

        // WAWebIdentityIcdcApi: u = i.some(e => e.id !== DEFAULT_DEVICE_ID)
        var hasCompanionDevices = devices.stream()
                .anyMatch(d -> d.id() != DeviceConstants.PRIMARY_DEVICE_ID);

        byte[] keyHash = null;
        List<Integer> keyIndexes = null;

        if (hasCompanionDevices) {
            var selfJid = store.jid().orElse(null);
            var isSelf = selfJid != null && userJid.toUserJid().equals(selfJid.toUserJid());

            // WAWebIdentityIcdcApi: separate self device key index from remote devices
            // isMeDevice(n) checks exact device match (not just account)
            Integer selfKeyIndex = null;
            var remoteDevices = new ArrayList<DeviceInfo>();
            for (var device : devices) {
                var deviceJid = device.toDeviceJid(userJid.user(), userJid.server());
                if (deviceJid.equals(selfJid)) {
                    // WAWebIdentityIcdcApi: isMeDevice(n) ? m = t
                    selfKeyIndex = device.keyIndex();
                } else {
                    // WAWebIdentityIcdcApi: d.push([t, n])
                    remoteDevices.add(device);
                }
            }

            // WAWebIdentityIcdcApi: getAllIdentityKeysBytes for remote devices
            var identityKeys = new ArrayList<byte[]>();
            var includedKeyIndexes = new ArrayList<Integer>();
            for (var device : remoteDevices) {
                var deviceJid = device.toDeviceJid(userJid.user(), userJid.server());
                var identityKey = store.findIdentityByAddress(deviceJid.toSignalAddress()).orElse(null);
                if (identityKey != null) {
                    // WAWebIdentityIcdcApi: h.push(e), y.push(d[t][0])
                    identityKeys.add(identityKey.toEncodedPoint()); // ADAPTED: toEncodedPoint returns raw 32-byte key, equivalent to toCurveKeyPubKey stripping the 0x05 prefix from 33-byte Signal keys
                    includedKeyIndexes.add(device.keyIndex());
                }
            }

            // WAWebIdentityIcdcApi: if isMeAccount(e), include own identity key pair
            if (isSelf) {
                // WAWebIdentityIcdcApi: getIdentityKeyPair(); if (!C) return null
                // ADAPTED: Cobalt always has a valid identity key pair (initialized with random if absent)
                identityKeys.add(store.identityKeyPair().publicKey().toEncodedPoint());
                // WAWebIdentityIcdcApi: y.push(nullthrows(m)) — selfKeyIndex must be non-null
                if (selfKeyIndex != null) {
                    includedKeyIndexes.add(selfKeyIndex);
                }
            }

            // WAWebIdentityIcdcApi: n.keyHash = yield p(h.map(...toCurveKeyPubKey...), f())
            keyHash = computeIdentityHash(identityKeys, getHashLength());

            // WAWebIdentityIcdcApi: y.length !== i.length && (n.keyIndexes = y)
            if (includedKeyIndexes.size() != devices.size()) {
                keyIndexes = includedKeyIndexes;
            }
        }

        // WAWebIdentityIcdcApi: (u || g(s)) && (n.timestamp = s)
        var resultTimestamp = (hasCompanionDevices || isRecent(timestamp)) ? timestamp : null;

        // WAWebIdentityIcdcApi: hosted account type, gated by bizHostedDevicesEnabled
        // ADAPTED: WAWebIdentityIcdcApi.getICDCMetaFromDeviceRecord checks
        // getIsHostedMeAccount() for self (senderAccountType) and advAccountType
        // for non-self (receiverAccountType). Cobalt uses a single accountType field
        // since the caller (IcdcEnricher) maps sender/receiver based on which JID
        // was passed. For self, Cobalt checks advAccountType as a proxy for
        // getIsHostedMeAccount(), which is semantically equivalent for synced accounts.
        ADVEncryptionType accountType = null;
        if (isBizHostedDevicesEnabled()) {
            var selfJid = store.jid().orElse(null);
            var isSelf = selfJid != null && userJid.toUserJid().equals(selfJid.toUserJid());
            if (isSelf && deviceList.advAccountType() == ADVEncryptionType.HOSTED) {
                // WAWebIdentityIcdcApi: n.senderAccountType = HOSTED
                accountType = ADVEncryptionType.HOSTED;
            } else if (!isSelf && deviceList.advAccountType() == ADVEncryptionType.HOSTED) {
                // WAWebIdentityIcdcApi: n.receiverAccountType = HOSTED
                accountType = ADVEncryptionType.HOSTED;
            }
        }

        return new IcdcResult(keyHash, resultTimestamp, keyIndexes, accountType);
    }

    /**
     * Computes the truncated SHA-256 hash of sorted, concatenated identity keys.
     *
     * <p>The keys are first sorted lexicographically (unsigned byte comparison),
     * concatenated into a single binary blob, hashed with SHA-256, and truncated
     * to the specified length.
     *
     * @param identityKeys the list of raw 32-byte identity key points
     * @param hashLength   the desired output hash length in bytes
     * @return the truncated hash
     * @implNote WAWebIdentityIcdcApi.computeIdentityHash:
     * calls {@code identityKeysToBinary(curveKeys)} which sorts keys and concatenates,
     * then {@code sha256(binary).slice(0, hashLength)}.
     */
    static byte[] computeIdentityHash(List<byte[]> identityKeys, int hashLength) {
        // WAWebIdentityIcdcApi.computeIdentityHash / WAWebIdentityApiUtils.identityKeysToBinary
        try {
            // WAWebIdentityApiUtils.identityKeysToBinary: sorts keys lexicographically then concatenates
            var sorted = new ArrayList<>(identityKeys);
            sorted.sort(IcdcComputer::compareKeyBytes);

            var digest = MessageDigest.getInstance("SHA-256");
            for (var key : sorted) {
                digest.update(key);
            }
            var hash = digest.digest();

            // WAWebIdentityIcdcApi: sliceBytes(hash, 0, hashLength)
            var truncated = new byte[Math.min(hashLength, hash.length)];
            System.arraycopy(hash, 0, truncated, 0, truncated.length);
            return truncated;
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedOperationException("SHA-256 not available", e);
        }
    }

    /**
     * Compares two byte arrays lexicographically using unsigned byte comparison.
     *
     * <p>This matches the sort comparator used by
     * {@code WAWebIdentityApiUtils.identityKeysToBinary} for deterministic
     * key ordering before hashing.
     *
     * @param a the first byte array
     * @param b the second byte array
     * @return a negative value if {@code a} comes before {@code b}, zero if equal,
     *         or a positive value if {@code a} comes after {@code b}
     * @implNote WAWebIdentityApiUtils: {@code function e(e, t)} byte-by-byte
     * comparator with length tie-breaking.
     */
    private static int compareKeyBytes(byte[] a, byte[] b) {
        // WAWebIdentityApiUtils: function e(e, t) { for (n=0; n<e.length && n<t.length; ++n) if (e[n] !== t[n]) return e[n]-t[n]; return e.length-t.length }
        // JS byte values are unsigned (0-255), so use Byte.toUnsignedInt for comparison
        var minLength = Math.min(a.length, b.length);
        for (var i = 0; i < minLength; i++) {
            var cmp = Byte.toUnsignedInt(a[i]) - Byte.toUnsignedInt(b[i]);
            if (cmp != 0) {
                return cmp;
            }
        }
        return a.length - b.length;
    }

    /**
     * Returns the configured hash length, with a minimum of {@value MIN_HASH_LENGTH}.
     *
     * @return the effective hash length in bytes
     * @implNote WAWebIdentityIcdcApi: {@code function f()} reads
     * {@code getABPropConfigValue("md_icdc_hash_length")} and returns
     * {@code Math.max(configValue, 8)}.
     */
    private int getHashLength() {
        // WAWebIdentityIcdcApi.f
        return Math.max(
                abPropsService.getInt(ABProp.MD_ICDC_HASH_LENGTH),
                MIN_HASH_LENGTH
        );
    }

    /**
     * Returns whether the timestamp is within the recent threshold.
     *
     * @param timestamp the timestamp to check, or {@code null}
     * @return {@code true} if the timestamp is non-null and within {@link #RECENT_THRESHOLD}
     * @implNote WAWebIdentityIcdcApi: {@code function g(e)} checks
     * {@code unixTime() - timestamp < 720 * 60 * 60}.
     */
    private static boolean isRecent(Instant timestamp) {
        // WAWebIdentityIcdcApi.g
        return timestamp != null
                && Duration.between(timestamp, Instant.now()).compareTo(RECENT_THRESHOLD) < 0;
    }

    /**
     * Returns whether business hosted devices are enabled.
     *
     * @return {@code true} if the hosted devices feature gate is active
     * @implNote WAWebBizCoexGatingUtils.bizHostedDevicesEnabled.
     */
    private boolean isBizHostedDevicesEnabled() {
        // WAWebBizCoexGatingUtils.bizHostedDevicesEnabled
        return abPropsService.getBool(ABProp.ADV_ACCEPT_HOSTED_DEVICES);
    }
}
