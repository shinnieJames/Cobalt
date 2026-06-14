package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.media.NewsletterSavedInterestsAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Applies {@link NewsletterSavedInterestsAction} sync mutations carrying the
 * user's saved newsletter interest selections.
 *
 * <p>This handler persists an opaque, server-defined token blob describing the
 * user's subscribed newsletter interest categories on
 * {@link com.github.auties00.cobalt.store.LinkedWhatsAppStore} so the personalised
 * channel directory can render the same selection across linked devices. Only
 * {@link SyncdOperation#SET} is accepted; any other operation is reported as
 * {@link MutationApplicationResult#unsupported()} and a missing or empty
 * {@link NewsletterSavedInterestsAction#newsletterSavedInterests()} field as
 * {@link MutationApplicationResult#malformed()}.
 *
 * @implNote
 * This implementation has no WA Web counterpart: the
 * {@code SyncActionValue.NewsletterSavedInterestsAction} protobuf is declared
 * in the sync-action protobuf module and the inline collection router maps it
 * to {@code REGULAR}, but no {@code WAWebNewsletterSavedInterestsSync} module
 * ships in the current snapshot and the action is absent from the WA Web
 * action-handler table, so WA Web silently drops any incoming mutation. The
 * handler exists in Cobalt as a forward-looking implementation that follows
 * the same singleton plus {@code applyMutation} contract as every other
 * registered handler.
 */
public final class NewsletterSavedInterestsHandler implements WebAppStateActionHandler {

    /**
     * Constructs a stateless {@link NewsletterSavedInterestsHandler} for
     * registration in the sync handler registry.
     */
    public NewsletterSavedInterestsHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String actionName() {
        return NewsletterSavedInterestsAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation returns {@link SyncPatchType#REGULAR} as declared by
     * the inline router in the WA Web sync-action protobuf module.
     */
    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int version() {
        return NewsletterSavedInterestsAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Only {@link SyncdOperation#SET} is accepted. The
     * {@link NewsletterSavedInterestsAction#newsletterSavedInterests()} field
     * is the only field on the protobuf and is required for any meaningful
     * update; an empty value is rejected as
     * {@link MutationApplicationResult#malformed()}. On success the resolved
     * token is written via
     * {@link com.github.auties00.cobalt.store.SettingsStore#setNewsletterSavedInterests(String)}.
     *
     * @implNote
     * This implementation rejects a {@code REMOVE} operation because the
     * action carries a single string token and there is no semantic for
     * removal.
     */
    @Override
    public MutationApplicationResult applyMutation(LinkedWhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof NewsletterSavedInterestsAction action)
                || action.newsletterSavedInterests().isEmpty()) {
            return MutationApplicationResult.malformed();
        }

        client.store().settingsStore().setNewsletterSavedInterests(action.newsletterSavedInterests().get());
        return MutationApplicationResult.success();
    }
}
