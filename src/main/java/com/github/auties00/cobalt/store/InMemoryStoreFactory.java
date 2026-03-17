package com.github.auties00.cobalt.store;

import com.github.auties00.cobalt.client.WhatsAppClientType;
import com.github.auties00.cobalt.model.jid.JidDevice;
import com.github.auties00.cobalt.util.StorePathUtils;
import it.auties.protobuf.stream.ProtobufInputStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class InMemoryStoreFactory implements WhatsAppStoreFactory {
    private static final Path DEFAULT_DIRECTORY = Path.of(System.getProperty("user.home"), ".cobalt", "proto");

    private final Path directory;

    InMemoryStoreFactory() {
        this(DEFAULT_DIRECTORY);
    }

    InMemoryStoreFactory(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory cannot be null");
    }

    @Override
    public Optional<WhatsAppStore> load(WhatsAppClientType clientType, UUID uuid) throws IOException {
        Objects.requireNonNull(clientType, "clientType cannot be null");
        Objects.requireNonNull(uuid, "uuid cannot be null");

        var path = StorePathUtils.getSessionFile(clientType, directory, uuid.toString(), "store.proto");
        return load(path);
    }

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

    @Override
    public WhatsAppStore create(WhatsAppClientType clientType, UUID uuid) throws IOException {
        var device = switch (clientType) {
            case WEB -> JidDevice.web();
            case MOBILE -> JidDevice.ios(false);
        };
        return new InMemoryStoreBuilder()
                .uuid(Objects.requireNonNullElseGet(uuid, UUID::randomUUID))
                .clientType(clientType)
                .device(device)
                .build();
    }

    @Override
    public WhatsAppStore create(WhatsAppClientType clientType, long phoneNumber) throws IOException {
        var device = switch (clientType) {
            case WEB -> JidDevice.web();
            case MOBILE -> JidDevice.ios(false);
        };
        return new InMemoryStoreBuilder()
                .uuid(UUID.randomUUID())
                .phoneNumber(phoneNumber)
                .clientType(clientType)
                .device(device)
                .build();
    }
}
