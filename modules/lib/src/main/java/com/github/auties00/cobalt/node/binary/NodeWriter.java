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
import java.io.OutputStream;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.SequencedCollection;
import java.util.SequencedMap;
import java.util.function.Supplier;

import static com.github.auties00.cobalt.node.binary.NodeTags.*;
import static com.github.auties00.cobalt.node.binary.NodeTokens.*;

/**
 * Serialises {@link Node} trees into WhatsApp's compact binary stanza
 * format.
 *
 * <p>Outbound stanzas are produced through a {@link Node} tree (typically
 * built with {@link com.github.auties00.cobalt.node.NodeBuilder}); this
 * class converts the tree into the wire byte sequence consumed by the
 * WhatsApp socket. Strings pass through the {@link NodeTokens}
 * compression ladder (single-byte token, extension dictionary, packed
 * nibble or hex, length-prefixed UTF-8); binary blobs use 8/20/32-bit
 * length prefixes; JIDs pick one of {@link NodeTags#JID_PAIR},
 * {@link NodeTags#AD_JID}, {@link NodeTags#JID_INTEROP},
 * {@link NodeTags#JID_FB} based on their server and device.
 *
 * <p>The class is an abstract template: the tree-traversal logic lives
 * here once and each subclass provides only the four primitive write
 * operations ({@link #writeByte(int)}, {@link #writeShort(int)},
 * {@link #writeInt(int)}, {@link #writeBytes(byte[], int, int)})
 * specialised for the backing sink. Four sinks are supported via
 * static factories:
 *
 * <ul>
 *   <li>{@link #toBytes(byte[], int)} writes into a caller-supplied
 *       {@code byte[]} starting at a given offset
 *   <li>{@link #toBuffer(ByteBuffer)} writes into a {@link ByteBuffer},
 *       advancing its position
 *   <li>{@link #toSegment(MemorySegment, long)} writes into a
 *       {@link MemorySegment} starting at the given byte offset
 *   <li>{@link #toStream(OutputStream)} streams each encoded byte
 *       directly to the underlying stream; the encoder allocates no
 *       intermediate buffer, so callers that want write batching
 *       should wrap the stream in
 *       {@link java.io.BufferedOutputStream} before passing it in
 * </ul>
 *
 * <p>Encoders are {@link AutoCloseable}: a buffer-backed encoder is a
 * no-op on close; the streaming encoder flushes the downstream when
 * the stream is caller-owned and closes it when the encoder owns it
 * (factory variant {@link #toStream(Supplier)}).
 *
 * <p>Pre-sizing a fixed sink through {@link NodeSizer#sizeOf(Node)} lets most
 * stanzas serialise into a single allocation without any growth.
 *
 * @see Node
 * @see NodeReader
 * @see NodeSizer
 * @see NodeTokens
 * @see NodeTags
 */
@WhatsAppWebModule(moduleName = "WAWap")
public abstract class NodeWriter implements AutoCloseable {

    /**
     * Holds the exclusive upper bound for values that fit in an unsigned byte.
     *
     * <p>Serves as the decision threshold between {@link NodeTags#LIST_8} and
     * {@link NodeTags#LIST_16} for list sizes, and between
     * {@link NodeTags#BINARY_8} and {@link NodeTags#BINARY_20} for payload
     * lengths, mirroring the threshold in {@link NodeSizer}.
     */
    private static final int UNSIGNED_BYTE_MAX_VALUE = 256;

    /**
     * Holds the exclusive upper bound for values that fit in an unsigned
     * short.
     *
     * <p>Serves as the decision threshold above which a list cannot be
     * encoded.
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
     * Constructs a new encoder for a package-internal subclass.
     *
     * <p>The hierarchy is closed within this package; the constructor exists
     * only to satisfy {@code javac} for the inner subclasses.
     */
    NodeWriter() {

    }

    /**
     * Returns an encoder that writes a node into the supplied byte array
     * starting at {@code offset}.
     *
     * <p>The array must be at least {@code offset + NodeSizer.sizeOf(node)}
     * bytes long, sized with {@link NodeSizer#sizeOf(Node)} before allocating.
     * Encoders are single-use; each node needs a fresh one.
     *
     * @param output the destination byte array
     * @param offset the starting offset
     * @return a {@code byte[]}-backed encoder
     */
    public static NodeWriter toBytes(byte[] output, int offset) {
        return new ByteArrayWriter(output, offset);
    }

