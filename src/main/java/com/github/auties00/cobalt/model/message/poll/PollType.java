package com.github.auties00.cobalt.model.message.poll;

@ProtobufEnum(name = "Message.PollType")
public enum PollType {
    POLL(0),
    QUIZ(1);

    PollType(@ProtobufEnumIndex int index) {
        this.index = index;
    }

    final int index;

    public int index() {
        return this.index;
    }
}
