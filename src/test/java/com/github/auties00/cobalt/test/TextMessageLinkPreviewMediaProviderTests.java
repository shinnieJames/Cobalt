package com.github.auties00.cobalt.test;

import com.github.auties00.cobalt.model.media.MediaPath;
import com.github.auties00.cobalt.model.media.TextMessageLinkPreviewMediaProvider;
import com.github.auties00.cobalt.model.message.standard.TextMessageBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TextMessageLinkPreviewMediaProviderTests {
    @Test
    public void shouldMapUploadMetadataToTextMessageFields() {
        var message = new TextMessageBuilder()
                .text("hello https://example.com")
                .thumbnail(new byte[]{1, 2, 3})
                .build();
        var provider = new TextMessageLinkPreviewMediaProvider(message);

        provider.setMediaDirectPath("/mms/thumbnail-link/test");
        provider.setMediaKey(new byte[]{4, 5, 6});
        provider.setMediaKeyTimestamp(123L);
        provider.setMediaSha256(new byte[]{7, 8});
        provider.setMediaEncryptedSha256(new byte[]{9, 10});
        provider.setMediaUrl("https://example.com/media");

        assertEquals(MediaPath.THUMBNAIL_LINK, provider.mediaPath());
        assertEquals("/mms/thumbnail-link/test", message.thumbnailDirectPath().orElseThrow());
        assertArrayEquals(new byte[]{4, 5, 6}, message.mediaKey().orElseThrow());
        assertEquals(123L, message.mediaKeyTimestampSeconds().orElseThrow());
        assertArrayEquals(new byte[]{7, 8}, message.thumbnailSha256().orElseThrow());
        assertArrayEquals(new byte[]{9, 10}, message.thumbnailEncSha256().orElseThrow());
        assertEquals("https://example.com/media", provider.mediaUrl().orElseThrow());
    }
}
