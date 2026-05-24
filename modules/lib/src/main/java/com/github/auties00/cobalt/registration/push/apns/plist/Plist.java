package com.github.auties00.cobalt.registration.push.apns.plist;

import com.github.auties00.cobalt.registration.push.apns.plist.binary.PlistBinaryParser;
import com.github.auties00.cobalt.registration.push.apns.plist.binary.PlistBinaryWriter;
import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistValue;
import com.github.auties00.cobalt.registration.push.apns.plist.xml.PlistXmlParser;
import com.github.auties00.cobalt.registration.push.apns.plist.xml.PlistXmlWriter;

import java.io.IOException;

/**
 * The static facade over the format-specific Apple
 * {@code Foundation/PropertyList} parsers and writers used by the
 * APNS code.
 *
 * @apiNote
 * Routes parse calls to {@link PlistBinaryParser} or
 * {@link PlistXmlParser} by inspecting the magic bytes of the input,
 * and routes write calls to the matching format-specific writer.
 * Callers should depend on this class rather than on the
 * implementation classes so future format additions (e.g.
 * {@code bplist15} or OpenStep) require only one edit.
 *
 * @implNote
 * This implementation is a non-instantiable namespace; the package
 * exposes only this entry point and the
 * {@link com.github.auties00.cobalt.registration.push.apns.plist.value}
 * type hierarchy.
 */
public final class Plist {
    /**
     * Hidden constructor.
     *
     * @apiNote
     * Prevents instantiation; the class is a stateless namespace.
     */
    private Plist() {
    }

    /**
     * Parses a plist by auto-detecting between the binary and XML
     * formats.
     *
     * @apiNote
     * Called by {@code ApnsBag.ofPlist} and
     * {@code ApnsActivationInfo.ofPlist} on bytes whose format Apple
     * may flip at any time (the activation response is XML today but
     * the bag is technically a binary plist on some carriers); the
     * auto-detection means callers do not need to track which.
     *
     * @implNote
     * This implementation delegates the magic-byte check to
     * {@link PlistBinaryParser#isBinary(byte[])}; anything that does
     * not start with {@code bplist00} is fed to
     * {@link PlistXmlParser#parse(byte[])}.
     *
     * @param data the source bytes
     * @return the root value
     * @throws IOException if the source is malformed for the
     *                     detected format
     */
    public static PlistValue parse(byte[] data) throws IOException {
        if (PlistBinaryParser.isBinary(data)) {
            return PlistBinaryParser.parse(data);
        }
        return PlistXmlParser.parse(data);
    }

    /**
     * Serialises a value tree as an XML plist with the canonical
     * Apple preamble.
     *
     * @apiNote
     * Used by the activation flow to emit the inner and outer
     * activation plists; Apple's activation endpoint requires the
     * XML form.
     *
     * @implNote
     * This implementation delegates to {@link PlistXmlWriter#write}.
     *
     * @param root the root value
     * @return the UTF-8 encoded XML plist bytes
     */
    public static byte[] writeXml(PlistValue root) {
        return PlistXmlWriter.write(root);
    }

    /**
     * Serialises a value tree as a {@code bplist00} binary plist.
     *
     * @apiNote
     * Provided for symmetry with {@link #writeXml(PlistValue)}; not
     * consumed by the current APNS code path but kept available for
     * future callers that need to emit binary plists.
     *
     * @implNote
     * This implementation delegates to {@link PlistBinaryWriter#write}.
     *
     * @param root the root value
     * @return the binary plist bytes
     */
    public static byte[] writeBinary(PlistValue root) {
        return PlistBinaryWriter.write(root);
    }
}
