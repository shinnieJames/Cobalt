package com.github.auties00.cobalt.model.message.status;

import com.github.auties00.cobalt.model.jid.Jid;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "StatusAttribution")
public final class StatusAttribution {
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    Type type;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String actionUrl;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    StatusReshare statusReshare;

    @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
    ExternalShare externalShare;

    @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
    Music music;

    @ProtobufProperty(index = 6, type = ProtobufType.MESSAGE)
    GroupStatus groupStatus;

    @ProtobufProperty(index = 7, type = ProtobufType.MESSAGE)
    RLAttribution rlAttribution;

    @ProtobufProperty(index = 8, type = ProtobufType.MESSAGE)
    AiCreatedAttribution aiCreatedAttribution;


    StatusAttribution(Type type, String actionUrl, StatusReshare statusReshare, ExternalShare externalShare, Music music, GroupStatus groupStatus, RLAttribution rlAttribution, AiCreatedAttribution aiCreatedAttribution) {
        this.type = type;
        this.actionUrl = actionUrl;
        this.statusReshare = statusReshare;
        this.externalShare = externalShare;
        this.music = music;
        this.groupStatus = groupStatus;
        this.rlAttribution = rlAttribution;
        this.aiCreatedAttribution = aiCreatedAttribution;
    }

    public Optional<Type> type() {
        return Optional.ofNullable(type);
    }

    public Optional<String> actionUrl() {
        return Optional.ofNullable(actionUrl);
    }

    public Optional<? extends AttributionData> attributionData() {
        if (statusReshare != null) return Optional.of(statusReshare);
        if (externalShare != null) return Optional.of(externalShare);
        if (music != null) return Optional.of(music);
        if (groupStatus != null) return Optional.of(groupStatus);
        if (rlAttribution != null) return Optional.of(rlAttribution);
        if (aiCreatedAttribution != null) return Optional.of(aiCreatedAttribution);
        return Optional.empty();
    }

    public void setType(Type type) {
        this.type = type;
    }

    public void setActionUrl(String actionUrl) {
        this.actionUrl = actionUrl;
    }

    public void setStatusReshare(StatusReshare statusReshare) {
        this.statusReshare = statusReshare;
    }

    public void setExternalShare(ExternalShare externalShare) {
        this.externalShare = externalShare;
    }

    public void setMusic(Music music) {
        this.music = music;
    }

    public void setGroupStatus(GroupStatus groupStatus) {
        this.groupStatus = groupStatus;
    }

    public void setRlAttribution(RLAttribution rlAttribution) {
        this.rlAttribution = rlAttribution;
    }

    public void setAiCreatedAttribution(AiCreatedAttribution aiCreatedAttribution) {
        this.aiCreatedAttribution = aiCreatedAttribution;
    }

    @ProtobufEnum(name = "StatusAttribution.Type")
    public static enum Type {
        UNKNOWN(0),
        RESHARE(1),
        EXTERNAL_SHARE(2),
        MUSIC(3),
        STATUS_MENTION(4),
        GROUP_STATUS(5),
        RL_ATTRIBUTION(6),
        AI_CREATED(7),
        LAYOUTS(8),
        STATUS_CLOSE_SHARING(9);

