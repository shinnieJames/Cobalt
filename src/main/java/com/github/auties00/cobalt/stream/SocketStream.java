package com.github.auties00.cobalt.stream;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientVerificationHandler;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.message.MessageService;
import com.github.auties00.cobalt.migration.InactiveGroupLidMigrationService;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.sync.SnapshotRecoveryService;
import com.github.auties00.cobalt.wam.WamService;

import java.util.*;

public final class SocketStream {
    private final Map<String, SequencedCollection<Handler>> handlers;

    public SocketStream(WhatsAppClient whatsapp, WhatsAppClientVerificationHandler.Web webVerificationHandler, LidMigrationService lidMigrationService, InactiveGroupLidMigrationService inactiveGroupLidMigrationService, MessageService messageService, ABPropsService abPropsService, DeviceService deviceService, WamService wamService, SnapshotRecoveryService snapshotRecoveryService) {
        var result = new HashMap<String, SequencedCollection<Handler>>();
        this.handlers = Collections.unmodifiableMap(result);
    }

    private void addHandler(Map<String, SequencedCollection<Handler>> result, Handler handler) {
        for (var description : handler.descriptions()) {
            result.computeIfAbsent(description, _ -> new ArrayList<>()).add(handler);
        }
    }
    
    public void digest(Node node) {
        var handlers = this.handlers.get(node.description());
        if(handlers != null) {
            for(var handler : handlers) {
                Thread.startVirtualThread(() -> handler.handle(node));
            }
        }
    }

    public void reset() {
        for (var entry : handlers.entrySet()) {
            for(var handler : entry.getValue()) {
                handler.reset();
            }
        }
    }

    public abstract static class Handler {
        protected final WhatsAppClient whatsapp;
        protected final Set<String> descriptions;

        public Handler(WhatsAppClient whatsapp, String... descriptions) {
            this.whatsapp = whatsapp;
            this.descriptions = Set.of(descriptions);
        }

        public abstract void handle(Node node);

        public Set<String> descriptions() {
            return descriptions;
        }

        public void reset() {

        }
    }
}
