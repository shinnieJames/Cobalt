package com.github.auties00.cobalt.stream.notification.account;

import com.github.auties00.cobalt.ack.AckSender;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.device.DeviceService;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.stream.SocketStream;

/**
 * Routes inbound {@code <notification>} stanzas whose category falls under the
 * authenticated account (status, contacts, disappearing mode, privacy tokens,
 * picture, about) to the matching per-type handler.
 *
 * @apiNote
 * Cobalt's {@code NotificationStreamHandler} forwards every stanza whose
 * {@code type} attribute names an account-scoped notification to this
 * dispatcher; embedders do not invoke it directly. The dispatcher owns one
 * instance of each concrete handler and is the single fan-in point for the
 * account branch of WhatsApp Web's {@code handleLoggedInStanza} switch.
 *
 * @implNote
 * This implementation collapses six WA Web notification handler modules
 * (account_sync, contacts, disappearing_mode, privacy_token, picture,
 * status) into one Java type with five fields; WA Web instead routes each
 * type through the central {@code WAWebCommsHandleLoggedInStanza} switch
 * to a dedicated handler module. The grouping has no protocol effect; it
 * keeps the dependency graph in {@code NotificationStreamHandler}
 * manageable.
 */
@WhatsAppWebModule(moduleName = "WAWebCommsHandleLoggedInStanza")
public final class NotificationAccountDispatcher implements SocketStream.Handler {
    /**
     * Handler for {@code type="account_sync"} notifications covering status,
     * text status, privacy, devices, blocklist, picture, disappearing mode,
     * TOS, notice, user, and business-opt-out updates for the authenticated
     * account.
     */
    private final NotificationAccountStreamHandler accountHandler;

    /**
     * Handler for {@code type="contacts"} notifications that announce
     * contact updates, phone-number changes, and full contact resyncs.
     */
    private final NotificationContactStreamHandler contactHandler;

    /**
     * Handler for {@code type="disappearing_mode"} notifications that
     * update a chat's per-chat ephemeral timer.
     */
    private final NotificationDisappearingModeStreamHandler disappearingModeHandler;

    /**
     * Handler for {@code type="privacy_token"} notifications that carry
     * trusted-contact tokens for end-to-end identity verification.
     */
    private final NotificationPrivacyStreamHandler privacyHandler;

    /**
     * Handler for {@code type="picture"} and {@code type="status"}
     * notifications that announce profile-picture and about-text changes.
     */
    private final NotificationProfileStreamHandler profileHandler;

    /**
     * Constructs the dispatcher and eagerly instantiates every sub-handler
     * with the shared client and device service.
     *
     * @apiNote
     * Called once during {@link SocketStream}
     * setup. The dispatcher is held as a final field by the parent
     * {@code NotificationStreamHandler}; embedders do not construct it
     * directly.
     *
     * @param whatsapp      the {@link WhatsAppClient} forwarded to every sub-handler for store and node access
     * @param deviceService the {@link DeviceService} consumed only by the
     *                      {@link NotificationAccountStreamHandler} when refreshing
     *                      the authenticated user's own device list
     * @param ackSender     the {@link AckSender} forwarded to every
     *                      sub-handler for emitting the per-notification
     *                      outbound {@code <ack>} stanza
     */
    public NotificationAccountDispatcher(WhatsAppClient whatsapp, DeviceService deviceService, AckSender ackSender) {
        this.accountHandler = new NotificationAccountStreamHandler(whatsapp, deviceService, ackSender);
        this.contactHandler = new NotificationContactStreamHandler(whatsapp, ackSender);
        this.disappearingModeHandler = new NotificationDisappearingModeStreamHandler(whatsapp, ackSender);
        this.privacyHandler = new NotificationPrivacyStreamHandler(whatsapp, ackSender);
        this.profileHandler = new NotificationProfileStreamHandler(whatsapp, ackSender);
    }

    /**
     * Forwards {@code node} to the sub-handler whose category matches the
     * stanza's {@code type} attribute; ignores types this dispatcher does
     * not own.
     *
     * @apiNote
     * Invoked by {@code NotificationStreamHandler}. Stanzas with no
     * {@code type} attribute and stanzas whose type is unknown to this
     * dispatcher return without side-effects. The outer pipeline owns
     * the ACK/NACK decision for unmatched stanzas.
     *
     * @implNote
     * This implementation maps {@code "picture"} and {@code "status"} to
     * the same {@link NotificationProfileStreamHandler}, mirroring WA Web's
     * {@code WAWebHandleProfilePicNotification} and
     * {@code WAWebHandleAboutNotification} which share enough structure
     * (action child, hash-vs-jid resolution, ack format) that Cobalt
     * merges them into one handler.
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
            case "account_sync" -> accountHandler.handle(node);
            case "contacts" -> contactHandler.handle(node);
            case "disappearing_mode" -> disappearingModeHandler.handle(node);
            case "privacy_token" -> privacyHandler.handle(node);
            case "picture", "status" -> profileHandler.handle(node);
            default -> {
            }
        }
    }

    /**
     * Propagates {@link SocketStream.Handler#reset()} to every sub-handler
     * so per-session caches (pending acks, in-flight refresh jobs) are
     * cleared on a socket reconnect.
     *
     * @apiNote
     * Invoked by the parent {@code NotificationStreamHandler} when the
     * stream reset signal fires. Embedders do not call this directly.
     */
    @Override
    public void reset() {
        accountHandler.reset();
        contactHandler.reset();
        disappearingModeHandler.reset();
        privacyHandler.reset();
        profileHandler.reset();
    }
}
