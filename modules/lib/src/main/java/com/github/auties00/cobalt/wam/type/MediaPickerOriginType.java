package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

/**
 * Enumerates the origin surfaces reported by WAM telemetry to identify where
 * the user invoked the WhatsApp media picker.
 *
 * <p>Each constant carries the fixed integer identifier transmitted on the
 * wire and tags a specific entry point into the media-picker flow (for
 * example, the chat attachment sheet, the camera tab, a sticker tray action,
 * or a share-extension). Values must never be renumbered or reused.
 *
 * @implNote WAWebWamEnumMediaPickerOriginType: the module default-exports a
 *     single frozen object {@code MEDIA_PICKER_ORIGIN_TYPE} whose keys are the
 *     origin names and whose values are the integer identifiers; Cobalt
 *     mirrors the full enumeration with {@link WamEnumConstant} preserving
 *     each numeric value.
 */
@WamEnum
@WhatsAppWebModule(moduleName = "WAWebWamEnumMediaPickerOriginType")
public enum MediaPickerOriginType {
    /**
     * Media picker opened from the in-chat photo library attachment option.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code CHAT_PHOTO_LIBRARY = 1}.
     */
    @WamEnumConstant(1)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    CHAT_PHOTO_LIBRARY,

    /**
     * Media picker opened from the camera-tab capture button.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code CAMERA_TAB_CAPTURE = 2}.
     */
    @WamEnumConstant(2)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    CAMERA_TAB_CAPTURE,

    /**
     * Media picker opened via a forward action.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code FORWARD = 3}.
     */
    @WamEnumConstant(3)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    FORWARD,

    /**
     * Media picker opened by pasting media into the composer.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code PASTE = 4}.
     */
    @WamEnumConstant(4)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    PASTE,

    /**
     * Media picker opened from a platform share extension.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code SHARE_EXTENSION = 5}.
     */
    @WamEnumConstant(5)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    SHARE_EXTENSION,

    /**
     * Media picker invoked by an export flow.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code EXPORT = 6}.
     */
    @WamEnumConstant(6)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    EXPORT,

    /**
     * Media picker opened from the menu camera-capture option.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code MENU_CAMERA_CAPTURE = 7}.
     */
    @WamEnumConstant(7)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    MENU_CAMERA_CAPTURE,

    /**
     * Media picker opened from an external camera source.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code EXTERNAL_CAMERA = 8}.
     */
    @WamEnumConstant(8)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    EXTERNAL_CAMERA,

    /**
     * Media downloaded for use as a link preview.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code LINK_PREVIEW_DOWNLOAD = 9}.
     */
    @WamEnumConstant(9)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    LINK_PREVIEW_DOWNLOAD,

    /**
     * Media picker opened from the camera-tab media strip.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code CAMERA_TAB_MEDIA_STRIP = 10}.
     */
    @WamEnumConstant(10)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    CAMERA_TAB_MEDIA_STRIP,

    /**
     * Media picker opened from the camera-tab photo library.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code CAMERA_TAB_PHOTO_LIBRARY = 11}.
     */
    @WamEnumConstant(11)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    CAMERA_TAB_PHOTO_LIBRARY,

    /**
     * Media picker opened from the chat camera-button capture.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code CHAT_BUTTON_CAMERA_CAPTURE = 12}.
     */
    @WamEnumConstant(12)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    CHAT_BUTTON_CAMERA_CAPTURE,

    /**
     * Media picker opened from the chat camera-button media strip.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code CHAT_BUTTON_CAMERA_MEDIA_STRIP = 13}.
     */
    @WamEnumConstant(13)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    CHAT_BUTTON_CAMERA_MEDIA_STRIP,

    /**
     * Media picker opened from the chat camera-button photo library.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code CHAT_BUTTON_CAMERA_PHOTO_LIBRARY = 14}.
     */
    @WamEnumConstant(14)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    CHAT_BUTTON_CAMERA_PHOTO_LIBRARY,

    /**
     * Media picker opened from the status-tab camera-capture button.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STATUS_TAB_CAMERA_CAPTURE = 15}.
     */
    @WamEnumConstant(15)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STATUS_TAB_CAMERA_CAPTURE,

    /**
     * Media picker opened from the status-tab camera media strip.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STATUS_TAB_CAMERA_MEDIA_STRIP = 16}.
     */
    @WamEnumConstant(16)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STATUS_TAB_CAMERA_MEDIA_STRIP,

