package com.github.auties00.cobalt.model.message.interactive;

import com.github.auties00.cobalt.model.message.location.LocationMessage;
import com.github.auties00.cobalt.model.message.media.DocumentMessage;
import com.github.auties00.cobalt.model.message.media.ImageMessage;
import com.github.auties00.cobalt.model.message.media.VideoMessage;

public sealed interface InteractiveHeader permits Text, DocumentMessage, ImageMessage, VideoMessage, LocationMessage {

    final class Text implements InteractiveHeader {
        String text;

        Text(String text) {
            this.text = text;
        }

        @ProtobufSerializer
        public String text() {
            return text;
        }

        @ProtobufDeserializer
        public static Text of(String text) {
            return new Text(text);
        }
    }
}
