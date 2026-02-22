module com.github.auties00.cobalt {
    // Codegen
    requires static com.palantir.javapoet;

    // Http client
    requires java.net.http;

    // Cryptography
    requires com.github.auties00.libsignal;
    requires com.github.auties00.curve25519;

    // QR related dependencies
    requires com.google.zxing;
    requires com.google.zxing.javase;
    requires it.auties.qr;
    requires static java.desktop;

    // Serialization (Protobuf, JSON)
    requires it.auties.protobuf.base;
    requires com.alibaba.fastjson2;

    // Generate messageContainer previews
    requires it.auties.linkpreview;
    requires com.googlecode.ezvcard;

    // Message store
    requires com.github.auties00.collections;

    // Database storage (SQLite via jOOQ)
    requires static org.jooq;
    requires static java.sql;
    requires static org.xerial.sqlitejdbc;

    // Mobile api
    requires net.dongliu.apkparser;
    requires com.google.i18n.phonenumbers.libphonenumber;

    // TODO: Decide exports
}