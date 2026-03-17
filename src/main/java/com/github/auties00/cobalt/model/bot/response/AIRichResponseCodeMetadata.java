package com.github.auties00.cobalt.model.bot.response;

import java.util.Collections;
import java.util.List;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

/**
 * Metadata for a syntax-highlighted code block fragment within an
 * AI rich response.
 *
 * <p>The code block is decomposed into a sequence of
 * {@link AIRichResponseCodeBlock} tokens, each carrying a
 * {@linkplain AIRichResponseCodeHighlightType highlight type} that
 * the client uses for syntax colouring. The
 * {@linkplain #codeLanguage() code language} identifies the
 * programming language so the client can apply the correct syntax
 * theme.
 */
@ProtobufMessage(name = "AIRichResponseCodeMetadata")
public final class AIRichResponseCodeMetadata implements AIRichResponseSubMessageContent {
    /**
     * The programming language of the code in this block,
     * used for syntax highlighting theme selection.
     *
     * <p>Example values: {@code "python"}, {@code "javascript"},
     * {@code "java"}, {@code "cpp"}, {@code "html"}, {@code "sql"}
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String codeLanguage;

    /**
     * The ordered list of syntax-highlighted tokens that compose
     * this code block.
     *
     * <p>Clients should concatenate the
     * {@linkplain AIRichResponseCodeBlock#codeContent() content} of
     * each block and apply the colour associated with its
     * {@linkplain AIRichResponseCodeBlock#highlightType() highlight type}.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    List<AIRichResponseCodeBlock> codeBlocks;


    AIRichResponseCodeMetadata(String codeLanguage, List<AIRichResponseCodeBlock> codeBlocks) {
        this.codeLanguage = codeLanguage;
        this.codeBlocks = codeBlocks;
    }

    /**
     * Returns the programming language of the code in this block.
     *
     * @return an {@link Optional} containing the language identifier,
     *         or empty if not set
     */
    public Optional<String> codeLanguage() {
        return Optional.ofNullable(codeLanguage);
    }

    /**
     * Returns the ordered list of syntax-highlighted tokens in this
     * code block.
     *
     * @return an unmodifiable list of code blocks, never {@code null}
     */
    public List<AIRichResponseCodeBlock> codeBlocks() {
        return codeBlocks == null ? List.of() : Collections.unmodifiableList(codeBlocks);
    }

    /**
     * Sets the programming language of the code in this block.
     *
     * @param codeLanguage the language identifier to set
     */
    public void setCodeLanguage(String codeLanguage) {
        this.codeLanguage = codeLanguage;
    }

    /**
     * Sets the ordered list of syntax-highlighted tokens in this
     * code block.
     *
     * @param codeBlocks the code blocks to set
     */
    public void setCodeBlocks(List<AIRichResponseCodeBlock> codeBlocks) {
        this.codeBlocks = codeBlocks;
    }

    /**
     * A syntax highlight category for a token within a code block.
     *
     * <p>Clients map each constant to a colour in their syntax
     * highlighting theme.
     */
    @ProtobufEnum(name = "AIRichResponseCodeMetadata.AIRichResponseCodeHighlightType")
    public static enum AIRichResponseCodeHighlightType {
        /**
         * Default text with no special highlighting applied.
         */
        DEFAULT(0),

        /**
         * A language keyword (e.g. {@code if}, {@code class},
         * {@code return}).
         */
        KEYWORD(1),

        /**
         * A method or function name.
         */
        METHOD(2),

        /**
         * A string literal.
         */
        STRING(3),

        /**
         * A numeric literal.
         */
        NUMBER(4),

        /**
         * A code comment.
         */
        COMMENT(5);

        AIRichResponseCodeHighlightType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        /**
         * Returns the protobuf index associated with this highlight type.
         *
         * @return the protobuf index
         */
        public int index() {
            return this.index;
        }
    }

    /**
     * A single syntax-highlighted token within a code block.
     *
     * <p>Each token carries a fragment of source code text and a
     * {@linkplain #highlightType() highlight type} that determines
     * how the client renders it (e.g. keyword colour, string colour).
     */
    @ProtobufMessage(name = "AIRichResponseCodeMetadata.AIRichResponseCodeBlock")
    public static final class AIRichResponseCodeBlock {
        /**
         * The syntax highlight category for this token.
         */
        @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
        AIRichResponseCodeHighlightType highlightType;

        /**
         * The source code text of this token.
         *
         * <p>Example: {@code "def hello_world():"}
         */
        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String codeContent;


        AIRichResponseCodeBlock(AIRichResponseCodeHighlightType highlightType, String codeContent) {
            this.highlightType = highlightType;
            this.codeContent = codeContent;
        }

        /**
         * Returns the syntax highlight category for this token.
         *
         * @return an {@link Optional} containing the highlight type,
         *         or empty if not set
         */
        public Optional<AIRichResponseCodeHighlightType> highlightType() {
            return Optional.ofNullable(highlightType);
        }

        /**
         * Returns the source code text of this token.
         *
         * @return an {@link Optional} containing the code content,
         *         or empty if not set
         */
        public Optional<String> codeContent() {
            return Optional.ofNullable(codeContent);
        }

        /**
         * Sets the syntax highlight category for this token.
         *
         * @param highlightType the highlight type to set
         */
        public void setHighlightType(AIRichResponseCodeHighlightType highlightType) {
            this.highlightType = highlightType;
    }

        /**
         * Sets the source code text of this token.
         *
         * @param codeContent the code content to set
         */
        public void setCodeContent(String codeContent) {
            this.codeContent = codeContent;
    }
    }
}
