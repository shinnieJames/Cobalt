package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.setting.DetectedOutcomesStatusAction;
import com.github.auties00.cobalt.model.sync.action.setting.DetectedOutcomesStatusActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing app-state mutations that toggle whether automated
 * message and contact detections (spam, flagged-account warnings) are
 * mirrored across linked devices.
 *
 * @apiNote
 * Drives the Click-To-WhatsApp detected-outcomes onboarding switch
 * exposed on the Web settings surface; consumed on receiving devices
 * by the detected-outcomes sync handler which forwards the boolean to
 * {@code WAWebUserPrefsDetectedOutcomes} so subsequent classifications
 * surface (or stop surfacing) on every companion.
 *
 * @implNote
 * This implementation mirrors
 * {@code WAWebCTWADetectedOutcomeOnboardingStatusUpdateAction}, the
 * dispatcher WA Web invokes from
 * {@code WAWebCTWABridgeApi.ctwaDetectedOutcomeOnboardingStatusUpdate}.
 */
public final class DetectedOutcomesStatusMutationFactory {
    /**
     * Constructs a detected-outcomes-status mutation factory.
     *
     * @apiNote
     * The factory is stateless; a single instance may be shared across
     * the lifetime of the client.
     */
    public DetectedOutcomesStatusMutationFactory() {

    }

    /**
     * Builds a pending SET mutation that toggles the detected-outcomes
     * status flag.
     *
     * @apiNote
     * Invoked from the public automated-detections setter on
     * {@link com.github.auties00.cobalt.client.WhatsAppClient}; the
     * index carries only the action name because the preference is a
     * singleton per account.
     *
     * @implNote
     * This implementation captures the timestamp via
     * {@link Instant#now()}; WA Web's
     * {@code WAWebCTWADetectedOutcomeOnboardingStatusUpdateAction}
     * uses {@code WATimeUtils.unixTimeMs()} for the same purpose.
     *
     * @param enabled {@code true} to enable cross-device sync of
     *                automated detections, {@code false} to disable it
     * @return the pending mutation ready to be queued for outbound
     *         app-state sync
     */
    @WhatsAppWebExport(moduleName = "WAWebCTWADetectedOutcomeOnboardingStatusUpdateAction", exports = "ctwaDetectedOutcomeOnboardingStatusUpdateAction", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getDetectedOutcomesStatusMutation(boolean enabled) {
        var timestamp = Instant.now();
        var action = new DetectedOutcomesStatusActionBuilder()
                .isEnabled(enabled)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .detectedOutcomesStatusAction(action)
                .build();
        var index = JSON.toJSONString(List.of(DetectedOutcomesStatusAction.ACTION_NAME));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                DetectedOutcomesStatusAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0);
    }
}
