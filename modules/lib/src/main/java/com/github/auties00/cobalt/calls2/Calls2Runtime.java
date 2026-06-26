package com.github.auties00.cobalt.calls2;

import com.github.auties00.cobalt.calls2.stream.AudioInput;
import com.github.auties00.cobalt.calls2.stream.AudioOutput;
import com.github.auties00.cobalt.calls2.stream.VideoInput;
import com.github.auties00.cobalt.calls2.stream.VideoOutput;
import com.github.auties00.cobalt.calls2.core.Calls2CallContext;
import com.github.auties00.cobalt.calls2.core.Calls2MediaPlane;
import com.github.auties00.cobalt.model.call.Call;
import com.github.auties00.cobalt.model.call.CallEndReason;
import com.github.auties00.cobalt.model.call.CallState;

import java.util.Objects;
import java.util.Optional;

/**
 * Holds the per-client, service-level live state of a single call: the application-facing counterpart of
 * the engine's per-call context.
 *
 * <p>{@link LiveCalls2Service} keeps one {@code Calls2Runtime} per active call in its registry and discards
 * it when the call ends. Where the engine's {@link Calls2CallContext} owns the transport, codec, and
 * participant machinery the wa-voip lifecycle drives, this runtime owns the slice the service layer is
 * responsible for: the public {@link Call} data view the application observes, the four media streams that
 * bridge the application and the codecs, the {@link Calls2CallStats} telemetry accumulator drained at the
 * ENDED transition, and the thirty-two-byte call key the media plane keys from. It also holds the
 * back-references the service needs to drive teardown without reaching into the engine: the engine
 * {@link Calls2CallContext} handle for this call once it is allocated, and the live
 * {@link Calls2MediaPlane.Session} once the media plane is up.
 *
 * <p>The runtime is the native {@code call_manager} per-context analogue at the host-API boundary: the
 * service's {@code activeCalls} registry of these runtimes is the host's view of the at-most-two engine
 * call contexts. {@link #end(CallEndReason)} is the single service-level teardown path: it flips the
 * {@link Call} to {@link CallState#ENDED}, stamps the telemetry accumulator, shuts the four streams so
 * blocked application reads and writes unblock, and closes the attached media-plane session if one is up.
 * It does not unregister the call from the service or emit telemetry; the service owns those steps because
 * they touch the service's registry and the WAM emitter.
 *
 * @implNote This implementation is the calls2 service-layer analogue of the legacy {@code call.CallRuntime}.
 * Unlike that runtime it does not own the transport stack or the media session construction itself: the
 * wa-voip engine builds and owns the {@link Calls2MediaPlane.Session} inside the lifecycle controller, and
 * this runtime only holds a reference to it for teardown, so the heavy media-plane machinery lives behind
 * the engine seam rather than being reproduced at the host boundary.
 */
public final class Calls2Runtime {
    /**
     * Holds the public data view this runtime drives.
     */
    private final Call call;

    /**
     * Holds the per-call telemetry accumulator owned by this runtime.
     *
     * <p>Stamped at the connected and ended lifecycle transitions and drained into a WAM Call event by
     * the service when the call is unregistered.
     */
    private final Calls2CallStats stats;

    /**
     * Holds the source the application writes local audio into and the encoder drains.
     */
    private final AudioOutput audioOut;

    /**
     * Holds the sink the decoder fills with remote audio and the application reads.
     */
    private final AudioInput audioIn;

    /**
     * Holds the source the application writes local video into and the encoder drains.
     */
    private final VideoOutput videoOut;

    /**
     * Holds the sink the decoder fills with remote video and the application reads.
     */
    private final VideoInput videoIn;

    /**
     * Holds the thirty-two-byte per-call shared key, or {@code null} until it is known.
     *
     * <p>For an outbound call it is minted locally and shipped, Signal-encrypted, to every peer device in
     * the offer; for an inbound call it is the key the caller minted, transferred here from the service's
     * pre-acceptance offer stash when the call is answered. It keys the end-to-end participant media SRTP
     * and SFrame layers.
     */
    private volatile byte[] callKey;

    /**
     * Holds the engine call context for this call once it is allocated, or {@code null} until then.
     *
     * <p>The context is allocated by the engine's call manager as the call starts; the service attaches
     * it here so a service-level lookup can reach the engine state for the call without consulting the
     * manager.
     */
    private volatile Calls2CallContext context;

    /**
     * Holds the live media-plane session driving this call once attached, or {@code null} before bring-up.
     *
     * <p>The wa-voip lifecycle controller builds and owns this session; the runtime holds the reference so
     * its {@link #end(CallEndReason)} can close it on a service-driven teardown.
     */
    private volatile Calls2MediaPlane.Session mediaSession;

