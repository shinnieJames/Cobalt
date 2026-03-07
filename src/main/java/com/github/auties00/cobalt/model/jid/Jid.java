package com.github.auties00.cobalt.model.jid;

import com.github.auties00.cobalt.exception.WhatsAppMalformedJidException;
import com.github.auties00.libsignal.SignalProtocolAddress;
import it.auties.protobuf.annotation.ProtobufDeserializer;
import it.auties.protobuf.annotation.ProtobufSerializer;
import it.auties.protobuf.model.ProtobufString;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.github.auties00.cobalt.model.jid.JidConstants.*;

/**
 * A record that represents a WhatsApp JID (Jabber ID), the primary addressing mechanism used
 * by the WhatsApp protocol to identify users, groups, devices, and other entities.
 *
 * <p>A JID is composed of four parts serialized as {@code user_agent:device@server}. The
 * {@code user} identifies the entity (such as a phone number for personal accounts, a numeric
 * identifier for groups or bots, or an opaque LID for privacy-preserving addressing). The
 * {@code server} is a {@link JidServer} that classifies the domain the entity belongs to.
 * The {@code agent} and {@code device} are unsigned byte values (0 to 255) that further
 * distinguish client agent configurations and companion devices, respectively. A value of
 * {@code 0} for either indicates the default (primary) agent or device.
 *
 * <p>This class provides a rich set of factory methods for parsing JIDs from string
 * representations, protobuf encodings, and numeric identifiers. Pre-allocated singleton
 * instances are available for commonly referenced server-only JIDs and well-known accounts
 * such as the Status broadcast, the announcements account, and the Meta AI bot.
 *
 * <p>Instances of this record are immutable. Transformation methods such as
 * {@link #withServer(JidServer)}, {@link #withAgent(int)}, and {@link #withDevice(int)}
 * return new {@code Jid} instances rather than modifying the original.
 *
 * @param user   the user identifier component, or {@code null} for server-only JIDs
 * @param server the non-null server domain component
 * @param device the device identifier, an unsigned byte (0 to 255) where {@code 0} is the
 *               primary device
 * @param agent  the agent identifier, an unsigned byte (0 to 255) where {@code 0} is the
 *               default agent
 * @see JidServer
 * @see JidProvider
 */
public record Jid(String user, JidServer server, int device, int agent) implements JidProvider {
    /**
     * A concurrent cache of server-only {@code Jid} instances keyed by {@link JidServer},
     * used for unknown server types that lack pre-allocated singletons.
     */
    private static final ConcurrentMap<JidServer, Jid> JID_SERVER_CACHE = new ConcurrentHashMap<>();

    /**
     * The singleton server-only JID for the legacy user domain ({@code c.us}).
     */
    private static final Jid LEGACY_USER_SERVER = new Jid(null, JidServer.legacyUser());

    /**
     * The singleton server-only JID for the group and community domain ({@code g.us}).
     */
    private static final Jid GROUP_OR_COMMUNITY_SERVER = new Jid(null, JidServer.groupOrCommunity());

    /**
     * The singleton server-only JID for the broadcast domain ({@code broadcast}).
     */
    private static final Jid BROADCAST_SERVER = new Jid(null, JidServer.broadcast());

    /**
     * The singleton server-only JID for the call domain ({@code call}).
     */
    private static final Jid CALL_SERVER = new Jid(null, JidServer.call());

    /**
     * The singleton server-only JID for the current standard user domain
     * ({@code s.whatsapp.net}).
     */
    private static final Jid USER_SERVER = new Jid(null, JidServer.user());

    /**
     * The singleton server-only JID for the Linked Identity domain ({@code lid}).
     */
    private static final Jid LID_SERVER = new Jid(null, JidServer.lid());

    /**
     * The singleton server-only JID for the newsletter domain ({@code newsletter}).
     */
    private static final Jid NEWSLETTER_SERVER = new Jid(null, JidServer.newsletter());

    /**
     * The singleton server-only JID for the bot domain ({@code bot}).
     */
    private static final Jid BOT_SERVER = new Jid(null, JidServer.bot());

    /**
     * The singleton server-only JID for the business-hosted domain ({@code hosted}).
     */
    private static final Jid HOSTED_SERVER = new Jid(null, JidServer.hosted());

