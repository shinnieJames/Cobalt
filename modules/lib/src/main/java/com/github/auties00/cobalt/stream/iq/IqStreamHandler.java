package com.github.auties00.cobalt.stream.iq;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientVerificationHandler;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.pairing.CompanionPairingService;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.device.identity.ADVDeviceIdentitySpec;
import com.github.auties00.cobalt.model.device.identity.ADVSignedDeviceIdentity;
import com.github.auties00.cobalt.model.device.identity.ADVSignedDeviceIdentityBuilder;
import com.github.auties00.cobalt.model.device.identity.ADVSignedDeviceIdentitySpec;
import com.github.auties00.cobalt.model.device.pairing.ClientPairingProps;
import com.github.auties00.cobalt.model.device.pairing.ClientPairingPropsSpec;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.SocketStream;
import com.github.auties00.cobalt.sync.SnapshotRecoveryService;
import com.github.auties00.cobalt.util.DataUtils;
import com.github.auties00.cobalt.wam.event.Lid11MigrationLifecycleEventBuilder;
import com.github.auties00.cobalt.wam.event.MdLinkDeviceCompanionEventBuilder;
import com.github.auties00.cobalt.wam.type.MdLinkDeviceCompanionStage;
import com.github.auties00.cobalt.wam.type.MigrationStageEnum;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Handles incoming IQ (Info/Query) stanzas from the WhatsApp server.
 *
 * <p>Routes IQ stanzas based on their {@code xmlns} attribute:
 * <ul>
 *   <li>{@code urn:xmpp:ping}, responds with an IQ result</li>
 *   <li>{@code md}, dispatches to pair-device or pair-success handlers</li>
 * </ul>
 *
 * @implNote WAWebHandleStanzaCommon.handleIq, WAWebHandlePairDevice.default, WAWebHandlePairSuccess.default
 */
@WhatsAppWebModule(moduleName = "WAWebHandleStanzaCommon")
@WhatsAppWebModule(moduleName = "WAWebHandlePairDevice")
@WhatsAppWebModule(moduleName = "WAWebHandlePairSuccess")
public final class IqStreamHandler implements SocketStream.Handler {

    /**
     * Logger for this handler.
     *
     * @implNote WAWebHandlePairDevice uses WALogger, WAWebHandlePairSuccess uses WALogger
     */
    private static final System.Logger LOGGER = System.getLogger(IqStreamHandler.class.getName());

    /**
     * Rotation interval in milliseconds when 6 QR refs are present.
     *
     * @implNote WAWebHandlePairDevice: var u = 6e4
     */
    private static final long QR_ROTATION_MS = 60_000L;

    /**
     * Rotation interval in milliseconds when fewer than 6 refs are present
     * (used for refresh rotations).
     *
     * @implNote WAWebHandlePairDevice: var c = 20 * 1e3
     */
    private static final long REFRESH_ROTATION_MS = 20_000L;

    /**
     * The WhatsApp client instance for sending nodes and accessing the store.
     *
     * @implNote ADAPTED: WAWebHandleStanzaCommon uses WAWap, WAWebHandlePairDevice uses WAWebConnModel/WAWebBackendEventBus
     */
    private final WhatsAppClient whatsapp;

    /**
     * The web verification handler for QR code or pairing code delivery.
     *
     * @implNote ADAPTED: WAWebHandlePairDevice sets Conn.ref; Cobalt delivers via verification handler
     */
    private final WhatsAppClientVerificationHandler.Web webVerificationHandler;

    /**
     * The device service for ADV validation during pair-success.
     *
     * @implNote ADAPTED: WAWebHandlePairSuccess calls WAWebAdvSignatureApi directly
     */
    private final DeviceService deviceService;

    /**
     * The snapshot recovery service for updating primary device syncd recovery support.
     *
     * @implNote ADAPTED: WAWebHandlePairSuccess calls WAWebSyncdSnapshotRecoveryGatingUtils.updatePrimaryDeviceSupportsSyncdRecovery
     */
    private final SnapshotRecoveryService snapshotRecoveryService;

    /**
     * Executor for scheduling QR ref rotation tasks.
     *
     * @implNote ADAPTED: WAWebHandlePairDevice uses WAShiftTimer; Cobalt uses ScheduledExecutorService
     */
    private final ScheduledExecutorService rotationExecutor;

    /**
     * Shared service that owns the alt-device-linking (phone-number
     * pairing-code) handshake state and IQs. When
     * {@link CompanionPairingService#isEnabled()} returns {@code true}
     * the {@code pair-device} stanza is acknowledged without scheduling
     * QR ref rotation and the {@code companion_hello} flow is started
     * instead.
     *
     * @implNote WAWebAltDeviceLinkingApi
     */
    private final CompanionPairingService deviceLinkingService;

    /**
     * Lock protecting rotation state ({@link #rotationTask}).
     *
     * @implNote NO_WA_BASIS: Java concurrency adaptation
     */
    private final Object rotationLock;

    /**
     * The currently scheduled rotation task, or {@code null} if no rotation is active.
     *
     * @implNote ADAPTED: WAWebHandlePairDevice: var m = null (ShiftTimer instance)
     */
    private ScheduledFuture<?> rotationTask;

