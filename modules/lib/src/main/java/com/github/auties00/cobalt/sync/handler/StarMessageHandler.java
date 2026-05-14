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
 * Handles star message sync actions for starring or unstarring individual
 * messages.
 *
 * <p>The action is identified by the {@code "star"} action name in
 * {@code SyncActionValue.starAction}. The mutation index format is
 * {@code ["star", chatJid, messageId, fromMe, participant]} where {@code fromMe}
 * is the string {@code "0"} or {@code "1"} and {@code participant} is the sender
 * JID for group messages or the empty string for 1:1 chats.
 *
 * <p>Per WhatsApp Web, this handler extends {@code MessageSyncdActionBase}
 * which is itself a {@code ChatSyncdActionBase}. The collection is
 * {@code RegularHigh} and the version is {@code 2}. Cobalt collapses the
 * inheritance hierarchy into a single sealed implementation.
 */
@WhatsAppWebModule(moduleName = "WAWebStarMessageSync")
public final class StarMessageHandler implements WebAppStateActionHandler {

    /**
     * Private constructor to enforce singleton pattern.
     */
    @WhatsAppWebExport(moduleName = "WAWebStarMessageSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public StarMessageHandler() {

    }

    /**
     * Returns the action name for star message actions.
     * @return the action name {@code "star"}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStarMessageSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return StarAction.ACTION_NAME;
    }

    /**
     * Returns the sync collection for star message actions.
     *
     * <p>Per WhatsApp Web, the star handler's {@code collectionName} is set to
     * {@code WASyncdConst.CollectionName.RegularHigh} in the constructor.
     * @return {@link SyncPatchType#REGULAR_HIGH}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStarMessageSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return StarAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for star message actions.
     * @return the version number {@code 2}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStarMessageSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return StarAction.ACTION_VERSION;
    }

    /**
     * Applies a star message mutation and returns a detailed result.
     *
     * <p>Per WhatsApp Web {@code WAWebStarMessageSync.applyMutations}, for each
     * mutation with {@code operation === "set"}:
     * <ol>
     *   <li>Extracts {@code chatJid}, {@code messageId}, {@code fromMe},
     *       {@code participant} from {@code indexParts[1..4]}</li>
     *   <li>If any of the four index parts is missing or empty, returns
     *       {@code malformedActionIndex()}</li>
     *   <li>Reads {@code starred} from {@code value.starAction.starred};
     *       if absent, returns {@code malformedActionValue(collectionName)}</li>
     *   <li>Reconstructs the {@link MessageKey} via
     *       {@link SyncdIndexUtils#syncKeyToMsgKey}; if reconstruction fails,
     *       returns {@code malformedActionIndex()}</li>
     *   <li>Locates the chat-local message via
     *       {@code WhatsAppStore.findMessageById(chatJid, messageId)};
     *       if not found, returns {@code Orphan} with {@code modelId} set to
     *       the serialized {@link MessageKey} and {@code modelType = "Msg"}</li>
     *   <li>Verifies the located message's {@code fromMe} and sender JID match
     *       the index parts (defensive check absent in WA Web because WA Web
     *       searches a pre-resolved DB id list)</li>
     *   <li>Sets the message's {@code starred} flag via
     *       {@link #starMessage(MessageInfo, boolean)}</li>
     * </ol>
     *
     * <p>Non-{@code SET} operations return {@code Unsupported}. Exceptions are
     * caught and return {@code Failed}, mirroring the WA Web inner try/catch.
     *
     * <p>Cobalt does not replicate WA Web's {@code WAWebDBProcessMessage.starMessages} /
     * {@code unstarMessages} batch persistence call nor its
     * {@code WAWebStarredMsgCollection.addStarredMsgs} / {@code removeStarredMsgs}
     * mutations because the flattened {@code WhatsAppStore} maintains starred state
     * directly on the message record (no separate StarredMsgCollection table).
     *
     * <p>Cobalt also does not invoke
     * {@code WAWebAssociationProcessor.detachAssociatedMsg} for messages with
     * an {@code associationType} because association detachment is a WA Web
     * in-memory reactive collection bookkeeping concern that does not apply to
     * Cobalt's plain record store.
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
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

            // ADAPTED: Cobalt's StarAction.starred() coalesces a missing protobuf bool to false because
            // the package-private raw Boolean field cannot be inspected from this package. The
            // outer instanceof StarAction check above already covers the dominant "starAction missing"
            // case, so the only divergence is that an explicit null `starred` is treated as `false`
            // rather than Malformed. In practice, the WA Web protobuf encoder always emits the bool.
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

            // Cobalt looks up the chat-local message directly via the flattened store.
            // The orphan modelId mirrors WA Web's `f.toString()` (the original-key form).
            var message = client.store().findMessageById(chatJid, messageId);
            if (message.isEmpty()) {
                return MutationApplicationResult.orphan(
                        SyncdIndexUtils.serializeMessageKey(msgKey),
                        "Msg"
                );
            }

            var found = message.get();
            // post-lookup because the y.find(prefix) DB-id-prefix search already constrains
            // the match by remote, id, fromMe, and participant. Cobalt's findMessageById
            // operates on a per-chat map and trusts (chatJid, id) to be unique within a chat.
            // The reconstructed msgKey is consumed only by the orphan branch above.
            starMessage(found, starred);
            // ADAPTED: Cobalt does not maintain a separate persistence batch — the in-memory store IS the persistence layer
            return MutationApplicationResult.success();
        } catch (Exception e) {
            return MutationApplicationResult.failed();
        }
    }

    /**
     * Toggles the {@code starred} flag on a {@link MessageInfo} regardless of
     * its concrete subtype.
     *
     * <p>Per WhatsApp Web {@code WAWebStarMessageSync.applyMutations}, the inner
     * function performs {@code k.star = p} on a {@code MsgCollection} record
     * and then adds/removes the message from {@code WAWebStarredMsgCollection}.
     *
     * <p>In Cobalt, the unified store keeps starred state directly on the
     * message record. {@link ChatMessageInfo#setStarred(Boolean)} accepts a
     * boxed value while {@link NewsletterMessageInfo#setStarred(boolean)}
     * accepts a primitive — both perform the same logical assignment.
     * @param message the message whose star flag is being updated
     * @param starred {@code true} to star the message, {@code false} to unstar it
     */
    @WhatsAppWebExport(moduleName = "WAWebStarMessageSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    private static void starMessage(MessageInfo message, boolean starred) {
        switch (message) { // ADAPTED: WA Web only has MsgCollection records; Cobalt's unified store dispatches by concrete type
            case ChatMessageInfo chatMessageInfo -> chatMessageInfo.setStarred(starred);
            case NewsletterMessageInfo newsletterMessageInfo -> newsletterMessageInfo.setStarred(starred); // ADAPTED: Cobalt extends starring to newsletter messages via the unified store
        }
    }
}
