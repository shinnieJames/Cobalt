package com.github.auties00.cobalt.model.message.media;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalLong;

@ProtobufMessage(name = "EmbeddedMusic")
public final class EmbeddedMusic implements EmbeddedContentVariant {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String musicContentMediaId;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String songId;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String author;

    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    String title;

    @ProtobufProperty(index = 5, type = ProtobufType.STRING)
    String artworkDirectPath;

    @ProtobufProperty(index = 6, type = ProtobufType.BYTES)
    byte[] artworkSha256;

    @ProtobufProperty(index = 7, type = ProtobufType.BYTES)
    byte[] artworkEncSha256;

    @ProtobufProperty(index = 8, type = ProtobufType.STRING)
    String artistAttribution;

    @ProtobufProperty(index = 9, type = ProtobufType.BYTES)
    byte[] countryBlocklist;

    @ProtobufProperty(index = 10, type = ProtobufType.BOOL)
    Boolean isExplicit;

    @ProtobufProperty(index = 11, type = ProtobufType.BYTES)
    byte[] artworkMediaKey;

    @ProtobufProperty(index = 12, type = ProtobufType.INT64)
    Long musicSongStartTimeInMs;

    @ProtobufProperty(index = 13, type = ProtobufType.INT64)
    Long derivedContentStartTimeInMs;

    @ProtobufProperty(index = 14, type = ProtobufType.INT64)
    Long overlapDurationInMs;


    EmbeddedMusic(String musicContentMediaId, String songId, String author, String title, String artworkDirectPath, byte[] artworkSha256, byte[] artworkEncSha256, String artistAttribution, byte[] countryBlocklist, Boolean isExplicit, byte[] artworkMediaKey, Long musicSongStartTimeInMs, Long derivedContentStartTimeInMs, Long overlapDurationInMs) {
        this.musicContentMediaId = musicContentMediaId;
        this.songId = songId;
        this.author = author;
        this.title = title;
        this.artworkDirectPath = artworkDirectPath;
        this.artworkSha256 = artworkSha256;
        this.artworkEncSha256 = artworkEncSha256;
        this.artistAttribution = artistAttribution;
        this.countryBlocklist = countryBlocklist;
        this.isExplicit = isExplicit;
        this.artworkMediaKey = artworkMediaKey;
        this.musicSongStartTimeInMs = musicSongStartTimeInMs;
        this.derivedContentStartTimeInMs = derivedContentStartTimeInMs;
        this.overlapDurationInMs = overlapDurationInMs;
    }

    public Optional<String> musicContentMediaId() {
        return Optional.ofNullable(musicContentMediaId);
    }

    public Optional<String> songId() {
        return Optional.ofNullable(songId);
    }

    public Optional<String> author() {
        return Optional.ofNullable(author);
    }

    public Optional<String> title() {
        return Optional.ofNullable(title);
    }

    public Optional<String> artworkDirectPath() {
        return Optional.ofNullable(artworkDirectPath);
    }

    public Optional<byte[]> artworkSha256() {
        return Optional.ofNullable(artworkSha256);
    }

    public Optional<byte[]> artworkEncSha256() {
        return Optional.ofNullable(artworkEncSha256);
    }

    public Optional<String> artistAttribution() {
        return Optional.ofNullable(artistAttribution);
    }

    public Optional<byte[]> countryBlocklist() {
        return Optional.ofNullable(countryBlocklist);
    }

    public boolean isExplicit() {
        return isExplicit != null && isExplicit;
    }

    public Optional<byte[]> artworkMediaKey() {
        return Optional.ofNullable(artworkMediaKey);
    }

    public OptionalLong musicSongStartTimeInMs() {
        return musicSongStartTimeInMs == null ? OptionalLong.empty() : OptionalLong.of(musicSongStartTimeInMs);
    }

    public OptionalLong derivedContentStartTimeInMs() {
        return derivedContentStartTimeInMs == null ? OptionalLong.empty() : OptionalLong.of(derivedContentStartTimeInMs);
    }

    public OptionalLong overlapDurationInMs() {
        return overlapDurationInMs == null ? OptionalLong.empty() : OptionalLong.of(overlapDurationInMs);
    }

    public void setMusicContentMediaId(String musicContentMediaId) {
        this.musicContentMediaId = musicContentMediaId;
    }

    public void setSongId(String songId) {
        this.songId = songId;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setArtworkDirectPath(String artworkDirectPath) {
        this.artworkDirectPath = artworkDirectPath;
    }

    public void setArtworkSha256(byte[] artworkSha256) {
        this.artworkSha256 = artworkSha256;
    }

    public void setArtworkEncSha256(byte[] artworkEncSha256) {
        this.artworkEncSha256 = artworkEncSha256;
    }

    public void setArtistAttribution(String artistAttribution) {
        this.artistAttribution = artistAttribution;
    }

    public void setCountryBlocklist(byte[] countryBlocklist) {
        this.countryBlocklist = countryBlocklist;
    }

    public void setExplicit(Boolean isExplicit) {
        this.isExplicit = isExplicit;
    }

    public void setArtworkMediaKey(byte[] artworkMediaKey) {
        this.artworkMediaKey = artworkMediaKey;
    }

    public void setMusicSongStartTimeInMs(Long musicSongStartTimeInMs) {
        this.musicSongStartTimeInMs = musicSongStartTimeInMs;
    }

    public void setDerivedContentStartTimeInMs(Long derivedContentStartTimeInMs) {
        this.derivedContentStartTimeInMs = derivedContentStartTimeInMs;
    }

    public void setOverlapDurationInMs(Long overlapDurationInMs) {
        this.overlapDurationInMs = overlapDurationInMs;
    }
}
