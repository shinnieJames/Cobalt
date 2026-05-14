package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.device.TimeFormatAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Applies {@code time_format} mutations decoded from app state sync.
 *
 * <p>Handles the {@link TimeFormatAction} sync action in the
 * {@link SyncPatchType#REGULAR_LOW} collection. A mutation of this type
 * carries the user's preferred 12/24 hour time format as chosen on the
 * paired companion device, and instructs every other linked client to
 * update its own time format preference to match.
 *
 * <p>On WhatsApp Web the handler forwards the new value to the frontend
 * via {@code WAWebBackendApi.frontendFireAndForget("setIs24Hour", {is24Hour: a})}.
 * Cobalt does not ship a UI layer, so instead it persists the new value
 * directly into {@link com.github.auties00.cobalt.store.WhatsAppStore} via
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#setTwentyFourHourFormat(boolean)}.
 */
@WhatsAppWebModule(moduleName = "WAWebTimeFormatSync")
public final class TimeFormatHandler implements WebAppStateActionHandler {

    /**
     * Creates a new {@code TimeFormatHandler}.
     *
     * <p>The constructor is private because callers should always go through
     * {@link #INSTANCE}, matching the WA Web module-level singleton.
     */
    @WhatsAppWebExport(moduleName = "WAWebTimeFormatSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public TimeFormatHandler() {

    }

    /**
     * Returns the action name this handler processes.
     * @return the constant {@link TimeFormatAction#ACTION_NAME}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebTimeFormatSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return TimeFormatAction.ACTION_NAME;
    }

    /**
     * Returns the sync collection this handler's action belongs to.
     *
     * <p>On WA Web this is set on the prototype inside the constructor as
     * {@code this.collectionName = CollectionName.RegularLow}.
     * @return the constant {@link TimeFormatAction#COLLECTION_NAME}, always
     *         {@link SyncPatchType#REGULAR_LOW}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebTimeFormatSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return TimeFormatAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version this handler supports.
     * @return the constant {@link TimeFormatAction#ACTION_VERSION}, always {@code 7}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebTimeFormatSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return TimeFormatAction.ACTION_VERSION;
    }

    /**
     * Applies a single decoded time format mutation and returns the detailed result.
     *
     * <p>This method implements the body of the WA Web per-mutation callback
     * passed to {@code a.map(function(e) { ... })} inside
     * {@code applyMutations(t)}. The order of checks mirrors WA Web exactly:
     * <ol>
     *   <li><b>Operation filter</b> — WA Web: {@code if (e.operation !== "set")
     *       return r++, {actionState: Unsupported}}. Cobalt returns
     *       {@link MutationApplicationResult#unsupported()}.</li>
     *   <li><b>Missing timeFormatAction or null field</b> — WA Web reads
     *       {@code var a = (t = e.value.timeFormatAction) == null ? void 0 :
     *       t.isTwentyFourHourFormatEnabled; return a == null ?
     *       n.malformedActionIndex() : ...}. Cobalt checks that the decoded
     *       action is a {@link TimeFormatAction} and returns
     *       {@link MutationApplicationResult#malformed()} otherwise. The
     *       sub-case where {@code timeFormatAction} exists but
     *       {@code isTwentyFourHourFormatEnabled} is {@code null} is folded
     *       into the {@code false} branch via the existing nullable boolean
     *       accessor on {@link TimeFormatAction#isTwentyFourHourFormatEnabled()},
     *       per Cobalt's nullable boolean coalescing convention.</li>
     *   <li><b>Apply the new time format</b> — WA Web fires
     *       {@code WAWebBackendApi.frontendFireAndForget("setIs24Hour",
     *       {is24Hour: a})} to push the value into the frontend. Cobalt has
     *       no frontend, so it persists the value into
     *       {@link com.github.auties00.cobalt.store.WhatsAppStore#setTwentyFourHourFormat(boolean)}.</li>
     *   <li><b>Success</b> — returns {@link MutationApplicationResult#success()},
     *       matching WA Web's {@code {actionState: Success}}.</li>
     * </ol>
     *
     * <p>WA Web also tracks an {@code r} counter of unsupported operations
     * and emits a single {@code WALogger.WARN("time format sync: %s operations
     * not supported", r)} after the batch completes. That telemetry is
     * intentionally omitted in Cobalt as logging noise with no behavioral
     * impact.
     *
     * <p>WA Web does NOT consult any AB prop before applying the mutation:
     * even though {@code md_syncd_24_hour_time_format_sync_enabled} is
     * registered in {@code WAWebABPropsConfigs}, it is never read by
     * {@code WAWebTimeFormatSync} or any other module. Cobalt therefore
     * applies every well-formed {@code SET} mutation unconditionally.
     * @param client   the WhatsApp client the mutation is being applied to
     * @param mutation the trusted, decoded mutation to apply
     * @return {@link MutationApplicationResult#unsupported()} for non-{@code SET}
     *         operations; {@link MutationApplicationResult#malformed()} if the
     *         decoded action is not a {@link TimeFormatAction};
     *         {@link MutationApplicationResult#success()} otherwise
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebTimeFormatSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof TimeFormatAction action)) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        // ADAPTED: WAWebTimeFormatSync.applyMutations:
        // Cobalt has no frontend, so the value is persisted into the store
        // instead. The nullable Boolean field is coalesced to false via the
        // existing accessor on TimeFormatAction.
        client.store().setTwentyFourHourFormat(action.isTwentyFourHourFormatEnabled()); // ADAPTED: WAWebTimeFormatSync.applyMutations -> frontendFireAndForget("setIs24Hour", ...)

        // NO_WA_BASIS: the WA Web "r" unsupported counter and the trailing
        // WALogger.WARN("time format sync: %s operations not supported", r)
        // are intentionally dropped as telemetry-only logging.
        return MutationApplicationResult.success();
    }

}
