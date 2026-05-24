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
 * The {@link WhatsAppStoreFactory} that produces {@link TemporaryStore} sessions held entirely
 * in RAM.
 *
 * @apiNote
 * Cobalt embedders obtain the singleton through {@link WhatsAppStoreFactory#temporary()};
 * suitable for tests, ephemeral bots, scratch programs, and any scenario where session state
 * should not survive a JVM restart.
 *
 * @implNote
 * This implementation is stateless and exposes a singleton instance because nothing inside the
 * factory varies per session. The three {@code load} entries always return empty since there is
 * no on-disk surface to consult; the three {@code create} entries delegate to
 * {@link #newStore(WhatsAppClientType, UUID, Long, SignalIdentityKeyPair, SignalIdentityKeyPair, byte[], Jid)}
 * which builds a {@link TemporaryStore} with empty collections seeded by the supplied identity
 * scalars.
 */
final class TemporaryStoreFactory implements WhatsAppStoreFactory {
    /**
     * The singleton instance.
     *
     * @apiNote
     * Obtained through {@link WhatsAppStoreFactory#temporary()}; the factory is stateless so
     * sharing one instance is safe across threads and sessions.
     */
    static final TemporaryStoreFactory INSTANCE = new TemporaryStoreFactory();

    /**
     * Constructs the singleton.
     *
     * @apiNote
     * Private; the only legitimate instance is {@link #INSTANCE}.
     */
    private TemporaryStoreFactory() {

    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always returns {@link Optional#empty()} because transient sessions
     * have no on-disk presence to load.
     */
    @Override
    public Optional<WhatsAppStore> load(WhatsAppClientType clientType, UUID uuid) {
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always returns {@link Optional#empty()} because transient sessions
     * have no on-disk presence to load.
     */
    @Override
    public Optional<WhatsAppStore> load(WhatsAppClientType clientType, long phoneNumber) {
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always returns {@link Optional#empty()} because transient sessions
     * have no on-disk presence to enumerate.
     */
    @Override
    public Optional<WhatsAppStore> loadLatest(WhatsAppClientType clientType) {
        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation generates a random UUID when {@code uuid} is {@code null} and forwards
     * to {@link #newStore} with the identity-bearing scalars all left {@code null}.
     */
    @Override
    public WhatsAppStore create(WhatsAppClientType clientType, UUID uuid) {
        Objects.requireNonNull(clientType, "clientType cannot be null");
        return newStore(clientType, Objects.requireNonNullElseGet(uuid, UUID::randomUUID), null, null, null, null, null);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation assigns a fresh random UUID and forwards to {@link #newStore} with the
     * identity-bearing scalars left {@code null}; the phone number alone is retained.
     */
    @Override
    public WhatsAppStore create(WhatsAppClientType clientType, long phoneNumber) {
        Objects.requireNonNull(clientType, "clientType cannot be null");
        return newStore(clientType, UUID.randomUUID(), phoneNumber, null, null, null, null);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation seeds the store with the noise and identity key pairs, identity id and
     * derived user JID from {@code sixPartsKeys} so a previously exported six-parts key blob can
     * be used to bootstrap a transient session without a fresh pairing flow.
     */
    @Override
    public WhatsAppStore create(WhatsAppClientType clientType, WhatsAppClientSixPartsKeys sixPartsKeys) {
        Objects.requireNonNull(clientType, "clientType cannot be null");
        Objects.requireNonNull(sixPartsKeys, "sixPartsKeys cannot be null");
        var phoneNumber = sixPartsKeys.phoneNumber();
        return newStore(clientType, UUID.randomUUID(), phoneNumber, sixPartsKeys.noiseKeyPair(), sixPartsKeys.identityKeyPair(), sixPartsKeys.identityId(), Jid.of(phoneNumber));
    }

    /**
     * Builds a fresh {@link TemporaryStore} with empty collections plus the supplied identity
     * scalars.
     *
     * @apiNote
     * Internal helper shared by every {@code create(...)} overload; centralises the
     * client-type-to-device mapping and the empty-collection initialisation so the three public
     * entry points read uniformly.
     *
     * @implNote
     * This implementation pre-allocates every collection with the {@code ConcurrentHashMap} or
     * {@code LinkedHashMap} variant required by the {@link AbstractWhatsAppStore} contract; the
     * session directory is intentionally {@code null} because the transient variant has no disk
     * surface.
     *
     * @param clientType      the client type
     * @param uuid            the session UUID
     * @param phoneNumber     the session phone number, or {@code null}
     * @param noiseKeyPair    the Noise XX static key pair, or {@code null}
     * @param identityKeyPair the Signal identity key pair, or {@code null}
     * @param identityId      the identity-id bytes, or {@code null}
     * @param jid             the user JID, or {@code null}
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
                false, false, null, null, false,
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
                new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
                null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null
        );
    }
}
