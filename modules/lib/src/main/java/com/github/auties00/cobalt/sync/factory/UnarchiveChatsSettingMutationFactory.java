package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.setting.UnarchiveChatsSetting;
import com.github.auties00.cobalt.model.sync.action.setting.UnarchiveChatsSettingBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Builds outgoing unarchive-chats-setting sync mutations.
 *
 * @apiNote
 * Drives the "keep chats archived" toggle on the Settings chats surface;
 * one call produces a single {@link SyncPendingMutation} that propagates
 * the chosen auto-unarchive behaviour to every linked device and is
 * consumed on receiving devices by
 * {@link com.github.auties00.cobalt.sync.handler.UnarchiveChatsSettingHandler}.
 *
 * @implNote
 * This implementation has no direct WA Web counterpart: the
 * {@code setting_unarchiveChats} action is declared only as a protobuf
 * shape in {@code WAWebProtobufSyncAction.pb} and a user-prefs key in
 * {@code WAWebUserPrefsKeys}; outgoing changes on WA Web are wrapped via
 * the generic {@code WAWebSyncdActionUtils.buildPendingMutation}
 * pathway shared by every {@code AccountSyncdActionBase} subclass.
 */
public final class UnarchiveChatsSettingMutationFactory {
    /**
     * Constructs an unarchive-chats-setting mutation factory.
     *
     * @apiNote
     * Required by the dependency-injection container before the factory
     * is wired into the public auto-unarchive setter. The factory keeps
     * no state, so a single instance is sufficient per client.
     */
    public UnarchiveChatsSettingMutationFactory() {

    }

    /**
     * Builds a pending {@code setting_unarchiveChats} mutation that
     * broadcasts the given auto-unarchive preference to every linked
     * device.
     *
     * @apiNote
     * Invoked from the public auto-unarchive setter. When
     * {@code unarchiveChats} is {@code true} every new incoming message
     * to an archived chat pops that chat back out of the archive folder
     * on every linked device; when {@code false} archived chats remain
     * archived regardless of new messages.
     *
     * @implNote
     * This implementation models the
     * {@code SyncActionValue.unarchiveChatsSetting} protobuf shape as
     * used by {@code WAWebSyncdActionUtils.buildPendingMutation}; the
     * index carries only the action name because the preference is a
     * singleton per account.
     *
     * @param timestamp      the mutation timestamp recorded on both the
     *                       outer mutation and the inner
     *                       {@code SyncActionValue}
     * @param unarchiveChats {@code true} to enable auto-unarchive on new
     *                       message, {@code false} otherwise
     * @return a pending mutation carrying the
     *         {@code setting_unarchiveChats} action
     * @throws NullPointerException if {@code timestamp} is {@code null}
     */
    public SyncPendingMutation getUnarchiveChatsMutation(Instant timestamp, boolean unarchiveChats) {
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        var setting = new UnarchiveChatsSettingBuilder()
                .unarchiveChats(unarchiveChats)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .unarchiveChatsSetting(setting)
                .build();
        var index = JSON.toJSONString(List.of(UnarchiveChatsSetting.ACTION_NAME));
        var pending = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                UnarchiveChatsSetting.ACTION_VERSION
        );
        return new SyncPendingMutation(pending, 0);
    }
}
