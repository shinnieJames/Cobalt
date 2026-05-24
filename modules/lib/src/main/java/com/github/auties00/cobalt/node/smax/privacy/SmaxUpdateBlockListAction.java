package com.github.auties00.cobalt.node.smax.privacy;

/**
 * The action selector for a {@link SmaxUpdateBlockListRequest}.
 *
 * @apiNote
 * Selects between adding the target JID to the blocklist or removing it, driving the block-or-unblock action
 * surfaced by chat-info and contact-info screens; the WA Web caller is {@code WAWebBlockUserJob.blockUnblockUser},
 * which derives the wire string from the same enum-of-two dispatch.
 */
public enum SmaxUpdateBlockListAction {
    /**
     * Adds the target JID to the blocklist.
     *
     * @apiNote
     * Selected when the user taps Block on the chat or contact-info screen; the relay rejects further sends to
     * the blocked target and stops delivering messages from them once it has applied the action.
     */
    BLOCK("block"),

    /**
     * Removes the target JID from the blocklist.
     *
     * @apiNote
     * Selected when the user taps Unblock; the relay re-enables bidirectional message delivery on success.
     */
    UNBLOCK("unblock");

    /**
     * The wire string serialised into the {@code action} attribute of the {@code <item>} child.
     */
    private final String wire;

    /**
     * Constructs an action constant.
     *
     * @apiNote
     * Invoked once per constant during class initialisation; not exposed to callers.
     *
     * @param wire the wire string for this action
     */
    SmaxUpdateBlockListAction(String wire) {
        this.wire = wire;
    }

    /**
     * Returns the wire string for this action.
     *
     * @apiNote
     * Used by {@link SmaxUpdateBlockListRequest#toNode()} to serialise the action onto the outbound stanza;
     * callers outside the stanza builder should prefer the enum value over the raw wire string.
     *
     * @return the wire string; never {@code null}
     */
    public String wire() {
        return wire;
    }
}
