package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.device.StatusPostOptInNotificationPreferencesAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Mirrors the user's opt-in choice for status-post notifications across linked
 * devices.
 *
 * <p>The sync dispatcher would route incoming
 * {@code "status_post_opt_in_notification_preferences_action"} mutations here if
 * the server ever emits one. The handler persists the boolean opt-in flag on
 * {@link com.github.auties00.cobalt.store.WhatsAppStore} so notification delivery
 * for status posts respects the user's last choice.
 *
 * @implNote
 * This implementation is forward-looking. The
 * {@code SyncActionValue.StatusPostOptInNotificationPreferencesAction} protobuf
 * is declared in {@code WAWebProtobufSyncAction.pb} (field 71, collection
 * {@code REGULAR_HIGH}, single optional {@code enabled: bool} at index 1), but
 * the current WA Web snapshot does not ship a
 * {@code WAWebStatusPostOptInNotificationPreferencesSync} module and the action
 * is absent from {@code WAWebCollectionHandlerActions.ActionHandlers}, so WA
 * Web's dispatcher would currently skip any incoming mutation.
 */
public final class StatusPostOptInNotificationPreferencesHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance held by the sync registry.
     */
    public static final StatusPostOptInNotificationPreferencesHandler INSTANCE =
            new StatusPostOptInNotificationPreferencesHandler();

    /**
     * Constructs the singleton.
     *
     * <p>Callers obtain the handler via {@link #INSTANCE}; the constructor is
     * private so the registry cannot accidentally instantiate it twice.
     */
    private StatusPostOptInNotificationPreferencesHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String actionName() {
        return StatusPostOptInNotificationPreferencesAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation returns {@link SyncPatchType#REGULAR_HIGH} as inferred
     * from {@code WAWebProtobufSyncAction.pb}'s
     * {@code STATUS_POST_OPT_IN_NOTIFICATION_PREFERENCES_ACTION -> REGULAR_HIGH}
     * collection-router branch.
     */
    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR_HIGH;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int version() {
        return StatusPostOptInNotificationPreferencesAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * <p>A non-{@link SyncdOperation#SET} operation is reported as
     * {@link MutationApplicationResult#unsupported()}, a value that does not decode
     * to a {@link StatusPostOptInNotificationPreferencesAction} is reported as
     * malformed, and the {@code enabled} field is otherwise written to
     * {@link com.github.auties00.cobalt.store.WhatsAppStore#setStatusPostOptInNotificationPreferencesEnabled(Boolean)}.
     *
     * @implNote
     * This implementation follows the canonical single-boolean-payload shape used
     * by sibling handlers because WA Web ships no concrete sync module for this
     * action; the shape is inferred from the protobuf schema and a sibling handler.
     */
    @Override
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof StatusPostOptInNotificationPreferencesAction action)) {
            return MutationApplicationResult.malformed();
        }

        client.store().setStatusPostOptInNotificationPreferencesEnabled(action.enabled());
        return MutationApplicationResult.success();
    }
}
