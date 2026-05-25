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
 *   <li>Connection and control through {@link com.github.auties00.cobalt.client.WhatsAppClient}, the
 *       single facade for pairing, connecting, sending and receiving traffic, and driving every other
 *       feature.</li>
 *   <li>Event delivery through {@link com.github.auties00.cobalt.client.WhatsAppClientListener}, the
 *       callback interface for incoming messages, presence, calls, and other asynchronous notifications.</li>
 *   <li>Session and entity persistence through {@link com.github.auties00.cobalt.store.WhatsAppStore},
 *       which holds chats, contacts, messages, and the Signal protocol material for a session.</li>
 *   <li>The {@code call} packages, which expose voice and video calling together with audio and video
 *       frame sources, sinks, and filters.</li>
 *   <li>The {@code node} packages, which expose the stanza model used to build and read IQ, MEX, SMAX,
 *       and USync protocol nodes.</li>
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
    // Source provenance annotations
    requires static com.github.auties00.cobalt.meta;

    // Vector API
    requires static jdk.incubator.vector;

    // Http client
    requires java.net.http;

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

    // Logging
    requires java.logging;

    // PDF rendering (document thumbnails in the upload transcoder)
    requires org.apache.pdfbox;

    // Data model
    requires com.github.auties00.cobalt.model;

    // WAM annotation processor and types
    requires com.github.auties00.cobalt.wam;

    // Mobile api
    requires net.dongliu.apkparser;
    requires com.google.i18n.phonenumbers.libphonenumber;

    // Calls, DTLS-SRTP handshake (BouncyCastle TLS + PKIX)
    requires org.bouncycastle.provider;
    requires org.bouncycastle.tls;
    requires org.bouncycastle.pkix;

    // Calls
    exports com.github.auties00.cobalt.call;
    exports com.github.auties00.cobalt.call.frame.audio;
    exports com.github.auties00.cobalt.call.frame.video;
    exports com.github.auties00.cobalt.call.source;
    exports com.github.auties00.cobalt.call.sink;
    exports com.github.auties00.cobalt.call.filter;
    exports com.github.auties00.cobalt.call.session;

    // Client API
    exports com.github.auties00.cobalt.client;

    // Exceptions
    exports com.github.auties00.cobalt.exception;

    // Node/Stanza
    // Exported so the user can make his own queries
    // TODO: Should we expose IQ/MEX/SMAX implementations queries as well?
    exports com.github.auties00.cobalt.node;
    exports com.github.auties00.cobalt.node.iq;
    exports com.github.auties00.cobalt.node.mex;
    exports com.github.auties00.cobalt.node.smax;
    exports com.github.auties00.cobalt.node.usync;

    // Store
    // Exported for obvious reasons
    exports com.github.auties00.cobalt.store;

    // Metrics
    // Export the codegen event specs + types so that users can send their own WAM events through WhatsAppClient
    // Don't export the WAM internals
    exports com.github.auties00.cobalt.wam.event;
    exports com.github.auties00.cobalt.wam.type;
}