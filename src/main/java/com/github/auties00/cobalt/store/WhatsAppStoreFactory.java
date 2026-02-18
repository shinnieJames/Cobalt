package com.github.auties00.cobalt.store;

import com.github.auties00.cobalt.client.WhatsAppClientType;
import com.github.auties00.cobalt.store.proto.InMemoryWhatsAppStore;

import java.io.IOException;
import java.util.UUID;

@FunctionalInterface
public interface WhatsAppStoreFactory {
    static WhatsAppStoreFactory discarding() {
        return InMemoryWhatsAppStore.DISCARDING_FACTORY;
    }


    WhatsAppStore loadOrCreateStore(WhatsAppClientType clientType, UUID uuid) throws IOException;
}
