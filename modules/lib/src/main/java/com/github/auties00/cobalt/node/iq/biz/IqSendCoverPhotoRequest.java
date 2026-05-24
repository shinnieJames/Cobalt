package com.github.auties00.cobalt.node.iq.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.iq.IqOperation;
import java.util.Arrays;
import java.util.Objects;

/**
 * The outbound {@code <iq xmlns="w:biz" type="set">} stanza that attaches
 * a previously-uploaded cover photo to the current merchant's business
 * profile.
 *
 * @apiNote
 * Use this request from the cover-photo edit surface after the binary
 * upload has succeeded and the mediaWeb upload service has returned the
 * {@code (id, ts, token)} triple identifying the artefact; the
 * {@code WAWebBizCoverPhotoAction.setCoverPhoto} delegate ships this
 * stanza right after the upload completes.
 */
@WhatsAppWebModule(moduleName = "WAWebBusinessProfileJob")
public final class IqSendCoverPhotoRequest implements IqOperation.Request {
    /**
     * The upload id stamped into the {@code id} attribute of the
     * {@code <cover_photo/>} grandchild.
     */
    private final long id;

    /**
     * The upload timestamp (Unix seconds) stamped into the {@code ts}
     * attribute of the {@code <cover_photo/>} grandchild.
     */
    private final long ts;

    /**
     * The opaque upload token stamped into the {@code token} attribute
     * of the {@code <cover_photo/>} grandchild; the relay uses it to
     * validate that the upload artefact still exists and belongs to
     * the calling user.
     */
    private final byte[] token;

    /**
     * Constructs a request.
     *
     * @apiNote
     * Pass the {@code (id, ts, token)} triple returned by the mediaWeb
     * upload service; the token is defensively cloned so the caller's
     * buffer is not retained.
     *
     * @param id    the upload id
     * @param ts    the upload timestamp
     * @param token the upload token; never {@code null}
     * @throws NullPointerException if {@code token} is {@code null}
     */
    public IqSendCoverPhotoRequest(long id, long ts, byte[] token) {
        this.id = id;
        this.ts = ts;
        Objects.requireNonNull(token, "token cannot be null");
        this.token = token.clone();
    }

    /**
     * Returns the upload id.
     *
     * @apiNote
     * Use this getter to read back the upload id the stanza will
     * stamp; the value is taken verbatim from the mediaWeb upload
     * service response.
     *
     * @return the id
     */
    public long id() {
        return id;
    }

    /**
     * Returns the upload timestamp.
     *
     * @apiNote
     * Use this getter to read back the upload timestamp the stanza
     * will stamp; the value is taken verbatim from the mediaWeb upload
     * service response.
     *
     * @return the timestamp
     */
    public long ts() {
        return ts;
    }

    /**
     * Returns a defensive copy of the upload token.
     *
     * @apiNote
     * Use this getter to read back the upload token the stanza will
     * stamp; the returned array is a fresh clone so callers may mutate
     * it freely without disturbing this request.
     *
     * @return the token; never {@code null}
     */
    public byte[] token() {
        return token.clone();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation materialises the WAP envelope produced by
     * the {@code WAWebBusinessProfileJob.sendCoverPhoto} export: a
     * {@code <cover_photo op="update" id ts token/>} grandchild wrapped
     * in a {@code <business_profile v="3" mutation_type="delta"/>}
     * envelope and a {@code w:biz set} IQ frame routed to the WhatsApp
     * service.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBusinessProfileJob",
            exports = "sendCoverPhoto", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var coverPhotoNode = new NodeBuilder()
                .description("cover_photo")
                .attribute("op", "update")
                .attribute("id", String.valueOf(id))
                .attribute("ts", String.valueOf(ts))
                .attribute("token", token)
                .build();
        var businessProfileNode = new NodeBuilder()
                .description("business_profile")
                .attribute("v", "3")
                .attribute("mutation_type", "delta")
                .content(coverPhotoNode)
                .build();
        return new NodeBuilder()
                .description("iq")
                .attribute("xmlns", "w:biz")
                .attribute("to", JidServer.user())
                .attribute("type", "set")
                .content(businessProfileNode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (IqSendCoverPhotoRequest) obj;
        return this.id == that.id
                && this.ts == that.ts
                && Arrays.equals(this.token, that.token);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        var h = Objects.hash(id, ts);
        return 31 * h + Arrays.hashCode(token);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "IqSendCoverPhotoRequest[id=" + id + ", ts=" + ts + ']';
    }
}
