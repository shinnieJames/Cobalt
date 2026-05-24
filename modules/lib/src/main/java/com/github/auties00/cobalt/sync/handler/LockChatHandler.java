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
 * Applies the {@code lock} app-state sync action that locks or unlocks a chat
 * across the user's linked devices.
 *
 * @apiNote
 * Drives the chat-list "Lock chat" affordance: when the primary device
 * locks or unlocks a conversation the resulting bit fans out across the
 * {@link SyncPatchType#REGULAR_LOW} collection. Locking forcibly clears
 * the chat's archive and pin states so the three sticky-chat states
 * remain mutually consistent. The mutation index keys each entry by
 * the chat JID, formatted as
 * {@snippet :
 *     ["lock", chatJid]
 * }
 *
 * @implNote
 * This implementation inlines the
 * {@code WAWebChatLockAction.setChatAsLocked / setChatAsUnlocked}
 * post-loop write directly on the in-memory
 * {@link com.github.auties00.cobalt.model.chat.Chat} instead of going
 * through a chat-table updater, mirroring the
 * {@code syncWithPrimaries: false} branch that WA Web takes for
 * incoming syncs. Per the project's "no Optional&lt;Boolean&gt;" rule,
 * a {@code null} {@code locked} field on a present action coalesces to
 * {@code false} (i.e. unlock) where WA Web would return
 * {@link MutationApplicationResult#malformed()}.
 */
@WhatsAppWebModule(moduleName = "WAWebLockChatSync")
public final class LockChatHandler implements WebAppStateActionHandler {

    /**
     * Constructs a new singleton {@link LockChatHandler}.
     *
     * @apiNote
     * Mirrors WA Web's constructor for {@code WAWebLockChatSync},
     * which inherits from {@code ChatSyncdActionBase} and assigns
     * {@code chatJidIndex = 1} and
     * {@code collectionName = WASyncdConst.CollectionName.RegularLow};
     * those are surfaced via the fixed {@code indexParts[1]} read in
     * {@link #applyMutation} and via {@link #collectionName()}.
     */
    @WhatsAppWebExport(moduleName = "WAWebLockChatSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public LockChatHandler() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLockChatSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return LockChatAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLockChatSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return LockChatAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLockChatSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return LockChatAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation collapses WA Web's two-pass
     * "collect, then setChatAsLocked / setChatAsUnlocked" loop into a
     * single in-place mutation on the
     * {@link com.github.auties00.cobalt.model.chat.Chat} model: when
     * locking, the chat is also marked as not archived and not pinned
     * so the sticky-chat invariant is preserved. A failed
     * {@link Jid#of(String)} is mapped to
     * {@link MutationApplicationResult#malformed()} mirroring WA Web's
     * {@code !WAWebWid.isWid(n)} branch.
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
        } catch (Exception e) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        var chat = client.store().findChatByJid(chatJid);
        if (chat.isEmpty()) {
            return MutationApplicationResult.orphan(chatJidString, "Chat");
        }

        chat.get().setLocked(action.locked());
        if (action.locked()) {
            chat.get().setArchived(false);
            chat.get().setPinnedTimestamp(null);
        }
        return MutationApplicationResult.success();
    }

}
