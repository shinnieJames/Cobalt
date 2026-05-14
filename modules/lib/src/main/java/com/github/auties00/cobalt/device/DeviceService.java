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
 * Service that orchestrates device-list operations for WhatsApp Multi-Device.
 *
 * <p>Production code uses {@link DefaultDeviceService} which talks to the
 * server via USync queries, the ADV pipeline, and the Signal protocol
 * store. Tests can provide their own implementation to stub fanout,
 * session establishment, and ICDC computation without the full network
 * + crypto stack.
 *
 * <p>The contract surface mirrors the original {@code final class} so
 * existing callers continue to compile unchanged; the only consumer-side
 * difference is that {@code new DeviceService(...)} becomes
 * {@code new DefaultDeviceService(...)}.
 */
public interface DeviceService {
    /**
     * Returns the device lists for the given user JIDs.
     *
     * @param userJids              the user JIDs to query
     * @param context               the USync context string
     * @param expectedPhash         the expected participant hash, or {@code null}
     * @param shouldMergeAltDevices whether to merge alternative device lists
     * @return the device lists
     */
    Set<DeviceList> getDeviceLists(Collection<Jid> userJids, String context, String expectedPhash, boolean shouldMergeAltDevices);

    /**
     * Returns the timestamp of the last ADV expiration check.
     *
     * @return the last check timestamp, or empty when never checked
     */
    Optional<Instant> lastAdvCheckTime();

    /**
     * Updates the ADV check timestamp to the current instant.
     */
    void updateAdvCheckTime();

    /**
     * Starts the daily ADV expiration check scheduler.
     */
    void startAdvCheckScheduler();

    /**
     * Stops the daily ADV expiration check scheduler.
     */
    void stopAdvCheckScheduler();

    /**
     * Retries any device syncs that previously failed.
     */
    void retryPendingSyncs();

    /**
     * Updates device records whose Signal sessions are missing.
     */
    void updateMissingKeyDevices();

    /**
     * Returns the per-device fanout for a 1:1 chat.
     *
     * @param chatJid       the chat JID
     * @param expectedPhash the expected participant hash, or {@code null}
     * @return the device JIDs to fan out to
     */
    Collection<Jid> getUserFanout(Jid chatJid, String expectedPhash);

    /**
     * Returns the per-device fanout for a group chat.
     *
     * @param groupJid       the group JID
     * @param senderDeviceJid the sender device JID
     * @return the fanout result
     */
    DeviceGroupFanoutResult getGroupFanout(Jid groupJid, Jid senderDeviceJid);

    /**
     * Computes the ICDC metadata for the given user JID.
     *
     * @param userJid the user JID
     * @return the ICDC result, or empty when not applicable
     */
    Optional<IcdcResult> computeIcdc(Jid userJid);

    /**
     * Ensures that Signal sessions exist for every device JID supplied.
     *
     * @param deviceJids the device JIDs whose sessions are needed
     * @return the number of new sessions established
     */
    int ensureSessions(Collection<Jid> deviceJids);

    /**
     * Handles a device-list change notification.
     *
     * @param node    the notification node
     * @param action  the action string ({@code "add"}, {@code "remove"})
     * @param userJid the user JID whose device list changed
     */
    void handleDeviceNotification(Node node, String action, Jid userJid);

    /**
     * Synchronises the local user's own device list with the server.
     */
    void syncMyDeviceList();

    /**
     * Synchronises and returns the device lists for the given user JIDs.
     *
     * @param userJids the user JIDs to query
     * @return the synchronised device lists
     */
    List<DeviceList> syncAndGetDeviceList(Collection<Jid> userJids);

    /**
     * Returns the stored device record for the given user JID.
     *
     * @param userJid the user JID
     * @return the device record, or empty when not stored
     */
    Optional<DeviceList> getDeviceRecord(Jid userJid);

    /**
     * Returns the stored device records for the given user JIDs.
     *
     * @param userJids the user JIDs
     * @return the device records
     */
    List<DeviceList> bulkGetDeviceRecord(Collection<Jid> userJids);

    /**
     * Persists or replaces the given device record.
     *
     * @param record the device record
     */
    void createOrReplaceDeviceRecord(DeviceList record);

    /**
     * Persists or replaces the given device records.
     *
     * @param records the device records
     */
    void bulkCreateOrReplaceDeviceRecord(Collection<DeviceList> records);

    /**
     * Returns the device IDs for the given user JIDs.
     *
     * @param userJids              the user JIDs
     * @param shouldMergeAltDevices whether to merge alternative device lists
     * @return the device lists
     */
    List<DeviceList> getDeviceIds(Collection<Jid> userJids, boolean shouldMergeAltDevices);

    /**
     * Returns the device records needed to drive a USync.
     *
     * @param userJids the user JIDs
     * @return the device lists
     */
    List<DeviceList> getDeviceInfoForSync(Collection<Jid> userJids);

    /**
     * Returns whether the given device exists for the given user.
     *
     * @param userJid  the user JID
     * @param deviceId the device id
     * @return {@code true} when the device exists
     */
    boolean hasDevice(Jid userJid, int deviceId);

    /**
     * Returns the local user's own device list.
     *
     * @return the local device list
     */
    DeviceList getMyDeviceList();

    /**
     * Returns every stored device list.
     *
     * @return the device lists
     */
    Collection<DeviceList> getAllDeviceLists();

    /**
     * Handles ICDC metadata extracted from an incoming message.
     *
     * @param senderJid        the sender JID
     * @param recipientUserJid the recipient user JID
     * @param metadata         the ICDC metadata
     */
    void handleICDCData(Jid senderJid, Jid recipientUserJid, DeviceListMetadata metadata);

    /**
     * Handles hosted-account ICDC metadata extracted from an incoming
     * message and returns the validation outcome.
     *
     * @param chatJid   the chat JID
     * @param authorJid the message author JID
     * @param metadata  the ICDC metadata
     * @return the hosted ICDC result
     */
    HostedIcdcResult handleHostedIcdcMetadataInline(Jid chatJid, Jid authorJid, DeviceListMetadata metadata);

    /**
     * Handles an ADV device update extracted from an incoming message.
     *
     * @param deviceJid    the device JID
     * @param rawId        the raw device identifier
     * @param timestamp    the device timestamp
     * @param keyIndex     the key index
     * @param identityKey  the device identity key bytes
     * @param accountType  the account encryption type
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
     * Extracts and validates the local signed device identity from the
     * given {@code <device-identity>} node.
     *
     * @param deviceIdentityNode the device-identity node
     * @return the validated signed device identity, or empty when invalid
     */
    Optional<ADVSignedDeviceIdentity> extractAndValidateLocalSignedDeviceIdentity(Node deviceIdentityNode);

    /**
     * Persists the local device identity emitted from a successful pair-success
     * exchange.
     *
     * @param deviceJid             the local device JID
     * @param accountSignatureKey   the pairing account signature key
     */
    void persistLocalDeviceIdentityFromPairSuccess(Jid deviceJid, byte[] accountSignatureKey);
}