    /**
     * Media picker opened from the status-tab camera photo library.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STATUS_TAB_CAMERA_PHOTO_LIBRARY = 17}.
     */
    @WamEnumConstant(17)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STATUS_TAB_CAMERA_PHOTO_LIBRARY,

    /**
     * Media picker opened from a status-reply camera-capture action.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STATUS_REPLY_CAMERA_CAPTURE = 18}.
     */
    @WamEnumConstant(18)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STATUS_REPLY_CAMERA_CAPTURE,

    /**
     * Media picker opened from a status-reply camera media strip.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STATUS_REPLY_CAMERA_MEDIA_STRIP = 19}.
     */
    @WamEnumConstant(19)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STATUS_REPLY_CAMERA_MEDIA_STRIP,

    /**
     * Media picker opened from a status-reply camera photo library.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STATUS_REPLY_CAMERA_PHOTO_LIBRARY = 20}.
     */
    @WamEnumConstant(20)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STATUS_REPLY_CAMERA_PHOTO_LIBRARY,

    /**
     * Media picker opened from the menu camera photo library option.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code MENU_CAMERA_PHOTO_LIBRARY = 21}.
     */
    @WamEnumConstant(21)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    MENU_CAMERA_PHOTO_LIBRARY,

    /**
     * Media picker opened from the in-app chat GIF picker.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code CHAT_GIF_INAPP = 22}.
     */
    @WamEnumConstant(22)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    CHAT_GIF_INAPP,

    /**
     * Media picker opened from an external chat GIF source.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code CHAT_GIF_EXTERNAL = 23}.
     */
    @WamEnumConstant(23)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    CHAT_GIF_EXTERNAL,

    /**
     * Media picker opened from the in-app status-reply GIF picker.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STATUS_REPLY_GIF_INAPP = 24}.
     */
    @WamEnumConstant(24)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STATUS_REPLY_GIF_INAPP,

    /**
     * Media picker opened from an external status-reply GIF source.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STATUS_REPLY_GIF_EXTERNAL = 25}.
     */
    @WamEnumConstant(25)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STATUS_REPLY_GIF_EXTERNAL,

    /**
     * Media picker opened to edit an SMB quick-reply attachment.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code SMB_QUICK_REPLY_EDIT = 26}.
     */
    @WamEnumConstant(26)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    SMB_QUICK_REPLY_EDIT,

    /**
     * Media picker opened to send an SMB quick-reply attachment.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code SMB_QUICK_REPLY_SEND = 27}.
     */
    @WamEnumConstant(27)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    SMB_QUICK_REPLY_SEND,

    /**
     * Media picker opened from the media browser.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code MEDIA_BROWSER = 28}.
     */
    @WamEnumConstant(28)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    MEDIA_BROWSER,

    /**
     * Media picker opened from the chat quick-edit flow.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code CHAT_QUICK_EDIT = 29}.
     */
    @WamEnumConstant(29)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    CHAT_QUICK_EDIT,

    /**
     * Media picker opened from the camera-tab GIF option.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code CAMERA_TAB_GIF = 30}.
     */
    @WamEnumConstant(30)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    CAMERA_TAB_GIF,

    /**
     * Media picker opened from the status-tab GIF option.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STATUS_TAB_GIF = 31}.
     */
    @WamEnumConstant(31)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STATUS_TAB_GIF,

    /**
     * Media picker opened from the payment composer.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code PAYMENT_COMPOSER = 32}.
     */
    @WamEnumConstant(32)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    PAYMENT_COMPOSER,

    /**
     * A photo was received (inbound photo that triggers media-picker
     * instrumentation).
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code PHOTO_RECEIVED = 33}.
     */
    @WamEnumConstant(33)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    PHOTO_RECEIVED,

    /**
     * A photo received was opened in the gallery.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code PHOTO_RECEIVED_GALLERY = 34}.
     */
    @WamEnumConstant(34)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    PHOTO_RECEIVED_GALLERY,

    /**
     * Media picker opened from the ad creation flow.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code AD_CREATION = 35}.
     */
    @WamEnumConstant(35)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    AD_CREATION,

    /**
     * Media picker invoked by an external drag-and-drop operation.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code EXTERNAL_DROP = 36}.
     */
    @WamEnumConstant(36)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    EXTERNAL_DROP,

    /**
     * Media picker opened from the in-app browser.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code IN_APP_BROWSER = 37}.
     */
    @WamEnumConstant(37)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    IN_APP_BROWSER,

