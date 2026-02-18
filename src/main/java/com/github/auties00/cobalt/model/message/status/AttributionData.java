package com.github.auties00.cobalt.model.message.status;

public sealed interface AttributionData permits StatusAttribution.StatusReshare, StatusAttribution.ExternalShare, StatusAttribution.Music, StatusAttribution.GroupStatus, StatusAttribution.RLAttribution, StatusAttribution.AiCreatedAttribution {
}
