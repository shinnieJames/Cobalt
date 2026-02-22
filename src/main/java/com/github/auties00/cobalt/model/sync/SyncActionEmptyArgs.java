package com.github.auties00.cobalt.model.sync;

/**
 * An arguments variant carrying no additional index parameters.
 *
 * <p>Used by actions whose index is simply {@code ["actionName"]}.
 */
public final class SyncActionEmptyArgs implements SyncActionArgs {
    /**
     * A shared singleton instance, since no state is carried.
     */
    static final SyncActionEmptyArgs INSTANCE = new SyncActionEmptyArgs();

    /**
     * Constructs a new {@code EmptyArgs} instance.
     */
    private SyncActionEmptyArgs() {

    }

    /**
     * {@inheritDoc}
     *
     * @return an empty array
     */
    @Override
    public String[] toIndexArgs() {
        return new String[0];
    }
}
