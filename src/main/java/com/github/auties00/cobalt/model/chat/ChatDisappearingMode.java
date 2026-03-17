package com.github.auties00.cobalt.model.chat;

import com.github.auties00.cobalt.model.jid.Jid;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "DisappearingMode")
public final class ChatDisappearingMode {
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    Initiator initiator;

    @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
    Trigger trigger;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    Jid initiatorDeviceJid;

    @ProtobufProperty(index = 4, type = ProtobufType.BOOL)
    Boolean initiatedByMe;


    ChatDisappearingMode(Initiator initiator, Trigger trigger, Jid initiatorDeviceJid, Boolean initiatedByMe) {
        this.initiator = initiator;
        this.trigger = trigger;
        this.initiatorDeviceJid = initiatorDeviceJid;
        this.initiatedByMe = initiatedByMe;
    }

    public Optional<Initiator> initiator() {
        return Optional.ofNullable(initiator);
    }

    public Optional<Trigger> trigger() {
        return Optional.ofNullable(trigger);
    }

    public Optional<Jid> initiatorDeviceJid() {
        return Optional.ofNullable(initiatorDeviceJid);
    }

    public boolean initiatedByMe() {
        return initiatedByMe != null && initiatedByMe;
    }

    public void setInitiator(Initiator initiator) {
        this.initiator = initiator;
    }

    public void setTrigger(Trigger trigger) {
        this.trigger = trigger;
    }

    public void setInitiatorDeviceJid(Jid initiatorDeviceJid) {
        this.initiatorDeviceJid = initiatorDeviceJid;
    }

    public void setInitiatedByMe(Boolean initiatedByMe) {
        this.initiatedByMe = initiatedByMe;
    }

    @ProtobufEnum(name = "DisappearingMode.Initiator")
    public static enum Initiator {
        CHANGED_IN_CHAT(0),
        INITIATED_BY_ME(1),
        INITIATED_BY_OTHER(2),
        BIZ_UPGRADE_FB_HOSTING(3);

        Initiator(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufEnum(name = "DisappearingMode.Trigger")
    public static enum Trigger {
        UNKNOWN(0),
        CHAT_SETTING(1),
        ACCOUNT_SETTING(2),
        BULK_CHANGE(3),
        BIZ_SUPPORTS_FB_HOSTING(4),
        UNKNOWN_GROUPS(5);

        Trigger(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
