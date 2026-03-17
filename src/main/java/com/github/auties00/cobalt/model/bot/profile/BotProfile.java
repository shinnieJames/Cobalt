package com.github.auties00.cobalt.model.bot.profile;

import com.github.auties00.cobalt.model.jid.Jid;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.net.URI;
import java.util.*;

/**
 * A bot's profile data fetched from the server via USync.
 *
 * <p>Contains the bot's identity, display metadata, registered commands,
 * suggested prompts, and classification flags. This data is parsed from the
 * {@code <bot><profile>} element in the USync response and persisted in the
 * local {@code bot-profile} store.
 *
 * <p>Example fields returned by the server:
 * <pre>{@code
 * <bot>
 *   <profile persona_id="abc123">
 *     <name>Meta AI</name>
 *     <attributes>...</attributes>
 *     <description>Ask me anything</description>
 *     <category>synthetic</category>
 *     <default>true</default>
 *     <is_meta_created>true</is_meta_created>
 *     <prompts>
 *       <prompt><emoji>✨</emoji><text>Tell me a joke</text></prompt>
 *     </prompts>
 *     <commands>
 *       <description>Available commands</description>
 *       <command><name>imagine</name><description>Generate an image</description></command>
 *     </commands>
 *     <creator>
 *       <name>Meta</name>
 *       <profile_url>https://...</profile_url>
 *     </creator>
 *     <posing_as_professional type="no"/>
 *   </profile>
 * </bot>
 * }</pre>
 */
@ProtobufMessage
public final class BotProfile {
    /**
     * The bot's JID (Jabber ID), uniquely identifying this bot account.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    Jid jid;

    /**
     * The bot's display name as shown to users (e.g. {@code "Meta AI"}).
     */
    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String name;

    /**
     * An opaque, server-supplied attributes string associated with this bot
     * profile.
     *
     * <p>This value comes from the {@code <attributes>} child element in the
     * USync bot profile response. Defaults to an empty string when absent.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String attributes;

    /**
     * A human-readable description of what the bot does
     * (e.g. {@code "Ask me anything"}).
     */
    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    String description;

    /**
     * The character category of this bot's persona.
     *
     * @see BotProfileCategory
     */
    @ProtobufProperty(index = 5, type = ProtobufType.STRING)
    BotProfileCategory category;

    /**
     * Whether this is the default Meta AI bot.
     *
     * <p>When {@code true}, this bot is the primary AI assistant and is
     * typically surfaced prominently in the chat list.
     */
    @ProtobufProperty(index = 6, type = ProtobufType.BOOL)
    boolean isDefault;

    /**
     * Suggested prompts displayed to users to help them start a conversation
     * with this bot.
     */
    @ProtobufProperty(index = 7, type = ProtobufType.MESSAGE)
    SequencedCollection<BotProfilePrompt> prompts;

    /**
     * The persona identifier for this bot profile
     * (e.g. {@code "abc123"}).
     *
     * <p>This is transmitted as the {@code persona_id} attribute on the
     * {@code <profile>} element and is used to differentiate between
     * multiple persona variants of the same bot JID (default, first-party
     * character, or user-generated).
     */
    @ProtobufProperty(index = 8, type = ProtobufType.STRING)
    String personaId;

    /**
     * The bot's registered slash-commands.
     *
     * <p>These are the commands the bot advertises (e.g. {@code "imagine"},
     * {@code "translate"}). When a user sends a message starting with
     * {@code /commandName}, the message's body type should be set to
     * {@code COMMAND} instead of {@code PROMPT}.
     */
    @ProtobufProperty(index = 9, type = ProtobufType.MESSAGE)
    SequencedCollection<BotProfileCommand> commands;

    /**
     * A description of the commands section as a whole
     * (e.g. {@code "Available commands"}).
     *
     * <p>This text is parsed from the {@code <description>} child element
     * inside the {@code <commands>} block.
     */
    @ProtobufProperty(index = 10, type = ProtobufType.STRING)
    String commandsDescription;

    /**
     * Whether this bot was created by Meta (first-party).
     */
    @ProtobufProperty(index = 11, type = ProtobufType.BOOL)
    boolean isMetaCreated;

    /**
     * The display name of the bot's creator
     * (e.g. {@code "Meta"}).
     */
    @ProtobufProperty(index = 12, type = ProtobufType.STRING)
    String creatorName;

    /**
     * The profile URL of the bot's creator
     * (e.g. {@code "https://www.meta.com"}).
     */
    @ProtobufProperty(index = 13, type = ProtobufType.STRING)
    URI creatorProfileUrl;

    /**
     * Whether this bot is posing as a professional (e.g. a doctor, lawyer,
     * or financial advisor).
     *
     * @see BotProfessionalStatus
     */
    @ProtobufProperty(index = 14, type = ProtobufType.STRING)
    BotProfessionalStatus professionalStatus;

