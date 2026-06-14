package com.github.auties00.cobalt.call.signaling;

import com.github.auties00.cobalt.model.jid.Jid;

import java.util.Objects;

/**
 * One {@code <participant pid jid />} child of a call ACK's {@code <relay>} block.
 *
 * <p>Identifies one party in the call by its server-assigned participant id and its addressing
 * mode JID. The local user is referenced by {@link CallRelay#selfPid()}; peers are referenced by
 * {@link CallRelay#peerPid()}; both index into the {@link CallRelay#participants()} collection.
 */
public final class CallParticipant {
    /**
     * The {@code pid} attribute, an unsigned 32-bit identifier the server assigns per call.
     */
    private final int pid;

    /**
     * The {@code jid} attribute, the participant's JID in the call's addressing mode.
     */
    private final Jid jid;

    /**
     * Constructs a participant entry.
     *
     * @param pid the {@code pid} attribute
     * @param jid the {@code jid} attribute; never {@code null}
     * @throws NullPointerException if {@code jid} is {@code null}
     */
    CallParticipant(int pid, Jid jid) {
        this.pid = pid;
        this.jid = Objects.requireNonNull(jid, "jid cannot be null");
    }

    /**
     * Returns the {@code pid} attribute.
     *
     * @return the participant id
     */
    public int pid() {
        return pid;
    }

    /**
     * Returns the {@code jid} attribute.
     *
     * @return the participant JID; never {@code null}
     */
    public Jid jid() {
        return jid;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || (obj instanceof CallParticipant that
                && this.pid == that.pid
                && this.jid.equals(that.jid));
    }

    @Override
    public int hashCode() {
        return Objects.hash(pid, jid);
    }

    @Override
    public String toString() {
        return "CallParticipant[pid=" + pid + ", jid=" + jid + ']';
    }
}
