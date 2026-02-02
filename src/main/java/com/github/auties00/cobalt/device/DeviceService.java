package com.github.auties00.cobalt.device;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.fanout.DeviceFanoutCalculator;
import com.github.auties00.cobalt.device.fanout.DeviceGroupFanoutResult;
import com.github.auties00.cobalt.device.adv.DeviceADVCheckScheduler;
import com.github.auties00.cobalt.device.fanout.DevicePhashCalculator;
import com.github.auties00.cobalt.device.fanout.DevicePhashVersion;
import com.github.auties00.cobalt.device.model.DeviceInfo;
import com.github.auties00.cobalt.device.model.DeviceList;
import com.github.auties00.cobalt.device.model.DeviceListHashInfo;
import com.github.auties00.cobalt.device.model.PendingDeviceSync;
import com.github.auties00.cobalt.device.util.DeviceConstants;
import com.github.auties00.cobalt.device.util.DeviceExpectedTsUtils;
import com.github.auties00.cobalt.device.stanza.DeviceListResult;
import com.github.auties00.cobalt.device.stanza.DeviceUSyncQueryBuilder;
import com.github.auties00.cobalt.device.stanza.DeviceUSyncResponseParser;
import com.github.auties00.cobalt.model.chat.ChatParticipant;
import com.github.auties00.cobalt.model.info.ChatMessageInfoBuilder;
import com.github.auties00.cobalt.model.info.MessageInfoStubType;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.model.ChatMessageKey;
import com.github.auties00.cobalt.model.message.model.ChatMessageKeyBuilder;
import com.github.auties00.cobalt.model.message.model.MessageStatus;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.concurrent.StructuredTaskScope.Subtask;

/**
 * Service for managing device lists and calculating fanout for messages.
 * Uses WhatsAppStore for integrated caching and persistence.
 */
public final class DeviceService {
    private static final System.Logger LOGGER = System.getLogger("DeviceService");

    private static final Joiner<List<DeviceListResult>, List<DeviceListResult>> JOINER = new Joiner<>() {
        private final List<Subtask<? extends List<DeviceListResult>>> subtasks = new ArrayList<>();

        @Override
        public boolean onFork(Subtask<? extends List<DeviceListResult>> subtask) {
            Objects.requireNonNull(subtask, "subtask cannot be null");

            if(subtask.state() != Subtask.State.UNAVAILABLE) {
                throw new IllegalStateException("Subtask should not be available");
            }

            subtasks.add(subtask);
            return false;
        }

        @Override
        public boolean onComplete(Subtask<? extends List<DeviceListResult>> subtask) {
            Objects.requireNonNull(subtask, "subtask cannot be null");

            return switch (subtask.state()) {
                case UNAVAILABLE -> throw new IllegalStateException("Subtask is not completed");
                case SUCCESS -> true;
                case FAILED -> false;
            };
        }

        @Override
        public List<DeviceListResult> result() {
            return subtasks.stream()
                    .flatMap(subtask -> subtask.get().stream())
                    .toList();
        }
    };

    private final WhatsAppClient client;
    private final WhatsAppStore store;
    private final ConcurrentHashMap<Jid, CompletableFuture<DeviceList>> pendingFetches;
    private final DeviceADVCheckScheduler advCheckScheduler;
    private final ABPropsService abPropsService;

    public DeviceService(WhatsAppClient client, ABPropsService abPropsService) {
        this.client = client;
        this.store = client.store();
        this.abPropsService = abPropsService;
        this.pendingFetches = new ConcurrentHashMap<>();
        this.advCheckScheduler = new DeviceADVCheckScheduler(client, this, abPropsService);
    }

