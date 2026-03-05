package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.DeviceType;
import com.github.auties00.cobalt.wam.type.E2eDeviceType;
import com.github.auties00.cobalt.wam.type.EncryptionTypeCode;
import com.github.auties00.cobalt.wam.type.InvisibleMessageCategoryType;
import com.github.auties00.cobalt.wam.type.MediaType;
import com.github.auties00.cobalt.wam.type.MessageType;
import com.github.auties00.cobalt.wam.type.RetryRejectReason;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 3578)
public interface E2eRetryRejectEvent extends WamEventSpec {
    @WamProperty(index = 10, type = WamType.ENUM)
    Optional<E2eDeviceType> e2eSenderType();

    @WamProperty(index = 9, type = WamType.ENUM)
    Optional<EncryptionTypeCode> encryptionType();

    @WamProperty(index = 6, type = WamType.ENUM)
    Optional<InvisibleMessageCategoryType> invisibleMessageCategory();

    @WamProperty(index = 8, type = WamType.BOOLEAN)
    Optional<Boolean> isPq();

    @WamProperty(index = 7, type = WamType.ENUM)
    Optional<MediaType> mediaType();

    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<MessageType> messageType();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt msgRetryCount();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<RetryRejectReason> retryRejectReason();

    @WamProperty(index = 4, type = WamType.BOOLEAN)
    Optional<Boolean> retryRevoke();

    @WamProperty(index = 5, type = WamType.ENUM)
    Optional<DeviceType> senderDeviceType();
}
