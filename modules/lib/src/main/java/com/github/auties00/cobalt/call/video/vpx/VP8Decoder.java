package com.github.auties00.cobalt.call.video.vpx;

import com.github.auties00.cobalt.call.video.vpx.bindings.LibVpx;
import com.github.auties00.cobalt.call.video.vpx.bindings.vpx_codec_ctx;
import com.github.auties00.cobalt.call.video.vpx.bindings.vpx_image;
import com.github.auties00.cobalt.exception.WhatsAppCallException;
import com.github.auties00.cobalt.util.NativeLibLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/**
 * VP8 decoder backed by libvpx's {@code vpx_codec_dec_*} family.
 * Bindings are jextract-generated; this class is the high-level
 * idiomatic-Java wrapper.
 *
 * <p>I/O contract: input is one complete VP8 bitstream frame as
 * delivered by RTP depacketisation; output is a raw I420 planar buffer
 * with width/height observed from the decoded stream.
 *
 * <p>Pipeline:
 *
 * <pre>{@code
 *   var dec = new VP8Decoder();
 *   while (calling) {
 *       byte[] vp8 = rtp.assembleFrame();
 *       var frame = dec.decode(vp8);
 *       if (frame != null) {
 *           render.draw(frame.yuvI420(), frame.width(), frame.height());
 *       }
 *   }
 *   dec.close();
 * }</pre>
 */
public final class VP8Decoder implements AutoCloseable {
    static {
        NativeLibLoader.load("vpx", Arena.global());
    }

    /**
     * Per-instance arena for the codec context and iter scratch.
     */
    private final Arena arena;

    /**
     * Pointer to the {@code vpx_codec_ctx_t} struct backing this
     * decoder. Nulled out by {@link #close}.
     */
    private MemorySegment ctx;

    /**
     * Reusable iterator scratch (a single pointer slot) for
     * {@code vpx_codec_get_frame}.
     */
    private final MemorySegment iter;

    /**
     * One decoded raw frame in I420 planar layout.
     *
     * @param yuvI420 the decoded frame bytes
     *                ({@code w*h + 2*(w/2)*(h/2)} bytes)
     * @param width   frame width in pixels
     * @param height  frame height in pixels
     */
    public record Frame(byte[] yuvI420, int width, int height) {
        /**
         * Compact constructor — null-checks the payload.
         */
        public Frame {
            Objects.requireNonNull(yuvI420, "yuvI420 cannot be null");
        }
    }

    /**
     * Constructs a new VP8 decoder. Threads, postprocessing, and
     * error concealment are left at libvpx's defaults.
     *
     * @throws WhatsAppCallException.Vpx         if libvpx initialisation fails
     * @throws UnsatisfiedLinkError if libvpx cannot be loaded
     */
    public VP8Decoder() {
        this.arena = Arena.ofShared();
        try {
            this.ctx = vpx_codec_ctx.allocate(arena);
            this.iter = arena.allocate(ValueLayout.ADDRESS);
            initCodec();
        } catch (RuntimeException e) {
            arena.close();
            throw e;
        }
    }

    /**
     * Decodes one VP8-bitstream frame and returns the resulting raw
     * I420 frame, or {@code null} if the bitstream produced no
     * decoded picture (rare under normal RTP depacketisation, but
     * possible during error concealment).
     *
     * @param vp8 the encoded VP8 frame bytes
     * @return the decoded frame, or {@code null} if none was produced
     * @throws IllegalStateException if the decoder is closed
     * @throws WhatsAppCallException.Vpx          if libvpx returns non-OK
     */
    public Frame decode(byte[] vp8) {
        Objects.requireNonNull(vp8, "vp8 cannot be null");
        requireOpen();
        if (vp8.length == 0) {
            return null;
        }
        try (var scratch = Arena.ofConfined()) {
            var data = scratch.allocate(vp8.length);
            MemorySegment.copy(vp8, 0, data, ValueLayout.JAVA_BYTE, 0, vp8.length);
            int rc;
            try {
                rc = LibVpx.vpx_codec_decode(ctx, data, vp8.length, MemorySegment.NULL, 0);
            } catch (Throwable t) {
                throw new WhatsAppCallException.Vpx("vpx_codec_decode failed", t);
            }
            if (rc != LibVpx.VPX_CODEC_OK()) {
                throw WhatsAppCallException.Vpx.fromErr("vpx_codec_decode", rc);
            }
        }
        return drainFrame();
    }

