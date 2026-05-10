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

    // Data model
    requires com.github.auties00.cobalt.model;

    // WAM annotation processor and types
    requires com.github.auties00.cobalt.wam;

    // Mobile api
    requires net.dongliu.apkparser;
    requires com.google.i18n.phonenumbers.libphonenumber;

    // Calls, DTLS-SRTP handshake (BouncyCastle TLS + PKIX)
    // SRTP packet protection is implemented in pure Java on top of the JDK (java.base) and needs no module declaration here.
    requires org.bouncycastle.provider;
    requires org.bouncycastle.tls;
    requires org.bouncycastle.pkix;

    // Call media SPI — public so user code can implement Sources /
    // Sinks for arbitrary inputs (the cobalt-call-toolkit is just
    // one such consumer).
    exports com.github.auties00.cobalt.call.io;

    // Native lib loader — exported only to the toolkit so it can
    // resolve FFmpeg the same way the lib module resolves libopus /
    // libvpx / openh264 / libspeexdsp / libusrsctp.
    exports com.github.auties00.cobalt.util to com.github.auties00.cobalt.call.toolkit;
}