    /**
     * The singleton server-only JID for the business-hosted LID domain
     * ({@code hosted.lid}).
     */
    private static final Jid HOSTED_LID_SERVER = new Jid(null, JidServer.hostedLid());

    /**
     * The singleton server-only JID for the Facebook Messenger interoperability domain
     * ({@code msgr}).
     */
    private static final Jid MSGR_SERVER = new Jid(null, JidServer.messenger());

    /**
     * The singleton server-only JID for the cross-platform interoperability domain
     * ({@code interop}).
     */
    private static final Jid INTEROP_SERVER = new Jid(null, JidServer.interop());

    /**
     * The JID for the official WhatsApp surveys account
     * ({@code 16505361212@s.whatsapp.net}).
     */
    private static final Jid OFFICIAL_SURVEYS_ACCOUNT = new Jid("16505361212", JidServer.user());

    /**
     * The JID for the official WhatsApp business account
     * ({@code 16505361212@c.us}).
     */
    private static final Jid OFFICIAL_BUSINESS_ACCOUNT = new Jid("16505361212", JidServer.legacyUser());

    /**
     * The JID for the announcements (PSA) account ({@code 0@s.whatsapp.net}).
     */
    private static final Jid ANNOUNCEMENTS_ACCOUNT = new Jid("0", JidServer.user());

    /**
     * The JID for the Meta AI bot account ({@code 867051314767696@bot}).
     */
    private static final Jid META_AI_BOT_ACCOUNT = new Jid("867051314767696", JidServer.bot());

    /**
     * The JID for the live location sharing broadcast
     * ({@code location@broadcast}).
     */
    private static final Jid LOCATION_BROADCAST = new Jid("location", JidServer.broadcast());

    /**
     * Validates record components.
     *
     * @throws NullPointerException           if {@code server} is {@code null}
     * @throws WhatsAppMalformedJidException if {@code device} or {@code agent} is not
     *                                        in the unsigned byte range (0 to 255)
     */
    public Jid {
        Objects.requireNonNull(server, "server cannot be null");
        checkUnsignedShort(device);
        checkUnsignedByte(agent);
    }

    /**
     * Constructs a new {@code Jid} with the specified user and server, using default
     * values of {@code 0} for both device and agent.
     *
     * @param user   the user identifier, or {@code null} for a server-only JID
     * @param server the non-null server domain
     * @throws NullPointerException if {@code server} is {@code null}
     */
    public Jid(String user, JidServer server) {
        this(user, server, 0, 0);
    }

    /**
     * Returns the singleton server-only JID for the legacy user domain ({@code c.us}).
     *
     * @return the legacy user server JID
     */
    public static Jid legacyUserServer() {
        return Jid.LEGACY_USER_SERVER;
    }

    /**
     * Returns the singleton server-only JID for the group and community domain
     * ({@code g.us}).
     *
     * @return the group or community server JID
     */
    public static Jid groupOrCommunityServer() {
        return Jid.GROUP_OR_COMMUNITY_SERVER;
    }

    /**
     * Returns the singleton server-only JID for the broadcast domain
     * ({@code broadcast}). This JID also serves as the Status broadcast account.
     *
     * @return the broadcast server JID
     */
    public static Jid broadcastServer() {
        return Jid.BROADCAST_SERVER;
    }

    /**
     * Returns the singleton server-only JID for the call domain ({@code call}).
     *
     * @return the call server JID
     */
    public static Jid callServer() {
        return Jid.CALL_SERVER;
    }

    /**
     * Returns the singleton server-only JID for the current standard user domain
     * ({@code s.whatsapp.net}).
     *
     * @return the user server JID
     */
    public static Jid userServer() {
        return Jid.USER_SERVER;
    }

    /**
     * Returns the singleton server-only JID for the Linked Identity domain
     * ({@code lid}).
     *
     * @return the LID server JID
     */
    public static Jid lidServer() {
        return Jid.LID_SERVER;
    }

    /**
     * Returns the singleton server-only JID for the newsletter domain
     * ({@code newsletter}).
     *
     * @return the newsletter server JID
     */
    public static Jid newsletterServer() {
        return Jid.NEWSLETTER_SERVER;
    }

    /**
     * Returns the singleton server-only JID for the bot domain ({@code bot}).
     *
     * @return the bot server JID
     */
    public static Jid botServer() {
        return Jid.BOT_SERVER;
    }

