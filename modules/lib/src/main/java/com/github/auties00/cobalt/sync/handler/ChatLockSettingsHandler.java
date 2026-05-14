package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.setting.ChatLockSettings;
import com.github.auties00.cobalt.model.setting.ChatLockSettingsBuilder;
import com.github.auties00.cobalt.model.setting.UserPassword;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Handles chat lock settings sync actions.
 *
 * <p>This handler processes mutations related to the global chat lock settings
 * (e.g., whether locked chats are hidden, the secret code configuration).
 * It validates each mutation's {@link ChatLockSettings} value, including
 * thorough validation of the secret code's transformer, encoding, data,
 * and arguments (iterations and salt).
 *
 * <p>Index format: {@code ["setting_chatLock"]}
 */
@WhatsAppWebModule(moduleName = "WAWebChatLockSettingsSync")
public final class ChatLockSettingsHandler implements WebAppStateActionHandler {
    /**
     * Logger for chat lock settings sync handler.
     */
    private static final Logger LOGGER = Logger.getLogger(ChatLockSettingsHandler.class.getName());

    /**
     * Constructs a new {@code ChatLockSettingsHandler}.
     *
     * <p>Per WhatsApp Web, the constructor of class {@code f} extends
     * {@code AccountSyncdActionBase} and sets
     * {@code this.collectionName = WASyncdConst.CollectionName.RegularLow}. The
     * {@code collectionName} assignment is surfaced in Cobalt via
     * {@link #collectionName()} rather than as an instance field.
     */
    @WhatsAppWebExport(moduleName = "WAWebChatLockSettingsSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public ChatLockSettingsHandler() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatLockSettingsSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return ChatLockSettings.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatLockSettingsSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return ChatLockSettings.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatLockSettingsSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return ChatLockSettings.ACTION_VERSION;
    }

