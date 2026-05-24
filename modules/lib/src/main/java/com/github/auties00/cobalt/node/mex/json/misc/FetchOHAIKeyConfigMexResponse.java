package com.github.auties00.cobalt.node.mex.json.misc;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.mex.MexOperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Inbound parsed response of the {@link FetchOHAIKeyConfigMexRequest}
 * query, exposing the OHAI key configuration list carried by the
 * {@code data.xwa2_ohai_configurations.ohai_configs} envelope.
 *
 * @apiNote Drives WA Web's
 * {@code WAWebOHAIKeyConfigProvider.provideOHAIKeyConfig} cache: each
 * {@link OhaiConfig} entry pins an AEAD cipher, an HPKE KDF and KEM, a
 * server-assigned key id, an expiration epoch second and the public-key
 * bytes the client must use to encapsulate an ACS request through OHAI.
 *
 * @implNote This implementation preserves the raw list returned by the
 * relay instead of reducing it to the entry with the earliest
 * {@code expiration_date} (the WA Web call-site does the reduce inline
 * before caching). Cobalt is a library and leaves the selection policy
 * to the caller, who may pick the latest, earliest or any other entry.
 * The field projection inside each entry mirrors WA Web's
 * {@code XWA2OHAIConfig} fragment exactly.
 */
@WhatsAppWebModule(moduleName = "WAWebFetchOHAIKeyConfigJob")
public final class FetchOHAIKeyConfigMexResponse implements MexOperation.Response.Json {
    /**
     * The unfiltered list of OHAI key configurations projected from
     * {@code data.xwa2_ohai_configurations.ohai_configs}.
     */
    private final List<OhaiConfig> ohaiConfigs;

    /**
     * Constructs a new response wrapping the parsed list of OHAI key
     * configurations.
     *
     * @apiNote Private; instances are produced by the {@link #of(Node)}
     * parser.
     *
     * @param ohaiConfigs the unfiltered list of configurations
     */
    private FetchOHAIKeyConfigMexResponse(List<OhaiConfig> ohaiConfigs) {
        this.ohaiConfigs = ohaiConfigs;
    }

    /**
     * Parses the MEX response carried by an inbound IQ stanza.
     *
     * @apiNote Reads the {@code <result>} child's byte content and routes
     * it through the private byte-level parser. Returns
     * {@link Optional#empty()} when the stanza carries no result or when
     * the {@code data.xwa2_ohai_configurations} envelope is absent.
     *
     * @param node the inbound IQ stanza carrying the {@code <result>} child
     * @return an {@link Optional} wrapping the parsed response, or
     *         {@link Optional#empty()} if the expected JSON shape is absent
     */
    @WhatsAppWebExport(moduleName = "WAWebFetchOHAIKeyConfigJob", exports = "mexFetchOHAIKeyConfig",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<FetchOHAIKeyConfigMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(FetchOHAIKeyConfigMexResponse::of);
    }

    /**
     * Returns the unfiltered list of OHAI key configurations advertised by
     * the relay.
     *
     * @apiNote Embedders typically iterate the list and pick the entry
     * whose {@link OhaiConfig#expirationDate()} is the latest still in
     * the future, mirroring {@code WAWebOHAIKeyConfigProvider}'s freshness
     * gate (one-hour grace window).
     *
     * @return the parsed list of OHAI key configurations, never {@code null};
     *         empty when the {@code ohai_configs} array was missing or
     *         empty on the wire
     */
    public List<OhaiConfig> ohaiConfigs() {
        return ohaiConfigs;
    }

    /**
     * Parses the JSON payload carried by the {@code <result>} child into a
     * {@link FetchOHAIKeyConfigMexResponse}.
     *
     * @apiNote Private; routed through {@link #of(Node)} after the byte
     * content of the {@code <result>} child is extracted. Returns
     * {@link Optional#empty()} when the envelope, the {@code data} branch,
     * or the {@code xwa2_ohai_configurations} child is absent.
     *
     * @param json the UTF-8 encoded JSON payload
     * @return an {@link Optional} wrapping the parsed response, or
     *         {@link Optional#empty()} if the envelope is missing
     */
    private static Optional<FetchOHAIKeyConfigMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("xwa2_ohai_configurations");
        if (root == null) {
            return Optional.empty();
        }