    /**
     * Returns the singleton server-only JID for the business-hosted domain
     * ({@code hosted}).
     *
     * @return the hosted server JID
     */
    public static Jid hostedServer() {
        return Jid.HOSTED_SERVER;
    }

    /**
     * Returns the singleton server-only JID for the business-hosted LID domain
     * ({@code hosted.lid}).
     *
     * @return the hosted LID server JID
     */
    public static Jid hostedLidServer() {
        return Jid.HOSTED_LID_SERVER;
    }

    /**
     * Returns the singleton server-only JID for the Facebook Messenger
     * interoperability domain ({@code msgr}).
     *
     * @return the Messenger server JID
     */
    public static Jid msgrServer() {
        return Jid.MSGR_SERVER;
    }

    /**
     * Returns the singleton server-only JID for the cross-platform interoperability
     * domain ({@code interop}).
     *
     * @return the interop server JID
     */
    public static Jid interopServer() {
        return Jid.INTEROP_SERVER;
    }

    /**
     * Returns the JID for the official WhatsApp surveys account
     * ({@code 16505361212@s.whatsapp.net}).
     *
     * @return the surveys account JID
     */
    public static Jid officialSurveysAccount() {
        return Jid.OFFICIAL_SURVEYS_ACCOUNT;
    }

    /**
     * Returns the JID for the official WhatsApp business account
     * ({@code 16505361212@c.us}).
     *
     * @return the official business account JID
     */
    public static Jid officialBusinessAccount() {
        return Jid.OFFICIAL_BUSINESS_ACCOUNT;
    }

    /**
     * Returns the JID for the Status broadcast account ({@code status@broadcast}).
     *
     * <p>This is the same as {@link #broadcastServer()} because the Status broadcast
     * JID is represented as a server-only broadcast JID in the protocol.
     *
     * @return the Status broadcast account JID
     */
    public static Jid statusBroadcastAccount() {
        return Jid.BROADCAST_SERVER;
    }

    /**
     * Returns the JID for the announcements (PSA) account ({@code 0@s.whatsapp.net}).
     *
     * @return the announcements account JID
     */
    public static Jid announcementsAccount() {
        return Jid.ANNOUNCEMENTS_ACCOUNT;
    }

    /**
     * Returns the JID for the Meta AI bot account ({@code 867051314767696@bot}).
     *
     * @return the Meta AI bot account JID
     */
    public static Jid metaAiBotAccount() {
        return Jid.META_AI_BOT_ACCOUNT;
    }

    /**
     * Returns the JID for the live location sharing broadcast
     * ({@code location@broadcast}).
     *
     * @return the location broadcast JID
     */
    public static Jid locationBroadcast() {
        return Jid.LOCATION_BROADCAST;
    }

    /**
     * Returns a server-only {@code Jid} for the given {@link JidServer}.
     *
     * <p>For known server types, the corresponding pre-allocated singleton is returned.
     * For unknown server types, a cached instance is returned from an internal concurrent
     * map.
     *
     * @param server the non-null server domain
     * @return the server-only JID for the specified server
     * @throws NullPointerException if {@code server} is {@code null}
     */
    public static Jid of(JidServer server) {
        Objects.requireNonNull(server, "Server cannot be null");
        return switch (server.type()) {
            case UNKNOWN -> JID_SERVER_CACHE.computeIfAbsent(server, _ -> new Jid(null, server));
            case LEGACY_USER -> legacyUserServer();
            case GROUP_OR_COMMUNITY -> groupOrCommunityServer();
            case BROADCAST -> broadcastServer();
            case CALL -> callServer();
            case USER -> userServer();
            case LID -> lidServer();
            case NEWSLETTER -> newsletterServer();
            case BOT -> botServer();
            case HOSTED -> hostedServer();
            case HOSTED_LID -> hostedLidServer();
            case MSGR -> msgrServer();
            case INTEROP -> interopServer();
        };
    }

    /**
     * Returns a {@code Jid} with the specified components.
     *
     * <p>If {@code user} is {@code null}, delegates to {@link #of(JidServer)} to return
     * a server-only JID. Otherwise constructs a new fully-qualified JID.
     *
     * @param user   the user identifier, or {@code null} for a server-only JID
     * @param server the non-null server domain
     * @param device the device identifier (0 to 255)
     * @param agent  the agent identifier (0 to 255)
     * @return the corresponding {@code Jid} instance
     */
    public static Jid of(String user, JidServer server, int device, int agent) {
        if (user == null) {
            return of(server);
        } else {
            return new Jid(user, server, device, agent);
        }
    }

