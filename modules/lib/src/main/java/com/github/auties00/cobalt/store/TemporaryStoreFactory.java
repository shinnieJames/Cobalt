package com.github.auties00.cobalt.store;

import com.github.auties00.cobalt.client.WhatsAppClientSixPartsKeys;
import com.github.auties00.cobalt.client.WhatsAppClientType;
import com.github.auties00.cobalt.client.WhatsAppDevice;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.libsignal.key.SignalIdentityKeyPair;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory implementation that produces RAM-only {@link TemporaryStore}
 * sessions.
 *
 * <p>Has no on-disk presence: {@link #load} and {@link #loadLatest}
 * always return {@link Optional#empty()} since there is nothing to load,
 * and every {@link #create} overload returns a freshly built store with
 * empty maps. Suitable for tests, ephemeral bots, scratch programs and
 * any scenario where session state should not survive a JVM restart.
 */
final class TemporaryStoreFactory implements WhatsAppStoreFactory {
    /**
     * The singleton instance
     */
    static final TemporaryStoreFactory INSTANCE = new TemporaryStoreFactory();

    private TemporaryStoreFactory() {

    }

    /**
     * Always returns {@link Optional#empty()}: transient sessions are
     * never persisted.
     */
    @Override
    public Optional<WhatsAppStore> load(WhatsAppClientType clientType, UUID uuid) {
        return Optional.empty();
    }

    /**
     * Always returns {@link Optional#empty()}: transient sessions are
     * never persisted.
     */
    @Override
    public Optional<WhatsAppStore> load(WhatsAppClientType clientType, long phoneNumber) {
        return Optional.empty();
    }

    /**
     * Always returns {@link Optional#empty()}: transient sessions are
     * never persisted.
     */
    @Override
    public Optional<WhatsAppStore> loadLatest(WhatsAppClientType clientType) {
        return Optional.empty();
    }

    @Override
    public WhatsAppStore create(WhatsAppClientType clientType, UUID uuid) {
        Objects.requireNonNull(clientType, "clientType cannot be null");
        return newStore(clientType, Objects.requireNonNullElseGet(uuid, UUID::randomUUID), null, null, null, null, null);
    }

    @Override
    public WhatsAppStore create(WhatsAppClientType clientType, long phoneNumber) {
        Objects.requireNonNull(clientType, "clientType cannot be null");
        return newStore(clientType, UUID.randomUUID(), phoneNumber, null, null, null, null);
    }

    @Override
    public WhatsAppStore create(WhatsAppClientType clientType, WhatsAppClientSixPartsKeys sixPartsKeys) {
        Objects.requireNonNull(clientType, "clientType cannot be null");
        Objects.requireNonNull(sixPartsKeys, "sixPartsKeys cannot be null");
        var phoneNumber = sixPartsKeys.phoneNumber();
        return newStore(clientType, UUID.randomUUID(), phoneNumber, sixPartsKeys.noiseKeyPair(), sixPartsKeys.identityKeyPair(), sixPartsKeys.identityId(), Jid.of(phoneNumber));
    }

    /**
     * Builds a fresh {@link TemporaryStore} with empty contact, call,
     * privacy, sticker and Signal-protocol collections, plus the
     * supplied identity scalars.
     *
     * @param clientType    the client type
     * @param uuid          the session UUID
     * @param phoneNumber   the session phone number, or {@code null}
     * @param noiseKeyPair  the Noise XX static key pair, or {@code null}
     * @param identityKeyPair the Signal identity key pair, or {@code null}
     * @param identityId    the identity-id bytes, or {@code null}
     * @param jid           the user JID, or {@code null}
     * @return a freshly built store
     */
    private static WhatsAppStore newStore(WhatsAppClientType clientType, UUID uuid, Long phoneNumber, SignalIdentityKeyPair noiseKeyPair, SignalIdentityKeyPair identityKeyPair, byte[] identityId, Jid jid) {
        var device = switch (clientType) {
            case WEB -> WhatsAppDevice.desktop();
            case MOBILE -> WhatsAppDevice.ios(false);
        };
        return new TemporaryStore(
                uuid, phoneNumber, clientType, Instant.now(), device, null,
                false, null, null, null, null, null, jid, null, null, null, null, null, null, null, null,
                new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
                false, false, null, null, false, false, false,
                false, false, false, false, false,
                null, noiseKeyPair, identityKeyPair, null, null, new LinkedHashMap<>(),
                null, null, null, identityId, null,
                new ConcurrentHashMap<>(), new LinkedHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
                false, false,
                new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
                null, null, null,
                new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
                null,
                new ConcurrentHashMap<>(),
                (Path) null,
                false, false, false, false,
                null, List.of(), List.of(),
                new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
                0L, null, null, null, 0L, null, 0L, 0L, 0L,
                new ConcurrentHashMap<>(), new ConcurrentHashMap<>()
        );
    }
}
