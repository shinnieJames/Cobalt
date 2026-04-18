package com.github.auties00.cobalt.store;

import com.github.auties00.cobalt.client.WhatsAppClientType;
import com.github.auties00.cobalt.client.WhatsAppDevice;
import com.github.auties00.cobalt.util.StorePathUtils;
import it.auties.protobuf.stream.ProtobufInputStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link WhatsAppStoreFactory} implementation that keeps the entire session
 * state in memory and persists it to protobuf files on disk.
 *
 * <p>Each session lives in its own directory named after the session UUID
 * (or phone number) under the configured root directory. The root
 * {@code store.proto} file holds the session-level state; individual chats
 * and newsletters are stored as separate {@code chat_*.proto} and
 * {@code newsletter_*.proto} files so that per-entity serialisation can
 * avoid rewriting the entire store on every change.
 */
final class InMemoryStoreFactory implements WhatsAppStoreFactory {
    /**
     * Default root directory for Cobalt session files:
     * {@code $HOME/.cobalt/proto}.
     */
    private static final Path DEFAULT_DIRECTORY = Path.of(System.getProperty("user.home"), ".cobalt", "proto");

    /**
     * Root directory under which per-session folders are created.
     */
    private final Path directory;

    /**
     * Constructs a new factory using the default storage directory.
     */
    InMemoryStoreFactory() {
        this(DEFAULT_DIRECTORY);
    }

    /**
     * Constructs a new factory using the given root directory.
     *
     * @param directory the root directory under which per-session folders
     *                  are created; must not be {@code null}
     */
    InMemoryStoreFactory(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory cannot be null");
    }

    /**
     * Loads an existing session store identified by UUID.
     *
     * @param clientType the client type (web or mobile) to look up
     * @param uuid       the session UUID
     * @return the loaded store, or {@link Optional#empty()} if no session
     *         file exists for that UUID
     * @throws IOException if the store file cannot be read or decoded
     */
    @Override
    public Optional<WhatsAppStore> load(WhatsAppClientType clientType, UUID uuid) throws IOException {
        Objects.requireNonNull(clientType, "clientType cannot be null");
        Objects.requireNonNull(uuid, "uuid cannot be null");

        var path = StorePathUtils.getSessionFile(clientType, directory, uuid.toString(), "store.proto");
        return load(path);
    }

    /**
     * Loads an existing session store identified by phone number.
     *
     * @param clientType  the client type (web or mobile) to look up
     * @param phoneNumber the phone number associated with the session
     * @return the loaded store, or {@link Optional#empty()} if no session
     *         file exists for that phone number
     * @throws IOException if the store file cannot be read or decoded
     */
    @Override
    public Optional<WhatsAppStore> load(WhatsAppClientType clientType, long phoneNumber) throws IOException {
        Objects.requireNonNull(clientType, "clientType cannot be null");

        var path = StorePathUtils.getSessionFile(clientType, directory, String.valueOf(phoneNumber), "store.proto");
        if (Files.exists(path)) {
            return Optional.empty();
        }

        try (var stream = Files.newInputStream(path)) {
            var result = InMemoryStoreSpec.decode(ProtobufInputStream.fromStream(stream));
            result.startBackgroundDeserialization();
            return Optional.of(result);
        }
    }

    /**
     * Loads the most recently modified session directory for the given
     * client type.
     *
     * @param clientType the client type (web or mobile) to look up
     * @return the most recent store, or {@link Optional#empty()} if no
     *         session directory exists
     * @throws IOException if the store file cannot be read or decoded
     */
    @Override
    public Optional<WhatsAppStore> loadLatest(WhatsAppClientType clientType) throws IOException {
        Objects.requireNonNull(clientType, "clientType cannot be null");

        var latest = StorePathUtils.getLatestSessionDirectory(clientType, directory);
        if (latest.isEmpty()) {
            return Optional.empty();
        } else {
            return load(latest.get());
        }
    }

    /**
     * Decodes a {@link InMemoryStore} from the given protobuf file and
     * kicks off background deserialisation of per-chat and per-newsletter
     * files.
     *
     * @param path the path to the {@code store.proto} file
     * @return the loaded store, or {@link Optional#empty()} if the file
     *         does not exist
     * @throws IOException if the file cannot be read or decoded
     */
    private static Optional<WhatsAppStore> load(Path path) throws IOException {
        if (Files.notExists(path)) {
            return Optional.empty();
        }

        try (var stream = Files.newInputStream(path)) {
            var result = InMemoryStoreSpec.decode(ProtobufInputStream.fromStream(stream));
            result.startBackgroundDeserialization();
            return Optional.of(result);
        }
    }

    /**
     * Creates a new, empty session identified by UUID.
     *
     * <p>The default {@link WhatsAppDevice} is chosen based on the client
     * type: a synthetic web device for {@link WhatsAppClientType#WEB}, or
     * a non-business iOS device for {@link WhatsAppClientType#MOBILE}.
     *
     * @param clientType the client type (web or mobile) for the new
     *                   session
     * @param uuid       the UUID to assign, or {@code null} to generate a
     *                   random one
     * @return the newly created store
     * @throws IOException if the store directory cannot be created
     */
    @Override
    public WhatsAppStore create(WhatsAppClientType clientType, UUID uuid) throws IOException {
        var device = switch (clientType) {
            case WEB -> WhatsAppDevice.web();
            case MOBILE -> WhatsAppDevice.ios(false);
        };
        var directory = StorePathUtils.getSessionDirectory(clientType, this.directory, uuid.toString());
        return new InMemoryStoreBuilder()
                .uuid(Objects.requireNonNullElseGet(uuid, UUID::randomUUID))
                .clientType(clientType)
                .device(device)
                .directory(directory)
                .build();
    }

    /**
     * Creates a new, empty session identified by phone number. A random
     * UUID is always generated for the session.
     *
     * @param clientType  the client type (web or mobile) for the new
     *                    session
     * @param phoneNumber the phone number to associate with the session
     * @return the newly created store
     * @throws IOException if the store directory cannot be created
     */
    @Override
    public WhatsAppStore create(WhatsAppClientType clientType, long phoneNumber) throws IOException {
        var device = switch (clientType) {
            case WEB -> WhatsAppDevice.web();
            case MOBILE -> WhatsAppDevice.ios(false);
        };
        var directory = StorePathUtils.getSessionDirectory(clientType, this.directory, String.valueOf(phoneNumber));
        return new InMemoryStoreBuilder()
                .directory(directory)
                .uuid(UUID.randomUUID())
                .phoneNumber(phoneNumber)
                .clientType(clientType)
                .device(device)
                .build();
    }
}
