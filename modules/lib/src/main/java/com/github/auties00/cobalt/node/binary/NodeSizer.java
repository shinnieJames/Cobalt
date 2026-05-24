package com.github.auties00.cobalt.node.binary;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeAttribute;

import java.util.SequencedCollection;
import java.util.SequencedMap;

import static com.github.auties00.cobalt.node.binary.NodeTokens.*;

/**
 * Computes the exact byte count required to encode a {@link Node} in
 * WhatsApp's compact binary stanza format without producing any output.
 *
 * <p>Callers that need to pre-allocate a fixed-size sink (the
 * {@link NodeWriter#toBytes(byte[], int) byte-array},
 * {@link NodeWriter#toBuffer(java.nio.ByteBuffer) ByteBuffer}, and
 * {@link NodeWriter#toSegment(java.lang.foreign.MemorySegment, long)
 * MemorySegment} writer variants) use {@link #sizeOf(Node)} to size the
 * buffer; callers that stream tokens directly to an
 * {@link java.io.OutputStream} also use it to announce the encoded length up
 * front to length-prefixed sinks such as the WhatsApp datagram stream via
 * {@link com.github.auties00.cobalt.socket.datagram.WhatsAppDatagramOutputStream#beginDatagram(byte[], int)}.
 *
 * @implNote
 * This implementation mirrors {@link NodeWriter} branch-for-branch: every
 * encoding decision (which {@link NodeTags} a string takes, which dictionary
 * it falls into, which width a binary or list length prefix uses) is
 * duplicated here so the computed size is exact, not an upper bound. The two
 * paths share {@link NodePackedFormat} for the packed-encoding classifier and
 * {@link NodeTokens} for the dictionary lookups. Computing the length up front
 * lets the single-pass writer target a fixed-size sink with no reallocation.
 *
 * @see NodeWriter
 * @see NodeTags
 * @see NodeTokens
 */
@WhatsAppWebModule(moduleName = "WAWap")
public final class NodeSizer {

    /**
     * Holds the exclusive upper bound for values that fit in an unsigned byte.
     *
     * <p>Serves as the decision threshold between {@link NodeTags#LIST_8} and
     * {@link NodeTags#LIST_16} for list sizes, and between
     * {@link NodeTags#BINARY_8} and {@link NodeTags#BINARY_20} for payload
     * lengths.
     */
    private static final int UNSIGNED_BYTE_MAX_VALUE = 256;

    /**
     * Holds the exclusive upper bound for values that fit in an unsigned
     * short.
     *
     * <p>Serves as the decision threshold above which a list cannot be
     * encoded; the wire format has no 32-bit list size variant.
     */
    private static final int UNSIGNED_SHORT_MAX_VALUE = 65536;

    /**
     * Holds the exclusive upper bound for values that fit in 20 bits.
     *
     * <p>Serves as the decision threshold between {@link NodeTags#BINARY_20}
     * and {@link NodeTags#BINARY_32} for payload lengths.
     */
    private static final int INT_20_MAX_VALUE = 1048576;

