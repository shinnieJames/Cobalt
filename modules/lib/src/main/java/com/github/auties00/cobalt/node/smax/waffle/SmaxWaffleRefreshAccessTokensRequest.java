package com.github.auties00.cobalt.node.smax.waffle;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code <iq xmlns="waffle" smax_id="46" type="get"/>}
 * Waffle refresh-access-tokens request.
 *
 * @apiNote
 * Powers {@code WAWebAccountLinkingAPI.refreshAccessToken}, which
 * rotates the linked Facebook account's OAuth-style access tokens
 * before they expire (the Account-Linking surface depends on those
 * tokens for Channels, Crossposting, and the Communities entry-point).
 * The embedder encrypts the rotation payload locally via
 * {@code WAWebAccountLinkingCryptoUtils.wrapPayloadWithRSAAESEncryption}
 * before constructing the request; the reply is parsed by
 * {@link SmaxWaffleRefreshAccessTokensResponse} and returns fresh
 * encryption metadata that the embedder decrypts back into the rotated
 * tokens.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutWaffleRefreshAccessTokensRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutWaffleBaseIQGetRequestMixin")
public final class SmaxWaffleRefreshAccessTokensRequest implements SmaxOperation.Request {
    /**
     * The RSA encryption metadata subtree.
     */
    private final SmaxWaffleRsaEncryptionMetadata encryptionMetadata;

    /**
     * The client wall-clock at request time.
     */
    private final long timestamp;

    /**
     * The linked Facebook account id as opaque bytes.
     */
    private final byte[] fbid;

    /**
     * Constructs a refresh-access-tokens request.
     *
     * @apiNote
     * The encrypted payload sealed inside
     * {@link SmaxWaffleRsaEncryptionMetadata} carries the rotation
     * context expected by the Waffle backend; the embedder produces
     * that payload through the WA Web equivalent of
     * {@code wrapPayloadWithRSAAESEncryption} against the linked
     * account's encryption key.
     *
     * @param encryptionMetadata the RSA encryption metadata; never
     *                           {@code null}
     * @param timestamp          the request timestamp
     * @param fbid               the linked Facebook account id; never
     *                           {@code null}
     * @throws NullPointerException if {@code encryptionMetadata} or
     *                              {@code fbid} is {@code null}
     */
    public SmaxWaffleRefreshAccessTokensRequest(SmaxWaffleRsaEncryptionMetadata encryptionMetadata, long timestamp, byte[] fbid) {
        this.encryptionMetadata = Objects.requireNonNull(encryptionMetadata, "encryptionMetadata cannot be null");
        this.timestamp = timestamp;
        this.fbid = Objects.requireNonNull(fbid, "fbid cannot be null");
    }

    /**
     * Returns the RSA encryption metadata.
     *
     * @return the metadata as supplied at construction time; never
     *         {@code null}
     */
    public SmaxWaffleRsaEncryptionMetadata encryptionMetadata() {
        return encryptionMetadata;
    }

    /**
     * Returns the request timestamp.
     *
     * @return the timestamp as supplied at construction time
     */
    public long timestamp() {
        return timestamp;
    }

    /**
     * Returns the linked Facebook account id.
     *
     * @return the id bytes as supplied at construction time; never
     *         {@code null}
     */
    public byte[] fbid() {
        return fbid;
    }

    /**
     * Builds the outbound IQ stanza ready for dispatch.
     *
     * @apiNote
     * Produces
     * {@code <iq xmlns="waffle" smax_id="46" type="get" to="s.whatsapp.net">
     * <encryption_metadata.../> <timestamp.../> <fbid.../></iq>}; the
     * dispatch path stamps a fresh {@code id} attribute on every
     * outbound stanza so the reply parser can match it back to this
     * request.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope and the
     *         encrypted refresh payload; never {@code null}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutWaffleRefreshAccessTokensRequest",
            exports = "makeRefreshAccessTokensRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var encryptionMetadataNode = encryptionMetadata.toNode();
        var timestampNode = new NodeBuilder()
                .description("timestamp")
                .content(timestamp)
                .build();
        var fbidNode = new NodeBuilder()
                .description("fbid")
                .content(fbid)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "waffle")
                .attribute("smax_id", 46)
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(encryptionMetadataNode, timestampNode, fbidNode);
    }

    /**
     * Returns whether the given object is a
     * {@link SmaxWaffleRefreshAccessTokensRequest} with equal payload
     * fields.
     *
     * @param obj the candidate; may be {@code null}
     * @return {@code true} when metadata, timestamp, and fbid all
     *         match
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxWaffleRefreshAccessTokensRequest) obj;
        return this.timestamp == that.timestamp
                && Objects.equals(this.encryptionMetadata, that.encryptionMetadata)
                && Arrays.equals(this.fbid, that.fbid);
    }

    /**
     * Returns a hash code derived from the three payload fields.
     *
     * @return a content-based hash consistent with
     *         {@link #equals(Object)}
     */
    @Override
    public int hashCode() {
        var result = Objects.hash(encryptionMetadata, timestamp);
        result = 31 * result + Arrays.hashCode(fbid);
        return result;
    }

    /**
     * Returns a debug rendering that summarises the fbid as a length
     * rather than as raw bytes.
     *
     * @return a human-readable summary; never {@code null}
     */
    @Override
    public String toString() {
        return "SmaxWaffleRefreshAccessTokensRequest[encryptionMetadata=" + encryptionMetadata
                + ", timestamp=" + timestamp
                + ", fbid=" + (fbid != null ? fbid.length + " bytes" : "null") + ']';
    }
}
