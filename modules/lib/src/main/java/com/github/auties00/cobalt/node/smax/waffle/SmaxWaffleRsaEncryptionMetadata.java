package com.github.auties00.cobalt.node.smax.waffle;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * The shared {@code <encryption_metadata version="1" algorithm="rsa2048"/>}
 * subtree carried by every Waffle RPC that exchanges encrypted payloads.
 *
 * @apiNote
 * Powers the wire framing of WhatsApp Web's "Account Linking" surface
 * ({@code WAWebAccountLinkingAPI}), which negotiates Facebook account
 * linking for federated features such as Channels, Crossposting, and the
 * Communities entry-point. Each linking RPC pre-encrypts its payload via
 * {@code WAWebAccountLinkingCryptoUtils.wrapPayloadWithRSAAESEncryption}
 * (an RSA-2048-wrapped AES-GCM session key) and stages the four blobs
 * inside this subtree. Embedders that drive linking themselves construct
 * an instance from the wrap result, then hand it to whichever
 * {@code SmaxWaffle*Request} they are dispatching.
 *
 * @implNote
 * This implementation collapses the WA Web
 * {@code WASmaxOutWaffleRSAEncryptionMetadataMixin} merge helper and the
 * {@code WASmaxInWaffleRSAEncryptionMetadataMixin} parser into a single
 * Java value class, mirroring how Cobalt collapses every other
 * {@code Out/In} mixin pair into one type. The byte-array fields are
 * stored by reference rather than defensively copied; callers must not
 * mutate the supplied arrays after construction.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutWaffleRSAEncryptionMetadataMixin")
@WhatsAppWebModule(moduleName = "WASmaxInWaffleRSAEncryptionMetadataMixin")
public final class SmaxWaffleRsaEncryptionMetadata {
    /**
     * The RSA-2048-wrapped AES session key.
     */
    private final byte[] encryptedKey;

    /**
     * The AES-GCM nonce.
     */
    private final byte[] nonce;

    /**
     * The AES-GCM ciphertext.
     */
    private final byte[] encryptedData;

    /**
     * The AES-GCM authentication tag.
     */
    private final byte[] authTag;

    /**
     * Constructs a new metadata instance from the four pre-computed
     * cryptographic blobs.
     *
     * @apiNote
     * Embedders typically build the four blobs by calling the WA Web
     * equivalent of {@code wrapPayloadWithRSAAESEncryption} against the
     * relay's certificate payload key, then hand the result here before
     * embedding it in a {@link SmaxWaffleEncryptedPayloadRequestRequest},
     * {@link SmaxWaffleGenerateWAEntACUserRequest},
     * {@link SmaxWaffleRefreshAccessTokensRequest}, or
     * {@link SmaxWaffleWFPingRequest}.
     *
     * @param encryptedKey  the RSA-wrapped AES session key; never
     *                      {@code null}
     * @param nonce         the AES-GCM nonce; never {@code null}
     * @param encryptedData the AES-GCM ciphertext; never {@code null}
     * @param authTag       the AES-GCM authentication tag; never
     *                      {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public SmaxWaffleRsaEncryptionMetadata(byte[] encryptedKey, byte[] nonce,
                                           byte[] encryptedData, byte[] authTag) {
        this.encryptedKey = Objects.requireNonNull(encryptedKey, "encryptedKey cannot be null");
        this.nonce = Objects.requireNonNull(nonce, "nonce cannot be null");
        this.encryptedData = Objects.requireNonNull(encryptedData, "encryptedData cannot be null");
        this.authTag = Objects.requireNonNull(authTag, "authTag cannot be null");
    }

    /**
     * Returns the RSA-wrapped AES session key.
     *
     * @return the wrapped key bytes; never {@code null}
     */
    public byte[] encryptedKey() {
        return encryptedKey;
    }

    /**
     * Returns the AES-GCM nonce.
     *
     * @return the nonce bytes; never {@code null}
     */
    public byte[] nonce() {
        return nonce;
    }

    /**
     * Returns the AES-GCM ciphertext.
     *
     * @return the ciphertext bytes; never {@code null}
     */
    public byte[] encryptedData() {
        return encryptedData;
    }

    /**
     * Returns the AES-GCM authentication tag.
     *
     * @return the tag bytes; never {@code null}
     */
    public byte[] authTag() {
        return authTag;
    }

