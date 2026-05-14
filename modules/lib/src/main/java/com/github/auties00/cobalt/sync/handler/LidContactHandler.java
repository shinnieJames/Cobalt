package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.OrphanMutationEntry;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.contact.LidContactAction;
import com.github.auties00.cobalt.model.sync.action.contact.UserStatusMuteAction;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * Handles LID contact sync actions from the {@code critical_unblock_low} collection.
 *
 * <p>This handler processes mutations that synchronize LID-addressed contact
 * information (first name, full name, username) across linked devices. Unlike
 * {@link ContactActionHandler}, which handles phone-number-addressed contacts,
 * this handler is dedicated to contacts created via username discovery whose
 * canonical identity is a LID JID.
 *
 * <p>It processes both {@code SET} operations (create or update a LID contact)
 * and {@code REMOVE} operations (mark a LID contact as no longer in the user's
 * address book).
 *
 * <p>Index format: {@code ["lid_contact", lidJid]}.
 */
@WhatsAppWebModule(moduleName = "WAWebLidContactSync")
public final class LidContactHandler implements WebAppStateActionHandler {
    /**
     * Logger for diagnostic messages emitted during LID contact sync processing.
     */
    private static final Logger LOGGER = Logger.getLogger(LidContactHandler.class.getName());

    /**
     * The AB-props service consulted before applying any mutation.
     */
    private final ABPropsService abPropsService;

    /**
     * The user-status-mute handler delegated to when an orphan LID
     * contact mutation must be retried as a user-status-mute mutation.
     */
    private final UserStatusMuteHandler userStatusMuteHandler;

    /**
     * Constructs the handler instance bound to the given AB-props
     * service and its companion user-status-mute handler.
     *
     * @param abPropsService        the AB-props service consulted on
     *                              every mutation
     * @param userStatusMuteHandler the user-status-mute handler used to
     *                              re-process orphan mutations
     */
    @WhatsAppWebExport(moduleName = "WAWebLidContactSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public LidContactHandler(ABPropsService abPropsService, UserStatusMuteHandler userStatusMuteHandler) {
        this.abPropsService = abPropsService;
        this.userStatusMuteHandler = userStatusMuteHandler;
    }

