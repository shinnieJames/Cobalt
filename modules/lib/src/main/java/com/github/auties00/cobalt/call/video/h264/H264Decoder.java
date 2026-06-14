package com.github.auties00.cobalt.call.video.h264;

import com.github.auties00.cobalt.call.video.h264.bindings.ISVCDecoderVtbl;
import com.github.auties00.cobalt.call.video.h264.bindings.OpenH264;
import com.github.auties00.cobalt.call.video.h264.bindings.TagBufferInfo;
import com.github.auties00.cobalt.call.video.h264.bindings.TagSVCDecodingParam;
import com.github.auties00.cobalt.call.video.h264.bindings.TagSysMemBuffer;
import com.github.auties00.cobalt.exception.WhatsAppCallException;
import com.github.auties00.cobalt.util.NativeLibLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Objects;

/**
 * Decodes H.264 video into raw I420 frames through the openh264 codec library.
 *
 * <p>Consumes one or more H.264 NAL units per call and yields a planar I420
 * picture once the codec has assembled a complete frame. Input accepts both
 * Annex-B byte-stream framing (NAL units delimited by {@code 00 00 00 01} start
 * codes) and length-prefixed framing; openh264 parses either. The decoded
 * picture geometry (width, height) is taken from the sequence parameter set
 * carried in the bitstream, so callers do not configure it ahead of time.
 *
 * <p>The instance owns native resources for its whole lifetime and is not
 * thread-safe: a single decoder must be driven from one thread, or externally
 * serialised. Callers release the codec and its native memory through
 * {@link #close()}.
 *
 * @implNote This implementation drives the {@code ISVCDecoder} C++ object
 * directly rather than through a thin C shim. {@code WelsCreateDecoder} and
 * {@code WelsDestroyDecoder} are plain exported functions, but every method on
 * the codec instance ({@code Initialize}, {@code DecodeFrame2},
 * {@code Uninitialize}) lives in the virtual table whose address sits in the
 * first pointer-sized slot of the instance. Each such method is bound to a
 * {@link MethodHandle} once at construction by reading the function pointer out
 * of {@link ISVCDecoderVtbl} and the instance pointer is passed back as the
 * implicit {@code this} (here named {@code self}) on every call.
 */
public final class H264Decoder implements AutoCloseable {
    static {
        NativeLibLoader.load("cobalt-native", Arena.global());
    }

    /**
     * Backs the decoder instance pointer, the virtual-table slice, and the
     * reusable scratch structures for this decoder's whole lifetime.
     *
     * <p>Allocated as a shared arena so the native memory may be touched from
     * any thread, and closed by {@link #close()} once teardown completes.
     */
    private final Arena arena;

    /**
     * Holds the {@code ISVCDecoder} instance pointer returned by
     * {@code WelsCreateDecoder}.
     *
     * <p>Passed as the implicit {@code this} argument to every virtual-table
     * method and to {@code WelsDestroyDecoder} at teardown. Reset to
     * {@link MemorySegment#NULL} once the codec is destroyed, which marks the
     * decoder closed for {@link #requireOpen()}.
     */
    private MemorySegment self;

    /**
     * Holds the reusable {@code unsigned char *[3]} array that
     * {@code DecodeFrame2} fills with the three decoded plane pointers (Y, U,
     * V) on every call.
     */
    private final MemorySegment planesArray;

    /**
     * Holds the reusable {@code SBufferInfo} structure that
     * {@code DecodeFrame2} fills with the decode status and the system-memory
     * picture description (width, height, per-plane stride).
     */
    private final MemorySegment bufInfo;

    /**
     * Holds the cached downcall handle for the {@code DecodeFrame2} virtual
     * table method.
     */
    private final MethodHandle decodeFrame2Handle;

    /**
     * Holds the cached downcall handle for the {@code Uninitialize} virtual
     * table method.
     */
    private final MethodHandle uninitHandle;

