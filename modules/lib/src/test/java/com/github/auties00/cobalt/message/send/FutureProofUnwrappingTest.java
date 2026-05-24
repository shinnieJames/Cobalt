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
 * Exercises the {@code FutureProofMessage} unwrapping path on every
 * {@link MessageContainer} wrapper field.
 *
 * @apiNote
 * {@code FutureProofMessage} envelopes a large set of forward-compatible
 * wrapper fields (viewOnce, ephemeral, editedMessage, groupMentioned,
 * status mentions, lottie sticker, document-with-caption, and the bot,
 * poll, event, and newsletter wrappers). The receiver-side resolver and
 * every send-path consumer rely on {@link MessageContainer#content()}
 * unwrapping to the innermost payload while
 * {@link MessageContainer#futureProofContentType()} reports which wrapper
 * was used; the parity asserted here is the load-bearing invariant for the
 * outbound stanza-type and media-type resolvers in
 * {@link MessageSender}.
 *
 * @implNote
 * This implementation parameterises the wrap-index variants from one
 * shared {@link MethodSource}; the parameterised cells assert that the
 * unwrap returns the inner payload by reference (not by structural
 * equality) and that empty wrappers fall back to the
 * {@link EmptyMessage} sentinel.
 */
@DisplayName("FutureProofMessage unwrapping")
class FutureProofUnwrappingTest {

    /**
     * Returns one parameter tuple per {@code FutureProofMessage} wrapper
     * field.
     *
     * @apiNote
     * Feeds {@link #wrapperUnwrapsToInner(FutureProofMessageType, WrapperSetter)}
     * and {@link #wrapperWithEmptyInnerFallsBack(FutureProofMessageType, WrapperSetter)};
     * each tuple pairs the {@link FutureProofMessageType} with the
     * matching {@link MessageContainerBuilder} setter.
     *
     * @return the parameter {@link Stream}
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

    /**
     * Asserts that every wrapper unwraps to its inner payload by reference.
     */
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

    /**
     * Asserts that every wrapper with an empty inner falls back to
     * {@link EmptyMessage}.
     */
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

    /**
     * Asserts that nested wrappers unwrap recursively to the innermost
     * payload.
     */
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

    /**
     * Asserts that an empty container reports
     * {@link FutureProofMessageType#NONE}.
     */
    @Test
    @DisplayName("container with no payload at all returns FutureProofMessageType.NONE")
    void noWrapperGivesNone() {
        var container = MessageContainer.empty();
        assertEquals(FutureProofMessageType.NONE, container.futureProofContentType());
    }

    /**
     * Asserts that a direct (non-wrapper) field reports
     * {@link FutureProofMessageType#NONE}.
     */
    @Test
    @DisplayName("container with a direct (non-wrapper) field returns NONE")
    void directFieldGivesNone() {
        var image = new ImageMessageBuilder().caption("direct").build();
        var container = new MessageContainerBuilder().imageMessage(image).build();
        assertEquals(FutureProofMessageType.NONE, container.futureProofContentType(),
                "imageMessage is a direct field, not a wrapper, so futureProofContentType is NONE");
    }

    /**
     * Builds a parameter tuple for a single wrapper case.
     *
     * @apiNote
     * Helper for {@link #wrappers()}; pairs the
     * {@link FutureProofMessageType} with the {@link WrapperSetter} that
     * plants the wrapper on the {@link MessageContainerBuilder}.
     *
     * @param type   the {@link FutureProofMessageType} reported by the
     *               wrapper
     * @param setter the {@link WrapperSetter} that places the wrapper into
     *               the right builder field
     * @return the JUnit parameter tuple
     */
    private static Arguments wrapperArg(
            FutureProofMessageType type,
            WrapperSetter setter
    ) {
        return Arguments.of(type, setter);
    }

    /**
     * Typed {@link BiConsumer} that places a
     * {@code FutureProofMessage} wrapper onto a
     * {@link MessageContainerBuilder}.
     *
     * @apiNote
     * Used as the second parameter of every parameterised cell to keep the
     * setter strongly typed at the call site.
     */
    @FunctionalInterface
    interface WrapperSetter extends BiConsumer<MessageContainerBuilder, FutureProofMessage> {
    }
}
