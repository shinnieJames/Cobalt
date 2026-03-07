package com.github.auties00.cobalt.socket;

import com.github.auties00.cobalt.exception.WhatsAppStreamException;
import com.github.auties00.cobalt.node.Node;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;

public final class WhatsAppSocketStanza {
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final Node body;
    private final Function<Node, Boolean> filter;
    private volatile Node response;

    public WhatsAppSocketStanza(Node body, Function<Node, Boolean> filter) {
        this.body = body;
        this.filter = filter;
    }

    public boolean complete(Node response) {
        var acceptable = response == null
                || filter == null
                || filter.apply(response);
        if (acceptable) {
            synchronized (this) {
                this.response = response;
                notifyAll();
            }
        }
        return acceptable;
    }

    public Node waitForResponse() {
        return waitForResponse(TIMEOUT);
    }

    public Node waitForResponse(Duration timeout) {
        if (response == null) {
            synchronized (this) {
                if (response == null) {
                    var end = Instant.now().plus(timeout);
                    while (Instant.now().isBefore(end)) {
                        try {
                            wait(TIMEOUT.toMillis());
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new WhatsAppStreamException.NodeTimeout(body);
                        }
                    }
                    if (response == null) {
                        throw new WhatsAppStreamException.NodeTimeout(body);
                    }
                }
            }
        }
        return response;
    }
}
