package com.github.auties00.cobalt.model.bot;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.bot.ai.AIRegenerateMetadata;
import com.github.auties00.cobalt.model.bot.ai.AIThreadInfo;
import com.github.auties00.cobalt.model.bot.feedback.*;
import com.github.auties00.cobalt.model.bot.metrics.BotInfrastructureDiagnostics;
import com.github.auties00.cobalt.model.bot.metrics.BotMetricsMetadata;
import com.github.auties00.cobalt.model.bot.plugin.*;
import com.github.auties00.cobalt.model.bot.rendering.*;
import com.github.auties00.cobalt.model.bot.session.*;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

/**
 * The top-level metadata container attached to an AI bot message on WhatsApp.
 *
 * <p>Every message exchanged with Meta AI carries an instance of
 * {@code BotMetadata} that aggregates all bot-related sub-metadata. This
 * includes information about the bot's:
 * <ul>
 * <li>{@linkplain #sessionMetadata() session} — model selection, mode, origin
 * <li>{@linkplain #pluginMetadata() plugins} — search, image generation, capabilities
 * <li>{@linkplain #renderingMetadata() rendering} — rich responses, progress indicators
 * <li>{@linkplain #memoryMetadata() memory} — personalization facts
 * <li>{@linkplain #reminderMetadata() reminders} — scheduled notifications
 * <li>{@linkplain #verificationMetadata() verification} — cryptographic signatures
 * <li>{@linkplain #botThreadInfo() thread info} — AI conversation thread context
 * </ul>
 */
@ProtobufMessage(name = "BotMetadata")
public final class BotMetadata {
    /**
     * The avatar metadata for the bot persona used in this message.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    BotAvatarMetadata avatarMetadata;

    /**
     * The identifier of the bot persona that generated this message, for example
     * {@code "meta_ai"}.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String personaId;

    /**
     * Metadata about the bot plugins active for this message (e.g. web search,
     * code generation).
     */
    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    BotPluginMetadata pluginMetadata;

    /**
     * Metadata for suggested prompts displayed alongside this message.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
    BotSuggestedPromptMetadata suggestedPromptMetadata;

    /**
     * The JID of the user who invoked the bot (relevant in group chats where
     * multiple users can interact with the bot).
     */
    @ProtobufProperty(index = 5, type = ProtobufType.STRING)
    Jid invokerJid;

    /**
     * Metadata about the bot session, including model and mode selection.
     */
    @ProtobufProperty(index = 6, type = ProtobufType.MESSAGE)
    BotSessionMetadata sessionMetadata;

    /**
     * Metadata for the Meta AI menu (quick-action shortcuts).
     */
    @ProtobufProperty(index = 7, type = ProtobufType.MESSAGE)
    BotMemuMetadata memuMetadata;

    /**
     * The IANA timezone of the user interacting with the bot, for example
     * {@code "America/New_York"}.
     */
    @ProtobufProperty(index = 8, type = ProtobufType.STRING)
    String timezone;

    /**
     * Metadata about a reminder associated with this message.
     */
    @ProtobufProperty(index = 9, type = ProtobufType.MESSAGE)
    BotReminderMetadata reminderMetadata;

    /**
     * Metadata about the AI model used for this message.
     */
    @ProtobufProperty(index = 10, type = ProtobufType.MESSAGE)
    BotModelMetadata modelMetadata;

    /**
     * A disclaimer text displayed below the bot message, for example
     * {@code "AI-generated content may be inaccurate"}.
     */
    @ProtobufProperty(index = 11, type = ProtobufType.STRING)
    String messageDisclaimerText;

    /**
     * Metadata for the typing/progress indicator shown while the bot generates
     * its response.
     */
    @ProtobufProperty(index = 12, type = ProtobufType.MESSAGE)
    BotProgressIndicatorMetadata progressIndicatorMetadata;

    /**
     * Metadata describing the capabilities the client advertises to the server
     * for rich response rendering.
     */
    @ProtobufProperty(index = 13, type = ProtobufType.MESSAGE)
    BotCapabilityMetadata capabilityMetadata;

    /**
     * Metadata for AI image-generation ("Imagine") responses.
     */
    @ProtobufProperty(index = 14, type = ProtobufType.MESSAGE)
    BotImagineMetadata imagineMetadata;

    /**
     * Metadata describing changes to the bot's personalization memory.
     */
    @ProtobufProperty(index = 15, type = ProtobufType.MESSAGE)
    BotMemoryMetadata memoryMetadata;

    /**
     * Metadata controlling how the bot response is rendered in the UI.
     */
    @ProtobufProperty(index = 16, type = ProtobufType.MESSAGE)
    BotRenderingMetadata renderingMetadata;

