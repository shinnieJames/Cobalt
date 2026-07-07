package com.github.auties00.cobalt.calls2.net.transport;

import com.github.auties00.cobalt.calls2.core.participant.ParticipantProvider;
import com.github.auties00.cobalt.calls2.core.participant.ParticipantView;
import com.github.auties00.cobalt.calls2.util.TimerEntry;
import com.github.auties00.cobalt.calls2.util.TimerHeap;
import com.github.auties00.cobalt.model.call.datachannel.RxSubscriptions;
import com.github.auties00.cobalt.model.call.datachannel.RxSubscriptionsBuilder;
import com.github.auties00.cobalt.model.call.datachannel.RxSubscriptionsSpec;
import com.github.auties00.cobalt.model.call.datachannel.RxVidSubscriptionInfo;
import com.github.auties00.cobalt.model.call.datachannel.RxVidSubscriptionInfoBuilder;
import com.github.auties00.cobalt.model.call.datachannel.SenderSubscriptions;
import com.github.auties00.cobalt.model.call.datachannel.SenderSubscriptionsSpec;
import com.github.auties00.cobalt.model.call.datachannel.StreamDescriptor;
import com.github.auties00.cobalt.model.call.datachannel.StreamDescriptorBuilder;
import com.github.auties00.cobalt.model.call.datachannel.StreamDescriptors;
import com.github.auties00.cobalt.model.call.datachannel.StreamDescriptorsBuilder;
import com.github.auties00.cobalt.model.jid.Jid;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;
import java.util.function.ToIntFunction;

/**
 * Publishes a client's send layout and receive wishes to the selective-forwarding unit.
 *
 * <p>A group call reaches the selective-forwarding unit through the relay, and the unit
 * forwards only the streams and qualities each client asks for. A client expresses what
 * it sends and what it wants to receive by embedding serialized protobufs inside STUN
 * binding-request attributes: a {@link SenderSubscriptions} in the sender attribute and
 * an {@link RxSubscriptions} in the receiver attribute. This class turns the call's
 * {@link StreamLayout} into the {@link StreamDescriptors} list, frames the sender and
 * receiver subscriptions as {@link SubscriptionStunAttribute} values for the STUN
 * message writer, and drives the periodic resend of the cached receive subscription so a
 * dropped binding does not strand the receiver. It also owns the hop-by-hop
 * {@link RtcpRxSubscriptionTable} that records which RTCP feedback the unit should
 * forward, exposed through {@link #rtcpRxTable()}.
 *
 * <p>Builds {@link StreamDescriptors} from a {@link StreamLayout}, serializes the sender
 * and receive subscriptions with their generated protobuf specs into
 * {@link SubscriptionStunAttribute} values, and resends the cached receive subscription
 * on a fixed interval through an injected resend callback. The cached subscription and
 * its change suppression live in a {@link RxSubscriptionState}, and the hop-by-hop
 * RTCP-feedback subscriptions live in a {@link RtcpRxSubscriptionTable}; both are owned
 * by this publisher for the lifetime of the call.
 *
 * <p>The resend timer is scheduled on the supplied {@link TimerHeap} against the supplied
 * nanosecond clock; each fire resends the most recently published subscription through
 * the resend callback and re-arms the timer, so the cadence continues until
 * {@link #close()} cancels it. This class is not thread-safe: the single call transport
 * thread that polls the timer heap also calls every method here.
 *
 * @implNote This implementation reproduces the subscription-build and resend logic of
 * {@code wa_transport_subscription.cc} in the wa-voip WASM module {@code ff-tScznZ8P}:
 * {@code append_stream_descriptors} (fn5183) plus the per-feature appenders fn5184/fn5185/fn5186 for
 * the descriptor list, {@code add_stun_attr_sender_subscriptions} / {@code add_stun_attr_receiver_subscription}
 * (fn5182) for the STUN attribute framing, and {@code wa_transport_p2p_send_cached_rx_subscription}
 * (fn10938) gated by {@code is_rx_sub_the_same} (fn5181) for the suppressed resend. The native
 * descriptor builder emits a media plus FEC plus NACK triple per active media layer and single
 * descriptors for the app-data, live-transcription, and hop-by-hop FEC SSRCs; this port reproduces that
 * exactly, omitting any triple whose SSRC is {@link StreamLayout#ABSENT_SSRC}. The resend cadence is
 * driven through the existing {@link TimerHeap} rather than the native PJSIP timer the engine uses, and
 * the resend re-arms itself on each fire to model the native periodic callback.
 */
