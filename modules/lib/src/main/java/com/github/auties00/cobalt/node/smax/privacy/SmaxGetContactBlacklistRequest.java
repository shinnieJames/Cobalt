package com.github.auties00.cobalt.node.smax.privacy;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxIqErrorResponseMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code <iq xmlns="privacy" type="get">} stanza that fetches a per-category contact-blacklist.
 *
 * @apiNote
 * Drives Settings > Account > Privacy for the Last Seen, About, Group-Add, and Profile-Picture surfaces; the
 * WA Web caller is {@code WAWebQueryPrivacyDisallowedListLidJob.queryPrivacyDisallowedListLid}, which maps a
 * {@code PrivacyDisallowedListType} to one of the four wire category names ({@code "last"}, {@code "status"},
 * {@code "groupadd"}, {@code "profile"}) and pairs it with
 * {@link SmaxGetContactBlacklistAddressingMode#LID} to drive the LID-migration sub-flow.
 *
 * @implNote
 * This implementation collapses WA Web's {@code GetIQMixin} and the two
 * {@code GetContactBlacklistLID/PN} mixin arms into the single {@link #toNode()} below; the LID-versus-PN
 * disambiguation reduces to the single {@code addressing_mode} attribute on the inner {@code <privacy/>}
 * element.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutPrivacyGetContactBlacklistRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutPrivacyGetIQMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutPrivacyBaseIQGetRequestMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutPrivacyGetContactBlacklistGetContactBlacklistLIDOrGetContactBlacklistPNMixinGroup")
@WhatsAppWebModule(moduleName = "WASmaxOutPrivacyGetContactBlacklistGetContactBlacklistLIDMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutPrivacyGetContactBlacklistGetContactBlacklistPNMixin")
@WhatsAppWebModule(moduleName = "WASmaxOutPrivacyCategoryNamesForContactBlacklistMixin")
public final class SmaxGetContactBlacklistRequest implements SmaxOperation.Request {
    /**
     * The wire-level privacy category name routed into the {@code <list name=...>} attribute.
     *
     * @apiNote
     * One of the four wire constants ({@code "last"}, {@code "status"}, {@code "groupadd"}, {@code "profile"})
     * mapped from {@code WAWebSchemaPrivacyDisallowedList.PrivacyDisallowedListType} by the caller.
     */
    private final String categoryName;

    /**
     * The wire addressing mode selecting the LID or PN variant of the {@code <privacy/>} envelope.
     */
    private final SmaxGetContactBlacklistAddressingMode addressingMode;

    /**
     * Constructs a contact-blacklist request.
     *
     * @apiNote
     * The caller should pass {@link SmaxGetContactBlacklistAddressingMode#LID} to follow the WA Web LID
     * migration path; the {@code PN} mode is the legacy fallback retained for pre-migration clients.
     *
     * @param categoryName   the wire category name; never {@code null}
     * @param addressingMode the addressing mode; never {@code null}
     * @throws NullPointerException if either argument is {@code null}
     */
    public SmaxGetContactBlacklistRequest(String categoryName, SmaxGetContactBlacklistAddressingMode addressingMode) {
        this.categoryName = Objects.requireNonNull(categoryName, "categoryName cannot be null");
        this.addressingMode = Objects.requireNonNull(addressingMode, "addressingMode cannot be null");
    }

    /**
     * Returns the wire category name.
     *
     * @return the category name; never {@code null}
     */
    public String categoryName() {
        return categoryName;
    }

    /**
     * Returns the wire addressing mode.
     *
     * @return the addressing mode; never {@code null}
     */
    public SmaxGetContactBlacklistAddressingMode addressingMode() {
        return addressingMode;
    }

    /**
     * Builds the outbound {@code <iq>} stanza ready for dispatch.
     *
     * @apiNote
     * The returned {@link NodeBuilder} addresses {@code s.whatsapp.net} with {@code xmlns="privacy"} and
     * {@code type="get"}; the {@code id} attribute is generated downstream by the central client dispatcher.
     * The inner {@code <privacy><list value="contact_blacklist" name="..."/></privacy>} payload is fixed; the
     * {@code addressing_mode="lid"} attribute is added only when the addressing mode is
     * {@link SmaxGetContactBlacklistAddressingMode#LID}.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutPrivacyGetContactBlacklistRequest",
            exports = "makeGetContactBlacklistRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var listNode = new NodeBuilder()
                .description("list")
                .attribute("value", "contact_blacklist")
                .attribute("name", categoryName)
                .build();
        var privacyBuilder = new NodeBuilder()
                .description("privacy");
        if (addressingMode == SmaxGetContactBlacklistAddressingMode.LID) {
            privacyBuilder.attribute("addressing_mode", "lid");
        }
        var privacyNode = privacyBuilder
                .content(listNode)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "privacy")
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(privacyNode);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxGetContactBlacklistRequest) obj;
        return Objects.equals(this.categoryName, that.categoryName)
                && this.addressingMode == that.addressingMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(categoryName, addressingMode);
    }

    @Override
    public String toString() {
        return "SmaxGetContactBlacklistRequest[categoryName=" + categoryName
                + ", addressingMode=" + addressingMode + ']';
    }
}
