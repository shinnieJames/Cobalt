package com.github.auties00.cobalt.store;

import com.github.auties00.cobalt.client.WhatsAppClientSixPartsKeys;
import com.github.auties00.cobalt.client.WhatsAppClientType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

public interface WhatsAppStoreFactory {
    static WhatsAppStoreFactory inMemory() {
        return new InMemoryStoreFactory();
    }

    static WhatsAppStoreFactory inMemory(Path directory) {
        return new InMemoryStoreFactory(directory);
    }

    static WhatsAppStoreFactory persistent() {
        // FIXME
        throw new UnsupportedOperationException();
    }

    static WhatsAppStoreFactory persistent(Path directory) {
        // FIXME
        throw new UnsupportedOperationException();
    }

    Optional<WhatsAppStore> load(WhatsAppClientType clientType, UUID uuid) throws IOException;

    Optional<WhatsAppStore> load(WhatsAppClientType clientType, long phoneNumber) throws IOException;

    default Optional<WhatsAppStore> load(WhatsAppClientType clientType, WhatsAppClientSixPartsKeys keys) throws IOException {
        return load(clientType, keys.phoneNumber());
    }

    Optional<WhatsAppStore> loadLatest(WhatsAppClientType clientType) throws IOException;

    WhatsAppStore create(WhatsAppClientType clientType, UUID uuid) throws IOException;

    WhatsAppStore create(WhatsAppClientType clientType, long phoneNumber) throws IOException;
}
