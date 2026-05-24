package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.media.NewsletterSavedInterestsAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Applies {@link NewsletterSavedInterestsAction} sync mutations carrying
 * the user's saved newsletter interest selections.
 *
 * @apiNote
 * Persists an opaque, server-defined token blob describing the user's
 * subscribed newsletter interest categories on
 * {@link com.github.auties00.cobalt.store.WhatsAppStore} so the
 * personalised channel directory can render the same selection across
 * linked devices. Only {@link SyncdOperation#SET} is accepted; any
 * other operation is reported as
 * {@link MutationApplicationResult#unsupported()} and a missing or
 * empty {@code newsletterSavedInterests} field as
 * {@link MutationApplicationResult#malformed()}.
 *
 * @implNote
 * This implementation has no WA Web counterpart: the
 * {@code SyncActionValue.NewsletterSavedInterestsAction} protobuf is
 * declared in {@code WAWebProtobufSyncAction.pb} and the inline
 * collection router maps it to {@code REGULAR}, but no
 * {@code WAWebNewsletterSavedInterestsSync} module ships in the
 * current snapshot and the action is absent from
 * {@code WAWebCollectionHandlerActions.ActionHandlers}, so WA Web
 * silently drops any incoming mutation. The handler exists in Cobalt
 * as a forward-looking implementation that follows the same singleton
 * + {@code applyMutation} contract as every other registered handler.
 */
public final class NewsletterSavedInterestsHandler implements WebAppStateActionHandler {

    /**
     * Constructs the newsletter saved interests sync handler.
     *
     * @apiNote
     * Used by the sync handler registry; consumers should never need to
     * call this constructor directly.
     *
     * @implNote
     * This implementation is stateless; the handler holds no
     * AB-prop / store / WAM dependency.
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
     * This implementation returns {@link SyncPatchType#REGULAR} as
     * declared by the inline router in
     * {@code WAWebProtobufSyncAction.pb}
     * ({@code e===c.NEWSLETTER_SAVED_INTERESTS_ACTION?u.REGULAR}).
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
     * @implNote
     * This implementation accepts only {@link SyncdOperation#SET}: the
     * action carries a single string token and there is no semantic for
     * {@code REMOVE}. The
     * {@link NewsletterSavedInterestsAction#newsletterSavedInterests()}
     * field is the only field on the protobuf and is required for any
     * meaningful update; an empty value is rejected as
     * {@link MutationApplicationResult#malformed()}. On success the
     * resolved token is written via
     * {@code WhatsAppStore.setNewsletterSavedInterests}.
     */
    @Override
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof NewsletterSavedInterestsAction action)
                || action.newsletterSavedInterests().isEmpty()) {
            return MutationApplicationResult.malformed();
        }

        client.store().setNewsletterSavedInterests(action.newsletterSavedInterests().get());
        return MutationApplicationResult.success();
    }
}
