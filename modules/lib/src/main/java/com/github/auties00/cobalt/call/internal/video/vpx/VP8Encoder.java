package com.github.auties00.cobalt.call.internal.video.vpx;

import com.github.auties00.cobalt.call.internal.video.vpx.bindings.LibVpx;
import com.github.auties00.cobalt.call.internal.video.vpx.bindings.vpx_codec_cx_pkt;
import com.github.auties00.cobalt.call.internal.video.vpx.bindings.vpx_codec_ctx;
import com.github.auties00.cobalt.call.internal.video.vpx.bindings.vpx_codec_enc_cfg;
import com.github.auties00.cobalt.call.internal.video.vpx.bindings.vpx_image;
import com.github.auties00.cobalt.call.internal.video.vpx.bindings.vpx_rational;
import com.github.auties00.cobalt.exception.WhatsAppCallException;
import com.github.auties00.cobalt.util.NativeLibLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Encodes raw I420 pictures into VP8 bitstream packets over libvpx.
 *
 * <p>This wrapper drives libvpx's {@code vpx_codec_enc_*} encoder family through
 * jextract-generated {@code LibVpx} bindings, exposing an idiomatic-Java surface.
 * It is configured for WebRTC realtime use: single-pass one-pass rate control, a
 * keyframe interval of {@code fps * 5} frames (five seconds), CPU-used 8 (the
 * fastest VP8 speed setting), and a single threading slot. These defaults suit
 * one-to-one WhatsApp video calls; richer multi-stream and SVC configurations are
 * out of scope for this class.
 *
 * <p>Input frames are I420 (YUV 4:2:0 planar) with the Y plane first, then U, then
 * V, which is the canonical WebRTC raw-frame layout. Output {@link Packet packets}
 * are complete VP8 bitstream frames suitable for direct RTP packetisation. The
 * encoder runs on a {@code (1, fps)} timebase, so the presentation timestamp passed
 * to {@link #encode(byte[], long, boolean)} is the frame index. {@link #close()}
 * destroys the native context and releases the backing arena.
 *
 * <p>A typical encode loop captures I420 frames and forwards each emitted packet to
 * the RTP layer:
 *
 * {@snippet :
 *   var enc = new VP8Encoder(640, 480, 1_000_000, 30);
 *   for (long pts = 0; capturing; pts++) {
 *       byte[] yuv = capture.nextFrame(); // 640*480 + 2*320*240 = 460800 bytes
 *       for (var pkt : enc.encode(yuv, pts, false)) {
 *           rtp.send(pkt.payload(), pkt.keyFrame());
 *       }
 *   }
 * }
 */
public final class VP8Encoder implements AutoCloseable {
    static {
        NativeLibLoader.load("vpx", Arena.global());
    }

    /**
     * Selects the VP8 CPU-used speed level applied at construction.
     *
     * @implNote This implementation uses 8, the maximum of libvpx's {@code -16..16}
     * VP8 CPU-used range, matching WebRTC's realtime speed setting; higher values
     * trade encode quality for lower latency.
     */
    private static final int VP8_REALTIME_CPU_USED = 8;

    /**
     * Holds the native allocations for this encoder.
     *
     * <p>Backs the codec context, the configuration struct, the reusable input
     * image, the per-call input scratch buffer, and the iterator scratch slot. The
     * arena is shared so the segments remain valid across the virtual threads that
     * may drive {@link #encode(byte[], long, boolean)}, and is closed by
     * {@link #close()}.
     */
    private final Arena arena;

    /**
     * References the {@code vpx_codec_ctx_t} struct backing this encoder.
     *
     * <p>Set to {@link MemorySegment#NULL} by {@link #close()} to mark the encoder
     * as destroyed; {@link #requireOpen()} treats both {@code null} and
     * {@code NULL} as closed.
     */
    private MemorySegment ctx;

    /**
     * Holds the pre-allocated {@code vpx_image_t} repopulated on each
     * {@link #encode(byte[], long, boolean)} call.
     */
    private final MemorySegment image;

    /**
     * Holds the single-pointer iterator scratch passed to
     * {@code vpx_codec_get_cx_data}.
     *
     * <p>Reset to {@link MemorySegment#NULL} at the start of every drain so the
     * encoder yields packets from the beginning of its output queue.
     */
    private final MemorySegment iter;

    /**
     * Holds the width, in pixels, of every encoded frame.
     */
    private final int width;

    /**
     * Holds the height, in pixels, of every encoded frame.
     */
    private final int height;

    /**
     * Caches the expected I420 input byte count, {@code w*h + 2*(w/2)*(h/2)}.
     */
    private final int yuvSize;

    /**
     * References the live {@code vpx_codec_enc_cfg} configuration struct.
     *
     * <p>Retained so {@link #setBitrate(int)} can mutate {@code rc_target_bitrate}
     * in place and push the updated configuration to libvpx via
     * {@code vpx_codec_enc_config_set} for runtime bandwidth-estimate adaptation.
     */
    private final MemorySegment cfg;

    /**
     * Carries one encoded VP8 packet, a complete bitstream frame ready for RTP
     * framing.
     *
     * @param payload  the encoded frame bytes
     * @param pts      the presentation timestamp the encoder was driven with for
     *                 this frame
     * @param keyFrame {@code true} if this frame is a keyframe (the start of a
     *                 group of pictures), {@code false} otherwise
     */
    public record Packet(byte[] payload, long pts, boolean keyFrame) {
        /**
         * Validates that the encoded payload is present.
         *
         * @throws NullPointerException if {@code payload} is {@code null}
         */
        public Packet {
            Objects.requireNonNull(payload, "payload cannot be null");
        }
    }

    /**
     * Constructs an encoder and initialises the underlying libvpx VP8 context.
     *
     * <p>Validates the geometry and rate parameters, caches the I420 byte size,
     * allocates the shared arena and its native structs, initialises the encoder
     * with the WebRTC realtime configuration, then applies the CPU-used control. If
     * any step throws, the arena is closed before the exception propagates so no
     * native memory is leaked. Width and height must be even because I420
     * chroma planes are subsampled by two in each dimension.
     *
     * @param width            the frame width in pixels; must be even and at least 2
     * @param height           the frame height in pixels; must be even and at least 2
     * @param targetBitrateBps the target bitrate in bits per second; must be at
     *                         least 1
     * @param fps              the capture frame rate driving the encoder timebase
     *                         and keyframe interval; must be at least 1
     * @throws IllegalArgumentException if a dimension is not even, or a dimension or
     *                                  rate is below its minimum
     * @throws WhatsAppCallException.Vpx if libvpx rejects the configuration or
     *                                   initialisation fails
     * @throws UnsatisfiedLinkError      if libvpx cannot be loaded
     */
    public VP8Encoder(int width, int height, int targetBitrateBps, int fps) {
        if (width < 1 || width % 2 != 0) throw new IllegalArgumentException("width must be even and >= 2");
        if (height < 1 || height % 2 != 0) throw new IllegalArgumentException("height must be even and >= 2");
        if (targetBitrateBps < 1) throw new IllegalArgumentException("targetBitrateBps must be >= 1");
        if (fps < 1) throw new IllegalArgumentException("fps must be >= 1");
        this.width = width;
        this.height = height;
        this.yuvSize = width * height + 2 * (width / 2) * (height / 2);
        this.arena = Arena.ofShared();
        try {
            this.ctx = vpx_codec_ctx.allocate(arena);
            this.iter = arena.allocate(ValueLayout.ADDRESS);
            this.image = vpx_image.allocate(arena);
            this.cfg = vpx_codec_enc_cfg.allocate(arena);
            initCodec(targetBitrateBps, fps);
            applyControl(LibVpx.VP8E_SET_CPUUSED(), VP8_REALTIME_CPU_USED);
        } catch (RuntimeException e) {
            arena.close();
            throw e;
        }
    }

    /**
     * Encodes one raw I420 frame and returns the VP8 packets it produced.
     *
     * <p>Wraps the input bytes as a {@code vpx_image_t}, drives
     * {@code vpx_codec_encode} on the realtime deadline, then drains the emitted
     * compressed-frame packets. The encoder may emit zero packets while buffering,
     * exactly one per frame in steady-state realtime mode, or more under unusual
     * configurations. When {@code forceKeyFrame} is set the call requests a keyframe
     * regardless of the natural keyframe cadence.
     *
     * @param yuvI420       the input frame bytes in I420 layout
     *                      ({@code w*h + 2*(w/2)*(h/2)} bytes)
     * @param pts           the presentation timestamp in encoder ticks; on the
     *                      {@code (1, fps)} timebase set up at construction this
     *                      equals the frame index
     * @param forceKeyFrame {@code true} to request that this frame be encoded as a
     *                      keyframe regardless of the natural cadence
     * @return the encoded packets, possibly empty
     * @throws NullPointerException     if {@code yuvI420} is {@code null}
     * @throws IllegalStateException    if the encoder is closed
     * @throws IllegalArgumentException if {@code yuvI420.length} is not the expected
     *                                  I420 byte count
     * @throws WhatsAppCallException.Vpx if libvpx returns a non-OK status
     */
    public List<Packet> encode(byte[] yuvI420, long pts, boolean forceKeyFrame) {
        Objects.requireNonNull(yuvI420, "yuvI420 cannot be null");
        requireOpen();
        if (yuvI420.length != yuvSize) {
            throw new IllegalArgumentException("yuvI420 must be " + yuvSize + " bytes, got " + yuvI420.length);
        }
        loadImage(yuvI420);
        var flags = forceKeyFrame ? LibVpx.VPX_EFLAG_FORCE_KF() : 0;
        int rc;
        try {
            rc = LibVpx.vpx_codec_encode(ctx, image, pts, 1, flags, LibVpx.VPX_DL_REALTIME());
        } catch (Throwable t) {
            throw new WhatsAppCallException.Vpx("vpx_codec_encode failed", t);
        }
        if (rc != LibVpx.VPX_CODEC_OK()) {
            throw WhatsAppCallException.Vpx.fromErr("vpx_codec_encode", rc);
        }
        return drainPackets();
    }

    /**
     * Returns the number of input bytes the encoder expects per
     * {@link #encode(byte[], long, boolean)} call.
     *
     * <p>Exposes the cached I420 size so callers do not have to recompute the
     * {@code w*h + 2*(w/2)*(h/2)} formula.
     *
     * @return the expected I420 input byte count
     */
    public int frameByteSize() {
        return yuvSize;
    }

    /**
     * Updates the encoder's target bitrate at runtime.
     *
     * <p>Mutates {@code rc_target_bitrate} on the live configuration and pushes it
     * to libvpx via {@code vpx_codec_enc_config_set}, letting the call's outbound
     * video track the latest bandwidth estimate from the BWE feedback loop.
     *
     * @param targetBitrateBps the new target bitrate in bits per second; must be at
     *                         least 1
     * @throws IllegalArgumentException if {@code targetBitrateBps} is below 1
     * @throws IllegalStateException    if the encoder is closed
     * @throws WhatsAppCallException.Vpx if libvpx rejects the update
     * @implNote This implementation divides the bits-per-second argument by 1000
     * because {@code rc_target_bitrate} is expressed in kilobits per second, and
     * clamps the result to a floor of 1 kbps so sub-kilobit estimates do not round
     * down to a zero target.
     */
    public void setBitrate(int targetBitrateBps) {
        if (targetBitrateBps < 1) {
            throw new IllegalArgumentException("targetBitrateBps must be >= 1");
        }
        requireOpen();
        vpx_codec_enc_cfg.rc_target_bitrate(cfg, Math.max(1, targetBitrateBps / 1000));
        int rc;
        try {
            rc = LibVpx.vpx_codec_enc_config_set(ctx, cfg);
        } catch (Throwable t) {
            throw new WhatsAppCallException.Vpx("vpx_codec_enc_config_set failed", t);
        }
        if (rc != LibVpx.VPX_CODEC_OK()) {
            throw WhatsAppCallException.Vpx.fromErr("vpx_codec_enc_config_set", rc);
        }
    }

    /**
     * Initialises the libvpx VP8 encoder with the constructor's parameters.
     *
     * <p>Loads libvpx's defaults via {@code vpx_codec_enc_config_default}, overrides
     * the WebRTC-relevant fields (geometry, single threading slot, one-pass rate
     * control, target bitrate, keyframe distance, and {@code (1, fps)} timebase),
     * then runs {@code vpx_codec_enc_init_ver} with the ABI version the bindings
     * were generated against.
     *
     * @param targetBitrateBps the target bitrate in bits per second
     * @param fps              the frame rate driving the timebase and keyframe
     *                         interval
     * @throws WhatsAppCallException.Vpx if configuration or initialisation returns a
     *                                   non-OK status
     * @implNote This implementation sets {@code kf_min_dist} to 0 and
     * {@code kf_max_dist} to {@code fps * 5}, forcing a keyframe at most every five
     * seconds while leaving libvpx free to emit earlier keyframes on scene change.
     */
    private void initCodec(int targetBitrateBps, int fps) {
        var iface = vp8CxIface();
        int rc;
        try {
            rc = LibVpx.vpx_codec_enc_config_default(iface, cfg, 0);
        } catch (Throwable t) {
            throw new WhatsAppCallException.Vpx("vpx_codec_enc_config_default failed", t);
        }
        if (rc != LibVpx.VPX_CODEC_OK()) {
            throw WhatsAppCallException.Vpx.fromErr("vpx_codec_enc_config_default", rc);
        }
        vpx_codec_enc_cfg.g_w(cfg, width);
        vpx_codec_enc_cfg.g_h(cfg, height);
        vpx_codec_enc_cfg.g_threads(cfg, 1);
        vpx_codec_enc_cfg.g_pass(cfg, LibVpx.VPX_RC_ONE_PASS());
        vpx_codec_enc_cfg.rc_target_bitrate(cfg, Math.max(1, targetBitrateBps / 1000));
        vpx_codec_enc_cfg.kf_min_dist(cfg, 0);
        vpx_codec_enc_cfg.kf_max_dist(cfg, fps * 5);
        var timebase = vpx_codec_enc_cfg.g_timebase(cfg);
        vpx_rational.num(timebase, 1);
        vpx_rational.den(timebase, fps);
        try {
            rc = LibVpx.vpx_codec_enc_init_ver(ctx, iface, cfg, 0, LibVpx.VPX_ENCODER_ABI_VERSION());
        } catch (Throwable t) {
            throw new WhatsAppCallException.Vpx("vpx_codec_enc_init_ver failed", t);
        }
        if (rc != LibVpx.VPX_CODEC_OK()) {
            throw WhatsAppCallException.Vpx.fromErr("vpx_codec_enc_init_ver", rc);
        }
    }

    /**
     * Copies the Java input frame into native memory and wraps it as the encoder's
     * {@code vpx_image_t}.
     *
     * <p>Allocates a per-call scratch buffer in the instance arena, copies the I420
     * bytes into it, then binds the pre-allocated image to that buffer via
     * {@code vpx_img_wrap} with the {@code VPX_IMG_FMT_I420} format and a stride
     * alignment of 1.
     *
     * @param yuvI420 the input I420 bytes
     * @throws WhatsAppCallException.Vpx if {@code vpx_img_wrap} returns NULL
     */
    private void loadImage(byte[] yuvI420) {
        var buf = arena.allocate(yuvSize);
        MemorySegment.copy(yuvI420, 0, buf, ValueLayout.JAVA_BYTE, 0, yuvSize);
        var wrapped = LibVpx.vpx_img_wrap(image, LibVpx.VPX_IMG_FMT_I420(), width, height, 1, buf);
        if (wrapped.equals(MemorySegment.NULL)) {
            throw new WhatsAppCallException.Vpx("vpx_img_wrap returned NULL");
        }
    }

    /**
     * Drains the encoder's pending compressed-frame packets into a list.
     *
     * <p>Iterates {@code vpx_codec_get_cx_data} until it returns NULL, keeping only
     * {@code VPX_CODEC_CX_FRAME_PKT} packets and skipping other kinds such as
     * two-pass stats and PSNR. For each frame packet it copies the bitstream bytes
     * out of native memory and records the presentation timestamp and the keyframe
     * flag.
     *
     * @return the encoded packets, possibly empty
     * @throws WhatsAppCallException.Vpx if {@code vpx_codec_get_cx_data} throws
     */
    private List<Packet> drainPackets() {
        iter.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
        var out = new ArrayList<Packet>();
        while (true) {
            MemorySegment pkt;
            try {
                pkt = LibVpx.vpx_codec_get_cx_data(ctx, iter);
            } catch (Throwable t) {
                throw new WhatsAppCallException.Vpx("vpx_codec_get_cx_data failed", t);
            }
            if (pkt.equals(MemorySegment.NULL)) {
                break;
            }
            pkt = pkt.reinterpret(vpx_codec_cx_pkt.layout().byteSize());
            var kind = vpx_codec_cx_pkt.kind(pkt);
            if (kind != LibVpx.VPX_CODEC_CX_FRAME_PKT()) {
                continue;
            }
            var data = vpx_codec_cx_pkt.data(pkt);
            var frame = vpx_codec_cx_pkt.data.frame(data);
            var bufPtr = vpx_codec_cx_pkt.data.frame.buf(frame);
            var sz = vpx_codec_cx_pkt.data.frame.sz(frame);
            var pts = vpx_codec_cx_pkt.data.frame.pts(frame);
            var flags = vpx_codec_cx_pkt.data.frame.flags(frame);
            if (sz <= 0 || bufPtr.equals(MemorySegment.NULL)) {
                continue;
            }
            var payload = bufPtr.reinterpret(sz).toArray(ValueLayout.JAVA_BYTE);
            out.add(new Packet(payload, pts, (flags & LibVpx.VPX_FRAME_IS_KEY()) != 0));
        }
        return out;
    }

    /**
     * Resolves the libvpx VP8 encoder interface pointer.
     *
     * <p>Calls the {@code vpx_codec_vp8_cx} accessor and verifies the returned
     * pointer is non-NULL.
     *
     * @return the VP8 encoder interface pointer
     * @throws WhatsAppCallException.Vpx if the accessor throws or returns NULL
     */
    private static MemorySegment vp8CxIface() {
        try {
            var iface = LibVpx.vpx_codec_vp8_cx();
            if (iface.equals(MemorySegment.NULL)) {
                throw new WhatsAppCallException.Vpx("vpx_codec_vp8_cx returned NULL");
            }
            return iface;
        } catch (Throwable t) {
            throw new WhatsAppCallException.Vpx("vpx_codec_vp8_cx failed", t);
        }
    }

    /**
     * Caches the variadic invoker for integer-valued codec controls.
     *
     * <p>Holds the {@code vpx_codec_control_} invoker specialised to the
     * {@code (ctx, ctrl_id, int)} shape shared by every {@code VP8E_SET_*} integer
     * control. Declared {@code volatile} for the double-checked lazy initialisation
     * in {@link #applyControl(int, int)}.
     */
    private static volatile LibVpx.vpx_codec_control_ INT_CONTROL_INVOKER;

    /**
     * Sends one integer-valued codec control to the encoder.
     *
     * <p>Lazily builds and caches the integer-specialised invoker on first use, then
     * applies the control and checks the status. Since {@code vpx_codec_control_} is
     * variadic in C, the bindings expose a {@code makeInvoker} factory that fixes the
     * trailing-argument layout per call site, here {@link ValueLayout#JAVA_INT}.
     *
     * @param controlId one of the {@code VP8E_*} codec-control identifiers
     * @param value     the integer payload for the control
     * @throws WhatsAppCallException.Vpx if the control invocation throws or returns a
     *                                   non-OK status
     */
    private void applyControl(int controlId, int value) {
        var invoker = INT_CONTROL_INVOKER;
        if (invoker == null) {
            synchronized (VP8Encoder.class) {
                invoker = INT_CONTROL_INVOKER;
                if (invoker == null) {
                    invoker = LibVpx.vpx_codec_control_.makeInvoker(ValueLayout.JAVA_INT);
                    INT_CONTROL_INVOKER = invoker;
                }
            }
        }
        int rc;
        try {
            rc = invoker.apply(ctx, controlId, value);
        } catch (Throwable t) {
            throw new WhatsAppCallException.Vpx("vpx_codec_control_ id=" + controlId + " failed", t);
        }
        if (rc != LibVpx.VPX_CODEC_OK()) {
            throw WhatsAppCallException.Vpx.fromErr("vpx_codec_control_ id=" + controlId, rc);
        }
    }

    /**
     * Verifies that the codec context is still live.
     *
     * <p>Treats both a {@code null} reference and a {@link MemorySegment#NULL}
     * pointer as closed.
     *
     * @throws IllegalStateException if the encoder has been closed
     */
    private void requireOpen() {
        if (ctx == null || ctx.equals(MemorySegment.NULL)) {
            throw new IllegalStateException("VP8Encoder is closed");
        }
    }

    /**
     * Destroys the codec context and releases the per-instance arena.
     *
     * <p>Idempotent: a second call after the context has been nulled returns
     * immediately. The native destroy is attempted on a best-effort basis and any
     * failure from it is swallowed so the arena is always released.
     */
    @Override
    public void close() {
        if (ctx == null || ctx.equals(MemorySegment.NULL)) {
            return;
        }
        try {
            LibVpx.vpx_codec_destroy(ctx);
        } catch (Throwable _) {
        } finally {
            ctx = MemorySegment.NULL;
            arena.close();
        }
    }
}
