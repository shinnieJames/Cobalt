package com.github.auties00.cobalt.client.cloud;

import com.github.auties00.cobalt.client.WhatsAppClientBuilder;
import com.github.auties00.cobalt.client.WhatsAppClientErrorHandler;
import com.github.auties00.cobalt.client.WhatsAppClientProxy;
import com.github.auties00.cobalt.client.WhatsAppClientProxyAuthenticator;
import com.github.auties00.cobalt.model.cloud.CloudApiVersion;
import com.github.auties00.cobalt.store.CloudWhatsAppStore;
import com.github.auties00.cobalt.store.CloudWhatsAppStoreBuilder;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.util.Objects;

/**
 * A fluent builder that constructs {@link CloudWhatsAppClient} instances.
 *
 * <p>The builder is staged, mirroring the {@code LinkedWhatsAppClientBuilder} flow: the entry stage
 * loads a connection, either from the Cloud credentials
 * ({@link #loadConnection(String, String)}) or from a pre-built {@link CloudWhatsAppStore}
 * ({@link #loadConnection(CloudWhatsAppStore)}); the returned stage then exposes only the
 * configuration that applies to it. {@link Options} collects the optional Graph configuration
 * (WhatsApp Business Account id, business portfolio id, API version, app secret), the transport
 * configuration (proxy, HTTP client, error handler), and the webhook receiver, which is itself a
 * sub-stage ({@link Options#webhook(String, int)} returning {@link Webhook}) so the receiver is
 * either configured as one explicit decision or not at all.
 *
 * @see CloudWhatsAppClient
 * @see WhatsAppClientBuilder#cloudApi()
 */
public sealed class CloudWhatsAppClientBuilder permits CloudWhatsAppClientBuilder.Transport {
    /**
     * Package-private constructor; obtain instances via {@link CloudWhatsAppClient#builder()}.
     */
    CloudWhatsAppClientBuilder() {

    }

    /**
     * Loads a connection from the Cloud credentials.
     *
     * @param accessToken   the system-user access token
     * @param phoneNumberId the operating phone number id
     * @return the next builder stage
     * @throws NullPointerException if {@code accessToken} or {@code phoneNumberId} is {@code null}
     */
    public Options loadConnection(String accessToken, String phoneNumberId) {
        Objects.requireNonNull(accessToken, "accessToken must not be null");
        Objects.requireNonNull(phoneNumberId, "phoneNumberId must not be null");
        return new Options(accessToken, phoneNumberId);
    }

    /**
     * Loads a connection from a pre-built store, bypassing the credential collection flow.
     *
     * @param store the store carrying the credentials and webhook configuration
     * @return the next builder stage
     * @throws NullPointerException if {@code store} is {@code null}
     */
    public Custom loadConnection(CloudWhatsAppStore store) {
        Objects.requireNonNull(store, "store must not be null");
        return new Custom(store);
    }

    /**
     * A builder stage that collects the transport configuration shared by every connection flavour:
     * the proxy, the HTTP client, and the error handler.
     *
     * @param <T> the concrete stage type returned by each setter, for fluent chaining
     */
    public static abstract sealed class Transport<T extends Transport<T>> extends CloudWhatsAppClientBuilder permits Options, Custom {
        /**
         * The proxy routing outbound Graph traffic, or {@code null} for a direct connection.
         */
        WhatsAppClientProxy proxy;

        /**
         * The HTTP client used by the transport, or {@code null} to build a default-configured one.
         */
        HttpClient httpClient;

        /**
         * The error handler installed on the future client, or {@code null} to use the default.
         */
        WhatsAppClientErrorHandler errorHandler;

        /**
         * Package-private constructor used by the concrete stages.
         */
        Transport() {

        }

        /**
         * Returns {@code this}, narrowed to the concrete stage type.
         *
         * @return this stage
         */
        abstract T self();

        /**
         * Sets the proxy that routes outbound Graph traffic.
         *
         * <p>The Cloud transport rides {@code java.net.http}, which supports only HTTP {@code CONNECT}
         * proxies; supplying a SOCKS proxy fails at build time. The proxy applies to the
         * default-built HTTP client; when an explicit {@link #httpClient(HttpClient)} is supplied,
         * its own proxy configuration wins.
         *
         * @param proxy the proxy, or {@code null} for a direct connection
         * @return this builder, for chaining
         */
        public T proxy(WhatsAppClientProxy proxy) {
            this.proxy = proxy;
            return self();
        }

        /**
         * Sets the HTTP client used by the transport.
         *
         * @param httpClient the HTTP client, or {@code null} to build a default-configured one
         * @return this builder, for chaining
         */
        public T httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return self();
        }

