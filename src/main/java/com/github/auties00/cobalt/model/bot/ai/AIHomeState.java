package com.github.auties00.cobalt.model.bot.ai;

import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A snapshot of the AI home screen state presented to the user when interacting
 * with Meta AI on WhatsApp.
 *
 * <p>The home screen displays two categories of interactive cards:
 * <ul>
 * <li>{@linkplain #capabilityOptions() capability options} — showcase what the AI can do
 *     (e.g. image generation, file analysis)
 * <li>{@linkplain #conversationOptions() conversation options} — suggested conversation
 *     starters the user can tap to begin a chat
 * </ul>
 *
 * <p>The {@linkplain #lastFetchTime() last fetch time} records when these options were
 * last retrieved from the server, enabling staleness checks and cache invalidation.
 */
@ProtobufMessage(name = "AIHomeState")
public final class AIHomeState {
    /**
     * The timestamp at which the home screen options were last fetched from the
     * server.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant lastFetchTime;

    /**
     * The list of capability-oriented cards displayed on the AI home screen.
     *
     * <p>Each option highlights a specific AI feature such as image creation or
     * document analysis.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    List<AIHomeOption> capabilityOptions;

    /**
     * The list of conversation-starter cards displayed on the AI home screen.
     *
     * <p>Each option provides a pre-written prompt the user can tap to start a
     * new AI conversation.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    List<AIHomeOption> conversationOptions;


    /**
     * Constructs a new {@code AIHomeState} with the specified values.
     *
     * @param lastFetchTime       the timestamp of the last server fetch, or {@code null}
     * @param capabilityOptions   the capability cards, or {@code null}
     * @param conversationOptions the conversation-starter cards, or {@code null}
     */
    AIHomeState(Instant lastFetchTime, List<AIHomeOption> capabilityOptions, List<AIHomeOption> conversationOptions) {
        this.lastFetchTime = lastFetchTime;
        this.capabilityOptions = capabilityOptions;
        this.conversationOptions = conversationOptions;
    }

    /**
     * Returns the timestamp at which the home screen options were last fetched.
     *
     * @return an {@code Optional} describing the last fetch time, or an empty
     *         {@code Optional} if unknown
     */
    public Optional<Instant> lastFetchTime() {
        return Optional.ofNullable(lastFetchTime);
    }

    /**
     * Returns the list of capability-oriented cards.
     *
     * @return an unmodifiable list of capability options, never {@code null}
     */
    public List<AIHomeOption> capabilityOptions() {
        return capabilityOptions == null ? List.of() : Collections.unmodifiableList(capabilityOptions);
    }

    /**
     * Returns the list of conversation-starter cards.
     *
     * @return an unmodifiable list of conversation options, never {@code null}
     */
    public List<AIHomeOption> conversationOptions() {
        return conversationOptions == null ? List.of() : Collections.unmodifiableList(conversationOptions);
    }

    /**
     * Sets the timestamp at which the home screen options were last fetched.
     *
     * @param lastFetchTime the new fetch timestamp, or {@code null}
     */
    public void setLastFetchTime(Instant lastFetchTime) {
        this.lastFetchTime = lastFetchTime;
    }

    /**
     * Sets the list of capability-oriented cards.
     *
     * @param capabilityOptions the new capability options list, or {@code null}
     */
    public void setCapabilityOptions(List<AIHomeOption> capabilityOptions) {
        this.capabilityOptions = capabilityOptions;
    }

    /**
     * Sets the list of conversation-starter cards.
     *
     * @param conversationOptions the new conversation options list, or {@code null}
     */
    public void setConversationOptions(List<AIHomeOption> conversationOptions) {
        this.conversationOptions = conversationOptions;
    }

    /**
     * An interactive card displayed on the AI home screen that the user can tap to
     * trigger a specific AI action.
     *
     * <p>Each option has a {@linkplain #type() type} that determines its behavior
     * (e.g. sending a text prompt, generating an image), a {@linkplain #title() title}
     * for display, and optional visual styling properties for its icon.
     */
    @ProtobufMessage(name = "AIHomeState.AIHomeOption")
    public static final class AIHomeOption {
        /**
         * The action type this option performs when tapped.
         */
        @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
        AIHomeOption.AIHomeActionType type;

        /**
         * The display title shown on the card, for example {@code "Create an image"}.
         */
        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String title;

        /**
         * The prompt text sent to the AI when this option is tapped, for example
         * {@code "Draw a sunset over the ocean"}.
         */
        @ProtobufProperty(index = 3, type = ProtobufType.STRING)
        String promptText;

        /**
         * The identifier of the AI session to associate with this option, for example
         * {@code "abc123-def456"}.
         */
        @ProtobufProperty(index = 4, type = ProtobufType.STRING)
        String sessionId;

        /**
         * The design-system asset identifier for the icon image displayed on this
         * card, for example {@code "ai_create_image_icon"}.
         */
        @ProtobufProperty(index = 5, type = ProtobufType.STRING)
        String imageAssetIdentifier;

        /**
         * The tint color applied to the icon image, as a CSS-compatible color string,
         * for example {@code "#FFFFFF"}.
         */
        @ProtobufProperty(index = 6, type = ProtobufType.STRING)
        String imageTintColor;

        /**
         * The background color behind the icon image, as a CSS-compatible color string,
         * for example {@code "#1A73E8"}.
         */
        @ProtobufProperty(index = 7, type = ProtobufType.STRING)
        String imageBackgroundColor;

        /**
         * An identifier for the card layout type used for rendering, for example
         * {@code "capability_card"}.
         */
        @ProtobufProperty(index = 8, type = ProtobufType.STRING)
        String cardTypeId;


        /**
         * Constructs a new {@code AIHomeOption} with the specified values.
         *
         * @param type                 the action type, or {@code null}
         * @param title                the display title, or {@code null}
         * @param promptText           the prompt text, or {@code null}
         * @param sessionId            the session identifier, or {@code null}
         * @param imageAssetIdentifier the icon asset identifier, or {@code null}
         * @param imageTintColor       the icon tint color, or {@code null}
         * @param imageBackgroundColor the icon background color, or {@code null}
         * @param cardTypeId           the card layout type identifier, or {@code null}
         */
        AIHomeOption(AIHomeActionType type, String title, String promptText, String sessionId, String imageAssetIdentifier, String imageTintColor, String imageBackgroundColor, String cardTypeId) {
            this.type = type;
            this.title = title;
            this.promptText = promptText;
            this.sessionId = sessionId;
            this.imageAssetIdentifier = imageAssetIdentifier;
            this.imageTintColor = imageTintColor;
            this.imageBackgroundColor = imageBackgroundColor;
            this.cardTypeId = cardTypeId;
        }

        /**
         * Returns the action type this option performs when tapped.
         *
         * @return an {@code Optional} describing the action type, or an empty
         *         {@code Optional} if not set
         */
        public Optional<AIHomeActionType> type() {
            return Optional.ofNullable(type);
        }

        /**
         * Returns the display title shown on the card.
         *
         * @return an {@code Optional} describing the title, or an empty
         *         {@code Optional} if not set
         */
        public Optional<String> title() {
            return Optional.ofNullable(title);
        }

        /**
         * Returns the prompt text sent to the AI when this option is tapped.
         *
         * @return an {@code Optional} describing the prompt text, or an empty
         *         {@code Optional} if not set
         */
        public Optional<String> promptText() {
            return Optional.ofNullable(promptText);
        }

        /**
         * Returns the AI session identifier associated with this option.
         *
         * @return an {@code Optional} describing the session identifier, or an empty
         *         {@code Optional} if not set
         */
        public Optional<String> sessionId() {
            return Optional.ofNullable(sessionId);
        }

        /**
         * Returns the design-system asset identifier for the icon image.
         *
         * @return an {@code Optional} describing the asset identifier, or an empty
         *         {@code Optional} if not set
         */
        public Optional<String> imageAssetIdentifier() {
            return Optional.ofNullable(imageAssetIdentifier);
        }

        /**
         * Returns the tint color applied to the icon image.
         *
         * @return an {@code Optional} describing the tint color, or an empty
         *         {@code Optional} if not set
         */
        public Optional<String> imageTintColor() {
            return Optional.ofNullable(imageTintColor);
        }

        /**
         * Returns the background color behind the icon image.
         *
         * @return an {@code Optional} describing the background color, or an empty
         *         {@code Optional} if not set
         */
        public Optional<String> imageBackgroundColor() {
            return Optional.ofNullable(imageBackgroundColor);
        }

        /**
         * Returns the card layout type identifier.
         *
         * @return an {@code Optional} describing the card type identifier, or an empty
         *         {@code Optional} if not set
         */
        public Optional<String> cardTypeId() {
            return Optional.ofNullable(cardTypeId);
        }

        /**
         * Sets the action type this option performs when tapped.
         *
         * @param type the new action type, or {@code null}
         */
        public void setType(AIHomeActionType type) {
            this.type = type;
    }

        /**
         * Sets the display title shown on the card.
         *
         * @param title the new title, or {@code null}
         */
        public void setTitle(String title) {
            this.title = title;
    }

        /**
         * Sets the prompt text sent to the AI when this option is tapped.
         *
         * @param promptText the new prompt text, or {@code null}
         */
        public void setPromptText(String promptText) {
            this.promptText = promptText;
    }

        /**
         * Sets the AI session identifier associated with this option.
         *
         * @param sessionId the new session identifier, or {@code null}
         */
        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
    }

        /**
         * Sets the design-system asset identifier for the icon image.
         *
         * @param imageAssetIdentifier the new asset identifier, or {@code null}
         */
        public void setImageAssetIdentifier(String imageAssetIdentifier) {
            this.imageAssetIdentifier = imageAssetIdentifier;
    }

        /**
         * Sets the tint color applied to the icon image.
         *
         * @param imageTintColor the new tint color, or {@code null}
         */
        public void setImageTintColor(String imageTintColor) {
            this.imageTintColor = imageTintColor;
    }

        /**
         * Sets the background color behind the icon image.
         *
         * @param imageBackgroundColor the new background color, or {@code null}
         */
        public void setImageBackgroundColor(String imageBackgroundColor) {
            this.imageBackgroundColor = imageBackgroundColor;
    }

        /**
         * Sets the card layout type identifier.
         *
         * @param cardTypeId the new card type identifier, or {@code null}
         */
        public void setCardTypeId(String cardTypeId) {
            this.cardTypeId = cardTypeId;
    }

        /**
         * The type of action performed when an {@link AIHomeOption} is tapped on
         * the AI home screen.
         */
        @ProtobufEnum(name = "AIHomeState.AIHomeOption.AIHomeActionType")
        public static enum AIHomeActionType {
            /**
             * Sends a text prompt to the AI.
             */
            PROMPT(0),

            /**
             * Opens the AI image-generation flow.
             */
            CREATE_IMAGE(1),

            /**
             * Opens the photo-animation flow.
             */
            ANIMATE_PHOTO(2),

            /**
             * Opens the file-analysis flow.
             */
            ANALYZE_FILE(3);

            AIHomeActionType(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            /**
             * The protobuf index of this enum constant.
             */
            final int index;

            /**
             * Returns the protobuf index of this enum constant.
             *
             * @return the protobuf index
             */
            public int index() {
                return this.index;
            }
        }
    }
}
