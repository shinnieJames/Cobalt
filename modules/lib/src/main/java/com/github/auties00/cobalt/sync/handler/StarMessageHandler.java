package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.ChatMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.newsletter.NewsletterMessageInfo;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.contact.StarAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Toggles the starred flag on a single message in response to a
 * cross-device {@code star} mutation.
 *
 * @apiNote
 * Cobalt embedders never invoke this handler directly; the sync dispatcher
 * routes incoming {@code star} mutations here whenever the user stars or
 * unstars a message on another linked device (typical trigger: long-press
 * a message on the phone and tap "star"). The handler locates the matching
 * message in the unified store and sets the starred flag on it.
 */
@WhatsAppWebModule(moduleName = "WAWebStarMessageSync")
public final class StarMessageHandler implements WebAppStateActionHandler {

    /**
     * Constructs the handler.
     *
     * @apiNote
     * The handler is stateless; Cobalt's sync registry holds a single
     * instance per client.
     */
    @WhatsAppWebExport(moduleName = "WAWebStarMessageSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public StarMessageHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStarMessageSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return StarAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStarMessageSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return StarAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStarMessageSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return StarAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation mirrors WA Web's per-mutation closure inside
     * {@code WAWebStarMessageSync.applyMutations}. The mutation index is
     * {@code ["star", chatJid, messageId, fromMe, participant]}; each slot
     * is required, the value must carry a {@link StarAction}, and the
     * message key is rebuilt via
     * {@link SyncdIndexUtils#syncKeyToMsgKey} for the orphan branch only
     * (the located message is otherwise looked up directly by chat JID
     * and message id). The starred flag is then propagated through
     * {@link #starMessage(MessageInfo, boolean)}.
     *
     * <p>WA Web's two-tier message resolution
     * ({@code WAWebSyncdResolveMessages.resolveMessagesForMutations} batch
     * pre-pass plus the {@code WAWebStarredMsgCollection.addStarredMsgs}
     * snapshot collection plus the
     * {@code WAWebDBProcessMessage.starMessages} persistence batch) is
     * collapsed into a single per-mutation store update because Cobalt's
     * flattened store keeps starred state directly on each message
     * record. WA Web's
     * {@code WAWebAssociationProcessor.detachAssociatedMsg} call is also
     * dropped because the in-memory reactive-collection bookkeeping it
     * performs has no Cobalt analogue. Any thrown exception maps to
     * {@link MutationApplicationResult#failed()}, mirroring WA Web's
     * inner {@code try/catch}.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStarMessageSync", exports = {"applyMutations", "getMessageKey"}, adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        try {
            var indexArray = JSON.parseArray(mutation.index());
            if (indexArray.size() < 5) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }

            var chatJidString = indexArray.getString(1);
            var messageId = indexArray.getString(2);
            var fromMeString = indexArray.getString(3);
            var participantString = indexArray.getString(4);
            if (chatJidString == null || chatJidString.isEmpty()
                    || messageId == null || messageId.isEmpty()
                    || fromMeString == null || fromMeString.isEmpty()
                    || participantString == null || participantString.isEmpty()) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }

            if (!(mutation.value().action().orElse(null) instanceof StarAction action)) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            var starred = action.starred();

            var msgKeyOpt = SyncdIndexUtils.syncKeyToMsgKey(client.store(), chatJidString, messageId, fromMeString, participantString);
            if (msgKeyOpt.isEmpty()) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }
            var msgKey = msgKeyOpt.get();

            Jid chatJid;
            try {
                chatJid = Jid.of(chatJidString);
            } catch (Exception e) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }

            var message = client.store().findMessageById(chatJid, messageId);
            if (message.isEmpty()) {
                return MutationApplicationResult.orphan(
                        SyncdIndexUtils.serializeMessageKey(msgKey),
                        "Msg"
                );
            }

            var found = message.get();
            starMessage(found, starred);
            return MutationApplicationResult.success();
        } catch (Exception e) {
            return MutationApplicationResult.failed();
        }
    }

    /**
     * Sets the starred flag on a {@link MessageInfo} regardless of its
     * concrete subtype.
     *
     * @apiNote
     * Used by {@link #applyMutation(WhatsAppClient, DecryptedMutation.Trusted)}
     * to write the resolved {@code starred} flag onto either a chat
     * message or a newsletter message; the dispatch keeps the
     * concrete-subtype detail inside this helper so the per-mutation
     * pipeline stays uniform.
     *
     * @implNote
     * This implementation accepts the type-system asymmetry between
     * {@link ChatMessageInfo#setStarred(Boolean)} (boxed) and
     * {@link NewsletterMessageInfo#setStarred(boolean)} (primitive); both
     * accessors perform the same logical assignment, the boxed signature
     * just predates the primitive one.
     *
     * @param message the message whose flag is being updated
     * @param starred {@code true} to star, {@code false} to unstar
     */
    @WhatsAppWebExport(moduleName = "WAWebStarMessageSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    private static void starMessage(MessageInfo message, boolean starred) {
        switch (message) {
            case ChatMessageInfo chatMessageInfo -> chatMessageInfo.setStarred(starred);
            case NewsletterMessageInfo newsletterMessageInfo -> newsletterMessageInfo.setStarred(starred);
        }
    }
}
