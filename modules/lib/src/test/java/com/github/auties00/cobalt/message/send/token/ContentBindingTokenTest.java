package com.github.auties00.cobalt.message.send.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Branch-coverage tests for {@link ContentBindingToken#getContentIdString} and
 * {@link ContentBindingToken#resolveContentId}.
 *
 * @apiNote
 * Mirrors WA Web's {@code WAWebMsgRcatUtils.getContentIdString} (the YouTube
 * id extraction) and {@code WAWebUtilsYoutubeUrlParser.parseYoutubeVideoId}.
 * Covers the canonical URL shapes ({@code youtu.be/},
 * {@code youtube.com/watch?v=}, {@code youtube.com/shorts/}, optional
 * {@code www.} and {@code m.} subdomains), the {@code youtubeOnly} flag
 * branches, and UTF-8 encoding of the resolved id.
 * @implNote
 * Uses a stable canonical YouTube id ({@code dQw4w9WgXcQ}) so the assertions
 * read as content-equality rather than length-or-shape checks. No fixtures.
 */
@DisplayName("ContentBindingToken")
class ContentBindingTokenTest {

    /**
     * Verifies that a canonical {@code youtube.com/watch?v=} URL resolves to
     * the 11-character video id.
     */
    @Test
    @DisplayName("getContentIdString: YouTube watch URL resolves to 11-char video id")
    void youtubeWatchUrl() {
        assertEquals("dQw4w9WgXcQ",
                ContentBindingToken.getContentIdString("https://www.youtube.com/watch?v=dQw4w9WgXcQ", true));
        assertEquals("dQw4w9WgXcQ",
                ContentBindingToken.getContentIdString("https://youtube.com/watch?v=dQw4w9WgXcQ", true));
    }

    /**
     * Verifies that a {@code youtu.be/} short URL resolves to the
     * 11-character video id.
     */
    @Test
    @DisplayName("getContentIdString: youtu.be short URL resolves to 11-char video id")
    void youtubeShortUrl() {
        assertEquals("dQw4w9WgXcQ",
                ContentBindingToken.getContentIdString("https://youtu.be/dQw4w9WgXcQ", true));
    }

    /**
     * Verifies that a {@code youtube.com/shorts/} URL resolves to the
     * 11-character video id.
     */
    @Test
    @DisplayName("getContentIdString: youtube.com/shorts URL resolves to 11-char video id")
    void youtubeShortsUrl() {
        assertEquals("dQw4w9WgXcQ",
                ContentBindingToken.getContentIdString("https://youtube.com/shorts/dQw4w9WgXcQ", true));
    }

    /**
     * Verifies that {@code youtubeOnly = true} on a non-YouTube URL returns
     * {@code null}.
     */
    @Test
    @DisplayName("getContentIdString(youtubeOnly=true) on non-YouTube URL returns null")
    void nonYoutubeWithYoutubeOnly() {
        assertNull(ContentBindingToken.getContentIdString("https://example.com/page", true));
    }

    /**
     * Verifies that {@code youtubeOnly = false} on a non-YouTube URL falls
     * back to the matched text verbatim.
     */
    @Test
    @DisplayName("getContentIdString(youtubeOnly=false) on non-YouTube URL returns matched text verbatim")
    void nonYoutubeFallsBackToMatched() {
        assertEquals("https://example.com/page",
                ContentBindingToken.getContentIdString("https://example.com/page", false));
    }

    /**
     * Verifies that {@code null} and empty input both return {@code null}.
     */
    @Test
    @DisplayName("getContentIdString: null and empty input return null")
    void nullEmptyInput() {
        assertNull(ContentBindingToken.getContentIdString(null, false));
        assertNull(ContentBindingToken.getContentIdString("", false));
    }

    /**
     * Verifies that {@link ContentBindingToken#resolveContentId} encodes the
     * resolved id as UTF-8 bytes.
     */
    @Test
    @DisplayName("resolveContentId: returns UTF-8 bytes of the resolved id")
    void resolveContentIdReturnsBytes() {
        var bytes = ContentBindingToken.resolveContentId("https://example.com/page");
        assertArrayEquals("https://example.com/page".getBytes(StandardCharsets.UTF_8), bytes);
    }

    /**
     * Verifies that {@link ContentBindingToken#resolveContentId} rejects a
     * {@code null} input.
     */
    @Test
    @DisplayName("resolveContentId: null input throws NullPointerException")
    void resolveContentIdNullThrows() {
        assertThrows(NullPointerException.class, () -> ContentBindingToken.resolveContentId(null));
    }
}
