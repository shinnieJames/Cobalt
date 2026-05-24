package com.github.auties00.cobalt.node.usync.result;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

/**
 * Success result of the {@code WAWebUsyncPicture.pictureParser} parser.
 *
 * @apiNote
 * Surfaced by USync queries that include
 * {@code UsyncQuery.withPictureProtocol()}; the WA Web caller is the
 * contact-import verifier, which uses the profile-picture id to fan out
 * thumbnail downloads in parallel with the registered-status check. Pass the
 * id to the media service to fetch the actual JPEG payload.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncPicture")
public final class PictureResult implements UsyncProtocolResponse {
    /**
     * The profile-picture id from the {@code id} attribute on the
     * {@code <picture>} response.
     */
    private final int id;

    /**
     * Creates a new picture result.
     *
     * @apiNote
     * Instantiated by the picture parser; embedders do not call this
     * directly.
     *
     * @param id the profile-picture id
     */
    public PictureResult(int id) {
        this.id = id;
    }

    /**
     * Returns the profile-picture id.
     *
     * @apiNote
     * Opaque server-side identifier; combined with the peer's JID by the
     * media layer to retrieve the actual JPEG.
     *
     * @return the id
     */
    public int id() {
        return id;
    }
}
