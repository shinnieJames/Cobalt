package com.github.auties00.cobalt.model.payment;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * A monetary amount expressed as a scaled integer value together with an ISO 4217 currency
 * code.
 *
 * <p>The {@link #value() value} field carries the raw numeric amount. To obtain the real
 * monetary figure, divide by the {@link #offset() offset}. When the offset is absent or
 * {@code 0}, the WhatsApp client treats it as {@code 1000}, meaning the value is expressed
 * in thousandths of the major currency unit. For example, a value of {@code 5500} with an
 * offset of {@code 1000} (or absent) represents {@code 5.50} in the currency indicated by
 * {@link #currencyCode()}.
 *
 * <p>This model mirrors the {@code Money} protobuf message defined in the WhatsApp Web
 * protocol and is referenced by {@link PaymentInfo} as well as by
 * {@link com.github.auties00.cobalt.model.message.payment.RequestPaymentMessage
 * RequestPaymentMessage} to carry structured amount information.
 */
@ProtobufMessage(name = "Money")
public final class Money {
    /**
     * The raw numeric monetary value, expressed as a scaled integer. Divide by
     * {@link #offset() offset} (defaulting to {@code 1000}) to obtain the amount
     * in the major currency unit.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.INT64)
    Long value;

    /**
     * The scale divisor applied to {@link #value() value} to convert it into the
     * major currency unit. When absent or {@code 0}, the WhatsApp client defaults
     * this to {@code 1000}.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.UINT32)
    Integer offset;

    /**
     * The ISO 4217 currency code that identifies the currency of this monetary
     * amount, such as {@code "INR"} or {@code "BRL"}.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String currencyCode;

    /**
     * Constructs a new {@code Money} with the given value, offset, and currency code.
     *
     * @param value        the raw scaled integer monetary value
     * @param offset       the scale divisor, or {@code null} to default to {@code 1000}
     * @param currencyCode the ISO 4217 currency code, or {@code null}
     */
    Money(Long value, Integer offset, String currencyCode) {
        this.value = value;
        this.offset = offset;
        this.currencyCode = currencyCode;
    }

    /**
     * Returns the raw numeric monetary value.
     *
     * @return an {@code OptionalLong} containing the scaled integer value, or empty
     *         if not set
     */
    public OptionalLong value() {
        return value == null ? OptionalLong.empty() : OptionalLong.of(value);
    }

    /**
     * Returns the scale divisor for the monetary value.
     *
     * @return an {@code OptionalInt} containing the offset, or empty if not set
     */
    public OptionalInt offset() {
        return offset == null ? OptionalInt.empty() : OptionalInt.of(offset);
    }

    /**
     * Returns the ISO 4217 currency code.
     *
     * @return an {@code Optional} containing the currency code, or empty if not set
     */
    public Optional<String> currencyCode() {
        return Optional.ofNullable(currencyCode);
    }

    /**
     * Sets the raw numeric monetary value.
     *
     * @param value the scaled integer value
     * @return this instance
     */
    public void setValue(Long value) {
        this.value = value;
    }

    /**
     * Sets the scale divisor for the monetary value.
     *
     * @param offset the divisor, or {@code null} to default to {@code 1000}
     * @return this instance
     */
    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    /**
     * Sets the ISO 4217 currency code.
     *
     * @param currencyCode the currency code
     * @return this instance
     */
    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }
}
