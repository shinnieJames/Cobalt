package com.github.auties00.cobalt.model.message.payment;

import com.github.auties00.cobalt.model.message.Message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.PaymentLinkMetadata")
public final class PaymentLinkMetadata implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    PaymentLinkButton button;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    PaymentLinkHeader header;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    PaymentLinkProvider provider;


    PaymentLinkMetadata(PaymentLinkButton button, PaymentLinkHeader header, PaymentLinkProvider provider) {
        this.button = button;
        this.header = header;
        this.provider = provider;
    }

    public Optional<PaymentLinkButton> button() {
        return Optional.ofNullable(button);
    }

    public Optional<PaymentLinkHeader> header() {
        return Optional.ofNullable(header);
    }

    public Optional<PaymentLinkProvider> provider() {
        return Optional.ofNullable(provider);
    }

    public void setButton(PaymentLinkButton button) {
        this.button = button;
    }

    public void setHeader(PaymentLinkHeader header) {
        this.header = header;
    }

    public void setProvider(PaymentLinkProvider provider) {
        this.provider = provider;
    }

    @ProtobufMessage(name = "Message.PaymentLinkMetadata.PaymentLinkButton")
    public static final class PaymentLinkButton {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String displayText;


        PaymentLinkButton(String displayText) {
            this.displayText = displayText;
        }

        public Optional<String> displayText() {
            return Optional.ofNullable(displayText);
        }

        public void setDisplayText(String displayText) {
            this.displayText = displayText;
    }
    }

    @ProtobufMessage(name = "Message.PaymentLinkMetadata.PaymentLinkHeader")
    public static final class PaymentLinkHeader {
        @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
        PaymentLinkHeader.PaymentLinkHeaderType headerType;


        PaymentLinkHeader(PaymentLinkHeaderType headerType) {
            this.headerType = headerType;
        }

        public Optional<PaymentLinkHeaderType> headerType() {
            return Optional.ofNullable(headerType);
        }

        public void setHeaderType(PaymentLinkHeaderType headerType) {
            this.headerType = headerType;
    }

        @ProtobufEnum(name = "Message.PaymentLinkMetadata.PaymentLinkHeader.PaymentLinkHeaderType")
        public static enum PaymentLinkHeaderType {
            LINK_PREVIEW(0),
            ORDER(1);

            PaymentLinkHeaderType(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            final int index;

            public int index() {
                return this.index;
            }
        }
    }

    @ProtobufMessage(name = "Message.PaymentLinkMetadata.PaymentLinkProvider")
    public static final class PaymentLinkProvider {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String paramsJson;


        PaymentLinkProvider(String paramsJson) {
            this.paramsJson = paramsJson;
        }

        public Optional<String> paramsJson() {
            return Optional.ofNullable(paramsJson);
        }

        public void setParamsJson(String paramsJson) {
            this.paramsJson = paramsJson;
    }
    }
}
