package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.ChatMute;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.chat.MuteAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import java.time.Instant;

/**
 * Applies the {@code mute} app-state sync action that mutes or unmutes a
 * chat across the user's linked devices.
 *
 * @apiNote
 * Drives the chat-list "Mute notifications" affordance: when the primary
 * device mutes or unmutes a conversation the resulting timestamp fans
 * out across the {@link SyncPatchType#REGULAR_HIGH} collection. For
 * group chats the action also carries an optional
 * {@code muteEveryoneMentionEndTimestamp} that gates @-everyone alerts
 * separately. The mutation index keys each entry by the chat JID,
 * formatted as
 * {@snippet :
 *     ["mute", chatJid]
 * }
 *
 * @implNote
 * This implementation flattens WA Web's
 * {@code WAWebMuteChatSync.applyMutations} into a single per-mutation
 * call: WA Web's per-batch
 * {@code malformedActionValue}/{@code Unsupported} counters are
 * dropped (Cobalt's per-mutation interface returns each result
 * directly), and the {@code muteCollectionAdd} frontend dispatch is
 * omitted because Cobalt has no UI consumer. The
 * {@code mentionAllMuteExpiration} branch is gated on
 * {@link ABProp#ENABLE_MENTION_EVERYONE_RECEIVER_WEB} exactly as in
 * WA Web; mutation-time exceptions surface as
 * {@link MutationApplicationResult#failed()} to mirror WA's
 * try/catch wrapper.
 */
@WhatsAppWebModule(moduleName = "WAWebMuteChatSync")
public final class MuteChatHandler implements WebAppStateActionHandler {
    /**
     * The AB-props service consulted for the
     * {@link ABProp#ENABLE_MENTION_EVERYONE_RECEIVER_WEB} gate on every
     * group mute mutation.
     *
     * @apiNote
     * Internal collaborator injected at construction; never accessed
     * outside {@link #applyMutation(WhatsAppClient, DecryptedMutation.Trusted)}.
     */
    private final ABPropsService abPropsService;

    /**
     * Constructs a mute-chat handler bound to the given AB-props service.
     *
     * @apiNote
     * Used by the sync handler registry; the AB-props service is consulted
     * on every group mute mutation to decide whether to honour
     * {@code muteEveryoneMentionEndTimestamp}.
     *
     * @implNote
     * This implementation mirrors the WA Web {@code WAWebMuteChatSync}
     * constructor: it sets {@code chatJidIndex = 1} and
     * {@code collectionName = RegularHigh} on the prototype and inherits
     * from {@code ChatSyncdActionBase}. Cobalt holds the chat-jid index
     * convention as a per-handler constant inside
     * {@link #applyMutation(WhatsAppClient, DecryptedMutation.Trusted)}
     * and surfaces the collection via {@link #collectionName()}.
     *
     * @param abPropsService the AB-props service consulted on every
     *                       group mute mutation
     */
    @WhatsAppWebExport(moduleName = "WAWebMuteChatSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public MuteChatHandler(ABPropsService abPropsService) {
        this.abPropsService = abPropsService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMuteChatSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return MuteAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMuteChatSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return MuteAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMuteChatSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return MuteAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation mirrors the WA Web per-mutation arm of
     * {@code WAWebMuteChatSync.applyMutations}: a missing
     * {@code muteEndTimestamp} alongside {@code muted == true} is
     * rejected as {@link MutationApplicationResult#malformed()}, the
     * resolved chat must exist in
     * {@link com.github.auties00.cobalt.store.WhatsAppStore} (otherwise
     * {@link MutationApplicationResult#orphan(String, String)} with
     * {@code modelType="Chat"}), and timestamps are clamped per WA Web
     * via {@code muteEndMillis > 0 && muteEndMillis < now ? 0 : ms / 1000}.
     * For group/community chats with
     * {@link ABProp#ENABLE_MENTION_EVERYONE_RECEIVER_WEB} enabled, the
     * mention-everyone expiration is computed with the same
     * past/future/zero clamping and persisted via
     * {@code store.setMentionEveryoneMuteExpiration}. The
     * {@code muteCollectionAdd} frontend fire-and-forget is dropped:
     * Cobalt has no UI consumer; mutation-time exceptions are caught
     * and reported as {@link MutationApplicationResult#failed()}.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMuteChatSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        try {
            if (!(mutation.value().action().orElse(null) instanceof MuteAction action)) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            var chatJidString = JSON.parseArray(mutation.index()).getString(1);
            if (chatJidString == null || chatJidString.isEmpty()) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }

            if (action.muted() && action.muteEndTimestamp().isEmpty()) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            var chatJid = Jid.of(chatJidString);
            if (chatJid == null) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }

            var chat = client.store().findChatByJid(chatJid);
            if (chat.isEmpty()) {
                return MutationApplicationResult.orphan(chatJidString, "Chat");
            }

            var muteEndMillis = action.muteEndTimestamp().map(Instant::toEpochMilli).orElse(0L);
            var muteEndSeconds = muteEndMillis > 0 && muteEndMillis < System.currentTimeMillis()
                    ? 0L
                    : muteEndMillis / 1000;
            chat.get().setMute(ChatMute.mutedUntil(muteEndSeconds));

            if (chatJid.hasGroupOrCommunityServer()
                    && abPropsService.getBool(ABProp.ENABLE_MENTION_EVERYONE_RECEIVER_WEB)) {
                action.muteEveryoneMentionEndTimestamp().ifPresent(mentionTs -> {
                    var mentionMillis = mentionTs.toEpochMilli();
                    long mentionSeconds;
                    if (mentionMillis > System.currentTimeMillis()) {
                        mentionSeconds = mentionMillis / 1000;
                    } else if (mentionMillis > 0) {
                        mentionSeconds = 0L;
                    } else {
                        mentionSeconds = mentionMillis;
                    }
                    client.store().setMentionEveryoneMuteExpiration(chatJid, ChatMute.mutedUntil(mentionSeconds));
                });
            }

            return MutationApplicationResult.success();
        } catch (Exception e) {
            return MutationApplicationResult.failed();
        }
    }

}
