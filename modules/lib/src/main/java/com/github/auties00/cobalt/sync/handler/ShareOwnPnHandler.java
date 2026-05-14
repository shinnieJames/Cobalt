package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
/**
 * Handles the {@code shareOwnPn} app state sync action.
 *
 * <p>Per WhatsApp Web {@code WAWebShareOwnPnSync}, this action carries the
 * decision to share the local user's phone number with a specific
 * LID-identified contact. The action's index is shaped as
 * {@code ["shareOwnPn", lidJid]} and there is no value payload: the presence
 * of a {@code SET} mutation simply records that the LID contact may now see
 * the local user's phone number, while the absence of the mutation (or a
 * non-{@code SET} operation) leaves the contact in its previous state.
 *
 * <p>Per {@code WAWebShareOwnPnSync.applyMutations}, the handler:
 * <ol>
 *   <li>Returns {@code Unsupported} for the entire batch when the
 *       {@code share_own_pn_sync} A/B prop is not {@code true}.</li>
 *   <li>Returns {@code Unsupported} for any non-{@code set} operation
 *       (WA Web increments the {@code a} unsupported counter and logs a
 *       batched WARN at the end of the loop).</li>
 *   <li>Returns {@link #malformedActionIndex()} when {@code indexParts[1]} is
 *       not a wid-like string (WA Web increments the {@code i} malformed
 *       counter and calls {@code malformedActionIndex()}).</li>
 *   <li>For every valid mutation, accumulates a
 *       {@code {lid, data: {shareOwnPn: true}}} entry, then calls
 *       {@code WAWebUpdateLidMetadataJob.updateLidMetadataJob(updates)} once
 *       per batch. The job ultimately invokes
 *       {@code WAWebApiContact.updateLidMetadata}, which writes
 *       {@code shareOwnPn = true} on each LID contact record via
 *       {@code WAWebLidAwareContactsDB.bulkCreateOrMerge}.</li>
 * </ol>
 *
 * <p>Cobalt collapses the WA Web batch + job + bulk-merge into a direct
 * per-mutation update of the local {@link com.github.auties00.cobalt.model.contact.Contact}
 * record's {@code phoneNumberShared} field, which is the storage column that
 * WA Web's {@code shareOwnPn} contact attribute maps to. The contact is
 * created on-demand if it does not yet exist, mirroring
 * {@code bulkCreateOrMerge}'s upsert semantic.
 *
 * <p>WA Web's frontend {@code bulkUpdateLidContactState} call and its WAM /
 * WALogger telemetry are intentionally omitted: Cobalt's store is the sole
 * source of truth and telemetry is not mirrored.
 */
@WhatsAppWebModule(moduleName = "WAWebShareOwnPnSync")
public final class ShareOwnPnHandler implements WebAppStateActionHandler {
    /**
     * The AB-props service consulted before applying any mutation.
     */
    private final ABPropsService abPropsService;

    /**
     * Constructs the handler instance bound to the given AB-props
     * service.
     *
     * @param abPropsService the AB-props service consulted on every
     *                       mutation
     */
    @WhatsAppWebExport(moduleName = "WAWebShareOwnPnSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public ShareOwnPnHandler(ABPropsService abPropsService) {
        this.abPropsService = abPropsService;
    }

