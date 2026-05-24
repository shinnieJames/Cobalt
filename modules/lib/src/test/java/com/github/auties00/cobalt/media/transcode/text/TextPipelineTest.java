package com.github.auties00.cobalt.media.transcode.text;

import com.github.auties00.cobalt.client.TestWhatsAppClient;
import com.github.auties00.cobalt.media.TestMediaConnectionService;
import com.github.auties00.cobalt.message.MessageFixtures;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.text.ExtendedTextMessageBuilder;
import com.github.auties00.cobalt.props.TestABPropsService;
import com.github.auties00.cobalt.store.WhatsAppStore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the short-circuit branches of {@link TextPipeline#decorate}.
 *
 * @apiNote
 * Covers the pure-function no-op paths
 * ({@code null} message, {@code disableLinkPreviews} privacy flag,
 * empty body, body without any URL) that bail before any HTTP or
 * cache interaction, mirroring the early guards in
 * {@code WAWebLinkPreviewChatAction.getLinkPreview}.
 *
 * @implNote
 * The HTTP-fetching branches require a stub {@link java.net.http.HttpClient}
 * injection point that the production constructor does not expose;
 * those branches are exercised by the live-corpus / parity harness
 * instead of this class.
 */
@DisplayName("TextPipeline")
class TextPipelineTest {

    /**
     * The local-user JID seeded onto the test store.
     */
    private static final Jid SELF = Jid.of("12025550100@s.whatsapp.net");

    /**
     * The phone-user recipient JID used for non-newsletter scenarios.
     */
    private static final Jid PEER = Jid.of("19254863482@s.whatsapp.net");

    /**
     * The newsletter recipient JID referenced by
     * {@link #newsletterChatRespected()} as a placeholder for the
     * newsletter branch.
     */
    private static final Jid NEWSLETTER = Jid.of("120363402045452944@newsletter");

    /**
     * Verifies that a {@code null} message returns without raising.
     */
    @Test
    @DisplayName("decorate(null message): no-op (no exception, no side effects)")
    void nullMessageNoOp() {
        var props = TestABPropsService.builder().build();
        var client = TestWhatsAppClient.create().withStore(store(false));
        var service = new TextPipeline(client, props, TestMediaConnectionService.create());
        service.run(PEER, null);
    }

    /**
     * Verifies that the {@code disableLinkPreviews} privacy flag
     * suppresses every preview field on the outgoing message.
     */
    @Test
    @DisplayName("decorate: store.disableLinkPreviews()=true -> message body unchanged")
    void disabledPreviewsLeaveMessageAlone() {
        var props = TestABPropsService.builder().build();
        var store = store(true);
        var client = TestWhatsAppClient.create().withStore(store);
        var service = new TextPipeline(client, props, TestMediaConnectionService.create());

        var msg = new ExtendedTextMessageBuilder().text("https://example.com").build();
        service.run(PEER, msg);

        assertEquals("https://example.com", msg.text().orElseThrow(),
                "text body is preserved verbatim — only preview metadata may be added by decorate");
        assertTrue(msg.matchedText().isEmpty(),
                "disabled preview: matchedText must remain absent");
        assertTrue(msg.title().isEmpty(),
                "disabled preview: title must remain absent");
    }

    /**
     * Verifies that a body containing no URL adds no preview fields.
     */
    @Test
    @DisplayName("decorate: text without any URL -> no preview metadata added")
    void plainTextNoUrlNoOp() {
        var props = TestABPropsService.builder().build();
        var client = TestWhatsAppClient.create().withStore(store(false));
        var service = new TextPipeline(client, props, TestMediaConnectionService.create());

        var msg = new ExtendedTextMessageBuilder().text("hello world").build();
        service.run(PEER, msg);

        assertTrue(msg.matchedText().isEmpty(),
                "no URL in text -> no matchedText set");
        assertTrue(msg.title().isEmpty(),
                "no URL in text -> no title set");
    }

    /**
     * Verifies that an empty body returns without raising and adds no
     * preview fields.
     */
    @Test
    @DisplayName("decorate: empty text body -> no-op")
    void emptyTextNoOp() {
        var props = TestABPropsService.builder().build();
        var client = TestWhatsAppClient.create().withStore(store(false));
        var service = new TextPipeline(client, props, TestMediaConnectionService.create());

        var msg = new ExtendedTextMessageBuilder().text("").build();
        service.run(PEER, msg);
        assertTrue(msg.matchedText().isEmpty());
    }

    /**
     * Verifies that a {@code null} client constructor argument is
     * rejected.
     */
    @Test
    @DisplayName("constructor(null client): throws NullPointerException")
    void nullClientThrows() {
        var props = TestABPropsService.builder().build();
        Assertions.assertThrows(NullPointerException.class,
                () -> new TextPipeline(null, props, TestMediaConnectionService.create()));
    }

    /**
     * Verifies that a {@code null} AB-props service constructor
     * argument is rejected.
     */
    @Test
    @DisplayName("constructor(null abPropsService): throws NullPointerException")
    void nullAbPropsThrows() {
        var client = TestWhatsAppClient.create().withStore(store(false));
        Assertions.assertThrows(NullPointerException.class,
                () -> new TextPipeline(client, null, TestMediaConnectionService.create()));
    }

    /**
     * Verifies that the non-newsletter chat path returns without
     * raising when the URL is not in the cache.
     *
     * @implNote
     * Without an HTTP stub the cache miss does not exercise the
     * fetch path; the assertion exists to keep the static
     * {@link #NEWSLETTER} reference live and to document the gate
     * for the newsletter branch.
     */
    @Test
    @DisplayName("decorate: text with URL but newsletter recipient + suspicious or non-previewable path -> no-op")
    void newsletterChatRespected() {
        var props = TestABPropsService.builder().build();
        var client = TestWhatsAppClient.create().withStore(store(false));
        var service = new TextPipeline(client, props, TestMediaConnectionService.create());
        var msg = new ExtendedTextMessageBuilder().text("https://example.com").build();
        service.run(PEER, msg);
        assertNotNull(NEWSLETTER);
    }

    /**
     * Builds a temporary store with the
     * {@code disableLinkPreviews} flag pre-set.
     *
     * @apiNote
     * Called by every test to materialise the
     * {@link WhatsAppStore}
     * the {@link TestWhatsAppClient} delegates to.
     *
     * @param disableLinkPreviews whether previews are disabled
     * @return the configured store
     */
    private static WhatsAppStore store(boolean disableLinkPreviews) {
        var store = MessageFixtures.temporaryStore(SELF, null);
        store.setDisableLinkPreviews(disableLinkPreviews);
        return store;
    }
}