    /**
     * Media picker opened from the premium message composer.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code PREMIUM_MESSAGE_COMPOSER = 38}.
     */
    @WamEnumConstant(38)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    PREMIUM_MESSAGE_COMPOSER,

    /**
     * Media picker opened from the document picker.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code DOCUMENT_PICKER = 39}.
     */
    @WamEnumConstant(39)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    DOCUMENT_PICKER,

    /**
     * Media picker opened from the in-app bug reporting flow.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code IN_APP_BUG_REPORTING = 40}.
     */
    @WamEnumConstant(40)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    IN_APP_BUG_REPORTING,

    /**
     * Media picker opened from the sticker tray "create sticker" action.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STICKER_TRAY_CREATE_STICKER = 41}.
     */
    @WamEnumConstant(41)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STICKER_TRAY_CREATE_STICKER,

    /**
     * Media picker opened from the sticker tray "edit sticker" action.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STICKER_TRAY_EDIT_STICKER = 42}.
     */
    @WamEnumConstant(42)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STICKER_TRAY_EDIT_STICKER,

    /**
     * Media picker opened from the sticker message "create your own" action.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STICKER_MESSAGE_CREATE_YOUR_OWN = 43}.
     */
    @WamEnumConstant(43)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STICKER_MESSAGE_CREATE_YOUR_OWN,

    /**
     * Media picker opened from the sticker message "edit sticker" action.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STICKER_MESSAGE_EDIT_STICKER = 44}.
     */
    @WamEnumConstant(44)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STICKER_MESSAGE_EDIT_STICKER,

    /**
     * Media picker opened from the media viewer "create sticker" action.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code MEDIA_VIEWER_CREATE_STICKER = 45}.
     */
    @WamEnumConstant(45)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    MEDIA_VIEWER_CREATE_STICKER,

    /**
     * Media picker opened from the status newsletter card.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STATUS_NEWSLETTER_CARD = 46}.
     */
    @WamEnumConstant(46)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STATUS_NEWSLETTER_CARD,

    /**
     * Media picker opened from the cutout-image "edit sticker" flow.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code CUTOUT_IMAGE_EDIT_STICKER = 47}.
     */
    @WamEnumConstant(47)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    CUTOUT_IMAGE_EDIT_STICKER,

    /**
     * Media picker opened from the sticker tray "create sticker" capture path.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STICKER_TRAY_CREATE_STICKER_CAPTURE = 48}.
     */
    @WamEnumConstant(48)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STICKER_TRAY_CREATE_STICKER_CAPTURE,

    /**
     * Media picker opened from the in-app Thunderstorm photo library.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code THUNDERSTORM_IN_APP_PHOTO_LIBRARY = 49}.
     */
    @WamEnumConstant(49)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    THUNDERSTORM_IN_APP_PHOTO_LIBRARY,

    /**
     * Media picker opened from the transparent-image "edit sticker" flow.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code TRANSPARENT_IMAGE_EDIT_STICKER = 50}.
     */
    @WamEnumConstant(50)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    TRANSPARENT_IMAGE_EDIT_STICKER,

    /**
     * Media picker opened from a Business Flows surface.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code BUSINESS_FLOWS = 51}.
     */
    @WamEnumConstant(51)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    BUSINESS_FLOWS,

    /**
     * Media picker opened via a system intent.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code SYSTEM_INTENT = 52}.
     */
    @WamEnumConstant(52)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    SYSTEM_INTENT,

    /**
     * Media picker opened from the SMB catalog camera flow.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code SMB_CATALOG_CAMERA = 53}.
     */
    @WamEnumConstant(53)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    SMB_CATALOG_CAMERA,

    /**
     * Media picker opened from the SMB catalog video upload flow.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code SMB_CATALOG_VIDEO_UPLOAD = 54}.
     */
    @WamEnumConstant(54)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    SMB_CATALOG_VIDEO_UPLOAD,

    /**
     * Media picker opened from a status-mention reshare.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STATUS_MENTION_RESHARE = 55}.
     */
    @WamEnumConstant(55)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STATUS_MENTION_RESHARE,

    /**
     * Media picker opened from the My Status FAB camera-capture control.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code MY_STATUS_FAB_CAMERA_CAPTURE = 56}.
     */
    @WamEnumConstant(56)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    MY_STATUS_FAB_CAMERA_CAPTURE,

