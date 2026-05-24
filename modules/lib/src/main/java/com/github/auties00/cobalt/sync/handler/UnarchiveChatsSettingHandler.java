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
 * Mirrors the "Keep chats archived" preference and applies the matching
 * side-effect to every previously archived chat.
 *
 * @apiNote
 * Cobalt embedders never invoke this handler directly; the sync dispatcher
 * routes incoming {@code setting_unarchiveChats} mutations here whenever
 * the user toggles "Keep chats archived" on another linked device. When
 * the setting flips to "do not keep archived", the handler unarchives
 * archived chats that have new messages waiting; when it flips back, the
 * handler re-archives previously-archived chats per their stored
 * {@link ArchiveChatAction} entries. The boolean preference itself is
 * persisted on
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#setUnarchiveChats(boolean)}.
 */
@WhatsAppWebModule(moduleName = "WAWebArchiveSettingSync")
public final class UnarchiveChatsSettingHandler implements WebAppStateActionHandler {

    /**
     * Constructs the handler.
     *
     * @apiNote
     * The handler is stateless; Cobalt's sync registry holds a single
     * instance per client.
     */
    @WhatsAppWebExport(moduleName = "WAWebArchiveSettingSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public UnarchiveChatsSettingHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebArchiveSettingSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return UnarchiveChatsSetting.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebArchiveSettingSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return UnarchiveChatsSetting.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebArchiveSettingSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return UnarchiveChatsSetting.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation mirrors WA Web's
     * {@code WAWebArchiveSettingSync.applyMutations} which reads only the
     * final mutation from the batch ({@code var l = e[e.length - 1]}).
     * Every earlier mutation is acknowledged as
     * {@link MutationApplicationResult#skipped()} so the dispatcher can
     * still correlate the result list with the input by position; the
     * trailing mutation runs through
     * {@link #applyMutation(WhatsAppClient, DecryptedMutation.Trusted)}.
     * An empty batch returns an empty list because the dispatcher does
     * not call this method with one; WA Web logs the empty case as
     * {@code Failed} but Cobalt does not exercise that path.
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
     * {@inheritDoc}
     *
     * @implNote
     * This implementation mirrors WA Web's single-mutation path inside
     * {@code WAWebArchiveSettingSync.applyMutations}: it requires a
     * {@link SyncdOperation#SET}, decodes the
     * {@link UnarchiveChatsSetting} value (treating a missing nullable
     * Boolean as {@code false} per the Cobalt nullable-boolean
     * convention), persists it via
     * {@link com.github.auties00.cobalt.store.WhatsAppStore#setUnarchiveChats(boolean)},
     * and runs {@link #updateSideEffectOnChats(WhatsAppClient, boolean)}.
     * WA Web also writes {@code archiveV2EnabledSetting = true} when
     * unset and fires {@code WAWebBackendApi.frontendFireAndForget("applyAppSetting", ...)};
     * Cobalt drops both because archive-v2 is always enabled in Cobalt
     * and there is no frontend to fire at.
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

            var unarchiveChats = setting.unarchiveChats();
            client.store().setUnarchiveChats(unarchiveChats);
            updateSideEffectOnChats(client, unarchiveChats);
            return MutationApplicationResult.success();
        } catch (Exception e) {
            return MutationApplicationResult.failed();
        }
    }

    /**
     * Dispatches the setting-change side-effect to the matching
     * unarchive / re-archive helper.
     *
     * @apiNote
     * Used by {@link #applyMutation(WhatsAppClient, DecryptedMutation.Trusted)}
     * after the new setting value has been persisted; the choice between
     * unarchiving newly-active chats and re-archiving previously
     * archived chats is determined solely by the setting value.
     *
     * @implNote
     * This implementation mirrors WA Web's
     * {@code updateSideEffectOnChats(unarchiveChats, _)} two-branch
     * dispatch, delegating to
     * {@link #applyUnarchiveSideEffect(WhatsAppClient)} or
     * {@link #applyArchiveSideEffect(WhatsAppClient)}.
     *
     * @param client         the {@link WhatsAppClient} whose store is being updated
     * @param unarchiveChats the new setting value
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
     * Unarchives chats that have new activity once the user opts out of
     * "Keep chats archived".
     *
     * @apiNote
     * Side-effect of switching the setting to {@code true}; iterates the
     * stored {@link ArchiveChatAction} entries and clears the archived
     * flag on chats that have a successful archive sync action with a
     * recorded message range.
     *
     * @implNote
     * This implementation simplifies WA Web's
     * {@code $ArchiveSettingSync$p_1} which compares the current
     * message range against the stored range via
     * {@code WAWebMessageRangeUtils.compareMessageRanges} and only
     * unarchives when the current range strictly encloses the stored
     * one. The comparison relies on WA Web's
     * {@code WAWebApiActiveMessageRanges} / {@code WAWebSchemaActiveMessageRanges}
     * IDB layer which Cobalt does not maintain; without that probe Cobalt
     * conservatively unarchives every archived chat with a successful
     * archive entry, which matches the user-facing intent of the
     * setting change.
     *
     * @param client the {@link WhatsAppClient} whose store is being updated
     */
    @WhatsAppWebExport(moduleName = "WAWebArchiveSettingSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    private void applyUnarchiveSideEffect(WhatsAppClient client) {
        // TODO: replicate WA Web's MessageRangeUtils.compareMessageRanges gating once Cobalt
        //       maintains active-message-range tracking; today Cobalt may unarchive chats
        //       that WA Web would have left archived.
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

            if (!chat.get().archived()) {
                continue;
            }

            chat.get().setArchived(false);
        }
    }

    /**
     * Re-archives chats that the user previously archived once the
     * "Keep chats archived" setting is turned back on.
     *
     * @apiNote
     * Side-effect of switching the setting to {@code false}; replays
     * stored {@link ArchiveChatAction} entries with
     * {@code archived = true} and a successful action state.
     *
     * @implNote
     * This implementation mirrors WA Web's
     * {@code $ArchiveSettingSync$p_2} which merges the persisted archive
     * entries with the pending mutations table. Cobalt only inspects the
     * persisted entries because it does not maintain a parallel pending
     * archive mutations log; the practical effect is identical because
     * the dispatcher applies pending mutations to the store before
     * invoking this handler.
     *
     * @param client the {@link WhatsAppClient} whose store is being updated
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
     * Extracts the chat JID from a stored archive-action index.
     *
     * @apiNote
     * The archive action index has the shape
     * {@code ["archive", chatJidString]}; this helper parses the JSON
     * array and returns the chat JID, defaulting to {@code null} for
     * any parse failure.
     *
     * @implNote
     * This implementation tolerates a missing index, a missing JID slot,
     * an empty JID string, and JID parse failures by returning
     * {@code null} so callers can simply skip the entry instead of
     * raising an exception out of the side-effect helpers.
     *
     * @param actionIndex the JSON-encoded archive-action index
     * @return the parsed chat JID, or {@code null} on any failure
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
