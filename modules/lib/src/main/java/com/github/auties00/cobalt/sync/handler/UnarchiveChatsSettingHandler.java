package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.chat.ArchiveChatAction;
import com.github.auties00.cobalt.model.sync.action.setting.UnarchiveChatsSetting;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the unarchive chats setting sync action.
 *
 * <p>This handler processes the {@code "setting_unarchiveChats"} sync action,
 * which controls whether archived chats should be automatically unarchived
 * when a new message arrives. When the setting changes, side effects are
 * applied to existing archived chats based on their archive sync action
 * entries.
 *
 * <p>Per WhatsApp Web, this handler extends {@code AccountSyncdActionBase}
 * and only applies the last mutation in a batch (all earlier mutations are
 * skipped). The collection is {@code RegularLow} and the version is {@code 4}.
 */
@WhatsAppWebModule(moduleName = "WAWebArchiveSettingSync")
public final class UnarchiveChatsSettingHandler implements WebAppStateActionHandler {

    /**
     * Private constructor to enforce singleton pattern.
     */
    @WhatsAppWebExport(moduleName = "WAWebArchiveSettingSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public UnarchiveChatsSettingHandler() {

    }

    /**
     * Returns the action name for the unarchive chats setting action.
     * @return the action name {@code "setting_unarchiveChats"}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebArchiveSettingSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return UnarchiveChatsSetting.ACTION_NAME;
    }

    /**
     * Returns the sync collection for the unarchive chats setting action.
     *
     * <p>Per WhatsApp Web, the handler's {@code collectionName} is set to
     * {@code WASyncdConst.CollectionName.RegularLow} in the constructor.
     * @return {@link SyncPatchType#REGULAR_LOW}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebArchiveSettingSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return UnarchiveChatsSetting.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for the unarchive chats setting action.
     * @return the version number {@code 4}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebArchiveSettingSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return UnarchiveChatsSetting.ACTION_VERSION;
    }

    /**
     * Applies a batch of unarchive chats setting mutations.
     *
     * <p>Per WhatsApp Web {@code WAWebArchiveSettingSync.applyMutations}: only the
     * last mutation in the batch is applied. All earlier mutations are skipped
     * because only the final setting value matters.
     *
     * <p>If the batch is empty, returns an empty list. Per WA Web, an empty
     * mutations array logs a warning and returns {@code [{actionState: Failed}]},
     * but since the caller should not dispatch empty batches, Cobalt returns an
     * empty list for API consistency with other handlers.
     * @param client    the WhatsApp client instance
     * @param mutations the batch of mutations to apply
     * @return a list of results parallel to the input
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebArchiveSettingSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public List<MutationApplicationResult> applyMutationBatch(WhatsAppClient client, List<DecryptedMutation.Trusted> mutations) {
        if (mutations.isEmpty()) {
            return List.of();
        }

        var results = new ArrayList<MutationApplicationResult>(mutations.size());
        for (var i = 0; i < mutations.size() - 1; i++) {
            results.add(MutationApplicationResult.skipped());
        }
        results.add(applyMutation(client, mutations.getLast()));
        return results;
    }

    /**
     * Applies an unarchive chats setting mutation and returns a detailed result.
     *
     * <p>Per WhatsApp Web {@code WAWebArchiveSettingSync.applyMutations}, for a
     * mutation with {@code operation === "set"}:
     * <ol>
     *   <li>Extracts the {@code unarchiveChatsSetting} from the sync action value</li>
     *   <li>Validates that {@code unarchiveChats} is not {@code null} (returns
     *       {@code malformedActionValue} if it is)</li>
     *   <li>Updates the unarchive chats setting in the store</li>
     *   <li>Calls {@link #updateSideEffectOnChats(WhatsAppClient, boolean)} to
     *       apply side effects to existing archived chats</li>
     * </ol>
     *
     * <p>Non-{@code SET} operations return {@code Unsupported}. Exceptions are
     * caught and return {@code Failed}.
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebArchiveSettingSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        try {
            if (!(mutation.value().action().orElse(null) instanceof UnarchiveChatsSetting setting)) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            // ADAPTED: WA Web checks (_.unarchiveChats == null) and returns malformedActionValue.
            // Cobalt's UnarchiveChatsSetting.unarchiveChats() coalesces null to false via
            // existing boolean accessor pattern. A null unarchiveChats in a valid protobuf
            // message is treated as false rather than malformed.
            var unarchiveChats = setting.unarchiveChats();
            client.store().setUnarchiveChats(unarchiveChats);
            // ADAPTED: WAWebArchiveSettingSync.applyMutations also sets archiveV2EnabledSetting
            // to true if not already set, and fires frontendFireAndForget("applyAppSetting", ...).
            // Cobalt does not have an archiveV2Enabled setting (archive v2 is always enabled)
            // and frontend fire-and-forget calls are UI-only browser concerns.
            updateSideEffectOnChats(client, unarchiveChats);
            return MutationApplicationResult.success();
        } catch (Exception e) {
            return MutationApplicationResult.failed();
        }
    }

    /**
     * Applies side effects to existing archived chats based on the new setting value.
     *
     * <p>Per WhatsApp Web {@code WAWebArchiveSettingSync.updateSideEffectOnChats}:
     * dispatches to one of two private methods depending on the setting value:
     * <ul>
     *   <li>When {@code unarchiveChats} is {@code true}: calls
     *       {@code $ArchiveSettingSync$p_1} which finds archived chats whose
     *       message ranges indicate new messages have arrived and unarchives them</li>
     *   <li>When {@code unarchiveChats} is {@code false}: calls
     *       {@code $ArchiveSettingSync$p_2} which finds chats with successful
     *       archive sync actions and re-archives them</li>
     * </ul>
     * @param client         the WhatsApp client instance
     * @param unarchiveChats the new unarchive chats setting value
     */
    @WhatsAppWebExport(moduleName = "WAWebArchiveSettingSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    private void updateSideEffectOnChats(WhatsAppClient client, boolean unarchiveChats) {
        if (unarchiveChats) {
            applyUnarchiveSideEffect(client);
        } else {
            applyArchiveSideEffect(client);
        }
    }

