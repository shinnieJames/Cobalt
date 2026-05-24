package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.media.AvatarUpdatedAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Updates the local user's avatar-presence state in response to {@code avatar_updated_action} sync mutations.
 *
 * @apiNote
 * Drives the avatar / Avatar Stickers surface where another device has
 * created, updated, or deleted the user's Meta-AI avatar. Cobalt
 * embedders observe the result through
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#hasAvatar()}
 * and (when the avatar is destroyed) through the recent-avatar-sticker
 * cache being cleared.
 *
 * @implNote
 * This implementation drops the per-batch {@code notSupported},
 * {@code malformed}, and {@code skipped} counter logging that WA Web
 * emits via {@code WALogger.WARN}; the per-mutation outcome surfaces in
 * the returned {@link MutationApplicationResult} instead.
 */
@WhatsAppWebModule(moduleName = "WAWebStickersAvatarUpdatedSyncAction")
public final class AvatarUpdatedHandler implements WebAppStateActionHandler {
    /**
     * The {@link ABPropsService} consulted before applying any mutation.
     *
     * @apiNote
     * Used to read the {@link ABProp#ENABLE_AVATARS_ON_WEB_COMPANION}
     * gate; when the gate is off every mutation in the batch resolves
     * to {@link MutationApplicationResult#unsupported()} unchanged.
     */
    private final ABPropsService abPropsService;

    /**
     * Constructs the avatar-updated handler with its dependency on the AB-props subsystem.
     *
     * @apiNote
     * Instantiated by the sync handler registry with a shared
     * {@link ABPropsService}. Embedders do not normally construct this
     * directly.
     *
     * @param abPropsService the {@link ABPropsService} consulted on every mutation
     */
    @WhatsAppWebExport(moduleName = "WAWebStickersAvatarUpdatedSyncAction", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public AvatarUpdatedHandler(ABPropsService abPropsService) {
        this.abPropsService = abPropsService;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebStickersAvatarUpdatedSyncAction", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return AvatarUpdatedAction.ACTION_NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebStickersAvatarUpdatedSyncAction", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return AvatarUpdatedAction.COLLECTION_NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebStickersAvatarUpdatedSyncAction", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return AvatarUpdatedAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Reads the {@link AvatarUpdatedAction#eventType()} field from the
     * mutation value and either marks the user as having an avatar
     * ({@code CREATED} / {@code UPDATED}) or as not having one
     * ({@code DELETED}), then drops the recent-avatar-sticker cache so
     * the UI no longer surfaces stale stickers.
     *
     * @implNote
     * This implementation gates on the
     * {@link ABProp#ENABLE_AVATARS_ON_WEB_COMPANION} prop first;
     * non-{@code SET} operations and missing event types are reported
     * directly through {@link MutationApplicationResult#unsupported()}
     * and {@link SyncdIndexUtils#malformedActionValue(String)}.
     * Mutations whose timestamp is at or before the local pairing
     * timestamp are reported as
     * {@link MutationApplicationResult#skipped()}, mirroring WA Web's
     * "events before pairing" filter.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStickersAvatarUpdatedSyncAction", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (!abPropsService.getBool(ABProp.ENABLE_AVATARS_ON_WEB_COMPANION)) {
            return MutationApplicationResult.unsupported();
        }

        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof AvatarUpdatedAction action)) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }
        var eventType = action.eventType().orElse(null);
        if (eventType == null) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        var pairingTimestamp = client.store().pairingTimestamp().orElse(null);
        if (pairingTimestamp != null && !mutation.timestamp().isAfter(pairingTimestamp)) {
            return MutationApplicationResult.skipped();
        }

        switch (eventType) {
            case CREATED, UPDATED -> client.store().setHasAvatar(true);
            case DELETED -> client.store().setHasAvatar(false);
        }

        client.store().removeAllRecentAvatarStickers();
        return MutationApplicationResult.success();
    }
}
