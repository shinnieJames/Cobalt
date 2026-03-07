package com.github.auties00.cobalt.node.binary;

import com.github.auties00.cobalt.exception.WhatsAppStreamException;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeAttribute;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.SequencedCollection;
import java.util.SequencedMap;

import static com.github.auties00.cobalt.node.binary.NodeTags.*;
import static com.github.auties00.cobalt.node.binary.NodeTokens.*;

/**
 * A utility class responsible for encoding {@link Node} objects into binary format
 * for transmission in the WhatsApp protocol.
 * <p>
 * This encoder implements WhatsApp's proprietary binary protocol that uses token-based
 * compression to reduce message size. The encoding process involves:
 * <ul>
 *   <li>Converting strings to dictionary tokens when possible (using single-byte or multi-dictionary lookup)</li>
 *   <li>Encoding binary data with length prefixes</li>
 *   <li>Efficiently serializing node trees with attributes and children</li>
 *   <li>Supporting various children types: text, binary buffers, JIDs, streams, and child nodes</li>
 * </ul>
 * <p>
 * The encoding format is optimized for small message sizes and includes:
 * <ul>
 *   <li>Token dictionaries (SINGLE_BYTE_TOKENS, DICTIONARY_0-3_TOKENS) for common strings</li>
 *   <li>Variable-length integer encoding (8-bit, 20-bit, 32-bit)</li>
 *   <li>List size encoding (8-bit or 16-bit)</li>
 *   <li>Special encoding for WhatsApp JIDs</li>
 * </ul>
 * <p>
 * This class is thread-safe as all methods are static and operate on provided parameters
 * without shared mutable state.
 *
 * @see Node
 * @see NodeDecoder
 * @see NodeTokens
 * @see NodeTags
 */