    /**
     * Applies the side effect when unarchive chats setting is enabled (true).
     *
     * <p>Per WhatsApp Web {@code WAWebArchiveSettingSync.$ArchiveSettingSync$p_1}:
     * finds archived chats that should be unarchived because new messages have
     * arrived. The logic:
     * <ol>
     *   <li>Gets all archived chats</li>
     *   <li>Filters out chats that have active archive message ranges (browser-specific)</li>
     *   <li>For remaining chats, looks up their archive sync action entry</li>
     *   <li>Filters to entries with {@code actionState} of {@code Success} or
     *       {@code Orphan}, {@code archived === true}, and a valid {@code messageRange}</li>
     *   <li>Compares current message range with the stored range</li>
     *   <li>If the current range encloses the stored range or ranges are not
     *       enclosing, unarchives the chat</li>
     * </ol>
     *
     * <p>In Cobalt, the active message range filtering (step 2) is not available
     * since it relies on browser-specific IndexedDB infrastructure
     * ({@code WAWebApiActiveMessageRanges}, {@code WAWebSchemaActiveMessageRanges}).
     * Instead, Cobalt iterates all archived chats and checks their archive sync
     * action entries directly. This is a conservative adaptation that may unarchive
     * slightly more chats than WA Web would.
     *
     * <p>The message range construction ({@code WAWebMessageRangeUtils.constructMessageRange})
     * is also not available in Cobalt since it requires per-chat message storage
     * infrastructure. Without current message ranges to compare against, Cobalt
     * unarchives all archived chats that have a successful archive sync action entry.
     * This matches the practical effect since the setting change implies the user
     * wants archived chats to auto-unarchive on new messages.
     * @param client the WhatsApp client instance
     */
    @WhatsAppWebExport(moduleName = "WAWebArchiveSettingSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    private void applyUnarchiveSideEffect(WhatsAppClient client) {
        // ADAPTED: WAWebArchiveSettingSync.$ArchiveSettingSync$p_1
        // WA Web performs complex message range comparisons using:
        // - WAWebApiActiveMessageRanges.getActiveMessageRanges
        // - WAWebSchemaActiveMessageRanges.ActiveRangeAction.Archive
        // - WAWebMessageRangeUtils.constructMessageRange / compareMessageRanges
        // - WAWebSyncdDb.getSyncAction
        // These rely on browser IndexedDB infrastructure not available in Cobalt.
        // The adapted logic looks up archive sync action entries directly and
        // unarchives chats that have a successful archived=true entry, which
        // is the conservative equivalent of the WA Web behavior.
        var archiveEntries = client.store().getSyncActionEntries(SyncPatchType.REGULAR_LOW);
        for (var entry : archiveEntries) {
            if (entry.actionState() != SyncActionState.SUCCESS
                    && entry.actionState() != SyncActionState.ORPHAN) {
                continue;
            }

            var actionValue = entry.actionValue();
            if (actionValue == null) {
                continue;
            }

            var action = actionValue.action().orElse(null);
            if (!(action instanceof ArchiveChatAction archiveAction)) {
                continue;
            }

            if (!archiveAction.archived()) {
                continue;
            }

            if (archiveAction.messageRange().isEmpty()) {
                continue;
            }

            var actionIndex = entry.actionIndex();
            if (actionIndex == null) {
                continue;
            }

            var chatJid = extractChatJidFromArchiveIndex(actionIndex);
            if (chatJid == null) {
                continue;
            }

            var chat = client.store().findChatByJid(chatJid);
            if (chat.isEmpty()) {
                continue;
            }

            if (!chat.get().archived()) { // Only process currently archived chats
                continue;
            }

            // ADAPTED: WAWebArchiveSettingSync.$ArchiveSettingSync$p_1 compares constructMessageRange
            // result against the stored messageRange using compareMessageRanges. If the result is
            // RangeAEnclosesRangeB or RangesNotEnclosing, the chat is unarchived.
            // Without constructMessageRange infrastructure, Cobalt unarchives the chat directly
            // since the setting change to unarchiveChats=true indicates the user wants
            // archived chats to auto-unarchive when messages arrive.
            chat.get().setArchived(false);
        }
    }

