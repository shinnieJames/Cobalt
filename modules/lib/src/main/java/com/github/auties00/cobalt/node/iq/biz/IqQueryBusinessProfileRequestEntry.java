package com.github.auties00.cobalt.node.iq.biz;

import com.github.auties00.cobalt.model.jid.Jid;
import java.util.Objects;
import java.util.Optional;

/**
 * One {@code (businessJid, tag)} pair fanned out into a {@code <profile/>}
 * child of an {@link IqQueryBusinessProfileRequest}.
 *
 * @apiNote
 * Use this entry to name one merchant in a fan-out business-profile fetch
 * and optionally to attach the version tag the relay previously returned
 * for that merchant; when the supplied tag matches the cached version the
 * relay echoes a header-only acknowledgement instead of the full profile
 * body so chat openers and the merchant directory can refresh many
 * merchants in one round-trip.
 */
public final class IqQueryBusinessProfileRequestEntry {
    /**
     * The merchant JID stamped into the {@code jid} attribute of the
     * {@code <profile/>} child.
     */
    private final Jid businessJid;

    /**
     * The optional version tag stamped into the {@code tag} attribute of
     * the {@code <profile/>} child.
     */
    private final Integer tag;

    /**
     * Constructs an entry.
     *
     * @apiNote
     * Pass the merchant JID together with the previously echoed version
     * tag to enable the relay's conditional-fetch path; pass a
     * {@code null} tag to force the full profile body to be returned.
     *
     * @param businessJid the merchant JID; never {@code null}
     * @param tag         the optional version tag; may be {@code null}
     * @throws NullPointerException if {@code businessJid} is {@code null}
     */
    public IqQueryBusinessProfileRequestEntry(Jid businessJid, Integer tag) {
        this.businessJid = Objects.requireNonNull(businessJid, "businessJid cannot be null");
        this.tag = tag;
    }

    /**
     * Returns the merchant JID.
     *
     * @apiNote
     * Use this getter to read back the merchant JID the fan-out will
     * name; the relay routes it verbatim into the {@code jid} attribute
     * of the resulting {@code <profile/>} child.
     *
     * @return the merchant JID; never {@code null}
     */
    public Jid businessJid() {
        return businessJid;
    }

    /**
     * Returns the optional version tag.
     *
     * @apiNote
     * Use this getter to read back the cached version tag the entry
     * carries; an empty {@link Optional} means the request forces the
     * relay to return the full profile body for this merchant.
     *
     * @return an {@link Optional} carrying the tag
     */
    public Optional<Integer> tag() {
        return Optional.ofNullable(tag);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (IqQueryBusinessProfileRequestEntry) obj;
        return Objects.equals(this.businessJid, that.businessJid)
                && Objects.equals(this.tag, that.tag);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(businessJid, tag);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "IqQueryBusinessProfileRequestEntry[businessJid=" + businessJid
                + ", tag=" + tag + ']';
    }
}
