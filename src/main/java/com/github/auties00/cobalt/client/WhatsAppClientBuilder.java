package com.github.auties00.cobalt.client;

import com.github.auties00.cobalt.client.registration.WhatsAppMobileClientRegistration;
import com.github.auties00.cobalt.model.auth.Version;
import com.github.auties00.cobalt.model.business.profile.BusinessCategory;
import com.github.auties00.cobalt.model.jid.JidDevice;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A builder for WhatsApp API client instances with support for both web-based and mobile-based connections.
 * <p>
 * This class implements a fluent builder pattern with specialized inner classes that handle:
 * <ul>
 *   <li>Web client connections (through QR codes or pairing codes)</li>
 *   <li>Mobile client connections (through phone numbers and verification)</li>
 *   <li>Custom client configurations for advanced use cases</li>
 * </ul>
 * <p>
 * The builder provides a clean, type-safe API for creating and configuring WhatsApp client instances
 * with appropriate connection, serialization, and authentication options.
 */

public sealed class WhatsAppClientBuilder {
    private static final WhatsAppClientMessagePreviewHandler DEFAULT_MESSAGE_PREVIEW_HANDLER = WhatsAppClientMessagePreviewHandler.enabled(true);
    private static final WhatsAppClientErrorHandler DEFAULT_ERROR_HANDLER = WhatsAppClientErrorHandler.toTerminal();
    private static final WhatsAppClientVerificationHandler.Web DEFAULT_WEB_VERIFICATION_HANDLER = WhatsAppClientVerificationHandler.Web.QrCode.toTerminal();

    static final WhatsAppClientBuilder INSTANCE = new WhatsAppClientBuilder();

    private WhatsAppClientBuilder() {

    }

    /**
     * Creates a web client with the default storage directory.
     *
     * @return a non-null web client instance
     */
    public Client.Web webClient() {
        return new Client.Web(null);
    }

    /**
     * Creates a web client with a custom storage directory.
     *
     * @param directory the directory for session persistence, must not be null
     * @return a non-null web client instance
     * @throws NullPointerException if directory is null
     */
    public Client.Web webClient(Path directory) {
        Objects.requireNonNull(directory, "directory must not be null");
        return new Client.Web(directory);
    }

    /**
     * Creates a mobile client with the default storage directory.
     *
     * @return a non-null mobile client instance
     */
    public Client.Mobile mobileClient() {
        return new Client.Mobile(null);
    }

    /**
     * Creates a mobile client with a custom storage directory.
     *
     * @param directory the directory for session persistence, must not be null
     * @return a non-null mobile client instance
     * @throws NullPointerException if directory is null
     */
    public Client.Mobile mobileClient(Path directory) {
        Objects.requireNonNull(directory, "directory must not be null");
        return new Client.Mobile(directory);
    }

    /**
     * Creates a custom client for advanced configuration
     *
     * @return a non-null custom client instance
     */
    public Custom customClient() {
        return new Custom();
    }

    public static abstract sealed class Client extends WhatsAppClientBuilder {
        final Path directory;

        private Client(Path directory) {
            this.directory = directory;
        }

        /**
         * Creates a new connection using a random UUID
         *
         * @return a non-null options selector
         */
        public abstract Options createConnection();

        /**
         * Loads a connection from the six parts key representation
         *
         * @param sixParts the six parts keys to use to create the connection, must not be null
         * @return a non-null options selector
         * @throws NullPointerException if sixParts is null
         */
        public abstract Options loadConnection(WhatsAppClientSixPartsKeys sixParts);

        /**
         * Loads the last serialized connection.
         * If no connection is available, an empty {@link Optional} will be returned.
         *
         * @return an {@link Optional} containing the last serialized connection, empty otherwise
         */
        public abstract Optional<Options> loadLastConnection();

        /**
         * Loads the last serialized connection.
         * If no connection is available, a new one will be created.
         *
         * @return a non-null options selector
         */
        public abstract Options loadLastOrCreateConnection();

        /**
         * Loads the connection whose id matches {@code uuid}.
         * If {@code uuid} is null, or if no connection has an id that matches {@code uuid}, an empty {@link Optional} will be returned.
         *
         * @param uuid the id to use for the connection; can be null
         * @return an {@link Optional} containing the connection whose id matches {@code uuid}, empty otherwise
         */
        public abstract Optional<Options> loadConnection(UUID uuid);