        Type(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufMessage(name = "StatusAttribution.AiCreatedAttribution")
    public static final class AiCreatedAttribution implements AttributionData {
        @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
        AiCreatedAttribution.Source source;


        AiCreatedAttribution(Source source) {
            this.source = source;
        }

        public Optional<Source> source() {
            return Optional.ofNullable(source);
        }

        public void setSource(Source source) {
            this.source = source;
    }

        @ProtobufEnum(name = "StatusAttribution.AiCreatedAttribution.Source")
        public static enum Source {
            UNKNOWN(0),
            STATUS_MIMICRY(1);

            Source(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            final int index;

            public int index() {
                return this.index;
            }
        }
    }

    @ProtobufMessage(name = "StatusAttribution.ExternalShare")
    public static final class ExternalShare implements AttributionData {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String actionUrl;

        @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
        ExternalShare.Source source;

        @ProtobufProperty(index = 3, type = ProtobufType.INT32)
        Integer duration;

        @ProtobufProperty(index = 4, type = ProtobufType.STRING)
        String actionFallbackUrl;


        ExternalShare(String actionUrl, Source source, Integer duration, String actionFallbackUrl) {
            this.actionUrl = actionUrl;
            this.source = source;
            this.duration = duration;
            this.actionFallbackUrl = actionFallbackUrl;
        }

        public Optional<String> actionUrl() {
            return Optional.ofNullable(actionUrl);
        }

        public Optional<Source> source() {
            return Optional.ofNullable(source);
        }

        public OptionalInt duration() {
            return duration == null ? OptionalInt.empty() : OptionalInt.of(duration);
        }

        public Optional<String> actionFallbackUrl() {
            return Optional.ofNullable(actionFallbackUrl);
        }

        public void setActionUrl(String actionUrl) {
            this.actionUrl = actionUrl;
    }

        public void setSource(Source source) {
            this.source = source;
    }

        public void setDuration(Integer duration) {
            this.duration = duration;
    }

        public void setActionFallbackUrl(String actionFallbackUrl) {
            this.actionFallbackUrl = actionFallbackUrl;
    }

        @ProtobufEnum(name = "StatusAttribution.ExternalShare.Source")
        public static enum Source {
            UNKNOWN(0),
            INSTAGRAM(1),
            FACEBOOK(2),
            MESSENGER(3),
            SPOTIFY(4),
            YOUTUBE(5),
            PINTEREST(6),
            THREADS(7),
            APPLE_MUSIC(8),
            SHARECHAT(9),
            GOOGLE_PHOTOS(10);

            Source(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            final int index;

            public int index() {
                return this.index;
            }
        }
    }

    @ProtobufMessage(name = "StatusAttribution.GroupStatus")
    public static final class GroupStatus implements AttributionData {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        Jid authorJid;


        GroupStatus(Jid authorJid) {
            this.authorJid = authorJid;
        }

        public Optional<Jid> authorJid() {
            return Optional.ofNullable(authorJid);
        }

        public void setAuthorJid(Jid authorJid) {
            this.authorJid = authorJid;
    }
    }

    @ProtobufMessage(name = "StatusAttribution.Music")
    public static final class Music implements AttributionData {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String authorName;

        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String songId;

        @ProtobufProperty(index = 3, type = ProtobufType.STRING)
        String title;

        @ProtobufProperty(index = 4, type = ProtobufType.STRING)
        String author;

        @ProtobufProperty(index = 5, type = ProtobufType.STRING)
        String artistAttribution;

        @ProtobufProperty(index = 6, type = ProtobufType.BOOL)
        Boolean isExplicit;


        Music(String authorName, String songId, String title, String author, String artistAttribution, Boolean isExplicit) {
            this.authorName = authorName;
            this.songId = songId;
            this.title = title;
            this.author = author;
            this.artistAttribution = artistAttribution;
            this.isExplicit = isExplicit;
        }

        public Optional<String> authorName() {
            return Optional.ofNullable(authorName);
        }

        public Optional<String> songId() {
            return Optional.ofNullable(songId);
        }

        public Optional<String> title() {
            return Optional.ofNullable(title);
        }

        public Optional<String> author() {
            return Optional.ofNullable(author);
        }

        public Optional<String> artistAttribution() {
            return Optional.ofNullable(artistAttribution);
        }

        public boolean isExplicit() {
            return isExplicit != null && isExplicit;
        }

        public void setAuthorName(String authorName) {
            this.authorName = authorName;
    }

        public void setSongId(String songId) {
            this.songId = songId;
    }

        public void setTitle(String title) {
            this.title = title;
    }

        public void setAuthor(String author) {
            this.author = author;
    }

        public void setArtistAttribution(String artistAttribution) {
            this.artistAttribution = artistAttribution;
    }

        public void setExplicit(Boolean isExplicit) {
            this.isExplicit = isExplicit;
    }
    }

    @ProtobufMessage(name = "StatusAttribution.RLAttribution")
    public static final class RLAttribution implements AttributionData {
        @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
        RLAttribution.Source source;


        RLAttribution(Source source) {
            this.source = source;
        }

        public Optional<Source> source() {
            return Optional.ofNullable(source);
        }

        public void setSource(Source source) {
            this.source = source;
    }

        @ProtobufEnum(name = "StatusAttribution.RLAttribution.Source")
        public static enum Source {
            UNKNOWN(0),
            RAY_BAN_META_GLASSES(1),
            OAKLEY_META_GLASSES(2),
            HYPERNOVA_GLASSES(3);

            Source(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            final int index;

            public int index() {
                return this.index;
            }
        }
    }

    @ProtobufMessage(name = "StatusAttribution.StatusReshare")
    public static final class StatusReshare implements AttributionData {
        @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
        StatusReshare.Source source;

        @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
        StatusReshare.Metadata metadata;


        StatusReshare(Source source, Metadata metadata) {
            this.source = source;
            this.metadata = metadata;
        }

        public Optional<Source> source() {
            return Optional.ofNullable(source);
        }

        public Optional<Metadata> metadata() {
            return Optional.ofNullable(metadata);
        }

        public void setSource(Source source) {
            this.source = source;
    }

        public void setMetadata(Metadata metadata) {
            this.metadata = metadata;
    }

        @ProtobufEnum(name = "StatusAttribution.StatusReshare.Source")
        public static enum Source {
            UNKNOWN(0),
            INTERNAL_RESHARE(1),
            MENTION_RESHARE(2),
            CHANNEL_RESHARE(3),
            FORWARD(4);

            Source(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            final int index;

            public int index() {
                return this.index;
            }
        }

        @ProtobufMessage(name = "StatusAttribution.StatusReshare.Metadata")
        public static final class Metadata {
            @ProtobufProperty(index = 1, type = ProtobufType.INT32)
            Integer duration;

            @ProtobufProperty(index = 2, type = ProtobufType.STRING)
            Jid channelJid;

            @ProtobufProperty(index = 3, type = ProtobufType.INT32)
            Integer channelMessageId;

            @ProtobufProperty(index = 4, type = ProtobufType.BOOL)
            Boolean hasMultipleReshares;


            Metadata(Integer duration, Jid channelJid, Integer channelMessageId, Boolean hasMultipleReshares) {
                this.duration = duration;
                this.channelJid = channelJid;
                this.channelMessageId = channelMessageId;
                this.hasMultipleReshares = hasMultipleReshares;
            }

            public OptionalInt duration() {
                return duration == null ? OptionalInt.empty() : OptionalInt.of(duration);
            }

            public Optional<Jid> channelJid() {
                return Optional.ofNullable(channelJid);
            }

            public OptionalInt channelMessageId() {
                return channelMessageId == null ? OptionalInt.empty() : OptionalInt.of(channelMessageId);
            }

            public boolean hasMultipleReshares() {
                return hasMultipleReshares != null && hasMultipleReshares;
            }

            public void setDuration(Integer duration) {
                this.duration = duration;
    }

            public void setChannelJid(Jid channelJid) {
                this.channelJid = channelJid;
    }

            public void setChannelMessageId(Integer channelMessageId) {
                this.channelMessageId = channelMessageId;
    }

            public void setHasMultipleReshares(Boolean hasMultipleReshares) {
                this.hasMultipleReshares = hasMultipleReshares;
    }
        }
    }
}
