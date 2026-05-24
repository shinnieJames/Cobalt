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
 * The outbound {@code <iq xmlns="waffle" smax_id="47" type="get"/>}
 * Waffle encrypted-payload-request stanza.
 *
 * @apiNote
 * Powers the generic encrypted-action channel that
 * {@code WAWebAccountLinkingAPI.sendLinkingMutation} (action
 * {@code "waffle_100"}) and {@code WAWebCrosspostingAPI} (action
 * {@code "waffle_1"}) use to dispatch arbitrary linked-account CRUD
 * payloads to the Waffle backend; the relay forwards the encrypted
 * action verbatim to the Facebook side. The embedder encrypts the
 * action with {@code WAWebAccountLinkingCryptoUtils.wrapPayloadWithRSAAESEncryption}
 * before constructing the request; the reply is parsed by
 * {@link SmaxWaffleEncryptedPayloadRequestResponse} and carries the
 * encrypted Facebook-side response.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutWaffleEncryptedPayloadRequestRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutWaffleBaseIQGetRequestMixin")
public final class SmaxWaffleEncryptedPayloadRequestRequest implements SmaxOperation.Request {
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
     * The opaque action selector bytes (the WA Web call sites pass
     * literal strings such as {@code "waffle_100"} or
     * {@code "waffle_1"}).
     */
    private final byte[] action;

    /**
     * Constructs an encrypted-payload-request stanza.
     *
     * @apiNote
     * The {@code action} bytes are the action selector that the relay
     * forwards to the Facebook side; the encrypted payload itself
     * lives inside {@code encryptionMetadata}. WA Web's two known
     * selectors are {@code "waffle_100"} (Account-Linking debug /
     * mutation) and {@code "waffle_1"} (Crossposting).
     *
     * @param encryptionMetadata the RSA encryption metadata; never
     *                           {@code null}
     * @param timestamp          the request timestamp
     * @param fbid               the linked Facebook account id; never
     *                           {@code null}
     * @param action             the opaque action selector bytes;
     *                           never {@code null}
     * @throws NullPointerException if {@code encryptionMetadata},
     *                              {@code fbid}, or {@code action} is
     *                              {@code null}
     */
    public SmaxWaffleEncryptedPayloadRequestRequest(SmaxWaffleRsaEncryptionMetadata encryptionMetadata, long timestamp,
                   byte[] fbid, byte[] action) {
        this.encryptionMetadata = Objects.requireNonNull(encryptionMetadata, "encryptionMetadata cannot be null");
        this.timestamp = timestamp;
        this.fbid = Objects.requireNonNull(fbid, "fbid cannot be null");
        this.action = Objects.requireNonNull(action, "action cannot be null");
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
     * Returns the opaque action selector bytes.
     *
     * @return the action bytes as supplied at construction time;
     *         never {@code null}
     */
    public byte[] action() {
        return action;
    }

    /**
     * Builds the outbound IQ stanza ready for dispatch.
     *
     * @apiNote
     * Produces
     * {@code <iq xmlns="waffle" smax_id="47" type="get" to="s.whatsapp.net">
     * <encryption_metadata.../> <timestamp.../> <fbid.../> <action.../></iq>};
     * the dispatch path stamps a fresh {@code id} attribute on every
     * outbound stanza so the reply parser can match it back to this
     * request.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope and the
     *         four payload children; never {@code null}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutWaffleEncryptedPayloadRequestRequest",
            exports = "makeEncryptedPayloadRequestRequest", adaptation = WhatsAppAdaptation.DIRECT)
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
        var actionNode = new NodeBuilder()
                .description("action")
                .content(action)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "waffle")
                .attribute("smax_id", 47)
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(encryptionMetadataNode, timestampNode, fbidNode, actionNode);
    }

    /**
     * Returns whether the given object is a
     * {@link SmaxWaffleEncryptedPayloadRequestRequest} with equal
     * payload fields.
     *
     * @param obj the candidate; may be {@code null}
     * @return {@code true} when metadata, timestamp, fbid, and action
     *         all match
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxWaffleEncryptedPayloadRequestRequest) obj;
        return this.timestamp == that.timestamp
                && Objects.equals(this.encryptionMetadata, that.encryptionMetadata)
                && Arrays.equals(this.fbid, that.fbid)
                && Arrays.equals(this.action, that.action);
    }

    /**
     * Returns a hash code derived from the four payload fields.
     *
     * @return a content-based hash consistent with
     *         {@link #equals(Object)}
     */
    @Override
    public int hashCode() {
        var result = Objects.hash(encryptionMetadata, timestamp);
        result = 31 * result + Arrays.hashCode(fbid);
        result = 31 * result + Arrays.hashCode(action);
        return result;
    }

    /**
     * Returns a debug rendering that summarises the fbid and action
     * arrays as lengths rather than as raw bytes.
     *
     * @return a human-readable summary; never {@code null}
     */
    @Override
    public String toString() {
        return "SmaxWaffleEncryptedPayloadRequestRequest[encryptionMetadata=" + encryptionMetadata
                + ", timestamp=" + timestamp
                + ", fbid=" + (fbid != null ? fbid.length + " bytes" : "null")
                + ", action=" + (action != null ? action.length + " bytes" : "null") + ']';
    }
}
