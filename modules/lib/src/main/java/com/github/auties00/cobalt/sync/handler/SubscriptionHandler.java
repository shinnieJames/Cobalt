package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.business.BusinessFeatureFlagBuilder;
import com.github.auties00.cobalt.model.business.BusinessSubscriptionBuilder;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.device.SubscriptionsSyncV2Action;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Mirrors the paid-business subscriptions and feature flags across linked
 * devices via the {@code subscriptions_sync_v2} mutation.
 *
 * @apiNote
 * Cobalt embedders never invoke this handler directly; the sync dispatcher
 * routes incoming mutations here whenever a Business-account subscription
 * state changes (typical trigger: a new subscription is purchased,
 * cancelled, or its bundled feature flags rotate). The handler rewrites
 * the
 * {@link com.github.auties00.cobalt.store.WhatsAppStore} business feature
 * flag and subscription tables in full ("rewrite" semantics) so the next
 * read sees only the new snapshot.
 */
@WhatsAppWebModule(moduleName = "WAWebSubscriptionsSyncV2Sync")
public final class SubscriptionHandler implements WebAppStateActionHandler {
    /**
     * The canonical WA Web action name for this handler.
     */
    private static final String ACTION_NAME = "subscriptions_sync_v2";

    /**
     * The mutation format version this handler accepts.
     */
    private static final int ACTION_VERSION = 1;

    /**
     * The {@link SyncPatchType} collection this handler reads from.
     */
    private static final SyncPatchType COLLECTION_NAME = SyncPatchType.REGULAR;

    /**
     * The logger used for the batch-level {@code REMOVE} diagnostic.
     */
    private static final Logger LOGGER = Logger.getLogger(SubscriptionHandler.class.getName());

    /**
     * Constructs the handler.
     *
     * @apiNote
     * The handler is stateless; Cobalt's sync registry holds a single
     * instance per client.
     */
    @WhatsAppWebExport(moduleName = "WAWebSubscriptionsSyncV2Sync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public SubscriptionHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSubscriptionsSyncV2Sync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSubscriptionsSyncV2Sync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSubscriptionsSyncV2Sync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation mirrors WA Web's per-mutation closure inside
     * {@code WAWebSubscriptionsSyncV2Sync.applyMutations}: on
     * {@link SyncdOperation#SET} it decodes the
     * {@link SubscriptionsSyncV2Action}, then clears and refills the two
     * {@link com.github.auties00.cobalt.store.WhatsAppStore} maps that
     * back business feature flags and subscriptions. WA Web's
     * {@code WAWebSubscriptions.applySubscriptionsAndFeatureFlags(..., "rewrite")}
     * is collapsed into direct store updates because Cobalt's flattened
     * store IS the persistence layer. {@link SyncdOperation#REMOVE}
     * returns {@link MutationApplicationResult#success()} without
     * mutating state, matching WA Web's REMOVE no-op semantics; any
     * other operation surfaces as {@link MutationApplicationResult#failed()}.
     * The {@code WAWebODS} subscription-sync counter increments and the
     * outer {@code try/catch -> Failed} wrapper are kept; the underlying
     * ODS telemetry calls themselves are dropped.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSubscriptionsSyncV2Sync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        try {
            if (mutation.operation() == SyncdOperation.SET) {
                if (!(mutation.value().action().orElse(null) instanceof SubscriptionsSyncV2Action action)) {
                    return SyncdIndexUtils.malformedActionValue(collectionName().name());
                }

                client.store().clearBusinessFeatureFlags();
                for (var feature : action.paidFeatures()) {
                    feature.name().ifPresent(name -> client.store().putBusinessFeatureFlag(
                            new BusinessFeatureFlagBuilder().name(name).enabled(feature.enabled()).build()));
                }

                client.store().clearBusinessSubscriptions();
                for (var subscription : action.subscriptions()) {
                    var idOpt = subscription.id();
                    if (idOpt.isEmpty()) {
                        continue;
                    }
                    var id = idOpt.get();
                    var builder = new BusinessSubscriptionBuilder().id(id);
                    subscription.status().ifPresent(builder::status);
                    var endTime = subscription.endTime();
                    if (endTime.isPresent()) {
                        builder.expiration(Instant.ofEpochSecond(endTime.getAsLong()));
                    }
                    var creationTime = subscription.creationTime();
                    if (creationTime.isPresent()) {
                        builder.createdAt(Instant.ofEpochSecond(creationTime.getAsLong()));
                    }
                    client.store().putBusinessSubscription(builder.build());
                }

                return MutationApplicationResult.success();
            }

            if (mutation.operation() == SyncdOperation.REMOVE) {
                return MutationApplicationResult.success();
            }

            return MutationApplicationResult.failed();
        } catch (Exception e) {
            return MutationApplicationResult.failed();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation tracks the per-batch {@code REMOVE} count
     * locally rather than as a mutable field on the handler because
     * Cobalt treats handlers as stateless services. WA Web aggregates
     * the count across the closure capture of the singleton then emits
     * a trailing {@code WALogger.WARN("[SubscriptionsSyncV2Sync] N REMOVE ops (singleton)")};
     * the same WARN is emitted here via {@link Logger} only if any
     * successful REMOVE was observed.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSubscriptionsSyncV2Sync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public List<MutationApplicationResult> applyMutationBatch(WhatsAppClient client, List<DecryptedMutation.Trusted> mutations) {
        var removeCount = 0;
        var results = new ArrayList<MutationApplicationResult>(mutations.size());
        for (var mutation : mutations) {
            var result = applyMutation(client, mutation);
            if (result.actionState() == SyncActionState.SUCCESS && mutation.operation() == SyncdOperation.REMOVE) {
                removeCount++;
            }
            results.add(result);
        }
        if (removeCount > 0) {
            LOGGER.warning("[SubscriptionsSyncV2Sync] " + removeCount + " REMOVE ops (singleton)");
        }
        return results;
    }
}
