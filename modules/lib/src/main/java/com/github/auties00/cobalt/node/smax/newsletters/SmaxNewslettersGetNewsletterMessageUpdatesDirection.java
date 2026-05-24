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
 * Sealed disjunction over the pagination cursor of a
 * {@link SmaxNewslettersGetNewsletterMessageUpdatesRequest}.
 *
 * @apiNote
 * Pick this when polling the message-updates delta for a single
 * newsletter; one of {@link Before} or {@link After} must be supplied,
 * never both and never neither. {@link After} drives the WA Web
 * forward-walk pattern in
 * {@code WAWebNewsletterGetMessageUpdatesQuery.getNewsletterMessageUpdatesQuery},
 * which paginates updates after the last-seen server-id.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutNewslettersMessageUpdatesBeforeOrAfterMixinMixinGroup")
public sealed interface SmaxNewslettersGetNewsletterMessageUpdatesDirection permits SmaxNewslettersGetNewsletterMessageUpdatesDirection.Before, SmaxNewslettersGetNewsletterMessageUpdatesDirection.After {

    /**
     * The variant that walks backwards from a pivot server-id.
     *
     * @apiNote
     * Selects message updates with server-ids strictly less than
     * {@link #pivot()}, materialised as the {@code before} attribute on
     * the wire {@code <message_updates>} element.
     */
    @WhatsAppWebModule(moduleName = "WASmaxOutNewslettersMessageUpdatesBeforeMixinMixin")
    final class Before implements SmaxNewslettersGetNewsletterMessageUpdatesDirection {
        /**
         * The server-id pivot below which message updates are returned.
         */
        private final long pivot;

        /**
         * Constructs a backward-walking cursor at the given pivot.
         *
         * @apiNote
         * Used when paginating older message updates one slice at a
         * time.
         *
         * @param pivot the server-id pivot; the relay returns updates
         *              strictly less than this value
         */
        public Before(long pivot) {
            this.pivot = pivot;
        }

        /**
         * Returns the server-id pivot for this cursor.
         *
         * @return the pivot below which updates are fetched
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
            return "SmaxNewslettersGetNewsletterMessageUpdatesDirection.Before[pivot=" + pivot + ']';
        }
    }

    /**
     * The variant that walks forwards from a pivot server-id.
     *
     * @apiNote
     * Selects message updates with server-ids strictly greater than
     * {@link #pivot()}, materialised as the {@code after} attribute on
     * the wire {@code <message_updates>} element. This is the variant
     * WA Web's background delta-sync defaults to.
     */
    @WhatsAppWebModule(moduleName = "WASmaxOutNewslettersMessageUpdatesAfterMixinMixin")
    final class After implements SmaxNewslettersGetNewsletterMessageUpdatesDirection {
        /**
         * The server-id pivot above which message updates are returned.
         */
        private final long pivot;

        /**
         * Constructs a forward-walking cursor at the given pivot.
         *
         * @apiNote
         * Used when polling for new message updates strictly past the
         * caller's last-seen server-id.
         *
         * @param pivot the server-id pivot; the relay returns updates
         *              strictly greater than this value
         */
        public After(long pivot) {
            this.pivot = pivot;
        }

        /**
         * Returns the server-id pivot for this cursor.
         *
         * @return the pivot above which updates are fetched
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
            return "SmaxNewslettersGetNewsletterMessageUpdatesDirection.After[pivot=" + pivot + ']';
        }
    }
}
