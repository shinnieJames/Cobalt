package com.github.auties00.cobalt.registration.push.apns.activation;

import com.github.auties00.cobalt.registration.push.apns.plist.Plist;
import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistDataValue;
import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistDictionaryValue;

import java.io.IOException;

/**
 * The decoded {@code <Protocol>} plist returned from
 * {@code albert.apple.com/.../deviceActivation}.
 *
 * @apiNote
 * Produced by {@link #ofPlist(byte[])} once the activation HTTP call
 * succeeds. Cobalt consumes only the {@code DeviceCertificate} (the
 * X.509 device cert Apple's iPhone Device CA signs, valid for roughly
 * three years); the rest of the plist
 * ({@code ack-received}, {@code show-settings}, capability bits) is
 * parsed but discarded.
 *
 * @implNote
 * This implementation models the result as a plain {@link Record}
 * with the single field the courier connection later needs; the
 * caller persists the bytes via
 * {@link com.github.auties00.cobalt.registration.push.apns.ApnsSession}.
 *
 * @param deviceCertificate the DER bytes of the Apple-signed device
 *                          certificate
 */
public record ApnsActivationInfo(byte[] deviceCertificate) {
    /**
     * Parses the {@code <Protocol>} plist into the structured record.
     *
     * @apiNote
     * Called once per fresh activation by
     * {@link com.github.auties00.cobalt.registration.push.apns.ApnsActivation}
     * after the regex extracts the inner plist from the activation
     * response. The required key path is
     * {@code device-activation -> activation-record -> DeviceCertificate}.
     *
     * @implNote
     * This implementation funnels every parsing failure (including
     * {@link NullPointerException} from missing keys and
     * {@link ClassCastException} from unexpected types) through a
     * single {@link IOException} so activation-side parse failures
     * surface as ordinary transport failures.
     *
     * @param plist the UTF-8 plist bytes lifted from the activation
     *              response
     * @return the decoded activation info
     * @throws IOException if the plist is malformed or missing the
     *                     expected nested keys
     */
    public static ApnsActivationInfo ofPlist(byte[] plist) throws IOException {
        try {
            var root = (PlistDictionaryValue) Plist.parse(plist);
            var deviceActivation = (PlistDictionaryValue) root.get("device-activation").orElseThrow();
            var activationRecord = (PlistDictionaryValue) deviceActivation.get("activation-record").orElseThrow();
            var deviceCertificate = (PlistDataValue) activationRecord.get("DeviceCertificate").orElseThrow();
            return new ApnsActivationInfo(deviceCertificate.toByteArray());
        } catch (Exception e) {
            throw new IOException("Cannot parse device activation info", e);
        }
    }
}
