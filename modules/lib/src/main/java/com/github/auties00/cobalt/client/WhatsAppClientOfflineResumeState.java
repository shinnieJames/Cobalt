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
 * in-flight replay can complete deterministically. Once the
 * {@code offline_stanza_complete} marker arrives, the state moves to
 * {@link #COMPLETE} and normal real-time operation resumes.
 *
 * <p>This type is the Cobalt adaptation of the {@code ResumeStatus}
 * "mirrored enum" exported by the WA Web module {@code WAWebOfflineResumeConst}
 * (declared via {@code $InternalEnum.Mirrored(["INIT", "RESUME_ON_RESTART",
 * "RESUME_WITH_OPEN_TAB", "COMPLETE"])}). The six numeric constants exported
 * alongside {@code ResumeStatus} by the same JS module are reproduced as
 * {@code public static final} fields on this type so that every consumer
 * that depends on the module has a single Cobalt home to import from.
 *
 * @implNote WAWebOfflineResumeConst.ResumeStatus mirrors the four-state
 * machine defined in WhatsApp Web's offline resume manager. Cobalt uses this
 * state to gate the same operations that WA Web gates via
 * {@code WAWebBlockingOfflineResumeManager} and
 * {@code WAWebNonBlockingOfflineResumeManager}.
 */
@WhatsAppWebModule(moduleName = "WAWebOfflineResumeConst")
public enum WhatsAppClientOfflineResumeState {
    /**
     * Initial state before any offline resume activity has started.
     *
     * <p>This is the state of a freshly constructed {@link WhatsAppClient}
     * and of a reconnecting client that has not yet received the
     * {@code offline_preview} IB from the server.
     *
     * @implNote WAWebOfflineResumeConst.ResumeStatus.INIT: the default value
     * assigned by {@code OfflineBlockingResumeStageManager.$10} on
     * construction.
     */
    INIT,

    /**
     * The client is actively consuming offline stanzas queued on the server
     * after a cold (re)start.
     *
     * <p>During this phase the socket is delivering a backlog of messages,
     * receipts and notifications; collection flushes and chat list sorting
     * are temporarily disabled to avoid spurious intermediate states.
     *
     * @implNote WAWebOfflineResumeConst.ResumeStatus.RESUME_ON_RESTART: set
     * by {@code OfflineBlockingResumeStageManager.processOfflinePreview} after
     * the offline preview IB arrives during a cold (re)start.
     */
    RESUME_ON_RESTART,

    /**
     * The client is consuming offline stanzas queued on the server after a
     * live socket disconnect/reconnect, while the tab (or process) was
     * already running.
     *
     * <p>This state is distinguished from {@link #RESUME_ON_RESTART} because
     * the surrounding runtime is already fully initialised: chat sort
     * listeners must be disabled for the duration, a pending device sync is
     * scheduled, and {@code isResumeOnSocketDisconnectInProgress} is defined
     * as exactly this state on both the blocking and non-blocking WA Web
     * offline resume managers.
     *
     * @implNote WAWebOfflineResumeConst.ResumeStatus.RESUME_WITH_OPEN_TAB:
     * set by {@code WAWebBlockingOfflineResumeManager} and
     * {@code WAWebNonBlockingOfflineResumeManager} on
     * {@code processOfflinePreview} when the preview arrives on an
     * already-open tab, after calling
     * {@code o("WAWebJSHaltDetector").jsHaltDetector.restartDetection()} and
     * before disabling the chat sort listener via
     * {@code o("WAWebBackendApi").frontendFireAndForget("updateChatSortListener",
     * {enable:!1})}.
     */
    RESUME_WITH_OPEN_TAB,

    /**
     * Offline resume is finished and the client is operating in real time.
     *
     * <p>All offline stanzas have been delivered, the pending device sync
     * has been performed, and deferred operations can proceed normally.
     *
     * @implNote WAWebOfflineResumeConst.ResumeStatus.COMPLETE: set by
     * {@code OfflineBlockingResumeStageManager.processOfflineSessionComplete}
     * once the offline backlog and the per-message queue have both drained.
     */
    COMPLETE;

    /**
     * Upper bound, in milliseconds, on how long the offline resume manager
     * waits for the next offline stanza to arrive before treating the current
     * count as stale and requesting a refreshed window from the server.
     *
     * <p>Consumed by {@code WAWebBlockingOfflineResumeManager} and
     * {@code WAWebNonBlockingOfflineResumeManager} as the timeout argument to
     * the stanza-count check timer that runs during an active offline resume.
     *
     * @implNote WAWebOfflineResumeConst.OFFLINE_STANZA_COUNT_CHECK_TIMEOUT_MS:
     * {@code 2e4} (20000 ms) in the WA Web source.
     */
    @WhatsAppWebExport(
            moduleName = "WAWebOfflineResumeConst",
            exports = "OFFLINE_STANZA_COUNT_CHECK_TIMEOUT_MS",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final long OFFLINE_STANZA_COUNT_CHECK_TIMEOUT_MS = 20_000L;

    /**
     * Maximum number of offline stanzas that the offline resume manager will
     * process in a single batch before yielding control and reporting
     * progress.
     *
     * <p>Used by the offline resume managers as the batch size for streamed
     * stanza delivery during an active offline resume.
     *
     * @implNote WAWebOfflineResumeConst.OFFLINE_STANZA_COUNT_LIMIT:
     * {@code 100} in the WA Web source.
     */
    @WhatsAppWebExport(
            moduleName = "WAWebOfflineResumeConst",
            exports = "OFFLINE_STANZA_COUNT_LIMIT",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int OFFLINE_STANZA_COUNT_LIMIT = 100;

    /**
     * Delay, in milliseconds, applied before running the pending device sync
     * once the offline backlog has drained.
     *
     * <p>Consumed by {@code WAWebBlockingOfflineResumeManager} and
     * {@code WAWebNonBlockingOfflineResumeManager} as the {@code setTimeout}
     * delay wrapping the call to
     * {@code WAWebApiPendingDeviceSync.doPendingDeviceSync()} after the
     * offline resume transitions to {@link #COMPLETE}.
     *
     * @implNote WAWebOfflineResumeConst.OFFLINE_DEVICE_SYNC_DELAY:
     * {@code 2e3} (2000 ms) in the WA Web source.
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
     *
     * <p>Consumed by {@code WAWebBlockingOfflineResumeManager.$13} /
     * {@code WAWebNonBlockingOfflineResumeManager.$16} as the
     * {@code onOrAfter} expiration value for the stall-detection
     * {@code ShiftTimer}.
     *
     * @implNote WAWebOfflineResumeConst.OFFLINE_STANZA_TIMEOUT_MS:
     * {@code 6e4} (60000 ms) in the WA Web source.
     */
    @WhatsAppWebExport(
            moduleName = "WAWebOfflineResumeConst",
            exports = "OFFLINE_STANZA_TIMEOUT_MS",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final long OFFLINE_STANZA_TIMEOUT_MS = 60_000L;

    /**
     * Debounce window, in milliseconds, during which repeated
     * {@code offline_preview} IBs are accepted as updates to the same ongoing
     * offline resume rather than treated as a new resume attempt.
     *
     * <p>Consumed by {@code WAWebBlockingOfflineResumeManager} and
     * {@code WAWebNonBlockingOfflineResumeManager} to decide whether an
     * incoming preview arrived within the debounce window of the previous
     * one ({@code now - prev < OFFLINE_PREVIEW_PERIOD_MS}).
     *
     * @implNote WAWebOfflineResumeConst.OFFLINE_PREVIEW_PERIOD_MS:
     * {@code 1e3} (1000 ms) in the WA Web source.
     */
    @WhatsAppWebExport(
            moduleName = "WAWebOfflineResumeConst",
            exports = "OFFLINE_PREVIEW_PERIOD_MS",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final long OFFLINE_PREVIEW_PERIOD_MS = 1_000L;

    /**
     * Minimum interval, in milliseconds, between offline-resume UI progress
     * updates pushed to the chat list surface.
     *
     * <p>Consumed by {@code WAWebBlockingOfflineResumeManager} and
     * {@code WAWebNonBlockingOfflineResumeManager} as the {@code onOrAfter}
     * throttle interval on the UI progress-bar {@code ShiftTimer} that
     * broadcasts {@code triggerOfflineProgressUpdateFromBridge} events.
     *
     * @implNote WAWebOfflineResumeConst.UI_UPDATE_TIME_MS:
     * {@code 1e3} (1000 ms) in the WA Web source.
     */
    @WhatsAppWebExport(
            moduleName = "WAWebOfflineResumeConst",
            exports = "UI_UPDATE_TIME_MS",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final long UI_UPDATE_TIME_MS = 1_000L;
}
