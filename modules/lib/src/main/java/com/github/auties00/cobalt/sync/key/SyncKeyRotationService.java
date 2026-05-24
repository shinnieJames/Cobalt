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
 * device removal, generates fresh keys with a monotonically increasing epoch, and shares
 * them with every peer device.
 *
 * <p>Two entry points dominate. {@link #getActiveKey(boolean)} resolves the current key for
 * outgoing patches and rotates inline when a rotation trigger fires;
 * {@link #handleKeyShare(int, List)} consumes inbound {@code AppStateSyncKeyShare}
 * protocol messages from companion devices and reconciles them against the local key store
 * and the missing-key tracker. A periodic 27-day background check
 * ({@link #startPeriodicRotationJob()}) ensures rotation also happens during long
 * mutation-free windows.
 *
 * @apiNote
 * Cobalt embedders that build their own outgoing mutation pipeline call
 * {@link #getActiveKey(boolean)} to obtain the key to encrypt with;
 * {@link #handleKeyShare(int, List)} is invoked by the inbound message stream handler when
 * an {@code APP_STATE_SYNC_KEY_SHARE} {@code ProtocolMessage} is received from a peer.
 *
 * @implNote
 * This implementation serialises the rotation flow through {@link #rotationLock} to mirror
 * WA Web's promise-queue (the {@code m.enqueue} idiom) without paying the overhead of an
 * actual queue under Loom; the synchronized block keeps the read-then-rotate-then-store
 * sequence atomic in the face of concurrent encrypt callers.
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
     * Diagnostic logger for the sync key rotation flow.
     */
    private static final Logger LOGGER = Logger.getLogger(SyncKeyRotationService.class.getName());

    /**
     * The lower bound on {@code syncd_key_max_use_days} after AB-prop clamping.
     */
    private static final int MIN_KEY_MAX_USE_DAYS = 1;

    /**
     * The upper bound on {@code syncd_key_max_use_days} after AB-prop clamping.
     */
    private static final int MAX_KEY_MAX_USE_DAYS = 90;

    /**
     * The interval between periodic rotation checks driven by
     * {@link #startPeriodicRotationJob()}, matching WA Web's
     * {@code WAWebTasksDefinitions} {@code RotateKeyTask} schedule.
     */
    private static final Duration PERIODIC_ROTATION_INTERVAL = Duration.ofDays(27);

    /**
     * The {@link SecureRandom} instance used to seed the 32-byte key data of every newly
     * rotated key.
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * The lock used to serialise the inner read-then-rotate-then-store sequence in
     * {@link #getActiveKey(boolean)}.
     *
     * @implNote
     * This implementation uses a dedicated object monitor rather than synchronising on
     * {@code this} so callers cannot inadvertently deadlock by also holding the service
     * monitor.
     */
    private final Object rotationLock = new Object();

    /**
     * The injected {@link WhatsAppClient} used to read and update the sync key store and to
     * dispatch the {@code AppStateSyncKeyShare} peer messages.
     */
    private final WhatsAppClient whatsapp;

    /**
     * The owning {@link WebAppStateService} used to schedule missing-key follow-ups when a
     * negative key share resolves a tracker entry.
     */
    private final WebAppStateService webAppStateService;

    /**
     * The {@link ABPropsService} used to read {@code syncd_key_max_use_days} and the
     * {@code wa_web_enable_syncd_key_persistence_only_after_server_ack} gate.
     */
    private final ABPropsService abPropsService;

    /**
     * The {@link WamService} used to commit
     * {@code MdAppStateKeyRotationEvent} and bootstrap-progress events.
     */
    private final WamService wamService;

    /**
     * The handle of the periodic 27-day rotation check, or {@code null} until
     * {@link #startPeriodicRotationJob()} is called.
     */
    private volatile CompletableFuture<?> periodicRotationJob;

    /**
     * Constructs a new {@code SyncKeyRotationService}.
     *
     * @apiNote
     * Invoked once per {@link WebAppStateService} instance during the syncd subsystem
     * bootstrap.
     *
     * @param whatsapp the {@link WhatsAppClient} that owns the store and the peer-message channel
     * @param webAppStateService the owning {@link WebAppStateService}
     * @param abPropsService the {@link ABPropsService} used to read rotation thresholds
     * @param wamService the {@link WamService} used to commit rotation events
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
     * @apiNote
     * Invoked by the message stream handler when an
     * {@link ProtocolMessage.Type#APP_STATE_SYNC_KEY_SHARE} message is received. Each shared
     * key is either a positive response (carrying {@code keyData}) that is added to the
     * store and clears any matching missing-key tracker, or a negative response (no
     * {@code keyData}) that records the sender as having answered without the key. After
     * reconciliation any blocked syncd collections are unblocked and resynced.
     *
     * @implNote
     * This implementation, after recording the new keys, calls
     * {@link WebAppStateService#scheduleAllDevicesRespondedCheck()} when a tracker entry now
     * has a no-key answer from every asked device, mirroring WA Web's
     * {@code _checkMissingKeyOnAllClients}. The blocked-collection unblock at the end
     * mirrors WA Web's {@code WAWebSyncd.syncBlockedCollections}.
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
     * @apiNote
     * The Cobalt message stream handler validates an inbound
     * {@code AppStateSyncKeyShare} on its own thread before delegating storage to
     * {@link #handleKeyShare(int, List)}; this method is exposed so the validator can fire
     * the bootstrap beacon at the WA Web call position
     * ({@code handleAppStateSyncKeyShare}'s first statement) regardless of whether any
     * shared key survives validation.
     *
     * @implNote
     * This implementation is a thin shim onto
     * {@link #logCriticalBootstrapStageIfNecessary(BootstrapAppStateDataStageCode)} and
     * exists only because the validator and the storage path live in different Cobalt
     * classes; WA Web fuses both in a single function body so the event call is inline
     * there.
     */
    @WhatsAppWebExport(moduleName = "WAWebKeyManagementHandleKeyShareApi", exports = "handleAppStateSyncKeyShare", adaptation = WhatsAppAdaptation.ADAPTED)
    public void logMissingKeysReceived() {
        logCriticalBootstrapStageIfNecessary(BootstrapAppStateDataStageCode.MISSING_KEYS_RECEIVED);
    }

    /**
     * Emits a {@code MdBootstrapAppStateCriticalDataProcessingEvent} for the supplied
     * bootstrap stage when the critical data sync is still in progress.
     *
     * @apiNote
     * Drives the WAM-side critical-data progress beacon used to populate the
     * "preparing your data" sub-states on first load.
     *
     * @implNote
     * This implementation approximates WA Web's
     * {@code WAWebSyncBootstrap.isSyncDCriticalDataSyncInProcess} state machine by reading
     * the {@code bootstrapped} flag for {@link SyncPatchType#CRITICAL_BLOCK}, mirroring
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
     * @apiNote
     * Called at the end of {@link #handleKeyShare(int, List)} so collections that paused
     * patching while waiting for missing key material can resume immediately when the
     * material arrives.
     *
     * @implNote
     * This implementation transitions every blocked collection to
     * {@link com.github.auties00.cobalt.store.WhatsAppStore#markWebAppStateDirty} and then
     * fires a single {@link WhatsAppClient#pullWebAppState} batched pull, mirroring WA
     * Web's {@code moveCollectionsToDirty(t)} followed by the {@code Z()} sync trigger.
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
     * Ensures the active sync key is fit for use; rotates inline when the
     * {@code triggerRotation} flag is set and either the key has expired or a companion
     * device has been removed.
     *
     * @apiNote
     * Convenience wrapper used by code paths that only need the side effect of rotation
     * without consuming the resulting key reference (for example the periodic 27-day
     * background check). Equivalent to discarding the return value of
     * {@link #getActiveKey(boolean)}.
     *
     * @param triggerRotation whether to rotate when the active key is stale
     */
    public void ensureActiveKey(boolean triggerRotation) {
        getActiveKey(triggerRotation);
    }

    /**
     * Returns the active sync key; rotates inline when {@code triggerRotation} is
     * {@code true} and the current newest key is either expired or carries a stale device
     * fingerprint.
     *
     * @apiNote
     * Called by {@link com.github.auties00.cobalt.sync.exchange.MutationRequestBuilder}
     * before encrypting outgoing patches and by the periodic background check in
     * {@link #startPeriodicRotationJob()}. {@code triggerRotation=false} is the read-only
     * mode used for diagnostics or by tests that wish to assert against the existing key
     * without inducing rotation.
     *
     * @implNote
     * This implementation serialises the read-rotate-store sequence under
     * {@link #rotationLock} so two concurrent encrypt callers cannot race into rotating
     * twice in a row.
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
     * @apiNote
     * Always called with {@link #rotationLock} held; never invoke directly.
     *
     * @implNote
     * This implementation returns the freshly rotated key directly rather than recursing
     * into {@code getActiveKeyInternal} as WA Web does (the recursive call is guaranteed to
     * resolve to the same {@code rotatedKey} now that storage is committed and neither the
     * expired nor the device-removed flag fires for the brand-new key); avoiding the
     * recursion keeps the lock-held window short and removes one layer of stack frames per
     * encrypt call.
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
     * Returns the newest sync key pair from the store using
     * {@link SyncKeyUtils#findNewestKey} ordering.
     *
     * @apiNote
     * Cobalt's outgoing mutation builder calls this directly to look up the encryption key
     * before invoking {@link #getActiveKey(boolean)} for rotation; the read-only contract
     * makes it cheap to use as a check.
     *
     * @return the newest {@link AppStateSyncKey} or {@code null} when no keys exist
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdKeyManagement",
            exports = "getNewestKeyPair",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public AppStateSyncKey getNewestKeyPair() {
        return SyncKeyUtils.findNewestKey(whatsapp.store().appStateKeys());
    }

    /**
     * Returns whether the supplied key has aged past the configured
     * {@code syncd_key_max_use_days} threshold.
     *
     * @apiNote
     * The threshold is read from the AB prop and clamped between
     * {@link #MIN_KEY_MAX_USE_DAYS} and {@link #MAX_KEY_MAX_USE_DAYS} so a misconfigured
     * AB-prop value cannot disable rotation entirely or trigger it on every call.
     *
     * @implNote
     * This implementation returns {@code false} when the key carries no timestamp; WA Web
     * relies on the same shape because the {@code keyData.timestamp} field is optional in
     * the protobuf.
     *
     * @param key the key to inspect
     * @return {@code true} when the key is past its max-use age
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
     * Returns whether a companion device has been removed since {@code key} was created, by
     * comparing the key's recorded device fingerprint against the live device list.
     *
     * @apiNote
     * Removal is one of the two rotation triggers (the other being expiry); the goal is to
     * cut a removed device's access to future mutations encrypted with the new key.
     *
     * @implNote
     * This implementation expands the key's stored {@code deviceIndexes} from
     * {@code currentIndex+1} up to the live {@code currentIndex} so devices added
     * legitimately since the key was minted do not register as removals; only a true set
     * mismatch counts.
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
     * @apiNote
     * Returns {@code null} when either the own JID is unavailable or the device list has
     * not been synced; both rotation triggers degrade to a no-op in that state rather than
     * forcing a rotation against an unknown peer set.
     *
     * @implNote
     * This implementation parses the device list's {@code rawId} as an integer because
     * Cobalt's device-list model holds it as a string; WA Web carries the integer directly
     * out of {@code WAWebApiDeviceList}.
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
     * Generates a new sync key with an incremented epoch, fresh random key data, and the
     * supplied device fingerprint.
     *
     * @apiNote
     * Returns {@code null} when any of the inputs needed to derive the new key id are
     * absent (previous key id, own JID, current fingerprint), which translates upstream
     * into "no rotation this round".
     *
     * @implNote
     * This implementation builds the 6-byte key id via
     * {@link SyncKeyUtils#buildKeyId(int, int)} (2-byte device id, 4-byte epoch) and uses a
     * {@link SecureRandom}-seeded 32-byte payload, matching WA Web's
     * {@code WAWebSyncdRotateKey.p}'s {@code getRandomValues(new Uint8Array(32))}.
     *
     * @param currentFingerprint the device fingerprint to attach to the new key
     * @param previousKey the key being rotated out; its epoch is the seed for the new epoch
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
     * Sends the rotated key to every companion device under the {@code key_rotation} reason.
     *
     * @apiNote
     * Called by {@link #getActiveKeyInternal(boolean)} after a rotation has been decided;
     * the share lets every peer encrypt outgoing mutations with the new key id and decrypt
     * incoming ones from the rotating device.
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
     * @apiNote
     * Called by the inbound {@code AppStateSyncKeyRequest} handler when this client has the
     * requested keys; the response carries any keys that were not found as
     * {@code orphanKeyIds} so the requester can mark them missing locally and stop asking.
     *
     * @param keys the keys this client holds for the peer's request
     * @param orphanKeyIds the requested key ids that were not found locally
     * @param peerDeviceJid the peer device that issued the request
     */
    public void sendMissingKeyShare(List<AppStateSyncKey> keys, List<byte[]> orphanKeyIds, Jid peerDeviceJid) {
        sendAppStateSyncKeyShare(keys, orphanKeyIds, List.of(peerDeviceJid), "missing_key");
    }

    /**
     * Builds an {@code AppStateSyncKeyShare} {@code ProtocolMessage} and dispatches it to
     * every target device.
     *
     * @apiNote
     * Shared backend for both {@link #shareKeyWithCompanionDevices(AppStateSyncKey)}
     * (rotation broadcast) and {@link #sendMissingKeyShare(List, List, Jid)} (missing-key
     * response). The {@code reason} string is for logging only and is not encoded on the
     * wire.
     *
     * @implNote
     * This implementation iterates the per-device sends sequentially and swallows
     * individual failures with a warning log; WA Web uses
     * {@code Promise.all(_.map(e => encryptAndSendKeyMsg({msg: e})))} which propagates the
     * first rejection. Cobalt's sequential, log-and-continue choice keeps a single
     * misbehaving peer from poisoning the rest of the broadcast.
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
     * @apiNote
     * Returns a singleton primary device on lookup failure so a degraded device-list state
     * still permits a rotation share to land somewhere; an empty list short-circuits the
     * dispatch path with a {@link Logger#fine} note.
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
     * @apiNote
     * Mirrors WA Web's {@code RotateKeyTask} so a session that has not pushed any
     * mutations for weeks (and therefore has not invoked
     * {@link #getActiveKey(boolean)} on the encrypt path) still rotates expired keys on
     * schedule. Cancels any previously-running job before scheduling a new one so the
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
     * @implNote
     * This implementation uses the {@link SchedulerUtils#scheduleDelayed} primitive so the
     * shared Cobalt scheduler executor is reused; the {@code finally} block re-arms the next
     * tick so a single rotation failure does not halt the periodic loop.
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
     * @apiNote
     * Called by {@link WebAppStateService} on disconnect; the partner of
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
     * Snapshot of the current device fingerprint used by both rotation triggers.
     *
     * @apiNote
     * Internal carrier type; not part of the public API. Holds only what
     * {@link #hasADeviceBeenRemoved(AppStateSyncKey, DeviceFingerprint)} needs to compare
     * against the per-key stored fingerprint.
     *
     * @param currentIndex the live device-list current index counter
     * @param deviceIndexes the live set of active device key indexes
     * @param rawId the live device-list raw id, or {@code -1} when unparseable
     */
    private record DeviceFingerprint(int currentIndex, Set<Integer> deviceIndexes, int rawId) {
    }
}
