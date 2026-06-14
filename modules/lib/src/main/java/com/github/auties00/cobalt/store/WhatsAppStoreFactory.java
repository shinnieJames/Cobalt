package com.github.auties00.cobalt.store;

import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientSixPartsKeys;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientType;
import com.github.auties00.cobalt.store.persistent.PersistentStoreFactory;
import com.github.auties00.cobalt.store.temporary.TemporaryStoreFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/**
 * The factory contract for constructing and loading {@link LinkedWhatsAppStore} instances.
 *
 * @apiNote
 * Embedders pick a storage strategy through one of the static factory methods on this interface
 * and use the returned {@link WhatsAppStoreFactory} to either {@link #create create} fresh
 * sessions or {@link #load load} existing ones. The supplied implementations cover the
 * RAM-only and on-disk-snapshot-plus-LMDB variants; embedders should not depend on the concrete
 * factory types directly.
 *
 * @implSpec
 * Implementations must produce stores that conform to the {@link LinkedWhatsAppStore} contract. The
 * {@code load} methods must return {@link Optional#empty()} when no session matches the
 * argument; the {@code create} methods must always return a fresh, non-{@code null} store ready
 * to be used by the rest of the client.
 */
public interface WhatsAppStoreFactory {
    /**
     * Returns a factory that keeps the entire store in RAM and never touches disk.
     *
     * @apiNote
     * Suitable for tests, ephemeral bots and scratch programs. Restarting the program loses
     * every chat, newsletter, message and key.
     *
     * @return a stateless RAM-only factory
     */
    static WhatsAppStoreFactory temporary() {
        return TemporaryStoreFactory.INSTANCE;
    }

    /**
     * Returns a factory that snapshots session metadata to a {@code store.proto} file and stores
     * message bodies in an embedded LMDB env under the default Cobalt session directory.
     *
     * @apiNote
     * The default directory is {@code $HOME/.cobalt/proto}; embedders that need a custom
     * location pick {@link #persistent(Path)} or {@link #persistent(Path, long)} instead.
     *
     * @return a persistent factory using the default storage directory
     */
    static WhatsAppStoreFactory persistent() {
        return new PersistentStoreFactory();
    }

    /**
     * Returns a factory that snapshots session metadata to a {@code store.proto} file and stores
     * message bodies in an embedded LMDB env under the given directory.
     *
     * @apiNote
     * Each session lives under {@code <directory>/<clientType>/<sessionId>/} so multiple sessions
     * under the same root never collide.
     *
     * @param directory the root directory under which per-session folders are created
     * @return a persistent factory using the given storage directory
     */
    static WhatsAppStoreFactory persistent(Path directory) {
        return new PersistentStoreFactory(directory);
    }

    /**
     * Returns a factory that snapshots session metadata to a {@code store.proto} file and stores
     * message bodies in an embedded LMDB env under the given directory, configured with the
     * given initial map size.
     *
     * @apiNote
     * The {@code mapSize} is the maximum LMDB env file size in bytes; the env is automatically
     * doubled on overflow so this value functions as a starting point rather than a hard cap.
     * On Windows the file is preallocated as sparse so very large defaults can look alarming in
     * Explorer.
     *
     * @param directory the root directory under which per-session folders are created
     * @param mapSize   the initial LMDB map size in bytes; must be positive
     * @return a persistent factory using the given storage directory and initial map size
     */
    static WhatsAppStoreFactory persistent(Path directory, long mapSize) {
        return new PersistentStoreFactory(directory, mapSize);
    }

    /**
     * Loads an existing session store identified by its UUID.
     *
     * @apiNote
     * Returns {@link Optional#empty()} when no session matches. The persistent variant runs the
     * orphan-recovery pass during loading so chats and newsletters whose bodies landed in LMDB
     * after the last metadata snapshot still surface on the returned store.
     *
     * @implSpec
     * Implementations must not return {@code null}; missing sessions are signalled with
     * {@link Optional#empty()}. Implementations may perform IO and propagate
     * {@link IOException} when the on-disk state is malformed or unreadable.
     *
     * @param clientType the client type (web or mobile) to look up
     * @param uuid       the session UUID previously assigned at creation time
     * @return the loaded store, or {@link Optional#empty()} if no session exists for that UUID
     * @throws IOException if the store file cannot be read or decoded
     */
    Optional<LinkedWhatsAppStore> load(LinkedWhatsAppClientType clientType, UUID uuid) throws IOException;

