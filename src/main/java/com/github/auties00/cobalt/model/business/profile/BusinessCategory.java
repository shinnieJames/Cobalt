package com.github.auties00.cobalt.model.business.profile;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Optional;

/**
 * A business category that classifies a WhatsApp Business account into an industry or service
 * type.
 *
 * <p>Each category is identified by a unique string assigned by WhatsApp and an optional
 * localized display name that represents the category in the user's language. Categories are
 * selected by the business owner during profile setup and are displayed on the business profile
 * to help users understand the nature of the business.
 *
 * <p>The category identifier is transmitted as an XML attribute on the {@code <category>} node
 * in the business profile stanza, while the localized display name is carried as the text
 * content of that same node.
 */
@ProtobufMessage
public final class BusinessCategory {
    /**
     * The unique identifier for this business category.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String id;

    /**
     * The localized display name of this business category, or {@code null} if not available.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String name;

    /**
     * Constructs a new business category with the specified identifier and localized display name.
     *
     * @param id   the unique identifier for this category
     * @param name the localized display name, or {@code null} if not available
     */
    BusinessCategory(String id, String name) {
        this.id = id;
        this.name = name;
    }

    /**
     * Returns the unique identifier for this business category.
     *
     * @return the category identifier
     */
    public String id() {
        return id;
    }

    /**
     * Sets the unique identifier for this business category.
     *
     * @param id the category identifier
     * @return this instance
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Returns the localized display name of this business category, if available.
     *
     * <p>The display name is provided by WhatsApp in the user's locale and represents the
     * human-readable label for this category, such as {@code "Restaurant"} or
     * {@code "Professional Services"}.
     *
     * @return an {@link Optional} containing the localized display name, or an empty
     *         {@code Optional} if not available
     */
    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    /**
     * Sets the localized display name of this business category.
     *
     * @param name the localized display name, or {@code null} to clear
     * @return this instance
     */
    public void setName(String name) {
        this.name = name;
    }
}
