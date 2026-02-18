package com.github.auties00.cobalt.model.bot.metrics;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;

/**
 * A UI surface or navigation path from which a user can initiate a bot
 * interaction on WhatsApp.
 *
 * <p>Each constant represents a distinct entry point in the WhatsApp client
 * where the user tapped, clicked, or otherwise triggered a conversation with
 * Meta AI. The entry point is recorded in
 * {@link BotMetricsMetadata#destinationEntryPoint()} for telemetry purposes.
 *
 * <p>Entry points span several categories:
 * <ul>
 * <li>Direct navigation — {@link #FAVICON}, {@link #CHATLIST},
 *     {@link #DEEPLINK}, {@link #NOTIFICATION}, {@link #APP_SHORTCUT}
 * <li>AI search surfaces — {@link #AISEARCH_NULL_STATE_PAPER_PLANE},
 *     {@link #AISEARCH_NULL_STATE_SUGGESTION},
 *     {@link #AISEARCH_TYPE_AHEAD_SUGGESTION} and related
 * <li>AI voice — {@link #AIVOICE_SEARCH_BAR}, {@link #AIVOICE_FAVICON},
 *     {@link #AIVOICE_FAVICON_CALL_HISTORY}
 * <li>AI tabs and home — {@link #AI_TAB}, {@link #AI_HOME},
 *     {@link #AI_DEEPLINK}, {@link #AI_DEEPLINK_IMMERSIVE}
 * <li>Context menus — {@link #ASK_META_AI_CONTEXT_MENU},
 *     {@link #ASK_META_AI_CONTEXT_MENU_1ON1},
 *     {@link #ASK_META_AI_CONTEXT_MENU_GROUP}
 * <li>Message actions — {@link #MESSAGE_QUICK_ACTION_1_ON_1_CHAT},
 *     {@link #MESSAGE_QUICK_ACTION_GROUP_CHAT}
 * <li>Media surfaces — {@link #ATTACHMENT_TRAY_1_ON_1_CHAT},
 *     {@link #MEDIA_PICKER_1_ON_1_CHAT},
 *     {@link #ASK_META_AI_MEDIA_VIEWER_1ON1} and related
 * <li>AI Studio — {@link #AISTUDIO},
 *     {@link #META_AI_CHAT_SHORTCUT_AI_STUDIO},
 *     {@link #UGC_CHAT_SHORTCUT_AI_STUDIO}, {@link #NEW_CHAT_AI_STUDIO}
 * <li>Other — {@link #FORWARD}, {@link #META_AI_FORWARD},
 *     {@link #PROFILE_MESSAGE_BUTTON}, {@link #META_AI_SETTINGS}
 * </ul>
 */
@ProtobufEnum(name = "BotMetricsEntryPoint")
public enum BotMetricsEntryPoint {
    /**
     * The entry point is not defined or not recognized.
     */
    UNDEFINED_ENTRY_POINT(0),

    /**
     * The bot favicon (small icon) displayed in the chat list or header.
     */
    FAVICON(1),

    /**
     * The main chat list surface.
     */
    CHATLIST(2),

    /**
     * The paper-plane (send) button in the AI search null state.
     */
    AISEARCH_NULL_STATE_PAPER_PLANE(3),

    /**
     * A suggested prompt shown in the AI search null state.
     */
    AISEARCH_NULL_STATE_SUGGESTION(4),

    /**
     * A type-ahead suggestion in the AI search bar.
     */
    AISEARCH_TYPE_AHEAD_SUGGESTION(5),

    /**
     * The paper-plane (send) button in the AI search type-ahead state.
     */
    AISEARCH_TYPE_AHEAD_PAPER_PLANE(6),

    /**
     * A chat-list result from AI search type-ahead.
     */
    AISEARCH_TYPE_AHEAD_RESULT_CHATLIST(7),

    /**
     * A message result from AI search type-ahead.
     */
    AISEARCH_TYPE_AHEAD_RESULT_MESSAGES(8),

    /**
     * The AI voice button in the search bar.
     */
    AIVOICE_SEARCH_BAR(9),

    /**
     * The AI voice favicon in the main UI.
     */
    AIVOICE_FAVICON(10),

    /**
     * The AI Studio creation/management surface.
     */
    AISTUDIO(11),

