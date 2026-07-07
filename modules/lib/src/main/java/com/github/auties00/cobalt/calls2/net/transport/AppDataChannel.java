package com.github.auties00.cobalt.calls2.net.transport;

import com.github.auties00.cobalt.model.call.datachannel.AppDataMessage;
import com.github.auties00.cobalt.model.call.datachannel.AppDataPayloads;
import com.github.auties00.cobalt.model.call.datachannel.AppDataPayloadsBuilder;
import com.github.auties00.cobalt.model.call.datachannel.AppDataPayloadsSpec;
import com.github.auties00.cobalt.model.call.datachannel.E2eRekeyPayload;
import com.github.auties00.cobalt.model.call.datachannel.E2eRekeyPayloadSpec;
import com.github.auties00.cobalt.model.call.datachannel.PeerFeedback;
import com.github.auties00.cobalt.model.call.datachannel.PeerFeedbackSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The single seam that ships call application-data over the one SCTP data channel, serializing the reused
 * protobuf payloads and writing them as SCTP DATA.
 *
 * <p>App-data (reactions, rekeys, subscriptions, feedback) rides the same SCTP data channel as media on a
 * WhatsApp Web call. Senders here never frame transport bytes themselves; they hand a payload to one of the
 * typed send methods and this seam serializes it and writes it to the {@link SctpAppDataSink}. While the
 * channel is still opening, payloads are buffered and flushed in order when {@link #onSctpReady()} reports
 * the channel open; a send the channel cannot accept is counted as dropped, because on the web transport
 * there is no second app-data path to fall back to.
 *
 * <p>The receive side is symmetric: raw payload bytes arriving on the data channel are decoded back into
 * the protobuf envelope and handed to the registered consumer. The seam is single-threaded for sends and
 * for the open transition it drives; the SCTP sink it delegates to owns its own concurrency.
 *
 * @implNote This implementation reproduces the app-data path of the wa-voip WASM module
 * {@code ff-tScznZ8P}: {@code wa_data_channel_controller_send_data} (fn5053) which queues a message and
 * forwards it over the host SCTP data channel, and {@code wa_data_channel_controller_flush_buffer}
 * (fn10695) which flushes the queue once the channel is ready. The capture
 * ({@code re/calls2-spec/captures/webrtc-datachannel-transport-2026-06-21.md}) confirms the single
 * SCTP-data-channel transport carries app-data as SCTP DATA, with no relay-RTP app-data fallback; the same
 * {@code Wa__Voip__AppDataPayloads}/{@code E2ERekeyPayload} protobufs are serialized once here and written
 * to the channel. The SCTP data channel itself is reached only through the {@link SctpAppDataSink}
 * functional seam, keeping this class pure Java.
 */
public final class AppDataChannel {
    /**
     * Sink writing one serialized app-data message to the SCTP data channel, reporting success.
     *
     * <p>Implemented over the transport's data-channel send seam; a {@code false} return or a thrown
     * exception is treated by this seam as a dropped message.
     */
    @FunctionalInterface
    public interface SctpAppDataSink {
        /**
         * Writes one serialized app-data message to the SCTP data channel.
         *
         * @param bytes the serialized protobuf payload to send; never {@code null}
         * @return {@code true} if the channel accepted the message, {@code false} if it could not be sent
         */
        boolean send(byte[] bytes);
    }

    /**
     * The SCTP data channel sink every app-data send is written to.
     */
    private final SctpAppDataSink sctpSink;

    /**
     * Consumer receiving each decoded inbound app-data envelope.
     *
     * <p>Invoked from {@link #receivePayloads(byte[])}; the call layer routes the contained reactions,
     * transcriptions, and other messages to their handlers.
     */
    private final Consumer<AppDataPayloads> payloadConsumer;

    /**
     * Payloads serialized while the SCTP channel was still opening, awaiting flush.
     *
     * <p>Drained in order into the channel by {@link #onSctpReady()}.
     */
    private final List<byte[]> pendingSctp = new ArrayList<>();

    /**
     * Whether the SCTP data channel has opened; while {@code false}, sends are buffered.
     */
    // TODO: wire Web-P2P DataChannelState - replace/back this boolean `ready` field with a DataChannelState field driven UNINITIALIZED->DTLS->SCTP->READY (and RELAY_FALLBACK on send-failure relay fallback), each step gating on the previous
    private volatile boolean ready;

    /**
     * Cumulative count of payloads dropped because the channel could not accept them.
     */
    private volatile long droppedPayloads;

    /**
     * Constructs an app-data channel that ships every payload over the SCTP data channel.
     *
     * <p>The channel starts not-ready: payloads are buffered until {@link #onSctpReady()} flushes them.
     *
     * @param sctpSink        the SCTP data channel sink for every app-data send; never {@code null}
     * @param payloadConsumer the consumer for decoded inbound app-data; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public AppDataChannel(SctpAppDataSink sctpSink, Consumer<AppDataPayloads> payloadConsumer) {
        this.sctpSink = Objects.requireNonNull(sctpSink, "sctpSink cannot be null");
        this.payloadConsumer = Objects.requireNonNull(payloadConsumer, "payloadConsumer cannot be null");
        this.ready = false;
    }

    /**
     * Sends a batch of app-data messages over the SCTP data channel.
     *
     * <p>Serializes the batch once, then buffers the bytes when the channel is still opening or writes them
     * to the channel when it is ready. A send the channel cannot accept is counted as dropped.
     *
     * @param payloads the batch to send; never {@code null}
     * @throws NullPointerException if {@code payloads} is {@code null}
     */
    public void send(AppDataPayloads payloads) {
        Objects.requireNonNull(payloads, "payloads cannot be null");
        dispatch(AppDataPayloadsSpec.encode(payloads));
    }

    /**
     * Wraps a single app-data message in a one-entry batch and sends it.
     *
     * <p>Convenience for the common case of one reaction or transcription fragment; produces the same
     * {@link AppDataPayloads} envelope a multi-message send uses.
     *
     * @param message the single message to send; never {@code null}
     * @throws NullPointerException if {@code message} is {@code null}
     */
    public void send(AppDataMessage message) {
        Objects.requireNonNull(message, "message cannot be null");
        send(new AppDataPayloadsBuilder()
                .messages(List.of(message))
                .build());
    }

    /**
     * Serializes and sends an end-to-end rekey bundle over the SCTP data channel.
     *
     * <p>The rekey payload uses its own protobuf envelope rather than the {@link AppDataPayloads} batch,
     * matching the distinct {@code E2ERekeyPayload} wire message, but rides the same channel as every other
     * app-data send.
     *
     * @param rekey the rekey bundle to send; never {@code null}
     * @throws NullPointerException if {@code rekey} is {@code null}
     */
    public void sendRekey(E2eRekeyPayload rekey) {
        Objects.requireNonNull(rekey, "rekey cannot be null");
        dispatch(E2eRekeyPayloadSpec.encode(rekey));
    }

    /**
     * Serializes and sends peer feedback over the SCTP data channel.
     *
     * <p>Peer feedback rides the app-data channel as its own serialized blob, exactly as
     * {@link #sendRekey(E2eRekeyPayload)} ships a rekey bundle and not wrapped in the
     * {@link AppDataPayloads} batch: the reference proto carries {@link PeerFeedback} as a top-level
     * message rather than an {@code appDataMessage} oneof slot, so it is encoded with its own spec.
     *
     * @param feedback the peer feedback to send; never {@code null}
     * @throws NullPointerException if {@code feedback} is {@code null}
     */
    public void sendFeedback(PeerFeedback feedback) {
        Objects.requireNonNull(feedback, "feedback cannot be null");
        dispatch(PeerFeedbackSpec.encode(feedback));
    }

    /**
     * Decodes a batch of app-data received over the data channel and delivers it to the consumer.
     *
     * <p>Parses the bytes back into the {@link AppDataPayloads} envelope and hands it to the registered
     * consumer; the caller has already stripped the SCTP framing.
     *
     * @param bytes the serialized {@link AppDataPayloads} payload; never {@code null}
     * @throws NullPointerException if {@code bytes} is {@code null}
     */
    public void receivePayloads(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes cannot be null");
        payloadConsumer.accept(AppDataPayloadsSpec.decode(bytes));
    }

    /**
     * Decodes a rekey bundle received over the data channel.
     *
     * <p>Parses the bytes back into the {@link E2eRekeyPayload} envelope; the caller routes the keys to the
     * SRTP and SFrame contexts.
     *
     * @param bytes the serialized {@link E2eRekeyPayload} payload; never {@code null}
     * @return the decoded rekey bundle
     * @throws NullPointerException if {@code bytes} is {@code null}
     */
    public E2eRekeyPayload receiveRekey(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes cannot be null");
        return E2eRekeyPayloadSpec.decode(bytes);
    }

    /**
     * Decodes peer feedback received over the data channel.
     *
     * <p>Parses the bytes back into the {@link PeerFeedback} envelope; the caller routes the feedback to the
     * rate-control sink. The bytes are the top-level {@link PeerFeedback} blob, not an
     * {@link AppDataPayloads} batch, mirroring {@link #sendFeedback(PeerFeedback)}.
     *
     * @param bytes the serialized {@link PeerFeedback} payload; never {@code null}
     * @return the decoded peer feedback
     * @throws NullPointerException if {@code bytes} is {@code null}
     */
    public PeerFeedback receiveFeedback(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes cannot be null");
        return PeerFeedbackSpec.decode(bytes);
    }

    /**
     * Marks the SCTP data channel ready and flushes any payloads buffered while it was opening.
     *
     * <p>Writes each buffered payload to the channel in order; a flushed write the channel cannot accept is
     * counted as dropped. Has no effect once the channel is already ready.
     */
    public void onSctpReady() {
        if (ready) {
            return;
        }
        ready = true;
        var backlog = List.copyOf(pendingSctp);
        pendingSctp.clear();
        for (var bytes : backlog) {
            writeOrDrop(bytes);
        }
    }

    /**
     * Returns whether the SCTP data channel is open and carrying sends.
     *
     * @return {@code true} once the channel is ready
     */
    public boolean isReady() {
        return ready;
    }

    /**
     * Returns the cumulative count of payloads dropped because the channel could not accept them.
     *
     * @return the dropped-payload count
     */
    public long droppedPayloads() {
        return droppedPayloads;
    }

    /**
     * Routes serialized app-data bytes to the SCTP data channel.
     *
     * <p>Buffers the bytes while the channel opens, or writes them to the channel when it is ready.
     *
     * @param bytes the serialized payload to route
     */
    private void dispatch(byte[] bytes) {
        if (!ready) {
            pendingSctp.add(bytes);
            return;
        }
        writeOrDrop(bytes);
    }

    /**
     * Writes serialized bytes to the SCTP data channel sink, counting a drop on failure.
     *
     * <p>Treats both a {@code false} return and a thrown exception as a dropped message.
     *
     * @param bytes the serialized payload to write
     */
    private void writeOrDrop(byte[] bytes) {
        try {
            if (!sctpSink.send(bytes)) {
                droppedPayloads++;
            }
        } catch (RuntimeException _) {
            droppedPayloads++;
        }
    }
}
