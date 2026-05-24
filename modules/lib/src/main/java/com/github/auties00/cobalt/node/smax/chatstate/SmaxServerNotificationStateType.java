package com.github.auties00.cobalt.node.smax.chatstate;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import java.util.Objects;
import java.util.Optional;

/**
 * Sealed disjunction over the two inbound chat-state indicators: the peer
 * is typing ({@link Composing}) or has stopped ({@link Paused}).
 *
 * @apiNote
 * Cobalt's analogue of {@code WAHandleChatStateProtocol.parseChatStatus}
 * derives a UI-facing {@code "composing"} / {@code "recording_audio"} /
 * {@code "paused"} status from this disjunction: {@link Paused} maps to
 * {@code "paused"}, {@link Composing} with empty {@link Composing#composingMedia()}
 * maps to {@code "composing"}, and {@link Composing} with
 * {@code composingMedia == "audio"} maps to {@code "recording_audio"}.
 */
@WhatsAppWebModule(moduleName = "WASmaxInChatstateStateTypes")
public sealed interface SmaxServerNotificationStateType permits SmaxServerNotificationStateType.Composing, SmaxServerNotificationStateType.Paused {

    /**
     * Parses an inbound stanza into the first matching variant.
     *
     * @implNote
     * This implementation mirrors {@code parseStateTypes}: it tries
     * {@link Composing} first and falls back to {@link Paused}, returning
     * the first successful parse.
     *
     * @param node the inbound {@code <chatstate/>} stanza
     * @return an {@link Optional} carrying the parsed variant, or empty on no-match
     */
    @WhatsAppWebExport(moduleName = "WASmaxInChatstateStateTypes",
            exports = "parseStateTypes", adaptation = WhatsAppAdaptation.ADAPTED)
    static Optional<? extends SmaxServerNotificationStateType> of(Node node) {
        Objects.requireNonNull(node, "node cannot be null");
        var composing = Composing.of(node);
        if (composing.isPresent()) {
            return composing;
        }
        return Paused.of(node);
    }

    /**
     * The variant indicating the peer is currently typing or recording
     * audio.
     *
     * @apiNote
     * The presence and value of {@link #composingMedia()} distinguishes
     * text typing (empty) from voice-note recording ({@code "audio"}).
     */
    @WhatsAppWebModule(moduleName = "WASmaxInChatstateComposingMixin")
    final class Composing implements SmaxServerNotificationStateType {
        /**
         * The optional {@code media} attribute on the {@code <composing>}
         * child. Either {@code "audio"} for voice-note recording or
         * {@code null} for plain text typing.
         */
        private final String composingMedia;

        /**
         * Constructs a {@link Composing} variant.
         *
         * @param composingMedia the optional {@code media} attribute; may be {@code null}
         */
        public Composing(String composingMedia) {
            this.composingMedia = composingMedia;
        }

        /**
         * Returns the optional {@code media} attribute.
         *
         * @return an {@link Optional} carrying {@code "audio"} when the peer
         *         is recording a voice note, or empty when typing text
         */
        public Optional<String> composingMedia() {
            return Optional.ofNullable(composingMedia);
        }

        /**
         * Parses an inbound stanza into a {@link Composing} variant.
         *
         * @implNote
         * This implementation mirrors {@code parseComposingMixin}: it
         * asserts the {@code <chatstate>} tag, requires an inner
         * {@code <composing>} child, and accepts only an absent
         * {@code media} attribute or one whose value is the literal
         * {@code "audio"}.
         *
         * @param node the inbound chatstate stanza
         * @return an {@link Optional} carrying the parsed variant, or empty on schema mismatch
         */
        @WhatsAppWebExport(moduleName = "WASmaxInChatstateComposingMixin",
                exports = "parseComposingMixin",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Composing> of(Node node) {
            if (!node.hasDescription("chatstate")) {
                return Optional.empty();
            }
            var composingChild = node.getChild("composing").orElse(null);
            if (composingChild == null) {
                return Optional.empty();
            }
            var media = composingChild.getAttributeAsString("media").orElse(null);
            if (media != null && !"audio".equals(media)) {
                return Optional.empty();
            }
            return Optional.of(new Composing(media));
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (Composing) obj;
            return Objects.equals(this.composingMedia, that.composingMedia);
        }

        @Override
        public int hashCode() {
            return Objects.hash(composingMedia);
        }

        @Override
        public String toString() {
            return "SmaxServerNotificationStateType.Composing[composingMedia="
                    + composingMedia + ']';
        }
    }

    /**
     * The variant indicating the peer has stopped typing.
     *
     * @apiNote
     * Clears the typing dot from the chat UI; carries no payload of its
     * own.
     */
    @WhatsAppWebModule(moduleName = "WASmaxInChatstatePausedMixin")
    final class Paused implements SmaxServerNotificationStateType {
        /**
         * Constructs the empty {@link Paused} variant.
         */
        public Paused() {
        }

        /**
         * Parses an inbound stanza into a {@link Paused} variant.
         *
         * @implNote
         * This implementation mirrors {@code parsePausedMixin}: it asserts
         * the {@code <chatstate>} tag and requires the inner
         * {@code <paused>} child to be present.
         *
         * @param node the inbound chatstate stanza
         * @return an {@link Optional} carrying the parsed variant, or empty on schema mismatch
         */
        @WhatsAppWebExport(moduleName = "WASmaxInChatstatePausedMixin",
                exports = "parsePausedMixin",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Paused> of(Node node) {
            if (!node.hasDescription("chatstate")) {
                return Optional.empty();
            }
            if (node.getChild("paused").isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Paused());
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            return obj != null && obj.getClass() == this.getClass();
        }

        @Override
        public int hashCode() {
            return Paused.class.hashCode();
        }

        @Override
        public String toString() {
            return "SmaxServerNotificationStateType.Paused[]";
        }
    }
}
