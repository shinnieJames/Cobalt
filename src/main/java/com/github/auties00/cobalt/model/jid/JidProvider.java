package com.github.auties00.cobalt.model.jid;

import com.github.auties00.cobalt.model.contact.Contact;
import com.github.auties00.cobalt.model.info.MessageInfoParent;

/**
 * A sealed interface that provides a standard way to obtain a {@link Jid} from various
 * WhatsApp model types.
 *
 * <p>Several types in the WhatsApp protocol carry an associated JID, such as contacts,
 * message information wrappers, and server descriptors. This interface unifies access
 * to the underlying {@code Jid} by requiring each implementor to expose a {@link #toJid()}
 * method, enabling uniform handling of JID-bearing objects across the API.
 */
public sealed interface JidProvider permits Contact, MessageInfoParent, Jid, JidServer {
    /**
     * Returns the {@link Jid} represented by this object.
     *
     * @return a non-null {@code Jid}
     */
    Jid toJid();
}
