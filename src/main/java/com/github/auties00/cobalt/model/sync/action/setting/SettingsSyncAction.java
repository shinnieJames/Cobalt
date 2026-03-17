package com.github.auties00.cobalt.model.sync.action.setting;

import com.github.auties00.cobalt.model.sync.SyncAction;
import com.github.auties00.cobalt.model.sync.SyncPatchType;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "SyncActionValue.SettingsSyncAction")
public final class SettingsSyncAction implements SyncAction<SettingsSyncActionArgs> {
    /**
     * Canonical WhatsApp Web action name for this action type.
     */
    public static final String ACTION_NAME = "settings_sync";

    /**
     * Canonical WhatsApp Web action version for this action type.
     */
    public static final int ACTION_VERSION = 1;

    /**
     * Canonical WhatsApp Web collection name for this action type.
     */
    public static final SyncPatchType COLLECTION_NAME = SyncPatchType.REGULAR_LOW;

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
    Boolean startAtLogin;

    @ProtobufProperty(index = 2, type = ProtobufType.BOOL)
    Boolean minimizeToTray;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String language;

    @ProtobufProperty(index = 4, type = ProtobufType.BOOL)
    Boolean replaceTextWithEmoji;

    @ProtobufProperty(index = 5, type = ProtobufType.ENUM)
    DisplayMode bannerNotificationDisplayMode;

    @ProtobufProperty(index = 6, type = ProtobufType.ENUM)
    DisplayMode unreadCounterBadgeDisplayMode;

    @ProtobufProperty(index = 7, type = ProtobufType.BOOL)
    Boolean isMessagesNotificationEnabled;

    @ProtobufProperty(index = 8, type = ProtobufType.BOOL)
    Boolean isCallsNotificationEnabled;

    @ProtobufProperty(index = 9, type = ProtobufType.BOOL)
    Boolean isReactionsNotificationEnabled;

    @ProtobufProperty(index = 10, type = ProtobufType.BOOL)
    Boolean isStatusReactionsNotificationEnabled;

    @ProtobufProperty(index = 11, type = ProtobufType.BOOL)
    Boolean isTextPreviewForNotificationEnabled;

    @ProtobufProperty(index = 12, type = ProtobufType.INT32)
    Integer defaultNotificationToneId;

    @ProtobufProperty(index = 13, type = ProtobufType.INT32)
    Integer groupDefaultNotificationToneId;

    @ProtobufProperty(index = 14, type = ProtobufType.INT32)
    Integer appTheme;

    @ProtobufProperty(index = 15, type = ProtobufType.INT32)
    Integer wallpaperId;

    @ProtobufProperty(index = 16, type = ProtobufType.BOOL)
    Boolean isDoodleWallpaperEnabled;

    @ProtobufProperty(index = 17, type = ProtobufType.INT32)
    Integer fontSize;

    @ProtobufProperty(index = 18, type = ProtobufType.BOOL)
    Boolean isPhotosAutodownloadEnabled;

    @ProtobufProperty(index = 19, type = ProtobufType.BOOL)
    Boolean isAudiosAutodownloadEnabled;

    @ProtobufProperty(index = 20, type = ProtobufType.BOOL)
    Boolean isVideosAutodownloadEnabled;

    @ProtobufProperty(index = 21, type = ProtobufType.BOOL)
    Boolean isDocumentsAutodownloadEnabled;

    @ProtobufProperty(index = 22, type = ProtobufType.BOOL)
    Boolean disableLinkPreviews;

    @ProtobufProperty(index = 23, type = ProtobufType.INT32)
    Integer notificationToneId;

    @ProtobufProperty(index = 24, type = ProtobufType.ENUM)
    MediaQualitySetting mediaUploadQuality;

    @ProtobufProperty(index = 25, type = ProtobufType.BOOL)
    Boolean isSpellCheckEnabled;

    @ProtobufProperty(index = 26, type = ProtobufType.BOOL)
    Boolean isEnterToSendEnabled;

    @ProtobufProperty(index = 27, type = ProtobufType.BOOL)
    Boolean isGroupMessageNotificationEnabled;

    @ProtobufProperty(index = 28, type = ProtobufType.BOOL)
    Boolean isGroupReactionsNotificationEnabled;

