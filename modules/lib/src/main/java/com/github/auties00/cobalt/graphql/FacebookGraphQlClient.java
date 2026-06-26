package com.github.auties00.cobalt.graphql;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.github.auties00.cobalt.exception.WhatsAppServerRuntimeException;
import com.github.auties00.cobalt.graphql.facebook.FacebookGraphQlOperation;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Dispatches {@link FacebookGraphQlOperation.Request} operations over WhatsApp Web's {@code http_comet}
 * GraphQL transport.
 *
 * <p>The Facebook GraphQL transport reaches Meta's graph endpoint rather than a WhatsApp server: it issues a
 * {@code POST https://graph.facebook.com/graphql} whose {@code application/x-www-form-urlencoded}
 * body carries the per-user Facebook {@code access_token}, the persisted {@code doc_id}, the
 * JSON-encoded {@code variables} and the remapped {@code locale}. The response is read as text, the
 * leading {@code for(;;);} anti-hijack prefix is stripped, the JSON is parsed, and the GraphQL
 * {@code data} object is returned. GraphQL and HTTP errors are surfaced as
 * {@link WhatsAppServerRuntimeException}.
 *
 * <p>Authentication is the Facebook {@code access_token} minted for the linked WhatsApp account; it
 * is supplied to this client as a constructor input. WhatsApp Web obtains it over the WhatsApp socket
 * (no facebook.com browser login), so an embedder must acquire it through that channel and pass it
 * here.
 *
 * @implNote This implementation models the {@code access_token}-bearing graph flavor used by
 * {@code WAWebRelayEnvironment} for the facebook environment type, which sends an
 * {@code access_token}+{@code doc_id}+{@code variables}+{@code locale} body. WhatsApp Web's
 * Comet ad-creation flows additionally route through a separate relay-fb network layer that adds FB
 * Comet parameters ({@code fb_dtsg}, {@code fb_api_caller_class}, {@code fb_api_req_friendly_name},
 * {@code server_timestamps}); that layer is loaded on demand and is not present in the analysed
 * static bundle, so those parameters are not emitted here. Capture a live {@code graph.facebook.com}
 * request to recover them if an operation is rejected without them.
 */
@WhatsAppWebModule(moduleName = "CometRelay")
@WhatsAppWebModule(moduleName = "WAWebAdsRelayEnvironment")
public final class FacebookGraphQlClient {
    /**
     * The fixed Meta graph endpoint every Facebook GraphQL operation targets.
     */
    private static final URI ENDPOINT = URI.create("https://graph.facebook.com/graphql");

    /**
     * Matches the leading {@code for(;;);} anti-JSON-hijack prefix Meta prepends to graph responses.
     */
    private static final Pattern XSSI_PREFIX = Pattern.compile("^for\\s*\\(\\s*;;\\s*\\)\\s*;\\s*");

    /**
     * The HTTP client used for the {@code POST}, reused across dispatches for connection pooling.
     */
    private final HttpClient httpClient;

    /**
     * The Facebook {@code access_token} minted over the WhatsApp socket for the linked account.
     */
    private final String accessToken;

    /**
     * The remapped locale (for example {@code en_US}) sent as the {@code locale} body field.
     */
    private final String locale;

    /**
     * Constructs a Facebook GraphQL client backed by a default-configured {@link HttpClient}.
     *
     * @param accessToken the Facebook access token minted over the WhatsApp socket
     * @param locale      the remapped locale, for example {@code en_US}
     * @throws NullPointerException if any argument is {@code null}
     */
    public FacebookGraphQlClient(String accessToken, String locale) {
        this(HttpClient.newHttpClient(), accessToken, locale);
    }

    /**
     * Constructs a Facebook GraphQL client backed by a caller-supplied {@link HttpClient}.
     *
     * <p>Intended for tests that drive the client with a recording {@link HttpClient} stub, or for
     * embedders that want to share a connection pool with other Cobalt subsystems.
     *
     * @param httpClient  the HTTP client to use
     * @param accessToken the Facebook access token minted over the WhatsApp socket
     * @param locale      the remapped locale, for example {@code en_US}
     * @throws NullPointerException if any argument is {@code null}
     */
    public FacebookGraphQlClient(HttpClient httpClient, String accessToken, String locale) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.accessToken = Objects.requireNonNull(accessToken, "accessToken must not be null");
        this.locale = Objects.requireNonNull(locale, "locale must not be null");
    }

    /**
     * Dispatches the given Facebook GraphQL operation and returns the unwrapped GraphQL {@code data} object.
     *
     * <p>Encodes the url-encoded request body, POSTs it to {@link #ENDPOINT}, strips the
     * {@code for(;;);} prefix from the response, and returns the GraphQL {@code data} object. A
     * non-2xx status, an unparsable body, or a non-empty {@code errors} array each raise
     * {@link WhatsAppServerRuntimeException}.
     *
     * @param request the Facebook GraphQL operation to dispatch
     * @return the unwrapped GraphQL {@code data} object, never {@code null}
     * @throws NullPointerException           if {@code request} is {@code null}
     * @throws WhatsAppServerRuntimeException if the transport fails, the body cannot be parsed, or the
     *                                        graph endpoint reports GraphQL errors
     */
    @WhatsAppWebExport(moduleName = "WAWebAdsRelayEnvironment", exports = "getEnvironment",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public JSONObject send(FacebookGraphQlOperation.Request request) {
        Objects.requireNonNull(request, "request must not be null");

        var httpRequest = HttpRequest.newBuilder(ENDPOINT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encodeBody(request)))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new WhatsAppServerRuntimeException("Facebook GraphQL request failed for " + request.name(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new WhatsAppServerRuntimeException("Facebook GraphQL request interrupted for " + request.name(), exception);
        }

        return parse(request, response);
    }

    /**
     * Encodes the url-encoded request body for the given operation.
     *
     * @param request the Facebook GraphQL operation being dispatched
     * @return the {@code application/x-www-form-urlencoded} body string
     */
    private String encodeBody(FacebookGraphQlOperation.Request request) {
        var params = new LinkedHashMap<String, String>();
        params.put("access_token", accessToken);
        params.put("doc_id", request.docId());
        params.put("variables", request.variables());
        params.put("locale", locale);

        var body = new StringBuilder();
        for (var entry : params.entrySet()) {
            if (!body.isEmpty()) {
                body.append('&');
            }
            body.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return body.toString();
    }

    /**
     * Parses a Facebook GraphQL HTTP response into the unwrapped GraphQL {@code data} object.
     *
     * @param request  the Facebook GraphQL operation that produced the response, used for error messages
     * @param response the HTTP response
     * @return the unwrapped GraphQL {@code data} object, never {@code null}
     * @throws WhatsAppServerRuntimeException if the body is unparsable, the status is non-2xx, or the
     *                                        graph endpoint reports GraphQL errors
     */
    private JSONObject parse(FacebookGraphQlOperation.Request request, HttpResponse<String> response) {
        var text = XSSI_PREFIX.matcher(response.body()).replaceFirst("");
        JSONObject json;
        try {
            json = JSON.parseObject(text);
        } catch (RuntimeException exception) {
            throw new WhatsAppServerRuntimeException("failed to parse Facebook GraphQL response for " + request.name(), exception);
        }
        if (json == null) {
            throw new WhatsAppServerRuntimeException("empty Facebook GraphQL response for " + request.name());
        }

        var status = response.statusCode();
        if (status < 200 || status >= 300) {
            var error = json.getJSONObject("error");
            var detail = error != null ? error.getString("message") : status + " " + response.body();
            throw new WhatsAppServerRuntimeException("Facebook GraphQL request for " + request.name() + " failed: " + detail);
        }

        var errors = json.getJSONArray("errors");
        if (errors != null && !errors.isEmpty()) {
            throw new WhatsAppServerRuntimeException("Facebook GraphQL request for " + request.name() + " returned errors: " + describeErrors(errors));
        }

        var data = json.getJSONObject("data");
        return data != null ? data : json;
    }

    /**
     * Renders a graph error array into a compact, single-line diagnostic string.
     *
     * @param errors the {@code errors} array from the graph response
     * @return a human-readable summary of the error codes and messages
     */
    private static String describeErrors(JSONArray errors) {
        var summary = new StringBuilder();
        for (var i = 0; i < errors.size(); i++) {
            var error = errors.getJSONObject(i);
            if (error == null) {
                continue;
            }
            if (!summary.isEmpty()) {
                summary.append("; ");
            }
            summary.append('[').append(error.getInteger("code")).append("] ").append(error.getString("message"));
        }
        return summary.toString();
    }
}
