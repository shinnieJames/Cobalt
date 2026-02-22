package com.github.auties00.cobalt.model.sync;

/**
 * An index-argument type for WhatsApp Web sync actions.
 *
 * <p>Each implementation encodes a specific combination of parameters that,
 * together with the action name, form the full index array
 * ({@code ["actionName", ...args]}) used by the WhatsApp Web app-state sync
 * protocol. The {@link #toIndexArgs()} method returns only the trailing
 * argument portion; the action name is prepended by
 * {@link SyncAction#toIndex(SyncActionArgs)}.
 *
 * <p>Actions that require no trailing index arguments use
 * {@link #empty()}. Actions that require trailing arguments define
 * their own nested class implementing this interface.
 *
 * <p>Callers should construct the appropriate implementation before invoking
 * {@code toIndex}. JID resolution (e.g. LID migration) must be performed at
 * the call site before passing the resolved JID into the constructor.
 */
public interface SyncActionArgs {

    /**
     * Returns the trailing index arguments for this action invocation.
     *
     * <p>The returned array is appended after the action name when building
     * the full JSON index via {@link SyncAction#toIndex(SyncActionArgs)}.
     *
     * @return a non-{@code null} array of string arguments
     */
    String[] toIndexArgs();

    /**
     * Returns a shared instance carrying no additional index parameters.
     *
     * <p>Used by actions whose index is simply {@code ["actionName"]}.
     *
     * @return a singleton empty-arguments instance
     */
    static SyncActionArgs empty() {
        return SyncActionEmptyArgs.INSTANCE;
    }
}
