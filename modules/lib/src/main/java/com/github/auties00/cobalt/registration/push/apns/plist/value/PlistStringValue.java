package com.github.auties00.cobalt.registration.push.apns.plist.value;

import java.util.Objects;

/**
 * Plist {@code <string>} leaf, the binary plist {@code 0x50..0x5F}
 * (ASCII) and {@code 0x60..0x6F} (UTF-16BE) marker families.
 *
 * @apiNote
 * Used wherever an APNS plist payload carries text; both encodings
 * decode into a Java {@link String} so consumers see a uniform value
 * regardless of the on-wire form.
 *
 * @implNote
 * This implementation stores the already-decoded {@link String}
 * rather than the raw bytes plus an encoding marker, since the parser
 * is the only call site that sees the original encoding and downstream
 * consumers always want the textual value.
 *
 * @param value the decoded string
 */
public record PlistStringValue(String value) implements PlistValue {
    /**
     * Canonical constructor that rejects a {@code null} string.
     *
     * @param value the string
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public PlistStringValue {
        Objects.requireNonNull(value, "value");
    }
}
