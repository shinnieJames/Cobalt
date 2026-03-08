package com.github.auties00.cobalt.stream;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientVerificationHandler;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.message.MessageService;
import com.github.auties00.cobalt.migration.InactiveGroupLidMigrationService;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.stream.call.CallStreamHandler;
import com.github.auties00.cobalt.stream.control.ErrorStreamHandler;
import com.github.auties00.cobalt.stream.control.FailureStreamHandler;
import com.github.auties00.cobalt.stream.control.InfoBulletinStreamHandler;
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

public final class SocketStream {
    private static final System.Logger LOGGER = System.getLogger(SocketStream.class.getName());

    private final Map<String, Handler> handlers;

    public SocketStream(WhatsAppClient whatsapp, WhatsAppClientVerificationHandler.Web webVerificationHandler, LidMigrationService lidMigrationService, InactiveGroupLidMigrationService inactiveGroupLidMigrationService, MessageService messageService, ABPropsService abPropsService, DeviceService deviceService, WamService wamService, SnapshotRecoveryService snapshotRecoveryService, WebAppStateService webAppStateService) {
        var result = new HashMap<String, Handler>();
        addHandler(result, "iq", new IqStreamHandler(whatsapp, webVerificationHandler, deviceService));
        addHandler(result, "message", new MessageStreamHandler(
                whatsapp,
                messageService,
                snapshotRecoveryService,
                lidMigrationService
        ));
        addHandler(result, "receipt", new ReceiptStreamHandler(whatsapp, messageService));
        addHandler(result, "presence", new PresenceStreamHandler(whatsapp));
        addHandler(result, "chatstate", new ChatStateStreamHandler(whatsapp));
        addHandler(result, "call", new CallStreamHandler(whatsapp));
        addHandler(result, "notification", new NotificationStreamHandler(
                whatsapp,
                webVerificationHandler,
                lidMigrationService,
                abPropsService,
                deviceService
        ));
        addHandler(result, "ib", new InfoBulletinStreamHandler(whatsapp));
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

    private void addHandler(Map<String, Handler> result, String description, Handler handler) {
        var previousHandler = result.putIfAbsent(description, handler);
        if (previousHandler != null) {
            throw new IllegalStateException("Duplicate handler for stanza description " + description
                    + ": " + previousHandler.getClass().getSimpleName()
                    + " and " + handler.getClass().getSimpleName());
        }
    }
    
    public void handle(Node node) {
        var handler = this.handlers.get(node.description());
        if (handler != null) {
            Thread.startVirtualThread(() -> runHandler(handler, node));
        }
    }

    public void reset() {
        for (var handler : new LinkedHashSet<>(handlers.values())) {
            handler.reset();
        }
    }

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

    public interface Handler {
        void handle(Node node) throws IOException;

        default void reset() {

        }
    }
}
