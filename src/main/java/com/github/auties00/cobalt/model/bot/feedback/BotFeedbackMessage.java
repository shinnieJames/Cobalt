package com.github.auties00.cobalt.model.bot.feedback;

import com.github.auties00.cobalt.model.message.MessageKey;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * A protobuf message representing user feedback on a WhatsApp AI bot response.
 *
 * <p>This message captures the user's evaluation of an AI-generated response,
 * including thumbs-up (positive) or thumbs-down (negative) feedback, optional
 * free-text commentary, and categorized reasons for the feedback. The
 * {@code kindNegative} and {@code kindPositive} fields are bitmasks composed of
 * {@link BotFeedbackKindMultipleNegative} and {@link BotFeedbackKindMultiplePositive}
 * flag values respectively, allowing multiple feedback reasons to be expressed
 * simultaneously.
 *
 * <p>Additionally, this message may contain {@link SideBySideSurveyMetadata}
 * for A/B testing scenarios where the user compares two AI-generated responses
 * side by side.
 */
@ProtobufMessage(name = "BotFeedbackMessage", generateBuilder = false)
public final class BotFeedbackMessage {
    /**
     * The key identifying the AI bot message that this feedback refers to.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageKey messageKey;

    /**
     * The overall kind of feedback provided by the user, for example
     * {@link BotFeedbackKind#BOT_FEEDBACK_POSITIVE} or one of the negative variants.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
    BotFeedbackKind kind;

    /**
     * Free-text feedback provided by the user, for example {@code "The answer was too vague"}.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String text;

    /**
     * A bitmask of negative feedback reasons selected by the user.
     *
     * <p>Each bit corresponds to a value in {@link BotFeedbackKindMultipleNegative},
     * allowing multiple reasons to be combined, for example {@code 3L} to indicate
     * both generic and unhelpful.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.UINT64)
    Long kindNegative;

    /**
     * A bitmask of positive feedback reasons selected by the user.
     *
     * <p>Each bit corresponds to a value in {@link BotFeedbackKindMultiplePositive},
     * for example {@code 1L} to indicate generic positive feedback.
     */
    @ProtobufProperty(index = 5, type = ProtobufType.UINT64)
    Long kindPositive;

    /**
     * The kind of report associated with this feedback, if the user chose to
     * report the AI response.
     */
    @ProtobufProperty(index = 6, type = ProtobufType.ENUM)
    ReportKind kindReport;

    /**
     * Metadata from a side-by-side survey in which the user compared two AI
     * responses and selected a preferred one.
     */
    @ProtobufProperty(index = 7, type = ProtobufType.MESSAGE)
    SideBySideSurveyMetadata sideBySideSurveyMetadata;

    BotFeedbackMessage(MessageKey messageKey, BotFeedbackKind kind, String text, Long kindNegative, Long kindPositive, ReportKind kindReport, SideBySideSurveyMetadata sideBySideSurveyMetadata) {
        this.messageKey = messageKey;
        this.kind = kind;
        this.text = text;
        this.kindNegative = kindNegative;
        this.kindPositive = kindPositive;
        this.kindReport = kindReport;
        this.sideBySideSurveyMetadata = sideBySideSurveyMetadata;
    }

    /**
     * Constructs a positive {@code BotFeedbackMessage}.
     *
     * <p>The {@code kind} field is automatically set to
     * {@link BotFeedbackKind#BOT_FEEDBACK_POSITIVE}. The negative
     * feedback bitmask and report kind are left unset.
     *
     * @param messageKey              the key identifying the message
     *        being rated, or {@code null}
     * @param text                    optional free-text feedback, or
     *        {@code null}
     * @param kindPositive            bitmask of positive feedback reasons
     *        composed of {@link BotFeedbackKindMultiplePositive} flags,
     *        or {@code null}
     * @param sideBySideSurveyMetadata metadata from a side-by-side
     *        comparison survey, or {@code null}
     * @return a new positive {@code BotFeedbackMessage}
     */
    @ProtobufBuilder(className = "BotFeedbackMessagePositiveBuilder")
    static BotFeedbackMessage ofPositive(MessageKey messageKey, String text, Long kindPositive, SideBySideSurveyMetadata sideBySideSurveyMetadata) {
        return new BotFeedbackMessage(messageKey, BotFeedbackKind.BOT_FEEDBACK_POSITIVE, text, null, kindPositive, null, sideBySideSurveyMetadata);
    }

    /**
     * Constructs a negative {@code BotFeedbackMessage}.
     *
     * <p>The positive feedback bitmask is left unset. The caller
     * supplies the specific negative {@code kind} (e.g.
     * {@link BotFeedbackKind#BOT_FEEDBACK_NEGATIVE_HELPFUL}) along
     * with the multi-select bitmask and an optional report kind.
     *
     * @param messageKey              the key identifying the message
     *        being rated, or {@code null}
     * @param kind                    the specific negative feedback kind
     * @param text                    optional free-text feedback, or
     *        {@code null}
     * @param kindNegative            bitmask of negative feedback reasons
     *        composed of {@link BotFeedbackKindMultipleNegative} flags,
     *        or {@code null}
     * @param kindReport              the report kind, or {@code null}
     * @param sideBySideSurveyMetadata metadata from a side-by-side
     *        comparison survey, or {@code null}
     * @return a new negative {@code BotFeedbackMessage}
     */
    @ProtobufBuilder(className = "BotFeedbackMessageNegativeBuilder")
    static BotFeedbackMessage ofNegative(MessageKey messageKey, BotFeedbackKind kind, String text, Long kindNegative, ReportKind kindReport, SideBySideSurveyMetadata sideBySideSurveyMetadata) {
        return new BotFeedbackMessage(messageKey, kind, text, kindNegative, null, kindReport, sideBySideSurveyMetadata);
    }

