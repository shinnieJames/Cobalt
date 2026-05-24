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
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/**
 * AVIO bridge that lets libavformat mux output through Java upcalls.
 *
 * @apiNote
 * Two sealed variants cover every muxer shape Cobalt drives.
 * {@link HeapBuffer} backs the sink with a growing heap {@code byte[]} and
 * never registers a read or seek callback; it is the right fit for
 * append-only muxers whose output is small enough to live in heap (OGG
 * voice notes, single-packet image codecs not driven through libavformat).
 * {@link FileSystem} backs the sink with a {@link FileChannel} on a
 * temporary file and registers read + seek callbacks too, supporting
 * muxers that backseek during {@code av_write_trailer} (M4A {@code ipod}
 * trailer-atom rewrite, MP4 {@code +faststart} moov hoist). Construct
 * either through the static {@link #ofHeap(Arena)} /
 * {@link #ofFileSystem(Arena)} factories.
 *
 * @implNote
 * Read, write, and seek upcalls wrap the native AVIO buffer slice as a
 * direct {@link java.nio.ByteBuffer} via {@link MemorySegment#asByteBuffer()}
 * (for the file-backed variant) so disk and FFmpeg's native buffer
 * exchange bytes with one syscall and zero Java-heap intermediation. The
 * heap variant copies once from the native AVIO buffer into the backing
 * array, which is acceptable because its callers cap their output at
 * sub-MB. The parent class binds upcall stubs to {@code this}, so the
 * virtual dispatch of the abstract {@link #writePacket} / {@link #readPacket}
 * / {@link #seek} methods routes to the concrete variant at runtime. The
 * AVIO context is created with the read and seek slots set to
 * {@code NULL} when the variant is not seekable, so FFmpeg never invokes
 * the no-op defaults inherited from the parent.
 */