    /**
     * Applies a single chat lock settings mutation and returns the detailed result.
     *
     * <p>Per WhatsApp Web {@code WAWebChatLockSettingsSync.applyMutations} per-mutation
     * map callback: validates that the operation is SET, that the sync action value
     * contains a non-null {@code chatLockSettings}, and that any present
     * {@code secretCode} has valid transformer, encoding, transformed data, and
     * transformer arguments (iterations and salt). On any malformed secretCode,
     * WA Web still updates the pending save target {@code r} with
     * {@code {hideLockedChats: s, secretCode: null}} before returning malformed,
     * so a later successful save commits the sanitized value. The single-mutation
     * entry point here matches that semantic by persisting the sanitized settings
     * only on full success.
     *
     * <p>The WA Web null check on {@code hideLockedChats} is classified as ADAPTED:
     * Cobalt's {@link ChatLockSettings#hideLockedChats()} accessor coalesces
     * {@code null} to {@code false} per project convention for nullable Boolean
     * fields, making the null case indistinguishable from an explicit {@code false}
     * value through the public API.
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed mutation application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatLockSettingsSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof ChatLockSettings settings)) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        // ADAPTED: WAWebChatLockSettingsSync.applyMutations: var s = t.hideLockedChats; if (s == null) return malformed
        // Cobalt's hideLockedChats() coalesces null to false per nullable Boolean convention;
        // the null-vs-false distinction is not observable through the public accessor.
        if (!isSecretCodeValid(settings)) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        client.store().setChatLockSettings(settings);
        return MutationApplicationResult.success();
    }

    /**
     * Applies a batch of chat lock settings mutations.
     *
     * <p>Per WhatsApp Web {@code WAWebChatLockSettingsSync.applyMutations}: iterates
     * all mutations, and for each SET mutation with a non-null {@code chatLockSettings}
     * and a non-null {@code hideLockedChats}, the pending save target {@code r}
     * is unconditionally reassigned to
     * {@code {hideLockedChats: s, secretCode: null}}. Only if the secretCode (when
     * present) validates successfully does {@code r.secretCode} get set to the
     * parsed value. A malformed secretCode therefore leaves {@code r} with the
     * hideLockedChats value but a null secretCode and still returns malformed for
     * that mutation. After iteration, if any {@code r} was produced, it is
     * persisted once via {@code getChatLockSettings().updateAndSave(r)}.
     *
     * <p>If no valid mutation is found, logs a warning matching WA Web's
     * {@code "ChatLockSettingsSync: mutations parse failed"} message.
     * @param client    the WhatsApp client instance
     * @param mutations the batch of mutations to apply
     * @return a list of results parallel to the input
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatLockSettingsSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.DIRECT)
    public List<MutationApplicationResult> applyMutationBatch(WhatsAppClient client, List<DecryptedMutation.Trusted> mutations) {
        ChatLockSettings pending = null;
        var results = new ArrayList<MutationApplicationResult>(mutations.size());
        for (var mutation : mutations) {
            if (mutation.operation() != SyncdOperation.SET) {
                results.add(MutationApplicationResult.unsupported());
                continue;
            }

            if (!(mutation.value().action().orElse(null) instanceof ChatLockSettings settings)) {
                results.add(SyncdIndexUtils.malformedActionValue(collectionName().name()));
                continue;
            }

            // ADAPTED: WAWebChatLockSettingsSync.applyMutations: var s = t.hideLockedChats; if (s == null) return malformed
            // Cobalt's hideLockedChats() coalesces null to false per nullable Boolean convention;
            // the null-vs-false distinction is not observable through the public accessor.
            // This assignment happens BEFORE the secretCode check in WA (JS comma expression),
            // so even malformed-secretCode mutations leave r populated with a sanitized value.
            pending = new ChatLockSettingsBuilder()
                    .hideLockedChats(settings.hideLockedChats())
                    .secretCode(null)
                    .build();

            if (!isSecretCodeValid(settings)) {
                results.add(SyncdIndexUtils.malformedActionValue(collectionName().name()));
                continue;
            }

            pending.setSecretCode(settings.secretCode().orElse(null));
            results.add(MutationApplicationResult.success());
        }

        if (pending != null) {
            client.store().setChatLockSettings(pending);
        } else {
            LOGGER.warning("ChatLockSettingsSync: mutations parse failed");
        }

        return results;
    }

    /**
     * Validates the secret code within a {@link ChatLockSettings} instance.
     *
     * <p>Per WhatsApp Web {@code WAWebChatLockSettingsSync.applyMutations}: when
     * {@code secretCode} is non-null, the following must all hold:
     * <ul>
     *   <li>{@code encoding}, {@code transformedData}, {@code transformer}, and
     *       {@code transformerArg} must all be non-null/non-empty</li>
     *   <li>{@code transformer} must equal
     *       {@link UserPassword.Transformer#PBKDF2_HMAC_SHA512}</li>
     *   <li>The {@code transformerArg} list must contain entries with keys
     *       {@code "iterations"} and {@code "salt"}, each with a non-null value
     *       of the appropriate type ({@code asUnsignedInteger} for iterations,
     *       {@code asBlob} for salt)</li>
     * </ul>
     *
     * <p>If {@code secretCode} is absent, no validation is needed and the method
     * returns {@code true}.
     * @param settings the chat lock settings to validate
     * @return {@code true} if the secret code is absent or well-formed,
     *         {@code false} if malformed
     */
    private boolean isSecretCodeValid(ChatLockSettings settings) {
        var secretCode = settings.secretCode();
        if (secretCode.isEmpty()) {
            return true;
        }

        var password = secretCode.get();

        var encoding = password.encoding();
        var transformedData = password.transformedData();
        var transformer = password.transformer();
        var transformerArgs = password.transformerArg();

        if (transformerArgs.isEmpty() || transformer.isEmpty() || transformedData.isEmpty() || encoding.isEmpty()) {
            return false;
        }

        if (transformer.get() != UserPassword.Transformer.PBKDF2_HMAC_SHA512) {
            return false;
        }

        //   return t.value == null || (t.key === "iterations" ? e.iterations = t.value.asUnsignedInteger
        //                              : t.key === "salt" && (e.salt = t.value.asBlob)), e
        // }, {})
        var hasIterations = false;
        var hasSalt = false;
        for (var arg : transformerArgs) {
            var value = arg.value().orElse(null);
            if (value == null) {
                continue;
            }
            var key = arg.key().orElse(null);
            if ("iterations".equals(key)) {
                // WA Web reads Value.asUnsignedInteger directly; Cobalt uses the oneof accessor
                // and checks the variant type to match the same semantics
                if (value.value().orElse(null) instanceof UserPassword.TransformerArg.ValueSpec.AsUnsignedInteger ui
                        && ui.asUnsignedInteger() != null) {
                    hasIterations = true;
                }
            } else if ("salt".equals(key)) {
                if (value.value().orElse(null) instanceof UserPassword.TransformerArg.ValueSpec.AsBlob blob
                        && blob.asBlob() != null) {
                    hasSalt = true;
                }
            }
        }

        return hasIterations && hasSalt;
    }
}
