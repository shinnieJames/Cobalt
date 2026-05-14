package com.github.auties00.cobalt.node;

import com.github.auties00.cobalt.exception.WhatsAppMalformedJidException;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.binary.NodeReader;
import com.github.auties00.cobalt.node.binary.NodeWriter;
import it.auties.protobuf.model.ProtobufString;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/**
 * Represents a node in a WhatsApp stanza tree.
 *
 * <p>Every message exchanged with the WhatsApp server is a tree of nodes
 * serialised through a compact binary XML protocol. A node carries a tag
 * name (the {@code description}), an ordered map of attributes, and an
 * optional content slot that holds either plain text, a single
 * {@link Jid}, a binary blob, or a list of child nodes. Nodes are
 * immutable: the read side methods exposed by this interface are the only
 * way to inspect a node, while {@link NodeBuilder} is the entry point for
 * producing one.
 *
 * <p>Five concrete variants populate the sealed hierarchy:
 * <ul>
 *   <li>{@link EmptyNode} for nodes without content</li>
 *   <li>{@link TextNode} for nodes whose content is a UTF-8 string</li>
 *   <li>{@link JidNode} for nodes whose content is a single JID</li>
 *   <li>{@link BytesNode} for nodes whose content is a binary blob</li>
 *   <li>{@link ContainerNode} for nodes whose content is a list of child
 *       nodes</li>
 * </ul>
 *
 * <p>Reading attributes and content uses convenience helpers that return
 * the value parsed in a specific shape. Each helper has three flavours:
 * {@code getXxx} returns an {@link Optional} wrapper, {@code getXxx(key,
 * default)} returns the value or a fallback, and {@code getRequiredXxx}
 * throws when the value is absent. Stream variants ({@code streamXxx})
 * yield zero or one element so they fit naturally into pipelines.
 *
 * <p>Example reading attributes from an inbound stanza:
 * <pre>{@code
 * String id = node.getRequiredAttributeAsString("id");
 * Jid from = node.getAttributeAsJid("from").orElse(null);
 * boolean retry = node.getAttributeAsBool("retry", false);
 * }</pre>
 *
 * <p>Example walking children:
 * <pre>{@code
 * Node body = node.getRequiredChild("body");
 * List<Node> participants = node.streamChildren("participant").toList();
 * }</pre>
 *
 * @see NodeBuilder
 * @see NodeAttribute
 * @see NodeWriter
 * @see NodeReader
 */
@WhatsAppWebModule(moduleName = "WAWap")
@WhatsAppWebModule(moduleName = "WAXmlNode")
@WhatsAppWebModule(moduleName = "WASmaxChildren")
public sealed interface Node {
    /**
     * Returns the description (tag name) of this node.
     *
     * @return the non null tag name
     */
    String description();

    /**
     * Returns whether this node's description equals the supplied value.
     *
     * @param description the description to compare against
     * @return {@code true} when the descriptions match
     */
    default boolean hasDescription(String description) {
        return Objects.equals(description(), description);
    }

    /**
     * Returns the attributes attached to this node, in insertion order.
     *
     * @return an unmodifiable sequenced map of attribute names to values
     */
    SequencedMap<String, NodeAttribute> attributes();

    /**
     * Returns the attribute associated with the supplied key.
     *
     * @param key the attribute key to look up
     * @return an {@link Optional} that holds the matching attribute, or
     *         empty when absent
     * @throws NullPointerException if {@code key} is {@code null}
     */
    default Optional<NodeAttribute> getAttribute(String key) {
        Objects.requireNonNull(key, "key cannot be null");
        return Optional.ofNullable(attributes().get(key));
    }

    /**
     * Returns the value of the supplied attribute as a string.
     *
     * @param key the attribute key
     * @return an {@link Optional} that holds the string value, or empty
     *         when the attribute is absent
     */
    default Optional<String> getAttributeAsString(String key) {
        return getAttribute(key)
                .map(NodeAttribute::toString);
    }

    /**
     * Returns the value of the supplied attribute parsed as a boolean.
     *
     * @param key the attribute key
     * @return an {@link Optional} that holds the parsed boolean, or empty
     *         when the attribute is absent
     */
    default Optional<Boolean> getAttributeAsBool(String key) {
        return getAttribute(key)
                .map(NodeAttribute::toString)
                .map(Boolean::parseBoolean);
    }

    /**
     * Returns the value of the supplied attribute as a string, falling
     * back to {@code defaultValue} when absent.
     *
     * @param key          the attribute key
     * @param defaultValue the fallback returned when the attribute is
     *                     absent
     * @return the attribute value or the fallback
     */
    default String getAttributeAsString(String key, String defaultValue) {
        return getAttributeAsString(key)
                .orElse(defaultValue);
    }

    /**
     * Returns the value of the supplied attribute parsed as a boolean,
     * falling back to {@code defaultValue} when absent.
     *
     * @param key          the attribute key
     * @param defaultValue the fallback returned when the attribute is
     *                     absent
     * @return the parsed boolean or the fallback
     */
    default boolean getAttributeAsBool(String key, boolean defaultValue) {
        return getAttributeAsBool(key)
                .orElse(defaultValue);
    }

    /**
     * Returns the value of the supplied attribute as a byte array.
     *
     * @param key the attribute key
     * @return an {@link Optional} that holds the byte array, or empty when
     *         the attribute is absent
     */
    default Optional<byte[]> getAttributeAsBytes(String key) {
        return getAttribute(key)
                .map(NodeAttribute::toBytes);
    }

    /**
     * Returns the value of the supplied attribute as a byte array, falling
     * back to {@code defaultValue} when absent.
     *
     * @param key          the attribute key
     * @param defaultValue the fallback returned when the attribute is
     *                     absent
     * @return the byte array or the fallback
     */
    default byte[] getAttributeAsBytes(String key, byte[] defaultValue) {
        return getAttribute(key)
                .map(NodeAttribute::toBytes)
                .orElse(defaultValue);
    }

    /**
     * Returns the value of the supplied attribute parsed as a {@link Jid}.
     *
     * @param key the attribute key
     * @return an {@link Optional} that holds the parsed JID, or empty when
     *         the attribute is absent or unparseable
     */
    default Optional<Jid> getAttributeAsJid(String key) {
        return getAttribute(key)
                .flatMap(NodeAttribute::toJid);
    }

    /**
     * Returns the value of the supplied attribute parsed as a {@link Jid},
     * falling back to {@code defaultValue} when absent or unparseable.
     *
     * @param key          the attribute key
     * @param defaultValue the fallback returned when the attribute is
     *                     absent or unparseable
     * @return the parsed JID or the fallback
     */
    default Jid getAttributeAsJid(String key, Jid defaultValue) {
        return getAttribute(key)
                .flatMap(NodeAttribute::toJid)
                .orElse(defaultValue);
    }

