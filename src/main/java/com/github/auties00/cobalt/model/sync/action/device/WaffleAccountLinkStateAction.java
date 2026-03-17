package com.github.auties00.cobalt.model.sync.action.device;

import com.github.auties00.cobalt.model.sync.SyncActionEmptyArgs;
import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncPatchType;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.WaffleAccountLinkStateAction")
public final class WaffleAccountLinkStateAction implements SyncAction<SyncActionEmptyArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "waffle_account_link_state";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 1;

    /**
     * Canonical WhatsApp Web collection name for this action type.
     */
    public static final SyncPatchType COLLECTION_NAME = SyncPatchType.REGULAR_HIGH;

    /**
     * {@inheritDoc}
     */
    @Override
    public String actionName() {
        return ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int actionVersion() {
        return ACTION_VERSION;
    }


    @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
    AccountLinkState linkState;


    WaffleAccountLinkStateAction(AccountLinkState linkState) {
        this.linkState = linkState;
    }

    public Optional<AccountLinkState> linkState() {
        return Optional.ofNullable(linkState);
    }

    public void setLinkState(AccountLinkState linkState) {
        this.linkState = linkState;
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
