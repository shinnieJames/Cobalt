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
 * USync {@code bot} protocol.
 *
 * @implNote WAWebUsyncBotProfile.USyncBotProfileProtocol.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncBotProfile")
public final class UsyncBotProfileProtocol implements UsyncProtocol {
    /** Wire literal for the protocol tag name. */
    public static final String NAME = "bot";

    /**
     * Wire-protocol version emitted on the {@code v} attribute of the
     * inner {@code <profile/>} query element.
     */
    public static final String PROFILE_VERSION = "1";

    /**
     * Constructs a default bot-profile-protocol descriptor.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncBotProfile",
            exports = "USyncBotProfileProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncBotProfileProtocol() {
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncBotProfile",
            exports = "USyncBotProfileProtocol.getName", adaptation = WhatsAppAdaptation.DIRECT)
    public String name() {
        return NAME;
    }

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
        Boolean isMetaCreated = isMetaCreatedNode.map(n -> "true".equals(textOf(n, ""))).orElse(null);
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
     * Reads the inline text content of the supplied node, falling back to
     * {@code defaultValue} when the node is {@code null} or carries no
     * text.
     */
    private static String textOf(Node node, String defaultValue) {
        return node == null ? defaultValue : node.toContentString().orElse(defaultValue);
    }

    /**
     * Parses the optional {@code <prompts>} child into a list of
     * {@link BotProfileResult.Prompt} entries.
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
     * Parses the optional {@code <commands>} child into a list of
     * {@link BotProfileResult.Command} entries plus a descriptive header.
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
     * child to the corresponding {@link BotProfileResult.PosingAsProfessional}.
     */
    private BotProfileResult.PosingAsProfessional posingFromWire(String wire) {
        return switch (wire) {
            case "yes" -> BotProfileResult.PosingAsProfessional.YES;
            case "no"  -> BotProfileResult.PosingAsProfessional.NO;
            default    -> BotProfileResult.PosingAsProfessional.UNKNOWN;
        };
    }

    /**
     * Internal pair returned by {@link #parseCommands(Optional)}.
     */
    private record CommandsParsed(List<BotProfileResult.Command> commands, String commandsDescription) {
    }
}
