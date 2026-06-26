package com.github.auties00.cobalt.store.linked.protobuf;

import com.github.auties00.cobalt.store.linked.LinkedWhatsAppAccountStore;
import com.github.auties00.cobalt.store.linked.LinkedWhatsAppWamStore;
import com.github.auties00.cobalt.wam.model.WamChannel;
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
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
    private final ConcurrentMap<Integer, Integer> sequenceNumbersMap;

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
     * @param sequenceNumbersMap the WAM sequence-number map, or {@code null} for an empty map
     */
    ProtobufLinkedWhatsAppWamStore(ConcurrentMap<Integer, Integer> sequenceNumbersMap) {
        this.sequenceNumbersMap = Objects.requireNonNullElseGet(sequenceNumbersMap, ConcurrentHashMap::new);
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
                            && Objects.equals(sequenceNumbersMap, that.sequenceNumbersMap);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(sequenceNumbersMap);
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
