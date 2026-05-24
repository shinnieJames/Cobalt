package com.github.auties00.cobalt.sync;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.exception.WhatsAppHistorySyncException;
import com.github.auties00.cobalt.exception.WhatsAppMediaException;
import com.github.auties00.cobalt.media.MediaConnectionService;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.model.chat.Chat;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.media.StickerMetadata;
import com.github.auties00.cobalt.model.message.system.history.HistorySyncNotification;
import com.github.auties00.cobalt.model.message.system.history.HistorySyncType;
import com.github.auties00.cobalt.model.preference.StickerBuilder;
import com.github.auties00.cobalt.model.sync.history.HistorySync;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.*;
import com.github.auties00.cobalt.wam.type.*;
import it.auties.protobuf.stream.ProtobufInputStream;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.zip.InflaterInputStream;

/**
 * Pulls every {@link HistorySyncNotification} the primary device sends
 * into a decoded {@link HistorySync} payload, fans the result out to the
 * registered {@code WhatsAppClientListener}s, and ingests the
 * phone-number to LID mappings carried with each chunk.
 *
 * <p>The class flattens three WA Web modules into a single pipeline:
 * <ul>
 *   <li>{@code WAWebHandleHistorySyncNotification} provides the entry
 *       point that receives the notification and the
 *       MdBootstrapHistoryDataReceived telemetry.</li>
 *   <li>{@code WAWebHandleHistorySyncChunk} provides the
 *       download/decode/store body and the
 *       MdBootstrapHistoryDataDownloaded /
 *       MdBootstrapDataApplied telemetry.</li>
 *   <li>{@code WAWebHistorySyncNotificationUtils} provides the metric
 *       builders shared by every emission point.</li>
 * </ul>
 *
 * <p>Processing runs on a dedicated virtual thread so the stanza
 * dispatch loop is not blocked while a multi-megabyte CDN blob is
 * fetched and decoded.
 *
 * @apiNote Cobalt embedders never call this directly; the service is
 * driven by the message receiver every time a
 * {@link HistorySyncNotification} arrives. To consume the decoded chunks
 * they should register a {@code WhatsAppClientListener} and override
 * {@code onWebHistorySyncMessages}, {@code onWebHistorySyncProgress},
 * {@code onWebHistorySyncPastParticipants},
 * {@code onChats}, {@code onContacts}, or {@code onStatus}.
 *
 * @implNote This implementation collapses the per-stage status writes
 * that WA Web persists in {@code WAWebUserPrefsHistorySync}
 * ({@code DOWNLOADING}, {@code DOWNLOADED}, {@code DECODED},
 * {@code APPLIED} per-chunk markers) because Cobalt has no equivalent
 * user-prefs surface; only the WAM emissions are mirrored. The
 * dynamic-throttling, queue-up, dedup-by-msg-key, and
 * receipt-fan-out branches in {@code WAWebHandleHistorySyncChunk} are
 * also omitted because Cobalt's listener API runs on virtual threads
 * with no per-chat back-pressure.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleHistorySyncNotification")
@WhatsAppWebModule(moduleName = "WAWebHistorySyncNotificationUtils")
@WhatsAppWebModule(moduleName = "WAWebHandleHistorySyncChunk")
public final class WebHistorySyncService {
    /**
     * The logger used to record non-fatal download and decode failures
     * without propagating them into the stanza dispatch loop.
     */
    private static final System.Logger LOGGER = System.getLogger(WebHistorySyncService.class.getName());

    /**
     * The {@link WhatsAppClient} the service is bound to; used to reach
     * the store for the shared {@link MediaConnectionService} and the listener
     * registry.
     */
    private final WhatsAppClient whatsapp;

    /**
     * The {@link LidMigrationService} that consumes the
     * {@code phoneNumberToLidMappings} carried by every history sync
     * chunk.
     */
    private final LidMigrationService lidMigrationService;

    /**
     * The {@link ABPropsService} threaded into the
     * {@link MediaConnectionService#download} call so the CDN fetch can apply
     * server-driven configuration.
     */
    private final ABPropsService abPropsService;

    /**
     * The {@link WamService} used to commit the per-chunk telemetry
     * events.
     */
    private final WamService wamService;

    /**
     * The shared {@link MediaConnectionService} used to download the
     * encrypted history-sync chunks.
     */
    private final MediaConnectionService mediaConnectionService;

    /**
     * Builds a service bound to {@code whatsapp}.
     *
     * @apiNote Called once by the client during startup; the service is
     * shared across every stanza-dispatcher thread. All four
     * collaborators are required and validated up front so that a
     * mis-wired client fails fast.
     *
     * @param whatsapp            the {@link WhatsAppClient} that owns
     *                            this service
     * @param lidMigrationService the {@link LidMigrationService} that
     *                            ingests the LID mappings carried by
     *                            every chunk
     * @param abPropsService      the {@link ABPropsService} threaded
     *                            into {@link MediaConnectionService#download}
     * @param wamService          the {@link WamService} used for
     *                            telemetry commits
     * @throws NullPointerException if any argument is {@code null}
     */
    public WebHistorySyncService(WhatsAppClient whatsapp, LidMigrationService lidMigrationService, ABPropsService abPropsService, WamService wamService, MediaConnectionService mediaConnectionService) {
        this.whatsapp = Objects.requireNonNull(whatsapp, "whatsapp cannot be null");
        this.lidMigrationService = Objects.requireNonNull(lidMigrationService, "lidMigrationService cannot be null");
        this.abPropsService = Objects.requireNonNull(abPropsService, "abPropsService cannot be null");
        this.wamService = Objects.requireNonNull(wamService, "wamService cannot be null");
        this.mediaConnectionService = Objects.requireNonNull(mediaConnectionService, "mediaConnectionService cannot be null");
    }

    /**
     * Hands a {@link HistorySyncNotification} off to a dedicated
     * virtual thread for processing.
     *
     * @apiNote Called by the message receiver immediately after a
     * {@link HistorySyncNotification} is decoded. The actual download,
     * decryption, and decoding happen asynchronously so that the
     * stanza dispatcher thread is not blocked on a CDN round-trip.
     * Passing {@code null} is a no-op, matching the early-return guard
     * in WA Web's
     * {@code WAWebHandleHistorySyncNotification.handleHistorySyncNotification}.
     *
     * @param notification the notification to process; {@code null} is
     *                     accepted and ignored
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
     * Runs the full download-decrypt-decode-fanout pipeline for one
     * notification on the current (virtual) thread.
     *
     * @apiNote Internal entry point invoked from the virtual thread
     * scheduled by {@link #process(HistorySyncNotification)}. Every
     * failure is logged at {@code WARNING} and swallowed because
     * history sync is non-fatal; the companion can continue operating
     * on partial history and the primary will retry on the next
     * bootstrap cycle.
     *
     * @implNote This implementation emits the same trio of WAM events
     * that WA Web fires from
     * {@code WAWebHandleHistorySyncChunk.handleHistorySyncChunk}
     * (HistoryDataReceived, HistoryDataStartDownloading,
     * HistoryDataDownloaded) plus the terminal
     * MdBootstrapDataApplied event. The download/decode failure path
     * also commits the Downloaded event with a FAILURE result before
     * the DataApplied event, mirroring the order in WA Web's catch
     * block so dashboards see both metrics on every catastrophic
     * processing error.
     *
     * @param notification the non-{@code null} notification to process
     */
    private void processSync(HistorySyncNotification notification) {
        var applyStartTs = System.currentTimeMillis();
        var inlinePayload = notification.initialHistBootstrapInlinePayload().orElse(null);
        var sentViaMms = inlinePayload == null || inlinePayload.length == 0;
        emitHistoryDataReceived(notification);
        emitHistoryDataStartDownloading(notification, applyStartTs);
        HistorySync historySync;
        try {
            historySync = decode(notification);
        } catch (WhatsAppHistorySyncException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "History sync chunk processing failed: {0}", exception.getMessage());
            emitHistoryDataDownloaded(notification, null, applyStartTs,
                    MdBootstrapStepResult.FAILURE, exception.getMessage());
            emitHistoryDataApplied(notification, null, sentViaMms, applyStartTs,
                    MdBootstrapStepResult.FAILURE, exception.getMessage());
            return;
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "History sync chunk processing failed", exception);
            emitHistoryDataDownloaded(notification, null, applyStartTs,
                    MdBootstrapStepResult.FAILURE, exception.getMessage());
            emitHistoryDataApplied(notification, null, sentViaMms, applyStartTs,
                    MdBootstrapStepResult.FAILURE, exception.getMessage());
            return;
        }
        if (historySync == null) {
            return;
        }
        emitHistoryDataDownloaded(notification, historySync, applyStartTs,
                MdBootstrapStepResult.SUCCESS, null);
        try {
            dispatch(historySync);
            emitHistoryDataApplied(notification, historySync, sentViaMms, applyStartTs,
                    MdBootstrapStepResult.SUCCESS, null);
            releaseMmsBlob(notification);
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "History sync chunk dispatch failed", exception);
            emitHistoryDataApplied(notification, historySync, sentViaMms, applyStartTs,
                    MdBootstrapStepResult.FAILURE, exception.getMessage());
        }
    }

    /**
     * Commits a
     * {@link MdBootstrapDataAppliedEventBuilder MdBootstrapDataAppliedEvent}
     * recording the terminal step of one chunk's processing.
     *
     * @apiNote Internal helper invoked from the success and failure
     * paths of {@link #processSync}. Mirrors the metric WA Web's
     * {@code WAWebHistorySyncNotificationUtils.commitHistoryDataAppliedMetric}
     * commits at the same logical point.
     *
     * @implNote This implementation merges the build/finalise split
     * that WA Web has between
     * {@code WAWebHistorySyncNotificationUtils.getHistorySyncMetrics}
     * (which seeds the static fields up front) and
     * {@code commitHistoryDataAppliedMetric} (which writes the dynamic
     * fields and commits) because the intermediate mutable event
     * object is not exposed anywhere else. The {@code mdSessionId}
     * field is omitted because it is sourced from
     * {@code WAWebSyncdMdSyncFieldstatMeta.MdSyncFieldStatsMeta.getMdSessionId},
     * which hashes the primary plus companion identity keys; Cobalt
     * has no equivalent derivation.
     *
     * @param notification  the notification whose chunk was processed
     * @param historySync   the decoded payload, or {@code null} when
     *                      decoding failed and the caller is reporting
     *                      the failure
     * @param sentViaMms    {@code true} when the chunk was fetched via
     *                      the CDN, {@code false} when an inline
     *                      bootstrap payload was used
     * @param applyStartTs  the start-of-processing timestamp captured
     *                      at the top of {@link #processSync}
     * @param result        the step result to record
     * @param failureReason the failure reason, or {@code null} on the
     *                      success path
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
        var syncType = historySync != null ? historySync.syncType() : null;
        var payloadType = syncType == HistorySyncType.INITIAL_BOOTSTRAP
                ? MdBootstrapPayloadType.CRITICAL
                : MdBootstrapPayloadType.NON_CRITICAL;
        var historyPayloadType = mapHistoryPayloadType(syncType);
        var now = System.currentTimeMillis();
        var duration = (int) (now - applyStartTs);
        var builder = new MdBootstrapDataAppliedEventBuilder()
                .mdBootstrapPayloadType(payloadType)
                .mdBootstrapSource(MdBootstrapSource.HISTORY)
                .mdBootstrapHistoryPayloadType(historyPayloadType)
                .sentViaMms(sentViaMms)
                .mdTimestamp((int) now)
                .mdBootstrapStepDuration(duration)
                .mdBootstrapStepResult(result);
        var chunkOrder = notification.chunkOrder();
        if (chunkOrder.isPresent()) {
            builder.historySyncChunkOrder(chunkOrder.getAsInt());
        }
        var progress = resolveStageProgress(notification, syncType);
        builder.historySyncStageProgress(progress);
        if (failureReason != null) {
            builder.mdSyncFailureReason(failureReason);
        }
        wamService.commit(builder.build());
    }

    /**
     * Commits a
     * {@link MdBootstrapHistoryDataDownloadedEventBuilder MdBootstrapHistoryDataDownloadedEvent}
     * after the encrypted blob has been fetched (or inlined) and
     * decoded into a {@link HistorySync}.
     *
     * @apiNote Internal helper invoked from the success and failure
     * paths of {@link #processSync}. Mirrors the metric WA Web's
     * {@code WAWebHistorySyncNotificationUtils.commitHistoryDownloadedMetric}
     * commits at the same logical point.
     *
     * @implNote This implementation merges the build/finalise split
     * that WA Web has between
     * {@code getHistorySyncMetrics} and the per-handler emission sites
     * ({@code WAWebHistoryMsgHandlerAction},
     * {@code WAWebHistorySyncHandlePushname},
     * {@code WAWebHistorySyncHandleStatusMessages}) because the
     * intermediate event object is not exposed anywhere else.
     * Per-chunk message counts are aggregated locally: the chats path
     * sums {@code messageCount} per chat (matching WA Web's
     * {@code Re} accumulator inside
     * {@code WAWebHandleHistorySyncChunk}); the status-only fallback
     * uses {@code statusV3Messages.length} (matching
     * {@code WAWebHistorySyncHandleStatusMessages}); the pushname-only
     * fallback uses {@code pushnames.length} as a coarse equivalent
     * because WA Web's
     * {@code WAWebHistorySyncHandlePushname} derives its count
     * indirectly through the decode step. The
     * {@code mdSessionId} / {@code mdStorageQuotaBytes} /
     * {@code mdStorageQuotaUsedBytes} fields are omitted because they
     * are sourced from
     * {@code WAWebSyncdMdSyncFieldstatMeta} (identity-key hash and
     * {@code navigator.storage.estimate}), which Cobalt has no
     * equivalent for.
     *
     * @param notification  the notification whose payload was
     *                      downloaded
     * @param historySync   the decoded payload, or {@code null} when
     *                      the download or decode itself failed
     * @param startTs       the start-of-processing timestamp captured
     *                      at the top of {@link #processSync}
     * @param result        the step result to record
     * @param failureReason the failure reason, or {@code null} on the
     *                      success path
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
        var syncType = historySync != null
                ? historySync.syncType()
                : notification.syncType().orElse(null);
        var payloadType = syncType == HistorySyncType.INITIAL_BOOTSTRAP
                ? MdBootstrapPayloadType.CRITICAL
                : MdBootstrapPayloadType.NON_CRITICAL;
        var historyPayloadType = mapHistoryPayloadType(syncType);
        var now = System.currentTimeMillis();
        var duration = (int) (now - startTs);
        var builder = new MdBootstrapHistoryDataDownloadedEventBuilder()
                .mdBootstrapPayloadType(payloadType)
                .mdBootstrapHistoryPayloadType(historyPayloadType)
                .mdTimestamp((int) now)
                .mdBootstrapStepDuration(duration)
                .mdBootstrapStepResult(result);
        var payloadSize = resolvePayloadSize(notification);
        if (payloadSize != null) {
            builder.mdBootstrapPayloadSize(payloadSize);
        }
        var chunkOrder = notification.chunkOrder();
        if (chunkOrder.isPresent()) {
            builder.historySyncChunkOrder(chunkOrder.getAsInt());
        }
        var progress = resolveStageProgress(notification, syncType);
        builder.historySyncStageProgress(progress);
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
                builder.mdBootstrapMessagesCount(historySync.statusV3Messages().size());
            } else if (!historySync.pushnames().isEmpty()) {
                builder.mdBootstrapMessagesCount(historySync.pushnames().size());
            }
        }
        if (failureReason != null) {
            builder.mdSyncFailureReason(failureReason);
        }
        wamService.commit(builder.build());
    }

    /**
     * Commits a
     * {@link MdBootstrapHistoryDataStartDownloadingEventBuilder MdBootstrapHistoryDataStartDownloadingEvent}
     * to mark the start of one chunk's download step.
     *
     * @apiNote Internal helper invoked from {@link #processSync} right
     * after the notification has been accepted and before the CDN
     * fetch or inline-payload read. Mirrors WA Web's
     * {@code WAWebHistorySyncNotificationUtils.commitHistoryStartDownloadingMetric},
     * which is called from
     * {@code WAWebHandleHistorySyncChunk.handleHistorySyncChunk},
     * {@code WAWebHandleHistorySyncNotification.handleHistorySyncNotification}
     * (recent-sync download-optimization path), and
     * {@code WAWebHandleWorkerCompatibleRecentSyncChunk}; Cobalt
     * funnels all three paths through {@link #processSync}, so one
     * emission here covers every WA Web call site.
     *
     * @implNote This implementation reads {@code syncType} from the
     * notification (not the decoded payload, which is not yet
     * available) and computes a near-zero
     * {@code mdBootstrapStepDuration} because the start-downloading
     * step has no preceding work, matching WA Web's behaviour.
     *
     * @param notification the non-{@code null} notification whose
     *                     chunk is about to be downloaded
     * @param applyStartTs the start-of-processing timestamp captured
     *                     at the top of {@link #processSync}
     */
    @WhatsAppWebExport(moduleName = "WAWebHistorySyncNotificationUtils",
            exports = "getHistorySyncMetrics", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebHistorySyncNotificationUtils",
            exports = "commitHistoryStartDownloadingMetric", adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitHistoryDataStartDownloading(HistorySyncNotification notification, long applyStartTs) {
        var syncType = notification.syncType().orElse(null);
        var payloadType = syncType == HistorySyncType.INITIAL_BOOTSTRAP
                ? MdBootstrapPayloadType.CRITICAL
                : MdBootstrapPayloadType.NON_CRITICAL;
        var historyPayloadType = mapHistoryPayloadType(syncType);
        var now = System.currentTimeMillis();
        var duration = (int) (now - applyStartTs);
        var builder = new MdBootstrapHistoryDataStartDownloadingEventBuilder()
                .mdBootstrapPayloadType(payloadType)
                .mdBootstrapHistoryPayloadType(historyPayloadType)
                .mdTimestamp((int) now)
                .mdBootstrapStepDuration(duration);
        var payloadSize = resolvePayloadSize(notification);
        if (payloadSize != null) {
            builder.mdBootstrapPayloadSize(payloadSize);
        }
        var chunkOrder = notification.chunkOrder();
        if (chunkOrder.isPresent()) {
            builder.historySyncChunkOrder(chunkOrder.getAsInt());
        }
        var progress = resolveStageProgress(notification, syncType);
        builder.historySyncStageProgress(progress);
        wamService.commit(builder.build());
    }

    /**
     * Commits a
     * {@link MdBootstrapHistoryDataReceivedEventBuilder MdBootstrapHistoryDataReceivedEvent}
     * to record the arrival of a notification, before any download or
     * decode work happens.
     *
     * @apiNote Internal helper invoked from {@link #processSync}
     * exactly once per notification, immediately after the local
     * processing context has been set up. Mirrors the inline event
     * commit in WA Web's
     * {@code WAWebHandleHistorySyncNotification.handleHistorySyncNotification}
     * which fires after the {@code MESSAGE_ACCESS_STATUS} early-return
     * and the {@code downloadOptions != null} guard.
     *
     * @implNote This implementation enforces the same WA Web guard
     * locally: the event is only fired when the notification carries
     * at least one of an inline bootstrap payload or a
     * {@code (directPath, mediaKey)} CDN handle. Notifications without
     * any of these (the {@code MESSAGE_ACCESS_STATUS} /
     * {@code NO_HISTORY} markers) are silently skipped, matching WA
     * Web's early-return behaviour.
     *
     * @param notification the notification whose arrival is being
     *                     reported
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleHistorySyncNotification",
            exports = "handleHistorySyncNotification", adaptation = WhatsAppAdaptation.ADAPTED)
    private void emitHistoryDataReceived(HistorySyncNotification notification) {
        var inlinePayload = notification.initialHistBootstrapInlinePayload().orElse(null);
        var hasInline = inlinePayload != null && inlinePayload.length > 0;
        var hasCdn = notification.directPath().isPresent() && notification.mediaKey().isPresent();
        if (!hasInline && !hasCdn) {
            return;
        }
        var syncType = notification.syncType().orElse(null);
        var payloadType = syncType == HistorySyncType.INITIAL_BOOTSTRAP
                ? MdBootstrapPayloadType.CRITICAL
                : MdBootstrapPayloadType.NON_CRITICAL;
        var historyPayloadType = mapHistoryPayloadType(syncType);
        var builder = new MdBootstrapHistoryDataReceivedEventBuilder()
                .mdBootstrapPayloadType(payloadType)
                .mdBootstrapHistoryPayloadType(historyPayloadType)
                .mdTimestamp((int) System.currentTimeMillis())
                .historySyncStageProgress(resolveStageProgress(notification, syncType));
        var chunkOrder = notification.chunkOrder();
        if (chunkOrder.isPresent()) {
            builder.historySyncChunkOrder(chunkOrder.getAsInt());
        }
        wamService.commit(builder.build());
    }

    /**
     * Resolves the {@code mdBootstrapPayloadSize} field for the
     * downloaded / start-downloading metrics from the notification.
     *
     * @apiNote Internal helper used by the metric emitters; mirrors WA
     * Web's {@code historySyncPayloadSize} sourcing from
     * {@code t.historySyncNotification.fileLength}.
     *
     * @implNote This implementation falls back to the inline bootstrap
     * payload byte count when {@code fileLength} is absent because WA
     * Web never populates {@code historySyncPayloadSize} for inline
     * bootstraps (the field is set only on the CDN-announced path),
     * which would otherwise leave inline-bootstrap dashboards without
     * a size signal.
     *
     * @param notification the non-{@code null} notification being
     *                     processed
     * @return the encrypted blob size, the inline payload size, or
     *         {@code null} when neither is available
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
     * Maps a {@link HistorySyncType} onto the
     * {@link MdBootstrapHistoryPayloadType} value reported via the
     * {@code mdBootstrapHistoryPayloadType} property of every history
     * sync metric.
     *
     * @apiNote Internal helper invoked from every metric emitter.
     * Mirrors WA Web's
     * {@code WAWebGetMetricHistorySyncPayloadType.getMetricHistorySyncPayloadType},
     * which is indexed by the protobuf wire value:
     * {@code 0 -> INITIAL}, {@code 1 -> STATUS_V3},
     * {@code 2 -> FULL_HISTORY}, {@code 3 -> RECENT_HISTORY},
     * {@code 4 -> PUSHNAME}, {@code 5 -> NON_BLOCKING_DATA},
     * {@code 6 -> ON_DEMAND}, fall-through to {@code PUSHNAME}.
     *
     * @implNote This implementation also returns {@code PUSHNAME} for a
     * {@code null} input so the decode-failure metric path mirrors WA
     * Web's final {@code else} branch instead of throwing.
     *
     * @param syncType the decoded sync type, or {@code null} when
     *                 decoding failed before the payload was available
     * @return the matching {@link MdBootstrapHistoryPayloadType}
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
     * Resolves the {@code historySyncStageProgress} percentage reported
     * via every history sync metric.
     *
     * @apiNote Internal helper invoked from every metric emitter.
     * Mirrors WA Web's
     * {@code WAWebGetHistorySyncProgress.getHistorySyncProgress}, which
     * returns {@code notification.progress} unless the sync is
     * {@link HistorySyncType#FULL} or the final
     * {@link HistorySyncType#RECENT} chunk, in which case the progress
     * is clamped to {@code 100}; the default for a missing
     * {@code progress} field is {@code 0}.
     *
     * @implNote This implementation cannot detect the last
     * {@code RECENT} chunk because WA Web tracks that through the
     * {@code WAWebUserPrefsHistorySync.getChunkCountForEndOfRecentHistorySync}
     * user-prefs lookup, which Cobalt does not implement; the
     * {@code RECENT} branch instead relies on the notification's
     * {@code progress} field, which the primary device already clamps
     * to {@code 100} on the terminal chunk.
     *
     * @param notification the chunk whose progress is being reported
     * @param syncType     the decoded sync type, or {@code null} on
     *                     decode failures
     * @return the stage progress percentage between {@code 0} and
     *         {@code 100}
     */
    @WhatsAppWebExport(moduleName = "WAWebGetHistorySyncProgress",
            exports = "getHistorySyncProgress", adaptation = WhatsAppAdaptation.ADAPTED)
    private static int resolveStageProgress(HistorySyncNotification notification, HistorySyncType syncType) {
        if (syncType == HistorySyncType.FULL) {
            return 100;
        }
        return notification.progress().orElse(0);
    }

    /**
     * Resolves a notification to a decoded {@link HistorySync}
     * payload, reading either the inline bootstrap payload or the
     * decrypted CDN blob.
     *
     * @apiNote Internal helper invoked from {@link #processSync}.
     * Mirrors the dispatch shape of WA Web's
     * {@code WAWebHandleHistorySyncNotification.handleHistorySyncNotification}
     * (inline-payload short-circuit) plus
     * {@code WAWebDownloadManager.downloadAndMaybeDecrypt} (CDN
     * download with HMAC verify and AES-CBC decrypt).
     *
     * @implNote This implementation returns {@code null} when neither
     * an inline payload nor a {@code (directPath, mediaKey)} pair is
     * available, which can legitimately happen for the
     * {@code MESSAGE_ACCESS_STATUS} or {@code NO_HISTORY} markers; the
     * caller short-circuits the dispatch step in that case and skips
     * the Downloaded / DataApplied metric emissions.
     *
     * @param notification the non-{@code null} notification to decode
     * @return the decoded payload, or {@code null} when the
     *         notification carries no recoverable bytes
     * @throws WhatsAppHistorySyncException if the inline payload fails
     *                                      to inflate or the CDN
     *                                      download / decode fails
     */
    @WhatsAppWebExport(moduleName = "WAWebDownloadManager", exports = "downloadAndMaybeDecrypt",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private HistorySync decode(HistorySyncNotification notification) {
        var inlinePayload = notification.initialHistBootstrapInlinePayload().orElse(null);
        if (inlinePayload != null && inlinePayload.length > 0) {
            try (var stream = new InflaterInputStream(new ByteArrayInputStream(inlinePayload))) {
                return decodeHistorySync(stream);
            } catch (Exception exception) {
                throw new WhatsAppHistorySyncException("Failed to decode inline history bootstrap payload", exception);
            }
        }

        if (notification.directPath().isEmpty() || notification.mediaKey().isEmpty()) {
            return null;
        }

        var downloadStart = Instant.now();
        try (var stream = mediaConnectionService.download(notification)) {
            var decoded = decodeHistorySync(stream);
            commitMediaDownload2Success(downloadStart);
            return decoded;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new WhatsAppHistorySyncException("Interrupted while waiting for media connection", exception);
        } catch (Exception exception) {
            commitMediaDownload2Failure(downloadStart, exception);
            throw new WhatsAppHistorySyncException("Failed to download or decode history sync chunk", exception);
        }
    }

    /**
     * Commits a successful
     * {@link MediaDownload2EventBuilder MediaDownload2Event} for the
     * just-finished history sync chunk download.
     *
     * @apiNote Internal helper invoked from the success branch of
     * {@link #decode(HistorySyncNotification)}. Mirrors WA Web's
     * {@code WAWebCreateMediaDownloadMetrics.createMediaDownloadMetrics}
     * with the constants for the {@code md-msg-hist} flow:
     * {@code overallMediaType=MD_HISTORY_SYNC},
     * {@code overallMmsVersion=4},
     * {@code overallDownloadOrigin=MESSAGE_HISTORY_SYNC},
     * {@code overallDownloadMode=FULL}.
     *
     * @param downloadStart the wall-clock instant at which the
     *                      download attempt began
     */
    @WhatsAppWebExport(moduleName = "WAWebCreateMediaDownloadMetrics",
            exports = "createMediaDownloadMetrics",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void commitMediaDownload2Success(Instant downloadStart) {
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
     * Commits a failing
     * {@link MediaDownload2EventBuilder MediaDownload2Event} for the
     * just-attempted history sync chunk download.
     *
     * @apiNote Internal helper invoked from the failure branch of
     * {@link #decode(HistorySyncNotification)}. Mirrors WA Web's
     * {@code WAWebCreateMediaDownloadMetrics.handleDownloadError}
     * which sets {@code downloadHttpCode} only when a status code is
     * present on the failure.
     *
     * @param downloadStart the wall-clock instant at which the
     *                      download attempt began
     * @param throwable     the failure that aborted the download
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
        var statusCode = extractHttpStatusCode(throwable);
        if (statusCode != null) {
            builder.downloadHttpCode(statusCode);
        }
        wamService.commit(builder.build());
    }

    /**
     * Maps a download failure to the matching
     * {@link MediaDownloadResultType}.
     *
     * @apiNote Internal helper invoked from
     * {@link #commitMediaDownload2Failure(Instant, Throwable)}.
     * Mirrors WA Web's
     * {@code WAWebWamMediaMetricUtils.getMetricDownloadErrorResultType}
     * which classifies the failure by HTTP status code, with no status
     * meaning {@link MediaDownloadResultType#ERROR_NETWORK} and an
     * unrecognised status meaning
     * {@link MediaDownloadResultType#ERROR_UNKNOWN}.
     *
     * @param throwable the failure raised by the download path
     * @return the mapped {@link MediaDownloadResultType}
     */
    @WhatsAppWebExport(moduleName = "WAWebWamMediaMetricUtils",
            exports = "getMetricDownloadErrorResultType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static MediaDownloadResultType classifyMediaDownloadError(Throwable throwable) {
        for (var current = throwable; current != null; current = current.getCause()) {
            if (current instanceof WhatsAppMediaException.Download download) {
                var optStatus = download.httpStatusCode();
                if (optStatus.isEmpty()) {
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
     * Extracts the HTTP status code embedded in a download failure, if
     * any.
     *
     * @apiNote Internal helper invoked from
     * {@link #commitMediaDownload2Failure(Instant, Throwable)}.
     * Mirrors WA Web's {@code WAWebWamMediaMetricUtils.getStatusCode}.
     *
     * @param throwable the failure raised by the download path
     * @return the status code, or {@code null} when none is available
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
     * Decodes a plaintext history sync byte stream into a
     * {@link HistorySync} payload.
     *
     * @apiNote Internal helper invoked from
     * {@link #decode(HistorySyncNotification)} for both the inline and
     * CDN paths. Mirrors WA Web's
     * {@code decodeProtobuf(WAWebProtobufsHistorySync.pb.HistorySyncSpec, ...)}.
     *
     * @implNote This implementation routes through the lightweight
     * {@link HistorySync#ofLight(ProtobufInputStream)} variant when the
     * embedder configured a zero
     * {@code webHistoryPolicy}, which trims the wire-decoded payload to
     * the fields Cobalt actually projects into the listener API; the
     * full decoder is otherwise used since WA Web makes no equivalent
     * distinction at the wire level.
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
     * Applies every wire-shape mutation carried by the decoded chunk to
     * the {@link WhatsAppStore} and fans the result out to every
     * registered {@code WhatsAppClientListener}.
     *
     * @apiNote Internal helper invoked from the success branch of
     * {@link #processSync}. Replaces the per-collection
     * {@code WAWebHandleAddChats} / {@code WAWebDBProcessInitialHistorySyncMessage}
     * / {@code WAWebHistorySyncHandlePushname} /
     * {@code WAWebHistorySyncHandleStatusMessages} /
     * {@code WAWebVoipActionWriteCallLogSync} fan-out that WA Web's
     * {@code WAWebHandleHistorySyncChunk.handleHistorySyncChunk} drives
     * inline through {@code WAWebHistoryMsgHandlerAction} and its
     * collaborators.
     *
     * @implNote This implementation runs every listener callback on a
     * dedicated virtual thread so a slow listener cannot block the next
     * chunk or the LID-mapping ingest. Per-chunk store mutations cover
     * the data shapes that have an equivalent in
     * {@link WhatsAppStore}: every {@code conversation} is merged into
     * the local {@link Chat} record (metadata fields plus embedded
     * messages); every {@code pushname} is folded into a
     * {@link com.github.auties00.cobalt.model.contact.Contact} via
     * {@link com.github.auties00.cobalt.model.contact.Contact#setChosenName(String)};
     * every {@code statusV3Messages} entry is appended to
     * {@link WhatsAppStore#status()}; every {@code callLogRecords}
     * entry that carries a callId is registered through
     * {@link WhatsAppStore#addCallLog(com.github.auties00.cobalt.model.call.CallLog)};
     * every {@code recentStickers} entry that carries a {@code fileSha256}
     * is folded into a {@link com.github.auties00.cobalt.model.preference.Sticker}
     * via {@link WhatsAppStore#addRecentSticker(String, com.github.auties00.cobalt.model.preference.Sticker)},
     * keyed by the base64-encoded plaintext hash to match WA Web's
     * {@code WAWebRecentStickerCollectionMd} row id; the
     * {@code companionMmsAuthNonce} (wire field {@code companionMetaNonce})
     * is captured into
     * {@link WhatsAppStore#setCompanionMmsAuthNonce(String)} so it can
     * authorise the post-apply MMS blob-deletion call, matching WA Web's
     * write to {@code userPrefsIdb["WAWebCompanionMetaNonce"]} inside
     * {@code WAWebHistoryMsgHandlerAction.handleInitialSyncMsgs};
     * the {@code shareableChatLinkKey} (wire field
     * {@code shareableChatIdentifierEncryptionKey}) is captured into
     * {@link WhatsAppStore#setShareableChatLinkKey(byte[])} and
     * persisted alongside the rest of the store so the
     * shareable-chat-link encryption material survives across restarts;
     * the WA Web bundle itself never reads the value (the deep-link
     * generator is server-driven), so capturing it is the full extent
     * of client-side responsibility.
     * Past participants are forwarded only as a listener event because
     * Cobalt's store has no past-participants collection. The
     * {@code threadIdUserSecret} and {@code threadDsTimeframeOffset}
     * fields are intentionally not consumed: WA Web feeds them through
     * {@code WAWebHistorySyncNotificationUtils.handleChatThreadLoggingMetadata}
     * into {@code WAWebChatThreadLogging.metadataStore} (the HMAC seed
     * and disappearing-mode window offset for Meta's internal
     * Chat-Thread-Logging analytics pipeline), and Cobalt has no
     * equivalent of that pipeline (the same Falco-style telemetry
     * surface elided from the error model and from the broader
     * receive-path code).
     * The {@code globalSettings} sub-message is decoded for protobuf
     * parity but deliberately not projected onto the store, because
     * WA Web does not read it from the history-sync chunk either: the
     * authoritative sources for those preferences are the app-state
     * (syncd) collections instead. {@code chatLockSettings} arrives
     * via {@code WAWebChatLockSettingsSync}, the disappearing-mode
     * default and security-notifications flag arrive via separate
     * syncd / user-prefs flows, and the bundle never accesses
     * {@code historySync.globalSettings} at runtime.
     * Terminal "sync complete" callbacks mirror WA Web's {@code Y()}
     * dispatcher: the {@code syncedChats} flag flips when the
     * {@link HistorySyncType#INITIAL_BOOTSTRAP} chunk lands
     * (triggerInitialChatHistorySynced), {@code syncedContacts} flips
     * on the {@link HistorySyncType#PUSH_NAME} chunk, and
     * {@code syncedStatus} flips on the
     * {@link HistorySyncType#INITIAL_STATUS_V3} chunk; the
     * {@code onChats} / {@code onContacts} / {@code onStatus} callbacks
     * each fire exactly once per session, with the full store
     * collection, even when the originating chunk is empty.
     *
     * @param historySync the decoded payload to dispatch
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleHistorySyncChunk", exports = "handleHistorySyncChunk",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void dispatch(HistorySync historySync) {
        lidMigrationService.processHistorySync(historySync);

        var store = whatsapp.store();
        var listeners = store.listeners();
        var syncType = historySync.syncType();
        var progressValue = historySync.progress().orElse(0);
        var recent = syncType == HistorySyncType.RECENT;
        var isLastChunk = progressValue >= 100;

        for (var listener : listeners) {
            Thread.startVirtualThread(() -> listener.onWebHistorySyncProgress(whatsapp, progressValue, recent));
        }

        for (var historyChat : historySync.chats()) {
            applyChat(historyChat, store);
            for (var listener : listeners) {
                Thread.startVirtualThread(() -> listener.onWebHistorySyncMessages(whatsapp, historyChat, isLastChunk));
            }
        }

        for (var pushname : historySync.pushnames()) {
            applyPushname(pushname, store);
        }

        for (var statusMessage : historySync.statusV3Messages()) {
            store.addStatus(statusMessage);
            for (var listener : listeners) {
                Thread.startVirtualThread(() -> listener.onNewStatus(whatsapp, statusMessage));
            }
        }

        for (var callLog : historySync.callLogRecords()) {
            if (callLog.callId().isPresent()) {
                store.addCallLog(callLog);
            }
        }

        for (var sticker : historySync.recentStickers()) {
            applyRecentSticker(sticker, store);
        }

        historySync.companionMmsAuthNonce()
                .ifPresent(store::setCompanionMmsAuthNonce);

        historySync.shareableChatLinkKey()
                .ifPresent(store::setShareableChatLinkKey);

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

        if (syncType == HistorySyncType.INITIAL_BOOTSTRAP && !store.syncedChats()) {
            store.setSyncedChats(true);
            var chats = store.chats();
            for (var listener : listeners) {
                Thread.startVirtualThread(() -> listener.onChats(whatsapp, chats));
            }
        }

        if (syncType == HistorySyncType.PUSH_NAME && !store.syncedContacts()) {
            store.setSyncedContacts(true);
            var contacts = store.contacts();
            for (var listener : listeners) {
                Thread.startVirtualThread(() -> listener.onContacts(whatsapp, contacts));
            }
        }

        if (syncType == HistorySyncType.INITIAL_STATUS_V3 && !store.syncedStatus()) {
            store.setSyncedStatus(true);
            var status = store.status();
            for (var listener : listeners) {
                Thread.startVirtualThread(() -> listener.onStatus(whatsapp, status));
            }
        }
    }

    /**
     * Folds one history-sync {@link StickerMetadata} entry into the
     * local recent-stickers collection.
     *
     * @apiNote Internal helper invoked from {@link #dispatch}. Replaces
     * WA Web's {@code WAWebHistorySyncStickers.processRecentStickers},
     * which assembles a row keyed by the base64-encoded plaintext file
     * digest and hands the batch off to
     * {@code WAWebRecentStickerCollectionMd.replaceAndEnqueue}.
     *
     * @implNote This implementation mirrors WA Web's hashing convention
     * exactly: the row key is the standard base64 encoding of
     * {@link StickerMetadata#fileSha256()} and the entry is dropped
     * outright when that digest is absent, matching WA Web's
     * {@code n == null} skip in {@code processRecentStickers}. The
     * resulting {@link com.github.auties00.cobalt.model.preference.Sticker}
     * is built with {@code favorite = false} and
     * {@code deviceIdHint = null} because neither field is carried by
     * the history-sync wire shape. The
     * {@code WAWebUserPrefsAppStateSync.setNonCriticalDataSyncStatus(RECENT_STICKER_INITIALIZED)}
     * call WA Web emits after the batch is elided because Cobalt has no
     * equivalent user-prefs surface.
     *
     * @param sticker the wire-shape sticker record
     * @param store   the store to mutate
     */
    @WhatsAppWebExport(moduleName = "WAWebHistorySyncStickers",
            exports = "processRecentStickers",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private static void applyRecentSticker(StickerMetadata sticker, WhatsAppStore store) {
        var fileSha256 = sticker.fileSha256().orElse(null);
        if (fileSha256 == null) {
            return;
        }
        var stickerHash = Base64.getEncoder().encodeToString(fileSha256);
        var lastSent = sticker.lastStickerSentTs()
                .map(Instant::getEpochSecond)
                .orElse(null);
        var built = new StickerBuilder()
                .mediaUrl(sticker.url().orElse(null))
                .mediaEncryptedSha256(sticker.fileEncSha256().orElse(null))
                .mediaKey(sticker.mediaKey().orElse(null))
                .mimetype(sticker.mimetype().orElse(null))
                .height(sticker.height().isPresent() ? sticker.height().getAsInt() : null)
                .width(sticker.width().isPresent() ? sticker.width().getAsInt() : null)
                .mediaDirectPath(sticker.directPath().orElse(null))
                .mediaSize(sticker.fileLength().isPresent() ? sticker.fileLength().getAsLong() : null)
                .favorite(false)
                .timestamp(lastSent)
                .isAvatar(sticker.isAvatarSticker())
                .build();
        store.addRecentSticker(stickerHash, built);
    }

    /**
     * Asks WhatsApp's MMS to release the just-applied history-sync blob
     * from the CDN.
     *
     * @apiNote Internal helper invoked from {@link #processSync} once a
     * chunk has been dispatched into the store. Mirrors the
     * {@code WAWebMmsClient.deleteMdHistorySyncBlob} call WA Web fires
     * at the tail of
     * {@code WAWebHandleHistorySyncChunk.handleHistorySyncChunk}; the
     * server uses the {@code companionMmsAuthNonce} delivered with the
     * bootstrap chunk as proof that the same companion that received
     * the blob is the one asking to delete it.
     *
     * @implNote This implementation hands the delete off to a dedicated
     * virtual thread so the synchronous part of {@link #processSync}
     * (the WAM metric emission and the eventual receipt-send path)
     * does not block on a CDN round trip, matching the fire-and-forget
     * shape of WA Web's trailing {@code .catch} on the same call. Any
     * failure is logged at {@code WARNING} and swallowed; the blob
     * release is opportunistic, never load-bearing on the apply flow.
     * The release is skipped when the notification carries no
     * {@code (directPath, fileEncSha256)} pair (the same
     * {@code e.downloadOptions.encFilehash != null} guard WA Web
     * applies), or when the store has no media connection or no
     * companion-MMS auth nonce yet.
     *
     * @param notification the notification whose CDN blob is being
     *                     released
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleHistorySyncChunk",
            exports = "handleHistorySyncChunk",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private void releaseMmsBlob(HistorySyncNotification notification) {
        var directPath = notification.directPath().orElse(null);
        if (directPath == null || directPath.isEmpty()) {
            return;
        }
        var encFilehash = notification.fileEncSha256().orElse(null);
        if (encFilehash == null || encFilehash.length == 0) {
            return;
        }
        var encHandle = notification.encHandle().orElse(null);
        var store = whatsapp.store();
        var nonce = store.companionMmsAuthNonce().orElse(null);
        Thread.startVirtualThread(() -> {
            try {
                mediaConnectionService.deleteHistorySyncBlob(directPath, encFilehash, encHandle, nonce);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException exception) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "MMS client delete error", exception);
            }
        });
    }

    /**
     * Merges one wire-shape history-sync chat into the local store and
     * appends its embedded messages to the corresponding store-side
     * {@link Chat}.
     *
     * @apiNote Internal helper invoked from {@link #dispatch}. Replaces
     * the {@code WAWebHandleAddChats} + {@code processMultipleMessages}
     * + {@code storeInitialSyncMessages} composition that WA Web's
     * {@code WAWebHistoryMsgHandlerAction.handleInitialSyncMsgs} drives
     * inline.
     *
     * @implNote This implementation looks up the existing store chat
     * keyed by {@link Chat#jid()} and creates a fresh record via
     * {@link WhatsAppStore#addNewChat(Jid)} only when none exists; the
     * wire-shape chat is never inserted directly because the store is
     * typed against its concrete {@code TemporaryChat} /
     * {@code PersistentChat} subclasses, neither of which is the
     * history-sync variant. Metadata fields are then transcribed
     * field-by-field through {@link #copyChatMetadata(Chat, Chat)},
     * preserving any local edits that the store already holds, and the
     * embedded messages from the chunk are appended to the local chat
     * via {@link Chat#addMessage(com.github.auties00.cobalt.model.chat.ChatMessageInfo)}.
     *
     * @param historyChat the wire-shape chat carrying chunk metadata and
     *                    embedded messages
     * @param store       the store to mutate
     */
    private static void applyChat(Chat historyChat, WhatsAppStore store) {
        var jid = historyChat.jid();
        var localChat = store.findChatByJid(jid)
                .orElseGet(() -> store.addNewChat(jid));
        if (localChat != historyChat) {
            copyChatMetadata(historyChat, localChat);
        }
        historyChat.messages().forEach(localChat::addMessage);
    }

    /**
     * Copies every present chat-metadata field from {@code from} to
     * {@code to}.
     *
     * @apiNote Internal helper invoked from
     * {@link #applyChat(Chat, WhatsAppStore)}. Mirrors the metadata
     * block that WA Web's
     * {@code WAWebHistoryMsgHandlerAction.handleInitialSyncMsgs}
     * assembles as the {@code pe} record before handing it to
     * {@code WAWebHandleAddChats}.
     *
     * @implNote This implementation only touches the target chat when
     * the source carries a present value, so a partial chunk does not
     * silently null-out a field that a previous chunk or an app-state
     * mutation has already populated. The merge is best-effort; fields
     * with no setter on {@link Chat} (computed properties such as
     * {@link Chat#messageCount()}) are skipped.
     *
     * @param from the wire-shape source chat
     * @param to   the store-resident target chat
     */
    private static void copyChatMetadata(Chat from, Chat to) {
        from.newJid().ifPresent(to::setNewJid);
        from.oldJid().ifPresent(to::setOldJid);
        from.lastMsgTimestamp().ifPresent(to::setLastMsgTimestamp);
        from.unreadCount().ifPresent(to::setUnreadCount);
        to.setReadOnly(from.readOnly());
        to.setEndOfHistoryTransfer(from.endOfHistoryTransfer());
        from.ephemeralExpiration().ifPresent(to::setEphemeralExpiration);
        from.ephemeralSettingTimestamp().ifPresent(to::setEphemeralSettingTimestamp);
        from.endOfHistoryTransferType().ifPresent(to::setEndOfHistoryTransferType);
        from.conversationTimestamp().ifPresent(to::setConversationTimestamp);
        from.name().ifPresent(to::setName);
        from.pHash().ifPresent(to::setPHash);
        to.setNotSpam(from.notSpam());
        to.setArchived(from.archived());
        from.disappearingMode().ifPresent(to::setDisappearingMode);
        from.unreadMentionCount().ifPresent(to::setUnreadMentionCount);
        to.setMarkedAsUnread(from.markedAsUnread());
        if (!from.participant().isEmpty()) {
            to.setParticipant(from.participant());
        }
        from.tcToken().ifPresent(to::setTcToken);
        from.tcTokenTimestamp().ifPresent(to::setTcTokenTimestamp);
        from.contactPrimaryIdentityKey().ifPresent(to::setContactPrimaryIdentityKey);
        from.pinnedTimestamp().ifPresent(to::setPinnedTimestamp);
        from.mute().ifPresent(to::setMute);
        from.wallpaper().ifPresent(to::setWallpaper);
        from.mediaVisibility().ifPresent(to::setMediaVisibility);
        from.tcTokenSenderTimestamp().ifPresent(to::setTcTokenSenderTimestamp);
        to.setSuspended(from.suspended());
        to.setTerminated(from.terminated());
        from.createdAt().ifPresent(to::setCreatedAt);
        from.createdBy().ifPresent(to::setCreatedBy);
        from.description().ifPresent(to::setDescription);
        to.setSupport(from.support());
        to.setParentGroup(from.isParentGroup());
        from.parentGroupId().ifPresent(to::setParentGroupId);
        to.setDefaultSubgroup(from.isDefaultSubgroup());
        from.displayName().ifPresent(to::setDisplayName);
        from.phoneNumberJid().ifPresent(to::setPhoneNumberJid);
        to.setShareOwnPhoneNumber(from.shareOwnPhoneNumber());
        to.setPhoneNumberDuplicateLidThread(from.phoneNumberhDuplicateLidThread());
        from.lid().ifPresent(to::setLid);
        from.username().ifPresent(to::setUsername);
        from.lidOriginType().ifPresent(to::setLidOriginType);
        from.commentsCount().ifPresent(to::setCommentsCount);
        to.setLocked(from.locked());
        from.systemMessageToInsert().ifPresent(to::setSystemMessageToInsert);
        to.setCapiCreatedGroup(from.capiCreatedGroup());
        from.accountLid().ifPresent(to::setAccountLid);
        to.setLimitSharing(from.limitSharing());
        from.limitSharingSettingTimestamp().ifPresent(to::setLimitSharingSettingTimestamp);
        from.limitSharingTrigger().ifPresent(to::setLimitSharingTrigger);
        to.setLimitSharingInitiatedByMe(from.limitSharingInitiatedByMe());
        to.setMaibaAiThreadEnabled(from.maibaAiThreadEnabled());
    }

    /**
     * Updates the local contact for one history-sync push-name record.
     *
     * @apiNote Internal helper invoked from {@link #dispatch}. Replaces
     * WA Web's {@code WAWebHistorySyncHandlePushname.handlePushName},
     * which bulk-creates contact rows via
     * {@code WAWebLidAwareContactsDB.bulkCreateOrMerge} and pushes the
     * update to the frontend via
     * {@code bulkUpdateContactPushnames}.
     *
     * @implNote This implementation parses the wire-string identifier
     * through {@link Jid#of(String)} and silently drops entries whose
     * id is unparseable, mirroring WA Web's {@code createUserWidOrThrow}
     * fan-out that catches and discards individual failures in the
     * surrounding loop. A new contact is created via
     * {@link WhatsAppStore#addNewContact(Jid)} when no entry exists
     * because WA Web's bulkCreateOrMerge inserts the row in that case
     * too. Push-names that are empty strings are skipped instead of
     * being written, matching the {@code !r("isStringNullOrEmpty")}
     * guard used elsewhere in WA Web for the same field.
     *
     * @param pushname the wire-shape pushname record
     * @param store    the store to mutate
     */
    private static void applyPushname(HistorySync.Pushname pushname, WhatsAppStore store) {
        var rawId = pushname.id().orElse(null);
        if (rawId == null || rawId.isEmpty()) {
            return;
        }
        Jid jid;
        try {
            jid = Jid.of(rawId);
        } catch (IllegalArgumentException _) {
            return;
        }
        if (jid == null) {
            return;
        }
        var name = pushname.pushname().orElse(null);
        if (name == null || name.isEmpty()) {
            return;
        }
        var contact = store.findContactByJid(jid)
                .orElseGet(() -> store.addNewContact(jid));
        contact.setChosenName(name);
    }
}
