package com.github.auties00.cobalt.device.key;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.libsignal.SignalSessionCipher;
import com.github.auties00.libsignal.key.SignalIdentityPublicKey;
import com.github.auties00.libsignal.state.SignalPreKeyBundle;
import com.github.auties00.libsignal.state.SignalPreKeyBundleBuilder;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;

/**
 * Fetches Signal pre-key bundles and identity keys for newly-learned devices and installs them
 * into the local Signal store so outgoing messages can be encrypted.
 *
 * @apiNote
 * Wired by {@link com.github.auties00.cobalt.device.DeviceService} to back two surfaces:
 * <ul>
 *   <li>the send path, which calls {@link #ensureSessions(Collection)} (and through it
 *       {@link #fetchAndProcessPreKeyBundles(Collection)}) before encrypting to a device that
 *       Cobalt has not yet talked to;</li>
 *   <li>the post-sync identity-prefetch path, which calls
 *       {@link #fetchAndStoreIdentityKeys(Collection)} so incoming PKMSG decryption does not
 *       need a separate server round-trip to validate peer identities.</li>
 * </ul>
 * Embedders do not normally call this class directly; the {@code DeviceService} fronting it
 * is the public surface.
 */
@WhatsAppWebModule(moduleName = "WAWebFetchPrekeysJob")
@WhatsAppWebModule(moduleName = "WAWebManageE2ESessionsJob")
@WhatsAppWebModule(moduleName = "WAWebGetIdentityKeysJob")
public final class DevicePreKeyHandler {
    /**
     * Maximum number of devices included in a single pre-key IQ batch.
     *
     * @apiNote
     * Caps a single {@code <iq>}'s {@code <user>} child count so partial failures only affect
     * one batch and the parallel scope has more subtasks to dispatch; WA Web's smax schema
     * accepts up to {@code 1e5} users per request, so the limit is a Cobalt-side throttle, not
     * a wire constraint.
     */
    private static final int MAX_DEVICES_PER_QUERY = 100;

    /**
     * XML namespace used on pre-key and identity-key IQs ({@code <iq xmlns="encrypt">}).
     */
    private static final String ENCRYPT_XMLNS = "encrypt";

    /**
     * The {@link WhatsAppClient} used for store access and IQ dispatch.
     */
    private final WhatsAppClient client;

    /**
     * The {@link SignalSessionCipher} used to materialise sessions from fetched bundles.
     */
    private final SignalSessionCipher sessionCipher;

    /**
     * Tracks in-flight pre-key fetches keyed by device {@link Jid} so concurrent senders to the
     * same device share a single IQ instead of issuing duplicates.
     */
    private final ConcurrentHashMap<Jid, CompletableFuture<SignalPreKeyBundle>> inFlightRequests = new ConcurrentHashMap<>();

    /**
     * Constructs a handler bound to the given client and session cipher.
     *
     * @apiNote
     * Wired by {@link com.github.auties00.cobalt.device.DeviceService}; embedders do not
     * normally instantiate this directly.
     *
     * @param client        the WhatsApp client used for store access and IQ dispatch
     * @param sessionCipher the Signal session cipher used to materialise sessions
     * @throws NullPointerException if any argument is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebManageE2ESessionsJob",
            exports = "ensureE2ESessions",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public DevicePreKeyHandler(WhatsAppClient client, SignalSessionCipher sessionCipher) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.sessionCipher = Objects.requireNonNull(sessionCipher, "sessionCipher cannot be null");
    }

    /**
     * Carries the result of a pre-key fetch and session-establishment pass.
     *
     * @apiNote
     * Combines the parsed {@link SignalPreKeyBundle}s with the {@code depletedPrekeyCount}
     * that downstream WAM emission needs. The WA Web counterparts return two adjacent tuples:
     * {@code WAWebManageE2ESessionsJob.ensureE2ESessions} yields
     * {@code {missedPrekeyCount, depletedPrekeyCount, deletedDevices}} and
     * {@code WAWebProcessKeyBundle.processKeyBundles} yields
     * {@code {depletedPrekeyCount, processedPrekeyCount}}; Cobalt keeps the two fields it
     * actually consumes downstream.
     *
     * @param bundles             the pre-key bundles keyed by device JID; empty when the fetch failed
     * @param depletedPrekeyCount the number of devices for which the server returned no
     *                            one-time pre-key (server-side pool depleted for that
     *                            non-bot device)
     */
    public record PreKeyFetchResult(Map<Jid, SignalPreKeyBundle> bundles, int depletedPrekeyCount) {
        /**
         * Canonical constructor that copies {@code bundles} into an unmodifiable map and
         * tolerates a {@code null} input.
         *
         * @apiNote
         * Defensive copy ensures the record's map cannot be mutated after construction; passing
         * {@code null} collapses to an empty map rather than throwing.
         *
         * @param bundles             the pre-key bundles keyed by device JID, or {@code null}
         * @param depletedPrekeyCount the depleted one-time pre-key count
         */
        public PreKeyFetchResult {
            bundles = bundles == null ? Map.of() : Map.copyOf(bundles);
        }
    }

