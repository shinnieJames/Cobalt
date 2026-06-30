package com.github.auties00.cobalt.socket.state;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.auth.SignedDeviceIdentitySpec;
import com.github.auties00.cobalt.model.auth.UserAgent.PlatformType;
import com.github.auties00.cobalt.model.contact.ContactStatus;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.socket.SocketStream;

import java.time.ZonedDateTime;

import static com.github.auties00.cobalt.client.WhatsAppClientErrorHandler.Location.MEDIA_CONNECTION;

public final class MobileFinishLoginStreamNodeHandler extends SocketStream.Handler {
    private final MediaConnectionBootstrap mediaConnectionBootstrap;

    public MobileFinishLoginStreamNodeHandler(WhatsAppClient whatsapp) {
        super(whatsapp, "success");
        this.mediaConnectionBootstrap = new MediaConnectionBootstrap(whatsapp);
    }

    @Override
    public void handle(Node node) {
        // Mobile login may complete before a dedicated identity update arrives.
        // Ensure local identity is available for callbacks that immediately send messages.
        var store = whatsapp.store();
        var shouldSerialize = false;
        if (store.jid().isEmpty()) {
            var jid = node.getAttributeAsJid("jid")
                    .or(() -> node.getAttributeAsJid("from"))
                    .or(() -> node.getChild("device").flatMap(device -> device.getAttributeAsJid("jid")))
                    .or(() -> store.phoneNumber().stream().mapToObj(Jid::of).findFirst());
            if (jid.isPresent()) {
                store.setJid(jid.get());
                shouldSerialize = true;
            }
        }

        if (store.lid().isEmpty()) {
            var lid = node.getAttributeAsJid("lid")
                    .or(() -> node.getChild("device").flatMap(device -> device.getAttributeAsJid("lid")));
            if (lid.isPresent()) {
                store.setLid(lid.get());
                shouldSerialize = true;
            }
        }

        var platform = getPlatform(node);
        if (platform != null && store.device().platform() != platform) {
            store.setDevice(store.device().withPlatform(platform));
            shouldSerialize = true;
        }

        if (store.companionIdentity().isEmpty()) {
            var companionIdentity = node.getChild("device-identity")
                    .flatMap(Node::toContentBytes)
                    .map(SignedDeviceIdentitySpec::decode)
                    .orElse(null);
            if (companionIdentity != null) {
                store.setCompanionIdentity(companionIdentity);
                shouldSerialize = true;
            }
        }

        if (!store.registered()) {
            store.setRegistered(true);
            shouldSerialize = true;
        }

        if (shouldSerialize) {
            store.serialize();
        }

        sendActive();
        sendAvailablePresence();

        try {
            mediaConnectionBootstrap.start();
        } catch (Exception exception) {
            whatsapp.handleFailure(MEDIA_CONNECTION, exception);
        }

        if (!store.hasPreKeys()) {
            whatsapp.sendPreKeys(0);
            store.serialize();
        }

        for (var listener : store.listeners()) {
            Thread.startVirtualThread(() -> listener.onLoggedIn(whatsapp));
        }
    }

    private PlatformType getPlatform(Node node) {
        var platform = node.getAttributeAsString("platform")
                .or(() -> node.getChild("device").flatMap(device -> device.getAttributeAsString("platform")))
                .orElse(null);
        return switch (platform) {
            case "smbi" -> PlatformType.IOS_BUSINESS;
            case "smba" -> PlatformType.ANDROID_BUSINESS;
            case "android" -> PlatformType.ANDROID;
            case "ios" -> PlatformType.IOS;
            case null, default -> null;
        };
    }

    private void sendActive() {
        var active = new NodeBuilder()
                .description("active")
                .build();
        var query = new NodeBuilder()
                .description("iq")
                .attribute("to", JidServer.user())
                .attribute("type", "set")
                .attribute("xmlns", "passive")
                .content(active);
        whatsapp.sendNode(query);
    }

    private void sendAvailablePresence() {
        var presence = new NodeBuilder()
                .description("presence")
                .attribute("name", whatsapp.store().name())
                .attribute("type", "available")
                .build();
        whatsapp.sendNodeWithNoResponse(presence);
        whatsapp.store().setOnline(true);
        whatsapp.store()
                .jid()
                .flatMap(whatsapp.store()::findContactByJid)
                .ifPresent(entry -> {
                    entry.setLastKnownPresence(ContactStatus.AVAILABLE);
                    entry.setLastSeen(ZonedDateTime.now());
                });
    }

    @Override
    public void reset() {
        mediaConnectionBootstrap.reset();
    }
}
