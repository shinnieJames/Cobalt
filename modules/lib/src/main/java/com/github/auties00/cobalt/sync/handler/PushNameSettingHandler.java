package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.setting.PushNameSetting;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.wam.event.MdBootstrapAppStateCriticalDataProcessingEventBuilder;
import com.github.auties00.cobalt.wam.type.BootstrapAppStateDataStageCode;
import com.github.auties00.cobalt.wam.WamService;

/**
 * Applies the {@code setting_pushName} app-state action that distributes
 * the user's broadcast pushname across linked devices.
 *
 * @apiNote
 * Drives the broadcast-display-name surface: the pushname is the
 * outwards-facing name the server attaches to the user's outgoing
 * presences and to message envelopes shown to peers. Each
 * {@link SyncdOperation#SET} mutation broadcasts a fresh
 * {@code <presence name="..."/>} stanza, persists the new name to
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#setName(String)},
 * mirrors it onto the self-contact's {@code chosenName}, and fires
 * {@link com.github.auties00.cobalt.client.WhatsAppClientListener#onNameChanged}
 * on every registered listener via virtual threads. The mutation
 * index is the singleton {@snippet :
 *     ["setting_pushName"]
 * }
 *
 * @implNote
 * This implementation collapses WA Web's two preference-side writes
 * ({@code WAWebConnModel.Conn.pushname} and
 * {@code WAWebUserPrefsGeneral.setPushname}) into a single
 * {@code WhatsAppStore.setName} call: Cobalt's store is the sole
 * source of truth for the broadcast pushname. The
 * {@code WAWebSyncBootstrap.isSyncDCriticalDataSyncInProcess} ->
 * {@code setSyncDCriticalSynced} / {@code setSyncDCriticalDataSyncCompleted}
 * coordination is omitted because Cobalt tracks bootstrap state at
 * the {@link com.github.auties00.cobalt.sync.WebAppStateService}
 * layer keyed by collection (no global syncdCritical flag exists).
 * The Cobalt-only listener-fanout and self-contact mirror are added
 * because Cobalt has no equivalent of WA Web's
 * {@code BackendEventBus} / {@code Conn} observable propagation;
 * direct listener notification is the Cobalt analogue.
 */
@WhatsAppWebModule(moduleName = "WAWebPushNameSync")
public final class PushNameSettingHandler implements WebAppStateActionHandler {
    /**
     * The WAM telemetry service used to commit critical-bootstrap
     * stage events when {@link #applyMutation(WhatsAppClient, DecryptedMutation.Trusted)}
     * runs during the initial sync.
     *
     * @apiNote
     * Internal collaborator injected at construction; never accessed
     * outside {@link #logCriticalBootstrapStageIfNecessary(WhatsAppClient, BootstrapAppStateDataStageCode)}.
     */
    private final WamService wamService;

