package com.github.auties00.cobalt.message.send;

import com.github.auties00.cobalt.model.jid.Jid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Routing-shape tests for {@link MessageSendingService}'s dispatch switch.
 *
 * <p>The orchestrator picks the sub-sender by JID-server predicates on the
 * parent JID:
 * <ul>
 *   <li>{@link Jid#hasUserServer()} or {@link Jid#hasLidServer()} →
 *       {@link UserMessageSender}</li>
 *   <li>{@link Jid#hasGroupOrCommunityServer()} →
 *       {@link GroupMessageSender}</li>
 *   <li>{@link Jid#isStatusBroadcastAccount()} →
 *       {@link StatusMessageSender}</li>
 *   <li>{@link Jid#hasNewsletterServer()} →
 *       {@link NewsletterMessageSender}</li>
 *   <li>otherwise → throws
 *       {@code WhatsAppMessageException.Send.InvalidRecipient}</li>
 * </ul>
 *
 * <p>This test pins the predicate semantics rather than the dispatch
 * itself, which keeps the test independent of the full DI graph wired by
 * {@code MessageSendingService}'s constructor (5 sub-senders + 4 stanza
 * builders). The integration coverage for the dispatch result is in the
 * synthetic {@code UserMessageSenderTest} / {@code GroupMessageSenderTest}
 * / {@code NewsletterMessageSenderTest} families.
 */
@DisplayName("MessageSendingService dispatch routing")
class MessageSendingServiceDispatchTest {

    @ParameterizedTest(name = "{0} → exactly one route ({1})")
    @CsvSource({
            // jid                                            ,   expected route
            "12025550100@s.whatsapp.net                       , USER",
            "19254863482@s.whatsapp.net                       , USER",
            "258252122116273@lid                              , USER_LID",
            "83116928594056@lid                               , USER_LID",
            "120363409745354608@g.us                          , GROUP",
            "120363023250764418@g.us                          , GROUP",
            "120363409813756927@g.us                          , GROUP",
            "120363409463700665@g.us                          , GROUP",
            "120363402045452944@newsletter                    , NEWSLETTER",
            "status@broadcast                                 , STATUS",
    })
    @DisplayName("every corpus JID matches exactly one dispatch predicate")
    void exactlyOneRoutePerJid(String jidString, String expectedRoute) {
        var jid = Jid.of(jidString.trim());

        int matched = 0;
        String matchedRoute = null;
        if (jid.hasUserServer()) { matched++; matchedRoute = "USER"; }
        if (jid.hasLidServer())  { matched++; matchedRoute = "USER_LID"; }
        if (jid.hasGroupOrCommunityServer()) { matched++; matchedRoute = "GROUP"; }
        if (jid.isStatusBroadcastAccount()) { matched++; matchedRoute = "STATUS"; }
        if (jid.hasNewsletterServer()) { matched++; matchedRoute = "NEWSLETTER"; }

        assertEquals(1, matched, jidString + " should match exactly one dispatch predicate");
        assertEquals(expectedRoute, matchedRoute,
                jidString + " expected to route to " + expectedRoute + " but matched " + matchedRoute);
    }

    @Test
    @DisplayName("LID and PN-form user JIDs both qualify as the User route, mutually exclusive on which predicate fires")
    void lidAndPnAreBothUserRoutes() {
        var pn = Jid.of("12025550100@s.whatsapp.net");
        var lid = Jid.of("258252122116273@lid");

        assertTrue(pn.hasUserServer(), "@s.whatsapp.net is a user server");
        assertFalse(pn.hasLidServer(), "@s.whatsapp.net is not a LID server");

        assertTrue(lid.hasLidServer(), "@lid is a LID server");
        assertFalse(lid.hasUserServer(), "@lid is not a user (PN) server");
    }

    @Test
    @DisplayName("group and community JIDs share the same predicate")
    void groupCommunitySamePredicate() {
        var group = Jid.of("120363409745354608@g.us");
        var community = Jid.of("120363409813756927@g.us");

        assertTrue(group.hasGroupOrCommunityServer());
        assertTrue(community.hasGroupOrCommunityServer());

        // Crucially, group JIDs are NOT user JIDs.
        assertFalse(group.hasUserServer());
        assertFalse(group.hasLidServer());
        assertFalse(community.isStatusBroadcastAccount());
    }

    @Test
    @DisplayName("status@broadcast is its own route, distinct from groups and users")
    void statusBroadcastIsOwnRoute() {
        var status = Jid.statusBroadcastAccount();
        var fromString = Jid.of("status@broadcast");

        assertEquals(status, fromString,
                "Jid.statusBroadcastAccount() and Jid.of(\"status@broadcast\") must be equal");
        assertTrue(status.isStatusBroadcastAccount());
        assertTrue(fromString.isStatusBroadcastAccount());
        assertFalse(status.hasUserServer());
        assertFalse(status.hasLidServer());
        assertFalse(status.hasGroupOrCommunityServer());
        assertFalse(status.hasNewsletterServer());
    }

    @Test
    @DisplayName("newsletter JIDs are their own route, distinct from everything else")
    void newsletterIsOwnRoute() {
        var newsletter = Jid.of("120363402045452944@newsletter");

        assertTrue(newsletter.hasNewsletterServer());
        assertFalse(newsletter.hasUserServer());
        assertFalse(newsletter.hasLidServer());
        assertFalse(newsletter.hasGroupOrCommunityServer());
        assertFalse(newsletter.isStatusBroadcastAccount());
    }

    @Test
    @DisplayName("@bot JIDs do not match any chat-dispatch predicate (bot routing goes through the user path with a bot-flag, not its own server kind)")
    void botJidDoesNotMatchChatDispatch() {
        var bot = Jid.of("867051314767696@bot");

        assertFalse(bot.hasUserServer(), "@bot is not @s.whatsapp.net");
        assertFalse(bot.hasLidServer(), "@bot is not @lid");
        assertFalse(bot.hasGroupOrCommunityServer(), "@bot is not @g.us");
        assertFalse(bot.isStatusBroadcastAccount(), "@bot is not status@broadcast");
        assertFalse(bot.hasNewsletterServer(), "@bot is not @newsletter");
        // Bot sends are dispatched via a peer JID lookup, not by the bot's own
        // JID server kind — the dispatch switch in MessageSendingService.send()
        // doesn't accept @bot as a chat JID directly.
    }

    @Test
    @DisplayName("bare @s.whatsapp.net device JIDs (user:device@s.whatsapp.net) still route to USER")
    void deviceJidsRouteAsUser() {
        var deviceJid = Jid.of("12025550100:73@s.whatsapp.net");
        assertTrue(deviceJid.hasUserServer(),
                "device-suffixed PN JID still has @s.whatsapp.net server");
        assertFalse(deviceJid.hasLidServer());
        assertFalse(deviceJid.hasGroupOrCommunityServer());
    }
}