    /**
     * Constructs a new {@code BotProfile} with all fields.
     *
     * @param jid                  the bot's JID, must be non-{@code null}
     * @param name                 the bot's display name, may be {@code null}
     * @param attributes           the opaque attributes string, may be {@code null}
     * @param description          the bot's description, may be {@code null}
     * @param category             the persona category, may be {@code null}
     * @param isDefault            whether this is the default Meta AI bot
     * @param prompts              the suggested prompts, may be {@code null}
     * @param personaId            the persona identifier, may be {@code null}
     * @param commands             the registered commands, may be {@code null}
     * @param commandsDescription  the commands section description, may be {@code null}
     * @param isMetaCreated        whether Meta created this bot
     * @param creatorName          the creator's display name, may be {@code null}
     * @param creatorProfileUrl    the creator's profile URL, may be {@code null}
     * @param professionalStatus   the professional-status classification, may be {@code null}
     */
    BotProfile(
            Jid jid,
            String name,
            String attributes,
            String description,
            BotProfileCategory category,
            boolean isDefault,
            SequencedCollection<BotProfilePrompt> prompts,
            String personaId,
            SequencedCollection<BotProfileCommand> commands,
            String commandsDescription,
            boolean isMetaCreated,
            String creatorName,
            URI creatorProfileUrl,
            BotProfessionalStatus professionalStatus
    ) {
        this.jid = Objects.requireNonNull(jid, "jid");
        this.name = name;
        this.attributes = attributes;
        this.description = description;
        this.category = category;
        this.isDefault = isDefault;
        this.prompts = prompts != null ? Collections.unmodifiableSequencedCollection(prompts) : List.of();
        this.personaId = personaId;
        this.commands = commands != null ? Collections.unmodifiableSequencedCollection(commands) : List.of();
        this.commandsDescription = commandsDescription;
        this.isMetaCreated = isMetaCreated;
        this.creatorName = creatorName;
        this.creatorProfileUrl = creatorProfileUrl;
        this.professionalStatus = professionalStatus;
    }

    /**
     * Returns the bot's JID.
     *
     * @return a non-{@code null} JID
     */
    public Jid jid() {
        return jid;
    }

    /**
     * Returns the bot's display name.
     *
     * @return an {@code Optional} containing the display name if present and
     *         non-empty, otherwise an empty {@code Optional}
     */
    public Optional<String> name() {
        return Optional.ofNullable(name).filter(s -> !s.isEmpty());
    }

    /**
     * Returns the opaque attributes string associated with this bot profile.
     *
     * @return an {@code Optional} containing the attributes if present and
     *         non-empty, otherwise an empty {@code Optional}
     */
    public Optional<String> attributes() {
        return Optional.ofNullable(attributes).filter(s -> !s.isEmpty());
    }

    /**
     * Returns the bot's description.
     *
     * @return an {@code Optional} containing the description if present and
     *         non-empty, otherwise an empty {@code Optional}
     */
    public Optional<String> description() {
        return Optional.ofNullable(description).filter(s -> !s.isEmpty());
    }

    /**
     * Returns the character category of this bot's persona.
     *
     * @return an {@code Optional} containing the {@link BotProfileCategory}
     *         if present, otherwise an empty {@code Optional}
     */
    public Optional<BotProfileCategory> category() {
        return Optional.ofNullable(category);
    }

    /**
     * Returns whether this is the default Meta AI bot.
     *
     * @return {@code true} if this is the default bot, otherwise {@code false}
     */
    public boolean isDefault() {
        return isDefault;
    }

    /**
     * Returns the suggested prompts for this bot.
     *
     * @return an unmodifiable collection of prompts, never {@code null}
     */
    public SequencedCollection<BotProfilePrompt> prompts() {
        return prompts;
    }

    /**
     * Returns the persona identifier for this bot profile.
     *
     * @return an {@code Optional} containing the persona ID if present and
     *         non-empty, otherwise an empty {@code Optional}
     */
    public Optional<String> personaId() {
        return Optional.ofNullable(personaId).filter(s -> !s.isEmpty());
    }

    /**
     * Returns the bot's registered slash-commands.
     *
     * <p>When a user sends a message starting with {@code /commandName}
     * and the command name matches one returned here, the message body type
     * should be set to {@code COMMAND} instead of {@code PROMPT}.
     *
     * @return an unmodifiable collection of commands, never {@code null}
     */
    public SequencedCollection<BotProfileCommand> commands() {
        return commands;
    }

