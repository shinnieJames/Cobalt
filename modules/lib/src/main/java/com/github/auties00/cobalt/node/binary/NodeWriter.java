package com.github.auties00.cobalt.node.binary;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeAttribute;
import com.github.auties00.cobalt.util.DataUtils;
import com.github.auties00.cobalt.util.SizedOutputStream;

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

import static com.github.auties00.cobalt.node.binary.NodeTags.*;
import static com.github.auties00.cobalt.node.binary.NodeTokens.*;

/**
 * Serialises {@link Node} trees into WhatsApp's compact binary stanza
 * format.
 *
 * <p>Strings are replaced with dictionary tokens when possible, short
 * numeric strings are packed as nibble or hex sequences, binary blobs
 * are length prefixed with 8, 20, or 32 bit widths, and JIDs are written
 * in one of four shapes ({@link NodeTags#JID_PAIR},
 * {@link NodeTags#AD_JID}, {@link NodeTags#JID_INTEROP},
 * {@link NodeTags#JID_FB}) depending on their server and device.
 *
 * <p>This is an abstract base. The tree-traversal logic lives here once;
 * each subclass provides only the four primitive write operations
 * ({@link #writeByte(int)}, {@link #writeShort(int)},
 * {@link #writeInt(int)}, {@link #writeBytes(byte[], int, int)})
 * specialised for the backing sink. Four sinks are supported via
 * static factory methods:
 *
 * <ul>
 *   <li>{@link #toBytes(byte[], int)} — writes into a caller-supplied
 *       {@code byte[]} starting at a given offset.
 *   <li>{@link #toBuffer(ByteBuffer)} — writes into a writable
 *       {@link ByteBuffer}, advancing its position as it writes.
 *   <li>{@link #toSegment(MemorySegment, long)} — writes into a
 *       {@link MemorySegment} starting at the given byte offset.
 *   <li>{@link #toStream(OutputStream)} — streams each encoded token
 *       byte directly to the underlying {@link OutputStream} via
 *       single-byte and bulk {@code write} calls. No intermediate
 *       buffer is allocated; callers that want write batching should
 *       wrap the underlying stream in a
 *       {@link java.io.BufferedOutputStream} before passing it in.
 * </ul>
 *
 * <p>Encoders are {@link AutoCloseable}; closing a streaming encoder
 * flushes the downstream stream, while closing a buffer-backed encoder
 * is a no-op.
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
     * Sole constructor, accessible only to package-internal subclasses.
     */
    NodeWriter() {

    }

    /**
     * Returns an encoder that writes a node into the supplied byte
     * array starting at {@code offset}.
     *
     * <p>The array must be at least
     * {@code offset + NodeSizer.sizeOf(node)} bytes long. The returned
     * encoder is single-use; create a new one per node.
     *
     * @param output the destination byte array
     * @param offset the starting offset
     * @return a {@code byte[]} backed encoder
     */
    public static NodeWriter toBytes(byte[] output, int offset) {
        return new ByteArrayWriter(output, offset);
    }

    /**
     * Returns an encoder that writes a node into the supplied
     * {@link ByteBuffer} starting at its current position.
     *
     * <p>The buffer must have at least {@code NodeSizer.sizeOf(node)}
     * bytes remaining. The returned encoder advances the buffer's
     * position as it writes.
     *
     * @param output the destination buffer
     * @return a {@link ByteBuffer} backed encoder
     */
    public static NodeWriter toBuffer(ByteBuffer output) {
        return new ByteBufferWriter(output);
    }

    /**
     * Returns an encoder that writes a node into the supplied
     * {@link MemorySegment} starting at {@code offset}.
     *
     * <p>The segment must have at least
     * {@code offset + NodeSizer.sizeOf(node)} bytes of space.
     *
     * @param output the destination segment
     * @param offset the starting byte offset within the segment
     * @return a {@link MemorySegment} backed encoder
     */
    public static NodeWriter toSegment(MemorySegment output, long offset) {
        return new MemorySegmentWriter(output, offset);
    }

    /**
     * Returns an encoder that streams a node directly to an
     * {@link OutputStream}.
     *
     * <p>Every encoded token is forwarded to the underlying stream as
     * it is produced via single-byte and bulk {@code write} calls; no
     * intermediate buffer is allocated. Callers that want write
     * batching should wrap the underlying stream in a
     * {@link java.io.BufferedOutputStream} before passing it in.
     *
     * @param output the destination stream
     * @return an {@link OutputStream} backed streaming encoder
     */
    public static NodeWriter toStream(OutputStream output) {
        return new StreamWriter(output);
    }

    /**
     * Writes one byte at the current encoder position.
     *
     * @param b the byte value; only the low 8 bits are used
     * @throws IOException if the underlying sink rejects the write
     */
    abstract void writeByte(int b) throws IOException;

    /**
     * Writes two bytes at the current encoder position in big-endian
     * order.
     *
     * @param v the 16-bit value to write
     * @throws IOException if the underlying sink rejects the write
     */
    abstract void writeShort(int v) throws IOException;

    /**
     * Writes four bytes at the current encoder position in big-endian
     * order.
     *
     * @param v the 32-bit value to write
     * @throws IOException if the underlying sink rejects the write
     */
    abstract void writeInt(int v) throws IOException;

    /**
     * Writes {@code len} bytes from {@code src} starting at {@code off}
     * to the current encoder position.
     *
     * @param src the source array
     * @param off the starting offset in {@code src}
     * @param len the number of bytes to write
     * @throws IOException if the underlying sink rejects the write
     */
    abstract void writeBytes(byte[] src, int off, int len) throws IOException;

    /**
     * Closes this encoder, releasing any resources it holds and
     * flushing any deferred output to the underlying sink.
     *
     * <p>The default implementation is a no-op; the buffer-backed
     * subclasses ({@link ByteArrayWriter}, {@link ByteBufferWriter},
     * {@link MemorySegmentWriter}) hold no resources beyond the
     * caller-owned sink and inherit this default. The
     * {@link StreamWriter} overrides it to flush the downstream
     * {@link OutputStream} so the last writes reach the network
     * promptly.
     *
     * @throws IOException if the underlying sink fails to close or
     *         flush; the narrowed throws clause overrides the looser
     *         {@code Exception} declared by
     *         {@link AutoCloseable#close()}
     */
    @Override
    public void close() throws IOException {

    }

    /**
     * Writes the leading flags byte ({@code 0x00}, no compression)
     * followed by the supplied node's encoding.
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
     * Writes a single node (size header, description, attributes,
     * content) without the leading flags byte.
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
     * Writes a list size tag followed by the size value, picking the
     * narrowest representation that fits.
     *
     * @param size the list size
     * @throws IllegalArgumentException if the size exceeds the 16 bit
     *         maximum
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
     * Writes a string under the most efficient applicable strategy
     * (single-byte token, dictionary token, packed nibble/hex, or
     * length-prefixed UTF-8).
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
     * Writes a binary length prefix tag followed by the length using
     * the narrowest tag that fits.
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
     * Writes a string in nibble or hex packed form (4 bits per
     * character).
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
     * Writes every entry of the supplied attribute map as a key value
     * pair.
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
     * Writes a single attribute value under its variant specific shape.
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
     * Writes a list size header followed by the encoding of every child
     * node.
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
     * Writes a JID under the shape appropriate for its server and
     * device.
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
     * Maps a {@link JidServer} to its multi device JID domain code.
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
     * Encoder that writes into a caller-supplied {@code byte[]}.
     */
    private static final class ByteArrayWriter extends NodeWriter {

        /**
         * The destination byte array.
         */
        private final byte[] output;

        /**
         * The current write position into {@link #output}.
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

        @Override
        void writeByte(int b) {
            output[position++] = (byte) b;
        }

        @Override
        void writeShort(int v) {
            DataUtils.putShort(output, position, (short) v, ByteOrder.BIG_ENDIAN);
            position += 2;
        }

        @Override
        void writeInt(int v) {
            DataUtils.putInt(output, position, v, ByteOrder.BIG_ENDIAN);
            position += 4;
        }

        @Override
        void writeBytes(byte[] src, int off, int len) {
            System.arraycopy(src, off, output, position, len);
            position += len;
        }
    }

    /**
     * Encoder that writes into a {@link ByteBuffer}, advancing its
     * position as it writes.
     */
    private static final class ByteBufferWriter extends NodeWriter {

        /**
         * The destination buffer.
         */
        private final ByteBuffer output;

        /**
         * Constructs a new {@link ByteBuffer}-backed encoder. Forces
         * big-endian order on the buffer.
         *
         * @param output the destination buffer
         */
        ByteBufferWriter(ByteBuffer output) {
            this.output = output.order(ByteOrder.BIG_ENDIAN);
        }

        @Override
        void writeByte(int b) {
            output.put((byte) b);
        }

        @Override
        void writeShort(int v) {
            output.putShort((short) v);
        }

        @Override
        void writeInt(int v) {
            output.putInt(v);
        }

        @Override
        void writeBytes(byte[] src, int off, int len) {
            output.put(src, off, len);
        }
    }

    /**
     * Encoder that writes into a {@link MemorySegment} via the FFM API.
     */
    private static final class MemorySegmentWriter extends NodeWriter {

        /**
         * Big-endian short layout reused across writes.
         */
        private static final ValueLayout.OfShort BE_SHORT =
                ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);

        /**
         * Big-endian int layout reused across writes.
         */
        private static final ValueLayout.OfInt BE_INT =
                ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN);

        /**
         * The destination segment.
         */
        private final MemorySegment output;

        /**
         * The current write offset within {@link #output}.
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

        @Override
        void writeByte(int b) {
            output.set(ValueLayout.JAVA_BYTE, position++, (byte) b);
        }

        @Override
        void writeShort(int v) {
            output.set(BE_SHORT, position, (short) v);
            position += 2;
        }

        @Override
        void writeInt(int v) {
            output.set(BE_INT, position, v);
            position += 4;
        }

        @Override
        void writeBytes(byte[] src, int off, int len) {
            MemorySegment.copy(src, off, output, ValueLayout.JAVA_BYTE, position, len);
            position += len;
        }
    }

    /**
     * Encoder that streams encoded tokens directly to an
     * {@link OutputStream} with no intermediate buffer.
     *
     * <p>Each call to {@link #writeByte(int)}, {@link #writeShort(int)}
     * and {@link #writeInt(int)} forwards the resulting bytes through
     * one or more {@link OutputStream#write(int)} calls; bulk payloads
     * pass through a single {@link OutputStream#write(byte[], int, int)}
     * call. The encoder allocates nothing per node.
     */
    private static final class StreamWriter extends NodeWriter {

        /**
         * The downstream output stream.
         */
        private final OutputStream output;

        /**
         * Constructs a new {@link OutputStream}-backed streaming
         * encoder.
         *
         * @param output the downstream stream
         */
        StreamWriter(OutputStream output) {
            this.output = output;
        }

        /**
         * Announces the encoded size to the downstream stream when it
         * implements {@link SizedOutputStream}, then delegates to the
         * base implementation to encode the node's bytes.
         *
         * <p>The downstream typically uses the hint to emit its
         * length-prefixed frame header up front so the encoded bytes
         * stream straight to the wire with no intermediate buffer.
         *
         * @param node the node to encode
         * @throws IOException          if the size announcement or any
         *                              encoded write fails
         * @throws NullPointerException if {@code node} is {@code null}
         */
        @Override
        public void writeNode(Node node) throws IOException {
            Objects.requireNonNull(node, "node");
            if (output instanceof SizedOutputStream sized) {
                sized.beginMessage(NodeSizer.sizeOf(node));
            }
            super.writeNode(node);
        }

        @Override
        void writeByte(int b) throws IOException {
            output.write(b);
        }

        @Override
        void writeShort(int v) throws IOException {
            output.write(v >>> 8);
            output.write(v);
        }

        @Override
        void writeInt(int v) throws IOException {
            output.write(v >>> 24);
            output.write(v >>> 16);
            output.write(v >>> 8);
            output.write(v);
        }

        @Override
        void writeBytes(byte[] src, int off, int len) throws IOException {
            output.write(src, off, len);
        }

        /**
         * Closes the downstream output stream
         *
         * @throws IOException if the downstream stream doesn't close
         */
        @Override
        public void close() throws IOException {
            output.close();
        }
    }
}
