package com.github.auties00.cobalt.client;

/**
 * Names the reason a {@link WhatsAppClient} session was torn down.
 *
 * @apiNote
 * Receivers of {@link WhatsAppClientListener#onDisconnected(WhatsAppClient, WhatsAppClientDisconnectReason)}
 * read this value to decide whether to retry, prompt the user to
 * re-authenticate, or surface a permanent failure. The mapping is fixed:
 * {@link WhatsAppClientErrorHandler.Result#DISCONNECT} produces
 * {@link #DISCONNECTED}, {@link WhatsAppClientErrorHandler.Result#RECONNECT}
 * produces {@link #RECONNECTING}, {@link WhatsAppClientErrorHandler.Result#LOG_OUT}
 * produces {@link #LOGGED_OUT}, and {@link WhatsAppClientErrorHandler.Result#BAN}
 * produces {@link #BANNED}.
 *
 * @see WhatsAppClient#disconnect(WhatsAppClientDisconnectReason)
 * @see WhatsAppClientListener#onDisconnected(WhatsAppClient, WhatsAppClientDisconnectReason)
 * @see WhatsAppClientErrorHandler
 */
public enum WhatsAppClientDisconnectReason {
    /**
     * A clean shutdown that does not request reconnection or credential
     * deletion.
     *
     * @apiNote
     * Emitted when the application calls {@link WhatsAppClient#disconnect()}
     * directly or when the library tears the socket down without intending
     * to recover. The session stays linked, so a later
     * {@link WhatsAppClient#connect()} resumes against the same store.
     */
    DISCONNECTED,

    /**
     * A transient teardown that the library follows with a fresh connect.
     *
     * @apiNote
     * Driven by recoverable stream errors and socket-level closes raised
     * by {@link com.github.auties00.cobalt.socket.WhatsAppSocketClient}.
     * Listeners typically log the event and let the client reconnect on
     * its own; persisted state remains intact.
     */
    RECONNECTING,

    /**
     * A logout that deletes session credentials from the store.
     *
     * @apiNote
     * Triggered by an explicit {@link WhatsAppClient#logout()} or by a
     * server-driven unpair. Re-authentication (QR scan, pairing code, or
     * mobile registration) is required before the same account can be
     * used again.
     */
    LOGGED_OUT,

    /**
     * A terminal teardown caused by the WhatsApp servers banning the
     * account.
     *
     * @apiNote
     * Reconnection is rejected by the server; the only recovery path is
     * via WhatsApp support. The store is deleted on emission so a stale
     * session cannot be reused.
     */
    BANNED
}
