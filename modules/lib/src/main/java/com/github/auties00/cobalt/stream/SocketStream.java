package com.github.auties00.cobalt.stream;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientVerificationHandler;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.pairing.CompanionPairingService;
import com.github.auties00.cobalt.message.MessageService;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.migration.InactiveGroupLidMigrationService;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.stream.call.CallStreamHandler;
import com.github.auties00.cobalt.stream.control.ErrorStreamHandler;
import com.github.auties00.cobalt.stream.control.FailureStreamHandler;
import com.github.auties00.cobalt.stream.control.InfoBulletinStreamHandler;
import com.github.auties00.cobalt.stream.control.OfflineNotificationsReporter;
import com.github.auties00.cobalt.stream.control.StreamErrorStreamHandler;
import com.github.auties00.cobalt.stream.control.SuccessStreamHandler;
import com.github.auties00.cobalt.stream.control.XmlStreamEndStreamHandler;
import com.github.auties00.cobalt.stream.iq.IqStreamHandler;
import com.github.auties00.cobalt.stream.message.MessageStreamHandler;
import com.github.auties00.cobalt.stream.newsletter.NewsletterStatusStreamHandler;
import com.github.auties00.cobalt.stream.notification.NotificationStreamHandler;
import com.github.auties00.cobalt.stream.presence.ChatStateStreamHandler;
import com.github.auties00.cobalt.stream.presence.PresenceStreamHandler;
import com.github.auties00.cobalt.stream.receipt.ReceiptStreamHandler;
import com.github.auties00.cobalt.sync.SnapshotRecoveryService;
import com.github.auties00.cobalt.sync.WebAppStateService;
import com.github.auties00.cobalt.wam.WamService;

import java.io.IOException;
import java.util.*;