    /**
     * Returns the key identifying the AI bot message that this feedback refers to.
     *
     * @return an {@code Optional} describing the message key, or an empty
     *         {@code Optional} if not set
     */
    public Optional<MessageKey> messageKey() {
        return Optional.ofNullable(messageKey);
    }

    /**
     * Returns the overall kind of feedback provided by the user.
     *
     * @return an {@code Optional} describing the feedback kind, or an empty
     *         {@code Optional} if not set
     */
    public Optional<BotFeedbackKind> kind() {
        return Optional.ofNullable(kind);
    }

    /**
     * Returns the free-text feedback provided by the user.
     *
     * @return an {@code Optional} describing the feedback text, or an empty
     *         {@code Optional} if not set
     */
    public Optional<String> text() {
        return Optional.ofNullable(text);
    }

    /**
     * Returns the bitmask of negative feedback reasons selected by the user.
     *
     * @return an {@code OptionalLong} describing the negative feedback bitmask,
     *         or an empty {@code OptionalLong} if not set
     */
    public OptionalLong kindNegative() {
        return kindNegative == null ? OptionalLong.empty() : OptionalLong.of(kindNegative);
    }

    /**
     * Returns the bitmask of positive feedback reasons selected by the user.
     *
     * @return an {@code OptionalLong} describing the positive feedback bitmask,
     *         or an empty {@code OptionalLong} if not set
     */
    public OptionalLong kindPositive() {
        return kindPositive == null ? OptionalLong.empty() : OptionalLong.of(kindPositive);
    }

    /**
     * Returns the kind of report associated with this feedback.
     *
     * @return an {@code Optional} describing the report kind, or an empty
     *         {@code Optional} if not set
     */
    public Optional<ReportKind> kindReport() {
        return Optional.ofNullable(kindReport);
    }

    /**
     * Returns the side-by-side survey metadata, if this feedback was collected
     * during a comparison survey.
     *
     * @return an {@code Optional} describing the survey metadata, or an empty
     *         {@code Optional} if not set
     */
    public Optional<SideBySideSurveyMetadata> sideBySideSurveyMetadata() {
        return Optional.ofNullable(sideBySideSurveyMetadata);
    }

    /**
     * An enumeration of individual feedback kinds that a user can provide on
     * an AI bot response.
     *
     * <p>These values represent single-select feedback categories, as opposed
     * to the multi-select bitmask variants in {@link BotFeedbackKindMultipleNegative}
     * and {@link BotFeedbackKindMultiplePositive}.
     */
    @ProtobufEnum(name = "BotFeedbackMessage.BotFeedbackKind")
    public static enum BotFeedbackKind {
        /**
         * The user provided positive feedback on the AI response.
         */
        BOT_FEEDBACK_POSITIVE(0),

        /**
         * The user provided generic negative feedback without a specific reason.
         */
        BOT_FEEDBACK_NEGATIVE_GENERIC(1),

        /**
         * The user indicated the AI response was not helpful.
         */
        BOT_FEEDBACK_NEGATIVE_HELPFUL(2),

        /**
         * The user indicated the AI response was not interesting.
         */
        BOT_FEEDBACK_NEGATIVE_INTERESTING(3),

        /**
         * The user indicated the AI response was not accurate.
         */
        BOT_FEEDBACK_NEGATIVE_ACCURATE(4),

        /**
         * The user indicated the AI response was not safe or appropriate.
         */
        BOT_FEEDBACK_NEGATIVE_SAFE(5),

        /**
         * The user selected "other" as the negative feedback reason.
         */
        BOT_FEEDBACK_NEGATIVE_OTHER(6),

        /**
         * The user indicated the AI refused to answer when it should not have.
         */
        BOT_FEEDBACK_NEGATIVE_REFUSED(7),

        /**
         * The user indicated the AI-generated visual content was not visually appealing.
         */
        BOT_FEEDBACK_NEGATIVE_NOT_VISUALLY_APPEALING(8),

        /**
         * The user indicated the AI-generated visual content was not relevant to the
         * text prompt.
         */
        BOT_FEEDBACK_NEGATIVE_NOT_RELEVANT_TO_TEXT(9),

        /**
         * The user indicated the AI response lacked personalization.
         */
        BOT_FEEDBACK_NEGATIVE_PERSONALIZED(10),

        /**
         * The user indicated the AI response lacked clarity.
         */
        BOT_FEEDBACK_NEGATIVE_CLARITY(11),

        /**
         * The user indicated the AI-generated image does not look like the
         * person it was supposed to depict.
         */
        BOT_FEEDBACK_NEGATIVE_DOESNT_LOOK_LIKE_THE_PERSON(12),

        /**
         * The user reported a hallucination in the AI response. This value is
         * intended for internal use only.
         */
        BOT_FEEDBACK_NEGATIVE_HALLUCINATION_INTERNAL_ONLY(13),

        /**
         * The user provided general negative feedback.
         */
        BOT_FEEDBACK_NEGATIVE(14);

        /**
         * Constructs a new {@code BotFeedbackKind} with the specified protobuf index.
         *
         * @param index the protobuf index value
         */
        BotFeedbackKind(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        /**
         * The protobuf index value of this feedback kind.
         */
        final int index;

        /**
         * Returns the protobuf index value of this feedback kind.
         *
         * @return the protobuf index value
         */
        public int index() {
            return this.index;
        }
    }

    /**
     * An enumeration of negative feedback flag values that can be combined as a
     * bitmask in the {@link BotFeedbackMessage#kindNegative()} field.
     *
     * <p>Each constant's index is a power of two, allowing multiple reasons to be
     * bitwise-ORed together into a single {@code long} value.
     */
    @ProtobufEnum(name = "BotFeedbackMessage.BotFeedbackKindMultipleNegative")
    public static enum BotFeedbackKindMultipleNegative {
        /**
         * A generic negative feedback flag with bitmask value {@code 1}.
         */
        BOT_FEEDBACK_MULTIPLE_NEGATIVE_GENERIC(1),

