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
 * {@link SmaxNewslettersGetNewsletterStatusesRequest}.
 *
 * @apiNote
 * Pick this when scrolling a newsletter's status history; omitting
 * the cursor altogether requests the latest slice. WA Web's admin
 * newsletter-status panel (see
 * {@code WAWebNewsletterGetStatusesQuery.queryNewsletterStatuses})
 * picks one of {@link Before} or {@link After} based on which scroll
 * pivot the UI passes in.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutNewslettersStatusDirections")
public sealed interface SmaxNewslettersGetNewsletterStatusesDirection permits SmaxNewslettersGetNewsletterStatusesDirection.Before, SmaxNewslettersGetNewsletterStatusesDirection.After {

    /**
     * The variant that walks backwards from a pivot server-id.
     *
     * @apiNote
     * Selects statuses with server-ids strictly less than
     * {@link #pivot()}, materialised as the {@code before} attribute on
     * the wire {@code <statuses>} element.
     */
    @WhatsAppWebModule(moduleName = "WASmaxOutNewslettersStatusBeforeMixinMixin")
    final class Before implements SmaxNewslettersGetNewsletterStatusesDirection {
        /**
         * The server-id pivot below which statuses are returned.
         */
        private final long pivot;

        /**
         * Constructs a backward-walking cursor at the given pivot.
         *
         * @apiNote
         * Used when paginating older statuses one slice at a time.
         *
         * @param pivot the server-id pivot; the relay returns statuses
         *              strictly less than this value
         */
        public Before(long pivot) {
            this.pivot = pivot;
        }

        /**
         * Returns the server-id pivot for this cursor.
         *
         * @return the pivot below which statuses are fetched
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
            return "SmaxNewslettersGetNewsletterStatusesDirection.Before[pivot=" + pivot + ']';
        }
    }

    /**
     * The variant that walks forwards from a pivot server-id.
     *
     * @apiNote
     * Selects statuses with server-ids strictly greater than
     * {@link #pivot()}, materialised as the {@code after} attribute on
     * the wire {@code <statuses>} element.
     */
    @WhatsAppWebModule(moduleName = "WASmaxOutNewslettersStatusAfterMixinMixin")
    final class After implements SmaxNewslettersGetNewsletterStatusesDirection {
        /**
         * The server-id pivot above which statuses are returned.
         */
        private final long pivot;

        /**
         * Constructs a forward-walking cursor at the given pivot.
         *
         * @apiNote
         * Used when fetching newsletter statuses newer than the
         * highest-known server-id of the previous slice.
         *
         * @param pivot the server-id pivot; the relay returns statuses
         *              strictly greater than this value
         */
        public After(long pivot) {
            this.pivot = pivot;
        }

        /**
         * Returns the server-id pivot for this cursor.
         *
         * @return the pivot above which statuses are fetched
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
            return "SmaxNewslettersGetNewsletterStatusesDirection.After[pivot=" + pivot + ']';
        }
    }
}