    /**
     * Returns a user {@code Jid} from a non-negative numeric identifier, using the
     * current standard user server domain ({@code s.whatsapp.net}).
     *
     * @param jid the numeric user identifier, which must be non-negative
     * @return a user JID with the given number as its user component
     * @throws WhatsAppMalformedJidException if {@code jid} is negative
     */
    public static Jid of(long jid) {
        if (jid < 0) {
            throw new WhatsAppMalformedJidException("value cannot be negative");
        }
        return new Jid(String.valueOf(jid), JidServer.user());
    }

    /**
     * Parses a {@code Jid} from its string representation.
     *
     * <p>The string is expected to follow the format
     * {@code [+]user[_agent][:device]@server}. If the string matches a known
     * server-only JID, the pre-allocated singleton is returned. If no {@code @}
     * separator is present, the user server ({@code s.whatsapp.net}) is assumed.
     *
     * @param jid the JID string to parse, or {@code null}
     * @return the parsed {@code Jid}, or {@code null} if the input is {@code null}
     * @throws WhatsAppMalformedJidException if the string is malformed
     */
    public static Jid of(String jid) {
        if (jid == null) {
            return null;
        }
        var knownServer = JidServer.of(jid, false);
        if (knownServer != null) {
            return of(knownServer);
        }
        var serverSeparatorIndex = jid.indexOf(SERVER_CHAR);
        var server = serverSeparatorIndex == -1
                ? JidServer.user()
                : JidServer.of(jid, serverSeparatorIndex + 1, jid.length() - serverSeparatorIndex - 1);
        return parseJid(jid, serverSeparatorIndex, server);
    }

    /**
     * Returns a {@code Jid} by parsing the user component from the given string,
     * using the specified server domain.
     *
     * @param user   the user string, which may contain device and agent components
     * @param server the non-null server domain to use
     * @return the parsed {@code Jid}
     * @throws NullPointerException          if {@code server} is {@code null}
     * @throws WhatsAppMalformedJidException if the user string is malformed
     */
    public static Jid of(String user, JidServer server) {
        Objects.requireNonNull(server, "Server cannot be null");
        return parseJid(user, user.indexOf(SERVER_CHAR), server);
    }

    /**
     * Deserializes a {@code Jid} from a protobuf string representation.
     *
     * <p>This method serves as the protobuf deserialization entry point. It handles
     * both {@linkplain ProtobufString.Lazy lazy} (zero-copy) and
     * {@linkplain ProtobufString.Value materialized} protobuf string variants.
     *
     * @param jid the protobuf string to deserialize, or {@code null}
     * @return the deserialized {@code Jid}, or {@code null} if the input is {@code null}
     * @throws WhatsAppMalformedJidException if the string is malformed
     */
    @ProtobufDeserializer
    public static Jid of(ProtobufString jid) {
        return switch (jid) {
            case ProtobufString.Lazy lazy -> Jid.of(lazy);
            case ProtobufString.Value value -> Jid.of(value.toString());
            case null -> null;
        };
    }

