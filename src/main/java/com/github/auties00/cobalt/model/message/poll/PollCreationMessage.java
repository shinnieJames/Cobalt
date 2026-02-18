package com.github.auties00.cobalt.model.message.poll;

import com.github.auties00.cobalt.model.message.ContextInfo;
import com.github.auties00.cobalt.model.message.ContextualMessage;

import java.util.Collections;
import java.util.List;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "Message.PollCreationMessage")
public final class PollCreationMessage implements ContextualMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] encKey;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String name;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    List<Option> options;

    @ProtobufProperty(index = 4, type = ProtobufType.UINT32)
    Integer selectableOptionsCount;

    @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;

    @ProtobufProperty(index = 6, type = ProtobufType.ENUM)
    PollContentType pollContentType;

    @ProtobufProperty(index = 7, type = ProtobufType.ENUM)
    PollType pollType;

    @ProtobufProperty(index = 8, type = ProtobufType.MESSAGE)
    Option correctAnswer;


    PollCreationMessage(byte[] encKey, String name, List<Option> options, Integer selectableOptionsCount, ContextInfo contextInfo, PollContentType pollContentType, PollType pollType, Option correctAnswer) {
        this.encKey = encKey;
        this.name = name;
        this.options = options;
        this.selectableOptionsCount = selectableOptionsCount;
        this.contextInfo = contextInfo;
        this.pollContentType = pollContentType;
        this.pollType = pollType;
        this.correctAnswer = correctAnswer;
    }

    public Optional<byte[]> encKey() {
        return Optional.ofNullable(encKey);
    }

    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    public List<Option> options() {
        return options == null ? List.of() : Collections.unmodifiableList(options);
    }

    public OptionalInt selectableOptionsCount() {
        return selectableOptionsCount == null ? OptionalInt.empty() : OptionalInt.of(selectableOptionsCount);
    }

    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    public Optional<PollContentType> pollContentType() {
        return Optional.ofNullable(pollContentType);
    }

    public Optional<PollType> pollType() {
        return Optional.ofNullable(pollType);
    }

    public Optional<Option> correctAnswer() {
        return Optional.ofNullable(correctAnswer);
    }

    public PollCreationMessage setEncKey(byte[] encKey) {
        this.encKey = encKey;
        return this;
    }

    public PollCreationMessage setName(String name) {
        this.name = name;
        return this;
    }

    public PollCreationMessage setOptions(List<Option> options) {
        this.options = options;
        return this;
    }

    public PollCreationMessage setSelectableOptionsCount(Integer selectableOptionsCount) {
        this.selectableOptionsCount = selectableOptionsCount;
        return this;
    }

    public PollCreationMessage setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
        return this;
    }

    public PollCreationMessage setPollContentType(PollContentType pollContentType) {
        this.pollContentType = pollContentType;
        return this;
    }

    public PollCreationMessage setPollType(PollType pollType) {
        this.pollType = pollType;
        return this;
    }

    public PollCreationMessage setCorrectAnswer(Option correctAnswer) {
        this.correctAnswer = correctAnswer;
        return this;
    }

    @ProtobufMessage(name = "Message.PollCreationMessage.Option")
    public static final class Option {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String optionName;

        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String optionHash;


        Option(String optionName, String optionHash) {
            this.optionName = optionName;
            this.optionHash = optionHash;
        }

        public Optional<String> optionName() {
            return Optional.ofNullable(optionName);
        }

        public Optional<String> optionHash() {
            return Optional.ofNullable(optionHash);
        }

        public Option setOptionName(String optionName) {
            this.optionName = optionName;
            return this;
        }

        public Option setOptionHash(String optionHash) {
            this.optionHash = optionHash;
            return this;
        }
    }
}
