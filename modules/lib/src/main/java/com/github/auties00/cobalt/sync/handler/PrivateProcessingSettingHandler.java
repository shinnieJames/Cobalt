package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.privacy.PrivateProcessingSettingAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
/**
 * Handles {@link PrivateProcessingSettingAction} sync mutations
 * ({@code "private_processing_setting"}).
 *
 * <p>Each mutation carries a single
 * {@link PrivateProcessingSettingAction.PrivateProcessingStatus} value
 * (one of {@code UNDEFINED}, {@code ENABLED}, {@code DISABLED}) which is
 * persisted on the local {@code WhatsAppStore} via
 * {@code setPrivateProcessingStatus}. Only {@code SET} operations are accepted;
 * any other operation maps to
 * {@link MutationApplicationResult#unsupported()} and a missing or unparseable
 * value maps to {@link MutationApplicationResult#malformed()}.
 *
 * <p><b>NO_WA_BASIS:</b> The {@code SyncActionValue.PrivateProcessingSettingAction}
 * protobuf is defined in {@code WAWebProtobufSyncAction.pb} as field index
 * {@code 74} with a single {@code privateProcessingStatus} enum, but the
 * current WA Web snapshot does <em>not</em> ship a corresponding sync handler
 * module (no {@code WAWebPrivateProcessingSettingSync}). The action is also
 * absent from {@code WAWebCollectionHandlerActions.ActionHandlers}, the
 * registry consumed by {@code WAWebSyncdGetActionHandler.setActionHandlers},
 * so WA Web would never dispatch any incoming mutation with this action.
 * The literal {@code "private_processing_setting"} only appears in the
 * protobuf spec module and nowhere else in the WA Web source.
 *
 * <p>The Cobalt handler is a forward-looking implementation: it follows the
 * Cobalt sync handler conventions used by every other registered handler
 * (singleton, {@code applyMutation} producing a typed
 * {@link MutationApplicationResult}, eager store update on
 * {@code SET}). Every behavioural step here is Cobalt-inferred until WA Web
 * ships the matching {@code WAWebPrivateProcessingSettingSync} module.
 */
public final class PrivateProcessingSettingHandler implements WebAppStateActionHandler {

    /**
     * Private constructor that enforces the singleton pattern.
     */
    public PrivateProcessingSettingHandler() {

    }

    /**
     * {@inheritDoc}
     * @return the canonical {@code "private_processing_setting"} string
     */
    @Override
    public String actionName() {
        return PrivateProcessingSettingAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@link SyncPatchType#REGULAR_HIGH} as an inferred default.
     * @return {@link SyncPatchType#REGULAR_HIGH}
     */
    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR_HIGH; // NO_WA_BASIS: no WA Web sync handler declares a collection for "private_processing_setting"; REGULAR_HIGH matches sibling preference-style handlers
    }

    /**
     * {@inheritDoc}
     * @return the integer version constant declared on the action class
     */
    @Override
    public int version() {
        return PrivateProcessingSettingAction.ACTION_VERSION;
    }

    /**
     * Applies a private processing setting mutation and returns the detailed
     * outcome.
     *
     * <p>The processing pipeline is:
     * <ol>
     *   <li>If the operation is not {@link SyncdOperation#SET}, return
     *       {@link MutationApplicationResult#unsupported()}. Only {@code SET}
     *       mutations are accepted; the action carries a single mandatory
     *       {@code privateProcessingStatus} enum and there is no semantic for
     *       {@code REMOVE}.</li>
     *   <li>Resolve the mutation value to a
     *       {@link PrivateProcessingSettingAction}; if the value is missing
     *       or of the wrong type, or if {@code privateProcessingStatus} is
     *       empty, return {@link MutationApplicationResult#malformed()}.</li>
     *   <li>Persist the resolved
     *       {@link PrivateProcessingSettingAction.PrivateProcessingStatus}
     *       on the store via {@code WhatsAppStore.setPrivateProcessingStatus}
     *       and return {@link MutationApplicationResult#success()}.</li>
     * </ol>
     *
     * <p>The store accessors {@code privateProcessingStatus()} and
     * {@code setPrivateProcessingStatus(...)} already exist on
     * {@code WhatsAppStore} / {@code AbstractWhatsAppStore}; this handler is
     * the sole writer.
     * @param client   the {@link WhatsAppClient} instance linked to the mutation
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        if (!(mutation.value().action().orElse(null) instanceof PrivateProcessingSettingAction action)
                || action.privateProcessingStatus().isEmpty()) { // NO_WA_BASIS: privateProcessingStatus is the only field on the protobuf and is required for any meaningful update
            return MutationApplicationResult.malformed();
        }

        client.store().setPrivateProcessingStatus(action.privateProcessingStatus().get());
        return MutationApplicationResult.success();
    }
}
