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
 * USync {@code contact} protocol descriptor.
 *
 * @apiNote
 * Asks the relay whether each peer is a registered WhatsApp user and, when
 * the addressing mode is LID, resolves usernames or phone numbers to LIDs in
 * the same round trip. Used by contact-import flows (see
 * {@code WAWebContactImportContactVerifier.verifyWhatsAppUsers}) and by
 * username lookups (see {@code WAWebQueryExistsJob}).
 *
 * @implNote
 * This implementation also hosts the shared
 * {@link #parseError(Node)} helper that the
 * other ten protocol parsers reuse, keeping the per-protocol error decode
 * single-sourced.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncContact")
public final class UsyncContactProtocol implements UsyncProtocol {
    /**
     * Wire literal for the protocol tag name.
     */
    public static final String NAME = "contact";

    /**
     * Addressing mode this descriptor applies to; {@code null} means the
     * default phone-number addressing.
     */
    private final UsyncAddressingMode addressingMode;

    /**
     * Builds a contact-protocol descriptor for the given addressing mode.
     *
     * @apiNote
     * Pass {@link UsyncAddressingMode#LID} when the local contact database
     * has been migrated to LID; pass {@link UsyncAddressingMode#PN} or
     * {@code null} otherwise.
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
     * Builds a contact-protocol descriptor with phone-number addressing.
     *
     * @apiNote
     * Convenience shortcut for {@code new UsyncContactProtocol(UsyncAddressingMode.PN)};
     * used by call sites that build queries without consulting the username
     * gating utility.
     */
    public UsyncContactProtocol() {
        this(UsyncAddressingMode.PN);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncContact",
            exports = "USyncContactProtocol.getName", adaptation = WhatsAppAdaptation.DIRECT)
    public String name() {
        return NAME;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation emits the {@code addressing_mode} attribute only
     * when the LID mode is selected, mirroring the JS {@code DROP_ATTR}
     * default for the PN mode.
     */
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

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation picks the addressing shape from the slots the
     * user carries, in priority order: phone number (inline text), username
     * (with optional {@code pin} and {@code lid} attributes), then contact
     * type (the {@code type} attribute alone). Users carrying none of these
     * slots produce {@link Optional#empty()}, matching the JS {@code null}
     * return in {@code USyncContactProtocol.getUserElement}.
     */
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

    /**
     * {@inheritDoc}
     *
     * @throws IllegalStateException {@inheritDoc} Also thrown when the
     *     required {@code type} attribute is missing on a non-error
     *     response, matching the JS {@code WALogger.ERROR(...).sendLogs("usync-contact-missing-type")}
     *     branch.
     */
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
     * Probes the optional {@code <error/>} child of a USync protocol
     * response.
     *
     * @apiNote
     * Reused by every protocol parser so the per-protocol error decode
     * lives in one place; the {@code error_backoff} attribute (in seconds)
     * is parsed into a {@link Duration} that the caller can hand back to
     * {@link com.github.auties00.cobalt.node.usync.UsyncBackoff#setProtocolBackoffMs(String, long)}.
     *
     * @param child the protocol-tagged response node
     * @return the parsed error, or empty when no {@code <error/>} child is
     *     present
     */
    public static Optional<UsyncProtocolError> parseError(Node child) {
        return child.getChild("error").map(err -> new UsyncProtocolError(
                err.getRequiredAttributeAsInt("code"),
                err.getAttributeAsString("text", ""),
                err.getAttributeAsLong("error_backoff").stream().boxed()
                        .map(Duration::ofSeconds).findFirst().orElse(null)));
    }
}
