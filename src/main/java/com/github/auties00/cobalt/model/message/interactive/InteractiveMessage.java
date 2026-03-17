package com.github.auties00.cobalt.model.message.interactive;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.commerce.ProductMessage;
import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;
import com.github.auties00.cobalt.model.message.location.LocationMessage;
import com.github.auties00.cobalt.model.message.media.AudioMessage;
import com.github.auties00.cobalt.model.message.media.DocumentMessage;
import com.github.auties00.cobalt.model.message.media.ImageMessage;
import com.github.auties00.cobalt.model.message.media.VideoMessage;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.ProtobufType;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "Message.InteractiveMessage")
public final class InteractiveMessage implements TemplateFormat, ContextualMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    InteractiveHeader header;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    Body body;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    Footer footer;

    @ProtobufProperty(index = 15, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;

    @ProtobufProperty(index = 16, type = ProtobufType.MESSAGE)
    UrlTrackingMap urlTrackingMap;

    @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
    ShopMessage shopStorefrontMessage;

    @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
    CollectionMessage collectionMessage;

    @ProtobufProperty(index = 6, type = ProtobufType.MESSAGE)
    NativeFlowMessage nativeFlowMessage;

    @ProtobufProperty(index = 7, type = ProtobufType.MESSAGE)
    CarouselMessage carouselMessage;


    InteractiveMessage(InteractiveHeader header, Body body, Footer footer, ContextInfo contextInfo, UrlTrackingMap urlTrackingMap, ShopMessage shopStorefrontMessage, CollectionMessage collectionMessage, NativeFlowMessage nativeFlowMessage, CarouselMessage carouselMessage) {
        this.header = header;
        this.body = body;
        this.footer = footer;
        this.contextInfo = contextInfo;
        this.urlTrackingMap = urlTrackingMap;
        this.shopStorefrontMessage = shopStorefrontMessage;
        this.collectionMessage = collectionMessage;
        this.nativeFlowMessage = nativeFlowMessage;
        this.carouselMessage = carouselMessage;
    }

    public Optional<InteractiveHeader> header() {
        return Optional.ofNullable(header);
    }

    public Optional<Body> body() {
        return Optional.ofNullable(body);
    }

    public Optional<Footer> footer() {
        return Optional.ofNullable(footer);
    }

    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    public Optional<UrlTrackingMap> urlTrackingMap() {
        return Optional.ofNullable(urlTrackingMap);
    }

    public Optional<? extends InteractiveMessageContent> content() {
        if (shopStorefrontMessage != null) return Optional.of(shopStorefrontMessage);
        if (collectionMessage != null) return Optional.of(collectionMessage);
        if (nativeFlowMessage != null) return Optional.of(nativeFlowMessage);
        if (carouselMessage != null) return Optional.of(carouselMessage);
        return Optional.empty();
    }

    public void setHeader(InteractiveHeader header) {
        this.header = header;
    }

    public void setBody(Body body) {
        this.body = body;
    }

    public void setFooter(Footer footer) {
        this.footer = footer;
    }

    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }

    public void setUrlTrackingMap(UrlTrackingMap urlTrackingMap) {
        this.urlTrackingMap = urlTrackingMap;
    }

    public void setShopStorefrontMessage(ShopMessage shopStorefrontMessage) {
        this.shopStorefrontMessage = shopStorefrontMessage;
    }

    public void setCollectionMessage(CollectionMessage collectionMessage) {
        this.collectionMessage = collectionMessage;
    }

    public void setNativeFlowMessage(NativeFlowMessage nativeFlowMessage) {
        this.nativeFlowMessage = nativeFlowMessage;
    }

    public void setCarouselMessage(CarouselMessage carouselMessage) {
        this.carouselMessage = carouselMessage;
    }

    public sealed interface Media permits AudioMessage {
    }

    public sealed interface MediaSpec permits DocumentMessage, ImageMessage, MediaSpec.JpegThumbnail, VideoMessage, LocationMessage, ProductMessage {

        final class JpegThumbnail implements MediaSpec {
            byte[] jpegThumbnail;

            JpegThumbnail(byte[] jpegThumbnail) {
                this.jpegThumbnail = jpegThumbnail;
            }

            @ProtobufSerializer
            public byte[] jpegThumbnail() {
                return jpegThumbnail;
            }

            @ProtobufDeserializer
            public static JpegThumbnail of(byte[] jpegThumbnail) {
                return new JpegThumbnail(jpegThumbnail);
            }
        }
    }

    @ProtobufMessage(name = "Message.InteractiveMessage.Body")
    public static final class Body {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String text;


        Body(String text) {
            this.text = text;
        }

        public Optional<String> text() {
            return Optional.ofNullable(text);
        }

        public void setText(String text) {
            this.text = text;
    }
    }

    @ProtobufMessage(name = "Message.InteractiveMessage.CarouselMessage")
    public static final class CarouselMessage implements InteractiveMessageContent {
        @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
        List<InteractiveMessage> cards;

        @ProtobufProperty(index = 2, type = ProtobufType.INT32)
        Integer messageVersion;

        @ProtobufProperty(index = 3, type = ProtobufType.ENUM)
        CarouselMessage.CarouselCardType carouselCardType;


        CarouselMessage(List<InteractiveMessage> cards, Integer messageVersion, CarouselCardType carouselCardType) {
            this.cards = cards;
            this.messageVersion = messageVersion;
            this.carouselCardType = carouselCardType;
        }

        public List<InteractiveMessage> cards() {
            return cards == null ? List.of() : Collections.unmodifiableList(cards);
        }

        public OptionalInt messageVersion() {
            return messageVersion == null ? OptionalInt.empty() : OptionalInt.of(messageVersion);
        }

        public Optional<CarouselCardType> carouselCardType() {
            return Optional.ofNullable(carouselCardType);
        }

        public void setCards(List<InteractiveMessage> cards) {
            this.cards = cards;
    }

        public void setMessageVersion(Integer messageVersion) {
            this.messageVersion = messageVersion;
    }

        public void setCarouselCardType(CarouselCardType carouselCardType) {
            this.carouselCardType = carouselCardType;
    }

        @ProtobufEnum(name = "Message.InteractiveMessage.CarouselMessage.CarouselCardType")
        public static enum CarouselCardType {
            UNKNOWN(0),
            HSCROLL_CARDS(1),
            ALBUM_IMAGE(2);

            CarouselCardType(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            final int index;

            public int index() {
                return this.index;
            }
        }
    }

    @ProtobufMessage(name = "Message.InteractiveMessage.CollectionMessage")
    public static final class CollectionMessage implements InteractiveMessageContent {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        Jid bizJid;

        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String id;

        @ProtobufProperty(index = 3, type = ProtobufType.INT32)
        Integer messageVersion;


        CollectionMessage(Jid bizJid, String id, Integer messageVersion) {
            this.bizJid = bizJid;
            this.id = id;
            this.messageVersion = messageVersion;
        }

        public Optional<Jid> bizJid() {
            return Optional.ofNullable(bizJid);
        }

        public Optional<String> id() {
            return Optional.ofNullable(id);
        }

        public OptionalInt messageVersion() {
            return messageVersion == null ? OptionalInt.empty() : OptionalInt.of(messageVersion);
        }

        public void setBizJid(Jid bizJid) {
            this.bizJid = bizJid;
    }

        public void setId(String id) {
            this.id = id;
    }

        public void setMessageVersion(Integer messageVersion) {
            this.messageVersion = messageVersion;
    }
    }

    @ProtobufMessage(name = "Message.InteractiveMessage.Footer")
    public static final class Footer {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String text;

        @ProtobufProperty(index = 3, type = ProtobufType.BOOL)
        Boolean hasMediaAttachment;

        @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
        AudioMessage audioMessage;


        Footer(String text, Boolean hasMediaAttachment, AudioMessage audioMessage) {
            this.text = text;
            this.hasMediaAttachment = hasMediaAttachment;
            this.audioMessage = audioMessage;
        }

        public Optional<String> text() {
            return Optional.ofNullable(text);
        }

        public boolean hasMediaAttachment() {
            return hasMediaAttachment != null && hasMediaAttachment;
        }

        public Optional<? extends Media> media() {
            if (audioMessage != null) return Optional.of(audioMessage);
            return Optional.empty();
        }

        public void setText(String text) {
            this.text = text;
    }

        public void setHasMediaAttachment(Boolean hasMediaAttachment) {
            this.hasMediaAttachment = hasMediaAttachment;
    }

        public void setAudioMessage(AudioMessage audioMessage) {
            this.audioMessage = audioMessage;
    }
    }

    @ProtobufMessage(name = "Message.InteractiveMessage.Header")
    public static final class InteractiveHeader {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String title;

        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String subtitle;

        @ProtobufProperty(index = 5, type = ProtobufType.BOOL)
        Boolean hasMediaAttachment;

        @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
        DocumentMessage documentMessage;

        @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
        ImageMessage imageMessage;

        @ProtobufProperty(index = 6, type = ProtobufType.BYTES)
        byte[] jpegThumbnail;

        @ProtobufProperty(index = 7, type = ProtobufType.MESSAGE)
        VideoMessage videoMessage;

        @ProtobufProperty(index = 8, type = ProtobufType.MESSAGE)
        LocationMessage locationMessage;

        @ProtobufProperty(index = 9, type = ProtobufType.MESSAGE)
        ProductMessage productMessage;


        InteractiveHeader(String title, String subtitle, Boolean hasMediaAttachment, DocumentMessage documentMessage, ImageMessage imageMessage, byte[] jpegThumbnail, VideoMessage videoMessage, LocationMessage locationMessage, ProductMessage productMessage) {
            this.title = title;
            this.subtitle = subtitle;
            this.hasMediaAttachment = hasMediaAttachment;
            this.documentMessage = documentMessage;
            this.imageMessage = imageMessage;
            this.jpegThumbnail = jpegThumbnail;
            this.videoMessage = videoMessage;
            this.locationMessage = locationMessage;
            this.productMessage = productMessage;
        }

        public Optional<String> title() {
            return Optional.ofNullable(title);
        }

        public Optional<String> subtitle() {
            return Optional.ofNullable(subtitle);
        }

        public boolean hasMediaAttachment() {
            return hasMediaAttachment != null && hasMediaAttachment;
        }

        public Optional<? extends MediaSpec> media() {
            if (documentMessage != null) return Optional.of(documentMessage);
            if (imageMessage != null) return Optional.of(imageMessage);
            if (jpegThumbnail != null) return Optional.of(MediaSpec.JpegThumbnail.of(jpegThumbnail));
            if (videoMessage != null) return Optional.of(videoMessage);
            if (locationMessage != null) return Optional.of(locationMessage);
            if (productMessage != null) return Optional.of(productMessage);
            return Optional.empty();
        }

        public void setTitle(String title) {
            this.title = title;
    }

        public void setSubtitle(String subtitle) {
            this.subtitle = subtitle;
    }

        public void setHasMediaAttachment(Boolean hasMediaAttachment) {
            this.hasMediaAttachment = hasMediaAttachment;
    }

        public void setDocumentMessage(DocumentMessage documentMessage) {
            this.documentMessage = documentMessage;
    }

        public void setImageMessage(ImageMessage imageMessage) {
            this.imageMessage = imageMessage;
    }

        public void setJpegThumbnail(byte[] jpegThumbnail) {
            this.jpegThumbnail = jpegThumbnail;
    }

        public void setVideoMessage(VideoMessage videoMessage) {
            this.videoMessage = videoMessage;
    }

        public void setLocationMessage(LocationMessage locationMessage) {
            this.locationMessage = locationMessage;
    }

        public void setProductMessage(ProductMessage productMessage) {
            this.productMessage = productMessage;
    }
    }

    @ProtobufMessage(name = "Message.InteractiveMessage.NativeFlowMessage")
    public static final class NativeFlowMessage implements InteractiveMessageContent {
        @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
        List<NativeFlowButton> buttons;

        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String messageParamsJson;

        @ProtobufProperty(index = 3, type = ProtobufType.INT32)
        Integer messageVersion;


        NativeFlowMessage(List<NativeFlowButton> buttons, String messageParamsJson, Integer messageVersion) {
            this.buttons = buttons;
            this.messageParamsJson = messageParamsJson;
            this.messageVersion = messageVersion;
        }

        public List<NativeFlowButton> buttons() {
            return buttons == null ? List.of() : Collections.unmodifiableList(buttons);
        }

        public Optional<String> messageParamsJson() {
            return Optional.ofNullable(messageParamsJson);
        }

        public OptionalInt messageVersion() {
            return messageVersion == null ? OptionalInt.empty() : OptionalInt.of(messageVersion);
        }

        public void setButtons(List<NativeFlowButton> buttons) {
            this.buttons = buttons;
    }

        public void setMessageParamsJson(String messageParamsJson) {
            this.messageParamsJson = messageParamsJson;
    }

        public void setMessageVersion(Integer messageVersion) {
            this.messageVersion = messageVersion;
    }

        @ProtobufMessage(name = "Message.InteractiveMessage.NativeFlowMessage.NativeFlowButton")
        public static final class NativeFlowButton {
            @ProtobufProperty(index = 1, type = ProtobufType.STRING)
            String name;

            @ProtobufProperty(index = 2, type = ProtobufType.STRING)
            String buttonParamsJson;


            NativeFlowButton(String name, String buttonParamsJson) {
                this.name = name;
                this.buttonParamsJson = buttonParamsJson;
            }

            public Optional<String> name() {
                return Optional.ofNullable(name);
            }

            public Optional<String> buttonParamsJson() {
                return Optional.ofNullable(buttonParamsJson);
            }

            public void setName(String name) {
                this.name = name;
    }

            public void setButtonParamsJson(String buttonParamsJson) {
                this.buttonParamsJson = buttonParamsJson;
    }
        }
    }

    @ProtobufMessage(name = "Message.InteractiveMessage.ShopMessage")
    public static final class ShopMessage implements InteractiveMessageContent {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String id;

        @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
        ShopMessage.Surface surface;

        @ProtobufProperty(index = 3, type = ProtobufType.INT32)
        Integer messageVersion;


        ShopMessage(String id, Surface surface, Integer messageVersion) {
            this.id = id;
            this.surface = surface;
            this.messageVersion = messageVersion;
        }

        public Optional<String> id() {
            return Optional.ofNullable(id);
        }

        public Optional<Surface> surface() {
            return Optional.ofNullable(surface);
        }

        public OptionalInt messageVersion() {
            return messageVersion == null ? OptionalInt.empty() : OptionalInt.of(messageVersion);
        }

        public void setId(String id) {
            this.id = id;
    }

        public void setSurface(Surface surface) {
            this.surface = surface;
    }

        public void setMessageVersion(Integer messageVersion) {
            this.messageVersion = messageVersion;
    }

        @ProtobufEnum(name = "Message.InteractiveMessage.ShopMessage.Surface")
        public static enum Surface {
            UNKNOWN_SURFACE(0),
            FB(1),
            IG(2),
            WA(3);

            Surface(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            final int index;

            public int index() {
                return this.index;
            }
        }
    }
}
