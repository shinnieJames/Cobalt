package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.media.NewsletterSavedInterestsAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

public final class NewsletterSavedInterestsHandler implements WebAppStateActionHandler {
    public static final NewsletterSavedInterestsHandler INSTANCE = new NewsletterSavedInterestsHandler();

    private NewsletterSavedInterestsHandler() {

    }

    @Override
    public String actionName() {
        return NewsletterSavedInterestsAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR;
    }

    @Override
    public int version() {
        return NewsletterSavedInterestsAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        return applyMutationResult(client, mutation).actionState() == com.github.auties00.cobalt.model.sync.SyncActionState.SUCCESS;
    }

    @Override
    public MutationApplicationResult applyMutationResult(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
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
