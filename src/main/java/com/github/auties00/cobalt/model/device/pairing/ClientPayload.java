package com.github.auties00.cobalt.model.device.pairing;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.*;

@ProtobufMessage(name = "ClientPayload")
public final class ClientPayload {
    @ProtobufProperty(index = 1, type = ProtobufType.UINT64)
    Long username;

    @ProtobufProperty(index = 3, type = ProtobufType.BOOL)
    Boolean passive;

    @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
    UserAgent userAgent;

    @ProtobufProperty(index = 6, type = ProtobufType.MESSAGE)
    WebInfo webInfo;

    @ProtobufProperty(index = 7, type = ProtobufType.STRING)
    String pushName;

    @ProtobufProperty(index = 9, type = ProtobufType.SFIXED32)
    Integer sessionId;

    @ProtobufProperty(index = 10, type = ProtobufType.BOOL)
    Boolean shortConnect;

    @ProtobufProperty(index = 12, type = ProtobufType.ENUM)
    ConnectType connectType;

    @ProtobufProperty(index = 13, type = ProtobufType.ENUM)
    ConnectReason connectReason;

    @ProtobufProperty(index = 14, type = ProtobufType.INT32)
    List<Integer> shards;

    @ProtobufProperty(index = 15, type = ProtobufType.MESSAGE)
    DNSSource dnsSource;

    @ProtobufProperty(index = 16, type = ProtobufType.UINT32)
    Integer connectAttemptCount;

    @ProtobufProperty(index = 18, type = ProtobufType.UINT32)
    Integer device;

    @ProtobufProperty(index = 19, type = ProtobufType.MESSAGE)
    DevicePairingRegistrationData devicePairingData;

    @ProtobufProperty(index = 20, type = ProtobufType.ENUM)
    Product product;

    @ProtobufProperty(index = 21, type = ProtobufType.BYTES)
    byte[] fbCat;

    @ProtobufProperty(index = 22, type = ProtobufType.BYTES)
    byte[] fbUserAgent;

    @ProtobufProperty(index = 23, type = ProtobufType.BOOL)
    Boolean oc;

    @ProtobufProperty(index = 24, type = ProtobufType.INT32)
    Integer lc;

    @ProtobufProperty(index = 30, type = ProtobufType.ENUM)
    IOSAppExtension iosAppExtension;

    @ProtobufProperty(index = 31, type = ProtobufType.UINT64)
    Long fbAppId;

    @ProtobufProperty(index = 32, type = ProtobufType.BYTES)
    byte[] fbDeviceId;

    @ProtobufProperty(index = 33, type = ProtobufType.BOOL)
    Boolean pull;

    @ProtobufProperty(index = 34, type = ProtobufType.BYTES)
    byte[] paddingBytes;

    @ProtobufProperty(index = 36, type = ProtobufType.INT32)
    Integer yearClass;

    @ProtobufProperty(index = 37, type = ProtobufType.INT32)
    Integer memClass;

    @ProtobufProperty(index = 38, type = ProtobufType.MESSAGE)
    InteropData interopData;

    @ProtobufProperty(index = 40, type = ProtobufType.ENUM)
    TrafficAnonymization trafficAnonymization;

    @ProtobufProperty(index = 41, type = ProtobufType.BOOL)
    Boolean lidDbMigrated;

    @ProtobufProperty(index = 42, type = ProtobufType.ENUM)
    AccountType accountType;

    @ProtobufProperty(index = 43, type = ProtobufType.SFIXED32)
    Integer connectionSequenceInfo;

    @ProtobufProperty(index = 44, type = ProtobufType.BOOL)
    Boolean paaLink;

    @ProtobufProperty(index = 45, type = ProtobufType.INT32)
    Integer preacksCount;

    @ProtobufProperty(index = 46, type = ProtobufType.INT32)
    Integer processingQueueSize;


    ClientPayload(Long username, Boolean passive, UserAgent userAgent, WebInfo webInfo, String pushName, Integer sessionId, Boolean shortConnect, ConnectType connectType, ConnectReason connectReason, List<Integer> shards, DNSSource dnsSource, Integer connectAttemptCount, Integer device, DevicePairingRegistrationData devicePairingData, Product product, byte[] fbCat, byte[] fbUserAgent, Boolean oc, Integer lc, IOSAppExtension iosAppExtension, Long fbAppId, byte[] fbDeviceId, Boolean pull, byte[] paddingBytes, Integer yearClass, Integer memClass, InteropData interopData, TrafficAnonymization trafficAnonymization, Boolean lidDbMigrated, AccountType accountType, Integer connectionSequenceInfo, Boolean paaLink, Integer preacksCount, Integer processingQueueSize) {
        this.username = username;
        this.passive = passive;
        this.userAgent = userAgent;
        this.webInfo = webInfo;
        this.pushName = pushName;
        this.sessionId = sessionId;
        this.shortConnect = shortConnect;
        this.connectType = connectType;
        this.connectReason = connectReason;
        this.shards = shards;
        this.dnsSource = dnsSource;
        this.connectAttemptCount = connectAttemptCount;
        this.device = device;
        this.devicePairingData = devicePairingData;
        this.product = product;
        this.fbCat = fbCat;
        this.fbUserAgent = fbUserAgent;
        this.oc = oc;
        this.lc = lc;
        this.iosAppExtension = iosAppExtension;
        this.fbAppId = fbAppId;
        this.fbDeviceId = fbDeviceId;
        this.pull = pull;
        this.paddingBytes = paddingBytes;
        this.yearClass = yearClass;
        this.memClass = memClass;
        this.interopData = interopData;
        this.trafficAnonymization = trafficAnonymization;
        this.lidDbMigrated = lidDbMigrated;
        this.accountType = accountType;
        this.connectionSequenceInfo = connectionSequenceInfo;
        this.paaLink = paaLink;
        this.preacksCount = preacksCount;
        this.processingQueueSize = processingQueueSize;
    }