    /**
     * Parses a {@code Jid} from a lazy protobuf string, operating directly on the
     * underlying byte array to avoid unnecessary string allocation.
     *
     * <p>This is the optimized fast path for protobuf deserialization. It parses the
     * user, agent, device, and server components by scanning the raw bytes, only
     * materializing a {@code String} for the user component and (when necessary) for
     * unknown server domains.
     *
     * @param jid the lazy protobuf string to parse, or {@code null}
     * @return the parsed {@code Jid}, or {@code null} if the input is {@code null}
     * @throws WhatsAppMalformedJidException if the byte content is malformed
     */
    public static Jid of(ProtobufString.Lazy jid) {
        if (jid == null) {
            return null;
        }
        var source = jid.encodedBytes();
        var offset = jid.encodedOffset();
        var length = jid.encodedLength();
        var knownServer = JidServer.of(source, offset, length, false);
        if (knownServer != null) {
            return of(knownServer);
        }

        enum ParserState { USER, DEVICE, AGENT }

        var state = ParserState.USER;
        var userLength = length;
        var agent = 0;
        var device = 0;
        var server = JidServer.user();
        for (var parserPosition = 0; parserPosition < length; parserPosition++) {
            var token = (char) (source[offset + parserPosition] & 0x7F);
            if (token == SERVER_CHAR) {
                if (state == ParserState.USER) {
                    userLength = parserPosition;
                }
                server = JidServer.of(source, offset + parserPosition + 1, length - parserPosition - 1, true);
                break;
            }
            switch (state) {
                case USER -> {
                    if (token == DEVICE_CHAR) {
                        userLength = parserPosition;
                        state = ParserState.DEVICE;
                    } else if (token == AGENT_CHAR) {
                        userLength = parserPosition;
                        state = ParserState.AGENT;
                    }
                }
                case DEVICE -> {
                    if (token == AGENT_CHAR) {
                        if (agent != 0) {
                            throw new WhatsAppMalformedJidException("Encountered unexpected token '" + token + "' while parsing value '" + jid + "'");
                        }
                        state = ParserState.AGENT;
                    } else if (Character.isDigit(token)) {
                        device = device * 10 + (token - '0');
                    } else {
                        var value = new String(source, offset, length, StandardCharsets.US_ASCII);
                        throw new WhatsAppMalformedJidException("Encountered unexpected token '" + token + "' while parsing value '" + value + "'");
                    }
                }
                case AGENT -> {
                    if (token == DEVICE_CHAR) {
                        if (device != 0) {
                            throw new WhatsAppMalformedJidException("Encountered unexpected token '" + token + "' while parsing value '" + jid + "'");
                        }
                        state = ParserState.DEVICE;
                    } else if (Character.isDigit(token)) {
                        agent = agent * 10 + (token - '0');
                    } else {
                        var value = new String(source, offset, length, StandardCharsets.US_ASCII);
                        throw new WhatsAppMalformedJidException("Encountered unexpected token '" + token + "' while parsing value '" + value + "'");
                    }
                }
            }
        }
        var user = new String(source, offset, userLength, StandardCharsets.UTF_8);
        return new Jid(user, server, device, agent);
    }

    /**
     * Validates that the given integer falls within the unsigned byte range.
     *
     * @param i the value to check
     * @throws WhatsAppMalformedJidException if the value is not in the range 0 to 255
     */
    /**
     * Validates that the given integer falls within the unsigned short range.
     *
     * @param i the value to check
     * @throws WhatsAppMalformedJidException if the value is not in the range 0 to 65535
     */
    private static void checkUnsignedShort(int i) {
        if (i < 0 || i > 65535) {
            throw new WhatsAppMalformedJidException(i + " is not an unsigned short");
        }
    }

    /**
     * Validates that the given integer falls within the unsigned byte range.
     *
     * @param i the value to check
     * @throws WhatsAppMalformedJidException if the value is not in the range 0 to 255
     */
    private static void checkUnsignedByte(int i) {
        if (i < 0 || i > 255) {
            throw new WhatsAppMalformedJidException(i + " is not an unsigned byte");
        }
    }

