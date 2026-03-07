package com.github.auties00.cobalt.node.binary;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeAttribute;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.Inflater;
import java.util.zip.DataFormatException;

import static com.github.auties00.cobalt.node.binary.NodeTags.*;
import static com.github.auties00.cobalt.node.binary.NodeTokens.*;

/**
 * A decoder for deserializing WhatsApp protocol nodes from binary ByteBuffer data.
 * <p>
 * This decoder implements the WhatsApp binary protocol specification for deserializing
 * node-based data structures used in WhatsApp communication. It handles various node types,
 * attributes, and children formats including compressed data, JID pairs, binary data,
 * and tokenized strings.
 * <p>
 * The decoder supports:
 * <ul>
 *     <li>Compressed and uncompressed data using DEFLATE algorithm</li>
 *     <li>Multiple binary data formats (8-bit, 20-bit, and 32-bit size prefixes)</li>
 *     <li>Packed hexadecimal and nibble-encoded strings</li>
 *     <li>Dictionary-based token resolution for efficient string encoding</li>
 *     <li>JID parsing for user and device identification</li>
 *     <li>Nested node structures with attributes and child nodes</li>
 * </ul>
 * <p>
 * Instances are obtained via the {@link #of(ByteBuffer)} factory method, which
 * automatically selects the appropriate implementation based on the compression
 * flag in the data header.
 * <p>
 * Usage example:
 * <pre>{@code
 * ByteBuffer buffer = ByteBuffer.wrap(encodedData);
 * NodeDecoder decoder = NodeDecoder.of(buffer);
 * Node node = decoder.decode();
 * }</pre>
 *
 * @see Node
 * @see NodeAttribute
 * @see NodeEncoder
 * @see NodeTokens
 * @see NodeTags
 */