    /**
     * Creates a new IQ stream handler.
     *
     * @param whatsapp                the WhatsApp client, must not be {@code null}
     * @param webVerificationHandler  the web verification handler for delivering QR/pairing codes, must not be {@code null}
     * @param deviceService           the device service for ADV validation, must not be {@code null}
     * @param snapshotRecoveryService the snapshot recovery service, must not be {@code null}
     * @implNote ADAPTED: WAWebHandleStanzaCommon, WAWebHandlePairDevice, WAWebHandlePairSuccess use module-level imports
     */
    public IqStreamHandler(
            WhatsAppClient whatsapp,
            WhatsAppClientVerificationHandler.Web webVerificationHandler,
            DeviceService deviceService,
            SnapshotRecoveryService snapshotRecoveryService,
            CompanionPairingService deviceLinkingService
    ) {
        this.whatsapp = whatsapp;
        this.webVerificationHandler = Objects.requireNonNull(webVerificationHandler, "webVerificationHandler cannot be null");
        this.deviceService = Objects.requireNonNull(deviceService, "deviceService cannot be null");
        this.snapshotRecoveryService = Objects.requireNonNull(snapshotRecoveryService, "snapshotRecoveryService cannot be null");
        this.deviceLinkingService = Objects.requireNonNull(deviceLinkingService, "altDeviceLinkingService cannot be null");
        this.rotationLock = new Object();
        this.rotationExecutor = Executors.newSingleThreadScheduledExecutor(runnable ->
                Thread.ofPlatform()
                        .daemon()
                        .name("CobaltPairDeviceRotation")
                        .unstarted(runnable)
        );
    }

    /**
     * Handles an incoming IQ stanza by routing it based on the {@code xmlns} attribute.
     *
     * <p>For {@code urn:xmpp:ping}, responds with an IQ result containing
     * {@code type=result} and {@code to=from}. For {@code md}, dispatches to
     * the pair-device or pair-success handler based on the first child's tag.
     *
     * @param node the incoming IQ stanza
     * @implNote WAWebHandleStanzaCommon.handleIq
     */
    @Override
    public void handle(Node node) {
        var xmlns = node.getAttributeAsString("xmlns", null); // WAWebHandleStanzaCommon.handleIq: var t = e.attrs; if (t.xmlns === "urn:xmpp:ping")
        if ("urn:xmpp:ping".equals(xmlns)) {
            handlePing(node); // WAWebHandleStanzaCommon.handleIq: return o("WAWap").wap("iq", {type: "result", to: t.from})
            return;
        }

        if (!"md".equals(xmlns)) { // WAWebHandleStanzaCommon.handleIq: if (t.xmlns === "md") { ... } else throw
            return; // ADAPTED: WAWebHandleStanzaCommon.handleIq throws; Cobalt silently returns
        }

        var child = node.getChild().orElse(null); // WAWebHandleStanzaCommon.handleIq: if (!Array.isArray(n) || !n.length) return; var a = n[0].tag
        if (child == null) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring md iq without child: {0}", node);
            return;
        }

