package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.bot.UGCBotAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
/**
 * Handles UGC (user-generated-content) bot sync actions.
 *
 * <p>This handler processes incoming mutations carrying a
 * {@code SyncActionValue.UGCBot} payload. The action is identified by the
 * {@code "ugc_bot"} action name and stored in the {@code REGULAR_HIGH} sync
 * collection.
 *
 * <p>{@code SyncActionValue$UGCBot} is declared in
 * {@code WAWebProtobufSyncAction.pb} as field {@code 43} (action name
 * {@code "ugc_bot"}) and routed to {@code REGULAR_HIGH} by the
 * {@code getCollectionForAction} switch in the same module. The protobuf
 * schema is {@code {definition: [1, BYTES]}}.
 *
 * <p>Despite being declared in the protobuf and assigned to a sync collection,
 * WhatsApp Web does <strong>not</strong> ship a dedicated {@code WAWeb*Sync}
 * module that consumes {@code ugc_bot} mutations: there is no
 * {@code WAWebUGCBotSync} (or similar) module exporting a singleton handler
 * with {@code getAction()/getVersion()/applyMutations()}. Searches across the
 * full WA Web bundle confirm that the only producers/consumers of the
 * {@code UGC_BOT} symbol are the protobuf module itself, deeplink parsing
 * ({@code WAWebApi}, {@code WAWebApiParse}, {@code WAWebExecApiCmd}) and the
 * unrelated bot-profile collection ({@code WAWebBotProfileCollection}).
 *
 * <p>This handler is therefore a forward-looking placeholder so that Cobalt's
 * sync action dispatcher can ingest {@code ugc_bot} payloads without crashing
 * if the server starts emitting them. The implementation simply persists the
 * raw {@code definition} bytes into {@link com.github.auties00.cobalt.store.WhatsAppStore}
 * via {@code setUserCreatedBotDefinition} so that downstream code can pick them up
 * once WhatsApp Web ships a real handler.
 */
public final class UGCBotHandler implements WebAppStateActionHandler {

    /**
     * Private constructor to enforce singleton pattern.
     */
    public UGCBotHandler() {

    }

    /**
     * Returns the action name for UGC bot actions.
     *
     * <p>Per {@code WAWebProtobufSyncAction.pb}, action index {@code 43}
     * ({@code UGC_BOT}) maps to the action name {@code "ugc_bot"} via the
     * {@code getMutationProps} switch.
     * @return the action name {@code "ugc_bot"}
     */
    @Override
    public String actionName() {
        return UGCBotAction.ACTION_NAME;
    }

    /**
     * Returns the sync collection for UGC bot actions.
     *
     * <p>Per {@code WAWebProtobufSyncAction.pb}'s {@code getCollectionForAction}
     * switch, the {@code UGC_BOT} action belongs to the {@code REGULAR_HIGH}
     * collection: {@code e===c.UGC_BOT||e===c.STATUS_PRIVACY?u.REGULAR_HIGH}.
     * @return {@link SyncPatchType#REGULAR_HIGH}
     */
    @Override
    public SyncPatchType collectionName() {
        return UGCBotAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for UGC bot actions.
     *
     * <p>WhatsApp Web does not ship a {@code WAWebUGCBotSync} module, so no
     * {@code getVersion()} value is published for this action. Cobalt defaults
     * to {@code 1} (the lowest valid mutation version) so that incoming
     * mutations are not version-gated out before reaching this placeholder
     * handler.
     * @return the version number {@code 1}
     */
    @Override
    public int version() {
        return UGCBotAction.ACTION_VERSION;
    }

    /**
     * Applies a UGC bot mutation and returns a detailed result.
     *
     * <p>Because WhatsApp Web does not ship a real handler for this action,
     * Cobalt implements a minimal placeholder that mirrors the conventions of
     * sibling handlers:
     * <ol>
     *   <li>{@code REMOVE} (and any non-{@code SET}) operations return
     *       {@link MutationApplicationResult#unsupported()}.</li>
     *   <li>The mutation value must contain a {@link UGCBotAction} with a
     *       non-empty {@code definition} byte string; otherwise the result is
     *       {@link MutationApplicationResult#malformed()}.</li>
     *   <li>The raw {@code definition} bytes are stored on the
     *       {@link com.github.auties00.cobalt.store.WhatsAppStore} via
     *       {@code setUserCreatedBotDefinition} so they can be picked up by future
     *       code paths.</li>
     * </ol>
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof UGCBotAction action)
                || action.definition().isEmpty()) {
            return MutationApplicationResult.malformed();
        }

        client.store().setUserCreatedBotDefinition(action.definition().get());
        return MutationApplicationResult.success();
    }
}
