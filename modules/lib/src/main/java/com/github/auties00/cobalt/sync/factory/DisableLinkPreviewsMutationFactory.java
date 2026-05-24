package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.privacy.PrivacySettingDisableLinkPreviewsAction;
import com.github.auties00.cobalt.model.sync.action.privacy.PrivacySettingDisableLinkPreviewsActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing app-state mutations that toggle the global "disable link previews" privacy setting.
 *
 * @apiNote
 * Drives the privacy-pane "disable link previews" switch:
 * {@code WAWebDisableLinkPreviewsSync.sendMutation} pushes the result of
 * this factory through {@code WAWebSyncdCoreApi.lockForSync} so every
 * linked device updates
 * {@code WAWebDisableLinkPreviewsAction.setDisableLinkPreviewsToUserPrefs}.
 * The factory is the outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.DisableLinkPreviewsHandler}.
 */
public final class DisableLinkPreviewsMutationFactory {
    /**
     * Creates an instance with no collaborators.
     *
     * @apiNote
     * The factory is stateless; a single instance may be shared across the
     * lifetime of the client.
     */
    public DisableLinkPreviewsMutationFactory() {

    }

    /**
     * Returns a SET mutation that toggles the global link-preview privacy setting.
     *
     * @apiNote
     * The mutation index follows
     * {@snippet :
     *     ["setting_disableLinkPreviews"]
     * }
     * with no per-row segment because the setting is account-wide; the
     * {@link PrivacySettingDisableLinkPreviewsAction} sub-message carries
     * the {@code isPreviewsDisabled} flag.
     *
     * @implNote
     * This implementation takes the timestamp from the caller, mirroring the
     * {@code WAWebDisableLinkPreviewsSync.getMutation(timestamp, value)}
     * shape; the sister WA Web export {@code sendMutation} captures
     * {@code WATimeUtils.unixTimeMs()} inline and then calls
     * {@code getMutation} the same way.
     *
     * @param timestamp          the mutation timestamp
     * @param isPreviewsDisabled {@code true} to disable link previews account-wide, {@code false} to re-enable them
     * @return the pending mutation ready to be queued for outbound app-state sync
     */
    @WhatsAppWebExport(moduleName = "WAWebDisableLinkPreviewsSync", exports = "getMutation", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getDisableLinkPreviewsMutation(Instant timestamp, boolean isPreviewsDisabled) {
        var action = new PrivacySettingDisableLinkPreviewsActionBuilder()
                .isPreviewsDisabled(isPreviewsDisabled)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .privacySettingDisableLinkPreviewsAction(action)
                .build();
        var index = JSON.toJSONString(List.of(PrivacySettingDisableLinkPreviewsAction.ACTION_NAME));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                PrivacySettingDisableLinkPreviewsAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0);
    }
}
