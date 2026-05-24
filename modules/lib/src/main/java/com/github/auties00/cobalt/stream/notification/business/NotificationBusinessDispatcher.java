package com.github.auties00.cobalt.stream.notification.business;

import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.migration.LidMigrationService;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

/**
 * Routes inbound {@code <notification>} stanzas whose category covers
 * WhatsApp Business (verified name, business profile, product catalog,
 * subscriptions, CTWA suggestions, SMB privacy settings, ad-account
 * nonces, marketing-campaign updates, MEX GraphQL events, and payment
 * transactions) to the matching per-type handler.
 *
 * @apiNote
 * Cobalt's {@code NotificationStreamHandler} forwards every stanza whose
 * {@code type} attribute falls in the business branch to this dispatcher;
 * embedders do not invoke it directly. The dispatcher owns one
 * instance of each concrete handler.
 *
 * @implNote
 * This implementation merges three WA Web modules
 * ({@code WAWebHandleBusinessNotification},
 * {@code WAWebHandleMexNotification},
 * {@code WAWebPaymentNotificationHandler}) under one Cobalt dispatcher
 * because they share the business-account fan-out surface. Each
 * sub-handler remains keyed to its WA Web type via the
 * {@link WhatsAppWebModule} annotation on the handler class.
 */
@WhatsAppWebModule(moduleName = "WAWebCommsHandleLoggedInStanza")
public final class NotificationBusinessDispatcher implements SocketStream.Handler {
    /**
     * Handler for {@code business}, {@code digital_commerce_subscription},
     * and {@code fb:update} notifications.
     */
    private final NotificationBusinessStreamHandler businessHandler;

    /**
     * Handler for {@code mex} notifications carrying MEX GraphQL
     * subscription update payloads.
     */
    private final NotificationMexStreamHandler mexHandler;

    /**
     * Handler for {@code pay} notifications carrying payment-invite and
     * payment-transaction stanzas.
     */
    private final NotificationPaymentStreamHandler paymentHandler;

    /**
     * Constructs the dispatcher and eagerly instantiates every
     * sub-handler with the shared client and migration service.
     *
     * @apiNote
     * Called once during
     * {@link SocketStream} setup;
     * embedders do not construct it directly.
     *
     * @param whatsapp            the {@link WhatsAppClient} forwarded to every sub-handler for store and node access
     * @param lidMigrationService the {@link LidMigrationService} consumed only by the {@link NotificationMexStreamHandler} when applying {@code LidChangeNotification} MEX events
     * @param ackSender           the {@link AckSender} forwarded to every
     *                            sub-handler for emitting the
     *                            per-notification outbound {@code <ack>}
     *                            stanza
     */
    public NotificationBusinessDispatcher(WhatsAppClient whatsapp, LidMigrationService lidMigrationService, AckSender ackSender) {
        this.businessHandler = new NotificationBusinessStreamHandler(whatsapp, ackSender);
        this.mexHandler = new NotificationMexStreamHandler(whatsapp, lidMigrationService, ackSender);
        this.paymentHandler = new NotificationPaymentStreamHandler(whatsapp, ackSender);
    }

    /**
     * Forwards {@code node} to the sub-handler whose category matches the
     * stanza's {@code type} attribute; drops stanzas with no type and
     * stanzas whose type this dispatcher does not own.
     *
     * @apiNote
     * Invoked by the parent {@code NotificationStreamHandler}. The
     * outer stream pipeline owns the NACK decision for stanzas this
     * dispatcher does not match.
     *
     * @implNote
     * This implementation maps three business-flavoured types
     * ({@code business}, {@code digital_commerce_subscription},
     * {@code fb:update}) to the same {@link NotificationBusinessStreamHandler}
     * because WA Web's
     * {@code WAWebHandleBusinessNotification.handleBusinessNotificationJob}
     * is the entry point for all three.
     *
     * @param node the incoming {@code <notification>} stanza
     */
    @WhatsAppWebExport(moduleName = "WAWebCommsHandleLoggedInStanza", exports = "handleLoggedInStanza", adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public void handle(Node node) {
        var type = node.getAttributeAsString("type", null);
        if (type == null) {
            return;
        }

        switch (type) {
            case "business", "digital_commerce_subscription", "fb:update" -> businessHandler.handle(node);
            case "mex" -> mexHandler.handle(node);
            case "pay" -> paymentHandler.handle(node);
            default -> {
            }
        }
    }

    /**
     * Propagates {@link SocketStream.Handler#reset()} to every sub-handler.
     *
     * @apiNote
     * Invoked by the parent {@code NotificationStreamHandler} when the
     * stream reset signal fires. Embedders do not call this directly.
     */
    @Override
    public void reset() {
        businessHandler.reset();
        mexHandler.reset();
        paymentHandler.reset();
    }
}
