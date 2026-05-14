package com.github.auties00.cobalt.node.binary;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeAttribute;
import com.github.auties00.cobalt.util.DataUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.SequencedCollection;
import java.util.SequencedMap;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import static com.github.auties00.cobalt.node.binary.NodeTags.*;
import static com.github.auties00.cobalt.node.binary.NodeTokens.*;

/**
 * Parses {@link Node} trees from WhatsApp's binary stanza format.
 *
 * <p>Inbound stanzas from the WhatsApp server are either raw or DEFLATE
 * compressed binary blobs encoded with the WAWap protocol. This class
 * turns those blobs back into node trees by reading the leading flags
 * byte, then dispatching to a source-and-compression specialised
 * decoder that handles the size header, description, attribute list,
 * and typed content (sized list, JID variant, hex or nibble packed run,
 * dictionary token, single byte token, or binary blob).
 *
 * <p>This is an abstract base. The tree-traversal logic lives here
 * once; subclasses provide only the four primitive read operations
 * ({@link #read()}, {@link #readShort()}, {@link #readInt()},
 * {@link #readBytes(int)}) and {@link #hasData()} specialised for the
 * backing source. Four sources are supported via static factory
 * methods, each automatically picking the compressed or uncompressed
 * variant based on the leading flags byte:
 *
 * <ul>
 *   <li>{@link #of(byte[])} — reads from a {@code byte[]} starting at
 *       offset zero.
 *   <li>{@link #of(ByteBuffer)} — reads from a {@link ByteBuffer}
 *       starting at its current position, advancing it as bytes are
 *       consumed.
 *   <li>{@link #of(MemorySegment, long)} — reads from a
 *       {@link MemorySegment} starting at the supplied byte offset.
 *   <li>{@link #of(InputStream)} — reads from an {@link InputStream}
 *       one byte (or one bulk read) at a time. Wrap in a
 *       {@link java.io.BufferedInputStream} if many small reads are
 *       expected on an unbuffered source.
 * </ul>
 *
 * <p>Decoders are {@link AutoCloseable}; closing a compressed decoder
 * releases the underlying {@link Inflater}, closing an uncompressed
 * decoder is a no-op.
 *
 * @see Node
 * @see NodeAttribute
 * @see NodeWriter
 * @see NodeTokens
 * @see NodeTags
 */
@WhatsAppWebModule(moduleName = "WAWap")
public abstract class NodeReader implements AutoCloseable {

