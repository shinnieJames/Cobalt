package com.github.auties00.cobalt.model.sync.action.privacy;

import com.github.auties00.cobalt.model.sync.SyncActionEmptyArgs;
import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage(name = "SyncActionValue.PrivacySettingDisableLinkPreviewsAction")
public final class PrivacySettingDisableLinkPreviewsAction implements SyncAction<SyncActionEmptyArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "setting_disableLinkPreviews";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 8;

    /**
     * Canonical WhatsApp Web collection name for this action type.
     */
    public static final SyncPatchType COLLECTION_NAME = SyncPatchType.REGULAR;

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


    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean isPreviewsDisabled;


    PrivacySettingDisableLinkPreviewsAction(Boolean isPreviewsDisabled) {
        this.isPreviewsDisabled = isPreviewsDisabled;
    }

    public boolean isPreviewsDisabled() {
        return isPreviewsDisabled != null && isPreviewsDisabled;
    }

    public void setPreviewsDisabled(Boolean isPreviewsDisabled) {
        this.isPreviewsDisabled = isPreviewsDisabled;
    }
}
