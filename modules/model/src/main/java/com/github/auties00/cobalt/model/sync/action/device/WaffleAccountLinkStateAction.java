package com.github.auties00.cobalt.model.sync.action.device;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.SyncActionEmptyArgs;
import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncPatchType;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

/**
 * Represents a sync action that records the link state between the WhatsApp
 * account and a Meta Accounts Center identity (the so called Waffle link).
 *
 * <p>Linking the WhatsApp account to a Meta Accounts Center identity enables
 * cross product features such as unified notifications and shared settings.
 * The current link state is replicated to every companion device through this
 * singleton action so each surface renders a consistent view. The mutation is
 * singleton, so the sync index is composed solely of {@link #ACTION_NAME} with
 * no trailing arguments.
 */
@ProtobufMessage(name = "SyncActionValue.WaffleAccountLinkStateAction")
public final class WaffleAccountLinkStateAction implements SyncAction<SyncActionEmptyArgs> {
    /**
     * Canonical action name used as the sole component of the singleton mutation
     * index for this action type.
     */
    public static final String ACTION_NAME = "waffle_account_link_state";

    /**
     * Schema version advertised by this action, used by sync handlers to gate
     * deserialisation and handling of newer payload shapes.
     */
    public static final int ACTION_VERSION = 1;

    /**
     * Collection this action belongs to, used by the sync protocol to route the
     * mutation into the correct replication stream.
     */
    public static final SyncPatchType COLLECTION_NAME = SyncPatchType.REGULAR_HIGH;

    /**
     * Returns the canonical action name for every {@code WaffleAccountLinkStateAction}.
     *
     * @return the constant {@link #ACTION_NAME}
     */
    @Override
    public String actionName() {
        return ACTION_NAME;
    }

    /**
     * Returns the schema version for every {@code WaffleAccountLinkStateAction}.
     *
     * @return the constant {@link #ACTION_VERSION}
     */
    @Override
    public int actionVersion() {
        return ACTION_VERSION;
    }


    /**
     * Current {@link AccountLinkState} between the WhatsApp account and the
     * Meta Accounts Center identity.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
    AccountLinkState linkState;


    /**
     * Constructs a new {@code WaffleAccountLinkStateAction} from raw protobuf
     * field values.
     *
     * @param linkState the current link state, possibly {@code null}
     */
    WaffleAccountLinkStateAction(AccountLinkState linkState) {
        this.linkState = linkState;
    }

    /**
     * Returns the current link state between the WhatsApp account and the Meta
     * Accounts Center identity, if one was encoded.
     *
     * @return an {@link Optional} containing the {@link AccountLinkState}, or
     *         {@link Optional#empty()} if absent
     */
    public Optional<AccountLinkState> linkState() {
        return Optional.ofNullable(linkState);
    }

    /**
     * Sets the current link state.
     *
     * @param linkState the new {@link AccountLinkState}, or {@code null} to
     *                  clear
     */
    public void setLinkState(AccountLinkState linkState) {
        this.linkState = linkState;
    }

    /**
     * Enumerates the possible states of the link between the WhatsApp account
     * and a Meta Accounts Center identity.
     *
     * @implNote WAWebAccountLinkingConstants.AccountLinkState - the WA Web
     *           constants module defines this as
     *           {@code $InternalEnum.Mirrored(["Active","Paused","Unlinked","Unknown"])}
     *           which yields ordinal indices {@code Active=0, Paused=1,
     *           Unlinked=2, Unknown=3}; the Cobalt enum preserves those exact
     *           indices via {@link ProtobufEnumIndex}.
     */
    @ProtobufEnum(name = "SyncActionValue.WaffleAccountLinkStateAction.AccountLinkState")
    @WhatsAppWebExport(moduleName = "WAWebAccountLinkingConstants", exports = "AccountLinkState", adaptation = WhatsAppAdaptation.DIRECT)
    public static enum AccountLinkState {
        /**
         * The link between the WhatsApp account and the Meta Accounts Center
         * identity is active and functional.
         *
         * @implNote WAWebAccountLinkingConstants.AccountLinkState.Active
         *           (mirrored enum ordinal {@code 0})
         */
        ACTIVE(0), // WAWebAccountLinkingConstants.AccountLinkState.Active
        /**
         * The link exists but has been suspended, typically by the user from
         * the Meta Accounts Center UI.
         *
         * @implNote WAWebAccountLinkingConstants.AccountLinkState.Paused
         *           (mirrored enum ordinal {@code 1})
         */
        PAUSED(1), // WAWebAccountLinkingConstants.AccountLinkState.Paused
        /**
         * The link has been dissolved and the WhatsApp account is no longer
         * associated with a Meta Accounts Center identity.
         *
         * @implNote WAWebAccountLinkingConstants.AccountLinkState.Unlinked
         *           (mirrored enum ordinal {@code 2})
         */
        UNLINKED(2), // WAWebAccountLinkingConstants.AccountLinkState.Unlinked
        /**
         * The link state is unknown. Per WA Web {@code WAWebAccountLinkingConstants.AccountLinkState}
         * (mirrored enum with {@code Active}, {@code Paused}, {@code Unlinked},
         * {@code Unknown}): values outside the known range map to
         * {@code Unknown} via {@code mapToAccountLinkState}.
         *
         * @implNote WAWebAccountLinkingConstants.AccountLinkState.Unknown
         *           (mirrored enum ordinal {@code 3})
         */
        UNKNOWN(3); // WAWebAccountLinkingConstants.AccountLinkState.Unknown

        /**
         * Constructs a new {@code AccountLinkState} with the given protobuf
         * wire index.
         *
         * @param index the protobuf wire index
         */
        AccountLinkState(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        /**
         * Protobuf wire index assigned to this enum constant.
         */
        final int index;

        /**
         * Returns the protobuf wire index for this enum constant.
         *
         * @return the wire index
         */
        public int index() {
            return this.index;
        }
    }
}
