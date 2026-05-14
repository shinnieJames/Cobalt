package com.github.auties00.cobalt.client;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Tracks the progress of the offline message resume phase that runs
 * immediately after a WhatsApp Web client connects or reconnects.
 *
 * <p>When a web companion re-establishes a socket, the server announces how
 * many queued stanzas it will replay with an {@code offline_preview} IB.
 * Until that backlog has been fully delivered, the client is in the middle
 * of an offline resume: certain operations such as immediate device syncs,
 * chat list re-sorting and collection flushes must be deferred so that the
 * in-flight replay can complete deterministically. Once the {@code offline}
 * info bulletin arrives carrying the final delivered count, the state moves
 * to {@link #COMPLETE} and normal real-time operation resumes.
 *
 * <p>This type is the Cobalt adaptation of the {@code ResumeStatus}
 * mirrored enum exported by {@code WAWebOfflineResumeConst}. The six
 * numeric constants exported alongside {@code ResumeStatus} by the same
 * JS module are reproduced as {@code public static final} fields on this
 * type so that every consumer that depends on the module has a single
 * Cobalt home to import from.
 */
@WhatsAppWebModule(moduleName = "WAWebOfflineResumeConst")
@WhatsAppWebExport(
        moduleName = "WAWebOfflineResumeConst",
        exports = "ResumeStatus",
        adaptation = WhatsAppAdaptation.DIRECT)
public enum WhatsAppClientOfflineResumeState {
    /**
     * Initial state before any offline resume activity has started.
     *
     * <p>This is the state of a freshly constructed {@link WhatsAppClient}
     * that has not yet received the {@code offline_preview} IB from the
     * server.
     */
    INIT,

    /**
     * The client is actively consuming offline stanzas queued on the
     * server after a cold (re)start.
     *
     * <p>During this phase the socket is delivering a backlog of messages,
     * receipts and notifications. Collection flushes and chat list sorting
     * are temporarily disabled to avoid spurious intermediate states.
     */
    RESUME_ON_RESTART,

    /**
     * The client is consuming offline stanzas queued on the server after a
     * live socket disconnect or reconnect while the process was already
     * running.
     *
     * <p>This state is distinguished from {@link #RESUME_ON_RESTART}
     * because the surrounding runtime is already fully initialised. Chat
     * sort listeners are disabled for the duration and a pending device
     * sync is scheduled. WA Web defines
     * {@code isResumeOnSocketDisconnectInProgress} as exactly this state
     * on both the blocking and non-blocking offline resume managers.
     */
    RESUME_WITH_OPEN_TAB,

    /**
     * Offline resume is finished and the client is operating in real time.
     *
     * <p>All offline stanzas have been delivered, the pending device sync
     * has been performed, and deferred operations can proceed normally.
     */
    COMPLETE;

    /**
     * Upper bound, in milliseconds, on how long the offline resume manager
     * waits for the next offline stanza to arrive before treating the
     * current count as stale and requesting a refreshed window from the
     * server.
     */
    @WhatsAppWebExport(
            moduleName = "WAWebOfflineResumeConst",
            exports = "OFFLINE_STANZA_COUNT_CHECK_TIMEOUT_MS",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final long OFFLINE_STANZA_COUNT_CHECK_TIMEOUT_MS = 20_000L;

    /**
     * Maximum number of offline stanzas that the offline resume manager
     * processes in a single batch before yielding control and reporting
     * progress.
     */
    @WhatsAppWebExport(
            moduleName = "WAWebOfflineResumeConst",
            exports = "OFFLINE_STANZA_COUNT_LIMIT",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int OFFLINE_STANZA_COUNT_LIMIT = 100;

    /**
     * Delay, in milliseconds, applied before running the pending device
     * sync once the offline backlog has drained and the resume transitions
     * to {@link #COMPLETE}.
     */
    @WhatsAppWebExport(
            moduleName = "WAWebOfflineResumeConst",
            exports = "OFFLINE_DEVICE_SYNC_DELAY",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final long OFFLINE_DEVICE_SYNC_DELAY = 2_000L;

    /**
     * Upper bound, in milliseconds, on how long the offline resume manager
     * will wait for the offline stanza stream to advance before logging a
     * missed-offline-complete event and forcing the resume state to
     * {@link #COMPLETE}.
     */
    @WhatsAppWebExport(
            moduleName = "WAWebOfflineResumeConst",
            exports = "OFFLINE_STANZA_TIMEOUT_MS",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final long OFFLINE_STANZA_TIMEOUT_MS = 60_000L;

    /**
     * Debounce window, in milliseconds, during which repeated
     * {@code offline_preview} IBs are accepted as updates to the same
     * ongoing offline resume rather than treated as a new resume attempt.
     */
    @WhatsAppWebExport(
            moduleName = "WAWebOfflineResumeConst",
            exports = "OFFLINE_PREVIEW_PERIOD_MS",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final long OFFLINE_PREVIEW_PERIOD_MS = 1_000L;

    /**
     * Minimum interval, in milliseconds, between offline-resume UI
     * progress updates pushed to the chat list surface.
     */
    @WhatsAppWebExport(
            moduleName = "WAWebOfflineResumeConst",
            exports = "UI_UPDATE_TIME_MS",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final long UI_UPDATE_TIME_MS = 1_000L;
}
