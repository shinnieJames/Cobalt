package com.github.auties00.cobalt.calls2.dsp;

/**
 * Receives audio/video synchronisation corrections from the video timing path and applies them to the
 * audio jitter buffer's playout delay.
 *
 * <p>This is the one-way seam from the video receive path to the audio receive path. The
 * {@link VideoTimingController} owns the measurement and produces an {@link AvSyncFeedback}; the audio
 * jitter buffer owns the playout-delay state and implements this sink to ingest the correction. The two
 * sides are wired together at call bring-up so neither has a compile-time dependency on the other's
 * concrete type: the video unit depends only on this interface, and the audio unit supplies the
 * implementation. The audio jitter buffer's ingestion entry point is
 * {@link LiveNetEq#ingestAvSyncFeedbackMillis(int)}, so the wiring-time implementation of this sink rounds
 * {@link AvSyncFeedback#correctionMs()} to whole milliseconds and forwards it there; the sign convention
 * matches, a positive correction lengthening the audio delay to wait for slower video.
 *
 * <p>The sink is invoked on the video receive thread, once per A/V-sync interval, with a correction
 * that is already bounded and weighted. A correction of zero is delivered as a heartbeat and the
 * implementation may treat it as a no-op. Because the call arrives from the video thread while the audio
 * buffer is pulled from the playback thread, an implementation that mutates audio-buffer state must
 * guard that state the same way the audio buffer guards its insert-versus-pull concurrency.
 *
 * @implNote This implementation is the Java counterpart of
 * {@code pjmedia_stream_adjust_from_avsync_feedback} in the wa-voip WASM module {@code ff-tScznZ8P}
 * ({@code rev-rtc-dsp} avsync host-API), gated natively by
 * {@code mvp->enable_avsync_feedback_ingestion_neteq}. The interface is owned by the dsp unit's video
 * jitter-buffer code and implemented by the audio NetEq, mirroring the functional-seam pattern the
 * audio receiver uses for its NetEq jitter-buffer and codec collaborators ({@code NetEqAudioSource},
 * {@code FrameDecoder}); a no-op implementation disables lip-sync correction without changing
 * interoperability.
 */
@FunctionalInterface
public interface AvSyncFeedbackSink {
    /**
     * Applies one synchronisation correction to the audio jitter buffer's playout delay.
     *
     * @param feedback the correction to apply, carrying the measured relative delay and the bounded
     *                 adjustment; never {@code null}
     */
    void applyAvSyncFeedback(AvSyncFeedback feedback);

    /**
     * Returns a sink that ignores every correction, disabling lip-sync adjustment.
     *
     * <p>Used when {@code enable_avsync_feedback_ingestion_neteq} is off or when no audio buffer is wired
     * to the video path, so the video timing controller can run without a null check.
     *
     * @return a sink whose {@link #applyAvSyncFeedback(AvSyncFeedback)} does nothing
     */
    static AvSyncFeedbackSink noop() {
        return feedback -> {
        };
    }
}