    /**
     * Initialises the VP8 decoder via {@code vpx_codec_dec_init_ver}.
     *
     * @throws WhatsAppCallException.Vpx if init returns non-OK
     */
    private void initCodec() {
        MemorySegment iface;
        try {
            iface = LibVpx.vpx_codec_vp8_dx();
        } catch (Throwable t) {
            throw new WhatsAppCallException.Vpx("vpx_codec_vp8_dx failed", t);
        }
        if (iface.equals(MemorySegment.NULL)) {
            throw new WhatsAppCallException.Vpx("vpx_codec_vp8_dx returned NULL");
        }
        int rc;
        try {
            rc = LibVpx.vpx_codec_dec_init_ver(ctx, iface, MemorySegment.NULL, 0,
                    LibVpx.VPX_DECODER_ABI_VERSION());
        } catch (Throwable t) {
            throw new WhatsAppCallException.Vpx("vpx_codec_dec_init_ver failed", t);
        }
        if (rc != LibVpx.VPX_CODEC_OK()) {
            throw WhatsAppCallException.Vpx.fromErr("vpx_codec_dec_init_ver", rc);
        }
    }

    /**
     * Pulls the first decoded frame off the codec, copying its
     * I420 planes into a fresh {@code byte[]} laid out as Y then U
     * then V. Returns {@code null} if no frame was produced.
     *
     * @return the decoded frame, or {@code null}
     */
    private Frame drainFrame() {
        iter.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL);
        MemorySegment img;
        try {
            img = LibVpx.vpx_codec_get_frame(ctx, iter);
        } catch (Throwable t) {
            throw new WhatsAppCallException.Vpx("vpx_codec_get_frame failed", t);
        }
        if (img.equals(MemorySegment.NULL)) {
            return null;
        }
        img = img.reinterpret(vpx_image.layout().byteSize());
        int w = vpx_image.d_w(img);
        int h = vpx_image.d_h(img);
        int ySize = w * h;
        int uvSize = (w / 2) * (h / 2);
        var yuv = new byte[ySize + 2 * uvSize];
        copyPlane(img, 0, w, h, yuv, 0);
        copyPlane(img, 1, w / 2, h / 2, yuv, ySize);
        copyPlane(img, 2, w / 2, h / 2, yuv, ySize + uvSize);
        return new Frame(yuv, w, h);
    }

    /**
     * Copies one image plane out of libvpx's internal buffer,
     * accounting for the plane's possibly-padded stride.
     *
     * @param img         the {@code vpx_image_t} to read from
     * @param planeIndex  0=Y, 1=U, 2=V
     * @param planeWidth  pixel width of the plane
     * @param planeHeight pixel height of the plane
     * @param dst         destination byte array
     * @param dstOffset   destination start offset
     */
    private static void copyPlane(MemorySegment img, int planeIndex, int planeWidth, int planeHeight,
                                  byte[] dst, int dstOffset) {
        var planePtr = vpx_image.planes(img, planeIndex);
        int stride = vpx_image.stride(img, planeIndex);
        if (planePtr.equals(MemorySegment.NULL)) {
            throw new WhatsAppCallException.Vpx("vpx_image plane " + planeIndex + " is NULL");
        }
        var plane = planePtr.reinterpret((long) stride * planeHeight);
        for (int row = 0; row < planeHeight; row++) {
            MemorySegment.copy(plane, ValueLayout.JAVA_BYTE, (long) row * stride,
                    dst, dstOffset + row * planeWidth, planeWidth);
        }
    }

    /**
     * Throws if the underlying codec context has been destroyed via
     * {@link #close}.
     */
    private void requireOpen() {
        if (ctx == null || ctx.equals(MemorySegment.NULL)) {
            throw new IllegalStateException("VP8Decoder is closed");
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
