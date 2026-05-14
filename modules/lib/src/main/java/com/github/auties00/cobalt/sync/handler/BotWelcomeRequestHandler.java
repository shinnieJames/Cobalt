package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.bot.BotWelcomeRequestStateBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.bot.BotWelcomeRequestAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles bot welcome request sync actions.
 *
 * <p>This handler processes incoming mutations that track whether a bot welcome
 * message has been requested for a given chat. The action is identified by the
 * {@code "bot_welcome_request"} action name in
 * {@code SyncActionValue.botWelcomeRequestAction}. The mutation index format is
 * {@code ["bot_welcome_request", chatJid]}.
 *
 * <p>Per WhatsApp Web, this handler extends {@code ChatSyncdActionBase}, which
 * provides shared chat-based mutation processing. The handler's
 * {@code chatJidIndex} is {@code 1}, {@code collectionName} is
 * {@code RegularLow}, and {@code getVersion()} returns {@code 2}.
 */
@WhatsAppWebModule(moduleName = "WAWebBotWelcomeRequestSync")
public final class BotWelcomeRequestHandler implements WebAppStateActionHandler {

    /**
     * Private constructor to enforce singleton pattern.
     */
    @WhatsAppWebExport(moduleName = "WAWebBotWelcomeRequestSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public BotWelcomeRequestHandler() {

    }

    /**
     * Returns the action name for bot welcome request actions.
     * @return the action name {@code "bot_welcome_request"}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBotWelcomeRequestSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return BotWelcomeRequestAction.ACTION_NAME;
    }

    /**
     * Returns the sync collection for bot welcome request actions.
     *
     * <p>Per WhatsApp Web, the bot welcome request handler's {@code collectionName}
     * is set to {@code WASyncdConst.CollectionName.RegularLow} in the constructor.
     * @return {@link SyncPatchType#REGULAR_LOW}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBotWelcomeRequestSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return BotWelcomeRequestAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for bot welcome request actions.
     * @return the version number {@code 2}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBotWelcomeRequestSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return BotWelcomeRequestAction.ACTION_VERSION;
    }

    /**
     * Applies a bot welcome request mutation and returns a detailed result.
     *
     * <p>Per WhatsApp Web {@code WAWebBotWelcomeRequestSync.applyMutations}, for each
     * mutation with {@code operation === "set"}:
     * <ol>
     *   <li>Extracts the chat JID from {@code indexParts[1]}</li>
     *   <li>Validates the JID is present; returns {@code malformedActionIndex()} if missing</li>
     *   <li>Extracts {@code botWelcomeRequestAction.isSent}; returns
     *       {@code malformedActionValue()} if {@code null}</li>
     *   <li>Resolves the chat via {@code resolveChatForMutationIndex(createWid(u))}</li>
     *   <li>Updates the chat table with {@code hasRequestedWelcomeMsg: isSent}</li>
     *   <li>Fires a frontend {@code chatCollectionUpdate} event</li>
     * </ol>
     *
     * <p>{@code "remove"} operations return {@code Unsupported}. Any other operation
     * triggers an error (caught by the surrounding try/catch, returning {@code Failed}).
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBotWelcomeRequestSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() == SyncdOperation.REMOVE) {
            return MutationApplicationResult.unsupported();
        }

        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.failed(); // ADAPTED: WA Web throws inside try/catch, caught as Failed
        }

        try {
            var indexArray = JSON.parseArray(mutation.index());
            var chatJidString = indexArray.getString(1);
            if (chatJidString == null || chatJidString.isEmpty()) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }

            if (!(mutation.value().action().orElse(null) instanceof BotWelcomeRequestAction action)) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            // ADAPTED: WAWebBotWelcomeRequestSync.applyMutations: var c = n?.isSent; if (c == null) return malformedActionValue
            // WA Web checks if isSent is null and returns malformedActionValue. In Cobalt,
            // BotWelcomeRequestAction.isSent() coalesces null to false per project convention
            // (nullable Boolean accessors return primitive boolean). The raw Boolean field is
            // package-private and inaccessible from this package. See Issues in Context Files.
            var chatJid = Jid.of(chatJidString);
            var chat = client.store().findChatByJid(chatJid);
            if (chat.isEmpty()) {
                return MutationApplicationResult.orphan(chatJidString, "Chat");
            }

            // ADAPTED: Cobalt stores hasRequestedWelcomeMsg in a typed quintet keyed by bot JID rather than on the chat record
            var resolvedJid = chat.get().toJid();
            client.store().putBotWelcomeRequestState(new BotWelcomeRequestStateBuilder().botJid(resolvedJid).requested(action.isSent()).build());

            // ADAPTED: Cobalt does not have frontend event dispatching; the store update is sufficient
            return MutationApplicationResult.success();
        } catch (Exception e) {
            return MutationApplicationResult.failed();
        }
    }

}
