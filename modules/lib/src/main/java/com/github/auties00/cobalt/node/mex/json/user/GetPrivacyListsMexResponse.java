package com.github.auties00.cobalt.node.mex.json.user;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.mex.MexOperation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedCollection;

/**
 * Parsed response for the privacy contact list fetch query.
 *
 * <p>The compiled GraphQL artifact projects
 * {@code xwa2_fetch_wa_users: [{ privacy_contact_list: { dhash, contacts: [{ jid, pn_jid, username_info: { username } }] } }]}.
 * Since the request always carries a single {@code query_input} entry, Cobalt collapses the outer array to its first
 * element and exposes the nested {@code privacy_contact_list} fields directly.
 */
@WhatsAppWebModule(moduleName = "WAWebMexGetPrivacyList")
public final class GetPrivacyListsMexResponse implements MexOperation.Response.Json {
    /**
     * The server-side digest of the privacy contact list, used to drive delta refreshes.
     */
    private final String dhash;

    /**
     * The contacts returned by the relay for this list.
     */
    private final List<PrivacyContact> contacts;

    /**
     * Constructs a new response with the given fields.
     *
     * @param dhash the server-side digest of the list
     * @param contacts the contacts returned by the relay
     */
    private GetPrivacyListsMexResponse(String dhash, List<PrivacyContact> contacts) {
        this.dhash = dhash;
        this.contacts = contacts == null ? List.of() : contacts;
    }

    /**
     * Parses the MEX response carried by an inbound IQ stanza.
     *
     * @param node the IQ response node received from the relay
     * @return an {@link Optional} containing the parsed response, or empty if the node is missing a result payload or
     *         the payload does not carry a {@code privacy_contact_list} projection
     */
    @WhatsAppWebExport(moduleName = "WAWebMexGetPrivacyList", exports = "fetchPrivacyList",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<GetPrivacyListsMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(GetPrivacyListsMexResponse::parse);
    }

    /**
     * Parses the response from the raw JSON payload bytes.
     *
     * @param payload the raw JSON bytes from the {@code <result>} child
     * @return an {@link Optional} containing the parsed response, or empty if the envelope is missing
     */
    private static Optional<GetPrivacyListsMexResponse> parse(byte[] payload) {
        JSONObject envelope;
        try {
            envelope = JSON.parseObject(payload);
        } catch (RuntimeException _) {
            return Optional.empty();
        }
        if (envelope == null) {
            return Optional.empty();
        }
        JSONArray users = envelope.getJSONArray("xwa2_fetch_wa_users");
        if (users == null || users.isEmpty()) {
            return Optional.empty();
        }
        JSONObject user = users.getJSONObject(0);
        if (user == null) {
            return Optional.empty();
        }
        JSONObject list = user.getJSONObject("privacy_contact_list");
        if (list == null) {
            return Optional.empty();
        }
        String dhash = list.getString("dhash");
        JSONArray rawContacts = list.getJSONArray("contacts");
        List<PrivacyContact> contacts;
        if (rawContacts == null || rawContacts.isEmpty()) {
            contacts = List.of();
        } else {
            contacts = new ArrayList<>(rawContacts.size());
            for (var i = 0; i < rawContacts.size(); i++) {
                var entry = rawContacts.getJSONObject(i);
                if (entry == null) {
                    continue;
                }
                contacts.add(PrivacyContact.parse(entry));
            }
        }
        return Optional.of(new GetPrivacyListsMexResponse(dhash, contacts));
    }

    /**
     * Returns the {@code dhash} digest of the server-side privacy contact list.
     *
     * @return an {@link Optional} containing the digest, or empty when the server omitted the field
     */
    public Optional<String> dhash() {
        return Optional.ofNullable(dhash);
    }

    /**
     * Returns the privacy-list contacts the server returned.
     *
     * @return an unmodifiable view of the contact entries, never {@code null}, possibly empty
     */
    public SequencedCollection<PrivacyContact> contacts() {
        return Collections.unmodifiableSequencedCollection(contacts);
    }

    /**
     * Single contact entry inside a privacy contact list response. Mirrors the {@code XWA2ContactEntry} GraphQL type
     * {@code { jid, pn_jid, username_info: { username } }}. The {@code pn_jid} alternate form is only present when the
     * contact has a phone-number-addressed JID distinct from the primary one (typically a LID-addressed account). The
     * {@code username} sub-field is only present when the contact has claimed a WhatsApp username.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexGetPrivacyList")
    public static final class PrivacyContact {
        /**
         * The primary contact JID.
         */
        private final Jid jid;

        /**
         * The phone-number-addressed alternate JID, or {@code null} when the contact only has the primary form.
         */
        private final Jid pnJid;

        /**
         * The WhatsApp username, or {@code null} when the contact has not claimed one.
         */
        private final String username;

        /**
         * Constructs a new privacy-list contact entry.
         *
         * @param jid the primary contact JID, never {@code null}
         * @param pnJid the phone-number-addressed alternate JID, or {@code null}
         * @param username the WhatsApp username, or {@code null}
         * @throws NullPointerException if {@code jid} is {@code null}
         */
        public PrivacyContact(Jid jid, Jid pnJid, String username) {
            this.jid = Objects.requireNonNull(jid, "jid cannot be null");
            this.pnJid = pnJid;
            this.username = username;
        }

        /**
         * Parses a contact entry from the given JSON object.
         *
         * @param entry the JSON object to parse
         * @return the parsed contact entry
         */
        private static PrivacyContact parse(JSONObject entry) {
            var rawJid = entry.getString("jid");
            var rawPnJid = entry.getString("pn_jid");
            var usernameInfo = entry.getJSONObject("username_info");
            String username = usernameInfo == null ? null : usernameInfo.getString("username");
            return new PrivacyContact(
                    Jid.of(rawJid),
                    rawPnJid == null ? null : Jid.of(rawPnJid),
                    username);
        }

        /**
         * Returns the primary contact JID.
         *
         * @return the JID, never {@code null}
         */
        public Jid jid() {
            return jid;
        }

        /**
         * Returns the phone-number-addressed alternate JID.
         *
         * @return an {@link Optional} carrying the alternate JID, or empty when the contact only has the primary form
         */
        public Optional<Jid> pnJid() {
            return Optional.ofNullable(pnJid);
        }

        /**
         * Returns the WhatsApp username the contact has claimed.
         *
         * @return an {@link Optional} carrying the username, or empty when the contact has not claimed one
         */
        public Optional<String> username() {
            return Optional.ofNullable(username);
        }
    }
}
