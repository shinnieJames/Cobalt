package com.github.auties00.cobalt.stream.notification.account;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.model.chat.ChatEphemeralTimer;
import com.github.auties00.cobalt.model.contact.ContactTextStatus;
import com.github.auties00.cobalt.model.contact.ContactTextStatusBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

final class NotificationAccountStreamHandler implements SocketStream.Handler {
    private static final System.Logger LOGGER = System.getLogger(NotificationAccountStreamHandler.class.getName());

    private final WhatsAppClient whatsapp;
    private final DeviceService deviceService;

    NotificationAccountStreamHandler(WhatsAppClient whatsapp, DeviceService deviceService) {
        this.whatsapp = whatsapp;
        this.deviceService = Objects.requireNonNull(deviceService, "deviceService cannot be null");
    }

    @Override
    public void handle(Node node) {
        if (!node.hasDescription("notification") || !node.hasAttribute("type", "account_sync")) {
            return;
        }

        try {
            handleNotification(node);
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Cannot handle account_sync notification {0}: {1}",
                    node.getAttributeAsString("id", "<missing>"),
                    throwable.getMessage());
        } finally {
            sendNotificationAck(node);
        }
    }

    private void handleNotification(Node node) {
        for (var child : node.children()) {
            switch (child.description()) {
                case "status" -> {
                    refreshOwnAbout();
                    return;
                }
                case "text_status" -> {
                    updateOwnTextStatus(child);
                    return;
                }
                case "privacy" -> {
                    whatsapp.pullWebAppState(SyncPatchType.REGULAR_LOW, SyncPatchType.REGULAR);
                    return;
                }
                case "devices" -> {
                    refreshOwnDevices(node);
                    return;
                }
                case "blocklist" -> {
                    refreshBlockList();
                    return;
                }
                case "picture" -> {
                    refreshOwnPicture();
                    return;
                }
                case "disappearing_mode" -> {
                    updateDefaultDisappearingMode(child);
                    return;
                }
                case "tos", "notice", "user", "biz_opt_out_list" -> {
                    handleAccountStateNotification(node);
                    return;
                }
                default -> {
                }
            }
        }

        LOGGER.log(System.Logger.Level.DEBUG,
                "Ignoring unrecognized account_sync notification {0}",
                node.getAttributeAsString("id", "<missing>"));
    }

    private void refreshOwnAbout() {
        var self = whatsapp.store().jid().orElse(null);
        if (self == null) {
            return;
        }

        var oldAbout = whatsapp.store().about().orElse("");
        var newAbout = whatsapp.queryAbout(self).orElse("");
        if (Objects.equals(oldAbout, newAbout)) {
            return;
        }

        whatsapp.store().setAbout(newAbout);
        fireListeners(listener -> listener.onAboutChanged(whatsapp, oldAbout, newAbout));
    }

    private void refreshOwnDevices(Node node) {
        var self = whatsapp.store().jid()
                .map(Jid::toUserJid)
                .orElseGet(() -> getUserJid(node, "from"));
        if (self == null) {
            return;
        }

        deviceService.getDeviceLists(Set.of(self), "account_sync_notification", null, true);
    }

    private void refreshBlockList() {
        var blockedJids = new HashSet<>(whatsapp.queryBlockList());
        for (var contact : whatsapp.store().contacts()) {
            var blocked = blockedJids.remove(contact.jid());
            if (contact.blocked() == blocked) {
                continue;
            }

            contact.setBlocked(blocked);
            whatsapp.store().addContact(contact);
            fireListeners(listener -> listener.onContactBlocked(whatsapp, contact.jid()));
        }

        for (var blockedJid : blockedJids) {
            var contact = whatsapp.store()
                    .findContactByJid(blockedJid)
                    .orElseGet(() -> whatsapp.store().addNewContact(blockedJid));
            contact.setBlocked(true);
            whatsapp.store().addContact(contact);
            fireListeners(listener -> listener.onContactBlocked(whatsapp, blockedJid));
        }
    }

    private void refreshOwnPicture() {
        var self = whatsapp.store().jid().orElse(null);
        if (self == null) {
            return;
        }

        var oldPicture = whatsapp.store().profilePicture().orElse(null);
        var newPicture = whatsapp.queryPicture(self).orElse(null);
        if (Objects.equals(oldPicture, newPicture)) {
            return;
        }

        whatsapp.store().setProfilePicture(newPicture);
        fireListeners(listener -> listener.onProfilePictureChanged(whatsapp, self));
    }

    private void updateDefaultDisappearingMode(Node disappearingMode) {
        var duration = disappearingMode.getAttributeAsInt("duration", (Integer) null);
        if (duration == null) {
            return;
        }

        whatsapp.store().setNewChatsEphemeralTimer(ChatEphemeralTimer.of(duration));
    }

    private void handleAccountStateNotification(Node node) {
        var notices = new java.util.HashSet<>(whatsapp.store().tosNoticeIds());
        var noticesUpdated = false;
        for (var child : node.children()) {
            switch (child.description()) {
                case "tos" -> {
                    notices.clear();
                    child.getChildren("notice").forEach(notice -> {
                        var id = notice.getAttributeAsString("id", null);
                        if (id != null) {
                            notices.add(id);
                        }
                    });
                    noticesUpdated = true;
                }
                case "notice" -> {
                    var id = child.getAttributeAsString("id", null);
                    if (id != null) {
                        notices.add(id);
                        noticesUpdated = true;
                    }
                }
                case "user" ->
                        whatsapp.store().setAiAvailable("AI available".equals(child.getAttributeAsString("state", null)));
                case "biz_opt_out_list" ->
                        whatsapp.store().setBusinessOptOutListHash(child.getAttributeAsString("dhash", null));
                default -> {
                }
            }
        }

        if (noticesUpdated) {
            whatsapp.store().setTosNoticeIds(notices);
        }
    }

    private void updateOwnTextStatus(Node textStatusNode) {
        var self = whatsapp.store().jid().orElse(null);
        if (self == null) {
            return;
        }

        var text = textStatusNode.getAttributeAsString("text", null);
        var emoji = textStatusNode.getChild("emoji")
                .flatMap(emojiNode -> emojiNode.getAttributeAsString("content"))
                .orElse(null);
        var ephemeralDuration = textStatusNode.getAttributeAsInt("ephemeral_duration_sec", (Integer) null);
        var lastUpdateTime = textStatusNode.getAttributeAsLong("last_update_time", (Long) null);
        upsertContactTextStatus(
                self.toUserJid(),
                text,
                emoji,
                ephemeralDuration,
                lastUpdateTime == null ? null : Instant.ofEpochSecond(lastUpdateTime)
        );
    }

    private Jid getUserJid(Node node, String key) {
        return node.getAttributeAsJid(key)
                .map(Jid::toUserJid)
                .orElse(null);
    }

    private void fireListeners(java.util.function.Consumer<com.github.auties00.cobalt.client.WhatsAppClientListener> consumer) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> consumer.accept(listener));
        }
    }

    private ContactTextStatus upsertContactTextStatus(
            Jid contactJid,
            String text,
            String emoji,
            Integer ephemeralDurationSeconds,
            Instant lastUpdateTime
    ) {
        var canonicalJid = contactJid.toUserJid();
        var current = whatsapp.store()
                .findContactTextStatus(canonicalJid)
                .orElseGet(() -> new ContactTextStatusBuilder().build());
        current.setText(text);
        current.setEmoji(emoji);
        current.setEphemeralDurationSeconds(ephemeralDurationSeconds);
        current.setLastUpdateTime(lastUpdateTime);
        whatsapp.store().addContactTextStatus(canonicalJid, current);
        notifyContactTextStatusChanged(canonicalJid, current);
        return current;
    }

    private void notifyContactTextStatusChanged(Jid contactJid, ContactTextStatus status) {
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onContactTextStatus(whatsapp, contactJid, status));
        }
    }

    private void sendNotificationAck(Node node) {
        var stanzaId = node.getAttributeAsString("id", null);
        var stanzaFrom = node.getAttributeAsJid("from", null);
        if (stanzaId == null || stanzaFrom == null) {
            return;
        }

        whatsapp.sendNodeWithNoResponse(new com.github.auties00.cobalt.node.NodeBuilder()
                .description("ack")
                .attribute("id", stanzaId)
                .attribute("class", node.description())
                .attribute("to", stanzaFrom)
                .attribute("type", node.getAttributeAsString("type", null))
                .attribute("participant", node.getAttributeAsJid("participant", null))
                .build());
    }
}
