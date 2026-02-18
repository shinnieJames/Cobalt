package com.github.auties00.cobalt.model.sync.setting.privacy;

import com.github.auties00.cobalt.model.sync.SyncAction;

@ProtobufMessage(name = "SyncActionValue.PrivacySettingChannelsPersonalisedRecommendationAction")
public final class PrivacySettingChannelsPersonalisedRecommendationAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean isUserOptedOut;


    PrivacySettingChannelsPersonalisedRecommendationAction(Boolean isUserOptedOut) {
        this.isUserOptedOut = isUserOptedOut;
    }

    public boolean isUserOptedOut() {
        return isUserOptedOut != null && isUserOptedOut;
    }

    public PrivacySettingChannelsPersonalisedRecommendationAction setUserOptedOut(Boolean isUserOptedOut) {
        this.isUserOptedOut = isUserOptedOut;
        return this;
    }
}
