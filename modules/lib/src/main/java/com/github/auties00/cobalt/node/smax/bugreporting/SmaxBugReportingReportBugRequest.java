package com.github.auties00.cobalt.node.smax.bugreporting;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code <iq xmlns="fb:thrift_iq" smax_id="105" type="set"/>}
 * bug-report submission.
 *
 * @apiNote
 * Drives the WA Web {@code submitBugReport} flow exposed through the
 * Help and Feedback surface (and the employee-only
 * {@code trigger_bugreport_v2} command in {@code WAWebCmd}); the relay
 * routes the payload to the internal bug-tracking backend and replies
 * with a {@link SmaxBugReportingReportBugResponse.Success} carrying the
 * backend-assigned task id. Cobalt embedders build one of these to
 * submit a programmatic bug report end-to-end without going through
 * the GraphQL {@code WAWebSupportBugReportSubmitMutation} fallback.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutBugReportingReportBugRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutBugReportingHackBaseIQSetRequestMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutBugReportingBaseIQSetRequestMixin")
public final class SmaxBugReportingReportBugRequest implements SmaxOperation.Request {
    /**
     * The optional sender JID stamped onto the IQ envelope.
     *
     * @apiNote
     * Mirrors the {@code OPTIONAL(USER_JID, iqFrom)} attribute slot in
     * {@code WASmaxOutBugReportingHackBaseIQSetRequestMixin.mergeHackBaseIQSetRequestMixin};
     * left {@code null} so the relay derives the sender from the
     * authenticated session.
     */
    private final Jid iqFrom;

    /**
     * The free-form description text shown to the user.
     *
     * @apiNote
     * Rendered as the mandatory {@code <description/>} child of the
     * outbound IQ; corresponds to the textarea the user fills in the
     * Help and Feedback dialog.
     */
    private final String descriptionElementValue;

    /**
     * The debug-information JSON blob attached to the report.
     *
     * @apiNote
     * Rendered as the mandatory {@code <debug_information_json/>}
     * child; WA Web ships a JSON-stringified snapshot of platform,
     * gating, and storage diagnostics here.
     */
    private final String debugInformationJsonElementValue;

    /**
     * The optional device-log handle attached to the report.
     *
     * @apiNote
     * Rendered as an optional {@code <device_log_handle/>} child via
     * {@code makeReportBugRequestDeviceLogHandle}; identifies a
     * server-side log artefact previously uploaded by the client so
     * the bug tracker can correlate the report with full device logs.
     */
    private final String deviceLogHandleElementValue;

    /**
     * The media uploads attached to the report.
     *
     * @apiNote
     * Rendered as the {@code REPEATED_CHILD(media, 0, 10)} slot via
     * {@code makeReportBugRequestMedia}; capped at 10 entries by the
     * WA Web SMAX schema and validated in the constructor.
     */
    private final List<SmaxBugReportingReportBugMediaUpload> mediaUploads;

    /**
     * The optional title label for the report.
     *
     * @apiNote
     * Rendered as an optional {@code <title/>} child via
     * {@code makeReportBugRequestTitle}; corresponds to the optional
     * subject-line field on the Help and Feedback dialog.
     */
    private final String titleElementValue;

    /**
     * The optional category label for the report.
     *
     * @apiNote
     * Rendered as an optional {@code <category/>} child via
     * {@code makeReportBugRequestCategory}; one of the
     * {@code WAWebBugReportCategoryTypes} keys selected from the
     * dropdown.
     */
    private final String categoryElementValue;

    /**
     * The optional client-server join key.
     *
     * @apiNote
     * Rendered as an optional {@code <client_server_join_key/>} child
     * via {@code makeReportBugRequestClientServerJoinKey}; carries the
     * cross-correlation token shared with the
     * {@code WAWebBugReportSessionWamEvent} so the report can be
     * joined back to the WAM telemetry record.
     */
    private final String clientServerJoinKeyElementValue;

    /**
     * The optional reproducibility marker.
     *
     * @apiNote
     * Rendered as an optional {@code <reproducibility/>} child via
     * {@code makeReportBugRequestReproducibility}; one of the
     * reproducibility-bucket strings the user picks from the dialog.
     */
    private final String reproducibilityElementValue;

