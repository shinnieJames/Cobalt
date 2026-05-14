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
 * Manages sync key rotation based on key age and device removal.
 *
 * <p>Per WhatsApp Web {@code WAWebSyncdKeyManagement}: before pushing mutations,
 * the active key is checked. If the key has expired (age exceeds
 * {@code syncd_key_max_use_days}) or a companion device has been removed
 * (fingerprint mismatch), a new key is generated and shared with companion
 * devices.
 *
 * <p>The rotation flow is:
 * <ol>
 * <li>Check if the current newest key is expired or if devices changed
 * <li>Generate a new key with incremented epoch and fresh random key data
 * <li>Store the new key locally (or send first, depending on AB prop gating)
 * <li>Share the new key with companion devices via {@code AppStateSyncKeyShare}
 * </ol>
 */
@WhatsAppWebModule(moduleName = "WAWebSyncdKeyManagement")
@WhatsAppWebModule(moduleName = "WAWebSyncdRotateKey")
@WhatsAppWebModule(moduleName = "WAWebSyncdKeyCallbacksApi")
@WhatsAppWebModule(moduleName = "WAWebSyncdHandleKeyShare")
@WhatsAppWebModule(moduleName = "WAWebKeyManagementSendKeyShareApi")
@WhatsAppWebModule(moduleName = "WAWebKeyManagementUtils")
@WhatsAppWebModule(moduleName = "WAWebTasksDefinitions")
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

    /**
     * Secure random instance for generating key data.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Lock object used to serialize access to the active key rotation flow.
     */
    private final Object rotationLock = new Object();

    /**
     * The WhatsApp client used for store access and for sending peer messages.
     */
    private final WhatsAppClient whatsapp;

    /**
     * Reference to the web app-state service used for missing-key follow-up
     * scheduling, covering both the all-devices-responded grace period and
     * the missing-key timeout rescheduling.
     */
    private final WebAppStateService webAppStateService;

    /**
     * Source of A/B-tested configuration values for rotation thresholds.
     */
    private final ABPropsService abPropsService;

    /**
     * The WAM telemetry service used to commit key rotation events.
     */
    private final WamService wamService;

    /**
     * Handle of the currently scheduled periodic rotation job, or {@code null}
     * when none is scheduled.
     */
    private volatile CompletableFuture<?> periodicRotationJob;

    /**
     * Constructs a new sync key rotation service.
     *
     * @param whatsapp           the WhatsApp client instance
     * @param webAppStateService the web app-state service used to
     *                           schedule missing-key follow-ups
     * @param abPropsService     the AB props service for threshold configuration
     * @param wamService         the WAM telemetry service for committing key rotation events
     */
    public SyncKeyRotationService(WhatsAppClient whatsapp, WebAppStateService webAppStateService, ABPropsService abPropsService, WamService wamService) {
        this.whatsapp = whatsapp;
        this.webAppStateService = webAppStateService;
        this.abPropsService = abPropsService;
        this.wamService = wamService;
    }

    /**
     * Handles an incoming app state sync key share message from a peer device.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdHandleKeyShare.handleKeyShare}: validates
     * the key data in each shared key, stores new keys that are not yet present in
     * the local sync key store, updates the missing key tracking for both positive
     * responses (keys with data) and negative responses (keys without data), and
     * finally unblocks any collections that were waiting for key material.
     *
     * <p>The flow is:
     * <ol>
     * <li>For each key with data: check if it already exists; if not, store it
     * <li>For each key with data that was tracked as missing: remove from missing key store
     * <li>For each key without data: mark the sending device as having responded without the key
     * <li>Reschedule the missing key timeout if any keys were resolved
     * <li>Check all-devices-responded if any negative responses were recorded
     * <li>Sync all blocked collections to resume pending syncs
     * </ol>
     *
     * @param senderDeviceId the device ID of the peer that sent the key share
     * @param keys           the list of shared keys from the {@code AppStateSyncKeyShare} message
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdHandleKeyShare",
            exports = "handleKeyShare",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void handleKeyShare(int senderDeviceId, List<AppStateSyncKey> keys) {
        // WAWebSyncdHandleKeyShare.handleKeyShare: r.some(e => e.fullKey != null)
        var hasAnyKeyData = keys.stream()
                .anyMatch(key -> key.keyData()
                        .flatMap(AppStateSyncKeyData::keyData)
                        .map(data -> data.length > 0)
                        .orElse(false));
        if (!hasAnyKeyData) { // WAWebSyncdHandleKeyShare.handleKeyShare: if no keys have data, log warning
            LOGGER.warning("syncd: key share from device " + senderDeviceId + " has no keys with keydata.");
        }

        var newKeysStored = new ArrayList<AppStateSyncKey>(); // WAWebSyncdHandleKeyShare.handleKeyShare: keys stored via setSyncKeyInTransaction
        var resolvedAny = false; // WAWebSyncdStoreMissingKeys.updateMissingKeys: tracks if +keys removed from missing

        for (var key : keys) { // WAWebSyncdHandleKeyShare.handleKeyShare: yield Promise.all(r.map(...))
            var keyIdBytes = key.keyId() // WAWebSyncdHandleKeyShare.handleKeyShare: var a = e.keyId
                    .flatMap(AppStateSyncKeyId::keyId)
                    .orElse(null);
            if (keyIdBytes == null) { // ADAPTED: defensive null check (WA Web skips via fullKey == null check)
                continue;
            }

            var hasKeyData = key.keyData() // WAWebSyncdHandleKeyShare.handleKeyShare: if (n != null)
                    .flatMap(AppStateSyncKeyData::keyData)
                    .map(data -> data.length > 0)
                    .orElse(false);
            var keyIdHex = SyncKeyUtils.syncKeyIdToHex(keyIdBytes); // WAWebSyncdHandleKeyShare.handleKeyShare: var i = syncKeyIdToHex(a)

            if (hasKeyData) {
                // WAWebSyncdHandleKeyShare.handleKeyShare: var l = yield getSyncKeyInTransaction_DO_NOT_USE(a)
                var existingKey = whatsapp.store().findWebAppStateKeyById(keyIdBytes).orElse(null);

                if (existingKey == null) { // WAWebSyncdHandleKeyShare.handleKeyShare: if (!l)
                    // WAWebSyncdHandleKeyShare.handleKeyShare: yield setSyncKeyInTransaction(n)
                    newKeysStored.add(key);

                    LOGGER.info("syncd: stored key share key id " + keyIdHex // WAWebSyncdHandleKeyShare.handleKeyShare: LOG("syncd: stored key share key id", ...)
                            + " from device " + senderDeviceId);
                } else {
                    // WAWebSyncdHandleKeyShare.handleKeyShare: diagnostic mismatch detection
                    var existingKeyData = existingKey.keyData()
                            .flatMap(AppStateSyncKeyData::keyData)
                            .orElse(null);
                    var incomingKeyData = key.keyData()
                            .flatMap(AppStateSyncKeyData::keyData)
                            .orElse(null);
                    if (existingKeyData != null && incomingKeyData != null
                            && !Arrays.equals(existingKeyData, incomingKeyData)) { // WAWebSyncdHandleKeyShare.handleKeyShare: !arrayBuffersEqual(l.keyData, n.keyData)
                        LOGGER.severe("syncd: got key share for existing key " + keyIdHex // WAWebSyncdHandleKeyShare.handleKeyShare: ERROR("syncd: got key share for existing key with different key data")
                                + " with different key data from device " + senderDeviceId);
                    }
                }

                // WAWebSyncdStoreMissingKeys.updateMissingKeys: +keys -> bulkRemove from missing store
                if (whatsapp.store().findMissingSyncKey(keyIdBytes).isPresent()) { // WAWebSyncdStoreMissingKeys.updateMissingKeys: a.length > 0 -> bulkRemove(a)
                    whatsapp.store().removeMissingSyncKey(keyIdBytes);
                    resolvedAny = true;
                }
            } else {
                // WAWebSyncdStoreMissingKeys.updateMissingKeys: -keys -> mark device responded without key
                if (senderDeviceId >= 0) { // ADAPTED: defensive guard on valid device ID
                    var missingKey = whatsapp.store().findMissingSyncKey(keyIdBytes).orElse(null);
                    if (missingKey != null && missingKey.wasAsked(senderDeviceId)) { // WAWebSyncdStoreMissingKeys.updateMissingKeys: bulkGet(i).filter(Boolean)
                        if (!missingKey.hasDeviceRespondedWithoutKey(senderDeviceId)) {
                            missingKey.markDeviceRespondedWithoutKey(senderDeviceId); // WAWebSyncdStoreMissingKeys.updateMissingKeys: e.deviceResponses.set(r, !1)
                        }
                        // WAWebSyncdStoreMissingKeys._checkMissingKeyOnAllClients (N): check after update
                        if (missingKey.isMissingOnAllDevices()) {
                            webAppStateService.scheduleAllDevicesRespondedCheck(); // WAWebSyncdStoreMissingKeys.N: asyncSleep(5e3) + handleSyncdFatal
                        }
                    }
                }
            }
        }

        // WAWebSyncdHandleKeyShare.handleKeyShare: yield setSyncKeyInTransaction(n) — bulk store new keys
        if (!newKeysStored.isEmpty()) { // WAWebSyncdHandleKeyShare.handleKeyShare: store keys (done per-key in WA Web, bulk in Cobalt)
            whatsapp.store().addWebAppStateKeys(newKeysStored);
        }

        // WAWebSyncdStoreMissingKeys.updateMissingKeys: yield I({MissingKeyStore: t}) — reschedule timeout after +keys removed
        if (resolvedAny) {
            webAppStateService.rescheduleMissingSyncKeyTimeout(); // WAWebSyncdStoreMissingKeys._setMissingKeyTimeout
        }

        // WAWebSyncdHandleKeyShare.handleKeyShare: o("WAWebSyncd").syncBlockedCollections()
        syncBlockedCollections();
    }

    /**
     * Emits a {@code MdBootstrapAppStateCriticalDataProcessingEvent} for the
     * {@link BootstrapAppStateDataStageCode#MISSING_KEYS_RECEIVED} stage when the
     * critical data sync is still in progress.
     *
     * <p>Per WhatsApp Web {@code WAWebKeyManagementHandleKeyShareApi.handleAppStateSyncKeyShare}:
     * the first statement of the inner async function is
     * {@code logCriticalBootstrapStageIfNecessary(MISSING_KEYS_RECEIVED)}, fired before
     * any key validation. Cobalt splits {@code handleAppStateSyncKeyShare} between
     * {@code MessageStreamHandler.processAppStateSyncKeyShare} (validation) and
     * {@link #handleKeyShare} (storage); this method is exposed so the caller-side
     * validator can fire the emission at the WA Web position, regardless of whether
     * any key survives validation.
     */
    @WhatsAppWebExport(moduleName = "WAWebKeyManagementHandleKeyShareApi", exports = "handleAppStateSyncKeyShare", adaptation = WhatsAppAdaptation.ADAPTED)
    public void logMissingKeysReceived() {
        logCriticalBootstrapStageIfNecessary(BootstrapAppStateDataStageCode.MISSING_KEYS_RECEIVED);
    }

    /**
     * Emits a {@code MdBootstrapAppStateCriticalDataProcessingEvent} for the
     * supplied bootstrap stage when the critical data sync is still in progress.
     *
     * <p>Per WhatsApp Web
     * {@code WAWebSyncdCriticalBootstrapProcessingApi.logCriticalBootstrapStageIfNecessary}:
     * the event is gated on
     * {@code WAWebSyncBootstrap.isSyncDCriticalDataSyncInProcess()}. In Cobalt that
     * global state machine is approximated by checking whether the
     * {@link SyncPatchType#CRITICAL_BLOCK} collection has been bootstrapped yet,
     * mirroring {@link WebAppStateService}.
     * @param stage the bootstrap stage reached; never {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCriticalBootstrapProcessingApi", exports = "logCriticalBootstrapStageIfNecessary", adaptation = WhatsAppAdaptation.ADAPTED)
    private void logCriticalBootstrapStageIfNecessary(BootstrapAppStateDataStageCode stage) {
        if (whatsapp.store().findWebAppState(SyncPatchType.CRITICAL_BLOCK).bootstrapped()) {
            return;
        }
        wamService.commit(new MdBootstrapAppStateCriticalDataProcessingEventBuilder()
                .bootstrapAppStateDataStage(stage) // WAWebSyncdCriticalBootstrapProcessingApi: bootstrapAppStateDataStage: e
                .mdTimestamp((int) System.currentTimeMillis()) // WAWebSyncdCriticalBootstrapProcessingApi: mdTimestamp: unixTimeMs()
                .build());
    }

    /**
     * Syncs all collections currently in {@code BLOCKED} state.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncd.syncBlockedCollections} (J): retrieves all
     * collections in the {@code Blocked} state from the state machine, transitions them
     * to {@code Dirty}, and triggers a sync round. This is called after missing sync
     * keys are received to resume syncing collections that were waiting for key material.
     */
    private void syncBlockedCollections() {
        var blockedCollections = new ArrayList<SyncPatchType>(); // WAWebSyncd.J: var t = getCollectionsInStateBlocked()
        for (var patchType : SyncPatchType.values()) { // WAWebSyncd.J: iterates blocked collections
            var metadata = whatsapp.store().findWebAppState(patchType);
            if (metadata.state() == SyncCollectionState.BLOCKED) { // WAWebSyncd.J: getCollectionsInStateBlocked
                whatsapp.store().markWebAppStateDirty(patchType); // WAWebSyncd.J: moveCollectionsToDirty(t)
                blockedCollections.add(patchType);
            }
        }
        if (!blockedCollections.isEmpty()) { // WAWebSyncd.J: Z() — schedule sync
            LOGGER.info("syncd: sync blocked collections: " + blockedCollections); // WAWebSyncd.J: LOG("syncd: sync blocked collections:", t)
            whatsapp.pullWebAppState(blockedCollections.toArray(SyncPatchType[]::new)); // WAWebSyncd.J: Z() -> ee() -> serverSync
        }
    }

    /**
     * Ensures the active key is valid for use, rotating if necessary.
     * Delegates to {@link #getActiveKey(boolean)}.
     *
     * @param triggerRotation whether to trigger rotation if the key is stale
     *                        (set to {@code false} for read-only checks)
     */
    public void ensureActiveKey(boolean triggerRotation) {
        getActiveKey(triggerRotation);
    }

    /**
     * Gets the active sync key, rotating if necessary.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdKeyManagement.getActiveKey}:
     * gets the newest key pair, checks if it has expired or if a device
     * was removed, and rotates if needed. Returns the active key pair.
     * The entire operation is serialized via a promise queue (mapped to
     * a synchronized block in Cobalt).
     *
     * @param triggerRotation whether to trigger rotation if the key is stale
     *                        (set to {@code false} for read-only checks)
     * @return the active sync key
     * @throws IllegalStateException if no sync key is available
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdKeyManagement",
            exports = "getActiveKey",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public AppStateSyncKey getActiveKey(boolean triggerRotation) {
        synchronized (rotationLock) { // WAWebSyncdKeyManagement.getActiveKey: m.enqueue(function(){return _(e)})
            return getActiveKeyInternal(triggerRotation);
        }
    }

    /**
     * Internal implementation of the active key retrieval and rotation logic.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdKeyManagement} function {@code _}:
     * retrieves the newest key pair, checks expiration and device removal,
     * rotates if necessary, and returns the active key pair.
     *
     * @param triggerRotation whether to trigger rotation if the key is stale
     * @return the active sync key
     * @throws IllegalStateException if no sync key is available
     */
    private AppStateSyncKey getActiveKeyInternal(boolean triggerRotation) {
        var newestKey = getNewestKeyPair(); // WAWebSyncdKeyManagement._: var n = yield g()
        var currentFingerprint = getCurrentDeviceFingerprint(); // WAWebSyncdKeyManagement._: var a = yield getDeviceFingerprint()
        var expired = false; // WAWebSyncdKeyManagement._: var i = !1
        var deviceRemoved = false; // WAWebSyncdKeyManagement._: var l = !1

        if (newestKey != null) { // WAWebSyncdKeyManagement._: if (n != null)
            expired = hasKeyExpired(newestKey); // WAWebSyncdKeyManagement._: i = hasKeyExpired(n)
            deviceRemoved = hasADeviceBeenRemoved(newestKey, currentFingerprint); // WAWebSyncdKeyManagement._: l = hasADeviceBeenRemoved(n, a)

            if (!triggerRotation || (!expired && !deviceRemoved)) { // WAWebSyncdKeyManagement._: if (!t || !i && !l)
                return newestKey; // WAWebSyncdKeyManagement._: return {keyId: n.keyId, keyData: n.keyData}
            }
        } else {
            throw new IllegalStateException("syncd: No sync key available"); // WAWebSyncdKeyManagement._: throw err("syncd: No sync key available")
        }

        var rotatedKey = rotateKey(currentFingerprint, newestKey); // WAWebSyncdKeyManagement._: var m = rotateKey(a, n)
        if (rotatedKey == null) { // ADAPTED: defensive null check, WA Web rotateKey always returns a value
            return newestKey;
        }

        LOGGER.info("syncd: rotating key id " + SyncKeyUtils.syncKeyIdToHex(rotatedKey)); // WAWebSyncdKeyManagement._: LOG("syncd: rotating key id", syncKeyIdToHex(m.keyId))

        if (SyncKeyUtils.getEnableSyncdKeyPersistenceOnlyAfterServerAck(abPropsService)) { // WAWebSyncdKeyManagement._: getEnableSyncdKeyPersistenceOnlyAfterServerAck() — delegated to SyncKeyUtils.getEnableSyncdKeyPersistenceOnlyAfterServerAck (WAWebSyncdGatingUtils.getEnableSyncdKeyPersistenceOnlyAfterServerAck)
            shareKeyWithCompanionDevices(rotatedKey); // WAWebSyncdKeyManagement._: yield sendSyncdKeyRotation([m])
            LOGGER.info("syncd: key share ACK received, storing key id " + SyncKeyUtils.syncKeyIdToHex(rotatedKey)); // WAWebSyncdKeyManagement._: LOG("syncd: key share ACK received, storing key id", ...)
            whatsapp.store().addWebAppStateKeys(List.of(rotatedKey)); // WAWebSyncdKeyManagement._: yield setSyncKeyInTransaction(m)
        } else { // WAWebSyncdKeyManagement._: else branch (store first, then send)
            whatsapp.store().addWebAppStateKeys(List.of(rotatedKey)); // WAWebSyncdKeyManagement._: yield setSyncKeyInTransaction(m)
            shareKeyWithCompanionDevices(rotatedKey); // WAWebSyncdKeyManagement._: yield sendSyncdKeyRotation([m])
        }

        if (expired) { // WAWebSyncdKeyManagement._: i && (LOG(...), reportSyncdKeyRotationEvent(APP_STATE_SYNC_KEY_EXPIRY))
            LOGGER.info("syncd: key rotation due to key expiry"); // WAWebSyncdKeyManagement._: LOG("syncd: key rotation due to key expiry")
            // WAWebSyncdMetrics.reportSyncdKeyRotationEvent: new MdAppStateKeyRotationWamEvent({mdAppStateKeyRotationReason: APP_STATE_SYNC_KEY_EXPIRY}).commit()
            wamService.commit(new MdAppStateKeyRotationEventBuilder()
                    .mdAppStateKeyRotationReason(MdAppStateKeyRotationReasonCode.APP_STATE_SYNC_KEY_EXPIRY)
                    .build());
        }
        if (deviceRemoved) { // WAWebSyncdKeyManagement._: l && (LOG(...), reportSyncdKeyRotationEvent(DEVICE_DEREGISTERATION))
            LOGGER.info("syncd: key rotation due to device removal"); // WAWebSyncdKeyManagement._: LOG("syncd: key rotation due to device removal")
            // WAWebSyncdMetrics.reportSyncdKeyRotationEvent: new MdAppStateKeyRotationWamEvent({mdAppStateKeyRotationReason: DEVICE_DEREGISTERATION}).commit()
            wamService.commit(new MdAppStateKeyRotationEventBuilder()
                    .mdAppStateKeyRotationReason(MdAppStateKeyRotationReasonCode.DEVICE_DEREGISTERATION)
                    .build());
        }

        // WAWebSyncdKeyManagement._: return (...,_(t)) — WA Web returns the recursive call's result,
        // which, after storing m, evaluates to {keyId: m.keyId, keyData: m.keyData}. ADAPTED: we
        // return the freshly-rotated key directly since it is guaranteed to be the one the recursive
        // call would otherwise fetch via getNewestKeyPair + expired/device checks (neither fires).
        return rotatedKey;
    }

    /**
     * Gets the newest (highest epoch) sync key pair from the store
     * without any rotation logic.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdKeyManagement.getNewestKeyPair}:
     * among all stored keys, finds those with the maximum epoch. Among those,
     * picks the one with the minimum device ID.
     *
     * @return the newest key, or {@code null} if no keys exist
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdKeyManagement",
            exports = "getNewestKeyPair",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public AppStateSyncKey getNewestKeyPair() {
        return SyncKeyUtils.findNewestKey(whatsapp.store().appStateKeys()); // WAWebSyncdKeyManagement.getNewestKeyPair: yield getAllSyncKeysInTransaction() -> max epoch, min deviceId
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
    @WhatsAppWebExport(moduleName = "WAWebSyncdRotateKey",
            exports = "hasKeyExpired",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean hasKeyExpired(AppStateSyncKey key) {
        var timestamp = key.keyData() // WAWebSyncdRotateKey.hasKeyExpired: var n = t.timestamp
                .flatMap(AppStateSyncKeyData::timestamp)
                .orElse(null);
        if (timestamp == null) {
            return false;
        }

        // WAWebSyncdRotateKey.hasKeyExpired: r = Math.min(s, Math.max(e, getSyncdKeyMaxUseDays())) — delegated to SyncKeyUtils.getSyncdKeyMaxUseDays (WAWebSyncdGatingUtils.getSyncdKeyMaxUseDays)
        var maxDays = Math.min(MAX_KEY_MAX_USE_DAYS,
                Math.max(MIN_KEY_MAX_USE_DAYS,
                        SyncKeyUtils.getSyncdKeyMaxUseDays(abPropsService)));
        // WAWebSyncdRotateKey.hasKeyExpired: a = r * DAY_MILLISECONDS, i = unixTimeMs() - n, return i > a
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
     * @param key                the key whose fingerprint to check
     * @param currentFingerprint the current device fingerprint snapshot
     * @return {@code true} if a device has been removed since the key was created
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdRotateKey",
            exports = "hasADeviceBeenRemoved",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean hasADeviceBeenRemoved(AppStateSyncKey key, DeviceFingerprint currentFingerprint) {
        var fingerprint = key.keyData() // WAWebSyncdRotateKey.hasADeviceBeenRemoved: var n = e.fingerprint
                .flatMap(AppStateSyncKeyData::fingerprint)
                .orElse(null);
        if (fingerprint == null) {
            return false;
        }

        if (currentFingerprint == null) {
            return false;
        }

        // WAWebSyncdRotateKey.hasADeviceBeenRemoved: n.rawId !== i
        var keyRawId = fingerprint.rawId().orElse(-1);
        if (keyRawId != currentFingerprint.rawId) {
            return true;
        }

        // WAWebSyncdRotateKey.hasADeviceBeenRemoved: new Set(n.deviceIndexes), expand from n.currentIndex+1 to o
        var keyDeviceIndexes = new HashSet<>(fingerprint.deviceIndexes());
        var keyCurrentIndex = fingerprint.currentIndex().orElse(0);
        for (var i = keyCurrentIndex + 1; i <= currentFingerprint.currentIndex; i++) {
            keyDeviceIndexes.add(i);
        }

        // WAWebSyncdRotateKey.hasADeviceBeenRemoved: !equalsSet(l, new Set(a))
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
    @WhatsAppWebExport(moduleName = "WAWebSyncdKeyCallbacksApi",
            exports = "getDeviceFingerprint",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebApiDeviceList",
            exports = "getMyDeviceList",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private DeviceFingerprint getCurrentDeviceFingerprint() {
        // ADAPTED: WAWebSyncdKeyCallbacksApi.getDeviceFingerprint: yield getMyDeviceList()
        var myJid = whatsapp.store().jid().orElse(null);
        if (myJid == null) {
            return null;
        }

        var deviceListOpt = whatsapp.store().findDeviceList(myJid.toUserJid());
        if (deviceListOpt.isEmpty()) {
            return null;
        }

        var deviceList = deviceListOpt.get();
        var currentIndex = deviceList.currentIndex(); // WAWebSyncdKeyCallbacksApi.getDeviceFingerprint: n = t.currentIndex
        var rawId = deviceList.rawId(); // WAWebSyncdKeyCallbacksApi.getDeviceFingerprint: i = t.rawId
        var rawIdInt = -1;
        if (rawId != null) {
            try {
                rawIdInt = Integer.parseInt(rawId);
            } catch (NumberFormatException _) {
            }
        }

        // WAWebSyncdKeyCallbacksApi.getDeviceFingerprint: a = t.devices.map(e => e.keyIndex)
        var deviceIndexes = new HashSet<Integer>();
        for (var device : deviceList.devices()) {
            deviceIndexes.add(device.keyIndex());
        }

        // WAWebSyncdKeyCallbacksApi.getDeviceFingerprint: return {currentIndex: n, deviceIndexes: a, rawId: i}
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
     * @param currentFingerprint the current device fingerprint snapshot
     * @param previousKey        the key being rotated out
     * @return the new key, or {@code null} if rotation cannot proceed
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdRotateKey",
            exports = "rotateKey",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private AppStateSyncKey rotateKey(DeviceFingerprint currentFingerprint, AppStateSyncKey previousKey) {
        var previousKeyIdBytes = previousKey.keyId() // WAWebSyncdRotateKey.m: e.keyId
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

        // WAWebSyncdRotateKey.m: t = generateNewKeyEpoch(e.keyId)
        var newEpoch = SyncKeyUtils.generateNewKeyEpoch(previousKeyIdBytes);
        // WAWebSyncdRotateKey.m: r = interpretAsNumber(extractDeviceId(getMyDeviceJid()))
        var myDeviceId = myJid.device();
        // WAWebSyncdRotateKey.m: keyId = toSyncKeyId(concat(intToBytes(2, r), intToBytes(4, t)))
        var newKeyId = SyncKeyUtils.buildKeyId(myDeviceId, newEpoch);

        // WAWebSyncdRotateKey.p: getRandomValues(new Uint8Array(32))
        var newKeyData = new byte[32];
        SECURE_RANDOM.nextBytes(newKeyData);

        // WAWebSyncdRotateKey.d: fingerprint: e (the passed-in fingerprint)
        var fingerprint = new AppStateSyncKeyFingerprintBuilder()
                .rawId(currentFingerprint.rawId)
                .currentIndex(currentFingerprint.currentIndex)
                .deviceIndexes(new ArrayList<>(currentFingerprint.deviceIndexes))
                .build();

        // WAWebSyncdRotateKey.d: timestamp: unixTimeMs()
        var keyData = new AppStateSyncKeyDataBuilder()
                .keyData(newKeyData)
                .fingerprint(fingerprint)
                .timestamp(Instant.now())
                .build();

        var keyIdProto = new AppStateSyncKeyIdBuilder()
                .keyId(newKeyId)
                .build();

        // WAWebSyncdRotateKey.d: return {keyId: a, keyEpoch: r, keyData: i, fingerprint: e, timestamp: l}
        return new AppStateSyncKeyBuilder()
                .keyId(keyIdProto)
                .keyData(keyData)
                .build();
    }

    /**
     * Shares the rotated key with all companion devices.
     *
     * <p>Per WhatsApp Web {@code WAWebSyncdKeyCallbacksApi.sendSyncdKeyRotation}:
     * wraps the key list in an {@code AppStateSyncKeyShare} protocol message with type
     * {@code key_rotation} and sends it to all peer devices via
     * {@code WAWebKeyManagementSendKeyShareApi.sendAppStateSyncKeyShare}.
     *
     * @param key the new key to share
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdKeyCallbacksApi",
            exports = "sendSyncdKeyRotation",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void shareKeyWithCompanionDevices(AppStateSyncKey key) {
        var companionDevices = getCompanionDevices(); // WAWebKeyManagementSendKeyShareApi.sendAppStateSyncKeyShare: i = yield getPeerDevices()
        sendAppStateSyncKeyShare(List.of(key), List.of(), companionDevices, "key_rotation"); // WAWebSyncdKeyCallbacksApi.sendSyncdKeyRotation: sendAppStateSyncKeyShare({type:"key_rotation",keys:t})
    }

    /**
     * Sends a missing key share response to a specific peer device.
     *
     * <p>Per WhatsApp Web {@code WAWebKeyManagementSendKeyShareApi.sendAppStateSyncKeyShare}
     * with {@code type === "missing_key"}: constructs an {@code AppStateSyncKeyShare} protobuf
     * from the found keys (with full key data) and orphan keys (key ID only, no data), then
     * sends it to the requesting peer device.
     *
     * <p>This is the {@code missing_key} branch of the WA Web function, invoked from
     * {@code WAWebSendRequestedKeyShareJob.sendRequestedKeyShare} when handling an incoming
     * {@code AppStateSyncKeyRequest} from a peer device.
     *
     * @param keys           the found keys with full key data
     * @param orphanKeyIds   the key IDs that were not found locally (sent with {@code null} key data)
     * @param peerDeviceJid  the JID of the peer device that requested the keys
     */
    public void sendMissingKeyShare(List<AppStateSyncKey> keys, List<byte[]> orphanKeyIds, Jid peerDeviceJid) {
        sendAppStateSyncKeyShare(keys, orphanKeyIds, List.of(peerDeviceJid), "missing_key"); // WAWebKeyManagementSendKeyShareApi.sendAppStateSyncKeyShare: type === "missing_key"
    }

    /**
     * Core implementation of the app state sync key share sending logic.
     *
     * <p>Per WhatsApp Web {@code WAWebKeyManagementSendKeyShareApi.sendAppStateSyncKeyShare}
     * (function {@code c}): builds the {@code AppStateSyncKeyShare} protobuf from the provided
     * keys and optional orphan key IDs, constructs a peer protocol message for each target device,
     * stores them, and sends them via {@code encryptAndSendKeyMsg}.
     *
     * <p>The function handles two call patterns:
     * <ul>
     *   <li>{@code key_rotation}: keys from rotation, all peer devices as targets
     *   <li>{@code missing_key}: keys + orphan key IDs responding to a specific device
     * </ul>
     *
     * @param keys          the keys to share (with full key data)
     * @param orphanKeyIds  key IDs without data (empty list if none; non-empty for missing_key responses)
     * @param targetDevices the list of target device JIDs to send the share to
     * @param reason        the reason string for logging ({@code "key_rotation"} or {@code "missing_key"})
     */
    private void sendAppStateSyncKeyShare(List<AppStateSyncKey> keys, List<byte[]> orphanKeyIds, List<Jid> targetDevices, String reason) {
        if (targetDevices.isEmpty()) { // ADAPTED: defensive guard
            LOGGER.fine("No target devices to share keys with");
            return;
        }

        var myJid = whatsapp.store().jid().orElse(null);
        if (myJid == null) { // ADAPTED: defensive null check
            LOGGER.warning("Cannot send key share: own JID not available");
            return;
        }

        // WAWebKeyManagementSendKeyShareApi.d: build AppStateSyncKeyShare protobuf
        var shareKeys = new ArrayList<AppStateSyncKey>(keys.size() + orphanKeyIds.size());
        shareKeys.addAll(keys); // WAWebKeyManagementSendKeyShareApi.d: e.map(key -> {keyId, keyData})
        for (var orphanKeyId : orphanKeyIds) { // WAWebKeyManagementSendKeyShareApi.d: t.map(e -> {keyId: {keyId: fromSyncKeyId(e)}, keyData: void 0})
            var orphanKey = new AppStateSyncKeyBuilder()
                    .keyId(new AppStateSyncKeyIdBuilder().keyId(orphanKeyId).build())
                    .build();
            shareKeys.add(orphanKey);
        }
        var keyShare = new AppStateSyncKeyShareBuilder() // WAWebKeyManagementSendKeyShareApi.d: return {keys: n}
                .keys(shareKeys)
                .build();

        var protocolMessage = new ProtocolMessageBuilder()
                .type(ProtocolMessage.Type.APP_STATE_SYNC_KEY_SHARE)
                .appStateSyncKeyShare(keyShare)
                .build();

        var messageContainer = new MessageContainerBuilder()
                .protocolMessage(protocolMessage)
                .build();

        // WAWebKeyManagementSendKeyShareApi.sendAppStateSyncKeyShare: _ = i.map(device -> message object)
        var messages = new ArrayList<Map.Entry<Jid, ChatMessageInfo>>(targetDevices.size());
        for (var device : targetDevices) {
            var messageKey = new MessageKeyBuilder()
                    .id(MessageIdGenerator.generate(MessageIdVersion.V1, myJid)) // WAWebMsgKey.newId_DEPRECATED
                    .parentJid(myJid) // WAWebMsgKey: remote: getMePnUserOrThrow_DO_NOT_USE()
                    .fromMe(true) // WAWebMsgKey: fromMe: true
                    .build();
            var messageInfo = new ChatMessageInfoBuilder()
                    .key(messageKey)
                    .message(messageContainer)
                    .build();
            messages.add(Map.entry(device, messageInfo));
        }

        // WAWebKeyManagementSendKeyShareApi.sendAppStateSyncKeyShare: log before sending
        var keyIdHexList = keys.stream() // WAWebKeyManagementSendKeyShareApi: g = t.keys.map(e => syncKeyIdToHex(e.keyId))
                .map(SyncKeyUtils::syncKeyIdToHex)
                .toList();
        var deviceIdList = targetDevices.stream() // WAWebKeyManagementSendKeyShareApi: f = i.map(e => e.getDeviceId())
                .map(Jid::device)
                .toList();
        LOGGER.info("syncd: send key share key id " + keyIdHexList // WAWebKeyManagementSendKeyShareApi: LOG("syncd: send key share key id", g, "to peer deviceIds", f, "due to", t.type)
                + " to peer deviceIds " + deviceIdList
                + " due to " + reason);

        // WAWebKeyManagementSendKeyShareApi.sendAppStateSyncKeyShare: yield storePeerMessages(_)
        // ADAPTED: Cobalt's sendPeerMessage handles peer message storage internally
        // WAWebKeyManagementSendKeyShareApi.sendAppStateSyncKeyShare: yield Promise.all(_.map(e => encryptAndSendKeyMsg({msg: e})))
        for (var entry : messages) {
            try {
                whatsapp.sendPeerMessage(entry.getKey(), entry.getValue()); // WAWebSendAppStateSyncMsgJob.encryptAndSendKeyMsg
            } catch (Exception e) {
                LOGGER.warning("Failed to send key share to device " + entry.getKey() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Gets companion device JIDs (all devices except our own).
     *
     * <p>Per WhatsApp Web {@code WAWebKeyManagementUtils.getPeerDevices}:
     * returns all peer devices from the device list, falling back to
     * the primary device (device 0) if the device list cannot be retrieved.
     *
     * @return the list of companion device JIDs
     */
    private List<Jid> getCompanionDevices() {
        var myJid = whatsapp.store().jid().orElse(null); // WAWebKeyManagementUtils.getPeerDevices: getMeDevicePnOrThrow()
        if (myJid == null) {
            return List.of();
        }

        try {
            var myDeviceList = whatsapp.store().findDeviceList(myJid.toUserJid()); // WAWebKeyManagementUtils.getPeerDevices: yield getMyDeviceList()
            if (myDeviceList.isEmpty()) {
                return List.of();
            }

            return myDeviceList.get()
                    .devices()
                    .stream()
                    .filter(device -> device.id() != myJid.device()) // WAWebKeyManagementUtils.getPeerDevices: e.id !== n.getDeviceId()
                    .map(device -> device.toDeviceJid(myJid.user(), myJid.server())) // WAWebKeyManagementUtils.getPeerDevices: createDeviceWidFromUserAndDevice
                    .toList();
        } catch (Exception e) {
            // WAWebKeyManagementUtils.getPeerDevices: catch -> fallback to primary device
            LOGGER.warning("[syncd] getPeerDevices: " + e.getMessage() + ". Key reqs->primary only");
            return List.of(Jid.of(myJid.user(), myJid.server(), 0, 0));
        }
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

    /**
     * Schedules the next periodic rotation check after the configured interval.
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
