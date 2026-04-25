package com.github.auties00.cobalt.node.usync.result;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import java.util.Objects;
import java.util.Optional;

/**
 * Success result of {@code WAWebUsyncContact.contactParser}.
 *
 * <p>Carries the {@code type} attribute (the discovery answer:
 * {@code "in"} for registered, {@code "out"} for not registered,
 * {@code "none"} for unknown), the optional {@code username} attribute,
 * and the optional inline content (typically the canonical phone number
 * the relay echoes back).
 *
 * @implNote WAWebUsyncContact.contactParser: success branch returns
 *     {@code {type, username?, content?}}.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncContact")
public final class ContactResult implements UsyncProtocolResponse {
    /**
     * The {@code type} attribute on the {@code <contact>} response.
     */
    private final String type;

    /**
     * The {@code username} attribute, or {@code null} if absent.
     */
    private final String username;

    /**
     * The inline text content of the {@code <contact>} child, or
     * {@code null} if the response had no content.
     */
    private final String content;

    /**
     * Creates a new contact result.
     *
     * @param type     the {@code type} attribute; must not be {@code null}
     * @param username the {@code username} attribute, or {@code null}
     * @param content  the inline content, or {@code null}
     */
    public ContactResult(String type, String username, String content) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.username = username;
        this.content = content;
    }

    /**
     * Returns the {@code type} attribute.
     *
     * @return the type, never {@code null}
     */
    public String type() {
        return type;
    }

    /**
     * Returns the {@code username} attribute, when present.
     *
     * @return the username
     */
    public Optional<String> username() {
        return Optional.ofNullable(username);
    }

    /**
     * Returns the inline content of the {@code <contact>} child, when
     * present.
     *
     * @return the content
     */
    public Optional<String> content() {
        return Optional.ofNullable(content);
    }
}