    /**
     * Returns an encoder that writes a node into the supplied
     * {@link ByteBuffer} starting at its current position.
     *
     * <p>The buffer must have at least {@link NodeSizer#sizeOf(Node)} bytes
     * remaining; the encoder advances the buffer's position as it writes.
     *
     * @param output the destination buffer
     * @return a {@link ByteBuffer}-backed encoder
     */
    public static NodeWriter toBuffer(ByteBuffer output) {
        return new ByteBufferWriter(output);
    }

    /**
     * Returns an encoder that writes a node into the supplied
     * {@link MemorySegment} starting at {@code offset}.
     *
     * <p>The segment must have at least {@code offset + NodeSizer.sizeOf(node)}
     * bytes of space. Used by callers writing into off-heap memory such as a
     * mapped file or a foreign socket buffer.
     *
     * @param output the destination segment
     * @param offset the starting byte offset within the segment
     * @return a {@link MemorySegment}-backed encoder
     */
    public static NodeWriter toSegment(MemorySegment output, long offset) {
        return new MemorySegmentWriter(output, offset);
    }

    /**
     * Returns an encoder that streams a node directly to a caller-owned
     * {@link OutputStream}.
     *
     * <p>Every encoded token is forwarded to the underlying stream as it is
     * produced through single-byte and bulk {@code write} calls; no
     * intermediate buffer is allocated, so callers that want write batching
     * wrap the underlying stream in {@link java.io.BufferedOutputStream} before
     * passing it in. The encoder does not close {@code output} on
     * {@link #close()}; it only flushes any deferred bytes, leaving the caller
     * lifecycle control over the long-lived stream (typically a socket output
     * stream shared across many encoded nodes). The {@link #toStream(Supplier)}
     * variant transfers stream ownership to the encoder.
     *
     * @param output the destination stream
     * @return an {@link OutputStream}-backed streaming encoder that flushes but
     *         does not close {@code output}
     */
    public static NodeWriter toStream(OutputStream output) {
        return new StreamWriter(output, false);
    }

    /**
     * Returns an encoder that streams a node into an {@link OutputStream}
     * resolved from {@code supplier} and owned by the encoder.
     *
     * <p>The supplier is resolved eagerly at this call; the returned encoder
     * closes the resolved stream when {@link #close()} runs.
     *
     * @param supplier the source of the destination stream; resolved eagerly
     * @return an {@link OutputStream}-backed streaming encoder that closes the
     *         resolved stream
     */
    public static NodeWriter toStream(Supplier<? extends OutputStream> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        return new StreamWriter(supplier.get(), true);
    }

    /**
     * Writes one byte at the current encoder position.
     *
     * @implSpec
     * Subclasses must write the low 8 bits of {@code b} to the
     * sink and advance the position by one.
     *
     * @param b the byte value; only the low 8 bits are used
     * @throws IOException if the underlying sink rejects the write
     */
    abstract void writeByte(int b) throws IOException;

    /**
     * Writes two bytes at the current encoder position in big-endian
     * order.
     *
     * @implSpec
     * Subclasses must write the 16-bit value in big-endian order
     * and advance the position by two.
     *
     * @param v the 16-bit value to write
     * @throws IOException if the underlying sink rejects the write
     */
    abstract void writeShort(int v) throws IOException;

    /**
     * Writes four bytes at the current encoder position in big-endian
     * order.
     *
     * @implSpec
     * Subclasses must write the 32-bit value in big-endian order
     * and advance the position by four.
     *
     * @param v the 32-bit value to write
     * @throws IOException if the underlying sink rejects the write
     */
    abstract void writeInt(int v) throws IOException;

    /**
     * Writes {@code len} bytes from {@code src} starting at {@code off}
     * to the current encoder position.
     *
     * @implSpec
     * Subclasses must copy {@code len} bytes from {@code src}
     * starting at {@code off} into the sink and advance the
     * position by {@code len}.
     *
     * @param src the source array
     * @param off the starting offset in {@code src}
     * @param len the number of bytes to write
     * @throws IOException if the underlying sink rejects the write
     */
    abstract void writeBytes(byte[] src, int off, int len) throws IOException;

