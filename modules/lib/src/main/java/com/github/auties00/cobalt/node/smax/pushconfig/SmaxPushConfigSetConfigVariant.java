package com.github.auties00.cobalt.node.smax.pushconfig;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.smax.SmaxOperation;
import com.github.auties00.cobalt.node.smax.util.SmaxBaseServerErrorMixin;
import com.github.auties00.cobalt.node.smax.util.SmaxIqResultResponseMixin;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Sealed disjunction of platform-specific {@code <config>} payloads for
 * {@link SmaxPushConfigSetSetVariant.Config}.
 *
 * @apiNote
 * Mirrors WA Web's {@code WASmaxOutPushConfigConfigMixins} branch
 * selector: one variant per supported push platform. Embedders pick the
 * variant matching their notification backend (Web Push, APNs, FCM-style
 * Android, WNS, FB, Enterprise) and pass the resulting
 * {@link SmaxPushConfigSetConfigVariant} into
 * {@link SmaxPushConfigSetSetVariant.Config}.
 */
public sealed interface SmaxPushConfigSetConfigVariant
        permits SmaxPushConfigSetConfigVariant.FbConfig, SmaxPushConfigSetConfigVariant.AndroidConfig,
        SmaxPushConfigSetConfigVariant.AppleConfig, SmaxPushConfigSetConfigVariant.WnsConfig,
        SmaxPushConfigSetConfigVariant.EnterpriseConfig, SmaxPushConfigSetConfigVariant.WebConfig {

    /**
     * Builds the {@code <config platform=...>} child node.
     *
     * @apiNote
     * Invoked by {@link SmaxPushConfigSetSetVariant.Config#toNode()} to
     * materialise the variant into the outbound stanza.
     *
     * @return the {@link Node}
     */
    Node toNode();

    /**
     * The Facebook-client {@code <config platform="fb">} variant.
     *
     * @apiNote
     * Registers a Facebook-app push channel; carries the FB app id and
     * device id with an optional FB user id.
     */
    @WhatsAppWebModule(moduleName = "WASmaxOutPushConfigFBClientMixin")
    final class FbConfig implements SmaxPushConfigSetConfigVariant {
        /**
         * The mandatory {@code appid} attribute.
         */
        private final String configAppid;

        /**
         * The mandatory {@code deviceid} attribute.
         */
        private final String configDeviceid;

        /**
         * The optional {@code fbid} attribute.
         */
        private final String configFbid;

        /**
         * Constructs a Facebook-client config.
         *
         * @apiNote
         * Used directly by embedders mirroring the FB-client push
         * pipeline.
         *
         * @param configAppid    the {@code appid} attribute
         * @param configDeviceid the {@code deviceid} attribute
         * @param configFbid     the optional {@code fbid} attribute
         * @throws NullPointerException if any required argument is
         *                              {@code null}
         */
        public FbConfig(String configAppid, String configDeviceid, String configFbid) {
            this.configAppid = Objects.requireNonNull(configAppid, "configAppid cannot be null");
            this.configDeviceid = Objects.requireNonNull(configDeviceid, "configDeviceid cannot be null");
            this.configFbid = configFbid;
        }

        /**
         * Returns the {@code appid} attribute.
         *
         * @apiNote
         * Used by {@link #toNode()} to populate the FB app id.
         *
         * @return the appid
         */
        public String configAppid() {
            return configAppid;
        }

        /**
         * Returns the {@code deviceid} attribute.
         *
         * @apiNote
         * Used by {@link #toNode()} to populate the FB device id.
         *
         * @return the device id
         */
        public String configDeviceid() {
            return configDeviceid;
        }

        /**
         * Returns the optional {@code fbid} attribute.
         *
         * @apiNote
         * Used by {@link #toNode()} to optionally populate the FB user
         * id.
         *
         * @return an {@link Optional} carrying the FB id
         */
        public Optional<String> configFbid() {
            return Optional.ofNullable(configFbid);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation hard-codes {@code platform="fb"} per the
         * {@code WASmaxOutPushConfigFBClientMixin.mergeFBClientMixin}
         * fixture and emits the optional {@code fbid} attribute only
         * when non-null.
         */
        @Override
        @WhatsAppWebExport(moduleName = "WASmaxOutPushConfigFBClientMixin",
                exports = "mergeFBClientMixin",
                adaptation = WhatsAppAdaptation.DIRECT)
        public Node toNode() {
            var builder = new NodeBuilder()
                    .description("config")
                    .attribute("platform", "fb")
                    .attribute("appid", configAppid)
                    .attribute("deviceid", configDeviceid);
            if (configFbid != null) {
                builder.attribute("fbid", configFbid);
            }
            return builder.build();
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation compares the three carried attributes.
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (FbConfig) obj;
            return Objects.equals(this.configAppid, that.configAppid)
                    && Objects.equals(this.configDeviceid, that.configDeviceid)
                    && Objects.equals(this.configFbid, that.configFbid);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation hashes the three carried attributes.
         */
        @Override
        public int hashCode() {
            return Objects.hash(configAppid, configDeviceid, configFbid);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation mirrors the record-like rendering used
         * across the {@code Smax*} stanza family.
         */
        @Override
        public String toString() {
            return "SmaxPushConfigSetConfigVariant.FbConfig[configAppid=" + configAppid
                    + ", configDeviceid=" + configDeviceid
                    + ", configFbid=" + configFbid + ']';
        }
    }

    /**
     * The Android-client {@code <config>} variant.
     *
     * @apiNote
     * Carries a list of per-group mute items; the relay uses the list to
     * suppress push deliveries for muted groups on the Android
     * notification channel.
     */
    @WhatsAppWebModule(moduleName = "WASmaxOutPushConfigAndroidClientMixin")
    @WhatsAppWebModule(moduleName = "WASmaxOutPushConfigAndroidClientConfigMixin")
    final class AndroidConfig implements SmaxPushConfigSetConfigVariant {
        /**
         * The list of {@code <item jid mute/>} mute entries.
         */
        private final List<AndroidMuteItem> itemArgs;

        /**
         * Constructs an Android-client config.
         *
         * @apiNote
         * The supplied list is defensively copied to keep the variant
         * immutable.
         *
         * @param itemArgs the mute items
         * @throws NullPointerException if {@code itemArgs} is
         *                              {@code null}
         */
        public AndroidConfig(List<AndroidMuteItem> itemArgs) {
            Objects.requireNonNull(itemArgs, "itemArgs cannot be null");
            this.itemArgs = List.copyOf(itemArgs);
        }

        /**
         * Returns the mute items.
         *
         * @apiNote
         * Exposed for test and audit code; the list is unmodifiable.
         *
         * @return an unmodifiable {@link List} of
         *         {@link AndroidMuteItem}
         */
        public List<AndroidMuteItem> itemArgs() {
            return itemArgs;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation emits one {@code <item>} grandchild per
         * entry under the single {@code <config>} parent, mirroring the
         * {@code mergeAndroidClientMixin} fixture.
         */
        @Override
        @WhatsAppWebExport(moduleName = "WASmaxOutPushConfigAndroidClientMixin",
                exports = "mergeAndroidClientMixin",
                adaptation = WhatsAppAdaptation.DIRECT)
        public Node toNode() {
            var children = new ArrayList<Node>(itemArgs.size());
            for (var item : itemArgs) {
                children.add(item.toNode());
            }
            return new NodeBuilder()
                    .description("config")
                    .content(children)
                    .build();
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation compares the items list.
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (AndroidConfig) obj;
            return Objects.equals(this.itemArgs, that.itemArgs);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation hashes the items list.
         */
        @Override
        public int hashCode() {
            return Objects.hash(itemArgs);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation mirrors the record-like rendering used
         * across the {@code Smax*} stanza family.
         */
        @Override
        public String toString() {
            return "SmaxPushConfigSetConfigVariant.AndroidConfig[itemArgs=" + itemArgs + ']';
        }

        /**
         * A single {@code <item jid mute/>} mute entry carried by an
         * {@link AndroidConfig}.
         *
         * @apiNote
         * Pairs a group {@link Jid} with the numeric mute marker the
         * relay forwards to the Android notification channel.
         */
        public static final class AndroidMuteItem {
            /**
             * The group {@link Jid} being muted.
             */
            private final Jid itemJid;

            /**
             * The numeric mute marker.
             */
            private final long itemMute;

            /**
             * Constructs a mute item.
             *
             * @apiNote
             * Used directly by embedders building the
             * {@link AndroidConfig} list.
             *
             * @param itemJid  the group {@link Jid}
             * @param itemMute the mute marker
             * @throws NullPointerException if {@code itemJid} is
             *                              {@code null}
             */
            public AndroidMuteItem(Jid itemJid, long itemMute) {
                this.itemJid = Objects.requireNonNull(itemJid, "itemJid cannot be null");
                this.itemMute = itemMute;
            }

            /**
             * Returns the group {@link Jid}.
             *
             * @apiNote
             * Used by {@link #toNode()} to populate the {@code jid}
             * attribute.
             *
             * @return the {@link Jid}
             */
            public Jid itemJid() {
                return itemJid;
            }

            /**
             * Returns the mute marker.
             *
             * @apiNote
             * Used by {@link #toNode()} to populate the {@code mute}
             * attribute.
             *
             * @return the marker
             */
            public long itemMute() {
                return itemMute;
            }

            /**
             * Builds the {@code <item jid mute/>} child node.
             *
             * @apiNote
             * Used by {@link AndroidConfig#toNode()} to assemble the
             * surrounding {@code <config>} payload.
             *
             * @implNote
             * This implementation matches the
             * {@code makeAndroidClientItem} fixture verbatim.
             *
             * @return the {@link Node}
             */
            @WhatsAppWebExport(moduleName = "WASmaxOutPushConfigAndroidClientMixin",
                    exports = "makeAndroidClientItem",
                    adaptation = WhatsAppAdaptation.DIRECT)
            public Node toNode() {
                return new NodeBuilder()
                        .description("item")
                        .attribute("jid", itemJid)
                        .attribute("mute", itemMute)
                        .build();
            }

            /**
             * {@inheritDoc}
             *
             * @implNote
             * This implementation compares both the JID and the mute
             * marker.
             */
            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (AndroidMuteItem) obj;
                return this.itemMute == that.itemMute
                        && Objects.equals(this.itemJid, that.itemJid);
            }

            /**
             * {@inheritDoc}
             *
             * @implNote
             * This implementation hashes both fields via
             * {@link Objects#hash(Object...)}.
             */
            @Override
            public int hashCode() {
                return Objects.hash(itemJid, itemMute);
            }

            /**
             * {@inheritDoc}
             *
             * @implNote
             * This implementation mirrors the record-like rendering
             * used across the {@code Smax*} stanza family.
             */
            @Override
            public String toString() {
                return "SmaxPushConfigSetConfigVariant.AndroidConfig.AndroidMuteItem[itemJid=" + itemJid
                        + ", itemMute=" + itemMute + ']';
            }
        }
    }

    /**
     * The Apple-client {@code <config>} variant.
     *
     * @apiNote
     * A thick record of APNs/iOS-specific attributes: device token, VoIP
     * token, NSE (Notification Service Extension) flags, Apple Watch
     * pairing, plus the per-chat preference list. Embedders mirroring
     * the iOS client populate the full set; most other embedders can
     * skip it.
     */
    @WhatsAppWebModule(moduleName = "WASmaxOutPushConfigAppleClientMixin")
    final class AppleConfig implements SmaxPushConfigSetConfigVariant {
        /**
         * The mandatory {@code platform} attribute (typically one of
         * {@code "iphone"} or {@code "ipad"}).
         */
        private final String configPlatform;

        /**
         * Whether the {@code version="2"} marker is set.
         */
        private final boolean hasConfigVersion2;

        /**
         * The optional {@code id} attribute.
         */
        private final String configId;

        /**
         * The optional {@code voip} attribute carrying the VoIP token.
         */
        private final String configVoip;

        /**
         * The mandatory {@code preview} attribute.
         */
        private final String configPreview;

        /**
         * The mandatory {@code default} attribute.
         */
        private final String configDefault;

        /**
         * The mandatory {@code groups} attribute.
         */
        private final String configGroups;

        /**
         * The mandatory {@code call} attribute.
         */
        private final String configCall;

        /**
         * The optional {@code status_sound} attribute.
         */
        private final String configStatusSound;

        /**
         * The mandatory {@code lg} (language) attribute.
         */
        private final String configLg;

        /**
         * The mandatory {@code lc} (locale) attribute.
         */
        private final String configLc;

        /**
         * The optional {@code background_location} attribute.
         */
        private final String configBackgroundLocation;

        /**
         * The optional {@code nse_ver} attribute.
         */
        private final String configNseVer;

        /**
         * The optional {@code nse_call} attribute.
         */
        private final String configNseCall;

        /**
         * The optional {@code nse_read} attribute.
         */
        private final String configNseRead;

        /**
         * The optional {@code nse_retry} attribute.
         */
        private final String configNseRetry;

        /**
         * The optional {@code reg_push} attribute.
         */
        private final String configRegPush;

        /**
         * The optional {@code pkey} attribute.
         */
        private final String configPkey;

        /**
         * The mandatory {@code voip_payload_type} attribute.
         */
        private final String configVoipPayloadType;

        /**
         * The optional {@code settings} attribute.
         */
        private final Long configSettings;

        /**
         * The optional {@code app_mute} attribute.
         */
        private final Long configAppMute;

        /**
         * The optional {@code apple_watch_id} attribute.
         */
        private final String configAppleWatchId;

        /**
         * The optional {@code apple_watch_pkey} attribute.
         */
        private final String configAppleWatchPkey;

        /**
         * The per-chat {@link AppleItem} entries.
         */
        private final List<AppleItem> itemArgs;

        /**
         * Constructs an Apple-client config.
         *
         * @apiNote
         * Used by embedders mirroring the iOS push pipeline; non-iOS
         * embedders pick a different variant.
         *
         * @param configPlatform           the platform marker
         * @param hasConfigVersion2        whether the
         *                                 {@code version="2"} marker is
         *                                 set
         * @param configId                 the optional id
         * @param configVoip               the optional voip token
         * @param configPreview            the preview marker
         * @param configDefault            the default marker
         * @param configGroups             the groups marker
         * @param configCall               the call marker
         * @param configStatusSound        the optional status-sound
         *                                 marker
         * @param configLg                 the language tag
         * @param configLc                 the locale tag
         * @param configBackgroundLocation the optional bg-location marker
         * @param configNseVer             the optional NSE version
         * @param configNseCall            the optional NSE call marker
         * @param configNseRead            the optional NSE read marker
         * @param configNseRetry           the optional NSE retry marker
         * @param configRegPush            the optional reg-push marker
         * @param configPkey               the optional pkey
         * @param configVoipPayloadType    the voip payload type
         * @param configSettings           the optional settings mask
         * @param configAppMute            the optional app-mute mask
         * @param configAppleWatchId       the optional Apple Watch id
         * @param configAppleWatchPkey     the optional Apple Watch pkey
         * @param itemArgs                 the per-item entries
         * @throws NullPointerException if any required argument is
         *                              {@code null}
         */
        public AppleConfig(String configPlatform, boolean hasConfigVersion2,
                           String configId, String configVoip,
                           String configPreview, String configDefault, String configGroups,
                           String configCall, String configStatusSound,
                           String configLg, String configLc, String configBackgroundLocation,
                           String configNseVer, String configNseCall, String configNseRead,
                           String configNseRetry, String configRegPush, String configPkey,
                           String configVoipPayloadType, Long configSettings, Long configAppMute,
                           String configAppleWatchId, String configAppleWatchPkey,
                           List<AppleItem> itemArgs) {
            this.configPlatform = Objects.requireNonNull(configPlatform, "configPlatform cannot be null");
            this.hasConfigVersion2 = hasConfigVersion2;
            this.configId = configId;
            this.configVoip = configVoip;
            this.configPreview = Objects.requireNonNull(configPreview, "configPreview cannot be null");
            this.configDefault = Objects.requireNonNull(configDefault, "configDefault cannot be null");
            this.configGroups = Objects.requireNonNull(configGroups, "configGroups cannot be null");
            this.configCall = Objects.requireNonNull(configCall, "configCall cannot be null");
            this.configStatusSound = configStatusSound;
            this.configLg = Objects.requireNonNull(configLg, "configLg cannot be null");
            this.configLc = Objects.requireNonNull(configLc, "configLc cannot be null");
            this.configBackgroundLocation = configBackgroundLocation;
            this.configNseVer = configNseVer;
            this.configNseCall = configNseCall;
            this.configNseRead = configNseRead;
            this.configNseRetry = configNseRetry;
            this.configRegPush = configRegPush;
            this.configPkey = configPkey;
            this.configVoipPayloadType = Objects.requireNonNull(configVoipPayloadType,
                    "configVoipPayloadType cannot be null");
            this.configSettings = configSettings;
            this.configAppMute = configAppMute;
            this.configAppleWatchId = configAppleWatchId;
            this.configAppleWatchPkey = configAppleWatchPkey;
            Objects.requireNonNull(itemArgs, "itemArgs cannot be null");
            this.itemArgs = List.copyOf(itemArgs);
        }

        /**
         * Returns the platform marker.
         *
         * @apiNote
         * Distinguishes {@code iphone} from {@code ipad} on the relay
         * side; embedders pass the marker matching their device family.
         *
         * @return the marker
         */
        public String configPlatform() {
            return configPlatform;
        }

        /**
         * Returns whether {@code version="2"} is set.
         *
         * @apiNote
         * Toggles between the legacy and the modern v2 fixture; modern
         * embedders set this to {@code true}.
         *
         * @return {@code true} when set
         */
        public boolean hasConfigVersion2() {
            return hasConfigVersion2;
        }

        /**
         * Returns the optional id.
         *
         * @apiNote
         * Cobalt embedders normally leave this null; the relay only
         * surfaces it for legacy iOS variants.
         *
         * @return an {@link Optional} carrying the id
         */
        public Optional<String> configId() {
            return Optional.ofNullable(configId);
        }

        /**
         * Returns the optional voip token.
         *
         * @apiNote
         * Required by embedders that route VoIP wake-ups through APNs;
         * leave null when VoIP is disabled.
         *
         * @return an {@link Optional} carrying the token
         */
        public Optional<String> configVoip() {
            return Optional.ofNullable(configVoip);
        }

        /**
         * Returns the preview marker.
         *
         * @apiNote
         * Controls whether the relay attaches the message preview to the
         * APNs payload; embedders mirror the iOS Settings toggle.
         *
         * @return the marker
         */
        public String configPreview() {
            return configPreview;
        }

        /**
         * Returns the default marker.
         *
         * @apiNote
         * Carries the default chat-notification mode.
         *
         * @return the marker
         */
        public String configDefault() {
            return configDefault;
        }

        /**
         * Returns the groups marker.
         *
         * @apiNote
         * Carries the default group-notification mode.
         *
         * @return the marker
         */
        public String configGroups() {
            return configGroups;
        }

        /**
         * Returns the call marker.
         *
         * @apiNote
         * Carries the default call-notification mode.
         *
         * @return the marker
         */
        public String configCall() {
            return configCall;
        }

        /**
         * Returns the optional status-sound marker.
         *
         * @apiNote
         * Used by embedders that surface a custom status-notification
         * sound; leave null to fall back to the system default.
         *
         * @return an {@link Optional} carrying the marker
         */
        public Optional<String> configStatusSound() {
            return Optional.ofNullable(configStatusSound);
        }

        /**
         * Returns the language tag.
         *
         * @apiNote
         * Picks the language used by the relay when localising
         * notification text.
         *
         * @return the tag
         */
        public String configLg() {
            return configLg;
        }

        /**
         * Returns the locale tag.
         *
         * @apiNote
         * Picks the locale used by the relay when localising
         * notification text.
         *
         * @return the tag
         */
        public String configLc() {
            return configLc;
        }

        /**
         * Returns the optional background-location marker.
         *
         * @apiNote
         * Required only by embedders that surface a background-location
         * UI; leave null otherwise.
         *
         * @return an {@link Optional} carrying the marker
         */
        public Optional<String> configBackgroundLocation() {
            return Optional.ofNullable(configBackgroundLocation);
        }

        /**
         * Returns the optional NSE version.
         *
         * @apiNote
         * Communicates the Notification Service Extension version that
         * embedders ship with their iOS bundle.
         *
         * @return an {@link Optional} carrying the version
         */
        public Optional<String> configNseVer() {
            return Optional.ofNullable(configNseVer);
        }

        /**
         * Returns the optional NSE call marker.
         *
         * @apiNote
         * Communicates whether the NSE handles call wake-ups on the
         * iOS client.
         *
         * @return an {@link Optional} carrying the marker
         */
        public Optional<String> configNseCall() {
            return Optional.ofNullable(configNseCall);
        }

        /**
         * Returns the optional NSE read marker.
         *
         * @apiNote
         * Communicates whether the NSE handles read-receipt wake-ups on
         * the iOS client.
         *
         * @return an {@link Optional} carrying the marker
         */
        public Optional<String> configNseRead() {
            return Optional.ofNullable(configNseRead);
        }

        /**
         * Returns the optional NSE retry marker.
         *
         * @apiNote
         * Communicates whether the NSE retries failed decryptions on the
         * iOS client.
         *
         * @return an {@link Optional} carrying the marker
         */
        public Optional<String> configNseRetry() {
            return Optional.ofNullable(configNseRetry);
        }

        /**
         * Returns the optional reg-push marker.
         *
         * @apiNote
         * Carries the registration-push flag used by the iOS client.
         *
         * @return an {@link Optional} carrying the marker
         */
        public Optional<String> configRegPush() {
            return Optional.ofNullable(configRegPush);
        }

        /**
         * Returns the optional pkey.
         *
         * @apiNote
         * Required by embedders that pin the iOS push channel to a
         * specific public key.
         *
         * @return an {@link Optional} carrying the pkey
         */
        public Optional<String> configPkey() {
            return Optional.ofNullable(configPkey);
        }

        /**
         * Returns the voip payload type.
         *
         * @apiNote
         * Picks the APNs VoIP payload shape; required even when
         * {@link #configVoip} is absent.
         *
         * @return the type
         */
        public String configVoipPayloadType() {
            return configVoipPayloadType;
        }

        /**
         * Returns the optional settings mask.
         *
         * @apiNote
         * Bitmask of notification settings forwarded to the relay.
         *
         * @return an {@link Optional} carrying the mask
         */
        public Optional<Long> configSettings() {
            return Optional.ofNullable(configSettings);
        }

        /**
         * Returns the optional app-mute mask.
         *
         * @apiNote
         * Bitmask of app-mute settings forwarded to the relay.
         *
         * @return an {@link Optional} carrying the mask
         */
        public Optional<Long> configAppMute() {
            return Optional.ofNullable(configAppMute);
        }

        /**
         * Returns the optional Apple Watch id.
         *
         * @apiNote
         * Required by embedders that pair an Apple Watch with the iOS
         * client.
         *
         * @return an {@link Optional} carrying the id
         */
        public Optional<String> configAppleWatchId() {
            return Optional.ofNullable(configAppleWatchId);
        }

        /**
         * Returns the optional Apple Watch pkey.
         *
         * @apiNote
         * Required by embedders that pin the paired Apple Watch push
         * channel to a specific public key.
         *
         * @return an {@link Optional} carrying the pkey
         */
        public Optional<String> configAppleWatchPkey() {
            return Optional.ofNullable(configAppleWatchPkey);
        }

        /**
         * Returns the per-item entries.
         *
         * @apiNote
         * One entry per chat with non-default mute/notify/call
         * preferences; the relay merges the list with the global
         * defaults.
         *
         * @return an unmodifiable {@link List} of {@link AppleItem}
         */
        public List<AppleItem> itemArgs() {
            return itemArgs;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation hard-codes the mandatory attributes per
         * the {@code mergeAppleClientMixin} fixture and adds every
         * optional attribute only when its backing field is non-null;
         * the {@code version="2"} marker is emitted via the
         * {@link NodeBuilder} conditional-attribute overload keyed on
         * {@link #hasConfigVersion2}.
         */
        @Override
        @WhatsAppWebExport(moduleName = "WASmaxOutPushConfigAppleClientMixin",
                exports = "mergeAppleClientMixin",
                adaptation = WhatsAppAdaptation.DIRECT)
        public Node toNode() {
            var children = new ArrayList<Node>(itemArgs.size());
            for (var item : itemArgs) {
                children.add(item.toNode());
            }
            var builder = new NodeBuilder()
                    .description("config")
                    .attribute("platform", configPlatform)
                    .attribute("version", "2", hasConfigVersion2)
                    .attribute("preview", configPreview)
                    .attribute("default", configDefault)
                    .attribute("groups", configGroups)
                    .attribute("call", configCall)
                    .attribute("lg", configLg)
                    .attribute("lc", configLc)
                    .attribute("voip_payload_type", configVoipPayloadType);
            if (configId != null) {
                builder.attribute("id", configId);
            }
            if (configVoip != null) {
                builder.attribute("voip", configVoip);
            }
            if (configStatusSound != null) {
                builder.attribute("status_sound", configStatusSound);
            }
            if (configBackgroundLocation != null) {
                builder.attribute("background_location", configBackgroundLocation);
            }
            if (configNseVer != null) {
                builder.attribute("nse_ver", configNseVer);
            }
            if (configNseCall != null) {
                builder.attribute("nse_call", configNseCall);
            }
            if (configNseRead != null) {
                builder.attribute("nse_read", configNseRead);
            }
            if (configNseRetry != null) {
                builder.attribute("nse_retry", configNseRetry);
            }
            if (configRegPush != null) {
                builder.attribute("reg_push", configRegPush);
            }
            if (configPkey != null) {
                builder.attribute("pkey", configPkey);
            }
            if (configSettings != null) {
                builder.attribute("settings", configSettings);
            }
            if (configAppMute != null) {
                builder.attribute("app_mute", configAppMute);
            }
            if (configAppleWatchId != null) {
                builder.attribute("apple_watch_id", configAppleWatchId);
            }
            if (configAppleWatchPkey != null) {
                builder.attribute("apple_watch_pkey", configAppleWatchPkey);
            }
            builder.content(children);
            return builder.build();
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation compares every carried attribute and the
         * items list.
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (AppleConfig) obj;
            return this.hasConfigVersion2 == that.hasConfigVersion2
                    && Objects.equals(this.configPlatform, that.configPlatform)
                    && Objects.equals(this.configId, that.configId)
                    && Objects.equals(this.configVoip, that.configVoip)
                    && Objects.equals(this.configPreview, that.configPreview)
                    && Objects.equals(this.configDefault, that.configDefault)
                    && Objects.equals(this.configGroups, that.configGroups)
                    && Objects.equals(this.configCall, that.configCall)
                    && Objects.equals(this.configStatusSound, that.configStatusSound)
                    && Objects.equals(this.configLg, that.configLg)
                    && Objects.equals(this.configLc, that.configLc)
                    && Objects.equals(this.configBackgroundLocation, that.configBackgroundLocation)
                    && Objects.equals(this.configNseVer, that.configNseVer)
                    && Objects.equals(this.configNseCall, that.configNseCall)
                    && Objects.equals(this.configNseRead, that.configNseRead)
                    && Objects.equals(this.configNseRetry, that.configNseRetry)
                    && Objects.equals(this.configRegPush, that.configRegPush)
                    && Objects.equals(this.configPkey, that.configPkey)
                    && Objects.equals(this.configVoipPayloadType, that.configVoipPayloadType)
                    && Objects.equals(this.configSettings, that.configSettings)
                    && Objects.equals(this.configAppMute, that.configAppMute)
                    && Objects.equals(this.configAppleWatchId, that.configAppleWatchId)
                    && Objects.equals(this.configAppleWatchPkey, that.configAppleWatchPkey)
                    && Objects.equals(this.itemArgs, that.itemArgs);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation combines a primary
         * {@link Objects#hash(Object...)} call over the scalar
         * attributes with a separate combine for the items list to keep
         * the running hash within the {@link Objects#hash(Object...)}
         * varargs limit.
         */
        @Override
        public int hashCode() {
            var result = Objects.hash(configPlatform, hasConfigVersion2, configId, configVoip,
                    configPreview, configDefault, configGroups, configCall, configStatusSound,
                    configLg, configLc, configBackgroundLocation, configNseVer, configNseCall,
                    configNseRead, configNseRetry, configRegPush, configPkey, configVoipPayloadType,
                    configSettings, configAppMute, configAppleWatchId, configAppleWatchPkey);
            result = 31 * result + Objects.hashCode(itemArgs);
            return result;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation mirrors the record-like rendering used
         * across the {@code Smax*} stanza family.
         */
        @Override
        public String toString() {
            return "SmaxPushConfigSetConfigVariant.AppleConfig[configPlatform=" + configPlatform
                    + ", hasConfigVersion2=" + hasConfigVersion2
                    + ", configId=" + configId
                    + ", configVoip=" + configVoip
                    + ", configPreview=" + configPreview
                    + ", configDefault=" + configDefault
                    + ", configGroups=" + configGroups
                    + ", configCall=" + configCall
                    + ", configStatusSound=" + configStatusSound
                    + ", configLg=" + configLg
                    + ", configLc=" + configLc
                    + ", configBackgroundLocation=" + configBackgroundLocation
                    + ", configNseVer=" + configNseVer
                    + ", configNseCall=" + configNseCall
                    + ", configNseRead=" + configNseRead
                    + ", configNseRetry=" + configNseRetry
                    + ", configRegPush=" + configRegPush
                    + ", configPkey=" + configPkey
                    + ", configVoipPayloadType=" + configVoipPayloadType
                    + ", configSettings=" + configSettings
                    + ", configAppMute=" + configAppMute
                    + ", configAppleWatchId=" + configAppleWatchId
                    + ", configAppleWatchPkey=" + configAppleWatchPkey
                    + ", itemArgs=" + itemArgs + ']';
        }

        /**
         * A single {@code <item jid mute? notify? call?/>} entry carried
         * by an {@link AppleConfig}.
         *
         * @apiNote
         * Pairs a chat {@link Jid} with the optional per-chat
         * mute/notify/call overrides; the relay merges the entry with
         * the global Apple defaults.
         */
        public static final class AppleItem {
            /**
             * The target chat {@link Jid}.
             */
            private final Jid itemJid;

            /**
             * The optional mute marker.
             */
            private final Long itemMute;

            /**
             * The optional notify marker.
             */
            private final String itemNotify;

            /**
             * The optional call marker.
             */
            private final String itemCall;

            /**
             * Constructs an Apple per-chat entry.
             *
             * @apiNote
             * Leave any marker null to fall back to the global Apple
             * default for the corresponding category.
             *
             * @param itemJid    the chat {@link Jid}
             * @param itemMute   the optional mute marker
             * @param itemNotify the optional notify marker
             * @param itemCall   the optional call marker
             * @throws NullPointerException if {@code itemJid} is
             *                              {@code null}
             */
            public AppleItem(Jid itemJid, Long itemMute, String itemNotify, String itemCall) {
                this.itemJid = Objects.requireNonNull(itemJid, "itemJid cannot be null");
                this.itemMute = itemMute;
                this.itemNotify = itemNotify;
                this.itemCall = itemCall;
            }

            /**
             * Returns the target chat {@link Jid}.
             *
             * @apiNote
             * Used by {@link #toNode()} to populate the {@code jid}
             * attribute.
             *
             * @return the {@link Jid}
             */
            public Jid itemJid() {
                return itemJid;
            }

            /**
             * Returns the optional mute marker.
             *
             * @apiNote
             * Used by {@link #toNode()} to optionally populate the
             * {@code mute} attribute.
             *
             * @return an {@link Optional} carrying the marker
             */
            public Optional<Long> itemMute() {
                return Optional.ofNullable(itemMute);
            }

            /**
             * Returns the optional notify marker.
             *
             * @apiNote
             * Used by {@link #toNode()} to optionally populate the
             * {@code notify} attribute.
             *
             * @return an {@link Optional} carrying the marker
             */
            public Optional<String> itemNotify() {
                return Optional.ofNullable(itemNotify);
            }

            /**
             * Returns the optional call marker.
             *
             * @apiNote
             * Used by {@link #toNode()} to optionally populate the
             * {@code call} attribute.
             *
             * @return an {@link Optional} carrying the marker
             */
            public Optional<String> itemCall() {
                return Optional.ofNullable(itemCall);
            }

            /**
             * Builds the {@code <item>} child node.
             *
             * @apiNote
             * Used by {@link AppleConfig#toNode()} to assemble the
             * surrounding {@code <config>} payload.
             *
             * @implNote
             * This implementation matches the {@code makeAppleClientItem}
             * fixture verbatim and emits each optional attribute only
             * when its backing field is non-null.
             *
             * @return the {@link Node}
             */
            @WhatsAppWebExport(moduleName = "WASmaxOutPushConfigAppleClientMixin",
                    exports = "makeAppleClientItem",
                    adaptation = WhatsAppAdaptation.DIRECT)
            public Node toNode() {
                var builder = new NodeBuilder()
                        .description("item")
                        .attribute("jid", itemJid);
                if (itemMute != null) {
                    builder.attribute("mute", itemMute);
                }
                if (itemNotify != null) {
                    builder.attribute("notify", itemNotify);
                }
                if (itemCall != null) {
                    builder.attribute("call", itemCall);
                }
                return builder.build();
            }

            /**
             * {@inheritDoc}
             *
             * @implNote
             * This implementation compares all four carried fields.
             */
            @Override
            public boolean equals(Object obj) {
                if (obj == this) {
                    return true;
                }
                if (obj == null || obj.getClass() != this.getClass()) {
                    return false;
                }
                var that = (AppleItem) obj;
                return Objects.equals(this.itemJid, that.itemJid)
                        && Objects.equals(this.itemMute, that.itemMute)
                        && Objects.equals(this.itemNotify, that.itemNotify)
                        && Objects.equals(this.itemCall, that.itemCall);
            }

            /**
             * {@inheritDoc}
             *
             * @implNote
             * This implementation hashes all four carried fields via
             * {@link Objects#hash(Object...)}.
             */
            @Override
            public int hashCode() {
                return Objects.hash(itemJid, itemMute, itemNotify, itemCall);
            }

            /**
             * {@inheritDoc}
             *
             * @implNote
             * This implementation mirrors the record-like rendering
             * used across the {@code Smax*} stanza family.
             */
            @Override
            public String toString() {
                return "SmaxPushConfigSetConfigVariant.AppleConfig.AppleItem[itemJid=" + itemJid
                        + ", itemMute=" + itemMute
                        + ", itemNotify=" + itemNotify
                        + ", itemCall=" + itemCall + ']';
            }
        }
    }

    /**
     * The Windows-Notification-Service-client
     * {@code <config platform="wns">} variant.
     *
     * @apiNote
     * Registers a WNS push channel for the Windows desktop client;
     * carries the WNS channel id and an optional version marker.
     */
    @WhatsAppWebModule(moduleName = "WASmaxOutPushConfigWNSClientMixin")
    final class WnsConfig implements SmaxPushConfigSetConfigVariant {
        /**
         * The optional {@code version} attribute.
         */
        private final String configVersion;

        /**
         * The mandatory {@code id} attribute carrying the WNS channel
         * id.
         */
        private final String configId;

        /**
         * Constructs a WNS config.
         *
         * @apiNote
         * Used directly by embedders mirroring the Windows desktop push
         * pipeline.
         *
         * @param configVersion the optional version
         * @param configId      the WNS channel id
         * @throws NullPointerException if {@code configId} is
         *                              {@code null}
         */
        public WnsConfig(String configVersion, String configId) {
            this.configVersion = configVersion;
            this.configId = Objects.requireNonNull(configId, "configId cannot be null");
        }

        /**
         * Returns the optional version.
         *
         * @apiNote
         * Used by {@link #toNode()} to optionally populate the
         * {@code version} attribute.
         *
         * @return an {@link Optional} carrying the version
         */
        public Optional<String> configVersion() {
            return Optional.ofNullable(configVersion);
        }

        /**
         * Returns the WNS channel id.
         *
         * @apiNote
         * Used by {@link #toNode()} to populate the {@code id}
         * attribute.
         *
         * @return the id
         */
        public String configId() {
            return configId;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation hard-codes {@code platform="wns"} per the
         * {@code mergeWNSClientMixin} fixture and emits the optional
         * {@code version} attribute only when non-null.
         */
        @Override
        @WhatsAppWebExport(moduleName = "WASmaxOutPushConfigWNSClientMixin",
                exports = "mergeWNSClientMixin",
                adaptation = WhatsAppAdaptation.DIRECT)
        public Node toNode() {
            var builder = new NodeBuilder()
                    .description("config")
                    .attribute("platform", "wns")
                    .attribute("id", configId);
            if (configVersion != null) {
                builder.attribute("version", configVersion);
            }
            return builder.build();
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation compares the two carried fields.
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (WnsConfig) obj;
            return Objects.equals(this.configVersion, that.configVersion)
                    && Objects.equals(this.configId, that.configId);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation hashes the two carried fields via
         * {@link Objects#hash(Object...)}.
         */
        @Override
        public int hashCode() {
            return Objects.hash(configVersion, configId);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation mirrors the record-like rendering used
         * across the {@code Smax*} stanza family.
         */
        @Override
        public String toString() {
            return "SmaxPushConfigSetConfigVariant.WnsConfig[configVersion=" + configVersion
                    + ", configId=" + configId + ']';
        }
    }

    /**
     * The Enterprise-client {@code <config platform="ent">} variant.
     *
     * @apiNote
     * Registers an enterprise-deployment push channel; carries only the
     * enterprise id.
     */
    @WhatsAppWebModule(moduleName = "WASmaxOutPushConfigEnterpriseClientMixin")
    final class EnterpriseConfig implements SmaxPushConfigSetConfigVariant {
        /**
         * The mandatory {@code id} attribute carrying the enterprise id.
         */
        private final String configId;

        /**
         * Constructs an enterprise-client config.
         *
         * @apiNote
         * Used directly by embedders mirroring the enterprise push
         * pipeline.
         *
         * @param configId the enterprise id
         * @throws NullPointerException if {@code configId} is
         *                              {@code null}
         */
        public EnterpriseConfig(String configId) {
            this.configId = Objects.requireNonNull(configId, "configId cannot be null");
        }

        /**
         * Returns the enterprise id.
         *
         * @apiNote
         * Used by {@link #toNode()} to populate the {@code id}
         * attribute.
         *
         * @return the id
         */
        public String configId() {
            return configId;
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation hard-codes {@code platform="ent"} per the
         * {@code mergeEnterpriseClientMixin} fixture.
         */
        @Override
        @WhatsAppWebExport(moduleName = "WASmaxOutPushConfigEnterpriseClientMixin",
                exports = "mergeEnterpriseClientMixin",
                adaptation = WhatsAppAdaptation.DIRECT)
        public Node toNode() {
            return new NodeBuilder()
                    .description("config")
                    .attribute("platform", "ent")
                    .attribute("id", configId)
                    .build();
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation compares the carried id.
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (EnterpriseConfig) obj;
            return Objects.equals(this.configId, that.configId);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation hashes the carried id.
         */
        @Override
        public int hashCode() {
            return Objects.hash(configId);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation mirrors the record-like rendering used
         * across the {@code Smax*} stanza family.
         */
        @Override
        public String toString() {
            return "SmaxPushConfigSetConfigVariant.EnterpriseConfig[configId=" + configId + ']';
        }
    }

    /**
     * The Web-client {@code <config platform="web">} variant.
     *
     * @apiNote
     * Registers a W3C Push API channel for the Web/Desktop client;
     * carries the {@code PushSubscription.endpoint} URL plus the
     * base64-encoded {@code auth} secret and {@code p256dh} application
     * server key returned by {@code PushManager.subscribe}. WA Web's
     * {@code WAWebSetPushConfigJob.setPushConfig} populates this variant
     * directly from a {@code PushSubscription} after
     * {@code WAWebSubscribePushManagerAction} obtains it; Cobalt
     * embedders that subscribe to a custom push service typically follow
     * the same flow.
     */
    @WhatsAppWebModule(moduleName = "WASmaxOutPushConfigWebClientMixin")
    final class WebConfig implements SmaxPushConfigSetConfigVariant {
        /**
         * The W3C Push API endpoint URL.
         */
        private final String configEndpoint;

        /**
         * The base64-encoded Push API auth secret.
         */
        private final String configAuth;

        /**
         * The base64-encoded P-256 application-server public key.
         */
        private final String configP256dh;

        /**
         * The optional language tag.
         */
        private final String configLg;

        /**
         * The optional locale tag.
         */
        private final String configLc;

        /**
         * Constructs a Web Push config.
         *
         * @apiNote
         * Populate {@code configEndpoint}, {@code configAuth}, and
         * {@code configP256dh} from the {@code PushSubscription}
         * returned by the W3C Push API; the language/locale tags are
         * forwarded verbatim to the relay for notification
         * localisation.
         *
         * @param configEndpoint the endpoint URL
         * @param configAuth     the auth secret
         * @param configP256dh   the P-256 key
         * @param configLg       the optional language tag
         * @param configLc       the optional locale tag
         * @throws NullPointerException if any required argument is
         *                              {@code null}
         */
        public WebConfig(String configEndpoint, String configAuth, String configP256dh,
                         String configLg, String configLc) {
            this.configEndpoint = Objects.requireNonNull(configEndpoint, "configEndpoint cannot be null");
            this.configAuth = Objects.requireNonNull(configAuth, "configAuth cannot be null");
            this.configP256dh = Objects.requireNonNull(configP256dh, "configP256dh cannot be null");
            this.configLg = configLg;
            this.configLc = configLc;
        }

        /**
         * Returns the endpoint URL.
         *
         * @apiNote
         * Used by {@link #toNode()} to populate the {@code endpoint}
         * attribute.
         *
         * @return the URL
         */
        public String configEndpoint() {
            return configEndpoint;
        }

        /**
         * Returns the auth secret.
         *
         * @apiNote
         * Used by {@link #toNode()} to populate the {@code auth}
         * attribute.
         *
         * @return the secret
         */
        public String configAuth() {
            return configAuth;
        }

        /**
         * Returns the P-256 key.
         *
         * @apiNote
         * Used by {@link #toNode()} to populate the {@code p256dh}
         * attribute.
         *
         * @return the key
         */
        public String configP256dh() {
            return configP256dh;
        }

        /**
         * Returns the optional language tag.
         *
         * @apiNote
         * Used by {@link #toNode()} to optionally populate the
         * {@code lg} attribute.
         *
         * @return an {@link Optional} carrying the tag
         */
        public Optional<String> configLg() {
            return Optional.ofNullable(configLg);
        }

        /**
         * Returns the optional locale tag.
         *
         * @apiNote
         * Used by {@link #toNode()} to optionally populate the
         * {@code lc} attribute.
         *
         * @return an {@link Optional} carrying the tag
         */
        public Optional<String> configLc() {
            return Optional.ofNullable(configLc);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation hard-codes {@code platform="web"} per the
         * {@code mergeWebClientMixin} fixture and emits the optional
         * {@code lg} and {@code lc} attributes only when non-null.
         */
        @Override
        @WhatsAppWebExport(moduleName = "WASmaxOutPushConfigWebClientMixin",
                exports = "mergeWebClientMixin",
                adaptation = WhatsAppAdaptation.DIRECT)
        public Node toNode() {
            var builder = new NodeBuilder()
                    .description("config")
                    .attribute("platform", "web")
                    .attribute("endpoint", configEndpoint)
                    .attribute("auth", configAuth)
                    .attribute("p256dh", configP256dh);
            if (configLg != null) {
                builder.attribute("lg", configLg);
            }
            if (configLc != null) {
                builder.attribute("lc", configLc);
            }
            return builder.build();
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation compares every carried attribute.
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (WebConfig) obj;
            return Objects.equals(this.configEndpoint, that.configEndpoint)
                    && Objects.equals(this.configAuth, that.configAuth)
                    && Objects.equals(this.configP256dh, that.configP256dh)
                    && Objects.equals(this.configLg, that.configLg)
                    && Objects.equals(this.configLc, that.configLc);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation hashes every carried attribute via
         * {@link Objects#hash(Object...)}.
         */
        @Override
        public int hashCode() {
            return Objects.hash(configEndpoint, configAuth, configP256dh, configLg, configLc);
        }

        /**
         * {@inheritDoc}
         *
         * @implNote
         * This implementation mirrors the record-like rendering used
         * across the {@code Smax*} stanza family.
         */
        @Override
        public String toString() {
            return "SmaxPushConfigSetConfigVariant.WebConfig[configEndpoint=" + configEndpoint
                    + ", configAuth=" + configAuth
                    + ", configP256dh=" + configP256dh
                    + ", configLg=" + configLg
                    + ", configLc=" + configLc + ']';
        }
    }
}
