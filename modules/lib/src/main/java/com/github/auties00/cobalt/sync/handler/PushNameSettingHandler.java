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
 * Applies the {@code setting_pushName} app-state action that distributes the
 * user's broadcast pushname across linked devices.
 *
 * <p>The pushname is the outwards-facing name the server attaches to the
 * user's outgoing presences and to message envelopes shown to peers. Each
 * {@link SyncdOperation#SET} mutation broadcasts a fresh
 * {@code <presence name="..."/>} stanza, persists the new name to
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#setName(String)},
 * mirrors it onto the self-contact's {@code chosenName}, and fires
 * {@link com.github.auties00.cobalt.client.WhatsAppClientListener#onNameChanged(WhatsAppClient, String, String)}
 * on every registered listener via virtual threads. The mutation index is the
 * singleton {@snippet :
 *     ["setting_pushName"]
 * }
 *
 * @implNote
 * This implementation collapses WA Web's two preference-side writes
 * ({@code WAWebConnModel.Conn.pushname} and
 * {@code WAWebUserPrefsGeneral.setPushname}) into a single
 * {@code WhatsAppStore.setName} call: Cobalt's store is the sole source of
 * truth for the broadcast pushname. The WA Web {@code syncdCritical}
 * coordination is omitted because Cobalt tracks bootstrap state at the
 * {@link com.github.auties00.cobalt.sync.WebAppStateService} layer keyed by
 * collection. The listener-fanout and self-contact mirror are Cobalt-only
 * because Cobalt has no equivalent of WA Web's observable propagation; direct
 * listener notification is the Cobalt analogue.
 */
@WhatsAppWebModule(moduleName = "WAWebPushNameSync")
public final class PushNameSettingHandler implements WebAppStateActionHandler {
    /**
     * Holds the WAM telemetry service used to commit critical-bootstrap stage
     * events when a mutation is applied during the initial sync.
     */
    private final WamService wamService;

    /**
     * Constructs the push-name sync handler bound to the given WAM telemetry
     * service.
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
     * {@code WAWebPushNameSync.applyMutations}: only {@link SyncdOperation#SET}
     * is accepted; the resolved pushname is read via the optional
     * {@link PushNameSetting#name()} accessor, with a missing or empty value
     * defaulting to the empty string and triggering a
     * {@link BootstrapAppStateDataStageCode#PUSHNAME_INVALID} WAM stage
     * emission; a {@code <presence name="..."/>} stanza is dispatched via
     * {@link WhatsAppClient#sendNodeWithNoResponse(com.github.auties00.cobalt.node.Node)}
     * with no {@code type} attribute; the new name is persisted via
     * {@code WhatsAppStore.setName}; the self-contact's
     * {@link com.github.auties00.cobalt.model.contact.Contact#setChosenName(String)}
     * is updated when present;
     * {@link com.github.auties00.cobalt.client.WhatsAppClientListener#onNameChanged(WhatsAppClient, String, String)}
     * is dispatched on every registered listener via a fresh virtual thread
     * per listener; and a
     * {@link BootstrapAppStateDataStageCode#PUSHNAME_APPLIED} WAM stage is
     * emitted on success. The WA Web {@code syncdCritical} coordination and the
     * trace logging are dropped. WA Web's outer try/catch is not mirrored:
     * Cobalt lets exceptions propagate so the configured
     * {@link com.github.auties00.cobalt.client.WhatsAppClientErrorHandler}
     * decides recovery.
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
     * for the given bootstrap stage when the critical data sync is still in
     * progress.
     *
     * @implNote
     * This implementation gates the WAM emission on the
     * {@link SyncPatchType#CRITICAL_BLOCK} app state's {@code bootstrapped}
     * flag; WA Web instead consults the global
     * {@code WAWebSyncBootstrap.isSyncDCriticalDataSyncInProcess()} state
     * machine. Cobalt approximates the same gate by checking whether the
     * critical-block collection has completed its initial sync: once
     * bootstrapped, no further stage events are emitted.
     *
     * @param client the {@link WhatsAppClient} whose store is queried for
     *               bootstrap state
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
