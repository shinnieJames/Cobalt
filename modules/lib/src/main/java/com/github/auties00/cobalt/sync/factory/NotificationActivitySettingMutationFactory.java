package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.setting.NotificationActivitySettingAction;
import com.github.auties00.cobalt.model.sync.action.setting.NotificationActivitySettingActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Builds outgoing notification-activity-setting sync mutations.
 *
 * <p>The factory is the outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.NotificationActivitySettingHandler}.
 */
public final class NotificationActivitySettingMutationFactory {
    /**
     * Constructs a notification-activity-setting mutation factory.
     */
    public NotificationActivitySettingMutationFactory() {

    }

    /**
     * Builds a pending {@code notificationActivitySetting} mutation carrying
     * the given notification activity preference.
     *
     * <p>NO_WA_BASIS: WA Web has no outgoing helper for this action; the shape
     * follows {@code WAWebSyncdActionUtils.buildPendingMutation} as used by
     * every other sibling {@code AccountSyncdActionBase} subclass. Cobalt
     * surfaces the helper so the public
     * {@code WhatsAppClient.editNotificationActivity} setter can build a single
     * mutation without hand-rolling the protobuf wrapping.
     *
     * @param timestamp the mutation timestamp
     * @param setting   the new {@link NotificationActivitySettingAction.NotificationActivitySetting}
     * @return a pending mutation carrying the {@code notificationActivitySetting}
     *         action
     * @throws NullPointerException if {@code timestamp} or {@code setting} is {@code null}
     */
    public SyncPendingMutation getNotificationActivityMutation(Instant timestamp, NotificationActivitySettingAction.NotificationActivitySetting setting) {
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(setting, "setting cannot be null");
        var action = new NotificationActivitySettingActionBuilder()
                .notificationActivitySetting(setting)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .notificationActivitySettingAction(action)
                .build();
        var index = JSON.toJSONString(List.of(NotificationActivitySettingAction.ACTION_NAME));
        var pending = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                NotificationActivitySettingAction.ACTION_VERSION
        );
        return new SyncPendingMutation(pending, 0);
    }
}
