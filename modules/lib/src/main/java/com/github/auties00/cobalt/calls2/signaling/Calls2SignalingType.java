package com.github.auties00.cobalt.calls2.signaling;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Enumerates the eighty-five signaling message types of the wa-voip {@code <call>} plane.
 *
 * <p>Every call action is exactly one child element inside a top-level {@code <call>} stanza, and the
 * wa-voip engine flattens each parsed action into a {@code wa_call_message} whose first word is a
 * numeric {@code message_type}. This enum re-derives that taxonomy: each constant binds the numeric
 * id the engine dispatches on ({@link #index()}), the delivery {@link #mechanism()} that selects
 * whether the action rides its own {@code <call>} child element, a shared {@code <ack>}/{@code <receipt>}
 * envelope, or no wire form at all, the lower-cased wire child tag that names the action inside the
 * {@code <call>} envelope when one exists ({@link #wireTag()}), and, where recovered, the fixed-header
 * byte length the inbound validator expects ({@link #fixedHeaderLength()}). The five blank slots in the
 * native table (ids 40, 41, 43, 44, 64) are not represented.
 *
 * <p>Dispatch is tag-keyed and id-keyed, never ordinal-keyed: the Java {@link Enum#ordinal()} of these
 * constants is deliberately meaningless for protocol purposes because the native ids contain gaps and
 * do not correspond to declaration order. Inbound classification of an action-bearing message keys on
 * the single child element tag through {@link #ofWireTag(String)}; id-based lookups (for example,
 * validating a flattened message against its own header) go through {@link #ofIndex(int)}. Both lookups
 * are constant-time.
 *
 * <p>Not every type names a {@code <call>} child element. The acknowledgement and receipt legs do not:
 * {@link Mechanism#ACK} legs ride a shared {@code <ack>} envelope and {@link Mechanism#RECEIPT} legs
 * ride a shared {@code <receipt>} envelope, both classified by the host stanza layer through the
 * envelope {@code type} attribute and the echoed request-stanza name rather than through a dedicated
 * child tag. For these legs {@link #wireTag()} is empty by design and they are absent from the
 * {@link #ofWireTag(String)} index; their delivery is recovered from the engine's
 * {@code handle_incoming_xmpp_ack} (fn11546) and {@code handle_incoming_xmpp_receipt} (fn11551)
 * paths and recorded by {@link #mechanism()}.
 *
 * <p>The wire tag of every action-bearing {@link Mechanism#CALL_CHILD} type is recovered from two static
 * dispatch paths: the engine's per-type {@code serialize_*} call sites, which load the lower-cased literal
 * as an {@code i32.const}, and the inbound tag-dispatch chains in {@code handleIncomingSignalingAck}
 * (fn844) and {@code handleIncomingSignalingMessage} (fn861), which compare the inbound child-element tag
 * against each literal through the string equality helper {@code fn12546}; the action tags are
 * cross-checked against the captured {@code <ack class="call" type="...">} legs, whose {@code type} echoes
 * the action's lower-cased child tag.
 *
 * <p>Three native ids occupy a message-type-table slot yet name no {@code <call>} child element, so they
 * carry an empty {@link #wireTag()} and the {@link Mechanism#INTERNAL} mechanism: legacy {@link #MUTE}
 * (12), {@link #WEB_CLIENT} (22), and {@link #CALL_RELAY} (35). The engine neither serializes nor
 * dispatches a wire child for any of them. {@link #MUTE} is superseded by {@link #MUTE_V2}: the only
 * addressable {@code mute} literal (data offset {@code 0x706d7}) is a tail-merged suffix of
 * {@code self_stream_unmute} that no function loads as a constant, and the inbound dispatch chain (fn861)
 * compares solely the {@code mute_v2} literal ({@code 0xc9247}), never {@code mute}. {@link #WEB_CLIENT}
 * and {@link #CALL_RELAY} have no lower-cased wire literal at all; their only strings are the CamelCase
 * diagnostic display names {@code WebClient} ({@code 0x1f226}) and {@code CallRelay} ({@code 0x715e}) held
 * in the display-name pointer table at {@code 0x1291ac} (entries 22 and 35) that the
 * {@code check_msg_header} diagnostic indexes, loaded by no function as a constant. Because the engine
 * never emits or dispatches a {@code <mute>}, {@code <web_client>}, or {@code <call_relay>} child, these
 * ids are absent from the {@link #ofWireTag(String)} index, so an inbound child of one of those names
 * resolves to no type and is dropped, exactly as the engine drops it.
 *
 * @implNote This implementation ports the {@code voip_signaling_message_type} table whose display-name
 * pointer array sits at WASM data offset {@code 0x1291ac} in module {@code ff-tScznZ8P} (the eighty-five
 * entries with the five documented gaps). The numeric ids {@code 1,2,3,4,5,6,9,0xd,0xf,0x12,0x13,0x21,
 * 0x4e} are confirmed from {@code make_and_send_*}/{@code handle_*} call sites in
 * {@code wacall/system/src/messages/*}; the remaining ids follow the table position. The per-type
 * {@link #fixedHeaderLength() fixed-header lengths} are recovered from the inbound validator
 * {@code check_msg_header} (fn11495) in {@code message_router.cc} (source {@code DAT 0xb887e}): it runs a
 * {@code br_table} on {@code (*msg) - 1} and, per case, compares {@code msg_len} against an
 * {@code i32.const} expected length with the diagnostic {@code "check_msg_header: invalid len %d for %s,
 * expected %d"} ({@code DAT 0xa3685}). Types with an explicit switch case carry their recovered length;
 * {@link #OFFER} takes {@code br_table} label 36 which skips the switch and keeps the entry-preset length
 * {@code 777400} ({@code 0xbdcb8}). Types whose id routes to the {@code br_table} default have no
 * validated fixed length (the validator sets {@code len = 0} and logs {@code "invalid len"}); they are
 * variable-length and {@link #fixedHeaderLength()} is empty for them. The acknowledgement and receipt
 * delivery split recorded by {@link #mechanism()} is recovered from {@code handle_incoming_xmpp_ack}
 * (fn11546, which reads the {@code <ack>} {@code type} attribute and calls {@code convert_xmpp_ack_to_msg})
 * and {@code handle_incoming_xmpp_receipt} (fn11551, {@code convert_xmpp_receipt_to_msg}). The captured
 * traffic confirms the envelope shape: an acknowledged action rides an {@code <ack class="call">} whose
 * {@code type} attribute echoes the action's own lower-cased child tag (seen as {@code offer},
 * {@code accept}, {@code preaccept}, {@code terminate}, {@code mute_v2}, {@code relaylatency},
 * {@code lobby}, and the rest across {@code re/calls2-spec/captures}), and a {@code <receipt>} leg is
 * correlated to its request by stanza id and carries no per-action {@code type}.
 */
public enum Calls2SignalingType {
    /**
     * Represents the sentinel the engine uses for the zeroth table slot when no message type matches.
     *
     * <p>This is the {@code None} entry at id {@code 0} of the native table, not a transmittable action;
     * it never names a wire child element nor rides an envelope, so its {@link #mechanism()} is
     * {@link Mechanism#NONE} and {@link #wireTag()} is empty.
     */
    NONE(0, Mechanism.NONE, null, -1),

    /**
     * Represents the initial offer that announces a one-to-one or group call to the peers.
     *
     * <p>The inbound validator expects a fixed-header length of {@code 777400} bytes.
     */
    OFFER(1, Mechanism.CALL_CHILD, "offer", 777400),

    /**
     * Represents the server-delivered receipt confirming an offer reached the peer.
     *
     * <p>This is a {@link Mechanism#RECEIPT} leg: it rides a shared {@code <receipt>} element and names
     * no dedicated {@code <call>} child, so {@link #wireTag()} is empty. The inbound validator expects a
     * fixed-header length of {@code 100} bytes.
     */
    OFFER_RECEIPT(2, Mechanism.RECEIPT, null, 100),

    /**
     * Represents the callee accepting the call.
     *
     * <p>The inbound validator expects a fixed-header length of {@code 271184} bytes.
     */
    ACCEPT(3, Mechanism.CALL_CHILD, "accept", 271184),

    /**
     * Represents the callee declining the call before answering.
     *
     * <p>The inbound validator expects a fixed-header length of {@code 240} bytes.
     */
    REJECT(4, Mechanism.CALL_CHILD, "reject", 240),

    /**
     * Represents either side ending the call, carrying a {@code reason} literal.
     *
     * <p>The inbound validator expects a fixed-header length of {@code 6528} bytes.
     */
    TERMINATE(5, Mechanism.CALL_CHILD, "terminate", 6528),

    /**
     * Represents a transport-plane exchange, carrying an inner transport sub-type.
     *
     * <p>The inner sub-type selects relay candidates, a candidate list, transport protocol, relay
     * latency, peer network health, or ICE/DTLS material; it is modeled separately by
     * {@link CallTransportSubType}. The inbound validator expects a fixed-header length of {@code 1112}
     * bytes.
     */
    TRANSPORT(6, Mechanism.CALL_CHILD, "transport", 1112),

    /**
     * Represents the acknowledgement that an offer this device sent was accepted by the server.
     *
     * <p>This is a {@link Mechanism#ACK} leg: it rides a shared {@code <ack>} element and names no
     * dedicated {@code <call>} child, so {@link #wireTag()} is empty. The inbound validator expects a
     * fixed-header length of {@code 1539272} bytes.
     */
    OFFER_ACK(7, Mechanism.ACK, null, 1539272),

    /**
     * Represents the negative acknowledgement that an offer this device sent was refused.
     *
     * <p>This is a {@link Mechanism#ACK} leg (a negative ack): it rides a shared {@code <ack>} element
     * and names no dedicated {@code <call>} child, so {@link #wireTag()} is empty. The inbound validator
     * expects a fixed-header length of {@code 1539272} bytes.
     */
    OFFER_NACK(8, Mechanism.ACK, null, 1539272),

    /**
     * Represents a relay-latency probe report.
     *
     * <p>The inbound validator expects a fixed-header length of {@code 1144} bytes.
     */
    RELAY_LATENCY(9, Mechanism.CALL_CHILD, "relaylatency", 1144),

    /**
     * Represents the acknowledgement of a relay-latency report.
     *
     * <p>This is a {@link Mechanism#RECEIPT} leg: it rides a shared {@code <receipt>} element and names
     * no dedicated {@code <call>} child, so {@link #wireTag()} is empty. The inbound validator expects a
     * fixed-header length of {@code 104} bytes.
     */
    RELAY_LATENCY_ACK(10, Mechanism.RECEIPT, null, 104),

    /**
     * Represents a call interruption notice, carrying a begin or end state.
     *
     * <p>The inbound validator expects a fixed-header length of {@code 108} bytes.
     */
    INTERRUPTION(11, Mechanism.CALL_CHILD, "interruption", 108),

    /**
     * Represents the legacy mute toggle id, superseded on the wire by {@link #MUTE_V2} and emitting no
     * {@code <call>} child in this build.
     *
     * <p>The native table reserves id {@code 12} for this type and the inbound validator carries an
     * explicit {@code check_msg_header} case expecting a fixed-header length of {@code 0} bytes, so the
     * engine recognizes the id, but it has no wire child: the module carries no {@code serialize_mute},
     * and the only addressable {@code mute} string (data offset {@code 0x706d7}) is a tail-merged suffix of
     * {@code self_stream_unmute} that no function loads as a constant. The inbound dispatch chain
     * {@code handleIncomingSignalingMessage} (fn861) compares solely the {@code mute_v2} literal
     * ({@code 0xc9247}), never {@code mute}. The engine therefore neither emits nor dispatches a legacy
     * {@code <mute>} child, so this id is {@link Mechanism#INTERNAL} with an empty {@link #wireTag()} and is
     * absent from the {@link #ofWireTag(String)} index; an inbound {@code <mute>} resolves to no type and is
     * dropped, exactly as the engine drops it.
     *
     * @implNote This implementation models this id as recognized-but-wire-less rather than carrying a
     * legacy {@code mute} tag: the {@code mute} string at {@code 0x706d7} is referenced by no
     * function (verified against the module's instruction stream), and a connected one-to-one and group
     * call observed only {@code <mute_v2>}, never {@code <mute>} ({@code re/calls2-spec/captures}). Routing
     * an inbound {@code <mute>} to a type would diverge from the engine, which never compares that literal.
     */
    MUTE(12, Mechanism.INTERNAL, null, 0),

    /**
     * Represents a pre-acceptance signal: the callee's device is alerting but the user has not
     * answered yet.
     *
     * <p>The inbound validator expects a fixed-header length of {@code 284} bytes.
     */
    PREACCEPT(13, Mechanism.CALL_CHILD, "preaccept", 284),

    /**
     * Represents the receipt confirming an accept was delivered.
     *
     * <p>This is a {@link Mechanism#RECEIPT} leg: it rides a shared {@code <receipt>} element and names
     * no dedicated {@code <call>} child, so {@link #wireTag()} is empty. The inbound validator expects a
     * fixed-header length of {@code 100} bytes.
     */
    ACCEPT_RECEIPT(14, Mechanism.RECEIPT, null, 100),

    /**
     * Represents a video-state change announcement during a call.
     *
     * <p>The inbound validator expects a fixed-header length of {@code 269696} bytes.
     */
    VIDEO_STATE(15, Mechanism.CALL_CHILD, "video", 269696),

    /**
     * Represents a generic in-call notification such as a battery-state notice.
     *
     * <p>The inbound validator expects a fixed-header length of {@code 104} bytes.
     */
    NOTIFY(16, Mechanism.CALL_CHILD, "notify", 104),

    /**
     * Represents a group-membership and configuration update during an in-progress group call.
     *
     * <p>The element bundles {@code voip_settings}, {@code relay}, {@code group_info}, {@code bot_info},
     * {@code extension_info}, {@code link_info}, and AV-upgrade children. The inbound validator expects a
     * fixed-header length of {@code 497848} bytes.
     */
    GROUP_UPDATE(17, Mechanism.CALL_CHILD, "group_update", 497848),

    /**
     * Represents a group-call key re-exchange, the one signaling message with a protobuf payload.
     *
     * <p>The inbound validator expects a fixed-header length of {@code 548} bytes.
     */
    REKEY(18, Mechanism.CALL_CHILD, "enc_rekey", 548),

    /**
     * Represents a per-participant peer-state update.
     *
     * <p>The inbound validator expects a fixed-header length of {@code 1896} bytes.
     */
    PEER_STATE(19, Mechanism.CALL_CHILD, "peer_state", 1896),

    /**
     * Represents the acknowledgement of a {@link #VIDEO_STATE} change.
     *
     * <p>This is a {@link Mechanism#RECEIPT} leg: it rides a shared {@code <receipt>} element and names
     * no dedicated {@code <call>} child, so {@link #wireTag()} is empty. The inbound validator expects a
     * fixed-header length of {@code 269592} bytes.
     */
    VIDEO_STATE_ACK(20, Mechanism.RECEIPT, null, 269592),

    /**
     * Represents a flow-control request carrying target bitrate, width, and frame rate.
     *
     * <p>The inbound validator expects a fixed-header length of {@code 116} bytes.
     */
    FLOW_CONTROL(21, Mechanism.CALL_CHILD, "flowcontrol", 116),

    /**
     * Represents the {@code WebClient} message-type id, an internal slot the web/shared core never emits as
     * a {@code <call>} child.
     *
     * <p>The native message-type table assigns this id its own slot and the inbound validator carries an
     * explicit {@code check_msg_header} case for it (expected fixed-header length {@code 0} bytes), so the
     * engine recognizes the id, but the module exposes no wire form for it: the only string is the CamelCase
     * display name {@code WebClient} at data offset {@code 0x1f226}, with no lower-cased {@code web_client}
     * wire literal, no {@code serialize_*}, and no inbound dispatch-chain reference. The web/shared-core path
     * neither emits nor dispatches a {@code <web_client>} child, so this id is {@link Mechanism#INTERNAL}
     * with an empty {@link #wireTag()}, exists only to map the native id, and is absent from the
     * {@link #ofWireTag(String)} index.
     *
     * @implNote This implementation does not recover a wire tag for this id because none exists in this
     * build: the {@code WebClient} string at {@code 0x1f226} is reachable only through the display-name
     * pointer array at {@code 0x1291ac} (entry 22) that the {@code check_msg_header} diagnostic indexes, no
     * function loads its address as a direct constant, and the module carries no lower-cased
     * {@code web_client} serializer, deserializer, handler, or sender. No {@code <web_client>} child appeared
     * across the {@code platform="web"} {@code <call>} stanzas captured in {@code re/calls2-spec/captures}
     * (one-to-one, group, and business calls). Not emitting this child is faithful to the web/shared core.
     */
    WEB_CLIENT(22, Mechanism.INTERNAL, null, 0),

    /**
     * Represents the acknowledgement of an accept on the group-call path.
     *
     * <p>This is a {@link Mechanism#ACK} leg: it rides a shared {@code <ack>} element and names no
     * dedicated {@code <call>} child, so {@link #wireTag()} is empty. The inbound validator expects a
     * fixed-header length of {@code 313232} bytes.
     */
    ACCEPT_ACK(23, Mechanism.ACK, null, 313232),

    /**
     * Represents a group-call lobby control message.
     *
     * <p>The child tag {@code lobby} is confirmed from {@code serialize_lobby} (fn11691); literal at data
     * offset {@code 0x687b}. The inbound validator expects a fixed-header length of {@code 104} bytes.
     */
    LOBBY(24, Mechanism.CALL_CHILD, "lobby", 104),

    /**
     * Represents the acknowledgement of a {@link #LOBBY} message.
     *
     * <p>This is a {@link Mechanism#RECEIPT} leg: it rides a shared {@code <receipt>} element and names
     * no dedicated {@code <call>} child, so {@link #wireTag()} is empty. The inbound validator expects a
     * fixed-header length of {@code 228272} bytes.
     */
    LOBBY_ACK(25, Mechanism.RECEIPT, null, 228272),

    /**
     * Represents the current mute toggle, requiring exactly one of a request-state or a mute-state.
     *
     * <p>The inbound validator expects a fixed-header length of {@code 104} bytes.
     */
    MUTE_V2(26, Mechanism.CALL_CHILD, "mute_v2", 104),

    /**
     * Represents a call-link creation request.
     *
     * <p>The inbound validator expects a fixed-header length of {@code 184} bytes.
     */
    LINK_CREATE(27, Mechanism.CALL_CHILD, "link_create", 184),

    /**
     * Represents the acknowledgement of a {@link #LINK_CREATE} request.
     *
     * <p>This is a {@link Mechanism#ACK} leg: it rides a shared {@code <ack>} element whose body echoes
     * the {@code link_create} request stanza and names no dedicated {@code <call>} child, so
     * {@link #wireTag()} is empty. The inbound validator expects a fixed-header length of {@code 132}
     * bytes.
     */
    LINK_CREATE_ACK(28, Mechanism.ACK, null, 132),

    /**
     * Represents a group-call heartbeat keepalive.
     *
     * <p>The child tag {@code heartbeat} is confirmed from {@code serialize_heartbeat}; literal at data
     * offset {@code 0x25848}. The inbound validator has no fixed-header case for this type (its id routes
     * to the {@code br_table} default), so {@link #fixedHeaderLength()} is empty and the message is
     * variable-length.
     */
    HEARTBEAT(29, Mechanism.CALL_CHILD, "heartbeat", -1),

    /**
     * Represents the acknowledgement of a {@link #HEARTBEAT}.
     *
     * <p>This is a {@link Mechanism#RECEIPT} leg: it rides a shared {@code <receipt>} element and names
     * no dedicated {@code <call>} child, so {@link #wireTag()} is empty. The inbound validator expects a
     * fixed-header length of {@code 104} bytes.
     */
    HEARTBEAT_ACK(30, Mechanism.RECEIPT, null, 104),

    /**
     * Represents a call-link query (preview or edit lookup).
     *
     * <p>The inbound validator expects a fixed-header length of {@code 132} bytes.
     */
    LINK_QUERY(31, Mechanism.CALL_CHILD, "link_query", 132),

    /**
     * Represents the acknowledgement of a {@link #LINK_QUERY}.
     *
     * <p>This is a {@link Mechanism#ACK} leg: it rides a shared {@code <ack>} element whose body echoes
     * the {@code link_query} request stanza and names no dedicated {@code <call>} child, so
     * {@link #wireTag()} is empty. The inbound validator expects a fixed-header length of {@code 43840}
     * bytes.
     */
    LINK_QUERY_ACK(32, Mechanism.ACK, null, 43840),

    /**
     * Represents a call-link join request.
     *
     * <p>The inbound validator expects a fixed-header length of {@code 312} bytes.
     */
    LINK_JOIN(33, Mechanism.CALL_CHILD, "link_join", 312),

    /**
     * Represents the acknowledgement of a {@link #LINK_JOIN}, supplying the call creator and relay
     * token on success.
     *
     * <p>This is a {@link Mechanism#ACK} leg: it rides a shared {@code <ack>} element whose body echoes
     * the join stanza and names no dedicated {@code <call>} child, so {@link #wireTag()} is empty. The
     * inbound validator expects a fixed-header length of {@code 531808} bytes.
     */
    LINK_JOIN_ACK(34, Mechanism.ACK, null, 531808),

    /**
     * Represents the {@code CallRelay} message-type id, a transport-plane relay-list construct the
     * web/shared core never emits as a {@code <call>} child.
     *
     * <p>This id names the transport plane's relay bookkeeping, not a top-level {@code <call>} child message:
     * the only display string is the CamelCase {@code CallRelay} at data offset {@code 0x715e}, and every
     * lower-cased {@code call_relay} string is a transport-internal identifier
     * ({@code call_relay_create_t}, {@code call_relay_server(s)}, {@code call_relay_bind_status},
     * {@code send_call_relay_ip_to_peer}, {@code wa_transport_get/set_peer_call_relay_ip}) from
     * {@code transport/call_relay.cc}, whose {@code wa_call_add_or_update_relay_item} maintains the in-memory
     * relay candidate list. The relay endpoints, tokens, and keys this id tracks reach the wire inside the
     * {@code <relay>} block of the {@link #TRANSPORT transport} message and the {@code group_info} of a
     * {@link #GROUP_UPDATE group_update}, both modeled by {@link RelayInfo}; there is no standalone
     * {@code <call_relay>} child element. The inbound validator has no fixed-header case (its id routes to
     * the {@code br_table} default), so {@link #fixedHeaderLength()} is empty. This id is therefore
     * {@link Mechanism#INTERNAL} with an empty {@link #wireTag()} and is absent from the
     * {@link #ofWireTag(String)} index.
     *
     * @implNote This implementation does not recover a {@code <call>}-child wire tag for this id because none
     * exists: the {@code CallRelay} string at {@code 0x715e} is reachable only through the display-name
     * pointer array at {@code 0x1291ac} (entry 35) that the {@code check_msg_header} diagnostic indexes, no
     * function loads its address as a direct constant, and the module carries no {@code serialize_call_relay}
     * / {@code make_and_send_call_relay} / {@code handle_call_relay}; the lower-cased {@code call_relay}
     * symbols belong to {@code transport/call_relay.cc} (fn11778) and {@code core/group_call_update.cc}
     * (fn10982, {@code handle_group_info_relays}), which manage the in-memory relay list rather than a
     * signaling child. {@code fill_relay_info} treats the relay block as the wire carrier, and a connected
     * group/SFU call produced no {@code <call_relay>} child ({@code re/calls2-spec/captures/group-sfu.json};
     * zero {@code call_relay} children across the capture set). Not emitting this child is faithful.
     */
    CALL_RELAY(35, Mechanism.INTERNAL, null, -1),

    /**
     * Represents an administrator request to remove a participant from a group call.
     *
     * <p>The child tag {@code remove_user} is confirmed from {@code serialize_remove_user} (fn11696);
     * literal at data offset {@code 0x48d60}, attribute {@code action} at {@code 0x56cc3}. The inbound
     * validator has no fixed-header case for this type (its id routes to the {@code br_table} default),
     * so {@link #fixedHeaderLength()} is empty and the message is variable-length.
     */
    REMOVE_USER(36, Mechanism.CALL_CHILD, "remove_user", -1),

    /**
     * Represents the acknowledgement of a {@link #REMOVE_USER} request.
     *
     * <p>This is a {@link Mechanism#RECEIPT} leg: it rides a shared {@code <receipt>} element and names
     * no dedicated {@code <call>} child, so {@link #wireTag()} is empty. The inbound validator expects a
     * fixed-header length of {@code 108} bytes.
     */
    REMOVE_USER_ACK(37, Mechanism.RECEIPT, null, 108),

    /**
     * Represents a screen-share state change, carrying a state and a version.
     *
     * <p>The inbound validator expects a fixed-header length of {@code 108} bytes.
     */
    SCREEN_SHARE(38, Mechanism.CALL_CHILD, "screen_share", 108),

    /**
     * Represents the acknowledgement of a {@link #SCREEN_SHARE} change.
     *
     * <p>This is a {@link Mechanism#RECEIPT} leg: it rides a shared {@code <receipt>} element and names
     * no dedicated {@code <call>} child, so {@link #wireTag()} is empty. The inbound validator expects a
     * fixed-header length of {@code 108} bytes.
     */
    SCREEN_SHARE_ACK(39, Mechanism.RECEIPT, null, 108),

    /**
     * Represents a DTMF tone, carrying a single tone child.
     *
     * <p>The inbound validator has no fixed-header case for this type (its id routes to the
     * {@code br_table} default), so {@link #fixedHeaderLength()} is empty and the message is
     * variable-length.
     */
    DTMF_TONE(42, Mechanism.CALL_CHILD, "dtmf", -1),

    /**
     * Represents the start of a broadcast or business call.
     *
     * <p>The child tag {@code bcall_start} (data offset {@code 0x193f6}) is confirmed from the inbound
     * acknowledgement dispatch chain in {@code handleIncomingSignalingAck} (fn844), which compares the
     * echoed request-stanza tag against this literal in the same sequence as the confirmed {@code offer},
     * {@code accept}, {@code relaylatency}, and {@code link_*} child tags. The inbound validator has no
     * fixed-header case (its id routes to the {@code br_table} default), so {@link #fixedHeaderLength()}
     * is empty and the message is variable-length.
     */
    BCALL_START(45, Mechanism.CALL_CHILD, "bcall_start", -1),

    /**
     * Represents the acknowledgement of a {@link #BCALL_START}.
     *
     * <p>This is a {@link Mechanism#ACK} leg: it rides a shared {@code <ack>} element whose body echoes
     * the {@code bcall_start} request stanza, so {@link #wireTag()} is empty. The {@code <ack>} carriage is
     * confirmed because {@code handleIncomingSignalingAck} (fn844), the inbound {@code <ack>} dispatcher,
     * classifies the echoed stanza by comparing it against the {@code bcall_start} literal in the same chain
     * as the other acknowledged request tags.
     */
    BCALL_START_ACK(46, Mechanism.ACK, null, -1),

    /**
     * Represents a join request on a broadcast or business call.
     *
     * <p>The child tag {@code bcall_join} (data offset {@code 0x58d0a}) is confirmed from the inbound
     * acknowledgement dispatch chain in {@code handleIncomingSignalingAck} (fn844) alongside
     * {@link #BCALL_START}. The inbound validator has no fixed-header case (its id routes to the
     * {@code br_table} default), so {@link #fixedHeaderLength()} is empty and the message is
     * variable-length.
     */
    BCALL_JOIN(47, Mechanism.CALL_CHILD, "bcall_join", -1),

    /**
     * Represents the acknowledgement of a {@link #BCALL_JOIN}.
     *
     * <p>This is a {@link Mechanism#ACK} leg: it rides a shared {@code <ack>} element whose body echoes
     * the {@code bcall_join} request stanza, so {@link #wireTag()} is empty. The {@code <ack>} carriage is
     * confirmed because {@code handleIncomingSignalingAck} (fn844) classifies the echoed stanza against the
     * {@code bcall_join} literal in the inbound dispatch chain.
     */
    BCALL_JOIN_ACK(48, Mechanism.ACK, null, -1),

    /**
     * Represents a leave request on a broadcast or business call.
     *
     * <p>The child tag {@code bcall_leave} (data offset {@code 0x6fc3f}) is confirmed from the inbound
     * acknowledgement dispatch chain in {@code handleIncomingSignalingAck} (fn844) alongside
     * {@link #BCALL_START}. The inbound validator has no fixed-header case (its id routes to the
     * {@code br_table} default), so {@link #fixedHeaderLength()} is empty and the message is
     * variable-length.
     */
    BCALL_LEAVE(49, Mechanism.CALL_CHILD, "bcall_leave", -1),

    /**
     * Represents the acknowledgement of a {@link #BCALL_LEAVE}.
     *
     * <p>This is a {@link Mechanism#ACK} leg: it rides a shared {@code <ack>} element whose body echoes
     * the {@code bcall_leave} request stanza, so {@link #wireTag()} is empty. The {@code <ack>} carriage is
     * confirmed because {@code handleIncomingSignalingAck} (fn844) classifies the echoed stanza against the
     * {@code bcall_leave} literal in the inbound dispatch chain.
     */
    BCALL_LEAVE_ACK(50, Mechanism.ACK, null, -1),

    /**
     * Represents an update on a broadcast or business call.
     *
     * <p>The child tag {@code bcall_update} (data offset {@code 0x75051}) is confirmed from the inbound
     * message dispatch chain in {@code handleIncomingSignalingMessage} (fn861), which compares the inbound
     * child-element tag against this literal in the same sequence as the confirmed {@code enc_rekey},
     * {@code mute_v2}, {@code screen_share}, and {@code flowcontrol} child tags. The inbound validator has
     * no fixed-header case (its id routes to the {@code br_table} default), so {@link #fixedHeaderLength()}
     * is empty and the message is variable-length.
     */
    BCALL_UPDATE(51, Mechanism.CALL_CHILD, "bcall_update", -1),

    /**
     * Represents the end of a broadcast or business call.
     *
     * <p>The child tag {@code bcall_end} (data offset {@code 0x82b78}) is confirmed from both inbound
     * dispatch chains: {@code handleIncomingSignalingAck} (fn844) and {@code handleIncomingSignalingMessage}
     * (fn861) each compare the inbound child-element tag against this literal alongside the other confirmed
     * action tags. The inbound validator has no fixed-header case (its id routes to the {@code br_table}
     * default), so {@link #fixedHeaderLength()} is empty and the message is variable-length.
     */
    BCALL_END(52, Mechanism.CALL_CHILD, "bcall_end", -1),

    /**
     * Represents the acknowledgement of a {@link #BCALL_END}.
     *
     * <p>This is a {@link Mechanism#ACK} leg: it rides a shared {@code <ack>} element whose body echoes
     * the {@code bcall_end} request stanza, so {@link #wireTag()} is empty. The {@code <ack>} carriage is
     * confirmed because {@code handleIncomingSignalingAck} (fn844) classifies the echoed stanza against the
     * {@code bcall_end} literal in the inbound dispatch chain.
     */
    BCALL_END_ACK(53, Mechanism.ACK, null, -1),

    /**
     * Represents a notification on a broadcast or business call.
     *
     * <p>The child tag {@code bcall_notify} (data offset {@code 0x5c33}) is confirmed from the inbound
     * message dispatch chain in {@code handleIncomingSignalingMessage} (fn861) alongside
     * {@link #BCALL_UPDATE}. The inbound validator has no fixed-header case (its id routes to the
     * {@code br_table} default), so {@link #fixedHeaderLength()} is empty and the message is
     * variable-length.
     */
    BCALL_NOTIFY(54, Mechanism.CALL_CHILD, "bcall_notify", -1),

    /**
     * Represents a call-link edit request.
     *
     * <p>The inbound validator has no fixed-header case for this type (its id routes to the
     * {@code br_table} default), so {@link #fixedHeaderLength()} is empty and the message is
     * variable-length.
     */
    LINK_EDIT(55, Mechanism.CALL_CHILD, "link_edit", -1),

    /**
     * Represents the acknowledgement of a {@link #LINK_EDIT}.
     *
     * <p>This is a {@link Mechanism#ACK} leg: it rides a shared {@code <ack>} element whose body echoes
     * the {@code link_edit} request stanza and names no dedicated {@code <call>} child, so
     * {@link #wireTag()} is empty. The inbound validator expects a fixed-header length of {@code 128}
     * bytes.
     */
    LINK_EDIT_ACK(56, Mechanism.ACK, null, 128),

    /**
     * Represents a scheduled group-call reminder.
     *
     * <p>The child tag {@code group_call_reminder} is confirmed from
     * {@code deserialize_group_call_reminder} (fn11703); literal at data offset {@code 0x4c1ec}. The
     * inbound validator expects a fixed-header length of {@code 271912} bytes.
     */
    GROUP_CALL_REMINDER(57, Mechanism.CALL_CHILD, "group_call_reminder", 271912),

    /**
     * Represents a connection-statistics report.
     *
     * <p>The child tag {@code connect_stat} is confirmed from {@code serialize_connect_stat}; literal at
     * data offset {@code 0x24cc4}. The inbound validator has no fixed-header case for this type (its id
     * routes to the {@code br_table} default), so {@link #fixedHeaderLength()} is empty and the message
     * is variable-length.
     */
    CONNECT_STAT(58, Mechanism.CALL_CHILD, "connect_stat", -1),

    /**
     * Represents the acknowledgement of a {@link #PREACCEPT}.
     *
     * <p>This is a {@link Mechanism#ACK} leg: it rides a shared {@code <ack>} element whose body echoes
     * the {@code preaccept} request stanza, so {@link #wireTag()} is empty. A connected group call observed
     * the acknowledgement as {@code <ack from="...@call" class="call" type="preaccept" id="..."/>}
     * (the synchronous return to the sent {@code <preaccept>}), confirming the {@code <ack>} carriage over
     * the {@code <receipt>} alternative.
     *
     * @implNote This implementation pins the {@code <ack>} carriage from the live capture
     * {@code re/calls2-spec/captures/group-stanzas-peer.jsonl} (and the matching primary capture), where
     * every {@code <preaccept>} sent inside a {@code <call>} stanza was acknowledged by an
     * {@code <ack class="call" type="preaccept">} keyed to the request id; no {@code <receipt>} carried a
     * preaccept acknowledgement.
     */
    PREACCEPT_ACK(59, Mechanism.ACK, null, -1),

    /**
     * Represents a generic user action on a call.
     *
     * <p>The child tag {@code user_action} is confirmed from {@code serialize_user_action} /
     * {@code deserialize_user_action} (fn11719/11720); literal at data offset {@code 0x56bbe}, attributes
     * {@code action} ({@code 0x56cc3}), {@code attribution} ({@code 0x557fc}), {@code wearable}
     * ({@code 0x7b079}). The inbound validator expects a fixed-header length of {@code 104} bytes.
     */
    USER_ACTION(60, Mechanism.CALL_CHILD, "user_action", 104),

    /**
     * Represents a bot reconfiguration request, carrying a request id.
     *
     * <p>The inbound validator expects a fixed-header length of {@code 112} bytes.
     */
    RECONFIGURE_BOT(61, Mechanism.CALL_CHILD, "reconfigure_bot", 112),

    /**
     * Represents a call-duration report.
     *
     * <p>The child tag {@code duration} is confirmed from {@code serialize_duration} (fn11723); literal
     * at data offset {@code 0x5707b}. The inbound validator expects a fixed-header length of {@code 240}
     * bytes.
     */
    DURATION(62, Mechanism.CALL_CHILD, "duration", 240),

    /**
     * Represents a group-call duration report.
     *
     * <p>The child tag {@code group_call_duration} is confirmed from {@code serialize_group_call_duration}
     * (fn11724); literal at data offset {@code 0x57551}. The inbound validator expects a fixed-header
     * length of {@code 236} bytes.
     */
    GROUP_CALL_DURATION(63, Mechanism.CALL_CHILD, "group_call_duration", 236),

    /**
     * Represents a readiness signal.
     *
     * <p>The child tag {@code ready} is confirmed from the {@code Ready} msgtype-65 serializer; literal at
     * data offset {@code 0x61e0}. The inbound validator expects a fixed-header length of {@code 104}
     * bytes.
     */
    READY(65, Mechanism.CALL_CHILD, "ready", 104),

    /**
     * Represents a relay-information update.
     *
     * <p>The child tag {@code relay_info_update} (data offset {@code 0x74f6f}) is confirmed as a top-level
     * {@code <call>} child from the inbound message dispatch chain in {@code handleIncomingSignalingMessage}
     * (fn861), which compares the inbound child-element tag against this literal in the same sequence as the
     * confirmed {@code transport}, {@code group_update}, {@code enc_rekey}, and {@code mute_v2} child tags,
     * routing it to {@code wa_transport_p2p_handle_relay_info_update}. It is therefore a standalone child
     * rather than a sub-element of {@code <transport>} or {@code <group_update>}. The inbound validator
     * expects a fixed-header length of {@code 9792} bytes.
     */
    RELAY_INFO_UPDATE(66, Mechanism.CALL_CHILD, "relay_info_update", 9792),

    /**
     * Represents a request to leave the waiting room.
     *
     * <p>The inbound validator has no fixed-header case for this type (its id routes to the
     * {@code br_table} default), so {@link #fixedHeaderLength()} is empty and the message is
     * variable-length.
     */
    WAITING_ROOM_LEAVE(67, Mechanism.CALL_CHILD, "waiting_room_leave", -1),

    /**
     * Represents the acknowledgement of a {@link #WAITING_ROOM_LEAVE}.
     *
     * <p>This is a {@link Mechanism#RECEIPT} leg: it rides a shared {@code <receipt>} element and names
     * no dedicated {@code <call>} child, so {@link #wireTag()} is empty. The inbound validator expects a
     * fixed-header length of {@code 104} bytes.
     */
    WAITING_ROOM_LEAVE_ACK(68, Mechanism.RECEIPT, null, 104),

    /**
     * Represents a request to toggle the waiting room on or off.
     *
     * <p>The inbound validator has no fixed-header case for this type (its id routes to the
     * {@code br_table} default), so {@link #fixedHeaderLength()} is empty and the message is
     * variable-length.
     */
    WAITING_ROOM_TOGGLE(69, Mechanism.CALL_CHILD, "waiting_room_toggle", -1),

    /**
     * Represents the acknowledgement of a {@link #WAITING_ROOM_TOGGLE}.
     *
     * <p>This is a {@link Mechanism#RECEIPT} leg: it rides a shared {@code <receipt>} element and names
     * no dedicated {@code <call>} child, so {@link #wireTag()} is empty. The inbound validator expects a
     * fixed-header length of {@code 128} bytes.
     */
    WAITING_ROOM_TOGGLE_ACK(70, Mechanism.RECEIPT, null, 128),

    /**
     * Represents an administrator request to admit a participant from the waiting room.
     *
     * <p>The inbound validator has no fixed-header case for this type (its id routes to the
     * {@code br_table} default), so {@link #fixedHeaderLength()} is empty and the message is
     * variable-length.
     */
    WAITING_ROOM_ADMIT(71, Mechanism.CALL_CHILD, "waiting_room_admit", -1),

    /**
     * Represents the acknowledgement of a {@link #WAITING_ROOM_ADMIT}.
     *
     * <p>This is a {@link Mechanism#RECEIPT} leg: it rides a shared {@code <receipt>} element and names
     * no dedicated {@code <call>} child, so {@link #wireTag()} is empty. The inbound validator expects a
     * fixed-header length of {@code 43628} bytes.
     */
    WAITING_ROOM_ADMIT_ACK(72, Mechanism.RECEIPT, null, 43628),

    /**
     * Represents an administrator request to deny a participant from the waiting room.
     *
     * <p>The inbound validator has no fixed-header case for this type (its id routes to the
     * {@code br_table} default), so {@link #fixedHeaderLength()} is empty and the message is
     * variable-length.
     */
    WAITING_ROOM_DENY(73, Mechanism.CALL_CHILD, "waiting_room_deny", -1),

    /**
     * Represents the acknowledgement of a {@link #WAITING_ROOM_DENY}.
     *
     * <p>This is a {@link Mechanism#RECEIPT} leg: it rides a shared {@code <receipt>} element and names
     * no dedicated {@code <call>} child, so {@link #wireTag()} is empty. The inbound validator expects a
     * fixed-header length of {@code 43628} bytes.
     */
    WAITING_ROOM_DENY_ACK(74, Mechanism.RECEIPT, null, 43628),

    /**
     * Represents a waiting-room state update.
     *
     * <p>The inbound validator expects a fixed-header length of {@code 43728} bytes.
     */
    WAITING_ROOM_UPDATE(75, Mechanism.CALL_CHILD, "waiting_room_update", 43728),

    /**
     * Represents a participant removal notice.
     *
     * <p>The child tag {@code remove} is confirmed from {@code serialize_remove} (fn11699); literal at
     * data offset {@code 0x6f50e}. The inbound validator expects a fixed-header length of {@code 356}
     * bytes.
     */
    REMOVE(76, Mechanism.CALL_CHILD, "remove", 356),

    /**
     * Represents the acknowledgement of a {@link #REMOVE}.
     *
     * <p>This is a {@link Mechanism#RECEIPT} leg: it rides a shared {@code <receipt>} element and names
     * no dedicated {@code <call>} child, so {@link #wireTag()} is empty. The inbound validator expects a
     * fixed-header length of {@code 1120} bytes.
     */
    REMOVE_ACK(77, Mechanism.RECEIPT, null, 1120),

    /**
     * Represents the fanout cancellation of an outstanding offer to a set of participants.
     *
     * <p>The inbound validator expects a fixed-header length of {@code 356} bytes.
     */
    CANCEL_OFFER(78, Mechanism.CALL_CHILD, "cancel_offer", 356),

    /**
     * Represents a request to add a call extension.
     *
     * <p>The element tag is {@code extension}; this is the canonical owner of that element for inbound
     * {@link #ofWireTag(String)} dispatch. The inbound validator expects a fixed-header length of
     * {@code 488936} bytes.
     */
    ADD_EXTENSION(79, Mechanism.CALL_CHILD, "extension", 488936),

    /**
     * Represents the acknowledgement of an {@link #ADD_EXTENSION}.
     *
     * <p>This is a {@link Mechanism#ACK} leg: it rides a shared {@code <ack>} element and names no
     * dedicated {@code <call>} child, so {@link #wireTag()} is empty. The inbound validator expects a
     * fixed-header length of {@code 228272} bytes.
     */
    ADD_EXTENSION_ACK(80, Mechanism.ACK, null, 228272),

    /**
     * Represents a request to remove a call extension.
     *
     * <p>This type reuses the {@code extension} element rather than a dedicated {@code remove_extension}
     * tag: {@code serialize_remove_extension} (fn11686) emits
     * {@code <destination><extension extension_id=.../></destination>} where the element tag is
     * {@code extension} (data offset {@code 0x58b07}), the same element {@link #ADD_EXTENSION} emits. The
     * two are distinguished by message id and by the absent session and key children, not by element tag,
     * so {@link #wireTag()} is {@code extension} but {@link #ADD_EXTENSION} is the canonical owner of that
     * element in the inbound {@link #ofWireTag(String)} index. The inbound validator expects a
     * fixed-header length of {@code 104} bytes.
     */
    REMOVE_EXTENSION(81, Mechanism.CALL_CHILD, "extension", 104),

    /**
     * Represents the acknowledgement of a {@link #REMOVE_EXTENSION}.
     *
     * <p>This is a {@link Mechanism#RECEIPT} leg: it rides a shared {@code <receipt>} element and names
     * no dedicated {@code <call>} child, so {@link #wireTag()} is empty. The inbound validator expects a
     * fixed-header length of {@code 104} bytes.
     */
    REMOVE_EXTENSION_ACK(82, Mechanism.RECEIPT, null, 104),

    /**
     * Represents acceptance of the group-call terms of service.
     *
     * <p>The child tag {@code group_call_tos_accepted} is confirmed from
     * {@code serialize_group_call_tos_accepted} (fn11704); literal at data offset {@code 0x8a727}. The
     * inbound validator expects a fixed-header length of {@code 104} bytes.
     */
    GROUP_CALL_TOS_ACCEPTED(83, Mechanism.CALL_CHILD, "group_call_tos_accepted", 104),

    /**
     * Represents the acknowledgement of {@link #GROUP_CALL_TOS_ACCEPTED}.
     *
     * <p>This is a {@link Mechanism#ACK} leg: it rides a shared {@code <ack>} element and names no
     * dedicated {@code <call>} child, so {@link #wireTag()} is empty. The inbound validator expects a
     * fixed-header length of {@code 104} bytes.
     */
    GROUP_CALL_TOS_ACCEPTED_ACK(84, Mechanism.ACK, null, 104);

    /**
     * Classifies how a signaling type is delivered on the wire.
     *
     * <p>The wa-voip engine carries action-bearing messages as a dedicated child element of the
     * top-level {@code <call>} stanza, but acknowledgement and receipt legs reuse the host stanza
     * layer's shared {@code <ack>} and {@code <receipt>} envelopes instead, and a few message-type ids
     * occupy a table slot without any wire form at all. This split, recovered from the engine's
     * {@code handle_incoming_xmpp_ack} (fn11546) and {@code handle_incoming_xmpp_receipt} (fn11551)
     * paths and the per-type {@code serialize_*} call sites, decides whether a type has a
     * {@link Calls2SignalingType#wireTag() wire tag} and whether it participates in
     * {@link Calls2SignalingType#ofWireTag(String)} child-tag dispatch.
     */
    public enum Mechanism {
        /**
         * Marks the non-transmittable {@link Calls2SignalingType#NONE} sentinel, which is neither an
         * action-bearing child nor an envelope leg.
         */
        NONE,

        /**
         * Marks an action-bearing type that is carried as its own child element inside the {@code <call>}
         * stanza.
         *
         * <p>These are the only types with a {@link Calls2SignalingType#wireTag() wire tag} and the only
         * types {@link Calls2SignalingType#ofWireTag(String)} resolves.
         */
        CALL_CHILD,

        /**
         * Marks an acknowledgement leg delivered inside the host stanza layer's shared {@code <ack>}
         * envelope rather than a dedicated {@code <call>} child.
         *
         * <p>The {@code <ack>} body echoes the named child stanza of the request it acknowledges; the engine
         * reads the envelope {@code type} attribute in {@code handle_incoming_xmpp_ack} (fn11546). A type
         * with this mechanism has an empty {@link Calls2SignalingType#wireTag() wire tag}.
         */
        ACK,

        /**
         * Marks a receipt leg delivered inside the host stanza layer's shared {@code <receipt>} envelope
         * rather than a dedicated {@code <call>} child.
         *
         * <p>The engine classifies it in {@code handle_incoming_xmpp_receipt} (fn11551). A type with this
         * mechanism has an empty {@link Calls2SignalingType#wireTag() wire tag}.
         */
        RECEIPT,

        /**
         * Marks a native message-type id that occupies a slot in the {@code voip_signaling_message_type}
         * table but names no wire form: it neither rides a dedicated {@code <call>} child nor an
         * {@code <ack>}/{@code <receipt>} envelope.
         *
         * <p>These ids carry only a CamelCase diagnostic display name in the {@code check_msg_header}
         * pointer table and are loaded by no serializer or dispatch comparison, so the engine never emits
         * or classifies a wire child for them. A type with this mechanism has an empty
         * {@link Calls2SignalingType#wireTag() wire tag} and is absent from the
         * {@link Calls2SignalingType#ofWireTag(String)} index. It is distinct from {@link #NONE}, which is
         * the id-0 no-match sentinel rather than a reserved table id.
         */
        INTERNAL
    }

    /**
     * Indexes the constants by their native message id for constant-time {@link #ofIndex(int)} lookup.
     */
    private static final Map<Integer, Calls2SignalingType> BY_INDEX = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(Calls2SignalingType::index, type -> type));

    /**
     * Indexes the {@link Mechanism#CALL_CHILD} constants by their wire child tag for constant-time
     * {@link #ofWireTag(String)} lookup.
     *
     * <p>Only action-bearing types that name a dedicated {@code <call>} child participate; the
     * acknowledgement and receipt legs ride shared envelopes, and the {@link Mechanism#INTERNAL} reserved
     * ids and {@link #NONE} sentinel name no wire form, so all of those carry no child tag and are
     * excluded. The {@code extension} element is emitted by both {@link #ADD_EXTENSION} and
     * {@link #REMOVE_EXTENSION}; the merge keeps the lower native id ({@link #ADD_EXTENSION}) as the
     * canonical owner because the two are disambiguated downstream by message id rather than by element
     * tag.
     */
    private static final Map<String, Calls2SignalingType> BY_WIRE_TAG = Stream.of(values())
            .filter(type -> type.mechanism == Mechanism.CALL_CHILD)
            .collect(Collectors.toUnmodifiableMap(
                    type -> type.wireTag,
                    type -> type,
                    (left, right) -> left.index <= right.index ? left : right));

    /**
     * Holds the numeric message id the wa-voip engine dispatches on.
     */
    private final int index;

    /**
     * Holds the delivery mechanism that selects whether this type names a {@code <call>} child or rides
     * a shared envelope.
     */
    private final Mechanism mechanism;

    /**
     * Holds the lower-cased wire child tag that names this action inside the {@code <call>} envelope, or
     * {@code null} when this type names no child element (an {@link Mechanism#ACK}/{@link Mechanism#RECEIPT}
     * envelope leg, the {@link Mechanism#NONE} sentinel, or an {@link Mechanism#INTERNAL} reserved id).
     */
    private final String wireTag;

    /**
     * Holds the fixed-header byte length the inbound validator expects, or {@code -1} when no validator
     * case was recovered for this type and the message is variable-length.
     */
    private final int fixedHeaderLength;

    /**
     * Constructs a constant bound to its native id, delivery mechanism, wire tag, and fixed-header length.
     *
     * @param index             the numeric message id the engine dispatches on
     * @param mechanism         the delivery mechanism selecting child-element versus envelope carriage
     * @param wireTag           the lower-cased wire child tag naming this action, or {@code null} when this
     *                          type names no {@code <call>} child element
     * @param fixedHeaderLength the recovered fixed-header length, or {@code -1} when not recovered
     */
    Calls2SignalingType(int index, Mechanism mechanism, String wireTag, int fixedHeaderLength) {
        this.index = index;
        this.mechanism = mechanism;
        this.wireTag = wireTag;
        this.fixedHeaderLength = fixedHeaderLength;
    }

    /**
     * Returns the numeric message id the wa-voip engine dispatches on.
     *
     * <p>This id is the first word of the flattened {@code wa_call_message} and is stable across
     * versions; it is distinct from this constant's {@link Enum#ordinal()}, which has no protocol
     * meaning because the native id space contains the five gaps at 40, 41, 43, 44, and 64.
     *
     * @return the native message id
     */
    public int index() {
        return index;
    }

    /**
     * Returns the delivery mechanism that selects how this type is carried on the wire.
     *
     * <p>A {@link Mechanism#CALL_CHILD} type names a dedicated child element of the {@code <call>} stanza
     * and has a present {@link #wireTag()}; a {@link Mechanism#ACK} or {@link Mechanism#RECEIPT} type
     * rides the host stanza layer's shared envelope and has an empty {@link #wireTag()}; the
     * {@link Mechanism#INTERNAL} reserved ids and the {@link #NONE} sentinel name no wire form and also
     * have an empty {@link #wireTag()}.
     *
     * @return the delivery mechanism for this type
     */
    public Mechanism mechanism() {
        return mechanism;
    }

    /**
     * Returns the lower-cased wire child tag that names this action inside the {@code <call>} envelope.
     *
     * <p>The result is present only for {@link Mechanism#CALL_CHILD} types, which name a dedicated child
     * element the receiver keys on; for {@link Mechanism#ACK} and {@link Mechanism#RECEIPT} legs, the
     * {@link Mechanism#INTERNAL} reserved ids, and the {@link #NONE} sentinel it is empty because they
     * carry no dedicated child element. A present tag is not necessarily a unique inbound dispatch key:
     * {@link #ADD_EXTENSION} and {@link #REMOVE_EXTENSION} both report {@code extension}, and
     * {@link #ofWireTag(String)} resolves that element to its canonical owner {@link #ADD_EXTENSION}.
     *
     * @return the wire child tag, or an empty result when this type names no {@code <call>} child element
     */
    public Optional<String> wireTag() {
        return Optional.ofNullable(wireTag);
    }

    /**
     * Returns the fixed-header byte length the inbound validator expects for this type, if recovered.
     *
     * <p>The wa-voip {@code check_msg_header} validator (fn11495) compares each flattened message's
     * header length against a per-type expected length set by an explicit {@code br_table} case. Types
     * whose id routes to the {@code br_table} default have no validated fixed length and yield an empty
     * result; those callers validate inbound shape by checking the required attributes of the decoded
     * record instead. A recovered length of zero ({@link #MUTE}, {@link #WEB_CLIENT}) is a present
     * {@code OptionalInt.of(0)}, distinct from the empty result of a type with no validator case.
     *
     * @return the expected fixed-header length, or an empty result when no validator case was recovered
     */
    public OptionalInt fixedHeaderLength() {
        return fixedHeaderLength < 0 ? OptionalInt.empty() : OptionalInt.of(fixedHeaderLength);
    }

    /**
     * Looks up the signaling type for a native message id.
     *
     * <p>The lookup is keyed on the protocol id, not on {@link Enum#ordinal()}. An id outside the
     * taxonomy, including any of the five gap ids, yields an empty result.
     *
     * @param index the native message id
     * @return the matching type, or an empty result when no type carries the id
     */
    public static Optional<Calls2SignalingType> ofIndex(int index) {
        return Optional.ofNullable(BY_INDEX.get(index));
    }

    /**
     * Looks up the signaling type for an inbound wire child tag.
     *
     * <p>This is the authoritative inbound dispatch path for action-bearing messages: the receiver reads
     * the single child element tag of a {@code <call>} stanza and resolves it here. Only
     * {@link Mechanism#CALL_CHILD} types are resolvable; acknowledgement and receipt legs are classified
     * by the host stanza layer through the {@code <ack>}/{@code <receipt>} envelope, not through this
     * path. The shared {@code extension} element resolves to its canonical owner {@link #ADD_EXTENSION};
     * a {@link #REMOVE_EXTENSION} is then distinguished downstream by message id. A tag this taxonomy
     * does not declare, including {@code null}, yields an empty result so the caller can drop or buffer
     * the message. Matching is case-sensitive because wire tags are always lower-cased.
     *
     * @param wireTag the wire child tag, or {@code null}
     * @return the matching type, or an empty result when no {@link Mechanism#CALL_CHILD} type carries the tag
     */
    public static Optional<Calls2SignalingType> ofWireTag(String wireTag) {
        if (wireTag == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_WIRE_TAG.get(wireTag));
    }
}