    /**
     * Represents one decoded raw picture in I420 (YUV 4:2:0 planar) layout.
     *
     * <p>The byte array holds the full-resolution Y plane followed by the
     * half-resolution U and V planes, packed contiguously with no inter-plane
     * stride padding. Its length is therefore
     * {@snippet : width*height + 2 * (width/2)*(height/2) }
     *
     * @param yuvI420 the decoded picture bytes in I420 plane order (Y, then U,
     *                then V); never {@code null}
     * @param width   the picture width in pixels, as carried in the bitstream
     * @param height  the picture height in pixels, as carried in the bitstream
     */
    public record Frame(byte[] yuvI420, int width, int height) {
        /**
         * Validates that the picture payload is present.
         *
         * @throws NullPointerException if {@code yuvI420} is {@code null}
         */
        public Frame {
            Objects.requireNonNull(yuvI420, "yuvI420 cannot be null");
        }
    }

    /**
     * Creates a decoder and initialises the underlying openh264 instance.
     *
     * <p>Allocates the instance through {@code WelsCreateDecoder}, binds the
     * required virtual-table methods, and calls {@code Initialize} with
     * settings appropriate for realtime decode: I420 output color format, error
     * concealment disabled, and no parse-only mode. On any failure the
     * partially created native state and the arena are released before the
     * triggering exception propagates, so a failed construction leaks nothing.
     *
     * @throws WhatsAppCallException.H264 if the codec cannot be created,
     *                                    the virtual table is absent, or
     *                                    {@code Initialize} fails
     * @throws UnsatisfiedLinkError       if the openh264 native library cannot
     *                                    be loaded
     */
    public H264Decoder() {
        this.arena = Arena.ofShared();
        try {
            var slot = arena.allocate(ValueLayout.ADDRESS);
            long rc;
            try {
                rc = OpenH264.WelsCreateDecoder(slot);
            } catch (Throwable t) {
                throw new WhatsAppCallException.H264("WelsCreateDecoder failed", t);
            }
            if (rc != 0) {
                throw new WhatsAppCallException.H264("WelsCreateDecoder returned " + rc);
            }
            this.self = slot.get(ValueLayout.ADDRESS, 0);
            if (self.equals(MemorySegment.NULL)) {
                throw new WhatsAppCallException.H264("WelsCreateDecoder produced NULL decoder");
            }
            var vtableAddr = self.reinterpret(ValueLayout.ADDRESS.byteSize())
                    .get(ValueLayout.ADDRESS, 0);
            if (vtableAddr.equals(MemorySegment.NULL)) {
                throw new WhatsAppCallException.H264("decoder vtable pointer is NULL");
            }
            var vtable = vtableAddr.reinterpret(ISVCDecoderVtbl.layout().byteSize());
            var initHandle = bindVtableFn(ISVCDecoderVtbl.Initialize(vtable),
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            this.decodeFrame2Handle = bindVtableFn(ISVCDecoderVtbl.DecodeFrame2(vtable),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            this.uninitHandle = bindVtableFn(ISVCDecoderVtbl.Uninitialize(vtable),
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
            this.planesArray = arena.allocate(ValueLayout.ADDRESS, 3);
            this.bufInfo = TagBufferInfo.allocate(arena);
            initialize(initHandle);
        } catch (RuntimeException e) {
            destroyDecoder();
            arena.close();
            throw e;
        }
    }

    /**
     * Decodes one access unit and returns the resulting I420 frame, or
     * {@code null} when none is ready yet.
     *
     * <p>Copies the encoded bytes into native scratch memory, feeds them to the
     * codec, and then issues a flush call so any picture the codec is holding
     * in its reorder buffer is released in the same invocation. An empty input
     * returns {@code null} without touching the codec. Because realtime WebRTC
     * streams emit exactly one decoded picture per access unit, the flush makes
     * the picture available synchronously rather than on the next call.
     *
     * @param h264 the encoded H.264 NAL units for one access unit; never
     *             {@code null}, may be empty
     * @return the decoded frame, or {@code null} if the codec produced no
     *         complete picture
     * @throws NullPointerException       if {@code h264} is {@code null}
     * @throws IllegalStateException      if the decoder has been closed
     * @throws WhatsAppCallException.H264 if the codec reports a decode error
     */
    public Frame decode(byte[] h264) {
        Objects.requireNonNull(h264, "h264 cannot be null");
        requireOpen();
        if (h264.length == 0) {
            return null;
        }
        try (var scratch = Arena.ofConfined()) {
            var data = scratch.allocate(h264.length);
            MemorySegment.copy(h264, 0, data, ValueLayout.JAVA_BYTE, 0, h264.length);
            var frame = invokeDecode(data, h264.length);
            if (frame != null) {
                return frame;
            }
            return invokeDecode(MemorySegment.NULL, 0);
        }
    }

    /**
     * Performs a single {@code DecodeFrame2} call and materialises the result.
     *
     * <p>Clears the three plane pointers, invokes the codec with the given
     * source bytes (or a null/zero pair to flush), and returns a {@link Frame}
     * only when the codec marks the buffer status as holding a complete
     * picture. Used both for the input-feed call and the flush call inside
     * {@link #decode(byte[])}.
     *
     * @param src    the pointer to the bitstream bytes, or
     *               {@link MemorySegment#NULL} to flush
     * @param srcLen the byte length of {@code src}, or {@code 0} to flush
     * @return the decoded frame, or {@code null} if the call produced no
     *         complete picture
     * @throws WhatsAppCallException.H264 if the codec call fails or returns a
     *                                    non-zero status
     */
    private Frame invokeDecode(MemorySegment src, int srcLen) {
        for (var i = 0; i < 3; i++) {
            planesArray.setAtIndex(ValueLayout.ADDRESS, i, MemorySegment.NULL);
        }
        int rc;
        try {
            rc = (int) decodeFrame2Handle.invokeExact(self, src, srcLen, planesArray, bufInfo);
        } catch (Throwable t) {
            throw new WhatsAppCallException.H264("ISVCDecoder.DecodeFrame2 failed", t);
        }
        if (rc != 0) {
            throw new WhatsAppCallException.H264("ISVCDecoder.DecodeFrame2 returned " + rc);
        }
        if (TagBufferInfo.iBufferStatus(bufInfo) != 1) {
            return null;
        }
        return readFrame();
    }

    /**
     * Copies the decoded picture out of the codec's internal buffers into a
     * packed I420 byte array.
     *
     * <p>Reads the picture width, height, and per-plane stride from the
     * system-memory buffer description in {@link #bufInfo}, then copies the
     * full-resolution Y plane and the two half-resolution chroma planes from
     * the pointers in {@link #planesArray} into a freshly allocated array. The
     * chroma plane dimensions are derived as {@code width/2} by
     * {@code height/2}, matching the 4:2:0 subsampling of I420.
     *
     * @return the decoded frame
     * @throws WhatsAppCallException.H264 if any decoded plane pointer is null
     */
    private Frame readFrame() {
        var sysBuf = TagBufferInfo.UsrData.sSystemBuffer(TagBufferInfo.UsrData(bufInfo));
        var w = TagSysMemBuffer.iWidth(sysBuf);
        var h = TagSysMemBuffer.iHeight(sysBuf);
        var yStride = TagSysMemBuffer.iStride(sysBuf, 0);
        var uvStride = TagSysMemBuffer.iStride(sysBuf, 1);
        var ySize = w * h;
        var uvSize = (w / 2) * (h / 2);
        var out = new byte[ySize + 2 * uvSize];
        copyPlane(0, w, h, yStride, out, 0);
        copyPlane(1, w / 2, h / 2, uvStride, out, ySize);
        copyPlane(2, w / 2, h / 2, uvStride, out, ySize + uvSize);
        return new Frame(out, w, h);
    }

    /**
     * Copies one decoded plane into the destination array row by row.
     *
     * <p>The codec's plane buffers are laid out with a line stride that may
     * exceed the plane's pixel width (the buffer is padded for alignment), so
     * each row is copied for exactly {@code planeWidth} bytes from a source
     * offset advanced by {@code stride}, packing the destination tightly.
     *
     * @param planeIndex  the plane to copy: {@code 0} for Y, {@code 1} for U,
     *                    {@code 2} for V
     * @param planeWidth  the plane width in pixels
     * @param planeHeight the plane height in pixels
     * @param stride      the plane byte stride between successive rows in the
     *                    source buffer
     * @param dst         the destination array
     * @param dstOffset   the offset into {@code dst} at which this plane starts
     * @throws WhatsAppCallException.H264 if the plane pointer is null
     */
    private void copyPlane(int planeIndex, int planeWidth, int planeHeight, int stride,
                           byte[] dst, int dstOffset) {
        var planePtr = planesArray.getAtIndex(ValueLayout.ADDRESS, planeIndex);
        if (planePtr.equals(MemorySegment.NULL)) {
            throw new WhatsAppCallException.H264("decoded plane " + planeIndex + " is NULL");
        }
        var plane = planePtr.reinterpret((long) stride * planeHeight);
        for (var row = 0; row < planeHeight; row++) {
            MemorySegment.copy(plane, ValueLayout.JAVA_BYTE, (long) row * stride,
                    dst, dstOffset + row * planeWidth, planeWidth);
        }
    }

    /**
     * Calls the codec's {@code Initialize} method with realtime decode
     * settings.
     *
     * <p>Populates an {@code SDecodingParam} with full CPU availability, all
     * dependency layers targeted, error concealment disabled, and parse-only
     * mode off, then invokes the cached {@code Initialize} handle. The
     * parameter struct lives in a per-call confined arena that is released as
     * soon as initialisation returns.
     *
     * @param initHandle the cached downcall handle for the {@code Initialize}
     *                   virtual table method
     * @throws WhatsAppCallException.H264 if the codec call fails or returns a
     *                                    non-zero status
     * @implNote This implementation sets {@code uiTargetDqLayer} to {@code 0xff}
     * (the all-layers sentinel) and {@code uiCpuLoad} to {@code 100}, which are
     * openh264's documented defaults for decoding the highest available layer
     * without artificial CPU throttling.
     */
    private void initialize(MethodHandle initHandle) {
        try (var scratch = Arena.ofConfined()) {
            var param = TagSVCDecodingParam.allocate(scratch);
            TagSVCDecodingParam.uiCpuLoad(param, 100);
            TagSVCDecodingParam.uiTargetDqLayer(param, (byte) 0xff);
            TagSVCDecodingParam.eEcActiveIdc(param, 0);
            TagSVCDecodingParam.bParseOnly(param, false);
            long rc;
            try {
                rc = (long) initHandle.invokeExact(self, param);
            } catch (Throwable t) {
                throw new WhatsAppCallException.H264("ISVCDecoder.Initialize failed", t);
            }
            if (rc != 0) {
                throw new WhatsAppCallException.H264("ISVCDecoder.Initialize returned " + rc);
            }
        }
    }

    /**
     * Binds a virtual-table function pointer to a downcall handle.
     *
     * @param fnPtr the function pointer read from the virtual table struct
     * @param desc  the native signature descriptor for the pointed-to function
     * @return a method handle suitable for {@code invokeExact} dispatch
     * @throws WhatsAppCallException.H264 if {@code fnPtr} is null
     */
    private static MethodHandle bindVtableFn(MemorySegment fnPtr, FunctionDescriptor desc) {
        if (fnPtr.equals(MemorySegment.NULL)) {
            throw new WhatsAppCallException.H264("vtable function pointer is NULL");
        }
        return Linker.nativeLinker().downcallHandle(fnPtr, desc);
    }

    /**
     * Verifies that the decoder is still open.
     *
     * @throws IllegalStateException if the decoder has been closed
     */
    private void requireOpen() {
        if (self == null || self.equals(MemorySegment.NULL)) {
            throw new IllegalStateException("H264Decoder is closed");
        }
    }

    /**
     * Destroys the native decoder instance if it is still live.
     *
     * <p>Calls {@code WelsDestroyDecoder} and nulls {@link #self} so subsequent
     * calls become no-ops. Any failure inside the native destroy call is
     * swallowed because teardown must not raise.
     */
    private void destroyDecoder() {
        if (self == null || self.equals(MemorySegment.NULL)) {
            return;
        }
        try {
            OpenH264.WelsDestroyDecoder(self);
        } catch (Throwable _) {
        }
        self = MemorySegment.NULL;
    }

    /**
     * Releases the decoder and all native resources it holds.
     *
     * <p>Calls {@code Uninitialize} through the virtual table, destroys the
     * codec instance, and closes the arena. The call is idempotent: once the
     * decoder has been closed, further calls return immediately.
     */
    @Override
    public void close() {
        if (self == null || self.equals(MemorySegment.NULL)) {
            return;
        }
        try {
            uninitHandle.invokeExact(self);
        } catch (Throwable _) {
        }
        destroyDecoder();
        arena.close();
    }
}
