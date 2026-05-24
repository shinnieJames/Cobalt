package com.github.auties00.cobalt.exception;

import com.github.auties00.cobalt.model.jid.Jid;

/**
 * Thrown when a string cannot be parsed as a WhatsApp {@link Jid}.
 *
 * @apiNote
 * JIDs follow a {@code user@server} shape where the server suffix
 * selects the entity kind ({@code s.whatsapp.net} for a contact,
 * {@code g.us} for a group, {@code newsletter} for a newsletter, and so
 * on). This exception is raised when the input is missing the separator,
 * has an empty component, contains forbidden characters, or names an
 * unknown server suffix. Catch it locally to reject user-supplied JID
 * strings without bringing down the rest of the operation.
 *
 * @implNote
 * This implementation always reports the failure as non-fatal: only the
 * offending value is rejected and the rest of the session is unaffected.
 *
 * @see Jid
 */
public final class WhatsAppMalformedJidException extends WhatsAppException {

    /**
     * Constructs a new malformed JID exception with the specified detail message.
     *
     * @param message the detail message explaining why the JID is malformed
     */
    public WhatsAppMalformedJidException(String message) {
        super(message);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always returns {@code false}: a JID that fails
     * to parse only invalidates the specific operation that produced it.
     */
    @Override
    public boolean isFatal() {
        return false;
    }
}
