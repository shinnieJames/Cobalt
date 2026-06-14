package com.github.auties00.cobalt.client.cloud;
import com.github.auties00.cobalt.client.WhatsAppClientDisconnectReason;
import com.github.auties00.cobalt.client.WhatsAppClientErrorHandler;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.cloud.CloudApiClient;
import com.github.auties00.cobalt.cloud.CloudMessageEncoder;
import com.github.auties00.cobalt.store.CloudWhatsAppStore;
import com.github.auties00.cobalt.cloud.CloudWebhookDecoder;
import com.github.auties00.cobalt.cloud.CloudWebhookServer;
import com.github.auties00.cobalt.listener.cloud.CloudAccountUpdateListener;
import com.github.auties00.cobalt.listener.cloud.CloudBusinessCapabilityListener;
import com.github.auties00.cobalt.listener.cloud.CloudCallListener;
import com.github.auties00.cobalt.listener.LoggedInListener;
import com.github.auties00.cobalt.listener.DisconnectedListener;
import com.github.auties00.cobalt.listener.cloud.CloudErrorListener;
import com.github.auties00.cobalt.listener.cloud.CloudFlowListener;
import com.github.auties00.cobalt.listener.cloud.CloudHistoryListener;
import com.github.auties00.cobalt.listener.cloud.CloudMessageEchoListener;
import com.github.auties00.cobalt.listener.MessageStatusListener;
import com.github.auties00.cobalt.listener.NewMessageListener;
import com.github.auties00.cobalt.listener.cloud.CloudWebhookReceivedListener;
import com.github.auties00.cobalt.listener.cloud.CloudPhoneNumberListener;
import com.github.auties00.cobalt.listener.cloud.CloudTemplateCategoryListener;
import com.github.auties00.cobalt.listener.cloud.CloudTemplateQualityListener;
import com.github.auties00.cobalt.listener.cloud.CloudTemplateStatusListener;
import com.github.auties00.cobalt.listener.cloud.CloudUserPreferenceListener;
import com.github.auties00.cobalt.listener.cloud.CloudListener;
import com.github.auties00.cobalt.listener.MessageDeletedListener;
import com.github.auties00.cobalt.listener.WhatsAppListener;
import com.github.auties00.cobalt.model.business.profile.BusinessProfile;
import com.github.auties00.cobalt.model.business.profile.BusinessProfileBuilder;
import com.github.auties00.cobalt.model.cloud.CloudCallEvent;
import com.github.auties00.cobalt.model.cloud.CloudFlow;
import com.github.auties00.cobalt.model.cloud.CloudMessageQr;
import com.github.auties00.cobalt.model.cloud.CloudMessageTemplate;
import com.github.auties00.cobalt.model.cloud.CloudPhoneNumber;
import com.github.auties00.cobalt.model.cloud.CloudRegistrationResult;
import com.github.auties00.cobalt.model.cloud.CloudVerificationMethod;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidProvider;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageInfo;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.MessageKeyBuilder;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Production implementation of {@link CloudWhatsAppClient}.
 *
 * <p>This class wires the Cloud transport ({@link CloudApiClient}), the message encoder/decoder
 * ({@link CloudMessageEncoder}, {@link CloudWebhookDecoder}), the built-in webhook receiver
 * ({@link CloudWebhookServer}), a listener registry, and the configurable error handler. Outbound
 * operations translate to graph requests; inbound webhook deliveries are decoded and dispatched to the
 * registered listeners.
 */
public final class LiveCloudWhatsAppClient implements CloudWhatsAppClient {
    /**
     * The credential and webhook configuration.
     */
    private final CloudWhatsAppStore store;

    /**
     * The configurable error handler.
     */
    private final WhatsAppClientErrorHandler errorHandler;

    /**
     * The HTTP/JSON transport.
     */
    private final CloudApiClient api;

    /**
     * The registered listeners.
     */
    private final CopyOnWriteArraySet<WhatsAppListener> listeners;

    /**
     * The last inbound message id seen per chat, used to adapt {@link #markChatAsRead(JidProvider)}.
     */
    private final Map<Jid, String> lastInboundByChat;