    /**
     * Constructs the push-name sync handler bound to the given WAM
     * telemetry service.
     *
     * @apiNote
     * Used by the sync handler registry; the WAM service is consulted
     * during the initial-sync window to emit
     * {@link BootstrapAppStateDataStageCode#PUSHNAME_INVALID} and
     * {@link BootstrapAppStateDataStageCode#PUSHNAME_APPLIED} stage
     * events.
     *
     * @implNote
     * This implementation mirrors the WA Web {@code WAWebPushNameSync}
     * constructor: it sets {@code collectionName = CriticalBlock} on
     * the prototype and inherits from {@code AccountSyncdActionBase}.
     *
     * @param wamService the WAM telemetry service used by this handler
     */
    @WhatsAppWebExport(moduleName = "WAWebPushNameSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public PushNameSettingHandler(WamService wamService) {
        this.wamService = wamService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPushNameSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return PushNameSetting.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPushNameSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return PushNameSetting.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPushNameSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return PushNameSetting.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation walks WA Web's per-mutation arm in
     * {@code WAWebPushNameSync.applyMutations}:
     * <ol>
     *   <li>only {@link SyncdOperation#SET} is accepted;</li>
     *   <li>the resolved pushname is read via the optional
     *       {@link PushNameSetting#name()} accessor; missing or empty
     *       defaults to {@code ""} and triggers a
     *       {@link BootstrapAppStateDataStageCode#PUSHNAME_INVALID}
     *       WAM stage emission;</li>
     *   <li>a {@code <presence name="..."/>} stanza is dispatched via
     *       {@link WhatsAppClient#sendNodeWithNoResponse(com.github.auties00.cobalt.node.Node)}
     *       (no {@code type} attribute, mirroring WA Web's
     *       {@code OPTIONAL(CUSTOM_STRING, undefined)} omission);</li>
     *   <li>the new name is persisted via
     *       {@code WhatsAppStore.setName} (the single source of truth
     *       for the broadcast pushname);</li>
     *   <li>the self-contact's
     *       {@link com.github.auties00.cobalt.model.contact.Contact#setChosenName(String)}
     *       is updated when present;</li>
     *   <li>{@link com.github.auties00.cobalt.client.WhatsAppClientListener#onNameChanged}
     *       is dispatched on every registered listener via a fresh
     *       virtual thread per listener;</li>
     *   <li>a
     *       {@link BootstrapAppStateDataStageCode#PUSHNAME_APPLIED}
     *       WAM stage is emitted on success;</li>
     *   <li>returns {@link MutationApplicationResult#success()}.</li>
     * </ol>
     * The {@code WAWebSyncBootstrap.isSyncDCriticalDataSyncInProcess}
     * coordination and the {@code WALogger} traces are dropped:
     * Cobalt tracks bootstrap state per-collection at the
     * {@link com.github.auties00.cobalt.sync.WebAppStateService}
     * layer. WA Web's outer try/catch ({@code Failed} on exception)
     * is NOT mirrored: Cobalt lets exceptions propagate so the
     * configured {@code WhatsAppClientErrorHandler} decides recovery.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPushNameSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        var resolvedName = mutation.value().action()
                .filter(PushNameSetting.class::isInstance)
                .map(PushNameSetting.class::cast)
                .flatMap(PushNameSetting::name)
                .orElse(null);
        String name;
        if (resolvedName == null || resolvedName.isEmpty()) {
            logCriticalBootstrapStageIfNecessary(client, BootstrapAppStateDataStageCode.PUSHNAME_INVALID);
            name = "";
        } else {
            name = resolvedName;
        }

        client.sendNodeWithNoResponse(new NodeBuilder()
                .description("presence")
                .attribute("name", name)
                .build());

        var oldName = client.store().name();
        client.store().setName(name);

        client.store()
                .jid()
                .flatMap(self -> client.store().findContactByJid(self.withoutData()))
                .ifPresent(contact -> contact.setChosenName(name));

        for (var listener : client.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onNameChanged(client, oldName, name));
        }

        logCriticalBootstrapStageIfNecessary(client, BootstrapAppStateDataStageCode.PUSHNAME_APPLIED);

        return MutationApplicationResult.success();
    }

    /**
     * Emits an
     * {@link com.github.auties00.cobalt.wam.event.MdBootstrapAppStateCriticalDataProcessingEvent}
     * for the given bootstrap stage when the critical data sync is
     * still in progress.
     *
     * @apiNote
     * Internal helper consumed by
     * {@link #applyMutation(WhatsAppClient, DecryptedMutation.Trusted)};
     * not used outside this class. The stage code identifies the
     * sub-step within the critical-block bootstrap (e.g.
     * {@link BootstrapAppStateDataStageCode#PUSHNAME_INVALID},
     * {@link BootstrapAppStateDataStageCode#PUSHNAME_APPLIED}).
     *
     * @implNote
     * This implementation gates the WAM emission on the
     * {@link SyncPatchType#CRITICAL_BLOCK} app state's
     * {@code bootstrapped} flag; WA Web instead consults the global
     * {@code WAWebSyncBootstrap.isSyncDCriticalDataSyncInProcess()}
     * state machine. Cobalt approximates the same gate by checking
     * whether the critical-block collection has completed its initial
     * sync: once bootstrapped, no further stage events are emitted.
     *
     * @param client the {@link WhatsAppClient} whose store is queried
     *               for bootstrap state
     * @param stage  the bootstrap stage reached
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdCriticalBootstrapProcessingApi", exports = "logCriticalBootstrapStageIfNecessary", adaptation = WhatsAppAdaptation.ADAPTED)
    private void logCriticalBootstrapStageIfNecessary(WhatsAppClient client, BootstrapAppStateDataStageCode stage) {
        if (client.store().findWebAppState(SyncPatchType.CRITICAL_BLOCK).bootstrapped()) {
            return;
        }
        this.wamService.commit(new MdBootstrapAppStateCriticalDataProcessingEventBuilder()
                .bootstrapAppStateDataStage(stage)
                .mdTimestamp((int) System.currentTimeMillis())
                .build());
    }
}
