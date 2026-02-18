package com.github.auties00.cobalt.model.bot.response;

/**
 * A sealed interface representing the content variants that an
 * {@link AIRichResponseSubMessage} can carry.
 *
 * <p>Each permitted type corresponds to one of the content types
 * supported by the WhatsApp AI rich response protocol. Using this
 * sealed interface with pattern matching guarantees exhaustive
 * handling of all content types at compile time:
 *
 * <pre>{@code
 *     switch (subMessage.content()) {
 *         case AIRichResponseText t              -> renderText(t.value());
 *         case AIRichResponseCodeMetadata c       -> renderCode(c);
 *         case AIRichResponseTableMetadata t      -> renderTable(t);
 *         case AIRichResponseGridImageMetadata g  -> renderGridImage(g);
 *         case AIRichResponseInlineImageMetadata i -> renderInlineImage(i);
 *         case AIRichResponseDynamicMetadata d    -> renderDynamic(d);
 *         case AIRichResponseLatexMetadata l      -> renderLatex(l);
 *         case AIRichResponseMapMetadata m        -> renderMap(m);
 *         case AIRichResponseContentItemsMetadata ci -> renderContentItems(ci);
 *         case null                               -> log.warn("unknown content");
 *     }
 * }</pre>
 *
 * <p>Instances of {@link AIRichResponseSubMessage} should be
 * constructed via the generated {@code AIRichResponseSubMessageBuilder},
 * which accepts a single {@code AIRichResponseSubMessageContent}
 * parameter to guarantee that exactly one payload is set and the
 * {@code messageType} discriminator is consistent.
 *
 * @see AIRichResponseSubMessage#content()
 * @see AIRichResponseSubMessage
 */
public sealed interface AIRichResponseSubMessageContent permits
        AIRichResponseText,
        AIRichResponseGridImageMetadata,
        AIRichResponseInlineImageMetadata,
        AIRichResponseCodeMetadata,
        AIRichResponseTableMetadata,
        AIRichResponseDynamicMetadata,
        AIRichResponseLatexMetadata,
        AIRichResponseMapMetadata,
        AIRichResponseContentItemsMetadata {
}
