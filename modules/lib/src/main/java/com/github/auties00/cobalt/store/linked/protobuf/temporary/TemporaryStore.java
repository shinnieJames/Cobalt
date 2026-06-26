package com.github.auties00.cobalt.store.linked.protobuf.temporary;

import com.github.auties00.cobalt.store.linked.protobuf.*;
import com.github.auties00.cobalt.store.linked.LinkedWhatsAppStoreFactory;

import java.nio.file.Path;

/**
 * The {@link ProtobufWhatsAppStore} that holds the entire session state in RAM and never touches disk.
 *
 * @apiNote
 * Cobalt embedders obtain instances through {@link LinkedWhatsAppStoreFactory#temporary()}. Useful for
 * short-lived sessions, one-shot bots, integration tests and scratch programs that do not need their
 * state to survive a JVM restart.
 *
 * @implNote
 * This implementation is not a protobuf message; its {@link #save()}, {@link #await()} and
 * {@link #delete()} are no-ops because there is no disk surface, and the in-memory chat sub-store is
 * discarded with the store instance.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
final class TemporaryStore extends ProtobufWhatsAppStore {
    /**
     * The in-memory chat sub-store.
     */
    private final TemporaryLinkedWhatsAppChatStore chatStore;

    /**
     * Constructs an in-memory store from its composed sub-stores.
     *
     * @param signalStore           the signal sub-store
     * @param accountStore          the account sub-store
     * @param contactStore          the contact sub-store
     * @param syncStore             the sync sub-store
     * @param settingsStore         the settings sub-store
     * @param directory             the session directory path; unused for the transient variant
     * @param webSessionStore       the web-GraphQL credential sub-store, or {@code null} for an empty one
     * @param wamStore              the WAM telemetry sub-store, or {@code null} for an empty one
     * @param chatStore             the in-memory chat sub-store
     */
    TemporaryStore(ProtobufLinkedWhatsAppSignalStore signalStore, ProtobufLinkedWhatsAppAccountStore accountStore, ProtobufLinkedWhatsAppContactStore contactStore, ProtobufLinkedWhatsAppSyncStore syncStore, ProtobufLinkedWhatsAppSettingsStore settingsStore, Path directory, ProtobufLinkedWebSessionStore webSessionStore, ProtobufLinkedWhatsAppWamStore wamStore, TemporaryLinkedWhatsAppChatStore chatStore) {
        super(signalStore, accountStore, contactStore, syncStore, settingsStore, directory, webSessionStore, wamStore);
        this.chatStore = chatStore;
        this.chatStore.bindContacts(contactStore());
    }

    @Override
    public TemporaryLinkedWhatsAppChatStore chatStore() {
        return chatStore;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation is a no-op.
     */
    @Override
    public void save() {

    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation is a no-op.
     */
    @Override
    public void await() {

    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation is a no-op; the in-memory state is discarded with the store.
     */
    @Override
    public void delete() {

    }
}
