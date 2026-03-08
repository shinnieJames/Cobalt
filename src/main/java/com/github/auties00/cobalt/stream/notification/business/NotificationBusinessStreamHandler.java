package com.github.auties00.cobalt.stream.notification.business;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.business.profile.BusinessProfile;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

public final class NotificationBusinessStreamHandler implements SocketStream.Handler {
    private static final System.Logger LOGGER =
            System.getLogger(NotificationBusinessStreamHandler.class.getName());
    private final WhatsAppClient whatsapp;

    public NotificationBusinessStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    @Override
    public void handle(Node node) {
        if (!node.hasDescription("notification") || !node.hasAttribute("type", "business")) {
            return;
        }

        try {
            var actionNode = node.getChild(
                    "remove",
                    "verified_name",
                    "profile",
                    "product_catalog",
                    "subscriptions",
                    "ctwa_suggestion",
                    "privacy",
                    "wa_ad_account_nonce",
                    "mm_campaign",
                    "feature_flags"
            ).orElse(null);
            if (actionNode == null) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Business notification {0} has no known action child",
                        node.getAttributeAsString("id", "[missing-id]"));
                return;
            }

            var targetJid = actionNode.getAttributeAsJid("jid")
                    .or(() -> node.getAttributeAsJid("from"))
                    .map(Jid::withoutData)
                    .orElse(null);

