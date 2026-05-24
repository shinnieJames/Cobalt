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
 * Parses the MEX response of the fetch-recommended-newsletters query built
 * by {@link FetchRecommendedNewslettersMexRequest}.
 *
 * @apiNote
 * Surfaces the relay's {@code data.xwa2_newsletters_recommended} payload as
 * a {@link PageInfo} cursor block plus one {@link Result} per recommended
 * newsletter; each result carries the newsletter Jid, a {@link Result.State}
 * lifecycle marker, a {@link Result.ThreadMetadata} block (name, description,
 * preview picture, invite handle, verification badge, subscriber count) and
 * the optional {@link Result.StatusMetadata} counters returned when
 * {@code fetch_status_metadata} was set on the request. Mirrors the shape
 * WA Web's {@code WAWebNewsletterDirectorySearchQueryJob} forwards to the
 * directory search UI.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchRecommendedNewslettersJob")
public final class FetchRecommendedNewslettersMexResponse implements MexOperation.Response.Json {
    /**
     * The pagination cursor block echoed under
     * {@code xwa2_newsletters_recommended.page_info}.
     */
    private final PageInfo pageInfo;

    /**
     * The parsed list of recommended newsletters; ordered as returned by
     * the relay.
     */
    private final List<Result> result;

    /**
     * Constructs a response wrapping the parsed page-info cursor and the
     * recommended-newsletter list.
     *
     * @apiNote
     * Reserved for the static parser; external callers obtain instances via
     * {@link #of(Node)}.
     *
     * @param pageInfo the parsed pagination cursor
     * @param result   the parsed recommended-newsletter list
     */
    private FetchRecommendedNewslettersMexResponse(PageInfo pageInfo, List<Result> result) {
        this.pageInfo = pageInfo;
        this.result = result;
    }