        /**
         * A negative feedback flag indicating the response was not helpful, with
         * bitmask value {@code 2}.
         */
        BOT_FEEDBACK_MULTIPLE_NEGATIVE_HELPFUL(2),

        /**
         * A negative feedback flag indicating the response was not interesting, with
         * bitmask value {@code 4}.
         */
        BOT_FEEDBACK_MULTIPLE_NEGATIVE_INTERESTING(4),

        /**
         * A negative feedback flag indicating the response was not accurate, with
         * bitmask value {@code 8}.
         */
        BOT_FEEDBACK_MULTIPLE_NEGATIVE_ACCURATE(8),

        /**
         * A negative feedback flag indicating the response was not safe, with
         * bitmask value {@code 16}.
         */
        BOT_FEEDBACK_MULTIPLE_NEGATIVE_SAFE(16),

        /**
         * A negative feedback flag for an unspecified "other" reason, with
         * bitmask value {@code 32}.
         */
        BOT_FEEDBACK_MULTIPLE_NEGATIVE_OTHER(32),

        /**
         * A negative feedback flag indicating the AI refused to respond, with
         * bitmask value {@code 64}.
         */
        BOT_FEEDBACK_MULTIPLE_NEGATIVE_REFUSED(64),

        /**
         * A negative feedback flag indicating the AI-generated visual content was
         * not visually appealing, with bitmask value {@code 128}.
         */
        BOT_FEEDBACK_MULTIPLE_NEGATIVE_NOT_VISUALLY_APPEALING(128),

        /**
         * A negative feedback flag indicating the AI-generated visual content was
         * not relevant to the text prompt, with bitmask value {@code 256}.
         */
        BOT_FEEDBACK_MULTIPLE_NEGATIVE_NOT_RELEVANT_TO_TEXT(256);

        /**
         * Constructs a new {@code BotFeedbackKindMultipleNegative} with the specified
         * protobuf index.
         *
         * @param index the protobuf index value (used as a bitmask flag)
         */
        BotFeedbackKindMultipleNegative(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        /**
         * The protobuf index value of this negative feedback flag, used as a bitmask.
         */
        final int index;

        /**
         * Returns the protobuf index value of this negative feedback flag.
         *
         * @return the protobuf index value
         */
        public int index() {
            return this.index;
        }
    }

    /**
     * An enumeration of positive feedback flag values that can be combined as a
     * bitmask in the {@link BotFeedbackMessage#kindPositive()} field.
     *
     * <p>Each constant's index is a power of two, allowing multiple reasons to be
     * bitwise-ORed together into a single {@code long} value.
     */
    @ProtobufEnum(name = "BotFeedbackMessage.BotFeedbackKindMultiplePositive")
    public static enum BotFeedbackKindMultiplePositive {
        /**
         * A generic positive feedback flag with bitmask value {@code 1}.
         */
        BOT_FEEDBACK_MULTIPLE_POSITIVE_GENERIC(1);

        /**
         * Constructs a new {@code BotFeedbackKindMultiplePositive} with the specified
         * protobuf index.
         *
         * @param index the protobuf index value (used as a bitmask flag)
         */
        BotFeedbackKindMultiplePositive(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        /**
         * The protobuf index value of this positive feedback flag, used as a bitmask.
         */
        final int index;

        /**
         * Returns the protobuf index value of this positive feedback flag.
         *
         * @return the protobuf index value
         */
        public int index() {
            return this.index;
        }
    }

    /**
     * An enumeration of report kinds that a user can select when reporting an
     * AI bot response as problematic.
     */
    @ProtobufEnum(name = "BotFeedbackMessage.ReportKind")
    public static enum ReportKind {
        /**
         * No report was submitted.
         */
        NONE(0),

        /**
         * A generic report was submitted without a specific category.
         */
        GENERIC(1);

        /**
         * Constructs a new {@code ReportKind} with the specified protobuf index.
         *
         * @param index the protobuf index value
         */
        ReportKind(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        /**
         * The protobuf index value of this report kind.
         */
        final int index;

        /**
         * Returns the protobuf index value of this report kind.
         *
         * @return the protobuf index value
         */
        public int index() {
            return this.index;
        }
    }

    /**
     * A protobuf message containing metadata from a side-by-side survey in which
     * a user compares two AI-generated responses and selects a preferred one.
     *
     * <p>This metadata captures the user's selection, the survey identifier, and
     * analytics data used for A/B testing of AI response quality.
     */
    @ProtobufMessage(name = "BotFeedbackMessage.SideBySideSurveyMetadata")
    public static final class SideBySideSurveyMetadata {
        /**
         * The identifier of the request whose response was selected by the user,
         * for example {@code "req_abc123"}.
         */
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String selectedRequestId;

        /**
         * The numeric identifier of the survey instance, for example {@code 42}.
         */
        @ProtobufProperty(index = 2, type = ProtobufType.UINT32)
        Integer surveyId;

        /**
         * The session identifier for the survey platform, for example {@code "1234567890"}.
         */
        @ProtobufProperty(index = 3, type = ProtobufType.STRING)
        String surveySessionId;

        /**
         * The original trace identifier of the AI response being evaluated,
         * for example {@code "otid_xyz789"}.
         */
        @ProtobufProperty(index = 4, type = ProtobufType.STRING)
        String responseOriginalTraceId;

        /**
         * The timestamp in milliseconds when the AI response was generated,
         * encoded as a string, for example {@code "1700000000000"}.
         */
        @ProtobufProperty(index = 5, type = ProtobufType.STRING)
        String responseTimestampMillis;

        /**
         * Whether the response selected by the user was the primary (control) response
         * in the A/B test.
         */
        @ProtobufProperty(index = 6, type = ProtobufType.BOOL)
        Boolean isSelectedResponsePrimary;