    /**
     * Returns the value of the supplied attribute parsed as a {@code long}.
     *
     * @param key the attribute key
     * @return an {@link OptionalLong} that holds the parsed value, or
     *         empty when the attribute is absent or unparseable
     */
    default OptionalLong getAttributeAsLong(String key) {
        var result = getAttribute(key);
        return result.isEmpty() ? OptionalLong.empty() : result.get().toLong();
    }

    /**
     * Returns the value of the supplied attribute parsed as a {@code long},
     * falling back to {@code defaultValue} when absent or unparseable.
     *
     * @param key          the attribute key
     * @param defaultValue the fallback returned when the attribute is
     *                     absent or unparseable
     * @return the parsed long or the fallback
     */
    default long getAttributeAsLong(String key, long defaultValue) {
        var result = getAttribute(key);
        return result.isEmpty() ? defaultValue : result.get().toLong().orElse(defaultValue);
    }

    /**
     * Returns the value of the supplied attribute parsed as a boxed
     * {@link Long}, falling back to {@code defaultValue} when absent or
     * unparseable.
     *
     * @param key          the attribute key
     * @param defaultValue the fallback, which may be {@code null}
     * @return the parsed {@link Long} or the fallback
     */
    default Long getAttributeAsLong(String key, Long defaultValue) {
        var result = getAttribute(key);
        if (result.isEmpty()) {
            return defaultValue;
        }

        var converted = result.get().toLong();
        if(converted.isEmpty()) {
            return defaultValue;
        }

        return converted.getAsLong();
    }

    /**
     * Returns the value of the supplied attribute parsed as an {@code int}.
     *
     * @param key the attribute key
     * @return an {@link OptionalInt} that holds the parsed value, or empty
     *         when the attribute is absent or unparseable
     */
    default OptionalInt getAttributeAsInt(String key) {
        var result = getAttribute(key);
        return result.isEmpty() ? OptionalInt.empty() : result.get().toInt();
    }

    /**
     * Returns the value of the supplied attribute parsed as an {@code int},
     * falling back to {@code defaultValue} when absent or unparseable.
     *
     * @param key          the attribute key
     * @param defaultValue the fallback returned when the attribute is
     *                     absent or unparseable
     * @return the parsed int or the fallback
     */
    default int getAttributeAsInt(String key, int defaultValue) {
        var result = getAttribute(key);
        return result.isEmpty() ? defaultValue : result.get().toInt().orElse(defaultValue);
    }

    /**
     * Returns the value of the supplied attribute parsed as a boxed
     * {@link Integer}, falling back to {@code defaultValue} when absent or
     * unparseable.
     *
     * @param key          the attribute key
     * @param defaultValue the fallback, which may be {@code null}
     * @return the parsed {@link Integer} or the fallback
     */
    default Integer getAttributeAsInt(String key, Integer defaultValue) {
        var result = getAttribute(key);
        if (result.isEmpty()) {
            return defaultValue;
        }

        var converted = result.get().toInt();
        if(converted.isEmpty()) {
            return defaultValue;
        }

        return converted.getAsInt();
    }

    /**
     * Returns the value of the supplied attribute parsed as a {@code double}.
     *
     * @param key the attribute key
     * @return an {@link OptionalDouble} that holds the parsed value, or
     *         empty when the attribute is absent or unparseable
     */
    default OptionalDouble getAttributeAsDouble(String key) {
        var result = getAttribute(key);
        return result.isEmpty() ? OptionalDouble.empty() : result.get().toDouble();
    }

    /**
     * Returns the value of the supplied attribute parsed as a {@code double},
     * falling back to {@code defaultValue} when absent or unparseable.
     *
     * @param key          the attribute key
     * @param defaultValue the fallback returned when the attribute is
     *                     absent or unparseable
     * @return the parsed double or the fallback
     */
    default double getAttributeAsDouble(String key, double defaultValue) {
        var result = getAttribute(key);
        return result.isEmpty() ? defaultValue : result.get().toDouble().orElse(defaultValue);
    }

    /**
     * Returns the value of the supplied attribute parsed as a boxed
     * {@link Double}, falling back to {@code defaultValue} when absent or
     * unparseable.
     *
     * @param key          the attribute key
     * @param defaultValue the fallback, which may be {@code null}
     * @return the parsed {@link Double} or the fallback
     */
    default Double getAttributeAsDouble(String key, Double defaultValue) {
        var result = getAttribute(key);
        if (result.isEmpty()) {
            return defaultValue;
        }

        var converted = result.get().toDouble();
        if(converted.isEmpty()) {
            return defaultValue;
        }

        return converted.getAsDouble();
    }

    /**
     * Returns a single element stream that yields the matching attribute,
     * or an empty stream when absent.
     *
     * @param key the attribute key
     * @return a {@link Stream} that yields the attribute or nothing
     * @throws NullPointerException if {@code key} is {@code null}
     */
    default Stream<NodeAttribute> streamAttribute(String key) {
        Objects.requireNonNull(key, "key cannot be null");
        return Stream.ofNullable(attributes().get(key));
    }

    /**
     * Returns a single element stream that yields the attribute as a
     * string, or an empty stream when absent.
     *
     * @param key the attribute key
     * @return a {@link Stream} that yields the string value or nothing
     */
    default Stream<String> streamAttributeAsString(String key) {
        return streamAttribute(key)
                .map(NodeAttribute::toString);
    }

    /**
     * Returns a single element stream that yields the attribute parsed as
     * a boolean, or an empty stream when absent.
     *
     * @param key the attribute key
     * @return a {@link Stream} that yields the boolean value or nothing
     */
    default Stream<Boolean> streamAttributeAsBool(String key) {
        return streamAttribute(key)
                .map(NodeAttribute::toString)
                .map(Boolean::parseBoolean);
    }

    /**
     * Returns a single element stream that yields the attribute as a byte
     * array, or an empty stream when absent.
     *
     * @param key the attribute key
     * @return a {@link Stream} that yields the byte array or nothing
     */
    default Stream<byte[]> streamAttributeAsBytes(String key) {
        return streamAttribute(key)
                .map(NodeAttribute::toBytes);
    }

    /**
     * Returns a single element stream that yields the attribute parsed as
     * a {@link Jid}, or an empty stream when absent or unparseable.
     *
     * @param key the attribute key
     * @return a {@link Stream} that yields the JID or nothing
     * @throws NullPointerException if {@code key} is {@code null}
     */
    default Stream<Jid> streamAttributeAsJid(String key) {
        Objects.requireNonNull(key, "key cannot be null");
        var attributeValue = attributes().get(key);
        return attributeValue != null
                ? attributeValue.toJid().stream()
                : Stream.empty();
    }

