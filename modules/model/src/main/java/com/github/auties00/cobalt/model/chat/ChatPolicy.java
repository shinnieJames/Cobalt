package com.github.auties00.cobalt.model.chat;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import it.auties.protobuf.annotation.ProtobufEnum;

/**
 * Represents a permission policy for group or community actions in WhatsApp.
 *
 * <p>WhatsApp groups and communities use boolean toggles to control which members
 * can perform certain actions such as editing the group subject, changing the group
 * description, or sending messages. When the toggle is {@code true} the action is
 * restricted to administrators; when {@code false} any member may perform it. This
 * enum provides a human-readable mapping over those boolean values.
 *
 * <p>Typical uses include controlling who may edit the group info
 * ({@code announce} and {@code restrict} group settings), who may send messages
 * in announcement groups, who may add new participants
 * ({@code member_add_mode}), and who may share the group's invite link
 * ({@code member_link_mode}).
 *
 * <p>This enum also serves as Cobalt's adapted representation of WA Web's
 * dedicated {@code WAWebGroupMemberLinkMode.MemberLinkMode} enum. WA Web
 * models member-link permission as a two-value enum
 * ({@code ALL_MEMBER_LINK}/{@code ADMIN_LINK}); Cobalt collapses this into the
 * broader {@code ChatPolicy} abstraction since the WA enum carries no semantics
 * beyond the "admin-only vs. everyone" toggle that {@code ChatPolicy} already
 * captures. The string conversions
 * {@link #ofMemberLinkModeMexType(String)} and
 * {@link #ofMemberLinkModeStanzaToken(String)} replace WA Web's
 * {@code getMemberLinkModeFromMexType}.
 *
 * @implNote WAWebGroupMemberLinkMode.MemberLinkMode: WA Web defines this as
 * {@code e = { ALL_MEMBER_LINK: "all_member_link", ADMIN_LINK: "admin_link" }}.
 * The two enum members correspond to {@link #ANYONE} and {@link #ADMINS}
 * respectively. The underlying string values are the lowercase stanza tokens
 * emitted on the wire in {@code <member_link_mode>} children of group
 * notification/metadata stanzas, distinct from the uppercase MEX-type tokens
 * {@code "ALL_MEMBER_LINK"}/{@code "ADMIN_LINK"} used by
 * {@code WAWebMexFetchGroupInfoJob} and friends.
 */
@ProtobufEnum
@WhatsAppWebModule(moduleName = "WAWebGroupMemberLinkMode")
public enum ChatPolicy {
    /**
     * Permits all group or community members (both regular participants and
     * administrators) to perform the action.
     *
     * @implNote WAWebGroupMemberLinkMode.MemberLinkMode.ALL_MEMBER_LINK:
     * mirrored string constant {@code "all_member_link"} in the
     * {@code e = { ALL_MEMBER_LINK: "all_member_link", ... }} table.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupMemberLinkMode",
            exports = "MemberLinkMode",
            adaptation = WhatsAppAdaptation.ADAPTED)
    ANYONE,

    /**
     * Restricts the action to group or community administrators only.
     *
     * @implNote WAWebGroupMemberLinkMode.MemberLinkMode.ADMIN_LINK:
     * mirrored string constant {@code "admin_link"} in the
     * {@code e = { ..., ADMIN_LINK: "admin_link" }} table.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupMemberLinkMode",
            exports = "MemberLinkMode",
            adaptation = WhatsAppAdaptation.ADAPTED)
    ADMINS;

    /**
     * Returns the {@code ChatPolicy} corresponding to a WhatsApp boolean toggle.
     *
     * <p>A value of {@code true} maps to {@link #ADMINS} (restricted), while
     * {@code false} maps to {@link #ANYONE} (open to all members).
     *
     * @param input the boolean toggle value from WhatsApp
     * @return {@link #ADMINS} if {@code input} is {@code true},
     *         {@link #ANYONE} otherwise
     */
    public static ChatPolicy of(boolean input) {
        return input ? ADMINS : ANYONE;
    }

