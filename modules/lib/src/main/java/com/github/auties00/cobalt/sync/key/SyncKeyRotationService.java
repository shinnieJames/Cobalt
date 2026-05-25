package com.github.auties00.cobalt.sync.key;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.message.send.id.MessageIdGenerator;
import com.github.auties00.cobalt.message.send.id.MessageIdVersion;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.chat.ChatMessageInfoBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainerBuilder;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.model.message.system.ProtocolMessageBuilder;
import com.github.auties00.cobalt.model.message.system.appstate.*;
import com.github.auties00.cobalt.model.sync.SyncCollectionState;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.sync.WebAppStateService;
import com.github.auties00.cobalt.util.SchedulerUtils;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.MdAppStateKeyRotationEventBuilder;
import com.github.auties00.cobalt.wam.event.MdBootstrapAppStateCriticalDataProcessingEventBuilder;
import com.github.auties00.cobalt.wam.type.BootstrapAppStateDataStageCode;
import com.github.auties00.cobalt.wam.type.MdAppStateKeyRotationReasonCode;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Manages the lifecycle of the active app state sync key: detects expiry, detects companion
 * device removal, mints fresh keys with a monotonically increasing epoch, and shares them with
 * every peer device.
 *
 * <p>Two entry points dominate. {@link #getActiveKey(boolean)} resolves the current key for
 * outgoing patches and rotates inline when a rotation trigger fires.
 * {@link #handleKeyShare(int, List)} consumes inbound {@code AppStateSyncKeyShare}
 * {@link ProtocolMessage} payloads from companion devices and reconciles them against the local
 * key store and the missing-key tracker. A periodic 27-day background check
 * ({@link #startPeriodicRotationJob()}) ensures rotation also happens during long
 * mutation-free windows.
 *
 * @implNote This implementation serialises the rotation flow through {@link #rotationLock} to
 * keep the read-then-rotate-then-store sequence atomic in the face of concurrent encrypt
 * callers, without paying the overhead of an actual queue under the virtual-thread model.
 */
@WhatsAppWebModule(moduleName = "WAWebSyncdKeyManagement")
@WhatsAppWebModule(moduleName = "WAWebSyncdRotateKey")
@WhatsAppWebModule(moduleName = "WAWebSyncdKeyCallbacksApi")
@WhatsAppWebModule(moduleName = "WAWebSyncdHandleKeyShare")
@WhatsAppWebModule(moduleName = "WAWebKeyManagementSendKeyShareApi")
@WhatsAppWebModule(moduleName = "WAWebKeyManagementUtils")
@WhatsAppWebModule(moduleName = "WAWebTasksDefinitions")
public final class SyncKeyRotationService {
    /**
     * Holds the diagnostic logger for the sync key rotation flow.
     */
    private static final Logger LOGGER = Logger.getLogger(SyncKeyRotationService.class.getName());

    /**
     * Holds the lower bound applied to {@code syncd_key_max_use_days} after AB-prop clamping.
     */
    private static final int MIN_KEY_MAX_USE_DAYS = 1;

    /**
     * Holds the upper bound applied to {@code syncd_key_max_use_days} after AB-prop clamping.
     */
    private static final int MAX_KEY_MAX_USE_DAYS = 90;

    /**
     * Holds the interval between periodic rotation checks driven by
     * {@link #startPeriodicRotationJob()}.
     *
     * @implNote This implementation uses 27 days to match the WA Web rotation-task schedule.
     */
    private static final Duration PERIODIC_ROTATION_INTERVAL = Duration.ofDays(27);

    /**
     * Holds the source of randomness used to seed the 32-byte key data of every newly rotated
     * key.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Holds the monitor that serialises the read-then-rotate-then-store sequence in
     * {@link #getActiveKey(boolean)}.
     *
     * @implNote This implementation uses a dedicated object monitor rather than synchronising on
     * {@code this} so callers cannot inadvertently deadlock by also holding the service monitor.
     */
    private final Object rotationLock = new Object();

    /**
     * Holds the injected client used to read and update the sync key store and to dispatch the
     * {@code AppStateSyncKeyShare} peer messages.
     */
    private final WhatsAppClient whatsapp;

    /**
     * Holds the owning service used to schedule missing-key follow-ups when a negative key
     * share resolves a tracker entry.
     */
    private final WebAppStateService webAppStateService;

    /**
     * Holds the AB prop source used to read {@code syncd_key_max_use_days} and the
     * persist-after-server-ack gate.
     */
    private final ABPropsService abPropsService;

    /**
     * Holds the WAM service used to commit rotation and bootstrap-progress events.
     */
    private final WamService wamService;

    /**
     * Holds the handle of the periodic 27-day rotation check, or {@code null} until
     * {@link #startPeriodicRotationJob()} is called.
     */
    private volatile CompletableFuture<?> periodicRotationJob;

    /**
     * Constructs a new rotation service bound to the supplied dependencies.
     *
     * @param whatsapp the client that owns the store and the peer-message channel
     * @param webAppStateService the owning service
     * @param abPropsService the AB prop source used to read rotation thresholds
     * @param wamService the WAM service used to commit rotation events
     */
    public SyncKeyRotationService(WhatsAppClient whatsapp, WebAppStateService webAppStateService, ABPropsService abPropsService, WamService wamService) {
        this.whatsapp = whatsapp;
        this.webAppStateService = webAppStateService;
        this.abPropsService = abPropsService;
        this.wamService = wamService;
    }

    /**
     * Reconciles an incoming {@code AppStateSyncKeyShare} protocol message against the local
     * sync key store and missing-key tracker.
     *
     * <p>Invoked by the message stream handler when an
     * {@link ProtocolMessage.Type#APP_STATE_SYNC_KEY_SHARE} message is received. Each shared key
     * is either a positive response carrying key data, which is added to the store and clears
     * any matching missing-key tracker entry, or a negative response without key data, which
     * records the sender as having answered without the key. When a tracker entry then has a
     * no-key answer from every asked device the all-devices-responded grace check is scheduled
     * via {@link WebAppStateService#scheduleAllDevicesRespondedCheck()}. When any tracker entry
     * is resolved the wait-for-key timeout is rescheduled. After reconciliation any blocked
     * syncd collections are unblocked and resynced.
     *
     * @param senderDeviceId the device id of the peer that sent the share
     * @param keys the keys carried by the share message
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdHandleKeyShare",
            exports = "handleKeyShare",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void handleKeyShare(int senderDeviceId, List<AppStateSyncKey> keys) {
        var hasAnyKeyData = keys.stream()
                .anyMatch(key -> key.keyData()
                        .flatMap(AppStateSyncKeyData::keyData)
                        .map(data -> data.length > 0)
                        .orElse(false));
        if (!hasAnyKeyData) {
            LOGGER.warning("syncd: key share from device " + senderDeviceId + " has no keys with keydata.");
        }

        var newKeysStored = new ArrayList<AppStateSyncKey>();
        var resolvedAny = false;

        for (var key : keys) {
            var keyIdBytes = key.keyId()
                    .flatMap(AppStateSyncKeyId::keyId)
                    .orElse(null);
            if (keyIdBytes == null) {
                continue;
            }

            var hasKeyData = key.keyData()
                    .flatMap(AppStateSyncKeyData::keyData)
                    .map(data -> data.length > 0)
                    .orElse(false);
            var keyIdHex = SyncKeyUtils.syncKeyIdToHex(keyIdBytes);

            if (hasKeyData) {
                var existingKey = whatsapp.store().findWebAppStateKeyById(keyIdBytes).orElse(null);

                if (existingKey == null) {
                    newKeysStored.add(key);

                    LOGGER.info("syncd: stored key share key id " + keyIdHex
                            + " from device " + senderDeviceId);
                } else {
                    var existingKeyData = existingKey.keyData()
                            .flatMap(AppStateSyncKeyData::keyData)
                            .orElse(null);
                    var incomingKeyData = key.keyData()
                            .flatMap(AppStateSyncKeyData::keyData)
                            .orElse(null);
                    if (existingKeyData != null && incomingKeyData != null
                            && !Arrays.equals(existingKeyData, incomingKeyData)) {
                        LOGGER.severe("syncd: got key share for existing key " + keyIdHex
                                + " with different key data from device " + senderDeviceId);
                    }
                }

                if (whatsapp.store().findMissingSyncKey(keyIdBytes).isPresent()) {
                    whatsapp.store().removeMissingSyncKey(keyIdBytes);
                    resolvedAny = true;
                }
            } else {
                if (senderDeviceId >= 0) {
                    var missingKey = whatsapp.store().findMissingSyncKey(keyIdBytes).orElse(null);
                    if (missingKey != null && missingKey.wasAsked(senderDeviceId)) {
                        if (!missingKey.hasDeviceRespondedWithoutKey(senderDeviceId)) {
                            missingKey.markDeviceRespondedWithoutKey(senderDeviceId);
                        }
                        if (missingKey.isMissingOnAllDevices()) {
                            webAppStateService.scheduleAllDevicesRespondedCheck();
                        }
                    }
                }
            }
        }

        if (!newKeysStored.isEmpty()) {
            whatsapp.store().addWebAppStateKeys(newKeysStored);
        }

        if (resolvedAny) {
            webAppStateService.rescheduleMissingSyncKeyTimeout();
        }

        syncBlockedCollections();
    }

    /**
     * Emits the {@link BootstrapAppStateDataStageCode#MISSING_KEYS_RECEIVED} bootstrap-stage
     * event from the validation half of the inbound key-share path.
     *
     * <p>The message stream handler validates an inbound {@code AppStateSyncKeyShare} on its own
     * thread before delegating storage to {@link #handleKeyShare(int, List)}. This method is
     * exposed so the validator can fire the bootstrap beacon at the correct call position
     * regardless of whether any shared key survives validation.
     *
     * @implNote This implementation is a thin shim onto
     * {@link #logCriticalBootstrapStageIfNecessary(BootstrapAppStateDataStageCode)} that exists
     * only because the validator and the storage path live in different Cobalt classes; WA Web
     * fuses both into one function body.
     */
    @WhatsAppWebExport(moduleName = "WAWebKeyManagementHandleKeyShareApi", exports = "handleAppStateSyncKeyShare", adaptation = WhatsAppAdaptation.ADAPTED)
    public void logMissingKeysReceived() {
        logCriticalBootstrapStageIfNecessary(BootstrapAppStateDataStageCode.MISSING_KEYS_RECEIVED);
    }

    /**
     * Emits a critical-data processing event for the supplied bootstrap stage when the critical
     * data sync is still in progress.
     *
     * <p>Drives the WAM-side critical-data progress beacon that populates the
     * preparing-your-data sub-states on first load. The method is a no-op once the
     * {@link SyncPatchType#CRITICAL_BLOCK} collection has been bootstrapped.
     *
     * @implNote This implementation approximates WA Web's critical-data sync state machine by
     * reading the {@code bootstrapped} flag for {@link SyncPatchType#CRITICAL_BLOCK}, mirroring
     * {@link WebAppStateService}.
     *
     * @param stage the bootstrap stage reached
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCriticalBootstrapProcessingApi", exports = "logCriticalBootstrapStageIfNecessary", adaptation = WhatsAppAdaptation.ADAPTED)
    private void logCriticalBootstrapStageIfNecessary(BootstrapAppStateDataStageCode stage) {
        if (whatsapp.store().findWebAppState(SyncPatchType.CRITICAL_BLOCK).bootstrapped()) {
            return;
        }
        wamService.commit(new MdBootstrapAppStateCriticalDataProcessingEventBuilder()
                .bootstrapAppStateDataStage(stage)
                .mdTimestamp((int) System.currentTimeMillis())
                .build());
    }

    /**
     * Re-syncs every collection currently in {@link SyncCollectionState#BLOCKED}.
     *
     * <p>Called at the end of {@link #handleKeyShare(int, List)} so collections that paused
     * patching while waiting for missing key material can resume immediately once the material
     * arrives. Each blocked collection is marked dirty and a single batched pull is issued for
     * the whole set.
     *
     * @implNote This implementation transitions every blocked collection through
     * {@link com.github.auties00.cobalt.store.WhatsAppStore#markWebAppStateDirty(SyncPatchType)}
     * and then fires one {@link WhatsAppClient#pullWebAppState(SyncPatchType...)} batched pull.
     */
    private void syncBlockedCollections() {
        var blockedCollections = new ArrayList<SyncPatchType>();
        for (var patchType : SyncPatchType.values()) {
            var metadata = whatsapp.store().findWebAppState(patchType);
            if (metadata.state() == SyncCollectionState.BLOCKED) {
                whatsapp.store().markWebAppStateDirty(patchType);
                blockedCollections.add(patchType);
            }
        }
        if (!blockedCollections.isEmpty()) {
            LOGGER.info("syncd: sync blocked collections: " + blockedCollections);
            whatsapp.pullWebAppState(blockedCollections.toArray(SyncPatchType[]::new));
        }
    }

    /**
     * Ensures the active sync key is fit for use, rotating inline when {@code triggerRotation}
     * is set and the active key is stale.
     *
     * <p>Convenience wrapper for code paths that need only the side effect of rotation without
     * consuming the resulting key reference, such as the periodic 27-day background check.
     * Equivalent to discarding the return value of {@link #getActiveKey(boolean)}.
     *
     * @param triggerRotation whether to rotate when the active key is stale
     */
    public void ensureActiveKey(boolean triggerRotation) {
        getActiveKey(triggerRotation);
    }

    /**
     * Returns the active sync key, rotating inline when {@code triggerRotation} is {@code true}
     * and the newest key is either expired or carries a stale device fingerprint.
     *
     * <p>Called by {@link com.github.auties00.cobalt.sync.exchange.MutationRequestBuilder} before
     * encrypting outgoing patches and by the periodic background check in
     * {@link #startPeriodicRotationJob()}. A {@code triggerRotation} of {@code false} is the
     * read-only mode that returns the existing key without inducing rotation.
     *
     * @implNote This implementation serialises the read-rotate-store sequence under
     * {@link #rotationLock} so two concurrent encrypt callers cannot race into rotating twice in
     * a row.
     *
     * @param triggerRotation whether to rotate when the active key is stale
     * @return the active {@link AppStateSyncKey}
     * @throws IllegalStateException when no sync key exists at all
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdKeyManagement",
            exports = "getActiveKey",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public AppStateSyncKey getActiveKey(boolean triggerRotation) {
        synchronized (rotationLock) {
            return getActiveKeyInternal(triggerRotation);
        }
    }

    /**
     * Performs the read-rotate-store sequence inside {@link #rotationLock}.
     *
     * <p>Must be called only with {@link #rotationLock} held. Returns the newest key unchanged
     * when rotation is not requested or not needed; otherwise mints a fresh key, persists and
     * shares it in the order dictated by the persist-after-server-ack gate, commits the
     * appropriate rotation-reason WAM event, and returns the new key. Throws when no key exists
     * at all.
     *
     * @implNote This implementation returns the freshly rotated key directly rather than
     * recursing: the recursive resolution would return the same key now that storage is
     * committed and neither rotation trigger fires for a brand-new key, so avoiding it keeps the
     * lock-held window short and removes one stack frame per encrypt call.
     *
     * @param triggerRotation whether to rotate when the active key is stale
     * @return the active {@link AppStateSyncKey}
     * @throws IllegalStateException when no sync key exists at all
     */
    private AppStateSyncKey getActiveKeyInternal(boolean triggerRotation) {
        var newestKey = getNewestKeyPair();
        var currentFingerprint = getCurrentDeviceFingerprint();
        var expired = false;
        var deviceRemoved = false;

        if (newestKey != null) {
            expired = hasKeyExpired(newestKey);
            deviceRemoved = hasADeviceBeenRemoved(newestKey, currentFingerprint);

            if (!triggerRotation || (!expired && !deviceRemoved)) {
                return newestKey;
            }
        } else {
            throw new IllegalStateException("syncd: No sync key available");
        }

        var rotatedKey = rotateKey(currentFingerprint, newestKey);
        if (rotatedKey == null) {
            return newestKey;
        }

        LOGGER.info("syncd: rotating key id " + SyncKeyUtils.syncKeyIdToHex(rotatedKey));

        if (SyncKeyUtils.getEnableSyncdKeyPersistenceOnlyAfterServerAck(abPropsService)) {
            shareKeyWithCompanionDevices(rotatedKey);
            LOGGER.info("syncd: key share ACK received, storing key id " + SyncKeyUtils.syncKeyIdToHex(rotatedKey));
            whatsapp.store().addWebAppStateKeys(List.of(rotatedKey));
        } else {
            whatsapp.store().addWebAppStateKeys(List.of(rotatedKey));
            shareKeyWithCompanionDevices(rotatedKey);
        }

        if (expired) {
            LOGGER.info("syncd: key rotation due to key expiry");
            wamService.commit(new MdAppStateKeyRotationEventBuilder()
                    .mdAppStateKeyRotationReason(MdAppStateKeyRotationReasonCode.APP_STATE_SYNC_KEY_EXPIRY)
                    .build());
        }
        if (deviceRemoved) {
            LOGGER.info("syncd: key rotation due to device removal");
            wamService.commit(new MdAppStateKeyRotationEventBuilder()
                    .mdAppStateKeyRotationReason(MdAppStateKeyRotationReasonCode.DEVICE_DEREGISTERATION)
                    .build());
        }

        return rotatedKey;
    }

    /**
     * Returns the newest sync key pair from the store.
     *
     * <p>Reads the store's available keys and selects the newest under
     * {@link SyncKeyUtils#findNewestKey(java.util.Collection)} ordering. The read-only contract
     * makes it cheap to use as a precondition check before invoking {@link #getActiveKey(boolean)}.
     *
     * @return the newest {@link AppStateSyncKey}, or {@code null} when no keys exist
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdKeyManagement",
            exports = "getNewestKeyPair",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public AppStateSyncKey getNewestKeyPair() {
        return SyncKeyUtils.findNewestKey(whatsapp.store().appStateKeys());
    }

    /**
     * Returns whether the supplied key has aged past the configured maximum use threshold.
     *
     * <p>The threshold is read from {@code syncd_key_max_use_days} and clamped between
     * {@link #MIN_KEY_MAX_USE_DAYS} and {@link #MAX_KEY_MAX_USE_DAYS} so a misconfigured AB-prop
     * value can neither disable rotation entirely nor trigger it on every call. A key carrying
     * no timestamp is treated as not expired.
     *
     * @implNote This implementation returns {@code false} when the key carries no timestamp
     * because that protobuf field is optional.
     *
     * @param key the key to inspect
     * @return {@code true} when the key is past its maximum-use age
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdRotateKey",
            exports = "hasKeyExpired",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean hasKeyExpired(AppStateSyncKey key) {
        var timestamp = key.keyData()
                .flatMap(AppStateSyncKeyData::timestamp)
                .orElse(null);
        if (timestamp == null) {
            return false;
        }

        var maxDays = Math.min(MAX_KEY_MAX_USE_DAYS,
                Math.max(MIN_KEY_MAX_USE_DAYS,
                        SyncKeyUtils.getSyncdKeyMaxUseDays(abPropsService)));
        var maxAge = Duration.ofDays(maxDays);
        var age = Duration.between(timestamp, Instant.now());
        return age.compareTo(maxAge) > 0;
    }

    /**
     * Returns whether a companion device has been removed since the supplied key was created.
     *
     * <p>Compares the key's recorded device fingerprint against the live device list; a removal
     * is one of the two rotation triggers, alongside expiry, and exists to cut a removed
     * device's access to future mutations. A key without a fingerprint, or a {@code null} live
     * fingerprint, yields {@code false}. A raw-id mismatch counts as a removal; otherwise only a
     * true device-index set mismatch counts.
     *
     * @implNote This implementation expands the key's stored device indexes from
     * {@code currentIndex + 1} up to the live current index so devices added legitimately since
     * the key was minted do not register as removals.
     *
     * @param key the key whose fingerprint to compare
     * @param currentFingerprint the current device fingerprint, possibly {@code null}
     * @return {@code true} when at least one device has been removed since the key was minted
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdRotateKey",
            exports = "hasADeviceBeenRemoved",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean hasADeviceBeenRemoved(AppStateSyncKey key, DeviceFingerprint currentFingerprint) {
        var fingerprint = key.keyData()
                .flatMap(AppStateSyncKeyData::fingerprint)
                .orElse(null);
        if (fingerprint == null) {
            return false;
        }

        if (currentFingerprint == null) {
            return false;
        }

        var keyRawId = fingerprint.rawId().orElse(-1);
        if (keyRawId != currentFingerprint.rawId) {
            return true;
        }

        var keyDeviceIndexes = new HashSet<>(fingerprint.deviceIndexes());
        var keyCurrentIndex = fingerprint.currentIndex().orElse(0);
        for (var i = keyCurrentIndex + 1; i <= currentFingerprint.currentIndex; i++) {
            keyDeviceIndexes.add(i);
        }

        return !keyDeviceIndexes.equals(currentFingerprint.deviceIndexes);
    }

    /**
     * Computes the current device fingerprint snapshot used by the rotation triggers.
     *
     * <p>Returns {@code null} when either the own JID is unavailable or the device list has not
     * been synced; both rotation triggers then degrade to a no-op rather than forcing a rotation
     * against an unknown peer set.
     *
     * @implNote This implementation parses the device list's raw id as an integer because
     * Cobalt's device-list model holds it as a string; an unparseable value yields {@code -1}.
     *
     * @return the current {@link DeviceFingerprint} snapshot, or {@code null} when unavailable
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdKeyCallbacksApi",
            exports = "getDeviceFingerprint",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebApiDeviceList",
            exports = "getMyDeviceList",
            adaptation = WhatsAppAdaptation.ADAPTED)
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
            } catch (NumberFormatException _) {
            }
        }

        var deviceIndexes = new HashSet<Integer>();
        for (var device : deviceList.devices()) {
            deviceIndexes.add(device.keyIndex());
        }

        return new DeviceFingerprint(currentIndex, deviceIndexes, rawIdInt);
    }

    /**
     * Mints a new sync key with an incremented epoch, fresh random key data, and the supplied
     * device fingerprint.
     *
     * <p>Returns {@code null} when any input needed to derive the new key id is absent (the
     * previous key id, the own JID, or the current fingerprint), which translates upstream into
     * no rotation this round. The new key id encodes the own device id and the next epoch, and
     * the new key data is a fresh 32-byte random payload.
     *
     * @implNote This implementation builds the 6-byte key id via
     * {@link SyncKeyUtils#buildKeyId(int, int)} from a 2-byte device id and a 4-byte epoch, and
     * seeds the 32-byte payload from {@link SecureRandom}.
     *
     * @param currentFingerprint the device fingerprint to attach to the new key
     * @param previousKey the key being rotated out; its epoch seeds the new epoch
     * @return the newly minted {@link AppStateSyncKey}, or {@code null} when rotation is impossible
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdRotateKey",
            exports = "rotateKey",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private AppStateSyncKey rotateKey(DeviceFingerprint currentFingerprint, AppStateSyncKey previousKey) {
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

        if (currentFingerprint == null) {
            LOGGER.warning("Cannot rotate key: device fingerprint not available");
            return null;
        }

        var newEpoch = SyncKeyUtils.generateNewKeyEpoch(previousKeyIdBytes);
        var myDeviceId = myJid.device();
        var newKeyId = SyncKeyUtils.buildKeyId(myDeviceId, newEpoch);

        var newKeyData = new byte[32];
        SECURE_RANDOM.nextBytes(newKeyData);

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
     * Sends the rotated key to every companion device under the key-rotation reason.
     *
     * <p>Called by {@link #getActiveKeyInternal(boolean)} after a rotation has been decided; the
     * share lets every peer encrypt outgoing mutations with the new key id and decrypt incoming
     * ones from the rotating device.
     *
     * @param key the newly minted key to share
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdKeyCallbacksApi",
            exports = "sendSyncdKeyRotation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void shareKeyWithCompanionDevices(AppStateSyncKey key) {
        var companionDevices = getCompanionDevices();
        sendAppStateSyncKeyShare(List.of(key), List.of(), companionDevices, "key_rotation");
    }

    /**
     * Sends a missing-key share back to a peer that previously requested it.
     *
     * <p>Called by the inbound {@code AppStateSyncKeyRequest} handler when this client holds the
     * requested keys. The response carries any keys that were not found locally as orphan key
     * ids so the requester can mark them missing and stop asking.
     *
     * @param keys the keys this client holds for the peer's request
     * @param orphanKeyIds the requested key ids that were not found locally
     * @param peerDeviceJid the peer device that issued the request
     */
    public void sendMissingKeyShare(List<AppStateSyncKey> keys, List<byte[]> orphanKeyIds, Jid peerDeviceJid) {
        sendAppStateSyncKeyShare(keys, orphanKeyIds, List.of(peerDeviceJid), "missing_key");
    }

    /**
     * Builds an {@code AppStateSyncKeyShare} {@link ProtocolMessage} and dispatches it to every
     * target device.
     *
     * <p>Shared backend for both {@link #shareKeyWithCompanionDevices(AppStateSyncKey)} (the
     * rotation broadcast) and {@link #sendMissingKeyShare(List, List, Jid)} (the missing-key
     * response). Orphan key ids are appended as data-less key entries so the requester can mark
     * them missing. The {@code reason} string is used in diagnostic logs only and is not encoded
     * on the wire. An empty target list, or an unavailable own JID, short-circuits the dispatch.
     *
     * @implNote This implementation iterates the per-device sends sequentially and swallows
     * individual failures with a warning log, so a single misbehaving peer cannot poison the
     * rest of the broadcast.
     *
     * @param keys the keys to share
     * @param orphanKeyIds the key ids without data, sent so the requester can mark them missing
     * @param targetDevices the JIDs to deliver the message to
     * @param reason the human-readable reason label used in diagnostic logs only
     */
    private void sendAppStateSyncKeyShare(List<AppStateSyncKey> keys, List<byte[]> orphanKeyIds, List<Jid> targetDevices, String reason) {
        if (targetDevices.isEmpty()) {
            LOGGER.fine("No target devices to share keys with");
            return;
        }

        var myJid = whatsapp.store().jid().orElse(null);
        if (myJid == null) {
            LOGGER.warning("Cannot send key share: own JID not available");
            return;
        }

        var shareKeys = new ArrayList<AppStateSyncKey>(keys.size() + orphanKeyIds.size());
        shareKeys.addAll(keys);
        for (var orphanKeyId : orphanKeyIds) {
            var orphanKey = new AppStateSyncKeyBuilder()
                    .keyId(new AppStateSyncKeyIdBuilder().keyId(orphanKeyId).build())
                    .build();
            shareKeys.add(orphanKey);
        }
        var keyShare = new AppStateSyncKeyShareBuilder()
                .keys(shareKeys)
                .build();

        var protocolMessage = new ProtocolMessageBuilder()
                .type(ProtocolMessage.Type.APP_STATE_SYNC_KEY_SHARE)
                .appStateSyncKeyShare(keyShare)
                .build();

        var messageContainer = new MessageContainerBuilder()
                .protocolMessage(protocolMessage)
                .build();

        var messages = new ArrayList<Map.Entry<Jid, ChatMessageInfo>>(targetDevices.size());
        for (var device : targetDevices) {
            var messageKey = new MessageKeyBuilder()
                    .id(MessageIdGenerator.generate(MessageIdVersion.V1, myJid))
                    .parentJid(myJid)
                    .fromMe(true)
                    .build();
            var messageInfo = new ChatMessageInfoBuilder()
                    .key(messageKey)
                    .message(messageContainer)
                    .build();
            messages.add(Map.entry(device, messageInfo));
        }

        var keyIdHexList = keys.stream()
                .map(SyncKeyUtils::syncKeyIdToHex)
                .toList();
        var deviceIdList = targetDevices.stream()
                .map(Jid::device)
                .toList();
        LOGGER.info("syncd: send key share key id " + keyIdHexList
                + " to peer deviceIds " + deviceIdList
                + " due to " + reason);

        for (var entry : messages) {
            try {
                whatsapp.sendPeerMessage(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                LOGGER.warning("Failed to send key share to device " + entry.getKey() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Returns every companion device {@link Jid} this client may share or request keys with.
     *
     * <p>The result is the registered own-device list with the current device filtered out. An
     * unavailable own JID or a missing device list yields an empty list, which short-circuits
     * the dispatch path. A device-list lookup failure falls back to a singleton primary device
     * (device id {@code 0}) so a degraded device-list state still permits a rotation share to
     * land somewhere.
     *
     * @return the companion device {@link Jid}s, or a singleton primary device on lookup failure
     */
    private List<Jid> getCompanionDevices() {
        var myJid = whatsapp.store().jid().orElse(null);
        if (myJid == null) {
            return List.of();
        }

        try {
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
        } catch (Exception e) {
            LOGGER.warning("[syncd] getPeerDevices: " + e.getMessage() + ". Key reqs->primary only");
            return List.of(Jid.of(myJid.user(), myJid.server(), 0, 0));
        }
    }

    /**
     * Starts the periodic 27-day background rotation check.
     *
     * <p>Ensures a session that has not pushed any mutations for weeks, and therefore has not
     * invoked {@link #getActiveKey(boolean)} on the encrypt path, still rotates expired keys on
     * schedule. Any previously running job is cancelled before scheduling a new one so the
     * service is idempotent across reconnects.
     */
    public void startPeriodicRotationJob() {
        stopPeriodicRotationJob();
        scheduleNextPeriodicRotation();
    }

    /**
     * Re-arms the periodic rotation check after each tick, regardless of whether the rotation
     * itself succeeded.
     *
     * <p>Schedules one rotation tick after {@link #PERIODIC_ROTATION_INTERVAL}, runs the
     * rotation, and re-arms the next tick unconditionally.
     *
     * @implNote This implementation reuses the shared Cobalt scheduler executor via
     * {@link SchedulerUtils#scheduleDelayed(Duration, Runnable)} and re-arms the next tick in a
     * {@code finally} block so a single rotation failure does not halt the periodic loop.
     */
    private void scheduleNextPeriodicRotation() {
        periodicRotationJob = SchedulerUtils.scheduleDelayed(
                PERIODIC_ROTATION_INTERVAL,
                () -> {
                    try {
                        getActiveKey(true);
                    } catch (Exception e) {
                        LOGGER.warning("Periodic key rotation check failed: " + e.getMessage());
                    } finally {
                        scheduleNextPeriodicRotation();
                    }
                }
        );
    }

    /**
     * Cancels the periodic 27-day rotation check, if any.
     *
     * <p>Called by {@link WebAppStateService} on disconnect; the partner of
     * {@link #startPeriodicRotationJob()}.
     */
    public void stopPeriodicRotationJob() {
        var job = periodicRotationJob;
        if (job != null) {
            job.cancel(false);
            periodicRotationJob = null;
        }
    }

    /**
     * Carries a snapshot of the current device fingerprint used by both rotation triggers.
     *
     * <p>Holds only the fields
     * {@link #hasADeviceBeenRemoved(AppStateSyncKey, DeviceFingerprint)} needs to compare against
     * the per-key stored fingerprint.
     *
     * @param currentIndex the live device-list current-index counter
     * @param deviceIndexes the live set of active device key indexes
     * @param rawId the live device-list raw id, or {@code -1} when unparseable
     */
    private record DeviceFingerprint(int currentIndex, Set<Integer> deviceIndexes, int rawId) {
    }
}