    /**
     * Media picker opened from the My Status FAB camera media strip.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code MY_STATUS_FAB_CAMERA_MEDIA_STRIP = 57}.
     */
    @WamEnumConstant(57)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    MY_STATUS_FAB_CAMERA_MEDIA_STRIP,

    /**
     * Media picker opened from the status-tab FAB camera-capture control.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STATUS_TAB_FAB_CAMERA_CAPTURE = 58}.
     */
    @WamEnumConstant(58)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STATUS_TAB_FAB_CAMERA_CAPTURE,

    /**
     * Media picker opened from the status-tab FAB camera media strip.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STATUS_TAB_FAB_CAMERA_MEDIA_STRIP = 59}.
     */
    @WamEnumConstant(59)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STATUS_TAB_FAB_CAMERA_MEDIA_STRIP,

    /**
     * Media picker opened from the home-toolbar chats camera-capture control.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code HOME_TOOLBAR_CHATS_CAMERA_CAPTURE = 60}.
     */
    @WamEnumConstant(60)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    HOME_TOOLBAR_CHATS_CAMERA_CAPTURE,

    /**
     * Media picker opened from the home-toolbar chats camera media strip.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code HOME_TOOLBAR_CHATS_CAMERA_MEDIA_STRIP = 61}.
     */
    @WamEnumConstant(61)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    HOME_TOOLBAR_CHATS_CAMERA_MEDIA_STRIP,

    /**
     * Media picker opened from the home-toolbar chats camera photo library.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code HOME_TOOLBAR_CHATS_CAMERA_PHOTO_LIBRARY = 62}.
     */
    @WamEnumConstant(62)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    HOME_TOOLBAR_CHATS_CAMERA_PHOTO_LIBRARY,

    /**
     * Media picker opened from the home-toolbar status camera-capture control.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code HOME_TOOLBAR_STATUS_CAMERA_CAPTURE = 63}.
     */
    @WamEnumConstant(63)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    HOME_TOOLBAR_STATUS_CAMERA_CAPTURE,

    /**
     * Media picker opened from the home-toolbar status camera media strip.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code HOME_TOOLBAR_STATUS_CAMERA_MEDIA_STRIP = 64}.
     */
    @WamEnumConstant(64)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    HOME_TOOLBAR_STATUS_CAMERA_MEDIA_STRIP,

    /**
     * Media picker opened from the home-toolbar status camera photo library.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code HOME_TOOLBAR_STATUS_CAMERA_PHOTO_LIBRARY = 65}.
     */
    @WamEnumConstant(65)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    HOME_TOOLBAR_STATUS_CAMERA_PHOTO_LIBRARY,

    /**
     * Media picker opened from the home-toolbar calls camera-capture control.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code HOME_TOOLBAR_CALLS_CAMERA_CAPTURE = 66}.
     */
    @WamEnumConstant(66)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    HOME_TOOLBAR_CALLS_CAMERA_CAPTURE,

    /**
     * Media picker opened from the home-toolbar calls camera media strip.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code HOME_TOOLBAR_CALLS_CAMERA_MEDIA_STRIP = 67}.
     */
    @WamEnumConstant(67)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    HOME_TOOLBAR_CALLS_CAMERA_MEDIA_STRIP,

    /**
     * Media picker opened from the home-toolbar calls camera photo library.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code HOME_TOOLBAR_CALLS_CAMERA_PHOTO_LIBRARY = 68}.
     */
    @WamEnumConstant(68)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    HOME_TOOLBAR_CALLS_CAMERA_PHOTO_LIBRARY,

    /**
     * Media picker opened from the home-toolbar community camera-capture control.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code HOME_TOOLBAR_COMMUNITY_CAMERA_CAPTURE = 69}.
     */
    @WamEnumConstant(69)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    HOME_TOOLBAR_COMMUNITY_CAMERA_CAPTURE,

    /**
     * Media picker opened from the home-toolbar community camera media strip.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code HOME_TOOLBAR_COMMUNITY_CAMERA_MEDIA_STRIP = 70}.
     */
    @WamEnumConstant(70)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    HOME_TOOLBAR_COMMUNITY_CAMERA_MEDIA_STRIP,

    /**
     * Media picker opened from the home-toolbar community camera photo library.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code HOME_TOOLBAR_COMMUNITY_CAMERA_PHOTO_LIBRARY = 71}.
     */
    @WamEnumConstant(71)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    HOME_TOOLBAR_COMMUNITY_CAMERA_PHOTO_LIBRARY,

