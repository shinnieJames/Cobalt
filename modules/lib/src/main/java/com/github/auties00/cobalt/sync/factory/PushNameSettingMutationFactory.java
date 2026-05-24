package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.setting.PushNameSetting;
import com.github.auties00.cobalt.model.sync.action.setting.PushNameSettingBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing push-name-setting sync mutations.
 *
 * @apiNote
 * Drives the profile-name editor on the Settings profile surface; one
 * call produces a single {@link SyncPendingMutation} that propagates the
 * new pushname to every linked device and is consumed on receiving
 * devices by
 * {@link com.github.auties00.cobalt.sync.handler.PushNameSettingHandler}
 * which fires {@code WAWebSetPushnameLocallyAction.setPushnameLocally}
 * and a {@code WASendPresenceStatusProtocol} presence broadcast.
 *
 * @implNote
 * This implementation mirrors {@code WAWebPushNameSync.getPushnameMutation}
 * and routes the mutation through the {@code CriticalBlock} collection
 * with version {@code 1}, matching the WA Web handler's behaviour where
 * the pushname is part of the critical bootstrap data sync stage.
 */
public final class PushNameSettingMutationFactory {
    /**
     * Constructs a push-name-setting mutation factory.
     *
     * @apiNote
     * Required by the dependency-injection container before the factory
     * is wired into the public pushname setter. The factory keeps no
     * state, so a single instance is sufficient per client.
     */
    public PushNameSettingMutationFactory() {

    }

    /**
     * Builds a pending mutation that broadcasts a new pushname to other
     * linked devices.
     *
     * @apiNote
     * Invoked from the public pushname setter on
     * {@link com.github.auties00.cobalt.client.WhatsAppClient}; receiving
     * devices treat an empty pushname as the bootstrap-stage invalid
     * marker and log
     * {@code BOOTSTRAP_APP_STATE_DATA_STAGE_CODE.PUSHNAME_INVALID} while
     * still applying the change locally. The single
     * {@code WAWebPushNameBridge} caller in WA Web wraps this with a
     * {@code lockForSync} transaction.
     *
     * @implNote
     * This implementation maps directly onto
     * {@code WAWebPushNameSync.getPushnameMutation}; the index carries
     * only {@link PushNameSetting#ACTION_NAME} because the action is a
     * singleton per account and {@code indexArgs} is always empty.
     *
     * @param timestamp the mutation timestamp recorded on both the outer
     *                  mutation and the inner
     *                  {@link com.github.auties00.cobalt.model.sync.SyncActionValue}
     * @param name      the new pushname to broadcast; may be
     *                  {@code null} or empty to clear the pushname (the
     *                  receiver substitutes the empty string and logs
     *                  the bootstrap invalid stage in that case)
     * @return a pending mutation carrying the {@code setting_pushName}
     *         action
     */
    @WhatsAppWebExport(moduleName = "WAWebPushNameSync", exports = "getPushnameMutation", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPendingMutation getPushnameMutation(Instant timestamp, String name) {
        var setting = new PushNameSettingBuilder()
                .name(name)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .pushNameSetting(setting)
                .build();
        var index = JSON.toJSONString(List.of(PushNameSetting.ACTION_NAME));
        var pending = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                PushNameSetting.ACTION_VERSION
        );
        return new SyncPendingMutation(pending, 0);
    }
}
