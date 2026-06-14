package com.github.auties00.cobalt.model.cloud;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A WhatsApp Business Account status change, decoded from an {@code account_update},
 * {@code account_alerts}, or {@code account_review_update} webhook change.
 *
 * <p>The platform reports account-level transitions through this family of changes: verification
 * outcomes, bans, messaging restrictions, policy violations, and review decisions. The fields are
 * a union of the payloads the three changes carry; absent members are empty.
 */
public final class CloudAccountUpdate {
    /**
     * The transition event, for example {@code VERIFIED_ACCOUNT} or {@code DISABLED_UPDATE}, or
     * {@code null} when the change is a review decision.
     */
    private final String event;

    /**
     * The phone number the update applies to, or {@code null} when account-wide.
     */
    private final String phoneNumber;

    /**
     * The review decision, for example {@code APPROVED}, or {@code null} when the change is not a
     * review.
     */
    private final String decision;

    /**
     * The ban state, for example {@code SCHEDULE_FOR_DISABLE}, or {@code null} when no ban was
     * reported.
     */
    private final String banState;

    /**
     * The instant the ban takes effect, or {@code null} when no ban was reported.
     */
    private final Instant banDate;

    /**
     * The messaging restrictions in effect.
     */
    private final List<Restriction> restrictions;

    /**
     * The policy violation type, or {@code null} when no violation was reported.
     */
    private final String violationType;

    /**
     * Constructs a new account update.
     *
     * @param event         the transition event, or {@code null}
     * @param phoneNumber   the phone number, or {@code null}
     * @param decision      the review decision, or {@code null}
     * @param banState      the ban state, or {@code null}
     * @param banDate       the ban instant, or {@code null}
     * @param restrictions  the messaging restrictions, or {@code null} for none
     * @param violationType the policy violation type, or {@code null}
     */
    public CloudAccountUpdate(String event, String phoneNumber, String decision, String banState,
                              Instant banDate, List<Restriction> restrictions, String violationType) {
        this.event = event;
        this.phoneNumber = phoneNumber;
        this.decision = decision;
        this.banState = banState;
        this.banDate = banDate;
        this.restrictions = restrictions == null ? List.of() : List.copyOf(restrictions);
        this.violationType = violationType;
    }

    /**
     * Returns the transition event.
     *
     * @return an {@link Optional} carrying the event, or empty when the change is a review decision
     */
    public Optional<String> event() {
        return Optional.ofNullable(event);
    }

    /**
     * Returns the phone number the update applies to.
     *
     * @return an {@link Optional} carrying the phone number, or empty when account-wide
     */
    public Optional<String> phoneNumber() {
        return Optional.ofNullable(phoneNumber);
    }

    /**
     * Returns the review decision.
     *
     * @return an {@link Optional} carrying the decision, or empty when the change is not a review
     */
    public Optional<String> decision() {
        return Optional.ofNullable(decision);
    }

    /**
     * Returns the ban state.
     *
     * @return an {@link Optional} carrying the ban state, or empty when no ban was reported
     */
    public Optional<String> banState() {
        return Optional.ofNullable(banState);
    }

    /**
     * Returns the instant the ban takes effect.
     *
     * @return an {@link Optional} carrying the ban instant, or empty when no ban was reported
     */
    public Optional<Instant> banDate() {
        return Optional.ofNullable(banDate);
    }

    /**
     * Returns the messaging restrictions in effect.
     *
     * @return an unmodifiable list of restrictions, empty when none were reported
     */
    public List<Restriction> restrictions() {
        return restrictions;
    }

    /**
     * Returns the policy violation type.
     *
     * @return an {@link Optional} carrying the violation type, or empty when none was reported
     */
    public Optional<String> violationType() {
        return Optional.ofNullable(violationType);
    }

    /**
     * A single messaging restriction reported by an account update.
     */
    public static final class Restriction {
        /**
         * The restriction type, for example {@code RESTRICTED_ADD_PHONE_NUMBER_ACTION}.
         */
        private final String restrictionType;

        /**
         * The instant the restriction expires, or {@code null} when indefinite.
         */
        private final Instant expiration;

        /**
         * Constructs a new restriction.
         *
         * @param restrictionType the restriction type
         * @param expiration      the expiration instant, or {@code null} when indefinite
         * @throws NullPointerException if {@code restrictionType} is {@code null}
         */
        public Restriction(String restrictionType, Instant expiration) {
            this.restrictionType = Objects.requireNonNull(restrictionType, "restrictionType must not be null");
            this.expiration = expiration;
        }

        /**
         * Returns the restriction type.
         *
         * @return the restriction type
         */
        public String restrictionType() {
            return restrictionType;
        }

        /**
         * Returns the instant the restriction expires.
         *
         * @return an {@link Optional} carrying the expiration, or empty when indefinite
         */
        public Optional<Instant> expiration() {
            return Optional.ofNullable(expiration);
        }
    }
}
