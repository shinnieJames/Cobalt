package com.github.auties00.cobalt.media.transcode.avio;

import com.github.auties00.cobalt.media.ffmpeg.AVIOContext;
import com.github.auties00.cobalt.media.ffmpeg.FFmpegError;
import com.github.auties00.cobalt.media.ffmpeg.Ffmpeg;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.channels.SeekableByteChannel;

/**
 * AVIO bridge that lets libavformat read from a {@link SeekableByteChannel}
 * via zero-copy Java upcalls.
 *
 * @apiNote
 * Shared by every transcoder pipeline that demuxes through libavformat.
 * Each instance owns one {@link Ffmpeg#av_malloc} buffer (handed to
 * {@code avio_alloc_context} so libavformat may grow or replace it at
 * runtime) and two upcall stubs ({@code read_packet} and {@code seek})
 * confined to the constructor's {@link Arena}. Implements
 * {@link AutoCloseable} so the owning pipeline can release the AVIO
 * context via try-with-resources; the underlying {@link SeekableByteChannel}
 * is the caller's to close.
 *
 * @implNote
 * The {@code read_packet} upcall wraps the native AVIO buffer slice as a
 * direct {@link java.nio.ByteBuffer} via {@link MemorySegment#asByteBuffer()}
 * and hands it straight to {@link SeekableByteChannel#read}, so disk bytes
 * land in FFmpeg's native buffer with one syscall and zero Java-heap
 * intermediation. The {@code seek} upcall delegates to
 * {@link SeekableByteChannel#position} and {@link SeekableByteChannel#size},
 * supporting {@code SEEK_SET}, {@code SEEK_CUR}, {@code SEEK_END}, and the
 * {@code AVSEEK_SIZE} flag. {@link IOException}s thrown from the channel
 * are stashed in {@link #lastError()} and surfaced as {@code AVERROR_EOF};
 * the owning pipeline checks {@link #lastError()} after the FFmpeg call
 * returns and converts a stashed error into a
 * {@link com.github.auties00.cobalt.exception.WhatsAppMediaException.Processing}.
 */
public final class AvioReadBuffer implements AutoCloseable {
    /**
     * Layout descriptor for the {@code read_packet} upcall:
     * {@code int (*)(void *opaque, uint8_t *buf, int buf_size)}.
     */
    private static final FunctionDescriptor READ_PACKET_DESCRIPTOR = FunctionDescriptor.of(
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT);

    /**
     * Layout descriptor for the {@code seek} upcall:
     * {@code int64_t (*)(void *opaque, int64_t offset, int whence)}.
     */
    private static final FunctionDescriptor SEEK_DESCRIPTOR = FunctionDescriptor.of(
            ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_INT);

    /**
     * {@code SEEK_SET} (POSIX): set the position to {@code offset}.
     */
    private static final int SEEK_SET = 0;

    /**
     * {@code SEEK_CUR} (POSIX): add {@code offset} to the current
     * position.
     */
    private static final int SEEK_CUR = 1;

    /**
     * {@code SEEK_END} (POSIX): set the position to
     * {@code size + offset}.
     */
    private static final int SEEK_END = 2;

    /**
     * {@code AVSEEK_SIZE} flag from {@code libavformat/avio.h}: report
     * the underlying size rather than performing an actual seek.
     */
    private static final int AVSEEK_SIZE = 0x10000;

    /**
     * Default size in bytes of the AVIO read buffer. Matches FFmpeg's own
     * example value.
     */
    public static final int DEFAULT_BUFFER_SIZE = 4096;

    /**
     * Source channel; read/seek delegate to this instance.
     */
    private final SeekableByteChannel channel;

    /**
     * Native buffer handed to {@code avio_alloc_context}.
     */
    private final MemorySegment avioBuffer;

    /**
     * AVIOContext pointer; set on the owning {@code AVFormatContext} via
     * {@code AVFormatContext.pb}.
     */
    private final MemorySegment ioContext;

    /**
     * Last {@link IOException} thrown from the channel, if any; surfaced
     * to the owning pipeline after the FFmpeg call returns.
     */
    private volatile IOException lastError;

    /**
     * Guards against double-close.
     */
    private boolean closed;

