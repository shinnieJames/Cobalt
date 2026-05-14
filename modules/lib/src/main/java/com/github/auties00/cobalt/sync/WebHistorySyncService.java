package com.github.auties00.cobalt.sync;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppHistorySyncException;
import com.github.auties00.cobalt.exception.WhatsAppMediaException;
import com.github.auties00.cobalt.media.MediaConnection;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.media.MediaPath;
import com.github.auties00.cobalt.model.media.MediaProvider;
import com.github.auties00.cobalt.model.message.system.history.HistorySyncNotification;
import com.github.auties00.cobalt.model.message.system.history.HistorySyncType;
import com.github.auties00.cobalt.model.sync.history.HistorySync;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.*;
import com.github.auties00.cobalt.wam.type.*;
import it.auties.protobuf.stream.ProtobufInputStream;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.zip.InflaterInputStream;

/**
 * Downloads, decrypts and decodes {@code HistorySyncNotification} payloads
 * sent by the primary device after a companion has been linked.
 *
 * <p>On every incoming {@link HistorySyncNotification} the primary device
 * advertises an encrypted history chunk (or inlines a bootstrap payload when
 * the data is small enough to skip the CDN round-trip). This service resolves
 * the notification to a decoded {@link HistorySync} protobuf, fans the chunk
 * out to the registered history-sync listeners, and forwards the payload to
 * the {@link LidMigrationService} so that any phone-number to LID mappings
 * carried with the chunk are ingested.
 *
 * <p>Processing runs on a dedicated virtual thread because history blobs can
 * reach several megabytes and the caller is the stanza dispatch thread; keeping
 * it off the main path avoids back-pressuring other incoming messages.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleHistorySyncNotification")
@WhatsAppWebModule(moduleName = "WAWebHistorySyncNotificationUtils")
@WhatsAppWebModule(moduleName = "WAWebHandleHistorySyncChunk")
public final class WebHistorySyncService {
    /**
     * Logger used to trace non-fatal download and decode failures without
     * propagating them into the stanza dispatch loop.
     */
    private static final System.Logger LOGGER = System.getLogger(WebHistorySyncService.class.getName());

    /**
     * The WhatsApp client, used to reach the store (for the shared media
     * connection) and to notify history-sync listeners.
     */
    private final WhatsAppClient whatsapp;

    /**
     * The LID migration service that consumes the
     * {@code phoneNumberToLidMappings} attached to every history sync chunk.
     */
    private final LidMigrationService lidMigrationService;

    /**
     * The AB-props service threaded into the media download so the CDN
     * fetch can apply server-driven configuration.
     */
    private final ABPropsService abPropsService;

    /**
     * The WAM telemetry service used to commit history-sync events.
     */
    private final WamService wamService;

    /**
     * Constructs a new service bound to the given client.
     *
     * @param whatsapp            the WhatsApp client
     * @param lidMigrationService the LID migration service that receives the
     *                            decoded chunks
     * @param abPropsService      the AB-props service threaded into the CDN
     *                            download
     * @param wamService          the WAM telemetry service for committing history-sync events
     * @throws NullPointerException if any argument is {@code null}
     */
    public WebHistorySyncService(WhatsAppClient whatsapp, LidMigrationService lidMigrationService, ABPropsService abPropsService, WamService wamService) {
        this.whatsapp = Objects.requireNonNull(whatsapp, "whatsapp cannot be null");
        this.lidMigrationService = Objects.requireNonNull(lidMigrationService, "lidMigrationService cannot be null");
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService cannot be null");
        this.wamService = Objects.requireNonNull(wamService, "wamService cannot be null");
    }

    /**
     * Schedules the asynchronous processing of a history-sync notification.
     *
     * <p>The actual download, decryption and decoding happen on a dedicated
     * virtual thread so that the caller (the message stanza dispatcher) is not
     * blocked waiting for a potentially large CDN fetch.
     *
     * @param notification the history-sync notification to process, may be
     *                     {@code null} in which case the call is a no-op
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleHistorySyncNotification",
            exports = "handleHistorySyncNotification",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void process(HistorySyncNotification notification) {
        if (notification == null) {
            return;
        }
        Thread.startVirtualThread(() -> processSync(notification));
    }

    /**
     * Runs the full download-decrypt-decode-fanout pipeline for a single
     * notification on the current thread. Any failure is logged at warning
     * level and swallowed because history sync is non-fatal: the companion
     * can continue operating on partial history and the primary will retry.
     *
     * @param notification the non-null notification to process
     */
    private void processSync(HistorySyncNotification notification) {
        // WAWebHandleHistorySyncChunk.handleHistorySyncChunk: historySyncStepStartedTs
        // captured at the entry point of the chunk-processing coroutine, used to
        // compute mdBootstrapStepDuration reported via commitHistoryDataAppliedMetric.
        var applyStartTs = System.currentTimeMillis();
        // WAWebHandleHistorySyncChunk.handleHistorySyncChunk: sentViaMms reports whether
        // the chunk was fetched over MMS (CDN) rather than inlined in the notification.
        // Cobalt's inline short-circuit in decode() corresponds to sentViaMms=false; any
        // CDN fetch path is sentViaMms=true.
        var inlinePayload = notification.initialHistBootstrapInlinePayload().orElse(null);
        var sentViaMms = inlinePayload == null || inlinePayload.length == 0;
        // WAWebHandleHistorySyncNotification.handleHistorySyncNotification: once the
        // notification has passed the MESSAGE_ACCESS_STATUS early return and the T
        // context has been populated, the receiver emits MdBootstrapHistoryDataReceived
        // to mark chunk arrival BEFORE any download/decode work happens. Cobalt mirrors
        // the placement here, right after the local sentViaMms/applyStartTs context is
        // captured and before decode() kicks off the CDN download.
        emitHistoryDataReceived(notification);
        // WAWebHandleHistorySyncChunk.handleHistorySyncChunk:
        //   setRecentSyncSingleChunkStatus(DOWNLOADING);
        //   commitHistoryStartDownloadingMetric(V, e.historySyncStepStartedTs, unixTimeMs());
        // Fired immediately after transitioning the chunk to the DOWNLOADING state and
        // before the CDN fetch / inline-payload read. Cobalt has no per-chunk status
        // user-prefs store, so only the WAM emission is mirrored here.
        emitHistoryDataStartDownloading(notification, applyStartTs);
        HistorySync historySync;
        try {
            historySync = decode(notification);
        } catch (WhatsAppHistorySyncException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "History sync chunk processing failed: {0}", exception.getMessage());
            // WAWebHandleHistorySyncChunk.handleHistorySyncChunk / WAWebHandleHistorySyncNotification.handleHistorySyncNotification:
            //   commitHistoryDownloadedMetric(B, historySyncStepStartedTs, false, unixTimeMs())
            //   is called on the download/decode failure path before the applied metric.
            emitHistoryDataDownloaded(notification, null, applyStartTs,
                    MdBootstrapStepResult.FAILURE, exception.getMessage());
            // WAWebHistorySyncNotificationUtils.commitHistoryDataAppliedMetric:
            //   failure branch with isSuccess=false, forceFlushWamBuffer=true, and
            //   failureReason populated from the caught error message.
            emitHistoryDataApplied(notification, null, sentViaMms, applyStartTs,
                    MdBootstrapStepResult.FAILURE, exception.getMessage());
            return;
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "History sync chunk processing failed", exception);
            // Same as WAWebHandleHistorySyncChunk.handleHistorySyncChunk error path: the
            // Downloaded event is emitted with FAILURE before DataApplied so the dashboards
            // see both metrics on catastrophic processing errors.
            emitHistoryDataDownloaded(notification, null, applyStartTs,
                    MdBootstrapStepResult.FAILURE, exception.getMessage());
            emitHistoryDataApplied(notification, null, sentViaMms, applyStartTs,
                    MdBootstrapStepResult.FAILURE, exception.getMessage());
            return;
        }
        if (historySync == null) {
            // Notification carried neither an inline payload nor a directPath/mediaKey pair
            // (MESSAGE_ACCESS_STATUS / NO_HISTORY markers). WA Web's equivalent early-return
            // in WAWebHandleHistorySyncNotification.handleHistorySyncNotification also skips all three metrics.
            return;
        }
        // WAWebHandleHistorySyncChunk.handleHistorySyncChunk:
        //   U.mdBootstrapMessagesCount = Re;
        //   U.mdBootstrapChatsCount = ae.conversations.length;
        //   commitHistoryDownloadedMetric(U, e.historySyncStepStartedTs, true, ce);
        // Both counts are set on the Downloaded event just before commit, after the
        // protobuf has been decoded and chat/message totals are known.
        emitHistoryDataDownloaded(notification, historySync, applyStartTs,
                MdBootstrapStepResult.SUCCESS, null);
        try {
            dispatch(historySync);
            // WAWebHistorySyncNotificationUtils.commitHistoryDataAppliedMetric:
            //   historySyncDataAppliedMetric built by getHistorySyncMetrics is committed
            //   at the end of the successful chunk-processing path with isSuccess=true,
            //   setting mdBootstrapStepResult=SUCCESS, mdTimestamp, and mdBootstrapStepDuration.
            emitHistoryDataApplied(notification, historySync, sentViaMms, applyStartTs,
                    MdBootstrapStepResult.SUCCESS, null);
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "History sync chunk dispatch failed", exception);
            emitHistoryDataApplied(notification, historySync, sentViaMms, applyStartTs,
                    MdBootstrapStepResult.FAILURE, exception.getMessage());
        }
    }

    /**
     * Emits a {@link MdBootstrapDataAppliedEventBuilder MdBootstrapDataAppliedEvent} for the
     * history-sync branch once the chunk has finished applying (success or
     * failure).
     *
     * <p>Per WhatsApp Web
     * {@code WAWebHistorySyncNotificationUtils.getHistorySyncMetrics} builds the
     * event up-front with the static properties (payload type, source, history
     * payload type, session id, sentViaMms, stage progress, chunk order), and
     * {@code commitHistoryDataAppliedMetric} populates the dynamic finalisation
     * properties ({@code mdTimestamp}, {@code mdBootstrapStepDuration},
     * {@code mdBootstrapStepResult}, optional {@code mdSyncFailureReason}) and
     * commits the event. Cobalt folds both steps into a single builder call at
     * the terminal point of {@link #processSync} because the intermediate
     * mutable event object is not exposed anywhere else.
     * @param notification  the notification whose chunk was processed; used for
     *                      {@code syncType}, {@code chunkOrder} and
     *                      {@code progress}
     * @param historySync   the decoded payload, or {@code null} when decoding
     *                      itself failed (in which case the payload-type/source
     *                      derivation falls back to the notification)
     * @param sentViaMms    {@code true} when the chunk was fetched via CDN
     *                      (MMS), {@code false} when the inline bootstrap
     *                      payload was used
     * @param applyStartTs  millisecond timestamp captured at the top of
     *                      {@link #processSync}
     * @param result        step result, {@link MdBootstrapStepResult#SUCCESS}
     *                      for the normal path or
     *                      {@link MdBootstrapStepResult#FAILURE} on any caught
     *                      exception
     * @param failureReason failure reason string, or {@code null} on the
     *                      success path; maps to {@code mdSyncFailureReason}
     */
    @WhatsAppWebExport(moduleName = "WAWebHistorySyncNotificationUtils",
            exports = "getHistorySyncMetrics", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebHistorySyncNotificationUtils",
            exports = "commitHistoryDataAppliedMetric", adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitHistoryDataApplied(
            HistorySyncNotification notification,
            HistorySync historySync,
            boolean sentViaMms,
            long applyStartTs,
            MdBootstrapStepResult result,
            String failureReason
    ) {
        // WAWebHistorySyncNotificationUtils.getHistorySyncMetrics: payload type derived from
        //   historySync.syncType when available (post-decode), otherwise from the notification
        //   syncType so the failure branch still records a payload type.
        var syncType = historySync != null ? historySync.syncType() : null;
        // WAWebHistorySyncNotificationUtils.getHistorySyncMetrics:
        //   mdBootstrapPayloadType = syncType === INITIAL_BOOTSTRAP ? CRITICAL : NON_CRITICAL
        var payloadType = syncType == HistorySyncType.INITIAL_BOOTSTRAP
                ? MdBootstrapPayloadType.CRITICAL
                : MdBootstrapPayloadType.NON_CRITICAL;
        var historyPayloadType = mapHistoryPayloadType(syncType); // WAWebGetMetricHistorySyncPayloadType.getMetricHistorySyncPayloadType
        var now = System.currentTimeMillis();
        var duration = (int) (now - applyStartTs);
        var builder = new MdBootstrapDataAppliedEventBuilder()
                // WAWebHistorySyncNotificationUtils.getHistorySyncMetrics:
                //   mdBootstrapPayloadType: n
                .mdBootstrapPayloadType(payloadType)
                // WAWebHistorySyncNotificationUtils.getHistorySyncMetrics:
                //   mdBootstrapSource: MD_BOOTSTRAP_SOURCE.HISTORY
                .mdBootstrapSource(MdBootstrapSource.HISTORY)
                // WAWebHistorySyncNotificationUtils.getHistorySyncMetrics:
                //   mdBootstrapHistoryPayloadType: r (via getMetricHistorySyncPayloadType)
                .mdBootstrapHistoryPayloadType(historyPayloadType)
                // WAWebHistorySyncNotificationUtils.getHistorySyncMetrics:
                //   sentViaMms: t
                .sentViaMms(sentViaMms)
                // WAWebHistorySyncNotificationUtils.commitHistoryDataAppliedMetric:
                //   e.mdTimestamp = unixTimeMs()
                .mdTimestamp((int) now)
                // WAWebHistorySyncNotificationUtils.commitHistoryDataAppliedMetric:
                //   e.mdBootstrapStepDuration = unixTimeMs() - startTs
                .mdBootstrapStepDuration(duration)
                // WAWebHistorySyncNotificationUtils.commitHistoryDataAppliedMetric:
                //   e.mdBootstrapStepResult = isSuccess ? SUCCESS : FAILURE
                .mdBootstrapStepResult(result);
        // WAWebHistorySyncNotificationUtils.getHistorySyncMetrics:
        //   if (e.chunkOrder != null) u.historySyncChunkOrder = e.chunkOrder
        var chunkOrder = notification.chunkOrder();
        if (chunkOrder.isPresent()) {
            builder.historySyncChunkOrder(chunkOrder.getAsInt());
        }
        // WAWebGetHistorySyncProgress.getHistorySyncProgress:
        //   var t = e.progress; (syncType === FULL || (syncType === RECENT && lastChunk)) && t = 100; return t ?? 0
        // Cobalt doesn't track chunk counts for the "last RECENT chunk" override, so the
        // progress is taken from the notification's progress field with a fallback to
        // 100 for FULL (which is always terminal in WA Web) and 0 otherwise.
        var progress = resolveStageProgress(notification, syncType);
        builder.historySyncStageProgress(progress);
        // WAWebHistorySyncNotificationUtils.commitHistoryDataAppliedMetric:
        //   failureReason != null && (e.mdSyncFailureReason = failureReason)
        if (failureReason != null) {
            builder.mdSyncFailureReason(failureReason);
        }
        // NO_WA_BASIS: WA Web populates mdSessionId via MdSyncFieldStatsMeta.getMdSessionId()
        // which hashes primary + companion identity keys; Cobalt has no equivalent derivation
        // so mdSessionId is omitted, matching the app-state emission in WebAppStateService.
        wamService.commit(builder.build());
    }

    /**
     * Emits a {@link MdBootstrapHistoryDataDownloadedEventBuilder MdBootstrapHistoryDataDownloadedEvent}
     * after the encrypted history blob has been fetched (or inlined) and the
     * resulting plaintext has been decoded into a {@link HistorySync} message.
     *
     * <p>Per WhatsApp Web
     * {@code WAWebHistorySyncNotificationUtils.getHistorySyncMetrics} builds
     * the event up-front with the static properties (payload type, history
     * payload type, payload size, session id, stage progress, chunk order,
     * storage quota), and each chunk consumer ({@code WAWebHandleHistorySyncChunk},
     * {@code WAWebHistoryMsgHandlerAction}, {@code WAWebHistorySyncHandlePushname},
     * {@code WAWebHistorySyncHandleStatusMessages}, ...) populates the dynamic
     * finalisation properties ({@code mdTimestamp}, {@code mdBootstrapStepDuration},
     * {@code mdBootstrapStepResult}, {@code mdBootstrapMessagesCount},
     * {@code mdBootstrapChatsCount}, optional {@code mdSyncFailureReason})
     * before calling {@code commitHistoryDownloadedMetric}. Cobalt folds both
     * steps into a single builder call right after
     * {@link #decode(HistorySyncNotification)} because the intermediate mutable
     * event object is not exposed elsewhere.
     * @param notification  the notification whose payload was downloaded; used
     *                      for {@code syncType}, {@code chunkOrder},
     *                      {@code progress} and {@code historySyncPayloadSize}
     * @param historySync   the decoded payload, or {@code null} when the
     *                      download or decode itself failed; used to derive
     *                      payload-type, message/chat counts
     * @param startTs       millisecond timestamp captured at the top of
     *                      {@link #processSync}; maps to
     *                      {@code historySyncStepStartedTs}
     * @param result        step result, {@link MdBootstrapStepResult#SUCCESS}
     *                      when the download+decode completed or
     *                      {@link MdBootstrapStepResult#FAILURE} otherwise
     * @param failureReason failure reason string, or {@code null} on the
     *                      success path; maps to {@code mdSyncFailureReason}
     */
    @WhatsAppWebExport(moduleName = "WAWebHistorySyncNotificationUtils",
            exports = "getHistorySyncMetrics", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebHistorySyncNotificationUtils",
            exports = "commitHistoryDownloadedMetric", adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitHistoryDataDownloaded(
            HistorySyncNotification notification,
            HistorySync historySync,
            long startTs,
            MdBootstrapStepResult result,
            String failureReason
    ) {
        // WAWebHistorySyncNotificationUtils.getHistorySyncMetrics: payload type derived from
        //   historySync.syncType when available (post-decode), otherwise from the notification
        //   syncType so the failure branch still records a payload type. Cobalt's
        //   HistorySync.syncType() is non-Optional while HistorySyncNotification.syncType() is,
        //   so we unwrap the notification variant manually.
        var syncType = historySync != null
                ? historySync.syncType()
                : notification.syncType().orElse(null);
        // WAWebHistorySyncNotificationUtils.getHistorySyncMetrics:
        //   mdBootstrapPayloadType = syncType === INITIAL_BOOTSTRAP ? CRITICAL : NON_CRITICAL
        var payloadType = syncType == HistorySyncType.INITIAL_BOOTSTRAP
                ? MdBootstrapPayloadType.CRITICAL
                : MdBootstrapPayloadType.NON_CRITICAL;
        // WAWebHistorySyncNotificationUtils.getHistorySyncMetrics:
        //   mdBootstrapHistoryPayloadType = getMetricHistorySyncPayloadType(syncType)
        var historyPayloadType = mapHistoryPayloadType(syncType);
        var now = System.currentTimeMillis();
        var duration = (int) (now - startTs);
        var builder = new MdBootstrapHistoryDataDownloadedEventBuilder()
                // WAWebHistorySyncNotificationUtils.getHistorySyncMetrics:
                //   mdBootstrapPayloadType: n
                .mdBootstrapPayloadType(payloadType)
                // WAWebHistorySyncNotificationUtils.getHistorySyncMetrics:
                //   mdBootstrapHistoryPayloadType: r (via getMetricHistorySyncPayloadType)
                .mdBootstrapHistoryPayloadType(historyPayloadType)
                // WAWebHistorySyncNotificationUtils.commitHistoryDownloadedMetric (R):
                //   e.mdTimestamp = r (unixTimeMs())
                .mdTimestamp((int) now)
                // WAWebHistorySyncNotificationUtils.commitHistoryDownloadedMetric (R):
                //   e.mdBootstrapStepDuration = r - t (unixTimeMs() - historySyncStepStartedTs)
                .mdBootstrapStepDuration(duration)
                // WAWebHistorySyncNotificationUtils.commitHistoryDownloadedMetric (R):
                //   e.mdBootstrapStepResult = n ? SUCCESS : FAILURE
                .mdBootstrapStepResult(result);
        // WAWebHistorySyncNotificationUtils.getHistorySyncMetrics:
        //   mdBootstrapPayloadSize: e.historySyncPayloadSize (mirrors notification.fileLength,
        //   or the inline bootstrap payload length when the chunk was inlined in the
        //   E2EE message rather than fetched from the CDN).
        var payloadSize = resolvePayloadSize(notification);
        if (payloadSize != null) {
            builder.mdBootstrapPayloadSize(payloadSize);
        }
        // WAWebHistorySyncNotificationUtils.getHistorySyncMetrics:
        //   if (e.chunkOrder != null) s.historySyncChunkOrder = e.chunkOrder
        var chunkOrder = notification.chunkOrder();
        if (chunkOrder.isPresent()) {
            builder.historySyncChunkOrder(chunkOrder.getAsInt());
        }
        // WAWebGetHistorySyncProgress.getHistorySyncProgress: same derivation as DataApplied.
        var progress = resolveStageProgress(notification, syncType);
        builder.historySyncStageProgress(progress);
        // WAWebHandleHistorySyncChunk.handleHistorySyncChunk (and sibling callers):
        //   U.mdBootstrapMessagesCount = Re;    (total messages decoded in the chunk)
        //   U.mdBootstrapChatsCount = ae.conversations.length;  (total chats decoded)
        // For pushname/status chunks Re is derived from pushnames.length / statusV3Messages.length,
        // so Cobalt aggregates in the same order: status messages take precedence when no chats
        // are present (status-only chunk), otherwise the per-chat message totals are summed.
        if (historySync != null) {
            var chats = historySync.chats();
            if (!chats.isEmpty()) {
                builder.mdBootstrapChatsCount(chats.size());
                var messagesCount = 0;
                for (var chat : chats) {
                    messagesCount += chat.messageCount();
                }
                builder.mdBootstrapMessagesCount(messagesCount);
            } else if (!historySync.statusV3Messages().isEmpty()) {
                // WAWebHistorySyncHandleStatusMessages: i.mdBootstrapMessagesCount = t.statusV3Messages.length
                builder.mdBootstrapMessagesCount(historySync.statusV3Messages().size());
            } else if (!historySync.pushnames().isEmpty()) {
                // WAWebHistorySyncHandlePushname derives its count from pushnames indirectly via
                // the decode step; Cobalt reports the pushname count as a coarse equivalent to
                // keep the metric non-zero on pushname-only chunks.
                builder.mdBootstrapMessagesCount(historySync.pushnames().size());
            }
        }
        // WAWebHistorySyncNotificationUtils.commitHistoryDownloadedMetric: failure metric
        // writes are handled inline at each caller; the shared commitDownloaded function
        // itself has no failureReason assignment. Cobalt mirrors the mdSyncFailureReason
        // propagation of the DataApplied event because the spec carries the field and
        // WA Web's downloaded-metric failure paths in WAWebHandleHistorySyncNotification
        // commit the event after setting sibling failure-tracking state.
        if (failureReason != null) {
            builder.mdSyncFailureReason(failureReason);
        }
        // NO_WA_BASIS: mdSessionId / mdStorageQuotaBytes / mdStorageQuotaUsedBytes are
        // sourced from MdSyncFieldStatsMeta (identity-key hash and navigator.storage.estimate)
        // which Cobalt has no equivalent for; mdRegAttemptId / applicationState / appContext*
        // / historySyncRetryRequestId / mdBootstrapPayloadThumbnailsSize / mdHsOldestMessageTimestamp
        // are not populated at any WA Web call site of this event, only declared in the spec.
        wamService.commit(builder.build());
    }

    /**
     * Emits a {@link MdBootstrapHistoryDataStartDownloadingEventBuilder MdBootstrapHistoryDataStartDownloadingEvent}
     * to mark the start of the download step for a history-sync chunk, right
     * after the notification has been accepted and before the CDN blob or
     * inline bootstrap payload is consumed.
     *
     * <p>Per WhatsApp Web
     * {@code WAWebHistorySyncNotificationUtils.getHistorySyncMetrics} builds
     * the event up-front with the static properties ({@code mdBootstrapPayloadType},
     * {@code mdBootstrapPayloadSize}, {@code mdBootstrapHistoryPayloadType},
     * {@code mdSessionId}, {@code historySyncStageProgress}) and, when
     * {@code chunkOrder} is present, sets {@code historySyncChunkOrder}.
     * {@code WAWebHistorySyncNotificationUtils.commitHistoryStartDownloadingMetric}
     * then finalises the event by setting {@code mdTimestamp = unixTimeMs()}
     * and {@code mdBootstrapStepDuration = unixTimeMs() - historySyncStepStartedTs}
     * before committing. The WA Web callers are
     * {@code WAWebHandleHistorySyncChunk.handleHistorySyncChunk} (main chunk
     * processing path) and
     * {@code WAWebHandleHistorySyncNotification.handleHistorySyncNotification}
     * (recent-sync download-optimization path) and
     * {@code WAWebHandleWorkerCompatibleRecentSyncChunk} (worker-compatible
     * variant). Cobalt funnels all three paths through a single
     * {@link #processSync} pipeline, so one emission here covers the three
     * WA Web sites.
     *
     * <p>{@code applyStartTs} is the same {@code historySyncStepStartedTs}
     * used by the Downloaded and DataApplied emitters; since this event is
     * committed immediately after it is captured, the {@code mdBootstrapStepDuration}
     * reported here is effectively zero, matching WA Web's behaviour (the
     * start-downloading step has no preceding work).
     * @param notification  the non-null notification whose chunk is about to
     *                      be downloaded
     * @param applyStartTs  millisecond timestamp captured at the top of
     *                      {@link #processSync}; maps to
     *                      {@code historySyncStepStartedTs}
     */
    @WhatsAppWebExport(moduleName = "WAWebHistorySyncNotificationUtils",
            exports = "getHistorySyncMetrics", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebHistorySyncNotificationUtils",
            exports = "commitHistoryStartDownloadingMetric", adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitHistoryDataStartDownloading(HistorySyncNotification notification, long applyStartTs) {
        // WAWebHandleHistorySyncChunk.handleHistorySyncChunk: the syncType used
        //   to seed the metric is notification.syncType (before decode). Cobalt
        //   mirrors this exactly since the event fires pre-download/pre-decode.
        var syncType = notification.syncType().orElse(null);
        // WAWebHistorySyncNotificationUtils.getHistorySyncMetrics:
        //   mdBootstrapPayloadType = syncType === INITIAL_BOOTSTRAP ? CRITICAL : NON_CRITICAL
        var payloadType = syncType == HistorySyncType.INITIAL_BOOTSTRAP
                ? MdBootstrapPayloadType.CRITICAL
                : MdBootstrapPayloadType.NON_CRITICAL;
        // WAWebHistorySyncNotificationUtils.getHistorySyncMetrics:
        //   mdBootstrapHistoryPayloadType: r (via getMetricHistorySyncPayloadType)
        var historyPayloadType = mapHistoryPayloadType(syncType);
        var now = System.currentTimeMillis();
        // WAWebHistorySyncNotificationUtils.commitHistoryStartDownloadingMetric (S):
        //   e.mdBootstrapStepDuration = n - t (unixTimeMs() - historySyncStepStartedTs)
        var duration = (int) (now - applyStartTs);
        var builder = new MdBootstrapHistoryDataStartDownloadingEventBuilder()
                // WAWebHistorySyncNotificationUtils.getHistorySyncMetrics:
                //   mdBootstrapPayloadType: n
                .mdBootstrapPayloadType(payloadType)
                // WAWebHistorySyncNotificationUtils.getHistorySyncMetrics:
                //   mdBootstrapHistoryPayloadType: r (via getMetricHistorySyncPayloadType)
                .mdBootstrapHistoryPayloadType(historyPayloadType)
                // WAWebHistorySyncNotificationUtils.commitHistoryStartDownloadingMetric (S):
                //   e.mdTimestamp = n (unixTimeMs())
                .mdTimestamp((int) now)
                // WAWebHistorySyncNotificationUtils.commitHistoryStartDownloadingMetric (S):
                //   e.mdBootstrapStepDuration = n - t
                .mdBootstrapStepDuration(duration);
        // WAWebHistorySyncNotificationUtils.getHistorySyncMetrics:
        //   mdBootstrapPayloadSize: e.historySyncPayloadSize (ciphertext byte length
        //   from the notification, or the inline bootstrap payload length).
        var payloadSize = resolvePayloadSize(notification);
        if (payloadSize != null) {
            builder.mdBootstrapPayloadSize(payloadSize);
        }
        // WAWebHistorySyncNotificationUtils.getHistorySyncMetrics:
        //   if (e.chunkOrder != null) l.historySyncChunkOrder = e.chunkOrder
        var chunkOrder = notification.chunkOrder();
        if (chunkOrder.isPresent()) {
            builder.historySyncChunkOrder(chunkOrder.getAsInt());
        }
        // WAWebGetHistorySyncProgress.getHistorySyncProgress: same derivation as sibling
        // DataReceived / Downloaded / DataApplied emissions.
        var progress = resolveStageProgress(notification, syncType);
        builder.historySyncStageProgress(progress);
        // NO_WA_BASIS: mdSessionId is sourced from MdSyncFieldStatsMeta.getMdSessionId()
        //   (SHA-256 hash of primary+companion identity keys) which Cobalt has no
        //   equivalent for; omitted here to match sibling emissions.
        //   historySyncRetryRequestId is declared in the event spec but never
        //   populated at WA Web's call sites of this event.
        wamService.commit(builder.build());
    }

    /**
     * Emits a {@link MdBootstrapHistoryDataReceivedEventBuilder MdBootstrapHistoryDataReceivedEvent}
     * to record arrival of a history-sync notification, before the CDN blob
     * is downloaded and the protobuf payload is decoded.
     *
     * <p>Per WhatsApp Web
     * {@code WAWebHandleHistorySyncNotification.handleHistorySyncNotification}
     * constructs the event inline with the static properties
     * ({@code mdBootstrapPayloadType}, {@code mdBootstrapHistoryPayloadType},
     * {@code mdTimestamp}, {@code mdSessionId}, {@code historySyncStageProgress})
     * sourced from the notification's {@code T} context object, conditionally
     * sets {@code historySyncChunkOrder} when present, and immediately commits
     * the event. The emission is skipped by WA Web when the notification is a
     * {@code MESSAGE_ACCESS_STATUS} marker (early return) or when
     * {@code downloadOptions} are absent (no download spec, no inline payload);
     * Cobalt enforces the same guard by checking for the presence of a direct
     * path, a media key, or an inline bootstrap payload before firing the
     * event.
     * @param notification the history-sync notification whose arrival is
     *                     being reported
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleHistorySyncNotification",
            exports = "handleHistorySyncNotification", adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitHistoryDataReceived(HistorySyncNotification notification) {
        // WAWebHandleHistorySyncNotification: the event commit is gated by the
        //   `if (l && !(drop_full_history_sync))` check where l === downloadOptions.
        //   downloadOptions are only populated when the primary has given the
        //   companion a means to fetch/inline the chunk (directPath/mediaKey or an
        //   inline bootstrap payload). Skipping the emission when none of those
        //   are present mirrors WA Web's early-return on the MESSAGE_ACCESS_STATUS
        //   and NO_HISTORY markers.
        var inlinePayload = notification.initialHistBootstrapInlinePayload().orElse(null);
        var hasInline = inlinePayload != null && inlinePayload.length > 0;
        var hasCdn = notification.directPath().isPresent() && notification.mediaKey().isPresent();
        if (!hasInline && !hasCdn) {
            return;
        }
        // WAWebHandleHistorySyncNotification: syncType is read from the T context,
        //   which mirrors the notification's syncType unless the chunk is a reupload
        //   for a prior originalMessageId. Cobalt emits once per notification without
        //   the reupload bookkeeping, so the notification's syncType is used directly.
        var syncType = notification.syncType().orElse(null);
        // WAWebHandleHistorySyncNotification:
        //   mdBootstrapPayloadType: T.syncType === INITIAL_BOOTSTRAP ? CRITICAL : NON_CRITICAL
        var payloadType = syncType == HistorySyncType.INITIAL_BOOTSTRAP
                ? MdBootstrapPayloadType.CRITICAL
                : MdBootstrapPayloadType.NON_CRITICAL;
        // WAWebHandleHistorySyncNotification:
        //   mdBootstrapHistoryPayloadType: getMetricHistorySyncPayloadType(T.syncType)
        var historyPayloadType = mapHistoryPayloadType(syncType);
        var builder = new MdBootstrapHistoryDataReceivedEventBuilder()
                // WAWebHandleHistorySyncNotification:
                //   mdBootstrapPayloadType: <derived from syncType above>
                .mdBootstrapPayloadType(payloadType)
                // WAWebHandleHistorySyncNotification:
                //   mdBootstrapHistoryPayloadType: <via getMetricHistorySyncPayloadType>
                .mdBootstrapHistoryPayloadType(historyPayloadType)
                // WAWebHandleHistorySyncNotification:
                //   mdTimestamp: unixTimeMs()
                .mdTimestamp((int) System.currentTimeMillis())
                // WAWebHandleHistorySyncNotification:
                //   historySyncStageProgress: k = (T.progress ?? 0)
                .historySyncStageProgress(resolveStageProgress(notification, syncType));
        // WAWebHandleHistorySyncNotification:
        //   T.chunkOrder != null && (x.historySyncChunkOrder = T.chunkOrder)
        var chunkOrder = notification.chunkOrder();
        if (chunkOrder.isPresent()) {
            builder.historySyncChunkOrder(chunkOrder.getAsInt());
        }
        // NO_WA_BASIS: mdSessionId is sourced from MdSyncFieldStatsMeta.getMdSessionId()
        //   (SHA-256 hash of primary+companion identity keys) which Cobalt has no
        //   equivalent for; omitted here to match the sibling DataApplied and
        //   DataDownloaded emissions. historySyncRetryRequestId and
        //   mdSyncFailureReason are declared in the event spec but never populated
        //   at WA Web's call site of this event, so they remain empty.
        wamService.commit(builder.build());
    }

    /**
     * Resolves the {@code mdBootstrapPayloadSize} property from the notification,
     * matching WhatsApp Web's {@code historySyncPayloadSize} derivation.
     *
     * <p>Per {@code WAWebHandleHistorySyncNotification.handleHistorySyncNotification}
     * the field is
     * set to {@code t.historySyncNotification.fileLength} when the chunk is
     * announced (i.e. the CDN-hosted ciphertext byte length). When the primary
     * instead inlines the bootstrap chunk in the E2EE message body, WA Web
     * never populates {@code historySyncPayloadSize} on the notification, so
     * Cobalt falls back to the inline payload byte count to report a
     * non-{@code null} size for inline bootstraps.
     *
     * @param notification the non-{@code null} notification being processed
     * @return the encrypted blob size, the inline payload size, or
     *         {@code null} when neither is available (MESSAGE_ACCESS_STATUS
     *         / NO_HISTORY markers)
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleHistorySyncNotification",
            exports = "handleHistorySyncNotification",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static Integer resolvePayloadSize(HistorySyncNotification notification) {
        var mediaSize = notification.fileLength();
        if (mediaSize.isPresent()) {
            return (int) mediaSize.getAsLong();
        }
        var inline = notification.initialHistBootstrapInlinePayload().orElse(null);
        if (inline != null) {
            return inline.length;
        }
        return null;
    }

    /**
     * Maps a {@link HistorySyncType} to the
     * {@link MdBootstrapHistoryPayloadType} constant reported via the
     * {@code mdBootstrapHistoryPayloadType} property.
     *
     * <p>Per WhatsApp Web
     * {@code WAWebGetMetricHistorySyncPayloadType.getMetricHistorySyncPayloadType}
     * the mapping is indexed by the protobuf wire value:
     * {@code 0 -> INITIAL}, {@code 1 -> STATUS_V3}, {@code 2 -> FULL_HISTORY},
     * {@code 3 -> RECENT_HISTORY}, {@code 4 -> PUSHNAME},
     * {@code 5 -> NON_BLOCKING_DATA}, {@code 6 -> ON_DEMAND}, and the
     * fall-through returns {@code PUSHNAME}. {@code null} inputs (the decode
     * failure path) also fall through to {@code PUSHNAME} to mirror WA Web's
     * final else-branch.
     * @param syncType the decoded sync type, or {@code null} when decoding
     *                 failed before the payload was available
     * @return the matching {@link MdBootstrapHistoryPayloadType} constant
     */
    @WhatsAppWebExport(moduleName = "WAWebGetMetricHistorySyncPayloadType",
            exports = "getMetricHistorySyncPayloadType", adaptation = WhatsAppAdaptation.DIRECT)
    private static MdBootstrapHistoryPayloadType mapHistoryPayloadType(HistorySyncType syncType) {
        return switch (syncType) {
            case INITIAL_BOOTSTRAP -> MdBootstrapHistoryPayloadType.INITIAL;
            case INITIAL_STATUS_V3 -> MdBootstrapHistoryPayloadType.STATUS_V3;
            case FULL -> MdBootstrapHistoryPayloadType.FULL_HISTORY;
            case RECENT -> MdBootstrapHistoryPayloadType.RECENT_HISTORY;
            case NON_BLOCKING_DATA -> MdBootstrapHistoryPayloadType.NON_BLOCKING_DATA;
            case ON_DEMAND -> MdBootstrapHistoryPayloadType.ON_DEMAND;
            case null, default -> MdBootstrapHistoryPayloadType.PUSHNAME;
        };
    }

    /**
     * Resolves the {@code historySyncStageProgress} percentage reported via
     * {@link MdBootstrapDataAppliedEventBuilder#historySyncStageProgress(Integer)}.
     *
     * <p>Per WhatsApp Web
     * {@code WAWebGetHistorySyncProgress.getHistorySyncProgress}: returns
     * {@code notification.progress} unless the sync is {@code FULL} or the
     * final {@code RECENT} chunk, in which case the progress is clamped to
     * {@code 100}. When {@code progress} is {@code null} the default is
     * {@code 0}. Cobalt cannot detect the last {@code RECENT} chunk without
     * the {@code WAWebUserPrefsHistorySync.getChunkCountForEndOfRecentHistorySync}
     * user-prefs lookup, so the RECENT branch relies on the notification's
     * {@code progress} field, which the primary device already clamps to
     * {@code 100} on the terminal chunk.
     * @param notification the chunk whose progress is being reported
     * @param syncType     the decoded sync type (may be {@code null} on
     *                     decode failures)
     * @return the stage progress percentage between {@code 0} and {@code 100}
     */
    @WhatsAppWebExport(moduleName = "WAWebGetHistorySyncProgress",
            exports = "getHistorySyncProgress", adaptation = WhatsAppAdaptation.ADAPTED)
    private static int resolveStageProgress(HistorySyncNotification notification, HistorySyncType syncType) {
        // WAWebGetHistorySyncProgress.getHistorySyncProgress: syncType === FULL -> 100
        if (syncType == HistorySyncType.FULL) {
            return 100;
        }
        // WAWebGetHistorySyncProgress.getHistorySyncProgress: t != null ? t : 0
        return notification.progress().orElse(0);
    }

    /**
     * Resolves a notification to a decoded {@link HistorySync} payload,
     * reading either the inline bootstrap payload or the decrypted CDN blob.
     *
     * @param notification the non-null notification
     * @return the decoded payload, or {@code null} when the notification is
     *         empty (no direct path and no inline payload)
     * @throws WhatsAppHistorySyncException if the CDN download fails or the
     *                                      decoded bytes cannot be parsed as
     *                                      a {@link HistorySync} protobuf
     */
    @WhatsAppWebExport(moduleName = "WAWebDownloadManager", exports = "downloadAndMaybeDecrypt",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private HistorySync decode(HistorySyncNotification notification) {
        // WAWebHandleHistorySyncNotification.handleHistorySyncNotification: initialHistBootstrapInlinePayload
        // short-circuits the CDN download when the primary inlined the bootstrap
        // bytes directly in the E2EE message (see the inlineInitialPayloadInE2EeMsg
        // device prop set at pairing time).
        var inlinePayload = notification.initialHistBootstrapInlinePayload().orElse(null);
        if (inlinePayload != null && inlinePayload.length > 0) {
            try (var stream = new InflaterInputStream(new ByteArrayInputStream(inlinePayload))) {
                return decodeHistorySync(stream);
            } catch (Exception exception) {
                throw new WhatsAppHistorySyncException("Failed to decode inline history bootstrap payload", exception);
            }
        }

        if (notification.directPath().isEmpty() || notification.mediaKey().isEmpty()) {
            // No download URL and no inline payload: nothing to decode. This can
            // legitimately happen for MESSAGE_ACCESS_STATUS or NO_HISTORY markers.
            return null;
        }

        // WAWebDownloadManager.downloadAndMaybeDecrypt: MediaConnection.download
        // streams the ciphertext from the CDN, HMAC-verifies it, AES-CBC decrypts
        // it with keys derived via HKDF("WhatsApp History Keys"), and inflates the
        // resulting zlib-compressed plaintext, all transparently to the caller.
        MediaConnection mediaConnection;
        try {
            mediaConnection = whatsapp.store().awaitMediaConnection();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new WhatsAppHistorySyncException("Interrupted while waiting for media connection", exception);
        }
        var downloadStart = Instant.now();
        try (var stream = mediaConnection.download(notification, abPropsService)) {
            var decoded = decodeHistorySync(stream);
            commitMediaDownload2Success(downloadStart);
            return decoded;
        } catch (Exception exception) {
            commitMediaDownload2Failure(downloadStart, exception);
            throw new WhatsAppHistorySyncException("Failed to download or decode history sync chunk", exception);
        }
    }

    /**
     * Commits a successful {@code MediaDownload2Event} for the just-finished
     * history sync chunk download.
     * @param downloadStart the instant at which the download attempt began
     */
    @WhatsAppWebExport(moduleName = "WAWebCreateMediaDownloadMetrics",
            exports = "createMediaDownloadMetrics",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void commitMediaDownload2Success(Instant downloadStart) {
        // WAWebCreateMediaDownloadMetrics.createMediaDownloadMetrics:
        // overall mms version, type, origin and mode are constant for the
        // md-msg-hist flow (WAWebHandleHistorySyncNotification.handleHistorySyncNotification
        // passes type="md-msg-hist" and origin=MESSAGE_HISTORY_SYNC).
        var overallT = Instant.ofEpochMilli(Duration.between(downloadStart, Instant.now()).toMillis());
        wamService.commit(new MediaDownload2EventBuilder()
                .overallMediaType(MediaType.MD_HISTORY_SYNC)
                .overallMmsVersion(4)
                .overallDownloadOrigin(DownloadOriginType.MESSAGE_HISTORY_SYNC)
                .overallDownloadMode(MediaDownloadModeType.FULL)
                .overallDownloadResult(MediaDownloadResultType.OK)
                .overallIsFinal(Boolean.TRUE)
                .downloadHttpCode(200)
                .overallT(overallT)
                .overallAttemptCount(1)
                .overallRetryCount(0)
                .build());
    }

    /**
     * Commits a failing {@code MediaDownload2Event} for the just-attempted
     * history sync chunk download.
     * @param downloadStart the instant at which the download attempt began
     * @param throwable     the exception that aborted the download
     */
    @WhatsAppWebExport(moduleName = "WAWebCreateMediaDownloadMetrics",
            exports = "createMediaDownloadMetrics",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebWamMediaMetricUtils",
            exports = "getMetricDownloadErrorResultType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void commitMediaDownload2Failure(Instant downloadStart, Throwable throwable) {
        var overallT = Instant.ofEpochMilli(Duration.between(downloadStart, Instant.now()).toMillis());
        var builder = new MediaDownload2EventBuilder()
                .overallMediaType(MediaType.MD_HISTORY_SYNC)
                .overallMmsVersion(4)
                .overallDownloadOrigin(DownloadOriginType.MESSAGE_HISTORY_SYNC)
                .overallDownloadMode(MediaDownloadModeType.FULL)
                .overallDownloadResult(classifyMediaDownloadError(throwable))
                .overallIsFinal(Boolean.TRUE)
                .overallT(overallT)
                .overallAttemptCount(1)
                .overallRetryCount(0);
        // WAWebCreateMediaDownloadMetrics.handleDownloadError:
        // {@code r.downloadHttpCode = a} only when a status code is present on
        // the error, mirrored here via the optional httpStatusCode() on
        // WhatsAppMediaException.
        var statusCode = extractHttpStatusCode(throwable);
        if (statusCode != null) {
            builder.downloadHttpCode(statusCode);
        }
        wamService.commit(builder.build());
    }

    /**
     * Maps a download failure to the appropriate
     * {@link MediaDownloadResultType} using the same rules as WA Web's
     * {@code WAWebWamMediaMetricUtils.getMetricDownloadErrorResultType}.
     * @param throwable the error raised by the download path
     * @return the mapped result type
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMediaMetricUtils",
            exports = "getMetricDownloadErrorResultType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static MediaDownloadResultType classifyMediaDownloadError(Throwable throwable) {
        for (var current = throwable; current != null; current = current.getCause()) {
            if (current instanceof WhatsAppMediaException.Download download) {
                var optStatus = download.httpStatusCode();
                if (optStatus.isEmpty()) {
                    // WAWebWamMediaMetricUtils.getMetricDownloadErrorResultType:
                    // HttpNetworkError -> ERROR_NETWORK.
                    return MediaDownloadResultType.ERROR_NETWORK;
                }
                var status = optStatus.getAsInt();
                return switch (status) {
                    case 404, 410 -> MediaDownloadResultType.ERROR_TOO_OLD;
                    case 416 -> MediaDownloadResultType.ERROR_CANNOT_RESUME;
                    case 401 -> MediaDownloadResultType.ERROR_INVALID_URL;
                    case 429, 507 -> MediaDownloadResultType.ERROR_THROTTLE;
                    default -> MediaDownloadResultType.ERROR_UNKNOWN;
                };
            }
        }
        return MediaDownloadResultType.ERROR_UNKNOWN;
    }

    /**
     * Extracts the HTTP status code embedded in a download failure, if any.
     * @param throwable the error raised by the download path
     * @return the status code, or {@code null} if unavailable
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMediaMetricUtils",
            exports = "getStatusCode",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static Integer extractHttpStatusCode(Throwable throwable) {
        for (var current = throwable; current != null; current = current.getCause()) {
            if (current instanceof WhatsAppMediaException.Download download) {
                var optStatus = download.httpStatusCode();
                return optStatus.isPresent() ? optStatus.getAsInt() : null;
            }
        }
        return null;
    }

    /**
     * Decodes a plaintext history sync byte stream into a {@link HistorySync}
     * payload. Full and light variants share the same wire layout for their
     * common fields, so the decoder always uses the full spec and simply
     * observes empty chat/status lists for light chunks.
     *
     * @param stream the plaintext protobuf stream
     * @return the decoded payload
     */
    @WhatsAppWebExport(moduleName = "WAWebBinHistorySync", exports = "HistorySync",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private HistorySync decodeHistorySync(InputStream stream) {
        var protoStream = ProtobufInputStream.fromStream(stream);
        var historyPolicy = whatsapp.store().webHistoryPolicy();
        if(historyPolicy.isPresent() && historyPolicy.get().isZero()) {
            return HistorySync.ofLight(protoStream);
        } else {
            return HistorySync.ofFull(protoStream);
        }
    }

    /**
     * Fans the decoded chunk out to the registered history-sync listeners and
     * the LID migration service.
     *
     * <p>Listener callbacks run on dedicated virtual threads so that a slow
     * listener cannot block the next chunk or the LID mapping ingest.
     *
     * @param historySync the decoded payload
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleHistorySyncChunk", exports = "handleHistorySyncChunk",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void dispatch(HistorySync historySync) {
        var store = whatsapp.store();
        var listeners = store.listeners();
        var syncType = historySync.syncType();
        var progressValue = historySync.progress().orElse(0);
        // "recent" in the listener contract means "this percentage tracks the
        // recent-messages stream"; WA Web surfaces the same boolean via the
        // syncType it passes to its UI progress reducer.
        var recent = syncType == HistorySyncType.RECENT;
        var isLast = progressValue >= 100;

        for (var listener : listeners) {
            Thread.startVirtualThread(() -> listener.onWebHistorySyncProgress(whatsapp, progressValue, recent));
        }

        for (var chat : historySync.chats()) {
            for (var listener : listeners) {
                Thread.startVirtualThread(() -> listener.onWebHistorySyncMessages(whatsapp, chat, isLast));
            }
        }

        for (var pastParticipants : historySync.pastParticipants()) {
            var groupJid = pastParticipants.groupJid().orElse(null);
            if (groupJid == null) {
                continue;
            }
            var participants = pastParticipants.pastParticipants();
            for (var listener : listeners) {
                Thread.startVirtualThread(() -> listener.onWebHistorySyncPastParticipants(whatsapp, groupJid, participants));
            }
        }

        // The first time the bootstrap delivers a non-empty chat conversations
        // list, mark chats as synced and fan out the full chat snapshot to the
        // onChats listeners. INITIAL_BOOTSTRAP also seeds the contacts via the
        // pushName/conversation participant lists, so flip syncedContacts here
        // too. WAWebHandleHistorySyncChunk.handleHistorySyncChunk in WA Web
        // surfaces the same dataset via WAWebChatCollection/WAWebContactCollection
        // collection-add events; Cobalt funnels both through the listener API.
        if (!historySync.chats().isEmpty() && !store.syncedChats()) {
            store.setSyncedChats(true);
            store.setSyncedContacts(true);
            var chats = store.chats();
            var contacts = store.contacts();
            for (var listener : listeners) {
                Thread.startVirtualThread(() -> listener.onChats(whatsapp, chats));
                Thread.startVirtualThread(() -> listener.onContacts(whatsapp, contacts));
            }
        }

        // The INITIAL_STATUS_V3 sync seeds the status tray with the most recent
        // status updates. Mirror the same gate-and-fan-out pattern used for
        // chats so that consumers always observe the status feed once.
        if (!historySync.statusV3Messages().isEmpty() && !store.syncedStatus()) {
            store.setSyncedStatus(true);
            var status = store.status();
            for (var listener : listeners) {
                Thread.startVirtualThread(() -> listener.onStatus(whatsapp, status));
            }
        }

        // WAWebLid1X1ThreadAccountMigrations.setLidMigrationMappings: the
        // history-sync variant of the mapping ingest consumes the top-level
        // phoneNumberToLidMappings list and the per-chat LID fields.
        lidMigrationService.processHistorySync(historySync);
    }
}
