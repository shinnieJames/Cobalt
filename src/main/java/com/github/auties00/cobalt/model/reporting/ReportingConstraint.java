package com.github.auties00.cobalt.model.reporting;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.OptionalInt;

@ProtobufMessage(name = "Reportable")
public final class ReportingConstraint {
    @ProtobufProperty(index = 1, type = ProtobufType.UINT32)
    Integer minVersion;

    @ProtobufProperty(index = 2, type = ProtobufType.UINT32)
    Integer maxVersion;

    @ProtobufProperty(index = 3, type = ProtobufType.UINT32)
    Integer notReportableMinVersion;

    @ProtobufProperty(index = 4, type = ProtobufType.BOOL)
    Boolean never;


    ReportingConstraint(Integer minVersion, Integer maxVersion, Integer notReportableMinVersion, Boolean never) {
        this.minVersion = minVersion;
        this.maxVersion = maxVersion;
        this.notReportableMinVersion = notReportableMinVersion;
        this.never = never;
    }

    public OptionalInt minVersion() {
        return minVersion == null ? OptionalInt.empty() : OptionalInt.of(minVersion);
    }

    public OptionalInt maxVersion() {
        return maxVersion == null ? OptionalInt.empty() : OptionalInt.of(maxVersion);
    }

    public OptionalInt notReportableMinVersion() {
        return notReportableMinVersion == null ? OptionalInt.empty() : OptionalInt.of(notReportableMinVersion);
    }

    public boolean never() {
        return never != null && never;
    }

    public void setMinVersion(Integer minVersion) {
        this.minVersion = minVersion;
    }

    public void setMaxVersion(Integer maxVersion) {
        this.maxVersion = maxVersion;
    }

    public void setNotReportableMinVersion(Integer notReportableMinVersion) {
        this.notReportableMinVersion = notReportableMinVersion;
    }

    public void setNever(Boolean never) {
        this.never = never;
    }
}
