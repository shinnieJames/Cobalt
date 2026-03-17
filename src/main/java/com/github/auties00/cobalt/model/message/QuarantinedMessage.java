package com.github.auties00.cobalt.model.message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "QuarantinedMessage")
public final class QuarantinedMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] originalData;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String extractedText;


    QuarantinedMessage(byte[] originalData, String extractedText) {
        this.originalData = originalData;
        this.extractedText = extractedText;
    }

    public Optional<byte[]> originalData() {
        return Optional.ofNullable(originalData);
    }

    public Optional<String> extractedText() {
        return Optional.ofNullable(extractedText);
    }

    public void setOriginalData(byte[] originalData) {
        this.originalData = originalData;
    }

    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }
}