    /**
     * Closes this encoder, releasing any resources it holds and flushing any
     * deferred output to the underlying sink.
     *
     * <p>The default is a no-op because the buffer-backed subclasses hold no
     * resources beyond the caller-owned sink. The streaming subclass overrides
     * to flush a caller-owned stream or close an encoder-owned stream.
     *
     * @implNote
     * This implementation narrows the throws clause from {@link Exception} on
     * {@link AutoCloseable#close()} to {@link IOException}.
     *
     * @throws IOException if the underlying sink fails to close or flush
     */
    @Override
    public void close() throws IOException {

    }

    /**
     * Writes the leading flags byte ({@code 0x00}, no compression) followed by
     * the supplied node's encoding.
     *
     * <p>The encode side always emits the uncompressed flags byte; compression
     * is only relevant for inbound stanzas. The companion
     * {@link NodeSizer#sizeOf(Node)} accounts for this leading byte.
     *
     * @param node the node to encode
     * @throws IOException if the underlying sink rejects a write
     * @throws NullPointerException if {@code node} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWap", exports = "makeStanza",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public void writeNode(Node node) throws IOException {
        Objects.requireNonNull(node, "node");
        writeByte(0);
        writeNodeBody(node);
    }

    /**
     * Writes a single node (size header, description, attributes, content)
     * without the leading flags byte.
     *
     * <p>Recurses from both {@link #writeNode(Node)} and
     * {@link #writeChildren(SequencedCollection)}; the second caller has
     * already written the parent's flags byte.
     *
     * @param input the node to write
     * @throws IOException if the underlying sink rejects a write
     */
    private void writeNodeBody(Node input) throws IOException {
        writeList(input.size());
        writeString(input.description());
        writeAttributes(input.attributes());
        writeContent(input);
    }

    /**
     * Writes a list size tag followed by the size value, picking the narrowest
     * representation that fits.
     *
     * <p>Picks {@link NodeTags#LIST_8} for sizes below 256 and
     * {@link NodeTags#LIST_16} for sizes below 65536. Sizes at or above the
     * 16-bit limit are protocol overflows and throw.
     *
     * @param size the list size
     * @throws IllegalArgumentException if the size exceeds the 16-bit maximum
     * @throws IOException if the underlying sink rejects a write
     */
    private void writeList(int size) throws IOException {
        if (size < UNSIGNED_BYTE_MAX_VALUE) {
            writeByte(LIST_8);
            writeByte(size);
        } else if (size < UNSIGNED_SHORT_MAX_VALUE) {
            writeByte(LIST_16);
            writeShort(size);
        } else {
            throw new IllegalArgumentException("Cannot write list: overflow");
        }
    }

    /**
     * Writes a string under the most efficient applicable strategy.
     *
     * <p>The strategy ladder mirrors {@link NodeSizer#stringLength(String)}
     * step-for-step: empty string ({@link NodeTags#BINARY_8} plus
     * {@link NodeTags#LIST_EMPTY}), single-byte token, each extension
     * dictionary in turn, packed nibble or hex for short strings
     * ({@code utf8Length < 128}) whose alphabet fits, and finally a
     * length-prefixed UTF-8 blob.
     *
     * @param input the string to write
     * @throws IOException if the underlying sink rejects a write
     */
    private void writeString(String input) throws IOException {
        if (input.isEmpty()) {
            writeByte(BINARY_8);
            writeByte(LIST_EMPTY);
            return;
        }

        var singleByteTokenIndex = SINGLE_BYTE_TOKENS.indexOf(input);
        if (singleByteTokenIndex != -1) {
            writeByte(singleByteTokenIndex);
            return;
        }

        var dictionary0TokenIndex = DICTIONARY_0_TOKENS.indexOf(input);
        if (dictionary0TokenIndex != -1) {
            writeByte(DICTIONARY_0);
            writeByte(dictionary0TokenIndex);
            return;
        }

        var dictionary1TokenIndex = DICTIONARY_1_TOKENS.indexOf(input);
        if (dictionary1TokenIndex != -1) {
            writeByte(DICTIONARY_1);
            writeByte(dictionary1TokenIndex);
            return;
        }

        var dictionary2TokenIndex = DICTIONARY_2_TOKENS.indexOf(input);
        if (dictionary2TokenIndex != -1) {
            writeByte(DICTIONARY_2);
            writeByte(dictionary2TokenIndex);
            return;
        }

        var dictionary3TokenIndex = DICTIONARY_3_TOKENS.indexOf(input);
        if (dictionary3TokenIndex != -1) {
            writeByte(DICTIONARY_3);
            writeByte(dictionary3TokenIndex);
            return;
        }

        var utf8Length = NodeSizer.calculateUtf8Length(input);
        if (utf8Length < 128) {
            var packedType = NodePackedFormat.getPackedType(input);
            if (packedType != -1) {
                writePacked(input, packedType);
                return;
            }
        }

        writeBinary(utf8Length);
        var encoded = input.getBytes(StandardCharsets.UTF_8);
        if (encoded.length != utf8Length) {
            throw new InternalError("Utf8 length mismatch");
        }
        writeBytes(encoded, 0, utf8Length);
    }

