package com.github.auties00.cobalt.model.sync.action.misc;

import com.github.auties00.cobalt.model.sync.SyncAction;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.WaffleAccountLinkStateAction")
public final class WaffleAccountLinkStateAction implements SyncAction {
    @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
    AccountLinkState linkState;


    WaffleAccountLinkStateAction(AccountLinkState linkState) {
        this.linkState = linkState;
    }

    public Optional<AccountLinkState> linkState() {
        return Optional.ofNullable(linkState);
    }

    public WaffleAccountLinkStateAction setLinkState(AccountLinkState linkState) {
        this.linkState = linkState;
        return this;
    }

    @ProtobufEnum(name = "SyncActionValue.WaffleAccountLinkStateAction.AccountLinkState")
    public static enum AccountLinkState {
        ACTIVE(0),
        PAUSED(1),
        UNLINKED(2);

        AccountLinkState(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
