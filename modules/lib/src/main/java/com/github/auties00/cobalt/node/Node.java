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
 * Represents an immutable node in a WhatsApp stanza tree.
 *
 * <p>Every message exchanged with the WhatsApp server is a tree of nodes
 * serialised through the WAWap binary protocol. A node carries a tag name
 * (the {@code description}), an insertion-ordered map of attributes, and
 * an optional content slot that holds either plain text, a single
 * {@link Jid}, a binary blob, or a list of child nodes. The sealed
 * hierarchy mirrors the five wire shapes a node can take and the read
 * surface is exhaustive: nothing in the protocol surfaces a node value
 * that this interface cannot return.
 *
 * <p>The five concrete variants are:
 * <ul>
 *   <li>{@link EmptyNode} for nodes without a content slot
 *   <li>{@link TextNode} for nodes whose content is a UTF-8 string
 *   <li>{@link JidNode} for nodes whose content is a single JID
 *   <li>{@link BytesNode} for nodes whose content is a binary blob
 *   <li>{@link ContainerNode} for nodes whose content is a child list
 * </ul>
 *
 * <p>Attribute and content readers come in three flavours: the
 * {@code getXxx} family returns an {@link Optional} or
 * {@link OptionalInt}/{@link OptionalLong}/{@link OptionalDouble} when
 * absent; the {@code getXxx(key, default)} family returns the value or
 * a caller-supplied fallback; the {@code getRequiredXxx} family throws
 * when the value is absent. Each family has a {@code streamXxx} mirror
 * that yields a single-element or empty stream so callers can compose
 * conversions inside a pipeline.
 *
 * <p>Reading attributes from an inbound stanza:
 * {@snippet :
 *     String id = node.getRequiredAttributeAsString("id");
 *     Jid from = node.getAttributeAsJid("from").orElse(null);
 *     boolean retry = node.getAttributeAsBool("retry", false);
 * }
 *
 * <p>Walking children:
 * {@snippet :
 *     Node body = node.getRequiredChild("body");
 *     List<Node> participants = node.streamChildren("participant").toList();
 * }
 *
 * @apiNote
 * Inbound nodes are produced by {@link NodeReader} from socket bytes;
 * outbound nodes are produced through {@link NodeBuilder} and
 * serialised by {@link NodeWriter}. The Cobalt class plays the same
 * role as the {@code WapNode} class in {@code WAWap}; {@code WAXmlNode}
 * is the WA Web debug-rendering helper that this interface's
 * {@code toString} overrides mirror.
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
     * @apiNote
     * The {@code tag} field of {@code WAWap.WapNode}; identifies the
     * stanza shape (for example {@code "iq"}, {@code "message"},
     * {@code "presence"}, {@code "receipt"}). Never {@code null} for
     * a valid node.
     *
     * @return the non null tag name
     */
    String description();

    /**
     * Returns whether this node's description equals the supplied value.
     *
     * @apiNote
     * Used by routing code (children walkers, dispatch tables) that
     * branches on the tag without having to call
     * {@link Objects#equals(Object, Object)} explicitly.
     *
     * @param description the description to compare against
     * @return {@code true} when the descriptions match
     */
    default boolean hasDescription(String description) {
        return Objects.equals(description(), description);
    }

    /**
     * Returns the attributes attached to this node in insertion order.
     *
     * @apiNote
     * The map is unmodifiable; callers must not attempt to add or
     * remove entries. Insertion order is preserved so re-encoded
     * stanzas round-trip stably even though the server is order-
     * agnostic on the semantic level.
     *
     * @return an unmodifiable sequenced map of attribute names to
     *         values
     */
    SequencedMap<String, NodeAttribute> attributes();

    /**
     * Returns the attribute associated with the supplied key, if any.
     *
     * @apiNote
     * The base lookup; every other {@code getAttributeAs...} helper
     * is a typed view over the result of this call.
     *
     * @param key the attribute key to look up
     * @return an {@link Optional} that holds the matching attribute,
     *         or empty when absent
     * @throws NullPointerException if {@code key} is {@code null}
     */
    default Optional<NodeAttribute> getAttribute(String key) {
        Objects.requireNonNull(key, "key cannot be null");
        return Optional.ofNullable(attributes().get(key));
    }

    /**
     * Returns the value of the supplied attribute as a string.
     *
     * @apiNote
     * Delegates to {@link NodeAttribute#toString()}; usable for
     * every attribute variant.
     *
     * @param key the attribute key
     * @return an {@link Optional} that holds the string value, or
     *         empty when the attribute is absent
     */
    default Optional<String> getAttributeAsString(String key) {
        return getAttribute(key)
                .map(NodeAttribute::toString);
    }

    /**
     * Returns the value of the supplied attribute parsed as a boolean.
     *
     * @apiNote
     * Delegates to {@link Boolean#parseBoolean(String)} over the
     * attribute's textual view; the WA wire protocol carries
     * booleans as the literal strings {@code "true"} and
     * {@code "false"}.
     *
     * @param key the attribute key
     * @return an {@link Optional} that holds the parsed boolean, or
     *         empty when the attribute is absent
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
     * @apiNote
     * Delegates to {@link NodeAttribute#toBytes()}; the result is a
     * fresh array for {@link NodeAttribute.TextAttribute} and
     * {@link NodeAttribute.JidAttribute} but is shared with the
     * underlying {@link NodeAttribute.BytesAttribute} instance,
     * which the caller must not mutate.
     *
     * @param key the attribute key
     * @return an {@link Optional} that holds the byte array, or
     *         empty when the attribute is absent
     */
    default Optional<byte[]> getAttributeAsBytes(String key) {
        return getAttribute(key)
                .map(NodeAttribute::toBytes);
    }

    /**
     * Returns the value of the supplied attribute as a byte array,
     * falling back to {@code defaultValue} when absent.
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
     * @apiNote
     * Delegates to {@link NodeAttribute#toJid()}; always succeeds
     * for {@link NodeAttribute.JidAttribute} and tries a parse for
     * the textual and binary variants.
     *
     * @param key the attribute key
     * @return an {@link Optional} that holds the parsed JID, or
     *         empty when the attribute is absent or unparseable
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
     * @apiNote
     * Distinct from the {@code long}-returning overload because the
     * fallback itself may be {@code null}, which is impossible to
     * express with a primitive return type.
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
     * @return an {@link OptionalInt} that holds the parsed value, or
     *         empty when the attribute is absent or unparseable
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
     * @apiNote
     * Distinct from the {@code int}-returning overload because the
     * fallback itself may be {@code null}.
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
     * @apiNote
     * Distinct from the {@code double}-returning overload because
     * the fallback itself may be {@code null}.
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
     * Returns a single-element stream that yields the matching attribute,
     * or an empty stream when absent.
     *
     * @apiNote
     * The stream form is convenient when a caller already has a
     * pipeline that wants to {@code flatMap} into an attribute
     * lookup without breaking the stream.
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
     * Returns a single-element stream that yields the attribute as a
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
     * Returns a single-element stream that yields the attribute parsed
     * as a boolean, or an empty stream when absent.
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
     * Returns a single-element stream that yields the attribute as a byte
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
     * Returns a single-element stream that yields the attribute parsed
     * as a {@link Jid}, or an empty stream when absent or unparseable.
     *
     * @apiNote
     * The unparseable case folds into the absent case here; callers
     * that need to distinguish the two should use the
     * {@code getAttributeAsJid} variants instead.
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
     * Returns a single-element stream that yields the attribute parsed
     * as a {@code long}, or an empty stream when absent or unparseable.
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
     * Returns a single-element stream that yields the attribute parsed
     * as an {@code int}, or an empty stream when absent or unparseable.
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
     * Returns a single-element stream that yields the attribute parsed
     * as a {@code double}, or an empty stream when absent or unparseable.
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
     * @apiNote
     * Used by inbound handlers that treat a missing attribute as a
     * malformed-stanza error rather than as a recoverable absence;
     * the exception message names the missing key and the
     * containing node's description and attribute map so log scans
     * can locate the source stanza.
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
     * @apiNote
     * Distinguishes absence ({@link NoSuchElementException}) from
     * unparseability ({@link IllegalArgumentException}) so inbound
     * handlers can route the two failure modes differently.
     *
     * @param key the attribute key
     * @return the parsed JID
     * @throws NoSuchElementException   if the attribute is absent
     * @throws IllegalArgumentException if the attribute does not parse
     *                                  to a valid JID
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
     * @throws IllegalArgumentException if the attribute does not parse
     *                                  to a valid long
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
     * @throws IllegalArgumentException if the attribute does not parse
     *                                  to a valid int
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
     * @throws IllegalArgumentException if the attribute does not parse
     *                                  to a valid double
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
     * Returns whether this node carries an attribute with the supplied
     * key whose textual view equals {@code value}.
     *
     * @apiNote
     * Used in dispatch chains that gate on a specific string
     * literal (for example {@code type == "set"} on an iq).
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
     * Returns whether this node carries an attribute with the supplied
     * key whose byte view equals {@code value}.
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
     * Returns whether this node carries an attribute with the supplied
     * key that parses to the supplied {@link Jid}.
     *
     * @param key   the attribute key
     * @param value the expected JID
     * @return {@code true} when the attribute exists and parses to
     *         {@code value}
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
     * Returns whether this node carries an attribute with the supplied
     * key that parses to the supplied {@code long}.
     *
     * @param key   the attribute key
     * @param value the expected long value
     * @return {@code true} when the attribute exists and parses to
     *         {@code value}
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
     * Returns whether this node carries an attribute with the supplied
     * key that parses to the supplied {@code int}.
     *
     * @param key   the attribute key
     * @param value the expected int value
     * @return {@code true} when the attribute exists and parses to
     *         {@code value}
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
     * Returns whether this node carries an attribute with the supplied
     * key that parses to the supplied {@code double}.
     *
     * @param key   the attribute key
     * @param value the expected double value
     * @return {@code true} when the attribute exists and parses to
     *         {@code value}
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
     * Returns whether this node carries an attribute with the supplied
     * key that parses to the supplied boolean.
     *
     * @param key   the attribute key
     * @param value the expected boolean value
     * @return {@code true} when the attribute exists and parses to
     *         {@code value}
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
     * Returns whether this node carries any content slot at all.
     *
     * @apiNote
     * {@code false} only for {@link EmptyNode}; every other variant
     * carries some content. The serialiser uses this to decide
     * whether to allocate a content token in the node's size
     * header.
     *
     * @return {@code false} for {@link EmptyNode}, {@code true} for
     *         every other variant
     */
    boolean hasContent();

    /**
     * Returns whether this node's content equals the supplied text.
     *
     * @apiNote
     * Variant-specific: {@link TextNode} compares strings,
     * {@link JidNode} compares the canonical JID string,
     * {@link BytesNode} decodes the blob and compares as text. The
     * empty and container variants always return {@code false}.
     *
     * @param content the expected text content
     * @return {@code true} when the node carries textual content
     *         equal to {@code content}
     */
    boolean hasContent(String content);

    /**
     * Returns whether this node's content equals the supplied JID.
     *
     * @apiNote
     * Variant-specific: {@link JidNode} compares JIDs directly;
     * {@link TextNode} and {@link BytesNode} compare the JID's
     * canonical string against the textual view. The empty and
     * container variants always return {@code false}.
     *
     * @param content the expected JID content
     * @return {@code true} when the node carries JID content equal
     *         to {@code content}
     */
    boolean hasContent(Jid content);

    /**
     * Returns whether this node's content equals the supplied byte
     * array.
     *
     * @apiNote
     * Variant-specific: {@link BytesNode} compares blobs directly;
     * {@link TextNode} and {@link JidNode} compare the textual view
     * to a UTF-8 decoded version of {@code content}. The empty and
     * container variants always return {@code false}.
     *
     * @param content the expected binary content
     * @return {@code true} when the node carries binary content
     *         equal to {@code content}
     */
    boolean hasContent(byte[] content);

    /**
     * Returns the encoded size of this node measured in token slots.
     *
     * @apiNote
     * Used by {@link NodeWriter} to emit the {@link com.github.auties00.cobalt.node.binary.NodeTags#LIST_8}
     * or {@link com.github.auties00.cobalt.node.binary.NodeTags#LIST_16}
     * size header at the start of the encoded node. The count is
     * one for the description, two for every attribute (key plus
     * value), and one when a content slot is populated.
     *
     * @return the number of token slots required by the node
     */
    default int size() {
        return 1
               + (attributes().size() * 2)
               + (hasContent() ? 1 : 0);
    }

    /**
     * Returns the content of this node as a byte array when applicable.
     *
     * @apiNote
     * Always succeeds for {@link BytesNode}; produces a UTF-8
     * encoding of the textual view for {@link TextNode} and
     * {@link JidNode}; returns {@link Optional#empty()} for
     * {@link EmptyNode} and {@link ContainerNode}.
     *
     * @return an {@link Optional} that holds the content as bytes,
     *         or empty when the variant has no convertible content
     */
    Optional<byte[]> toContentBytes();

    /**
     * Returns a single-element stream that yields the content as a byte
     * array, or an empty stream when no conversion is possible.
     *
     * @return a {@link Stream} that yields the byte array or nothing
     */
    default Stream<byte[]> streamContentBytes() {
        return toContentBytes()
                .stream();
    }

    /**
     * Returns the content of this node as an {@link InputStream} when
     * applicable.
     *
     * @apiNote
     * Wraps the same bytes that {@link #toContentBytes()} would
     * return in a {@link ByteArrayInputStream}; useful for callers
     * that want to decode a payload incrementally.
     *
     * @return an {@link Optional} that holds the content as a
     *         stream, or empty when the variant has no convertible
     *         content
     */
    Optional<InputStream> toContentStream();

    /**
     * Returns a single-element stream that yields the content as an
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
     * Returns the content of this node as a string when applicable.
     *
     * @apiNote
     * Always succeeds for {@link TextNode}; produces the canonical
     * string for {@link JidNode}; decodes the blob under the
     * platform default charset for {@link BytesNode}; returns
     * {@link Optional#empty()} for {@link EmptyNode} and
     * {@link ContainerNode}.
     *
     * @return an {@link Optional} that holds the content as text,
     *         or empty when the variant has no convertible content
     */
    Optional<String> toContentString();

    /**
     * Returns a single-element stream that yields the content as a
     * string, or an empty stream when no conversion is possible.
     *
     * @return a {@link Stream} that yields the string or nothing
     */
    default Stream<String> streamContentString() {
        return toContentString()
                .stream();
    }

    /**
     * Returns the content of this node parsed as a boolean when
     * applicable.
     *
     * @apiNote
     * Delegates to {@link #toContentString()} and then to
     * {@link Boolean#parseBoolean(String)}.
     *
     * @return an {@link Optional} that holds the parsed boolean,
     *         or empty when no conversion is possible
     */
    default Optional<Boolean> toContentBool() {
        return toContentString()
                .map(Boolean::parseBoolean);
    }

    /**
     * Returns a single-element stream that yields the content parsed as
     * a boolean, or an empty stream when no conversion is possible.
     *
     * @return a {@link Stream} that yields the parsed boolean or
     *         nothing
     */
    default Stream<Boolean> streamContentBool() {
        return toContentBool()
                .stream();
    }

    /**
     * Returns the content of this node parsed as an {@link Integer} when
     * applicable.
     *
     * @apiNote
     * A non-integer textual payload returns
     * {@link Optional#empty()} rather than throwing, so callers
     * composing inside a stream pipeline can chain on it without a
     * try/catch.
     *
     * @return an {@link Optional} that holds the parsed int, or
     *         empty when the content does not represent a valid int
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
     * Returns a single-element stream that yields the content parsed as
     * an {@link Integer}, or an empty stream when parsing fails.
     *
     * @return a {@link Stream} that yields the parsed int or nothing
     */
    default Stream<Integer> streamContentInt() {
        return toContentInt()
                .stream();
    }

    /**
     * Returns the content of this node parsed as a {@link Long} when
     * applicable.
     *
     * @apiNote
     * A non-long textual payload returns {@link Optional#empty()}
     * rather than throwing.
     *
     * @return an {@link Optional} that holds the parsed long, or
     *         empty when the content does not represent a valid long
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
     * Returns a single-element stream that yields the content parsed as
     * a {@link Long}, or an empty stream when parsing fails.
     *
     * @return a {@link Stream} that yields the parsed long or nothing
     */
    default Stream<Long> streamContentLong() {
        return toContentLong()
                .stream();
    }

    /**
     * Returns the content of this node as a {@link Jid} when applicable.
     *
     * @apiNote
     * Always succeeds for {@link JidNode}; tries a parse for
     * {@link TextNode} and {@link BytesNode}; returns
     * {@link Optional#empty()} for {@link EmptyNode} and
     * {@link ContainerNode}.
     *
     * @return an {@link Optional} that holds the JID, or empty when
     *         the content does not parse to a valid JID
     */
    Optional<Jid> toContentJid();

    /**
     * Returns a single-element stream that yields the content as a
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
     * @apiNote
     * Non-empty only for {@link ContainerNode}; every other variant
     * returns an empty list. The returned collection is
     * unmodifiable for the container variant.
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
     * Returns the first child of this node, if any.
     *
     * @apiNote
     * Used in walkers that consume a known wrapper node (for
     * example {@code <result><body>...</body></result>}) and want
     * the singular inner child without filtering by description.
     *
     * @return an {@link Optional} that holds the first child, or
     *         empty when the node has none
     */
    default Optional<Node> getChild() {
        var children = children();
        return children.isEmpty()
                ? Optional.empty()
                : Optional.ofNullable(children.getFirst());
    }

    /**
     * Returns a single-element stream that yields the first child, or an
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
     * @apiNote
     * The canonical lookup for unwrapping a known sub-element from
     * a container stanza (for example the {@code <body>} child of
     * an iq result).
     *
     * @param description the description to match
     * @return an {@link Optional} that holds the matching child, or
     *         empty when none matches
     * @throws NullPointerException if {@code description} is {@code null}
     */
    default Optional<Node> getChild(String description) {
        Objects.requireNonNull(description, "description cannot be null");
        return streamChildren(description)
                .findFirst();
    }

    /**
     * Returns the first child whose description matches any of the
     * supplied descriptions, tested in order.
     *
     * @apiNote
     * Used when a stanza may carry one of several alternative
     * inner shapes (for example {@code <body>} or {@code <error>}
     * inside an iq result); the order of {@code descriptions}
     * encodes the caller's preference.
     *
     * @param descriptions the descriptions to match
     * @return an {@link Optional} that holds the first match, or
     *         empty when none matches
     * @throws NullPointerException if {@code descriptions} or any
     *                              element is {@code null}
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
     * @apiNote
     * Used by inbound handlers that treat a missing child as a
     * malformed-stanza error.
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
     * @apiNote
     * The order of {@code descriptions} encodes the caller's
     * preference; the first match wins.
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
     * Returns a single-element stream that yields the first child whose
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
     * Returns a single-element stream that yields the first child whose
     * description matches any of the supplied descriptions, or an empty
     * stream when none matches.
     *
     * @param descriptions the descriptions to match
     * @return a {@link Stream} that yields the matching child or nothing
     * @throws NullPointerException if {@code descriptions} or any
     *                              element is {@code null}
     */
    default Stream<Node> streamChild(String... descriptions) {
        return getChild(descriptions)
                .stream();
    }

    /**
     * Returns every child whose description matches {@code description},
     * preserving declaration order.
     *
     * @apiNote
     * Used to iterate repeated sub-elements (for example
     * {@code <participant>} entries inside a group iq result).
     *
     * @param description the description to match
     * @return a sequenced collection of matching children, possibly
     *         empty
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
     * @return a sequenced collection of matching children, possibly
     *         empty
     * @throws NullPointerException if {@code descriptions} or any
     *                              element is {@code null}
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
     * Returns a stream of every child whose description matches any of
     * the supplied descriptions, preserving declaration order.
     *
     * @apiNote
     * Pre-builds a {@link LinkedHashSet} of the requested
     * descriptions so each child's membership check is a single
     * hash lookup; the JS source iterates the descriptions in the
     * outer loop instead.
     *
     * @param descriptions the descriptions to match
     * @return a {@link Stream} over the matching children
     * @throws NullPointerException if {@code descriptions} or any
     *                              element is {@code null}
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
     * @throws NullPointerException if {@code descriptions} or any
     *                              element is {@code null}
     */
    default boolean hasChild(String... descriptions) {
        return getChild(descriptions).isPresent();
    }

    /**
     * Node variant that carries no content slot.
     *
     * @apiNote
     * Used for stanzas whose meaning is conveyed entirely by the
     * tag and attributes (presence updates, acknowledgements, and
     * the {@code <pair-device>} family of handshake nodes are
     * typical examples).
     *
     * @param description the tag name
     * @param attributes  the attribute map
     */
    record EmptyNode(String description, SequencedMap<String, NodeAttribute> attributes) implements Node {
        /**
         * Builds an empty node, rejecting {@code null} arguments.
         *
         * @apiNote
         * Maps to the {@code WAWap.makeWapNode(tag, attrs, null)}
         * call shape; the encoder's {@code WAWap.re} branch with no
         * content emits exactly this variant.
         *
         * @throws NullPointerException if any argument is {@code null}
         */
        @WhatsAppWebExport(moduleName = "WAXmlNode", exports = "XmlNode",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public EmptyNode {
            Objects.requireNonNull(description, "description cannot be null");
            Objects.requireNonNull(attributes, "attributes cannot be null");
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation wraps the underlying map with
         * {@link Collections#unmodifiableSequencedMap(SequencedMap)}
         * on every call rather than freezing it once; the cost is
         * a single object allocation and the read surface stays
         * truly read-only.
         */
        @Override
        public SequencedMap<String, NodeAttribute> attributes() {
            return Collections.unmodifiableSequencedMap(attributes);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasContent() {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasContent(String content) {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasContent(Jid content) {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Optional<String> toContentString() {
            return Optional.empty();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasContent(byte[] content) {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Optional<Jid> toContentJid() {
            return Optional.empty();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public SequencedCollection<Node> children() {
            return List.of();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Optional<byte[]> toContentBytes() {
            return Optional.empty();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Optional<InputStream> toContentStream() {
            return Optional.empty();
        }

        /**
         * Returns whether {@code o} is a {@link Node} that compares
         * structurally equal to this one.
         *
         * @apiNote
         * Cross-variant equality is supported: an
         * {@link EmptyNode} equals a {@link TextNode} with an
         * empty payload (the empty case is the same on the wire),
         * and equals other content-less {@link BytesNode} instances
         * where {@code hasContent(content)} succeeds. This matches
         * the wire-level reality that a node with no content and a
         * node with an empty payload decode to indistinguishable
         * bytes.
         *
         * @param o the object to compare against
         * @return {@code true} when the two nodes match
         *         structurally
         */
        @Override
        public boolean equals(Object o) {
            return switch (o) {
                case EmptyNode(var thatDescription, var thatAttributes) -> Objects.equals(description, thatDescription) && Objects.equals(attributes, thatAttributes);
                case TextNode(var thatDescription, var thatAttributes, var thatContent) -> Objects.equals(description, thatDescription) && Objects.equals(attributes, thatAttributes) && hasContent(thatContent);
                case BytesNode(var thatDescription, var thatAttributes, var thatContent) -> Objects.equals(description, thatDescription) && Objects.equals(attributes, thatAttributes) && hasContent(thatContent);
                case null, default -> false;
            };
        }

        /**
         * Returns a hash code consistent with {@link #equals(Object)}.
         *
         * @return the hash of the description and attribute map
         */
        @Override
        public int hashCode() {
            return Objects.hash(description, attributes);
        }

        /**
         * Returns a debug-oriented string for this empty node.
         *
         * @apiNote
         * Mirrors {@code WAXmlNode.XmlNode#toString} combined with
         * {@code WAXmlNode.attrsToString}; the output is not part
         * of the wire format and exists only for logs and stack
         * traces.
         *
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
     * @apiNote
     * Used for stanzas whose payload is textual: status blurbs,
     * identifiers serialised as text, free-form message bodies,
     * and any inbound stanza whose content slot decoded as a
     * dictionary token, packed nibble/hex string, or
     * length-prefixed UTF-8 blob.
     *
     * @param description the tag name
     * @param attributes  the attribute map
     * @param content     the textual payload
     */
    record TextNode(String description, SequencedMap<String, NodeAttribute> attributes, String content) implements Node {
        /**
         * Builds a text node, rejecting {@code null} arguments.
         *
         * @throws NullPointerException if any argument is {@code null}
         */
        @WhatsAppWebExport(moduleName = "WAXmlNode", exports = "XmlNode",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public TextNode {
            Objects.requireNonNull(description, "description cannot be null");
            Objects.requireNonNull(attributes, "attributes cannot be null");
            Objects.requireNonNull(content, "content cannot be null");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public SequencedMap<String, NodeAttribute> attributes() {
            return Collections.unmodifiableSequencedMap(attributes);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasContent() {
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasContent(String content) {
            return Objects.equals(this.content, content);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation compares the textual view of the
         * supplied JID against the stored string; a {@code null}
         * {@code content} returns {@code false} without
         * dereferencing.
         */
        @Override
        public boolean hasContent(Jid content) {
            return content != null && Objects.equals(this.content, content.toString());
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation decodes the supplied bytes under the
         * platform default charset, matching {@link #toContentBytes()}'s
         * encoding direction.
         */
        @Override
        public boolean hasContent(byte[] content) {
            return content != null && Objects.equals(this.content, new String(content));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Optional<byte[]> toContentBytes() {
            return Optional.of(content.getBytes());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Optional<InputStream> toContentStream() {
            return Optional.of(new ByteArrayInputStream(content.getBytes()));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Optional<Jid> toContentJid() {
            try {
                var result = Jid.of(content);
                return Optional.of(result);
            }catch (WhatsAppMalformedJidException exception) {
                return Optional.empty();
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Optional<String> toContentString() {
            return Optional.of(content);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public SequencedCollection<Node> children() {
            return List.of();
        }

        /**
         * Returns whether {@code o} is a {@link Node} that compares
         * structurally equal to this one.
         *
         * @apiNote
         * Cross-variant equality is supported: an {@link EmptyNode}
         * equals this node when this node's payload is the empty
         * string; {@link JidNode} and {@link BytesNode} match
         * through their textual views via {@link #hasContent(Jid)}
         * and {@link #hasContent(byte[])}.
         *
         * @param o the object to compare against
         * @return {@code true} when the two nodes match
         *         structurally
         */
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

        /**
         * Returns a hash code consistent with {@link #equals(Object)}.
         *
         * @return the hash of the description, attribute map, and
         *         textual content
         */
        @Override
        public int hashCode() {
            return Objects.hash(description, attributes, content);
        }

        /**
         * Returns a debug-oriented string for this text node.
         *
         * @apiNote
         * Mirrors {@code WAXmlNode.XmlNode#toString} combined with
         * {@code WAXmlNode.attrsToString}.
         *
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
     * @apiNote
     * Used for stanzas that address a user, group, or device. The
     * JID is encoded under one of the four JID wire shapes
     * ({@link com.github.auties00.cobalt.node.binary.NodeTags#JID_PAIR},
     * {@link com.github.auties00.cobalt.node.binary.NodeTags#AD_JID},
     * {@link com.github.auties00.cobalt.node.binary.NodeTags#JID_INTEROP},
     * {@link com.github.auties00.cobalt.node.binary.NodeTags#JID_FB})
     * by the writer, picked from the JID's server and device.
     *
     * @param description the tag name
     * @param attributes  the attribute map
     * @param content     the JID payload
     */
    record JidNode(String description, SequencedMap<String, NodeAttribute> attributes, Jid content) implements Node {
        /**
         * Builds a JID node, rejecting {@code null} arguments.
         *
         * @throws NullPointerException if any argument is {@code null}
         */
        @WhatsAppWebExport(moduleName = "WAXmlNode", exports = "XmlNode",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public JidNode {
            Objects.requireNonNull(description, "description cannot be null");
            Objects.requireNonNull(attributes, "attributes cannot be null");
            Objects.requireNonNull(content, "content cannot be null");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public SequencedMap<String, NodeAttribute> attributes() {
            return Collections.unmodifiableSequencedMap(attributes);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasContent() {
            return true;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation compares against the JID's canonical
         * string form.
         */
        @Override
        public boolean hasContent(String content) {
            return Objects.equals(this.content.toString(), content);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasContent(Jid content) {
            return Objects.equals(this.content, content);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation decodes the supplied bytes under the
         * platform default charset and compares against the JID's
         * canonical string form.
         */
        @Override
        public boolean hasContent(byte[] content) {
            return content != null && Objects.equals(this.content.toString(), new String(content));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Optional<String> toContentString() {
            return Optional.of(content.toString());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Optional<Jid> toContentJid() {
            return Optional.of(content);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Optional<byte[]> toContentBytes() {
            return Optional.of(content.toString().getBytes());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Optional<InputStream> toContentStream() {
            return Optional.of(new ByteArrayInputStream(content.toString().getBytes()));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public SequencedCollection<Node> children() {
            return List.of();
        }

        /**
         * Returns whether {@code o} is a {@link Node} that compares
         * structurally equal to this one.
         *
         * @apiNote
         * Matches structurally against {@link TextNode},
         * {@link JidNode}, and {@link BytesNode} through their
         * textual views.
         *
         * @param o the object to compare against
         * @return {@code true} when the two nodes match
         *         structurally
         */
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

        /**
         * Returns a hash code consistent with {@link #equals(Object)}.
         *
         * @return the hash of the description, attribute map, and
         *         JID content
         */
        @Override
        public int hashCode() {
            return Objects.hash(description, attributes, content);
        }

        /**
         * Returns a debug-oriented string for this JID node.
         *
         * @apiNote
         * Mirrors {@code WAXmlNode.XmlNode#toString} combined with
         * {@code WAXmlNode.attrsToString}.
         *
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
     * @apiNote
     * Used for stanzas that carry raw bytes: Signal ciphertext,
     * media thumbnails, identity blobs, and any inbound stanza
     * whose content slot decoded as a length-prefixed binary
     * shape ({@link com.github.auties00.cobalt.node.binary.NodeTags#BINARY_8},
     * {@link com.github.auties00.cobalt.node.binary.NodeTags#BINARY_20},
     * or {@link com.github.auties00.cobalt.node.binary.NodeTags#BINARY_32}).
     *
     * @param description the tag name
     * @param attributes  the attribute map
     * @param content     the binary payload
     */
    record BytesNode(String description, SequencedMap<String, NodeAttribute> attributes, byte[] content) implements Node {
        /**
         * Builds a bytes node, rejecting {@code null} arguments.
         *
         * @throws NullPointerException if any argument is {@code null}
         */
        @WhatsAppWebExport(moduleName = "WAXmlNode", exports = "XmlNode",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public BytesNode {
            Objects.requireNonNull(description, "description cannot be null");
            Objects.requireNonNull(attributes, "attributes cannot be null");
            Objects.requireNonNull(content, "content cannot be null");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public SequencedMap<String, NodeAttribute> attributes() {
            return Collections.unmodifiableSequencedMap(attributes);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Optional<byte[]> toContentBytes() {
            return Optional.of(content);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Optional<InputStream> toContentStream() {
            return Optional.of(new ByteArrayInputStream(content));
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation routes the parse through the
         * protobuf {@link ProtobufString#lazy(byte[])} fast path
         * so the JID parser walks the underlying bytes directly,
         * avoiding the intermediate UTF-8 string allocation that
         * {@code Jid.of(new String(content))} would incur.
         */
        @Override
        public Optional<Jid> toContentJid() {
            try {
                var result = Jid.of(ProtobufString.lazy(content));
                return Optional.of(result);
            } catch (WhatsAppMalformedJidException exception) {
                return Optional.empty();
            }
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation decodes the blob under the platform
         * default charset; non-UTF-8 bytes produce a string that
         * is faithful to the platform decoder but may not
         * round-trip.
         */
        @Override
        public Optional<String> toContentString() {
            var decoded = new String(content);
            return Optional.of(decoded);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasContent() {
            return true;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation decodes the stored blob under the
         * platform default charset and compares against the
         * supplied string.
         */
        @Override
        public boolean hasContent(String content) {
            return Objects.equals(new String(this.content), content);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation decodes the stored blob under the
         * platform default charset and compares against the
         * canonical JID string of the supplied JID.
         */
        @Override
        public boolean hasContent(Jid content) {
            return content != null && Objects.equals(new String(this.content), content.toString());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasContent(byte[] content) {
            return Arrays.equals(this.content, content);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public SequencedCollection<Node> children() {
            return List.of();
        }

        /**
         * Returns whether {@code o} is a {@link Node} that compares
         * structurally equal to this one.
         *
         * @apiNote
         * Matches structurally against {@link TextNode},
         * {@link JidNode}, and {@link BytesNode}; cross-variant
         * matches use the textual or byte views as appropriate.
         *
         * @param o the object to compare against
         * @return {@code true} when the two nodes match
         *         structurally
         */
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

        /**
         * Returns a hash code consistent with {@link #equals(Object)}.
         *
         * @return the hash of the description, attribute map, and
         *         the hash of the byte content
         */
        @Override
        public int hashCode() {
            return Objects.hash(description, attributes, Arrays.hashCode(content));
        }

        /**
         * Returns a debug-oriented string for this bytes node.
         *
         * @apiNote
         * Mirrors {@code WAXmlNode.XmlNode#toString} combined with
         * {@code WAXmlNode.attrsToString} and
         * {@code WAXmlNode.uint8ArrayToDebugString}. For
         * descriptions that conventionally carry textual payloads
         * ({@code "result"}, {@code "query"}, {@code "body"}) the
         * bytes are decoded as a string for readability; for
         * everything else the raw byte array is rendered through
         * {@link Arrays#toString(byte[])}.
         *
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
     * @apiNote
     * The recursive case of the stanza tree: a container node
     * groups a list of children under a common tag, matching the
     * XML element-with-children shape used pervasively across the
     * WhatsApp protocol (iq results, group payloads, syncd
     * collections, and so on).
     *
     * @param description the tag name
     * @param attributes  the attribute map
     * @param children    the child nodes in declaration order
     */
    record ContainerNode(String description, SequencedMap<String, NodeAttribute> attributes, SequencedCollection<Node> children) implements Node {
        /**
         * Builds a container node, rejecting {@code null} arguments.
         *
         * @throws NullPointerException if any argument is {@code null}
         */
        @WhatsAppWebExport(moduleName = "WAXmlNode", exports = "XmlNode",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public ContainerNode {
            Objects.requireNonNull(description, "description cannot be null");
            Objects.requireNonNull(attributes, "attributes cannot be null");
            Objects.requireNonNull(children, "children cannot be null");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public SequencedMap<String, NodeAttribute> attributes() {
            return Collections.unmodifiableSequencedMap(attributes);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public SequencedCollection<Node> children() {
            return Collections.unmodifiableSequencedCollection(children);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasContent() {
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasContent(Jid content) {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasContent(byte[] content) {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasContent(String content) {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Optional<byte[]> toContentBytes() {
            return Optional.empty();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Optional<String> toContentString() {
            return Optional.empty();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Optional<Jid> toContentJid() {
            return Optional.empty();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Optional<InputStream> toContentStream() {
            return Optional.empty();
        }

        /**
         * Returns whether {@code o} is a {@link Node} that compares
         * structurally equal to this one.
         *
         * @apiNote
         * Cross-variant equality matches the wire-level reality:
         * a container with an empty child list compares equal to
         * an {@link EmptyNode} of the same description and
         * attributes; matches against {@link TextNode},
         * {@link JidNode}, and {@link BytesNode} use the
         * appropriate {@code hasContent} variant on the other
         * side of the comparison.
         *
         * @param o the object to compare against
         * @return {@code true} when the two nodes match
         *         structurally
         */
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

        /**
         * Returns a hash code consistent with {@link #equals(Object)}.
         *
         * @return the hash of the description, attribute map, and
         *         child list
         */
        @Override
        public int hashCode() {
            return Objects.hash(description, attributes, children);
        }

        /**
         * Returns a debug-oriented string for this container node.
         *
         * @apiNote
         * Mirrors {@code WAXmlNode.XmlNode#toString} combined with
         * {@code WAXmlNode.attrsToString}; children are rendered
         * recursively through their own {@code toString}.
         *
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