public sealed abstract class AvioWriteBuffer implements AutoCloseable
        permits AvioWriteBuffer.HeapBuffer, AvioWriteBuffer.FileSystem {
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
     * Layout descriptor for the {@code write_packet} upcall:
     * {@code int (*)(void *opaque, const uint8_t *buf, int buf_size)}.
     */
    private static final FunctionDescriptor WRITE_PACKET_DESCRIPTOR = FunctionDescriptor.of(
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
     * {@code SEEK_SET}: set the position to {@code offset}.
     */
    private static final int SEEK_SET = 0;

    /**
     * {@code SEEK_CUR}: add {@code offset} to the current position.
     */
    private static final int SEEK_CUR = 1;

    /**
     * {@code SEEK_END}: set the position to {@code size + offset}.
     */
    private static final int SEEK_END = 2;

    /**
     * {@code AVSEEK_SIZE} flag from {@code libavformat/avio.h}: report
     * the underlying size rather than performing an actual seek.
     */
    private static final int AVSEEK_SIZE = 0x10000;

    /**
     * Default size of the AVIO buffer in bytes.
     */
    public static final int DEFAULT_BUFFER_SIZE = 4096;

    /**
     * Native buffer handed to {@code avio_alloc_context}.
     */
    protected final MemorySegment avioBuffer;

    /**
     * AVIOContext pointer; set on the owning {@code AVFormatContext} via
     * {@code AVFormatContext.pb}.
     */
    protected final MemorySegment ioContext;

    /**
     * Last {@link IOException} thrown from the underlying sink, if any.
     */
    protected volatile IOException lastError;

    /**
     * Guards against double-close.
     */
    private boolean closed;

    /**
     * Initialises the AVIO context, binding the {@code write_packet}
     * upcall stub to {@link #writePacket} and (when {@code seekable} is
     * {@code true}) the {@code read_packet} and {@code seek} stubs to
     * {@link #readPacket} and {@link #seek}.
     *
     * @apiNote
     * Subclass-only constructor invoked by {@link HeapBuffer} and
     * {@link FileSystem}. The bound upcall stubs dispatch through the
     * subclass's overrides so the abstract write path resolves to the
     * concrete implementation at native-call time. The AVIO context is
     * created with {@code read_packet} and {@code seek} set to
     * {@code NULL} when {@code seekable} is {@code false}, so libavformat
     * never invokes those upcalls for muxers that do not seek.
     *
     * @param arena      the arena confining the upcall stubs
     * @param bufferSize the AVIO buffer size in bytes; must be positive
     * @param seekable   {@code true} when {@link #readPacket} and
     *                   {@link #seek} should be registered alongside
     *                   {@link #writePacket}
     */
    private AvioWriteBuffer(Arena arena, int bufferSize, boolean seekable) {
        MemorySegment allocatedBuffer = null;
        MemorySegment allocatedContext = null;
        try {
            allocatedBuffer = FFmpegError.requireNonNull("av_malloc(AVIO write buffer)",
                    Ffmpeg.av_malloc(bufferSize)).reinterpret(bufferSize);
            MethodHandle writeHandle;
            MethodHandle readHandle = null;
            MethodHandle seekHandle = null;
            try {
                writeHandle = MethodHandles.lookup().bind(this, "writePacket",
                        MethodType.methodType(int.class, MemorySegment.class,
                                MemorySegment.class, int.class));
                if (seekable) {
                    readHandle = MethodHandles.lookup().bind(this, "readPacket",
                            MethodType.methodType(int.class, MemorySegment.class,
                                    MemorySegment.class, int.class));
                    seekHandle = MethodHandles.lookup().bind(this, "seek",
                            MethodType.methodType(long.class, MemorySegment.class,
                                    long.class, int.class));
                }
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new AssertionError("AvioWriteBuffer.{writePacket,readPacket,seek} must exist", e);
            }
            var writeStub = Linker.nativeLinker().upcallStub(writeHandle, WRITE_PACKET_DESCRIPTOR, arena);
            var readStub = seekable
                    ? Linker.nativeLinker().upcallStub(readHandle, READ_PACKET_DESCRIPTOR, arena)
                    : MemorySegment.NULL;
            var seekStub = seekable
                    ? Linker.nativeLinker().upcallStub(seekHandle, SEEK_DESCRIPTOR, arena)
                    : MemorySegment.NULL;
            allocatedContext = FFmpegError.requireNonNull("avio_alloc_context(write)",
                    Ffmpeg.avio_alloc_context(allocatedBuffer, bufferSize, 1,
                            MemorySegment.NULL, readStub, writeStub, seekStub));
        } catch (Throwable t) {
            if (allocatedBuffer != null && allocatedContext == null) {
                try (var local = Arena.ofConfined()) {
                    var pp = local.allocate(ValueLayout.ADDRESS);
                    pp.set(ValueLayout.ADDRESS, 0L, allocatedBuffer);
                    Ffmpeg.av_freep(pp);
                }
            }
            throw t;
        }
        this.avioBuffer = allocatedBuffer;
        this.ioContext = allocatedContext;
    }

    /**
     * Returns a fresh heap-backed write bridge with the default AVIO
     * buffer size.
     *
     * @apiNote
     * Used by pipelines whose output fits comfortably in heap and is
     * produced by an append-only muxer (OGG voice notes).
     *
     * @param arena the arena confining the upcall stubs
     * @return the heap-backed bridge
     */
    public static HeapBuffer ofHeap(Arena arena) {
        return ofHeap(arena, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Returns a fresh heap-backed write bridge with an explicit AVIO
     * buffer size.
     *
     * @param arena      the arena confining the upcall stubs
     * @param bufferSize the AVIO buffer size in bytes; must be positive
     * @return the heap-backed bridge
     */
    public static HeapBuffer ofHeap(Arena arena, int bufferSize) {
        return new HeapBuffer(arena, bufferSize);
    }

    /**
     * Returns a fresh file-backed write bridge with the default AVIO
     * buffer size, backed by a freshly-created temp file.
     *
     * @apiNote
     * Used by pipelines whose muxer backseeks (M4A {@code ipod},
     * MP4 {@code +faststart}) or whose output is too large to live in
     * heap.
     *
     * @param arena the arena confining the upcall stubs
     * @return the file-backed bridge
     * @throws IOException if the temp file cannot be created or opened
     */
    public static FileSystem ofFileSystem(Arena arena) throws IOException {
        return ofFileSystem(arena, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Returns a fresh file-backed write bridge with an explicit AVIO
     * buffer size.
     *
     * @param arena      the arena confining the upcall stubs
     * @param bufferSize the AVIO buffer size in bytes; must be positive
     * @return the file-backed bridge
     * @throws IOException if the temp file cannot be created or opened
     */
    public static FileSystem ofFileSystem(Arena arena, int bufferSize) throws IOException {
        var path = Files.createTempFile(FileSystem.TEMP_FILE_PREFIX, FileSystem.TEMP_FILE_SUFFIX);
        FileChannel channel = null;
        try {
            channel = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE);
            return new FileSystem(arena, bufferSize, path, channel);
        } catch (Throwable t) {
            if (channel != null && channel.isOpen()) {
                try {
                    channel.close();
                } catch (IOException ignored) {
                }
            }
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
            throw t;
        }
    }

    /**
     * Returns the AVIOContext pointer for this bridge.
     *
     * @return the AVIOContext pointer
     */
    public final MemorySegment ioContext() {
        return ioContext;
    }

    /**
     * Returns the last {@link IOException} thrown from the underlying
     * sink, if any.
     *
     * @apiNote
     * The upcalls cannot throw across the native boundary, so a sink
     * I/O error is reported to FFmpeg as a negative status and stashed
     * here. Pipelines must inspect this value after FFmpeg returns and
     * convert a non-null result into a
     * {@link com.github.auties00.cobalt.exception.WhatsAppMediaException.Processing}.
     *
     * @return the last sink exception, or {@code null} if none
     */
    public final IOException lastError() {
        return lastError;
    }

    /**
     * libavformat write upcall; implementations route the supplied
     * native bytes to their backing sink.
     *
     * @param opaque  unused
     * @param buf     source buffer
     * @param bufSize bytes to write
     * @return the number of bytes written, or a negative
     *         {@code AVERROR_*} on failure
     */
    abstract int writePacket(MemorySegment opaque, MemorySegment buf, int bufSize);

    /**
     * libavformat read upcall; non-seekable implementations inherit the
     * default which immediately reports end-of-input.
     *
     * @param opaque  unused
     * @param buf     destination buffer
     * @param bufSize maximum bytes to copy
     * @return {@link Ffmpeg#AVERROR_EOF()} for the default implementation
     */
    int readPacket(MemorySegment opaque, MemorySegment buf, int bufSize) {
        return Ffmpeg.AVERROR_EOF();
    }

    /**
     * libavformat seek upcall; non-seekable implementations inherit the
     * default which reports failure.
     *
     * @param opaque unused
     * @param offset target offset
     * @param whence seek mode
     * @return {@code -1} for the default implementation
     */
    long seek(MemorySegment opaque, long offset, int whence) {
        return -1L;
    }

    /**
     * Releases the AVIO context, frees the backing native buffer, and
     * (for the file-backed variant) closes the channel and deletes the
     * temp file when not previously detached.
     */
    @Override
    public final void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (ioContext != MemorySegment.NULL) {
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
        releaseBackingStorage();
    }

    /**
     * Releases any type-specific backing storage held by the subclass.
     *
     * @apiNote
     * Hook invoked by {@link #close()} after the AVIO context is freed.
     * {@link HeapBuffer} has nothing to release; {@link FileSystem}
     * closes its channel and deletes its temp file when not previously
     * detached via {@link FileSystem#release()}.
     */
    protected void releaseBackingStorage() {
    }

    /**
     * A heap-backed write bridge that accumulates muxer output in a
     * growing {@code byte[]}.
     *
     * @apiNote
     * Suitable for append-only muxers (OGG) whose total output is small
     * enough to fit comfortably in heap. The accumulated bytes are
     * exposed through {@link #toByteArray()} once the caller has invoked
     * {@code av_write_trailer}. Construct through
     * {@link AvioWriteBuffer#ofHeap(Arena)}.
     */
    public static final class HeapBuffer extends AvioWriteBuffer {
        /**
         * Initial backing buffer capacity in bytes.
         */
        private static final int INITIAL_BACKING_CAPACITY = 16 * 1024;

        /**
         * Backing buffer that holds every byte the muxer writes; grows
         * geometrically.
         */
        private byte[] backing = new byte[INITIAL_BACKING_CAPACITY];

        /**
         * Logical size of the accumulated output in bytes; never
         * decreases.
         */
        private int size;

        /**
         * Constructs the heap-backed bridge.
         *
         * @param arena      the arena confining the upcall stubs
         * @param bufferSize the AVIO buffer size in bytes; must be
         *                   positive
         */
        private HeapBuffer(Arena arena, int bufferSize) {
            super(arena, bufferSize, false);
        }

        /**
         * Returns the bytes accumulated by the muxer so far as a fresh
         * array sized exactly to the logical end of the output.
         *
         * @apiNote
         * Callers usually invoke this after {@code av_write_trailer}.
         * Subsequent muxer writes append to the same internal buffer; a
         * second call returns a snapshot that includes the trailing
         * bytes.
         *
         * @return a fresh array of every accumulated byte
         */
        public byte[] toByteArray() {
            return Arrays.copyOf(backing, size);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * Copies the native AVIO bytes into the heap backing array,
         * growing the array geometrically on demand.
         */
        @Override
        @SuppressWarnings("unused")
        int writePacket(MemorySegment opaque, MemorySegment buf, int bufSize) {
            if (bufSize <= 0) {
                return 0;
            }
            ensureCapacity(size + bufSize);
            var data = buf.reinterpret(bufSize);
            MemorySegment.copy(data, ValueLayout.JAVA_BYTE, 0L, backing, size, bufSize);
            size += bufSize;
            return bufSize;
        }

        /**
         * Grows {@link #backing} so it can hold at least {@code required}
         * bytes.
         *
         * @param required the new minimum capacity
         */
        private void ensureCapacity(int required) {
            if (required <= backing.length) {
                return;
            }
            var next = backing.length;
            while (next < required) {
                next *= 2;
            }
            var grown = new byte[next];
            System.arraycopy(backing, 0, grown, 0, size);
            backing = grown;
        }
    }

    /**
     * A file-backed write bridge that pipes muxer output through a
     * {@link FileChannel} on a temp file.
     *
     * @apiNote
     * Suitable for muxers that backseek during {@code av_write_trailer}
     * (M4A {@code ipod}, MP4 {@code +faststart}). The bridge owns the
     * underlying temp file; on {@link #close()} the file is deleted
     * unless ownership was transferred via {@link #release()}. After a
     * successful mux the pipeline calls {@link #release()} to detach the
     * path and wraps it in a {@code MediaPayload.OfPath} the upload
     * layer can stream from. Construct through
     * {@link AvioWriteBuffer#ofFileSystem(Arena)}.
     */
    public static final class FileSystem extends AvioWriteBuffer {
        /**
         * Prefix for the temp file name.
         */
        private static final String TEMP_FILE_PREFIX = "cobalt-mux-";

        /**
         * Suffix for the temp file name.
         */
        private static final String TEMP_FILE_SUFFIX = ".tmp";

        /**
         * The temp file backing this bridge. Nulled by
         * {@link #release()} once ownership is transferred to the
         * caller.
         */
        private Path path;

        /**
         * The open file channel used for read, write, and seek.
         */
        private final FileChannel channel;

        /**
         * Constructs the file-backed bridge with a pre-validated temp
         * file and open channel.
         *
         * @param arena      the arena confining the upcall stubs
         * @param bufferSize the AVIO buffer size in bytes; must be
         *                   positive
         * @param path       the temp file
         * @param channel    the open file channel
         */
        private FileSystem(Arena arena, int bufferSize, Path path, FileChannel channel) {
            super(arena, bufferSize, true);
            this.path = path;
            this.channel = channel;
        }

        /**
         * Detaches the temp file from this bridge's cleanup, returning
         * its path to the caller.
         *
         * @apiNote
         * Invoked by the pipeline after {@code av_write_trailer}
         * completes successfully. Closes the file channel after forcing
         * pending writes to disk, then returns the path; the caller is
         * responsible for deleting the file. A subsequent
         * {@link #close()} will free the FFmpeg-side AVIO context but
         * will not touch the file.
         *
         * @return the temp file path
         * @throws IOException if the channel cannot be flushed or closed
         */
        public Path release() throws IOException {
            if (path == null) {
                throw new IllegalStateException("AvioWriteBuffer.FileSystem already released");
            }
            channel.force(true);
            channel.close();
            var released = path;
            path = null;
            return released;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * Hands the native AVIO buffer slice to the channel as a direct
         * {@link java.nio.ByteBuffer} so muxer bytes land on disk with
         * no Java-heap copy.
         */
        @Override
        @SuppressWarnings("unused")
        int writePacket(MemorySegment opaque, MemorySegment buf, int bufSize) {
            if (bufSize <= 0) {
                return 0;
            }
            try {
                var src = buf.reinterpret(bufSize).asByteBuffer();
                src.limit(bufSize);
                var total = 0;
                while (src.hasRemaining()) {
                    var n = channel.write(src);
                    if (n <= 0) {
                        break;
                    }
                    total += n;
                }
                return total;
            } catch (IOException e) {
                lastError = e;
                return Ffmpeg.AVERROR_EOF();
            }
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * Required by the MP4 muxer's {@code +faststart} path: the
         * muxer seeks to position 0 and reads the entire payload back
         * so it can rewrite the file with the {@code moov} atom at the
         * front.
         */
        @Override
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
         * {@inheritDoc}
         *
         * @implNote
         * Reports the channel size when {@link AvioWriteBuffer#AVSEEK_SIZE}
         * is set; otherwise interprets {@code whence} as a POSIX seek
         * mode and delegates to {@link FileChannel#position}. Off-bounds
         * seeks return {@code -1}.
         */
        @Override
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
                if (newPosition < 0) {
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
         * {@inheritDoc}
         *
         * @implNote
         * Closes the channel and deletes the temp file when
         * {@link #release()} has not transferred ownership.
         */
        @Override
        protected void releaseBackingStorage() {
            if (path == null) {
                return;
            }
            if (channel.isOpen()) {
                try {
                    channel.close();
                } catch (IOException ignored) {
                }
            }
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
            path = null;
        }
    }
}
