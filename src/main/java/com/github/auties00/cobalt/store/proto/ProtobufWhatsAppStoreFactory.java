package com.github.auties00.cobalt.store.proto;

import com.github.auties00.cobalt.client.WhatsAppClientType;
import com.github.auties00.cobalt.model.jid.JidDevice;
import com.github.auties00.cobalt.store.InMemoryWhatsAppStoreBuilder;
import com.github.auties00.cobalt.store.InMemoryWhatsAppStoreSpec;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.store.WhatsAppStoreFactory;
import it.auties.protobuf.stream.ProtobufInputStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

public final class ProtobufWhatsAppStoreFactory implements WhatsAppStoreFactory {
    private final Path directory;

    public ProtobufWhatsAppStoreFactory(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory cannot be null");
    }

    @Override
    public WhatsAppStore loadOrCreateStore(WhatsAppClientType clientType, UUID uuid) throws IOException {
        Objects.requireNonNull(clientType, "clientType cannot be null");
        Objects.requireNonNull(uuid, "uuid cannot be null");

        var path = ProtobufWhatsAppStorePathUtils.getSessionFile(clientType, directory, uuid.toString(), "store.proto");
        if (Files.exists(path)) {
            try (var stream = Files.newInputStream(path)) {
                var result = InMemoryWhatsAppStoreSpec.decode(ProtobufInputStream.fromStream(stream));
                result.directory = dir;
                result.serializable = true;
                result.storesHashCodes.put(result.uuid(), result.hashCode());
                result.startBackgroundDeserialization();
                return result;
            }
        } else {
            var device = switch (clientType) {
                case WEB -> JidDevice.web();
                case MOBILE -> JidDevice.ios(false);
            };
            var result = new InMemoryWhatsAppStoreBuilder()
                    .uuid(UUID.randomUUID())
                    .clientType(clientType)
                    .device(device)
                    .build();
            result.directory = directory;
            result.serializable = true;
            return result;
        }
    }
}
