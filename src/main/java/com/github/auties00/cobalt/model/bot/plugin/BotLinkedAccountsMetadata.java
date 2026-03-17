package com.github.auties00.cobalt.model.bot.plugin;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Metadata about external accounts linked to the bot for plugin access.
 *
 * <p>This message is attached to {@code BotMetadata} (field 18) and carries
 * the list of linked accounts, along with authentication tokens and error
 * state for the account-linking flow.
 */
@ProtobufMessage(name = "BotLinkedAccountsMetadata")
public final class BotLinkedAccountsMetadata {
    /**
     * The list of external accounts currently linked to the bot.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    List<BotLinkedAccount> accounts;

    /**
     * Opaque authentication tokens for the account-linking flow.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] acAuthTokens;

    /**
     * An error code returned by the account-linking service, or {@code null}
     * if no error occurred. A value of {@code 0} typically indicates success.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.INT32)
    Integer acErrorCode;

    /**
     * Constructs a new {@code BotLinkedAccountsMetadata} with the specified
     * values.
     *
     * @param accounts     the linked accounts, or {@code null}
     * @param acAuthTokens the authentication tokens, or {@code null}
     * @param acErrorCode  the error code, or {@code null}
     */
    BotLinkedAccountsMetadata(List<BotLinkedAccount> accounts, byte[] acAuthTokens, Integer acErrorCode) {
        this.accounts = accounts;
        this.acAuthTokens = acAuthTokens;
        this.acErrorCode = acErrorCode;
    }

    /**
     * Returns the list of external accounts currently linked to the bot.
     *
     * @return an unmodifiable list of linked accounts, possibly empty
     */
    public List<BotLinkedAccount> accounts() {
        return accounts == null ? List.of() : Collections.unmodifiableList(accounts);
    }

    /**
     * Returns the opaque authentication tokens for the account-linking flow.
     *
     * @return an {@code Optional} describing the auth tokens, or an empty
     *         {@code Optional} if not set
     */
    public Optional<byte[]> acAuthTokens() {
        return Optional.ofNullable(acAuthTokens);
    }

    /**
     * Returns the error code from the account-linking service.
     *
     * @return an {@code OptionalInt} describing the error code, or an empty
     *         {@code OptionalInt} if not set
     */
    public OptionalInt acErrorCode() {
        return acErrorCode == null ? OptionalInt.empty() : OptionalInt.of(acErrorCode);
    }

    /**
     * Sets the list of external accounts linked to the bot.
     *
     * @param accounts the new list of linked accounts, or {@code null}
     */
    public void setAccounts(List<BotLinkedAccount> accounts) {
        this.accounts = accounts;
    }

    /**
     * Sets the opaque authentication tokens for the account-linking flow.
     *
     * @param acAuthTokens the new auth tokens, or {@code null}
     */
    public void setAcAuthTokens(byte[] acAuthTokens) {
        this.acAuthTokens = acAuthTokens;
    }

    /**
     * Sets the error code from the account-linking service.
     *
     * @param acErrorCode the new error code, or {@code null}
     */
    public void setAcErrorCode(Integer acErrorCode) {
        this.acErrorCode = acErrorCode;
    }
}
