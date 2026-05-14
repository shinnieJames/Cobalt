package com.github.auties00.cobalt.message.preview.gate;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.props.TestABPropsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link DomainPreviewableGate}, mirroring
 * {@code WAWebCheckIfDomainIsPreviewable.checkIfDomainIsPreviewable}.
 *
 * <p>Covers the short-circuit branches that do not touch the client's
 * AB-props service: {@code chatJid == null} and non-newsletter chats
 * always return previewable without consulting any feature flags. The
 * newsletter branch (AB-prop driven + server allow-list lookup) is
 * exercised end-to-end by the newsletter-send live oracle and would
 * need a stubbed {@code abPropsService()} on the test client harness.
 */
@DisplayName("DomainPreviewableGate")
class DomainPreviewableGateTest {

    private static final Jid PEER_PN = Jid.of("19254863482@s.whatsapp.net");
    private static final Jid GROUP = Jid.of("120363023250764418@g.us");
    private static final Jid LID = Jid.of("83116928594056@lid");

    @Test
    @DisplayName("null chatJid always returns true (no chat context = always previewable)")
    void nullChatJidPreviewable() {
        // Implementation short-circuits on null/non-newsletter before
        // accessing the client — safe to pass any test stub here.
        var props = TestABPropsService.builder().build();
        assertTrue(DomainPreviewableGate.isPreviewable(TestWhatsAppClient.create(),
                props, null, "example.com"));
    }

    @Test
    @DisplayName("PN chat (s.whatsapp.net) is always previewable")
    void pnChatPreviewable() {
        var props = TestABPropsService.builder().build();
        assertTrue(DomainPreviewableGate.isPreviewable(TestWhatsAppClient.create(),
                props, PEER_PN, "example.com"));
    }

    @Test
    @DisplayName("LID chat is always previewable (non-newsletter)")
    void lidChatPreviewable() {
        var props = TestABPropsService.builder().build();
        assertTrue(DomainPreviewableGate.isPreviewable(TestWhatsAppClient.create(),
                props, LID, "example.com"));
    }

    @Test
    @DisplayName("group chat is always previewable (non-newsletter)")
    void groupChatPreviewable() {
        var props = TestABPropsService.builder().build();
        assertTrue(DomainPreviewableGate.isPreviewable(TestWhatsAppClient.create(),
                props, GROUP, "example.com"));
    }

    @Test
    @DisplayName("status broadcast is always previewable (non-newsletter)")
    void statusBroadcastPreviewable() {
        var props = TestABPropsService.builder().build();
        assertTrue(DomainPreviewableGate.isPreviewable(TestWhatsAppClient.create(),
                props, Jid.statusBroadcastAccount(), "example.com"));
    }

    @Test
    @DisplayName("null domain on a non-newsletter chat is still previewable (domain only matters for newsletters)")
    void nullDomainOnNonNewsletter() {
        var props = TestABPropsService.builder().build();
        assertTrue(DomainPreviewableGate.isPreviewable(TestWhatsAppClient.create(),
                props, PEER_PN, null));
    }
}
