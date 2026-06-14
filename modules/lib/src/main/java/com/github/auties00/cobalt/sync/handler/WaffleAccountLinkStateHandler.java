package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.message.send.id.MessageIdGenerator;
import com.github.auties00.cobalt.message.send.id.MessageIdVersion;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.message.MessageContainerBuilder;
import com.github.auties00.cobalt.model.message.system.ProtocolMessage;
import com.github.auties00.cobalt.model.message.system.ProtocolMessageBuilder;
import com.github.auties00.cobalt.model.message.system.peer.PeerDataOperationRequestMessageBuilder;
import com.github.auties00.cobalt.model.message.system.peer.PeerDataOperationRequestType;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.device.WaffleAccountLinkStateAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.model.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.wam.event.NonMessagePeerDataRequestEventBuilder;
import com.github.auties00.cobalt.wam.type.PeerDataRequestType;
import com.github.auties00.cobalt.wam.WamService;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors the WAFFLE (WhatsApp - Meta Account) account-linking state and triggers a primary-device
 * nonce fetch when the link becomes active.
 *
 * <p>The sync dispatcher routes incoming {@code waffle_account_link_state} mutations here whenever
 * the user's Meta-account linking state changes on another linked device (typical trigger: linking
 * or unlinking the WhatsApp account to a Meta account from the phone). The handler persists the
 * {@link WaffleAccountLinkStateAction.AccountLinkState} on the store and, when the state becomes
 * {@link WaffleAccountLinkStateAction.AccountLinkState#ACTIVE}, sends a
 * {@link PeerDataOperationRequestType#WAFFLE_LINKING_NONCE_FETCH} peer message so the primary device
 * delivers a fresh linking nonce.
 */
@WhatsAppWebModule(moduleName = "WAWebWaffleAccountLinkStateSync")
public final class WaffleAccountLinkStateHandler implements WebAppStateActionHandler {
    /**
     * The AB-props service consulted on every mutation to enforce the {@link ABProp#WEB_WAFFLE}
     * gate.
     */
    private final ABPropsService abPropsService;

    /**
     * The WAM service used to commit the peer-data-request telemetry event when triggering a
     * linking nonce fetch.
     */
    private final WamService wamService;

    /**
     * Constructs the handler with its injected dependencies.
     *
     * <p>The handler is stateful only through the injected {@link ABPropsService} and
     * {@link WamService}; Cobalt's sync registry holds a single instance per client.
     *
     * @param abPropsService the AB-props service consulted on every mutation
     * @param wamService     the WAM service used to commit the nonce-fetch event
     */
    @WhatsAppWebExport(moduleName = "WAWebWaffleAccountLinkStateSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public WaffleAccountLinkStateHandler(ABPropsService abPropsService, WamService wamService) {
        this.abPropsService = abPropsService;
        this.wamService = wamService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebWaffleAccountLinkStateSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return WaffleAccountLinkStateAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebWaffleAccountLinkStateSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return WaffleAccountLinkStateAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebWaffleAccountLinkStateSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return WaffleAccountLinkStateAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * <p>When {@link ABProp#WEB_WAFFLE} is disabled every mutation is
     * {@link MutationApplicationResult#unsupported()} without inspection. Otherwise non-{@link SyncdOperation#SET}
     * operations are unsupported, mutations whose decoded value is not a
     * {@link WaffleAccountLinkStateAction} or whose {@link WaffleAccountLinkStateAction#linkState()}
     * is empty are malformed, and the remaining {@code SET} mutations contribute to the running
     * latest-timestamp tracker. After the per-mutation pass the latest mutation's link state and
     * timestamp are persisted via
     * {@link com.github.auties00.cobalt.store.AccountStore#setLinkedMetaAccountState(WaffleAccountLinkStateAction.AccountLinkState)}
     * and
     * {@link com.github.auties00.cobalt.store.AccountStore#setLinkedMetaAccountStateTimestamp(java.time.Instant)},
     * and an {@link WaffleAccountLinkStateAction.AccountLinkState#ACTIVE} state triggers
     * {@link #requestNonceFromPrimary(LinkedWhatsAppClient)}.
     *
     * @implNote
     * This implementation drops WA Web's per-mutation warning counters and "already Active" no-op
     * log as telemetry; Cobalt's store layer absorbs WA Web's account-linking-state record.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebWaffleAccountLinkStateSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public List<MutationApplicationResult> applyMutationBatch(LinkedWhatsAppClient client, List<DecryptedMutation.Trusted> mutations) {
        var accountLinkingEnabled = abPropsService.getBool(ABProp.WEB_WAFFLE);
        DecryptedMutation.Trusted latest = null;
        var results = new ArrayList<MutationApplicationResult>(mutations.size());
        for (var mutation : mutations) {
            if (!accountLinkingEnabled) {
                results.add(MutationApplicationResult.unsupported());
                continue;
            }

            if (mutation.operation() != SyncdOperation.SET) {
                results.add(MutationApplicationResult.unsupported());
                continue;
            }

            if (!(mutation.value().action().orElse(null) instanceof WaffleAccountLinkStateAction action)
                    || action.linkState().isEmpty()) {
                results.add(SyncdIndexUtils.malformedActionValue(collectionName().name()));
                continue;
            }

            if (latest == null || mutation.timestamp().compareTo(latest.timestamp()) > 0) {
                latest = mutation;
            }
            results.add(MutationApplicationResult.success());
        }
        if (latest != null) {
            var action = (WaffleAccountLinkStateAction) latest.value().action().orElseThrow();
            var linkState = action.linkState().orElseThrow();
            client.store().accountStore().setLinkedMetaAccountState(linkState);
            client.store().accountStore().setLinkedMetaAccountStateTimestamp(latest.timestamp());
            if (linkState == WaffleAccountLinkStateAction.AccountLinkState.ACTIVE) {
                requestNonceFromPrimary(client);
            }
        }

        return results;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This is the single-mutation adapter and applies the same gate-and-validate-and-persist
     * sequence as the batch entry point on a list of size one. The
     * {@link #requestNonceFromPrimary(LinkedWhatsAppClient)} side-effect runs inline when the resolved
     * state is {@link WaffleAccountLinkStateAction.AccountLinkState#ACTIVE}.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebWaffleAccountLinkStateSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(LinkedWhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (!abPropsService.getBool(ABProp.WEB_WAFFLE)) {
            return MutationApplicationResult.unsupported();
        }

        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof WaffleAccountLinkStateAction action)
                || action.linkState().isEmpty()) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        var linkState = action.linkState().orElseThrow();
        client.store().accountStore().setLinkedMetaAccountState(linkState);
        client.store().accountStore().setLinkedMetaAccountStateTimestamp(mutation.timestamp());
        if (linkState == WaffleAccountLinkStateAction.AccountLinkState.ACTIVE) {
            requestNonceFromPrimary(client);
        }
        return MutationApplicationResult.success();
    }

    /**
     * Sends a WAFFLE linking-nonce-fetch peer data operation request to the primary device.
     *
     * <p>Called when the resolved link state becomes
     * {@link WaffleAccountLinkStateAction.AccountLinkState#ACTIVE}; the primary device responds with
     * a fresh WAFFLE linking nonce that the companion needs to complete the linking handshake with
     * Meta's servers. A {@link PeerDataOperationRequestType#WAFFLE_LINKING_NONCE_FETCH} request is
     * wrapped in a {@link ProtocolMessage.Type#PEER_DATA_OPERATION_REQUEST_MESSAGE} and dispatched
     * to the current account on device 0, and a corresponding peer-data-request event is committed
     * through the {@link WamService}. The method returns without effect when the store has no
     * current JID.
     *
     * @implNote
     * This implementation rebuilds the peer protocol message directly rather than going through WA
     * Web's nonce-fetch API delegation chain. The in-flight promise memoization performed by WA Web
     * is dropped because Cobalt's sync pipeline already serializes mutations on a virtual thread.
     *
     * @param client the {@link LinkedWhatsAppClient} used to dispatch the peer message
     */
    @WhatsAppWebExport(moduleName = "WAWebAccountLinkingNonceFetchAPI", exports = "requestNonceFromPrimary", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSendNonMessageDataRequest", exports = "sendPeerDataOperationRequest", adaptation = WhatsAppAdaptation.ADAPTED)
    private void requestNonceFromPrimary(LinkedWhatsAppClient client) {
        var me = client.store().accountStore().jid().orElse(null);
        if (me == null) {
            return;
        }

        var request = new PeerDataOperationRequestMessageBuilder()
                .peerDataOperationRequestType(PeerDataOperationRequestType.WAFFLE_LINKING_NONCE_FETCH)
                .build();
        var protocol = new ProtocolMessageBuilder()
                .type(ProtocolMessage.Type.PEER_DATA_OPERATION_REQUEST_MESSAGE)
                .peerDataOperationRequestMessage(request)
                .build();
        var container = new MessageContainerBuilder()
                .protocolMessage(protocol)
                .build();
        var sessionId = MessageIdGenerator.generate(MessageIdVersion.V2, me);
        this.wamService.commit(new NonMessagePeerDataRequestEventBuilder()
                .peerDataRequestCount(1)
                .peerDataRequestType(PeerDataRequestType.WAFFLE_LINKING_NONCE_FETCH)
                .peerDataRequestSessionId(sessionId)
                .build());
        client.sendMessage(me.withDevice(0), container);
    }
}
