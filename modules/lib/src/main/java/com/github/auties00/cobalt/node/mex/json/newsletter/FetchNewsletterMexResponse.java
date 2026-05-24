package com.github.auties00.cobalt.node.mex.json.newsletter;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Parses the MEX response of the fetch-newsletter query built by
 * {@link FetchNewsletterMexRequest}.
 *
 * @apiNote
 * Exposes the full newsletter projection echoed under
 * {@code xwa2_newsletter}: the newsletter id, the optional state
 * sub-object, the rich {@link ThreadMetadata} (versioned name,
 * description, picture, preview, optional creation time, optional
 * status metadata and paid-subscription info) and the optional
 * {@link ViewerMetadata} (viewer role, per-channel notification
 * settings, WAMo subscription status).
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterJob")
public final class FetchNewsletterMexResponse implements MexOperation.Response.Json {
    /**
     * The newsletter Jid string.
     */
    private final String id;

    /**
     * The optional state sub-object.
     */
    private final State state;

    /**
     * The rich thread metadata.
     */
    private final ThreadMetadata threadMetadata;

    /**
     * The optional viewer-side metadata.
     */
    private final ViewerMetadata viewerMetadata;

    /**
     * Constructs a response wrapping the parsed top-level fields.
     *
     * @apiNote
     * Reserved for the static parser.
     *
     * @param id             the newsletter Jid string
     * @param state          the optional state sub-object
     * @param threadMetadata the rich thread metadata
     * @param viewerMetadata the optional viewer-side metadata
     */
    private FetchNewsletterMexResponse(String id, State state, ThreadMetadata threadMetadata, ViewerMetadata viewerMetadata) {
        this.id = id;
        this.state = state;
        this.threadMetadata = threadMetadata;
        this.viewerMetadata = viewerMetadata;
    }

