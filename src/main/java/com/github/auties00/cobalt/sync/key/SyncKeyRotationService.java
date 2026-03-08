package com.github.auties00.cobalt.sync.key;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.chat.ChatMessageInfoBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainerBuilder;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.model.message.system.ProtocolMessageBuilder;
import com.github.auties00.cobalt.model.message.system.appstate.*;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.SyncPendingMutation;
import com.github.auties00.cobalt.model.sync.action.device.KeyExpirationAction;
import com.github.auties00.cobalt.model.sync.action.device.KeyExpirationActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.util.SchedulerUtils;

import java.security.SecureRandom;
import java.util.concurrent.CompletableFuture;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

/**
 * Manages sync key rotation based on key age and device removal.
 *
 * <p>Per WhatsApp Web {@code WAWebSyncdKeyManagement}: before pushing mutations,
 * the active key is checked. If the key has expired (age exceeds
 * {@code syncd_key_max_use_days}) or a companion device has been removed
 * (fingerprint mismatch), a new key is generated, shared with companion
 * devices, and sentinel mutations are queued to notify all collections
 * about the old key epoch's expiration.
 *
 * <p>The rotation flow is:
 * <ol>
 * <li>Check if the current newest key is expired or if devices changed
 * <li>Generate a new key with incremented epoch and fresh random key data
 * <li>Store the new key locally
 * <li>Share the new key with companion devices via {@code AppStateSyncKeyShare}
 * <li>Queue sentinel mutations for all collections
 * </ol>
 */
public final class SyncKeyRotationService {
    private static final Logger LOGGER = Logger.getLogger(SyncKeyRotationService.class.getName());

    /**
     * Minimum value for the key max use days threshold.
     */
    private static final int MIN_KEY_MAX_USE_DAYS = 1;

    /**
     * Maximum value for the key max use days threshold.
     */
    private static final int MAX_KEY_MAX_USE_DAYS = 90;

    /**
     * Per WhatsApp Web {@code WAWebTasksDefinitions}: the periodic key rotation
     * check runs every 27 days.
     */
    private static final Duration PERIODIC_ROTATION_INTERVAL = Duration.ofDays(27);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final WhatsAppClient whatsapp;
    private final ABPropsService abPropsService;
    private volatile CompletableFuture<?> periodicRotationJob;

    /**
     * Constructs a new sync key rotation service.
     *
     * @param whatsapp       the WhatsApp client instance
     * @param abPropsService the AB props service for threshold configuration
     */
    public SyncKeyRotationService(WhatsAppClient whatsapp, ABPropsService abPropsService) {
        this.whatsapp = whatsapp;
        this.abPropsService = abPropsService;
    }

    /**
     * Ensures the active key is valid for use, rotating if necessary.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdKeyManagement.getActiveKey}:
     * gets the newest key pair, checks if it has expired or if a device
     * was removed, and rotates if needed. After rotation, sentinel mutations
     * are generated and queued.
     *
     * @param triggerRotation whether to trigger rotation if the key is stale
     *                        (set to {@code false} for read-only checks)
     */
    public void ensureActiveKey(boolean triggerRotation) {
        var newestKey = findNewestKey();
        if (newestKey == null) {
            LOGGER.warning("No sync keys available; cannot check rotation");
            return;
        }

        var expired = hasKeyExpired(newestKey);
        var deviceRemoved = hasADeviceBeenRemoved(newestKey);

        if (!triggerRotation || (!expired && !deviceRemoved)) {
            return;
        }

        // Rotate
        if (expired) {
            LOGGER.info("Sync key rotation triggered: key expired");
        }
        if (deviceRemoved) {
            LOGGER.info("Sync key rotation triggered: device removed");
        }

        var rotatedKey = rotateKey(newestKey);
        if (rotatedKey == null) {
            return;
        }

        // Store the new key
        whatsapp.store().addWebAppStateKeys(List.of(rotatedKey));

        // Share with companion devices
        shareKeyWithCompanionDevices(rotatedKey);

        // Generate and queue sentinel mutations for all collections
        queueSentinelMutations(rotatedKey);
    }