/**
 * Top-level dispatcher for every inbound stanza received from the WhatsApp
 * server.
 *
 * <p>The WhatsApp protocol carries many logically distinct message types
 * ({@code <iq>}, {@code <message>}, {@code <receipt>}, {@code <presence>},
 * {@code <chatstate>}, {@code <call>}, {@code <notification>}, {@code <ib>},
 * {@code <success>}, {@code <failure>}, {@code <stream:error>},
 * {@code <error>}, {@code <status>}, {@code <xmlstreamend>}) on the same
 * noise-encrypted WebSocket connection. This class owns one dedicated
 * {@link Handler} per stanza tag, exposes a single entry point
 * {@link #handle(Node)}, and dispatches every incoming stanza on a fresh
 * virtual thread so that slow or blocking handlers cannot stall the
 * read loop of the underlying socket.
 *
 * <p>Each registered {@link Handler} also exposes {@link Handler#reset()}
 * which is called through {@link #reset()} whenever the socket is torn
 * down, allowing per-handler state (for example, single-run bootstrap
 * guards) to be cleared before the next connection starts.
 *
 * @implNote WA Web splits the top-level dispatch across three JS modules:
 * {@code WAWebCommsHandleStanza.u} (offline-attr tracking plus the
 * {@code iq} fast path), {@code WAWebCommsHandleWorkerCompatibleStanza.u}
 * (stanzas that may be handled inside a Web Worker: newsletter
 * {@code message}/{@code status}, group {@code notification w:gp2},
 * identity-change {@code notification encrypt/identity}, and
 * {@code receipt} call-receipts), and
 * {@code WAWebCommsHandleLoggedInStanza._} (every remaining tag). Cobalt
 * has no Web Worker, so the three-stage fall-through is flattened into a
 * single {@code Map<String, Handler>} keyed by stanza tag; the
 * sub-dispatch that WA Web performs inside each stage (for example the
 * newsletter branch of {@code message}, or the {@code type="retry"}
 * branch of {@code receipt}) is pushed down into the corresponding
 * per-tag handler.
 * @implNote WA Web's {@code WAWebCommsHandleLoggedInStanzaDeferred.handleLoggedInStanza}
 * is a lazy-loading wrapper around {@code WAWebCommsHandleLoggedInStanza}
 * that uses {@code requireDeferred} to defer loading the large
 * logged-in-stanza dispatcher (with all its ~45 notification/handler
 * dependencies) until the first post-login stanza actually arrives.
 * It memoises the resolved module in a module-scoped variable ({@code s})
 * and in-flight load promises ({@code u}) so concurrent callers share
 * the same {@code load()} call, then forwards
 * {@code handleLoggedInStanza(e, t)} to the loaded module. Cobalt has no
 * code-splitting or dynamic module loading: every handler is constructed
 * eagerly in {@link #SocketStream SocketStream's constructor} and
 * dispatched synchronously from {@link #handle(Node)}, so the deferred
 * wrapper has no Java counterpart to implement. The two arguments WA Web
 * passes through ({@code e} = stanza, {@code t} = offline attribute) map
 * to the single {@code Node} parameter of {@link #handle(Node)}; the
 * {@code offline} attribute is read from the node's attributes by the
 * per-tag handlers themselves.
 * @implNote WA Web registers per-stanza parsers via
 * {@code WADeprecatedWapParser} inside the {@code WAWebCommsRouter} /
 * {@code WAWebSocketModel} boot sequence. Cobalt collapses the registration
 * into this class's constructor and uses a plain {@code Map<String, Handler>}
 * instead of the WA Web parser registry.
 * @implNote WA Web's {@code WAWebCommsHandleStanza.u} also invokes
 * {@code OfflineMessageHandler.newOfflineStanza(t, n, offline)} for every
 * stanza that carries an {@code offline} attribute, driving the adaptive
 * offline-resume batch manager ({@code WASmaxOfflineBatchRPC.sendBatchRPC})
 * and the UI progress bar. Cobalt does not adapt this behaviour: there is
 * no offline-resume UI and no client-driven batch sizing; the server-side
 * offline backlog is drained as stanzas arrive. Completion state is still
 * tracked through {@link com.github.auties00.cobalt.client.WhatsAppClientOfflineResumeState}
 * and the {@code offline_preview}/{@code offline} info-bulletin handlers.
 * @implNote WA Web's outer dispatch ({@code WAWebCommsHandleStanza.d})
 * wraps each stanza in a try/catch that logs and returns {@code "NO_ACK"}
 * on failure. Cobalt mirrors this through {@link #runHandler(Handler, Node)}
 * which catches any {@link Throwable}, logs it, and prevents the failure
 * from propagating out of the dispatch virtual thread. The WA Web NACK
 * responses for parsing failures and unrecognised stanzas
 * ({@code createNackFromStanza}) are replaced by Cobalt's pluggable
 * {@code WhatsAppClientErrorHandler}; see the error-model note on
 * {@link #handle(Node)}.
 */
@WhatsAppWebModule(moduleName = "WAWebCommsRouter")
@WhatsAppWebModule(moduleName = "WAWebSocketModel")
@WhatsAppWebModule(moduleName = "WAWebCommsHandleStanza")
@WhatsAppWebModule(moduleName = "WAWebCommsHandleLoggedInStanza")
@WhatsAppWebModule(moduleName = "WAWebCommsHandleLoggedInStanzaDeferred")
@WhatsAppWebModule(moduleName = "WAWebCommsHandleWorkerCompatibleStanza")
public final class SocketStream {
    /**
     * Logger used to report handler failures that escape the per-handler
     * try/catch on a virtual thread.
     */
    private static final System.Logger LOGGER = System.getLogger(SocketStream.class.getName());

    /**
     * Immutable map from stanza tag (for example {@code "message"}) to the
     * handler responsible for that tag. Populated once in the constructor
     * and never mutated afterwards.
     */
    private final Map<String, Handler> handlers;