    /**
     * Constructs a new bug-report request.
     *
     * @apiNote
     * Embedders build one per submission; reuse across submissions is
     * pointless because every field is intrinsic to the specific
     * report being filed.
     *
     * @implNote
     * This implementation validates the {@code mediaUploads} cardinality
     * eagerly and stores the list as an immutable copy so concurrent
     * modification by the caller cannot race with {@link #toNode()};
     * WA Web defers the bound check to
     * {@code WASmaxChildren.REPEATED_CHILD(_, _, 0, 10)} at render
     * time.
     *
     * @param iqFrom                          the optional sender JID;
     *                                        may be {@code null}
     * @param descriptionElementValue         the description text;
     *                                        never {@code null}
     * @param debugInformationJsonElementValue the debug JSON; never
     *                                        {@code null}
     * @param deviceLogHandleElementValue     the optional log handle;
     *                                        may be {@code null}
     * @param mediaUploads                    the media uploads; never
     *                                        {@code null}; at most 10
     *                                        entries
     * @param titleElementValue               the optional title; may
     *                                        be {@code null}
     * @param categoryElementValue            the optional category;
     *                                        may be {@code null}
     * @param clientServerJoinKeyElementValue the optional join key;
     *                                        may be {@code null}
     * @param reproducibilityElementValue     the optional
     *                                        reproducibility marker;
     *                                        may be {@code null}
     * @throws NullPointerException     if any required argument is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code mediaUploads} carries
     *                                  more than 10 entries
     */
    public SmaxBugReportingReportBugRequest(Jid iqFrom,
                   String descriptionElementValue,
                   String debugInformationJsonElementValue,
                   String deviceLogHandleElementValue,
                   List<SmaxBugReportingReportBugMediaUpload> mediaUploads,
                   String titleElementValue,
                   String categoryElementValue,
                   String clientServerJoinKeyElementValue,
                   String reproducibilityElementValue) {
        this.iqFrom = iqFrom;
        this.descriptionElementValue = Objects.requireNonNull(descriptionElementValue,
                "descriptionElementValue cannot be null");
        this.debugInformationJsonElementValue = Objects.requireNonNull(debugInformationJsonElementValue,
                "debugInformationJsonElementValue cannot be null");
        this.deviceLogHandleElementValue = deviceLogHandleElementValue;
        Objects.requireNonNull(mediaUploads, "mediaUploads cannot be null");
        if (mediaUploads.size() > 10) {
            throw new IllegalArgumentException(
                    "mediaUploads must carry at most 10 entries");
        }
        this.mediaUploads = List.copyOf(mediaUploads);
        this.titleElementValue = titleElementValue;
        this.categoryElementValue = categoryElementValue;
        this.clientServerJoinKeyElementValue = clientServerJoinKeyElementValue;
        this.reproducibilityElementValue = reproducibilityElementValue;
    }

    /**
     * Returns the optional sender JID.
     *
     * @apiNote
     * Empty when the relay should derive the sender from the
     * authenticated session.
     *
     * @return an {@link Optional} carrying the JID
     */
    public Optional<Jid> iqFrom() {
        return Optional.ofNullable(iqFrom);
    }

    /**
     * Returns the description text.
     *
     * @apiNote
     * Mirrors the user-typed contents of the Help and Feedback
     * textarea.
     *
     * @return the description; never {@code null}
     */
    public String descriptionElementValue() {
        return descriptionElementValue;
    }

    /**
     * Returns the debug-information JSON blob.
     *
     * @apiNote
     * Mirrors the JSON-stringified diagnostics snapshot WA Web bundles
     * with the report.
     *
     * @return the blob; never {@code null}
     */
    public String debugInformationJsonElementValue() {
        return debugInformationJsonElementValue;
    }

    /**
     * Returns the optional device-log handle.
     *
     * @apiNote
     * Empty when the embedder has not pre-uploaded a device-log
     * artefact for the backend to correlate.
     *
     * @return an {@link Optional} carrying the handle
     */
    public Optional<String> deviceLogHandleElementValue() {
        return Optional.ofNullable(deviceLogHandleElementValue);
    }

    /**
     * Returns the media uploads.
     *
     * @apiNote
     * Returns an unmodifiable snapshot; callers that want to mutate
     * the list must copy it first.
     *
     * @return the uploads; never {@code null}
     */
    public List<SmaxBugReportingReportBugMediaUpload> mediaUploads() {
        return mediaUploads;
    }

    /**
     * Returns the optional title label.
     *
     * @apiNote
     * Empty when the user left the optional subject-line field blank.
     *
     * @return an {@link Optional} carrying the title
     */
    public Optional<String> titleElementValue() {
        return Optional.ofNullable(titleElementValue);
    }

    /**
     * Returns the optional category label.
     *
     * @apiNote
     * Empty when no category was selected; otherwise one of the
     * {@code WAWebBugReportCategoryTypes} keys.
     *
     * @return an {@link Optional} carrying the category
     */
    public Optional<String> categoryElementValue() {
        return Optional.ofNullable(categoryElementValue);
    }

    /**
     * Returns the optional client-server join key.
     *
     * @apiNote
     * Empty when the embedder did not pair this submission with a
     * WAM telemetry record via the
     * {@code WAWebBugReportSessionWamEvent.clientServerJoinKey} slot.
     *
     * @return an {@link Optional} carrying the key
     */
    public Optional<String> clientServerJoinKeyElementValue() {
        return Optional.ofNullable(clientServerJoinKeyElementValue);
    }

