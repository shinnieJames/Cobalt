package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.media.StatusPrivacyAction;
import com.github.auties00.cobalt.model.sync.action.media.StatusPrivacyActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing status-privacy sync mutations.
 *
 * <p>Mirrors the {@code getStatusPrivacySettingMutation} export of WhatsApp
 * Web's {@code WAWebStatusPrivacySettingSync} module. The factory is the
 * outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.StatusPrivacyHandler}.
 */
public final class StatusPrivacyMutationFactory {
    /**
     * Constructs a status-privacy mutation factory.
     */
    public StatusPrivacyMutationFactory() {

    }

    /**
     * Builds a pending SET mutation for the status privacy setting.
     *
     * <p>Per WhatsApp Web {@code WAWebStatusPrivacySettingSync.getStatusPrivacySettingMutation}:
     * <ol>
     *   <li>Maps the {@code StatusPrivacySettingType} input to the matching
     *       {@code StatusDistributionMode} enum value
     *       ({@code Contact -> CONTACTS}, {@code AllowList -> ALLOW_LIST},
     *       {@code DenyList -> DENY_LIST}).</li>
     *   <li>Wraps the result in a {@code statusPrivacy} sub-message containing
     *       {@code mode}, {@code userJid}, optionally {@code shareToFB} /
     *       {@code shareToIG} (only when
     *       {@code crosspostSettingsSyncSenderEnabled} is true), and an empty
     *       {@code customLists} list.</li>
     *   <li>Delegates to {@code WAWebSyncdActionUtils.buildPendingMutation}
     *       with collection {@code RegularHigh}, empty index args, operation
     *       {@code SET}, version {@code 7}, and action
     *       {@code "status_privacy"}.</li>
     * </ol>
     *
     * <p>The {@code shareToFB} / {@code shareToIG} fields are gated by
     * {@code crosspostSettingsSyncSenderEnabled} in WA Web; Cobalt has no
     * equivalent AB-prop gating and no FB/IG persistence layer, so they are
     * left unset. The {@code customLists} field is always passed as an empty
     * list to mirror WA Web's unconditional {@code customLists: []} override.
     *
     * @param timestamp the mutation timestamp
     * @param mode      the target distribution mode
     * @param userJids  the JIDs to associate with the mode (whitelist for
     *                  {@code ALLOW_LIST}, blacklist for {@code DENY_LIST}, may
     *                  be empty for {@code CONTACTS} or {@code CLOSE_FRIENDS})
     * @return the pending mutation ready for sync upload
     */
    @WhatsAppWebExport(moduleName = "WAWebStatusPrivacySettingSync", exports = "getStatusPrivacySettingMutation", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getStatusPrivacyMutation(Instant timestamp, StatusPrivacyAction.StatusDistributionMode mode, List<Jid> userJids) {
        var statusPrivacy = new StatusPrivacyActionBuilder()
                .mode(mode)
                .userJid(userJids == null ? List.of() : userJids)
                .customLists(List.of())
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .statusPrivacy(statusPrivacy)
                .build();
        var index = JSON.toJSONString(List.of(StatusPrivacyAction.ACTION_NAME));
        var trusted = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                StatusPrivacyAction.ACTION_VERSION
        );
        return new SyncPendingMutation(trusted, 0);
    }
}
