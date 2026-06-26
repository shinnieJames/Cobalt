import com.github.auties00.cobalt.listener.WhatsAppListener;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientListener;
import com.github.auties00.cobalt.store.linked.LinkedWhatsAppStore;

/**
 * Defines the Cobalt library, a Java reimplementation of the WhatsApp Web, Desktop, and Mobile clients.
 *
 * <p>This module is the primary entry point for applications that want to connect to WhatsApp, exchange
 * messages, place and receive calls, and manage session state without depending on the official clients.
 * It bundles the connection, protocol, persistence, and media capabilities behind a small public surface
 * and keeps the wire-level and cryptographic internals encapsulated.
 *
 * <p>The exported capability surface is organised as follows:
 * <ul>
 *   <li>Connection and control through {@link com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient}, the
 *       single facade for pairing, connecting, sending and receiving traffic, and driving every other
 *       feature.</li>
 *   <li>Event delivery through {@link WhatsAppListener} and its
 *       aggregator {@link LinkedWhatsAppClientListener}, the
 *       callback interfaces for incoming messages, presence, calls, and other asynchronous notifications.</li>
 *   <li>Session and entity persistence through {@link LinkedWhatsAppStore},
 *       which holds chats, contacts, messages, and the Signal protocol material for a session.</li>
 *   <li>The {@code calls2} packages, which expose voice and video calling together with audio and video
 *       frame sources, sinks, and filters.</li>
 *   <li>The {@code stanza} packages, which expose the stanza model used to build and read IQ, MEX, SMAX,
 *       and USync protocol stanzas.</li>
 *   <li>The {@code exception} package, which exposes the configurable error hierarchy raised across the
 *       library.</li>
 *   <li>The {@code wam.event} and {@code wam.type} packages, which expose the metrics event
 *       specifications and types so applications can emit their own WAM events.</li>
 * </ul>
 *
 * <p>The data model and the WAM internals live in separate modules; the cryptography, serialization,
 * media, and transport dependencies this library relies on are not re-exported.
 */
module com.github.auties00.cobalt {
    // Source provenance annotations (only source requires them, optional)
    requires static com.github.auties00.cobalt.meta;

    // Vector API (only optimized paths requires it, optional until it comes out of incubation)
    requires static jdk.incubator.vector;

    // Logging
    requires java.logging;

    // Http client
    requires java.net.http;

    // Built-in webhook receiver for the Cloud API client
    requires jdk.httpserver;

    // Cryptography
    requires com.github.auties00.libsignal;
    requires com.github.auties00.curve25519;

    // QR related dependencies
    requires com.google.zxing;
    requires com.google.zxing.javase;
    requires it.auties.qr;
    requires static java.desktop; // Not necessary if the user doesn't want to open the QR on the Desktop

    // Serialization (Protobuf, JSON)
    requires it.auties.protobuf.base;
    requires com.alibaba.fastjson2;

    // Generate messageContainer previews
    requires it.auties.linkpreview;
    requires com.googlecode.ezvcard;

    // Message store
    requires com.github.auties00.collections;

    // PDF rendering (document thumbnails in the upload transcoder)
    requires org.apache.pdfbox;

    // Data model
    requires com.github.auties00.cobalt.model;

    // WAM annotation processor and types
    requires com.github.auties00.cobalt.wam;

    // Mobile api
    requires net.dongliu.apkparser;
    requires com.google.i18n.phonenumbers.libphonenumber;

    // Calls, DTLS-SRTP handshake (BouncyCastle TLS)
    requires org.bouncycastle.provider;
    requires org.bouncycastle.tls;

    // Calls
    exports com.github.auties00.cobalt.calls2.stream;

    // Client API
    exports com.github.auties00.cobalt.client;
    exports com.github.auties00.cobalt.client.linked;
    exports com.github.auties00.cobalt.client.cloud;

    // Listeners
    exports com.github.auties00.cobalt.listener;
    exports com.github.auties00.cobalt.listener.linked;
    exports com.github.auties00.cobalt.listener.cloud;

    // Exceptions
    exports com.github.auties00.cobalt.exception;

    // Stanza queries
    // Exported so the user can make his own queries
    exports com.github.auties00.cobalt.stanza;
    exports com.github.auties00.cobalt.stanza.iq;
    exports com.github.auties00.cobalt.stanza.mex;
    exports com.github.auties00.cobalt.stanza.smax;
    exports com.github.auties00.cobalt.stanza.usync;

    // GraphQL queries
    // Exported so the user can make his own queries
    exports com.github.auties00.cobalt.graphql.facebook;
    exports com.github.auties00.cobalt.graphql.whatsapp;

    // Store
    // Exported for obvious reasons
    exports com.github.auties00.cobalt.store;
    exports com.github.auties00.cobalt.store.linked;
    exports com.github.auties00.cobalt.store.cloud;

    // Metrics
    // Export the codegen event specs + types so that users can send their own WAM events through LinkedWhatsAppClient
    // Don't export the WAM internals
    exports com.github.auties00.cobalt.wam.event;
    exports com.github.auties00.cobalt.wam.type;
}