package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.business.BroadcastListParticipantAction;
import com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastAssociationAction;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;

import java.util.ArrayList;
import java.util.HashMap;

public final class BusinessBroadcastAssociationHandler implements WebAppStateActionHandler {
    public static final BusinessBroadcastAssociationHandler INSTANCE = new BusinessBroadcastAssociationHandler();

    private BusinessBroadcastAssociationHandler() {

    }

    @Override
    public String actionName() {
        return BusinessBroadcastAssociationAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return BusinessBroadcastAssociationAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return BusinessBroadcastAssociationAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        return applyMutationResult(client, mutation).actionState() == com.github.auties00.cobalt.model.sync.SyncActionState.SUCCESS;
    }

    @Override
    public MutationApplicationResult applyMutationResult(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        var indexArray = JSON.parseArray(mutation.index());
        if (indexArray.size() < 3) {
            return MutationApplicationResult.malformed();
        }

        var listId = indexArray.getString(1);
        var recipientJidString = indexArray.getString(2);
        if (listId == null || listId.isBlank() || recipientJidString == null || recipientJidString.isBlank()) {
            return MutationApplicationResult.malformed();
        }

        if (!(mutation.value().action().orElse(null) instanceof BusinessBroadcastAssociationAction action)) {
            return MutationApplicationResult.malformed();
        }

        var lists = new HashMap<>(client.store().businessBroadcastLists());
        var broadcastList = lists.get(listId);
        if (broadcastList == null) {
            return MutationApplicationResult.orphan(listId, "BroadcastList");
        }

        var recipientJid = Jid.of(recipientJidString);
        var participants = new ArrayList<>(broadcastList.participants());
        participants.removeIf(participant ->
                recipientJid.equals(participant.lidJid()) || participant.pnJid().filter(recipientJid::equals).isPresent()
        );
        if (!action.deleted()) {
            var participant = new BroadcastListParticipantAction();
            if (recipientJid.hasLidServer() || recipientJid.hasHostedLidServer()) {
                participant.setLidJid(recipientJid);
            } else {
                participant.setPnJid(recipientJid);
                participant.setLidJid(client.store().getLidByPhoneNumber(recipientJid).orElse(recipientJid));
            }
            participants.add(participant);
        }

        broadcastList.setParticipants(participants);
        lists.put(listId, broadcastList);
        client.store().setBusinessBroadcastLists(lists);
        return MutationApplicationResult.success();
    }
}
