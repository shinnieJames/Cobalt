package com.github.auties00.cobalt.sync.key;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.message.send.id.MessageIdGenerator;
import com.github.auties00.cobalt.message.send.id.MessageIdVersion;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.chat.ChatMessageInfoBuilder;
import com.github.auties00.cobalt.model.device.sync.MissingDeviceSyncKeyBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageContainerBuilder;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.model.message.system.ProtocolMessageBuilder;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyIdBuilder;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyRequest;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyRequestBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.MdBootstrapAppStateCriticalDataProcessingEventBuilder;
import com.github.auties00.cobalt.wam.type.BootstrapAppStateDataStageCode;

import java.time.Instant;
import java.util.*;

/**
 * Asks companion devices for app state sync keys whose key id was referenced by a snapshot or
 * patch but is absent from the local sync key store.
 *
 * <p>The service is the entry-point invoked by the syncd collection handler when patch
 * decryption fails with a {@code SyncdMissingKeyError}: it filters away ids that are already
 * tracked, broadcasts an {@code AppStateSyncKeyRequest} {@code ProtocolMessage} to every
 * companion device, persists per-device delivery outcomes through
 * {@link MissingSyncKeyTimeoutScheduler}, and emits the bootstrap-progress
 * {@code MdBootstrapAppStateCriticalDataProcessingEvent} that drives the critical-data
 * spinner on the WhatsApp Web first-load screen.
 *
 * @apiNote
 * Cobalt embedders do not call this directly. The web app-state pipeline calls
 * {@link #requestMissingKeys(Collection)} from inside the syncd snapshot/patch decrypt
 * path; the periodic {@link #reRequestMissingKeys(Collection)} entry point is driven by
 * {@link MissingSyncKeyTimeoutScheduler#startPeriodicReRequestJob()}.
 *
 * @implNote
 * This implementation collapses WA Web's {@code handleMissingKeysInSnapshot} and
 * {@code handleMissingKeysInPatches} (which separately scan snapshot records and patch
 * mutations on the WA Web side) into the inline collection that the Cobalt syncd decrypt
 * path already performs; the result is passed directly to {@link #requestMissingKeys}.
 * Per-device dispatch is sequential rather than {@code Promise.allSettled}-batched so a
 * single misbehaving companion does not stall the whole broadcast under a virtual thread.
 */
@WhatsAppWebModule(moduleName = "WAWebSyncdHandleMissingKeys")
@WhatsAppWebModule(moduleName = "WAWebSyncdRequestAllSyncdMissingKeysJob")
@WhatsAppWebModule(moduleName = "WAWebKeyManagementSendKeyRequestApi")
@WhatsAppWebModule(moduleName = "WAWebSyncdStoreMissingKeys")
public final class MissingSyncKeyRequestService {
    /**
     * Diagnostic logger for the syncd missing-key request flow.
     *
     * @implNote
     * This implementation uses {@link System.Logger} so the bridged JUL handler installed by
     * the Cobalt {@code modules/lib} bootstrap surfaces the {@code [syncd] ...} lines under
     * the same handler chain as the rest of the syncd subsystem.
     */
    private static final System.Logger LOGGER = System.getLogger(MissingSyncKeyRequestService.class.getName());

    /**
     * The injected {@link WhatsAppClient} used to dispatch peer messages and resolve the
     * shared {@link WhatsAppStore}.
     *
     * @apiNote
     * Held by reference rather than fetched lazily so {@link #sendKeyRequestToAllDevices} can
     * call {@link WhatsAppClient#sendPeerMessage} without a per-call store lookup.
     */
    private final WhatsAppClient client;

    /**
     * The shared {@link WhatsAppStore} consulted for the current device list, the offline
     * resume state, the missing-key tracker, and the {@link SyncPatchType#CRITICAL_BLOCK}
     * bootstrap flag.
     */
    private final WhatsAppStore store;

    /**
     * The companion {@link MissingSyncKeyTimeoutScheduler} reference wired in after
     * construction via {@link #setTimeoutScheduler(MissingSyncKeyTimeoutScheduler)}.
     *
     * @implNote
     * This implementation uses post-construction wiring because the scheduler also depends on
     * this service for its periodic re-request job, producing a circular construction
     * dependency that the owning {@link com.github.auties00.cobalt.sync.WebAppStateService}
     * resolves by constructing both then calling
     * {@link #setTimeoutScheduler(MissingSyncKeyTimeoutScheduler)}.
     */
    private MissingSyncKeyTimeoutScheduler timeoutScheduler;