    /**
     * Gets device lists for multiple users, batching where possible.
     * Uses WhatsAppStore cache before querying the server.
     * Handles PN/LID alternate identity merging and fallback to primary device.
     *
     * @param userJids the user JIDs
     * @return list of devices
     */
    public List<DeviceList> getDeviceLists(Collection<Jid> userJids) {
        var result = new ArrayList<DeviceList>();
        var missingJids = new ArrayList<Jid>();

        // Check WhatsAppStore cache first
        for (var jid : userJids) {
            var cached = store.findDeviceList(jid);
            if (cached.isPresent()) {
                var deviceList = cached.get();
                // Feature 6: If cached list is deleted but not due to hosted transition, fallback to primary
                if (deviceList.deleted() && !deviceList.deletedChangedToHost()) {
                    var fallback = DeviceList.primaryOnly(jid);
                    store.addDeviceList(fallback);
                    result.add(fallback);
                } else {
                    result.add(deviceList);
                }
            } else {
                missingJids.add(jid);
            }
        }

        // Fetch missing device lists from server
        if (!missingJids.isEmpty()) {
            var fetched = fetchDeviceListsFromServer(missingJids);
            for (var deviceList : fetched) {
                store.addDeviceList(deviceList);
                result.add(deviceList);
            }
        }

        // Feature 5: Merge PN/LID alternate device lists
        return mergeAlternateDeviceLists(result);
    }

    /**
     * Gets device lists for the specified users with phash pre-check optimization.
     * <p>
     * Feature 13: Per WhatsApp Web, if the locally computed phash from cached device lists
     * matches the provided expectedPhash, the server sync is skipped entirely.
     *
     * @param userJids      the user JIDs to get device lists for
     * @param expectedPhash optional phash to compare against local device list
     * @return the device lists
     */
    public List<DeviceList> getDeviceLists(Collection<Jid> userJids, String expectedPhash) {
        // Feature 13: If phash is provided, check if local phash matches
        if (expectedPhash != null && !expectedPhash.isEmpty()) {
            // Get cached device lists for all requested JIDs
            var cachedLists = new ArrayList<DeviceList>();
            for (var jid : userJids) {
                var cached = store.findDeviceList(jid);
                cached.ifPresent(cachedLists::add);
            }

            // If we have all cached lists, compute local phash and compare
            if (cachedLists.size() == userJids.size()) {
                try {
                    // Extract device JIDs from cached lists
                    var allDeviceJids = new HashSet<Jid>();
                    for (var deviceList : cachedLists) {
                        allDeviceJids.addAll(deviceList.deviceJids());
                    }

                    // Compute local phash
                    var localPhash = DevicePhashCalculator.calculate(allDeviceJids, DevicePhashVersion.V2, false);
                    if (localPhash.equals(expectedPhash)) {
                        LOGGER.log(System.Logger.Level.DEBUG, "Phash pre-check passed, skipping device sync");
                        return mergeAlternateDeviceLists(cachedLists);
                    }
                } catch (NoSuchAlgorithmException e) {
                    // Fall through to normal sync
                }
            }
        }

        // Fall through to normal sync
        return getDeviceLists(userJids);
    }

    /**
     * Merges device lists for users who have both PN and LID identities.
     * Deduplicates by device ID, with PN version taking precedence.
     */
    private List<DeviceList> mergeAlternateDeviceLists(List<DeviceList> deviceLists) {
        var mergedMap = new LinkedHashMap<Jid, DeviceList>();

        for (var deviceList : deviceLists) {
            var userJid = deviceList.userJid();
            var canonicalJid = userJid;

            // Resolve to canonical JID (PN preferred)
            if (userJid.hasLidServer()) {
                var phoneJid = store.findPhoneByLid(userJid).orElse(null);
                if (phoneJid != null) {
                    canonicalJid = phoneJid;
                }
            }

            var existing = mergedMap.get(canonicalJid);
            if (existing != null) {
                // Merge: existing (PN) takes precedence over incoming (LID)
                mergedMap.put(canonicalJid, existing.merge(deviceList));
            } else {
                mergedMap.put(canonicalJid, deviceList);
            }
        }

        return new ArrayList<>(mergedMap.values());
    }

