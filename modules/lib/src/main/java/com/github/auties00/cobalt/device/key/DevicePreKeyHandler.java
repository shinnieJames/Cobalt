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
 * Fetches Signal pre-key bundles and identity keys from the server and installs them into
 * the local Signal store so outgoing messages can be encrypted for newly-learned devices.
 *
 * <p>Every WhatsApp companion device that Cobalt has not yet talked to needs a freshly
 * fetched pre-key bundle (identity key, signed pre-key, optional one-time pre-key) so the
 * Signal session can be initialized before the first outgoing message. This handler batches
 * those fetches (up to 100 devices per IQ), deduplicates concurrent requests for the same
 * device, sorts bundles so primary devices are processed before companion devices, and
 * finally drives {@link SignalSessionCipher} to materialise the sessions.
 *
 * <p>Also provides the identity-key prefetch that runs after a successful device sync so
 * incoming PKMSG decryption does not need a separate server round-trip to validate peer
 * identities, and a shortcut that stores a user's identity key directly from the ADV
 * account signature key when the hosted override flag is enabled.
 *
 * <p>Invoked by {@link com.github.auties00.cobalt.device.DeviceService} during message send
 * (to ensure sessions exist) and after device sync (to prefetch identity keys).
 *
 * @implNote WAWebFetchPrekeysJob.fetchPrekeys: performs the IQ exchange against
 * {@code <iq xmlns="encrypt" type="get">} and parses prekey bundles from the response.
 * WAWebManageE2ESessionsJob.ensureE2ESessions: deduplicates concurrent session establishment
 * requests via a module-level map.
 * WAWebGetIdentityKeysJob.getAndStoreIdentityKeys: prefetches identity keys for users with
 * validated key index info after USync completes.
 */
@WhatsAppWebModule(moduleName = "WAWebFetchPrekeysJob")
@WhatsAppWebModule(moduleName = "WAWebManageE2ESessionsJob")
@WhatsAppWebModule(moduleName = "WAWebGetIdentityKeysJob")
public final class DevicePreKeyHandler {
    /**
     * Maximum number of devices included in a single pre-key IQ query.
     *
     * <p>WA Web does not impose a per-IQ device cap of its own: the smax schema
     * {@code WASmaxOutPreKeysFetchKeyBundlesRequest} declares the {@code <user>}
     * children as {@code REPEATED_CHILD(..., 1, 1e5)}, i.e. between 1 and 100,000
     * users per request, and {@code WAWebFetchPrekeysJob.fetchPrekeys} sends every
     * caller-provided device in a single IQ. Cobalt batches at 100 to keep individual
     * request payloads manageable and to fan out across virtual threads.
     *
     * @implNote NO_WA_BASIS: the {@code length} entry indexed against
     * {@code WAWebFetchPrekeysJob} is a static-analysis artefact (there is no
     * exported numeric constant; the bundle contains {@code l.length===0} array
     * checks on local arrays). The real upstream cap is {@code 1e5} from the smax
     * request mixin; Cobalt's 100 is a prudence ceiling chosen so failures affect
     * fewer devices and so the parallel scope has multiple subtasks to dispatch.
     */
    private static final int MAX_DEVICES_PER_QUERY = 100;

    /**
     * XML namespace for pre-key and identity-key IQs.
     *
     * @implNote WAWebFetchPrekeysJob.fetchPrekeys: uses {@code xmlns="encrypt"} on the IQ.
     */
    private static final String ENCRYPT_XMLNS = "encrypt";

    /**
     * The WhatsApp client providing network access and the Signal store.
     */
    private final WhatsAppClient client;

    /**
     * The Signal session cipher used to install freshly fetched pre-key bundles.
     *
     * @implNote WAWebProcessKeyBundle: converts raw pre-key bundle bytes into an initialised
     * Signal session; Cobalt delegates that step to {@link SignalSessionCipher#process}.
     */
    private final SignalSessionCipher sessionCipher;

    /**
     * Tracks in-flight prekey fetch requests by device JID to prevent duplicate concurrent
     * session establishment requests.
     *
     * <p>When multiple message sends target the same device simultaneously, this ensures
     * only one IQ is dispatched and other callers wait on the same future.
     *
     * @implNote WAWebManageE2ESessionsJob.ensureE2ESessions: deduplicates concurrent
     * {@code getE2ESession} calls using a module-level WaitableMap; Cobalt uses a
     * {@link ConcurrentHashMap} keyed by device JID.
     */
    private final ConcurrentHashMap<Jid, CompletableFuture<SignalPreKeyBundle>> inFlightRequests = new ConcurrentHashMap<>();

