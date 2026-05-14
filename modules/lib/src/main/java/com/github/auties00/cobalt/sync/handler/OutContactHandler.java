package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.contact.OutContactBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.contact.OutContactAction;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import java.util.logging.Logger;

/**
 * Handles outgoing contact sync actions from the {@code regular_low} collection.
 *
 * <p>This handler processes mutations that synchronize outgoing contact entries —
 * contact records used by the "invite by contact" feature on WhatsApp Web for macOS.
 * An outgoing contact is a locally stored reference to a phone-number-identified
 * person used to invite new users to WhatsApp. Each record stores only the contact's
 * JID, full name and first name.
 *
 * <p>It processes both {@code SET} operations (create or update an outgoing contact)
 * and {@code REMOVE} operations (delete an outgoing contact entry). Operations other
 * than {@code SET} and {@code REMOVE} are treated as malformed.
 *
 * <p>Index format: {@code ["outContact", userJid]}.
 *
 * <p>Per WhatsApp Web {@code WAWebOutContactInviteGating.isOutContactInviteEnabled},
 * this handler is gated behind the {@code out_contact_invites_enabled} AB prop
 * (code 28170, int). When the gate is closed, every mutation in the batch is
 * reported as {@link MutationApplicationResult#unsupported()}.
 */
@WhatsAppWebModule(moduleName = "WAWebOutContactSync")
public final class OutContactHandler implements WebAppStateActionHandler {
    /**
     * Logger for diagnostic messages emitted during out-contact sync processing.
     */
    private static final Logger LOGGER = Logger.getLogger(OutContactHandler.class.getName());

    /**
     * The AB-props service consulted before applying any mutation.
     */
    private final ABPropsService abPropsService;

    /**
     * Value of the {@code out_contact_invites_enabled} AB prop that enables the
     * outgoing-contact invite flow.
     */
    private static final int OUT_CONTACT_INVITES_ENABLED_VALUE = 1;

    /**
     * Constructs the handler instance bound to the given AB-props
     * service.
     *
     * @param abPropsService the AB-props service consulted on every
     *                       mutation
     */
    @WhatsAppWebExport(moduleName = "WAWebOutContactSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public OutContactHandler(ABPropsService abPropsService) {
        this.abPropsService = abPropsService;
    }

