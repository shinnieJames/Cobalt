package com.github.auties00.cobalt.node.usync.protocol;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.usync.UsyncProtocol;
import com.github.auties00.cobalt.node.usync.UsyncProtocolResult;
import com.github.auties00.cobalt.node.usync.UsyncUser;
import com.github.auties00.cobalt.node.usync.result.BotProfileResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * USync {@code bot} protocol descriptor.
 *
 * @apiNote
 * Asks the relay for each peer's bot profile metadata (display name,
 * description, prompts, slash-commands, creator block, persona id). Used by
 * {@code WAWebRequestBotProfiles.requestBotProfiles}; pair each
 * {@link UsyncUser} with a persona id through
 * {@link UsyncUser#withPersonaId(String)} when the bot exposes multiple
 * personas.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncBotProfile")
public final class UsyncBotProfileProtocol implements UsyncProtocol {
    /**
     * Wire literal for the protocol tag name.
     */
    public static final String NAME = "bot";

    /**
     * Wire-protocol version emitted on the {@code v} attribute of the inner
     * {@code <profile/>} query element.
     */
    public static final String PROFILE_VERSION = "1";

    /**
     * Builds a default bot-profile-protocol descriptor.
     *
     * @apiNote
     * The descriptor is stateless; per-user state lives on each
     * {@link UsyncUser} (specifically the persona id).
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncBotProfile",
            exports = "USyncBotProfileProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncBotProfileProtocol() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncBotProfile",
            exports = "USyncBotProfileProtocol.getName", adaptation = WhatsAppAdaptation.DIRECT)
    public String name() {
        return NAME;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation wraps a versioned {@code <profile v="1"/>} child
     * inside the {@code <bot>} element, matching the JS
     * {@code wap("bot", null, wap("profile", {v: "1"}))} shape.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncBotProfile",
            exports = "USyncBotProfileProtocol.getQueryElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Node buildQueryElement() {
        return new NodeBuilder()
                .description(NAME)
                .content(List.of(new NodeBuilder()
                        .description("profile")
                        .attribute("v", PROFILE_VERSION)
                        .build()))
                .build();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always attaches a per-user {@code <bot>} child
     * carrying an inner {@code <profile/>} with the optional
     * {@code persona_id} attribute, matching the JS
     * {@code USyncBotProfileProtocol.getUserElement} shape (the JS path
     * returns the same wrap unconditionally, whether or not a persona id
     * is set).
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncBotProfile",
            exports = "USyncBotProfileProtocol.getUserElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<Node> buildUserElement(UsyncUser user) {
        var inner = new NodeBuilder().description("profile");
        user.personaId().ifPresent(p -> inner.attribute("persona_id", p));
        return Optional.of(new NodeBuilder()
                .description(NAME)
                .content(List.of(inner.build()))
                .build());
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation reads every optional sub-element the JS
     * {@code botProfileParser} reads (name, attributes, description,
     * category, default flag, prompts, persona id, commands block with its
     * description header, meta-created flag, creator block,
     * posing-as-professional type) and projects them into the typed
     * {@link BotProfileResult} record. Missing nodes fall back to empty
     * strings or {@code null} per the JS optional-chaining defaults.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncBotProfile",
            exports = "botProfileParser", adaptation = WhatsAppAdaptation.ADAPTED)
    public UsyncProtocolResult parseUserResult(Node child) {
        if (!child.hasDescription(NAME)) {
            throw new IllegalStateException("expected <" + NAME + ">, got <" + child.description() + ">");
        }
        var error = UsyncContactProtocol.parseError(child);
        if (error.isPresent()) {
            return error.get();
        }
        var profile = child.getRequiredChild("profile");

        var name = textOf(profile.getChild("name").orElse(null), "");
        var attributes = textOf(profile.getChild("attributes").orElse(null), "");
        var description = textOf(profile.getChild("description").orElse(null), "");
        var category = textOf(profile.getChild("category").orElse(null), "");
        var isDefault = "true".equals(textOf(profile.getChild("default").orElse(null), ""));
        var prompts = parsePrompts(profile.getChild("prompts"));
        var personaId = profile.getAttributeAsString("persona_id", "");
        var commandsParsed = parseCommands(profile.getChild("commands"));
        var isMetaCreatedNode = profile.getChild("is_meta_created");
        var isMetaCreated = isMetaCreatedNode.map(n -> "true".equals(textOf(n, ""))).orElse(null);
        var creatorNode = profile.getChild("creator");
        var creatorName = creatorNode
                .flatMap(n -> n.getChild("name"))
                .map(n -> textOf(n, "")).orElse(null);
        var creatorProfileUrl = creatorNode
                .flatMap(n -> n.getChild("profile_url"))
                .map(n -> textOf(n, "")).orElse(null);
        var posing = profile.getChild("posing_as_professional")
                .flatMap(n -> n.getAttributeAsString("type"))
                .map(this::posingFromWire).orElse(null);

        return new BotProfileResult(
                name, attributes, description, category, isDefault,
                prompts, personaId, commandsParsed.commands, commandsParsed.commandsDescription,
                isMetaCreated, creatorName, creatorProfileUrl, posing);
    }

    /**
     * Reads a node's inline text content, falling back when the node is
     * absent or empty.
     *
     * @apiNote
     * Used by the bot-profile parser to mirror the JS
     * {@code (node?.contentString()) ?? default} pattern in one place.
     *
     * @param node         the node to read
     * @param defaultValue the value to return when the node is {@code null}
     *                     or empty
     * @return the inline text or the fallback
     */
    private static String textOf(Node node, String defaultValue) {
        return node == null ? defaultValue : node.toContentString().orElse(defaultValue);
    }

    /**
     * Parses the optional {@code <prompts>} child into a list of typed
     * {@link BotProfileResult.Prompt} entries.
     *
     * @apiNote
     * Each prompt carries an {@code <emoji>} and a {@code <text>}; both
     * fall back to the empty string when absent, matching the JS
     * coalescing in {@code parsePrompts}.
     *
     * @param promptsChild the optional {@code <prompts>} child
     * @return the parsed prompts, never {@code null}
     */
    private static List<BotProfileResult.Prompt> parsePrompts(Optional<Node> promptsChild) {
        if (promptsChild.isEmpty()) {
            return List.of();
        }
        var out = new ArrayList<BotProfileResult.Prompt>();
        promptsChild.get().streamChildren("prompt").forEach(prompt -> {
            var emoji = textOf(prompt.getChild("emoji").orElse(null), "");
            var text = textOf(prompt.getChild("text").orElse(null), "");
            out.add(new BotProfileResult.Prompt(emoji, text));
        });
        return List.copyOf(out);
    }

    /**
     * Parses the optional {@code <commands>} child into a list of typed
     * {@link BotProfileResult.Command} entries plus the block's descriptive
     * header.
     *
     * @apiNote
     * The header lives on a {@code <description>} sibling of the
     * {@code <command>} entries, not inside each command.
     *
     * @param commandsChild the optional {@code <commands>} child
     * @return the parsed commands and their header
     */
    private static CommandsParsed parseCommands(Optional<Node> commandsChild) {
        if (commandsChild.isEmpty()) {
            return new CommandsParsed(List.of(), "");
        }
        var node = commandsChild.get();
        var description = textOf(node.getChild("description").orElse(null), "");
        var out = new ArrayList<BotProfileResult.Command>();
        node.streamChildren("command").forEach(cmd -> {
            var name = textOf(cmd.getChild("name").orElse(null), "");
            var desc = textOf(cmd.getChild("description").orElse(null), "");
            out.add(new BotProfileResult.Command(name, desc));
        });
        return new CommandsParsed(List.copyOf(out), description);
    }

    /**
     * Maps the {@code type} attribute on the {@code <posing_as_professional>}
     * child to its typed enum value.
     *
     * @apiNote
     * Returns {@link BotProfileResult.PosingAsProfessional#UNKNOWN} for any
     * value other than {@code "yes"} or {@code "no"}, matching the JS
     * fall-through default.
     *
     * @param wire the wire literal
     * @return the matching enum value
     */
    private BotProfileResult.PosingAsProfessional posingFromWire(String wire) {
        return switch (wire) {
            case "yes" -> BotProfileResult.PosingAsProfessional.YES;
            case "no"  -> BotProfileResult.PosingAsProfessional.NO;
            default    -> BotProfileResult.PosingAsProfessional.UNKNOWN;
        };
    }

    /**
     * Pair of the parsed command list and its descriptive header.
     *
     * @apiNote
     * Internal carrier returned by {@link #parseCommands(Optional)}; kept
     * private because callers always destructure both values together.
     *
     * @param commands            the parsed command list
     * @param commandsDescription the free-form header above the list
     */
    private record CommandsParsed(List<BotProfileResult.Command> commands, String commandsDescription) {
    }
}