    /**
     * Constructs a fresh read bridge backed by the given channel.
     *
     * @param arena   the arena confining the upcall stubs
     * @param channel the source channel; not closed by this bridge
     */
    public AvioReadBuffer(Arena arena, SeekableByteChannel channel) {
        this(arena, channel, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Constructs a fresh read bridge with an explicit buffer size.
     *
     * @param arena      the arena confining the upcall stubs
     * @param channel    the source channel; not closed by this bridge
     * @param bufferSize the AVIO buffer size in bytes; must be positive
     */
    public AvioReadBuffer(Arena arena, SeekableByteChannel channel, int bufferSize) {
        this.channel = channel;
        this.avioBuffer = FFmpegError.requireNonNull("av_malloc(AVIO read buffer)",
                Ffmpeg.av_malloc(bufferSize)).reinterpret(bufferSize);
        MethodHandle readHandle;
        MethodHandle seekHandle;
        try {
            readHandle = MethodHandles.lookup().bind(this, "readPacket",
                    MethodType.methodType(int.class, MemorySegment.class,
                            MemorySegment.class, int.class));
            seekHandle = MethodHandles.lookup().bind(this, "seek",
                    MethodType.methodType(long.class, MemorySegment.class,
                            long.class, int.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError("AvioReadBuffer.{readPacket,seek} must exist", e);
        }
        var readStub = Linker.nativeLinker().upcallStub(readHandle, READ_PACKET_DESCRIPTOR, arena);
        var seekStub = Linker.nativeLinker().upcallStub(seekHandle, SEEK_DESCRIPTOR, arena);
        this.ioContext = FFmpegError.requireNonNull("avio_alloc_context(read)",
                Ffmpeg.avio_alloc_context(avioBuffer, bufferSize, 0,
                        MemorySegment.NULL, readStub, MemorySegment.NULL, seekStub));
    }

    /**
     * Returns the AVIOContext pointer for this bridge.
     *
     * @return the AVIOContext pointer
     */
    public MemorySegment ioContext() {
        return ioContext;
    }

    /**
     * Returns the last {@link IOException} thrown from the underlying
     * channel, if any.
     *
     * @apiNote
     * The {@code read_packet} and {@code seek} upcalls cannot throw across
     * the native boundary, so a channel-level I/O error is reported to
     * FFmpeg as {@code AVERROR_EOF} and stashed here. Pipelines must
     * inspect this value after FFmpeg returns and convert a non-null
     * result into a
     * {@link com.github.auties00.cobalt.exception.WhatsAppMediaException.Processing}.
     *
     * @return the last channel exception, or {@code null} if none
     */
    public IOException lastError() {
        return lastError;
    }

    /**
     * libavformat read upcall.
     *
     * @apiNote
     * Invoked by libavformat through the upcall stub installed in the AVIO
     * context. Hands the native AVIO buffer slice straight to the channel
     * as a direct {@link java.nio.ByteBuffer} so disk bytes land in
     * FFmpeg's buffer with no Java-heap copy.
     *
     * @param opaque  unused; the bridge captures {@code this}
     * @param buf     destination buffer libavformat provided
     * @param bufSize maximum bytes to copy
     * @return the number of bytes copied, or {@link Ffmpeg#AVERROR_EOF()}
     */
    @SuppressWarnings("unused")
    int readPacket(MemorySegment opaque, MemorySegment buf, int bufSize) {
        try {
            var dst = buf.reinterpret(bufSize).asByteBuffer();
            dst.limit(bufSize);
            var read = channel.read(dst);
            return read > 0 ? read : Ffmpeg.AVERROR_EOF();
        } catch (IOException e) {
            lastError = e;
            return Ffmpeg.AVERROR_EOF();
        }
    }

    /**
     * libavformat seek upcall.
     *
     * @apiNote
     * Handles {@code SEEK_SET}, {@code SEEK_CUR}, and {@code SEEK_END} by
     * delegating to {@link SeekableByteChannel#position} and reports the
     * channel size when {@link #AVSEEK_SIZE} is set. Required for demuxers
     * (including MP4 / M4A) that seek back to a trailing metadata atom
     * during {@code avformat_open_input}.
     *
     * @param opaque unused
     * @param offset target offset; interpretation depends on {@code whence}
     * @param whence one of {@link #SEEK_SET}, {@link #SEEK_CUR},
     *               {@link #SEEK_END}, or a flag word containing
     *               {@link #AVSEEK_SIZE}
     * @return the new absolute position, the channel size when
     *         {@link #AVSEEK_SIZE} is set, or {@code -1} on error
     */
    @SuppressWarnings("unused")
    long seek(MemorySegment opaque, long offset, int whence) {
        try {
            if ((whence & AVSEEK_SIZE) != 0) {
                return channel.size();
            }
            var size = channel.size();
            var newPosition = switch (whence & 0xFF) {
                case SEEK_SET -> offset;
                case SEEK_CUR -> channel.position() + offset;
                case SEEK_END -> size + offset;
                default -> -1L;
            };
            if (newPosition < 0 || newPosition > size) {
                return -1L;
            }
            channel.position(newPosition);
            return newPosition;
        } catch (IOException e) {
            lastError = e;
            return -1L;
        }
    }

    /**
     * Releases the AVIO context and frees the backing buffer. The
     * underlying channel is not closed.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (ioContext == MemorySegment.NULL) {
            return;
        }
        try (var local = Arena.ofConfined()) {
            var bufferPtr = AVIOContext.buffer(ioContext);
            if (bufferPtr != MemorySegment.NULL) {
                var pp = local.allocate(ValueLayout.ADDRESS);
                pp.set(ValueLayout.ADDRESS, 0L, bufferPtr);
                Ffmpeg.av_freep(pp);
            }
            var pp = local.allocate(ValueLayout.ADDRESS);
            pp.set(ValueLayout.ADDRESS, 0L, ioContext);
            Ffmpeg.avio_context_free(pp);
        }
    }
}
