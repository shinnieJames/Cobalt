package com.github.auties00.cobalt.node.smax.privacy;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code <iq xmlns="optoutlist" type="set">} stanza applying a marketing-message opt-out action.
 *
 * @apiNote
 * Drives the marketing-messages user-controls flows; the WA Web caller is
 * {@code WAWebOptOutUserJob.optOutUser}, {@code optInUser}, and {@code signupUser}, which all funnel through
 * {@code WASmaxBlocklistsUpdateOptOutListRPC.sendUpdateOptOutListRPC}. The three mandatory attributes
 * ({@code jid}, {@code category}, {@code action}) identify the target business and the action being applied;
 * the five optional attributes pass the cache digest, opt-out reason, entry-point, signup id, and duration.
 *
 * @implNote
 * This implementation centralises {@code generateId()} on the client dispatcher, so {@link #toNode()} omits the
 * {@code id} attribute; every other SMAX request in the package follows the same convention.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutBlocklistsUpdateOptOutListRequest")
public final class SmaxUpdateOptOutListRequest implements SmaxOperation.Request {
    /**
     * The target business JID being opted in or out of marketing messages.
     */
    private final Jid itemJid;

    /**
     * The marketing-messages category that scopes the opt-out.
     */
    private final String itemCategory;

    /**
     * The action string mapped onto the wire ({@code "block"}, {@code "unblock"}, or {@code "signup"}).
     */
    private final String itemAction;

    /**
     * The cached digest of the opt-out list, or {@code null} to skip the cache-match shortcut.
     */
    private final String itemDhash;

    /**
     * The optional marketing-messages reason marker.
     */
    private final String itemReason;

    /**
     * The optional marketing-messages entry-point marker.
     */
    private final String itemEntryPoint;

    /**
     * The optional signup id for the {@code "signup"} action.
     */
    private final String itemSignupId;

    /**
     * The optional opt-out duration in seconds.
     */
    private final Integer itemDuration;

    /**
     * Constructs an update-opt-out request with every optional field.
     *
     * @apiNote
     * The caller derives {@code itemAction} from the high-level operation: {@code "block"} for
     * {@code WAWebOptOutUserJob.optOutUser}, {@code "unblock"} for {@code optInUser}, {@code "signup"} for
     * {@code signupUser}. Pass {@code null} for any optional field whose value is unknown.
     *
     * @param itemJid        the target business JID; never {@code null}
     * @param itemCategory   the marketing category; never {@code null}
     * @param itemAction     the action string; never {@code null}
     * @param itemDhash      the cached digest; may be {@code null}
     * @param itemReason     the reason marker; may be {@code null}
     * @param itemEntryPoint the entry-point marker; may be {@code null}
     * @param itemSignupId   the signup id; may be {@code null}
     * @param itemDuration   the opt-out duration in seconds; may be {@code null}
     * @throws NullPointerException if {@code itemJid}, {@code itemCategory}, or {@code itemAction} is
     *                              {@code null}
     */
    public SmaxUpdateOptOutListRequest(Jid itemJid, String itemCategory, String itemAction,
                   String itemDhash, String itemReason, String itemEntryPoint,
                   String itemSignupId, Integer itemDuration) {
        this.itemJid = Objects.requireNonNull(itemJid, "itemJid cannot be null");
        this.itemCategory = Objects.requireNonNull(itemCategory, "itemCategory cannot be null");
        this.itemAction = Objects.requireNonNull(itemAction, "itemAction cannot be null");
        this.itemDhash = itemDhash;
        this.itemReason = itemReason;
        this.itemEntryPoint = itemEntryPoint;
        this.itemSignupId = itemSignupId;
        this.itemDuration = itemDuration;
    }

    /**
     * Returns the target business JID.
     *
     * @return the JID; never {@code null}
     */
    public Jid itemJid() {
        return itemJid;
    }

    /**
     * Returns the marketing category.
     *
     * @return the category; never {@code null}
     */
    public String itemCategory() {
        return itemCategory;
    }

    /**
     * Returns the action string.
     *
     * @return the action; never {@code null}
     */
    public String itemAction() {
        return itemAction;
    }

    /**
     * Returns the cached digest when set.
     *
     * @return an {@link Optional} carrying the digest, or empty when no cached digest was supplied
     */
    public Optional<String> itemDhash() {
        return Optional.ofNullable(itemDhash);
    }

    /**
     * Returns the reason marker when set.
     *
     * @return an {@link Optional} carrying the reason, or empty when no reason was supplied
     */
    public Optional<String> itemReason() {
        return Optional.ofNullable(itemReason);
    }

    /**
     * Returns the entry-point marker when set.
     *
     * @return an {@link Optional} carrying the entry-point, or empty when no entry-point was supplied
     */
    public Optional<String> itemEntryPoint() {
        return Optional.ofNullable(itemEntryPoint);
    }

    /**
     * Returns the signup id when set.
     *
     * @return an {@link Optional} carrying the signup id, or empty when no signup id was supplied
     */
    public Optional<String> itemSignupId() {
        return Optional.ofNullable(itemSignupId);
    }

    /**
     * Returns the opt-out duration when set.
     *
     * @return an {@link Optional} carrying the duration in seconds, or empty when no duration was supplied
     */
    public Optional<Integer> itemDuration() {
        return Optional.ofNullable(itemDuration);
    }

    /**
     * Builds the outbound {@code <iq>} stanza ready for dispatch.
     *
     * @apiNote
     * The returned {@link NodeBuilder} addresses {@code s.whatsapp.net} with {@code xmlns="optoutlist"} and
     * {@code type="set"}; the inner {@code <item>} child always carries the three mandatory attributes
     * (target JID, category, action), and the five optional attributes are added only when their fields are
     * set. The {@code id} attribute is filled in downstream.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutBlocklistsUpdateOptOutListRequest",
            exports = "makeUpdateOptOutListRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var itemBuilder = new NodeBuilder()
                .description("item")
                .attribute("jid", itemJid)
                .attribute("category", itemCategory)
                .attribute("action", itemAction);
        if (itemDhash != null) {
            itemBuilder.attribute("dhash", itemDhash);
        }
        if (itemReason != null) {
            itemBuilder.attribute("reason", itemReason);
        }
        if (itemEntryPoint != null) {
            itemBuilder.attribute("entry_point", itemEntryPoint);
        }
        if (itemSignupId != null) {
            itemBuilder.attribute("signup_id", itemSignupId);
        }
        if (itemDuration != null) {
            itemBuilder.attribute("duration", itemDuration.intValue());
        }
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "optoutlist")
                .attribute("to", JidServer.user())
                .attribute("type", "set")
                .content(itemBuilder.build());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxUpdateOptOutListRequest) obj;
        return Objects.equals(this.itemJid, that.itemJid)
                && Objects.equals(this.itemCategory, that.itemCategory)
                && Objects.equals(this.itemAction, that.itemAction)
                && Objects.equals(this.itemDhash, that.itemDhash)
                && Objects.equals(this.itemReason, that.itemReason)
                && Objects.equals(this.itemEntryPoint, that.itemEntryPoint)
                && Objects.equals(this.itemSignupId, that.itemSignupId)
                && Objects.equals(this.itemDuration, that.itemDuration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemJid, itemCategory, itemAction, itemDhash, itemReason,
                itemEntryPoint, itemSignupId, itemDuration);
    }

    @Override
    public String toString() {
        return "SmaxUpdateOptOutListRequest[itemJid=" + itemJid
                + ", itemCategory=" + itemCategory
                + ", itemAction=" + itemAction
                + ", itemDhash=" + itemDhash
                + ", itemReason=" + itemReason
                + ", itemEntryPoint=" + itemEntryPoint
                + ", itemSignupId=" + itemSignupId
                + ", itemDuration=" + itemDuration + ']';
    }
}
