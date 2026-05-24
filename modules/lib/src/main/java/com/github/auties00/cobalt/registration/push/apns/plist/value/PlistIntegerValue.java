package com.github.auties00.cobalt.registration.push.apns.plist.value;

/**
 * Plist {@code <integer>} leaf, the binary plist {@code 0x10..0x13}
 * marker family covering 1, 2, 4, and 8-byte integer widths.
 *
 * @apiNote
 * Used wherever an APNS plist payload carries a whole number (status
 * codes, lengths, identifiers); the parser narrows every supported
 * width into a single Java {@code long} so consumers do not have to
 * branch on the on-wire size.
 *
 * @implNote
 * This implementation only models widths up to 8 bytes; binary plists
 * also define a {@code 0x14} 16-byte integer marker, which the parser
 * surfaces as an {@link java.io.IOException} rather than truncating
 * silently.
 *
 * @param value the integer value
 */
public record PlistIntegerValue(long value) implements PlistValue {
}