    /**
     * Returns a single element stream that yields the attribute parsed as
     * a {@code long}, or an empty stream when absent or unparseable.
     *
     * @param key the attribute key
     * @return a {@link LongStream} that yields the long value or nothing
     * @throws NullPointerException if {@code key} is {@code null}
     */
    default LongStream streamAttributeAsLong(String key) {
        Objects.requireNonNull(key, "key cannot be null");
        var attributeValue = attributes().get(key);
        return attributeValue != null
                ? attributeValue.toLong().stream()
                : LongStream.empty();
    }

    /**
     * Returns a single element stream that yields the attribute parsed as
     * an {@code int}, or an empty stream when absent or unparseable.
     *
     * @param key the attribute key
     * @return an {@link IntStream} that yields the int value or nothing
     * @throws NullPointerException if {@code key} is {@code null}
     */
    default IntStream streamAttributeAsInt(String key) {
        Objects.requireNonNull(key, "key cannot be null");
        var attributeValue = attributes().get(key);
        return attributeValue != null
                ? attributeValue.toInt().stream()
                : IntStream.empty();
    }

    /**
     * Returns a single element stream that yields the attribute parsed as
     * a {@code double}, or an empty stream when absent or unparseable.
     *
     * @param key the attribute key
     * @return a {@link DoubleStream} that yields the double value or
     *         nothing
     * @throws NullPointerException if {@code key} is {@code null}
     */
    default DoubleStream streamAttributeAsDouble(String key) {
        Objects.requireNonNull(key, "key cannot be null");
        var attributeValue = attributes().get(key);
        return attributeValue != null
                ? attributeValue.toDouble().stream()
                : DoubleStream.empty();
    }

    /**
     * Returns the attribute associated with the supplied key, throwing
     * when absent.
     *
     * @param key the attribute key
     * @return the matching attribute
     * @throws NoSuchElementException if the attribute is absent
     */
    default NodeAttribute getRequiredAttribute(String key) {
        var result = attributes().get(key);
        if(result == null) {
            throw new NoSuchElementException("No attribute with key " + key + " found in node " + description() + " with attributes " + attributes());
        }

        return result;
    }

    /**
     * Returns the value of the supplied attribute as a string, throwing
     * when absent.
     *
     * @param key the attribute key
     * @return the string value
     * @throws NoSuchElementException if the attribute is absent
     */
    default String getRequiredAttributeAsString(String key) {
        return getRequiredAttribute(key)
                .toString();
    }

    /**
     * Returns the value of the supplied attribute parsed as a boolean,
     * throwing when absent.
     *
     * @param key the attribute key
     * @return the parsed boolean
     * @throws NoSuchElementException if the attribute is absent
     */
    default boolean getRequiredAttributeAsBool(String key) {
        var result = getRequiredAttribute(key)
                .toString();
        return Boolean.parseBoolean(result);
    }

    /**
     * Returns the value of the supplied attribute as a byte array,
     * throwing when absent.
     *
     * @param key the attribute key
     * @return the byte array
     * @throws NoSuchElementException if the attribute is absent
     */
    default byte[] getRequiredAttributeAsBytes(String key) {
        return getRequiredAttribute(key)
                .toBytes();
    }

    /**
     * Returns the value of the supplied attribute parsed as a {@link Jid},
     * throwing when absent or unparseable.
     *
     * @param key the attribute key
     * @return the parsed JID
     * @throws NoSuchElementException   if the attribute is absent
     * @throws IllegalArgumentException if the attribute does not parse to
     *                                  a valid JID
     */
    default Jid getRequiredAttributeAsJid(String key) {
        var requiredAttribute = getRequiredAttribute(key);
        return requiredAttribute
                .toJid()
                .orElseThrow(() -> new IllegalArgumentException("Cannot convert required attribute " + key + " to JID. Attribute value: " + requiredAttribute));
    }

    /**
     * Returns the value of the supplied attribute parsed as a {@code long},
     * throwing when absent or unparseable.
     *
     * @param key the attribute key
     * @return the parsed long
     * @throws NoSuchElementException   if the attribute is absent
     * @throws IllegalArgumentException if the attribute does not parse to
     *                                  a valid long
     */
    default long getRequiredAttributeAsLong(String key) {
        var requiredAttribute = getRequiredAttribute(key);
        return requiredAttribute
                .toLong()
                .orElseThrow(() -> new IllegalArgumentException("Cannot convert required attribute " + key + " to long. Attribute value: " + requiredAttribute));
    }

    /**
     * Returns the value of the supplied attribute parsed as an {@code int},
     * throwing when absent or unparseable.
     *
     * @param key the attribute key
     * @return the parsed int
     * @throws NoSuchElementException   if the attribute is absent
     * @throws IllegalArgumentException if the attribute does not parse to
     *                                  a valid int
     */
    default int getRequiredAttributeAsInt(String key) {
        var requiredAttribute = getRequiredAttribute(key);
        return requiredAttribute
                .toInt()
                .orElseThrow(() -> new IllegalArgumentException("Cannot convert required attribute " + key + " to int. Attribute value: " + requiredAttribute));
    }

    /**
     * Returns the value of the supplied attribute parsed as a {@code double},
     * throwing when absent or unparseable.
     *
     * @param key the attribute key
     * @return the parsed double
     * @throws NoSuchElementException   if the attribute is absent
     * @throws IllegalArgumentException if the attribute does not parse to
     *                                  a valid double
     */
    default double getRequiredAttributeAsDouble(String key) {
        var requiredAttribute = getRequiredAttribute(key);
        return requiredAttribute
                .toDouble()
                .orElseThrow(() -> new IllegalArgumentException("Cannot convert required attribute " + key + " to double. Attribute value: " + requiredAttribute));
    }

    /**
     * Returns whether this node carries an attribute with the supplied
     * key.
     *
     * @param key the attribute key
     * @return {@code true} when the attribute is present
     * @throws NullPointerException if {@code key} is {@code null}
     */
    default boolean hasAttribute(String key) {
        Objects.requireNonNull(key, "key cannot be null");
        return attributes().containsKey(key);
    }

    /**
     * Returns whether this node carries an attribute with the supplied key
     * whose string value equals {@code value}.
     *
     * @param key   the attribute key
     * @param value the expected string value
     * @return {@code true} when the attribute exists and matches
     * @throws NullPointerException if {@code key} is {@code null}
     */
    default boolean hasAttribute(String key, String value) {
        Objects.requireNonNull(key, "key cannot be null");
        var attribute = attributes().get(key);
        return attribute != null
               && attribute.toString().equals(value);
    }

