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
 * <p>The factory is the outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.UnarchiveChatsSettingHandler}.
 */
public final class UnarchiveChatsSettingMutationFactory {
    /**
     * Constructs an unarchive-chats-setting mutation factory.
     */
    public UnarchiveChatsSettingMutationFactory() {

    }

    /**
     * Builds a pending {@code setting_unarchiveChats} mutation that broadcasts
     * the given auto-unarchive preference to every linked device.
     *
     * <p>WA Web exposes an outgoing path via {@code WAWebArchiveSettingBridge}
     * that goes through {@code WAWebSyncdActionUtils.buildPendingMutation};
     * Cobalt surfaces a typed helper so the public
     * {@code WhatsAppClient.editUnarchiveChatsOnNewMessage} setter can build a
     * single mutation without hand-rolling the protobuf wrapping.
     *
     * @param timestamp       the mutation timestamp
     * @param unarchiveChats  {@code true} to enable auto-unarchive on new
     *                        message, {@code false} otherwise
     * @return a pending mutation carrying the {@code setting_unarchiveChats} action
     * @throws NullPointerException if {@code timestamp} is {@code null}
     */
    public SyncPendingMutation getUnarchiveChatsMutation(Instant timestamp, boolean unarchiveChats) {
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        var setting = new UnarchiveChatsSettingBuilder() // ADAPTED: WAWebSyncdActionUtils.buildPendingMutation value shape: {unarchiveChatsSetting: {unarchiveChats: f}}
                .unarchiveChats(unarchiveChats)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .unarchiveChatsSetting(setting)
                .build();
        var index = JSON.toJSONString(List.of(UnarchiveChatsSetting.ACTION_NAME)); // ADAPTED: WAWebSyncdActionUtils.buildPendingMutation: index = JSON.stringify([action])
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
