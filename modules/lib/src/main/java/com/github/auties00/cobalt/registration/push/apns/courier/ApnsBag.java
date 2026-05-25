package com.github.auties00.cobalt.registration.push.apns.courier;

import com.github.auties00.cobalt.registration.push.apns.plist.Plist;
import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistDataValue;
import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistDictionaryValue;
import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistIntegerValue;
import com.github.auties00.cobalt.registration.push.apns.plist.value.PlistStringValue;

import java.io.IOException;

/**
 * Holds the two courier-routing values decoded from the APNS bag response.
 *
 * <p>The bag is the document Apple publishes at
 * {@code http://init-p01st.push.apple.com/bag} and that the courier connection
 * bootstrap fetches to pick which courier replica to dial. The {@link #hostname()}
 * is the DNS suffix shared by every replica (for example
 * {@code "courier.push.apple.com"}) and {@link #hostCount()} is the number of live
 * front-ends Apple is currently rotating between, so a caller dials
 * {@code <index>-<hostname>:443} for some random {@code index} in
 * {@code [1, hostCount)}.
 *
 * @param hostCount the number of courier replicas advertised by Apple
 * @param hostname  the DNS suffix shared by every replica
 * @implNote This implementation models the bag as a plain {@link Record} carrying
 * only the two fields the courier handshake actually needs; the remaining metadata
 * in the bag plist (carrier-specific overrides, retry hints, region info) is parsed
 * but discarded.
 */
public record ApnsBag(int hostCount, String hostname) {
    /**
     * Decodes the raw bag plist bytes into an {@link ApnsBag}.
     *
     * <p>The response is a plist nested inside a plist: the outer
     * {@link PlistDictionaryValue} has a {@code "bag"} key whose
     * {@link PlistDataValue} payload is itself a plist carrying the
     * {@code APNSCourierHostcount} and {@code APNSCourierHostname} entries that map
     * to {@link #hostCount()} and {@link #hostname()}. The integer host count is
     * read as a {@code long} via {@link PlistIntegerValue#value()} and narrowed to
     * an {@code int}. This is called once per courier connection after fetching
     * {@code /bag}.
     *
     * @param plist the raw plist bytes returned by the bag endpoint
     * @return the decoded bag
     * @throws IOException if the plist is malformed or missing either of the two
     *                     required keys
     * @implNote This implementation funnels every parsing failure (including a
     * {@link NullPointerException} from a missing key and a {@link ClassCastException}
     * from an unexpected value type) through a single {@link IOException} so callers
     * can treat bag-parse failures as ordinary transport failures.
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
