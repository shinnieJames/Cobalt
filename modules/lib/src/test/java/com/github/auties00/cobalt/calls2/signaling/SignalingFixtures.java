package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.calls2.VideoStreamState;
import com.github.auties00.cobalt.model.call.CallEndReason;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Representative {@link CallMessage} fixtures and an enum of inbound actions that DO carry a
 * {@link Calls2SignalingType} taxonomy ordinal, so each must classify to {@link CallSignalingRouter.Disposition#PROCESS}
 * with a present {@link CallSignalingRouter.Verdict#type()} and parse back to its own record. The
 * ordinal-less {@code <ringing>} and {@code <raise_hand>} actions are deliberately excluded from this
 * enum because they route via the parser-known tag fallback with an empty verdict type; they are
 * covered directly in the routing tests.
 */
final class SignalingFixtures {
    private SignalingFixtures() {
    }

    static OfferStanza minimalOffer(String callId, Jid callCreator) {
        return new OfferStanza(callId, callCreator, null, null, null, null, null, null,
                false, false, null, -1, -1, List.of(), List.of(), List.of(), List.of(), null,
                null, null, null, null, null, null, List.of(), null);
    }

    /**
     * One representative tag-routable inbound action per record, each pinned to the
     * {@link Calls2SignalingType} the router must classify it to and the record the parser must yield.
     */
    enum Kind {
        OFFER(Calls2SignalingType.OFFER, OfferStanza.class,
                SignalingFixtures::minimalOffer),
        ACCEPT(Calls2SignalingType.ACCEPT, AcceptStanza.class,
                (id, creator) -> new AcceptStanza(id, creator, 2, List.of(), List.of(), List.of(), List.of(),
                        null, null, null, null)),
        PREACCEPT(Calls2SignalingType.PREACCEPT, PreacceptStanza.class,
                (id, creator) -> new PreacceptStanza(id, creator, List.of(), List.of(), List.of(), null, null)),
        REJECT(Calls2SignalingType.REJECT, RejectStanza.class,
                (id, creator) -> RejectStanza.of(id, creator, CallEndReason.REJECT_DO_NOT_DISTURB)),
        TERMINATE(Calls2SignalingType.TERMINATE, TerminateStanza.class,
                (id, creator) -> TerminateStanza.of(id, creator, CallEndReason.HANGUP, List.of())),
        MUTE_V2(Calls2SignalingType.MUTE_V2, MuteV2Stanza.class,
                (id, creator) -> MuteV2Stanza.ofSelfState(id, creator, true, false)),
        DTMF(Calls2SignalingType.DTMF_TONE, DtmfStanza.class,
                (id, creator) -> new DtmfStanza(id, creator, "5")),
        INTERRUPTION(Calls2SignalingType.INTERRUPTION, InterruptionStanza.class,
                (id, creator) -> new InterruptionStanza(id, creator, true, 1)),
        NOTIFY(Calls2SignalingType.NOTIFY, NotifyStanza.class,
                (id, creator) -> new NotifyStanza(id, creator, 80)),
        SCREEN_SHARE(Calls2SignalingType.SCREEN_SHARE, ScreenShareStanza.class,
                (id, creator) -> new ScreenShareStanza(id, creator, 1, 3)),
        VIDEO_STATE(Calls2SignalingType.VIDEO_STATE, VideoStateStanza.class,
                (id, creator) -> new VideoStateStanza(id, creator, VideoStreamState.ENABLED)),
        PEER_STATE(Calls2SignalingType.PEER_STATE, PeerStateStanza.class,
                (id, creator) -> new PeerStateStanza(id, creator,
                        Jid.of("55555555", JidServer.lid(), 3, 0), 1)),
        FLOW_CONTROL(Calls2SignalingType.FLOW_CONTROL, FlowControlStanza.class,
                (id, creator) -> new FlowControlStanza(id, creator, 7, 300000, 640, 30)),
        RECONFIGURE_BOT(Calls2SignalingType.RECONFIGURE_BOT, ReconfigureBotStanza.class,
                (id, creator) -> new ReconfigureBotStanza(id, creator, 9));

        private final Calls2SignalingType expectedType;
        private final Class<? extends CallMessage> recordClass;
        private final BiFunction<String, Jid, ? extends CallMessage> factory;

        Kind(Calls2SignalingType expectedType, Class<? extends CallMessage> recordClass,
             BiFunction<String, Jid, ? extends CallMessage> factory) {
            this.expectedType = expectedType;
            this.recordClass = recordClass;
            this.factory = factory;
        }

        CallMessage build(String callId, Jid callCreator) {
            return factory.apply(callId, callCreator);
        }

        Calls2SignalingType expectedType() {
            return expectedType;
        }

        Class<? extends CallMessage> recordClass() {
            return recordClass;
        }
    }
}
