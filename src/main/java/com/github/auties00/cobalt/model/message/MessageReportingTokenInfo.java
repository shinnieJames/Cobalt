package com.github.auties00.cobalt.model.message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "ReportingTokenInfo")
public final class MessageReportingTokenInfo {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] reportingTag;


    MessageReportingTokenInfo(byte[] reportingTag) {
        this.reportingTag = reportingTag;
    }

    public Optional<byte[]> reportingTag() {
        return Optional.ofNullable(reportingTag);
    }

    public void setReportingTag(byte[] reportingTag) {
        this.reportingTag = reportingTag;
    }
}