    /**
     * Prevents instantiation of this stateless utility.
     *
     * @throws UnsupportedOperationException always
     */
    private NodeSizer() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Returns the exact byte count required to encode the supplied node,
     * including the leading flags byte.
     *
     * <p>The flags byte is the leading {@code 0x00} (no compression) that
     * {@link NodeWriter#writeNode(Node)} emits ahead of the node body, so the
     * returned count is what a caller allocates for a complete stanza buffer
     * or announces as the length-prefixed frame size on the wire. A
     * single-shot {@code byte[]} sink passes this value to
     * {@link NodeWriter#toBytes(byte[], int)} with an offset of zero.
     *
     * @param node the node to size
     * @return the byte count required to encode the node, including the
     *         leading flags byte
     * @throws IllegalArgumentException if the node exceeds the format's length
     *         limits (a list of more than {@code 65535} entries or any size
     *         header that overflows {@link #UNSIGNED_SHORT_MAX_VALUE})
     */
    @WhatsAppWebExport(moduleName = "WAWap", exports = "encodeStanza",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static int sizeOf(Node node) {
        return 1 + nodeLength(node);
    }

    /**
     * Returns the byte count of a single node's encoding without the leading
     * flags byte.
     *
     * <p>The count covers the {@link NodeTags#LIST_8} or
     * {@link NodeTags#LIST_16} size header, the description token, every
     * attribute key value pair, and the content slot. It is reached
     * recursively from {@link #childrenLength(SequencedCollection)} and from
     * {@link #sizeOf(Node)} once the flags byte has been accounted for.
     *
     * @param input the node to size
     * @return the byte count required to encode the node body
     */
    static int nodeLength(Node input) {
        return listLength(input.size())
               + stringLength(input.description())
               + attributesLength(input.attributes())
               + contentLength(input);
    }

    /**
     * Returns the byte count required to write a list size header.
     *
     * <p>Used both for a node's leading size header (description plus two slots
     * per attribute plus an optional content slot) and for a child-list's
     * length prefix.
     *
     * @param size the list size to encode
     * @return {@code 2} for an 8-bit length ({@link NodeTags#LIST_8} plus the
     *         byte), {@code 3} for a 16-bit length ({@link NodeTags#LIST_16}
     *         plus the short)
     * @throws IllegalArgumentException if {@code size} is at or above
     *         {@link #UNSIGNED_SHORT_MAX_VALUE}
     */
    static int listLength(int size) {
        if (size < UNSIGNED_BYTE_MAX_VALUE) {
            return 2;
        } else if (size < UNSIGNED_SHORT_MAX_VALUE) {
            return 3;
        } else {
            throw new IllegalArgumentException("Cannot calculate list length: overflow");
        }
    }

    /**
     * Returns the byte count required to encode a string under the most
     * efficient applicable strategy.
     *
     * <p>The strategy order mirrors {@link NodeWriter} step-for-step: empty
     * string ({@link NodeTags#BINARY_8} plus {@link NodeTags#LIST_EMPTY}),
     * single-byte token, then each extension dictionary in turn, then a packed
     * nibble or hex shape for short strings ({@code utf8Length < 128}) whose
     * alphabet allows it, and finally a length-prefixed UTF-8 blob.
     *
     * @implNote
     * This implementation computes the UTF-8 length once via
     * {@link #calculateUtf8Length(String)} so the packed-shape probe and the
     * eventual fall-through to a length-prefixed UTF-8 blob share the single
     * pass.
     *
     * @param input the string to size
     * @return the byte count required to encode the string
     */
    static int stringLength(String input) {
        if (input.isEmpty()) {
            return 2;
        }

        var singleByteTokenIndex = SINGLE_BYTE_TOKENS.indexOf(input);
        if (singleByteTokenIndex != -1) {
            return 1;
        }

        var dictionary0TokenIndex = DICTIONARY_0_TOKENS.indexOf(input);
        if (dictionary0TokenIndex != -1) {
            return 2;
        }

        var dictionary1TokenIndex = DICTIONARY_1_TOKENS.indexOf(input);
        if (dictionary1TokenIndex != -1) {
            return 2;
        }

        var dictionary2TokenIndex = DICTIONARY_2_TOKENS.indexOf(input);
        if (dictionary2TokenIndex != -1) {
            return 2;
        }

        var dictionary3TokenIndex = DICTIONARY_3_TOKENS.indexOf(input);
        if (dictionary3TokenIndex != -1) {
            return 2;
        }

        var utf8Length = calculateUtf8Length(input);
        if (utf8Length < 128) {
            var packedType = NodePackedFormat.getPackedType(input);
            if (packedType != -1) {
                return 2 + (input.length() + 1) / 2;
            }
        }

        return calculateLength(utf8Length);
    }

    /**
     * Returns the byte count required to write a binary-blob length prefix
     * header.
     *
     * <p>Excludes the payload bytes themselves; {@link #calculateLength(int)}
     * combines this with the payload length to size the full prefixed blob.
     *
     * @param input the length value to encode
     * @return {@code 2} for an 8-bit length, {@code 4} for a 20-bit length
     *         (one tag plus three length bytes), {@code 5} for a 32-bit length
     *         (one tag plus four length bytes)
     */
    static int binaryLength(long input) {
        if (input < UNSIGNED_BYTE_MAX_VALUE) {
            return 2;
        } else if (input < INT_20_MAX_VALUE) {
            return 4;
        } else {
            return 5;
        }
    }

    /**
     * Returns the byte count required to encode an attribute map.
     *
     * <p>Equivalent to summing {@link #stringLength(String)} for every key and
     * {@link #attributeLength(NodeAttribute)} for every value. There is no
     * surrounding list header; the attribute pair count is folded into the
     * enclosing node's size header by {@link Node#size()}.
     *
     * @param attributes the attribute map to size
     * @return the byte count required to encode every key value pair
     */
    static int attributesLength(SequencedMap<String, ? extends NodeAttribute> attributes) {
        var result = 0;
        for (var entry : attributes.entrySet()) {
            result += stringLength(entry.getKey()) + attributeLength(entry.getValue());
        }
        return result;
    }

    /**
     * Returns the byte count required to encode a single attribute value under
     * its variant-specific shape.
     *
     * <p>{@link NodeAttribute.TextAttribute} uses the full string compression
     * ladder (token, dictionary, packed, UTF-8);
     * {@link NodeAttribute.BytesAttribute} always uses a length-prefixed binary
     * shape; {@link NodeAttribute.JidAttribute} picks one of four JID shapes
     * based on the server and device through {@link #jidLength(Jid)}.
     *
     * @param attribute the attribute to size
     * @return the byte count required to encode the attribute value
     */
    static int attributeLength(NodeAttribute attribute) {
        return switch (attribute) {
            case NodeAttribute.BytesAttribute(var bytes) -> bytesLength(bytes);
            case NodeAttribute.TextAttribute(var literal) -> stringLength(literal);
            case NodeAttribute.JidAttribute(var jid) -> jidLength(jid);
        };
    }

    /**
     * Returns the byte count required to encode a list of child nodes
     * including its leading list size header.
     *
     * <p>Sizes the content slot of a {@link Node.ContainerNode}.
     *
     * @param values the child nodes to size
     * @return the byte count required to encode the entire child list
     */
    static int childrenLength(SequencedCollection<Node> values) {
        var length = listLength(values.size());
        for (var value : values) {
            length += nodeLength(value);
        }
        return length;
    }

    /**
     * Returns the byte count required to encode the content slot of a node.
     *
     * <p>Dispatches on the concrete {@link Node} variant: text uses the string
     * ladder, JID uses the JID ladder, bytes uses the binary ladder, children
     * recurse through {@link #childrenLength(SequencedCollection)}, and
     * {@link Node.EmptyNode} contributes nothing.
     *
     * @param node the node whose content slot to size
     * @return the byte count required to encode the content slot
     */
    static int contentLength(Node node) {
        return switch (node) {
            case Node.BytesNode(var _, var _, var bytes) -> bytesLength(bytes);
            case Node.ContainerNode(var _, var _, var children) -> childrenLength(children);
            case Node.EmptyNode _ -> 0;
            case Node.JidNode(var _, var _, var jid) -> jidLength(jid);
            case Node.TextNode(var _, var _, var text) -> stringLength(text);
        };
    }

    /**
     * Returns the byte count required to encode a binary blob with its length
     * prefix.
     *
     * <p>Adds the payload length to the prefix width chosen by
     * {@link #binaryLength(long)}.
     *
     * @param bytes the blob to size
     * @return the byte count required to encode the prefixed blob
     */
    static int bytesLength(byte[] bytes) {
        return calculateLength(bytes.length);
    }

    /**
     * Returns the byte count required to encode a JID under the wire shape
     * appropriate for its server and device.
     *
     * <p>Picks between {@link NodeTags#JID_FB} (Messenger),
     * {@link NodeTags#JID_INTEROP} (Meta cross-platform),
     * {@link NodeTags#AD_JID} (any JID with a device suffix) and
     * {@link NodeTags#JID_PAIR} (device-less canonical JIDs) using the same
     * predicate cascade as {@link NodeWriter}. The interop branch splits a
     * {@code "integrator-user"} compound on {@code '-'} so only the user
     * component is encoded as a string and the integrator number is sent as a
     * 16-bit short.
     *
     * @param jid the JID to size
     * @return the byte count required to encode the JID, including the leading
     *         tag byte
     */
    static int jidLength(Jid jid) {
        if (jid.hasMessengerServer()) {
            return 1 + stringLength(jid.user()) + 2 + stringLength(jid.server().address());
        } else if (jid.hasInteropServer()) {
            var user = jid.user();
            var dashIndex = user.indexOf('-');
            var actualUser = dashIndex >= 0 ? user.substring(dashIndex + 1) : user;
            return 1 + stringLength(actualUser) + 2 + 2;
        } else if (jid.hasDevice()) {
            return 3 + stringLength(jid.user());
        } else {
            return 1 + (jid.hasUser() ? stringLength(jid.user()) : 1) + stringLength(jid.server().address());
        }
    }

    /**
     * Returns the byte count required to encode a payload of the supplied
     * length together with its binary length prefix.
     *
     * <p>Combines the prefix width from {@link #binaryLength(long)} with the
     * raw payload length.
     *
     * @param length the payload length in bytes
     * @return the byte count required to encode the prefixed payload
     */
    static int calculateLength(int length) {
        return binaryLength(length) + length;
    }

    /**
     * Returns the byte count of a string when encoded as UTF-8.
     *
     * <p>Used twice during sizing: as the candidate length for the
     * length-prefixed blob fall-through, and as the {@code utf8Length < 128}
     * gate that decides whether a string is short enough to attempt a packed
     * encoding. A {@code null} input returns zero so callers do not need a
     * guard.
     *
     * @implNote
     * This implementation walks the {@code char[]} and counts UTF-8 bytes
     * inline rather than calling
     * {@code input.getBytes(StandardCharsets.UTF_8).length}, avoiding the
     * throw-away allocation that the one-liner would incur for every string
     * the encoder considers. High surrogates contribute four bytes and consume
     * a low surrogate from the next iteration.
     *
     * @param input the string to measure, possibly {@code null}
     * @return the UTF-8 byte length, or {@code 0} when {@code input} is
     *         {@code null}
     */
    static int calculateUtf8Length(String input) {
        var length = 0;
        if (input == null) {
            return length;
        }

        var len = input.length();
        for (var i = 0; i < len; i++) {
            var ch = input.charAt(i);
            if (ch <= 0x7F) {
                length++;
            } else if (ch <= 0x7FF) {
                length += 2;
            } else if (Character.isHighSurrogate(ch)) {
                length += 4;
                i++;
            } else {
                length += 3;
            }
        }
        return length;
    }
}
