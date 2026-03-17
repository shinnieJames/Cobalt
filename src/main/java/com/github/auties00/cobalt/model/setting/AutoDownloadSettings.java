package com.github.auties00.cobalt.model.setting;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage(name = "AutoDownloadSettings")
public final class AutoDownloadSettings {
    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean downloadImages;

    @ProtobufProperty(index = 2, type = ProtobufType.BOOL)
    Boolean downloadAudio;

    @ProtobufProperty(index = 3, type = ProtobufType.BOOL)
    Boolean downloadVideo;

    @ProtobufProperty(index = 4, type = ProtobufType.BOOL)
    Boolean downloadDocuments;


    AutoDownloadSettings(Boolean downloadImages, Boolean downloadAudio, Boolean downloadVideo, Boolean downloadDocuments) {
        this.downloadImages = downloadImages;
        this.downloadAudio = downloadAudio;
        this.downloadVideo = downloadVideo;
        this.downloadDocuments = downloadDocuments;
    }

    public boolean downloadImages() {
        return downloadImages != null && downloadImages;
    }

    public boolean downloadAudio() {
        return downloadAudio != null && downloadAudio;
    }

    public boolean downloadVideo() {
        return downloadVideo != null && downloadVideo;
    }

    public boolean downloadDocuments() {
        return downloadDocuments != null && downloadDocuments;
    }

    public void setDownloadImages(Boolean downloadImages) {
        this.downloadImages = downloadImages;
    }

    public void setDownloadAudio(Boolean downloadAudio) {
        this.downloadAudio = downloadAudio;
    }

    public void setDownloadVideo(Boolean downloadVideo) {
        this.downloadVideo = downloadVideo;
    }

    public void setDownloadDocuments(Boolean downloadDocuments) {
        this.downloadDocuments = downloadDocuments;
    }
}
