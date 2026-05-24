package com.github.auties00.cobalt.device;

import com.github.auties00.cobalt.device.fanout.DeviceGroupFanoutResult;
import com.github.auties00.cobalt.device.icdc.HostedIcdcResult;
import com.github.auties00.cobalt.device.icdc.IcdcResult;
import com.github.auties00.cobalt.model.device.DeviceListMetadata;
import com.github.auties00.cobalt.model.device.identity.ADVEncryptionType;
import com.github.auties00.cobalt.model.device.identity.ADVSignedDeviceIdentity;
import com.github.auties00.cobalt.model.device.info.DeviceList;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service that orchestrates device-list operations for WhatsApp multi-device.
 *
 * @apiNote
 * Use this service whenever Cobalt needs the recipient's set of linked devices,
 * needs to refresh that set after an ADV change notification, needs to apply
 * Identity Change Detection Consistency metadata extracted from inbound messages,
 * or needs to manage the local user's own device identity. {@link DefaultDeviceService}
 * is the production implementation that talks to the server via USync queries, the
 * ADV pipeline, and the Signal protocol store; tests substitute
 * {@link com.github.auties00.cobalt.device.StubDeviceService} or a custom
 * implementation when they need to stub fanout, session establishment, or ICDC
 * computation without the network and crypto stack.
 *
 * @implSpec
 * Implementations are expected to be thread-safe; multiple sender pipelines may
 * call the fanout methods concurrently while the ADV check scheduler is running.
 */
public interface DeviceService {
    /**
     * Returns the device lists for the given user JIDs by running a USync query.
     *
     * @apiNote
     * The entry point for sender pipelines that need every device JID a chat
     * fans out to. Pass {@code shouldMergeAltDevices = true} from chats that
     * address recipients in both PN and LID forms so each returned entry is
     * augmented with the device-id set of its alternate-addressing twin.
     *
     * @param userJids              the user JIDs to query
     * @param context               the USync context string the server uses for
     *                              telemetry and rate-limiting (for example,
     *                              {@code "message"} or {@code "interactive"})
     * @param expectedPhash         the expected participant hash to validate
     *                              against the server's response, or {@code null}
     *                              when no expectation is held
     * @param shouldMergeAltDevices when {@code true}, each entry's device ids are
     *                              merged with its alternate-addressing twin's
     * @return the device lists
     */
    Set<DeviceList> getDeviceLists(Collection<Jid> userJids, String context, String expectedPhash, boolean shouldMergeAltDevices);

    /**
     * Returns the wall-clock instant at which the ADV expiration check last ran.
     *
     * @apiNote
     * Inspected by the ADV check scheduler to compute the initial delay before its
     * first periodic tick; returns empty before the first check has ever run.
     *
     * @return the last check timestamp, or empty when never checked
     */
    Optional<Instant> lastAdvCheckTime();

    /**
     * Updates the persisted ADV check timestamp to the current instant.
     *
     * @apiNote
     * Called by the ADV check scheduler at the end of every periodic tick (even
     * the first no-op tick) so {@link #lastAdvCheckTime()} converges to the wall
     * clock.
     */
    void updateAdvCheckTime();

    /**
     * Starts the 24-hour ADV expiration check scheduler.
     *
     * @apiNote
     * Idempotent. Embedders that drive Cobalt's full multi-device lifecycle call
     * this once after the socket is online; embedders that only use Cobalt for
     * short-lived sessions can leave the scheduler off and accept stale device
     * lists.
     */
    void startAdvCheckScheduler();

    /**
     * Stops the ADV expiration check scheduler and cancels pending checks.
     *
     * @apiNote
     * Idempotent. Called on client teardown; should never be called from inside
     * the scheduler tick because it shuts down the executor that runs the tick.
     */
    void stopAdvCheckScheduler();

    /**
     * Retries any device syncs that previously failed or were queued by the ADV
     * check job.
     *
     * @apiNote
     * Invoked by the ADV check scheduler after it has queued lists that are
     * expired or close to expiration, and by the receive path when a connection
     * resumes and pending USync queries can be flushed.
     */
    void retryPendingSyncs();