        /**
         * Sets the error handler that decides how the future client reacts to failures.
         *
         * @param errorHandler the error handler, or {@code null} to use the default
         *                     terminal-printing handler
         * @return this builder, for chaining
         */
        public T errorHandler(WhatsAppClientErrorHandler errorHandler) {
            this.errorHandler = errorHandler;
            return self();
        }

        /**
         * Builds the configured Cloud client around the given store.
         *
         * @param store the store backing the client
         * @return the configured client
         * @throws IllegalArgumentException if a SOCKS proxy was supplied
         */
        final CloudWhatsAppClient build(CloudWhatsAppStore store) {
            var resolvedErrorHandler = Objects.requireNonNullElseGet(errorHandler, WhatsAppClientErrorHandler::toTerminal);
            var resolvedHttpClient = httpClient != null ? httpClient : buildHttpClient();
            return new LiveCloudWhatsAppClient(store, resolvedErrorHandler, resolvedHttpClient);
        }

        /**
         * Builds the default HTTP client, applying the configured proxy.
         *
         * @return the HTTP client
         * @throws IllegalArgumentException if a SOCKS proxy was supplied
         */
        private HttpClient buildHttpClient() {
            if (proxy == null) {
                return HttpClient.newHttpClient();
            }
            if (proxy instanceof WhatsAppClientProxy.Socks) {
                throw new IllegalArgumentException("The Cloud transport supports only HTTP proxies");
            }
            var builder = HttpClient.newBuilder()
                    .proxy(ProxySelector.of(new InetSocketAddress(proxy.host(), proxy.port())));
            if (proxy.authenticator().orElse(null) instanceof WhatsAppClientProxyAuthenticator.Http.Basic basic) {
                builder.authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(basic.username(), basic.password().toCharArray());
                    }
                });
            }
            return builder.build();
        }
    }

    /**
     * The credential-backed builder stage.
     *
     * <p>Collects the optional Graph configuration and the webhook receiver around the credentials
     * supplied to {@link CloudWhatsAppClientBuilder#loadConnection(String, String)}, then produces
     * the client with {@link #build()}.
     */
    public static sealed class Options extends Transport<Options> permits Webhook {
        /**
         * The system-user access token.
         */
        final String accessToken;

        /**
         * The operating phone number id.
         */
        final String phoneNumberId;

        /**
         * The WhatsApp Business Account id, or {@code null} when management edges are unused.
         */
        String whatsappBusinessAccountId;

        /**
         * The business portfolio id, or {@code null} when onboarding edges are unused.
         */
        String businessId;

        /**
         * The graph API version.
         */
        CloudApiVersion apiVersion = CloudApiVersion.DEFAULT;

        /**
         * The app secret, or {@code null} when proofs and signature checks are disabled.
         */
        String appSecret;

        /**
         * Constructs the stage around the required credentials.
         *
         * @param accessToken   the system-user access token
         * @param phoneNumberId the operating phone number id
         */
        Options(String accessToken, String phoneNumberId) {
            this.accessToken = accessToken;
            this.phoneNumberId = phoneNumberId;
        }

        /**
         * Copy constructor used by the {@link Webhook} sub-stage.
         *
         * @param source the stage whose configuration is inherited
         */
        Options(Options source) {
            this.accessToken = source.accessToken;
            this.phoneNumberId = source.phoneNumberId;
            this.whatsappBusinessAccountId = source.whatsappBusinessAccountId;
            this.businessId = source.businessId;
            this.apiVersion = source.apiVersion;
            this.appSecret = source.appSecret;
            this.proxy = source.proxy;
            this.httpClient = source.httpClient;
            this.errorHandler = source.errorHandler;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        Options self() {
            return this;
        }

        /**
         * Sets the WhatsApp Business Account id used by template and phone-number management edges.
         *
         * @param whatsappBusinessAccountId the WABA id, or {@code null} to leave it unset
         * @return this builder, for chaining
         */
        public Options whatsappBusinessAccountId(String whatsappBusinessAccountId) {
            this.whatsappBusinessAccountId = whatsappBusinessAccountId;
            return this;
        }

        /**
         * Sets the business portfolio id used by partner onboarding edges.
         *
         * @param businessId the business id, or {@code null} to leave it unset
         * @return this builder, for chaining
         */
        public Options businessId(String businessId) {
            this.businessId = businessId;
            return this;
        }

        /**
         * Sets the graph API version targeted by every request.
         *
         * @param apiVersion the API version, or {@code null} to keep the default
         *                   ({@link CloudApiVersion#DEFAULT})
         * @return this builder, for chaining
         */
        public Options apiVersion(CloudApiVersion apiVersion) {
            if (apiVersion != null) {
                this.apiVersion = apiVersion;
            }
            return this;
        }

        /**
         * Sets the app secret used for {@code appsecret_proof} and webhook signature verification.
         *
         * @param appSecret the app secret, or {@code null} to disable proofs and signature checks
         * @return this builder, for chaining
         */
        public Options appSecret(String appSecret) {
            this.appSecret = appSecret;
            return this;
        }

        /**
         * Configures the built-in webhook receiver and moves to the webhook sub-stage.
         *
         * <p>The verify token is echoed during the subscription handshake and the port is where the
         * receiver binds. Skipping this step leaves the client send-only.
         *
         * @param verifyToken the webhook verify token
         * @param port        the TCP port to bind
         * @return the webhook sub-stage
         * @throws NullPointerException if {@code verifyToken} is {@code null}
         */
        public Webhook webhook(String verifyToken, int port) {
            Objects.requireNonNull(verifyToken, "verifyToken must not be null");
            return new Webhook(this, verifyToken, port);
        }

        /**
         * Builds the configured Cloud client.
         *
         * @return the configured client
         * @throws IllegalArgumentException if a SOCKS proxy was supplied
         */
        public CloudWhatsAppClient build() {
            return build(buildStore(null, null, 0, null));
        }

        /**
         * Builds the backing store from the collected configuration.
         *
         * @param webhookVerifyToken the webhook verify token, or {@code null} when the receiver is
         *                           disabled
         * @param webhookBindAddress the webhook bind address, or {@code null} to bind the wildcard
         *                           address
         * @param webhookPort        the webhook port, or {@code 0} when the receiver is disabled
         * @param webhookPath        the webhook URL path, or {@code null} for the default
         * @return the backing store
         */
        final CloudWhatsAppStore buildStore(String webhookVerifyToken, String webhookBindAddress, int webhookPort, String webhookPath) {
            return new CloudWhatsAppStoreBuilder()
                    .accessToken(accessToken)
                    .phoneNumberId(phoneNumberId)
                    .whatsappBusinessAccountId(whatsappBusinessAccountId)
                    .businessId(businessId)
                    .apiVersion(apiVersion.version())
                    .appSecret(appSecret)
                    .webhookVerifyToken(webhookVerifyToken)
                    .webhookBindAddress(webhookBindAddress)
                    .webhookPort(webhookPort == 0 ? null : webhookPort)
                    .webhookPath(webhookPath)
                    .build();
        }
    }

    /**
     * The webhook-receiver builder sub-stage.
     *
     * <p>Reached through {@link Options#webhook(String, int)}; refines the receiver with an optional
     * bind address and URL path, then produces the client with {@link #build()}.
     */
    public static final class Webhook extends Options {
        /**
         * The default webhook URL path.
         */
        private static final String DEFAULT_WEBHOOK_PATH = "/webhook";

        /**
         * The webhook verify token.
         */
        private final String verifyToken;

        /**
         * The webhook port.
         */
        private final int port;

        /**
         * The webhook bind address, or {@code null} to bind the wildcard address.
         */
        private String bindAddress;

        /**
         * The webhook URL path.
         */
        private String path = DEFAULT_WEBHOOK_PATH;

        /**
         * Constructs the sub-stage around the receiver essentials.
         *
         * @param source      the stage whose configuration is inherited
         * @param verifyToken the webhook verify token
         * @param port        the TCP port to bind
         */
        Webhook(Options source, String verifyToken, int port) {
            super(source);
            this.verifyToken = verifyToken;
            this.port = port;
        }

        /**
         * Sets the webhook bind address.
         *
         * @param bindAddress the bind address, or {@code null} to bind the wildcard address
         * @return this builder, for chaining
         */
        public Webhook bindAddress(String bindAddress) {
            this.bindAddress = bindAddress;
            return this;
        }

        /**
         * Sets the webhook URL path.
         *
         * @param path the URL path, or {@code null} to keep the default {@code /webhook}
         * @return this builder, for chaining
         */
        public Webhook path(String path) {
            if (path != null) {
                this.path = path;
            }
            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public CloudWhatsAppClient build() {
            return build(buildStore(verifyToken, bindAddress, port, path));
        }
    }

    /**
     * The pre-built-store builder stage.
     *
     * <p>Reached through {@link CloudWhatsAppClientBuilder#loadConnection(CloudWhatsAppStore)}; the
     * Graph and webhook configuration is read from the store, so only the transport configuration
     * remains to collect before {@link #build()}.
     */
    public static final class Custom extends Transport<Custom> {
        /**
         * The pre-built store backing the future client.
         */
        private final CloudWhatsAppStore store;

        /**
         * Constructs the stage around the pre-built store.
         *
         * @param store the store backing the future client
         */
        Custom(CloudWhatsAppStore store) {
            this.store = store;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        Custom self() {
            return this;
        }

        /**
         * Builds the configured Cloud client.
         *
         * @return the configured client
         * @throws IllegalArgumentException if a SOCKS proxy was supplied
         */
        public CloudWhatsAppClient build() {
            return build(store);
        }
    }
}