    /**
     * Telemetry and performance metrics for this bot interaction.
     */
    @ProtobufProperty(index = 17, type = ProtobufType.MESSAGE)
    BotMetricsMetadata botMetricsMetadata;

    /**
     * Metadata about external accounts linked to the bot for plugin access.
     */
    @ProtobufProperty(index = 18, type = ProtobufType.MESSAGE)
    BotLinkedAccountsMetadata botLinkedAccountsMetadata;

    /**
     * Source attribution metadata for the bot's rich response content.
     */
    @ProtobufProperty(index = 19, type = ProtobufType.MESSAGE)
    BotSourcesMetadata richResponseSourcesMetadata;

    /**
     * Opaque binary context for the AI conversation, used by the server to
     * maintain state across turns.
     */
    @ProtobufProperty(index = 20, type = ProtobufType.BYTES)
    byte[] aiConversationContext;

    /**
     * Metadata for promotional messages displayed within the bot UI.
     */
    @ProtobufProperty(index = 21, type = ProtobufType.MESSAGE)
    BotPromotionMessageMetadata botPromotionMessageMetadata;

    /**
     * Metadata about the AI mode the user has selected (e.g. creative, precise).
     */
    @ProtobufProperty(index = 22, type = ProtobufType.MESSAGE)
    BotModeSelectionMetadata botModeSelectionMetadata;

    /**
     * Metadata about the user's usage quota for AI interactions.
     */
    @ProtobufProperty(index = 23, type = ProtobufType.MESSAGE)
    BotQuotaMetadata botQuotaMetadata;

    /**
     * Metadata for age-verification collection required before AI interactions.
     */
    @ProtobufProperty(index = 24, type = ProtobufType.MESSAGE)
    BotAgeCollectionMetadata botAgeCollectionMetadata;

    /**
     * The identifier of the conversation-starter prompt that initiated this
     * session, for example {@code "starter_travel_planning"}.
     */
    @ProtobufProperty(index = 25, type = ProtobufType.STRING)
    String conversationStarterPromptId;

    /**
     * The server-assigned identifier for this bot response, for example
     * {@code "resp_abc123def456"}.
     */
    @ProtobufProperty(index = 26, type = ProtobufType.STRING)
    String botResponseId;

    /**
     * Cryptographic signature verification metadata for authenticating this
     * bot message.
     */
    @ProtobufProperty(index = 27, type = ProtobufType.MESSAGE)
    BotSignatureVerificationMetadata verificationMetadata;

    /**
     * Mutation data for unified rich responses (incremental updates to a
     * streaming response).
     */
    @ProtobufProperty(index = 28, type = ProtobufType.MESSAGE)
    BotUnifiedResponseMutation unifiedResponseMutation;

    /**
     * Metadata about where this bot message originated from.
     */
    @ProtobufProperty(index = 29, type = ProtobufType.MESSAGE)
    BotMessageOriginMetadata botMessageOriginMetadata;

    /**
     * Metadata for an in-thread user satisfaction survey.
     */
    @ProtobufProperty(index = 30, type = ProtobufType.MESSAGE)
    InThreadSurveyMetadata inThreadSurveyMetadata;

    /**
     * Information about the AI conversation thread this message belongs to.
     */
    @ProtobufProperty(index = 31, type = ProtobufType.MESSAGE)
    AIThreadInfo botThreadInfo;

    /**
     * Metadata for a response-regeneration request.
     */
    @ProtobufProperty(index = 32, type = ProtobufType.MESSAGE)
    AIRegenerateMetadata regenerateMetadata;

    /**
     * Transparency disclosure metadata shown during this session.
     */
    @ProtobufProperty(index = 33, type = ProtobufType.MESSAGE)
    SessionTransparencyMetadata sessionTransparencyMetadata;

    /**
     * Metadata about documents attached to this bot message.
     */
    @ProtobufProperty(index = 34, type = ProtobufType.MESSAGE)
    BotDocumentMessageMetadata botDocumentMessageMetadata;

    /**
     * Metadata about the group context when the bot is used in a group chat.
     */
    @ProtobufProperty(index = 35, type = ProtobufType.MESSAGE)
    BotGroupMetadata botGroupMetadata;

    /**
     * Configuration metadata controlling rendering behavior for this message.
     */
    @ProtobufProperty(index = 36, type = ProtobufType.MESSAGE)
    BotRenderingConfigMetadata botRenderingConfigMetadata;

    /**
     * Infrastructure diagnostics data for debugging bot interactions.
     */
    @ProtobufProperty(index = 37, type = ProtobufType.MESSAGE)
    BotInfrastructureDiagnostics botInfrastructureDiagnostics;

