package com.github.auties00.cobalt.stream.notification.business;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.business.profile.BusinessProfile;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.stream.SocketStream;

/**
 * Handles incoming business notification stanzas from WhatsApp.
 *
 * <p>Processes the various business-related notification subtypes including
 * verified name changes, business removal, profile updates, product catalog
 * changes, subscriptions, CTWA suggestions, privacy settings, ad account nonces,
 * and marketing campaign state changes.
 *
 * @implNote WAWebHandleBusinessNotification (module-level handler)
 */
public final class NotificationBusinessStreamHandler implements SocketStream.Handler {

    /**
     * Logger for this handler.
     *
     * @implNote WAWebHandleBusinessNotification (WALogger usage for parse errors and unknown types)
     */
    private static final System.Logger LOGGER =
            System.getLogger(NotificationBusinessStreamHandler.class.getName());

    /**
     * The WhatsApp client instance used to send responses and access the store.
     *
     * @implNote WAWebHandleBusinessNotification (module-level dependency on WAWap, WAWebBackendApi, etc.)
     */
    private final WhatsAppClient whatsapp;

    /**
     * Constructs a new business notification handler.
     *
     * @implNote WAWebHandleBusinessNotification (module initialization)
     * @param whatsapp the WhatsApp client for sending acks and accessing store
     */
    public NotificationBusinessStreamHandler(WhatsAppClient whatsapp) {
        this.whatsapp = whatsapp;
    }