        /**
         * Loads the connection whose id matches {@code uuid}.
         * If {@code uuid} is null, or if no connection has an id that matches {@code uuid}, a new connection will be created.
         *
         * @param uuid the id to use for the connection; can be null
         * @return a non-null options selector
         */
        public abstract Options loadOrCreateConnection(UUID uuid);

        /**
         * Loads the connection whose phone number matches the given phone number.
         * If the phone number is null, or if no connection matches the given phone number, an empty {@link Optional} will be returned.
         *
         * @param phoneNumber the phone value to use to create the connection, can be null
         * @return an {@link Optional} containing the connection, empty otherwise
         */
        public abstract Optional<Options> loadConnection(Long phoneNumber);

        /**
         * Loads the connection whose id matches {@code phoneNumber}.
         * If {@code phoneNumber} is null, or if no connection matches {@code phoneNumber}, a new connection will be created.
         *
         * @param phoneNumber the id to use for the connection, can be null
         * @return a non-null options selector
         */
        public abstract Options loadOrCreateConnection(Long phoneNumber);

        abstract WhatsAppClientType clientType();

        public static final class Web extends Client {
            private Web(Path directory) {
                super(directory);
            }

            @Override
            WhatsAppClientType clientType() {
                return WhatsAppClientType.WEB;
            }

            @Override
            public Options.Web createConnection() {
                return new Options.Web(WhatsAppStore.createInMemory(WhatsAppClientType.WEB, directory));
            }

            @Override
            public Options.Web loadLastOrCreateConnection() {
                var uuids = WhatsAppStore.listIds(WhatsAppClientType.WEB, directory);
                if(uuids.isEmpty()) {
                    return createConnection();
                }else {
                    return loadOrCreateConnection(uuids.getLast());
                }
            }

            @Override
            public Optional<Options> loadConnection(UUID uuid) {
                if (uuid == null) {
                    return Optional.empty();
                }
                return WhatsAppStore.loadOrCreateInMemory(WhatsAppClientType.WEB, uuid, directory)
                        .map(Options.Web::new);
            }

            @Override
            public Options.Web loadOrCreateConnection(UUID uuid) {
                var sessionUuid = Objects.requireNonNullElseGet(uuid, UUID::randomUUID);
                var store = WhatsAppStore.loadOrCreateInMemory(WhatsAppClientType.WEB, sessionUuid, directory)
                        .orElseGet(() -> WhatsAppStore.createInMemory(WhatsAppClientType.WEB, directory));
                return new Options.Web(store);
            }

            @Override
            public Optional<Options> loadConnection(Long phoneNumber) {
                if (phoneNumber == null) {
                    return Optional.empty();
                }
                return WhatsAppStore.loadOrCreateInMemory(WhatsAppClientType.WEB, phoneNumber, directory)
                        .map(Options.Web::new);
            }

            @Override
            public Options.Web loadOrCreateConnection(Long phoneNumber) {
                if (phoneNumber != null) {
                    var loaded = WhatsAppStore.loadOrCreateInMemory(WhatsAppClientType.WEB, phoneNumber, directory);
                    if (loaded.isPresent()) {
                        return new Options.Web(loaded.get());
                    }
                }
                return new Options.Web(WhatsAppStore.createInMemory(WhatsAppClientType.WEB, directory));
            }

            @Override
            public Options.Web loadConnection(WhatsAppClientSixPartsKeys sixParts) {
                Objects.requireNonNull(sixParts, "sixParts must not be null");
                var loaded = WhatsAppStore.loadOrCreateInMemory(WhatsAppClientType.WEB, sixParts.phoneNumber(), directory);
                if(loaded.isPresent()) {
                    return new Options.Web(loaded.get());
                }
                return new Options.Web(WhatsAppStore.loadOrCreateInMemory(WhatsAppClientType.WEB, sixParts, directory));
            }

            @Override
            public Optional<Options> loadLastConnection() {
                var uuids = WhatsAppStore.listIds(WhatsAppClientType.WEB, directory);
                if(uuids.isEmpty()) {
                    return Optional.empty();
                }else {
                    return loadConnection(uuids.getLast());
                }
            }
        }

        public static final class Mobile extends Client {
            private Mobile(Path directory) {
                super(directory);
            }

            @Override
            WhatsAppClientType clientType() {
                return WhatsAppClientType.MOBILE;
            }