    /**
     * Opaque internal metadata reserved for server-side use.
     */
    @ProtobufProperty(index = 999, type = ProtobufType.BYTES)
    byte[] internalMetadata;


    /**
     * Constructs a new {@code BotMetadata} with the specified values.
     *
     * @param avatarMetadata                the avatar metadata, or {@code null}
     * @param personaId                     the bot persona identifier, or {@code null}
     * @param pluginMetadata                the plugin metadata, or {@code null}
     * @param suggestedPromptMetadata       the suggested prompt metadata, or {@code null}
     * @param invokerJid                    the invoker JID, or {@code null}
     * @param sessionMetadata               the session metadata, or {@code null}
     * @param memuMetadata                  the menu metadata, or {@code null}
     * @param timezone                      the user timezone, or {@code null}
     * @param reminderMetadata              the reminder metadata, or {@code null}
     * @param modelMetadata                 the model metadata, or {@code null}
     * @param messageDisclaimerText         the disclaimer text, or {@code null}
     * @param progressIndicatorMetadata     the progress indicator metadata, or {@code null}
     * @param capabilityMetadata            the capability metadata, or {@code null}
     * @param imagineMetadata               the image-generation metadata, or {@code null}
     * @param memoryMetadata                the memory metadata, or {@code null}
     * @param renderingMetadata             the rendering metadata, or {@code null}
     * @param botMetricsMetadata            the metrics metadata, or {@code null}
     * @param botLinkedAccountsMetadata     the linked accounts metadata, or {@code null}
     * @param richResponseSourcesMetadata   the sources metadata, or {@code null}
     * @param aiConversationContext          the conversation context bytes, or {@code null}
     * @param botPromotionMessageMetadata   the promotion message metadata, or {@code null}
     * @param botModeSelectionMetadata      the mode selection metadata, or {@code null}
     * @param botQuotaMetadata              the quota metadata, or {@code null}
     * @param botAgeCollectionMetadata      the age collection metadata, or {@code null}
     * @param conversationStarterPromptId   the starter prompt identifier, or {@code null}
     * @param botResponseId                 the response identifier, or {@code null}
     * @param verificationMetadata          the signature verification metadata, or {@code null}
     * @param unifiedResponseMutation       the unified response mutation, or {@code null}
     * @param botMessageOriginMetadata      the message origin metadata, or {@code null}
     * @param inThreadSurveyMetadata        the in-thread survey metadata, or {@code null}
     * @param botThreadInfo                 the AI thread info, or {@code null}
     * @param regenerateMetadata            the regeneration metadata, or {@code null}
     * @param sessionTransparencyMetadata   the session transparency metadata, or {@code null}
     * @param botDocumentMessageMetadata    the document message metadata, or {@code null}
     * @param botGroupMetadata              the group metadata, or {@code null}
     * @param botRenderingConfigMetadata    the rendering config metadata, or {@code null}
     * @param botInfrastructureDiagnostics  the infrastructure diagnostics, or {@code null}
     * @param internalMetadata              the internal metadata bytes, or {@code null}
     */
    BotMetadata(BotAvatarMetadata avatarMetadata, String personaId, BotPluginMetadata pluginMetadata, BotSuggestedPromptMetadata suggestedPromptMetadata, Jid invokerJid, BotSessionMetadata sessionMetadata, BotMemuMetadata memuMetadata, String timezone, BotReminderMetadata reminderMetadata, BotModelMetadata modelMetadata, String messageDisclaimerText, BotProgressIndicatorMetadata progressIndicatorMetadata, BotCapabilityMetadata capabilityMetadata, BotImagineMetadata imagineMetadata, BotMemoryMetadata memoryMetadata, BotRenderingMetadata renderingMetadata, BotMetricsMetadata botMetricsMetadata, BotLinkedAccountsMetadata botLinkedAccountsMetadata, BotSourcesMetadata richResponseSourcesMetadata, byte[] aiConversationContext, BotPromotionMessageMetadata botPromotionMessageMetadata, BotModeSelectionMetadata botModeSelectionMetadata, BotQuotaMetadata botQuotaMetadata, BotAgeCollectionMetadata botAgeCollectionMetadata, String conversationStarterPromptId, String botResponseId, BotSignatureVerificationMetadata verificationMetadata, BotUnifiedResponseMutation unifiedResponseMutation, BotMessageOriginMetadata botMessageOriginMetadata, InThreadSurveyMetadata inThreadSurveyMetadata, AIThreadInfo botThreadInfo, AIRegenerateMetadata regenerateMetadata, SessionTransparencyMetadata sessionTransparencyMetadata, BotDocumentMessageMetadata botDocumentMessageMetadata, BotGroupMetadata botGroupMetadata, BotRenderingConfigMetadata botRenderingConfigMetadata, BotInfrastructureDiagnostics botInfrastructureDiagnostics, byte[] internalMetadata) {
        this.avatarMetadata = avatarMetadata;
        this.personaId = personaId;
        this.pluginMetadata = pluginMetadata;
        this.suggestedPromptMetadata = suggestedPromptMetadata;
        this.invokerJid = invokerJid;
        this.sessionMetadata = sessionMetadata;
        this.memuMetadata = memuMetadata;
        this.timezone = timezone;
        this.reminderMetadata = reminderMetadata;
        this.modelMetadata = modelMetadata;
        this.messageDisclaimerText = messageDisclaimerText;
        this.progressIndicatorMetadata = progressIndicatorMetadata;
        this.capabilityMetadata = capabilityMetadata;
        this.imagineMetadata = imagineMetadata;
        this.memoryMetadata = memoryMetadata;
        this.renderingMetadata = renderingMetadata;
        this.botMetricsMetadata = botMetricsMetadata;
        this.botLinkedAccountsMetadata = botLinkedAccountsMetadata;
        this.richResponseSourcesMetadata = richResponseSourcesMetadata;
        this.aiConversationContext = aiConversationContext;
        this.botPromotionMessageMetadata = botPromotionMessageMetadata;
        this.botModeSelectionMetadata = botModeSelectionMetadata;
        this.botQuotaMetadata = botQuotaMetadata;
        this.botAgeCollectionMetadata = botAgeCollectionMetadata;
        this.conversationStarterPromptId = conversationStarterPromptId;
        this.botResponseId = botResponseId;
        this.verificationMetadata = verificationMetadata;
        this.unifiedResponseMutation = unifiedResponseMutation;
        this.botMessageOriginMetadata = botMessageOriginMetadata;
        this.inThreadSurveyMetadata = inThreadSurveyMetadata;
        this.botThreadInfo = botThreadInfo;
        this.regenerateMetadata = regenerateMetadata;
        this.sessionTransparencyMetadata = sessionTransparencyMetadata;
        this.botDocumentMessageMetadata = botDocumentMessageMetadata;
        this.botGroupMetadata = botGroupMetadata;
        this.botRenderingConfigMetadata = botRenderingConfigMetadata;
        this.botInfrastructureDiagnostics = botInfrastructureDiagnostics;
        this.internalMetadata = internalMetadata;
    }