    /**
     * Media picker opened from the status-tab self-POG camera media strip.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STATUS_TAB_SELF_POG_CAMERA_MEDIA_STRIP = 72}.
     */
    @WamEnumConstant(72)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STATUS_TAB_SELF_POG_CAMERA_MEDIA_STRIP,

    /**
     * Media picker opened from the status-tab self-POG camera-capture control.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STATUS_TAB_SELF_POG_CAMERA_CAPTURE = 73}.
     */
    @WamEnumConstant(73)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STATUS_TAB_SELF_POG_CAMERA_CAPTURE,

    /**
     * Media picker opened from the chats-tab camera media strip.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code CHATS_TAB_CAMERA_MEDIA_STRIP = 74}.
     */
    @WamEnumConstant(74)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    CHATS_TAB_CAMERA_MEDIA_STRIP,

    /**
     * Media picker opened from the chats-tab camera-capture control.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code CHATS_TAB_CAMERA_CAPTURE = 75}.
     */
    @WamEnumConstant(75)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    CHATS_TAB_CAMERA_CAPTURE,

    /**
     * Media picker opened from the self-POG on status viewer.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code SELF_POG_ON_STATUS_VIEWER = 76}.
     */
    @WamEnumConstant(76)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    SELF_POG_ON_STATUS_VIEWER,

    /**
     * Media picker opened from the sticker maker camera-capture control.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STICKER_MAKER_CAMERA_CAPTURE = 77}.
     */
    @WamEnumConstant(77)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STICKER_MAKER_CAMERA_CAPTURE,

    /**
     * Media picker opened from the updates-tab "create status" camera button.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code UPDATES_TAB_CREATE_STATUS_CAMERA_BUTTON = 78}.
     */
    @WamEnumConstant(78)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    UPDATES_TAB_CREATE_STATUS_CAMERA_BUTTON,

    /**
     * Media picker opened from the updates-tab "create status" unified button.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code UPDATES_TAB_CREATE_STATUS_UNIFIED_BUTTON = 79}.
     */
    @WamEnumConstant(79)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    UPDATES_TAB_CREATE_STATUS_UNIFIED_BUTTON,

    /**
     * Media picker opened from the My Status "add status" button.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code MY_STATUS_ADD_STATUS_BUTTON = 80}.
     */
    @WamEnumConstant(80)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    MY_STATUS_ADD_STATUS_BUTTON,

    /**
     * Media picker opened from the unified creation quick-action camera control.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code UNIFIED_CREATION_QUICK_ACTION_CAMERA = 81}.
     */
    @WamEnumConstant(81)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    UNIFIED_CREATION_QUICK_ACTION_CAMERA,

    /**
     * Media picker opened from the unified creation menu camera entry.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code UNIFIED_CREATION_MENU_CAMERA = 82}.
     */
    @WamEnumConstant(82)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    UNIFIED_CREATION_MENU_CAMERA,

    /**
     * Media picker opened from the unified creation menu gallery entry.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code UNIFIED_CREATION_MENU_GALLERY = 83}.
     */
    @WamEnumConstant(83)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    UNIFIED_CREATION_MENU_GALLERY,

    /**
     * Media picker opened from the share-extension status IG-story self-viewer.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code SHARE_EXTENSION_STATUS_IG_STORY_SELF_VIEWER = 84}.
     */
    @WamEnumConstant(84)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    SHARE_EXTENSION_STATUS_IG_STORY_SELF_VIEWER,

    /**
     * Media picker opened from the share-extension status IG-reels share.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code SHARE_EXTENSION_STATUS_IG_REELS_SHARE = 85}.
     */
    @WamEnumConstant(85)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    SHARE_EXTENSION_STATUS_IG_REELS_SHARE,

    /**
     * Media picker opened from a status post reshare.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STATUS_POST_RESHARE = 86}.
     */
    @WamEnumConstant(86)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STATUS_POST_RESHARE,

    /**
     * Media picker opened from the poll creation flow.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code POLL_CREATION = 87}.
     */
    @WamEnumConstant(87)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    POLL_CREATION,

    /**
     * Media picker opened to attach music attribution.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code MUSIC_ATTRIBUTION = 88}.
     */
    @WamEnumConstant(88)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    MUSIC_ATTRIBUTION,