    /**
     * Parses the MEX response carried by the given IQ result node.
     *
     * @apiNote
     * Drains the {@code <result>} child's byte content into the JSON parser;
     * the returned {@link Optional} is empty when the result child is
     * missing or when the JSON envelope omits the expected
     * {@code data.xwa2_newsletter} root.
     *
     * @param node the IQ result node received from the relay
     * @return the parsed response, or empty when the node does not carry a
     *         well-formed result payload
     */
    public static Optional<FetchNewsletterMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(FetchNewsletterMexResponse::of);
    }

    /**
     * Returns the newsletter Jid string.
     *
     * @return the newsletter id, or empty when the relay omitted the field
     */
    public Optional<String> id() {
        return Optional.ofNullable(id);
    }

    /**
     * Returns the state sub-object.
     *
     * @return the parsed {@link State}, or empty when the relay omitted the
     *         field
     */
    public Optional<State> state() {
        return Optional.ofNullable(state);
    }

    /**
     * Returns the rich thread metadata.
     *
     * @return the parsed {@link ThreadMetadata}, or empty when the relay
     *         omitted the field
     */
    public Optional<ThreadMetadata> threadMetadata() {
        return Optional.ofNullable(threadMetadata);
    }

    /**
     * Returns the viewer-side metadata.
     *
     * @return the parsed {@link ViewerMetadata}, or empty when the relay
     *         omitted the field
     */
    public Optional<ViewerMetadata> viewerMetadata() {
        return Optional.ofNullable(viewerMetadata);
    }

    /**
     * Wraps the {@code state} sub-object.
     *
     * @apiNote
     * Carries a single {@code type} label describing the newsletter's
     * lifecycle state (active, suspended, geo-suspended, etc.).
     */
    public static final class State {
        /**
         * The state type label.
         */
        private final String type;

        /**
         * Constructs a state wrapper from the parsed sub-fields.
         *
         * @apiNote
         * Reserved for the static parser.
         *
         * @param type the state type label
         */
        private State(String type) {
            this.type = type;
        }

        /**
         * Returns the state type label.
         *
         * @return the type label, or empty when the relay omitted the field
         */
        public Optional<String> type() {
            return Optional.ofNullable(type);
        }

        /**
         * Parses a {@link State} from the given JSON object.
         *
         * @apiNote
         * Used by {@link FetchNewsletterMexResponse#of(byte[])} to hydrate
         * the nested {@code state} entry.
         *
         * @param obj the JSON object to parse
         * @return the parsed entry, or empty when {@code obj} is
         *         {@code null}
         */
        static Optional<State> of(JSONObject obj) {
            if (obj == null) {
                return Optional.empty();
            }

            var type = obj.getString("type");
            return Optional.of(new State(type));
        }

        /**
         * Parses a list of {@link State} entries from the given JSON array.
         *
         * @apiNote
         * Provided for symmetry; the newsletter envelope does not carry a
         * {@code state} array.
         *
         * @param arr the JSON array to parse
         * @return the parsed list, empty when {@code arr} is {@code null}
         */
        static List<State> ofArray(JSONArray arr) {
            if (arr == null) {
                return List.of();
            }

            var result = new ArrayList<State>(arr.size());
            for (var i = 0; i < arr.size(); i++) {
                of(arr.getJSONObject(i)).ifPresent(result::add);
            }
            return result;
        }
    }

    /**
     * Wraps the rich {@code thread_metadata} sub-object.
     *
     * @apiNote
     * Carries the full newsletter metadata projection: optional creation
     * time, versioned name and description, picture and preview image
     * references, public invite token and handle, subscriber count,
     * verification tier label, per-channel settings, optional WAMo
     * paid-subscription id, and optional status (last published status)
     * metadata.
     */
    public static final class ThreadMetadata {
        /**
         * The newsletter creation epoch-second.
         */
        private final Long creationTime;

        /**
         * The versioned name sub-object.
         */
        private final Name name;

        /**
         * The picture reference sub-object.
         */
        private final Picture picture;

        /**
         * The preview-image reference sub-object.
         */
        private final Preview preview;

        /**
         * The versioned description sub-object.
         */
        private final Description description;

        /**
         * The public invite token.
         */
        private final String invite;

        /**
         * The public handle.
         */
        private final String handle;

        /**
         * The follower count.
         */
        private final Long subscribersCount;

        /**
         * The verification tier label.
         */
        private final String verification;

        /**
         * The per-channel settings sub-object.
         */
        private final Settings settings;

        /**
         * The optional WAMo paid-subscription id sub-object.
         */
        private final WamoSub wamoSub;

        /**
         * The optional status-metadata sub-object.
         */
        private final StatusMetadata statusMetadata;

        /**
         * Constructs a thread-metadata wrapper from the parsed sub-fields.
         *
         * @apiNote
         * Reserved for the static parser.
         *
         * @param creationTime     the newsletter creation epoch-second
         * @param name             the versioned name sub-object
         * @param picture          the picture reference sub-object
         * @param preview          the preview-image reference sub-object
         * @param description      the versioned description sub-object
         * @param invite           the public invite token
         * @param handle           the public handle
         * @param subscribersCount the follower count
         * @param verification     the verification tier label
         * @param settings         the per-channel settings sub-object
         * @param wamoSub          the optional WAMo paid-subscription id
         *                         sub-object
         * @param statusMetadata   the optional status-metadata sub-object
         */
        private ThreadMetadata(Long creationTime, Name name, Picture picture, Preview preview, Description description, String invite, String handle, Long subscribersCount, String verification, Settings settings, WamoSub wamoSub, StatusMetadata statusMetadata) {
            this.creationTime = creationTime;
            this.name = name;
            this.picture = picture;
            this.preview = preview;
            this.description = description;
            this.invite = invite;
            this.handle = handle;
            this.subscribersCount = subscribersCount;
            this.verification = verification;
            this.settings = settings;
            this.wamoSub = wamoSub;
            this.statusMetadata = statusMetadata;
        }

        /**
         * Returns the newsletter creation instant.
         *
         * @return the creation instant, or empty when the relay omitted the
         *         field
         */
        public Optional<Instant> creationTime() {
            return Optional.ofNullable(creationTime).map(Instant::ofEpochSecond);
        }

        /**
         * Returns the versioned name sub-object.
         *
         * @return the parsed {@link Name}, or empty when the relay omitted
         *         the field
         */
        public Optional<Name> name() {
            return Optional.ofNullable(name);
        }

        /**
         * Returns the picture reference sub-object.
         *
         * @return the parsed {@link Picture}, or empty when the relay
         *         omitted the field
         */
        public Optional<Picture> picture() {
            return Optional.ofNullable(picture);
        }

        /**
         * Returns the preview-image reference sub-object.
         *
         * @return the parsed {@link Preview}, or empty when the relay
         *         omitted the field
         */
        public Optional<Preview> preview() {
            return Optional.ofNullable(preview);
        }

        /**
         * Returns the versioned description sub-object.
         *
         * @return the parsed {@link Description}, or empty when the relay
         *         omitted the field
         */
        public Optional<Description> description() {
            return Optional.ofNullable(description);
        }

        /**
         * Returns the public invite token.
         *
         * @return the invite token, or empty when the relay omitted the
         *         field
         */
        public Optional<String> invite() {
            return Optional.ofNullable(invite);
        }

        /**
         * Returns the public handle.
         *
         * @return the public handle, or empty when the relay omitted the
         *         field
         */
        public Optional<String> handle() {
            return Optional.ofNullable(handle);
        }

        /**
         * Returns the follower count.
         *
         * @return the follower count, or empty when the relay omitted the
         *         field
         */
        public OptionalLong subscribersCount() {
            return subscribersCount != null ? OptionalLong.of(subscribersCount) : OptionalLong.empty();
        }

        /**
         * Returns the verification tier label.
         *
         * @return the verification label, or empty when the relay omitted
         *         the field
         */
        public Optional<String> verification() {
            return Optional.ofNullable(verification);
        }

        /**
         * Returns the per-channel settings sub-object.
         *
         * @return the parsed {@link Settings}, or empty when the relay
         *         omitted the field
         */
        public Optional<Settings> settings() {
            return Optional.ofNullable(settings);
        }

        /**
         * Returns the WAMo paid-subscription id sub-object.
         *
         * @apiNote
         * Populated only when the request set {@code fetch_wamo_sub=true}
         * and the newsletter carries a paid subscription plan.
         *
         * @return the parsed {@link WamoSub}, or empty when the relay
         *         omitted the field
         */
        public Optional<WamoSub> wamoSub() {
            return Optional.ofNullable(wamoSub);
        }

        /**
         * Returns the status-metadata sub-object.
         *
         * @apiNote
         * Populated only when the request set
         * {@code fetch_status_metadata=true}; carries the last published
         * status id and timestamp.
         *
         * @return the parsed {@link StatusMetadata}, or empty when the
         *         relay omitted the field
         */
        public Optional<StatusMetadata> statusMetadata() {
            return Optional.ofNullable(statusMetadata);
        }

        /**
         * Wraps the {@code name} versioned-text sub-object.
         *
         * @apiNote
         * Carries the server-assigned revision id, the current text and
         * the last-update epoch-second.
         */
        public static final class Name {
            /**
             * The revision identifier.
             */
            private final String id;

            /**
             * The current text.
             */
            private final String text;

            /**
             * The epoch-second of the last update.
             */
            private final Long updateTime;

            /**
             * Constructs a name wrapper from the parsed sub-fields.
             *
             * @apiNote
             * Reserved for the static parser.
             *
             * @param id         the revision identifier
             * @param text       the current text
             * @param updateTime the epoch-second of the last update
             */
            private Name(String id, String text, Long updateTime) {
                this.id = id;
                this.text = text;
                this.updateTime = updateTime;
            }

            /**
             * Returns the revision identifier.
             *
             * @return the revision id, or empty when the relay omitted the
             *         field
             */
            public Optional<String> id() {
                return Optional.ofNullable(id);
            }

            /**
             * Returns the current text.
             *
             * @return the text, or empty when the relay omitted the field
             */
            public Optional<String> text() {
                return Optional.ofNullable(text);
            }

            /**
             * Returns the last-update instant.
             *
             * @return the update instant, or empty when the relay omitted
             *         the field
             */
            public Optional<Instant> updateTime() {
                return Optional.ofNullable(updateTime).map(Instant::ofEpochSecond);
            }

            /**
             * Parses a {@link Name} from the given JSON object.
             *
             * @apiNote
             * Used by {@link ThreadMetadata#of(JSONObject)} to hydrate the
             * nested {@code name} entry.
             *
             * @param obj the JSON object to parse
             * @return the parsed entry, or empty when {@code obj} is
             *         {@code null}
             */
            static Optional<Name> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var id = obj.getString("id");
                var text = obj.getString("text");
                var updateTime = obj.getLong("update_time");
                return Optional.of(new Name(id, text, updateTime));
            }

            /**
             * Parses a list of {@link Name} entries from the given JSON
             * array.
             *
             * @apiNote
             * Provided for symmetry.
             *
             * @param arr the JSON array to parse
             * @return the parsed list, empty when {@code arr} is
             *         {@code null}
             */
            static List<Name> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<Name>(arr.size());
                for (var i = 0; i < arr.size(); i++) {
                    of(arr.getJSONObject(i)).ifPresent(result::add);
                }
                return result;
            }
        }

        /**
         * Wraps the {@code picture} reference sub-object.
         *
         * @apiNote
         * Carries the file id, type discriminator and direct-path used to
         * fetch the picture bytes.
         */
        public static final class Picture {
            /**
             * The file identifier.
             */
            private final String id;

            /**
             * The picture type discriminator.
             */
            private final String type;

            /**
             * The relay direct-path.
             */
            private final String directPath;

            /**
             * Constructs a picture wrapper from the parsed sub-fields.
             *
             * @apiNote
             * Reserved for the static parser.
             *
             * @param id         the file identifier
             * @param type       the picture type discriminator
             * @param directPath the relay direct-path
             */
            private Picture(String id, String type, String directPath) {
                this.id = id;
                this.type = type;
                this.directPath = directPath;
            }

            /**
             * Returns the file identifier.
             *
             * @return the file id, or empty when the relay omitted the
             *         field
             */
            public Optional<String> id() {
                return Optional.ofNullable(id);
            }

            /**
             * Returns the picture type discriminator.
             *
             * @return the type, or empty when the relay omitted the field
             */
            public Optional<String> type() {
                return Optional.ofNullable(type);
            }

            /**
             * Returns the relay direct-path.
             *
             * @return the direct path, or empty when the relay omitted the
             *         field
             */
            public Optional<String> directPath() {
                return Optional.ofNullable(directPath);
            }

            /**
             * Parses a {@link Picture} from the given JSON object.
             *
             * @apiNote
             * Used by {@link ThreadMetadata#of(JSONObject)} to hydrate the
             * nested {@code picture} entry.
             *
             * @param obj the JSON object to parse
             * @return the parsed entry, or empty when {@code obj} is
             *         {@code null}
             */
            static Optional<Picture> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var id = obj.getString("id");
                var type = obj.getString("type");
                var directPath = obj.getString("direct_path");
                return Optional.of(new Picture(id, type, directPath));
            }

            /**
             * Parses a list of {@link Picture} entries from the given JSON
             * array.
             *
             * @apiNote
             * Provided for symmetry.
             *
             * @param arr the JSON array to parse
             * @return the parsed list, empty when {@code arr} is
             *         {@code null}
             */
            static List<Picture> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<Picture>(arr.size());
                for (var i = 0; i < arr.size(); i++) {
                    of(arr.getJSONObject(i)).ifPresent(result::add);
                }
                return result;
            }
        }

        /**
         * Wraps the {@code preview} reference sub-object.
         *
         * @apiNote
         * Same shape as {@link Picture} but points to the smaller preview
         * variant used for tile rendering.
         */
        public static final class Preview {
            /**
             * The file identifier.
             */
            private final String id;

            /**
             * The picture type discriminator.
             */
            private final String type;

            /**
             * The relay direct-path.
             */
            private final String directPath;

            /**
             * Constructs a preview wrapper from the parsed sub-fields.
             *
             * @apiNote
             * Reserved for the static parser.
             *
             * @param id         the file identifier
             * @param type       the picture type discriminator
             * @param directPath the relay direct-path
             */
            private Preview(String id, String type, String directPath) {
                this.id = id;
                this.type = type;
                this.directPath = directPath;
            }

            /**
             * Returns the file identifier.
             *
             * @return the file id, or empty when the relay omitted the
             *         field
             */
            public Optional<String> id() {
                return Optional.ofNullable(id);
            }

            /**
             * Returns the picture type discriminator.
             *
             * @return the type, or empty when the relay omitted the field
             */
            public Optional<String> type() {
                return Optional.ofNullable(type);
            }

            /**
             * Returns the relay direct-path.
             *
             * @return the direct path, or empty when the relay omitted the
             *         field
             */
            public Optional<String> directPath() {
                return Optional.ofNullable(directPath);
            }

            /**
             * Parses a {@link Preview} from the given JSON object.
             *
             * @apiNote
             * Used by {@link ThreadMetadata#of(JSONObject)} to hydrate the
             * nested {@code preview} entry.
             *
             * @param obj the JSON object to parse
             * @return the parsed entry, or empty when {@code obj} is
             *         {@code null}
             */
            static Optional<Preview> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var id = obj.getString("id");
                var type = obj.getString("type");
                var directPath = obj.getString("direct_path");
                return Optional.of(new Preview(id, type, directPath));
            }

            /**
             * Parses a list of {@link Preview} entries from the given JSON
             * array.
             *
             * @apiNote
             * Provided for symmetry.
             *
             * @param arr the JSON array to parse
             * @return the parsed list, empty when {@code arr} is
             *         {@code null}
             */
            static List<Preview> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<Preview>(arr.size());
                for (var i = 0; i < arr.size(); i++) {
                    of(arr.getJSONObject(i)).ifPresent(result::add);
                }
                return result;
            }
        }

        /**
         * Wraps the {@code description} versioned-text sub-object.
         *
         * @apiNote
         * Same shape as {@link Name}: revision id, current text and
         * last-update epoch-second.
         */
        public static final class Description {
            /**
             * The revision identifier.
             */
            private final String id;

            /**
             * The current text.
             */
            private final String text;

            /**
             * The epoch-second of the last update.
             */
            private final Long updateTime;

            /**
             * Constructs a description wrapper from the parsed sub-fields.
             *
             * @apiNote
             * Reserved for the static parser.
             *
             * @param id         the revision identifier
             * @param text       the current text
             * @param updateTime the epoch-second of the last update
             */
            private Description(String id, String text, Long updateTime) {
                this.id = id;
                this.text = text;
                this.updateTime = updateTime;
            }

            /**
             * Returns the revision identifier.
             *
             * @return the revision id, or empty when the relay omitted the
             *         field
             */
            public Optional<String> id() {
                return Optional.ofNullable(id);
            }

            /**
             * Returns the current text.
             *
             * @return the text, or empty when the relay omitted the field
             */
            public Optional<String> text() {
                return Optional.ofNullable(text);
            }

            /**
             * Returns the last-update instant.
             *
             * @return the update instant, or empty when the relay omitted
             *         the field
             */
            public Optional<Instant> updateTime() {
                return Optional.ofNullable(updateTime).map(Instant::ofEpochSecond);
            }

            /**
             * Parses a {@link Description} from the given JSON object.
             *
             * @apiNote
             * Used by {@link ThreadMetadata#of(JSONObject)} to hydrate the
             * nested {@code description} entry.
             *
             * @param obj the JSON object to parse
             * @return the parsed entry, or empty when {@code obj} is
             *         {@code null}
             */
            static Optional<Description> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var id = obj.getString("id");
                var text = obj.getString("text");
                var updateTime = obj.getLong("update_time");
                return Optional.of(new Description(id, text, updateTime));
            }

            /**
             * Parses a list of {@link Description} entries from the given
             * JSON array.
             *
             * @apiNote
             * Provided for symmetry.
             *
             * @param arr the JSON array to parse
             * @return the parsed list, empty when {@code arr} is
             *         {@code null}
             */
            static List<Description> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<Description>(arr.size());
                for (var i = 0; i < arr.size(); i++) {
                    of(arr.getJSONObject(i)).ifPresent(result::add);
                }
                return result;
            }
        }

        /**
         * Wraps the {@code settings} sub-object.
         *
         * @apiNote
         * Holds the per-channel reaction-code policy that gates which
         * emojis followers may use as message reactions.
         */
        public static final class Settings {
            /**
             * The reaction-code policy sub-object.
             */
            private final ReactionCodes reactionCodes;

            /**
             * Constructs a settings wrapper from the parsed sub-fields.
             *
             * @apiNote
             * Reserved for the static parser.
             *
             * @param reactionCodes the reaction-code policy sub-object
             */
            private Settings(ReactionCodes reactionCodes) {
                this.reactionCodes = reactionCodes;
            }

            /**
             * Returns the reaction-code policy sub-object.
             *
             * @return the parsed {@link ReactionCodes}, or empty when the
             *         relay omitted the field
             */
            public Optional<ReactionCodes> reactionCodes() {
                return Optional.ofNullable(reactionCodes);
            }

            /**
             * Wraps the {@code reaction_codes} sub-object.
             *
             * @apiNote
             * Exposes the policy value verbatim; consumers map it to the
             * corresponding {@code WAWebNewsletterReactionCodes} enum
             * themselves.
             */
            public static final class ReactionCodes {
                /**
                 * The textual policy value.
                 */
                private final String value;

                /**
                 * Constructs a reaction-codes wrapper from the parsed
                 * sub-fields.
                 *
                 * @apiNote
                 * Reserved for the static parser.
                 *
                 * @param value the textual policy value
                 */
                private ReactionCodes(String value) {
                    this.value = value;
                }

                /**
                 * Returns the textual policy value.
                 *
                 * @return the value, or empty when the relay omitted the
                 *         field
                 */
                public Optional<String> value() {
                    return Optional.ofNullable(value);
                }

                /**
                 * Parses a {@link ReactionCodes} from the given JSON
                 * object.
                 *
                 * @apiNote
                 * Used by {@link Settings#of(JSONObject)} to hydrate the
                 * nested {@code reaction_codes} entry.
                 *
                 * @param obj the JSON object to parse
                 * @return the parsed entry, or empty when {@code obj} is
                 *         {@code null}
                 */
                static Optional<ReactionCodes> of(JSONObject obj) {
                    if (obj == null) {
                        return Optional.empty();
                    }

                    var value = obj.getString("value");
                    return Optional.of(new ReactionCodes(value));
                }

                /**
                 * Parses a list of {@link ReactionCodes} entries from the
                 * given JSON array.
                 *
                 * @apiNote
                 * Provided for symmetry.
                 *
                 * @param arr the JSON array to parse
                 * @return the parsed list, empty when {@code arr} is
                 *         {@code null}
                 */
                static List<ReactionCodes> ofArray(JSONArray arr) {
                    if (arr == null) {
                        return List.of();
                    }

                    var result = new ArrayList<ReactionCodes>(arr.size());
                    for (var i = 0; i < arr.size(); i++) {
                        of(arr.getJSONObject(i)).ifPresent(result::add);
                    }
                    return result;
                }
            }

            /**
             * Parses a {@link Settings} from the given JSON object.
             *
             * @apiNote
             * Used by {@link ThreadMetadata#of(JSONObject)} to hydrate the
             * nested {@code settings} entry.
             *
             * @param obj the JSON object to parse
             * @return the parsed entry, or empty when {@code obj} is
             *         {@code null}
             */
            static Optional<Settings> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var reactionCodes = ReactionCodes.of(obj.getJSONObject("reaction_codes")).orElse(null);
                return Optional.of(new Settings(reactionCodes));
            }

            /**
             * Parses a list of {@link Settings} entries from the given JSON
             * array.
             *
             * @apiNote
             * Provided for symmetry.
             *
             * @param arr the JSON array to parse
             * @return the parsed list, empty when {@code arr} is
             *         {@code null}
             */
            static List<Settings> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<Settings>(arr.size());
                for (var i = 0; i < arr.size(); i++) {
                    of(arr.getJSONObject(i)).ifPresent(result::add);
                }
                return result;
            }
        }

        /**
         * Wraps the {@code wamo_sub} sub-object.
         *
         * @apiNote
         * Carries the WhatsApp Monetisation paid-subscription plan
         * identifier; populated only when the request set
         * {@code fetch_wamo_sub=true}.
         */
        public static final class WamoSub {
            /**
             * The subscription plan identifier.
             */
            private final String planId;

            /**
             * Constructs a WAMo-subscription wrapper from the parsed
             * sub-fields.
             *
             * @apiNote
             * Reserved for the static parser.
             *
             * @param planId the subscription plan identifier
             */
            private WamoSub(String planId) {
                this.planId = planId;
            }

            /**
             * Returns the subscription plan identifier.
             *
             * @return the plan id, or empty when the relay omitted the
             *         field
             */
            public Optional<String> planId() {
                return Optional.ofNullable(planId);
            }

            /**
             * Parses a {@link WamoSub} from the given JSON object.
             *
             * @apiNote
             * Used by {@link ThreadMetadata#of(JSONObject)} to hydrate the
             * nested {@code wamo_sub} entry.
             *
             * @param obj the JSON object to parse
             * @return the parsed entry, or empty when {@code obj} is
             *         {@code null}
             */
            static Optional<WamoSub> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var planId = obj.getString("plan_id");
                return Optional.of(new WamoSub(planId));
            }

            /**
             * Parses a list of {@link WamoSub} entries from the given JSON
             * array.
             *
             * @apiNote
             * Provided for symmetry.
             *
             * @param arr the JSON array to parse
             * @return the parsed list, empty when {@code arr} is
             *         {@code null}
             */
            static List<WamoSub> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<WamoSub>(arr.size());
                for (var i = 0; i < arr.size(); i++) {
                    of(arr.getJSONObject(i)).ifPresent(result::add);
                }
                return result;
            }
        }

        /**
         * Wraps the {@code status_metadata} sub-object.
         *
         * @apiNote
         * Carries the last published status id and timestamp; populated
         * only when the request set {@code fetch_status_metadata=true}.
         */
        public static final class StatusMetadata {
            /**
             * The last published status server id.
             */
            private final String lastStatusServerId;

            /**
             * The epoch-second the last status was sent.
             */
            private final Long lastStatusSentTime;

            /**
             * Constructs a status-metadata wrapper from the parsed
             * sub-fields.
             *
             * @apiNote
             * Reserved for the static parser.
             *
             * @param lastStatusServerId the last published status server id
             * @param lastStatusSentTime the epoch-second the last status
             *                           was sent
             */
            private StatusMetadata(String lastStatusServerId, Long lastStatusSentTime) {
                this.lastStatusServerId = lastStatusServerId;
                this.lastStatusSentTime = lastStatusSentTime;
            }

            /**
             * Returns the last published status server id.
             *
             * @return the server id, or empty when the relay omitted the
             *         field
             */
            public Optional<String> lastStatusServerId() {
                return Optional.ofNullable(lastStatusServerId);
            }

            /**
             * Returns the last-status sent instant.
             *
             * @return the instant, or empty when the relay omitted the
             *         field
             */
            public Optional<Instant> lastStatusSentTime() {
                return Optional.ofNullable(lastStatusSentTime).map(Instant::ofEpochSecond);
            }

            /**
             * Parses a {@link StatusMetadata} from the given JSON object.
             *
             * @apiNote
             * Used by {@link ThreadMetadata#of(JSONObject)} to hydrate the
             * nested {@code status_metadata} entry.
             *
             * @param obj the JSON object to parse
             * @return the parsed entry, or empty when {@code obj} is
             *         {@code null}
             */
            static Optional<StatusMetadata> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var lastStatusServerId = obj.getString("last_status_server_id");
                var lastStatusSentTime = obj.getLong("last_status_sent_time");
                return Optional.of(new StatusMetadata(lastStatusServerId, lastStatusSentTime));
            }

            /**
             * Parses a list of {@link StatusMetadata} entries from the
             * given JSON array.
             *
             * @apiNote
             * Provided for symmetry.
             *
             * @param arr the JSON array to parse
             * @return the parsed list, empty when {@code arr} is
             *         {@code null}
             */
            static List<StatusMetadata> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<StatusMetadata>(arr.size());
                for (var i = 0; i < arr.size(); i++) {
                    of(arr.getJSONObject(i)).ifPresent(result::add);
                }
                return result;
            }
        }

        /**
         * Parses a {@link ThreadMetadata} from the given JSON object.
         *
         * @apiNote
         * Used by {@link FetchNewsletterMexResponse#of(byte[])} to hydrate
         * the nested {@code thread_metadata} entry.
         *
         * @param obj the JSON object to parse
         * @return the parsed entry, or empty when {@code obj} is
         *         {@code null}
         */
        static Optional<ThreadMetadata> of(JSONObject obj) {
            if (obj == null) {
                return Optional.empty();
            }

            var creationTime = obj.getLong("creation_time");
            var name = Name.of(obj.getJSONObject("name")).orElse(null);
            var picture = Picture.of(obj.getJSONObject("picture")).orElse(null);
            var preview = Preview.of(obj.getJSONObject("preview")).orElse(null);
            var description = Description.of(obj.getJSONObject("description")).orElse(null);
            var invite = obj.getString("invite");
            var handle = obj.getString("handle");
            var subscribersCount = obj.getLong("subscribers_count");
            var verification = obj.getString("verification");
            var settings = Settings.of(obj.getJSONObject("settings")).orElse(null);
            var wamoSub = WamoSub.of(obj.getJSONObject("wamo_sub")).orElse(null);
            var statusMetadata = StatusMetadata.of(obj.getJSONObject("status_metadata")).orElse(null);
            return Optional.of(new ThreadMetadata(creationTime, name, picture, preview, description, invite, handle, subscribersCount, verification, settings, wamoSub, statusMetadata));
        }

        /**
         * Parses a list of {@link ThreadMetadata} entries from the given
         * JSON array.
         *
         * @apiNote
         * Provided for symmetry; the newsletter envelope does not carry a
         * {@code thread_metadata} array.
         *
         * @param arr the JSON array to parse
         * @return the parsed list, empty when {@code arr} is {@code null}
         */
        static List<ThreadMetadata> ofArray(JSONArray arr) {
            if (arr == null) {
                return List.of();
            }

            var result = new ArrayList<ThreadMetadata>(arr.size());
            for (var i = 0; i < arr.size(); i++) {
                of(arr.getJSONObject(i)).ifPresent(result::add);
            }
            return result;
        }
    }

    /**
     * Wraps the {@code viewer_metadata} sub-object.
     *
     * @apiNote
     * Carries the per-viewer projection: the array of per-channel
     * notification settings, the viewer role label
     * ({@code OWNER}/{@code ADMIN}/{@code SUBSCRIBER}/...) and the WAMo
     * subscription status label; populated only when the request set
     * {@code fetch_viewer_metadata=true}.
     */
    public static final class ViewerMetadata {
        /**
         * The per-channel viewer settings.
         */
        private final List<Settings> settings;

        /**
         * The viewer role label.
         */
        private final String role;

        /**
         * The WAMo subscription status label.
         */
        private final String wamoSubStatus;

        /**
         * Constructs a viewer-metadata wrapper from the parsed sub-fields.
         *
         * @apiNote
         * Reserved for the static parser.
         *
         * @param settings      the per-channel viewer settings
         * @param role          the viewer role label
         * @param wamoSubStatus the WAMo subscription status label
         */
        private ViewerMetadata(List<Settings> settings, String role, String wamoSubStatus) {
            this.settings = settings;
            this.role = role;
            this.wamoSubStatus = wamoSubStatus;
        }

        /**
         * Returns the per-channel viewer settings.
         *
         * @return the parsed settings, empty when the relay returned none
         */
        public List<Settings> settings() {
            return settings;
        }

        /**
         * Returns the viewer role label.
         *
         * @return the role label, or empty when the relay omitted the field
         */
        public Optional<String> role() {
            return Optional.ofNullable(role);
        }

        /**
         * Returns the WAMo subscription status label.
         *
         * @return the status label, or empty when the relay omitted the
         *         field
         */
        public Optional<String> wamoSubStatus() {
            return Optional.ofNullable(wamoSubStatus);
        }

        /**
         * Wraps one entry of the viewer-side {@code settings} array.
         *
         * @apiNote
         * Carries a {@code (type, value)} pair; WA Web treats each type as
         * a distinct preference key with the value as its current setting
         * (mute, notification, custom emoji, etc.).
         */
        public static final class Settings {
            /**
             * The setting type label.
             */
            private final String type;

            /**
             * The setting value label.
             */
            private final String value;

            /**
             * Constructs a settings wrapper from the parsed sub-fields.
             *
             * @apiNote
             * Reserved for the static parser.
             *
             * @param type  the setting type label
             * @param value the setting value label
             */
            private Settings(String type, String value) {
                this.type = type;
                this.value = value;
            }

            /**
             * Returns the setting type label.
             *
             * @return the type, or empty when the relay omitted the field
             */
            public Optional<String> type() {
                return Optional.ofNullable(type);
            }

            /**
             * Returns the setting value label.
             *
             * @return the value, or empty when the relay omitted the field
             */
            public Optional<String> value() {
                return Optional.ofNullable(value);
            }

            /**
             * Parses a {@link Settings} from the given JSON object.
             *
             * @apiNote
             * Used by {@link ViewerMetadata#of(JSONObject)} to hydrate one
             * entry of the {@code settings} array.
             *
             * @param obj the JSON object to parse
             * @return the parsed entry, or empty when {@code obj} is
             *         {@code null}
             */
            static Optional<Settings> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var type = obj.getString("type");
                var value = obj.getString("value");
                return Optional.of(new Settings(type, value));
            }

            /**
             * Parses a list of {@link Settings} entries from the given JSON
             * array.
             *
             * @apiNote
             * Used by {@link ViewerMetadata#of(JSONObject)} to hydrate the
             * {@code settings} array.
             *
             * @param arr the JSON array to parse
             * @return the parsed list, empty when {@code arr} is
             *         {@code null}
             */
            static List<Settings> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<Settings>(arr.size());
                for (var i = 0; i < arr.size(); i++) {
                    of(arr.getJSONObject(i)).ifPresent(result::add);
                }
                return result;
            }
        }

        /**
         * Parses a {@link ViewerMetadata} from the given JSON object.
         *
         * @apiNote
         * Used by {@link FetchNewsletterMexResponse#of(byte[])} to hydrate
         * the nested {@code viewer_metadata} entry.
         *
         * @param obj the JSON object to parse
         * @return the parsed entry, or empty when {@code obj} is
         *         {@code null}
         */
        static Optional<ViewerMetadata> of(JSONObject obj) {
            if (obj == null) {
                return Optional.empty();
            }

            var settings = Settings.ofArray(obj.getJSONArray("settings"));
            var role = obj.getString("role");
            var wamoSubStatus = obj.getString("wamo_sub_status");
            return Optional.of(new ViewerMetadata(settings, role, wamoSubStatus));
        }

        /**
         * Parses a list of {@link ViewerMetadata} entries from the given
         * JSON array.
         *
         * @apiNote
         * Provided for symmetry; the newsletter envelope does not carry a
         * {@code viewer_metadata} array.
         *
         * @param arr the JSON array to parse
         * @return the parsed list, empty when {@code arr} is {@code null}
         */
        static List<ViewerMetadata> ofArray(JSONArray arr) {
            if (arr == null) {
                return List.of();
            }

            var result = new ArrayList<ViewerMetadata>(arr.size());
            for (var i = 0; i < arr.size(); i++) {
                of(arr.getJSONObject(i)).ifPresent(result::add);
            }
            return result;
        }
    }

    /**
     * Parses the response from the raw UTF-8 JSON payload of the
     * {@code <result>} child.
     *
     * @apiNote
     * Reserved for the public {@link #of(Node)} overload.
     *
     * @implNote
     * This implementation guards every nested object lookup so a malformed
     * envelope produces {@link Optional#empty()} rather than a parser
     * exception.
     *
     * @param json the UTF-8 encoded JSON payload
     * @return the parsed response, or empty when the envelope lacks the
     *         expected {@code data.xwa2_newsletter} root
     */
    private static Optional<FetchNewsletterMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("xwa2_newsletter");
        if (root == null) {
            return Optional.empty();
        }

        var id = root.getString("id");
        var state = State.of(root.getJSONObject("state")).orElse(null);
        var threadMetadata = ThreadMetadata.of(root.getJSONObject("thread_metadata")).orElse(null);
        var viewerMetadata = ViewerMetadata.of(root.getJSONObject("viewer_metadata")).orElse(null);

        return Optional.of(new FetchNewsletterMexResponse(id, state, threadMetadata, viewerMetadata));
    }
}
