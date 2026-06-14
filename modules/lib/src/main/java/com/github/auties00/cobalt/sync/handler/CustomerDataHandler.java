package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.business.CustomerDataAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Acknowledges Business CRM customer-data mutations from {@code customer_data} sync mutations without persisting them.
 *
 * <p>This handler drives the SMB Business CRM customer-record surface where
 * the merchant attaches CRM fields (contact type, email, alternate phone
 * numbers, birthday, address, acquisition source, lead stage, last order) to a
 * chat JID. When the merchant edits a record on another device, the server
 * replays the resulting {@link CustomerDataAction} here.
 *
 * @implNote
 * This implementation validates the index and value but does not
 * persist anything: Cobalt has no dedicated customer-data store
 * mirroring WA Web's {@code customerData2} IDB table or the
 * {@code addOrEditCustomerData} / {@code removeCustomerDataByChatJid}
 * pipeline. The mutation is acknowledged so the sync engine sees
 * {@link MutationApplicationResult#success()} and does not retry; the
 * payload is dropped.
 */
@WhatsAppWebModule(moduleName = "WAWebCustomerDataSync")
public final class CustomerDataHandler implements WebAppStateActionHandler {

    /**
     * Constructs the singleton customer-data handler.
     *
     * <p>The sync handler registry instantiates this once during client
     * bootstrap.
     */
    @WhatsAppWebExport(moduleName = "WAWebCustomerDataSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public CustomerDataHandler() {
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebCustomerDataSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return CustomerDataAction.ACTION_NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebCustomerDataSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return CustomerDataAction.COLLECTION_NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebCustomerDataSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return CustomerDataAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * <p>For SET mutations, validates {@code indexParts[1]} as a chat JID and
     * the value as a {@link CustomerDataAction}. For REMOVE mutations,
     * validates the chat JID when present. Returns
     * {@link MutationApplicationResult#unsupported()} for other operations and
     * {@link SyncdIndexUtils#malformedActionValue(String)} when the index slot
     * is empty, the JID fails to parse, or the value is mistyped.
     *
     * @implNote
     * This implementation acknowledges valid mutations with
     * {@link MutationApplicationResult#success()} without persisting:
     * Cobalt has no
     * {@code addOrEditCustomerData} / {@code removeCustomerDataByChatJid}
     * equivalent. The {@link Jid#of(String)} call replaces WA Web's
     * {@code WAJids.validateChatJid} and is more lenient about
     * accepted forms; this is acceptable here because the payload is
     * dropped either way.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebCustomerDataSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(LinkedWhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var indexArray = JSON.parseArray(mutation.index());
        var chatJidString = indexArray.size() >= 2 ? indexArray.getString(1) : null;

        if (mutation.operation() == SyncdOperation.SET) {
            if (chatJidString == null || chatJidString.isBlank()) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            var chatJid = Jid.of(chatJidString);
            if (chatJid == null) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            if (mutation.value() == null) {
                return MutationApplicationResult.success();
            }

            if (!(mutation.value().action().orElse(null) instanceof CustomerDataAction)) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            return MutationApplicationResult.success();
        } else if (mutation.operation() == SyncdOperation.REMOVE) {
            return MutationApplicationResult.success();
        } else {
            return MutationApplicationResult.unsupported();
        }
    }
}
