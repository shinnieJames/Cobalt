package com.github.auties00.cobalt.stream;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientVerificationHandler;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.message.MessageService;
import com.github.auties00.cobalt.message.receive.receipt.MessageReceiptHandler;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.stream.call.CallAckStreamNodeHandler;
import com.github.auties00.cobalt.stream.call.CallStreamNodeHandler;
import com.github.auties00.cobalt.stream.error.ErrorStreamNodeHandler;
import com.github.auties00.cobalt.stream.error.FailureStreamNodeHandler;
import com.github.auties00.cobalt.stream.ib.IbStreamNodeHandler;
import com.github.auties00.cobalt.stream.iq.IqStreamNodeHandler;
import com.github.auties00.cobalt.stream.message.MessageAckStreamNodeHandler;
import com.github.auties00.cobalt.stream.message.MessageReceiptStreamNodeHandler;
import com.github.auties00.cobalt.stream.message.MessageStreamNodeHandler;
import com.github.auties00.cobalt.stream.notification.NotificationStreamNodeHandler;
import com.github.auties00.cobalt.stream.notification.PresenceStreamNodeHandler;
import com.github.auties00.cobalt.stream.state.*;

import java.util.*;

public final class SocketStream {
    private final Map<String, SequencedCollection<Handler>> handlers;

    public SocketStream(WhatsAppClient whatsapp, WhatsAppClientVerificationHandler.Web webVerificationHandler, LidMigrationService lidMigrationService, MessageService messageService, ABPropsService abPropsService, DeviceService deviceService, WamService wamService) {
        var pairingCode = switch (webVerificationHandler) {
            case WhatsAppClientVerificationHandler.Web.PairingCode _ -> new SocketPhonePairing();
            case WhatsAppClientVerificationHandler.Web.QrCode _ -> null;
        };

        var result = new HashMap<String, SequencedCollection<Handler>>();

        // Common handlers
        addHandler(result, new CallStreamNodeHandler(whatsapp));
        addHandler(result, new CallAckStreamNodeHandler(whatsapp));
        addHandler(result, new ErrorStreamNodeHandler(whatsapp));
        addHandler(result, new FailureStreamNodeHandler(whatsapp));
        addHandler(result, new IbStreamNodeHandler(whatsapp, deviceService));
        addHandler(result, new IqStreamNodeHandler(whatsapp, webVerificationHandler, pairingCode, deviceService));
        var messageReceiptHandler = new MessageReceiptHandler(whatsapp);
        addHandler(result, new MessageStreamNodeHandler(whatsapp, lidMigrationService, messageService, messageReceiptHandler));
        addHandler(result, new MessageAckStreamNodeHandler(whatsapp));
        addHandler(result, new MessageReceiptStreamNodeHandler(whatsapp));
        addHandler(result, new NotificationStreamNodeHandler(whatsapp, pairingCode, lidMigrationService, deviceService, abPropsService));
        addHandler(result, new PresenceStreamNodeHandler(whatsapp));
        addHandler(result, new EndStreamNodeHandler(whatsapp));
        addHandler(result, new UpdateIdentityStreamNodeHandler(whatsapp));

        // Session-specific handlers
        switch (whatsapp.store().clientType()) {
            case WEB -> {
                addHandler(result, new WebNotifyStoreStreamNodeHandler(whatsapp));
                addHandler(result, new WebQueryGroupsStreamNodeHandler(whatsapp));
                addHandler(result, new WebPullInitialAppStatePatchesStreamNodeHandler(whatsapp));
                addHandler(result, new WebSetActiveConnectionStreamNodeHandler(whatsapp));
                addHandler(result, new WebScheduleMediaConnectionUpdateStreamNodeHandler(whatsapp));
                addHandler(result, new WebUpdateSelfPresenceStreamNodeHandler(whatsapp));
                addHandler(result, new WebQuery2faStreamNodeHandler(whatsapp));
                addHandler(result, new WebQueryAboutPrivacyStreamNodeHandler(whatsapp));
                addHandler(result, new WebQueryPrivacySettingsStreamNodeHandler(whatsapp));
                addHandler(result, new WebQueryDisappearingModeStreamNodeHandler(whatsapp));
                addHandler(result, new WebQueryBlockListStreamNodeHandler(whatsapp));
                addHandler(result, new WebOnInitialInfoStreamNodeHandler(whatsapp, lidMigrationService, abPropsService, deviceService, wamService));
                addHandler(result, new WebQueryNewslettersStreamNodeHandler(whatsapp));
                addHandler(result, new WebPropsStreamNodeHandler(whatsapp, abPropsService));
            }
            case MOBILE -> {
                addHandler(result, new MobileFinishLoginStreamNodeHandler(whatsapp, lidMigrationService));
            }
        }

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
