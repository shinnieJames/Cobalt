package com.github.auties00.cobalt.stream.iq;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientVerificationHandler;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.migration.LidMigrationService;
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
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.Lid11MigrationLifecycleEventBuilder;
import com.github.auties00.cobalt.wam.event.MdLinkDeviceCompanionEventBuilder;
import com.github.auties00.cobalt.wam.type.MdLinkDeviceCompanionStage;
import com.github.auties00.cobalt.wam.type.MigrationStageEnum;

import java.nio.charset.StandardCharsets;
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
 * Handles {@code <iq>} (info/query) stanzas pushed by the server,
 * specifically the pairing-flow IQs ({@code md} xmlns) and the
 * keep-alive pings ({@code urn:xmpp:ping} xmlns).
 *
 * @apiNote
 * This handler is registered under the {@code "iq"} tag inside
 * {@link SocketStream} and routes server-initiated {@code <iq>} stanzas
 * based on their {@code xmlns} attribute. The two supported namespaces
 * are {@code urn:xmpp:ping} (responded to with an empty
 * {@code <iq type="result">}) and {@code md} (the multi-device
 * companion-pairing exchange that lands the QR ref rotation and the
 * follow-up pair-success exchange). Response IQs for outbound requests
 * are not routed here; they flow through the per-call request/response
 * correlator on {@link WhatsAppClient}.
 *
 * @implNote
 * This implementation collapses WA Web's
 * {@link WhatsAppWebModule WAWebHandlePairDevice} and
 * {@link WhatsAppWebModule WAWebHandlePairSuccess} entry points into a
 * single handler keyed on the {@code <iq>} child tag, and schedules the
 * QR ref rotation on a dedicated daemon
 * {@link ScheduledExecutorService} rather than WA Web's
 * {@code WAShiftTimer}. The {@code mdSessionId} computation,
 * {@code MdLinkDeviceCompanion} WAM stage emissions and
 * {@code Lid11MigrationLifecycle} WAM commit on pair-success mirror the
 * upstream reporter logic exactly so the device-link funnel landed in
 * the WhatsApp dashboards matches the official client.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleStanzaCommon")
@WhatsAppWebModule(moduleName = "WAWebHandlePairDevice")
@WhatsAppWebModule(moduleName = "WAWebHandlePairSuccess")
public final class IqStreamHandler implements SocketStream.Handler {

    /**
     * The system logger used for diagnostic output during
     * pairing-flow IQ processing.
     */
    private static final System.Logger LOGGER = System.getLogger(IqStreamHandler.class.getName());

    /**
     * The display duration in milliseconds for the first QR ref of a
     * fresh pairing exchange.
     *
     * @apiNote
     * Matches WA Web's {@code u = 6e4} constant: the initial ref is
     * displayed for 60 seconds before the rotation falls back to
     * {@link #REFRESH_ROTATION_MS}.
     */
    private static final long QR_ROTATION_MS = 60_000L;

    /**
     * The display duration in milliseconds for every refresh QR ref
     * after the first.
     *
     * @apiNote
     * Matches WA Web's {@code c = 20*1e3} constant: refresh refs cycle
     * every 20 seconds until the user scans one or the server stops
     * pushing new refs.
     */
    private static final long REFRESH_ROTATION_MS = 20_000L;

    /**
     * The {@link WhatsAppClient} used for store access and outbound
     * stanza dispatch.
     */
    private final WhatsAppClient whatsapp;

    /**
     * The {@link WhatsAppClientVerificationHandler.Web} that receives
     * QR-payload strings during the QR pairing branch.
     */
    private final WhatsAppClientVerificationHandler.Web webVerificationHandler;

    /**
     * The {@link DeviceService} consulted for ADV identity validation
     * and local-identity persistence during pair-success.
     */
    private final DeviceService deviceService;

    /**
     * The {@link SnapshotRecoveryService} updated with the primary
     * device's syncd-snapshot-recovery support flag when pair-success
     * resolves.
     */
    private final SnapshotRecoveryService snapshotRecoveryService;

    /**
     * The {@link LidMigrationService} consulted when committing the
     * {@code Lid11MigrationLifecycle} WAM event after a successful
     * pairing.
     */
    private final LidMigrationService lidMigrationService;

    /**
     * The dedicated single-thread daemon executor that drives the QR
     * ref rotation ticks.
     */
    private final ScheduledExecutorService rotationExecutor;

