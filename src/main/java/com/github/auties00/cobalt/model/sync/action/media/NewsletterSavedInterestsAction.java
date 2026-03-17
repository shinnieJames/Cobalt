package com.github.auties00.cobalt.model.sync.action.media;

import com.github.auties00.cobalt.model.sync.SyncActionEmptyArgs;
import com.github.auties00.cobalt.model.sync.SyncAction;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.NewsletterSavedInterestsAction")
public final class NewsletterSavedInterestsAction implements SyncAction<SyncActionEmptyArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "newsletter_saved_interests";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 1;

    /**
     * {@inheritDoc}
     */
    @Override
    public String actionName() {
        return ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int actionVersion() {
        return ACTION_VERSION;
    }


    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String newsletterSavedInterests;


    NewsletterSavedInterestsAction(String newsletterSavedInterests) {
        this.newsletterSavedInterests = newsletterSavedInterests;
    }

    public Optional<String> newsletterSavedInterests() {
        return Optional.ofNullable(newsletterSavedInterests);
    }

    public void setNewsletterSavedInterests(String newsletterSavedInterests) {
        this.newsletterSavedInterests = newsletterSavedInterests;
    }
}