    /**
     * The {@link WamService} used to commit the
     * {@code MdBootstrapAppStateCriticalDataProcessingEvent} progress beacons fired during
     * the critical data sync.
     */
    private final WamService wamService;

    /**
     * Constructs a new {@code MissingSyncKeyRequestService}.
     *
     * @apiNote
     * Invoked once per {@link com.github.auties00.cobalt.sync.WebAppStateService} instance.
     * The owning service is responsible for completing the wiring with
     * {@link #setTimeoutScheduler(MissingSyncKeyTimeoutScheduler)}; without that call the
     * tracker fan-out at the end of {@link #trackMissingKeys(Collection, Set)} is a no-op.
     *
     * @param client     the {@link WhatsAppClient} used to dispatch peer messages
     * @param wamService the {@link WamService} used to commit critical-bootstrap progress events
     */
    public MissingSyncKeyRequestService(WhatsAppClient client, WamService wamService) {
        this.client = client;
        this.store = client.store();
        this.wamService = wamService;
    }

    /**
     * Wires the {@link MissingSyncKeyTimeoutScheduler} dependency after construction.
     *
     * @apiNote
     * Must be invoked exactly once by {@link com.github.auties00.cobalt.sync.WebAppStateService}
     * before {@link #requestMissingKeys(Collection)} is invoked; otherwise the inline
     * timeout reschedule at the end of {@link #trackMissingKeys(Collection, Set)} is silently
     * skipped and any later {@code _setMissingKeyTimeout} guarantee is lost.
     *
     * @param timeoutScheduler the {@link MissingSyncKeyTimeoutScheduler} to wire in
     */
    public void setTimeoutScheduler(MissingSyncKeyTimeoutScheduler timeoutScheduler) {
        this.timeoutScheduler = timeoutScheduler;
    }

