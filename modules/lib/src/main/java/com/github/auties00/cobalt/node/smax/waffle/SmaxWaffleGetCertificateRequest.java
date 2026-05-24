package com.github.auties00.cobalt.node.smax.waffle;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * The outbound {@code <iq xmlns="waffle" smax_id="51" type="get"/>}
 * Waffle get-certificate request.
 *
 * @apiNote
 * Powers {@code WAWebAccountLinkingAPI.fetchValidCertificate}, which
 * fetches the Waffle backend's public certificate set before any
 * encrypted Waffle RPC runs (the certificates' public keys feed
 * {@code wrapPayloadWithRSAAESEncryption} when wrapping outbound
 * action payloads, and the password PEM feeds
 * {@code WAWebAccountLinkingCryptoUtils.encryptPassword} when
 * bootstrapping a new linked account). The two boolean flags toggle
 * the optional {@code <payload_enc_certificates/>} and
 * {@code <password_pem/>} markers in the body, telling the relay
 * which PEM subset to return. The reply is parsed by
 * {@link SmaxWaffleGetCertificateResponse}.
 */
@WhatsAppWebModule(moduleName = "WASmaxOutWaffleGetCertificateRequest")
@WhatsAppWebModule(moduleName = "WASmaxOutWaffleBaseIQGetRequestMixin")
public final class SmaxWaffleGetCertificateRequest implements SmaxOperation.Request {
    /**
     * The client wall-clock at request time, in seconds since the
     * Unix epoch.
     */
    private final long timestamp;

    /**
     * Whether to include the {@code <payload_enc_certificates/>}
     * marker; when set, the relay also returns the encryption PEM and
     * signature PEM children.
     */
    private final boolean hasPayloadEncCertificates;

    /**
     * Whether to include the {@code <password_pem/>} marker; when
     * set, the relay also returns the password PEM child.
     */
    private final boolean hasPasswordPem;

    /**
     * Constructs a get-certificate request.
     *
     * @apiNote
     * The two flags are not mutually exclusive: WA Web's
     * {@code fetchValidCertificate} call site sets both to
     * {@code true} so the reply carries the full PEM trio (encryption,
     * signature, password). A request with both flags clear is legal
     * but only fetches the bare timestamp envelope.
     *
     * @param timestamp                 the request timestamp
     * @param hasPayloadEncCertificates whether the
     *                                  {@code <payload_enc_certificates/>}
     *                                  marker is present
     * @param hasPasswordPem            whether the
     *                                  {@code <password_pem/>} marker is
     *                                  present
     */
    public SmaxWaffleGetCertificateRequest(long timestamp, boolean hasPayloadEncCertificates, boolean hasPasswordPem) {
        this.timestamp = timestamp;
        this.hasPayloadEncCertificates = hasPayloadEncCertificates;
        this.hasPasswordPem = hasPasswordPem;
    }

    /**
     * Returns the request timestamp.
     *
     * @return the timestamp as supplied at construction time
     */
    public long timestamp() {
        return timestamp;
    }

    /**
     * Reports whether the {@code <payload_enc_certificates/>} marker
     * is present.
     *
     * @return {@code true} when the marker is present
     */
    public boolean hasPayloadEncCertificates() {
        return hasPayloadEncCertificates;
    }

    /**
     * Reports whether the {@code <password_pem/>} marker is present.
     *
     * @return {@code true} when the marker is present
     */
    public boolean hasPasswordPem() {
        return hasPasswordPem;
    }

    /**
     * Builds the outbound IQ stanza ready for dispatch.
     *
     * @apiNote
     * Produces
     * {@code <iq xmlns="waffle" smax_id="51" type="get" to="s.whatsapp.net">
     * <timestamp.../> [<payload_enc_certificates/>] [<password_pem/>]</iq>};
     * the dispatch path stamps a fresh {@code id} attribute on every
     * outbound stanza so the reply parser can match it back to this
     * request. The two optional markers are appended only when their
     * corresponding flags are {@code true}.
     *
     * @return a {@link NodeBuilder} carrying the IQ envelope; never
     *         {@code null}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WASmaxOutWaffleGetCertificateRequest",
            exports = "makeGetCertificateRequest", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var children = new ArrayList<Node>();
        children.add(new NodeBuilder()
                .description("timestamp")
                .content(timestamp)
                .build());
        if (hasPayloadEncCertificates) {
            children.add(new NodeBuilder()
                    .description("payload_enc_certificates")
                    .build());
        }
        if (hasPasswordPem) {
            children.add(new NodeBuilder()
                    .description("password_pem")
                    .build());
        }
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "waffle")
                .attribute("smax_id", 51)
                .attribute("to", JidServer.user())
                .attribute("type", "get")
                .content(children);
    }

    /**
     * Returns whether the given object is a
     * {@link SmaxWaffleGetCertificateRequest} with equal payload
     * fields.
     *
     * @param obj the candidate; may be {@code null}
     * @return {@code true} when timestamp and both flags match
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxWaffleGetCertificateRequest) obj;
        return this.timestamp == that.timestamp
                && this.hasPayloadEncCertificates == that.hasPayloadEncCertificates
                && this.hasPasswordPem == that.hasPasswordPem;
    }

    /**
     * Returns a hash code derived from the timestamp and the two
     * flags.
     *
     * @return a content-based hash consistent with
     *         {@link #equals(Object)}
     */
    @Override
    public int hashCode() {
        return Objects.hash(timestamp, hasPayloadEncCertificates, hasPasswordPem);
    }

    /**
     * Returns a debug rendering of this request.
     *
     * @return a human-readable summary; never {@code null}
     */
    @Override
    public String toString() {
        return "SmaxWaffleGetCertificateRequest[timestamp=" + timestamp
                + ", hasPayloadEncCertificates=" + hasPayloadEncCertificates
                + ", hasPasswordPem=" + hasPasswordPem + ']';
    }
}
