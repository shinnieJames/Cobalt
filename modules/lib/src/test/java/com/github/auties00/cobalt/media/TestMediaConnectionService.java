package com.github.auties00.cobalt.media;

import com.github.auties00.cobalt.exception.WhatsAppMediaException;
import com.github.auties00.cobalt.model.media.MediaPath;
import com.github.auties00.cobalt.model.media.MediaProvider;
import com.github.auties00.cobalt.node.Node;

import java.io.InputStream;
import java.util.SequencedCollection;

/**
 * Test-only {@link MediaConnectionService} that throws
 * {@link UnsupportedOperationException} from every method.
 *
 * @apiNote
 * Used by tests that need a {@link MediaConnectionService} collaborator to
 * satisfy a constructor without exercising the CDN. Defaults are
 * deliberately loud: any unexpected media-connection call fails the test
 * instead of silently returning {@code null} or blocking forever on the
 * real service's first-refresh latch.
 *
 * @implNote
 * This implementation has no installable handlers; every method raises an
 * {@link UnsupportedOperationException} carrying the method name so the
 * test report points directly at the unexpected call.
 */
public final class TestMediaConnectionService implements MediaConnectionService {
    /**
     * Returns a new test service with every method throwing.
     *
     * @apiNote
     * Entry point for every consumer; do not call the constructor directly.
     *
     * @return the new test service
     */
    public static TestMediaConnectionService create() {
        return new TestMediaConnectionService();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; the test service installs no
     * credentials.
     */
    @Override
    public void update(Node response) {
        throw unsupported("update");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; the test service performs no CDN
     * transfers.
     */
    @Override
    public boolean upload(MediaProvider provider, MediaPayload payload) {
        throw unsupported("upload");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; the test service performs no CDN
     * transfers.
     */
    @Override
    public InputStream download(MediaProvider provider) {
        throw unsupported("download");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; the test service performs no CDN
     * transfers.
     */
    @Override
    public InputStream tryDownload(MediaProvider provider, String downloadUrl) {
        throw unsupported("tryDownload");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; the test service performs no CDN
     * transfers.
     */
    @Override
    public void checkExistence(String hostname, MediaPath mediaType, String directPath, String encFileHash) {
        throw unsupported("checkExistence");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; the test service performs no CDN
     * transfers.
     */
    @Override
    public long getEncryptedMediaSize(String hostname, MediaPath mediaType, String directPath, String encFileHash) {
        throw unsupported("getEncryptedMediaSize");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; the test service performs no CDN
     * transfers.
     */
    @Override
    public void deleteHistorySyncBlob(String directPath, byte[] encFilehash, String encHandle, String companionMmsAuthNonce) {
        throw unsupported("deleteHistorySyncBlob");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; the test service installs no
     * credentials.
     */
    @Override
    public String auth() {
        throw unsupported("auth");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; the test service installs no
     * credentials.
     */
    @Override
    public int ttl() {
        throw unsupported("ttl");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; the test service installs no
     * credentials.
     */
    @Override
    public int authTtl() {
        throw unsupported("authTtl");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; the test service installs no
     * credentials.
     */
    @Override
    public int maxBuckets() {
        throw unsupported("maxBuckets");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; the test service installs no
     * credentials.
     */
    @Override
    public int maxManualRetry() {
        throw unsupported("maxManualRetry");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; the test service installs no
     * credentials.
     */
    @Override
    public int maxAutoDownloadRetry() {
        throw unsupported("maxAutoDownloadRetry");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; the test service installs no
     * credentials.
     */
    @Override
    public long timestamp() {
        throw unsupported("timestamp");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; the test service installs no
     * credentials.
     */
    @Override
    public SequencedCollection<? extends MediaHost> hosts() {
        throw unsupported("hosts");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; the test service installs no
     * credentials.
     */
    @Override
    public boolean isExpired() {
        throw unsupported("isExpired");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always throws; the test service installs no
     * credentials.
     */
    @Override
    public boolean needsRefresh() {
        throw unsupported("needsRefresh");
    }

    /**
     * Builds the failure thrown by every method.
     *
     * @apiNote
     * Internal helper; the method name is embedded in the message so the
     * test report points directly at the unexpected call.
     *
     * @param method the method name
     * @return the configured exception
     */
    private static UnsupportedOperationException unsupported(String method) {
        return new UnsupportedOperationException("TestMediaConnectionService." + method + " is not stubbed");
    }
}
