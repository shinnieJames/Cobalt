package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
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
 * Handles customer data (CRM) actions.
 *
 * <p>Per WhatsApp Web {@code WAWebCustomerDataSync}, this handler processes
 * mutations for business customer relationship data associated with chat JIDs.
 *
 * <p>Index format: {@code ["customer_data", chatJid]}
 */
@WhatsAppWebModule(moduleName = "WAWebCustomerDataSync")
public final class CustomerDataHandler implements WebAppStateActionHandler {

    /**
     * Creates a new {@code CustomerDataHandler}.
     */
    @WhatsAppWebExport(moduleName = "WAWebCustomerDataSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public CustomerDataHandler() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebCustomerDataSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return CustomerDataAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebCustomerDataSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return CustomerDataAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebCustomerDataSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return CustomerDataAction.ACTION_VERSION;
    }

    /**
     * Applies a customer data mutation and returns a detailed result.
     *
     * <p>Per WhatsApp Web {@code WAWebCustomerDataSync.applyMutations}, each mutation
     * is processed as follows:
     * <ul>
     *   <li><b>SET:</b> validates chatJid from index, validates the customerDataAction
     *       field on the value, then stores the customer data record via
     *       {@code $CustomerDataSync$p_1}.</li>
     *   <li><b>REMOVE:</b> if chatJid is present and valid, removes the customer data
     *       record via {@code $CustomerDataSync$p_2}; otherwise silently succeeds.</li>
     *   <li><b>Unknown:</b> returns unsupported.</li>
     * </ul>
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebCustomerDataSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var indexArray = JSON.parseArray(mutation.index());
        var chatJidString = indexArray.size() >= 2 ? indexArray.getString(1) : null;

        if (mutation.operation() == SyncdOperation.SET) {
            if (chatJidString == null || chatJidString.isBlank()) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            var chatJid = Jid.of(chatJidString); // ADAPTED: WAWebCustomerDataSync.applyMutations — validateChatJid(u); Cobalt uses Jid.of which is more lenient than validateChatJid
            if (chatJid == null) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            if (mutation.value() == null) {
                return MutationApplicationResult.success();
            }

            if (!(mutation.value().action().orElse(null) instanceof CustomerDataAction)) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            // ADAPTED: WAWebCustomerDataSync.$CustomerDataSync$p_1 — addOrEditCustomerData + frontendFireAndForget
            // Cobalt does not have a dedicated customer data store; the action is acknowledged
            return MutationApplicationResult.success();
        } else if (mutation.operation() == SyncdOperation.REMOVE) {
            if (chatJidString != null && !chatJidString.isBlank()) {
                // ADAPTED: WAWebCustomerDataSync.$CustomerDataSync$p_2 — removeCustomerDataByChatJid + frontendFireAndForget
                // Cobalt does not have a dedicated customer data store; valid JID removal is acknowledged
            }
            return MutationApplicationResult.success();
        } else {
            return MutationApplicationResult.unsupported();
        }
    }
}
