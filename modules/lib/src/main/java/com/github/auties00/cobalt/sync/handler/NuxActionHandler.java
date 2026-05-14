package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.preference.OnboardingHintStateBuilder;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.device.NuxAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles NUX (New User Experience) sync mutations.
 *
 * <p>This handler processes mutations that track completion of onboarding
 * steps and new feature introductions. Per WhatsApp Web
 * {@code WAWebNuxSync}, the handler belongs to the {@code RegularLow}
 * collection, uses version {@code 7}, and routes on action name
 * {@code "nux"}.
 *
 * <p>Index format: {@code ["nux", nuxKey]}
 *
 * <p>On {@code SET}, the handler validates that {@code indexParts[1]} (the
 * {@code nuxKey}) is a string and extracts the {@code acknowledged} flag
 * from the nested {@code nuxAction} value. If {@code nuxAction} is absent,
 * {@code acknowledged} defaults to {@code false}. The resolved state is
 * written to the local NUX store.
 *
 * <p>All non-{@code SET} operations are classified as {@code UNSUPPORTED}.
 */
@WhatsAppWebModule(moduleName = "WAWebNuxSync")
public final class NuxActionHandler implements WebAppStateActionHandler {

    /**
     * Creates the singleton NUX sync handler.
     */
    @WhatsAppWebExport(moduleName = "WAWebNuxSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public NuxActionHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNuxSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return NuxAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNuxSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return NuxAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNuxSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return NuxAction.ACTION_VERSION;
    }

    /**
     * Applies a NUX mutation and returns the detailed result.
     *
     * <p>Per WhatsApp Web {@code WAWebNuxSync.applyMutations}, the handler
     * iterates the batch and, for each mutation:
     * <ol>
     *   <li>If {@code operation !== "set"}, records {@code Unsupported} and
     *       moves on (Cobalt returns {@link MutationApplicationResult#unsupported()})</li>
     *   <li>Reads {@code indexParts[1]} as the {@code nuxKey}; if it is not
     *       a string, records {@code malformedActionIndex()} and moves on</li>
     *   <li>Collects {@code {nuxKey, acknowledged: value.nuxAction?.acknowledged === true,
     *       timestamp: Number(value.timestamp)}} into a list</li>
     * </ol>
     *
     * <p>After the loop, WA Web logs the unsupported/malformed counts via
     * {@code WALogger.WARN} and, if the collected list is non-empty, calls
     * {@code WAWebUserPrefsNuxPreferences.updateNuxSyncList(list)} which
     * merges each entry into the {@code NUX_LIST} set (acknowledged keys)
     * and the {@code NUX_DATA} map (full {@code {acknowledged, timestamp}}
     * record).
     *
     * <p>In Cobalt, the store is simplified to a single
     * {@code Map<String, Boolean>} indexed by {@code nuxKey}, so the
     * timestamp from {@code NUX_DATA} is dropped; the {@code acknowledged}
     * flag is still written for both {@code true} and {@code false} values
     * (matching the {@code NUX_DATA} merge semantics). WAM logging is
     * intentionally omitted. Unlike WA Web, which iterates the whole batch
     * inside the handler, Cobalt processes mutations one-by-one through the
     * shared {@link WebAppStateActionHandler} interface; the cumulative
     * behavior is equivalent because the NUX handler has no batch-level
     * deduplication.
     *
     * <p>Note that, unlike the previous Cobalt implementation, there is no
     * {@code ABProp.NUX_SYNC} gate: {@code WAWebNuxSync} does not check any
     * AB prop, and {@code WAWebCollectionHandlerActions} registers the NUX
     * handler unconditionally.
     * @param client   the WhatsApp client
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNuxSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        var indexArray = JSON.parseArray(mutation.index());
        // WAWebNuxSync.applyMutations: var s=e.indexParts[1]; return isString(s) ? success : malformedActionIndex().
        // indexParts[1] is undefined when missing, which is not a string and yields malformed.
        if (indexArray.size() <= 1) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }
        var nuxKey = indexArray.getString(1);
        if (nuxKey == null) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        // If nuxAction is absent, `acknowledged` defaults to false (WA Web does NOT
        // return malformed in this branch — it still records the nux key with
        // acknowledged=false and returns Success).
        var nuxAction = mutation.value().action().orElse(null);
        var acknowledged = nuxAction instanceof NuxAction action && action.acknowledged();

        // into the NUX_LIST set (add on true, remove on false) and the NUX_DATA map
        // ({acknowledged, timestamp}). Cobalt drops the timestamp and stores just the
        // boolean.
        client.store().putOnboardingHintState(new OnboardingHintStateBuilder().hintId(nuxKey).dismissed(acknowledged).build()); // ADAPTED: WAWebUserPrefsNuxPreferences.updateNuxSyncList — Cobalt uses typed store quintet

        return MutationApplicationResult.success();
    }

}