    /**
     * Returns the avatar metadata for the bot persona.
     *
     * @return an {@code Optional} describing the avatar metadata, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotAvatarMetadata> avatarMetadata() {
        return Optional.ofNullable(avatarMetadata);
    }

    /**
     * Returns the identifier of the bot persona.
     *
     * @return an {@code Optional} describing the persona identifier, or an empty
     *         {@code Optional} if not set
     */
    public Optional<String> personaId() {
        return Optional.ofNullable(personaId);
    }

    /**
     * Returns the plugin metadata for this message.
     *
     * @return an {@code Optional} describing the plugin metadata, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotPluginMetadata> pluginMetadata() {
        return Optional.ofNullable(pluginMetadata);
    }

    /**
     * Returns the suggested prompt metadata for this message.
     *
     * @return an {@code Optional} describing the suggested prompt metadata, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotSuggestedPromptMetadata> suggestedPromptMetadata() {
        return Optional.ofNullable(suggestedPromptMetadata);
    }

    /**
     * Returns the JID of the user who invoked the bot.
     *
     * @return an {@code Optional} describing the invoker JID, or an empty
     *         {@code Optional} if not set
     */
    public Optional<Jid> invokerJid() {
        return Optional.ofNullable(invokerJid);
    }

    /**
     * Returns the bot session metadata.
     *
     * @return an {@code Optional} describing the session metadata, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotSessionMetadata> sessionMetadata() {
        return Optional.ofNullable(sessionMetadata);
    }

    /**
     * Returns the Meta AI menu metadata.
     *
     * @return an {@code Optional} describing the menu metadata, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotMemuMetadata> memuMetadata() {
        return Optional.ofNullable(memuMetadata);
    }

    /**
     * Returns the IANA timezone of the user.
     *
     * @return an {@code Optional} describing the timezone, or an empty
     *         {@code Optional} if not set
     */
    public Optional<String> timezone() {
        return Optional.ofNullable(timezone);
    }