    /**
     * Finds the newest (highest epoch) sync key.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdKeyManagement.getNewestKeyPair}:
     * among all stored keys, find those with the maximum epoch. Among those,
     * pick the one with the minimum device ID.
     *
     * @return the newest key, or {@code null} if no keys exist
     */
    private AppStateSyncKey findNewestKey() {
        var keys = whatsapp.store().appStateKeys();
        if (keys.isEmpty()) {
            return null;
        }

        var maxEpoch = Integer.MIN_VALUE;
        for (var key : keys) {
            var epoch = SyncKeyUtils.getKeyEpoch(key);
            if (epoch > maxEpoch) {
                maxEpoch = epoch;
            }
        }

        AppStateSyncKey bestKey = null;
        var bestDeviceId = Integer.MAX_VALUE;
        for (var key : keys) {
            if (SyncKeyUtils.getKeyEpoch(key) != maxEpoch) {
                continue;
            }

            var keyIdBytes = key.keyId()
                    .flatMap(AppStateSyncKeyId::keyId)
                    .orElse(null);
            if (keyIdBytes == null) {
                continue;
            }

            var deviceId = SyncKeyUtils.getKeyDeviceId(keyIdBytes);
            if (deviceId < bestDeviceId) {
                bestDeviceId = deviceId;
                bestKey = key;
            }
        }

        return bestKey;
    }

    /**
     * Checks whether the given key has expired based on its timestamp
     * and the configured maximum use days.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdRotateKey.hasKeyExpired}:
     * compares the key's timestamp against {@code syncd_key_max_use_days}
     * (clamped between 1 and 90).
     *
     * @param key the key to check
     * @return {@code true} if the key has expired
     */
    private boolean hasKeyExpired(AppStateSyncKey key) {
        var timestamp = key.keyData()
                .flatMap(AppStateSyncKeyData::timestamp)
                .orElse(null);
        if (timestamp == null) {
            return false;
        }

        var maxDays = Math.min(MAX_KEY_MAX_USE_DAYS,
                Math.max(MIN_KEY_MAX_USE_DAYS,
                        abPropsService.getInt(ABProp.SYNCD_KEY_MAX_USE_DAYS)));
        var maxAge = Duration.ofDays(maxDays);
        var age = Duration.between(timestamp, Instant.now());
        return age.compareTo(maxAge) > 0;
    }

    /**
     * Checks whether a device has been removed by comparing the key's
     * stored fingerprint with the current device list.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdRotateKey.hasADeviceBeenRemoved}:
     * expands the key's fingerprint device indexes up to the current device
     * list's {@code currentIndex} and compares with the current device indexes.
     * A mismatch (rawId change or device set difference) indicates removal.
     *
     * @param key the key whose fingerprint to check
     * @return {@code true} if a device has been removed since the key was created
     */
    private boolean hasADeviceBeenRemoved(AppStateSyncKey key) {
        var fingerprint = key.keyData()
                .flatMap(AppStateSyncKeyData::fingerprint)
                .orElse(null);
        if (fingerprint == null) {
            return false;
        }

        var currentFingerprint = getCurrentDeviceFingerprint();
        if (currentFingerprint == null) {
            return false;
        }

        // Check rawId mismatch
        var keyRawId = fingerprint.rawId().orElse(-1);
        if (keyRawId != currentFingerprint.rawId) {
            return true;
        }

        // Expand the key's device indexes up to currentIndex
        var keyDeviceIndexes = new HashSet<>(fingerprint.deviceIndexes());
        var keyCurrentIndex = fingerprint.currentIndex().orElse(0);
        for (int i = keyCurrentIndex + 1; i <= currentFingerprint.currentIndex; i++) {
            keyDeviceIndexes.add(i);
        }

        // Compare with current device indexes
        return !keyDeviceIndexes.equals(currentFingerprint.deviceIndexes);
    }

    /**
     * Gets the current device fingerprint from the own device list.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdKeyCallbacksApi.getDeviceFingerprint}:
     * reads the own device list and extracts currentIndex, deviceIndexes (keyIndex
     * values), and rawId.
     *
     * @return the current fingerprint, or {@code null} if unavailable
     */
    private DeviceFingerprint getCurrentDeviceFingerprint() {
        var myJid = whatsapp.store().jid().orElse(null);
        if (myJid == null) {
            return null;
        }

        var deviceListOpt = whatsapp.store().findDeviceList(myJid.toUserJid());
        if (deviceListOpt.isEmpty()) {
            return null;
        }

        var deviceList = deviceListOpt.get();
        var currentIndex = deviceList.currentIndex();
        var rawId = deviceList.rawId();
        var rawIdInt = -1;
        if (rawId != null) {
            try {
                rawIdInt = Integer.parseInt(rawId);
            } catch (NumberFormatException ignored) {
            }
        }

        var deviceIndexes = new HashSet<Integer>();
        for (var device : deviceList.devices()) {
            deviceIndexes.add(device.keyIndex());
        }

        return new DeviceFingerprint(currentIndex, deviceIndexes, rawIdInt);
    }

