package com.github.auties00.cobalt.model.message.poll;

import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;

import java.util.Collections;
import java.util.List;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalLong;

@ProtobufMessage(name = "Message.PollResultSnapshotMessage")
public final class PollResultSnapshotMessage implements ContextualMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String name;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    List<PollVote> pollVotes;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;

    @ProtobufProperty(index = 4, type = ProtobufType.ENUM)
    PollType pollType;


    PollResultSnapshotMessage(String name, List<PollVote> pollVotes, ContextInfo contextInfo, PollType pollType) {
        this.name = name;
        this.pollVotes = pollVotes;
        this.contextInfo = contextInfo;
        this.pollType = pollType;
    }

    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    public List<PollVote> pollVotes() {
        return pollVotes == null ? List.of() : Collections.unmodifiableList(pollVotes);
    }

    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    public Optional<PollType> pollType() {
        return Optional.ofNullable(pollType);
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPollVotes(List<PollVote> pollVotes) {
        this.pollVotes = pollVotes;
    }

    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }

    public void setPollType(PollType pollType) {
        this.pollType = pollType;
    }

    @ProtobufMessage(name = "Message.PollResultSnapshotMessage.PollVote")
    public static final class PollVote {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String optionName;

        @ProtobufProperty(index = 2, type = ProtobufType.INT64)
        Long optionVoteCount;


        PollVote(String optionName, Long optionVoteCount) {
            this.optionName = optionName;
            this.optionVoteCount = optionVoteCount;
        }

        public Optional<String> optionName() {
            return Optional.ofNullable(optionName);
        }

        public OptionalLong optionVoteCount() {
            return optionVoteCount == null ? OptionalLong.empty() : OptionalLong.of(optionVoteCount);
        }

        public void setOptionName(String optionName) {
            this.optionName = optionName;
    }

        public void setOptionVoteCount(Long optionVoteCount) {
            this.optionVoteCount = optionVoteCount;
    }
    }
}
