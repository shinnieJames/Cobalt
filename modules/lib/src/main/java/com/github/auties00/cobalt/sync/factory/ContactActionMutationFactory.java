package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.contact.ContactAction;
import com.github.auties00.cobalt.model.sync.action.contact.ContactActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing contact-sync mutations.
 *
 * <p>The factory is the outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.ContactActionHandler}.
 */
public final class ContactActionMutationFactory {
    /**
     * Constructs a contact-action mutation factory.
     */
    public ContactActionMutationFactory() {

    }

    /**
     * Builds a pending mutation for syncing a contact to the server.
     *
     * <p>For {@code SET} operations (when {@code isDelete} is {@code false}), the mutation
     * includes the contact's full name, first name, LID JID, address book sync preference,
     * and username. For {@code REMOVE} operations (when {@code isDelete} is {@code true}),
     * only the operation type is set.
     *
     * <p>The contact JID is serialized in legacy format for the index, matching
     * WhatsApp Web's use of {@code e.toString({legacy: true})}.
     *
     * @param contactJid          the JID of the contact being synced
     * @param firstName           the contact's first name, or {@code null}
     * @param fullName            the contact's full name, or {@code null}
     * @param isDelete            whether this is a delete (REMOVE) operation
     * @param lid                 the contact's LID JID, or {@code null}
     * @param syncToAddressbook   whether to sync to primary address book, or {@code null}
     * @param username            the contact's username, or {@code null}
     * @return the pending mutation ready for submission to the sync pipeline
     */
    @WhatsAppWebExport(moduleName = "WAWebContactSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getContactSyncMutation(
            Jid contactJid,
            String firstName,
            String fullName,
            boolean isDelete,
            Jid lid,
            Boolean syncToAddressbook,
            String username
    ) {
        return getContactSyncMutation(contactJid, firstName, fullName, isDelete, lid, syncToAddressbook, username, Instant.now());
    }

    /**
     * Overload accepting an explicit {@link Instant} for the mutation
     * timestamp. The public seven-arg call delegates here with
     * {@link Instant#now()} so production callers behave unchanged; the
     * explicit overload exists so tests can pin a deterministic timestamp
     * and assert byte-equality against a captured WA Web oracle.
     *
     * @param contactJid          the JID of the contact
     * @param firstName           the contact's first name, or {@code null}
     * @param fullName            the contact's full name, or {@code null}
     * @param isDelete            whether this is a REMOVE operation
     * @param lid                 the contact's LID JID, or {@code null}
     * @param syncToAddressbook   whether to sync to the primary address book
     * @param username            the contact's username, or {@code null}
     * @param timestamp           the timestamp to seed into the action value
     *                            and the pending mutation
     * @return the pending mutation
     */
    public SyncPendingMutation getContactSyncMutation(
            Jid contactJid,
            String firstName,
            String fullName,
            boolean isDelete,
            Jid lid,
            Boolean syncToAddressbook,
            String username,
            Instant timestamp
    ) {
        var action = new ContactActionBuilder()
                .fullName(fullName)
                .firstName(firstName)
                .lidJid(lid)
                .saveOnPrimaryAddressbook(syncToAddressbook)
                .username(username)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .contactAction(action)
                .build();
        var operation = isDelete
                ? SyncdOperation.REMOVE
                : SyncdOperation.SET;
        // where indexArgs = [e.toString({legacy: true})] in WA Web.
        // ADAPTED: Cobalt uses Jid.toString() canonical form; WA Web's legacy form is not mirrored
        // because Cobalt normalizes JIDs to a single canonical representation.
        var index = JSON.toJSONString(List.of(ContactAction.ACTION_NAME, contactJid.toString()));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                operation,
                timestamp,
                ContactAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0);
    }
}
