package com.github.auties00.cobalt.exception;

/**
 * Non-fatal exception used for server-driven runtime conditions that should
 * still flow through the central error handler without forcing a disconnect.
 */
public final class WhatsAppServerRuntimeException extends WhatsAppException {
    public WhatsAppServerRuntimeException(String message) {
        super(message);
    }

    public WhatsAppServerRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public boolean isFatal() {
        return false;
    }
}
