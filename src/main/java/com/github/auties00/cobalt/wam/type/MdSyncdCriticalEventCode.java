package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum MdSyncdCriticalEventCode {
    @WamEnumConstant(1) MESSAGE_RANGE_UNSET,
    @WamEnumConstant(2) MESSAGE_RANGE_LAST_SYSTEM_MESSAGE_TIMESTAMP_SET,
    @WamEnumConstant(3) MESSAGE_RANGE_MESSAGES_UNSET,
    @WamEnumConstant(4) MESSAGE_RANGE_MESSAGES_EMPTY,
    @WamEnumConstant(5) MESSAGE_RANGE_MESSAGES_CROSS_LIMIT,
    @WamEnumConstant(6) MESSAGE_RANGE_MESSAGE_KEY_UNSET,
    @WamEnumConstant(7) MESSAGE_RANGE_MESSAGE_KEY_REMOTE_JID_UNSET,
    @WamEnumConstant(8) MESSAGE_RANGE_MESSAGE_KEY_FROM_ME_UNSET,
    @WamEnumConstant(9) MESSAGE_RANGE_MESSAGE_KEY_STANZA_ID_UNSET,
    @WamEnumConstant(10) MESSAGE_RANGE_MESSAGE_KEY_REMOTE_JID_INVALID,
    @WamEnumConstant(11) MESSAGE_RANGE_MESSAGE_KEY_PARTICIPANT_UNSET,
    @WamEnumConstant(12) MALFORMED_PENDING_MUTATION,
    @WamEnumConstant(13) ACTION_INVALID_INDEX_DATA,
    @WamEnumConstant(14) MISSING_MUTATION_TO_REMOVE,
    @WamEnumConstant(15) LTHASH_INCONSISTENCY_ON_DAILY_CHECK,
    @WamEnumConstant(16) LTHASH_INCONSISTENCY_ON_SNAPSHOT_MAC_MISMATCH,
    @WamEnumConstant(17) NO_CONFIRMED_SET_MUTATION_FOR_A_PENDING_REMOVE,
    @WamEnumConstant(18) NO_KEY_DATA_FOR_A_PENDING_REMOVE_MUTATION
}
