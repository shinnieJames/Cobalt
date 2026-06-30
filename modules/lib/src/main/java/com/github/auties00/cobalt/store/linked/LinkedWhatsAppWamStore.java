package com.github.auties00.cobalt.store.linked;

import com.github.auties00.cobalt.wam.model.WamChannel;
import com.github.auties00.cobalt.wam.threadlogging.ThreadLoggingCounters;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The WAM (WhatsApp Metrics) telemetry state of a WhatsApp client session.
 *
 * <p>This is the sub-store that owns the bookkeeping the telemetry pipeline needs to survive a
 * restart: the per-channel sequence numbers written into every uploaded WAM buffer header, and the
 * staging area for WAM event buffers that have been encoded but not yet uploaded. The sequence numbers
 * are persisted inline with the aggregate; the staged buffers live as files under the session
 * directory and are therefore only available on a disk-backed store.
 *
 * @apiNote
 * Embedders reach this through {@link LinkedWhatsAppStore#wamStore()} and rarely call it directly; the
 * telemetry service owns the read and write paths.
 *
 * @see LinkedWhatsAppStore
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public interface LinkedWhatsAppWamStore {
    /**
     * Returns the WAM sequence number for a channel.
     *
     * @param channel the WAM channel
     * @return the sequence number, or empty if none is recorded
     */
    OptionalInt findSequenceNumber(WamChannel channel);

    /**
     * Sets the WAM sequence number for a channel.
     *
     * @param channel        the WAM channel
     * @param sequenceNumber the sequence number
     * @return this store instance for method chaining
     */
    LinkedWhatsAppWamStore putSequenceNumber(WamChannel channel, int sequenceNumber);

    /**
     * Returns the instant of the most recent daily-stats task run.
     *
     * @return the last daily-stats run instant, or empty if the task has never run
     */
    Optional<Instant> lastDailyStatsTimestamp();

    /**
     * Sets the instant of the most recent daily-stats task run.
     *
     * @param lastDailyStatsTimestamp the last daily-stats run instant, or {@code null} to clear
     * @return this store instance for method chaining
     */
    LinkedWhatsAppWamStore setLastDailyStatsTimestamp(Instant lastDailyStatsTimestamp);

    /**
     * Returns the chat-thread-logging shared secret provisioned from the companion phone.
     *
     * <p>This secret keys the HMAC that derives the per-thread {@code threadId} reported in the
     * {@code ThreadInteractionData} WAM events. It is delivered through history sync; when it is absent
     * the thread-logging uploader emits nothing.
     *
     * @return the shared secret bytes, or empty if none has been provisioned
     */
    Optional<byte[]> chatThreadLoggingSecret();

    /**
     * Sets the chat-thread-logging shared secret provisioned from the companion phone.
     *
     * @param chatThreadLoggingSecret the shared secret bytes, or {@code null} to clear
     * @return this store instance for method chaining
     */
    LinkedWhatsAppWamStore setChatThreadLoggingSecret(byte[] chatThreadLoggingSecret);

    /**
     * Returns the chat-thread-logging day-bucket offset, in seconds, provisioned from the companion phone.
     *
     * <p>This offset shifts the start of each aggregation day bucket away from midnight UTC. It is
     * delivered through history sync; when it is absent no thread activity can be bucketed and both the
     * producer and the uploader no-op.
     *
     * @return the day-bucket offset in seconds, or empty if none has been provisioned
     */
    OptionalInt chatThreadLoggingOffset();

    /**
     * Sets the chat-thread-logging day-bucket offset, in seconds, provisioned from the companion phone.
     *
     * @param chatThreadLoggingOffset the day-bucket offset in seconds, or {@code null} to clear
     * @return this store instance for method chaining
     */
    LinkedWhatsAppWamStore setChatThreadLoggingOffset(Integer chatThreadLoggingOffset);

    /**
     * Returns the watermark instant of the most recent thread-logging upload pass.
     *
     * <p>This is the single global high-water mark: day buckets whose start is at or below it have
     * already been uploaded, so the producer drops activity for them and the uploader skips them.
     *
     * @return the upload watermark instant, or empty if no pass has completed
     */
    Optional<Instant> lastUploadedThreadLoggingTs();

    /**
     * Sets the watermark instant of the most recent thread-logging upload pass.
     *
     * @param lastUploadedThreadLoggingTs the upload watermark instant, or {@code null} to clear
     * @return this store instance for method chaining
     */
    LinkedWhatsAppWamStore setLastUploadedThreadLoggingTs(Instant lastUploadedThreadLoggingTs);

    /**
     * Returns the pending thread-logging counter rows staged for upload.
     *
     * <p>Each row's identity is the {@code (chatJid, startTs)} pair it carries, so the rows are held
     * as a flat collection rather than an explicit map. The producer mutates a row's counters in place
     * and adds new rows through {@link #addThreadLoggingCounters(ThreadLoggingCounters)}; the uploader
     * removes elapsed rows through {@link #removeThreadLoggingCounters(Collection)}.
     *
     * @return an unmodifiable view of the pending counter rows
     */
    Collection<ThreadLoggingCounters> threadLoggingPending();

    /**
     * Adds a pending thread-logging counter row.
     *
     * @param counters the counter row to stage
     * @return this store instance for method chaining
     */
    LinkedWhatsAppWamStore addThreadLoggingCounters(ThreadLoggingCounters counters);

    /**
     * Removes the given pending thread-logging counter rows.
     *
     * @param counters the counter rows to remove
     * @return this store instance for method chaining
     */
    LinkedWhatsAppWamStore removeThreadLoggingCounters(Collection<ThreadLoggingCounters> counters);

    /**
     * Returns the save keys of the WAM event buffers staged on disk.
     *
     * @return an unmodifiable copy of the buffer save keys
     */
    Collection<String> pendingBufferKeys();

    /**
     * Opens an atomic writer for a WAM event buffer.
     *
     * @param saveKey the buffer save key
     * @return an output stream that publishes the buffer on close
     * @throws IOException if the buffer file cannot be created
     */
    OutputStream openPendingBufferWriter(String saveKey) throws IOException;

    /**
     * Opens a reader for a staged WAM event buffer.
     *
     * @param saveKey the buffer save key
     * @return a reader for the buffer, or empty if none is staged
     * @throws IOException if the buffer file cannot be opened
     */
    Optional<InputStream> openPendingBufferReader(String saveKey) throws IOException;

    /**
     * Removes a staged WAM event buffer.
     *
     * @param saveKey the buffer save key
     * @return {@code true} if a buffer was removed
     * @throws IOException if the buffer file cannot be deleted
     */
    boolean removePendingBuffer(String saveKey) throws IOException;

    /**
     * Removes every staged WAM event buffer.
     *
     * @return this store instance for method chaining
     * @throws IOException if a buffer file cannot be deleted
     */
    LinkedWhatsAppWamStore clearPendingBuffers() throws IOException;
}
