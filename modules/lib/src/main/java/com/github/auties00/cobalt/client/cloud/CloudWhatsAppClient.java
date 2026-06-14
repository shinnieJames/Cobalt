package com.github.auties00.cobalt.client.cloud;
import com.github.auties00.cobalt.listener.MessageStatusListener;
import com.github.auties00.cobalt.listener.DisconnectedListener;
import com.github.auties00.cobalt.listener.LoggedInListener;
import com.github.auties00.cobalt.listener.NewMessageListener;
import com.github.auties00.cobalt.client.WhatsAppClient;

import com.github.auties00.cobalt.listener.MessageDeletedListener;
import com.github.auties00.cobalt.listener.WhatsAppListener;
import com.github.auties00.cobalt.listener.cloud.*;
import com.github.auties00.cobalt.model.business.profile.BusinessProfile;
import com.github.auties00.cobalt.model.cloud.*;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageKey;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Contract for the Meta WhatsApp Cloud API client flavour.
 *
 * <p>This client drives the WhatsApp Business Platform Cloud API: outbound traffic is a sequence of
 * stateless HTTPS requests to {@code graph.facebook.com}, and inbound traffic is delivered to a
 * webhook receiver the client hosts. It shares the cross-transport surface declared on
 * {@link WhatsAppClient} (lifecycle, listener registration, sending, reactions, read receipts,
 * business profile, blocking) and adds the management operations unique to the Cloud API: media
 * upload/download, message-template CRUD, phone-number registration and verification, webhook
 * subscription, the Calling API, Flows, and QR short-links.
 *
 * <p>Inbound events are surfaced through {@link CloudWhatsAppClientListener}
 * and its single-method listener relatives, registered either through the generic
 * {@link #addListener(CloudListener)} or the typed {@code addXxxListener} convenience methods.
 *
 * @apiNote
 * Build an instance with {@link WhatsAppClient#builder()}{@code .cloudApi()}. The client is inert until
 * {@link #connect()} validates the access token and starts the webhook receiver.
 *
 * @see WhatsAppClient
 * @see CloudWhatsAppClientBuilder
 */
public sealed interface CloudWhatsAppClient extends WhatsAppClient permits LiveCloudWhatsAppClient {
    /**
     * Returns the entry point for assembling a configured {@link CloudWhatsAppClient}.
     *
     * @apiNote
     * Embedders chain the connection step
     * ({@link CloudWhatsAppClientBuilder}) followed by the optional configuration
     * steps to obtain a ready {@code CloudWhatsAppClient}.
     *
     * @return a fresh {@link CloudWhatsAppClientBuilder}
     */
    static CloudWhatsAppClientBuilder builder() {
        return new CloudWhatsAppClientBuilder();
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code this}, for fluent chaining
     */
    @Override
    CloudWhatsAppClient connect();

    /**
     * {@inheritDoc}
     *
     * @return {@code this}, for fluent chaining
     */
    @Override
    CloudWhatsAppClient waitForDisconnection();

    /**
     * Registers a listener that receives this client's webhook events.
     *
     * @param listener the listener to register
     * @return {@code this}, for fluent chaining
     */
    CloudWhatsAppClient addListener(CloudListener listener);

    /**
     * Unregisters a previously {@linkplain #addListener(CloudListener) registered} listener.
     *
     * @param listener the listener to remove
     * @return {@code this}, for fluent chaining
     */
    @Override
    CloudWhatsAppClient removeListener(WhatsAppListener listener);

    /**
     * Uploads media from a byte array and returns the resulting media id.
     *
     * @param data     the raw media bytes
     * @param mimeType the MIME type, for example {@code image/jpeg}
     * @param filename the file name advertised to the server
     * @return the media id usable as the {@code id} of a media message
     */
    String uploadMedia(byte[] data, String mimeType, String filename);

    /**
     * Uploads media from a file and returns the resulting media id.
     *
     * @param file     the file to upload
     * @param mimeType the MIME type, for example {@code image/jpeg}
     * @return the media id usable as the {@code id} of a media message
     */
    String uploadMedia(Path file, String mimeType);

    /**
     * Uploads media from a file, probing its MIME type from the file system, and returns the
     * resulting media id.
     *
     * @param file the file to upload
     * @return the media id usable as the {@code id} of a media message
     * @throws IllegalArgumentException if the MIME type cannot be determined
     */
    String uploadMedia(Path file);

    /**
     * Downloads the binary content of a media asset by its id.
     *
     * @param mediaId the media id, typically taken from an inbound media message
     * @return the raw media bytes
     */
    byte[] downloadMedia(String mediaId);

    /**
     * Resolves the short-lived download URL of a media asset by its id.
     *
     * @param mediaId the media id
     * @return the resolved media URL
     */
    URI queryMediaUrl(String mediaId);

    /**
     * Deletes an uploaded media asset by its id.
     *
     * @param mediaId the media id to delete
     */
    void deleteMedia(String mediaId);

    /**
     * Queries the business profile of the phone number this client operates.
     *
     * @return the business profile, or {@link Optional#empty()} when none is set
     */
    Optional<BusinessProfile> queryBusinessProfile();

    /**
     * Creates a message template under the WhatsApp Business Account.
     *
     * @param template the template definition (name, language, category, components)
     * @return the created template, populated with its server-assigned id and status
     */
    CloudMessageTemplate createMessageTemplate(CloudMessageTemplate template);

    /**
     * Lists the message templates of the WhatsApp Business Account.
     *
     * @return the first page of message templates
     */
    List<CloudMessageTemplate> queryMessageTemplates();

    /**
     * Lists the message templates of the WhatsApp Business Account, bounding the page size.
     *
     * @param limit the maximum number of templates to return
     * @return the message templates, at most {@code limit}
     */
    List<CloudMessageTemplate> queryMessageTemplates(int limit);

    /**
     * Queries a single message template by name.
     *
     * @param name the template name
     * @return the matching template, or {@link Optional#empty()} when none exists
     */
    Optional<CloudMessageTemplate> queryMessageTemplate(String name);

    /**
     * Edits an existing message template.
     *
     * <p>The template is keyed by the server-assigned id carried on the model, populated by
     * {@link #createMessageTemplate(CloudMessageTemplate)} and the query methods.
     *
     * @param template the template to edit, carrying its server-assigned id
     * @throws IllegalArgumentException if the template carries no id
     */
    void editMessageTemplate(CloudMessageTemplate template);

    /**
     * Deletes a message template by name.
     *
     * @param name the template name to delete
     */
    void deleteMessageTemplate(String name);

    /**
     * Deletes a message template.
     *
     * @param template the template to delete
     */
    default void deleteMessageTemplate(CloudMessageTemplate template) {
        deleteMessageTemplate(template.name());
    }

    /**
     * Registers the phone number for Cloud API use.
     *
     * @param pin the six-digit two-step verification PIN to set during registration
     * @return the registration result
     */
    CloudRegistrationResult registerPhoneNumber(String pin);

    /**
     * Deregisters the phone number from Cloud API use.
     */
    void deregisterPhoneNumber();

    /**
     * Requests a verification code for the phone number.
     *
     * @param method   the delivery channel
     * @param language the language code for the message, for example {@code en_US}
     */
    void requestVerificationCode(CloudVerificationMethod method, String language);

    /**
     * Submits a previously requested verification code.
     *
     * @param code the verification code received over SMS or voice
     */
    void verifyCode(String code);

    /**
     * Runs the full phone-number verification ceremony through a handler.
     *
     * <p>The code is requested over the handler's preferred channel, the handler blocks until the
     * user supplies the received code, and the code is submitted back for verification.
     *
     * @param language the language code for the verification message, for example {@code en_US}
     * @param handler  the strategy that picks the delivery channel and supplies the received code
     */
    default void verifyPhoneNumber(String language, CloudWhatsAppClientVerificationHandler handler) {
        requestVerificationCode(handler.requestMethod(), language);
        verifyCode(handler.verificationCode());
    }

    /**
     * Updates the two-step verification PIN of the phone number.
     *
     * @param pin the six-digit PIN
     */
    void editTwoStepPin(String pin);

    /**
     * Queries the details of the phone number this client operates.
     *
     * @return the phone number details (status, quality rating, verified name)
     */
    CloudPhoneNumber queryPhoneNumber();

    /**
     * Lists all phone numbers registered under the WhatsApp Business Account.
     *
     * @return the first page of phone numbers
     */
    List<CloudPhoneNumber> queryPhoneNumbers();

    /**
     * Lists the phone numbers registered under the WhatsApp Business Account, bounding the page
     * size.
     *
     * @param limit the maximum number of phone numbers to return
     * @return the phone numbers, at most {@code limit}
     */
    List<CloudPhoneNumber> queryPhoneNumbers(int limit);

    /**
     * Enables inbound and outbound calling on the phone number.
     */
    void enableCalling();

    /**
     * Disables calling on the phone number.
     */
    void disableCalling();

    /**
     * Subscribes the configured app to the WhatsApp Business Account's webhooks.
     */
    void subscribeApp();

    /**
     * Lists the apps subscribed to the WhatsApp Business Account's webhooks.
     *
     * @return the subscribed app identifiers
     */
    List<String> querySubscribedApps();

    /**
     * Unsubscribes the configured app from the WhatsApp Business Account's webhooks.
     */
    void unsubscribeApp();

    /**
     * Creates a Flow under the WhatsApp Business Account.
     *
     * @param flow the flow definition
     * @return the created flow, populated with its server-assigned id
     */
    CloudFlow createFlow(CloudFlow flow);

    /**
     * Lists the Flows of the WhatsApp Business Account.
     *
     * @return the first page of flows
     */
    List<CloudFlow> queryFlows();

    /**
     * Lists the Flows of the WhatsApp Business Account, bounding the page size.
     *
     * @param limit the maximum number of flows to return
     * @return the flows, at most {@code limit}
     */
    List<CloudFlow> queryFlows(int limit);

    /**
     * Publishes a Flow, making it available to send.
     *
     * @param flowId the server-assigned flow id
     */
    void publishFlow(String flowId);

    /**
     * Publishes a Flow, making it available to send.
     *
     * @param flow the flow to publish, carrying its server-assigned id
     * @throws IllegalArgumentException if the flow carries no id
     */
    default void publishFlow(CloudFlow flow) {
        publishFlow(flow.id().orElseThrow(
                () -> new IllegalArgumentException("flow must carry an id")));
    }

    /**
     * Deprecates a published Flow.
     *
     * @param flowId the server-assigned flow id
     */
    void deprecateFlow(String flowId);

    /**
     * Deprecates a published Flow.
     *
     * @param flow the flow to deprecate, carrying its server-assigned id
     * @throws IllegalArgumentException if the flow carries no id
     */
    default void deprecateFlow(CloudFlow flow) {
        deprecateFlow(flow.id().orElseThrow(
                () -> new IllegalArgumentException("flow must carry an id")));
    }

    /**
     * Creates a QR short-link that opens a chat with a prefilled message.
     *
     * @param prefilledMessage the message text prefilled in the opened chat
     * @return the created QR short-link
     */
    CloudMessageQr createMessageQr(String prefilledMessage);

    /**
     * Lists the QR short-links of the phone number.
     *
     * @return the QR short-links
     */
    List<CloudMessageQr> queryMessageQrs();

    /**
     * Deletes a QR short-link by its code.
     *
     * @param code the QR short-link code
     */
    void deleteMessageQr(String code);

    /**
     * Deletes a QR short-link.
     *
     * @param messageQr the QR short-link to delete
     */
    default void deleteMessageQr(CloudMessageQr messageQr) {
        deleteMessageQr(messageQr.code());
    }

    /**
     * Registers a listener for inbound messages.
     *
     * @param listener the listener
     * @return {@code this}, for fluent chaining
     */
    @Override
    CloudWhatsAppClient addNewMessageListener(NewMessageListener listener);

    /**
     * Registers a listener for outbound message status updates.
     *
     * @param listener the listener
     * @return {@code this}, for fluent chaining
     */
    @Override
    CloudWhatsAppClient addMessageStatusListener(MessageStatusListener listener);

    /**
     * Registers a listener for message deletions reported through {@code statuses[]} entries whose
     * status is {@code deleted}.
     *
     * @param listener the listener
     * @return {@code this}, for fluent chaining
     */
    @Override
    CloudWhatsAppClient addMessageDeletedListener(MessageDeletedListener listener);

    /**
     * Registers a listener for echoes of messages the business sent from the WhatsApp app.
     *
     * @param listener the listener
     * @return {@code this}, for fluent chaining
     */
    CloudWhatsAppClient addMessageEchoListener(CloudMessageEchoListener listener);

    /**
     * Registers a listener for Calling API events.
     *
     * @param listener the listener
     * @return {@code this}, for fluent chaining
     */
    CloudWhatsAppClient addCallListener(CloudCallListener listener);

    /**
     * Registers a listener for message-template status updates.
     *
     * @param listener the listener
     * @return {@code this}, for fluent chaining
     */
    CloudWhatsAppClient addTemplateStatusListener(CloudTemplateStatusListener listener);

    /**
     * Registers a listener for message-template quality updates.
     *
     * @param listener the listener
     * @return {@code this}, for fluent chaining
     */
    CloudWhatsAppClient addTemplateQualityListener(CloudTemplateQualityListener listener);

    /**
     * Registers a listener for message-template category updates.
     *
     * @param listener the listener
     * @return {@code this}, for fluent chaining
     */
    CloudWhatsAppClient addTemplateCategoryListener(CloudTemplateCategoryListener listener);

    /**
     * Registers a listener for phone-number name and quality updates.
     *
     * @param listener the listener
     * @return {@code this}, for fluent chaining
     */
    CloudWhatsAppClient addPhoneNumberListener(CloudPhoneNumberListener listener);

    /**
     * Registers a listener for account updates (restrictions, bans, verification).
     *
     * @param listener the listener
     * @return {@code this}, for fluent chaining
     */
    CloudWhatsAppClient addAccountUpdateListener(CloudAccountUpdateListener listener);

    /**
     * Registers a listener for business-capability updates (messaging limits, tier).
     *
     * @param listener the listener
     * @return {@code this}, for fluent chaining
     */
    CloudWhatsAppClient addBusinessCapabilityListener(CloudBusinessCapabilityListener listener);

    /**
     * Registers a listener for user-preference updates (marketing opt-out).
     *
     * @param listener the listener
     * @return {@code this}, for fluent chaining
     */
    CloudWhatsAppClient addUserPreferenceListener(CloudUserPreferenceListener listener);

    /**
     * Registers a listener for Flow status updates.
     *
     * @param listener the listener
     * @return {@code this}, for fluent chaining
     */
    CloudWhatsAppClient addFlowListener(CloudFlowListener listener);

    /**
     * Registers a listener for history-sync deliveries on newly onboarded numbers.
     *
     * @param listener the listener
     * @return {@code this}, for fluent chaining
     */
    CloudWhatsAppClient addHistoryListener(CloudHistoryListener listener);

    /**
     * Registers a listener invoked once the access token has been validated and the webhook
     * receiver has started.
     *
     * @param listener the listener
     * @return {@code this}, for fluent chaining
     */
    @Override
    CloudWhatsAppClient addLoggedInListener(LoggedInListener listener);

    /**
     * Registers a listener invoked when the webhook receiver stops.
     *
     * @param listener the listener
     * @return {@code this}, for fluent chaining
     */
    @Override
    CloudWhatsAppClient addDisconnectedListener(DisconnectedListener listener);

    /**
     * Registers a listener that receives every raw webhook envelope before decoding.
     *
     * @param listener the listener
     * @return {@code this}, for fluent chaining
     */
    CloudWhatsAppClient addWebhookReceivedListener(CloudWebhookReceivedListener listener);

    /**
     * Registers a listener for decode, signature, and dispatch failures.
     *
     * @param listener the listener
     * @return {@code this}, for fluent chaining
     */
    CloudWhatsAppClient addErrorListener(CloudErrorListener listener);
}