    /**
     * Parses the MEX response carried by the given IQ result node.
     *
     * @apiNote
     * Drains the {@code <result>} child's byte content into the JSON parser;
     * the returned {@link Optional} is empty when the result child is
     * missing or when the JSON envelope omits the expected
     * {@code data.xwa2_newsletters_recommended} root.
     *
     * @param node the IQ result node received from the relay
     * @return the parsed response, or empty when the node does not carry a
     *         well-formed result payload
     */
    public static Optional<FetchRecommendedNewslettersMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(FetchRecommendedNewslettersMexResponse::of);
    }

    /**
     * Returns the pagination cursor block.
     *
     * @apiNote
     * Empty when the GraphQL envelope omits {@code page_info}; otherwise
     * carries the {@code hasNextPage}, {@code hasPreviousPage},
     * {@code startCursor} and {@code endCursor} markers callers feed back to
     * follow-up queries.
     *
     * @return the parsed {@link PageInfo}, or empty when omitted
     */
    public Optional<PageInfo> pageInfo() {
        return Optional.ofNullable(pageInfo);
    }

    /**
     * Returns the parsed list of recommended newsletters.
     *
     * @apiNote
     * The list is ordered as returned by the relay and may be empty when no
     * recommendations are available for the requested scope.
     *
     * @return the parsed list, empty when the {@code result} array was
     *         missing or empty
     */
    public List<Result> result() {
        return result;
    }

    /**
     * The {@code page_info} cursor block returned by the relay.
     *
     * @apiNote
     * Mirrors the Relay-style pagination cursor used across MEX queries:
     * {@code hasNextPage}/{@code hasPreviousPage} flags plus opaque
     * {@code startCursor}/{@code endCursor} strings the caller passes back
     * verbatim on the next page request.
     */
    public static final class PageInfo {
        /**
         * The {@code hasNextPage} flag.
         */
        private final Boolean hasNextPage;

        /**
         * The {@code hasPreviousPage} flag.
         */
        private final Boolean hasPreviousPage;

        /**
         * The opaque {@code startCursor} string for backward pagination.
         */
        private final String startCursor;

        /**
         * The opaque {@code endCursor} string for forward pagination.
         */
        private final String endCursor;

        /**
         * Constructs a parsed {@code page_info} value.
         *
         * @apiNote
         * Reserved for {@link #of(JSONObject)}.
         *
         * @param hasNextPage     the {@code hasNextPage} flag
         * @param hasPreviousPage the {@code hasPreviousPage} flag
         * @param startCursor     the opaque backward-pagination cursor
         * @param endCursor       the opaque forward-pagination cursor
         */
        private PageInfo(Boolean hasNextPage, Boolean hasPreviousPage, String startCursor, String endCursor) {
            this.hasNextPage = hasNextPage;
            this.hasPreviousPage = hasPreviousPage;
            this.startCursor = startCursor;
            this.endCursor = endCursor;
        }

        /**
         * Returns whether more pages follow the current one.
         *
         * @apiNote
         * Treats a missing or {@code null} value as {@code false}; callers
         * use this flag to decide whether to request the next page.
         *
         * @return {@code true} when more pages follow, {@code false}
         *         otherwise
         */
        public boolean hasNextPage() {
            return hasNextPage != null && hasNextPage;
        }

        /**
         * Returns whether more pages precede the current one.
         *
         * @apiNote
         * Treats a missing or {@code null} value as {@code false}.
         *
         * @return {@code true} when more pages precede the current one,
         *         {@code false} otherwise
         */
        public boolean hasPreviousPage() {
            return hasPreviousPage != null && hasPreviousPage;
        }

        /**
         * Returns the opaque cursor of the first entry on this page.
         *
         * @apiNote
         * Empty when the GraphQL envelope omits {@code startCursor}; pass
         * the value verbatim to the relay when paginating backward.
         *
         * @return the {@code startCursor} value, or empty when omitted
         */
        public Optional<String> startCursor() {
            return Optional.ofNullable(startCursor);
        }

        /**
         * Returns the opaque cursor of the last entry on this page.
         *
         * @apiNote
         * Empty when the GraphQL envelope omits {@code endCursor}; pass the
         * value verbatim to the relay when paginating forward.
         *
         * @return the {@code endCursor} value, or empty when omitted
         */
        public Optional<String> endCursor() {
            return Optional.ofNullable(endCursor);
        }

        /**
         * Parses a {@code page_info} fragment from the given JSON object.
         *
         * @apiNote
         * Reserved for the parent parser; returns {@link Optional#empty()}
         * when {@code obj} is {@code null} so an absent fragment cleanly
         * back-propagates to {@link #pageInfo()}.
         *
         * @param obj the JSON object to parse
         * @return the parsed value, or empty when {@code obj} is
         *         {@code null}
         */
        static Optional<PageInfo> of(JSONObject obj) {
            if (obj == null) {
                return Optional.empty();
            }

            var hasNextPage = obj.getBoolean("hasNextPage");
            var hasPreviousPage = obj.getBoolean("hasPreviousPage");
            var startCursor = obj.getString("startCursor");
            var endCursor = obj.getString("endCursor");
            return Optional.of(new PageInfo(hasNextPage, hasPreviousPage, startCursor, endCursor));
        }

        /**
         * Parses every {@code page_info} fragment in the given JSON array.
         *
         * @apiNote
         * Reserved for callers that handle batched page-info arrays;
         * returns {@link List#of()} when {@code arr} is {@code null}.
         *
         * @param arr the JSON array to parse
         * @return the list of parsed values, empty when {@code arr} is
         *         {@code null}
         */
        static List<PageInfo> ofArray(JSONArray arr) {
            if (arr == null) {
                return List.of();
            }

            var result = new ArrayList<PageInfo>(arr.size());
            for (var i = 0; i < arr.size(); i++) {
                of(arr.getJSONObject(i)).ifPresent(result::add);
            }
            return result;
        }
    }

    /**
     * A single recommended-newsletter entry returned under
     * {@code xwa2_newsletters_recommended.result}.
     *
     * @apiNote
     * Carries the newsletter Jid, a lifecycle {@link State} marker, a
     * {@link ThreadMetadata} block with display fields, and an optional
     * {@link StatusMetadata} block populated when the request set
     * {@code fetch_status_metadata}.
     */
    public static final class Result {
        /**
         * The newsletter Jid string echoed under {@code id}.
         */
        private final String id;

        /**
         * The lifecycle state of the newsletter, echoed under {@code state}.
         */
        private final State state;

        /**
         * The display-metadata block echoed under {@code thread_metadata}.
         */
        private final ThreadMetadata threadMetadata;

        /**
         * The optional per-newsletter status counters echoed under
         * {@code status_metadata}.
         */
        private final StatusMetadata statusMetadata;

        /**
         * Constructs a parsed {@code result} entry.
         *
         * @apiNote
         * Reserved for {@link #of(JSONObject)}.
         *
         * @param id             the newsletter Jid string
         * @param state          the lifecycle state marker
         * @param threadMetadata the display-metadata block
         * @param statusMetadata the optional per-newsletter status counters
         */
        private Result(String id, State state, ThreadMetadata threadMetadata, StatusMetadata statusMetadata) {
            this.id = id;
            this.state = state;
            this.threadMetadata = threadMetadata;
            this.statusMetadata = statusMetadata;
        }

        /**
         * Returns the newsletter Jid string.
         *
         * @apiNote
         * Empty when the GraphQL envelope omits {@code id}.
         *
         * @return the {@code id} value, or empty when omitted
         */
        public Optional<String> id() {
            return Optional.ofNullable(id);
        }

        /**
         * Returns the lifecycle state of the newsletter.
         *
         * @apiNote
         * Empty when the GraphQL envelope omits {@code state}; otherwise
         * carries the relay-defined state-type marker
         * (for example {@code "ACTIVE"}, {@code "DELETED"}).
         *
         * @return the parsed {@link State}, or empty when omitted
         */
        public Optional<State> state() {
            return Optional.ofNullable(state);
        }

        /**
         * Returns the display-metadata block.
         *
         * @apiNote
         * Empty when the GraphQL envelope omits {@code thread_metadata}.
         *
         * @return the parsed {@link ThreadMetadata}, or empty when omitted
         */
        public Optional<ThreadMetadata> threadMetadata() {
            return Optional.ofNullable(threadMetadata);
        }

        /**
         * Returns the per-newsletter status counters.
         *
         * @apiNote
         * Populated only when the request set
         * {@code fetch_status_metadata} and the relay's newsletter-status
         * surface is enabled for the local user; otherwise empty.
         *
         * @return the parsed {@link StatusMetadata}, or empty when omitted
         */
        public Optional<StatusMetadata> statusMetadata() {
            return Optional.ofNullable(statusMetadata);
        }

        /**
         * The lifecycle {@code state} marker on a recommended-newsletter
         * entry.
         *
         * @apiNote
         * Carries the relay-defined {@code type} string (for example
         * {@code "ACTIVE"}, {@code "DELETED"}, {@code "SUSPENDED"}) that
         * gates whether the directory UI should surface the entry.
         */
        public static final class State {
            /**
             * The relay-defined state-type string.
             */
            private final String type;

            /**
             * Constructs a parsed {@code state} value.
             *
             * @apiNote
             * Reserved for {@link #of(JSONObject)}.
             *
             * @param type the state-type string
             */
            private State(String type) {
                this.type = type;
            }

            /**
             * Returns the state-type string.
             *
             * @apiNote
             * Empty when the GraphQL envelope omits {@code type}.
             *
             * @return the {@code type} value, or empty when omitted
             */
            public Optional<String> type() {
                return Optional.ofNullable(type);
            }

            /**
             * Parses a {@code state} fragment from the given JSON object.
             *
             * @apiNote
             * Reserved for the parent parser; returns
             * {@link Optional#empty()} when {@code obj} is {@code null}.
             *
             * @param obj the JSON object to parse
             * @return the parsed value, or empty when {@code obj} is
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
             * Parses every {@code state} fragment in the given JSON array.
             *
             * @apiNote
             * Reserved for callers that handle batched state arrays;
             * returns {@link List#of()} when {@code arr} is {@code null}.
             *
             * @param arr the JSON array to parse
             * @return the list of parsed values, empty when {@code arr} is
             *         {@code null}
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
         * The {@code thread_metadata} block on a recommended-newsletter
         * entry.
         *
         * @apiNote
         * Carries the display surface for the directory UI: creation
         * timestamp, localised {@link Name} and {@link Description} blocks,
         * a thumbnail {@link Preview}, the canonical invite URL, the
         * vanity handle, the verification badge string, and the current
         * subscriber count.
         */
        public static final class ThreadMetadata {
            /**
             * The newsletter-creation timestamp in epoch seconds.
             */
            private final Long creationTime;

            /**
             * The localised display-name block.
             */
            private final Name name;

            /**
             * The localised description block.
             */
            private final Description description;

            /**
             * The thumbnail-image block.
             */
            private final Preview preview;

            /**
             * The canonical invite URL string.
             */
            private final String invite;

            /**
             * The vanity-handle string.
             */
            private final String handle;

            /**
             * The verification-badge string echoed under
             * {@code verification}.
             */
            private final String verification;

            /**
             * The current subscriber count.
             */
            private final Long subscribersCount;

            /**
             * Constructs a parsed {@code thread_metadata} value.
             *
             * @apiNote
             * Reserved for {@link #of(JSONObject)}.
             *
             * @param creationTime     the newsletter-creation epoch seconds
             * @param name             the localised display-name block
             * @param description      the localised description block
             * @param preview          the thumbnail-image block
             * @param invite           the canonical invite URL
             * @param handle           the vanity-handle string
             * @param verification     the verification-badge string
             * @param subscribersCount the current subscriber count
             */
            private ThreadMetadata(Long creationTime, Name name, Description description, Preview preview, String invite, String handle, String verification, Long subscribersCount) {
                this.creationTime = creationTime;
                this.name = name;
                this.description = description;
                this.preview = preview;
                this.invite = invite;
                this.handle = handle;
                this.verification = verification;
                this.subscribersCount = subscribersCount;
            }

            /**
             * Returns the moment the newsletter was created.
             *
             * @apiNote
             * Empty when the GraphQL envelope omits {@code creation_time};
             * the underlying value is the wire-level epoch-second integer
             * remapped to an {@link Instant}.
             *
             * @return the {@code creation_time} as an {@link Instant}, or
             *         empty when omitted
             */
            public Optional<Instant> creationTime() {
                return Optional.ofNullable(creationTime).map(Instant::ofEpochSecond);
            }

            /**
             * Returns the localised display-name block.
             *
             * @apiNote
             * Empty when the GraphQL envelope omits {@code name}.
             *
             * @return the parsed {@link Name}, or empty when omitted
             */
            public Optional<Name> name() {
                return Optional.ofNullable(name);
            }

            /**
             * Returns the localised description block.
             *
             * @apiNote
             * Empty when the GraphQL envelope omits {@code description}.
             *
             * @return the parsed {@link Description}, or empty when omitted
             */
            public Optional<Description> description() {
                return Optional.ofNullable(description);
            }

            /**
             * Returns the thumbnail-image block.
             *
             * @apiNote
             * Empty when the GraphQL envelope omits {@code preview}.
             *
             * @return the parsed {@link Preview}, or empty when omitted
             */
            public Optional<Preview> preview() {
                return Optional.ofNullable(preview);
            }

            /**
             * Returns the canonical invite URL.
             *
             * @apiNote
             * Empty when the GraphQL envelope omits {@code invite}.
             *
             * @return the {@code invite} value, or empty when omitted
             */
            public Optional<String> invite() {
                return Optional.ofNullable(invite);
            }

            /**
             * Returns the vanity-handle string.
             *
             * @apiNote
             * Empty when the GraphQL envelope omits {@code handle}.
             *
             * @return the {@code handle} value, or empty when omitted
             */
            public Optional<String> handle() {
                return Optional.ofNullable(handle);
            }

            /**
             * Returns the verification-badge string.
             *
             * @apiNote
             * Empty when the GraphQL envelope omits {@code verification};
             * otherwise carries the relay-defined badge string (for
             * example {@code "VERIFIED"}, {@code "UNVERIFIED"}).
             *
             * @return the {@code verification} value, or empty when omitted
             */
            public Optional<String> verification() {
                return Optional.ofNullable(verification);
            }

            /**
             * Returns the current subscriber count.
             *
             * @apiNote
             * Empty when the GraphQL envelope omits
             * {@code subscribers_count}; returned as an
             * {@link OptionalLong} to avoid the boxing penalty of
             * {@code Optional<Long>}.
             *
             * @return the {@code subscribers_count} value, or empty when
             *         omitted
             */
            public OptionalLong subscribersCount() {
                return subscribersCount != null ? OptionalLong.of(subscribersCount) : OptionalLong.empty();
            }

            /**
             * The localised {@code name} block on a recommended-newsletter
             * entry.
             *
             * @apiNote
             * Carries the localisation identifier ({@code id}), the
             * display text, and the last-update timestamp the relay uses
             * to drive cache invalidation.
             */
            public static final class Name {
                /**
                 * The localisation identifier.
                 */
                private final String id;

                /**
                 * The display text.
                 */
                private final String text;

                /**
                 * The last-update timestamp in epoch seconds.
                 */
                private final Long updateTime;

                /**
                 * Constructs a parsed {@code name} value.
                 *
                 * @apiNote
                 * Reserved for {@link #of(JSONObject)}.
                 *
                 * @param id         the localisation identifier
                 * @param text       the display text
                 * @param updateTime the last-update epoch seconds
                 */
                private Name(String id, String text, Long updateTime) {
                    this.id = id;
                    this.text = text;
                    this.updateTime = updateTime;
                }

                /**
                 * Returns the localisation identifier.
                 *
                 * @apiNote
                 * Empty when the GraphQL envelope omits {@code id}.
                 *
                 * @return the {@code id} value, or empty when omitted
                 */
                public Optional<String> id() {
                    return Optional.ofNullable(id);
                }

                /**
                 * Returns the display text.
                 *
                 * @apiNote
                 * Empty when the GraphQL envelope omits {@code text}.
                 *
                 * @return the {@code text} value, or empty when omitted
                 */
                public Optional<String> text() {
                    return Optional.ofNullable(text);
                }

                /**
                 * Returns the last-update timestamp.
                 *
                 * @apiNote
                 * Empty when the GraphQL envelope omits {@code update_time};
                 * the underlying value is the wire-level epoch-second
                 * integer remapped to an {@link Instant}.
                 *
                 * @return the {@code update_time} as an {@link Instant}, or
                 *         empty when omitted
                 */
                public Optional<Instant> updateTime() {
                    return Optional.ofNullable(updateTime).map(Instant::ofEpochSecond);
                }

                /**
                 * Parses a {@code name} fragment from the given JSON
                 * object.
                 *
                 * @apiNote
                 * Reserved for the parent parser; returns
                 * {@link Optional#empty()} when {@code obj} is
                 * {@code null}.
                 *
                 * @param obj the JSON object to parse
                 * @return the parsed value, or empty when {@code obj} is
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
                 * Parses every {@code name} fragment in the given JSON
                 * array.
                 *
                 * @apiNote
                 * Reserved for callers that handle batched name arrays;
                 * returns {@link List#of()} when {@code arr} is
                 * {@code null}.
                 *
                 * @param arr the JSON array to parse
                 * @return the list of parsed values, empty when {@code arr}
                 *         is {@code null}
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
             * The localised {@code description} block on a
             * recommended-newsletter entry.
             *
             * @apiNote
             * Mirrors {@link Name}'s shape: a localisation identifier, the
             * display text, and the last-update timestamp that drives
             * cache invalidation.
             */
            public static final class Description {
                /**
                 * The localisation identifier.
                 */
                private final String id;

                /**
                 * The display text.
                 */
                private final String text;

                /**
                 * The last-update timestamp in epoch seconds.
                 */
                private final Long updateTime;

                /**
                 * Constructs a parsed {@code description} value.
                 *
                 * @apiNote
                 * Reserved for {@link #of(JSONObject)}.
                 *
                 * @param id         the localisation identifier
                 * @param text       the display text
                 * @param updateTime the last-update epoch seconds
                 */
                private Description(String id, String text, Long updateTime) {
                    this.id = id;
                    this.text = text;
                    this.updateTime = updateTime;
                }

                /**
                 * Returns the localisation identifier.
                 *
                 * @apiNote
                 * Empty when the GraphQL envelope omits {@code id}.
                 *
                 * @return the {@code id} value, or empty when omitted
                 */
                public Optional<String> id() {
                    return Optional.ofNullable(id);
                }

                /**
                 * Returns the display text.
                 *
                 * @apiNote
                 * Empty when the GraphQL envelope omits {@code text}.
                 *
                 * @return the {@code text} value, or empty when omitted
                 */
                public Optional<String> text() {
                    return Optional.ofNullable(text);
                }

                /**
                 * Returns the last-update timestamp.
                 *
                 * @apiNote
                 * Empty when the GraphQL envelope omits {@code update_time};
                 * the underlying value is the wire-level epoch-second
                 * integer remapped to an {@link Instant}.
                 *
                 * @return the {@code update_time} as an {@link Instant}, or
                 *         empty when omitted
                 */
                public Optional<Instant> updateTime() {
                    return Optional.ofNullable(updateTime).map(Instant::ofEpochSecond);
                }

                /**
                 * Parses a {@code description} fragment from the given JSON
                 * object.
                 *
                 * @apiNote
                 * Reserved for the parent parser; returns
                 * {@link Optional#empty()} when {@code obj} is
                 * {@code null}.
                 *
                 * @param obj the JSON object to parse
                 * @return the parsed value, or empty when {@code obj} is
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
                 * Parses every {@code description} fragment in the given
                 * JSON array.
                 *
                 * @apiNote
                 * Reserved for callers that handle batched description
                 * arrays; returns {@link List#of()} when {@code arr} is
                 * {@code null}.
                 *
                 * @param arr the JSON array to parse
                 * @return the list of parsed values, empty when {@code arr}
                 *         is {@code null}
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
             * The {@code preview} thumbnail block on a
             * recommended-newsletter entry.
             *
             * @apiNote
             * Carries the relay-issued media handle ({@code id}), the
             * media type, and the direct download path the client uses to
             * fetch the thumbnail bytes.
             */
            public static final class Preview {
                /**
                 * The media handle.
                 */
                private final String id;

                /**
                 * The media type marker.
                 */
                private final String type;

                /**
                 * The direct download path.
                 */
                private final String directPath;

                /**
                 * Constructs a parsed {@code preview} value.
                 *
                 * @apiNote
                 * Reserved for {@link #of(JSONObject)}.
                 *
                 * @param id         the media handle
                 * @param type       the media type marker
                 * @param directPath the direct download path
                 */
                private Preview(String id, String type, String directPath) {
                    this.id = id;
                    this.type = type;
                    this.directPath = directPath;
                }

                /**
                 * Returns the media handle.
                 *
                 * @apiNote
                 * Empty when the GraphQL envelope omits {@code id}.
                 *
                 * @return the {@code id} value, or empty when omitted
                 */
                public Optional<String> id() {
                    return Optional.ofNullable(id);
                }

                /**
                 * Returns the media-type marker.
                 *
                 * @apiNote
                 * Empty when the GraphQL envelope omits {@code type};
                 * otherwise carries the relay-defined media-type string.
                 *
                 * @return the {@code type} value, or empty when omitted
                 */
                public Optional<String> type() {
                    return Optional.ofNullable(type);
                }

                /**
                 * Returns the direct download path.
                 *
                 * @apiNote
                 * Empty when the GraphQL envelope omits {@code direct_path};
                 * otherwise carries the server-relative path the client
                 * uses to fetch the thumbnail bytes.
                 *
                 * @return the {@code direct_path} value, or empty when
                 *         omitted
                 */
                public Optional<String> directPath() {
                    return Optional.ofNullable(directPath);
                }

                /**
                 * Parses a {@code preview} fragment from the given JSON
                 * object.
                 *
                 * @apiNote
                 * Reserved for the parent parser; returns
                 * {@link Optional#empty()} when {@code obj} is
                 * {@code null}.
                 *
                 * @param obj the JSON object to parse
                 * @return the parsed value, or empty when {@code obj} is
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
                 * Parses every {@code preview} fragment in the given JSON
                 * array.
                 *
                 * @apiNote
                 * Reserved for callers that handle batched preview arrays;
                 * returns {@link List#of()} when {@code arr} is
                 * {@code null}.
                 *
                 * @param arr the JSON array to parse
                 * @return the list of parsed values, empty when {@code arr}
                 *         is {@code null}
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
             * Parses a {@code thread_metadata} fragment from the given
             * JSON object.
             *
             * @apiNote
             * Reserved for the parent parser; returns
             * {@link Optional#empty()} when {@code obj} is {@code null}.
             *
             * @param obj the JSON object to parse
             * @return the parsed value, or empty when {@code obj} is
             *         {@code null}
             */
            static Optional<ThreadMetadata> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var creationTime = obj.getLong("creation_time");
                var name = Name.of(obj.getJSONObject("name")).orElse(null);
                var description = Description.of(obj.getJSONObject("description")).orElse(null);
                var preview = Preview.of(obj.getJSONObject("preview")).orElse(null);
                var invite = obj.getString("invite");
                var handle = obj.getString("handle");
                var verification = obj.getString("verification");
                var subscribersCount = obj.getLong("subscribers_count");
                return Optional.of(new ThreadMetadata(creationTime, name, description, preview, invite, handle, verification, subscribersCount));
            }

            /**
             * Parses every {@code thread_metadata} fragment in the given
             * JSON array.
             *
             * @apiNote
             * Reserved for callers that handle batched thread-metadata
             * arrays; returns {@link List#of()} when {@code arr} is
             * {@code null}.
             *
             * @param arr the JSON array to parse
             * @return the list of parsed values, empty when {@code arr} is
             *         {@code null}
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
         * The {@code status_metadata} block carrying per-newsletter
         * status-message counters.
         *
         * @apiNote
         * Populated only when the request set
         * {@code fetch_status_metadata} and the relay's newsletter-status
         * receiver is enabled for the local user; carries the most-recent
         * status server-id and its send timestamp so the UI can render a
         * "latest status" cue beside the newsletter.
         */
        public static final class StatusMetadata {
            /**
             * The most-recent status-message server identifier.
             */
            private final String lastStatusServerId;

            /**
             * The most-recent status-message send timestamp in epoch
             * seconds.
             */
            private final Long lastStatusSentTime;

            /**
             * Constructs a parsed {@code status_metadata} value.
             *
             * @apiNote
             * Reserved for {@link #of(JSONObject)}.
             *
             * @param lastStatusServerId the most-recent status server-id
             * @param lastStatusSentTime the most-recent status send epoch
             *                           seconds
             */
            private StatusMetadata(String lastStatusServerId, Long lastStatusSentTime) {
                this.lastStatusServerId = lastStatusServerId;
                this.lastStatusSentTime = lastStatusSentTime;
            }

            /**
             * Returns the most-recent status-message server identifier.
             *
             * @apiNote
             * Empty when the GraphQL envelope omits
             * {@code last_status_server_id}.
             *
             * @return the {@code last_status_server_id} value, or empty
             *         when omitted
             */
            public Optional<String> lastStatusServerId() {
                return Optional.ofNullable(lastStatusServerId);
            }

            /**
             * Returns the moment the most-recent status was sent.
             *
             * @apiNote
             * Empty when the GraphQL envelope omits
             * {@code last_status_sent_time}; the underlying value is the
             * wire-level epoch-second integer remapped to an
             * {@link Instant}.
             *
             * @return the {@code last_status_sent_time} as an
             *         {@link Instant}, or empty when omitted
             */
            public Optional<Instant> lastStatusSentTime() {
                return Optional.ofNullable(lastStatusSentTime).map(Instant::ofEpochSecond);
            }

            /**
             * Parses a {@code status_metadata} fragment from the given
             * JSON object.
             *
             * @apiNote
             * Reserved for the parent parser; returns
             * {@link Optional#empty()} when {@code obj} is {@code null}.
             *
             * @param obj the JSON object to parse
             * @return the parsed value, or empty when {@code obj} is
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
             * Parses every {@code status_metadata} fragment in the given
             * JSON array.
             *
             * @apiNote
             * Reserved for callers that handle batched status-metadata
             * arrays; returns {@link List#of()} when {@code arr} is
             * {@code null}.
             *
             * @param arr the JSON array to parse
             * @return the list of parsed values, empty when {@code arr} is
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
         * Parses a single {@code result} entry from the given JSON object.
         *
         * @apiNote
         * Reserved for {@link #ofArray(JSONArray)}; returns
         * {@link Optional#empty()} when {@code obj} is {@code null}.
         *
         * @param obj the JSON object to parse
         * @return the parsed value, or empty when {@code obj} is
         *         {@code null}
         */
        static Optional<Result> of(JSONObject obj) {
            if (obj == null) {
                return Optional.empty();
            }

            var id = obj.getString("id");
            var state = State.of(obj.getJSONObject("state")).orElse(null);
            var threadMetadata = ThreadMetadata.of(obj.getJSONObject("thread_metadata")).orElse(null);
            var statusMetadata = StatusMetadata.of(obj.getJSONObject("status_metadata")).orElse(null);
            return Optional.of(new Result(id, state, threadMetadata, statusMetadata));
        }

        /**
         * Parses every {@code result} entry in the given JSON array.
         *
         * @apiNote
         * Materialises the {@code xwa2_newsletters_recommended.result}
         * array into a fresh {@link List}; returns {@link List#of()} when
         * {@code arr} is {@code null} so an absent or empty server response
         * surfaces as an empty list.
         *
         * @param arr the JSON array to parse
         * @return the list of parsed values, empty when {@code arr} is
         *         {@code null}
         */
        static List<Result> ofArray(JSONArray arr) {
            if (arr == null) {
                return List.of();
            }

            var result = new ArrayList<Result>(arr.size());
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
     * Reserved for the public {@link #of(Node)} overload; callers should not
     * hold raw JSON bytes.
     *
     * @implNote
     * This implementation guards every nested object lookup so a malformed
     * envelope produces {@link Optional#empty()} rather than a parser
     * exception, mirroring the defensive null-checks in WA Web's caller.
     *
     * @param json the UTF-8 encoded JSON payload
     * @return the parsed response, or empty when the envelope lacks the
     *         expected {@code data.xwa2_newsletters_recommended} root
     */
    private static Optional<FetchRecommendedNewslettersMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("xwa2_newsletters_recommended");
        if (root == null) {
            return Optional.empty();
        }

        var pageInfo = PageInfo.of(root.getJSONObject("page_info")).orElse(null);
        var result = Result.ofArray(root.getJSONArray("result"));

        return Optional.of(new FetchRecommendedNewslettersMexResponse(pageInfo, result));
    }
}