public final class NodeEncoder {
    /**
     * VarHandle for writing 16-bit values in big-endian byte order to byte arrays.
     */
    private static final VarHandle SHORT_HANDLE = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.BIG_ENDIAN);

    /**
     * VarHandle for writing 32-bit values in big-endian byte order to byte arrays.
     */
    private static final VarHandle INT_HANDLE = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);

    /**
     * Lookup table for nibble encoding: maps ASCII characters to their 4-bit nibble values.
     * Valid characters are {@code [0-9.-]}, all others map to {@code -1}.
     */
    private static final byte[] NIBBLE_ENCODE = new byte[128];

    /**
     * Lookup table for hex encoding: maps ASCII characters to their 4-bit nibble values.
     * Valid characters are {@code [0-9A-F]}, all others map to {@code -1}.
     */
    private static final byte[] HEX_ENCODE = new byte[128];

    static {
        Arrays.fill(NIBBLE_ENCODE, (byte) -1);
        Arrays.fill(HEX_ENCODE, (byte) -1);
        for (var i = 0; i <= 9; i++) {
            NIBBLE_ENCODE['0' + i] = (byte) i;
            HEX_ENCODE['0' + i] = (byte) i;
        }
        NIBBLE_ENCODE['-'] = 10;
        NIBBLE_ENCODE['.'] = 11;
        for (var i = 0; i < 6; i++) {
            HEX_ENCODE['A' + i] = (byte) (10 + i);
        }
    }

    /**
     * Maximum value for unsigned byte (2^8).
     */
    private static final int UNSIGNED_BYTE_MAX_VALUE = 256;

    /**
     * Maximum value for unsigned short (2^16).
     */
    private static final int UNSIGNED_SHORT_MAX_VALUE = 65536;

    /**
     * Maximum value for 20-bit integer (2^20).
     */
    private static final int INT_20_MAX_VALUE = 1048576;

    /**
     * Private constructor to prevent instantiation of this utility class.
     *
     * @throws UnsupportedOperationException always, as this class should not be instantiated
     */
    private NodeEncoder() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Calculates the total size in bytes required to encode the given node.
     * <p>
     * This includes the message header (1 byte) and the full encoded length of the node
     * including its description, attributes, and children.
     *
     * @param node the node to calculate the size for
     * @return the total number of bytes required to encode the node
     * @throws IllegalArgumentException if the node is too large to encode
     */
    public static int sizeOf(Node node) {
        return 1 + nodeLength(node);
    }

    /**
     * Calculates the length of a node's encoding, excluding the message header.
     *
     * @param input the node to calculate the length for
     * @return the length in bytes
     */
    private static int nodeLength(Node input){
        return listLength(input.size())
               + stringLength(input.description())
               + attributesLength(input.attributes())
               + contentLength(input);
    }

    /**
     * Calculates the number of bytes required to encode a list size.
     * <p>
     * Uses 8-bit encoding (LIST_8) for sizes less than 256, and 16-bit encoding (LIST_16)
     * for sizes less than 65536.
     *
     * @param size the size of the list
     * @return the number of bytes required (2 or 3)
     * @throws IllegalArgumentException if the size exceeds the maximum supported value
     */
    private static int listLength(int size) {
        if (size < UNSIGNED_BYTE_MAX_VALUE) {
            return 2;
        }else if (size < UNSIGNED_SHORT_MAX_VALUE) {
            return 3;
        }else {
            throw new IllegalArgumentException("Cannot calculate list length: overflow");
        }
    }

    /**
     * Calculates the number of bytes required to encode a string.
     * <p>
     * The encoding strategy prioritizes efficiency:
     * <ol>
     *   <li>Empty strings use 2 bytes (BINARY_8 + LIST_EMPTY)</li>
     *   <li>Strings in SINGLE_BYTE_TOKENS dictionary use 1 byte</li>
     *   <li>Strings in DICTIONARY_0-3 use 2 bytes (dictionary tag + index)</li>
     *   <li>Short strings matching {@code [0-9.-]+} use nibble encoding (tag + metadata + packed data)</li>
     *   <li>Short strings matching {@code [0-9A-F]+} use hex encoding (tag + metadata + packed data)</li>
     *   <li>Other strings are UTF-8 encoded with a length prefix</li>
     * </ol>
     *
     * @param input the string to calculate the encoding length for
     * @return the number of bytes required to encode the string
     */
    private static int stringLength(String input){
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
            var packedType = getPackedType(input);
            if (packedType != -1) {
                return 2 + (input.length() + 1) / 2;
            }
        }

        return calculateLength(utf8Length);
    }

    /**
     * Calculates the number of bytes required to encode a binary length prefix.
     *
     * @param input the length value to encode
     * @return the number of bytes required (2, 4, or 5)
     */
    private static int binaryLength(long input) {
        if (input < UNSIGNED_BYTE_MAX_VALUE) {
            return 2;
        }else if (input < INT_20_MAX_VALUE) {
            return 4;
        }else {
            return 5;
        }
    }

    /**
     * Calculates the total number of bytes required to encode a map of node attributes.
     *
     * @param attributes the attributes to encode
     * @return the total number of bytes required
     */
    private static int attributesLength(SequencedMap<String, ? extends NodeAttribute> attributes) {
        var result = 0;
        for (var entry : attributes.entrySet()) {
            result += stringLength(entry.getKey()) + attributeLength(entry.getValue());
        }
        return result;
    }

    /**
     * Calculates the number of bytes required to encode a single node attribute value.
     *
     * @param attribute the attribute to encode
     * @return the number of bytes required
     */
    private static int attributeLength(NodeAttribute attribute){
        return switch (attribute) {
            case NodeAttribute.BytesAttribute(var bytes) -> bytesLength(bytes);
            case NodeAttribute.TextAttribute(var literal) -> stringLength(literal);
            case NodeAttribute.JidAttribute(var jid) -> jidLength(jid);
        };
    }

    /**
     * Calculates the total number of bytes required to encode a collection of child nodes.
     *
     * @param values the child nodes to encode
     * @return the total number of bytes required
     */
    private static int childrenLength(SequencedCollection<Node> values) {
        var length = listLength(values.size());
        for(var value : values) {
            length += nodeLength(value);
        }
        return length;
    }

    /**
     * Calculates the number of bytes required to encode a node's children.
     *
     * @param node the node whose children to calculate
     * @return the number of bytes required
     */
    private static int contentLength(Node node){
        return switch (node) {
            case Node.BytesNode(var _, var _, var bytes) -> bytesLength(bytes);
            case Node.ContainerNode(var _, var _, var children) -> childrenLength(children);
            case Node.EmptyNode _ -> 0;
            case Node.JidNode(var _, var _, var jid) -> jidLength(jid);
            case Node.TextNode(var _, var _, var text) -> stringLength(text);
        };
    }

    /**
     * Calculates the number of bytes required to encode an array of bytes.
     *
     * @param bytes the array of bytes to encode
     * @return the number of bytes required (length prefix + data)
     */
    private static int bytesLength(byte[] bytes){
        return calculateLength(bytes.length);
    }

    /**
     * Calculates the number of bytes required to encode a WhatsApp JID.
     * <p>
     * JIDs can be encoded in four ways:
     * <ul>
     *   <li>JID_FB format: for Messenger JIDs (1 + user string + 2 device + domain string)</li>
     *   <li>JID_INTEROP format: for interop JIDs (1 + user string + 2 device + 2 integrator)</li>
     *   <li>AD_JID format: for device JIDs (1 + 1 domainType + 1 device + user string)</li>
     *   <li>JID_PAIR format: standard format (1 + user string or 1 + server string)</li>
     * </ul>
     *
     * @param jid the JID to encode
     * @return the number of bytes required
     */
    private static int jidLength(Jid jid){
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
     * Calculates the total number of bytes required to encode data with a length prefix.
     *
     * @param length the length of the data
     * @return the number of bytes required (prefix + data)
     */
    private static int calculateLength(int length) {
        return binaryLength(length) + length;
    }

    /**
     * Calculates the total number of bytes required to encode a UTF-8 string.
     *
     * @param input the UTF-8 string to calculate the length for
     * @return the number of bytes required
     */
    private static int calculateUtf8Length(String input) {
        var length = 0;
        if(input == null) {
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

    /**
     * Encodes a node into the provided byte array at the specified offset.
     *
     * @param node   the node to encode
     * @param output the output byte array
     * @param offset the offset in the output array where encoding should start
     * @param length the length of the output array, in bytes, to encode to.
     * @throws WhatsAppStreamException.MalformedNode if the node is shorter/longer than the specified length
     * @return the new offset after writing
     */
    public static int encode(Node node, byte[] output, int offset, int length) {
        output[offset] = 0;
        var result = writeNode(node, output, offset + 1);
        if(result - offset != length) {
            throw new WhatsAppStreamException.MalformedNode();
        }
        return result;
    }

    /**
     * Writes a complete node to the output array.
     * <p>
     * A node consists of:
     * <ol>
     *   <li>List size (number of attributes + 2 for description + children)</li>
     *   <li>Description string</li>
     *   <li>Attributes</li>
     *   <li>Content</li>
     * </ol>
     *
     * @param input the node to write
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    private static int writeNode(Node input, byte[] output, int offset){
        offset = writeList(input.size(), output, offset);
        offset = writeString(input.description(), output, offset);
        offset = writeAttributes(input.attributes(), output, offset);
        offset = writeContent(input, output, offset);
        return offset;
    }

    /**
     * Writes a list size tag and value.
     *
     * @param size the size of the list
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     * @throws IllegalArgumentException if the size exceeds the maximum supported value
     */
    private static int writeList(int size, byte[] output, int offset) {
        if (size < UNSIGNED_BYTE_MAX_VALUE) {
            return writeList8((byte) size, output, offset);
        }else if (size < UNSIGNED_SHORT_MAX_VALUE) {
            return writeList16(size, output, offset);
        }else {
            throw new IllegalArgumentException("Cannot write list: overflow");
        }
    }

    /**
     * Writes an 8-bit list size (LIST_8 tag + size byte).
     *
     * @param size the size of the list
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    private static int writeList8(byte size, byte[] output, int offset) {
        output[offset++] = LIST_8;
        output[offset++] = size;
        return offset;
    }

    /**
     * Writes a 16-bit list size (LIST_16 tag + two size bytes).
     *
     * @param size the size of the list
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    private static int writeList16(int size, byte[] output, int offset) {
        output[offset++] = LIST_16;
        SHORT_HANDLE.set(output, offset, (short) size);
        return offset + 2;
    }

    /**
     * Writes a string using the most efficient encoding method.
     * <p>
     * Encoding priority:
     * <ol>
     *   <li>Empty string → BINARY_8 + LIST_EMPTY</li>
     *   <li>Single-byte token → token index only</li>
     *   <li>Dictionary token → DICTIONARY_X + index</li>
     *   <li>UTF-8 string → binary length prefix + UTF-8 bytes</li>
     * </ol>
     *
     * @param input the string to write
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     * @throws RuntimeException if UTF-8 encoding fails
     */
    private static int writeString(String input, byte[] output, int offset){
        if (input.isEmpty()) {
            output[offset++] = BINARY_8;
            output[offset++] = LIST_EMPTY;
            return offset;
        }

        var singleByteTokenIndex = SINGLE_BYTE_TOKENS.indexOf(input);
        if (singleByteTokenIndex != -1) {
            output[offset++] = (byte) singleByteTokenIndex;
            return offset;
        }

        var dictionary0TokenIndex = DICTIONARY_0_TOKENS.indexOf(input);
        if (dictionary0TokenIndex != -1) {
            output[offset++] = DICTIONARY_0;
            output[offset++] = (byte) dictionary0TokenIndex;
            return offset;
        }

        var dictionary1TokenIndex = DICTIONARY_1_TOKENS.indexOf(input);
        if (dictionary1TokenIndex != -1) {
            output[offset++] = DICTIONARY_1;
            output[offset++] = (byte) dictionary1TokenIndex;
            return offset;
        }

        var dictionary2TokenIndex = DICTIONARY_2_TOKENS.indexOf(input);
        if (dictionary2TokenIndex != -1) {
            output[offset++] = DICTIONARY_2;
            output[offset++] = (byte) dictionary2TokenIndex;
            return offset;
        }

        var dictionary3TokenIndex = DICTIONARY_3_TOKENS.indexOf(input);
        if (dictionary3TokenIndex != -1) {
            output[offset++] = DICTIONARY_3;
            output[offset++] = (byte) dictionary3TokenIndex;
            return offset;
        }

        var utf8Length = calculateUtf8Length(input);
        if (utf8Length < 128) {
            var packedType = getPackedType(input);
            if (packedType != -1) {
                return writePacked(input, packedType, output, offset);
            }
        }

        offset = writeBinary(utf8Length, output, offset);
        var encoded = input.getBytes(StandardCharsets.UTF_8);
        if(encoded.length != utf8Length) {
            throw new InternalError("Utf8 length mismatch");
        }
        System.arraycopy(encoded, 0, output, offset, utf8Length);
        return offset + utf8Length;
    }

    /**
     * Writes a binary length prefix with the appropriate size tag.
     *
     * @param input the length value to write
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    private static int writeBinary(int input, byte[] output, int offset) {
        if (input < UNSIGNED_BYTE_MAX_VALUE) {
            return writeBinary8((byte) input, output, offset);
        }else if (input < INT_20_MAX_VALUE) {
            return writeBinary20(input, output, offset);
        }else {
            return writeBinary32(input, output, offset);
        }
    }

    /**
     * Writes an 8-bit binary length (BINARY_8 tag + length byte).
     *
     * @param input the length value
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    private static int writeBinary8(byte input, byte[] output, int offset) {
        output[offset++] = BINARY_8;
        output[offset++] = input;
        return offset;
    }

    /**
     * Writes a 20-bit binary length (BINARY_20 tag + three length bytes).
     *
     * @param input the length value
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    private static int writeBinary20(int input, byte[] output, int offset) {
        output[offset++] = BINARY_20;
        output[offset++] = (byte) (input >> 16);
        output[offset++] = (byte) (input >> 8);
        output[offset++] = (byte) input;
        return offset;
    }

    /**
     * Writes a 32-bit binary length (BINARY_32 tag + four length bytes).
     *
     * @param input the length value
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    private static int writeBinary32(int input, byte[] output, int offset) {
        output[offset++] = BINARY_32;
        INT_HANDLE.set(output, offset, input);
        return offset + 4;
    }

    /**
     * Determines whether a string can be packed using nibble or hex encoding.
     * <p>
     * Nibble encoding supports characters {@code [0-9.-]} and hex encoding supports
     * characters {@code [0-9A-F]}. Nibble encoding is preferred when applicable.
     *
     * @param input the string to check
     * @return {@link NodeTags#NIBBLE_8} if nibble-encodable, {@link NodeTags#HEX_8} if
     *         hex-encodable, or {@code -1} if neither
     */
    private static byte getPackedType(String input) {
        var nibble = true;
        var hex = true;
        for (var i = 0; i < input.length(); i++) {
            var ch = input.charAt(i);
            if (ch >= 128 || NIBBLE_ENCODE[ch] < 0) {
                nibble = false;
            }
            if (ch >= 128 || HEX_ENCODE[ch] < 0) {
                hex = false;
            }
            if (!nibble && !hex) {
                return -1;
            }
        }
        if (nibble) {
            return NIBBLE_8;
        } else {
            return HEX_8;
        }
    }

    /**
     * Writes a string using nibble or hex packed encoding.
     * <p>
     * Each character is encoded as a 4-bit nibble, with two characters packed per byte.
     * For odd-length strings, the last byte contains the final character in the high
     * nibble and {@code 0xF} as padding in the low nibble.
     *
     * @param input  the string to encode
     * @param tag    the encoding tag ({@link NodeTags#NIBBLE_8} or {@link NodeTags#HEX_8})
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    private static int writePacked(String input, byte tag, byte[] output, int offset) {
        var table = tag == NIBBLE_8 ? NIBBLE_ENCODE : HEX_ENCODE;
        var len = input.length();
        output[offset++] = tag;
        var byteCount = (len + 1) / 2;
        if ((len & 1) == 1) {
            byteCount |= 128;
        }
        output[offset++] = (byte) byteCount;
        var i = 0;
        for (; i + 1 < len; i += 2) {
            output[offset++] = (byte) ((table[input.charAt(i)] << 4) | table[input.charAt(i + 1)]);
        }
        if (i < len) {
            output[offset++] = (byte) ((table[input.charAt(i)] << 4) | 0x0F);
        }
        return offset;
    }

    /**
     * Writes all node attributes as key-value pairs.
     *
     * @param attributes the attributes to write
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    private static int writeAttributes(SequencedMap<String, ? extends NodeAttribute> attributes, byte[] output, int offset) {
        for (var entry : attributes.entrySet()) {
            offset = writeString(entry.getKey(), output, offset);
            offset = writeAttribute(entry.getValue(), output, offset);
        }
        return offset;
    }

    /**
     * Writes a single attribute value.
     *
     * @param attribute the attribute to write
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    private static int writeAttribute(NodeAttribute attribute, byte[] output, int offset) {
        return switch (attribute) {
            case NodeAttribute.BytesAttribute(var buffer) -> writeBytes(buffer, output, offset);
            case NodeAttribute.JidAttribute(var jid) -> writeJid(jid, output, offset);
            case NodeAttribute.TextAttribute(var string) -> writeString(string, output, offset);
        };
    }

    /**
     * Writes the children of a node based on its type.
     *
     * @param content the node whose children to write
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    private static int writeContent(Node content, byte[] output, int offset) {
        return switch (content) {
            case Node.EmptyNode _ -> offset;
            case Node.BytesNode(var _, var _, var buffer) -> writeBytes(buffer, output, offset);
            case Node.ContainerNode(var _, var _, var children) -> writeChildren(children, output, offset);
            case Node.JidNode(var _, var _, var jid) -> writeJid(jid, output, offset);
            case Node.TextNode(var _, var _, var text) -> writeString(text, output, offset);
        };
    }

    /**
     * Writes a collection of child nodes.
     *
     * @param values the child nodes to write
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    private static int writeChildren(SequencedCollection<Node> values, byte[] output, int offset) {
        offset = writeList(values.size(), output, offset);
        for(var value : values) {
            offset = writeNode(value, output, offset);
        }
        return offset;
    }

    /**
     * Writes a byte array with a length prefix.
     *
     * @param buffer the byte array to write
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    private static int writeBytes(byte[] buffer, byte[] output, int offset){
        var length = buffer.length;
        offset = writeBinary(length, output, offset);
        System.arraycopy(buffer, 0, output, offset, length);
        return offset + length;
    }

    /**
     * Writes a WhatsApp JID.
     * <p>
     * Four encoding formats:
     * <ul>
     *   <li>JID_FB: for Messenger JIDs (tag + user + device(u16) + domain)</li>
     *   <li>JID_INTEROP: for interop JIDs (tag + user + device(u16) + integrator(u16))</li>
     *   <li>AD_JID: for device JIDs (tag + domainType + device + user)</li>
     *   <li>JID_PAIR: standard format (tag + user/empty + server)</li>
     * </ul>
     *
     * @param jid the JID to write
     * @param output the output byte array
     * @param offset the current offset in the output array
     * @return the new offset after writing
     */
    private static int writeJid(Jid jid, byte[] output, int offset){
        if (jid.hasMessengerServer()) {
            output[offset++] = JID_FB;
            offset = writeString(jid.user(), output, offset);
            SHORT_HANDLE.set(output, offset, (short) jid.device());
            offset += 2;
            return writeString(jid.server().address(), output, offset);
        } else if (jid.hasInteropServer()) {
            output[offset++] = JID_INTEROP;
            var user = jid.user();
            var dashIndex = user.indexOf('-');
            var integrator = 0;
            var actualUser = user;
            if (dashIndex >= 0) {
                for (var i = 0; i < dashIndex; i++) {
                    integrator = integrator * 10 + (user.charAt(i) - '0');
                }
                actualUser = user.substring(dashIndex + 1);
            }
            offset = writeString(actualUser, output, offset);
            SHORT_HANDLE.set(output, offset, (short) jid.device());
            offset += 2;
            SHORT_HANDLE.set(output, offset, (short) integrator);
            offset += 2;
            return offset;
        } else if (jid.hasDevice()) {
            output[offset++] = AD_JID;
            output[offset++] = (byte) getDomainForServer(jid.server());
            output[offset++] = (byte) jid.device();
            return writeString(jid.user(), output, offset);
        } else {
            output[offset++] = JID_PAIR;
            if(jid.hasUser()) {
                offset = writeString(jid.user(), output, offset);
            }else {
                output[offset++] = LIST_EMPTY;
            }
            return writeString(jid.server().address(), output, offset);
        }
    }

    /**
     * Returns the binary domain type encoding for the given server.
     *
     * @param server the JID server to map
     * @return the domain type value
     */
    private static int getDomainForServer(JidServer server) {
        return switch (server.type()) {
            case LID -> DOMAIN_LID;
            case HOSTED -> DOMAIN_HOSTED;
            case HOSTED_LID -> DOMAIN_HOSTED_LID;
            default -> DOMAIN_WHATSAPP;
        };
    }
}