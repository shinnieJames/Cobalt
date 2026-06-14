package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.listener.linked.LinkedLocaleChangedListener;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClientListener;
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
 * <p>The action carries a BCP-47 string fanned out across the
 * {@link SyncPatchType#CRITICAL_BLOCK} collection so every companion surface
 * re-renders in the same locale. The mutation index has no variable parts and
 * is always
 * {@snippet :
 *     ["setting_locale"]
 * }
 *
 * @implNote
 * This implementation persists the locale into
 * {@link com.github.auties00.cobalt.store.AccountStore#setLocale(String)} and
 * notifies every registered
 * {@link LinkedWhatsAppClientListener#onLocaleChanged(LinkedWhatsAppClient, String, String)}
 * on its own virtual thread, since Cobalt has no UI layer to delegate to.
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
     * <p>Rejects non-{@link SyncdOperation#SET} operations as
     * {@link MutationApplicationResult#unsupported()}, an absent action payload
     * as {@link MutationApplicationResult#malformed()}, and a {@code null}
     * locale as {@link MutationApplicationResult#skipped()}. Otherwise the new
     * locale is committed to the store and fanned out to listeners.
     *
     * @implNote
     * This implementation dispatches each listener notification on its own
     * virtual thread so a slow listener never blocks the sync pipeline.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebLocaleSettingSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(LinkedWhatsAppClient client, DecryptedMutation.Trusted mutation) {
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

        var oldLocale = client.store().accountStore().locale().orElse(null);
        client.store().accountStore().setLocale(newLocale);
        for (var listener : client.store().listeners()) {
            if (listener instanceof LinkedLocaleChangedListener typed) {
                Thread.startVirtualThread(() -> typed.onLocaleChanged(client, oldLocale, newLocale));
            }
        }

        return MutationApplicationResult.success();
    }

}
