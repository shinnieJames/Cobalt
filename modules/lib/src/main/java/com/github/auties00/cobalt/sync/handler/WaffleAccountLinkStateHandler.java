package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
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
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import com.github.auties00.cobalt.wam.event.NonMessagePeerDataRequestEventBuilder;
import com.github.auties00.cobalt.wam.type.PeerDataRequestType;
import com.github.auties00.cobalt.wam.WamService;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles waffle account link state sync actions.
 *
 * <p>Per WhatsApp Web {@code WAWebWaffleAccountLinkStateSync}, this handler
 * processes the {@code "waffle_account_link_state"} sync action in the
 * {@code RegularHigh} collection at version {@code 1}. The handler is gated by
 * the {@code web_waffle} AB prop ({@code WAWebAccountLinkingGatingUtils.accountLinkingEnabled}):
 * when disabled, all mutations are acknowledged as {@code UNSUPPORTED} without
 * inspection.
 *
 * <p>Only {@code SET} operations are supported. On {@code SET}, the handler
 * validates that {@code waffleAccountLinkStateAction.linkState} is non-{@code null}
 * and, when processing a batch, applies only the latest mutation by
 * {@code value.timestamp} to the local store. After persisting an
 * {@code Active} link state, the handler triggers a primary-device WAFFLE
 * linking nonce fetch via a peer data operation request.
 *
 * <p>Index format: {@code ["waffle_account_link_state"]}
 */
@WhatsAppWebModule(moduleName = "WAWebWaffleAccountLinkStateSync")
public final class WaffleAccountLinkStateHandler implements WebAppStateActionHandler {
    /**
     * The AB-props service consulted on every mutation to enforce the
     * {@code web_waffle} gate.
     */
    private final ABPropsService abPropsService;

    /**
     * The WAM telemetry service used to commit the non-message peer data
     * request event when triggering a WAFFLE linking nonce fetch.
     */
    private final WamService wamService;

