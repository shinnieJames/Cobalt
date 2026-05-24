package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.chat.UsernameChatStartModeAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Mirrors the user's preferred addressing scheme for chats started from a
 * username (LID versus phone number).
 *
 * @apiNote
 * Cobalt embedders never invoke this handler directly; the sync dispatcher
 * would route incoming {@code usernameChatStartMode} mutations here if the
 * server ever emits one. The handler persists the
 * {@link UsernameChatStartModeAction.ChatStartMode} value onto
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#setUsernameChatStartMode(UsernameChatStartModeAction.ChatStartMode)}
 * so any chat the user opens from the username discovery surface uses the
 * preferred identifier.
 *
 * @implNote
 * This implementation is forward-looking. WA Web defines the
 * {@code UsernameChatStartModeAction} protobuf in
 * {@code WAWebProtobufSyncAction.pb} at action index 59 with name
 * {@code "usernameChatStartMode"} and the
 * {@code USERNAME_CHAT_START_MODE -> REGULAR} collection-router branch,
 * but the bundle ships no concrete {@code WAWebUsernameChatStartModeSync}
 * module: WA Web's dispatcher would currently drop any incoming mutation
 * with this action as unsupported. Cobalt's handler ingests it today so
 * the wire payload is not lost.
 */
public final class UsernameChatStartModeHandler implements WebAppStateActionHandler {

    /**
     * Constructs the handler.
     *
     * @apiNote
     * The handler is stateless; Cobalt's sync registry holds a single
     * instance per client.
     */
    public UsernameChatStartModeHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String actionName() {
        return UsernameChatStartModeAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation returns {@link SyncPatchType#REGULAR} as
     * declared by WA Web's
     * {@code e === c.USERNAME_CHAT_START_MODE ? u.REGULAR} branch in
     * {@code WAWebProtobufSyncAction.pb}'s collection router.
     */
    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation returns
     * {@link UsernameChatStartModeAction#ACTION_VERSION}; WA Web has no
     * concrete sync module so the value is the initial protobuf version
     * and should be revisited once WA Web ships the matching handler.
     */
    @Override
    public int version() {
        return UsernameChatStartModeAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation follows the canonical shape used by sibling
     * handlers because WA Web ships no concrete module to mirror:
     * non-{@code SET} operations are unsupported, the mutation value
     * must decode to a {@link UsernameChatStartModeAction} with a
     * populated {@link UsernameChatStartModeAction#chatStartMode()}, and
     * the resolved enum is written into
     * {@link com.github.auties00.cobalt.store.WhatsAppStore#setUsernameChatStartMode(UsernameChatStartModeAction.ChatStartMode)}.
     * Sibling WA Web handlers wrap their body in a {@code try/catch}
     * that maps any throw to {@link MutationApplicationResult#failed()};
     * Cobalt lets exceptions propagate to the configured
     * {@link com.github.auties00.cobalt.exception.WhatsAppClientErrorHandler}
     * per the Cobalt error model.
     */
    @Override
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof UsernameChatStartModeAction action)
                || action.chatStartMode().isEmpty()) {
            return MutationApplicationResult.malformed();
        }

        client.store().setUsernameChatStartMode(action.chatStartMode().get());

        return MutationApplicationResult.success();
    }
}
