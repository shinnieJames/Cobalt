package com.github.auties00.cobalt.model.setting;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "NotificationSettings")
public final class NotificationSettings {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String messageVibrate;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String messagePopup;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String messageLight;

    @ProtobufProperty(index = 4, type = ProtobufType.BOOL)
    Boolean lowPriorityNotifications;

    @ProtobufProperty(index = 5, type = ProtobufType.BOOL)
    Boolean reactionsMuted;

    @ProtobufProperty(index = 6, type = ProtobufType.STRING)
    String callVibrate;


    NotificationSettings(String messageVibrate, String messagePopup, String messageLight, Boolean lowPriorityNotifications, Boolean reactionsMuted, String callVibrate) {
        this.messageVibrate = messageVibrate;
        this.messagePopup = messagePopup;
        this.messageLight = messageLight;
        this.lowPriorityNotifications = lowPriorityNotifications;
        this.reactionsMuted = reactionsMuted;
        this.callVibrate = callVibrate;
    }

    public Optional<String> messageVibrate() {
        return Optional.ofNullable(messageVibrate);
    }

    public Optional<String> messagePopup() {
        return Optional.ofNullable(messagePopup);
    }

    public Optional<String> messageLight() {
        return Optional.ofNullable(messageLight);
    }

    public boolean lowPriorityNotifications() {
        return lowPriorityNotifications != null && lowPriorityNotifications;
    }

    public boolean reactionsMuted() {
        return reactionsMuted != null && reactionsMuted;
    }

    public Optional<String> callVibrate() {
        return Optional.ofNullable(callVibrate);
    }

    public void setMessageVibrate(String messageVibrate) {
        this.messageVibrate = messageVibrate;
    }

    public void setMessagePopup(String messagePopup) {
        this.messagePopup = messagePopup;
    }

    public void setMessageLight(String messageLight) {
        this.messageLight = messageLight;
    }

    public void setLowPriorityNotifications(Boolean lowPriorityNotifications) {
        this.lowPriorityNotifications = lowPriorityNotifications;
    }

    public void setReactionsMuted(Boolean reactionsMuted) {
        this.reactionsMuted = reactionsMuted;
    }

    public void setCallVibrate(String callVibrate) {
        this.callVibrate = callVibrate;
    }
}
