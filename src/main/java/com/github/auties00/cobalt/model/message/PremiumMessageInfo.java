package com.github.auties00.cobalt.model.message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "PremiumMessageInfo")
public final class PremiumMessageInfo {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String serverCampaignId;


    PremiumMessageInfo(String serverCampaignId) {
        this.serverCampaignId = serverCampaignId;
    }

    public Optional<String> serverCampaignId() {
        return Optional.ofNullable(serverCampaignId);
    }

    public void setServerCampaignId(String serverCampaignId) {
        this.serverCampaignId = serverCampaignId;
    }
}
