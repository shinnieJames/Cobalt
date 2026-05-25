package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.model.message.EmptyMessage;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageContainerBuilder;
import com.github.auties00.cobalt.model.message.FutureProofMessageType;
import com.github.auties00.cobalt.model.message.media.ImageMessageBuilder;
import com.github.auties00.cobalt.model.message.system.FutureProofMessage;
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
 * Covers {@link MessageContainer} unwrapping across every
 * {@code FutureProofMessage} wrapper field: {@link MessageContainer#content()}
 * must reach the innermost payload by reference while
 * {@link MessageContainer#futureProofContentType()} reports the outermost
 * wrapper. The wrapper variants are driven from one shared
 * {@link MethodSource}, and empty wrappers are expected to fall back to the
 * {@link EmptyMessage} sentinel.
 */
@DisplayName("FutureProofMessage unwrapping")
class FutureProofUnwrappingTest {

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
                "double wrap (ephemeral -> viewOnce -> extendedText) must unwrap to innermost");
        // futureProofContentType reports the OUTER wrapper, not the inner one.
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
                "imageMessage is a direct field, not a wrapper, so futureProofContentType is NONE");
    }

    private static Arguments wrapperArg(
            FutureProofMessageType type,
            WrapperSetter setter
    ) {
        return Arguments.of(type, setter);
    }

    @FunctionalInterface
    interface WrapperSetter extends BiConsumer<MessageContainerBuilder, FutureProofMessage> {
    }
}
