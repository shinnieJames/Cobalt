package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.business.BroadcastListParticipantAction;
import com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastListAction;
import com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastListActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing business-broadcast-list sync mutations.
 *
 * <p>Mirrors the {@code getBroadcastListMutation} and
 * {@code getDeleteBroadcastListMutation} exports of WhatsApp Web's
 * {@code WAWebBroadcastListSync} module. The factory is the
 * outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.BusinessBroadcastListHandler}.
 */
public final class BusinessBroadcastListMutationFactory {
    /**
     * Constructs a business-broadcast-list mutation factory.
     */
    public BusinessBroadcastListMutationFactory() {

    }

    /**
     * Builds a pending SET mutation for creating or updating a business broadcast list with
     * a {@code null} audience expression.
     *
     * <p>Convenience overload that delegates to
     * {@link #getBroadcastListMutation(String, List, String, Instant, String)} with a
     * {@code null} audience expression, matching the common WA Web caller path where the
     * broadcast list is defined purely by its explicit participant snapshot.
     *
     * @param listId       the broadcast list identifier (index arg)
     * @param participants the list of broadcast list participants
     * @param listName     the name of the broadcast list
     * @param timestamp    the mutation timestamp
     * @return a pending mutation ready for outbound sync
     */
    @WhatsAppWebExport(moduleName = "WAWebBroadcastListSync", exports = "getBroadcastListMutation", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getBroadcastListMutation(
            String listId,
            List<BroadcastListParticipantAction> participants,
            String listName,
            Instant timestamp
    ) {
        return getBroadcastListMutation(listId, participants, listName, timestamp, null); // ADAPTED: WAWebBroadcastListSync.getBroadcastListMutation defaults audience expression to null
    }

    /**
     * Builds a pending SET mutation for creating or updating a business broadcast list.
     *
     * <p>Per WhatsApp Web ({@code WAWebBroadcastListSync.getBroadcastListMutation}), this method
     * creates a sync action value containing the business broadcast list action with the
     * supplied participants, list name, an always-empty label id list, and a serialized
     * audience expression.
     *
     * <p>WA Web compiles the audience expression object through
     * {@code WAWebAudienceExpressionTypes.serializeAudienceExpression(i)} before persisting it
     * on the wire. Cobalt accepts the already-serialized JSON string directly because the
     * AudienceExpression DSL is not ported; callers must supply the serialized form or
     * {@code null} to clear it.
     *
     * @param listId             the broadcast list identifier (index arg)
     * @param participants       the list of broadcast list participants
     * @param listName           the name of the broadcast list
     * @param timestamp          the mutation timestamp
     * @param audienceExpression the pre-serialized audience expression, or {@code null}
     * @return a pending mutation ready for outbound sync
     */
    @WhatsAppWebExport(moduleName = "WAWebBroadcastListSync", exports = "getBroadcastListMutation", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getBroadcastListMutation(
            String listId,
            List<BroadcastListParticipantAction> participants,
            String listName,
            Instant timestamp,
            String audienceExpression
    ) {
        var action = new BusinessBroadcastListActionBuilder()
                .participants(participants)
                .listName(listName)
                .labelIds(List.of())
                .audienceExpression(audienceExpression)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .businessBroadcastListAction(action)
                .build();
        var index = JSON.toJSONString(List.of(BusinessBroadcastListAction.ACTION_NAME, listId));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                BusinessBroadcastListAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0);
    }

    /**
     * Builds a pending REMOVE mutation for deleting a business broadcast list.
     *
     * <p>Per WhatsApp Web ({@code WAWebBroadcastListSync.getDeleteBroadcastListMutation}),
     * this method creates a sync action value with an empty payload and builds
     * a REMOVE operation via {@code WAWebSyncdActionUtils.buildPendingMutation}.
     *
     * @param listId    the broadcast list identifier to remove
     * @param timestamp the mutation timestamp
     * @return a pending mutation ready for outbound sync
     */
    @WhatsAppWebExport(moduleName = "WAWebBroadcastListSync", exports = "getDeleteBroadcastListMutation", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getDeleteBroadcastListMutation(String listId, Instant timestamp) {
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .build();
        var index = JSON.toJSONString(List.of(BusinessBroadcastListAction.ACTION_NAME, listId));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.REMOVE,
                timestamp,
                BusinessBroadcastListAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0);
    }
}