    /**
     * Writes a binary-blob length prefix tag followed by the length, using the
     * narrowest tag that fits.
     *
     * <p>Selects {@link NodeTags#BINARY_8}, {@link NodeTags#BINARY_20}, or
     * {@link NodeTags#BINARY_32} for the same thresholds used in
     * {@link NodeSizer#binaryLength(long)}.
     *
     * @param input the length value
     * @throws IOException if the underlying sink rejects a write
     */
    private void writeBinary(int input) throws IOException {
        if (input < UNSIGNED_BYTE_MAX_VALUE) {
            writeByte(BINARY_8);
            writeByte(input);
        } else if (input < INT_20_MAX_VALUE) {
            writeByte(BINARY_20);
            writeByte(input >> 16);
            writeByte(input >> 8);
            writeByte(input);
        } else {
            writeByte(BINARY_32);
            writeInt(input);
        }
    }

    /**
     * Writes a string in nibble or hex packed form (4 bits per character).
     *
     * <p>The length byte's high bit signals whether the last byte carries one
     * character with a filler nibble or two characters.
     *
     * @implNote
     * This implementation uses {@code 0x0F} as the filler nibble for the odd
     * trailing character, matching the sentinel the protocol expects.
     *
     * @param input the string to encode
     * @param tag   {@link NodeTags#NIBBLE_8} or {@link NodeTags#HEX_8}
     * @throws IOException if the underlying sink rejects a write
     */
    private void writePacked(String input, byte tag) throws IOException {
        var table = tag == NIBBLE_8 ? NodePackedFormat.NIBBLE_ENCODE : NodePackedFormat.HEX_ENCODE;
        var len = input.length();
        writeByte(tag);
        var byteCount = (len + 1) / 2;
        if ((len & 1) == 1) {
            byteCount |= 128;
        }
        writeByte(byteCount);
        var i = 0;
        for (; i + 1 < len; i += 2) {
            writeByte((table[input.charAt(i)] << 4) | table[input.charAt(i + 1)]);
        }
        if (i < len) {
            writeByte((table[input.charAt(i)] << 4) | 0x0F);
        }
    }

    /**
     * Writes every entry of the supplied attribute map as a key value pair.
     *
     * <p>Iteration follows the map's insertion order so re-encoded stanzas
     * round-trip stably.
     *
     * @param attributes the attributes to write
     * @throws IOException if the underlying sink rejects a write
     */
    private void writeAttributes(SequencedMap<String, ? extends NodeAttribute> attributes) throws IOException {
        for (var entry : attributes.entrySet()) {
            writeString(entry.getKey());
            writeAttribute(entry.getValue());
        }
    }

    /**
     * Writes a single attribute value under its variant-specific shape.
     *
     * <p>{@link NodeAttribute.TextAttribute} goes through the full string
     * compression ladder; {@link NodeAttribute.BytesAttribute} always uses a
     * length-prefixed binary shape; {@link NodeAttribute.JidAttribute}
     * dispatches through {@link #writeJid(Jid)}.
     *
     * @param attribute the attribute to write
     * @throws IOException if the underlying sink rejects a write
     */
    private void writeAttribute(NodeAttribute attribute) throws IOException {
        switch (attribute) {
            case NodeAttribute.BytesAttribute(var buffer) -> writeBytesBlob(buffer);
            case NodeAttribute.JidAttribute(var jid) -> writeJid(jid);
            case NodeAttribute.TextAttribute(var string) -> writeString(string);
        }
    }

