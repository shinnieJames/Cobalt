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
 * @apiNote
 * Drives the Status privacy picker on the Settings privacy surface;
 * supports the My-Contacts, allow-list, deny-list, close-friends, and
 * custom-list modes that WA Web's
 * {@code WAWebStatusSetAndSyncPrivacy} flow funnels through this
 * builder. Mutations are consumed on receiving devices by
 * {@link com.github.auties00.cobalt.sync.handler.StatusPrivacyHandler}
 * which writes the relevant IDB rows and fires a
 * {@code BackendEventBus.triggerUpdateStatusPrivacySettings} event.
 *
 * @implNote
 * This implementation mirrors
 * {@code WAWebStatusPrivacySettingSync.getStatusPrivacySettingMutation}
 * but takes the {@link StatusPrivacyAction.StatusDistributionMode}
 * directly rather than the WA Web {@code StatusPrivacySettingType} that
 * WA Web maps onto the protobuf enum inside the builder.
 */
public final class StatusPrivacyMutationFactory {
    /**
     * Constructs a status-privacy mutation factory.
     *
     * @apiNote
     * Required by the dependency-injection container before the factory
     * is wired into the public status-privacy setter. The factory keeps
     * no state, so a single instance is sufficient per client.
     */
    public StatusPrivacyMutationFactory() {

    }

    /**
     * Builds a pending SET mutation for the status privacy setting.
     *
     * @apiNote
     * Invoked from the public status-privacy setter on
     * {@link com.github.auties00.cobalt.client.WhatsAppClient}. The
     * {@code userJids} argument is the whitelist when
     * {@code mode == ALLOW_LIST}, the blacklist when
     * {@code mode == DENY_LIST}, and may be empty (or {@code null}, in
     * which case it is treated as empty) for the {@code CONTACTS},
     * {@code CLOSE_FRIENDS}, and {@code CUSTOM_LIST} modes. Receiving
     * devices route each non-user JID out of the resolved set before
     * persistence.
     *
     * @implNote
     * This implementation omits the {@code shareToFB}/{@code shareToIG}
     * fields because Cobalt has no FB/IG cross-post persistence layer
     * and no equivalent of WA Web's
     * {@code crosspostSettingsSyncSenderEnabled} AB-prop gate; the
     * {@code customLists} field is always emitted as an empty list to
     * mirror WA Web's unconditional {@code customLists: []} override.
     * The index carries only the action name because the setting is a
     * singleton per account.
     *
     * @param timestamp the mutation timestamp recorded on both the outer
     *                  mutation and the inner {@code SyncActionValue}
     * @param mode      the target distribution mode
     * @param userJids  the JIDs associated with the mode (whitelist for
     *                  {@code ALLOW_LIST}, blacklist for
     *                  {@code DENY_LIST}, may be empty for
     *                  {@code CONTACTS} or {@code CLOSE_FRIENDS})
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
