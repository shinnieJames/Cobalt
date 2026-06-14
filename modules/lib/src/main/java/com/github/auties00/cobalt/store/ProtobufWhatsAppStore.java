package com.github.auties00.cobalt.store;

import com.github.auties00.cobalt.listener.WhatsAppListener;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientOfflineResumeState;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientType;
import com.github.auties00.cobalt.client.WhatsAppClientProxy;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.mixin.PathMixin;
import com.github.auties00.cobalt.model.sync.action.chat.UsernameChatStartModeAction;
import com.github.auties00.cobalt.wam.model.WamChannel;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.builtin.ProtobufLazyMixin;
import it.auties.protobuf.model.ProtobufType;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap.KeySetView;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The in-memory backbone shared by every {@link LinkedWhatsAppStore} persistence strategy.
 *
 * <p>This abstract aggregate composes the seven domain sub-stores ({@link SignalStore},
 * {@link AccountStore}, {@link ContactStore}, {@link ChatStore}, {@link SyncStore},
 * {@link SettingsStore}, {@link BusinessStore}) and exposes them through accessors. It does not
 * delegate domain operations; callers reach domain state through the relevant sub-store. It owns only
 * the session-runtime state that belongs to no single domain: the registered listeners, the proxy,
 * the offline-resume coordination, the routing hints, the receipt-record buffer, the linked-device
 * list, the WAM telemetry buffers and sequence numbers, and the on-disk directory shape.
 *
 * <p>The chat sub-store is the only domain whose backing differs per persistence strategy, so it is
 * supplied by the concrete subclass through {@link #chatStore()}; the other six are nested
 * {@code MESSAGE} fields serialised inline with this aggregate ({@link BusinessStore} alone is
 * transient and never serialised).
 *
 * @implSpec
 * Concrete subclasses provide the variant {@link #chatStore()} and the persistence lifecycle
 * ({@link #await()}, {@link #save()}, {@link #delete()}).
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
@ProtobufMessage
public abstract class ProtobufWhatsAppStore implements LinkedWhatsAppStore {
    /**
     * The time-to-live for a cached device-list entry before it is considered stale.
     *
     * @apiNote
     * Limits how long stale per-recipient device fan-out lists may be reused for outgoing message
     * sessions; one day matches the refresh cadence the companion fleet honours.
     */
    public static final Duration DEVICE_TTL = Duration.ofDays(1);

    /**
     * The filename prefix used by {@link #openWamPendingBufferWriter} when staging a WAM event buffer.
     */
    private static final String WAM_BUFFER_PREFIX = "wam_buffer_";

    /**
     * The filename suffix paired with {@link #WAM_BUFFER_PREFIX} for staged WAM event buffers.
     */
    private static final String WAM_BUFFER_SUFFIX = ".bin";

    /**
     * The Signal-protocol cryptographic sub-store.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    private final ProtobufSignalStore signalStore;

    /**
     * The account-identity and profile sub-store.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    private final ProtobufAccountStore accountStore;

    /**
     * The address-book and per-peer-device sub-store.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    private final ProtobufContactStore contactStore;

    /**
     * The app-state-sync and feature-flag sub-store.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
    private final ProtobufSyncStore syncStore;

    /**
     * The user-preference and settings sub-store.
     */
    @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
    private final ProtobufSettingsStore settingsStore;

    /**
     * The on-disk directory under which this store persists, or {@code null} for an in-memory store.
     */
    @ProtobufProperty(index = 6, type = ProtobufType.STRING, mixins = {PathMixin.class, ProtobufLazyMixin.class})
    private final Path directory;

    /**
     * The companion-side authentication nonce for the MMS history-sync blob release.
     */
    @ProtobufProperty(index = 7, type = ProtobufType.STRING)
    private String companionMmsAuthNonce;

    /**
     * The per-account key protecting the opaque chat identifier in shareable chat links.
     */
    @ProtobufProperty(index = 8, type = ProtobufType.BYTES)
    private byte[] shareableChatLinkKey;

    /**
     * The WAM event sequence numbers per channel for dedup.
     */
    @ProtobufProperty(index = 9, type = ProtobufType.MAP, mapKeyType = ProtobufType.INT32, mapValueType = ProtobufType.INT32)
    private final ConcurrentMap<Integer, Integer> wamSequenceNumbersMap;

    /**
     * The web-GraphQL credential sub-store holding the WhatsApp Web GraphQL session cookie/lsd and the Facebook GraphQL token.
     */
    @ProtobufProperty(index = 10, type = ProtobufType.MESSAGE)
    private final ProtobufWebSessionStore webSessionStore;

    /**
     * The WhatsApp Business and payments sub-store; not persisted (all-transient state).
     */
    private final ProtobufBusinessStore businessStore;

    /**
     * The concurrent set of registered event listeners; not persisted.
     */
    private final KeySetView<WhatsAppListener, Boolean> listeners;

    /**
     * The optional HTTP/SOCKS proxy configuration; not persisted.
     */
    private WhatsAppClientProxy proxy;

    /**
     * The logger instance for this store; not persisted.
     */
    protected final System.Logger logger;

    /**
     * The offline-resume state; not persisted.
     */
    private volatile LinkedWhatsAppClientOfflineResumeState offlineResumeState;

    /**
     * The latch coordinating offline-delivery completion; not persisted.
     */
    private volatile CountDownLatch offlineDeliveryLatch;

    /**
     * The opaque server-routing token for load balancer pinning; not persisted.
     */
    private byte[] routingInfo;

    /**
     * The routing domain hint; not persisted.
     */
    private String routingDomain;

    /**
     * The companion device pairing expiration deadline; not persisted.
     */
    private Instant clientExpiration;

    /**
     * The pending receipt-record recipients per sent message id; not persisted.
     */
    private final ConcurrentMap<String, Set<Jid>> pendingMessageRecipients;

    /**
     * The list of linked companion devices; not persisted.
     */
    private volatile List<Jid> linkedDevices;

    /**
     * The timestamp of when this device paired with the primary; not persisted.
     */
    private volatile Instant pairingTimestamp;

    /**
     * Whether the account has a profile avatar; not persisted.
     */
    private Boolean hasAvatar;

    /**
     * The salt for notification-content-token hashing; not persisted.
     */
    private byte[] notificationContentTokenSalt;

    /**
     * The username chat-start mode setting; not persisted.
     */
    private UsernameChatStartModeAction.ChatStartMode usernameChatStartMode;

    /**
     * Constructs the aggregate from its composed sub-stores and retained runtime state.
     *
     * @implNote
     * This implementation wires the account sub-store into the signal and contact sub-stores (for
     * self-address resolution) and allocates the business sub-store and every transient runtime
     * collection.
     *
     * @param signalStore           the signal sub-store, never {@code null}
     * @param accountStore          the account sub-store, never {@code null}
     * @param contactStore          the contact sub-store, never {@code null}
     * @param syncStore             the sync sub-store, never {@code null}
     * @param settingsStore         the settings sub-store, never {@code null}
     * @param directory             the session directory, or {@code null} for in-memory
     * @param companionMmsAuthNonce the MMS auth nonce, or {@code null}
     * @param shareableChatLinkKey  the shareable-chat-link key, or {@code null}
     * @param wamSequenceNumbersMap the WAM sequence-number map, or {@code null} for an empty map
     * @param webSessionStore       the web-GraphQL credential sub-store, or {@code null} for an empty one
     */
    protected ProtobufWhatsAppStore(ProtobufSignalStore signalStore, ProtobufAccountStore accountStore, ProtobufContactStore contactStore, ProtobufSyncStore syncStore, ProtobufSettingsStore settingsStore, Path directory, String companionMmsAuthNonce, byte[] shareableChatLinkKey, ConcurrentMap<Integer, Integer> wamSequenceNumbersMap, ProtobufWebSessionStore webSessionStore) {
        this.signalStore = Objects.requireNonNull(signalStore, "signalStore cannot be null");
        this.accountStore = Objects.requireNonNull(accountStore, "accountStore cannot be null");
        this.contactStore = Objects.requireNonNull(contactStore, "contactStore cannot be null");
        this.syncStore = Objects.requireNonNull(syncStore, "syncStore cannot be null");
        this.settingsStore = Objects.requireNonNull(settingsStore, "settingsStore cannot be null");
        this.directory = directory;
        this.companionMmsAuthNonce = companionMmsAuthNonce;
        this.shareableChatLinkKey = shareableChatLinkKey;
        this.wamSequenceNumbersMap = Objects.requireNonNullElseGet(wamSequenceNumbersMap, ConcurrentHashMap::new);
        this.webSessionStore = Objects.requireNonNullElseGet(webSessionStore, () -> new ProtobufWebSessionStore(null, null));
        this.businessStore = new ProtobufBusinessStore();
        this.signalStore.bindAccount(accountStore);
        this.contactStore.bindAccount(accountStore);
        this.listeners = ConcurrentHashMap.newKeySet();
        this.logger = System.getLogger(this.getClass().getName());
        this.offlineResumeState = LinkedWhatsAppClientOfflineResumeState.INIT;
        this.offlineDeliveryLatch = new CountDownLatch(1);
        this.pendingMessageRecipients = new ConcurrentHashMap<>();
    }

    @Override
    public ProtobufSignalStore signalStore() {
        return signalStore;
    }

    @Override
    public ProtobufAccountStore accountStore() {
        return accountStore;
    }

    @Override
    public ProtobufContactStore contactStore() {
        return contactStore;
    }

    @Override
    public ProtobufSyncStore syncStore() {
        return syncStore;
    }

    @Override
    public ProtobufSettingsStore settingsStore() {
        return settingsStore;
    }

    @Override
    public ProtobufBusinessStore businessStore() {
        return businessStore;
    }

    @Override
    public ProtobufWebSessionStore webSessionStore() {
        return webSessionStore;
    }

    @Override
    public abstract ProtobufChatStore chatStore();

    /**
     * Returns the on-disk directory under which this store persists, or {@code null} if in-memory.
     *
     * @return the persistence directory, or {@code null}
     */
    public Path directory() {
        return directory;
    }

    /**
     * Returns the live WAM sequence-number map backing this store.
     *
     * @return the live WAM sequence-number map
     */
    protected ConcurrentMap<Integer, Integer> wamSequenceNumbersMap() {
        return wamSequenceNumbersMap;
    }

    @Override
    public WhatsAppListener addListener(WhatsAppListener listener) {
        listeners.add(listener);
        return listener;
    }

    @Override
    public boolean removeListener(WhatsAppListener listener) {
        return listeners.remove(listener);
    }

    @Override
    public Collection<WhatsAppListener> listeners() {
        return List.copyOf(listeners);
    }

    @Override
    public Optional<WhatsAppClientProxy> proxy() {
        return Optional.ofNullable(proxy);
    }

    @Override
    public LinkedWhatsAppStore setProxy(WhatsAppClientProxy proxy) {
        this.proxy = proxy;
        return this;
    }

    @Override
    public LinkedWhatsAppClientOfflineResumeState offlineResumeState() {
        return offlineResumeState;
    }

    @Override
    public LinkedWhatsAppStore setOfflineResumeState(LinkedWhatsAppClientOfflineResumeState state) {
        this.offlineResumeState = Objects.requireNonNull(state, "state cannot be null");
        if (state == LinkedWhatsAppClientOfflineResumeState.COMPLETE) {
            offlineDeliveryLatch.countDown();
        } else if (state == LinkedWhatsAppClientOfflineResumeState.INIT) {
            offlineDeliveryLatch = new CountDownLatch(1);
        }
        return this;
    }

    @Override
    public boolean isResumeFromRestartComplete() {
        return offlineResumeState != LinkedWhatsAppClientOfflineResumeState.INIT
               && offlineResumeState != LinkedWhatsAppClientOfflineResumeState.RESUME_ON_RESTART;
    }

    @Override
    public void waitForOfflineDeliveryEnd() {
        if (offlineResumeState == LinkedWhatsAppClientOfflineResumeState.COMPLETE) {
            return;
        }
        try {
            offlineDeliveryLatch.await(5, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public Optional<byte[]> routingInfo() {
        return Optional.ofNullable(routingInfo);
    }

    @Override
    public LinkedWhatsAppStore setRoutingInfo(byte[] routingInfo) {
        this.routingInfo = routingInfo;
        return this;
    }

    @Override
    public Optional<String> routingDomain() {
        return Optional.ofNullable(routingDomain);
    }

    @Override
    public LinkedWhatsAppStore setRoutingDomain(String routingDomain) {
        this.routingDomain = routingDomain;
        return this;
    }

    @Override
    public Optional<Instant> clientExpiration() {
        return Optional.ofNullable(clientExpiration);
    }

    @Override
    public LinkedWhatsAppStore setClientExpiration(Instant clientExpiration) {
        this.clientExpiration = clientExpiration;
        return this;
    }

    @Override
    public void createOrMergeReceiptRecords(String messageId, Collection<Jid> recipientJids) {
        if (messageId == null || recipientJids == null || recipientJids.isEmpty()) {
            return;
        }
        pendingMessageRecipients.compute(messageId, (k, existing) -> {
            var set = existing != null ? existing : ConcurrentHashMap.<Jid>newKeySet();
            set.addAll(recipientJids);
            return set;
        });
    }

    @Override
    public void removeReceiptRecords(String messageId) {
        pendingMessageRecipients.remove(messageId);
    }

    @Override
    public Set<Jid> findReceiptRecords(String messageId) {
        if (messageId == null) {
            return Set.of();
        }
        var recipients = pendingMessageRecipients.get(messageId);
        return recipients != null ? Set.copyOf(recipients) : Set.of();
    }

    @Override
    public List<Jid> linkedDevices() {
        var current = linkedDevices;
        return current == null ? List.of() : List.copyOf(current);
    }

    @Override
    public LinkedWhatsAppStore setLinkedDevices(Collection<Jid> linkedDevices) {
        this.linkedDevices = linkedDevices == null ? null : List.copyOf(linkedDevices);
        return this;
    }

    @Override
    public Optional<Instant> pairingTimestamp() {
        return Optional.ofNullable(pairingTimestamp);
    }

    @Override
    public LinkedWhatsAppStore setPairingTimestamp(Instant pairingTimestamp) {
        this.pairingTimestamp = pairingTimestamp;
        return this;
    }

    @Override
    public Optional<Boolean> hasAvatar() {
        return Optional.ofNullable(hasAvatar);
    }

    @Override
    public LinkedWhatsAppStore setHasAvatar(Boolean hasAvatar) {
        this.hasAvatar = hasAvatar;
        return this;
    }

    @Override
    public Optional<byte[]> notificationContentTokenSalt() {
        return Optional.ofNullable(notificationContentTokenSalt);
    }

    @Override
    public LinkedWhatsAppStore setNotificationContentTokenSalt(byte[] salt) {
        this.notificationContentTokenSalt = salt;
        return this;
    }

    @Override
    public Optional<String> companionMmsAuthNonce() {
        return Optional.ofNullable(companionMmsAuthNonce);
    }

    @Override
    public LinkedWhatsAppStore setCompanionMmsAuthNonce(String nonce) {
        this.companionMmsAuthNonce = nonce;
        return this;
    }

    @Override
    public Optional<byte[]> shareableChatLinkKey() {
        return Optional.ofNullable(shareableChatLinkKey);
    }

    @Override
    public LinkedWhatsAppStore setShareableChatLinkKey(byte[] key) {
        this.shareableChatLinkKey = key;
        return this;
    }

    @Override
    public Optional<UsernameChatStartModeAction.ChatStartMode> usernameChatStartMode() {
        return Optional.ofNullable(usernameChatStartMode);
    }

    @Override
    public LinkedWhatsAppStore setUsernameChatStartMode(UsernameChatStartModeAction.ChatStartMode mode) {
        this.usernameChatStartMode = mode;
        return this;
    }

    @Override
    public OptionalInt findWamSequenceNumber(WamChannel channel) {
        Objects.requireNonNull(channel, "channel cannot be null");
        var stored = wamSequenceNumbersMap.get(channel.id());
        return stored == null ? OptionalInt.empty() : OptionalInt.of(stored);
    }

    @Override
    public LinkedWhatsAppStore putWamSequenceNumber(WamChannel channel, int sequenceNumber) {
        Objects.requireNonNull(channel, "channel cannot be null");
        wamSequenceNumbersMap.put(channel.id(), sequenceNumber);
        return this;
    }

    @Override
    public Collection<String> wamPendingBufferKeys() {
        if (directory == null) {
            return List.of();
        }
        Path sessionDir;
        try {
            sessionDir = getSessionDirectory(accountStore.clientType(), directory, accountStore.uuid().toString());
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
    public OutputStream openWamPendingBufferWriter(String saveKey) throws IOException {
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
    public Optional<InputStream> openWamPendingBufferReader(String saveKey) throws IOException {
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
    public boolean removeWamPendingBuffer(String saveKey) throws IOException {
        Objects.requireNonNull(saveKey, "saveKey cannot be null");
        validateSaveKey(saveKey);
        if (directory == null) {
            return false;
        }
        return Files.deleteIfExists(wamBufferPath(saveKey));
    }

    @Override
    public LinkedWhatsAppStore clearWamPendingBuffers() throws IOException {
        if (directory == null) {
            return this;
        }
        var sessionDir = getSessionDirectory(accountStore.clientType(), directory, accountStore.uuid().toString());
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
        return getSessionFile(
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

    /**
     * Resolves the home directory for {@code type} under {@code baseDirectory}, creating it if necessary.
     *
     * @param type          the client type
     * @param baseDirectory the base storage directory
     * @return the resolved home directory, guaranteed to exist
     * @throws IOException if the directory cannot be created
     */
    public static Path getHomeDirectory(LinkedWhatsAppClientType type, Path baseDirectory) throws IOException {
        var id = switch (type) {
            case WEB -> "web";
            case MOBILE -> "mobile";
        };
        var result = baseDirectory.resolve(id);
        Files.createDirectories(result);
        return result;
    }

    /**
     * Resolves the session directory identified by {@code path}, creating it if necessary.
     *
     * @param clientType    the client type that owns the home directory layer
     * @param baseDirectory the base storage directory
     * @param path          the session identifier
     * @return the resolved session directory, guaranteed to exist
     * @throws IOException if the directory cannot be created
     */
    public static Path getSessionDirectory(LinkedWhatsAppClientType clientType, Path baseDirectory, String path) throws IOException {
        var result = getHomeDirectory(clientType, baseDirectory)
                .resolve(path);
        Files.createDirectories(result);
        return result;
    }

    /**
     * Resolves the path of {@code fileName} inside the session directory identified by {@code uuid}.
     *
     * @param clientType    the client type that owns the home directory layer
     * @param baseDirectory the base storage directory
     * @param uuid          the session identifier
     * @param fileName      the file name inside the session
     * @return the resolved path
     * @throws IOException if the parent directories cannot be created
     */
    public static Path getSessionFile(LinkedWhatsAppClientType clientType, Path baseDirectory, String uuid, String fileName) throws IOException {
        return getSessionDirectory(clientType, baseDirectory, uuid)
                .resolve(fileName);
    }

    /**
     * Recursively deletes {@code path} and everything underneath it.
     *
     * @param path the path to delete
     * @throws IOException if any filesystem operation fails
     */
    public static void deleteRecursively(Path path) throws IOException {
        if (Files.notExists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof ProtobufWhatsAppStore that
                            && Objects.equals(signalStore, that.signalStore)
                            && Objects.equals(accountStore, that.accountStore)
                            && Objects.equals(contactStore, that.contactStore)
                            && Objects.equals(syncStore, that.syncStore)
                            && Objects.equals(settingsStore, that.settingsStore)
                            && Objects.equals(chatStore(), that.chatStore())
                            && Objects.equals(directory, that.directory)
                            && Objects.equals(companionMmsAuthNonce, that.companionMmsAuthNonce)
                            && Arrays.equals(shareableChatLinkKey, that.shareableChatLinkKey)
                            && Objects.equals(wamSequenceNumbersMap, that.wamSequenceNumbersMap)
                            && Objects.equals(webSessionStore, that.webSessionStore);
    }

    @Override
    public int hashCode() {
        return Objects.hash(signalStore, accountStore, contactStore, syncStore, settingsStore, chatStore(),
                directory, companionMmsAuthNonce, Arrays.hashCode(shareableChatLinkKey), wamSequenceNumbersMap, webSessionStore);
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
