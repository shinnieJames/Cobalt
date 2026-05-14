package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.media.RemoveRecentStickerAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import java.time.Instant;

/**
 * Handles {@code removeRecentSticker} app-state sync mutations.
 *
 * <p>Per WhatsApp Web {@code WAWebStickersRemoveRecentSyncAction.applyMutations}:
 * <ol>
 *   <li>If the {@code "recent_sticker"} primary feature is not enabled, every
 *       mutation in the batch is reported as {@code Unsupported}.</li>
 *   <li>Non-{@code SET} operations are acknowledged with {@code Unsupported}.</li>
 *   <li>The sticker file hash is read from {@code indexParts[1]}; a missing value
 *       yields {@code malformedActionIndex}.</li>
 *   <li>The {@code lastStickerSentTs} field is read from
 *       {@code value.removeRecentStickerAction} via an optional chain; either a
 *       missing sub-message or a missing field results in {@code null}.</li>
 *   <li>The recent-stickers collection is queried by hash; a missing entry is
 *       reported as {@code Orphan} (with no model id/type).</li>
 *   <li>If {@code lastStickerSentTs} is {@code null}, OR the local sticker's
 *       {@code timestamp} is less than or equal to {@code lastStickerSentTs},
 *       the entry is removed from the recent-stickers collection.</li>
 *   <li>The mutation always returns {@code Success} after reaching the
 *       collection lookup step (whether or not the removal was performed).</li>
 * </ol>
 *
 * <p>Index format: {@code ["removeRecentSticker", stickerFileHash]}.
 */
@WhatsAppWebModule(moduleName = "WAWebStickersRemoveRecentSyncAction")
public final class RemoveRecentStickerHandler implements WebAppStateActionHandler {
    /**
     * The {@code "recent_sticker"} primary feature flag name.
     *
     * <p>Per WhatsApp Web {@code WAWebMiscGatingUtils.isRecentStickersMDEnabled}:
     * {@code function c() { return WAWebPrimaryFeatures.primaryFeatureEnabled("recent_sticker"); }}.
     * The handler must consult the primary device's reported feature set rather
     * than any AB prop, since recent-sticker sync is gated on the primary's
     * support for the feature, not on a per-companion experiment.
     */
    @WhatsAppWebExport(moduleName = "WAWebMiscGatingUtils", exports = "isRecentStickersMDEnabled", adaptation = WhatsAppAdaptation.ADAPTED)
    private static final String RECENT_STICKER_FEATURE = "recent_sticker";

    /**
     * Constructs the singleton instance.
     */
    @WhatsAppWebExport(moduleName = "WAWebStickersRemoveRecentSyncAction", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public RemoveRecentStickerHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStickersRemoveRecentSyncAction", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return RemoveRecentStickerAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStickersRemoveRecentSyncAction", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return RemoveRecentStickerAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStickersRemoveRecentSyncAction", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return RemoveRecentStickerAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Implements the body of
     * {@code WAWebStickersRemoveRecentSyncAction.applyMutations} for a single
     * mutation. The WA Web batch counter logging that aggregates the
     * {@code notSupported} and {@code malformed} buckets via {@code WALogger.WARN}
     * is intentionally omitted (WAM/telemetry).
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStickersRemoveRecentSyncAction", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        //   if (!WAWebMiscGatingUtils.isRecentStickersMDEnabled())
        //     return WALogger.WARN("syncd: remove recent sticker operation not supported"),
        //            t.map(() => ({actionState: Unsupported}))
        //   return WAWebPrimaryFeatures.primaryFeatureEnabled("recent_sticker")
        if (!client.store().primaryFeatures().contains(RECENT_STICKER_FEATURE)) {
            return MutationApplicationResult.unsupported();
        }

        //   if (e.operation !== "set") return r++, {actionState: Unsupported}
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        //   var i = e.indexParts, l = i[1]
        //   if (l == null) return a++, n.malformedActionIndex()
        // indexParts[1] is undefined when the slot is missing; mirror with explicit size check.
        var indexArray = JSON.parseArray(mutation.index());
        if (indexArray.size() <= 1) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }
        var stickerHash = indexArray.getString(1);
        if (stickerHash == null) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        //   var s = (t = e.value.removeRecentStickerAction) == null ? void 0 : t.lastStickerSentTs
        // The optional-chain returns undefined when either the sub-message OR the
        // lastStickerSentTs field is missing; both cases collapse into c == null below.
        var action = mutation.value().action().orElse(null) instanceof RemoveRecentStickerAction entry ? entry : null;
        var lastStickerSentTs = action == null ? null : action.lastStickerSentTs().orElse(null);

        //   var u = WAWebRecentStickerCollectionMd.RecentStickerCollectionMd.get(l)
        //   if (!u) return {actionState: Orphan}
        var sticker = client.store().findRecentSticker(stickerHash);
        if (sticker.isEmpty()) {
            return MutationApplicationResult.orphan();
        }

        //   var c = WALongInt.maybeNumberOrThrowIfTooLarge(s)
        //   (c == null || WALongInt.numberOrThrowIfTooLarge(u.timestamp) <= c)
        //     && WAWebRecentStickerCollectionMd.RecentStickerCollectionMd.removeAndSave(u)
        //
        // WALongInt.maybeNumberOrThrowIfTooLarge: returns null when the input is null/undefined,
        // otherwise asserts the value is a safe Number and returns it.
        // WALongInt.numberOrThrowIfTooLarge: asserts the value is a safe Number and returns it.
        // In Cobalt both timestamps are already plain longs, so the safe-integer guard is a no-op.
        var stickerTimestamp = sticker.get().timestamp().orElse(0L);
        if (lastStickerSentTs == null || stickerTimestamp <= toEpochComparable(lastStickerSentTs)) {
            client.store().removeRecentSticker(stickerHash);
        }

        return MutationApplicationResult.success();
    }

    /**
     * Converts an {@link Instant} representing the WhatsApp Web
     * {@code lastStickerSentTs} field to a comparable {@code long} value in the
     * same units as {@code Sticker.timestamp()}.
     *
     * <p>Per WhatsApp Web {@code WAWebStickersRemoveRecentSyncAction.applyMutations}
     * the {@code lastStickerSentTs} field carries the raw value of the recent
     * sticker's {@code timestamp} (set via {@code WATimeUtils.unixTimeMs}), and
     * the comparison is performed numerically without any unit conversion.
     *
     * <p>Cobalt's {@link RemoveRecentStickerAction#lastStickerSentTs()} accessor
     * exposes the field as an {@link Instant} (decoded via
     * {@code InstantSecondsMixin}) so the millisecond value stored on the wire is
     * reinterpreted as seconds. To preserve the original numeric comparison this
     * helper unwraps the {@code Instant} back to its raw epoch-second value.
     *
     * @apiNote The unit mismatch between {@code Sticker.timestamp()}
     *          (millis-since-epoch in WA Web) and {@code lastStickerSentTs}
     *          (decoded as epoch-seconds in Cobalt) is a bug in the
     *          {@code RemoveRecentStickerAction} protobuf model — the field
     *          should be exposed as a raw {@code OptionalLong} so that no unit
     *          assumption is encoded.
     * @param instant the {@link Instant} read from the protobuf, must not be {@code null}
     * @return the epoch-second value of {@code instant}
     */
    @WhatsAppWebExport(moduleName = "WAWebStickersRemoveRecentSyncAction", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    private static long toEpochComparable(Instant instant) {
        return instant.getEpochSecond();
    }

}