    /**
     * Whether the client is currently connected.
     */
    private final AtomicBoolean connected;

    /**
     * The webhook receiver, or {@code null} when the receiver is disabled.
     */
    private volatile CloudWebhookServer webhookServer;

    /**
     * The latch released on disconnect, used by {@link #waitForDisconnection()}.
     */
    private volatile CountDownLatch disconnectLatch;

    /**
     * Constructs a new live Cloud client.
     *
     * @param store      the credential and webhook configuration
     * @param errorHandler the configurable error handler
     * @param httpClient   the HTTP client backing the transport
     * @throws NullPointerException if any argument is {@code null}
     */
    LiveCloudWhatsAppClient(CloudWhatsAppStore store, WhatsAppClientErrorHandler errorHandler,
                            java.net.http.HttpClient httpClient) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler must not be null");
        this.api = new CloudApiClient(httpClient, store.accessToken(), store.apiVersion(),
                store.appSecret().orElse(null));
        this.listeners = new CopyOnWriteArraySet<>();
        this.lastInboundByChat = new ConcurrentHashMap<>();
        this.connected = new AtomicBoolean();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudWhatsAppClient connect() {
        if (!connected.compareAndSet(false, true)) {
            throw new IllegalStateException("client already connected");
        }
        api.get(store.phoneNumberId(), Map.of("fields", "id"));
        disconnectLatch = new CountDownLatch(1);
        if (store.hasWebhookReceiver()) {
            var server = new CloudWebhookServer(
                    store.webhookBindAddress().orElse(null),
                    store.webhookPort().orElseThrow(),
                    store.webhookPath(),
                    store.webhookVerifyToken().orElseThrow(),
                    store.appSecret().orElse(null),
                    this::dispatchEnvelope,
                    this::fireError);
            server.start();
            this.webhookServer = server;
        }
        forEach(LoggedInListener.class, listener -> listener.onLoggedIn(this));
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disconnect() {
        disconnect(WhatsAppClientDisconnectReason.DISCONNECTED);
    }

    /**
     * Tears the client down, notifying the disconnect listeners with the given reason.
     *
     * @param reason the reason surfaced to the disconnect listeners
     */
    private void disconnect(WhatsAppClientDisconnectReason reason) {
        if (!connected.compareAndSet(true, false)) {
            return;
        }
        var server = webhookServer;
        if (server != null) {
            server.stop();
            webhookServer = null;
        }
        forEach(DisconnectedListener.class, listener -> listener.onDisconnected(this, reason));
        var latch = disconnectLatch;
        if (latch != null) {
            latch.countDown();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudWhatsAppClient reconnect() {
        disconnect(WhatsAppClientDisconnectReason.RECONNECTING);
        connect();
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isConnected() {
        return connected.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudWhatsAppClient waitForDisconnection() {
        var latch = disconnectLatch;
        if (latch != null) {
            try {
                latch.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudWhatsAppClient addListener(CloudListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener must not be null"));
        return this;
    }

    /**
     * Registers a transport-agnostic listener.
     *
     * @param listener the listener to register
     * @return {@code this}, for fluent chaining
     */
    private CloudWhatsAppClient addShared(WhatsAppListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener must not be null"));
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudWhatsAppClient removeListener(WhatsAppListener listener) {
        listeners.remove(listener);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MessageKey sendMessage(JidProvider recipient, MessageContainer message) {
        var body = CloudMessageEncoder.encode(recipient, message);
        var response = api.post(store.phoneNumberId() + "/messages", body);
        return new MessageKeyBuilder()
                .id(firstMessageId(response))
                .parentJid(recipient.toJid())
                .fromMe(true)
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendMessage(MessageInfo messageInfo) {
        var recipient = messageInfo.key().parentJid()
                .orElseThrow(() -> new IllegalArgumentException("messageInfo key must carry a parentJid"));
        sendMessage(recipient, messageInfo.message());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addReaction(MessageKey messageKey, String emoji) {
        sendReaction(messageKey, emoji);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeReaction(MessageKey messageKey) {
        sendReaction(messageKey, "");
    }

    /**
     * Posts a reaction message for the given target with the given emoji.
     *
     * @param messageKey the key of the message to react to
     * @param emoji      the reaction emoji, or the empty string to clear it
     */
    private void sendReaction(MessageKey messageKey, String emoji) {
        var recipient = messageKey.parentJid()
                .orElseThrow(() -> new IllegalArgumentException("messageKey must carry a parentJid"));
        var body = new JSONObject();
        body.put("messaging_product", "whatsapp");
        body.put("recipient_type", "individual");
        body.put("to", recipient.user());
        body.put("type", "reaction");
        var reaction = new JSONObject();
        reaction.put("message_id", messageKey.id().orElseThrow(
                () -> new IllegalArgumentException("messageKey must carry an id")));
        reaction.put("emoji", emoji);
        body.put("reaction", reaction);
        api.post(store.phoneNumberId() + "/messages", body);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void markChatAsRead(JidProvider chat) {
        // TODO: the chat-to-last-inbound-message mapping is in-memory only, so chats whose last
        // inbound message arrived before this process started are silently skipped; prefer
        // markMessageAsRead when the inbound message key is available.
        var lastInbound = lastInboundByChat.get(chat.toJid());
        if (lastInbound == null) {
            return;
        }
        postRead(lastInbound, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void markMessageAsRead(MessageKey messageKey) {
        var id = messageKey.id().orElseThrow(
                () -> new IllegalArgumentException("messageKey must carry an id"));
        postRead(id, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void sendTypingIndicator(MessageKey inboundMessage) {
        var id = inboundMessage.id().orElseThrow(
                () -> new IllegalArgumentException("inboundMessage must carry an id"));
        postRead(id, "text");
    }

    /**
     * Posts a {@code status: read} update for a message, optionally attaching a typing indicator.
     *
     * <p>The Cloud API couples the two concerns: a typing-indicator request is a read update with a
     * {@code typing_indicator} attachment, so requesting the indicator also marks the message read.
     *
     * @param messageId     the inbound message id
     * @param indicatorType the typing indicator type, or {@code null} to send a plain read update
     */
    private void postRead(String messageId, String indicatorType) {
        var body = new JSONObject();
        body.put("messaging_product", "whatsapp");
        body.put("status", "read");
        body.put("message_id", messageId);
        if (indicatorType != null) {
            var indicator = new JSONObject();
            indicator.put("type", indicatorType);
            body.put("typing_indicator", indicator);
        }
        api.post(store.phoneNumberId() + "/messages", body);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String uploadMedia(byte[] data, String mimeType, String filename) {
        var response = api.uploadMedia(store.phoneNumberId(), data, mimeType, filename);
        return response.getString("id");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String uploadMedia(Path file, String mimeType) {
        try {
            var data = Files.readAllBytes(file);
            return uploadMedia(data, mimeType, file.getFileName().toString());
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("failed to read media file: " + file, exception);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String uploadMedia(Path file) {
        String mimeType;
        try {
            mimeType = Files.probeContentType(file);
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("failed to probe the MIME type of " + file, exception);
        }
        if (mimeType == null) {
            throw new IllegalArgumentException("cannot determine the MIME type of " + file);
        }
        return uploadMedia(file, mimeType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] downloadMedia(String mediaId) {
        return api.download(queryMediaUrl(mediaId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public URI queryMediaUrl(String mediaId) {
        var response = api.get(mediaId, Map.of());
        return URI.create(response.getString("url"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteMedia(String mediaId) {
        api.delete(mediaId, Map.of());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<BusinessProfile> queryBusinessProfile() {
        var response = api.get(store.phoneNumberId() + "/whatsapp_business_profile",
                Map.of("fields", "about,address,description,email,websites,vertical,profile_picture_url"));
        var data = response.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(parseBusinessProfile(data.getJSONObject(0)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void editBusinessProfile(BusinessProfile profile) {
        var body = new JSONObject();
        body.put("messaging_product", "whatsapp");
        profile.description().ifPresent(value -> body.put("description", value));
        profile.address().ifPresent(value -> body.put("address", value));
        profile.email().ifPresent(value -> body.put("email", value));
        if (!profile.websites().isEmpty()) {
            var websites = new JSONArray();
            for (var website : profile.websites()) {
                websites.add(website.toString());
            }
            body.put("websites", websites);
        }
        api.post(store.phoneNumberId() + "/whatsapp_business_profile", body);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void blockContact(JidProvider contact) {
        api.post(store.phoneNumberId() + "/block_users", blockUsersBody(contact));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unblockContact(JidProvider contact) {
        api.delete(store.phoneNumberId() + "/block_users", blockUsersBody(contact));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Jid> queryBlockedContacts() {
        var response = api.get(store.phoneNumberId() + "/block_users", Map.of());
        var data = response.getJSONArray("data");
        var result = new ArrayList<Jid>();
        if (data != null) {
            for (var index = 0; index < data.size(); index++) {
                var user = data.getJSONObject(index).getString("wa_id");
                if (user != null) {
                    result.add(Jid.of(user, JidServer.user()));
                }
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudMessageTemplate createMessageTemplate(CloudMessageTemplate template) {
        var body = new JSONObject();
        body.put("name", template.name());
        body.put("language", template.language());
        body.put("category", template.category());
        template.components().ifPresent(components -> body.put("components", components));
        var response = api.post(requireWaba() + "/message_templates", body);
        return new CloudMessageTemplate(response.getString("id"), template.name(), template.language(),
                template.category(), response.getString("status"), template.components().orElse(null));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CloudMessageTemplate> queryMessageTemplates() {
        return queryMessageTemplates(Map.of());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CloudMessageTemplate> queryMessageTemplates(int limit) {
        return queryMessageTemplates(Map.of("limit", String.valueOf(limit)));
    }

    /**
     * Lists the message templates of the WhatsApp Business Account with the given query parameters.
     *
     * @param parameters the query parameters
     * @return the message templates
     */
    private List<CloudMessageTemplate> queryMessageTemplates(Map<String, String> parameters) {
        var response = api.get(requireWaba() + "/message_templates", parameters);
        var data = response.getJSONArray("data");
        var result = new ArrayList<CloudMessageTemplate>();
        if (data != null) {
            for (var index = 0; index < data.size(); index++) {
                result.add(parseTemplate(data.getJSONObject(index)));
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<CloudMessageTemplate> queryMessageTemplate(String name) {
        var response = api.get(requireWaba() + "/message_templates", Map.of("name", name));
        var data = response.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(parseTemplate(data.getJSONObject(0)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void editMessageTemplate(CloudMessageTemplate template) {
        var templateId = template.id().orElseThrow(
                () -> new IllegalArgumentException("template must carry an id"));
        var body = new JSONObject();
        body.put("category", template.category());
        template.components().ifPresent(components -> body.put("components", components));
        api.post(templateId, body);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteMessageTemplate(String name) {
        api.delete(requireWaba() + "/message_templates", Map.of("name", name));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudRegistrationResult registerPhoneNumber(String pin) {
        var body = new JSONObject();
        body.put("messaging_product", "whatsapp");
        body.put("pin", pin);
        var response = api.post(store.phoneNumberId() + "/register", body);
        return new CloudRegistrationResult(!response.containsKey("success") || response.getBooleanValue("success"), null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deregisterPhoneNumber() {
        api.post(store.phoneNumberId() + "/deregister", new JSONObject());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void requestVerificationCode(CloudVerificationMethod method, String language) {
        api.postForm(store.phoneNumberId() + "/request_code",
                Map.of("code_method", method.name(), "language", language));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void verifyCode(String code) {
        api.postForm(store.phoneNumberId() + "/verify_code", Map.of("code", code));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void editTwoStepPin(String pin) {
        var body = new JSONObject();
        body.put("pin", pin);
        api.post(store.phoneNumberId(), body);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudPhoneNumber queryPhoneNumber() {
        var response = api.get(store.phoneNumberId(),
                Map.of("fields", "id,display_phone_number,verified_name,quality_rating,code_verification_status,status"));
        return parsePhoneNumber(response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CloudPhoneNumber> queryPhoneNumbers() {
        return queryPhoneNumbers(Map.of());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CloudPhoneNumber> queryPhoneNumbers(int limit) {
        return queryPhoneNumbers(Map.of("limit", String.valueOf(limit)));
    }

    /**
     * Lists the phone numbers of the WhatsApp Business Account with the given query parameters.
     *
     * @param parameters the query parameters
     * @return the phone numbers
     */
    private List<CloudPhoneNumber> queryPhoneNumbers(Map<String, String> parameters) {
        var response = api.get(requireWaba() + "/phone_numbers", parameters);
        var data = response.getJSONArray("data");
        var result = new ArrayList<CloudPhoneNumber>();
        if (data != null) {
            for (var index = 0; index < data.size(); index++) {
                result.add(parsePhoneNumber(data.getJSONObject(index)));
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void enableCalling() {
        applyCallingStatus("enabled");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void disableCalling() {
        applyCallingStatus("disabled");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void subscribeApp() {
        api.post(requireWaba() + "/subscribed_apps", new JSONObject());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> querySubscribedApps() {
        var response = api.get(requireWaba() + "/subscribed_apps", Map.of());
        var data = response.getJSONArray("data");
        var result = new ArrayList<String>();
        if (data != null) {
            for (var index = 0; index < data.size(); index++) {
                var app = data.getJSONObject(index).getJSONObject("whatsapp_business_api_data");
                if (app != null) {
                    result.add(app.getString("id"));
                }
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unsubscribeApp() {
        api.delete(requireWaba() + "/subscribed_apps", Map.of());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudFlow createFlow(CloudFlow flow) {
        var body = new JSONObject();
        body.put("name", flow.name());
        if (!flow.categories().isEmpty()) {
            var categories = new JSONArray();
            categories.addAll(flow.categories());
            body.put("categories", categories);
        }
        var response = api.post(requireWaba() + "/flows", body);
        return new CloudFlow(response.getString("id"), flow.name(), "DRAFT", flow.categories());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CloudFlow> queryFlows() {
        return queryFlows(Map.of());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CloudFlow> queryFlows(int limit) {
        return queryFlows(Map.of("limit", String.valueOf(limit)));
    }

    /**
     * Lists the Flows of the WhatsApp Business Account with the given query parameters.
     *
     * @param parameters the query parameters
     * @return the flows
     */
    private List<CloudFlow> queryFlows(Map<String, String> parameters) {
        var response = api.get(requireWaba() + "/flows", parameters);
        var data = response.getJSONArray("data");
        var result = new ArrayList<CloudFlow>();
        if (data != null) {
            for (var index = 0; index < data.size(); index++) {
                result.add(parseFlow(data.getJSONObject(index)));
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void publishFlow(String flowId) {
        api.post(flowId + "/publish", new JSONObject());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deprecateFlow(String flowId) {
        api.post(flowId + "/deprecate", new JSONObject());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudMessageQr createMessageQr(String prefilledMessage) {
        var body = new JSONObject();
        body.put("prefilled_message", prefilledMessage);
        body.put("generate_qr_image", "PNG");
        var response = api.post(store.phoneNumberId() + "/message_qrdls", body);
        return parseMessageQr(response);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CloudMessageQr> queryMessageQrs() {
        var response = api.get(store.phoneNumberId() + "/message_qrdls", Map.of());
        var data = response.getJSONArray("data");
        var result = new ArrayList<CloudMessageQr>();
        if (data != null) {
            for (var index = 0; index < data.size(); index++) {
                result.add(parseMessageQr(data.getJSONObject(index)));
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteMessageQr(String code) {
        api.delete(store.phoneNumberId() + "/message_qrdls/" + code, Map.of());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudWhatsAppClient addNewMessageListener(NewMessageListener listener) {
        return addShared(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudWhatsAppClient addMessageStatusListener(MessageStatusListener listener) {
        return addShared(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudWhatsAppClient addMessageDeletedListener(MessageDeletedListener listener) {
        return addShared(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudWhatsAppClient addMessageEchoListener(CloudMessageEchoListener listener) {
        return addListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudWhatsAppClient addCallListener(CloudCallListener listener) {
        return addListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudWhatsAppClient addTemplateStatusListener(CloudTemplateStatusListener listener) {
        return addListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudWhatsAppClient addTemplateQualityListener(CloudTemplateQualityListener listener) {
        return addListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudWhatsAppClient addTemplateCategoryListener(CloudTemplateCategoryListener listener) {
        return addListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudWhatsAppClient addPhoneNumberListener(CloudPhoneNumberListener listener) {
        return addListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudWhatsAppClient addAccountUpdateListener(CloudAccountUpdateListener listener) {
        return addListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudWhatsAppClient addBusinessCapabilityListener(CloudBusinessCapabilityListener listener) {
        return addListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudWhatsAppClient addUserPreferenceListener(CloudUserPreferenceListener listener) {
        return addListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudWhatsAppClient addFlowListener(CloudFlowListener listener) {
        return addListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudWhatsAppClient addHistoryListener(CloudHistoryListener listener) {
        return addListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudWhatsAppClient addLoggedInListener(LoggedInListener listener) {
        return addShared(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudWhatsAppClient addDisconnectedListener(DisconnectedListener listener) {
        return addShared(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudWhatsAppClient addWebhookReceivedListener(CloudWebhookReceivedListener listener) {
        return addListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CloudWhatsAppClient addErrorListener(CloudErrorListener listener) {
        return addListener(listener);
    }

    /**
     * Dispatches a verified webhook envelope to the registered listeners.
     *
     * @param envelope the webhook envelope
     */
    private void dispatchEnvelope(JSONObject envelope) {
        forEach(CloudWebhookReceivedListener.class, listener -> listener.onWebhookReceived(this, envelope));
        var entries = envelope.getJSONArray("entry");
        if (entries == null) {
            return;
        }
        for (var entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
            var changes = entries.getJSONObject(entryIndex).getJSONArray("changes");
            if (changes == null) {
                continue;
            }
            for (var changeIndex = 0; changeIndex < changes.size(); changeIndex++) {
                var change = changes.getJSONObject(changeIndex);
                dispatchChange(change.getString("field"), change.getJSONObject("value"));
            }
        }
    }

    /**
     * Dispatches a single webhook change to the listeners that match its field.
     *
     * @param field the change field
     * @param value the change value
     */
    private void dispatchChange(String field, JSONObject value) {
        if (field == null || value == null) {
            return;
        }
        switch (field) {
            case "messages" -> dispatchMessages(value);
            case "smb_message_echoes" -> dispatchEchoes(value);
            case "calls" -> dispatchCalls(value);
            case "message_template_status_update" -> {
                var update = CloudWebhookDecoder.decodeTemplateStatus(value);
                forEach(CloudTemplateStatusListener.class, listener -> listener.onTemplateStatus(this, update));
            }
            case "message_template_quality_update" -> {
                var update = CloudWebhookDecoder.decodeTemplateQuality(value);
                forEach(CloudTemplateQualityListener.class, listener -> listener.onTemplateQuality(this, update));
            }
            case "template_category_update" -> {
                var update = CloudWebhookDecoder.decodeTemplateCategory(value);
                forEach(CloudTemplateCategoryListener.class, listener -> listener.onTemplateCategory(this, update));
            }
            case "phone_number_name_update" -> {
                var update = CloudWebhookDecoder.decodePhoneNumberName(value);
                forEach(CloudPhoneNumberListener.class, listener -> listener.onPhoneNumberUpdate(this, update));
            }
            case "phone_number_quality_update" -> {
                var update = CloudWebhookDecoder.decodePhoneNumberQuality(value);
                forEach(CloudPhoneNumberListener.class, listener -> listener.onPhoneNumberUpdate(this, update));
            }
            case "account_update", "account_alerts", "account_review_update" -> {
                var update = CloudWebhookDecoder.decodeAccountUpdate(value);
                forEach(CloudAccountUpdateListener.class, listener -> listener.onAccountUpdate(this, update));
            }
            case "business_capability_update" -> {
                var update = CloudWebhookDecoder.decodeBusinessCapability(value);
                forEach(CloudBusinessCapabilityListener.class, listener -> listener.onBusinessCapabilityUpdate(this, update));
            }
            case "user_preferences" -> {
                for (var update : CloudWebhookDecoder.decodeUserPreferences(value)) {
                    forEach(CloudUserPreferenceListener.class, listener -> listener.onUserPreference(this, update));
                }
            }
            case "flows" -> {
                var update = CloudWebhookDecoder.decodeFlowStatus(value);
                forEach(CloudFlowListener.class, listener -> listener.onFlowStatus(this, update));
            }
            case "history" -> {
                for (var chunk : CloudWebhookDecoder.decodeHistory(value)) {
                    forEach(CloudHistoryListener.class, listener -> listener.onHistorySync(this, chunk));
                }
            }
            default -> {
                // Unmodelled field; the raw envelope was already delivered to the webhook listeners.
            }
        }
    }

    /**
     * Decodes and dispatches inbound messages and outbound statuses of a {@code messages} change.
     *
     * @param value the change value
     */
    private void dispatchMessages(JSONObject value) {
        for (var info : CloudWebhookDecoder.decodeMessages(value)) {
            info.key().parentJid().ifPresent(chat ->
                    info.key().id().ifPresent(id -> lastInboundByChat.put(chat, id)));
            forEach(NewMessageListener.class, listener -> listener.onNewMessage(this, info));
        }
        for (var status : CloudWebhookDecoder.decodeStatuses(value)) {
            if (status.deleted()) {
                forEach(MessageDeletedListener.class,
                        listener -> listener.onMessageDeleted(this, status.info(), true));
            } else {
                forEach(MessageStatusListener.class,
                        listener -> listener.onMessageStatus(this, status.info()));
            }
        }
    }

    /**
     * Decodes and dispatches the business message echoes of an {@code smb_message_echoes} change.
     *
     * @param value the change value
     */
    private void dispatchEchoes(JSONObject value) {
        for (var info : CloudWebhookDecoder.decodeMessages(value)) {
            forEach(CloudMessageEchoListener.class, listener -> listener.onMessageEcho(this, info));
        }
    }

    /**
     * Decodes and dispatches the calling events of a {@code calls} change.
     *
     * @param value the change value
     */
    private void dispatchCalls(JSONObject value) {
        var calls = value.getJSONArray("calls");
        if (calls == null) {
            return;
        }
        for (var index = 0; index < calls.size(); index++) {
            var call = calls.getJSONObject(index);
            var timestampValue = call.getLong("timestamp");
            var event = new CloudCallEvent(
                    call.getString("id"),
                    call.getString("event"),
                    call.getString("from"),
                    callSdp(call),
                    timestampValue == null ? null : Instant.ofEpochSecond(timestampValue));
            forEach(CloudCallListener.class, listener -> listener.onCall(this, event));
        }
    }

    /**
     * Extracts the SDP description carried on a calling event, if any.
     *
     * @param call the call object
     * @return the SDP description, or {@code null} when absent
     */
    private static String callSdp(JSONObject call) {
        var store = call.getJSONObject("store");
        return store == null ? null : store.getString("sdp");
    }

    /**
     * Invokes the given action for each registered listener that matches the listener type, routing
     * any failure to the error listeners and the error handler.
     *
     * @param type   the listener type to match
     * @param action the action to run for each matching listener
     * @param <T>    the listener type
     */
    private <T extends WhatsAppListener> void forEach(Class<T> type, Consumer<T> action) {
        for (var listener : listeners) {
            if (type.isInstance(listener)) {
                try {
                    action.accept(type.cast(listener));
                } catch (RuntimeException exception) {
                    fireError(exception);
                }
            }
        }
    }

    /**
     * Routes a processing failure to the error listeners and the configurable error handler.
     *
     * @param error the failure
     */
    private void fireError(Throwable error) {
        for (var listener : listeners) {
            if (listener instanceof CloudErrorListener errorListener) {
                try {
                    errorListener.onError(this, error);
                } catch (RuntimeException ignored) {
                    // A failing error listener must not mask the original failure.
                }
            }
        }
    }

    /**
     * Returns the WhatsApp Business Account id, requiring it to be configured.
     *
     * @return the WABA id
     * @throws IllegalStateException if no WABA id was configured
     */
    private String requireWaba() {
        return store.whatsappBusinessAccountId().orElseThrow(
                () -> new IllegalStateException("operation requires a whatsappBusinessAccountId"));
    }

    /**
     * Posts a calling-status change to the phone number's settings edge.
     *
     * @param status the calling status, {@code "ENABLED"} or {@code "DISABLED"}
     */
    private void applyCallingStatus(String status) {
        var settings = new JSONObject();
        var calling = new JSONObject();
        calling.put("status", status);
        settings.put("calling", calling);
        api.post(store.phoneNumberId() + "/settings", settings);
    }

    /**
     * Builds the {@code block_users} request body for a single contact.
     *
     * @param contact the contact
     * @return the request body
     */
    private static JSONObject blockUsersBody(JidProvider contact) {
        var body = new JSONObject();
        body.put("messaging_product", "whatsapp");
        var users = new JSONArray();
        var user = new JSONObject();
        user.put("user", contact.toJid().user());
        users.add(user);
        body.put("block_users", users);
        return body;
    }

    /**
     * Extracts the {@code wamid} of the first sent message in a messages response.
     *
     * @param response the messages response
     * @return the message id, or {@code null} when absent
     */
    private static String firstMessageId(JSONObject response) {
        var messages = response.getJSONArray("messages");
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        return messages.getJSONObject(0).getString("id");
    }

    /**
     * Parses a phone-number node into a {@link CloudPhoneNumber}.
     *
     * @param node the phone-number node
     * @return the parsed phone number
     */
    private CloudPhoneNumber parsePhoneNumber(JSONObject node) {
        return new CloudPhoneNumber(
                node.getString("id") != null ? node.getString("id") : store.phoneNumberId(),
                node.getString("display_phone_number"),
                node.getString("verified_name"),
                node.getString("quality_rating"),
                node.getString("code_verification_status"),
                node.getString("status"));
    }

    /**
     * Parses a template node into a {@link CloudMessageTemplate}.
     *
     * @param node the template node
     * @return the parsed template
     */
    private static CloudMessageTemplate parseTemplate(JSONObject node) {
        return new CloudMessageTemplate(
                node.getString("id"),
                node.getString("name"),
                node.getString("language"),
                node.getString("category"),
                node.getString("status"),
                node.getJSONArray("components"));
    }

    /**
     * Parses a flow node into a {@link CloudFlow}.
     *
     * @param node the flow node
     * @return the parsed flow
     */
    private static CloudFlow parseFlow(JSONObject node) {
        var categories = new ArrayList<String>();
        var array = node.getJSONArray("categories");
        if (array != null) {
            for (var index = 0; index < array.size(); index++) {
                categories.add(array.getString(index));
            }
        }
        return new CloudFlow(node.getString("id"), node.getString("name"), node.getString("status"), categories);
    }

    /**
     * Parses a QR short-link node into a {@link CloudMessageQr}.
     *
     * @param node the QR node
     * @return the parsed QR short-link
     */
    private static CloudMessageQr parseMessageQr(JSONObject node) {
        return new CloudMessageQr(
                node.getString("code"),
                node.getString("prefilled_message"),
                node.getString("deep_link_url"),
                node.getString("qr_image_url"));
    }

    /**
     * Parses a business-profile node into a {@link BusinessProfile}.
     *
     * @param node the business-profile node
     * @return the parsed business profile
     */
    private BusinessProfile parseBusinessProfile(JSONObject node) {
        var websites = new ArrayList<URI>();
        var array = node.getJSONArray("websites");
        if (array != null) {
            for (var index = 0; index < array.size(); index++) {
                websites.add(URI.create(array.getString(index)));
            }
        }
        return new BusinessProfileBuilder()
                .jid(Jid.of(store.phoneNumberId(), JidServer.user()))
                .description(node.getString("description"))
                .address(node.getString("address"))
                .email(node.getString("email"))
                .websites(websites)
                .build();
    }
}
