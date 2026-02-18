package com.github.auties00.cobalt.model.device;

import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Optional;

@ProtobufMessage(name = "DeviceCapabilities")
public final class DeviceCapabilities {
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    ChatLockSupportLevel chatLockSupportLevel;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    LIDMigration lidMigration;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    BusinessBroadcast businessBroadcast;

    @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
    UserHasAvatar userHasAvatar;

    @ProtobufProperty(index = 5, type = ProtobufType.ENUM)
    MemberNameTagPrimarySupport memberNameTagPrimarySupport;

    @ProtobufProperty(index = 6, type = ProtobufType.MESSAGE)
    AiThread aiThread;


    DeviceCapabilities(ChatLockSupportLevel chatLockSupportLevel, LIDMigration lidMigration, BusinessBroadcast businessBroadcast, UserHasAvatar userHasAvatar, MemberNameTagPrimarySupport memberNameTagPrimarySupport, AiThread aiThread) {
        this.chatLockSupportLevel = chatLockSupportLevel;
        this.lidMigration = lidMigration;
        this.businessBroadcast = businessBroadcast;
        this.userHasAvatar = userHasAvatar;
        this.memberNameTagPrimarySupport = memberNameTagPrimarySupport;
        this.aiThread = aiThread;
    }

    public Optional<ChatLockSupportLevel> chatLockSupportLevel() {
        return Optional.ofNullable(chatLockSupportLevel);
    }

    public Optional<LIDMigration> lidMigration() {
        return Optional.ofNullable(lidMigration);
    }

    public Optional<BusinessBroadcast> businessBroadcast() {
        return Optional.ofNullable(businessBroadcast);
    }

    public Optional<UserHasAvatar> userHasAvatar() {
        return Optional.ofNullable(userHasAvatar);
    }

    public Optional<MemberNameTagPrimarySupport> memberNameTagPrimarySupport() {
        return Optional.ofNullable(memberNameTagPrimarySupport);
    }

    public Optional<AiThread> aiThread() {
        return Optional.ofNullable(aiThread);
    }

    public DeviceCapabilities setChatLockSupportLevel(ChatLockSupportLevel chatLockSupportLevel) {
        this.chatLockSupportLevel = chatLockSupportLevel;
        return this;
    }

    public DeviceCapabilities setLidMigration(LIDMigration lidMigration) {
        this.lidMigration = lidMigration;
        return this;
    }

    public DeviceCapabilities setBusinessBroadcast(BusinessBroadcast businessBroadcast) {
        this.businessBroadcast = businessBroadcast;
        return this;
    }

    public DeviceCapabilities setUserHasAvatar(UserHasAvatar userHasAvatar) {
        this.userHasAvatar = userHasAvatar;
        return this;
    }

    public DeviceCapabilities setMemberNameTagPrimarySupport(MemberNameTagPrimarySupport memberNameTagPrimarySupport) {
        this.memberNameTagPrimarySupport = memberNameTagPrimarySupport;
        return this;
    }

    public DeviceCapabilities setAiThread(AiThread aiThread) {
        this.aiThread = aiThread;
        return this;
    }

    @ProtobufEnum(name = "DeviceCapabilities.ChatLockSupportLevel")
    public static enum ChatLockSupportLevel {
        NONE(0),
        MINIMAL(1),
        FULL(2);

        ChatLockSupportLevel(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufEnum(name = "DeviceCapabilities.MemberNameTagPrimarySupport")
    public static enum MemberNameTagPrimarySupport {
        DISABLED(0),
        RECEIVER_ENABLED(1),
        SENDER_ENABLED(2);

        MemberNameTagPrimarySupport(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufMessage(name = "DeviceCapabilities.AiThread")
    public static final class AiThread {
        @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
        AiThread.SupportLevel supportLevel;


        AiThread(SupportLevel supportLevel) {
            this.supportLevel = supportLevel;
        }

        public Optional<SupportLevel> supportLevel() {
            return Optional.ofNullable(supportLevel);
        }

        public AiThread setSupportLevel(SupportLevel supportLevel) {
            this.supportLevel = supportLevel;
            return this;
        }

        @ProtobufEnum(name = "DeviceCapabilities.AiThread.SupportLevel")
        public static enum SupportLevel {
            NONE(0),
            INFRA(1),
            FULL(2);

            SupportLevel(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            final int index;

            public int index() {
                return this.index;
            }
        }
    }

    @ProtobufMessage(name = "DeviceCapabilities.BusinessBroadcast")
    public static final class BusinessBroadcast {
        @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
        Boolean importListEnabled;


        BusinessBroadcast(Boolean importListEnabled) {
            this.importListEnabled = importListEnabled;
        }

        public boolean importListEnabled() {
            return importListEnabled != null && importListEnabled;
        }

        public BusinessBroadcast setImportListEnabled(Boolean importListEnabled) {
            this.importListEnabled = importListEnabled;
            return this;
        }
    }

    @ProtobufMessage(name = "DeviceCapabilities.LIDMigration")
    public static final class LIDMigration {
        @ProtobufProperty(index = 1, type = ProtobufType.UINT64, mixins = InstantSecondsMixin.class)
        Instant chatDbMigrationTimestamp;


        LIDMigration(Instant chatDbMigrationTimestamp) {
            this.chatDbMigrationTimestamp = chatDbMigrationTimestamp;
        }

        public Optional<Instant> chatDbMigrationTimestamp() {
            return Optional.ofNullable(chatDbMigrationTimestamp);
        }

        public LIDMigration setChatDbMigrationTimestamp(Instant chatDbMigrationTimestamp) {
            this.chatDbMigrationTimestamp = chatDbMigrationTimestamp;
            return this;
        }
    }

    @ProtobufMessage(name = "DeviceCapabilities.UserHasAvatar")
    public static final class UserHasAvatar {
        @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
        Boolean userHasAvatar;


        UserHasAvatar(Boolean userHasAvatar) {
            this.userHasAvatar = userHasAvatar;
        }

        public boolean userHasAvatar() {
            return userHasAvatar != null && userHasAvatar;
        }

        public UserHasAvatar setUserHasAvatar(Boolean userHasAvatar) {
            this.userHasAvatar = userHasAvatar;
            return this;
        }
    }
}
