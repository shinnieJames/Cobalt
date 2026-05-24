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
 * Applies the {@code nux} app-state action that records acknowledgement
 * of New User Experience prompts (onboarding hints, first-run dialogs).
 *
 * @apiNote
 * Drives the per-account NUX surface: when the user dismisses a
 * one-shot tip on any device the resulting acknowledgement fans out
 * across the {@link SyncPatchType#REGULAR_LOW} collection so the same
 * tip does not reappear on the other paired devices. The mutation
 * index keys each entry by the NUX key, formatted as
 * {@snippet :
 *     ["nux", nuxKey]
 * }
 *
 * @implNote
 * This implementation collapses WA Web's two-tier
 * {@code (NUX_LIST, NUX_DATA)} preference layout into a single
 * {@code Map<String, Boolean>} keyed by NUX key on
 * {@link com.github.auties00.cobalt.store.WhatsAppStore}; the
 * timestamp WA Web carries in {@code NUX_DATA} is not preserved
 * because no Cobalt consumer reads it. Per the WA Web source, a
 * missing {@code nuxAction} on the value defaults
 * {@code acknowledged} to {@code false} and still returns
 * {@link MutationApplicationResult#success()} (the per-batch
 * {@code WALogger.WARN} counters are dropped). Unlike the previous
 * Cobalt implementation, there is no AB-prop gate: WA Web registers
 * the handler unconditionally.
 */
@WhatsAppWebModule(moduleName = "WAWebNuxSync")
public final class NuxActionHandler implements WebAppStateActionHandler {

    /**
     * Constructs the singleton NUX sync handler.
     *
     * @apiNote
     * Used by the sync handler registry; consumers should never need to
     * call this constructor directly.
     *
     * @implNote
     * This implementation is stateless; the handler holds no
     * AB-prop / store / WAM dependency.
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
     * {@inheritDoc}
     *
     * @implNote
     * This implementation walks the per-mutation arms of WA Web's
     * batch {@code WAWebNuxSync.applyMutations}: only
     * {@link SyncdOperation#SET} is accepted; a missing or
     * non-string {@code indexParts[1]} surfaces as
     * {@link SyncdIndexUtils#malformedActionIndex(String, String)}; a
     * missing {@code nuxAction} coalesces {@code acknowledged} to
     * {@code false} and STILL writes the hint state (matching WA Web's
     * {@code (e.value.nuxAction)?.acknowledged === !0} expression).
     * The resolved {@code (nuxKey, dismissed)} pair is persisted via
     * {@code WhatsAppStore.putOnboardingHintState}; the WA Web
     * timestamp on {@code NUX_DATA} is dropped because Cobalt's typed
     * store has no consumer for it.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebNuxSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        var indexArray = JSON.parseArray(mutation.index());
        if (indexArray.size() <= 1) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }
        var nuxKey = indexArray.getString(1);
        if (nuxKey == null) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        var nuxAction = mutation.value().action().orElse(null);
        var acknowledged = nuxAction instanceof NuxAction action && action.acknowledged();

        client.store().putOnboardingHintState(new OnboardingHintStateBuilder().hintId(nuxKey).dismissed(acknowledged).build());

        return MutationApplicationResult.success();
    }

}
