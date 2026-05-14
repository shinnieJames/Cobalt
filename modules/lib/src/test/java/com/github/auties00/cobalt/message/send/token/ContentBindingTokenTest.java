package com.github.auties00.cobalt.message.send.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link ContentBindingToken}, mirroring
 * {@code WAWebMsgRcatUtils.getContentIdString} (YouTube ID extraction)
 * and {@code WAWebUtilsYoutubeUrlParser}.
 *
 * <p>Coverage:
 * <ul>
 *   <li>YouTube ID extraction across canonical URL shapes
 *       ({@code youtu.be/}, {@code youtube.com/watch?v=},
 *       {@code youtube.com/shorts/}, {@code www.} / {@code m.}
 *       subdomains).</li>
 *   <li>{@code youtubeOnly} flag toggles whether non-YouTube URLs return
 *       the raw matched text or {@code null}.</li>
 *   <li>{@link ContentBindingToken#resolveContentId} encodes the ID as
 *       UTF-8 bytes.</li>
 * </ul>
 */
@DisplayName("ContentBindingToken")
class ContentBindingTokenTest {

    @Test
    @DisplayName("getContentIdString: YouTube watch URL → 11-char video id")
    void youtubeWatchUrl() {
        assertEquals("dQw4w9WgXcQ",
                ContentBindingToken.getContentIdString("https://www.youtube.com/watch?v=dQw4w9WgXcQ", true));
        assertEquals("dQw4w9WgXcQ",
                ContentBindingToken.getContentIdString("https://youtube.com/watch?v=dQw4w9WgXcQ", true));
    }

    @Test
    @DisplayName("getContentIdString: youtu.be short URL → 11-char video id")
    void youtubeShortUrl() {
        assertEquals("dQw4w9WgXcQ",
                ContentBindingToken.getContentIdString("https://youtu.be/dQw4w9WgXcQ", true));
    }

    @Test
    @DisplayName("getContentIdString: youtube.com/shorts URL → 11-char video id")
    void youtubeShortsUrl() {
        assertEquals("dQw4w9WgXcQ",
                ContentBindingToken.getContentIdString("https://youtube.com/shorts/dQw4w9WgXcQ", true));
    }

    @Test
    @DisplayName("getContentIdString(youtubeOnly=true) on non-YouTube URL → null")
    void nonYoutubeWithYoutubeOnly() {
        assertNull(ContentBindingToken.getContentIdString("https://example.com/page", true));
    }

    @Test
    @DisplayName("getContentIdString(youtubeOnly=false) on non-YouTube URL → matched text verbatim")
    void nonYoutubeFallsBackToMatched() {
        assertEquals("https://example.com/page",
                ContentBindingToken.getContentIdString("https://example.com/page", false));
    }

    @Test
    @DisplayName("getContentIdString: null / empty input → null")
    void nullEmptyInput() {
        assertNull(ContentBindingToken.getContentIdString(null, false));
        assertNull(ContentBindingToken.getContentIdString("", false));
    }

    @Test
    @DisplayName("resolveContentId: returns UTF-8 bytes of the resolved id")
    void resolveContentIdReturnsBytes() {
        var bytes = ContentBindingToken.resolveContentId("https://example.com/page");
        assertArrayEquals("https://example.com/page".getBytes(StandardCharsets.UTF_8), bytes);
    }

    @Test
    @DisplayName("resolveContentId: null input throws NullPointerException")
    void resolveContentIdNullThrows() {
        assertThrows(NullPointerException.class, () -> ContentBindingToken.resolveContentId(null));
    }
}