    /**
     * Fetches pre-key bundles for the specified devices and establishes Signal sessions, with
     * the identity-reason flag off.
     *
     * @apiNote
     * Convenience overload equivalent to
     * {@link #fetchAndProcessPreKeyBundles(Collection, boolean) fetchAndProcessPreKeyBundles(deviceJids, false)};
     * use this on the regular send path. The two-argument variant is reserved for identity
     * prefetches.
     *
     * @param deviceJids the device JIDs to fetch pre-keys for
     * @return the {@link PreKeyFetchResult} carrying the resolved bundles and the depleted count
     */
    @WhatsAppWebExport(moduleName = "WAWebFetchPrekeysJob",
            exports = "fetchPrekeys",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public PreKeyFetchResult fetchAndProcessPreKeyBundles(Collection<Jid> deviceJids) {
        return fetchAndProcessPreKeyBundles(deviceJids, false);
    }

    /**
     * Fetches pre-key bundles for the specified devices and establishes Signal sessions.
     *
     * @apiNote
     * Sets the per-user {@code reason="identity"} attribute on each {@code <user>} child when
     * {@code hasUserReasonIdentity} is true so the server can distinguish identity-verification
     * prefetches from regular send-path fetches; pass {@code false} for the send path. The
     * pass deduplicates concurrent fetches for the same device via {@link #inFlightRequests},
     * batches new devices in groups of at most {@value #MAX_DEVICES_PER_QUERY} per IQ, and
     * dispatches the batches in parallel across virtual threads.
     *
     * @implNote
     * This implementation sorts the resolved bundles so primary devices (device id {@code 0})
     * land in the session cipher before companion devices, avoiding shared-state races inside
     * the Signal session install path. Devices the server omits from the response resolve to a
     * {@code null} future and are dropped silently from the result. Failures inside an
     * in-flight future leave the device without a bundle but do not throw; the dedicated
     * exception path only fires on virtual-thread interruption.
     *
     * @param deviceJids            the device JIDs to fetch pre-keys for
     * @param hasUserReasonIdentity whether to set {@code reason="identity"} on each {@code <user>} node
     * @return the {@link PreKeyFetchResult} carrying the resolved bundles and the depleted count
     * @throws NullPointerException if {@code deviceJids} is {@code null}
     * @throws RuntimeException     wrapping {@link InterruptedException} when the virtual thread is interrupted while waiting for the batches
     */
    @WhatsAppWebExport(moduleName = "WAWebFetchPrekeysJob",
            exports = "fetchPrekeys",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebManageE2ESessionsJob",
            exports = "ensureE2ESessions",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public PreKeyFetchResult fetchAndProcessPreKeyBundles(Collection<Jid> deviceJids, boolean hasUserReasonIdentity) {
        Objects.requireNonNull(deviceJids, "deviceJids cannot be null");

        if (deviceJids.isEmpty()) {
            return new PreKeyFetchResult(Map.of(), 0);
        }

        var devicesNeedingFetch = new ArrayList<Jid>();
        var existingFutures = new HashMap<Jid, CompletableFuture<SignalPreKeyBundle>>();

        for (var deviceJid : deviceJids) {
            var existingFuture = inFlightRequests.get(deviceJid);
            if (existingFuture != null) {
                existingFutures.put(deviceJid, existingFuture);
            } else {
                devicesNeedingFetch.add(deviceJid);
            }
        }

        var newFutures = new HashMap<Jid, CompletableFuture<SignalPreKeyBundle>>();
        for (var deviceJid : devicesNeedingFetch) {
            var future = new CompletableFuture<SignalPreKeyBundle>();
            var existing = inFlightRequests.putIfAbsent(deviceJid, future);
            if (existing != null) {
                existingFutures.put(deviceJid, existing);
            } else {
                newFutures.put(deviceJid, future);
            }
        }

        var allBundles = new HashMap<Jid, SignalPreKeyBundle>();
        var depletedPrekeyCount = 0;

        if (!newFutures.isEmpty()) {
            var devicesToFetch = new ArrayList<>(newFutures.keySet());
            var batches = batchDevices(devicesToFetch);

            try (var scope = StructuredTaskScope.open()) {
                var subtasks = new ArrayList<Subtask<PreKeyBatchResult>>();
                for (var batch : batches) {
                    subtasks.add(scope.fork(() -> fetchPreKeyBatch(batch, hasUserReasonIdentity)));
                }
                scope.join();

                for (var subtask : subtasks) {
                    if (subtask.state() == Subtask.State.SUCCESS) {
                        var batchResult = subtask.get();
                        depletedPrekeyCount += batchResult.depletedPrekeyCount();
                        var fetchedBundles = batchResult.bundles();
                        allBundles.putAll(fetchedBundles);

                        for (var entry : fetchedBundles.entrySet()) {
                            var future = newFutures.get(entry.getKey());
                            if (future != null) {
                                future.complete(entry.getValue());
                            }
                        }
                    }
                }

                for (var entry : newFutures.entrySet()) {
                    if (!entry.getValue().isDone()) {
                        entry.getValue().complete(null);
                    }
                }
            } catch (InterruptedException e) {
                for (var future : newFutures.values()) {
                    future.completeExceptionally(e);
                }
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while fetching prekey bundles", e);
            } finally {
                for (var deviceJid : newFutures.keySet()) {
                    inFlightRequests.remove(deviceJid);
                }
            }
        }

        for (var entry : existingFutures.entrySet()) {
            try {
                var bundle = entry.getValue().join();
                if (bundle != null) {
                    allBundles.put(entry.getKey(), bundle);
                }
            } catch (Exception e) {
                // A failed in-flight fetch leaves the device without a bundle.
            }
        }

        var sortedEntries = allBundles.entrySet().stream()
                .sorted((a, b) -> {
                    var deviceA = a.getKey().device();
                    var deviceB = b.getKey().device();
                    var isPrimaryA = deviceA == 0;
                    var isPrimaryB = deviceB == 0;
                    if (isPrimaryA && !isPrimaryB) return -1;
                    if (!isPrimaryA && isPrimaryB) return 1;
                    return Integer.compare(deviceA, deviceB);
                })
                .toList();

        for (var entry : sortedEntries) {
            var deviceJid = entry.getKey();
            var bundle = entry.getValue();
            sessionCipher.process(deviceJid.toSignalAddress(), bundle);
        }

        return new PreKeyFetchResult(allBundles, depletedPrekeyCount);
    }

    /**
     * Carries one batch's worth of parsed bundles together with the depleted count surfaced by
     * that batch's response.
     *
     * @apiNote
     * Internal-only return type for {@link #fetchPreKeyBatch}; aggregated into the top-level
     * {@link PreKeyFetchResult} by {@link #fetchAndProcessPreKeyBundles(Collection, boolean)}.
     *
     * @param bundles             the pre-key bundles parsed from this batch's response
     * @param depletedPrekeyCount the depleted one-time pre-key count for this batch
     */
    private record PreKeyBatchResult(Map<Jid, SignalPreKeyBundle> bundles, int depletedPrekeyCount) {
    }

    /**
     * Dispatches a single pre-key IQ for one batch of devices and parses the response.
     *
     * @apiNote
     * One subtask of the {@link StructuredTaskScope} in
     * {@link #fetchAndProcessPreKeyBundles(Collection, boolean)}; runs on its own virtual
     * thread so batches dispatch in parallel.
     *
     * @param deviceJids            the devices included in this batch
     * @param hasUserReasonIdentity whether to set {@code reason="identity"} on each {@code <user>} node
     * @return the parsed bundles and the depleted count for this batch
     */
    @WhatsAppWebExport(moduleName = "WAWebFetchPrekeysJob",
            exports = "fetchPrekeys",
            adaptation = WhatsAppAdaptation.DIRECT)
    private PreKeyBatchResult fetchPreKeyBatch(List<Jid> deviceJids, boolean hasUserReasonIdentity) {
        var query = buildPreKeyQuery(deviceJids, hasUserReasonIdentity);
        var response = client.sendNode(query);
        return parsePreKeyResponse(response);
    }

    /**
     * Splits a device list into batches of at most {@value #MAX_DEVICES_PER_QUERY} entries.
     *
     * @apiNote
     * Helper for {@link #fetchAndProcessPreKeyBundles(Collection, boolean)}; preserves the
     * input order so the eventual session-install order is stable.
     *
     * @param deviceJids the devices to batch
     * @return the list of batches in input order
     */
    @WhatsAppWebExport(moduleName = "WAWebFetchPrekeysJob",
            exports = "fetchPrekeys",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private List<List<Jid>> batchDevices(Collection<Jid> deviceJids) {
        var batches = new ArrayList<List<Jid>>();
        var currentBatch = new ArrayList<Jid>();

        for (var jid : deviceJids) {
            currentBatch.add(jid);
            if (currentBatch.size() >= MAX_DEVICES_PER_QUERY) {
                batches.add(new ArrayList<>(currentBatch));
                currentBatch.clear();
            }
        }

        if (!currentBatch.isEmpty()) {
            batches.add(currentBatch);
        }

        return batches;
    }

    /**
     * Builds the pre-key query IQ stanza for one batch of devices.
     *
     * @apiNote
     * Produces an IQ of shape
     * {@snippet :
     *     <iq xmlns="encrypt" type="get" to="s.whatsapp.net">
     *       <key>
     *         <user jid="..." reason="identity"/>
     *         <user jid="..."/>
     *       </key>
     *     </iq>
     * }
     * The {@code reason="identity"} attribute is included only when the caller flagged the
     * batch as an identity prefetch so the server can route the request differently.
     *
     * @param deviceJids            the device JIDs to query
     * @param hasUserReasonIdentity whether to include {@code reason="identity"} on every {@code <user>} node
     * @return the IQ node builder ready to send
     */
    @WhatsAppWebExport(moduleName = "WAWebFetchPrekeysJob",
            exports = "fetchPrekeys",
            adaptation = WhatsAppAdaptation.DIRECT)
    private NodeBuilder buildPreKeyQuery(List<Jid> deviceJids, boolean hasUserReasonIdentity) {
        var userNodes = deviceJids.stream()
                .map(jid -> {
                    var builder = new NodeBuilder()
                            .description("user")
                            .attribute("jid", jid);
                    if (hasUserReasonIdentity) {
                        builder.attribute("reason", "identity");
                    }
                    return builder.build();
                })
                .toList();

        var keyNode = new NodeBuilder()
                .description("key")
                .content(userNodes)
                .build();

        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", ENCRYPT_XMLNS)
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(keyNode);
    }

    /**
     * Parses the pre-key response into {@link SignalPreKeyBundle}s keyed by device {@link Jid}
     * and tallies the depleted one-time pre-key count.
     *
     * @apiNote
     * The response carries a {@code <list>} envelope with one {@code <user>} entry per
     * queried device. An entry that fails to parse is logged and skipped so a single malformed
     * device does not fail the batch.
     *
     * @implNote
     * This implementation matches WA Web's {@code WAWebProcessKeyBundle.splitKeyBundles}
     * depletion-counting rule: a {@code <user>} entry contributes to {@code depletedPrekeyCount}
     * only when it has no {@code <key>} child and is not a bot JID. The count drives
     * downstream WAM telemetry (Cobalt does not currently emit
     * {@code PostPrekeysDepletionMetric}; this field is exposed for embedders that mirror
     * WA Web's WAM surface).
     *
     * @param response the IQ response node
     * @return the bundles and depleted count for this batch
     */
    @WhatsAppWebExport(moduleName = "WAWebFetchPrekeysJob",
            exports = "fetchPrekeys",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebProcessKeyBundle",
            exports = "splitKeyBundles",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private PreKeyBatchResult parsePreKeyResponse(Node response) {
        var result = new HashMap<Jid, SignalPreKeyBundle>();
        var depletedPrekeyCount = 0;

        var listNode = response.getChild("list");
        if (listNode.isEmpty()) {
            return new PreKeyBatchResult(result, 0);
        }

        for (var userNode : listNode.get().getChildren("user")) {
            try {
                var deviceJid = userNode.getRequiredAttributeAsJid("jid");

                if (userNode.getChild("key").isEmpty() && !deviceJid.isBot()) {
                    depletedPrekeyCount++;
                }

                var bundle = parseUserPreKeyBundle(userNode);
                if (bundle != null) {
                    result.put(deviceJid, bundle);
                }
            } catch (Exception e) {
                var jid = userNode.getAttribute("jid").map(Object::toString).orElse("unknown");
                System.err.println("Failed to parse prekey bundle for " + jid + ": " + e.getMessage());
            }
        }

        return new PreKeyBatchResult(result, depletedPrekeyCount);
    }

    /**
     * Parses a single user's pre-key bundle from a response {@code <user>} child.
     *
     * @apiNote
     * Extracts the registration id, identity key, signed pre-key (id, public, signature) and
     * optional one-time pre-key (id, public) into a {@link SignalPreKeyBundle}. The one-time
     * pre-key is genuinely optional; when the server's pool is exhausted only the signed
     * pre-key block is returned.
     *
     * @implNote
     * This implementation decodes the registration id (4 bytes) and the pre-key ids (3 bytes
     * each) via {@link #convertBytesToUint(byte[], int)} because the wire format uses raw
     * big-endian unsigned bytes rather than an ASCII number string.
     *
     * @param userNode the {@code <user>} node for this device
     * @return the parsed pre-key bundle
     * @throws IllegalArgumentException if any required field is missing
     */
    @WhatsAppWebExport(moduleName = "WAWebFetchPrekeysJob",
            exports = "fetchPrekeys",
            adaptation = WhatsAppAdaptation.DIRECT)
    private SignalPreKeyBundle parseUserPreKeyBundle(Node userNode) {
        var registrationId = userNode.getChild("registration")
                .flatMap(Node::toContentBytes)
                .map(bytes -> convertBytesToUint(bytes, 4))
                .orElseThrow(() -> new IllegalArgumentException("Missing registration ID"));

        var identityKey = userNode.getChild("identity")
                .flatMap(Node::toContentBytes)
                .map(SignalIdentityPublicKey::ofDirect)
                .orElseThrow(() -> new IllegalArgumentException("Missing identity key"));

        var signedPreKeyNode = userNode.getChild("skey")
                .orElseThrow(() -> new IllegalArgumentException("Missing signed prekey"));

        var signedPreKeyId = signedPreKeyNode.getChild("id")
                .flatMap(Node::toContentBytes)
                .map(bytes -> convertBytesToUint(bytes, 3))
                .orElseThrow(() -> new IllegalArgumentException("Missing signed prekey ID"));

        var signedPreKeyValue = signedPreKeyNode.getChild("value")
                .flatMap(Node::toContentBytes)
                .map(SignalIdentityPublicKey::ofDirect)
                .orElseThrow(() -> new IllegalArgumentException("Missing signed prekey value"));

        var signedPreKeySignature = signedPreKeyNode.getChild("signature")
                .flatMap(Node::toContentBytes)
                .orElseThrow(() -> new IllegalArgumentException("Missing signed prekey signature"));

        var builder = new SignalPreKeyBundleBuilder()
                .registrationId(registrationId)
                .deviceId(0)
                .signedPreKeyId(signedPreKeyId)
                .signedPreKeyPublic(signedPreKeyValue)
                .signedPreKeySignature(signedPreKeySignature)
                .identityKey(identityKey);

        var preKeyNode = userNode.getChild("key");
        if (preKeyNode.isPresent()) {
            var preKeyId = preKeyNode.get().getChild("id")
                    .flatMap(Node::toContentBytes)
                    .map(bytes -> convertBytesToUint(bytes, 3))
                    .orElse(null);

            var preKeyValue = preKeyNode.get().getChild("value")
                    .flatMap(Node::toContentBytes)
                    .map(SignalIdentityPublicKey::ofDirect)
                    .orElse(null);

            if (preKeyId != null && preKeyValue != null) {
                builder.preKeyId(preKeyId);
                builder.preKeyPublic(preKeyValue);
            }
        }

        return builder.build();
    }

    /**
     * Converts a big-endian unsigned byte array to an int.
     *
     * @apiNote
     * Helper for {@link #parseUserPreKeyBundle} that decodes the raw byte encoding used by
     * every smax {@code KeyIDMixin} and {@code RegistrationIDMixin} parser; registration ids
     * occupy 4 bytes, signed-pre-key and one-time-pre-key ids occupy 3 bytes.
     *
     * @implNote
     * This implementation accumulates {@code n = (n << 8) | (bytes[i] & 0xFF)} for the first
     * {@code byteCount} bytes, matching {@code WAParsableXmlNode.convertBytesToUint} exactly.
     *
     * @param bytes     the raw content bytes from the wire-format node
     * @param byteCount the number of leading bytes to interpret
     * @return the decoded big-endian unsigned integer
     * @throws IllegalArgumentException if {@code bytes} is {@code null} or holds fewer than {@code byteCount} bytes
     */
    @WhatsAppWebExport(moduleName = "WAParsableXmlNode",
            exports = "convertBytesToUint",
            adaptation = WhatsAppAdaptation.DIRECT)
    private static int convertBytesToUint(byte[] bytes, int byteCount) {
        if (bytes == null || bytes.length < byteCount) {
            throw new IllegalArgumentException("Expected " + byteCount + " bytes, got " + (bytes == null ? 0 : bytes.length));
        }
        var n = 0;
        for (var i = 0; i < byteCount; i++) {
            n = (n << 8) | (bytes[i] & 0xFF);
        }
        return n;
    }

    /**
     * Returns the subset of the given devices that do not yet have a cached Signal session.
     *
     * @apiNote
     * Driven by {@link #ensureSessions(Collection)} to determine which devices still need a
     * pre-key fetch before they can be encrypted to; mirrors the
     * {@code Signal.Session.hasSignalSessions} check inside
     * {@code WAWebManageE2ESessionsJob.ensureE2ESessions}.
     *
     * @param deviceJids the device JIDs to check
     * @return the devices for which {@link com.github.auties00.cobalt.store.WhatsAppStore#findSessionByAddress} returned empty
     */
    @WhatsAppWebExport(moduleName = "WAWebManageE2ESessionsJob",
            exports = "ensureE2ESessions",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public List<Jid> findDevicesNeedingSessions(Collection<Jid> deviceJids) {
        var store = client.store();
        var result = new ArrayList<Jid>();

        for (var deviceJid : deviceJids) {
            var address = deviceJid.toSignalAddress();
            if (store.findSessionByAddress(address).isEmpty()) {
                result.add(deviceJid);
            }
        }

        return result;
    }

    /**
     * Ensures Signal sessions exist for the specified devices, fetching and installing any
     * missing bundles.
     *
     * @apiNote
     * The canonical send-path entry point: callers pass the fanout from
     * {@link com.github.auties00.cobalt.device.fanout.DeviceFanoutCalculator} and the returned
     * count drives the WAM {@code PostPrekeysDepletionMetric} branch on the caller side (WA
     * Web emits it through {@code WAWebPostPrekeysDepletionMetric.maybePostPrekeysDepletionMetric}).
     *
     * @param deviceJids the device JIDs to ensure sessions for
     * @return the number of devices in the response whose one-time pre-key pool was depleted
     *         (no {@code <key>} child returned for a non-bot device); zero when every device
     *         already had a cached session
     */
    @WhatsAppWebExport(moduleName = "WAWebManageE2ESessionsJob",
            exports = "ensureE2ESessions",
            adaptation = WhatsAppAdaptation.DIRECT)
    public int ensureSessions(Collection<Jid> deviceJids) {
        var devicesNeedingSessions = findDevicesNeedingSessions(deviceJids);
        if (devicesNeedingSessions.isEmpty()) {
            return 0;
        }

        return fetchAndProcessPreKeyBundles(devicesNeedingSessions).depletedPrekeyCount();
    }

    /**
     * Prefetches identity keys for the specified users and stores them as trusted identities.
     *
     * @apiNote
     * Called by {@link com.github.auties00.cobalt.device.DeviceService} after a successful
     * device sync for every user whose validated signed-key-index list contains at least one
     * entry, so incoming PKMSG decryption can validate peer identities without a separate
     * server round-trip. The request shape is
     * {@snippet :
     *     <iq xmlns="encrypt" type="get" to="s.whatsapp.net">
     *       <identity>
     *         <user jid="...primary-device JID..."/>
     *         ...
     *       </identity>
     *     </iq>
     * }
     * with one {@code <user>} per user JID, each targeting that user's primary device.
     *
     * @implNote
     * This implementation skips users whose primary-device identity is already cached, so the
     * IQ never carries already-known entries; matches the
     * {@code WAWebGetIdentityKeysJob.getAndStoreIdentityKeys} bulk-load-then-filter pattern.
     *
     * @param userJids the user JIDs to fetch identity keys for
     * @throws NullPointerException if {@code userJids} is {@code null}
     * @throws RuntimeException     wrapping {@link InterruptedException} when the virtual thread is interrupted while waiting for the batches
     */
    @WhatsAppWebExport(moduleName = "WAWebGetIdentityKeysJob",
            exports = "getAndStoreIdentityKeys",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void fetchAndStoreIdentityKeys(Collection<Jid> userJids) {
        Objects.requireNonNull(userJids, "userJids cannot be null");

        if (userJids.isEmpty()) {
            return;
        }

        var store = client.store();

        var usersNeedingKeys = new ArrayList<Jid>();
        for (var userJid : userJids) {
            var primaryDeviceJid = userJid.toUserJid().withDevice(0);
            var address = primaryDeviceJid.toSignalAddress();
            if (store.findIdentityByAddress(address).isEmpty()) {
                usersNeedingKeys.add(userJid);
            }
        }

        if (usersNeedingKeys.isEmpty()) {
            return;
        }

        var batches = batchUsers(usersNeedingKeys, MAX_DEVICES_PER_QUERY);

        try (var scope = StructuredTaskScope.open()) {
            var subtasks = new ArrayList<Subtask<Void>>();
            for (var batch : batches) {
                subtasks.add(scope.fork(() -> {
                    fetchAndStoreIdentityKeyBatch(batch);
                    return null;
                }));
            }
            scope.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while fetching identity keys", e);
        }
    }

    /**
     * Dispatches and parses one batch of the identity-key prefetch.
     *
     * @apiNote
     * One subtask of the {@link StructuredTaskScope} inside
     * {@link #fetchAndStoreIdentityKeys(Collection)}; runs on its own virtual thread so
     * batches dispatch in parallel.
     *
     * @param userJids the users in this batch
     */
    @WhatsAppWebExport(moduleName = "WAWebGetIdentityKeysJob",
            exports = "getAndStoreIdentityKeys",
            adaptation = WhatsAppAdaptation.DIRECT)
    private void fetchAndStoreIdentityKeyBatch(List<Jid> userJids) {
        var query = buildIdentityKeyQuery(userJids);
        var response = client.sendNode(query);
        parseAndStoreIdentityKeyResponse(response);
    }

    /**
     * Builds the identity-key query IQ stanza for one batch of users.
     *
     * @apiNote
     * Produces an IQ with one {@code <user jid="...primary-device JID...">} child per user
     * inside an {@code <identity>} envelope; matches the wapper shape built by
     * {@code WAWebGetIdentityKeysJob}'s inner {@code m} function.
     *
     * @param userJids the users in this batch
     * @return the IQ node builder ready to send
     */
    @WhatsAppWebExport(moduleName = "WAWebGetIdentityKeysJob",
            exports = "getAndStoreIdentityKeys",
            adaptation = WhatsAppAdaptation.DIRECT)
    private NodeBuilder buildIdentityKeyQuery(List<Jid> userJids) {
        var userNodes = userJids.stream()
                .map(jid -> new NodeBuilder()
                        .description("user")
                        .attribute("jid", jid.toUserJid().withDevice(0))
                        .build())
                .toList();

        var identityNode = new NodeBuilder()
                .description("identity")
                .content(userNodes)
                .build();

        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", ENCRYPT_XMLNS)
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(identityNode);
    }

    /**
     * Parses the identity-key response and stores each successfully decoded key as a trusted
     * identity.
     *
     * @apiNote
     * Entries that carry an {@code <error>} child are skipped (matching the WA Web
     * {@code identityKeysParser} branch that throws on the same condition); entries with a
     * malformed or non-32-byte identity blob are likewise skipped and logged.
     *
     * @implNote
     * This implementation enforces the 32-byte length check before passing the value to
     * {@link SignalIdentityPublicKey#ofDirect(byte[])}, mirroring WA Web's
     * {@code contentBytes(32)} length-checked accessor.
     *
     * @param response the IQ response node
     */
    @WhatsAppWebExport(moduleName = "WAWebGetIdentityKeysJob",
            exports = "getAndStoreIdentityKeys",
            adaptation = WhatsAppAdaptation.DIRECT)
    private void parseAndStoreIdentityKeyResponse(Node response) {
        var listNode = response.getChild("list");
        if (listNode.isEmpty()) {
            return;
        }

        var store = client.store();

        for (var userNode : listNode.get().getChildren("user")) {
            try {
                var errorNode = userNode.getChild("error");
                if (errorNode.isPresent()) {
                    continue;
                }

                var deviceJid = userNode.getRequiredAttributeAsJid("jid");

                var identityKeyBytes = userNode.getChild("identity")
                        .flatMap(Node::toContentBytes)
                        .orElse(null);

                if (identityKeyBytes == null || identityKeyBytes.length != 32) {
                    continue;
                }

                var address = deviceJid.toSignalAddress();
                var identityKey = SignalIdentityPublicKey.ofDirect(identityKeyBytes);
                store.saveIdentity(address, identityKey);

            } catch (Exception e) {
                var jid = userNode.getAttribute("jid").map(Object::toString).orElse("unknown");
                System.err.println("Failed to store identity key for " + jid + ": " + e.getMessage());
            }
        }
    }

    /**
     * Splits a user list into batches of at most {@code batchSize} entries.
     *
     * @apiNote
     * Helper for {@link #fetchAndStoreIdentityKeys(Collection)}; preserves the input order so
     * the dispatch order matches the caller's input order.
     *
     * @param userJids  the users to batch
     * @param batchSize the maximum batch size
     * @return the list of batches in input order
     */
    @WhatsAppWebExport(moduleName = "WAWebGetIdentityKeysJob",
            exports = "getAndStoreIdentityKeys",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private List<List<Jid>> batchUsers(Collection<Jid> userJids, int batchSize) {
        var batches = new ArrayList<List<Jid>>();
        var currentBatch = new ArrayList<Jid>();

        for (var jid : userJids) {
            currentBatch.add(jid);
            if (currentBatch.size() >= batchSize) {
                batches.add(new ArrayList<>(currentBatch));
                currentBatch.clear();
            }
        }

        if (!currentBatch.isEmpty()) {
            batches.add(currentBatch);
        }

        return batches;
    }

    /**
     * Stores an identity key directly from an ADV account-signature key, bypassing the
     * identity-fetch round-trip.
     *
     * @apiNote
     * Used on the hosted-devices fast path: when the hosted-override AB prop is on and a
     * device list contains hosted entries, the {@code accountSignatureKey} taken from the
     * signed-key-index list is stored as the user's primary-device identity directly, saving
     * a round-trip. Matches the {@code WAWebHandleAdvDeviceNotificationUtils.verifySKeyIndexWithAccSigKey}
     * branch that ends in {@code saveIdentity(createSignalAddress(...), accSigKey)}.
     *
     * @param userJid             the user JID to store the identity for
     * @param accountSignatureKey the 32-byte account signature key
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code accountSignatureKey} is not exactly 32 bytes
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleAdvDeviceNotificationUtils",
            exports = "verifySKeyIndexWithAccSigKey",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void storeIdentityFromAccountSignatureKey(Jid userJid, byte[] accountSignatureKey) {
        Objects.requireNonNull(userJid, "userJid cannot be null");
        Objects.requireNonNull(accountSignatureKey, "accountSignatureKey cannot be null");

        if (accountSignatureKey.length != 32) {
            throw new IllegalArgumentException("Account signature key must be 32 bytes");
        }

        var store = client.store();
        var primaryDeviceJid = userJid.toUserJid().withDevice(0);
        var address = primaryDeviceJid.toSignalAddress();
        var identityKey = SignalIdentityPublicKey.ofDirect(accountSignatureKey);
        store.saveIdentity(address, identityKey);
    }
}
