package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.setting.LocaleSetting;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Applies {@code setting_locale} mutations decoded from app state sync.
 *
 * <p>Handles the {@code LocaleSetting} sync action in the
 * {@link SyncPatchType#CRITICAL_BLOCK} collection. A mutation of this type
 * carries the user's preferred locale (e.g. {@code "en_US"}) as chosen on
 * the paired companion device, and instructs every other linked client to
 * update its own UI locale to match.
 *
 * <p>On WhatsApp Web the handler forwards the new locale to the frontend
 * l10n layer via {@code WAWebBackendApi.frontendSendAndReceive("setLocale", ...)}.
 * Cobalt does not ship a UI layer, so instead it:
 * <ol>
 *   <li>writes the new locale into {@link com.github.auties00.cobalt.store.WhatsAppStore},</li>
 *   <li>dispatches a {@code onLocaleChanged} event to every registered
 *       {@link com.github.auties00.cobalt.client.WhatsAppClientListener} on a
 *       virtual thread.</li>
 * </ol>
 *
 * <p>WA Web skips the mutation entirely on the Windows-hybrid Electron
 * build because the host OS owns locale on that platform. Cobalt is not
 * an Electron host and has no IPC bridge to delegate to, so the gate is
 * intentionally absent — every mutation is applied to the Cobalt store
 * regardless of which platform descriptor the companion identifies as.
 */
@WhatsAppWebModule(moduleName = "WAWebLocaleSettingSync")
public final class LocaleSettingHandler implements WebAppStateActionHandler {

    /**
     * Creates a new {@code LocaleSettingHandler}.
     *
     * <p>The constructor is private because callers should always go through
     * {@link #INSTANCE}, matching the WA Web module-level singleton.
     */
    @WhatsAppWebExport(moduleName = "WAWebLocaleSettingSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public LocaleSettingHandler() {

    }

    /**
     * Returns the action name this handler processes.
     * @return the constant {@link LocaleSetting#ACTION_NAME}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLocaleSettingSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return LocaleSetting.ACTION_NAME;
    }

    /**
     * Returns the sync collection this handler's action belongs to.
     *
     * <p>On WA Web this is set on the prototype inside the constructor as
     * {@code this.collectionName = CollectionName.CriticalBlock}.
     * @return the constant {@link LocaleSetting#COLLECTION_NAME}, always
     *         {@link SyncPatchType#CRITICAL_BLOCK}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLocaleSettingSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return LocaleSetting.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version this handler supports.
     * @return the constant {@link LocaleSetting#ACTION_VERSION}, always {@code 3}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLocaleSettingSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return LocaleSetting.ACTION_VERSION;
    }

    /**
     * Applies a single decoded locale mutation and returns the detailed result.
     *
     * <p>This method implements the body of the WA Web per-mutation callback
     * passed to {@code Promise.all(a.map(async e => { ... }))} inside
     * {@code applyMutations(a)}. The order of checks mirrors WA Web exactly,
     * minus the Windows-hybrid runtime gate which does not apply to Cobalt:
     * <ol>
     *   <li><b>Operation filter</b> — WA Web falls through to the final
     *       {@code p++; return {actionState: Unsupported};} branch for any
     *       operation other than {@code "set"}. Cobalt returns
     *       {@link MutationApplicationResult#unsupported()}.</li>
     *   <li><b>Missing localeSetting payload</b> — WA Web reads
     *       {@code var n = e.value, a = n.localeSetting; if (!a) { i++;
     *       return malformedActionValue(this.collectionName); }}. Cobalt
     *       checks that the decoded action is a {@link LocaleSetting} and
     *       returns {@link MutationApplicationResult#malformed()} otherwise.</li>
     *   <li><b>Null locale field</b> — WA Web: {@code var s = a.locale;
     *       if (s == null) { l++; return {actionState: Skipped}; }}. Cobalt
     *       returns {@link MutationApplicationResult#skipped()}.</li>
     *   <li><b>Apply the new locale</b> — WA Web awaits
     *       {@code WAWebBackendApi.frontendSendAndReceive("setLocale",
     *       {locale: s, priority: L10N_PRIORITY.PHONE, reload: false})}
     *       to push the new locale into the l10n subsystem of the web UI.
     *       Cobalt has no frontend, so it persists the locale into
     *       {@link com.github.auties00.cobalt.store.WhatsAppStore#setLocale(String)}
     *       and notifies listeners via {@code onLocaleChanged(client, oldLocale, newLocale)}.</li>
     *   <li><b>Success</b> — returns {@link MutationApplicationResult#success()}.</li>
     * </ol>
     *
     * <p>WA Web also wraps the body in a {@code WAWebEnvironment.isWindows}
     * short-circuit that returns {@code Skipped} on the Windows-hybrid
     * Electron build, because the host OS owns locale on that platform and
     * the renderer delegates via the hybrid IPC bridge. Cobalt has no
     * Electron host and no IPC bridge — the locale must be applied to the
     * store unconditionally so listeners can observe the change. The gate
     * is therefore intentionally absent in Cobalt's adaptation.
     *
     * <p>WA Web also increments local telemetry counters ({@code i}, {@code l},
     * {@code p}), appends each applied locale to a bounded {@code _} array
     * ({@code if (_.length < 3) _.push(s)}), and emits {@code WALogger.LOG}/
     * {@code WALogger.WARN} messages at the end of the batch. These are
     * intentionally omitted in Cobalt as telemetry/logging noise with no
     * behavioral impact.
     *
     * <p>WA Web wraps the whole per-mutation body in a {@code try/catch} that
     * swallows any exception and returns {@code {actionState: Failed}}. In
     * Cobalt, exceptions are allowed to propagate and the configured
     * {@code WhatsAppClientErrorHandler} decides recovery, per Cobalt's
     * pluggable error model.
     * @param client   the WhatsApp client the mutation is being applied to
     * @param mutation the trusted, decoded mutation to apply
     * @return {@link MutationApplicationResult#skipped()} on null locale;
     *         {@link MutationApplicationResult#unsupported()} for
     *         non-{@code SET} operations; {@link MutationApplicationResult#malformed()}
     *         if the decoded action is not a {@link LocaleSetting};
     *         {@link MutationApplicationResult#success()} otherwise
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLocaleSettingSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof LocaleSetting setting)) {
            return MutationApplicationResult.malformed();
        }

        var newLocale = setting.locale().orElse(null);
        if (newLocale == null) {
            return MutationApplicationResult.skipped();
        }

        // ADAPTED: WAWebLocaleSettingSync.applyMutations:
        //   yield WAWebBackendApi.frontendSendAndReceive("setLocale",
        //       {locale: s, priority: L10N_PRIORITY.PHONE, reload: false});
        // Cobalt has no frontend l10n layer, so the locale is persisted into
        // the store and broadcast to application listeners instead.
        var oldLocale = client.store().locale().orElse(null);
        client.store().setLocale(newLocale); // ADAPTED: WAWebLocaleSettingSync.applyMutations -> frontendSendAndReceive("setLocale", ...)
        for (var listener : client.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onLocaleChanged(client, oldLocale, newLocale)); // ADAPTED: WAWebLocaleSettingSync -> notify Cobalt listeners instead of frontend IPC
        }

        // NO_WA_BASIS: the following WA Web telemetry is intentionally dropped:
        //   - i/l/p counters and the trailing WALogger.LOG/WARN calls
        //   - the bounded "_.push(s)" tracker (unused even on WA Web)
        return MutationApplicationResult.success();
    }

}
