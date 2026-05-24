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
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * Applies the {@code lid_contact} app-state sync action that propagates
 * username-discovered contacts whose canonical identity is a LID JID.
 *
 * @apiNote
 * Drives the username-based contact discovery flow: when the primary
 * device adds, edits or removes a contact via {@code @username}
 * lookup the resulting record fans out across the
 * {@link SyncPatchType#CRITICAL_UNBLOCK_LOW} collection so companion
 * devices see the same address-book entry. The mutation index keys each
 * entry by the LID JID, formatted as
 * {@snippet :
 *     ["lid_contact", lidJid]
 * }
 * The handler is gated by the
 * {@link ABProp#USERNAME_CONTACT_SYNCD_SUPPORT_ENABLE} A/B prop; while
 * the prop is off, every mutation is reported as
 * {@link MutationApplicationResult#unsupported()} regardless of
 * payload, exactly mirroring WA Web.
 *
 * @implNote
 * This implementation persists the contact directly through
 * {@link com.github.auties00.cobalt.store.WhatsAppStore} rather than
 * through WA Web's
 * {@code WAWebApiContact.createOrMergeAddressBookContacts /
 * setNotAddressBookContacts} batched flow, and stores the username on
 * the contact record so a later remove can recognise it as a
 * username-added contact without consulting an external table. After a
 * successful set, any orphan
 * {@link UserStatusMuteAction} mutations keyed by the same LID JID are
 * replayed via {@link UserStatusMuteHandler} and removed from the
 * orphan store on success, mirroring WA Web's
 * {@code WAWebSyncdOrphan.checkOrphanUserStatusMutes(h.map(...))}
 * post-batch hook. The
 * {@code WAWebSetUsernameJob.setUsernamesJob} job, the
 * {@code clearStatusForRemovedContact} RPC and the contact-collection
 * {@code bulkAddContactToCollection} fire-and-forget are not modelled.
 */
@WhatsAppWebModule(moduleName = "WAWebLidContactSync")
public final class LidContactHandler implements WebAppStateActionHandler {
    /**
     * The {@link Logger} that records non-LID-jid sightings and orphan
     * status-mute retry failures.
     */
    private static final Logger LOGGER = Logger.getLogger(LidContactHandler.class.getName());

    /**
     * The {@link ABPropsService} consulted before every mutation to
     * gate the handler on
     * {@link ABProp#USERNAME_CONTACT_SYNCD_SUPPORT_ENABLE}.
     */
    private final ABPropsService abPropsService;

    /**
     * The {@link UserStatusMuteHandler} replayed against any orphan
     * mutation that was waiting for this LID contact to materialise.
     */
    private final UserStatusMuteHandler userStatusMuteHandler;

    /**
     * Constructs a {@link LidContactHandler} bound to the given
     * dependencies.
     *
     * @apiNote
     * The handler must consult
     * {@link ABProp#USERNAME_CONTACT_SYNCD_SUPPORT_ENABLE} on every
     * mutation rather than caching the value, so server-side prop
     * flips reach the next incoming sync without restarting the
     * client; the
     * {@link UserStatusMuteHandler} is held as a constructor
     * dependency so the orphan-replay loop does not depend on a
     * service-locator.
     *
     * @param abPropsService        the A/B-props service consulted on
     *                              every mutation
     * @param userStatusMuteHandler the handler replayed against orphan
     *                              user-status-mute mutations on a
     *                              successful set
     */
    @WhatsAppWebExport(moduleName = "WAWebLidContactSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public LidContactHandler(ABPropsService abPropsService, UserStatusMuteHandler userStatusMuteHandler) {
        this.abPropsService = abPropsService;
        this.userStatusMuteHandler = userStatusMuteHandler;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLidContactSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return LidContactAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLidContactSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return LidContactAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLidContactSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return LidContactAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation reads the
     * {@link ABProp#USERNAME_CONTACT_SYNCD_SUPPORT_ENABLE} flag first
     * and short-circuits the mutation as
     * {@link MutationApplicationResult#unsupported()} when the flag is
     * off, mirroring WA Web's gating that returns the same state for
     * every entry in the batch. A non-LID JID is rejected as
     * {@link MutationApplicationResult#malformed()} matching WA Web's
     * {@code !a.isLid()} branch. On a {@link SyncdOperation#SET} the
     * username is normalised by stripping a leading {@code "@"} so
     * downstream lookups can match the bare username, and the
     * {@code addedByUsername} flag is set when a non-empty username is
     * present so a later
     * {@link SyncdOperation#REMOVE} can recognise the contact as a
     * username-added entry. Per Cobalt's pluggable error model
     * exceptions propagate to the orchestrator instead of being
     * caught inline as
     * {@link SyncActionState#FAILED}.
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
            LOGGER.fine(() -> "[syncd] lid contact sync received non-lid jid: " + lidJidString);
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
                        .orElseGet(() -> ContactActionHandler.deriveShortName(fullName));
                contact.setShortName(shortName);

                var rawUsername = action.username().orElse(null);
                var hasUsername = rawUsername != null && !rawUsername.isEmpty();
                if (hasUsername) {
                    var normalizedUsername = rawUsername.startsWith("@")
                            ? rawUsername.substring(1)
                            : rawUsername;
                    contact.setUsername(normalizedUsername);
                } else {
                    contact.setUsername(null);
                }
                contact.setAddedByUsername(hasUsername);

                retryOrphanStatusMutes(client, lidJidString);
                yield MutationApplicationResult.success();
            }
            case REMOVE -> {
                client.store().findContactByJid(lidJid).ifPresent(contact -> {
                    if (contact.isAddedByUsername()) {
                        contact.setFullName(null);
                        contact.setShortName(null);
                        contact.setUsername(null);
                        contact.setAddedByUsername(false);
                    }
                });
                yield MutationApplicationResult.success();
            }
            default -> MutationApplicationResult.unsupported();
        };
    }

    /**
     * Replays orphan {@link UserStatusMuteAction} mutations keyed by the
     * given LID JID and prunes the ones that succeed.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebSyncdOrphan.checkOrphanUserStatusMutes(h.map(...))}
     * post-batch hook: when a LID contact materialises, any earlier
     * mute mutation that referenced the contact while it was unknown
     * is given a second chance.
     *
     * @implNote
     * This implementation iterates the orphan entries returned by
     * {@link com.github.auties00.cobalt.store.WhatsAppStore#findOrphanMutationsByModel(SyncPatchType, String)},
     * rebuilds a {@link DecryptedMutation.Trusted} for each, calls the
     * stored {@link UserStatusMuteHandler#applyMutation}, and removes
     * only the entries that returned
     * {@link SyncActionState#SUCCESS}. Failures are kept so the next
     * sync can retry. Unexpected exceptions are caught and logged at
     * {@link java.util.logging.Level#WARNING} because the surrounding
     * mutation has already mutated the store and must not roll back.
     *
     * @param client       the {@link WhatsAppClient} whose orphan store
     *                     is consulted
     * @param lidJidString the LID JID string keying the orphan
     *                     mutations
     */
    private void retryOrphanStatusMutes(WhatsAppClient client, String lidJidString) {
        try {
            var entries = client.store().findOrphanMutationsByModel(UserStatusMuteAction.COLLECTION_NAME, lidJidString);
            if (entries.isEmpty()) {
                return;
            }

            var applied = new ArrayList<OrphanMutationEntry>();
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
            LOGGER.warning("[syncd] lid contact: orphan status mutes check failed: " + e.getMessage());
        }
    }
}