    /**
     * Constructs a new dispatcher and wires one handler per supported
     * stanza tag, injecting the shared services that each handler depends
     * on.
     *
     * @param whatsapp                         the WhatsApp client used by
     *                                         nearly every handler
     * @param webVerificationHandler           verification handler used
     *                                         during companion pairing
     *                                         prompts
     * @param lidMigrationService              service managing LID-based
     *                                         one-on-one chat migration
     * @param inactiveGroupLidMigrationService service migrating inactive
     *                                         groups to LID addressing
     * @param messageService                   service for decrypting and
     *                                         storing inbound messages
     * @param abPropsService                   service exposing A/B feature
     *                                         flags synced from the server
     * @param deviceService                    service for companion/linked
     *                                         device management
     * @param wamService                       service collecting WhatsApp
     *                                         analytics events
     * @param snapshotRecoveryService          service handling app-state
     *                                         snapshot recovery
     * @param webAppStateService               service managing web app-state
     *                                         sync patches
     * @param companionPairingService          companion pairing service
     */
    public SocketStream(WhatsAppClient whatsapp, WhatsAppClientVerificationHandler.Web webVerificationHandler, LidMigrationService lidMigrationService, InactiveGroupLidMigrationService inactiveGroupLidMigrationService, MessageService messageService, ABPropsService abPropsService, DeviceService deviceService, WamService wamService, SnapshotRecoveryService snapshotRecoveryService, WebAppStateService webAppStateService, CompanionPairingService companionPairingService) {
        // WAWebHandleReportServerSyncNotification: shared between the server_sync notification
        // handler (producer) and the info-bulletin offline handler (consumer/flush) to mirror
        // WA Web's module-scoped offlineNotificationsCount map.
        var offlineNotificationsReporter = new OfflineNotificationsReporter(whatsapp);
        var result = new HashMap<String, Handler>();
        addHandler(result, "iq", new IqStreamHandler(whatsapp, webVerificationHandler, deviceService, snapshotRecoveryService, companionPairingService));
        addHandler(result, "message", new MessageStreamHandler(
                whatsapp,
                messageService,
                snapshotRecoveryService,
                webAppStateService,
                lidMigrationService
        ));
        addHandler(result, "receipt", new ReceiptStreamHandler(whatsapp, messageService));
        addHandler(result, "presence", new PresenceStreamHandler(whatsapp));
        addHandler(result, "chatstate", new ChatStateStreamHandler(whatsapp));
        addHandler(result, "call", new CallStreamHandler(whatsapp));
        addHandler(result, "notification", new NotificationStreamHandler(
                whatsapp,
                companionPairingService,
                lidMigrationService,
                abPropsService,
                deviceService,
                offlineNotificationsReporter
        ));
        addHandler(result, "ib", new InfoBulletinStreamHandler(whatsapp, webAppStateService, offlineNotificationsReporter));
        addHandler(result, "success", new SuccessStreamHandler(
                whatsapp,
                abPropsService,
                deviceService,
                lidMigrationService,
                inactiveGroupLidMigrationService,
                wamService,
                webAppStateService
        ));
        addHandler(result, "failure", new FailureStreamHandler(whatsapp));
        addHandler(result, "stream:error", new StreamErrorStreamHandler(whatsapp));
        addHandler(result, "error", new ErrorStreamHandler());
        addHandler(result, "status", new NewsletterStatusStreamHandler(whatsapp));
        addHandler(result, "xmlstreamend", new XmlStreamEndStreamHandler());
        this.handlers = Collections.unmodifiableMap(result);
    }

    /**
     * Registers a handler under the given stanza tag, failing fast if a
     * handler was already registered for the same tag.
     *
     * @param result      the map being built inside the constructor
     * @param description the stanza tag (for example {@code "message"})
     * @param handler     the handler to register for that tag
     * @throws IllegalStateException if the same tag is already registered
     */
    private void addHandler(Map<String, Handler> result, String description, Handler handler) {
        var previousHandler = result.putIfAbsent(description, handler);
        if (previousHandler != null) {
            throw new IllegalStateException("Duplicate handler for stanza description " + description
                    + ": " + previousHandler.getClass().getSimpleName()
                    + " and " + handler.getClass().getSimpleName());
        }
    }

