package com.github.auties00.cobalt.model.business.catalog;

import it.auties.protobuf.annotation.ProtobufEnum;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The stock availability of a product in a WhatsApp Business catalog.
 *
 * <p>In the WhatsApp Web client, product availability is represented by
 * the {@code ProductAvailability} enum defined in
 * {@code WAWebProductTypes.flow}, which maps each value to a lowercase
 * display string such as {@code "in stock"} or {@code "out of stock"}.
 * The availability value is obtained from the {@code availability}
 * attribute on the product node in the {@code w:biz:catalog} IQ stanza
 * or from the {@code product_availability} field in GraphQL catalog
 * responses.
 *
 * <p>Products whose availability is {@link #OUT_OF_STOCK} are considered
 * unavailable for purchase. In the WhatsApp Web client, the constant
 * {@code PRODUCT_AVAILABILITY_UNAVAILABLE_VALUES} groups the
 * availability values that indicate a product cannot be purchased.
 */
@ProtobufEnum
public enum BusinessItemAvailability {
    /**
     * The availability of the item is unknown or has not been specified
     * by the business owner. This is the default value used by the
     * WhatsApp Web client when the {@code product_availability} field
     * is absent or does not match any known value.
     */
    UNKNOWN,

    /**
     * The item is currently in stock and available for purchase. This
     * corresponds to the {@code "in stock"} display string in the
     * WhatsApp Web client's {@code ProductAvailability} enum.
     */
    IN_STOCK,

    /**
     * The item is currently out of stock and unavailable for purchase.
     * This corresponds to the {@code "out of stock"} display string in
     * the WhatsApp Web client's {@code ProductAvailability} enum. The
     * WhatsApp Web client includes this value in the
     * {@code PRODUCT_AVAILABILITY_UNAVAILABLE_VALUES} constant.
     */
    OUT_OF_STOCK;

    /**
     * A lookup map from lowercase display names (with underscores
     * replaced by spaces) to their corresponding enum constants. Used
     * by {@link #ofName(String)} for case-insensitive display name
     * resolution.
     */
    private static final Map<String, BusinessItemAvailability> PRETTY_NAME_TO_AVAILABILITY = Arrays.stream(BusinessItemAvailability.values())
            .collect(Collectors.toMap(entry -> entry.name().toLowerCase().replaceAll("_", " "), Function.identity()));

    /**
     * Returns the availability corresponding to the given display name.
     *
     * <p>The display name is matched case-insensitively with underscores
     * replaced by spaces, following the same format used by the
     * {@code ProductAvailability} enum in {@code WAWebProductTypes.flow}
     * (e.g. {@code "in stock"}, {@code "out of stock"},
     * {@code "unknown"}).
     *
     * @param name the display name to look up
     * @return an {@code Optional} describing the matching availability,
     *         or an empty {@code Optional} if no match is found
     */
    public static Optional<BusinessItemAvailability> ofName(String name) {
        return Optional.ofNullable(PRETTY_NAME_TO_AVAILABILITY.get(name));
    }
}
