package com.github.auties00.cobalt.model.bot.session;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

/**
 * Metadata controlling age-verification collection for bot interactions on
 * WhatsApp.
 *
 * <p>Before a user can interact with certain AI features, the client may need
 * to collect and verify the user's age. This metadata signals whether the
 * user is eligible for age collection, whether the client should present the
 * age-collection UI, and which collection method to use.
 *
 * <p>This metadata is attached to a bot message via
 * {@link com.github.auties00.cobalt.model.bot.BotMetadata#botAgeCollectionMetadata()}.
 */
@ProtobufMessage(name = "BotAgeCollectionMetadata")
public final class BotAgeCollectionMetadata {
    /**
     * Whether the user is eligible for age collection.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean ageCollectionEligible;

    /**
     * Whether the client should trigger the age-collection UI flow.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.BOOL)
    Boolean shouldTriggerAgeCollectionOnClient;

    /**
     * The type of age-collection mechanism to use.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.ENUM)
    AgeCollectionType ageCollectionType;


    /**
     * Constructs a new {@code BotAgeCollectionMetadata} with the specified
     * values.
     *
     * @param ageCollectionEligible               whether the user is eligible
     * @param shouldTriggerAgeCollectionOnClient   whether to trigger the UI
     * @param ageCollectionType                    the collection mechanism
     */
    BotAgeCollectionMetadata(Boolean ageCollectionEligible, Boolean shouldTriggerAgeCollectionOnClient, AgeCollectionType ageCollectionType) {
        this.ageCollectionEligible = ageCollectionEligible;
        this.shouldTriggerAgeCollectionOnClient = shouldTriggerAgeCollectionOnClient;
        this.ageCollectionType = ageCollectionType;
    }

    /**
     * Returns whether the user is eligible for age collection.
     *
     * @return {@code true} if the user is eligible, {@code false} otherwise
     *         or if not set
     */
    public boolean ageCollectionEligible() {
        return ageCollectionEligible != null && ageCollectionEligible;
    }

    /**
     * Returns whether the client should trigger the age-collection UI flow.
     *
     * @return {@code true} if the client should trigger the flow,
     *         {@code false} otherwise or if not set
     */
    public boolean shouldTriggerAgeCollectionOnClient() {
        return shouldTriggerAgeCollectionOnClient != null && shouldTriggerAgeCollectionOnClient;
    }

    /**
     * Returns the type of age-collection mechanism to use.
     *
     * @return an {@code Optional} describing the collection type, or an
     *         empty {@code Optional} if not set
     */
    public Optional<AgeCollectionType> ageCollectionType() {
        return Optional.ofNullable(ageCollectionType);
    }

    /**
     * Sets whether the user is eligible for age collection.
     *
     * @param ageCollectionEligible the new eligibility flag, or {@code null}
     */
    public void setAgeCollectionEligible(Boolean ageCollectionEligible) {
        this.ageCollectionEligible = ageCollectionEligible;
    }

    /**
     * Sets whether the client should trigger the age-collection UI flow.
     *
     * @param shouldTriggerAgeCollectionOnClient the new trigger flag, or
     *        {@code null}
     */
    public void setShouldTriggerAgeCollectionOnClient(Boolean shouldTriggerAgeCollectionOnClient) {
        this.shouldTriggerAgeCollectionOnClient = shouldTriggerAgeCollectionOnClient;
    }

    /**
     * Sets the type of age-collection mechanism to use.
     *
     * @param ageCollectionType the new collection type, or {@code null}
     */
    public void setAgeCollectionType(AgeCollectionType ageCollectionType) {
        this.ageCollectionType = ageCollectionType;
    }

    /**
     * The mechanism used to collect and verify a user's age before granting
     * access to AI bot features.
     */
    @ProtobufEnum(name = "BotAgeCollectionMetadata.AgeCollectionType")
    public static enum AgeCollectionType {
        /**
         * A simple binary over-18 age check — the user confirms whether they
         * are at least 18 years old.
         */
        OVER_18_BINARY(0),

        /**
         * A progressive age-collection flow managed by Meta's Waffle
         * experimentation framework, which may present different
         * verification steps depending on the user's experiment cohort.
         */
        WAFFLE(1);

        /**
         * Constructs a new age-collection type constant with the specified
         * protobuf index.
         *
         * @param index the protobuf enum index
         */
        AgeCollectionType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        /**
         * Returns the protobuf enum index of this age-collection type.
         *
         * @return the protobuf index
         */
        public int index() {
            return this.index;
        }
    }
}
