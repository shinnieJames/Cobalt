package com.github.auties00.cobalt.stream;

import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.call.internal.CallService;
import com.github.auties00.cobalt.call.internal.signaling.CallTerminateReceiver;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientVerificationHandler;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.media.MediaConnectionService;
import com.github.auties00.cobalt.pairing.CompanionPairingService;
import com.github.auties00.cobalt.message.MessageService;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.migration.InactiveGroupLidMigrationService;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.call.internal.signaling.CallReceiver;
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
 * Top-level dispatcher that routes every inbound WhatsApp stanza to the
 * {@link Handler} registered for its tag.
 *
 * <p>The WhatsApp wire protocol multiplexes many logically distinct
 * stanza types on the same noise-encrypted WebSocket: {@code <iq>},
 * {@code <message>}, {@code <receipt>}, {@code <presence>},
 * {@code <chatstate>}, {@code <call>}, {@code <terminate>},
 * {@code <notification>}, {@code <ib>}, {@code <success>},
 * {@code <failure>}, {@code <stream:error>}, {@code <error>},
 * {@code <status>}, {@code <xmlstreamend>}. This class owns one
 * dedicated {@link Handler} per stanza tag, exposes a single
 * {@link #handle(Node)} entry point used by the noise transport layer,
 * and fans every stanza out onto a fresh virtual thread so that a slow
 * or blocking handler cannot back up the socket reader.
 *
 * <p>Each registered {@link Handler} also exposes {@link Handler#reset()}
 * which is invoked through {@link #reset()} on socket teardown so that
 * per-connection state (for example, the one-shot bootstrap guard inside
 * {@link SuccessStreamHandler}) is cleared before the next connection
 * starts.
 *
 * @apiNote
 * Cobalt embedders never construct this class directly; the lifecycle
 * client wires one instance per logical session and threads it into the
 * noise transport layer. The dispatcher is the Java counterpart of WA
 * Web's {@code WAWebCommsHandleLoggedInStanza.handleLoggedInStanza}
 * switch statement.
 *
 * @implNote
 * This implementation flattens WA Web's three-layer dispatcher
 * ({@code WAWebCommsRouter} routing into
 * {@code WAWebCommsHandleStanza} routing into
 * {@code WAWebCommsHandleLoggedInStanza}, with the optional
 * {@code WAWebCommsHandleLoggedInStanzaDeferred} and
 * {@code WAWebCommsHandleWorkerCompatibleStanza} indirections) into a
 * single tag-keyed map. WA Web's pre-handshake stanza routing (the
 * stanza queue before login completes) has no Cobalt analogue because
 * the noise handshake completes synchronously on a virtual thread
 * before this dispatcher is even reachable. Unrecognised stanza tags
 * are silently dropped rather than nacked with the
 * {@code UnrecognizedStanza} reason because Cobalt does not implement
 * the {@code WAWebCreateNackFromStanza} ack/nack protocol.
 */
@WhatsAppWebModule(moduleName = "WAWebCommsRouter")
@WhatsAppWebModule(moduleName = "WAWebSocketModel")
@WhatsAppWebModule(moduleName = "WAWebCommsHandleStanza")
@WhatsAppWebModule(moduleName = "WAWebCommsHandleLoggedInStanza")
@WhatsAppWebModule(moduleName = "WAWebCommsHandleLoggedInStanzaDeferred")
@WhatsAppWebModule(moduleName = "WAWebCommsHandleWorkerCompatibleStanza")
public final class SocketStream {
    /**
     * The system logger used to report handler failures that escape the
     * per-handler try/catch on the fan-out virtual thread.
     */
    private static final System.Logger LOGGER = System.getLogger(SocketStream.class.getName());

    /**
     * The immutable map from stanza tag (for example {@code "message"})
     * to the {@link Handler} registered for that tag.
     *
     * @apiNote
     * Populated once in the constructor and never mutated afterwards;
     * the map is wrapped in {@link Collections#unmodifiableMap(Map)} to
     * make accidental mutation a runtime error.
     */
    private final Map<String, Handler> handlers;

    /**
     * Constructs a new dispatcher and wires one {@link Handler} per
     * supported stanza tag, injecting the shared services each handler
     * depends on.
     *
     * @apiNote
     * Cobalt embedders never call this constructor directly; the
     * lifecycle client instantiates one dispatcher per session. The
     * {@link OfflineNotificationsReporter} is created locally and shared
     * between the {@code server_sync} producer
     * ({@link NotificationStreamHandler}) and the offline-bulletin
     * consumer ({@link InfoBulletinStreamHandler}) to mirror WA Web's
     * module-scoped {@code offlineNotificationsCount} map.
     *
     * @param whatsapp                         the {@link WhatsAppClient}
     *                                         used by nearly every
     *                                         handler for store access,
     *                                         outbound stanza dispatch
     *                                         and listener fan-out
     * @param callService                      the {@link CallService}
     *                                         consumed by the
     *                                         {@code <call>} and
     *                                         {@code <terminate>} call
     *                                         signalling handlers
     * @param webVerificationHandler           the verification handler
     *                                         used during companion
     *                                         pairing prompts
     * @param lidMigrationService              the service managing
     *                                         LID-based one-on-one chat
     *                                         migration
     * @param inactiveGroupLidMigrationService the service migrating
     *                                         inactive groups to LID
     *                                         addressing
     * @param messageService                   the service decrypting and
     *                                         storing inbound messages
     * @param abPropsService                   the service exposing A/B
     *                                         feature flags synced from
     *                                         the server
     * @param deviceService                    the service performing
     *                                         companion/linked device
     *                                         management
     * @param wamService                       the service collecting
     *                                         WhatsApp analytics events
     * @param snapshotRecoveryService          the service handling
     *                                         app-state snapshot
     *                                         recovery
     * @param webAppStateService               the service managing web
     *                                         app-state sync patches
     * @param companionPairingService          the companion-pairing
     *                                         service consulted by the
     *                                         IQ pair-device flow
     * @param ackSender                        the {@link AckSender}
     *                                         service that ships every
     *                                         outbound {@code <ack>}
     *                                         stanza emitted by the
     *                                         message, receipt, call
     *                                         and notification
     *                                         handlers
     */
    public SocketStream(WhatsAppClient whatsapp, CallService callService, WhatsAppClientVerificationHandler.Web webVerificationHandler, LidMigrationService lidMigrationService, InactiveGroupLidMigrationService inactiveGroupLidMigrationService, MessageService messageService, ABPropsService abPropsService, DeviceService deviceService, WamService wamService, SnapshotRecoveryService snapshotRecoveryService, WebAppStateService webAppStateService, CompanionPairingService companionPairingService, AckSender ackSender, MediaConnectionService mediaConnectionService) {
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
                wamService,
                ackSender,
                mediaConnectionService
        ));
        addHandler(result, "receipt", new ReceiptStreamHandler(whatsapp, messageService, wamService, ackSender));
        addHandler(result, "presence", new PresenceStreamHandler(whatsapp));
        addHandler(result, "chatstate", new ChatStateStreamHandler(whatsapp));
        addHandler(result, "call", new CallReceiver(whatsapp, callService, ackSender));
        addHandler(result, "terminate", new CallTerminateReceiver(whatsapp, callService));
        addHandler(result, "notification", new NotificationStreamHandler(
                whatsapp,
                companionPairingService,
                lidMigrationService,
                abPropsService,
                deviceService,
                offlineNotificationsReporter,
                wamService,
                ackSender
        ));
        addHandler(result, "ib", new InfoBulletinStreamHandler(whatsapp, webAppStateService, offlineNotificationsReporter, wamService, deviceService));
        addHandler(result, "success", new SuccessStreamHandler(
                whatsapp,
                abPropsService,
                deviceService,
                lidMigrationService,
                inactiveGroupLidMigrationService,
                wamService,
                webAppStateService,
                mediaConnectionService
        ));
        addHandler(result, "failure", new FailureStreamHandler(whatsapp));
        addHandler(result, "stream:error", new StreamErrorStreamHandler(whatsapp));
        addHandler(result, "error", new ErrorStreamHandler());
        addHandler(result, "status", new NewsletterStatusStreamHandler(whatsapp));
        addHandler(result, "xmlstreamend", new XmlStreamEndStreamHandler());
        this.handlers = Collections.unmodifiableMap(result);
    }

    /**
     * Registers the given {@link Handler} under the given stanza tag,
     * failing fast if another handler is already registered for that
     * tag.
     *
     * @apiNote
     * Only called from the constructor while {@code result} is still a
     * private working map. Catches accidental double registration at
     * construction time rather than letting one of two handlers
     * silently win.
     *
     * @param result      the working map being populated inside the
     *                    constructor
     * @param description the stanza tag (for example {@code "message"})
     * @param handler     the {@link Handler} to register for {@code description}
     * @throws IllegalStateException if a handler is already registered
     *                               for {@code description}
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
     * Dispatches the given stanza to the {@link Handler} registered for
     * its tag on a fresh virtual thread.
     *
     * @apiNote
     * Cobalt embedders never invoke this method directly; the noise
     * transport layer feeds every decrypted stanza here. Stanzas whose
     * tag has no registered handler are silently dropped rather than
     * nacked, departing from WA Web's
     * {@code WAWebCreateNackFromStanza.NackReason.UnrecognizedStanza}
     * which Cobalt does not implement.
     *
     * @implNote
     * This implementation starts a fresh virtual thread for every
     * stanza so a slow handler cannot stall the socket reader. The WA
     * Web counterpart runs handlers on the JS event loop and relies on
     * each handler awaiting its own promises; on virtual threads the
     * equivalent is a plain blocking call wrapped in a started thread.
     *
     * @param node the inbound stanza
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
     * Invokes {@link Handler#reset()} on every registered handler so
     * per-connection state is cleared before the next connection
     * starts.
     *
     * @apiNote
     * Called by the socket layer immediately after the underlying
     * connection is torn down. The reset is required so that one-shot
     * bootstrap guards (notably the {@link SuccessStreamHandler} latch)
     * fire again on the next {@code <success>} stanza of a
     * reconnecting session.
     *
     * @implNote
     * This implementation iterates a {@link LinkedHashSet} view of the
     * registered handlers so a handler registered under more than one
     * tag (none today, but the design supports it) is reset exactly
     * once.
     */
    public void reset() {
        for (var handler : new LinkedHashSet<>(handlers.values())) {
            handler.reset();
        }
    }

    /**
     * Invokes the given {@link Handler} on the given stanza, catching
     * any {@link Throwable} so handler failures do not bubble up to the
     * fan-out virtual thread's uncaught exception path.
     *
     * @apiNote
     * Invoked from {@link #handle(Node)} as the body of every per-stanza
     * virtual thread. Failures are logged at {@code WARNING} with the
     * handler simple name and the stanza tag for diagnostics, mirroring
     * WA Web's per-arm {@code try/catch} blocks that log the failure but
     * keep the socket up.
     *
     * @param handler the {@link Handler} to invoke
     * @param node    the stanza to pass to {@code handler}
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
     * Contract implemented by every per-tag stanza handler registered on
     * a {@link SocketStream}.
     *
     * @apiNote
     * Handlers are dispatched one stanza at a time from
     * {@link SocketStream#handle(Node)} on a fresh virtual thread per
     * stanza, mirroring the per-arm dispatch in WA Web's
     * {@code WAWebCommsHandleLoggedInStanza.handleLoggedInStanza} switch.
     * Any {@link IOException} thrown out of {@link #handle(Node)} or any
     * other {@link Throwable} is caught and logged by
     * {@link SocketStream#runHandler(Handler, Node)}.
     */
    public interface Handler {
        /**
         * Handles the given inbound stanza.
         *
         * @implSpec
         * Implementations must complete on the calling virtual thread;
         * any required asynchrony is performed with plain blocking calls
         * because the dispatcher already runs each invocation on its own
         * virtual thread. Implementations may throw {@link IOException}
         * to signal a blocking I/O failure during a downstream store or
         * socket operation; any other {@link Throwable} is allowed and
         * is caught and logged by {@link SocketStream#runHandler(Handler, Node)}.
         *
         * @param node the inbound stanza
         * @throws IOException if a blocking I/O operation fails
         */
        void handle(Node node) throws IOException;

        /**
         * Clears any per-connection state so the handler is safe to
         * reuse after a reconnection.
         *
         * @implSpec
         * The default implementation does nothing. Subclasses that hold
         * one-shot bootstrap guards, rotation timers or accumulated
         * counters override this method to clear them.
         *
         * @implNote
         * This implementation is a no-op; the dispatcher iterates every
         * registered handler unconditionally so stateless handlers do
         * not need to override.
         */
        default void reset() {

        }
    }
}