    /**
     * Returns the reminder metadata associated with this message.
     *
     * @return an {@code Optional} describing the reminder metadata, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotReminderMetadata> reminderMetadata() {
        return Optional.ofNullable(reminderMetadata);
    }

    /**
     * Returns the AI model metadata.
     *
     * @return an {@code Optional} describing the model metadata, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotModelMetadata> modelMetadata() {
        return Optional.ofNullable(modelMetadata);
    }

    /**
     * Returns the disclaimer text displayed below the bot message.
     *
     * @return an {@code Optional} describing the disclaimer text, or an empty
     *         {@code Optional} if not set
     */
    public Optional<String> messageDisclaimerText() {
        return Optional.ofNullable(messageDisclaimerText);
    }

    /**
     * Returns the progress indicator metadata.
     *
     * @return an {@code Optional} describing the progress indicator metadata, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotProgressIndicatorMetadata> progressIndicatorMetadata() {
        return Optional.ofNullable(progressIndicatorMetadata);
    }

    /**
     * Returns the capability metadata advertised by the client.
     *
     * @return an {@code Optional} describing the capability metadata, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotCapabilityMetadata> capabilityMetadata() {
        return Optional.ofNullable(capabilityMetadata);
    }

    /**
     * Returns the image-generation ("Imagine") metadata.
     *
     * @return an {@code Optional} describing the imagine metadata, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotImagineMetadata> imagineMetadata() {
        return Optional.ofNullable(imagineMetadata);
    }

    /**
     * Returns the bot memory metadata.
     *
     * @return an {@code Optional} describing the memory metadata, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotMemoryMetadata> memoryMetadata() {
        return Optional.ofNullable(memoryMetadata);
    }

    /**
     * Returns the rendering metadata for this message.
     *
     * @return an {@code Optional} describing the rendering metadata, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotRenderingMetadata> renderingMetadata() {
        return Optional.ofNullable(renderingMetadata);
    }

    /**
     * Returns the telemetry metrics metadata.
     *
     * @return an {@code Optional} describing the metrics metadata, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotMetricsMetadata> botMetricsMetadata() {
        return Optional.ofNullable(botMetricsMetadata);
    }

    /**
     * Returns the linked accounts metadata.
     *
     * @return an {@code Optional} describing the linked accounts metadata, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotLinkedAccountsMetadata> botLinkedAccountsMetadata() {
        return Optional.ofNullable(botLinkedAccountsMetadata);
    }

    /**
     * Returns the source attribution metadata for rich responses.
     *
     * @return an {@code Optional} describing the sources metadata, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotSourcesMetadata> richResponseSourcesMetadata() {
        return Optional.ofNullable(richResponseSourcesMetadata);
    }

    /**
     * Returns the opaque AI conversation context bytes.
     *
     * @return an {@code Optional} describing the context bytes, or an empty
     *         {@code Optional} if not set
     */
    public Optional<byte[]> aiConversationContext() {
        return Optional.ofNullable(aiConversationContext);
    }

    /**
     * Returns the promotional message metadata.
     *
     * @return an {@code Optional} describing the promotion metadata, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotPromotionMessageMetadata> botPromotionMessageMetadata() {
        return Optional.ofNullable(botPromotionMessageMetadata);
    }

    /**
     * Returns the AI mode selection metadata.
     *
     * @return an {@code Optional} describing the mode selection metadata, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotModeSelectionMetadata> botModeSelectionMetadata() {
        return Optional.ofNullable(botModeSelectionMetadata);
    }

    /**
     * Returns the usage quota metadata.
     *
     * @return an {@code Optional} describing the quota metadata, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotQuotaMetadata> botQuotaMetadata() {
        return Optional.ofNullable(botQuotaMetadata);
    }

    /**
     * Returns the age-verification collection metadata.
     *
     * @return an {@code Optional} describing the age collection metadata, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotAgeCollectionMetadata> botAgeCollectionMetadata() {
        return Optional.ofNullable(botAgeCollectionMetadata);
    }

    /**
     * Returns the conversation-starter prompt identifier.
     *
     * @return an {@code Optional} describing the prompt identifier, or an empty
     *         {@code Optional} if not set
     */
    public Optional<String> conversationStarterPromptId() {
        return Optional.ofNullable(conversationStarterPromptId);
    }

    /**
     * Returns the server-assigned bot response identifier.
     *
     * @return an {@code Optional} describing the response identifier, or an empty
     *         {@code Optional} if not set
     */
    public Optional<String> botResponseId() {
        return Optional.ofNullable(botResponseId);
    }