    /**
     * Media picker opened from the music standalone-type flow.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code MUSIC_STANDALONE_TYPE = 89}.
     */
    @WamEnumConstant(89)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    MUSIC_STANDALONE_TYPE,

    /**
     * Media picker opened from a status prompt response.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STATUS_PROMPT_RESPONSE = 90}.
     */
    @WamEnumConstant(90)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STATUS_PROMPT_RESPONSE,

    /**
     * Media picker opened from a status layouts action.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STATUS_LAYOUTS_ACTION = 91}.
     */
    @WamEnumConstant(91)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STATUS_LAYOUTS_ACTION,

    /**
     * Media picker opened from the status layouts editor.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STATUS_LAYOUTS_EDITOR = 92}.
     */
    @WamEnumConstant(92)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STATUS_LAYOUTS_EDITOR,

    /**
     * Media picker opened from the AI Imagine bottom sheet.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code AI_IMAGINE_BOTTOM_SHEET = 93}.
     */
    @WamEnumConstant(93)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    AI_IMAGINE_BOTTOM_SHEET,

    /**
     * Media picker opened from the AI Imagine message quick action.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code AI_IMAGINE_MESSAGE_QUICK_ACTION = 94}.
     */
    @WamEnumConstant(94)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    AI_IMAGINE_MESSAGE_QUICK_ACTION,

    /**
     * Media picker opened from the Meta AI (MAIBA) knowledge surface.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code MAIBA_KNOWLEDGE = 95}.
     */
    @WamEnumConstant(95)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    MAIBA_KNOWLEDGE,

    /**
     * Media picker opened by forwarding from a status.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code FORWARD_FROM_STATUS = 96}.
     */
    @WamEnumConstant(96)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    FORWARD_FROM_STATUS,

    /**
     * Media picker opened from the AI composer camera entry.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code AI_COMPOSER_CAMERA = 97}.
     */
    @WamEnumConstant(97)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    AI_COMPOSER_CAMERA,

    /**
     * Media picker opened from the AI attachment tray camera entry.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code AI_ATTACHMENT_TRAY_CAMERA = 98}.
     */
    @WamEnumConstant(98)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    AI_ATTACHMENT_TRAY_CAMERA,

    /**
     * Media picker opened from the AI attachment tray gallery entry.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code AI_ATTACHMENT_TRAY_GALLERY = 99}.
     */
    @WamEnumConstant(99)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    AI_ATTACHMENT_TRAY_GALLERY,

    /**
     * Media picker opened from the group status info surface.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code GROUP_STATUS_INFO = 100}.
     */
    @WamEnumConstant(100)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    GROUP_STATUS_INFO,

    /**
     * Media picker opened from the status viewer top bar.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STATUS_VIEWER_TOPBAR = 101}.
     */
    @WamEnumConstant(101)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STATUS_VIEWER_TOPBAR,

    /**
     * Media picker opened to reshare after a status question answer.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STATUS_QUESTION_ANSWER_POST_RESHARE = 102}.
     */
    @WamEnumConstant(102)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STATUS_QUESTION_ANSWER_POST_RESHARE,

    /**
     * Media picker opened from the status "add yours" AI Imagine flow.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STATUS_ADD_YOURS_AI_IMAGINE = 103}.
     */
    @WamEnumConstant(103)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STATUS_ADD_YOURS_AI_IMAGINE,

    /**
     * Media picker opened from a newsletter question.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code NEWSLETTER_QUESTION = 104}.
     */
    @WamEnumConstant(104)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    NEWSLETTER_QUESTION,

    /**
     * Media picker opened from a newsletter question response reply.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code NEWSLETTER_QUESTION_RESPONSE_REPLY = 105}.
     */
    @WamEnumConstant(105)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    NEWSLETTER_QUESTION_RESPONSE_REPLY,

    /**
     * Media picker opened from a Facebook story.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code FACEBOOK_STORY = 106}.
     */
    @WamEnumConstant(106)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    FACEBOOK_STORY,

    /**
     * Media picker opened from an Instagram story.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code INSTAGRAM_STORY = 107}.
     */
    @WamEnumConstant(107)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    INSTAGRAM_STORY,

    /**
     * Media picker opened from the Meta AI (MAIBA) product info surface.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code MAIBA_PRODUCT_INFO = 108}.
     */
    @WamEnumConstant(108)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    MAIBA_PRODUCT_INFO,

    /**
     * Media picker opened to attach a layouts attribution.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code LAYOUTS_ATTRIBUTION = 109}.
     */
    @WamEnumConstant(109)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    LAYOUTS_ATTRIBUTION,

