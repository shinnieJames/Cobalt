package com.github.auties00.cobalt.message.preview.linkify;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.message.preview.linkify.DeepLinkParser.DeepLink;
import com.github.auties00.cobalt.props.TestABPropsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Tests for {@link DeepLinkParser}, mirroring {@code WAWebApiParse.parseAPICmd}.
 *
 * <p>Only the paths that match a deep-link prefix BEFORE the payment-link
 * fallback are exercised here, because the payment fallback reaches into
 * {@code client.abPropsService()} and requires a stubbed
 * {@code ABPropsService} that isn't yet wired into {@link TestWhatsAppClient}.
 * Those byte-equal cases are deferred to the live-corpus test class.
 */
@DisplayName("DeepLinkParser")
class DeepLinkParserTest {
    private static final TestWhatsAppClient CLIENT = TestWhatsAppClient.create();
    private static final TestABPropsService PROPS = TestABPropsService.builder().build();

    @Test
    @DisplayName("chat.whatsapp.com invite URL → GroupInvite with the code captured")
    void groupInviteShort() {
        var result = DeepLinkParser.parse(CLIENT, PROPS, "https://chat.whatsapp.com/AbCdEf0123456789");
        var invite = assertInstanceOf(DeepLink.GroupInvite.class, result);
        assertEquals("AbCdEf0123456789", invite.code());
    }
}
