package com.github.auties00.cobalt.model.chat.group;

import com.github.auties00.cobalt.model.message.system.history.MessageHistoryBundle;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "GroupHistoryBundleInfo")
public final class GroupHistoryBundleInfo {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageHistoryBundle deprecatedMessageHistoryBundle;

    @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
    ProcessState processState;


    GroupHistoryBundleInfo(MessageHistoryBundle deprecatedMessageHistoryBundle, ProcessState processState) {
        this.deprecatedMessageHistoryBundle = deprecatedMessageHistoryBundle;
        this.processState = processState;
    }

    public Optional<MessageHistoryBundle> deprecatedMessageHistoryBundle() {
        return Optional.ofNullable(deprecatedMessageHistoryBundle);
    }

    public Optional<ProcessState> processState() {
        return Optional.ofNullable(processState);
    }

    public void setDeprecatedMessageHistoryBundle(MessageHistoryBundle deprecatedMessageHistoryBundle) {
        this.deprecatedMessageHistoryBundle = deprecatedMessageHistoryBundle;
    }

    public void setProcessState(ProcessState processState) {
        this.processState = processState;
    }

    @ProtobufEnum(name = "GroupHistoryBundleInfo.ProcessState")
    public static enum ProcessState {
        NOT_INJECTED(0),
        INJECTED(1),
        INJECTED_PARTIAL(2),
        INJECTION_FAILED(3),
        INJECTION_FAILED_NO_RETRY(4);

        ProcessState(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
