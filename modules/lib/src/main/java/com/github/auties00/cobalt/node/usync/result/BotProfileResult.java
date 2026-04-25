package com.github.auties00.cobalt.node.usync.result;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Success result of {@code WAWebUsyncBotProfile.botProfileParser}.
 *
 * <p>Carries the bot's profile metadata. Several fields are nullable in
 * the wire response (creator name/url, "is meta-created" flag,
 * "posing as professional" classification); those are exposed via
 * {@link Optional}-returning accessors.
 *
 * @implNote WAWebUsyncBotProfile.botProfileParser.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncBotProfile")
public final class BotProfileResult implements UsyncProtocolResponse {
    /**
     * The bot's display name; never {@code null} (defaults to the empty
     * string when the relay omits the {@code <name>} child).
     */
    private final String name;

    /**
     * Opaque attribute string used by the rendering tier; carried through
     * verbatim. Never {@code null} (defaults to the empty string).
     */
    private final String attributes;

    /**
     * The bot's profile description; never {@code null} (defaults to the
     * empty string).
     */
    private final String description;

    /**
     * The bot's category label; never {@code null} (defaults to the empty
     * string).
     */
    private final String category;

    /**
     * Whether the bot is the default suggestion for new conversations.
     */
    private final boolean isDefault;

    /**
     * Suggested prompts to help the user start chatting; never
     * {@code null} (defaults to the empty list).
     */
    private final List<Prompt> prompts;

    /**
     * The bot's persona id (free-form string); never {@code null}
     * (defaults to the empty string).
     */
    private final String personaId;

    /**
     * The slash-command list; never {@code null} (defaults to the empty
     * list).
     */
    private final List<Command> commands;

    /**
     * Free-form blurb above the command list; never {@code null} (defaults
     * to the empty string).
     */
    private final String commandsDescription;

    /**
     * Whether Meta authored the bot, or {@code null} when the
     * {@code <is_meta_created>} child is absent.
     */
    private final Boolean isMetaCreated;

    /**
     * The human creator's display name, or {@code null} when absent.
     */
    private final String creatorName;

    /**
     * The human creator's profile URL, or {@code null} when absent.
     */
    private final String creatorProfileUrl;

    /**
     * The bot's "posing as professional" classification, or {@code null}
     * when absent.
     */
    private final PosingAsProfessional posingAsProfessional;

