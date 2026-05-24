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
 * @apiNote
 * Drives the "always relay calls" privacy toggle on the Settings privacy
 * calls surface; relayed calls go through WhatsApp infrastructure rather
 * than peer-to-peer, masking the caller's network identifiers from the
 * callee. Mutations produced here are consumed on receiving devices by
 * {@link com.github.auties00.cobalt.sync.handler.VoipRelayAllCallsHandler}
 * which forwards the boolean to
 * {@code WAWebBackendApi.frontendSendAndReceive("setRelayAllCallsToUserPrefs",
 * {disallowAllP2p})}.
 *
 * @implNote
 * This implementation mirrors
 * {@code WAWebVoipRelayAllCallsSettingSync.getMutation}; the wrapping
 * {@code sendMutation} that WA Web exposes goes through
 * {@code lockForSync} which Cobalt does not run at this layer.
 */
public final class VoipRelayAllCallsMutationFactory {
    /**
     * Constructs a VoIP relay-all-calls mutation factory.
     *
     * @apiNote
     * Required by the dependency-injection container before the factory
     * is wired into the public relay-all-calls setter. The factory keeps
     * no state, so a single instance is sufficient per client.
     */
    public VoipRelayAllCallsMutationFactory() {

    }

    /**
     * Builds a pending SET mutation for the VoIP relay-all-calls setting.
     *
     * @apiNote
     * Invoked from the public relay-all-calls setter. The index carries
     * only the action name because the preference is a singleton per
     * account; receiving devices interpret the {@code isEnabled} flag as
     * "disallow all peer-to-peer call routing" and route subsequent calls
     * exclusively through WhatsApp relays.
     *
     * @implNote
     * This implementation models the
     * {@code SyncActionValue.privacySettingRelayAllCalls} protobuf shape
     * as used by {@code WAWebSyncdActionUtils.buildPendingMutation}; the
     * mutation is routed through the {@code Regular} collection with
     * version {@code 1}.
     *
     * @param timestamp the mutation timestamp recorded on both the outer
     *                  mutation and the inner {@code SyncActionValue}
     * @param isEnabled {@code true} to disallow peer-to-peer call
     *                  routing (force relay), {@code false} to allow
     *                  peer-to-peer routing
     * @return the pending mutation ready for sync upload
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipRelayAllCallsSettingSync", exports = "getMutation", adaptation = WhatsAppAdaptation.DIRECT)
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