    /**
     * Media picker opened from the newsletter music surface.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code NEWSLETTER_MUSIC = 110}.
     */
    @WamEnumConstant(110)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    NEWSLETTER_MUSIC,

    /**
     * Media picker opened from a status widget.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code STATUS_WIDGET = 111}.
     */
    @WamEnumConstant(111)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    STATUS_WIDGET,

    /**
     * Media picker opened from the group status tab self-POG surface.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code GROUP_STATUS_TAB_SELF_POG = 112}.
     */
    @WamEnumConstant(112)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    GROUP_STATUS_TAB_SELF_POG,

    /**
     * Media picker opened from the My Group Status FAB camera control.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code MY_GROUP_STATUS_FAB_CAMERA = 113}.
     */
    @WamEnumConstant(113)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    MY_GROUP_STATUS_FAB_CAMERA,

    /**
     * Media picker opened from a channel thread page.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code CHANNEL_THREAD_PAGE = 114}.
     */
    @WamEnumConstant(114)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    CHANNEL_THREAD_PAGE,

    /**
     * Media picker opened from the channel status viewer top bar.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code CHANNEL_STATUS_VIEWER_TOP_BAR = 115}.
     */
    @WamEnumConstant(115)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    CHANNEL_STATUS_VIEWER_TOP_BAR,

    /**
     * Media picker opened from the channel status updates-tab self-POG.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code CHANNEL_STATUS_UPDATES_TAB_SELF_POG = 116}.
     */
    @WamEnumConstant(116)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    CHANNEL_STATUS_UPDATES_TAB_SELF_POG,

    /**
     * Media picker opened from the My Channel Status button.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code MY_CHANNEL_STATUS_BUTTON = 117}.
     */
    @WamEnumConstant(117)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    MY_CHANNEL_STATUS_BUTTON,

    /**
     * Media picker opened from an engagement card.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code ENGAGEMENT_CARD = 118}.
     */
    @WamEnumConstant(118)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    ENGAGEMENT_CARD,

    /**
     * Media picker opened from the group status chat header.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code GROUP_STATUS_CHAT_HEADER = 119}.
     */
    @WamEnumConstant(119)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    GROUP_STATUS_CHAT_HEADER,

    /**
     * Media picker opened from the share-extension status IG-posts share.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code SHARE_EXTENSION_STATUS_IG_POSTS_SHARE = 120}.
     */
    @WamEnumConstant(120)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    SHARE_EXTENSION_STATUS_IG_POSTS_SHARE,

    /**
     * Media picker opened from the chats-tab "new chat add status" entry.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code CHATS_TAB_NEW_CHAT_ADD_STATUS = 121}.
     */
    @WamEnumConstant(121)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    CHATS_TAB_NEW_CHAT_ADD_STATUS,

    /**
     * Media picker opened from the chats-tab self-POG surface.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code CHATS_TAB_SELF_POG = 122}.
     */
    @WamEnumConstant(122)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    CHATS_TAB_SELF_POG,

    /**
     * Media picker opened from an Instagram post.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code INSTAGRAM_POST = 123}.
     */
    @WamEnumConstant(123)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    INSTAGRAM_POST,

    /**
     * Media picker opened from an Instagram reel.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code INSTAGRAM_REEL = 124}.
     */
    @WamEnumConstant(124)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    INSTAGRAM_REEL,

    /**
     * Media picker opened from the group status chat header profile ring.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code GROUP_STATUS_CHAT_HEADER_PROFILE_RING = 125}.
     */
    @WamEnumConstant(125)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    GROUP_STATUS_CHAT_HEADER_PROFILE_RING,

    /**
     * Media picker opened from the group status chat header profile ring
     * dropdown.
     *
     * @implNote WAWebWamEnumMediaPickerOriginType.MEDIA_PICKER_ORIGIN_TYPE: {@code GROUP_STATUS_CHAT_HEADER_PROFILE_RING_DROPDOWN = 126}.
     */
    @WamEnumConstant(126)
    @WhatsAppWebExport(moduleName = "WAWebWamEnumMediaPickerOriginType",
            exports = "MEDIA_PICKER_ORIGIN_TYPE",
            adaptation = WhatsAppAdaptation.DIRECT)
    GROUP_STATUS_CHAT_HEADER_PROFILE_RING_DROPDOWN
}
