package com.github.auties00.cobalt.message.send.icdc;

import com.github.auties00.cobalt.device.icdc.IcdcResult;
import com.github.auties00.cobalt.model.chat.ChatMessageContextInfoBuilder;
import com.github.auties00.cobalt.model.device.DeviceListMetadataBuilder;
import com.github.auties00.cobalt.model.message.MessageContainer;

/**
 * Populates ICDC (Identity Change Detection Consistency) metadata on
 * outgoing message containers.
 *
 * <p>ICDC metadata is written into the container's
 * {@code messageContextInfo.deviceListMetadata} so that recipients can
 * detect changes in the sender's or recipient's device list since the
 * last key exchange.
 *
 * @implNote WAWebE2EProtoGenerator.populateMessageContextInfo: merges
 * ICDC metadata into the existing messageContextInfo via spread,
 * preserving all existing fields while adding/replacing
 * {@code deviceListMetadata} and {@code deviceListMetadataVersion}.
 * Called by WAWebICDCMetaApi.populateICDCMeta after computing sender
 * and recipient ICDC results.
 */
public final class IcdcEnricher {
    /**
     * Prevents instantiation of this utility class.
     *
     * @throws UnsupportedOperationException always
     * @implNote NO_WA_BASIS: Java utility class pattern.
     */
    private IcdcEnricher() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Returns a copy of the container with ICDC metadata populated in its
     * {@code messageContextInfo}.
     *
     * <p>Preserves all existing fields on the container's
     * {@code ChatMessageContextInfo} via spread-equivalent merge, while
     * adding or replacing the {@code deviceListMetadata} and setting
     * {@code deviceListMetadataVersion} to 2.
     *
     * @param container     the original message container
     * @param senderIcdc    the sender's ICDC result, or {@code null}
     * @param recipientIcdc the recipient's ICDC result, or {@code null}
     * @return the enriched container (unchanged if both args are {@code null})
     *
     * @implNote WAWebE2EProtoGenerator.populateMessageContextInfo: uses
     * {@code babelHelpers.extends({{}}, e.messageContextInfo, {{...}})} to
     * shallow-merge the existing messageContextInfo with the new ICDC
     * fields, preserving all pre-existing properties.
     */
    public static MessageContainer enrich(
            MessageContainer container,
            IcdcResult senderIcdc,
            IcdcResult recipientIcdc
    ) {
        // WAWebE2EProtoGenerator.populateMessageContextInfo: !t && !n || (...)
        if (senderIcdc == null && recipientIcdc == null) {
            return container;
        }

        // WAWebE2EProtoGenerator.populateMessageContextInfo: deviceListMetadata object
        var metadataBuilder = new DeviceListMetadataBuilder();
        if (senderIcdc != null) {
            // WAWebE2EProtoGenerator.populateMessageContextInfo: senderKeyHash: t?.keyHash
            senderIcdc.keyHash().ifPresent(metadataBuilder::senderKeyHash);
            // WAWebE2EProtoGenerator.populateMessageContextInfo: senderTimestamp: t?.timestamp
            senderIcdc.timestamp().ifPresent(metadataBuilder::senderTimestamp);
            // WAWebE2EProtoGenerator.populateMessageContextInfo: senderKeyIndexes: t?.keyIndexes
            metadataBuilder.senderKeyIndexes(senderIcdc.keyIndexes());
            // WAWebE2EProtoGenerator.populateMessageContextInfo: senderAccountType: bizHostedDevicesEnabled() ? t?.senderAccountType : undefined
            // ADAPTED: bizHostedDevicesEnabled gating is done in IcdcComputer.computeFromDeviceList
            senderIcdc.accountType().ifPresent(metadataBuilder::senderAccountType);
        }
        if (recipientIcdc != null) {
            // WAWebE2EProtoGenerator.populateMessageContextInfo: recipientKeyHash: n?.keyHash
            recipientIcdc.keyHash().ifPresent(metadataBuilder::recipientKeyHash);
            // WAWebE2EProtoGenerator.populateMessageContextInfo: recipientTimestamp: n?.timestamp
            recipientIcdc.timestamp().ifPresent(metadataBuilder::recipientTimestamp);
            // WAWebE2EProtoGenerator.populateMessageContextInfo: recipientKeyIndexes: n?.keyIndexes
            metadataBuilder.recipientKeyIndexes(recipientIcdc.keyIndexes());
            // WAWebE2EProtoGenerator.populateMessageContextInfo: receiverAccountType: bizHostedDevicesEnabled() ? n?.receiverAccountType : undefined
            // ADAPTED: bizHostedDevicesEnabled gating is done in IcdcComputer.computeFromDeviceList
            recipientIcdc.accountType().ifPresent(metadataBuilder::receiverAccountType);
        }

        // WAWebE2EProtoGenerator.populateMessageContextInfo:
        // babelHelpers.extends({}, e.messageContextInfo, { deviceListMetadata: {...}, deviceListMetadataVersion: 2 })
        // Spread-equivalent merge: preserves ALL existing fields, overrides deviceListMetadata and version
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
            // WAWebE2EProtoGenerator.populateMessageContextInfo: spread preserves all fields
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