    /**
     * Returns the {@code ChatPolicy} corresponding to a WhatsApp MEX-type
     * string as produced by {@code WAWebMexFetchGroupInfoJob} and related
     * MEX queries for the {@code member_link_mode} field.
     *
     * <p>The conversion follows WA Web's
     * {@code WAWebGroupMemberLinkMode.getMemberLinkModeFromMexType} exactly:
     * <ul>
     *   <li>{@code null} input → {@link #ADMINS} (default when the field is
     *       omitted from the server response).</li>
     *   <li>{@code "ALL_MEMBER_LINK"} → {@link #ANYONE}.</li>
     *   <li>{@code "ADMIN_LINK"} → {@link #ADMINS}.</li>
     *   <li>Any other value → {@link #ADMINS} (safe default for unknown
     *       server-side enum additions).</li>
     * </ul>
     *
     * @param mexType the MEX-type string from the server, or {@code null}
     * @return the corresponding {@code ChatPolicy}, never {@code null}
     *
     * @implNote WAWebGroupMemberLinkMode.getMemberLinkModeFromMexType:
     * {@code function l(t) { if (t == null) return e.ADMIN_LINK; switch (t) {
     * case "ALL_MEMBER_LINK": return e.ALL_MEMBER_LINK; case "ADMIN_LINK":
     * return e.ADMIN_LINK; default: return e.ADMIN_LINK } }}. Cobalt adapts
     * the return type from the dedicated {@code MemberLinkMode} enum to
     * {@code ChatPolicy}; {@code ALL_MEMBER_LINK} maps to {@link #ANYONE} and
     * {@code ADMIN_LINK} maps to {@link #ADMINS}.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupMemberLinkMode",
            exports = "getMemberLinkModeFromMexType",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static ChatPolicy ofMemberLinkModeMexType(String mexType) {
        if (mexType == null) {
            return ADMINS;
        }
        return switch (mexType) {
            case "ALL_MEMBER_LINK" -> ANYONE;
            case "ADMIN_LINK" -> ADMINS;
            default -> ADMINS;
        };
    }

    /**
     * Returns the {@code ChatPolicy} corresponding to a WhatsApp stanza-level
     * token as appears in the text content of {@code <member_link_mode>}
     * children of group notification stanzas.
     *
     * <p>This is the stanza-wire equivalent of
     * {@link #ofMemberLinkModeMexType(String)}; the two use different casing
     * (lowercase on the wire, uppercase in the MEX/GraphQL schema).
     * <ul>
     *   <li>{@code null} input → {@link #ADMINS} (consistent with the MEX
     *       helper's null handling, and with WA Web's stanza parser, which
     *       ignores absent children and leaves the previous value in place).</li>
     *   <li>{@code "all_member_link"} → {@link #ANYONE}.</li>
     *   <li>{@code "admin_link"} → {@link #ADMINS}.</li>
     *   <li>Any other value → {@link #ADMINS}.</li>
     * </ul>
     *
     * @param stanzaToken the lowercase token from a group stanza, or
     *                    {@code null}
     * @return the corresponding {@code ChatPolicy}, never {@code null}
     *
     * @implNote WAWebGroupMemberLinkMode.MemberLinkMode: the wire-level
     * lowercase counterparts of the enum values. WA Web
     * ({@code WAWebHandleGroupNotification.T}) compares stanza content against
     * {@code MemberLinkMode.ALL_MEMBER_LINK} / {@code MemberLinkMode.ADMIN_LINK}
     * strictly and leaves the metadata field untouched on unknown values; this
     * helper collapses that pattern into a single null/unknown → {@code ADMINS}
     * default for callers that need an unconditional mapping.
     */
    @WhatsAppWebExport(moduleName = "WAWebGroupMemberLinkMode",
            exports = "MemberLinkMode",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static ChatPolicy ofMemberLinkModeStanzaToken(String stanzaToken) {
        if (stanzaToken == null) {
            return ADMINS;
        }
        return switch (stanzaToken) {
            case "all_member_link" -> ANYONE;
            case "admin_link" -> ADMINS;
            default -> ADMINS;
        };
    }
}