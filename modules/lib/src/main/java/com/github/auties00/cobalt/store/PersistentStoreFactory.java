package com.github.auties00.cobalt.store;

import com.github.auties00.cobalt.client.WhatsAppClientSixPartsKeys;
import com.github.auties00.cobalt.client.WhatsAppClientType;
import com.github.auties00.cobalt.client.WhatsAppDevice;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.util.StorePathUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@link WhatsAppStoreFactory} that snapshots session metadata to {@code store.proto} and
 * offloads message bodies to an embedded {@link PersistentMessageStore LMDB} env per session.
 *
 * @apiNote
 * Cobalt embedders obtain instances through {@link WhatsAppStoreFactory#persistent()} and its
 * overloads; the class is package-private so the persistence strategy is not part of the public
 * API surface. Each session lives under {@code <baseDirectory>/<clientType>/<sessionId>/} with a
 * {@code store.proto} metadata snapshot beside a {@code messages.lmdb/} env directory.
 *
 * @implNote
 * This implementation runs an orphan-recovery pass on {@link #load load}: after the metadata
 * snapshot deserialises, the factory walks the LMDB env once and inserts metadata stubs for any
 * chat or newsletter that holds bodies in LMDB but is missing from the snapshot. This bridges the
 * post-commit window where an LMDB write landed but the next metadata save never ran (process
 * killed in between). Recovered entries surface through the normal
 * {@link WhatsAppStore#chats()} and {@link WhatsAppStore#newsletters()} collections so callers
 * see a consistent shape regardless of whether the previous shutdown was clean or crashy.
 */
final class PersistentStoreFactory implements WhatsAppStoreFactory {
    /**
     * The default root directory for Cobalt persistent sessions.
     *
     * @implNote
     * This implementation resolves to {@code $HOME/.cobalt/proto}; embedders that need a custom
     * location pass an explicit directory to {@link #PersistentStoreFactory(Path)} or
     * {@link #PersistentStoreFactory(Path, long)}.
     */
    private static final Path DEFAULT_DIRECTORY = Path.of(System.getProperty("user.home"), ".cobalt", "proto");

    /**
     * The default LMDB map size in bytes.
     *
     * @implNote
     * This implementation uses 256 MiB. On Windows this is the preallocated sparse file size so a
     * much larger default looks alarming in Explorer; the {@link PersistentMessageStore} doubles
     * the map on {@code MDB_MAP_FULL}, so the cap rises automatically when traffic demands it.
     */
    private static final long DEFAULT_MAP_SIZE = 256L * 1024 * 1024;

    /**
     * The root directory under which per-session folders are created.
     */
    private final Path directory;

    /**
     * The initial LMDB map size in bytes used for newly opened envs.
     */
    private final long mapSize;

    /**
     * Constructs a factory using {@link #DEFAULT_DIRECTORY} and {@link #DEFAULT_MAP_SIZE}.
     *
     * @apiNote
     * Used by {@link WhatsAppStoreFactory#persistent()}.
     */
    PersistentStoreFactory() {
        this(DEFAULT_DIRECTORY, DEFAULT_MAP_SIZE);
    }

    /**
     * Constructs a factory using the given root directory and {@link #DEFAULT_MAP_SIZE}.
     *
     * @apiNote
     * Used by {@link WhatsAppStoreFactory#persistent(Path)}.
     *
     * @param directory the root directory under which per-session folders are created; must not
     *                  be {@code null}
     */
    PersistentStoreFactory(Path directory) {
        this(directory, DEFAULT_MAP_SIZE);
    }

    /**
     * Constructs a factory using the given root directory and initial LMDB map size.
     *
     * @apiNote
     * Used by {@link WhatsAppStoreFactory#persistent(Path, long)}.
     *
     * @implNote
     * This implementation rejects non-positive {@code mapSize} eagerly so the failure surfaces at
     * factory construction time rather than later inside the LMDB binding.
     *
     * @param directory the root directory under which per-session folders are created; must not
     *                  be {@code null}
     * @param mapSize   the initial LMDB map size in bytes; must be positive
     * @throws IllegalArgumentException if {@code mapSize <= 0}
     * @throws NullPointerException     if {@code directory} is {@code null}
     */
    PersistentStoreFactory(Path directory, long mapSize) {
        if (mapSize <= 0) {
            throw new IllegalArgumentException("mapSize must be positive");
        }
        this.directory = Objects.requireNonNull(directory, "directory cannot be null");
        this.mapSize = mapSize;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation forwards to {@link #loadSession(WhatsAppClientType, String)} with the
     * UUID stringified.
     */
    @Override
    public Optional<WhatsAppStore> load(WhatsAppClientType clientType, UUID uuid) throws IOException {
        Objects.requireNonNull(clientType, "clientType cannot be null");
        Objects.requireNonNull(uuid, "uuid cannot be null");
        return loadSession(clientType, uuid.toString());
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation forwards to {@link #loadSession(WhatsAppClientType, String)} with the
     * phone number stringified.
     */
    @Override
    public Optional<WhatsAppStore> load(WhatsAppClientType clientType, long phoneNumber) throws IOException {
        Objects.requireNonNull(clientType, "clientType cannot be null");
        return loadSession(clientType, String.valueOf(phoneNumber));
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation asks {@link StorePathUtils#getLatestSessionDirectory(WhatsAppClientType, Path)}
     * for the most recently modified session folder and forwards its name to
     * {@link #loadSession(WhatsAppClientType, String)}.
     */
    @Override
    public Optional<WhatsAppStore> loadLatest(WhatsAppClientType clientType) throws IOException {
        Objects.requireNonNull(clientType, "clientType cannot be null");
        var latest = StorePathUtils.getLatestSessionDirectory(clientType, directory);
        if (latest.isEmpty()) {
            return Optional.empty();
        }
        return loadSession(clientType, latest.get().getFileName().toString());
    }

    /**
     * Loads the session identified by {@code sessionId} for the given client type, opens its
     * LMDB env, attaches it to the deserialised store, and runs the orphan-recovery pass.
     *
     * @apiNote
     * Internal helper for the three public {@code load} overloads.
     *
     * @param clientType the client type
     * @param sessionId  the session UUID string or phone-number string
     * @return the loaded store, or {@link Optional#empty()} if no {@code store.proto} exists for
     *         that session
     * @throws IOException if the metadata file cannot be read or decoded
     */
    private Optional<WhatsAppStore> loadSession(WhatsAppClientType clientType, String sessionId) throws IOException {
        var storeFile = PersistentStore.storeFilePath(clientType, directory, sessionId);
        if (Files.notExists(storeFile)) {
            return Optional.empty();
        }
        var store = PersistentStore.deserialize(storeFile);
        var envPath = PersistentStore.messagesEnvPath(clientType, directory, sessionId);
        var messageStore = PersistentMessageStore.open(envPath, mapSize);
        store.attachMessageStore(messageStore);
        recoverOrphans(store, messageStore);
        return Optional.of(store);
    }

    /**
     * Inserts metadata stubs for every chat or newsletter that holds messages in
     * {@code messageStore} but has no corresponding entry in the deserialised snapshot.
     *
     * @apiNote
     * Bridges the post-commit window where an LMDB write succeeded but the next metadata save
     * never happened (for example, the process was killed between the two).
     *
     * @implNote
     * This implementation walks {@link PersistentMessageStore#distinctChatJids()} and
     * {@link PersistentMessageStore#distinctNewsletterJids()} once and reuses
     * {@link PersistentStore#addNewChat} and {@link PersistentStore#addNewNewsletter} to build the
     * stubs so the inserted entries receive the same attachment handling as fresh ones.
     *
     * @param store        the freshly attached store
     * @param messageStore the just-opened LMDB facade
     */
    private static void recoverOrphans(PersistentStore store, PersistentMessageStore messageStore) {
        for (var chatJid : messageStore.distinctChatJids()) {
            if (!store.chats.containsKey(chatJid)) {
                store.addNewChat(chatJid);
            }
        }
        for (var newsletterJid : messageStore.distinctNewsletterJids()) {
            if (!store.newsletters.containsKey(newsletterJid)) {
                store.addNewNewsletter(newsletterJid);
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation generates a random UUID when {@code uuid} is {@code null}, opens a
     * fresh LMDB env, and returns an otherwise empty store with the platform-appropriate device
     * descriptor.
     */
    @Override
    public WhatsAppStore create(WhatsAppClientType clientType, UUID uuid) throws IOException {
        Objects.requireNonNull(clientType, "clientType cannot be null");
        var resolvedUuid = Objects.requireNonNullElseGet(uuid, UUID::randomUUID);
        var sessionId = resolvedUuid.toString();
        var sessionDirectory = StorePathUtils.getSessionDirectory(clientType, directory, sessionId);
        var store = new PersistentStoreBuilder()
                .uuid(resolvedUuid)
                .clientType(clientType)
                .device(defaultDevice(clientType))
                .directory(sessionDirectory)
                .build();
        attachFreshLmdb(store, clientType, sessionId);
        return store;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation keys the session directory by the stringified phone number and assigns
     * a fresh random UUID.
     */
    @Override
    public WhatsAppStore create(WhatsAppClientType clientType, long phoneNumber) throws IOException {
        Objects.requireNonNull(clientType, "clientType cannot be null");
        var sessionId = String.valueOf(phoneNumber);
        var sessionDirectory = StorePathUtils.getSessionDirectory(clientType, directory, sessionId);
        var store = new PersistentStoreBuilder()
                .uuid(UUID.randomUUID())
                .phoneNumber(phoneNumber)
                .clientType(clientType)
                .device(defaultDevice(clientType))
                .directory(sessionDirectory)
                .build();
        attachFreshLmdb(store, clientType, sessionId);
        return store;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation builds a web-device store seeded with the supplied Noise and identity
     * key pairs, sets {@code registered=true} so the pairing pipeline is skipped, and assigns
     * the user JID derived from the bundled phone number. Used to bootstrap a web session from a
     * previously exported six-parts key blob without a fresh QR pairing flow.
     */
    @Override
    public WhatsAppStore create(WhatsAppClientType clientType, WhatsAppClientSixPartsKeys sixPartsKeys) throws IOException {
        Objects.requireNonNull(clientType, "clientType cannot be null");
        Objects.requireNonNull(sixPartsKeys, "sixPartsKeys cannot be null");
        var phoneNumber = sixPartsKeys.phoneNumber();
        var sessionId = String.valueOf(phoneNumber);
        var sessionDirectory = StorePathUtils.getSessionDirectory(clientType, directory, sessionId);
        var store = new PersistentStoreBuilder()
                .directory(sessionDirectory)
                .uuid(UUID.randomUUID())
                .phoneNumber(phoneNumber)
                .noiseKeyPair(sixPartsKeys.noiseKeyPair())
                .identityKeyPair(sixPartsKeys.identityKeyPair())
                .identityId(sixPartsKeys.identityId())
                .clientType(clientType)
                .device(WhatsAppDevice.web())
                .registered(true)
                .jid(Jid.of(phoneNumber))
                .build();
        attachFreshLmdb(store, clientType, sessionId);
        return store;
    }

    /**
     * Opens a fresh LMDB env for {@code sessionId} and wires it into {@code store}.
     *
     * @apiNote
     * Internal helper shared by every {@code create(...)} overload after the metadata builder
     * produces an otherwise empty store.
     *
     * @param store      the freshly built store
     * @param clientType the client type
     * @param sessionId  the session UUID string or phone-number string
     * @throws IOException if the env directory cannot be created
     */
    private void attachFreshLmdb(PersistentStore store, WhatsAppClientType clientType, String sessionId) throws IOException {
        var envPath = PersistentStore.messagesEnvPath(clientType, directory, sessionId);
        store.attachMessageStore(PersistentMessageStore.open(envPath, mapSize));
    }

    /**
     * Returns the synthetic device descriptor used for newly created sessions of the given
     * client type.
     *
     * @apiNote
     * Internal helper that picks a desktop-shaped {@link WhatsAppDevice} for web sessions and an
     * iOS-shaped descriptor for mobile sessions.
     *
     * @param clientType the client type
     * @return a fresh {@link WhatsAppDevice} suitable for the type
     */
    private static WhatsAppDevice defaultDevice(WhatsAppClientType clientType) {
        return switch (clientType) {
            case WEB -> WhatsAppDevice.desktop();
            case MOBILE -> WhatsAppDevice.ios(false);
        };
    }
}
