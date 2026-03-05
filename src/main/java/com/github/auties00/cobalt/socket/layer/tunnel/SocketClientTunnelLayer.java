package com.github.auties00.cobalt.socket.layer.tunnel;

import com.github.auties00.cobalt.client.WhatsAppClientProxy;
import com.github.auties00.cobalt.socket.layer.SocketClientLayer;
import com.github.auties00.cobalt.socket.layer.tunnel.http.HttpSocketClientTunnelLayer;
import com.github.auties00.cobalt.socket.layer.tunnel.socks.SocksSocketClientTunnelLayer;

public interface SocketClientTunnelLayer extends SocketClientLayer {
    static SocketClientTunnelLayer ofSocks(WhatsAppClientProxy.Socks socks, SocketClientLayer innerLayer) {
        return new SocksSocketClientTunnelLayer(socks, innerLayer);
    }

    static SocketClientTunnelLayer ofHttp(WhatsAppClientProxy.Http http, SocketClientLayer innerLayer) {
        return new HttpSocketClientTunnelLayer(http, innerLayer);
    }
}
