package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.ChatMutedType;
import com.github.auties00.cobalt.wam.type.ChatType;
import com.github.auties00.cobalt.wam.type.GaStatus;
import com.github.auties00.cobalt.wam.type.OppositeVisibleIdentificationType;
import com.github.auties00.cobalt.wam.type.TypeOfGroupEnum;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 6466)
public interface ThreadInteractionDataCoreConsumerEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt audioMessagesReceived();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt audioMessagesSent();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt broadcastMsgsReceived();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt broadcastMsgsSent();

    @WamProperty(index = 6, type = WamType.INTEGER)
    OptionalInt chatEphemeralityDuration();

    @WamProperty(index = 7, type = WamType.ENUM)
    Optional<ChatMutedType> chatMuted();

    @WamProperty(index = 8, type = WamType.INTEGER)
    OptionalInt chatOverflowClicks();

    @WamProperty(index = 9, type = WamType.ENUM)
    Optional<ChatType> chatTypeInd();

    @WamProperty(index = 10, type = WamType.INTEGER)
    OptionalInt commentsReceived();

    @WamProperty(index = 13, type = WamType.INTEGER)
    OptionalInt documentMessagesReceived();

    @WamProperty(index = 14, type = WamType.INTEGER)
    OptionalInt documentMessagesSent();

    @WamProperty(index = 15, type = WamType.INTEGER)
    OptionalInt editedMsgsSent();

    @WamProperty(index = 110, type = WamType.INTEGER)
    OptionalInt ephemeralMessagesExpired();

    @WamProperty(index = 16, type = WamType.INTEGER)
    OptionalInt ephemeralMessagesReceived();

    @WamProperty(index = 17, type = WamType.INTEGER)
    OptionalInt ephemeralMessagesSent();

    @WamProperty(index = 18, type = WamType.INTEGER)
    OptionalInt ephemeralMessagesUnreadExpired();

    @WamProperty(index = 21, type = WamType.INTEGER)
    OptionalInt eventCreationMessagesReceived();

    @WamProperty(index = 22, type = WamType.INTEGER)
    OptionalInt eventCreationMessagesSent();

    @WamProperty(index = 23, type = WamType.INTEGER)
    OptionalInt eventResponseMessagesReceived();

    @WamProperty(index = 24, type = WamType.INTEGER)
    OptionalInt eventResponseMessagesSent();

    @WamProperty(index = 25, type = WamType.INTEGER)
    OptionalInt forwardAudioMessagesReceived();

    @WamProperty(index = 26, type = WamType.INTEGER)
    OptionalInt forwardAudioMessagesSent();

    @WamProperty(index = 27, type = WamType.INTEGER)
    OptionalInt forwardDocumentMessagesReceived();

    @WamProperty(index = 28, type = WamType.INTEGER)
    OptionalInt forwardDocumentMessagesSent();

    @WamProperty(index = 29, type = WamType.INTEGER)
    OptionalInt forwardGifMessagesReceived();

    @WamProperty(index = 30, type = WamType.INTEGER)
    OptionalInt forwardGifMessagesSent();

    @WamProperty(index = 31, type = WamType.INTEGER)
    OptionalInt forwardMessagesReceived();

    @WamProperty(index = 32, type = WamType.INTEGER)
    OptionalInt forwardMessagesSent();

    @WamProperty(index = 33, type = WamType.INTEGER)
    OptionalInt forwardPhotoMessagesReceived();

    @WamProperty(index = 34, type = WamType.INTEGER)
    OptionalInt forwardPhotoMessagesSent();

    @WamProperty(index = 35, type = WamType.INTEGER)
    OptionalInt forwardStickerMessagesReceived();

    @WamProperty(index = 36, type = WamType.INTEGER)
    OptionalInt forwardStickerMessagesSent();

    @WamProperty(index = 37, type = WamType.INTEGER)
    OptionalInt forwardTextMessagesReceived();

    @WamProperty(index = 38, type = WamType.INTEGER)
    OptionalInt forwardTextMessagesSent();

    @WamProperty(index = 39, type = WamType.INTEGER)
    OptionalInt forwardUrlMessagesReceived();

    @WamProperty(index = 40, type = WamType.INTEGER)
    OptionalInt forwardUrlMessagesSent();

    @WamProperty(index = 41, type = WamType.INTEGER)
    OptionalInt forwardVideoMessagesReceived();

    @WamProperty(index = 42, type = WamType.INTEGER)
    OptionalInt forwardVideoMessagesSent();

    @WamProperty(index = 43, type = WamType.ENUM)
    Optional<GaStatus> gaStatus();

    @WamProperty(index = 44, type = WamType.INTEGER)
    OptionalInt gifMessagesReceived();

    @WamProperty(index = 45, type = WamType.INTEGER)
    OptionalInt gifMessagesSent();

    @WamProperty(index = 47, type = WamType.INTEGER)
    OptionalInt groupMembershipReplies();

    @WamProperty(index = 48, type = WamType.INTEGER)
    OptionalInt groupPrivateReplies();

    @WamProperty(index = 49, type = WamType.INTEGER)
    OptionalInt groupSize();

    @WamProperty(index = 113, type = WamType.INTEGER)
    OptionalInt groupStatusLikesOthersToOthers();

    @WamProperty(index = 114, type = WamType.INTEGER)
    OptionalInt groupStatusLikesOthersToOwn();

    @WamProperty(index = 115, type = WamType.INTEGER)
    OptionalInt groupStatusLikesOwnToOthers();

    @WamProperty(index = 116, type = WamType.INTEGER)
    OptionalInt groupStatusLikesOwnToOwn();

    @WamProperty(index = 117, type = WamType.INTEGER)
    OptionalInt groupStatusRepliesOthersToOthers();

    @WamProperty(index = 118, type = WamType.INTEGER)
    OptionalInt groupStatusRepliesOthersToOwn();

    @WamProperty(index = 119, type = WamType.INTEGER)
    OptionalInt groupStatusRepliesOwnToOthers();

    @WamProperty(index = 120, type = WamType.INTEGER)
    OptionalInt groupStatusRepliesOwnToOwn();

    @WamProperty(index = 50, type = WamType.BOOLEAN)
    Optional<Boolean> hasUsername();

    @WamProperty(index = 51, type = WamType.BOOLEAN)
    Optional<Boolean> hasUsernamePin();

    @WamProperty(index = 52, type = WamType.BOOLEAN)
    Optional<Boolean> isAContact();

    @WamProperty(index = 53, type = WamType.BOOLEAN)
    Optional<Boolean> isAContactAtThreadCreation();

    @WamProperty(index = 54, type = WamType.BOOLEAN)
    Optional<Boolean> isAGroup();

    @WamProperty(index = 55, type = WamType.BOOLEAN)
    Optional<Boolean> isArchived();

    @WamProperty(index = 57, type = WamType.BOOLEAN)
    Optional<Boolean> isDeleted();

    @WamProperty(index = 112, type = WamType.BOOLEAN)
    Optional<Boolean> isGuestThread();

    @WamProperty(index = 58, type = WamType.BOOLEAN)
    Optional<Boolean> isInviteCreatedThread();

    @WamProperty(index = 121, type = WamType.BOOLEAN)
    Optional<Boolean> isManagedAccount();

    @WamProperty(index = 59, type = WamType.BOOLEAN)
    Optional<Boolean> isMetaAiAssistant();

    @WamProperty(index = 122, type = WamType.BOOLEAN)
    Optional<Boolean> isNewManagedAccountEmIgnored();

    @WamProperty(index = 60, type = WamType.BOOLEAN)
    Optional<Boolean> isPinned();

    @WamProperty(index = 61, type = WamType.BOOLEAN)
    Optional<Boolean> isPnhEnabledChat();

    @WamProperty(index = 123, type = WamType.BOOLEAN)
    Optional<Boolean> isUsernameThread();

    @WamProperty(index = 124, type = WamType.BOOLEAN)
    Optional<Boolean> limitSharingOption();

    @WamProperty(index = 62, type = WamType.INTEGER)
    OptionalInt markedReadCnt();

    @WamProperty(index = 63, type = WamType.INTEGER)
    OptionalInt markedReadMessageCnt();

    @WamProperty(index = 64, type = WamType.INTEGER)
    OptionalInt messagesRead();

    @WamProperty(index = 65, type = WamType.INTEGER)
    OptionalInt messagesReceived();

    @WamProperty(index = 66, type = WamType.INTEGER)
    OptionalInt messagesSent();

    @WamProperty(index = 68, type = WamType.INTEGER)
    OptionalInt messagesUnread();

    @WamProperty(index = 125, type = WamType.BOOLEAN)
    Optional<Boolean> oppositePartyLimitSharingOption();

    @WamProperty(index = 69, type = WamType.ENUM)
    Optional<OppositeVisibleIdentificationType> oppositeVisibleIdentification();

    @WamProperty(index = 70, type = WamType.INTEGER)
    OptionalInt photoMessagesReceived();

    @WamProperty(index = 71, type = WamType.INTEGER)
    OptionalInt photoMessagesSent();

    @WamProperty(index = 72, type = WamType.INTEGER)
    OptionalInt pollCreationMessagesReceived();

    @WamProperty(index = 73, type = WamType.INTEGER)
    OptionalInt pollCreationMessagesSent();

    @WamProperty(index = 74, type = WamType.INTEGER)
    OptionalInt pollUpdateMessagesReceived();

    @WamProperty(index = 75, type = WamType.INTEGER)
    OptionalInt pollUpdateMessagesSent();

    @WamProperty(index = 76, type = WamType.INTEGER)
    OptionalInt profileReplies();

    @WamProperty(index = 77, type = WamType.INTEGER)
    OptionalInt profileViews();

    @WamProperty(index = 78, type = WamType.INTEGER)
    OptionalInt pttMessagesReceived();

    @WamProperty(index = 79, type = WamType.INTEGER)
    OptionalInt pttMessagesSent();

    @WamProperty(index = 80, type = WamType.INTEGER)
    OptionalInt ptvMessagesReceived();

    @WamProperty(index = 81, type = WamType.INTEGER)
    OptionalInt ptvMessagesSent();

    @WamProperty(index = 82, type = WamType.INTEGER)
    OptionalInt reactionsReceived();

    @WamProperty(index = 83, type = WamType.INTEGER)
    OptionalInt reactionsSent();

    @WamProperty(index = 85, type = WamType.INTEGER)
    OptionalInt repliesSent();

    @WamProperty(index = 86, type = WamType.BOOLEAN)
    Optional<Boolean> requestedPhoneNumber();

    @WamProperty(index = 87, type = WamType.BOOLEAN)
    Optional<Boolean> seenMaskedPhoneNumber();

    @WamProperty(index = 89, type = WamType.BOOLEAN)
    Optional<Boolean> sharedPhoneNumber();

    @WamProperty(index = 107, type = WamType.BOOLEAN)
    Optional<Boolean> sharesCommonGroup();

    @WamProperty(index = 90, type = WamType.INTEGER)
    OptionalInt statusReactionsReceived();

    @WamProperty(index = 126, type = WamType.INTEGER)
    OptionalInt statusReactionsSent();

    @WamProperty(index = 91, type = WamType.INTEGER)
    OptionalInt statusReplies();

    @WamProperty(index = 127, type = WamType.INTEGER)
    OptionalInt statusReplyMessagesReceived();

    @WamProperty(index = 92, type = WamType.INTEGER)
    OptionalInt statusViews();

    @WamProperty(index = 93, type = WamType.INTEGER)
    OptionalInt stickerMessagesReceived();

    @WamProperty(index = 94, type = WamType.INTEGER)
    OptionalInt stickerMessagesSent();

    @WamProperty(index = 95, type = WamType.INTEGER)
    OptionalInt textMessagesReceived();

    @WamProperty(index = 96, type = WamType.INTEGER)
    OptionalInt textMessagesSent();

    @WamProperty(index = 111, type = WamType.STRING)
    Optional<String> threadCreationDate();

    @WamProperty(index = 97, type = WamType.STRING)
    Optional<String> threadDs();

    @WamProperty(index = 98, type = WamType.STRING)
    Optional<String> threadId();

    @WamProperty(index = 109, type = WamType.STRING)
    Optional<String> threadIdByLid();

    @WamProperty(index = 128, type = WamType.INTEGER)
    OptionalInt tombstoneEphemeralMessagesReceived();

    @WamProperty(index = 129, type = WamType.INTEGER)
    OptionalInt tombstoneViewOnceMessagesReceived();

    @WamProperty(index = 108, type = WamType.ENUM)
    Optional<TypeOfGroupEnum> typeOfGroup();

    @WamProperty(index = 99, type = WamType.INTEGER)
    OptionalInt urlMessagesReceived();

    @WamProperty(index = 100, type = WamType.INTEGER)
    OptionalInt urlMessagesSent();

    @WamProperty(index = 102, type = WamType.INTEGER)
    OptionalInt videoMessagesReceived();

    @WamProperty(index = 103, type = WamType.INTEGER)
    OptionalInt videoMessagesSent();

    @WamProperty(index = 104, type = WamType.INTEGER)
    OptionalInt viewOnceMessagesOpened();

    @WamProperty(index = 105, type = WamType.INTEGER)
    OptionalInt viewOnceMessagesReceived();

    @WamProperty(index = 106, type = WamType.INTEGER)
    OptionalInt viewOnceMessagesSent();
}
