package com.github.auties00.cobalt.socket.application;

import com.github.auties00.cobalt.exception.WhatsAppException;
import com.github.auties00.cobalt.node.Node;

public interface WhatsAppSocketListener {
    void onNode(Node node);
    void onError(WhatsAppException exception);
    void onClose();
}
