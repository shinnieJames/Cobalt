package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.setting.LocaleSetting;
import com.github.auties00.cobalt.model.sync.action.setting.LocaleSettingBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Builds outgoing locale-setting sync mutations.
 *
 * <p>The factory is the outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.LocaleSettingHandler}.
 */
public final class LocaleSettingMutationFactory {
    /**
     * Constructs a locale-setting mutation factory.
     */
    public LocaleSettingMutationFactory() {

    }

    /**
     * Builds a pending {@code setting_locale} mutation that broadcasts the
     * given locale to every linked device.
     *
     * <p>WA Web does not ship a dedicated {@code getLocaleMutation} helper on
     * {@code WAWebLocaleSettingSync}; outgoing locale changes go through the
     * generic {@code WAWebSyncdActionUtils.buildPendingMutation} pathway used
     * by every other {@code AccountSyncdActionBase} subclass. Cobalt exposes
     * a typed helper here — mirroring {@code WAWebPushNameSync.getPushnameMutation}
     * and {@code WAWebDisableLinkPreviewsSync.getMutation} — so the public
     * {@code WhatsAppClient.editLocale} setter can build a single mutation
     * without hand-rolling the protobuf wrapping.
     *
     * @param timestamp the mutation timestamp
     * @param locale    the new BCP-47 locale tag (e.g. {@code "en_US"})
     * @return a pending mutation carrying the {@code setting_locale} action
     * @throws NullPointerException if {@code timestamp} or {@code locale} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebSyncdActionUtils", exports = "buildPendingMutation", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getLocaleMutation(Instant timestamp, String locale) {
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        Objects.requireNonNull(locale, "locale cannot be null");
        var setting = new LocaleSettingBuilder() // ADAPTED: WAWebSyncdActionUtils.buildPendingMutation value shape: {localeSetting: {locale: s}}
                .locale(locale)
                .build();
        var value = new SyncActionValueBuilder() // ADAPTED: WAWebSyncdActionUtils.buildPendingMutation: encodeProtobuf(SyncActionValueSpec, {...l, timestamp: i})
                .timestamp(timestamp)
                .localeSetting(setting)
                .build();
        var index = JSON.toJSONString(List.of(LocaleSetting.ACTION_NAME)); // ADAPTED: WAWebSyncdActionUtils.buildPendingMutation: index = JSON.stringify([action].concat(indexArgs)) with indexArgs = []
        var pending = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET, // ADAPTED: WAWebSyncdActionUtils.buildPendingMutation: operation: SyncdOperation.SET
                timestamp,
                LocaleSetting.ACTION_VERSION // ADAPTED: WAWebSyncdActionUtils.buildPendingMutation: version: this.getVersion()
        );
        return new SyncPendingMutation(pending, 0);
    }
}
