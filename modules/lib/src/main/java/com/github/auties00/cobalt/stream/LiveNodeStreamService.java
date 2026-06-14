package com.github.auties00.cobalt.stream;

import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.call.CallService;
import com.github.auties00.cobalt.call.signaling.CallTerminateReceiver;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientVerificationHandler;
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

import java.util.*;

/**
 * Production {@link NodeStreamService} that routes every inbound WhatsApp stanza to the
 * {@link SocketStreamHandler} registered for its tag.
 *
 * <p>The WhatsApp wire protocol multiplexes many logically distinct
 * stanza types on the same noise-encrypted WebSocket: {@code <iq>},
 * {@code <message>}, {@code <receipt>}, {@code <presence>},
 * {@code <chatstate>}, {@code <call>}, {@code <terminate>},
 * {@code <notification>}, {@code <ib>}, {@code <success>},
 * {@code <failure>}, {@code <stream:error>}, {@code <error>},
 * {@code <status>}, {@code <xmlstreamend>}. This class owns one
 * dedicated {@link SocketStreamHandler} per stanza tag, exposes a single
 * {@link #handle(Node)} entry point fed by the noise transport layer,
 * and fans each stanza out onto a fresh virtual thread so that a slow
 * or blocking handler cannot back up the socket reader, with the single
 * exception of {@code <message>} stanzas, which are serialised per chat
 * (in arrival order) so a chat's messages never decrypt concurrently.
 *
 * <p>The lifecycle client wires one instance per logical session and
 * threads it into the noise transport layer; embedders never construct
 * or invoke it directly. Each registered {@link SocketStreamHandler} also exposes
 * {@link SocketStreamHandler#reset()}, which {@link #reset()} invokes on socket
 * teardown so that per-connection state (for example, the one-shot
 * bootstrap guard inside {@link SuccessStreamHandler}) is cleared before
 * the next connection starts.
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
 * the noise handshake completes synchronously on a virtual thread before
 * this dispatcher is even reachable. Unrecognised stanza tags are
 * silently dropped rather than nacked with the {@code UnrecognizedStanza}
 * reason because Cobalt does not implement the
 * {@code WAWebCreateNackFromStanza} ack/nack protocol.
 */
@WhatsAppWebModule(moduleName = "WAWebCommsRouter")
@WhatsAppWebModule(moduleName = "WAWebSocketModel")
@WhatsAppWebModule(moduleName = "WAWebCommsHandleStanza")
@WhatsAppWebModule(moduleName = "WAWebCommsHandleLoggedInStanza")
@WhatsAppWebModule(moduleName = "WAWebCommsHandleLoggedInStanzaDeferred")
@WhatsAppWebModule(moduleName = "WAWebCommsHandleWorkerCompatibleStanza")
public final class LiveNodeStreamService implements NodeStreamService {
    /**
     * Holds the map from stanza tag (for example {@code "message"}) to
     * the {@link SocketStreamHandler} registered for that tag.
     *
     * <p>The map is populated once in the constructor and never mutated
     * afterwards; it is wrapped in
     * {@link Collections#unmodifiableMap(Map)} so that any accidental
     * mutation is a runtime error.
     */
    private final Map<String, SocketStreamHandler> handlers;

