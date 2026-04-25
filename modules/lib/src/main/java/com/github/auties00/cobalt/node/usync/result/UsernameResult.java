package com.github.auties00.cobalt.node.usync.result;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import java.util.Optional;

/**
 * Success result of {@code WAWebUsyncUsername.usernameParser}.
 *
 * @implNote WAWebUsyncUsername.usernameParser: success branch returns the
 *     content string or {@code null}.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncUsername")
public final class UsernameResult implements UsyncProtocolResponse {
    /**
     * The peer's username, or {@code null} if the peer has not claimed
     * one.
     */
    private final String username;

    /**
     * Creates a new username result.
     *
     * @param username the peer's username, or {@code null}
     */
    public UsernameResult(String username) {
        this.username = username;
    }

    /**
     * Returns the peer's username, when present.
     *
     * @return the username
     */
    public Optional<String> username() {
        return Optional.ofNullable(username);
    }
}