    /**
     * Returns the action name for this handler.
     * @return the action name string
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLidContactSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return LidContactAction.ACTION_NAME;
    }

    /**
     * Returns the sync collection this handler belongs to.
     * @return the sync patch type
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLidContactSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return LidContactAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for this handler.
     * @return the version number
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLidContactSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return LidContactAction.ACTION_VERSION;
    }

    /**
     * Applies a single LID contact mutation and returns a detailed result.
     *
     * <p>Gated on {@code username_contact_syncd_support_enable}: if the flag is
     * disabled the handler returns {@link MutationApplicationResult#unsupported()}
     * for every mutation, mirroring WA Web's early return of
     * {@code {actionState: Unsupported}}.
     *
     * <p>For {@code SET} operations, this method:
     * <ul>
     *   <li>Validates the mutation value is a {@link LidContactAction}</li>
     *   <li>Extracts and validates the LID JID from the index</li>
     *   <li>Rejects non-LID JIDs as malformed (matches WA Web {@code !a.isLid()})</li>
     *   <li>Creates or updates the contact with full name, short name and username</li>
     *   <li>Stores {@code username} unconditionally on the local contact record so
     *       that subsequent {@code REMOVE} operations can recognize it as a
     *       username-added contact</li>
     *   <li>Retries orphan {@code userStatusMute} mutations keyed by this LID JID</li>
     * </ul>
     *
     * <p>For {@code REMOVE} operations, this method:
     * <ul>
     *   <li>Validates the LID JID from the index</li>
     *   <li>Looks up the existing contact record; if it was added by username
     *       ({@code isUsernameContact === true} in WA Web) it clears the
     *       full name, short name, username and {@code addedByUsername} flag</li>
     * </ul>
     *
     * <p>Per-mutation exceptions thrown by WA Web are caught inline and mapped to
     * {@code {actionState: Failed}}. Per Cobalt's error model, these are
     * propagated as {@link com.github.auties00.cobalt.exception.WhatsAppException}
     * instances by the calling pipeline; the handler itself no longer mirrors the
     * inline {@code try/catch}.
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLidContactSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (!abPropsService.getBool(ABProp.USERNAME_CONTACT_SYNCD_SUPPORT_ENABLE)) {
            return MutationApplicationResult.unsupported();
        }

        var indexArray = JSON.parseArray(mutation.index());
        var lidJidString = indexArray.getString(1);
        if (lidJidString == null || lidJidString.isEmpty()) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        var lidJid = Jid.of(lidJidString);
        if (!lidJid.hasLidServer()) {
            LOGGER.fine(() -> "[syncd] lid contact sync received non-lid jid: " + lidJidString); // ADAPTED: WAWebLidContactSync.applyMutations — WALogger.ERROR("[syncd] lid contact sync received %s non-lid jids") batched telemetry
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        return switch (mutation.operation()) {
            case SET -> {
                if (!(mutation.value().action().orElse(null) instanceof LidContactAction action)) {
                    yield SyncdIndexUtils.malformedActionValue(collectionName().name());
                }

                var contact = client.store()
                        .findContactByJid(lidJid)
                        .orElseGet(() -> client.store().addNewContact(lidJid));
                var fullName = action.fullName().orElse("");
                contact.setFullName(fullName);
                var shortName = action.firstName()
                        .orElseGet(() -> ContactActionHandler.deriveShortName(fullName)); // ADAPTED: WAWebLidContactSync.applyMutations — WA Web's getShortName(u) short-circuits on undefined; Cobalt follows WAWebContactSync's intended behavior of deriving from the full name
                contact.setShortName(shortName);

                var rawUsername = action.username().orElse(null);
                var hasUsername = rawUsername != null && !rawUsername.isEmpty();
                if (hasUsername) {
                    // WA Web stores raw `d` on the local contact record (m.username = d) and only strips
                    // the leading '@' when passing to setUsernamesJob(C). Cobalt has no separate
                    // setUsernamesJob pipeline, so the stripped form is persisted directly on the
                    // contact record to keep lookups consistent across handlers.
                    var normalizedUsername = rawUsername.startsWith("@") // ADAPTED: WAWebLidContactSync.applyMutations — setUsernamesJob strip integrated into local write
                            ? rawUsername.substring(1)
                            : rawUsername;
                    contact.setUsername(normalizedUsername);
                } else {
                    contact.setUsername(null);
                }
                contact.setAddedByUsername(hasUsername);

                // SKIPPED: debounced background contact sync refresh; not mirrored in Cobalt.
                retryOrphanStatusMutes(client, lidJidString);
                yield MutationApplicationResult.success();
            }
            case REMOVE -> {
                //   if (e && e.isUsernameContact === true) push to E, then setNotAddressBookContacts(E)
                //   which clears the address-book fields on the record.
                // Cobalt adapts this per-mutation: look up the contact directly and, if it was
                // added by username, clear its address-book fields.
                client.store().findContactByJid(lidJid).ifPresent(contact -> {
                    if (contact.isAddedByUsername()) {
                        contact.setFullName(null); // ADAPTED: WAWebLidContactSync.applyMutations — setNotAddressBookContacts clears address book fields
                        contact.setShortName(null); // ADAPTED: WAWebLidContactSync.applyMutations — setNotAddressBookContacts clears address book fields
                        contact.setUsername(null); // ADAPTED: WAWebLidContactSync.applyMutations — setContactsNotMyUsernameContacts clears username
                        contact.setAddedByUsername(false); // ADAPTED: WAWebLidContactSync.applyMutations — contact no longer a username contact
                    }
                });
                // SKIPPED: UI notification to clear status rings for removed contacts; not mirrored in Cobalt.
                yield MutationApplicationResult.success();
            }
            default -> {
                // NO_WA_BASIS: SyncdOperation only has SET and REMOVE, so this branch is
                // unreachable today, but it mirrors WA Web's fall-through for any future
                // operation type and matches ContactActionHandler's shape.
                yield MutationApplicationResult.unsupported();
            }
        };
    }

    /**
     * Retries orphan {@code userStatusMute} mutations that may have been blocked
     * by the absence of the specified LID contact.
     *
     * <p>When a LID contact is created or updated via a {@code SET} mutation,
     * any previously orphaned {@code userStatusMute} mutations referencing that
     * contact's JID are re-applied. Successfully applied orphan mutations are
     * removed from the store.
     * @param client        the WhatsApp client instance
     * @param lidJidString  the LID JID string of the contact to check orphans for
     */
    private void retryOrphanStatusMutes(WhatsAppClient client, String lidJidString) {
        try {
            var entries = client.store().findOrphanMutationsByModel(UserStatusMuteAction.COLLECTION_NAME, lidJidString);
            if (entries.isEmpty()) {
                return;
            }

            var applied = new ArrayList<OrphanMutationEntry>(); // NO_WA_BASIS — Cobalt orphan retry bookkeeping
            for (var entry : entries) {
                var orphanMutation = new DecryptedMutation.Trusted(
                        entry.index(),
                        entry.value(),
                        entry.operation(),
                        entry.timestamp(),
                        entry.actionVersion()
                );
                var result = userStatusMuteHandler.applyMutation(client, orphanMutation);
                if (result.actionState() == SyncActionState.SUCCESS) {
                    applied.add(entry);
                }
            }

            if (!applied.isEmpty()) {
                client.store().removeOrphanMutations(UserStatusMuteAction.COLLECTION_NAME, applied);
            }
        } catch (Exception e) {
            LOGGER.warning("[syncd] lid contact: orphan status mutes check failed: " + e.getMessage()); // ADAPTED: WAWebLidContactSync.applyMutations — WALogger.ERROR replaced with j.u.l WARNING
        }
    }
}
