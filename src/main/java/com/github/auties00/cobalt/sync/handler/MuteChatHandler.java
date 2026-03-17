package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.chat.ChatMute;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.chat.MuteAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles mute chat actions.
 */
public final class MuteChatHandler implements WebAppStateActionHandler {
    public static final MuteChatHandler INSTANCE = new MuteChatHandler();

    private MuteChatHandler() {

    }

    @Override
    public String actionName() {
        return MuteAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return MuteAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return MuteAction.ACTION_VERSION;
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

        if (!(mutation.value().action().orElse(null) instanceof MuteAction action)) {
            return MutationApplicationResult.malformed();
        }

        if (action.muted() && action.muteEndTimestamp().isEmpty()) {
            return MutationApplicationResult.malformed();
        }

        var chatJidString = JSON.parseArray(mutation.index()).getString(1);
        if (chatJidString == null || chatJidString.isEmpty()) {
            return MutationApplicationResult.malformed();
        }

        var chatJid = Jid.of(chatJidString);
        var chat = client.store().findChatByJid(chatJid);
        if (chat.isEmpty()) {
            return MutationApplicationResult.orphan(chatJidString, "Chat");
        }

        var muteEndMillis = action.muteEndTimestamp().map(java.time.Instant::toEpochMilli).orElse(0L);
        var muteEndSeconds = muteEndMillis > 0 && muteEndMillis < System.currentTimeMillis()
                ? 0L
                : muteEndMillis / 1000;
        chat.get().setMute(ChatMute.mutedUntil(muteEndSeconds));

        if (chatJid.hasServer(JidServer.groupOrCommunity()) && client.abPropsService().getBool(ABProp.ENABLE_MENTION_EVERYONE_RECEIVER_WEB)) {
            action.muteEveryoneMentionEndTimestamp().ifPresent(mentionTs -> {
                var mentionMillis = mentionTs.toEpochMilli();
                var mentionSeconds = mentionMillis > 0 && mentionMillis < System.currentTimeMillis()
                        ? 0L
                        : mentionMillis / 1000;
                client.store().setMentionEveryoneMuteExpiration(chatJid, ChatMute.mutedUntil(mentionSeconds));
            });
        }

        return MutationApplicationResult.success();
    }
}