    /**
     * Returns the signature verification metadata.
     *
     * @return an {@code Optional} describing the verification metadata, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotSignatureVerificationMetadata> verificationMetadata() {
        return Optional.ofNullable(verificationMetadata);
    }

    /**
     * Returns the unified response mutation data.
     *
     * @return an {@code Optional} describing the mutation, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotUnifiedResponseMutation> unifiedResponseMutation() {
        return Optional.ofNullable(unifiedResponseMutation);
    }

    /**
     * Returns the message origin metadata.
     *
     * @return an {@code Optional} describing the origin metadata, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotMessageOriginMetadata> botMessageOriginMetadata() {
        return Optional.ofNullable(botMessageOriginMetadata);
    }

    /**
     * Returns the in-thread survey metadata.
     *
     * @return an {@code Optional} describing the survey metadata, or an empty
     *         {@code Optional} if not set
     */
    public Optional<InThreadSurveyMetadata> inThreadSurveyMetadata() {
        return Optional.ofNullable(inThreadSurveyMetadata);
    }

    /**
     * Returns the AI thread information for this message.
     *
     * @return an {@code Optional} describing the thread info, or an empty
     *         {@code Optional} if not set
     */
    public Optional<AIThreadInfo> botThreadInfo() {
        return Optional.ofNullable(botThreadInfo);
    }

    /**
     * Returns the response-regeneration metadata.
     *
     * @return an {@code Optional} describing the regeneration metadata, or an empty
     *         {@code Optional} if not set
     */
    public Optional<AIRegenerateMetadata> regenerateMetadata() {
        return Optional.ofNullable(regenerateMetadata);
    }

    /**
     * Returns the session transparency metadata.
     *
     * @return an {@code Optional} describing the transparency metadata, or an empty
     *         {@code Optional} if not set
     */
    public Optional<SessionTransparencyMetadata> sessionTransparencyMetadata() {
        return Optional.ofNullable(sessionTransparencyMetadata);
    }

    /**
     * Returns the document message metadata.
     *
     * @return an {@code Optional} describing the document metadata, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotDocumentMessageMetadata> botDocumentMessageMetadata() {
        return Optional.ofNullable(botDocumentMessageMetadata);
    }

    /**
     * Returns the group context metadata.
     *
     * @return an {@code Optional} describing the group metadata, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotGroupMetadata> botGroupMetadata() {
        return Optional.ofNullable(botGroupMetadata);
    }

    /**
     * Returns the rendering configuration metadata.
     *
     * @return an {@code Optional} describing the rendering config, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotRenderingConfigMetadata> botRenderingConfigMetadata() {
        return Optional.ofNullable(botRenderingConfigMetadata);
    }

    /**
     * Returns the infrastructure diagnostics data.
     *
     * @return an {@code Optional} describing the diagnostics, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotInfrastructureDiagnostics> botInfrastructureDiagnostics() {
        return Optional.ofNullable(botInfrastructureDiagnostics);
    }

    /**
     * Returns the opaque internal metadata bytes.
     *
     * @return an {@code Optional} describing the internal metadata, or an empty
     *         {@code Optional} if not set
     */
    public Optional<byte[]> internalMetadata() {
        return Optional.ofNullable(internalMetadata);
    }

    /**
     * Sets the avatar metadata for the bot persona.
     *
     * @param avatarMetadata the new avatar metadata, or {@code null}
     */
    public void setAvatarMetadata(BotAvatarMetadata avatarMetadata) {
        this.avatarMetadata = avatarMetadata;
    }

    /**
     * Sets the identifier of the bot persona.
     *
     * @param personaId the new persona identifier, or {@code null}
     */
    public void setPersonaId(String personaId) {
        this.personaId = personaId;
    }

    /**
     * Sets the plugin metadata for this message.
     *
     * @param pluginMetadata the new plugin metadata, or {@code null}
     */
    public void setPluginMetadata(BotPluginMetadata pluginMetadata) {
        this.pluginMetadata = pluginMetadata;
    }

    /**
     * Sets the suggested prompt metadata for this message.
     *
     * @param suggestedPromptMetadata the new suggested prompt metadata, or {@code null}
     */
    public void setSuggestedPromptMetadata(BotSuggestedPromptMetadata suggestedPromptMetadata) {
        this.suggestedPromptMetadata = suggestedPromptMetadata;
    }

    /**
     * Sets the JID of the user who invoked the bot.
     *
     * @param invokerJid the new invoker JID, or {@code null}
     */
    public void setInvokerJid(Jid invokerJid) {
        this.invokerJid = invokerJid;
    }

    /**
     * Sets the bot session metadata.
     *
     * @param sessionMetadata the new session metadata, or {@code null}
     */
    public void setSessionMetadata(BotSessionMetadata sessionMetadata) {
        this.sessionMetadata = sessionMetadata;
    }

    /**
     * Sets the Meta AI menu metadata.
     *
     * @param memuMetadata the new menu metadata, or {@code null}
     */
    public void setMemuMetadata(BotMemuMetadata memuMetadata) {
        this.memuMetadata = memuMetadata;
    }

