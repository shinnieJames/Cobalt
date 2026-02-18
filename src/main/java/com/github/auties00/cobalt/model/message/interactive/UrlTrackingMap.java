package com.github.auties00.cobalt.model.message.interactive;

import java.util.Collections;
import java.util.List;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "UrlTrackingMap")
public final class UrlTrackingMap {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    List<UrlTrackingMapElement> urlTrackingMapElements;


    UrlTrackingMap(List<UrlTrackingMapElement> urlTrackingMapElements) {
        this.urlTrackingMapElements = urlTrackingMapElements;
    }

    public List<UrlTrackingMapElement> urlTrackingMapElements() {
        return urlTrackingMapElements == null ? List.of() : Collections.unmodifiableList(urlTrackingMapElements);
    }

    public UrlTrackingMap setUrlTrackingMapElements(List<UrlTrackingMapElement> urlTrackingMapElements) {
        this.urlTrackingMapElements = urlTrackingMapElements;
        return this;
    }

    @ProtobufMessage(name = "UrlTrackingMap.UrlTrackingMapElement")
    public static final class UrlTrackingMapElement {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String originalUrl;

        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String unconsentedUsersUrl;

        @ProtobufProperty(index = 3, type = ProtobufType.STRING)
        String consentedUsersUrl;

        @ProtobufProperty(index = 4, type = ProtobufType.UINT32)
        Integer cardIndex;


        UrlTrackingMapElement(String originalUrl, String unconsentedUsersUrl, String consentedUsersUrl, Integer cardIndex) {
            this.originalUrl = originalUrl;
            this.unconsentedUsersUrl = unconsentedUsersUrl;
            this.consentedUsersUrl = consentedUsersUrl;
            this.cardIndex = cardIndex;
        }

        public Optional<String> originalUrl() {
            return Optional.ofNullable(originalUrl);
        }

        public Optional<String> unconsentedUsersUrl() {
            return Optional.ofNullable(unconsentedUsersUrl);
        }

        public Optional<String> consentedUsersUrl() {
            return Optional.ofNullable(consentedUsersUrl);
        }

        public OptionalInt cardIndex() {
            return cardIndex == null ? OptionalInt.empty() : OptionalInt.of(cardIndex);
        }

        public UrlTrackingMapElement setOriginalUrl(String originalUrl) {
            this.originalUrl = originalUrl;
            return this;
        }

        public UrlTrackingMapElement setUnconsentedUsersUrl(String unconsentedUsersUrl) {
            this.unconsentedUsersUrl = unconsentedUsersUrl;
            return this;
        }

        public UrlTrackingMapElement setConsentedUsersUrl(String consentedUsersUrl) {
            this.consentedUsersUrl = consentedUsersUrl;
            return this;
        }

        public UrlTrackingMapElement setCardIndex(Integer cardIndex) {
            this.cardIndex = cardIndex;
            return this;
        }
    }
}