        /**
         * The identifier of the message to edit as a result of this survey feedback,
         * for example {@code "msg_edit_456"}.
         */
        @ProtobufProperty(index = 7, type = ProtobufType.STRING)
        String messageIdToEdit;

        /**
         * Analytics data for the side-by-side survey event tracking.
         */
        @ProtobufProperty(index = 8, type = ProtobufType.MESSAGE)
        SideBySideSurveyMetadata.SideBySideSurveyAnalyticsData analyticsData;

        /**
         * Meta AI-specific analytics data for the side-by-side survey.
         */
        @ProtobufProperty(index = 9, type = ProtobufType.MESSAGE)
        SideBySideSurveyMetadata.SidebySideSurveyMetaAiAnalyticsData metaAiAnalyticsData;

        /**
         * Constructs a new {@code SideBySideSurveyMetadata} with the specified fields.
         *
         * @param selectedRequestId       the identifier of the selected request
         * @param surveyId                the numeric survey identifier
         * @param surveySessionId         the survey platform session identifier
         * @param responseOriginalTraceId the original trace identifier of the response
         * @param responseTimestampMillis the response timestamp in milliseconds as a string
         * @param isSelectedResponsePrimary whether the selected response was primary
         * @param messageIdToEdit         the identifier of the message to edit
         * @param analyticsData           the survey analytics data
         * @param metaAiAnalyticsData     the Meta AI analytics data
         */
        SideBySideSurveyMetadata(String selectedRequestId, Integer surveyId, String surveySessionId, String responseOriginalTraceId, String responseTimestampMillis, Boolean isSelectedResponsePrimary, String messageIdToEdit, SideBySideSurveyAnalyticsData analyticsData, SidebySideSurveyMetaAiAnalyticsData metaAiAnalyticsData) {
            this.selectedRequestId = selectedRequestId;
            this.surveyId = surveyId;
            this.surveySessionId = surveySessionId;
            this.responseOriginalTraceId = responseOriginalTraceId;
            this.responseTimestampMillis = responseTimestampMillis;
            this.isSelectedResponsePrimary = isSelectedResponsePrimary;
            this.messageIdToEdit = messageIdToEdit;
            this.analyticsData = analyticsData;
            this.metaAiAnalyticsData = metaAiAnalyticsData;
        }

        /**
         * Returns the identifier of the request whose response was selected.
         *
         * @return an {@code Optional} describing the selected request identifier, or an empty {@code Optional} if not set
         */
        public Optional<String> selectedRequestId() {
            return Optional.ofNullable(selectedRequestId);
        }

        /**
         * Returns the numeric identifier of the survey instance.
         *
         * @return an {@code OptionalInt} describing the survey identifier, or an empty {@code OptionalInt} if not set
         */
        public OptionalInt surveyId() {
            return surveyId == null ? OptionalInt.empty() : OptionalInt.of(surveyId);
        }

        /**
         * Returns the session identifier for the survey platform.
         *
         * @return an {@code Optional} describing the survey session identifier, or an empty {@code Optional} if not set
         */
        public Optional<String> surveySessionId() {
            return Optional.ofNullable(surveySessionId);
        }

        /**
         * Returns the original trace identifier of the AI response being evaluated.
         *
         * @return an {@code Optional} describing the response original trace identifier, or an empty {@code Optional} if not set
         */
        public Optional<String> responseOriginalTraceId() {
            return Optional.ofNullable(responseOriginalTraceId);
        }

        /**
         * Returns the timestamp in milliseconds when the AI response was generated.
         *
         * @return an {@code Optional} describing the response timestamp in milliseconds, or an empty {@code Optional} if not set
         */
        public Optional<String> responseTimestampMillis() {
            return Optional.ofNullable(responseTimestampMillis);
        }

        /**
         * Returns whether the response selected by the user was the primary (control)
         * response in the A/B test.
         *
         * @return {@code true} if the selected response was primary, {@code false} otherwise
         */
        public boolean isSelectedResponsePrimary() {
            return isSelectedResponsePrimary != null && isSelectedResponsePrimary;
        }

        /**
         * Returns the identifier of the message to edit as a result of this survey.
         *
         * @return an {@code Optional} describing the message identifier to edit, or an empty {@code Optional} if not set
         */
        public Optional<String> messageIdToEdit() {
            return Optional.ofNullable(messageIdToEdit);
        }

        /**
         * Returns the analytics data for the side-by-side survey.
         *
         * @return an {@code Optional} describing the analytics data, or an empty {@code Optional} if not set
         */
        public Optional<SideBySideSurveyAnalyticsData> analyticsData() {
            return Optional.ofNullable(analyticsData);
        }

        /**
         * Returns the Meta AI-specific analytics data for the side-by-side survey.
         *
         * @return an {@code Optional} describing the Meta AI analytics data, or an empty {@code Optional} if not set
         */
        public Optional<SidebySideSurveyMetaAiAnalyticsData> metaAiAnalyticsData() {
            return Optional.ofNullable(metaAiAnalyticsData);
        }

        /**
         * Sets the identifier of the request whose response was selected.
         *
         * @param selectedRequestId the new selected request identifier, or {@code null}
         */
        public void setSelectedRequestId(String selectedRequestId) {
            this.selectedRequestId = selectedRequestId;
    }

        /**
         * Sets the numeric identifier of the survey instance.
         *
         * @param surveyId the new survey identifier, or {@code null}
         */
        public void setSurveyId(Integer surveyId) {
            this.surveyId = surveyId;
    }

        /**
         * Sets the session identifier for the survey platform.
         *
         * @param surveySessionId the new survey session identifier, or {@code null}
         */
        public void setSurveySessionId(String surveySessionId) {
            this.surveySessionId = surveySessionId;
    }