    /**
     * Returns the commands section description.
     *
     * @return an {@code Optional} containing the description if present and
     *         non-empty, otherwise an empty {@code Optional}
     */
    public Optional<String> commandsDescription() {
        return Optional.ofNullable(commandsDescription).filter(s -> !s.isEmpty());
    }

    /**
     * Returns whether this bot was created by Meta.
     *
     * @return {@code true} if Meta created this bot, otherwise {@code false}
     */
    public boolean isMetaCreated() {
        return isMetaCreated;
    }

    /**
     * Returns the bot creator's display name.
     *
     * @return an {@code Optional} containing the creator name if present and
     *         non-empty, otherwise an empty {@code Optional}
     */
    public Optional<String> creatorName() {
        return Optional.ofNullable(creatorName).filter(s -> !s.isEmpty());
    }

    /**
     * Returns the bot creator's profile URL.
     *
     * @return an {@code Optional} containing the URL if present and
     *         non-empty, otherwise an empty {@code Optional}
     */
    public Optional<URI> creatorProfileUrl() {
        return Optional.ofNullable(creatorProfileUrl);
    }

    /**
     * Returns whether this bot is posing as a professional.
     *
     * @return an {@code Optional} containing the {@link BotProfessionalStatus}
     *         if present, otherwise an empty {@code Optional}
     */
    public Optional<BotProfessionalStatus> professionalStatus() {
        return Optional.ofNullable(professionalStatus);
    }

    /**
     * Returns whether the given text starts with a registered slash-command
     * for this bot.
     *
     * <p>The text must begin with {@code /commandName} and either end there
     * or be followed by whitespace. For example, given a registered command
     * named {@code "imagine"}, the texts {@code "/imagine"} and
     * {@code "/imagine a sunset"} both match, but {@code "/imaginemore"}
     * does not.
     *
     * @param text the message text to test, may be {@code null}
     * @return {@code true} if the text starts with a registered command,
     *         otherwise {@code false}
     */
    public boolean isCommand(String text) {
        if (text == null || !text.startsWith("/") || commands.isEmpty()) {
            return false;
        }

        for (var command : commands) {
            var slash = "/" + command.name();
            if (text.startsWith(slash)
                    && (text.length() == slash.length() || Character.isWhitespace(text.charAt(slash.length())))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sets the bot's JID.
     *
     * @param jid the JID to set, must be non-{@code null}
     */
    public void setJid(Jid jid) {
        this.jid = jid;
    }

    /**
     * Sets the bot's display name.
     *
     * @param name the display name, may be {@code null}
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Sets the opaque attributes string.
     *
     * @param attributes the attributes string, may be {@code null}
     */
    public void setAttributes(String attributes) {
        this.attributes = attributes;
    }

    /**
     * Sets the bot's description.
     *
     * @param description the description, may be {@code null}
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Sets the persona category.
     *
     * @param category the category, may be {@code null}
     */
    public void setCategory(BotProfileCategory category) {
        this.category = category;
    }

    /**
     * Sets whether this is the default Meta AI bot.
     *
     * @param isDefault {@code true} if this is the default bot
     */
    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }

    /**
     * Sets the suggested prompts.
     *
     * @param prompts the prompts collection, may be {@code null}
     */
    public void setPrompts(SequencedCollection<BotProfilePrompt> prompts) {
        this.prompts = prompts;
    }

    /**
     * Sets the persona identifier.
     *
     * @param personaId the persona ID, may be {@code null}
     */
    public void setPersonaId(String personaId) {
        this.personaId = personaId;
    }

    /**
     * Sets the registered commands.
     *
     * @param commands the commands collection, may be {@code null}
     */
    public void setCommands(SequencedCollection<BotProfileCommand> commands) {
        this.commands = commands;
    }

    /**
     * Sets the commands section description.
     *
     * @param commandsDescription the description, may be {@code null}
     */
    public void setCommandsDescription(String commandsDescription) {
        this.commandsDescription = commandsDescription;
    }

    /**
     * Sets whether this bot was created by Meta.
     *
     * @param isMetaCreated {@code true} if Meta created this bot
     */
    public void setMetaCreated(boolean isMetaCreated) {
        this.isMetaCreated = isMetaCreated;
    }

    /**
     * Sets the bot creator's display name.
     *
     * @param creatorName the creator name, may be {@code null}
     */
    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }

    /**
     * Sets the bot creator's profile URL.
     *
     * @param creatorProfileUrl the profile URL, may be {@code null}
     */
    public void setCreatorProfileUrl(URI creatorProfileUrl) {
        this.creatorProfileUrl = creatorProfileUrl;
    }

    /**
     * Sets the professional-status classification.
     *
     * @param professionalStatus the status, may be {@code null}
     */
    public void setProfessionalStatus(BotProfessionalStatus professionalStatus) {
        this.professionalStatus = professionalStatus;
    }
}
