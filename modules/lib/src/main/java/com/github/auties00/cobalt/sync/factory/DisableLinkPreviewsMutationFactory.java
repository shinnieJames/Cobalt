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
 * Builds outgoing disable-link-previews sync mutations.
 *
 * <p>Mirrors the {@code getMutation} export of WhatsApp Web's
 * {@code WAWebDisableLinkPreviewsSync} module. The factory is the
 * outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.DisableLinkPreviewsHandler}.
 */
public final class DisableLinkPreviewsMutationFactory {
    /**
     * Constructs a disable-link-previews mutation factory.
     */
    public DisableLinkPreviewsMutationFactory() {

    }

    /**
     * Builds a pending SET mutation for the disable link previews setting.
     *
     * <p>Per WhatsApp Web {@code WAWebDisableLinkPreviewsSync.getMutation}:
     * <ol>
     *   <li>Wraps the value in a {@code privacySettingDisableLinkPreviewsAction}
     *       object: {@code {isPreviewsDisabled: n}}</li>
     *   <li>Delegates to {@code WAWebSyncdActionUtils.buildPendingMutation} with
     *       collection={@code Regular}, indexArgs={@code []},
     *       operation={@code SET}, version={@code 8},
     *       action={@code "setting_disableLinkPreviews"}</li>
     * </ol>
     *
     * @param timestamp          the mutation timestamp
     * @param isPreviewsDisabled whether link previews should be disabled
     * @return the pending mutation ready for sync upload
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