    @ProtobufProperty(index = 29, type = ProtobufType.BOOL)
    Boolean isStatusNotificationEnabled;

    @ProtobufProperty(index = 30, type = ProtobufType.INT32)
    Integer statusNotificationToneId;

    @ProtobufProperty(index = 31, type = ProtobufType.BOOL)
    Boolean shouldPlaySoundForCallNotification;


    SettingsSyncAction(Boolean startAtLogin, Boolean minimizeToTray, String language, Boolean replaceTextWithEmoji, DisplayMode bannerNotificationDisplayMode, DisplayMode unreadCounterBadgeDisplayMode, Boolean isMessagesNotificationEnabled, Boolean isCallsNotificationEnabled, Boolean isReactionsNotificationEnabled, Boolean isStatusReactionsNotificationEnabled, Boolean isTextPreviewForNotificationEnabled, Integer defaultNotificationToneId, Integer groupDefaultNotificationToneId, Integer appTheme, Integer wallpaperId, Boolean isDoodleWallpaperEnabled, Integer fontSize, Boolean isPhotosAutodownloadEnabled, Boolean isAudiosAutodownloadEnabled, Boolean isVideosAutodownloadEnabled, Boolean isDocumentsAutodownloadEnabled, Boolean disableLinkPreviews, Integer notificationToneId, MediaQualitySetting mediaUploadQuality, Boolean isSpellCheckEnabled, Boolean isEnterToSendEnabled, Boolean isGroupMessageNotificationEnabled, Boolean isGroupReactionsNotificationEnabled, Boolean isStatusNotificationEnabled, Integer statusNotificationToneId, Boolean shouldPlaySoundForCallNotification) {
        this.startAtLogin = startAtLogin;
        this.minimizeToTray = minimizeToTray;
        this.language = language;
        this.replaceTextWithEmoji = replaceTextWithEmoji;
        this.bannerNotificationDisplayMode = bannerNotificationDisplayMode;
        this.unreadCounterBadgeDisplayMode = unreadCounterBadgeDisplayMode;
        this.isMessagesNotificationEnabled = isMessagesNotificationEnabled;
        this.isCallsNotificationEnabled = isCallsNotificationEnabled;
        this.isReactionsNotificationEnabled = isReactionsNotificationEnabled;
        this.isStatusReactionsNotificationEnabled = isStatusReactionsNotificationEnabled;
        this.isTextPreviewForNotificationEnabled = isTextPreviewForNotificationEnabled;
        this.defaultNotificationToneId = defaultNotificationToneId;
        this.groupDefaultNotificationToneId = groupDefaultNotificationToneId;
        this.appTheme = appTheme;
        this.wallpaperId = wallpaperId;
        this.isDoodleWallpaperEnabled = isDoodleWallpaperEnabled;
        this.fontSize = fontSize;
        this.isPhotosAutodownloadEnabled = isPhotosAutodownloadEnabled;
        this.isAudiosAutodownloadEnabled = isAudiosAutodownloadEnabled;
        this.isVideosAutodownloadEnabled = isVideosAutodownloadEnabled;
        this.isDocumentsAutodownloadEnabled = isDocumentsAutodownloadEnabled;
        this.disableLinkPreviews = disableLinkPreviews;
        this.notificationToneId = notificationToneId;
        this.mediaUploadQuality = mediaUploadQuality;
        this.isSpellCheckEnabled = isSpellCheckEnabled;
        this.isEnterToSendEnabled = isEnterToSendEnabled;
        this.isGroupMessageNotificationEnabled = isGroupMessageNotificationEnabled;
        this.isGroupReactionsNotificationEnabled = isGroupReactionsNotificationEnabled;
        this.isStatusNotificationEnabled = isStatusNotificationEnabled;
        this.statusNotificationToneId = statusNotificationToneId;
        this.shouldPlaySoundForCallNotification = shouldPlaySoundForCallNotification;
    }

    public boolean startAtLogin() {
        return startAtLogin != null && startAtLogin;
    }

    public boolean minimizeToTray() {
        return minimizeToTray != null && minimizeToTray;
    }

