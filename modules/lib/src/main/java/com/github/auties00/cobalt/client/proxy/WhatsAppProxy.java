package com.github.auties00.cobalt.client.proxy;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.client.WhatsAppClientBuilder;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * Proxy configuration used by {@link WhatsAppClient} when opening the
 * socket to the WhatsApp servers.
 *
 * <p>Developers register a {@code WhatsAppProxy} on the client through
 * {@link WhatsAppClientBuilder.Options#proxy(WhatsAppProxy)} to route
 * all outbound traffic through an intermediate hop. The library
 * supports two protocol families: HTTP {@code CONNECT} tunnels
 * (plaintext or TLS) and SOCKS tunnels (versions 4, 4a, 5 and 5h).
 * Instances are typically constructed via the static factory methods
 * such as {@link #ofHttp(String, int)}, {@link #ofSocks5(String, int)}
 * or {@link #ofSocks5h(String, int)}, or parsed from a URI through
 * {@link #of(URI)} when the configuration is supplied via environment
 * variables.
 *
 * <p>The proxy is applied before the Noise handshake runs. The tunnel
 * client first negotiates with the proxy and then hands the opened
 * socket to the WhatsApp protocol layer.
 *
 * @see WhatsAppProxyAuthenticator
 * @see WhatsAppClientBuilder.Options#proxy(WhatsAppProxy)
 */
public sealed interface WhatsAppProxy {

    /**
     * Returns the proxy server hostname.
     *
     * @return the hostname or IP address used to reach the proxy
     */
    String host();

    /**
     * Returns the proxy server port.
     *
     * @return the TCP port between {@code 1} and {@code 65535} used to
     *         reach the proxy
     */
    int port();

    /**
     * Returns the authentication strategy configured for this proxy,
     * if any.
     *
     * @return an {@link Optional} wrapping the authenticator, or empty
     *         if no authentication is required
     */
    Optional<? extends WhatsAppProxyAuthenticator> authenticator();

    /**
     * Creates a plain HTTP {@code CONNECT} proxy configuration without
     * authentication.
     *
     * @param host the proxy hostname
     * @param port the proxy port
     * @return a new {@code Http.Plain} configuration
     */
    static Http.Plain ofHttp(String host, int port) {
        return new Http.Plain(host, port, null);
    }

    /**
     * Creates a plain HTTP {@code CONNECT} proxy configuration that
     * uses the given authenticator.
     *
     * @param host          the proxy hostname
     * @param port          the proxy port
     * @param authenticator the authentication strategy
     * @return a new {@code Http.Plain} configuration
     */
    static Http.Plain ofHttp(String host, int port, WhatsAppProxyAuthenticator.Http authenticator) {
        return new Http.Plain(host, port, authenticator);
    }

    /**
     * Creates a TLS-encrypted HTTP {@code CONNECT} proxy configuration
     * without authentication.
     *
     * @param host the proxy hostname
     * @param port the proxy port
     * @return a new {@code Http.Secure} configuration
     */
    static Http.Secure ofHttps(String host, int port) {
        return new Http.Secure(host, port, null);
    }

    /**
     * Creates a TLS-encrypted HTTP {@code CONNECT} proxy configuration
     * that uses the given authenticator.
     *
     * @param host          the proxy hostname
     * @param port          the proxy port
     * @param authenticator the authentication strategy
     * @return a new {@code Http.Secure} configuration
     */
    static Http.Secure ofHttps(String host, int port, WhatsAppProxyAuthenticator.Http authenticator) {
        return new Http.Secure(host, port, authenticator);
    }

    /**
     * Creates a SOCKS5 proxy configuration with local DNS resolution
     * and no authentication.
     *
     * @param host the proxy hostname
     * @param port the proxy port
     * @return a new {@code Socks.V5.Local} configuration
     */
    static Socks.V5.Local ofSocks5(String host, int port) {
        return new Socks.V5.Local(host, port, null);
    }

    /**
     * Creates a SOCKS5 proxy configuration with local DNS resolution
     * that uses the given authenticator.
     *
     * @param host          the proxy hostname
     * @param port          the proxy port
     * @param authenticator the SOCKS5 authenticator
     * @return a new {@code Socks.V5.Local} configuration
     */
    static Socks.V5.Local ofSocks5(String host, int port, WhatsAppProxyAuthenticator.Socks.V5 authenticator) {
        return new Socks.V5.Local(host, port, authenticator);
    }

    /**
     * Creates a SOCKS5 proxy configuration with remote DNS resolution
     * and no authentication.
     *
     * @param host the proxy hostname
     * @param port the proxy port
     * @return a new {@code Socks.V5.Remote} configuration
     */
    static Socks.V5.Remote ofSocks5h(String host, int port) {
        return new Socks.V5.Remote(host, port, null);
    }

    /**
     * Creates a SOCKS5 proxy configuration with remote DNS resolution
     * that uses the given authenticator.
     *
     * @param host          the proxy hostname
     * @param port          the proxy port
     * @param authenticator the SOCKS5 authenticator
     * @return a new {@code Socks.V5.Remote} configuration
     */
    static Socks.V5.Remote ofSocks5h(String host, int port, WhatsAppProxyAuthenticator.Socks.V5 authenticator) {
        return new Socks.V5.Remote(host, port, authenticator);
    }

    /**
     * Creates a SOCKS4 proxy configuration without a user ID.
     *
     * @param host the proxy hostname
     * @param port the proxy port
     * @return a new {@code Socks.V4.Local} configuration
     */
    static Socks.V4.Local ofSocks4(String host, int port) {
        return new Socks.V4.Local(host, port, null);
    }

    /**
     * Creates a SOCKS4 proxy configuration that uses the given user ID
     * authenticator.
     *
     * @param host          the proxy hostname
     * @param port          the proxy port
     * @param authenticator the SOCKS4 user ID authenticator
     * @return a new {@code Socks.V4.Local} configuration
     */
    static Socks.V4.Local ofSocks4(String host, int port, WhatsAppProxyAuthenticator.Socks.V4 authenticator) {
        return new Socks.V4.Local(host, port, authenticator);
    }

    /**
     * Creates a SOCKS4a proxy configuration with remote DNS resolution
     * and no user ID.
     *
     * @param host the proxy hostname
     * @param port the proxy port
     * @return a new {@code Socks.V4.Remote} configuration
     */
    static Socks.V4.Remote ofSocks4a(String host, int port) {
        return new Socks.V4.Remote(host, port, null);
    }

    /**
     * Creates a SOCKS4a proxy configuration with remote DNS resolution
     * that uses the given user ID authenticator.
     *
     * @param host          the proxy hostname
     * @param port          the proxy port
     * @param authenticator the SOCKS4 user ID authenticator
     * @return a new {@code Socks.V4.Remote} configuration
     */
    static Socks.V4.Remote ofSocks4a(String host, int port, WhatsAppProxyAuthenticator.Socks.V4 authenticator) {
        return new Socks.V4.Remote(host, port, authenticator);
    }

    /**
     * Parses a proxy configuration from a URI.
     *
     * <p>The supported schemes are {@code http} (default port
     * {@code 80}), {@code https} (default port {@code 443}),
     * {@code socks4}, {@code socks4a}, {@code socks5} and
     * {@code socks5h} (all defaulting to port {@code 1080}).
     * Credentials are extracted from the URI user-info component when
     * present.
     *
     * @param uri the proxy URI
     * @return the parsed proxy configuration
     * @throws NullPointerException     if {@code uri}, its scheme, or
     *                                  its host is {@code null}
     * @throws IllegalArgumentException if the scheme is not one of the
     *                                  supported values
     */
    static WhatsAppProxy of(URI uri) {
        Objects.requireNonNull(uri, "uri");
        var scheme = Objects.requireNonNull(uri.getScheme(), "Proxy URI scheme cannot be null").toLowerCase();
        var host = Objects.requireNonNull(uri.getHost(), "Proxy URI host cannot be null");
        var port = uri.getPort();
        var userInfo = uri.getUserInfo();

        String username = null;
        String password = null;
        if (userInfo != null) {
            var colon = userInfo.indexOf(':');
            username = colon == -1 ? userInfo : userInfo.substring(0, colon);
            password = colon == -1 ? "" : userInfo.substring(colon + 1);
        }

        return switch (scheme) {
            case "http" -> {
                var p = port == -1 ? 80 : port;
                yield username != null
                        ? ofHttp(host, p, new WhatsAppProxyAuthenticator.Http.Basic(username, password))
                        : ofHttp(host, p);
            }
            case "https" -> {
                var p = port == -1 ? 443 : port;
                yield username != null
                        ? ofHttps(host, p, new WhatsAppProxyAuthenticator.Http.Basic(username, password))
                        : ofHttps(host, p);
            }
            case "socks4" -> {
                var p = port == -1 ? 1080 : port;
                yield username != null
                        ? ofSocks4(host, p, new WhatsAppProxyAuthenticator.Socks.V4(username))
                        : ofSocks4(host, p);
            }
            case "socks4a" -> {
                var p = port == -1 ? 1080 : port;
                yield username != null
                        ? ofSocks4a(host, p, new WhatsAppProxyAuthenticator.Socks.V4(username))
                        : ofSocks4a(host, p);
            }
            case "socks5" -> {
                var p = port == -1 ? 1080 : port;
                yield username != null
                        ? ofSocks5(host, p, new WhatsAppProxyAuthenticator.Socks.V5.UserPassword(username, password))
                        : ofSocks5(host, p);
            }
            case "socks5h" -> {
                var p = port == -1 ? 1080 : port;
                yield username != null
                        ? ofSocks5h(host, p, new WhatsAppProxyAuthenticator.Socks.V5.UserPassword(username, password))
                        : ofSocks5h(host, p);
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported proxy scheme: " + scheme);
        };
    }

    /**
     * An HTTP {@code CONNECT} proxy configuration.
     *
     * <p>An HTTP tunnel can either be plaintext ({@link Plain}) or
     * TLS-encrypted ({@link Secure}). When TLS-encrypted is selected,
     * a TLS session is established with the proxy before the
     * {@code CONNECT} request is issued.
     */
    sealed interface Http extends WhatsAppProxy {

        /**
         * Returns the HTTP-specific authenticator configured for this
         * proxy.
         *
         * @return an {@link Optional} wrapping the HTTP authenticator,
         *         or empty if no authentication is required
         */
        @Override
        Optional<WhatsAppProxyAuthenticator.Http> authenticator();

        /**
         * A plain HTTP {@code CONNECT} proxy configuration in which
         * the tunnel to the proxy itself is not encrypted.
         */
        final class Plain implements Http {

            /**
             * The proxy hostname.
             */
            private final String host;

            /**
             * The proxy TCP port.
             */
            private final int port;

            /**
             * The authentication strategy, or {@code null} if the
             * proxy accepts anonymous connections.
             */
            private final WhatsAppProxyAuthenticator.Http authenticator;

            /**
             * Constructs a new plain HTTP proxy configuration.
             *
             * @param host          the proxy hostname, must not be
             *                      {@code null}
             * @param port          the proxy port, must be between
             *                      {@code 1} and {@code 65535}
             * @param authenticator the authentication strategy, or
             *                      {@code null}
             * @throws NullPointerException     if {@code host} is
             *                                  {@code null}
             * @throws IllegalArgumentException if {@code port} is out
             *                                  of range
             */
            private Plain(String host, int port, WhatsAppProxyAuthenticator.Http authenticator) {
                Objects.requireNonNull(host, "host");
                if (port < 1 || port > 65535) {
                    throw new IllegalArgumentException(
                            "port must be between 1 and 65535: " + port);
                }
                this.host = host;
                this.port = port;
                this.authenticator = authenticator;
            }

            /**
             * Returns the proxy hostname.
             *
             * @return the hostname
             */
            @Override
            public String host() {
                return host;
            }

            /**
             * Returns the proxy TCP port.
             *
             * @return the port
             */
            @Override
            public int port() {
                return port;
            }

            /**
             * Returns the HTTP authenticator associated with this
             * proxy.
             *
             * @return an {@link Optional} wrapping the authenticator,
             *         or empty if none is configured
             */
            @Override
            public Optional<WhatsAppProxyAuthenticator.Http> authenticator() {
                return Optional.ofNullable(authenticator);
            }

            /**
             * Compares this proxy to the given object for structural
             * equality.
             *
             * @param obj the object to compare with
             * @return {@code true} if {@code obj} is another
             *         {@code Plain} with the same host, port, and
             *         authenticator
             */
            @Override
            public boolean equals(Object obj) {
                return obj == this
                       || (obj instanceof Plain other
                           && host.equals(other.host)
                           && port == other.port
                           && Objects.equals(authenticator, other.authenticator));
            }

            /**
             * Returns a hash code derived from the host, port, and
             * authenticator.
             *
             * @return the hash code
             */
            @Override
            public int hashCode() {
                return Objects.hash(host, port, authenticator);
            }

            /**
             * Returns a human-readable description of this proxy
             * configuration suitable for logs.
             *
             * @return the string representation
             */
            @Override
            public String toString() {
                return "Http.Plain[host=" + host
                       + ", port=" + port
                       + ", authenticator=" + authenticator + "]";
            }
        }

        /**
         * A TLS-encrypted HTTP {@code CONNECT} proxy configuration.
         *
         * <p>The tunnel to the proxy itself is established over TLS,
         * using the hostname for both Server Name Indication and
         * certificate verification. Once the tunnel is open, the
         * WhatsApp TLS and Noise session runs inside it, resulting in
         * a double-encrypted transport.
         */
        final class Secure implements Http {

            /**
             * The proxy hostname, also used as the TLS SNI value.
             */
            private final String host;

            /**
             * The proxy TCP port.
             */
            private final int port;

            /**
             * The authentication strategy, or {@code null} if the
             * proxy accepts anonymous connections.
             */
            private final WhatsAppProxyAuthenticator.Http authenticator;

            /**
             * Constructs a new TLS-encrypted HTTP proxy configuration.
             *
             * @param host          the proxy hostname, must not be
             *                      {@code null}
             * @param port          the proxy port, must be between
             *                      {@code 1} and {@code 65535}
             * @param authenticator the authentication strategy, or
             *                      {@code null}
             * @throws NullPointerException     if {@code host} is
             *                                  {@code null}
             * @throws IllegalArgumentException if {@code port} is out
             *                                  of range
             */
            private Secure(String host, int port, WhatsAppProxyAuthenticator.Http authenticator) {
                Objects.requireNonNull(host, "host");
                if (port < 1 || port > 65535) {
                    throw new IllegalArgumentException(
                            "port must be between 1 and 65535: " + port);
                }
                this.host = host;
                this.port = port;
                this.authenticator = authenticator;
            }

            /**
             * Returns the proxy hostname.
             *
             * @return the hostname
             */
            @Override
            public String host() {
                return host;
            }

            /**
             * Returns the proxy TCP port.
             *
             * @return the port
             */
            @Override
            public int port() {
                return port;
            }

            /**
             * Returns the HTTP authenticator associated with this
             * proxy.
             *
             * @return an {@link Optional} wrapping the authenticator,
             *         or empty if none is configured
             */
            @Override
            public Optional<WhatsAppProxyAuthenticator.Http> authenticator() {
                return Optional.ofNullable(authenticator);
            }

            /**
             * Compares this proxy to the given object for structural
             * equality.
             *
             * @param obj the object to compare with
             * @return {@code true} if {@code obj} is another
             *         {@code Secure} with the same host, port and
             *         authenticator
             */
            @Override
            public boolean equals(Object obj) {
                return obj == this || (obj instanceof Secure other
                                       && host.equals(other.host)
                                       && port == other.port
                                       && Objects.equals(authenticator, other.authenticator));
            }

            /**
             * Returns a hash code derived from the host, port, and
             * authenticator.
             *
             * @return the hash code
             */
            @Override
            public int hashCode() {
                return Objects.hash(host, port, authenticator);
            }

            /**
             * Returns a human-readable description of this proxy
             * configuration suitable for logs.
             *
             * @return the string representation
             */
            @Override
            public String toString() {
                return "Http.Secure[host=" + host
                       + ", port=" + port
                       + ", authenticator=" + authenticator + "]";
            }
        }
    }

    /**
     * A SOCKS proxy configuration covering both SOCKS4 and 4a (RFC
     * 1928 with the 4a extension) and SOCKS5 (RFC 1928 with RFC 1929
     * sub-negotiation).
     *
     * <p>SOCKS4 variants ({@link V4.Local}, {@link V4.Remote}) carry
     * an optional user ID. SOCKS5 variants ({@link V5.Local},
     * {@link V5.Remote}) support RFC 1929 username and password
     * authentication and optional remote DNS resolution (the
     * {@code socks5h} scheme).
     */
    sealed interface Socks extends WhatsAppProxy {

        /**
         * Returns the SOCKS-specific authenticator configured for this
         * proxy.
         *
         * @return an {@link Optional} wrapping the SOCKS
         *         authenticator, or empty if anonymous connections are
         *         used
         */
        @Override
        Optional<? extends WhatsAppProxyAuthenticator.Socks> authenticator();

        /**
         * A SOCKS4 proxy configuration.
         *
         * <p>Hostnames may be resolved by the client ({@link Local})
         * or by the proxy ({@link Remote}, commonly referred to as
         * SOCKS4a).
         */
        sealed interface V4 extends Socks {

            /**
             * Returns the SOCKS4 user ID authenticator configured for
             * this proxy.
             *
             * @return an {@link Optional} wrapping the user ID
             *         authenticator, or empty if none is configured
             */
            @Override
            Optional<WhatsAppProxyAuthenticator.Socks.V4> authenticator();

            /**
             * A SOCKS4 proxy configuration that resolves hostnames on
             * the client side. Only IPv4 destinations are supported.
             */
            final class Local implements V4 {

                /**
                 * The proxy hostname.
                 */
                private final String host;

                /**
                 * The proxy TCP port.
                 */
                private final int port;

                /**
                 * The SOCKS4 user ID authenticator, or {@code null} if
                 * no user ID is supplied.
                 */
                private final WhatsAppProxyAuthenticator.Socks.V4 authenticator;

                /**
                 * Constructs a new SOCKS4 local-DNS proxy
                 * configuration.
                 *
                 * @param host          the proxy hostname, must not be
                 *                      {@code null}
                 * @param port          the proxy port, must be between
                 *                      {@code 1} and {@code 65535}
                 * @param authenticator the user ID authenticator, or
                 *                      {@code null}
                 * @throws NullPointerException     if {@code host} is
                 *                                  {@code null}
                 * @throws IllegalArgumentException if {@code port} is
                 *                                  out of range
                 */
                private Local(String host, int port, WhatsAppProxyAuthenticator.Socks.V4 authenticator) {
                    Objects.requireNonNull(host, "host");
                    if (port < 1 || port > 65535) {
                        throw new IllegalArgumentException(
                                "port must be between 1 and 65535: " + port);
                    }
                    this.host = host;
                    this.port = port;
                    this.authenticator = authenticator;
                }

                /**
                 * Returns the proxy hostname.
                 *
                 * @return the hostname
                 */
                @Override
                public String host() {
                    return host;
                }

                /**
                 * Returns the proxy TCP port.
                 *
                 * @return the port
                 */
                @Override
                public int port() {
                    return port;
                }

                /**
                 * Returns the SOCKS4 user ID authenticator.
                 *
                 * @return an {@link Optional} wrapping the
                 *         authenticator, or empty if none is configured
                 */
                @Override
                public Optional<WhatsAppProxyAuthenticator.Socks.V4> authenticator() {
                    return Optional.ofNullable(authenticator);
                }

                /**
                 * Compares this proxy to the given object for
                 * structural equality.
                 *
                 * @param obj the object to compare with
                 * @return {@code true} if {@code obj} is another
                 *         {@code Local} with the same host, port and
                 *         authenticator
                 */
                @Override
                public boolean equals(Object obj) {
                    return obj == this || (obj instanceof Local other
                                           && host.equals(other.host)
                                           && port == other.port
                                           && Objects.equals(authenticator, other.authenticator));
                }

                /**
                 * Returns a hash code derived from the host, port and
                 * authenticator.
                 *
                 * @return the hash code
                 */
                @Override
                public int hashCode() {
                    return Objects.hash(host, port, authenticator);
                }

                /**
                 * Returns a human-readable description of this proxy
                 * configuration suitable for logs.
                 *
                 * @return the string representation
                 */
                @Override
                public String toString() {
                    return "Socks.V4.Local[host=" + host
                           + ", port=" + port
                           + ", authenticator=" + authenticator + "]";
                }
            }

            /**
             * A SOCKS4a proxy configuration in which hostname
             * resolution happens on the proxy side via the
             * {@code 0.0.0.x} sentinel IP.
             */
            final class Remote implements V4 {

                /**
                 * The proxy hostname.
                 */
                private final String host;

                /**
                 * The proxy TCP port.
                 */
                private final int port;

                /**
                 * The SOCKS4 user ID authenticator, or {@code null} if
                 * no user ID is supplied.
                 */
                private final WhatsAppProxyAuthenticator.Socks.V4 authenticator;

                /**
                 * Constructs a new SOCKS4a remote-DNS proxy
                 * configuration.
                 *
                 * @param host          the proxy hostname, must not be
                 *                      {@code null}
                 * @param port          the proxy port, must be between
                 *                      {@code 1} and {@code 65535}
                 * @param authenticator the user ID authenticator, or
                 *                      {@code null}
                 * @throws NullPointerException     if {@code host} is
                 *                                  {@code null}
                 * @throws IllegalArgumentException if {@code port} is
                 *                                  out of range
                 */
                private Remote(String host, int port, WhatsAppProxyAuthenticator.Socks.V4 authenticator) {
                    Objects.requireNonNull(host, "host");
                    if (port < 1 || port > 65535) {
                        throw new IllegalArgumentException(
                                "port must be between 1 and 65535: " + port);
                    }
                    this.host = host;
                    this.port = port;
                    this.authenticator = authenticator;
                }

                /**
                 * Returns the proxy hostname.
                 *
                 * @return the hostname
                 */
                @Override
                public String host() {
                    return host;
                }

                /**
                 * Returns the proxy TCP port.
                 *
                 * @return the port
                 */
                @Override
                public int port() {
                    return port;
                }

                /**
                 * Returns the SOCKS4 user ID authenticator.
                 *
                 * @return an {@link Optional} wrapping the
                 *         authenticator, or empty if none is configured
                 */
                @Override
                public Optional<WhatsAppProxyAuthenticator.Socks.V4> authenticator() {
                    return Optional.ofNullable(authenticator);
                }

                /**
                 * Compares this proxy to the given object for
                 * structural equality.
                 *
                 * @param obj the object to compare with
                 * @return {@code true} if {@code obj} is another
                 *         {@code Remote} with the same host, port and
                 *         authenticator
                 */
                @Override
                public boolean equals(Object obj) {
                    return obj == this || (obj instanceof Remote other
                                           && host.equals(other.host)
                                           && port == other.port
                                           && Objects.equals(authenticator, other.authenticator));
                }

                /**
                 * Returns a hash code derived from the host, port and
                 * authenticator.
                 *
                 * @return the hash code
                 */
                @Override
                public int hashCode() {
                    return Objects.hash(host, port, authenticator);
                }

                /**
                 * Returns a human-readable description of this proxy
                 * configuration suitable for logs.
                 *
                 * @return the string representation
                 */
                @Override
                public String toString() {
                    return "Socks.V4.Remote[host=" + host
                           + ", port=" + port
                           + ", authenticator=" + authenticator + "]";
                }
            }
        }

        /**
         * A SOCKS5 proxy configuration as defined by RFC 1928.
         *
         * <p>Hostnames may be resolved by the client ({@link Local})
         * or by the proxy ({@link Remote}, commonly referred to as
         * {@code socks5h}).
         */
        sealed interface V5 extends Socks {

            /**
             * Returns the SOCKS5 authenticator configured for this
             * proxy.
             *
             * @return an {@link Optional} wrapping the SOCKS5
             *         authenticator, or empty if anonymous connections
             *         are used
             */
            @Override
            Optional<WhatsAppProxyAuthenticator.Socks.V5> authenticator();

            /**
             * A SOCKS5 proxy configuration that resolves hostnames on
             * the client side, as defined by RFC 1928.
             */
            final class Local implements V5 {

                /**
                 * The proxy hostname.
                 */
                private final String host;

                /**
                 * The proxy TCP port.
                 */
                private final int port;

                /**
                 * The SOCKS5 authenticator, or {@code null} if
                 * anonymous connections are used.
                 */
                private final WhatsAppProxyAuthenticator.Socks.V5 authenticator;

                /**
                 * Constructs a new SOCKS5 local-DNS proxy
                 * configuration.
                 *
                 * @param host          the proxy hostname, must not be
                 *                      {@code null}
                 * @param port          the proxy port, must be between
                 *                      {@code 1} and {@code 65535}
                 * @param authenticator the SOCKS5 authenticator, or
                 *                      {@code null}
                 * @throws NullPointerException     if {@code host} is
                 *                                  {@code null}
                 * @throws IllegalArgumentException if {@code port} is
                 *                                  out of range
                 */
                private Local(String host, int port, WhatsAppProxyAuthenticator.Socks.V5 authenticator) {
                    Objects.requireNonNull(host, "host");
                    if (port < 1 || port > 65535) {
                        throw new IllegalArgumentException(
                                "port must be between 1 and 65535: " + port);
                    }
                    this.host = host;
                    this.port = port;
                    this.authenticator = authenticator;
                }

                /**
                 * Returns the proxy hostname.
                 *
                 * @return the hostname
                 */
                @Override
                public String host() {
                    return host;
                }

                /**
                 * Returns the proxy TCP port.
                 *
                 * @return the port
                 */
                @Override
                public int port() {
                    return port;
                }

                /**
                 * Returns the SOCKS5 authenticator associated with
                 * this proxy.
                 *
                 * @return an {@link Optional} wrapping the
                 *         authenticator, or empty if none is configured
                 */
                @Override
                public Optional<WhatsAppProxyAuthenticator.Socks.V5> authenticator() {
                    return Optional.ofNullable(authenticator);
                }

                /**
                 * Compares this proxy to the given object for
                 * structural equality.
                 *
                 * @param obj the object to compare with
                 * @return {@code true} if {@code obj} is another
                 *         {@code Local} with the same host, port and
                 *         authenticator
                 */
                @Override
                public boolean equals(Object obj) {
                    return obj == this || (obj instanceof Local other
                                           && host.equals(other.host)
                                           && port == other.port
                                           && Objects.equals(authenticator, other.authenticator));
                }

                /**
                 * Returns a hash code derived from the host, port and
                 * authenticator.
                 *
                 * @return the hash code
                 */
                @Override
                public int hashCode() {
                    return Objects.hash(host, port, authenticator);
                }

                /**
                 * Returns a human-readable description of this proxy
                 * configuration suitable for logs.
                 *
                 * @return the string representation
                 */
                @Override
                public String toString() {
                    return "Socks.V5.Local[host=" + host
                           + ", port=" + port
                           + ", authenticator=" + authenticator + "]";
                }
            }

            /**
             * A SOCKS5 proxy configuration with remote DNS resolution,
             * commonly referred to as {@code socks5h}.
             */
            final class Remote implements V5 {

                /**
                 * The proxy hostname.
                 */
                private final String host;

                /**
                 * The proxy TCP port.
                 */
                private final int port;

                /**
                 * The SOCKS5 authenticator, or {@code null} if
                 * anonymous connections are used.
                 */
                private final WhatsAppProxyAuthenticator.Socks.V5 authenticator;

                /**
                 * Constructs a new SOCKS5 remote-DNS proxy
                 * configuration.
                 *
                 * @param host          the proxy hostname, must not be
                 *                      {@code null}
                 * @param port          the proxy port, must be between
                 *                      {@code 1} and {@code 65535}
                 * @param authenticator the SOCKS5 authenticator, or
                 *                      {@code null}
                 * @throws NullPointerException     if {@code host} is
                 *                                  {@code null}
                 * @throws IllegalArgumentException if {@code port} is
                 *                                  out of range
                 */
                private Remote(String host, int port, WhatsAppProxyAuthenticator.Socks.V5 authenticator) {
                    Objects.requireNonNull(host, "host");
                    if (port < 1 || port > 65535) {
                        throw new IllegalArgumentException(
                                "port must be between 1 and 65535: " + port);
                    }
                    this.host = host;
                    this.port = port;
                    this.authenticator = authenticator;
                }

                /**
                 * Returns the proxy hostname.
                 *
                 * @return the hostname
                 */
                @Override
                public String host() {
                    return host;
                }

                /**
                 * Returns the proxy TCP port.
                 *
                 * @return the port
                 */
                @Override
                public int port() {
                    return port;
                }

                /**
                 * Returns the SOCKS5 authenticator associated with
                 * this proxy.
                 *
                 * @return an {@link Optional} wrapping the
                 *         authenticator, or empty if none is configured
                 */
                @Override
                public Optional<WhatsAppProxyAuthenticator.Socks.V5> authenticator() {
                    return Optional.ofNullable(authenticator);
                }

                /**
                 * Compares this proxy to the given object for
                 * structural equality.
                 *
                 * @param obj the object to compare with
                 * @return {@code true} if {@code obj} is another
                 *         {@code Remote} with the same host, port and
                 *         authenticator
                 */
                @Override
                public boolean equals(Object obj) {
                    return obj == this || (obj instanceof Remote other
                                           && host.equals(other.host)
                                           && port == other.port
                                           && Objects.equals(authenticator, other.authenticator));
                }

                /**
                 * Returns a hash code derived from the host, port and
                 * authenticator.
                 *
                 * @return the hash code
                 */
                @Override
                public int hashCode() {
                    return Objects.hash(host, port, authenticator);
                }

                /**
                 * Returns a human-readable description of this proxy
                 * configuration suitable for logs.
                 *
                 * @return the string representation
                 */
                @Override
                public String toString() {
                    return "Socks.V5.Remote[host=" + host
                           + ", port=" + port
                           + ", authenticator=" + authenticator + "]";
                }
            }
        }
    }
}