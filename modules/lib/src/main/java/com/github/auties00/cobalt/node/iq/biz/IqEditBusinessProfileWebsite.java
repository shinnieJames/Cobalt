package com.github.auties00.cobalt.node.iq.biz;

import java.util.Objects;

/**
 * The typed website slot carried inside the {@code <website/>} children of an {@link IqEditBusinessProfileRequest}.
 *
 * @apiNote
 * Use this slot to express one of the at-most-two website URLs that the SMB profile editor shows alongside the merchant's contact details; pass an empty string as the URL to clear the slot through the edit-profile mutation envelope.
 *
 * @implNote
 * This implementation mirrors the wire shape produced by {@code WAWebBusinessProfileJob.editBusinessProfile}: the request consumes at most the first two entries of the website list and emits one {@code <website/>} child per entry, with the URL routed verbatim into the child content.
 */
public final class IqEditBusinessProfileWebsite {
    /**
     * The website URL routed verbatim into the {@code <website/>} child content; an empty string clears the slot.
     */
    private final String url;

    /**
     * Constructs a typed slot.
     *
     * @apiNote
     * Call this constructor with the URL string; pass an empty string when the slot should be cleared but the {@code <website/>} envelope structure should still be preserved.
     *
     * @param url the website URL; never {@code null}
     * @throws NullPointerException if {@code url} is {@code null}
     */
    public IqEditBusinessProfileWebsite(String url) {
        this.url = Objects.requireNonNull(url, "url cannot be null");
    }

    /**
     * Returns the website URL.
     *
     * @apiNote
     * Use this getter to read back the URL that the slot will stamp; an empty string clears the slot.
     *
     * @return the URL; never {@code null}
     */
    public String url() {
        return url;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (IqEditBusinessProfileWebsite) obj;
        return Objects.equals(this.url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }

    @Override
    public String toString() {
        return "IqEditBusinessProfileWebsite[url=" + url + ']';
    }
}
