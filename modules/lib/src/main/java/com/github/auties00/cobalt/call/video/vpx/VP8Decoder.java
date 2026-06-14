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
 * Decodes VP8 bitstream frames into raw I420 pictures over libvpx.
 *
 * <p>This wrapper drives libvpx's {@code vpx_codec_dec_*} decoder family through
 * jextract-generated {@code LibVpx} bindings, exposing an idiomatic-Java surface.
 * Each input is one complete VP8 bitstream frame as delivered by RTP
 * depacketisation, and each output is a raw I420 planar buffer whose width and
 * height are observed from the decoded stream rather than fixed at construction.
 * Threading, postprocessing, and error concealment are left at libvpx's defaults.
 *
 * <p>The instance owns a native codec context and an iterator scratch slot for the
 * lifetime of the object; {@link #close()} destroys the context and releases the
 * backing arena. A typical decode loop pulls assembled VP8 frames from the RTP
 * layer and renders each non-{@code null} {@link Frame}:
 *
 * {@snippet :
 *   var dec = new VP8Decoder();
 *   while (calling) {
 *       byte[] vp8 = rtp.assembleFrame();
 *       var frame = dec.decode(vp8);
 *       if (frame != null) {
 *           render.draw(frame.yuvI420(), frame.width(), frame.height());
 *       }
 *   }
 *   dec.close();
 * }
 *
 * @implNote This implementation copies each decoded plane row-by-row out of
 * libvpx's internal buffer because libvpx pads plane rows to an alignment stride
 * that is typically wider than the visible pixel width; a flat bulk copy would
 * include that padding.
 */
public final class VP8Decoder implements AutoCloseable {
    static {
        NativeLibLoader.load("cobalt-native", Arena.global());
    }

    /**
     * Holds the native allocations for this decoder.
     *
     * <p>Backs the codec context and the iterator scratch slot. The arena is
     * shared so the segments remain valid across the virtual threads that may
     * drive {@link #decode(byte[])}, and is closed by {@link #close()}.
     */
    private final Arena arena;

    /**
     * References the {@code vpx_codec_ctx_t} struct backing this decoder.
     *
     * <p>Set to {@link MemorySegment#NULL} by {@link #close()} to mark the
     * decoder as destroyed; {@link #requireOpen()} treats both {@code null} and
     * {@code NULL} as closed.
     */
    private MemorySegment ctx;

    /**
     * Holds the single-pointer iterator scratch passed to
     * {@code vpx_codec_get_frame}.
     *
     * <p>Reset to {@link MemorySegment#NULL} at the start of every drain so the
     * decoder yields frames from the beginning of its output queue.
     */
    private final MemorySegment iter;

    /**
     * Carries one decoded raw picture in I420 (YUV 4:2:0 planar) layout.
     *
     * <p>The {@code yuvI420} buffer is laid out as the full-resolution Y plane
     * followed by the half-resolution U and V planes, totalling
     * {@code width*height + 2*(width/2)*(height/2)} bytes.
     *
     * @param yuvI420 the decoded frame bytes in Y-then-U-then-V plane order
     * @param width   the frame width in pixels
     * @param height  the frame height in pixels
     */
    public record Frame(byte[] yuvI420, int width, int height) {
        /**
         * Validates that the decoded payload is present.
         *
         * @throws NullPointerException if {@code yuvI420} is {@code null}
         */
        public Frame {
            Objects.requireNonNull(yuvI420, "yuvI420 cannot be null");
        }
    }

    /**
     * Constructs a decoder and initialises the underlying libvpx VP8 context.
     *
     * <p>Allocates the shared arena, the codec context, and the iterator scratch,
     * then runs decoder initialisation. If initialisation throws, the arena is
     * closed before the exception propagates so no native memory is leaked.
     *
     * @throws WhatsAppCallException.Vpx if libvpx initialisation fails
     * @throws UnsatisfiedLinkError      if libvpx cannot be loaded
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
     * Decodes one VP8 bitstream frame and returns its decoded I420 picture.
     *
     * <p>Copies the encoded bytes into a confined native scratch buffer, feeds them
     * to {@code vpx_codec_decode}, then drains the first decoded picture. An empty
     * input yields {@code null} without touching libvpx. The decoder may also yield
     * {@code null} when the bitstream produced no picture; this is rare under normal
     * RTP depacketisation but can occur during error concealment.
     *
     * @param vp8 the encoded VP8 frame bytes
     * @return the decoded frame, or {@code null} if none was produced
     * @throws NullPointerException     if {@code vp8} is {@code null}
     * @throws IllegalStateException    if the decoder is closed
     * @throws WhatsAppCallException.Vpx if libvpx returns a non-OK status
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
     * Initialises the libvpx VP8 decoder into the codec context.
     *
     * <p>Resolves the VP8 decoder interface, verifies it is non-NULL, then calls
     * {@code vpx_codec_dec_init_ver} with the ABI version the bindings were
     * generated against. Any native failure or non-OK status is surfaced as a
     * {@link WhatsAppCallException.Vpx}.
     *
     * @throws WhatsAppCallException.Vpx if interface resolution or initialisation
     *                                   returns a non-OK status
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
     * Pulls the first decoded picture off the codec and copies it into a fresh
     * I420 byte array.
     *
     * <p>Reinterprets the returned {@code vpx_image_t} pointer over the binding's
     * struct layout, reads the displayed width and height, allocates a buffer sized
     * {@code w*h + 2*(w/2)*(h/2)}, then copies the Y, U, and V planes in order.
     * Returns {@code null} when {@code vpx_codec_get_frame} yields no image.
     *
     * @return the decoded frame, or {@code null} if the codec produced none
     * @throws WhatsAppCallException.Vpx if {@code vpx_codec_get_frame} throws
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
        var w = vpx_image.d_w(img);
        var h = vpx_image.d_h(img);
        var ySize = w * h;
        var uvSize = (w / 2) * (h / 2);
        var yuv = new byte[ySize + 2 * uvSize];
        copyPlane(img, 0, w, h, yuv, 0);
        copyPlane(img, 1, w / 2, h / 2, yuv, ySize);
        copyPlane(img, 2, w / 2, h / 2, yuv, ySize + uvSize);
        return new Frame(yuv, w, h);
    }

    /**
     * Copies one image plane out of libvpx's internal buffer into the destination
     * array, dropping the plane's row stride padding.
     *
     * <p>Reads the plane pointer and stride from the {@code vpx_image_t}, then
     * copies {@code planeWidth} bytes per row for {@code planeHeight} rows, skipping
     * the {@code stride - planeWidth} padding bytes libvpx leaves at the end of each
     * row.
     *
     * @param img         the {@code vpx_image_t} to read from
     * @param planeIndex  the plane to copy: 0 for Y, 1 for U, 2 for V
     * @param planeWidth  the pixel width of the plane
     * @param planeHeight the pixel height of the plane
     * @param dst         the destination byte array
     * @param dstOffset   the destination start offset
     * @throws WhatsAppCallException.Vpx if the plane pointer is NULL
     */
    private static void copyPlane(MemorySegment img, int planeIndex, int planeWidth, int planeHeight,
                                  byte[] dst, int dstOffset) {
        var planePtr = vpx_image.planes(img, planeIndex);
        var stride = vpx_image.stride(img, planeIndex);
        if (planePtr.equals(MemorySegment.NULL)) {
            throw new WhatsAppCallException.Vpx("vpx_image plane " + planeIndex + " is NULL");
        }
        var plane = planePtr.reinterpret((long) stride * planeHeight);
        for (var row = 0; row < planeHeight; row++) {
            MemorySegment.copy(plane, ValueLayout.JAVA_BYTE, (long) row * stride,
                    dst, dstOffset + row * planeWidth, planeWidth);
        }
    }

    /**
     * Verifies that the codec context is still live.
     *
     * <p>Treats both a {@code null} reference and a {@link MemorySegment#NULL}
     * pointer as closed.
     *
     * @throws IllegalStateException if the decoder has been closed
     */
    private void requireOpen() {
        if (ctx == null || ctx.equals(MemorySegment.NULL)) {
            throw new IllegalStateException("VP8Decoder is closed");
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
