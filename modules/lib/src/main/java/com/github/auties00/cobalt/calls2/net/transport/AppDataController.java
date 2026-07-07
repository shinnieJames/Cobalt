package com.github.auties00.cobalt.calls2.net.transport;

import com.github.auties00.cobalt.model.call.datachannel.AppDataMessage;
import com.github.auties00.cobalt.model.call.datachannel.AppDataMessageBuilder;
import com.github.auties00.cobalt.model.call.datachannel.AppDataPayloads;
import com.github.auties00.cobalt.model.call.datachannel.E2eRekeyPayload;
import com.github.auties00.cobalt.model.call.datachannel.LiveTranscriptionInfo;
import com.github.auties00.cobalt.model.call.datachannel.PeerFeedback;
import com.github.auties00.cobalt.model.call.datachannel.ReactionInfo;
import com.github.auties00.cobalt.model.call.datachannel.ReactionInfoBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.util.ScheduledTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The in-call application-data side-channel: sends and expires emoji reactions, retransmits the most
 * recent reaction, and demultiplexes inbound app-data into reaction, transcription, rekey, subscription,
 * and feedback handlers.
 *
 * <p>Reactions are unreliable, transient UI events shipped over the {@link AppDataChannel}. The
 * controller keeps a single outbound reaction live for a short window so it can be retransmitted against
 * packet loss, then clears it; it tracks one inbound reaction per participant and expires each after its
 * lifetime so a displayed overlay is taken down. The three housekeeping actions run as the
 * three self-clearing timers, each on its own virtual-thread {@link ScheduledTask}: a one-shot clear
 * of the outbound reaction, a periodic sweep of aged inbound reactions, and an optional periodic
 * retransmission of the live outbound reaction.
 *
 * <p>The controller is also the inbound demultiplexer. A received {@link AppDataPayloads} batch is split
 * into its messages: each reaction updates the per-participant record and notifies the reaction
 * observer, each transcription fragment is forwarded to the transcription observer; a received
 * {@link E2eRekeyPayload}, {@link PeerFeedback}, or subscription is routed to the matching handler. The
 * controller does not itself decide the transport; it serializes through the {@link AppDataChannel},
 * which selects the SCTP DataChannel or the relay RTP stream.
 *
 * <p>Mutations of the reaction state are guarded by a single lock so a timer callback and an inbound or
 * outbound reaction never race; each timer runs on its own virtual thread. The controller is closed with
 * {@link #close()}, which cancels every timer.
 *
 * <p>The inbound observers may be supplied at construction (the fully wired constructor) or left as no-ops
 * and attached afterwards through {@link #attachReactionObserver(BiConsumer)} and its siblings (the
 * channel-only constructor). The latter breaks the construction cycle between this controller, which is
 * built during the media-plane bring-up, and the in-call control units that observe it, which are built
 * later in the call lifecycle; it mirrors how the reaction control unit attaches its outbound send seam
 * after the controller exists.
 *
 * @implNote This implementation reproduces {@code wa_app_data_controller_create} (fn11747),
 * {@code wa_app_data_controller_destroy} (fn11746), {@code wa_app_data_controller_tx_reaction_clear_callback}
 * (fn11749), and {@code wa_app_data_controller_rx_reaction_clear_callback} (fn11750) of the wa-voip WASM
 * module {@code ff-tScznZ8P} ({@code system/src/transport/app_data_controller.cc}, a {@code 0x130}-byte
 * pool-allocated controller). The native create wires three call timers with type tags {@code 1}/{@code 2}/{@code 3}
 * and schedules the retransmission timer when reaction retransmission is
 * enabled ({@code call+0x22f62}, interval {@code call+0x22f5c}); {@code tx_reaction_clear} zeroes the
 * outbound reaction state at {@code controller+0x108}; {@code rx_reaction_clear} sweeps the participant
 * collection, clears each reaction older than a threshold, optionally notifies the app, and re-arms
 * itself with interval {@code DAT_22f5c}. The native pjlib pool/mutex/timer machinery is replaced by JDK
 * objects per the threading design: a virtual-thread {@link ScheduledTask} per timer and a
 * {@link ReentrantLock} for the reaction-state mutex, rather than reimplemented. The
 * concrete native controller implements only the reaction tx/rx path; rekey, subscription, and feedback
 * app-data are demultiplexed here to their handlers because they share the AppData stream that this
 * controller owns.
 */
public final class AppDataController implements AutoCloseable {
    /**
     * Default lifetime of an outbound reaction before its clear timer fires.
     *
     * <p>After this window elapses the outbound reaction is cleared so it stops being retransmitted. It is
     * the {@code vp->call_reaction_clear_interval_ms} voip parameter ({@code call+0x22f58}), read by
     * {@code wa_call_send_reaction_internal} (fn11128) when it reschedules the clear timer.
     *
     * @implNote This implementation uses {@code 5000} ms, the compiled default that
     * {@code reg_param_entry_impl} ({@code voip_param_internal.cc}, fn11822) registers for the parameter at
     * params offset {@code 2520} (the voip-params struct is embedded at {@code call+0x22580}, so the field
     * resolves to {@code call+0x22f58}). The default applies unless the server pushes the key, which is
     * confirmed absent from the 759-key live {@code voip_settings} union
     * (re/calls2-spec/captures/voip-settings-merged.json), so it is the operative value.
     */
    private static final Duration DEFAULT_TX_REACTION_LIFETIME = Duration.ofMillis(5000);

    /**
     * Default interval between sweeps of aged inbound reactions.
     *
     * <p>The rx-reaction-clear timer re-arms itself at this cadence to expire per-participant reactions
     * past their lifetime. It is the {@code vp->call_reaction_clear_timer_frequency_ms} voip parameter
     * ({@code call+0x22f5c}), with which {@code wa_app_data_controller_rx_reaction_clear_callback} (fn11750)
     * re-arms itself.
     *
     * @implNote This implementation uses {@code 100} ms, the compiled default that
     * {@code reg_param_entry_impl} ({@code voip_param_internal.cc}, fn11822) registers for the parameter at
     * params offset {@code 2524} (the voip-params struct is embedded at {@code call+0x22580}, so the field
     * resolves to {@code call+0x22f5c}). The default applies unless the server pushes the key, which is
     * confirmed absent from the 759-key live {@code voip_settings} union
     * (re/calls2-spec/captures/voip-settings-merged.json), so it is the operative value.
     */
    private static final Duration DEFAULT_RX_REACTION_SWEEP = Duration.ofMillis(100);

    /**
     * Default lifetime of an inbound per-participant reaction before it is expired.
     *
     * <p>A participant's reaction older than this when a sweep runs is cleared and the observer notified.
     * It is the same {@code vp->call_reaction_clear_interval_ms} field ({@code call+0x22f58}) as the
     * tx-clear interval; the sweep clears a participant reaction once its age exceeds it
     * ({@code app_data_controller.cc} fn11750).
     *
     * @implNote This implementation uses {@code 5000} ms, the compiled default that
     * {@code reg_param_entry_impl} ({@code voip_param_internal.cc}, fn11822) registers for the parameter at
     * params offset {@code 2520} ({@code call+0x22f58}). The default applies unless the server pushes the
     * key, which is confirmed absent from the 759-key live {@code voip_settings} union
     * (re/calls2-spec/captures/voip-settings-merged.json), so it is the operative value; it matches
     * {@link #DEFAULT_TX_REACTION_LIFETIME} because both read the one field.
     */
    private static final Duration DEFAULT_RX_REACTION_LIFETIME = Duration.ofMillis(5000);

    /**
     * Default interval between retransmissions of the live outbound reaction.
     *
     * <p>Used only when reaction retransmission is enabled; resends the outbound reaction to survive a
     * lost packet on the unreliable path. It is the {@code vp->call_reactions_retransmission_timeout_ms}
     * voip parameter ({@code call+0x22f54}), with which {@code wa_app_data_controller_create} (fn11747) arms
     * the type-3 retransmission timer, gated by the retransmission-enabled flag {@code call+0x22f62}.
     *
     * @implNote This implementation uses {@code 500} ms, the compiled default that
     * {@code reg_param_entry_impl} ({@code voip_param_internal.cc}, fn11822) registers for the parameter at
     * params offset {@code 2516} (the voip-params struct is embedded at {@code call+0x22580}, so the field
     * resolves to {@code call+0x22f54}, distinct from the adjacent clear-frequency field at
     * {@code call+0x22f5c}). The default applies unless the server pushes the key, which is confirmed absent
     * from the 759-key live {@code voip_settings} union (re/calls2-spec/captures/voip-settings-merged.json),
     * so it is the operative value.
     */
    private static final Duration DEFAULT_RETRANSMISSION_INTERVAL = Duration.ofMillis(500);

    /**
     * The app-data transport seam this controller serializes reactions and other payloads through.
     */
    private final AppDataChannel channel;

    /**
     * Guards every mutation of the outbound and inbound reaction state.
     *
     * <p>Held briefly by a send, an inbound reaction, and each timer callback so they never observe a
     * half-updated reaction record; the analogue of the native controller mutex at {@code +0x114}.
     */
    private final ReentrantLock reactionLock = new ReentrantLock();

    /**
     * The most recent inbound reaction per participant, keyed by device JID.
     *
     * <p>Each entry records the reaction and the time it arrived so the sweep can expire it; concurrent
     * because it is read under the lock but iterated by the sweep.
     */
    private final Map<Jid, RxReaction> rxReactions = new ConcurrentHashMap<>();

    /**
     * Observer notified when an inbound reaction arrives or is expired.
     *
     * <p>Receives the originating participant and the reaction; an expiry delivers an empty reaction so
     * the UI can take the overlay down. Defaults to a no-op and may be replaced once, after construction,
     * through {@link #attachReactionObserver(BiConsumer)} so a controller can be brought up before
     * the in-call control unit that observes it exists.
     *
     * <p>Volatile because the attach runs on the media-plane wiring thread while the inbound demux and the
     * rx-reaction-clear sweep read it on their own threads; the field is written at most once after
     * construction.
     */
    private volatile BiConsumer<Jid, Optional<ReactionInfo>> reactionObserver;

    /**
     * Observer notified, with the sending device, when an inbound live-transcription fragment arrives.
     *
     * <p>Defaults to a no-op and may be replaced once, after construction, through
     * {@link #attachTranscriptionObserver(BiConsumer)}; volatile for the same reason as
     * {@link #reactionObserver}.
     */
    private volatile BiConsumer<Jid, LiveTranscriptionInfo> transcriptionObserver;

    /**
     * Handler invoked when an inbound end-to-end rekey bundle arrives over the AppData stream.
     *
     * <p>Defaults to a no-op and may be replaced once, after construction, through
     * {@link #attachRekeyHandler(Consumer)}; volatile for the same reason as {@link #reactionObserver}.
     */
    private volatile Consumer<E2eRekeyPayload> rekeyHandler;

    /**
     * Handler invoked when inbound peer feedback arrives over the AppData stream.
     *
     * <p>Defaults to a no-op and may be replaced once, after construction, through
     * {@link #attachFeedbackHandler(Consumer)}; volatile for the same reason as
     * {@link #reactionObserver}.
     */
    private volatile Consumer<PeerFeedback> feedbackHandler;

    /**
     * Whether reaction retransmission is enabled for this call.
     *
     * <p>When true the retransmission timer is armed at construction; the analogue of the native
     * {@code call+0x22f62} flag.
     */
    private final boolean retransmissionEnabled;

    /**
     * The live outbound reaction retained for retransmission, or {@code null} when none is live.
     *
     * <p>Set by {@link #sendReaction(String)} and cleared by the tx-reaction-clear timer; read by the
     * retransmission timer.
     */
    private ReactionInfo txReaction;

    /**
     * Handle to the pending one-shot tx-reaction-clear timer, or {@code null} when none is armed.
     *
     * <p>Cancelled and rescheduled each time a new outbound reaction is sent so the clear fires a full
     * lifetime after the latest reaction.
     */
    private ScheduledTask txClearTimer;

    /**
     * Handle to the self-rearming rx-reaction-clear sweep, or {@code null} once the controller is closed.
     */
    private ScheduledTask rxSweepTimer;

    /**
     * Handle to the periodic reaction-retransmission timer, or {@code null} when retransmission is
     * disabled.
     */
    private ScheduledTask retransmissionTimer;

    /**
     * Monotonically increasing transaction id stamped into successive outbound reactions.
     *
     * <p>Lets the sender's own UI suppress its echoed reaction and lets receivers deduplicate
     * retransmissions; advanced per sent reaction.
     */
    private long nextTransactionId = 1;

    /**
     * Whether the controller has been closed.
     *
     * <p>Once set, sends and timer rescheduling become no-ops so a late callback cannot resurrect state.
     */
    private volatile boolean closed;

    /**
     * Constructs an app-data controller bound to the given channel and inbound handlers.
     *
     * <p>Starts the periodic inbound-reaction sweep immediately and, when retransmission is enabled,
     * the periodic retransmission timer; the outbound-reaction clear timer is armed only once a reaction
     * is sent. The controller owns its timers and cancels them on {@link #close()}.
     *
     * @param channel               the app-data transport seam to send through; never {@code null}
     * @param reactionObserver      the observer for inbound reaction arrival and expiry; never
     *                              {@code null}
     * @param transcriptionObserver the observer for inbound transcription fragments and their sender; never
     *                              {@code null}
     * @param rekeyHandler          the handler for inbound rekey bundles; never {@code null}
     * @param feedbackHandler       the handler for inbound peer feedback; never {@code null}
     * @param retransmissionEnabled whether to arm the reaction-retransmission timer
     * @throws NullPointerException if any handler or the channel is {@code null}
     */
    public AppDataController(AppDataChannel channel,
                            BiConsumer<Jid, Optional<ReactionInfo>> reactionObserver,
                            BiConsumer<Jid, LiveTranscriptionInfo> transcriptionObserver,
                            Consumer<E2eRekeyPayload> rekeyHandler,
                            Consumer<PeerFeedback> feedbackHandler,
                            boolean retransmissionEnabled) {
        this.channel = Objects.requireNonNull(channel, "channel cannot be null");
        this.reactionObserver = Objects.requireNonNull(reactionObserver, "reactionObserver cannot be null");
        this.transcriptionObserver = Objects.requireNonNull(transcriptionObserver, "transcriptionObserver cannot be null");
        this.rekeyHandler = Objects.requireNonNull(rekeyHandler, "rekeyHandler cannot be null");
        this.feedbackHandler = Objects.requireNonNull(feedbackHandler, "feedbackHandler cannot be null");
        this.retransmissionEnabled = retransmissionEnabled;
        startRecurringTimers();
    }

    /**
     * Constructs an app-data controller bound to the given channel with no inbound observers yet attached.
     *
     * <p>Every inbound observer defaults to a no-op, so a received reaction, transcription fragment, rekey
     * bundle, or peer feedback is decoded and demultiplexed but delivered nowhere until the matching
     * observer is attached through {@link #attachReactionObserver(BiConsumer)},
     * {@link #attachTranscriptionObserver(BiConsumer)}, {@link #attachRekeyHandler(Consumer)}, or
     * {@link #attachFeedbackHandler(Consumer)}. This breaks the construction cycle between this controller
     * and the in-call control units that observe it: the controller is built during the media-plane
     * bring-up, and the control units, which are built later in the call lifecycle, attach themselves to it.
     * The recurring timers start immediately exactly as for the fully wired constructor, so the rx-reaction
     * sweep runs even before an observer is attached (it simply expires reactions into the no-op observer).
     *
     * @param channel               the app-data transport seam to send through; never {@code null}
     * @param retransmissionEnabled whether to arm the reaction-retransmission timer
     * @throws NullPointerException if {@code channel} is {@code null}
     */
    public AppDataController(AppDataChannel channel, boolean retransmissionEnabled) {
        this(channel,
                (participant, reaction) -> {
                },
                (participant, transcription) -> {
                },
                rekey -> {
                },
                feedback -> {
                },
                retransmissionEnabled);
    }

    /**
     * Attaches the observer notified of inbound reaction arrival and expiry, replacing the current one.
     *
     * <p>Wires the in-call reaction control unit (built later than this controller) into the inbound
     * reaction path; until it is attached an inbound reaction updates the per-participant record but is
     * delivered to the no-op default. Intended to be called once during call bring-up.
     *
     * @param reactionObserver the inbound reaction observer; never {@code null}
     * @throws NullPointerException if {@code reactionObserver} is {@code null}
     */
    public void attachReactionObserver(BiConsumer<Jid, Optional<ReactionInfo>> reactionObserver) {
        this.reactionObserver = Objects.requireNonNull(reactionObserver, "reactionObserver cannot be null");
    }

    /**
     * Attaches the observer notified of inbound live-transcription fragments, replacing the current one.
     *
     * <p>Wires the in-call transcription control unit (built later than this controller) into the inbound
     * transcription path; until it is attached an inbound fragment is delivered to the no-op default.
     * Intended to be called once during call bring-up.
     *
     * @param transcriptionObserver the inbound transcription observer; never {@code null}
     * @throws NullPointerException if {@code transcriptionObserver} is {@code null}
     */
    public void attachTranscriptionObserver(BiConsumer<Jid, LiveTranscriptionInfo> transcriptionObserver) {
        this.transcriptionObserver = Objects.requireNonNull(transcriptionObserver, "transcriptionObserver cannot be null");
    }

    /**
     * Attaches the handler invoked for inbound end-to-end rekey bundles, replacing the current one.
     *
     * <p>Wires the participant-crypto rekey sink (built later than this controller) into the inbound rekey
     * path; until it is attached an inbound rekey bundle is delivered to the no-op default. Intended to be
     * called once during call bring-up.
     *
     * @param rekeyHandler the inbound rekey handler; never {@code null}
     * @throws NullPointerException if {@code rekeyHandler} is {@code null}
     */
    public void attachRekeyHandler(Consumer<E2eRekeyPayload> rekeyHandler) {
        this.rekeyHandler = Objects.requireNonNull(rekeyHandler, "rekeyHandler cannot be null");
    }

    /**
     * Attaches the handler invoked for inbound peer feedback, replacing the current one.
     *
     * <p>Wires the rate-control feedback sink (built later than this controller) into the inbound feedback
     * path; until it is attached inbound feedback is delivered to the no-op default. Intended to be called
     * once during call bring-up.
     *
     * @param feedbackHandler the inbound feedback handler; never {@code null}
     * @throws NullPointerException if {@code feedbackHandler} is {@code null}
     */
    public void attachFeedbackHandler(Consumer<PeerFeedback> feedbackHandler) {
        this.feedbackHandler = Objects.requireNonNull(feedbackHandler, "feedbackHandler cannot be null");
    }

    /**
     * Sends an emoji reaction over the app-data channel and arms its clear timer.
     *
     * <p>Stamps the reaction with the next transaction id, wraps it in an {@link AppDataMessage}, sends
     * it through the channel, retains it as the live outbound reaction for retransmission, and reschedules
     * the one-shot clear timer to fire a full lifetime later. A send on a closed controller is ignored.
     *
     * @param emoji the reaction emoji string to broadcast; never {@code null}
     * @return the transaction id assigned to the sent reaction
     * @throws NullPointerException if {@code emoji} is {@code null}
     */
    public long sendReaction(String emoji) {
        Objects.requireNonNull(emoji, "emoji cannot be null");
        if (closed) {
            return -1;
        }
        reactionLock.lock();
        try {
            var transactionId = nextTransactionId++;
            var reaction = new ReactionInfoBuilder()
                    .transactionId(transactionId)
                    .reaction(emoji)
                    .build();
            txReaction = reaction;
            channel.send(new AppDataMessageBuilder().reactionInfo(reaction).build());
            rescheduleTxClear();
            return transactionId;
        } finally {
            reactionLock.unlock();
        }
    }

    /**
     * Sends an end-to-end rekey bundle over the app-data channel.
     *
     * <p>Delegates to the channel's rekey send, which uses the distinct {@link E2eRekeyPayload} envelope
     * and the same transport selection as every other app-data send. Ignored on a closed controller.
     *
     * @param rekey the rekey bundle to send; never {@code null}
     * @throws NullPointerException if {@code rekey} is {@code null}
     */
    public void sendRekey(E2eRekeyPayload rekey) {
        Objects.requireNonNull(rekey, "rekey cannot be null");
        if (closed) {
            return;
        }
        channel.sendRekey(rekey);
    }

    /**
     * Sends peer feedback over the app-data channel.
     *
     * <p>Serializes the feedback and hands it to the channel, which ships it on the AppData stream as its
     * own top-level blob and selects the live transport (SCTP DataChannel or relay RTP) exactly as
     * {@link #sendRekey(E2eRekeyPayload)} ships a rekey bundle; the call layer publishes feedback
     * periodically. The outbound feedback is dispatched to the channel only and not looped back into the
     * inbound {@link #feedbackHandler}. Ignored on a closed controller.
     *
     * @param feedback the peer feedback to send; never {@code null}
     * @throws NullPointerException if {@code feedback} is {@code null}
     */
    public void sendFeedback(PeerFeedback feedback) {
        Objects.requireNonNull(feedback, "feedback cannot be null");
        if (closed) {
            return;
        }
        channel.sendFeedback(feedback);
    }

    /**
     * Demultiplexes a batch of inbound app-data, routing each message to its observer.
     *
     * <p>Each reaction updates the per-participant inbound record under the lock and notifies the
     * reaction observer; each transcription fragment is forwarded to the transcription observer. The
     * batch is attributed to the supplied sender so a reaction can be tracked and later expired per
     * participant. Ignored on a closed controller.
     *
     * @param sender   the device JID the batch was received from; never {@code null}
     * @param payloads the decoded inbound batch; never {@code null}
     * @throws NullPointerException if {@code sender} or {@code payloads} is {@code null}
     */
    public void onReceive(Jid sender, AppDataPayloads payloads) {
        Objects.requireNonNull(sender, "sender cannot be null");
        Objects.requireNonNull(payloads, "payloads cannot be null");
        if (closed) {
            return;
        }
        // TODO: this routes only the AppDataPayloads-batch path; the demux that tells an AppDataPayloads
        //  batch apart from a top-level E2eRekeyPayload or PeerFeedback blob arriving on the same app-data
        //  stream is a higher layer not yet reversed. The app-data stream delivers opaque payload bytes
        //  (app_data_stream.cc fn6243) under a type-agnostic "PAPR" framing, so the discriminator (a framed
        //  payload-type tag or a trial-decode order) is not recoverable from the static artifacts; it needs
        //  the app-data framing/demux capture. The routing seams for the other two blob types exist here
        //  (onRekey, onFeedback) and decode through AppDataChannel.receiveRekey / receiveFeedback; the
        //  upstream consumer (LiveMediaSession's inbound app-data consumer) currently calls only onReceive,
        //  so an inbound rekey or feedback blob reaches no handler until that demux is captured and wired.
        for (var message : payloads.messages()) {
            message.reactionInfo().ifPresent(reaction -> onRxReaction(sender, reaction));
            message.transcriptionInfo().ifPresent(transcription -> transcriptionObserver.accept(sender, transcription));
        }
    }

    /**
     * Routes an inbound rekey bundle to the rekey handler.
     *
     * <p>The rekey arrives in its own envelope rather than an {@link AppDataPayloads} batch, so it is
     * dispatched separately from {@link #onReceive(Jid, AppDataPayloads)}. Ignored on a closed
     * controller.
     *
     * @param rekey the decoded inbound rekey bundle; never {@code null}
     * @throws NullPointerException if {@code rekey} is {@code null}
     */
    public void onRekey(E2eRekeyPayload rekey) {
        Objects.requireNonNull(rekey, "rekey cannot be null");
        if (closed) {
            return;
        }
        rekeyHandler.accept(rekey);
    }

    /**
     * Routes inbound peer feedback to the feedback handler.
     *
     * <p>Like a rekey, peer feedback arrives in its own top-level envelope rather than an
     * {@link AppDataPayloads} batch, so it is dispatched separately from
     * {@link #onReceive(Jid, AppDataPayloads)}. Ignored on a closed controller.
     *
     * @param feedback the decoded inbound peer feedback; never {@code null}
     * @throws NullPointerException if {@code feedback} is {@code null}
     */
    public void onFeedback(PeerFeedback feedback) {
        Objects.requireNonNull(feedback, "feedback cannot be null");
        if (closed) {
            return;
        }
        feedbackHandler.accept(feedback);
    }

    /**
     * Returns the live outbound reaction retained for retransmission, if any.
     *
     * @return an {@link Optional} with the live outbound reaction, or empty when none is live
     */
    public Optional<ReactionInfo> outboundReaction() {
        reactionLock.lock();
        try {
            return Optional.ofNullable(txReaction);
        } finally {
            reactionLock.unlock();
        }
    }

    /**
     * Returns the live inbound reaction from the given participant, if one is currently held.
     *
     * @param participant the device JID to look up; never {@code null}
     * @return an {@link Optional} with the participant's live reaction, or empty
     * @throws NullPointerException if {@code participant} is {@code null}
     */
    public Optional<ReactionInfo> inboundReaction(Jid participant) {
        Objects.requireNonNull(participant, "participant cannot be null");
        var record = rxReactions.get(participant);
        return record == null ? Optional.empty() : Optional.of(record.reaction());
    }

    /**
     * Cancels every timer.
     *
     * <p>Marks the controller closed so a late send or callback is a no-op and cancels the three timers;
     * idempotent.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        reactionLock.lock();
        try {
            cancel(txClearTimer);
            cancel(rxSweepTimer);
            cancel(retransmissionTimer);
            txClearTimer = null;
            rxSweepTimer = null;
            retransmissionTimer = null;
        } finally {
            reactionLock.unlock();
        }
    }

    /**
     * Arms the recurring timers that run for the controller's whole lifetime.
     *
     * <p>Starts the self-rearming inbound-reaction sweep and, when retransmission is enabled, the
     * periodic retransmission timer; the one-shot outbound clear is armed lazily on the first reaction.
     */
    private void startRecurringTimers() {
        rxSweepTimer = ScheduledTask.schedule(DEFAULT_RX_REACTION_SWEEP, this::rxReactionClear);
        if (retransmissionEnabled) {
            retransmissionTimer = ScheduledTask.schedule(DEFAULT_RETRANSMISSION_INTERVAL, this::reactionRetransmission);
        }
    }

    /**
     * Reschedules the one-shot outbound-reaction clear to fire a full lifetime from now.
     *
     * <p>Cancels any pending clear and arms a fresh one so the live outbound reaction is cleared a full
     * lifetime after the latest send; called under the reaction lock. Does nothing once closed.
     */
    private void rescheduleTxClear() {
        if (closed) {
            return;
        }
        cancel(txClearTimer);
        txClearTimer = ScheduledTask.scheduleDelayed(DEFAULT_TX_REACTION_LIFETIME, this::txReactionClear);
    }

    /**
     * Clears the outbound reaction state, the outbound-reaction clear timer callback.
     *
     * <p>Drops the live outbound reaction under the lock so it stops being retransmitted, matching the
     * native callback that zeroes the outbound reaction state at {@code controller+0x108}.
     */
    private void txReactionClear() {
        reactionLock.lock();
        try {
            txReaction = null;
        } finally {
            reactionLock.unlock();
        }
    }

    /**
     * Expires aged inbound reactions, the self-rearming inbound-reaction clear timer
     * callback.
     *
     * <p>Removes every per-participant reaction older than its lifetime under the lock, then notifies the
     * reaction observer with an empty reaction for each outside the lock so the overlay is taken down,
     * matching the native sweep that clears aged reactions and re-arms itself.
     */
    private void rxReactionClear() {
        if (closed) {
            return;
        }
        var now = System.nanoTime();
        var lifetimeNanos = DEFAULT_RX_REACTION_LIFETIME.toNanos();
        List<Jid> expired = null;
        reactionLock.lock();
        try {
            var iterator = rxReactions.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (now - entry.getValue().arrivalNanos() >= lifetimeNanos) {
                    iterator.remove();
                    if (expired == null) {
                        expired = new ArrayList<>();
                    }
                    expired.add(entry.getKey());
                }
            }
        } finally {
            reactionLock.unlock();
        }
        if (expired != null) {
            for (var participant : expired) {
                // TODO: the native rx_reaction_clear sweep (fn11750) clears each aged reaction and only
                //  OPTIONALLY notifies the app on expiry; the condition gating that notify is not isolable
                //  from the static decompile (the function's expiry branch is too dense in the Ghidra
                //  output to attribute the notify call to a specific predicate). This fires the expiry
                //  notify unconditionally for every cleared reaction, which over-notifies relative to the
                //  native gate. Recovering the exact condition needs a successful targeted decompile of
                //  fn11750's expiry branch; until then the unconditional notify is the safe over-set
                //  (the UI takes an already-hidden overlay down again, never the reverse).
                reactionObserver.accept(participant, Optional.empty());
            }
        }
    }

    /**
     * Retransmits the live outbound reaction, the reaction-retransmission timer
     * callback.
     *
     * <p>Resends the retained outbound reaction over the channel under the lock so a lost packet does not
     * drop the reaction; does nothing when no outbound reaction is live.
     */
    private void reactionRetransmission() {
        if (closed) {
            return;
        }
        reactionLock.lock();
        try {
            if (txReaction != null) {
                channel.send(new AppDataMessageBuilder().reactionInfo(txReaction).build());
            }
        } finally {
            reactionLock.unlock();
        }
    }

    /**
     * Records an inbound reaction for a participant and notifies the observer.
     *
     * <p>Stores the reaction with its arrival time so the sweep can expire it, replacing any previous
     * reaction from the same participant, and delivers it to the reaction observer; called under the
     * lock.
     *
     * @param sender   the device JID the reaction came from
     * @param reaction the inbound reaction
     */
    private void onRxReaction(Jid sender, ReactionInfo reaction) {
        reactionLock.lock();
        try {
            rxReactions.put(sender, new RxReaction(reaction, System.nanoTime()));
        } finally {
            reactionLock.unlock();
        }
        reactionObserver.accept(sender, Optional.of(reaction));
    }

    /**
     * Cancels a scheduled timer if it is armed.
     *
     * <p>A {@code null} handle is ignored; cancelling wakes a pending timer so it never fires and
     * interrupts one whose callback is already running.
     *
     * @param timer the timer handle to cancel, or {@code null}
     */
    private static void cancel(ScheduledTask timer) {
        if (timer != null) {
            timer.cancel();
        }
    }

    /**
     * One inbound reaction held for a participant with the time it arrived.
     *
     * <p>The arrival time is a {@link System#nanoTime()} reading used by the sweep to decide when the
     * reaction has aged out.
     *
     * @param reaction     the inbound reaction
     * @param arrivalNanos the {@link System#nanoTime()} reading when the reaction arrived
     */
    private record RxReaction(ReactionInfo reaction, long arrivalNanos) {
    }
}
