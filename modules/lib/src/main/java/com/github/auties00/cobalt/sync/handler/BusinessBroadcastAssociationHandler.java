package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.business.BroadcastListParticipant;
import com.github.auties00.cobalt.model.business.BroadcastListParticipantBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.business.BroadcastListParticipantAction;
import com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastAssociationAction;
import com.github.auties00.cobalt.model.sync.action.business.BusinessBroadcastListAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds or removes a single recipient JID from a business broadcast list in response to a {@code broadcast_jid} sync mutation.
 *
 * @apiNote
 * Drives the per-recipient diff of a Business broadcast list when the
 * user adds or removes one recipient on another device. The
 * {@link BusinessBroadcastAssociationAction#deleted()} flag inside the
 * mutation value distinguishes additions from removals; both arrive
 * over the wire as {@link SyncdOperation#SET}.
 *
 * @implNote
 * This implementation is forward-looking: WA Web reserves the
 * {@code broadcast_jid} action under
 * {@code WASyncdConst.Actions.BroadcastJid} and defines the
 * {@link BusinessBroadcastAssociationAction} protobuf in
 * {@code WAWebProtobufSyncAction.pb}, but the current snapshot does
 * not ship a {@code WAWebBroadcastJidSync} module nor register this
 * action in {@code WAWebCollectionHandlerActions.ActionHandlers}, so
 * WA Web does not currently dispatch incoming mutations of this
 * type. The Cobalt handler infers the per-recipient mutation from
 * the related {@code WAWebBroadcastListSync} (which manages the
 * parent broadcast list and its participant array). When recipient
 * JIDs are LID/HostedLID they are stored as {@code lidJid}
 * directly; phone-number recipients populate {@code pnJid} and
 * resolve {@code lidJid} via
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#findLidByPhone(Jid)},
 * falling back to the phone-number JID itself when no LID is
 * known.
 */
@WhatsAppWebModule(moduleName = "WAWebBroadcastListSync")
public final class BusinessBroadcastAssociationHandler implements WebAppStateActionHandler {

    /**
     * Constructs the singleton broadcast-association handler.
     *
     * @apiNote
     * Instantiated once by the sync handler registry. Embedders do not
     * normally construct this directly.
     */
    @WhatsAppWebExport(moduleName = "WAWebBroadcastListSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public BusinessBroadcastAssociationHandler() {

    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebBroadcastListSync", exports = "getAction", adaptation = WhatsAppAdaptation.ADAPTED)
    public String actionName() {
        return BusinessBroadcastAssociationAction.ACTION_NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebBroadcastListSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPatchType collectionName() {
        return BusinessBroadcastAssociationAction.COLLECTION_NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebBroadcastListSync", exports = "getVersion", adaptation = WhatsAppAdaptation.ADAPTED)
    public int version() {
        return BusinessBroadcastAssociationAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Validates the JSON index {@code ["broadcast_jid", listId, recipientJid]},
     * resolves the parent {@link BusinessBroadcastListAction} from the
     * store, removes any existing entry for the recipient, and
     * (when the action is not {@code deleted}) appends a fresh
     * {@link BroadcastListParticipantAction} entry. Returns
     * {@link MutationApplicationResult#unsupported()} for non-{@code SET}
     * operations and an orphan result keyed by list id with model
     * type {@code "BroadcastList"} when the parent list is not in the
     * store.
     *
     * @implNote
     * This implementation copies the existing participant array into a
     * mutable {@link ArrayList} before mutating because the model
     * exposes an unmodifiable {@link List}; the parent list is then
     * upserted via
     * {@link com.github.auties00.cobalt.store.WhatsAppStore#putBusinessBroadcastList(BusinessBroadcastListAction)}.
     * An empty participant array is normalized to {@code null} so the
     * stored list shape matches the wire shape.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBroadcastListSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
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

        var existing = client.store().findBusinessBroadcastList(listId).orElse(null);
        if (existing == null) {
            return MutationApplicationResult.orphan(listId, "BroadcastList");
        }

        var recipientJid = Jid.of(recipientJidString);
        List<BroadcastListParticipant> participants = new ArrayList<>(existing.participants());
        participants.removeIf(participant ->
                recipientJid.equals(participant.lidJid())
                        || participant.pnJid().filter(recipientJid::equals).isPresent()
        );
        if (!action.deleted()) {
            BroadcastListParticipant participant;
            if (recipientJid.hasLidServer() || recipientJid.hasHostedLidServer()) {
                participant = new BroadcastListParticipantBuilder()
                        .lidJid(recipientJid)
                        .build();
            } else {
                participant = new BroadcastListParticipantBuilder()
                        .lidJid(client.store().findLidByPhone(recipientJid).orElse(recipientJid))
                        .pnJid(recipientJid)
                        .build();
            }
            participants.add(participant);
        }

        existing.setParticipants(participants.isEmpty() ? null : participants);
        client.store().putBusinessBroadcastList(existing);
        return MutationApplicationResult.success();
    }
}