    /**
     * Generates a new rotated key from the given previous key.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdRotateKey.rotateKey}:
     * creates a new key with an incremented epoch, the current device's ID,
     * fresh random 32-byte key data, the current device fingerprint, and
     * the current timestamp.
     *
     * @param previousKey the key being rotated out
     * @return the new key, or {@code null} if rotation cannot proceed
     */
    private AppStateSyncKey rotateKey(AppStateSyncKey previousKey) {
        var previousKeyIdBytes = previousKey.keyId()
                .flatMap(AppStateSyncKeyId::keyId)
                .orElse(null);
        if (previousKeyIdBytes == null) {
            LOGGER.warning("Cannot rotate key: previous key has no ID");
            return null;
        }

        var myJid = whatsapp.store().jid().orElse(null);
        if (myJid == null) {
            LOGGER.warning("Cannot rotate key: own JID not available");
            return null;
        }

        var currentFingerprint = getCurrentDeviceFingerprint();
        if (currentFingerprint == null) {
            LOGGER.warning("Cannot rotate key: device fingerprint not available");
            return null;
        }

        // Generate new epoch and key ID
        var newEpoch = SyncKeyUtils.generateNewKeyEpoch(previousKeyIdBytes);
        var myDeviceId = myJid.device();
        var newKeyId = SyncKeyUtils.buildKeyId(myDeviceId, newEpoch);

        // Generate random key data
        var newKeyData = new byte[32];
        SECURE_RANDOM.nextBytes(newKeyData);

        // Build fingerprint from current device state
        var fingerprint = new AppStateSyncKeyFingerprintBuilder()
                .rawId(currentFingerprint.rawId)
                .currentIndex(currentFingerprint.currentIndex)
                .deviceIndexes(new ArrayList<>(currentFingerprint.deviceIndexes))
                .build();

        var keyData = new AppStateSyncKeyDataBuilder()
                .keyData(newKeyData)
                .fingerprint(fingerprint)
                .timestamp(Instant.now())
                .build();

        var keyIdProto = new AppStateSyncKeyIdBuilder()
                .keyId(newKeyId)
                .build();

        return new AppStateSyncKeyBuilder()
                .keyId(keyIdProto)
                .keyData(keyData)
                .build();
    }

    /**
     * Shares the rotated key with all companion devices.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdKeyCallbacksApi.sendSyncdKeyRotation}:
     * wraps the key in an {@code AppStateSyncKeyShare} protocol message with type
     * {@code key_rotation} and sends it to all peer devices.
     *
     * @param key the new key to share
     */
    private void shareKeyWithCompanionDevices(AppStateSyncKey key) {
        var companionDevices = getCompanionDevices();
        if (companionDevices.isEmpty()) {
            LOGGER.fine("No companion devices to share rotated key with");
            return;
        }

        var myJid = whatsapp.store().jid().orElse(null);
        if (myJid == null) {
            LOGGER.warning("Cannot share rotated key: own JID not available");
            return;
        }

        var keyShare = new AppStateSyncKeyShareBuilder()
                .keys(List.of(key))
                .build();

        var protocolMessage = new ProtocolMessageBuilder()
                .type(ProtocolMessage.Type.APP_STATE_SYNC_KEY_SHARE)
                .appStateSyncKeyShare(keyShare)
                .build();

        var messageContainer = new MessageContainerBuilder()
                .protocolMessage(protocolMessage)
                .build();

        // Per WA Web WAWebKeyManagementSendKeyShareApi: key rotation shares
        // are sent as peer messages (category="peer", push_priority="high")
        // with subtype "app_state_sync_key_share"
        for (var device : companionDevices) {
            try {
                var messageKey = new MessageKeyBuilder()
                        .id(MessageKey.randomId(whatsapp.store().clientType()))
                        .chatJid(myJid)
                        .fromMe(true)
                        .senderJid(myJid)
                        .build();
                var messageInfo = new ChatMessageInfoBuilder()
                        .key(messageKey)
                        .message(messageContainer)
                        .build();
                whatsapp.sendPeerMessage(device, messageInfo);
            } catch (Exception e) {
                LOGGER.warning("Failed to send rotated key to device " + device + ": " + e.getMessage());
            }
        }

        LOGGER.info("Shared rotated key with " + companionDevices.size() + " companion devices");
    }