            @Override
            public Options.Mobile createConnection() {
                return new Options.Mobile(WhatsAppStore.createInMemory(WhatsAppClientType.MOBILE, directory));
            }

            @Override
            public Options.Mobile loadLastOrCreateConnection() {
                var uuids = WhatsAppStore.listIds(WhatsAppClientType.MOBILE, directory);
                if(uuids.isEmpty()) {
                    return createConnection();
                }else {
                    return loadOrCreateConnection(uuids.getLast());
                }
            }

            @Override
            public Optional<Options> loadConnection(UUID uuid) {
                if(uuid == null) {
                    return Optional.empty();
                }
                return WhatsAppStore.loadOrCreateInMemory(WhatsAppClientType.MOBILE, uuid, directory)
                        .map(Options.Mobile::new);
            }

            @Override
            public Options.Mobile loadOrCreateConnection(UUID uuid) {
                var sessionUuid = Objects.requireNonNullElseGet(uuid, UUID::randomUUID);
                var store = WhatsAppStore.loadOrCreateInMemory(WhatsAppClientType.MOBILE, sessionUuid, directory)
                        .orElseGet(() -> WhatsAppStore.createInMemory(WhatsAppClientType.MOBILE, directory));
                return new Options.Mobile(store);
            }

            @Override
            public Optional<Options> loadConnection(Long phoneNumber) {
                if(phoneNumber == null) {
                    return Optional.empty();
                }
                return WhatsAppStore.loadOrCreateInMemory(WhatsAppClientType.MOBILE, phoneNumber, directory)
                        .map(Options.Mobile::new);
            }

            @Override
            public Options.Mobile loadOrCreateConnection(Long phoneNumber) {
                if (phoneNumber != null) {
                    var loaded = WhatsAppStore.loadOrCreateInMemory(WhatsAppClientType.MOBILE, phoneNumber, directory);
                    if (loaded.isPresent()) {
                        return new Options.Mobile(loaded.get());
                    }
                }
                return new Options.Mobile(WhatsAppStore.createInMemory(WhatsAppClientType.MOBILE, directory));
            }

            @Override
            public Options.Mobile loadConnection(WhatsAppClientSixPartsKeys sixParts) {
                Objects.requireNonNull(sixParts, "sixParts must not be null");
                var loaded = WhatsAppStore.loadOrCreateInMemory(WhatsAppClientType.MOBILE, sixParts.phoneNumber(), directory);
                if(loaded.isPresent()) {
                    return new Options.Mobile(loaded.get());
                }
                return new Options.Mobile(WhatsAppStore.loadOrCreateInMemory(WhatsAppClientType.MOBILE, sixParts, directory));
            }

            @Override
            public Optional<Options> loadLastConnection() {
                var uuids = WhatsAppStore.listIds(WhatsAppClientType.MOBILE, directory);
                if(uuids.isEmpty()) {
                    return Optional.empty();
                }else {
                    return loadConnection(uuids.getLast());
                }
            }
        }
    }

