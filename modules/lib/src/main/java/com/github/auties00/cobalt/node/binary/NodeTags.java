package com.github.auties00.cobalt.node.binary;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.node.Node;

/**
 * Defines the leading byte tags used by WhatsApp's compact binary stanza
 * protocol.
 *
 * <p>The wire format places a single tag byte before every value to identify
 * its shape: an empty list, a dictionary token, a sized list, a JID variant,
 * a hex or nibble packed string, or a binary blob with an 8, 20, or 32 bit
 * length prefix. {@link NodeWriter} and {@link NodeReader} consult these
 * constants while translating between {@link Node} trees and their serialised
 * form.
 *
 * @see Node
 * @see NodeWriter
 * @see NodeReader
 */
@WhatsAppWebModule(moduleName = "WAWap")
public final class NodeTags {
    /**
     * Prevents instantiation of this constants holder.
     *
     * @throws UnsupportedOperationException always
     */
    private NodeTags() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Marks an empty list or an absent value.
     */
    public static final byte LIST_EMPTY = 0;

    /**
     * Selects {@link NodeTokens#DICTIONARY_0_TOKENS} for the next index byte.
     */
    public static final byte DICTIONARY_0 = (byte) 236;

    /**
     * Selects {@link NodeTokens#DICTIONARY_1_TOKENS} for the next index byte.
     */
    public static final byte DICTIONARY_1 = (byte) 237;

    /**
     * Selects {@link NodeTokens#DICTIONARY_2_TOKENS} for the next index byte.
     */
    public static final byte DICTIONARY_2 = (byte) 238;

    /**
     * Selects {@link NodeTokens#DICTIONARY_3_TOKENS} for the next index byte.
     */
    public static final byte DICTIONARY_3 = (byte) 239;

    /**
     * Domain code for the standard WhatsApp user server ({@code s.whatsapp.net}).
     */
    public static final int DOMAIN_WHATSAPP = 0;

    /**
     * Domain code for the linked identity server ({@code lid}).
     */
    public static final int DOMAIN_LID = 1;

    /**
     * Domain code for the business hosted server ({@code hosted}).
     */
    public static final int DOMAIN_HOSTED = 128;

    /**
     * Domain code for the business hosted linked identity server ({@code hosted.lid}).
     */
    public static final int DOMAIN_HOSTED_LID = 129;

    /**
     * Marks a cross platform interoperability JID.
     */
    public static final byte JID_INTEROP = (byte) 245;

    /**
     * Marks a Facebook Messenger JID.
     */
    public static final byte JID_FB = (byte) 246;

    /**
     * Marks a multi device JID with explicit domain code.
     */
    public static final byte AD_JID = (byte) 247;

    /**
     * Marks a list whose length fits in 8 bits.
     */
    public static final byte LIST_8 = (byte) 248;

    /**
     * Marks a list whose length fits in 16 bits.
     */
    public static final byte LIST_16 = (byte) 249;

    /**
     * Marks a JID built from a user component and a server component.
     */
    public static final byte JID_PAIR = (byte) 250;

    /**
     * Marks a hex packed string with an 8 bit byte count.
     */
    public static final byte HEX_8 = (byte) 251;

    /**
     * Marks a binary blob whose length fits in 8 bits.
     */
    public static final byte BINARY_8 = (byte) 252;

    /**
     * Marks a binary blob whose length fits in 20 bits.
     */
    public static final byte BINARY_20 = (byte) 253;

    /**
     * Marks a binary blob whose length fits in 32 bits.
     */
    public static final byte BINARY_32 = (byte) 254;

    /**
     * Marks a nibble packed string with an 8 bit byte count.
     */
    public static final byte NIBBLE_8 = (byte) 255;
}
