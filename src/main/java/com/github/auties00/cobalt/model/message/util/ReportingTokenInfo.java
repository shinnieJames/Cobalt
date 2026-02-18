package com.github.auties00.cobalt.model.message.util;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "ReportingTokenInfo")
public final class ReportingTokenInfo {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] reportingTag;


    ReportingTokenInfo(byte[] reportingTag) {
        this.reportingTag = reportingTag;
    }

    public Optional<byte[]> reportingTag() {
        return Optional.ofNullable(reportingTag);
    }

    public ReportingTokenInfo setReportingTag(byte[] reportingTag) {
        this.reportingTag = reportingTag;
        return this;
    }
}
