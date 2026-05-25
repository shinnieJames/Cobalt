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
 * <p>The sync dispatcher routes incoming mutations here whenever a
 * Business-account subscription state changes. The handler rewrites the
 * {@link com.github.auties00.cobalt.store.WhatsAppStore} business feature flag
 * and subscription tables in full ("rewrite" semantics) so the next read sees
 * only the new snapshot.
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
     * <p>The handler is stateless; Cobalt's sync registry holds a single
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
     * <p>On {@link SyncdOperation#SET} the decoded {@link SubscriptionsSyncV2Action}
     * clears and refills the two {@link com.github.auties00.cobalt.store.WhatsAppStore}
     * maps that back business feature flags and subscriptions; a value that does
     * not decode is reported as malformed. {@link SyncdOperation#REMOVE} returns
     * {@link MutationApplicationResult#success()} without mutating state; any other
     * operation, and any thrown exception, resolves to
     * {@link MutationApplicationResult#failed()}.
     *
     * @implNote
     * WA Web's {@code WAWebSubscriptions.applySubscriptionsAndFeatureFlags(..., "rewrite")}
     * is collapsed into direct store updates because Cobalt's flattened store is
     * the persistence layer; the {@code WAWebODS} subscription-sync telemetry calls
     * are dropped while the outer {@code try/catch -> Failed} wrapper is kept.
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
     * <p>Applies each mutation through
     * {@link #applyMutation(WhatsAppClient, DecryptedMutation.Trusted)}, counts the
     * successful {@link SyncdOperation#REMOVE} operations, and logs a single
     * batch-level warning when at least one was observed.
     *
     * @implNote
     * This implementation tracks the per-batch {@code REMOVE} count in a local
     * variable rather than as a mutable field because Cobalt treats handlers as
     * stateless services; the warning matches WA Web's trailing
     * {@code WALogger.WARN("[SubscriptionsSyncV2Sync] N REMOVE ops (singleton)")}.
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
