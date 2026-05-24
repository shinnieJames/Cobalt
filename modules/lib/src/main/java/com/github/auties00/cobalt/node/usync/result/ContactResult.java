package com.github.auties00.cobalt.node.usync.result;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import java.util.Objects;
import java.util.Optional;

/**
 * Success result of the {@code WAWebUsyncContact.contactParser} parser.
 *
 * @apiNote
 * Surfaced by USync queries that include
 * {@code UsyncQuery.withContactProtocol(...)}. WA Web callers include
 * {@code WAWebContactImportContactVerifier} (the address-book sync that
 * decides which numbers are on WhatsApp), {@code WAWebContactSyncUtils}
 * (delta sync), {@code WAWebQueryExistsJob} (the "does this number exist"
 * check), and {@code WAWebDebugUsync} (developer tooling). The
 * {@link #type()} attribute carries the discovery answer:
 * {@code "in"} means registered, {@code "out"} means not registered, and
 * {@code "none"} means unknown. The {@link #username()} accessor surfaces the
 * peer's claimed username when the request asked for it, and {@link #content()}
 * carries the canonical phone number the relay echoes back inside the
 * {@code <contact>} element.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncContact")
public final class ContactResult implements UsyncProtocolResponse {
    /**
     * The {@code type} attribute on the {@code <contact>} response.
     */
    private final String type;

    /**
     * The {@code username} attribute, or {@code null} when the attribute is
     * absent.
     */
    private final String username;

    /**
     * The inline text content of the {@code <contact>} child, or {@code null}
     * when the element had no content.
     */
    private final String content;

    /**
     * Creates a new contact result.
     *
     * @apiNote
     * Instantiated by the contact parser; embedders do not call this
     * directly.
     *
     * @param type     the {@code type} attribute; must not be {@code null}
     * @param username the {@code username} attribute, or {@code null} when
     *                 absent
     * @param content  the inline content, or {@code null} when empty
     */
    public ContactResult(String type, String username, String content) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.username = username;
        this.content = content;
    }

    /**
     * Returns the {@code type} attribute.
     *
     * @apiNote
     * Discovery verdict: {@code "in"} when the peer is on WhatsApp,
     * {@code "out"} when the peer is not, {@code "none"} when the relay
     * cannot tell.
     *
     * @return the type, never {@code null}
     */
    public String type() {
        return type;
    }

    /**
     * Returns the {@code username} attribute, when present.
     *
     * @apiNote
     * Set on contact requests addressed by username; the relay echoes the
     * canonical username back so callers can verify the lookup.
     *
     * @return the username, or empty when absent
     */
    public Optional<String> username() {
        return Optional.ofNullable(username);
    }

    /**
     * Returns the inline content of the {@code <contact>} child, when
     * present.
     *
     * @apiNote
     * Typically the canonical phone number echoed back by the relay; absent
     * when the request was addressed by username or by JID.
     *
     * @return the content, or empty when the element had no inline content
     */
    public Optional<String> content() {
        return Optional.ofNullable(content);
    }
}
