package com.github.auties00.cobalt.calls2.signaling;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.stanza.Stanza;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents the acknowledgement of a {@link LinkJoinStanza}: the relay's reply admitting a join.
 *
 * <p>A link-join ack is delivered inside the host stanza layer's shared {@code <ack>} envelope, whose
 * body echoes a {@code <link_join>} stanza. It names the {@link #callId() call} the joined device entered,
 * the {@link #callCreator() call creator} it must address, and echoes the {@link #token() link token}
 * (the join handler rejects a reply whose token does not match the request). It carries the call's
 * {@link #groupInfo() membership roster} and {@link #voipSettings() tuning block}, an optional
 * {@link #event() event flag} and {@link #linkInfoValue() link-info block}, and, for a waiting-room link,
 * the lobby {@link #waitingRoomUsers() participant list}.
 *
 * <p>On the wire the acknowledged element is
 * {@code <link_join token="..." call-id="..." call-creator="..." event="0|1"><group_info .../>
 * <voip_settings .../>[<link_info .../>][<user .../>*]</link_join>}. The {@code <group_info>} roster is
 * given a typed projection through {@link GroupInfoStanza}; the {@code <voip_settings>} and
 * {@code <link_info>} blocks are forwarded as opaque {@link Stanza} trees because their typed parse is
 * owned by the configuration and call-link subsystems, the same split {@link GroupUpdateStanza} uses.
 *
 * <p>The ack carries no relay candidates. A joiner's relay endpoints reach the media plane through the
 * call's offer (whose sibling {@code <relay>} block, modeled by {@link RelayInfo}, is consumed by the
 * group-call update path) or a later {@code <group_update>} broadcast, never through this reply.
 *
 * @implNote This implementation models the {@code <link_join>} ack body parsed by
 * {@code deserialize_link_join_ack} (function index {@code 11681}, {@code stanzas/call_link.cc}) in the
 * wa-voip WASM module {@code ff-tScznZ8P} (message type {@code 34} / {@code 0x4b}). That function selects
 * one of two shapes on an internal flag; the full shape modeled here reads, in textual order: a required
 * {@code token} attribute (diagnostic {@code "LinkJoinAck: cannot find token attr"}, vaddr {@code 277648}),
 * a required {@code call-id} attribute ({@code "cannot find call-id"}, vaddr {@code 559341}), a required
 * {@code call-creator} attribute ({@code "LinkJoinAck: cannot find call-creator attr"}, vaddr
 * {@code 277481}), a required {@code group_info} child ({@code "LinkJoinAck group_info not present"} /
 * {@code "LinkJoinAck error converting group info"}, vaddr {@code 125276} / {@code 345334}), a required
 * {@code voip_settings} child ({@code "missing voip_settings"} / {@code "LinkJoinAck error parse voip
 * params"}, vaddr {@code 227137} / {@code 197740}), an optional {@code event} boolean attribute (vaddr
 * {@code 125189}), an optional {@code link_info} child whose parse failure is logged but non-fatal
 * ({@code "%s: cannot parse link_info"}, vaddr {@code 344034}; {@code fill_link_info}, function index
 * {@code 11635}, reads a within-block required {@code link_creator} and an optional {@code link_creator_pn}),
 * and the waiting-room participants copied through {@code handle_waiting_room} (function index {@code 11477})
 * with each {@code <user>} entry filled by function index {@code 11614} and decoded here by
 * {@link WaitingRoomUser#of(Stanza)} ({@code "%s: cannot parse waiting_room stanza in link_join ack"}, vaddr
 * {@code 410205}). The function references neither {@code fill_relay_info} (function index {@code 11630}),
 * the {@code <relay>} element, nor {@code auth_token}: a cross-reference sweep of {@code ff-tScznZ8P}
 * places every relay parser and the {@code auth_token} attribute exclusively in the offer and transport
 * deserializer cluster, never in this function. The relay candidates a joiner uses are therefore populated
 * from the offer's sibling {@code <relay>} (consumed by {@code handle_group_info_relays}, function index
 * {@code 10982}, a runtime update path, not a deserializer) or a later {@code <group_update>}, so this ack
 * carries no relay block. The {@code voip_settings} and {@code link_info} blocks are kept as raw nodes
 * because their typed parse is owned by the configuration ({@link com.github.auties00.cobalt.calls2.config.VoipSettings})
 * and call-link subsystems.
 *
 * @param token            the echoed call-link token; never {@code null}
 * @param callId           the call identifier the relay admitted the joined device into; never
 *                         {@code null}
 * @param callCreator      the call creator the joined device must address; never {@code null}
 * @param groupInfo        the membership roster snapshot the joined device reconciles against; never
 *                         {@code null}
 * @param voipSettings     the raw {@code <voip_settings>} tuning block, parsed by the configuration
 *                         subsystem; never {@code null}
 * @param event            whether the ack stamped the {@code event} flag
 * @param linkInfo         the raw {@code <link_info>} block carrying the link-creator metadata, or
 *                         {@code null} when the ack carried none
 * @param waitingRoomUsers the lobby participant list for a waiting-room link; never {@code null}, empty
 *                         when the ack carries no participants
 * @see Calls2SignalingType#LINK_JOIN_ACK
 * @see LinkJoinStanza
 * @see GroupInfoStanza
 * @see WaitingRoomUser
 * @see RelayInfo
 */
public record LinkJoinAck(String token, String callId, Jid callCreator, GroupInfoStanza groupInfo,
                          Stanza voipSettings, boolean event, Stanza linkInfo,
                          List<WaitingRoomUser> waitingRoomUsers) {
    /**
     * The wire attribute naming the echoed call-link token.
     */
    private static final String TOKEN_ATTRIBUTE = "token";

    /**
     * The wire element tag for the {@code <voip_settings>} tuning block.
     */
    private static final String VOIP_SETTINGS_ELEMENT = "voip_settings";

    /**
     * The wire attribute naming the event flag.
     */
    private static final String EVENT_ATTRIBUTE = "event";

    /**
     * The wire element tag for the {@code <link_info>} link-creator block.
     */
    private static final String LINK_INFO_ELEMENT = "link_info";

    /**
     * Validates the required record components and defensively copies the participant list.
     *
     * @throws NullPointerException if {@code token}, {@code callId}, {@code callCreator},
     *                              {@code groupInfo}, {@code voipSettings}, or {@code waitingRoomUsers}
     *                              is {@code null}
     */
    public LinkJoinAck {
        Objects.requireNonNull(token, "token cannot be null");
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(callCreator, "callCreator cannot be null");
        Objects.requireNonNull(groupInfo, "groupInfo cannot be null");
        Objects.requireNonNull(voipSettings, "voipSettings cannot be null");
        Objects.requireNonNull(waitingRoomUsers, "waitingRoomUsers cannot be null");
        waitingRoomUsers = List.copyOf(waitingRoomUsers);
    }

    /**
     * Decodes a {@code <link_join>} ack stanza into a {@link LinkJoinAck}.
     *
     * <p>The required {@code token}, {@code call-id}, and {@code call-creator} attributes and the
     * required {@code <group_info>} and {@code <voip_settings>} children are read first; an absent
     * required attribute or child raises a {@link NoSuchElementException} so an error reply with no
     * echoed body surfaces the missing field rather than yielding a half-filled record. The optional
     * {@code event} flag defaults to {@code false} when absent, the optional {@code <link_info>} block is
     * kept verbatim when present and {@code null} otherwise, and every nested {@code <user>} child is
     * decoded into a {@link WaitingRoomUser} forming the lobby participant list, empty when the ack
     * carries no participants.
     *
     * @param stanza the echoed {@code <link_join>} stanza from the {@code <ack>} body
     * @return the decoded link-join ack
     * @throws NullPointerException   if {@code stanza} is {@code null}
     * @throws NoSuchElementException if the required {@code token}, {@code call-id}, or
     *                                {@code call-creator} attribute, or the required {@code <group_info>}
     *                                or {@code <voip_settings>} child, is absent
     */
    public static LinkJoinAck of(Stanza stanza) {
        Objects.requireNonNull(stanza, "stanza cannot be null");
        var token = stanza.getRequiredAttributeAsString(TOKEN_ATTRIBUTE);
        var callId = stanza.getRequiredAttributeAsString(CallMessages.CALL_ID_ATTRIBUTE);
        var callCreator = stanza.getRequiredAttributeAsJid(CallMessages.CALL_CREATOR_ATTRIBUTE);
        var groupInfo = stanza.getChild(GroupInfoStanza.ELEMENT)
                .flatMap(GroupInfoStanza::of)
                .orElseThrow(() -> new NoSuchElementException("link_join ack is missing a required <group_info> roster"));
        var voipSettings = stanza.getChild(VOIP_SETTINGS_ELEMENT)
                .orElseThrow(() -> new NoSuchElementException("link_join ack is missing a required <voip_settings> block"));
        var event = "1".equals(stanza.getAttributeAsString(EVENT_ATTRIBUTE).orElse("0"));
        var linkInfo = stanza.getChild(LINK_INFO_ELEMENT).orElse(null);
        var waitingRoomUsers = stanza.streamChildren(WaitingRoomUser.ELEMENT)
                .map(WaitingRoomUser::of)
                .toList();
        return new LinkJoinAck(token, callId, callCreator, groupInfo, voipSettings, event, linkInfo, waitingRoomUsers);
    }

    /**
     * Returns the raw {@code <link_info>} block, if the ack carried one.
     *
     * @return an {@link Optional} holding the {@code <link_info>} stanza, or empty when absent
     */
    public Optional<Stanza> linkInfoValue() {
        return Optional.ofNullable(linkInfo);
    }
}