    /**
     * Parses the user, agent, and device components from a JID string up to the
     * specified length, using the given server domain.
     *
     * @param jid       the source string
     * @param jidLength the index of the server separator, or {@code -1} if absent
     * @param server    the pre-resolved server domain
     * @return the parsed {@code Jid}
     * @throws WhatsAppMalformedJidException if the string is malformed
     */
    private static Jid parseJid(String jid, int jidLength, JidServer server) {
        var length = jidLength == -1 ? jid.length() : jidLength;
        if (length == 0) {
            return of(server);
        }
        var offset = jid.charAt(0) == PHONE_CHAR ? 1 : 0;
        if (offset >= length) {
            throw new WhatsAppMalformedJidException("Malformed value '" + jid + "'");
        }

        enum ParserState { USER, DEVICE, AGENT }

        var state = ParserState.USER;
        var userLength = length;
        var agent = 0;
        var device = 0;
        for (var parserPosition = 0; parserPosition < length; parserPosition++) {
            var token = jid.charAt(offset + parserPosition);
            if (token == SERVER_CHAR) {
                if (state == ParserState.USER) {
                    userLength = parserPosition;
                }
                server = JidServer.of(jid, offset + parserPosition + 1, length - parserPosition - 1);
                break;
            }
            switch (state) {
                case USER -> {
                    if (token == DEVICE_CHAR) {
                        userLength = parserPosition;
                        state = ParserState.DEVICE;
                    } else if (token == AGENT_CHAR) {
                        userLength = parserPosition;
                        state = ParserState.AGENT;
                    }
                }
                case DEVICE -> {
                    if (token == AGENT_CHAR) {
                        if (agent != 0) {
                            throw new WhatsAppMalformedJidException("Encountered unexpected token '" + token + "' while parsing value '" + jid + "'");
                        }
                        state = ParserState.AGENT;
                    } else if (Character.isDigit(token)) {
                        device = device * 10 + (token - '0');
                    } else {
                        throw new WhatsAppMalformedJidException("Encountered unexpected token '" + token + "' while parsing value '" + jid + "'");
                    }
                }
                case AGENT -> {
                    if (token == DEVICE_CHAR) {
                        if (device != 0) {
                            throw new WhatsAppMalformedJidException("Encountered unexpected token '" + token + "' while parsing value '" + jid + "'");
                        }
                        state = ParserState.DEVICE;
                    } else if (Character.isDigit(token)) {
                        agent = agent * 10 + (token - '0');
                    } else {
                        throw new WhatsAppMalformedJidException("Encountered unexpected token '" + token + "' while parsing value '" + jid + "'");
                    }
                }
            }
        }
        var user = jid.substring(offset, offset + userLength);
        return new Jid(user, server, device, agent);
    }

    /**
     * Returns the full string representation of this JID in the format
     * {@code user_agent:device@server}.
     *
     * <p>Components with default values are omitted: the agent is omitted when it is
     * {@code 0}, the device is omitted when it is {@code 0}, and the user is omitted
     * for server-only JIDs. A server-only JID produces just the server address string.
     *
     * @return the string form of this JID
     */
    @Override
    public String toString() {
        var hasUser = this.hasUser();
        var hasAgent = this.hasAgent();
        var hasDevice = this.hasDevice();
        if (!hasUser && !hasAgent && !hasDevice) {
            return this.server().toString();
        }
        var user = hasUser ? this.user() : "";
        var agentStr = hasAgent ? "" + AGENT_CHAR + this.agent() : "";
        var deviceStr = hasDevice ? "" + DEVICE_CHAR + this.device() : "";
        return user + agentStr + deviceStr + SERVER_CHAR + this.server().toString();
    }

    /**
     * Serializes this JID to its protobuf string representation.
     *
     * <p>The serialized form is identical to the result of {@link #toString()}.
     *
     * @return the protobuf-compatible string form of this JID
     */
    @ProtobufSerializer
    public String toProtobufString() {
        return toString();
    }

    /**
     * Returns whether this JID has a non-null user component.
     *
     * @return {@code true} if the user component is present, {@code false} for
     *         server-only JIDs
     */
    public boolean hasUser() {
        return user != null;
    }

    /**
     * Returns whether this JID's user component equals the specified value.
     *
     * @param user the user string to compare against
     * @return {@code true} if the user components are equal (including both being
     *         {@code null}), {@code false} otherwise
     */
    public boolean hasUser(String user) {
        return Objects.equals(this.user, user);
    }

    /**
     * Returns whether this JID's server component equals the specified server.
     *
     * @param server the server to compare against
     * @return {@code true} if the server components are equal, {@code false} otherwise
     */
    public boolean hasServer(JidServer server) {
        return this.server().equals(server);
    }

    /**
     * Returns whether this JID has a non-zero device identifier.
     *
     * @return {@code true} if the device identifier is not {@code 0}
     */
    public boolean hasDevice() {
        return device != 0;
    }

    /**
     * Returns whether this JID's device identifier equals the specified value.
     *
     * @param device the device identifier to compare against
     * @return {@code true} if the device identifiers are equal
     */
    public boolean hasDevice(int device) {
        return this.device == device;
    }

    /**
     * Returns whether this JID has a non-zero agent identifier.
     *
     * @return {@code true} if the agent identifier is not {@code 0}
     */
    public boolean hasAgent() {
        return agent != 0;
    }

    /**
     * Returns whether this JID's agent identifier equals the specified value.
     *
     * @param agent the agent identifier to compare against
     * @return {@code true} if the agent identifiers are equal
     */
    public boolean hasAgent(int agent) {
        return this.agent == agent;
    }