    /**
     * Returns whether this node carries an attribute with the supplied key
     * whose byte value equals {@code value}.
     *
     * @param key   the attribute key
     * @param value the expected byte array value
     * @return {@code true} when the attribute exists and matches
     * @throws NullPointerException if {@code key} is {@code null}
     */
    default boolean hasAttribute(String key, byte[] value) {
        Objects.requireNonNull(key, "key cannot be null");
        var attribute = attributes().get(key);
        return attribute != null
               && Arrays.equals(attribute.toBytes(), value);
    }

    /**
     * Returns whether this node carries an attribute with the supplied key
     * that parses to the supplied {@link Jid}.
     *
     * @param key   the attribute key
     * @param value the expected JID
     * @return {@code true} when the attribute exists and matches
     * @throws NullPointerException if {@code key} is {@code null}
     */
    default boolean hasAttribute(String key, Jid value) {
        Objects.requireNonNull(key, "key cannot be null");
        var attribute = attributes().get(key);
        if(attribute == null) {
            return false;
        }

        var attributeValue = attribute.toJid();
        return attributeValue.isPresent()
               && Objects.equals(attributeValue.get(), value);
    }

    /**
     * Returns whether this node carries an attribute with the supplied key
     * that parses to the supplied {@code long}.
     *
     * @param key   the attribute key
     * @param value the expected long value
     * @return {@code true} when the attribute exists and matches
     * @throws NullPointerException if {@code key} is {@code null}
     */
    default boolean hasAttribute(String key, long value) {
        Objects.requireNonNull(key, "key cannot be null");
        var attribute = attributes().get(key);
        if(attribute == null) {
            return false;
        }

        var attributeValue = attribute.toLong();
        return attributeValue.isPresent()
               && attributeValue.getAsLong() == value;
    }

    /**
     * Returns whether this node carries an attribute with the supplied key
     * that parses to the supplied {@code int}.
     *
     * @param key   the attribute key
     * @param value the expected int value
     * @return {@code true} when the attribute exists and matches
     * @throws NullPointerException if {@code key} is {@code null}
     */
    default boolean hasAttribute(String key, int value) {
        Objects.requireNonNull(key, "key cannot be null");
        var attribute = attributes().get(key);
        if(attribute == null) {
            return false;
        }

        var attributeValue = attribute.toInt();
        return attributeValue.isPresent()
               && attributeValue.getAsInt() == value;
    }

    /**
     * Returns whether this node carries an attribute with the supplied key
     * that parses to the supplied {@code double}.
     *
     * @param key   the attribute key
     * @param value the expected double value
     * @return {@code true} when the attribute exists and matches
     * @throws NullPointerException if {@code key} is {@code null}
     */
    default boolean hasAttribute(String key, double value) {
        Objects.requireNonNull(key, "key cannot be null");
        var attribute = attributes().get(key);
        if(attribute == null) {
            return false;
        }

        var attributeValue = attribute.toDouble();
        return attributeValue.isPresent()
               && attributeValue.getAsDouble() == value;
    }

    /**
     * Returns whether this node carries an attribute with the supplied key
     * that parses to the supplied {@code boolean}.
     *
     * @param key   the attribute key
     * @param value the expected boolean value
     * @return {@code true} when the attribute exists and matches
     * @throws NullPointerException if {@code key} is {@code null}
     */
    default boolean hasAttribute(String key, boolean value) {
        Objects.requireNonNull(key, "key cannot be null");
        var attribute = attributes().get(key);
        if(attribute == null) {
            return false;
        }

        var attributeValue = attribute.toString();
        return Boolean.parseBoolean(attributeValue) == value;
    }

    /**
     * Returns whether this node carries any content slot.
     *
     * @return {@code false} for {@link EmptyNode}, {@code true} for every
     *         other variant
     */
    boolean hasContent();

    /**
     * Returns whether this node's content equals the supplied text.
     *
     * @param content the expected text content
     * @return {@code true} when the node carries text content equal to
     *         {@code content}
     */
    boolean hasContent(String content);

    /**
     * Returns whether this node's content equals the supplied JID.
     *
     * @param content the expected JID content
     * @return {@code true} when the node carries JID content equal to
     *         {@code content}
     */
    boolean hasContent(Jid content);

    /**
     * Returns whether this node's content equals the supplied byte array.
     *
     * @param content the expected binary content
     * @return {@code true} when the node carries binary content equal to
     *         {@code content}
     */
    boolean hasContent(byte[] content);

    /**
     * Returns the encoded size of this node measured in token slots.
     *
     * <p>The count is one for the description, two for every attribute
     * (key plus value), and one when a content slot is populated.
     *
     * @return the number of token slots required by the node
     */
    default int size() {
        return 1
               + (attributes().size() * 2)
               + (hasContent() ? 1 : 0);
    }

    /**
     * Returns the content of this node as a byte array, when applicable.
     *
     * @return an {@link Optional} that holds the content as bytes, or
     *         empty when the variant has no convertible content
     */
    Optional<byte[]> toContentBytes();

    /**
     * Returns a single element stream that yields the content as a byte
     * array, or an empty stream when no conversion is possible.
     *
     * @return a {@link Stream} that yields the byte array or nothing
     */
    default Stream<byte[]> streamContentBytes() {
        return toContentBytes()
                .stream();
    }

    /**
     * Returns the content of this node as an {@link InputStream}, when
     * applicable.
     *
     * @return an {@link Optional} that holds the content as a stream, or
     *         empty when the variant has no convertible content
     */
    Optional<InputStream> toContentStream();

    /**
     * Returns a single element stream that yields the content as an
     * {@link InputStream}, or an empty stream when no conversion is
     * possible.
     *
     * @return a {@link Stream} that yields the input stream or nothing
     */
    default Stream<InputStream> streamContentStream() {
        return toContentStream()
                .stream();
    }

    /**
     * Returns the content of this node as a string, when applicable.
     *
     * @return an {@link Optional} that holds the content as text, or
     *         empty when the variant has no convertible content
     */
    Optional<String> toContentString();

    /**
     * Returns a single element stream that yields the content as a string,
     * or an empty stream when no conversion is possible.
     *
     * @return a {@link Stream} that yields the string or nothing
     */
    default Stream<String> streamContentString() {
        return toContentString()
                .stream();
    }

    /**
     * Returns the content of this node parsed as a boolean, when
     * applicable.
     *
     * @return an {@link Optional} that holds the parsed boolean, or empty
     *         when no conversion is possible
     */
    default Optional<Boolean> toContentBool() {
        return toContentString()
                .map(Boolean::parseBoolean);
    }

