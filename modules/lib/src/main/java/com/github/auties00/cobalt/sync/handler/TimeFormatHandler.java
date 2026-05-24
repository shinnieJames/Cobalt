package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.device.TimeFormatAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Mirrors the user's 12/24-hour clock preference across linked devices.
 *
 * @apiNote
 * Cobalt embedders never invoke this handler directly; the sync dispatcher
 * routes incoming {@code time_format} mutations here whenever the user
 * toggles 12/24-hour display on another linked device. The handler writes
 * the boolean preference into
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#setTwentyFourHourFormat(boolean)}
 * so any UI built on top of Cobalt can render timestamps using the
 * user-chosen format.
 */
@WhatsAppWebModule(moduleName = "WAWebTimeFormatSync")
public final class TimeFormatHandler implements WebAppStateActionHandler {

    /**
     * Constructs the handler.
     *
     * @apiNote
     * The handler is stateless; Cobalt's sync registry holds a single
     * instance per client.
     */
    @WhatsAppWebExport(moduleName = "WAWebTimeFormatSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public TimeFormatHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebTimeFormatSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return TimeFormatAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebTimeFormatSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return TimeFormatAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebTimeFormatSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return TimeFormatAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation mirrors WA Web's per-mutation closure inside
     * {@code WAWebTimeFormatSync.applyMutations}: non-{@code SET}
     * operations are unsupported; a missing {@code timeFormatAction} or
     * {@code isTwentyFourHourFormatEnabled} value is malformed; otherwise
     * the boolean is written to
     * {@link com.github.auties00.cobalt.store.WhatsAppStore#setTwentyFourHourFormat(boolean)}
     * in place of WA Web's
     * {@code WAWebBackendApi.frontendFireAndForget("setIs24Hour", ...)}
     * shell hop. The {@code md_syncd_24_hour_time_format_sync_enabled}
     * AB-prop is never consulted because WA Web itself never reads it;
     * the prop exists in {@code WAWebABPropsConfigs} but no module uses
     * it. The trailing {@code WALogger.WARN} unsupported counter is
     * omitted as telemetry.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebTimeFormatSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof TimeFormatAction action)) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        client.store().setTwentyFourHourFormat(action.isTwentyFourHourFormatEnabled());

        return MutationApplicationResult.success();
    }

}
