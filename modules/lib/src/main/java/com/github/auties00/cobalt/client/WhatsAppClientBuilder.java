package com.github.auties00.cobalt.client;

import com.github.auties00.cobalt.proxy.WhatsAppProxy;
import com.github.auties00.cobalt.registration.MobileClientRegistration;
import com.github.auties00.cobalt.model.business.profile.BusinessCategory;
import com.github.auties00.cobalt.model.contact.ContactTextStatus;
import com.github.auties00.cobalt.model.contact.ContactTextStatusBuilder;
import com.github.auties00.cobalt.model.device.pairing.ClientAppVersion;
import com.github.auties00.cobalt.model.device.pairing.ClientPlatformType;
import com.github.auties00.cobalt.store.WhatsAppStore;
import com.github.auties00.cobalt.store.WhatsAppStoreFactory;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

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
     * Returns a web client builder backed by an in-memory store.
     *
     * <p>The resulting builder follows the web companion linking flow,
     * which authenticates against an existing primary device via QR code
     * or pairing code.
     *
     * @return the web client builder
     */
    public Client.Web webClient() {
        return new Client.Web(WhatsAppStoreFactory.temporary());
    }

    /**
     * Returns a web client builder backed by the given store factory.
     *
     * @param factory the factory to use for data persistence
     * @return the web client builder
     * @throws NullPointerException if {@code factory} is {@code null}
     */
    public Client.Web webClient(WhatsAppStoreFactory factory) {
        Objects.requireNonNull(factory, "factory must not be null");
        return new Client.Web(factory);
    }

    /**
     * Returns a mobile client builder backed by an in-memory store.
     *
     * <p>The resulting builder follows the mobile registration flow,
     * which registers a phone number directly with the WhatsApp servers.
     *
     * @return the mobile client builder
     */
    public Client.Mobile mobileClient() {
        return new Client.Mobile(WhatsAppStoreFactory.temporary());
    }

    /**
     * Returns a mobile client builder backed by the given store factory.
     *
     * @param factory the factory to use for data persistence
     * @return the mobile client builder
     * @throws NullPointerException if {@code factory} is {@code null}
     */
    public Client.Mobile mobileClient(WhatsAppStoreFactory factory) {
        Objects.requireNonNull(factory, "factory must not be null");
        return new Client.Mobile(factory);
    }

    /**
     * Returns a low-level builder that bypasses the
     * {@link WhatsAppStoreFactory} flow and accepts a pre-built
     * {@link WhatsAppStore} directly.
     *
     * @return the custom client builder
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
         * Creates a fresh connection identified by a random UUID.
         *
         * @return the next builder stage configured with a brand-new
         *         store
         * @throws IOException if the store cannot be created on disk
         */
        public abstract Options createConnection() throws IOException;

        /**
         * Creates a connection from a six-parts credentials representation.
         *
         * @param sixParts the credentials to load
         * @return the next builder stage
         * @throws NullPointerException if {@code sixParts} is {@code null}
         * @throws IOException if the store cannot be created on disk
         */
        public abstract Options createConnection(WhatsAppClientSixPartsKeys sixParts) throws IOException;

        /**
         * Loads the most recently serialised connection.
         *
         * @return the next builder stage if a previous connection exists,
         *         empty otherwise
         * @throws IOException if the store cannot be read from disk
         */
        public abstract Optional<Options> loadLatestConnection() throws IOException;

        /**
         * Loads the most recently serialised connection, or provisions a
         * fresh one if none exists yet.
         *
         * @return the next builder stage
         * @throws IOException if the store cannot be read from or written
         *                     to disk
         */
        public abstract Options loadLatestOrCreateConnection() throws IOException;

        /**
         * Loads the connection whose identifier matches {@code uuid}.
         *
         * @param uuid the identifier of the connection to load, or
         *             {@code null} to skip the lookup
         * @return the next builder stage if a matching store was found,
         *         empty otherwise
         * @throws IOException if the store cannot be read from disk
         */
        public abstract Optional<? extends Options> loadConnection(UUID uuid) throws IOException;

        /**
         * Loads the connection whose identifier matches {@code uuid}, or
         * provisions a fresh one if none exists yet.
         *
         * @param uuid the identifier of the connection to load, or
         *             {@code null} to provision under a fresh random UUID
         * @return the next builder stage
         * @throws IOException if the store cannot be read from or written
         *                     to disk
         */
        public abstract Options loadOrCreateConnection(UUID uuid) throws IOException;

        /**
         * Loads the connection whose phone number matches
         * {@code phoneNumber}.
         *
         * @param phoneNumber the phone number associated with the
         *                    connection, or {@code null} to skip the
         *                    lookup
         * @return the next builder stage if a matching store was found,
         *         empty otherwise
         * @throws IOException if the store cannot be read from disk
         */
        public abstract Optional<? extends Options> loadConnection(Long phoneNumber) throws IOException;

        /**
         * Loads the connection whose phone number matches
         * {@code phoneNumber}, or provisions a fresh one if none exists
         * yet.
         *
         * @param phoneNumber the phone number to load, or {@code null} to
         *                    provision under a fresh random UUID
         * @return the next builder stage
         * @throws IOException if the store cannot be read from or written
         *                     to disk
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
            public Options.Web createConnection(WhatsAppClientSixPartsKeys sixParts) throws IOException {
                Objects.requireNonNull(sixParts, "sixParts must not be null");
                var existingStore = factory.load(WhatsAppClientType.WEB, sixParts.phoneNumber());
                if(existingStore.isPresent()) {
                    return new Options.Web(existingStore.get());
                }

                var freshStore = factory.create(WhatsAppClientType.WEB, sixParts);
                return new Options.Web(freshStore);
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
            public Options.Mobile createConnection(WhatsAppClientSixPartsKeys sixParts) throws IOException {
                Objects.requireNonNull(sixParts, "sixParts must not be null");
                var existingStore = factory.load(WhatsAppClientType.WEB, sixParts.phoneNumber());
                if(existingStore.isPresent()) {
                    return new Options.Mobile(existingStore.get());
                }

                var freshStore = factory.create(WhatsAppClientType.WEB, sixParts);
                return new Options.Mobile(freshStore);
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
         * Sets the display name advertised by this session.
         *
         * <p>On mobile this is the preferred name that contacts who have
         * not saved the user yet see next to the phone number. On web
         * this is the companion-device name visible in the
         * "Linked Devices" tab.
         *
         * @param name the name to set, or {@code null} to clear it
         * @return this builder, for chaining
         */
        public Options name(String name) {
            store.setName(name);
            return this;
        }

        /**
         * Sets the proxy used by the connection.
         *
         * @param proxy the proxy, or {@code null} to use no proxy
         * @return this builder, for chaining
         */
        public Options proxy(WhatsAppProxy proxy) {
            store.setProxy(proxy);
            return this;
        }

        /**
         * Sets the device descriptor advertised by the connection.
         *
         * @param device the device, or {@code null} to clear it
         * @return this builder, for chaining
         */
        public Options device(WhatsAppDevice device) {
            store.setDevice(device);
            return this;
        }

        /**
         * Controls whether the library sends read receipts automatically
         * for incoming messages.
         *
         * <p>Disabled by default. For the web API, enabling this option
         * suppresses notifications on the companion device because the
         * server only delivers notifications for unread messages.
         *
         * @param automaticMessageReceipts {@code true} to enable automatic
         *                                 receipts, {@code false}
         *                                 otherwise
         * @return this builder, for chaining
         */
        public Options automaticMessageReceipts(boolean automaticMessageReceipts) {
            store.setAutomaticMessageReceipts(automaticMessageReceipts);
            return this;
        }

        /**
         * Sets the WhatsApp client version advertised by the connection.
         *
         * @param clientVersion the client version, or {@code null} to
         *                      keep the default
         * @return this builder, for chaining
         */
        public Options clientVersion(ClientAppVersion clientVersion) {
            store.setClientVersion(clientVersion);
            return this;
        }

        /**
         * Sets the error handler that decides how the future client
         * reacts to failures.
         *
         * @param errorHandler the error handler, or {@code null} to use
         *                     the default terminal-printing handler
         * @return this builder, for chaining
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
             * {@inheritDoc}
             */
            @Override
            public Mobile name(String name) {
                return (Mobile) super.name(name);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Web proxy(WhatsAppProxy proxy) {
                return (Web) super.proxy(proxy);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Web device(WhatsAppDevice device) {
                return (Web) super.device(device);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Web errorHandler(WhatsAppClientErrorHandler errorHandler) {
                return (Web) super.errorHandler(errorHandler);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Web automaticMessageReceipts(boolean automaticMessageReceipts) {
                return (Web) super.automaticMessageReceipts(automaticMessageReceipts);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Web clientVersion(ClientAppVersion clientVersion) {
                return (Web) super.clientVersion(clientVersion);
            }

            /**
             * Sets the chat-history policy applied during the initial
             * history sync that runs after companion linking completes.
             *
             * <p>The default is one year of history. Use
             * {@link WhatsAppWebClientHistory#discard(boolean)},
             * {@link WhatsAppWebClientHistory#standard(boolean)}, or
             * {@link WhatsAppWebClientHistory#extended(boolean)} for
             * common presets.
             *
             * @param historyLength the history policy
             * @return this builder, for chaining
             * @throws NullPointerException if {@code historyLength} is
             *                              {@code null}
             */
            public Web historySetting(WhatsAppWebClientHistory historyLength) {
                Objects.requireNonNull(historyLength, "historyLength must not be null");
                store.setWebHistoryPolicy(historyLength);
                return this;
            }

            /**
             * Builds a web client whose linking ceremony surfaces a QR
             * code through the supplied handler.
             *
             * @param qrHandler the QR code handler
             * @return the configured client
             * @throws NullPointerException if {@code qrHandler} is
             *                              {@code null}
             */
            public WhatsAppClient unregistered(WhatsAppClientVerificationHandler.Web.QrCode qrHandler) {
                Objects.requireNonNull(qrHandler, "qrHandler must not be null");
                var errorHandler = Objects.requireNonNullElse(this.errorHandler, DEFAULT_ERROR_HANDLER);
                return new WhatsAppClient(store, qrHandler, errorHandler);
            }

            /**
             * Builds a web client whose linking ceremony surfaces a
             * pairing code through the supplied handler.
             *
             * @param phoneNumber        the phone number of the primary
             *                           account being linked
             * @param pairingCodeHandler the pairing-code handler
             * @return the configured client
             * @throws NullPointerException if {@code pairingCodeHandler}
             *                              is {@code null}
             */
            public WhatsAppClient unregistered(long phoneNumber, WhatsAppClientVerificationHandler.Web.PairingCode pairingCodeHandler) {
                Objects.requireNonNull(pairingCodeHandler, "pairingCodeHandler must not be null");
                store.setPhoneNumber(phoneNumber);
                var errorHandler = Objects.requireNonNullElse(this.errorHandler, DEFAULT_ERROR_HANDLER);
                return new WhatsAppClient(store, pairingCodeHandler, errorHandler);
            }

            /**
             * Builds a web client for a session that has already been
             * registered, reusing the persisted credentials.
             *
             * @return the configured client if the underlying store is
             *         registered, empty otherwise
             */
            public Optional<WhatsAppClient> registered() {
                if (!store.registered()) {
                    return Optional.empty();
                }

                var errorHandler = Objects.requireNonNullElse(this.errorHandler, DEFAULT_ERROR_HANDLER);
                var result = new WhatsAppClient(store, null, errorHandler);
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
             * the concrete subclass's {@code EMPTY_ATTESTOR} default.
             */
            private WhatsAppDeviceAttestor attestor;

            /**
             * Push client captured by
             * {@link #devicePushClient(WhatsAppDevicePushClient)} —
             * the caller-owned variant. The builder treats this
             * instance as borrowed and never closes it. Mutually
             * exclusive with {@link #pushClientSupplier}: setting
             * either overload clears the other.
             *
             * <p>{@code null} (together with a {@code null}
             * {@link #pushClientSupplier}) means "no push client
             * configured" and the registration falls back to
             * {@link WhatsAppDevicePushClient#noop()}, which emits
             * empty {@code push_token} and {@code push_code} form
             * fields.
             */
            private WhatsAppDevicePushClient pushClient;

            /**
             * Push client supplier captured by
             * {@link #devicePushClient(Supplier)} — the
             * builder-owned variant. The supplier is invoked exactly
             * once at registration time and the produced instance is
             * closed via {@link WhatsAppDevicePushClient#close()}
             * after the registration ceremony finishes (success or
             * failure). Mutually exclusive with {@link #pushClient}:
             * setting either overload clears the other.
             */
            private Supplier<WhatsAppDevicePushClient> pushClientSupplier;

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
             * {@inheritDoc}
             */
            @Override
            public Mobile proxy(WhatsAppProxy proxy) {
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
             * mismatch. If a push client has already been attached via
             * {@link #devicePushClient}, the new device's platform is
             * also validated against the client's
             * {@link WhatsAppDevicePushClient#supportedPlatforms()} set and the
             * call raises {@link IllegalArgumentException} on a
             * mismatch. The builder also flips an internal flag marking
             * the device as explicitly chosen so that a subsequent
             * {@code deviceAttestor} or {@code devicePushClient} call
             * can itself perform the symmetric check.
             *
             * @param device the companion device, can be null
             * @return the same instance for chaining
             * @throws IllegalArgumentException if an attestor or push
             *                                  client is already set
             *                                  and its platform does
             *                                  not match {@code device}
             */
            @Override
            public Mobile device(WhatsAppDevice device) {
                if (device != null) {
                    requirePlatformMatches(device, attestor);
                    requirePushClientSupportsDevice(device, pushClient);
                }
                store.setDevice(device);
                this.deviceExplicitlySet = true;
                return this;
            }

            /**
             * Sets the device attestor that produces the Play Integrity
             * or App Attest payloads (and, on Android, the TEE-backed
             * body signature and the install source) embedded into the
             * upcoming registration requests.
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
             * registration subclass's private {@code EMPTY_ATTESTOR}
             * default).
             *
             * @param attestor the device attestor, or {@code null} to clear
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
                this.attestor = attestor;
                return this;
            }

            /**
             * Sets the push client that produces the {@code push_token}
             * advertised on every attested endpoint and the
             * {@code push_code} echoed back on {@code /v2/code} when a
             * push-based verification method is in flight.
             *
             * <p>The {@code pushClient} instance stays caller-owned:
             * the builder treats it as a borrowed reference and
             * never invokes {@link WhatsAppDevicePushClient#close()}
             * on it. If you want the builder to own the lifecycle
             * (for example because the client opens a long-lived TLS
             * connection that should be released as soon as the
             * registration ceremony finishes), use the
             * {@link #devicePushClient(Supplier) supplier-based
             * overload} instead.
             *
             * <p>If {@link #device(WhatsAppDevice)} has already been
             * called explicitly, the push client's
             * {@link WhatsAppDevicePushClient#supportedPlatforms()}
             * set is validated against the stored device's platform
             * and the call raises {@link IllegalArgumentException} on
             * a mismatch. When the device is still the factory
             * default the check is deferred to the terminal methods
             * ({@link #register} and {@link #registered()}), which
             * catches the case of a push client set against the
             * defaulted device.
             *
             * <p>Passing {@code null} clears the push client and
             * brings the registration back to the low-trust lane
             * ({@link WhatsAppDevicePushClient#noop()}, which emits
             * empty {@code push_token} and {@code push_code} fields).
             * Calling this overload also clears any supplier
             * previously installed via
             * {@link #devicePushClient(Supplier)}.
             *
             * @param pushClient the push client, or {@code null} to clear
             * @return the same instance for chaining
             * @throws IllegalArgumentException if {@code device(...)}
             *                                  was called explicitly
             *                                  and the stored device's
             *                                  platform is not in
             *                                  {@code pushClient.supportedPlatforms()}
             */
            public Mobile devicePushClient(WhatsAppDevicePushClient pushClient) {
                if (deviceExplicitlySet && pushClient != null) {
                    requirePushClientSupportsDevice(store.device(), pushClient);
                }
                this.pushClient = pushClient;
                this.pushClientSupplier = null;
                return this;
            }

            /**
             * Sets a supplier of the push client that produces the
             * {@code push_token} and {@code push_code} form fields.
             *
             * <p>The supplier is invoked exactly once at registration
             * time, and the produced
             * {@link WhatsAppDevicePushClient} instance is owned by
             * the builder: it is closed via
             * {@link WhatsAppDevicePushClient#close()} after the
             * registration ceremony completes (success or failure).
             * Use this overload for clients that open vendor-side
             * resources (an FCM MCS stream, an APNS courier
             * connection) so they are torn down promptly. For
             * caller-managed clients, prefer
             * {@link #devicePushClient(WhatsAppDevicePushClient)}.
             *
             * <p>Because the supplier may carry side effects (opening
             * a network connection, generating a fresh device
             * identifier, etc.) it is not invoked here, which means
             * no immediate {@link WhatsAppDevicePushClient#supportedPlatforms()}
             * validation is possible. The terminal {@link #register}
             * resolves the supplier and validates the produced
             * client's supported platforms before driving the
             * registration; mismatches surface as
             * {@link IllegalArgumentException} at that point.
             *
             * <p>Passing {@code null} clears any supplier previously
             * installed and brings the registration back to the
             * low-trust lane ({@link WhatsAppDevicePushClient#noop()}).
             * Calling this overload also clears any caller-owned
             * client previously installed via
             * {@link #devicePushClient(WhatsAppDevicePushClient)}.
             *
             * @param pushClientSupplier the supplier, or {@code null}
             *                           to clear
             * @return the same instance for chaining
             */
            public Mobile devicePushClient(Supplier<WhatsAppDevicePushClient> pushClientSupplier) {
                this.pushClientSupplier = pushClientSupplier;
                this.pushClient = null;
                return this;
            }

            /**
             * Cross-checks the currently stored device against the
             * currently stored attestor and raises
             * {@link IllegalArgumentException} if they do not agree.
             *
             * <p>Called by the terminal {@link #register} and
             * {@link #registered()} methods so that a caller that set
             * an attestor without also picking a matching device (thus
             * relying on the factory default) still gets a clear error
             * at the point the mismatch matters.
             *
             * @throws IllegalArgumentException if the stored attestor's
             *                                  platform does not match
             *                                  the stored device's
             *                                  platform
             */
            private void validateAttestorMatchesDevice() {
                if (attestor != null) {
                    requirePlatformMatches(store.device(), attestor);
                }
            }

            /**
             * Cross-checks the currently stored device against the
             * eager push client installed via
             * {@link #devicePushClient(WhatsAppDevicePushClient)} and
             * raises {@link IllegalArgumentException} if the device's
             * platform is not in
             * {@link WhatsAppDevicePushClient#supportedPlatforms()}.
             *
             * <p>Called by the terminal {@link #register} and
             * {@link #registered()} methods so that a caller that set
             * a push client without also picking a matching device
             * (thus relying on the factory default) still gets a clear
             * error at the point the mismatch matters.
             *
             * <p>The supplier-based variant installed via
             * {@link #devicePushClient(Supplier)} is not handled here:
             * its platform set is unknown until the supplier is
             * resolved, so {@link #register} performs the equivalent
             * check after invoking the supplier.
             *
             * @throws IllegalArgumentException if the stored push
             *                                  client does not list
             *                                  the stored device's
             *                                  platform in its
             *                                  supported set
             */
            private void validatePushClientMatchesDevice() {
                if (pushClient != null) {
                    requirePushClientSupportsDevice(store.device(), pushClient);
                }
            }

            /**
             * Validates that the sub-interface of {@code attestor}
             * matches the platform carried by {@code device}.
             *
             * <p>A {@code null} attestor is always accepted, because
             * registration will fall back to the concrete subclass's
             * {@code EMPTY_ATTESTOR} default.
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
             * Validates that {@code device}'s platform appears in
             * {@code pushClient.supportedPlatforms()}.
             *
             * <p>A {@code null} push client is always accepted, because
             * registration will fall back to
             * {@link WhatsAppDevicePushClient#noop()}, which accepts every
             * platform.
             *
             * @param device the device whose platform the push client
             *               must support; never {@code null}
             * @param pushClient the push client to validate, or
             *                   {@code null} to skip the check
             * @throws IllegalArgumentException if the push client does
             *                                  not list the device's
             *                                  platform in its
             *                                  supported set
             */
            private static void requirePushClientSupportsDevice(WhatsAppDevice device, WhatsAppDevicePushClient pushClient) {
                if (pushClient == null) {
                    return;
                }
                var platform = device.platform();
                var supported = pushClient.supportedPlatforms();
                if (!supported.contains(platform)) {
                    throw new IllegalArgumentException(
                            "Push client does not support device platform: " + platform
                                    + " (supported: " + supported + ")");
                }
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Mobile errorHandler(WhatsAppClientErrorHandler errorHandler) {
                super.errorHandler(errorHandler);
                return this;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Mobile automaticMessageReceipts(boolean automaticMessageReceipts) {
                super.automaticMessageReceipts(automaticMessageReceipts);
                return this;
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Mobile clientVersion(ClientAppVersion clientVersion) {
                return (Mobile) super.clientVersion(clientVersion);
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public Mobile name(String name) {
                return (Mobile) super.name(name);
            }

            /**
             * Sets the text status (the "about" line) attached to the
             * account.
             *
             * @param selfTextStatus the text status, or {@code null} to clear
             *                       it
             * @return this builder, for chaining
             */
            public Mobile selfTextStatus(ContactTextStatus selfTextStatus) {
                store.setSelfTextStatus(selfTextStatus);
                return this;
            }

            /**
             * Convenience overload that sets the text status to a plain
             * string with no emoji or ephemeral expiration.
             *
             * @param aboutText the about text, or {@code null} to clear it
             * @return this builder, for chaining
             */
            public Mobile selfTextStatus(String aboutText) {
                var status = aboutText == null ? null : new ContactTextStatusBuilder()
                        .text(aboutText)
                        .build();
                return selfTextStatus(status);
            }

            /**
             * Sets the business address advertised on the account's
             * business profile.
             *
             * @param businessAddress the address, or {@code null} to
             *                        clear it
             * @return this builder, for chaining
             */
            public Mobile businessAddress(String businessAddress) {
                store.setBusinessAddress(businessAddress);
                return this;
            }

            /**
             * Sets the longitude component of the business address
             * geolocation.
             *
             * @param businessLongitude the longitude, or {@code null} to
             *                          clear it
             * @return this builder, for chaining
             */
            public Mobile businessLongitude(Double businessLongitude) {
                store.setBusinessLongitude(businessLongitude);
                return this;
            }

            /**
             * Sets the latitude component of the business address
             * geolocation.
             *
             * @param businessLatitude the latitude, or {@code null} to
             *                         clear it
             * @return this builder, for chaining
             */
            public Mobile businessLatitude(Double businessLatitude) {
                store.setBusinessLatitude(businessLatitude);
                return this;
            }

            /**
             * Sets the business description shown on the account's
             * business profile.
             *
             * @param businessDescription the description, or {@code null}
             *                            to clear it
             * @return this builder, for chaining
             */
            public Mobile businessDescription(String businessDescription) {
                store.setBusinessDescription(businessDescription);
                return this;
            }

            /**
             * Sets the business website URL.
             *
             * @param businessWebsite the website URL, or {@code null} to
             *                        clear it
             * @return this builder, for chaining
             */
            public Mobile businessWebsite(String businessWebsite) {
                store.setBusinessWebsite(businessWebsite);
                return this;
            }

            /**
             * Sets the business contact email address.
             *
             * @param businessEmail the email address, or {@code null} to
             *                      clear it
             * @return this builder, for chaining
             */
            public Mobile businessEmail(String businessEmail) {
                store.setBusinessEmail(businessEmail);
                return this;
            }

            /**
             * Sets the business category advertised on the business
             * profile.
             *
             * @param businessCategory the category, or {@code null} to
             *                         clear it
             * @return this builder, for chaining
             */
            public Mobile businessCategory(BusinessCategory businessCategory) {
                store.setBusinessCategory(businessCategory);
                return this;
            }

            /**
             * Builds a mobile client for a session that has already been
             * registered, reusing the persisted credentials.
             *
             * @return the configured client if the underlying store is
             *         registered, empty otherwise
             * @throws IllegalArgumentException if a previously attached
             *                                  attestor or push client
             *                                  does not match the
             *                                  configured device
             *                                  platform
             */
            public Optional<WhatsAppClient> registered() {
                validateAttestorMatchesDevice();
                validatePushClientMatchesDevice();
                if (!store.registered()) {
                    return Optional.empty();
                }

                var errorHandler = Objects.requireNonNullElse(this.errorHandler, DEFAULT_ERROR_HANDLER);
                var result = new WhatsAppClient(store, null, errorHandler);
                return Optional.of(result);
            }

            /**
             * Builds a mobile client and runs the registration ceremony
             * for a session that has not yet been registered.
             *
             * @param phoneNumber  the phone number being registered
             * @param verification the verification handler used to drive
             *                     the OTP exchange
             * @return the configured client
             * @throws NullPointerException     if {@code verification} is
             *                                  {@code null}
             * @throws IllegalArgumentException if the store already
             *                                  carries a different phone
             *                                  number, or if a previously
             *                                  attached attestor or push
             *                                  client does not match the
             *                                  configured device platform
             */
            public WhatsAppClient register(long phoneNumber, WhatsAppClientVerificationHandler.Mobile verification) {
                Objects.requireNonNull(verification, "verification must not be null");
                validateAttestorMatchesDevice();
                validatePushClientMatchesDevice();

                var oldPhoneNumber = store.phoneNumber();
                if(oldPhoneNumber.isPresent() && oldPhoneNumber.getAsLong() != phoneNumber) {
                    throw new IllegalArgumentException("The phone number(" + phoneNumber + ") must match the existing phone number(" + oldPhoneNumber.getAsLong() + ")");
                }else {
                    store.setPhoneNumber(phoneNumber);
                }

                if (!store.registered()) {
                    if (pushClientSupplier != null) {
                        try (var ownedPushClient = pushClientSupplier.get()) {
                            requirePushClientSupportsDevice(store.device(), ownedPushClient);
                            try (var registration = MobileClientRegistration.newRegistration(store, verification, attestor, ownedPushClient)) {
                                registration.register();
                            }
                        }
                    } else {
                        try (var registration = MobileClientRegistration.newRegistration(store, verification, attestor, pushClient)) {
                            registration.register();
                        }
                    }
                }

                var errorHandler = Objects.requireNonNullElse(this.errorHandler, DEFAULT_ERROR_HANDLER);
                return new WhatsAppClient(store, null, errorHandler);
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
         * Sets the externally-supplied store backing the client.
         *
         * @param store the store, or {@code null} to leave it unset
         *              (which fails fast at {@link #build()})
         * @return this builder, for chaining
         */
        public Custom store(WhatsAppStore store) {
            this.store = store;
            return this;
        }

        /**
         * Sets the error handler that decides how the future client
         * reacts to failures.
         *
         * @param errorHandler the error handler, or {@code null} to use
         *                     the default terminal-printing handler
         * @return this builder, for chaining
         */
        public Custom errorHandler(WhatsAppClientErrorHandler errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        /**
         * Sets the web verification handler used when the supplied store
         * is configured for {@link WhatsAppClientType#WEB}.
         *
         * @param webVerificationHandler the verification handler, or
         *                               {@code null} to use the default
         *                               terminal-rendering handler
         * @return this builder, for chaining
         */
        public Custom webVerificationSupport(WhatsAppClientVerificationHandler.Web webVerificationHandler) {
            this.webVerificationHandler = webVerificationHandler;
            return this;
        }

        /**
         * Builds the configured client.
         *
         * @return the configured client
         * @throws NullPointerException if no store has been supplied
         */
        public WhatsAppClient build() {
            var store = Objects.requireNonNull(this.store, "Expected a valid store");
            var webVerificationHandler = switch (store.clientType()) {
                case WEB -> Objects.requireNonNullElse(this.webVerificationHandler, DEFAULT_WEB_VERIFICATION_HANDLER);
                case MOBILE -> null;
            };
            var errorHandler = Objects.requireNonNullElse(this.errorHandler, DEFAULT_ERROR_HANDLER);
            return new WhatsAppClient(store, webVerificationHandler, errorHandler);
        }
    }
}