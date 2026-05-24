package com.github.auties00.cobalt.node.usync.result;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import java.util.Optional;

/**
 * Success result of the {@code WAWebUsyncUsername.usernameParser} parser.
 *
 * @apiNote
 * Surfaced by USync queries that include
 * {@code UsyncQuery.withUsernameProtocol()}; WA Web callers include the
 * background contact sync, {@code WAWebContactSyncUtils} delta sync, and
 * {@code WAWebQueryExistsJob} (the username probe used by the "find on
 * WhatsApp" flow). Carries the peer's claimed username; empty when the peer
 * has not claimed one.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncUsername")
public final class UsernameResult implements UsyncProtocolResponse {
    /**
     * The peer's username from the inline content of the {@code <username>}
     * element, or {@code null} when the element had no content.
     */
    private final String username;

    /**
     * Creates a new username result.
     *
     * @apiNote
     * Instantiated by the username parser; embedders do not call this
     * directly.
     *
     * @param username the peer's username, or {@code null}
     */
    public UsernameResult(String username) {
        this.username = username;
    }

    /**
     * Returns the peer's username, when present.
     *
     * @apiNote
     * Empty when the peer has not claimed a username yet; the username probe
     * uses that condition to fall back to phone-number addressing.
     *
     * @return the username, or empty when absent
     */
    public Optional<String> username() {
        return Optional.ofNullable(username);
    }
}
