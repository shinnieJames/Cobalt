package com.github.auties00.cobalt.call.internal.video.h264;

import com.github.auties00.cobalt.call.internal.video.h264.bindings.ISVCEncoderVtbl;
import com.github.auties00.cobalt.call.internal.video.h264.bindings.OpenH264;
import com.github.auties00.cobalt.call.internal.video.h264.bindings.SBitrateInfo;
import com.github.auties00.cobalt.call.internal.video.h264.bindings.SFrameBSInfo;
import com.github.auties00.cobalt.call.internal.video.h264.bindings.SLayerBSInfo;
import com.github.auties00.cobalt.call.internal.video.h264.bindings.SSourcePicture;
import com.github.auties00.cobalt.call.internal.video.h264.bindings.Source_Picture_s;
import com.github.auties00.cobalt.call.internal.video.h264.bindings.TagBitrateInfo;
import com.github.auties00.cobalt.call.internal.video.h264.bindings.TagEncParamBase;
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
 * Encodes raw I420 frames into H.264 video through the openh264 codec library.
 *
 * <p>Accepts one I420 (YUV 4:2:0 planar) frame per call and produces the
 * concatenated NAL units the codec emitted for that frame, in bitstream order,
 * ready for RTP NAL-unit-mode packetisation per RFC 6184. Each output
 * {@link Packet} carries the frame type, so the caller can flag IDR frames as
 * keyframes for RTP and signalling purposes.
 *
 * <p>The encoder is configured once at construction for realtime conversational
 * video: realtime camera usage, bitrate-targeted rate control, a single spatial
 * layer, and the geometry and frame rate supplied to the constructor. The
 * target bitrate may be retuned at runtime through {@link #setBitrate(int)} to
 * follow a bandwidth estimate, and a keyframe may be requested per call.
 *
 * <p>The instance owns native resources for its whole lifetime and is not
 * thread-safe: a single encoder must be driven from one thread, or externally
 * serialised. Callers release the codec and its native memory through
 * {@link #close()}. A typical capture loop:
 * {@snippet :
 *   var enc = new H264Encoder(640, 480, 1_000_000, 30);
 *   for (long pts = 0; capturing; pts++) {
 *       byte[] yuv = capture.nextFrame();
 *       var pkt = enc.encode(yuv, pts, false);
 *       if (pkt != null) {
 *           rtp.send(pkt.payload(), pkt.keyFrame());
 *       }
 *   }
 * }
 *
 * @implNote This implementation drives the {@code ISVCEncoder} C++ object
 * directly rather than through a thin C shim. {@code WelsCreateSVCEncoder} and
 * {@code WelsDestroySVCEncoder} are plain exported functions, but every method
 * on the codec instance ({@code Initialize}, {@code EncodeFrame},
 * {@code ForceIntraFrame}, {@code SetOption}, {@code Uninitialize}) lives in the
 * virtual table whose address sits in the first pointer-sized slot of the
 * instance. Each such method is bound to a {@link MethodHandle} once at
 * construction by reading the function pointer out of {@link ISVCEncoderVtbl},
 * and the instance pointer is passed back as the implicit {@code this} (here
 * named {@code self}) on every call.
 */
public final class H264Encoder implements AutoCloseable {
    static {
        NativeLibLoader.load("openh264", Arena.global());
    }

    /**
     * Selects openh264's bitrate-targeted rate-control mode for
     * {@code Initialize}.
     *
     * @implNote This implementation hard-codes the value {@code 1} because the
     * matching {@code RC_BITRATE_MODE} constant lives in an anonymous typedef
     * enum that jextract does not surface as a named binding. The value is the
     * {@code RC_MODES} ordinal of {@code RC_BITRATE_MODE} in openh264's
     * {@code codec_app_def.h}.
     */
    private static final int RC_BITRATE_MODE = 1;

    /**
     * Selects openh264's realtime camera usage type for {@code Initialize}, as
     * opposed to screen-content or non-realtime usage.
     *
     * @implNote This implementation hard-codes the value {@code 0} because the
     * matching {@code CAMERA_VIDEO_REAL_TIME} constant lives in an anonymous
     * typedef enum that jextract does not surface as a named binding. The value
     * is the {@code EUsageType} ordinal of {@code CAMERA_VIDEO_REAL_TIME} in
     * openh264's {@code codec_app_def.h}.
     */
    private static final int CAMERA_VIDEO_REAL_TIME = 0;

    /**
     * Selects the bitrate option for {@code SetOption}, whose payload is an
     * {@code SBitrateInfo} pointer carrying the new target bitrate.
     *
     * @implNote This implementation hard-codes the value {@code 5} because the
     * matching {@code ENCODER_OPTION_BITRATE} constant lives in an anonymous
     * typedef enum that jextract does not surface as a named binding. The value
     * is the {@code ENCODER_OPTION} ordinal of {@code ENCODER_OPTION_BITRATE}
     * in openh264's {@code codec_app_def.h}.
     */
    private static final int ENCODER_OPTION_BITRATE = 5;

    /**
     * Targets all spatial layers when applying a bitrate change, written to the
     * layer field of the {@code SBitrateInfo} payload.
     *
     * @implNote This implementation hard-codes the value {@code 4}, openh264's
     * {@code SPATIAL_LAYER_ALL} sentinel. Although Cobalt configures the encoder
     * single-layer, the bitrate option is addressed to all layers so the change
     * applies regardless of the active layer count.
     */
    private static final int SPATIAL_LAYER_ALL = 4;

    /**
     * Backs the encoder instance pointer, the virtual-table slice, and the two
     * reusable param/output structures for this encoder's whole lifetime.
     *
     * <p>Allocated as a shared arena so the native memory may be touched from
     * any thread, and closed by {@link #close()} once teardown completes.
     */
    private final Arena arena;

    /**
     * Holds the {@code ISVCEncoder} instance pointer returned by
     * {@code WelsCreateSVCEncoder}.
     *
     * <p>The instance's first pointer-sized slot holds the virtual table
     * address. This pointer is passed as the implicit {@code this} argument to
     * every virtual-table method and to {@code WelsDestroySVCEncoder} at
     * teardown. Reset to {@link MemorySegment#NULL} once the codec is
     * destroyed, which marks the encoder closed for {@link #requireOpen()}.
     */
    private MemorySegment self;

    /**
     * Holds the reusable {@code SSourcePicture} structure populated with the
     * input plane pointers and strides on every {@link #encode(byte[], long, boolean)}
     * call.
     */
    private final MemorySegment srcPicture;

    /**
     * Holds the reusable {@code SFrameBSInfo} structure the codec fills with the
     * per-frame layer and NAL bitstream description on every encode.
     */
    private final MemorySegment bsInfo;

    /**
     * Holds the cached downcall handle for the {@code EncodeFrame} virtual table
     * method.
     */
    private final MethodHandle encodeFrameHandle;

    /**
     * Holds the cached downcall handle for the {@code ForceIntraFrame} virtual
     * table method.
     */
    private final MethodHandle forceIntraHandle;

    /**
     * Holds the cached downcall handle for the {@code SetOption} virtual table
     * method.
     */
    private final MethodHandle setOptionHandle;

    /**
     * Holds the cached downcall handle for the {@code Uninitialize} virtual
     * table method.
     */
    private final MethodHandle uninitHandle;

    /**
     * Holds the configured frame width in pixels.
     */
    private final int width;

    /**
     * Holds the configured frame height in pixels.
     */
    private final int height;

    /**
     * Holds the precomputed I420 frame byte size for the configured geometry.
     *
     * <p>Equal to {@snippet : width*height + 2 * (width/2)*(height/2) } and used
     * to validate {@link #encode(byte[], long, boolean)} input and size the
     * scratch buffer.
     */
    private final int yuvSize;

    /**
     * Represents one encoded H.264 frame as the concatenation of all its NAL
     * units in bitstream order.
     *
     * <p>The payload is ready for RTP NAL-unit-mode packetisation. The
     * presentation timestamp echoes the value the frame was encoded with, and
     * the keyframe flag is set when the frame is an IDR.
     *
     * @param payload  the concatenated NAL bytes for the frame; never
     *                 {@code null}
     * @param pts      the presentation timestamp the encoder was driven with
     * @param keyFrame {@code true} if the frame is an IDR, {@code false}
     *                 otherwise
     */
    public record Packet(byte[] payload, long pts, boolean keyFrame) {
        /**
         * Validates that the frame payload is present.
         *
         * @throws NullPointerException if {@code payload} is {@code null}
         */
        public Packet {
            Objects.requireNonNull(payload, "payload cannot be null");
        }
    }

    /**
     * Creates an encoder, validates its geometry, and initialises the
     * underlying openh264 instance.
     *
     * <p>Both dimensions must be even because I420 chroma planes are subsampled
     * by two in each direction. Allocates the instance through
     * {@code WelsCreateSVCEncoder}, binds the required virtual-table methods,
     * and calls {@code Initialize} with the realtime configuration described on
     * the class. On any failure the partially created native state and the
     * arena are released before the triggering exception propagates, so a
     * failed construction leaks nothing.
     *
     * @param width            the frame width in pixels; must be even and at
     *                         least {@code 2}
     * @param height           the frame height in pixels; must be even and at
     *                         least {@code 2}
     * @param targetBitrateBps the initial target bitrate in bits per second;
     *                         must be at least {@code 1}
     * @param fps              the capture frame rate; must be at least {@code 1}
     * @throws IllegalArgumentException   if any dimension is not even and
     *                                    positive, or if the bitrate or frame
     *                                    rate is below {@code 1}
     * @throws WhatsAppCallException.H264 if the codec cannot be created, the
     *                                    virtual table is absent, or
     *                                    {@code Initialize} fails
     * @throws UnsatisfiedLinkError       if the openh264 native library cannot
     *                                    be loaded
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
     * Encodes one I420 frame and returns its NAL units, or {@code null} when the
     * codec skipped the frame.
     *
     * <p>When a keyframe is requested, instructs the codec to emit the next
     * frame as an IDR before encoding. Copies the frame into native scratch
     * memory, drives {@code EncodeFrame}, and concatenates every NAL the codec
     * produced. The codec skips a frame only rarely under realtime
     * configuration, in which case no output is produced.
     *
     * @param yuvI420       the input frame bytes in packed I420 layout; never
     *                      {@code null}, and exactly {@link #frameByteSize()}
     *                      bytes long
     * @param ptsTicks      the presentation timestamp for the frame, echoed back
     *                      on the returned packet
     * @param forceKeyFrame {@code true} to request that this frame be encoded as
     *                      an IDR
     * @return the encoded packet, or {@code null} if the codec produced no
     *         output
     * @throws NullPointerException       if {@code yuvI420} is {@code null}
     * @throws IllegalStateException      if the encoder has been closed
     * @throws IllegalArgumentException   if {@code yuvI420.length} differs from
     *                                    {@link #frameByteSize()}
     * @throws WhatsAppCallException.H264 if the codec reports an encode error
     * @implNote This implementation feeds the codec a millisecond-scale
     * timestamp. openh264 accepts a monotonically increasing 90 kHz or
     * millisecond timestamp; Cobalt drives it in milliseconds by convention.
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
     * Returns the exact number of bytes an {@link #encode(byte[], long, boolean)}
     * input frame must contain.
     *
     * <p>The value is the packed I420 size for the configured geometry,
     * {@snippet : width*height + 2 * (width/2)*(height/2) }
     *
     * @return the required input frame size in bytes
     */
    public int frameByteSize() {
        return yuvSize;
    }

    /**
     * Retunes the encoder's target bitrate at runtime.
     *
     * <p>Issues a {@code SetOption} bitrate change addressed to all spatial
     * layers, allowing a bandwidth-estimation feedback loop to track the
     * current bandwidth estimate without recreating the encoder. The change
     * takes effect on subsequent frames.
     *
     * @param targetBitrateBps the new target bitrate in bits per second; must be
     *                         at least {@code 1}
     * @throws IllegalArgumentException   if {@code targetBitrateBps} is below
     *                                    {@code 1}
     * @throws IllegalStateException      if the encoder has been closed
     * @throws WhatsAppCallException.H264 if the codec reports an error applying
     *                                    the new bitrate
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
     * Calls the codec's {@code Initialize} method with Cobalt's realtime
     * configuration.
     *
     * <p>Populates an {@code SEncParamBase} with realtime camera usage, the
     * configured geometry, the supplied target bitrate and frame rate, and
     * bitrate-targeted rate control, then invokes the cached {@code Initialize}
     * handle. The parameter struct lives in a per-call confined arena that is
     * released as soon as initialisation returns.
     *
     * @param initHandle       the cached downcall handle for the
     *                         {@code Initialize} virtual table method
     * @param targetBitrateBps the target bitrate in bits per second
     * @param fps              the frame rate
     * @throws WhatsAppCallException.H264 if the codec call fails or returns a
     *                                    non-zero status
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
     * Loads the input frame into the reusable source-picture structure.
     *
     * <p>Copies the I420 bytes into a per-call native scratch buffer and points
     * the source picture's three plane fields at the Y, U, and V slices, with
     * strides of {@code width}, {@code width/2}, and {@code width/2}
     * respectively; the fourth plane (unused by I420) is left null with a zero
     * stride. The color format and timestamp are set on the same structure.
     *
     * @param scratch  the per-encode scratch arena owning the copied frame bytes
     * @param yuvI420  the input frame bytes in packed I420 layout
     * @param ptsTicks the presentation timestamp to attach to the frame
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
     * Concatenates the NAL units the codec wrote for the most recent frame into
     * one byte array.
     *
     * <p>Inspects the frame type the codec recorded in {@link #bsInfo} and
     * returns {@code null} when it indicates a skipped or invalid frame. Walks
     * each emitted layer, sums its per-NAL lengths, and copies every layer's
     * bitstream buffer into a single output array in layer order. The resulting
     * packet is flagged as a keyframe when the frame type is IDR.
     *
     * @param ptsTicks the presentation timestamp the frame was encoded with,
     *                 echoed onto the returned packet
     * @return the encoded packet, or {@code null} if the codec emitted no NAL
     *         units for the frame
     */
    private Packet drainPacket(long ptsTicks) {
        var frameType = SFrameBSInfo.eFrameType(bsInfo);
        if (frameType == OpenH264.videoFrameTypeInvalid() || frameType == OpenH264.videoFrameTypeSkip()) {
            return null;
        }
        var layerCount = SFrameBSInfo.iLayerNum(bsInfo);
        if (layerCount <= 0) {
            return null;
        }
        var layerArray = SFrameBSInfo.sLayerInfo(bsInfo);
        var layerStride = SLayerBSInfo.layout().byteSize();
        long total = 0;
        var perLayerSize = new long[layerCount];
        for (var i = 0; i < layerCount; i++) {
            var layer = layerArray.asSlice(i * layerStride, layerStride);
            var nals = SLayerBSInfo.iNalCount(layer);
            var lengths = SLayerBSInfo.pNalLengthInByte(layer)
                    .reinterpret((long) nals * ValueLayout.JAVA_INT.byteSize());
            long sum = 0;
            for (var n = 0; n < nals; n++) {
                sum += lengths.getAtIndex(ValueLayout.JAVA_INT, n);
            }
            perLayerSize[i] = sum;
            total += sum;
        }
        if (total == 0) {
            return null;
        }
        var out = new byte[(int) total];
        var off = 0;
        for (var i = 0; i < layerCount; i++) {
            if (perLayerSize[i] == 0) continue;
            var layer = layerArray.asSlice(i * layerStride, layerStride);
            var bs = SLayerBSInfo.pBsBuf(layer).reinterpret(perLayerSize[i]);
            MemorySegment.copy(bs, ValueLayout.JAVA_BYTE, 0, out, off, (int) perLayerSize[i]);
            off += (int) perLayerSize[i];
        }
        var keyFrame = frameType == OpenH264.videoFrameTypeIDR();
        return new Packet(out, ptsTicks, keyFrame);
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
     * Verifies that the encoder is still open.
     *
     * @throws IllegalStateException if the encoder has been closed
     */
    private void requireOpen() {
        if (self == null || self.equals(MemorySegment.NULL)) {
            throw new IllegalStateException("H264Encoder is closed");
        }
    }

    /**
     * Destroys the native encoder instance if it is still live.
     *
     * <p>Calls {@code WelsDestroySVCEncoder} and nulls {@link #self} so
     * subsequent calls become no-ops. Used both by {@link #close()} and by the
     * constructor's failure path. Any failure inside the native destroy call is
     * swallowed because teardown must not raise.
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
     * Releases the encoder and all native resources it holds.
     *
     * <p>Calls {@code Uninitialize} through the virtual table, destroys the
     * codec instance, and closes the arena. The call is idempotent: once the
     * encoder has been closed, further calls return immediately.
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
