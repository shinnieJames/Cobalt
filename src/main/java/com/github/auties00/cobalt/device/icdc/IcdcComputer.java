package com.github.auties00.cobalt.device.icdc;

import com.github.auties00.cobalt.device.DeviceConstants;
import com.github.auties00.cobalt.model.auth.ADVEncryptionType;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
 * @apiNote WAWebIdentityIcdcApi: getICDCMeta, getICDCMetaFromDeviceRecord,
 * computeIdentityHash.
 */
public final class IcdcComputer {
    private static final System.Logger LOGGER = System.getLogger("IcdcComputer");

    /**
     * Minimum hash length in bytes.
     *
     * @apiNote WAWebIdentityIcdcApi: {@code var e = 8} used as lower bound
     * for {@code Math.max(configValue, e)}.
     */
    private static final int MIN_HASH_LENGTH = 8;

    /**
     * A device list timestamp is considered "recent" if it is within
     * this duration from the current time.
     *
     * @apiNote WAWebIdentityIcdcApi: {@code var s = 720 * 60 * 60} seconds.
     */
    private static final Duration RECENT_THRESHOLD = Duration.ofHours(720);

    private final WhatsAppStore store;
    private final ABPropsService abPropsService;

    public IcdcComputer(WhatsAppStore store, ABPropsService abPropsService) {
        this.store = Objects.requireNonNull(store, "store");
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService");
    }

    /**
     * Computes ICDC metadata for the given user.
     *
     * @param userJid the user JID (will be normalised to a user-level JID)
     * @return the ICDC result, or {@code Optional.empty()} if no device list is cached
     *         or the list is marked as deleted
     *
     * @apiNote WAWebIdentityIcdcApi.getICDCMeta: retrieves the device record
     * and delegates to {@code getICDCMetaFromDeviceRecord}.
     */
    public Optional<IcdcResult> compute(Jid userJid) {
        return store.findDeviceList(userJid.toUserJid())
                .filter(deviceList -> !deviceList.deleted())
                .map(deviceList -> computeFromDeviceList(userJid, deviceList));
    }

    /**
     * Computes ICDC metadata from an already-resolved device list.
     *
     * @apiNote WAWebIdentityIcdcApi.getICDCMetaFromDeviceRecord.
     */
    private IcdcResult computeFromDeviceList(Jid userJid, DeviceList deviceList) {
        var devices = deviceList.devices();
        var timestamp = deviceList.timestamp();

        // WAWebIdentityIcdcApi: hasMultipleDevices = devices.some(d => d.id !== DEFAULT_DEVICE_ID)
        var hasCompanionDevices = devices.stream()
                .anyMatch(d -> d.id() != DeviceConstants.PRIMARY_DEVICE_ID);

        byte[] keyHash = null;
        List<Integer> keyIndexes = null;

        if (hasCompanionDevices) {
            var selfJid = store.jid().orElse(null);
            var isSelf = selfJid != null && userJid.toUserJid().equals(selfJid.toUserJid());

            // WAWebIdentityIcdcApi: separate self device key index from remote devices
            Integer selfKeyIndex = null;
            var remoteDevices = new ArrayList<DeviceInfo>();
            for (var device : devices) {
                var deviceJid = device.toDeviceJid(userJid.user(), userJid.server());
                if (deviceJid.equals(selfJid)) {
                    selfKeyIndex = device.keyIndex();
                } else {
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
                    identityKeys.add(identityKey.toEncodedPoint());
                    includedKeyIndexes.add(device.keyIndex());
                }
            }

            // WAWebIdentityIcdcApi: if computing for self, include own identity key pair
            if (isSelf) {
                identityKeys.add(store.identityKeyPair().publicKey().toEncodedPoint());
                if (selfKeyIndex != null) {
                    includedKeyIndexes.add(selfKeyIndex);
                }
            }

            keyHash = computeIdentityHash(identityKeys, getHashLength());

            // WAWebIdentityIcdcApi: only set keyIndexes when not all devices were included
            if (includedKeyIndexes.size() != devices.size()) {
                keyIndexes = includedKeyIndexes;
            }
        }

        // WAWebIdentityIcdcApi: include timestamp if has companions or timestamp is recent
        var resultTimestamp = (hasCompanionDevices || isRecent(timestamp)) ? timestamp : null;

        // WAWebIdentityIcdcApi: hosted account type, gated by bizHostedDevicesEnabled
        ADVEncryptionType accountType = null;
        if (isBizHostedDevicesEnabled()) {
            var selfJid = store.jid().orElse(null);
            var isSelf = selfJid != null && userJid.toUserJid().equals(selfJid.toUserJid());
            if (isSelf && deviceList.advAccountType() == ADVEncryptionType.HOSTED) {
                accountType = ADVEncryptionType.HOSTED;
            } else if (!isSelf && deviceList.advAccountType() == ADVEncryptionType.HOSTED) {
                accountType = ADVEncryptionType.HOSTED;
            }
        }

        return new IcdcResult(keyHash, resultTimestamp, keyIndexes, accountType);
    }

    /**
     * Computes the truncated SHA-256 hash of concatenated identity keys.
     *
     * @apiNote WAWebIdentityIcdcApi.computeIdentityHash:
     * {@code sha256(identityKeysToBinary(curveKeys)).slice(0, hashLength)}.
     */
    private static byte[] computeIdentityHash(List<byte[]> identityKeys, int hashLength) {
        if (identityKeys.isEmpty()) {
            return null;
        }

        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (var key : identityKeys) {
                digest.digest(key);
            }
            var hash = digest.digest();
            var truncated = new byte[Math.min(hashLength, hash.length)];
            System.arraycopy(hash, 0, truncated, 0, truncated.length);
            return truncated;
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedOperationException("SHA-256 not available", e);
        }
    }

    /**
     * Returns the configured hash length, with a minimum of {@value MIN_HASH_LENGTH}.
     *
     * @apiNote WAWebIdentityIcdcApi: {@code Math.max(getABPropConfigValue("md_icdc_hash_length"), 8)}.
     */
    private int getHashLength() {
        return Math.max(
                abPropsService.getInt(ABProp.MD_ICDC_HASH_LENGTH),
                MIN_HASH_LENGTH
        );
    }

    /**
     * Returns whether the timestamp is within the recent threshold.
     *
     * @apiNote WAWebIdentityIcdcApi: {@code unixTime() - timestamp < 720 * 60 * 60}.
     */
    private static boolean isRecent(Instant timestamp) {
        return timestamp != null
                && Duration.between(timestamp, Instant.now()).compareTo(RECENT_THRESHOLD) < 0;
    }

    /**
     * Returns whether business hosted devices are enabled.
     *
     * @apiNote WAWebBizCoexGatingUtils.bizHostedDevicesEnabled.
     */
    private boolean isBizHostedDevicesEnabled() {
        return abPropsService.getBool(ABProp.BIZ_HOSTED_DEVICES_ENABLED);
    }
}
