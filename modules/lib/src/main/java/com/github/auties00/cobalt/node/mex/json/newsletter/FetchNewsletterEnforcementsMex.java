package com.github.auties00.cobalt.node.mex.json.newsletter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.mex.json.MexJsonOperation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Fetches the moderation enforcement history for a newsletter.
 *
 * <p>The enforcement history includes profile picture deletions, account suspensions, violating message takedowns and geographical suspensions applied to the newsletter. Admins use it to audit moderation actions.
 *
 * @implNote WAWebMexFetchNewsletterEnforcementsJob: adapts the {@code mexFetchNewsletterEnforcements} GraphQL query,
 * which in WA Web is invoked via {@code WAWebMexClient.fetchQuery} and
 * whose response is unwrapped by the same module. Cobalt models the request
 * and response as sibling variants of a sealed interface rather than a
 * free-standing async function.
 */
@WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterEnforcementsJob")
public sealed interface FetchNewsletterEnforcementsMex extends MexJsonOperation permits FetchNewsletterEnforcementsMex.Request, FetchNewsletterEnforcementsMex.Response {
    /**
     * The numeric GraphQL query identifier assigned by the WhatsApp relay
     * to the {@code FetchNewsletterEnforcements} compiled query.
     *
     * @implNote WAWebMexFetchNewsletterEnforcementsJobQuery.graphql: corresponds to the compiled
     * document id registered for the {@code mexFetchNewsletterEnforcements} query.
     */
    String QUERY_ID = "25987882310910935";

