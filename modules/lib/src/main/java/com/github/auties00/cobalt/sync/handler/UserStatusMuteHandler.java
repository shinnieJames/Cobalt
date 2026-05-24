package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.group.GroupMetadata;
import com.github.auties00.cobalt.model.chat.group.GroupMetadataEditBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.model.sync.action.contact.UserStatusMuteAction;
import com.github.auties00.cobalt.model.sync.action.contact.UserStatusMuteActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import java.time.Instant;
import java.util.List;

/**
 * Mirrors the per-contact (or per-group) "mute status updates" preference
 * across linked devices.
 *
 * @apiNote
 * Cobalt embedders never invoke this handler directly; the sync dispatcher
 * routes incoming {@code userStatusMute} mutations here whenever the user
 * mutes or unmutes another contact's or group's status posts on a linked
 * device. The handler writes the new value to the matching
 * {@link com.github.auties00.cobalt.model.contact.Contact} or
 * {@link GroupMetadata#statusMuted()} record on
 * {@link com.github.auties00.cobalt.store.WhatsAppStore}; mutations
 * referencing an unknown contact or group are reported as orphan so a
 * later contact / group-metadata sync can retry them.
 */
@WhatsAppWebModule(moduleName = "WAWebUserStatusMuteSync")
public final class UserStatusMuteHandler implements WebAppStateActionHandler {

    /**
     * Constructs the handler.
     *
     * @apiNote
     * The handler is stateless; Cobalt's sync registry holds a single
     * instance per client.
     */
    @WhatsAppWebExport(moduleName = "WAWebUserStatusMuteSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public UserStatusMuteHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUserStatusMuteSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return UserStatusMuteAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUserStatusMuteSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return UserStatusMuteAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUserStatusMuteSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return UserStatusMuteAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation mirrors WA Web's per-mutation closure inside
     * {@code WAWebUserStatusMuteSync.applyMutations}: the
     * {@code indexParts[1]} JID is validated first (mirroring WA Web's
     * {@code if(!u||!isWid(u)) return malformedActionIndex()}), then the
     * decoded {@link UserStatusMuteAction} value must be present. Group
     * JIDs route through
     * {@link com.github.auties00.cobalt.store.WhatsAppStore#applyGroupMetadataEdit(Jid, com.github.auties00.cobalt.model.chat.group.GroupMetadataEdit)};
     * user JIDs route through the
     * {@link com.github.auties00.cobalt.model.contact.Contact#setStatusMuted(Boolean)}
     * accessor on the resolved contact. An unknown group or contact
     * surfaces as {@link MutationApplicationResult#orphan(String, String)}
     * with the model type {@code "UserStatusMute"} so the sync
     * dispatcher can retry once the missing entity arrives via a
     * separate sync. WA Web's newsletter-status branch is dropped
     * because Cobalt has no newsletter status surface; WA Web's
     * {@code WALogger.WARN} counters and frontend
     * {@code updateContactsStatusMute} fire-and-forget hop are omitted
     * as telemetry / UI concerns.
     *
     * <p>Cobalt's {@link UserStatusMuteAction#muted()} accessor coalesces
     * a missing nullable boolean to {@code false} per the
     * project-wide convention; the explicit {@code c === void 0} check
     * that WA Web performs is therefore relaxed to "value action
     * present", and a mutation carrying {@code muted: null} is silently
     * applied as "unmute" rather than reported as malformed.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUserStatusMuteSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        var indexArray = JSON.parseArray(mutation.index());
        if (indexArray.size() <= 1) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }
        var widString = indexArray.getString(1);
        if (widString == null || widString.isEmpty()) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        Jid wid;
        try {
            wid = Jid.of(widString);
        } catch (RuntimeException e) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        if (!(mutation.value().action().orElse(null) instanceof UserStatusMuteAction action)) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        if (wid.hasServer(JidServer.groupOrCommunity())) {
            var edit = new GroupMetadataEditBuilder()
                    .group(wid)
                    .statusMuted(action.muted())
                    .build();
            var updated = client.store().applyGroupMetadataEdit(wid, edit);
            return updated.isPresent()
                    ? MutationApplicationResult.success()
                    : MutationApplicationResult.orphan(widString, "UserStatusMute");
        }

        var contact = client.store().findContactByJid(wid);
        if (contact.isEmpty()) {
            return MutationApplicationResult.orphan(widString, "UserStatusMute");
        }

        contact.get().setStatusMuted(action.muted());
        return MutationApplicationResult.success();
    }

    /**
     * Builds the pending {@link SyncPendingMutation} that mutes or
     * unmutes another user's or group's status updates.
     *
     * @apiNote
     * Used by Cobalt's outgoing-mutation factory when the embedder
     * toggles {@code statusMuted} on a contact or group; the returned
     * pending mutation is queued through the regular sync pipeline so
     * the change propagates to the user's other devices.
     *
     * @implNote
     * This implementation mirrors WA Web's
     * {@code WAWebUserStatusMuteSync.getMutationForStatusMute(e, t, n)}:
     * the index is {@code ["userStatusMute", wid.toString()]} (legacy
     * serialization), the value carries the {@code muted} boolean, and
     * the operation is {@link SyncdOperation#SET}. WA Web's call into
     * {@code WAWebSyncdActionUtils.buildPendingMutation} reduces to
     * direct {@link DecryptedMutation.Trusted} and {@link SyncPendingMutation}
     * construction in Cobalt because the trusted mutation already
     * carries the encoded value.
     *
     * @param wid       the user or group JID whose status mute state is being updated
     * @param muted     {@code true} to mute status updates, {@code false} to unmute
     * @param timestamp the mutation timestamp
     * @return the pending mutation ready for the outgoing sync pipeline
     */
    @WhatsAppWebExport(moduleName = "WAWebUserStatusMuteSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPendingMutation getMutationForStatusMute(Jid wid, boolean muted, Instant timestamp) {
        var action = new UserStatusMuteActionBuilder()
                .muted(muted)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .userStatusMuteAction(action)
                .build();
        var index = JSON.toJSONString(List.of(actionName(), wid.toString()));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                version()
        );
        return new SyncPendingMutation(mutation, 0);
    }
}
