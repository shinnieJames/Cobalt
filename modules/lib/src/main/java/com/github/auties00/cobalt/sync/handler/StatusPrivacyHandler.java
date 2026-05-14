package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.model.privacy.PrivacySettingEntry;
import com.github.auties00.cobalt.model.privacy.PrivacySettingEntryBuilder;
import com.github.auties00.cobalt.model.privacy.PrivacySettingType;
import com.github.auties00.cobalt.model.privacy.PrivacySettingValue;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.media.StatusPrivacyAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handles status privacy sync actions.
 *
 * <p>This handler processes mutations that control who can see the local user's
 * status updates. The supported distribution modes are:
 * <ul>
 *   <li>{@code CONTACTS} — share with all contacts (mapped to
 *       {@link PrivacySettingValue#CONTACTS}, no excluded list).</li>
 *   <li>{@code ALLOW_LIST} — share only with a whitelist of contacts (mapped to
 *       {@link PrivacySettingValue#CONTACTS_ONLY} with the JID list filtered to
 *       user-server JIDs).</li>
 *   <li>{@code DENY_LIST} — share with all contacts except a blacklist (mapped
 *       to {@link PrivacySettingValue#CONTACTS_EXCEPT} with the JID list filtered
 *       to user-server JIDs).</li>
 *   <li>{@code CLOSE_FRIENDS} and {@code CUSTOM_LIST} — accepted but applied as
 *       a no-op, mirroring WA Web which simply breaks out of the dispatch switch
 *       without writing any IndexedDB entries.</li>
 * </ul>
 *
 * <p>Per WhatsApp Web {@code WAWebStatusPrivacySettingSync.applyMutations}: the
 * batch must contain exactly one mutation; otherwise every mutation in the batch
 * is reported as {@link MutationApplicationResult#malformed() Malformed}. The
 * single mutation must have operation {@code SET}, must carry a non-{@code null}
 * {@code statusPrivacy} value, and must have a non-{@code null} distribution
 * mode. Any failure in the application logic is reported as
 * {@link MutationApplicationResult#failed() Failed}.
 *
 * <p>The crossposting branch (gated by
 * {@code WAWebCrosspostingBackendGatingUtils.crosspostSettingsSyncReceiverEnabled})
 * persists {@code shareToFB} / {@code shareToIG} preferences via WA Web's
 * {@code WAWebUserPrefsStatus} module. Cobalt's {@link StatusPrivacyAction}
 * carries these fields on the protobuf, but the Cobalt store does not expose a
 * dedicated FB/IG cross-posting persistence layer, so the receiver branch is a
 * no-op.
 *
 * <p>Index format: {@code ["status_privacy"]}.
 */
@WhatsAppWebModule(moduleName = "WAWebStatusPrivacySettingSync")
public final class StatusPrivacyHandler implements WebAppStateActionHandler {

    /**
     * Constructs the singleton instance.
     */
    @WhatsAppWebExport(moduleName = "WAWebStatusPrivacySettingSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public StatusPrivacyHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStatusPrivacySettingSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return StatusPrivacyAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStatusPrivacySettingSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return StatusPrivacyAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStatusPrivacySettingSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return StatusPrivacyAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Per WhatsApp Web {@code WAWebStatusPrivacySettingSync.applyMutations}:
     * <ol>
     *   <li>If the batch contains anything other than exactly one mutation, every
     *       mutation in the batch is reported as
     *       {@link MutationApplicationResult#malformed() Malformed}. WA Web
     *       additionally logs {@code "[syncd] unexpected mutation count %s for
     *       status privacy sync"}; Cobalt omits the WALogger telemetry.</li>
     *   <li>Reads the last mutation from the batch
     *       ({@code var a = t[t.length - 1]}) and dispatches to the
     *       single-mutation pipeline.</li>
     *   <li>Wraps the single-mutation logic in a try/catch that maps any thrown
     *       error to {@link MutationApplicationResult#failed() Failed} for every
     *       mutation in the batch (WA Web logs
     *       {@code "[syncd] status privacy IDB write failed %s"}).</li>
     *   <li>If the operation is not {@code SET}, returns
     *       {@link MutationApplicationResult#unsupported() Unsupported}.</li>
     * </ol>
     * @param client    the WhatsApp client instance
     * @param mutations the batch of mutations to apply
     * @return a list of results parallel to the input
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStatusPrivacySettingSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public List<MutationApplicationResult> applyMutationBatch(WhatsAppClient client, List<DecryptedMutation.Trusted> mutations) {
        if (mutations.size() != 1) {
            var malformed = new ArrayList<MutationApplicationResult>(mutations.size());
            for (var i = 0; i < mutations.size(); i++) {
                malformed.add(MutationApplicationResult.malformed());
            }
            return malformed;
        }

        var last = mutations.get(mutations.size() - 1);
        if (last.operation() != SyncdOperation.SET) {
            return List.of(MutationApplicationResult.unsupported());
        }

        try {
            return List.of(applySetMutation(client, last));
        } catch (RuntimeException e) {
            return List.of(MutationApplicationResult.failed());
        }
    }

    /**
     * Applies a single status privacy mutation and returns a detailed result.
     *
     * <p>Per WhatsApp Web {@code WAWebStatusPrivacySettingSync.applyMutations}
     * (single-mutation path within the batch):
     * <ol>
     *   <li>If the operation is not {@code SET}, returns
     *       {@link MutationApplicationResult#unsupported() Unsupported}.</li>
     *   <li>Wraps the application in a try/catch and returns
     *       {@link MutationApplicationResult#failed() Failed} on any exception
     *       (WA Web logs the failure to {@code WALogger.ERROR}).</li>
     *   <li>If the value is missing, the {@code statusPrivacy} sub-message is
     *       missing, or the distribution {@code mode} is {@code null}, returns
     *       {@link #malformedActionValue()} ({@code Malformed}).</li>
     *   <li>For {@code CONTACTS}, persists a {@link PrivacySettingType#STATUS}
     *       entry with value {@link PrivacySettingValue#CONTACTS} and an empty
     *       JID list.</li>
     *   <li>For {@code ALLOW_LIST}, filters {@code userJid} to user-server JIDs
     *       and persists an entry with value
     *       {@link PrivacySettingValue#CONTACTS_ONLY} and the filtered list.</li>
     *   <li>For {@code DENY_LIST}, filters {@code userJid} to user-server JIDs
     *       and persists an entry with value
     *       {@link PrivacySettingValue#CONTACTS_EXCEPT} and the filtered list.</li>
     *   <li>For {@code CLOSE_FRIENDS} (and the missing {@code CUSTOM_LIST}
     *       variant), no entries are written and {@code Success} is returned —
     *       WA Web breaks out of the dispatch switch without populating any
     *       IDB key list.</li>
     * </ol>
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebStatusPrivacySettingSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        try {
            return applySetMutation(client, mutation);
        } catch (RuntimeException e) {
            return MutationApplicationResult.failed();
        }
    }

    /**
     * Applies a single SET mutation, mirroring WA Web's switch dispatch on
     * {@code StatusDistributionMode}.
     *
     * <p>The caller is responsible for the operation check and the try/catch
     * wrapper; this helper handles only the value validation and the per-mode
     * dispatch.
     * @param client   the WhatsApp client instance
     * @param mutation the SET mutation to apply
     * @return the detailed application result
     */
    @WhatsAppWebExport(moduleName = "WAWebStatusPrivacySettingSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    private MutationApplicationResult applySetMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (!(mutation.value().action().orElse(null) instanceof StatusPrivacyAction action)) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        var mode = action.mode().orElse(null);
        if (mode == null) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        // Cobalt's StatusPrivacyAction model does not carry shareToFB/shareToIG; the crossposting receiver branch is intentionally omitted (see report).
        var userJid = action.userJid();

        PrivacySettingEntry entry = null;
        switch (mode) {
            case CONTACTS -> {
                entry = new PrivacySettingEntryBuilder()
                        .type(PrivacySettingType.STATUS) // ADAPTED: WAWebUserPrefsKeys.BACKEND_ONLY_KEYS.STATUS_PRIVACY_SETTING -> Cobalt PrivacySettingEntry.type
                        .value(PrivacySettingValue.CONTACTS) // ADAPTED: StatusPrivacySettingType.Contact ("contact") -> PrivacySettingValue.CONTACTS
                        .excluded(List.of()) // ADAPTED: no allowList/denyList written by WA Web for the Contact setting
                        .build();
            }
            case ALLOW_LIST -> {
                var allowList = filterUserJids(userJid);
                entry = new PrivacySettingEntryBuilder()
                        .type(PrivacySettingType.STATUS) // ADAPTED: STATUS_PRIVACY_SETTING -> PrivacySettingEntry.type
                        .value(PrivacySettingValue.CONTACTS_ONLY) // ADAPTED: StatusPrivacySettingType.AllowList ("allow-list") -> PrivacySettingValue.CONTACTS_ONLY ("contact_whitelist")
                        .excluded(allowList) // ADAPTED: STATUS_ALLOW_LIST -> PrivacySettingEntry.excluded (Cobalt overloads excluded for both allow and deny lists)
                        .build();
            }
            case DENY_LIST -> {
                var denyList = filterUserJids(userJid);
                entry = new PrivacySettingEntryBuilder()
                        .type(PrivacySettingType.STATUS) // ADAPTED: STATUS_PRIVACY_SETTING -> PrivacySettingEntry.type
                        .value(PrivacySettingValue.CONTACTS_EXCEPT) // ADAPTED: StatusPrivacySettingType.DenyList ("deny-list") -> PrivacySettingValue.CONTACTS_EXCEPT ("contact_blacklist")
                        .excluded(denyList) // ADAPTED: STATUS_DENY_LIST -> PrivacySettingEntry.excluded
                        .build();
            }
            case CLOSE_FRIENDS, CUSTOM_LIST -> {
            }
        }

        // Crossposting receiver branch intentionally omitted: shareToFB/shareToIG fields are not in Cobalt's StatusPrivacyAction protobuf model.
        if (entry != null) {
            client.store().addPrivacySetting(entry); // ADAPTED: bulkSetItemsToIndexedDB -> WhatsAppStore.addPrivacySetting (Cobalt collapses STATUS_PRIVACY_SETTING/STATUS_ALLOW_LIST/STATUS_DENY_LIST into one PrivacySettingEntry)
        }
        return MutationApplicationResult.success();
    }

    /**
     * Filters a JID list to those whose server is a user-server domain,
     * mirroring WA Web's {@code wid.isUser()} predicate after
     * {@code WAWebWidFactory.createWid} normalization.
     *
     * <p>WA Web's {@code Wid.prototype.isUser} returns {@code true} for the
     * {@code c.us}, {@code lid}, {@code bot}, {@code hosted}, and
     * {@code hosted.lid} server domains. Cobalt's {@link Jid#hasUserServer()}
     * only matches the standard ({@code s.whatsapp.net}) and legacy
     * ({@code c.us}) user domains; for full WA Web parity, this helper also
     * accepts {@code lid}, {@code bot}, {@code hosted}, and {@code hosted.lid}.
     * @param jids the input JID list
     * @return an unmodifiable list containing only user-server JIDs in the
     *         original order
     */
    @WhatsAppWebExport(moduleName = "WAWebWid", exports = "isUser", adaptation = WhatsAppAdaptation.ADAPTED)
    private static List<Jid> filterUserJids(List<Jid> jids) {
        if (jids == null || jids.isEmpty()) {
            return List.of();
        }
        var filtered = new ArrayList<Jid>(jids.size());
        for (var jid : jids) {
            if (jid == null) { // ADAPTED: defensive null check; WA Web's createWid throws on null
                continue;
            }
            if (isUserWid(jid)) {
                filtered.add(jid);
            }
        }
        return Collections.unmodifiableList(filtered);
    }

    /**
     * Returns whether the given JID belongs to a user-server domain matching
     * WA Web's {@code Wid.prototype.isUser} predicate.
     * @param jid the JID to test
     * @return {@code true} if the JID's server is a user-server domain
     */
    @WhatsAppWebExport(moduleName = "WAWebWid", exports = "isUser", adaptation = WhatsAppAdaptation.DIRECT)
    private static boolean isUserWid(Jid jid) {
        var type = jid.server().type();
        return type == JidServer.Type.USER
                || type == JidServer.Type.LEGACY_USER
                || type == JidServer.Type.LID
                || type == JidServer.Type.BOT
                || type == JidServer.Type.HOSTED
                || type == JidServer.Type.HOSTED_LID;
    }

}
