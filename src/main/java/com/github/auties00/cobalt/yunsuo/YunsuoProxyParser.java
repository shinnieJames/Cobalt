package com.github.auties00.cobalt.yunsuo;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class YunsuoProxyParser {
    static final String INPUT_EXAMPLE = "SG|45.38.111.38|5953|cfchgwfs|rc97cfzd5e42|2026-06-08 12:45:30";
    private static final DateTimeFormatter EXPIRY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private YunsuoProxyParser() {

    }

    static URI parse(String proxyAddress) {
        if (proxyAddress == null) {
            throw new IllegalArgumentException("Proxy address cannot be null");
        }

        var segments = proxyAddress.split("\\|", -1);
        if (segments.length != 6) {
            throw new IllegalArgumentException("Proxy address must contain 6 segments separated by '|'");
        }

        requireSegment(segments[0], "region");
        var host = requireSegment(segments[1], "host");
        var port = parsePort(segments[2]);
        var username = requireSegment(segments[3], "username");
        var password = requireSegment(segments[4], "password");
        var expiresAt = requireSegment(segments[5], "expiresAt");
        LocalDateTime.parse(expiresAt, EXPIRY_FORMATTER);
        try {
            return new URI("socks5", username + ":" + password, host, port, null, null, null);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Cannot build proxy URI from proxy address", exception);
        }
    }

    private static String requireSegment(String value, String name) {
        var trimmed = value == null ? null : value.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            throw new IllegalArgumentException("Proxy " + name + " cannot be empty");
        }
        return trimmed;
    }

    private static int parsePort(String value) {
        var trimmed = requireSegment(value, "port");
        try {
            var result = Integer.parseInt(trimmed);
            if (result < 1 || result > 65535) {
                throw new IllegalArgumentException("Proxy port must be between 1 and 65535");
            }
            return result;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Proxy port must be a valid integer", exception);
        }
    }
}
