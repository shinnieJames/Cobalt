package com.github.auties00.cobalt.store.persistent;

import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientType;
import com.github.auties00.cobalt.store.ProtobufAccountStore;
import com.github.auties00.cobalt.store.ProtobufContactStore;
import com.github.auties00.cobalt.store.ProtobufSettingsStore;
import com.github.auties00.cobalt.store.ProtobufSignalStore;
import com.github.auties00.cobalt.store.ProtobufSyncStore;
import com.github.auties00.cobalt.store.ProtobufWebSessionStore;
import com.github.auties00.cobalt.store.ProtobufWhatsAppStore;
import com.github.auties00.cobalt.store.WhatsAppStoreFactory;
import com.github.auties00.cobalt.util.BufferedProtobufInputStream;
import com.github.auties00.cobalt.util.BufferedProtobufOutputStream;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ConcurrentMap;

import static java.lang.System.Logger.Level.WARNING;

/**
 * The {@link ProtobufWhatsAppStore} that persists session metadata to a single protobuf file on disk
 * and offloads every message body to an embedded {@link PersistentMessageStore LMDB} env.
 *
 * <p>Data layout under the session directory:
 * <ul>
 *   <li>{@code store.proto} holds the aggregate inherited from {@link ProtobufWhatsAppStore} (the five
 *       nested sub-stores, the retained runtime scalars) plus the {@link PersistentChatStore} at index
 *       82, whose chat and newsletter entries carry metadata only; message bodies live in LMDB.</li>
 *   <li>{@code messages.mdbx/} holds the LMDB env.</li>
 * </ul>
 *
 * @apiNote
 * Cobalt embedders obtain a {@link PersistentStore} indirectly through
 * {@link WhatsAppStoreFactory#persistent()}; the class is package-private so the persistence strategy
 * is not part of the public API surface.
 *
 * @implNote
 * This implementation captures {@link #hashCode()} into {@link #storeHashCode} at the end of each
 * successful {@link #save()} and short-circuits subsequent saves when nothing has changed. The LMDB
 * facade is owned by the {@link PersistentChatStore} and attached after construction.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
@ProtobufMessage
final class PersistentStore extends ProtobufWhatsAppStore {
    /**
     * The name of the metadata file written under the session directory.
     */
    private static final String STORE_FILE = "store.proto";

    /**
     * The name of the libmdbx env sub-directory under the session directory.
     */
    private static final String MESSAGES_DIRECTORY = "messages.mdbx";

    /**
     * The persistence-variant chat sub-store holding the LMDB-backed chats and newsletters.
     */
    @ProtobufProperty(index = 82, type = ProtobufType.MESSAGE)
    final PersistentChatStore chatStore;

    /**
     * The hash code captured at the end of the last successful {@link #save()}.
     */
    private volatile Integer storeHashCode;

    /**
     * Constructs a {@code PersistentStore} from its composed sub-stores and the persistent chat store.
     *
     * @apiNote
     * Package-private and intended for the generated {@code PersistentStoreBuilder} and the protobuf
     * deserialiser. The LMDB facade is wired by {@link PersistentStoreFactory} via
     * {@link #attachMessageStore(PersistentMessageStore)} immediately after construction.
     *
     * @param signalStore           the signal sub-store
     * @param accountStore          the account sub-store
     * @param contactStore          the contact sub-store
     * @param syncStore             the sync sub-store
     * @param settingsStore         the settings sub-store
     * @param directory             the session directory
     * @param companionMmsAuthNonce the MMS auth nonce, or {@code null}
     * @param shareableChatLinkKey  the shareable-chat-link key, or {@code null}
     * @param wamSequenceNumbersMap the WAM sequence-number map, or {@code null}
     * @param webSessionStore       the web-GraphQL credential sub-store, or {@code null} for an empty one
     * @param chatStore             the persistent chat sub-store, or {@code null} for an empty one
     */
    PersistentStore(ProtobufSignalStore signalStore, ProtobufAccountStore accountStore, ProtobufContactStore contactStore, ProtobufSyncStore syncStore, ProtobufSettingsStore settingsStore, Path directory, String companionMmsAuthNonce, byte[] shareableChatLinkKey, ConcurrentMap<Integer, Integer> wamSequenceNumbersMap, ProtobufWebSessionStore webSessionStore, PersistentChatStore chatStore) {
        super(signalStore, accountStore, contactStore, syncStore, settingsStore, directory, companionMmsAuthNonce, shareableChatLinkKey, wamSequenceNumbersMap, webSessionStore);
        this.chatStore = chatStore != null ? chatStore : new PersistentChatStore(null, null, null, null);
        this.chatStore.bindContacts(contactStore());
    }

    @Override
    public PersistentChatStore chatStore() {
        return chatStore;
    }

    @Override
    protected ConcurrentMap<Integer, Integer> wamSequenceNumbersMap() {
        return super.wamSequenceNumbersMap();
    }

    /**
     * Wires the LMDB facade into the persistent chat sub-store.
     *
     * @apiNote
     * Called by {@link PersistentStoreFactory} after construction or deserialisation.
     *
     * @param messageStore the freshly opened LMDB facade
     */
    void attachMessageStore(PersistentMessageStore messageStore) {
        chatStore.attachMessageStore(messageStore);
    }

    /**
     * Returns the LMDB facade owned by the persistent chat sub-store.
     *
     * @return the message store, or {@code null} if not yet attached
     */
    PersistentMessageStore messageStore() {
        return chatStore.messageStore();
    }

    /**
     * Returns the path to the LMDB env directory for the given session.
     *
     * @param clientType    the client type
     * @param baseDirectory the root directory under which per-session folders are created
     * @param sessionId     the session UUID string or phone-number string
     * @return the LMDB env directory
     * @throws IOException if the parent session directory cannot be created
     */
    static Path messagesEnvPath(LinkedWhatsAppClientType clientType, Path baseDirectory, String sessionId) throws IOException {
        return getSessionDirectory(clientType, baseDirectory, sessionId)
                .resolve(MESSAGES_DIRECTORY);
    }

    /**
     * Returns the path to the {@code store.proto} metadata file for the given session.
     *
     * @param clientType    the client type
     * @param baseDirectory the root directory under which per-session folders are created
     * @param sessionId     the session UUID string or phone-number string
     * @return the metadata file path
     * @throws IOException if the parent session directory cannot be created
     */
    static Path storeFilePath(LinkedWhatsAppClientType clientType, Path baseDirectory, String sessionId) throws IOException {
        return getSessionFile(clientType, baseDirectory, sessionId, STORE_FILE);
    }

    /**
     * Deserializes a {@code store.proto} metadata snapshot into a {@code PersistentStore}.
     *
     * @apiNote
     * Internal helper for {@link PersistentStoreFactory#load}; the LMDB facade is attached separately.
     *
     * @param storeFile the metadata file
     * @return the deserialized store
     * @throws IOException if the file cannot be read or decoded
     */
    static PersistentStore deserialize(Path storeFile) throws IOException {
        try (var stream = new BufferedProtobufInputStream(storeFile)) {
            return PersistentStoreSpec.decode(stream);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation uses double-checked locking on {@link #storeHashCode}; the slow path
     * serialises on {@code synchronized(this)}. IO failures are logged and swallowed because callers
     * cannot meaningfully recover; the next successful save observes the same dirty state.
     */
    @Override
    public void save() {
        var newHashCode = hashCode();
        if (storeHashCode == null || storeHashCode != newHashCode) {
            synchronized (this) {
                if (storeHashCode == null || storeHashCode != newHashCode) {
                    try {
                        serializeStore();
                        storeHashCode = newHashCode;
                    } catch (IOException error) {
                        logger.log(WARNING, "Error while serializing store", error);
                    }
                }
            }
        }
    }

    /**
     * Writes the current metadata snapshot to {@code store.proto} via a sibling temp file followed by
     * an atomic move.
     *
     * @throws IOException if the file cannot be created, written, or moved
     */
    private void serializeStore() throws IOException {
        var path = storeFilePath(accountStore().clientType(), directory(), accountStore().uuid().toString());
        var parent = path.getParent();
        Files.createDirectories(parent);
        var tempFile = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
        try {
            try (var stream = new BufferedProtobufOutputStream(tempFile)) {
                PersistentStoreSpec.encode(this, stream);
            }
            Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException error) {
            Files.deleteIfExists(tempFile);
            throw error;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation closes the LMDB facade before recursively removing the session directory so
     * the directory remove can succeed on Windows, where open mapped files cannot be unlinked.
     */
    @Override
    public void delete() throws IOException {
        var folderPath = getSessionDirectory(accountStore().clientType(), directory(), accountStore().uuid().toString());
        var messageStore = chatStore.messageStore();
        if (messageStore != null) {
            messageStore.close();
        }
        deleteRecursively(folderPath);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation is a no-op: the metadata snapshot decodes synchronously in
     * {@link PersistentStoreFactory#load}, so there is no pending background work to await.
     */
    @Override
    public void await() {
    }

    /**
     * Closes the LMDB env so the session can be shut down without being deleted.
     *
     * @apiNote
     * Called by the factory during ordinary shutdown.
     */
    void close() {
        var messageStore = chatStore.messageStore();
        if (messageStore != null) {
            messageStore.close();
        }
    }
}
