package com.github.auties00.cobalt.registration.push.apns.courier;

import java.util.Map;

/**
 * A decoded courier frame: the {@link ApnsPayloadTag} that classifies
 * the packet plus the type-length-value fields parsed from its body.
 *
 * @apiNote
 * Produced by the courier read pump and consumed by the response
 * matchers registered with {@code exchange}. Field ids are one byte
 * wide and their semantics are scoped to the enclosing
 * {@link ApnsPayloadTag}, so the same numeric id can carry different
 * data in different packets; see the {@code FIELD_*} constants in
 * {@code ApnsCourierConnection} for the per-tag mapping Cobalt
 * recognises.
 *
 * @implNote
 * This implementation models the body as a plain {@code Map<Integer,
 * byte[]>} rather than a typed object; the courier protocol's TLV
 * layout admits arbitrary field ids and types so a typed model would
 * either reject unknown fields or duplicate the parser dispatch in
 * the model.
 *
 * @param tag    the wire tag of this packet, or {@code null} when the
 *               read pump observed a tag byte outside the
 *               {@link ApnsPayloadTag} range
 * @param fields the {@code field-id -> raw bytes} map, never
 *               {@code null}, in insertion order
 */
public record ApnsPacket(ApnsPayloadTag tag, Map<Integer, byte[]> fields) {
}