        var configs = OhaiConfig.ofArray(root.getJSONArray("ohai_configs"));
        return Optional.of(new FetchOHAIKeyConfigMexResponse(configs));
    }

    /**
     * Parsed projection of one OHAI key configuration entry returned by
     * the relay.
     *
     * @apiNote Mirrors the {@code XWA2OHAIConfig} fragment of
     * {@code WAWebFetchOHAIKeyConfigJobQuery.graphql}, which projects
     * {@code aead_id}, {@code expiration_date}, {@code kdf_id},
     * {@code kem_id}, {@code key_id}, {@code last_updated_time} and
     * {@code public_key} as the seven required scalar fields. Callers
     * pass the projected scalars to an HPKE library (WA Web uses its own
     * {@code Hpke.AeadID}, {@code Hpke.KdfID} and {@code Hpke.KemID}
     * enums to cast the string ids) before encapsulating an ACS request.
     */
    public static final class OhaiConfig {
        /**
         * The {@code aead_id} scalar identifying the AEAD cipher suite
         * paired with this OHAI key.
         */
        private final String aeadId;

        /**
         * The {@code expiration_date} scalar (Unix epoch second) after
         * which this key is considered expired.
         */
        private final String expirationDate;

        /**
         * The {@code kdf_id} scalar identifying the HPKE KDF paired with
         * this OHAI key.
         */
        private final String kdfId;

        /**
         * The {@code kem_id} scalar identifying the HPKE KEM paired with
         * this OHAI key.
         */
        private final String kemId;

        /**
         * The {@code key_id} scalar carrying the server-assigned
         * identifier for this OHAI key entry.
         */
        private final String keyId;

        /**
         * The {@code last_updated_time} scalar (Unix epoch second) marking
         * when the relay last issued this entry.
         */
        private final String lastUpdatedTime;

        /**
         * The {@code public_key} scalar carrying the OHAI public-key bytes
         * encoded as a hexadecimal string.
         *
         * @apiNote WA Web feeds the value through
         * {@code WAHex.parseHex} and wraps the result in a
         * {@code Uint8Array} before passing it to the HPKE encapsulator;
         * Cobalt callers perform the same hex decode locally.
         */
        private final String publicKey;

        /**
         * Constructs a new OHAI key configuration entry from the parsed
         * scalar fields of the {@code XWA2OHAIConfig} fragment.
         *
         * @apiNote Private; instances are produced by
         * {@link #of(JSONObject)}.
         *
         * @param aeadId          the {@code aead_id} scalar, may be {@code null}
         * @param expirationDate  the {@code expiration_date} scalar, may be {@code null}
         * @param kdfId           the {@code kdf_id} scalar, may be {@code null}
         * @param kemId           the {@code kem_id} scalar, may be {@code null}
         * @param keyId           the {@code key_id} scalar, may be {@code null}
         * @param lastUpdatedTime the {@code last_updated_time} scalar, may be {@code null}
         * @param publicKey       the {@code public_key} scalar, may be {@code null}
         */
        private OhaiConfig(String aeadId, String expirationDate, String kdfId, String kemId,
                           String keyId, String lastUpdatedTime, String publicKey) {
            this.aeadId = aeadId;
            this.expirationDate = expirationDate;
            this.kdfId = kdfId;
            this.kemId = kemId;
            this.keyId = keyId;
            this.lastUpdatedTime = lastUpdatedTime;
            this.publicKey = publicKey;
        }

        /**
         * Returns the {@code aead_id} scalar identifying the AEAD cipher
         * suite paired with this OHAI key.
         *
         * @return an {@link Optional} containing the AEAD identifier, or
         *         {@link Optional#empty()} if the relay omitted the scalar
         */
        public Optional<String> aeadId() {
            return Optional.ofNullable(aeadId);
        }

        /**
         * Returns the {@code expiration_date} scalar, the Unix epoch
         * second after which this key is considered expired.
         *
         * @return an {@link Optional} containing the expiration second,
         *         or {@link Optional#empty()} if the relay omitted the scalar
         */
        public Optional<String> expirationDate() {
            return Optional.ofNullable(expirationDate);
        }

        /**
         * Returns the {@code kdf_id} scalar identifying the HPKE KDF
         * paired with this OHAI key.
         *
         * @return an {@link Optional} containing the KDF identifier, or
         *         {@link Optional#empty()} if the relay omitted the scalar
         */
        public Optional<String> kdfId() {
            return Optional.ofNullable(kdfId);
        }

        /**
         * Returns the {@code kem_id} scalar identifying the HPKE KEM
         * paired with this OHAI key.
         *
         * @return an {@link Optional} containing the KEM identifier, or
         *         {@link Optional#empty()} if the relay omitted the scalar
         */
        public Optional<String> kemId() {
            return Optional.ofNullable(kemId);
        }

        /**
         * Returns the {@code key_id} scalar carrying the server-assigned
         * identifier of this OHAI key entry.
         *
         * @return an {@link Optional} containing the key identifier, or
         *         {@link Optional#empty()} if the relay omitted the scalar
         */
        public Optional<String> keyId() {
            return Optional.ofNullable(keyId);
        }

        /**
         * Returns the {@code last_updated_time} scalar, the Unix epoch
         * second at which the relay last issued this entry.
         *
         * @return an {@link Optional} containing the last-updated second,
         *         or {@link Optional#empty()} if the relay omitted the scalar
         */
        public Optional<String> lastUpdatedTime() {
            return Optional.ofNullable(lastUpdatedTime);
        }

        /**
         * Returns the {@code public_key} scalar carrying the OHAI public
         * key bytes encoded as a hexadecimal string.
         *
         * @return an {@link Optional} containing the hex-encoded public
         *         key, or {@link Optional#empty()} if the relay omitted
         *         the scalar
         */
        public Optional<String> publicKey() {
            return Optional.ofNullable(publicKey);
        }

        /**
         * Parses a single {@link OhaiConfig} entry from the given JSON
         * object.
         *
         * @apiNote Used by {@link #ofArray(JSONArray)} when walking the
         * {@code ohai_configs} array; callers receive
         * {@link Optional#empty()} when {@code obj} is {@code null}.
         *
         * @param obj the JSON object carrying the entry scalars, may be {@code null}
         * @return an {@link Optional} wrapping the parsed entry, or
         *         {@link Optional#empty()} if {@code obj} is {@code null}
         */
        static Optional<OhaiConfig> of(JSONObject obj) {
            if (obj == null) {
                return Optional.empty();
            }

            var aeadId = obj.getString("aead_id");
            var expirationDate = obj.getString("expiration_date");
            var kdfId = obj.getString("kdf_id");
            var kemId = obj.getString("kem_id");
            var keyId = obj.getString("key_id");
            var lastUpdatedTime = obj.getString("last_updated_time");
            var publicKey = obj.getString("public_key");
            return Optional.of(new OhaiConfig(aeadId, expirationDate, kdfId, kemId, keyId, lastUpdatedTime, publicKey));
        }

        /**
         * Parses a list of {@link OhaiConfig} entries from the given JSON
         * array.
         *
         * @apiNote Walks every element through {@link #of(JSONObject)};
         * {@code null} entries inside the array are skipped via
         * {@link Optional#empty()}. A {@code null} array collapses to
         * {@link List#of()}.
         *
         * @param arr the JSON array carrying the OHAI configuration
         *            entries, may be {@code null}
         * @return an unmodifiable list of parsed entries, empty when
         *         {@code arr} is {@code null}
         */
        static List<OhaiConfig> ofArray(JSONArray arr) {
            if (arr == null) {
                return List.of();
            }

            var result = new ArrayList<OhaiConfig>(arr.size());
            for (var i = 0; i < arr.size(); i++) {
                of(arr.getJSONObject(i)).ifPresent(result::add);
            }
            return result;
        }
    }
}