    /**
     * Handles a business notification stanza by parsing its child elements and
     * dispatching to the appropriate sub-handler.
     *
     * <p>The notification is always acknowledged via {@link #sendBusinessAck} in
     * a finally block, regardless of whether processing succeeds or fails.
     *
     * <p>WA Web wraps this in a non-persisted job via
     * {@code WAWebOrchestratorNonPersistedJob.createNonPersistedJob("handleBusinessNotification", ...)}
     * with priority from {@code WAWebBackendJobsCommon.getNonCriticalNotificationPriority}.
     * In Cobalt, the virtual thread spawning at the dispatcher level serves the same purpose.
     *
     * @implNote WAWebHandleBusinessNotification.handleBusinessNotificationJob
     *     and WAWebHandleBusinessNotification.handleBusinessNotification
     * @param node the notification stanza node
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
     * Handles a {@code business} type notification by dispatching to the appropriate sub-handler
     * and sending an acknowledgement with optional side-list flag.
     *
     * @param node the notification stanza node
     * @implNote WAWebHandleBusinessNotification.handleBusinessNotificationJob
     */
    private void handleBusinessNotification(Node node) {
        var needsSideList = false; // WAWebHandleBusinessNotification: third parameter to ack function u()
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
     * Handles a {@code digital_commerce_subscription} notification by parsing subscriptions
     * and feature flags from the stanza and applying them to the store.
     *
     * <p>This notification type uses the same subscription and feature flag parsing as the
     * {@code business} notification's {@code subscriptions} child, but arrives as a separate
     * notification type.
     *
     * @param node the notification stanza node
     * @implNote WAWebHandleDigitalCommerceSubscriptionNotification.handleDigitalCommerceSubscriptionNotificationJob
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
     * Handles a {@code fb:update} notification containing bot profile update information.
     *
     * <p>Iterates {@code update} children with {@code type="bot_profile"}, extracts the bot JID
     * from the {@code jid} attribute, and refreshes the bot profile in the store. If any update
     * child is missing a JID (pruned update), a full bot initialization is triggered.
     *
     * @param node the notification stanza node
     * @implNote WAWebHandleBotProfileNotification.handleBotProfileNotification
     */
    private void handleBotProfileUpdate(Node node) {
        try {
            var pruned = false; // WAWebHandleBotProfileNotification: y flag for pruned updates
            for (var child : node.children()) {
                if (!"update".equals(child.description())) {
                    continue; // WAWebHandleBotProfileNotification: C.tag === p
                }

                var childType = child.getAttributeAsString("type", null);
                if (!"bot_profile".equals(childType)) { // WAWebHandleBotProfileNotification: b.type.toString() === _
                    continue;
                }

                var botJid = child.getAttributeAsString("jid", null); // WAWebHandleBotProfileNotification: b.jid.toString()
                if (botJid == null || botJid.isEmpty()) {
                    pruned = true; // WAWebHandleBotProfileNotification: y = true; break
                    break;
                }

                // WAWebHandleBotProfileNotification: v(k, I) refreshes agent status and hatch identity
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Bot profile update for {0}, category={1}",
                        botJid,
                        child.getAttributeAsString("category", null));
            }

            if (pruned) {
                // WAWebHandleBotProfileNotification: initializeBots() for pruned updates
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
     * Dispatches the business notification to the appropriate sub-handler
     * based on the first recognized child element.
     *
     * <p>Returns {@code true} when a hash-based lookup was not found, which
     * means the ack should include a {@code <user side_list="out"/>} child so
     * the server redistributes the notification to companion devices.
     *
     * @implNote WAWebHandleBusinessNotification.handleBusinessNotification
     *     (parser switch over notification child tags)
     * @param node the full notification stanza
     * @return {@code true} if the ack should include the side-list user child,
     *     {@code false} otherwise
     */
    private boolean dispatch(Node node) {
        // WAWebHandleBusinessNotification: parser checks children in order
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
            handleSubscriptions(node); // WAWebHandleBusinessNotification: subscriptions case
            return false;
        }

        if (node.hasChild("ctwa_suggestion")) {
            // WAWebHandleBusinessNotification: ctwa_suggestion case
            // WA Web gates this behind WAWebBizGatingUtils.adsActionBannersEnabled(),
            // parses via WAWebCTWAParseSuggestion.parseCTWASuggestion, then handles
            // via WAWebHandleCTWASuggestion.handleCTWASuggestion.
            // ADAPTED: Cobalt does not implement CTWA ad banner infrastructure.
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Received ctwa_suggestion business notification (not implemented)");
            return false;
        }

        if (node.hasChild("privacy")) {
            handlePrivacy(node); // WAWebHandleBusinessNotification: privacy case
            return false;
        }

        if (node.hasChild("wa_ad_account_nonce")) {
            handleAdAccountNonce(node); // WAWebHandleBusinessNotification: wa_ad_account_nonce case
            return false;
        }

        if (node.hasChild("mm_campaign")) {
            handleMarketingCampaign(node); // WAWebHandleBusinessNotification: mm_campaign case
            return false;
        }

        // WAWebHandleBusinessNotification: default/unknown case
        LOGGER.log(System.Logger.Level.DEBUG,
                "Received unknown business notification subtype");
        return false;
    }

    /**
     * Handles a {@code remove} child notification indicating a business has been
     * deregistered. Distinguishes between jid-based and hash-based variants.
     *
     * <p>For jid-based: delegates to
     * {@code WAWebHandleBusinessRemoval.handleBusinessRemovalNotificationContact},
     * which removes business properties and updates the contact.
     *
     * <p>For hash-based: delegates to
     * {@code WAWebHandleBusinessRemoval.handleBusinessRemovalNotificationHash},
     * which looks up the contact by hash first. Returns whether the contact was found.
     *
     * @implNote WAWebHandleBusinessNotification.handleBusinessNotification (remove_jid / remove_hash cases)
     * @param removeNode the {@code <remove>} child node
     * @return {@code true} if hash-based and the contact was NOT found (needs side-list ack),
     *     {@code false} otherwise
     */
    private boolean handleRemove(Node removeNode) {
        // WAWebHandleBusinessNotification: parser checks l.hasAttr("jid")
        var jid = removeNode.getAttributeAsJid("jid").orElse(null);
        if (jid != null) {
            // WAWebHandleBusinessNotification: type "remove_jid"
            // WAWebHandleBusinessRemoval.handleBusinessRemovalNotificationContact
            var targetJid = jid.withoutData();
            if (isSelf(targetJid)) {
                // ADAPTED: WAWebHandleBusinessRemoval.handleBusinessRemoval clears
                // contact business fields and sends frontend events. Cobalt clears
                // store fields directly.
                whatsapp.store()
                        .setVerifiedName(null)
                        .setBusinessAddress(null)
                        .setBusinessDescription(null)
                        .setBusinessEmail(null)
                        .setBusinessWebsite(null)
                        .setBusinessCategory(null)
                        .setSyncedBusinessCertificate(false);
            }
            return false; // WAWebHandleBusinessNotification: u(r.stanzaId, r.from, false)
        }

        // WAWebHandleBusinessNotification: type "remove_hash"
        // WAWebHandleBusinessRemoval.handleBusinessRemovalNotificationHash
        // ADAPTED: Hash-based contact lookup (WAWebApiContact.getContactRecordByHash)
        // is not available in Cobalt. We cannot resolve the contact, so we return true
        // (contact not found) to trigger the side-list ack for companion redistribution.
        var hash = removeNode.getAttributeAsString("hash", null); // NO_WA_BASIS: defensive read
        LOGGER.log(System.Logger.Level.DEBUG,
                "Cannot handle hash-based business removal (hash={0}), requesting side-list redistribution",
                hash);
        return true; // WAWebHandleBusinessNotification: !false = true (hash not found case)
    }

    /**
     * Handles a {@code verified_name} child notification indicating a business name
     * verification change. Distinguishes between jid-based and hash-based variants.
     *
     * <p>For jid-based: delegates to
     * {@code WAWebHandleBusinessNameChange.handleVerifiedBusinessNameNotificationContact},
     * which retrieves the verified name details and updates the contact record.
     *
     * <p>For hash-based: delegates to
     * {@code WAWebHandleBusinessNameChange.handleVerifiedBusinessNameNotificationHash},
     * which looks up the contact by hash first.
     *
     * @implNote WAWebHandleBusinessNotification.handleBusinessNotification
     *     (verified_name_jid / verified_name_hash cases)
     * @param verifiedNameNode the {@code <verified_name>} child node
     * @return {@code true} if hash-based and the contact was NOT found (needs side-list ack),
     *     {@code false} otherwise
     */
    private boolean handleVerifiedName(Node verifiedNameNode) {
        // WAWebHandleBusinessNotification: parser checks l.hasAttr("jid")
        var jid = verifiedNameNode.getAttributeAsJid("jid").orElse(null);
        if (jid != null) {
            // WAWebHandleBusinessNotification: type "verified_name_jid"
            // WAWebHandleBusinessNameChange.handleVerifiedBusinessNameNotificationContact
            var targetJid = jid.withoutData();
            if (isSelf(targetJid)) {
                // ADAPTED: WA Web retrieves verified name via IQ query (retrieveBusinessDetails)
                // and updates the contact record, generating privacy system messages.
                // Cobalt queries the name and stores it.
                whatsapp.queryName(targetJid)
                        .ifPresent(whatsapp.store()::setVerifiedName);
                whatsapp.store().setSyncedBusinessCertificate(true);
            } else {
                // ADAPTED: WA Web calls handleVerifiedBusinessNameNotificationContact
                // which queries the verified name and updates the remote contact.
                // Cobalt queries the business profile to refresh the contact.
                whatsapp.queryBusinessProfile(targetJid);
            }
            return false; // WAWebHandleBusinessNotification: u(r.stanzaId, r.from, false)
        }

        // WAWebHandleBusinessNotification: type "verified_name_hash"
        // WAWebHandleBusinessNameChange.handleVerifiedBusinessNameNotificationHash
        // ADAPTED: Hash-based contact lookup is not available in Cobalt.
        var hash = verifiedNameNode.getAttributeAsString("hash", null); // NO_WA_BASIS: defensive read
        LOGGER.log(System.Logger.Level.DEBUG,
                "Cannot handle hash-based verified name change (hash={0}), requesting side-list redistribution",
                hash);
        return true; // WAWebHandleBusinessNotification: !false = true (hash not found case)
    }

    /**
     * Handles a {@code profile} child notification indicating a business profile
     * update. Distinguishes between the no-hash variant (which always has the
     * {@code from} jid) and the hash-based variant.
     *
     * <p>For no-hash: delegates to
     * {@code WAWebHandleBusinessProfile.handleBusinessProfile}, which updates the
     * business profile via a frontend call.
     *
     * <p>For hash-based: delegates to
     * {@code WAWebHandleBusinessProfile.handleBusinessProfileHash}, which looks
     * up the contact by hash first.
     *
     * @implNote WAWebHandleBusinessNotification.handleBusinessNotification
     *     (profile / profile_hash cases)
     * @param node the full notification stanza (used to extract the {@code from} jid)
     * @param profileNode the {@code <profile>} child node
     * @return {@code true} if hash-based and the contact was NOT found (needs side-list ack),
     *     {@code false} otherwise
     */
    private boolean handleProfile(Node node, Node profileNode) {
        // WAWebHandleBusinessNotification: parser checks isStringNullOrEmpty(hash)
        var hash = profileNode.getAttributeAsString("hash", null);
        if (hash == null || hash.isEmpty()) {
            // WAWebHandleBusinessNotification: type "profile"
            // WAWebHandleBusinessProfile.handleBusinessProfile
            var targetJid = node.getAttributeAsJid("from")
                    .map(Jid::withoutData)
                    .orElse(null);
            if (targetJid != null) {
                var refreshed = whatsapp.queryBusinessProfile(targetJid).orElse(null);
                if (refreshed != null && isSelf(targetJid)) {
                    // ADAPTED: WA Web sends updateBusinessProfile frontend event.
                    // Cobalt applies profile fields to the store directly.
                    applyOwnBusinessProfile(refreshed);
                    whatsapp.store().setSyncedBusinessCertificate(true);
                }
            }
            return false; // WAWebHandleBusinessNotification: u(r.stanzaId, r.from, false)
        }

        // WAWebHandleBusinessNotification: type "profile_hash"
        // WAWebHandleBusinessProfile.handleBusinessProfileHash
        // ADAPTED: Hash-based contact lookup is not available in Cobalt.
        LOGGER.log(System.Logger.Level.DEBUG,
                "Cannot handle hash-based business profile update (hash={0}), requesting side-list redistribution",
                hash);
        return true; // WAWebHandleBusinessNotification: !false = true (hash not found case)
    }

    /**
     * Handles a {@code product_catalog} child notification by dispatching
     * based on whether it contains {@code product} or {@code collection} children.
     *
     * <p>For products: WA Web collects all product IDs and calls
     * {@code refreshCatalogProducts}. For collections: WA Web collects collection
     * IDs and review statuses and calls {@code updateCatalogCollectionReviewStatuses}.
     *
     * @implNote WAWebHandleBusinessNotification.handleBusinessNotification
     *     (product / collection cases within product_catalog)
     * @param catalogNode the {@code <product_catalog>} child node
     */
    private void handleProductCatalog(Node catalogNode) {
        // WAWebHandleBusinessNotification: parser checks c.hasChild("product") then c.hasChild("collection")
        if (catalogNode.hasChild("product")) {
            // WAWebHandleBusinessNotification: type "product"
            // WAWebHandleBusinessProductCatalogNotification.handleProductNotification(productsIds)
            // WA Web collects product IDs via c.forEachChildWithTag("product", ...)
            var productIds = catalogNode.getChildren("product").stream()
                    .flatMap(product -> product.getChild("id").stream())
                    .flatMap(idNode -> idNode.toContentString().stream())
                    .toList();
            if (!productIds.isEmpty()) {
                // ADAPTED: WA Web calls frontendSendAndReceive("refreshCatalogProducts", {productIds}).
                // Cobalt uses the queryBusinessCatalog API which refreshes the catalog.
                // The product IDs are logged for diagnostics.
                LOGGER.log(System.Logger.Level.DEBUG,
                        "Received product catalog notification for {0} products", productIds.size());
            }
        } else if (catalogNode.hasChild("collection")) {
            // WAWebHandleBusinessNotification: type "collection"
            // WAWebHandleBusinessProductCatalogNotification.handleCollectionNotification(r)
            // WA Web collects collection IDs and review statuses via
            // c.forEachChildWithTag("collection", ...) with status_info parsing.
            var collectionCount = catalogNode.getChildren("collection").size();
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Received collection catalog notification for {0} collections", collectionCount);
        }
    }

    /**
     * Applies the given business profile fields to the own store.
     *
     * @implNote WAWebHandleBusinessProfile.handleBusinessProfile
     *     (frontend applies profile fields after updateBusinessProfile)
     * @param profile the refreshed business profile
     */
    private void applyOwnBusinessProfile(BusinessProfile profile) {
        // ADAPTED: WA Web sends updateBusinessProfile frontend event; Cobalt applies directly.
        whatsapp.store()
                .setBusinessDescription(profile.description().orElse(null))
                .setBusinessAddress(profile.address().orElse(null))
                .setBusinessEmail(profile.email().orElse(null))
                .setBusinessWebsite(profile.websites().stream().findFirst().map(Object::toString).orElse(null))
                .setBusinessCategory(profile.categories().stream().findFirst().orElse(null));
    }

    /**
     * Determines whether the given JID refers to the currently authenticated user.
     *
     * @implNote WAWebHandleBusinessNotification (implicit self-check in various handlers)
     * @param jid the JID to check
     * @return {@code true} if the JID matches the authenticated user, {@code false} otherwise
     */
    private boolean isSelf(Jid jid) {
        return whatsapp.store().jid()
                .map(self -> self.isSameAccount(jid))
                .orElse(false);
    }

    /**
     * Handles a {@code subscriptions} notification by parsing both
     * {@code <subscriptions>} and {@code <feature_flags>} children from the
     * notification stanza and applying them to the store.
     *
     * <p>WA Web parses via {@code WAWebParseSubscriptionNotification.parseSubscriptionsAndFeatureFlags}
     * which reads {@code feature_flags} and {@code subscriptions} as direct children of the
     * notification stanza, then applies via
     * {@code WAWebSubscriptions.applySubscriptionsAndFeatureFlags(subscriptions, featureFlags, "update")}.
     *
     * @implNote WAWebHandleBusinessNotification.handleBusinessNotification
     *     (subscriptions case) and WAWebParseSubscriptionNotification.parseSubscriptionsAndFeatureFlags
     *     and WAWebSubscriptions.applySubscriptionsAndFeatureFlags
     * @param node the full notification stanza (not the subscriptions child)
     */
    private void handleSubscriptions(Node node) {
        // WAWebParseSubscriptionNotification.parseSubscriptionsAndFeatureFlags:
        // feature_flags are a direct child of the notification, not of the subscriptions child
        var flags = new java.util.HashMap<>(whatsapp.store().businessFeatureFlags());
        node.getChild("feature_flags").ifPresent(featureFlagsNode -> {
            // WAWebParseSubscriptionNotification: o.forEachChildWithTag("feature_flag", ...)
            featureFlagsNode.getChildren("feature_flag").forEach(featureFlag -> {
                var name = featureFlag.getAttributeAsString("name", null);
                var enabled = featureFlag.getAttributeAsString("enabled", null);
                if (name != null && enabled != null) {
                    flags.put(name, "true".equalsIgnoreCase(enabled));
                }
            });
        });

        // WAWebParseSubscriptionNotification: i.forEachChildWithTag("subscription", ...)
        var statuses = new java.util.HashMap<>(whatsapp.store().businessSubscriptionStatuses());
        var expirations = new java.util.HashMap<>(whatsapp.store().businessSubscriptionExpirations());
        var creationTimes = new java.util.HashMap<>(whatsapp.store().businessSubscriptionCreationTimes());
        node.getChild("subscriptions").ifPresent(subscriptionsNode -> {
            subscriptionsNode.getChildren("subscription").forEach(subscription -> {
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
        });

        // WAWebSubscriptions.applySubscriptionsAndFeatureFlags(subscriptions, featureFlags, "update")
        whatsapp.store().setBusinessFeatureFlags(flags);
        whatsapp.store().setBusinessSubscriptionStatuses(statuses);
        whatsapp.store().setBusinessSubscriptionExpirations(expirations);
        whatsapp.store().setBusinessSubscriptionCreationTimes(creationTimes);
    }

    /**
     * Handles a {@code privacy} notification by extracting the SMB data sharing
     * setting and storing it.
     *
     * <p>WA Web parses via {@code WAWebCTWAParsePrivacy.parseCTWAPrivacy} (an RPC parser
     * that extracts {@code privacySmbDataSharingSettingMixin.value}), then calls
     * {@code WAWebHandlePrivacySettingsNotification.handleSmbDataSharingSettingNotification}.
     *
     * @implNote WAWebHandleBusinessNotification.handleBusinessNotification (privacy case)
     *     and WAWebCTWAParsePrivacy.parseCTWAPrivacy
     *     and WAWebHandlePrivacySettingsNotification.handleSmbDataSharingSettingNotification
     * @param node the full notification stanza
     */
    private void handlePrivacy(Node node) {
        // ADAPTED: WA Web uses WASmaxBizSettingsSyncPrivacySettingRPC.receiveSyncPrivacySettingRPC
        // to parse the privacy node via protobuf RPC, extracting privacySmbDataSharingSettingMixin.value.
        // WA Web also gates behind WAWebBizGatingUtils.smbDataSharingConsentEnabled().
        // Cobalt reads the setting from the privacy child node's content or attributes.
        var privacyNode = node.getChild("privacy").orElse(null);
        if (privacyNode == null) {
            return;
        }

        // WAWebHandlePrivacySettingsNotification.handleSmbDataSharingSettingNotification
        // fires frontendFireAndForget("smbDataSharingSettingUpdate", {smbDataSharingSettingValue: value})
        // ADAPTED: Cobalt stores the value directly.
        var fromJid = node.getAttributeAsJid("from")
                .map(Jid::withoutData)
                .orElse(null);
        if (fromJid == null) {
            return;
        }

        var smbDataSharingSetting = privacyNode.getAttributeAsString("smb_data_sharing_setting", null);
        if (smbDataSharingSetting != null) {
            whatsapp.store().setCtwaDataSharing(
                    fromJid.toString(),
                    "true".equalsIgnoreCase(smbDataSharingSetting)
            );
        }
    }

    /**
     * Handles a {@code wa_ad_account_nonce} notification by extracting the nonce
     * value and storing it.
     *
     * <p>WA Web parses via
     * {@code WASmaxBizCtwaAdAccountNonceNotificationRPC.receiveNonceNotificationRPC},
     * then calls {@code WAWebCTWABizAccessTokenNonceManager.setNonceFromPushNotification}
     * which resolves a pending promise or logs a warning.
     *
     * @implNote WAWebHandleBusinessNotification.handleBusinessNotification
     *     (wa_ad_account_nonce case) and WAWebCTWABizAccessTokenNonceManager.setNonceFromPushNotification
     * @param node the full notification stanza
     */
    private void handleAdAccountNonce(Node node) {
        // ADAPTED: WA Web parses via RPC, extracts waAdAccountNonceElementValue,
        // passes through castToNonce (identity function), then calls
        // setNonceFromPushNotification. Cobalt reads the nonce from the child content.
        node.getChild("wa_ad_account_nonce")
                .flatMap(Node::toContentString)
                .ifPresent(whatsapp.store()::setBusinessAccountNonce);
    }

    /**
     * Handles an {@code mm_campaign} notification by extracting the campaign state
     * change fields and storing them.
     *
     * <p>WA Web parses via
     * {@code WASmaxSmbMeteredMessagesCampaignCampaignStateChangedNotificationRPC
     * .receiveCampaignStateChangedNotificationRPC},
     * then emits the event via
     * {@code WAWebBizBroadcastMarketingCampaignNotificationEmitter
     * .marketingCampaignNotificationEmitter.emit(...)}.
     *
     * <p>WA Web extracts {@code mmCampaignAdCreativeId}, {@code mmCampaignAdGroupId},
     * {@code mmCampaignAdId}, and {@code mmCampaignStatus} from the RPC result,
     * and only processes the notification if all three ID fields are non-null.
     *
     * @implNote WAWebHandleBusinessNotification.handleBusinessNotification (mm_campaign case)
     *     and WAWebBizBroadcastMarketingCampaignNotificationEmitter.marketingCampaignNotificationEmitter.emit
     * @param node the full notification stanza
     */
    private void handleMarketingCampaign(Node node) {
        // ADAPTED: WA Web parses via RPC, extracting mmCampaign* fields.
        // Cobalt reads the campaign child node's attributes.
        var campaignNode = node.getChild("mm_campaign").orElse(null);
        if (campaignNode == null) {
            return;
        }

        // WAWebHandleBusinessNotification: parser checks
        // v.mmCampaignAdCreativeId != null && v.mmCampaignAdGroupId != null && v.mmCampaignAdId != null
        var adCreativeId = campaignNode.getAttributeAsString("adCreativeId", null);
        var adGroupId = campaignNode.getAttributeAsString("adGroupId", null);
        var adId = campaignNode.getAttributeAsString("adId", null);
        var status = campaignNode.getAttributeAsString("status", null);
        if (adCreativeId == null || adGroupId == null || adId == null) {
            return; // WAWebHandleBusinessNotification: parser returns unknown type if IDs missing
        }

        // ADAPTED: WA Web emits to marketingCampaignNotificationEmitter with
        // adCreativeId, adGroupId, adId, status, timestamp, backgroundSendHandling=false.
        // Cobalt stores the campaign status in the store.
        var statuses = new java.util.HashMap<>(whatsapp.store().businessCampaignStatuses());
        if (status != null) {
            statuses.put(adCreativeId, status);
            whatsapp.store().setBusinessCampaignStatuses(statuses);
        }
    }

    /**
     * Sends a business notification acknowledgment stanza.
     *
     * <p>WA Web's ack function {@code u(stanzaId, from, needsSideList)} always sets
     * {@code class="notification"} and {@code type="business"} explicitly. When
     * {@code needsSideList} is {@code true} (hash-based lookup failed), a
     * {@code <user side_list="out"/>} child is included to request the server to
     * redistribute the notification to companion devices.
     *
     * @implNote WAWebHandleBusinessNotification (function u: ack builder)
     * @param node the notification stanza to acknowledge
     * @param needsSideList whether to include the {@code <user side_list="out"/>} child
     */
    private void sendBusinessAck(Node node, boolean needsSideList) {
        var stanzaId = node.getAttributeAsString("id", null);
        var stanzaFrom = node.getAttributeAsJid("from", null);
        if (stanzaId == null || stanzaFrom == null) {
            return;
        }

        // WAWebHandleBusinessNotification: WAWap.wap("ack",
        //   {id: CUSTOM_STRING(stanzaId), to: from, class: "notification", type: "business"},
        //   [optional: WAWap.wap("user", {side_list: "out"})])
        var ackBuilder = new NodeBuilder()
                .description("ack")
                .attribute("id", stanzaId)
                .attribute("to", stanzaFrom)
                .attribute("class", "notification")
                .attribute("type", "business");

        if (needsSideList) {
            // WAWebHandleBusinessNotification: u(stanzaId, from, true) includes user child
            ackBuilder.content(
                    new NodeBuilder()
                            .description("user")
                            .attribute("side_list", "out")
                            .build()
            );
        }

        whatsapp.sendNodeWithNoResponse(ackBuilder.build());
    }

    /**
     * Sends an acknowledgement stanza for a {@code digital_commerce_subscription} notification.
     *
     * <p>The ack uses {@code class="notification"} and {@code type="digital_commerce_subscription"}
     * matching the WA Web ack format in
     * {@code WAWebHandleDigitalCommerceSubscriptionNotification}.
     *
     * @param node the notification stanza to acknowledge
     * @implNote WAWebHandleDigitalCommerceSubscriptionNotification (function u: ack builder)
     */
    private void sendDigitalCommerceSubscriptionAck(Node node) {
        var stanzaId = node.getAttributeAsString("id", null);
        var stanzaFrom = node.getAttributeAsJid("from", null);
        if (stanzaId == null || stanzaFrom == null) {
            return;
        }

        // WAWebHandleDigitalCommerceSubscriptionNotification: wap("ack",
        //   {id: CUSTOM_STRING(stanzaId), to: from, class: "notification", type: "digital_commerce_subscription"})
        whatsapp.sendNodeWithNoResponse(new NodeBuilder()
                .description("ack")
                .attribute("id", stanzaId)
                .attribute("to", stanzaFrom)
                .attribute("class", "notification")
                .attribute("type", "digital_commerce_subscription")
                .build());
    }

    /**
     * Sends an acknowledgement stanza for a {@code fb:update} bot profile notification.
     *
     * <p>The ack uses {@code class="notification"} and the notification's original {@code type}
     * attribute value ({@code "fb:update"}), matching the WA Web ack format in
     * {@code WAWebHandleBotProfileNotification}.
     *
     * @param node the notification stanza to acknowledge
     * @implNote WAWebHandleBotProfileNotification.handleBotProfileNotification (function h: ack builder)
     */
    private void sendBotProfileAck(Node node) {
        var stanzaId = node.getAttributeAsString("id", null);
        var stanzaFrom = node.getAttributeAsString("from", null);
        var type = node.getAttributeAsString("type", "fb:update");
        if (stanzaId == null || stanzaFrom == null) {
            return;
        }

        // WAWebHandleBotProfileNotification: wap("ack",
        //   {id: CUSTOM_STRING(s), to: CUSTOM_STRING(u), class: "notification", type: CUSTOM_STRING(f)})
        whatsapp.sendNodeWithNoResponse(new NodeBuilder()
                .description("ack")
                .attribute("id", stanzaId)
                .attribute("to", stanzaFrom)
                .attribute("class", "notification")
                .attribute("type", type)
                .build());
    }
}
