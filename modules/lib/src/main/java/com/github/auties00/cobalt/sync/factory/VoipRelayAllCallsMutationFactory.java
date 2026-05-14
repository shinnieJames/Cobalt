package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.privacy.PrivacySettingRelayAllCalls;
import com.github.auties00.cobalt.model.sync.action.privacy.PrivacySettingRelayAllCallsBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing VoIP relay-all-calls sync mutations.
 *
 * <p>The factory is the outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.VoipRelayAllCallsHandler}.
 */
public final class VoipRelayAllCallsMutationFactory {
    /**
     * Constructs a VoIP relay-all-calls mutation factory.
     */
    public VoipRelayAllCallsMutationFactory() {

    }

    /**
     * Builds a pending SET mutation for the VoIP relay-all-calls setting.
     *
     * <p>Per WhatsApp Web {@code WAWebVoipRelayAllCallsSettingSync.getMutation}:
     * <ol>
     *   <li>Wraps the value in a {@code privacySettingRelayAllCalls} object:
     *       {@code {isEnabled: n}}</li>
     *   <li>Delegates to {@code WAWebSyncdActionUtils.buildPendingMutation} with
     *       collection={@code Regular}, indexArgs={@code []},
     *       operation={@code SET}, version={@code 1},
     *       action={@code "setting_relayAllCalls"}</li>
     * </ol>
     *
     * @param timestamp the mutation timestamp
     * @param isEnabled whether VoIP relay-all-calls should be enabled
     * @return the pending mutation ready for sync upload
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipRelayAllCallsSettingSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPendingMutation getVoipRelayAllCallsMutation(Instant timestamp, boolean isEnabled) {
        var action = new PrivacySettingRelayAllCallsBuilder()
                .isEnabled(isEnabled)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .privacySettingRelayAllCalls(action)
                .build();
        var index = JSON.toJSONString(List.of(PrivacySettingRelayAllCalls.ACTION_NAME));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                PrivacySettingRelayAllCalls.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0);
    }
}