    /**
     * Fetches device lists from the server with request deduplication.
     * If a fetch is already in progress for a JID, waits for that result instead of making a duplicate request.
     *
     * @param userJids the user JIDs to fetch
     * @return list of device lists
     */
    private List<DeviceList> fetchDeviceListsFromServer(Collection<Jid> userJids) {
        var result = new ArrayList<DeviceList>();
        var toFetch = new ArrayList<Jid>();

        // Separate JIDs into those with pending requests vs those needing new requests
        for (var jid : userJids) {
            var pendingFuture = pendingFetches.get(jid);
            if (pendingFuture != null) {
                // Wait for existing request to complete
                try {
                    result.add(pendingFuture.join());
                } catch (Exception e) {
                    // If pending request failed, we'll try fetching again
                    toFetch.add(jid);
                }
            } else {
                toFetch.add(jid);
            }
        }

        if (toFetch.isEmpty()) {
            return result;
        }

        // Create futures for new requests
        var futures = new ConcurrentHashMap<Jid, CompletableFuture<DeviceList>>();
        for (var jid : toFetch) {
            var future = new CompletableFuture<DeviceList>();
            pendingFetches.put(jid, future);
            futures.put(jid, future);
        }

        try {
            // Build hash info map from cached device lists for delta updates
            var hashInfos = new HashMap<Jid, DeviceListHashInfo>();
            for (var jid : toFetch) {
                var cached = store.findDeviceList(jid);
                if (cached.isPresent() && !cached.get().isExpired()) {
                    try {
                        hashInfos.put(jid, DeviceListHashInfo.of(cached.get()));
                    } catch (NoSuchAlgorithmException e) {
                        // Skip hash for this JID
                    }
                }
            }

            // Batch fetch from server
            var batches = DeviceUSyncQueryBuilder.build(toFetch, "message", hashInfos);
            List<DeviceListResult> fetchedResults;

            try (var scope = StructuredTaskScope.open(JOINER)) {
                for (var batch : batches) {
                    scope.fork(() -> {
                        var response = client.sendNode(batch);
                        return DeviceUSyncResponseParser.parse(response);
                    });
                }
                fetchedResults = scope.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while fetching device lists", e);
            }

            // Feature 6: Backfill missing device sync entries (LID→PN mapping)
            // If a PN entry was requested but server returned LID entry only, copy LID result to PN
            fetchedResults = backfillMissingDeviceSyncEntries(toFetch, fetchedResults);

            // Process results (full device lists or omitted results)
            var lastADVCheckTime = store.lastAdvCheckTime();
            // Feature 15: Track users with validated key index info for optional identity key prefetch
            var usersWithValidatedKeyIndex = new ArrayList<Jid>();
            for (var deviceResult : fetchedResults) {
                var deviceList = switch (deviceResult) {
                    case DeviceListResult.Full full -> {
                        // Full device list response
                        var newList = full.deviceList();
                        var cachedList = store.findDeviceList(newList.userJid());

                        // Feature 4: Detect rawId change (full identity reset)
                        // If rawId differs, clear all Signal sessions and rebuild from scratch
                        var needsListReset = false;
                        if (cachedList.isPresent() && !cachedList.get().deleted()) {
                            var oldRawId = cachedList.get().rawId();
                            var newRawId = newList.rawId();
                            if (oldRawId != null && newRawId != null && !oldRawId.equals(newRawId)) {
                                needsListReset = true;
                                LOGGER.log(System.Logger.Level.INFO, "Device list rawId changed for {0}: {1} -> {2}, triggering full reset",
                                        newList.userJid(), oldRawId, newRawId);
                                // Clear all Signal sessions for this user's devices
                                cleanupAllSessionsForUser(newList.userJid(), cachedList.get());
                            }
                        }

                        // Feature 9: Detect account type transitions
                        if (cachedList.isPresent() && newList.hasAccountTypeChanged(cachedList.get())) {
                            var oldType = cachedList.get().advAccountType();
                            var newType = newList.advAccountType();
                            handleAccountTypeTransition(newList.userJid(), oldType, newType, cachedList.get());
                            needsListReset = true; // Account type change also requires list reset
                        }

                        // Track expectedTs changes and update tracking fields
                        Long newExpectedTsUpdateTs = null;
                        Long newExpectedTsLastDeviceJobTs = null;
                        Long finalExpectedTs = newList.expectedTs();

                        // Check if we should clear expectedTs based on staleness
                        var newTimestamp = newList.timestamp().toEpochMilli() / 1000; // Convert to seconds
                        if (DeviceExpectedTsUtils.shouldClearExpectedTs(
                                newTimestamp,
                                finalExpectedTs,
                                cachedList.orElse(null),
                                lastADVCheckTime)) {
                            finalExpectedTs = null;
                            newExpectedTsUpdateTs = null;
                            newExpectedTsLastDeviceJobTs = null;
                        } else if (cachedList.isPresent()) {
                            // Check if expectedTs has changed
                            var oldExpectedTs = cachedList.get().expectedTs();
                            if (DeviceExpectedTsUtils.hasExpectedTsChanged(oldExpectedTs, finalExpectedTs)) {
                                // ExpectedTs changed - update tracking fields
                                newExpectedTsUpdateTs = System.currentTimeMillis();
                                newExpectedTsLastDeviceJobTs = lastADVCheckTime;
                            } else {
                                // ExpectedTs unchanged - preserve existing tracking
                                newExpectedTsUpdateTs = cachedList.get().expectedTsUpdateTs();
                                newExpectedTsLastDeviceJobTs = cachedList.get().expectedTsLastDeviceJobTs();
                            }
                        }

                        // Create device list with properly tracked expectedTs fields
                        var trackedList = new DeviceList(
                                newList.userJid(),
                                newList.devices(),
                                newList.timestamp(),
                                newList.expiresAt(),
                                newList.rawId(),
                                newList.deleted(),
                                newList.deletedChangedToHost(),
                                newList.advAccountType(),
                                finalExpectedTs,
                                newExpectedTsLastDeviceJobTs,
                                newExpectedTsUpdateTs,
                                newList.currentIndex(),
                                newList.validIndexes()
                        );

                        // Detect identity changes and cleanup stale sessions (Feature 7)
                        if (cachedList.isPresent()) {
                            var changes = trackedList.mismatch(cachedList.get());
                            if (!changes.identityChangedDevices().isEmpty()) {
                                // Mark devices and cleanup stale Signal sessions
                                for (var changedDevice : changes.identityChangedDevices()) {
                                    store.markIdentityChange(changedDevice);
                                    store.cleanupSignalSessionsForDevice(changedDevice);
                                }

                                // Notify listeners about identity changes
                                for (var listener : client.store().listeners()) {
                                    Thread.startVirtualThread(() ->
                                            listener.onDeviceIdentityChanged(client, trackedList.userJid(), changes.identityChangedDevices())
                                    );
                                }
                            }

                            // Feature 7: Cleanup sessions for removed devices
                            if (!changes.removedDevices().isEmpty()) {
                                for (var removedDevice : changes.removedDevices()) {
                                    store.cleanupSignalSessionsForDevice(removedDevice);
                                }
                            }
                        }

                        // Feature 15: Track users with validated key index info
                        // These are users that had signedKeyIndexBytes and passed signature verification
                        if (!trackedList.validIndexes().isEmpty() || trackedList.currentIndex() > 0) {
                            usersWithValidatedKeyIndex.add(trackedList.userJid());
                        }

                        yield trackedList;
                    }

                    case DeviceListResult.Omitted omitted -> {
                        // Omitted result - server confirmed dhash matches
                        // Per WhatsApp Web: strip to primary-only device list
                        var cachedList = store.findDeviceList(omitted.userJid());
                        if (cachedList.isEmpty()) {
                            // No cached list to update, skip
                            yield null;
                        }

                        var oldList = cachedList.get();
                        var newTimestampMillis = omitted.timestamp() != null
                                ? omitted.timestamp()
                                : oldList.timestamp().toEpochMilli();
                        var newTimestamp = java.time.Instant.ofEpochMilli(newTimestampMillis);
                        var newExpiresAt = newTimestamp.plus(java.time.Duration.ofDays(1));
                        var newExpectedTs = omitted.expectedTs();

                        // Track expectedTs changes
                        Long finalExpectedTs = newExpectedTs;
                        Long newExpectedTsUpdateTs = oldList.expectedTsUpdateTs();
                        Long newExpectedTsLastDeviceJobTs = oldList.expectedTsLastDeviceJobTs();

                        // Check if we should clear expectedTs based on staleness
                        var newTimestampSeconds = newTimestampMillis / 1000;
                        if (DeviceExpectedTsUtils.shouldClearExpectedTs(
                                newTimestampSeconds,
                                finalExpectedTs,
                                oldList,
                                lastADVCheckTime)) {
                            finalExpectedTs = null;
                            newExpectedTsUpdateTs = null;
                            newExpectedTsLastDeviceJobTs = null;
                        } else {
                            // Check if expectedTs has changed
                            var oldExpectedTs = oldList.expectedTs();
                            if (DeviceExpectedTsUtils.hasExpectedTsChanged(oldExpectedTs, newExpectedTs)) {
                                // ExpectedTs changed - update tracking fields
                                newExpectedTsUpdateTs = System.currentTimeMillis();
                                newExpectedTsLastDeviceJobTs = lastADVCheckTime;
                            }
                        }

                        // Feature 5: Per WhatsApp Web, omitted result strips to primary-only
                        var primaryOnlyDevices = List.of(DeviceInfo.ofE2EE(DeviceConstants.PRIMARY_DEVICE_ID, 0));

                        // Feature 5: If advAccountType was HOSTED, reset to E2EE
                        var newAdvAccountType = oldList.advAccountType();
                        if (newAdvAccountType == DeviceInfo.Type.HOSTED) {
                            newAdvAccountType = DeviceInfo.Type.E2EE;
                        }

                        // Create updated device list with primary-only devices
                        var updatedList = new DeviceList(
                                oldList.userJid(),
                                primaryOnlyDevices,
                                newTimestamp,
                                newExpiresAt,
                                oldList.rawId(),
                                oldList.deleted(),
                                oldList.deletedChangedToHost(),
                                newAdvAccountType,
                                finalExpectedTs,
                                newExpectedTsLastDeviceJobTs,
                                newExpectedTsUpdateTs,
                                oldList.currentIndex(),
                                oldList.validIndexes()
                        );

                        yield updatedList;
                    }
                };

                // Store and complete future
                if (deviceList != null) {
                    store.addDeviceList(deviceList);
                    var future = futures.get(deviceList.userJid());
                    if (future != null) {
                        future.complete(deviceList);
                        result.add(deviceList);
                    }
                }
            }

            // Feature 6: Fallback to primary device for remaining unfound JIDs
            for (var entry : futures.entrySet()) {
                if (!entry.getValue().isDone()) {
                    var fallback = DeviceList.primaryOnly(entry.getKey());
                    store.addDeviceList(fallback);
                    entry.getValue().complete(fallback);
                    result.add(fallback);
                    LOGGER.log(System.Logger.Level.DEBUG, "Device list not found for {0}, falling back to primary device", entry.getKey());
                }
            }

            // Feature 15: Log users that had validated key index info (can be used for identity key prefetching)
            // Per WhatsApp Web: after device sync, call getAndStoreIdentityKeys for these users
            // The actual identity key fetching happens on-demand during message sending via MessagePreKeyBundleService
            if (!usersWithValidatedKeyIndex.isEmpty()) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Device sync completed for {0} users with validated key index info",
                        usersWithValidatedKeyIndex.size());
            }

