package com.github.auties00.cobalt.calls2.core.participant;

import com.github.auties00.cobalt.calls2.common.CallDeviceJid;
import com.github.auties00.cobalt.calls2.common.VideoDecoderCapability;
import com.github.auties00.cobalt.calls2.media.sframe.SFrameKeyProvider;
import com.github.auties00.cobalt.calls2.signaling.GroupInfoStanza;
import com.github.auties00.cobalt.exception.WhatsAppCallException;
import com.github.auties00.cobalt.model.jid.Jid;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Owns the participant set of a single call and reconciles it against the group-info roster.
 *
 * <p>This is the call engine's membership manager: it allocates a slot per member, finds a member by its
 * user JID or by any of its device JIDs, removes a member on leave, and reconciles the whole set against
 * a fresh {@code <group_info>} roster, all behind one per-call lock. It is the sole owner of the
 * membership slots, mirroring the engine rule that a participant is never reached except through this
 * manager and never leaked by reference; the rest of the engine reads members through the immutable
 * snapshots this manager hands out, and through the {@link ParticipantProvider} this manager exposes over
 * {@link #participantProvider()}.
 *
 * <p>Each slot binds a member's user JID to its parsed roster identity, a {@link CallParticipantUserNode}
 * carrying the member's devices, capabilities, account kind, user type, and server-projected state, and to
 * the heavy {@link CallParticipant} aggregate the engine keys media and crypto off. The identity is the
 * transient wire DTO that feeds the aggregate: on allocation and on every reconcile or identity refresh the
 * manager upserts the slot's {@link CallParticipant} from the fresh identity (mirroring the user JID, the
 * phone-number and LID forms, the names, the {@linkplain CallParticipantAccountKind account kind}, the
 * {@linkplain CallParticipantUserType user type}, the {@linkplain CallParticipantPlatform platform}, the
 * participant id, the device list, and the membership state), so the slot's aggregate stays in step with the
 * roster while the DTO carries only the latest wire shape. The
 * set is bounded at {@value #MAX_PARTICIPANTS} members, the engine's hard slot-array ceiling; an
 * allocation that would exceed it is refused rather than silently dropped. A one-to-one call takes the
 * fast path: a single peer slot allocated once and never diffed. A group call takes the full diff: every
 * reconcile against a roster adds members new to the roster, refreshes the identity of members already
 * present, and marks-removed members absent from the roster.
 *
 * <p>The reconcile reports its effect as a {@link Reconciliation} of the members added, updated, and
 * removed, so the caller can drive the downstream media, transport, and per-participant key effects the
 * engine attaches to each membership change (creating a participant's media and deriving its keys on
 * add, tearing them down on remove). This manager owns only the identity-and-state lifecycle; it does
 * not itself create media streams or derive keys.
 *
 * @apiNote This is an internal engine collaborator, not a public surface; embedders never call it. The
 * call engine constructs one manager per call and reconciles it from each inbound {@code <group_info>}
 * for the lifetime of the call.
 * @implNote This implementation reproduces the membership lifecycle of {@code call_membership.cc} in the
 * wa-voip WASM module {@code ff-tScznZ8P}: slot allocation ({@code wa_call_group_allocate_participant_at_index},
 * fn10828, bounded to index {@code 0x7f}), lookup by user JID
 * ({@code wa_call_group_get_participant}/{@code wa_call_group_get_participant_by_jid}, fn10825/fn10827),
 * lookup by active device JID with a relaxed same-account fallback
 * ({@code wa_call_group_get_participant_by_device_jid}, fn10836), the reconcile diff
 * ({@code call_update_participants}, fn10834, with the one-to-one fast path of fn10832 versus the group
 * full diff), the mark-removed on a member absent from the roster
 * ({@code voip_signaling_call_remove_participant}, fn10823, which sets the member's device state to
 * {@code kParticipantStateInvalid}), and slot teardown ({@code wa_call_group_destroy_participant},
 * fn10821). The native per-call {@code stream_mutex} that serializes every one of these mutations is
 * modelled by {@link #lock}, a {@link ReentrantLock} held for the duration of each operation, per the
 * Cobalt locking model. The native heavy {@code call_participant} aggregate (the
 * {@code 0x55108}-byte struct with media streams, SSRC sets, and the per-participant crypto block) is
 * composed in each slot here ({@link CallMembershipSlot#participant()}) and upserted from the slot's
 * {@link CallParticipantUserNode} on every allocate and reconcile, mirroring the native rule that each
 * membership slot's aggregate IS the {@code call_participant} the engine keys media and crypto off. The
 * read seam over those aggregates is {@link #participantProvider()}, which snapshots each slot's
 * {@link CallParticipant#toView()} under {@link #lock} ({@code participant_provider.cc}); each slot's
 * {@link CallParticipant} carries the membership-derived identity and state, the per-device and per-stream
 * media-plane SSRC sets (derived from the {@linkplain #callId call-id} the manager is constructed with), and
 * the per-participant crypto block, derived from the raw call key the caller installs through
 * {@link #installCallKey(byte[], int)} (before the key is installed the crypto block stays empty).
 */
public final class CallMembership {
    /**
     * Logs membership allocations, removals, and reconcile diffs, mirroring the engine manager's
     * structured logging.
     */
    private static final System.Logger LOGGER = System.getLogger(CallMembership.class.getName());

    /**
     * The maximum number of members a call's slot array may hold.
     *
     * <p>This is the engine's hard slot-array ceiling: a slot is allocated at an index strictly below
     * this value, so a call holds at most this many members regardless of the server-negotiated
     * connected limit.
     */
    private static final int MAX_PARTICIPANTS = 0x7f;

    /**
     * Serializes every membership mutation and lookup for this call.
     *
     * <p>Held for the duration of each public operation so allocation, lookup, removal, and reconcile
     * never interleave; this is the manager's analogue of the engine's per-call stream mutex.
     */
    private final ReentrantLock lock;

    /**
     * The wire call-id this membership belongs to, the per-device SSRC keying input.
     *
     * <p>The native group-diff worker reads the call-id from the embedded {@code call_context} (offset
     * {@code +0x270}) to key {@link CallSecureSsrcGenerator}; Cobalt splits the membership out of the call
     * context, so the call-id is threaded in at construction instead. It is the shared on-the-wire call
     * identifier from the offer, never the secret call key. Fixed for the manager's lifetime.
     */
    private final String callId;

    /**
     * Binds each member's user JID to its slot, in insertion order.
     *
     * <p>Keyed by the {@linkplain Jid#toUserJid() account form} of the member's user JID so a device JID
     * and a user JID for the same account resolve to one slot. Insertion order is preserved so a
     * reconcile and a roster re-emit list members in a stable order. Guarded by {@link #lock}.
     */
    private final SequencedMap<Jid, CallMembershipSlot> slots;

    /**
     * The account-form user JID of the local (self) participant, or {@code null} until it is set.
     *
     * <p>Read by {@link #participantProvider()} to resolve {@link ParticipantProvider#selfView()}: the
     * provider returns the view of the slot keyed by this JID, or {@link ParticipantView#invalid()} when no
     * self JID is set or no slot matches. Stored in the {@linkplain Jid#toUserJid() account form} so a self
     * device JID resolves to the self slot. Guarded by {@link #lock}.
     */
    private Jid selfUserJid;

    /**
     * The 32-byte raw end-to-end call key, or {@code null} until the caller installs it.
     *
     * <p>This is the shared secret minted by the caller or decrypted by the callee from the offer's
     * Signal-encrypted {@code <enc>} envelope, and re-installed on a group rekey through
     * {@link #installCallKey(byte[], int)}. Until it is present every participant's crypto block stays
     * empty, since the per-participant SFrame and SRTP keys derive from it; once present, the upsert
     * derives each device's keys. It is never the wire {@linkplain #callId call-id}. Guarded by
     * {@link #lock}.
     */
    private byte[] rawCallKey;

    /**
     * The key-generation version recorded with {@link #rawCallKey}.
     *
     * <p>Only {@link CallParticipantCrypto#SUPPORTED_KEYGEN_VER} is accepted by the derivation; the
     * version is carried through the upsert into each participant's crypto block. Guarded by
     * {@link #lock}.
     */
    private int keygenVersion;

    /**
     * Constructs an empty membership manager for one call, keyed by its wire call-id.
     *
     * <p>The slot set starts empty; members are added through {@link #allocate(CallParticipantUserNode)}
     * or {@link #reconcile(GroupInfoStanza)}. No self participant is set until
     * {@link #selfUserJid(Jid)} records one. The call-id is the per-device SSRC keying input the upsert
     * threads into {@link CallSecureSsrcGenerator} as each device is mirrored onto its participant aggregate.
     *
     * @param callId the wire call-id from the offer (the shared on-the-wire identifier, not the call key)
     * @throws NullPointerException if {@code callId} is {@code null}
     */
    public CallMembership(String callId) {
        this.callId = Objects.requireNonNull(callId, "callId cannot be null");
        this.lock = new ReentrantLock();
        this.slots = new LinkedHashMap<>();
        this.selfUserJid = null;
        this.rawCallKey = null;
        this.keygenVersion = 0;
    }

    /**
     * Allocates a slot for the given roster identity, or refreshes the existing slot for that member.
     *
     * <p>The member is keyed by the {@linkplain Jid#toUserJid() account form} of its
     * {@linkplain CallParticipantUserNode#jid() user JID}. When no slot exists for that account a new one
     * is allocated, provided the set is below the {@value #MAX_PARTICIPANTS}-member ceiling; when a slot
     * already exists its identity is replaced with the supplied one, matching the engine's allocate path
     * that fills an existing slot rather than duplicating it. An allocation that would exceed the ceiling
     * is refused and reported as an empty result.
     *
     * @param identity the member's parsed roster identity
     * @return an {@link Optional} holding the member's slot, or empty when the set is full and no slot
     *         exists for the member
     * @throws NullPointerException if {@code identity} is {@code null}
     */
    public Optional<CallMembershipSlot> allocate(CallParticipantUserNode identity) {
        Objects.requireNonNull(identity, "identity cannot be null");
        var key = identity.jid().toUserJid();
        lock.lock();
        try {
            var existing = slots.get(key);
            if (existing != null) {
                existing.identity(identity);
                deriveSlotCrypto(existing.participant());
                return Optional.of(existing);
            }
            if (slots.size() >= MAX_PARTICIPANTS) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Cannot allocate call participant: slot array is full ({0})", MAX_PARTICIPANTS);
                return Optional.empty();
            }
            var slot = new CallMembershipSlot(callId, key, identity, new CallParticipant(key, isExtension(identity)));
            slots.put(key, slot);
            deriveSlotCrypto(slot.participant());
            return Optional.of(slot);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the slot for the member with the given user JID, if present.
     *
     * <p>The lookup keys on the {@linkplain Jid#toUserJid() account form} of {@code userJid}, so a
     * device-bearing JID for the same account resolves to the member's slot.
     *
     * @param userJid the member's user JID
     * @return an {@link Optional} holding the matching slot, or empty when no member matches
     * @throws NullPointerException if {@code userJid} is {@code null}
     */
    public Optional<CallMembershipSlot> find(Jid userJid) {
        Objects.requireNonNull(userJid, "userJid cannot be null");
        var key = userJid.toUserJid();
        lock.lock();
        try {
            return Optional.ofNullable(slots.get(key));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the slot for the member owning the given device JID, if present.
     *
     * <p>A member is matched first by an exact device JID listed under its identity, then, failing that,
     * by the device JID's {@linkplain Jid#isSameAccount(Jid) account} matching the member's user JID.
     * This mirrors the engine's by-device lookup that falls back to a relaxed same-account match when no
     * exact device entry is found.
     *
     * @param deviceJid the device JID to resolve
     * @return an {@link Optional} holding the owning member's slot, or empty when no member matches
     * @throws NullPointerException if {@code deviceJid} is {@code null}
     */
    public Optional<CallMembershipSlot> findByDeviceJid(Jid deviceJid) {
        Objects.requireNonNull(deviceJid, "deviceJid cannot be null");
        lock.lock();
        try {
            for (var slot : slots.values()) {
                for (var device : slot.identity().devices()) {
                    if (device.jid().equals(deviceJid)) {
                        return Optional.of(slot);
                    }
                }
            }
            for (var slot : slots.values()) {
                if (slot.userJid().isSameAccount(deviceJid)) {
                    return Optional.of(slot);
                }
            }
            return Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes the member with the given user JID from the set, returning the freed slot if it existed.
     *
     * <p>The slot is marked {@linkplain CallMembershipSlot#markRemoved() removed} before it is dropped
     * from the set, mirroring the engine teardown that sets the member's device state to invalid before
     * freeing the slot, so the returned slot reports a removed state to any caller that drives the
     * member's media and transport teardown off it.
     *
     * @param userJid the member's user JID
     * @return an {@link Optional} holding the removed slot, or empty when no member matched
     * @throws NullPointerException if {@code userJid} is {@code null}
     */
    public Optional<CallMembershipSlot> remove(Jid userJid) {
        Objects.requireNonNull(userJid, "userJid cannot be null");
        var key = userJid.toUserJid();
        lock.lock();
        try {
            var removed = slots.remove(key);
            if (removed == null) {
                return Optional.empty();
            }
            removed.markRemoved();
            return Optional.of(removed);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the number of members currently allocated.
     *
     * @return the slot count, never negative and never above {@value #MAX_PARTICIPANTS}
     */
    public int size() {
        lock.lock();
        try {
            return slots.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the identities of every currently allocated member, in insertion order.
     *
     * <p>The returned list is a snapshot copied under the lock; it does not track later mutations and
     * carries only the immutable {@link CallParticipantUserNode} identities, never the live slots, so no
     * member reference escapes this manager.
     *
     * @return an unmodifiable snapshot of the member identities, possibly empty
     */
    public List<CallParticipantUserNode> identities() {
        lock.lock();
        try {
            var result = new ArrayList<CallParticipantUserNode>(slots.size());
            for (var slot : slots.values()) {
                result.add(slot.identity());
            }
            return List.copyOf(result);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns every currently allocated member slot, in insertion order.
     *
     * <p>The returned list is a snapshot of the live slot references copied under the lock; unlike
     * {@link #identities()}, which copies out only the immutable identities, this exposes the slots
     * themselves so a caller that drives a per-peer effect keyed on a slot's mutable bookkeeping (such as
     * the per-peer offer-send timestamp the outbound-group-call sweep walks) can read and update them. The
     * list itself does not track later allocations or removals, but each slot is the live one the manager
     * holds, so mutating a slot's bookkeeping through it is observed by the manager; the slot's
     * {@linkplain CallMembershipSlot#identity() identity} and {@linkplain CallMembershipSlot#removed()
     * removed flag} stay the manager's to mutate.
     *
     * @return an unmodifiable snapshot of the live member slots, possibly empty
     */
    public List<CallMembershipSlot> slots() {
        lock.lock();
        try {
            return List.copyOf(slots.values());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Reconciles the member set against a group-info roster, returning the diff applied.
     *
     * <p>Each roster entry is parsed into a {@link CallParticipantUserNode}; an entry whose account has
     * no slot is allocated (added), an entry whose account already has a slot replaces its identity
     * (updated), and a member with a slot but no matching roster entry is removed. The set stays bounded
     * at {@value #MAX_PARTICIPANTS} members: an entry that would exceed the ceiling is skipped and
     * omitted from the added list. The roster's declared child form selects whether the entries are read
     * as {@code <user>} or {@code <participant>} elements; an entry that does not parse to a usable
     * identity is skipped.
     *
     * <p>The whole reconcile runs under the lock so a concurrent allocate, remove, or second reconcile
     * cannot observe a half-applied diff.
     *
     * @param roster the group-info roster to reconcile against
     * @return the {@link Reconciliation} of members added, updated, and removed
     * @throws NullPointerException if {@code roster} is {@code null}
     */
    public Reconciliation reconcile(GroupInfoStanza roster) {
        Objects.requireNonNull(roster, "roster cannot be null");
        lock.lock();
        try {
            var added = new ArrayList<CallParticipantUserNode>();
            var updated = new ArrayList<CallParticipantUserNode>();
            var seen = new HashSet<Jid>();
            for (var entry : roster.entries()) {
                var identity = CallParticipantUserNode.of(entry).orElse(null);
                if (identity == null) {
                    continue;
                }
                var key = identity.jid().toUserJid();
                var existing = slots.get(key);
                if (existing != null) {
                    existing.identity(identity);
                    deriveSlotCrypto(existing.participant());
                    seen.add(key);
                    updated.add(identity);
                    continue;
                }
                if (slots.size() >= MAX_PARTICIPANTS) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Skipping roster member {0}: slot array is full ({1})", key, MAX_PARTICIPANTS);
                    continue;
                }
                var slot = new CallMembershipSlot(callId, key, identity,
                        new CallParticipant(key, isExtension(identity)));
                slots.put(key, slot);
                deriveSlotCrypto(slot.participant());
                seen.add(key);
                added.add(identity);
            }
            var removed = new ArrayList<CallParticipantUserNode>();
            var iterator = slots.entrySet().iterator();
            while (iterator.hasNext()) {
                var slotEntry = iterator.next();
                if (seen.contains(slotEntry.getKey())) {
                    continue;
                }
                var slot = slotEntry.getValue();
                slot.markRemoved();
                removed.add(slot.identity());
                iterator.remove();
            }
            return new Reconciliation(added, updated, removed);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Records the local participant's user JID so the {@linkplain #participantProvider() participant provider}
     * can resolve its self view.
     *
     * <p>The JID is stored in its {@linkplain Jid#toUserJid() account form}, so a self device JID resolves to
     * the self slot. Until this is set, {@link ParticipantProvider#selfView()} reports
     * {@link ParticipantView#invalid()}; setting it does not allocate a slot, so the self view stays invalid
     * until a roster also allocates the self member's slot.
     *
     * @param selfUserJid the local participant's user JID, or {@code null} to clear it
     */
    public void selfUserJid(Jid selfUserJid) {
        lock.lock();
        try {
            this.selfUserJid = selfUserJid == null ? null : selfUserJid.toUserJid();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the local participant's user JID, if one has been recorded.
     *
     * @return an {@link Optional} holding the account-form self user JID, or empty when none is set
     */
    public Optional<Jid> selfUserJid() {
        lock.lock();
        try {
            return Optional.ofNullable(selfUserJid);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Installs (or rotates) the raw end-to-end call key and re-derives every member's crypto block.
     *
     * <p>The caller mints this key and fans it out per device in the offer, or the callee decrypts it from
     * the offer's Signal-encrypted {@code <enc>} envelope; on a group rekey the rotated key is installed
     * here again. Installing the key records it together with its key-generation version and, under the
     * lock, re-runs the per-participant key derivation for every currently allocated member, so each
     * member's {@linkplain CallParticipant#crypto() crypto block} gets its SFrame chain key and SRTP
     * master from the new key. A member added or refreshed after this call derives its keys from the
     * stored key during the upsert. The key must be the full {@value CallParticipantCrypto#RAW_E2E_KEY_LENGTH}
     * bytes, since the per-participant {@link CallE2eKeyDerivation} chain requires a full-length key.
     *
     * @implNote This implementation reproduces the engine flow where
     * {@code call_update_participant_keys} (fn10898) ingests the raw key (length {@code 1..0x20}, keygen
     * version {@code 2}) and then derives the SFrame and SRTP keys inline for the self participant and
     * every connected participant; here the ingest is threaded into the membership so the derivation runs
     * over every slot the membership holds.
     * @param rawCallKey    the raw end-to-end call key; never {@code null}
     * @param keygenVersion the key-generation version recorded for the key
     * @throws NullPointerException       if {@code rawCallKey} is {@code null}
     * @throws WhatsAppCallException.Srtp if the key is not the full length, or the keygen version is
     *                                    unsupported
     */
    public void installCallKey(byte[] rawCallKey, int keygenVersion) {
        Objects.requireNonNull(rawCallKey, "rawCallKey cannot be null");
        CallE2eKeyDerivation.requireSupportedKeygenVersion(keygenVersion);
        if (rawCallKey.length != CallParticipantCrypto.RAW_E2E_KEY_LENGTH) {
            throw new WhatsAppCallException.Srtp("raw call key must be "
                    + CallParticipantCrypto.RAW_E2E_KEY_LENGTH + " bytes, got " + rawCallKey.length);
        }
        lock.lock();
        try {
            this.rawCallKey = rawCallKey.clone();
            this.keygenVersion = keygenVersion;
            for (var slot : slots.values()) {
                deriveSlotCrypto(slot.participant());
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns whether a raw end-to-end call key has been installed.
     *
     * @return {@code true} once {@link #installCallKey(byte[], int)} has recorded a key
     */
    public boolean hasCallKey() {
        lock.lock();
        try {
            return rawCallKey != null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Builds one SFrame key provider per member device whose crypto block has a derived chain key, keyed
     * by the member's active device JID.
     *
     * <p>This is the seam the media plane drives to open peers' relayed SFrame media: for every member
     * with an active device and a derived SFrame chain key, it builds a fresh {@link SFrameKeyProvider}
     * and installs that member's base key
     * ({@link CallParticipantCrypto#installSframeChainKey(SFrameKeyProvider)}), so the inbound demux can
     * resolve a sender device JID to the provider that opens its frames. A member without an installed
     * call key, without an active device, or whose keys are not yet derived is omitted. The returned map
     * is a snapshot taken under {@link #lock}; each provider is freshly built and owned by the caller, and
     * mutating the map does not affect the membership.
     *
     * @apiNote The media plane installs the SELF device's provider from the call key directly; this method
     * supplies the per-peer providers the inbound SFrame open path needs. Call it after a reconcile or a
     * rekey (an {@link #installCallKey(byte[], int)}) so newly-derived peers are included.
     * @implNote This implementation reproduces the native rule that each participant owns an SFrame key
     * provider ({@code participant+0xe0}) seeded by {@code generate_sframe_keys_for_participant} (fn10894)
     * with that participant's base key. The native engine seeds the providers in place during the diff;
     * Cobalt's membership holds the crypto material and hands the media plane freshly-built providers it
     * installs into its inbound demux.
     * @return a map from each ready member's active device JID to its SFrame key provider, possibly empty
     */
    public SequencedMap<CallDeviceJid, SFrameKeyProvider> sframeProvidersByDevice() {
        lock.lock();
        try {
            var result = new LinkedHashMap<CallDeviceJid, SFrameKeyProvider>();
            for (var slot : slots.values()) {
                var participant = slot.participant();
                var deviceJid = participant.activeDeviceJid().orElse(null);
                if (deviceJid == null) {
                    continue;
                }
                var provider = new SFrameKeyProvider();
                if (participant.crypto().installSframeChainKey(provider)) {
                    result.put(deviceJid, provider);
                }
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Derives and stores the SFrame chain key and SRTP master for a participant aggregate from the
     * installed call key, keyed by the participant's active device JID.
     *
     * <p>A no-op when no call key has been installed or the participant has no active device pinned (its
     * keys cannot be bound without a device JID); in both cases the crypto block is left as it is. The
     * derivation ingests the raw key into the participant's {@linkplain CallParticipant#crypto() crypto
     * block} (recording the keygen version and clearing any stale derived keys) and then runs the
     * byte-verified {@link CallE2eKeyDerivation} chain for the active device JID.
     *
     * @implNote This implementation reproduces the per-participant derivation
     * {@code call_update_participant_keys} (fn10898) performs inline through
     * {@code generate_sframe_keys_for_participant} (fn10894) and
     * {@code generate_srtp_and_p2p_keys_for_participant} (fn10895), keyed on the participant's active
     * device JID exactly as the native diff keys {@code derive_sframe_key} (fn10896) on the device JID at
     * {@code participant+0x137}. Must be called under {@link #lock}.
     * @param participant the aggregate whose crypto block is derived
     */
    private void deriveSlotCrypto(CallParticipant participant) {
        if (rawCallKey == null) {
            return;
        }
        var deviceJid = participant.activeDeviceJid().orElse(null);
        if (deviceJid == null) {
            return;
        }
        var crypto = participant.crypto();
        crypto.storeRawKey(rawCallKey, keygenVersion, crypto.keyCount(), crypto.hasBot());
        crypto.deriveKeys(deviceJid);
    }

    /**
     * Returns a {@link ParticipantProvider} reading this manager's per-slot {@link CallParticipant}
     * aggregates.
     *
     * <p>The returned provider is a thin flyweight bound to this manager: each call to its
     * {@link ParticipantProvider#views() views()} snapshots every slot's {@link CallParticipant#toView()}
     * under {@link #lock}, its {@link ParticipantProvider#selfView() selfView()} returns the
     * {@linkplain #selfUserJid(Jid) self} slot's view or {@link ParticipantView#invalid()}, and its
     * {@link ParticipantProvider#isValid() isValid()} reports whether this manager currently holds any slot.
     * No {@link CallParticipant} reference escapes through it; only immutable {@link ParticipantView}
     * snapshots are handed out, honoring the provider contract.
     *
     * @return a participant provider over this manager's slots, never {@code null}
     */
    public ParticipantProvider participantProvider() {
        return new CallMembershipParticipantProvider(this);
    }

    /**
     * Returns immutable snapshots of every currently allocated member's participant aggregate, in insertion
     * order.
     *
     * <p>Each snapshot is the slot's {@link CallParticipant#toView()} taken under {@link #lock}; the returned
     * list is a copy and carries no live {@link CallParticipant} reference. This is the primitive
     * {@link #participantProvider()} builds {@link ParticipantProvider#views()} on.
     *
     * @return an unmodifiable snapshot of every member's participant view, possibly empty
     */
    List<ParticipantView> participantViews() {
        lock.lock();
        try {
            var result = new ArrayList<ParticipantView>(slots.size());
            for (var slot : slots.values()) {
                result.add(slot.participant().toView());
            }
            return List.copyOf(result);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the immutable snapshot of the local participant's aggregate, or
     * {@link ParticipantView#invalid()} when there is no self participant.
     *
     * <p>The self slot is the one keyed by the {@linkplain #selfUserJid(Jid) recorded self user JID}; when no
     * self JID is set or no slot matches it, this returns {@link ParticipantView#invalid()}. The snapshot is
     * taken under {@link #lock} and carries no live reference. This is the primitive
     * {@link #participantProvider()} builds {@link ParticipantProvider#selfView()} on.
     *
     * @return the self participant view, or {@link ParticipantView#invalid()} when there is no self
     *         participant
     */
    ParticipantView participantSelfView() {
        lock.lock();
        try {
            if (selfUserJid == null) {
                return ParticipantView.invalid();
            }
            var slot = slots.get(selfUserJid);
            return slot == null ? ParticipantView.invalid() : slot.participant().toView();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns whether this manager currently holds at least one member slot.
     *
     * <p>This is the primitive {@link #participantProvider()} builds {@link ParticipantProvider#isValid()} on,
     * reproducing the native check that a provider is valid only while it is backed by a live participant set.
     *
     * @return {@code true} when at least one member is allocated
     */
    boolean hasParticipants() {
        lock.lock();
        try {
            return !slots.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the per-stream receive SSRCs of every connected peer, in slot (insertion) order, excluding the
     * self participant.
     *
     * <p>This is the roster read the fused stream-subscription publish keys its remote entries off: for every
     * connected, non-self member it reads the member's deterministic per-stream SSRCs off its
     * {@linkplain CallParticipant#media() media} object (the audio main SSRC and the two simulcast video
     * primary SSRCs, the values {@link CallSecureSsrcGenerator} derived from the call-id and the member's
     * active device JID during the upsert), and returns them as a {@link PeerStreamSsrcs} tuple. The list
     * preserves slot order, so the positional index of a peer in the returned list is the index the fused
     * {@code 0x4024} {@link com.github.auties00.cobalt.model.call.datachannel.StreamSubscriptions} matrix
     * tags that peer's entries with. A peer whose audio SSRC has not been derived (no active device pinned
     * yet) is skipped, since it carries no receive layout to subscribe to. Self is excluded because it is the
     * matrix's leading participant-less entry, sourced from the local send layout rather than the roster.
     *
     * <p>The snapshot is taken under {@link #lock}; the returned list is a copy and carries no live
     * {@link CallParticipant} reference. Because the SSRCs are a pure function of the call-id and the device
     * JID, the tuple is stable across reconciles for a member whose active device does not change.
     *
     * @implNote This implementation supplies the connected-peer receive SSRCs the native fused-subscription
     * builder reads back out of each {@code call_participant}'s media sub-object ({@code call_participant+0x5f8},
     * the audio stream container and the two {@code +0x608} simulcast video-stream-data records,
     * {@code call_participant_media.cc}), the same per-(device, stream) SSRCs {@code call_generate_device_ssrc}
     * (fn10901) wrote there at participant add. The native builder reads them through the
     * {@code wa_call_participant_streams} struct ({@code populate_participant_streams}, fn10992); Cobalt reads
     * the participant's {@link CallParticipantMedia} fields directly. The connected predicate is the membership
     * state {@code == kParticipantStateConnected} ({@link CallParticipantState#isConnected()}), matching the
     * native rule that an un-connected member contributes no receive subscription.
     * @return the connected peers' per-stream receive SSRCs in slot order, excluding self; possibly empty
     */
    public List<PeerStreamSsrcs> connectedPeerStreamSubscriptions() {
        lock.lock();
        try {
            var result = new ArrayList<PeerStreamSsrcs>(slots.size());
            for (var entry : slots.entrySet()) {
                if (entry.getKey().equals(selfUserJid)) {
                    continue;
                }
                var participant = entry.getValue().participant();
                if (!participant.isConnected()) {
                    continue;
                }
                var media = participant.media();
                var audioSsrc = media.audioSsrc().orElse(CallParticipantMedia.UNASSIGNED_SSRC);
                if (audioSsrc == CallParticipantMedia.UNASSIGNED_SSRC) {
                    continue;
                }
                result.add(new PeerStreamSsrcs(
                        audioSsrc,
                        media.videoPrimarySsrc(0).orElse(CallParticipantMedia.UNASSIGNED_SSRC),
                        media.videoPrimarySsrc(1).orElse(CallParticipantMedia.UNASSIGNED_SSRC)));
            }
            return List.copyOf(result);
        } finally {
            lock.unlock();
        }
    }

    /**
     * The per-stream receive SSRCs of one connected peer the fused stream subscription declares.
     *
     * <p>Carries the peer's deterministic audio main SSRC and the primary SSRCs of its two simulcast video
     * streams, each in the canonical unsigned {@code 0..0xFFFFFFFF} range held in an {@code int}. A video SSRC
     * equal to {@link CallParticipantMedia#UNASSIGNED_SSRC} marks a video stream the peer has not derived, so
     * the subscription omits that stream's entry; the audio SSRC is always present in a returned tuple.
     *
     * @param audioSsrc        the peer's audio main receive SSRC, never {@link CallParticipantMedia#UNASSIGNED_SSRC}
     * @param videoStream0Ssrc the peer's first simulcast video primary SSRC, or
     *                         {@link CallParticipantMedia#UNASSIGNED_SSRC} when absent
     * @param videoStream1Ssrc the peer's second simulcast video primary SSRC, or
     *                         {@link CallParticipantMedia#UNASSIGNED_SSRC} when absent
     */
    public record PeerStreamSsrcs(int audioSsrc, int videoStream0Ssrc, int videoStream1Ssrc) {
    }

    /**
     * Upserts a participant aggregate from a fresh roster identity, mirroring every membership-derived field.
     *
     * <p>This copies the identity vocabulary of the {@link CallParticipantUserNode} wire DTO onto the
     * {@link CallParticipant} aggregate: the user type (defaulting to {@link CallParticipantUserType#NORMAL}
     * when the wire token was unclassifiable), the account kind (defaulting to
     * {@link CallParticipantAccountKind#REGULAR}), the LID, the server-assigned participant id, the push and
     * guest names, the active-device JID (the entry's first device, the engine's active device for a member),
     * the platform (the active device's {@code platform} token, since the entry itself carries no platform
     * attribute), the per-device {@link CallDeviceInfo} list (each device's participant id and the entry's
     * advertised video-decode capabilities), and the membership state projected from the roster {@code state}
     * literal onto {@link CallParticipantState}. A device already present keeps its mutable per-device media
     * state (SSRC set) while its identity-derived fields are refreshed; a device absent from the fresh roster
     * is dropped from the aggregate so the device list tracks the entry.
     *
     * <p>The aggregate's per-device and per-stream media-plane SSRC sets (on each {@link CallDeviceInfo} and
     * on {@link CallParticipant#media()}) ARE filled here from the call-id, which the native group diff reads
     * from the embedded {@code call_context} ({@code +0x270}) to key the secure-SSRC generator. The crypto
     * block ({@link CallParticipant#crypto()}) is NOT filled here: it derives from the 32-byte raw call key,
     * which is not an input to this manager (the key is minted or decrypted after the first reconcile and
     * lives on the call's orchestration handle and media plane); see the crypto-block {@code TODO} at the end
     * of this method's body for the precise blocker.
     *
     * @implNote This implementation reproduces the membership-driven field population of the native
     * {@code call_participant} struct from a {@code <group_info>} entry ({@code call_participant.cc} fill on
     * {@code call_update_participants}, fn10834, dispatching the group diff to fn10973). The native group diff
     * derives the per-device SSRC set inline during the reconcile by calling {@code call_generate_device_ssrc}
     * (fn10901) once per device, reading the call-id from the embedded {@code call_context} at offset
     * {@code +0x270}; this implementation calls {@link CallSecureSsrcGenerator} with the {@link #callId} the
     * manager is constructed with for the same effect (see {@link #upsertDevices}). The native diff also
     * derives the per-participant SFrame base key plus SRTP master through fn10897/fn10896
     * ({@code CallE2eKeyDerivation}, reading the raw call key from the context's key field). This method
     * mirrors only the identity-and-device fields; the per-participant crypto block is derived separately
     * by {@link #deriveSlotCrypto(CallParticipant)} (called right after each allocate, reconcile, and
     * identity refresh, and on every {@link #installCallKey(byte[], int)}), because it keys on the raw
     * call key the membership receives after the first reconcile rather than on the wire call-id this
     * upsert keys the SSRCs on. The membership state projection inverts the server-user-state table at
     * {@code 0x1262b0} ({@code serialize_peer_state}, fn11702) onto {@link CallParticipantState} through
     * {@link #stateOfLiteral(String)}, with {@code "connected"} mapping to
     * {@link CallParticipantState#CONNECTED}, the terminal literals to {@link CallParticipantState#INVALID},
     * and an unrecognized or absent literal leaving the aggregate's current state untouched (an allocated
     * participant defaults to {@link CallParticipantState#INVITED}).
     * @param callId      the wire call-id keying the per-device secure-SSRC generation
     * @param participant the aggregate to refresh in place
     * @param identity    the fresh roster identity to mirror onto it
     */
    private static void upsertParticipant(String callId, CallParticipant participant,
                                          CallParticipantUserNode identity) {
        participant.userType(identity.userType().orElse(CallParticipantUserType.NORMAL));
        participant.accountKind(identity.accountKind().orElse(CallParticipantAccountKind.REGULAR));
        participant.lid(identity.userLid().orElse(null));
        identity.pidValue().ifPresent(participant::pid);
        participant.pushName(identity.pushName().orElse(null));
        participant.guestName(identity.guestName().orElse(null));
        upsertDevices(callId, participant, identity.devices(), identity.decoderCapability().orElse(null));
        identity.devices().stream()
                .findFirst()
                .flatMap(CallParticipantUserNode.Device::platform)
                .ifPresent(participant::platform);
        identity.state()
                .flatMap(CallMembership::stateOfLiteral)
                .ifPresent(participant::state);
    }

    /**
     * Upserts the per-device {@link CallDeviceInfo} list of a participant aggregate from a roster entry's
     * devices, and pins the entry's first device as the active device.
     *
     * <p>Each roster device is added to the aggregate if absent (preserving any already-present device's
     * mutable media state) and its identity-derived fields are refreshed: the server-assigned participant id,
     * and, when the entry advertised a {@code <dec>} video-decode descriptor, the codecs parsed from it. The
     * decode descriptor is an entry-level (not per-device) {@code vid_dec} token string in the wire DTO, so it
     * is applied to every device of the entry; an absent descriptor leaves a device's existing decode
     * capabilities untouched. A device the aggregate holds that the fresh roster no longer lists is dropped,
     * so the aggregate's device set tracks the entry. The active device is pinned to the entry's first device
     * when none is set or the current one is no longer rostered, but an already-pinned active device that is
     * still present is preserved, so a media-plane pin survives a reconcile.
     *
     * <p>Each device's device-wide SSRC set (main RTP, app-data, IMU, and hop-by-hop FEC transmit and
     * receive) is generated from the call-id and the device JID and recorded on the device through
     * {@link CallDeviceInfo#ssrcs(int, int, int, int, int)}, and the participant's per-stream SSRCs (the
     * audio stream and the two simulcast video streams) are generated for the active device and recorded on
     * {@link CallParticipant#media()}. The SSRCs are a pure function of the call-id and the device JID, so a
     * reconcile re-derives the same values; they are deterministic both ends pre-register, never random.
     *
     * @implNote This implementation mirrors the per-device fill of {@code call_participant.cc} and the
     * decode-capability parse of {@code wa_call_device_info_update_vid_dec_cap} (fn10873), which parses the
     * entry's {@code vid_dec} descriptor string (here {@link CallParticipantUserNode#decoderCapability()})
     * through {@link com.github.auties00.cobalt.calls2.common.VideoDecoderCapability#parse(String)} with its
     * H264 fallback. The device's binary {@code <capability>} mask
     * ({@link CallParticipantUserNode.Device#capability()}) is the feature bitset, not the codec descriptor,
     * and is owned by the capability subsystem rather than mapped onto the decode-capability set here. The
     * per-device video-enabled flag is a media-plane signal not carried in the roster entry, so it is left at
     * its default here. The SSRC generation reproduces {@code call_generate_device_ssrc} (fn10901), which the
     * group diff (fn10973) calls once per device: per device it derives the main, app-data (media-type
     * {@code 6}), IMU (media-type {@code 10}), and hop-by-hop FEC transmit and receive (media-types {@code 7}
     * and {@code 8}) SSRCs through {@link CallSecureSsrcGenerator}, and per participant the audio triple
     * (codes {@code 0}/{@code 1}/{@code 4}) and the two simulcast video triples (codes {@code 2}/{@code 3}/
     * {@code 5}) for the active device. The native fn10901 generates the full set for every device; this maps
     * the per-device scalars onto each {@link CallDeviceInfo} and the participant-level stream triples onto
     * the active device's {@link CallParticipantMedia}, matching the Cobalt split where per-stream triples are
     * a participant-media field rather than a per-device one.
     * @param callId           the wire call-id keying the per-device secure-SSRC generation
     * @param participant      the aggregate whose device list is refreshed
     * @param devices          the roster entry's devices to mirror
     * @param decoderCapability the entry's advertised video-decode descriptor, or {@code null} when absent
     */
    private static void upsertDevices(String callId, CallParticipant participant,
                                      List<CallParticipantUserNode.Device> devices,
                                      String decoderCapability) {
        // TODO: gate the SSRC derivation on newly-added devices only. addDevice returns the existing
        //  instance for a device already present (reference identity distinguishes a fresh add), and the
        //  per-device and per-stream SSRCs are a pure function of (callId, deviceJid), so re-deriving them
        //  on every reconcile recomputes identical values. Skipping the info.ssrcs(...) block for an
        //  already-present device, and gating populateStreamSsrcs on the active device actually changing,
        //  would drop that redundant work and let CallSecureSsrcGenerator encode the callId/base once per
        //  reconcile via a batch deviceSsrcs(byte[] callIdBytes, ...) helper. Left as-is because the current
        //  code unconditionally overwrites those media/device SSRC fields every reconcile, so gating is only
        //  behavior-preserving if no other writer (the media plane) ever mutates them between reconciles;
        //  that invariant is not proven here, and a divergence would be a silent wire-SSRC change.
        var seen = new ArrayList<CallDeviceJid>(devices.size());
        CallDeviceJid first = null;
        for (var device : devices) {
            var deviceJid = CallDeviceJid.of(device.jid());
            seen.add(deviceJid);
            if (first == null) {
                first = deviceJid;
            }
            var info = participant.addDevice(new CallDeviceInfo(deviceJid));
            device.pidValue().ifPresent(info::pid);
            if (decoderCapability != null) {
                info.decoderCapabilities(VideoDecoderCapability.parse(decoderCapability));
            }
            info.ssrcs(
                    CallSecureSsrcGenerator.audioMainSsrc(callId, deviceJid),
                    CallSecureSsrcGenerator.appDataSsrc(callId, deviceJid),
                    CallSecureSsrcGenerator.imuDataSsrc(callId, deviceJid),
                    CallSecureSsrcGenerator.ssrc(callId, deviceJid,
                            CallSecureSsrcGenerator.MEDIA_TYPE_HBH_FEC_TX, 0),
                    CallSecureSsrcGenerator.ssrc(callId, deviceJid,
                            CallSecureSsrcGenerator.MEDIA_TYPE_HBH_FEC_RX, 0));
        }
        for (var existing : participant.devices()) {
            if (!seen.contains(existing.deviceJid())) {
                participant.removeDevice(existing.deviceJid());
            }
        }
        var currentActive = participant.activeDeviceJid().orElse(null);
        if (currentActive == null || !seen.contains(currentActive)) {
            participant.activeDeviceJid(first);
        }
        participant.activeDeviceJid()
                .ifPresent(active -> populateStreamSsrcs(callId, participant.media(), active));
    }

    /**
     * Generates and records the participant's per-stream SSRCs for its active device.
     *
     * <p>Derives the audio stream's main SSRC and the two simulcast video streams' {primary, FEC, NACK}
     * triples for the active device from the call-id and records them on the participant media object. The
     * SSRCs are deterministic functions of the call-id and the device JID, so this re-derives the same values
     * on every reconcile.
     *
     * @implNote This implementation reproduces the participant-level stream SSRCs the native
     * {@code call_generate_device_ssrc} (fn10901) derives for a device: the audio primary (code {@code 0},
     * the {@code param2+0x15} identifier context, fn10901 line 37) and the two simulcast video triples (codes
     * {@code 2}/{@code 3}/{@code 5} for stream ids {@code 0} and {@code 1}, the {@code param2+0x18} context,
     * fn10901 lines 44-53) through {@link CallSecureSsrcGenerator#videoTriple(String, CallDeviceJid, int)},
     * whose audio and video values are byte-verified against the live capture. The native diff additionally
     * runs a second two-stream video loop (the {@code param2+0x1e} context, fn10901 lines 55-64) for the
     * screenshare stream; that family is NOT pre-derived here because the Cobalt secure-SSRC generator does
     * not expose its identifier context and its values are not capture-verified, and the screenshare triple
     * is recorded on {@link CallParticipantMedia#screenShareSsrcs(int, int, int)} by the media plane when the
     * participant starts sharing rather than seeded from the roster. Fabricating it would violate the
     * never-diverge rule; see the screenshare {@code TODO} in this method's body.
     * @param callId    the wire call-id keying the secure-SSRC generation
     * @param media     the participant media object the stream SSRCs are recorded on
     * @param deviceJid the active device whose stream SSRCs are generated
     */
    private static void populateStreamSsrcs(String callId, CallParticipantMedia media, CallDeviceJid deviceJid) {
        media.audioSsrc(CallSecureSsrcGenerator.audioMainSsrc(callId, deviceJid));
        for (var streamId = 0; streamId < CallSecureSsrcGenerator.VIDEO_STREAM_COUNT; streamId++) {
            var triple = CallSecureSsrcGenerator.videoTriple(callId, deviceJid, streamId);
            media.videoStreamSsrcs(streamId, triple.primary(), triple.fec(), triple.oobNack());
        }
        // TODO: the native fn10901 second video loop (param2+0x1e context, lines 55-64) derives the
        //  screenshare simulcast SSRC family (codes 2/3/5) per device during the diff. It is not pre-derived
        //  here: CallSecureSsrcGenerator exposes no screenshare identifier-context helper, the values are not
        //  capture-verified (the live capture pinned only audio and the +0x18 video family), and the
        //  screenshare triple is recorded on CallParticipantMedia.screenShareSsrcs by the media plane when
        //  sharing starts. Pre-seeding it from the roster requires the +0x1e identifier context to be added to
        //  the generator and verified against a live screenshare capture. Do NOT fabricate it.
    }

    /**
     * Projects a roster {@code state} literal (a server-user-state) onto the internal
     * {@link CallParticipantState}.
     *
     * <p>The roster {@code state} attribute carries one of the twelve server-user-state literals the
     * relay projects a participant onto; this maps each onto the matching internal membership state, the
     * load-bearing distinction being that a terminal server state ({@code rejected}, {@code terminated},
     * {@code timedout}, {@code cancel_offer}, {@code invalid}) projects onto {@link CallParticipantState#INVALID}
     * so the participant stops reporting {@linkplain CallParticipantState#isAllocated() active}, while a
     * pre-connect or visibility state projects onto an allocated-but-not-connected state and
     * {@code connected} projects onto {@link CallParticipantState#CONNECTED}. An unrecognized literal
     * yields an empty result so the caller leaves the aggregate's current state untouched.
     *
     * @implNote This implementation inverts the server-user-state table at WASM data offset
     * {@code 0x1262b0} that {@code serialize_peer_state} (fn11702) indexes by the participant's
     * server-user-state field ({@code participant+0xb4}): {@code 0 invalid, 1 connected, 2 outgoing,
     * 3 receipt, 4 rejected, 5 terminated, 6 timedout, 7 creating, 8 invisible, 9 visible,
     * 10 cancel_offer, 11 invited}. The internal {@link CallParticipantState} is a distinct enum from
     * that server-user-state vocabulary; only {@code connected -> CONNECTED} (internal code {@code 1}) and
     * {@code invalid -> INVALID} (internal code {@code 0}) are name-confirmed. The terminal states are
     * projected onto {@link CallParticipantState#INVALID} (the engine's not-active sentinel), the
     * transitional states ({@code outgoing}, {@code receipt}, {@code creating}) onto
     * {@link CallParticipantState#CONNECTING}, and the pre-connect and visibility states ({@code invited},
     * {@code invisible}, {@code visible}) onto {@link CallParticipantState#INVITED}; the exact internal
     * code each transitional and pre-connect server-state maps to is the semantic projection of the
     * recovered literal table, not a recovered internal-state table (see the {@code CallParticipantState}
     * placeholder {@code TODO} for the unconfirmed internal codes).
     * @param literal the roster state literal
     * @return the matching membership state, or {@link Optional#empty()} when the literal is unrecognized
     */
    private static Optional<CallParticipantState> stateOfLiteral(String literal) {
        if (literal == null) {
            return Optional.empty();
        }
        return switch (literal.toLowerCase(java.util.Locale.ROOT)) {
            case "connected" -> Optional.of(CallParticipantState.CONNECTED);
            case "invalid", "rejected", "terminated", "timedout", "cancel_offer" ->
                    Optional.of(CallParticipantState.INVALID);
            case "outgoing", "receipt", "creating" -> Optional.of(CallParticipantState.CONNECTING);
            case "invited", "invisible", "visible" -> Optional.of(CallParticipantState.INVITED);
            default -> Optional.empty();
        };
    }

    /**
     * Resolves whether a roster identity allocates a call extension (a bot or AI media stream) rather
     * than a real user participant.
     *
     * <p>An extension gets the engine's forced-enabled video-state view and the v3/ext rekey path. The
     * candidate signal is the participant's {@link CallParticipantUserType#BOT bot} user type, but whether
     * that signal alone (versus a distinct roster marker) flips the extension flag, and whether it is
     * additionally gated behind the {@code kCallExtensionMV1} feature flag, is not recovered; this returns
     * {@code false} so no participant is provisionally marked an extension.
     *
     * @implNote This implementation deliberately does not yet derive the extension flag
     * ({@code participant+0x10}) the engine tracks for call media extensions (the AI/bot path with its own
     * {@code ext_rekey}/{@code handle_enc_rekey_v3} flow, fn11457). {@code populate_extension_view}
     * (fn10992) / {@code wa_participant_view_get_video_state} (fn11014) force video state {@code 1} for an
     * extension, but the precise enabling signal is unrecovered: the {@code wa_call_cstr_to_user_type}
     * (fn10884) {@code "bot"} token (user-type code {@code 3}) is the candidate, yet the
     * {@code kCallExtensionMV1} enable flag and whether the bot user type alone sets
     * {@code participant+0x10} are not confirmed from the {@code ext_rekey}/participant-create path.
     * Returning a fabricated {@code true} for a bot-typed participant would risk the forced video-state
     * override and the v3 rekey path firing for a participant the engine would not treat as an extension,
     * so it stays {@code false} until the signal is confirmed.
     * @param identity the roster identity to classify
     * @return {@code false}; the extension classification is not yet recovered
     */
    private static boolean isExtension(CallParticipantUserNode identity) {
        // TODO: derive the extension flag (participant+0x10) from the recovered participant-create / ext_rekey
        //  path. The candidate signal is identity.userType() == CallParticipantUserType.BOT (the "bot" token,
        //  wa_call_cstr_to_user_type fn10884, code 3), but it is unconfirmed whether the bot user type alone
        //  flips the extension flag and whether it is gated behind the kCallExtensionMV1 feature flag
        //  (handle_enc_rekey_v3 fn11457, populate_extension_view fn10992). Until confirmed, every participant
        //  is allocated as a non-extension so the forced-enabled video-state view (fn11014) and the v3/ext
        //  rekey path are not wrongly engaged. Do NOT fabricate the flag from the bot user type alone.
        return false;
    }

    /**
     * Holds one member's slot in the call's membership set.
     *
     * <p>A slot binds a member's {@linkplain #userJid() account-form user JID} to its current
     * {@linkplain #identity() roster identity}, the heavy {@linkplain #participant() participant aggregate}
     * the engine keys media and crypto off, and a {@linkplain #removed() removed} flag the manager sets when
     * the member leaves. The identity is the transient wire DTO; it is replaced in place as fresh rosters
     * arrive, and each replacement upserts the slot's {@link CallParticipant} from it so the aggregate's
     * identity and membership state track the roster. The account-form key is fixed for the slot's lifetime.
     * A slot is mutated only under the owning manager's lock and is never handed outside the manager other
     * than as the return of a membership operation the caller already holds the right to act on; its
     * {@link CallParticipant} aggregate is never leaked by reference to a reader, which sees only the
     * immutable {@link ParticipantView} the {@linkplain CallMembership#participantProvider() provider} hands
     * out.
     *
     * <p>Beyond identity the slot carries the per-peer offer-send timestamp the outbound-group-call
     * watchdog reads: the wall-clock millisecond moment an offer or rekey was last fanned to this member,
     * or {@link #NO_OUTSTANDING_OFFER} when no offer is outstanding. The timestamp lives on the slot
     * rather than on the {@linkplain #identity() identity} precisely so it survives a
     * {@link CallMembership#reconcile(GroupInfoStanza) reconcile}, which replaces a present member's
     * identity in place but keeps its slot; an offer fanned before a roster refresh is therefore still
     * pending afterward. It is set by the outbound-group-call unit when it fans an offer or rekey to the
     * member and cleared when the member connects, mirroring the engine's per-participant offer-elapsed
     * field.
     */
    public static final class CallMembershipSlot {
        /**
         * The sentinel value of {@link #offerSendMillis} when no offer is outstanding for the member.
         *
         * <p>A zero timestamp marks the no-outstanding-offer state, reproducing the engine's
         * {@code *offer_send_t != 0} guard in {@code periodic_call_timer_callback}: a slot whose timestamp
         * is zero is skipped by the unanswered-offer sweep.
         */
        public static final long NO_OUTSTANDING_OFFER = 0L;

        /**
         * The wire call-id this slot's call belongs to, the per-device SSRC keying input passed through to
         * the participant upsert.
         */
        private final String callId;

        /**
         * The fixed account-form user JID this slot is keyed by.
         */
        private final Jid userJid;

        /**
         * The member's current parsed roster identity, replaced in place across reconciles.
         */
        private CallParticipantUserNode identity;

        /**
         * The member's heavy participant aggregate, upserted from {@link #identity} on construction and on
         * every identity refresh.
         *
         * <p>This is the slot's canonical {@link CallParticipant}: its identity, devices, membership state,
         * and per-device and per-stream media-plane SSRC sets are mirrored from the wire DTO and the manager's
         * call-id; its crypto block is filled by {@link CallMembership#deriveSlotCrypto(CallParticipant)} once
         * the manager holds a call key ({@link CallMembership#installCallKey(byte[], int)}). The aggregate is
         * created with the slot and lives for the slot's lifetime; it is never replaced (only mutated in
         * place), so the reference is stable. It is reachable through {@link #participant()} by a caller
         * already holding this slot, but the {@linkplain CallMembership#participantProvider() participant
         * provider} the wider engine reads through hands out only immutable {@link ParticipantView}
         * snapshots, never this reference.
         */
        private final CallParticipant participant;

        /**
         * Whether the manager has marked this member removed.
         */
        private boolean removed;

        /**
         * The wall-clock millisecond timestamp at which an offer or rekey was last fanned to this member,
         * or {@link #NO_OUTSTANDING_OFFER} when no offer is outstanding.
         *
         * <p>Read by the outbound-group-call watchdog to compute how long a member's offer has gone
         * unanswered; survives a reconcile because the slot is kept while only its identity is replaced.
         * Declared {@code volatile} so the watchdog driver thread that reads it observes a write made on a
         * control thread without itself taking the manager lock.
         */
        private volatile long offerSendMillis;

        /**
         * Constructs a slot binding an account-form user JID to its initial identity and participant
         * aggregate.
         *
         * <p>The supplied aggregate is upserted from the initial identity at once, so a freshly allocated
         * slot already exposes a populated {@link CallParticipant}.
         *
         * @param callId      the wire call-id keying the per-device secure-SSRC generation
         * @param userJid     the fixed account-form user JID key
         * @param identity    the member's initial roster identity
         * @param participant the member's participant aggregate to own and keep upserted
         */
        private CallMembershipSlot(String callId, Jid userJid, CallParticipantUserNode identity,
                                   CallParticipant participant) {
            this.callId = callId;
            this.userJid = userJid;
            this.identity = identity;
            this.participant = participant;
            this.removed = false;
            this.offerSendMillis = NO_OUTSTANDING_OFFER;
            upsertParticipant(callId, participant, identity);
        }

        /**
         * Returns the fixed account-form user JID this slot is keyed by.
         *
         * @return the slot's user JID, never {@code null}
         */
        public Jid userJid() {
            return userJid;
        }

        /**
         * Returns the member's current parsed roster identity.
         *
         * @return the member identity, never {@code null}
         */
        public CallParticipantUserNode identity() {
            return identity;
        }

        /**
         * Returns the member's participant aggregate.
         *
         * <p>The returned object is the slot's live mutable {@link CallParticipant}, the canonical store the
         * engine keys media and crypto off. Its identity, devices, per-device and per-stream media SSRC sets,
         * and membership state are kept upserted from the slot's {@linkplain #identity() roster identity} and
         * the call-id; its crypto block is filled once the manager holds a call key.
         *
         * <p>The membership-derived fields the upsert mirrors are the user JID, the
         * {@linkplain CallParticipantUserType user type}, the {@linkplain CallParticipantAccountKind account
         * kind}, the {@linkplain CallParticipantPlatform platform}, the LID, the participant id, the push and
         * guest names, the device list (each device's participant id, platform-derived presence, advertised
         * decode capabilities, and device-wide SSRC set), the participant's per-stream SSRCs, and the
         * membership state projected from the roster {@code state} literal onto {@link CallParticipantState}.
         * The crypto block (the SFrame chain key and SRTP master) is derived separately by
         * {@link CallMembership#installCallKey(byte[], int)} once the raw call key is available.
         *
         * @implNote This implementation mirrors the membership-derived fields of the native
         * {@code call_participant} struct ({@code call_participant.cc}) from the {@code <group_info>} entry,
         * including the per-device SSRC sets the native group diff derives inline through
         * {@code call_generate_device_ssrc} (fn10901), here generated from the slot's call-id through
         * {@link CallSecureSsrcGenerator}. The per-participant SFrame base key and SRTP master
         * ({@code derive_sframe_key} fn10896, {@code generate_srtp_and_p2p_keys_for_participant} fn10895) are
         * derived by {@link CallMembership#deriveSlotCrypto(CallParticipant)} when the manager's call key is
         * installed, mirroring the inline derivation {@code call_update_participant_keys} (fn10898) runs over
         * every connected participant.
         * @return the participant aggregate, never {@code null}
         */
        public CallParticipant participant() {
            return participant;
        }

        /**
         * Replaces this slot's identity with a fresher one and upserts the participant aggregate from it.
         *
         * <p>Called by the manager under its lock when a reconcile or allocate carries a newer roster
         * entry for the same member; the account-form key and the {@link CallParticipant} instance are
         * unaffected, but the aggregate's identity and membership state are refreshed from the new DTO.
         *
         * @param identity the fresher roster identity
         */
        private void identity(CallParticipantUserNode identity) {
            this.identity = identity;
            upsertParticipant(callId, participant, identity);
        }

        /**
         * Returns whether the manager has marked this member removed.
         *
         * @return {@code true} once the member has left the call
         */
        public boolean removed() {
            return removed;
        }

        /**
         * Marks this member removed.
         *
         * <p>Called by the manager under its lock as the slot leaves the set; the flag lets a caller
         * driving the member's teardown off the returned slot recognize the removal.
         */
        private void markRemoved() {
            this.removed = true;
        }

        /**
         * Returns the wall-clock millisecond timestamp at which an offer or rekey was last fanned to this
         * member.
         *
         * <p>A return of {@link #NO_OUTSTANDING_OFFER} means no offer is currently outstanding for the
         * member, either because none has been fanned or because the member has since connected and the
         * outbound-group-call unit cleared the timestamp.
         *
         * @return the offer-send timestamp in milliseconds, or {@link #NO_OUTSTANDING_OFFER} when none is
         *         outstanding
         */
        public long offerSendMillis() {
            return offerSendMillis;
        }

        /**
         * Records the wall-clock millisecond timestamp at which an offer or rekey was fanned to this member.
         *
         * <p>Called by the outbound-group-call unit when it fans an offer or rekey to the member, so the
         * watchdog can later measure how long the offer has gone unanswered. Passing
         * {@link #NO_OUTSTANDING_OFFER} clears the outstanding-offer marker, which the unit does once the
         * member connects.
         *
         * @param offerSendMillis the offer-send timestamp in milliseconds, or {@link #NO_OUTSTANDING_OFFER}
         *                        to clear the marker
         */
        public void offerSendMillis(long offerSendMillis) {
            this.offerSendMillis = offerSendMillis;
        }
    }

    /**
     * Reports the effect of reconciling the member set against a group-info roster.
     *
     * <p>The three lists partition the reconcile's effect: {@link #added()} are members new to the set
     * this reconcile created, {@link #updated()} are members already present whose identity the roster
     * refreshed, and {@link #removed()} are members the manager dropped because the roster no longer
     * lists them. A caller drives the downstream media, transport, and key effects off these lists,
     * bringing up a participant for each added member and tearing one down for each removed member.
     *
     * @param added   the members allocated by this reconcile; never {@code null}, possibly empty
     * @param updated the members whose identity this reconcile refreshed; never {@code null}, possibly
     *                empty
     * @param removed the members this reconcile dropped; never {@code null}, possibly empty
     */
    public record Reconciliation(List<CallParticipantUserNode> added,
                                 List<CallParticipantUserNode> updated,
                                 List<CallParticipantUserNode> removed) {
        /**
         * Canonicalizes the record components, defensively copying each list.
         *
         * @throws NullPointerException if {@code added}, {@code updated}, or {@code removed} is
         *                              {@code null}, or any element is {@code null}
         */
        public Reconciliation {
            added = List.copyOf(added);
            updated = List.copyOf(updated);
            removed = List.copyOf(removed);
        }

        /**
         * Returns whether this reconcile changed the member set in any way.
         *
         * @return {@code true} when at least one member was added, updated, or removed
         */
        public boolean isEmpty() {
            return added.isEmpty() && updated.isEmpty() && removed.isEmpty();
        }
    }
}
