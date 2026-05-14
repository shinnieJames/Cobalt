package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.chat.UsernameChatStartModeAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
/**
 * Applies {@code usernameChatStartMode} mutations decoded from app state sync.
 *
 * <p>Handles the {@code SyncActionValue.UsernameChatStartModeAction} sync
 * action in the {@link SyncPatchType#REGULAR} collection. A mutation of this
 * type carries the user's preferred chat-start identifier mode for chats
 * initiated against another user's username, encoded as a
 * {@link UsernameChatStartModeAction.ChatStartMode} enum value:
 * {@code LID} (start the chat from the linked-device identifier) or
 * {@code PN} (start the chat from the phone-number identifier). The setting
 * controls which addressing scheme is used when materialising a new chat
 * thread that was opened via the username discovery surface.
 *
 * <h2>Forward-looking handler &mdash; no current WA Web counterpart</h2>
 *
 * <p>This handler has <b>no concrete WA Web sync module</b>. WA Web defines
 * the {@code UsernameChatStartModeAction} protobuf schema (action index
 * {@code 59}, action name {@code "usernameChatStartMode"}, collection
 * {@code REGULAR}, version {@code 1}) inside {@code WAWebProtobufSyncAction.pb},
 * but it never registers a {@code WAWebUsernameChatStartModeSync} module in
 * {@code WAWebCollectionHandlerActions.ActionHandlers}. As a result, when such
 * a mutation reaches WA Web, {@code WAWebSyncdGetActionHandler.getActionHandler}
 * returns {@code undefined} and the mutation is dropped by the upstream
 * dispatcher as unsupported.
 *
 * <p>Cobalt anticipates a future WA Web release that adds the missing handler
 * by implementing the obvious shape derived from the protobuf metadata:
 * <ul>
 *   <li>{@code SET}-only operation,</li>
 *   <li>routed via {@link UsernameChatStartModeAction#ACTION_NAME},</li>
 *   <li>persisted to {@link com.github.auties00.cobalt.store.WhatsAppStore#setUsernameChatStartMode},</li>
 *   <li>stored in {@link SyncPatchType#REGULAR} at version
 *       {@link UsernameChatStartModeAction#ACTION_VERSION}.</li>
 * </ul>
 *
 * <p>The metadata mirrors {@code WAWebProtobufSyncAction.pb}'s
 * {@code getMutationProps$CollectionName} branch
 * {@code e === c.USERNAME_CHAT_START_MODE ? u.REGULAR}, so when WA Web ships
 * the real {@code WAWebUsernameChatStartModeSync} this file should already be
 * wire-compatible.
 */
public final class UsernameChatStartModeHandler implements WebAppStateActionHandler {

    /**
     * Creates a new {@code UsernameChatStartModeHandler}.
     *
     * <p>The constructor is private because callers should always go through
     * {@link #INSTANCE}, matching the WA Web module-level singleton pattern
     * used by other sync handlers.
     */
    public UsernameChatStartModeHandler() {

    }

    /**
     * Returns the action name this handler processes.
     * @return the constant {@link UsernameChatStartModeAction#ACTION_NAME},
     *         always {@code "usernameChatStartMode"}
     */
    @Override
    public String actionName() {
        return UsernameChatStartModeAction.ACTION_NAME;
    }

    /**
     * Returns the sync collection this handler's action belongs to.
     *
     * <p>Per {@code WAWebProtobufSyncAction.pb} the
     * {@code getMutationProps$CollectionName} resolver branches on
     * {@code e === c.USERNAME_CHAT_START_MODE} and returns {@code u.REGULAR},
     * so the mutation is stored in the regular-priority sync collection.
     * @return {@link SyncPatchType#REGULAR}
     */
    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR;
    }

    /**
     * Returns the mutation format version this handler supports.
     *
     * <p>WA Web has no concrete sync module for this action, so there is no
     * {@code getVersion()} method to inspect. The protobuf shape only defines
     * a single field ({@code chatStartMode} at index 1), so Cobalt declares
     * the initial version {@code 1} as a forward-looking default. The actual
     * value should be re-checked when WA Web ships the matching
     * {@code WAWebUsernameChatStartModeSync} module.
     * @return the constant {@link UsernameChatStartModeAction#ACTION_VERSION},
     *         always {@code 1}
     */
    @Override
    public int version() {
        return UsernameChatStartModeAction.ACTION_VERSION;
    }

    /**
     * Applies a single decoded username chat start mode mutation and returns
     * the detailed result.
     *
     * <p>WA Web has no concrete handler to mirror, so this implementation
     * follows the canonical shape used by every other sibling sync handler
     * (e.g. {@code WAWebLocaleSettingSync.applyMutations}):
     * <ol>
     *   <li><b>Operation filter</b> &mdash; only {@code SET} mutations are
     *       processed; any other operation falls through to
     *       {@link MutationApplicationResult#unsupported()}, mirroring the
     *       trailing {@code p++; return {actionState: Unsupported}} branch in
     *       sibling handlers.</li>
     *   <li><b>Action type and payload check</b> &mdash; the mutation value
     *       must decode to a {@link UsernameChatStartModeAction} with a
     *       non-empty {@code chatStartMode} enum field. Sibling handlers
     *       express the same guard as
     *       {@code var n = e.value, a = n.someAction; if (!a) { i++; return malformedActionValue(this.collectionName) }}.
     *       Cobalt collapses the equivalent
     *       {@link SyncdIndexUtils#malformedActionValue}
     *       contract into {@link MutationApplicationResult#malformed()}.</li>
     *   <li><b>Apply the new setting</b> &mdash; persists the decoded enum
     *       into {@link com.github.auties00.cobalt.store.WhatsAppStore#setUsernameChatStartMode}.
     *       Since WA Web does not yet have an apply path, there is no UI/IPC
     *       call to mirror; only the store side-effect is performed.</li>
     *   <li><b>Success</b> &mdash; returns {@link MutationApplicationResult#success()}.</li>
     * </ol>
     *
     * <p>Sibling WA Web handlers also wrap the per-mutation body in a
     * {@code try/catch} that swallows any exception and returns
     * {@code {actionState: Failed}}. In Cobalt, exceptions are allowed to
     * propagate and the configured {@code WhatsAppClientErrorHandler} decides
     * recovery, per Cobalt's pluggable error model.
     * @param client   the WhatsApp client the mutation is being applied to
     * @param mutation the trusted, decoded mutation to apply
     * @return {@link MutationApplicationResult#unsupported()} for non-{@code SET}
     *         operations; {@link MutationApplicationResult#malformed()} if the
     *         decoded action is not a {@link UsernameChatStartModeAction} or
     *         has no {@code chatStartMode} field;
     *         {@link MutationApplicationResult#success()} otherwise
     */
    @Override
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        // ADAPTED: sibling WAWeb*Sync.applyMutations: if (e.operation === "set") { ... } p++; return {actionState: Unsupported}
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        // ADAPTED: sibling WAWeb*Sync.applyMutations: var n = e.value, a = n.chatStartMode; if (!a) { i++; return malformedActionValue(this.collectionName) }
        if (!(mutation.value().action().orElse(null) instanceof UsernameChatStartModeAction action)
                || action.chatStartMode().isEmpty()) {
            return MutationApplicationResult.malformed();
        }

        // NO_WA_BASIS: WA Web has no apply path; Cobalt persists the decoded enum into the store as the obvious side-effect
        client.store().setUsernameChatStartMode(action.chatStartMode().get());

        // ADAPTED: sibling WAWeb*Sync.applyMutations: return {actionState: Success}
        return MutationApplicationResult.success();
    }
}
