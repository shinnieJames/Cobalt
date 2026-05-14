package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.device.pairing.ClientPlatformType;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.setting.SettingsSyncAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.props.ABProp;
import com.github.auties00.cobalt.props.ABPropsService;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Applies generic settings sync mutations decoded from app state sync.
 *
 * <p>Handles the {@code settings_sync} action in the
 * {@link SyncPatchType#REGULAR_LOW} collection. The action carries a single
 * field of {@link SettingsSyncAction} keyed by a {@link SettingsSyncAction.SettingKey}
 * enum value, scoped to either the {@code "app"} (global) namespace or to a
 * specific chat JID. The mutation index is a 4-tuple of strings:
 * {@code [userIndex, platform, settingKey, scope]}.
 *
 * <p>WhatsApp Web routes the resolved {@code field/value/scope} triple through
 * {@code WAWebSettingsSyncHelpers.applySettingUpdate} which forwards it via
 * {@code WAWebBackendApi.frontendSendAndReceive("applyAppSetting" |
 * "applyPerChatSetting", ...)} to the WhatsApp Desktop native shell. Cobalt
 * has no native shell, so the equivalent Cobalt-side action is to persist the
 * value into the corresponding {@link com.github.auties00.cobalt.store.WhatsAppStore}
 * field where one exists. Settings without a Cobalt store equivalent still
 * resolve to {@link MutationApplicationResult#success()} so that the WA-side
 * action state telemetry stays consistent.
 */
@WhatsAppWebModule(moduleName = "WAWebSettingsSync")
@WhatsAppWebModule(moduleName = "WAWebSettingsSyncHelpers")
public final class SettingsSyncHandler implements WebAppStateActionHandler {
    /**
     * The {@code app} scope literal used by WA Web for global (non-per-chat)
     * settings.
     */
    private static final String APP_SCOPE = "app";

    /**
     * The AB-props service consulted before applying any mutation.
     */
    private final ABPropsService abPropsService;

    /**
     * The number of components expected in the {@code indexParts} array of a
     * settings sync mutation, namely {@code [userIndex, platform, settingKey,
     * scope]}.
     */
    private static final int INDEX_PARTS_LENGTH = 4;

    /**
     * Creates a new {@code SettingsSyncHandler}.
     *
     * @param abPropsService the AB-props service consulted on every
     *                       mutation
     */
    @WhatsAppWebExport(moduleName = "WAWebSettingsSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public SettingsSyncHandler(ABPropsService abPropsService) {
        this.abPropsService = abPropsService;
    }

    /**
     * Returns the action name this handler processes.
     * @return the constant {@link SettingsSyncAction#ACTION_NAME}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSettingsSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return SettingsSyncAction.ACTION_NAME;
    }

    /**
     * Returns the sync collection this handler's action belongs to.
     *
     * <p>On WA Web this is set on the prototype inside the constructor as
     * {@code this.collectionName = CollectionName.RegularLow}.
     * @return the constant {@link SettingsSyncAction#COLLECTION_NAME}, always
     *         {@link SyncPatchType#REGULAR_LOW}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSettingsSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return SettingsSyncAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version this handler supports.
     * @return the constant {@link SettingsSyncAction#ACTION_VERSION}, always {@code 1}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSettingsSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return SettingsSyncAction.ACTION_VERSION;
    }

    /**
     * Applies a batch of settings sync mutations.
     *
     * <p>Per WhatsApp Web {@code WAWebSettingsSync.applyMutations}:
     * <ol>
     *   <li>If the {@code settings_sync_enabled} primary feature or AB prop is
     *       disabled, every mutation in the batch resolves to
     *       {@link MutationApplicationResult#unsupported()}.</li>
     *   <li>The handler builds a map keyed by {@code JSON.stringify(indexParts)}.
     *       Only {@code SET} operations are inserted; among colliding entries,
     *       only the one with the highest {@code timestamp} is kept.</li>
     *   <li>The result list is then assembled by mapping each input mutation:
     *       <ul>
     *         <li>If the index has no entry in the map (i.e. only non-SET
     *             mutations exist for that index), result is
     *             {@link MutationApplicationResult#malformed()}.</li>
     *         <li>If the entry is not the current mutation, result is
     *             {@link MutationApplicationResult#skipped()}.</li>
     *         <li>Otherwise the per-mutation apply path runs and the result
     *             reflects the validation/apply outcome.</li>
     *       </ul></li>
     * </ol>
     *
     * <p>The WA Web inner per-mutation work is wrapped in {@code Promise.all}
     * because each item awaits {@code applySettingUpdate}. Cobalt's
     * {@link #applyMutation(WhatsAppClient, DecryptedMutation.Trusted)}
     * is synchronous (running on a virtual thread) so the equivalent here is
     * just a sequential loop preserving result ordering.
     * @param client    the WhatsApp client the mutations are being applied to
     * @param mutations the batch of mutations to apply
     * @return a list of results parallel to the input
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSettingsSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public List<MutationApplicationResult> applyMutationBatch(WhatsAppClient client, List<DecryptedMutation.Trusted> mutations) {
        if (!isSettingsSyncEnabled(client)) {
            var unsupported = new ArrayList<MutationApplicationResult>(mutations.size());
            for (var ignored : mutations) {
                unsupported.add(MutationApplicationResult.unsupported());
            }
            return unsupported;
        }

        var latestByIndex = new HashMap<String, DecryptedMutation.Trusted>();
        for (var mutation : mutations) {
            if (mutation.operation() != SyncdOperation.SET) {
                continue;
            }
            var key = mutation.index();
            var existing = latestByIndex.get(key);
            if (existing == null || mutation.timestamp().compareTo(existing.timestamp()) > 0) {
                latestByIndex.put(key, mutation);
            }
        }

        var results = new ArrayList<MutationApplicationResult>(mutations.size());
        for (var mutation : mutations) {
            var latest = latestByIndex.get(mutation.index());
            if (latest == null) {
                results.add(MutationApplicationResult.malformed());
            } else if (latest != mutation) {
                results.add(MutationApplicationResult.skipped());
            } else {
                results.add(applyOne(client, mutation));
            }
        }
        return results;
    }

    /**
     * Applies a single decoded settings sync mutation and returns the detailed
     * result.
     *
     * <p>This method is the entry point used by callers that bypass the batch
     * deduplication logic of
     * {@link #applyMutationBatch(WhatsAppClient, List)}. It performs
     * the same {@code settings_sync_enabled} gating that the batch entry
     * point does, then defers to {@link #applyOne(WhatsAppClient, DecryptedMutation.Trusted)}
     * for the per-mutation validation and store update.
     *
     * <p>For non-{@code SET} operations the method returns
     * {@link MutationApplicationResult#unsupported()} as a defensive shortcut.
     * In the WA Web batch entry point, non-SET mutations are filtered out of
     * the latest map and resolve to {@code Malformed} or {@code Skipped} via
     * the cross-mutation lookup; that lookup is unavailable when applying a
     * single mutation in isolation, so this branch reflects the
     * {@code Unsupported} sentinel that WA Web reserves for "no SET mutation
     * for this index".
     * @param client   the WhatsApp client the mutation is being applied to
     * @param mutation the trusted, decoded mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebSettingsSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (!isSettingsSyncEnabled(client)) {
            return MutationApplicationResult.unsupported();
        }
        // ADAPTED: WAWebSettingsSync.applyMutations — non-SET operations are filtered out
        // by the batch dedup map and never reach the per-mutation apply path.
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }
        return applyOne(client, mutation);
    }

    /**
     * Validates and applies a single SET settings sync mutation, mirroring
     * {@code WAWebSettingsSync.$SettingsSync$p_1}.
     *
     * <p>The order of checks mirrors WA Web exactly:
     * <ol>
     *   <li><b>Index parsing</b> — the {@code indexParts} array must have
     *       exactly {@value #INDEX_PARTS_LENGTH} elements; otherwise the
     *       result is {@link MutationApplicationResult#malformed()}.</li>
     *   <li><b>Index slot {@code [1]} → platform</b> — parsed as a
     *       {@link SettingsSyncAction.SettingPlatform}. Mutations are only
     *       accepted when the platform is {@link SettingsSyncAction.SettingPlatform#WEB}
     *       or, on the Windows hybrid client, {@link SettingsSyncAction.SettingPlatform#HYBRID}.
     *       Any other platform (including unparseable values) yields
     *       {@link MutationApplicationResult#skipped()}.</li>
     *   <li><b>Index slot {@code [2]} → settingKey</b> — parsed as a
     *       {@link SettingsSyncAction.SettingKey}. An unparseable or unknown
     *       key (including {@link SettingsSyncAction.SettingKey#SETTING_KEY_UNKNOWN},
     *       which has no field mapping in WA Web's
     *       {@code WAWebSettingsSyncConst.SETTING_KEY_TO_FIELD}) yields
     *       {@link MutationApplicationResult#malformed()}.</li>
     *   <li><b>Index slot {@code [3]} → scope</b> — passed through verbatim.
     *       In WA Web this is the {@code app} literal for global settings or
     *       a chat JID for per-chat overrides.</li>
     *   <li><b>Settings sync action presence</b> — the decoded action must be
     *       a {@link SettingsSyncAction}; otherwise
     *       {@link MutationApplicationResult#malformed()}.</li>
     *   <li><b>Field presence</b> — the specific protobuf field that backs
     *       the {@code settingKey} must be present (i.e. non-null) in the
     *       action; otherwise {@link MutationApplicationResult#malformed()}.
     *       In WA Web this is {@code if (b === void 0) return Malformed}.</li>
     *   <li><b>Apply</b> — WA Web forwards
     *       {@code (field, value, scope)} to the Windows shell via
     *       {@code WAWebSettingsSyncHelpers.applySettingUpdate}. Cobalt
     *       persists the value into the corresponding store field if one
     *       exists, otherwise treats the apply as a no-op success. Throws
     *       are mapped to {@link MutationApplicationResult#failed()} to
     *       mirror WA Web's {@code try/catch → Failed} branch.</li>
     * </ol>
     *
     * <p>WA Web also emits {@code WALogger.WARN}/{@code WALogger.ERROR}
     * messages along the failure paths; those are intentionally omitted in
     * Cobalt as logging noise with no behavioral impact.
     * @param client   the WhatsApp client the mutation is being applied to
     * @param mutation the trusted, decoded mutation to apply
     * @return the detailed application result
     */
    @WhatsAppWebExport(moduleName = "WAWebSettingsSync", exports = "$SettingsSync$p_1", adaptation = WhatsAppAdaptation.ADAPTED)
    private MutationApplicationResult applyOne(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        JSONArray indexArray;
        try {
            indexArray = JSON.parseArray(mutation.index());
        } catch (Exception exception) {
            return MutationApplicationResult.malformed(); // ADAPTED: WAWebSettingsSync.$SettingsSync$p_1 — Cobalt parses the JSON eagerly so a malformed JSON also yields Malformed
        }
        if (indexArray == null || indexArray.size() != INDEX_PARTS_LENGTH) {
            return MutationApplicationResult.malformed();
        }

        // n[0] (user index) is read but never used after assignment in WA Web — Cobalt skips it.
        var platformValue = indexArray.getString(1);
        var settingKeyValue = indexArray.getString(2);
        var scope = indexArray.getString(3);

        // var g = f === WEB || (isWindows && f === HYBRID); if (!g) return Skipped
        var platform = parsePlatform(platformValue);
        if (!appliesToCurrentPlatform(client, platform)) {
            return MutationApplicationResult.skipped();
        }

        var settingKey = parseSettingKey(settingKeyValue);
        if (settingKey == null) {
            return MutationApplicationResult.malformed();
        }

        // SETTING_KEY_UNKNOWN has no field mapping in WAWebSettingsSyncConst.SETTING_KEY_TO_FIELD,
        // so it falls into this branch.
        if (settingKey == SettingsSyncAction.SettingKey.SETTING_KEY_UNKNOWN) {
            return MutationApplicationResult.malformed();
        }

        if (!(mutation.value().action().orElse(null) instanceof SettingsSyncAction action)) {
            return MutationApplicationResult.malformed();
        }

        if (!hasFieldFor(action, settingKey)) {
            return MutationApplicationResult.malformed();
        }

        try {
            applySettingUpdate(client, action, settingKey, scope); // ADAPTED: WAWebSettingsSyncHelpers.applySettingUpdate -> direct store mutation
        } catch (RuntimeException exception) {
            return MutationApplicationResult.failed();
        }

        return MutationApplicationResult.success();
    }

    /**
     * Returns whether the {@code settings_sync_enabled} feature is enabled for
     * the given client.
     *
     * <p>Mirrors the WA Web {@code h()} helper which returns
     * {@code WAWebPrimaryFeatures.primaryFeatureEnabled("settings_sync_enabled") === true
     * && WAWebABProps.getABPropConfigValue("settings_sync_enabled") === true}.
     * @param client the WhatsApp client whose feature flags are being inspected
     * @return {@code true} if both the primary feature and the AB prop are enabled
     */
    private boolean isSettingsSyncEnabled(WhatsAppClient client) {
        return client.store().primaryFeatures().contains("settings_sync_enabled")
                && abPropsService.getBool(ABProp.SETTINGS_SYNC_ENABLED);
    }

    /**
     * Parses the platform string from the mutation index into a
     * {@link SettingsSyncAction.SettingPlatform} enum value.
     *
     * <p>Mirrors WA Web's {@code SettingPlatform.cast(Number(l))}: any value
     * that is not a numeric string mapping to a known enum index returns
     * {@code null} so the caller can take the {@code Skipped} branch.
     * @param value the raw platform string from {@code indexParts[1]}, may be {@code null}
     * @return the parsed {@link SettingsSyncAction.SettingPlatform}, or
     *         {@code null} if the value cannot be mapped to a known enum entry
     */
    private SettingsSyncAction.SettingPlatform parsePlatform(String value) {
        if (value == null) {
            return null;
        }
        try {
            var index = Integer.parseInt(value);
            for (var platform : SettingsSyncAction.SettingPlatform.values()) {
                if (platform.index() == index) {
                    return platform;
                }
            }
            return null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * Parses the setting key string from the mutation index into a
     * {@link SettingsSyncAction.SettingKey} enum value.
     *
     * <p>Mirrors WA Web's {@code SettingKey.cast(Number(p))}: any value that
     * is not a numeric string mapping to a known enum index returns
     * {@code null} so the caller can take the {@code Malformed} branch.
     * @param value the raw setting key string from {@code indexParts[2]}, may be {@code null}
     * @return the parsed {@link SettingsSyncAction.SettingKey}, or {@code null}
     *         if the value cannot be mapped to a known enum entry
     */
    private SettingsSyncAction.SettingKey parseSettingKey(String value) {
        if (value == null) {
            return null;
        }
        try {
            var index = Integer.parseInt(value);
            for (var settingKey : SettingsSyncAction.SettingKey.values()) {
                if (settingKey.index() == index) {
                    return settingKey;
                }
            }
            return null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * Returns whether the given platform should be applied on the current
     * client.
     *
     * <p>Per WA Web {@code WAWebSettingsSync.$SettingsSync$p_1}: a mutation is
     * applied only when its platform is {@link SettingsSyncAction.SettingPlatform#WEB}
     * or, on the Windows hybrid runtime, {@link SettingsSyncAction.SettingPlatform#HYBRID}.
     * WA Web detects the Windows hybrid runtime through
     * {@code WAWebEnvironment.isWindows}; Cobalt has no per-client runtime
     * gate, so it falls back to the paired device platform — matching the
     * convention used by {@link LocaleSettingHandler}.
     * @param client   the WhatsApp client the mutation is being applied to
     * @param platform the parsed platform from {@code indexParts[1]}, may be {@code null}
     * @return {@code true} if the mutation should be applied on this client
     */
    private boolean appliesToCurrentPlatform(WhatsAppClient client, SettingsSyncAction.SettingPlatform platform) {
        if (platform == null) {
            return false;
        }
        return switch (platform) {
            case WEB -> true;
            // ADAPTED: WAWebSettingsSync.$SettingsSync$p_1 -> isWindows check; Cobalt uses paired device platform
            case HYBRID -> client.store().device() != null && client.store().device().platform() == ClientPlatformType.WINDOWS;
            default -> false;
        };
    }

    /**
     * Returns whether the protobuf field that backs the given setting key is
     * present on the action.
     *
     * <p>Mirrors WA Web's {@code var b = C[y]; if (b === void 0)} guard. Each
     * {@link SettingsSyncAction.SettingKey} maps to a single field of
     * {@link SettingsSyncAction}; for {@code String}, {@code Integer} and
     * enum-typed fields the {@code Optional}/{@code OptionalInt} accessors
     * preserve nullability and the check is exact. For {@code Boolean}-typed
     * fields the public boolean accessor coalesces {@code null} to
     * {@code false} and a strict "is field present" check is impossible from
     * outside the {@link SettingsSyncAction} package; per Cobalt's nullable
     * boolean convention these fields are treated as always present, which
     * matches the deserialization model where a {@code BOOL} field defaults
     * to its zero value when absent on the wire.
     *
     * <p>The {@link SettingsSyncAction.SettingKey#SETTING_KEY_UNKNOWN} key is
     * filtered out by the caller (it has no field mapping in WA Web's
     * {@code SETTING_KEY_TO_FIELD}), so the {@code SETTING_KEY_UNKNOWN}
     * branch here only exists to satisfy exhaustiveness.
     * @param action the decoded settings sync action
     * @param key    the setting key whose field should be checked
     * @return {@code true} if the field is present on the action
     */
    private boolean hasFieldFor(SettingsSyncAction action, SettingsSyncAction.SettingKey key) {
        return switch (key) {
            // ADAPTED: nullable Boolean — coalesced to false by accessor; presence not strictly checkable
            case START_AT_LOGIN -> true;
            case MINIMIZE_TO_TRAY -> true;
            case LANGUAGE -> action.language().isPresent();
            case REPLACE_TEXT_WITH_EMOJI -> true;
            case BANNER_NOTIFICATION_DISPLAY_MODE -> action.bannerNotificationDisplayMode().isPresent();
            case UNREAD_COUNTER_BADGE_DISPLAY_MODE -> action.unreadCounterBadgeDisplayMode().isPresent();
            case IS_MESSAGES_NOTIFICATION_ENABLED -> true;
            case IS_CALLS_NOTIFICATION_ENABLED -> true;
            case IS_REACTIONS_NOTIFICATION_ENABLED -> true;
            case IS_STATUS_REACTIONS_NOTIFICATION_ENABLED -> true;
            case IS_TEXT_PREVIEW_FOR_NOTIFICATION_ENABLED -> true;
            case DEFAULT_NOTIFICATION_TONE_ID -> action.defaultNotificationToneId().isPresent();
            case GROUP_DEFAULT_NOTIFICATION_TONE_ID -> action.groupDefaultNotificationToneId().isPresent();
            case APP_THEME -> action.appTheme().isPresent();
            case WALLPAPER_ID -> action.wallpaperId().isPresent();
            case IS_DOODLE_WALLPAPER_ENABLED -> true;
            case FONT_SIZE -> action.fontSize().isPresent();
            case IS_PHOTOS_AUTODOWNLOAD_ENABLED -> true;
            case IS_AUDIOS_AUTODOWNLOAD_ENABLED -> true;
            case IS_VIDEOS_AUTODOWNLOAD_ENABLED -> true;
            case IS_DOCUMENTS_AUTODOWNLOAD_ENABLED -> true;
            case DISABLE_LINK_PREVIEWS -> true;
            case NOTIFICATION_TONE_ID -> action.notificationToneId().isPresent();
            case MEDIA_UPLOAD_QUALITY -> action.mediaUploadQuality().isPresent();
            case IS_SPELL_CHECK_ENABLED -> true;
            case IS_ENTER_TO_SEND_ENABLED -> true;
            case IS_GROUP_MESSAGE_NOTIFICATION_ENABLED -> true;
            case IS_GROUP_REACTIONS_NOTIFICATION_ENABLED -> true;
            case IS_STATUS_NOTIFICATION_ENABLED -> true;
            case STATUS_NOTIFICATION_TONE_ID -> action.statusNotificationToneId().isPresent();
            case SHOULD_PLAY_SOUND_FOR_CALL_NOTIFICATION -> true;
            // SETTING_KEY_UNKNOWN has no entry in WAWebSettingsSyncConst.SETTING_KEY_TO_FIELD
            // and is filtered out by applyOne before this method is reached.
            case SETTING_KEY_UNKNOWN -> false;
        };
    }

    /**
     * Forwards the resolved {@code (settingKey, value, scope)} triple to the
     * appropriate Cobalt-side persistence path.
     *
     * <p>WA Web routes the triple via
     * {@code WAWebSettingsSyncHelpers.applySettingUpdate} to the WhatsApp
     * Desktop native shell. Cobalt has no shell, so the equivalent action is
     * to write into a {@link com.github.auties00.cobalt.store.WhatsAppStore}
     * field where one exists. Settings without a Cobalt store equivalent are
     * intentionally a no-op so that the WA-side action state telemetry stays
     * consistent. Per-chat overrides (i.e. any non-{@code "app"} scope) are
     * also a no-op for the same reason: Cobalt does not maintain a per-chat
     * settings store.
     * @param client     the WhatsApp client the mutation is being applied to
     * @param action     the decoded settings sync action carrying the new value
     * @param settingKey the setting key whose value should be persisted
     * @param scope      either {@link #APP_SCOPE} for global settings or a chat JID string
     */
    @WhatsAppWebExport(moduleName = "WAWebSettingsSyncHelpers", exports = "applySettingUpdate", adaptation = WhatsAppAdaptation.ADAPTED)
    private void applySettingUpdate(WhatsAppClient client,
                                    SettingsSyncAction action,
                                    SettingsSyncAction.SettingKey settingKey,
                                    String scope) {
        // ADAPTED: WAWebSettingsSyncHelpers.applySettingUpdate — Cobalt only has store fields for
        // the global "app" scope; per-chat overrides have no Cobalt counterpart and are dropped.
        if (!APP_SCOPE.equals(scope)) {
            return;
        }
        switch (settingKey) {
            // ADAPTED: WAWebSettingsSyncHelpers.applySettingUpdate("language", action.language, "app") -> store.setLocale
            case LANGUAGE -> action.language().ifPresent(client.store()::setLocale);
            // ADAPTED: WAWebSettingsSyncHelpers.applySettingUpdate("disableLinkPreviews", action.disableLinkPreviews, "app") -> store.setDisableLinkPreviews
            case DISABLE_LINK_PREVIEWS -> client.store().setDisableLinkPreviews(action.disableLinkPreviews());
            // ADAPTED: WAWebSettingsSyncHelpers.applySettingUpdate — every other setting is a UI/desktop
            // shell concern (font size, app theme, notification tones, etc.) with no Cobalt store
            // equivalent. The mutation is still considered Successful by WA Web's contract.
            default -> {
                // NO_WA_BASIS (intentional): Cobalt has no native shell to forward the value to.
            }
        }
    }
}
