package com.github.auties00.cobalt.client.cloud;
import com.github.auties00.cobalt.listener.MessageStatusListener;
import com.github.auties00.cobalt.listener.DisconnectedListener;
import com.github.auties00.cobalt.listener.LoggedInListener;
import com.github.auties00.cobalt.listener.NewMessageListener;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientDisconnectReason;
import com.github.auties00.cobalt.listener.MessageDeletedListener;

import com.github.auties00.cobalt.listener.cloud.*;

import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.model.cloud.CloudAccountUpdate;
import com.github.auties00.cobalt.model.cloud.CloudBusinessCapabilityUpdate;
import com.github.auties00.cobalt.model.cloud.CloudCallEvent;
import com.github.auties00.cobalt.model.cloud.CloudFlowStatusUpdate;
import com.github.auties00.cobalt.model.cloud.CloudHistorySync;
import com.github.auties00.cobalt.model.cloud.CloudPhoneNumberUpdate;
import com.github.auties00.cobalt.model.cloud.CloudTemplateCategoryUpdate;
import com.github.auties00.cobalt.model.cloud.CloudTemplateQualityUpdate;
import com.github.auties00.cobalt.model.cloud.CloudTemplateStatusUpdate;
import com.github.auties00.cobalt.model.cloud.CloudUserPreferenceUpdate;
import com.github.auties00.cobalt.model.message.MessageInfo;

/**
 * Aggregator listener for every event a {@link CloudWhatsAppClient} emits.
 *
 * <p>This interface extends each single-method Cloud listener in this package and supplies an empty
 * default implementation for every callback, so an embedder can implement only the events they care
 * about. It mirrors the structure of {@code LinkedWhatsAppClientListener}: register an instance once
 * through {@link CloudWhatsAppClient#addListener(CloudListener)} to observe everything, or register
 * any of the single-method relatives as a lambda to observe one event in isolation.
 *
 * @see CloudWhatsAppClient
 */
public non-sealed interface CloudWhatsAppClientListener extends CloudListener, NewMessageListener,
        MessageStatusListener,
        CloudMessageEchoListener,
        CloudCallListener,
        CloudTemplateStatusListener,
        CloudTemplateQualityListener,
        CloudTemplateCategoryListener,
        CloudPhoneNumberListener,
        CloudAccountUpdateListener,
        CloudBusinessCapabilityListener,
        CloudUserPreferenceListener,
        CloudFlowListener,
        CloudHistoryListener,
        LoggedInListener,
        DisconnectedListener,
        MessageDeletedListener,
        CloudWebhookReceivedListener,
        CloudErrorListener {
    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation does nothing; override to handle the event.
     */
    @Override
    default void onNewMessage(WhatsAppClient whatsapp, MessageInfo info) {

    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation does nothing; override to handle the event.
     */
    @Override
    default void onMessageStatus(WhatsAppClient whatsapp, MessageInfo info) {

    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation does nothing; override to handle the event.
     */
    @Override
    default void onMessageEcho(CloudWhatsAppClient whatsapp, MessageInfo info) {

    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation does nothing; override to handle the event.
     */
    @Override
    default void onCall(CloudWhatsAppClient whatsapp, CloudCallEvent event) {

    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation does nothing; override to handle the event.
     */
    @Override
    default void onTemplateStatus(CloudWhatsAppClient whatsapp, CloudTemplateStatusUpdate update) {

    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation does nothing; override to handle the event.
     */
    @Override
    default void onTemplateQuality(CloudWhatsAppClient whatsapp, CloudTemplateQualityUpdate update) {

    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation does nothing; override to handle the event.
     */
    @Override
    default void onTemplateCategory(CloudWhatsAppClient whatsapp, CloudTemplateCategoryUpdate update) {

    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation does nothing; override to handle the event.
     */
    @Override
    default void onPhoneNumberUpdate(CloudWhatsAppClient whatsapp, CloudPhoneNumberUpdate update) {

    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation does nothing; override to handle the event.
     */
    @Override
    default void onAccountUpdate(CloudWhatsAppClient whatsapp, CloudAccountUpdate update) {

    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation does nothing; override to handle the event.
     */
    @Override
    default void onBusinessCapabilityUpdate(CloudWhatsAppClient whatsapp, CloudBusinessCapabilityUpdate update) {

    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation does nothing; override to handle the event.
     */
    @Override
    default void onUserPreference(CloudWhatsAppClient whatsapp, CloudUserPreferenceUpdate update) {

    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation does nothing; override to handle the event.
     */
    @Override
    default void onFlowStatus(CloudWhatsAppClient whatsapp, CloudFlowStatusUpdate update) {

    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation does nothing; override to handle the event.
     */
    @Override
    default void onHistorySync(CloudWhatsAppClient whatsapp, CloudHistorySync chunk) {

    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation does nothing; override to handle the event.
     */
    @Override
    default void onLoggedIn(WhatsAppClient whatsapp) {

    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation does nothing; override to handle the event.
     */
    @Override
    default void onDisconnected(WhatsAppClient whatsapp, WhatsAppClientDisconnectReason reason) {

    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation does nothing; override to handle the event.
     */
    @Override
    default void onWebhookReceived(CloudWhatsAppClient whatsapp, JSONObject envelope) {

    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation does nothing; override to handle the event.
     */
    @Override
    default void onError(CloudWhatsAppClient whatsapp, Throwable error) {

    }

    /**
     * {@inheritDoc}
     *
     * @implSpec This implementation does nothing; override to handle the event.
     */
    @Override
    default void onMessageDeleted(WhatsAppClient whatsapp, MessageInfo info, boolean everyone) {

    }
}