    /**
     * Alphabet used to decode nibble packed strings.
     */
    private static final char[] NIBBLE_ALPHABET = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '.', '�', '�', '�', '�'};

    /**
     * Alphabet used to decode hex packed strings.
     */
    private static final char[] HEX_ALPHABET = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    /**
     * Capacity of the per-instance buffers used by the compressed
     * decoder for inflater input and inflated output staging.
     */
    private static final int DECOMPRESSION_BUFFER_SIZE = 8192;

    /**
     * Mask bit in the leading flags byte signalling that the stanza
     * payload is DEFLATE compressed.
     */
    private static final int COMPRESSION_FLAG = 2;

    /**
     * Sole constructor, accessible only to package-internal subclasses.
     */
    NodeReader() {

    }

    /**
     * Returns a decoder appropriate for the leading flags byte of the
     * supplied {@code byte[]} source starting at offset zero.
     *
     * @param source the array of encoded stanza bytes, starting with
     *               the flags byte at index zero
     * @return an inflating decoder when the compression flag is set, a
     *         direct decoder otherwise
     */
    @WhatsAppWebExport(moduleName = "WAWap", exports = "decodeStanza",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static NodeReader of(byte[] source) {
        return of(source, 0);
    }

    /**
     * Returns a decoder appropriate for the leading flags byte of the
     * supplied {@code byte[]} source starting at {@code offset}.
     *
     * @param source the array of encoded stanza bytes
     * @param offset the index of the flags byte within {@code source}
     * @return an inflating decoder when the compression flag is set, a
     *         direct decoder otherwise
     */
    public static NodeReader of(byte[] source, int offset) {
        Objects.requireNonNull(source, "source");
        var flags = source[offset] & 0xFF;
        if ((flags & COMPRESSION_FLAG) != 0) {
            return new ByteArrayCompressed(source, offset + 1);
        }
        return new ByteArrayUncompressed(source, offset + 1);
    }

    /**
     * Returns a decoder appropriate for the leading flags byte of the
     * supplied {@link ByteBuffer} source.
     *
     * @param source the buffer of encoded stanza bytes, in read mode
     *               with the flags byte at its current position
     * @return an inflating decoder when the compression flag is set, a
     *         direct decoder otherwise
     */
    @WhatsAppWebExport(moduleName = "WAWap", exports = "decodeStanza",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static NodeReader of(ByteBuffer source) {
        Objects.requireNonNull(source, "source");
        var flags = source.get() & 0xFF;
        if ((flags & COMPRESSION_FLAG) != 0) {
            return new ByteBufferCompressed(source);
        }
        return new ByteBufferUncompressed(source);
    }

    /**
     * Returns a decoder appropriate for the leading flags byte of the
     * supplied {@link MemorySegment} source starting at {@code offset}.
     *
     * @param source the segment of encoded stanza bytes
     * @param offset the byte offset of the flags byte within
     *               {@code source}
     * @return an inflating decoder when the compression flag is set, a
     *         direct decoder otherwise
     */
    public static NodeReader of(MemorySegment source, long offset) {
        Objects.requireNonNull(source, "source");
        var flags = source.get(ValueLayout.JAVA_BYTE, offset) & 0xFF;
        if ((flags & COMPRESSION_FLAG) != 0) {
            return new MemorySegmentCompressed(source, offset + 1);
        }
        return new MemorySegmentUncompressed(source, offset + 1);
    }

    /**
     * Returns a decoder appropriate for the leading flags byte of the
     * supplied {@link InputStream} source.
     *
     * <p>The leading flags byte is consumed immediately to pick the
     * uncompressed or compressed variant. Callers that expect many
     * small reads from an unbuffered source should wrap the input in
     * {@link java.io.BufferedInputStream} before passing it in.
     *
     * @param source the input stream of encoded stanza bytes
     * @return an inflating decoder when the compression flag is set, a
     *         direct decoder otherwise
     * @throws IOException if reading the flags byte fails
     */
    public static NodeReader of(InputStream source) throws IOException {
        Objects.requireNonNull(source, "source");
        var flags = source.read();
        if (flags < 0) {
            throw new IOException("Unexpected end of stream while reading flags byte");
        }
        if ((flags & COMPRESSION_FLAG) != 0) {
            return new StreamCompressed(source);
        }
        return new StreamUncompressed(source);
    }

    /**
     * Decodes the root {@link Node} of the source stanza.
     *
     * @return the decoded node
     * @throws IOException if the input is truncated, malformed, or
     *         fails to decompress
     */
    @WhatsAppWebExport(moduleName = "WAWap", exports = "decodeStanza",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public final Node decode() throws IOException {
        return readNode();
    }

    /**
     * Returns whether this decoder still has bytes available to be
     * read.
     *
     * @return {@code true} when more bytes remain
     */
    public abstract boolean hasData();

    /**
     * Reads the next byte as an unsigned value.
     *
     * @return the next byte in the {@code 0..255} range
     * @throws IOException if no more bytes are available
     */
    abstract int read() throws IOException;

    /**
     * Reads the next two bytes as an unsigned 16 bit big endian
     * integer.
     *
     * @return the next 16 bit value in the {@code 0..65535} range
     * @throws IOException if fewer than two bytes are available
     */
    abstract int readShort() throws IOException;

    /**
     * Reads the next four bytes as a signed 32 bit big endian integer.
     *
     * @return the next 32 bit signed value
     * @throws IOException if fewer than four bytes are available
     */
    abstract int readInt() throws IOException;

    /**
     * Reads the next {@code length} bytes into a fresh array.
     *
     * @param length the number of bytes to read
     * @return a newly allocated array holding the bytes that were read
     * @throws IOException if fewer than {@code length} bytes are
     *         available
     */
    abstract byte[] readBytes(int length) throws IOException;

    /**
     * Closes this decoder, releasing any resources it holds.
     *
     * <p>The default implementation is a no-op; the uncompressed
     * subclasses hold no resources beyond the caller-owned source and
     * inherit this default. The compressed subclasses override it to
     * release their {@link Inflater}.
     *
     * @throws IOException if the underlying source fails to close; the
     *         narrowed throws clause overrides the looser
     *         {@code Exception} declared by
     *         {@link AutoCloseable#close()}
     */
    @Override
    public void close() throws IOException {

    }

    /**
     * Reads a complete node from the source.
     *
     * @return the decoded node
     * @throws IOException if the stream is truncated or holds a
     *         malformed tag
     */
    private Node readNode() throws IOException {
        var size = readNodeSize();
        if (size == 0) {
            throw new IOException("Unexpected empty node");
        }

        var description = readString();
        var attrs = readAttributes(size - 1);

        if ((size & 1) == 1) {
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
     * Reads a list size header.
     *
     * @return the list size
     * @throws IOException if reading fails
     * @throws IllegalStateException if the leading byte is not a known
     *         list size tag
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
     * Reads a string under whichever encoding tag appears next.
     *
     * @return the decoded string, or {@code null} when the leading tag
     *         is {@link NodeTags#LIST_EMPTY}
     * @throws IOException if the leading tag is not a string shape
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
     * Reads a binary blob with an 8 bit length prefix.
     *
     * @return the bytes that were read
     * @throws IOException if the stream is truncated
     */
    private byte[] readBinary8() throws IOException {
        var size = read() & 0xFF;
        return readBytes(size);
    }

    /**
     * Reads a binary blob with a 20 bit big endian length prefix.
     *
     * @return the bytes that were read
     * @throws IOException if the stream is truncated
     */
    private byte[] readBinary20() throws IOException {
        var size = ((read() & 0x0F) << 16)
                   | (read() << 8)
                   | read();
        return readBytes(size);
    }

    /**
     * Reads a binary blob with a 32 bit big endian length prefix.
     *
     * @return the bytes that were read
     * @throws IOException if the stream is truncated
     */
    private byte[] readBinary32() throws IOException {
        return readBytes(readInt());
    }

    /**
     * Reads an 8 bit token index and resolves it through the supplied
     * dictionary.
     *
     * @param dictionary the dictionary to look up
     * @return the resolved string
     * @throws IOException if the stream is truncated
     */
    private String readDictionaryToken(NodeTokens dictionary) throws IOException {
        var index = read() & 0xFF;
        return dictionary.get(index);
    }

    /**
     * Resolves a single byte token through
     * {@link NodeTokens#SINGLE_BYTE_TOKENS}.
     *
     * @param tag the token byte
     * @return the resolved string
     */
    private String readSingleByteToken(byte tag) {
        var index = tag & 0xFF;
        return SINGLE_BYTE_TOKENS.get(index);
    }

    /**
     * Reads {@code size / 2} attribute key value pairs preserving
     * their declaration order.
     *
     * @param size the number of size units that the attribute block
     *             consumes (always even)
     * @return the parsed attribute map
     * @throws IOException if the stream is truncated
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
     * Reads a single attribute value under whichever encoding tag
     * appears next.
     *
     * @return the parsed attribute, or {@code null} for
     *         {@link NodeTags#LIST_EMPTY} and list shapes
     * @throws IOException if the stream is truncated or holds a
     *         malformed tag
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
     * Reads a list of child nodes with an 8 bit length prefix.
     *
     * @return the parsed children in declaration order
     * @throws IOException if the stream is truncated
     */
    private SequencedCollection<Node> readList8() throws IOException {
        var length = read() & 0xFF;
        return readList(length);
    }

    /**
     * Reads a list of child nodes with a 16 bit big endian length
     * prefix.
     *
     * @return the parsed children in declaration order
     * @throws IOException if the stream is truncated
     */
    private SequencedCollection<Node> readList16() throws IOException {
        return readList(readShort());
    }

    /**
     * Reads {@code size} consecutive child nodes.
     *
     * @param size the number of nodes to read
     * @return the parsed children in declaration order
     * @throws IOException if the stream is truncated
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
     * Reads a packed string under the supplied alphabet.
     *
     * @param alphabet the 16 entry alphabet to translate nibbles
     *                 through
     * @return the decoded string
     * @throws IOException if the stream is truncated
     */
    private String readPacked(char[] alphabet) throws IOException {
        var token = read() & 0xFF;
        var start = token >>> 7;
        var end = token & 127;
        var string = new char[2 * end - start];
        for (var index = 0; index < string.length - 1; index += 2) {
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
     * Reads a {@link NodeTags#JID_PAIR} body.
     *
     * @return the parsed JID
     * @throws IOException if the stream is truncated
     * @throws NullPointerException if the server component is missing
     */
    private Jid readJidPair() throws IOException {
        var user = readString();
        var server = JidServer.of(Objects.requireNonNull(readString(), "Malformed value pair: no server"));
        return user == null ? Jid.of(server) : Jid.of(user, server);
    }

    /**
     * Reads an {@link NodeTags#AD_JID} body.
     *
     * @return the parsed JID
     * @throws IOException if the stream is truncated or carries an
     *         unknown domain code
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
     * Reads a {@link NodeTags#JID_FB} body.
     *
     * @return the parsed JID
     * @throws IOException if the stream is truncated
     */
    private Jid readFbJid() throws IOException {
        var user = readString();
        var device = readShort();
        var _ = readString();
        return Jid.of(user, JidServer.messenger(), device, 0);
    }

    /**
     * Reads a {@link NodeTags#JID_INTEROP} body.
     *
     * @return the parsed JID
     * @throws IOException if the stream is truncated
     */
    private Jid readInteropJid() throws IOException {
        var user = readString();
        var device = readShort();
        var integrator = readShort();
        var _ = readString();
        return Jid.of(integrator + "-" + user, JidServer.interop(), device, 0);
    }

    /**
     * Decoder that reads from a caller-supplied {@code byte[]} without
     * decompressing.
     */
    private static final class ByteArrayUncompressed extends NodeReader {

        /**
         * The source byte array.
         */
        private final byte[] source;

        /**
         * The current read position into {@link #source}.
         */
        private int position;

        /**
         * Constructs a new {@code byte[]}-backed uncompressed decoder.
         *
         * @param source the source bytes
         * @param offset the starting read offset
         */
        ByteArrayUncompressed(byte[] source, int offset) {
            this.source = source;
            this.position = offset;
        }

        @Override
        public boolean hasData() {
            return position < source.length;
        }

        @Override
        int read() throws IOException {
            if (position >= source.length) {
                throw new IOException("Unexpected end of data");
            }
            return source[position++] & 0xFF;
        }

        @Override
        int readShort() throws IOException {
            if (source.length - position < 2) {
                throw new IOException("Unexpected end of data");
            }
            var value = DataUtils.getShort(source, position, ByteOrder.BIG_ENDIAN);
            position += 2;
            return value & 0xFFFF;
        }

        @Override
        int readInt() throws IOException {
            if (source.length - position < 4) {
                throw new IOException("Unexpected end of data");
            }
            var value = DataUtils.getInt(source, position, ByteOrder.BIG_ENDIAN);
            position += 4;
            return value;
        }

        @Override
        byte[] readBytes(int length) throws IOException {
            if (source.length - position < length) {
                throw new IOException("Insufficient data available");
            }
            var result = new byte[length];
            System.arraycopy(source, position, result, 0, length);
            position += length;
            return result;
        }
    }

    /**
     * Decoder that reads from a caller-supplied {@link ByteBuffer}
     * without decompressing, advancing the buffer's position as bytes
     * are consumed.
     */
    private static final class ByteBufferUncompressed extends NodeReader {

        /**
         * The source buffer.
         */
        private final ByteBuffer source;

        /**
         * Constructs a new {@link ByteBuffer}-backed uncompressed
         * decoder. Forces big-endian order on the buffer.
         *
         * @param source the source buffer
         */
        ByteBufferUncompressed(ByteBuffer source) {
            this.source = source.order(ByteOrder.BIG_ENDIAN);
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
    }

    /**
     * Decoder that reads from a caller-supplied {@link MemorySegment}
     * without decompressing, advancing an internal position cursor as
     * bytes are consumed.
     */
    private static final class MemorySegmentUncompressed extends NodeReader {

        /**
         * Big-endian short layout reused across reads.
         */
        private static final ValueLayout.OfShort BE_SHORT =
                ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);

        /**
         * Big-endian int layout reused across reads.
         */
        private static final ValueLayout.OfInt BE_INT =
                ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);

        /**
         * The source segment.
         */
        private final MemorySegment source;

        /**
         * The current read offset within {@link #source}.
         */
        private long position;

        /**
         * Constructs a new {@link MemorySegment}-backed uncompressed
         * decoder.
         *
         * @param source the source segment
         * @param offset the starting byte offset
         */
        MemorySegmentUncompressed(MemorySegment source, long offset) {
            this.source = source;
            this.position = offset;
        }

        @Override
        public boolean hasData() {
            return position < source.byteSize();
        }

        @Override
        int read() throws IOException {
            if (position >= source.byteSize()) {
                throw new IOException("Unexpected end of data");
            }
            return source.get(ValueLayout.JAVA_BYTE, position++) & 0xFF;
        }

        @Override
        int readShort() throws IOException {
            if (source.byteSize() - position < 2) {
                throw new IOException("Unexpected end of data");
            }
            var value = source.get(BE_SHORT, position);
            position += 2;
            return value & 0xFFFF;
        }

        @Override
        int readInt() throws IOException {
            if (source.byteSize() - position < 4) {
                throw new IOException("Unexpected end of data");
            }
            var value = source.get(BE_INT, position);
            position += 4;
            return value;
        }

        @Override
        byte[] readBytes(int length) throws IOException {
            if (source.byteSize() - position < length) {
                throw new IOException("Insufficient data available");
            }
            var result = new byte[length];
            MemorySegment.copy(source, ValueLayout.JAVA_BYTE, position, result, 0, length);
            position += length;
            return result;
        }
    }

    /**
     * Decoder that reads from an {@link InputStream} without
     * decompressing.
     *
     * <p>Each call to {@link #read()}, {@link #readShort()},
     * {@link #readInt()} and {@link #readBytes(int)} drives one or
     * more {@code in.read} calls. Callers expecting many small reads
     * on an unbuffered source should wrap the input in a
     * {@link java.io.BufferedInputStream}.
     */
    private static final class StreamUncompressed extends NodeReader {

        /**
         * The source input stream.
         */
        private final InputStream source;

        /**
         * Constructs a new {@link InputStream}-backed uncompressed
         * decoder.
         *
         * @param source the source stream
         */
        StreamUncompressed(InputStream source) {
            this.source = source;
        }

        @Override
        public boolean hasData() {
            try {
                return source.available() > 0;
            } catch (IOException e) {
                return false;
            }
        }

        @Override
        int read() throws IOException {
            var b = source.read();
            if (b < 0) {
                throw new IOException("Unexpected end of data");
            }
            return b;
        }

        @Override
        int readShort() throws IOException {
            var hi = read();
            var lo = read();
            return (hi << 8) | lo;
        }

        @Override
        int readInt() throws IOException {
            var b0 = read();
            var b1 = read();
            var b2 = read();
            var b3 = read();
            return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
        }

        @Override
        byte[] readBytes(int length) throws IOException {
            var result = source.readNBytes(length);
            if (result.length < length) {
                throw new IOException("Insufficient data available");
            }
            return result;
        }
    }

    /**
     * Abstract base for decoders that inflate DEFLATE compressed
     * source bytes through a staging buffer.
     *
     * <p>Centralises the {@link Inflater} lifecycle, the inflated-byte
     * staging buffer and the per-primitive read/inflate logic.
     * Subclasses provide only the source-specific method
     * {@link #fillInflaterInput(byte[], int)} that copies raw
     * compressed bytes from the underlying source into the inflater
     * input buffer.
     */
    private abstract static class Compressed extends NodeReader {

        /**
         * Inflater that decompresses the source bytes.
         */
        private final Inflater inflater;

        /**
         * Staging buffer that holds inflated bytes pending consumption.
         */
        private final byte[] decompressionBuffer;

        /**
         * Working buffer that feeds compressed bytes into the inflater.
         */
        final byte[] inflaterInputBuffer;

        /**
         * Read offset into {@link #decompressionBuffer}.
         */
        private int bufferPosition;

        /**
         * Count of valid bytes in {@link #decompressionBuffer}.
         */
        private int bufferLimit;

        /**
         * Sole constructor; allocates the inflater and the two staging
         * buffers.
         */
        Compressed() {
            this.inflater = new Inflater();
            this.decompressionBuffer = new byte[DECOMPRESSION_BUFFER_SIZE];
            this.inflaterInputBuffer = new byte[DECOMPRESSION_BUFFER_SIZE];
        }

        /**
         * Copies up to {@code max} compressed bytes from the
         * source-specific backing into {@link #inflaterInputBuffer}
         * starting at offset zero.
         *
         * @param dst the destination buffer (always
         *            {@link #inflaterInputBuffer})
         * @param max the maximum number of bytes to copy
         * @return the number of bytes copied, or {@code 0} if no more
         *         source bytes are available
         */
        abstract int fillInflaterInput(byte[] dst, int max);

        /**
         * Returns whether more compressed source bytes are available
         * to feed the inflater.
         *
         * @return {@code true} when the source still has compressed
         *         bytes to feed
         */
        abstract boolean sourceHasRemaining();

        @Override
        public final boolean hasData() {
            return bufferPosition < bufferLimit
                   || !inflater.finished()
                   || sourceHasRemaining();
        }

        @Override
        final int read() throws IOException {
            if (bufferPosition >= bufferLimit) {
                fillDecompressionBuffer();
            }

            if (bufferPosition >= bufferLimit) {
                throw new IOException("Unexpected end of decompressed data");
            }

            return decompressionBuffer[bufferPosition++] & 0xFF;
        }

        @Override
        final int readShort() throws IOException {
            ensureAvailable(2);
            var value = DataUtils.getShort(decompressionBuffer, bufferPosition, ByteOrder.BIG_ENDIAN);
            bufferPosition += 2;
            return value & 0xFFFF;
        }

        @Override
        final int readInt() throws IOException {
            ensureAvailable(4);
            var value = DataUtils.getInt(decompressionBuffer, bufferPosition, ByteOrder.BIG_ENDIAN);
            bufferPosition += 4;
            return value;
        }

        /**
         * Ensures that at least {@code needed} contiguous inflated
         * bytes are available starting at {@link #bufferPosition}.
         *
         * @param needed the minimum number of contiguous bytes
         *               required
         * @throws IOException if the source ends before enough bytes
         *         are inflated
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
                    if (inflater.needsInput() && sourceHasRemaining()) {
                        var toRead = fillInflaterInput(inflaterInputBuffer, inflaterInputBuffer.length);
                        if (toRead > 0) {
                            inflater.setInput(inflaterInputBuffer, 0, toRead);
                        }
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
        final byte[] readBytes(int length) throws IOException {
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
         * Refills the staging buffer with another inflated block.
         *
         * @throws IOException if the source bytes are malformed
         */
        private void fillDecompressionBuffer() throws IOException {
            try {
                if (inflater.needsInput() && sourceHasRemaining()) {
                    var available = fillInflaterInput(inflaterInputBuffer, inflaterInputBuffer.length);
                    if (available > 0) {
                        inflater.setInput(inflaterInputBuffer, 0, available);
                    }
                }

                bufferPosition = 0;
                bufferLimit = inflater.inflate(decompressionBuffer);
            } catch (DataFormatException e) {
                throw new IOException("Decompression error", e);
            }
        }

        @Override
        public final void close() {
            inflater.close();
        }
    }

    /**
     * Compressed decoder that reads from a {@code byte[]} source.
     */
    private static final class ByteArrayCompressed extends Compressed {

        /**
         * The source byte array of compressed bytes.
         */
        private final byte[] source;

        /**
         * The current read position into {@link #source}.
         */
        private int position;

        /**
         * Constructs a new {@code byte[]}-backed compressed decoder.
         *
         * @param source the source bytes
         * @param offset the starting read offset
         */
        ByteArrayCompressed(byte[] source, int offset) {
            this.source = source;
            this.position = offset;
        }

        @Override
        boolean sourceHasRemaining() {
            return position < source.length;
        }

        @Override
        int fillInflaterInput(byte[] dst, int max) {
            var available = source.length - position;
            if (available <= 0) {
                return 0;
            }
            var toCopy = Math.min(available, max);
            System.arraycopy(source, position, dst, 0, toCopy);
            position += toCopy;
            return toCopy;
        }
    }

    /**
     * Compressed decoder that reads from a {@link ByteBuffer} source.
     */
    private static final class ByteBufferCompressed extends Compressed {

        /**
         * The source buffer of compressed bytes.
         */
        private final ByteBuffer source;

        /**
         * Constructs a new {@link ByteBuffer}-backed compressed
         * decoder.
         *
         * @param source the source buffer
         */
        ByteBufferCompressed(ByteBuffer source) {
            this.source = source;
        }

        @Override
        boolean sourceHasRemaining() {
            return source.hasRemaining();
        }

        @Override
        int fillInflaterInput(byte[] dst, int max) {
            var toCopy = Math.min(source.remaining(), max);
            if (toCopy == 0) {
                return 0;
            }
            source.get(dst, 0, toCopy);
            return toCopy;
        }
    }

    /**
     * Compressed decoder that reads from a {@link MemorySegment}
     * source.
     */
    private static final class MemorySegmentCompressed extends Compressed {

        /**
         * The source segment of compressed bytes.
         */
        private final MemorySegment source;

        /**
         * The current read offset within {@link #source}.
         */
        private long position;

        /**
         * Constructs a new {@link MemorySegment}-backed compressed
         * decoder.
         *
         * @param source the source segment
         * @param offset the starting byte offset
         */
        MemorySegmentCompressed(MemorySegment source, long offset) {
            this.source = source;
            this.position = offset;
        }

        @Override
        boolean sourceHasRemaining() {
            return position < source.byteSize();
        }

        @Override
        int fillInflaterInput(byte[] dst, int max) {
            var available = source.byteSize() - position;
            if (available <= 0) {
                return 0;
            }
            var toCopy = (int) Math.min(available, max);
            MemorySegment.copy(source, ValueLayout.JAVA_BYTE, position, dst, 0, toCopy);
            position += toCopy;
            return toCopy;
        }
    }

    /**
     * Compressed decoder that reads from an {@link InputStream}
     * source.
     */
    private static final class StreamCompressed extends Compressed {

        /**
         * The source input stream of compressed bytes.
         */
        private final InputStream source;

        /**
         * Sticky flag set once {@link InputStream#read(byte[], int, int)}
         * has returned end-of-stream so subsequent
         * {@link #sourceHasRemaining()} queries do not have to read
         * again.
         */
        private boolean exhausted;

        /**
         * Constructs a new {@link InputStream}-backed compressed
         * decoder.
         *
         * @param source the source stream
         */
        StreamCompressed(InputStream source) {
            this.source = source;
        }

        @Override
        boolean sourceHasRemaining() {
            return !exhausted;
        }

        @Override
        int fillInflaterInput(byte[] dst, int max) {
            if (exhausted) {
                return 0;
            }
            int n;
            try {
                n = source.read(dst, 0, max);
            } catch (IOException e) {
                exhausted = true;
                return 0;
            }
            if (n < 0) {
                exhausted = true;
                return 0;
            }
            return n;
        }
    }
}