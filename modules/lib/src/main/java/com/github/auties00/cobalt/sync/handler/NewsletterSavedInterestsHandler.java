package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.media.NewsletterSavedInterestsAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
/**
 * Handles {@link NewsletterSavedInterestsAction} sync mutations
 * ({@code "newsletter_saved_interests"}).
 *
 * <p>Each mutation carries a single {@code newsletterSavedInterests} string
 * (an opaque, server-defined token blob describing the user's saved newsletter
 * interest selections) which is persisted on the local {@code WhatsAppStore}
 * via {@code setNewsletterSavedInterests}. Only {@code SET} operations are
 * accepted; any other operation maps to
 * {@link MutationApplicationResult#unsupported()} and a missing or empty value
 * maps to {@link MutationApplicationResult#malformed()}.
 *
 * <p><b>NO_WA_BASIS:</b> The
 * {@code SyncActionValue.NewsletterSavedInterestsAction} protobuf is defined
 * in {@code WAWebProtobufSyncAction.pb} as field index {@code 75} with a single
 * {@code newsletterSavedInterests: string} (index {@code 1}, exported as
 * {@code SyncActionValue$NewsletterSavedInterestsActionSpec}). The collection
 * is hardcoded to {@code REGULAR} by the protobuf-side router
 * ({@code e===c.NEWSLETTER_SAVED_INTERESTS_ACTION?u.REGULAR} in
 * {@code WAWebProtobufSyncAction.pb}). However, the current WA Web snapshot
 * does <em>not</em> ship a corresponding sync handler module (no
 * {@code WAWebNewsletterSavedInterestsSync}). The action is also absent from
 * {@code WAWebCollectionHandlerActions.ActionHandlers}, the registry consumed
 * by {@code WAWebSyncdGetActionHandler.setActionHandlers}, so WA Web would
 * never dispatch any incoming mutation with this action via
 * {@code WAWebSyncdGetActionHandler.getActionHandler("newsletter_saved_interests")}
 * (the lookup would return {@code undefined} and the mutation would be
 * skipped). The closest WA Web modules that touch the newsletter surface
 * deal with newsletter metadata, channel discovery, channel directory and
 * newsletter cleanup tasks (for example {@code WAWebNewsletterCleanupTasks}
 * registered in {@code WAWebTasksDefinitions}), none of which consume
 * {@code SyncActionValue.NewsletterSavedInterestsAction}.
 *
 * <p>The Cobalt handler is a forward-looking implementation: it follows the
 * Cobalt sync handler conventions used by every other registered handler
 * (singleton, {@code applyMutation} producing a typed
 * {@link MutationApplicationResult}, eager store update on {@code SET}). Every
 * behavioural step here is Cobalt-inferred until WA Web ships the matching
 * {@code WAWebNewsletterSavedInterestsSync} module.
 */
public final class NewsletterSavedInterestsHandler implements WebAppStateActionHandler {

    /**
     * Private constructor that enforces the singleton pattern.
     */
    public NewsletterSavedInterestsHandler() {

    }

    /**
     * {@inheritDoc}
     * @return the canonical {@code "newsletter_saved_interests"} string
     */
    @Override
    public String actionName() {
        return NewsletterSavedInterestsAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@link SyncPatchType#REGULAR} as inferred from the WA Web
     * protobuf-side collection router.
     * @return {@link SyncPatchType#REGULAR}
     */
    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR; // NO_WA_BASIS: matches the WAWebProtobufSyncAction.pb inline collection mapping NEWSLETTER_SAVED_INTERESTS_ACTION -> REGULAR
    }

    /**
     * {@inheritDoc}
     * @return the integer version constant declared on the action class
     */
    @Override
    public int version() {
        return NewsletterSavedInterestsAction.ACTION_VERSION;
    }

    /**
     * Applies a newsletter saved interests mutation and returns the detailed
     * outcome.
     *
     * <p>The processing pipeline is:
     * <ol>
     *   <li>If the operation is not {@link SyncdOperation#SET}, return
     *       {@link MutationApplicationResult#unsupported()}. Only {@code SET}
     *       mutations are accepted; the action carries a single string token
     *       and there is no semantic for {@code REMOVE}.</li>
     *   <li>Resolve the mutation value to a
     *       {@link NewsletterSavedInterestsAction}; if the value is missing
     *       or of the wrong type, or if {@code newsletterSavedInterests} is
     *       empty, return {@link MutationApplicationResult#malformed()}.</li>
     *   <li>Persist the resolved interests string on the store via
     *       {@code WhatsAppStore.setNewsletterSavedInterests} and return
     *       {@link MutationApplicationResult#success()}.</li>
     * </ol>
     *
     * <p>The store accessors {@code newsletterSavedInterests()} and
     * {@code setNewsletterSavedInterests(...)} already exist on
     * {@code WhatsAppStore} / {@code AbstractWhatsAppStore}; this handler is
     * the sole writer.
     * @param client   the {@link WhatsAppClient} instance linked to the
     *                 mutation
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof NewsletterSavedInterestsAction action)
                || action.newsletterSavedInterests().isEmpty()) { // NO_WA_BASIS: newsletterSavedInterests is the only field on the protobuf and is required for any meaningful update
            return MutationApplicationResult.malformed();
        }

        client.store().setNewsletterSavedInterests(action.newsletterSavedInterests().get());
        return MutationApplicationResult.success();
    }
}
