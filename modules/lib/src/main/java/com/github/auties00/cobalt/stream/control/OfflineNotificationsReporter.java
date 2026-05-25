package com.github.auties00.cobalt.stream.control;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.stream.notification.device.NotificationSyncStreamHandler;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.MdAppStateOfflineNotificationsEventBuilder;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Tracks the per-collection multiplicity of {@code server_sync} notifications received while the offline backlog is
 * still draining and emits a single {@code MdAppStateOfflineNotifications} WAM event when the backlog window ends.
 *
 * <p>The reporter is shared between a producer and a consumer. Every time the server pushes a
 * {@code <notification type="server_sync">} stanza, the producer ({@link NotificationSyncStreamHandler}) calls
 * {@link #increment(SyncPatchType)} once per affected collection; when the offline-end bulletin arrives, the consumer
 * ({@link InfoBulletinStreamHandler}) calls {@link #report()}, which flushes the map into the WAM event with a
 * redundant count equal to the number of duplicate notifications observed for the same collection. It is wired up once
 * inside {@link com.github.auties00.cobalt.stream.SocketStream}.
 *
 * @implNote This implementation collapses WA Web's producer/consumer pair into a single shared service so the two
 * distinct {@code SocketStream.Handler} implementations can observe the same map without exposing private state on the
 * {@link WhatsAppClient}; the map is cleared atomically on flush.
 */
@WhatsAppWebModule(moduleName = "WAWebHandleReportServerSyncNotification")
public final class OfflineNotificationsReporter {
    /**
     * The {@link WhatsAppClient} retained for parity with sibling reporters; the actual WAM emission is routed through
     * {@link #wamService}.
     */
    private final WhatsAppClient whatsapp;

    /**
     * The {@link WamService} used to commit the {@code MdAppStateOfflineNotifications} event when {@link #report()}
     * runs.
     */
    private final WamService wamService;

    /**
     * The per-collection observation count for offline {@code server_sync} notifications received since the last flush.
     *
     * <p>Producers call {@link #increment(SyncPatchType)}; the consumer drains the map atomically when the offline
     * backlog window closes.
     */
    private final ConcurrentMap<SyncPatchType, Integer> offlineNotificationsCount;

    /**
     * Constructs a new reporter bound to the given client and WAM service.
     *
     * <p>The same instance is threaded through both the server-sync producer ({@link NotificationSyncStreamHandler})
     * and the offline-bulletin consumer ({@link InfoBulletinStreamHandler}).
     *
     * @param whatsapp   the {@link WhatsAppClient}; must not be {@code null}
     * @param wamService the {@link WamService} used to commit the offline-notifications event; must not be
     *                   {@code null}
     * @throws NullPointerException if {@code whatsapp} or {@code wamService} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleReportServerSyncNotification",
            exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public OfflineNotificationsReporter(WhatsAppClient whatsapp, WamService wamService) {
        this.whatsapp = Objects.requireNonNull(whatsapp, "whatsapp cannot be null");
        this.wamService = Objects.requireNonNull(wamService, "wamService cannot be null");
        this.offlineNotificationsCount = new ConcurrentHashMap<>();
    }

    /**
     * Increments the observation count for the given {@link SyncPatchType} by one.
     *
     * <p>Called once per affected collection by {@link NotificationSyncStreamHandler} for every offline
     * {@code <notification type="server_sync">} stanza. The first notification for a given collection is informational;
     * subsequent notifications for the same collection inside the same offline window are redundant and inflate the WAM
     * event's redundant count.
     *
     * @implNote This implementation uses
     * {@link ConcurrentMap#merge(Object, Object, java.util.function.BiFunction)} with the
     * {@link Integer#sum(int, int)} combiner so the bump is lock-free against producers writing other keys and against
     * the consumer's atomic drain in {@link #report()}.
     *
     * @param collection the collection whose offline notification count should be bumped; must not be {@code null}
     * @throws NullPointerException if {@code collection} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleReportServerSyncNotification",
            exports = "offlineNotificationsCount", adaptation = WhatsAppAdaptation.ADAPTED)
    public void increment(SyncPatchType collection) {
        Objects.requireNonNull(collection, "collection cannot be null");
        offlineNotificationsCount.merge(collection, 1, Integer::sum);
    }

    /**
     * Flushes the accumulated offline-notification counts as a single {@code MdAppStateOfflineNotifications} WAM event
     * and clears the map.
     *
     * <p>Called by {@link InfoBulletinStreamHandler} when the {@code <ib><offline/></ib>} bulletin announces that the
     * server has finished delivering the offline queue. The committed event carries the total number of redundant
     * notifications (the sum of {@code count - 1} over each tracked collection) as a wire-efficiency telemetry signal.
     * It is a no-op when no offline notifications were observed.
     */
    @WhatsAppWebExport(moduleName = "WAWebHandleReportServerSyncNotification",
            exports = "reportOfflineNotifications", adaptation = WhatsAppAdaptation.DIRECT)
    public void report() {
        if (offlineNotificationsCount.isEmpty()) {
            return;
        }

        var redundantCount = 0;
        for (var count : offlineNotificationsCount.values()) {
            redundantCount += count - 1;
        }

        wamService.commit(new MdAppStateOfflineNotificationsEventBuilder()
                .redundantCount(redundantCount)
                .build());

        offlineNotificationsCount.clear();
    }
}
