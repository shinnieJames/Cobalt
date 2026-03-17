package com.github.auties00.cobalt.model.chat;

import com.github.auties00.cobalt.model.chat.group.GroupParticipant;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidProvider;
import com.github.auties00.cobalt.model.media.MediaVisibility;
import com.github.auties00.cobalt.model.message.PrivacySystemMessage;
import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import com.github.auties00.cobalt.model.setting.WallpaperSettings;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.*;

@ProtobufMessage(name = "Conversation")
public non-sealed abstract class Chat implements JidProvider {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    private Jid jid;

    // Messages are not deserialized by defualt because it's up to the implementation class to decide how to do so
    // @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    // private ... messages;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    private Jid newJid;

    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    private Jid oldJid;

    @ProtobufProperty(index = 5, type = ProtobufType.UINT64, mixins = InstantMillisMixin.class)
    private Instant lastMsgTimestamp;

    @ProtobufProperty(index = 6, type = ProtobufType.UINT32)
    private Integer unreadCount;

    @ProtobufProperty(index = 7, type = ProtobufType.BOOL)
    private Boolean readOnly;

    @ProtobufProperty(index = 8, type = ProtobufType.BOOL)
    private Boolean endOfHistoryTransfer;

    @ProtobufProperty(index = 9, type = ProtobufType.UINT32)
    private ChatEphemeralTimer ephemeralExpiration;

    @ProtobufProperty(index = 10, type = ProtobufType.INT64, mixins = InstantMillisMixin.class)
    private Instant ephemeralSettingTimestamp;

    @ProtobufProperty(index = 11, type = ProtobufType.ENUM)
    private EndOfHistoryTransferType endOfHistoryTransferType;

    @ProtobufProperty(index = 12, type = ProtobufType.UINT64, mixins = InstantMillisMixin.class)
    private Instant conversationTimestamp;

    @ProtobufProperty(index = 13, type = ProtobufType.STRING)
    private String name;

    @ProtobufProperty(index = 14, type = ProtobufType.STRING)
    private String pHash;

    @ProtobufProperty(index = 15, type = ProtobufType.BOOL)
    private Boolean notSpam;

    @ProtobufProperty(index = 16, type = ProtobufType.BOOL)
    private Boolean archived;

    @ProtobufProperty(index = 17, type = ProtobufType.MESSAGE)
    private ChatDisappearingMode disappearingMode;

    @ProtobufProperty(index = 18, type = ProtobufType.UINT32)
    private Integer unreadMentionCount;

    @ProtobufProperty(index = 19, type = ProtobufType.BOOL)
    private Boolean markedAsUnread;

    @ProtobufProperty(index = 20, type = ProtobufType.MESSAGE)
    private List<GroupParticipant> participant;

    @ProtobufProperty(index = 21, type = ProtobufType.BYTES)
    private byte[] tcToken;

    @ProtobufProperty(index = 22, type = ProtobufType.UINT64, mixins = InstantMillisMixin.class)
    private Instant tcTokenTimestamp;

    @ProtobufProperty(index = 23, type = ProtobufType.BYTES)
    private byte[] contactPrimaryIdentityKey;

    @ProtobufProperty(index = 24, type = ProtobufType.UINT32, mixins = InstantSecondsMixin.class)
    private Instant pinnedTimestamp;

    @ProtobufProperty(index = 25, type = ProtobufType.UINT64)
    private ChatMute mute;

    @ProtobufProperty(index = 26, type = ProtobufType.MESSAGE)
    private WallpaperSettings wallpaper;

    @ProtobufProperty(index = 27, type = ProtobufType.ENUM)
    private MediaVisibility mediaVisibility;

    @ProtobufProperty(index = 28, type = ProtobufType.UINT64, mixins = InstantMillisMixin.class)
    private Instant tcTokenSenderTimestamp;

    @ProtobufProperty(index = 29, type = ProtobufType.BOOL)
    private Boolean suspended;

    @ProtobufProperty(index = 30, type = ProtobufType.BOOL)
    private Boolean terminated;

    @ProtobufProperty(index = 31, type = ProtobufType.UINT64)
    private Long createdAt;

    @ProtobufProperty(index = 32, type = ProtobufType.STRING)
    private String createdBy;

    @ProtobufProperty(index = 33, type = ProtobufType.STRING)
    private String description;

    @ProtobufProperty(index = 34, type = ProtobufType.BOOL)
    private Boolean support;

    @ProtobufProperty(index = 35, type = ProtobufType.BOOL)
    private Boolean isParentGroup;

    @ProtobufProperty(index = 37, type = ProtobufType.STRING)
    private String parentGroupId;

    @ProtobufProperty(index = 36, type = ProtobufType.BOOL)
    private Boolean isDefaultSubgroup;

    @ProtobufProperty(index = 38, type = ProtobufType.STRING)
    private String displayName;

    @ProtobufProperty(index = 39, type = ProtobufType.STRING)
    private Jid phoneNumberJid;

    @ProtobufProperty(index = 40, type = ProtobufType.BOOL)
    private Boolean shareOwnPhoneNumber;

    @ProtobufProperty(index = 41, type = ProtobufType.BOOL)
    private Boolean phoneNumberhDuplicateLidThread;

    @ProtobufProperty(index = 42, type = ProtobufType.STRING)
    private Jid lid;

    @ProtobufProperty(index = 43, type = ProtobufType.STRING)
    private String username;

    @ProtobufProperty(index = 44, type = ProtobufType.STRING)
    private String lidOriginType;

    @ProtobufProperty(index = 45, type = ProtobufType.UINT32)
    private Integer commentsCount;

    @ProtobufProperty(index = 46, type = ProtobufType.BOOL)
    private Boolean locked;

    @ProtobufProperty(index = 47, type = ProtobufType.ENUM)
    private PrivacySystemMessage systemMessageToInsert;

    @ProtobufProperty(index = 48, type = ProtobufType.BOOL)
    private Boolean capiCreatedGroup;

    @ProtobufProperty(index = 49, type = ProtobufType.STRING)
    private Jid accountLid;

    @ProtobufProperty(index = 50, type = ProtobufType.BOOL)
    private Boolean limitSharing;

    @ProtobufProperty(index = 51, type = ProtobufType.INT64, mixins = InstantMillisMixin.class)
    private Instant limitSharingSettingTimestamp;

    @ProtobufProperty(index = 52, type = ProtobufType.ENUM)
    private ChatLimitSharing.TriggerType limitSharingTrigger;

    @ProtobufProperty(index = 53, type = ProtobufType.BOOL)
    private Boolean limitSharingInitiatedByMe;

    @ProtobufProperty(index = 54, type = ProtobufType.BOOL)
    private Boolean maibaAiThreadEnabled;

    protected Chat(Jid jid, Jid newJid, Jid oldJid, Instant lastMsgTimestamp, Integer unreadCount, Boolean readOnly, Boolean endOfHistoryTransfer, ChatEphemeralTimer ephemeralExpiration, Instant ephemeralSettingTimestamp, EndOfHistoryTransferType endOfHistoryTransferType, Instant conversationTimestamp, String name, String pHash, Boolean notSpam, Boolean archived, ChatDisappearingMode disappearingMode, Integer unreadMentionCount, Boolean markedAsUnread, List<GroupParticipant> participant, byte[] tcToken, Instant tcTokenTimestamp, byte[] contactPrimaryIdentityKey, Instant pinnedTimestamp, ChatMute mute, WallpaperSettings wallpaper, MediaVisibility mediaVisibility, Instant tcTokenSenderTimestamp, Boolean suspended, Boolean terminated, Long createdAt, String createdBy, String description, Boolean support, Boolean isParentGroup, String parentGroupId, Boolean isDefaultSubgroup, String displayName, Jid phoneNumberJid, Boolean shareOwnPhoneNumber, Boolean phoneNumberhDuplicateLidThread, Jid lid, String username, String lidOriginType, Integer commentsCount, Boolean locked, PrivacySystemMessage systemMessageToInsert, Boolean capiCreatedGroup, Jid accountLid, Boolean limitSharing, Instant limitSharingSettingTimestamp, ChatLimitSharing.TriggerType limitSharingTrigger, Boolean limitSharingInitiatedByMe, Boolean maibaAiThreadEnabled) {
        this.jid = Objects.requireNonNull(jid);
        this.newJid = newJid;
        this.oldJid = oldJid;
        this.lastMsgTimestamp = lastMsgTimestamp;
        this.unreadCount = unreadCount;
        this.readOnly = readOnly;
        this.endOfHistoryTransfer = endOfHistoryTransfer;
        this.ephemeralExpiration = ephemeralExpiration;
        this.ephemeralSettingTimestamp = ephemeralSettingTimestamp;
        this.endOfHistoryTransferType = endOfHistoryTransferType;
        this.conversationTimestamp = conversationTimestamp;
        this.name = name;
        this.pHash = pHash;
        this.notSpam = notSpam;
        this.archived = archived;
        this.disappearingMode = disappearingMode;
        this.unreadMentionCount = unreadMentionCount;
        this.markedAsUnread = markedAsUnread;
        this.participant = participant;
        this.tcToken = tcToken;
        this.tcTokenTimestamp = tcTokenTimestamp;
        this.contactPrimaryIdentityKey = contactPrimaryIdentityKey;
        this.pinnedTimestamp = pinnedTimestamp;
        this.mute = mute;
        this.wallpaper = wallpaper;
        this.mediaVisibility = mediaVisibility;
        this.tcTokenSenderTimestamp = tcTokenSenderTimestamp;
        this.suspended = suspended;
        this.terminated = terminated;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.description = description;
        this.support = support;
        this.isParentGroup = isParentGroup;
        this.parentGroupId = parentGroupId;
        this.isDefaultSubgroup = isDefaultSubgroup;
        this.displayName = displayName;
        this.phoneNumberJid = phoneNumberJid;
        this.shareOwnPhoneNumber = shareOwnPhoneNumber;
        this.phoneNumberhDuplicateLidThread = phoneNumberhDuplicateLidThread;
        this.lid = lid;
        this.username = username;
        this.lidOriginType = lidOriginType;
        this.commentsCount = commentsCount;
        this.locked = locked;
        this.systemMessageToInsert = systemMessageToInsert;
        this.capiCreatedGroup = capiCreatedGroup;
        this.accountLid = accountLid;
        this.limitSharing = limitSharing;
        this.limitSharingSettingTimestamp = limitSharingSettingTimestamp;
        this.limitSharingTrigger = limitSharingTrigger;
        this.limitSharingInitiatedByMe = limitSharingInitiatedByMe;
        this.maibaAiThreadEnabled = maibaAiThreadEnabled;
    }

    @Override
    public Jid toJid() {
        return jid;
    }

    public Jid jid() {
        return jid;
    }

    /**
     * Returns the messages in this chat as an unmodifiable sequenced collection
     * of {@link ChatMessageInfo}.
     *
     * @return a non-null, unmodifiable sequenced collection of chat messages
     */
    public abstract SequencedCollection<ChatMessageInfo> messages();

    /**
     * Adds a message to this chat.
     *
     * @param info the message to add
     * @throws NullPointerException if {@code info} is {@code null}
     */
    public abstract void addMessage(ChatMessageInfo info);

    /**
     * Removes the message with the specified key ID from this chat.
     *
     * @param id the message key ID to remove
     * @return {@code true} if a message was removed
     */
    public abstract boolean removeMessage(String id);

    /**
     * Removes all messages from this chat.
     */
    public abstract void removeMessages();

    /**
     * Returns the message with the specified key ID, if present.
     *
     * @param id the message key ID to look up
     * @return an {@code Optional} containing the matching message, or empty
     *         if no message with the given ID exists in this chat
     */
    public abstract Optional<ChatMessageInfo> getMessageById(String id);

    /**
     * Returns the newest (most recently added) message in this chat.
     *
     * @return an {@code Optional} containing the newest message, or empty
     *         if this chat has no messages
     */
    public abstract Optional<ChatMessageInfo> newestMessage();

    /**
     * Returns the oldest (earliest added) message in this chat.
     *
     * @return an {@code Optional} containing the oldest message, or empty
     *         if this chat has no messages
     */
    public abstract Optional<ChatMessageInfo> oldestMessage();

    public Optional<Jid> newJid() {
        return Optional.ofNullable(newJid);
    }

    public Optional<Jid> oldJid() {
        return Optional.ofNullable(oldJid);
    }

    public Optional<Instant> lastMsgTimestamp() {
        return Optional.ofNullable(lastMsgTimestamp);
    }

    public OptionalInt unreadCount() {
        return unreadCount == null ? OptionalInt.empty() : OptionalInt.of(unreadCount);
    }

    public boolean readOnly() {
        return readOnly != null && readOnly;
    }

    public boolean endOfHistoryTransfer() {
        return endOfHistoryTransfer != null && endOfHistoryTransfer;
    }

    public Optional<ChatEphemeralTimer> ephemeralExpiration() {
        return Optional.ofNullable(ephemeralExpiration);
    }

    public Optional<Instant> ephemeralSettingTimestamp() {
        return Optional.ofNullable(ephemeralSettingTimestamp);
    }

    public Optional<EndOfHistoryTransferType> endOfHistoryTransferType() {
        return Optional.ofNullable(endOfHistoryTransferType);
    }

    public Optional<Instant> conversationTimestamp() {
        return Optional.ofNullable(conversationTimestamp);
    }

    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    public Optional<String> pHash() {
        return Optional.ofNullable(pHash);
    }

    public boolean notSpam() {
        return notSpam != null && notSpam;
    }

    public boolean archived() {
        return archived != null && archived;
    }

    public Optional<ChatDisappearingMode> disappearingMode() {
        return Optional.ofNullable(disappearingMode);
    }

    public OptionalInt unreadMentionCount() {
        return unreadMentionCount == null ? OptionalInt.empty() : OptionalInt.of(unreadMentionCount);
    }

    public boolean markedAsUnread() {
        return markedAsUnread != null && markedAsUnread;
    }

    public List<GroupParticipant> participant() {
        return participant == null ? List.of() : Collections.unmodifiableList(participant);
    }

    public Optional<byte[]> tcToken() {
        return Optional.ofNullable(tcToken);
    }

    public Optional<Instant> tcTokenTimestamp() {
        return Optional.ofNullable(tcTokenTimestamp);
    }

    public Optional<byte[]> contactPrimaryIdentityKey() {
        return Optional.ofNullable(contactPrimaryIdentityKey);
    }

    public Optional<Instant> pinnedTimestamp() {
        return Optional.ofNullable(pinnedTimestamp);
    }

    public Optional<ChatMute> mute() {
        return Optional.ofNullable(mute);
    }

    public Optional<WallpaperSettings> wallpaper() {
        return Optional.ofNullable(wallpaper);
    }

    public Optional<MediaVisibility> mediaVisibility() {
        return Optional.ofNullable(mediaVisibility);
    }

    public Optional<Instant> tcTokenSenderTimestamp() {
        return Optional.ofNullable(tcTokenSenderTimestamp);
    }

    public boolean suspended() {
        return suspended != null && suspended;
    }

    public boolean terminated() {
        return terminated != null && terminated;
    }

    public OptionalLong createdAt() {
        return createdAt == null ? OptionalLong.empty() : OptionalLong.of(createdAt);
    }

    public Optional<String> createdBy() {
        return Optional.ofNullable(createdBy);
    }

    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    public boolean support() {
        return support != null && support;
    }

    public boolean isParentGroup() {
        return isParentGroup != null && isParentGroup;
    }

    public Optional<String> parentGroupId() {
        return Optional.ofNullable(parentGroupId);
    }

    public boolean isDefaultSubgroup() {
        return isDefaultSubgroup != null && isDefaultSubgroup;
    }

    public Optional<String> displayName() {
        return Optional.ofNullable(displayName);
    }

    public Optional<Jid> phoneNumberJid() {
        return Optional.ofNullable(phoneNumberJid);
    }

    public boolean shareOwnPhoneNumber() {
        return shareOwnPhoneNumber != null && shareOwnPhoneNumber;
    }

    public boolean phoneNumberhDuplicateLidThread() {
        return phoneNumberhDuplicateLidThread != null && phoneNumberhDuplicateLidThread;
    }

    public Optional<Jid> lid() {
        return Optional.ofNullable(lid);
    }

    public Optional<String> username() {
        return Optional.ofNullable(username);
    }

    public Optional<String> lidOriginType() {
        return Optional.ofNullable(lidOriginType);
    }

    public OptionalInt commentsCount() {
        return commentsCount == null ? OptionalInt.empty() : OptionalInt.of(commentsCount);
    }

    public boolean locked() {
        return locked != null && locked;
    }

    public Optional<PrivacySystemMessage> systemMessageToInsert() {
        return Optional.ofNullable(systemMessageToInsert);
    }

    public boolean capiCreatedGroup() {
        return capiCreatedGroup != null && capiCreatedGroup;
    }

    public Optional<Jid> accountLid() {
        return Optional.ofNullable(accountLid);
    }

    public boolean limitSharing() {
        return limitSharing != null && limitSharing;
    }

    public Optional<Instant> limitSharingSettingTimestamp() {
        return Optional.ofNullable(limitSharingSettingTimestamp);
    }

    public Optional<ChatLimitSharing.TriggerType> limitSharingTrigger() {
        return Optional.ofNullable(limitSharingTrigger);
    }

    public boolean limitSharingInitiatedByMe() {
        return limitSharingInitiatedByMe != null && limitSharingInitiatedByMe;
    }

    public boolean maibaAiThreadEnabled() {
        return maibaAiThreadEnabled != null && maibaAiThreadEnabled;
    }

    public void setJid(Jid jid) {
        this.jid = jid;
    }

    /**
     * Transfers all messages from the specified chat into this chat.
     *
     * <p>After this operation the source chat's message collection is empty
     * and this chat owns every message that was previously in the source.
     *
     * @param source the chat whose messages are transferred into this chat
     * @throws NullPointerException if {@code source} is {@code null}
     */
    public void transferMessages(Chat source) {
        Objects.requireNonNull(source);
        for (var msg : source.messages()) {
            addMessage(msg);
        }
        source.removeMessages();
    }

    public void setNewJid(Jid newJid) {
        this.newJid = newJid;
    }

    public void setOldJid(Jid oldJid) {
        this.oldJid = oldJid;
    }

    public void setLastMsgTimestamp(Instant lastMsgTimestamp) {
        this.lastMsgTimestamp = lastMsgTimestamp;
    }

    public void setUnreadCount(Integer unreadCount) {
        this.unreadCount = unreadCount;
    }

    public void setReadOnly(Boolean readOnly) {
        this.readOnly = readOnly;
    }

    public void setEndOfHistoryTransfer(Boolean endOfHistoryTransfer) {
        this.endOfHistoryTransfer = endOfHistoryTransfer;
    }

    public void setEphemeralExpiration(ChatEphemeralTimer ephemeralExpiration) {
        this.ephemeralExpiration = ephemeralExpiration;
    }

    public void setEphemeralSettingTimestamp(Instant ephemeralSettingTimestamp) {
        this.ephemeralSettingTimestamp = ephemeralSettingTimestamp;
    }

    public void setEndOfHistoryTransferType(EndOfHistoryTransferType endOfHistoryTransferType) {
        this.endOfHistoryTransferType = endOfHistoryTransferType;
    }

    public void setConversationTimestamp(Instant conversationTimestamp) {
        this.conversationTimestamp = conversationTimestamp;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPHash(String pHash) {
        this.pHash = pHash;
    }

    public void setNotSpam(Boolean notSpam) {
        this.notSpam = notSpam;
    }

    public void setArchived(Boolean archived) {
        this.archived = archived;
    }

    public void setDisappearingMode(ChatDisappearingMode disappearingMode) {
        this.disappearingMode = disappearingMode;
    }

    public void setUnreadMentionCount(Integer unreadMentionCount) {
        this.unreadMentionCount = unreadMentionCount;
    }

    public void setMarkedAsUnread(Boolean markedAsUnread) {
        this.markedAsUnread = markedAsUnread;
    }

    public void setParticipant(List<GroupParticipant> participant) {
        this.participant = participant;
    }

    public void setTcToken(byte[] tcToken) {
        this.tcToken = tcToken;
    }

    public void setTcTokenTimestamp(Instant tcTokenTimestamp) {
        this.tcTokenTimestamp = tcTokenTimestamp;
    }

    public void setContactPrimaryIdentityKey(byte[] contactPrimaryIdentityKey) {
        this.contactPrimaryIdentityKey = contactPrimaryIdentityKey;
    }

    public void setPinnedTimestamp(Instant pinned) {
        this.pinnedTimestamp = pinned;
    }

    public void setMute(ChatMute mute) {
        this.mute = mute;
    }

    public void setWallpaper(WallpaperSettings wallpaper) {
        this.wallpaper = wallpaper;
    }

    public void setMediaVisibility(MediaVisibility mediaVisibility) {
        this.mediaVisibility = mediaVisibility;
    }

    public void setTcTokenSenderTimestamp(Instant tcTokenSenderTimestamp) {
        this.tcTokenSenderTimestamp = tcTokenSenderTimestamp;
    }

    public void setSuspended(Boolean suspended) {
        this.suspended = suspended;
    }

    public void setTerminated(Boolean terminated) {
        this.terminated = terminated;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setSupport(Boolean support) {
        this.support = support;
    }

    public void setParentGroup(Boolean isParentGroup) {
        this.isParentGroup = isParentGroup;
    }

    public void setParentGroupId(String parentGroupId) {
        this.parentGroupId = parentGroupId;
    }

    public void setDefaultSubgroup(Boolean isDefaultSubgroup) {
        this.isDefaultSubgroup = isDefaultSubgroup;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setPhoneNumberJid(Jid phoneNumberJid) {
        this.phoneNumberJid = phoneNumberJid;
    }

    public void setShareOwnPhoneNumber(Boolean shareOwnPn) {
        this.shareOwnPhoneNumber = shareOwnPn;
    }

    public void setPhoneNumberDuplicateLidThread(Boolean phoneNumberhDuplicateLidThread) {
        this.phoneNumberhDuplicateLidThread = phoneNumberhDuplicateLidThread;
    }

    public void setLid(Jid lidJid) {
        this.lid = lidJid;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setLidOriginType(String lidOriginType) {
        this.lidOriginType = lidOriginType;
    }

    public void setCommentsCount(Integer commentsCount) {
        this.commentsCount = commentsCount;
    }

    public void setLocked(Boolean locked) {
        this.locked = locked;
    }

    public void setSystemMessageToInsert(PrivacySystemMessage systemMessageToInsert) {
        this.systemMessageToInsert = systemMessageToInsert;
    }

    public void setCapiCreatedGroup(Boolean capiCreatedGroup) {
        this.capiCreatedGroup = capiCreatedGroup;
    }

    public void setAccountLid(Jid accountLid) {
        this.accountLid = accountLid;
    }

    public void setLimitSharing(Boolean limitSharing) {
        this.limitSharing = limitSharing;
    }

    public void setLimitSharingSettingTimestamp(Instant limitSharingSettingTimestamp) {
        this.limitSharingSettingTimestamp = limitSharingSettingTimestamp;
    }

    public void setLimitSharingTrigger(ChatLimitSharing.TriggerType limitSharingTrigger) {
        this.limitSharingTrigger = limitSharingTrigger;
    }

    public void setLimitSharingInitiatedByMe(Boolean limitSharingInitiatedByMe) {
        this.limitSharingInitiatedByMe = limitSharingInitiatedByMe;
    }

    public void setMaibaAiThreadEnabled(Boolean maibaAiThreadEnabled) {
        this.maibaAiThreadEnabled = maibaAiThreadEnabled;
    }

    @ProtobufEnum(name = "Conversation.EndOfHistoryTransferType")
    public enum EndOfHistoryTransferType {
        COMPLETE_BUT_MORE_MESSAGES_REMAIN_ON_PRIMARY(0),
        COMPLETE_AND_NO_MORE_MESSAGE_REMAIN_ON_PRIMARY(1),
        COMPLETE_ON_DEMAND_SYNC_BUT_MORE_MSG_REMAIN_ON_PRIMARY(2),
        COMPLETE_ON_DEMAND_SYNC_WITH_MORE_MSG_ON_PRIMARY_BUT_NO_ACCESS(3);

        EndOfHistoryTransferType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
