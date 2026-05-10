package com.github.auties00.cobalt.call.video.h264;

import com.github.auties00.cobalt.call.video.h264.bindings.ISVCEncoderVtbl;
import com.github.auties00.cobalt.call.video.h264.bindings.OpenH264;
import com.github.auties00.cobalt.call.video.h264.bindings.SBitrateInfo;
import com.github.auties00.cobalt.call.video.h264.bindings.SFrameBSInfo;
import com.github.auties00.cobalt.call.video.h264.bindings.SLayerBSInfo;
import com.github.auties00.cobalt.call.video.h264.bindings.SSourcePicture;
import com.github.auties00.cobalt.call.video.h264.bindings.Source_Picture_s;
import com.github.auties00.cobalt.call.video.h264.bindings.TagBitrateInfo;
import com.github.auties00.cobalt.call.video.h264.bindings.TagEncParamBase;
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
 * H.264 encoder backed by openh264's {@code ISVCEncoder} vtable. The
 * jextract bindings expose {@code WelsCreateSVCEncoder} /
 * {@code WelsDestroySVCEncoder} as plain functions; everything else
 * (Initialize, EncodeFrame, Uninitialize…) is dispatched through
 * function-pointer fields of the {@link ISVCEncoderVtbl} struct.
 *
 * <p>Configured for WebRTC realtime use: {@code CAMERA_VIDEO_REAL_TIME}
 * usage type, {@code RC_BITRATE_MODE} rate control, single layer, no
 * SPS/PPS strategy reuse — matching what mainstream WebRTC stacks
 * configure for 1:1 H.264 calls.
 *
 * <p>I/O contract: input frames are I420 (YUV 4:2:0 planar), Y then U
 * then V. Output is the concatenated NAL units for the frame, ready
 * for RTP NALU-mode packetisation (RFC 6184). Each {@link Packet}
 * record carries its frame type so the caller can mark IDRs as
 * keyframes.
 *
 * <p>Pipeline:
 *
 * <pre>{@code
 *   var enc = new H264Encoder(640, 480, 1_000_000, 30);
 *   for (long pts = 0; capturing; pts++) {
 *       byte[] yuv = capture.nextFrame();
 *       var pkt = enc.encode(yuv, pts, false);
 *       if (pkt != null) rtp.send(pkt.payload(), pkt.keyFrame());
 *   }
 * }</pre>
 */
public final class H264Encoder implements AutoCloseable {
    static {
        NativeLibLoader.load("openh264", Arena.global());
    }

    /**
     * openh264's {@code RC_BITRATE_MODE} rate-control selector. The
     * binding's typedef enum is anonymous so we hard-code the value
     * here rather than chase {@code RC_BITRATE_MODE()}.
     */
    private static final int RC_BITRATE_MODE = 1;

    /**
     * openh264's {@code CAMERA_VIDEO_REAL_TIME} usage type for
     * realtime camera capture (vs. screen content / non-realtime).
     */
    private static final int CAMERA_VIDEO_REAL_TIME = 0;

    /**
     * openh264's {@code ENCODER_OPTION_BITRATE} selector for
     * {@code SetOption} — its {@code SBitrateInfo*} payload carries
     * the new target bitrate. The binding's typedef enum is anonymous
     * so we hard-code the value here (mirrors the constant in
     * {@code codec_app_def.h}'s {@code ENCODER_OPTION} enum).
     */
    private static final int ENCODER_OPTION_BITRATE = 5;

    /**
     * openh264's {@code SPATIAL_LAYER_ALL} sentinel — applied to the
     * {@link TagBitrateInfo#iLayer} field to mean "all layers" when the
     * encoder is configured single-layer (Cobalt's WebRTC config).
     */
    private static final int SPATIAL_LAYER_ALL = 4;

    /**
     * Per-instance arena for the encoder pointer slot, the vtable
     * slice, and the two reusable param/output structs.
     */
    private final Arena arena;

    /**
     * The {@code ISVCEncoder} pointer returned by
     * {@code WelsCreateSVCEncoder} — a pointer to the encoder
     * instance whose first 8 bytes hold the vtable address. Passed
     * as the first argument ({@code self}) to every vtable method,
     * and to {@code WelsDestroySVCEncoder} at teardown. Nulled by
     * {@link #close}.
     */
    private MemorySegment self;

    /**
     * Reusable {@link SSourcePicture} populated each
     * {@link #encode} call.
     */
    private final MemorySegment srcPicture;

    /**
     * Reusable {@link SFrameBSInfo} the encoder writes per encode.
     */
    private final MemorySegment bsInfo;

    /**
     * Cached downcall handle for {@code ISVCEncoderVtbl.EncodeFrame}.
     */
    private final MethodHandle encodeFrameHandle;

