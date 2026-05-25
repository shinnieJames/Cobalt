package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.business.ctwa.CtwaDataSharingPreferenceBuilder;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.business.CtwaPerCustomerDataSharingAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Tracks per-customer Click-To-WhatsApp Ads data-sharing consent from {@code ctwaPerCustomerDataSharing} sync mutations.
 *
 * <p>This handler drives the SMB CTWA per-customer data-sharing consent
 * surface where a business owner toggles, per business-account LID, whether
 * customer-level CTWA telemetry may be shared with third-party partners. When
 * the toggle changes on another device, the server replays the resulting
 * {@link CtwaPerCustomerDataSharingAction} here, and the flag becomes readable
 * through
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#findCtwaDataSharing(String)}.
 *
 * @implNote
 * This implementation drops two WA Web side effects: the
 * {@code maybeGeneratePerCustomerDataSharingSystemMessage} and
 * {@code updateDataSharing3pdLidInCollection} /
 * {@code removeDataSharing3pdLidFromCollection} fire-and-forget
 * frontend events because Cobalt has no browser frontend bridge or
 * UI system-message pipeline. The per-LID IDB row maps to a single
 * per-LID {@link com.github.auties00.cobalt.model.business.ctwa.CtwaDataSharingPreference}
 * keyed by raw LID string in the unified store.
 */
@WhatsAppWebModule(moduleName = "WAWebCtwaPerCustomerDataSharingSync")
public final class CtwaPerCustomerDataSharingHandler implements WebAppStateActionHandler {

    /**
     * Constructs the singleton CTWA-per-customer-data-sharing handler.
     *
     * <p>The sync handler registry instantiates this once during client
     * bootstrap.
     */
    @WhatsAppWebExport(moduleName = "WAWebCtwaPerCustomerDataSharingSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public CtwaPerCustomerDataSharingHandler() {
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebCtwaPerCustomerDataSharingSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return CtwaPerCustomerDataSharingAction.ACTION_NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebCtwaPerCustomerDataSharingSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return CtwaPerCustomerDataSharingAction.COLLECTION_NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebCtwaPerCustomerDataSharingSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return CtwaPerCustomerDataSharingAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * <p>For SET mutations, validates that {@code indexParts[1]} (the account
     * LID raw string) is present and that the value carries a
     * {@link CtwaPerCustomerDataSharingAction}, then upserts a
     * {@link com.github.auties00.cobalt.model.business.ctwa.CtwaDataSharingPreference}
     * keyed by that LID. For REMOVE mutations, drops the entry by LID. Returns
     * {@link MutationApplicationResult#unsupported()} for other operations.
     *
     * @implNote
     * This implementation reads the
     * {@link CtwaPerCustomerDataSharingAction#isCtwaPerCustomerDataSharingEnabled()}
     * field which coalesces a missing wire field to {@code false};
     * WA Web treats a missing flag as malformed and emits
     * {@link SyncdIndexUtils#malformedActionValue(String)}. The
     * Cobalt model accessor is lossy on the boolean wire field so
     * the malformed branch on null-flag is unreachable here. The
     * REMOVE branch passes a possibly-null account LID through to
     * {@link com.github.auties00.cobalt.store.WhatsAppStore#removeCtwaDataSharing(String)},
     * matching WA Web's IDB-no-op semantic when the index slot is
     * missing.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebCtwaPerCustomerDataSharingSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var indexArray = JSON.parseArray(mutation.index());
        var accountLid = indexArray.size() > 1 ? indexArray.getString(1) : null;

        switch (mutation.operation()) {
            case SET -> {
                if (accountLid == null) {
                    return SyncdIndexUtils.malformedActionValue(collectionName().name());
                }

                if (!(mutation.value().action().orElse(null) instanceof CtwaPerCustomerDataSharingAction action)) {
                    return SyncdIndexUtils.malformedActionValue(collectionName().name());
                }

                var enabled = action.isCtwaPerCustomerDataSharingEnabled();

                client.store().putCtwaDataSharing(new CtwaDataSharingPreferenceBuilder()
                        .accountLid(accountLid)
                        .enabled(enabled)
                        .build());

                return MutationApplicationResult.success();
            }
            case REMOVE -> {
                client.store().removeCtwaDataSharing(accountLid);

                return MutationApplicationResult.success();
            }
            default -> {
                return MutationApplicationResult.unsupported();
            }
        }
    }

}