    /**
     * Constructs a {@code WaffleAccountLinkStateHandler}.
     *
     * @param abPropsService the AB-props service consulted on every
     *                       mutation
     * @param wamService     the WAM telemetry service used by this
     *                       handler
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
     * <p>Per WhatsApp Web {@code WAWebWaffleAccountLinkStateSync.applyMutations}:
     * <ol>
     *   <li>If {@code WAWebAccountLinkingGatingUtils.accountLinkingEnabled()}
     *       returns {@code false}, every mutation is acknowledged as
     *       {@code UNSUPPORTED} and no store work is performed.</li>
     *   <li>Otherwise, each mutation is mapped to a per-mutation
     *       {@link MutationApplicationResult}: non-{@code SET} mutations are
     *       acknowledged as {@code UNSUPPORTED}; mutations whose decoded value
     *       is not a {@link WaffleAccountLinkStateAction} or whose
     *       {@code linkState} is {@code null} are acknowledged as
     *       {@code MALFORMED}.</li>
     *   <li>While mapping, the mutation with the highest {@code value.timestamp}
     *       among the valid {@code SET} mutations is tracked.</li>
     *   <li>After mapping, the latest valid mutation's link state and
     *       timestamp are persisted via
     *       {@code createOrUpdateAccountLinkingState}, and if the persisted
     *       link state is {@code Active} the handler triggers
     *       {@code requestNonceFromPrimary} to fetch a fresh waffle linking
     *       nonce from the primary device.</li>
     * </ol>
     *
     * <p>WA Web also emits {@code WALogger.WARN} entries with the unsupported
     * and malformed mutation counts; these telemetry warnings are intentionally
     * omitted in Cobalt and the return semantics are preserved exactly.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebWaffleAccountLinkStateSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public List<MutationApplicationResult> applyMutationBatch(WhatsAppClient client, List<DecryptedMutation.Trusted> mutations) {
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
            client.store().setLinkedMetaAccountState(linkState); // ADAPTED: WAWebAccountLinkingDBOperations_DO_NOT_USE_DIRECTLY.createOrUpdateAccountLinkingState — Cobalt flattens the account-linking record into store fields
            client.store().setLinkedMetaAccountStateTimestamp(latest.timestamp()); // ADAPTED: createOrUpdateAccountLinkingState linkTimestamp field
            if (linkState == WaffleAccountLinkStateAction.AccountLinkState.ACTIVE) {
                requestNonceFromPrimary(client);
            }
        }

        return results;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Single-mutation adapter that mirrors the WhatsApp Web batch logic for
     * a list of size one. The {@code web_waffle} AB prop is checked first;
     * when disabled, the result is {@code UNSUPPORTED}. A non-{@code SET}
     * mutation yields {@code UNSUPPORTED}; a mutation whose decoded value is
     * not a {@link WaffleAccountLinkStateAction} or whose {@code linkState} is
     * {@code null} yields {@code MALFORMED}; otherwise the link state and
     * timestamp are persisted to the store and {@code SUCCESS} is returned.
     * If the persisted link state is {@code Active}, the handler also triggers
     * {@code requestNonceFromPrimary}.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebWaffleAccountLinkStateSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
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
        client.store().setLinkedMetaAccountState(linkState); // ADAPTED: WAWebAccountLinkingDBOperations_DO_NOT_USE_DIRECTLY.createOrUpdateAccountLinkingState — Cobalt flattens the account-linking record into store fields
        client.store().setLinkedMetaAccountStateTimestamp(mutation.timestamp()); // ADAPTED: createOrUpdateAccountLinkingState linkTimestamp field
        if (linkState == WaffleAccountLinkStateAction.AccountLinkState.ACTIVE) {
            requestNonceFromPrimary(client);
        }
        return MutationApplicationResult.success();
    }

    /**
     * Sends a {@code WAFFLE_LINKING_NONCE_FETCH} peer data operation request to
     * the primary device.
     *
     * <p>Per WhatsApp Web {@code WAWebAccountLinkingNonceFetchAPI.requestNonceFromPrimary}:
     * delegates to {@code WAWebSendNonMessageDataRequest.sendPeerDataOperationRequest}
     * with the {@code WAFFLE_LINKING_NONCE_FETCH} request type and an empty
     * payload. The send pipeline constructs a single peer protocol message of
     * subtype {@code "peer_data_operation_request_message"} addressed to the
     * primary device (own user, device {@code 0}) and dispatches it via the
     * non-message data request flow.
     *
     * <p>WA Web memoizes the in-flight promise so that concurrent calls share
     * the same request and the cache is cleared in {@code finally}; that
     * micro-optimization is intentionally omitted here because sync mutations
     * are processed sequentially in Cobalt.
     *
     * @param client the WhatsApp client used to dispatch the peer message
     */
    @WhatsAppWebExport(moduleName = "WAWebAccountLinkingNonceFetchAPI", exports = "requestNonceFromPrimary", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebSendNonMessageDataRequest", exports = "sendPeerDataOperationRequest", adaptation = WhatsAppAdaptation.ADAPTED)
    private void requestNonceFromPrimary(WhatsAppClient client) {
        var me = client.store().jid().orElse(null); // ADAPTED: WAWebSendNonMessageDataRequest.D: getMePnUserOrThrow_DO_NOT_USE() / getMeDevicePnOrThrow_DO_NOT_USE()
        if (me == null) {
            return; // ADAPTED: defensive guard against missing own JID; WA Web throws via getMePnUserOrThrow_DO_NOT_USE
        }

        var request = new PeerDataOperationRequestMessageBuilder()
                .peerDataOperationRequestType(PeerDataOperationRequestType.WAFFLE_LINKING_NONCE_FETCH)
                .build();
        var protocol = new ProtocolMessageBuilder() // ADAPTED: WAWebSendNonMessageDataRequest wraps in protocol msg via send pipeline
                .type(ProtocolMessage.Type.PEER_DATA_OPERATION_REQUEST_MESSAGE)
                .peerDataOperationRequestMessage(request)
                .build();
        var container = new MessageContainerBuilder() // ADAPTED: WAWebSendNonMessageDataRequest wraps in message container via send pipeline
                .protocolMessage(protocol)
                .build();
        // fanout message in WAWebSendNonMessageDataRequest.sendPeerDataOperationRequest. For
        // WAFFLE_LINKING_NONCE_FETCH WAWebNonMessageDataRequestLoggingUtils.d returns 1,
        // and peerDataRequestSessionId is the outbound peer message key id (t.id.id).
        var sessionId = MessageIdGenerator.generate(MessageIdVersion.V2, me);
        this.wamService.commit(new NonMessagePeerDataRequestEventBuilder()
                .peerDataRequestCount(1)
                .peerDataRequestType(PeerDataRequestType.WAFFLE_LINKING_NONCE_FETCH)
                .peerDataRequestSessionId(sessionId)
                .build());
        client.sendMessage(me.withDevice(0), container);
    }
}