    /**
     * Creates a new bot-profile result. {@code null}-tolerant on every
     * field except the four required strings.
     *
     * @param name                 the display name; must not be
     *                             {@code null}
     * @param attributes           the attribute string; must not be
     *                             {@code null}
     * @param description          the profile description; must not be
     *                             {@code null}
     * @param category             the category label; must not be
     *                             {@code null}
     * @param isDefault            whether the bot is the default
     *                             suggestion
     * @param prompts              the suggested prompts; defaults to the
     *                             empty list when {@code null}
     * @param personaId            the persona id; must not be {@code null}
     * @param commands             the command list; defaults to the empty
     *                             list when {@code null}
     * @param commandsDescription  the commands description; must not be
     *                             {@code null}
     * @param isMetaCreated        whether Meta authored the bot, or
     *                             {@code null}
     * @param creatorName          the creator's display name, or
     *                             {@code null}
     * @param creatorProfileUrl    the creator's profile URL, or
     *                             {@code null}
     * @param posingAsProfessional the posing-as-professional classification,
     *                             or {@code null}
     */
    public BotProfileResult(
            String name,
            String attributes,
            String description,
            String category,
            boolean isDefault,
            List<Prompt> prompts,
            String personaId,
            List<Command> commands,
            String commandsDescription,
            Boolean isMetaCreated,
            String creatorName,
            String creatorProfileUrl,
            PosingAsProfessional posingAsProfessional) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.attributes = Objects.requireNonNull(attributes, "attributes cannot be null");
        this.description = Objects.requireNonNull(description, "description cannot be null");
        this.category = Objects.requireNonNull(category, "category cannot be null");
        this.isDefault = isDefault;
        this.prompts = prompts == null ? List.of() : List.copyOf(prompts);
        this.personaId = Objects.requireNonNull(personaId, "personaId cannot be null");
        this.commands = commands == null ? List.of() : List.copyOf(commands);
        this.commandsDescription = Objects.requireNonNull(commandsDescription, "commandsDescription cannot be null");
        this.isMetaCreated = isMetaCreated;
        this.creatorName = creatorName;
        this.creatorProfileUrl = creatorProfileUrl;
        this.posingAsProfessional = posingAsProfessional;
    }

    /** @return the bot's display name */
    public String name() {
        return name;
    }

    /** @return the opaque attributes string */
    public String attributes() {
        return attributes;
    }

    /** @return the bot's description */
    public String description() {
        return description;
    }

    /** @return the bot's category */
    public String category() {
        return category;
    }

    /** @return whether the bot is the default suggestion */
    public boolean isDefault() {
        return isDefault;
    }

    /** @return the suggested-prompts list, never {@code null} */
    public List<Prompt> prompts() {
        return prompts;
    }

    /** @return the persona id */
    public String personaId() {
        return personaId;
    }

    /** @return the slash-command list, never {@code null} */
    public List<Command> commands() {
        return commands;
    }

    /** @return the free-form blurb above the command list */
    public String commandsDescription() {
        return commandsDescription;
    }

    /** @return whether Meta authored the bot, when present */
    public Optional<Boolean> isMetaCreated() {
        return Optional.ofNullable(isMetaCreated);
    }

    /** @return the human creator's display name, when present */
    public Optional<String> creatorName() {
        return Optional.ofNullable(creatorName);
    }

    /** @return the human creator's profile URL, when present */
    public Optional<String> creatorProfileUrl() {
        return Optional.ofNullable(creatorProfileUrl);
    }

    /** @return the "posing as professional" classification, when present */
    public Optional<PosingAsProfessional> posingAsProfessional() {
        return Optional.ofNullable(posingAsProfessional);
    }

    /**
     * One suggested prompt — a hint the UI displays as a starter for the
     * user to send to the bot.
     *
     * @implNote WAWebUsyncBotProfile.s: nested helper that walks the
     *     {@code <prompts>} child.
     */
    @WhatsAppWebModule(moduleName = "WAWebUsyncBotProfile")
    public static final class Prompt {
        /**
         * Leading emoji glyph; never {@code null} (defaults to the empty
         * string).
         */
        private final String emoji;

        /**
         * Prompt text; never {@code null} (defaults to the empty string).
         */
        private final String text;

        /**
         * Creates a new prompt.
         *
         * @param emoji the emoji; must not be {@code null}
         * @param text  the text; must not be {@code null}
         */
        public Prompt(String emoji, String text) {
            this.emoji = Objects.requireNonNull(emoji, "emoji cannot be null");
            this.text = Objects.requireNonNull(text, "text cannot be null");
        }

        /** @return the leading emoji glyph */
        public String emoji() {
            return emoji;
        }

        /** @return the prompt text */
        public String text() {
            return text;
        }
    }

    /**
     * One slash-command supported by the bot.
     *
     * @implNote WAWebUsyncBotProfile.u: nested helper that walks the
     *     {@code <commands>} child.
     */
    @WhatsAppWebModule(moduleName = "WAWebUsyncBotProfile")
    public static final class Command {
        /**
         * Slash-command identifier; never {@code null}.
         */
        private final String name;

        /**
         * Description shown in the picker; never {@code null}.
         */
        private final String description;

        /**
         * Creates a new command descriptor.
         *
         * @param name        the slash-command identifier
         * @param description the picker description
         */
        public Command(String name, String description) {
            this.name = Objects.requireNonNull(name, "name cannot be null");
            this.description = Objects.requireNonNull(description, "description cannot be null");
        }

        /** @return the slash-command identifier */
        public String name() {
            return name;
        }

        /** @return the picker description */
        public String description() {
            return description;
        }
    }

    /**
     * Tristate flag for the bot's "posing as professional" classification.
     *
     * @implNote WAWebBotTypes.BotPosingAsProfessionalType.
     */
    @WhatsAppWebModule(moduleName = "WAWebBotTypes")
    public enum PosingAsProfessional {
        /** The relay returned {@code type="unknown"}. */
        UNKNOWN,
        /** The relay returned {@code type="yes"}. */
        YES,
        /** The relay returned {@code type="no"}. */
        NO
    }
}