    /**
     * Returns the action name for this handler.
     * @return the action name string
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebOutContactSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return OutContactAction.ACTION_NAME;
    }

    /**
     * Returns the sync collection this handler belongs to.
     * @return the sync patch type
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebOutContactSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return OutContactAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for this handler.
     * @return the version number
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebOutContactSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return OutContactAction.ACTION_VERSION;
    }

    /**
     * Applies a single out-contact mutation and returns a detailed result.
     *
     * <p>Gated on {@code out_contact_invites_enabled}: when the gate is closed the
     * handler returns {@link MutationApplicationResult#unsupported()} for every
     * mutation, mirroring WA Web's early return of {@code {actionState: Unsupported}}
     * for every entry in the batch.
     *
     * <p>The JID at {@code indexParts[1]} is validated: per WA Web, it must resolve to a
     * {@code phoneUser} JID (i.e. {@code hasUserServer() == true}). Any other JID type
     * is logged and treated as malformed.
     *
     * <p>For {@code SET} operations, this method:
     * <ul>
     *   <li>Validates the mutation value is an {@link OutContactAction}</li>
     *   <li>Extracts {@code fullName} and {@code firstName}; empty strings are
     *       coalesced to {@code null} mirroring WA Web's {@code m(e)} helper</li>
     *   <li>When {@code firstName} is absent, derives it from the first
     *       whitespace-separated token of {@code fullName} mirroring WA Web's
     *       {@code p(e)} helper</li>
     *   <li>Writes the resulting record to the dedicated outgoing contact
     *       store on {@link com.github.auties00.cobalt.store.WhatsAppStore},
     *       which mirrors WA Web's separate {@code out-contact} IndexedDB
     *       table</li>
     * </ul>
     *
     * <p>For {@code REMOVE} operations, this method removes the record keyed by the
     * validated phone-user JID.
     *
     * <p>Anything other than {@code SET} or {@code REMOVE} is reported as
     * malformed, matching WA Web's {@code else} arm which increments the malformed
     * counter and pushes {@code malformedActionValue(collectionName)}.
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebOutContactSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        //     return t.map(function() { return {actionState: Unsupported} })
        var gateValue = abPropsService.getInt(ABProp.OUT_CONTACT_INVITES_ENABLED);
        if (gateValue != OUT_CONTACT_INVITES_ENABLED_VALUE) {
            return MutationApplicationResult.unsupported();
        }

        var indexArray = JSON.parseArray(mutation.index());
        // WAWebOutContactSync.applyMutations: var d=l.indexParts[1]; if(d==null) return malformedActionValue(...).
        // indexParts[1] is undefined when missing; mirror with explicit size check before getString.
        if (indexArray.size() <= 1) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }
        var userJidString = indexArray.getString(1);
        if (userJidString == null) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        Jid userJid;
        try {
            userJid = Jid.of(userJidString);
        } catch (Exception e) { // ADAPTED: WAWebOutContactSync.applyMutations — WAJids.interpretAndValidateJid returns {jidType: "unknown"} instead of throwing; Cobalt's Jid.of throws WhatsAppMalformedJidException on unparsable strings
            LOGGER.fine(() -> "OutContactSync: malformed JID: " + userJidString); // ADAPTED: WAWebOutContactSync.applyMutations — WALogger.ERROR("OutContactSync: JID missing expected domain: %s", d)
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        if (!userJid.hasUserServer()) {
            LOGGER.fine(() -> "OutContactSync: JID missing expected domain: " + userJidString);
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        return switch (mutation.operation()) {
            case SET -> {
                if (!(mutation.value().action().orElse(null) instanceof OutContactAction action)) {
                    yield SyncdIndexUtils.malformedActionValue(collectionName().name());
                }

                var fullName = coalesceEmpty(action.fullName().orElse(null));
                var explicitFirstName = coalesceEmpty(action.firstName().orElse(null));
                var firstName = explicitFirstName != null ? explicitFirstName : deriveFirstWord(fullName);

                // `out-contact` IndexedDB table (see WAWebSchemaOutContact). Cobalt now
                // owns a parallel OutContact store on AbstractWhatsAppStore, so the
                // record is written there directly rather than mirrored onto the
                // shared Contact collection — preserving the (id, fullName, firstName)
                // tuple WA Web relies on when rendering the invite-by-contact flow.
                var outContact = new OutContactBuilder()
                        .jid(userJid)
                        .fullName(fullName)
                        .firstName(firstName)
                        .build();
                client.store().addOutContact(outContact);

                LOGGER.fine(() -> "OutContactSync: set " + userJidString); // ADAPTED: WAWebOutContactSync.applyMutations — WALogger.LOG replaced with j.u.l FINE

                // SKIPPED: WA Web dispatches an IPC notification to its Electron frontend so
                // the macOS UI can refresh the invite-by-contact list. Cobalt has no such
                // frontend bridge and the contact mutation above is sufficient to keep the
                // in-memory store consistent.
                yield MutationApplicationResult.success();
            }
            case REMOVE -> {
                // the record from the dedicated outContacts store rather than the shared
                // Contact collection, preserving address-book entries that happen to
                // share the same phone JID.
                client.store().removeOutContact(userJid);

                LOGGER.fine(() -> "OutContactSync: remove " + userJidString); // ADAPTED: WAWebOutContactSync.applyMutations — WALogger.LOG replaced with j.u.l FINE

                // SKIPPED: Electron-frontend IPC notification; no Cobalt equivalent.
                yield MutationApplicationResult.success();
            }
            default -> {
                // SyncdOperation only has SET and REMOVE today, but mirroring WA Web's
                // catch-all branch keeps the handler structurally aligned with the batch
                // generator.
                yield SyncdIndexUtils.malformedActionValue(collectionName().name());
            }
        };
    }

    /**
     * Coalesces a {@code null} or empty string into {@code null}.
     *
     * <p>This mirrors WA Web's inline helper {@code m(e)}:
     * {@code return e == null || e === "" ? null : e}. It is used to normalise
     * {@link OutContactAction#fullName()} and {@link OutContactAction#firstName()}
     * before deciding whether to derive a fallback or skip the field entirely.
     * @param value the value to normalise, possibly {@code null}
     * @return {@code null} if the input is {@code null} or empty, otherwise the
     *         original value unchanged
     */
    private static String coalesceEmpty(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return value;
    }

    /**
     * Returns the first whitespace-separated token of the given string, trimmed,
     * or {@code null} if no non-empty token can be produced.
     *
     * <p>This mirrors WA Web's inline helper {@code p(e)}:
     * {@code var t = e.trim().split(" ")[0]; return t || null;}. WA Web splits on
     * the literal ASCII space character {@code " "} (rather than any Unicode
     * whitespace), so this implementation uses the same delimiter to preserve
     * exact parity with the source.
     * @param value the value to extract the first word from, possibly {@code null}
     * @return the first whitespace-delimited token of the trimmed value, or
     *         {@code null} when the input is {@code null} or empty after trimming
     */
    private static String deriveFirstWord(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        var spaceIndex = trimmed.indexOf(' ');
        var firstToken = spaceIndex == -1 ? trimmed : trimmed.substring(0, spaceIndex);
        return firstToken.isEmpty() ? null : firstToken;
    }
}