    /**
     * The {@link CompanionPairingService} that owns the
     * alt-device-linking (phone-number pairing-code) handshake state.
     *
     * @apiNote
     * When {@link CompanionPairingService#isEnabled()} returns
     * {@code true} the QR ref rotation is skipped and the
     * {@code companion_hello} pairing-code flow is started instead.
     */
    private final CompanionPairingService deviceLinkingService;

    /**
     * The lock protecting {@link #rotationTask} so the rotation can be
     * cancelled atomically from both the executor thread and the
     * handler thread.
     */
    private final Object rotationLock;

    /**
     * The currently scheduled rotation tick, or {@code null} when no
     * rotation is active.
     */
    private ScheduledFuture<?> rotationTask;

    /**
     * The {@link WamService} used to commit the
     * {@code MdLinkDeviceCompanion} stage transitions and the
     * {@code Lid11MigrationLifecycle} event.
     */
    private final WamService wamService;

    /**
     * Constructs a new IQ stream handler bound to the given services.
     *
     * @apiNote
     * Cobalt embedders never call this constructor directly; the
     * dispatcher in {@link SocketStream} instantiates the handler once
     * per client.
     *
     * @param whatsapp                the {@link WhatsAppClient}; must
     *                                not be {@code null}
     * @param webVerificationHandler  the
     *                                {@link WhatsAppClientVerificationHandler.Web}
     *                                that receives QR/pairing payloads;
     *                                must not be {@code null}
     * @param deviceService           the {@link DeviceService} used for
     *                                ADV validation; must not be
     *                                {@code null}
     * @param snapshotRecoveryService the {@link SnapshotRecoveryService}
     *                                updated on pair-success; must not
     *                                be {@code null}
     * @param lidMigrationService     the {@link LidMigrationService}
     *                                consulted on pair-success; must
     *                                not be {@code null}
     * @param deviceLinkingService    the
     *                                {@link CompanionPairingService}
     *                                used to gate {@code pair-device}
     *                                handling; must not be {@code null}
     * @param wamService              the {@link WamService} used for
     *                                pairing-funnel telemetry; must
     *                                not be {@code null}
     * @throws NullPointerException if any service is {@code null}
     */
    public IqStreamHandler(
            WhatsAppClient whatsapp,
            WhatsAppClientVerificationHandler.Web webVerificationHandler,
            DeviceService deviceService,
            SnapshotRecoveryService snapshotRecoveryService,
            LidMigrationService lidMigrationService,
            CompanionPairingService deviceLinkingService,
            WamService wamService
    ) {
        this.whatsapp = whatsapp;
        this.webVerificationHandler = Objects.requireNonNull(webVerificationHandler, "webVerificationHandler cannot be null");
        this.deviceService = Objects.requireNonNull(deviceService, "deviceService cannot be null");
        this.snapshotRecoveryService = Objects.requireNonNull(snapshotRecoveryService, "snapshotRecoveryService cannot be null");
        this.lidMigrationService = Objects.requireNonNull(lidMigrationService, "lidMigrationService cannot be null");
        this.deviceLinkingService = Objects.requireNonNull(deviceLinkingService, "altDeviceLinkingService cannot be null");
        this.wamService = Objects.requireNonNull(wamService, "wamService cannot be null");
        this.rotationLock = new Object();
        this.rotationExecutor = Executors.newSingleThreadScheduledExecutor(runnable ->
                Thread.ofPlatform()
                        .daemon()
                        .name("CobaltPairDeviceRotation")
                        .unstarted(runnable)
        );
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Routes the incoming {@code <iq>} by its {@code xmlns} attribute:
     * {@code urn:xmpp:ping} replies with an empty
     * {@code <iq type="result">}, {@code md} dispatches to the
     * {@code pair-device} or {@code pair-success} branch based on the
     * first child tag, anything else is logged at {@code DEBUG} and
     * dropped.
     */
    @Override
    public void handle(Node node) {
        var xmlns = node.getAttributeAsString("xmlns", null);
        if ("urn:xmpp:ping".equals(xmlns)) {
            handlePing(node);
            return;
        }

        if (!"md".equals(xmlns)) {
            return;
        }

        var child = node.getChild().orElse(null);
        if (child == null) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring md iq without child: {0}", node);
            return;
        }

        switch (child.description()) {
            case "pair-device" -> handlePairDevice(node);
            case "pair-success" -> handlePairSuccess(node);
            default -> LOGGER.log(System.Logger.Level.DEBUG,
                    "Ignoring unsupported md iq child {0}", child.description());
        }
    }