    public Optional<String> language() {
        return Optional.ofNullable(language);
    }

    public boolean replaceTextWithEmoji() {
        return replaceTextWithEmoji != null && replaceTextWithEmoji;
    }

    public Optional<DisplayMode> bannerNotificationDisplayMode() {
        return Optional.ofNullable(bannerNotificationDisplayMode);
    }

    public Optional<DisplayMode> unreadCounterBadgeDisplayMode() {
        return Optional.ofNullable(unreadCounterBadgeDisplayMode);
    }

    public boolean isMessagesNotificationEnabled() {
        return isMessagesNotificationEnabled != null && isMessagesNotificationEnabled;
    }

    public boolean isCallsNotificationEnabled() {
        return isCallsNotificationEnabled != null && isCallsNotificationEnabled;
    }

    public boolean isReactionsNotificationEnabled() {
        return isReactionsNotificationEnabled != null && isReactionsNotificationEnabled;
    }

    public boolean isStatusReactionsNotificationEnabled() {
        return isStatusReactionsNotificationEnabled != null && isStatusReactionsNotificationEnabled;
    }

    public boolean isTextPreviewForNotificationEnabled() {
        return isTextPreviewForNotificationEnabled != null && isTextPreviewForNotificationEnabled;
    }

    public OptionalInt defaultNotificationToneId() {
        return defaultNotificationToneId == null ? OptionalInt.empty() : OptionalInt.of(defaultNotificationToneId);
    }

    public OptionalInt groupDefaultNotificationToneId() {
        return groupDefaultNotificationToneId == null ? OptionalInt.empty() : OptionalInt.of(groupDefaultNotificationToneId);
    }

    public OptionalInt appTheme() {
        return appTheme == null ? OptionalInt.empty() : OptionalInt.of(appTheme);
    }

    public OptionalInt wallpaperId() {
        return wallpaperId == null ? OptionalInt.empty() : OptionalInt.of(wallpaperId);
    }

    public boolean isDoodleWallpaperEnabled() {
        return isDoodleWallpaperEnabled != null && isDoodleWallpaperEnabled;
    }

    public OptionalInt fontSize() {
        return fontSize == null ? OptionalInt.empty() : OptionalInt.of(fontSize);
    }

    public boolean isPhotosAutodownloadEnabled() {
        return isPhotosAutodownloadEnabled != null && isPhotosAutodownloadEnabled;
    }

    public boolean isAudiosAutodownloadEnabled() {
        return isAudiosAutodownloadEnabled != null && isAudiosAutodownloadEnabled;
    }

    public boolean isVideosAutodownloadEnabled() {
        return isVideosAutodownloadEnabled != null && isVideosAutodownloadEnabled;
    }

    public boolean isDocumentsAutodownloadEnabled() {
        return isDocumentsAutodownloadEnabled != null && isDocumentsAutodownloadEnabled;
    }

    public boolean disableLinkPreviews() {
        return disableLinkPreviews != null && disableLinkPreviews;
    }

    public OptionalInt notificationToneId() {
        return notificationToneId == null ? OptionalInt.empty() : OptionalInt.of(notificationToneId);
    }

    public Optional<MediaQualitySetting> mediaUploadQuality() {
        return Optional.ofNullable(mediaUploadQuality);
    }

    public boolean isSpellCheckEnabled() {
        return isSpellCheckEnabled != null && isSpellCheckEnabled;
    }

    public boolean isEnterToSendEnabled() {
        return isEnterToSendEnabled != null && isEnterToSendEnabled;
    }

    public boolean isGroupMessageNotificationEnabled() {
        return isGroupMessageNotificationEnabled != null && isGroupMessageNotificationEnabled;
    }

    public boolean isGroupReactionsNotificationEnabled() {
        return isGroupReactionsNotificationEnabled != null && isGroupReactionsNotificationEnabled;
    }

    public boolean isStatusNotificationEnabled() {
        return isStatusNotificationEnabled != null && isStatusNotificationEnabled;
    }

    public OptionalInt statusNotificationToneId() {
        return statusNotificationToneId == null ? OptionalInt.empty() : OptionalInt.of(statusNotificationToneId);
    }