    /**
     * Generates sentinel mutations for all collections and queues them
     * as pending mutations.
     *
     * <p>Per WhatsApp Web {@code WAWebSentinel} + {@code WAWebSentinelMutationSync.getSentinelMutations}:
     * gets the newest key pair's epoch and creates SET sentinel mutations
     * with {@code {keyExpiration: {expiredKeyEpoch: epoch}}} for every collection.
     * These mutations are then added as pending mutations and the collections
     * are marked dirty.
     *
     * @param newKey the newly created key (used to determine the epoch to expire)
     */
    private void queueSentinelMutations(AppStateSyncKey newKey) {
        // The epoch to expire is the new key's epoch (the key that was just created).
        // Per WA Web: getSentinelMutations() gets getNewestKeyPair().keyEpoch
        // which at this point IS the new key (we just stored it).
        var newEpoch = SyncKeyUtils.getKeyEpoch(newKey);
        if (newEpoch < 0) {
            LOGGER.warning("Cannot generate sentinel mutations: invalid key epoch");
            return;
        }

        var timestamp = Instant.now();
        var actionValue = new SyncActionValueBuilder()
                .keyExpirationAction(new KeyExpirationActionBuilder()
                        .expiredKeyEpoch(newEpoch)
                        .build())
                .build();

        for (var collection : SyncPatchType.values()) {
            var index = "[\"sentinel\",\"" + collection + "\"]";
            var mutation = new DecryptedMutation.Trusted(
                    index,
                    actionValue,
                    SyncdOperation.SET,
                    timestamp,
                    KeyExpirationAction.ACTION_VERSION
            );
            var pendingMutation = new SyncPendingMutation(mutation, 0);
            whatsapp.store().addPendingMutations(collection, List.of(pendingMutation));
            whatsapp.store().markWebAppStateDirty(collection);
        }

        LOGGER.info("Queued sentinel mutations for " + SyncPatchType.values().length + " collections with epoch " + newEpoch);
    }

    /**
     * Gets companion device JIDs (all devices except our own).
     *
     * @return the list of companion device JIDs
     */
    private List<Jid> getCompanionDevices() {
        var myJid = whatsapp.store().jid().orElse(null);
        if (myJid == null) {
            return List.of();
        }

        var myDeviceList = whatsapp.store().findDeviceList(myJid.toUserJid());
        if (myDeviceList.isEmpty()) {
            return List.of();
        }

        return myDeviceList.get()
                .devices()
                .stream()
                .filter(device -> device.id() != myJid.device())
                .map(device -> device.toDeviceJid(myJid.user(), myJid.server()))
                .toList();
    }

    /**
     * Starts a periodic background job that checks key rotation every 27 days.
     *
     * <p>Per WhatsApp Web {@code WAWebTasksDefinitions}: a persisted
     * {@code RotateKeyTask} runs every 27 days as a background check
     * independent of mutation push. This ensures expired keys are rotated
     * even if no mutations are pushed for extended periods.
     */
    public void startPeriodicRotationJob() {
        stopPeriodicRotationJob();
        scheduleNextPeriodicRotation();
    }

    private void scheduleNextPeriodicRotation() {
        periodicRotationJob = SchedulerUtils.scheduleDelayed(
                PERIODIC_ROTATION_INTERVAL,
                () -> {
                    try {
                        ensureActiveKey(true);
                    } catch (Exception e) {
                        LOGGER.warning("Periodic key rotation check failed: " + e.getMessage());
                    } finally {
                        scheduleNextPeriodicRotation();
                    }
                }
        );
    }

    /**
     * Stops the periodic key rotation background job.
     */
    public void stopPeriodicRotationJob() {
        var job = periodicRotationJob;
        if (job != null) {
            job.cancel(false);
            periodicRotationJob = null;
        }
    }

    /**
     * Internal representation of a device fingerprint snapshot.
     *
     * @param currentIndex  the current device index counter
     * @param deviceIndexes the set of active device key indexes
     * @param rawId         the raw fingerprint ID
     */
    private record DeviceFingerprint(int currentIndex, Set<Integer> deviceIndexes, int rawId) {
    }
}