            return result;
        } catch (Exception e) {
            // Save as pending sync for retry on reconnect
            var pending = PendingDeviceSync.of(toFetch, "message");
            store.addPendingDeviceSync(pending);

            // Complete all futures exceptionally
            for (var future : futures.values()) {
                if (!future.isDone()) {
                    future.completeExceptionally(e);
                }
            }

            throw new RuntimeException("Failed to fetch device lists", e);
        } finally {
            // Clean up pending requests
            for (var jid : toFetch) {
                pendingFetches.remove(jid);
            }
        }
    }

    /**
     * Handles an account type transition (E2EE ↔ HOSTED).
     * Cleans up Signal sessions, updates device list, and generates notifications.
     */
    private void handleAccountTypeTransition(Jid userJid, DeviceInfo.Type oldType, DeviceInfo.Type newType, DeviceList oldList) {
        LOGGER.log(System.Logger.Level.INFO, "Account type changed for {0}: {1} -> {2}", userJid, oldType, newType);

        // Cleanup all Signal sessions for old devices
        cleanupAllSessionsForUser(userJid, oldList);

        // If transitioning to HOSTED, mark device list as deleted-changed-to-host
        if (newType == DeviceInfo.Type.HOSTED) {
            store.addDeviceList(DeviceList.deleted(userJid, true));
        }

        // Feature 10: Notify listeners about account type change
        for (var listener : client.store().listeners()) {
            Thread.startVirtualThread(() ->
                    listener.onAccountTypeChanged(client, userJid, oldType, newType)
            );
        }

        // Feature 10: Create system message in chat
        createAccountTypeChangeSystemMessage(userJid, oldType, newType);
    }

    /**
     * Creates a system message in the chat when a contact's account type changes.
     */
    private void createAccountTypeChangeSystemMessage(Jid userJid, DeviceInfo.Type oldType, DeviceInfo.Type newType) {
        var chat = store.findChatByJid(userJid).orElse(null);
        if (chat == null) {
            return;
        }

        // E2EE→HOSTED: messages are no longer end-to-end encrypted
        // HOSTED→E2EE: messages are now end-to-end encrypted
        var stubType = (newType == DeviceInfo.Type.E2EE)
                ? MessageInfoStubType.E2E_ENCRYPTED_NOW
                : MessageInfoStubType.CIPHERTEXT;

        var key = new ChatMessageKeyBuilder()
                .id(ChatMessageKey.randomId(store.clientType()))
                .chatJid(chat.jid())
                .senderJid(userJid)

                .build();
        var message = new ChatMessageInfoBuilder()
                .status(MessageStatus.DELIVERED)
                .timestampSeconds(System.currentTimeMillis() / 1000)
                .key(key)
                .ignore(true)
                .stubType(stubType)
                .senderJid(userJid)
                .build();
        chat.addMessage(message);

        for (var listener : client.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onNewMessage(client, message));
        }
    }

    /**
     * Cleans up all Signal sessions and sender keys for all devices of a user.
     */
    private void cleanupAllSessionsForUser(Jid userJid, DeviceList oldList) {
        for (var device : oldList.devices()) {
            var deviceJid = device.toDeviceJid(userJid.user(), userJid.server());
            store.cleanupSignalSessionsForDevice(deviceJid);
        }
    }

    /**
     * Backfills missing device sync entries when server returns LID-based results but PN was requested.
     * <p>
     * Per WhatsApp Web: if a PN entry was requested but server only returned the LID entry,
     * duplicate the LID result as if it were the PN entry.
     *
     * @param requestedJids the JIDs that were originally requested
     * @param results       the results returned from the server
     * @return the results with backfilled entries
     */
    private List<DeviceListResult> backfillMissingDeviceSyncEntries(
            Set<Jid> requestedJids,
            List<DeviceListResult> results
    ) {
        // Build a map of returned results by JID
        var resultMap = new HashMap<Jid, DeviceListResult>();
        for (var result : results) {
            var jid = switch (result) {
                case DeviceListResult.Full full -> full.deviceList().userJid();
                case DeviceListResult.Omitted omitted -> omitted.userJid();
            };
            resultMap.put(jid, result);
        }

        // Check for missing PN entries that have LID counterparts
        var backfilledResults = new ArrayList<>(results);
        for (var requestedJid : requestedJids) {
            // Skip if we already have a result for this JID
            if (resultMap.containsKey(requestedJid)) {
                continue;
            }

            // Only backfill for PN JIDs (user JIDs)
            if (!requestedJid.hasUserServer()) {
                continue;
            }

            // Try to find LID mapping for this PN
            var lidJid = store.findLidByPhone(requestedJid);
            if (lidJid.isEmpty()) {
                continue;
            }

            // Check if we have a result for the LID
            var lidResult = resultMap.get(lidJid.get());
            if (lidResult == null) {
                continue;
            }

            // Duplicate the LID result as a PN result
            LOGGER.log(System.Logger.Level.DEBUG, "Backfilling device list for {0} from LID {1}",
                    requestedJid, lidJid.get());

            var backfilledResult = switch (lidResult) {
                case DeviceListResult.Full full -> {
                    // Create a new DeviceList with the PN JID
                    var originalList = full.deviceList();
                    var backfilledList = new DeviceList(
                            requestedJid,
                            originalList.devices(),
                            originalList.timestamp(),
                            originalList.expiresAt(),
                            originalList.rawId(),
                            originalList.deleted(),
                            originalList.deletedChangedToHost(),
                            originalList.advAccountType(),
                            originalList.expectedTs(),
                            originalList.expectedTsLastDeviceJobTs(),
                            originalList.expectedTsUpdateTs(),
                            originalList.currentIndex(),
                            originalList.validIndexes()
                    );
                    yield new DeviceListResult.Full(backfilledList);
                }
                case DeviceListResult.Omitted omitted -> new DeviceListResult.Omitted(
                        requestedJid,
                        omitted.timestamp(),
                        omitted.expectedTs()
                );
            };

            backfilledResults.add(backfilledResult);
        }

        return backfilledResults;
    }

    /**
     * Clears the device list cache in WhatsAppStore.
     */
    public void clearCache() {
        store.clearDeviceLists();
    }

    /**
     * Gets the last ADV check time from WhatsAppStore.
     *
     * @return the last check time in epoch millis, or null if never checked
     */
    public Long lastAdvCheckTime() {
        return store.lastAdvCheckTime();
    }

    /**
     * Updates the ADV check time in WhatsAppStore.
     */
    public void updateAdvCheckTime() {
        store.updateAdvCheckTime();
    }

    /**
     * Starts the ADV device info check scheduler.
     * Should be called after client connects.
     */
    public void startAdvCheckScheduler() {
        advCheckScheduler.start();
    }

    /**
     * Stops the ADV device info check scheduler.
     * Should be called before client disconnects.
     */
    public void stopAdvCheckScheduler() {
        advCheckScheduler.close();
    }

    /**
     * Retries all pending device syncs.
     * Should be called after client reconnects.
     */
    public void retryPendingSyncs() {
        var pending = store.pendingDevicesSyncs();
        for (var sync : pending) {
            // Remove expired syncs
            if (sync.isExpired()) {
                store.removePendingDeviceSync(sync);
                continue;
            }

            // Skip syncs that have exceeded max retries
            if (!sync.shouldRetry()) {
                store.removePendingDeviceSync(sync);
                continue;
            }

            // Retry the sync
            try {
                fetchDeviceListsFromServer(sync.userJids());
                store.removePendingDeviceSync(sync);
            } catch (Exception e) {
                // Increment retry count and re-queue
                var retried = sync.nextRetry();
                store.removePendingDeviceSync(sync);
                if (retried.shouldRetry()) {
                    store.addPendingDeviceSync(retried);
                }
            }
        }
    }

    /**
     * Gets the complete fanout for a group message.
     *
     * @param groupJid    the group JID
     * @param myDeviceJid the current device's JID
     * @return the fanout result
     */
    public DeviceGroupFanoutResult getGroupFanout(Jid groupJid, Jid myDeviceJid) {
        var metadata = client.queryGroupOrCommunityMetadata(groupJid);
        var participants = metadata.participants()
                .stream()
                .map(ChatParticipant::jid)
                .toList();
        return getGroupFanout(participants, myDeviceJid);
    }

    /**
     * Gets the complete fanout for a group message given participants.
     *
     * @param participants     the group participants
     * @param myDeviceJid      the current device's JID
     * @return the fanout result
     */
    public DeviceGroupFanoutResult getGroupFanout(Collection<Jid> participants, Jid myDeviceJid) {
        try {
            var deviceLists = getDeviceLists(participants);
            var fanoutDevices = DeviceFanoutCalculator.calculate(myDeviceJid, deviceLists);

            // Filter out devices with unconfirmed identity changes
            var changedIdentities = store.unconfirmedIdentityChanges();
            var filteredDevices = DeviceFanoutCalculator.filterIdentityChanges(fanoutDevices, changedIdentities);

            // Use cached phash calculation for better performance
            // Check if open group bot feature is enabled via AB props
            // Both web_ai_group_open_support and ai_group_participation_enabled must be true
            var webAiGroupOpenSupport = abPropsService.getBool(ABProp.WEB_AI_GROUP_OPEN_SUPPORT_AB_PROP_CODE)
                    .orElse(false);
            var aiGroupParticipationEnabled = abPropsService.getBool(ABProp.AI_GROUP_PARTICIPATION_ENABLED_AB_PROP_CODE)
                    .orElse(false);
            var includeMetaBot = webAiGroupOpenSupport && aiGroupParticipationEnabled;
            var phash = DevicePhashCalculator.calculate(filteredDevices, DevicePhashVersion.V2, includeMetaBot);
            return new DeviceGroupFanoutResult(filteredDevices, phash);
        } catch (NoSuchAlgorithmException exception) {
            throw new InternalError("Missing SHA-256 implementation", exception);
        }
    }
}