    /**
     * Cached downcall handle for {@code ISVCEncoderVtbl.ForceIntraFrame}.
     */
    private final MethodHandle forceIntraHandle;

    /**
     * Cached downcall handle for {@code ISVCEncoderVtbl.SetOption}.
     */
    private final MethodHandle setOptionHandle;

    /**
     * Cached downcall handle for {@code ISVCEncoderVtbl.Uninitialize}.
     */
    private final MethodHandle uninitHandle;

    /**
     * Frame width in pixels.
     */
    private final int width;

    /**
     * Frame height in pixels.
     */
    private final int height;

    /**
     * Cached I420 byte size: {@code w*h + 2*(w/2)*(h/2)}.
     */
    private final int yuvSize;

    /**
     * One encoded H.264 frame — all NAL units for the picture
     * concatenated in bitstream order, ready for RTP NALU
     * packetisation.
     *
     * @param payload  the NAL bytes
     * @param pts      the presentation timestamp the encoder was
     *                 driven with
     * @param keyFrame {@code true} if this frame is an IDR
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
     * Constructs and configures a new H.264 encoder.
     *
     * @param width            frame width in pixels (must be even)
     * @param height           frame height in pixels (must be even)
     * @param targetBitrateBps target bitrate in bits per second
     * @param fps              capture frame rate
     * @throws IllegalArgumentException if any argument is invalid
     * @throws WhatsAppCallException.H264            if openh264 rejects the
     *                                  config or initialisation fails
     * @throws UnsatisfiedLinkError     if libopenh264 cannot be loaded
     */
    public H264Encoder(int width, int height, int targetBitrateBps, int fps) {
        if (width < 1 || width % 2 != 0) throw new IllegalArgumentException("width must be even and ≥ 2");
        if (height < 1 || height % 2 != 0) throw new IllegalArgumentException("height must be even and ≥ 2");
        if (targetBitrateBps < 1) throw new IllegalArgumentException("targetBitrateBps must be ≥ 1");
        if (fps < 1) throw new IllegalArgumentException("fps must be ≥ 1");
        this.width = width;
        this.height = height;
        this.yuvSize = width * height + 2 * (width / 2) * (height / 2);
        this.arena = Arena.ofShared();
        try {
            var slot = arena.allocate(ValueLayout.ADDRESS);
            int rc;
            try {
                rc = OpenH264.WelsCreateSVCEncoder(slot);
            } catch (Throwable t) {
                throw new WhatsAppCallException.H264("WelsCreateSVCEncoder failed", t);
            }
            if (rc != 0) {
                throw new WhatsAppCallException.H264("WelsCreateSVCEncoder returned " + rc);
            }
            this.self = slot.get(ValueLayout.ADDRESS, 0);
            if (self.equals(MemorySegment.NULL)) {
                throw new WhatsAppCallException.H264("WelsCreateSVCEncoder produced NULL encoder");
            }
            var vtableAddr = self.reinterpret(ValueLayout.ADDRESS.byteSize())
                    .get(ValueLayout.ADDRESS, 0);
            if (vtableAddr.equals(MemorySegment.NULL)) {
                throw new WhatsAppCallException.H264("encoder vtable pointer is NULL");
            }
            var vtable = vtableAddr.reinterpret(ISVCEncoderVtbl.layout().byteSize());
            var initHandle = bindVtableFn(ISVCEncoderVtbl.Initialize(vtable),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            this.encodeFrameHandle = bindVtableFn(ISVCEncoderVtbl.EncodeFrame(vtable),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            this.forceIntraHandle = bindVtableFn(ISVCEncoderVtbl.ForceIntraFrame(vtable),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_BOOLEAN));
            this.setOptionHandle = bindVtableFn(ISVCEncoderVtbl.SetOption(vtable),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            this.uninitHandle = bindVtableFn(ISVCEncoderVtbl.Uninitialize(vtable),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            this.srcPicture = SSourcePicture.allocate(arena);
            this.bsInfo = SFrameBSInfo.allocate(arena);
            initialize(initHandle, targetBitrateBps, fps);
        } catch (RuntimeException e) {
            destroyEncoder();
            arena.close();
            throw e;
        }
    }

    /**
     * Encodes one I420 frame and returns the concatenated NAL units
     * the codec produced for it, or {@code null} if the encoder
     * skipped the frame (rare under realtime config).
     *
     * @param yuvI420       the input frame bytes
     * @param ptsTicks      presentation timestamp in encoder ticks
     *                      (millisecond-scale; openh264 takes
     *                      monotonically-increasing 90 kHz or ms
     *                      timestamps — Cobalt uses ms by convention)
     * @param forceKeyFrame request that this frame be an IDR
     * @return the encoded packet, or {@code null} if no output was
     *         produced
     * @throws IllegalStateException    if the encoder is closed
     * @throws IllegalArgumentException if {@code yuvI420.length} is
     *                                  not the expected I420 size
     * @throws WhatsAppCallException.H264            if openh264 returns non-zero
     */
    public Packet encode(byte[] yuvI420, long ptsTicks, boolean forceKeyFrame) {
        Objects.requireNonNull(yuvI420, "yuvI420 cannot be null");
        requireOpen();
        if (yuvI420.length != yuvSize) {
            throw new IllegalArgumentException("yuvI420 must be " + yuvSize + " bytes, got " + yuvI420.length);
        }
        if (forceKeyFrame) {
            int rc;
            try {
                rc = (int) forceIntraHandle.invokeExact(self, true);
            } catch (Throwable t) {
                throw new WhatsAppCallException.H264("ISVCEncoder.ForceIntraFrame failed", t);
            }
            if (rc != 0) {
                throw new WhatsAppCallException.H264("ISVCEncoder.ForceIntraFrame returned " + rc);
            }
        }
        try (var scratch = Arena.ofConfined()) {
            loadSourcePicture(scratch, yuvI420, ptsTicks);
            int rc;
            try {
                rc = (int) encodeFrameHandle.invokeExact(self, srcPicture, bsInfo);
            } catch (Throwable t) {
                throw new WhatsAppCallException.H264("ISVCEncoder.EncodeFrame failed", t);
            }
            if (rc != 0) {
                throw new WhatsAppCallException.H264("ISVCEncoder.EncodeFrame returned " + rc);
            }
            return drainPacket(ptsTicks);
        }
    }

    /**
     * Returns the number of bytes the encoder expects per
     * {@link #encode} input.
     *
     * @return {@code w*h + 2*(w/2)*(h/2)}
     */
    public int frameByteSize() {
        return yuvSize;
    }

    /**
     * Updates the encoder's target bitrate at runtime by calling
     * {@code ISVCEncoder.SetOption(ENCODER_OPTION_BITRATE, &SBitrateInfo)}.
     * Used by the BWE feedback loop to track the current bandwidth
     * estimate. Applies to all spatial layers
     * ({@link #SPATIAL_LAYER_ALL}).
     *
     * @param targetBitrateBps the new target bitrate in bits per
     *                         second; must be {@code >= 1}
     * @throws IllegalArgumentException if {@code targetBitrateBps < 1}
     * @throws IllegalStateException    if the encoder is closed
     * @throws WhatsAppCallException.H264            if openh264 returns non-zero
     */
    public void setBitrate(int targetBitrateBps) {
        if (targetBitrateBps < 1) throw new IllegalArgumentException("targetBitrateBps must be ≥ 1");
        requireOpen();
        try (var scratch = Arena.ofConfined()) {
            var info = SBitrateInfo.allocate(scratch);
            TagBitrateInfo.iLayer(info, SPATIAL_LAYER_ALL);
            TagBitrateInfo.iBitrate(info, targetBitrateBps);
            int rc;
            try {
                rc = (int) setOptionHandle.invokeExact(self, ENCODER_OPTION_BITRATE, info);
            } catch (Throwable t) {
                throw new WhatsAppCallException.H264("ISVCEncoder.SetOption(BITRATE) failed", t);
            }
            if (rc != 0) {
                throw new WhatsAppCallException.H264("ISVCEncoder.SetOption(BITRATE) returned " + rc);
            }
        }
    }

    /**
     * Calls {@code ISVCEncoder.Initialize(SEncParamBase *)} with
     * Cobalt's WebRTC-realtime defaults.
     *
     * @param initHandle       cached handle for the Initialize vtable slot
     * @param targetBitrateBps target bitrate in bps
     * @param fps              frame rate
     */
    private void initialize(MethodHandle initHandle, int targetBitrateBps, int fps) {
        try (var scratch = Arena.ofConfined()) {
            var param = TagEncParamBase.allocate(scratch);
            TagEncParamBase.iUsageType(param, CAMERA_VIDEO_REAL_TIME);
            TagEncParamBase.iPicWidth(param, width);
            TagEncParamBase.iPicHeight(param, height);
            TagEncParamBase.iTargetBitrate(param, targetBitrateBps);
            TagEncParamBase.iRCMode(param, RC_BITRATE_MODE);
            TagEncParamBase.fMaxFrameRate(param, (float) fps);
            int rc;
            try {
                rc = (int) initHandle.invokeExact(self, param);
            } catch (Throwable t) {
                throw new WhatsAppCallException.H264("ISVCEncoder.Initialize failed", t);
            }
            if (rc != 0) {
                throw new WhatsAppCallException.H264("ISVCEncoder.Initialize returned " + rc);
            }
        }
    }

    /**
     * Populates the reusable {@link SSourcePicture} struct with the
     * input I420 plane pointers and stride values, copying the YUV
     * bytes into a per-call native scratch buffer.
     *
     * @param scratch  per-encode scratch arena
     * @param yuvI420  the input frame bytes
     * @param ptsTicks presentation timestamp
     */
    private void loadSourcePicture(Arena scratch, byte[] yuvI420, long ptsTicks) {
        var buf = scratch.allocate(yuvSize);
        MemorySegment.copy(yuvI420, 0, buf, ValueLayout.JAVA_BYTE, 0, yuvSize);
        Source_Picture_s.iColorFormat(srcPicture, OpenH264.videoFormatI420());
        Source_Picture_s.iPicWidth(srcPicture, width);
        Source_Picture_s.iPicHeight(srcPicture, height);
        Source_Picture_s.uiTimeStamp(srcPicture, ptsTicks);
        Source_Picture_s.iStride(srcPicture, 0, width);
        Source_Picture_s.iStride(srcPicture, 1, width / 2);
        Source_Picture_s.iStride(srcPicture, 2, width / 2);
        Source_Picture_s.iStride(srcPicture, 3, 0);
        var ySize = width * height;
        var uvSize = (width / 2) * (height / 2);
        Source_Picture_s.pData(srcPicture, 0, buf.asSlice(0, ySize));
        Source_Picture_s.pData(srcPicture, 1, buf.asSlice(ySize, uvSize));
        Source_Picture_s.pData(srcPicture, 2, buf.asSlice(ySize + uvSize, uvSize));
        Source_Picture_s.pData(srcPicture, 3, MemorySegment.NULL);
    }

    /**
     * Walks the layer/NAL structure openh264 wrote into
     * {@link #bsInfo} and concatenates every NAL into a single
     * {@code byte[]}.
     *
     * @param ptsTicks the presentation timestamp the frame was
     *                 encoded with — used to populate the returned
     *                 packet
     * @return the encoded packet, or {@code null} if openh264 emitted
     *         no NALs ({@code videoFrameTypeSkip} or
     *         {@code videoFrameTypeInvalid})
     */
    private Packet drainPacket(long ptsTicks) {
        int frameType = SFrameBSInfo.eFrameType(bsInfo);
        if (frameType == OpenH264.videoFrameTypeInvalid() || frameType == OpenH264.videoFrameTypeSkip()) {
            return null;
        }
        int layerCount = SFrameBSInfo.iLayerNum(bsInfo);
        if (layerCount <= 0) {
            return null;
        }
        var layerArray = SFrameBSInfo.sLayerInfo(bsInfo);
        long layerStride = SLayerBSInfo.layout().byteSize();
        long total = 0;
        long[] perLayerSize = new long[layerCount];
        for (int i = 0; i < layerCount; i++) {
            var layer = layerArray.asSlice(i * layerStride, layerStride);
            int nals = SLayerBSInfo.iNalCount(layer);
            var lengths = SLayerBSInfo.pNalLengthInByte(layer)
                    .reinterpret((long) nals * ValueLayout.JAVA_INT.byteSize());
            long sum = 0;
            for (int n = 0; n < nals; n++) {
                sum += lengths.getAtIndex(ValueLayout.JAVA_INT, n);
            }
            perLayerSize[i] = sum;
            total += sum;
        }
        if (total == 0) {
            return null;
        }
        var out = new byte[(int) total];
        int off = 0;
        for (int i = 0; i < layerCount; i++) {
            if (perLayerSize[i] == 0) continue;
            var layer = layerArray.asSlice(i * layerStride, layerStride);
            var bs = SLayerBSInfo.pBsBuf(layer).reinterpret(perLayerSize[i]);
            MemorySegment.copy(bs, ValueLayout.JAVA_BYTE, 0, out, off, (int) perLayerSize[i]);
            off += (int) perLayerSize[i];
        }
        boolean keyFrame = frameType == OpenH264.videoFrameTypeIDR();
        return new Packet(out, ptsTicks, keyFrame);
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
     * Throws if the encoder has been closed.
     */
    private void requireOpen() {
        if (self == null || self.equals(MemorySegment.NULL)) {
            throw new IllegalStateException("H264Encoder is closed");
        }
    }

    /**
     * Calls {@code WelsDestroySVCEncoder} if the encoder pointer is
     * still live. Used both by {@link #close} and by the constructor's
     * failure path.
     */
    private void destroyEncoder() {
        if (self == null || self.equals(MemorySegment.NULL)) {
            return;
        }
        try {
            OpenH264.WelsDestroySVCEncoder(self);
        } catch (Throwable _) {
        }
        self = MemorySegment.NULL;
    }

    /**
     * Tears down the encoder: calls {@code Uninitialize} via the
     * vtable, then {@code WelsDestroySVCEncoder}, then closes the
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
        destroyEncoder();
        arena.close();
    }
}
