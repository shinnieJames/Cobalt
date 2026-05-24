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
 * Applies the {@code setting_locale} app-state sync action that propagates the
 * user's preferred display locale across linked devices.
 *
 * @apiNote
 * Drives the cross-device locale switch: when the primary device picks
 * a new UI language the resulting BCP-47 string fans out across the
 * {@link SyncPatchType#CRITICAL_BLOCK} collection so every companion
 * surface re-renders in the same locale. The mutation index has no
 * variable parts and is always
 * {@snippet :
 *     ["setting_locale"]
 * }
 *
 * @implNote
 * This implementation persists the new locale into
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#setLocale(String)}
 * and notifies every registered
 * {@link com.github.auties00.cobalt.client.WhatsAppClientListener#onLocaleChanged}
 * on a virtual thread, replacing WA Web's
 * {@code frontendSendAndReceive("setLocale", {locale, priority: PHONE,
 * reload: false})} RPC because Cobalt has no UI layer to delegate to.
 * The Windows-Electron short-circuit that returns
 * {@link MutationApplicationResult#skipped()} on the WA Web side is
 * intentionally absent because Cobalt is not an Electron host.
 */
@WhatsAppWebModule(moduleName = "WAWebLocaleSettingSync")
public final class LocaleSettingHandler implements WebAppStateActionHandler {

    /**
     * Constructs a new singleton {@link LocaleSettingHandler}.
     */
    @WhatsAppWebExport(moduleName = "WAWebLocaleSettingSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public LocaleSettingHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLocaleSettingSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return LocaleSetting.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLocaleSettingSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return LocaleSetting.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLocaleSettingSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return LocaleSetting.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation orders the checks the same way as WA Web's
     * per-mutation closure: filter by operation, validate the action
     * payload, return
     * {@link MutationApplicationResult#skipped()} on a {@code null}
     * locale, then commit the new locale to the store and fan it out
     * to listeners. Each listener notification runs on its own virtual
     * thread so a slow listener never blocks the sync pipeline. WA
     * Web's batch tally counters and the
     * {@code WALogger.WARN}/{@code WALogger.LOG} emissions are not
     * modelled because they have no behavioural impact.
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

        var oldLocale = client.store().locale().orElse(null);
        client.store().setLocale(newLocale);
        for (var listener : client.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onLocaleChanged(client, oldLocale, newLocale));
        }

        return MutationApplicationResult.success();
    }

}