    /**
     * Writes the content slot of a node based on its concrete variant.
     *
     * <p>{@link Node.EmptyNode} contributes nothing because the leading size
     * header already encodes the missing content slot; every other variant
     * writes its payload through the appropriate writer.
     *
     * @param content the node whose content to write
     * @throws IOException if the underlying sink rejects a write
     */
    private void writeContent(Node content) throws IOException {
        switch (content) {
            case Node.EmptyNode _ -> {
            }
            case Node.BytesNode(var _, var _, var buffer) -> writeBytesBlob(buffer);
            case Node.ContainerNode(var _, var _, var children) -> writeChildren(children);
            case Node.JidNode(var _, var _, var jid) -> writeJid(jid);
            case Node.TextNode(var _, var _, var text) -> writeString(text);
        }
    }

    /**
     * Writes a list size header followed by the encoding of every child node.
     *
     * <p>Recurses through {@link #writeNodeBody(Node)} for each child; the
     * parent's flags byte has already been written by {@link #writeNode(Node)}.
     *
     * @param values the child nodes to write
     * @throws IOException if the underlying sink rejects a write
     */
    private void writeChildren(SequencedCollection<Node> values) throws IOException {
        writeList(values.size());
        for (var value : values) {
            writeNodeBody(value);
        }
    }

    /**
     * Writes a binary blob with its length prefix.
     *
     * @param buffer the blob to write
     * @throws IOException if the underlying sink rejects a write
     */
    private void writeBytesBlob(byte[] buffer) throws IOException {
        var length = buffer.length;
        writeBinary(length);
        writeBytes(buffer, 0, length);
    }

    /**
     * Writes a JID under the wire shape appropriate for its server and device.
     *
     * <p>Messenger JIDs become {@link NodeTags#JID_FB}; interoperability JIDs
     * become {@link NodeTags#JID_INTEROP} after splitting a
     * {@code "integrator-user"} compound; device-bearing JIDs become
     * {@link NodeTags#AD_JID}; everything else becomes {@link NodeTags#JID_PAIR}
     * with a {@link NodeTags#LIST_EMPTY} user placeholder when the user
     * component is absent.
     *
     * @param jid the JID to write
     * @throws IOException if the underlying sink rejects a write
     */
    private void writeJid(Jid jid) throws IOException {
        if (jid.hasMessengerServer()) {
            writeByte(JID_FB);
            writeString(jid.user());
            writeShort(jid.device());
            writeString(jid.server().address());
        } else if (jid.hasInteropServer()) {
            writeByte(JID_INTEROP);
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
            writeString(actualUser);
            writeShort(jid.device());
            writeShort(integrator);
        } else if (jid.hasDevice()) {
            writeByte(AD_JID);
            writeByte(getDomainForServer(jid.server()));
            writeByte(jid.device());
            writeString(jid.user());
        } else {
            writeByte(JID_PAIR);
            if (jid.hasUser()) {
                writeString(jid.user());
            } else {
                writeByte(LIST_EMPTY);
            }
            writeString(jid.server().address());
        }
    }

    /**
     * Maps a {@link JidServer} to its multi-device JID domain code.
     *
     * <p>Used only by the {@link NodeTags#AD_JID} branch of
     * {@link #writeJid(Jid)} to project the server enum into one of the
     * wire-level {@code DOMAIN_*} constants.
     *
     * @param server the server to translate
     * @return one of the {@code DOMAIN_*} constants in {@link NodeTags}
     */
    private static int getDomainForServer(JidServer server) {
        return switch (server.type()) {
            case LID -> DOMAIN_LID;
            case HOSTED -> DOMAIN_HOSTED;
            case HOSTED_LID -> DOMAIN_HOSTED_LID;
            default -> DOMAIN_WHATSAPP;
        };
    }

    /**
     * Encodes into a caller-supplied {@code byte[]}.
     */
    private static final class ByteArrayWriter extends NodeWriter {

        /**
         * Holds the destination byte array.
         */
        private final byte[] output;

        /**
         * Holds the current write position into {@link #output}.
         */
        private int position;

