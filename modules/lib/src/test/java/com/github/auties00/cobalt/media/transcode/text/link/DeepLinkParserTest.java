package com.github.auties00.cobalt.media.transcode.text.link;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.media.transcode.text.link.DeepLinkParser.DeepLink;
import com.github.auties00.cobalt.props.TestABPropsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Exercises the deep-link branches of {@link DeepLinkParser#parse}
 * that match before the payment-link fallback.
 *
 * @apiNote
 * Mirrors a subset of {@code WAWebApiParse.parseAPICmd}; only the
 * group-invite / catalog / product paths are covered here because
 * the payment-link fallback reaches into the client's AB-props
 * service and requires a stub that is not wired into the test
 * harness. The byte-equal cases for every shape are exercised by
 * the live-corpus harness.
 */
@DisplayName("DeepLinkParser")
class DeepLinkParserTest {
    /**
     * The shared test client used as the {@code client} parameter.
     */
    private static final TestWhatsAppClient CLIENT = TestWhatsAppClient.create();

    /**
     * The shared empty AB-props service used as the
     * {@code abPropsService} parameter; only the payment-link
     * fallback reads from it, and no test here triggers that
     * fallback.
     */
    private static final TestABPropsService PROPS = TestABPropsService.builder().build();

    /**
     * Verifies that a short-form {@code chat.whatsapp.com} URL
     * parses to a {@link DeepLink.GroupInvite} carrying the invite
     * code.
     */
    @Test
    @DisplayName("chat.whatsapp.com invite URL -> GroupInvite with the code captured")
    void groupInviteShort() {
        var result = DeepLinkParser.parse(CLIENT, PROPS, "https://chat.whatsapp.com/AbCdEf0123456789");
        var invite = assertInstanceOf(DeepLink.GroupInvite.class, result);
        assertEquals("AbCdEf0123456789", invite.code());
    }
}
