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
 * <p>The factory is the outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.PushNameSettingHandler}.
 */
public final class PushNameSettingMutationFactory {
    /**
     * Constructs a push-name-setting mutation factory.
     */
    public PushNameSettingMutationFactory() {

    }

    /**
     * Builds a pending mutation that broadcasts a new pushname to other linked
     * devices.
     *
     * <p>Per WhatsApp Web {@code WAWebPushNameSync.getPushnameMutation}: wraps
     * the supplied name into a {@code SyncActionValue.pushNameSetting} payload
     * and forwards it to {@code WAWebSyncdActionUtils.buildPendingMutation}
     * with empty {@code indexArgs}, the handler's collection
     * ({@code CriticalBlock}), version ({@code 1}), action
     * ({@code "setting_pushName"}) and {@code SyncdMutation$SyncdOperation.SET}.
     *
     * <p>The resulting mutation is queued via
     * {@code WAWebSyncdDb.appendPendingMutationsRows} by the WA Web caller and
     * picked up on the next sync cycle. In Cobalt the returned
     * {@link SyncPendingMutation} is appended to the per-collection pending
     * queue by the same upper-layer call sites that handle the other
     * {@code get*Mutation} builders.
     *
     * @param timestamp the mutation timestamp ({@code SyncActionValue.timestamp})
     * @param name      the new pushname to broadcast; may be {@code null} or
     *                  empty to clear the pushname
     * @return a pending mutation carrying the {@code setting_pushName} action
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
