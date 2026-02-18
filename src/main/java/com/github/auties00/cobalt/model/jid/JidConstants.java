package com.github.auties00.cobalt.model.jid;

/**
 * A collection of static constants that define the delimiter characters used during JID parsing.
 *
 * <p>WhatsApp JIDs follow the general format {@code user_agent:device@server}, where each
 * component is separated by a specific delimiter character defined in this class. The phone
 * character handles the optional international dialing prefix that may appear in phone-number-based
 * user identifiers.
 */
final class JidConstants {
    /**
     * Constructs a new {@code JidConstants}.
     *
     * @throws UnsupportedOperationException always, because this is a utility class
     */
    private JidConstants() {
        throw new UnsupportedOperationException("JidConstants is a utility class and should not be initialized");
    }

    /**
     * The character prefix used to denote international phone numbers in JID user components.
     * Phone-number-based JIDs may begin with this character, which is stripped during parsing.
     */
    static final char PHONE_CHAR = '+';

    /**
     * The character used to separate the user component from the server component in a JID.
     * For example, in the JID {@code 1234567890@s.whatsapp.net}, this character separates
     * the user {@code 1234567890} from the server {@code s.whatsapp.net}.
     */
    static final char SERVER_CHAR = '@';

    /**
     * The character used to separate the device identifier from the user component in a JID.
     * For example, in the JID {@code 1234567890:1@s.whatsapp.net}, this character separates
     * the user {@code 1234567890} from the device identifier {@code 1}.
     */
    static final char DEVICE_CHAR = ':';

    /**
     * The character used to separate the agent identifier from the user component in a JID.
     * For example, in the JID {@code 1234567890_1:2@s.whatsapp.net}, this character separates
     * the user {@code 1234567890} from the agent identifier {@code 1}.
     */
    static final char AGENT_CHAR = '_';
}
