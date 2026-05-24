module com.github.auties00.cobalt {
    // Source provenance annotations
    requires static com.github.auties00.cobalt.meta;

    // Vector API
    requires jdk.incubator.vector;

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

    // LMDB key-value store
    requires static lmdbjava; // Not necessary if the user doesn't want a persistent store

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

    // Client
    exports com.github.auties00.cobalt.client;

    // Exceptions
    exports com.github.auties00.cobalt.exception;

    // Node/Stanza
    // Exported so the user can
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