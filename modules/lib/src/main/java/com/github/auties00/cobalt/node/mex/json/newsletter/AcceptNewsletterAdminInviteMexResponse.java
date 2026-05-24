package com.github.auties00.cobalt.node.mex.json.newsletter;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.node.mex.MexOperation;
import com.github.auties00.cobalt.node.Node;
import java.util.Optional;

/**
 * Parses the MEX response of the accept-newsletter-admin-invite mutation
 * built by {@link AcceptNewsletterAdminInviteMexRequest}.
 *
 * @apiNote
 * Hands back the newsletter id echoed under {@code xwa2_newsletter_admin_invite_accept}
 * after the invitee accepts the admin invite; consumers use it to confirm
 * which newsletter just transitioned the local user to admin membership.
 */
@WhatsAppWebModule(moduleName = "WAWebMexAcceptNewsletterAdminInviteJob")
public final class AcceptNewsletterAdminInviteMexResponse implements MexOperation.Response.Json {
    /**
     * The newsletter Jid string echoed under the
     * {@code xwa2_newsletter_admin_invite_accept.id} response field.
     */
    private final String id;

    /**
     * Constructs a response wrapping the echoed newsletter id.
     *
     * @apiNote
     * Reserved for the static parser; external callers obtain instances via
     * {@link #of(Node)}.
     *
     * @param id the newsletter Jid string echoed by the relay
     */
    private AcceptNewsletterAdminInviteMexResponse(String id) {
        this.id = id;
    }

    /**
     * Parses the MEX response carried by the given IQ result node.
     *
     * @apiNote
     * Drains the {@code <result>} child's byte content into the JSON parser;
     * the returned {@link Optional} is empty when the result child is
     * missing, when its content cannot be decoded, or when the JSON envelope
     * omits the expected {@code data.xwa2_newsletter_admin_invite_accept}
     * root.
     *
     * @param node the IQ result node received from the relay
     * @return the parsed response, or empty when the node does not carry a
     *         well-formed result payload
     */
    public static Optional<AcceptNewsletterAdminInviteMexResponse> of(Node node) {
        return node.getChild("result")
                .flatMap(Node::toContentBytes)
                .flatMap(AcceptNewsletterAdminInviteMexResponse::of);
    }

    /**
     * Returns the newsletter Jid string echoed by the relay.
     *
     * @apiNote
     * Empty when the GraphQL envelope omits {@code id}; otherwise carries the
     * same Jid string sent in {@link AcceptNewsletterAdminInviteMexRequest}.
     *
     * @return the echoed newsletter id, or empty when omitted
     */
    public Optional<String> id() {
        return Optional.ofNullable(id);
    }

    /**
     * Parses the response from the raw UTF-8 JSON payload of the
     * {@code <result>} child.
     *
     * @apiNote
     * Reserved for the public {@link #of(Node)} overload; callers should not
     * hold raw JSON bytes.
     *
     * @implNote
     * This implementation guards every nested object lookup so a malformed
     * envelope produces {@link Optional#empty()} rather than a parser
     * exception, mirroring the defensive null checks in WA Web's caller.
     *
     * @param json the UTF-8 encoded JSON payload
     * @return the parsed response, or empty when the envelope lacks the
     *         expected {@code data.xwa2_newsletter_admin_invite_accept} root
     */
    private static Optional<AcceptNewsletterAdminInviteMexResponse> of(byte[] json) {
        var jsonObject = JSON.parseObject(json);
        if (jsonObject == null) {
            return Optional.empty();
        }

        var data = jsonObject.getJSONObject("data");
        if (data == null) {
            return Optional.empty();
        }

        var root = data.getJSONObject("xwa2_newsletter_admin_invite_accept");
        if (root == null) {
            return Optional.empty();
        }

        var id = root.getString("id");

        return Optional.of(new AcceptNewsletterAdminInviteMexResponse(id));
    }
}
