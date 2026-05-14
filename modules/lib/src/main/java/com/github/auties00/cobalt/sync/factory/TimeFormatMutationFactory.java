package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.device.TimeFormatAction;
import com.github.auties00.cobalt.model.sync.action.device.TimeFormatActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Builds outgoing time-format sync mutations.
 *
 * <p>The factory is the outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.TimeFormatHandler}.
 */
public final class TimeFormatMutationFactory {
    /**
     * Constructs a time-format mutation factory.
     */
    public TimeFormatMutationFactory() {

    }

    /**
     * Builds a pending {@code time_format} mutation that broadcasts the
     * given 12h/24h preference to every linked device.
     *
     * <p>WA Web does not expose a dedicated {@code getTimeFormatMutation} on
     * {@code WAWebTimeFormatSync}; outgoing time-format changes reuse
     * {@code WAWebSyncdActionUtils.buildPendingMutation} directly. Cobalt
     * surfaces the typed helper — mirroring sibling handlers — so the public
     * {@code WhatsAppClient.editTwentyFourHourFormat} setter can build a single
     * mutation without hand-rolling the protobuf wrapping.
     *
     * @param timestamp               the mutation timestamp
     * @param twentyFourHourFormat    {@code true} to enable 24-hour display,
     *                                {@code false} for 12-hour display
     * @return a pending mutation carrying the {@code time_format} action
     * @throws NullPointerException if {@code timestamp} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdActionUtils", exports = "buildPendingMutation", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getTimeFormatMutation(Instant timestamp, boolean twentyFourHourFormat) {
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        var action = new TimeFormatActionBuilder() // ADAPTED: WAWebSyncdActionUtils.buildPendingMutation value shape: {timeFormatAction: {isTwentyFourHourFormatEnabled: t}}
                .isTwentyFourHourFormatEnabled(twentyFourHourFormat)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .timeFormatAction(action)
                .build();
        var index = JSON.toJSONString(List.of(TimeFormatAction.ACTION_NAME)); // ADAPTED: WAWebSyncdActionUtils.buildPendingMutation: index = JSON.stringify([action]) with indexArgs = []
        var pending = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET, // ADAPTED: WAWebSyncdActionUtils.buildPendingMutation: operation: SyncdOperation.SET
                timestamp,
                TimeFormatAction.ACTION_VERSION
        );
        return new SyncPendingMutation(pending, 0);
    }
}