    /**
     * Returns whether this JID is a server-only JID for the specified server.
     *
     * <p>A server-only JID has a {@code null} user component and the given server.
     *
     * @param server the server to check
     * @return {@code true} if this is a server-only JID matching the specified server
     */
    public boolean isServerJid(JidServer server) {
        return user() == null && this.server().equals(server);
    }

    /**
     * Returns whether this JID belongs to the Linked Identity server domain
     * ({@code lid}).
     *
     * @return {@code true} if the server is the LID domain
     */
    public boolean hasLidServer() {
        return hasServer(JidServer.lid());
    }

    /**
     * Returns whether this JID belongs to a user server domain, either the current
     * standard ({@code s.whatsapp.net}) or the legacy ({@code c.us}) domain.
     *
     * @return {@code true} if the server is a user domain
     */
    public boolean hasUserServer() {
        return hasServer(JidServer.user()) || hasServer(JidServer.legacyUser());
    }

    /**
     * Returns whether this JID belongs to the group and community server domain
     * ({@code g.us}).
     *
     * @return {@code true} if the server is the group or community domain
     */
    public boolean hasGroupOrCommunityServer() {
        return hasServer(JidServer.groupOrCommunity());
    }

    /**
     * Returns whether this JID belongs to the broadcast server domain
     * ({@code broadcast}).
     *
     * @return {@code true} if the server is the broadcast domain
     */
    public boolean hasBroadcastServer() {
        return hasServer(JidServer.broadcast());
    }

    /**
     * Returns whether this JID is the Status broadcast account
     * ({@code status@broadcast}).
     *
     * @return {@code true} if this JID equals the Status broadcast account
     */
    public boolean isStatusBroadcastAccount() {
        return statusBroadcastAccount().equals(this);
    }

    /**
     * Returns whether this JID belongs to the newsletter server domain
     * ({@code newsletter}).
     *
     * @return {@code true} if the server is the newsletter domain
     */
    public boolean hasNewsletterServer() {
        return hasServer(JidServer.newsletter());
    }

    /**
     * Returns whether this JID belongs to the bot server domain ({@code bot}).
     *
     * @return {@code true} if the server is the bot domain
     */
    public boolean hasBotServer() {
        return hasServer(JidServer.bot());
    }

    /**
     * Returns whether this JID belongs to the call server domain ({@code call}).
     *
     * @return {@code true} if the server is the call domain
     */
    public boolean hasCallServer() {
        return hasServer(JidServer.call());
    }

    /**
     * Returns whether this JID belongs to the Facebook Messenger interoperability
     * server domain ({@code msgr}).
     *
     * @return {@code true} if the server is the Messenger domain
     */
    public boolean hasMessengerServer() {
        return hasServer(JidServer.messenger());
    }

    /**
     * Returns whether this JID belongs to the cross-platform interoperability server
     * domain ({@code interop}).
     *
     * @return {@code true} if the server is the interop domain
     */
    public boolean hasInteropServer() {
        return hasServer(JidServer.interop());
    }

    /**
     * Returns whether this JID belongs to the business-hosted server domain
     * ({@code hosted}).
     *
     * @return {@code true} if the server is the hosted domain
     */
    public boolean hasHostedServer() {
        return hasServer(JidServer.hosted());
    }

    /**
     * Returns whether this JID belongs to the business-hosted LID server domain
     * ({@code hosted.lid}).
     *
     * @return {@code true} if the server is the hosted LID domain
     */
    public boolean hasHostedLidServer() {
        return hasServer(JidServer.hostedLid());
    }

    /**
     * Returns a new {@code Jid} with the specified server, keeping all other components
     * unchanged. If the server is already equal to this JID's server, {@code this} is
     * returned.
     *
     * @param server the new server domain
     * @return this JID if the server is unchanged, otherwise a new JID with the given
     *         server
     */
    public Jid withServer(JidServer server) {
        if (Objects.equals(this.server, server)) {
            return this;
        }
        return new Jid(user, server, device, agent);
    }

    /**
     * Returns a new {@code Jid} with the specified agent, keeping all other components
     * unchanged. If the agent is already equal to this JID's agent, {@code this} is
     * returned.
     *
     * @param agent the new agent identifier (0 to 255)
     * @return this JID if the agent is unchanged, otherwise a new JID with the given
     *         agent
     */
    public Jid withAgent(int agent) {
        if (this.agent == agent) {
            return this;
        }
        return new Jid(user, server, device, agent);
    }

