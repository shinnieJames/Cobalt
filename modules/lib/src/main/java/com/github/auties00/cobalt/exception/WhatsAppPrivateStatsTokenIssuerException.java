package com.github.auties00.cobalt.exception;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

/**
 * Thrown when Cobalt cannot obtain a private-stats authentication token
 * from the WhatsApp servers.
 *
 * @apiNote
 * The private-stats upload backend (used by the WAM telemetry pipeline)
 * gates uploads with a single-use, blinded credential issued by the
 * {@code <sign_credential>} IQ exchange documented in WA Web's
 * {@code WAWebIssuePrivateStatsToken}. Failures fall into three buckets:
 * the IQ comes back with {@code type="error"}, a required child element
 * is missing or malformed, or the returned Ed25519 material does not
 * decode as a valid curve point so the unblinding step cannot run. Most
 * Cobalt embedders do not run the WAM pipeline and will never see this.
 *
 * @implNote
 * This implementation always reports the failure as non-fatal: token
 * issuance is scoped to a single upload, not to the session as a whole,
 * so the upload can be retried independently.
 */
@WhatsAppWebModule(moduleName = "WAWebIssuePrivateStatsToken")
public final class WhatsAppPrivateStatsTokenIssuerException extends WhatsAppException {

    /**
     * Constructs a new token-issuance exception with the specified detail message.
     *
     * @param message the detail message describing the failure
     */
    public WhatsAppPrivateStatsTokenIssuerException(String message) {
        super(message);
    }

    /**
     * Constructs a new token-issuance exception with the specified detail message and cause.
     *
     * @param message the detail message describing the failure
     * @param cause   the underlying cause
     */
    public WhatsAppPrivateStatsTokenIssuerException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always returns {@code false}: token issuance
     * is scoped to a single upload.
     */
    @Override
    public boolean isFatal() {
        return false;
    }
}