        switch (child.description()) { // WAWebHandleStanzaCommon.handleIq: switch (a)
            case "pair-device" -> handlePairDevice(node); // WAWebHandleStanzaCommon.handleIq: return r("WAWebHandlePairDevice")(e), passes full IQ
            case "pair-success" -> handlePairSuccess(node); // WAWebHandleStanzaCommon.handleIq: return r("WAWebHandlePairSuccess")(e), passes full IQ
            default -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring unsupported md iq child {0}", child.description());
        }
    }

    /**
     * Handles an {@code urn:xmpp:ping} IQ by responding with an IQ result.
     *
     * @param node the incoming ping IQ stanza
     * @implNote WAWebHandleStanzaCommon.handleIq (ping branch): return o("WAWap").wap("iq", {type: "result", to: t.from})
     */
    private void handlePing(Node node) {
        var from = node.getAttributeAsJid("from").orElse(null); // WAWebHandleStanzaCommon.handleIq: t.from
        if (from == null) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring ping iq without from attribute"); // NO_WA_BASIS: defensive null check
            return;
        }

        var response = new NodeBuilder() // WAWebHandleStanzaCommon.handleIq: o("WAWap").wap("iq", {type: "result", to: t.from})
                .description("iq")
                .attribute("type", "result") // WAWebHandleStanzaCommon.handleIq: type: "result"
                .attribute("to", from) // WAWebHandleStanzaCommon.handleIq: to: t.from
                .attribute("id", node.getAttributeAsString("id", null)) // ADAPTED: XMPP convention carries id for correlation
                .build();
        whatsapp.sendNodeWithNoResponse(response); // ADAPTED: WA Web returns node which flows through WAComms.castStanza
    }

    /**
     * Handles a pair-device IQ stanza by generating an ADV secret key,
     * extracting QR code refs, scheduling their rotation, and sending back
     * an IQ result acknowledgment.
     *
     * @param iqNode the full IQ stanza containing the pair-device child
     * @implNote WAWebHandlePairDevice.default
     */
    private void handlePairDevice(Node iqNode) {
        var pairDevice = iqNode.getChild("pair-device").orElse(null); // WAWebHandlePairDevice: receiveSetToCompanionRPC extracts pair-device child
        if (pairDevice == null) {
            LOGGER.log(System.Logger.Level.WARNING, "Received md iq without pair-device child"); // NO_WA_BASIS: defensive check
            return;
        }

        whatsapp.store().setAdvSecretKey(DataUtils.randomByteArray(32)); // WAWebHandlePairDevice.g: yield o("WAWebAdvSignatureApi").generateADVSecretKey()
        sendPairDeviceAck(iqNode); // WAWebHandlePairDevice._: u(), makeSetToCompanionResponseClientResponse

        if (deviceLinkingService.isEnabled()) { // WAWebAltDeviceLinkingApi.startAltLinkingFlow: issued client-side when pairing type is ALT_DEVICE_LINKING
            try {
                deviceLinkingService.start();
            } catch (Throwable throwable) {
                LOGGER.log(System.Logger.Level.WARNING, "Cannot start alt-device-linking: {0}", throwable.getMessage());
            }
            return;
        }

        var refs = extractPairRefs(pairDevice); // WAWebHandlePairDevice._: c.pairDeviceRef.map(function(e) { ... readString(t.size()) })
        if (refs.isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING, "Received pair-device iq without any usable refs"); // NO_WA_BASIS: defensive check
            return;
        }

        scheduleVerificationValues(refs); // WAWebHandlePairDevice.g: f(d), schedules timer rotation
    }

    /**
     * Sends an IQ result acknowledgment for the pair-device IQ.
     *
     * <p>Per WA Web, the response is an IQ result with the original stanza
     * {@code id} and {@code to} set to the domain JID from the original
     * {@code from} attribute.
     *
     * @param iqNode the original pair-device IQ stanza
     * @implNote WASmaxOutMdSetToCompanionResponseClientResponse.makeSetToCompanionResponseClientResponse
     */
    private void sendPairDeviceAck(Node iqNode) {
        var id = iqNode.getAttributeAsString("id", null); // WASmaxOutMdSetToCompanionResponseClientResponse: attrFromReference(attrStanzaId, e, ["id"])
        var from = iqNode.getAttributeAsJid("from").orElse(null); // WASmaxOutMdSetToCompanionResponseClientResponse: attrFromReference(attrDomainJid, e, ["from"])
        if (id == null || from == null) {
            LOGGER.log(System.Logger.Level.DEBUG, "Cannot send pair-device ack: missing id or from"); // NO_WA_BASIS: defensive check
            return;
        }

        var response = new NodeBuilder() // WASmaxOutMdSetToCompanionResponseClientResponse: smax("iq", {id: STANZA_ID(t.value), to: DOMAIN_JID(n.value), type: "result"})
                .description("iq")
                .attribute("id", id) // WASmaxOutMdSetToCompanionResponseClientResponse: id: STANZA_ID(t.value)
                .attribute("to", Jid.userServer()) // WASmaxOutMdSetToCompanionResponseClientResponse: to: DOMAIN_JID(n.value), domain JID is s.whatsapp.net
                .attribute("type", "result") // WASmaxOutMdSetToCompanionResponseClientResponse: type: "result"
                .build();
        whatsapp.sendNodeWithNoResponse(response); // ADAPTED: WA Web returns node through WAComms.castStanza
    }

    /**
     * Extracts QR code ref strings from the pair-device node.
     *
     * <p>Iterates over child {@code ref} elements and decodes their binary
     * content as UTF-8 strings, matching WA Web's SMAX parsing which expects
     * exactly 6 ref elements.
     *
     * @param pairDevice the pair-device child node
     * @return an ordered set of non-blank ref strings
     * @implNote WASmaxInMdSetToCompanionRequest.parseSetToCompanionRequestPairDeviceRef,
     *           WAWebHandlePairDevice._: c.pairDeviceRef.map(function(e) { var t = new Binary(e.elementValue); return t.readString(t.size()) })
     */
    private LinkedHashSet<String> extractPairRefs(Node pairDevice) {
        var refs = new LinkedHashSet<String>(); // ADAPTED: WA Web uses array from SMAX parse
        decodeContentAsString(pairDevice).ifPresent(refs::add); // NO_WA_BASIS: defensive extraction from node itself

        for (var child : pairDevice.children()) { // WASmaxInMdSetToCompanionRequest: mapChildrenWithTag(r.value, "ref", 6, 6, e)
            decodeContentAsString(child).ifPresent(refs::add); // WASmaxInMdSetToCompanionRequest.e: contentBytes(e) -> elementValue

            findStringAttribute(child, "ref", "value", "code") // NO_WA_BASIS: defensive attribute extraction
                    .ifPresent(refs::add);
        }

        findStringAttribute(pairDevice, "ref", "value", "code") // NO_WA_BASIS: defensive attribute extraction
                .ifPresent(refs::add);
        refs.removeIf(String::isBlank); // NO_WA_BASIS: defensive cleanup
        return refs;
    }

    /**
     * Schedules the rotation of QR code verification values.
     *
     * <p>Mirrors WA Web's {@code ShiftTimer} loop where each tick recomputes
     * the rotation delay from the current queue size before popping the next
     * ref: the tick where {@code d.length === 6} uses {@link #QR_ROTATION_MS}
     * (the initial 60 second display for the first ref), every subsequent
     * tick uses {@link #REFRESH_ROTATION_MS}. The first tick is fired
     * synchronously via {@code forceRunNow}; later ticks are scheduled one
     * at a time via {@code onOrAfter(e)} so the delay can change between
     * ticks.
     *
     * <p>Rotation also stops early if the device is already registered
     * (WA Web guards the tick body with
     * {@code WAWebUserPrefsMultiDevice.isRegistered}) or if the queue has
     * been drained.
     *
     * @param refs the ordered set of ref strings to rotate through
     * @implNote WAWebHandlePairDevice.g: d = e; m.forceRunNow() with
     *           ShiftTimer callback that calls d.shift()
     */
    private void scheduleVerificationValues(LinkedHashSet<String> refs) {
        synchronized (rotationLock) {
            cancelRotationLocked(); // WAWebHandlePairDevice.g: m || (m = new ShiftTimer(...)) reuses single instance
        }

        var queue = new ArrayDeque<>(refs);
        runRotationTick(queue); // WAWebHandlePairDevice.g: m.forceRunNow(), first tick runs immediately
    }

    /**
     * Runs a single rotation tick matching WA Web's {@code ShiftTimer}
     * callback body.
     *
     * <p>The tick order is identical to WA Web: check the early-exit
     * conditions (registered or empty refs) first, then compute the
     * next TTL from the current queue size, pop the next ref, publish
     * it, and reschedule the next tick after the computed delay unless
     * the queue is now empty.
     *
     * @param queue the mutable ref queue shared across ticks
     * @implNote WAWebHandlePairDevice ShiftTimer callback body
     */
    private void runRotationTick(ArrayDeque<String> queue) {
        String next;
        long rotationDelay;
        synchronized (rotationLock) {
            cancelRotationLocked(); // ADAPTED: WAShiftTimer reschedules via onOrAfter; we reschedule by cancelling + scheduling a new task

            if (whatsapp.store().registered()) { // WAWebHandlePairDevice: if (WAWebUserPrefsMultiDevice.isRegistered()) m && m.cancel(), m = null
                return;
            }

            if (queue.isEmpty()) { // WAWebHandlePairDevice: else if (!d || !d.length) m && m.cancel(), m = null, triggerSetSocketState(UNPAIRED_IDLE)
                return; // ADAPTED: UNPAIRED_IDLE event is a WA Web backend event bus signal with no Cobalt analogue
            }

            rotationDelay = queue.size() == 6 ? QR_ROTATION_MS : REFRESH_ROTATION_MS; // WAWebHandlePairDevice: var e = d.length === 6 ? u : c
            next = queue.pollFirst(); // WAWebHandlePairDevice: var t = d.shift()
        }

        publishVerificationValue(next); // WAWebHandlePairDevice: Conn.set({ref: t, refTTL: e}), triggerSetSocketState(UNPAIRED)

        synchronized (rotationLock) {
            if (queue.isEmpty()) { // WAWebHandlePairDevice: on next tick (!d || !d.length) branch cancels; we skip scheduling to avoid a trailing no-op tick
                return;
            }

            rotationTask = rotationExecutor.schedule( // WAWebHandlePairDevice: m && m.onOrAfter(e)
                    () -> runRotationTick(queue),
                    rotationDelay,
                    TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Publishes a single QR ref to the web verification handler by
     * combining the ref with the noise/identity/ADV keys into the
     * comma-separated payload WA Web stamps into the QR PNG.
     *
     * <p>Only invoked for the QR branch; the pairing-code branch runs
     * through {@link CompanionPairingService} which feeds the handler a
     * client-generated eight-character Crockford base32 code derived
     * from its own random bytes rather than any server ref.
     *
     * @param ref the ref string to publish, or {@code null} to skip
     * @implNote WAWebHandlePairDevice ShiftTimer callback: Conn.set({ref: t, refTTL: e}),
     *           BackendEventBus.triggerSetSocketState(SOCKET_STATE.UNPAIRED)
     */
    private void publishVerificationValue(String ref) {
        if (ref == null || ref.isBlank()) { // NO_WA_BASIS: defensive null/blank check
            return;
        }

        if (!(webVerificationHandler instanceof WhatsAppClientVerificationHandler.Web.QrCode)) {
            return;
        }
        webVerificationHandler.handle(buildQrPayload(ref)); // ADAPTED: WA Web stores raw ref in Conn.ref; QR assembly done in UI layer
    }

    /**
     * Builds a QR code payload string by combining the ref with the noise
     * public key, identity public key, ADV secret key, and client type.
     *
     * @param ref the QR ref from the server
     * @return the comma-separated QR payload string
     * @implNote ADAPTED: WAWebHandlePairDevice stores ref in Conn.ref; QR string assembly
     *           happens in the QR UI layer. Cobalt pre-assembles it for the handler.
     */
    private String buildQrPayload(String ref) {
        var store = whatsapp.store();
        var advSecret = store.advSecretKey().orElseGet(() -> { // WAWebHandlePairDevice.g: yield generateADVSecretKey(), already generated
            var generated = DataUtils.randomByteArray(32); // NO_WA_BASIS: fallback generation
            store.setAdvSecretKey(generated);
            return generated;
        });

        var encoder = Base64.getEncoder();
        var noise = encoder.encodeToString(store.noiseKeyPair().publicKey().toEncodedPoint());
        var identity = encoder.encodeToString(store.identityKeyPair().publicKey().toEncodedPoint());
        var secret = encoder.encodeToString(advSecret);
        return String.join(",",
                ref,
                noise,
                identity,
                secret,
                whatsapp.store().device().clientType().name().toLowerCase());
    }

    /**
     * Handles a pair-success IQ stanza by validating the device identity,
     * storing the paired JID and LID, processing client pairing props,
     * and sending back an IQ result with the signed device identity.
     *
     * <p>The early return mirrors WA Web's
     * {@code !(g || WAWebUserPrefsMultiDevice.isRegistered())} guard: the
     * {@code g} module-level flag prevents concurrent re-entry for the
     * same IQ, and {@code isRegistered()} swallows duplicate pair-success
     * deliveries that arrive after the store has already completed
     * registration. Cobalt folds both checks into the single
     * {@code store.registered()} call because {@link #handlePairSuccess}
     * is invoked on a virtual thread per stanza and
     * {@link com.github.auties00.cobalt.store.WhatsAppStore#setRegistered(boolean)}
     * is flipped at the very end of a successful run.
     *
     * @param iqNode the full IQ stanza containing the pair-success child
     * @implNote WAWebHandlePairSuccess.default (h/y function)
     */
    private void handlePairSuccess(Node iqNode) {
        if (whatsapp.store().registered()) { // WAWebHandlePairSuccess.y: if (!(g || WAWebUserPrefsMultiDevice.isRegistered())) { ... }
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring pair-success iq: store already registered");
            return;
        }

        synchronized (rotationLock) {
            cancelRotationLocked(); // ADAPTED: WAWebHandlePairDevice.g timer checks isRegistered() and cancels; we cancel eagerly once pair-success arrives
        }

        // WAWebHandlePairSuccess.y: var a = WATimeUtils.unixTimeWithoutClockSkewCorrection() captured
        // at entry and threaded into initDeviceLinkEvent / commitDeviceLinkEvent as the
        // `regStartTime` baseline. Cobalt captures it here so the mdDurationS/mdTimestampS
        // deltas on the MdLinkDeviceCompanionEvent commits below are consistent across stages.
        var regStartSeconds = Instant.now().getEpochSecond();

        var pairSuccess = iqNode.getChild("pair-success").orElse(null); // WAWebHandlePairSuccess: receiveSetRegRPC extracts pair-success child
        if (pairSuccess == null) {
            LOGGER.log(System.Logger.Level.WARNING, "Received md iq without pair-success child"); // NO_WA_BASIS: defensive check
            return;
        }

        var store = whatsapp.store();

        resolvePairedJid(pairSuccess, false).ifPresent(jid -> { // WAWebHandlePairSuccess: setMe(deviceJidToDeviceWid(y))
            store.setJid(jid); // WAWebHandlePairSuccess: setMe(deviceJidToDeviceWid(y))
            if (store.phoneNumber().isEmpty()) { // ADAPTED: WA Web does not set phone number from JID
                try {
                    store.setPhoneNumber(Long.parseLong(jid.user()));
                } catch (NumberFormatException ignored) {
                }
            }
        });
        resolvePairedJid(pairSuccess, true).ifPresent(store::setLid); // WAWebHandlePairSuccess: b != null && setMeLid(deviceJidToDeviceWid(b))

        var validatedIdentity = deviceService.extractAndValidateLocalSignedDeviceIdentity(pairSuccess) // WAWebHandlePairSuccess: decode HMAC, verify, generate device signature
                .orElse(null);

        if (validatedIdentity == null) {
            // WAWebHandlePairSuccess.y: if HMAC fails, m() returns without committing a link
            // event; if verifyDeviceIdentityAccountSignature fails, commitDeviceLinkEvent(401)
            // fires. Cobalt's DeviceService.extractAndValidateLocalSignedDeviceIdentity
            // collapses both failures into an empty Optional (the underlying
            // WhatsAppAdvValidationException is handed to the client error handler).
            // Emit a generic commit with errorCode=-1 so the telemetry pipeline still records
            // a failed pairing attempt; 401-vs-HMAC distinction is not preserved here because
            // the validator does not surface the specific sub-type to this handler.
            emitMdLinkDeviceCompanionStage(null, -1, null, regStartSeconds);
            return;
        }

        // WAWebHandlePairSuccess.y: var w = yield initDeviceLinkEvent(P, M.identityKeyPair.pubKey, a);
        // yield setDeviceLinkPairStage(PAIR_SUCCESS_RECEIVED).
        // P is the accountSignatureKey from the decoded SignedDeviceIdentity (available once
        // validation passes); M.identityKeyPair.pubKey is the local companion identity key.
        // The reporter hashes both into mdSessionId and emits the PAIR_SUCCESS_RECEIVED stage.
        var mdSessionId = computeMdLinkSessionId(
                validatedIdentity.accountSignatureKey().orElse(null),
                store.identityKeyPair().publicKey().toEncodedPoint()
        );
        emitMdLinkDeviceCompanionStage(MdLinkDeviceCompanionStage.PAIR_SUCCESS_RECEIVED, null, mdSessionId, regStartSeconds);

        try {
            // WAWebHandlePairSuccess: yield waSignalStore.putIdentity(createSignalAddress(asUserWidOrThrow(deviceJidToDeviceWid(y))).toString(), bufferToStr(toSignalCurvePubKey(P)))
            // Persist the accountSignatureKey as the local user's signal identity (device 0) so
            // subsequent ADV validations resolve the primary identity from the local store.
            store.jid().ifPresent(localJid -> deviceService.persistLocalDeviceIdentityFromPairSuccess(
                    localJid, validatedIdentity.accountSignatureKey().orElse(null)));
            store.setSignedDeviceIdentity(validatedIdentity); // WAWebHandlePairSuccess: setADVSignedIdentity($)
            sendPairSuccessResponse(iqNode, validatedIdentity); // WAWebHandlePairSuccess: return q = d({deviceIdentityElementValue: W, deviceIdentityKeyIndex: B})

            // WAWebHandlePairSuccess.y: yield setDeviceLinkPairStage(PAIR_DEVICE_SIGN_SENT).
            // Emitted after the signed identity response has been sent back to the server;
            // WA Web performs the commit immediately after assembling q and before the next
            // yield, so we mirror that ordering by committing right after sendPairSuccessResponse.
            emitMdLinkDeviceCompanionStage(MdLinkDeviceCompanionStage.PAIR_DEVICE_SIGN_SENT, null, mdSessionId, regStartSeconds);

            extractPairingProps(pairSuccess) // WAWebHandlePairSuccess: if (_ != null) yield C(_)
                    .ifPresent(props -> {
                        snapshotRecoveryService.updatePrimaryDeviceSupportsSyncdRecovery(props.isSyncdSnapshotRecoveryEnabled()); // WAWebHandlePairSuccess.C/b: updatePrimaryDeviceSupportsSyncdRecovery(i === true)
                        // WAWebHandlePairSuccess.C/b: n === true && (
                        //   yield Lid1X1MigrationUtils.setIsLidMigrated(true, LidMigrationSource.HISTORY, a),
                        //   new Lid11MigrationLifecycleWamEvent({
                        //     migrationStage: COMPANION_MIGRATED_ON_NEW_PAIRING,
                        //     webClientDidPairingStanzaIndicated1x1MigrationThisSession: true,
                        //     isLocally1x1MigratedFromDb: Lid1X1MigrationUtils.isLidMigrated()
                        //   }).commit()
                        // )
                        // Cobalt routes the isChatDbLidMigrated flag through the migration service and
                        // mirrors WA Web's unconditional WAM commit with the post-setIsLidMigrated read.
                        if (props.isChatDbLidMigrated()) {
                            whatsapp.wamService().commit(new Lid11MigrationLifecycleEventBuilder()
                                    .migrationStage(MigrationStageEnum.COMPANION_MIGRATED_ON_NEW_PAIRING)
                                    .webClientDidPairingStanzaIndicated1x1MigrationThisSession(true)
                                    .isLocally1x1MigratedFromDb(whatsapp.lidMigrationService().isLidMigrated())
                                    .build());
                        }
                    });
            store.setRegistered(true); // WAWebHandlePairSuccess: setPairingTimestamp(a), marks as registered
            store.setOnline(true); // ADAPTED: Cobalt sets online flag
            safeSave("pair-success"); // ADAPTED: Cobalt persists store
        } catch (RuntimeException exception) {
            // WAWebHandlePairSuccess.y catch branch: yield commitDeviceLinkEvent(-1) before
            // logging out via socketLogout(LogoutReason.UnknownCompanion). Cobalt does not
            // swallow the underlying exception — it is rethrown after the WAM emission so
            // upstream error handling (client.handleFailure / socket teardown) still runs.
            emitMdLinkDeviceCompanionStage(null, -1, mdSessionId, regStartSeconds);
            throw exception;
        }
    }

    /**
     * Computes the {@code mdSessionId} used on the MdLinkDeviceCompanion WAM event.
     *
     * <p>Mirrors WAWebWamDeviceLinkReporter's {@code v(e, t)} helper which concatenates the
     * account signature key, a {@code 0x5f} separator, and the local companion identity key
     * public bytes, SHA-256 hashes the buffer, and base64-encodes the digest. The result
     * uniquely identifies a pairing attempt across stage emissions.
     *
     * @param accountSignatureKey the account signature key from the validated
     *                            {@link ADVSignedDeviceIdentity}, may be {@code null} when
     *                            the identity did not embed one (in which case the session id
     *                            cannot be computed and a {@code null} marker is returned)
     * @param localIdentityKey    the local companion identity key public bytes
     * @return the base64-encoded SHA-256 session id, or {@code null} when the input cannot
     *         produce a deterministic hash
     * @implNote WAWebWamDeviceLinkReporter.v: new Binary; writeBuffer(e); write(95); writeBuffer(t);
     *           var r = n.readByteArrayView(); var a = yield sha256(r); return encodeB64(a).
     */
    private String computeMdLinkSessionId(byte[] accountSignatureKey, byte[] localIdentityKey) {
        if (accountSignatureKey == null || localIdentityKey == null) {
            return null;
        }

        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(accountSignatureKey);
            digest.update((byte) 0x5f); // WAWebWamDeviceLinkReporter.v: n.write(95)
            digest.update(localIdentityKey);
            return Base64.getEncoder().encodeToString(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 is required by the JRE spec; this path is unreachable on any
            // conforming platform. Log and fall through to a null session id so the
            // telemetry emission can still run without the sessionId field populated.
            LOGGER.log(System.Logger.Level.WARNING, "SHA-256 is not available: {0}", exception.getMessage());
            return null;
        }
    }

    /**
     * Commits a {@link com.github.auties00.cobalt.wam.event.MdLinkDeviceCompanionEvent} with
     * the given stage and optional error code.
     *
     * <p>Every stage commit rebuilds the event from the pinned
     * {@code regStartSeconds}/{@code mdSessionId} context so each emission carries the
     * cumulative duration and timestamp. A {@code null} stage with a non-null error code
     * corresponds to the WA Web {@code commitDeviceLinkEvent(errorCode)} path taken when the
     * pairing flow aborts before the stage machine has advanced further.
     *
     * <p>Only the subset of properties that Cobalt can populate deterministically is set:
     * {@code mdSessionId}, {@code mdTimestampS}, {@code mdDurationS},
     * {@code mdLinkDeviceCompanionStage}, and {@code mdLinkDeviceCompanionErrorCode}.
     * The remaining fields ({@code mdLinkDeviceExperienceId}, {@code userLocale},
     * {@code applicationState}, {@code appContext}, etc.) are left unset because their
     * source modules ({@code WAWebLinkDeviceExperience}, {@code WAWebAppTracker}) have no
     * Cobalt counterpart — WA Web attaches them via {@code attachWAMAppContext} right before
     * {@code commitAndWaitForFlush}, a frontend-only helper that Cobalt does not model.
     *
     * @param stage            the stage to record, or {@code null} for a raw error-code
     *                         commit
     * @param errorCode        the error code to attach, or {@code null} for a normal stage
     *                         transition
     * @param mdSessionId      the deterministic session id computed by
     *                         {@link #computeMdLinkSessionId}, may be {@code null} when the
     *                         session id could not be derived
     * @param regStartSeconds  the Unix-seconds timestamp captured at pair-success entry
     * @implNote WAWebWamDeviceLinkReporter.R (commitDeviceLinkEvent): rebuilds the event with
     *           {mdDurationS, mdSessionId, mdTimestampS, mdLinkDeviceCompanionErrorCode,
     *           mdLinkDeviceCompanionStage, mdLinkDeviceExperienceId}, attaches app context,
     *           then commits. Cobalt omits the app-context attachment because Cobalt has no
     *           WAWebAppTracker analogue.
     */
    private void emitMdLinkDeviceCompanionStage(MdLinkDeviceCompanionStage stage, Integer errorCode, String mdSessionId, long regStartSeconds) {
        try {
            var nowSeconds = Instant.now().getEpochSecond();
            var builder = new MdLinkDeviceCompanionEventBuilder()
                    .mdTimestampS((int) regStartSeconds) // WAWebWamDeviceLinkReporter.R: mdTimestampS: r.regStartTime
                    .mdDurationS((int) (nowSeconds - regStartSeconds)) // WAWebWamDeviceLinkReporter.R: mdDurationS: unixTimeWithoutClockSkewCorrection() - r.regStartTime
                    .mdLinkDeviceCompanionErrorCode(errorCode != null ? errorCode : 0) // WAWebWamDeviceLinkReporter.R: mdLinkDeviceCompanionErrorCode: t == null ? 0 : t
                    .mdLinkDeviceCompanionStage(stage); // WAWebWamDeviceLinkReporter.R: mdLinkDeviceCompanionStage: i (u, the last stored stage)
            if (mdSessionId != null) {
                builder.mdSessionId(mdSessionId); // WAWebWamDeviceLinkReporter.R: mdSessionId: r.sessionId
            }
            whatsapp.wamService().commit(builder.build()); // WAWebWamDeviceLinkReporter.R: l.commitAndWaitForFlush(true)
        } catch (RuntimeException wamException) {
            // Telemetry emission must never disrupt the pairing flow: log and swallow.
            LOGGER.log(System.Logger.Level.WARNING, "Cannot commit MdLinkDeviceCompanion event: {0}", wamException.getMessage());
        }
    }

    /**
     * Sends the pair-success IQ result response containing the signed
     * device identity with the device signature and key index.
     *
     * <p>Per WA Web, the response clears the {@code accountSignatureKey}
     * from the signed identity before encoding, then wraps it in a
     * {@code pair-device-sign > device-identity} node structure.
     *
     * @param iqNode            the original pair-success IQ stanza
     * @param validatedIdentity the validated and signed device identity
     * @implNote WAWebHandlePairSuccess: $.accountSignatureKey = void 0;
     *           var W = encodeProtobuf(ADVSignedDeviceIdentitySpec, $).readByteArrayView();
     *           var q = d({deviceIdentityElementValue: W, deviceIdentityKeyIndex: B});
     *           WASmaxOutMdSetRegResponseClientResponse.makeSetRegResponseClientResponse
     */
    private void sendPairSuccessResponse(Node iqNode, ADVSignedDeviceIdentity validatedIdentity) {
        var id = iqNode.getAttributeAsString("id", null); // WASmaxOutMdSetRegResponseClientResponse: attrFromReference(attrStanzaId, t, ["id"])
        if (id == null) {
            LOGGER.log(System.Logger.Level.DEBUG, "Cannot send pair-success response: missing id"); // NO_WA_BASIS: defensive check
            return;
        }

        // WAWebHandlePairSuccess: var O = decodeProtobuf(ADVDeviceIdentitySpec, $.details); var B = O.keyIndex
        var details = validatedIdentity.details().orElse(null);
        if (details == null) {
            LOGGER.log(System.Logger.Level.WARNING, "Cannot send pair-success response: missing details in validated identity"); // NO_WA_BASIS: defensive check
            return;
        }

        int keyIndex;
        try {
            var innerIdentity = ADVDeviceIdentitySpec.decode(details); // WAWebHandlePairSuccess: decodeProtobuf(ADVDeviceIdentitySpec, $.details)
            keyIndex = innerIdentity.keyIndex().orElseThrow(() -> // WAWebHandlePairSuccess: B != null || s(0, 56297)
                    new NullPointerException("keyIndex cannot be null"));
        } catch (Exception exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Cannot send pair-success response: failed to decode inner device identity: {0}", exception.getMessage()); // NO_WA_BASIS: defensive check
            return;
        }

        // WAWebHandlePairSuccess: $.accountSignatureKey = void 0
        var identityForResponse = new ADVSignedDeviceIdentityBuilder()
                .details(details)
                .accountSignature(validatedIdentity.accountSignature().orElse(null)) // WAWebHandlePairSuccess: keeps accountSignature
                .deviceSignature(validatedIdentity.deviceSignature().orElse(null)) // WAWebHandlePairSuccess: keeps deviceSignature (generated earlier)
                // accountSignatureKey intentionally omitted, WAWebHandlePairSuccess: $.accountSignatureKey = void 0
                .build();

        var encodedIdentity = ADVSignedDeviceIdentitySpec.encode(identityForResponse); // WAWebHandlePairSuccess: encodeProtobuf(ADVSignedDeviceIdentitySpec, $).readByteArrayView()

        // WASmaxOutMdRegularCompanionSetRegResponseBundleMixin: smax("pair-device-sign", null, smax("device-identity", {"key-index": INT(a)}, i))
        var deviceIdentityNode = new NodeBuilder()
                .description("device-identity")
                .attribute("key-index", keyIndex) // WASmaxOutMdRegularCompanionSetRegResponseBundleMixin: "key-index": INT(a)
                .content(encodedIdentity) // WASmaxOutMdRegularCompanionSetRegResponseBundleMixin: content is deviceIdentityElementValue
                .build();

        var pairDeviceSignNode = new NodeBuilder() // WASmaxOutMdRegularCompanionSetRegResponseBundleMixin: smax("pair-device-sign", null, ...)
                .description("pair-device-sign")
                .content(deviceIdentityNode)
                .build();

        var response = new NodeBuilder() // WASmaxOutMdSetRegResponseClientResponse: smax("iq", {id: STANZA_ID(n.value), to: S_WHATSAPP_NET, type: "result"})
                .description("iq")
                .attribute("id", id) // WASmaxOutMdSetRegResponseClientResponse: id: STANZA_ID(n.value)
                .attribute("to", Jid.userServer()) // WASmaxOutMdSetRegResponseClientResponse: to: S_WHATSAPP_NET
                .attribute("type", "result") // WASmaxOutMdSetRegResponseClientResponse: type: "result"
                .content(pairDeviceSignNode) // WASmaxOutMdRegularCompanionSetRegResponseBundleMixin: mergeStanzas appends smax$any children
                .build();

        whatsapp.sendNodeWithNoResponse(response); // ADAPTED: WA Web returns node through WAComms.castStanza
    }

    /**
     * Resolves the paired device JID or LID from a pair-success node.
     *
     * <p>Searches for the JID in node attributes first, then falls back to
     * child node attributes and content parsing.
     *
     * @param node the pair-success node to search
     * @param lid  {@code true} to resolve the LID, {@code false} for the device JID
     * @return an {@code Optional} containing the resolved JID, or empty if not found
     * @implNote WAWebHandlePairSuccess: p.pairSuccessDeviceJid (jid=false), p.pairSuccessDeviceLid (lid=true)
     */
    private Optional<Jid> resolvePairedJid(Node node, boolean lid) {
        return node.getChild("device")
                .flatMap(device -> device.getAttributeAsJid(lid ? "lid" : "jid"));
    }

    /**
     * Extracts and decodes the client pairing props protobuf from a
     * pair-success node.
     *
     * <p>Searches the pair-success node itself and any {@code props} child
     * nodes for a valid {@link ClientPairingProps} protobuf.
     *
     * @param pairSuccess the pair-success node
     * @return an {@code Optional} containing the decoded pairing props, or empty if not found
     * @implNote WAWebHandlePairSuccess: var _ = p.pairSuccessClientProps; if (_ != null) yield C(_)
     */
    private Optional<ClientPairingProps> extractPairingProps(Node pairSuccess) {
        var candidates = new ArrayList<Node>();
        candidates.add(pairSuccess);
        for (var child : pairSuccess.children()) {
            if ("props".equals(child.description())) { // ADAPTED: WA Web SMAX parsing uses pairSuccessClientProps field
                candidates.add(child);
            }
        }

        for (var candidate : candidates) {
            var bytes = candidate.toContentBytes().orElse(null);
            if (bytes == null || bytes.length == 0) {
                continue;
            }

            try {
                return Optional.of(ClientPairingPropsSpec.decode(bytes)); // WAWebHandlePairSuccess.C/b: decodeProtobuf(ClientPairingPropsSpec, e.elementValue)
            } catch (Throwable ignored) {
            }
        }

        return Optional.empty();
    }

    /**
     * Cancels the current rotation task if one is active.
     *
     * <p>Must be called while holding {@link #rotationLock}.
     *
     * @implNote WAWebHandlePairDevice ShiftTimer callback: m && m.cancel(), m = null
     */
    private void cancelRotationLocked() {
        var task = rotationTask;
        if (task != null) {
            task.cancel(false);
            rotationTask = null;
        }
    }

    /**
     * Resets handler state by cancelling any active rotation.
     *
     * @implNote ADAPTED: WAWebHandlePairDevice: module-level var m = null, d = [] reset on reconnect
     */
    @Override
    public void reset() {
        synchronized (rotationLock) {
            cancelRotationLocked();
        }
    }

    /**
     * Searches a node's attributes for the first non-blank string value
     * matching one of the given keys.
     *
     * @param node the node to search
     * @param keys the attribute keys to try, in order
     * @return an {@code Optional} containing the first non-blank value found, or empty
     * @implNote NO_WA_BASIS: utility for flexible attribute extraction
     */
    private Optional<String> findStringAttribute(Node node, String... keys) {
        for (var key : keys) {
            var value = node.getAttributeAsString(key).orElse(null);
            if (value != null && !value.isBlank()) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /**
     * Searches a node's attributes for the first JID value matching one of
     * the given keys.
     *
     * @param node the node to search
     * @param keys the attribute keys to try, in order
     * @return an {@code Optional} containing the first JID found, or empty
     * @implNote NO_WA_BASIS: utility for flexible JID attribute extraction
     */
    private Optional<Jid> findJidAttribute(Node node, String... keys) {
        for (var key : keys) {
            var value = node.getAttributeAsJid(key).orElse(null);
            if (value != null) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    /**
     * Attempts to decode a node's content as a UTF-8 string.
     *
     * <p>First tries the string content accessor, then falls back to
     * decoding the raw bytes as UTF-8.
     *
     * @param node the node whose content to decode
     * @return an {@code Optional} containing the non-blank string content, or empty
     * @implNote WASmaxInMdSetToCompanionRequest.e: contentBytes(e) -> Binary(e.elementValue).readString(t.size())
     */
    private Optional<String> decodeContentAsString(Node node) {
        var text = node.toContentString().orElse(null);
        if (text != null && !text.isBlank()) {
            return Optional.of(text);
        }

        var bytes = node.toContentBytes().orElse(null);
        if (bytes == null || bytes.length == 0) {
            return Optional.empty();
        }

        var decoded = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        return decoded.isBlank() ? Optional.empty() : Optional.of(decoded);
    }

    /**
     * Persists the store, logging any failure without propagating.
     *
     * @param context a human-readable context string for log messages
     * @implNote NO_WA_BASIS: Cobalt-specific store persistence
     */
    private void safeSave(String context) {
        try {
            whatsapp.store().save();
        } catch (Exception exception) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "{0}: failed to persist store: {1}",
                    context,
                    exception.getMessage());
        }
    }
}
