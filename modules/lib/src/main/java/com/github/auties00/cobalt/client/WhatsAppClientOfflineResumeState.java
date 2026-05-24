package com.github.auties00.cobalt.client;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Tracks the four states of the offline-stanza replay that runs after a
 * web companion socket connects or reconnects.
 *
 * @apiNote
 * Listeners that need to defer actions while the server is replaying
 * queued stanzas (chat-list re-sorts, collection flushes, immediate
 * device syncs) read this value through the client. WA Web's
 * {@code WAWebBlockingOfflineResumeManager} and
 * {@code WAWebNonBlockingOfflineResumeManager} drive the same four
 * states; Cobalt collapses both managers into a single state machine.
 *
 * @implNote
 * This implementation mirrors the four-state {@code ResumeStatus}
 * enum exported by {@code WAWebOfflineResumeConst} verbatim. The
 * companion constants exposed alongside the enum (timeouts, batch
 * limits, debounce windows) are reproduced as
 * {@code public static final} fields on this enum so consumers have a
 * single import point per WA module.
 *
 * @see WhatsAppClient
 */
@WhatsAppWebModule(moduleName = "WAWebOfflineResumeConst")
@WhatsAppWebExport(
        moduleName = "WAWebOfflineResumeConst",
        exports = "ResumeStatus",
        adaptation = WhatsAppAdaptation.DIRECT)
public enum WhatsAppClientOfflineResumeState {
    /**
     * The state of a freshly constructed client before any
     * {@code offline_preview} info bulletin has arrived.
     *
     * @apiNote
     * Initial value persisted into the client until the first
     * post-connect IB lands. Drives
     * {@code isResumeFromRestartInProgress} and
     * {@code isResumeFromRestartComplete} parity checks against WA Web.
     */
    INIT,

    /**
     * The client is replaying the backlog queued on the server during
     * the cold start after a process restart.
     *
     * @apiNote
     * During this phase chat-sort listeners are disabled and collection
     * flushes are deferred so the in-flight replay completes
     * deterministically. WA Web matches this on
     * {@code ResumeStatus.RESUME_ON_RESTART}.
     */
    RESUME_ON_RESTART,

    /**
     * The client is replaying the backlog queued on the server after a
     * live socket reconnect while the runtime stayed up.
     *
     * @apiNote
     * Distinct from {@link #RESUME_ON_RESTART} because the surrounding
     * runtime is already initialised. WA Web's
     * {@code isResumeOnSocketDisconnectInProgress} is exactly this
     * state on both the blocking and non-blocking offline-resume
     * managers; on entry chat-sort listeners are disabled and a pending
     * device sync is scheduled to run after
     * {@link #OFFLINE_DEVICE_SYNC_DELAY}.
     */
    RESUME_WITH_OPEN_TAB,

    /**
     * The replay has drained and the client is operating in real time.
     *
     * @apiNote
     * Reached either when the server signals the {@code offline}
     * info bulletin or when the local stale-stream watchdog fires
     * after {@link #OFFLINE_STANZA_TIMEOUT_MS}. Pending device sync has
     * run by this point; deferred operations may proceed.
     */
    COMPLETE;

    /**
     * Upper bound, in milliseconds, on how long the offline-resume
     * manager waits for the next offline stanza before treating the
     * current stanza count as stale and refreshing the window from the
     * server.
     *
     * @apiNote
     * Matches the {@code l=2e4} constant in
     * {@code WAWebOfflineResumeConst}. WA Web schedules a
     * {@code self.setTimeout} that fires
     * {@code WAWebOfflineResumeUtils.refreshWindow()} once this elapses
     * without progress; the same timeout is re-armed after every batch.
     */
    @WhatsAppWebExport(
            moduleName = "WAWebOfflineResumeConst",
            exports = "OFFLINE_STANZA_COUNT_CHECK_TIMEOUT_MS",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final long OFFLINE_STANZA_COUNT_CHECK_TIMEOUT_MS = 20_000L;

    /**
     * Soft cap, in stanzas, on how many offline messages the resume
     * manager processes before it logs a warning that the count exceeded
     * the announced offline window.
     *
     * @apiNote
     * Matches the {@code s=100} constant in
     * {@code WAWebOfflineResumeConst}. Both blocking and non-blocking
     * managers compare their per-resume counter against this value and
     * call {@code WALogger.WARN} when it is exceeded; the resume itself
     * is not aborted.
     */
    @WhatsAppWebExport(
            moduleName = "WAWebOfflineResumeConst",
            exports = "OFFLINE_STANZA_COUNT_LIMIT",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int OFFLINE_STANZA_COUNT_LIMIT = 100;

    /**
     * Delay, in milliseconds, applied before running the pending device
     * sync once the offline backlog has drained.
     *
     * @apiNote
     * Matches the {@code u=2e3} constant in
     * {@code WAWebOfflineResumeConst}. WA Web's blocking and
     * non-blocking managers both schedule
     * {@code WAWebApiPendingDeviceSync.doPendingDeviceSync()} after this
     * delay on the transition into {@link #RESUME_WITH_OPEN_TAB} and on
     * the transition into {@link #COMPLETE}.
     */
    @WhatsAppWebExport(
            moduleName = "WAWebOfflineResumeConst",
            exports = "OFFLINE_DEVICE_SYNC_DELAY",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final long OFFLINE_DEVICE_SYNC_DELAY = 2_000L;

    /**
     * Stale-stream watchdog, in milliseconds, after which the
     * offline-resume manager forces the state to {@link #COMPLETE} and
     * logs a missed-offline-complete event.
     *
     * @apiNote
     * Matches the {@code c=6e4} constant in
     * {@code WAWebOfflineResumeConst}. Each new offline stanza calls
     * {@code ShiftTimer.onOrAfter(this)} so the watchdog slides forward
     * with traffic; it only fires when the stream goes silent for a
     * full minute.
     */
    @WhatsAppWebExport(
            moduleName = "WAWebOfflineResumeConst",
            exports = "OFFLINE_STANZA_TIMEOUT_MS",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final long OFFLINE_STANZA_TIMEOUT_MS = 60_000L;

    /**
     * Debounce window, in milliseconds, during which a fresh
     * {@code offline_preview} IB is treated as an update to the same
     * resume rather than a new one.
     *
     * @apiNote
     * Matches the {@code d=1e3} constant in
     * {@code WAWebOfflineResumeConst}. When two previews arrive within
     * the window WA Web logs an
     * "Accept multiple offline previews" message and merges the new
     * counts into the running resume; outside the window the second
     * preview restarts the state machine.
     */
    @WhatsAppWebExport(
            moduleName = "WAWebOfflineResumeConst",
            exports = "OFFLINE_PREVIEW_PERIOD_MS",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final long OFFLINE_PREVIEW_PERIOD_MS = 1_000L;

    /**
     * Minimum interval, in milliseconds, between offline-resume UI
     * progress updates pushed to the chat-list surface.
     *
     * @apiNote
     * Matches the {@code m=1e3} constant in
     * {@code WAWebOfflineResumeConst}. WA Web throttles its progress-bar
     * updates with a {@code ShiftTimer.onOrAfter(this)} so the UI does
     * not redraw faster than once per second during a flood of stanzas.
     */
    @WhatsAppWebExport(
            moduleName = "WAWebOfflineResumeConst",
            exports = "UI_UPDATE_TIME_MS",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final long UI_UPDATE_TIME_MS = 1_000L;
}
