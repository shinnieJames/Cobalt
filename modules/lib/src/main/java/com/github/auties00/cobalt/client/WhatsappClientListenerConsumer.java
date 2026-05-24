package com.github.auties00.cobalt.client;

/**
 * A sealed marker that groups the arity-specific functional consumer
 * shapes used to register lambda-based listeners with a
 * {@link WhatsAppClient}.
 *
 * @apiNote
 * Java lambdas cannot be polymorphic across arities, so each
 * convenience overload on {@link WhatsAppClient#addListener(WhatsappClientListenerConsumer)}
 * accepts a consumer whose arity matches the underlying
 * {@link WhatsAppClientListener} callback. Callers pick the variant
 * (zero through four payloads) that mirrors the event signature they
 * want to observe.
 *
 * @see WhatsAppClientListener
 */
public sealed interface WhatsappClientListenerConsumer {
    /**
     * A consumer for listener overloads whose underlying event carries
     * no payload.
     *
     * @apiNote
     * Used for callbacks such as
     * {@link WhatsAppClientListener#onLoggedIn(WhatsAppClient)} where
     * the receipt of the event is the entire signal.
     */
    @FunctionalInterface
    non-sealed interface Empty extends WhatsappClientListenerConsumer {
        /**
         * Invokes the consumer.
         */
        void accept();
    }

    /**
     * A consumer for listener overloads whose underlying event carries
     * a single payload.
     *
     * @param <F> the payload type
     */
    @FunctionalInterface
    non-sealed interface Unary<F> extends WhatsappClientListenerConsumer {
        /**
         * Invokes the consumer with the given payload.
         *
         * @param value the event payload
         */
        void accept(F value);
    }

    /**
     * A consumer for listener overloads whose underlying event carries
     * two payloads.
     *
     * @param <F> the first payload type
     * @param <S> the second payload type
     */
    @FunctionalInterface
    non-sealed interface Binary<F, S> extends WhatsappClientListenerConsumer {
        /**
         * Invokes the consumer with the given payloads.
         *
         * @param first  the first event payload
         * @param second the second event payload
         */
        void accept(F first, S second);
    }

    /**
     * A consumer for listener overloads whose underlying event carries
     * three payloads.
     *
     * @param <F> the first payload type
     * @param <S> the second payload type
     * @param <T> the third payload type
     */
    @FunctionalInterface
    non-sealed interface Ternary<F, S, T> extends WhatsappClientListenerConsumer {
        /**
         * Invokes the consumer with the given payloads.
         *
         * @param first  the first event payload
         * @param second the second event payload
         * @param third  the third event payload
         */
        void accept(F first, S second, T third);
    }

    /**
     * A consumer for listener overloads whose underlying event carries
     * four payloads.
     *
     * @param <F>  the first payload type
     * @param <S>  the second payload type
     * @param <T>  the third payload type
     * @param <FO> the fourth payload type
     */
    @FunctionalInterface
    non-sealed interface Quaternary<F, S, T, FO> extends WhatsappClientListenerConsumer {
        /**
         * Invokes the consumer with the given payloads.
         *
         * @param first  the first event payload
         * @param second the second event payload
         * @param third  the third event payload
         * @param fourth the fourth event payload
         */
        void accept(F first, S second, T third, FO fourth);
    }
}