    /**
     * The request variant of {@link FetchNewsletterEnforcementsMex} that serialises the
     * query variables and emits the outbound IQ stanza.
     *
     * @implNote WAWebMexFetchNewsletterEnforcementsJob.mexFetchNewsletterEnforcements: adapts the {@code variables}
     * object constructed inline in the JS implementation into a dedicated
     * Java class.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterEnforcementsJob")
    final class Request implements FetchNewsletterEnforcementsMex {
        private final String locale;
        private final String newsletterId;

        public Request(String locale, String newsletterId) {
            this.locale = locale;
            this.newsletterId = newsletterId;
        }

        /**
         * Builds the IQ stanza that dispatches this operation to the
         * WhatsApp relay.
         *
         * @implNote WAWebMexFetchNewsletterEnforcementsJob.mexFetchNewsletterEnforcements: WA Web constructs the
         * {@code variables} object inline and delegates to
         * {@code WAWebMexClient.fetchQuery}. Cobalt writes the JSON directly
         * via {@code fastjson2.JSONWriter} and wraps it through
         * {@link MexJsonOperation#createMexNode(String, String)}.
         * @return a {@link NodeBuilder} carrying the IQ envelope and the
         *         serialised GraphQL variables
         */
        @WhatsAppWebExport(moduleName = "WAWebMexFetchNewsletterEnforcementsJob", exports = "mexFetchNewsletterEnforcements",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public NodeBuilder toNode() {
            // WAWebMexFetchNewsletterEnforcementsJob.mexFetchNewsletterEnforcements
            // Opens a UTF-8 JSON writer that will serialise the GraphQL variables envelope
            try (var writer = JSONWriter.ofUTF8()) {
                // WAWebMexFetchNewsletterEnforcementsJob.mexFetchNewsletterEnforcements
                // Begins the outer envelope and the nested "variables" object consumed by WAWebMexClient.fetchQuery
                writer.startObject();
                writer.writeName("variables");
                writer.writeColon();
                writer.startObject();
                // WAWebMexFetchNewsletterEnforcementsJob.mexFetchNewsletterEnforcements
                // Emits the locale variable when present
                if (locale != null) {
                    writer.writeName("locale");
                    writer.writeColon();
                    writer.writeString(locale);
                }

                // WAWebMexFetchNewsletterEnforcementsJob.mexFetchNewsletterEnforcements
                // Emits the newsletter_id variable when present
                if (newsletterId != null) {
                    writer.writeName("newsletter_id");
                    writer.writeColon();
                    writer.writeString(newsletterId);
                }
                writer.endObject();
                writer.endObject();

                // ADAPTED: WAWebMexFetchNewsletterEnforcementsJob.mexFetchNewsletterEnforcements
                // Flushes the JSON buffer into a StringWriter and wraps it in the shared MEX IQ envelope
                try (var output = new StringWriter()) {
                    writer.flushTo(output);
                    return MexJsonOperation.createMexNode(QUERY_ID, output.toString());
                }
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }

    /**
     * The response variant of {@link FetchNewsletterEnforcementsMex} that exposes the data
     * returned by the server after a successful query.
     *
     * @implNote WAWebMexFetchNewsletterEnforcementsJob: adapts the JSON root returned by the GraphQL
     * query into a Java value object.
     */
    @WhatsAppWebModule(moduleName = "WAWebMexFetchNewsletterEnforcementsJob")
    final class Response implements FetchNewsletterEnforcementsMex {
        private final List<ProfilePictureDeletions> profilePictureDeletions;
        private final List<Suspensions> suspensions;
        private final List<ViolatingMessages> violatingMessages;
        private final List<Geosuspensions> geosuspensions;

        private Response(List<ProfilePictureDeletions> profilePictureDeletions, List<Suspensions> suspensions, List<ViolatingMessages> violatingMessages, List<Geosuspensions> geosuspensions) {
            this.profilePictureDeletions = profilePictureDeletions;
            this.suspensions = suspensions;
            this.violatingMessages = violatingMessages;
            this.geosuspensions = geosuspensions;
        }

        /**
         * Parses a MEX response from the given IQ response node.
         *
         * @implNote WAWebMexFetchNewsletterEnforcementsJob.mexFetchNewsletterEnforcements: WA Web relies on the
         * GraphQL client to unwrap the response. Cobalt performs the
         * unwrapping manually from the IQ {@code <result>} child.
         * @param node the IQ response node received from the relay
         * @return an {@link Optional} containing the parsed response, or
         *         empty if the node is missing a result payload
         */
        public static Optional<Response> of(Node node) {
            return node.getChild("result")
                    .flatMap(Node::toContentBytes)
                    .flatMap(Response::of);
        }

        /**
         * Returns the {@code profile_picture_deletions} field.
         *
         * @return the list of values, empty if absent
         */
        public List<ProfilePictureDeletions> profilePictureDeletions() {
            return profilePictureDeletions;
        }

        /**
         * Returns the {@code suspensions} field.
         *
         * @return the list of values, empty if absent
         */
        public List<Suspensions> suspensions() {
            return suspensions;
        }

        /**
         * Returns the {@code violating_messages} field.
         *
         * @return the list of values, empty if absent
         */
        public List<ViolatingMessages> violatingMessages() {
            return violatingMessages;
        }

        /**
         * Returns the {@code geosuspensions} field.
         *
         * @return the list of values, empty if absent
         */
        public List<Geosuspensions> geosuspensions() {
            return geosuspensions;
        }

        /**
         * A parsed {@code ProfilePictureDeletions} object.
         */
        public static final class ProfilePictureDeletions {
            private final Long enforcementCreationTime;
            private final Long appealCreationTime;
            private final String appealState;
            private final String enforcementViolationCategory;
            private final String enforcementSource;
            private final String enforcementId;
            private final EnforcementExtraData enforcementExtraData;
            private final EnforcementPolicyInformation enforcementPolicyInformation;

            private ProfilePictureDeletions(Long enforcementCreationTime, Long appealCreationTime, String appealState, String enforcementViolationCategory, String enforcementSource, String enforcementId, EnforcementExtraData enforcementExtraData, EnforcementPolicyInformation enforcementPolicyInformation) {
                this.enforcementCreationTime = enforcementCreationTime;
                this.appealCreationTime = appealCreationTime;
                this.appealState = appealState;
                this.enforcementViolationCategory = enforcementViolationCategory;
                this.enforcementSource = enforcementSource;
                this.enforcementId = enforcementId;
                this.enforcementExtraData = enforcementExtraData;
                this.enforcementPolicyInformation = enforcementPolicyInformation;
            }

            /**
             * Returns the {@code enforcement_creation_time} field.
             *
             * @return an {@link Optional} containing the value as an {@link Instant}, or empty if absent
             */
            public Optional<Instant> enforcementCreationTime() {
                return Optional.ofNullable(enforcementCreationTime).map(Instant::ofEpochSecond);
            }

            /**
             * Returns the {@code appeal_creation_time} field.
             *
             * @return an {@link Optional} containing the value as an {@link Instant}, or empty if absent
             */
            public Optional<Instant> appealCreationTime() {
                return Optional.ofNullable(appealCreationTime).map(Instant::ofEpochSecond);
            }

            /**
             * Returns the {@code appeal_state} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<String> appealState() {
                return Optional.ofNullable(appealState);
            }

            /**
             * Returns the {@code enforcement_violation_category} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<String> enforcementViolationCategory() {
                return Optional.ofNullable(enforcementViolationCategory);
            }

            /**
             * Returns the {@code enforcement_source} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<String> enforcementSource() {
                return Optional.ofNullable(enforcementSource);
            }

            /**
             * Returns the {@code enforcement_id} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<String> enforcementId() {
                return Optional.ofNullable(enforcementId);
            }

            /**
             * Returns the {@code enforcement_extra_data} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<EnforcementExtraData> enforcementExtraData() {
                return Optional.ofNullable(enforcementExtraData);
            }

            /**
             * Returns the {@code enforcement_policy_information} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<EnforcementPolicyInformation> enforcementPolicyInformation() {
                return Optional.ofNullable(enforcementPolicyInformation);
            }

            /**
             * A parsed {@code EnforcementExtraData} object.
             */
            public static final class EnforcementExtraData {
                private final IpViolationReportData ipViolationReportData;

                private EnforcementExtraData(IpViolationReportData ipViolationReportData) {
                    this.ipViolationReportData = ipViolationReportData;
                }

                /**
                 * Returns the {@code ip_violation_report_data} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<IpViolationReportData> ipViolationReportData() {
                    return Optional.ofNullable(ipViolationReportData);
                }

                /**
                 * A parsed {@code IpViolationReportData} object.
                 */
                public static final class IpViolationReportData {
                    private final String reportFbid;
                    private final String appealFormUrl;
                    private final String reporterEmail;
                    private final String reporterName;

                    private IpViolationReportData(String reportFbid, String appealFormUrl, String reporterEmail, String reporterName) {
                        this.reportFbid = reportFbid;
                        this.appealFormUrl = appealFormUrl;
                        this.reporterEmail = reporterEmail;
                        this.reporterName = reporterName;
                    }

                    /**
                     * Returns the {@code report_fbid} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> reportFbid() {
                        return Optional.ofNullable(reportFbid);
                    }

                    /**
                     * Returns the {@code appeal_form_url} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> appealFormUrl() {
                        return Optional.ofNullable(appealFormUrl);
                    }

                    /**
                     * Returns the {@code reporter_email} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> reporterEmail() {
                        return Optional.ofNullable(reporterEmail);
                    }

                    /**
                     * Returns the {@code reporter_name} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> reporterName() {
                        return Optional.ofNullable(reporterName);
                    }

                    /**
                     * Parses a {@code IpViolationReportData} from the given JSON object.
                     *
                     * @param obj the JSON object to parse
                     * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                     */
                    static Optional<IpViolationReportData> of(JSONObject obj) {
                        if (obj == null) {
                            return Optional.empty();
                        }

                        var reportFbid = obj.getString("report_fbid");
                        var appealFormUrl = obj.getString("appeal_form_url");
                        var reporterEmail = obj.getString("reporter_email");
                        var reporterName = obj.getString("reporter_name");
                        return Optional.of(new IpViolationReportData(reportFbid, appealFormUrl, reporterEmail, reporterName));
                    }

                    /**
                     * Parses a list of {@code IpViolationReportData} from the given JSON array.
                     *
                     * @param arr the JSON array to parse
                     * @return the list of parsed results, empty if {@code arr} is {@code null}
                     */
                    static List<IpViolationReportData> ofArray(JSONArray arr) {
                        if (arr == null) {
                            return List.of();
                        }

                        var result = new ArrayList<IpViolationReportData>(arr.size());
                        for (var i = 0; i < arr.size(); i++) {
                            of(arr.getJSONObject(i)).ifPresent(result::add);
                        }
                        return result;
                    }
                }

                /**
                 * Parses a {@code EnforcementExtraData} from the given JSON object.
                 *
                 * @param obj the JSON object to parse
                 * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                 */
                static Optional<EnforcementExtraData> of(JSONObject obj) {
                    if (obj == null) {
                        return Optional.empty();
                    }

                    var ipViolationReportData = IpViolationReportData.of(obj.getJSONObject("ip_violation_report_data")).orElse(null);
                    return Optional.of(new EnforcementExtraData(ipViolationReportData));
                }

                /**
                 * Parses a list of {@code EnforcementExtraData} from the given JSON array.
                 *
                 * @param arr the JSON array to parse
                 * @return the list of parsed results, empty if {@code arr} is {@code null}
                 */
                static List<EnforcementExtraData> ofArray(JSONArray arr) {
                    if (arr == null) {
                        return List.of();
                    }

                    var result = new ArrayList<EnforcementExtraData>(arr.size());
                    for (var i = 0; i < arr.size(); i++) {
                        of(arr.getJSONObject(i)).ifPresent(result::add);
                    }
                    return result;
                }
            }

            /**
             * A parsed {@code EnforcementPolicyInformation} object.
             */
            public static final class EnforcementPolicyInformation {
                private final String overview;
                private final String headline;
                private final String subtitle;
                private final String explanation;
                private final String adminDisclaimer;

                private EnforcementPolicyInformation(String overview, String headline, String subtitle, String explanation, String adminDisclaimer) {
                    this.overview = overview;
                    this.headline = headline;
                    this.subtitle = subtitle;
                    this.explanation = explanation;
                    this.adminDisclaimer = adminDisclaimer;
                }

                /**
                 * Returns the {@code overview} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> overview() {
                    return Optional.ofNullable(overview);
                }

                /**
                 * Returns the {@code headline} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> headline() {
                    return Optional.ofNullable(headline);
                }

                /**
                 * Returns the {@code subtitle} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> subtitle() {
                    return Optional.ofNullable(subtitle);
                }

                /**
                 * Returns the {@code explanation} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> explanation() {
                    return Optional.ofNullable(explanation);
                }

                /**
                 * Returns the {@code admin_disclaimer} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> adminDisclaimer() {
                    return Optional.ofNullable(adminDisclaimer);
                }

                /**
                 * Parses a {@code EnforcementPolicyInformation} from the given JSON object.
                 *
                 * @param obj the JSON object to parse
                 * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                 */
                static Optional<EnforcementPolicyInformation> of(JSONObject obj) {
                    if (obj == null) {
                        return Optional.empty();
                    }

                    var overview = obj.getString("overview");
                    var headline = obj.getString("headline");
                    var subtitle = obj.getString("subtitle");
                    var explanation = obj.getString("explanation");
                    var adminDisclaimer = obj.getString("admin_disclaimer");
                    return Optional.of(new EnforcementPolicyInformation(overview, headline, subtitle, explanation, adminDisclaimer));
                }

                /**
                 * Parses a list of {@code EnforcementPolicyInformation} from the given JSON array.
                 *
                 * @param arr the JSON array to parse
                 * @return the list of parsed results, empty if {@code arr} is {@code null}
                 */
                static List<EnforcementPolicyInformation> ofArray(JSONArray arr) {
                    if (arr == null) {
                        return List.of();
                    }

                    var result = new ArrayList<EnforcementPolicyInformation>(arr.size());
                    for (var i = 0; i < arr.size(); i++) {
                        of(arr.getJSONObject(i)).ifPresent(result::add);
                    }
                    return result;
                }
            }

            /**
             * Parses a {@code ProfilePictureDeletions} from the given JSON object.
             *
             * @param obj the JSON object to parse
             * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
             */
            static Optional<ProfilePictureDeletions> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var enforcementCreationTime = obj.getLong("enforcement_creation_time");
                var appealCreationTime = obj.getLong("appeal_creation_time");
                var appealState = obj.getString("appeal_state");
                var enforcementViolationCategory = obj.getString("enforcement_violation_category");
                var enforcementSource = obj.getString("enforcement_source");
                var enforcementId = obj.getString("enforcement_id");
                var enforcementExtraData = EnforcementExtraData.of(obj.getJSONObject("enforcement_extra_data")).orElse(null);
                var enforcementPolicyInformation = EnforcementPolicyInformation.of(obj.getJSONObject("enforcement_policy_information")).orElse(null);
                return Optional.of(new ProfilePictureDeletions(enforcementCreationTime, appealCreationTime, appealState, enforcementViolationCategory, enforcementSource, enforcementId, enforcementExtraData, enforcementPolicyInformation));
            }

            /**
             * Parses a list of {@code ProfilePictureDeletions} from the given JSON array.
             *
             * @param arr the JSON array to parse
             * @return the list of parsed results, empty if {@code arr} is {@code null}
             */
            static List<ProfilePictureDeletions> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<ProfilePictureDeletions>(arr.size());
                for (var i = 0; i < arr.size(); i++) {
                    of(arr.getJSONObject(i)).ifPresent(result::add);
                }
                return result;
            }
        }

        /**
         * A parsed {@code Suspensions} object.
         */
        public static final class Suspensions {
            private final Long appealCreationTime;
            private final Long enforcementCreationTime;
            private final String appealState;
            private final String enforcementViolationCategory;
            private final String enforcementId;
            private final String enforcementSource;
            private final EnforcementExtraData enforcementExtraData;
            private final EnforcementPolicyInformation enforcementPolicyInformation;

            private Suspensions(Long appealCreationTime, Long enforcementCreationTime, String appealState, String enforcementViolationCategory, String enforcementId, String enforcementSource, EnforcementExtraData enforcementExtraData, EnforcementPolicyInformation enforcementPolicyInformation) {
                this.appealCreationTime = appealCreationTime;
                this.enforcementCreationTime = enforcementCreationTime;
                this.appealState = appealState;
                this.enforcementViolationCategory = enforcementViolationCategory;
                this.enforcementId = enforcementId;
                this.enforcementSource = enforcementSource;
                this.enforcementExtraData = enforcementExtraData;
                this.enforcementPolicyInformation = enforcementPolicyInformation;
            }

            /**
             * Returns the {@code appeal_creation_time} field.
             *
             * @return an {@link Optional} containing the value as an {@link Instant}, or empty if absent
             */
            public Optional<Instant> appealCreationTime() {
                return Optional.ofNullable(appealCreationTime).map(Instant::ofEpochSecond);
            }

            /**
             * Returns the {@code enforcement_creation_time} field.
             *
             * @return an {@link Optional} containing the value as an {@link Instant}, or empty if absent
             */
            public Optional<Instant> enforcementCreationTime() {
                return Optional.ofNullable(enforcementCreationTime).map(Instant::ofEpochSecond);
            }

            /**
             * Returns the {@code appeal_state} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<String> appealState() {
                return Optional.ofNullable(appealState);
            }

            /**
             * Returns the {@code enforcement_violation_category} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<String> enforcementViolationCategory() {
                return Optional.ofNullable(enforcementViolationCategory);
            }

            /**
             * Returns the {@code enforcement_id} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<String> enforcementId() {
                return Optional.ofNullable(enforcementId);
            }

            /**
             * Returns the {@code enforcement_source} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<String> enforcementSource() {
                return Optional.ofNullable(enforcementSource);
            }

            /**
             * Returns the {@code enforcement_extra_data} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<EnforcementExtraData> enforcementExtraData() {
                return Optional.ofNullable(enforcementExtraData);
            }

            /**
             * Returns the {@code enforcement_policy_information} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<EnforcementPolicyInformation> enforcementPolicyInformation() {
                return Optional.ofNullable(enforcementPolicyInformation);
            }

            /**
             * A parsed {@code EnforcementExtraData} object.
             */
            public static final class EnforcementExtraData {
                private final IpViolationReportData ipViolationReportData;
                private final EnforcementTargetData enforcementTargetData;
                private final AppealExtraData appealExtraData;

                private EnforcementExtraData(IpViolationReportData ipViolationReportData, EnforcementTargetData enforcementTargetData, AppealExtraData appealExtraData) {
                    this.ipViolationReportData = ipViolationReportData;
                    this.enforcementTargetData = enforcementTargetData;
                    this.appealExtraData = appealExtraData;
                }

                /**
                 * Returns the {@code ip_violation_report_data} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<IpViolationReportData> ipViolationReportData() {
                    return Optional.ofNullable(ipViolationReportData);
                }

                /**
                 * Returns the {@code enforcement_target_data} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<EnforcementTargetData> enforcementTargetData() {
                    return Optional.ofNullable(enforcementTargetData);
                }

                /**
                 * Returns the {@code appeal_extra_data} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<AppealExtraData> appealExtraData() {
                    return Optional.ofNullable(appealExtraData);
                }

                /**
                 * A parsed {@code IpViolationReportData} object.
                 */
                public static final class IpViolationReportData {
                    private final String reportFbid;
                    private final String appealFormUrl;
                    private final String reporterEmail;
                    private final String reporterName;

                    private IpViolationReportData(String reportFbid, String appealFormUrl, String reporterEmail, String reporterName) {
                        this.reportFbid = reportFbid;
                        this.appealFormUrl = appealFormUrl;
                        this.reporterEmail = reporterEmail;
                        this.reporterName = reporterName;
                    }

                    /**
                     * Returns the {@code report_fbid} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> reportFbid() {
                        return Optional.ofNullable(reportFbid);
                    }

                    /**
                     * Returns the {@code appeal_form_url} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> appealFormUrl() {
                        return Optional.ofNullable(appealFormUrl);
                    }

                    /**
                     * Returns the {@code reporter_email} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> reporterEmail() {
                        return Optional.ofNullable(reporterEmail);
                    }

                    /**
                     * Returns the {@code reporter_name} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> reporterName() {
                        return Optional.ofNullable(reporterName);
                    }

                    /**
                     * Parses a {@code IpViolationReportData} from the given JSON object.
                     *
                     * @param obj the JSON object to parse
                     * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                     */
                    static Optional<IpViolationReportData> of(JSONObject obj) {
                        if (obj == null) {
                            return Optional.empty();
                        }

                        var reportFbid = obj.getString("report_fbid");
                        var appealFormUrl = obj.getString("appeal_form_url");
                        var reporterEmail = obj.getString("reporter_email");
                        var reporterName = obj.getString("reporter_name");
                        return Optional.of(new IpViolationReportData(reportFbid, appealFormUrl, reporterEmail, reporterName));
                    }

                    /**
                     * Parses a list of {@code IpViolationReportData} from the given JSON array.
                     *
                     * @param arr the JSON array to parse
                     * @return the list of parsed results, empty if {@code arr} is {@code null}
                     */
                    static List<IpViolationReportData> ofArray(JSONArray arr) {
                        if (arr == null) {
                            return List.of();
                        }

                        var result = new ArrayList<IpViolationReportData>(arr.size());
                        for (var i = 0; i < arr.size(); i++) {
                            of(arr.getJSONObject(i)).ifPresent(result::add);
                        }
                        return result;
                    }
                }

                /**
                 * A parsed {@code EnforcementTargetData} object.
                 */
                public static final class EnforcementTargetData {
                    private final String serverMsgId;

                    private EnforcementTargetData(String serverMsgId) {
                        this.serverMsgId = serverMsgId;
                    }

                    /**
                     * Returns the {@code server_msg_id} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> serverMsgId() {
                        return Optional.ofNullable(serverMsgId);
                    }

                    /**
                     * Parses a {@code EnforcementTargetData} from the given JSON object.
                     *
                     * @param obj the JSON object to parse
                     * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                     */
                    static Optional<EnforcementTargetData> of(JSONObject obj) {
                        if (obj == null) {
                            return Optional.empty();
                        }

                        var serverMsgId = obj.getString("server_msg_id");
                        return Optional.of(new EnforcementTargetData(serverMsgId));
                    }

                    /**
                     * Parses a list of {@code EnforcementTargetData} from the given JSON array.
                     *
                     * @param arr the JSON array to parse
                     * @return the list of parsed results, empty if {@code arr} is {@code null}
                     */
                    static List<EnforcementTargetData> ofArray(JSONArray arr) {
                        if (arr == null) {
                            return List.of();
                        }

                        var result = new ArrayList<EnforcementTargetData>(arr.size());
                        for (var i = 0; i < arr.size(); i++) {
                            of(arr.getJSONObject(i)).ifPresent(result::add);
                        }
                        return result;
                    }
                }

                /**
                 * A parsed {@code AppealExtraData} object.
                 */
                public static final class AppealExtraData {
                    private final String appealFormUrl;

                    private AppealExtraData(String appealFormUrl) {
                        this.appealFormUrl = appealFormUrl;
                    }

                    /**
                     * Returns the {@code appeal_form_url} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> appealFormUrl() {
                        return Optional.ofNullable(appealFormUrl);
                    }

                    /**
                     * Parses a {@code AppealExtraData} from the given JSON object.
                     *
                     * @param obj the JSON object to parse
                     * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                     */
                    static Optional<AppealExtraData> of(JSONObject obj) {
                        if (obj == null) {
                            return Optional.empty();
                        }

                        var appealFormUrl = obj.getString("appeal_form_url");
                        return Optional.of(new AppealExtraData(appealFormUrl));
                    }

                    /**
                     * Parses a list of {@code AppealExtraData} from the given JSON array.
                     *
                     * @param arr the JSON array to parse
                     * @return the list of parsed results, empty if {@code arr} is {@code null}
                     */
                    static List<AppealExtraData> ofArray(JSONArray arr) {
                        if (arr == null) {
                            return List.of();
                        }

                        var result = new ArrayList<AppealExtraData>(arr.size());
                        for (var i = 0; i < arr.size(); i++) {
                            of(arr.getJSONObject(i)).ifPresent(result::add);
                        }
                        return result;
                    }
                }

                /**
                 * Parses a {@code EnforcementExtraData} from the given JSON object.
                 *
                 * @param obj the JSON object to parse
                 * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                 */
                static Optional<EnforcementExtraData> of(JSONObject obj) {
                    if (obj == null) {
                        return Optional.empty();
                    }

                    var ipViolationReportData = IpViolationReportData.of(obj.getJSONObject("ip_violation_report_data")).orElse(null);
                    var enforcementTargetData = EnforcementTargetData.of(obj.getJSONObject("enforcement_target_data")).orElse(null);
                    var appealExtraData = AppealExtraData.of(obj.getJSONObject("appeal_extra_data")).orElse(null);
                    return Optional.of(new EnforcementExtraData(ipViolationReportData, enforcementTargetData, appealExtraData));
                }

                /**
                 * Parses a list of {@code EnforcementExtraData} from the given JSON array.
                 *
                 * @param arr the JSON array to parse
                 * @return the list of parsed results, empty if {@code arr} is {@code null}
                 */
                static List<EnforcementExtraData> ofArray(JSONArray arr) {
                    if (arr == null) {
                        return List.of();
                    }

                    var result = new ArrayList<EnforcementExtraData>(arr.size());
                    for (var i = 0; i < arr.size(); i++) {
                        of(arr.getJSONObject(i)).ifPresent(result::add);
                    }
                    return result;
                }
            }

            /**
             * A parsed {@code EnforcementPolicyInformation} object.
             */
            public static final class EnforcementPolicyInformation {
                private final String overview;
                private final String headline;
                private final String subtitle;
                private final String explanation;
                private final String adminDisclaimer;

                private EnforcementPolicyInformation(String overview, String headline, String subtitle, String explanation, String adminDisclaimer) {
                    this.overview = overview;
                    this.headline = headline;
                    this.subtitle = subtitle;
                    this.explanation = explanation;
                    this.adminDisclaimer = adminDisclaimer;
                }

                /**
                 * Returns the {@code overview} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> overview() {
                    return Optional.ofNullable(overview);
                }

                /**
                 * Returns the {@code headline} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> headline() {
                    return Optional.ofNullable(headline);
                }

                /**
                 * Returns the {@code subtitle} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> subtitle() {
                    return Optional.ofNullable(subtitle);
                }

                /**
                 * Returns the {@code explanation} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> explanation() {
                    return Optional.ofNullable(explanation);
                }

                /**
                 * Returns the {@code admin_disclaimer} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> adminDisclaimer() {
                    return Optional.ofNullable(adminDisclaimer);
                }

                /**
                 * Parses a {@code EnforcementPolicyInformation} from the given JSON object.
                 *
                 * @param obj the JSON object to parse
                 * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                 */
                static Optional<EnforcementPolicyInformation> of(JSONObject obj) {
                    if (obj == null) {
                        return Optional.empty();
                    }

                    var overview = obj.getString("overview");
                    var headline = obj.getString("headline");
                    var subtitle = obj.getString("subtitle");
                    var explanation = obj.getString("explanation");
                    var adminDisclaimer = obj.getString("admin_disclaimer");
                    return Optional.of(new EnforcementPolicyInformation(overview, headline, subtitle, explanation, adminDisclaimer));
                }

                /**
                 * Parses a list of {@code EnforcementPolicyInformation} from the given JSON array.
                 *
                 * @param arr the JSON array to parse
                 * @return the list of parsed results, empty if {@code arr} is {@code null}
                 */
                static List<EnforcementPolicyInformation> ofArray(JSONArray arr) {
                    if (arr == null) {
                        return List.of();
                    }

                    var result = new ArrayList<EnforcementPolicyInformation>(arr.size());
                    for (var i = 0; i < arr.size(); i++) {
                        of(arr.getJSONObject(i)).ifPresent(result::add);
                    }
                    return result;
                }
            }

            /**
             * Parses a {@code Suspensions} from the given JSON object.
             *
             * @param obj the JSON object to parse
             * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
             */
            static Optional<Suspensions> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var appealCreationTime = obj.getLong("appeal_creation_time");
                var enforcementCreationTime = obj.getLong("enforcement_creation_time");
                var appealState = obj.getString("appeal_state");
                var enforcementViolationCategory = obj.getString("enforcement_violation_category");
                var enforcementId = obj.getString("enforcement_id");
                var enforcementSource = obj.getString("enforcement_source");
                var enforcementExtraData = EnforcementExtraData.of(obj.getJSONObject("enforcement_extra_data")).orElse(null);
                var enforcementPolicyInformation = EnforcementPolicyInformation.of(obj.getJSONObject("enforcement_policy_information")).orElse(null);
                return Optional.of(new Suspensions(appealCreationTime, enforcementCreationTime, appealState, enforcementViolationCategory, enforcementId, enforcementSource, enforcementExtraData, enforcementPolicyInformation));
            }

            /**
             * Parses a list of {@code Suspensions} from the given JSON array.
             *
             * @param arr the JSON array to parse
             * @return the list of parsed results, empty if {@code arr} is {@code null}
             */
            static List<Suspensions> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<Suspensions>(arr.size());
                for (var i = 0; i < arr.size(); i++) {
                    of(arr.getJSONObject(i)).ifPresent(result::add);
                }
                return result;
            }
        }

        /**
         * A parsed {@code ViolatingMessages} object.
         */
        public static final class ViolatingMessages {
            private final BaseEnforcementData baseEnforcementData;
            private final ContentData contentData;

            private ViolatingMessages(BaseEnforcementData baseEnforcementData, ContentData contentData) {
                this.baseEnforcementData = baseEnforcementData;
                this.contentData = contentData;
            }

            /**
             * Returns the {@code base_enforcement_data} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<BaseEnforcementData> baseEnforcementData() {
                return Optional.ofNullable(baseEnforcementData);
            }

            /**
             * Returns the {@code content_data} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<ContentData> contentData() {
                return Optional.ofNullable(contentData);
            }

            /**
             * A parsed {@code content_data} polymorphic object.
             *
             * @implNote WAWebMexFetchNewsletterEnforcementsJobQuery.graphql: adapts the inline fragments on the
             * {@code content_data} {@code LinkedField}, which selects
             * {@code server_msg_id} from {@code XWA2ChannelServerMsgData} or
             * {@code server_id} from {@code XWA2ChannelStatusData}.
             */
            public static final class ContentData {
                private final String typename;
                private final String serverMsgId;
                private final String serverId;

                private ContentData(String typename, String serverMsgId, String serverId) {
                    this.typename = typename;
                    this.serverMsgId = serverMsgId;
                    this.serverId = serverId;
                }

                /**
                 * Returns the {@code __typename} discriminator.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> typename() {
                    return Optional.ofNullable(typename);
                }

                /**
                 * Returns the {@code server_msg_id} field from the
                 * {@code XWA2ChannelServerMsgData} inline fragment.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> serverMsgId() {
                    return Optional.ofNullable(serverMsgId);
                }

                /**
                 * Returns the {@code server_id} field from the
                 * {@code XWA2ChannelStatusData} inline fragment.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> serverId() {
                    return Optional.ofNullable(serverId);
                }

                /**
                 * Parses a {@code ContentData} from the given JSON object.
                 *
                 * @param obj the JSON object to parse
                 * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                 */
                static Optional<ContentData> of(JSONObject obj) {
                    if (obj == null) {
                        return Optional.empty();
                    }

                    var typename = obj.getString("__typename");
                    var serverMsgId = obj.getString("server_msg_id");
                    var serverId = obj.getString("server_id");
                    return Optional.of(new ContentData(typename, serverMsgId, serverId));
                }

                /**
                 * Parses a list of {@code ContentData} from the given JSON array.
                 *
                 * @param arr the JSON array to parse
                 * @return the list of parsed results, empty if {@code arr} is {@code null}
                 */
                static List<ContentData> ofArray(JSONArray arr) {
                    if (arr == null) {
                        return List.of();
                    }

                    var result = new ArrayList<ContentData>(arr.size());
                    for (var i = 0; i < arr.size(); i++) {
                        of(arr.getJSONObject(i)).ifPresent(result::add);
                    }
                    return result;
                }
            }

            /**
             * A parsed {@code BaseEnforcementData} object.
             */
            public static final class BaseEnforcementData {
                private final Long enforcementCreationTime;
                private final Long appealCreationTime;
                private final String appealState;
                private final String enforcementId;
                private final String enforcementViolationCategory;
                private final String enforcementSource;
                private final EnforcementExtraData enforcementExtraData;
                private final EnforcementPolicyInformation enforcementPolicyInformation;

                private BaseEnforcementData(Long enforcementCreationTime, Long appealCreationTime, String appealState, String enforcementId, String enforcementViolationCategory, String enforcementSource, EnforcementExtraData enforcementExtraData, EnforcementPolicyInformation enforcementPolicyInformation) {
                    this.enforcementCreationTime = enforcementCreationTime;
                    this.appealCreationTime = appealCreationTime;
                    this.appealState = appealState;
                    this.enforcementId = enforcementId;
                    this.enforcementViolationCategory = enforcementViolationCategory;
                    this.enforcementSource = enforcementSource;
                    this.enforcementExtraData = enforcementExtraData;
                    this.enforcementPolicyInformation = enforcementPolicyInformation;
                }

                /**
                 * Returns the {@code enforcement_creation_time} field.
                 *
                 * @return an {@link Optional} containing the value as an {@link Instant}, or empty if absent
                 */
                public Optional<Instant> enforcementCreationTime() {
                    return Optional.ofNullable(enforcementCreationTime).map(Instant::ofEpochSecond);
                }

                /**
                 * Returns the {@code appeal_creation_time} field.
                 *
                 * @return an {@link Optional} containing the value as an {@link Instant}, or empty if absent
                 */
                public Optional<Instant> appealCreationTime() {
                    return Optional.ofNullable(appealCreationTime).map(Instant::ofEpochSecond);
                }

                /**
                 * Returns the {@code appeal_state} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> appealState() {
                    return Optional.ofNullable(appealState);
                }

                /**
                 * Returns the {@code enforcement_id} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> enforcementId() {
                    return Optional.ofNullable(enforcementId);
                }

                /**
                 * Returns the {@code enforcement_violation_category} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> enforcementViolationCategory() {
                    return Optional.ofNullable(enforcementViolationCategory);
                }

                /**
                 * Returns the {@code enforcement_source} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> enforcementSource() {
                    return Optional.ofNullable(enforcementSource);
                }

                /**
                 * Returns the {@code enforcement_extra_data} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<EnforcementExtraData> enforcementExtraData() {
                    return Optional.ofNullable(enforcementExtraData);
                }

                /**
                 * Returns the {@code enforcement_policy_information} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<EnforcementPolicyInformation> enforcementPolicyInformation() {
                    return Optional.ofNullable(enforcementPolicyInformation);
                }

                /**
                 * A parsed {@code EnforcementExtraData} object.
                 */
                public static final class EnforcementExtraData {
                    private final IpViolationReportData ipViolationReportData;

                    private EnforcementExtraData(IpViolationReportData ipViolationReportData) {
                        this.ipViolationReportData = ipViolationReportData;
                    }

                    /**
                     * Returns the {@code ip_violation_report_data} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<IpViolationReportData> ipViolationReportData() {
                        return Optional.ofNullable(ipViolationReportData);
                    }

                    /**
                     * A parsed {@code IpViolationReportData} object.
                     */
                    public static final class IpViolationReportData {
                        private final String reportFbid;
                        private final String appealFormUrl;
                        private final String reporterEmail;
                        private final String reporterName;

                        private IpViolationReportData(String reportFbid, String appealFormUrl, String reporterEmail, String reporterName) {
                            this.reportFbid = reportFbid;
                            this.appealFormUrl = appealFormUrl;
                            this.reporterEmail = reporterEmail;
                            this.reporterName = reporterName;
                        }

                        /**
                         * Returns the {@code report_fbid} field.
                         *
                         * @return an {@link Optional} containing the value, or empty if absent
                         */
                        public Optional<String> reportFbid() {
                            return Optional.ofNullable(reportFbid);
                        }

                        /**
                         * Returns the {@code appeal_form_url} field.
                         *
                         * @return an {@link Optional} containing the value, or empty if absent
                         */
                        public Optional<String> appealFormUrl() {
                            return Optional.ofNullable(appealFormUrl);
                        }

                        /**
                         * Returns the {@code reporter_email} field.
                         *
                         * @return an {@link Optional} containing the value, or empty if absent
                         */
                        public Optional<String> reporterEmail() {
                            return Optional.ofNullable(reporterEmail);
                        }

                        /**
                         * Returns the {@code reporter_name} field.
                         *
                         * @return an {@link Optional} containing the value, or empty if absent
                         */
                        public Optional<String> reporterName() {
                            return Optional.ofNullable(reporterName);
                        }

                        /**
                         * Parses a {@code IpViolationReportData} from the given JSON object.
                         *
                         * @param obj the JSON object to parse
                         * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                         */
                        static Optional<IpViolationReportData> of(JSONObject obj) {
                            if (obj == null) {
                                return Optional.empty();
                            }

                            var reportFbid = obj.getString("report_fbid");
                            var appealFormUrl = obj.getString("appeal_form_url");
                            var reporterEmail = obj.getString("reporter_email");
                            var reporterName = obj.getString("reporter_name");
                            return Optional.of(new IpViolationReportData(reportFbid, appealFormUrl, reporterEmail, reporterName));
                        }

                        /**
                         * Parses a list of {@code IpViolationReportData} from the given JSON array.
                         *
                         * @param arr the JSON array to parse
                         * @return the list of parsed results, empty if {@code arr} is {@code null}
                         */
                        static List<IpViolationReportData> ofArray(JSONArray arr) {
                            if (arr == null) {
                                return List.of();
                            }

                            var result = new ArrayList<IpViolationReportData>(arr.size());
                            for (var i = 0; i < arr.size(); i++) {
                                of(arr.getJSONObject(i)).ifPresent(result::add);
                            }
                            return result;
                        }
                    }

                    /**
                     * Parses a {@code EnforcementExtraData} from the given JSON object.
                     *
                     * @param obj the JSON object to parse
                     * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                     */
                    static Optional<EnforcementExtraData> of(JSONObject obj) {
                        if (obj == null) {
                            return Optional.empty();
                        }

                        var ipViolationReportData = IpViolationReportData.of(obj.getJSONObject("ip_violation_report_data")).orElse(null);
                        return Optional.of(new EnforcementExtraData(ipViolationReportData));
                    }

                    /**
                     * Parses a list of {@code EnforcementExtraData} from the given JSON array.
                     *
                     * @param arr the JSON array to parse
                     * @return the list of parsed results, empty if {@code arr} is {@code null}
                     */
                    static List<EnforcementExtraData> ofArray(JSONArray arr) {
                        if (arr == null) {
                            return List.of();
                        }

                        var result = new ArrayList<EnforcementExtraData>(arr.size());
                        for (var i = 0; i < arr.size(); i++) {
                            of(arr.getJSONObject(i)).ifPresent(result::add);
                        }
                        return result;
                    }
                }

                /**
                 * A parsed {@code EnforcementPolicyInformation} object.
                 */
                public static final class EnforcementPolicyInformation {
                    private final String overview;
                    private final String headline;
                    private final String subtitle;
                    private final String explanation;
                    private final String adminDisclaimer;

                    private EnforcementPolicyInformation(String overview, String headline, String subtitle, String explanation, String adminDisclaimer) {
                        this.overview = overview;
                        this.headline = headline;
                        this.subtitle = subtitle;
                        this.explanation = explanation;
                        this.adminDisclaimer = adminDisclaimer;
                    }

                    /**
                     * Returns the {@code overview} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> overview() {
                        return Optional.ofNullable(overview);
                    }

                    /**
                     * Returns the {@code headline} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> headline() {
                        return Optional.ofNullable(headline);
                    }

                    /**
                     * Returns the {@code subtitle} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> subtitle() {
                        return Optional.ofNullable(subtitle);
                    }

                    /**
                     * Returns the {@code explanation} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> explanation() {
                        return Optional.ofNullable(explanation);
                    }

                    /**
                     * Returns the {@code admin_disclaimer} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> adminDisclaimer() {
                        return Optional.ofNullable(adminDisclaimer);
                    }

                    /**
                     * Parses a {@code EnforcementPolicyInformation} from the given JSON object.
                     *
                     * @param obj the JSON object to parse
                     * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                     */
                    static Optional<EnforcementPolicyInformation> of(JSONObject obj) {
                        if (obj == null) {
                            return Optional.empty();
                        }

                        var overview = obj.getString("overview");
                        var headline = obj.getString("headline");
                        var subtitle = obj.getString("subtitle");
                        var explanation = obj.getString("explanation");
                        var adminDisclaimer = obj.getString("admin_disclaimer");
                        return Optional.of(new EnforcementPolicyInformation(overview, headline, subtitle, explanation, adminDisclaimer));
                    }

                    /**
                     * Parses a list of {@code EnforcementPolicyInformation} from the given JSON array.
                     *
                     * @param arr the JSON array to parse
                     * @return the list of parsed results, empty if {@code arr} is {@code null}
                     */
                    static List<EnforcementPolicyInformation> ofArray(JSONArray arr) {
                        if (arr == null) {
                            return List.of();
                        }

                        var result = new ArrayList<EnforcementPolicyInformation>(arr.size());
                        for (var i = 0; i < arr.size(); i++) {
                            of(arr.getJSONObject(i)).ifPresent(result::add);
                        }
                        return result;
                    }
                }

                /**
                 * Parses a {@code BaseEnforcementData} from the given JSON object.
                 *
                 * @param obj the JSON object to parse
                 * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                 */
                static Optional<BaseEnforcementData> of(JSONObject obj) {
                    if (obj == null) {
                        return Optional.empty();
                    }

                    var enforcementCreationTime = obj.getLong("enforcement_creation_time");
                    var appealCreationTime = obj.getLong("appeal_creation_time");
                    var appealState = obj.getString("appeal_state");
                    var enforcementId = obj.getString("enforcement_id");
                    var enforcementViolationCategory = obj.getString("enforcement_violation_category");
                    var enforcementSource = obj.getString("enforcement_source");
                    var enforcementExtraData = EnforcementExtraData.of(obj.getJSONObject("enforcement_extra_data")).orElse(null);
                    var enforcementPolicyInformation = EnforcementPolicyInformation.of(obj.getJSONObject("enforcement_policy_information")).orElse(null);
                    return Optional.of(new BaseEnforcementData(enforcementCreationTime, appealCreationTime, appealState, enforcementId, enforcementViolationCategory, enforcementSource, enforcementExtraData, enforcementPolicyInformation));
                }

                /**
                 * Parses a list of {@code BaseEnforcementData} from the given JSON array.
                 *
                 * @param arr the JSON array to parse
                 * @return the list of parsed results, empty if {@code arr} is {@code null}
                 */
                static List<BaseEnforcementData> ofArray(JSONArray arr) {
                    if (arr == null) {
                        return List.of();
                    }

                    var result = new ArrayList<BaseEnforcementData>(arr.size());
                    for (var i = 0; i < arr.size(); i++) {
                        of(arr.getJSONObject(i)).ifPresent(result::add);
                    }
                    return result;
                }
            }

            /**
             * Parses a {@code ViolatingMessages} from the given JSON object.
             *
             * @param obj the JSON object to parse
             * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
             */
            static Optional<ViolatingMessages> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var baseEnforcementData = BaseEnforcementData.of(obj.getJSONObject("base_enforcement_data")).orElse(null);
                var contentData = ContentData.of(obj.getJSONObject("content_data")).orElse(null);
                return Optional.of(new ViolatingMessages(baseEnforcementData, contentData));
            }

            /**
             * Parses a list of {@code ViolatingMessages} from the given JSON array.
             *
             * @param arr the JSON array to parse
             * @return the list of parsed results, empty if {@code arr} is {@code null}
             */
            static List<ViolatingMessages> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<ViolatingMessages>(arr.size());
                for (var i = 0; i < arr.size(); i++) {
                    of(arr.getJSONObject(i)).ifPresent(result::add);
                }
                return result;
            }
        }

        /**
         * A parsed {@code Geosuspensions} object.
         */
        public static final class Geosuspensions {
            private final BaseEnforcementData baseEnforcementData;
            private final List<String> countryCodes;

            private Geosuspensions(BaseEnforcementData baseEnforcementData, List<String> countryCodes) {
                this.baseEnforcementData = baseEnforcementData;
                this.countryCodes = countryCodes;
            }

            /**
             * Returns the {@code base_enforcement_data} field.
             *
             * @return an {@link Optional} containing the value, or empty if absent
             */
            public Optional<BaseEnforcementData> baseEnforcementData() {
                return Optional.ofNullable(baseEnforcementData);
            }

            /**
             * Returns the {@code country_codes} field.
             *
             * @implNote WAWebMexFetchNewsletterEnforcementsJob.mexFetchNewsletterEnforcements: WA Web maps each
             * raw 2-letter ISO code through {@code WAWebAsISOCountryCode.asISOCountryCode}
             * and enriches it with a localized country name from
             * {@code WAWebLocaleModules.getCountryData()}. Cobalt exposes the
             * raw codes only; locale enrichment is a UI concern left to callers.
             * @return the list of raw country codes, empty if absent
             */
            public List<String> countryCodes() {
                return countryCodes;
            }

            /**
             * A parsed {@code BaseEnforcementData} object.
             */
            public static final class BaseEnforcementData {
                private final Long enforcementCreationTime;
                private final Long appealCreationTime;
                private final String appealState;
                private final String enforcementId;
                private final String enforcementViolationCategory;
                private final String enforcementSource;
                private final EnforcementExtraData enforcementExtraData;
                private final EnforcementPolicyInformation enforcementPolicyInformation;

                private BaseEnforcementData(Long enforcementCreationTime, Long appealCreationTime, String appealState, String enforcementId, String enforcementViolationCategory, String enforcementSource, EnforcementExtraData enforcementExtraData, EnforcementPolicyInformation enforcementPolicyInformation) {
                    this.enforcementCreationTime = enforcementCreationTime;
                    this.appealCreationTime = appealCreationTime;
                    this.appealState = appealState;
                    this.enforcementId = enforcementId;
                    this.enforcementViolationCategory = enforcementViolationCategory;
                    this.enforcementSource = enforcementSource;
                    this.enforcementExtraData = enforcementExtraData;
                    this.enforcementPolicyInformation = enforcementPolicyInformation;
                }

                /**
                 * Returns the {@code enforcement_creation_time} field.
                 *
                 * @return an {@link Optional} containing the value as an {@link Instant}, or empty if absent
                 */
                public Optional<Instant> enforcementCreationTime() {
                    return Optional.ofNullable(enforcementCreationTime).map(Instant::ofEpochSecond);
                }

                /**
                 * Returns the {@code appeal_creation_time} field.
                 *
                 * @return an {@link Optional} containing the value as an {@link Instant}, or empty if absent
                 */
                public Optional<Instant> appealCreationTime() {
                    return Optional.ofNullable(appealCreationTime).map(Instant::ofEpochSecond);
                }

                /**
                 * Returns the {@code appeal_state} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> appealState() {
                    return Optional.ofNullable(appealState);
                }

                /**
                 * Returns the {@code enforcement_id} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> enforcementId() {
                    return Optional.ofNullable(enforcementId);
                }

                /**
                 * Returns the {@code enforcement_violation_category} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> enforcementViolationCategory() {
                    return Optional.ofNullable(enforcementViolationCategory);
                }

                /**
                 * Returns the {@code enforcement_source} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<String> enforcementSource() {
                    return Optional.ofNullable(enforcementSource);
                }

                /**
                 * Returns the {@code enforcement_extra_data} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<EnforcementExtraData> enforcementExtraData() {
                    return Optional.ofNullable(enforcementExtraData);
                }

                /**
                 * Returns the {@code enforcement_policy_information} field.
                 *
                 * @return an {@link Optional} containing the value, or empty if absent
                 */
                public Optional<EnforcementPolicyInformation> enforcementPolicyInformation() {
                    return Optional.ofNullable(enforcementPolicyInformation);
                }

                /**
                 * A parsed {@code EnforcementExtraData} object.
                 */
                public static final class EnforcementExtraData {
                    private final IpViolationReportData ipViolationReportData;
                    private final EnforcementTargetData enforcementTargetData;
                    private final AppealExtraData appealExtraData;
                    private final EnforcingEntityData enforcingEntityData;
                    private final String enforcementOriginWorkflow;
                    private final String enforcementOriginLegalBasis;

                    private EnforcementExtraData(IpViolationReportData ipViolationReportData, EnforcementTargetData enforcementTargetData, AppealExtraData appealExtraData, EnforcingEntityData enforcingEntityData, String enforcementOriginWorkflow, String enforcementOriginLegalBasis) {
                        this.ipViolationReportData = ipViolationReportData;
                        this.enforcementTargetData = enforcementTargetData;
                        this.appealExtraData = appealExtraData;
                        this.enforcingEntityData = enforcingEntityData;
                        this.enforcementOriginWorkflow = enforcementOriginWorkflow;
                        this.enforcementOriginLegalBasis = enforcementOriginLegalBasis;
                    }

                    /**
                     * Returns the {@code ip_violation_report_data} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<IpViolationReportData> ipViolationReportData() {
                        return Optional.ofNullable(ipViolationReportData);
                    }

                    /**
                     * Returns the {@code enforcement_target_data} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<EnforcementTargetData> enforcementTargetData() {
                        return Optional.ofNullable(enforcementTargetData);
                    }

                    /**
                     * Returns the {@code appeal_extra_data} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<AppealExtraData> appealExtraData() {
                        return Optional.ofNullable(appealExtraData);
                    }

                    /**
                     * Returns the {@code enforcing_entity_data} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<EnforcingEntityData> enforcingEntityData() {
                        return Optional.ofNullable(enforcingEntityData);
                    }

                    /**
                     * Returns the {@code enforcement_origin_workflow} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> enforcementOriginWorkflow() {
                        return Optional.ofNullable(enforcementOriginWorkflow);
                    }

                    /**
                     * Returns the {@code enforcement_origin_legal_basis} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> enforcementOriginLegalBasis() {
                        return Optional.ofNullable(enforcementOriginLegalBasis);
                    }

                    /**
                     * A parsed {@code IpViolationReportData} object.
                     */
                    public static final class IpViolationReportData {
                        private final String reportFbid;
                        private final String appealFormUrl;
                        private final String reporterEmail;
                        private final String reporterName;

                        private IpViolationReportData(String reportFbid, String appealFormUrl, String reporterEmail, String reporterName) {
                            this.reportFbid = reportFbid;
                            this.appealFormUrl = appealFormUrl;
                            this.reporterEmail = reporterEmail;
                            this.reporterName = reporterName;
                        }

                        /**
                         * Returns the {@code report_fbid} field.
                         *
                         * @return an {@link Optional} containing the value, or empty if absent
                         */
                        public Optional<String> reportFbid() {
                            return Optional.ofNullable(reportFbid);
                        }

                        /**
                         * Returns the {@code appeal_form_url} field.
                         *
                         * @return an {@link Optional} containing the value, or empty if absent
                         */
                        public Optional<String> appealFormUrl() {
                            return Optional.ofNullable(appealFormUrl);
                        }

                        /**
                         * Returns the {@code reporter_email} field.
                         *
                         * @return an {@link Optional} containing the value, or empty if absent
                         */
                        public Optional<String> reporterEmail() {
                            return Optional.ofNullable(reporterEmail);
                        }

                        /**
                         * Returns the {@code reporter_name} field.
                         *
                         * @return an {@link Optional} containing the value, or empty if absent
                         */
                        public Optional<String> reporterName() {
                            return Optional.ofNullable(reporterName);
                        }

                        /**
                         * Parses a {@code IpViolationReportData} from the given JSON object.
                         *
                         * @param obj the JSON object to parse
                         * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                         */
                        static Optional<IpViolationReportData> of(JSONObject obj) {
                            if (obj == null) {
                                return Optional.empty();
                            }

                            var reportFbid = obj.getString("report_fbid");
                            var appealFormUrl = obj.getString("appeal_form_url");
                            var reporterEmail = obj.getString("reporter_email");
                            var reporterName = obj.getString("reporter_name");
                            return Optional.of(new IpViolationReportData(reportFbid, appealFormUrl, reporterEmail, reporterName));
                        }

                        /**
                         * Parses a list of {@code IpViolationReportData} from the given JSON array.
                         *
                         * @param arr the JSON array to parse
                         * @return the list of parsed results, empty if {@code arr} is {@code null}
                         */
                        static List<IpViolationReportData> ofArray(JSONArray arr) {
                            if (arr == null) {
                                return List.of();
                            }

                            var result = new ArrayList<IpViolationReportData>(arr.size());
                            for (var i = 0; i < arr.size(); i++) {
                                of(arr.getJSONObject(i)).ifPresent(result::add);
                            }
                            return result;
                        }
                    }

                    /**
                     * A parsed {@code EnforcementTargetData} object.
                     */
                    public static final class EnforcementTargetData {
                        private final String serverMsgId;

                        private EnforcementTargetData(String serverMsgId) {
                            this.serverMsgId = serverMsgId;
                        }

                        /**
                         * Returns the {@code server_msg_id} field.
                         *
                         * @return an {@link Optional} containing the value, or empty if absent
                         */
                        public Optional<String> serverMsgId() {
                            return Optional.ofNullable(serverMsgId);
                        }

                        /**
                         * Parses a {@code EnforcementTargetData} from the given JSON object.
                         *
                         * @param obj the JSON object to parse
                         * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                         */
                        static Optional<EnforcementTargetData> of(JSONObject obj) {
                            if (obj == null) {
                                return Optional.empty();
                            }

                            var serverMsgId = obj.getString("server_msg_id");
                            return Optional.of(new EnforcementTargetData(serverMsgId));
                        }

                        /**
                         * Parses a list of {@code EnforcementTargetData} from the given JSON array.
                         *
                         * @param arr the JSON array to parse
                         * @return the list of parsed results, empty if {@code arr} is {@code null}
                         */
                        static List<EnforcementTargetData> ofArray(JSONArray arr) {
                            if (arr == null) {
                                return List.of();
                            }

                            var result = new ArrayList<EnforcementTargetData>(arr.size());
                            for (var i = 0; i < arr.size(); i++) {
                                of(arr.getJSONObject(i)).ifPresent(result::add);
                            }
                            return result;
                        }
                    }

                    /**
                     * A parsed {@code AppealExtraData} object.
                     */
                    public static final class AppealExtraData {
                        private final String appealFormUrl;

                        private AppealExtraData(String appealFormUrl) {
                            this.appealFormUrl = appealFormUrl;
                        }

                        /**
                         * Returns the {@code appeal_form_url} field.
                         *
                         * @return an {@link Optional} containing the value, or empty if absent
                         */
                        public Optional<String> appealFormUrl() {
                            return Optional.ofNullable(appealFormUrl);
                        }

                        /**
                         * Parses a {@code AppealExtraData} from the given JSON object.
                         *
                         * @param obj the JSON object to parse
                         * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                         */
                        static Optional<AppealExtraData> of(JSONObject obj) {
                            if (obj == null) {
                                return Optional.empty();
                            }

                            var appealFormUrl = obj.getString("appeal_form_url");
                            return Optional.of(new AppealExtraData(appealFormUrl));
                        }

                        /**
                         * Parses a list of {@code AppealExtraData} from the given JSON array.
                         *
                         * @param arr the JSON array to parse
                         * @return the list of parsed results, empty if {@code arr} is {@code null}
                         */
                        static List<AppealExtraData> ofArray(JSONArray arr) {
                            if (arr == null) {
                                return List.of();
                            }

                            var result = new ArrayList<AppealExtraData>(arr.size());
                            for (var i = 0; i < arr.size(); i++) {
                                of(arr.getJSONObject(i)).ifPresent(result::add);
                            }
                            return result;
                        }
                    }

                    /**
                     * A parsed {@code EnforcingEntityData} object.
                     */
                    public static final class EnforcingEntityData {
                        private final String name;

                        private EnforcingEntityData(String name) {
                            this.name = name;
                        }

                        /**
                         * Returns the {@code name} field.
                         *
                         * @return an {@link Optional} containing the value, or empty if absent
                         */
                        public Optional<String> name() {
                            return Optional.ofNullable(name);
                        }

                        /**
                         * Parses a {@code EnforcingEntityData} from the given JSON object.
                         *
                         * @param obj the JSON object to parse
                         * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                         */
                        static Optional<EnforcingEntityData> of(JSONObject obj) {
                            if (obj == null) {
                                return Optional.empty();
                            }

                            var name = obj.getString("name");
                            return Optional.of(new EnforcingEntityData(name));
                        }

                        /**
                         * Parses a list of {@code EnforcingEntityData} from the given JSON array.
                         *
                         * @param arr the JSON array to parse
                         * @return the list of parsed results, empty if {@code arr} is {@code null}
                         */
                        static List<EnforcingEntityData> ofArray(JSONArray arr) {
                            if (arr == null) {
                                return List.of();
                            }

                            var result = new ArrayList<EnforcingEntityData>(arr.size());
                            for (var i = 0; i < arr.size(); i++) {
                                of(arr.getJSONObject(i)).ifPresent(result::add);
                            }
                            return result;
                        }
                    }

                    /**
                     * Parses a {@code EnforcementExtraData} from the given JSON object.
                     *
                     * @param obj the JSON object to parse
                     * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                     */
                    static Optional<EnforcementExtraData> of(JSONObject obj) {
                        if (obj == null) {
                            return Optional.empty();
                        }

                        var ipViolationReportData = IpViolationReportData.of(obj.getJSONObject("ip_violation_report_data")).orElse(null);
                        var enforcementTargetData = EnforcementTargetData.of(obj.getJSONObject("enforcement_target_data")).orElse(null);
                        var appealExtraData = AppealExtraData.of(obj.getJSONObject("appeal_extra_data")).orElse(null);
                        var enforcingEntityData = EnforcingEntityData.of(obj.getJSONObject("enforcing_entity_data")).orElse(null);
                        var enforcementOriginWorkflow = obj.getString("enforcement_origin_workflow");
                        var enforcementOriginLegalBasis = obj.getString("enforcement_origin_legal_basis");
                        return Optional.of(new EnforcementExtraData(ipViolationReportData, enforcementTargetData, appealExtraData, enforcingEntityData, enforcementOriginWorkflow, enforcementOriginLegalBasis));
                    }

                    /**
                     * Parses a list of {@code EnforcementExtraData} from the given JSON array.
                     *
                     * @param arr the JSON array to parse
                     * @return the list of parsed results, empty if {@code arr} is {@code null}
                     */
                    static List<EnforcementExtraData> ofArray(JSONArray arr) {
                        if (arr == null) {
                            return List.of();
                        }

                        var result = new ArrayList<EnforcementExtraData>(arr.size());
                        for (var i = 0; i < arr.size(); i++) {
                            of(arr.getJSONObject(i)).ifPresent(result::add);
                        }
                        return result;
                    }
                }

                /**
                 * A parsed {@code EnforcementPolicyInformation} object.
                 */
                public static final class EnforcementPolicyInformation {
                    private final String overview;
                    private final String headline;
                    private final String subtitle;
                    private final String explanation;
                    private final String adminDisclaimer;

                    private EnforcementPolicyInformation(String overview, String headline, String subtitle, String explanation, String adminDisclaimer) {
                        this.overview = overview;
                        this.headline = headline;
                        this.subtitle = subtitle;
                        this.explanation = explanation;
                        this.adminDisclaimer = adminDisclaimer;
                    }

                    /**
                     * Returns the {@code overview} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> overview() {
                        return Optional.ofNullable(overview);
                    }

                    /**
                     * Returns the {@code headline} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> headline() {
                        return Optional.ofNullable(headline);
                    }

                    /**
                     * Returns the {@code subtitle} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> subtitle() {
                        return Optional.ofNullable(subtitle);
                    }

                    /**
                     * Returns the {@code explanation} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> explanation() {
                        return Optional.ofNullable(explanation);
                    }

                    /**
                     * Returns the {@code admin_disclaimer} field.
                     *
                     * @return an {@link Optional} containing the value, or empty if absent
                     */
                    public Optional<String> adminDisclaimer() {
                        return Optional.ofNullable(adminDisclaimer);
                    }

                    /**
                     * Parses a {@code EnforcementPolicyInformation} from the given JSON object.
                     *
                     * @param obj the JSON object to parse
                     * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                     */
                    static Optional<EnforcementPolicyInformation> of(JSONObject obj) {
                        if (obj == null) {
                            return Optional.empty();
                        }

                        var overview = obj.getString("overview");
                        var headline = obj.getString("headline");
                        var subtitle = obj.getString("subtitle");
                        var explanation = obj.getString("explanation");
                        var adminDisclaimer = obj.getString("admin_disclaimer");
                        return Optional.of(new EnforcementPolicyInformation(overview, headline, subtitle, explanation, adminDisclaimer));
                    }

                    /**
                     * Parses a list of {@code EnforcementPolicyInformation} from the given JSON array.
                     *
                     * @param arr the JSON array to parse
                     * @return the list of parsed results, empty if {@code arr} is {@code null}
                     */
                    static List<EnforcementPolicyInformation> ofArray(JSONArray arr) {
                        if (arr == null) {
                            return List.of();
                        }

                        var result = new ArrayList<EnforcementPolicyInformation>(arr.size());
                        for (var i = 0; i < arr.size(); i++) {
                            of(arr.getJSONObject(i)).ifPresent(result::add);
                        }
                        return result;
                    }
                }

                /**
                 * Parses a {@code BaseEnforcementData} from the given JSON object.
                 *
                 * @param obj the JSON object to parse
                 * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
                 */
                static Optional<BaseEnforcementData> of(JSONObject obj) {
                    if (obj == null) {
                        return Optional.empty();
                    }

                    var enforcementCreationTime = obj.getLong("enforcement_creation_time");
                    var appealCreationTime = obj.getLong("appeal_creation_time");
                    var appealState = obj.getString("appeal_state");
                    var enforcementId = obj.getString("enforcement_id");
                    var enforcementViolationCategory = obj.getString("enforcement_violation_category");
                    var enforcementSource = obj.getString("enforcement_source");
                    var enforcementExtraData = EnforcementExtraData.of(obj.getJSONObject("enforcement_extra_data")).orElse(null);
                    var enforcementPolicyInformation = EnforcementPolicyInformation.of(obj.getJSONObject("enforcement_policy_information")).orElse(null);
                    return Optional.of(new BaseEnforcementData(enforcementCreationTime, appealCreationTime, appealState, enforcementId, enforcementViolationCategory, enforcementSource, enforcementExtraData, enforcementPolicyInformation));
                }

                /**
                 * Parses a list of {@code BaseEnforcementData} from the given JSON array.
                 *
                 * @param arr the JSON array to parse
                 * @return the list of parsed results, empty if {@code arr} is {@code null}
                 */
                static List<BaseEnforcementData> ofArray(JSONArray arr) {
                    if (arr == null) {
                        return List.of();
                    }

                    var result = new ArrayList<BaseEnforcementData>(arr.size());
                    for (var i = 0; i < arr.size(); i++) {
                        of(arr.getJSONObject(i)).ifPresent(result::add);
                    }
                    return result;
                }
            }

            /**
             * Parses a {@code Geosuspensions} from the given JSON object.
             *
             * @param obj the JSON object to parse
             * @return an {@link Optional} containing the parsed result, or empty if {@code obj} is {@code null}
             */
            static Optional<Geosuspensions> of(JSONObject obj) {
                if (obj == null) {
                    return Optional.empty();
                }

                var baseEnforcementData = BaseEnforcementData.of(obj.getJSONObject("base_enforcement_data")).orElse(null);
                // WAWebMexFetchNewsletterEnforcementsJob.mexFetchNewsletterEnforcements
                // Reads country_codes as a list of raw ISO 2-letter strings; WA Web enriches via WAWebAsISOCountryCode/WAWebLocaleModules in UI code
                var countryCodesArr = obj.getJSONArray("country_codes");
                List<String> countryCodes;
                if (countryCodesArr == null) {
                    countryCodes = List.of();
                } else {
                    var codes = new ArrayList<String>(countryCodesArr.size());
                    for (var i = 0; i < countryCodesArr.size(); i++) {
                        var code = countryCodesArr.getString(i);
                        if (code != null) {
                            codes.add(code);
                        }
                    }
                    countryCodes = List.copyOf(codes);
                }
                return Optional.of(new Geosuspensions(baseEnforcementData, countryCodes));
            }

            /**
             * Parses a list of {@code Geosuspensions} from the given JSON array.
             *
             * @param arr the JSON array to parse
             * @return the list of parsed results, empty if {@code arr} is {@code null}
             */
            static List<Geosuspensions> ofArray(JSONArray arr) {
                if (arr == null) {
                    return List.of();
                }

                var result = new ArrayList<Geosuspensions>(arr.size());
                for (var i = 0; i < arr.size(); i++) {
                    of(arr.getJSONObject(i)).ifPresent(result::add);
                }
                return result;
            }
        }

        /**
         * Parses a {@link Response} from the raw JSON bytes of the
         * {@code <result>} child.
         *
         * @implNote WAWebMexFetchNewsletterEnforcementsJob.mexFetchNewsletterEnforcements: mirrors the implicit
         * unwrapping that WA Web performs on the GraphQL response,
         * extracting the {@code xwa2_channel_enforcements} root.
         * @param json the UTF-8 encoded JSON payload
         * @return an {@link Optional} containing the parsed response, or
         *         empty if the envelope is missing expected fields
         */
        private static Optional<Response> of(byte[] json) {
            // WAWebMexFetchNewsletterEnforcementsJob.mexFetchNewsletterEnforcements
            // Parses the raw JSON payload, bailing out if fastjson2 returns null
            var jsonObject = JSON.parseObject(json);
            if (jsonObject == null) {
                return Optional.empty();
            }

            // WAWebMexFetchNewsletterEnforcementsJob.mexFetchNewsletterEnforcements
            // Descends into the standard GraphQL "data" envelope
            var data = jsonObject.getJSONObject("data");
            if (data == null) {
                return Optional.empty();
            }

            // WAWebMexFetchNewsletterEnforcementsJob.mexFetchNewsletterEnforcements
            // Extracts the operation-specific root keyed by xwa2_channel_enforcements
            var root = data.getJSONObject("xwa2_channel_enforcements");
            if (root == null) {
                return Optional.empty();
            }

            var profilePictureDeletions = ProfilePictureDeletions.ofArray(root.getJSONArray("profile_picture_deletions"));
            var suspensions = Suspensions.ofArray(root.getJSONArray("suspensions"));
            var violatingMessages = ViolatingMessages.ofArray(root.getJSONArray("violating_messages"));
            var geosuspensions = Geosuspensions.ofArray(root.getJSONArray("geosuspensions"));

            return Optional.of(new Response(profilePictureDeletions, suspensions, violatingMessages, geosuspensions));
        }
    }
}
