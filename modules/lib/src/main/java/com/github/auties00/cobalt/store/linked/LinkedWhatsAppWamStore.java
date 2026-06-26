package com.github.auties00.cobalt.store.linked;

import com.github.auties00.cobalt.wam.model.WamChannel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
