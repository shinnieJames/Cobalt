package com.github.auties00.cobalt.node.iq.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.JidServer;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.iq.IqOperation;

import java.util.Objects;

/**
 * The typed outbound {@code <iq xmlns="w:biz" type="set">} stanza that detaches a previously-uploaded cover photo from the current merchant's business profile.
 *
 * @apiNote
 * Use this request from the SMB profile editor to clear the cover photo previously uploaded via the matching {@code sendCoverPhoto} sibling stanza; the relay accepts the request irrespective of whether the cover photo still exists, and the {@code id} field carries the upload id so the relay can route the matching detach event into the business-profile mutation pipeline.
 */
@WhatsAppWebModule(moduleName = "WAWebBusinessProfileJob")
public final class IqDeleteCoverPhotoRequest implements IqOperation.Request {
    /**
     * The upload identifier of the cover photo to detach, emitted as the {@code id} attribute of {@code <cover_photo op="delete"/>}.
     */
    private final String id;

    /**
     * Constructs a typed request.
     *
     * @apiNote
     * Call this constructor with the upload id of the cover photo that should be detached; the value is the same id stamped by the original cover-photo upload stanza.
     *
     * @param id the upload identifier; never {@code null}
     * @throws NullPointerException if {@code id} is {@code null}
     */
    public IqDeleteCoverPhotoRequest(String id) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
    }

    /**
     * Returns the upload identifier.
     *
     * @apiNote
     * Use this getter to read back the upload id that the stanza will detach.
     *
     * @return the identifier; never {@code null}
     */
    public String id() {
        return id;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation materialises the WAP envelope produced by {@code WAWebBusinessProfileJob.deleteCoverPhoto}: a single {@code <cover_photo op="delete" id/>} child wrapped in a {@code <business_profile v="3" mutation_type="delta"/>} mutation envelope.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebBusinessProfileJob",
            exports = "deleteCoverPhoto", adaptation = WhatsAppAdaptation.DIRECT)
    public NodeBuilder toNode() {
        var coverPhotoNode = new NodeBuilder()
                .description("cover_photo")
                .attribute("op", "delete")
                .attribute("id", id)
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

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (IqDeleteCoverPhotoRequest) obj;
        return Objects.equals(this.id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "IqDeleteCoverPhotoRequest[id=" + id + ']';
    }
}
