package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.privacy.PrivacySettingRelayAllCalls;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Mirrors the "Always relay calls" privacy setting across linked devices.
 *
 * @apiNote
 * Cobalt embedders never invoke this handler directly; the sync dispatcher
 * routes incoming {@code setting_relayAllCalls} mutations here whenever
 * the user toggles "Always relay calls" on another linked device (typical
 * trigger: Privacy Settings -> Advanced -> Protect IP Address in Calls).
 * The handler writes the boolean preference onto
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#setRelayAllCalls(boolean)}
 * so Cobalt's VoIP layer routes subsequent calls through the WA relay
 * instead of letting the peer learn the local IP address.
 */
@WhatsAppWebModule(moduleName = "WAWebVoipRelayAllCallsSettingSync")
public final class VoipRelayAllCallsHandler implements WebAppStateActionHandler {

    /**
     * Constructs the handler.
     *
     * @apiNote
     * The handler is stateless; Cobalt's sync registry holds a single
     * instance per client.
     */
    @WhatsAppWebExport(moduleName = "WAWebVoipRelayAllCallsSettingSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public VoipRelayAllCallsHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebVoipRelayAllCallsSettingSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return PrivacySettingRelayAllCalls.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebVoipRelayAllCallsSettingSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return PrivacySettingRelayAllCalls.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebVoipRelayAllCallsSettingSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return PrivacySettingRelayAllCalls.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation mirrors WA Web's per-mutation closure inside
     * {@code WAWebVoipRelayAllCallsSettingSync.applyMutations}: it
     * requires a {@link SyncdOperation#SET}, decodes the
     * {@link PrivacySettingRelayAllCalls} value, and writes the boolean
     * into
     * {@link com.github.auties00.cobalt.store.WhatsAppStore#setRelayAllCalls(boolean)}
     * in place of WA Web's
     * {@code WAWebBackendApi.frontendSendAndReceive("setRelayAllCallsToUserPrefs", {disallowAllP2p: s})}
     * shell hop. Cobalt's
     * {@link PrivacySettingRelayAllCalls#isEnabled()} accessor coalesces
     * a null Boolean to {@code false} per the project nullable-boolean
     * convention, so the WA Web {@code s == null} short-circuit (which
     * skips the persist call but still reports {@code Success}) is
     * relaxed to a direct write of {@code false}. The trailing
     * {@code WALogger.WARN} counters and the outer {@code try/catch -> Failed}
     * wrapper are dropped per Cobalt's pluggable error model: thrown
     * exceptions surface to the configured
     * {@link com.github.auties00.cobalt.exception.WhatsAppClientErrorHandler}.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebVoipRelayAllCallsSettingSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof PrivacySettingRelayAllCalls action)) {
            return MutationApplicationResult.malformed();
        }

        client.store().setRelayAllCalls(action.isEnabled());
        return MutationApplicationResult.success();
    }

}
