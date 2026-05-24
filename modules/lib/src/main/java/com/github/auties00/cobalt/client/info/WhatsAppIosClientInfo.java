package com.github.auties00.cobalt.client.info;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.model.device.pairing.ClientAppVersion;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * {@link WhatsAppMobileClientInfo} flavour for the consumer ({@code net.whatsapp.WhatsApp}) and business
 * ({@code net.whatsapp.WhatsAppSMB}) iOS WhatsApp bundles.
 *
 * @apiNote Selected automatically by {@link WhatsAppMobileClientInfo#of(com.github.auties00.cobalt.model.device.pairing.ClientPlatformType)}
 *          for {@code IOS} and {@code IOS_BUSINESS}; embedders can also reach a flavour directly through
 *          {@link #ofPersonal()} or {@link #ofBusiness()}. Resolution requires a single call to the public Apple App Store
 *          {@code itunes.apple.com/lookup?bundleId=...} endpoint to read the published version string; no signed binary is
 *          ever downloaded because the static secrets the iOS registration scheme needs are embedded in this class.
 * @implNote This implementation has no WA Web counterpart; the iOS registration token scheme is reverse engineered from the
 *           iOS WhatsApp IPA. The token algorithm is much simpler than the Android counterpart in
 *           {@link WhatsAppAndroidClientInfo}: a single MD5 over a static 40 character secret plus the build hash plus the
 *           phone number, with no per request signing material.
 * @see WhatsAppMobileClientInfo
 */
final class WhatsAppIosClientInfo implements WhatsAppMobileClientInfo {
    /**
     * App Store lookup URL that returns JSON metadata for the consumer WhatsApp bundle.
     *
     * @apiNote Used by {@link #queryIpaInfo(boolean)} when {@code business} is {@code false}.
     */
    private static final URI MOBILE_PERSONAL_IOS_URL = URI.create("https://itunes.apple.com/lookup?bundleId=net.whatsapp.WhatsApp");

    /**
     * App Store lookup URL that returns JSON metadata for the business WhatsApp bundle.
     *
     * @apiNote Used by {@link #queryIpaInfo(boolean)} when {@code business} is {@code true}.
     */
    private static final URI MOBILE_BUSINESS_IOS_URL = URI.create("https://itunes.apple.com/lookup?bundleId=net.whatsapp.WhatsAppSMB");

    /**
     * User-Agent header sent when calling the App Store lookup API.
     *
     * @apiNote Mimics a recent mobile Safari on an iPhone so the lookup endpoint returns metadata identical to what a real
     *          device would see; some catalog responses vary by User-Agent.
     */
    private static final String MOBILE_IOS_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_3_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.3.1 Mobile/15E148 Safari/604.1";

    /**
     * Cached singleton for the consumer iOS flavour.
     *
     * @apiNote Populated lazily by the first call to {@link #ofPersonal()}.
     * @implNote This implementation pairs the field with {@link #personalIpaInfoLock} for the double checked locking idiom;
     *           the {@code volatile} keyword publishes a fully constructed instance to readers on the unsynchronised fast
     *           path.
     */
    private static volatile WhatsAppIosClientInfo personalIpaInfo;

    /**
     * Monitor that serialises initialisation of {@link #personalIpaInfo}.
     *
     * @apiNote Not exposed; callers go through {@link #ofPersonal()}.
     */
    private static final Object personalIpaInfoLock = new Object();

    /**
     * Cached singleton for the business iOS flavour.
     *
     * @apiNote Populated lazily by the first call to {@link #ofBusiness()}.
     * @implNote This implementation pairs the field with {@link #businessIpaInfoLock} for the double checked locking idiom;
     *           the {@code volatile} keyword publishes a fully constructed instance to readers on the unsynchronised fast
     *           path.
     */
    private static volatile WhatsAppIosClientInfo businessIpaInfo;

    /**
     * Monitor that serialises initialisation of {@link #businessIpaInfo}.
     *
     * @apiNote Not exposed; callers go through {@link #ofBusiness()}.
     */
    private static final Object businessIpaInfoLock = new Object();

    /**
     * Static 40 character secret prefix used by the consumer iOS registration token algorithm.
     *
     * @apiNote Reverse engineered from the consumer iOS WhatsApp IPA; rotation requires a binary release on Apple's side.
     */
    private static final String MOBILE_IOS_STATIC = "0a1mLfGUIBVrMKF1RdvLI5lkRBvof6vn0fD2QRSM";

    /**
     * Static 40 character secret prefix used by the business iOS registration token algorithm.
     *
     * @apiNote Reverse engineered from the business iOS WhatsApp IPA; differs from {@link #MOBILE_IOS_STATIC} so consumer
     *          and business builds cannot impersonate each other.
     */
    private static final String MOBILE_BUSINESS_IOS_STATIC = "USUDuDYDeQhY4RF2fCSp5m3F6kJ1M2J8wS7bbNA2";

    /**
     * Resolved {@link ClientAppVersion} returned by the App Store lookup, normalised to the {@code 2.X.Y} form.
     *
     * @apiNote Returned verbatim from {@link #version()}.
     */
    private final ClientAppVersion version;

    /**
     * Whether this instance represents the WhatsApp Business IPA rather than the consumer IPA.
     *
     * @apiNote Returned verbatim from {@link #business()}.
     */
    private final boolean business;

    /**
     * Constructs an immutable instance from the App Store lookup result.
     *
     * @apiNote Package private; callers always go through {@link #ofPersonal()} or {@link #ofBusiness()}.
     * @param version  the parsed application version
     * @param business whether this represents the business flavour
     */
    private WhatsAppIosClientInfo(ClientAppVersion version, boolean business) {
        this.version = version;
        this.business = business;
    }

    /**
     * Returns the cached consumer iOS identity, performing the App Store lookup on the first call.
     *
     * @apiNote Subsequent calls in the same JVM return the same instance. A failed lookup is not cached, so callers may
     *          retry by simply calling this method again.
     * @implNote This implementation uses double checked locking; the {@code volatile} {@link #personalIpaInfo} field
     *           publishes the fully constructed instance to readers on the unsynchronised fast path.
     * @return the consumer iOS client identity
     * @throws RuntimeException if the App Store lookup fails
     */
    public static WhatsAppIosClientInfo ofPersonal() {
        if (personalIpaInfo == null) {
            synchronized (personalIpaInfoLock) {
                if(personalIpaInfo == null) {
                    personalIpaInfo = queryIpaInfo(false);
                }
            }
        }
        return personalIpaInfo;
    }

    /**
     * Returns the cached business iOS identity, performing the App Store lookup on the first call.
     *
     * @apiNote Subsequent calls in the same JVM return the same instance. A failed lookup is not cached, so callers may
     *          retry by simply calling this method again.
     * @implNote This implementation uses double checked locking; the {@code volatile} {@link #businessIpaInfo} field
     *           publishes the fully constructed instance to readers on the unsynchronised fast path.
     * @return the business iOS client identity
     * @throws RuntimeException if the App Store lookup fails
     */
    public static WhatsAppIosClientInfo ofBusiness() {
        if (businessIpaInfo == null) {
            synchronized (businessIpaInfoLock) {
                if(businessIpaInfo == null) {
                    businessIpaInfo = queryIpaInfo(true);
                }
            }
        }
        return businessIpaInfo;
    }

    /**
     * Calls the App Store lookup API for the requested bundle and parses the returned version string into a
     * {@link ClientAppVersion}.
     *
     * @apiNote Called at most once per JVM per flavour by {@link #ofPersonal()} or {@link #ofBusiness()}. Returns
     *          {@code null} when the response is empty or missing the {@code version} field so the calling accessor leaves
     *          the singleton unpopulated and the next call retries.
     * @implNote This implementation prepends {@code "2."} to App Store versions that lack the leading {@code "2."} prefix
     *           because the iOS marketing version is sometimes published as {@code "23.X.Y"} ({@code 23.X.Y} year based)
     *           while WhatsApp's wire scheme expects the canonical {@code "2.X.Y"} form.
     * @param business {@code true} for the business flavour, {@code false} for the consumer flavour
     * @return a populated {@link WhatsAppIosClientInfo}, or {@code null} if the lookup returned no usable data
     * @throws RuntimeException if the HTTP exchange fails
     */
    private static WhatsAppIosClientInfo queryIpaInfo(boolean business) {
        try(var httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build()) {
            var request = HttpRequest.newBuilder()
                    .uri(business ? MOBILE_BUSINESS_IOS_URL : MOBILE_PERSONAL_IOS_URL)
                    .header("User-Agent", MOBILE_IOS_USER_AGENT)
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP request failed with status code: " + response.statusCode());
            }

            var jsonObject = JSON.parseObject(response.body());
            var results = jsonObject.getJSONArray("results");
            if (results == null || results.isEmpty()) {
                return null;
            }

            var result = results.getJSONObject(0);
            var version = result.getString("version");
            if (version == null) {
                return null;
            }

            if (!version.startsWith("2.")) {
                version = "2." + version;
            }

            var parsedVersion = ClientAppVersion.of(version);
            return new WhatsAppIosClientInfo(parsedVersion, business);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Cannot query iOS version", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote The version is the {@code version} field returned by the Apple App Store lookup endpoint, normalised to the
     *          canonical {@code 2.X.Y} form WhatsApp servers expect.
     */
    @Override
    public ClientAppVersion version() {
        return version;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote Determined by which App Store bundle the lookup queried
     *          ({@link #MOBILE_PERSONAL_IOS_URL} versus {@link #MOBILE_BUSINESS_IOS_URL}).
     */
    @Override
    public boolean business() {
        return business;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation MD5s the lower case hex concatenation of the flavour specific static secret
     *           ({@link #MOBILE_IOS_STATIC} or {@link #MOBILE_BUSINESS_IOS_STATIC}), the hex encoded build hash from
     *           {@link ClientAppVersion#toHash()}, and the decimal national phone number; no signed binary key material is
     *           involved on iOS, which is why no IPA download is needed.
     * @throws UnsupportedOperationException if MD5 is not available on the running JDK
     */
    @Override
    public String computeRegistrationToken(long nationalPhoneNumber) {
        try {
            var staticToken = business ? MOBILE_BUSINESS_IOS_STATIC : MOBILE_IOS_STATIC;
            var token = staticToken + HexFormat.of().formatHex(version.toHash()) + nationalPhoneNumber;
            var digest = MessageDigest.getInstance("MD5");
            digest.update(token.getBytes());
            var result = digest.digest();
            return HexFormat.of().formatHex(result);
        } catch (NoSuchAlgorithmException exception) {
            throw new UnsupportedOperationException("Missing md5 implementation", exception);
        }
    }
}