    /**
     * Re-fetches device records whose Signal sessions are missing locally.
     *
     * @apiNote
     * Used by the send pipeline to repair the local store before sending: any
     * companion device that the cached list claims but the Signal session store
     * does not know about gets its identity re-fetched via USync.
     */
    void updateMissingKeyDevices();

    /**
     * Returns the device fanout for a 1:1 chat with the given user.
     *
     * @apiNote
     * Resolves the recipient's companion devices plus the local sender's other
     * devices (with the sending device stripped), returning the exact set of
     * Signal addresses the outbound message must be encrypted to.
     *
     * @param chatJid       the chat JID (the recipient user)
     * @param expectedPhash the expected participant hash to validate, or
     *                      {@code null} when no expectation is held
     * @return the device JIDs to fan out to
     */
    Collection<Jid> getUserFanout(Jid chatJid, String expectedPhash);

    /**
     * Returns the device fanout for a group chat plus the resolved phash.
     *
     * @apiNote
     * Drives group-message sends. Resolves every group participant's device list,
     * strips the sender's own device, and computes the phash the server will
     * cross-check against.
     *
     * @param chatJid         the group JID
     * @param senderDeviceJid the sender device JID, excluded from the fanout but
     *                        included in the phash
     * @return the fanout result with devices and phash
     */
    DeviceGroupFanoutResult getGroupFanout(Jid chatJid, Jid senderDeviceJid);

    /**
     * Returns the device fanout for a business broadcast-list send plus the
     * resolved phash.
     *
     * @apiNote
     * Broadcast lists are a client-only audience model. The recipient roster is
     * stored locally on
     * {@link com.github.auties00.cobalt.model.business.BusinessBroadcastList} and
     * never round-tripped through server-side group metadata, so the caller passes
     * the resolved recipient user JIDs explicitly rather than expecting the
     * SKMSG-target {@code <id>@broadcast} JID to drive a server-side metadata
     * lookup. The fanout calculator and identity filter mirror
     * {@link #getGroupFanout(Jid, Jid)} verbatim; the phash is computed over the
     * resolved device set plus {@code senderDeviceJid}.
     *
     * @param broadcastJid       the broadcast list JID ({@code <id>@broadcast});
     *                           used for diagnostics only, not for metadata
     *                           resolution
     * @param senderDeviceJid    the sender device JID, included in the phash but
     *                           not in the returned device list
     * @param recipientUserJids  the resolved recipient user JIDs from the local
     *                           broadcast list roster
     * @return the fanout result with devices and phash
     */
    DeviceGroupFanoutResult getBroadcastFanout(Jid broadcastJid, Jid senderDeviceJid, Collection<Jid> recipientUserJids);

    /**
     * Computes the Identity Change Detection Consistency metadata for the given
     * user.
     *
     * @apiNote
     * The send pipeline calls this for each message that needs to embed
     * {@code deviceListMetadata} in its {@code messageContextInfo} so the
     * recipient can detect a stale view of the sender's or their own companion
     * devices.
     *
     * @param userJid the user JID
     * @return the ICDC result, or empty when no cached device list is available
     *         for the user
     */
    Optional<IcdcResult> computeIcdc(Jid userJid);

    /**
     * Ensures that Signal sessions exist for every device JID supplied.
     *
     * @apiNote
     * Called by the send pipeline before encrypting outbound messages: any device
     * JID that the local Signal store does not yet have a session with has its
     * prekey bundle fetched and a session established.
     *
     * @param deviceJids the device JIDs whose sessions are needed
     * @return the number of new sessions established
     */
    int ensureSessions(Collection<Jid> deviceJids);

    /**
     * Handles an incoming ADV device-list change notification.
     *
     * @apiNote
     * The stream handler routes the {@code <devices>} child of a
     * {@code <notification type="account_sync">} stanza here. {@code action} is
     * the {@code type} attribute on the outer notification ({@code "add"} when a
     * companion device was linked, {@code "remove"} when one was unlinked).
     *
     * @param node    the {@code <devices>} child node
     * @param action  the action string ({@code "add"} or {@code "remove"})
     * @param userJid the user JID whose device list changed
     */
    void handleDeviceNotification(Node node, String action, Jid userJid);