    public OptionalLong username() {
        return username == null ? OptionalLong.empty() : OptionalLong.of(username);
    }

    public boolean passive() {
        return passive != null && passive;
    }

    public Optional<UserAgent> userAgent() {
        return Optional.ofNullable(userAgent);
    }

    public Optional<WebInfo> webInfo() {
        return Optional.ofNullable(webInfo);
    }

    public Optional<String> pushName() {
        return Optional.ofNullable(pushName);
    }

    public OptionalInt sessionId() {
        return sessionId == null ? OptionalInt.empty() : OptionalInt.of(sessionId);
    }

    public boolean shortConnect() {
        return shortConnect != null && shortConnect;
    }

    public Optional<ConnectType> connectType() {
        return Optional.ofNullable(connectType);
    }

    public Optional<ConnectReason> connectReason() {
        return Optional.ofNullable(connectReason);
    }

    public List<Integer> shards() {
        return shards == null ? List.of() : Collections.unmodifiableList(shards);
    }

    public Optional<DNSSource> dnsSource() {
        return Optional.ofNullable(dnsSource);
    }

    public OptionalInt connectAttemptCount() {
        return connectAttemptCount == null ? OptionalInt.empty() : OptionalInt.of(connectAttemptCount);
    }

    public OptionalInt device() {
        return device == null ? OptionalInt.empty() : OptionalInt.of(device);
    }

    public Optional<DevicePairingRegistrationData> devicePairingData() {
        return Optional.ofNullable(devicePairingData);
    }

    public Optional<Product> product() {
        return Optional.ofNullable(product);
    }

    public Optional<byte[]> fbCat() {
        return Optional.ofNullable(fbCat);
    }

    public Optional<byte[]> fbUserAgent() {
        return Optional.ofNullable(fbUserAgent);
    }

    public boolean oc() {
        return oc != null && oc;
    }

    public OptionalInt lc() {
        return lc == null ? OptionalInt.empty() : OptionalInt.of(lc);
    }

    public Optional<IOSAppExtension> iosAppExtension() {
        return Optional.ofNullable(iosAppExtension);
    }

    public OptionalLong fbAppId() {
        return fbAppId == null ? OptionalLong.empty() : OptionalLong.of(fbAppId);
    }

    public Optional<byte[]> fbDeviceId() {
        return Optional.ofNullable(fbDeviceId);
    }

    public boolean pull() {
        return pull != null && pull;
    }

    public Optional<byte[]> paddingBytes() {
        return Optional.ofNullable(paddingBytes);
    }

    public OptionalInt yearClass() {
        return yearClass == null ? OptionalInt.empty() : OptionalInt.of(yearClass);
    }

    public OptionalInt memClass() {
        return memClass == null ? OptionalInt.empty() : OptionalInt.of(memClass);
    }

    public Optional<InteropData> interopData() {
        return Optional.ofNullable(interopData);
    }

    public Optional<TrafficAnonymization> trafficAnonymization() {
        return Optional.ofNullable(trafficAnonymization);
    }

    public boolean lidDbMigrated() {
        return lidDbMigrated != null && lidDbMigrated;
    }

    public Optional<AccountType> accountType() {
        return Optional.ofNullable(accountType);
    }

    public OptionalInt connectionSequenceInfo() {
        return connectionSequenceInfo == null ? OptionalInt.empty() : OptionalInt.of(connectionSequenceInfo);
    }

    public boolean paaLink() {
        return paaLink != null && paaLink;
    }

    public OptionalInt preacksCount() {
        return preacksCount == null ? OptionalInt.empty() : OptionalInt.of(preacksCount);
    }

    public OptionalInt processingQueueSize() {
        return processingQueueSize == null ? OptionalInt.empty() : OptionalInt.of(processingQueueSize);
    }

    public ClientPayload setUsername(Long username) {
        this.username = username;
        return this;
    }

    public ClientPayload setPassive(Boolean passive) {
        this.passive = passive;
        return this;
    }

