package com.github.auties00.cobalt.stream;

import com.github.auties00.cobalt.call.CallService;
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
import com.github.auties00.cobalt.call.signaling.CallReceiver;
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
    public SocketStream(WhatsAppClient whatsapp, CallService callService, WhatsAppClientVerificationHandler.Web webVerificationHandler, LidMigrationService lidMigrationService, InactiveGroupLidMigrationService inactiveGroupLidMigrationService, MessageService messageService, ABPropsService abPropsService, DeviceService deviceService, WamService wamService, SnapshotRecoveryService snapshotRecoveryService, WebAppStateService webAppStateService, CompanionPairingService companionPairingService) {
        // WAWebHandleReportServerSyncNotification: shared between the server_sync notification
        // handler (producer) and the info-bulletin offline handler (consumer/flush) to mirror
        // WA Web's module-scoped offlineNotificationsCount map.
        var offlineNotificationsReporter = new OfflineNotificationsReporter(whatsapp, wamService);
        var result = new HashMap<String, Handler>();
        addHandler(result, "iq", new IqStreamHandler(whatsapp, webVerificationHandler, deviceService, snapshotRecoveryService, lidMigrationService, companionPairingService, wamService));
        addHandler(result, "message", new MessageStreamHandler(
                whatsapp,
                messageService,
                snapshotRecoveryService,
                webAppStateService,
                lidMigrationService,
                abPropsService,
                wamService
        ));
        addHandler(result, "receipt", new ReceiptStreamHandler(whatsapp, messageService, wamService));
        addHandler(result, "presence", new PresenceStreamHandler(whatsapp));
        addHandler(result, "chatstate", new ChatStateStreamHandler(whatsapp));
        addHandler(result, "call", new CallReceiver(whatsapp, callService));
        addHandler(result, "notification", new NotificationStreamHandler(
                whatsapp,
                companionPairingService,
                lidMigrationService,
                abPropsService,
                deviceService,
                offlineNotificationsReporter,
                wamService
        ));
        addHandler(result, "ib", new InfoBulletinStreamHandler(whatsapp, webAppStateService, offlineNotificationsReporter, wamService, deviceService));
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