        /**
         * Sets the original trace identifier of the AI response being evaluated.
         *
         * @param responseOriginalTraceId the new response original trace identifier, or {@code null}
         */
        public void setResponseOriginalTraceId(String responseOriginalTraceId) {
            this.responseOriginalTraceId = responseOriginalTraceId;
    }

        /**
         * Sets the timestamp in milliseconds when the AI response was generated.
         *
         * @param responseTimestampMillis the new response timestamp in milliseconds, or {@code null}
         */
        public void setResponseTimestampMillis(String responseTimestampMillis) {
            this.responseTimestampMillis = responseTimestampMillis;
    }

        /**
         * Sets whether the response selected by the user was the primary (control)
         * response in the A/B test.
         *
         * @param isSelectedResponsePrimary the new primary response flag, or {@code null}
         */
        public void setSelectedResponsePrimary(Boolean isSelectedResponsePrimary) {
            this.isSelectedResponsePrimary = isSelectedResponsePrimary;
    }

        /**
         * Sets the identifier of the message to edit as a result of this survey.
         *
         * @param messageIdToEdit the new message identifier to edit, or {@code null}
         */
        public void setMessageIdToEdit(String messageIdToEdit) {
            this.messageIdToEdit = messageIdToEdit;
    }

        /**
         * Sets the analytics data for the side-by-side survey.
         *
         * @param analyticsData the new analytics data, or {@code null}
         */
        public void setAnalyticsData(SideBySideSurveyAnalyticsData analyticsData) {
            this.analyticsData = analyticsData;
    }

        /**
         * Sets the Meta AI-specific analytics data for the side-by-side survey.
         *
         * @param metaAiAnalyticsData the new Meta AI analytics data, or {@code null}
         */
        public void setMetaAiAnalyticsData(SidebySideSurveyMetaAiAnalyticsData metaAiAnalyticsData) {
            this.metaAiAnalyticsData = metaAiAnalyticsData;
    }

        /**
         * A protobuf message containing analytics event tracking data for a
         * side-by-side survey.
         *
         * <p>This captures the analytics event name and session identifiers used
         * to correlate survey interactions with backend analytics systems.
         */
        @ProtobufMessage(name = "BotFeedbackMessage.SideBySideSurveyMetadata.SideBySideSurveyAnalyticsData")
        public static final class SideBySideSurveyAnalyticsData {
            /**
             * The name of the analytics event to log, for example {@code "sbs_survey_completed"}.
             */
            @ProtobufProperty(index = 1, type = ProtobufType.STRING)
            String analyticsEvent;

            /**
             * The analytics platform session identifier, for example {@code "9876543210"}.
             */
            @ProtobufProperty(index = 2, type = ProtobufType.STRING)
            String analyticsSessionId;

            /**
             * The survey platform session identifier, for example {@code "1234567890"}.
             */
            @ProtobufProperty(index = 3, type = ProtobufType.STRING)
            String surveySessionId;

            /**
             * Constructs a new {@code SideBySideSurveyAnalyticsData} with the specified fields.
             *
             * @param analyticsEvent    the analytics event name
             * @param analyticsSessionId the analytics platform session identifier
             * @param surveySessionId   the survey platform session identifier
             */
            SideBySideSurveyAnalyticsData(String analyticsEvent, String analyticsSessionId, String surveySessionId) {
                this.analyticsEvent = analyticsEvent;
                this.analyticsSessionId = analyticsSessionId;
                this.surveySessionId = surveySessionId;
            }

            /**
             * Returns the name of the analytics event to log.
             *
             * @return an {@code Optional} describing the analytics event name, or an empty {@code Optional} if not set
             */
            public Optional<String> analyticsEvent() {
                return Optional.ofNullable(analyticsEvent);
            }

            /**
             * Returns the analytics platform session identifier.
             *
             * @return an {@code Optional} describing the analytics session identifier, or an empty {@code Optional} if not set
             */
            public Optional<String> analyticsSessionId() {
                return Optional.ofNullable(analyticsSessionId);
            }

            /**
             * Returns the survey platform session identifier.
             *
             * @return an {@code Optional} describing the survey session identifier, or an empty {@code Optional} if not set
             */
            public Optional<String> surveySessionId() {
                return Optional.ofNullable(surveySessionId);
            }

            /**
             * Sets the name of the analytics event to log.
             *
             * @param analyticsEvent the new analytics event name, or {@code null}
             */
            public void setAnalyticsEvent(String analyticsEvent) {
                this.analyticsEvent = analyticsEvent;
    }

            /**
             * Sets the analytics platform session identifier.
             *
             * @param analyticsSessionId the new analytics session identifier, or {@code null}
             */
            public void setAnalyticsSessionId(String analyticsSessionId) {
                this.analyticsSessionId = analyticsSessionId;
    }

            /**
             * Sets the survey platform session identifier.
             *
             * @param surveySessionId the new survey session identifier, or {@code null}
             */
            public void setSurveySessionId(String surveySessionId) {
                this.surveySessionId = surveySessionId;
    }
        }

        /**
         * A protobuf message containing Meta AI-specific analytics data for a
         * side-by-side survey.
         *
         * <p>This captures detailed event data including CTA impressions, clicks,
         * card impressions, response selections, and survey abandonment events,
         * along with their associated timing and metadata.
         */
        @ProtobufMessage(name = "BotFeedbackMessage.SideBySideSurveyMetadata.SidebySideSurveyMetaAiAnalyticsData")
        public static final class SidebySideSurveyMetaAiAnalyticsData {
            /**
             * The numeric identifier of the survey instance, for example {@code 42}.
             */
            @ProtobufProperty(index = 1, type = ProtobufType.UINT32)
            Integer surveyId;

            /**
             * The identifier of the primary (control) AI response in the A/B test,
             * for example {@code "resp_primary_001"}.
             */
            @ProtobufProperty(index = 2, type = ProtobufType.STRING)
            String primaryResponseId;

