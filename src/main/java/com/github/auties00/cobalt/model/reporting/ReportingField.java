package com.github.auties00.cobalt.model.reporting;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Collections;
import java.util.Map;
import java.util.OptionalInt;

@ProtobufMessage(name = "Field")
public final class ReportingField {
    @ProtobufProperty(index = 1, type = ProtobufType.UINT32)
    Integer minVersion;

    @ProtobufProperty(index = 2, type = ProtobufType.UINT32)
    Integer maxVersion;

    @ProtobufProperty(index = 3, type = ProtobufType.UINT32)
    Integer notReportableMinVersion;

    @ProtobufProperty(index = 4, type = ProtobufType.BOOL)
    Boolean isMessage;

    @ProtobufProperty(index = 5, type = ProtobufType.MAP, mapKeyType = ProtobufType.UINT32, mapValueType = ProtobufType.MESSAGE)
    Map<Integer, ReportingField> subfield;


    ReportingField(Integer minVersion, Integer maxVersion, Integer notReportableMinVersion, Boolean isMessage, Map<Integer, ReportingField> subfield) {
        this.minVersion = minVersion;
        this.maxVersion = maxVersion;
        this.notReportableMinVersion = notReportableMinVersion;
        this.isMessage = isMessage;
        this.subfield = subfield;
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

    public boolean isMessage() {
        return isMessage != null && isMessage;
    }

    public Map<Integer, ReportingField> subfield() {
        return subfield == null ? Map.of() : Collections.unmodifiableMap(subfield);
    }

    public ReportingField setMinVersion(Integer minVersion) {
        this.minVersion = minVersion;
        return this;
    }

    public ReportingField setMaxVersion(Integer maxVersion) {
        this.maxVersion = maxVersion;
        return this;
    }

    public ReportingField setNotReportableMinVersion(Integer notReportableMinVersion) {
        this.notReportableMinVersion = notReportableMinVersion;
        return this;
    }

    public ReportingField setMessage(Boolean isMessage) {
        this.isMessage = isMessage;
        return this;
    }

    public ReportingField setSubfield(Map<Integer, ReportingField> subfield) {
        this.subfield = subfield;
        return this;
    }
}
