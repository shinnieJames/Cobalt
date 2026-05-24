package com.github.auties00.cobalt.call.internal.video.h264;

import com.github.auties00.cobalt.call.internal.video.h264.bindings.ISVCDecoderVtbl;
import com.github.auties00.cobalt.call.internal.video.h264.bindings.OpenH264;
import com.github.auties00.cobalt.call.internal.video.h264.bindings.TagBufferInfo;
import com.github.auties00.cobalt.call.internal.video.h264.bindings.TagSVCDecodingParam;
import com.github.auties00.cobalt.call.internal.video.h264.bindings.TagSysMemBuffer;
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
 * H.264 decoder backed by openh264's {@code ISVCDecoder} vtable. The
 * {@code WelsCreateDecoder} / {@code WelsDestroyDecoder} entry points
 * are plain functions; everything else (Initialize, DecodeFrame2,
 * Uninitialize) is dispatched through function-pointer fields of
 * {@link ISVCDecoderVtbl}.
 *
 * <p>I/O contract: input is one or more H.264 NAL units in byte-stream
 * format (annexed or length-prefixed — openh264 accepts both); output
 * is a raw I420 planar buffer with width/height observed from the
 * decoded SPS.
 */
public final class H264Decoder implements AutoCloseable {
    static {
        NativeLibLoader.load("openh264", Arena.global());
    }

    /**
     * Per-instance arena for the decoder pointer, the vtable slice,
     * and the reusable scratch buffers.
     */
    private final Arena arena;

    /**
     * The {@code ISVCDecoder} pointer returned by
     * {@code WelsCreateDecoder} — passed as {@code self} to every
     * vtable method, and to {@code WelsDestroyDecoder} at teardown.
     */
    private MemorySegment self;

    /**
     * Reusable {@code unsigned char *[3]} array for the decoded
     * plane pointers populated by every {@code DecodeFrame2} call.
     */
    private final MemorySegment planesArray;

    /**
     * Reusable {@link TagBufferInfo} populated by
     * {@code DecodeFrame2}.
     */
    private final MemorySegment bufInfo;

    /**
     * Cached downcall handle for {@code ISVCDecoderVtbl.DecodeFrame2}.
     */
    private final MethodHandle decodeFrame2Handle;

    /**
     * Cached downcall handle for {@code ISVCDecoderVtbl.Uninitialize}.
     */
    private final MethodHandle uninitHandle;

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
     * Constructs a new H.264 decoder with openh264's defaults — no
     * postprocessing, error concealment off, output color format
     * I420.
     *
     * @throws WhatsAppCallException.H264        if openh264 initialisation fails
     * @throws UnsatisfiedLinkError if libopenh264 cannot be loaded
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
     * Decodes one H.264 access unit and returns the resulting raw
     * I420 frame, or {@code null} if the decoder has not yet
     * produced a complete picture.
     *
     * <p>Internally drives {@code DecodeFrame2} twice: once with the
     * input bitstream, then once with {@code (NULL, 0)} to flush any
     * pending decoded frame held by openh264's internal reorder
     * buffer. WebRTC realtime streams emit one decoded frame per
     * input access unit, so the flush call yields the picture
     * synchronously — this matches openh264's documented sample
     * pattern.
     *
     * @param h264 the encoded H.264 bytes
     * @return the decoded frame, or {@code null} if none was produced
     * @throws IllegalStateException if the decoder is closed
     * @throws WhatsAppCallException.H264         if openh264 returns non-zero
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
     * Single {@code DecodeFrame2} call that reads back the plane
     * pointers and {@link TagBufferInfo} and turns them into a
     * {@link Frame} if the decoder produced one. Used both for the
     * input-feed call and the flush call inside {@link #decode}.
     *
     * @param src    pointer to bitstream bytes (or {@code NULL} for
     *               flush)
     * @param srcLen byte length (or {@code 0} for flush)
     * @return the decoded frame, or {@code null} if the call did not
     *         produce one
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
     * Reads the SPS-derived width/height/stride out of
     * {@link #bufInfo} and copies the three planes from the
     * pointers in {@link #planesArray} into a fresh I420 byte array.
     *
     * @return the decoded frame
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
     * Copies one decoded plane out of openh264's internal buffer
     * accounting for the (possibly padded) line stride.
     *
     * @param planeIndex  0=Y, 1=U, 2=V
     * @param planeWidth  pixel width of the plane
     * @param planeHeight pixel height of the plane
     * @param stride      byte stride of the plane
     * @param dst         destination byte array
     * @param dstOffset   destination start offset
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
     * Calls {@code ISVCDecoder.Initialize(SDecodingParam *)} with
     * defaults appropriate for WebRTC realtime decode — output
     * format I420, no error-concealment IDR loop, postprocessing off.
     *
     * @param initHandle the cached Initialize vtable handle
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
     * Builds a downcall handle from a vtable function-pointer slot.
     *
     * @param fnPtr the function pointer read from the vtable struct
     * @param desc  the C-level signature descriptor
     * @return a method handle suitable for {@code invokeExact}
     */
    private static MethodHandle bindVtableFn(MemorySegment fnPtr, FunctionDescriptor desc) {
        if (fnPtr.equals(MemorySegment.NULL)) {
            throw new WhatsAppCallException.H264("vtable function pointer is NULL");
        }
        return Linker.nativeLinker().downcallHandle(fnPtr, desc);
    }

    /**
     * Throws if the decoder has been closed.
     */
    private void requireOpen() {
        if (self == null || self.equals(MemorySegment.NULL)) {
            throw new IllegalStateException("H264Decoder is closed");
        }
    }

    /**
     * Calls {@code WelsDestroyDecoder} if the decoder pointer is
     * still live.
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
     * Tears down the decoder: calls {@code Uninitialize} via the
     * vtable, then {@code WelsDestroyDecoder}, then closes the
     * arena. Idempotent.
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
