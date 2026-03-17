package com.github.auties00.cobalt.model.message.poll;

import com.github.auties00.cobalt.model.message.Message;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Collections;
import java.util.List;

@ProtobufMessage(name = "Message.PollVoteMessage")
public final class PollVoteMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    List<byte[]> selectedOptions;


    PollVoteMessage(List<byte[]> selectedOptions) {
        this.selectedOptions = selectedOptions;
    }

    public List<byte[]> selectedOptions() {
        return selectedOptions == null ? List.of() : Collections.unmodifiableList(selectedOptions);
    }

    public void setSelectedOptions(List<byte[]> selectedOptions) {
        this.selectedOptions = selectedOptions;
    }
}
