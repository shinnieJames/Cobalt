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
 * Applies {@code setting_pushName} mutations decoded from app state sync.
 *
 * <p>Handles the {@link PushNameSetting} sync action in the
 * {@link SyncPatchType#CRITICAL_BLOCK} collection. A mutation of this type
 * carries the user's display name (the "push name") as chosen on the paired
 * companion device, and instructs every other linked client to update its
 * own broadcast pushname to match.
 *
 * <p>On WhatsApp Web the handler:
 * <ol>
 *   <li>Sends an outbound {@code <presence name="..."/>} stanza so that the
 *       server propagates the new pushname to peers
 *       ({@code WASendPresenceStatusProtocol.sendPresenceStatusProtocol}).</li>
 *   <li>Persists the new pushname locally via
 *       {@code WAWebSetPushnameLocallyAction.setPushnameLocally}, which writes
 *       to {@code WAWebConnModel.Conn.pushname} and to the
 *       {@code LAST_PUSHNAME} user preference.</li>
 * </ol>
 *
 * <p>Cobalt mirrors both side effects: the {@code <presence name="..."/>}
 * stanza is dispatched through {@link WhatsAppClient#sendNodeWithNoResponse},
 * and the new name is persisted into {@link com.github.auties00.cobalt.store.WhatsAppStore#setName(String)}
 * (Cobalt's analogue of {@code Conn.pushname}/{@code LAST_PUSHNAME}). The
 * change is then broadcast to every registered
 * {@link com.github.auties00.cobalt.client.WhatsAppClientListener} on a
 * virtual thread, mirroring the {@link LocaleSettingHandler} pattern.
 */
@WhatsAppWebModule(moduleName = "WAWebPushNameSync")
public final class PushNameSettingHandler implements WebAppStateActionHandler {
    /**
     * The WAM telemetry service used to commit the bootstrap stage events
     * during {@code applyMutation}.
     */
    private final WamService wamService;

    /**
     * Creates a new {@code PushNameSettingHandler}.
     *
     * <p>WA Web instantiates the handler exactly once at module evaluation
     * time via {@code var y = new h; l.default = y;}. Cobalt mirrors that
     * with a registry-injected instance carrying its WAM dependency.
     * @param wamService the WAM telemetry service used by this handler
     */
    @WhatsAppWebExport(moduleName = "WAWebPushNameSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public PushNameSettingHandler(WamService wamService) {
        this.wamService = wamService;
    }

    /**
     * Returns the action name this handler processes.
     * @return the constant {@link PushNameSetting#ACTION_NAME}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPushNameSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return PushNameSetting.ACTION_NAME;
    }

    /**
     * Returns the sync collection this handler's action belongs to.
     *
     * <p>On WA Web this is set on the prototype inside the constructor as
     * {@code this.collectionName = CollectionName.CriticalBlock}.
     * @return the constant {@link PushNameSetting#COLLECTION_NAME}, always
     *         {@link SyncPatchType#CRITICAL_BLOCK}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPushNameSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return PushNameSetting.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version this handler supports.
     * @return the constant {@link PushNameSetting#ACTION_VERSION}, always {@code 1}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPushNameSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return PushNameSetting.ACTION_VERSION;
    }

    /**
     * Applies a single decoded pushname mutation and returns the detailed result.
     *
     * <p>This method implements the body of the WA Web per-mutation callback
     * passed to {@code Promise.all(t.map(async e => { ... }))} inside
     * {@code applyMutations(t)}. The order of checks mirrors WA Web exactly:
     * <ol>
     *   <li><b>Operation filter</b> — WA Web falls through to the final
     *       {@code i++; return {actionState: Unsupported};} branch for any
     *       operation other than {@code "set"}. Cobalt returns
     *       {@link MutationApplicationResult#unsupported()}.</li>
     *   <li><b>Read pushname</b> — WA Web reads
     *       {@code _ = e.value.pushNameSetting?.name}. The optional chain
     *       quietly tolerates a missing {@code pushNameSetting} field; the
     *       only requirement is that the decoded {@code SyncActionValue}
     *       exists. Cobalt mirrors that by reading the optional
     *       {@link PushNameSetting#name()} via the action accessor without
     *       failing when the field is absent.</li>
     *   <li><b>Falsy default</b> — WA Web: {@code _ || (a++,
     *       logCriticalBootstrapStageIfNecessary(PUSHNAME_INVALID), _="")}.
     *       When the pushname is missing or empty it defaults to
     *       {@code ""} and a critical-bootstrap WAM event is logged.
     *       Cobalt drops the WAM increment and the {@code a++} counter
     *       (telemetry only) but preserves the empty-string default.</li>
     *   <li><b>Broadcast new presence</b> — WA Web awaits
     *       {@code WASendPresenceStatusProtocol.sendPresenceStatusProtocol(
     *       {name: _})}, which goes through
     *       {@code WASmaxOutPresenceAvailabilityRequest.makeAvailabilityRequest}
     *       and emits a {@code <presence name="..."/>} stanza via
     *       {@code WAComms.castSmaxStanza}. Cobalt builds the same stanza
     *       through {@link NodeBuilder} and dispatches it via
     *       {@link WhatsAppClient#sendNodeWithNoResponse(com.github.auties00.cobalt.node.Node)}.
     *       The {@code type} attribute is left out because the call site
     *       passes {@code status = undefined}, so WA's
     *       {@code OPTIONAL(CUSTOM_STRING, undefined)} omits it from the wire.</li>
     *   <li><b>Persist locally</b> — WA Web calls
     *       {@code WAWebSetPushnameLocallyAction.setPushnameLocally(_)}, which
     *       writes {@code WAWebConnModel.Conn.pushname = _} and
     *       {@code WAWebUserPrefsGeneral.setPushname(_)} (the
     *       {@code LAST_PUSHNAME} user pref). Cobalt collapses both writes
     *       into {@link com.github.auties00.cobalt.store.WhatsAppStore#setName(String)},
     *       which is the single source of truth for the broadcast pushname.</li>
     *   <li><b>Mirror into self-contact</b> — Cobalt-only side effect: when a
     *       {@code Contact} entry exists for the local user (Cobalt models the
     *       self-jid in the contacts map), its {@code chosenName} is updated
     *       so downstream consumers reading from the self-contact stay in
     *       sync with {@code store.name()}.</li>
     *   <li><b>Notify listeners</b> — Cobalt fires
     *       {@code onNameChanged(client, oldName, newName)} on every registered
     *       {@code WhatsAppClientListener}, mirroring the
     *       {@link LocaleSettingHandler#applyMutation} pattern of
     *       converting WA Web frontend IPC into Cobalt event broadcasts.</li>
     *   <li><b>Success</b> — returns {@link MutationApplicationResult#success()}.</li>
     * </ol>
     *
     * <p>WA Web also performs critical-bootstrap state coordination here: if
     * {@code WAWebSyncBootstrap.isSyncDCriticalDataSyncInProcess()} returns
     * {@code true}, it awaits {@code setSyncDCriticalSynced()} and
     * {@code setSyncDCriticalDataSyncCompleted()} to mark the
     * {@code syncdCritical} flag in the bootstrap tracker. Cobalt's bootstrap
     * coordination happens at a different layer
     * ({@code WebAppStateService}/{@code SyncCollectionMetadata.bootstrapped})
     * driven per-collection by the broader sync flow, so this handler does
     * not need to flip a global flag.
     *
     * <p>WA Web increments local telemetry counters ({@code a}, {@code i}),
     * emits {@code WALogger.LOG}/{@code WALogger.WARN} messages and logs
     * critical-bootstrap WAM stages ({@code PUSHNAME_INVALID},
     * {@code PUSHNAME_APPLIED}). These are intentionally omitted in Cobalt
     * as telemetry/logging noise with no behavioral impact.
     *
     * <p>WA Web wraps the whole per-mutation body in a {@code try/catch} that
     * swallows any exception, logs {@code PUSHNAME_INVALID}, and returns
     * {@code {actionState: Failed}}. In Cobalt, exceptions are allowed to
     * propagate and the configured {@code WhatsAppClientErrorHandler} decides
     * recovery, per Cobalt's pluggable error model.
     * @param client   the WhatsApp client the mutation is being applied to
     * @param mutation the trusted, decoded mutation to apply
     * @return {@link MutationApplicationResult#unsupported()} for non-{@code SET}
     *         operations; {@link MutationApplicationResult#success()} otherwise
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPushNameSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        // Cobalt: read the optional name from the decoded action; treat missing pushNameSetting the same as a missing name (WA Web's optional chain).
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

        // -> WASmaxOutPresenceAvailabilityRequest.makeAvailabilityRequest({presenceType: undefined, presenceName: _})
        // -> smax("presence", {type: OPTIONAL(CUSTOM_STRING, undefined), name: OPTIONAL(CUSTOM_STRING, _)})
        // -> WAComms.castSmaxStanza(...)
        client.sendNodeWithNoResponse(new NodeBuilder()
                .description("presence") // WASmaxOutPresenceAvailabilityRequest.makeAvailabilityRequest: smax("presence", {...})
                .attribute("name", name) // WASmaxOutPresenceAvailabilityRequest.makeAvailabilityRequest: name: OPTIONAL(CUSTOM_STRING, n)
                .build());

        // -> WAWebConnModel.Conn.pushname = _ + WAWebUserPrefsGeneral.setPushname(_)
        // Cobalt collapses both writes into store.setName(), the single source of truth for the broadcast pushname.
        var oldName = client.store().name();
        client.store().setName(name);

        // ADAPTED: Cobalt-only — keep the self-contact's chosenName aligned with the broadcast pushname
        // so downstream consumers that read the self-jid contact stay consistent. WA Web does not touch
        // the contact collection here because it stores Conn.pushname separately from contacts.
        client.store()
                .jid()
                .flatMap(self -> client.store().findContactByJid(self.withoutData()))
                .ifPresent(contact -> contact.setChosenName(name)); // ADAPTED: keep Cobalt's self-contact in sync

        // ADAPTED: WAWebPushNameSync.applyMutations — Cobalt notifies registered listeners on virtual threads
        // instead of relying on WA Web's BackendEventBus / Conn observable propagation.
        for (var listener : client.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onNameChanged(client, oldName, name));
        }

        logCriticalBootstrapStageIfNecessary(client, BootstrapAppStateDataStageCode.PUSHNAME_APPLIED);

        // NO_WA_BASIS: the following WA Web telemetry/logging is intentionally dropped:
        //   - a/i counters and the trailing WALogger.LOG/WARN calls
        // ADAPTED: WAWebPushNameSync.applyMutations:
        //   if (WAWebSyncBootstrap.isSyncDCriticalDataSyncInProcess()) {
        //     yield WAWebSyncBootstrap.setSyncDCriticalSynced();
        //     yield WAWebSyncBootstrap.setSyncDCriticalDataSyncCompleted();
        //   }
        // Cobalt's bootstrap tracking lives at the collection level
        // (SyncCollectionMetadata.bootstrapped) and is flipped by
        // WebAppStateService/MutationRequestBuilder, not by individual
        // setting handlers. There is no global syncdCritical flag to flip here.
        return MutationApplicationResult.success();
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
     * {@link SyncPatchType#CRITICAL_BLOCK}
     * collection has been bootstrapped yet, matching
     * {@link com.github.auties00.cobalt.sync.WebAppStateService}.
     * @param client     the WhatsApp client whose store is queried for bootstrap state
     * @param stage      the bootstrap stage reached; never {@code null}
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
