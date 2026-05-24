package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.setting.NotificationActivitySettingAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Applies the {@code notificationActivitySetting} app-state action that
 * carries the user's per-account notification activity preference.
 *
 * @apiNote
 * Persists one of
 * {@link NotificationActivitySettingAction.NotificationActivitySetting}'s
 * four enum values
 * ({@code DEFAULT_ALL_MESSAGES}, {@code ALL_MESSAGES},
 * {@code HIGHLIGHTS}, {@code DEFAULT_HIGHLIGHTS}) on
 * {@link com.github.auties00.cobalt.store.WhatsAppStore} so the
 * notification activity preference fans out across linked devices.
 * Only {@link SyncdOperation#SET} is accepted; any other operation is
 * reported as {@link MutationApplicationResult#unsupported()} and a
 * missing or wrong-typed value as
 * {@link MutationApplicationResult#malformed()}.
 *
 * @implNote
 * This implementation has no WA Web counterpart: the
 * {@code SyncActionValue.NotificationActivitySettingAction} protobuf
 * (action index 60, action name {@code "notificationActivitySetting"})
 * is declared in {@code WAWebProtobufSyncAction.pb} but no
 * {@code WAWebNotificationActivitySettingSync} module ships, and the
 * action is absent from
 * {@code WAWebCollectionHandlerActions.ActionHandlers}, so WA Web
 * silently drops any incoming mutation. The handler exists in Cobalt
 * as a forward-looking implementation. The {@link SyncPatchType#REGULAR}
 * collection is taken directly from the inline router in
 * {@code WAWebProtobufSyncAction.pb}; the version and apply path are
 * inferred from sibling settings handlers.
 */
public final class NotificationActivitySettingHandler implements WebAppStateActionHandler {

    /**
     * Constructs the notification activity setting sync handler.
     *
     * @apiNote
     * Used by the sync handler registry; consumers should never need to
     * call this constructor directly.
     *
     * @implNote
     * This implementation is stateless; the handler holds no
     * AB-prop / store / WAM dependency.
     */
    public NotificationActivitySettingHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String actionName() {
        return NotificationActivitySettingAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation returns {@link SyncPatchType#REGULAR} as
     * declared by the inline router in
     * {@code WAWebProtobufSyncAction.pb}
     * ({@code e === c.NOTIFICATION_ACTIVITY_SETTING_ACTION ? u.REGULAR}).
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
     * {@link NotificationActivitySettingAction#ACTION_VERSION}
     * (currently {@code 1}); WA Web has no concrete handler so there
     * is no {@code getVersion()} to mirror, and {@code 1} is the
     * forward-looking default for a single-field protobuf.
     */
    @Override
    public int version() {
        return NotificationActivitySettingAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation accepts only {@link SyncdOperation#SET}: the
     * action carries a single mandatory enum and there is no semantic
     * for {@code REMOVE}. A missing or empty
     * {@link NotificationActivitySettingAction#notificationActivitySetting()}
     * is rejected as {@link MutationApplicationResult#malformed()}; on
     * success the resolved enum is written via
     * {@code WhatsAppStore.setNotificationActivitySetting}. WA Web
     * sibling handlers wrap the body in a try/catch returning
     * {@code Failed}; Cobalt lets exceptions propagate so the
     * configured {@code WhatsAppClientErrorHandler} decides recovery.
     */
    @Override
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof NotificationActivitySettingAction action)
                || action.notificationActivitySetting().isEmpty()) {
            return MutationApplicationResult.malformed();
        }

        client.store().setNotificationActivitySetting(action.notificationActivitySetting().get());

        return MutationApplicationResult.success();
    }

}
