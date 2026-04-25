package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

/**
 * Enumerates the thread-level action identifiers reported by WAM telemetry for
 * the Meta AI conversation-thread experience on WhatsApp.
 *
 * <p>Each constant carries the fixed integer identifier transmitted on the
 * wire and tags a specific interaction with a Meta AI thread (for example,
 * pinning, renaming, entering or exiting a thread, impressions on the thread
 * list, or clicks from the search surface). Values must never be renumbered
 * or reused.
 *
 * @implNote WAWebWamEnumThreadActionTypes: the module default-exports a single
 *     frozen object {@code THREAD_ACTION_TYPES} whose keys are the action
 *     names and whose values are the integer identifiers; Cobalt mirrors the
 *     full enumeration with {@link WamEnumConstant} preserving each numeric
 *     value.
 */
@WamEnum
@WhatsAppWebModule(moduleName = "WAWebWamEnumThreadActionTypes")
public enum ThreadActionTypes {
    /**
     * User pinned a Meta AI conversation thread.
     *
     * @implNote WAWebWamEnumThreadActionTypes.THREAD_ACTION_TYPES: {@code PIN = 1}.
     */
    @WamEnumConstant(1)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumThreadActionTypes",
            exports = "THREAD_ACTION_TYPES",
            adaptation = WhatsAppAdaptation.DIRECT)
    PIN,

    /**
     * User unpinned a Meta AI conversation thread.
     *
     * @implNote WAWebWamEnumThreadActionTypes.THREAD_ACTION_TYPES: {@code UNPIN = 2}.
     */
    @WamEnumConstant(2)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumThreadActionTypes",
            exports = "THREAD_ACTION_TYPES",
            adaptation = WhatsAppAdaptation.DIRECT)
    UNPIN,

    /**
     * User deleted a Meta AI conversation thread.
     *
     * @implNote WAWebWamEnumThreadActionTypes.THREAD_ACTION_TYPES: {@code DELETE = 3}.
     */
    @WamEnumConstant(3)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumThreadActionTypes",
            exports = "THREAD_ACTION_TYPES",
            adaptation = WhatsAppAdaptation.DIRECT)
    DELETE,

    /**
     * User renamed a Meta AI conversation thread.
     *
     * @implNote WAWebWamEnumThreadActionTypes.THREAD_ACTION_TYPES: {@code RENAME = 4}.
     */
    @WamEnumConstant(4)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumThreadActionTypes",
            exports = "THREAD_ACTION_TYPES",
            adaptation = WhatsAppAdaptation.DIRECT)
    RENAME,

    /**
     * User clicked the new-chat affordance to start a fresh Meta AI thread.
     *
     * @implNote WAWebWamEnumThreadActionTypes.THREAD_ACTION_TYPES: {@code CLICK_NEW_CHAT = 5}.
     */
    @WamEnumConstant(5)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumThreadActionTypes",
            exports = "THREAD_ACTION_TYPES",
            adaptation = WhatsAppAdaptation.DIRECT)
    CLICK_NEW_CHAT,

    /**
     * User clicked the chat-history entry to resume a prior Meta AI thread.
     *
     * @implNote WAWebWamEnumThreadActionTypes.THREAD_ACTION_TYPES: {@code CLICK_CHAT_HISTORY = 6}.
     */
    @WamEnumConstant(6)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumThreadActionTypes",
            exports = "THREAD_ACTION_TYPES",
            adaptation = WhatsAppAdaptation.DIRECT)
    CLICK_CHAT_HISTORY,

    /**
     * User clicked a specific conversation thread from the list of threads.
     *
     * @implNote WAWebWamEnumThreadActionTypes.THREAD_ACTION_TYPES: {@code CLICK_CONVERSATION_THREAD = 7}.
     */
    @WamEnumConstant(7)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumThreadActionTypes",
            exports = "THREAD_ACTION_TYPES",
            adaptation = WhatsAppAdaptation.DIRECT)
    CLICK_CONVERSATION_THREAD,

    /**
     * User entered a Meta AI conversation thread surface.
     *
     * @implNote WAWebWamEnumThreadActionTypes.THREAD_ACTION_TYPES: {@code THREAD_ENTER = 8}.
     */
    @WamEnumConstant(8)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumThreadActionTypes",
            exports = "THREAD_ACTION_TYPES",
            adaptation = WhatsAppAdaptation.DIRECT)
    THREAD_ENTER,

    /**
     * User exited a Meta AI conversation thread surface.
     *
     * @implNote WAWebWamEnumThreadActionTypes.THREAD_ACTION_TYPES: {@code THREAD_EXIT = 9}.
     */
    @WamEnumConstant(9)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumThreadActionTypes",
            exports = "THREAD_ACTION_TYPES",
            adaptation = WhatsAppAdaptation.DIRECT)
    THREAD_EXIT,

    /**
     * The thread-list surface was rendered and recorded as an impression.
     *
     * @implNote WAWebWamEnumThreadActionTypes.THREAD_ACTION_TYPES: {@code THREAD_LIST_IMPRESSION = 10}.
     */
    @WamEnumConstant(10)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumThreadActionTypes",
            exports = "THREAD_ACTION_TYPES",
            adaptation = WhatsAppAdaptation.DIRECT)
    THREAD_LIST_IMPRESSION,

    /**
     * User opened the three-dot overflow menu on a thread.
     *
     * @implNote WAWebWamEnumThreadActionTypes.THREAD_ACTION_TYPES: {@code THREE_DOT_MENU = 11}.
     */
    @WamEnumConstant(11)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumThreadActionTypes",
            exports = "THREAD_ACTION_TYPES",
            adaptation = WhatsAppAdaptation.DIRECT)
    THREE_DOT_MENU,

    /**
     * User sent the first prompt that created the thread.
     *
     * @implNote WAWebWamEnumThreadActionTypes.THREAD_ACTION_TYPES: {@code FIRST_PROMPT_SENT = 12}.
     */
    @WamEnumConstant(12)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumThreadActionTypes",
            exports = "THREAD_ACTION_TYPES",
            adaptation = WhatsAppAdaptation.DIRECT)
    FIRST_PROMPT_SENT,

    /**
     * User clicked a search result that refers to a thread or message in it.
     *
     * @implNote WAWebWamEnumThreadActionTypes.THREAD_ACTION_TYPES: {@code SEARCH_RESULT_CLICK = 13}.
     */
    @WamEnumConstant(13)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumThreadActionTypes",
            exports = "THREAD_ACTION_TYPES",
            adaptation = WhatsAppAdaptation.DIRECT)
    SEARCH_RESULT_CLICK,

    /**
     * A search result referring to a thread was rendered and recorded as shown.
     *
     * @implNote WAWebWamEnumThreadActionTypes.THREAD_ACTION_TYPES: {@code SEARCH_RESULT_SHOWN = 14}.
     */
    @WamEnumConstant(14)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumThreadActionTypes",
            exports = "THREAD_ACTION_TYPES",
            adaptation = WhatsAppAdaptation.DIRECT)
    SEARCH_RESULT_SHOWN,

    /**
     * The Meta AI home surface was rendered and recorded as an impression.
     *
     * @implNote WAWebWamEnumThreadActionTypes.THREAD_ACTION_TYPES: {@code AI_HOME_IMPRESSION = 15}.
     */
    @WamEnumConstant(15)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumThreadActionTypes",
            exports = "THREAD_ACTION_TYPES",
            adaptation = WhatsAppAdaptation.DIRECT)
    AI_HOME_IMPRESSION,

    /**
     * User clicked the continue-chat module to resume an existing thread.
     *
     * @implNote WAWebWamEnumThreadActionTypes.THREAD_ACTION_TYPES: {@code CLICK_CONTINUE_CHAT_MODULE = 16}.
     */
    @WamEnumConstant(16)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumThreadActionTypes",
            exports = "THREAD_ACTION_TYPES",
            adaptation = WhatsAppAdaptation.DIRECT)
    CLICK_CONTINUE_CHAT_MODULE
}
