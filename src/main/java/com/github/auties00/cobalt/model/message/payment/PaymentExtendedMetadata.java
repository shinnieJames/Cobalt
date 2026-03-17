package com.github.auties00.cobalt.model.message.payment;

import com.github.auties00.cobalt.model.message.Message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "Message.PaymentExtendedMetadata")
public final class PaymentExtendedMetadata implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.UINT32)
    Integer type;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String platform;


    PaymentExtendedMetadata(Integer type, String platform) {
        this.type = type;
        this.platform = platform;
    }

    public OptionalInt type() {
        return type == null ? OptionalInt.empty() : OptionalInt.of(type);
    }

    public Optional<String> platform() {
        return Optional.ofNullable(platform);
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }
}