public final class LiveSubscriptionPublisher {
    /**
     * The default interval between resends of the cached receive subscription.
     *
     * <p>Applied as the period of the receive-subscription resend timer and, with the same value, as the
     * minimum-duration gate before a re-subscription is issued; at runtime both read the
     * {@code tp->rx_subscription_min_duration_ms} field.
     *
     * @implNote The {@code 100ms} is the compiled-in default of the voip parameter
     * {@code rx_subscription_min_duration_ms}. The parameter-apply pass {@code reg_param_entry_impl}
     * (fn11834 in {@code voip_param_internal.cc}) writes it as {@code i32.const 100; i32.store off=251644}
     * at the call-struct offset {@code 0x3D6FC}; that value reaches the transport field
     * {@code transport+0x2cc}, which {@code wa_call_media_update_participants_rx_subscription} (fn11420)
     * reads both as the resend timer period ({@code fn10948(call, call+0x1e8, *(transport+0x2cc))}) and as
     * the min-duration comparison. The server may override it through the voip param key
     * {@code rx_sub_min_dur_ms}; that key is absent from the captured {@code voip_settings} union
     * (re/calls2-spec/captures/voip-settings-merged.json), so the compiled default governs. The earlier
     * {@code 1s} placeholder overstated the cadence by 10x.
     */
    public static final Duration DEFAULT_RESEND_INTERVAL = Duration.ofMillis(100);

    /**
     * The exclusive upper bound on a valid receive-subscription video-quality index.
     *
     * <p>A quality entry whose {@link RxVidSubscriptionInfo.VideoQuality#index() index} is at or above this
     * is dropped from the computed subscription.
     *
     * @implNote This implementation uses {@code 5}, the bound {@code wa_call_media_update_participants_rx_subscription}
     * (fn11420) enforces: it rejects a requested quality with {@code quality > 4}
     * (re/calls/out/ff-tScznZ8P-full4/flat/fn11420.c), which is exactly the five
     * {@link RxVidSubscriptionInfo.VideoQuality} levels {@code DEFAULT}(0) through {@code HD}(4).
     */
    private static final int VIDEO_QUALITY_BOUND = 5;

    /**
     * The timer heap the resend timer is scheduled on.
     *
     * <p>Driven by the call transport thread; this publisher schedules and cancels a
     * single resend entry on it. Never {@code null}.
     */
    private final TimerHeap timerHeap;

    /**
     * Source of the current time in the timer heap's nanosecond timebase.
     *
     * <p>Read when arming the resend timer so the deadline shares the clock the transport
     * thread polls the heap with. Never {@code null}.
     */
    private final LongSupplier clock;

    /**
     * Callback invoked to resend the cached receive subscription on each timer fire.
     *
     * <p>Receives the framed receiver {@link SubscriptionStunAttribute} carrying the most
     * recently published subscription; the transport attaches it to a STUN binding request
     * toward the relay. Never {@code null}.
     */
    private final Consumer<SubscriptionStunAttribute> rxResender;

    /**
     * The interval between successive resends of the cached subscription.
     *
     * <p>Applied when arming and re-arming the resend timer. Never {@code null}.
     */
    private final Duration resendInterval;

    /**
     * The cached receive subscription and its change-suppression state.
     *
     * <p>Holds the last-published subscription so a redundant publish is suppressed and a
     * resend carries the current subscription. Never {@code null}.
     */
    private final RxSubscriptionState rxState;