    /**
     * Sets the IANA timezone of the user.
     *
     * @param timezone the new timezone, or {@code null}
     */
    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    /**
     * Sets the reminder metadata associated with this message.
     *
     * @param reminderMetadata the new reminder metadata, or {@code null}
     */
    public void setReminderMetadata(BotReminderMetadata reminderMetadata) {
        this.reminderMetadata = reminderMetadata;
    }

    /**
     * Sets the AI model metadata.
     *
     * @param modelMetadata the new model metadata, or {@code null}
     */
    public void setModelMetadata(BotModelMetadata modelMetadata) {
        this.modelMetadata = modelMetadata;
    }

    /**
     * Sets the disclaimer text displayed below the bot message.
     *
     * @param messageDisclaimerText the new disclaimer text, or {@code null}
     */
    public void setMessageDisclaimerText(String messageDisclaimerText) {
        this.messageDisclaimerText = messageDisclaimerText;
    }

    /**
     * Sets the progress indicator metadata.
     *
     * @param progressIndicatorMetadata the new progress indicator metadata, or {@code null}
     */
    public void setProgressIndicatorMetadata(BotProgressIndicatorMetadata progressIndicatorMetadata) {
        this.progressIndicatorMetadata = progressIndicatorMetadata;
    }

    /**
     * Sets the capability metadata advertised by the client.
     *
     * @param capabilityMetadata the new capability metadata, or {@code null}
     */
    public void setCapabilityMetadata(BotCapabilityMetadata capabilityMetadata) {
        this.capabilityMetadata = capabilityMetadata;
    }

    /**
     * Sets the image-generation ("Imagine") metadata.
     *
     * @param imagineMetadata the new imagine metadata, or {@code null}
     */
    public void setImagineMetadata(BotImagineMetadata imagineMetadata) {
        this.imagineMetadata = imagineMetadata;
    }

    /**
     * Sets the bot memory metadata.
     *
     * @param memoryMetadata the new memory metadata, or {@code null}
     */
    public void setMemoryMetadata(BotMemoryMetadata memoryMetadata) {
        this.memoryMetadata = memoryMetadata;
    }

    /**
     * Sets the rendering metadata for this message.
     *
     * @param renderingMetadata the new rendering metadata, or {@code null}
     */
    public void setRenderingMetadata(BotRenderingMetadata renderingMetadata) {
        this.renderingMetadata = renderingMetadata;
    }

    /**
     * Sets the telemetry metrics metadata.
     *
     * @param botMetricsMetadata the new metrics metadata, or {@code null}
     */
    public void setBotMetricsMetadata(BotMetricsMetadata botMetricsMetadata) {
        this.botMetricsMetadata = botMetricsMetadata;
    }

    /**
     * Sets the linked accounts metadata.
     *
     * @param botLinkedAccountsMetadata the new linked accounts metadata, or {@code null}
     */
    public void setBotLinkedAccountsMetadata(BotLinkedAccountsMetadata botLinkedAccountsMetadata) {
        this.botLinkedAccountsMetadata = botLinkedAccountsMetadata;
    }

    /**
     * Sets the source attribution metadata for rich responses.
     *
     * @param richResponseSourcesMetadata the new sources metadata, or {@code null}
     */
    public void setRichResponseSourcesMetadata(BotSourcesMetadata richResponseSourcesMetadata) {
        this.richResponseSourcesMetadata = richResponseSourcesMetadata;
    }

    /**
     * Sets the opaque AI conversation context bytes.
     *
     * @param aiConversationContext the new context bytes, or {@code null}
     */
    public void setAiConversationContext(byte[] aiConversationContext) {
        this.aiConversationContext = aiConversationContext;
    }

    /**
     * Sets the promotional message metadata.
     *
     * @param botPromotionMessageMetadata the new promotion metadata, or {@code null}
     */
    public void setBotPromotionMessageMetadata(BotPromotionMessageMetadata botPromotionMessageMetadata) {
        this.botPromotionMessageMetadata = botPromotionMessageMetadata;
    }

    /**
     * Sets the AI mode selection metadata.
     *
     * @param botModeSelectionMetadata the new mode selection metadata, or {@code null}
     */
    public void setBotModeSelectionMetadata(BotModeSelectionMetadata botModeSelectionMetadata) {
        this.botModeSelectionMetadata = botModeSelectionMetadata;
    }

    /**
     * Sets the usage quota metadata.
     *
     * @param botQuotaMetadata the new quota metadata, or {@code null}
     */
    public void setBotQuotaMetadata(BotQuotaMetadata botQuotaMetadata) {
        this.botQuotaMetadata = botQuotaMetadata;
    }

