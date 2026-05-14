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
 * <p>Callers that need to pre-allocate a fixed-size buffer (for example
 * the byte-array variant of {@link NodeWriter}) use {@link #sizeOf(Node)}
 * to size the buffer; streaming encoders that write tokens directly to
 * an {@link java.io.OutputStream} do not need this class.
 *
 * <p>The traversal mirrors {@link NodeWriter} exactly: every encoding
 * decision (which {@link NodeTags} a string takes, whether the string is
 * dictionary-tokenised or packed as nibble/hex, which width a binary or
 * list length prefix uses) is applied here so the computed size matches
 * the bytes the encoder will produce.
 *
 * <p>The class is a stateless utility with only static methods.
 *
 * @see NodeWriter
 * @see NodeTags
 * @see NodeTokens
 */
@WhatsAppWebModule(moduleName = "WAWap")
public final class NodeSizer {

    /**
     * Exclusive upper bound for values that fit in an unsigned byte.
     */
    private static final int UNSIGNED_BYTE_MAX_VALUE = 256;

    /**
     * Exclusive upper bound for values that fit in an unsigned short.
     */
    private static final int UNSIGNED_SHORT_MAX_VALUE = 65536;

    /**
     * Exclusive upper bound for values that fit in 20 bits.
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
     * @param node the node to size
     * @return the byte count required to encode the node
     * @throws IllegalArgumentException if the node exceeds the format's
     *         length limits
     */
    @WhatsAppWebExport(moduleName = "WAWap", exports = "encodeStanza",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static int sizeOf(Node node) {
        return 1 + nodeLength(node);
    }

    /**
     * Returns the length of a single node's encoding without the leading
     * flags byte.
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
     * @param size the list size
     * @return {@code 2} for an 8 bit length, {@code 3} for a 16 bit length
     * @throws IllegalArgumentException if the size exceeds the 16 bit
     *         maximum
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
     * Returns the byte count required to write a binary length prefix
     * header.
     *
     * @param input the length value to encode
     * @return {@code 2} for 8 bit, {@code 4} for 20 bit, {@code 5} for 32 bit
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
     * Returns the byte count required to encode a single attribute value.
     *
     * @param attribute the attribute to size
     * @return the byte count required to encode the attribute
     */
    static int attributeLength(NodeAttribute attribute) {
        return switch (attribute) {
            case NodeAttribute.BytesAttribute(var bytes) -> bytesLength(bytes);
            case NodeAttribute.TextAttribute(var literal) -> stringLength(literal);
            case NodeAttribute.JidAttribute(var jid) -> jidLength(jid);
        };
    }

    /**
     * Returns the byte count required to encode a list of child nodes,
     * including its leading list size header.
     *
     * @param values the child nodes to size
     * @return the byte count required to encode the list
     */
    static int childrenLength(SequencedCollection<Node> values) {
        var length = listLength(values.size());
        for (var value : values) {
            length += nodeLength(value);
        }
        return length;
    }

    /**
     * Returns the byte count required to encode the content slot of a
     * node.
     *
     * @param node the node whose content to size
     * @return the byte count required to encode the content
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
     * Returns the byte count required to encode a binary blob with its
     * length prefix.
     *
     * @param bytes the blob to size
     * @return the byte count required to encode the blob
     */
    static int bytesLength(byte[] bytes) {
        return calculateLength(bytes.length);
    }

    /**
     * Returns the byte count required to encode a JID under the shape
     * appropriate for its server and device.
     *
     * @param jid the JID to size
     * @return the byte count required to encode the JID
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
     * @param length the payload length in bytes
     * @return the byte count required to encode the prefixed payload
     */
    static int calculateLength(int length) {
        return binaryLength(length) + length;
    }

    /**
     * Returns the byte count of a string when encoded as UTF-8.
     *
     * @param input the string to measure, may be {@code null}
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