    /**
     * Synchronises the local user's own device list with the server.
     *
     * @apiNote
     * Triggered after pair-success and after the offline pipeline finishes
     * replaying queued notifications; refreshes the cached own-device list to
     * reflect server-side changes that might have happened while this session
     * was offline.
     */
    void syncMyDeviceList();

    /**
     * Synchronises and returns the device lists for the given user JIDs.
     *
     * @apiNote
     * Convenience for callers that need both the side effect of refreshing the
     * cache and the resolved lists; semantically equivalent to invoking
     * {@link #getDeviceLists} with {@code context = "message"} and
     * {@code shouldMergeAltDevices = false}, then reading the resulting cached
     * entries.
     *
     * @param userJids the user JIDs to query
     * @return the synchronised device lists
     */
    List<DeviceList> syncAndGetDeviceList(Collection<Jid> userJids);

    /**
     * Returns the cached device record for the given user JID without a server
     * round-trip.
     *
     * @apiNote
     * Use when the caller already knows the cache is fresh (during message
     * encoding, for example). Returns empty when no record is cached; the caller
     * must decide whether to issue a USync or treat the user as primary-only.
     *
     * @param userJid the user JID
     * @return the device record, or empty when not stored
     */
    Optional<DeviceList> getDeviceRecord(Jid userJid);

    /**
     * Returns the cached device records for the given user JIDs.
     *
     * @apiNote
     * Bulk counterpart of {@link #getDeviceRecord(Jid)}. The returned list omits
     * users with no cached entry; the caller must reconcile by JID order, not by
     * positional alignment.
     *
     * @param userJids the user JIDs
     * @return the device records
     */
    List<DeviceList> bulkGetDeviceRecord(Collection<Jid> userJids);

    /**
     * Persists or replaces the cached device record.
     *
     * @apiNote
     * Used by the ADV update path after a successful USync response or device
     * notification; the new record fully supersedes the existing one for its
     * user JID.
     *
     * @param record the device record
     */
    void createOrReplaceDeviceRecord(DeviceList record);

    /**
     * Persists or replaces the cached device records in bulk.
     *
     * @apiNote
     * Bulk counterpart of {@link #createOrReplaceDeviceRecord(DeviceList)} called
     * by the batched ADV applier so the underlying store can do one
     * transactional write per round-trip.
     *
     * @param records the device records
     */
    void bulkCreateOrReplaceDeviceRecord(Collection<DeviceList> records);

    /**
     * Returns the device id lists for the given user JIDs, fetching from the
     * server when missing.
     *
     * @apiNote
     * Lighter-weight than {@link #getDeviceLists}: returns only the device ids
     * for each user, suitable for fanout-only callers that do not need key
     * indexes or timestamps. Honours {@code shouldMergeAltDevices} the same way
     * as {@link #getDeviceLists}.
     *
     * @param userJids              the user JIDs
     * @param shouldMergeAltDevices when {@code true}, each entry's device ids are
     *                              merged with its alternate-addressing twin's
     * @return the device lists
     */
    List<DeviceList> getDeviceIds(Collection<Jid> userJids, boolean shouldMergeAltDevices);

    /**
     * Returns the device records to feed into a USync query for the given user
     * JIDs.
     *
     * @apiNote
     * The USync query builder consumes these records to extract each user's
     * cached device hash, snapshot timestamp, and expected timestamp so the
     * server can return either a full device list or a confirming omitted
     * result.
     *
     * @param userJids the user JIDs
     * @return the device lists
     */
    List<DeviceList> getDeviceInfoForSync(Collection<Jid> userJids);

    /**
     * Returns whether the cached device list for {@code userJid} contains the
     * device id.
     *
     * @apiNote
     * Used by the receive pipeline to validate the {@code participant} attribute
     * of an inbound message against the sender's known devices; mismatches
     * trigger a device-list resync.
     *
     * @param userJid  the user JID
     * @param deviceId the device id to look up
     * @return {@code true} when the device exists in the cached list
     */
    boolean hasDevice(Jid userJid, int deviceId);

    /**
     * Returns the local user's own cached device list.
     *
     * @apiNote
     * Used by the send pipeline to compute the sender's own-device fanout and
     * to feed the ICDC sender-side metadata.
     *
     * @return the local device list
     */
    DeviceList getMyDeviceList();