    /**
     * The hop-by-hop RTCP-feedback subscription table for this call.
     *
     * <p>Records which RTCP feedback the selective-forwarding unit should forward per
     * media SSRC. Never {@code null}.
     */
    private final RtcpRxSubscriptionTable rtcpRxTable;

    /**
     * The participant read seam this publisher reads the call roster through, or {@code null}
     * on a one-to-one call that tracks no roster.
     *
     * <p>The native subscription path reads the participant provider to drive two decisions
     * the receive-subscription computation needs: the first connected peer the one-to-one
     * layout keys its single peer off, and the per-stream subscriber count the
     * selective-forwarding-unit quality picker uses to avoid subscribing to a video stream no
     * participant wants. It is the roster the receive-subscription compute walks in
     * {@link #computeRxSubscription(ToIntFunction, IntFunction)}. It is {@code null} on a
     * one-to-one call, which has a single fixed peer and allocates no
     * {@link com.github.auties00.cobalt.calls2.core.participant.CallMembership}, and on a
     * publisher built without a roster (the wire-shape unit tests).
     *
     * @implNote This implementation holds the read seam reproduced by
     * {@code participant_provider.cc} (fn10988-11005): the
     * {@code wa_participant_provider_first_connected_peer_participant} (fn11001) and
     * {@code wa_participant_provider_get_vid_stream_num_subscribers} (fn11002) accessors the
     * native receive-subscription build in {@code wa_transport_subscription.cc} reads. Cobalt
     * splits the membership out of the native {@code call_context}, so the provider is threaded
     * in at construction from {@link com.github.auties00.cobalt.calls2.core.participant.CallMembership#participantProvider()}
     * rather than reached through the embedded context.
     */
    private final ParticipantProvider participantProvider;

    /**
     * The armed resend timer entry, or {@code null} when no resend is scheduled.
     *
     * <p>Set when a changed subscription is published and re-armed on each fire; cancelled
     * by {@link #close()}. Holds {@code null} before the first publish and after close.
     */
    private TimerEntry resendTimer;

    /**
     * The framed receiver attribute for the last-published subscription, or {@code null}
     * when nothing is cached.
     *
     * <p>Built once in {@link #publishRxSubscription(RxSubscriptions, long)} alongside the
     * {@link RxSubscriptionState} record and forwarded verbatim by {@link #onResendTimer()}
     * so the 10 Hz resend does not re-encode the protobuf and re-clone it into a fresh
     * {@link SubscriptionStunAttribute} every tick. Kept in lockstep with the
     * {@link #rxState} cache: set when that cache is recorded, nulled when it is cleared in
     * {@link #close()}. {@link SubscriptionStunAttribute} is an immutable record (its
     * constructor and {@link SubscriptionStunAttribute#value() value()} both clone), so the
     * one instance is shared across the initial publish and every resend without aliasing.
     */
    private SubscriptionStunAttribute cachedReceiverAttribute;

    /**
     * Whether this publisher has been closed.
     *
     * <p>Once {@code true} the resend timer stays cancelled and {@link #close()} is a
     * no-op; publishing after close still frames attributes but arms no timer.
     */
    private boolean closed;

