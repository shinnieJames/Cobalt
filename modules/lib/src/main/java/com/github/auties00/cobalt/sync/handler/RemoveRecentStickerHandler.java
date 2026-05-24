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
 * Removes a sticker from the recent-stickers picker shelf when the paired
 * companion device retires it.
 *
 * @apiNote
 * Cobalt embedders never invoke this handler directly; the sync dispatcher
 * routes incoming {@code removeRecentSticker} mutations here whenever the
 * paired phone curates its recent-stickers list (typical trigger: the user
 * removes a sticker from the recents tray on the phone). The handler then
 * deletes the matching entry from
 * {@link com.github.auties00.cobalt.store.WhatsAppStore}'s recent-stickers
 * map so the sticker picker on this device stops surfacing the retired
 * sticker.
 */
@WhatsAppWebModule(moduleName = "WAWebStickersRemoveRecentSyncAction")
public final class RemoveRecentStickerHandler implements WebAppStateActionHandler {
    /**
     * The primary-feature gate name that enables recent-sticker sync.
     *
     * @apiNote
     * The dispatcher must consult the paired phone's reported feature set
     * before applying any mutation; phones that have not announced this
     * feature do not maintain a recent-stickers shelf and incoming
     * mutations are reported as unsupported rather than applied.
     *
     * @implNote
     * This implementation reads the literal {@code "recent_sticker"}
     * directly from
     * {@link com.github.auties00.cobalt.store.WhatsAppStore#primaryFeatures()}
     * rather than going through
     * {@code WAWebPrimaryFeatures.primaryFeatureEnabled} as WA Web does;
     * the predicate is identical because that helper is just a containment
     * check against the same set.
     */
    @WhatsAppWebExport(moduleName = "WAWebMiscGatingUtils", exports = "isRecentStickersMDEnabled", adaptation = WhatsAppAdaptation.ADAPTED)
    private static final String RECENT_STICKER_FEATURE = "recent_sticker";

    /**
     * Constructs the handler.
     *
     * @apiNote
     * The handler is stateless; Cobalt's sync registry holds a single
     * instance per client.
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
     * @implNote
     * This implementation mirrors WA Web's per-mutation closure inside
     * {@code WAWebStickersRemoveRecentSyncAction.applyMutations} step by
     * step: feature gate, operation filter, index extraction at slot
     * {@code [1]}, optional-chain read of {@code lastStickerSentTs},
     * recent-sticker lookup, and the
     * {@code lastSent == null || localTs <= lastSent} predicate gating the
     * removal. WA Web's {@code WALongInt.maybeNumberOrThrowIfTooLarge} /
     * {@code numberOrThrowIfTooLarge} safe-integer guards are a no-op
     * because Cobalt's timestamps are already plain {@code long}. The
     * trailing {@code WALogger.WARN} aggregation of the unsupported and
     * malformed counters is omitted as telemetry.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStickersRemoveRecentSyncAction", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (!client.store().primaryFeatures().contains(RECENT_STICKER_FEATURE)) {
            return MutationApplicationResult.unsupported();
        }

        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        var indexArray = JSON.parseArray(mutation.index());
        if (indexArray.size() <= 1) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }
        var stickerHash = indexArray.getString(1);
        if (stickerHash == null) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        var action = mutation.value().action().orElse(null) instanceof RemoveRecentStickerAction entry ? entry : null;
        var lastStickerSentTs = action == null ? null : action.lastStickerSentTs().orElse(null);

        var sticker = client.store().findRecentSticker(stickerHash);
        if (sticker.isEmpty()) {
            return MutationApplicationResult.orphan();
        }

        var stickerTimestamp = sticker.get().timestamp().orElse(0L);
        if (lastStickerSentTs == null || stickerTimestamp <= toEpochComparable(lastStickerSentTs)) {
            client.store().removeRecentSticker(stickerHash);
        }

        return MutationApplicationResult.success();
    }

    /**
     * Converts the action's {@code lastStickerSentTs} {@link Instant} back to
     * an epoch-second {@code long} comparable against the local sticker
     * timestamp.
     *
     * @apiNote
     * The unit mismatch between
     * {@link RemoveRecentStickerAction#lastStickerSentTs()}
     * (decoded as epoch-seconds in Cobalt via
     * {@code InstantSecondsMixin}) and the local sticker's millis-since-epoch
     * field is a bug in the {@link RemoveRecentStickerAction} protobuf
     * model; the field should be exposed as a raw {@code OptionalLong} so no
     * unit assumption is encoded here.
     *
     * @implNote
     * This implementation unwraps the {@link Instant} to its epoch-second
     * value so the {@code localTs <= lastSent} comparison performed by
     * {@link #applyMutation(WhatsAppClient, DecryptedMutation.Trusted)}
     * stays purely numeric.
     *
     * @param instant the decoded {@link Instant} from the protobuf
     * @return the epoch-second value of {@code instant}
     */
    @WhatsAppWebExport(moduleName = "WAWebStickersRemoveRecentSyncAction", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    private static long toEpochComparable(Instant instant) {
        return instant.getEpochSecond();
    }

}