    /**
     * Builds the {@code <encryption_metadata/>} subtree wrapping the four
     * cryptographic blobs.
     *
     * @apiNote
     * Called by every {@code SmaxWaffle*Request.toNode()} that needs to
     * embed encryption metadata; the subtree is the first child of the
     * outer {@code <iq xmlns="waffle"/>} envelope. The fixed
     * {@code version="1"} and {@code algorithm="rsa2048"} attributes
     * match WA Web's hard-coded values and the relay's RSA-2048
     * verification.
     *
     * @return the {@code <encryption_metadata/>} {@link Node}; never
     *         {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxOutWaffleRSAEncryptionMetadataMixin",
            exports = "mergeRSAEncryptionMetadataMixin", adaptation = WhatsAppAdaptation.DIRECT)
    public Node toNode() {
        var encryptedKeyNode = new NodeBuilder()
                .description("encrypted_key")
                .content(encryptedKey)
                .build();
        var nonceNode = new NodeBuilder()
                .description("nonce")
                .content(nonce)
                .build();
        var encryptedDataNode = new NodeBuilder()
                .description("encrypted_data")
                .content(encryptedData)
                .build();
        var authTagNode = new NodeBuilder()
                .description("auth_tag")
                .content(authTag)
                .build();
        return new NodeBuilder()
                .description("encryption_metadata")
                .attribute("version", "1")
                .attribute("algorithm", "rsa2048")
                .content(encryptedKeyNode, nonceNode, encryptedDataNode, authTagNode)
                .build();
    }

    /**
     * Parses an inbound {@code <encryption_metadata/>} subtree.
     *
     * @apiNote
     * Called by every {@code SmaxWaffle*Response.Success.of(...)} that
     * receives encryption metadata back from the relay (the
     * {@code GenerateWAEntACUser}, {@code RefreshAccessTokens}, and
     * {@code EncryptedPayloadRequest} success replies all carry one).
     * The returned instance owns the four extracted byte blobs that
     * embedders subsequently feed into the WA Web counterpart of
     * {@code decryptRSAEncryptedPayload}.
     *
     * @implNote
     * This implementation gates on the fixed {@code version="1"} and
     * {@code algorithm="rsa2048"} attributes and on the presence of all
     * four child elements; WA Web's parser additionally enforces
     * per-blob size ranges (key 1-2048 bytes, nonce 1-128, data 1-8192,
     * tag 1-128) which Cobalt does not currently re-check after the
     * presence test.
     *
     * @param node the {@code <encryption_metadata/>} stanza; never
     *             {@code null}
     * @return an {@link Optional} carrying the parsed metadata, or empty
     *         when the stanza does not match the expected shape
     * @throws NullPointerException if {@code node} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxInWaffleRSAEncryptionMetadataMixin",
            exports = "parseRSAEncryptionMetadataMixin", adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<SmaxWaffleRsaEncryptionMetadata> of(Node node) {
        Objects.requireNonNull(node, "node cannot be null");
        if (!node.hasAttribute("version", "1")) {
            return Optional.empty();
        }
        if (!node.hasAttribute("algorithm", "rsa2048")) {
            return Optional.empty();
        }
        var encryptedKey = node.getChild("encrypted_key")
                .flatMap(Node::toContentBytes)
                .orElse(null);
        if (encryptedKey == null) {
            return Optional.empty();
        }
        var nonce = node.getChild("nonce")
                .flatMap(Node::toContentBytes)
                .orElse(null);
        if (nonce == null) {
            return Optional.empty();
        }
        var encryptedData = node.getChild("encrypted_data")
                .flatMap(Node::toContentBytes)
                .orElse(null);
        if (encryptedData == null) {
            return Optional.empty();
        }
        var authTag = node.getChild("auth_tag")
                .flatMap(Node::toContentBytes)
                .orElse(null);
        if (authTag == null) {
            return Optional.empty();
        }
        return Optional.of(new SmaxWaffleRsaEncryptionMetadata(encryptedKey, nonce, encryptedData, authTag));
    }

    /**
     * Returns whether the given object is a
     * {@link SmaxWaffleRsaEncryptionMetadata} with equal cryptographic
     * blobs.
     *
     * @param obj the candidate; may be {@code null}
     * @return {@code true} when the four byte arrays match element-wise
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxWaffleRsaEncryptionMetadata) obj;
        return Arrays.equals(this.encryptedKey, that.encryptedKey)
                && Arrays.equals(this.nonce, that.nonce)
                && Arrays.equals(this.encryptedData, that.encryptedData)
                && Arrays.equals(this.authTag, that.authTag);
    }

    /**
     * Returns a hash code derived from the four cryptographic blobs.
     *
     * @return a content-based hash consistent with
     *         {@link #equals(Object)}
     */
    @Override
    public int hashCode() {
        var result = Arrays.hashCode(encryptedKey);
        result = 31 * result + Arrays.hashCode(nonce);
        result = 31 * result + Arrays.hashCode(encryptedData);
        result = 31 * result + Arrays.hashCode(authTag);
        return result;
    }

    /**
     * Returns a debug rendering that summarises each blob as its length
     * rather than its contents.
     *
     * @apiNote
     * Bytes are summarised because the blobs are ciphertext and
     * generally not useful to print verbatim; the length-only rendering
     * keeps the {@code toString} bounded and avoids leaking sensitive
     * material into log files.
     *
     * @return a human-readable summary; never {@code null}
     */
    @Override
    public String toString() {
        return "SmaxWaffleRsaEncryptionMetadata[encryptedKey="
                + (encryptedKey != null ? encryptedKey.length + " bytes" : "null")
                + ", nonce="
                + (nonce != null ? nonce.length + " bytes" : "null")
                + ", encryptedData="
                + (encryptedData != null ? encryptedData.length + " bytes" : "null")
                + ", authTag="
                + (authTag != null ? authTag.length + " bytes" : "null") + ']';
    }
}