            /**
             * The name of the test arm (experiment variant) the user was assigned to,
             * for example {@code "treatment_v2"}.
             */
            @ProtobufProperty(index = 3, type = ProtobufType.STRING)
            String testArmName;

            /**
             * The timestamp in milliseconds when this analytics event occurred,
             * encoded as a string, for example {@code "1700000000000"}.
             */
            @ProtobufProperty(index = 4, type = ProtobufType.STRING)
            String timestampMillis;

            /**
             * Event data recorded when the survey call-to-action was displayed to the user.
             */
            @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
            SideBySideSurveyMetadata.SidebySideSurveyMetaAiAnalyticsData.SideBySideSurveyCTAImpressionEventData ctaImpressionEvent;

            /**
             * Event data recorded when the user clicked the survey call-to-action.
             */
            @ProtobufProperty(index = 6, type = ProtobufType.MESSAGE)
            SideBySideSurveyMetadata.SidebySideSurveyMetaAiAnalyticsData.SideBySideSurveyCTAClickEventData ctaClickEvent;

            /**
             * Event data recorded when the survey comparison card was displayed to the user.
             */
            @ProtobufProperty(index = 7, type = ProtobufType.MESSAGE)
            SideBySideSurveyMetadata.SidebySideSurveyMetaAiAnalyticsData.SideBySideSurveyCardImpressionEventData cardImpressionEvent;

            /**
             * Event data recorded when the user submitted a response selection in the survey.
             */
            @ProtobufProperty(index = 8, type = ProtobufType.MESSAGE)
            SideBySideSurveyMetadata.SidebySideSurveyMetaAiAnalyticsData.SideBySideSurveyResponseEventData responseEvent;

            /**
             * Event data recorded when the user abandoned the survey without completing it.
             */
            @ProtobufProperty(index = 9, type = ProtobufType.MESSAGE)
            SideBySideSurveyMetadata.SidebySideSurveyMetaAiAnalyticsData.SideBySideSurveyAbandonEventData abandonEvent;

            /**
             * Constructs a new {@code SidebySideSurveyMetaAiAnalyticsData} with the specified fields.
             *
             * @param surveyId            the numeric survey identifier
             * @param primaryResponseId   the primary response identifier
             * @param testArmName         the test arm name
             * @param timestampMillis     the event timestamp in milliseconds as a string
             * @param ctaImpressionEvent  the CTA impression event data
             * @param ctaClickEvent       the CTA click event data
             * @param cardImpressionEvent the card impression event data
             * @param responseEvent       the response selection event data
             * @param abandonEvent        the survey abandonment event data
             */
            SidebySideSurveyMetaAiAnalyticsData(Integer surveyId, String primaryResponseId, String testArmName, String timestampMillis, SideBySideSurveyCTAImpressionEventData ctaImpressionEvent, SideBySideSurveyCTAClickEventData ctaClickEvent, SideBySideSurveyCardImpressionEventData cardImpressionEvent, SideBySideSurveyResponseEventData responseEvent, SideBySideSurveyAbandonEventData abandonEvent) {
                this.surveyId = surveyId;
                this.primaryResponseId = primaryResponseId;
                this.testArmName = testArmName;
                this.timestampMillis = timestampMillis;
                this.ctaImpressionEvent = ctaImpressionEvent;
                this.ctaClickEvent = ctaClickEvent;
                this.cardImpressionEvent = cardImpressionEvent;
                this.responseEvent = responseEvent;
                this.abandonEvent = abandonEvent;
            }

            /**
             * Returns the numeric identifier of the survey instance.
             *
             * @return an {@code OptionalInt} describing the survey identifier, or an empty {@code OptionalInt} if not set
             */
            public OptionalInt surveyId() {
                return surveyId == null ? OptionalInt.empty() : OptionalInt.of(surveyId);
            }

            /**
             * Returns the identifier of the primary (control) AI response.
             *
             * @return an {@code Optional} describing the primary response identifier, or an empty {@code Optional} if not set
             */
            public Optional<String> primaryResponseId() {
                return Optional.ofNullable(primaryResponseId);
            }

            /**
             * Returns the name of the test arm (experiment variant) the user was assigned to.
             *
             * @return an {@code Optional} describing the test arm name, or an empty {@code Optional} if not set
             */
            public Optional<String> testArmName() {
                return Optional.ofNullable(testArmName);
            }

            /**
             * Returns the timestamp in milliseconds when this analytics event occurred.
             *
             * @return an {@code Optional} describing the timestamp in milliseconds, or an empty {@code Optional} if not set
             */
            public Optional<String> timestampMillis() {
                return Optional.ofNullable(timestampMillis);
            }

            /**
             * Returns the CTA impression event data.
             *
             * @return an {@code Optional} describing the CTA impression event, or an empty {@code Optional} if not set
             */
            public Optional<SideBySideSurveyCTAImpressionEventData> ctaImpressionEvent() {
                return Optional.ofNullable(ctaImpressionEvent);
            }

            /**
             * Returns the CTA click event data.
             *
             * @return an {@code Optional} describing the CTA click event, or an empty {@code Optional} if not set
             */
            public Optional<SideBySideSurveyCTAClickEventData> ctaClickEvent() {
                return Optional.ofNullable(ctaClickEvent);
            }

            /**
             * Returns the card impression event data.
             *
             * @return an {@code Optional} describing the card impression event, or an empty {@code Optional} if not set
             */
            public Optional<SideBySideSurveyCardImpressionEventData> cardImpressionEvent() {
                return Optional.ofNullable(cardImpressionEvent);
            }

            /**
             * Returns the response selection event data.
             *
             * @return an {@code Optional} describing the response event, or an empty {@code Optional} if not set
             */
            public Optional<SideBySideSurveyResponseEventData> responseEvent() {
                return Optional.ofNullable(responseEvent);
            }

