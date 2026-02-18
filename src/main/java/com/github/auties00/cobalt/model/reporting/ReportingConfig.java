package com.github.auties00.cobalt.model.reporting;

import java.util.Collections;
import java.util.Map;
import java.util.OptionalInt;

@ProtobufMessage(name = "Config")
public final class ReportingConfig {
    @ProtobufProperty(index = 1, type = ProtobufType.MAP, mapKeyType = ProtobufType.UINT32, mapValueType = ProtobufType.MESSAGE)
    Map<Integer, ReportingField> field;

    @ProtobufProperty(index = 2, type = ProtobufType.UINT32)
    Integer version;


    ReportingConfig(Map<Integer, ReportingField> field, Integer version) {
        this.field = field;
        this.version = version;
    }

    public Map<Integer, ReportingField> field() {
        return field == null ? Map.of() : Collections.unmodifiableMap(field);
    }

    public OptionalInt version() {
        return version == null ? OptionalInt.empty() : OptionalInt.of(version);
    }

    public ReportingConfig setField(Map<Integer, ReportingField> field) {
        this.field = field;
        return this;
    }

    public ReportingConfig setVersion(Integer version) {
        this.version = version;
        return this;
    }
}