    /**
     * Sets the age-verification collection metadata.
     *
     * @param botAgeCollectionMetadata the new age collection metadata, or {@code null}
     */
    public void setBotAgeCollectionMetadata(BotAgeCollectionMetadata botAgeCollectionMetadata) {
        this.botAgeCollectionMetadata = botAgeCollectionMetadata;
    }

    /**
     * Sets the conversation-starter prompt identifier.
     *
     * @param conversationStarterPromptId the new prompt identifier, or {@code null}
     */
    public void setConversationStarterPromptId(String conversationStarterPromptId) {
        this.conversationStarterPromptId = conversationStarterPromptId;
    }

    /**
     * Sets the server-assigned bot response identifier.
     *
     * @param botResponseId the new response identifier, or {@code null}
     */
    public void setBotResponseId(String botResponseId) {
        this.botResponseId = botResponseId;
    }

    /**
     * Sets the signature verification metadata.
     *
     * @param verificationMetadata the new verification metadata, or {@code null}
     */
    public void setVerificationMetadata(BotSignatureVerificationMetadata verificationMetadata) {
        this.verificationMetadata = verificationMetadata;
    }

    /**
     * Sets the unified response mutation data.
     *
     * @param unifiedResponseMutation the new mutation, or {@code null}
     */
    public void setUnifiedResponseMutation(BotUnifiedResponseMutation unifiedResponseMutation) {
        this.unifiedResponseMutation = unifiedResponseMutation;
    }

    /**
     * Sets the message origin metadata.
     *
     * @param botMessageOriginMetadata the new origin metadata, or {@code null}
     */
    public void setBotMessageOriginMetadata(BotMessageOriginMetadata botMessageOriginMetadata) {
        this.botMessageOriginMetadata = botMessageOriginMetadata;
    }

    /**
     * Sets the in-thread survey metadata.
     *
     * @param inThreadSurveyMetadata the new survey metadata, or {@code null}
     */
    public void setInThreadSurveyMetadata(InThreadSurveyMetadata inThreadSurveyMetadata) {
        this.inThreadSurveyMetadata = inThreadSurveyMetadata;
    }

    /**
     * Sets the AI thread information for this message.
     *
     * @param botThreadInfo the new thread info, or {@code null}
     */
    public void setBotThreadInfo(AIThreadInfo botThreadInfo) {
        this.botThreadInfo = botThreadInfo;
    }

    /**
     * Sets the response-regeneration metadata.
     *
     * @param regenerateMetadata the new regeneration metadata, or {@code null}
     */
    public void setRegenerateMetadata(AIRegenerateMetadata regenerateMetadata) {
        this.regenerateMetadata = regenerateMetadata;
    }

    /**
     * Sets the session transparency metadata.
     *
     * @param sessionTransparencyMetadata the new transparency metadata, or {@code null}
     */
    public void setSessionTransparencyMetadata(SessionTransparencyMetadata sessionTransparencyMetadata) {
        this.sessionTransparencyMetadata = sessionTransparencyMetadata;
    }

    /**
     * Sets the document message metadata.
     *
     * @param botDocumentMessageMetadata the new document metadata, or {@code null}
     */
    public void setBotDocumentMessageMetadata(BotDocumentMessageMetadata botDocumentMessageMetadata) {
        this.botDocumentMessageMetadata = botDocumentMessageMetadata;
    }

    /**
     * Sets the group context metadata.
     *
     * @param botGroupMetadata the new group metadata, or {@code null}
     */
    public void setBotGroupMetadata(BotGroupMetadata botGroupMetadata) {
        this.botGroupMetadata = botGroupMetadata;
    }

    /**
     * Sets the rendering configuration metadata.
     *
     * @param botRenderingConfigMetadata the new rendering config, or {@code null}
     */
    public void setBotRenderingConfigMetadata(BotRenderingConfigMetadata botRenderingConfigMetadata) {
        this.botRenderingConfigMetadata = botRenderingConfigMetadata;
    }

    /**
     * Sets the infrastructure diagnostics data.
     *
     * @param botInfrastructureDiagnostics the new diagnostics, or {@code null}
     */
    public void setBotInfrastructureDiagnostics(BotInfrastructureDiagnostics botInfrastructureDiagnostics) {
        this.botInfrastructureDiagnostics = botInfrastructureDiagnostics;
    }

    /**
     * Sets the opaque internal metadata bytes.
     *
     * @param internalMetadata the new internal metadata, or {@code null}
     */
    public void setInternalMetadata(byte[] internalMetadata) {
        this.internalMetadata = internalMetadata;
    }
}
