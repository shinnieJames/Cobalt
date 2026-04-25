package com.github.auties00.cobalt.node.usync.protocol;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.usync.UsyncAddressingMode;
import com.github.auties00.cobalt.node.usync.UsyncProtocol;
import com.github.auties00.cobalt.node.usync.UsyncProtocolResult;
import com.github.auties00.cobalt.node.usync.result.UsyncProtocolError;
import com.github.auties00.cobalt.node.usync.UsyncUser;
import com.github.auties00.cobalt.node.usync.result.ContactResult;

import java.time.Duration;
import java.util.Optional;

/**
 * USync {@code contact} protocol.
 *
 * @implNote WAWebUsyncContact.USyncContactProtocol.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncContact")
public final class UsyncContactProtocol implements UsyncProtocol {
    /** The wire literal used as the protocol's tag name. */
    public static final String NAME = "contact";

    /**
     * The addressing mode the request applies to. {@code null} means the
     * default phone-number addressing.
     */
    private final UsyncAddressingMode addressingMode;

    /**
     * Creates a contact-protocol descriptor for the given addressing mode.
     *
     * @param addressingMode the addressing mode, or {@code null} for the
     *                       default phone-number addressing
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncContact",
            exports = "USyncContactProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncContactProtocol(UsyncAddressingMode addressingMode) {
        this.addressingMode = addressingMode;
    }

    /**
     * Creates a contact-protocol descriptor with PN addressing.
     */
    public UsyncContactProtocol() {
        this(UsyncAddressingMode.PN);
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncContact",
            exports = "USyncContactProtocol.getName", adaptation = WhatsAppAdaptation.DIRECT)
    public String name() {
        return NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncContact",
            exports = "USyncContactProtocol.getQueryElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Node buildQueryElement() {
        var builder = new NodeBuilder().description(NAME);
        if (addressingMode == UsyncAddressingMode.LID) {
            builder.attribute("addressing_mode", UsyncAddressingMode.LID.wireValue());
        }
        return builder.build();
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncContact",
            exports = "USyncContactProtocol.getUserElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<Node> buildUserElement(UsyncUser user) {
        if (user.phoneNumber().isPresent()) {
            return Optional.of(new NodeBuilder()
                    .description(NAME)
                    .content(user.phoneNumber().get().getBytes())
                    .build());
        }
        if (user.username().isPresent()) {
            var builder = new NodeBuilder().description(NAME)
                    .attribute("username", user.username().get());
            user.pin().ifPresent(p -> builder.attribute("pin", p));
            user.lid().ifPresent(l -> builder.attribute("lid", l.toString()));
            return Optional.of(builder.build());
        }
        if (user.contactType().isPresent()) {
            return Optional.of(new NodeBuilder()
                    .description(NAME)
                    .attribute("type", user.contactType().get())
                    .build());
        }
        return Optional.empty();
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncContact",
            exports = "contactParser", adaptation = WhatsAppAdaptation.ADAPTED)
    public UsyncProtocolResult parseUserResult(Node child) {
        if (!child.hasDescription(NAME)) {
            throw new IllegalStateException("expected <" + NAME + ">, got <" + child.description() + ">");
        }
        var error = parseError(child);
        if (error.isPresent()) {
            return error.get();
        }
        var type = child.getAttributeAsString("type")
                .orElseThrow(() -> new IllegalStateException("[usync] contact node has missing type attribute"));
        var username = child.getAttributeAsString("username").orElse(null);
        var content = child.toContentString().orElse(null);
        return new ContactResult(type, username, content);
    }

    /**
     * Helper that probes the optional {@code <error/>} child of a USync
     * protocol response. Reused by every protocol parser.
     *
     * @param child the protocol-tagged response node
     * @return the parsed error, or empty when the response is a success
     */
    public static Optional<UsyncProtocolError> parseError(Node child) {
        return child.getChild("error").map(err -> new UsyncProtocolError(
                err.getRequiredAttributeAsInt("code"),
                err.getAttributeAsString("text", ""),
                err.getAttributeAsLong("error_backoff").stream().boxed()
                        .map(Duration::ofSeconds).findFirst().orElse(null)));
    }
}
