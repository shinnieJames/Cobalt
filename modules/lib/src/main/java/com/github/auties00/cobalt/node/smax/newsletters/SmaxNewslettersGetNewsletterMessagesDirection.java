package com.github.auties00.cobalt.node.smax.newsletters;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Sealed disjunction over the optional pagination cursor of a
 * {@link SmaxNewslettersGetNewsletterMessagesRequest}.
 *
 * @apiNote
 * Pick this when scrolling a newsletter's message history; omitting
 * the cursor altogether requests the latest slice. WA Web's Channels
 * surface (see
 * {@code WAWebNewsletterGetMessagesQueryJob.queryNewsletterMessagesByJid})
 * sets {@link Before} when the user scrolls up into older content.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutNewslettersMessageDirections")
public sealed interface SmaxNewslettersGetNewsletterMessagesDirection permits SmaxNewslettersGetNewsletterMessagesDirection.Before, SmaxNewslettersGetNewsletterMessagesDirection.After {

    /**
     * The variant that walks backwards from a pivot server-id.
     *
     * @apiNote
     * Selects messages with server-ids strictly less than
     * {@link #pivot()}, materialised as the {@code before} attribute on
     * the wire {@code <messages>} element. This is the dominant cursor
     * direction in WA Web's history-scroll-up UI.
     */
    @WhatsAppWebModule(moduleName = "WASmaxOutNewslettersBeforeMixinMixin")
    final class Before implements SmaxNewslettersGetNewsletterMessagesDirection {
        /**
         * The server-id pivot below which messages are returned.
         */
        private final long pivot;

        /**
         * Constructs a backward-walking cursor at the given pivot.
         *
         * @apiNote
         * Used when paginating older newsletter messages, typically
         * with the lowest-known server-id of the previous slice.
         *
         * @param pivot the server-id pivot; the relay returns messages
         *              strictly less than this value
         */
        public Before(long pivot) {
            this.pivot = pivot;
        }

        /**
         * Returns the server-id pivot for this cursor.
         *
         * @return the pivot below which messages are fetched
         */
        public long pivot() {
            return pivot;
        }

        /**
         * Compares two cursors for value equality on {@link #pivot()}.
         *
         * @param obj the reference object to compare against
         * @return {@code true} when {@code obj} is a {@link Before}
         *         carrying the same pivot
         */
        @Override
        public boolean equals(Object obj) {
            return obj instanceof Before that && this.pivot == that.pivot;
        }

        /**
         * Returns the hash code derived from {@link #pivot()}.
         *
         * @return the {@link Long#hashCode(long)} of {@link #pivot()}
         */
        @Override
        public int hashCode() {
            return Long.hashCode(pivot);
        }

        /**
         * Returns a debug representation including the pivot.
         *
         * @return a record-like rendering of this cursor
         */
        @Override
        public String toString() {
            return "SmaxNewslettersGetNewsletterMessagesDirection.Before[pivot=" + pivot + ']';
        }
    }

    /**
     * The variant that walks forwards from a pivot server-id.
     *
     * @apiNote
     * Selects messages with server-ids strictly greater than
     * {@link #pivot()}, materialised as the {@code after} attribute on
     * the wire {@code <messages>} element.
     */
    @WhatsAppWebModule(moduleName = "WASmaxOutNewslettersAfterMixinMixin")
    final class After implements SmaxNewslettersGetNewsletterMessagesDirection {
        /**
         * The server-id pivot above which messages are returned.
         */
        private final long pivot;

        /**
         * Constructs a forward-walking cursor at the given pivot.
         *
         * @apiNote
         * Used when fetching newsletter messages newer than the
         * highest-known server-id of the previous slice.
         *
         * @param pivot the server-id pivot; the relay returns messages
         *              strictly greater than this value
         */
        public After(long pivot) {
            this.pivot = pivot;
        }

        /**
         * Returns the server-id pivot for this cursor.
         *
         * @return the pivot above which messages are fetched
         */
        public long pivot() {
            return pivot;
        }

        /**
         * Compares two cursors for value equality on {@link #pivot()}.
         *
         * @param obj the reference object to compare against
         * @return {@code true} when {@code obj} is an {@link After}
         *         carrying the same pivot
         */
        @Override
        public boolean equals(Object obj) {
            return obj instanceof After that && this.pivot == that.pivot;
        }

        /**
         * Returns the hash code derived from {@link #pivot()}.
         *
         * @return the {@link Long#hashCode(long)} of {@link #pivot()}
         */
        @Override
        public int hashCode() {
            return Long.hashCode(pivot);
        }

        /**
         * Returns a debug representation including the pivot.
         *
         * @return a record-like rendering of this cursor
         */
        @Override
        public String toString() {
            return "SmaxNewslettersGetNewsletterMessagesDirection.After[pivot=" + pivot + ']';
        }
    }
}
