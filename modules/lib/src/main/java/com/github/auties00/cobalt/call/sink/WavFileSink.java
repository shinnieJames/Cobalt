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
 * Writes call audio to a WAV file.
 *
 * <p>This sink records incoming frames as a canonical RIFF/WAVE PCM file. The 44-byte header is
 * emitted up front with its two size fields zeroed; each {@link #write(AudioFrame)} appends the
 * frame's samples little-endian to the body; and {@link #close()} seeks back to patch the size fields
 * with the final byte counts and closes the file. Because the call wire format is already 16 kHz mono
 * signed 16-bit PCM, the samples map one-to-one onto the WAVE payload and no FFmpeg dependency or
 * re-encoding is involved. Writing after close throws.
 *
 * @apiNote Wire this in to capture a call as an uncompressed WAV with no native dependency. The header
 * is finalised only on {@link #close()}, so a recording whose process is killed before close leaves
 * the size fields zeroed and most players treat the file as empty; for a compressed container or for
 * video use {@link CallRecorder} instead.
 */
public final class WavFileSink implements AudioSink, AutoCloseable {
    /**
     * Names the sample rate written into the WAV header, matching the call wire rate of 16 kHz.
     */
    private static final int SAMPLE_RATE = 16_000;

    /**
     * Names the channel count written into the WAV header, matching the call wire layout of mono.
     */
    private static final int CHANNELS = 1;

    /**
     * Names the bit depth written into the WAV header, matching the signed 16-bit call wire samples.
     */
    private static final int BITS_PER_SAMPLE = 16;

    /**
     * Holds the underlying file as a {@link RandomAccessFile} so the header size fields can be
     * patched by seeking back on close.
     */
    private final RandomAccessFile raf;

    /**
     * Holds the writable channel view of {@link #raf}.
     */
    private final WritableByteChannel channel;

    /**
     * Holds a buffered stream over {@link #channel} used for the sample appends.
     *
     * @implNote This implementation wraps the channel in a {@link BufferedOutputStream} so the many
     * small per-frame writes coalesce into larger block writes.
     */
    private final OutputStream out;

    /**
     * Counts the audio sample bytes written so far, used to compute the final WAV size fields on
     * close.
     */
    private long sampleBytes;

    /**
     * Records whether {@link #close()} has run.
     */
    private boolean closed;

    /**
     * Opens the given path for writing and emits the WAV header with placeholder size fields.
     *
     * <p>Truncates any existing file at the path, opens it for read-write so the header can later be
     * patched, and writes the 44-byte header with its size fields zeroed.
     *
     * @param path the output file path; never {@code null}
     * @throws NullPointerException if {@code path} is {@code null}
     * @throws UncheckedIOException if the file cannot be opened or the header cannot be written
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
     * {@inheritDoc}
     *
     * <p>Appends the frame's samples little-endian to the WAV body and advances the running byte
     * count used to finalise the header on close.
     *
     * @throws IllegalStateException if the sink has already been closed
     * @throws UncheckedIOException  if the underlying write fails
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
     * Flushes pending samples, patches the header size fields, and closes the file.
     *
     * <p>Flushes the buffered body, rewrites the two placeholder size fields with the final byte
     * counts, and closes the file even if the patch fails. Calling this more than once does nothing
     * after the first time, so it is idempotent.
     *
     * @throws UncheckedIOException if flushing or patching the header fails
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
     * Writes the 44-byte RIFF/WAVE PCM header with its size fields zeroed.
     *
     * <p>Emits the {@code RIFF} chunk, the {@code WAVE} form type, the 16-byte {@code fmt } chunk
     * carrying PCM format code 1 with the configured channel count, sample rate, byte rate, block
     * align, and bit depth, and the {@code data} chunk header. The RIFF size and {@code data} size
     * fields are left zero and are filled in by {@link #patchHeaderSizes()} on close.
     *
     * @throws IOException if writing the header fails
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
     * Patches the header's two size fields with the final byte counts.
     *
     * <p>Seeks to offset 4 and writes the RIFF chunk size, the total sample byte count plus the
     * 36 bytes of header that follow that field, then seeks to offset 40 and writes the {@code data}
     * chunk size, the total sample byte count. Both values are stored little-endian.
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
