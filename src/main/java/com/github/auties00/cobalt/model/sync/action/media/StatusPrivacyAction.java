package com.github.auties00.cobalt.model.sync.action.media;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncAction;

import java.util.Collections;
import java.util.List;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.StatusPrivacyAction")
public final class StatusPrivacyAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    StatusDistributionMode mode;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    List<Jid> userJid;


    StatusPrivacyAction(StatusDistributionMode mode, List<Jid> userJid) {
        this.mode = mode;
        this.userJid = userJid;
    }

    public Optional<StatusDistributionMode> mode() {
        return Optional.ofNullable(mode);
    }

    public List<Jid> userJid() {
        return userJid == null ? List.of() : Collections.unmodifiableList(userJid);
    }

    public StatusPrivacyAction setMode(StatusDistributionMode mode) {
        this.mode = mode;
        return this;
    }

    public StatusPrivacyAction setUserJid(List<Jid> userJid) {
        this.userJid = userJid;
        return this;
    }

    @ProtobufEnum(name = "SyncActionValue.StatusPrivacyAction.StatusDistributionMode")
    public static enum StatusDistributionMode {
        ALLOW_LIST(0),
        DENY_LIST(1),
        CONTACTS(2),
        CLOSE_FRIENDS(3);

        StatusDistributionMode(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
