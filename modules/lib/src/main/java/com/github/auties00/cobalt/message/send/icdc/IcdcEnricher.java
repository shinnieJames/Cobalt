package com.github.auties00.cobalt.message.send.icdc;

import com.github.auties00.cobalt.device.icdc.IcdcResult;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.ChatMessageContextInfoBuilder;
import com.github.auties00.cobalt.model.device.DeviceListMetadataBuilder;
import com.github.auties00.cobalt.model.message.MessageContainer;

/**
 * Stamps ICDC (identity-change detection consistency) device-list metadata on
 * outgoing message containers.
 *
 * @apiNote
 * Invoked by the per-recipient fanout writer (mirroring WA Web's
 * {@code WAWebICDCMetaApi.populateICDCMeta} and
 * {@code WAWebE2EProtoGenerator.populateMessageContextInfo}) so the recipient
 * can detect any change to the sender's or recipient's device list since the
 * last key exchange. The output is consumed by the receiver-side device-sync
 * pipeline; messages that omit ICDC silently lose the integrity check, they
 * are not rejected. Used internally by
 * {@link com.github.auties00.cobalt.message.send.senderkey.SenderKeyDistribution}
 * and the device-stanza writer; embedders do not call it.
 */
@WhatsAppWebModule(moduleName = "WAWebE2EProtoGenerator")
@WhatsAppWebModule(moduleName = "WAWebICDCMetaApi")
public final class IcdcEnricher {
    /**
     * Prevents instantiation of this utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private IcdcEnricher() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Returns a copy of {@code container} with ICDC device-list metadata
     * merged into its {@code messageContextInfo}.
     *
     * @apiNote
     * Mirrors WA Web's {@code populateMessageContextInfo}: the supplied
     * sender/recipient {@link IcdcResult} pair populates a
     * {@link com.github.auties00.cobalt.model.device.DeviceListMetadata} on
     * the container's {@link com.github.auties00.cobalt.model.chat.ChatMessageContextInfo},
     * leaves every other context-info field intact, and pins
     * {@code deviceListMetadataVersion = 2}. When both {@link IcdcResult}
     * inputs are {@code null} this is a no-op and the original container is
     * returned by reference; that short-circuit matches the
     * {@code populateICDCMeta} caller branches that pass {@code (icdcMeta, null)}
     * or {@code (null, null)}.
     * @implNote
     * This implementation performs the equivalent of WA Web's JS spread merge
     * by copying every present field of an existing
     * {@link com.github.auties00.cobalt.model.chat.ChatMessageContextInfo} into
     * a fresh builder before overwriting {@code deviceListMetadata} and
     * {@code deviceListMetadataVersion}; the {@link MessageContainer} itself
     * is replaced via {@link MessageContainer#withMessageContextInfo} so the
     * caller can keep the original instance.
     *
     * @param container     the original {@link MessageContainer}
     * @param senderIcdc    the sender's {@link IcdcResult}, or {@code null}
     * @param recipientIcdc the recipient's {@link IcdcResult}, or {@code null}
     * @return the enriched container; the original instance when both ICDC
     *         inputs are {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebE2EProtoGenerator", exports = "populateMessageContextInfo",
            adaptation = WhatsAppAdaptation.DIRECT)
    @WhatsAppWebExport(moduleName = "WAWebICDCMetaApi", exports = "populateICDCMeta",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static MessageContainer enrich(
            MessageContainer container,
            IcdcResult senderIcdc,
            IcdcResult recipientIcdc
    ) {
        if (senderIcdc == null && recipientIcdc == null) {
            return container;
        }

        var metadataBuilder = new DeviceListMetadataBuilder();
        if (senderIcdc != null) {
            senderIcdc.keyHash().ifPresent(metadataBuilder::senderKeyHash);
            senderIcdc.timestamp().ifPresent(metadataBuilder::senderTimestamp);
            metadataBuilder.senderKeyIndexes(senderIcdc.keyIndexes());
            senderIcdc.accountType().ifPresent(metadataBuilder::senderAccountType);
        }
        if (recipientIcdc != null) {
            recipientIcdc.keyHash().ifPresent(metadataBuilder::recipientKeyHash);
            recipientIcdc.timestamp().ifPresent(metadataBuilder::recipientTimestamp);
            metadataBuilder.recipientKeyIndexes(recipientIcdc.keyIndexes());
            recipientIcdc.accountType().ifPresent(metadataBuilder::receiverAccountType);
        }

        var existing = container.messageContextInfo().orElse(null);
        var infoBuilder = new ChatMessageContextInfoBuilder()
                .deviceListMetadata(metadataBuilder.build())
                .deviceListMetadataVersion(2);
        if (existing != null) {
            existing.messageSecret().ifPresent(infoBuilder::messageSecret);
            existing.paddingBytes().ifPresent(infoBuilder::paddingBytes);
            existing.botMessageSecret().ifPresent(infoBuilder::botMessageSecret);
            existing.botMetadata().ifPresent(infoBuilder::botMetadata);
            infoBuilder.capiCreatedGroup(existing.capiCreatedGroup());
            existing.supportPayload().ifPresent(infoBuilder::supportPayload);
            infoBuilder.threadId(existing.threadId());
            existing.messageAddOnExpiryType().ifPresent(infoBuilder::messageAddOnExpiryType);
            existing.messageAssociation().ifPresent(infoBuilder::messageAssociation);
            existing.limitSharing().ifPresent(infoBuilder::limitSharing);
            existing.limitSharingV2().ifPresent(infoBuilder::limitSharingV2);
            existing.weblinkRenderConfig().ifPresent(infoBuilder::weblinkRenderConfig);
            existing.reportingTokenVersion().ifPresent(version -> infoBuilder.reportingTokenVersion(version));
            existing.messageAddOnDurationInSecs().ifPresent(secs -> infoBuilder.messageAddOnDurationInSecs(secs));
        }

        return container.withMessageContextInfo(infoBuilder.build());
    }
}
