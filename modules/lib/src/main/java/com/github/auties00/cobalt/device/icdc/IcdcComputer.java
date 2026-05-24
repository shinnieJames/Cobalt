package com.github.auties00.cobalt.device.icdc;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.device.DeviceConstants;
import com.github.auties00.cobalt.model.device.identity.ADVEncryptionType;
import com.github.auties00.cobalt.model.device.info.DeviceInfo;
import com.github.auties00.cobalt.model.device.info.DeviceList;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Computes Identity Change Detection Consistency metadata for a user's device
 * list.
 *
 * @apiNote
 * The send pipeline calls {@link #compute(Jid)} once per outgoing message that
 * needs to embed a {@code deviceListMetadata} payload in its
 * {@code messageContextInfo}. Recipients compare the embedded hash, timestamp,
 * and key-index set against their own view of the participant's device list and,
 * when it disagrees, invalidate cached sessions and trigger a USync to refresh
 * the participant's devices. This defends against stale device lists that would
 * otherwise lead to undelivered or mis-encrypted messages.
 *
 * @implNote
 * This implementation derives identity-key serialisation and lexicographic
 * sorting from {@code WAWebIdentityApiUtils.identityKeysToBinary} and the
 * truncated SHA-256 hash from {@code WAWebIdentityIcdcApi.computeIdentityHash}.
 * Hash length is taken from the {@code md_icdc_hash_length} AB prop, clamped
 * to {@link #MIN_HASH_LENGTH} bytes.
 */
@WhatsAppWebModule(moduleName = "WAWebIdentityIcdcApi")
@WhatsAppWebModule(moduleName = "WAWebIdentityApiUtils")
public final class IcdcComputer {

    /**
     * Lower bound for the truncated hash length.
     *
     * @apiNote
     * Floor applied on top of the {@code md_icdc_hash_length} AB prop so a
     * server-side value smaller than eight bytes still produces a hash with
     * enough entropy to be useful.
     */
    @WhatsAppWebExport(moduleName = "WAWebIdentityIcdcApi",
            exports = "getICDCMetaFromDeviceRecord",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final int MIN_HASH_LENGTH = 8;

    /**
     * Window during which a device-list snapshot timestamp is considered recent
     * (720 hours).
     *
     * @apiNote
     * Used by {@link #isRecent(Instant)} to decide whether the snapshot
     * timestamp accompanies a primary-only ICDC result; pins the WA Web
     * constant {@code 720*60*60} seconds.
     */
    @WhatsAppWebExport(moduleName = "WAWebIdentityIcdcApi",
            exports = "getICDCMetaFromDeviceRecord",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static final Duration RECENT_THRESHOLD = Duration.ofHours(720);

    /**
     * The store providing device lists, identity keys, and session state.
     */
    private final WhatsAppStore store;

    /**
     * The AB props service used to read feature flags and the hash-length
     * configuration.
     */
    private final ABPropsService abPropsService;

    /**
     * Constructs a new ICDC computer.
     *
     * @apiNote
     * Wired up by the device-service construction graph; embedders do not
     * usually call this directly.
     *
     * @param store          the store
     * @param abPropsService the AB props service
     * @throws NullPointerException if any argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebIdentityIcdcApi",
            exports = {"getICDCMeta", "getICDCMetaFromDeviceRecord"},
            adaptation = WhatsAppAdaptation.ADAPTED)
    public IcdcComputer(WhatsAppStore store, ABPropsService abPropsService) {
        this.store = Objects.requireNonNull(store, "store");
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService");
    }

    /**
     * Computes ICDC metadata for the given user from the cached device list.
     *
     * @apiNote
     * The entry point the send pipeline calls; returns empty when no device
     * list is cached for the user or when the cached list is marked as
     * deleted. Either case is treated by the caller as "do not embed
     * deviceListMetadata for this participant".
     *
     * @param userJid the user JID
     * @return the ICDC result, or empty when no usable device list is cached
     */
    @WhatsAppWebExport(moduleName = "WAWebIdentityIcdcApi",
            exports = "getICDCMeta",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<IcdcResult> compute(Jid userJid) {
        return store.findDeviceList(userJid.toUserJid())
                .filter(deviceList -> !deviceList.deleted())
                .map(deviceList -> computeFromDeviceList(userJid, deviceList));
    }

    /**
     * Computes ICDC metadata from an already-resolved device list.
     *
     * @apiNote
     * Internal worker called by {@link #compute(Jid)}. Exposed as
     * package-private so tests can stub the cached-list lookup and exercise the
     * algorithm directly. Detects whether the user has companion devices,
     * gathers identity keys for those companions, includes the local
     * identity-key-pair public key when the user is the local self, computes
     * the truncated SHA-256 hash, and resolves the hosted account type when
     * the hosted-devices feature flag is on.
     *
     * @implNote
     * This implementation calls {@link DeviceInfo}'s raw identity key
     * accessor directly. Cobalt's local identity key pair is always present
     * (the store cannot exist without one), so the WA Web {@code if (!C) return null}
     * guard is unreachable here.
     *
     * @param userJid    the user JID
     * @param deviceList the device list
     * @return the computed ICDC result
     */
    @WhatsAppWebExport(moduleName = "WAWebIdentityIcdcApi",
            exports = "getICDCMetaFromDeviceRecord",
            adaptation = WhatsAppAdaptation.DIRECT)
    IcdcResult computeFromDeviceList(Jid userJid, DeviceList deviceList) {
        var devices = deviceList.devices();
        var timestamp = deviceList.timestamp();

        var hasCompanionDevices = devices.stream()
                .anyMatch(d -> d.id() != DeviceConstants.PRIMARY_DEVICE_ID);

        byte[] keyHash = null;
        List<Integer> keyIndexes = null;

        if (hasCompanionDevices) {
            var selfJid = store.jid().orElse(null);
            var isSelf = selfJid != null && userJid.toUserJid().equals(selfJid.toUserJid());

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

            if (isSelf) {
                identityKeys.add(store.identityKeyPair().publicKey().toEncodedPoint());
                if (selfKeyIndex != null) {
                    includedKeyIndexes.add(selfKeyIndex);
                }
            }

            keyHash = computeIdentityHash(identityKeys, getHashLength());

            if (includedKeyIndexes.size() != devices.size()) {
                keyIndexes = includedKeyIndexes;
            }
        }

        var resultTimestamp = (hasCompanionDevices || isRecent(timestamp)) ? timestamp : null;

        // Cobalt collapses WA Web's senderAccountType/receiverAccountType pair onto a
        // single accountType field; the caller decides which role this result
        // represents. For self, advAccountType is used as the proxy for
        // getIsHostedMeAccount().
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
     * Computes the truncated SHA-256 hash of the sorted, concatenated identity
     * keys.
     *
     * @apiNote
     * Exposed as package-private static so the test class can exercise the
     * pure-function contract (determinism, order-independence, truncation,
     * distinctness) without setting up a {@link WhatsAppStore}.
     *
     * @implNote
     * This implementation sorts the keys lexicographically using unsigned-byte
     * comparison ({@link #compareKeyBytes(byte[], byte[])}), concatenates them,
     * hashes with SHA-256, and truncates to the smaller of the requested
     * length and 32 (the SHA-256 output size).
     *
     * @param identityKeys the raw 32-byte identity key points
     * @param hashLength   the requested output hash length in bytes
     * @return the truncated hash
     */
    @WhatsAppWebExport(moduleName = "WAWebIdentityIcdcApi",
            exports = "computeIdentityHash",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebIdentityApiUtils",
            exports = "identityKeysToBinary",
            adaptation = WhatsAppAdaptation.DIRECT)
    static byte[] computeIdentityHash(List<byte[]> identityKeys, int hashLength) {
        try {
            var sorted = new ArrayList<>(identityKeys);
            sorted.sort(IcdcComputer::compareKeyBytes);

            var digest = MessageDigest.getInstance("SHA-256");
            for (var key : sorted) {
                digest.update(key);
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
     * Compares two byte arrays lexicographically using unsigned-byte ordering.
     *
     * @apiNote
     * Internal helper used by {@link #computeIdentityHash} to order identity
     * keys deterministically before concatenation.
     *
     * @param a the first array
     * @param b the second array
     * @return a negative value when {@code a} sorts before {@code b}, zero
     *         when equal, or a positive value otherwise
     */
    @WhatsAppWebExport(moduleName = "WAWebIdentityApiUtils",
            exports = "identityKeysToBinary",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static int compareKeyBytes(byte[] a, byte[] b) {
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
     * Returns the effective hash length, clamped to {@value MIN_HASH_LENGTH}
     * bytes.
     *
     * @apiNote
     * Reads the {@code md_icdc_hash_length} AB prop and clamps it up to the
     * eight-byte floor so server-side overrides cannot reduce the hash below
     * the safe minimum.
     *
     * @return the effective hash length in bytes
     */
    @WhatsAppWebExport(moduleName = "WAWebIdentityIcdcApi",
            exports = "getICDCMetaFromDeviceRecord",
            adaptation = WhatsAppAdaptation.DIRECT)
    private int getHashLength() {
        return Math.max(
                abPropsService.getInt(ABProp.MD_ICDC_HASH_LENGTH),
                MIN_HASH_LENGTH
        );
    }

    /**
     * Returns whether a timestamp falls within {@link #RECENT_THRESHOLD}.
     *
     * @apiNote
     * Internal predicate used by {@link #computeFromDeviceList} to decide
     * whether the snapshot timestamp accompanies a primary-only result.
     *
     * @param timestamp the timestamp, or {@code null}
     * @return {@code true} when {@code timestamp} is non-{@code null} and
     *         within {@link #RECENT_THRESHOLD}
     */
    @WhatsAppWebExport(moduleName = "WAWebIdentityIcdcApi",
            exports = "getICDCMetaFromDeviceRecord",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static boolean isRecent(Instant timestamp) {
        return timestamp != null
                && Duration.between(timestamp, Instant.now()).compareTo(RECENT_THRESHOLD) < 0;
    }

    /**
     * Returns whether the hosted business-coexistence path is enabled.
     *
     * @apiNote
     * Reads the {@code adv_accept_hosted_devices} AB prop; gates the
     * hosted-account-type computation in {@link #computeFromDeviceList}.
     *
     * @return {@code true} when {@code adv_accept_hosted_devices} is set
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCoexGatingUtils",
            exports = "bizHostedDevicesEnabled",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean isBizHostedDevicesEnabled() {
        return abPropsService.getBool(ABProp.ADV_ACCEPT_HOSTED_DEVICES);
    }
}
