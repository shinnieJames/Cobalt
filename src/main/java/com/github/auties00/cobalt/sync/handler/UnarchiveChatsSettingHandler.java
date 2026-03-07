package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.setting.UnarchiveChatsSetting;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles unarchive chats setting changes.
 *
 * <p>This handler processes mutations that control whether archived chats
 * should be automatically unarchived when a new message arrives.
 *
 * <p>Per WhatsApp Web {@code WAWebArchiveSettingSync}: when the setting changes,
 * a side effect runs on all archived chats to transition them between classic
 * mode (unarchive on new message) and keep-archived mode.
 */
public final class UnarchiveChatsSettingHandler implements WebAppStateActionHandler {
    public static final UnarchiveChatsSettingHandler INSTANCE = new UnarchiveChatsSettingHandler();

    private UnarchiveChatsSettingHandler() {

    }

    @Override
    public String actionName() {
        return UnarchiveChatsSetting.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return UnarchiveChatsSetting.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return UnarchiveChatsSetting.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        if (!(mutation.value().action().orElse(null) instanceof UnarchiveChatsSetting setting)) {
            return false;
        }

        var unarchiveChats = setting.unarchiveChats();
        client.store().setUnarchiveChats(unarchiveChats);
        updateSideEffectOnChats(client, unarchiveChats);
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Per WhatsApp Web {@code WAWebArchiveSettingSync.applyMutations}: takes
     * the last mutation in the batch ({@code e[e.length-1]}) and applies only that one.
     * Earlier mutations in the batch are acknowledged but not applied.
     */
    @Override
    public List<Boolean> applyMutationBatch(WhatsAppClient client, List<DecryptedMutation.Trusted> mutations) {
        if (mutations.isEmpty()) {
            return List.of();
        }

        var results = new ArrayList<Boolean>(mutations.size());
        // Web: only the last mutation is applied
        for (int i = 0; i < mutations.size() - 1; i++) {
            results.add(true);
        }

        var last = mutations.getLast();
        results.add(applyMutation(client, last));
        return results;
    }

    /**
     * Applies the side effect on archived chats when the unarchive setting changes.
     *
     * <p>Per WhatsApp Web {@code WAWebArchiveSettingSync.updateSideEffectOnChats}:
     * <ul>
     *   <li>When switching to classic mode ({@code unarchiveChats=true}): scans all
     *       archived chats that were archived without an active message range, compares
     *       current and archived message ranges, and unarchives chats that received new
     *       messages since being archived. Simplified here: unarchives chats that have
     *       unread messages, as a proxy for "messages arrived after archive point" since
     *       we do not track per-chat archive message ranges.
     *   <li>When switching to keep-archived mode ({@code unarchiveChats=false}): in Web,
     *       re-archives chats that have a successful archive sync action. Since our archive
     *       state is already maintained by individual archive mutations, no additional
     *       side effect is needed.
     * </ul>
     *
     * @param client         the WhatsApp client
     * @param unarchiveChats the new setting value
     */
    private void updateSideEffectOnChats(WhatsAppClient client, boolean unarchiveChats) {
        if (unarchiveChats) {
            // Web: $ArchiveSettingSync$p_1
            // Switching to classic mode: unarchive chats that received new messages
            // while in keep-archived mode. Since we lack per-chat archive message ranges,
            // use unreadCount > 0 as a proxy for "messages arrived after archive point".
            for (var chat : client.store().chats()) {
                if (chat.archived() && chat.unreadCount().orElse(0) > 0) {
                    chat.setArchived(false);
                }
            }
        }
        // Web: $ArchiveSettingSync$p_2 — when switching to keep-archived mode,
        // re-archives chats with successful archive actions. Our archive state is
        // already maintained by individual ArchiveChatHandler mutations, so no
        // additional side effect is needed.
    }
}
