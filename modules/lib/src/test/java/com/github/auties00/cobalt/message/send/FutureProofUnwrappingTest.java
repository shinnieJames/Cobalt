package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.model.message.EmptyMessage;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageContainerBuilder;
import com.github.auties00.cobalt.model.message.FutureProofMessageType;
import com.github.auties00.cobalt.model.message.media.ImageMessageBuilder;
import com.github.auties00.cobalt.model.message.system.FutureProofMessageBuilder;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessageBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for the {@code FutureProofMessage} wrapping path on
 * {@link MessageContainer}.
 *
 * <p>{@code FutureProofMessage} envelopes 19 different
 * {@link MessageContainer} fields used as forward-compat wrappers
 * (viewOnce, ephemeral, editedMessage, groupMentioned, status*, lottie
 * sticker, etc.). All of them share the resolver behaviour: the wrapper
 * is opaque on the wire, and {@link MessageContainer#content()} must
 * unwrap to the innermost message.
 *
 * <p>This class parameterises every wrap-index variant and asserts:
 *
 * <ul>
 *   <li>{@link MessageContainer#content()} returns the innermost payload
 *       by reference, regardless of the wrapper field used.</li>
 *   <li>{@link MessageContainer#futureProofContentType()} reports the
 *       correct {@link FutureProofMessageType} for each wrapper.</li>
 *   <li>Empty wrappers (no inner content) fall back to
 *       {@link EmptyMessage}.</li>
 * </ul>
 */
@DisplayName("FutureProofMessage unwrapping")
class FutureProofUnwrappingTest {

    /**
     * One test cell per wrapper field — pairs the
     * {@link FutureProofMessageType} enum value with the corresponding
     * setter on {@link MessageContainerBuilder}.
     *
     * @return the parameterised test arguments
     */
    static Stream<Arguments> wrappers() {
        return Stream.of(
                wrapperArg(FutureProofMessageType.GROUP_MENTIONED, MessageContainerBuilder::groupMentionedMessage),
                wrapperArg(FutureProofMessageType.DOCUMENT_WITH_CAPTION, MessageContainerBuilder::documentWithCaptionMessage),
                wrapperArg(FutureProofMessageType.VIEW_ONCE, MessageContainerBuilder::viewOnceMessage),
                wrapperArg(FutureProofMessageType.EPHEMERAL, MessageContainerBuilder::ephemeralMessage),
                wrapperArg(FutureProofMessageType.EDITED, MessageContainerBuilder::editedMessage),
                wrapperArg(FutureProofMessageType.BOT_INVOKE, MessageContainerBuilder::botInvokeMessage),
                wrapperArg(FutureProofMessageType.LOTTIE_STICKER, MessageContainerBuilder::lottieStickerMessage),
                wrapperArg(FutureProofMessageType.EVENT_COVER_IMAGE, MessageContainerBuilder::eventCoverImage),
                wrapperArg(FutureProofMessageType.STATUS_MENTION, MessageContainerBuilder::statusMentionMessage),
                wrapperArg(FutureProofMessageType.POLL_CREATION_OPTION_IMAGE, MessageContainerBuilder::pollCreationOptionImageMessage),
                wrapperArg(FutureProofMessageType.ASSOCIATED_CHILD, MessageContainerBuilder::associatedChildMessage),
                wrapperArg(FutureProofMessageType.GROUP_STATUS_MENTION, MessageContainerBuilder::groupStatusMentionMessage),
                wrapperArg(FutureProofMessageType.POLL_CREATION, MessageContainerBuilder::pollCreationMessageV4),
                wrapperArg(FutureProofMessageType.STATUS_ADD_YOURS, MessageContainerBuilder::statusAddYours),
                wrapperArg(FutureProofMessageType.GROUP_STATUS, MessageContainerBuilder::groupStatusMessage),
                wrapperArg(FutureProofMessageType.LIMIT_SHARING, MessageContainerBuilder::limitSharingMessage),
                wrapperArg(FutureProofMessageType.BOT_TASK, MessageContainerBuilder::botTaskMessage),
                wrapperArg(FutureProofMessageType.QUESTION, MessageContainerBuilder::questionMessage),
                wrapperArg(FutureProofMessageType.BOT_FORWARDED, MessageContainerBuilder::botForwardedMessage),
                wrapperArg(FutureProofMessageType.QUESTION_REPLY, MessageContainerBuilder::questionReplyMessage),
                wrapperArg(FutureProofMessageType.NEWSLETTER_ADMIN_PROFILE, MessageContainerBuilder::newsletterAdminProfileMessage)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wrappers")
    @DisplayName("every FutureProof wrapper unwraps to its inner payload by reference")
    void wrapperUnwrapsToInner(FutureProofMessageType type, WrapperSetter setter) {
        var inner = new ImageMessageBuilder().caption("inner for " + type).build();
        var wrap = new FutureProofMessageBuilder()
                .messageContainer(new MessageContainerBuilder().imageMessage(inner).build())
                .build();
        var builder = new MessageContainerBuilder();
        setter.accept(builder, wrap);
        var container = builder.build();

        assertSame(inner, container.content(),
                "wrapper " + type + " must unwrap to its inner ImageMessage");
        assertEquals(type, container.futureProofContentType(),
                "futureProofContentType() must report " + type + " for this wrapper");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("wrappers")
    @DisplayName("every FutureProof wrapper with an empty inner falls back to EmptyMessage")
    void wrapperWithEmptyInnerFallsBack(FutureProofMessageType type, WrapperSetter setter) {
        var emptyWrap = new FutureProofMessageBuilder()
                .messageContainer(MessageContainer.empty())
                .build();
        var builder = new MessageContainerBuilder();
        setter.accept(builder, emptyWrap);
        var container = builder.build();

        assertInstanceOf(EmptyMessage.class, container.content(),
                "wrapper " + type + " with empty inner must unwrap to EmptyMessage sentinel");
    }

    @Test
    @DisplayName("nested wrappers (viewOnce inside ephemeral) unwrap recursively")
    void nestedWrappersUnwrap() {
        var inner = new ExtendedTextMessageBuilder().text("deeply nested").build();
        var view = new FutureProofMessageBuilder()
                .messageContainer(new MessageContainerBuilder().extendedTextMessage(inner).build())
                .build();
        var ephemeral = new FutureProofMessageBuilder()
                .messageContainer(new MessageContainerBuilder().viewOnceMessage(view).build())
                .build();
        var container = new MessageContainerBuilder().ephemeralMessage(ephemeral).build();

        assertSame(inner, container.content(),
                "double wrap (ephemeral→viewOnce→extendedText) must unwrap to innermost");
        // The outermost futureProofContentType reports the OUTER wrapper, not the inner one.
        assertEquals(FutureProofMessageType.EPHEMERAL, container.futureProofContentType());
    }

    @Test
    @DisplayName("container with no payload at all returns FutureProofMessageType.NONE")
    void noWrapperGivesNone() {
        var container = MessageContainer.empty();
        assertEquals(FutureProofMessageType.NONE, container.futureProofContentType());
    }

    @Test
    @DisplayName("container with a direct (non-wrapper) field returns NONE")
    void directFieldGivesNone() {
        var image = new ImageMessageBuilder().caption("direct").build();
        var container = new MessageContainerBuilder().imageMessage(image).build();
        assertEquals(FutureProofMessageType.NONE, container.futureProofContentType(),
                "imageMessage is a direct field, not a wrapper — futureProofContentType is NONE");
    }

    /**
     * Builds a parameterised argument tuple for a wrapper case.
     *
     * @param type    the corresponding {@link FutureProofMessageType}
     * @param setter  the builder setter that places a wrapper into the field
     * @return the JUnit parameter tuple
     */
    private static Arguments wrapperArg(
            FutureProofMessageType type,
            WrapperSetter setter
    ) {
        return Arguments.of(type, setter);
    }

    /**
     * A typed alias for the {@link BiConsumer} that maps a wrapper into a
     * {@link MessageContainerBuilder} field.
     */
    @FunctionalInterface
    interface WrapperSetter extends BiConsumer<MessageContainerBuilder, com.github.auties00.cobalt.model.message.system.FutureProofMessage> {
    }
}