    public ClientPayload setUserAgent(UserAgent userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    public ClientPayload setWebInfo(WebInfo webInfo) {
        this.webInfo = webInfo;
        return this;
    }

    public ClientPayload setPushName(String pushName) {
        this.pushName = pushName;
        return this;
    }

    public ClientPayload setSessionId(Integer sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public ClientPayload setShortConnect(Boolean shortConnect) {
        this.shortConnect = shortConnect;
        return this;
    }

    public ClientPayload setConnectType(ConnectType connectType) {
        this.connectType = connectType;
        return this;
    }

    public ClientPayload setConnectReason(ConnectReason connectReason) {
        this.connectReason = connectReason;
        return this;
    }

    public ClientPayload setShards(List<Integer> shards) {
        this.shards = shards;
        return this;
    }

    public ClientPayload setDnsSource(DNSSource dnsSource) {
        this.dnsSource = dnsSource;
        return this;
    }

    public ClientPayload setConnectAttemptCount(Integer connectAttemptCount) {
        this.connectAttemptCount = connectAttemptCount;
        return this;
    }

    public ClientPayload setDevice(Integer device) {
        this.device = device;
        return this;
    }

    public ClientPayload setDevicePairingData(DevicePairingRegistrationData devicePairingData) {
        this.devicePairingData = devicePairingData;
        return this;
    }

    public ClientPayload setProduct(Product product) {
        this.product = product;
        return this;
    }

    public ClientPayload setFbCat(byte[] fbCat) {
        this.fbCat = fbCat;
        return this;
    }

    public ClientPayload setFbUserAgent(byte[] fbUserAgent) {
        this.fbUserAgent = fbUserAgent;
        return this;
    }

    public ClientPayload setOc(Boolean oc) {
        this.oc = oc;
        return this;
    }

    public ClientPayload setLc(Integer lc) {
        this.lc = lc;
        return this;
    }

    public ClientPayload setIosAppExtension(IOSAppExtension iosAppExtension) {
        this.iosAppExtension = iosAppExtension;
        return this;
    }

    public ClientPayload setFbAppId(Long fbAppId) {
        this.fbAppId = fbAppId;
        return this;
    }

    public ClientPayload setFbDeviceId(byte[] fbDeviceId) {
        this.fbDeviceId = fbDeviceId;
        return this;
    }

    public ClientPayload setPull(Boolean pull) {
        this.pull = pull;
        return this;
    }

    public ClientPayload setPaddingBytes(byte[] paddingBytes) {
        this.paddingBytes = paddingBytes;
        return this;
    }

    public ClientPayload setYearClass(Integer yearClass) {
        this.yearClass = yearClass;
        return this;
    }

    public ClientPayload setMemClass(Integer memClass) {
        this.memClass = memClass;
        return this;
    }

    public ClientPayload setInteropData(InteropData interopData) {
        this.interopData = interopData;
        return this;
    }

    public ClientPayload setTrafficAnonymization(TrafficAnonymization trafficAnonymization) {
        this.trafficAnonymization = trafficAnonymization;
        return this;
    }

    public ClientPayload setLidDbMigrated(Boolean lidDbMigrated) {
        this.lidDbMigrated = lidDbMigrated;
        return this;
    }

    public ClientPayload setAccountType(AccountType accountType) {
        this.accountType = accountType;
        return this;
    }

    public ClientPayload setConnectionSequenceInfo(Integer connectionSequenceInfo) {
        this.connectionSequenceInfo = connectionSequenceInfo;
        return this;
    }

    public ClientPayload setPaaLink(Boolean paaLink) {
        this.paaLink = paaLink;
        return this;
    }

    public ClientPayload setPreacksCount(Integer preacksCount) {
        this.preacksCount = preacksCount;
        return this;
    }

    public ClientPayload setProcessingQueueSize(Integer processingQueueSize) {
        this.processingQueueSize = processingQueueSize;
        return this;
    }

    @ProtobufEnum(name = "ClientPayload.AccountType")
    public static enum AccountType {
        DEFAULT(0),
        GUEST(1);

        AccountType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufEnum(name = "ClientPayload.ConnectReason")
    public static enum ConnectReason {
        PUSH(0),
        USER_ACTIVATED(1),
        SCHEDULED(2),
        ERROR_RECONNECT(3),
        NETWORK_SWITCH(4),
        PING_RECONNECT(5),
        UNKNOWN(6);

        ConnectReason(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufEnum(name = "ClientPayload.ConnectType")
    public static enum ConnectType {
        CELLULAR_UNKNOWN(0),
        WIFI_UNKNOWN(1),
        CELLULAR_EDGE(100),
        CELLULAR_IDEN(101),
        CELLULAR_UMTS(102),
        CELLULAR_EVDO(103),
        CELLULAR_GPRS(104),
        CELLULAR_HSDPA(105),
        CELLULAR_HSUPA(106),
        CELLULAR_HSPA(107),
        CELLULAR_CDMA(108),
        CELLULAR_1XRTT(109),
        CELLULAR_EHRPD(110),
        CELLULAR_LTE(111),
        CELLULAR_HSPAP(112);

        ConnectType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufEnum(name = "ClientPayload.IOSAppExtension")
    public static enum IOSAppExtension {
        SHARE_EXTENSION(0),
        SERVICE_EXTENSION(1),
        INTENTS_EXTENSION(2);

        IOSAppExtension(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufEnum(name = "ClientPayload.Product")
    public static enum Product {
        WHATSAPP(0),
        MESSENGER(1),
        INTEROP(2),
        INTEROP_MSGR(3),
        WHATSAPP_LID(4);

        Product(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufEnum(name = "ClientPayload.TrafficAnonymization")
    public static enum TrafficAnonymization {
        OFF(0),
        STANDARD(1);

        TrafficAnonymization(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufMessage(name = "ClientPayload.DNSSource")
    public static final class DNSSource {
        @ProtobufProperty(index = 15, type = ProtobufType.ENUM)
        DNSSource.DNSResolutionMethod dnsMethod;

        @ProtobufProperty(index = 16, type = ProtobufType.BOOL)
        Boolean appCached;


        DNSSource(DNSResolutionMethod dnsMethod, Boolean appCached) {
            this.dnsMethod = dnsMethod;
            this.appCached = appCached;
        }

        public Optional<DNSResolutionMethod> dnsMethod() {
            return Optional.ofNullable(dnsMethod);
        }

        public boolean appCached() {
            return appCached != null && appCached;
        }

        public DNSSource setDnsMethod(DNSResolutionMethod dnsMethod) {
            this.dnsMethod = dnsMethod;
            return this;
        }

        public DNSSource setAppCached(Boolean appCached) {
            this.appCached = appCached;
            return this;
        }

        @ProtobufEnum(name = "ClientPayload.DNSSource.DNSResolutionMethod")
        public static enum DNSResolutionMethod {
            SYSTEM(0),
            GOOGLE(1),
            HARDCODED(2),
            OVERRIDE(3),
            FALLBACK(4),
            MNS(5),
            MNS_SECONDARY(6),
            SOCKS_PROXY(7);

            DNSResolutionMethod(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            final int index;

            public int index() {
                return this.index;
            }
        }
    }

    @ProtobufMessage(name = "ClientPayload.DevicePairingRegistrationData")
    public static final class DevicePairingRegistrationData {
        @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
        byte[] eRegid;

        @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
        byte[] eKeytype;

        @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
        byte[] eIdent;

        @ProtobufProperty(index = 4, type = ProtobufType.BYTES)
        byte[] eSkeyId;

        @ProtobufProperty(index = 5, type = ProtobufType.BYTES)
        byte[] eSkeyVal;

        @ProtobufProperty(index = 6, type = ProtobufType.BYTES)
        byte[] eSkeySig;

        @ProtobufProperty(index = 7, type = ProtobufType.BYTES)
        byte[] buildHash;

        @ProtobufProperty(index = 8, type = ProtobufType.BYTES)
        byte[] deviceProps;


        DevicePairingRegistrationData(byte[] eRegid, byte[] eKeytype, byte[] eIdent, byte[] eSkeyId, byte[] eSkeyVal, byte[] eSkeySig, byte[] buildHash, byte[] deviceProps) {
            this.eRegid = eRegid;
            this.eKeytype = eKeytype;
            this.eIdent = eIdent;
            this.eSkeyId = eSkeyId;
            this.eSkeyVal = eSkeyVal;
            this.eSkeySig = eSkeySig;
            this.buildHash = buildHash;
            this.deviceProps = deviceProps;
        }

        public Optional<byte[]> eRegid() {
            return Optional.ofNullable(eRegid);
        }

        public Optional<byte[]> eKeytype() {
            return Optional.ofNullable(eKeytype);
        }

        public Optional<byte[]> eIdent() {
            return Optional.ofNullable(eIdent);
        }

        public Optional<byte[]> eSkeyId() {
            return Optional.ofNullable(eSkeyId);
        }

        public Optional<byte[]> eSkeyVal() {
            return Optional.ofNullable(eSkeyVal);
        }

        public Optional<byte[]> eSkeySig() {
            return Optional.ofNullable(eSkeySig);
        }

        public Optional<byte[]> buildHash() {
            return Optional.ofNullable(buildHash);
        }

        public Optional<byte[]> deviceProps() {
            return Optional.ofNullable(deviceProps);
        }

        public DevicePairingRegistrationData setERegid(byte[] eRegid) {
            this.eRegid = eRegid;
            return this;
        }

        public DevicePairingRegistrationData setEKeytype(byte[] eKeytype) {
            this.eKeytype = eKeytype;
            return this;
        }

        public DevicePairingRegistrationData setEIdent(byte[] eIdent) {
            this.eIdent = eIdent;
            return this;
        }

        public DevicePairingRegistrationData setESkeyId(byte[] eSkeyId) {
            this.eSkeyId = eSkeyId;
            return this;
        }

        public DevicePairingRegistrationData setESkeyVal(byte[] eSkeyVal) {
            this.eSkeyVal = eSkeyVal;
            return this;
        }

        public DevicePairingRegistrationData setESkeySig(byte[] eSkeySig) {
            this.eSkeySig = eSkeySig;
            return this;
        }

        public DevicePairingRegistrationData setBuildHash(byte[] buildHash) {
            this.buildHash = buildHash;
            return this;
        }

        public DevicePairingRegistrationData setDeviceProps(byte[] deviceProps) {
            this.deviceProps = deviceProps;
            return this;
        }
    }

    @ProtobufMessage(name = "ClientPayload.InteropData")
    public static final class InteropData {
        @ProtobufProperty(index = 1, type = ProtobufType.UINT64)
        Long accountId;

        @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
        byte[] token;

        @ProtobufProperty(index = 3, type = ProtobufType.BOOL)
        Boolean enableReadReceipts;


        InteropData(Long accountId, byte[] token, Boolean enableReadReceipts) {
            this.accountId = accountId;
            this.token = token;
            this.enableReadReceipts = enableReadReceipts;
        }

        public OptionalLong accountId() {
            return accountId == null ? OptionalLong.empty() : OptionalLong.of(accountId);
        }

        public Optional<byte[]> token() {
            return Optional.ofNullable(token);
        }

        public boolean enableReadReceipts() {
            return enableReadReceipts != null && enableReadReceipts;
        }

        public InteropData setAccountId(Long accountId) {
            this.accountId = accountId;
            return this;
        }

        public InteropData setToken(byte[] token) {
            this.token = token;
            return this;
        }

        public InteropData setEnableReadReceipts(Boolean enableReadReceipts) {
            this.enableReadReceipts = enableReadReceipts;
            return this;
        }
    }

    @ProtobufMessage(name = "ClientPayload.UserAgent")
    public static final class UserAgent {
        @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
        UserAgent.Platform platform;

        @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
        UserAgent.AppVersion appVersion;

        @ProtobufProperty(index = 3, type = ProtobufType.STRING)
        String mcc;

        @ProtobufProperty(index = 4, type = ProtobufType.STRING)
        String mnc;

        @ProtobufProperty(index = 5, type = ProtobufType.STRING)
        String osVersion;

        @ProtobufProperty(index = 6, type = ProtobufType.STRING)
        String manufacturer;

        @ProtobufProperty(index = 7, type = ProtobufType.STRING)
        String device;

        @ProtobufProperty(index = 8, type = ProtobufType.STRING)
        String osBuildNumber;

        @ProtobufProperty(index = 9, type = ProtobufType.STRING)
        String phoneId;

        @ProtobufProperty(index = 10, type = ProtobufType.ENUM)
        UserAgent.ReleaseChannel releaseChannel;

        @ProtobufProperty(index = 11, type = ProtobufType.STRING)
        String localeLanguageIso6391;

        @ProtobufProperty(index = 12, type = ProtobufType.STRING)
        String localeCountryIso31661Alpha2;

        @ProtobufProperty(index = 13, type = ProtobufType.STRING)
        String deviceBoard;

        @ProtobufProperty(index = 14, type = ProtobufType.STRING)
        String deviceExpId;

        @ProtobufProperty(index = 15, type = ProtobufType.ENUM)
        UserAgent.DeviceType deviceType;

        @ProtobufProperty(index = 16, type = ProtobufType.STRING)
        String deviceModelType;


        UserAgent(Platform platform, AppVersion appVersion, String mcc, String mnc, String osVersion, String manufacturer, String device, String osBuildNumber, String phoneId, ReleaseChannel releaseChannel, String localeLanguageIso6391, String localeCountryIso31661Alpha2, String deviceBoard, String deviceExpId, DeviceType deviceType, String deviceModelType) {
            this.platform = platform;
            this.appVersion = appVersion;
            this.mcc = mcc;
            this.mnc = mnc;
            this.osVersion = osVersion;
            this.manufacturer = manufacturer;
            this.device = device;
            this.osBuildNumber = osBuildNumber;
            this.phoneId = phoneId;
            this.releaseChannel = releaseChannel;
            this.localeLanguageIso6391 = localeLanguageIso6391;
            this.localeCountryIso31661Alpha2 = localeCountryIso31661Alpha2;
            this.deviceBoard = deviceBoard;
            this.deviceExpId = deviceExpId;
            this.deviceType = deviceType;
            this.deviceModelType = deviceModelType;
        }

        public Optional<Platform> platform() {
            return Optional.ofNullable(platform);
        }

        public Optional<AppVersion> appVersion() {
            return Optional.ofNullable(appVersion);
        }

        public Optional<String> mcc() {
            return Optional.ofNullable(mcc);
        }

        public Optional<String> mnc() {
            return Optional.ofNullable(mnc);
        }

        public Optional<String> osVersion() {
            return Optional.ofNullable(osVersion);
        }

        public Optional<String> manufacturer() {
            return Optional.ofNullable(manufacturer);
        }

        public Optional<String> device() {
            return Optional.ofNullable(device);
        }

        public Optional<String> osBuildNumber() {
            return Optional.ofNullable(osBuildNumber);
        }

        public Optional<String> phoneId() {
            return Optional.ofNullable(phoneId);
        }

        public Optional<ReleaseChannel> releaseChannel() {
            return Optional.ofNullable(releaseChannel);
        }

        public Optional<String> localeLanguageIso6391() {
            return Optional.ofNullable(localeLanguageIso6391);
        }

        public Optional<String> localeCountryIso31661Alpha2() {
            return Optional.ofNullable(localeCountryIso31661Alpha2);
        }

        public Optional<String> deviceBoard() {
            return Optional.ofNullable(deviceBoard);
        }

        public Optional<String> deviceExpId() {
            return Optional.ofNullable(deviceExpId);
        }

        public Optional<DeviceType> deviceType() {
            return Optional.ofNullable(deviceType);
        }

        public Optional<String> deviceModelType() {
            return Optional.ofNullable(deviceModelType);
        }

        public UserAgent setPlatform(Platform platform) {
            this.platform = platform;
            return this;
        }

        public UserAgent setAppVersion(AppVersion appVersion) {
            this.appVersion = appVersion;
            return this;
        }

        public UserAgent setMcc(String mcc) {
            this.mcc = mcc;
            return this;
        }

        public UserAgent setMnc(String mnc) {
            this.mnc = mnc;
            return this;
        }

        public UserAgent setOsVersion(String osVersion) {
            this.osVersion = osVersion;
            return this;
        }

        public UserAgent setManufacturer(String manufacturer) {
            this.manufacturer = manufacturer;
            return this;
        }

        public UserAgent setDevice(String device) {
            this.device = device;
            return this;
        }

        public UserAgent setOsBuildNumber(String osBuildNumber) {
            this.osBuildNumber = osBuildNumber;
            return this;
        }

        public UserAgent setPhoneId(String phoneId) {
            this.phoneId = phoneId;
            return this;
        }

        public UserAgent setReleaseChannel(ReleaseChannel releaseChannel) {
            this.releaseChannel = releaseChannel;
            return this;
        }

        public UserAgent setLocaleLanguageIso6391(String localeLanguageIso6391) {
            this.localeLanguageIso6391 = localeLanguageIso6391;
            return this;
        }

        public UserAgent setLocaleCountryIso31661Alpha2(String localeCountryIso31661Alpha2) {
            this.localeCountryIso31661Alpha2 = localeCountryIso31661Alpha2;
            return this;
        }

        public UserAgent setDeviceBoard(String deviceBoard) {
            this.deviceBoard = deviceBoard;
            return this;
        }

        public UserAgent setDeviceExpId(String deviceExpId) {
            this.deviceExpId = deviceExpId;
            return this;
        }

        public UserAgent setDeviceType(DeviceType deviceType) {
            this.deviceType = deviceType;
            return this;
        }

        public UserAgent setDeviceModelType(String deviceModelType) {
            this.deviceModelType = deviceModelType;
            return this;
        }

        @ProtobufEnum(name = "ClientPayload.UserAgent.DeviceType")
        public static enum DeviceType {
            PHONE(0),
            TABLET(1),
            DESKTOP(2),
            WEARABLE(3),
            VR(4);

            DeviceType(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            final int index;

            public int index() {
                return this.index;
            }
        }

        @ProtobufEnum(name = "ClientPayload.UserAgent.Platform")
        public static enum Platform {
            ANDROID(0),
            IOS(1),
            WINDOWS_PHONE(2),
            BLACKBERRY(3),
            BLACKBERRYX(4),
            S40(5),
            S60(6),
            PYTHON_CLIENT(7),
            TIZEN(8),
            ENTERPRISE(9),
            SMB_ANDROID(10),
            KAIOS(11),
            SMB_IOS(12),
            WINDOWS(13),
            WEB(14),
            PORTAL(15),
            GREEN_ANDROID(16),
            GREEN_IPHONE(17),
            BLUE_ANDROID(18),
            BLUE_IPHONE(19),
            FBLITE_ANDROID(20),
            MLITE_ANDROID(21),
            IGLITE_ANDROID(22),
            PAGE(23),
            MACOS(24),
            OCULUS_MSG(25),
            OCULUS_CALL(26),
            MILAN(27),
            CAPI(28),
            WEAROS(29),
            ARDEVICE(30),
            VRDEVICE(31),
            BLUE_WEB(32),
            IPAD(33),
            TEST(34),
            SMART_GLASSES(35),
            BLUE_VR(36),
            AR_WRIST(37);

            Platform(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            final int index;

            public int index() {
                return this.index;
            }
        }

        @ProtobufEnum(name = "ClientPayload.UserAgent.ReleaseChannel")
        public static enum ReleaseChannel {
            RELEASE(0),
            BETA(1),
            ALPHA(2),
            DEBUG(3);

            ReleaseChannel(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            final int index;

            public int index() {
                return this.index;
            }
        }

        @ProtobufMessage(name = "ClientPayload.UserAgent.AppVersion")
        public static final class AppVersion {
            @ProtobufProperty(index = 1, type = ProtobufType.UINT32)
            Integer primary;

            @ProtobufProperty(index = 2, type = ProtobufType.UINT32)
            Integer secondary;

            @ProtobufProperty(index = 3, type = ProtobufType.UINT32)
            Integer tertiary;

            @ProtobufProperty(index = 4, type = ProtobufType.UINT32)
            Integer quaternary;

            @ProtobufProperty(index = 5, type = ProtobufType.UINT32)
            Integer quinary;


            AppVersion(Integer primary, Integer secondary, Integer tertiary, Integer quaternary, Integer quinary) {
                this.primary = primary;
                this.secondary = secondary;
                this.tertiary = tertiary;
                this.quaternary = quaternary;
                this.quinary = quinary;
            }

            public OptionalInt primary() {
                return primary == null ? OptionalInt.empty() : OptionalInt.of(primary);
            }

            public OptionalInt secondary() {
                return secondary == null ? OptionalInt.empty() : OptionalInt.of(secondary);
            }

            public OptionalInt tertiary() {
                return tertiary == null ? OptionalInt.empty() : OptionalInt.of(tertiary);
            }

            public OptionalInt quaternary() {
                return quaternary == null ? OptionalInt.empty() : OptionalInt.of(quaternary);
            }

            public OptionalInt quinary() {
                return quinary == null ? OptionalInt.empty() : OptionalInt.of(quinary);
            }

            public AppVersion setPrimary(Integer primary) {
                this.primary = primary;
                return this;
            }

            public AppVersion setSecondary(Integer secondary) {
                this.secondary = secondary;
                return this;
            }

            public AppVersion setTertiary(Integer tertiary) {
                this.tertiary = tertiary;
                return this;
            }

            public AppVersion setQuaternary(Integer quaternary) {
                this.quaternary = quaternary;
                return this;
            }

            public AppVersion setQuinary(Integer quinary) {
                this.quinary = quinary;
                return this;
            }
        }
    }

    @ProtobufMessage(name = "ClientPayload.WebInfo")
    public static final class WebInfo {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String refToken;

        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String version;

        @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
        WebInfo.WebdPayload webdPayload;

        @ProtobufProperty(index = 4, type = ProtobufType.ENUM)
        WebInfo.WebSubPlatform webSubPlatform;

        @ProtobufProperty(index = 5, type = ProtobufType.STRING)
        String browser;

        @ProtobufProperty(index = 6, type = ProtobufType.STRING)
        String browserVersion;


        WebInfo(String refToken, String version, WebdPayload webdPayload, WebSubPlatform webSubPlatform, String browser, String browserVersion) {
            this.refToken = refToken;
            this.version = version;
            this.webdPayload = webdPayload;
            this.webSubPlatform = webSubPlatform;
            this.browser = browser;
            this.browserVersion = browserVersion;
        }

        public Optional<String> refToken() {
            return Optional.ofNullable(refToken);
        }

        public Optional<String> version() {
            return Optional.ofNullable(version);
        }

        public Optional<WebdPayload> webdPayload() {
            return Optional.ofNullable(webdPayload);
        }

        public Optional<WebSubPlatform> webSubPlatform() {
            return Optional.ofNullable(webSubPlatform);
        }

        public Optional<String> browser() {
            return Optional.ofNullable(browser);
        }

        public Optional<String> browserVersion() {
            return Optional.ofNullable(browserVersion);
        }

        public WebInfo setRefToken(String refToken) {
            this.refToken = refToken;
            return this;
        }

        public WebInfo setVersion(String version) {
            this.version = version;
            return this;
        }

        public WebInfo setWebdPayload(WebdPayload webdPayload) {
            this.webdPayload = webdPayload;
            return this;
        }

        public WebInfo setWebSubPlatform(WebSubPlatform webSubPlatform) {
            this.webSubPlatform = webSubPlatform;
            return this;
        }

        public WebInfo setBrowser(String browser) {
            this.browser = browser;
            return this;
        }

        public WebInfo setBrowserVersion(String browserVersion) {
            this.browserVersion = browserVersion;
            return this;
        }

        @ProtobufEnum(name = "ClientPayload.WebInfo.WebSubPlatform")
        public static enum WebSubPlatform {
            WEB_BROWSER(0),
            APP_STORE(1),
            WIN_STORE(2),
            DARWIN(3),
            WIN32(4),
            WIN_HYBRID(5);

            WebSubPlatform(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            final int index;

            public int index() {
                return this.index;
            }
        }

        @ProtobufMessage(name = "ClientPayload.WebInfo.WebdPayload")
        public static final class WebdPayload {
            @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
            Boolean usesParticipantInKey;

            @ProtobufProperty(index = 2, type = ProtobufType.BOOL)
            Boolean supportsStarredMessages;

            @ProtobufProperty(index = 3, type = ProtobufType.BOOL)
            Boolean supportsDocumentMessages;

            @ProtobufProperty(index = 4, type = ProtobufType.BOOL)
            Boolean supportsUrlMessages;

            @ProtobufProperty(index = 5, type = ProtobufType.BOOL)
            Boolean supportsMediaRetry;

            @ProtobufProperty(index = 6, type = ProtobufType.BOOL)
            Boolean supportsE2EImage;

            @ProtobufProperty(index = 7, type = ProtobufType.BOOL)
            Boolean supportsE2EVideo;

            @ProtobufProperty(index = 8, type = ProtobufType.BOOL)
            Boolean supportsE2EAudio;

            @ProtobufProperty(index = 9, type = ProtobufType.BOOL)
            Boolean supportsE2EDocument;

            @ProtobufProperty(index = 10, type = ProtobufType.STRING)
            String documentTypes;

            @ProtobufProperty(index = 11, type = ProtobufType.BYTES)
            byte[] features;


            WebdPayload(Boolean usesParticipantInKey, Boolean supportsStarredMessages, Boolean supportsDocumentMessages, Boolean supportsUrlMessages, Boolean supportsMediaRetry, Boolean supportsE2EImage, Boolean supportsE2EVideo, Boolean supportsE2EAudio, Boolean supportsE2EDocument, String documentTypes, byte[] features) {
                this.usesParticipantInKey = usesParticipantInKey;
                this.supportsStarredMessages = supportsStarredMessages;
                this.supportsDocumentMessages = supportsDocumentMessages;
                this.supportsUrlMessages = supportsUrlMessages;
                this.supportsMediaRetry = supportsMediaRetry;
                this.supportsE2EImage = supportsE2EImage;
                this.supportsE2EVideo = supportsE2EVideo;
                this.supportsE2EAudio = supportsE2EAudio;
                this.supportsE2EDocument = supportsE2EDocument;
                this.documentTypes = documentTypes;
                this.features = features;
            }

            public boolean usesParticipantInKey() {
                return usesParticipantInKey != null && usesParticipantInKey;
            }

            public boolean supportsStarredMessages() {
                return supportsStarredMessages != null && supportsStarredMessages;
            }

            public boolean supportsDocumentMessages() {
                return supportsDocumentMessages != null && supportsDocumentMessages;
            }

            public boolean supportsUrlMessages() {
                return supportsUrlMessages != null && supportsUrlMessages;
            }

            public boolean supportsMediaRetry() {
                return supportsMediaRetry != null && supportsMediaRetry;
            }

            public boolean supportsE2EImage() {
                return supportsE2EImage != null && supportsE2EImage;
            }

            public boolean supportsE2EVideo() {
                return supportsE2EVideo != null && supportsE2EVideo;
            }

            public boolean supportsE2EAudio() {
                return supportsE2EAudio != null && supportsE2EAudio;
            }

            public boolean supportsE2EDocument() {
                return supportsE2EDocument != null && supportsE2EDocument;
            }

            public Optional<String> documentTypes() {
                return Optional.ofNullable(documentTypes);
            }

            public Optional<byte[]> features() {
                return Optional.ofNullable(features);
            }

            public WebdPayload setUsesParticipantInKey(Boolean usesParticipantInKey) {
                this.usesParticipantInKey = usesParticipantInKey;
                return this;
            }

            public WebdPayload setSupportsStarredMessages(Boolean supportsStarredMessages) {
                this.supportsStarredMessages = supportsStarredMessages;
                return this;
            }

            public WebdPayload setSupportsDocumentMessages(Boolean supportsDocumentMessages) {
                this.supportsDocumentMessages = supportsDocumentMessages;
                return this;
            }

            public WebdPayload setSupportsUrlMessages(Boolean supportsUrlMessages) {
                this.supportsUrlMessages = supportsUrlMessages;
                return this;
            }

            public WebdPayload setSupportsMediaRetry(Boolean supportsMediaRetry) {
                this.supportsMediaRetry = supportsMediaRetry;
                return this;
            }

            public WebdPayload setSupportsE2EImage(Boolean supportsE2EImage) {
                this.supportsE2EImage = supportsE2EImage;
                return this;
            }

            public WebdPayload setSupportsE2EVideo(Boolean supportsE2EVideo) {
                this.supportsE2EVideo = supportsE2EVideo;
                return this;
            }

            public WebdPayload setSupportsE2EAudio(Boolean supportsE2EAudio) {
                this.supportsE2EAudio = supportsE2EAudio;
                return this;
            }

            public WebdPayload setSupportsE2EDocument(Boolean supportsE2EDocument) {
                this.supportsE2EDocument = supportsE2EDocument;
                return this;
            }

            public WebdPayload setDocumentTypes(String documentTypes) {
                this.documentTypes = documentTypes;
                return this;
            }

            public WebdPayload setFeatures(byte[] features) {
                this.features = features;
                return this;
            }
        }
    }
}
