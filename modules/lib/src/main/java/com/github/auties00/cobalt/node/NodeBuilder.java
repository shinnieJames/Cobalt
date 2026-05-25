package com.github.auties00.cobalt.node;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidProvider;

import java.util.*;

/**
 * Fluent builder for assembling {@link Node} stanzas.
 *
 * <p>Stanzas are the unit of communication with the WhatsApp server. Every
 * outbound stanza in Cobalt is produced through a builder: a tag name is set
 * with {@link #description(String)}, attributes are accumulated through one of
 * the {@code attribute} overloads, and the payload is set through one of the
 * {@code content} overloads. A final call to {@link #build()} freezes the
 * accumulated state into an immutable {@link Node} of the variant matching the
 * populated content slot.
 *
 * <p>The {@code attribute} overloads are null-safe: a {@code null} value skips
 * the attribute instead of writing a literal {@code "null"}, and the
 * {@code condition} overload skips when the guard is {@code false}. This
 * removes the null check that every caller would otherwise need at every
 * attribute site.
 *
 * <p>Building an iq stanza for a profile-picture upload:
 * {@snippet :
 *     Node iq = new NodeBuilder()
 *             .description("iq")
 *             .attribute("id", "12345")
 *             .attribute("to", recipient)
 *             .attribute("type", "set")
 *             .attribute("xmlns", "w:profile:picture")
 *             .content(new NodeBuilder()
 *                     .description("picture")
 *                     .attribute("type", "image")
 *                     .content(imageBytes)
 *                     .build())
 *             .build();
 * }
 *
 * <p>Building a simple presence stanza without content:
 * {@snippet :
 *     Node presence = new NodeBuilder()
 *             .description("presence")
 *             .attribute("type", "available")
 *             .build();
 * }
 *
 * @implNote
 * The drop-on-null guard mirrors the {@code DROP_ATTR} sentinel handling in
 * the {@code WAWap.X} factory ({@code n!==F&&(u[t]=n)} where
 * {@code F = DROP_ATTR}).
 *
 * @see Node
 * @see NodeAttribute
 */
@WhatsAppWebModule(moduleName = "WAWap")
public final class NodeBuilder {
    /**
     * Holds the pending tag name of the node under construction.
     *
     * <p>Set by {@link #description(String)}. When {@code null} at
     * {@link #build()} time the resulting node carries an empty tag name.
     */
    private String description;

    /**
     * Holds the pending attributes accumulated in insertion order.
     *
     * <p>Insertion order is preserved so that the encoder emits attributes in
     * the order they were added; the server is order-agnostic on the semantic
     * level, so the only requirement is that the stanza round-trips stably.
     *
     * @implNote
     * This implementation backs the map with a {@link LinkedHashMap} to give
     * the deterministic iteration order the encoder relies on.
     */
    private final SequencedMap<String, NodeAttribute> attributes;

    /**
     * Holds the pending text content, or {@code null} when not set.
     *
     * <p>One of the four content slots; {@link #build()} dispatches on the
     * first non-null slot in the order text, JID, bytes, children.
     */
    private String textContent;

    /**
     * Holds the pending JID content, or {@code null} when not set.
     *
     * <p>Stored as a {@link JidProvider} so callers can pass a {@link Jid}
     * directly or any provider that resolves to one; the resolution to a
     * concrete {@link Jid} is deferred to {@link #build()}.
     */
    private JidProvider jidContent;

    /**
     * Holds the pending binary content, or {@code null} when not set.
     *
     * <p>Carries Signal ciphertext, media thumbnails, and any other payload
     * that must traverse the wire as opaque bytes.
     */
    private byte[] bytesContent;

    /**
     * Holds the pending child node content, or {@code null} when not set.
     *
     * <p>Lazily allocated to a fresh {@link ArrayList} the first time a
     * {@code content(Node...)} or {@code content(SequencedCollection)}
     * overload is invoked; subsequent calls append.
     */
    private SequencedCollection<Node> childrenContent;

    /**
     * Builds a fresh builder with no description, no attributes, and no
     * content.
     *
     * <p>The default starting point for every outbound stanza in Cobalt.
     */
    public NodeBuilder() {
        this.attributes = new LinkedHashMap<>();
    }

