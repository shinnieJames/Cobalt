package com.github.auties00.cobalt.model.bot.plugin;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

/**
 * A single external account linked to the bot for enhanced plugin
 * functionality.
 *
 * <p>Linked accounts allow the bot to access first-party services on behalf
 * of the user (e.g. accessing Meta services). Each linked account has a
 * {@link BotLinkedAccountType} that identifies the type of linkage.
 *
 * @see BotLinkedAccountsMetadata
 */
@ProtobufMessage(name = "BotLinkedAccount")
public final class BotLinkedAccount {
    /**
     * The type of linked account.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    BotLinkedAccountType type;

    /**
     * Constructs a new {@code BotLinkedAccount} with the specified type.
     *
     * @param type the linked account type, or {@code null}
     */
    BotLinkedAccount(BotLinkedAccountType type) {
        this.type = type;
    }

    /**
     * Returns the type of linked account.
     *
     * @return an {@code Optional} describing the linked account type, or an
     *         empty {@code Optional} if not set
     */
    public Optional<BotLinkedAccountType> type() {
        return Optional.ofNullable(type);
    }

    /**
     * Sets the type of linked account.
     *
     * @param type the new linked account type, or {@code null}
     */
    public void setType(BotLinkedAccountType type) {
        this.type = type;
    }

    /**
     * The type of external account linked to the bot.
     */
    @ProtobufEnum(name = "BotLinkedAccount.BotLinkedAccountType")
    public static enum BotLinkedAccountType {
        /**
         * A first-party (1P) linked account, providing access to Meta's own
         * services.
         */
        BOT_LINKED_ACCOUNT_TYPE_1P(0);

        /**
         * Constructs a new linked account type constant with the specified
         * protobuf index.
         *
         * @param index the protobuf enum index
         */
        BotLinkedAccountType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        /**
         * Returns the protobuf enum index of this linked account type.
         *
         * @return the protobuf index
         */
        public int index() {
            return this.index;
        }
    }
}
