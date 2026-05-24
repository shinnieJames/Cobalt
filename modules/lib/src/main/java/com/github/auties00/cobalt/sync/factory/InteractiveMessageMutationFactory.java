package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.chat.InteractiveMessageAction;
import com.github.auties00.cobalt.model.sync.action.chat.InteractiveMessageActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Builds outgoing app-state mutations that disable a tappable button
 * on an interactive message.
 *
 * @apiNote
 * Drives the post-tap state of buttons (call-to-action, flow trigger,
 * list entry) on interactive and template messages: tapping a button
 * on one device should disable it on every linked device so a user
 * cannot retrigger the same flow twice. Mutations produced here are
 * consumed on receiving devices by the interactive-message sync
 * handler which marks the targeted button as disabled in the local
 * message row.
 *
 * @implNote
 * This implementation mirrors
 * {@code WAWebInteractiveMessageSync.sendDisableCTAMutation}, which
 * WA Web invokes from {@code WAWebGalaxyFlowsCompleteAction} and
 * {@code WAWebFlowsInvokeHandler} when a button-driven flow
 * completes. The mutation is keyed on the button identifier so each
 * tappable element gets its own row.
 */
public final class InteractiveMessageMutationFactory {
    /**
     * Constructs an interactive-message mutation factory.
     *
     * @apiNote
     * The factory is stateless; a single instance may be shared across
     * the lifetime of the client.
     */
    public InteractiveMessageMutationFactory() {

    }

    /**
     * Builds a pending SET mutation that disables the given button.
     *
     * @apiNote
     * Invoked from the public interactive-message-button-disable entry
     * point on {@link com.github.auties00.cobalt.client.WhatsAppClient};
     * the index carries the button identifier so each disabled button
     * gets its own row.
     *
     * @implNote
     * This implementation captures the timestamp via
     * {@link Instant#now()}; WA Web's
     * {@code WAWebInteractiveMessageSync.sendDisableCTAMutation} uses
     * {@code WATimeUtils.unixTimeMs()} for the same purpose. The action
     * mode is always
     * {@link InteractiveMessageAction.InteractiveMessageActionMode#DISABLE_CTA}
     * since that is the only mode the WA Web schema supports today.
     *
     * @param buttonId the identifier of the interactive button being
     *                 disabled, used as the second segment of the
     *                 mutation index
     * @return the pending mutation ready to be queued for outbound
     *         app-state sync
     * @throws NullPointerException if {@code buttonId} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WAWebInteractiveMessageSync", exports = "sendDisableCTAMutation", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getDisableButtonMutation(String buttonId) {
        Objects.requireNonNull(buttonId, "buttonId cannot be null");
        var timestamp = Instant.now();
        var action = new InteractiveMessageActionBuilder()
                .type(InteractiveMessageAction.InteractiveMessageActionMode.DISABLE_CTA)
                .agmId(buttonId)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .interactiveMessageAction(action)
                .build();
        var index = JSON.toJSONString(List.of(InteractiveMessageAction.ACTION_NAME, buttonId));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                InteractiveMessageAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0);
    }
}
