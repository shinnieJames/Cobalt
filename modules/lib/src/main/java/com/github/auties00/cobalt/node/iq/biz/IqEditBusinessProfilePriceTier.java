package com.github.auties00.cobalt.node.iq.biz;

import java.util.Objects;

/**
 * The typed {@code (id, symbol, description)} price-tier triple carried inside {@link IqEditBusinessProfileRequest}.
 *
 * @apiNote
 * Use this triple to populate the {@code <price_tier/>} child of an edit-profile mutation; the {@code id} identifies the tier in the WA Web price-tier catalog, the {@code symbol} is the currency symbol shown next to the tier label, and the {@code description} is the free-text band rendered in the SMB profile editor.
 *
 * @implNote
 * This implementation mirrors the cached shape produced by {@code WAWebBizGetPriceTiersQuery.getCachedPriceTierById}: the WAP envelope routes {@code id} and {@code symbol} into attributes and the description into the {@code <price_tier/>} content.
 */
public final class IqEditBusinessProfilePriceTier {
    /**
     * The price-tier identifier within the WA Web price-tier catalog.
     */
    private final String id;

    /**
     * The currency symbol shown next to the tier label.
     */
    private final String symbol;

    /**
     * The free-text band description rendered in the SMB profile editor.
     */
    private final String description;

    /**
     * Constructs a typed tier.
     *
     * @apiNote
     * Call this constructor when projecting a cached price-tier entry into the typed model; all three fields are mandatory because the wire envelope rejects an entry that omits any of them.
     *
     * @param id          the tier identifier; never {@code null}
     * @param symbol      the currency symbol; never {@code null}
     * @param description the band description; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public IqEditBusinessProfilePriceTier(String id, String symbol, String description) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.symbol = Objects.requireNonNull(symbol, "symbol cannot be null");
        this.description = Objects.requireNonNull(description, "description cannot be null");
    }

    /**
     * Returns the tier identifier.
     *
     * @apiNote
     * Use this getter to read back the tier id that the edit-profile mutation will stamp.
     *
     * @return the identifier; never {@code null}
     */
    public String id() {
        return id;
    }

    /**
     * Returns the currency symbol.
     *
     * @apiNote
     * Use this getter to read back the per-tier currency symbol; the value drives the symbol shown next to the tier label.
     *
     * @return the symbol; never {@code null}
     */
    public String symbol() {
        return symbol;
    }

    /**
     * Returns the band description.
     *
     * @apiNote
     * Use this getter to read back the free-text band description rendered in the SMB profile editor.
     *
     * @return the description; never {@code null}
     */
    public String description() {
        return description;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (IqEditBusinessProfilePriceTier) obj;
        return Objects.equals(this.id, that.id)
                && Objects.equals(this.symbol, that.symbol)
                && Objects.equals(this.description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, symbol, description);
    }

    @Override
    public String toString() {
        return "IqEditBusinessProfilePriceTier[id=" + id + ", symbol=" + symbol
                + ", description=" + description + ']';
    }
}
