package com.github.auties00.cobalt.model.message.status;

import java.time.Instant;
import java.util.Objects;

import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "StatusPSA")
public final class StatusPSA {
    @ProtobufProperty(index = 44, type = ProtobufType.UINT64)
    Long campaignId;

    @ProtobufProperty(index = 45, type = ProtobufType.UINT64, mixins = InstantSecondsMixin.class)
    Instant campaignExpirationTimestamp;


    StatusPSA(Long campaignId, Instant campaignExpirationTimestamp) {
        this.campaignId = Objects.requireNonNull(campaignId);
        this.campaignExpirationTimestamp = campaignExpirationTimestamp;
    }

    public Long campaignId() {
        return campaignId;
    }

    public Optional<Instant> campaignExpirationTimestamp() {
        return Optional.ofNullable(campaignExpirationTimestamp);
    }

    public void setCampaignId(Long campaignId) {
        this.campaignId = campaignId;
    }

    public void setCampaignExpirationTimestamp(Instant campaignExpirationTimestamp) {
        this.campaignExpirationTimestamp = campaignExpirationTimestamp;
    }
}