            /**
             * Returns the survey abandonment event data.
             *
             * @return an {@code Optional} describing the abandon event, or an empty {@code Optional} if not set
             */
            public Optional<SideBySideSurveyAbandonEventData> abandonEvent() {
                return Optional.ofNullable(abandonEvent);
            }

            /**
             * Sets the numeric identifier of the survey instance.
             *
             * @param surveyId the new survey identifier, or {@code null}
             */
            public void setSurveyId(Integer surveyId) {
                this.surveyId = surveyId;
    }

            /**
             * Sets the identifier of the primary (control) AI response.
             *
             * @param primaryResponseId the new primary response identifier, or {@code null}
             */
            public void setPrimaryResponseId(String primaryResponseId) {
                this.primaryResponseId = primaryResponseId;
    }

            /**
             * Sets the name of the test arm (experiment variant).
             *
             * @param testArmName the new test arm name, or {@code null}
             */
            public void setTestArmName(String testArmName) {
                this.testArmName = testArmName;
    }

            /**
             * Sets the timestamp in milliseconds when this analytics event occurred.
             *
             * @param timestampMillis the new timestamp in milliseconds, or {@code null}
             */
            public void setTimestampMillis(String timestampMillis) {
                this.timestampMillis = timestampMillis;
    }

            /**
             * Sets the CTA impression event data.
             *
             * @param ctaImpressionEvent the new CTA impression event data, or {@code null}
             */
            public void setCtaImpressionEvent(SideBySideSurveyCTAImpressionEventData ctaImpressionEvent) {
                this.ctaImpressionEvent = ctaImpressionEvent;
    }

            /**
             * Sets the CTA click event data.
             *
             * @param ctaClickEvent the new CTA click event data, or {@code null}
             */
            public void setCtaClickEvent(SideBySideSurveyCTAClickEventData ctaClickEvent) {
                this.ctaClickEvent = ctaClickEvent;
    }

            /**
             * Sets the card impression event data.
             *
             * @param cardImpressionEvent the new card impression event data, or {@code null}
             */
            public void setCardImpressionEvent(SideBySideSurveyCardImpressionEventData cardImpressionEvent) {
                this.cardImpressionEvent = cardImpressionEvent;
    }

            /**
             * Sets the response selection event data.
             *
             * @param responseEvent the new response event data, or {@code null}
             */
            public void setResponseEvent(SideBySideSurveyResponseEventData responseEvent) {
                this.responseEvent = responseEvent;
    }

            /**
             * Sets the survey abandonment event data.
             *
             * @param abandonEvent the new abandon event data, or {@code null}
             */
            public void setAbandonEvent(SideBySideSurveyAbandonEventData abandonEvent) {
                this.abandonEvent = abandonEvent;
    }

            /**
             * A protobuf message containing event data recorded when a user abandons
             * a side-by-side survey without completing it.
             */
            @ProtobufMessage(name = "BotFeedbackMessage.SideBySideSurveyMetadata.SidebySideSurveyMetaAiAnalyticsData.SideBySideSurveyAbandonEventData")
            public static final class SideBySideSurveyAbandonEventData {
                /**
                 * The dwell time in milliseconds before the user abandoned the survey,
                 * encoded as a string, for example {@code "5000"}.
                 */
                @ProtobufProperty(index = 1, type = ProtobufType.STRING)
                String abandonDwellTimeMillis;

                /**
                 * Constructs a new {@code SideBySideSurveyAbandonEventData} with the
                 * specified abandon dwell time.
                 *
                 * @param abandonDwellTimeMillis the dwell time in milliseconds before abandonment
                 */
                SideBySideSurveyAbandonEventData(String abandonDwellTimeMillis) {
                    this.abandonDwellTimeMillis = abandonDwellTimeMillis;
                }

                /**
                 * Returns the dwell time in milliseconds before the user abandoned the survey.
                 *
                 * @return an {@code Optional} describing the abandon dwell time in milliseconds, or an empty {@code Optional} if not set
                 */
                public Optional<String> abandonDwellTimeMillis() {
                    return Optional.ofNullable(abandonDwellTimeMillis);
                }

                /**
                 * Sets the dwell time in milliseconds before the user abandoned the survey.
                 *
                 * @param abandonDwellTimeMillis the new abandon dwell time in milliseconds, or {@code null}
                 */
                public void setAbandonDwellTimeMillis(String abandonDwellTimeMillis) {
                    this.abandonDwellTimeMillis = abandonDwellTimeMillis;
    }
            }

            /**
             * A protobuf message containing event data recorded when a user clicks
             * the call-to-action button of a side-by-side survey.
             */
            @ProtobufMessage(name = "BotFeedbackMessage.SideBySideSurveyMetadata.SidebySideSurveyMetaAiAnalyticsData.SideBySideSurveyCTAClickEventData")
            public static final class SideBySideSurveyCTAClickEventData {
                /**
                 * Whether the survey had expired at the time the user clicked the CTA.
                 */
                @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
                Boolean isSurveyExpired;

                /**
                 * The dwell time in milliseconds before the user clicked the CTA,
                 * encoded as a string, for example {@code "3000"}.
                 */
                @ProtobufProperty(index = 2, type = ProtobufType.STRING)
                String clickDwellTimeMillis;

                /**
                 * Constructs a new {@code SideBySideSurveyCTAClickEventData} with the
                 * specified fields.
                 *
                 * @param isSurveyExpired    whether the survey had expired
                 * @param clickDwellTimeMillis the dwell time in milliseconds before the click
                 */
                SideBySideSurveyCTAClickEventData(Boolean isSurveyExpired, String clickDwellTimeMillis) {
                    this.isSurveyExpired = isSurveyExpired;
                    this.clickDwellTimeMillis = clickDwellTimeMillis;
                }

                /**
                 * Returns whether the survey had expired at the time the user clicked the CTA.
                 *
                 * @return {@code true} if the survey was expired, {@code false} otherwise
                 */
                public boolean isSurveyExpired() {
                    return isSurveyExpired != null && isSurveyExpired;
                }

