package com.github.auties00.cobalt.calls2.core.control;

import com.github.auties00.cobalt.model.jid.Jid;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Drives the in-call IMU control: publishing the local device's motion samples and tracking inbound ones.
 *
 * <p>Inertial-measurement-unit samples are uploaded on the call's application-data stream, the same
 * transport that carries reactions and transcripts, packed into fixed
 * {@linkplain ImuSample#FRAME_SIZE thirty-six-byte} frames. {@link #publish(ImuSample)} hands a local
 * sample to the application-data stream sender and retains it as the latest sample sent; inbound peer
 * samples are delivered through {@link #onSample(Jid, ImuSample)}, which records the latest sample per
 * participant and notifies the inbound observer. The controller holds only the most recent sample in each
 * direction, since IMU is a continuous best-effort stream where stale samples have no value.
 *
 * <p>Unlike the other in-call controls, IMU has no host-facing {@link com.github.auties00.cobalt.calls2.core.CallEvent}
 * in the recovered event table, so this controller emits none; it bridges between the local capture and
 * the application-data stream and exposes the latest inbound sample per participant for a consumer such as
 * a video renderer. The latest-sample state is held in a volatile field; the controller is bound to its
 * sample sender and the inbound observer at construction and owns no timers.
 *
 * @implNote This implementation reproduces the {@code imu_data} handling of the wa-voip WASM module
 * {@code ff-tScznZ8P}: {@code send_imu_data_on_stream} (fn11527) drains a mutex-guarded ring of
 * {@code 0x25}-byte entries whose leading {@linkplain ImuSample#FRAME_SIZE 0x24 bytes} are the frame and
 * whose trailing byte is the entry's used-flag ({@code imu_data_controller.cc:259} tests the flag at
 * {@code entry+0x24} and {@code imu_data_controller.cc:260} copies exactly {@code 0x24} bytes verbatim into
 * the send buffer), batches {@code frames_per_packet} of them, and writes them on the self participant's
 * {@code app_data_stream} through the same {@code fn6246} the AppData stream uses
 * ({@code imu_data_controller.cc:297}), over the SCTP data channel and clocked at the IMU controller's
 * configured rate; there is no corresponding entry in the event table. IMU therefore shares the AppData
 * SCTP stream rather than taking a distinct stream id, and the frame carries no on-wire sub-type byte (the
 * whole {@code 0x24} bytes are the sample); an {@code imu_data} SSRC media-type code ({@code 10}) is
 * allocated per device but the controller's frames do not travel on that SSRC set. The internal field
 * semantics of the {@code 0x24}-byte sample are owned by the native mobile host's sensor enqueue, not by
 * this shared core: {@code wa_update_imu_data_controller} is a config-key name read by the parameter-apply
 * path ({@code fn10606}), not an export of this module, and no member of the controller fills the ring (the
 * feature is config-gated, with only a test-only {@code enable_mock_imu_data_sender} path whose data also
 * originates in the host). Carrying the reading as opaque bytes is therefore the faithful web/shared-core
 * behavior rather than a recovery gap. Cobalt keeps the latest sample in each direction; the single
 * latest-outbound-sample reference is held in a volatile field rather than behind the info-mutex, per the
 * threading design.
 */
public final class ImuDataController {
    // The internal field layout of the 0x24-byte IMU sample (ImuSample.FRAME_SIZE) is not a property of
    //  this module: it is a mobile sensor reading produced by the native iOS/Android host, and the shared
    //  wa-voip core ff-tScznZ8P only transports it verbatim. Carrying the sample as opaque bytes is the
    //  faithful behavior for the web/shared-core target Cobalt reimplements, NOT a recovery gap. Evidence
    //  from ff-tScznZ8P:
    //  - The controller struct (fn11521, wa_imu_data_controller_create, size 0x58) allocates the entry ring
    //    (buffer_size * 0x25 bytes, field [5]/+0x14) and the send buffer (fpp * 0x24 bytes, field [0xc]/+0x30)
    //    EMPTY; no member writes sample bytes into a ring slot.
    //  - fn11522 (ring reset) only clears the used-flag at entry+0x24 to 0 for every entry; it is a clear,
    //    not a fill.
    //  - fn11527 (send_imu_data_on_stream) drains ready entries by copying exactly 0x24 bytes verbatim
    //    (fn9180 memcpy, imu_data_controller.cc:259/260) into the send buffer and writing them on the self
    //    participant's app_data_stream via fn6246 (imu_data_controller.cc:297); it never inspects the bytes.
    //  - The controller's entire public API is create / start / pause / resume / destroy / update_config /
    //    reset_segment_stats; there is no enqueue/add-sample entry, and no other function in the module sets
    //    the entry+0x24 used-flag to 1.
    //  - wa_update_imu_data_controller (string at data 0x49682, referenced only by fn10606 config-apply and
    //    fn11526 start logging) is NOT a WASM export of this module; the export table is solely Emscripten /
    //    libc / C++ runtime glue. The host enqueue that fills the frame lives outside this binary.
    //  - The feature is config-gated (mvp->imu_data.{enable_imu_data_stream, imu_data_fpp,
    //    imu_data_circular_buffer_size, imu_data_stream_enc_clock_rate_hz}) and has a test-only fill path
    //    (mvp->imu_data.enable_mock_imu_data_sender) whose mock data also originates in the host, so the
    //    shared core produces no real samples on its own.
    //  Recovering the accel/gyro/orientation field meanings would require the native mobile sensor-enqueue
    //  source or a wearable IMU data-channel capture, neither of which is part of this module. (Device
    //  orientation, by contrast, IS recovered: it travels in signaling as the device_orientation attribute
    //  on VideoState/group-info, a separate path from this continuous motion stream.)

    /**
     * The application-data stream sender local IMU samples are published through.
     */
    private final Consumer<ImuSample> streamSender;

    /**
     * The observer notified when an inbound peer IMU sample arrives.
     */
    private final BiConsumer<Jid, ImuSample> inboundObserver;

    /**
     * The latest inbound sample per participant, keyed by device JID.
     */
    private final Map<Jid, ImuSample> latestInbound = new ConcurrentHashMap<>();

    /**
     * The latest local sample published, or {@code null} when none has been published.
     *
     * <p>Volatile so {@link #publish(ImuSample)} can store it and {@link #latestOutbound()} can read it
     * without a lock: the field is a lone reference with no compound read-modify-write.
     */
    private volatile ImuSample latestOutbound;

    /**
     * Constructs an IMU controller bound to the application-data stream sender and the inbound observer.
     *
     * @param streamSender    the application-data stream sender to publish local samples through; never
     *                        {@code null}
     * @param inboundObserver the observer for inbound peer IMU samples; never {@code null}
     * @throws NullPointerException if either argument is {@code null}
     */
    public ImuDataController(Consumer<ImuSample> streamSender, BiConsumer<Jid, ImuSample> inboundObserver) {
        this.streamSender = Objects.requireNonNull(streamSender, "streamSender cannot be null");
        this.inboundObserver = Objects.requireNonNull(inboundObserver, "inboundObserver cannot be null");
    }

    /**
     * Publishes the local device's IMU sample on the application-data stream.
     *
     * <p>Hands the sample to the application-data stream sender and retains it as the latest local sample.
     *
     * @param sample the local IMU sample to publish; never {@code null}
     * @throws NullPointerException if {@code sample} is {@code null}
     */
    public void publish(ImuSample sample) {
        Objects.requireNonNull(sample, "sample cannot be null");
        latestOutbound = sample;
        streamSender.accept(sample);
    }

    /**
     * Records an inbound peer IMU sample and notifies the inbound observer.
     *
     * <p>Replaces the participant's latest inbound sample and delivers the sample to the observer.
     *
     * @param participant the device JID the sample came from; never {@code null}
     * @param sample      the inbound IMU sample; never {@code null}
     * @throws NullPointerException if {@code participant} or {@code sample} is {@code null}
     */
    public void onSample(Jid participant, ImuSample sample) {
        Objects.requireNonNull(participant, "participant cannot be null");
        Objects.requireNonNull(sample, "sample cannot be null");
        latestInbound.put(participant, sample);
        inboundObserver.accept(participant, sample);
    }

    /**
     * Returns the latest local IMU sample published, if any.
     *
     * @return an {@link Optional} with the latest local sample, or empty when none has been published
     */
    public Optional<ImuSample> latestOutbound() {
        return Optional.ofNullable(latestOutbound);
    }

    /**
     * Returns the latest inbound IMU sample from a participant, if one is held.
     *
     * @param participant the device JID to look up; never {@code null}
     * @return an {@link Optional} with the participant's latest inbound sample, or empty
     * @throws NullPointerException if {@code participant} is {@code null}
     */
    public Optional<ImuSample> latestInbound(Jid participant) {
        Objects.requireNonNull(participant, "participant cannot be null");
        return Optional.ofNullable(latestInbound.get(participant));
    }
}