    /**
     * Returns the optional reproducibility marker.
     *
     * @apiNote
     * Empty when no reproducibility bucket was selected.
     *
     * @return an {@link Optional} carrying the marker
     */
    public Optional<String> reproducibilityElementValue() {
        return Optional.ofNullable(reproducibilityElementValue);
    }

    /**
     * Builds the outbound IQ stanza ready for dispatch.
     *
     * @apiNote
     * Folded together with
     * {@link com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin}
     * and the IQ-set merge mixins so the result is ready for
     * {@code WAComms.sendSmaxStanza}; the dispatcher stamps the IQ id
     * before flushing.
     *
     * @return the {@link NodeBuilder} carrying the IQ envelope and
     *         payload
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutBugReportingReportBugRequest",
            exports = "makeReportBugRequest", adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WASmaxOutBugReportingReportBugRequest",
            exports = "makeReportBugRequestDeviceLogHandle", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxOutBugReportingReportBugRequest",
            exports = "makeReportBugRequestTitle", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxOutBugReportingReportBugRequest",
            exports = "makeReportBugRequestCategory", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxOutBugReportingReportBugRequest",
            exports = "makeReportBugRequestClientServerJoinKey", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxOutBugReportingReportBugRequest",
            exports = "makeReportBugRequestReproducibility", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxOutBugReportingHackBaseIQSetRequestMixin",
            exports = "mergeHackBaseIQSetRequestMixin", adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WASmaxOutBugReportingBaseIQSetRequestMixin",
            exports = "mergeBaseIQSetRequestMixin", adaptation = WhatsAppAdaptation.ADAPTED)
    public NodeBuilder toNode() {
        var children = new ArrayList<Node>();
        var descriptionNode = new NodeBuilder()
                .description("description")
                .content(descriptionElementValue)
                .build();
        children.add(descriptionNode);
        var debugNode = new NodeBuilder()
                .description("debug_information_json")
                .content(debugInformationJsonElementValue)
                .build();
        children.add(debugNode);
        if (deviceLogHandleElementValue != null) {
            var deviceLogNode = new NodeBuilder()
                    .description("device_log_handle")
                    .content(deviceLogHandleElementValue)
                    .build();
            children.add(deviceLogNode);
        }
        for (var media : mediaUploads) {
            children.add(media.toNode());
        }
        if (titleElementValue != null) {
            var titleNode = new NodeBuilder()
                    .description("title")
                    .content(titleElementValue)
                    .build();
            children.add(titleNode);
        }
        if (categoryElementValue != null) {
            var categoryNode = new NodeBuilder()
                    .description("category")
                    .content(categoryElementValue)
                    .build();
            children.add(categoryNode);
        }
        if (clientServerJoinKeyElementValue != null) {
            var joinKeyNode = new NodeBuilder()
                    .description("client_server_join_key")
                    .content(clientServerJoinKeyElementValue)
                    .build();
            children.add(joinKeyNode);
        }
        if (reproducibilityElementValue != null) {
            var reproducibilityNode = new NodeBuilder()
                    .description("reproducibility")
                    .content(reproducibilityElementValue)
                    .build();
            children.add(reproducibilityNode);
        }
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "fb:thrift_iq")
                .attribute("smax_id", 105)
                .attribute("from", iqFrom)
                .attribute("to", Jid.userServer())
                .attribute("type", "set")
                .content(children);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxBugReportingReportBugRequest) obj;
        return Objects.equals(this.iqFrom, that.iqFrom)
                && Objects.equals(this.descriptionElementValue, that.descriptionElementValue)
                && Objects.equals(this.debugInformationJsonElementValue, that.debugInformationJsonElementValue)
                && Objects.equals(this.deviceLogHandleElementValue, that.deviceLogHandleElementValue)
                && Objects.equals(this.mediaUploads, that.mediaUploads)
                && Objects.equals(this.titleElementValue, that.titleElementValue)
                && Objects.equals(this.categoryElementValue, that.categoryElementValue)
                && Objects.equals(this.clientServerJoinKeyElementValue, that.clientServerJoinKeyElementValue)
                && Objects.equals(this.reproducibilityElementValue, that.reproducibilityElementValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(iqFrom, descriptionElementValue, debugInformationJsonElementValue,
                deviceLogHandleElementValue, mediaUploads, titleElementValue, categoryElementValue,
                clientServerJoinKeyElementValue, reproducibilityElementValue);
    }

    @Override
    public String toString() {
        return "SmaxBugReportingReportBugRequest[iqFrom=" + iqFrom
                + ", descriptionElementValue=" + descriptionElementValue
                + ", debugInformationJsonElementValue=" + debugInformationJsonElementValue
                + ", deviceLogHandleElementValue=" + deviceLogHandleElementValue
                + ", mediaUploads=" + mediaUploads
                + ", titleElementValue=" + titleElementValue
                + ", categoryElementValue=" + categoryElementValue
                + ", clientServerJoinKeyElementValue=" + clientServerJoinKeyElementValue
                + ", reproducibilityElementValue=" + reproducibilityElementValue + ']';
    }
}
