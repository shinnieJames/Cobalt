package com.github.auties00.cobalt.model.message.system.peer;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;

@ProtobufEnum(name = "Message.PeerDataOperationRequestType")
public enum PeerDataOperationRequestType {
    UPLOAD_STICKER(0),
    SEND_RECENT_STICKER_BOOTSTRAP(1),
    GENERATE_LINK_PREVIEW(2),
    HISTORY_SYNC_ON_DEMAND(3),
    PLACEHOLDER_MESSAGE_RESEND(4),
    WAFFLE_LINKING_NONCE_FETCH(5),
    FULL_HISTORY_SYNC_ON_DEMAND(6),
    COMPANION_META_NONCE_FETCH(7),
    COMPANION_SYNCD_SNAPSHOT_FATAL_RECOVERY(8),
    COMPANION_CANONICAL_USER_NONCE_FETCH(9),
    HISTORY_SYNC_CHUNK_RETRY(10),
    GALAXY_FLOW_ACTION(11);

    PeerDataOperationRequestType(@ProtobufEnumIndex int index) {
        this.index = index;
    }

    final int index;

    public int index() {
        return this.index;
    }
}