    /**
     * Constructs a dispatcher and wires one {@link SocketStreamHandler} per supported
     * stanza tag, injecting the shared services each handler depends on.
     *
     * <p>A single {@link OfflineNotificationsReporter} is created locally
     * and shared between the {@code server_sync} producer
     * ({@link NotificationStreamHandler}) and the offline-bulletin
     * consumer ({@link InfoBulletinStreamHandler}) to mirror WA Web's
     * module-scoped {@code offlineNotificationsCount} map.
     *
     * @param whatsapp                         the {@link LinkedWhatsAppClient}
     *                                         used by nearly every
     *                                         handler for store access,
     *                                         outbound stanza dispatch
     *                                         and listener fan-out
     * @param callService                      the {@link CallService}
     *                                         consumed by the
     *                                         {@code <call>} and
     *                                         {@code <terminate>} call
     *                                         signalling handlers
     * @param webVerificationHandler           the
     *                                         {@link LinkedWhatsAppClientVerificationHandler.Web}
     *                                         used during companion
     *                                         pairing prompts
     * @param lidMigrationService              the {@link LidMigrationService}
     *                                         managing LID-based
     *                                         one-on-one chat migration
     * @param inactiveGroupLidMigrationService the
     *                                         {@link InactiveGroupLidMigrationService}
     *                                         migrating inactive groups to
     *                                         LID addressing
     * @param messageService                   the {@link MessageService}
     *                                         decrypting and storing
     *                                         inbound messages
     * @param abPropsService                   the {@link ABPropsService}
     *                                         exposing A/B feature flags
     *                                         synced from the server
     * @param deviceService                    the {@link DeviceService}
     *                                         performing companion/linked
     *                                         device management
     * @param wamService                       the {@link WamService}
     *                                         collecting WhatsApp
     *                                         analytics events
     * @param snapshotRecoveryService          the {@link SnapshotRecoveryService}
     *                                         handling app-state snapshot
     *                                         recovery
     * @param webAppStateService               the {@link WebAppStateService}
     *                                         managing web app-state sync
     *                                         patches
     * @param companionPairingService          the {@link CompanionPairingService}
     *                                         consulted by the IQ
     *                                         pair-device flow
     * @param ackSender                        the {@link AckSender} that
     *                                         ships every outbound
     *                                         {@code <ack>} stanza emitted
     *                                         by the message, receipt,
     *                                         call and notification
     *                                         handlers
     * @param mediaConnectionService           the {@link MediaConnectionService}
     *                                         supplying the media-endpoint
     *                                         connection consumed by the
     *                                         message and success handlers
     */
    public LiveNodeStreamService(LinkedWhatsAppClient whatsapp, CallService callService, LinkedWhatsAppClientVerificationHandler.Web webVerificationHandler, LidMigrationService lidMigrationService, InactiveGroupLidMigrationService inactiveGroupLidMigrationService, MessageService messageService, ABPropsService abPropsService, DeviceService deviceService, WamService wamService, SnapshotRecoveryService snapshotRecoveryService, WebAppStateService webAppStateService, CompanionPairingService companionPairingService, AckSender ackSender, MediaConnectionService mediaConnectionService) {
        var offlineNotificationsReporter = new OfflineNotificationsReporter(whatsapp, wamService);
        var result = new HashMap<String, SocketStreamHandler>();
        addHandler(result, "iq", new IqStreamHandler(whatsapp, webVerificationHandler, deviceService, snapshotRecoveryService, lidMigrationService, companionPairingService, wamService));
        addHandler(result, "message", new MessageStreamHandler(
                whatsapp,
                messageService,
                snapshotRecoveryService,
                webAppStateService,
                lidMigrationService,
                wamService,
                ackSender,
                mediaConnectionService
        ));
        addHandler(result, "receipt", new ReceiptStreamHandler(whatsapp, messageService, wamService, ackSender));
        addHandler(result, "presence", new PresenceStreamHandler(whatsapp));
        addHandler(result, "chatstate", new ChatStateStreamHandler(whatsapp));
        addHandler(result, "call", new CallReceiver(whatsapp, callService, messageService, ackSender));
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
     * Registers the given {@link SocketStreamHandler} under the given stanza tag,
     * failing fast if another handler is already registered for that tag.
     *
     * <p>This method is called only from the constructor while
     * {@code result} is still a private working map. It catches accidental
     * double registration at construction time rather than letting one of
     * two handlers silently win.
     *
     * @param result      the working map being populated inside the
     *                    constructor
     * @param description the stanza tag (for example {@code "message"})
     * @param handler     the {@link SocketStreamHandler} to register for {@code description}
     * @throws IllegalStateException if a handler is already registered for
     *                               {@code description}
     */
    private void addHandler(Map<String, SocketStreamHandler> result, String description, SocketStreamHandler handler) {
        var previousHandler = result.putIfAbsent(description, handler);
        if (previousHandler != null) {
            throw new IllegalStateException("Duplicate handler for stanza description " + description
                    + ": " + previousHandler.getClass().getSimpleName()
                    + " and " + handler.getClass().getSimpleName());
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation looks up the handler for the stanza tag and delegates scheduling to
     * {@link SocketStreamHandler#handleAsync(Node)}: a {@link SocketStreamHandler.Concurrent} runs the
     * stanza on a fresh virtual thread so a slow handler cannot stall the socket reader, while a
     * {@link SocketStreamHandler.Ordered} chains the stanza onto its ordering key so stanzas sharing a
     * key are processed in arrival order. The WA Web counterpart runs handlers on the JS event loop and
     * relies on each handler awaiting its own promises; on virtual threads the equivalent is a
     * plain blocking call wrapped in a started thread. Unrecognised tags are dropped rather than
     * nacked because Cobalt does not implement the
     * {@code WAWebCreateNackFromStanza.NackReason.UnrecognizedStanza} path.
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
    @Override
    public void handle(Node node) {
        var handler = this.handlers.get(node.description());
        if (handler != null) {
            handler.handleAsync(node);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation iterates a {@link LinkedHashSet} view of the registered handlers so a handler
     * registered under more than one tag (none today, but the design supports it) is reset exactly once.
     */
    @Override
    public void reset() {
        for (var handler : new LinkedHashSet<>(handlers.values())) {
            handler.reset();
        }
    }
}