    /**
     * Returns a new {@code Jid} with the specified device, keeping all other components
     * unchanged. If the device is already equal to this JID's device, {@code this} is
     * returned.
     *
     * @param device the new device identifier (0 to 255)
     * @return this JID if the device is unchanged, otherwise a new JID with the given
     *         device
     */
    public Jid withDevice(int device) {
        if (this.device == device) {
            return this;
        }
        return new Jid(user, server, device, agent);
    }

    /**
     * Returns a new {@code Jid} with the agent and device reset to {@code 0}, keeping
     * the user and server unchanged. If both are already {@code 0}, {@code this} is
     * returned.
     *
     * @return this JID if agent and device are already zero, otherwise a new JID
     *         without agent and device data
     */
    public Jid withoutData() {
        if (!hasDevice() && !hasAgent()) {
            return this;
        }
        return new Jid(user, server);
    }

    /**
     * Converts this JID to its corresponding user JID by stripping device and agent
     * data and remapping hosted server domains to their underlying user domains.
     *
     * <p>If the server is the {@linkplain JidServer#hosted() hosted} domain, the
     * resulting JID uses the {@linkplain JidServer#user() user} domain. If the server
     * is the {@linkplain JidServer#hostedLid() hosted LID} domain, the resulting JID
     * uses the {@linkplain JidServer#lid() LID} domain. For all other servers, this
     * method behaves like {@link #withoutData()}.
     *
     * @return the user JID derived from this JID
     */
    public Jid toUserJid() {
        var server = server();
        if (server.equals(JidServer.hosted())) {
            return Jid.of(user(), JidServer.user());
        }
        if (server.equals(JidServer.hostedLid())) {
            return Jid.of(user(), JidServer.lid());
        }
        return withoutData();
    }

    /**
     * Returns whether this JID and the specified JID refer to the same account,
     * even if they use different addressing-mode domains.
     *
     * <p>This matches hosted-domain and non-hosted-domain pairs that represent
     * the same underlying account:
     * <ul>
     * <li>{@code hosted} ↔ {@code s.whatsapp.net} (or {@code c.us})
     * <li>{@code hosted.lid} ↔ {@code lid}
     * </ul>
     *
     * <p>The comparison is performed by normalizing both JIDs via
     * {@link #toUserJid()} (which strips device/agent data and remaps hosted
     * domains) and then checking equality.
     *
     * @param other the other JID to compare against
     * @return {@code true} if both JIDs resolve to the same user JID after
     *         hosted-domain normalization
     *
     * @apiNote WAWebWidFactory.isSameAccountAndAddressingMode: recognizes
     * {@code hosted↔c.us} and {@code hosted.lid↔lid} as same-account pairs.
     */
    public boolean isSameAccount(Jid other) {
        if (other == null) {
            return false;
        }
        return this.toUserJid().equals(other.toUserJid());
    }

    /**
     * Attempts to extract a phone number from this JID's user component.
     *
     * <p>If the user component consists entirely of digits, an {@code Optional}
     * containing the phone number prefixed with {@code +} is returned. Otherwise
     * an empty {@code Optional} is returned.
     *
     * @return an {@code Optional} containing the phone number string (e.g.
     *         {@code "+1234567890"}), or empty if the user is not a phone number
     */
    public Optional<String> toPhoneNumber() {
        var user = user();
        if (user == null) {
            return Optional.empty();
        }
        for (var i = 0; i < user.length(); i++) {
            if (!Character.isDigit(user.charAt(i))) {
                return Optional.empty();
            }
        }
        return Optional.of('+' + user);
    }

    /**
     * Converts this JID to a {@link SignalProtocolAddress} for use with the Signal
     * protocol encryption layer.
     *
     * <p>The resulting address uses the {@linkplain #user() user} component as the
     * name and the {@linkplain #device() device} identifier as the device id.
     *
     * @return a new {@code SignalProtocolAddress}
     */
    public SignalProtocolAddress toSignalAddress() {
        return new SignalProtocolAddress(user(), device());
    }

    /**
     * Returns this JID instance.
     *
     * @return {@code this}
     */
    @Override
    public Jid toJid() {
        return this;
    }
}
