package com.github.auties00.cobalt.store;

import com.github.auties00.cobalt.client.WhatsAppClientSixPartsKeys;
import com.github.auties00.cobalt.client.WhatsAppClientType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

public interface WhatsAppStoreFactory {
    static WhatsAppStoreFactory discarding() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    static WhatsAppStoreFactory toProtobuf() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    static WhatsAppStoreFactory toProtobuf(Path path) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    static WhatsAppStoreFactory toDatabase(String connectionUrl) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    static WhatsAppStoreFactory toMixed(String connectionUrl) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    static WhatsAppStoreFactory toMixed(String connectionUrl, Path path) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    Optional<WhatsAppStore> load(WhatsAppClientType clientType, UUID uuid);
    Optional<WhatsAppStore> load(WhatsAppClientType clientType, long phoneNumber);
    Optional<WhatsAppStore> load(WhatsAppClientType clientType, WhatsAppClientSixPartsKeys keys);
    Optional<WhatsAppStore> loadLatest(WhatsAppClientType clientType);

    WhatsAppStore create(WhatsAppClientType clientType, UUID uuid) throws IOException;
    WhatsAppStore create(WhatsAppClientType clientType, long phoneNumber) throws IOException;
}