    public boolean shouldPlaySoundForCallNotification() {
        return shouldPlaySoundForCallNotification != null && shouldPlaySoundForCallNotification;
    }

    public void setStartAtLogin(Boolean startAtLogin) {
        this.startAtLogin = startAtLogin;
    }

    public void setMinimizeToTray(Boolean minimizeToTray) {
        this.minimizeToTray = minimizeToTray;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public void setReplaceTextWithEmoji(Boolean replaceTextWithEmoji) {
        this.replaceTextWithEmoji = replaceTextWithEmoji;
    }

    public void setBannerNotificationDisplayMode(DisplayMode bannerNotificationDisplayMode) {
        this.bannerNotificationDisplayMode = bannerNotificationDisplayMode;
    }

    public void setUnreadCounterBadgeDisplayMode(DisplayMode unreadCounterBadgeDisplayMode) {
        this.unreadCounterBadgeDisplayMode = unreadCounterBadgeDisplayMode;
    }

    public void setMessagesNotificationEnabled(Boolean isMessagesNotificationEnabled) {
        this.isMessagesNotificationEnabled = isMessagesNotificationEnabled;
    }

    public void setCallsNotificationEnabled(Boolean isCallsNotificationEnabled) {
        this.isCallsNotificationEnabled = isCallsNotificationEnabled;
    }

    public void setReactionsNotificationEnabled(Boolean isReactionsNotificationEnabled) {
        this.isReactionsNotificationEnabled = isReactionsNotificationEnabled;
    }

    public void setStatusReactionsNotificationEnabled(Boolean isStatusReactionsNotificationEnabled) {
        this.isStatusReactionsNotificationEnabled = isStatusReactionsNotificationEnabled;
    }

    public void setTextPreviewForNotificationEnabled(Boolean isTextPreviewForNotificationEnabled) {
        this.isTextPreviewForNotificationEnabled = isTextPreviewForNotificationEnabled;
    }

    public void setDefaultNotificationToneId(Integer defaultNotificationToneId) {
        this.defaultNotificationToneId = defaultNotificationToneId;
    }

    public void setGroupDefaultNotificationToneId(Integer groupDefaultNotificationToneId) {
        this.groupDefaultNotificationToneId = groupDefaultNotificationToneId;
    }

    public void setAppTheme(Integer appTheme) {
        this.appTheme = appTheme;
    }

    public void setWallpaperId(Integer wallpaperId) {
        this.wallpaperId = wallpaperId;
    }

    public void setDoodleWallpaperEnabled(Boolean isDoodleWallpaperEnabled) {
        this.isDoodleWallpaperEnabled = isDoodleWallpaperEnabled;
    }

    public void setFontSize(Integer fontSize) {
        this.fontSize = fontSize;
    }

    public void setPhotosAutodownloadEnabled(Boolean isPhotosAutodownloadEnabled) {
        this.isPhotosAutodownloadEnabled = isPhotosAutodownloadEnabled;
    }

    public void setAudiosAutodownloadEnabled(Boolean isAudiosAutodownloadEnabled) {
        this.isAudiosAutodownloadEnabled = isAudiosAutodownloadEnabled;
    }

    public void setVideosAutodownloadEnabled(Boolean isVideosAutodownloadEnabled) {
        this.isVideosAutodownloadEnabled = isVideosAutodownloadEnabled;
    }

    public void setDocumentsAutodownloadEnabled(Boolean isDocumentsAutodownloadEnabled) {
        this.isDocumentsAutodownloadEnabled = isDocumentsAutodownloadEnabled;
    }

    public void setDisableLinkPreviews(Boolean disableLinkPreviews) {
        this.disableLinkPreviews = disableLinkPreviews;
    }

    public void setNotificationToneId(Integer notificationToneId) {
        this.notificationToneId = notificationToneId;
    }

    public void setMediaUploadQuality(MediaQualitySetting mediaUploadQuality) {
        this.mediaUploadQuality = mediaUploadQuality;
    }

    public void setSpellCheckEnabled(Boolean isSpellCheckEnabled) {
        this.isSpellCheckEnabled = isSpellCheckEnabled;
    }

    public void setEnterToSendEnabled(Boolean isEnterToSendEnabled) {
        this.isEnterToSendEnabled = isEnterToSendEnabled;
    }

    public void setGroupMessageNotificationEnabled(Boolean isGroupMessageNotificationEnabled) {
        this.isGroupMessageNotificationEnabled = isGroupMessageNotificationEnabled;
    }

    public void setGroupReactionsNotificationEnabled(Boolean isGroupReactionsNotificationEnabled) {
        this.isGroupReactionsNotificationEnabled = isGroupReactionsNotificationEnabled;
    }

    public void setStatusNotificationEnabled(Boolean isStatusNotificationEnabled) {
        this.isStatusNotificationEnabled = isStatusNotificationEnabled;
    }

    public void setStatusNotificationToneId(Integer statusNotificationToneId) {
        this.statusNotificationToneId = statusNotificationToneId;
    }

    public void setShouldPlaySoundForCallNotification(Boolean shouldPlaySoundForCallNotification) {
        this.shouldPlaySoundForCallNotification = shouldPlaySoundForCallNotification;
    }

    @ProtobufEnum(name = "SyncActionValue.SettingsSyncAction.DisplayMode")
    public static enum DisplayMode {
        DISPLAY_MODE_UNKNOWN(0),
        ALWAYS(1),
        NEVER(2),
        ONLY_WHEN_APP_IS_OPEN(3);

        DisplayMode(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufEnum(name = "SyncActionValue.SettingsSyncAction.MediaQualitySetting")
    public static enum MediaQualitySetting {
        MEDIA_QUALITY_UNKNOWN(0),
        STANDARD(1),
        HD(2);

        MediaQualitySetting(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufEnum(name = "SyncActionValue.SettingsSyncAction.SettingKey")
    public static enum SettingKey {
        SETTING_KEY_UNKNOWN(0),
        START_AT_LOGIN(1),
        MINIMIZE_TO_TRAY(2),
        LANGUAGE(3),
        REPLACE_TEXT_WITH_EMOJI(4),
        BANNER_NOTIFICATION_DISPLAY_MODE(5),
        UNREAD_COUNTER_BADGE_DISPLAY_MODE(6),
        IS_MESSAGES_NOTIFICATION_ENABLED(7),
        IS_CALLS_NOTIFICATION_ENABLED(8),
        IS_REACTIONS_NOTIFICATION_ENABLED(9),
        IS_STATUS_REACTIONS_NOTIFICATION_ENABLED(10),
        IS_TEXT_PREVIEW_FOR_NOTIFICATION_ENABLED(11),
        DEFAULT_NOTIFICATION_TONE_ID(12),
        GROUP_DEFAULT_NOTIFICATION_TONE_ID(13),
        APP_THEME(14),
        WALLPAPER_ID(15),
        IS_DOODLE_WALLPAPER_ENABLED(16),
        FONT_SIZE(17),
        IS_PHOTOS_AUTODOWNLOAD_ENABLED(18),
        IS_AUDIOS_AUTODOWNLOAD_ENABLED(19),
        IS_VIDEOS_AUTODOWNLOAD_ENABLED(20),
        IS_DOCUMENTS_AUTODOWNLOAD_ENABLED(21),
        DISABLE_LINK_PREVIEWS(22),
        NOTIFICATION_TONE_ID(23),
        MEDIA_UPLOAD_QUALITY(24),
        IS_SPELL_CHECK_ENABLED(25),
        IS_ENTER_TO_SEND_ENABLED(26),
        IS_GROUP_MESSAGE_NOTIFICATION_ENABLED(27),
        IS_GROUP_REACTIONS_NOTIFICATION_ENABLED(28),
        IS_STATUS_NOTIFICATION_ENABLED(29),
        STATUS_NOTIFICATION_TONE_ID(30),
        SHOULD_PLAY_SOUND_FOR_CALL_NOTIFICATION(31);

        SettingKey(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufEnum(name = "SyncActionValue.SettingsSyncAction.SettingPlatform")
    public static enum SettingPlatform {
        PLATFORM_UNKNOWN(0),
        WEB(1),
        HYBRID(2),
        WINDOWS(3),
        MAC(4);

        SettingPlatform(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }


}