    /**
     * Requests the supplied missing app state sync key ids from companion devices.
     *
     * @apiNote
     * Called by {@link com.github.auties00.cobalt.sync.WebAppStateService} from inside the
     * syncd snapshot and patch decrypt paths whenever an {@code indexKey} or
     * {@code valueEncryptionKey} resolves to a key id that is not in the local sync key
     * store (the {@code SyncdMissingKeyError} branch on WA Web's
     * {@code WAWebSyncdCollectionHandler}). The method is fire-and-forget; the caller does
     * not await any companion response.
     *
     * @implNote
     * This implementation merges WA Web's {@code handleMissingKeysInSnapshot} and
     * {@code handleMissingKeysInPatches} entry points into a single id-collection that
     * Cobalt assembles inline during decryption; both entry points delegate to the same
     * {@code handleMissingKeys} body that is invoked here.
     *
     * @param keyIds the missing key ids; {@code null} entries are dropped
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdHandleMissingKeys",
            exports = {"handleMissingKeysInSnapshot", "handleMissingKeysInPatches"},
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void requestMissingKeys(Collection<byte[]> keyIds) {
        handleMissingKeys(keyIds);
    }

    /**
     * Requests a single missing app state sync key id from companion devices.
     *
     * @apiNote
     * Convenience overload used when a single decrypt failure surfaces only one id; the body
     * just wraps the id in a singleton list and delegates to
     * {@link #requestMissingKeys(Collection)}.
     *
     * @param keyId the missing key id
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdHandleMissingKeys",
            exports = "handleMissingKeys",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void requestMissingKey(byte[] keyId) {
        requestMissingKeys(List.of(keyId));
    }

    /**
     * Re-broadcasts an {@code AppStateSyncKeyRequest} for a previously-tracked set of
     * missing key ids without re-running the resume guard or duplicating tracker entries.
     *
     * @apiNote
     * Driven exclusively by {@link MissingSyncKeyTimeoutScheduler#startPeriodicReRequestJob()}
     * to mirror WA Web's six-hour {@code requestAllSyncdMissingKeysJob}, which exists to
     * recover keys when the original peer broadcast was dropped or a new companion has come
     * online since the original ask.
     *
     * @implNote
     * This implementation, like WA Web, intentionally bypasses
     * {@link #handleMissingKeys(Collection)}: the resume gate
     * ({@link WhatsAppStore#isResumeFromRestartComplete()}) and the deduplication filter
     * against {@link WhatsAppStore#findMissingSyncKey(byte[])} are skipped because the keys
     * are by construction already in the missing-key store, and the periodic job runs only
     * after resume has long completed.
     *
     * @param keyIds the missing key ids to re-broadcast; {@code null} entries are dropped
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdHandleMissingKeys",
            exports = "requestAllMissingKeys",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSyncdRequestAllSyncdMissingKeysJob",
            exports = "requestAllSyncdMissingKeysJob",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void reRequestMissingKeys(Collection<byte[]> keyIds) {
        if (keyIds.isEmpty()) {
            return;
        }

        var keyIdList = keyIds.stream()
                .filter(Objects::nonNull)
                .map(id -> new AppStateSyncKeyIdBuilder()
                        .keyId(Arrays.copyOf(id, id.length))
                        .build())
                .toList();
        var keyRequest = new AppStateSyncKeyRequestBuilder()
                .keyIds(keyIdList)
                .build();

        sendKeyRequestToAllDevices(keyRequest);
    }

    /**
     * Returns every companion {@link Jid} this client may legitimately ask for sync keys.
     *
     * @apiNote
     * The list is the registered own-device list with the current device filtered out, so
     * the broadcast in {@link #sendKeyRequestToAllDevices(AppStateSyncKeyRequest)} reaches
     * every linked phone or other web session that could plausibly hold the key.
     *
     * @implNote
     * This implementation falls back to the primary device (device id {@code 0}) when the
     * own-device list is missing or fails to load, matching WA Web's catch branch which
     * logs the same {@code Key reqs->primary only} warning before returning a singleton
     * primary-only list.
     *
     * @return the companion device {@link Jid}s, or a singleton primary device on lookup failure
     */
    @WhatsAppWebExport(moduleName = "WAWebKeyManagementUtils",
            exports = "getPeerDevices",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private List<Jid> getCompanionDevices() {
        var myJid = store.jid()
                .orElse(null);
        if (myJid == null) {
            return List.of();
        }

        try {
            var myDeviceList = store.findDeviceList(myJid.toUserJid());
            if (myDeviceList.isEmpty()) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "[syncd] getPeerDevices: no device list. Key reqs->primary only");
                return List.of(Jid.of(myJid.user(), myJid.server(), 0, 0));
            }

            return myDeviceList.get()
                    .devices()
                    .stream()
                    .filter(device -> device.id() != myJid.device())
                    .map(device -> device.toDeviceJid(myJid.user(), myJid.server()))
                    .toList();
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "[syncd] getPeerDevices: {0}. Key reqs->primary only", e.getMessage());
            return List.of(Jid.of(myJid.user(), myJid.server(), 0, 0));
        }
    }

    /**
     * Filters out already-tracked key ids, broadcasts the request, then records the new
     * trackers via {@link #trackMissingKeys(Collection, Set)}.
     *
     * @apiNote
     * The shared body invoked by both {@link #requestMissingKeys(Collection)} and the
     * single-id overload. Returns early when the syncd offline-resume sequence has not yet
     * completed (matching WA Web's {@code if (t !== "idle") return} guard) and when the
     * supplied ids are all already tracked.
     *
     * @implNote
     * This implementation defensively copies each incoming {@code byte[]} before storing it
     * because Cobalt's protobuf model exposes mutable byte arrays through its getters; WA
     * Web stores immutable {@code ArrayBuffer} views and does not need the copy.
     *
     * @param keyIds the missing key ids
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdHandleMissingKeys",
            exports = "handleMissingKeys",
            adaptation = WhatsAppAdaptation.DIRECT)
    private void handleMissingKeys(Collection<byte[]> keyIds) {
        if (keyIds.isEmpty()) {
            return;
        }

        if (!store.isResumeFromRestartComplete()) {
            LOGGER.log(System.Logger.Level.DEBUG, "syncd: _handleMissingKeys: skip, resume in progress");
            return;
        }

        var requestedKeyIds = keyIds.stream()
                .filter(Objects::nonNull)
                .map(id -> Arrays.copyOf(id, id.length))
                .filter(id -> store.findMissingSyncKey(id).isEmpty())
                .toList();
        if (requestedKeyIds.isEmpty()) {
            return;
        }

        var keyIdList = requestedKeyIds.stream()
                .map(id -> new AppStateSyncKeyIdBuilder()
                        .keyId(id)
                        .build())
                .toList();
        var keyRequest = new AppStateSyncKeyRequestBuilder()
                .keyIds(keyIdList)
                .build();

        var successfulDeviceIds = sendKeyRequestToAllDevices(keyRequest);

        trackMissingKeys(requestedKeyIds, successfulDeviceIds);
    }

    /**
     * Builds and dispatches the {@code AppStateSyncKeyRequest} peer message to every
     * companion device, returning the device ids that accepted the send.
     *
     * @apiNote
     * Throws when the device list resolves to an empty companion set (no peer can answer the
     * request) and when every per-device send fails (matching WA Web's
     * {@code throw err(...)} branch on a fully-failed {@code Promise.allSettled}). Partial
     * failure logs a warning and returns the surviving device ids.
     *
     * @implNote
     * This implementation runs the per-device sends sequentially rather than via
     * {@code Promise.allSettled}: under Cobalt's virtual-thread model the savings of parallel
     * fan-out do not justify the bookkeeping overhead, and the sequential loop preserves the
     * peer-message store ordering already established by
     * {@link WhatsAppStore#addPeerMessage(String, ChatMessageInfo)}. The
     * {@link BootstrapAppStateDataStageCode#MISSING_KEYS_REQUESTED} beacon fires after the
     * fan-out so the WAM event reflects the delivery outcome rather than just the intent.
     *
     * @param keyRequest the {@code AppStateSyncKeyRequest} payload
     * @return the device ids that successfully accepted the peer message
     * @throws IllegalStateException when no companion devices exist or every send fails
     */
    @WhatsAppWebExport(moduleName = "WAWebKeyManagementSendKeyRequestApi",
            exports = "sendAppStateSyncKeyRequest",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private Set<Integer> sendKeyRequestToAllDevices(AppStateSyncKeyRequest keyRequest) {
        var companionDevices = getCompanionDevices();

        if (companionDevices.isEmpty()) {
            throw new IllegalStateException(
                    "syncd: sendAppStateSyncKeyRequest: no peer devices available to request key from");
        }

        var messages = buildKeyRequestMessages(companionDevices, keyRequest);

        var keyIdHexes = keyRequest.keyIds().stream()
                .map(id -> id.keyId().map(SyncKeyUtils::syncKeyIdToHex).orElse("?"))
                .toList();
        var deviceIds = companionDevices.stream()
                .map(Jid::device)
                .toList();
        LOGGER.log(System.Logger.Level.INFO,
                "syncd: send key request key id {0} to peer deviceIds {1}", keyIdHexes, deviceIds);

        for (var entry : messages.entrySet()) {
            var msgId = entry.getValue().key().id().orElse(null);
            if (msgId != null) {
                store.addPeerMessage(msgId, entry.getValue());
            }
        }

        var successfulDeviceIds = new LinkedHashSet<Integer>();
        var failureMessages = new ArrayList<String>();
        for (var entry : messages.entrySet()) {
            var device = entry.getKey();
            var messageInfo = entry.getValue();
            try {
                client.sendPeerMessage(device, messageInfo);
                successfulDeviceIds.add(device.device());
            } catch (Exception e) {
                LOGGER.log(System.Logger.Level.WARNING, "Failed to send key request to device {0}: {1}",
                        device, e.getMessage());
                failureMessages.add(e.getMessage());
            }
        }

        var failureCount = failureMessages.size();
        if (failureCount > 0 && failureCount < companionDevices.size()) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "syncd: sendAppStateSyncKeyRequest: {0}/{1} peer device(s) failed",
                    failureCount, companionDevices.size());
        } else if (failureCount == companionDevices.size()) {
            var errorDetails = String.join(", ", failureMessages);
            LOGGER.log(System.Logger.Level.ERROR,
                    "[syncd] sendAppStateSyncKeyRequest: all {0} peers failed: {1}", companionDevices.size(), errorDetails);
            throw new IllegalStateException(
                    "syncd: sendAppStateSyncKeyRequest failed for all " + companionDevices.size() + " peer device(s): " + errorDetails);
        }

        logCriticalBootstrapStageIfNecessary(BootstrapAppStateDataStageCode.MISSING_KEYS_REQUESTED);

        return successfulDeviceIds;
    }

    /**
     * Emits a {@code MdBootstrapAppStateCriticalDataProcessingEvent} for the supplied
     * bootstrap stage when the critical data sync is still in progress.
     *
     * @apiNote
     * Drives the WAM-side critical-data progress beacon that surfaces the
     * "preparing your data" sub-states on the WA Web first-load screen. A no-op once the
     * {@link SyncPatchType#CRITICAL_BLOCK} collection has been bootstrapped.
     *
     * @implNote
     * This implementation approximates WA Web's
     * {@code WAWebSyncBootstrap.isSyncDCriticalDataSyncInProcess} state machine by reading
     * the {@code bootstrapped} flag on the local
     * {@link com.github.auties00.cobalt.sync.WebAppStateService} state for
     * {@link SyncPatchType#CRITICAL_BLOCK}; Cobalt does not expose a separate global
     * critical-data sync flag.
     *
     * @param stage the bootstrap stage reached
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCriticalBootstrapProcessingApi", exports = "logCriticalBootstrapStageIfNecessary", adaptation = WhatsAppAdaptation.ADAPTED)
    private void logCriticalBootstrapStageIfNecessary(BootstrapAppStateDataStageCode stage) {
        if (store.findWebAppState(SyncPatchType.CRITICAL_BLOCK).bootstrapped()) {
            return;
        }
        wamService.commit(new MdBootstrapAppStateCriticalDataProcessingEventBuilder()
                .bootstrapAppStateDataStage(stage)
                .mdTimestamp((int) System.currentTimeMillis())
                .build());
    }

    /**
     * Builds one {@code AppStateSyncKeyRequest} {@link ChatMessageInfo} per target device,
     * returning a map preserving the iteration order of the input device list.
     *
     * @apiNote
     * All messages share the same {@code ProtocolMessage} payload but each carries a
     * freshly-generated {@code MessageKey} id so the relay treats them as independent peer
     * messages rather than a duplicate broadcast.
     *
     * @implNote
     * This implementation routes every {@link MessageKeyBuilder#parentJid(Jid)} to the
     * user-level JID (no device suffix) per WA Web's
     * {@code remote: getMePnUserOrThrow_DO_NOT_USE()}. Cobalt's message infrastructure
     * additionally requires {@code senderJid} and {@code timestamp} to be populated; WA Web
     * derives both from {@code WebSendChatTransactionalUtils} downstream.
     *
     * @param companionDevices the target device {@link Jid}s; iteration order is preserved
     * @param keyRequest the {@code AppStateSyncKeyRequest} payload
     * @return a {@link LinkedHashMap} from device {@link Jid} to the built {@link ChatMessageInfo}
     */
    @WhatsAppWebExport(moduleName = "WAWebKeyManagementSendKeyRequestApi",
            exports = "sendAppStateSyncKeyRequest",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private LinkedHashMap<Jid, ChatMessageInfo> buildKeyRequestMessages(List<Jid> companionDevices, AppStateSyncKeyRequest keyRequest) {
        var self = store.jid().orElseThrow(() ->
                new IllegalStateException("syncd: sendAppStateSyncKeyRequest: no JID available"));

        var protocolMessage = new ProtocolMessageBuilder()
                .type(ProtocolMessage.Type.APP_STATE_SYNC_KEY_REQUEST)
                .appStateSyncKeyRequest(keyRequest)
                .build();
        var messageContainer = new MessageContainerBuilder()
                .protocolMessage(protocolMessage)
                .build();

        var userJid = self.toUserJid();
        var messages = new LinkedHashMap<Jid, ChatMessageInfo>();
        for (var device : companionDevices) {
            var messageKey = new MessageKeyBuilder()
                    .id(MessageIdGenerator.generate(MessageIdVersion.V1, self))
                    .parentJid(userJid)
                    .fromMe(true)
                    .senderJid(self)
                    .build();
            var messageInfo = new ChatMessageInfoBuilder()
                    .key(messageKey)
                    .message(messageContainer)
                    .timestamp(Instant.now())
                    .senderJid(self)
                    .build();
            messages.put(device, messageInfo);
        }
        return messages;
    }

    /**
     * Records each newly-asked key in the missing-key store with its asked-device set, then
     * triggers an inline reschedule of the wait-for-key timeout via
     * {@link MissingSyncKeyTimeoutScheduler#scheduleTimeoutCheck()}.
     *
     * @apiNote
     * Each entry is created with the current timestamp; existing entries for the same key
     * id are overwritten through the upsert semantics of
     * {@link WhatsAppStore#addMissingSyncKey} so the asked-device set always reflects the
     * latest broadcast.
     *
     * @implNote
     * This implementation upserts per-entry rather than calling WA Web's
     * {@code bulkUpdateMissingKeysInTransaction} (Cobalt's store does not expose a
     * single-transaction bulk-update primitive). The terminal scheduler call is gated on
     * {@link #timeoutScheduler} being non-null so test harnesses that exercise the request
     * path without the scheduler can do so without an NPE.
     *
     * @param keyIds the key ids to track
     * @param successfulDeviceIds the device ids that accepted the broadcast
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdStoreMissingKeys",
            exports = "addMissingKeys",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void trackMissingKeys(Collection<byte[]> keyIds, Set<Integer> successfulDeviceIds) {
        for (var keyId : keyIds) {
            var missingKey = new MissingDeviceSyncKeyBuilder()
                    .keyId(keyId)
                    .timestamp(Instant.now())
                    .askedDevices(Set.copyOf(successfulDeviceIds))
                    .build();
            store.addMissingSyncKey(missingKey);
        }

        if (timeoutScheduler != null) {
            timeoutScheduler.scheduleTimeoutCheck();
        }
    }
}