    /**
     * Sets the description (tag name) of the node under construction.
     *
     * <p>Mandatory in practice, since every meaningful stanza has a tag, but
     * not enforced by the builder; an unset description produces a node with
     * an empty tag name.
     *
     * @param description the tag name
     * @return this builder
     */
    public NodeBuilder description(String description) {
        this.description = description;
        return this;
    }

    /**
     * Adds a text attribute when the value is non null.
     *
     * <p>Passing {@code null} silently drops the attribute; use the
     * {@code condition}-taking overload for an explicit guard. Every other
     * {@code attribute} overload follows the same drop-on-null convention.
     *
     * @param key   the attribute key
     * @param value the attribute value, or {@code null} to skip
     * @return this builder
     */
    @WhatsAppWebExport(moduleName = "WAWap", exports = "DROP_ATTR",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public NodeBuilder attribute(String key, String value) {
        if(value != null) {
            this.attributes.put(key, new NodeAttribute.TextAttribute(value));
        }
        return this;
    }

    /**
     * Adds a text attribute when the value is non null and the condition is
     * {@code true}.
     *
     * <p>Removes the {@code if (cond) builder.attribute(key, value);}
     * boilerplate at every conditional call site.
     *
     * @param key       the attribute key
     * @param value     the attribute value, or {@code null} to skip
     * @param condition guard that must hold for the attribute to be written
     * @return this builder
     */
    public NodeBuilder attribute(String key, String value, boolean condition) {
        if(value != null && condition) {
            this.attributes.put(key, new NodeAttribute.TextAttribute(value));
        }
        return this;
    }

    /**
     * Adds a numeric attribute when the value is non null.
     *
     * <p>The number is serialised through {@link Number#toString()} into a
     * decimal string before the encoder runs the standard string compression
     * ladder.
     *
     * @param key   the attribute key
     * @param value the numeric value, or {@code null} to skip
     * @return this builder
     */
    public NodeBuilder attribute(String key, Number value) {
        if(value != null) {
            this.attributes.put(key, new NodeAttribute.TextAttribute(value.toString()));
        }
        return this;
    }

    /**
     * Adds a numeric attribute when the value is non null and the condition is
     * {@code true}.
     *
     * <p>The number is serialised through {@link Number#toString()}.
     *
     * @param key       the attribute key
     * @param value     the numeric value, or {@code null} to skip
     * @param condition guard that must hold for the attribute to be written
     * @return this builder
     */
    public NodeBuilder attribute(String key, Number value, boolean condition) {
        if(value != null && condition) {
            this.attributes.put(key, new NodeAttribute.TextAttribute(value.toString()));
        }
        return this;
    }

    /**
     * Adds a boolean attribute.
     *
     * <p>The value is serialised as {@code "true"} or {@code "false"}; the
     * WhatsApp wire protocol carries booleans as strings rather than as a
     * dedicated wire shape.
     *
     * @param key   the attribute key
     * @param value the boolean value
     * @return this builder
     */
    public NodeBuilder attribute(String key, boolean value) {
        this.attributes.put(key, new NodeAttribute.TextAttribute(Boolean.toString(value)));
        return this;
    }

    /**
     * Adds a boolean attribute when the condition is {@code true}.
     *
     * <p>The value is serialised as {@code "true"} or {@code "false"}.
     *
     * @param key       the attribute key
     * @param value     the boolean value
     * @param condition guard that must hold for the attribute to be written
     * @return this builder
     */
    public NodeBuilder attribute(String key, boolean value, boolean condition) {
        if(condition) {
            this.attributes.put(key, new NodeAttribute.TextAttribute(Boolean.toString(value)));
        }
        return this;
    }

    /**
     * Adds a JID attribute when the provider is non null.
     *
     * <p>The {@link JidProvider} is resolved eagerly into a concrete
     * {@link Jid} so the builder snapshot does not change if the provider's
     * state mutates later. {@code null} is accepted and skips the attribute.
     *
     * @param key   the attribute key
     * @param value the JID provider, or {@code null} to skip
     * @return this builder
     * @see Jid
     */
    public NodeBuilder attribute(String key, JidProvider value) {
        if(value != null) {
            this.attributes.put(key, new NodeAttribute.JidAttribute(value.toJid()));
        }
        return this;
    }

    /**
     * Adds a JID attribute when the provider is non null and the condition is
     * {@code true}.
     *
     * @param key       the attribute key
     * @param value     the JID provider, or {@code null} to skip
     * @param condition guard that must hold for the attribute to be written
     * @return this builder
     * @see Jid
     */
    public NodeBuilder attribute(String key, JidProvider value, boolean condition) {
        if(value != null && condition) {
            this.attributes.put(key, new NodeAttribute.JidAttribute(value.toJid()));
        }
        return this;
    }

    /**
     * Adds a binary attribute when the value is non null.
     *
     * <p>The blob is stored by reference; callers must not mutate the array
     * after handing it off. Carries already-encoded payloads (Signal
     * ciphertext, base64-decoded hashes, and similar) that should not pass
     * through the string compression ladder.
     *
     * @param key   the attribute key
     * @param value the binary value, or {@code null} to skip
     * @return this builder
     */
    public NodeBuilder attribute(String key, byte[] value) {
        if(value != null) {
            this.attributes.put(key, new NodeAttribute.BytesAttribute(value));
        }
        return this;
    }

    /**
     * Adds a binary attribute when the value is non null and the condition is
     * {@code true}.
     *
     * @param key       the attribute key
     * @param value     the binary value, or {@code null} to skip
     * @param condition guard that must hold for the attribute to be written
     * @return this builder
     */
    public NodeBuilder attribute(String key, byte[] value, boolean condition) {
        if(value != null && condition) {
            this.attributes.put(key, new NodeAttribute.BytesAttribute(value));
        }
        return this;
    }

    /**
     * Copies every entry from the supplied map into the pending attribute set,
     * preserving any prior attributes and overwriting on key collision.
     *
     * <p>Lets callers splice a pre-built attribute map into a fresh stanza,
     * for example forwarding a captured attribute set from one node to
     * another. A {@code null} map is treated as a no-op.
     *
     * @param attributes the entries to copy, or {@code null} to skip
     * @return this builder
     */
    public NodeBuilder attributes(Map<String, ? extends NodeAttribute> attributes) {
        if(attributes != null) {
            this.attributes.putAll(attributes);
        }
        return this;
    }

    /**
     * Sets the node content to a text value, clearing any previously set
     * content slot.
     *
     * <p>Carries free-form textual payloads (status blurbs, plain message
     * bodies) whose wire format passes through the string compression ladder.
     *
     * @param value the textual content
     * @return this builder
     */
    public NodeBuilder content(String value) {
        this.textContent = value;
        this.jidContent = null;
        this.bytesContent = null;
        this.childrenContent = null;
        return this;
    }

    /**
     * Sets the node content to a numeric value, clearing any previously set
     * content slot.
     *
     * <p>The number is serialised through {@link Objects#toString(Object)}.
     *
     * @param value the numeric content
     * @return this builder
     */
    public NodeBuilder content(Number value) {
        this.textContent = Objects.toString(value);
        this.jidContent = null;
        this.bytesContent = null;
        this.childrenContent = null;
        return this;
    }

    /**
     * Sets the node content to a boolean value, clearing any previously set
     * content slot.
     *
     * <p>The value is serialised as {@code "true"} or {@code "false"}; the
     * WhatsApp wire protocol has no dedicated boolean shape.
     *
     * @param value the boolean content
     * @return this builder
     */
    public NodeBuilder content(boolean value) {
        this.textContent = Objects.toString(value);
        this.jidContent = null;
        this.bytesContent = null;
        this.childrenContent = null;
        return this;
    }

    /**
     * Sets the node content to a JID, clearing any previously set content
     * slot.
     *
     * <p>The provider is captured by reference and resolved lazily at
     * {@link #build()} time.
     *
     * @param value the JID provider
     * @return this builder
     * @see Jid
     */
    public NodeBuilder content(JidProvider value) {
        this.textContent = null;
        this.jidContent = value;
        this.bytesContent = null;
        this.childrenContent = null;
        return this;
    }

    /**
     * Sets the node content to a binary blob, clearing any previously set
     * content slot.
     *
     * <p>The blob is stored by reference; do not mutate the array after
     * passing it in.
     *
     * @param value the binary content
     * @return this builder
     */
    public NodeBuilder content(byte[] value) {
        this.textContent = null;
        this.jidContent = null;
        this.bytesContent = value;
        this.childrenContent = null;
        return this;
    }

    /**
     * Appends a collection of child nodes to the pending children list,
     * clearing any non-child content previously set.
     *
     * <p>Additive: a second call merges its argument into the children already
     * accumulated, preserving order. {@code null} entries inside the supplied
     * collection are skipped.
     *
     * @implNote
     * This implementation filters out {@code null} entries to match the
     * {@code WAWap.X} {@code Array.isArray(n)} branch, which applies
     * {@code filter(Boolean)}.
     *
     * @param nodes the children to append, or {@code null} to skip
     * @return this builder
     */
    public NodeBuilder content(SequencedCollection<Node> nodes) {
        this.textContent = null;
        this.jidContent = null;
        this.bytesContent = null;
        if(childrenContent == null) {
            this.childrenContent = new ArrayList<>();
        }
        if(nodes != null) {
            for(var node : nodes) {
                if(node != null) {
                    this.childrenContent.add(node);
                }
            }
        }
        return this;
    }

    /**
     * Appends a varargs sequence of child nodes to the pending children list,
     * clearing any non-child content previously set.
     *
     * <p>Additive: a second call merges its arguments into the children
     * already accumulated, preserving order. {@code null} entries are skipped.
     *
     * @param nodes the children to append
     * @return this builder
     */
    public NodeBuilder content(Node... nodes) {
        this.textContent = null;
        this.jidContent = null;
        this.bytesContent = null;
        if(childrenContent == null) {
            this.childrenContent = new ArrayList<>();
        }
        if(nodes != null) {
            for(var node : nodes) {
                if(node != null) {
                    this.childrenContent.add(node);
                }
            }
        }
        return this;
    }

    /**
     * Returns whether an attribute with the supplied key is currently set on
     * this builder.
     *
     * <p>Lets callers that compose a stanza in branches decide whether to
     * override or skip based on what an earlier branch already added.
     *
     * @param key the attribute key
     * @return {@code true} when the attribute is present
     */
    public boolean hasAttribute(String key) {
        return attributes.containsKey(key);
    }

    /**
     * Returns whether any content slot has been populated on this builder.
     *
     * <p>Lets callers conditionally add children or content depending on
     * whether the stanza is meant to remain empty.
     *
     * @return {@code true} when text, JID, binary, or child content has been
     *         set
     */
    public boolean hasContent() {
        return textContent != null
               || jidContent != null
               || bytesContent != null
               || childrenContent != null;
    }

    /**
     * Returns the constructed immutable {@link Node}.
     *
     * <p>The concrete variant is selected from the first populated content
     * slot in the order text, JID, bytes, children, falling back to
     * {@link Node.EmptyNode} when no slot is set. If no description was
     * supplied the node carries an empty tag name. The builder is not reset;
     * callers must construct a fresh {@code NodeBuilder} for the next stanza.
     *
     * <ul>
     *   <li>{@link Node.TextNode} when text content is set
     *   <li>{@link Node.JidNode} when JID content is set
     *   <li>{@link Node.BytesNode} when binary content is set
     *   <li>{@link Node.ContainerNode} when child nodes are set
     *   <li>{@link Node.EmptyNode} when no content slot is populated
     * </ul>
     *
     * @return the freshly built node
     * @see Node
     */
    @WhatsAppWebExport(moduleName = "WAWap", exports = "makeWapNode",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWap", exports = "wap",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public Node build() {
        var description = Objects.requireNonNullElse(this.description, "");
        if(textContent != null) {
            return new Node.TextNode(description, attributes, textContent);
        }else if(jidContent != null){
            return new Node.JidNode(description, attributes, jidContent.toJid());
        }else if(bytesContent != null){
            return new Node.BytesNode(description, attributes, bytesContent);
        }else if(childrenContent != null){
            return new Node.ContainerNode(description, attributes, childrenContent);
        }else {
            return new Node.EmptyNode(description, attributes);
        }
    }
}