    /**
     * A deep link URL that opens a bot conversation.
     */
    DEEPLINK(12),

    /**
     * A push notification that leads to a bot conversation.
     */
    NOTIFICATION(13),

    /**
     * The "Message" button on a bot's profile page.
     */
    PROFILE_MESSAGE_BUTTON(14),

    /**
     * A forwarded message action that triggers a bot interaction.
     */
    FORWARD(15),

    /**
     * An app shortcut (e.g. long-press home icon) that opens the bot.
     */
    APP_SHORTCUT(16),

    /**
     * The Family Features (FF) family entry point.
     */
    FF_FAMILY(17),

    /**
     * The dedicated AI tab in the bottom navigation bar.
     */
    AI_TAB(18),

    /**
     * The AI home screen surface.
     */
    AI_HOME(19),

    /**
     * An immersive deep link that opens the AI in a full-screen view.
     */
    AI_DEEPLINK_IMMERSIVE(20),

    /**
     * A standard deep link that opens the AI conversation.
     */
    AI_DEEPLINK(21),

    /**
     * The Meta AI chat shortcut within AI Studio.
     */
    META_AI_CHAT_SHORTCUT_AI_STUDIO(22),

    /**
     * A user-generated content (UGC) bot chat shortcut in AI Studio.
     */
    UGC_CHAT_SHORTCUT_AI_STUDIO(23),

    /**
     * The "New Chat" action within AI Studio.
     */
    NEW_CHAT_AI_STUDIO(24),

    /**
     * The AI voice favicon in the call history screen.
     */
    AIVOICE_FAVICON_CALL_HISTORY(25),

    /**
     * The "Ask Meta AI" option in a generic context menu.
     */
    ASK_META_AI_CONTEXT_MENU(26),

    /**
     * The "Ask Meta AI" option in a 1-on-1 chat context menu.
     */
    ASK_META_AI_CONTEXT_MENU_1ON1(27),

    /**
     * The "Ask Meta AI" option in a group chat context menu.
     */
    ASK_META_AI_CONTEXT_MENU_GROUP(28),

    /**
     * Invoking Meta AI from within a 1-on-1 chat.
     */
    INVOKE_META_AI_1ON1(29),

    /**
     * Invoking Meta AI from within a group chat.
     */
    INVOKE_META_AI_GROUP(30),

    /**
     * Forwarding a message to Meta AI.
     */
    META_AI_FORWARD(31),

    /**
     * Starting a new chat with an AI contact.
     */
    NEW_CHAT_AI_CONTACT(32),

    /**
     * A quick-action button on a message in a 1-on-1 chat.
     */
    MESSAGE_QUICK_ACTION_1_ON_1_CHAT(33),

    /**
     * A quick-action button on a message in a group chat.
     */
    MESSAGE_QUICK_ACTION_GROUP_CHAT(34),

    /**
     * The attachment tray in a 1-on-1 chat.
     */
    ATTACHMENT_TRAY_1_ON_1_CHAT(35),

    /**
     * The attachment tray in a group chat.
     */
    ATTACHMENT_TRAY_GROUP_CHAT(36),

    /**
     * The "Ask Meta AI" option in the media viewer for a 1-on-1 chat.
     */
    ASK_META_AI_MEDIA_VIEWER_1ON1(37),

    /**
     * The "Ask Meta AI" option in the media viewer for a group chat.
     */
    ASK_META_AI_MEDIA_VIEWER_GROUP(38),

    /**
     * The media picker in a 1-on-1 chat.
     */
    MEDIA_PICKER_1_ON_1_CHAT(39),

    /**
     * The media picker in a group chat.
     */
    MEDIA_PICKER_GROUP_CHAT(40),

    /**
     * The "Ask Meta AI" prompt shown when a regular search yields no results.
     */
    ASK_META_AI_NO_SEARCH_RESULTS(41),

    /**
     * The Meta AI settings page.
     */
    META_AI_SETTINGS(45);

    /**
     * Constructs a new entry point constant with the specified protobuf index.
     *
     * @param index the protobuf enum index
     */
    BotMetricsEntryPoint(@ProtobufEnumIndex int index) {
        this.index = index;
    }

    final int index;

    /**
     * Returns the protobuf enum index of this entry point.
     *
     * @return the protobuf index
     */
    public int index() {
        return this.index;
    }
}