    /**
     * Returns the action name routed to this handler.
     * @return the action identifier {@code "shareOwnPn"}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebShareOwnPnSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return "shareOwnPn";
    }

    /**
     * Returns the sync collection this handler operates on.
     * @return {@link SyncPatchType#REGULAR}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebShareOwnPnSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR;
    }

    /**
     * Returns the mutation format version implemented by this handler.
     * @return {@code 8}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebShareOwnPnSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return 8;
    }

    /**
     * Applies a single {@code shareOwnPn} mutation and returns the detailed
     * {@link MutationApplicationResult}.
     *
     * <p>Order of validation, matching {@code WAWebShareOwnPnSync.applyMutations}:
     * <ol>
     *   <li>If the {@code share_own_pn_sync} A/B prop is not {@code true},
     *       the mutation is reported as
     *       {@link MutationApplicationResult#unsupported() unsupported}
     *       (WA Web returns {@code Unsupported} for every mutation in the
     *       batch and logs a single WARN).</li>
     *   <li>If the operation is not a {@link SyncdOperation#SET}, it is
     *       reported as unsupported (WA Web increments the {@code a}
     *       counter).</li>
     *   <li>If {@code indexParts[1]} is missing, empty, or not wid-like, it
     *       is reported via {@link #malformedActionIndex()} (WA Web
     *       increments the {@code i} counter and calls
     *       {@code n.malformedActionIndex()}).</li>
     *   <li>If {@code createUserLidOrThrow} would throw because the parsed
     *       JID is not a {@code @lid}, it is reported via
     *       {@link #malformedActionIndex()} as well, mirroring WA Web's
     *       upstream throw which would propagate out of {@code applyMutations}
     *       and be caught by the parent batch loop.</li>
     * </ol>
     *
     * <p>When every check passes, the handler upserts the LID contact record
     * with {@code phoneNumberShared = true}. This corresponds to WhatsApp
     * Web's {@code updateLidMetadataJob([{lid, data: {shareOwnPn: true}}])}
     * call which fans out to {@code WAWebApiContact.updateLidMetadata}, which
     * in turn invokes {@code bulkCreateOrMerge} on the LID-aware contacts DB
     * with the {@code shareOwnPn} flag set. The frontend
     * {@code bulkUpdateLidContactState} mirror call and the WAM telemetry are
     * intentionally not replicated, as Cobalt's store is the sole source of
     * truth.
     * @param client   the WhatsApp client whose store receives the contact update
     * @param mutation the decrypted, trusted mutation to apply
     * @return the detailed {@link MutationApplicationResult}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebShareOwnPnSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (!abPropsService.getBool(ABProp.SHARE_OWN_PN_SYNC)) {
            return MutationApplicationResult.unsupported();
        }

        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        // ADAPTED: Cobalt parses the raw JSON index string here; WA Web's WAWebSyncdMutationParser already exposes indexParts.
        var indexArray = JSON.parseArray(mutation.index());
        var lidJidString = indexArray != null && indexArray.size() > 1 ? indexArray.getString(1) : null;
        // ADAPTED: WA Web's isWidlike accepts any non-empty string that parses as a Wid; Cobalt collapses the
        // empty/null guard and the wid-likeness check into a single null/empty test, then defers the
        // strict @lid check to the JID parse below.
        if (lidJidString == null || lidJidString.isEmpty()) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        // ADAPTED: WAWebWidFactory.createUserLidOrThrow throws if the parsed wid is not a @lid; the throw
        // propagates out of applyMutations and is caught by the upstream batch loop. Cobalt converts the
        // would-be exception into a malformedActionIndex result, which preserves the underlying MALFORMED
        // semantic without surfacing a runtime exception to the caller.
        var lidJid = Jid.of(lidJidString);
        if (!lidJid.hasLidServer()) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        //   -> WAWebUpdateLidMetadataApi.updateLidMetadata({updates: r})
        //     -> WAWebApiContact.updateLidMetadata(updates)
        //       -> WAWebLidAwareContactsDB.bulkCreateOrMerge([{id: lid.toString(), shareOwnPn: true}])
        // ADAPTED: WA Web accumulates valid entries across the batch and flushes once at the end via the
        // updateLidMetadataJob; Cobalt writes each contact directly per-mutation. The contact is upserted
        // (created on demand) to mirror bulkCreateOrMerge's create-or-merge semantic. The frontend
        // bulkUpdateLidContactState mirror call is omitted because Cobalt's store is the sole source of truth.
        var contact = client.store()
                .findContactByJid(lidJid)
                .orElseGet(() -> client.store().addNewContact(lidJid));
        contact.setPhoneNumberShared(true);
        return MutationApplicationResult.success();
    }
}