                /**
                 * Returns the dwell time in milliseconds before the user clicked the CTA.
                 *
                 * @return an {@code Optional} describing the click dwell time in milliseconds, or an empty {@code Optional} if not set
                 */
                public Optional<String> clickDwellTimeMillis() {
                    return Optional.ofNullable(clickDwellTimeMillis);
                }

                /**
                 * Sets whether the survey had expired at the time the user clicked the CTA.
                 *
                 * @param isSurveyExpired the new survey expired flag, or {@code null}
                 */
                public void setSurveyExpired(Boolean isSurveyExpired) {
                    this.isSurveyExpired = isSurveyExpired;
    }

                /**
                 * Sets the dwell time in milliseconds before the user clicked the CTA.
                 *
                 * @param clickDwellTimeMillis the new click dwell time in milliseconds, or {@code null}
                 */
                public void setClickDwellTimeMillis(String clickDwellTimeMillis) {
                    this.clickDwellTimeMillis = clickDwellTimeMillis;
    }
            }

            /**
             * A protobuf message containing event data recorded when the survey
             * comparison card is displayed to the user.
             *
             * <p>This is a marker message with no fields, used to signal that the
             * card impression event occurred.
             */
            @ProtobufMessage(name = "BotFeedbackMessage.SideBySideSurveyMetadata.SidebySideSurveyMetaAiAnalyticsData.SideBySideSurveyCTAImpressionEventData")
            public static final class SideBySideSurveyCTAImpressionEventData {
                /**
                 * Whether the survey had expired at the time the CTA was displayed.
                 */
                @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
                Boolean isSurveyExpired;

                /**
                 * Constructs a new {@code SideBySideSurveyCTAImpressionEventData} with the
                 * specified expiry flag.
                 *
                 * @param isSurveyExpired whether the survey had expired
                 */
                SideBySideSurveyCTAImpressionEventData(Boolean isSurveyExpired) {
                    this.isSurveyExpired = isSurveyExpired;
                }

                /**
                 * Returns whether the survey had expired at the time the CTA was displayed.
                 *
                 * @return {@code true} if the survey was expired, {@code false} otherwise
                 */
                public boolean isSurveyExpired() {
                    return isSurveyExpired != null && isSurveyExpired;
                }

                /**
                 * Sets whether the survey had expired at the time the CTA was displayed.
                 *
                 * @param isSurveyExpired the new survey expired flag, or {@code null}
                 */
                public void setSurveyExpired(Boolean isSurveyExpired) {
                    this.isSurveyExpired = isSurveyExpired;
    }
            }

            /**
             * A protobuf message representing a card impression event in a side-by-side
             * survey.
             *
             * <p>This is a marker message with no fields, used to signal that the
             * survey comparison card was displayed to the user.
             */
            @ProtobufMessage(name = "BotFeedbackMessage.SideBySideSurveyMetadata.SidebySideSurveyMetaAiAnalyticsData.SideBySideSurveyCardImpressionEventData")
            public static final class SideBySideSurveyCardImpressionEventData {
                /**
                 * Constructs a new {@code SideBySideSurveyCardImpressionEventData}.
                 */
                SideBySideSurveyCardImpressionEventData() {
                }
            }

            /**
             * A protobuf message containing event data recorded when a user submits
             * a response selection in a side-by-side survey.
             */
            @ProtobufMessage(name = "BotFeedbackMessage.SideBySideSurveyMetadata.SidebySideSurveyMetaAiAnalyticsData.SideBySideSurveyResponseEventData")
            public static final class SideBySideSurveyResponseEventData {
                /**
                 * The dwell time in milliseconds before the user submitted a response,
                 * encoded as a string, for example {@code "8000"}.
                 */
                @ProtobufProperty(index = 1, type = ProtobufType.STRING)
                String responseDwellTimeMillis;

                /**
                 * The identifier of the AI response selected by the user,
                 * for example {@code "resp_selected_002"}.
                 */
                @ProtobufProperty(index = 2, type = ProtobufType.STRING)
                String selectedResponseId;

                /**
                 * Constructs a new {@code SideBySideSurveyResponseEventData} with the
                 * specified fields.
                 *
                 * @param responseDwellTimeMillis the dwell time in milliseconds before the response
                 * @param selectedResponseId      the identifier of the selected response
                 */
                SideBySideSurveyResponseEventData(String responseDwellTimeMillis, String selectedResponseId) {
                    this.responseDwellTimeMillis = responseDwellTimeMillis;
                    this.selectedResponseId = selectedResponseId;
                }

                /**
                 * Returns the dwell time in milliseconds before the user submitted a response.
                 *
                 * @return an {@code Optional} describing the response dwell time in milliseconds, or an empty {@code Optional} if not set
                 */
                public Optional<String> responseDwellTimeMillis() {
                    return Optional.ofNullable(responseDwellTimeMillis);
                }

                /**
                 * Returns the identifier of the AI response selected by the user.
                 *
                 * @return an {@code Optional} describing the selected response identifier, or an empty {@code Optional} if not set
                 */
                public Optional<String> selectedResponseId() {
                    return Optional.ofNullable(selectedResponseId);
                }

                /**
                 * Sets the dwell time in milliseconds before the user submitted a response.
                 *
                 * @param responseDwellTimeMillis the new response dwell time in milliseconds, or {@code null}
                 */
                public void setResponseDwellTimeMillis(String responseDwellTimeMillis) {
                    this.responseDwellTimeMillis = responseDwellTimeMillis;
    }

                /**
                 * Sets the identifier of the AI response selected by the user.
                 *
                 * @param selectedResponseId the new selected response identifier, or {@code null}
                 */
                public void setSelectedResponseId(String selectedResponseId) {
                    this.selectedResponseId = selectedResponseId;
    }
            }
        }
    }
}