        /**
         * Constructs a new {@code byte[]}-backed encoder.
         *
         * @param output the destination byte array
         * @param offset the starting offset
         */
        ByteArrayWriter(byte[] output, int offset) {
            this.output = output;
            this.position = offset;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        void writeByte(int b) {
            output[position++] = (byte) b;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        void writeShort(int v) {
            DataUtils.putShort(output, position, (short) v, ByteOrder.BIG_ENDIAN);
            position += 2;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        void writeInt(int v) {
            DataUtils.putInt(output, position, v, ByteOrder.BIG_ENDIAN);
            position += 4;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        void writeBytes(byte[] src, int off, int len) {
            System.arraycopy(src, off, output, position, len);
            position += len;
        }
    }

    /**
     * Encodes into a {@link ByteBuffer}, advancing its position as it writes.
     *
     * <p>Forces big-endian order on the buffer at construction so the
     * {@code putShort} and {@code putInt} primitives write multi-byte values in
     * wire order.
     */
    private static final class ByteBufferWriter extends NodeWriter {

        /**
         * Holds the destination buffer.
         */
        private final ByteBuffer output;

        /**
         * Constructs a new {@link ByteBuffer}-backed encoder.
         *
         * <p>Forces big-endian order on the buffer so multi-byte primitives
         * write in wire order.
         *
         * @param output the destination buffer
         */
        ByteBufferWriter(ByteBuffer output) {
            this.output = output.order(ByteOrder.BIG_ENDIAN);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        void writeByte(int b) {
            output.put((byte) b);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        void writeShort(int v) {
            output.putShort((short) v);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        void writeInt(int v) {
            output.putInt(v);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        void writeBytes(byte[] src, int off, int len) {
            output.put(src, off, len);
        }
    }

    /**
     * Encodes into a {@link MemorySegment} via the FFM API.
     */
    private static final class MemorySegmentWriter extends NodeWriter {

        /**
         * Holds the big-endian short layout reused across writes.
         */
        private static final ValueLayout.OfShort BE_SHORT =
                ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);

        /**
         * Holds the big-endian int layout reused across writes.
         */
        private static final ValueLayout.OfInt BE_INT =
                ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);

        /**
         * Holds the destination segment.
         */
        private final MemorySegment output;

        /**
         * Holds the current write offset within {@link #output}.
         */
        private long position;

        /**
         * Constructs a new {@link MemorySegment}-backed encoder.
         *
         * @param output the destination segment
         * @param offset the starting byte offset
         */
        MemorySegmentWriter(MemorySegment output, long offset) {
            this.output = output;
            this.position = offset;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        void writeByte(int b) {
            output.set(ValueLayout.JAVA_BYTE, position++, (byte) b);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        void writeShort(int v) {
            output.set(BE_SHORT, position, (short) v);
            position += 2;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        void writeInt(int v) {
            output.set(BE_INT, position, v);
            position += 4;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        void writeBytes(byte[] src, int off, int len) {
            MemorySegment.copy(src, off, output, ValueLayout.JAVA_BYTE, position, len);
            position += len;
        }
    }

    /**
     * Streams encoded tokens directly to an {@link OutputStream} with no
     * intermediate buffer.
     *
     * <p>Each call to a primitive writer translates to one or more
     * {@link OutputStream#write(int)} calls; bulk payloads pass through a
     * single {@link OutputStream#write(byte[], int, int)} call. The encoder
     * allocates nothing per node.
     */
    private static final class StreamWriter extends NodeWriter {

        /**
         * Holds the downstream output stream.
         */
        private final OutputStream output;

        /**
         * Indicates whether this encoder owns {@link #output} and so must close
         * it during {@link #close()}.
         *
         * <p>When {@code false}, the stream is caller-owned and {@link #close()}
         * only flushes it.
         */
        private final boolean owned;

        /**
         * Constructs a new {@link OutputStream}-backed streaming encoder.
         *
         * @param output the downstream stream
         * @param owned  {@code true} when the encoder owns {@code output} and
         *               must close it on {@link #close()}; {@code false} when
         *               the stream is caller-owned and should only be flushed
         */
        StreamWriter(OutputStream output, boolean owned) {
            this.output = output;
            this.owned = owned;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        void writeByte(int b) throws IOException {
            output.write(b);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        void writeShort(int v) throws IOException {
            output.write(v >>> 8);
            output.write(v);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        void writeInt(int v) throws IOException {
            output.write(v >>> 24);
            output.write(v >>> 16);
            output.write(v >>> 8);
            output.write(v);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        void writeBytes(byte[] src, int off, int len) throws IOException {
            output.write(src, off, len);
        }

        /**
         * Closes the downstream output stream when the encoder owns
         * it; otherwise only flushes it so the last bytes written
         * through this encoder reach the network without waiting
         * for a later write to amortise the flush.
         *
         * @throws IOException if the downstream flush or close fails
         */
        @Override
        public void close() throws IOException {
            if (owned) {
                output.close();
            } else {
                output.flush();
            }
        }
    }
}
