package com.github.auties00.cobalt.model.error;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Optional;

/**
 * A receipt that the client sends to the server to acknowledge a media download failure
 * and request re-upload of the media content.
 *
 * <p>When a media message cannot be downloaded, for example because the CDN link has
 * expired or the content is no longer available, the client constructs a
 * {@code ServerErrorReceipt} containing the {@link #stanzaId()} of the failed message,
 * serializes it to protobuf, encrypts the result with AES-GCM using a key derived from
 * the original media key via HKDF, and transmits it as a {@code server-error} type
 * receipt stanza. The server then notifies the sender to re-upload the media, and the
 * result is delivered to the requesting client as a
 * {@link com.github.auties00.cobalt.model.media.MediaRetryNotification}.
 *
 * <p>The encryption uses the info string {@code "WhatsApp Media Retry Notification"}
 * for HKDF key derivation and a random 12-byte initialization vector for AES-GCM. The
 * serialized receipt bytes serve as the additional authenticated data (AAD) parameter,
 * with the stanza identifier used as the nonce context.
 *
 * @see com.github.auties00.cobalt.model.media.MediaRetryNotification
 */
@ProtobufMessage(name = "ServerErrorReceipt")
public final class ServerErrorReceipt {
    /**
     * The identifier of the message stanza whose media download failed, used by the
     * server to correlate this error receipt with the original media message.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String stanzaId;

    /**
     * Constructs a new {@code ServerErrorReceipt} with the given stanza identifier.
     *
     * @param stanzaId the identifier of the message stanza whose media download
     *        failed, or {@code null} if not available
     */
    ServerErrorReceipt(String stanzaId) {
        this.stanzaId = stanzaId;
    }

    /**
     * Returns the identifier of the message stanza whose media download failed.
     *
     * @return an {@link Optional} containing the stanza identifier, or an empty
     *         {@code Optional} if not available
     */
    public Optional<String> stanzaId() {
        return Optional.ofNullable(stanzaId);
    }

    /**
     * Sets the identifier of the message stanza whose media download failed.
     *
     * @param stanzaId the stanza identifier, or {@code null} to clear
     */
    public void setStanzaId(String stanzaId) {
        this.stanzaId = stanzaId;
    }
}
