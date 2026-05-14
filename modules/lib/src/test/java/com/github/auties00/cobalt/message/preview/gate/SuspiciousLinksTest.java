package com.github.auties00.cobalt.message.preview.gate;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.message.preview.linkify.Linkify;
import com.github.auties00.cobalt.model.jid.Jid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests for {@link SuspiciousLinks}, mirroring
 * {@code WASuspiciousLinks.findSuspiciousCharacters} entry point.
 *
 * <p>The unit drops obviously-suspicious URLs (Unicode look-alikes,
 * mixed-script attacks) before they reach the preview pipeline. The
 * core matcher lives in {@code Idn.isSuspicious}; this class verifies
 * the {@code SuspiciousLinks} entry shape — null guards, country-code
 * sentinel for non-phone recipients, and the most obvious safe cases.
 */
@DisplayName("SuspiciousLinks")
class SuspiciousLinksTest {

    private static final Jid SELF = Jid.of("12025550100@s.whatsapp.net");
    private static final Jid PEER = Jid.of("19254863482@s.whatsapp.net");
    private static final Jid GROUP = Jid.of("120363023250764418@g.us");

    @Test
    @DisplayName("null match → not suspicious")
    void nullMatchNotSuspicious() {
        assertFalse(SuspiciousLinks.isSuspicious(client(), PEER, null));
    }

    @Test
    @DisplayName("match with null domain → not suspicious")
    void nullDomainNotSuspicious() {
        var match = ascii("example.com", null);
        assertFalse(SuspiciousLinks.isSuspicious(client(), PEER, match));
    }

    @Test
    @DisplayName("plain ASCII domain → not suspicious (no IDN look-alikes)")
    void asciiDomainNotSuspicious() {
        var match = ascii("https://example.com", "example.com");
        assertFalse(SuspiciousLinks.isSuspicious(client(), PEER, match));
    }

    @Test
    @DisplayName("group recipient: country-code sentinel applied (no extracted country)")
    void groupRecipientUsesCountryCodeSentinel() {
        var match = ascii("https://github.com", "github.com");
        // Group has no country code → recipientCC = ZZ. ASCII domain still
        // not suspicious.
        assertFalse(SuspiciousLinks.isSuspicious(client(), GROUP, match));
    }

    @Test
    @DisplayName("null client: ASCII domain still not suspicious (defaults to ZZ self CC)")
    void nullClientAscii() {
        var match = ascii("https://example.com", "example.com");
        assertFalse(SuspiciousLinks.isSuspicious(null, PEER, match));
    }

    /**
     * Builds a {@link Linkify.Match} for an ASCII URL.
     *
     * @param url    the URL
     * @param domain the host component, or {@code null}
     * @return the synthetic match
     */
    private static Linkify.Match ascii(String url, String domain) {
        return new Linkify.Match(
                url, url, 0, url,
                "https", null,
                domain,
                null, null, null, null,
                true);
    }

    /**
     * Builds a {@link TestWhatsAppClient} with a self JID configured so
     * {@code SuspiciousLinks} can read it for the self-country-code path.
     *
     * @return the test client
     */
    private static TestWhatsAppClient client() {
        return TestWhatsAppClient.create()
                .withStore(MessageFixtures.temporaryStore(SELF, null));
    }
}
