package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.stanza.Stanza;
import com.github.auties00.cobalt.stanza.StanzaBuilder;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a single {@code <capability>} advertisement carried inside an offer, accept, or
 * preaccept.
 *
 * <p>A capability child advertises one version of a device's feature bitmask: a {@code ver}
 * attribute selecting the bitmask version and a binary blob holding the mask itself, least
 * significant bit first within each byte. The wa-voip engine packs every supported version of the
 * mask into one or more of these children; this record models exactly one such version-tagged mask
 * as it appears on the wire, leaving the typed interpretation of the bit indices and the
 * multi-version assembly to the capability subsystem.
 *
 * <p>On the wire the element is {@code <capability ver="N">MASK_BYTES</capability>} where {@code N}
 * ranges over {@code 1..0x40} and the mask is between one and sixty-four bytes. A captured
 * one-to-one offer advertises the seven-byte mask {@code 01 05 F7 09 E4 BB 13} under version
 * {@code 1}.
 *
 * @implNote This implementation models the single {@code <capability>} element parsed by
 * {@code fill_capability} (fn11598) and built by {@code add_capability_child} (fn11593) in the
 * wa-voip WASM module {@code ff-tScznZ8P} ({@code shared_elements/common.cc}): the {@code ver}
 * attribute (data offset {@code 0x481b3}) bounded to {@code 1..0x40} and up to {@code 0x40} mask
 * bytes as the element content. The multi-version serialization
 * ({@code wa_serialize_voip_capabilities}) that emits one length-prefixed mask per version, and the
 * meaning of each bit index, are owned by the capability subsystem and are not modeled here; this
 * record is the per-element wire shape only.
 *
 * @param version the bitmask version this advertisement carries, in the range {@code 1..0x40}
 * @param mask    the capability bitmask bytes, least significant bit first within each byte; never
 *                {@code null} and never empty
 * @see Calls2SignalingType
 */
public record CallCapability(int version, byte[] mask) {
    /**
     * The wire attribute naming the bitmask version on a {@code <capability>} element.
     */
    private static final String VERSION_ATTRIBUTE = "ver";

    /**
     * The wire element tag for a capability advertisement.
     */
    public static final String ELEMENT = "capability";

    /**
     * Canonicalizes the record components, defensively copying the mask and rejecting an empty mask.
     *
     * @throws NullPointerException     if {@code mask} is {@code null}
     * @throws IllegalArgumentException if {@code mask} is empty
     */
    public CallCapability {
        Objects.requireNonNull(mask, "mask cannot be null");
        if (mask.length == 0) {
            throw new IllegalArgumentException("capability mask cannot be empty");
        }
        mask = mask.clone();
    }

    /**
     * Returns the capability bitmask bytes backing this advertisement.
     *
     * <p>This accessor overrides the implicit record accessor to return a defensive copy so the
     * stored array cannot be mutated through the returned reference.
     *
     * @return a copy of the mask bytes
     */
    @Override
    public byte[] mask() {
        return mask.clone();
    }

    /**
     * Builds the {@code <capability ver="N">MASK</capability>} stanza for this advertisement.
     *
     * @return the capability stanza
     */
    public Stanza toStanza() {
        return new StanzaBuilder()
                .description(ELEMENT)
                .attribute(VERSION_ATTRIBUTE, version)
                .content(mask)
                .build();
    }

    /**
     * Decodes a {@code <capability>} stanza into a {@link CallCapability}.
     *
     * <p>The {@code ver} attribute supplies the version and the element content supplies the mask. A
     * stanza with no content, or a stanza that is not a {@code <capability>} element, yields an empty
     * result rather than throwing so callers iterating a mixed child list can skip it.
     *
     * @param stanza the {@code <capability>} stanza
     * @return the decoded capability, or an empty result when the stanza is not a usable capability
     *         element
     * @throws NullPointerException if {@code stanza} is {@code null}
     */
    public static Optional<CallCapability> of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        if (!stanza.hasDescription(ELEMENT)) {
            return Optional.empty();
        }
        var mask = stanza.toContentBytes();
        if (mask.isEmpty()) {
            return Optional.empty();
        }
        var version = stanza.getAttributeAsInt(VERSION_ATTRIBUTE, 1);
        return Optional.of(new CallCapability(version, mask.get()));
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || (obj instanceof CallCapability that
                && this.version == that.version
                && Arrays.equals(this.mask, that.mask));
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, Arrays.hashCode(mask));
    }

    @Override
    public String toString() {
        return "CallCapability[version=" + version + ", maskLen=" + mask.length + ']';
    }
}