            switch (actionNode.description()) {
                case "verified_name" -> handleVerifiedName(targetJid);
                case "profile" -> handleProfile(targetJid);
                case "remove" -> handleRemove(targetJid);
                case "product_catalog" -> handleCatalogRefresh(targetJid, actionNode);
                case "subscriptions" -> handleSubscriptions(actionNode);
                case "ctwa_suggestion" -> LOGGER.log(System.Logger.Level.DEBUG,
                        "Received ctwa_suggestion business notification");
                case "privacy" -> handlePrivacy(actionNode);
                case "wa_ad_account_nonce" -> handleNonce(actionNode);
                case "mm_campaign" -> handleCampaign(actionNode);
                case "feature_flags" -> handleFeatureFlags(actionNode);
                default -> LOGGER.log(System.Logger.Level.DEBUG,
                        "Ignoring unsupported business notification subtype {0}",
                        actionNode.description());
            }
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to handle business notification {0}: {1}",
                    node.getAttributeAsString("id", "[missing-id]"), throwable.getMessage());
        } finally {
            sendNotificationAck(node);
        }
    }

    private void handleVerifiedName(Jid targetJid) {
        if (targetJid == null) {
            return;
        }

        if (isSelf(targetJid)) {
            whatsapp.queryName(targetJid)
                    .ifPresent(whatsapp.store()::setVerifiedName);
            whatsapp.store().setSyncedBusinessCertificate(true);
        } else {
            whatsapp.queryBusinessProfile(targetJid);
        }
    }

    private void handleProfile(Jid targetJid) {
        if (targetJid == null) {
            return;
        }

        var refreshed = whatsapp.queryBusinessProfile(targetJid).orElse(null);
        if (refreshed == null || !isSelf(targetJid)) {
            return;
        }

        applyOwnBusinessProfile(refreshed);
        whatsapp.store().setSyncedBusinessCertificate(true);
    }

    private void handleRemove(Jid targetJid) {
        if (targetJid == null || !isSelf(targetJid)) {
            return;
        }

        whatsapp.store()
                .setVerifiedName(null)
                .setBusinessAddress(null)
                .setBusinessDescription(null)
                .setBusinessEmail(null)
                .setBusinessWebsite(null)
                .setBusinessCategory(null)
                .setSyncedBusinessCertificate(false);
    }

    private void handleCatalogRefresh(Jid targetJid, Node actionNode) {
        if (targetJid == null) {
            return;
        }

        for (var child : actionNode.children()) {
            switch (child.description()) {
                case "product" -> {
                    whatsapp.queryBusinessCatalog(targetJid);
                    return;
                }
                case "collection" -> {
                    whatsapp.queryBusinessCollections(targetJid);
                    return;
                }
                default -> {
                }
            }
        }
    }

    private void applyOwnBusinessProfile(BusinessProfile profile) {
        whatsapp.store()
                .setBusinessDescription(profile.description().orElse(null))
                .setBusinessAddress(profile.address().orElse(null))
                .setBusinessEmail(profile.email().orElse(null))
                .setBusinessWebsite(profile.websites().stream().findFirst().map(Object::toString).orElse(null))
                .setBusinessCategory(profile.categories().stream().findFirst().orElse(null));
    }

    private boolean isSelf(Jid jid) {
        return whatsapp.store().jid()
                .map(self -> self.isSameAccount(jid))
                .orElse(false);
    }

    private void handleSubscriptions(Node actionNode) {
        var flags = new java.util.HashMap<>(whatsapp.store().businessFeatureFlags());
        var statuses = new java.util.HashMap<>(whatsapp.store().businessSubscriptionStatuses());
        var expirations = new java.util.HashMap<>(whatsapp.store().businessSubscriptionExpirations());
        var creationTimes = new java.util.HashMap<>(whatsapp.store().businessSubscriptionCreationTimes());
        actionNode.getChildren("feature_flag").forEach(featureFlag -> {
            var name = featureFlag.getAttributeAsString("name", null);
            var enabled = featureFlag.getAttributeAsString("enabled", null);
            if (name != null && enabled != null) {
                flags.put(name, "true".equalsIgnoreCase(enabled));
            }
        });
        actionNode.getChildren("subscription").forEach(subscription -> {
            var id = subscription.getAttributeAsString("id", null);
            var status = subscription.getAttributeAsString("status", null);
            if (id != null && status != null) {
                statuses.put(id, status);
            }
            var expiration = subscription.getAttributeAsLong("subscription_end_time", (Long) null);
            if (id != null && expiration != null) {
                expirations.put(id, expiration);
            }
            var creationTime = subscription.getAttributeAsLong("subscription_creation_time", (Long) null);
            if (id != null && creationTime != null) {
                creationTimes.put(id, creationTime);
            }
        });
        whatsapp.store().setBusinessFeatureFlags(flags);
        whatsapp.store().setBusinessSubscriptionStatuses(statuses);
        whatsapp.store().setBusinessSubscriptionExpirations(expirations);
        whatsapp.store().setBusinessSubscriptionCreationTimes(creationTimes);
    }

    private void handlePrivacy(Node actionNode) {
        var enabled = actionNode.getAttributeAsString("smb_data_sharing_setting", null);
        if (enabled != null) {
            whatsapp.store().setCtwaDataSharingEnabled("true".equalsIgnoreCase(enabled));
        }
    }

    private void handleNonce(Node actionNode) {
        actionNode.toContentString()
                .ifPresent(whatsapp.store()::setBusinessAccountNonce);
    }

    private void handleCampaign(Node actionNode) {
        var statuses = new java.util.HashMap<>(whatsapp.store().businessCampaignStatuses());
        var creativeId = actionNode.getAttributeAsString("adCreativeId", null);
        var status = actionNode.getAttributeAsString("status", null);
        if (creativeId != null && status != null) {
            statuses.put(creativeId, status);
            whatsapp.store().setBusinessCampaignStatuses(statuses);
        }
    }

    private void handleFeatureFlags(Node actionNode) {
        var flags = new java.util.HashMap<>(whatsapp.store().businessFeatureFlags());
        actionNode.getChildren("feature_flag").forEach(featureFlag -> {
            var name = featureFlag.getAttributeAsString("name", null);
            var enabled = featureFlag.getAttributeAsString("enabled", null);
            if (name != null && enabled != null) {
                flags.put(name, "true".equalsIgnoreCase(enabled));
            }
        });
        whatsapp.store().setBusinessFeatureFlags(flags);
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
