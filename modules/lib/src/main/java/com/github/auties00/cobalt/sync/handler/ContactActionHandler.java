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
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Handles contact sync actions from the {@code critical_unblock_low} collection.
 *
 * <p>This handler processes mutations that synchronize address-book contact information
 * (first name, full name, username, LID mapping) across linked devices. It processes
 * both {@code SET} operations (create or update a contact) and {@code REMOVE} operations
 * (mark a contact as no longer in the user's address book).
 *
 * <p>Index format: {@code ["contact", contactJid]}.
 */
@WhatsAppWebModule(moduleName = "WAWebContactSync")
public final class ContactActionHandler implements WebAppStateActionHandler {
    /**
     * Logger for diagnostic messages emitted during contact sync processing.
     */
    private static final Logger LOGGER = Logger.getLogger(ContactActionHandler.class.getName());

    /**
     * The AB-props service consulted before applying any mutation.
     */
    private final ABPropsService abPropsService;

    /**
     * The user-status-mute handler delegated to when an orphan contact
     * mutation must be retried as a user-status-mute mutation.
     */
    private final UserStatusMuteHandler userStatusMuteHandler;

    /**
     * Compiled pattern matching any Unicode whitespace character.
     *
     * <p>Used by {@link #deriveShortName(String)} to split a full name into words,
     * matching the WA Web {@code WAWebContactShortName.getShortName} behavior which
     * splits on {@code /\s/}.
     */
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s");

    /**
     * Constructs a contact action handler bound to the given AB-props
     * service and its companion user-status-mute handler.
     *
     * @param abPropsService        the AB-props service consulted on
     *                              every mutation
     * @param userStatusMuteHandler the user-status-mute handler used to
     *                              re-process orphan mutations
     */
    @WhatsAppWebExport(moduleName = "WAWebContactSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public ContactActionHandler(ABPropsService abPropsService, UserStatusMuteHandler userStatusMuteHandler) {
        this.abPropsService = abPropsService;
        this.userStatusMuteHandler = userStatusMuteHandler;
    }

    /**
     * Returns the action name for this handler.
     * @return the action name string
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebContactSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return ContactAction.ACTION_NAME;
    }

    /**
     * Returns the sync collection this handler belongs to.
     * @return the sync patch type
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebContactSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return ContactAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for this handler.
     * @return the version number
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebContactSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return ContactAction.ACTION_VERSION;
    }

    /**
     * Applies a single contact mutation and returns a detailed result.
     *
     * <p>For {@code SET} operations, this method:
     * <ul>
     *   <li>Validates the mutation value is a {@link ContactAction}</li>
     *   <li>Extracts and validates the contact JID from the index</li>
     *   <li>Skips LID contacts (returns {@code SKIPPED})</li>
     *   <li>Creates or updates the contact with full name, short name, username, and LID</li>
     *   <li>Retries orphan status mute mutations for the contact</li>
     * </ul>
     *
     * <p>For {@code REMOVE} operations, this method:
     * <ul>
     *   <li>Skips LID and bot contacts (returns {@code SKIPPED})</li>
     *   <li>Clears the contact's name and username fields</li>
     * </ul>
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebContactSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var indexArray = JSON.parseArray(mutation.index());
        // WAWebContactSync.applyMutations: var n=t.indexParts, a=n[1]; if(isStringNullOrEmpty(a)) return l.malformedActionIndex()
        // n[1] is undefined when the slot is missing; mirror that via an explicit size check.
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

                //   var g = m != null ? asUserLidOrThrow(createUserWidOrThrow(m, "lid")) : null;
                //   S.set(i, g != null ? g : WAWebLidMigrationUtils.toLid(i));
                //   if (i.isRegularUserPn() && g) v.push({lid: C, pn: i});
                //   ... yield createLidPnMappings({mappings: v, flushImmediately: true, learningSource: "other"})
                // ADAPTED: Cobalt's store is keyed by canonical JID, so the dual-key map S is not mirrored.
                // The createLidPnMappings call is gated on `contactJid.hasUserServer() && lidJid != null`,
                // matching WA Web's `isRegularUserPn() && g` condition.
                action.lidJid().ifPresent(lid -> {
                    contact.setLid(lid);
                    if (contactJid.hasUserServer()) {
                        client.store().registerLidMapping(contactJid, lid);
                    }
                });

                // SKIPPED: debounced background contact sync refresh; not mirrored in Cobalt.
                retryOrphanStatusMutes(client, contactJidString);

                return MutationApplicationResult.success();
            }
            case REMOVE -> {
                if (contactJid.hasLidServer() || contactJid.hasBotServer()) {
                    return MutationApplicationResult.skipped();
                }

                //   b.push(i)                                                                 // per-mutation: queue removal
                //   var I = []; if (h) { D = bulkGet(T); D.forEach((e,t) => { (e==null || e.isUsernameContact!==true) && I.push(b[t]) }) } else I = b;
                //   if (I.length>0) { yield setNotAddressBookContacts(x); ... }
                //   I.forEach(e => { var t = ContactCollection.get(e); t && t.setNotMyContact(); ... })
                // ADAPTED: Cobalt adapts the batch-level bulkGet + filter to a per-mutation
                // check. When username contacts gating is enabled, contacts marked
                // isUsernameContact (addedByUsername) are skipped; otherwise the contact's
                // address-book fields are cleared (setNotMyContact).
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
     * Retries orphan status mute mutations that may have been blocked by the
     * absence of the specified contact.
     *
     * <p>When a contact is created or updated via a SET mutation, any previously
     * orphaned {@code userStatusMute} mutations referencing that contact's JID
     * are re-applied. Successfully applied orphan mutations are removed from
     * the store.
     * @param client           the WhatsApp client instance
     * @param contactJidString the JID string of the contact to check orphans for
     */
    @WhatsAppWebExport(moduleName = "WAWebContactSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    private void retryOrphanStatusMutes(WhatsAppClient client, String contactJidString) {
        try {
            var entries = client.store().findOrphanMutationsByModel(UserStatusMuteAction.COLLECTION_NAME, contactJidString);
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
            LOGGER.warning("[syncd] contact: orphan status mutes check failed: " + e.getMessage()); // ADAPTED: WAWebContactSync.applyMutations — WALogger.ERROR replaced with j.u.l WARNING
        }
    }

    /**
     * Derives a short name from a full name by extracting the first word,
     * matching WhatsApp Web's {@code WAWebContactShortName.getShortName} logic.
     *
     * <p>The algorithm splits the full name on any Unicode whitespace character
     * (matching WA Web's JS {@code /\s/} regex), takes the first token, and
     * checks whether it contains at least one Unicode letter character (matching
     * the {@code WAWebAlphaRegex} character class). If the first token is
     * non-empty and contains a letter, it is returned; otherwise an empty string
     * is returned.
     * @param fullName the full name to derive from
     * @return the first word of the full name, or an empty string if the name
     *         is blank or the first token does not contain a letter character
     */
    @WhatsAppWebExport(moduleName = "WAWebContactShortName", exports = "getShortName", adaptation = WhatsAppAdaptation.ADAPTED)
    static String deriveShortName(String fullName) {
        if (fullName == null || fullName.isEmpty()) {
            return ""; // ADAPTED: WA Web returns null, but caller coalesces to ""
        }
        var tokens = WHITESPACE_PATTERN.split(fullName, 2);
        var firstToken = tokens[0];
        if (firstToken.isEmpty()) {
            return ""; // ADAPTED: WA Web returns null, but caller coalesces to ""
        }
        if (!containsLetter(firstToken)) {
            return "";
        }
        return firstToken;
    }

    /**
     * Checks whether the given string contains at least one Unicode letter character.
     *
     * <p>This is equivalent to testing whether the WA Web {@code WAWebAlphaRegex}
     * regex (a comprehensive Unicode letter character class) matches any character
     * in the string. Java's {@code Character.isLetter()} covers the same Unicode
     * categories (L* general category), so a simple code-point scan is sufficient.
     * @param s the string to test
     * @return {@code true} if the string contains at least one Unicode letter
     */
    @WhatsAppWebExport(moduleName = "WAWebAlphaRegex", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    private static boolean containsLetter(String s) {
        return s.codePoints().anyMatch(Character::isLetter);
    }
}