    /**
     * Creates a new pre-key handler.
     *
     * @param client        the WhatsApp client for network and store access
     * @param sessionCipher the Signal session cipher for installing pre-key bundles
     * @throws NullPointerException if any argument is {@code null}
     * @implNote ADAPTED: WAWebFetchPrekeysJob and WAWebManageE2ESessionsJob access store and
     * network via module-level imports; Cobalt injects them through the constructor.
     */
    @WhatsAppWebExport(moduleName = "WAWebManageE2ESessionsJob",
            exports = "ensureE2ESessions",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public DevicePreKeyHandler(WhatsAppClient client, SignalSessionCipher sessionCipher) {
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.sessionCipher = Objects.requireNonNull(sessionCipher, "sessionCipher cannot be null");
    }

    /**
     * Result of a pre-key fetch/establish pass.
     *
     * <p>Mirrors the {@code {missedPrekeyCount, depletedPrekeyCount, deletedDevices}} tuple
     * returned by {@code WAWebManageE2ESessionsJob.ensureE2ESessions} and the
     * {@code {depletedPrekeyCount, processedPrekeyCount}} tuple from
     * {@code WAWebProcessKeyBundle.processKeyBundles}. Cobalt keeps the two fields that
     * downstream WAM emission needs: the resolved bundles and the count of devices in
     * the response whose one-time pre-key was missing (depleted server-side pool).
     *
     * @param bundles             the pre-key bundles keyed by device JID
     * @param depletedPrekeyCount the number of devices in the response for which the
     *                            server returned no one-time pre-key (the pool was
     *                            depleted for that device)
     *
     * @implNote WAWebProcessKeyBundle.splitKeyBundles: the count is incremented for
     *     each response entry where {@code !i.key && !i.wid.isBot()}; Cobalt replicates
     *     that check during response parsing.
     */
    public record PreKeyFetchResult(Map<Jid, SignalPreKeyBundle> bundles, int depletedPrekeyCount) {
        /**
         * Canonical constructor that copies the bundles map for immutability.
         *
         * @param bundles             the pre-key bundles keyed by device JID
         * @param depletedPrekeyCount the depleted one-time pre-key count
         */
        public PreKeyFetchResult {
            bundles = bundles == null ? Map.of() : Map.copyOf(bundles);
        }
    }

    /**
     * Fetches pre-key bundles for the specified devices and establishes Signal sessions.
     *
     * <p>Convenience overload that passes {@code false} for the identity-reason flag.
     *
     * @param deviceJids the device JIDs to fetch pre-keys for
     * @return the fetch result with bundles and depleted one-time pre-key count
     * @implNote WAWebFetchPrekeysJob.fetchPrekeys: default call does not set {@code reason=identity}.
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
     * <p>Devices already covered by an in-flight request reuse the pending future; new
     * devices are batched (up to {@value #MAX_DEVICES_PER_QUERY} per IQ) and dispatched in
     * parallel across virtual threads. After bundles are fetched they are sorted so primary
     * devices are processed before companion devices, then handed to the Signal session
     * cipher to materialise sessions.
     *
     * @param deviceJids            the device JIDs to fetch pre-keys for
     * @param hasUserReasonIdentity whether to set {@code reason="identity"} on each user node
     * @return map of device JIDs to their pre-key bundles; empty if the fetch fails
     * @throws RuntimeException if the virtual thread is interrupted while waiting
     * @implNote WAWebFetchPrekeysJob.fetchPrekeys: builds the IQ, parses the response into
     * per-user bundles, then hands each bundle to WAWebProcessKeyBundle.
     * WAWebManageE2ESessionsJob.ensureE2ESessions: deduplicates concurrent fetches and
     * delegates to fetchPrekeys for missing devices.
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

        // WAWebManageE2ESessionsJob.ensureE2ESessions
        // Separates devices into those with in-flight requests and those needing new fetches
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

        // WAWebManageE2ESessionsJob.ensureE2ESessions
        // Registers new futures for devices about to be fetched, handling race conditions
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

            // WAWebFetchPrekeysJob.fetchPrekeys
            // Fans out one virtual-thread subtask per batch to dispatch IQs in parallel
            try (var scope = StructuredTaskScope.open()) {
                var subtasks = new ArrayList<Subtask<PreKeyBatchResult>>();
                for (var batch : batches) {
                    subtasks.add(scope.fork(() -> fetchPreKeyBatch(batch, hasUserReasonIdentity)));
                }
                scope.join();

                for (var subtask : subtasks) {
                    if (subtask.state() == Subtask.State.SUCCESS) {
                        var batchResult = subtask.get();
                        // WAWebProcessKeyBundle.processKeyBundles: aggregates depletedPrekeyCount
                        // across batches (E += (N=w.depletedPrekeyCount)!=null?N:0)
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

                // WAWebFetchPrekeysJob.fetchPrekeys
                // Completes any remaining futures with null when the server omitted the device
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

        // WAWebManageE2ESessionsJob.ensureE2ESessions
        // Waits for previously in-flight requests to complete so deduplicated callers get the same bundle.
        // Depleted counts are NOT re-attributed here: WA Web dedupes via its WaitableMap so only the
        // originating caller contributes to the depletion metric for a given device.
        for (var entry : existingFutures.entrySet()) {
            try {
                var bundle = entry.getValue().join();
                if (bundle != null) {
                    allBundles.put(entry.getKey(), bundle);
                }
            } catch (Exception e) {
                // Failed in-flight fetch: the device simply will not have a bundle
            }
        }

        // WAWebProcessKeyBundle.processKeyBundle
        // Sorts bundles so primary devices are processed before companions to avoid races on shared state
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

        // WAWebProcessKeyBundle.processKeyBundle
        // Installs each bundle into the Signal session store in sorted order
        for (var entry : sortedEntries) {
            var deviceJid = entry.getKey();
            var bundle = entry.getValue();
            var address = deviceJid.toSignalAddress();
            sessionCipher.process(address, bundle);
        }

        return new PreKeyFetchResult(allBundles, depletedPrekeyCount);
    }

    /**
     * Per-batch pre-key fetch result carrying the parsed bundles and the count of
     * depleted one-time pre-keys found in that batch's response.
     *
     * @param bundles             the pre-key bundles parsed from this batch's response
     * @param depletedPrekeyCount the depleted one-time pre-key count for this batch
     *
     * @implNote WAWebProcessKeyBundle.splitKeyBundles: returns {@code {primaryBundle,
     *     companionBundle, depletedPrekeyCount}}; Cobalt collapses the primary/companion
     *     split since sorting happens later and only keeps the depleted count.
     */
    private record PreKeyBatchResult(Map<Jid, SignalPreKeyBundle> bundles, int depletedPrekeyCount) {
    }

    /**
     * Dispatches a single pre-key IQ for one batch of devices.
     *
     * @param deviceJids            the devices included in this batch
     * @param hasUserReasonIdentity whether to set {@code reason="identity"} on each user node
     * @return the batch result with parsed bundles and depleted one-time pre-key count
     * @implNote WAWebFetchPrekeysJob.fetchPrekeys: sends the IQ and passes the response to
     * the response parser.
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
     * @param deviceJids the devices to batch
     * @return the list of batches in input order
     * @implNote WAWebRunInBatches: WA Web provides a generic batching helper; Cobalt inlines
     * a simple linear split because the sizes involved are small.
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
     * Builds the prekey query IQ stanza.
     *
     * <p>Produces an IQ of the form
     * {@code <iq xmlns="encrypt" type="get" to="s.whatsapp.net"><key><user jid="..." [reason="identity"]/>...</key></iq>}.
     * The {@code reason="identity"} attribute is set when the caller requested identity
     * verification so the server can distinguish identity prefetches from send-path fetches.
     *
     * @param deviceJids            the device JIDs to query
     * @param hasUserReasonIdentity whether to include {@code reason="identity"}
     * @return the IQ node builder
     * @implNote WAWebFetchPrekeysJob.fetchPrekeys: constructs the {@code <key>} node with
     * {@code <user jid=".." [reason="identity"]/>} children.
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
     * Parses the prekey response into {@link SignalPreKeyBundle} instances keyed by device JID.
     *
     * <p>The response carries a {@code <list>} envelope with one {@code <user>} entry per
     * queried device; entries that fail to parse are logged and skipped so one malformed
     * device does not fail the batch.
     *
     * <p>Also tallies the depleted one-time pre-key count: devices whose {@code <user>} entry
     * has no {@code <key>} child (server-side pool exhausted for that device) and which are
     * not bot JIDs. This count is used by {@code PrekeysDepletionEvent} (id 3014).
     *
     * @param response the IQ response node
     * @return the batch result with parsed bundles and depleted one-time pre-key count
     * @implNote WAWebFetchPrekeysJob.fetchPrekeys: iterates the response list and delegates
     * per-user parsing to an internal function matching {@link #parseUserPreKeyBundle}.
     * WAWebProcessKeyBundle.splitKeyBundles: increments the depleted counter when
     * {@code !i.key && !i.wid.isBot()}.
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

                // WAWebProcessKeyBundle.splitKeyBundles: !i.key && !i.wid.isBot()
                // Counts devices whose server-side one-time pre-key pool is depleted,
                // excluding bot JIDs which never carry one-time pre-keys.
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
     * Parses a single user's pre-key bundle from a response child node.
     *
     * <p>Extracts the registration id, identity key, signed pre-key (id, public, signature)
     * and optional one-time pre-key (id, public) into a {@link SignalPreKeyBundle}.
     *
     * @param userNode the {@code <user>} node for this device
     * @return the parsed pre-key bundle
     * @throws IllegalArgumentException if any required field is missing
     * @implNote WAWebFetchPrekeysJob.fetchPrekeys: decodes registration id, identity, signed
     * pre-key, and optional one-time pre-key from the user node.
     */
    @WhatsAppWebExport(moduleName = "WAWebFetchPrekeysJob",
            exports = "fetchPrekeys",
            adaptation = WhatsAppAdaptation.DIRECT)
    private SignalPreKeyBundle parseUserPreKeyBundle(Node userNode) {
        // WAWebFetchPrekeysJob.fetchPrekeys via WASmaxInPreKeysRegistrationIDMixin:
        // <registration> content is exactly 4 raw bytes parsed as a big-endian
        // unsigned integer, NOT an ASCII number string.
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

        // WAWebFetchPrekeysJob.fetchPrekeys via WASmaxInPreKeysKeyIDMixin:
        // <id> content is exactly 3 raw bytes parsed as a big-endian unsigned integer.
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

        // WAWebFetchPrekeysJob.fetchPrekeys
        // One-time pre-key is optional: when the server ran out, only skey is returned
        var preKeyNode = userNode.getChild("key");
        if (preKeyNode.isPresent()) {
            // WAWebFetchPrekeysJob.fetchPrekeys via WASmaxInPreKeysKeyIDMixin:
            // <id> content is 3 raw bytes parsed as a big-endian unsigned integer.
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
     * <p>Mirrors the WA Web {@code WAParsableXmlNode.convertBytesToUint(bytes, byteCount)}
     * helper used by every smax {@code KeyIDMixin} / {@code RegistrationIDMixin} parser:
     * accumulates {@code n = n * 256 + bytes[i]} for the first {@code byteCount} bytes.
     *
     * <p>Pre-key wire fields use this encoding instead of an ASCII number string:
     * registration ids are 4 bytes, signed-pre-key and one-time-pre-key ids are 3 bytes.
     *
     * @param bytes     the raw content bytes from the wire-format node
     * @param byteCount the number of leading bytes to interpret
     * @return the resulting big-endian unsigned integer
     * @throws IllegalArgumentException if {@code bytes} has fewer than {@code byteCount} bytes
     * @implNote WAParsableXmlNode.convertBytesToUint: identical accumulator implementation.
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
     * Returns the subset of the given devices that do not yet have a Signal session.
     *
     * @param deviceJids the device JIDs to check
     * @return the devices for which a session needs to be established
     * @implNote WAWebManageE2ESessionsJob.ensureE2ESessions: filters devices via
     * {@code hasSignalSessions} before calling {@code fetchPrekeys}.
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
     * Ensures Signal sessions exist for all specified devices by fetching and installing any
     * missing pre-key bundles.
     *
     * @param deviceJids the device JIDs to ensure sessions for
     * @return the number of devices in the server response for which the one-time pre-key
     *         pool was depleted (i.e. no {@code <key>} element was returned for a non-bot
     *         device); used by the caller to emit {@code PrekeysDepletionEvent}
     * @implNote WAWebManageE2ESessionsJob.ensureE2ESessions: combines the
     * "missing session?" filter and the fetch into a single top-level entry point and
     * returns {@code {missedPrekeyCount, depletedPrekeyCount, deletedDevices}}.
     */
    @WhatsAppWebExport(moduleName = "WAWebManageE2ESessionsJob",
            exports = "ensureE2ESessions",
            adaptation = WhatsAppAdaptation.DIRECT)
    public int ensureSessions(Collection<Jid> deviceJids) {
        // WAWebManageE2ESessionsJob.ensureE2ESessions
        // Only fetches pre-keys for devices without an existing Signal session
        var devicesNeedingSessions = findDevicesNeedingSessions(deviceJids);
        if (devicesNeedingSessions.isEmpty()) {
            return 0;
        }

        return fetchAndProcessPreKeyBundles(devicesNeedingSessions).depletedPrekeyCount();
    }

    /**
     * Prefetches identity keys for the specified users and stores them as trusted identities.
     *
     * <p>Called after a successful device sync for every user whose validated signed key
     * index list contains at least one key index, so incoming PKMSG decryption can validate
     * peer identities without needing a separate server round-trip.
     *
     * <p>The request is an {@code <iq xmlns="encrypt" type="get">} with an {@code <identity>}
     * child containing one {@code <user>} per user JID targeting their primary device.
     *
     * @param userJids the user JIDs to fetch identity keys for
     * @throws RuntimeException if the virtual thread is interrupted while waiting
     * @implNote WAWebGetIdentityKeysJob.getAndStoreIdentityKeys: sends the identity query and
     * stores each received identity via {@code WAWebSignalProtocolStore.saveIdentity}.
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

        // WAWebGetIdentityKeysJob.getAndStoreIdentityKeys
        // Filters users whose primary device already has a stored identity key via
        // bulkLoadIdentityKey (mirrored here as a per-address findIdentityByAddress lookup)
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

        // WAWebGetIdentityKeysJob.getAndStoreIdentityKeys
        // Batches users and fans out one virtual-thread subtask per batch
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
     * Fetches and stores identity keys for a single batch of users.
     *
     * @param userJids the users in this batch
     * @implNote WAWebGetIdentityKeysJob.getAndStoreIdentityKeys: sends the identity IQ and
     * parses the response into trusted identity store entries.
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
     * Builds the identity-key query IQ stanza.
     *
     * @param userJids the users in this batch
     * @return the IQ node builder with the {@code <identity>} child
     * @implNote WAWebGetIdentityKeysJob.getAndStoreIdentityKeys: constructs the
     * {@code <identity>} node with one {@code <user>} per user JID targeting the primary device.
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
     * Parses the identity-key response and stores each key as a trusted identity.
     *
     * <p>Entries that carry an {@code <error>} child are skipped. Entries with a malformed
     * or non-32-byte identity value are likewise skipped and logged.
     *
     * @param response the IQ response node
     * @implNote WAWebGetIdentityKeysJob.getAndStoreIdentityKeys: iterates response
     * {@code <list>/<user>} children and calls {@code WAWebSignalProtocolStore.saveIdentity}
     * for each valid entry.
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
                // WAWebGetIdentityKeysJob.getAndStoreIdentityKeys: bulkCreateIdentity ->
                // WAWebSignalProtocolStore.saveIdentity persists the identity key
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
     * @param userJids  the users to batch
     * @param batchSize the maximum batch size
     * @return the list of batches in input order
     * @implNote WAWebRunInBatches: WA Web uses a generic batching helper; Cobalt inlines a
     * linear split.
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
     * Stores an identity key directly from an ADV account signature key.
     *
     * <p>Used on the hosted-devices fast path: when the hosted-override AB prop is enabled
     * and a device list contains hosted devices, the {@code accountSignatureKey} from the
     * signed key index list is saved as the user's primary-device identity key, avoiding a
     * separate identity fetch.
     *
     * @param userJid             the user JID to store the identity for
     * @param accountSignatureKey the 32-byte account signature key
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code accountSignatureKey} is not exactly 32 bytes
     * @implNote WAWebBizCoexGatingUtils.hostedOverrideAdvAccountSignatureKeyEnabled path:
     * WA Web calls {@code WAWebSignalProtocolStore.saveIdentity(address, accountSignatureKey)}
     * directly when the override flag is active.
     */
    @WhatsAppWebExport(moduleName = "WAWebBizCoexGatingUtils",
            exports = "hostedOverrideAdvAccountSignatureKeyEnabled",
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
