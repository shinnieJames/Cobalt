package com.github.auties00.cobalt.node.iq.encrypt;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.util.DataUtils;

import java.util.Arrays;
import java.util.Objects;

/**
 * Typed container for one one-time pre-key entry inside the {@code <list/>} wrapper of an
 * {@link IqUploadPreKeysRequest}.
 *
 * @apiNote
 * Maps the WA Web {@code xmppPreKey({keyId, keyPair})} render: one {@code <key/>} subtree with a
 * three-byte big-endian {@code <id/>} content and a thirty-two-byte raw {@code <value/>} content
 * carrying the X25519 public key.
 */
@WhatsAppWebModule(moduleName = "WAWebSignalUtilsApi")
public final class IqUploadPreKeysPreKey {
    /**
     * The pre-key identifier; serialised as a three-byte big-endian unsigned integer into the
     * {@code <id/>} grandchild.
     */
    private final int id;

    /**
     * The thirty-two-byte X25519 public-key bytes carried verbatim by the {@code <value/>}
     * grandchild.
     */
    private final byte[] publicKey;

    /**
     * Constructs a populated pre-key entry.
     *
     * @param id        the pre-key identifier
     * @param publicKey the public-key bytes
     * @throws NullPointerException if {@code publicKey} is {@code null}
     */
    public IqUploadPreKeysPreKey(int id, byte[] publicKey) {
        this.id = id;
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey cannot be null");
    }

    /**
     * Returns the pre-key identifier.
     *
     * @return the identifier
     */
    public int id() {
        return id;
    }

    /**
     * Returns the pre-key public-key bytes.
     *
     * @return the public-key bytes
     */
    public byte[] publicKey() {
        return publicKey;
    }

    /**
     * Renders this pre-key as the canonical {@code <key/>} subtree.
     *
     * @apiNote
     * Called by {@link IqUploadPreKeysRequest#toNode()} and
     * {@link IqUploadPrekeysForRegRequest#toNode()} to assemble the {@code <list/>} payload one
     * entry at a time.
     *
     * @implNote
     * This implementation packs the identifier with
     * {@link DataUtils#intToBytes(int, int) DataUtils.intToBytes(id, 3)} so the wire content is
     * three bytes regardless of {@link #id()}'s magnitude, matching WA Web's
     * {@code BIG_ENDIAN_CONTENT(keyId, 3)}.
     *
     * @return the rendered {@code <key/>} node
     */
    @WhatsAppWebExport(moduleName = "WAWebSignalUtilsApi",
            exports = "xmppPreKey", adaptation = WhatsAppAdaptation.DIRECT)
    public Node toNode() {
        var idNode = new NodeBuilder()
                .description("id")
                .content(DataUtils.intToBytes(id, 3))
                .build();
        var valueNode = new NodeBuilder()
                .description("value")
                .content(publicKey)
                .build();
        return new NodeBuilder()
                .description("key")
                .content(idNode, valueNode)
                .build();
    }

    /**
     * Compares this entry to another instance for equality.
     *
     * @param obj the candidate instance
     * @return {@code true} when {@code obj} is an {@code IqUploadPreKeysPreKey} carrying the same
     *         identifier and identical public-key bytes
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (IqUploadPreKeysPreKey) obj;
        return this.id == that.id
                && Arrays.equals(this.publicKey, that.publicKey);
    }

    /**
     * Returns a hash code derived from the identifier and public-key bytes.
     *
     * @return the combined hash
     */
    @Override
    public int hashCode() {
        return 31 * Integer.hashCode(id) + Arrays.hashCode(publicKey);
    }

    /**
     * Returns the record-style rendering for this entry.
     *
     * @return the rendered string
     */
    @Override
    public String toString() {
        return "IqUploadPreKeysPreKey[id=" + id
                + ", publicKey=" + Arrays.toString(publicKey) + ']';
    }
}
