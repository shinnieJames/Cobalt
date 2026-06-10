package com.github.auties00.cobalt.test;

import com.github.auties00.cobalt.client.WhatsAppClientMessagePreviewHandler;
import com.github.auties00.cobalt.model.message.standard.TextMessage;
import com.github.auties00.cobalt.model.message.standard.TextMessageBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TextLinkPreviewHandlerTests {
    @Test
    public void shouldPreserveManualCanonicalUrl() {
        var message = new TextMessageBuilder()
                .text("hello https://example.com")
                .canonicalUrl("https://manual.example.com")
                .build();

        assertEquals("https://manual.example.com", message.canonicalUrl().orElseThrow());
    }

    @Test
    public void shouldIgnoreNonTextMessagesByContract() {
        var handler = WhatsAppClientMessagePreviewHandler.disabled();
        assertDoesNotThrow(() -> handler.attribute(TextMessage.of("https://example.com")));
    }
}
