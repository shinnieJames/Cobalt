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
 * Handles business broadcast association sync actions ({@code "broadcast_jid"}).
 *
 * <p>Each mutation associates (SET) or disassociates (REMOVE / {@code deleted=true})
 * a single recipient JID with a business broadcast list. The recipient is stored
 * inside the parent {@link BusinessBroadcastListAction} via its
 * {@link BroadcastListParticipantAction} list. When SET, the handler appends a
 * participant entry, taking care to populate {@code lidJid} for LID/HostedLID
 * recipients and {@code pnJid} (with the resolved LID via
 * {@code WhatsAppStore.getLidByPhoneNumber}) for phone-number recipients.
 * When REMOVE (or when the action carries {@code deleted=true}), the handler
 * strips any participant whose {@code lidJid} or {@code pnJid} matches the
 * recipient.
 *
 * <p>Index format: {@code ["broadcast_jid", broadcastListId, recipientJid]}
 *
 * <p><b>NO_WA_BASIS:</b> The {@code "broadcast_jid"} action is registered as
 * {@code WASyncdConst.Actions.BroadcastJid} and the
 * {@code SyncActionValue.BusinessBroadcastAssociationAction} protobuf is defined
 * in {@code WAWebProtobufSyncAction.pb} (with a single {@code deleted: bool}
 * field), but the current WA Web snapshot does <em>not</em> ship a corresponding
 * sync handler module. The action is also absent from
 * {@code WAWebCollectionHandlerActions.ActionHandlers}, so WA Web would not
 * dispatch any incoming mutation with this action. The Cobalt handler is a
 * forward-looking implementation: it follows the conventions of the closely
 * related {@code WAWebBroadcastListSync} (which manages the parent broadcast
 * list and its participants) and the canonical Cobalt index format
 * {@code [actionName, ...indexArgs]}, but every behavioural step here is
 * Cobalt-inferred until WA Web ships the matching {@code WAWebBroadcastJidSync}
 * module.
 */
@WhatsAppWebModule(moduleName = "WAWebBroadcastListSync")
public final class BusinessBroadcastAssociationHandler implements WebAppStateActionHandler {

    /**
     * Private constructor to enforce the singleton pattern.
     */
    @WhatsAppWebExport(moduleName = "WAWebBroadcastListSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public BusinessBroadcastAssociationHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBroadcastListSync", exports = "getAction", adaptation = WhatsAppAdaptation.ADAPTED)
    public String actionName() {
        return BusinessBroadcastAssociationAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBroadcastListSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPatchType collectionName() {
        return BusinessBroadcastAssociationAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBroadcastListSync", exports = "getVersion", adaptation = WhatsAppAdaptation.ADAPTED)
    public int version() {
        return BusinessBroadcastAssociationAction.ACTION_VERSION;
    }

    /**
     * Applies a business broadcast association mutation and returns a detailed result.
     *
     * <p>The processing pipeline is:
     * <ol>
     *   <li>If the operation is not {@link SyncdOperation#SET}, return
     *       {@link MutationApplicationResult#unsupported()}. Only SET mutations
     *       are accepted; the {@code deleted} flag inside the action payload
     *       distinguishes additions from removals.</li>
     *   <li>Parse {@code mutation.index()} as a JSON array; require at least
     *       three elements. {@code index[1]} is the broadcast list id and
     *       {@code index[2]} is the recipient JID string. Return
     *       {@link MutationApplicationResult#malformed()} when the array is
     *       too short or either field is blank.</li>
     *   <li>Resolve the action payload to a
     *       {@link BusinessBroadcastAssociationAction}; return
     *       {@link MutationApplicationResult#malformed()} when missing or of
     *       the wrong type.</li>
     *   <li>Look up the parent {@link BusinessBroadcastListAction} in
     *       {@code client.store().businessBroadcastLists()}; if absent return
     *       {@link MutationApplicationResult#orphan(String, String)} with the
     *       missing list id and {@code "BroadcastList"} model type.</li>
     *   <li>Remove any existing participant whose {@code lidJid} or {@code pnJid}
     *       equals the recipient.</li>
     *   <li>If the action is <em>not</em> deleted, append a fresh
     *       {@link BroadcastListParticipantAction}: when the recipient JID has a
     *       LID or HostedLID server, populate {@code lidJid}; otherwise populate
     *       {@code pnJid} and resolve {@code lidJid} via
     *       {@code WhatsAppStore.getLidByPhoneNumber}, falling back to the
     *       phone-number JID itself.</li>
     *   <li>Persist the mutated participant list back into the broadcast list
     *       and write the parent map to the store.</li>
     * </ol>
     * @param client   the WhatsAppClient instance linked to the mutation
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBroadcastListSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        var indexArray = JSON.parseArray(mutation.index()); // ADAPTED: WAWebBroadcastListSync.applyMutations: var t = e.indexParts (pre-parsed in WA Web)
        if (indexArray.size() < 3) {
            return MutationApplicationResult.malformed();
        }

        var listId = indexArray.getString(1);
        var recipientJidString = indexArray.getString(2);
        if (listId == null || listId.isBlank() || recipientJidString == null || recipientJidString.isBlank()) { // ADAPTED: WAWebBroadcastListSync.applyMutations: if (!n) return r.malformedActionIndex()
            return MutationApplicationResult.malformed();
        }

        if (!(mutation.value().action().orElse(null) instanceof BusinessBroadcastAssociationAction action)) { // ADAPTED: WAWebBroadcastListSync.applyMutations: if (!u) return malformedActionValue(...)
            return MutationApplicationResult.malformed();
        }

        // ADAPTED: WAWebBroadcastListSync resolves the parent broadcast list via
        // the typed broadcast-list quintet on WhatsAppStore.
        var existing = client.store().findBusinessBroadcastList(listId).orElse(null);
        if (existing == null) {
            return MutationApplicationResult.orphan(listId, "BroadcastList");
        }

        var recipientJid = Jid.of(recipientJidString);
        List<BroadcastListParticipant> participants = new ArrayList<>(existing.participants()); // ADAPTED: WAWebBroadcastListSync uses BroadcastListParticipantAction[]; Cobalt copies to a mutable list before mutating
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
                        .lidJid(client.store().getLidByPhoneNumber(recipientJid).orElse(recipientJid)) // ADAPTED: WhatsAppStore.getLidByPhoneNumber resolves the LID/PN map maintained by Cobalt
                        .pnJid(recipientJid)
                        .build();
            }
            participants.add(participant);
        }

        existing.setParticipants(participants.isEmpty() ? null : participants); // ADAPTED: WAWebBroadcastListSync writes participants via WAWebBroadcastListStorageUtils.updateBroadcastListStorage
        client.store().putBusinessBroadcastList(existing);
        return MutationApplicationResult.success();
    }
}
