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
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Records that a LID-identified contact may now see the local user's phone
 * number.
 *
 * @apiNote
 * Cobalt embedders never invoke this handler directly; the sync dispatcher
 * routes incoming {@code shareOwnPn} mutations here whenever the user
 * promotes a LID-only contact to phone-number-visible (typical trigger: a
 * primary-device prompt that asks "share your phone number with this
 * contact?"). The handler flips
 * {@link com.github.auties00.cobalt.model.contact.Contact#phoneNumberShared()}
 * on the matching contact, upserting the contact when it is not yet in the
 * local store.
 */
@WhatsAppWebModule(moduleName = "WAWebShareOwnPnSync")
public final class ShareOwnPnHandler implements WebAppStateActionHandler {
    /**
     * The AB-props service consulted before applying any mutation.
     */
    private final ABPropsService abPropsService;

    /**
     * Constructs the handler.
     *
     * @apiNote
     * The handler is stateful only through the injected
     * {@link ABPropsService}; Cobalt's sync registry holds a single
     * instance per client.
     *
     * @param abPropsService the AB-props service consulted on every mutation
     */
    @WhatsAppWebExport(moduleName = "WAWebShareOwnPnSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public ShareOwnPnHandler(ABPropsService abPropsService) {
        this.abPropsService = abPropsService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebShareOwnPnSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return "shareOwnPn";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebShareOwnPnSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebShareOwnPnSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return 8;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation mirrors WA Web's per-mutation closure inside
     * {@code WAWebShareOwnPnSync.applyMutations}: it gates on
     * {@link ABProp#SHARE_OWN_PN_SYNC}, requires a {@link SyncdOperation#SET},
     * extracts {@code indexParts[1]} as a wid-like string, parses it into a
     * {@link Jid} that must carry the LID server, then upserts the LID
     * contact with {@code phoneNumberShared = true}. WA Web accumulates
     * valid entries across the whole batch and flushes them once via
     * {@code WAWebUpdateLidMetadataJob.updateLidMetadataJob -> WAWebApiContact.updateLidMetadata -> WAWebLidAwareContactsDB.bulkCreateOrMerge};
     * Cobalt collapses that pipeline into a direct per-mutation contact
     * update because the unified {@code WhatsAppStore} is itself the
     * source of truth and there is no IDB layer to batch. The frontend
     * {@code bulkUpdateLidContactState} mirror and the
     * {@code WALogger.WARN} counters are omitted as telemetry.
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

        var indexArray = JSON.parseArray(mutation.index());
        var lidJidString = indexArray != null && indexArray.size() > 1 ? indexArray.getString(1) : null;
        if (lidJidString == null || lidJidString.isEmpty()) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        var lidJid = Jid.of(lidJidString);
        if (!lidJid.hasLidServer()) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        var contact = client.store()
                .findContactByJid(lidJid)
                .orElseGet(() -> client.store().addNewContact(lidJid));
        contact.setPhoneNumberShared(true);
        return MutationApplicationResult.success();
    }
}
