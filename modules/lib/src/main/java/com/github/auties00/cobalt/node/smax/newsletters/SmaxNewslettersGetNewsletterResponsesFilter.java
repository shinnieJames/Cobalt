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
 * Sealed disjunction over the at-most-one filter applied to a
 * {@link SmaxNewslettersGetNewsletterResponsesRequest}.
 *
 * @apiNote
 * Pick this when narrowing the question-responses slice; the variant
 * maps onto WA Web's {@code ResponsesFilterType.CONTACTS} or
 * {@code ResponsesFilterType.REPLIED} toggle as wired through
 * {@code WAWebNewsletterGetQuestionResponsesQuery.getQuestionResponsesQuery}.
 * The two variants are mutually exclusive and the parameter remains
 * optional; omit it entirely to disable filtering.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutNewslettersContactsOrRepliedFilterMixinMixinGroup")
public sealed interface SmaxNewslettersGetNewsletterResponsesFilter permits SmaxNewslettersGetNewsletterResponsesFilter.Contacts, SmaxNewslettersGetNewsletterResponsesFilter.Replied {

    /**
     * The variant that restricts the slice to address-book contacts.
     *
     * @apiNote
     * Surfaces the WA Web "contacts" toggle on the question-responses
     * UI, materialised on the wire as an empty {@code <contacts/>}
     * marker inside the {@code <filters>} block.
     */
    @WhatsAppWebModule(moduleName = "WASmaxOutNewslettersContactsFilterMixinMixin")
    final class Contacts implements SmaxNewslettersGetNewsletterResponsesFilter {
        /**
         * Constructs the contacts filter marker.
         *
         * @apiNote
         * Carries no data; the variant identity alone selects the
         * filter on the wire.
         */
        public Contacts() {
        }

        /**
         * Compares two markers for type identity.
         *
         * @param obj the reference object to compare against
         * @return {@code true} when {@code obj} is a {@link Contacts}
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            return obj != null && obj.getClass() == this.getClass();
        }

        /**
         * Returns a stable hash code for the marker.
         *
         * @return the class's {@link Object#hashCode()}
         */
        @Override
        public int hashCode() {
            return Contacts.class.hashCode();
        }

        /**
         * Returns a debug representation of the marker.
         *
         * @return a record-like rendering of this variant
         */
        @Override
        public String toString() {
            return "SmaxNewslettersGetNewsletterResponsesFilter.Contacts[]";
        }
    }

    /**
     * The variant that restricts the slice to entries the question
     * owner has explicitly replied to.
     *
     * @apiNote
     * Surfaces the WA Web "replied" toggle on the question-responses
     * UI, materialised on the wire as an empty {@code <replied/>}
     * marker inside the {@code <filters>} block.
     */
    @WhatsAppWebModule(moduleName = "WASmaxOutNewslettersRepliedFilterMixinMixin")
    final class Replied implements SmaxNewslettersGetNewsletterResponsesFilter {
        /**
         * Constructs the replied filter marker.
         *
         * @apiNote
         * Carries no data; the variant identity alone selects the
         * filter on the wire.
         */
        public Replied() {
        }

        /**
         * Compares two markers for type identity.
         *
         * @param obj the reference object to compare against
         * @return {@code true} when {@code obj} is a {@link Replied}
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            return obj != null && obj.getClass() == this.getClass();
        }

        /**
         * Returns a stable hash code for the marker.
         *
         * @return the class's {@link Object#hashCode()}
         */
        @Override
        public int hashCode() {
            return Replied.class.hashCode();
        }

        /**
         * Returns a debug representation of the marker.
         *
         * @return a record-like rendering of this variant
         */
        @Override
        public String toString() {
            return "SmaxNewslettersGetNewsletterResponsesFilter.Replied[]";
        }
    }
}
