package com.github.auties00.cobalt.model.sync.action.setting;

import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Optional;

/**
 * A sync action representing the NCT (Notification Content Tokenizer) salt
 * used for privacy-preserving notification content processing.
 *
 * <p>Per WhatsApp Web, the salt is synced via the {@code REGULAR_HIGH}
 * collection and stored in the user preferences IndexedDB storage.
 */
@ProtobufMessage(name = "SyncActionValue.NctSaltSyncAction")
public final class NctSaltSyncAction implements SyncAction<NctSaltSyncActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "nct_salt_sync";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 1;

    /**
     * Canonical WhatsApp Web collection name for this action type.
     */
    public static final SyncPatchType COLLECTION_NAME = SyncPatchType.REGULAR_HIGH;

    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] salt;

    NctSaltSyncAction(byte[] salt) {
        this.salt = salt;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String actionName() {
        return ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int actionVersion() {
        return ACTION_VERSION;
    }

    /**
     * Returns the NCT salt bytes.
     *
     * @return the salt bytes, or empty if not set
     */
    public Optional<byte[]> salt() {
        return Optional.ofNullable(salt);
    }
}
