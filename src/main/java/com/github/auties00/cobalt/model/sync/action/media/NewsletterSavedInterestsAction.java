package com.github.auties00.cobalt.model.sync.action.media;

import com.github.auties00.cobalt.model.sync.SyncAction;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.NewsletterSavedInterestsAction")
public final class NewsletterSavedInterestsAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String newsletterSavedInterests;


    NewsletterSavedInterestsAction(String newsletterSavedInterests) {
        this.newsletterSavedInterests = newsletterSavedInterests;
    }

    public Optional<String> newsletterSavedInterests() {
        return Optional.ofNullable(newsletterSavedInterests);
    }

    public NewsletterSavedInterestsAction setNewsletterSavedInterests(String newsletterSavedInterests) {
        this.newsletterSavedInterests = newsletterSavedInterests;
        return this;
    }
}
