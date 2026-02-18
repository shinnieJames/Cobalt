package com.github.auties00.cobalt.model.chat;

import it.auties.protobuf.annotation.ProtobufEnum;

/**
 * A policy that determines which members are permitted to perform an action
 * in a group or community.
 *
 * <p>In the WhatsApp Web protocol, group and community permissions are
 * transmitted as boolean toggle values. A value of {@code true} restricts the
 * action to administrators only, corresponding to {@link #ADMINS}. A value of
 * {@code false} allows all members to perform the action, corresponding to
 * {@link #ANYONE}. The {@link #of(boolean)} factory method encodes this
 * mapping.
 */
@ProtobufEnum
public enum ChatPolicy {
    /**
     * Allows both admins and users
     */
    ANYONE,

    /**
     * Allows only admins
     */
    ADMINS;

    /**
     * Returns a GroupPolicy based on a boolean value obtained from Whatsapp
     *
     * @param input the boolean value obtained from Whatsapp
     * @return a non-null GroupPolicy
     */
    public static ChatPolicy of(boolean input) {
        return input ? ADMINS : ANYONE;
    }
}