    /**
     * Loads an existing session store identified by its phone number.
     *
     * @apiNote
     * Returns {@link Optional#empty()} when no session matches.
     *
     * @implSpec
     * Implementations must not return {@code null}; missing sessions are signalled with
     * {@link Optional#empty()}. Implementations may perform IO and propagate
     * {@link IOException} when the on-disk state is malformed or unreadable.
     *
     * @param clientType  the client type (web or mobile) to look up
     * @param phoneNumber the phone number associated with the session
     * @return the loaded store, or {@link Optional#empty()} if no session exists for that phone
     *         number
     * @throws IOException if the store file cannot be read or decoded
     */
    Optional<LinkedWhatsAppStore> load(LinkedWhatsAppClientType clientType, long phoneNumber) throws IOException;

    /**
     * Loads the most recently persisted session for the given client type.
     *
     * @apiNote
     * Useful for resume flows where the embedder does not track the session id externally.
     * Returns {@link Optional#empty()} when no session directory exists.
     *
     * @implSpec
     * Implementations must not return {@code null}; missing sessions are signalled with
     * {@link Optional#empty()}. Implementations may perform IO and propagate
     * {@link IOException} when the on-disk state is malformed or unreadable.
     *
     * @param clientType the client type (web or mobile) to look up
     * @return the most recent store, or {@link Optional#empty()} if no session directory exists
     * @throws IOException if the store file cannot be read or decoded
     */
    Optional<LinkedWhatsAppStore> loadLatest(LinkedWhatsAppClientType clientType) throws IOException;

    /**
     * Creates a new, empty session store identified by the given UUID.
     *
     * @apiNote
     * Passing {@code null} for {@code uuid} requests a fresh random UUID.
     *
     * @implSpec
     * Implementations must return a freshly built, non-{@code null} store ready for use by the
     * rest of the client; persistent implementations must additionally open the message-body
     * backing store so subsequent message accessors operate immediately.
     *
     * @param clientType the client type (web or mobile) for the new session
     * @param uuid       the UUID to assign to the session, or {@code null} to generate a random one
     * @return the newly created store
     * @throws IOException if the store directory cannot be created
     */
    LinkedWhatsAppStore create(LinkedWhatsAppClientType clientType, UUID uuid) throws IOException;

    /**
     * Creates a new, empty session store identified by the given phone number.
     *
     * @apiNote
     * The session UUID is generated randomly and the phone number is retained as a separate
     * scalar; the session directory is keyed by the stringified phone number for the persistent
     * variant.
     *
     * @implSpec
     * Implementations must return a freshly built, non-{@code null} store; persistent
     * implementations must open the message-body backing store before returning.
     *
     * @param clientType  the client type (web or mobile) for the new session
     * @param phoneNumber the phone number to associate with the session
     * @return the newly created store
     * @throws IOException if the store directory cannot be created
     */
    LinkedWhatsAppStore create(LinkedWhatsAppClientType clientType, long phoneNumber) throws IOException;

    /**
     * Creates a new session store using the data in {@code sixPartsKeys}.
     *
     * @apiNote
     * Bootstraps a session from a previously exported six-parts key blob so the new store has
     * the noise key pair, the Signal identity key pair, the identity id, and the derived user
     * JID already populated; for web sessions the device descriptor is forced to
     * {@code WhatsAppDevice.web()} and {@code registered=true} so the pairing pipeline is
     * skipped.
     *
     * @implSpec
     * Implementations must return a freshly built, non-{@code null} store seeded from the
     * supplied keys; persistent implementations must open the message-body backing store before
     * returning.
     *
     * @param clientType   the client type (web or mobile) for the new session
     * @param sixPartsKeys the six-parts key blob
     * @return the newly created store
     * @throws IOException if the store directory cannot be created
     */
    LinkedWhatsAppStore create(LinkedWhatsAppClientType clientType, LinkedWhatsAppClientSixPartsKeys sixPartsKeys) throws IOException;
}
