package com.github.auties00.cobalt.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * An authentication strategy for proxy connections.
 *
 * <p>Each proxy protocol family defines its own authenticator subtypes:
 * {@linkplain Http HTTP} authenticators produce {@code Proxy-Authorization}
 * headers, {@link Socks.V4} carries a user ID for the SOCKS4 handshake, and
 * {@linkplain Socks.V5 SOCKS5} authenticators handle RFC 1929 sub-negotiation.
 *
 * @see WhatsAppClientProxy
 */
public sealed interface WhatsAppClientProxyAuthenticator {

    /**
     * Authentication for HTTP CONNECT proxies. Implementations produce
     * a {@code Proxy-Authorization} header value.
     */
    sealed interface Http extends WhatsAppClientProxyAuthenticator {

        /**
         * Computes the {@code Proxy-Authorization} header value.
         *
         * @param method     the HTTP method (typically {@code "CONNECT"})
         * @param uri        the request URI ({@code "host:port"})
         * @param challenges {@code Proxy-Authenticate} values from a 407,
         *                   or an empty list on the initial attempt
         * @return the header value
         * @throws IOException if the computation fails
         */
        String authenticate(String method, String uri, List<String> challenges) throws IOException;

        /**
         * HTTP Basic authentication (RFC 7617).
         *
         * @param username the username
         * @param password the password, may be {@code null}
         */
        record Basic(String username, String password) implements Http {

            public Basic {
                Objects.requireNonNull(username, "username");
            }

            @Override
            public String authenticate(String method, String uri, List<String> challenges) {
                Objects.requireNonNull(method, "method");
                Objects.requireNonNull(uri, "uri");
                Objects.requireNonNull(challenges, "challenges");

                var pair = username + ":" + Objects.requireNonNullElse(password, "");
                return "Basic " + Base64.getEncoder()
                        .encodeToString(pair.getBytes(StandardCharsets.UTF_8));
            }
        }

        /**
         * HTTP Bearer token authentication (RFC 6750).
         *
         * @param token the bearer token
         */
        record Bearer(String token) implements Http {

            public Bearer {
                Objects.requireNonNull(token, "token");
            }

            @Override
            public String authenticate(String method, String uri, List<String> challenges) {
                Objects.requireNonNull(method, "method");
                Objects.requireNonNull(uri, "uri");
                Objects.requireNonNull(challenges, "challenges");

                return "Bearer " + token;
            }
        }
    }

    sealed interface Socks extends WhatsAppClientProxyAuthenticator {

        /**
         * SOCKS4 user ID authentication. The user ID is sent as a
         * null-terminated ISO 8859-1 string during the SOCKS4 handshake.
         *
         * @param userId the user ID
         */
        record V4(String userId) implements Socks {
            public V4 {
                Objects.requireNonNull(userId, "userId");
            }
        }

        /**
         * Authentication for SOCKS5 proxies (RFC 1928). Implementations
         * participate in the SOCKS5 method negotiation handshake.
         */
        sealed interface V5 extends Socks {

            /**
             * Returns the SOCKS5 method number (e.g. {@code 0x02} for username/password).
             */
            int methodId();

            /**
             * SOCKS5 username/password authentication (RFC 1929, method {@code 0x02}).
             * Both values are encoded as ISO 8859-1 and must be at most 255 bytes.
             *
             * @param username the username
             * @param password the password, may be {@code null} (treated as empty)
             */
            record UserPassword(String username, String password) implements V5 {

                private static final int METHOD_ID = 0x02;
                private static final int MAX_LENGTH = 255;

                public UserPassword(String username, String password) {
                    Objects.requireNonNull(username, "username");
                    if (username.getBytes(StandardCharsets.ISO_8859_1).length > MAX_LENGTH) {
                        throw new IllegalArgumentException(
                                "username must be at most 255 bytes (ISO_8859_1)");
                    }
                    this.username = username;

                    if(password == null) {
                        this.password = "";
                    } else {
                        if (password.getBytes(StandardCharsets.ISO_8859_1).length > 255) {
                            throw new IllegalArgumentException(
                                    "password must be at most 255 bytes (ISO_8859_1)");
                        }
                        this.password = password;
                    }
                }

                @Override
                public int methodId() {
                    return METHOD_ID;
                }
            }
        }
    }
}
