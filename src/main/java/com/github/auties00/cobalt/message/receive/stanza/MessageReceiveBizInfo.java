package com.github.auties00.cobalt.message.receive.stanza;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Parsed business information from the incoming message stanza.
 *
 * <p>Combines data from the {@code <biz>} child node, the
 * {@code verified_name} attribute/child, and the {@code verified_level}
 * attribute.  This metadata identifies verified business accounts and
 * carries privacy mode information for business-hosted messaging.
 *
 * @implNote WAWebHandleMsgParser function v(): parses verified_name,
 * verified_level, biz node (actual_actors, host_storage, privacy_mode_ts,
 * native_flow_name, campaign_id, button/list/hsm envelope flags).
 */
public final class MessageReceiveBizInfo {
    private final byte[] verifiedNameCert;
    private final int verifiedNameSerial;
    private final String verifiedLevel;
    private final String nativeFlowName;
    private final String campaignId;
    private final Integer actualActors;
    private final Integer hostStorage;
    private final Integer privacyModeTs;
    private final boolean verifiedButtonsEnvelope;
    private final boolean verifiedListEnvelope;
    private final boolean verifiedHsmEnvelope;

    public MessageReceiveBizInfo(
            byte[] verifiedNameCert,
            int verifiedNameSerial,
            String verifiedLevel,
            String nativeFlowName,
            String campaignId,
            Integer actualActors,
            Integer hostStorage,
            Integer privacyModeTs,
            boolean verifiedButtonsEnvelope,
            boolean verifiedListEnvelope,
            boolean verifiedHsmEnvelope
    ) {
        this.verifiedNameCert = verifiedNameCert;
        this.verifiedNameSerial = verifiedNameSerial;
        this.verifiedLevel = verifiedLevel;
        this.nativeFlowName = nativeFlowName;
        this.campaignId = campaignId;
        this.actualActors = actualActors;
        this.hostStorage = hostStorage;
        this.privacyModeTs = privacyModeTs;
        this.verifiedButtonsEnvelope = verifiedButtonsEnvelope;
        this.verifiedListEnvelope = verifiedListEnvelope;
        this.verifiedHsmEnvelope = verifiedHsmEnvelope;
    }

    public Optional<byte[]> verifiedNameCert() {
        return Optional.ofNullable(verifiedNameCert);
    }

    public int verifiedNameSerial() {
        return verifiedNameSerial;
    }

    public Optional<String> verifiedLevel() {
        return Optional.ofNullable(verifiedLevel);
    }

    public Optional<String> nativeFlowName() {
        return Optional.ofNullable(nativeFlowName);
    }

    public Optional<String> campaignId() {
        return Optional.ofNullable(campaignId);
    }

    public Optional<Integer> actualActors() {
        return Optional.ofNullable(actualActors);
    }

    public Optional<Integer> hostStorage() {
        return Optional.ofNullable(hostStorage);
    }

    public Optional<Integer> privacyModeTs() {
        return Optional.ofNullable(privacyModeTs);
    }

    public boolean verifiedButtonsEnvelope() {
        return verifiedButtonsEnvelope;
    }

    public boolean verifiedListEnvelope() {
        return verifiedListEnvelope;
    }

    public boolean verifiedHsmEnvelope() {
        return verifiedHsmEnvelope;
    }

    /**
     * Returns whether this message has a privacy mode section (all three
     * fields present), indicating a business-hosted messaging context.
     */
    public boolean hasPrivacyMode() {
        return actualActors != null && hostStorage != null && privacyModeTs != null;
    }
}
