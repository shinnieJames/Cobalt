package com.github.auties00.cobalt.stream.notification.business;

import com.github.auties00.cobalt.ack.AckClass;
import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.model.business.BusinessCampaignStatusBuilder;
import com.github.auties00.cobalt.model.business.BusinessFeatureFlagBuilder;
import com.github.auties00.cobalt.model.business.BusinessSubscriptionBuilder;
import com.github.auties00.cobalt.model.business.profile.BusinessProfile;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.biz.SmaxNonceNotificationResponse;
import com.github.auties00.cobalt.node.smax.biz.SmaxSyncPrivacySettingResponse;
import com.github.auties00.cobalt.stream.SocketStream;

import java.time.Instant;

/**
 * Handles {@code type="business"}, {@code type="digital_commerce_subscription"},
 * and {@code type="fb:update"} notifications carrying WhatsApp Business
 * mutations.
 *
 * @apiNote
 * Dispatched by {@link NotificationBusinessDispatcher}. The
 * {@code business} type covers verified-name changes, business
 * deregistration, business-profile updates, product-catalog updates,
 * subscription/feature-flag pushes, CTWA banner suggestions, SMB
 * privacy-settings sync, ad-account nonce delivery, and marketing-campaign
 * state changes. {@code digital_commerce_subscription} re-uses the
 * subscription parser, and {@code fb:update} delivers bot-profile updates.
 *
 * @implNote
 * This implementation produces an ack with an optional
 * {@code <user side_list="out"/>} child when a hash-based lookup failed,
 * mirroring WA Web's
 * {@code WAWebHandleBusinessNotification.c(stanzaId, from, needsSideList)}
 * builder. The side-list flag asks the server to redistribute the
 * notification to companion devices that may have the contact record
 * Cobalt could not resolve.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleBusinessNotification")
public final class NotificationBusinessStreamHandler implements SocketStream.Handler {

    /**
     * Logger used for warnings about malformed stanzas and debug messages
     * about unsupported sub-types.
     */
    private static final System.Logger LOGGER =
            System.getLogger(NotificationBusinessStreamHandler.class.getName());

    /**
     * The {@link WhatsAppClient} used for store reads and business-profile
     * queries.
     */
    private final WhatsAppClient whatsapp;

    /**
     * The {@link AckSender} used to ship the post-processing
     * {@code <ack class="notification">} stanzas for {@code business},
     * {@code digital_commerce_subscription} and {@code fb:update}
     * notifications.
     */
    private final AckSender ackSender;

    /**
     * Constructs the handler with the shared client and ack sender.
     *
     * @apiNote
     * Called once by {@link NotificationBusinessDispatcher}; embedders
     * do not instantiate this handler directly.
     *
     * @param whatsapp  the {@link WhatsAppClient}
     * @param ackSender the {@link AckSender}
     */
    public NotificationBusinessStreamHandler(WhatsAppClient whatsapp, AckSender ackSender) {
        this.whatsapp = whatsapp;
        this.ackSender = ackSender;
    }

    /**
     * Validates the stanza shape, dispatches to the per-type sub-handler,
     * and always sends the protocol-level ACK.
     *
     * @apiNote
     * Invoked by {@link NotificationBusinessDispatcher}. The {@code type}
     * attribute routes between {@code business} (delegates to
     * {@link #handleBusinessNotification(Node)}),
     * {@code digital_commerce_subscription} (delegates to
     * {@link #handleDigitalCommerceSubscription(Node)}), and
     * {@code fb:update} (delegates to {@link #handleBotProfileUpdate(Node)}).
     *
     * @implNote
     * This implementation routes the three types from one Cobalt handler;
     * WA Web wraps each one in a separate
     * {@code createNonPersistedJob} so the orchestrator can reorder them
     * relative to other non-critical work. Cobalt's virtual-thread fan-out
     * at the dispatcher level achieves the equivalent concurrency.
     *
     * @param node the incoming {@code <notification>} stanza
     */
    @Override
    public void handle(Node node) {
        if (!node.hasDescription("notification")) {
            return;
        }

        var type = node.getAttributeAsString("type", null);
        if (type == null) {
            return;
        }

        switch (type) {
            case "business" -> handleBusinessNotification(node);
            case "digital_commerce_subscription" -> handleDigitalCommerceSubscription(node);
            case "fb:update" -> handleBotProfileUpdate(node);
            default -> {
            }
        }
    }

    /**
     * Routes a {@code business}-type stanza through {@link #dispatch(Node)}
     * and sends the ack with the side-list child the dispatch returned.
     *
     * @apiNote
     * The side-list flag asks the server to redistribute the
     * notification to companions that may have the contact record we
     * could not resolve from a hash.
     *
     * @param node the {@code <notification type="business"/>} stanza
     */
    private void handleBusinessNotification(Node node) {
        var needsSideList = false;
        try {
            needsSideList = dispatch(node);
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to handle business notification {0}: {1}",
                    node.getAttributeAsString("id", "[missing-id]"), throwable.getMessage());
        } finally {
            sendBusinessAck(node, needsSideList);
        }
    }

    /**
     * Routes a {@code digital_commerce_subscription}-type stanza through
     * the shared {@link #handleSubscriptions(Node)} path and sends the
     * dedicated digital-commerce ack.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebHandleDigitalCommerceSubscriptionNotification} which
     * reuses
     * {@code WAWebSubscriptions.applySubscriptionsAndFeatureFlags} on
     * the parsed payload. The notification type is distinct from
     * {@code business} so the server can route based on subscriber
     * eligibility but the wire shape is the same.
     *
     * @param node the {@code <notification type="digital_commerce_subscription"/>} stanza
     */
    private void handleDigitalCommerceSubscription(Node node) {
        try {
            handleSubscriptions(node);
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to handle digital_commerce_subscription notification {0}: {1}",
                    node.getAttributeAsString("id", "[missing-id]"), throwable.getMessage());
        } finally {
            sendDigitalCommerceSubscriptionAck(node);
        }
    }

    /**
     * Processes a {@code fb:update} stanza by iterating
     * {@code <update type="bot_profile"/>} children and refreshing each
     * referenced bot profile.
     *
     * @apiNote
     * Drives the in-chat bot affordances for accounts that have
     * registered Meta AI bots. A pruned update (an {@code <update>}
     * with no {@code jid}) signals that the server has invalidated the
     * cache and a full bot resync is needed.
     *
     * @implNote
     * This implementation only logs the bot JID and category. WA Web's
     * {@code WAWebHandleBotProfileNotification} fires a frontend
     * refresh on each bot id; Cobalt does not maintain an in-memory
     * bot-profile collection and lets callers re-query on demand.
     *
     * @param node the {@code <notification type="fb:update"/>} stanza
     */
    private void handleBotProfileUpdate(Node node) {
        try {
            var pruned = false;
            for (var child : node.children()) {
                if (!"update".equals(child.description())) {
                    continue;
                }

                var childType = child.getAttributeAsString("type", null);
                if (!"bot_profile".equals(childType)) {
                    continue;
                }

                var botJid = child.getAttributeAsString("jid", null);
                if (botJid == null || botJid.isEmpty()) {
                    pruned = true;
                    break;
                }

                LOGGER.log(System.Logger.Level.DEBUG,
                        "Bot profile update for {0}, category={1}",
                        botJid,
                        child.getAttributeAsString("category", null));
            }

            if (pruned) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Pruned bot profile update, full bot sync needed");
            }
        } catch (Throwable throwable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Failed to handle fb:update notification {0}: {1}",
                    node.getAttributeAsString("id", "[missing-id]"), throwable.getMessage());
        } finally {
            sendBotProfileAck(node);
        }
    }

    /**
     * Selects the {@code business}-type branch from the first recognised
     * child element and returns whether the ack should request a
     * side-list redistribution.
     *
     * @apiNote
     * Mirrors WA Web's {@code businessNotificationParser} type encoding:
     * {@code remove_jid}/{@code remove_hash}, {@code verified_name_jid}/
     * {@code verified_name_hash}, {@code profile}/{@code profile_hash},
     * {@code product}/{@code collection}, {@code subscriptions},
     * {@code ctwa_suggestion}, {@code privacy},
     * {@code wa_ad_account_nonce}, {@code mm_campaign}, and an unknown
     * fall-through.
     *
     * @param node the full {@code <notification>} stanza
     * @return {@code true} when a hash-based lookup failed and the ack should include {@code <user side_list="out"/>}; {@code false} otherwise
     */
    private boolean dispatch(Node node) {
        if (node.hasChild("remove")) {
            return handleRemove(node.getRequiredChild("remove"));
        }

        if (node.hasChild("verified_name")) {
            return handleVerifiedName(node.getRequiredChild("verified_name"));
        }

        if (node.hasChild("profile")) {
            return handleProfile(node, node.getRequiredChild("profile"));
        }

        if (node.hasChild("product_catalog")) {
            handleProductCatalog(node.getRequiredChild("product_catalog"));
            return false;
        }

        if (node.hasChild("subscriptions")) {
            handleSubscriptions(node);
            return false;
        }

        if (node.hasChild("ctwa_suggestion")) {
            // TODO: implement the CTWA action-banner suggestion pipeline once Cobalt has an equivalent of WAWebHandleCTWASuggestion.
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Received ctwa_suggestion business notification (not implemented)");
            return false;
        }

        if (node.hasChild("privacy")) {
            handlePrivacy(node);
            return false;
        }

        if (node.hasChild("wa_ad_account_nonce")) {
            handleAdAccountNonce(node);
            return false;
        }

        if (node.hasChild("mm_campaign")) {
            handleMarketingCampaign(node);
            return false;
        }

        LOGGER.log(System.Logger.Level.DEBUG,
                "Received unknown business notification subtype");
        return false;
    }

    /**
     * Clears the local business fields when the {@code <remove>} child
     * targets self by JID, and returns whether a hash-only remove
     * needs the side-list ack.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebHandleBusinessRemoval.handleBusinessRemovalNotificationContact}
     * (jid branch) and
     * {@code handleBusinessRemovalNotificationHash} (hash branch). The
     * hash branch in Cobalt always returns {@code true} because
     * Cobalt has no hash-keyed contact lookup.
     *
     * @implNote
     * This implementation clears verified name, address, description,
     * email, websites, categories, and the
     * {@code syncedBusinessCertificate} flag when the remove targets
     * the local account. WA Web also fires a
     * {@code privacy_system_message} into the chat thread; Cobalt's
     * message-generation pipeline runs from a different stream.
     *
     * @param removeNode the {@code <remove>} child node
     * @return {@code true} when hash-based lookup failed (Cobalt always returns true on the hash branch), {@code false} otherwise
     */
    private boolean handleRemove(Node removeNode) {
        var jid = removeNode.getAttributeAsJid("jid").orElse(null);
        if (jid != null) {
            var targetJid = jid.withoutData();
            if (isSelf(targetJid)) {
                whatsapp.store()
                        .setVerifiedName(null)
                        .setBusinessAddress(null)
                        .setBusinessDescription(null)
                        .setBusinessEmail(null)
                        .setBusinessWebsites(null)
                        .setBusinessCategories(null)
                        .setSyncedBusinessCertificate(false);
            }
            return false;
        }

        // TODO: resolve the contact via hash and apply the remove locally. Today Cobalt requests side-list redistribution so a companion that owns the contact applies it instead.
        var hash = removeNode.getAttributeAsString("hash", null);
        LOGGER.log(System.Logger.Level.DEBUG,
                "Cannot handle hash-based business removal (hash={0}), requesting side-list redistribution",
                hash);
        return true;
    }

    /**
     * Re-queries the verified name (self branch) or the business profile
     * (peer branch) when the {@code <verified_name>} child targets a
     * JID; returns the side-list flag when the targeting is hash-based.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebHandleBusinessNameChange.handleVerifiedBusinessNameNotificationContact}
     * (jid branch) and
     * {@code handleVerifiedBusinessNameNotificationHash} (hash branch).
     *
     * @implNote
     * This implementation defers the verified name resolution to
     * {@link WhatsAppClient#queryName(Jid)}
     * for self and to
     * {@link WhatsAppClient#queryBusinessProfile(Jid)}
     * for peers. The hash branch always returns {@code true} because
     * Cobalt has no hash-keyed contact lookup.
     *
     * @param verifiedNameNode the {@code <verified_name>} child node
     * @return {@code true} when hash-based and the contact was not resolved; {@code false} otherwise
     */
    private boolean handleVerifiedName(Node verifiedNameNode) {
        var jid = verifiedNameNode.getAttributeAsJid("jid").orElse(null);
        if (jid != null) {
            var targetJid = jid.withoutData();
            if (isSelf(targetJid)) {
                whatsapp.queryName(targetJid)
                        .ifPresent(whatsapp.store()::setVerifiedName);
                whatsapp.store().setSyncedBusinessCertificate(true);
            } else {
                whatsapp.queryBusinessProfile(targetJid);
            }
            return false;
        }

        // TODO: resolve the contact via hash. Today Cobalt requests side-list redistribution so a companion that owns the contact applies the verified-name change instead.
        var hash = verifiedNameNode.getAttributeAsString("hash", null);
        LOGGER.log(System.Logger.Level.DEBUG,
                "Cannot handle hash-based verified name change (hash={0}), requesting side-list redistribution",
                hash);
        return true;
    }

    /**
     * Re-queries the business profile for self when the
     * {@code <profile>} child has no hash attribute; returns the
     * side-list flag otherwise.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebHandleBusinessProfile.handleBusinessProfile} (no-hash
     * branch) and
     * {@code handleBusinessProfileHash} (hash branch).
     *
     * @implNote
     * This implementation applies the queried profile to the local
     * store only when the target is self; for non-self the queried
     * profile is discarded after the server-side state is refreshed
     * (the call has its own caching semantics).
     *
     * @param node        the full {@code <notification>} stanza, used to read {@code from}
     * @param profileNode the {@code <profile>} child node
     * @return {@code true} when hash-based and Cobalt cannot resolve the contact; {@code false} otherwise
     */
    private boolean handleProfile(Node node, Node profileNode) {
        var hash = profileNode.getAttributeAsString("hash", null);
        if (hash == null || hash.isEmpty()) {
            var targetJid = node.getAttributeAsJid("from")
                    .map(Jid::withoutData)
                    .orElse(null);
            if (targetJid != null) {
                var refreshed = whatsapp.queryBusinessProfile(targetJid).orElse(null);
                if (refreshed != null && isSelf(targetJid)) {
                    applyOwnBusinessProfile(refreshed);
                    whatsapp.store().setSyncedBusinessCertificate(true);
                }
            }
            return false;
        }

        // TODO: resolve the contact via hash. Today Cobalt requests side-list redistribution for the hash branch.
        LOGGER.log(System.Logger.Level.DEBUG,
                "Cannot handle hash-based business profile update (hash={0}), requesting side-list redistribution",
                hash);
        return true;
    }

    /**
     * Logs the product or collection counts contained in a
     * {@code <product_catalog>} child.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebHandleBusinessProductCatalogNotification.handleProductNotification}
     * and {@code handleCollectionNotification}.
     *
     * @implNote
     * This implementation does not refresh the catalog from the
     * server. WA Web invokes
     * {@code frontendSendAndReceive("refreshCatalogProducts", ...)}
     * to drive its catalog cache; Cobalt has no equivalent in-memory
     * catalog cache so the change is observable only on the next
     * explicit {@code queryBusinessCatalog} call.
     *
     * @param catalogNode the {@code <product_catalog>} child node
     */
    private void handleProductCatalog(Node catalogNode) {
        if (catalogNode.hasChild("product")) {
            var productIds = catalogNode.getChildren("product").stream()
                    .flatMap(product -> product.getChild("id").stream())
                    .flatMap(idNode -> idNode.toContentString().stream())
                    .toList();
            if (!productIds.isEmpty()) {
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Received product catalog notification for {0} products", productIds.size());
            }
        } else if (catalogNode.hasChild("collection")) {
            var collectionCount = catalogNode.getChildren("collection").size();
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Received collection catalog notification for {0} collections", collectionCount);
        }
    }

    /**
     * Applies the queried business profile fields to the local store
     * for the authenticated business account.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code frontendSendAndReceive("updateBusinessProfile", ...)}
     * frontend event by writing the same fields directly.
     *
     * @param profile the refreshed business profile
     */
    private void applyOwnBusinessProfile(BusinessProfile profile) {
        whatsapp.store()
                .setBusinessDescription(profile.description().orElse(null))
                .setBusinessAddress(profile.address().orElse(null))
                .setBusinessEmail(profile.email().orElse(null))
                .setBusinessWebsites(profile.websites())
                .setBusinessCategories(profile.categories());
    }

    /**
     * Returns whether the given JID identifies the authenticated account.
     *
     * @apiNote
     * Internal helper used by {@link #handleRemove(Node)},
     * {@link #handleVerifiedName(Node)}, and
     * {@link #handleProfile(Node, Node)} to distinguish self-business
     * mutations from peer-business mutations.
     *
     * @param jid the JID to check
     * @return {@code true} when {@code jid} matches the local account, {@code false} otherwise
     */
    private boolean isSelf(Jid jid) {
        return whatsapp.store().jid()
                .map(self -> self.isSameAccount(jid))
                .orElse(false);
    }

    /**
     * Reads {@code feature_flags} and {@code subscriptions} children
     * from a {@code business} or {@code digital_commerce_subscription}
     * notification and merges each entry into the store.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WAWebParseSubscriptionNotification.parseSubscriptionsAndFeatureFlags}
     * (parser) and
     * {@code WAWebSubscriptions.applySubscriptionsAndFeatureFlags(..., "update")}
     * (applier).
     *
     * @implNote
     * This implementation merges existing fields with the new values
     * when the stanza is partial (subscription stanzas often carry
     * only the changed attribute), matching WA Web's
     * shallow-merge semantics. Subscription identifiers without a
     * status, expiration, or creation-time are still applied so the
     * record is created.
     *
     * @param node the full {@code <notification>} stanza (the children live directly under the notification, not under the subscriptions child)
     */
    private void handleSubscriptions(Node node) {
        node.getChild("feature_flags").ifPresent(featureFlagsNode -> {
            featureFlagsNode.getChildren("feature_flag").forEach(featureFlag -> {
                var name = featureFlag.getAttributeAsString("name", null);
                var enabled = featureFlag.getAttributeAsString("enabled", null);
                if (name != null && enabled != null) {
                    whatsapp.store().putBusinessFeatureFlag(new BusinessFeatureFlagBuilder()
                            .name(name)
                            .enabled("true".equalsIgnoreCase(enabled))
                            .build());
                }
            });
        });

        node.getChild("subscriptions").ifPresent(subscriptionsNode -> {
            subscriptionsNode.getChildren("subscription").forEach(subscription -> {
                var id = subscription.getAttributeAsString("id", null);
                if (id == null) {
                    return;
                }
                var existing = whatsapp.store().findBusinessSubscription(id).orElse(null);
                var builder = new BusinessSubscriptionBuilder().id(id);
                if (existing != null) {
                    existing.status().ifPresent(builder::status);
                    existing.expiration().ifPresent(builder::expiration);
                    existing.createdAt().ifPresent(builder::createdAt);
                }
                var status = subscription.getAttributeAsString("status", null);
                if (status != null) {
                    builder.status(status);
                }
                var expiration = subscription.getAttributeAsLong("subscription_end_time", (Long) null);
                if (expiration != null) {
                    builder.expiration(Instant.ofEpochSecond(expiration));
                }
                var creationTime = subscription.getAttributeAsLong("subscription_creation_time", (Long) null);
                if (creationTime != null) {
                    builder.createdAt(Instant.ofEpochSecond(creationTime));
                }
                whatsapp.store().putBusinessSubscription(builder.build());
            });
        });
    }

    /**
     * Writes the SMB data-sharing-with-Meta consent value parsed from
     * the {@code <privacy>} child to the store.
     *
     * @apiNote
     * Mirrors WA Web's flow
     * {@code WAWebCTWAParsePrivacy.parseCTWAPrivacy} ->
     * {@code handleSmbDataSharingSettingNotification} ->
     * {@code CTWADataSharingModel.setValue(value)}. Cobalt persists
     * the wire literal ({@code "false"}, {@code "notset"}, {@code "true"})
     * directly via
     * {@link com.github.auties00.cobalt.store.AbstractWhatsAppStore#setSmbDataSharingConsent}.
     *
     * @implNote
     * This implementation routes through the typed
     * {@link SmaxSyncPrivacySettingResponse} parser so the SMAX export
     * remains the single source of truth for envelope validation.
     *
     * @param node the {@code <notification>} stanza
     */
    private void handlePrivacy(Node node) {
        var consent = SmaxSyncPrivacySettingResponse.of(node)
                .flatMap(SmaxSyncPrivacySettingResponse.Notification::dataSharingConsent)
                .orElse(null);
        if (consent == null) {
            return;
        }
        whatsapp.store().setSmbDataSharingConsent(consent);
    }

    /**
     * Stores the ad-account nonce parsed from a
     * {@code <wa_ad_account_nonce>} child.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WASmaxBizCtwaAdAccountNonceNotificationRPC.receiveNonceNotificationRPC}
     * parser followed by
     * {@code WAWebCTWABizAccessTokenNonceManager.setNonceFromPushNotification}.
     * The nonce is a short-lived token consumed by the next ad-creation
     * authentication call.
     *
     * @implNote
     * This implementation routes through the typed
     * {@link SmaxNonceNotificationResponse} parser then writes the
     * nonce to the store via
     * {@link com.github.auties00.cobalt.store.AbstractWhatsAppStore#setBusinessAccountNonce}.
     *
     * @param node the {@code <notification>} stanza
     */
    private void handleAdAccountNonce(Node node) {
        SmaxNonceNotificationResponse.of(node)
                .map(SmaxNonceNotificationResponse.Notification::nonce)
                .ifPresent(whatsapp.store()::setBusinessAccountNonce);
    }

    /**
     * Writes the marketing-campaign status carried by a
     * {@code <mm_campaign>} child to the store.
     *
     * @apiNote
     * Mirrors WA Web's
     * {@code WASmaxSmbMeteredMessagesCampaignCampaignStateChangedNotificationRPC.receiveCampaignStateChangedNotificationRPC}
     * parser followed by
     * {@code WAWebBizBroadcastMarketingCampaignNotificationEmitter.marketingCampaignNotificationEmitter.emit(...)}.
     * Cobalt stores the campaign status keyed by the ad-creative id.
     *
     * @implNote
     * This implementation reads the inline {@code <mm_campaign>} child
     * attributes directly rather than re-parsing via SMAX, because the
     * notification carries the fields the campaign store needs and a
     * second parser pass adds no validation. The notification is
     * dropped when any of the three id fields is missing, matching WA
     * Web's null check.
     *
     * @param node the {@code <notification>} stanza
     */
    private void handleMarketingCampaign(Node node) {
        var campaignNode = node.getChild("mm_campaign").orElse(null);
        if (campaignNode == null) {
            return;
        }

        var adCreativeId = campaignNode.getAttributeAsString("adCreativeId", null);
        var adGroupId = campaignNode.getAttributeAsString("adGroupId", null);
        var adId = campaignNode.getAttributeAsString("adId", null);
        var status = campaignNode.getAttributeAsString("status", null);
        if (adCreativeId == null || adGroupId == null || adId == null) {
            return;
        }

        if (status != null) {
            whatsapp.store().putBusinessCampaignStatus(new BusinessCampaignStatusBuilder()
                    .campaignId(adCreativeId)
                    .status(status)
                    .build());
        }
    }

    /**
     * Sends the {@code <ack class="notification" type="business"/>}
     * stanza, with an optional {@code <user side_list="out"/>} child
     * when {@code needsSideList} is {@code true}.
     *
     * @apiNote
     * Fire-and-forget; identical attribute set to WA Web's
     * {@code c(stanzaId, from, needsSideList)} ack-builder. The
     * side-list child asks the server to redistribute the notification
     * to companions that may own the contact record this device could
     * not resolve.
     *
     * @param node          the original {@code <notification>} stanza
     * @param needsSideList whether the ack should include the side-list child
     */
    private void sendBusinessAck(Node node, boolean needsSideList) {
        var builder = ackSender.ack(AckClass.NOTIFICATION, node).type("business");
        if (needsSideList) {
            builder.child(new NodeBuilder()
                    .description("user")
                    .attribute("side_list", "out")
                    .build());
        }
        builder.send();
    }

    /**
     * Sends the {@code <ack class="notification"
     * type="digital_commerce_subscription"/>} stanza.
     *
     * @apiNote
     * Fire-and-forget; identical attribute set to WA Web's
     * {@code WAWebHandleDigitalCommerceSubscriptionNotification}
     * ack-builder.
     *
     * @param node the original {@code <notification>} stanza
     */
    private void sendDigitalCommerceSubscriptionAck(Node node) {
        ackSender.ack(AckClass.NOTIFICATION, node).type("digital_commerce_subscription").send();
    }

    /**
     * Sends the {@code <ack class="notification" type="fb:update"/>}
     * stanza for a bot-profile update notification.
     *
     * @apiNote
     * Fire-and-forget; identical attribute set to WA Web's
     * {@code WAWebHandleBotProfileNotification} ack-builder. The type
     * attribute reflects the original notification's type rather than
     * a hard-coded string because WA Web's ack-builder reads it from
     * the stanza.
     *
     * @param node the original {@code <notification>} stanza
     */
    private void sendBotProfileAck(Node node) {
        var type = node.getAttributeAsString("type", "fb:update");
        ackSender.ack(AckClass.NOTIFICATION, node).type(type).send();
    }
}
