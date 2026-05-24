package com.github.auties00.cobalt.node.smax.profilepicture;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The optional add-request sub-payload of a
 * {@link SmaxProfilePictureGetRequest}; carries the join-code,
 * optional admin JID, and expiration timestamp the relay needs to
 * validate a group-join-link invitation while fetching a picture.
 *
 * @apiNote
 * Pass an instance to
 * {@link SmaxProfilePictureGetRequest}'s constructor when fetching a
 * picture as part of a group-add-request flow; the relay correlates
 * the join intent with the picture fetch so the UI can show the
 * inviter's avatar alongside the join prompt.
 *
 * @implNote
 * This implementation projects the WA Web
 * {@code mergeAddRequestMixin} payload as a single
 * {@link #toNode()} call rather than a separate smax-mixin merge.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutProfilePictureAddRequestMixin")
public final class SmaxProfilePictureGetAddRequestMixin {
    /**
     * The mandatory {@code code} attribute carrying the
     * group-join-link token.
     */
    private final String addRequestCode;

    /**
     * The optional {@code admin} attribute carrying the admin JID
     * that issued the join link.
     */
    private final Jid addRequestAdmin;

    /**
     * The mandatory {@code expiration} attribute carrying the join
     * link's expiry timestamp.
     */
    private final long addRequestExpiration;

    /**
     * Constructs an add-request payload.
     *
     * @apiNote
     * Use this when assembling a {@link SmaxProfilePictureGetRequest}
     * for a join-link-triggered picture fetch.
     *
     * @param addRequestCode       the join-link code; never
     *                             {@code null}
     * @param addRequestAdmin      the optional admin JID; may be
     *                             {@code null}
     * @param addRequestExpiration the expiry timestamp
     * @throws NullPointerException if {@code addRequestCode} is
     *                              {@code null}
     */
    public SmaxProfilePictureGetAddRequestMixin(String addRequestCode, Jid addRequestAdmin, long addRequestExpiration) {
        this.addRequestCode = Objects.requireNonNull(addRequestCode, "addRequestCode cannot be null");
        this.addRequestAdmin = addRequestAdmin;
        this.addRequestExpiration = addRequestExpiration;
    }

    /**
     * Returns the join-link code.
     *
     * @return the code; never {@code null}
     */
    public String addRequestCode() {
        return addRequestCode;
    }

    /**
     * Returns the optional admin JID.
     *
     * @apiNote
     * Read by {@link #toNode()} to decide whether to stamp the
     * {@code <add_request admin=...>} attribute.
     *
     * @return an {@link Optional} carrying the JID, or
     *         {@link Optional#empty()} when omitted
     */
    public Optional<Jid> addRequestAdmin() {
        return Optional.ofNullable(addRequestAdmin);
    }

    /**
     * Returns the expiry timestamp.
     *
     * @return the timestamp
     */
    public long addRequestExpiration() {
        return addRequestExpiration;
    }

    /**
     * Builds the {@code <add_request>} child node.
     *
     * @apiNote
     * The node has shape
     * {@snippet lang=xml :
     * <add_request code="..." expiration="N" admin="..."?/>
     * }
     *
     * @return the {@link Node}
     */
    @WhatsAppWebExport(moduleName = "WASmaxOutProfilePictureAddRequestMixin",
            exports = "mergeAddRequestMixin",
            adaptation = WhatsAppAdaptation.DIRECT)
    public Node toNode() {
        var builder = new NodeBuilder()
                .description("add_request")
                .attribute("code", addRequestCode)
                .attribute("expiration", addRequestExpiration);
        if (addRequestAdmin != null) {
            builder.attribute("admin", addRequestAdmin);
        }
        return builder.build();
    }

    /**
     * Compares this payload to another for value equality.
     *
     * @param obj the object to compare against
     * @return {@code true} when {@code obj} is a
     *         {@link SmaxProfilePictureGetAddRequestMixin} with
     *         identical fields
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxProfilePictureGetAddRequestMixin) obj;
        return this.addRequestExpiration == that.addRequestExpiration
                && Objects.equals(this.addRequestCode, that.addRequestCode)
                && Objects.equals(this.addRequestAdmin, that.addRequestAdmin);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(addRequestCode, addRequestAdmin, addRequestExpiration);
    }

    /**
     * Returns a debug-friendly representation of this payload.
     *
     * @apiNote
     * Intended for logging; the format is not part of the public
     * contract.
     *
     * @return the string form
     */
    @Override
    public String toString() {
        return "SmaxProfilePictureGetAddRequestMixin[addRequestCode=" + addRequestCode
                + ", addRequestAdmin=" + addRequestAdmin
                + ", addRequestExpiration=" + addRequestExpiration + ']';
    }
}
