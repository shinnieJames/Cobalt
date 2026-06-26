package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.business.BroadcastListParticipant;
import com.github.auties00.cobalt.model.business.BroadcastListParticipantBuilder;
import com.github.auties00.cobalt.model.business.BusinessBroadcastListBuilder;
import com.github.auties00.cobalt.model.sync.mutation.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.action.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastListAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.store.linked.LinkedWhatsAppBusinessStore;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Maintains the business broadcast-list catalog from {@code business_broadcast_list} sync mutations.
 *
 * <p>A broadcast list is a named set of recipients used as a target for
 * marketing-message campaigns. When a broadcast list is created, edited, or
 * deleted on another device, the server replays the change here as a
 * {@link SyncdOperation#SET} (upsert) or {@link SyncdOperation#REMOVE}; the
 * result is read back via
 * {@link LinkedWhatsAppBusinessStore#findBusinessBroadcastList(String)}.
 *
 * @implNote
 * This implementation stores the wire-shape protobuf action directly
 * in a single typed quintet rather than running WA Web's
 * {@code WAWebAudienceExpressionTypes} compile pass to materialize an
 * audience predicate from {@code labelIds} or
 * {@code audienceExpression}. WA Web's
 * {@code isBizBroadcastSendWebEnabledNoExposure()} AB-prop gate is
 * intentionally not replicated; the {@code getMaybeMeLidUser()}
 * self-filter that drops the local user from the participant list is
 * not applied because Cobalt mirrors the wire participant array as-is.
 */
@WhatsAppWebModule(moduleName = "WAWebBroadcastListSync")
public final class BusinessBroadcastListHandler implements WebAppStateActionHandler {
    /**
     * The handler-scoped {@link Logger} used to emit the per-batch malformed-mutation summary.
     *
     * <p>Records the count of malformed mutations after each batch.
     */
    private static final Logger LOGGER = Logger.getLogger(BusinessBroadcastListHandler.class.getName());

    /**
     * Constructs the singleton broadcast-list handler.
     *
     * <p>The sync handler registry instantiates this type exactly once.
     */
    @WhatsAppWebExport(moduleName = "WAWebBroadcastListSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public BusinessBroadcastListHandler() {

    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebBroadcastListSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return BusinessBroadcastListAction.ACTION_NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebBroadcastListSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return BusinessBroadcastListAction.COLLECTION_NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebBroadcastListSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return BusinessBroadcastListAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * <p>For {@link SyncdOperation#SET} mutations, upserts a
     * {@link com.github.auties00.cobalt.model.business.BusinessBroadcastList}
     * keyed by the {@code listId} in index slot 1, mirroring the full
     * {@link BusinessBroadcastListAction} wire shape including participants,
     * label ids, audience expression, and the {@link BusinessBroadcastListAction#deleted()}
     * tombstone flag. For {@link SyncdOperation#REMOVE} mutations, drops the
     * list by id. Returns
     * {@link SyncdIndexUtils#malformedActionIndex(String, String)} when the
     * index slot is empty, {@link SyncdIndexUtils#malformedActionValue(String)}
     * when the value is missing, and {@link MutationApplicationResult#failed()}
     * for unknown operations or any thrown exception.
     *
     * @implNote
     * This implementation copies the participant array and label-ids
     * list into mutable {@link ArrayList}s and stores them as-is rather
     * than compiling the audience expression via WA Web's
     * {@code parseAudienceExpressionJson} /
     * {@code createLabelPredicateExpression} /
     * {@code createExplicitExpression} pipeline. An empty array is
     * normalized to {@code null} so the stored shape matches the wire
     * shape.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBroadcastListSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(LinkedWhatsAppClient client, DecryptedMutation.Trusted mutation) {
        try {
            var indexArray = JSON.parseArray(mutation.index());
            if (indexArray.size() <= 1) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }
            var listId = indexArray.getString(1);
            if (listId == null || listId.isEmpty()) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }

            if (mutation.operation() == SyncdOperation.SET) {
                if (!(mutation.value().flatMap(sav -> sav.action()).orElse(null) instanceof BusinessBroadcastListAction action)) {
                    return SyncdIndexUtils.malformedActionValue(collectionName().name());
                }

                List<BroadcastListParticipant> mirroredParticipants = null;
                if (!action.participants().isEmpty()) {
                    mirroredParticipants = new ArrayList<>(action.participants().size());
                    for (var p : action.participants()) {
                        mirroredParticipants.add(new BroadcastListParticipantBuilder()
                                .lidJid(p.lidJid())
                                .pnJid(p.pnJid().orElse(null))
                                .build());
                    }
                }
                List<String> mirroredLabelIds = action.labelIds().isEmpty() ? null : new ArrayList<>(action.labelIds());
                client.store().businessStore().putBusinessBroadcastList(new BusinessBroadcastListBuilder()
                        .id(listId)
                        .deleted(action.deleted())
                        .participants(mirroredParticipants)
                        .listName(action.listName().orElse(null))
                        .labelIds(mirroredLabelIds)
                        .audienceExpression(action.audienceExpression().orElse(null))
                        .build());
                return MutationApplicationResult.success();
            }

            if (mutation.operation() == SyncdOperation.REMOVE) {
                client.store().businessStore().removeBusinessBroadcastList(listId);
                return MutationApplicationResult.success();
            }

            return MutationApplicationResult.failed();
        } catch (Exception e) {
            return MutationApplicationResult.failed();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Iterates the batch, applying each mutation via
     * {@link #applyMutation(LinkedWhatsAppClient, DecryptedMutation.Trusted)} and
     * aggregating a malformed-mutation count for the warning log.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBroadcastListSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public List<MutationApplicationResult> applyMutationBatch(LinkedWhatsAppClient client, List<DecryptedMutation.Trusted> mutations) {
        var malformedCount = 0;
        var results = new ArrayList<MutationApplicationResult>(mutations.size());
        for (var mutation : mutations) {
            var result = applyMutation(client, mutation);
            if (result.actionState() == SyncActionState.MALFORMED) {
                malformedCount++;
            }
            results.add(result);
        }
        if (malformedCount > 0) {
            LOGGER.warning("broadcast list sync: " + malformedCount + " malformed mutations");
        }
        return results;
    }

}