    /**
     * Constructs a runtime bound to a call, its four media streams, and its telemetry accumulator.
     *
     * @param call     the public data view
     * @param stats    the per-call telemetry accumulator
     * @param audioOut the local-audio source
     * @param audioIn  the remote-audio sink
     * @param videoOut the local-video source
     * @param videoIn  the remote-video sink
     * @throws NullPointerException if any argument is {@code null}
     */
    public Calls2Runtime(Call call, Calls2CallStats stats,
                         AudioOutput audioOut, AudioInput audioIn,
                         VideoOutput videoOut, VideoInput videoIn) {
        this.call = Objects.requireNonNull(call, "call cannot be null");
        this.stats = Objects.requireNonNull(stats, "stats cannot be null");
        this.audioOut = Objects.requireNonNull(audioOut, "audioOut cannot be null");
        this.audioIn = Objects.requireNonNull(audioIn, "audioIn cannot be null");
        this.videoOut = Objects.requireNonNull(videoOut, "videoOut cannot be null");
        this.videoIn = Objects.requireNonNull(videoIn, "videoIn cannot be null");
    }

    /**
     * Returns the public data view this runtime drives.
     *
     * @return the call
     */
    public Call call() {
        return call;
    }

    /**
     * Returns this call's identifier.
     *
     * @return the call id
     */
    public String callId() {
        return call.callId();
    }

    /**
     * Returns the per-call telemetry accumulator owned by this runtime.
     *
     * @return the telemetry accumulator
     */
    public Calls2CallStats stats() {
        return stats;
    }

    /**
     * Returns the source the application writes local audio into.
     *
     * @return the local-audio source
     */
    public AudioOutput audioOut() {
        return audioOut;
    }

    /**
     * Returns the sink the application reads remote audio from.
     *
     * @return the remote-audio sink
     */
    public AudioInput audioIn() {
        return audioIn;
    }

    /**
     * Returns the source the application writes local video into.
     *
     * @return the local-video source
     */
    public VideoOutput videoOut() {
        return videoOut;
    }

    /**
     * Returns the sink the application reads remote video from.
     *
     * @return the remote-video sink
     */
    public VideoInput videoIn() {
        return videoIn;
    }

    /**
     * Records the thirty-two-byte per-call shared key for this call, replacing any previously-set key.
     *
     * @param key the call key, defensively copied; {@code null} clears it
     */
    public void callKey(byte[] key) {
        this.callKey = key == null ? null : key.clone();
    }

    /**
     * Returns the thirty-two-byte per-call shared key, when one has been set.
     *
     * <p>The returned array, when present, is a defensive copy so no caller can mutate the stored key.
     *
     * @return an {@link Optional} holding a copy of the call key, or empty until the key is set
     */
    public Optional<byte[]> callKey() {
        return Optional.ofNullable(callKey).map(byte[]::clone);
    }

    /**
     * Attaches the engine call context allocated for this call.
     *
     * @param context the engine call context
     * @throws NullPointerException if {@code context} is {@code null}
     */
    public void context(Calls2CallContext context) {
        this.context = Objects.requireNonNull(context, "context cannot be null");
    }

    /**
     * Returns the engine call context for this call, when it has been allocated.
     *
     * @return an {@link Optional} holding the engine call context, or empty until it is attached
     */
    public Optional<Calls2CallContext> context() {
        return Optional.ofNullable(context);
    }

    /**
     * Attaches the live media-plane session driving this call.
     *
     * @param session the media-plane session
     * @throws NullPointerException if {@code session} is {@code null}
     */
    public void mediaSession(Calls2MediaPlane.Session session) {
        this.mediaSession = Objects.requireNonNull(session, "session cannot be null");
    }

    /**
     * Returns the live media-plane session driving this call, when one is attached.
     *
     * @return an {@link Optional} holding the media-plane session, or empty before bring-up
     */
    public Optional<Calls2MediaPlane.Session> mediaSession() {
        return Optional.ofNullable(mediaSession);
    }

    /**
     * Drives the call's service-level state to {@link CallState#ENDED} and releases its application-facing
     * resources.
     *
     * <p>Records the end reason on the {@link Call}, stamps the telemetry accumulator's ended instant,
     * shuts down the four media streams so blocked application reads and writes return, and closes the
     * attached media-plane session if one is up. Idempotent: a second invocation after the call has ended
     * returns without effect. This does not remove the call from the service registry or commit telemetry;
     * the service performs those steps so the registry and the WAM emitter stay owned by the service.
     *
     * @param reason the canonical end reason to record on the call view
     * @throws NullPointerException if {@code reason} is {@code null}
     */
    public synchronized void end(CallEndReason reason) {
        Objects.requireNonNull(reason, "reason cannot be null");
        if (call.state() == CallState.ENDED) {
            return;
        }
        call.setState(CallState.ENDED);
        call.setEndReason(reason);
        stats.markEnded();
        audioOut.shutdown();
        audioIn.shutdown();
        videoOut.shutdown();
        videoIn.shutdown();
        var session = this.mediaSession;
        if (session != null) {
            try {
                session.close();
            } catch (RuntimeException _) {
                // A media-plane close that races the transport's own teardown is best-effort; a failure
                // here must not prevent the call from ending at the service layer.
            }
            this.mediaSession = null;
        }
    }
}