    /**
     * Returns every cached device list.
     *
     * @apiNote
     * Used by the ADV check scheduler to walk every cached entry and decide
     * which are expired, close to expiration, or already deleted. Also used by
     * diagnostic surfaces.
     *
     * @return the device lists
     */
    Collection<DeviceList> getAllDeviceLists();

    /**
     * Handles ICDC metadata extracted from an inbound message.
     *
     * @apiNote
     * Routed by the inbound message processor after decryption. When the
     * sender's metadata reports a newer device-list timestamp than the cached
     * one, the cached entry's expected-timestamp tracking fields are refreshed
     * so the next ADV check job knows to fetch the sender's devices again.
     * When the sender is the recipient's own primary device and the metadata
     * carries only a timestamp (no key hash), the minimal-sync path resets the
     * cached list to primary-only and bumps the timestamp by one second.
     *
     * @param senderJid        the sender device JID
     * @param recipientUserJid the recipient user JID (only used when the sender
     *                         is the local user's own primary), or {@code null}
     * @param metadata         the {@code deviceListMetadata} from the inbound
     *                         message's {@code messageContextInfo}, or
     *                         {@code null}
     */
    void handleICDCData(Jid senderJid, Jid recipientUserJid, DeviceListMetadata metadata);

    /**
     * Handles hosted-business ICDC metadata extracted from an inbound message and
     * returns the validation outcome.
     *
     * @apiNote
     * Called only when the {@code adv_accept_hosted_devices} AB prop is on. The
     * returned {@link HostedIcdcResult} tells the caller whether the local ADV
     * account type disagrees with the inbound HOSTED type (so a device-list
     * refresh is required) and whether either side of the chat is a hosted
     * account (so coexistence UI markers may be needed).
     *
     * @param chatJid   the chat JID
     * @param authorJid the message author JID
     * @param metadata  the {@code deviceListMetadata} from the inbound message's
     *                  {@code messageContextInfo}
     * @return the hosted ICDC result
     */
    HostedIcdcResult handleHostedIcdcMetadataInline(Jid chatJid, Jid authorJid, DeviceListMetadata metadata);

    /**
     * Handles an ADV device update extracted from an inbound message's prekey
     * envelope.
     *
     * @apiNote
     * The Signal session-creation path calls this once it has decrypted a prekey
     * message: the embedded identity key for the sender's device is forwarded
     * along with the device's signed metadata so the cached device list and
     * Signal identity store both converge.
     *
     * @param deviceJid    the device JID
     * @param rawId        the raw device identifier
     * @param timestamp    the device timestamp (Unix seconds)
     * @param keyIndex     the device key index
     * @param identityKey  the 32-byte raw device identity key
     * @param accountType  the device account encryption type
     */
    void handleADVDeviceUpdateForMessage(
            Jid deviceJid,
            String rawId,
            long timestamp,
            int keyIndex,
            byte[] identityKey,
            ADVEncryptionType accountType
    );

    /**
     * Extracts and validates the local signed device identity from a
     * {@code <device-identity>} pairing node.
     *
     * @apiNote
     * Called once during pairing, when the server's {@code pair-success}
     * envelope carries the device-identity HMAC and signed payload. Returns the
     * validated identity (which carries the locally-generated device signature)
     * on success; returns empty when the payload fails HMAC or signature checks
     * so the caller can abort pairing.
     *
     * @param deviceIdentityNode the {@code <device-identity>} pairing node
     * @return the validated signed device identity, or empty when invalid
     */
    Optional<ADVSignedDeviceIdentity> extractAndValidateLocalSignedDeviceIdentity(Node deviceIdentityNode);

    /**
     * Persists the local device identity emitted from a successful pair-success
     * exchange.
     *
     * @apiNote
     * Final pairing step: takes the device JID and the account signature key
     * recovered from the validated {@link ADVSignedDeviceIdentity} and stores
     * them on the local store so subsequent sends can sign with them.
     *
     * @param deviceJid             the local device JID
     * @param accountSignatureKey   the pairing account signature key
     */
    void persistLocalDeviceIdentityFromPairSuccess(Jid deviceJid, byte[] accountSignatureKey);
}
