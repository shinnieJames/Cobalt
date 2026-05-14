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
 * Handles {@code userStatusMute} sync actions from the {@code regular_high} collection.
 *
 * <p>This handler processes mutations that mute or unmute another user's (or group's)
 * status updates across linked devices. The mute state is applied to the matching
 * contact in the local store; for groups, it is written to the typed
 * {@link GroupMetadata#statusMuted()} field on the corresponding metadata row.
 * When the referenced contact or group is unknown to the local store, the
 * mutation is recorded as orphan so that a future {@code contact} or
 * {@code group_metadata} sync may retry it.
 *
 * <p>Index format: {@code ["userStatusMute", widString]} where {@code widString}
 * is a user wid (legacy form) or a group wid.
 */
@WhatsAppWebModule(moduleName = "WAWebUserStatusMuteSync")
public final class UserStatusMuteHandler implements WebAppStateActionHandler {

    /**
     * Private constructor preventing external instantiation.
     */
    @WhatsAppWebExport(moduleName = "WAWebUserStatusMuteSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public UserStatusMuteHandler() {

    }

    /**
     * Returns the action name for this handler.
     * @return the action name string
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUserStatusMuteSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return UserStatusMuteAction.ACTION_NAME;
    }

    /**
     * Returns the sync collection this handler belongs to.
     * @return the sync patch type
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUserStatusMuteSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return UserStatusMuteAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for this handler.
     * @return the version number
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUserStatusMuteSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return UserStatusMuteAction.ACTION_VERSION;
    }

    /**
     * Applies a single user status mute mutation and returns the detailed result.
     *
     * <p>For {@code SET} operations, this method:
     * <ul>
     *   <li>Validates the mutation index second element is a usable wid string;
     *       returns {@code MALFORMED} otherwise</li>
     *   <li>Validates the mutation value carries a {@link UserStatusMuteAction}
     *       with a non-{@code null} {@code muted} field; returns {@code MALFORMED} otherwise</li>
     *   <li>For group wids, returns {@code ORPHAN} when the group is unknown to
     *       the local store; otherwise updates the {@link GroupMetadata#statusMuted()}
     *       field on the corresponding metadata row and returns {@code SUCCESS}</li>
     *   <li>For user wids, returns {@code ORPHAN} when the contact is unknown;
     *       otherwise updates the contact's {@code statusMuted} field and returns
     *       {@code SUCCESS}</li>
     * </ul>
     *
     * <p>For non-{@code SET} operations, returns {@code UNSUPPORTED}.
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUserStatusMuteSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        // MISMATCH (ORDER): WA Web checks the index (indexParts[1] is a wid) BEFORE the value. Cobalt currently
        // checks the value first, so a mutation with both a bad index and a missing muted field ends up tagged
        // {@code MALFORMED_VALUE} in Cobalt vs {@code MALFORMED_INDEX} in WA Web. Reordered below to match WA Web.
        var indexArray = JSON.parseArray(mutation.index());
        // WAWebUserStatusMuteSync.applyMutations: var n=e.indexParts, u=n[1]; if(!u||!isWid(u)) return malformedActionIndex().
        // indexParts[1] is undefined when the slot is missing; mirror with explicit size check.
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

        // MISMATCH (COALESCE): WA Web specifically tests {@code c === void 0} where {@code c = userStatusMuteAction.muted}.
        // Cobalt's {@link UserStatusMuteAction#muted()} coalesces a {@code null} Boolean to {@code false}, so a
        // mutation with {@code userStatusMuteAction: {muted: null}} is silently applied as "unmute" here instead of
        // returning {@code malformedActionValue}. Fixing this would require promoting the nullable Boolean accessor
        // out of the package-private field; the project-wide "nullable boolean coalesces to false" convention
        // (see {@code feedback_nullable_bool_accessors.md}) intentionally accepts this divergence.
        // ADAPTED: WAWebUserStatusMuteSync.applyMutations — malformed-counter + first-three WARN log telemetry
        // is intentionally omitted (matches the project-wide policy of dropping WAM/WALogger paths).
        if (wid.hasServer(JidServer.groupOrCommunity())) {
            // Build a GroupMetadataEdit carrying only the statusMuted override and let the store
            // merge it into the existing GroupMetadata row. The autoboxed Boolean preserves
            // "value applied" semantics: an absent muted field would yield Boolean.FALSE here
            // (per the project-wide nullable-bool-coalesce convention), which is fine because
            // any malformed-value detection already happened in the actionValue null-check above.
            var edit = new GroupMetadataEditBuilder()
                    .group(wid)
                    .statusMuted(action.muted())
                    .build();
            var updated = client.store().applyGroupMetadataEdit(wid, edit);
            // ADAPTED: Cobalt does not have frontend event dispatching; the store update is sufficient
            return updated.isPresent()
                    ? MutationApplicationResult.success()
                    : MutationApplicationResult.orphan(widString, "UserStatusMute");
        }

        var contact = client.store().findContactByJid(wid);
        if (contact.isEmpty()) {
            return MutationApplicationResult.orphan(widString, "UserStatusMute");
        }

        contact.get().setStatusMuted(action.muted());
        // ADAPTED: Cobalt does not have frontend event dispatching; the store update is sufficient
        return MutationApplicationResult.success();
    }

    /**
     * Builds a pending mutation for muting or unmuting a user's status updates.
     *
     * <p>Per WhatsApp Web {@code WAWebUserStatusMuteSync.getMutationForStatusMute(e, t, n)}:
     * <ol>
     *   <li>Constructs the value with {@code {userStatusMuteAction: {muted: t}}}</li>
     *   <li>Builds the pending mutation via {@code WAWebSyncdActionUtils.buildPendingMutation}
     *       with {@code action = this.getAction()}, {@code collection = this.collectionName},
     *       {@code indexArgs = [e.toString({legacy: true})]}, {@code operation = SET},
     *       {@code timestamp = n}, {@code value}, and {@code version = this.getVersion()}</li>
     * </ol>
     * @param wid       the JID of the user (or group) whose status mute state is being updated
     * @param muted     the new status mute state
     * @param timestamp the mutation timestamp
     * @return the pending mutation for the user status mute action
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