    /**
     * Dispatches the given stanza to the handler registered for its tag.
     *
     * <p>The handler is invoked on a freshly started virtual thread so that
     * a blocking or slow handler cannot back up the read loop of the
     * underlying socket. Stanzas whose tag is not registered are silently
     * dropped.
     *
     * @param node the stanza to dispatch
     * @implNote WA Web's {@code WAWebCommsHandleLoggedInStanza._} falls
     * through to {@code createNackFromStanza(e, NackReason.UnrecognizedStanza)}
     * for every stanza whose tag is not in its dispatch switch, sending a
     * {@code NACK} response back to the server with a dedicated
     * {@code DEV_XMPP} log line. Cobalt intentionally diverges: the ack
     * decision is made by each per-tag handler (see for example
     * {@code IqStreamHandler} which acknowledges IQs that reach the fallback
     * arm of its switch), and stanzas whose top-level tag is not registered
     * at all are treated as a protocol-level surprise that the server is not
     * allowed to send on a logged-in session — the pluggable
     * {@code WhatsAppClientErrorHandler} owns the recovery policy instead of
     * an inline NACK. See the class-level error-model note.
     * @implNote WA Web dispatches in the caller's own microtask
     * ({@code WAWebCommsHandleStanza.d} returns a {@code Promise} that the
     * noise layer awaits); Cobalt starts a fresh virtual thread per stanza
     * so that a blocking handler (for example one waiting on a synchronous
     * IQ round-trip or an SQL store write) cannot stall the socket read
     * loop. Handler ordering across stanzas is therefore not guaranteed by
     * this dispatcher — handlers that require ordering apply their own
     * synchronisation, matching the WA Web model where Promises may
     * interleave.
     */
    @WhatsAppWebExport(moduleName = "WAWebCommsHandleLoggedInStanza",
            exports = "handleLoggedInStanza",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebCommsHandleLoggedInStanzaDeferred",
            exports = "handleLoggedInStanza",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebCommsHandleWorkerCompatibleStanza",
            exports = "handleWorkerCompatibleStanza",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void handle(Node node) {
        var handler = this.handlers.get(node.description());
        if (handler != null) {
            Thread.startVirtualThread(() -> runHandler(handler, node));
        }
    }

    /**
     * Resets every registered handler. Called by the socket layer when the
     * underlying connection is torn down, ensuring that per-connection
     * state (for example, single-run bootstrap guards on
     * {@code <success>}) is cleared before a potential reconnection.
     */
    public void reset() {
        for (var handler : new LinkedHashSet<>(handlers.values())) {
            handler.reset();
        }
    }

    /**
     * Invokes the given handler on the given stanza and catches any
     * exception so that handler failures do not bubble up to the calling
     * virtual thread's uncaught exception path.
     *
     * @param handler the handler to invoke
     * @param node    the stanza to pass to the handler
     */
    static void runHandler(Handler handler, Node node) {
        try {
            handler.handle(node);
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Handler {0} failed for stanza {1}: {2}",
                    handler.getClass().getSimpleName(),
                    node.description(),
                    throwable.getMessage());
        }
    }

    /**
     * Contract implemented by every per-tag stanza handler.
     *
     * <p>Handlers receive one inbound stanza at a time and may throw
     * {@link IOException} when the underlying store or socket operation
     * fails; any other exception is caught and logged by
     * {@link SocketStream#runHandler(Handler, Node)}.
     */
    public interface Handler {
        /**
         * Handles the given stanza.
         *
         * @param node the stanza to handle
         * @throws IOException if a blocking I/O operation fails
         */
        void handle(Node node) throws IOException;

        /**
         * Clears any per-connection state so that the handler is safe to
         * reuse after a reconnection. The default implementation does
         * nothing.
         */
        default void reset() {

        }
    }
}
