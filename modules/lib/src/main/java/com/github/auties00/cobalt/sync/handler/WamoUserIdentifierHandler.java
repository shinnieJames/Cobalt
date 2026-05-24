package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.device.WamoUserIdentifierAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Persists the server-issued WAMO (WhatsApp Newsletter Subscription) user
 * identifier when the primary device announces it via a
 * {@code generated_wui} mutation.
 *
 * @apiNote
 * Cobalt embedders never invoke this handler directly; the sync dispatcher
 * would route incoming {@code generated_wui} mutations here if the server
 * ever emits one. The identifier is an opaque token (WAMO User
 * Identifier) used to tag the local user inside WA's paid-newsletter
 * subscription pipeline; the handler stores it on
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#setNewsletterSubscriptionUserIdentifier(String)}
 * so subsequent newsletter subscription operations can include it.
 *
 * @implNote
 * This implementation is forward-looking. The
 * {@code SyncActionValue.WamoUserIdentifierAction} protobuf is declared
 * in {@code WAWebProtobufSyncAction.pb} at action index 52, action name
 * {@code "generated_wui"}, collection {@link SyncPatchType#CRITICAL_BLOCK},
 * but the WA Web bundle ships no
 * {@code WAWebWamoUserIdentifierSync} module and the action is absent
 * from {@code WAWebCollectionHandlerActions.ActionHandlers}. The only
 * other reference to {@code "generated_wui"} in WA Web is inside
 * {@code WAWebSyncdAntiTampering} which tracks the action while logging
 * MAC inconsistencies without ever applying it. Cobalt's handler
 * captures the identifier today so it is not lost once a real consumer
 * ships.
 */
public final class WamoUserIdentifierHandler implements WebAppStateActionHandler {

    /**
     * Constructs the handler.
     *
     * @apiNote
     * The handler is stateless; Cobalt's sync registry holds a single
     * instance per client.
     */
    public WamoUserIdentifierHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String actionName() {
        return WamoUserIdentifierAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation returns {@link SyncPatchType#CRITICAL_BLOCK}
     * as declared by WA Web's
     * {@code e===c.WAMO_USER_IDENTIFIER_ACTION?u.CRITICAL_BLOCK} branch
     * in {@code WAWebProtobufSyncAction.pb}'s collection router.
     */
    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.CRITICAL_BLOCK;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int version() {
        return WamoUserIdentifierAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation follows the canonical
     * {@code SET}-only-single-string shape used by sibling
     * identifier-token handlers because WA Web ships no concrete handler
     * to mirror: non-{@code SET} operations are unsupported, a missing
     * or blank {@link WamoUserIdentifierAction#identifier()} is
     * malformed, and the resolved string is written to
     * {@link com.github.auties00.cobalt.store.WhatsAppStore#setNewsletterSubscriptionUserIdentifier(String)}.
     * The blank-string filter is Cobalt-defensive: an empty WAMO
     * identifier carries no meaningful update.
     */
    @Override
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof WamoUserIdentifierAction action)
                || action.identifier().isEmpty()
                || action.identifier().get().isBlank()) {
            return MutationApplicationResult.malformed();
        }

        client.store().setNewsletterSubscriptionUserIdentifier(action.identifier().get());
        return MutationApplicationResult.success();
    }
}
