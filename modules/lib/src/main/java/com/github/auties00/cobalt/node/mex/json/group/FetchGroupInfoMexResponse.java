package com.github.auties00.cobalt.node.mex.json.group;

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
import java.util.OptionalLong;

/**
 * Inbound parsed response of the {@link FetchGroupInfoMexRequest} query,
 * exposing the full {@code xwa2_group_query_by_id} envelope returned by
 * the relay.
 *
 * @apiNote Drives WA Web's group-info panel, chat header and chat-info
 * sidebar; consumed by Cobalt callers mirroring
 * {@code WAWebGroupQueryGroupJob.queryGroupJob}, which feeds the result
 * into the in-memory group descriptor. The four-way inline-fragment
 * typename (regular group, community, default subgroup, subgroup) is not
 * surfaced here; WA Web resolves it through
 * {@code WAWebMexGetTypename.getTypename(O)} and Cobalt callers may infer
 * it from the populated property set.
 *
 * @implNote This implementation keeps the nested class hierarchy aligned
 * with the GraphQL response shape and exposes every scalar as an
 * {@link Optional}, leaving cross-property derivation (such as the
 * {@code suspended}/{@code terminated} flags WA Web folds out of the
 * {@code state} scalar, or the {@code typename} dispatch) to the caller.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchGroupInfoJob")
public final class FetchGroupInfoMexResponse implements MexOperation.Response.Json {
    /**
     * The group identifier scalar projected from
     * {@code xwa2_group_query_by_id.id}.
     */
    private final String id;

    /**
     * The group creation timestamp (seconds since epoch) projected from
     * {@code xwa2_group_query_by_id.creation_time}.
     */
    private final Long creationTime;

    /**
     * The parsed {@code creator} sub-object exposing the group founder's
     * identifiers.
     */
    private final Creator creator;

    /**
     * The group lifecycle state scalar projected from
     * {@code xwa2_group_query_by_id.state} ({@code "ACTIVE"},
     * {@code "SUSPENDED"} or {@code "NON_EXISTENT"}).
     */
    private final String state;

    /**
     * The parsed {@code subject} sub-object exposing the current subject
     * text and last-edit author.
     */
    private final Subject subject;

    /**
     * The parsed {@code description} sub-object exposing the current
     * description text and last-edit author.
     */
    private final Description description;

    /**
     * The parsed {@code participants} sub-object carrying the edge list
     * and the {@code participants_phash_match} flag.
     */
    private final Participants participants;

    /**
     * The total participant count scalar projected from
     * {@code xwa2_group_query_by_id.total_participants_count}.
     */
    private final Long totalParticipantsCount;

    /**
     * The flag scalar projected from
     * {@code xwa2_group_query_by_id.missing_participant_identification},
     * indicating that the relay could not identify every participant.
     */
    private final String missingParticipantIdentification;

    /**
     * The parsed {@code properties} sub-object aggregating the
     * configurable group properties.
     */
    private final Properties properties;

    /**
     * The membership-approval request flag scalar projected from
     * {@code xwa2_group_query_by_id.membership_approval_request}.
     */
    private final String membershipApprovalRequest;

    /**
     * Constructs a new response wrapping the parsed scalar and nested
     * fields of the {@code xwa2_group_query_by_id} envelope.
     *
     * @apiNote Package-private; instances are produced by the
     * {@link #of(Node)} parser.
     *
     * @param id                                 the group identifier scalar
     * @param creationTime                       the group creation timestamp in seconds since epoch
     * @param creator                            the parsed {@code creator} sub-object
     * @param state                              the group {@code state} scalar
     * @param subject                            the parsed {@code subject} sub-object
     * @param description                        the parsed {@code description} sub-object
     * @param participants                       the parsed {@code participants} sub-object
     * @param totalParticipantsCount             the {@code total_participants_count} scalar
     * @param missingParticipantIdentification   the {@code missing_participant_identification} scalar
     * @param properties                         the parsed {@code properties} sub-object
     * @param membershipApprovalRequest          the {@code membership_approval_request} scalar
     */
    private FetchGroupInfoMexResponse(String id, Long creationTime, Creator creator, String state, Subject subject, Description description, Participants participants, Long totalParticipantsCount, String missingParticipantIdentification, Properties properties, String membershipApprovalRequest) {
        this.id = id;
        this.creationTime = creationTime;
        this.creator = creator;
        this.state = state;
        this.subject = subject;
        this.description = description;
        this.participants = participants;
        this.totalParticipantsCount = totalParticipantsCount;
        this.missingParticipantIdentification = missingParticipantIdentification;
        this.properties = properties;
        this.membershipApprovalRequest = membershipApprovalRequest;
    }

    /**
     * Parses the MEX response carried by an inbound IQ stanza.
     *
     * @apiNote Entry point for receivers handling the IQ reply of
     * {@link FetchGroupInfoMexRequest}. The returned value is
     * {@link Optional#empty()} when the reply lacks a {@code <result>}
     * child or its JSON body cannot be parsed into the expected envelope.
     *
     * @param node the inbound IQ stanza carrying the {@code <result>} child
     * @return the parsed response, or {@link Optional#empty()} if the
     *         expected JSON shape is absent
     */
    @WhatsAppWebExport(moduleName = "WAWebMexFetchGroupInfoJob", exports = "mexGetGroupInfo",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<FetchGroupInfoMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(FetchGroupInfoMexResponse::of);
    }

    /**
     * Returns the group identifier scalar.
     *
     * @apiNote Mirrors the {@code id} variable echoed by the relay; useful
     * when correlating the reply against a batched dispatch.
     *
     * @return an {@link Optional} containing the value, or empty if absent
     */
    public Optional<String> id() {
        return Optional.ofNullable(id);
    }

    /**
     * Returns the group creation timestamp.
     *
     * @apiNote Adapted from the relay's seconds-since-epoch {@code Long}
     * into a JDK {@link Instant}; matches WA Web's
     * {@code creation: Number(q)} projection on the parsed group info.
     *
     * @return an {@link Optional} containing the value as an {@link Instant}, or empty if absent
     */
    public Optional<Instant> creationTime() {
        return Optional.ofNullable(creationTime).map(Instant::ofEpochSecond);
    }

    /**
     * Returns the parsed {@code creator} sub-object exposing the group
     * founder's identifiers.
     *
     * @apiNote Carries the {@code id}, {@code lid}, {@code pn} and
     * {@code username_info} fields of the founder; WA Web folds these
     * into the {@code owner}, {@code creatorPn} and {@code creatorUsername}
     * group-info fields.
     *
     * @return an {@link Optional} containing the value, or empty if absent
     */
    public Optional<Creator> creator() {
        return Optional.ofNullable(creator);
    }

    /**
     * Returns the group lifecycle state scalar.
     *
     * @apiNote One of {@code "ACTIVE"}, {@code "SUSPENDED"} or
     * {@code "NON_EXISTENT"}; WA Web folds the latter two into the
     * {@code suspended} and {@code terminated} boolean fields on the
     * group-info object.
     *
     * @return an {@link Optional} containing the value, or empty if absent
     */
    public Optional<String> state() {
        return Optional.ofNullable(state);
    }

    /**
     * Returns the parsed {@code subject} sub-object.
     *
     * @apiNote Carries the current subject text, its last-edit timestamp
     * and the editor's identifiers.
     *
     * @return an {@link Optional} containing the value, or empty if absent
     */
    public Optional<Subject> subject() {
        return Optional.ofNullable(subject);
    }

    /**
     * Returns the parsed {@code description} sub-object.
     *
     * @apiNote Carries the current description text, its identifier,
     * last-edit timestamp and the editor's identifiers.
     *
     * @return an {@link Optional} containing the value, or empty if absent
     */
    public Optional<Description> description() {
        return Optional.ofNullable(description);
    }

    /**
     * Returns the parsed {@code participants} sub-object.
     *
     * @apiNote Carries the edge list of human participants (bots are
     * excluded; use {@link FetchGroupInfoIncludBotsMexResponse} to include
     * them) together with the {@code participants_phash_match} flag.
     *
     * @return an {@link Optional} containing the value, or empty if absent
     */
    public Optional<Participants> participants() {
        return Optional.ofNullable(participants);
    }

    /**
     * Returns the total participant count scalar.
     *
     * @apiNote Reported by the relay even when the edge list is omitted
     * (e.g. when {@code participants_phash_match} is {@code true}),
     * allowing UI surfaces to render the group size without the full
     * edge list.
     *
     * @return an {@link OptionalLong} containing the value, or empty if absent
     */
    public OptionalLong totalParticipantsCount() {
        return totalParticipantsCount != null ? OptionalLong.of(totalParticipantsCount) : OptionalLong.empty();
    }

    /**
     * Returns the {@code missing_participant_identification} scalar.
     *
     * @apiNote Surfaces the case where the relay could not identify every
     * participant in the edge list; WA Web folds the boolean form into
     * the {@code hasIncompleteParticipantInformation} group-info field.
     *
     * @return an {@link Optional} containing the value, or empty if absent
     */
    public Optional<String> missingParticipantIdentification() {
        return Optional.ofNullable(missingParticipantIdentification);
    }

    /**
     * Returns the parsed {@code properties} sub-object aggregating the
     * configurable group properties.
     *
     * @apiNote Includes membership-approval flags, member-add mode,
     * announcement-only mode, ephemeral timer, parent-community link,
     * LID migration state, growth lock, hidden-group flag and more.
     *
     * @return an {@link Optional} containing the value, or empty if absent
     */
    public Optional<Properties> properties() {
        return Optional.ofNullable(properties);
    }

    /**
     * Returns the {@code membership_approval_request} scalar.
     *
     * @apiNote Indicates whether the current user has a pending join
     * request awaiting admin approval; WA Web compares the string against
     * {@code true} in
     * {@code membershipApprovalRequest: O.membership_approval_request === !0}.
     *
     * @return an {@link Optional} containing the value, or empty if absent
     */
    public Optional<String> membershipApprovalRequest() {
        return Optional.ofNullable(membershipApprovalRequest);
    }

    /**
     * Parsed projection of the top-level {@code creator} sub-object.
     *
     * @apiNote Surfaces the group founder's identifiers in the same
     * shape as the WA Web {@code U=O.creator} destructuring; the nested
     * {@link UsernameInfo} reflects the username only when WA Web's
     * {@code usernameDisplayedEnabled()} flag is on at relay-side.
     */
    public static final class Creator {
        /**
         * The default identifier scalar.
         */
        private final String id;

        /**
         * The LID identifier scalar.
         */
        private final String lid;

        /**
         * The phone-number identifier scalar.
         */
        private final String pn;

        /**
         * The parsed {@code username_info} sub-object.
         */
        private final UsernameInfo usernameInfo;

        /**
         * Constructs a new {@code Creator} wrapping the parsed scalar and
         * nested fields.
         *
         * @apiNote Package-private; instances are produced by
         * {@link #of(JSONObject)}.
         *
         * @param id           the default identifier scalar
         * @param lid          the LID identifier scalar
         * @param pn           the phone-number identifier scalar
         * @param usernameInfo the parsed {@code username_info} sub-object
         */
        private Creator(String id, String lid, String pn, UsernameInfo usernameInfo) {
            this.id = id;
            this.lid = lid;
            this.pn = pn;
            this.usernameInfo = usernameInfo;
        }

        /**
         * Returns the default identifier scalar.
         *
         * @apiNote WA Web feeds this into
         * {@code WAWebWidFactory.createWid(U.id)} to build the
         * {@code owner} WID.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<String> id() {
            return Optional.ofNullable(id);
        }

        /**
         * Returns the LID identifier scalar.
         *
         * @apiNote Present once the founder has migrated to LID
         * addressing; absent on legacy-only accounts.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<String> lid() {
            return Optional.ofNullable(lid);
        }

        /**
         * Returns the phone-number identifier scalar.
         *
         * @apiNote WA Web feeds this into
         * {@code WAWebWidFactory.createWid(V)} to build the
         * {@code creatorPn} WID.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<String> pn() {
            return Optional.ofNullable(pn);
        }

        /**
         * Returns the parsed {@code username_info} sub-object.
         *
         * @apiNote Present only when WA Web's
         * {@code usernameDisplayedEnabled()} gating flag is on at
         * relay-side; carries the founder's username.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<UsernameInfo> usernameInfo() {
            return Optional.ofNullable(usernameInfo);
        }

        /**
         * Parsed projection of the {@code creator.username_info}
         * sub-object.
         *
         * @apiNote Carries the founder's username scalar; absent when
         * usernames are gated off at relay-side.
         */
        public static final class UsernameInfo {
            /**
             * The username scalar projected from
             * {@code creator.username_info.username}.
             */
            private final String username;

            /**
             * Constructs a new {@code UsernameInfo} wrapping the
             * parsed {@code username} scalar.
             *
             * @apiNote Package-private; instances are produced by
             * {@link #of(JSONObject)}.
             *
             * @param username the username scalar
             */
            private UsernameInfo(String username) {
                this.username = username;
            }

            /**
             * Returns the username scalar.
             *
             * @apiNote WA Web feeds this into the {@code creatorUsername}
             * group-info field when usernames are surfaced.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<String> username() {
                return Optional.ofNullable(username);
            }

            /**
             * Parses a {@code UsernameInfo} from the given JSON object.
             *
             * @apiNote Package-private; called from {@link Creator#of(JSONObject)}.
             *
             * @param obj the JSON object to parse
             * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
             */
            static Optional<UsernameInfo> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var username = obj.getString("username");
                return Optional.of(new UsernameInfo(username));
            }

            /**
             * Parses a list of {@code UsernameInfo} entries from the given
             * JSON array.
             *
             * @apiNote Convenience helper for callers walking GraphQL
             * connection edges; skips array entries whose object form
             * parses to {@link Optional#empty()}.
             *
             * @param arr the JSON array to parse
             * @return the list of parsed results, empty if {@code arr} is {@code null}
             */
            static List<UsernameInfo> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<UsernameInfo>(arr.size());
                for (var i = 0; i < arr.size(); i++) {
                    of(arr.getJSONObject(i)).ifPresent(result::add);
                }
                return result;
            }
        }

        /**
         * Parses a {@code Creator} from the given JSON object.
         *
         * @apiNote Package-private; called from the top-level
         * {@link FetchGroupInfoMexResponse#of(byte[])} parser.
         *
         * @param obj the JSON object to parse
         * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
         */
        static Optional<Creator> of(JSONObject obj) {
            if (obj == null) {
                return Optional.empty();
            }

            var id = obj.getString("id");
            var lid = obj.getString("lid");
            var pn = obj.getString("pn");
            var usernameInfo = UsernameInfo.of(obj.getJSONObject("username_info")).orElse(null);
            return Optional.of(new Creator(id, lid, pn, usernameInfo));
        }

        /**
         * Parses a list of {@code Creator} entries from the given JSON
         * array.
         *
         * @apiNote Convenience helper for callers walking GraphQL
         * connection edges; skips array entries whose object form parses
         * to {@link Optional#empty()}.
         *
         * @param arr the JSON array to parse
         * @return the list of parsed results, empty if {@code arr} is {@code null}
         */
        static List<Creator> ofArray(JSONArray arr) {
            if (arr == null) {
                return List.of();
            }

            var result = new ArrayList<Creator>(arr.size());
            for (var i = 0; i < arr.size(); i++) {
                of(arr.getJSONObject(i)).ifPresent(result::add);
            }
            return result;
        }
    }

    /**
     * Parsed projection of the {@code subject} sub-object.
     *
     * @apiNote Carries the current subject text together with the last
     * edit's author and timestamp.
     */
    public static final class Subject {
        /**
         * The parsed {@code creator} sub-object exposing the subject
         * editor's identifiers.
         */
        private final Creator creator;

        /**
         * The subject edit timestamp (seconds since epoch) projected
         * from {@code subject.creation_time}.
         */
        private final Long creationTime;

        /**
         * The subject text scalar projected from {@code subject.value}.
         */
        private final String value;

        /**
         * Constructs a new {@code Subject} wrapping the parsed scalar
         * and nested fields.
         *
         * @apiNote Package-private; instances are produced by
         * {@link #of(JSONObject)}.
         *
         * @param creator      the parsed {@code creator} sub-object
         * @param creationTime the subject edit timestamp in seconds since epoch
         * @param value        the subject text scalar
         */
        private Subject(Creator creator, Long creationTime, String value) {
            this.creator = creator;
            this.creationTime = creationTime;
            this.value = value;
        }

        /**
         * Returns the parsed {@code creator} sub-object.
         *
         * @apiNote Carries the identifiers of the user who last edited
         * the subject; WA Web folds these into {@code subjectOwner},
         * {@code subjectOwnerPn} and {@code subjectOwnerUsername} on the
         * group-info object.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<Creator> creator() {
            return Optional.ofNullable(creator);
        }

        /**
         * Returns the subject edit timestamp.
         *
         * @apiNote Adapted from the relay's seconds-since-epoch
         * {@code Long} into a JDK {@link Instant}; WA Web stores the
         * raw value as {@code subjectTime}.
         *
         * @return an {@link Optional} containing the value as an {@link Instant}, or empty if absent
         */
        public Optional<Instant> creationTime() {
            return Optional.ofNullable(creationTime).map(Instant::ofEpochSecond);
        }

        /**
         * Returns the subject text scalar.
         *
         * @apiNote WA Web mirrors the value into the
         * {@code subject} field of the group-info object and gates the
         * whole projection on its presence
         * ({@code (le == null ? void 0 : le.value) != null}).
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<String> value() {
            return Optional.ofNullable(value);
        }

        /**
         * Parsed projection of the {@code subject.creator} sub-object.
         *
         * @apiNote Mirrors the top-level {@link FetchGroupInfoMexResponse.Creator}
         * shape; the nested class is declared here because the GraphQL
         * schema inlines a separate copy under each parent object.
         */
        public static final class Creator {
            /**
             * The default identifier scalar.
             */
            private final String id;

            /**
             * The LID identifier scalar.
             */
            private final String lid;

            /**
             * The phone-number identifier scalar.
             */
            private final String pn;

            /**
             * The parsed {@code username_info} sub-object.
             */
            private final UsernameInfo usernameInfo;

            /**
             * Constructs a new {@code Creator} wrapping the parsed
             * scalar and nested fields.
             *
             * @apiNote Package-private; instances are produced by
             * {@link #of(JSONObject)}.
             *
             * @param id           the default identifier scalar
             * @param lid          the LID identifier scalar
             * @param pn           the phone-number identifier scalar
             * @param usernameInfo the parsed {@code username_info} sub-object
             */
            private Creator(String id, String lid, String pn, UsernameInfo usernameInfo) {
                this.id = id;
                this.lid = lid;
                this.pn = pn;
                this.usernameInfo = usernameInfo;
            }

            /**
             * Returns the default identifier scalar.
             *
             * @apiNote WA Web feeds this into
             * {@code WAWebWidFactory.createWid(le.creator.id)} to build
             * the {@code subjectOwner} WID.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<String> id() {
                return Optional.ofNullable(id);
            }

            /**
             * Returns the LID identifier scalar.
             *
             * @apiNote Present once the subject editor has migrated to
             * LID addressing; absent on legacy-only accounts.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<String> lid() {
                return Optional.ofNullable(lid);
            }

            /**
             * Returns the phone-number identifier scalar.
             *
             * @apiNote WA Web feeds this into
             * {@code WAWebWidFactory.createWid(se)} to build the
             * {@code subjectOwnerPn} WID.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<String> pn() {
                return Optional.ofNullable(pn);
            }

            /**
             * Returns the parsed {@code username_info} sub-object.
             *
             * @apiNote Present only when WA Web's
             * {@code usernameDisplayedEnabled()} gating flag is on at
             * relay-side; carries the editor's username.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<UsernameInfo> usernameInfo() {
                return Optional.ofNullable(usernameInfo);
            }

            /**
             * Parsed projection of the
             * {@code subject.creator.username_info} sub-object.
             *
             * @apiNote Carries the editor's username scalar; absent
             * when usernames are gated off at relay-side.
             */
            public static final class UsernameInfo {
                /**
                 * The username scalar projected from
                 * {@code subject.creator.username_info.username}.
                 */
                private final String username;

                /**
                 * Constructs a new {@code UsernameInfo} wrapping the
                 * parsed {@code username} scalar.
                 *
                 * @apiNote Package-private; instances are produced by
                 * {@link #of(JSONObject)}.
                 *
                 * @param username the username scalar
                 */
                private UsernameInfo(String username) {
                    this.username = username;
                }

                /**
                 * Returns the username scalar.
                 *
                 * @apiNote WA Web feeds this into the
                 * {@code subjectOwnerUsername} group-info field when
                 * usernames are surfaced.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> username() {
                    return Optional.ofNullable(username);
                }

                /**
                 * Parses a {@code UsernameInfo} from the given JSON
                 * object.
                 *
                 * @apiNote Package-private; called from
                 * {@link Creator#of(JSONObject)}.
                 *
                 * @param obj the JSON object to parse
                 * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                 */
                static Optional<UsernameInfo> of(JSONObject obj) {
                    if (obj == null) {
                        return Optional.empty();
                    }

                    var username = obj.getString("username");
                    return Optional.of(new UsernameInfo(username));
                }

                /**
                 * Parses a list of {@code UsernameInfo} entries from
                 * the given JSON array.
                 *
                 * @apiNote Convenience helper for callers walking
                 * GraphQL connection edges; skips array entries whose
                 * object form parses to {@link Optional#empty()}.
                 *
                 * @param arr the JSON array to parse
                 * @return the list of parsed results, empty if {@code arr} is {@code null}
                 */
                static List<UsernameInfo> ofArray(JSONArray arr) {
                    if (arr == null) {
                        return List.of();
                    }

                    var result = new ArrayList<UsernameInfo>(arr.size());
                    for (var i = 0; i < arr.size(); i++) {
                        of(arr.getJSONObject(i)).ifPresent(result::add);
                    }
                    return result;
                }
            }

            /**
             * Parses a {@code Creator} from the given JSON object.
             *
             * @apiNote Package-private; called from
             * {@link Subject#of(JSONObject)}.
             *
             * @param obj the JSON object to parse
             * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
             */
            static Optional<Creator> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var id = obj.getString("id");
                var lid = obj.getString("lid");
                var pn = obj.getString("pn");
                var usernameInfo = UsernameInfo.of(obj.getJSONObject("username_info")).orElse(null);
                return Optional.of(new Creator(id, lid, pn, usernameInfo));
            }

            /**
             * Parses a list of {@code Creator} entries from the given
             * JSON array.
             *
             * @apiNote Convenience helper for callers walking GraphQL
             * connection edges; skips array entries whose object form
             * parses to {@link Optional#empty()}.
             *
             * @param arr the JSON array to parse
             * @return the list of parsed results, empty if {@code arr} is {@code null}
             */
            static List<Creator> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<Creator>(arr.size());
                for (var i = 0; i < arr.size(); i++) {
                    of(arr.getJSONObject(i)).ifPresent(result::add);
                }
                return result;
            }
        }

        /**
         * Parses a {@code Subject} from the given JSON object.
         *
         * @apiNote Package-private; called from the top-level
         * {@link FetchGroupInfoMexResponse#of(byte[])} parser.
         *
         * @param obj the JSON object to parse
         * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
         */
        static Optional<Subject> of(JSONObject obj) {
            if (obj == null) {
                return Optional.empty();
            }

            var creator = Creator.of(obj.getJSONObject("creator")).orElse(null);
            var creationTime = obj.getLong("creation_time");
            var value = obj.getString("value");
            return Optional.of(new Subject(creator, creationTime, value));
        }

        /**
         * Parses a list of {@code Subject} entries from the given JSON
         * array.
         *
         * @apiNote Convenience helper for callers walking GraphQL
         * connection edges; skips array entries whose object form
         * parses to {@link Optional#empty()}.
         *
         * @param arr the JSON array to parse
         * @return the list of parsed results, empty if {@code arr} is {@code null}
         */
        static List<Subject> ofArray(JSONArray arr) {
            if (arr == null) {
                return List.of();
            }

            var result = new ArrayList<Subject>(arr.size());
            for (var i = 0; i < arr.size(); i++) {
                of(arr.getJSONObject(i)).ifPresent(result::add);
            }
            return result;
        }
    }

    /**
     * Parsed projection of the {@code description} sub-object.
     *
     * @apiNote Carries the current description text together with the
     * last edit's author and timestamp.
     */
    public static final class Description {
        /**
         * The description identifier scalar projected from
         * {@code description.id}.
         */
        private final String id;

        /**
         * The description edit timestamp (seconds since epoch) projected
         * from {@code description.creation_time}.
         */
        private final Long creationTime;

        /**
         * The parsed {@code creator} sub-object exposing the description
         * editor's identifiers.
         */
        private final Creator creator;

        /**
         * The description text scalar projected from
         * {@code description.value}.
         */
        private final String value;

        /**
         * Constructs a new {@code Description} wrapping the parsed
         * scalar and nested fields.
         *
         * @apiNote Package-private; instances are produced by
         * {@link #of(JSONObject)}.
         *
         * @param id           the description identifier scalar
         * @param creationTime the description edit timestamp in seconds since epoch
         * @param creator      the parsed {@code creator} sub-object
         * @param value        the description text scalar
         */
        private Description(String id, Long creationTime, Creator creator, String value) {
            this.id = id;
            this.creationTime = creationTime;
            this.creator = creator;
            this.value = value;
        }

        /**
         * Returns the description identifier scalar.
         *
         * @apiNote WA Web mirrors this into the {@code descId} group-info
         * field; the identifier is the description version, rotated on
         * each edit.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<String> id() {
            return Optional.ofNullable(id);
        }

        /**
         * Returns the description edit timestamp.
         *
         * @apiNote Adapted from the relay's seconds-since-epoch
         * {@code Long} into a JDK {@link Instant}; WA Web stores the
         * raw value as {@code descTime}.
         *
         * @return an {@link Optional} containing the value as an {@link Instant}, or empty if absent
         */
        public Optional<Instant> creationTime() {
            return Optional.ofNullable(creationTime).map(Instant::ofEpochSecond);
        }

        /**
         * Returns the parsed {@code creator} sub-object.
         *
         * @apiNote Carries the identifiers of the user who last edited
         * the description; WA Web folds the id into {@code descOwner}.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<Creator> creator() {
            return Optional.ofNullable(creator);
        }

        /**
         * Returns the description text scalar.
         *
         * @apiNote WA Web mirrors the value into the {@code desc}
         * group-info field.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<String> value() {
            return Optional.ofNullable(value);
        }

        /**
         * Parsed projection of the {@code description.creator}
         * sub-object.
         *
         * @apiNote Mirrors the top-level
         * {@link FetchGroupInfoMexResponse.Creator} shape; the nested
         * class is declared here because the GraphQL schema inlines a
         * separate copy under each parent object.
         */
        public static final class Creator {
            /**
             * The default identifier scalar.
             */
            private final String id;

            /**
             * The LID identifier scalar.
             */
            private final String lid;

            /**
             * The phone-number identifier scalar.
             */
            private final String pn;

            /**
             * The parsed {@code username_info} sub-object.
             */
            private final UsernameInfo usernameInfo;

            /**
             * Constructs a new {@code Creator} wrapping the parsed
             * scalar and nested fields.
             *
             * @apiNote Package-private; instances are produced by
             * {@link #of(JSONObject)}.
             *
             * @param id           the default identifier scalar
             * @param lid          the LID identifier scalar
             * @param pn           the phone-number identifier scalar
             * @param usernameInfo the parsed {@code username_info} sub-object
             */
            private Creator(String id, String lid, String pn, UsernameInfo usernameInfo) {
                this.id = id;
                this.lid = lid;
                this.pn = pn;
                this.usernameInfo = usernameInfo;
            }

            /**
             * Returns the default identifier scalar.
             *
             * @apiNote WA Web feeds this into
             * {@code WAWebWidFactory.createWid(G.creator.id)} to build
             * the {@code descOwner} WID.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<String> id() {
                return Optional.ofNullable(id);
            }

            /**
             * Returns the LID identifier scalar.
             *
             * @apiNote Present once the description editor has migrated
             * to LID addressing; absent on legacy-only accounts.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<String> lid() {
                return Optional.ofNullable(lid);
            }

            /**
             * Returns the phone-number identifier scalar.
             *
             * @apiNote Surfaces the editor's phone number; WA Web does
             * not currently fold this into a dedicated group-info
             * field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<String> pn() {
                return Optional.ofNullable(pn);
            }

            /**
             * Returns the parsed {@code username_info} sub-object.
             *
             * @apiNote Present only when WA Web's
             * {@code usernameDisplayedEnabled()} gating flag is on at
             * relay-side; carries the editor's username.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<UsernameInfo> usernameInfo() {
                return Optional.ofNullable(usernameInfo);
            }

            /**
             * Parsed projection of the
             * {@code description.creator.username_info} sub-object.
             *
             * @apiNote Carries the editor's username scalar; absent
             * when usernames are gated off at relay-side.
             */
            public static final class UsernameInfo {
                /**
                 * The username scalar projected from
                 * {@code description.creator.username_info.username}.
                 */
                private final String username;

                /**
                 * Constructs a new {@code UsernameInfo} wrapping the
                 * parsed {@code username} scalar.
                 *
                 * @apiNote Package-private; instances are produced by
                 * {@link #of(JSONObject)}.
                 *
                 * @param username the username scalar
                 */
                private UsernameInfo(String username) {
                    this.username = username;
                }

                /**
                 * Returns the username scalar.
                 *
                 * @apiNote WA Web feeds this into the
                 * {@code descOwnerUsername} group-info field when
                 * usernames are surfaced.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> username() {
                    return Optional.ofNullable(username);
                }

                /**
                 * Parses a {@code UsernameInfo} from the given JSON
                 * object.
                 *
                 * @apiNote Package-private; called from
                 * {@link Creator#of(JSONObject)}.
                 *
                 * @param obj the JSON object to parse
                 * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                 */
                static Optional<UsernameInfo> of(JSONObject obj) {
                    if (obj == null) {
                        return Optional.empty();
                    }

                    var username = obj.getString("username");
                    return Optional.of(new UsernameInfo(username));
                }

                /**
                 * Parses a list of {@code UsernameInfo} entries from
                 * the given JSON array.
                 *
                 * @apiNote Convenience helper for callers walking
                 * GraphQL connection edges; skips array entries whose
                 * object form parses to {@link Optional#empty()}.
                 *
                 * @param arr the JSON array to parse
                 * @return the list of parsed results, empty if {@code arr} is {@code null}
                 */
                static List<UsernameInfo> ofArray(JSONArray arr) {
                    if (arr == null) {
                        return List.of();
                    }

                    var result = new ArrayList<UsernameInfo>(arr.size());
                    for (var i = 0; i < arr.size(); i++) {
                        of(arr.getJSONObject(i)).ifPresent(result::add);
                    }
                    return result;
                }
            }

            /**
             * Parses a {@code Creator} from the given JSON object.
             *
             * @apiNote Package-private; called from
             * {@link Description#of(JSONObject)}.
             *
             * @param obj the JSON object to parse
             * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
             */
            static Optional<Creator> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var id = obj.getString("id");
                var lid = obj.getString("lid");
                var pn = obj.getString("pn");
                var usernameInfo = UsernameInfo.of(obj.getJSONObject("username_info")).orElse(null);
                return Optional.of(new Creator(id, lid, pn, usernameInfo));
            }

            /**
             * Parses a list of {@code Creator} entries from the given
             * JSON array.
             *
             * @apiNote Convenience helper for callers walking GraphQL
             * connection edges; skips array entries whose object form
             * parses to {@link Optional#empty()}.
             *
             * @param arr the JSON array to parse
             * @return the list of parsed results, empty if {@code arr} is {@code null}
             */
            static List<Creator> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<Creator>(arr.size());
                for (var i = 0; i < arr.size(); i++) {
                    of(arr.getJSONObject(i)).ifPresent(result::add);
                }
                return result;
            }
        }

        /**
         * Parses a {@code Description} from the given JSON object.
         *
         * @apiNote Package-private; called from the top-level
         * {@link FetchGroupInfoMexResponse#of(byte[])} parser.
         *
         * @param obj the JSON object to parse
         * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
         */
        static Optional<Description> of(JSONObject obj) {
            if (obj == null) {
                return Optional.empty();
            }

            var id = obj.getString("id");
            var creationTime = obj.getLong("creation_time");
            var creator = Creator.of(obj.getJSONObject("creator")).orElse(null);
            var value = obj.getString("value");
            return Optional.of(new Description(id, creationTime, creator, value));
        }

        /**
         * Parses a list of {@code Description} entries from the given
         * JSON array.
         *
         * @apiNote Convenience helper for callers walking GraphQL
         * connection edges; skips array entries whose object form
         * parses to {@link Optional#empty()}.
         *
         * @param arr the JSON array to parse
         * @return the list of parsed results, empty if {@code arr} is {@code null}
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
     * Parsed projection of the {@code participants} sub-object.
     *
     * @apiNote Carries the edge list of human participants and the
     * {@code participants_phash_match} flag that signals the relay
     * skipped the edge list because the caller's cache matched.
     */
    public static final class Participants {
        /**
         * The parsed participant edge list projected from
         * {@code participants.edges}.
         */
        private final List<Edges> edges;

        /**
         * The participant-set hash match flag projected from
         * {@code participants.participants_phash_match}.
         */
        private final Boolean participantsPhashMatch;

        /**
         * Constructs a new {@code Participants} wrapping the parsed
         * edge list and hash-match flag.
         *
         * @apiNote Package-private; instances are produced by
         * {@link #of(JSONObject)}.
         *
         * @param edges                  the parsed edge list
         * @param participantsPhashMatch the participant-set hash match flag
         */
        private Participants(List<Edges> edges, Boolean participantsPhashMatch) {
            this.edges = edges;
            this.participantsPhashMatch = participantsPhashMatch;
        }

        /**
         * Returns the parsed participant edge list.
         *
         * @apiNote Empty when the relay omitted the edges (typically
         * because {@code participants_phash_match} is {@code true}); WA
         * Web treats a missing edge list with hash mismatch as a server
         * error.
         *
         * @return the list of edge entries, empty if absent
         */
        public List<Edges> edges() {
            return edges;
        }

        /**
         * Returns the participant-set hash match flag.
         *
         * @apiNote {@code true} when the relay confirms the caller's
         * locally-known participant hash matches its own, in which
         * case the edge list is intentionally omitted to save
         * bandwidth.
         *
         * @return {@code true} if the value is present and true,
         *         {@code false} otherwise
         */
        public boolean participantsPhashMatch() {
            return participantsPhashMatch != null && participantsPhashMatch;
        }

        /**
         * Parsed projection of a single {@code participants.edges} entry.
         *
         * @apiNote Wraps the edge's {@code node} (the participant
         * details) and the {@code role} scalar
         * ({@code "ADMIN_MEMBER"}, {@code "SUPERADMIN_MEMBER"} or
         * regular member).
         */
        public static final class Edges {
            /**
             * The parsed {@code node} sub-object exposing the
             * participant's identifiers.
             */
            private final Node node;

            /**
             * The participant role scalar projected from
             * {@code edges.role}.
             */
            private final String role;

            /**
             * Constructs a new {@code Edges} wrapping the parsed
             * node and role scalar.
             *
             * @apiNote Package-private; instances are produced by
             * {@link #of(JSONObject)}.
             *
             * @param node the parsed {@code node} sub-object
             * @param role the participant role scalar
             */
            private Edges(Node node, String role) {
                this.node = node;
                this.role = role;
            }

            /**
             * Returns the parsed {@code node} sub-object.
             *
             * @apiNote Carries the participant's identifiers; WA Web
             * raises a server error when this field is absent because
             * the edge list cannot be flattened without it.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<Node> node() {
                return Optional.ofNullable(node);
            }

            /**
             * Returns the participant role scalar.
             *
             * @apiNote One of {@code "ADMIN_MEMBER"} or
             * {@code "SUPERADMIN_MEMBER"} for admins; a non-admin
             * member returns the default (typically {@code null} or
             * an empty role).
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<String> role() {
                return Optional.ofNullable(role);
            }

            /**
             * Parsed projection of a single
             * {@code participants.edges.node} sub-object.
             *
             * @apiNote Carries the participant's identifiers and
             * display fields; the bot-aware variant (with an extra
             * {@code jid} field) lives in
             * {@link FetchGroupInfoIncludBotsMexResponse}.
             */
            public static final class Node {
                /**
                 * The default identifier scalar projected from
                 * {@code node.id}.
                 */
                private final String id;

                /**
                 * The LID identifier scalar projected from
                 * {@code node.lid}.
                 */
                private final String lid;

                /**
                 * The phone-number identifier scalar projected from
                 * {@code node.pn}.
                 */
                private final String pn;

                /**
                 * The participant display name scalar projected from
                 * {@code node.display_name}.
                 */
                private final String displayName;

                /**
                 * The parsed {@code username_info} sub-object.
                 */
                private final UsernameInfo usernameInfo;

                /**
                 * Constructs a new {@code Node} wrapping the parsed
                 * scalar and nested fields.
                 *
                 * @apiNote Package-private; instances are produced by
                 * {@link #of(JSONObject)}.
                 *
                 * @param id           the default identifier scalar
                 * @param lid          the LID identifier scalar
                 * @param pn           the phone-number identifier scalar
                 * @param displayName  the display name scalar
                 * @param usernameInfo the parsed {@code username_info} sub-object
                 */
                private Node(String id, String lid, String pn, String displayName, UsernameInfo usernameInfo) {
                    this.id = id;
                    this.lid = lid;
                    this.pn = pn;
                    this.displayName = displayName;
                    this.usernameInfo = usernameInfo;
                }

                /**
                 * Returns the default identifier scalar.
                 *
                 * @apiNote WA Web feeds this into
                 * {@code WAWebWidFactory.createWid(a.id)} to build
                 * the participant WID; the relay raises a 500 status
                 * code when the field is absent.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> id() {
                    return Optional.ofNullable(id);
                }

                /**
                 * Returns the LID identifier scalar.
                 *
                 * @apiNote Present once the participant has migrated
                 * to LID addressing; absent on legacy-only accounts.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> lid() {
                    return Optional.ofNullable(lid);
                }

                /**
                 * Returns the phone-number identifier scalar.
                 *
                 * @apiNote WA Web folds this into the {@code phoneNumber}
                 * field on the flattened participant descriptor.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> pn() {
                    return Optional.ofNullable(pn);
                }

                /**
                 * Returns the participant display name scalar.
                 *
                 * @apiNote WA Web folds this into the
                 * {@code displayName} field on the flattened
                 * participant descriptor.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> displayName() {
                    return Optional.ofNullable(displayName);
                }

                /**
                 * Returns the parsed {@code username_info} sub-object.
                 *
                 * @apiNote Present only when WA Web's
                 * {@code usernameDisplayedEnabled()} gating flag is on
                 * at relay-side; carries the participant's username.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<UsernameInfo> usernameInfo() {
                    return Optional.ofNullable(usernameInfo);
                }

                /**
                 * Parsed projection of the
                 * {@code node.username_info} sub-object.
                 *
                 * @apiNote Carries the participant's username scalar;
                 * absent when usernames are gated off at relay-side.
                 */
                public static final class UsernameInfo {
                    /**
                     * The username scalar projected from
                     * {@code node.username_info.username}.
                     */
                    private final String username;

                    /**
                     * Constructs a new {@code UsernameInfo} wrapping
                     * the parsed {@code username} scalar.
                     *
                     * @apiNote Package-private; instances are produced
                     * by {@link #of(JSONObject)}.
                     *
                     * @param username the username scalar
                     */
                    private UsernameInfo(String username) {
                        this.username = username;
                    }

                    /**
                     * Returns the username scalar.
                     *
                     * @apiNote WA Web folds this into the
                     * {@code username} field on the flattened
                     * participant descriptor when usernames are
                     * surfaced.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> username() {
                        return Optional.ofNullable(username);
                    }

                    /**
                     * Parses a {@code UsernameInfo} from the given
                     * JSON object.
                     *
                     * @apiNote Package-private; called from
                     * {@link Node#of(JSONObject)}.
                     *
                     * @param obj the JSON object to parse
                     * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                     */
                    static Optional<UsernameInfo> of(JSONObject obj) {
                        if (obj == null) {
                            return Optional.empty();
                        }

                        var username = obj.getString("username");
                        return Optional.of(new UsernameInfo(username));
                    }

                    /**
                     * Parses a list of {@code UsernameInfo} entries
                     * from the given JSON array.
                     *
                     * @apiNote Convenience helper for callers walking
                     * GraphQL connection edges; skips array entries
                     * whose object form parses to {@link Optional#empty()}.
                     *
                     * @param arr the JSON array to parse
                     * @return the list of parsed results, empty if {@code arr} is {@code null}
                     */
                    static List<UsernameInfo> ofArray(JSONArray arr) {
                        if (arr == null) {
                            return List.of();
                        }

                        var result = new ArrayList<UsernameInfo>(arr.size());
                        for (var i = 0; i < arr.size(); i++) {
                            of(arr.getJSONObject(i)).ifPresent(result::add);
                        }
                        return result;
                    }
                }

                /**
                 * Parses a {@code Node} from the given JSON object.
                 *
                 * @apiNote Package-private; called from
                 * {@link Edges#of(JSONObject)}.
                 *
                 * @param obj the JSON object to parse
                 * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                 */
                static Optional<Node> of(JSONObject obj) {
                    if (obj == null) {
                        return Optional.empty();
                    }

                    var id = obj.getString("id");
                    var lid = obj.getString("lid");
                    var pn = obj.getString("pn");
                    var displayName = obj.getString("display_name");
                    var usernameInfo = UsernameInfo.of(obj.getJSONObject("username_info")).orElse(null);
                    return Optional.of(new Node(id, lid, pn, displayName, usernameInfo));
                }

                /**
                 * Parses a list of {@code Node} entries from the given
                 * JSON array.
                 *
                 * @apiNote Convenience helper for callers walking
                 * GraphQL connection edges; skips array entries whose
                 * object form parses to {@link Optional#empty()}.
                 *
                 * @param arr the JSON array to parse
                 * @return the list of parsed results, empty if {@code arr} is {@code null}
                 */
                static List<Node> ofArray(JSONArray arr) {
                    if (arr == null) {
                        return List.of();
                    }

                    var result = new ArrayList<Node>(arr.size());
                    for (var i = 0; i < arr.size(); i++) {
                        of(arr.getJSONObject(i)).ifPresent(result::add);
                    }
                    return result;
                }
            }

            /**
             * Parses an {@code Edges} entry from the given JSON object.
             *
             * @apiNote Package-private; called from
             * {@link #ofArray(JSONArray)}.
             *
             * @param obj the JSON object to parse
             * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
             */
            static Optional<Edges> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var node = Node.of(obj.getJSONObject("node")).orElse(null);
                var role = obj.getString("role");
                return Optional.of(new Edges(node, role));
            }

            /**
             * Parses a list of {@code Edges} entries from the given
             * JSON array.
             *
             * @apiNote Called by {@link Participants#of(JSONObject)} to
             * unwrap the GraphQL connection edge array; skips array
             * entries whose object form parses to {@link Optional#empty()}.
             *
             * @param arr the JSON array to parse
             * @return the list of parsed results, empty if {@code arr} is {@code null}
             */
            static List<Edges> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<Edges>(arr.size());
                for (var i = 0; i < arr.size(); i++) {
                    of(arr.getJSONObject(i)).ifPresent(result::add);
                }
                return result;
            }
        }

        /**
         * Parses a {@code Participants} from the given JSON object.
         *
         * @apiNote Package-private; called from the top-level
         * {@link FetchGroupInfoMexResponse#of(byte[])} parser.
         *
         * @param obj the JSON object to parse
         * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
         */
        static Optional<Participants> of(JSONObject obj) {
            if (obj == null) {
                return Optional.empty();
            }

            var edges = Edges.ofArray(obj.getJSONArray("edges"));
            var participantsPhashMatch = obj.getBoolean("participants_phash_match");
            return Optional.of(new Participants(edges, participantsPhashMatch));
        }

        /**
         * Parses a list of {@code Participants} entries from the given
         * JSON array.
         *
         * @apiNote Convenience helper for callers walking GraphQL
         * connection edges; skips array entries whose object form
         * parses to {@link Optional#empty()}.
         *
         * @param arr the JSON array to parse
         * @return the list of parsed results, empty if {@code arr} is {@code null}
         */
        static List<Participants> ofArray(JSONArray arr) {
            if (arr == null) {
                return List.of();
            }

            var result = new ArrayList<Participants>(arr.size());
            for (var i = 0; i < arr.size(); i++) {
                of(arr.getJSONObject(i)).ifPresent(result::add);
            }
            return result;
        }
    }

    /**
     * Parsed projection of the {@code properties} sub-object
     * aggregating the configurable group properties.
     *
     * @apiNote Each scalar maps onto a single
     * {@code WAWebSchemaGroupMetadata} field WA Web folds into the
     * flattened group-info object. The sub-objects ({@code LimitSharing},
     * {@code LidMigrationState}, {@code Ephemeral}, {@code GrowthLocked2})
     * mirror the GraphQL sub-shape because the relay nests their
     * properties one level deeper than the rest.
     */
    public static final class Properties {
        /**
         * The {@code allow_non_admin_sub_group_creation} flag scalar.
         */
        private final Boolean allowNonAdminSubGroupCreation;

        /**
         * The {@code closed_by_membership_approval_mode} scalar.
         */
        private final String closedByMembershipApprovalMode;

        /**
         * The parsed {@code limit_sharing} sub-object.
         */
        private final LimitSharing limitSharing;

        /**
         * The parsed {@code lid_migration_state} sub-object.
         */
        private final LidMigrationState lidMigrationState;

        /**
         * The parsed {@code ephemeral} sub-object carrying the
         * disappearing-messages timer.
         */
        private final Ephemeral ephemeral;

        /**
         * The parsed {@code growth_locked2} sub-object.
         */
        private final GrowthLocked2 growthLocked2;

        /**
         * The {@code member_add_mode} scalar
         * ({@code "ADMIN_ADD"} or {@code "ALL_MEMBER_ADD"}).
         */
        private final String memberAddMode;

        /**
         * The {@code parent_group_jid} scalar linking a subgroup to
         * its parent community.
         */
        private final String parentGroupJid;

        /**
         * The {@code group_safety_check} scalar.
         */
        private final String groupSafetyCheck;

        /**
         * The {@code allow_admin_reports} flag scalar.
         */
        private final Boolean allowAdminReports;

        /**
         * The {@code announcement} flag scalar.
         */
        private final String announcement;

        /**
         * The {@code locked} flag scalar carrying the restrict-mode
         * indicator.
         */
        private final String locked;

        /**
         * The {@code member_link_mode} scalar.
         */
        private final String memberLinkMode;

        /**
         * The {@code member_share_group_history_mode} scalar.
         */
        private final String memberShareGroupHistoryMode;

        /**
         * The {@code membership_approval_mode_enabled} flag scalar.
         */
        private final Boolean membershipApprovalModeEnabled;

        /**
         * The {@code general_chat} flag scalar marking a subgroup as
         * the community's general-chat thread.
         */
        private final String generalChat;

        /**
         * The {@code auto_add_disabled} flag scalar.
         */
        private final Boolean autoAddDisabled;

        /**
         * The {@code hidden_group} flag scalar.
         */
        private final String hiddenGroup;

        /**
         * The {@code capi} flag scalar (Conversion-API attribution).
         */
        private final String capi;

        /**
         * The {@code support} flag scalar marking the group as a
         * support channel.
         */
        private final String support;

        /**
         * Constructs a new {@code Properties} wrapping the parsed
         * scalar and nested fields.
         *
         * @apiNote Package-private; instances are produced by
         * {@link #of(JSONObject)}.
         *
         * @param allowNonAdminSubGroupCreation   the {@code allow_non_admin_sub_group_creation} scalar
         * @param closedByMembershipApprovalMode  the {@code closed_by_membership_approval_mode} scalar
         * @param limitSharing                    the parsed {@code limit_sharing} sub-object
         * @param lidMigrationState               the parsed {@code lid_migration_state} sub-object
         * @param ephemeral                       the parsed {@code ephemeral} sub-object
         * @param growthLocked2                   the parsed {@code growth_locked2} sub-object
         * @param memberAddMode                   the {@code member_add_mode} scalar
         * @param parentGroupJid                  the {@code parent_group_jid} scalar
         * @param groupSafetyCheck                the {@code group_safety_check} scalar
         * @param allowAdminReports               the {@code allow_admin_reports} scalar
         * @param announcement                    the {@code announcement} scalar
         * @param locked                          the {@code locked} scalar
         * @param memberLinkMode                  the {@code member_link_mode} scalar
         * @param memberShareGroupHistoryMode     the {@code member_share_group_history_mode} scalar
         * @param membershipApprovalModeEnabled   the {@code membership_approval_mode_enabled} scalar
         * @param generalChat                     the {@code general_chat} scalar
         * @param autoAddDisabled                 the {@code auto_add_disabled} scalar
         * @param hiddenGroup                     the {@code hidden_group} scalar
         * @param capi                            the {@code capi} scalar
         * @param support                         the {@code support} scalar
         */
        private Properties(Boolean allowNonAdminSubGroupCreation, String closedByMembershipApprovalMode, LimitSharing limitSharing, LidMigrationState lidMigrationState, Ephemeral ephemeral, GrowthLocked2 growthLocked2, String memberAddMode, String parentGroupJid, String groupSafetyCheck, Boolean allowAdminReports, String announcement, String locked, String memberLinkMode, String memberShareGroupHistoryMode, Boolean membershipApprovalModeEnabled, String generalChat, Boolean autoAddDisabled, String hiddenGroup, String capi, String support) {
            this.allowNonAdminSubGroupCreation = allowNonAdminSubGroupCreation;
            this.closedByMembershipApprovalMode = closedByMembershipApprovalMode;
            this.limitSharing = limitSharing;
            this.lidMigrationState = lidMigrationState;
            this.ephemeral = ephemeral;
            this.growthLocked2 = growthLocked2;
            this.memberAddMode = memberAddMode;
            this.parentGroupJid = parentGroupJid;
            this.groupSafetyCheck = groupSafetyCheck;
            this.allowAdminReports = allowAdminReports;
            this.announcement = announcement;
            this.locked = locked;
            this.memberLinkMode = memberLinkMode;
            this.memberShareGroupHistoryMode = memberShareGroupHistoryMode;
            this.membershipApprovalModeEnabled = membershipApprovalModeEnabled;
            this.generalChat = generalChat;
            this.autoAddDisabled = autoAddDisabled;
            this.hiddenGroup = hiddenGroup;
            this.capi = capi;
            this.support = support;
        }

        /**
         * Returns the {@code allow_non_admin_sub_group_creation} flag.
         *
         * @apiNote {@code true} when a community lets non-admins create
         * subgroups; WA Web folds this into the
         * {@code allowNonAdminSubGroupCreation} group-info field.
         *
         * @return {@code true} if the value is present and true,
         *         {@code false} otherwise
         */
        public boolean allowNonAdminSubGroupCreation() {
            return allowNonAdminSubGroupCreation != null && allowNonAdminSubGroupCreation;
        }

        /**
         * Returns the {@code closed_by_membership_approval_mode} scalar.
         *
         * @apiNote WA Web compares this against {@code true} to set
         * {@code isParentGroupClosed}; carries a stringly-typed boolean
         * because the relay surfaces it as a textual flag.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<String> closedByMembershipApprovalMode() {
            return Optional.ofNullable(closedByMembershipApprovalMode);
        }

        /**
         * Returns the parsed {@code limit_sharing} sub-object.
         *
         * @apiNote Carries the {@code limit_sharing_enabled} flag that
         * WA Web folds into the {@code limitSharingEnabled} group-info
         * field.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<LimitSharing> limitSharing() {
            return Optional.ofNullable(limitSharing);
        }

        /**
         * Returns the parsed {@code lid_migration_state} sub-object.
         *
         * @apiNote Carries the {@code addressing_mode} scalar; WA Web
         * compares it against the {@code "LID"} literal to set the
         * {@code isLidAddressingMode} group-info field.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<LidMigrationState> lidMigrationState() {
            return Optional.ofNullable(lidMigrationState);
        }

        /**
         * Returns the parsed {@code ephemeral} sub-object carrying the
         * disappearing-messages timer.
         *
         * @apiNote Folds into the {@code ephemeralDuration} (and
         * {@code afterReadDuration} when WA Web's after-read gating
         * is on) group-info field.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<Ephemeral> ephemeral() {
            return Optional.ofNullable(ephemeral);
        }

        /**
         * Returns the parsed {@code growth_locked2} sub-object.
         *
         * @apiNote WA Web folds the nested {@code locked} flag into
         * {@code growthLockType: "invite"} on the group-info object
         * when {@code true}, suspending invite-link growth.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<GrowthLocked2> growthLocked2() {
            return Optional.ofNullable(growthLocked2);
        }

        /**
         * Returns the {@code member_add_mode} scalar.
         *
         * @apiNote One of {@code "ADMIN_ADD"} or
         * {@code "ALL_MEMBER_ADD"}; WA Web folds this through
         * {@code WAWebSchemaGroupMetadata.MemberAddMode} into the
         * {@code memberAddMode} group-info field.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<String> memberAddMode() {
            return Optional.ofNullable(memberAddMode);
        }

        /**
         * Returns the {@code parent_group_jid} scalar.
         *
         * @apiNote Set on subgroups to point at the parent community
         * JID; WA Web feeds the value through
         * {@code WAWebWidFactory.createWid} into the
         * {@code parentGroup} group-info field.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<String> parentGroupJid() {
            return Optional.ofNullable(parentGroupJid);
        }

        /**
         * Returns the {@code group_safety_check} scalar.
         *
         * @apiNote Mirrors the {@code groupSafetyCheck} group-info
         * field surfaced by WA Web's group-safety pipeline.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<String> groupSafetyCheck() {
            return Optional.ofNullable(groupSafetyCheck);
        }

        /**
         * Returns the {@code allow_admin_reports} flag.
         *
         * @apiNote {@code true} when the group lets admins receive
         * member reports; WA Web folds this into the
         * {@code reportToAdminMode} group-info field.
         *
         * @return {@code true} if the value is present and true,
         *         {@code false} otherwise
         */
        public boolean allowAdminReports() {
            return allowAdminReports != null && allowAdminReports;
        }

        /**
         * Returns the {@code announcement} scalar.
         *
         * @apiNote WA Web compares this against {@code true} to set
         * the {@code announce} group-info field, which restricts new
         * messages to admins only.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<String> announcement() {
            return Optional.ofNullable(announcement);
        }

        /**
         * Returns the {@code locked} scalar.
         *
         * @apiNote WA Web compares this against {@code true} to set
         * the {@code restrict} group-info field, which restricts group
         * info edits to admins only.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<String> locked() {
            return Optional.ofNullable(locked);
        }

        /**
         * Returns the {@code member_link_mode} scalar.
         *
         * @apiNote WA Web folds this through
         * {@code WAWebGroupMemberLinkMode.getMemberLinkModeFromMexType}
         * into the {@code memberLinkMode} group-info field.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<String> memberLinkMode() {
            return Optional.ofNullable(memberLinkMode);
        }

        /**
         * Returns the {@code member_share_group_history_mode} scalar.
         *
         * @apiNote WA Web folds this through
         * {@code WAWebGroupHistoryShareMode.getMemberShareGroupHistoryModeFromMexType}
         * into the {@code memberShareGroupHistoryMode} group-info
         * field, controlling chat-history sharing on member joins.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<String> memberShareGroupHistoryMode() {
            return Optional.ofNullable(memberShareGroupHistoryMode);
        }

        /**
         * Returns the {@code membership_approval_mode_enabled} flag.
         *
         * @apiNote {@code true} when joins require admin approval; WA
         * Web folds this into the {@code membershipApprovalMode}
         * group-info field.
         *
         * @return {@code true} if the value is present and true,
         *         {@code false} otherwise
         */
        public boolean membershipApprovalModeEnabled() {
            return membershipApprovalModeEnabled != null && membershipApprovalModeEnabled;
        }

        /**
         * Returns the {@code general_chat} scalar.
         *
         * @apiNote WA Web compares this against {@code true} to set
         * the {@code generalSubgroup} group-info field, marking a
         * subgroup as the community's auto-created general chat.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<String> generalChat() {
            return Optional.ofNullable(generalChat);
        }

        /**
         * Returns the {@code auto_add_disabled} flag.
         *
         * @apiNote {@code true} when the general-chat subgroup will
         * not auto-add new community members; WA Web folds this into
         * the {@code generalChatAutoAddDisabled} group-info field.
         *
         * @return {@code true} if the value is present and true,
         *         {@code false} otherwise
         */
        public boolean autoAddDisabled() {
            return autoAddDisabled != null && autoAddDisabled;
        }

        /**
         * Returns the {@code hidden_group} scalar.
         *
         * @apiNote WA Web folds this into the {@code hiddenSubgroup}
         * group-info field; carries the relay's stringly-typed flag
         * because the wire payload uses a textual indicator.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<String> hiddenGroup() {
            return Optional.ofNullable(hiddenGroup);
        }

        /**
         * Returns the {@code capi} scalar.
         *
         * @apiNote WA Web compares this against {@code true} to set
         * the {@code hasCapi} group-info field, flagging
         * Conversion-API attribution.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<String> capi() {
            return Optional.ofNullable(capi);
        }

        /**
         * Returns the {@code support} scalar.
         *
         * @apiNote WA Web folds this into the {@code support}
         * group-info field, marking the group as a support channel
         * with the corresponding chat-UI treatment.
         *
         * @return an {@link Optional} containing the value, or empty if absent
         */
        public Optional<String> support() {
            return Optional.ofNullable(support);
        }

        /**
         * Parsed projection of the {@code limit_sharing} sub-object.
         *
         * @apiNote Wraps the single
         * {@code limit_sharing_enabled} flag; the GraphQL schema
         * exposes a sub-object to leave room for future per-property
         * extensions.
         */
        public static final class LimitSharing {
            /**
             * The {@code limit_sharing_enabled} flag scalar.
             */
            private final Boolean limitSharingEnabled;

            /**
             * Constructs a new {@code LimitSharing} wrapping the
             * parsed {@code limit_sharing_enabled} flag scalar.
             *
             * @apiNote Package-private; instances are produced by
             * {@link #of(JSONObject)}.
             *
             * @param limitSharingEnabled the flag scalar
             */
            private LimitSharing(Boolean limitSharingEnabled) {
                this.limitSharingEnabled = limitSharingEnabled;
            }

            /**
             * Returns the {@code limit_sharing_enabled} flag.
             *
             * @apiNote {@code true} when the group restricts
             * cross-app sharing of its content; WA Web folds this
             * into the {@code limitSharingEnabled} group-info field.
             *
             * @return {@code true} if the value is present and true,
             *         {@code false} otherwise
             */
            public boolean limitSharingEnabled() {
                return limitSharingEnabled != null && limitSharingEnabled;
            }

            /**
             * Parses a {@code LimitSharing} from the given JSON object.
             *
             * @apiNote Package-private; called from
             * {@link Properties#of(JSONObject)}.
             *
             * @param obj the JSON object to parse
             * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
             */
            static Optional<LimitSharing> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var limitSharingEnabled = obj.getBoolean("limit_sharing_enabled");
                return Optional.of(new LimitSharing(limitSharingEnabled));
            }

            /**
             * Parses a list of {@code LimitSharing} entries from the
             * given JSON array.
             *
             * @apiNote Convenience helper for callers walking GraphQL
             * connection edges; skips array entries whose object form
             * parses to {@link Optional#empty()}.
             *
             * @param arr the JSON array to parse
             * @return the list of parsed results, empty if {@code arr} is {@code null}
             */
            static List<LimitSharing> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<LimitSharing>(arr.size());
                for (var i = 0; i < arr.size(); i++) {
                    of(arr.getJSONObject(i)).ifPresent(result::add);
                }
                return result;
            }
        }

        /**
         * Parsed projection of the {@code lid_migration_state}
         * sub-object.
         *
         * @apiNote Wraps the single {@code addressing_mode} scalar
         * (one of {@code "LID"} or {@code "PHONE_NUMBER"}); WA Web
         * compares it against the {@code "LID"} literal to drive the
         * {@code isLidAddressingMode} group-info field.
         */
        public static final class LidMigrationState {
            /**
             * The {@code addressing_mode} scalar.
             */
            private final String addressingMode;

            /**
             * Constructs a new {@code LidMigrationState} wrapping the
             * parsed {@code addressing_mode} scalar.
             *
             * @apiNote Package-private; instances are produced by
             * {@link #of(JSONObject)}.
             *
             * @param addressingMode the addressing-mode scalar
             */
            private LidMigrationState(String addressingMode) {
                this.addressingMode = addressingMode;
            }

            /**
             * Returns the {@code addressing_mode} scalar.
             *
             * @apiNote One of {@code "LID"} or {@code "PHONE_NUMBER"};
             * WA Web treats the {@code "LID"} literal as the trigger
             * for the LID-addressing group-info flag.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<String> addressingMode() {
                return Optional.ofNullable(addressingMode);
            }

            /**
             * Parses a {@code LidMigrationState} from the given JSON
             * object.
             *
             * @apiNote Package-private; called from
             * {@link Properties#of(JSONObject)}.
             *
             * @param obj the JSON object to parse
             * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
             */
            static Optional<LidMigrationState> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var addressingMode = obj.getString("addressing_mode");
                return Optional.of(new LidMigrationState(addressingMode));
            }

            /**
             * Parses a list of {@code LidMigrationState} entries from
             * the given JSON array.
             *
             * @apiNote Convenience helper for callers walking GraphQL
             * connection edges; skips array entries whose object form
             * parses to {@link Optional#empty()}.
             *
             * @param arr the JSON array to parse
             * @return the list of parsed results, empty if {@code arr} is {@code null}
             */
            static List<LidMigrationState> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<LidMigrationState>(arr.size());
                for (var i = 0; i < arr.size(); i++) {
                    of(arr.getJSONObject(i)).ifPresent(result::add);
                }
                return result;
            }
        }

        /**
         * Parsed projection of the {@code ephemeral} sub-object
         * carrying the disappearing-messages timer.
         *
         * @apiNote Wraps the {@code expiration_time_in_sec} scalar; WA
         * Web folds it into the {@code ephemeralDuration} group-info
         * field, optionally routing it through the after-read fallback
         * when {@code WAWebAfterReadUtils} reports an after-read
         * duration.
         */
        public static final class Ephemeral {
            /**
             * The {@code expiration_time_in_sec} scalar.
             */
            private final Long expirationTimeInSec;

            /**
             * Constructs a new {@code Ephemeral} wrapping the parsed
             * {@code expiration_time_in_sec} scalar.
             *
             * @apiNote Package-private; instances are produced by
             * {@link #of(JSONObject)}.
             *
             * @param expirationTimeInSec the expiration-time scalar in seconds
             */
            private Ephemeral(Long expirationTimeInSec) {
                this.expirationTimeInSec = expirationTimeInSec;
            }

            /**
             * Returns the {@code expiration_time_in_sec} value as a
             * {@link Duration}.
             *
             * @apiNote Adapted from the relay's seconds-since-epoch
             * {@code Long} into a JDK {@link Duration}; WA Web routes
             * the raw value through {@code WAWebAfterReadUtils} when
             * the after-read gating flag is on.
             *
             * @return an {@link Optional} containing the value as a {@link Duration}, or empty if absent
             */
            public Optional<Duration> expirationTimeInSec() {
                return Optional.ofNullable(expirationTimeInSec).map(Duration::ofSeconds);
            }

            /**
             * Parses an {@code Ephemeral} from the given JSON object.
             *
             * @apiNote Package-private; called from
             * {@link Properties#of(JSONObject)}.
             *
             * @param obj the JSON object to parse
             * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
             */
            static Optional<Ephemeral> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var expirationTimeInSec = obj.getLong("expiration_time_in_sec");
                return Optional.of(new Ephemeral(expirationTimeInSec));
            }

            /**
             * Parses a list of {@code Ephemeral} entries from the given
             * JSON array.
             *
             * @apiNote Convenience helper for callers walking GraphQL
             * connection edges; skips array entries whose object form
             * parses to {@link Optional#empty()}.
             *
             * @param arr the JSON array to parse
             * @return the list of parsed results, empty if {@code arr} is {@code null}
             */
            static List<Ephemeral> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<Ephemeral>(arr.size());
                for (var i = 0; i < arr.size(); i++) {
                    of(arr.getJSONObject(i)).ifPresent(result::add);
                }
                return result;
            }
        }

        /**
         * Parsed projection of the {@code growth_locked2} sub-object.
         *
         * @apiNote Wraps the single {@code locked} flag; WA Web folds
         * a {@code true} value into {@code growthLockType: "invite"}
         * on the flattened group-info object, suspending invite-link
         * growth.
         */
        public static final class GrowthLocked2 {
            /**
             * The {@code locked} flag scalar.
             */
            private final String locked;

            /**
             * Constructs a new {@code GrowthLocked2} wrapping the
             * parsed {@code locked} scalar.
             *
             * @apiNote Package-private; instances are produced by
             * {@link #of(JSONObject)}.
             *
             * @param locked the locked-flag scalar
             */
            private GrowthLocked2(String locked) {
                this.locked = locked;
            }

            /**
             * Returns the {@code locked} flag scalar.
             *
             * @apiNote WA Web compares this against {@code true} to
             * set {@code growthLockType: "invite"} on the group-info
             * object, suspending invite-link growth.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<String> locked() {
                return Optional.ofNullable(locked);
            }

            /**
             * Parses a {@code GrowthLocked2} from the given JSON
             * object.
             *
             * @apiNote Package-private; called from
             * {@link Properties#of(JSONObject)}.
             *
             * @param obj the JSON object to parse
             * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
             */
            static Optional<GrowthLocked2> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var locked = obj.getString("locked");
                return Optional.of(new GrowthLocked2(locked));
            }

            /**
             * Parses a list of {@code GrowthLocked2} entries from the
             * given JSON array.
             *
             * @apiNote Convenience helper for callers walking GraphQL
             * connection edges; skips array entries whose object form
             * parses to {@link Optional#empty()}.
             *
             * @param arr the JSON array to parse
             * @return the list of parsed results, empty if {@code arr} is {@code null}
             */
            static List<GrowthLocked2> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<GrowthLocked2>(arr.size());
                for (var i = 0; i < arr.size(); i++) {
                    of(arr.getJSONObject(i)).ifPresent(result::add);
                }
                return result;
            }
        }

        /**
         * Parses a {@code Properties} from the given JSON object.
         *
         * @apiNote Package-private; called from the top-level
         * {@link FetchGroupInfoMexResponse#of(byte[])} parser.
         *
         * @param obj the JSON object to parse
         * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
         */
        static Optional<Properties> of(JSONObject obj) {
            if (obj == null) {
                return Optional.empty();
            }

            var allowNonAdminSubGroupCreation = obj.getBoolean("allow_non_admin_sub_group_creation");
            var closedByMembershipApprovalMode = obj.getString("closed_by_membership_approval_mode");
            var limitSharing = LimitSharing.of(obj.getJSONObject("limit_sharing")).orElse(null);
            var lidMigrationState = LidMigrationState.of(obj.getJSONObject("lid_migration_state")).orElse(null);
            var ephemeral = Ephemeral.of(obj.getJSONObject("ephemeral")).orElse(null);
            var growthLocked2 = GrowthLocked2.of(obj.getJSONObject("growth_locked2")).orElse(null);
            var memberAddMode = obj.getString("member_add_mode");
            var parentGroupJid = obj.getString("parent_group_jid");
            var groupSafetyCheck = obj.getString("group_safety_check");
            var allowAdminReports = obj.getBoolean("allow_admin_reports");
            var announcement = obj.getString("announcement");
            var locked = obj.getString("locked");
            var memberLinkMode = obj.getString("member_link_mode");
            var memberShareGroupHistoryMode = obj.getString("member_share_group_history_mode");
            var membershipApprovalModeEnabled = obj.getBoolean("membership_approval_mode_enabled");
            var generalChat = obj.getString("general_chat");
            var autoAddDisabled = obj.getBoolean("auto_add_disabled");
            var hiddenGroup = obj.getString("hidden_group");
            var capi = obj.getString("capi");
            var support = obj.getString("support");
            return Optional.of(new Properties(allowNonAdminSubGroupCreation, closedByMembershipApprovalMode, limitSharing, lidMigrationState, ephemeral, growthLocked2, memberAddMode, parentGroupJid, groupSafetyCheck, allowAdminReports, announcement, locked, memberLinkMode, memberShareGroupHistoryMode, membershipApprovalModeEnabled, generalChat, autoAddDisabled, hiddenGroup, capi, support));
        }

        /**
         * Parses a list of {@code Properties} entries from the given
         * JSON array.
         *
         * @apiNote Convenience helper for callers walking GraphQL
         * connection edges; skips array entries whose object form
         * parses to {@link Optional#empty()}.
         *
         * @param arr the JSON array to parse
         * @return the list of parsed results, empty if {@code arr} is {@code null}
         */
        static List<Properties> ofArray(JSONArray arr) {
            if (arr == null) {
                return List.of();
            }

            var result = new ArrayList<Properties>(arr.size());
            for (var i = 0; i < arr.size(); i++) {
                of(arr.getJSONObject(i)).ifPresent(result::add);
            }
            return result;
        }
    }

    /**
     * Parses the JSON payload carried by the {@code <result>} child
     * into a {@link FetchGroupInfoMexResponse}.
     *
     * @implNote This implementation walks the
     * {@code data.xwa2_group_query_by_id} envelope and returns
     * {@link Optional#empty()} when any intermediate object is missing,
     * mirroring the WA Web {@code if (O == null) return null} guard in
     * {@code WAWebMexFetchGroupInfoJob}.
     *
     * @param json the UTF-8 encoded JSON payload
     * @return an {@link Optional} containing the parsed response, or empty
     *         if the {@code data.xwa2_group_query_by_id} envelope is absent
     */
    private static Optional<FetchGroupInfoMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("xwa2_group_query_by_id");
        if (root == null) {
            return Optional.empty();
        }

        var id = root.getString("id");
        var creationTime = root.getLong("creation_time");
        var creator = Creator.of(root.getJSONObject("creator")).orElse(null);
        var state = root.getString("state");
        var subject = Subject.of(root.getJSONObject("subject")).orElse(null);
        var description = Description.of(root.getJSONObject("description")).orElse(null);
        var participants = Participants.of(root.getJSONObject("participants")).orElse(null);
        var totalParticipantsCount = root.getLong("total_participants_count");
        var missingParticipantIdentification = root.getString("missing_participant_identification");
        var properties = Properties.of(root.getJSONObject("properties")).orElse(null);
        var membershipApprovalRequest = root.getString("membership_approval_request");

        return Optional.of(new FetchGroupInfoMexResponse(id, creationTime, creator, state, subject, description, participants, totalParticipantsCount, missingParticipantIdentification, properties, membershipApprovalRequest));
    }
}
