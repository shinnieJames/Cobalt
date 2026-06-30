package com.github.auties00.cobalt.store.linked.protobuf;

import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import com.github.auties00.cobalt.store.linked.LinkedWhatsAppAccountStore;
import com.github.auties00.cobalt.store.linked.LinkedWhatsAppWamStore;
import com.github.auties00.cobalt.wam.model.WamChannel;
import com.github.auties00.cobalt.wam.threadlogging.ThreadLoggingCounters;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The protobuf-backed {@link LinkedWhatsAppWamStore} holding this session's telemetry bookkeeping.
 *
 * <p>This is a nested {@code MESSAGE} sub-store of {@link ProtobufWhatsAppStore}; it persists the
 * per-channel WAM sequence numbers inline with the aggregate and stages encoded-but-unsent WAM buffers
 * as files under the session directory. The session directory and the account identity used to resolve
 * those file paths are not part of the persisted state; they are wired by the owning aggregate via
 * {@link #bind(Path, LinkedWhatsAppAccountStore)} after construction.
 *
 * @implNote
 * This implementation resolves buffer file paths through the static directory helpers on
 * {@link ProtobufWhatsAppStore} and publishes each buffer atomically with
 * {@link AtomicMoveOutputStream}. When the bound directory is {@code null} (the transient store) the
 * buffer-staging methods degrade to no-ops because there is no disk surface to stage onto.
 */
@ProtobufMessage
@SuppressWarnings({"unused", "UnusedReturnValue"})
public final class ProtobufLinkedWhatsAppWamStore implements LinkedWhatsAppWamStore {
    /**
     * The filename prefix used by {@link #openPendingBufferWriter} when staging a WAM event buffer.
     */
    private static final String WAM_BUFFER_PREFIX = "wam_buffer_";

    /**
     * The filename suffix paired with {@link #WAM_BUFFER_PREFIX} for staged WAM event buffers.
     */
    private static final String WAM_BUFFER_SUFFIX = ".bin";

    /**
     * The WAM event sequence numbers per channel for dedup.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.MAP, mapKeyType = ProtobufType.INT32, mapValueType = ProtobufType.INT32)
    final ConcurrentMap<Integer, Integer> sequenceNumbersMap;

    /**
     * The instant of the most recent daily-stats task run, or {@code null} if it has never run; serialized
     * on the wire as an epoch-millis {@code UINT64} via {@link InstantMillisMixin}.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.UINT64, mixins = InstantMillisMixin.class)
    Instant lastDailyStatsTimestamp;

    /**
     * The chat-thread-logging shared secret provisioned from the companion phone, or {@code null} if none.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] chatThreadLoggingSecret;

    /**
     * The chat-thread-logging day-bucket offset in seconds provisioned from the companion phone, or {@code null} if none.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.INT32)
    Integer chatThreadLoggingOffset;

    /**
     * The watermark instant of the most recent thread-logging upload pass, or {@code null} if none has
     * completed; serialized on the wire as an epoch-seconds {@code UINT64} via {@link InstantSecondsMixin}.
     */
    @ProtobufProperty(index = 5, type = ProtobufType.UINT64, mixins = InstantSecondsMixin.class)
    Instant lastUploadedThreadLoggingTs;

    /**
     * The pending thread-logging counter rows, each identified by its own {@code (chatJid, startTs)} pair.
     */
    @ProtobufProperty(index = 6, type = ProtobufType.MESSAGE)
    final List<ThreadLoggingCounters> threadLoggingPending;

    /**
     * The on-disk directory under which buffers are staged, or {@code null} for an in-memory store; wired post-construction.
     */
    private Path directory;

    /**
     * The account sub-store consulted for the client type and session id used to resolve buffer paths; wired post-construction.
     */
    private LinkedWhatsAppAccountStore accountStore;

    /**
     * The logger instance for buffer-staging diagnostics; wired post-construction.
     */
    private System.Logger logger;

    /**
     * Full protobuf constructor invoked by the generated builder and the deserializer.
     *
     * @param sequenceNumbersMap          the WAM sequence-number map, or {@code null} for an empty map
     * @param lastDailyStatsTimestamp     the instant of the last daily-stats run, or {@code null}
     * @param chatThreadLoggingSecret     the chat-thread-logging shared secret, or {@code null}
     * @param chatThreadLoggingOffset     the chat-thread-logging day-bucket offset in seconds, or {@code null}
     * @param lastUploadedThreadLoggingTs the watermark instant of the last thread-logging upload, or {@code null}
     * @param threadLoggingPending        the pending thread-logging counter rows, or {@code null} for an empty list
     */
    ProtobufLinkedWhatsAppWamStore(ConcurrentMap<Integer, Integer> sequenceNumbersMap, Instant lastDailyStatsTimestamp,
                                   byte[] chatThreadLoggingSecret, Integer chatThreadLoggingOffset,
                                   Instant lastUploadedThreadLoggingTs,
                                   List<ThreadLoggingCounters> threadLoggingPending) {
        this.sequenceNumbersMap = Objects.requireNonNullElseGet(sequenceNumbersMap, ConcurrentHashMap::new);
        this.lastDailyStatsTimestamp = lastDailyStatsTimestamp;
        this.chatThreadLoggingSecret = chatThreadLoggingSecret;
        this.chatThreadLoggingOffset = chatThreadLoggingOffset;
        this.lastUploadedThreadLoggingTs = lastUploadedThreadLoggingTs;
        this.threadLoggingPending = threadLoggingPending == null
                ? new CopyOnWriteArrayList<>()
                : new CopyOnWriteArrayList<>(threadLoggingPending);
    }

    /**
     * Binds the session directory and account identity used to resolve staged-buffer file paths.
     *
     * @param directory    the session directory, or {@code null} for an in-memory store
     * @param accountStore the account sub-store, never {@code null}
     */
    void bind(Path directory, LinkedWhatsAppAccountStore accountStore) {
        this.directory = directory;
        this.accountStore = Objects.requireNonNull(accountStore, "accountStore cannot be null");
        this.logger = System.getLogger(getClass().getName());
    }

    /**
     * Returns the live WAM sequence-number map backing this store.
     *
     * @return the live WAM sequence-number map
     */
    ConcurrentMap<Integer, Integer> sequenceNumbersMap() {
        return sequenceNumbersMap;
    }

    @Override
    public OptionalInt findSequenceNumber(WamChannel channel) {
        Objects.requireNonNull(channel, "channel cannot be null");
        var stored = sequenceNumbersMap.get(channel.id());
        return stored == null ? OptionalInt.empty() : OptionalInt.of(stored);
    }

    @Override
    public LinkedWhatsAppWamStore putSequenceNumber(WamChannel channel, int sequenceNumber) {
        Objects.requireNonNull(channel, "channel cannot be null");
        sequenceNumbersMap.put(channel.id(), sequenceNumber);
        return this;
    }

    @Override
    public Optional<Instant> lastDailyStatsTimestamp() {
        return Optional.ofNullable(lastDailyStatsTimestamp);
    }

    @Override
    public LinkedWhatsAppWamStore setLastDailyStatsTimestamp(Instant lastDailyStatsTimestamp) {
        this.lastDailyStatsTimestamp = lastDailyStatsTimestamp;
        return this;
    }

    @Override
    public Optional<byte[]> chatThreadLoggingSecret() {
        return Optional.ofNullable(chatThreadLoggingSecret);
    }

    @Override
    public LinkedWhatsAppWamStore setChatThreadLoggingSecret(byte[] chatThreadLoggingSecret) {
        this.chatThreadLoggingSecret = chatThreadLoggingSecret;
        return this;
    }

    @Override
    public OptionalInt chatThreadLoggingOffset() {
        return chatThreadLoggingOffset == null ? OptionalInt.empty() : OptionalInt.of(chatThreadLoggingOffset);
    }

    @Override
    public LinkedWhatsAppWamStore setChatThreadLoggingOffset(Integer chatThreadLoggingOffset) {
        this.chatThreadLoggingOffset = chatThreadLoggingOffset;
        return this;
    }

    @Override
    public Optional<Instant> lastUploadedThreadLoggingTs() {
        return Optional.ofNullable(lastUploadedThreadLoggingTs);
    }

    @Override
    public LinkedWhatsAppWamStore setLastUploadedThreadLoggingTs(Instant lastUploadedThreadLoggingTs) {
        this.lastUploadedThreadLoggingTs = lastUploadedThreadLoggingTs;
        return this;
    }

    @Override
    public Collection<ThreadLoggingCounters> threadLoggingPending() {
        return List.copyOf(threadLoggingPending);
    }

    @Override
    public LinkedWhatsAppWamStore addThreadLoggingCounters(ThreadLoggingCounters counters) {
        threadLoggingPending.add(counters);
        return this;
    }

    @Override
    public LinkedWhatsAppWamStore removeThreadLoggingCounters(Collection<ThreadLoggingCounters> counters) {
        threadLoggingPending.removeAll(counters);
        return this;
    }

    @Override
    public Collection<String> pendingBufferKeys() {
        if (directory == null) {
            return List.of();
        }
        Path sessionDir;
        try {
            sessionDir = ProtobufWhatsAppStore.getSessionDirectory(accountStore.clientType(), directory, accountStore.uuid().toString());
        } catch (IOException error) {
            logger.log(System.Logger.Level.WARNING, "Cannot resolve session directory for WAM buffers", error);
            return List.of();
        }
        if (!Files.isDirectory(sessionDir)) {
            return List.of();
        }
        try (var stream = Files.list(sessionDir)) {
            return stream
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith(WAM_BUFFER_PREFIX) && name.endsWith(WAM_BUFFER_SUFFIX))
                    .map(name -> name.substring(WAM_BUFFER_PREFIX.length(), name.length() - WAM_BUFFER_SUFFIX.length()))
                    .toList();
        } catch (IOException error) {
            logger.log(System.Logger.Level.WARNING, "Cannot list WAM buffer files", error);
            return List.of();
        }
    }

    @Override
    public OutputStream openPendingBufferWriter(String saveKey) throws IOException {
        Objects.requireNonNull(saveKey, "saveKey cannot be null");
        validateSaveKey(saveKey);
        if (directory == null) {
            return OutputStream.nullOutputStream();
        }
        var target = wamBufferPath(saveKey);
        Files.createDirectories(target.getParent());
        var temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        return new AtomicMoveOutputStream(Files.newOutputStream(temp), temp, target);
    }

    @Override
    public Optional<InputStream> openPendingBufferReader(String saveKey) throws IOException {
        Objects.requireNonNull(saveKey, "saveKey cannot be null");
        validateSaveKey(saveKey);
        if (directory == null) {
            return Optional.empty();
        }
        var path = wamBufferPath(saveKey);
        if (Files.notExists(path)) {
            return Optional.empty();
        }
        return Optional.of(Files.newInputStream(path));
    }

    @Override
    public boolean removePendingBuffer(String saveKey) throws IOException {
        Objects.requireNonNull(saveKey, "saveKey cannot be null");
        validateSaveKey(saveKey);
        if (directory == null) {
            return false;
        }
        return Files.deleteIfExists(wamBufferPath(saveKey));
    }

    @Override
    public LinkedWhatsAppWamStore clearPendingBuffers() throws IOException {
        if (directory == null) {
            return this;
        }
        var sessionDir = ProtobufWhatsAppStore.getSessionDirectory(accountStore.clientType(), directory, accountStore.uuid().toString());
        if (!Files.isDirectory(sessionDir)) {
            return this;
        }
        try (var stream = Files.list(sessionDir)) {
            for (var path : (Iterable<Path>) stream::iterator) {
                var name = path.getFileName().toString();
                if (name.startsWith(WAM_BUFFER_PREFIX) && name.endsWith(WAM_BUFFER_SUFFIX)) {
                    Files.deleteIfExists(path);
                }
            }
        }
        return this;
    }

    /**
     * Resolves the on-disk path of the WAM buffer file for {@code saveKey}.
     *
     * @param saveKey the bare save key, already validated
     * @return the path of the file that backs the buffer
     * @throws IOException if the session directory cannot be resolved or created
     */
    private Path wamBufferPath(String saveKey) throws IOException {
        return ProtobufWhatsAppStore.getSessionFile(
                accountStore.clientType(), directory, accountStore.uuid().toString(),
                WAM_BUFFER_PREFIX + saveKey + WAM_BUFFER_SUFFIX);
    }

    /**
     * Rejects any save key that could resolve outside the session directory.
     *
     * @param saveKey the bare save key
     * @throws IllegalArgumentException if {@code saveKey} is empty or contains a forbidden character
     */
    private static void validateSaveKey(String saveKey) {
        if (saveKey.isEmpty()) {
            throw new IllegalArgumentException("saveKey cannot be empty");
        }
        for (var i = 0; i < saveKey.length(); i++) {
            var c = saveKey.charAt(i);
            if (c == '/' || c == '\\' || c == 0 || c == '.' && (i == 0)) {
                throw new IllegalArgumentException("saveKey contains forbidden character: " + saveKey);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof ProtobufLinkedWhatsAppWamStore that
                            && Objects.equals(sequenceNumbersMap, that.sequenceNumbersMap)
                            && Objects.equals(lastDailyStatsTimestamp, that.lastDailyStatsTimestamp)
                            && Arrays.equals(chatThreadLoggingSecret, that.chatThreadLoggingSecret)
                            && Objects.equals(chatThreadLoggingOffset, that.chatThreadLoggingOffset)
                            && Objects.equals(lastUploadedThreadLoggingTs, that.lastUploadedThreadLoggingTs)
                            && Objects.equals(threadLoggingPending, that.threadLoggingPending);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sequenceNumbersMap, lastDailyStatsTimestamp, Arrays.hashCode(chatThreadLoggingSecret),
                chatThreadLoggingOffset, lastUploadedThreadLoggingTs, threadLoggingPending);
    }

    /**
     * Buffers writes to a sibling temp file and atomically renames it over the target on close.
     *
     * @implNote
     * This implementation overrides {@code write(byte[], int, int)} to forward the range write
     * directly to the delegate, avoiding the default per-byte loop, and performs the atomic move on
     * {@link #close()}.
     */
    private static final class AtomicMoveOutputStream extends FilterOutputStream {
        /**
         * The temporary sibling file that receives every write.
         */
        private final Path tempFile;

        /**
         * The destination path that the temp file is renamed over on close.
         */
        private final Path targetFile;

        /**
         * Guards against a double-close.
         */
        private boolean closed;

        /**
         * Wraps the supplied delegate stream with atomic-move close-time semantics.
         *
         * @param delegate   the stream that writes to {@code tempFile}
         * @param tempFile   the sibling temp file that receives every write
         * @param targetFile the destination path renamed over on close
         */
        AtomicMoveOutputStream(OutputStream delegate, Path tempFile, Path targetFile) {
            super(delegate);
            this.tempFile = tempFile;
            this.targetFile = targetFile;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            try {
                super.close();
                Files.move(tempFile, targetFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException error) {
                Files.deleteIfExists(tempFile);
                throw error;
            }
        }
    }
}
