package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.chat.LockChatAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles lock chat sync actions.
 *
 * <p>This handler processes incoming mutations that lock or unlock individual
 * chats. When a chat is locked it is also forcibly unarchived and unpinned so
 * that the three sticky-chat states ({@code archive}, {@code pin}, {@code lock})
 * remain mutually consistent.
 *
 * <p>The action is identified by the {@code "lock"} action name in
 * {@code SyncActionValue.lockChatAction}. The mutation index format is
 * {@code ["lock", chatJid]}.
 *
 * <p>Per WhatsApp Web {@code WAWebLockChatSync}, the handler extends
 * {@code ChatSyncdActionBase}. In Cobalt this inheritance is flattened: the
 * handler directly implements {@link WebAppStateActionHandler}, the chat JID
 * is extracted from the JSON-encoded index array, and the lock state is
 * applied directly to the in-memory chat model instead of going through a
 * dedicated chat table update layer.
 */
@WhatsAppWebModule(moduleName = "WAWebLockChatSync")
public final class LockChatHandler implements WebAppStateActionHandler {

    /**
     * Constructs a lock-chat handler.
     *
     * <p>Per WhatsApp Web, class {@code m} (extending {@code ChatSyncdActionBase})
     * has no custom constructor logic beyond initializing {@code chatJidIndex = 1}
     * and {@code collectionName = RegularLow}. Both values are represented in
     * Cobalt through the {@link #collectionName()} accessor and the fixed
     * {@code indexParts[1]} lookup used in {@link #applyMutation}.
     */
    @WhatsAppWebExport(moduleName = "WAWebLockChatSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public LockChatHandler() {
    }

    /**
     * Returns the action name for lock chat actions.
     * @return the action name {@code "lock"}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLockChatSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return LockChatAction.ACTION_NAME;
    }

    /**
     * Returns the sync collection for lock chat actions.
     *
     * <p>Per WhatsApp Web, the lock handler's {@code collectionName} is set to
     * {@code WASyncdConst.CollectionName.RegularLow} in the constructor.
     * @return {@link SyncPatchType#REGULAR_LOW}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLockChatSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return LockChatAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for lock chat actions.
     * @return the version number {@code 7}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLockChatSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return LockChatAction.ACTION_VERSION;
    }

    /**
     * Applies a lock chat mutation and returns a detailed result.
     *
     * <p>Per WhatsApp Web {@code WAWebLockChatSync.applyMutations}, for each
     * mutation:
     * <ol>
     *   <li>If the operation is not {@code SET}, increments the unsupported
     *       counter and returns {@code Unsupported}.</li>
     *   <li>Extracts {@code chatJid = indexParts[1]} and
     *       {@code locked = value.lockChatAction?.locked}.</li>
     *   <li>If {@code locked == null}, increments the malformed-value counter
     *       and returns {@code malformedActionValue(collectionName)}.</li>
     *   <li>If the chat JID is not a valid WID, increments the malformed-index
     *       counter and returns {@code malformedActionIndex()}.</li>
     *   <li>Resolves the chat via
     *       {@code WAWebSyncdGetChat.resolveChatForMutationIndex}; if unsuccessful,
     *       returns {@code {actionState: Orphan, orphanModel: u.orphanModel}}.</li>
     *   <li>Queues an application record {@code {isLocked, chatId}} and returns
     *       {@code {actionState: Success}}.</li>
     * </ol>
     *
     * <p>After collecting the success list, WA Web iterates it and calls
     * {@code WAWebChatLockAction.setChatAsLocked(chatId, {syncWithPrimaries: false})}
     * or {@code WAWebChatLockAction.setChatAsUnlocked(chatId, {syncWithPrimaries: false})}.
     * With {@code syncWithPrimaries = false}, both routes collapse to the
     * internal chat-table updater that writes
     * {@code {isLocked: true, archive: false, pin: undefined}} when locking and
     * {@code {isLocked: false}} when unlocking. Cobalt inlines this final
     * write step directly on the in-memory {@code Chat} model because the
     * {@code ChatCollection}/{@code updateChatTable} indirection is not used.
     *
     * <p>Cobalt's {@link LockChatAction#locked()} accessor null-coalesces to
     * {@code false}, so the WA Web {@code locked == null} check is not
     * reachable: a missing {@code locked} field is treated as an unlock
     * request, consistent with how Cobalt handles other nullable boolean
     * sync fields. See {@code ArchiveChatAction.archived()} for the matching
     * pattern.
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLockChatSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof LockChatAction action)) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        var chatJidString = JSON.parseArray(mutation.index()).getString(1);
        if (chatJidString == null || chatJidString.isEmpty()) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        Jid chatJid;
        try {
            chatJid = Jid.of(chatJidString);
        } catch (Exception e) { // ADAPTED: WAWebWid.isWid would reject before reaching createWid; Cobalt catches parse failures
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        var chat = client.store().findChatByJid(chatJid);
        if (chat.isEmpty()) {
            return MutationApplicationResult.orphan(chatJidString, "Chat");
        }

        // Post-loop, WA Web iterates i and calls setChatAsLocked / setChatAsUnlocked with syncWithPrimaries: false.
        // Both collapse to WAWebChatLockAction.e() which writes
        //   {isLocked: true, archive: false, pin: undefined} when locking
        //   {isLocked: false}                                when unlocking
        // Cobalt inlines this write directly on the in-memory Chat model.
        chat.get().setLocked(action.locked());
        if (action.locked()) {
            chat.get().setArchived(false);
            chat.get().setPinnedTimestamp(null);
        }
        return MutationApplicationResult.success();
    }

}
