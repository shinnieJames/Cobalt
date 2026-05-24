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
 * VP8 encoder backed by libvpx's {@code vpx_codec_enc_*} family.
 * Bindings are jextract-generated; this class is the high-level
 * idiomatic-Java wrapper.
 *
 * <p>Configured for WebRTC realtime use: single-pass VBR rate control,
 * keyframe interval of {@code fps * 5} (5 s), CPU-used 8 (fastest),
 * one threading slot. The defaults are appropriate for 1:1 WhatsApp
 * video calls; richer multi-stream/SVC configurations live in the VP8
 * branch of the video pipeline (#62).
 *
 * <p>I/O contract: input frames are I420 (YUV 4:2:0 planar) with the
 * Y plane first, then U, then V — the canonical WebRTC raw-frame
 * layout. Output packets are complete VP8-bitstream frames suitable
 * for direct RTP packetisation.
 *
 * <p>Pipeline:
 *
 * <pre>{@code
 *   var enc = new VP8Encoder(640, 480, 1_000_000, 30);
 *   for (long pts = 0; capturing; pts++) {
 *       byte[] yuv = capture.nextFrame();          // 640*480 + 2*320*240 = 460_800 bytes
 *       for (var pkt : enc.encode(yuv, pts, false)) {
 *           rtp.send(pkt.payload(), pkt.keyFrame());
 *       }
 *   }
 * }</pre>
 */
public final class VP8Encoder implements AutoCloseable {
    static {
        NativeLibLoader.load("vpx", Arena.global());
    }

    /**
     * libvpx CPU-used parameter for VP8 (-16 .. 16). Higher means
     * faster/lower-quality. WebRTC uses 8 for realtime; we mirror.
     */
    private static final int VP8_REALTIME_CPU_USED = 8;

    /**
     * Per-instance arena for the codec context, config, image, and
     * iter scratch.
     */
    private final Arena arena;

    /**
     * Pointer to the {@code vpx_codec_ctx_t} struct backing this
     * encoder. Nulled out by {@link #close}.
     */
    private MemorySegment ctx;

    /**
     * Pre-allocated input image — populated in place each
     * {@link #encode} call.
     */
    private final MemorySegment image;

    /**
     * Reusable iterator scratch (a single pointer slot) for
     * {@code vpx_codec_get_cx_data}.
     */
    private final MemorySegment iter;

    /**
     * Width of every encoded frame, in pixels.
     */
    private final int width;

    /**
     * Height of every encoded frame, in pixels.
     */
    private final int height;

    /**
     * Cached I420 byte size: {@code w*h + 2*(w/2)*(h/2)}.
     */
    private final int yuvSize;

    /**
     * Live encoder configuration — kept around so
     * {@link #setBitrate(int)} can mutate {@code rc_target_bitrate}
     * and pass the updated config to {@code vpx_codec_enc_config_set}
     * for runtime BWE adaptation.
     */
    private final MemorySegment cfg;

    /**
     * One encoded VP8 packet — a complete frame in the VP8 bitstream
     * suitable for RTP framing.
     *
     * @param payload  the encoded frame bytes
     * @param pts      the presentation timestamp the encoder was
     *                 driven with for this frame
     * @param keyFrame {@code true} if this is a keyframe (start of a
     *                 GOP), {@code false} otherwise
     */
    public record Packet(byte[] payload, long pts, boolean keyFrame) {
        /**
         * Compact constructor — null-checks the payload.
         */
        public Packet {
            Objects.requireNonNull(payload, "payload cannot be null");
        }
    }

    /**
     * Constructs a new VP8 encoder.
     *
     * @param width             frame width in pixels
     * @param height            frame height in pixels
     * @param targetBitrateBps  target bitrate in bits per second
     * @param fps               capture frame rate; drives the
     *                          encoder's timebase and keyframe interval
     * @throws IllegalArgumentException if any argument is &lt; 1
     * @throws WhatsAppCallException.Vpx             if libvpx rejects the config
     *                                  or initialisation fails
     * @throws UnsatisfiedLinkError     if libvpx cannot be loaded
     */
    public VP8Encoder(int width, int height, int targetBitrateBps, int fps) {
        if (width < 1 || width % 2 != 0) throw new IllegalArgumentException("width must be even and ≥ 2");
        if (height < 1 || height % 2 != 0) throw new IllegalArgumentException("height must be even and ≥ 2");
        if (targetBitrateBps < 1) throw new IllegalArgumentException("targetBitrateBps must be ≥ 1");
        if (fps < 1) throw new IllegalArgumentException("fps must be ≥ 1");
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
     * Encodes one raw I420 frame and returns any VP8 packets the
     * encoder produced for it. The encoder may emit zero packets
     * when buffering, exactly one per frame in steady-state realtime
     * mode, or more under unusual configurations.
     *
     * @param yuvI420        the input frame bytes in I420 layout
     *                       ({@code w*h + 2*(w/2)*(h/2)} bytes)
     * @param pts            presentation timestamp in encoder ticks
     *                       (the {@code (1, fps)} timebase set up at
     *                       construction means {@code pts = frame index})
     * @param forceKeyFrame  if {@code true}, request that this frame
     *                       be encoded as a keyframe regardless of
     *                       the natural GOP cadence
     * @return zero or more output packets
     * @throws IllegalStateException    if the encoder is closed
     * @throws IllegalArgumentException if {@code yuvI420.length} is
     *                                  not the expected I420 byte
     *                                  count
     * @throws WhatsAppCallException.Vpx             if libvpx returns non-OK
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
     * Returns the number of bytes the encoder expects per
     * {@link #encode} input — provided so callers don't have to
     * recompute the I420 size formula.
     *
     * @return {@code w*h + 2*(w/2)*(h/2)}
     */
    public int frameByteSize() {
        return yuvSize;
    }

    /**
     * Updates the encoder's target bitrate at runtime — drives the
     * BWE feedback loop so the call's outbound video tracks the
     * latest bandwidth estimate. Mutates {@code rc_target_bitrate}
     * on the live {@code vpx_codec_enc_cfg} and pushes it to libvpx
     * via {@code vpx_codec_enc_config_set}.
     *
     * @param targetBitrateBps the new target bitrate in bps; must be
     *                         &gt;= 1
     * @throws IllegalArgumentException if {@code targetBitrateBps} is
     *                                  &lt; 1
     * @throws WhatsAppCallException.Vpx             if libvpx rejects the update
     */
    public void setBitrate(int targetBitrateBps) {
        if (targetBitrateBps < 1) {
            throw new IllegalArgumentException("targetBitrateBps must be ≥ 1");
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
     * Initialises the VPX encoder with the constructor's parameters.
     * Reads libvpx's defaults via {@code vpx_codec_enc_config_default},
     * overrides the WebRTC-relevant fields, then runs
     * {@code vpx_codec_enc_init_ver}.
     *
     * @param targetBitrateBps target bitrate in bps
     * @param fps              frame rate
     * @throws WhatsAppCallException.Vpx if config or init returns non-OK
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
     * Wraps the current Java input byte[] as a {@code vpx_image_t}
     * pointing at a per-call native scratch buffer. Re-used per
     * encode call.
     *
     * @param yuvI420 the input I420 bytes
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
     * Iterates {@code vpx_codec_get_cx_data} until it returns NULL,
     * collecting any compressed-frame packets into a list. Other
     * packet kinds (stats, PSNR) are skipped.
     *
     * @return zero or more output packets
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
     * Resolves the VP8 encoder iface pointer via libvpx's
     * {@code vpx_codec_vp8_cx} accessor function.
     *
     * @return the iface pointer
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
     * Lazily-cached variadic invoker for {@code vpx_codec_control_}
     * specialised to {@code (ctx, ctrl_id, int)} — the typed shape of
     * every {@code VP8E_SET_*} integer control.
     */
    private static volatile LibVpx.vpx_codec_control_ INT_CONTROL_INVOKER;

    /**
     * Sends one integer-valued codec control to the encoder.
     * {@code vpx_codec_control_} is variadic in C — the jextract
     * surface exposes a {@code makeInvoker} factory that specialises
     * the trailing-arg layout per-call-site.
     *
     * @param controlId one of the {@code VP8E_*} codec-control IDs
     * @param value     the integer payload
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
     * Throws if the underlying codec context has been destroyed via
     * {@link #close}.
     */
    private void requireOpen() {
        if (ctx == null || ctx.equals(MemorySegment.NULL)) {
            throw new IllegalStateException("VP8Encoder is closed");
        }
    }

    /**
     * Destroys the codec context and releases the per-instance arena.
     * Idempotent.
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