    /**
     * Returns a single element stream that yields the content parsed as a
     * boolean, or an empty stream when no conversion is possible.
     *
     * @return a {@link Stream} that yields the parsed boolean or nothing
     */
    default Stream<Boolean> streamContentBool() {
        return toContentBool()
                .stream();
    }

    /**
     * Returns the content of this node parsed as an {@link Integer}, when
     * applicable.
     *
     * @return an {@link Optional} that holds the parsed int, or empty when
     *         the content does not represent a valid int
     */
    default Optional<Integer> toContentInt() {
        return toContentString().map(str -> {
            try {
                return Integer.parseInt(str);
            }catch (NumberFormatException _) {
                return null;
            }
        });
    }

    /**
     * Returns a single element stream that yields the content parsed as
     * an {@link Integer}, or an empty stream when parsing fails.
     *
     * @return a {@link Stream} that yields the parsed int or nothing
     */
    default Stream<Integer> streamContentInt() {
        return toContentInt()
                .stream();
    }

    /**
     * Returns the content of this node parsed as a {@link Long}, when
     * applicable.
     *
     * @return an {@link Optional} that holds the parsed long, or empty
     *         when the content does not represent a valid long
     */
    default Optional<Long> toContentLong() {
        return toContentString().map(str -> {
            try {
                return Long.parseLong(str);
            }catch (NumberFormatException _) {
                return null;
            }
        });
    }

    /**
     * Returns a single element stream that yields the content parsed as a
     * {@link Long}, or an empty stream when parsing fails.
     *
     * @return a {@link Stream} that yields the parsed long or nothing
     */
    default Stream<Long> streamContentLong() {
        return toContentLong()
                .stream();
    }

    /**
     * Returns the content of this node as a {@link Jid}, when applicable.
     *
     * @return an {@link Optional} that holds the JID, or empty when the
     *         content does not parse to a valid JID
     */
    Optional<Jid> toContentJid();

    /**
     * Returns a single element stream that yields the content as a
     * {@link Jid}, or an empty stream when parsing fails.
     *
     * @return a {@link Stream} that yields the JID or nothing
     */
    default Stream<Jid> streamContentJid() {
        return toContentJid()
                .stream();
    }

    /**
     * Returns the children of this node in declaration order.
     *
     * @return a sequenced collection of child nodes, possibly empty
     */
    SequencedCollection<Node> children();

    /**
     * Returns the children of this node as a {@link Stream}.
     *
     * @return a non null stream over the children
     */
    default Stream<Node> streamChildren() {
        return children()
                .stream();
    }

    /**
     * Returns the first child of this node, when present.
     *
     * @return an {@link Optional} that holds the first child, or empty
     *         when the node has none
     */
    default Optional<Node> getChild() {
        var children = children();
        return children.isEmpty()
                ? Optional.empty()
                : Optional.ofNullable(children.getFirst());
    }

    /**
     * Returns a single element stream that yields the first child, or an
     * empty stream when the node has none.
     *
     * @return a {@link Stream} that yields the first child or nothing
     */
    default Stream<Node> streamChild() {
        var children = children();
        return children.isEmpty()
                ? Stream.empty()
                : Stream.of(children.getFirst());
    }

    /**
     * Returns the first child whose description matches {@code description}.
     *
     * @param description the description to match
     * @return an {@link Optional} that holds the matching child, or empty
     *         when none matches
     * @throws NullPointerException if {@code description} is {@code null}
     */
    default Optional<Node> getChild(String description) {
        Objects.requireNonNull(description, "description cannot be null");
        return streamChildren(description)
                .findFirst();
    }

    /**
     * Returns the first child whose description matches any of the
     * supplied descriptions.
     *
     * <p>The descriptions are tested in the supplied order; the first
     * match wins.
     *
     * @param descriptions the descriptions to match
     * @return an {@link Optional} that holds the first match, or empty
     *         when none matches
     * @throws NullPointerException if {@code descriptions} or any element
     *                              is {@code null}
     */
    default Optional<Node> getChild(String... descriptions) {
        Objects.requireNonNull(descriptions, "descriptions cannot be null");
        for (var description : descriptions) {
            Objects.requireNonNull(description, "description cannot be null");
            var child = getChild(description);
            if (child.isPresent()) {
                return child;
            }
        }

        return Optional.empty();
    }

    /**
     * Returns the first child whose description matches {@code description},
     * or {@code defaultValue} when none matches.
     *
     * @param description  the description to match
     * @param defaultValue the fallback returned when no child matches
     * @return the matching child or the fallback
     * @throws NullPointerException if {@code description} is {@code null}
     */
    default Node getChild(String description, Node defaultValue) {
        return getChild(description)
                .orElse(defaultValue);
    }

    /**
     * Returns the first child whose description matches {@code description},
     * throwing when none matches.
     *
     * @param description the description to match
     * @return the first matching child
     * @throws NullPointerException     if {@code description} is {@code null}
     * @throws IllegalArgumentException if no child matches
     */
    default Node getRequiredChild(String description) {
        Objects.requireNonNull(description, "description cannot be null");
        return streamChildren(description)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No child node found with description: " + description));
    }