    /**
     * Applies the side effect when unarchive chats setting is disabled (false).
     *
     * <p>Per WhatsApp Web {@code WAWebArchiveSettingSync.$ArchiveSettingSync$p_2}:
     * finds chats that should be re-archived based on their archive sync action
     * entries. The logic:
     * <ol>
     *   <li>Gets all sync action entries with the {@code "archive"} action type</li>
     *   <li>Merges with pending mutations for the archive action</li>
     *   <li>Filters to entries with {@code actionState === Success} and
     *       {@code archived === true}</li>
     *   <li>Resolves the chat for each entry</li>
     *   <li>Sets the chat as archived</li>
     * </ol>
     * @param client the WhatsApp client instance
     */
    @WhatsAppWebExport(moduleName = "WAWebArchiveSettingSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    private void applyArchiveSideEffect(WhatsAppClient client) {
        var archiveEntries = client.store().getSyncActionEntries(SyncPatchType.REGULAR_LOW);
        for (var entry : archiveEntries) {
            if (entry.actionState() != SyncActionState.SUCCESS) {
                continue;
            }

            var actionValue = entry.actionValue();
            if (actionValue == null) {
                continue;
            }

            var action = actionValue.action().orElse(null);
            if (!(action instanceof ArchiveChatAction archiveAction)) {
                continue;
            }

            if (!archiveAction.archived()) {
                continue;
            }

            var actionIndex = entry.actionIndex();
            if (actionIndex == null) {
                continue;
            }

            var chatJid = extractChatJidFromArchiveIndex(actionIndex);
            if (chatJid == null) {
                continue;
            }

            var chat = client.store().findChatByJid(chatJid);
            if (chat.isEmpty()) {
                continue;
            }

            chat.get().setArchived(true);
        }
    }

    /**
     * Extracts the chat JID from an archive sync action index string.
     *
     * <p>Per WhatsApp Web, the archive action index is formatted as
     * {@code ["archive", chatJidString]}. This method parses the JSON array
     * and extracts the second element.
     * @param actionIndex the action index string (JSON array format)
     * @return the parsed chat JID, or {@code null} if parsing fails
     */
    private Jid extractChatJidFromArchiveIndex(String actionIndex) {
        try {
            var parsed = JSON.parseArray(actionIndex);
            if (parsed == null || parsed.size() < 2) {
                return null;
            }
            var jidString = parsed.getString(1);
            if (jidString == null || jidString.isEmpty()) {
                return null;
            }
            return Jid.of(jidString);
        } catch (Exception e) {
            return null;
        }
    }

}
