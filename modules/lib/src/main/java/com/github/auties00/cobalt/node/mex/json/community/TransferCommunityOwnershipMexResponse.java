package com.github.auties00.cobalt.node.mex.json.community;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parsed response of the {@code mexTransferCommunityOwnershipJob} MEX
 * mutation.
 *
 * @apiNote Carries the affected community group identifier together with
 * the LID migration state projected from
 * {@code data.xwa2_group_update_users_role}. Paired with
 * {@link TransferCommunityOwnershipMexRequest}; consumed by
 * {@code WAWebTransferCommunityOwnershipAction}, which uses the
 * {@code lid_migration_state.addressing_mode} field to detect whether the
 * group switched LID-addressing modes (and if so, refreshes the group
 * metadata via {@code WAWebGroupQueryJob.queryAndUpdateGroupMetadataById}).
 *
 * @implNote This implementation models only the response shape; the
 * downstream metadata-refresh side-effect WA Web triggers on an addressing
 * mode change is left to the caller. The {@code group_id} field returned
 * by the relay is a stringified WhatsApp WID; WA Web wraps it through
 * {@code WAWebWidFactory.createWid}, Cobalt keeps it as a {@link String}
 * and lets callers wrap it on demand.
 */
@WhatsAppWebModule(moduleName = "WAWebMexTransferCommunityOwnershipJob")
public final class TransferCommunityOwnershipMexResponse implements MexOperation.Response.Json {
    /**
     * Affected community group identifier.
     *
     * @apiNote The community whose ownership was transferred; matches the
     * {@code community_id} sent in the mutation input.
     */
    private final String groupId;

    /**
     * LID migration state record reported alongside the mutation result.
     *
     * @apiNote Carries the post-mutation addressing-mode tag; callers
     * compare it against their local addressing-mode flag to detect whether
     * the group switched modes during the transfer.
     */
    private final LidMigrationState lidMigrationState;

    /**
     * Constructs a new response with the given fields.
     *
     * @apiNote Package-private; instances are produced by the
     * {@link #of(Node)} factory after parsing the inbound IQ payload.
     *
     * @param groupId           the affected community group identifier
     * @param lidMigrationState the LID migration state record
     */
    private TransferCommunityOwnershipMexResponse(String groupId, LidMigrationState lidMigrationState) {
        this.groupId = groupId;
        this.lidMigrationState = lidMigrationState;
    }

    /**
     * Parses the MEX response carried by an inbound IQ stanza.
     *
     * @apiNote Entry point for receivers handling
     * {@code <iq xmlns="w:mex">} replies tagged with
     * {@link TransferCommunityOwnershipMexRequest#QUERY_ID}. Unwraps the
     * {@code <result>} child, reads its content bytes and decodes the
     * GraphQL JSON envelope.
     *
     * @param node the inbound IQ stanza carrying the {@code <result>} child
     * @return the parsed response, or {@link Optional#empty()} if the
     *         expected JSON shape is absent
     */
    @WhatsAppWebExport(moduleName = "WAWebMexTransferCommunityOwnershipJob", exports = "mexTransferCommunityOwnershipJob",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<TransferCommunityOwnershipMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(TransferCommunityOwnershipMexResponse::of);
    }

    /**
     * Returns the affected community group identifier.
     *
     * @apiNote Empty when the relay omitted the {@code group_id} field;
     * callers that need a {@code Wid} should pass the value through their
     * own factory.
     *
     * @return an {@link Optional} containing the identifier, or empty if
     *         absent
     */
    public Optional<String> groupId() {
        return Optional.ofNullable(groupId);
    }

    /**
     * Returns the LID migration state record reported alongside the
     * mutation result.
     *
     * @apiNote Compare {@link LidMigrationState#addressingMode()} against
     * the local addressing-mode flag to decide whether to refresh the
     * group metadata after the transfer; WA Web only triggers the refresh
     * when the two disagree.
     *
     * @return an {@link Optional} containing the record, or empty if
     *         absent
     */
    public Optional<LidMigrationState> lidMigrationState() {
        return Optional.ofNullable(lidMigrationState);
    }

    /**
     * LID migration state record.
     *
     * @apiNote Captures the addressing-mode tag describing whether the
     * group is in LID-addressed mode or has fallen back to legacy
     * phone-number addressing; the WA Web caller uses
     * {@code !!d !== n} (where {@code n} is the locally cached
     * {@code isLidAddressingMode}) to detect mode changes.
     */
    public static final class LidMigrationState {
        /**
         * Addressing mode tag for the affected group.
         *
         * @apiNote Wire-side string sourced from the
         * {@code addressing_mode} GraphQL scalar; WA Web treats any
         * non-null value as truthy in the addressing-mode boolean
         * coercion.
         */
        private final String addressingMode;

        /**
         * Constructs a new record with the given addressing mode tag.
         *
         * @apiNote Package-private; instances are produced by the
         * {@link LidMigrationState#of(JSONObject)} factory.
         *
         * @param addressingMode the addressing mode tag
         */
        private LidMigrationState(String addressingMode) {
            this.addressingMode = addressingMode;
        }

        /**
         * Returns the addressing mode tag for the affected group.
         *
         * @apiNote Empty when the relay omitted the
         * {@code addressing_mode} field, which WA Web interprets as the
         * group having no LID addressing (legacy phone-number mode).
         *
         * @return an {@link Optional} containing the tag, or empty if
         *         absent
         */
        public Optional<String> addressingMode() {
            return Optional.ofNullable(addressingMode);
        }

        /**
         * Parses a LID migration state record from the given JSON object.
         *
         * @apiNote Package-private; invoked from
         * {@link TransferCommunityOwnershipMexResponse#of(byte[])} to
         * project the {@code lid_migration_state} GraphQL object.
         *
         * @param obj the JSON object to parse
         * @return an {@link Optional} containing the parsed record, or
         *         empty if {@code obj} is {@code null}
         */
        static Optional<LidMigrationState> of(JSONObject obj) {
            if (obj == null) {
                return Optional.empty();
            }

            var addressingMode = obj.getString("addressing_mode");
            return Optional.of(new LidMigrationState(addressingMode));
        }

        /**
         * Parses a list of LID migration state records from the given JSON
         * array.
         *
         * @apiNote Package-private; symmetry helper for callers that need
         * to read array-shaped state data. Currently unused at the call
         * sites of this response.
         *
         * @implNote This implementation skips {@code null} entries without
         * raising.
         *
         * @param arr the JSON array to parse
         * @return the list of parsed records, empty if {@code arr} is
         *         {@code null}
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
     * Parses the response from the raw JSON payload bytes.
     *
     * @apiNote Package-private; only invoked via the {@link #of(Node)}
     * entry point after unwrapping the IQ stanza.
     *
     * @implNote This implementation requires the {@code data} and
     * {@code data.xwa2_group_update_users_role} envelopes to be present;
     * the inner {@code lid_migration_state} object is allowed to be
     * absent, which collapses to {@code null} on the response (mirroring
     * WA Web's optional-chaining check {@code u?.lid_migration_state}).
     *
     * @param json the raw JSON bytes from the {@code <result>} child
     * @return an {@link Optional} containing the parsed response, or empty
     *         if the envelope is missing
     */
    private static Optional<TransferCommunityOwnershipMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("xwa2_group_update_users_role");
        if (root == null) {
            return Optional.empty();
        }

        var groupId = root.getString("group_id");
        var lidMigrationState = LidMigrationState.of(root.getJSONObject("lid_migration_state")).orElse(null);

        return Optional.of(new TransferCommunityOwnershipMexResponse(groupId, lidMigrationState));
    }
}
