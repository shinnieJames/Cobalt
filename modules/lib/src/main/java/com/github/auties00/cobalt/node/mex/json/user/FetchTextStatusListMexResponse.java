package com.github.auties00.cobalt.node.mex.json.user;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.mex.MexOperation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Decoded reply to the text-status batch query.
 *
 * @apiNote Consume after dispatching {@link FetchTextStatusListMexRequest}.
 * Each {@link Item} mirrors one element of WA Web's
 * {@code parseTextStatusServerResponse} output (id, text, emoji,
 * last-update timestamp, ephemeral duration). Empty {@link #items()}
 * indicates the relay returned no rows for the queried batch.
 *
 * @see FetchTextStatusListMexRequest
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchTextStatusListJob")
public final class FetchTextStatusListMexResponse implements MexOperation.Response.Json {
    /**
     * The decoded {@code xwa2_text_status_list} array, one entry per user.
     */
    private final List<Item> items;

    /**
     * Wraps a pre-parsed list of per-user records.
     *
     * @param items the per-user records produced by {@link #of(byte[])}
     */
    private FetchTextStatusListMexResponse(List<Item> items) {
        this.items = items;
    }

    /**
     * Decodes the {@code <result>} child of an inbound MEX IQ.
     *
     * @apiNote Pass the IQ node received in reply to a stanza dispatched
     * with {@link FetchTextStatusListMexRequest#toNode()}.
     *
     * @param node the IQ reply stanza
     * @return the decoded reply, or {@link Optional#empty()} when the
     *         payload is missing or malformed
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchTextStatusListJob", exports = "mexGetTextStatusList",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<FetchTextStatusListMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(FetchTextStatusListMexResponse::of);
    }

    /**
     * Returns the decoded {@code xwa2_text_status_list} entries.
     *
     * @apiNote The list mirrors the order the relay returned. WA Web keys
     * the response back to the request batch by {@link Item#jid()}.
     *
     * @return the per-user records; may be empty, never {@code null}
     */
    public List<Item> items() {
        return items;
    }

    /**
     * A decoded text-status record for one user.
     *
     * @apiNote Mirrors WA Web's {@code parseTextStatusServerResponse} per-row
     * projection. {@link #lastUpdateTime()} and {@link #ephemeralDurationSec()}
     * drive the expiry countdown rendered by the Status tab UI.
     */
    public static final class Item {
        /**
         * The {@code jid} field identifying the status author.
         */
        private final String jid;

        /**
         * The {@code text} field carrying the status body.
         */
        private final String text;

        /**
         * The {@code last_update_time} field, in epoch seconds.
         */
        private final Long lastUpdateTime;

        /**
         * The {@code ephemeral_duration_sec} field, in seconds.
         */
        private final Long ephemeralDurationSec;

        /**
         * The decoded {@code emoji} sub-object, possibly {@code null}.
         */
        private final Emoji emoji;

        /**
         * Wraps the decoded fields of a single text-status row.
         *
         * @param jid the {@code jid} field
         * @param text the {@code text} field
         * @param lastUpdateTime the {@code last_update_time} field, in
         *                       epoch seconds
         * @param ephemeralDurationSec the {@code ephemeral_duration_sec}
         *                             field, in seconds
         * @param emoji the decoded {@code emoji} sub-object, or
         *              {@code null} when the relay omitted it
         */
        private Item(String jid, String text, Long lastUpdateTime, Long ephemeralDurationSec, Emoji emoji) {
            this.jid = jid;
            this.text = text;
            this.lastUpdateTime = lastUpdateTime;
            this.ephemeralDurationSec = ephemeralDurationSec;
            this.emoji = emoji;
        }

        /**
         * Returns the status author identifier.
         *
         * @apiNote Mirrors WA Web's {@code id} projection, derived from the
         * raw {@code jid} string via {@code WAWebWidFactory.createWid}.
         *
         * @return the author JID wrapped in an {@link Optional}, or
         *         {@link Optional#empty()} when the relay omitted the field
         */
        public Optional<String> jid() {
            return Optional.ofNullable(jid);
        }

        /**
         * Returns the status text body.
         *
         * @return the body wrapped in an {@link Optional}, or
         *         {@link Optional#empty()} when the relay omitted the field
         */
        public Optional<String> text() {
            return Optional.ofNullable(text);
        }

        /**
         * Returns the timestamp the status was last updated at.
         *
         * @apiNote Used together with {@link #ephemeralDurationSec()} to
         * compute the expiry instant rendered in the Status tab.
         *
         * @return the timestamp wrapped in an {@link Optional}, or
         *         {@link Optional#empty()} when the relay omitted the field
         */
        public Optional<Instant> lastUpdateTime() {
            return Optional.ofNullable(lastUpdateTime).map(Instant::ofEpochSecond);
        }

        /**
         * Returns the duration after which the status expires.
         *
         * @apiNote A status with {@link #lastUpdateTime()} {@code + this}
         * in the past has already expired and should be elided from the UI.
         *
         * @return the duration wrapped in an {@link Optional}, or
         *         {@link Optional#empty()} when the relay omitted the field
         */
        public Optional<Duration> ephemeralDurationSec() {
            return Optional.ofNullable(ephemeralDurationSec).map(Duration::ofSeconds);
        }

        /**
         * Returns the decoded emoji decoration accompanying the status.
         *
         * @return the emoji wrapped in an {@link Optional}, or
         *         {@link Optional#empty()} when the relay omitted the field
         */
        public Optional<Emoji> emoji() {
            return Optional.ofNullable(emoji);
        }

        /**
         * Decoded {@code emoji} sub-object of a text-status row.
         *
         * @apiNote Mirrors the {@code textStatusEmoji} field that WA Web
         * projects via {@code emoji.content} in
         * {@code parseTextStatusServerResponse}.
         */
        public static final class Emoji {
            /**
             * The {@code content} field carrying the emoji codepoints.
             */
            private final String content;

            /**
             * Wraps the decoded emoji content string.
             *
             * @param content the {@code content} field, possibly
             *                {@code null}
             */
            private Emoji(String content) {
                this.content = content;
            }

            /**
             * Returns the emoji codepoints.
             *
             * @return the content wrapped in an {@link Optional}, or
             *         {@link Optional#empty()} when the relay omitted the
             *         field
             */
            public Optional<String> content() {
                return Optional.ofNullable(content);
            }

            /**
             * Decodes a single emoji entry from a {@link JSONObject}.
             *
             * @apiNote Used by {@link Item#of(JSONObject)} when projecting
             * the {@code emoji} sub-object; not part of the public API.
             *
             * @param obj the JSON object to decode, possibly {@code null}
             * @return the decoded emoji, or {@link Optional#empty()} when
             *         {@code obj} is {@code null}
             */
            static Optional<Emoji> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var content = obj.getString("content");
                return Optional.of(new Emoji(content));
            }

            /**
             * Decodes a list of emoji entries from a {@link JSONArray}.
             *
             * @apiNote Provided for parity with the other {@code ofArray}
             * helpers in this file; not invoked by the response decoder
             * because the wire schema carries {@code emoji} as a single
             * sub-object, not an array.
             *
             * @param arr the JSON array to decode, possibly {@code null}
             * @return the decoded emojis in source order; empty when
             *         {@code arr} is {@code null}
             */
            static List<Emoji> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<Emoji>(arr.size());
                for (var i = 0; i < arr.size(); i++) {
                    of(arr.getJSONObject(i)).ifPresent(result::add);
                }
                return result;
            }
        }

        /**
         * Decodes a single text-status row from a {@link JSONObject}.
         *
         * @apiNote Used by {@link #ofArray(JSONArray)} while walking the
         * {@code xwa2_text_status_list} array; not part of the public API.
         *
         * @param obj the JSON object to decode, possibly {@code null}
         * @return the decoded row, or {@link Optional#empty()} when
         *         {@code obj} is {@code null}
         */
        static Optional<Item> of(JSONObject obj) {
            if (obj == null) {
                return Optional.empty();
            }

            var jid = obj.getString("jid");
            var text = obj.getString("text");
            var lastUpdateTime = obj.getLong("last_update_time");
            var ephemeralDurationSec = obj.getLong("ephemeral_duration_sec");
            var emoji = Emoji.of(obj.getJSONObject("emoji")).orElse(null);
            return Optional.of(new Item(jid, text, lastUpdateTime, ephemeralDurationSec, emoji));
        }

        /**
         * Decodes the {@code xwa2_text_status_list} array of the MEX payload.
         *
         * @apiNote Used by the package-level decoder to project the array
         * nested under {@code data} of the {@code <result>} payload; not
         * part of the public API.
         *
         * @param arr the JSON array to decode, possibly {@code null}
         * @return the decoded rows in source order; empty when {@code arr}
         *         is {@code null}
         */
        static List<Item> ofArray(JSONArray arr) {
            if (arr == null) {
                return List.of();
            }

            var result = new ArrayList<Item>(arr.size());
            for (var i = 0; i < arr.size(); i++) {
                of(arr.getJSONObject(i)).ifPresent(result::add);
            }
            return result;
        }
    }

    /**
     * Decodes the {@code <result>} payload bytes into a {@link FetchTextStatusListMexResponse}.
     *
     * @implNote This implementation projects {@code data.xwa2_text_status_list};
     * a missing {@code data} envelope yields {@link Optional#empty()}, while
     * a present-but-empty array yields a response with an empty
     * {@link #items()} list.
     *
     * @param json the raw {@code <result>} payload bytes
     * @return the decoded reply, or {@link Optional#empty()} when the
     *         payload does not parse as a JSON object or lacks the
     *         {@code data} envelope
     */
    private static Optional<FetchTextStatusListMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var rootArr = data.getJSONArray("xwa2_text_status_list");
        var items = Item.ofArray(rootArr);

        return Optional.of(new FetchTextStatusListMexResponse(items));
    }
}