public sealed abstract class NodeDecoder implements AutoCloseable {
    /**
     * VarHandle for reading 16-bit values in big-endian byte order from byte arrays.
     */
    private static final VarHandle SHORT_HANDLE = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.BIG_ENDIAN);

    /**
     * VarHandle for reading 32-bit values in big-endian byte order from byte arrays.
     */
    private static final VarHandle INT_HANDLE = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);

    /**
     * Alphabet used for decoding nibble-encoded strings (4-bit per character).
     * Contains digits, hyphen, period, and special characters.
     */
    private static final char[] NIBBLE_ALPHABET = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '.', '�', '�', '�', '�'};

    /**
     * Alphabet used for decoding hexadecimal-encoded strings (4-bit per character).
     * Contains standard hexadecimal digits 0-9 and A-F.
     */
    private static final char[] HEX_ALPHABET = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    /**
     * Maximum size of the temporary buffer used for decompression operations.
     */
    private static final int DECOMPRESSION_BUFFER_SIZE = 8192;

    /**
     * The source ByteBuffer containing the encoded node data.
     */
    final ByteBuffer source;

    /**
     * Constructs a new {@code NodeDecoder} backed by the given source buffer.
     *
     * @param source the ByteBuffer containing the encoded node data
     */
    private NodeDecoder(ByteBuffer source) {
        this.source = source;
    }

    /**
     * Creates a new {@code NodeDecoder} for the provided ByteBuffer.
     * <p>
     * The factory reads the first byte's compression flag (bit 2) to determine
     * whether the data is DEFLATE-compressed. If compression is detected, a
     * decompressing decoder is returned; otherwise, a direct-read decoder is
     * returned.
     *
     * @param source the ByteBuffer containing the encoded node data
     * @return a {@code NodeDecoder} appropriate for the data's compression mode
     */
    public static NodeDecoder of(ByteBuffer source) {
        var flags = source.get() & 0xFF;
        if ((flags & 2) != 0) {
            return new Compressed(source);
        } else {
            return new Uncompressed(source);
        }
    }

    /**
     * Decodes a node from the ByteBuffer.
     *
     * @return the decoded {@link Node} object representing the node structure
     * @throws IOException if an I/O error occurs while reading or decompressing data
     */
    public final Node decode() throws IOException {
        return readNode();
    }

    /**
     * Checks if there is more data available to be processed.
     *
     * @return {@code true} if more data is available to read, {@code false} otherwise
     */
    public abstract boolean hasData();

    /**
     * Reads a single byte from the underlying data source.
     *
     * @return the next byte value (0-255)
     * @throws IOException if an I/O error occurs or end of data is reached
     */
    abstract int read() throws IOException;

    /**
     * Reads a 16-bit unsigned value in big-endian byte order from the underlying data source.
     *
     * @return the next 16-bit value (0-65535)
     * @throws IOException if an I/O error occurs or insufficient data is available
     */
    abstract int readShort() throws IOException;

    /**
     * Reads a 32-bit signed value in big-endian byte order from the underlying data source.
     *
     * @return the next 32-bit value
     * @throws IOException if an I/O error occurs or insufficient data is available
     */
    abstract int readInt() throws IOException;

    /**
     * Reads the specified number of bytes into a new byte array from the underlying data source.
     *
     * @param length the number of bytes to read
     * @return a byte array containing the read data
     * @throws IOException if an I/O error occurs or insufficient data is available
     */
    abstract byte[] readBytes(int length) throws IOException;

    /**
     * Reads and decodes a complete node from the data source.
     * <p>
     * The node structure consists of:
     * <ul>
     *     <li>Size indicator (determines number of attributes and children presence)</li>
     *     <li>Description string (optional)</li>
     *     <li>Attributes as key-value pairs (each pair consumes 2 size units)</li>
     *     <li>Content (present if size is odd after accounting for attributes)</li>
     * </ul>
     *
     * @return the decoded {@link Node} which may be an EmptyNode, TextNode, BufferNode,
     *         JidNode, ContainerNode, or null
     * @throws IOException if an I/O error occurs during reading or decoding
     */
    private Node readNode() throws IOException {
        var size = readNodeSize();
        if(size == 0) {
            throw new IOException("Unexpected empty node");
        }

        var description = readString();
        var attrs = readAttributes(size - 1);

        if((size & 1) == 1) {
            return new Node.EmptyNode(description, attrs);
        }

        var tag = (byte) read();
        return switch (tag) {
            case LIST_EMPTY -> new Node.EmptyNode(description, attrs);
            case JID_INTEROP -> new Node.JidNode(description, attrs, readInteropJid());
            case JID_FB -> new Node.JidNode(description, attrs, readFbJid());
            case AD_JID -> new Node.JidNode(description, attrs, readAdJid());
            case LIST_8 -> new Node.ContainerNode(description, attrs, readList8());
            case LIST_16 -> new Node.ContainerNode(description, attrs, readList16());
            case JID_PAIR -> new Node.JidNode(description, attrs, readJidPair());
            case HEX_8 -> new Node.TextNode(description, attrs, readPacked(HEX_ALPHABET));
            case BINARY_8 -> new Node.BytesNode(description, attrs, readBinary8());
            case BINARY_20 -> new Node.BytesNode(description, attrs, readBinary20());
            case BINARY_32 -> new Node.BytesNode(description, attrs, readBinary32());
            case NIBBLE_8 -> new Node.TextNode(description, attrs, readPacked(NIBBLE_ALPHABET));
            case DICTIONARY_0 -> new Node.TextNode(description, attrs, readDictionaryToken(DICTIONARY_0_TOKENS));
            case DICTIONARY_1 -> new Node.TextNode(description, attrs, readDictionaryToken(DICTIONARY_1_TOKENS));
            case DICTIONARY_2 -> new Node.TextNode(description, attrs, readDictionaryToken(DICTIONARY_2_TOKENS));
            case DICTIONARY_3 -> new Node.TextNode(description, attrs, readDictionaryToken(DICTIONARY_3_TOKENS));
            default -> {
                var index = tag & 0xFF;
                if (index >= 240) {
                    throw new IOException("Unexpected tag in node content: " + index);
                }
                yield new Node.TextNode(description, attrs, readSingleByteToken(tag));
            }
        };
    }

    /**
     * Reads the size indicator that determines the node structure.
     * <p>
     * Supports two size formats:
     * <ul>
     *     <li>LIST_8: 8-bit size (0-255)</li>
     *     <li>LIST_16: 16-bit size (0-65535)</li>
     * </ul>
     *
     * @return the size value indicating number of elements in the node structure
     * @throws IOException if an I/O error occurs
     * @throws IllegalStateException if an unexpected size token is encountered
     */
    private int readNodeSize() throws IOException {
        var token = (byte) read();
        return switch (token) {
            case LIST_8 -> read() & 0xFF;
            case LIST_16 -> readShort();
            default -> throw new IllegalStateException("Unexpected value: " + token);
        };
    }

    /**
     * Reads and decodes a string based on its encoding tag.
     * <p>
     * Supports multiple string encoding formats including packed hexadecimal,
     * nibble encoding, binary data, dictionary tokens, and single-byte tokens.
     *
     * @return the decoded string, or null if LIST_EMPTY tag is encountered
     * @throws IOException if an I/O error occurs during reading or decoding
     */
    private String readString() throws IOException {
        var tag = (byte) read();
        return switch (tag) {
            case LIST_EMPTY -> null;
            case HEX_8 -> readPacked(HEX_ALPHABET);
            case NIBBLE_8 -> readPacked(NIBBLE_ALPHABET);
            case BINARY_8 -> new String(readBinary8(), StandardCharsets.UTF_8);
            case BINARY_20 -> new String(readBinary20(), StandardCharsets.UTF_8);
            case BINARY_32 -> new String(readBinary32(), StandardCharsets.UTF_8);
            case DICTIONARY_0 -> readDictionaryToken(DICTIONARY_0_TOKENS);
            case DICTIONARY_1 -> readDictionaryToken(DICTIONARY_1_TOKENS);
            case DICTIONARY_2 -> readDictionaryToken(DICTIONARY_2_TOKENS);
            case DICTIONARY_3 -> readDictionaryToken(DICTIONARY_3_TOKENS);
            default -> {
                var index = tag & 0xFF;
                if (index >= 240) {
                    throw new IOException("Unexpected tag in string position: " + index);
                }
                yield readSingleByteToken(tag);
            }
        };
    }

    /**
     * Reads binary data with an 8-bit size prefix (up to 255 bytes).
     *
     * @return a byte array containing the read data
     * @throws IOException if an I/O error occurs
     */
    private byte[] readBinary8() throws IOException {
        var size = read() & 0xFF;
        return readBytes(size);
    }

    /**
     * Reads binary data with a 20-bit size prefix (up to 1,048,575 bytes).
     * <p>
     * Size is encoded in big-endian format across 3 bytes.
     *
     * @return a byte array containing the read data
     * @throws IOException if an I/O error occurs
     */
    private byte[] readBinary20() throws IOException {
        var size = ((read() & 0x0F) << 16)
                   | (read() << 8)
                   | read();
        return readBytes(size);
    }

    /**
     * Reads binary data with a 32-bit size prefix (up to 2,147,483,647 bytes).
     * <p>
     * Size is encoded in big-endian format across 4 bytes.
     *
     * @return a byte array containing the read data
     * @throws IOException if an I/O error occurs
     */
    private byte[] readBinary32() throws IOException {
        return readBytes(readInt());
    }

    /**
     * Reads a token from a specified dictionary using an 8-bit index.
     * <p>
     * Dictionaries provide efficient string encoding by mapping frequently used
     * strings to single-byte indices.
     *
     * @param dictionary the token dictionary to use for lookup
     * @return the string value associated with the read index
     * @throws IOException if an I/O error occurs
     */
    private String readDictionaryToken(NodeTokens dictionary) throws IOException {
        var index = read() & 0xFF;
        return dictionary.get(index);
    }

    /**
     * Reads a single-byte token from the global single-byte token dictionary.
     * <p>
     * Used for very common strings that can be represented by a single byte.
     *
     * @param tag the byte tag representing the token index
     * @return the string value associated with the token
     */
    private String readSingleByteToken(byte tag) {
        var index = tag & 0xFF;
        return SINGLE_BYTE_TOKENS.get(index);
    }

    /**
     * Reads a sequenced map of attributes.
     * <p>
     * Each attribute consists of a key-value pair, consuming 2 size units.
     * The order of attributes is preserved in the returned map.
     *
     * @param size the number of remaining size units (must be even for complete attributes)
     * @return a sequenced map of attribute keys to {@link NodeAttribute} values
     * @throws IOException if an I/O error occurs during reading
     */
    private SequencedMap<String, NodeAttribute> readAttributes(int size) throws IOException {
        var attributes = new LinkedHashMap<String, NodeAttribute>(size / 2);
        while (size >= 2) {
            var key = readString();
            var value = readAttribute();
            attributes.put(key, value);
            size -= 2;
        }
        return attributes;
    }

    /**
     * Reads and decodes a single attribute value.
     * <p>
     * Attributes can be text, bytes, or JID values, encoded using various formats
     * similar to node children encoding.
     *
     * @return a {@link NodeAttribute} object representing the attribute value, or null if empty
     * @throws IOException if an I/O error occurs
     * @throws IllegalStateException if unexpected list tags (LIST_8 or LIST_16) are encountered
     */
    private NodeAttribute readAttribute() throws IOException {
        var tag = (byte) read();
        return switch (tag) {
            case LIST_EMPTY -> null;
            case JID_INTEROP -> new NodeAttribute.JidAttribute(readInteropJid());
            case JID_FB -> new NodeAttribute.JidAttribute(readFbJid());
            case AD_JID -> new NodeAttribute.JidAttribute(readAdJid());
            case LIST_8 -> {
                readList8();
                yield null;
            }
            case LIST_16 -> {
                readList16();
                yield null;
            }
            case JID_PAIR -> new NodeAttribute.JidAttribute(readJidPair());
            case HEX_8 -> new NodeAttribute.TextAttribute(readPacked(HEX_ALPHABET));
            case BINARY_8 -> new NodeAttribute.BytesAttribute(readBinary8());
            case BINARY_20 -> new NodeAttribute.BytesAttribute(readBinary20());
            case BINARY_32 -> new NodeAttribute.BytesAttribute(readBinary32());
            case NIBBLE_8 -> new NodeAttribute.TextAttribute(readPacked(NIBBLE_ALPHABET));
            case DICTIONARY_0 -> new NodeAttribute.TextAttribute(readDictionaryToken(DICTIONARY_0_TOKENS));
            case DICTIONARY_1 -> new NodeAttribute.TextAttribute(readDictionaryToken(DICTIONARY_1_TOKENS));
            case DICTIONARY_2 -> new NodeAttribute.TextAttribute(readDictionaryToken(DICTIONARY_2_TOKENS));
            case DICTIONARY_3 -> new NodeAttribute.TextAttribute(readDictionaryToken(DICTIONARY_3_TOKENS));
            default -> {
                var index = tag & 0xFF;
                if (index >= 240) {
                    throw new IOException("Unexpected tag in attribute position: " + index);
                }
                yield new NodeAttribute.TextAttribute(readSingleByteToken(tag));
            }
        };
    }

    /**
     * Reads a sequence of nodes with an 8-bit length prefix (up to 255 nodes).
     *
     * @return a sequence of decoded {@link Node} objects
     * @throws IOException if an I/O error occurs
     */
    private SequencedCollection<Node> readList8() throws IOException {
        var length = read() & 0xFF;
        return readList(length);
    }

    /**
     * Reads a sequence of nodes with a 16-bit length prefix (up to 65,535 nodes).
     * <p>
     * Length is encoded in big-endian format across 2 bytes.
     *
     * @return a sequence of decoded {@link Node} objects
     * @throws IOException if an I/O error occurs
     */
    private SequencedCollection<Node> readList16() throws IOException {
        return readList(readShort());
    }

    /**
     * Reads a sequence of nodes with the specified size.
     * <p>
     * Each node in the list is decoded sequentially.
     *
     * @param size the number of nodes to read
     * @return a sequence of decoded {@link Node} objects
     * @throws IOException if an I/O error occurs during reading or decoding
     */
    private SequencedCollection<Node> readList(int size) throws IOException {
        var results = new ArrayList<Node>(size);
        for (var index = 0; index < size; index++) {
            var node = readNode();
            results.add(node);
        }
        return results;
    }

    /**
     * Reads a packed string encoded using the specified alphabet.
     * <p>
     * Packed encoding stores two characters per byte, with each character represented
     * by 4 bits (nibble). The first byte contains metadata:
     * <ul>
     *     <li>Bit 7: Start offset (0 or 1)</li>
     *     <li>Bits 0-6: End position</li>
     * </ul>
     * If the start offset is 1, the last character is stored in the high nibble
     * of an additional byte.
     *
     * @param alphabet the character alphabet to use for decoding nibbles (must have 16 elements)
     * @return the decoded string
     * @throws IOException if an I/O error occurs
     */
    private String readPacked(char[] alphabet) throws IOException {
        var token = read() & 0xFF;
        var start = token >>> 7;
        var end = token & 127;
        var string = new char[2 * end - start];
        for(var index = 0; index < string.length - 1; index += 2) {
            token = read() & 0xFF;
            string[index] = alphabet[token >>> 4];
            string[index + 1] = alphabet[15 & token];
        }
        if (start != 0) {
            token = read() & 0xFF;
            string[string.length - 1] = alphabet[token >>> 4];
        }
        return String.valueOf(string);
    }

    /**
     * Reads a JID pair consisting of a user and server component.
     * <p>
     * A JID pair represents a WhatsApp user or group identifier. If the user
     * component is null, a server-only JID is created.
     *
     * @return a {@link Jid} object representing the user or group
     * @throws IOException if an I/O error occurs
     * @throws NullPointerException if the server component is null (malformed pair)
     */
    private Jid readJidPair() throws IOException {
        var user = readString();
        var server = JidServer.of(Objects.requireNonNull(readString(), "Malformed value pair: no server"));
        return user == null ? Jid.of(server) : Jid.of(user, server);
    }

    /**
     * Reads an AD JID which includes domain type and device identifiers.
     * <p>
     * AD JIDs are used for multi-device WhatsApp accounts and contain:
     * <ul>
     *     <li>Domain type (8-bit): determines the server domain</li>
     *     <li>Device ID (8-bit)</li>
     *     <li>User identifier string</li>
     * </ul>
     * The domain type is mapped to a {@link JidServer} as follows:
     * <ul>
     *     <li>{@code 0} → {@code s.whatsapp.net} (WHATSAPP)</li>
     *     <li>{@code 1} → {@code lid} (LID)</li>
     *     <li>{@code 129} → {@code hosted.lid} (HOSTED_LID)</li>
     *     <li>Even values with bit 7 set → {@code hosted} (HOSTED)</li>
     * </ul>
     *
     * @return a {@link Jid} object representing the device-specific user identifier
     * @throws IOException if an I/O error occurs or the domain type is invalid
     */
    private Jid readAdJid() throws IOException {
        var domainType = read() & 0xFF;
        var device = read() & 0xFF;
        var user = readString();
        var server = switch (domainType) {
            case DOMAIN_WHATSAPP -> JidServer.user();
            case DOMAIN_LID -> JidServer.lid();
            case DOMAIN_HOSTED_LID -> JidServer.hostedLid();
            default -> {
                if ((domainType & 1) == 0 && (domainType & DOMAIN_HOSTED) != 0) {
                    yield JidServer.hosted();
                }
                throw new IOException("Invalid AD_JID domain type: " + domainType);
            }
        };
        return Jid.of(user, server, device, 0);
    }

    /**
     * Reads a Facebook Messenger JID (tag 246).
     * <p>
     * FB JIDs represent Messenger users participating in cross-platform conversations
     * and contain:
     * <ul>
     *     <li>User identifier string</li>
     *     <li>Device ID (16-bit)</li>
     *     <li>Domain string (consumed but not used, as the server is implicit)</li>
     * </ul>
     *
     * @return a {@link Jid} object representing the Messenger user
     * @throws IOException if an I/O error occurs
     */
    private Jid readFbJid() throws IOException {
        var user = readString();
        var device = readShort();
        // Domain string is part of the wire format but the server is implicit (msgr)
        var _ = readString();
        return Jid.of(user, JidServer.messenger(), device, 0);
    }

    /**
     * Reads a cross-platform interoperability JID (tag 245).
     * <p>
     * Interop JIDs represent users from external platforms and contain:
     * <ul>
     *     <li>User identifier string</li>
     *     <li>Device ID (16-bit)</li>
     *     <li>Integrator ID (16-bit platform identifier)</li>
     *     <li>Domain string (consumed but not used, as the server is implicit)</li>
     * </ul>
     * The integrator and user are combined as {@code integrator-user} in the
     * resulting JID's user component.
     *
     * @return a {@link Jid} object representing the interop user
     * @throws IOException if an I/O error occurs
     */
    private Jid readInteropJid() throws IOException {
        var user = readString();
        var device = readShort();
        var integrator = readShort();
        // Domain string is part of the wire format but the server is implicit (interop)
        var _ = readString();
        return Jid.of(integrator + "-" + user, JidServer.interop(), device, 0);
    }

    /**
     * A decoder implementation for uncompressed node data that reads directly
     * from the source {@link ByteBuffer} without any decompression.
     */
    private static final class Uncompressed extends NodeDecoder {
        /**
         * Constructs a new uncompressed decoder backed by the given source buffer.
         *
         * @param source the ByteBuffer containing the encoded node data
         */
        Uncompressed(ByteBuffer source) {
            super(source);
        }

        @Override
        public boolean hasData() {
            return source.hasRemaining();
        }

        @Override
        int read() throws IOException {
            if (!source.hasRemaining()) {
                throw new IOException("Unexpected end of data");
            }
            return source.get() & 0xFF;
        }

        @Override
        int readShort() throws IOException {
            if (source.remaining() < 2) {
                throw new IOException("Unexpected end of data");
            }
            return source.getShort() & 0xFFFF;
        }

        @Override
        int readInt() throws IOException {
            if (source.remaining() < 4) {
                throw new IOException("Unexpected end of data");
            }
            return source.getInt();
        }

        @Override
        byte[] readBytes(int length) throws IOException {
            if (source.remaining() < length) {
                throw new IOException("Insufficient data available");
            }
            var result = new byte[length];
            source.get(result);
            return result;
        }

        @Override
        public void close() {
        }
    }

    /**
     * A decoder implementation for DEFLATE-compressed node data that inflates
     * data from the source {@link ByteBuffer} through a buffered decompression layer.
     */
    private static final class Compressed extends NodeDecoder {
        /**
         * The inflater used for decompressing data.
         */
        private final Inflater inflater;

        /**
         * Temporary buffer used for decompression output.
         */
        private final byte[] decompressionBuffer;

        /**
         * Pre-allocated buffer used for feeding compressed data to the inflater.
         */
        private final byte[] inflaterInputBuffer;

        /**
         * Current read position in the decompression buffer.
         */
        private int bufferPosition;

        /**
         * Number of valid bytes available in the decompression buffer.
         */
        private int bufferLimit;

        /**
         * Constructs a new compressed decoder backed by the given source buffer.
         *
         * @param source the ByteBuffer containing the DEFLATE-compressed node data
         */
        Compressed(ByteBuffer source) {
            super(source);
            this.inflater = new Inflater();
            this.decompressionBuffer = new byte[DECOMPRESSION_BUFFER_SIZE];
            this.inflaterInputBuffer = new byte[DECOMPRESSION_BUFFER_SIZE];
        }

        @Override
        public boolean hasData() {
            return bufferPosition < bufferLimit
                   || !inflater.finished()
                   || source.hasRemaining();
        }

        @Override
        int read() throws IOException {
            if (bufferPosition >= bufferLimit) {
                fillDecompressionBuffer();
            }

            if (bufferPosition >= bufferLimit) {
                throw new IOException("Unexpected end of decompressed data");
            }

            return decompressionBuffer[bufferPosition++] & 0xFF;
        }

        @Override
        int readShort() throws IOException {
            ensureAvailable(2);
            var value = (short) SHORT_HANDLE.get(decompressionBuffer, bufferPosition);
            bufferPosition += 2;
            return value & 0xFFFF;
        }

        @Override
        int readInt() throws IOException {
            ensureAvailable(4);
            var value = (int) INT_HANDLE.get(decompressionBuffer, bufferPosition);
            bufferPosition += 4;
            return value;
        }

        /**
         * Ensures that at least the specified number of bytes are available contiguously
         * in the decompression buffer starting at {@code bufferPosition}.
         * <p>
         * If fewer bytes are available, the remaining bytes are compacted to the start
         * of the buffer and more data is inflated until the requirement is met.
         *
         * @param needed the minimum number of contiguous bytes required
         * @throws IOException if the stream ends before enough bytes are available
         */
        private void ensureAvailable(int needed) throws IOException {
            var available = bufferLimit - bufferPosition;
            if (available >= needed) {
                return;
            }
            if (available > 0) {
                System.arraycopy(decompressionBuffer, bufferPosition, decompressionBuffer, 0, available);
            }
            bufferPosition = 0;
            bufferLimit = available;
            try {
                while (bufferLimit < needed) {
                    if (inflater.needsInput() && source.hasRemaining()) {
                        var toRead = Math.min(source.remaining(), inflaterInputBuffer.length);
                        source.get(inflaterInputBuffer, 0, toRead);
                        inflater.setInput(inflaterInputBuffer, 0, toRead);
                    }
                    var inflated = inflater.inflate(decompressionBuffer, bufferLimit, decompressionBuffer.length - bufferLimit);
                    if (inflated == 0) {
                        throw new IOException("Unexpected end of decompressed data");
                    }
                    bufferLimit += inflated;
                }
            } catch (DataFormatException e) {
                throw new IOException("Decompression error", e);
            }
        }

        @Override
        byte[] readBytes(int length) throws IOException {
            var result = new byte[length];
            var offset = 0;
            while (offset < length) {
                if (bufferPosition >= bufferLimit) {
                    fillDecompressionBuffer();
                }

                if (bufferPosition >= bufferLimit) {
                    throw new IOException("Unexpected end of decompressed data");
                }

                var available = bufferLimit - bufferPosition;
                var toRead = Math.min(available, length - offset);
                System.arraycopy(decompressionBuffer, bufferPosition, result, offset, toRead);
                bufferPosition += toRead;
                offset += toRead;
            }
            return result;
        }

        /**
         * Fills the decompression buffer by inflating data from the source ByteBuffer.
         *
         * @throws IOException if a decompression error occurs
         */
        private void fillDecompressionBuffer() throws IOException {
            try {
                if (inflater.needsInput() && source.hasRemaining()) {
                    var available = Math.min(source.remaining(), inflaterInputBuffer.length);
                    source.get(inflaterInputBuffer, 0, available);
                    inflater.setInput(inflaterInputBuffer, 0, available);
                }

                bufferPosition = 0;
                bufferLimit = inflater.inflate(decompressionBuffer);
            } catch (DataFormatException e) {
                throw new IOException("Decompression error", e);
            }
        }

        @Override
        public void close() {
            inflater.close();
        }
    }
}
