package com.github.auties00.cobalt.client;

import com.github.auties00.cobalt.client.registration.WhatsAppMobileClientRegistration;
import com.github.auties00.cobalt.model.business.profile.BusinessCategory;
import com.github.auties00.cobalt.model.device.pairing.ClientAppVersion;
import com.github.auties00.cobalt.model.device.pairing.ClientPlatformType;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.store.WhatsAppStoreFactory;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A fluent builder that constructs {@link WhatsAppClient} instances.
 *
 * <p>The builder exposes three entry points via
 * {@link #webClient()}, {@link #mobileClient()}, and
 * {@link #customClient()}. Each entry point returns a specialised
 * sub-builder that guides the caller through the steps needed for that
 * client flavour: selecting a store factory, loading or creating a
 * connection, providing a verification handler, and finally producing the
 * {@link WhatsAppClient}. The specialisations use a sealed class
 * hierarchy so each step exposes only the parameters that apply to it,
 * keeping the surface type-safe.
 *
 * @see WhatsAppClient
 */

public sealed class WhatsAppClientBuilder {
    /**
     * The default message preview handler: preview inference is enabled.
     */
    private static final WhatsAppClientMessagePreviewHandler DEFAULT_MESSAGE_PREVIEW_HANDLER = WhatsAppClientMessagePreviewHandler.enabled(true);
    /**
     * The default error handler: prints stack traces to stderr.
     */
    private static final WhatsAppClientErrorHandler DEFAULT_ERROR_HANDLER = WhatsAppClientErrorHandler.toTerminal();
    /**
     * The default web verification handler: renders the QR code in the
     * terminal.
     */
    private static final WhatsAppClientVerificationHandler.Web DEFAULT_WEB_VERIFICATION_HANDLER = WhatsAppClientVerificationHandler.Web.QrCode.toTerminal();

    /**
     * The shared root builder, accessed via {@link WhatsAppClient#builder()}.
     */
    static final WhatsAppClientBuilder INSTANCE = new WhatsAppClientBuilder();

    /**
     * Private singleton constructor; obtain instances via
     * {@link WhatsAppClient#builder()}.
     */
    private WhatsAppClientBuilder() {

    }

    /**
     * Creates a web client with the default Protobuf serializer
     *
     * @return a non-null web client instance
     */
    public Client.Web webClient() {
        return new Client.Web(WhatsAppStoreFactory.inMemory());
    }

    /**
     * Creates a web client with a custom factory
     *
     * @param factory the factory to use for data persistence, must not be null
     * @return a non-null web client instance
     * @throws NullPointerException if factory is null
     */
    public Client.Web webClient(WhatsAppStoreFactory factory) {
        Objects.requireNonNull(factory, "factory must not be null");
        return new Client.Web(factory);
    }

    /**
     * Creates a mobile client with the default Protobuf serializer
     *
     * @return a non-null mobile client instance
     */
    public Client.Mobile mobileClient() {
        return new Client.Mobile(WhatsAppStoreFactory.inMemory());
    }

    /**
     * Creates a mobile client with a custom factory
     *
     * @param factory the factory to use for data persistence, must not be null
     * @return a non-null mobile client instance
     * @throws NullPointerException if factory is null
     */
    public Client.Mobile mobileClient(WhatsAppStoreFactory factory) {
        Objects.requireNonNull(factory, "factory must not be null");
        return new Client.Mobile(factory);
    }

    /**
     * Creates a custom client for advanced configuration
     *
     * @return a non-null custom client instance
     */
    public Custom customClient() {
        return new Custom();
    }

    /**
     * A builder stage that selects an existing persisted session or
     * provisions a new one, backed by a {@link WhatsAppStoreFactory}.
     *
     * <p>Sub-types {@link Web} and {@link Mobile} specialise the behaviour
     * to the respective client flavour. Every {@code loadXxx} method
     * offers a parallel {@code loadOrCreateXxx} variant that falls back
     * to provisioning a fresh store when the lookup is not satisfied.
     */
    public static abstract sealed class Client extends WhatsAppClientBuilder {
        /**
         * The store factory that loads or creates the session on disk.
         */
        final WhatsAppStoreFactory factory;

        /**
         * Constructs a new {@code Client} stage backed by the given
         * factory.
         *
         * @param factory the store factory; must not be {@code null}
         * @throws NullPointerException if {@code factory} is {@code null}
         */
        private Client(WhatsAppStoreFactory factory) {
            this.factory = Objects.requireNonNull(factory, "factory must not be null");
        }

        /**
         * Creates a new connection using a random UUID
         *
         * @return a non-null options selector
         */
        public abstract Options createConnection() throws IOException;

        /**
         * Loads a connection from the six parts key representation
         *
         * @param sixParts the six parts keys to use to create the connection, must not be null
         * @return a non-null options selector
         * @throws NullPointerException if sixParts is null
         */
        public abstract Optional<? extends Options> loadConnection(WhatsAppClientSixPartsKeys sixParts) throws IOException;

        /**
         * Loads the last serialized connection.
         * If no connection is available, an empty {@link Optional} will be returned.
         *
         * @return an {@link Optional} containing the last serialized connection, empty otherwise
         */
        public abstract Optional<Options> loadLatestConnection() throws IOException;

        /**
         * Loads the last serialized connection.
         * If no connection is available, a new one will be created.
         *
         * @return a non-null options selector
         */
        public abstract Options loadLatestOrCreateConnection() throws IOException;

        /**
         * Loads the connection whose id matches {@code uuid}.
         * If {@code uuid} is null, or if no connection has an id that matches {@code uuid}, an empty {@link Optional} will be returned.
         *
         * @param uuid the id to use for the connection; can be null
         * @return an {@link Optional} containing the connection whose id matches {@code uuid}, empty otherwise
         */
        public abstract Optional<? extends Options> loadConnection(UUID uuid) throws IOException;

        /**
         * Loads the connection whose id matches {@code uuid}.
         * If {@code uuid} is null, or if no connection has an id that matches {@code uuid}, a new connection will be created.
         *
         * @param uuid the id to use for the connection; can be null
         * @return a non-null options selector
         */
        public abstract Options loadOrCreateConnection(UUID uuid) throws IOException;

        /**
         * Loads the connection whose phone number matches the given UUID.
         * If the UUID is null, or if no connection matches the given UUID, a new connection will be created.
         *
         * @param phoneNumber the phone value to use to create the connection, can be null (will generate a random UUID)
         * @return a non-null options selector
         */
        public abstract Optional<? extends Options> loadConnection(Long phoneNumber) throws IOException;

        /**
         * Loads the connection whose id matches {@code phoneNumber}.
         * If {@code phoneNumber} is null, or if no connection matches {@code phoneNumber}, a new connection will be created.
         *
         * @param phoneNumber the id to use for the connection, can be null
         * @return a non-null options selector
         */
        public abstract Options loadOrCreateConnection(Long phoneNumber) throws IOException;

        /**
         * The {@link WhatsAppClientType#WEB} specialisation of the
         * {@code Client} stage.
         *
         * <p>Produces {@link Options.Web} instances whose store is tagged
         * as a web companion and whose subsequent verification flow
         * accepts QR codes or pairing codes.
         */
        public static final class Web extends Client {
            /**
             * Package-private constructor used by
             * {@link WhatsAppClientBuilder#webClient()}.
             *
             * @param factory the store factory for the web client
             */
            private Web(WhatsAppStoreFactory factory) {
                super(factory);
            }
            
            @Override
            public Options.Web createConnection() throws IOException {
                return loadOrCreateConnection(UUID.randomUUID());
            }
            
            @Override
            public Options.Web loadLatestOrCreateConnection() throws IOException {
                var existingStore = factory.loadLatest(WhatsAppClientType.WEB);
                if (existingStore.isPresent()) {
                    return new Options.Web(existingStore.get());
                }

                var newStore = factory.create(WhatsAppClientType.WEB, UUID.randomUUID());
                return new Options.Web(newStore);
            }

            @Override
            public Optional<Options> loadConnection(UUID uuid) throws IOException {
                if (uuid == null) {
                    return Optional.empty();
                }

                var store = factory.loadLatest(WhatsAppClientType.WEB);
                if (store.isEmpty()) {
                    return Optional.empty();
                }

                var result = new Options.Web(store.get());
                return Optional.of(result);
            }
            
            @Override
            public Options.Web loadOrCreateConnection(UUID uuid) throws IOException {
                if (uuid == null) {
                    var store = factory.create(WhatsAppClientType.WEB, UUID.randomUUID());
                    return new Options.Web(store);
                }

                var existingStore = factory.load(WhatsAppClientType.WEB, uuid);
                if (existingStore.isPresent()) {
                    return new Options.Web(existingStore.get());
                }

                var newStore = factory.create(WhatsAppClientType.WEB, uuid);
                return new Options.Web(newStore);
            }

            @Override
            public Optional<Options> loadConnection(Long phoneNumber) throws IOException {
                if (phoneNumber == null) {
                    return Optional.empty();
                }

                var existingStore = factory.load(WhatsAppClientType.WEB, phoneNumber);
                if (existingStore.isEmpty()) {
                    return Optional.empty();
                }

                var result = new Options.Web(existingStore.get());
                return Optional.of(result);
            }
            
            @Override
            public Options.Web loadOrCreateConnection(Long phoneNumber) throws IOException {
                if (phoneNumber == null) {
                    var store = factory.create(WhatsAppClientType.WEB, UUID.randomUUID());
                    return new Options.Web(store);
                }

                var existingStore = factory.load(WhatsAppClientType.WEB, phoneNumber);
                if (existingStore.isPresent()) {
                    return new Options.Web(existingStore.get());
                }

                var newStore = factory.create(WhatsAppClientType.WEB, phoneNumber);
                return new Options.Web(newStore);
            }
            
            @Override
            public Optional<Options.Web> loadConnection(WhatsAppClientSixPartsKeys sixParts) throws IOException {
                if (sixParts == null) {
                    return Optional.empty();
                }

                var store = factory.load(WhatsAppClientType.WEB, sixParts);
                if (store.isEmpty()) {
                    return Optional.empty();
                }

                var result = new Options.Web(store.get());
                return Optional.of(result);
            }

            @Override
            public Optional<Options> loadLatestConnection() throws IOException {
                var store = factory.loadLatest(WhatsAppClientType.WEB);
                if (store.isEmpty()) {
                    return Optional.empty();
                }

                var result = new Options.Web(store.get());
                return Optional.of(result);
            }
        }

        /**
         * The {@link WhatsAppClientType#MOBILE} specialisation of the
         * {@code Client} stage.
         *
         * <p>Produces {@link Options.Mobile} instances whose store is
         * tagged as a primary mobile device; the subsequent step lets the
         * caller register a phone number via
         * {@link Options.Mobile#register(long, WhatsAppClientVerificationHandler.Mobile)}.
         */
        public static final class Mobile extends Client {
            /**
             * Package-private constructor used by
             * {@link WhatsAppClientBuilder#mobileClient()}.
             *
             * @param factory the store factory for the mobile client
             */
            private Mobile(WhatsAppStoreFactory factory) {
                super(factory);
            }

            @Override
            public Options.Mobile createConnection() throws IOException {
                return loadOrCreateConnection(UUID.randomUUID());
            }

            @Override
            public Options.Mobile loadLatestOrCreateConnection() throws IOException {
                var existingStore = factory.loadLatest(WhatsAppClientType.MOBILE);
                if (existingStore.isPresent()) {
                    return new Options.Mobile(existingStore.get());
                }

                var newStore = factory.create(WhatsAppClientType.MOBILE, UUID.randomUUID());
                return new Options.Mobile(newStore);
            }

            @Override
            public Optional<Options> loadConnection(UUID uuid) throws IOException {
                if (uuid == null) {
                    return Optional.empty();
                }

                var store = factory.loadLatest(WhatsAppClientType.MOBILE);
                if (store.isEmpty()) {
                    return Optional.empty();
                }

                var result = new Options.Mobile(store.get());
                return Optional.of(result);
            }

            @Override
            public Options.Mobile loadOrCreateConnection(UUID uuid) throws IOException {
                if (uuid == null) {
                    var store = factory.create(WhatsAppClientType.MOBILE, UUID.randomUUID());
                    return new Options.Mobile(store);
                }

                var existingStore = factory.load(WhatsAppClientType.MOBILE, uuid);
                if (existingStore.isPresent()) {
                    return new Options.Mobile(existingStore.get());
                }

                var newStore = factory.create(WhatsAppClientType.MOBILE, uuid);
                return new Options.Mobile(newStore);
            }

            @Override
            public Optional<Options> loadConnection(Long phoneNumber) throws IOException {
                if (phoneNumber == null) {
                    return Optional.empty();
                }

                var existingStore = factory.load(WhatsAppClientType.MOBILE, phoneNumber);
                if (existingStore.isEmpty()) {
                    return Optional.empty();
                }

                var result = new Options.Mobile(existingStore.get());
                return Optional.of(result);
            }

            @Override
            public Options.Mobile loadOrCreateConnection(Long phoneNumber) throws IOException {
                if (phoneNumber == null) {
                    var store = factory.create(WhatsAppClientType.MOBILE, UUID.randomUUID());
                    return new Options.Mobile(store);
                }

                var existingStore = factory.load(WhatsAppClientType.MOBILE, phoneNumber);
                if (existingStore.isPresent()) {
                    return new Options.Mobile(existingStore.get());
                }

                var newStore = factory.create(WhatsAppClientType.MOBILE, phoneNumber);
                return new Options.Mobile(newStore);
            }

            @Override
            public Optional<Options.Mobile> loadConnection(WhatsAppClientSixPartsKeys sixParts) throws IOException {
                if (sixParts == null) {
                    return Optional.empty();
                }

                var store = factory.load(WhatsAppClientType.MOBILE, sixParts);
                if (store.isEmpty()) {
                    return Optional.empty();
                }

                var result = new Options.Mobile(store.get());
                return Optional.of(result);
            }

            @Override
            public Optional<Options> loadLatestConnection() throws IOException {
                var store = factory.loadLatest(WhatsAppClientType.MOBILE);
                if (store.isEmpty()) {
                    return Optional.empty();
                }

                var result = new Options.Mobile(store.get());
                return Optional.of(result);
            }
        }
    }

    /**
     * A builder stage that applies session-wide options to a resolved
     * {@link WhatsAppStore} before the client is materialised.
     *
     * <p>Every fluent setter on this stage writes directly into the
     * underlying store (for things that must be persisted, such as the
     * proxy configuration, device profile, and client version) or into
     * local fields (for handlers, which are not serialised). Concrete
     * {@link Web} and {@link Mobile} specialisations add verification and
     * business-profile options that are only meaningful for their
     * respective flavours.
     */
    public static sealed class Options extends WhatsAppClientBuilder {
        /**
         * The resolved store on which configuration writes are applied.
         */
        final WhatsAppStore store;
        /**
         * The message preview handler installed on the future client.
         */
        WhatsAppClientMessagePreviewHandler messagePreviewHandler;
        /**
         * The error handler installed on the future client.
         */
        WhatsAppClientErrorHandler errorHandler;

        /**
         * Package-private constructor used by the {@link Client}
         * sub-builder once the store has been resolved.
         *
         * @param store the store to configure; must not be {@code null}
         * @throws NullPointerException if {@code store} is {@code null}
         */
        private Options(WhatsAppStore store) {
            this.store = Objects.requireNonNull(store, "store must not be null");
        }

        /**
         * Sets the display name
         * On Mobile, this is the preferred name that contacts that haven't saved you yet see next to your phone number.
         * On Web, this is the name of the companion device, visible in the "Linked Devices" tab
         *
         * @param name the name to set, can be null
         * @return the same instance for chaining
         */
        public Options name(String name) {
            store.setName(name);
            return this;
        }

        /**
         * Sets a proxy for the connection
         *
         * @param proxy the proxy to use, can be null to use no proxy
         * @return the same instance for chaining
         */
        public Options proxy(WhatsAppClientProxy proxy) {
            store.setProxy(proxy);
            return this;
        }

        /**
         * Sets the companion device for the connection
         *
         * @param device the companion device, can be null
         * @return the same instance for chaining
         */
        public Options device(WhatsAppDevice device) {
            store.setDevice(device);
            return this;
        }

        /**
         * Controls whether the library should send receipts automatically for messages
         * By default disabled
         * For the web API, if enabled, the companion won't receive notifications
         *
         * @param automaticMessageReceipts true to enable automatic message receipts, false otherwise
         * @return the same instance for chaining
         */
        public Options automaticMessageReceipts(boolean automaticMessageReceipts) {
            store.setAutomaticMessageReceipts(automaticMessageReceipts);
            return this;
        }

        /**
         * Sets the client version for the connection
         * This allows customization of the WhatsApp client version identifier
         *
         * @param clientVersion the client version to use, can be null to use the default
         * @return the same instance for chaining
         */
        public Options clientVersion(ClientAppVersion clientVersion) {
            store.setClientVersion(clientVersion);
            return this;
        }

        /**
         * Sets a handler for message previews
         *
         * @param messagePreviewHandler the handler to use, can be null
         * @return the same instance for chaining
         */
        public Options messagePreviewHandler(WhatsAppClientMessagePreviewHandler messagePreviewHandler) {
            this.messagePreviewHandler = messagePreviewHandler;
            return this;
        }

        /**
         * Sets an error handler for the connection
         *
         * @param errorHandler the error handler to use, can be null
         * @return the same instance for chaining
         */
        public Options errorHandler(WhatsAppClientErrorHandler errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        /**
         * The {@link WhatsAppClientType#WEB} specialisation of the
         * {@code Options} stage.
         *
         * <p>Adds the {@link #historySetting(WhatsAppWebClientHistory)}
         * option and the terminal step methods
         * {@link #unregistered(WhatsAppClientVerificationHandler.Web.QrCode)},
         * {@link #unregistered(long, WhatsAppClientVerificationHandler.Web.PairingCode)},
         * and {@link #registered()} that materialise the client.
         */
        public static final class Web extends Options {
            /**
             * Package-private constructor used by {@link Client.Web}.
             *
             * @param store the store resolved by the previous stage
             */
            private Web(WhatsAppStore store) {
                super(store);
            }

            /**
             * Sets the display name for the companion device, visible in the "Linked Devices" tab
             *
             * @param name the name to set, can be null
             * @return the same instance for chaining
             */
            @Override
            public Mobile name(String name) {
                return (Mobile) super.name(name);
            }

            /**
             * Sets a proxy for the connection
             *
             * @param proxy the proxy to use, can be null to use no proxy
             * @return the same instance for chaining
             */
            @Override
            public Web proxy(WhatsAppClientProxy proxy) {
                return (Web) super.proxy(proxy);
            }

            /**
             * Sets the companion device for the connection
             *
             * @param device the companion device, can be null
             * @return the same instance for chaining
             */
            @Override
            public Web device(WhatsAppDevice device) {
                return (Web) super.device(device);
            }

            /**
             * Sets a handler for message previews
             *
             * @param messagePreviewHandler the handler to use, can be null
             * @return the same instance for chaining
             */
            @Override
            public Web messagePreviewHandler(WhatsAppClientMessagePreviewHandler messagePreviewHandler) {
                return (Web) super.messagePreviewHandler(messagePreviewHandler);
            }

            /**
             * Sets an error handler for the connection
             *
             * @param errorHandler the error handler to use, can be null
             * @return the same instance for chaining
             */
            @Override
            public Web errorHandler(WhatsAppClientErrorHandler errorHandler) {
                return (Web) super.errorHandler(errorHandler);
            }

            /**
             * Controls whether the library should send receipts automatically for messages
             * By default disabled
             * For the web API, if enabled, the companion won't receive notifications
             *
             * @param automaticMessageReceipts true to enable automatic message receipts, false otherwise
             * @return the same instance for chaining
             */
            @Override
            public Web automaticMessageReceipts(boolean automaticMessageReceipts) {
                return (Web) super.automaticMessageReceipts(automaticMessageReceipts);
            }

            /**
             * Sets the client version for the connection
             * This allows customization of the WhatsApp client version identifier
             *
             * @param clientVersion the client version to use, can be null to use the default
             * @return the same instance for chaining
             */
            @Override
            public Web clientVersion(ClientAppVersion clientVersion) {
                return (Web) super.clientVersion(clientVersion);
            }

            /**
             * Sets how much chat history WhatsApp should send when the QR is first scanned
             * By default, one year
             *
             * @param historyLength the history policy to use, must not be null
             * @return the same instance for chaining
             * @throws NullPointerException if historyLength is null
             */
            public Web historySetting(WhatsAppWebClientHistory historyLength) {
                Objects.requireNonNull(historyLength, "historyLength must not be null");
                store.setWebHistoryPolicy(historyLength);
                return this;
            }

            /**
             * Creates a WhatsApp instance with a QR code handler
             *
             * @param qrHandler the handler to process QR codes, must not be null
             * @return a non-null WhatsApp instance
             * @throws NullPointerException if qrHandler is null
             */
            public WhatsAppClient unregistered(WhatsAppClientVerificationHandler.Web.QrCode qrHandler) {
                Objects.requireNonNull(qrHandler, "qrHandler must not be null");
                var messagePreviewHandler = Objects.requireNonNullElse(this.messagePreviewHandler, DEFAULT_MESSAGE_PREVIEW_HANDLER);
                var errorHandler = Objects.requireNonNullElse(this.errorHandler, DEFAULT_ERROR_HANDLER);
                return new WhatsAppClient(store, qrHandler, messagePreviewHandler, errorHandler);
            }

            /**
             * Creates a WhatsApp instance with an OTP handler
             *
             * @param phoneNumber the phone value of the user, must be valid
             * @param pairingCodeHandler the handler for the pairing code, must not be null
             * @return a non-null WhatsApp instance
             * @throws NullPointerException if pairingCodeHandler is null
             */
            public WhatsAppClient unregistered(long phoneNumber, WhatsAppClientVerificationHandler.Web.PairingCode pairingCodeHandler) {
                Objects.requireNonNull(pairingCodeHandler, "pairingCodeHandler must not be null");
                store.setPhoneNumber(phoneNumber);
                var messagePreviewHandler = Objects.requireNonNullElse(this.messagePreviewHandler, DEFAULT_MESSAGE_PREVIEW_HANDLER);
                var errorHandler = Objects.requireNonNullElse(this.errorHandler, DEFAULT_ERROR_HANDLER);
                return new WhatsAppClient(store, pairingCodeHandler, messagePreviewHandler, errorHandler);
            }

            /**
             * Creates a WhatsApp instance with no handlers
             * This method assumes that you have already logged in using a QR code or OTP
             * Otherwise, it returns an empty optional.
             *
             * @return an optional containing the WhatsApp instance if registered, empty otherwise
             */
            public Optional<WhatsAppClient> registered() {
                if (!store.registered()) {
                    return Optional.empty();
                }

                var messagePreviewHandler = Objects.requireNonNullElse(this.messagePreviewHandler, DEFAULT_MESSAGE_PREVIEW_HANDLER);
                var errorHandler = Objects.requireNonNullElse(this.errorHandler, DEFAULT_ERROR_HANDLER);
                var result = new WhatsAppClient(store, null, messagePreviewHandler, errorHandler);
                return Optional.of(result);
            }
        }

        /**
         * The {@link WhatsAppClientType#MOBILE} specialisation of the
         * {@code Options} stage.
         *
         * <p>Adds mobile-only setters for the account "about" text and
         * business profile (address, geolocation, description, website,
         * email, category) plus the terminal step methods
         * {@link #register(long, WhatsAppClientVerificationHandler.Mobile)}
         * and {@link #registered()}.
         */
        public static final class Mobile extends Options {
            /**
             * Device attestor captured by {@link #deviceAttestor}. Holds
             * at most one of {@link WhatsAppDeviceAttestor.Android} or
             * {@link WhatsAppDeviceAttestor.Ios}; {@code null} means "no
             * attestor configured" and the registration falls back to
             * the concrete subclass's NOOP.
             */
            private WhatsAppDeviceAttestor deviceAttestor;

            /**
             * Tracks whether the caller has explicitly selected the
             * device via {@link #device(WhatsAppDevice)}, as opposed to
             * inheriting the default produced by the store factory.
             *
             * <p>{@link #deviceAttestor} consults this flag to decide
             * whether an immediate platform-mismatch check is
             * meaningful: when the device is still the factory default
             * the caller has not expressed an intent yet, so the
             * deferred check in the terminal methods covers it instead.
             */
            private boolean deviceExplicitlySet;

            /**
             * Package-private constructor used by {@link Client.Mobile}.
             *
             * @param store the store resolved by the previous stage
             */
            private Mobile(WhatsAppStore store) {
                super(store);
            }

            /**
             * Sets a proxy for the connection
             *
             * @param proxy the proxy to use, can be null to use no proxy
             * @return the same instance for chaining
             */
            @Override
            public Mobile proxy(WhatsAppClientProxy proxy) {
                store.setProxy(proxy);
                return this;
            }

            /**
             * Sets the companion device for the connection.
             *
             * <p>If a device attestor has already been attached via
             * {@link #deviceAttestor}, the new device's platform is
             * validated against the attestor's sealed sub-type and the
             * call raises {@link IllegalArgumentException} on a
             * mismatch. The builder also flips an internal flag marking
             * the device as explicitly chosen so that a subsequent
             * {@code deviceAttestor} call can itself perform the
             * symmetric check.
             *
             * @param device the companion device, can be null
             * @return the same instance for chaining
             * @throws IllegalArgumentException if an attestor is already
             *                                  set and its platform
             *                                  does not match
             *                                  {@code device}
             */
            @Override
            public Mobile device(WhatsAppDevice device) {
                if (device != null) {
                    requirePlatformMatches(device, deviceAttestor);
                }
                store.setDevice(device);
                this.deviceExplicitlySet = true;
                return this;
            }

            /**
             * Sets the device attestor that produces the Play Integrity
             * or App Attest payloads (and, on Android, the
             * TEE-backed body signature) embedded into the upcoming
             * registration requests.
             *
             * <p>If {@link #device(WhatsAppDevice)} has already been
             * called explicitly, the attestor's sealed sub-type is
             * validated against the stored device's platform and the
             * call raises {@link IllegalArgumentException} on a
             * mismatch. When the device is still the factory default
             * the check is deferred to the terminal methods
             * ({@link #register} and {@link #registered()}), which
             * catches the case of an attestor set against the defaulted
             * device.
             *
             * <p>Passing {@code null} clears the attestor and brings
             * the registration back to the low-trust lane (the concrete
             * registration subclass's private NOOP attestor).
             *
             * @param attestor the device attestor, or {@code null} to
             *                 clear
             * @return the same instance for chaining
             * @throws IllegalArgumentException if {@code device(...)}
             *                                  was called explicitly
             *                                  and the stored device's
             *                                  platform does not match
             *                                  {@code attestor}
             */
            public Mobile deviceAttestor(WhatsAppDeviceAttestor attestor) {
                if (deviceExplicitlySet && attestor != null) {
                    requirePlatformMatches(store.device(), attestor);
                }
                this.deviceAttestor = attestor;
                return this;
            }

            /**
             * Cross-checks the currently stored device against the
             * currently stored attestor and raises
             * {@link IllegalArgumentException} if they do not agree.
             *
             * <p>Called by the terminal {@link #register} and
             * {@link #registered()} methods so that a caller that set an
             * attestor without also picking a matching device (thus
             * relying on the factory default) still gets a clear error
             * at the point the mismatch matters.
             *
             * @throws IllegalArgumentException if the stored attestor's
             *                                  platform does not match
             *                                  the stored device's
             *                                  platform
             */
            private void validateAttestorMatchesDevice() {
                if (deviceAttestor != null) {
                    requirePlatformMatches(store.device(), deviceAttestor);
                }
            }

            /**
             * Validates that the sub-interface of {@code attestor}
             * matches the platform carried by {@code device}.
             *
             * <p>A {@code null} attestor is always accepted, because
             * registration will fall back to the concrete subclass's
             * NOOP attestor.
             *
             * @param device the device whose platform the attestor must
             *               match; never {@code null}
             * @param attestor the device attestor to validate, or
             *                 {@code null} to skip the check
             * @throws IllegalArgumentException if the attestor's
             *                                  platform does not match
             *                                  the device's platform
             */
            private static void requirePlatformMatches(WhatsAppDevice device, WhatsAppDeviceAttestor attestor) {
                if (attestor == null) {
                    return;
                }
                var platform = device.platform();
                switch (attestor) {
                    case WhatsAppDeviceAttestor.Android ignored -> {
                        if (platform != ClientPlatformType.ANDROID
                                && platform != ClientPlatformType.ANDROID_BUSINESS) {
                            throw new IllegalArgumentException(
                                    "Android attestor requires an Android device, got platform: " + platform);
                        }
                    }
                    case WhatsAppDeviceAttestor.Ios ignored -> {
                        if (platform != ClientPlatformType.IOS
                                && platform != ClientPlatformType.IOS_BUSINESS) {
                            throw new IllegalArgumentException(
                                    "iOS attestor requires an iOS device, got platform: " + platform);
                        }
                    }
                }
            }

            /**
             * Sets an error handler for the connection
             *
             * @param errorHandler the error handler to use, can be null
             * @return the same instance for chaining
             */
            @Override
            public Mobile errorHandler(WhatsAppClientErrorHandler errorHandler) {
                super.errorHandler(errorHandler);
                return this;
            }

            /**
             * Controls whether the library should send receipts automatically for messages
             * By default disabled
             * For the web API, if enabled, the companion won't receive notifications
             *
             * @param automaticMessageReceipts true to enable automatic message receipts, false otherwise
             * @return the same instance for chaining
             */
            @Override
            public Mobile automaticMessageReceipts(boolean automaticMessageReceipts) {
                super.automaticMessageReceipts(automaticMessageReceipts);
                return this;
            }

            /**
             * Sets the client version for the connection
             * This allows customization of the WhatsApp client version identifier
             *
             * @param clientVersion the client version to use, can be null to use the default
             * @return the same instance for chaining
             */
            @Override
            public Mobile clientVersion(ClientAppVersion clientVersion) {
                return (Mobile) super.clientVersion(clientVersion);
            }

            /**
             * Sets the display name for the WhatsApp account
             * This is the preferred name that contacts that haven't saved you yet see next to your phone number.
             *
             * @param name the name to set, can be null
             * @return the same instance for chaining
             */
            @Override
            public Mobile name(String name) {
                return (Mobile) super.name(name);
            }

            /**
             * Sets the about/status message for the WhatsApp account
             *
             * @param about the about message to set, can be null
             * @return the same instance for chaining
             */
            public Mobile about(String about) {
                store.setAbout(about);
                return this;
            }

            /**
             * Sets the business' address
             *
             * @param businessAddress the address to set, can be null
             * @return the same instance for chaining
             */
            public Mobile businessAddress(String businessAddress) {
                store.setBusinessAddress(businessAddress);
                return this;
            }

            /**
             * Sets the business' address longitude
             *
             * @param businessLongitude the longitude to set, can be null
             * @return the same instance for chaining
             */
            public Mobile businessLongitude(Double businessLongitude) {
                store.setBusinessLongitude(businessLongitude);
                return this;
            }

            /**
             * Sets the business' address latitude
             *
             * @param businessLatitude the latitude to set, can be null
             * @return the same instance for chaining
             */
            public Mobile businessLatitude(Double businessLatitude) {
                store.setBusinessLatitude(businessLatitude);
                return this;
            }

            /**
             * Sets the business' description
             *
             * @param businessDescription the description to set, can be null
             * @return the same instance for chaining
             */
            public Mobile businessDescription(String businessDescription) {
                store.setBusinessDescription(businessDescription);
                return this;
            }

            /**
             * Sets the business' website URL
             *
             * @param businessWebsite the website URL to set, can be null
             * @return the same instance for chaining
             */
            public Mobile businessWebsite(String businessWebsite) {
                store.setBusinessWebsite(businessWebsite);
                return this;
            }

            /**
             * Sets the business' email address
             *
             * @param businessEmail the email address to set, can be null
             * @return the same instance for chaining
             */
            public Mobile businessEmail(String businessEmail) {
                store.setBusinessEmail(businessEmail);
                return this;
            }

            /**
             * Sets the business' category
             *
             * @param businessCategory the category to set, can be null
             * @return the same instance for chaining
             */
            public Mobile businessCategory(BusinessCategory businessCategory) {
                store.setBusinessCategory(businessCategory);
                return this;
            }

            /**
             * Creates a WhatsApp instance assuming the session is already registered
             * This means that the verification code has already been sent to WhatsApp
             *
             * @return an optional containing the WhatsApp instance if registered, empty otherwise
             * @throws IllegalArgumentException if an attestor was
             *                                  attached via
             *                                  {@link #deviceAttestor}
             *                                  whose platform does not
             *                                  match the configured
             *                                  device
             */
            public Optional<WhatsAppClient> registered() {
                validateAttestorMatchesDevice();
                if (!store.registered()) {
                    return Optional.empty();
                }

                var messagePreviewHandler = Objects.requireNonNullElse(this.messagePreviewHandler, DEFAULT_MESSAGE_PREVIEW_HANDLER);
                var errorHandler = Objects.requireNonNullElse(this.errorHandler, DEFAULT_ERROR_HANDLER);
                var result = new WhatsAppClient(store, null, messagePreviewHandler, errorHandler);
                return Optional.of(result);
            }

            /**
             * Creates a WhatsApp instance for a session that needs registration
             * This means that you may or may not have a verification code, but it hasn't been sent to WhatsApp yet
             *
             * @param phoneNumber the phone value to register, must be valid
             * @param verification the verification handler to use, must not be null
             * @return a non-null WhatsApp instance
             * @throws NullPointerException if verification is null
             * @throws IllegalArgumentException if the store already has a phone number set, and the phone number is different from the one being registered
             */
            public WhatsAppClient register(long phoneNumber, WhatsAppClientVerificationHandler.Mobile verification) {
                Objects.requireNonNull(verification, "verification must not be null");
                validateAttestorMatchesDevice();

                var oldPhoneNumber = store.phoneNumber();
                if(oldPhoneNumber.isPresent() && oldPhoneNumber.getAsLong() != phoneNumber) {
                    throw new IllegalArgumentException("The phone number(" + phoneNumber + ") must match the existing phone number(" + oldPhoneNumber.getAsLong() + ")");
                }else {
                    store.setPhoneNumber(phoneNumber);
                }

                if (!store.registered()) {
                    try(var registration = WhatsAppMobileClientRegistration.of(
                            store, verification, deviceAttestor)) {
                        registration.register();
                    }
                }

                var messagePreviewHandler = Objects.requireNonNullElse(this.messagePreviewHandler, DEFAULT_MESSAGE_PREVIEW_HANDLER);
                var errorHandler = Objects.requireNonNullElse(this.errorHandler, DEFAULT_ERROR_HANDLER);
                return new WhatsAppClient(store, null, messagePreviewHandler, errorHandler);
            }
        }
    }

    /**
     * A low-level builder stage that bypasses the
     * {@link WhatsAppStoreFactory} flow and lets the caller supply a
     * pre-built {@link WhatsAppStore}.
     *
     * <p>{@code Custom} is useful for test harnesses or for integrators
     * that already own a store (for example, one loaded from an external
     * database). The caller is responsible for ensuring the store's
     * {@link WhatsAppStore#clientType()} matches the intended flavour and
     * that the keys stored inside it are consistent with any identifiers
     * passed elsewhere in the build chain.
     */
    public static final class Custom extends WhatsAppClientBuilder {
        /**
         * The externally-supplied store.
         */
        private WhatsAppStore store;
        /**
         * The message preview handler to install on the built client.
         */
        private WhatsAppClientMessagePreviewHandler messagePreviewHandler;
        /**
         * The error handler to install on the built client.
         */
        private WhatsAppClientErrorHandler errorHandler;
        /**
         * The web verification handler to install on the built client,
         * only honoured when the store's client type is
         * {@link WhatsAppClientType#WEB}.
         */
        private WhatsAppClientVerificationHandler.Web webVerificationHandler;

        /**
         * Package-private constructor used by
         * {@link WhatsAppClientBuilder#customClient()}.
         */
        private Custom() {

        }

        /**
         * Sets the store for the connection
         *
         * @param store the store to use, can be null
         * @return the same instance for chaining
         */
        public Custom store(WhatsAppStore store) {
            this.store = store;
            return this;
        }

        /**
         * Sets an error handler for the connection
         *
         * @param errorHandler the error handler to use, can be null
         * @return the same instance for chaining
         */
        public Custom errorHandler(WhatsAppClientErrorHandler errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        /**
         * Sets the web verification handler for the connection
         *
         * @param webVerificationHandler the verification handler to use, can be null
         * @return the same instance for chaining
         */
        public Custom webVerificationSupport(WhatsAppClientVerificationHandler.Web webVerificationHandler) {
            this.webVerificationHandler = webVerificationHandler;
            return this;
        }

        /**
         * Sets a message preview handler for the connection
         *
         * @param messagePreviewHandler the handler to use, can be null
         * @return the same instance for chaining
         */
        public Custom messagePreviewHandler(WhatsAppClientMessagePreviewHandler messagePreviewHandler) {
            this.messagePreviewHandler = messagePreviewHandler;
            return this;
        }

        /**
         * Builds a WhatsApp instance with the configured parameters
         *
         * @return a non-null WhatsApp instance
         * @throws NullPointerException if store or keys are null
         * @throws IllegalArgumentException if there is a UUID mismatch between store and keys
         */
        public WhatsAppClient build() {
            var store = Objects.requireNonNull(this.store, "Expected a valid store");
            var webVerificationHandler = switch (store.clientType()) {
                case WEB -> Objects.requireNonNullElse(this.webVerificationHandler, DEFAULT_WEB_VERIFICATION_HANDLER);
                case MOBILE -> null;
            };
            var messagePreviewHandler = Objects.requireNonNullElse(this.messagePreviewHandler, DEFAULT_MESSAGE_PREVIEW_HANDLER);
            var errorHandler = Objects.requireNonNullElse(this.errorHandler, DEFAULT_ERROR_HANDLER);
            return new WhatsAppClient(store, webVerificationHandler, messagePreviewHandler, errorHandler);
        }
    }
}