    /**
     * Responds to a server-initiated {@code <iq xmlns="urn:xmpp:ping">}
     * keep-alive with an empty result IQ correlated by the inbound
     * {@code id}.
     *
     * @apiNote
     * The WhatsApp server occasionally pings the client to assert
     * liveness; failing to reply within the configured deadline causes
     * the server to drop the socket. The reply is fire-and-forget; the
     * server does not echo an ack.
     *
     * @param node the inbound ping {@code <iq>} stanza
     */
    private void handlePing(Node node) {
        var from = node.getAttributeAsJid("from").orElse(null);
        if (from == null) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring ping iq without from attribute");
            return;
        }

        var response = new NodeBuilder()
                .description("iq")
                .attribute("type", "result")
                .attribute("to", from)
                .attribute("id", node.getAttributeAsString("id", null))
                .build();
        whatsapp.sendNodeWithNoResponse(response);
    }

    /**
     * Handles the {@code <iq><pair-device/></iq>} stanza that opens the
     * QR or pairing-code flow by generating a fresh ADV secret key,
     * acking the IQ and either starting the alt-device-linking handler
     * or extracting and rotating the server refs.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@link WhatsAppWebModule WAWebHandlePairDevice}.default async
     * function: the inbound stanza carries up to six {@code <ref/>}
     * elements (the server is the source of truth for the QR ref
     * values) and the response is a typed
     * {@code makeSetToCompanionResponseClientResponse}. The Cobalt
     * branch instead constructs the ack inline through
     * {@link NodeBuilder} because the SMAX RPC indirection is not
     * needed for this exchange.
     *
     * @implNote
     * This implementation acks the IQ before either starting the
     * alt-device-linking flow or scheduling QR ref rotation so the
     * server stops retransmitting; if the alt-device-linking service
     * is enabled the QR ref rotation is skipped because pairing-code
     * pairing derives its own client-side code rather than displaying
     * a server ref.
     *
     * @param iqNode the full {@code <iq>} stanza containing the
     *               {@code <pair-device/>} child
     */
    private void handlePairDevice(Node iqNode) {
        var pairDevice = iqNode.getChild("pair-device").orElse(null);
        if (pairDevice == null) {
            LOGGER.log(System.Logger.Level.WARNING, "Received md iq without pair-device child");
            return;
        }

        whatsapp.store().setAdvSecretKey(DataUtils.randomByteArray(32));
        sendPairDeviceAck(iqNode);

        if (deviceLinkingService.isEnabled()) {
            try {
                deviceLinkingService.start();
            } catch (Throwable throwable) {
                LOGGER.log(System.Logger.Level.WARNING, "Cannot start alt-device-linking: {0}", throwable.getMessage());
            }
            return;
        }

        var refs = extractPairRefs(pairDevice);
        if (refs.isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING, "Received pair-device iq without any usable refs");
            return;
        }

        scheduleVerificationValues(refs);
    }

    /**
     * Sends the {@code <iq type="result">} ack for the inbound
     * {@code <pair-device/>} IQ correlated by the inbound {@code id}.
     *
     * @apiNote
     * Mirrors the {@code makeSetToCompanionResponseClientResponse}
     * envelope WA Web's
     * {@code WASmaxMdSetToCompanionRPC.receiveSetToCompanionRPC}
     * returns: an empty {@code <iq type="result">} routed to
     * {@link Jid#userServer()} (the {@code s.whatsapp.net} domain JID),
     * carrying the inbound stanza id for correlation.
     *
     * @param iqNode the original {@code <pair-device/>} IQ stanza
     */
    private void sendPairDeviceAck(Node iqNode) {
        var id = iqNode.getAttributeAsString("id", null);
        var from = iqNode.getAttributeAsJid("from").orElse(null);
        if (id == null || from == null) {
            LOGGER.log(System.Logger.Level.DEBUG, "Cannot send pair-device ack: missing id or from");
            return;
        }

        var response = new NodeBuilder()
                .description("iq")
                .attribute("id", id)
                .attribute("to", Jid.userServer())
                .attribute("type", "result")
                .build();
        whatsapp.sendNodeWithNoResponse(response);
    }

    /**
     * Extracts the ordered set of QR ref strings from a
     * {@code <pair-device/>} node by reading every {@code <ref/>}
     * child's binary content as UTF-8.
     *
     * @apiNote
     * WA Web's SMAX parser expects exactly six refs encoded as
     * Binary readString payloads. Cobalt is defensive: ref values may
     * also surface in the {@code ref}, {@code value} or {@code code}
     * attributes of either the parent or its children, so the parser
     * folds all four sources into an ordered set and rejects blanks.
     *
     * @implNote
     * This implementation uses {@link LinkedHashSet} rather than WA
     * Web's array because the defensive multi-source extraction would
     * otherwise emit duplicates when the same ref appears in both an
     * attribute and a content blob.
     *
     * @param pairDevice the {@code <pair-device/>} child node
     * @return the ordered set of non-blank ref strings; never
     *         {@code null}
     */
    private LinkedHashSet<String> extractPairRefs(Node pairDevice) {
        var refs = new LinkedHashSet<String>();
        decodeContentAsString(pairDevice).ifPresent(refs::add);

        for (var child : pairDevice.children()) {
            decodeContentAsString(child).ifPresent(refs::add);

            findStringAttribute(child, "ref", "value", "code")
                    .ifPresent(refs::add);
        }

        findStringAttribute(pairDevice, "ref", "value", "code")
                .ifPresent(refs::add);
        refs.removeIf(String::isBlank);
        return refs;
    }

    /**
     * Cancels any in-flight rotation and starts a fresh rotation cycle
     * over the given ref queue.
     *
     * @apiNote
     * Cobalt's entry point into the QR ref rotation; mirrors WA Web's
     * call to {@code m = new ShiftTimer(...); m.forceRunNow()} which
     * fires the first tick synchronously.
     *
     * @param refs the ordered set of ref strings to rotate through
     */
    private void scheduleVerificationValues(LinkedHashSet<String> refs) {
        synchronized (rotationLock) {
            cancelRotationLocked();
        }

        var queue = new ArrayDeque<>(refs);
        runRotationTick(queue);
    }

    /**
     * Runs a single rotation tick, mirroring the body of WA Web's
     * {@code ShiftTimer} callback.
     *
     * @apiNote
     * Each tick first checks the WA Web early-exit conditions (device
     * already registered or queue empty), then computes the next TTL
     * from the queue size (60 seconds while the queue holds all six
     * initial refs, 20 seconds for every refresh ref), pops the next
     * ref, publishes it to the
     * {@link WhatsAppClientVerificationHandler.Web} and reschedules
     * the next tick after the computed delay.
     *
     * @implNote
     * This implementation reschedules by cancelling the previous
     * {@link ScheduledFuture} and submitting a new one rather than
     * using WA Web's {@code WAShiftTimer.onOrAfter} primitive, because
     * the JDK has no native sliding-deadline scheduler.
     *
     * @param queue the mutable ref queue shared across ticks
     */
    private void runRotationTick(ArrayDeque<String> queue) {
        String next;
        long rotationDelay;
        synchronized (rotationLock) {
            cancelRotationLocked();

            if (whatsapp.store().registered()) {
                return;
            }

            if (queue.isEmpty()) {
                return;
            }

            rotationDelay = queue.size() == 6 ? QR_ROTATION_MS : REFRESH_ROTATION_MS;
            next = queue.pollFirst();
        }

        publishVerificationValue(next);

        synchronized (rotationLock) {
            if (queue.isEmpty()) {
                return;
            }

            rotationTask = rotationExecutor.schedule(
                    () -> runRotationTick(queue),
                    rotationDelay,
                    TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Publishes a single QR ref to the
     * {@link WhatsAppClientVerificationHandler.Web} by combining the
     * ref with the noise, identity and ADV keys into the
     * comma-separated payload WA Web stamps into the QR PNG.
     *
     * @apiNote
     * Only invoked on the QR branch; the pairing-code branch runs
     * through {@link CompanionPairingService}, which feeds the handler
     * a client-generated eight-character Crockford base32 code derived
     * from its own random bytes rather than any server ref.
     *
     * @implNote
     * WA Web stores the raw ref on {@code Conn.ref} and lets the React
     * component assemble the QR string; Cobalt builds the payload here
     * because the embedder receives the assembled string directly.
     *
     * @param ref the ref string to publish; ignored when {@code null}
     *            or blank
     */
    private void publishVerificationValue(String ref) {
        if (ref == null || ref.isBlank()) {
            return;
        }

        if (!(webVerificationHandler instanceof WhatsAppClientVerificationHandler.Web.QrCode)) {
            return;
        }
        webVerificationHandler.handle(buildQrPayload(ref));
    }

    /**
     * Builds the comma-separated QR payload string from the given ref,
     * the noise and identity public keys, the ADV secret key and the
     * client type tag.
     *
     * @apiNote
     * The resulting payload is the exact string the official WhatsApp
     * mobile app reads off the QR code: {@code <ref>,<noise>,<identity>,<adv>,<clientType>}.
     *
     * @implNote
     * This implementation regenerates an ADV secret key if the store
     * has not been seeded yet; the regenerated key is persisted before
     * the payload is assembled so a subsequent pair-success exchange
     * uses the same key.
     *
     * @param ref the QR ref from the server
     * @return the assembled QR payload string
     */
    private String buildQrPayload(String ref) {
        var store = whatsapp.store();
        var advSecret = store.advSecretKey().orElseGet(() -> {
            var generated = DataUtils.randomByteArray(32);
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
     * Handles the {@code <iq><pair-success/></iq>} stanza that lands
     * the validated identity exchange and finalises companion
     * registration.
     *
     * @apiNote
     * Mirrors WA Web's {@link WhatsAppWebModule WAWebHandlePairSuccess}.default
     * async function. The handler validates the ADV signed-device
     * identity, persists the resulting JID and LID, decodes the
     * companion pairing props, applies any embedded sub-state
     * (syncd-snapshot-recovery, 1-on-1 LID migration) and finally
     * responds with an {@code <iq><pair-device-sign/></iq>} containing
     * the device signature so the primary device can finish linking.
     *
     * @implNote
     * This implementation folds WA Web's module-level {@code g}
     * re-entry flag and the {@code WAWebUserPrefsMultiDevice.isRegistered()}
     * guard into a single {@code store.registered()} call because the
     * dispatcher already runs each stanza on its own virtual thread and
     * the registered flag is flipped at the end of a successful pair.
     * The funnel telemetry emissions
     * ({@link MdLinkDeviceCompanionStage#PAIR_SUCCESS_RECEIVED} after
     * validation, {@link MdLinkDeviceCompanionStage#PAIR_DEVICE_SIGN_SENT}
     * after the IQ response, error-code commit on validation failure
     * and rollback) mirror the upstream
     * {@code WAWebWamDeviceLinkReporter} sequence exactly so the
     * device-link funnel matches the official client. A
     * {@link RuntimeException} thrown by the response-sending path
     * emits the failure commit and is rethrown so the configured error
     * handler still runs.
     *
     * @param iqNode the full {@code <iq>} stanza containing the
     *               {@code <pair-success/>} child
     */
    private void handlePairSuccess(Node iqNode) {
        if (whatsapp.store().registered()) {
            LOGGER.log(System.Logger.Level.DEBUG, "Ignoring pair-success iq: store already registered");
            return;
        }

        synchronized (rotationLock) {
            cancelRotationLocked();
        }

        var regStartSeconds = Instant.now().getEpochSecond();

        var pairSuccess = iqNode.getChild("pair-success").orElse(null);
        if (pairSuccess == null) {
            LOGGER.log(System.Logger.Level.WARNING, "Received md iq without pair-success child");
            return;
        }

        var store = whatsapp.store();

        resolvePairedJid(pairSuccess, false).ifPresent(jid -> {
            store.setJid(jid);
            if (store.phoneNumber().isEmpty()) {
                try {
                    store.setPhoneNumber(Long.parseLong(jid.user()));
                } catch (NumberFormatException _) {
                }
            }
        });
        resolvePairedJid(pairSuccess, true).ifPresent(store::setLid);

        var validatedIdentity = deviceService.extractAndValidateLocalSignedDeviceIdentity(pairSuccess)
                .orElse(null);

        if (validatedIdentity == null) {
            emitMdLinkDeviceCompanionStage(null, -1, null, regStartSeconds);
            return;
        }

        var mdSessionId = computeMdLinkSessionId(
                validatedIdentity.accountSignatureKey().orElse(null),
                store.identityKeyPair().publicKey().toEncodedPoint()
        );
        emitMdLinkDeviceCompanionStage(MdLinkDeviceCompanionStage.PAIR_SUCCESS_RECEIVED, null, mdSessionId, regStartSeconds);

        try {
            store.jid().ifPresent(localJid -> deviceService.persistLocalDeviceIdentityFromPairSuccess(
                    localJid, validatedIdentity.accountSignatureKey().orElse(null)));
            store.setSignedDeviceIdentity(validatedIdentity);
            sendPairSuccessResponse(iqNode, validatedIdentity);

            emitMdLinkDeviceCompanionStage(MdLinkDeviceCompanionStage.PAIR_DEVICE_SIGN_SENT, null, mdSessionId, regStartSeconds);

            extractPairingProps(pairSuccess)
                    .ifPresent(props -> {
                        snapshotRecoveryService.updatePrimaryDeviceSupportsSyncdRecovery(props.isSyncdSnapshotRecoveryEnabled());
                        if (props.isChatDbLidMigrated()) {
                            wamService.commit(new Lid11MigrationLifecycleEventBuilder()
                                    .migrationStage(MigrationStageEnum.COMPANION_MIGRATED_ON_NEW_PAIRING)
                                    .webClientDidPairingStanzaIndicated1x1MigrationThisSession(true)
                                    .isLocally1x1MigratedFromDb(lidMigrationService.isLidMigrated())
                                    .build());
                        }
                    });
            store.setPairingTimestamp(Instant.ofEpochSecond(regStartSeconds));
            store.setRegistered(true);
            store.setOnline(true);
            safeSave("pair-success");
        } catch (RuntimeException exception) {
            emitMdLinkDeviceCompanionStage(null, -1, mdSessionId, regStartSeconds);
            throw exception;
        }
    }

    /**
     * Computes the {@code mdSessionId} that uniquely identifies a
     * pairing attempt across the device-link WAM stages.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebWamDeviceLinkReporter} private {@code v(e, t)}
     * helper: SHA-256 of {@code accountSignatureKey || 0x5f || localIdentityKey}
     * then base64 encode. The same session id is then threaded through
     * every {@link MdLinkDeviceCompanionStage} commit so the funnel
     * lines up across stages.
     *
     * @implNote
     * This implementation falls through to {@code null} when SHA-256
     * is unavailable on the JRE (an unreachable path on any conforming
     * platform) so the rest of the funnel can still emit without a
     * session id; WA Web cannot hit this branch because the browser's
     * Web Crypto API always provides SHA-256.
     *
     * @param accountSignatureKey the account signature key from the
     *                            validated
     *                            {@link ADVSignedDeviceIdentity}; may
     *                            be {@code null} when the identity did
     *                            not embed one
     * @param localIdentityKey    the local companion identity key
     *                            public bytes
     * @return the base64-encoded SHA-256 session id, or {@code null}
     *         when the input cannot produce a deterministic hash
     */
    private String computeMdLinkSessionId(byte[] accountSignatureKey, byte[] localIdentityKey) {
        if (accountSignatureKey == null || localIdentityKey == null) {
            return null;
        }

        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(accountSignatureKey);
            digest.update((byte) 0x5f);
            digest.update(localIdentityKey);
            return Base64.getEncoder().encodeToString(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "SHA-256 is not available: {0}", exception.getMessage());
            return null;
        }
    }

    /**
     * Commits one
     * {@link com.github.auties00.cobalt.wam.event.MdLinkDeviceCompanionEvent}
     * stage commit so the device-link funnel records the current
     * pairing checkpoint.
     *
     * @apiNote
     * Mirrors the per-stage emissions inside
     * {@code WAWebWamDeviceLinkReporter}. A {@code null} stage with a
     * non-{@code null} error code corresponds to the
     * {@code commitDeviceLinkEvent(errorCode)} branch taken when the
     * pairing flow aborts before the stage machine advances.
     *
     * @implNote
     * This implementation only populates the subset of properties
     * Cobalt can derive deterministically ({@code mdSessionId},
     * {@code mdTimestampS}, {@code mdDurationS},
     * {@code mdLinkDeviceCompanionStage},
     * {@code mdLinkDeviceCompanionErrorCode}); the WA-Web-only fields
     * ({@code mdLinkDeviceExperienceId}, {@code userLocale},
     * {@code applicationState}, {@code appContext}) are left unset
     * because their source modules ({@code WAWebLinkDeviceExperience},
     * {@code WAWebAppTracker}) have no Cobalt counterpart. Telemetry
     * emission is wrapped in a {@link RuntimeException} catch so a WAM
     * commit failure cannot disrupt the pairing flow.
     *
     * @param stage           the stage to record, or {@code null} for
     *                        a raw error-code commit
     * @param errorCode       the error code to attach, or {@code null}
     *                        for a normal stage transition
     * @param mdSessionId     the deterministic session id produced by
     *                        {@link #computeMdLinkSessionId(byte[], byte[])};
     *                        may be {@code null} when the session id
     *                        could not be derived
     * @param regStartSeconds the Unix-seconds timestamp captured at
     *                        pair-success entry, used as the baseline
     *                        for {@code mdDurationS} and
     *                        {@code mdTimestampS}
     */
    private void emitMdLinkDeviceCompanionStage(MdLinkDeviceCompanionStage stage, Integer errorCode, String mdSessionId, long regStartSeconds) {
        try {
            var nowSeconds = Instant.now().getEpochSecond();
            var builder = new MdLinkDeviceCompanionEventBuilder()
                    .mdTimestampS((int) regStartSeconds)
                    .mdDurationS((int) (nowSeconds - regStartSeconds))
                    .mdLinkDeviceCompanionErrorCode(errorCode != null ? errorCode : 0)
                    .mdLinkDeviceCompanionStage(stage);
            if (mdSessionId != null) {
                builder.mdSessionId(mdSessionId);
            }
            wamService.commit(builder.build());
        } catch (RuntimeException wamException) {
            LOGGER.log(System.Logger.Level.WARNING, "Cannot commit MdLinkDeviceCompanion event: {0}", wamException.getMessage());
        }
    }

    /**
     * Sends the {@code <iq><pair-device-sign><device-identity/></pair-device-sign></iq>}
     * response containing the device-signed identity, the
     * {@code keyIndex} attribute extracted from the inner
     * {@link ADVDeviceIdentitySpec}, and the
     * server-correlation {@code id}.
     *
     * @apiNote
     * The {@code accountSignatureKey} is intentionally cleared on the
     * outgoing identity ({@code $.accountSignatureKey = void 0} in WA
     * Web): the server only needs the device signature, not the
     * account signature key.
     *
     * @implNote
     * This implementation logs and returns rather than throwing when
     * the IQ is missing required attributes; defensive null checks are
     * unavoidable because the inbound stanza shape is server-driven.
     *
     * @param iqNode            the original {@code <pair-success/>}
     *                          {@code <iq>} stanza
     * @param validatedIdentity the validated {@link ADVSignedDeviceIdentity}
     */
    private void sendPairSuccessResponse(Node iqNode, ADVSignedDeviceIdentity validatedIdentity) {
        var id = iqNode.getAttributeAsString("id", null);
        if (id == null) {
            LOGGER.log(System.Logger.Level.DEBUG, "Cannot send pair-success response: missing id");
            return;
        }

        var details = validatedIdentity.details().orElse(null);
        if (details == null) {
            LOGGER.log(System.Logger.Level.WARNING, "Cannot send pair-success response: missing details in validated identity");
            return;
        }

        int keyIndex;
        try {
            var innerIdentity = ADVDeviceIdentitySpec.decode(details);
            keyIndex = innerIdentity.keyIndex().orElseThrow(() ->
                    new NullPointerException("keyIndex cannot be null"));
        } catch (Exception exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Cannot send pair-success response: failed to decode inner device identity: {0}", exception.getMessage());
            return;
        }

        var identityForResponse = new ADVSignedDeviceIdentityBuilder()
                .details(details)
                .accountSignature(validatedIdentity.accountSignature().orElse(null))
                .deviceSignature(validatedIdentity.deviceSignature().orElse(null))
                .build();

        var encodedIdentity = ADVSignedDeviceIdentitySpec.encode(identityForResponse);

        var deviceIdentityNode = new NodeBuilder()
                .description("device-identity")
                .attribute("key-index", keyIndex)
                .content(encodedIdentity)
                .build();

        var pairDeviceSignNode = new NodeBuilder()
                .description("pair-device-sign")
                .content(deviceIdentityNode)
                .build();

        var response = new NodeBuilder()
                .description("iq")
                .attribute("id", id)
                .attribute("to", Jid.userServer())
                .attribute("type", "result")
                .content(pairDeviceSignNode)
                .build();

        whatsapp.sendNodeWithNoResponse(response);
    }

    /**
     * Resolves the paired device JID or LID from a
     * {@code <pair-success/>} node by inspecting the
     * {@code <device/>} child's {@code jid} or {@code lid} attribute.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebJidToWid.deviceJidToDeviceWid(pairSuccessDeviceJid)}
     * / {@code (...DeviceLid)} extraction, which reads the same two
     * attributes off the parsed {@code device} child.
     *
     * @param node the {@code <pair-success/>} node
     * @param lid  {@code true} to resolve the LID, {@code false} for
     *             the device JID
     * @return the resolved {@link Jid}, or an empty
     *         {@link Optional} when the attribute is absent
     */
    private Optional<Jid> resolvePairedJid(Node node, boolean lid) {
        return node.getChild("device")
                .flatMap(device -> device.getAttributeAsJid(lid ? "lid" : "jid"));
    }

    /**
     * Extracts and decodes the {@link ClientPairingProps} protobuf
     * embedded in a {@code <pair-success/>} stanza.
     *
     * @apiNote
     * WA Web's SMAX parser exposes the protobuf bytes through the
     * {@code pairSuccessClientProps} field of the parsed request; the
     * Cobalt branch is defensive and inspects both the
     * {@code pair-success} content blob and every {@code <props/>}
     * child, returning the first decode that succeeds.
     *
     * @implNote
     * This implementation swallows individual decode failures so a
     * malformed {@code <props/>} child does not block the next
     * candidate from being tried.
     *
     * @param pairSuccess the {@code <pair-success/>} node
     * @return the decoded {@link ClientPairingProps}, or an empty
     *         {@link Optional} when no candidate decodes cleanly
     */
    private Optional<ClientPairingProps> extractPairingProps(Node pairSuccess) {
        var candidates = new ArrayList<Node>();
        candidates.add(pairSuccess);
        for (var child : pairSuccess.children()) {
            if ("props".equals(child.description())) {
                candidates.add(child);
            }
        }

        for (var candidate : candidates) {
            var bytes = candidate.toContentBytes().orElse(null);
            if (bytes == null || bytes.length == 0) {
                continue;
            }

            try {
                return Optional.of(ClientPairingPropsSpec.decode(bytes));
            } catch (Throwable _) {
            }
        }

        return Optional.empty();
    }

    /**
     * Cancels the currently scheduled rotation tick.
     *
     * @implSpec
     * Callers must hold {@link #rotationLock} for the duration of
     * this method so the cancel is atomic against a concurrent
     * {@link #runRotationTick(ArrayDeque)} reschedule.
     */
    private void cancelRotationLocked() {
        var task = rotationTask;
        if (task != null) {
            task.cancel(false);
            rotationTask = null;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Cancels any in-flight QR ref rotation so the next connection
     * starts from a clean slate. The
     * {@link CompanionPairingService}, {@link DeviceService} and other
     * dependencies are stateless against this handler.
     */
    @Override
    public void reset() {
        synchronized (rotationLock) {
            cancelRotationLocked();
        }
    }

    /**
     * Returns the first non-blank string attribute on the given node
     * matching one of the supplied attribute keys.
     *
     * @apiNote
     * Used by the defensive ref extraction in
     * {@link #extractPairRefs(Node)} to cover ref values placed on a
     * {@code ref}, {@code value} or {@code code} attribute rather than
     * a content blob.
     *
     * @param node the node to search
     * @param keys the attribute keys to try, in priority order
     * @return the first non-blank attribute value, or an empty
     *         {@link Optional} when none of the keys produce one
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
     * Returns the first {@link Jid}-typed attribute on the given node
     * matching one of the supplied attribute keys.
     *
     * @apiNote
     * Helper for stanza shapes that carry the same logical JID under
     * multiple attribute names.
     *
     * @param node the node to search
     * @param keys the attribute keys to try, in priority order
     * @return the first {@link Jid} attribute value, or an empty
     *         {@link Optional} when none of the keys produce one
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
     * Decodes the content of the given node as a UTF-8 string,
     * preferring the typed string accessor and falling back to a UTF-8
     * decode of the raw bytes.
     *
     * @apiNote
     * Used by {@link #extractPairRefs(Node)} so refs encoded as either
     * a {@code <ref>...</ref>} text node or a {@code <ref/>} binary
     * blob land in the same {@link LinkedHashSet}.
     *
     * @param node the node whose content to decode
     * @return the non-blank decoded string, or an empty
     *         {@link Optional} when the content is missing or blank
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

        var decoded = new String(bytes, StandardCharsets.UTF_8);
        return decoded.isBlank() ? Optional.empty() : Optional.of(decoded);
    }

    /**
     * Persists the {@link com.github.auties00.cobalt.store.WhatsAppStore}
     * to disk and logs any failure at {@code DEBUG}.
     *
     * @apiNote
     * Used at the end of the pair-success handler so a successful
     * pair survives a process restart. Failures are best-effort
     * because the bootstrap path that runs next will re-persist.
     *
     * @param context a human-readable context string used in the
     *                {@code DEBUG} log entry on failure
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