    public static sealed class Options extends WhatsAppClientBuilder {
        final WhatsAppStore store;
        WhatsAppClientMessagePreviewHandler messagePreviewHandler;
        WhatsAppClientErrorHandler errorHandler;

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
        public Options device(JidDevice device) {
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
        public Options clientVersion(Version clientVersion) {
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

        public static final class Web extends Options {
            private Web(WhatsAppStore store) {
                super(store);
            }

            @Override
            public Mobile name(String name) {
                return (Mobile) super.name(name);
            }

            @Override
            public Web proxy(WhatsAppClientProxy proxy) {
                return (Web) super.proxy(proxy);
            }

            @Override
            public Web device(JidDevice device) {
                return (Web) super.device(device);
            }

            @Override
            public Web messagePreviewHandler(WhatsAppClientMessagePreviewHandler messagePreviewHandler) {
                return (Web) super.messagePreviewHandler(messagePreviewHandler);
            }

            @Override
            public Web errorHandler(WhatsAppClientErrorHandler errorHandler) {
                return (Web) super.errorHandler(errorHandler);
            }

            @Override
            public Web automaticMessageReceipts(boolean automaticMessageReceipts) {
                return (Web) super.automaticMessageReceipts(automaticMessageReceipts);
            }

            @Override
            public Web clientVersion(Version clientVersion) {
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

        public static final class Mobile extends Options {
            private Mobile(WhatsAppStore store) {
                super(store);
            }

            @Override
            public Mobile proxy(WhatsAppClientProxy proxy) {
                store.setProxy(proxy);
                return this;
            }

            @Override
            public Mobile device(JidDevice device) {
                store.setDevice(device);
                return this;
            }

            @Override
            public Mobile errorHandler(WhatsAppClientErrorHandler errorHandler) {
                super.errorHandler(errorHandler);
                return this;
            }

            @Override
            public Mobile automaticMessageReceipts(boolean automaticMessageReceipts) {
                super.automaticMessageReceipts(automaticMessageReceipts);
                return this;
            }

            @Override
            public Mobile clientVersion(Version clientVersion) {
                return (Mobile) super.clientVersion(clientVersion);
            }

            @Override
            public Mobile name(String name) {
                return (Mobile) super.name(name);
            }

            public Mobile about(String about) {
                store.setAbout(about);
                return this;
            }

            public Mobile businessAddress(String businessAddress) {
                store.setBusinessAddress(businessAddress);
                return this;
            }

            public Mobile businessLongitude(Double businessLongitude) {
                store.setBusinessLongitude(businessLongitude);
                return this;
            }

            public Mobile businessLatitude(Double businessLatitude) {
                store.setBusinessLatitude(businessLatitude);
                return this;
            }

            public Mobile businessDescription(String businessDescription) {
                store.setBusinessDescription(businessDescription);
                return this;
            }

            public Mobile businessWebsite(String businessWebsite) {
                store.setBusinessWebsite(businessWebsite);
                return this;
            }

            public Mobile businessEmail(String businessEmail) {
                store.setBusinessEmail(businessEmail);
                return this;
            }

            public Mobile businessCategory(BusinessCategory businessCategory) {
                store.setBusinessCategory(businessCategory);
                return this;
            }

            public Optional<WhatsAppClient> registered() {
                if (!store.registered()) {
                    return Optional.empty();
                }

                var messagePreviewHandler = Objects.requireNonNullElse(this.messagePreviewHandler, DEFAULT_MESSAGE_PREVIEW_HANDLER);
                var errorHandler = Objects.requireNonNullElse(this.errorHandler, DEFAULT_ERROR_HANDLER);
                var result = new WhatsAppClient(store, null, messagePreviewHandler, errorHandler);
                return Optional.of(result);
            }

            public WhatsAppClient register(long phoneNumber, WhatsAppClientVerificationHandler.Mobile verification) {
                Objects.requireNonNull(verification, "verification must not be null");

                var oldPhoneNumber = store.phoneNumber();
                if(oldPhoneNumber.isPresent() && oldPhoneNumber.getAsLong() != phoneNumber) {
                    throw new IllegalArgumentException("The phone number(" + phoneNumber + ") must match the existing phone number(" + oldPhoneNumber.getAsLong() + ")");
                }else {
                    store.setPhoneNumber(phoneNumber);
                }

                if (!store.registered()) {
                    try(var registration = WhatsAppMobileClientRegistration.of(store, verification)) {
                        registration.register();
                    }
                }

                var messagePreviewHandler = Objects.requireNonNullElse(this.messagePreviewHandler, DEFAULT_MESSAGE_PREVIEW_HANDLER);
                var errorHandler = Objects.requireNonNullElse(this.errorHandler, DEFAULT_ERROR_HANDLER);
                return new WhatsAppClient(store, null, messagePreviewHandler, errorHandler);
            }
        }
    }

    public static final class Custom extends WhatsAppClientBuilder {
        private WhatsAppStore store;
        private WhatsAppClientMessagePreviewHandler messagePreviewHandler;
        private WhatsAppClientErrorHandler errorHandler;
        private WhatsAppClientVerificationHandler.Web webVerificationHandler;

        private Custom() {

        }

        public Custom store(WhatsAppStore store) {
            this.store = store;
            return this;
        }

        public Custom errorHandler(WhatsAppClientErrorHandler errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        public Custom webVerificationSupport(WhatsAppClientVerificationHandler.Web webVerificationHandler) {
            this.webVerificationHandler = webVerificationHandler;
            return this;
        }

        public Custom messagePreviewHandler(WhatsAppClientMessagePreviewHandler messagePreviewHandler) {
            this.messagePreviewHandler = messagePreviewHandler;
            return this;
        }

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
