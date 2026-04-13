package com.github.auties00.cobalt.stream.iq;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientVerificationHandler;
import com.github.auties00.cobalt.device.DeviceService;
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
import com.github.auties00.cobalt.util.FastRandomUtils;

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
 *   <li>{@code urn:xmpp:ping} -- responds with an IQ result</li>
 *   <li>{@code md} -- dispatches to pair-device or pair-success handlers</li>
 * </ul>
 *
 * @implNote WAWebHandleStanzaCommon.handleIq, WAWebHandlePairDevice.default, WAWebHandlePairSuccess.default
 */
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
            SnapshotRecoveryService snapshotRecoveryService
    ) {
        this.whatsapp = whatsapp;
        this.webVerificationHandler = Objects.requireNonNull(webVerificationHandler, "webVerificationHandler cannot be null");
        this.deviceService = Objects.requireNonNull(deviceService, "deviceService cannot be null");
        this.snapshotRecoveryService = Objects.requireNonNull(snapshotRecoveryService, "snapshotRecoveryService cannot be null");
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
            case "pair-device" -> handlePairDevice(node); // WAWebHandleStanzaCommon.handleIq: return r("WAWebHandlePairDevice")(e) -- passes full IQ
            case "pair-success" -> handlePairSuccess(node); // WAWebHandleStanzaCommon.handleIq: return r("WAWebHandlePairSuccess")(e) -- passes full IQ
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

        whatsapp.store().setAdvSecretKey(FastRandomUtils.randomByteArray(32)); // WAWebHandlePairDevice.g: yield o("WAWebAdvSignatureApi").generateADVSecretKey()

        var refs = extractPairRefs(pairDevice); // WAWebHandlePairDevice._: c.pairDeviceRef.map(function(e) { ... readString(t.size()) })
        if (refs.isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING, "Received pair-device iq without any usable refs"); // NO_WA_BASIS: defensive check
            return;
        }

        scheduleVerificationValues(refs); // WAWebHandlePairDevice.g: f(d) -- schedules timer rotation
        sendPairDeviceAck(iqNode); // WAWebHandlePairDevice._: u() -- makeSetToCompanionResponseClientResponse
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
                .attribute("to", Jid.userServer()) // WASmaxOutMdSetToCompanionResponseClientResponse: to: DOMAIN_JID(n.value) -- domain JID is s.whatsapp.net
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
     * <p>Publishes the first ref immediately, then schedules a fixed-rate
     * rotation for remaining refs. The rotation interval is
     * {@link #QR_ROTATION_MS} when 6 refs are present (initial QR display),
     * or {@link #REFRESH_ROTATION_MS} otherwise (refresh rotation).
     *
     * @param refs the ordered set of ref strings to rotate through
     * @implNote WAWebHandlePairDevice.g: d = e; m.forceRunNow() with ShiftTimer callback that calls d.shift()
     */
    private void scheduleVerificationValues(LinkedHashSet<String> refs) {
        synchronized (rotationLock) {
            cancelRotationLocked(); // WAWebHandlePairDevice.g: reuses single ShiftTimer instance m

            var queue = new ArrayDeque<>(refs);
            var rotationDelay = refs.size() == 6 ? QR_ROTATION_MS : REFRESH_ROTATION_MS; // WAWebHandlePairDevice ShiftTimer callback: var e = d.length === 6 ? u : c

            publishVerificationValue(queue.pollFirst()); // WAWebHandlePairDevice ShiftTimer callback: var t = d.shift(); Conn.set({ref: t, refTTL: e})
            if (queue.isEmpty()) {
                return;
            }

            rotationTask = rotationExecutor.scheduleAtFixedRate(() -> { // ADAPTED: WAWebHandlePairDevice uses ShiftTimer.onOrAfter(e)
                String next;
                synchronized (rotationLock) {
                    next = queue.pollFirst(); // WAWebHandlePairDevice ShiftTimer callback: var t = d.shift()
                    if (next == null) {
                        cancelRotationLocked(); // WAWebHandlePairDevice ShiftTimer callback: m && m.cancel(), m = null
                        return;
                    }
                }

                publishVerificationValue(next); // WAWebHandlePairDevice ShiftTimer callback: Conn.set({ref: t, refTTL: e})

                synchronized (rotationLock) {
                    if (queue.isEmpty()) {
                        cancelRotationLocked(); // WAWebHandlePairDevice ShiftTimer callback: if (!d || !d.length) { m.cancel(), m = null }
                    }
                }
            }, rotationDelay, rotationDelay, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Publishes a single verification value (QR ref or pairing code) to the
     * web verification handler.
     *
     * <p>For QR code handlers, the ref is combined with cryptographic keys
     * into a comma-separated payload via {@link #buildQrPayload(String)}.
     * For pairing code handlers, the raw ref is delivered directly.
     *
     * @param ref the ref string to publish, or {@code null} to skip
     * @implNote WAWebHandlePairDevice ShiftTimer callback: Conn.set({ref: t, refTTL: e}),
     *           BackendEventBus.triggerSetSocketState(SOCKET_STATE.UNPAIRED)
     */
    private void publishVerificationValue(String ref) {
        if (ref == null || ref.isBlank()) { // NO_WA_BASIS: defensive null/blank check
            return;
        }

        var payload = webVerificationHandler instanceof WhatsAppClientVerificationHandler.Web.QrCode // ADAPTED: WA Web stores raw ref in Conn.ref; QR assembly done in UI layer
                ? buildQrPayload(ref)
                : ref;
        webVerificationHandler.handle(payload); // ADAPTED: WA Web triggers via BackendEventBus
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
        var advSecret = store.advSecretKey().orElseGet(() -> { // WAWebHandlePairDevice.g: yield generateADVSecretKey() -- already generated
            var generated = FastRandomUtils.randomByteArray(32); // NO_WA_BASIS: fallback generation
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
     * @param iqNode the full IQ stanza containing the pair-success child
     * @implNote WAWebHandlePairSuccess.default (h/y function)
     */
    private void handlePairSuccess(Node iqNode) {
        synchronized (rotationLock) {
            cancelRotationLocked(); // ADAPTED: WAWebHandlePairSuccess.y: g = true prevents re-entry; Cobalt cancels rotation
        }

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
        if (validatedIdentity != null) {
            store.setSignedDeviceIdentity(validatedIdentity); // WAWebHandlePairSuccess: setADVSignedIdentity($)
            sendPairSuccessResponse(iqNode, validatedIdentity); // WAWebHandlePairSuccess: return q = d({deviceIdentityElementValue: W, deviceIdentityKeyIndex: B})
        }

        extractPairingProps(pairSuccess) // WAWebHandlePairSuccess: if (_ != null) yield C(_)
                .ifPresent(props -> snapshotRecoveryService.updatePrimaryDeviceSupportsSyncdRecovery(props.isSyncdSnapshotRecoveryEnabled())); // WAWebHandlePairSuccess.C/b: updatePrimaryDeviceSupportsSyncdRecovery(i === true)
        store.setRegistered(true); // WAWebHandlePairSuccess: setPairingTimestamp(a) -- marks as registered
        store.setOnline(true); // ADAPTED: Cobalt sets online flag
        safeSave("pair-success"); // ADAPTED: Cobalt persists store
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
                // accountSignatureKey intentionally omitted -- WAWebHandlePairSuccess: $.accountSignatureKey = void 0
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
        var attrNames = lid // WAWebHandlePairSuccess: pairSuccessDeviceLid vs pairSuccessDeviceJid
                ? new String[]{"lid", "device_lid", "user_lid"}
                : new String[]{"jid", "device_jid", "user_jid"};

        var direct = findJidAttribute(node, attrNames).orElse(null);
        if (direct != null) {
            return Optional.of(direct);
        }

        for (var child : node.children()) {
            var fromChild = findJidAttribute(child, attrNames).orElse(null);
            if (fromChild != null) {
                return Optional.of(fromChild);
            }

            var content = decodeContentAsString(child).orElse(null);
            if (content == null || content.isBlank()) {
                continue;
            }

            try {
                var parsed = Jid.of(content);
                var matches = lid
                        ? parsed.hasLidServer() || parsed.hasHostedLidServer()
                        : !parsed.hasLidServer() && !parsed.hasHostedLidServer();
                if (matches) {
                    return Optional.of(parsed);
                }
            } catch (RuntimeException ignored) {
            }
        }

        return Optional.empty();
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
