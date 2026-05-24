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
import com.github.auties00.cobalt.model.sync.action.contact.ContactAction;
import com.github.auties00.cobalt.model.sync.action.contact.UserStatusMuteAction;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Reconciles the local address-book contact roster with {@code contact} sync mutations.
 *
 * @apiNote
 * Drives the address-book surface (the Contacts list, the
 * new-message picker, the LID-PN learning index). When the user adds,
 * edits, renames, or deletes an address-book contact on another
 * device, the server replays the change here as a {@link ContactAction}.
 * Cobalt embedders observe the result through
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#findContactByJid(Jid)}.
 *
 * @implNote
 * This implementation drops several WA Web batch-level side effects
 * because Cobalt has no equivalent surface: the
 * {@code WAWebSyncContactsJob} debounced background refresh, the
 * {@code clearStatusForRemovedContact} frontend send-and-receive
 * call, the per-batch {@code writeSyncdLog} markers, and the
 * batched {@code bulkGet} that filters out username-only contacts
 * before clearing address-book fields. The username-contact filter is
 * implemented per-mutation against the local
 * {@link com.github.auties00.cobalt.model.contact.Contact#isAddedByUsername()}
 * flag instead. LID-PN learning is performed inline via
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#registerLidMapping(Jid, Jid)}
 * rather than batched and committed via WA Web's
 * {@code createLidPnMappings(flushImmediately:true, learningSource:"other")}.
 */
@WhatsAppWebModule(moduleName = "WAWebContactSync")
public final class ContactActionHandler implements WebAppStateActionHandler {
    /**
     * The handler-scoped {@link Logger} used to record orphan-retry failures.
     *
     * @apiNote
     * Records the line equivalent to WA Web's
     * {@code [syncd] contact: orphan status mutes check failed} when
     * the orphan-retry pass throws.
     */
    private static final Logger LOGGER = Logger.getLogger(ContactActionHandler.class.getName());

    /**
     * The {@link ABPropsService} consulted before writing the username field.
     *
     * @apiNote
     * Used to read the {@link ABProp#USERNAME_CONTACT_SYNCD_SUPPORT_ENABLE}
     * gate; when off the username field on a SET mutation is ignored
     * and the username-contact filter on a REMOVE mutation is bypassed.
     */
    private final ABPropsService abPropsService;

    /**
     * The {@link UserStatusMuteHandler} delegated to when retrying orphan user-status-mute mutations.
     *
     * @apiNote
     * Used to re-process any orphan {@code user_status_mute} mutations
     * unblocked by the appearance of a fresh contact, mirroring WA
     * Web's {@code checkOrphanUserStatusMutes} pass after the contact
     * upsert.
     */
    private final UserStatusMuteHandler userStatusMuteHandler;

    /**
     * The compiled {@link Pattern} matching any single Unicode whitespace character.
     *
     * @apiNote
     * Used by {@link #deriveShortName(String)} to take the first
     * whitespace-delimited token of a full name, mirroring WA Web's
     * {@code WAWebContactShortName.getShortName} which splits on
     * {@code /\s/}.
     */
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s");

    /**
     * Constructs the contact-action handler with its dependencies.
     *
     * @apiNote
     * Instantiated by the sync handler registry with the shared
     * {@link ABPropsService} and the dependent
     * {@link UserStatusMuteHandler}. Embedders do not normally
     * construct this directly.
     *
     * @param abPropsService the {@link ABPropsService} consulted for the username gate
     * @param userStatusMuteHandler the {@link UserStatusMuteHandler} used to re-process orphan mutations
     */
    @WhatsAppWebExport(moduleName = "WAWebContactSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public ContactActionHandler(ABPropsService abPropsService, UserStatusMuteHandler userStatusMuteHandler) {
        this.abPropsService = abPropsService;
        this.userStatusMuteHandler = userStatusMuteHandler;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebContactSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return ContactAction.ACTION_NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebContactSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return ContactAction.COLLECTION_NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebContactSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return ContactAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * For SET mutations, validates the JSON index
     * {@code ["contact", contactJid]}, skips LID JIDs (WA Web
     * explicitly rejects {@code isLid()} contacts here), upserts the
     * {@link com.github.auties00.cobalt.model.contact.Contact} with
     * its full name, derived short name, optional username, and LID
     * mapping, and retries any pending orphan
     * {@code user_status_mute} mutations for the same JID. For REMOVE
     * mutations, skips LID and bot JIDs and clears the contact's
     * address-book fields (name, short name, username).
     *
     * @implNote
     * This implementation derives the short name via
     * {@link #deriveShortName(String)} when the action does not carry
     * one, mirroring WA Web's
     * {@code WAWebContactShortName.getShortName} fallback. The
     * username field is written only when
     * {@link ABProp#USERNAME_CONTACT_SYNCD_SUPPORT_ENABLE} is set,
     * matching WA Web's {@code usernameContactSyncdEnabled()} gate.
     * Username-only contacts (those flagged
     * {@link com.github.auties00.cobalt.model.contact.Contact#isAddedByUsername()})
     * survive a REMOVE when the gate is on, mirroring WA Web's
     * {@code bulkGet} filter that exempts {@code isUsernameContact === true}
     * entries from address-book clearing.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebContactSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var indexArray = JSON.parseArray(mutation.index());
        if (indexArray.size() <= 1) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }
        var contactJidString = indexArray.getString(1);
        if (contactJidString == null || contactJidString.isEmpty()) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        var contactJid = Jid.of(contactJidString);
        var usernameEnabled = abPropsService
                .getBool(ABProp.USERNAME_CONTACT_SYNCD_SUPPORT_ENABLE);

        switch (mutation.operation()) {
            case SET -> {
                if (!(mutation.value().action().orElse(null) instanceof ContactAction action)) {
                    return SyncdIndexUtils.malformedActionValue(collectionName().name());
                }

                if (contactJid.hasLidServer()) {
                    return MutationApplicationResult.skipped();
                }

                var contact = client.store()
                        .findContactByJid(contactJid)
                        .orElseGet(() -> client.store().addNewContact(contactJid));
                var fullName = action.fullName().orElse("");
                contact.setFullName(fullName);
                var shortName = action.firstName()
                        .orElseGet(() -> deriveShortName(fullName));
                contact.setShortName(shortName);

                if (usernameEnabled) {
                    action.username()
                            .filter(u -> !u.isEmpty())
                            .map(u -> u.startsWith("@") ? u.substring(1) : u)
                            .ifPresent(contact::setUsername);
                }

                action.lidJid().ifPresent(lid -> {
                    contact.setLid(lid);
                    if (contactJid.hasUserServer()) {
                        client.store().registerLidMapping(contactJid, lid);
                    }
                });

                retryOrphanStatusMutes(client, contactJidString);

                return MutationApplicationResult.success();
            }
            case REMOVE -> {
                if (contactJid.hasLidServer() || contactJid.hasBotServer()) {
                    return MutationApplicationResult.skipped();
                }

                var contact = client.store().findContactByJid(contactJid);
                if (contact.isPresent()) {
                    if (usernameEnabled && contact.get().isAddedByUsername()) {
                        return MutationApplicationResult.success();
                    }
                    contact.get().setFullName(null);
                    contact.get().setShortName(null);
                    contact.get().setUsername(null);
                }
                return MutationApplicationResult.success();
            }
            default -> {
                return MutationApplicationResult.unsupported();
            }
        }
    }

    /**
     * Re-processes any orphan {@code user_status_mute} mutations whose target contact JID is the one just upserted.
     *
     * @apiNote
     * Called from the SET branch of
     * {@link #applyMutation(WhatsAppClient, DecryptedMutation.Trusted)}
     * once the contact upsert lands. Successfully reapplied orphans
     * are deleted from the orphan store; failures are left in place
     * for a future retry.
     *
     * @implNote
     * This implementation walks
     * {@link com.github.auties00.cobalt.store.WhatsAppStore#findOrphanMutationsByModel(SyncPatchType, String)}
     * and dispatches each entry through {@link UserStatusMuteHandler}.
     * Any thrown exception is caught and reported via
     * {@link Logger#warning(String)}, replacing WA Web's
     * {@code WALogger.ERROR(...).sendLogs(...)} pair (Cobalt has no
     * server-side log-uploading channel here).
     *
     * @param client the {@link WhatsAppClient} whose store hosts the orphan entries
     * @param contactJidString the contact JID string used to look up the orphan entries
     */
    @WhatsAppWebExport(moduleName = "WAWebContactSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    private void retryOrphanStatusMutes(WhatsAppClient client, String contactJidString) {
        try {
            var entries = client.store().findOrphanMutationsByModel(UserStatusMuteAction.COLLECTION_NAME, contactJidString);
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
            LOGGER.warning("[syncd] contact: orphan status mutes check failed: " + e.getMessage());
        }
    }

    /**
     * Returns the first whitespace-delimited word of {@code fullName} when it contains a Unicode letter.
     *
     * @apiNote
     * Used as the fallback for
     * {@link ContactAction#firstName()} when the wire payload omits
     * the short name. Matches WA Web's
     * {@code WAWebContactShortName.getShortName} except that an empty
     * or letter-free first token returns the empty string instead of
     * {@code null}; callers in this module coalesce the two
     * uniformly.
     *
     * @implNote
     * This implementation splits on the {@link #WHITESPACE_PATTERN}
     * (Unicode whitespace), takes the first token, and confirms via
     * {@link #containsLetter(String)} that the token has at least one
     * Unicode letter character. WA Web uses an explicit
     * {@code WAWebAlphaRegex} character class; Java's
     * {@link Character#isLetter(int)} covers the same Unicode L*
     * categories.
     *
     * @param fullName the contact full name to derive the short name from
     * @return the first whitespace-delimited token containing a letter, or the empty string if none
     */
    @WhatsAppWebExport(moduleName = "WAWebContactShortName", exports = "getShortName", adaptation = WhatsAppAdaptation.ADAPTED)
    static String deriveShortName(String fullName) {
        if (fullName == null || fullName.isEmpty()) {
            return "";
        }
        var tokens = WHITESPACE_PATTERN.split(fullName, 2);
        var firstToken = tokens[0];
        if (firstToken.isEmpty()) {
            return "";
        }
        if (!containsLetter(firstToken)) {
            return "";
        }
        return firstToken;
    }

    /**
     * Returns whether the given string contains at least one Unicode letter character.
     *
     * @apiNote
     * Used by {@link #deriveShortName(String)} to reject tokens that
     * are pure punctuation, digits, or symbols, mirroring WA Web's
     * {@code WAWebAlphaRegex} acceptance test.
     *
     * @implNote
     * This implementation streams the string's code points and tests
     * each via {@link Character#isLetter(int)}; the L* general
     * category coverage matches the Unicode letter character class WA
     * Web's regex compiles to.
     *
     * @param s the string to inspect
     * @return {@code true} when at least one code point is a Unicode letter; {@code false} otherwise
     */
    @WhatsAppWebExport(moduleName = "WAWebAlphaRegex", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    private static boolean containsLetter(String s) {
        return s.codePoints().anyMatch(Character::isLetter);
    }
}
