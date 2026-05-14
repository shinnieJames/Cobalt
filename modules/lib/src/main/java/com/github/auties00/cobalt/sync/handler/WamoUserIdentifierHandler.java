package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.device.WamoUserIdentifierAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
/**
 * Handles {@link WamoUserIdentifierAction} sync mutations
 * ({@code "generated_wui"}).
 *
 * <p>Each mutation carries a single {@code identifier} string (an opaque,
 * server-generated WAMO user identifier token, where {@code wui} stands for
 * "WAMO user identifier") which is persisted on the local
 * {@code WhatsAppStore} via {@code setNewsletterSubscriptionUserIdentifier}. Only {@code SET}
 * operations are accepted; any other operation maps to
 * {@link MutationApplicationResult#unsupported()} and a missing, wrong-typed,
 * empty or blank value maps to
 * {@link MutationApplicationResult#malformed()}.
 *
 * <p><b>NO_WA_BASIS:</b> The {@code SyncActionValue.WamoUserIdentifierAction}
 * protobuf is defined in {@code WAWebProtobufSyncAction.pb} (exported as
 * {@code SyncActionValue$WamoUserIdentifierActionSpec}) with one optional
 * field ({@code identifier: string} at index {@code 1}), and the
 * {@code WAWebProtobufSyncAction.pb} collection-name resolver explicitly maps
 * {@code WAMO_USER_IDENTIFIER_ACTION} (numeric id {@code 52}) to the
 * {@code CRITICAL_BLOCK} collection
 * ({@code e===c.WAMO_USER_IDENTIFIER_ACTION?u.CRITICAL_BLOCK}). However, the
 * current WA Web snapshot does <em>not</em> ship a corresponding sync handler
 * module (no {@code WAWebWamoUserIdentifierSync}). The action is also absent
 * from {@code WASyncdConst.Actions}, the action-name enum consumed by
 * {@code WAWebSyncdGetActionHandler}, and from
 * {@code WAWebCollectionHandlerActions.ActionHandlers}, the registry consumed
 * by {@code WAWebSyncdGetActionHandler.setActionHandlers}. Consequently WA Web
 * would never dispatch any incoming mutation with this action via
 * {@code WAWebSyncdGetActionHandler.getActionHandler("generated_wui")} (the
 * lookup would return {@code undefined} and the mutation would be skipped).
 * The {@code "generated_wui"} string literal does appear once outside the
 * protobuf module: {@code WAWebSyncdAntiTampering} hardcodes it inside an
 * inclusion list {@code A} consumed by the {@code te(collectionName, action)}
 * helper, which only causes the action to be tracked while logging
 * snapshot/patch MAC inconsistencies — it does <em>not</em> apply the action
 * to any local state.
 *
 * <p>The other "WAMO" surface in WA Web (modules such as
 * {@code WAWebWamoNewsletterGatingUtils}, {@code WAWebWamoPDFNGatingUtils},
 * {@code WAWebNewsletterWamoSubMessageType},
 * {@code WAWebNewsletterWamoSubUtils}, {@code WASmaxInNewslettersWAMOSubMixin})
 * deals exclusively with paid newsletter subscription gating, terms of
 * service, and message parsing — none of those modules consume
 * {@code SyncActionValue.WamoUserIdentifierAction}.
 *
 * <p>The Cobalt handler is a forward-looking implementation: it follows the
 * Cobalt sync handler conventions used by every other registered handler
 * (singleton, {@code applyMutation} producing a typed
 * {@link MutationApplicationResult}, eager store update on {@code SET}). The
 * shape of the handler — only-{@code SET}, single non-blank string payload,
 * single store setter — is inferred directly from the protobuf shape (one
 * optional {@code identifier: string} field) and from sibling
 * identifier-style handlers (e.g. {@code MusicUserIdHandler},
 * {@code NewsletterSavedInterestsHandler}) which follow the same
 * {@code single-string -> single store setter} pattern. Every behavioural step
 * here is Cobalt-inferred until WA Web ships the matching
 * {@code WAWebWamoUserIdentifierSync} module.
 */
public final class WamoUserIdentifierHandler implements WebAppStateActionHandler {

    /**
     * Private constructor that enforces the singleton pattern.
     */
    public WamoUserIdentifierHandler() {

    }

    /**
     * {@inheritDoc}
     * @return the canonical {@code "generated_wui"} string
     */
    @Override
    public String actionName() {
        return WamoUserIdentifierAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@link SyncPatchType#CRITICAL_BLOCK} as inferred from the WA
     * Web protobuf-side collection router.
     * @return {@link SyncPatchType#CRITICAL_BLOCK}
     */
    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.CRITICAL_BLOCK; // NO_WA_BASIS: matches the WAWebProtobufSyncAction.pb inline collection mapping WAMO_USER_IDENTIFIER_ACTION -> CRITICAL_BLOCK
    }

    /**
     * {@inheritDoc}
     * @return the integer version constant declared on the action class
     */
    @Override
    public int version() {
        return WamoUserIdentifierAction.ACTION_VERSION;
    }

    /**
     * Applies a wamo user identifier mutation and returns the detailed
     * outcome.
     *
     * <p>The processing pipeline is:
     * <ol>
     *   <li>If the operation is not {@link SyncdOperation#SET}, return
     *       {@link MutationApplicationResult#unsupported()}. Only {@code SET}
     *       mutations are accepted; the action carries a single string token
     *       and there is no semantic for {@code REMOVE}.</li>
     *   <li>Resolve the mutation value to a {@link WamoUserIdentifierAction};
     *       if the value is missing or of the wrong type, return
     *       {@link MutationApplicationResult#malformed()}.</li>
     *   <li>Reject mutations whose {@code identifier} is empty or blank by
     *       returning {@link MutationApplicationResult#malformed()}: an empty
     *       or whitespace-only WAMO identifier carries no meaningful update.</li>
     *   <li>Persist the resolved identifier on the store via
     *       {@code WhatsAppStore.setNewsletterSubscriptionUserIdentifier} and return
     *       {@link MutationApplicationResult#success()}.</li>
     * </ol>
     *
     * <p>The store accessors {@code newsletterSubscriptionUserIdentifier()} and
     * {@code setNewsletterSubscriptionUserIdentifier(...)} already exist on
     * {@code WhatsAppStore} / {@code AbstractWhatsAppStore}; this handler is
     * the sole writer.
     * @param client   the {@link WhatsAppClient} instance linked to the
     *                 mutation
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof WamoUserIdentifierAction action)
                || action.identifier().isEmpty() // NO_WA_BASIS: identifier is the only field on the protobuf and is required for any meaningful update
                || action.identifier().get().isBlank()) {
            return MutationApplicationResult.malformed();
        }

        client.store().setNewsletterSubscriptionUserIdentifier(action.identifier().get());
        return MutationApplicationResult.success();
    }
}
