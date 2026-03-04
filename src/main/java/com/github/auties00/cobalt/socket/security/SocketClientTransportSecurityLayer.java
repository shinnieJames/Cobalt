package com.github.auties00.cobalt.socket.security;

import com.github.auties00.cobalt.socket.SocketClientLayer;
import com.github.auties00.cobalt.socket.security.tls.TlsSocketClientSecurityLayer;
import com.github.auties00.cobalt.socket.security.whatsapp.WhatsAppSocketClientSecurityLayer;
import com.github.auties00.cobalt.store.WhatsAppStore;

import java.util.Objects;

public sealed interface SocketClientTransportSecurityLayer
        extends SocketClientSecurityLayer
        permits TlsSocketClientSecurityLayer, WhatsAppSocketClientSecurityLayer {
    static SocketClientTransportSecurityLayer ofTls(SocketClientLayer transportLayer) {
        Objects.requireNonNull(transportLayer, "transportLayer cannot be null");
        return new TlsSocketClientSecurityLayer(transportLayer);
    }

    static SocketClientTransportSecurityLayer ofWhatsApp(SocketClientLayer transportLayer, WhatsAppStore store) {
        Objects.requireNonNull(transportLayer, "transportLayer cannot be null");
        Objects.requireNonNull(store, "store cannot be null");

       return new WhatsAppSocketClientSecurityLayer(transportLayer, store);
    }
}