    /**
     * Constructs a publisher with the default resend interval and no participant provider.
     *
     * <p>Equivalent to {@link #LiveSubscriptionPublisher(TimerHeap, LongSupplier, Consumer, Duration, ParticipantProvider)}
     * with {@link #DEFAULT_RESEND_INTERVAL} and a {@code null} provider. Suitable for a publisher with no
     * participant context (a one-to-one call, or a wire-shape test exercising only the framing methods).
     *
     * @param timerHeap  the heap the resend timer is scheduled on; must not be {@code null}
     * @param clock      the nanosecond clock the heap is polled with; must not be {@code null}
     * @param rxResender the callback that sends the resent receiver attribute; must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public LiveSubscriptionPublisher(TimerHeap timerHeap,
                                     LongSupplier clock,
                                     Consumer<SubscriptionStunAttribute> rxResender) {
        this(timerHeap, clock, rxResender, DEFAULT_RESEND_INTERVAL, null);
    }

    /**
     * Constructs a publisher with an explicit resend interval and no participant provider.
     *
     * <p>Equivalent to {@link #LiveSubscriptionPublisher(TimerHeap, LongSupplier, Consumer, Duration, ParticipantProvider)}
     * with a {@code null} provider.
     *
     * @param timerHeap      the heap the resend timer is scheduled on; must not be {@code null}
     * @param clock          the nanosecond clock the heap is polled with; must not be {@code null}
     * @param rxResender     the callback that sends the resent receiver attribute; must not be {@code null}
     * @param resendInterval the interval between cached-subscription resends; must not be {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public LiveSubscriptionPublisher(TimerHeap timerHeap,
                                     LongSupplier clock,
                                     Consumer<SubscriptionStunAttribute> rxResender,
                                     Duration resendInterval) {
        this(timerHeap, clock, rxResender, resendInterval, null);
    }

    /**
     * Constructs a publisher with an explicit resend interval and a participant read seam.
     *
     * <p>The publisher starts with no cached subscription, an empty feedback table, and no
     * armed timer; the first changed {@link #publishRxSubscription(RxSubscriptions, long)}
     * arms the resend. The {@code participantProvider} is the roster read seam the
     * receive-subscription compute reads; pass {@code null} on a one-to-one call that tracks
     * no roster.
     *
     * @param timerHeap           the heap the resend timer is scheduled on; must not be {@code null}
     * @param clock               the nanosecond clock the heap is polled with; must not be {@code null}
     * @param rxResender          the callback that sends the resent receiver attribute; must not be
     *                            {@code null}
     * @param resendInterval      the interval between cached-subscription resends; must not be {@code null}
     * @param participantProvider the call roster read seam, or {@code null} when no roster is tracked
     * @throws NullPointerException if {@code timerHeap}, {@code clock}, {@code rxResender}, or
     *                              {@code resendInterval} is {@code null}
     */
    public LiveSubscriptionPublisher(TimerHeap timerHeap,
                                     LongSupplier clock,
                                     Consumer<SubscriptionStunAttribute> rxResender,
                                     Duration resendInterval,
                                     ParticipantProvider participantProvider) {
        this.timerHeap = Objects.requireNonNull(timerHeap, "timerHeap cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.rxResender = Objects.requireNonNull(rxResender, "rxResender cannot be null");
        this.resendInterval = Objects.requireNonNull(resendInterval, "resendInterval cannot be null");
        this.participantProvider = participantProvider;
        this.rxState = new RxSubscriptionState();
        this.rtcpRxTable = new RtcpRxSubscriptionTable();
        this.resendTimer = null;
        this.closed = false;
    }

    /**
     * Returns the participant read seam this publisher reads the call roster through, if one was supplied.
     *
     * <p>Present on a group call, where it is the
     * {@link com.github.auties00.cobalt.calls2.core.participant.CallMembership#participantProvider() membership
     * provider}; empty on a one-to-one call that tracks no roster. It is the seam the
     * receive-subscription compute reads for the first connected peer and the per-stream subscriber count.
     *
     * @return an {@link Optional} holding the participant provider, or empty when none was supplied
     */
    public Optional<ParticipantProvider> participantProvider() {
        return Optional.ofNullable(participantProvider);
    }

    /**
     * Builds the stream-descriptor list declaring every stream the layout publishes.
     *
     * <p>Emits one {@link com.github.auties00.cobalt.model.call.datachannel.StreamDescriptor}
     * per active stream: the audio media plus its forward-error-correction and
     * negative-acknowledgement descriptors, the same triple for each present video
     * simulcast layer, and the application-data, live-transcription, and hop-by-hop
     * forward-error-correction descriptors for whichever feature SSRCs the layout
     * allocates. Absent SSRCs yield no descriptors. The result is the
     * {@link StreamDescriptors} the SFU reads to set up forwarding.
     *
     * @param layout the SSRC and feature layout this client publishes; must not be {@code null}
     * @return the stream descriptors for the layout
     * @throws NullPointerException if {@code layout} is {@code null}
     */
    public StreamDescriptors buildStreamDescriptors(StreamLayout layout) {
        Objects.requireNonNull(layout, "layout cannot be null");
        var descriptors = new ArrayList<StreamDescriptor>(StreamLayout.MAX_STREAM_DESCRIPTORS);
        appendMediaTriple(descriptors, StreamDescriptor.StreamLayer.AUDIO, layout.audioSsrc(), false);
        appendMediaTriple(descriptors, StreamDescriptor.StreamLayer.VIDEO_STREAM0,
                layout.videoStream0Ssrc(), layout.uplinkPrefetch());
        appendMediaTriple(descriptors, StreamDescriptor.StreamLayer.VIDEO_STREAM1,
                layout.videoStream1Ssrc(), layout.uplinkPrefetch());
        appendSingle(descriptors, StreamDescriptor.StreamLayer.APP_DATA_STREAM0,
                StreamDescriptor.PayloadType.APP_DATA, layout.appDataSsrc(), layout.uplinkPrefetch());
        appendSingle(descriptors, StreamDescriptor.StreamLayer.LIVE_TRANSCRIPTION_STREAM0,
                StreamDescriptor.PayloadType.MEDIA, layout.liveTranscriptionSsrc(), false);
        appendSingle(descriptors, StreamDescriptor.StreamLayer.HBH_FEC_CLIENT_TO_SERVER,
                StreamDescriptor.PayloadType.HBH_FEC, layout.hbhFecTxSsrc(), false);
        appendSingle(descriptors, StreamDescriptor.StreamLayer.HBH_FEC_SERVER_TO_CLIENT,
                StreamDescriptor.PayloadType.HBH_FEC, layout.hbhFecRxSsrc(), false);
        return new StreamDescriptorsBuilder()
                .streamDescriptors(descriptors)
                .build();
    }

    /**
     * Appends the media, FEC, and NACK descriptors for one media layer when its SSRC is
     * present.
     *
     * <p>A media layer is published as three descriptors sharing the layer's SSRC: the
     * media payload, the paired forward-error-correction stream, and the
     * negative-acknowledgement stream. When the SSRC is {@link StreamLayout#ABSENT_SSRC}
     * no descriptor is appended, so an inactive layer contributes nothing. The
     * uplink-prefetch flag is carried on the media descriptor only.
     *
     * @param into     the accumulator to append to
     * @param layer    the logical layer the triple describes
     * @param ssrc     the layer SSRC, or {@link StreamLayout#ABSENT_SSRC} to skip
     * @param prefetch whether uplink prefetch is engaged on the media descriptor
     */
    private void appendMediaTriple(List<StreamDescriptor> into,
                                   StreamDescriptor.StreamLayer layer,
                                   int ssrc,
                                   boolean prefetch) {
        if (ssrc == StreamLayout.ABSENT_SSRC) {
            return;
        }
        appendSingle(into, layer, StreamDescriptor.PayloadType.MEDIA, ssrc, prefetch);
        appendSingle(into, layer, StreamDescriptor.PayloadType.FEC, ssrc, false);
        appendSingle(into, layer, StreamDescriptor.PayloadType.NACK, ssrc, false);
    }

    /**
     * Appends a single descriptor for the given layer and payload type when the SSRC is
     * present.
     *
     * <p>Builds one {@link StreamDescriptor} binding the layer and payload type to the
     * SSRC, carrying the uplink-prefetch flag, and appends it. When the SSRC is
     * {@link StreamLayout#ABSENT_SSRC} nothing is appended.
     *
     * @param into        the accumulator to append to
     * @param layer       the logical layer the descriptor describes
     * @param payloadType the payload type the descriptor describes
     * @param ssrc        the stream SSRC, or {@link StreamLayout#ABSENT_SSRC} to skip
     * @param prefetch    whether uplink prefetch is engaged
     */
    private void appendSingle(List<StreamDescriptor> into,
                              StreamDescriptor.StreamLayer layer,
                              StreamDescriptor.PayloadType payloadType,
                              int ssrc,
                              boolean prefetch) {
        if (ssrc == StreamLayout.ABSENT_SSRC) {
            return;
        }
        into.add(new StreamDescriptorBuilder()
                .streamLayer(layer)
                .payloadType(payloadType)
                .ssrc(ssrc)
                .isUplinkPrefetchEnabled(prefetch ? Boolean.TRUE : null)
                .build());
    }

    /**
     * Frames a sender subscription as the proprietary STUN sender-subscription attribute.
     *
     * <p>Serializes the {@link SenderSubscriptions} protobuf and wraps the bytes in a
     * {@link SubscriptionStunAttribute} of type
     * {@link SubscriptionStunAttribute#SENDER_SUBSCRIPTIONS_TYPE} so the STUN message
     * writer can append it to a binding request.
     *
     * @param senderSubscriptions the sender subscription to frame; must not be {@code null}
     * @return the STUN attribute carrying the serialized sender subscription
     * @throws NullPointerException if {@code senderSubscriptions} is {@code null}
     */
    public SubscriptionStunAttribute buildSenderAttribute(SenderSubscriptions senderSubscriptions) {
        Objects.requireNonNull(senderSubscriptions, "senderSubscriptions cannot be null");
        var bytes = SenderSubscriptionsSpec.encode(senderSubscriptions);
        return new SubscriptionStunAttribute(SubscriptionStunAttribute.SENDER_SUBSCRIPTIONS_TYPE, bytes);
    }

    /**
     * Frames a receive subscription as the proprietary STUN receiver-subscription attribute.
     *
     * <p>Serializes the {@link RxSubscriptions} protobuf and wraps the bytes in a
     * {@link SubscriptionStunAttribute} of type
     * {@link SubscriptionStunAttribute#RECEIVER_SUBSCRIPTION_TYPE}. Unlike
     * {@link #publishRxSubscription(RxSubscriptions, long)} this performs no suppression and
     * does not touch the cached state; it is the framing primitive the publish path builds on.
     *
     * @param rxSubscriptions the receive subscription to frame; must not be {@code null}
     * @return the STUN attribute carrying the serialized receive subscription
     * @throws NullPointerException if {@code rxSubscriptions} is {@code null}
     */
    public SubscriptionStunAttribute buildReceiverAttribute(RxSubscriptions rxSubscriptions) {
        Objects.requireNonNull(rxSubscriptions, "rxSubscriptions cannot be null");
        var bytes = RxSubscriptionsSpec.encode(rxSubscriptions);
        return new SubscriptionStunAttribute(SubscriptionStunAttribute.RECEIVER_SUBSCRIPTION_TYPE, bytes);
    }

    /**
     * Computes this client's receive subscription by walking the connected peers in the call roster.
     *
     * <p>Reproduces the receive-subscription shape the native
     * {@code wa_call_media_update_participants_rx_subscription} (fn11420) builds: every connected, non-self,
     * non-extension peer the subscriber wants video from contributes its relay PID to the
     * {@link RxSubscriptions#vidRxPids() vid_rx_pids} list, and, when a quality is chosen for that PID, an
     * {@link RxVidSubscriptionInfo} entry pairing the PID with the requested
     * {@link RxVidSubscriptionInfo.VideoQuality quality} to {@link RxSubscriptions#vidSubscriptions()}. The
     * two roster facts the native build reads but the participant snapshot does not carry are supplied as
     * seams: {@code pidResolver} maps a peer's user JID to its server-assigned relay PID (the relay
     * {@code <participant pid jid>} mapping), and {@code qualityChooser} returns the requested receive
     * quality for a PID (the per-tile quality the platform decides), or empty to omit a quality entry for a
     * PID the subscriber wants any video from. A peer whose JID resolves to no PID, and a quality whose
     * ordinal is outside the valid range the native rejects, are skipped. The result is suitable for
     * {@link #publishRxSubscription(RxSubscriptions, long)}.
     *
     * <p>When this publisher tracks no roster ({@link #participantProvider()} is empty) or the roster is not
     * valid, the computed subscription is empty, matching a call with no connected peers to subscribe to.
     *
     * @param pidResolver    maps a connected peer's user JID to its relay PID, returning a negative value
     *                       when the JID has no PID; never {@code null}
     * @param qualityChooser returns the requested receive quality for a relay PID, or empty to request any
     *                       video without a quality entry; never {@code null}
     * @return the computed receive subscription over the connected peers
     * @throws NullPointerException if {@code pidResolver} or {@code qualityChooser} is {@code null}
     * @implNote This implementation reproduces the {@code (pid, vidQuality)} accumulation of fn11420 in
     * {@code video_subscription.cc}: it walks the connected peers, validates each requested quality against
     * the {@code vidQuality < 5} bound fn11420 enforces, and emits a {@code vid_rx_pids} entry per wanted
     * peer plus a {@code vid_subscriptions} entry per chosen quality. fn11420 itself is fed the
     * {@code (pid, quality)} array already decided by the platform and does not pick the quality; Cobalt
     * mirrors that by taking the quality through the {@code qualityChooser} seam rather than computing it,
     * because the per-tile quality is a not-statically-recoverable UI decision (which video tiles are
     * visible and their on-screen size). The relay-PID lookup is a seam rather than a participant-view field
     * because {@link ParticipantView} carries the peer JIDs but not the relay-assigned PID, which lives in
     * the parsed relay {@code <participant>} list outside this publisher.
     */
    public RxSubscriptions computeRxSubscription(ToIntFunction<Jid> pidResolver,
                                                 IntFunction<Optional<RxVidSubscriptionInfo.VideoQuality>> qualityChooser) {
        Objects.requireNonNull(pidResolver, "pidResolver cannot be null");
        Objects.requireNonNull(qualityChooser, "qualityChooser cannot be null");
        var pids = new ArrayList<Integer>();
        var subscriptions = new ArrayList<RxVidSubscriptionInfo>();
        if (participantProvider != null && participantProvider.isValid()) {
            for (var view : participantProvider.views()) {
                if (!view.isConnectedPeer()) {
                    continue;
                }
                var userJid = view.userJid();
                if (userJid == null) {
                    continue;
                }
                var pid = pidResolver.applyAsInt(userJid);
                if (pid < 0) {
                    continue;
                }
                pids.add(pid);
                var quality = qualityChooser.apply(pid).orElse(null);
                if (quality != null && quality.index() < VIDEO_QUALITY_BOUND) {
                    subscriptions.add(new RxVidSubscriptionInfoBuilder()
                            .pid(pid)
                            .vidQuality(quality)
                            .build());
                }
            }
        }
        return new RxSubscriptionsBuilder()
                .vidRxPids(pids)
                .vidSubscriptions(subscriptions)
                .build();
    }

    /**
     * Publishes a receive subscription, suppressing it when it has not changed.
     *
     * <p>When the subscription is identical to the last published one this returns an
     * empty result and changes nothing, so the caller sends no binding. When it differs
     * this records it as the new cached subscription, arms or re-arms the resend timer
     * against the supplied clock, and returns the framed
     * {@link SubscriptionStunAttribute} for the caller to attach to a STUN binding
     * request. The subsequent resend the timer drives carries the most recently published
     * subscription.
     *
     * @param rxSubscriptions the receive subscription to publish; must not be {@code null}
     * @param nowNanos        the current time in the resend timer's nanosecond timebase
     * @return the STUN attribute to send, or an empty result when the subscription is a
     *         redundant resend
     * @throws NullPointerException if {@code rxSubscriptions} is {@code null}
     */
    public Optional<SubscriptionStunAttribute> publishRxSubscription(RxSubscriptions rxSubscriptions,
                                                                     long nowNanos) {
        Objects.requireNonNull(rxSubscriptions, "rxSubscriptions cannot be null");
        // Frames the receive subscription, suppresses an identical resend, records the new subscription, and
        // arms the resend timer; the framed attribute is returned for the caller to ship inside the 0x0003
        // subscription envelope over the data channel (SubscriptionEnvelope.subscriptionEnvelope ->
        // LiveRelayTransport.sendAppData), whose outer MESSAGE-INTEGRITY is keyed by the relay <key>
        // (re/calls2-spec/web-transport-crypto-RE.md ADDENDUM). The live capture's leading 0x4000 WARP
        // attribute is an optional hop-by-hop-SRTP-sealed rate-control report Cobalt does not emit (see
        // SubscriptionEnvelope; the seal is not capture-reproducible).
        if (!rxState.shouldPublish(rxSubscriptions)) {
            return Optional.empty();
        }
        rxState.record(rxSubscriptions);
        var attribute = buildReceiverAttribute(rxSubscriptions);
        cachedReceiverAttribute = attribute;
        armResendTimer(nowNanos);
        return Optional.of(attribute);
    }

    /**
     * Cancels any armed resend timer and schedules a fresh one against the supplied clock.
     *
     * <p>Ensures a single resend entry is live at a time: an existing entry is cancelled
     * before a new one is scheduled {@link #resendInterval} ahead of {@code nowNanos}. When
     * the publisher is closed no timer is armed. The scheduled callback runs
     * {@link #onResendTimer()}.
     *
     * @param nowNanos the current time in the timer heap's nanosecond timebase
     */
    private void armResendTimer(long nowNanos) {
        if (closed) {
            return;
        }
        if (resendTimer != null) {
            resendTimer.cancel();
        }
        resendTimer = timerHeap.schedule(nowNanos, resendInterval, this::onResendTimer);
    }

    /**
     * Resends the cached receive subscription and re-arms the resend timer.
     *
     * <p>Invoked by the timer heap when the resend interval elapses. When a subscription
     * has been cached and the publisher is open, it hands the pre-built
     * {@link #cachedReceiverAttribute} to the resend callback, then schedules the next
     * resend; when nothing is cached or the publisher is closed it does neither, ending the
     * cadence. Reading the current time from the injected clock keeps the re-armed deadline
     * on the heap's timebase.
     */
    private void onResendTimer() {
        if (closed) {
            return;
        }
        var cached = cachedReceiverAttribute;
        if (cached == null) {
            return;
        }
        rxResender.accept(cached);
        armResendTimer(clock.getAsLong());
    }

    /**
     * Returns the hop-by-hop RTCP-feedback subscription table for this call.
     *
     * <p>The caller registers and removes feedback subscriptions on the returned table to
     * tell the selective-forwarding unit which RTCP feedback to forward for each media
     * SSRC.
     *
     * @return the RTCP-feedback subscription table, never {@code null}
     */
    public RtcpRxSubscriptionTable rtcpRxTable() {
        return rtcpRxTable;
    }

    /**
     * Releases the publisher's timer and clears its cached subscription and feedback table.
     *
     * <p>Cancels the resend timer if it is armed, clears the cached receive subscription
     * so a later transport republishes from scratch, and empties the RTCP-feedback table.
     * Idempotent: a second close is a no-op.
     */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (resendTimer != null) {
            resendTimer.cancel();
            resendTimer = null;
        }
        rxState.clear();
        cachedReceiverAttribute = null;
        rtcpRxTable.clear();
    }
}