    /**
     * Returns the first child whose description matches any of the
     * supplied descriptions, throwing when none matches.
     *
     * <p>The descriptions are tested in the supplied order; the first
     * match wins.
     *
     * @param descriptions the descriptions to match
     * @return the first matching child
     * @throws NullPointerException     if {@code descriptions} or any
     *                                  element is {@code null}
     * @throws IllegalArgumentException if no child matches
     */
    default Node getRequiredChild(String... descriptions) {
        return getChild(descriptions)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No child node found with descriptions: " + Arrays.toString(descriptions)
                ));
    }

    /**
     * Returns a single element stream that yields the first child whose
     * description matches {@code description}, or an empty stream when
     * none matches.
     *
     * @param description the description to match
     * @return a {@link Stream} that yields the matching child or nothing
     * @throws NullPointerException if {@code description} is {@code null}
     */
    default Stream<Node> streamChild(String description) {
        Objects.requireNonNull(description, "description cannot be null");
        return streamChildren(description)
                .findFirst()
                .stream();
    }

    /**
     * Returns a single element stream that yields the first child whose
     * description matches any of the supplied descriptions, or an empty
     * stream when none matches.
     *
     * @param descriptions the descriptions to match
     * @return a {@link Stream} that yields the matching child or nothing
     * @throws NullPointerException if {@code descriptions} or any element
     *                              is {@code null}
     */
    default Stream<Node> streamChild(String... descriptions) {
        return getChild(descriptions)
                .stream();
    }

    /**
     * Returns every child whose description matches {@code description},
     * preserving declaration order.
     *
     * @param description the description to match
     * @return a sequenced collection of matching children, possibly empty
     * @throws NullPointerException if {@code description} is {@code null}
     */
    default SequencedCollection<Node> getChildren(String description) {
        Objects.requireNonNull(description, "description cannot be null");
        return children()
                .stream()
                .filter(node -> node.hasDescription(description))
                .toList();
    }

    /**
     * Returns every child whose description matches any of the supplied
     * descriptions, preserving declaration order.
     *
     * @param descriptions the descriptions to match
     * @return a sequenced collection of matching children, possibly empty
     * @throws NullPointerException if {@code descriptions} or any element
     *                              is {@code null}
     */
    default SequencedCollection<Node> getChildren(String... descriptions) {
        return streamChildren(descriptions)
                .toList();
    }

    /**
     * Returns a stream of every child whose description matches
     * {@code description}, preserving declaration order.
     *
     * @param description the description to match
     * @return a {@link Stream} over the matching children
     * @throws NullPointerException if {@code description} is {@code null}
     */
    default Stream<Node> streamChildren(String description) {
        Objects.requireNonNull(description, "description cannot be null");
        return children()
                .stream()
                .filter(node -> node.hasDescription(description));
    }

    /**
     * Returns a stream of every child whose description matches any of the
     * supplied descriptions, preserving declaration order.
     *
     * @param descriptions the descriptions to match
     * @return a {@link Stream} over the matching children
     * @throws NullPointerException if {@code descriptions} or any element
     *                              is {@code null}
     */
    default Stream<Node> streamChildren(String... descriptions) {
        var descriptionSet = new LinkedHashSet<String>();
        Objects.requireNonNull(descriptions, "descriptions cannot be null");
        for (var description : descriptions) {
            descriptionSet.add(Objects.requireNonNull(description, "description cannot be null"));
        }

        return children()
                .stream()
                .filter(node -> descriptionSet.contains(node.description()));
    }

    /**
     * Returns whether this node has at least one child whose description
     * matches {@code description}.
     *
     * @param description the description to match
     * @return {@code true} when at least one child matches
     * @throws NullPointerException if {@code description} is {@code null}
     */
    default boolean hasChild(String description) {
        Objects.requireNonNull(description, "description cannot be null");
        return children()
                .stream()
                .anyMatch(node -> node.hasDescription(description));
    }

    /**
     * Returns whether this node has at least one child whose description
     * matches any of the supplied descriptions.
     *
     * @param descriptions the descriptions to match
     * @return {@code true} when at least one child matches
     * @throws NullPointerException if {@code descriptions} or any element
     *                              is {@code null}
     */
    default boolean hasChild(String... descriptions) {
        return getChild(descriptions).isPresent();
    }

    /**
     * Node variant that carries no content slot.
     *
     * <p>Used for stanzas whose meaning is conveyed entirely by the tag
     * name and attributes, such as simple presence updates or
     * acknowledgements.
     *
     * @param description the tag name
     * @param attributes  the attribute map
     */
    record EmptyNode(String description, SequencedMap<String, NodeAttribute> attributes) implements Node {
        /**
         * Builds an empty node, rejecting {@code null} arguments.
         * @throws NullPointerException if any argument is {@code null}
         */
        @WhatsAppWebExport(moduleName = "WAXmlNode", exports = "XmlNode",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public EmptyNode {
            Objects.requireNonNull(description, "description cannot be null");
            Objects.requireNonNull(attributes, "attributes cannot be null");
        }

        @Override
        public SequencedMap<String, NodeAttribute> attributes() {
            return Collections.unmodifiableSequencedMap(attributes);
        }

        @Override
        public boolean hasContent() {
            return false;
        }

        @Override
        public boolean hasContent(String content) {
            return false;
        }

        @Override
        public boolean hasContent(Jid content) {
            return false;
        }

        @Override
        public Optional<String> toContentString() {
            return Optional.empty();
        }

        @Override
        public boolean hasContent(byte[] content) {
            return false;
        }

        @Override
        public Optional<Jid> toContentJid() {
            return Optional.empty();
        }

        @Override
        public SequencedCollection<Node> children() {
            return List.of();
        }

        @Override
        public Optional<byte[]> toContentBytes() {
            return Optional.empty();
        }

        @Override
        public Optional<InputStream> toContentStream() {
            return Optional.empty();
        }

        @Override
        public boolean equals(Object o) {
            return switch (o) {
                case EmptyNode(var thatDescription, var thatAttributes) -> Objects.equals(description, thatDescription) && Objects.equals(attributes, thatAttributes);
                case TextNode(var thatDescription, var thatAttributes, var thatContent) -> Objects.equals(description, thatDescription) && Objects.equals(attributes, thatAttributes) && hasContent(thatContent);
                case BytesNode(var thatDescription, var thatAttributes, var thatContent) -> Objects.equals(description, thatDescription) && Objects.equals(attributes, thatAttributes) && hasContent(thatContent);
                case null, default -> false;
            };
        }

        @Override
        public int hashCode() {
            return Objects.hash(description, attributes);
        }

        /**
         * Returns a debug oriented string for this empty node.
         * @return the debug string
         */
        @Override
        @WhatsAppWebExport(moduleName = "WAXmlNode",
                exports = {"XmlNode", "attrsToString"},
                adaptation = WhatsAppAdaptation.ADAPTED)
        public String toString() {
            var result = new StringBuilder();
            result.append("Node[description=");
            result.append(description);

            if(!attributes.isEmpty()) {
                result.append(", attributes=");
                result.append(attributes);
            }

            result.append("]");

            return result.toString();
        }
    }

    /**
     * Node variant whose content is a UTF-8 string.
     *
     * <p>Used for stanzas whose payload is textual data such as a status
     * blurb, an identifier serialised as text, or a free form message
     * body.
     *
     * @param description the tag name
     * @param attributes  the attribute map
     * @param content     the textual payload
     */
    record TextNode(String description, SequencedMap<String, NodeAttribute> attributes, String content) implements Node {
        /**
         * Builds a text node, rejecting {@code null} arguments.
         * @throws NullPointerException if any argument is {@code null}
         */
        @WhatsAppWebExport(moduleName = "WAXmlNode", exports = "XmlNode",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public TextNode {
            Objects.requireNonNull(description, "description cannot be null");
            Objects.requireNonNull(attributes, "attributes cannot be null");
            Objects.requireNonNull(content, "content cannot be null");
        }

        @Override
        public SequencedMap<String, NodeAttribute> attributes() {
            return Collections.unmodifiableSequencedMap(attributes);
        }

        @Override
        public boolean hasContent() {
            return true;
        }

        @Override
        public boolean hasContent(String content) {
            return Objects.equals(this.content, content);
        }

        @Override
        public boolean hasContent(Jid content) {
            return content != null && Objects.equals(this.content, content.toString());
        }

        @Override
        public boolean hasContent(byte[] content) {
            return content != null && Objects.equals(this.content, new String(content));
        }

        @Override
        public Optional<byte[]> toContentBytes() {
            return Optional.of(content.getBytes());
        }

        @Override
        public Optional<InputStream> toContentStream() {
            return Optional.of(new ByteArrayInputStream(content.getBytes()));
        }

        @Override
        public Optional<Jid> toContentJid() {
            try {
                var result = Jid.of(content);
                return Optional.of(result);
            }catch (WhatsAppMalformedJidException exception) {
                return Optional.empty();
            }
        }

        @Override
        public Optional<String> toContentString() {
            return Optional.of(content);
        }

        @Override
        public SequencedCollection<Node> children() {
            return List.of();
        }

        @Override
        public boolean equals(Object o) {
            return switch (o) {
                case EmptyNode(var thatDescription, var thatAttributes) -> Objects.equals(description, thatDescription)
                        && Objects.equals(attributes, thatAttributes)
                        && content.isEmpty();
                case TextNode(var thatDescription, var thatAttributes, var thatContent) -> Objects.equals(description, thatDescription)
                        && Objects.equals(attributes, thatAttributes)
                        && hasContent(thatContent);
                case JidNode(var thatDescription, var thatAttributes, var thatContent) -> Objects.equals(description, thatDescription)
                        && Objects.equals(attributes, thatAttributes)
                        && hasContent(thatContent);
                case BytesNode(var thatDescription, var thatAttributes, var thatContent) -> Objects.equals(description, thatDescription)
                        && Objects.equals(attributes, thatAttributes)
                        && hasContent(thatContent);
                case null, default -> false;
            };
        }

        @Override
        public int hashCode() {
            return Objects.hash(description, attributes, content);
        }

        /**
         * Returns a debug oriented string for this text node.
         * @return the debug string
         */
        @Override
        @WhatsAppWebExport(moduleName = "WAXmlNode",
                exports = {"XmlNode", "attrsToString"},
                adaptation = WhatsAppAdaptation.ADAPTED)
        public String toString() {
            var result = new StringBuilder();
            result.append("Node[description=");
            result.append(description());

            if(!attributes.isEmpty()) {
                result.append(", attributes=");
                result.append(attributes);
            }

            if(content != null) {
                result.append(", content=");
                result.append(content);
            }

            result.append("]");

            return result.toString();
        }
    }

    /**
     * Node variant whose content is a single {@link Jid}.
     *
     * <p>Used for stanzas whose payload addresses a user, group, or
     * device. The JID is serialised on the wire under one of the
     * supported JID shapes rather than as plain text.
     *
     * @param description the tag name
     * @param attributes  the attribute map
     * @param content     the JID payload
     */
    record JidNode(String description, SequencedMap<String, NodeAttribute> attributes, Jid content) implements Node {
        /**
         * Builds a JID node, rejecting {@code null} arguments.
         * @throws NullPointerException if any argument is {@code null}
         */
        @WhatsAppWebExport(moduleName = "WAXmlNode", exports = "XmlNode",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public JidNode {
            Objects.requireNonNull(description, "description cannot be null");
            Objects.requireNonNull(attributes, "attributes cannot be null");
            Objects.requireNonNull(content, "content cannot be null");
        }

        @Override
        public SequencedMap<String, NodeAttribute> attributes() {
            return Collections.unmodifiableSequencedMap(attributes);
        }

        @Override
        public boolean hasContent() {
            return true;
        }

        @Override
        public boolean hasContent(String content) {
            return Objects.equals(this.content.toString(), content);
        }

        @Override
        public boolean hasContent(Jid content) {
            return Objects.equals(this.content, content);
        }

        @Override
        public boolean hasContent(byte[] content) {
            return content != null && Objects.equals(this.content.toString(), new String(content));
        }

        @Override
        public Optional<String> toContentString() {
            return Optional.of(content.toString());
        }

        @Override
        public Optional<Jid> toContentJid() {
            return Optional.of(content);
        }

        @Override
        public Optional<byte[]> toContentBytes() {
            return Optional.of(content.toString().getBytes());
        }

        @Override
        public Optional<InputStream> toContentStream() {
            return Optional.of(new ByteArrayInputStream(content.toString().getBytes()));
        }

        @Override
        public SequencedCollection<Node> children() {
            return List.of();
        }

        @Override
        public boolean equals(Object o) {
            return switch (o) {
                case TextNode(var thatDescription, var thatAttributes, var thatContent) -> Objects.equals(description, thatDescription)
                        && Objects.equals(attributes, thatAttributes)
                        && hasContent(thatContent);
                case JidNode(var thatDescription, var thatAttributes, var thatContent) -> Objects.equals(description, thatDescription)
                        && Objects.equals(attributes, thatAttributes)
                        && hasContent(thatContent);
                case BytesNode(var thatDescription, var thatAttributes, var thatContent) -> Objects.equals(description, thatDescription)
                        && Objects.equals(attributes, thatAttributes)
                        && hasContent(thatContent);
                case null, default -> false;
            };
        }

        @Override
        public int hashCode() {
            return Objects.hash(description, attributes, content);
        }

        /**
         * Returns a debug oriented string for this JID node.
         * @return the debug string
         */
        @Override
        @WhatsAppWebExport(moduleName = "WAXmlNode",
                exports = {"XmlNode", "attrsToString"},
                adaptation = WhatsAppAdaptation.ADAPTED)
        public String toString() {
            var result = new StringBuilder();
            result.append("Node[description=");
            result.append(description());

            if(!attributes.isEmpty()) {
                result.append(", attributes=");
                result.append(attributes);
            }

            if(content != null) {
                result.append(", content=");
                result.append(content);
            }

            result.append("]");

            return result.toString();
        }
    }

    /**
     * Node variant whose content is an opaque binary blob.
     *
     * <p>Used for stanzas that carry raw bytes such as Signal ciphertext,
     * media thumbnails, or any other already encoded payload.
     *
     * @param description the tag name
     * @param attributes  the attribute map
     * @param content     the binary payload
     */
    record BytesNode(String description, SequencedMap<String, NodeAttribute> attributes, byte[] content) implements Node {
        /**
         * Builds a bytes node, rejecting {@code null} arguments.
         * @throws NullPointerException if any argument is {@code null}
         */
        @WhatsAppWebExport(moduleName = "WAXmlNode", exports = "XmlNode",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public BytesNode {
            Objects.requireNonNull(description, "description cannot be null");
            Objects.requireNonNull(attributes, "attributes cannot be null");
            Objects.requireNonNull(content, "content cannot be null");
        }

        @Override
        public SequencedMap<String, NodeAttribute> attributes() {
            return Collections.unmodifiableSequencedMap(attributes);
        }

        @Override
        public Optional<byte[]> toContentBytes() {
            return Optional.of(content);
        }

        @Override
        public Optional<InputStream> toContentStream() {
            return Optional.of(new ByteArrayInputStream(content));
        }

        @Override
        public Optional<Jid> toContentJid() {
            try {
                var result = Jid.of(ProtobufString.lazy(content));
                return Optional.of(result);
            } catch (WhatsAppMalformedJidException exception) {
                return Optional.empty();
            }
        }

        @Override
        public Optional<String> toContentString() {
            var decoded = new String(content);
            return Optional.of(decoded);
        }

        @Override
        public boolean hasContent() {
            return true;
        }

        @Override
        public boolean hasContent(String content) {
            return Objects.equals(new String(this.content), content);
        }

        @Override
        public boolean hasContent(Jid content) {
            return content != null && Objects.equals(new String(this.content), content.toString());
        }

        @Override
        public boolean hasContent(byte[] content) {
            return Arrays.equals(this.content, content);
        }

        @Override
        public SequencedCollection<Node> children() {
            return List.of();
        }

        @Override
        public boolean equals(Object o) {
            return switch (o) {
                case TextNode(var thatDescription, var thatAttributes, var thatContent) -> Objects.equals(description, thatDescription)
                        && Objects.equals(attributes, thatAttributes)
                        && hasContent(thatContent);
                case JidNode(var thatDescription, var thatAttributes, var thatContent) -> Objects.equals(description, thatDescription)
                        && Objects.equals(attributes, thatAttributes)
                        && hasContent(thatContent);
                case BytesNode(var thatDescription, var thatAttributes, var thatContent) -> Objects.equals(description, thatDescription)
                        && Objects.equals(attributes, thatAttributes)
                        && hasContent(thatContent);
                case null, default -> false;
            };
        }

        @Override
        public int hashCode() {
            return Objects.hash(description, attributes, Arrays.hashCode(content));
        }

        /**
         * Returns a debug oriented string for this bytes node.
         * @return the debug string
         */
        @Override
        @WhatsAppWebExport(moduleName = "WAXmlNode",
                exports = {"XmlNode", "attrsToString", "uint8ArrayToDebugString"},
                adaptation = WhatsAppAdaptation.ADAPTED)
        public String toString() {
            var result = new StringBuilder();
            result.append("Node[description=");
            result.append(description());

            if(!attributes.isEmpty()) {
                result.append(", attributes=");
                result.append(attributes);
            }

            if(content != null) {
                if(hasDescription("result") || hasDescription("query") || hasDescription("body")) {
                    result.append(", content=");
                    result.append(new String(content));
                }else {
                    result.append(", content=");
                    result.append(Arrays.toString(content));
                }
            }

            result.append("]");

            return result.toString();
        }
    }

    /**
     * Node variant whose content is a sequence of child nodes.
     *
     * <p>This is the recursive case of the stanza tree: a container node
     * groups a list of children under a common tag, matching the XML
     * element with children shape used across the WhatsApp protocol.
     *
     * @param description the tag name
     * @param attributes  the attribute map
     * @param children    the child nodes in declaration order
     */
    record ContainerNode(String description, SequencedMap<String, NodeAttribute> attributes, SequencedCollection<Node> children) implements Node {
        /**
         * Builds a container node, rejecting {@code null} arguments.
         * @throws NullPointerException if any argument is {@code null}
         */
        @WhatsAppWebExport(moduleName = "WAXmlNode", exports = "XmlNode",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public ContainerNode {
            Objects.requireNonNull(description, "description cannot be null");
            Objects.requireNonNull(attributes, "attributes cannot be null");
            Objects.requireNonNull(children, "children cannot be null");
        }

        @Override
        public SequencedMap<String, NodeAttribute> attributes() {
            return Collections.unmodifiableSequencedMap(attributes);
        }

        @Override
        public SequencedCollection<Node> children() {
            return Collections.unmodifiableSequencedCollection(children);
        }

        @Override
        public boolean hasContent() {
            return true;
        }

        @Override
        public boolean hasContent(Jid content) {
            return false;
        }

        @Override
        public boolean hasContent(byte[] content) {
            return false;
        }

        @Override
        public boolean hasContent(String content) {
            return false;
        }

        @Override
        public Optional<byte[]> toContentBytes() {
            return Optional.empty();
        }

        @Override
        public Optional<String> toContentString() {
            return Optional.empty();
        }

        @Override
        public Optional<Jid> toContentJid() {
            return Optional.empty();
        }

        @Override
        public Optional<InputStream> toContentStream() {
            return Optional.empty();
        }

        @Override
        public boolean equals(Object o) {
            return switch (o) {
                case EmptyNode(var thatDescription, var thatAttributes) -> Objects.equals(description, thatDescription)
                                                                           && Objects.equals(attributes, thatAttributes);
                case TextNode(var thatDescription, var thatAttributes, var thatContent) -> Objects.equals(description, thatDescription)
                        && Objects.equals(attributes, thatAttributes)
                        && hasContent(thatContent);
                case JidNode(var thatDescription, var thatAttributes, var thatContent) -> Objects.equals(description, thatDescription)
                        && Objects.equals(attributes, thatAttributes)
                        && hasContent(thatContent);
                case BytesNode(var thatDescription, var thatAttributes, var thatContent) -> Objects.equals(description, thatDescription)
                        && Objects.equals(attributes, thatAttributes)
                        && hasContent(thatContent);
                case ContainerNode(var thatDescription, var thatAttributes, var thatContent) -> Objects.equals(description, thatDescription)
                        && Objects.equals(attributes, thatAttributes)
                        && Objects.equals(children, thatContent);
                case null, default -> false;
            };
        }

        @Override
        public int hashCode() {
            return Objects.hash(description, attributes, children);
        }

        /**
         * Returns a debug oriented string for this container node.
         * @return the debug string
         */
        @Override
        @WhatsAppWebExport(moduleName = "WAXmlNode",
                exports = {"XmlNode", "attrsToString"},
                adaptation = WhatsAppAdaptation.ADAPTED)
        public String toString() {
            var result = new StringBuilder();
            result.append("Node[description=");
            result.append(description());

            if(!attributes.isEmpty()) {
                result.append(", attributes=");
                result.append(attributes);
            }

            if(!children.isEmpty()) {
                result.append(", children=");
                result.append(children);
            }

            result.append("]");

            return result.toString();
        }
    }
}
