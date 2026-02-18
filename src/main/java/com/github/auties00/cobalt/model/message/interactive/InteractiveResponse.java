package com.github.auties00.cobalt.model.message.interactive;

public sealed interface InteractiveResponse permits SelectedDisplayText {

    final class SelectedDisplayText implements InteractiveResponse {
        String selectedDisplayText;

        SelectedDisplayText(String selectedDisplayText) {
            this.selectedDisplayText = selectedDisplayText;
        }

        @ProtobufSerializer
        public String selectedDisplayText() {
            return selectedDisplayText;
        }

        @ProtobufDeserializer
        public static SelectedDisplayText of(String selectedDisplayText) {
            return new SelectedDisplayText(selectedDisplayText);
        }
    }
}
