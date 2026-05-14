package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.media.AvatarUpdatedAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
/**
 * Handles {@code avatar_updated_action} app-state sync mutations.
 *
 * <p>Per WhatsApp Web {@code WAWebStickersAvatarUpdatedSyncAction}, only
 * {@code SET} operations are supported. The handler:
 * <ol>
 *   <li>Checks the {@code enable_avatars_on_web_companion} A/B prop and
 *       returns {@code Unsupported} for every mutation when the feature
 *       is disabled.</li>
 *   <li>Returns {@code Unsupported} for non-{@code SET} operations.</li>
 *   <li>Returns {@code Malformed} when the action or its {@code eventType}
 *       is missing.</li>
 *   <li>Skips mutations whose timestamp is at or before the local pairing
 *       timestamp (events that happened before this companion was paired).</li>
 *   <li>For {@code CREATED}/{@code UPDATED} marks the local user as having
 *       an avatar, for {@code DELETED} marks the user as not having one,
 *       and clears the recent avatar stickers cache.</li>
 * </ol>
 *
 * <p>Index format: {@code ["avatar_updated_action"]}.
 */
@WhatsAppWebModule(moduleName = "WAWebStickersAvatarUpdatedSyncAction")
public final class AvatarUpdatedHandler implements WebAppStateActionHandler {
    /**
     * The AB-props service consulted before applying any mutation.
     */
    private final ABPropsService abPropsService;

    /**
     * Constructs a new handler.
     *
     * @param abPropsService the AB-props service consulted on every
     *                       mutation
     */
    @WhatsAppWebExport(moduleName = "WAWebStickersAvatarUpdatedSyncAction", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public AvatarUpdatedHandler(ABPropsService abPropsService) {
        this.abPropsService = abPropsService;
    }

    /**
     * {@inheritDoc}
     * @return the canonical {@code "avatar_updated_action"} identifier
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStickersAvatarUpdatedSyncAction", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return AvatarUpdatedAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     * @return {@link SyncPatchType#REGULAR}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStickersAvatarUpdatedSyncAction", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return AvatarUpdatedAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     * @return {@code 7}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStickersAvatarUpdatedSyncAction", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return AvatarUpdatedAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Implements the body of {@code WAWebStickersAvatarUpdatedSyncAction.applyMutations}
     * for a single mutation. The WA Web counter logging that aggregates
     * {@code notSupported}, {@code malformed} and {@code skipped} mutations
     * is intentionally omitted (WAM/telemetry).
     * @param client   the WhatsApp client linked to the mutation
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStickersAvatarUpdatedSyncAction", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        //   if (!WAWebAvatarGatingUtils.avatarsOnWebEnabled())
        //     return mutations.map(() => ({actionState: Unsupported}))
        //   return WAWebABProps.getABPropConfigValue("enable_avatars_on_web_companion")
        if (!abPropsService.getBool(ABProp.ENABLE_AVATARS_ON_WEB_COMPANION)) {
            return MutationApplicationResult.unsupported();
        }

        //   if (e.operation !== "set") return notSupported++, {actionState: Unsupported}
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        //   var l = e.value.avatarUpdatedAction?.eventType
        //   if (l == null) return malformed++, malformedActionValue(this.collectionName)
        if (!(mutation.value().action().orElse(null) instanceof AvatarUpdatedAction action)) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }
        var eventType = action.eventType().orElse(null);
        if (eventType == null) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        //   var s = WAWebUserPrefsMultiDevice.getPairingTimestamp()
        //   if (s != null) {
        //     var u = WATimeUtils.castMilliSecondsToUnixTime(e.timestamp)
        //     if (u <= WATimeUtils.castToUnixTime(s)) return skipped++, {actionState: Skipped}
        //   }
        var pairingTimestamp = client.store().pairingTimestamp().orElse(null);
        if (pairingTimestamp != null && !mutation.timestamp().isAfter(pairingTimestamp)) {
            return MutationApplicationResult.skipped();
        }

        //   case CREATED: case UPDATED: WAWebHasAvatar.saveHasAvatarOnTempStorage(true); break
        //   case DELETED: WAWebHasAvatar.saveHasAvatarOnTempStorage(false); break
        //
        //
        // Cobalt collapses the UserPrefsStore "UserHasAvatar" key into
        // WhatsAppStore.setHasAvatar(Boolean) (Optional<Boolean> hasAvatar() getter).
        switch (eventType) {
            case CREATED, UPDATED -> client.store().setHasAvatar(true);
            case DELETED -> client.store().setHasAvatar(false);
        }

        //   WAWebRecentStickerCollectionMd.RecentStickerCollectionMd.removeAllRecentAvatarStickers()
        client.store().removeAllRecentAvatarStickers();
        return MutationApplicationResult.success();
    }
}
