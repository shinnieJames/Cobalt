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
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import java.time.Instant;

/**
 * Handles mute chat sync actions.
 *
 * <p>This handler processes incoming mutations that mute or unmute chats,
 * applying the {@code muteEndTimestamp} (and, for groups, the optional
 * {@code muteEveryoneMentionEndTimestamp}) to the chat row in the store.
 *
 * <p>The action is identified by the {@code "mute"} action name in
 * {@code SyncActionValue.muteAction}. The mutation index format is
 * {@code ["mute", chatJid]}.
 *
 * <p>Per WhatsApp Web, this handler extends {@code ChatSyncdActionBase},
 * which shares the {@code chatJidIndex = 1} convention and supplies the
 * default per-chat sync scaffolding. In Cobalt, that inheritance is
 * flattened because each handler implements {@link WebAppStateActionHandler}
 * directly.
 */
@WhatsAppWebModule(moduleName = "WAWebMuteChatSync")
public final class MuteChatHandler implements WebAppStateActionHandler {
    /**
     * The AB-props service consulted for the mention-everyone gates on
     * both the receive and send paths.
     */
    private final ABPropsService abPropsService;

    /**
     * Constructs a mute-chat handler bound to the given AB-props service.
     *
     * @param abPropsService the AB-props service consulted on every
     *                       mute mutation
     */
    @WhatsAppWebExport(moduleName = "WAWebMuteChatSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public MuteChatHandler(ABPropsService abPropsService) {
        this.abPropsService = abPropsService;
    }

    /**
     * Returns the action name for mute chat actions.
     * @return the action name {@code "mute"}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMuteChatSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return MuteAction.ACTION_NAME;
    }

    /**
     * Returns the sync collection for mute chat actions.
     *
     * <p>Per WhatsApp Web, the mute handler's {@code collectionName} is set to
     * {@code WASyncdConst.CollectionName.RegularHigh} in the constructor.
     * @return {@link SyncPatchType#REGULAR_HIGH}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMuteChatSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return MuteAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for mute chat actions.
     * @return the version number {@code 2}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMuteChatSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return MuteAction.ACTION_VERSION;
    }

    /**
     * Applies a mute chat mutation and returns the detailed outcome.
     *
     * <p>Per WhatsApp Web {@code WAWebMuteChatSync.applyMutations}, for each
     * mutation with {@code operation === "set"}:
     * <ol>
     *   <li>Extracts the chat JID from {@code indexParts[1]}</li>
     *   <li>Validates the JID is a valid WID via {@code WAWebWid.isWid}</li>
     *   <li>Extracts {@code muteAction.muted} and
     *       {@code muteAction.muteEndTimestamp}; returns
     *       {@code malformedActionValue} when {@code muted} is {@code true}
     *       but the end timestamp is missing</li>
     *   <li>Resolves the chat via
     *       {@code WAWebSyncdGetChat.resolveChatForMutationIndex}</li>
     *   <li>Computes the mute expiration in seconds: past timestamps collapse
     *       to {@code 0}, future timestamps are converted via
     *       {@code Math.floor(ms / 1000)}</li>
     *   <li>For groups only, when the
     *       {@code enable_mention_everyone_receiver_web} AB prop is enabled,
     *       applies the same conversion to
     *       {@code muteEveryoneMentionEndTimestamp} and stores the
     *       {@code mentionAllMuteExpiration} alongside the regular mute
     *       expiration</li>
     *   <li>Merges {@code {muteExpiration, mentionAllMuteExpiration?}} into
     *       the chat row via {@code getChatTable().merge}</li>
     *   <li>Emits a {@code muteCollectionAdd} backend event (intentionally
     *       skipped in Cobalt because there is no frontend consumer)</li>
     * </ol>
     *
     * <p>Non-{@code SET} operations return {@code Unsupported}. Exceptions are
     * caught and return {@code Failed} to match WA Web's per-mutation recovery.
     *
     * <p>Telemetry (the {@code WALogger.WARN} calls that emit the malformed
     * and unsupported counts at the end of the batch) is intentionally
     * omitted in Cobalt because WAM events are not forwarded.
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
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

            // ADAPTED: Cobalt's MuteAction.muted() coalesces null to false per Cobalt nullable-Boolean
            // conventions; the (d && m == null) check — muted=true with missing timestamp — is preserved.
            if (action.muted() && action.muteEndTimestamp().isEmpty()) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            var chatJid = Jid.of(chatJidString);
            if (chatJid == null) { // ADAPTED: Jid.of returns null for null input; WA Web uses isWid() validation
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

            //   var h = maybeNumberOrThrowIfTooLarge(c?.muteEveryoneMentionEndTimestamp);
            //   var y;
            //   h != null && getABPropConfigValue("enable_mention_everyone_receiver_web") && (
            //     h > unixTimeMs() ? y = Math.floor(h / 1e3) : h > 0 ? y = 0 : y = h
            //   );
            //   isGroup(createWid(_)) && y != null && (C.mentionAllMuteExpiration = y)
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

            // SKIPPED: Cobalt has no frontend consumer for UI collection updates.
            return MutationApplicationResult.success();
        } catch (Exception e) {
            return MutationApplicationResult.failed();
        }
    }

}
