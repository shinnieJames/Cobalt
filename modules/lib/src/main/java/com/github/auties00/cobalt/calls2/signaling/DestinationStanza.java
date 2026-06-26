package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.StanzaBuilder;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents the {@code <destination>} fanout-addressing block of a group call signaling message.
 *
 * <p>A destination block names the set of device JIDs a fanout signaling message is addressed to. The
 * engine attaches one to a group message that must reach several specific devices rather than the
 * single peer of a one-to-one exchange: each addressed device is a {@code <to jid="<deviceJid>"/>}
 * child, and the receiver delivers the enclosing message to exactly those devices. This record models
 * the block as the ordered list of addressed device JIDs.
 *
 * <p>On the wire the element is {@code <destination> <to jid="..."/>* </destination>}. The {@code <to>}
 * children carry the addressed device JID in a {@code jid} attribute, not as element content; a
 * destination block addressing no devices is an empty {@code <destination/>} element.
 *
 * <p>This block addresses devices but carries no per-device key material; the offer's per-device
 * call-key fanout, where each {@code <to>} additionally wraps an {@code <enc>} ciphertext child, is
 * modeled by {@link CallKeyDistribution}. A bare destination block is the addressing-only form used by
 * the group rekey and extension fanout paths.
 *
 * @implNote This implementation models the {@code <destination>} element built by
 * {@code add_destination_if_needed} (fn11610) in the wa-voip WASM module {@code ff-tScznZ8P}: the
 * {@code destination} element (data offset {@code 0x5791e}, double-byte dictionary token page 0 index
 * 169) wrapping {@code <to>} children (data offset {@code 0x52a10}, single-byte dictionary token index
 * 17), each carrying the addressed device JID in a {@code jid} attribute (single-byte dictionary token
 * index 12). This record carries only the addressing; the per-device {@code <enc>} key child the offer
 * fanout adds to each {@code <to>} is modeled separately by {@link CallKeyDistribution}.
 *
 * @param devices the addressed device JIDs in fanout order; never {@code null}, possibly empty
 * @see CallKeyDistribution
 * @see GroupUpdateStanza
 */
public record DestinationStanza(List<Jid> devices) implements CallMessage {
    /**
     * The wire element tag for a destination fanout block.
     */
    static final String ELEMENT = "destination";

    /**
     * The wire element tag for one addressed-device entry inside a destination block.
     */
    private static final String TO_ELEMENT = "to";

    /**
     * The wire attribute naming the addressed device JID on a {@code <to>} element.
     */
    private static final String JID_ATTRIBUTE = "jid";

    /**
     * Canonicalizes the record components, defensively copying the device list.
     *
     * @throws NullPointerException if {@code devices} is {@code null} or any element is {@code null}
     */
    public DestinationStanza {
        Objects.requireNonNull(devices, "devices cannot be null");
        devices = List.copyOf(devices);
    }

    /**
     * Returns a destination block addressing the given device JIDs.
     *
     * @param devices the addressed device JIDs in fanout order
     * @return the destination block
     * @throws NullPointerException if {@code devices} is {@code null} or any element is {@code null}
     */
    public static DestinationStanza of(List<Jid> devices) {
        return new DestinationStanza(devices);
    }

    /**
     * {@inheritDoc}
     *
     * <p>A destination block has no entry in the numeric {@code voip_signaling_message_type} table: it
     * is a structural addressing sub-element attached to another action, not a standalone signaling
     * action, so this projection has no {@link Calls2SignalingType} and the method returns
     * {@code null}.
     *
     * @return {@code null}, since a destination block carries no taxonomy ordinal
     */
    @Override
    public Calls2SignalingType type() {
        return null;
    }

    /**
     * Builds the {@code <destination> <to jid="..."/>* </destination>} block stanza.
     *
     * <p>Each addressed device becomes a keyless {@code <to jid="..."/>} child; a block addressing no
     * devices produces an empty {@code <destination/>} element.
     *
     * @return the destination block stanza
     */
    @Override
    public Stanza toStanza() {
        var builder = new StanzaBuilder()
                .description(ELEMENT);
        if (!devices.isEmpty()) {
            var children = devices.stream()
                    .map(device -> new StanzaBuilder()
                            .description(TO_ELEMENT)
                            .attribute(JID_ATTRIBUTE, device)
                            .build())
                    .toList();
            builder.content(children);
        }
        return builder.build();
    }

    /**
     * Decodes a {@code <destination>} stanza into a {@link DestinationStanza}.
     *
     * <p>Each {@code <to>} child contributes its {@code jid} attribute to the addressed-device list; a
     * {@code <to>} child without a parseable {@code jid} attribute is skipped. A stanza that is not a
     * {@code <destination>} element yields an empty result so callers iterating a mixed child list can
     * skip it.
     *
     * @param stanza the {@code <destination>} stanza
     * @return the decoded destination block, or an empty result when the stanza is not a
     *         {@code <destination>} element
     * @throws NullPointerException if {@code stanza} is {@code null}
     */
    public static Optional<DestinationStanza> of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        if (!stanza.hasDescription(ELEMENT)) {
            return Optional.empty();
        }
        var devices = stanza.streamChildren(TO_ELEMENT)
                .flatMap(child -> child.streamAttributeAsJid(JID_ATTRIBUTE))
                .toList();
        return Optional.of(new DestinationStanza(devices));
    }
}
