package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.chat.PnForLidChatAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Applies the {@code pnForLidChat} app-state action that registers a
 * bidirectional phone number / LID mapping for a chat.
 *
 * @apiNote
 * Drives the phone-number-hiding (PNH) migration: each mutation
 * carries a {@code (lidJid, pnJid)} pair so all linked devices learn
 * the phone-number JID associated with a previously-LID-only chat.
 * Gated on {@link ABProp#PNH_PN_FOR_LID_CHAT_SYNC}; non-opted-in
 * accounts surface every mutation as
 * {@link MutationApplicationResult#unsupported()}. The mutation index
 * keys each entry by the LID, formatted as
 * {@snippet :
 *     ["pnForLidChat", lidJid]
 * }
 *
 * @implNote
 * This implementation registers each mapping eagerly via
 * {@code WhatsAppStore.registerLidMapping(pnJid, lidJid)} as the
 * mutations arrive, rather than batching them and calling
 * {@code WAWebDBCreateLidPnMappings.createLidPnMappings({mappings,
 * flushImmediately: true, learningSource: "other"})} once at the end
 * of the batch as WA Web does; the typed Cobalt store is the sole
 * source of truth so the per-mutation register call is semantically
 * equivalent. WA Web's
 * {@code WAWebWidFactory.createUserLidOrThrow}/{@code createUserWidOrThrow}
 * pair is collapsed into {@link Jid#hasLidServer()} and the action's
 * {@code pnJid} accessor; an unparseable or non-LID index is reported
 * as {@link SyncdIndexUtils#malformedActionIndex(String, String)}
 * (mirroring WA Web's combined {@code isWidlike} +
 * {@code createUserLidOrThrow} guard) and a missing
 * {@link PnForLidChatAction#pnJid()} as
 * {@link SyncdIndexUtils#malformedActionValue(String)}.
 */
@WhatsAppWebModule(moduleName = "WAWebPnForLidChatSync")
public final class PnForLidChatHandler implements WebAppStateActionHandler {
    /**
     * The AB-props service consulted before applying any mutation.
     *
     * @apiNote
     * Internal collaborator injected at construction; never accessed
     * outside {@link #applyMutation(WhatsAppClient, DecryptedMutation.Trusted)}.
     */
    private final ABPropsService abPropsService;

    /**
     * Constructs the PN-for-LID-chat sync handler bound to the given
     * AB-props service.
     *
     * @apiNote
     * Used by the sync handler registry; the AB-props service is
     * consulted on every mutation to honour the
     * {@link ABProp#PNH_PN_FOR_LID_CHAT_SYNC} gate.
     *
     * @implNote
     * This implementation mirrors the WA Web {@code WAWebPnForLidChatSync}
     * constructor: it sets {@code collectionName = Regular} on the
     * prototype and inherits from {@code AccountSyncdActionBase}.
     *
     * @param abPropsService the AB-props service consulted on every
     *                       mutation
     */
    @WhatsAppWebExport(moduleName = "WAWebPnForLidChatSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public PnForLidChatHandler(ABPropsService abPropsService) {
        this.abPropsService = abPropsService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPnForLidChatSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return PnForLidChatAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPnForLidChatSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return PnForLidChatAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPnForLidChatSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return PnForLidChatAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation walks the per-mutation arms of WA Web's
     * {@code WAWebPnForLidChatSync.applyMutations} in this order:
     * <ol>
     *   <li>the {@link ABProp#PNH_PN_FOR_LID_CHAT_SYNC} gate;</li>
     *   <li>{@link SyncdOperation#SET};</li>
     *   <li>a non-empty {@code indexParts[1]} (mirroring WA Web's
     *       {@code isWidlike(indexParts[1])});</li>
     *   <li>a {@link PnForLidChatAction} payload with a non-{@code null}
     *       {@code pnJid} (mirroring
     *       {@code isWidlike(pnForLidChatAction.pnJid)});</li>
     *   <li>the index JID must be a LID
     *       ({@link Jid#hasLidServer()}, mirroring WA Web's
     *       {@code createUserLidOrThrow}).</li>
     * </ol>
     * On success the bidirectional pair is registered eagerly via
     * {@code WhatsAppStore.registerLidMapping}; the
     * {@code WAWebDBCreateLidPnMappings.createLidPnMappings} batch
     * flush, the per-batch {@code WALogger.WARN} counters, and the
     * frontend-only {@code bulkUpdatePhoneNumberJids} dispatch are
     * intentionally not replicated.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebPnForLidChatSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (!abPropsService.getBool(ABProp.PNH_PN_FOR_LID_CHAT_SYNC)) {
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

        if (!(mutation.value().action().orElse(null) instanceof PnForLidChatAction action)) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        var pnJid = action.pnJid().orElse(null);
        if (pnJid == null) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        var lidJid = Jid.of(lidJidString);
        if (!lidJid.hasLidServer()) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        client.store().registerLidMapping(pnJid, lidJid);
        return MutationApplicationResult.success();
    }
}
