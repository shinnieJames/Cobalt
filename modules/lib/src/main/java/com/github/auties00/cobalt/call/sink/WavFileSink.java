package com.github.auties00.cobalt.call.sink;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.frame.audio.AudioSink;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * {@link AudioSink} that writes incoming {@link AudioFrame}s to
 * a WAV file. WAV is implemented in pure Java (no FFmpeg
 * dependency) — the call wire format is already 16 kHz mono
 * signed-16-bit PCM, which maps 1:1 to a WAVE PCM payload.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>The 44-byte RIFF/WAVE header is written up-front with
 *       placeholder size fields.</li>
 *   <li>{@link #write} appends the frame's samples little-endian
 *       to the output stream.</li>
 *   <li>{@link #close} seeks back and patches the placeholder
 *       size fields with the final byte counts, then closes the
 *       file.</li>
 * </ul>
 *
 * <p>For non-WAV outputs use
 * {@link CallRecorder}.
 */
public final class WavFileSink implements AudioSink, AutoCloseable {
    /**
     * Sample rate the call wire uses (16 kHz).
     */
    private static final int SAMPLE_RATE = 16_000;

    /**
     * Channel count the call wire uses (mono).
     */
    private static final int CHANNELS = 1;

    /**
     * Bit depth (S16).
     */
    private static final int BITS_PER_SAMPLE = 16;

    /**
     * Underlying file — kept as {@link RandomAccessFile} so we can
     * seek back to patch the WAV header sizes on close.
     */
    private final RandomAccessFile raf;

    /**
     * Buffered + writable view of {@link #raf}'s channel.
     */
    private final WritableByteChannel channel;

    /**
     * Buffered output stream wrapping the channel, used for the
     * sample writes (bulks small writes into 8 KB blocks).
     */
    private final OutputStream out;

    /**
     * Cumulative count of audio sample bytes written — used to
     * compute the final WAV header sizes on close.
     */
    private long sampleBytes;

    /**
     * Whether {@link #close} has run.
     */
    private boolean closed;

    /**
     * Opens {@code path} for writing and emits the WAV header
     * with placeholder size fields.
     *
     * @param path the output file path
     * @throws NullPointerException if {@code path} is null
     * @throws UncheckedIOException if the file can't be opened
     */
    public WavFileSink(Path path) {
        Objects.requireNonNull(path, "path cannot be null");
        try {
            Files.write(path, new byte[0],
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            this.raf = new RandomAccessFile(path.toFile(), "rw");
            this.channel = raf.getChannel();
            this.out = new BufferedOutputStream(Channels.newOutputStream(channel));
            writeHeaderPlaceholder();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to open WAV sink at " + path, e);
        }
    }

    /**
     * Appends one frame of PCM samples to the WAV body.
     *
     * @param frame the frame to write
     * @throws NullPointerException if {@code frame} is null
     * @throws UncheckedIOException if the underlying write fails
     */
    @Override
    public void write(AudioFrame frame) {
        Objects.requireNonNull(frame, "frame cannot be null");
        if (closed) {
            throw new IllegalStateException("WavFileSink already closed");
        }
        var pcm = frame.pcm();
        var buf = ByteBuffer.allocate(pcm.length * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (var s : pcm) {
            buf.putShort(s);
        }
        try {
            out.write(buf.array());
            sampleBytes += buf.array().length;
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write WAV samples", e);
        }
    }

    /**
     * Flushes pending samples, patches the WAV header's
     * placeholder sizes with the final byte counts, and closes
     * the file. Idempotent.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            out.flush();
            patchHeaderSizes();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to finalise WAV file", e);
        } finally {
            try {
                raf.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Writes the canonical 44-byte RIFF/WAVE PCM header with
     * size fields zero'd — patched in {@link #patchHeaderSizes}
     * on close.
     *
     * @throws IOException if writing fails
     */
    private void writeHeaderPlaceholder() throws IOException {
        var header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put((byte) 'R').put((byte) 'I').put((byte) 'F').put((byte) 'F');
        header.putInt(0);
        header.put((byte) 'W').put((byte) 'A').put((byte) 'V').put((byte) 'E');
        header.put((byte) 'f').put((byte) 'm').put((byte) 't').put((byte) ' ');
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) CHANNELS);
        header.putInt(SAMPLE_RATE);
        header.putInt(SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8);
        header.putShort((short) (CHANNELS * BITS_PER_SAMPLE / 8));
        header.putShort((short) BITS_PER_SAMPLE);
        header.put((byte) 'd').put((byte) 'a').put((byte) 't').put((byte) 'a');
        header.putInt(0);
        out.write(header.array());
    }

    /**
     * Seeks back to the placeholder offsets and writes the final
     * byte counts.
     *
     * @throws IOException if seeking or writing fails
     */
    private void patchHeaderSizes() throws IOException {
        var total = sampleBytes;
        raf.seek(4L);
        raf.writeInt(Integer.reverseBytes((int) (36L + total)));
        raf.seek(40L);
        raf.writeInt(Integer.reverseBytes((int) total));
    }
}
