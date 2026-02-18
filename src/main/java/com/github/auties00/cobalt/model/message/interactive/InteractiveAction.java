package com.github.auties00.cobalt.model.message.interactive;

import com.github.auties00.cobalt.model.message.ContextInfo;
import com.github.auties00.cobalt.model.location.Location;

public sealed interface InteractiveAction permits Location, ContextInfo.ForwardedNewsletterMessageInfo, EmbeddedAction, TapLinkAction {

    final class EmbeddedAction implements InteractiveAction {
        Boolean embeddedAction;

        EmbeddedAction(Boolean embeddedAction) {
            this.embeddedAction = embeddedAction;
        }

        @ProtobufSerializer
        public Boolean embeddedAction() {
            return embeddedAction;
        }

        @ProtobufDeserializer
        public static EmbeddedAction of(Boolean embeddedAction) {
            return new EmbeddedAction(embeddedAction);
        }
    }
}
