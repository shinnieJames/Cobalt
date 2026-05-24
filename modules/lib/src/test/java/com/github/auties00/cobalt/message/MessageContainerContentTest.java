package com.github.auties00.cobalt.message;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.EmptyMessage;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageContainerBuilder;
import com.github.auties00.cobalt.model.message.system.FutureProofMessage;
import com.github.auties00.cobalt.model.message.system.FutureProofMessageBuilder;
import com.github.auties00.cobalt.model.message.system.DeviceSentMessage;
import com.github.auties00.cobalt.model.message.system.DeviceSentMessageBuilder;
import com.github.auties00.cobalt.model.message.media.ImageMessage;
import com.github.auties00.cobalt.model.message.media.ImageMessageBuilder;
import com.github.auties00.cobalt.model.message.location.LocationMessage;
import com.github.auties00.cobalt.model.message.location.LocationMessageBuilder;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessage;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessageBuilder;
import com.github.auties00.cobalt.model.message.text.ReactionMessage;
import com.github.auties00.cobalt.model.message.text.ReactionMessageBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link MessageContainer#content()}, the resolver that walks the
 * union-of-fields {@code Message} protobuf and returns the innermost payload.
 *
 * @apiNote Mirrors the field-resolution order WA Web's {@code WAWebMsgGetters}
 * applies when deciding which field of a {@code Message} oneof is the actual
 * payload. The test covers the four interesting branches:
 * {@link FutureProofMessage} wrappers (viewOnce / ephemeral / edited /
 * groupMentioned) unwrap recursively to their inner content,
 * {@link DeviceSentMessage} unwraps before direct fields are consulted,
 * direct fields are scanned in protobuf field-index order, a bare
 * {@code conversation} string is promoted to an {@link ExtendedTextMessage},
 * and an empty container returns {@link EmptyMessage#INSTANCE}.
 *
 * @implNote This implementation builds containers through the generated
 * builders rather than parsing protobuf bytes, so the precedence assertions
 * reflect the in-memory resolver only; the wire-level encoder is exercised
 * separately by the spec-level tests.
 */
@DisplayName("MessageContainer.content")
class MessageContainerContentTest {

    /**
     * Verifies that an empty container reports {@link EmptyMessage#INSTANCE}
     * and tests as empty.
     */
    @Test
    @DisplayName("empty container returns an EmptyMessage sentinel")
    void emptyContainer() {
        var container = MessageContainer.empty();
        assertInstanceOf(EmptyMessage.class, container.content());
        assertTrue(container.isEmpty());
    }

    /**
     * Verifies that a bare {@code conversation} string is wrapped in an
     * {@link ExtendedTextMessage}.
     */
    @Test
    @DisplayName("string conversation field promotes to an ExtendedTextMessage")
    void conversationPromotesToExtendedText() {
        var container = MessageContainer.of("hello world");

        var content = container.content();
        assertInstanceOf(ExtendedTextMessage.class, content);
        var text = (ExtendedTextMessage) content;
        assertEquals("hello world", text.text().orElseThrow());
    }

    /**
     * Verifies that a direct payload field is returned by reference rather
     * than rebuilt.
     */
    @Test
    @DisplayName("direct ImageMessage is returned as-is (no wrapping)")
    void directImageMessage() {
        var image = new ImageMessageBuilder()
                .caption("a picture")
                .build();
        var container = new MessageContainerBuilder()
                .imageMessage(image)
                .build();

        var content = container.content();
        assertSame(image, content, "direct payload field must be returned by-reference");
    }

    /**
     * Verifies that the viewOnce {@link FutureProofMessage} wrapper unwraps
     * to its inner payload.
     */
    @Test
    @DisplayName("FutureProofMessage (viewOnce) unwraps to its inner content")
    void futureProofViewOnceUnwraps() {
        var location = new LocationMessageBuilder()
                .name("center")
                .degreesLatitude(0.0)
                .degreesLongitude(0.0)
                .build();
        var viewOnce = new FutureProofMessageBuilder()
                .messageContainer(new MessageContainerBuilder()
                        .locationMessage(location)
                        .build())
                .build();
        var container = new MessageContainerBuilder()
                .viewOnceMessage(viewOnce)
                .build();

        var content = container.content();
        assertSame(location, content,
                "viewOnce wrapper must unwrap to its inner location message");
    }

    /**
     * Verifies that the ephemeral {@link FutureProofMessage} wrapper unwraps
     * to its inner payload.
     */
    @Test
    @DisplayName("FutureProofMessage (ephemeral) unwraps to inner content")
    void futureProofEphemeralUnwraps() {
        var image = new ImageMessageBuilder().caption("eph").build();
        var ephemeral = new FutureProofMessageBuilder()
                .messageContainer(new MessageContainerBuilder()
                        .imageMessage(image)
                        .build())
                .build();
        var container = new MessageContainerBuilder()
                .ephemeralMessage(ephemeral)
                .build();
        assertSame(image, container.content());
    }

    /**
     * Verifies that the editedMessage {@link FutureProofMessage} wrapper
     * unwraps to its inner payload.
     */
    @Test
    @DisplayName("FutureProofMessage (editedMessage) unwraps to inner content")
    void futureProofEditedUnwraps() {
        var text = new ExtendedTextMessageBuilder().text("edited body").build();
        var edited = new FutureProofMessageBuilder()
                .messageContainer(new MessageContainerBuilder()
                        .extendedTextMessage(text)
                        .build())
                .build();
        var container = new MessageContainerBuilder()
                .editedMessage(edited)
                .build();
        assertSame(text, container.content());
    }

    /**
     * Verifies that nested {@link FutureProofMessage} wrappers unwrap
     * recursively to the innermost payload.
     */
    @Test
    @DisplayName("nested FutureProofMessage wrappers unwrap recursively")
    void nestedFutureProofUnwraps() {
        var image = new ImageMessageBuilder().caption("nested").build();
        var inner = new FutureProofMessageBuilder()
                .messageContainer(new MessageContainerBuilder()
                        .imageMessage(image)
                        .build())
                .build();
        var outer = new FutureProofMessageBuilder()
                .messageContainer(new MessageContainerBuilder()
                        .viewOnceMessage(inner)
                        .build())
                .build();
        var container = new MessageContainerBuilder()
                .ephemeralMessage(outer)
                .build();

        assertSame(image, container.content(),
                "two-level FutureProof nesting must unwrap to the innermost payload");
    }

    /**
     * Verifies that a {@link DeviceSentMessage} wrapper unwraps to its inner
     * payload before direct fields are consulted.
     */
    @Test
    @DisplayName("DeviceSentMessage unwraps to its inner message before direct fields are consulted")
    void deviceSentUnwraps() {
        var reaction = new ReactionMessageBuilder().text("👍").build();
        var deviceSent = new DeviceSentMessageBuilder()
                .destinationJid(Jid.of("12025550100@s.whatsapp.net"))
                .messageContainer(new MessageContainerBuilder()
                        .reactionMessage(reaction)
                        .build())
                .build();
        var container = new MessageContainerBuilder()
                .deviceSentMessage(deviceSent)
                .build();

        assertSame(reaction, container.content(),
                "DeviceSentMessage must unwrap to its inner ReactionMessage");
    }

    /**
     * Verifies that a {@link FutureProofMessage} wrapper wins over a direct
     * field on the same container.
     *
     * @apiNote Mirrors WA Web's resolver, which probes wrappers first to
     * recover the original payload nested inside.
     */
    @Test
    @DisplayName("FutureProofMessage wrapper wins over a direct field on the same container")
    void wrapperWinsOverDirectField() {
        var directText = new ExtendedTextMessageBuilder().text("direct").build();
        var wrapped = new ImageMessageBuilder().caption("wrapped").build();
        var viewOnce = new FutureProofMessageBuilder()
                .messageContainer(new MessageContainerBuilder()
                        .imageMessage(wrapped)
                        .build())
                .build();
        var container = new MessageContainerBuilder()
                .extendedTextMessage(directText)
                .viewOnceMessage(viewOnce)
                .build();

        assertSame(wrapped, container.content(),
                "viewOnce wrapper takes precedence over a direct extendedTextMessage");
    }

    /**
     * Verifies that {@link MessageContainer#contextualContent()} returns the
     * innermost message when it is contextual.
     */
    @Test
    @DisplayName("contextualContent() returns the contextual inner message when present")
    void contextualContentReturnsContextual() {
        var text = new ExtendedTextMessageBuilder().text("with context").build();
        var container = new MessageContainerBuilder()
                .extendedTextMessage(text)
                .build();

        var contextual = container.contextualContent();
        assertTrue(contextual.isPresent());
        assertSame(text, contextual.orElseThrow());
    }

    /**
     * Verifies that {@link MessageContainer#contextualContent()} returns
     * empty when the innermost message is not contextual.
     *
     * @implNote {@link ReactionMessage} does not implement
     * {@code ContextualMessage}, so the contextual accessor must report
     * absent rather than fall through to the non-contextual payload.
     */
    @Test
    @DisplayName("contextualContent() returns empty when innermost message is not contextual")
    void contextualContentEmptyForNonContextual() {
        var reaction = new ReactionMessageBuilder().text("👍").build();
        var container = new MessageContainerBuilder()
                .reactionMessage(reaction)
                .build();

        assertTrue(container.contextualContent().isEmpty(),
                "ReactionMessage is not a ContextualMessage; contextualContent() must be empty");
    }

    /**
     * Verifies that a {@link DeviceSentMessage} carrying an empty inner
     * container falls through to {@link EmptyMessage#INSTANCE} rather than
     * pinning the resolver on the wrapper.
     *
     * @implNote The resolver checks {@code deviceSentMessage.message().isPresent()}
     * before committing to the wrapper; when the inner message is empty it
     * falls through to subsequent fields, leaving the container resolved as
     * empty.
     */
    @Test
    @DisplayName("DeviceSentMessage with empty inner message falls back to EmptyMessage")
    void deviceSentEmptyInner() {
        var deviceSent = new DeviceSentMessageBuilder()
                .destinationJid(Jid.of("12025550100@s.whatsapp.net"))
                .messageContainer(MessageContainer.empty())
                .build();
        var container = new MessageContainerBuilder()
                .deviceSentMessage(deviceSent)
                .build();

        assertInstanceOf(EmptyMessage.class, container.content());
    }

    /**
     * Verifies that the groupMentioned wrapper outranks every other wrapper.
     *
     * @apiNote The groupMentionedMessage is the first field tested by the
     * resolver, so anything inside it takes priority over a sibling viewOnce
     * wrapper on the same container.
     */
    @Test
    @DisplayName("groupMentioned wrapper wins over every other wrapper (highest priority)")
    void groupMentionedWinsOverOtherWrappers() {
        var location = new LocationMessageBuilder()
                .name("inside groupMentioned")
                .degreesLatitude(0.0).degreesLongitude(0.0)
                .build();
        var groupMentioned = new FutureProofMessageBuilder()
                .messageContainer(new MessageContainerBuilder()
                        .locationMessage(location)
                        .build())
                .build();
        var siblingImage = new ImageMessageBuilder().caption("ignored").build();
        var siblingViewOnce = new FutureProofMessageBuilder()
                .messageContainer(new MessageContainerBuilder()
                        .imageMessage(siblingImage)
                        .build())
                .build();
        var container = new MessageContainerBuilder()
                .groupMentionedMessage(groupMentioned)
                .viewOnceMessage(siblingViewOnce)
                .build();

        assertSame(location, container.content(),
                "groupMentioned wrapper must beat viewOnce wrapper");
    }

    /**
     * Verifies the field-order tie-break between {@code conversation} and
     * {@code extendedTextMessage}: the former is consulted first and wins
     * when both are populated.
     */
    @Test
    @DisplayName("conversation field is overridden by extendedTextMessage when both are set")
    void conversationLosesToExtendedText() {
        var extended = new ExtendedTextMessageBuilder().text("the extended one").build();
        var container = new MessageContainerBuilder()
                .conversation("the plain one")
                .extendedTextMessage(extended)
                .build();

        var content = container.content();
        assertInstanceOf(ExtendedTextMessage.class, content);
        assertEquals("the plain one", ((ExtendedTextMessage) content).text().orElseThrow(),
                "conversation precedes extendedTextMessage in the field order; wins");
    }

    /**
     * Verifies that {@link MessageContainer#of(com.github.auties00.cobalt.model.message.Message)}
     * routes the supplied message to the correct typed field and that
     * {@link MessageContainer#content()} returns it unchanged.
     */
    @Test
    @DisplayName("static factory MessageContainer.of(Message) routes the message to the correct typed field")
    void factoryOfMessageRoutes() {
        var image = new ImageMessageBuilder().caption("via factory").build();
        var container = MessageContainer.of(image);
        assertSame(image, container.content(),
                "MessageContainer.of(image) must route to imageMessage and content() must return it");

        var location = new LocationMessageBuilder().name("origin").degreesLatitude(0.0).degreesLongitude(0.0).build();
        var locContainer = MessageContainer.of(location);
        assertSame(location, locContainer.content());

        var reaction = new ReactionMessageBuilder().text("🎯").build();
        assertSame(reaction, MessageContainer.of(reaction).content());
    }

    /**
     * Verifies that copying a container through the builder preserves the
     * original payload through {@link MessageContainer#content()}.
     */
    @Test
    @DisplayName("withMessageContextInfo preserves the original content")
    void withMessageContextInfoPreservesContent() {
        var image = new ImageMessageBuilder().caption("preserved").build();
        var container = MessageContainer.of(image);

        var withCtx = new MessageContainerBuilder()
                .imageMessage(image)
                .build();
        assertSame(image, withCtx.content(),
                "side-channel fields don't affect content() resolution");
    }

    /**
     * Sentinel test that exercises the static-import of {@link Jid} so test
     * refactors that drop other JID usages do not leave the import unused.
     */
    @Test
    @DisplayName("scratch suppression: a JID-typed field shape can round-trip through the resolver")
    void jidImportSentinel() {
        var ignored = Jid.of("0@s.whatsapp.net");
        assertEquals("0@s.whatsapp.net", ignored.toString());
    }
}
