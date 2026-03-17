package com.github.auties00.cobalt.model.setting;

import com.github.auties00.cobalt.model.media.MediaVisibility;

import java.time.Instant;

import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "GlobalSettings")
public final class GlobalSettings {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    WallpaperSettings lightThemeWallpaper;

    @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
    MediaVisibility mediaVisibility;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    WallpaperSettings darkThemeWallpaper;

    @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
    AutoDownloadSettings autoDownloadWiFi;

    @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
    AutoDownloadSettings autoDownloadCellular;

    @ProtobufProperty(index = 6, type = ProtobufType.MESSAGE)
    AutoDownloadSettings autoDownloadRoaming;

    @ProtobufProperty(index = 7, type = ProtobufType.BOOL)
    Boolean showIndividualNotificationsPreview;

    @ProtobufProperty(index = 8, type = ProtobufType.BOOL)
    Boolean showGroupNotificationsPreview;

    @ProtobufProperty(index = 9, type = ProtobufType.INT32)
    Integer disappearingModeDuration;

    @ProtobufProperty(index = 10, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant disappearingModeTimestamp;

    @ProtobufProperty(index = 11, type = ProtobufType.MESSAGE)
    AvatarUserSettings avatarUserSettings;

    @ProtobufProperty(index = 12, type = ProtobufType.INT32)
    Integer fontSize;

    @ProtobufProperty(index = 13, type = ProtobufType.BOOL)
    Boolean securityNotifications;

    @ProtobufProperty(index = 14, type = ProtobufType.BOOL)
    Boolean autoUnarchiveChats;

    @ProtobufProperty(index = 15, type = ProtobufType.INT32)
    Integer videoQualityMode;

    @ProtobufProperty(index = 16, type = ProtobufType.INT32)
    Integer photoQualityMode;

    @ProtobufProperty(index = 17, type = ProtobufType.MESSAGE)
    NotificationSettings individualNotificationSettings;

    @ProtobufProperty(index = 18, type = ProtobufType.MESSAGE)
    NotificationSettings groupNotificationSettings;

    @ProtobufProperty(index = 19, type = ProtobufType.MESSAGE)
    ChatLockSettings chatLockSettings;

    @ProtobufProperty(index = 20, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant chatDbLidMigrationTimestamp;


    GlobalSettings(WallpaperSettings lightThemeWallpaper, MediaVisibility mediaVisibility, WallpaperSettings darkThemeWallpaper, AutoDownloadSettings autoDownloadWiFi, AutoDownloadSettings autoDownloadCellular, AutoDownloadSettings autoDownloadRoaming, Boolean showIndividualNotificationsPreview, Boolean showGroupNotificationsPreview, Integer disappearingModeDuration, Instant disappearingModeTimestamp, AvatarUserSettings avatarUserSettings, Integer fontSize, Boolean securityNotifications, Boolean autoUnarchiveChats, Integer videoQualityMode, Integer photoQualityMode, NotificationSettings individualNotificationSettings, NotificationSettings groupNotificationSettings, ChatLockSettings chatLockSettings, Instant chatDbLidMigrationTimestamp) {
        this.lightThemeWallpaper = lightThemeWallpaper;
        this.mediaVisibility = mediaVisibility;
        this.darkThemeWallpaper = darkThemeWallpaper;
        this.autoDownloadWiFi = autoDownloadWiFi;
        this.autoDownloadCellular = autoDownloadCellular;
        this.autoDownloadRoaming = autoDownloadRoaming;
        this.showIndividualNotificationsPreview = showIndividualNotificationsPreview;
        this.showGroupNotificationsPreview = showGroupNotificationsPreview;
        this.disappearingModeDuration = disappearingModeDuration;
        this.disappearingModeTimestamp = disappearingModeTimestamp;
        this.avatarUserSettings = avatarUserSettings;
        this.fontSize = fontSize;
        this.securityNotifications = securityNotifications;
        this.autoUnarchiveChats = autoUnarchiveChats;
        this.videoQualityMode = videoQualityMode;
        this.photoQualityMode = photoQualityMode;
        this.individualNotificationSettings = individualNotificationSettings;
        this.groupNotificationSettings = groupNotificationSettings;
        this.chatLockSettings = chatLockSettings;
        this.chatDbLidMigrationTimestamp = chatDbLidMigrationTimestamp;
    }

    public Optional<WallpaperSettings> lightThemeWallpaper() {
        return Optional.ofNullable(lightThemeWallpaper);
    }

    public Optional<MediaVisibility> mediaVisibility() {
        return Optional.ofNullable(mediaVisibility);
    }

    public Optional<WallpaperSettings> darkThemeWallpaper() {
        return Optional.ofNullable(darkThemeWallpaper);
    }

    public Optional<AutoDownloadSettings> autoDownloadWiFi() {
        return Optional.ofNullable(autoDownloadWiFi);
    }

    public Optional<AutoDownloadSettings> autoDownloadCellular() {
        return Optional.ofNullable(autoDownloadCellular);
    }

    public Optional<AutoDownloadSettings> autoDownloadRoaming() {
        return Optional.ofNullable(autoDownloadRoaming);
    }

    public boolean showIndividualNotificationsPreview() {
        return showIndividualNotificationsPreview != null && showIndividualNotificationsPreview;
    }

    public boolean showGroupNotificationsPreview() {
        return showGroupNotificationsPreview != null && showGroupNotificationsPreview;
    }

    public OptionalInt disappearingModeDuration() {
        return disappearingModeDuration == null ? OptionalInt.empty() : OptionalInt.of(disappearingModeDuration);
    }

    public Optional<Instant> disappearingModeTimestamp() {
        return Optional.ofNullable(disappearingModeTimestamp);
    }

    public Optional<AvatarUserSettings> avatarUserSettings() {
        return Optional.ofNullable(avatarUserSettings);
    }

    public OptionalInt fontSize() {
        return fontSize == null ? OptionalInt.empty() : OptionalInt.of(fontSize);
    }

    public boolean securityNotifications() {
        return securityNotifications != null && securityNotifications;
    }

    public boolean autoUnarchiveChats() {
        return autoUnarchiveChats != null && autoUnarchiveChats;
    }

    public OptionalInt videoQualityMode() {
        return videoQualityMode == null ? OptionalInt.empty() : OptionalInt.of(videoQualityMode);
    }

    public OptionalInt photoQualityMode() {
        return photoQualityMode == null ? OptionalInt.empty() : OptionalInt.of(photoQualityMode);
    }

    public Optional<NotificationSettings> individualNotificationSettings() {
        return Optional.ofNullable(individualNotificationSettings);
    }

    public Optional<NotificationSettings> groupNotificationSettings() {
        return Optional.ofNullable(groupNotificationSettings);
    }

    public Optional<ChatLockSettings> chatLockSettings() {
        return Optional.ofNullable(chatLockSettings);
    }

    public Optional<Instant> chatDbLidMigrationTimestamp() {
        return Optional.ofNullable(chatDbLidMigrationTimestamp);
    }

    public void setLightThemeWallpaper(WallpaperSettings lightThemeWallpaper) {
        this.lightThemeWallpaper = lightThemeWallpaper;
    }

    public void setMediaVisibility(MediaVisibility mediaVisibility) {
        this.mediaVisibility = mediaVisibility;
    }

    public void setDarkThemeWallpaper(WallpaperSettings darkThemeWallpaper) {
        this.darkThemeWallpaper = darkThemeWallpaper;
    }

    public void setAutoDownloadWiFi(AutoDownloadSettings autoDownloadWiFi) {
        this.autoDownloadWiFi = autoDownloadWiFi;
    }

    public void setAutoDownloadCellular(AutoDownloadSettings autoDownloadCellular) {
        this.autoDownloadCellular = autoDownloadCellular;
    }

    public void setAutoDownloadRoaming(AutoDownloadSettings autoDownloadRoaming) {
        this.autoDownloadRoaming = autoDownloadRoaming;
    }

    public void setShowIndividualNotificationsPreview(Boolean showIndividualNotificationsPreview) {
        this.showIndividualNotificationsPreview = showIndividualNotificationsPreview;
    }

    public void setShowGroupNotificationsPreview(Boolean showGroupNotificationsPreview) {
        this.showGroupNotificationsPreview = showGroupNotificationsPreview;
    }

    public void setDisappearingModeDuration(Integer disappearingModeDuration) {
        this.disappearingModeDuration = disappearingModeDuration;
    }

    public void setDisappearingModeTimestamp(Instant disappearingModeTimestamp) {
        this.disappearingModeTimestamp = disappearingModeTimestamp;
    }

    public void setAvatarUserSettings(AvatarUserSettings avatarUserSettings) {
        this.avatarUserSettings = avatarUserSettings;
    }

    public void setFontSize(Integer fontSize) {
        this.fontSize = fontSize;
    }

    public void setSecurityNotifications(Boolean securityNotifications) {
        this.securityNotifications = securityNotifications;
    }

    public void setAutoUnarchiveChats(Boolean autoUnarchiveChats) {
        this.autoUnarchiveChats = autoUnarchiveChats;
    }

    public void setVideoQualityMode(Integer videoQualityMode) {
        this.videoQualityMode = videoQualityMode;
    }

    public void setPhotoQualityMode(Integer photoQualityMode) {
        this.photoQualityMode = photoQualityMode;
    }

    public void setIndividualNotificationSettings(NotificationSettings individualNotificationSettings) {
        this.individualNotificationSettings = individualNotificationSettings;
    }

    public void setGroupNotificationSettings(NotificationSettings groupNotificationSettings) {
        this.groupNotificationSettings = groupNotificationSettings;
    }

    public void setChatLockSettings(ChatLockSettings chatLockSettings) {
        this.chatLockSettings = chatLockSettings;
    }

    public void setChatDbLidMigrationTimestamp(Instant chatDbLidMigrationTimestamp) {
        this.chatDbLidMigrationTimestamp = chatDbLidMigrationTimestamp;
    }
}
