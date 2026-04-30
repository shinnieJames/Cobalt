package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.MediaPickerOriginType;
import com.github.auties00.cobalt.wam.type.MediaQuality;
import com.github.auties00.cobalt.wam.type.MediaType;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebMediaPickerWamEvent")
@WamEvent(id = 1038)
public interface MediaPickerEvent extends WamEventSpec {
    @WamProperty(index = 24, type = WamType.BOOLEAN)
    Optional<Boolean> audienceSelectorClicked();

    @WamProperty(index = 25, type = WamType.BOOLEAN)
    Optional<Boolean> audienceSelectorUpdated();

    @WamProperty(index = 51, type = WamType.INTEGER)
    OptionalInt autoScaleCount();

    @WamProperty(index = 37, type = WamType.STRING)
    Optional<String> captionPositions();

    @WamProperty(index = 16, type = WamType.INTEGER)
    OptionalInt chatRecipients();

    @WamProperty(index = 38, type = WamType.BOOLEAN)
    Optional<Boolean> hasCollectionCaption();

    @WamProperty(index = 34, type = WamType.INTEGER)
    OptionalInt hdToggleChange();

    @WamProperty(index = 35, type = WamType.BOOLEAN)
    Optional<Boolean> hdToggleEligible();

    @WamProperty(index = 36, type = WamType.ENUM)
    Optional<MediaQuality> hdToggleState();

    @WamProperty(index = 54, type = WamType.BOOLEAN)
    Optional<Boolean> isFbCrosspostingEnabled();

    @WamProperty(index = 55, type = WamType.BOOLEAN)
    Optional<Boolean> isIgCrosspostingEnabled();

    @WamProperty(index = 41, type = WamType.BOOLEAN)
    Optional<Boolean> isSentInLandscape();

    @WamProperty(index = 22, type = WamType.BOOLEAN)
    Optional<Boolean> isViewOnce();

    @WamProperty(index = 39, type = WamType.INTEGER)
    OptionalInt itemCaptionCount();

    @WamProperty(index = 42, type = WamType.INTEGER)
    OptionalInt mediaPickerArBackground();

    @WamProperty(index = 43, type = WamType.INTEGER)
    OptionalInt mediaPickerArFilter();

    @WamProperty(index = 44, type = WamType.INTEGER)
    OptionalInt mediaPickerArFunEffect();

    @WamProperty(index = 33, type = WamType.INTEGER)
    OptionalInt mediaPickerAvatarStickers();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt mediaPickerChanged();

    @WamProperty(index = 10, type = WamType.INTEGER)
    OptionalInt mediaPickerCroppedRotated();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt mediaPickerDeleted();

    @WamProperty(index = 11, type = WamType.INTEGER)
    OptionalInt mediaPickerDrawing();

    @WamProperty(index = 18, type = WamType.INTEGER)
    OptionalInt mediaPickerFilter();

    @WamProperty(index = 26, type = WamType.BOOLEAN)
    Optional<Boolean> mediaPickerHasLocationSticker();

    @WamProperty(index = 47, type = WamType.INTEGER)
    OptionalInt mediaPickerIgluLowlight();

    @WamProperty(index = 48, type = WamType.INTEGER)
    OptionalInt mediaPickerIgluTouchup();

    @WamProperty(index = 19, type = WamType.INTEGER)
    OptionalInt mediaPickerLikeDoc();

    @WamProperty(index = 20, type = WamType.INTEGER)
    OptionalInt mediaPickerNotLikeDoc();

    @WamProperty(index = 14, type = WamType.ENUM)
    Optional<MediaPickerOriginType> mediaPickerOrigin();

    @WamProperty(index = 21, type = WamType.BOOLEAN)
    Optional<Boolean> mediaPickerOriginThirdParty();

    @WamProperty(index = 53, type = WamType.STRING)
    Optional<String> mediaPickerPosition();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt mediaPickerSent();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt mediaPickerSentUnchanged();

    @WamProperty(index = 29, type = WamType.STRING)
    Optional<String> mediaPickerSessionId();

    @WamProperty(index = 12, type = WamType.INTEGER)
    OptionalInt mediaPickerStickers();

    @WamProperty(index = 15, type = WamType.TIMER)
    Optional<Instant> mediaPickerT();

    @WamProperty(index = 13, type = WamType.INTEGER)
    OptionalInt mediaPickerText();

    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<MediaType> mediaType();

    @WamProperty(index = 31, type = WamType.INTEGER)
    OptionalInt motionPhotoImpressionCount();

    @WamProperty(index = 32, type = WamType.INTEGER)
    OptionalInt motionPhotoSentCount();

    @WamProperty(index = 45, type = WamType.INTEGER)
    OptionalInt numberOfArPostCapture();

    @WamProperty(index = 46, type = WamType.INTEGER)
    OptionalInt numberOfArPreCapture();

    @WamProperty(index = 49, type = WamType.INTEGER)
    OptionalInt numberOfIgluPostCapture();

    @WamProperty(index = 50, type = WamType.INTEGER)
    OptionalInt numberOfIgluPreCapture();

    @WamProperty(index = 23, type = WamType.TIMER)
    Optional<Instant> photoGalleryDurationT();

    @WamProperty(index = 27, type = WamType.ENUM)
    Optional<MediaQuality> photoQualitySetting();

    @WamProperty(index = 30, type = WamType.INTEGER)
    OptionalInt pickerSessionId();

    @WamProperty(index = 17, type = WamType.INTEGER)
    OptionalInt statusRecipients();

    @WamProperty(index = 52, type = WamType.INTEGER)
    OptionalInt transformCount();

    @WamProperty(index = 28, type = WamType.ENUM)
    Optional<MediaQuality> videoQualitySetting();
}
