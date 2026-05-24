package com.github.auties00.cobalt.registration.push.apns.courier;

import com.github.auties00.cobalt.registration.push.apns.plist.Plist;
import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistDataValue;
import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistDictionaryValue;
import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistIntegerValue;
import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistStringValue;

import java.io.IOException;

/**
 * The decoded bag response published at
 * {@code http://init-p01st.push.apple.com/bag}.
 *
 * @apiNote
 * Consumed by the courier connection bootstrap to pick which courier
 * replica to dial. The hostname is the DNS suffix
 * (e.g. {@code "courier.push.apple.com"}) and the replica count is the
 * number of live front-ends Apple is currently rotating between; the
 * caller dials {@code <index>-<hostname>:443} for some random
 * {@code index} in {@code [1, hostCount)}.
 *
 * @implNote
 * This implementation models the bag as a plain {@link Record} with
 * only the two fields the courier handshake actually needs; the
 * remaining metadata in the bag plist (carrier-specific overrides,
 * retry hints, region info) is parsed but discarded.
 *
 * @param hostCount the number of courier replicas advertised by Apple
 * @param hostname  the DNS suffix shared by every replica
 */
public record ApnsBag(int hostCount, String hostname) {
    /**
     * Decodes the bag response into an {@link ApnsBag}.
     *
     * @apiNote
     * Called once per courier connection by
     * {@link com.github.auties00.cobalt.registration.push.apns.ApnsCourierConnection}
     * after fetching {@code /bag}. The response is a plist-in-plist:
     * the outer {@link PlistDictionaryValue} has a {@code "bag"} key
     * whose {@link PlistDataValue} payload is itself a plist carrying
     * {@code APNSCourierHostcount} and {@code APNSCourierHostname}.
     *
     * @implNote
     * This implementation funnels every parsing failure (including
     * {@link NullPointerException} from missing keys and
     * {@link ClassCastException} from unexpected types) through a
     * single {@link IOException} so callers can handle bag-parse
     * failures as ordinary transport failures.
     *
     * @param plist the raw plist bytes returned by the bag endpoint
     * @return the decoded bag
     * @throws IOException if the plist is malformed or missing either
     *                     of the two required keys
     */
    public static ApnsBag ofPlist(byte[] plist) throws IOException {
        try {
            var outer = (PlistDictionaryValue) Plist.parse(plist);
            var bagData = (PlistDataValue) outer.get("bag").orElseThrow();
            var bag = (PlistDictionaryValue) Plist.parse(bagData.toByteArray());
            var hostCount = (int) ((PlistIntegerValue) bag.get("APNSCourierHostcount").orElseThrow()).value();
            var hostname = ((PlistStringValue) bag.get("APNSCourierHostname").orElseThrow()).value();
            return new ApnsBag(hostCount, hostname);
        } catch (Exception e) {
            throw new IOException("Cannot parse APNS bag", e);
        }
    }
}
