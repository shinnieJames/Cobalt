package com.github.auties00.cobalt.calls2.core.control;

import com.github.auties00.cobalt.calls2.signaling.CallMessage;
import com.github.auties00.cobalt.stanza.Stanza;

/**
 * The request-reply egress a call-link or waiting-room IQ operation is dispatched through.
 *
 * <p>The call-link query, join, and edit, and the waiting-room admit and deny, are not fire-and-forget
 * actions: they are IQ requests addressed to the {@code call} service that the relay answers with a typed
 * acknowledgement carried in the reply. This seam captures that round trip: it takes the typed request
 * {@link CallMessage} and returns the reply {@link Stanza} the controller parses into the operation's ack.
 * It hides the IQ envelope construction, the {@code to="call"} addressing, the request type code, and the
 * blocking wait for the reply, all of which the lifecycle layer that owns the call's request dispatcher
 * supplies.
 *
 * <p>The call blocks the calling virtual thread until the reply arrives, matching the Cobalt threading
 * model where a request-reply IQ is a plain blocking call on a virtual thread rather than a future.
 *
 * @implSpec An implementation MUST wrap the request in the appropriate IQ envelope addressed to the
 * {@code call} service with the request type the operation uses (for example the call-link query type and
 * the waiting-room admit and deny types), block the calling thread until the reply arrives, and return the
 * reply stanza. It MAY throw to signal a transport failure or a server error reply; a controller treats a
 * throw as the operation failing.
 * @implNote This implementation seam stands in for the native call-link and waiting-room IQ senders of
 * module {@code ff-tScznZ8P} ({@code protocol/xmpp/stanzas/call_link.cc} and {@code waiting_room.cc}),
 * which route these operations over SMAX to {@code to="call"} with the per-operation IQ type (query type
 * {@code 0x84}, admit type {@code 0x47}, deny type {@code 0x49}); Cobalt isolates that round trip behind
 * this seam so the control package depends only on the typed {@link CallMessage} and the reply
 * {@link Stanza}.
 * @see CallMessage
 */
@FunctionalInterface
public interface CallLinkIqSender {
    /**
     * Dispatches a call-link or waiting-room IQ request and returns its reply.
     *
     * @implSpec An implementation MUST block until the reply arrives and return the reply stanza, or throw on
     * a transport or server failure.
     * @param request the typed request to send; never {@code null}
     * @return the reply stanza the caller parses into the operation's ack; never {@code null}
     */
    Stanza sendForReply(CallMessage request);
}
