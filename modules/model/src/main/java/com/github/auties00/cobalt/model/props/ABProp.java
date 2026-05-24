package com.github.auties00.cobalt.model.props;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Represents an A/B testing property (AB prop) definition with its configuration code and default values.
 *
 * <p>AB props are feature flags and configuration values that WhatsApp uses to control client behavior,
 * enable/disable features, and conduct A/B testing experiments. Each prop definition consists of:
 * <ul>
 * <li>A numeric {@code code} that uniquely identifies the property
 * <li>A {@code defaultValue} string used when the server has not sent a value for this prop
 * <li>A {@code debugDefaultValue} string used in place of {@code defaultValue} when the user
 *     has joined the WhatsApp Web Beta programme
 * </ul>
 *
 * <p>Both default values are always strings, matching the format in which values are received from
 * the server. Static conversion methods are provided to parse the string into typed values
 * (boolean, int, long, double).
 *
 * <p>This record is immutable and thread-safe.
 *
 * @param code              the unique numeric identifier for this configuration property
 * @param defaultValue      the production default value to use when the server has not provided
 *                          a value for this property, must not be {@code null}
 * @param debugDefaultValue the debug/beta default value used when the user has joined the
 *                          WhatsApp Web Beta programme, must not be {@code null}
 */
public record ABProp(int code, String defaultValue, String debugDefaultValue) {

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp STATUS_VIDEO_MAX_DURATION = new ABProp(175, "30", "30");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp UPLOAD_DOCUMENT_THUMB_MMS_ENABLED = new ABProp(247, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DOWNLOAD_STATUS_THUMB_MMS_ENABLED = new ABProp(249, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DOWNLOAD_DOCUMENT_THUMB_MMS_ENABLED = new ABProp(250, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MD_ICDC_HASH_LENGTH = new ABProp(310, "10", "10");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_COLLECTIONS_ENABLED = new ABProp(451, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DISAPPEARING_MODE = new ABProp(536, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_CATCH_UP = new ABProp(559, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ABPROP_DROP_FULL_HISTORY_SYNC = new ABProp(600, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DROP_LAST_NAME = new ABProp(726, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp NUM_DAYS_KEY_INDEX_LIST_EXPIRATION = new ABProp(730, "35", "35");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp NUM_DAYS_BEFORE_DEVICE_EXPIRY_CHECK = new ABProp(731, "7", "7");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ABPROP_COLLECTIONS_NUX_BANNER = new ABProp(741, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ADV_V2_M4_M5 = new ABProp(753, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ABPROP_BUSINESS_PROFILE_REFRESH_LINKED_ACCOUNT_ENABLED = new ABProp(764, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp TOS_3_CLIENT_GATING_ENABLED = new ABProp(791, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp TCTOKEN_DURATION = new ABProp(865, "604800", "604800");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ABPROP_DIRECT_CONNECTION_MD = new ABProp(869, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_STATUS_PSA = new ABProp(873, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp TOS_CLIENT_STATE_FETCH_ENABLED = new ABProp(877, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ABPROP_BLOCK_CATALOG_CREATION_ECOMMERCE_COMPLIANCE_INDIA = new ABProp(894, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp TOS_CLIENT_STATE_FETCH_ITERATION = new ABProp(908, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp TCTOKEN_NUM_BUCKETS = new ABProp(909, "4", "4");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BANNED_SHOPS_UX_ENABLED = new ABProp(957, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_TOS_FILTERING_ENABLED = new ABProp(976, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PARENT_GROUP_VIEW_ENABLED = new ABProp(982, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp TCTOKEN_DURATION_SENDER = new ABProp(996, "604800", "604800");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp TCTOKEN_NUM_BUCKETS_SENDER = new ABProp(997, "4", "4");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_ECOMMERCE_COMPLIANCE_INDIA_M4 = new ABProp(1003, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMART_FILTERS_ENABLED = new ABProp(1015, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp IN_APP_SUPPORT_V2_NUMBER_PREFIXES = new ABProp(1031, "15517868", "15517868");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SYSTEM_MSG_NUMBERS_FB_BRANDED = new ABProp(1035, "16325551023,16505434800,16503130062,16507885324,16508620604,16504228206,447710173736,16315551023,16505361212,16508129150,16315555102,16315558723,16505212669,16507885280,19032707825,0", "16325551023,16505434800,16503130062,16507885324,16508620604,16504228206,447710173736,16315551023,16505361212,16508129150,16315555102,16315558723,16505212669,16507885280,19032707825,0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SYSTEM_MSG_NUMBERS_FB_INC = new ABProp(1036, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_SHOP_STOREFRONT_MESSAGE = new ABProp(1053, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DEV_PROP_STRING = new ABProp(1064, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DEV_PROP_BOOLEAN = new ABProp(1065, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DEV_PROP_INT = new ABProp(1066, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DEV_PROP_FLOAT = new ABProp(1067, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_SEND_INVISIBLE_MSG_MIN_GROUP_SIZE = new ABProp(1100, "128", "128");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp LTHASH_CHECK_HOURS = new ABProp(1104, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp COUNTRY_CLIENT_GATING_ENABLED = new ABProp(1105, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ORDER_DETAILS_FROM_CART_ENABLED = new ABProp(1107, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp INTERACTIVE_MESSAGE_NATIVE_FLOW_KILLSWITCH = new ABProp(1133, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MESSAGE_COUNT_LOGGING_MD_ENABLED = new ABProp(1135, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_INIT_CHAT_BATCH_SIZE = new ABProp(1171, "100", "100");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_INIT_CHAT_MAX_UNREAD_MESSAGE_COUNT = new ABProp(1172, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ORDER_DETAILS_CUSTOM_ITEM_ENABLED = new ABProp(1176, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ADMIN_REVOKE_RECEIVER = new ABProp(1177, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ORDER_MANAGEMENT_ENABLED = new ABProp(1188, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp LOG_CLOCK_SKEW = new ABProp(1190, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_ECOMMERCE_COMPLIANCE_INDIA_M4_5 = new ABProp(1192, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_HIDE_UNSUPPORTED_CURRENCY_PRICE = new ABProp(1203, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ORDER_DETAILS_FROM_CATALOG_ENABLED = new ABProp(1212, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AUDIO_LEVEL_SPEAKING_THRESHOLD = new ABProp(1213, "30", "50");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_CATKIT_QUERY_VERSION = new ABProp(1229, "1", "1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PARENT_GROUP_LINK_LIMIT = new ABProp(1238, "100", "100");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMART_FILTERS_ENABLED_CONSUMER = new ABProp(1287, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_SIZE_LIMIT = new ABProp(1304, "257", "257");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp COMMERCE_SANCTIONED = new ABProp(1319, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ABPROP_BUSINESS_PROFILE_REFRESH_LINKED_ACCOUNTS_KILLSWITCH = new ABProp(1351, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MD_APP_STATE_GATE_D34336913 = new ABProp(1379, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SYNCD_PERIODIC_SYNC_DAYS = new ABProp(1400, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp POLL_NAME_LENGTH = new ABProp(1406, "255", "255");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp POLL_OPTION_LENGTH = new ABProp(1407, "100", "100");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp POLL_OPTION_COUNT = new ABProp(1408, "12", "12");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp HEARTBEAT_INTERVAL_S = new ABProp(1430, "10", "5");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp INTERACTIVE_RESPONSE_MESSAGE_KILLSWITCH = new ABProp(1435, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp INTERACTIVE_RESPONSE_MESSAGE_NATIVE_FLOW_KILLSWITCH = new ABProp(1436, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_SYNCD_MAX_MUTATIONS_TO_PROCESS_DURING_RESUME = new ABProp(1513, "1000", "1000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CATALOG_CATEGORIES_ENABLED = new ABProp(1514, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MD_OFFLINE_V2_M2_ENABLED = new ABProp(1517, "10", "10");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp LOBBY_TIMEOUT_MIN = new ABProp(1565, "0", "1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_BILLING_ENABLED = new ABProp(1583, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ORDER_DETAILS_QUICK_PAY = new ABProp(1600, "{\"allowed_product_type\":\"none\"}", "{\"allowed_product_type\":\"none\"}");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp REACTIONS_CHAT_PREVIEW = new ABProp(1605, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHATLIST_FILTERS_V1 = new ABProp(1608, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PARENT_GROUP_ADMINS_LIMIT = new ABProp(1655, "20", "20");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DM_UPDATED_SYSTEM_MESSAGE = new ABProp(1670, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ABPROP_SCREEN_LOCK_ENABLED = new ABProp(1680, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_CTWA_LOG_USER_JOURNEY_ENABLED = new ABProp(1681, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ORDER_DETAILS_TOTAL_MAXIMUM_VALUE = new ABProp(1684, "500000000", "500000000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp KEEP_IN_CHAT_UNDO_DURATION_LIMIT = new ABProp(1698, "2592000", "2592000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ORDER_DETAILS_TOTAL_ORDER_MINIMUM_VALUE = new ABProp(1719, "1", "1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_GROUP_PROFILE_EDITOR = new ABProp(1745, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ABPROP_CORE_WAM_RUNTIME = new ABProp(1753, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_PHASE_OUT_NOT_A_BUSINESS_V2 = new ABProp(1771, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_OFFLINE_RESUME_QPL_ENABLED = new ABProp(1773, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp IS_META_EMPLOYEE_OR_INTERNAL_TESTER = new ABProp(1777, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SYNCD_LTHASH_CONSISTENCY_CHECK_ON_SNAPSHOT_MAC_MISMATCH = new ABProp(1783, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_MD_AGENT_CHAT_ASSIGNMENT_ENABLED = new ABProp(1798, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_SYNCD_FATAL_FIELDS_FROM_L1104589PRV2 = new ABProp(1808, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp REPORT_CALL_REPLAYER_ID = new ABProp(1834, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DISABLE_AUTO_DOWNLOAD = new ABProp(1838, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_DATA_MAX_LENGTH = new ABProp(1841, "768", "768");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DIRECT_CONNECTION_BUSINESS_NUMBERS = new ABProp(1846, "16005554444,918591749310,917977079770", "16005554444,918591749310,917977079770");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_MULTI_SKIN_TONED_EMOJI_PICKER = new ABProp(1850, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp STATUS_REACTION_EMOJIS = new ABProp(1852, "[128525, 128514, 128558, 128546, 128591, 128079, 127881, 128175]", "[128525, 128514, 128558, 128546, 128591, 128079, 127881, 128175]");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_SIZE_BYPASSING_SAMPLING = new ABProp(1861, "100000", "100000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp COMMUNITY_ADMIN_PROMOTION_ONE_TIME_PROMPT = new ABProp(1864, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SHARE_PHONE_NUMBER_ON_CART_SEND_TO_DIRECT_CONNECTION_BIZ_ENABLED = new ABProp(1867, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_MULTI_DEVICE_AGENTS_LOGGING_V2_ENABLED = new ABProp(1897, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_PTT_STREAMER_UPLOAD = new ABProp(1902, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_TEMP_COVER_PHOTO_PRIVACY_MESSAGING = new ABProp(1913, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_SEND_INVISIBLE_MSG_MAX_GROUP_SIZE = new ABProp(1945, "1024", "1024");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_MULTI_DEVICE_MESSAGE_ATTRIBUTION_ENABLED = new ABProp(1981, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PARENT_GROUP_LINK_LIMIT_COMMUNITY_CREATION = new ABProp(1990, "10", "20");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GRAPHQL_LOCALE_REMAPPING = new ABProp(2014, "{}", "{}");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_MESSAGE_LIST_A11Y_REDESIGN = new ABProp(2016, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ENABLE_PROFILE_PIC_THUMB_DB_CACHING = new ABProp(2018, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ENABLE_BIZ_CATALOG_VIEW_PS_LOGGING = new ABProp(2056, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_SUSPEND_APPEAL_INCLUDE_ENTITY_ID_ENABLED = new ABProp(2057, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ABPROP_MEDIA_LINKS_DOCS_SEARCH = new ABProp(2063, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MMS_VCACHE_AGGREGATION_ENABLED = new ABProp(2134, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_LINK_PREVIEW_SYNC_ENABLED = new ABProp(2156, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_CTWA_BILLING_ENABLED = new ABProp(2158, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp VIDEO_STREAM_BUFFERING_UI_ENABLED = new ABProp(2167, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PARENT_GROUP_VIEW_ENABLED_FOR_SMB_ON_WEB = new ABProp(2205, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_MD_AGENT_CHAT_ASSIGNMENT_NUX_IMPRESSIONS = new ABProp(2207, "0", "3");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MEX_PHASE3_ENABLED = new ABProp(2249, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MEX_PHASE3_STATUS_FLAGS = new ABProp(2250, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_MAX_CONTACTS_TO_SHOW_COMMON_GROUPS = new ABProp(2264, "10", "10");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_MAX_FOUND_COMMON_GROUPS_DISPLAYED = new ABProp(2268, "15", "15");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_MESSAGE_CUSTOM_ARIA_LABEL = new ABProp(2280, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PARENT_GROUP_CREATE_PRIVACY = new ABProp(2356, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp FOUR_REACTIONS_IN_BUBBLE_ENABLED = new ABProp(2378, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PARENT_GROUP_MIN_PARTICIPANTS_FOR_GROUP_ENTRY_POINT = new ABProp(2382, "20", "1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_JOIN_REQUEST_M2_BANNER_ON_CONVERSATION = new ABProp(2449, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_NON_BLOCKING_OFFLINE_RESUME_MAX_MESSAGE_COUNT = new ABProp(2508, "1000", "1000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp NEW_END_CALL_SURVEY_POP_UP_USER_INTERVAL_S = new ABProp(2553, "-1", "-1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp OUT_OF_SYNC_DISAPPEARING_MESSAGES_LOGGING = new ABProp(2561, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp LINK_PREVIEW_WAIT_TIME = new ABProp(2566, "7", "7");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_BIZ_PROFILE_CUSTOM_URL = new ABProp(2582, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_INIT_BWE_FOR_GROUP_CALL = new ABProp(2601, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MEDIA_PICKER_SELECT_LIMIT = new ABProp(2614, "30", "30");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_SCREEN_LOCK_MAX_RETRIES = new ABProp(2622, "10", "10");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PLACEHOLDER_MESSAGE_KEY_HASH_LOGGING = new ABProp(2639, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp VID_STREAM_PAUSE_RESUME_JB_RESET_THRESHOLD_MS = new ABProp(2642, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MEDIA_PICKER_SELECT_LIMIT_NEW = new ABProp(2693, "30", "30");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_MD_AGENT_CHAT_ASSIGNMENT_SYSTEM_MESSAGES_LOGGING_V2_ENABLED = new ABProp(2709, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp EPHEMERAL_SYNC_RESPONSE = new ABProp(2714, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp POLL_RECEIVING_CAG_ENABLED = new ABProp(2737, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp POLL_CREATION_CAG_ENABLED = new ABProp(2738, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp COMMUNITY_ANNOUNCEMENT_GROUP_SIZE_LIMIT = new ABProp(2774, "5000", "5000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp FULLSCREEN_ANIMATION_FOR_KEYWORD = new ABProp(2776, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SYNCD_ADDITIONAL_MUTATIONS_COUNT = new ABProp(2777, "1", "1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_MD_AGENT_CHAT_ASSIGNMENT_CHATS_REORDER_ON_CHAT_ASSIGNMENT_ENABLED = new ABProp(2787, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_MD_AGENT_CHAT_ASSIGNMENT_CHATS_REORDER_ON_CHAT_UNASSIGNMENT_ENABLED = new ABProp(2788, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_MESSAGE_PLUGIN_FRONTEND_REGISTRATION_ENABLED = new ABProp(2793, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_SOOX_MESSAGE_SENDING = new ABProp(2832, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SUPPORTS_KEEP_IN_CHAT_IN_CAG = new ABProp(2844, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp UTM_TRACKING_ENABLED = new ABProp(2895, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp UTM_TRACKING_EXPIRATION_HOURS = new ABProp(2896, "24", "24");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_CTWA_WEB_THREAD_AD_ATTRIBUTION_ENABLED = new ABProp(2898, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ELEVATED_PUSH_NAMES_V2_M2_ENABLED = new ABProp(2904, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_MD_AGENT_CHAT_ASSIGNMENT_NOTIFICATIONS_ENABLED = new ABProp(2908, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CALL_ADMIN_VERSION = new ABProp(2912, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MAXIMUM_GROUP_SIZE_FOR_RCAT = new ABProp(2915, "100", "100");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_SMB_DATA_SHARING_CONSENT = new ABProp(2934, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MESSAGE_EDIT_WINDOW_DURATION_SECONDS = new ABProp(2983, "1200", "1200");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp UGC_ENABLED = new ABProp(3011, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_NATIVE_FETCH_MEDIA_DOWNLOAD = new ABProp(3031, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_IMAGE_MAX_EDGE = new ABProp(3042, "1600", "1600");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENTS_LINK_TO_LITE_CONSUMER_ENABLED = new ABProp(3051, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_CTWA_WEB_ENTRYPOINT_HOME_HEADER_ENABLED = new ABProp(3058, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PNH_PN_FOR_LID_CHAT_SYNC = new ABProp(3062, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ORIGINAL_QUALITY_IMAGE_MIN_EDGE = new ABProp(3068, "2560", "2560");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SEND_CAG_MEMBER_REVOKES_AS_GDM = new ABProp(3069, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SHARE_OWN_PN_SYNC = new ABProp(3070, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp EXTERNAL_BETA_CAN_JOIN = new ABProp(3081, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_CTWA_WEB_ENTRYPOINT_HOME_HEADER_DROPDOWN_ENABLED = new ABProp(3095, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MEDIA_LARGE_FILE_AWARENESS_POPUP_FILE_SIZE_IN_MB = new ABProp(3115, "2048", "2048");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_RAMBUTAN_ENABLED = new ABProp(3124, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_STORE_QUOTA_MANAGER_ENABLED = new ABProp(3133, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_BROWSER_QUOTA_THRESHOLD = new ABProp(3134, "100", "100");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_BROWSER_MIN_STORAGE_QUOTA = new ABProp(3135, "5", "5");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ORIGINAL_PHOTO_QUALITY_UPLOAD_ENABLED = new ABProp(3136, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PINNED_MESSAGES_M0 = new ABProp(3138, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PINNED_MESSAGES_M1_RECEIVER = new ABProp(3139, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PINNED_MESSAGES_M1_SENDER = new ABProp(3140, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PINNED_MESSAGES_M2 = new ABProp(3141, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PARENT_GROUP_SUBGROUP_FILTER = new ABProp(3147, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_DEPRECATE_MMS4_HASH_BASED_DOWNLOAD = new ABProp(3152, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_SUSPEND_V2_ENABLED = new ABProp(3180, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_CHAT_PSA_AUTO_PLAY_VIDEOS = new ABProp(3182, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DEFAULT_VIDEO_LIMIT_MB = new ABProp(3185, "16", "64");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_IMAGE_MAX_HD_EDGE = new ABProp(3204, "2560", "2560");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp EXTENSIONS_USER_REPORT_STORE_MAX_DATA_EXCHANGES_PER_SESSION = new ABProp(3211, "10", "10");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp EXTENSIONS_USER_REPORT_STORE_MAX_DATA_MAX_SESSIONS_PER_MESSAGE = new ABProp(3212, "3", "3");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_E2E_BACKFILL_EXPIRE_TIME = new ABProp(3234, "5", "60");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_SILENT_OFFER = new ABProp(3235, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ORDER_MESSAGES_EPHEMERAL_EXCEPTION_ENABLED = new ABProp(3240, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MESSAGE_EDIT_CLIENT_ENTRY_POINT_LIMIT_SECONDS = new ABProp(3272, "900", "900");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SEND_EXTENDED_NACK_ENABLED = new ABProp(3280, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_WALDO_SERVICE_OFFERINGS_SELECTION_ENABLED = new ABProp(3285, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_CTWA_WEB_FETCH_LINKED_ACCOUNTS_ENABLED = new ABProp(3294, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DM_ADDITIONAL_DURATIONS = new ABProp(3305, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_DAYS_SINCE_RECEIVE_LOGGING = new ABProp(3322, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_SMB_DATA_SHARING_OPT_IN_COOL_OFF_PERIOD = new ABProp(3331, "259200", "259200");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp HISTORY_SYNC_ON_DEMAND = new ABProp(3337, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WDS_RADIUS_AND_CASING = new ABProp(3350, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PTV_MAX_DURATION_SECONDS = new ABProp(3356, "60", "60");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CALLING_LID_VERSION = new ABProp(3358, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_CTWA_WEB_ENTRYPOINT_MANAGE_ADS_HOME_HEADER_DROPDOWN_ENABLED = new ABProp(3376, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_JOIN_REQUEST_CAN_VIEW_OPTIONAL_MESSAGE = new ABProp(3383, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_JOIN_REQUEST_CAN_SEND_OPTIONAL_MESSAGE = new ABProp(3384, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_PROJECT_WALDO_SET_PRICE_TIER_BIZ_PROFILE_ENABLED = new ABProp(3467, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PTV_AUTOPLAY_ENABLED = new ABProp(3482, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PTV_AUTOPLAY_LOOP_LIMIT = new ABProp(3483, "3", "3");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp QP_CAMPAIGN_CLIENT_ENABLED = new ABProp(3536, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ANIMATED_EMOJIS_ENABLED = new ABProp(3575, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PLACEHOLDER_MESSAGE_RESEND = new ABProp(3579, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp COUPON_COPY_BUTTON_URL = new ABProp(3631, "https://www.whatsapp.com/coupon?code=", "https://www.whatsapp.com/coupon?code=");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PLACEHOLDER_MESSAGE_RESEND_MAXIMUM_DAYS_LIMIT = new ABProp(3639, "14", "14");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DEFAULT_AUDIO_LIMIT_MB = new ABProp(3657, "16", "64");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DEFAULT_STATUS_MEDIA_LIMIT_MB = new ABProp(3659, "16", "64");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DEFAULT_MEDIA_LIMIT_MB = new ABProp(3660, "16", "64");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SERVICE_IMPROVEMENT_OPT_OUT_FLAG = new ABProp(3664, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GIF_MIN_PLAY_LOOPS = new ABProp(3682, "1", "1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GIF_MAX_PLAY_LOOPS = new ABProp(3683, "3", "3");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GIF_MAX_PLAY_DURATION = new ABProp(3684, "5", "5");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ORDERS_EXPANSION_RECEIVER_COUNTRIES_ALLOWED = new ABProp(3690, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MAX_NUM_PARTICIPANTS_FOR_SS = new ABProp(3694, "8", "8");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp REPORT_TO_ADMIN_KILL_SWITCH = new ABProp(3695, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp REPORT_TO_ADMIN_ENABLED = new ABProp(3696, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp QUICK_PROMOTION_BANNER_CLIENT_ENABLED = new ABProp(3712, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_MESSAGE_PROCESSING_CACHE_SIZE = new ABProp(3728, "400", "400");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PINNED_MESSAGES_M2_PIN_MAX = new ABProp(3732, "1", "1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENTS_MERCHANT_GLOBAL_ORDERS_VALUE_PROPS_BANNER_ENABLED = new ABProp(3744, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp NEWSLETTER_TOS_NOTICE_ID = new ABProp(3810, "20601216", "20601216");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp HISTORY_SYNC_ON_DEMAND_MESSAGE_COUNT = new ABProp(3811, "50", "50");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp UNIFIED_OTP_COPY_CODE_URL = new ABProp(3827, "https://www.whatsapp.com/otp/copy/", "https://www.whatsapp.com/otp/copy/");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp UNIFIED_OTP_RETRIEVER_URL = new ABProp(3828, "https://www.whatsapp.com/otp/code", "https://www.whatsapp.com/otp/code");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp NEWSLETTER_CREATION_TOS_ID = new ABProp(3834, "20601217", "20601217");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp NEWSLETTER_CREATION_NUX_ID = new ABProp(3835, "20601218", "20601218");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp TS_SESSION_DURATION_MS = new ABProp(3860, "600000", "600000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_ENABLED = new ABProp(3877, "0", "2");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_CREATION_ENABLED = new ABProp(3878, "0", "2");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_DIRECTORY_ENABLED = new ABProp(3879, "0", "2");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp HISTORY_SYNC_ON_DEMAND_TIMEOUT_MS = new ABProp(3882, "10000", "10000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNEL_SUPPORTED_MESSAGE_TYPES = new ABProp(3919, "1, 2, 3, 5, 9, 10, 12, 15", "1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PRIVACY_TIPS_GROUPS_BUILD = new ABProp(3995, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PRIVACY_TIPS_PROFILE_BUILD = new ABProp(3998, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BONSAI_ENABLED = new ABProp(4010, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp UGC_PARTICIPANT_LIMIT = new ABProp(4118, "5", "5");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp HISTORY_SYNC_ON_DEMAND_WITH_ANDROID_BETA = new ABProp(4135, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp HD_VIDEO_DEFINITION_MIN_EDGE = new ABProp(4171, "720", "720");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp HD_VIDEO_DEFINITION_MAX_EDGE = new ABProp(4172, "864", "864");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp HD_VIDEO_DEFINITION_MIN_EDGE_WITH_MAX_EDGE = new ABProp(4175, "480", "480");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_CALL_MAX_PARTICIPANTS = new ABProp(4190, "32", "32");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENTS_BR_CONTENT_OPTIMIZATION_VARIANT = new ABProp(4248, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp P2M_EXTERNAL_PAYMENTS_LINK_ENABLED = new ABProp(4295, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNEL_REACTIONS_ENABLED = new ABProp(4306, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp RECOMMENDED_CHANNELS_BACKGROUND_REFRESH = new ABProp(4309, "14400000", "1800000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PRIVACY_TIPS_KILLSWITCH = new ABProp(4314, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNEL_PULL_MESSAGE_UPDATES_THRESHOLD_SECONDS = new ABProp(4326, "120", "120");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_OTP_COPY_CODE_DISABLED = new ABProp(4330, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNEL_FORWARD_TO_CHAT_ENABLED = new ABProp(4338, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_ML_BWE_MODEL_DOWNLOAD = new ABProp(4349, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp HISTORY_SYNC_ON_DEMAND_FAILURE_LIMIT = new ABProp(4364, "10", "10");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp HISTORY_SYNC_ON_DEMAND_COOLDOWN_SEC = new ABProp(4365, "7200", "7200");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp FLATTENED_REACTIONS_COLLECTION = new ABProp(4390, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BONSAI_PTT_ENABLED = new ABProp(4416, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BONSAI_UPDATE_INTERVAL = new ABProp(4417, "86400", "86400");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BUSINESS_TOOL_ENHANCED_LOGGING = new ABProp(4427, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PINNED_MESSAGES_SENDER_SHORT_EXPIRY_DURATIONS_ENABLED = new ABProp(4432, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PNH_CAG_DISABLE_REACTIONS_GROUP_SIZE = new ABProp(4495, "10000", "10000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BONSAI_AVATAR_ENABLED = new ABProp(4532, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp IN_APP_COMMS_MANAGE_ADS_WEB_BANNER_CAMPAIGN_ENABLED = new ABProp(4542, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ADD_MEMBER_SYSTEM_MESSAGE = new ABProp(4579, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_PREMIUM_MESSAGES_INTERACTIVITY_RENDERING_ENABLED = new ABProp(4596, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNEL_VIEWS_DURATION_MILLISECONDS = new ABProp(4648, "250", "250");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_PREMIUM_MESSAGES_CLICK_LOGGING_ENABLED = new ABProp(4657, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_CLEAR_FORMATTED_PREVIEW = new ABProp(4659, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CAROUSEL_MESSAGE_CLIENT_ENABLED = new ABProp(4668, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_INTERNAL_IN_APP_BUG_REPORTING_ENABLE = new ABProp(4681, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNEL_FORWARD_TO_CHAT_V2_MESSAGE_NAVIGATION_ENABLED = new ABProp(4682, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MAX_GROUP_SIZE_FOR_LONG_RINGTONE = new ABProp(4710, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNEL_VIEW_COUNTS_ENABLED = new ABProp(4721, "0", "3");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNEL_PLAYABLE_MESSAGE_VIEWS_DURATION_MILLISECONDS = new ABProp(4722, "3000", "3000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_STICKER_SUGGESTIONS_ENABLE = new ABProp(4726, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BONSAI_TI_TIMEOUT_DURATION_MS = new ABProp(4736, "10000", "10000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USERNAME_CREATION = new ABProp(4745, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USERNAME_CONTACT_DISPLAY = new ABProp(4746, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_SEND_VIEW_RECEIPT_ENABLED = new ABProp(4760, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp IN_APP_SUPPORT_CAPI_NUMBER_PREFIXES = new ABProp(4799, "155178684", "155178684");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp LOW_CACHE_HIT_RATE_MEDIA_TYPES = new ABProp(4836, "ptt,audio,document,ppic", "ptt,audio,document,ppic");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAE_METADATA_INTEGRITY_TIMEOUT_MINUTES = new ABProp(4849, "5", "5");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WABAI_MESSAGE_RENDERING_ENABLED = new ABProp(4873, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNEL_REACTIONS_SETTINGS_ENABLED = new ABProp(4887, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ROW_BUYER_ORDER_REVAMP_M0_ENABLED = new ABProp(4893, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp TS_SURFACE_KILLSWITCH = new ABProp(4929, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BONSAI_WORD_STREAMING_ENABLED = new ABProp(4974, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_SPAM_REPORT_IQ_WITH_PRIVACY_TOKEN = new ABProp(4991, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_PRIVACY_TOKEN_WITH_TIMESTAMP = new ABProp(4992, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_LABELS_CTWA_DATA_SHARING = new ABProp(5009, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_FILTER_OUT_SUBSCRIBED_IN_DIRECTORY_NULL_STATE = new ABProp(5015, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp COMMUNITY_GENERAL_CHAT_UI_ENABLED = new ABProp(5021, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_PREMIUM_MESSAGES_URL_CTA_ALERT_DIALOG_ENABLED = new ABProp(5044, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PNH_CAG_DISABLE_POLLS_GROUP_SIZE = new ABProp(5056, "10000", "10000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PARENT_GROUP_ALLOW_MEMBER_SUGGEST_EXISTING_M3_SENDER = new ABProp(5077, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PARENT_GROUP_ALLOW_MEMBER_SUGGEST_EXISTING_M3_RECEIVER = new ABProp(5078, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_PRELOAD_CHAT_MESSAGES = new ABProp(5079, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_NONCRITICAL_HISTORY_SYNC_MESSAGE_PROCESSING_BREAK_ITERATION = new ABProp(5106, "100", "100");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_TC_TOKEN_DB_READ_ENABLED = new ABProp(5110, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BUYER_INITIATED_ORDER_REQUEST_VARIANT_ENABLED = new ABProp(5114, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_DIRECTORY_V2_FILTER_TYPES = new ABProp(5127, "", "1, 2, 3, 4, 5, 6");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp INBOX_FILTERS_ENABLED = new ABProp(5171, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNEL_REACTIONS_SENDER_LIST_ENABLED = new ABProp(5185, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SELLER_ORDERS_MANAGEMENT_REVAMP = new ABProp(5190, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_DIRECTORY_SEARCH_DEBOUNCE_MS = new ABProp(5204, "250", "250");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WABAI_MESSAGE_FEEDBACK_ENABLED = new ABProp(5215, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_FOLLOWERS_LIST_CACHE_REFRESH_MILLISECONDS = new ABProp(5217, "60000", "60000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAVOIP_ML_BWE_PLC_MODEL_DOWNLOAD_VERSIONS = new ABProp(5228, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAVOIP_ML_BWE_UNDERSHOOT_MODEL_DOWNLOAD_VERSIONS = new ABProp(5231, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_OFFLINE_DYNAMIC_BATCH_SIZE_ENABLED = new ABProp(5271, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BLUE_ENABLED = new ABProp(5276, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BONSAI_CAROUSEL_ENABLED = new ABProp(5283, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_HIDE_NEWS_URL_PREVIEW = new ABProp(5287, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BLUE_EDUCATION_ENABLED = new ABProp(5295, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_OFFLINE_DYNAMIC_BATCH_CONFIG = new ABProp(5297, "{\"version\": \"progressive\", \"multiplier\": 0.25}", "{\"version\": \"progressive\", \"multiplier\": 0.25}");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_DIRECTORY_V2_CACHE_REFRESH_INTERVAL_MS = new ABProp(5304, "1800000", "600000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PREMIUM_BLUE_ENABLED = new ABProp(5318, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp EXTENSIONS_GEOBLOCKING_ENABLED = new ABProp(5333, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_EVOLVE_ABOUT_SEND_ENABLED = new ABProp(5347, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp COMMUNITY_GENERAL_CHAT_CREATE_ENABLED = new ABProp(5453, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_SHARE_LINK_LOGGING_ENABLED = new ABProp(5491, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_FORWARD_LOGGING_V2_ENABLED = new ABProp(5492, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_MAX_MESSAGES_BATCH_PULL = new ABProp(5494, "100", "100");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_RESUME_OPTIMIZED_READ_RECEIPT_SEND_INTERVAL = new ABProp(5502, "500", "500");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp EVENTS_CREATE = new ABProp(5562, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DM_RELIABILITY_LOGGING = new ABProp(5580, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BOT_3P_ENABLED = new ABProp(5587, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp NEWSLETTER_TOS_NOTICE_ID_SMB_WEB = new ABProp(5597, "20601216", "20601216");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp NEWSLETTER_CREATION_TOS_ID_SMB_WEB = new ABProp(5598, "20601217", "20601217");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_SMB_DATA_SHARING_SETTINGS_KILLSWITCH = new ABProp(5615, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SAGA_ENABLED = new ABProp(5626, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BONSAI_ENGLISH_ONLY = new ABProp(5637, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_SEND_ALBUM_ENABLED = new ABProp(5643, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp RT_RECEIVE_REPORTING_TAG = new ABProp(5718, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WABAI_CONSENT_COOLDOWN = new ABProp(5746, "-1", "-1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WABAI_CONSENT_REQUIRED = new ABProp(5747, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp INBOX_FILTERS_RESET_TIMEOUT = new ABProp(5765, "1800", "1800");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ORDER_STATUSES_REVAMP_M1_ENABLED = new ABProp(5770, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PARENT_GROUP_ANNOUNCEMENT_COMMENTS_HISTORY_SYNC_RECEIVER_ENABLED = new ABProp(5813, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp EVOLVE_ABOUT_M1_RECEIVER_ENABLED = new ABProp(5839, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BLUE_STRINGS_ENABLED = new ABProp(5846, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_DIRECTORY_PAGE_SIZE = new ABProp(5853, "50", "50");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MM_DATA_SHARING_DISCLOSURE_ENABLED = new ABProp(5869, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_PROACTIVE_MESSAGE_GAP_HANDLING_ENABLED = new ABProp(5871, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_PTT_RECEIVER_ENABLED = new ABProp(5876, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BOT_3P_STATUS = new ABProp(5985, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DATA_SHARING_TRANSPARENCY_INDICATOR_DURATION = new ABProp(5990, "604800", "604800");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp UNIFIED_POLL_VOTE_ADDON_INFRA_ENABLED = new ABProp(6046, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp INBOX_FILTERS_HAPTIC_FEEDBACK_ENABLED = new ABProp(6052, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp FMX_CTWA_KILL_SWITCH = new ABProp(6061, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BLUE_EDUCATION_V2_ENABLED = new ABProp(6127, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DSA_CHANNELS_REPORT_UNLAWFUL_CONTENT_ENABLED = new ABProp(6145, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp TEXT_STATUS_TTL_SECONDS_ALLOWLIST = new ABProp(6153, "1800,3600,7200,14400,28800,86400", "1800,3600,7200,14400,28800,86400");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp EVOLVE_ABOUT_M1_RECEIVER_FOR_NEW_SURFACES_ENABLED = new ABProp(6172, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_POLL_RECEIVE_ENABLED = new ABProp(6191, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp EVENT_NAME_LENGTH_LIMIT = new ABProp(6207, "100", "100");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp EVENT_DESCRIPTION_LENGTH_LIMIT = new ABProp(6208, "2048", "2048");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_ENTRY_POINT_CONFIG_FETCH_THRESHHOLD = new ABProp(6214, "43200000", "2000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp KILL_SWITCH_CTWA_ML_ENTRY_POINT_CONFIG = new ABProp(6215, "true", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_CTWA_ML_ENTRY_POINT_CONFIG = new ABProp(6216, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SYSTEM_MSG_TEXT_STYLING = new ABProp(6246, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BONSAI_CHAT_LIST_ENTRY_POINT_ENABLED = new ABProp(6251, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_PTT_LOGGING_ENABLED = new ABProp(6274, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_MATERIAL_REFRESH = new ABProp(6332, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BLUE_PROFILE_LOCKED_UI_ENABLED = new ABProp(6337, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_POLL_VOTER_LIST_ENABLED = new ABProp(6382, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_STATUS_UPDATES_CONSUMPTION_ENABLED = new ABProp(6444, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BONSAI_CAROUSEL_REELS_PROFILE_PHOTO_ENABLED = new ABProp(6458, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BONSAI_CAROUSEL_HQ_THUMBNAIL_ENABLED = new ABProp(6459, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_MULTI_ADMIN_MAX_ADMIN_COUNT = new ABProp(6461, "16", "16");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp NEWSLETTER_ADMIN_INVITE_TOS_ID = new ABProp(6498, "20610101", "20610101");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_AUDIO_FILES_SENDER_ENABLED = new ABProp(6505, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_AUDIO_FILES_RECEIVER_ENABLED = new ABProp(6506, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp NEWSLETTER_ADMIN_INVITE_TOS_ID_SMB_WEB = new ABProp(6536, "20610104", "20610104");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp RT_SYNC_REPORTING_TAG = new ABProp(6578, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_SYNCD_DEBUG_DATA_IN_PATCH = new ABProp(6614, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_JPEG_QUALITY = new ABProp(6619, "92", "92");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_PWA_BACKGROUND_SYNC = new ABProp(6656, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_DESIGN_REFRESH = new ABProp(6665, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ORDER_DETAILS_PAYMENT_INSTRUCTIONS_SYNC_ENABLED = new ABProp(6670, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMBA_PREMIUM_MESSAGES_LEAVING_WA_CONTENT = new ABProp(6693, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_PWA_BACKGROUND_SYNC_MIN_INTERVAL_HOURS = new ABProp(6706, "24", "24");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp RT_CLEAN_REPORTING_TAG = new ABProp(6723, "31", "31");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GIMMICK_PHASE_TWO_DATA_SUFFIX = new ABProp(6785, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp LID_STATUS_SEND_ENABLED = new ABProp(6791, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_BUSINESS_TOOLS_DRAWER_ENABLED = new ABProp(6803, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DATA_PRIVACY_PHASE_2_ENABLED = new ABProp(6843, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_INTERN_DOGFOODING_UPSELL_ENABLED = new ABProp(6858, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_INTERN_DOGFOODING_UPSELL_SNOOZE_DURATION = new ABProp(6859, "86400", "86400");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_INTERN_DOGFOODING_UPSELL_CONTENT = new ABProp(6860, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ADV_ACCEPT_HOSTED_DEVICES = new ABProp(6939, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_AUDIO_FILES_SENDER_WAVEFORM_ENABLED = new ABProp(6943, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp INBOX_FILTERS_READ_UNREAD_LOGGING_ENABLED = new ABProp(6967, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_AUDIO_FILES_DISPLAY_WAVEFORM_ENABLED = new ABProp(6996, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENTS_BR_PIX_PHASE_1_SELLER_SYNC_ENABLED = new ABProp(7024, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SAGA_COPY = new ABProp(7044, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BONSAI_PLUGINS_V2_ENABLED = new ABProp(7075, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SUPPORT_MESSAGE_FEEDBACK_ENABLED = new ABProp(7080, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp INBOX_FILTERS_SMB_ENABLED = new ABProp(7108, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DATA_PRIVACY_PHASE_2_NON_E2EE_ENABLED = new ABProp(7131, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DM_INITIATOR_TRIGGER_GROUPS = new ABProp(7141, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_PRODUCT_CAROUSEL_MESSAGE = new ABProp(7177, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_ADMIN_REPLY_ENABLED = new ABProp(7211, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_QUICK_FORWARDING_BUTTON_MODE = new ABProp(7234, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_ADMIN_REPLY_RECEIVER_ENABLED = new ABProp(7237, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp FAVORITES_LIMIT = new ABProp(7267, "100", "100");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_DISABLE_SW_ON_SAFARI_PWA = new ABProp(7281, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp VISIBLE_MESSAGE_DROP_PLACEHOLDER_ENABLED_INTERNAL_ONLY = new ABProp(7287, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp NATIVE_CONTACT_COMPANION_CHANGE_ENABLED = new ABProp(7301, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_RECENT_SYNC_CHUNK_DOWNLOAD_OPTIMIZATION = new ABProp(7356, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp EVENTS_EDIT_RECEIVE = new ABProp(7358, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DM_INITIATOR_TRIGGER_DAILY_LOGS = new ABProp(7402, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_AUTODOWNLOAD_STICKERS = new ABProp(7422, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CUSTOM_RACING_EMOJI = new ABProp(7463, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PINNED_MESSAGES_M2_IMAGE_THUMBNAIL = new ABProp(7467, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USERNAME_SECURITY_CODE_GENERATION = new ABProp(7468, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SIMILAR_CHANNELS_IN_THREAD_ON_FOLLOW_ENABLED = new ABProp(7472, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SIMILAR_CHANNELS_IN_CHANNEL_DETAILS_ENABLED = new ABProp(7473, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp EVENTS_M3_COVER_IMAGE_SEND = new ABProp(7510, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp EVENTS_M3_COVER_IMAGE_RECEIVE = new ABProp(7511, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SIMILAR_CHANNELS_MAX_LIMIT = new ABProp(7559, "10", "10");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SIMILAR_CHANNELS_MIN_LIMIT = new ABProp(7560, "4", "4");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ADDON_INFRA_ENABLE_PERF_LOGGING = new ABProp(7567, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DSA_INFORMATION_FOR_EU_ONLY_ENABLED = new ABProp(7592, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PROFILE_PICTURE_DEEPLINK_ENABLED = new ABProp(7634, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp INBOX_FILTERS_CUSTOM_SMB_ENABLED = new ABProp(7637, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_GRAPHQL_TO_FETCH_QP_ENABLED = new ABProp(7645, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_GRAPHQL_TO_FETCH_QP_FREQUENCY_MINS = new ABProp(7646, "1320", "5");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_GRAPHQL_TO_FETCH_QP_SURFACE_IDS = new ABProp(7647, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_QPL_LOGGING = new ABProp(7677, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_REACTIONS_BOTTOMSHEET_TAP_TO_REACT_ENABLED = new ABProp(7682, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_DIRECTORY_CATEGORIES_ENABLED = new ABProp(7685, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_NOTES_V1_ENABLED = new ABProp(7710, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_DIRECTORY_CATEGORY_TYPES = new ABProp(7734, "3,7,6,4,1,5,2", "3,7,6,4,1,5,2");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp INBOX_FILTERS_SUPPRESS_CONTACT_FILTER = new ABProp(7769, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SINGLE_E2EE_SESSION_MIGRATION_STATE_OUTGOING = new ABProp(7820, "2", "2");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SINGLE_E2EE_SESSION_MIGRATION_STATE_INCOMING = new ABProp(7821, "2", "2");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BONSAI_SUPPORTED_LANGUAGES = new ABProp(7848, "en", "en");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_COMMS_SOCKET_RECONNECT_ENABLED = new ABProp(7854, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENTS_BR_PIX_QUICK_REPLY_ENABLED = new ABProp(7857, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp STATUS_MENTIONS_RECEIVER = new ABProp(7869, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_STICKER_VERIFICATION_FOR_GIMMICK = new ABProp(7886, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_POLL_VOTERS_SUMMARY_CACHE_TTL_MS = new ABProp(7919, "120000", "120000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_POLL_VOTERS_DETAILS_CACHE_TTL_MS = new ABProp(7920, "300000", "300000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp META_VERIFIED_BADGE_EDUCATION_VAI_CONTENT = new ABProp(7976, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DIRECTORY_CATEGORIES_NEWSLETTERS_PER_CATEGORY_LIMIT = new ABProp(7986, "10", "10");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BONSAI_META_AI_SHORTCUT_TOS_ENABLED = new ABProp(8004, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_LONG_TERM_HOLDOUT_CONTENT_ENABLED = new ABProp(8015, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_SOCKET_PARALLEL_CONNECTION_ENABLED = new ABProp(8019, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_SEARCH_NULL_STATE_ENABLED = new ABProp(8026, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_VERIFIED_BADGE_IN_COMPACT_INBOX_ENABLED = new ABProp(8059, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_SEARCH_MAX_NUM_SUGGESTIONS = new ABProp(8076, "5", "5");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp XPLAT_ATTACHMENT_FORMAT_CHECK_V2 = new ABProp(8082, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_SEARCH_NULL_STATE_UPDATE_INTERVAL = new ABProp(8100, "86400", "86400");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_STICKY_HD_PHOTO_SETTING_ENABLED = new ABProp(8115, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp APP_EXIT_REASON_VERSION = new ABProp(8147, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_DIRECTORY_CATEGORIES_CACHE_REFRESH_INTERVAL_MS = new ABProp(8151, "86400000", "600000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BR_ENABLE_PAYMENT_LOGOS_ON_BUBBLE = new ABProp(8160, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_AD_ACCOUNT_TOKEN_STORAGE_KILL_SWITCH_WEB = new ABProp(8166, "true", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_RECOMMENDED_V3_UI_LIMIT = new ABProp(8167, "5", "5");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SEARCH_THE_WEB_DIALOG_REDESIGN = new ABProp(8171, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_LARGER_LINK_PREVIEWS = new ABProp(8172, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNEL_TO_CHANNEL_FORWARDING_LOGGING_ENABLED = new ABProp(8227, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_META_VERIFIED_CONTEXT_CARD = new ABProp(8313, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp REPORT_BLOCK_IMPROVEMENTS_FOR_GROUPS_ENABLED = new ABProp(8327, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_PENDING_MESSAGE_CACHE_ENABLED = new ABProp(8353, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp UNIFIED_PIN_ADDON_TABLE_ENABLED = new ABProp(8356, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_OFFLINE_MESSAGE_PROCESSOR_TIMEOUT_SECONDS = new ABProp(8406, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_SEARCH_NULL_STATE_ROW_COUNT = new ABProp(8407, "3", "3");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MEX_USYNC_USERNAME_QUERY = new ABProp(8421, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEBC_PAGE_LOAD_EARLY_COMMIT_ENABLED = new ABProp(8458, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SEARCH_THE_WEB_URL_OFFER = new ABProp(8473, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BIZ_AI_SMB_AGENTS_AUTOMATIC_REPLY_ENABLED = new ABProp(8505, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ALBUM_V2_RECEIVING_ENABLED = new ABProp(8528, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ALBUM_V2_SENDER_ENABLED = new ABProp(8529, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp IMPROVE_SUBGROUP_ACTIVATION_SUBGROUP_POLL_INTERVAL = new ABProp(8542, "43200", "43200");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_COMMUNITIES_GENERAL_CHAT_V_2 = new ABProp(8580, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp TEXT_USER_JOURNEY_LOGGING_WAM_ENABLED = new ABProp(8627, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PTT_USER_JOURNEY_LOGGING_WAM_ENABLED = new ABProp(8630, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_UPDATES_TAB_SWIPE_ACTIONS_ENABLED = new ABProp(8653, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_AD_ACCOUNT_NONCE_RETRIES_MAX_WEB = new ABProp(8663, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_AD_ACCOUNT_NONCE_PUSH_WAIT_TIMEOUT_WEB = new ABProp(8664, "20", "20");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_METABOT_SEND_IMAGE_LIMIT = new ABProp(8685, "1", "1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp STATUS_POSTER_SIDE_GATING_ENABLED = new ABProp(8742, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USERNAME_MEX_ACCOUNT_SYNC_ENABLED = new ABProp(8763, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_BACKGROUND_SYNC_V2 = new ABProp(8782, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MESSAGE_ASSOCIATION_INFRA_ENABLED = new ABProp(8783, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp LID_MIGRATION_NOTIFICATIONS_ENABLED = new ABProp(8785, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_CATALOG_GRAPHQL_GET_PRODUCT_LIST = new ABProp(8799, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GRAPHQL_GET_PRODUCT_LIST = new ABProp(8800, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp RT_SENDER_REPORTING_TOKEN_VERSION = new ABProp(8860, "2", "2");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_MINIMIZE_INDIVIDUAL_MUTATION_WRITE = new ABProp(8910, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENTS_BR_FORCE_COPY_PIX_CTA_ENABLED = new ABProp(8953, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_PRODUCER_INSIGHTS_ENABLED = new ABProp(8960, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_PAYMENT_LINKS_URL_REGEX_LIST = new ABProp(8969, "{}", "{}");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENTS_BR_COPY_PIX_CODE_API_MERCHANT_ENABLED = new ABProp(9017, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_ORDERS_GRAPHQL_GET_ORDER_INFO = new ABProp(9030, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_ORDERS_GRAPHQL_REFRESH_CART = new ABProp(9032, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENTS_BR_MERCHANT_PSP_ACCOUNT_STATUS_SYNC = new ABProp(9076, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp LAZY_SYSTEM_MESSAGE_INSERTION_ENABLED = new ABProp(9077, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp FLOWS_TERMINATION_MESSAGE_V2_SENDING_ENABLED = new ABProp(9157, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_METABOT_IMAGE_INPUT_LANGUAGES = new ABProp(9163, " ", "en");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SEND_INVALID_PROTOBUF_NACK_FAILURE_REASON = new ABProp(9174, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_GRAPHQL_TOKEN_RECOVERY_DURING_ACCOUNT_RECOVERY_ENABLED = new ABProp(9197, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp HASH_IDENTITY_KEYS_FOR_QR_CODE_DEVICE_VERIFICATION = new ABProp(9211, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_PAYMENT_LINKS_LOGGING_ENABLED = new ABProp(9213, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp VERIFIED_BADGE_IN_CHATS_LIST_ENABLED = new ABProp(9292, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DIRECTORY_CATEGORIES_DISPLAY_NEWSLETTERS_PER_CATEGORY_LIMIT = new ABProp(9312, "4", "4");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp OPTIMIZED_DELIVERY_SIGNAL_COLLECTION_ENABLED = new ABProp(9348, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNEL_FORWARD_BOTTOM_BUTTON_ENABLED = new ABProp(9422, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp LID_ONE_ON_ONE_MIGRATION_ENABLED = new ABProp(9435, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_PRODUCER_INSIGHTS_MIN_FOLLOWERS = new ABProp(9447, "100", "100");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp STATUS_RANKING_POSTER_SIDE_GATING_ENABLED = new ABProp(9453, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_PDFN_TOS_SHORTCUT_NOTICE_ID = new ABProp(9482, " ", " ");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_PDFN_TOS_INVOKE_NOTICE_ID = new ABProp(9483, " ", " ");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_WAM_MAX_BUFFER_UPLOAD_SIZE_BYTES = new ABProp(9501, "64000", "64000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp STATUS_FUTURE_PROOFING = new ABProp(9522, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MEX_USYNC_ABOUT_STATUS = new ABProp(9524, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BONSAI_FP_UGC_SENDER = new ABProp(9541, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SEARCH_THE_WEB_IMAGE_SEARCH = new ABProp(9547, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SEARCH_THE_WEB_TEXT_SEARCH = new ABProp(9548, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp RT_CLEAN_REPORTING_TOKEN = new ABProp(9567, "31", "31");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_AD_CREATION_ENTRY_POINT_CATALOG_WEB = new ABProp(9596, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_EXPERIMENT_GRAPHQL_CONFIG = new ABProp(9601, " ", " ");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_ADMIN_INSIGHTS_GIZMOS_ENABLED = new ABProp(9641, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PROFILE_SCRAPING_PRIVACY_TOKEN_IN_ABOUT_IQ = new ABProp(9668, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SINGLE_EMOJI_LOGGING_ENABLED = new ABProp(9669, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_BUSY_REASON_FS = new ABProp(9674, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_AD_CREATION_ENTRY_POINT_CATALOG_PRODUCT_WEB = new ABProp(9677, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_PTT_MAIN_GATE_SUPPORTED_LANGUAGES = new ABProp(9694, " ", "en");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_CTWA_WEB_HIDE_AD_CONTEXT_IF_SOFT_DISMISSED_IN_PRIMARY = new ABProp(9729, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USE_PER_CHAT_WALLPAPER = new ABProp(9756, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ANIMATED_EMOJI_FINAL_SET_ENABLED = new ABProp(9757, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ANIMATED_EMOJI_SET_1_ENABLED = new ABProp(9758, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_PRODUCER_INSIGHTS_HIDE_DELTAS = new ABProp(9792, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp RT_REPORT_TOKEN_FROM_INCLUSION_LIST = new ABProp(9818, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WHATSAPP_VPV_LOGGING_ENABLED = new ABProp(9833, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_VPV_LOGGING_ENABLED = new ABProp(9834, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BRIGADING_PRIVACY_SETTING_ENABLED = new ABProp(9876, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SAGA_V1_REENGAGEMENT_ENABLED = new ABProp(9924, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp EVENTS_CREATE_CAG_ENABLED = new ABProp(9932, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SAGA_V1_ENABLED = new ABProp(9942, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SAGA_V1_NUX_ENABLED = new ABProp(9944, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MM_MESSAGE_LEVEL_FEEDBACK_ENABLED = new ABProp(10011, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_CALLING_DEEP_LINK_ERROR = new ABProp(10051, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_RING_FOR_GC_ON_OFFER_EXPIRE = new ABProp(10103, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_DIRECTORY_CATEGORIES_LOGGING_ENABLED = new ABProp(10188, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_WEFR_CLIENT_EXPO_PULSE = new ABProp(10230, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_NOTES_CONTENT_MAX_LIMIT = new ABProp(10272, "5000", "5000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp IGNORE_ONE_TO_ONE_TERMINATE_IN_GROUP_CALL = new ABProp(10273, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp OPTIMIZED_DELIVERY_SIGNAL_COLLECTION_CONFIG = new ABProp(10302, "{}", "{}");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp OPTIMIZED_DELIVERY_TOKENS_STORAGE_CONFIG = new ABProp(10303, "{}", "{}");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_FETCH_AND_LOG_CAPABILITIES = new ABProp(10325, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_CAPABILITIES_ENABLED = new ABProp(10328, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_MM_BIZ_AI_DISCLOSURE_UPDATE_ENABLED = new ABProp(10379, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_PAYMENT_LINKS_SELLER_LOGGING_ENABLED = new ABProp(10389, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNEL_POLL_FORWARDING_ENABLED = new ABProp(10412, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp REACTION_USER_JOURNEY_LOGGING_ENABLED = new ABProp(10438, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_AGENT_CHAT_LIST_INDICATOR_ENABLED = new ABProp(10455, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_AGENT_THREAD_CONTROL_NOTIFICATION_ENABLED = new ABProp(10456, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PRIVACY_TOKEN_SENDING_ON_ALL_1_ON_1_MESSAGES = new ABProp(10518, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_COMMUNITY_SUSPEND_AND_APPEALS = new ABProp(10539, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_CALL_RESULT_FIX_FOR_404_ACCEPT_NACK = new ABProp(10565, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SAGA_V1_CAROUSEL = new ABProp(10609, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MM_MESSAGE_LEVEL_FEEDBACK_NOT_INTERESTED_MENU_ENABLED = new ABProp(10668, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ALBUM_V2_FORWARD_AS_ALBUM_ENABLED = new ABProp(10725, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_HISTORY_SYNC_ALLOW_DUPLICATE_IN_BULK_ERROR = new ABProp(10842, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ALBUM_V2_MIN_ITEMS_TO_SEND_AS_ALBUM_ENABLED = new ABProp(10848, "4", "4");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_VIEW_MODE_USAGE_ENABLED = new ABProp(10856, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WABBA_RECEIVER_ENABLED = new ABProp(10970, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MUSIC_OHAI_PROXY_URL = new ABProp(10975, "https://meta-ohttp-relay-prod.fastly-edge.com/", "https://meta-ohttp-relay-prod.fastly-edge.com/");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_LONG_TERM_HOLDOUT_CLIENT_SIDE_CHECK = new ABProp(11000, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ADV_LOGOUT_ON_SELF_DEVICE_LIST_EXPIRED = new ABProp(11011, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAMO_SUB_LOGGING_ENABLED_V2 = new ABProp(11017, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAMO_SUB_ADMIN_ENABLED_V2 = new ABProp(11020, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAMO_SUB_CONSUMER_ENABLED_V2 = new ABProp(11021, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp META_CATALOG_LINKING_M2_ENABLED = new ABProp(11029, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp LID_MIGRATION_FOR_VNAME_ENABLED = new ABProp(11049, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp LID_ONE_ON_ONE_MIGRATION_LOG_OUT_ON_MISMATCH = new ABProp(11050, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAMO_SUB_MESSAGES_SUPPORTED = new ABProp(11062, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DEFAULT_ENDPOINT_THREAD_POLL_TIMEOUT = new ABProp(11129, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_HOME_BOT_PROFILE_SYNC_INTERVAL_SEC = new ABProp(11168, "86400", "86400");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_CHANNEL_VIDEO_SERVER_THUMBNAIL = new ABProp(11192, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_CUSTOM_LABEL_SIGNALS_ENABLED = new ABProp(11205, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MM_OPT_OUT_ENABLED = new ABProp(11241, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PRIVACY_TOKEN_SENDING_ON_GROUP_CREATE = new ABProp(11261, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PRIVACY_TOKEN_SENDING_ON_GROUP_PARTICIPANT_ADD = new ABProp(11262, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ML_MODEL_DOWNLOAD_SKIP_HASH_CHECK = new ABProp(11454, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp IGNORE_JOINABLE_TERMINATE_ON_EXPIRED_OFFER = new ABProp(11519, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_CATALOG_GRAPHQL_VERIFY_POSTCODE = new ABProp(11624, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp NATIVE_CONTACT_COMPANION_NUX_LEARN_MORE_ARTICLE_ID = new ABProp(11644, "1191526044909364", "1191526044909364");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp EXTERNAL_CTX_AUTHORISE_WA_CHAT = new ABProp(11655, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_FBID_MIGRATION_RECEIVE_ENABLED = new ABProp(11660, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENTS_BR_P2M_BOLETO_ENABLED = new ABProp(11671, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_CATALOG_GRAPHQL_GET_PUBLIC_KEY = new ABProp(11690, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ADD_TO_CALL_IN_CHAT_THREAD = new ABProp(11700, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SAGA_PROTOBUF_AI_STARDUST_WEB = new ABProp(11756, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USERNAME_ENGAGEMENT_NETWORK_IMPACT_LOGGING = new ABProp(11794, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SYNCD_MUTATION_AND_BUNDLE_LOGGING = new ABProp(11821, "{\"allowlist\": []}", "{\"allowlist\": []}");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SAGA_PROTOBUF_SHOW_SYSMSG_WEB = new ABProp(11832, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp NOTIFICATION_HIGHLIGHT_GROUP_SIZE_THRESHOLD = new ABProp(11891, "130", "130");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ALBUM_V2_ITEM_WITH_CAPTION_IN_ALBUM_RECEIVER_ENABLED = new ABProp(11943, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp FUTUREPROOF_ASSOCIATED_CHILD_ENABLED = new ABProp(11976, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USE_SIGNED_SHIMMED_URL_LINK = new ABProp(11977, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNEL_PHOTO_POLL_RECEIVER_ENABLED = new ABProp(11980, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNEL_PHOTO_POLL_SENDER_ENABLED = new ABProp(11989, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp LID_MIGRATION_FOR_BIZ_PROFILE_ENABLED = new ABProp(12000, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_GROUP_CREATE_OR_ADD_RATE_LIMITING_ERROR_UX = new ABProp(12020, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_CATALOG_GRAPHQL_COMMERCE_SETTINGS = new ABProp(12099, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MM_OPT_OUT_FMX_STOP_FOR_HIGH_TRUST = new ABProp(12172, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp OHAI_REQUEST_KB_SIZE = new ABProp(12248, "20", "20");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp STATUS_MENTIONS_GROUP_MENTION_RECEIVER = new ABProp(12254, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp POLL_RESULT_SNAPSHOT_POLLTYPE_ENVELOPE_ENABLED = new ABProp(12258, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_NEW_CHAT_FLOW_REFRESH_VARIANT = new ABProp(12276, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_VIEW_COUNTS_VPV_LOGGING_ENABLED = new ABProp(12295, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAM_DISABLE_ABKEY_ATTRIBUTE = new ABProp(12390, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAM_DISABLE_EXPOKEY_ATTRIBUTE = new ABProp(12391, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_SIGNAL_FUTURE_MESSAGES_MAX = new ABProp(12509, "20000", "20000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp FLOWS_WA_WEB = new ABProp(12520, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ALBUM_V2_MIN_ITEMS_TO_SEND_ALBUM_WITH_CAPTION = new ABProp(12538, "2", "2");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_RICH_RESPONSE_MAIN_GATE_ENABLED = new ABProp(12539, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp OTP_LID_MIGRATION_ENABLED = new ABProp(12553, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp RT_SENDER_DUAL_ENCRYPTED_MSG_ENABLED = new ABProp(12623, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_VOICE_MULTIMODAL_COMPOSER_ENABLED = new ABProp(12692, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAMO_SUB_PROCESS_MESSAGE_KILL_SWITCH = new ABProp(12722, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp EXTERNAL_CTX_URL_PARAM_NAMES = new ABProp(12726, "partnertoken", "partnertoken");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp EXTERNAL_CTX_AUTHORISE_EXISTING_CHATS = new ABProp(12761, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_FBID_MIGRATION_INVOKE_RECEIVE_ENABLED = new ABProp(12795, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MEDIA_VIEWER_ACCELERATED_PLAYBACK_ENABLED = new ABProp(12813, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_DEXIE_HOOKS_SUPPORT_ENABLED = new ABProp(12831, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp REUSE_CACHED_CERTS_FOR_DATA_CHANNEL = new ABProp(12913, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp LID_GROUP_CREATION_ADDRESSING_MODE_OVERRIDE = new ABProp(12985, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNEL_OSA_REPORTING_ENABLED = new ABProp(12987, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PRIVATE_OSA_REPORTING_ENABLED = new ABProp(12990, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_UI_REFRESH_M1 = new ABProp(12993, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DISCLOSURE_FOR_THE_MARKETING_MESSAGE_BODY_LINKS_ENABLED = new ABProp(12994, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp FT_VALIDATION_FAILURE_DROP_PLACEHOLDER = new ABProp(13063, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WABBA_SAVE_TO_CAMERA_ROLL_ENABLED = new ABProp(13114, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp LID_ONE_ON_ONE_MIGRATION_COMPATIBLE = new ABProp(13161, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp EARLY_AUDIO_DRIVER_CAPTURE_AT_NATIVE = new ABProp(13166, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp EARLY_AUDIO_DRIVER_PRE_BUFFERING = new ABProp(13168, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNEL_ALBUM_V2_RECEIVING_ENABLED = new ABProp(13219, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNEL_ALBUM_V2_SENDER_ENABLED = new ABProp(13220, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_AUDIO_DEVICE_ASYNC_START = new ABProp(13231, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_ENABLE_BIZ_DATA_SHARING_AFTER_NUX_DISMISS = new ABProp(13240, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_VOICE_ENTRY_POINT_LOGGING_ENABLED = new ABProp(13247, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ANYONE_CAN_LINK_TO_GROUPS = new ABProp(13268, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp STATUS_SAVE_TO_CAMERA_ROLL_ENABLED = new ABProp(13280, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CUSTOM_RACING_EMOJI_FEB2025 = new ABProp(13322, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp EMOJI_SEARCH_CLDR = new ABProp(13323, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_CALLING_USERNAME = new ABProp(13359, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PER_CUSTOMER_DATA_SHARING_CONTROLS_ELIGIBLE = new ABProp(13383, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_DOWNLOAD_3PD_SIGNALS = new ABProp(13385, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_AI_AGENTS_WEB_CHAT_ASSIGNMENT_INTEROP_ENABLED = new ABProp(13387, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_PRODUCT_COUNTRY_OF_ORIGIN_M1 = new ABProp(13415, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USE_CACHED_APP_SETTINGS_FROM_GLOBAL_CTX = new ABProp(13428, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp RASTERIZE_TEXT_STATUS_PIXEL_WIDTH = new ABProp(13460, "1080", "1080");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BIZ_AI_AUTO_SAVE_ENABLED = new ABProp(13464, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BIZ_AI_COACHING_ENABLED = new ABProp(13465, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_3PD_DATA_SHARING_ON_THREAD_ENTRY = new ABProp(13485, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ANIMATED_RACE_MERCEDES_CAR_EMOJI_ENABLED = new ABProp(13490, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_UNIFIED_CALL_BUTTONS_IN_CHAT = new ABProp(13497, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MM_USER_CONTROLS_EXPOSURE = new ABProp(13510, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MEMBER_NAME_TAG_RECEIVER_ENABLED = new ABProp(13523, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_PTV_RECEIVING_ENABLED = new ABProp(13559, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp EXTERNAL_CTX_FOA_LOGGING = new ABProp(13565, "1", "1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_RICH_RESPONSE_GRID_IMAGE_ENABLED = new ABProp(13578, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_SHOW_ADS_DATA_SHARING_AFTER_MESSAGE = new ABProp(13579, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_FMX_AGM_ENABLED = new ABProp(13597, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp STICKY_CHAT_PROFILE_PICTURE_ENABLED = new ABProp(13692, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_PTV_FORWARDING_ENABLED = new ABProp(13776, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_EARLY_AUDIO_DRIVER_START = new ABProp(13807, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PREMIUM_BROADCAST_SMB_CAPPING_ENABLED = new ABProp(13808, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DEFENSE_MODE_AVAILABLE = new ABProp(13874, "0", "1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_FORWARD_FLOW_SURFACE_META_AI_AS_CONTACT_ENABLED = new ABProp(13879, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp LID_ONE_ON_ONE_MIGRATION_PEER_SYNC_TIMEOUT_IN_SECONDS = new ABProp(13936, "300", "300");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp NEWSLETTERS_VIDEO_PLAYBACK_WABBA_LOGGING_ENABLED = new ABProp(13954, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_STATUS_RECEIVER_ENABLED = new ABProp(13956, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_PDFN_TOS_INLINE_NOTICES = new ABProp(13970, " ", " ");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp UPDATES_QUICK_PROMOTION_BANNER_ENABLED = new ABProp(13997, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MM_USER_CONTROLS_EXCEPTION_NUMBER_PREFIXES = new ABProp(13999, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SNAPL_NEWSLETTER_LOGGING_MEDIA_ID_PLACEHOLDER_STRING = new ABProp(14064, "-1", "-1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_RICH_RESPONSE_WEB_STRUCTURED_RESPONSE_ENABLED = new ABProp(14141, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp VIEW_REPLIES_INFRA_ENABLED = new ABProp(14199, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_REWRITE_ENABLED = new ABProp(14219, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_REWRITE_SUPPORTED_LANGUAGES = new ABProp(14220, " ", "en");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_UK_OSA_ENABLED = new ABProp(14249, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PRIVATE_MESSAGING_UK_OSA_ENABLED = new ABProp(14250, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_FAVICONS_UPDATE_M1 = new ABProp(14260, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp IS_PART_OF_GSC_EXPERIMENT = new ABProp(14279, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USERNAME_NUMERIC_CODE_V4 = new ABProp(14286, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_CATALOG_RECOVERY_FLOW_ENABLED = new ABProp(14294, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_WAFFLE = new ABProp(14300, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp LID_TRUSTED_TOKEN_ISSUE_TO_LID = new ABProp(14303, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SUPPORT_LIDS = new ABProp(14317, "4200746488034,30563255730192,70334669676777,19349129719984,66065505775654,133814269518032,243799792062487,7323238039569,269290422947912,261718412386336,4351103873168,12391299473616,92410801582180,277730033709185,36090878648473,79882365190287,94274800595104,117794058317863,115784047153172,179250745360524,7301780005088,166653589463190,94249030815912,198964645236955,198427807899653,23656948363422,255735573270728,106670109786240,130932396826763,18855208456329", "4200746488034,30563255730192,70334669676777,19349129719984,66065505775654,133814269518032,243799792062487,7323238039569,269290422947912,261718412386336,4351103873168,12391299473616,92410801582180,277730033709185,36090878648473,79882365190287,94274800595104,117794058317863,115784047153172,179250745360524,7301780005088,166653589463190,94249030815912,198964645236955,198427807899653,23656948363422,255735573270728,106670109786240,130932396826763,18855208456329");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENT_SUPPORT_LIDS = new ABProp(14333, "116664750354676,128385682505839,46635358933114,26521959944357,200206125658243,179985503506636,187797998674170,228746200088715,117914552262794,10158134550607", "116664750354676,128385682505839,46635358933114,26521959944357,200206125658243,179985503506636,187797998674170,228746200088715,117914552262794,10158134550607");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GIF_PROVIDER = new ABProp(14343, "1", "1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENT_BR_HOLDOUT = new ABProp(14358, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp UPDATES_PRIVACY_NOTICE_ROLLOUT_DATE = new ABProp(14387, "1742310000", "1742310000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp RENDER_UPDATED_DISCLOSURE = new ABProp(14407, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SYNCD_SENTINEL_TIMEOUT_SECONDS = new ABProp(14485, "3", "3");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SYNCD_KEY_MAX_USE_DAYS = new ABProp(14488, "30", "30");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SYNCD_WAIT_FOR_KEY_TIMEOUT_DAYS = new ABProp(14492, "7", "7");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SYNCD_INLINE_MUTATIONS_MAX_COUNT = new ABProp(14494, "100", "100");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SYNCD_PATCH_PROTOBUF_MAX_SIZE = new ABProp(14495, "10", "10");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USERNAME_CONTACT_USYNC_LID_BASED = new ABProp(14565, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp OPTIMIZED_DELIVERY_MULTIPLE_COLLECTION_WINDOWS_ENABLED = new ABProp(14588, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_UGC_VOICE_FS_LOGGING = new ABProp(14641, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp HYBRID_EDUCATIONAL_DIALOGS_ENABLED = new ABProp(14674, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp EDUCATIONAL_DIALOGS_BUTTON_ENABLED = new ABProp(14676, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SEARCH_USER_JOURNEY_LOGGING_WAM_ENABLED = new ABProp(14682, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_REWRITE_TONE_MODIFIERS = new ABProp(14743, "rephrase,professional,funny,supportive", "rephrase,professional,funny,supportive");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_DESCRIPTION_LENGTH = new ABProp(14778, "2048", "2048");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_MAX_SUBJECT = new ABProp(14801, "100", "100");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_BIZ_PROFILE_OPTIONS = new ABProp(14881, "116", "116");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_CUSTOM_LABEL_ALGORITHM = new ABProp(14887, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_REWRITE_ENTRY_POINT_MIN_WORDS = new ABProp(14923, "4", "4");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_REWRITE_NUM_SUGGESTIONS = new ABProp(14924, "3", "3");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_PAYMENT_LINKS_CTA_VARIANT = new ABProp(14957, "2", "2");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_PAYMENT_LINKS_CTA_BUTTON_KILL_SWITCH = new ABProp(14967, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAMO_PRIVACY_TOS_LINKED_HIGHLIGHTED_NOTICE_ID = new ABProp(14985, "20610204", "20610204");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAMO_PRIVACY_TOS_UNLINKED_HIGHLIGHTED_NOTICE_ID = new ABProp(14987, "20610203", "20610203");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_PAYMENT_LINKS_CTA_PSP_LIST = new ABProp(14998, "{}", "{}");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp RT_EDIT_RECEIVE = new ABProp(15016, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp HARMFUL_FILE_DIALOG_LOGGING = new ABProp(15020, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp UPDATED_HARMFUL_DOCUMENT_DIALOG = new ABProp(15022, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp LIMIT_SHARING_ENABLED_FOR_1ON1_CHAT = new ABProp(15127, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp LIMIT_SHARING_PROTOCOL_MESSAGE_RECEIVER_ENABLED = new ABProp(15129, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp RT_WEB_DELAY_PROCESSING = new ABProp(15181, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_QUESTION_RECEIVER_MESSAGE_TYPES_M1_ENABLED = new ABProp(15246, "", " 22");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAMO_PRIVACY_TOS_SHOW_CHANNELS_NUX_ENABLED = new ABProp(15254, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp NEWSLETTER_NUX_NOTICE_ID = new ABProp(15255, "20610210", "20610210");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp NEWSLETTER_ADMIN_INVITE_NUX_ID = new ABProp(15256, "20610220", "20610220");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp RT_RECEIVER_DUAL_ENCRYPTED_MSG_ENABLED = new ABProp(15258, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_IMPORTANT_LABEL_SENDS_SIGNALS = new ABProp(15271, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_PDFN_TOS_NON_BLOCKING_NOTICES = new ABProp(15280, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_PDFN_TOS_MASTER_NOTICE_ID = new ABProp(15295, " ", " ");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_SMB_DETECTED_OUTCOME_LABELS_ENABLED = new ABProp(15307, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_SMB_DETECTED_OUTCOME_LABELS_MERGER_ENABLED = new ABProp(15308, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_HISTORY_RECEIVE = new ABProp(15311, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_HISTORY_SEND = new ABProp(15313, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_QUESTION_SENDER_MESSAGE_TYPES_M1_ENABLED = new ABProp(15418, " ", " ");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SEARCH_THE_WEB_DESIGN_EXPERIMENT_V1 = new ABProp(15423, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_WEB_CALLING = new ABProp(15461, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USERNAME_ADOPTION_AND_ENGAGEMENT_MONITORING_ENABLED = new ABProp(15493, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_UPCOMING_SCHEDULE_CALL_EVENTS_IN_CALLS_TAB = new ABProp(15514, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BIZ_AI_IN_THREAD_UNMUTE_V2 = new ABProp(15523, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_CATALOG_VIEWING_VARIANTS_ENABLED = new ABProp(15534, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_GROWTH_EMPTY_STATE_UPSELL_VARIANT_M1 = new ABProp(15557, "1", "1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_RICH_RESPONSE_REASONING_ENABLED = new ABProp(15589, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PHONE_NUMBER_SHARING_FLOW = new ABProp(15653, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_3PD_DATA_SHARING_COOLDOWN_MAX_TIMES_SHOWN_FOR_OPTED_OUT = new ABProp(15686, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp QUOTED_MESSAGE_USER_JOURNEY_LOGGING_ENABLED = new ABProp(15694, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAMO_AGM_ENABLED = new ABProp(15714, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_HISTORY_NOTICE_RECEIVE = new ABProp(15722, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_OPEN_QPL_IMPROVEMENTS_ENABLED = new ABProp(15754, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CREATE_GROUP_AND_ADD_MEMBER_OVERFLOW = new ABProp(15772, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp LID_ONE_TO_ONE_MIGRATION_EVENT_RESPONSE_FORCE_PN_JID = new ABProp(15791, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp KMP_SYNCD_ENGINE_CRYPTO_ENABLED = new ABProp(15909, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USERNAME_SEARCH = new ABProp(15956, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_PRE_WARM_AUDIO_COMPONENT = new ABProp(15994, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp FORWARDED_MESSAGE_USER_JOURNEY_LOGGING_ENABLED = new ABProp(16055, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MESSAGE_EDIT_TO_MESSAGE_SECRET_SENDER_ENABLED = new ABProp(16057, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_ALL_LANGUAGES_ENABLED = new ABProp(16091, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp LID_GROUP_MIGRATION_NON_MEMBER_IQ = new ABProp(16104, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_DEBUG_COLOR_CODE_RETRY_MESSAGES = new ABProp(16138, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USERNAME_GROUP_MUTATION_ENABLED = new ABProp(16148, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENTS_BR_PIX_ON_WEB = new ABProp(16156, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PRIVACY_SETTINGS_PROFILE_LID_MIGRATION_ENABLE = new ABProp(16161, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_IS_MULTI_ADMIN_LID_MIGRATION_ENABLED = new ABProp(16193, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PRIVACY_SETTINGS_ABOUT_LID_MIGRATION_ENABLE = new ABProp(16195, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ATTACH_TRANSPORT_RTX = new ABProp(16201, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_SEARCH_BAR_2025_REDESIGN_ENABLED = new ABProp(16208, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_MAC_BETA_UPSELL = new ABProp(16223, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SCHEDULE_CALL_SHOW_JOIN_BUTTON_TIME_INTERVAL_MINS = new ABProp(16253, "5", "5");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SCHEDULE_CALL_SHOW_UPCOMING_BANNER_TIME_INTERVAL_MINS = new ABProp(16254, "1440", "1440");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PRIVACY_SETTINGS_GROUP_ADD_LID_MIGRATION_ENABLE = new ABProp(16274, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PRIVACY_SETTINGS_PRESENCE_LID_MIGRATION_ENABLE = new ABProp(16275, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_PEER_SNAPSHOT_RECOVERY = new ABProp(16329, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_HISTORY_BUMP_MESSAGE_ID = new ABProp(16346, "200", "200");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp LIMIT_SHARING_UPDATE_ENABLED_WEB = new ABProp(16376, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BIZ_AI_PRIORITY_LIST_ENABLED = new ABProp(16420, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp VOIP_STACK_INCOMING_MESSAGE_OWNERSHIP_TRANSFER = new ABProp(16481, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_VIDEO_PLAY_LOGGING_ENABLED = new ABProp(16491, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_REWRITE_IN_EXPRESSION_TRAY_ENABLED = new ABProp(16510, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp RICH_ORDER_STATUS_WA_WEB = new ABProp(16534, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MEMBER_NAME_TAG_DB_ENABLED = new ABProp(16551, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PNH_THREAD_PROMOTION_TO_GENERAL_LID = new ABProp(16632, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_RICH_RESPONSE_FORWARD_SENDING_ENABLED = new ABProp(16681, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_RICH_RESPONSE_FORWARD_RECEIVING_ENABLED = new ABProp(16682, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MM_SIGNAL_SHARING_VERIFICATION_SYSTEM_LID_ENABLED = new ABProp(16727, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_INVITE_CONTACTS_TO_FOLLOW_PRODUCER_ENABLED = new ABProp(16789, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_INVITE_CONTACTS_TO_FOLLOW_CONSUMER_ENABLED = new ABProp(16790, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp REACTIONS_ALIGNMENT_FOR_TRANSPARENT_MESSAGES_ENABLED = new ABProp(16792, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_CONSOLE_LOG_LEVEL = new ABProp(16806, "3", "1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_PDF_THUMBNAIL_SIZE_IN_BYTES = new ABProp(16834, "1300", "1300");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_STICKER_FORWARDED_ATTRIBUTION_UI_ENABLED = new ABProp(16856, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_STICKER_PACK_FORWARDED_ATTRIBUTION_UI_ENABLED = new ABProp(16858, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENT_LINKS_TRUST_SIGNALS_METATAG_ENABLED = new ABProp(16866, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_REWRITE_STACK_UNDO_ENABLED = new ABProp(16943, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MM_OPT_OUT_LID_MIGRATION_ENABLED = new ABProp(16952, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_OFFLINE_RESUME_WAIT_FOR_PING_TIMEOUT_SECONDS = new ABProp(16956, "10", "10");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp VIEW_REPLIES_WITH_THREADID_ENABLED = new ABProp(16998, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp STATUS_ALLOW_FORWARDING_TO_STATUS_ON_WEB = new ABProp(17071, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_EMOJI_FORWARDED_ATTRIBUTION_UI_ENABLED = new ABProp(17081, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BIZ_AI_WEB_AI_HUB_TAP_CTA_SHOW_ALERT = new ABProp(17093, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENT_LINKS_TRUST_SIGNALS_METATAG_PSP_LIST = new ABProp(17162, "{\"psp\":[\"mercadopago\"]} ", "{\"psp\":[\"mercadopago\"]} ");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp HISTORY_SYNC_ON_DEMAND_COMPANION = new ABProp(17198, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_UGC_NOT_AN_EXPERT_ENABLED = new ABProp(17285, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENT_LINKS_TRUST_SIGNALS_OTHER_METATAGS_ENABLED = new ABProp(17355, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_RICH_RESPONSE_SIDE_BY_SIDE_SURVEY_ENABLED = new ABProp(17408, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_QUESTION_FOLLOWER_ENABLED = new ABProp(17425, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_QUESTION_ADMIN_ENABLED = new ABProp(17426, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_BUSINESS_BROADCAST_IMPORT_CONTACT = new ABProp(17433, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_HYBRID_SIMPLE_CHAT_CONVERSATION_CONTEXT_MENU_ENABLED = new ABProp(17479, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_RATING_AND_REVIEW_ENABLED = new ABProp(17540, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_SUPPRESS_MESSAGE_VIA_AD_SPAM_WEB = new ABProp(17580, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_QUESTIONS_INTEGRITY_M1_ENABLED = new ABProp(17600, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USERNAME_CONTACT_SYNCD_SUPPORT_ENABLE = new ABProp(17614, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MM_DATA_SHARING_DISCLOSURE_ON_CHAT_OPEN_ENABLED = new ABProp(17630, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp HYBRID_FONT_SIZE_DROPDOWN = new ABProp(17637, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_HYBRID_CONTEXT_MENU_REACTIONS_ENABLED = new ABProp(17650, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CALLS_TAB_USERNAME_GLOBAL_SEARCH_ENABLED = new ABProp(17698, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_OPEN_QPL_USER_RID_LOGGING_ENABLED = new ABProp(17712, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp HYBRID_NUX_BETA_50_ENABLED = new ABProp(17717, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_CALLS_TAB_EMPTY_STATE_BUTTONS = new ABProp(17724, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_CALLING_PHONE_NUMBER_PRIVACY = new ABProp(17731, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMBA_BUSINESS_BROADCAST_GENAI_TEXT = new ABProp(17743, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MESSAGE_EDIT_TO_MESSAGE_SECRET_RECEIVER_ENABLED = new ABProp(17811, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_INDIVIDUAL_NEW_CHAT_MSG_CAPPING_LIMIT = new ABProp(17845, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMBA_BUSINESS_BROADCAST_RECIPIENT_LIMIT = new ABProp(17937, "-1", "-1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMOOTHIE_PERFORMANCE_MSG_SEND = new ABProp(17942, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CALLING_RUST_MIGRATION_BITMAP = new ABProp(17954, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp META_AI_IN_APP_SURVEY_ENABLED = new ABProp(17956, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_METABOT_DOCUMENT_UPLOAD_ENABLED = new ABProp(17957, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ADVANCED_CHAT_PRIVACY_CONTENT_UPDATE_JULY_25 = new ABProp(18025, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp COEX_CALLING_ENABLED = new ABProp(18047, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp HYBRID_INCREMENTAL_ZOOMING_SIMPLE_ENABLED = new ABProp(18080, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_AVATARS_ON_WEB_COMPANION = new ABProp(18081, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PUSHNAME_BLOCKLIST_STARTING_WITH_AT = new ABProp(18097, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp INTERNAL_GROUP_INDICATOR = new ABProp(18109, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_AV_DOWNGRADE_1ON1 = new ABProp(18165, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USERNAME_CONTACT_UI_VCARD = new ABProp(18204, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp LISTS_SMB_ENABLED = new ABProp(18229, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp KMP_SYNCD_ENGINE_OUTGOING_PROCESSOR_ENABLED = new ABProp(18234, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USERNAME_GLOBAL_SEARCH_ENABLED = new ABProp(18251, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_FORWARD_ATTRIBUTION_ENABLED = new ABProp(18286, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp STATUS_POG_ID_ROTATION_WINDOW_DAYS = new ABProp(18297, "-1", "-1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_REWRITE_NON_STREAMING_ENABLED = new ABProp(18316, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp HISTORY_SYNC_ON_DEMAND_TIME_BOUNDARY_DAYS_DESKTOPS = new ABProp(18391, "1095", "1095");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_QUESTION_REPLY_RECEIVER_MESSAGE_TYPES_M1_ENABLED = new ABProp(18393, "", "25");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_QUESTION_REPLY_SENDER_MESSAGE_TYPES_M1_ENABLED = new ABProp(18394, "", "22");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_HISTORY_MESSAGE_COUNT_LIMIT = new ABProp(18405, "100", "100");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_HISTORY_MESSAGES_TIME_LIMIT_SECS = new ABProp(18406, "1209600", "1209600");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_NEW_USER_ACTION_STANZA_FOR_RAISE_HAND_SENDER = new ABProp(18489, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_ADMIN_NOTIFICATIONS_ENABLED = new ABProp(18560, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_CREATION_ENTRYPOINT_IN_DIRECTORY_ENABLED = new ABProp(18613, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_DISABLE_LOGS_LOW_END_DEVICE = new ABProp(18660, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DIALER_PAD_FOR_NEW_CHATS = new ABProp(18688, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp RNR_MIN_DAYS_USER_ACTIVE = new ABProp(18702, "2", "2");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp RNR_DAYS_COOLDOWN = new ABProp(18703, "100000", "100000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp OPTIMIZED_DELIVERY_BLOCK_AND_REPORT_ENTRY_POINTS_ALLOWLIST_WEB = new ABProp(18736, "4,10,12,13,14,15,17,18,24,31,32,33,34,35,36,39,40,45", "4,10,12,13,14,15,17,18,24,31,32,33,34,35,36,39,40,45");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_RATING_AND_REVIEW_CONTEXTUAL_PROMPT_ENABLED = new ABProp(18737, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_SEARCH_EXPERIENCE_WEB_ENABLED = new ABProp(18740, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_RICH_RESPONSE_UR_MEDIA_GRID_ENABLED = new ABProp(18746, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_LOW_END_DEVICE_LEVEL = new ABProp(18747, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SNAPSHOT_RECOVERY_MAX_MUTATIONS_COUNT_ALLOWED = new ABProp(18786, "2000", "2000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_FALCO_CLEAR_LOCAL_STORAGE_QUEUE_ENABLED = new ABProp(18835, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_MIGRATE_AWAY_FROM_INLINE_TOS_ENABLED = new ABProp(18843, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_NATIVE_ADS_CREATION_WEB_ENABLED = new ABProp(18857, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CUSTOM_NOTIFICATION_TONES = new ABProp(18884, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_CREATION_ENTRYPOINT_IN_UPDATES_TAB_ENABLED = new ABProp(18925, "0", "1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USERNAME_CHECK_DEBOUNCE_IN_MS = new ABProp(18975, "600", "600");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_QUESTION_FETCH_RESPONSES_PAGE_SIZE = new ABProp(18984, "30", "30");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_QUESTION_FORWARD_MESSAGE_TYPES_CHAT_M1_ENABLED = new ABProp(18988, "", "22");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMOOTHIE_PERFORMANCE_RESIZE_FOLLOWUP = new ABProp(18992, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp COEX_EDIT_MSG_ENABLED = new ABProp(19039, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_REPLY_FORWARD_MESSAGE_TYPES_CHAT_M1_ENABLED = new ABProp(19053, "", "25");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp UTILITY_ORDER_STATUS_LOGGING_ENABLED = new ABProp(19059, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_HISTORY_SYNC_DYNAMIC_THROTTLING = new ABProp(19110, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PUBLIC_BUG_REPORTING_SIDEBAR = new ABProp(19124, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_NOTIFICATIONS_BANNER_VARIANT = new ABProp(19168, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DM_RECEIVER_ALLOWED_VALUES = new ABProp(19232, "{\"timers\": [0, 86400, 604800, 7776000]}", "{\"timers\": [0, 86400, 604800, 7776000]}");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENTS_BR_PIX_WEB_ATTACHMENT_TRAY = new ABProp(19276, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp COEX_REVOKE_MESSAGE_ENABLED = new ABProp(19285, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHATLIST_SHOW_DRAFT_FOR_EMPTY_CHAT = new ABProp(19287, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ANR_THROTTLE_HISTORY_SYNC_DB_WRITES = new ABProp(19298, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp NEWSLETTER_RCAT_FIELD_GENERATING_ENABLED = new ABProp(19303, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_NOTIFICATIONS_BANNER_NEW_LOGIC_ENABLED = new ABProp(19399, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENT_LINK_TRACE_ID_LOGGING_ENABLED = new ABProp(19440, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp HYBRID_FLYTRAP_FEEDBACK_ENABLED = new ABProp(19495, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DESKTOP_UPSELL_INTRO_PANEL_ILLUSTRATION_VARIANT = new ABProp(19518, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_QPL_IMPROVEMENTS_SUPPORTED_TYPES = new ABProp(19589, "", "1,2");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_RICH_RESPONSE_FORWARDING_VERIFICATION_ENABLED_V1 = new ABProp(19590, "\"none\"", "\"none\"");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp VOICE_AI_CONVERSATION_STARTER_LATENCY_TRACKING = new ABProp(19624, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_WEB_FORWARD_FLOW_ENABLED = new ABProp(19676, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp LID_STATUS_NON_SOAKED_CLIENT_SUPPORT_ENABLED = new ABProp(19696, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_HYBRID_GETTERS_CACHE_ENABLED = new ABProp(19700, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_PER_CUSTOMER_DATA_SHARING_CONTROLS_DO_NOT_SHOW_MSG_UNTIL_CHOSEN = new ABProp(19763, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_QUIZ_SENDING_ENABLED = new ABProp(19777, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_QUIZ_RECEIVING_ENABLED = new ABProp(19778, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_METABOT_DOCUMENT_UPLOAD_SIZE_LIMIT_MB = new ABProp(19823, "40", "40");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ENABLE_IMPROVED_BULK_MERGE = new ABProp(19854, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp VIEW_REPLIES_ENTRY_POINT = new ABProp(19860, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp NEWSLETTER_FORWARD_COUNTER_UI_ENABLED = new ABProp(19888, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp NEWSLETTER_FORWARD_COUNTER_INFRA_ENABLED = new ABProp(19889, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_FMX_LOGGING = new ABProp(19893, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_RATE_APP_PROMPT = new ABProp(19894, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_HYBRID_VIDEO_TRANSCODING = new ABProp(19895, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_CHANNEL_VIDEO_SERVER_TRANSCODE_UPLOAD = new ABProp(19920, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_CORE_BIZ_PROFILE_UX_REFRESHED = new ABProp(19929, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_WEB_CUSTOM_LABEL_SIGNALS_ENABLED = new ABProp(19985, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_METABOT_DOCUMENT_UPLOAD_PAGE_COUNT_LIMIT = new ABProp(19987, "100000", "100000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_QUESTION_RESPONSE_RATE_LIMIT_MAX_COUNT_IN_CLIENT_UI = new ABProp(19989, "5", "5");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_FIX_DUPLICATED_LIDS_HISTORY_SYNC = new ABProp(19994, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_UGC_HIDE_ENABLED = new ABProp(20041, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_APP_LOCK_UPSELL = new ABProp(20064, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_HYBRID_VIDEO_TRANSCODING_FOR_VALID_MP4 = new ABProp(20070, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BIZ_AI_AGENT_THREAD_STATUS_HISTORY_SYNC_ENABLED = new ABProp(20099, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_STICKER_PACK_RENDERING = new ABProp(20182, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNEL_STICKER_PACK_FORWARDING = new ABProp(20212, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_SMB_DETECTED_OUTCOME_LISTS_ENABLED = new ABProp(20220, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_MUSIC_RECEIVER_ENABLED = new ABProp(20266, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ADMIN_ONLY_MENTION_EVERYONE_GROUP_SIZE = new ABProp(20354, "33", "33");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_USE_KALEIDOSCOPE_MEDIA_CHECK_ENABLED = new ABProp(20375, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MM_USER_CONTROLS_ENTRY_POINTS_UPDATE_M1_ICON = new ABProp(20388, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_NATIVE_ADS_CREATION_WEB_HAWK_TOOL_ENABLED = new ABProp(20442, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMBA_BUSINESS_BROADCAST_GENAI_CUSTOM_USER_PROMPT_ENABLED = new ABProp(20464, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_WEB_META_AI_IMAGE_INPUT_ENABLED = new ABProp(20522, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PENDING_GROUP_REQUESTS_PERSISTENT_BANNER = new ABProp(20545, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNEL_ENFORCEMENT_LOGGING_ENABLED = new ABProp(20549, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_PINNING_NUDGE_ENABLED = new ABProp(20551, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEBTP_USE_THUMBNAIL_RENDERER = new ABProp(20555, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_VOIP_VIDEO_RENDERER = new ABProp(20573, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_WEB_META_AI_PDF_DOCUMENT_INPUT_ENABLED = new ABProp(20581, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_INDIVIDUAL_NEW_CHAT_MSG_LATEST_RAMPUP_DATE = new ABProp(20601, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEBTP_USE_PDF_RENDERER = new ABProp(20607, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_CLEAR_SELECTED_CHATS_ENABLED = new ABProp(20626, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_CHAT_THREADS_INFRA_ENABLED = new ABProp(20652, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_NATIVE_ADS_CREATION_WEB_TARGETING_MODAL_HAWK_TOOL_ENABLED = new ABProp(20731, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PROFILE_SCRAPING_PRIVACY_TOKEN_IN_ABOUT_USYNC = new ABProp(20798, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp FAVORITE_STICKER_SYNC_AFTER_PAIRING_ENABLED_WEB = new ABProp(20815, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp VIEW_REPLIES_IS_COMPOSER_ENABLED = new ABProp(20817, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BIZ_AI_TOS_VARIANT = new ABProp(20833, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_INVITE_CONTACTS_TO_FOLLOW_RECEIVER_LOGGING_ENABLED = new ABProp(20836, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_INVITE_CONTACTS_TO_FOLLOW_SENDER_LOGGING_ENABLED = new ABProp(20837, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp HIDE_AUTO_QUOTES_ON_WEB = new ABProp(20892, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_REWRITE_LOAD_MORE_ENABLED = new ABProp(20918, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_WEB_GROUP_CALLING = new ABProp(20924, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMBA_BUSINESS_BROADCAST_GENAI_SHARE_MESSAGE_HISTORY = new ABProp(20926, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMBA_BUSINESS_BROADCAST_GENAI_TEXT_MODEL = new ABProp(20929, "LLAMA", "LLAMA");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMBA_BUSINESS_BROADCAST_GENAI_TEXT_MAX_TRIES = new ABProp(20946, "30", "30");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WDS_WEB_CHIP = new ABProp(20970, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USERNAME_CONTACT_PRIVACY_SETTING_ALLOW_UNCONTACT_SET_ENABLE = new ABProp(20993, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_THREADS_INFRA_ENABLED = new ABProp(21062, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DSA_21_CHANNEL_REPORTING_ENABLED = new ABProp(21073, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_REWRITE_LANGUAGES_AND_TONES_CONFIG = new ABProp(21139, "{}", "{\"en\": \"rephrase,professional,funny,supportive,proofread\"}");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHATLIST_PREVENT_AUTOREAD = new ABProp(21156, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_SHARING_FILES_FROM_WEB_WINDOWS_HYBRID = new ABProp(21184, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAA_SUPPORT_FOR_DISABLED_EPEHEMERALITY = new ABProp(21235, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_NAVIGATION_BAR_UPDATES_TAB = new ABProp(21250, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_HISTORY_SETTINGS = new ABProp(21261, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MM_DATA_SHARING_DISCLOSURE_ENABLED_COMPANION_HISTORY_SYNC = new ABProp(21288, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_HISTORY_MESSAGES_TIME_LIMIT_RECEIVER_ENFORCEMENT_SECS = new ABProp(21313, "1209600", "1209600");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_VOIP_VIDEO_CAPTURE_IMPL = new ABProp(21350, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMBA_BB_GENAI_COMPOSER_MIN_WORDS = new ABProp(21447, "4", "4");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_HISTORY_SETTINGS_TOGGLE_UI = new ABProp(21481, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_STATUS_CROSSPOSTING_ENABLED = new ABProp(21501, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_BUSINESS_BROADCAST_SEND_WEB = new ABProp(21508, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_CONTINUOUS_SESSION_TRANSPARENCY_NOTICE_ENABLED = new ABProp(21510, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp UNIFIED_CALLING_ENTRY_POINT_DESKTOP_TYPE = new ABProp(21591, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_WAITING_ROOM_ADMIN_UI = new ABProp(21676, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_VOIP_AUDIO_CAPTURE_IMPL = new ABProp(21688, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_VOIP_AUDIO_PLAYBACK_IMPL = new ABProp(21689, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp RT_SWAPPED_FALLBACK_VALIDATION = new ABProp(21718, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAVOIP_ML_MEDIA_AUTOMOS_MODEL_DOWNLOAD_VERSIONS = new ABProp(21731, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAVOIP_ML_BWE_CONG_MODEL_DOWNLOAD_VERSIONS = new ABProp(21732, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAVOIP_ML_BWE_RL_MODEL_DOWNLOAD_VERSIONS = new ABProp(21733, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAVOIP_ML_BWE_TR_MODEL_DOWNLOAD_VERSIONS = new ABProp(21734, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAVOIP_ML_MEDIA_VSR_MODEL_DOWNLOAD_VERSIONS = new ABProp(21735, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAVOIP_ML_MEDIA_VMOS_MODEL_DOWNLOAD_VERSIONS = new ABProp(21736, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAVOIP_ML_MEDIA_NS_MODEL_DOWNLOAD_VERSIONS = new ABProp(21737, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAVOIP_ML_BWE_HD_TARGET_MODEL_DOWNLOAD_VERSIONS = new ABProp(21738, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BR_PAYMENTS_PIX_GROUPS_ENABLED = new ABProp(21741, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_WAE_QPL_ENABLED = new ABProp(21742, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MM_OPTIMIZED_DELIVERY_REPLACING_SHIMMED_LINKS_ENABLED = new ABProp(21782, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp FUNCTIONAL_CHATLIST_ENABLED = new ABProp(21799, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAVOIP_ML_TEMP_MODEL_DOWNLOAD_VERSIONS = new ABProp(21815, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_SUPPRESS_MESSAGE_WITH_EXTERNAL_AD_REPLY_CONSUMER_DB_LEVEL_ENABLED = new ABProp(21819, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAVOIP_ML_BWE_GC_UNDERSHOOT_MODEL_DOWNLOAD_VERSIONS = new ABProp(21821, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAVOIP_ML_BWE_GC_HD_TARGET_MODEL_DOWNLOAD_VERSIONS = new ABProp(21822, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_ENABLE_GRANULAR_NOTIFICATIONS = new ABProp(21909, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_DISABLE_PREFETCH_LOADABLES = new ABProp(21917, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USERNAME_SUGGESTIONS_ENABLED = new ABProp(21984, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_AGM_FLOW_CTA = new ABProp(22006, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_REPLY_MESSAGE_CONTEXT_MAX_COUNT = new ABProp(22024, "20", "20");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_REPLY_MESSAGE_CONTEXT_TRIGGER_MIN_COUNT = new ABProp(22025, "10", "10");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_CHAT_THREAD_CAPABILITY_ENABLED = new ABProp(22038, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_CHAT_THREADS_HISTORICAL_MESSAGES_MIGRATION_ENABLED = new ABProp(22070, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_LISTS_M2_ENABLED = new ABProp(22086, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_MUSIC_FORWARDING_DISABLED = new ABProp(22089, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_LISTS_M1_ENABLED = new ABProp(22090, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_CACHE_OPEN_FAILED_RELOAD_FLOW_ENABLED = new ABProp(22155, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_GROUP_PARTICIPATION_ENABLED = new ABProp(22171, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_GROUP_PARTICIPATION_SEND_ENABLED = new ABProp(22184, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_CALLING_PERF_OPTIMIZATIONS_BITMASK = new ABProp(22186, "1", "1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_INVITE_LINK_PREVIEW_IMPROVEMENT_ENABLED = new ABProp(22196, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp NEWSLETTER_FORWARD_COUNTER_BUMP_OWN_CHANNEL_UPDATES_FOWARDS = new ABProp(22203, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp NEWSLETTER_FORWARD_COUNTER_BUMP_FORWARDS_TO_SELF = new ABProp(22204, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp NEWSLETTER_FORWARD_COUNTER_BUMP_SECOND_ORDER_FORWARDS = new ABProp(22205, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp NEWSLETTER_FORWARD_COUNTER_MAX_SEND_AFTER_RANDOM_TIME = new ABProp(22206, "3600", "60");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp IS_SPOILER_RICH_FORMAT_ENABLED = new ABProp(22221, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_GROUP_PARTICIPATION_ADD_TEE_ENABLED = new ABProp(22236, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BR_PAYMENTS_HOME_DURATION_RULE_FOR_PUX_BANNER = new ABProp(22249, "604800", "604800");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_INVITE_CONTACTS_TO_FOLLOW_RECEIVER_INVALID_MESSAGE_DROP_ENDABLED = new ABProp(22280, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_METABOT_DOCUMENT_OCR_IMAGE_CONVERSION_ENABLED = new ABProp(22301, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_FUTUREPROOF_GALAXY_FLOW_MESSAGE_FOR_BUSINESS_NUMBERS = new ABProp(22311, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_ADMIN_PROFILES_SENDER_ENABLED = new ABProp(22316, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_ADMIN_PROFILES_RECEIVER_ENABLED = new ABProp(22318, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMBA_BUSINESS_BROADCAST_GENAI_MASTER_ABPROP = new ABProp(22384, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp UTILITY_PAYMENT_REMINDER_M1_ENABLED = new ABProp(22434, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_REACTIONS_2 = new ABProp(22469, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_CONTEXTUAL_WRITING_HELP_ENABLED = new ABProp(22488, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DSA_26_RECEIVER_ENABLED = new ABProp(22515, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DSA_26_SENDER_ENABLED = new ABProp(22516, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MEDIA_HUB_HISTORY_MAX_DAYS = new ABProp(22518, "14", "14");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp VID_PORT_FRM_BUF_MUTEX_FIXES = new ABProp(22525, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_CORE_BIZ_PROFILE_UX_REFRESHED_V2 = new ABProp(22561, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_SHOW_STATUS_RING_FOR_NO_UNREAD = new ABProp(22567, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_PHONE_NUMBER_GLOBAL_SEARCH = new ABProp(22603, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_CREATE_GROUP_IN_FILTER = new ABProp(22617, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MEMBER_NAME_TAG_WEB_SENDER_ENABLED = new ABProp(22654, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MEMBER_NAME_TAG_WEB_RECEIVER_ENABLED = new ABProp(22655, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_RICH_RESPONSE_POST_CITATIONS_ENABLED = new ABProp(22672, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SETTINGS_SYNC_ENABLED = new ABProp(22692, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_RICH_RESPONSE_ZEITGEIST_CAROUSEL_ENABLED = new ABProp(22750, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_CONTEXTUAL_WRITING_HELP_NUM_SUGGESTIONS = new ABProp(22759, "4", "4");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MM_OPTIMIZED_DELIVERY_APP_CTA_ENABLED = new ABProp(22776, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_MEDIA_IMAGE_UPLOAD_CACHE = new ABProp(22784, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_IMAGINE_LOADING_INDICATOR_ENABLED = new ABProp(22795, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_CONTEXTUAL_WRITING_HELP_LANGUAGES_AND_TONES_CONFIG = new ABProp(22797, "{}", "{\"en\": \"auto,professional,funny,supportive\"}");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_SHARE_CONTENT_UJ = new ABProp(22813, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MESSAGE_KEYS_ASYNC_CHUNK_SIZE = new ABProp(22815, "50", "50");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SYNCED_MESSAGE_KEYS_PROCESSING_TYPE = new ABProp(22825, "control", "control");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_FAVICON_BADGING_ENABLED = new ABProp(22924, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ANR_FILE_SIZE_THRESHOLD_TO_USE_WORKER_MB = new ABProp(22930, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ANR_MEDIA_CHUNK_ENC_DELAY_ENABLED = new ABProp(22931, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp EMPTY_UNREAD_FILTER_CTA_VARIANT = new ABProp(22962, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAWEB_CHATINFO_REFRESH = new ABProp(23018, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_GRAPHQL_MERCHANT_INFO_SET_COMPLIANCE = new ABProp(23026, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_GRAPHQL_MERCHANT_INFO_GET_COMPLIANCE = new ABProp(23027, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BR_SMB_PAYMENTSHOME_ENABLED = new ABProp(23042, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_VOIP_LOAD_WASM_VARIANT = new ABProp(23045, "prod-nonlab", "prod-nonlab");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_CHAT_THREADS_WEB_ENABLED = new ABProp(23169, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_ADMIN_PROFILES_FORWARDING_TO_CHATS_ENABLED = new ABProp(23170, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_ADMIN_PROFILES_LIST_ENABLED = new ABProp(23174, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_SESSION_TRANSPARENCY_META_AI_ENABLED = new ABProp(23188, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ANR_ASYNC_MEDIA_DECRYPTION_ENABLED = new ABProp(23200, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AURA_ENABLED = new ABProp(23270, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AURA_APP_THEMES_BENEFIT_ACTIVE = new ABProp(23273, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AURA_APP_THEMES_ENABLED = new ABProp(23274, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AURA_PINNED_CHATS_ENABLED = new ABProp(23277, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AURA_PINNED_CHATS_BENEFIT_ACTIVE = new ABProp(23278, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp IMP_SEND_SIGNAL_POST_CONNECT_WEBC_ENABLED = new ABProp(23322, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp IMP_SEND_SIGNAL_POST_CONNECT_DELAY = new ABProp(23323, "500", "500");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SAGA_MESSAGE_FEEDBACK_USING_CANONICAL_ENT = new ABProp(23328, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_UNIFIED_RESPONSE_SENDER_WEB_ENABLED = new ABProp(23347, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_UNIFIED_RESPONSE_RECEIVER_WEB_ENABLED = new ABProp(23348, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MEX_GET_PRIVACY_SETTINGS_MODE = new ABProp(23463, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp COEX_CALLING_PERMISSIONS_3P_ENABLED = new ABProp(23464, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WDS_WEB_TOAST = new ABProp(23486, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEBTP_USE_PDF_EDITOR = new ABProp(23498, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_AI_GROUP_OPEN_SUPPORT = new ABProp(23530, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BOOKING_CONFIRMATION_ENABLED_WA_WEB = new ABProp(23559, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_HYBRID_APPLY_LATEST_DB_SCHEMA_OPTIMIZATION_ENABLED = new ABProp(23595, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNEL_VIEWS_VPV_DEFINITION_ENABLED = new ABProp(23616, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AIGC_VERSION = new ABProp(23692, "1", "1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_CHAT_THREADS_WEB_MSGS_LOAD_LIMIT = new ABProp(23694, "50", "50");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_CHATPSA_FORWARDING = new ABProp(23695, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_WEB_ASK_META_AI_ENABLED = new ABProp(23725, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNEL_ENFORCEMENT_POLICY_EDUCATION_ENABLED = new ABProp(23745, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_VOIP_DYNAMIC_THREAD_PREALLOCATE_COUNT = new ABProp(23789, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USERNAME_CHANNELS_PN_PRIVACY_ENABLED = new ABProp(23795, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp TOP_LEVEL_MESSAGE_SECRET_CHECK = new ABProp(23796, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_ALBUM_RECEIVER_ENABLED = new ABProp(23809, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USERNAME_ENABLED_ON_COMPANION = new ABProp(23817, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_RICH_RESPONSE_INLINE_LINKS_ENABLED = new ABProp(23819, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DISABLE_LIBAOM_REGISTRATION = new ABProp(23836, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SCHEDULED_MESSAGES_SENDER_ENABLED = new ABProp(23845, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMBI_PREMIUM_BROADCAST_MAX_RECIPIENT_LIMIT = new ABProp(23857, "256", "500");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_ALBUM_SENDER_ENABLED = new ABProp(23859, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MEX_GET_PRIVACY_CONTACT_LIST_ENABLED = new ABProp(23874, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BIZ_AI_CONSUMER_TOS_UPDATE_WEB = new ABProp(23880, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_MODE_SELECTOR_ENABLED = new ABProp(23885, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PIX_ONBOARDING_NEW_CONTENT_ENABLED = new ABProp(23953, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNEL_STATUS_CREATION = new ABProp(23994, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNEL_STATUS_CONSUMPTION = new ABProp(23995, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_QUESTIONS_SEARCH_ENABLED = new ABProp(24004, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_FROM_GROUP = new ABProp(24024, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AURA_RINGTONES_ENABLED = new ABProp(24047, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AURA_RINGTONES_BENEFIT_ACTIVE = new ABProp(24050, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_UNIFIED_RESPONSE_IMAGINE_RECEIVER_WEB_ENABLED = new ABProp(24109, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp LISTS_CHAT_LIST_ROW_PILL_ENABLED = new ABProp(24133, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_HISTORY_SYNC_WORKER_ENABLED = new ABProp(24147, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BUG_REPORTING_USING_GRAPHQL = new ABProp(24161, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAVOIP_ML_TRANSPORT_DOWNLOAD_VERSIONS = new ABProp(24173, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAVOIP_ML_NADL_MODEL_DOWNLOAD_VERSIONS = new ABProp(24174, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp IS_SPOILER_RICH_FORMAT_SENDER_ENABLED = new ABProp(24210, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp FLOWS_WA_WEB_AGM_CTA = new ABProp(24215, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp FLOWS_WA_WEB_RESPONSES_DOWNLOAD = new ABProp(24216, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_MENTION_EVERYONE_SYNCD_SENDER = new ABProp(24244, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_DISPLAY_LID_CONTACTS = new ABProp(24280, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_LABEL_SYNC_CRITICAL_EVENT_LOGGING = new ABProp(24311, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_FORCE_LID_CHATS_IN_HISTORY = new ABProp(24343, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_GROUP_SEND_MENTIONED_PUSHNAME_ENABLED = new ABProp(24361, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_LOG_CAPACITY_OVERRIDE = new ABProp(24363, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_CONSUMER_ENTRY_POINT_ENABLED = new ABProp(24380, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ATTACH_MENU_ADD_DRAWING_ENABLED = new ABProp(24384, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BR_SMB_PIX_PAYMENT_REQUEST_VARIANT = new ABProp(24388, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_HORIZONTAL_LINK_PREVIEWS = new ABProp(24425, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_ENABLE_FOLLOW_UP_REPLY_ICON = new ABProp(24429, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_ANYONE_CAN_LINK_M2 = new ABProp(24432, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_UNIFIED_RESPONSE_QPL_LOGGING = new ABProp(24484, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp IS_AI_MODE_SELECTOR_VISIBLE = new ABProp(24489, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_CONSUMER_NOVA_ENTRY_POINT_SETTINGS_ENABLED = new ABProp(24495, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_WEB_CALLING_NUX = new ABProp(24504, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_REDUCE_FORCED_LAYOUT_CHAT_OPEN = new ABProp(24526, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_CHANNELS_COMET_VIDEO_PLAYER_ENABLED_V2 = new ABProp(24541, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_GLOBAL_SEARCH_PREFIX_BASED = new ABProp(24559, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_MULTI_PPL_TYPING_INDICATOR_FOR_CHATLIST_GROUPS_VARIANT = new ABProp(24560, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_MEMBER_UPDATES_HIDE_IN_THREAD_ENABLED = new ABProp(24584, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CALLING_AV_SYNC_WEBRTC = new ABProp(24599, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SCHEDULED_MESSAGES_RECEIVER_ENABLED = new ABProp(24610, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_MEMBER_UPDATES_USERNAMES_ENABLED = new ABProp(24617, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_GROUP_CALL_MAX_VERSION_BY_PLATFORM = new ABProp(24655, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_GROUP_CALL_MAX_VERSION_BY_COUNTRY = new ABProp(24656, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENT_LINKS_TRUST_SIGNALS_OTHER_METATAG_KILL_SWITCH_ENABLED = new ABProp(24662, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_WEB_NATIVE_ADS_MVP_QE1_ENABLED = new ABProp(24668, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_WEB_NATIVE_ADS_MVP_QE2_ENABLED = new ABProp(24669, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp LISTS_SMB_WEB_ENABLED = new ABProp(24732, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp RT_GHS_SENDER_ENABLED = new ABProp(24741, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp RT_GHS_RECEIVER_ENABLED = new ABProp(24742, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BIZ_AI_CONSUMER_TOS_NOTICE_IQ_WEB = new ABProp(24754, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_WEB_NATIVE_ADS_MVP_QE1_ENABLED_NO_EXPOSURE = new ABProp(24761, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_CONTACT_SEARCH_TOKENIZED_ENABLED = new ABProp(24773, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_WINDOWS_XDR_CHAT_HANDOFF = new ABProp(24783, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_STATUS_COMET_VIDEO_PLAYER_ENABLED = new ABProp(24791, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AURA_STICKERS_ENABLED = new ABProp(24800, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AURA_STICKERS_BENEFIT_ACTIVE = new ABProp(24801, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_WEB_CALLING_BETA_UPSELL = new ABProp(24812, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_REQUEST_MISSING_KEYS_FOR_REMOVES = new ABProp(24838, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_MENTION_EVERYONE_RECEIVER_WEB = new ABProp(24843, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_MENTION_EVERYONE_SENDER_WEB = new ABProp(24844, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENHANCED_MENTION_SUGGESTIONS_NON_GROUP_MEMBERS_ENABLED = new ABProp(24852, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CCI_COMPLIANCE_MM = new ABProp(24853, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_BULK_ADD_CONTACTS_ENABLED = new ABProp(24875, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp POLL_END_TIME_RECEIVING_ENABLED = new ABProp(24884, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp POLL_HIDE_VOTERS_RECEIVING_ENABLED = new ABProp(24885, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp POLL_CREATOR_EDIT_RECEIVING_VERSION = new ABProp(24886, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_VIDEO_COMET_VIDEO_PLAYER_ENABLED = new ABProp(24905, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_NCT_TOKEN_SALT_CREATION_ENABLED = new ABProp(24915, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_WORKER_ADV_PROCESSING_ENABLED = new ABProp(24924, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_NCT_TOKEN_SEND_ENABLED = new ABProp(24941, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_ME_TAB = new ABProp(24944, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_SELF_PROFILE_PHOTO_FIX_ENABLED = new ABProp(24945, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_CONSUMER_NOVA_SETTINGS_GREEN_DOT_ENABLED = new ABProp(24955, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DEFENSE_MODE_QUARANTINE = new ABProp(24959, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CCI_COMPLIANCE_CTWA = new ABProp(24983, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_3PD_OPT_OUT_COUNTER_OPTIMIZATION_ENABLED = new ABProp(24984, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_WAITING_ROOM_LOGGING = new ABProp(24991, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_REDUCE_CASCADING_UPDATES_CHAT_OPEN = new ABProp(25006, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_ANYONE_CAN_LINK_M2_FLOOD_LIMIT = new ABProp(25009, "10", "10");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_STATUS_FIRST_UPLOAD_FIX_ENABLED = new ABProp(25015, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MM_DISCLOSURE_LEARN_MORE_ARTICLE_ID = new ABProp(25021, "263784176043634", "263784176043634");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp STATUS_INFRA_1_1_SESSION_SPLIT = new ABProp(25034, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_COMET_VIDEO_PLAYER_SNAPL = new ABProp(25065, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp IM_BLOKS_WIDGET_ENABLE = new ABProp(25071, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_T_ENABLED = new ABProp(25078, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_ENABLE_STATUS_HQ_THUMBNAIL = new ABProp(25079, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_FILE_UPLOAD_SUPPORTED_FILE_TYPES = new ABProp(25090, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_FILE_UPLOAD_COUNT_LIMIT = new ABProp(25093, "0", "1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_BOT_INTEGRATION_ENABLED = new ABProp(25119, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WDS_WEB_EXPRESSIONS_PANEL = new ABProp(25144, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_LOGGING_QBM_INCOMING_MESSAGE = new ABProp(25149, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_STATUS_VIEWER_SIDE_POSTER_IDENTIFIERS_ENABLED = new ABProp(25151, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_WEB_VOIP_PLATFORM_AV_SYNC = new ABProp(25177, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_SMB_LABEL_CHAT_HEADER_ENABLED_WEB = new ABProp(25180, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_NCT_TOKEN_HISTORY_SYNC_ENABLED = new ABProp(25189, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_BUSINESS_BROADCAST_MULTI_AUDIENCE_SEND_WEB = new ABProp(25206, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AURA_STICKERS_OVERLAY_ANIMATION_ENABLED = new ABProp(25210, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_NCT_TOKEN_SYNCD_ENABLED = new ABProp(25253, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ACP_REMOVAL = new ABProp(25255, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_BOT_INTEGRATION_BOT_PROFILE = new ABProp(25268, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_DYNAMIC_MODE_SELECTOR_ENABLED = new ABProp(25287, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_GROUP_INFO_NOTIFICATION_ROW = new ABProp(25292, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_WAM_FALCO_MODE = new ABProp(25306, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_WAM_FALCO_SHADOW_EVENT_IDS = new ABProp(25309, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_SEARCH_EMPTY_STATE_M1 = new ABProp(25310, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_UR_IMAGINE_VIDEO_ENABLED = new ABProp(25329, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_IMAGINE_UR_ENABLED = new ABProp(25331, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_UR_BLOKS_ENABLED = new ABProp(25332, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WDS_WEB_SUBMENUS = new ABProp(25351, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USERNAME_EXPOSED_LOGGING_ENABLED = new ABProp(25353, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CCI_COMPLIANCE_CTWA_LEARN_MORE_HYPERLINK = new ABProp(25366, "https://faq.whatsapp.com/785493319976156/", "https://faq.whatsapp.com/785493319976156/");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_CONSUMER_NOVA_ELIGIBILITY_SUBSCRIPTION_STATUS_CHECK_ENABLED = new ABProp(25388, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_WEB_VOIP_DYNAMIC_FPS_THROTTLE = new ABProp(25394, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_HIGHLIGHT_ME_MENTION = new ABProp(25408, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ANR_BATCH_AND_QUEUE_BULK_CONTACTS_DB_WRITES_ENABLED = new ABProp(25413, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_GROUP_EXPERIMENTATION_ENABLE = new ABProp(25414, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MM_DATA_SHARING_DISCLOSURE_ENABLED_ADDITIONAL_TRANSPARENCY_LARGE_SCREENS = new ABProp(25421, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_DEFAULT_PROFILE_PICS = new ABProp(25455, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BIZ_VPV_IMPRESSION_LOGGING_ENABLED = new ABProp(25465, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_CHAT_THREADS_PIN_ENABLED = new ABProp(25517, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_CHAT_THREADS_PIN_MAX_COUNT = new ABProp(25520, "3", "3");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_FILE_UPLOAD_SIZE_LIMIT_MB = new ABProp(25524, "40", "40");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_NOTIFY_FOR = new ABProp(25544, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BR_PAYMENTS_PAYMENT_REQUEST_CTA = new ABProp(25599, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_CHAT_SEARCH_ENTRYPOINT = new ABProp(25609, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_WEB_VOIP_P2P = new ABProp(25621, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp STICKER_STORE_TESTING_ENABLED = new ABProp(25639, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_MEDIA_COMPUTE_IN_WORKER_ENABLED = new ABProp(25641, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AFTER_READ_SENDING_ENABLED = new ABProp(25648, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AFTER_READ_RECEIVER_ENABLED = new ABProp(25649, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_BASE_VIDEO_COMET_VIDEO_PLAYER_ENABLED = new ABProp(25660, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_GROUP_DISCARD_DIALOG_CONTACT_THRESHOLD = new ABProp(25682, "-1", "2");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp POLL_ADD_OPTION_RECEIVING_ENABLED = new ABProp(25758, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USERNAME_KEY_UPSELL_MAX_NUMBERS = new ABProp(25789, "1", "1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USERNAME_KEY_UPSELL_MAX_CHARACTERS = new ABProp(25790, "8", "8");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_DYNAMIC_MODE_SELECTOR_TTL_SECONDS = new ABProp(25797, "86400", "86400");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_LISTS_FULL_WIDTH_FILTERS = new ABProp(25805, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_GROUPS_IN_COMMON_MULTI_CONTACT = new ABProp(25808, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_DATE_MARKER_CALENDAR_ENABLED = new ABProp(25811, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CONTACTS_FROM_COMMON_GROUPS_SECTION_ENABLED = new ABProp(25817, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNEL_US_NCII_REPORTING_ENABLED = new ABProp(25818, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_COPY_LINK_URL_ENABLED = new ABProp(25820, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_CALENDAR_MESSAGE_DENSITY_ENABLED = new ABProp(25823, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_HIGHLIGHT_ME_MENTION_GROUPSIZE_THRESHOLD = new ABProp(25836, "130", "130");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_BIZ_PROFILE_GRAPHQL_MIGRATION = new ABProp(25846, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_WEB_VOIP_VIDEO_RESOLUTION_CAP = new ABProp(25899, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_HISTORY_BUNDLE_TIME_LIMIT_RECEIVER_ENFORCEMENT_SECS = new ABProp(25910, "1209600", "1209600");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ANR_NOOP_GC_ENABLED = new ABProp(25915, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_UNIFIED_RESPONSE_RECEIVER_WEB_ENABLED_V2 = new ABProp(25929, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_UNIFIED_RESPONSE_RECEIVER_WEB_TIMESTAMP_V2 = new ABProp(25930, "1772082000", "1772082000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_PRELOAD_CONVERSATION_CHAT_OPEN = new ABProp(25937, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENHANCED_MENTION_LIMIT = new ABProp(25951, "5", "5");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ACP_REMOVAL_EPOCH_TIME = new ABProp(25993, "1782518400", "1782518400");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SUPPORT_CONTACT_FORM_USING_GRAPHQL = new ABProp(26001, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_WEB_VOIP_PROXY_AND_SCTP_WORKERS = new ABProp(26012, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp TRANSCODE_AND_REPAIR_VIDEOS = new ABProp(26027, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BR_PIX_KEY_BUBBLE_CONTENT_UPDATE = new ABProp(26033, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_HISTORY_OUT_OF_WINDOW_PIN_SENDER = new ABProp(26037, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_HISTORY_OUT_OF_WINDOW_PINS_RECEIVER = new ABProp(26039, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp TAPPABLE_LINKS_IN_POLL_OPTION_ENABLED = new ABProp(26062, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_WEBCODEC_VIDEO_ENCODE = new ABProp(26079, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AURA_SUBSCRIPTION_SIMULATION_ENABLED = new ABProp(26086, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_REACTIONS_MOTION_V2_ENABLED = new ABProp(26102, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp IMPROVE_GROUP_REPORTING = new ABProp(26114, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_FOLLOWER_INVITE_CREATION_MODAL_ENABLED = new ABProp(26120, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_WORKER_PREKEY_PROCESSING_ENABLED = new ABProp(26133, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAWEB_CROSSPOSTING_ATTRIBUTIONS = new ABProp(26138, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_FORWARD_COUNTER_ON_STATUS_CARD_ENABLED = new ABProp(26148, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_WEB_CUSTOMER_MANAGEMENT_ENABLED = new ABProp(26165, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_PDFN_NUX_AI_GROUP_TEE_DISCOVER_NOTICE_ID = new ABProp(26171, "20260212", "20260212");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GC_DEVICE_SWITCHING_KILLSWITCH = new ABProp(26182, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_HATCH_INTEGRATION_ENABLED = new ABProp(26189, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_HATCH_INTEGRATION_BOT_PROFILE = new ABProp(26190, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp IS_INDIVIDUAL_SUSPICIOUS_FMX_ENABLED = new ABProp(26191, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_THREAD_LOADING_INFRA_ENABLED = new ABProp(26192, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_WAM_FALCO_LOGGING_ENABLED = new ABProp(26200, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_EXPORT_CHAT = new ABProp(26201, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SUGGESTED_AUDIENCES_WA_WEB = new ABProp(26207, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_PNLESS_STANZAS = new ABProp(26211, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp NEWSLETTER_STATUS_RECEIVER_ENABLED = new ABProp(26217, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DM_RECEIVER_AFTER_READ_ALLOW_VALUES = new ABProp(26218, "{\"timers\": [0, 900]}", "{\"timers\": [0, 900]}");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USERNAME_KEY_UPSELL_MODE = new ABProp(26220, "0", "1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AFTER_READ_FALLBACK_DURATION = new ABProp(26225, "86400", "86400");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_ASTERIA_ENABLED = new ABProp(26234, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_CALLING_ENABLE_ON_WINDOWS = new ABProp(26259, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_SUSPENSION_APPEALS_REDESIGN_ENABLED = new ABProp(26276, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEBTP_EDIT_PDF_IN_WHATSAPP_ENABLED = new ABProp(26279, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MM_1PD_POST_DC_NEW_SCHEMA_ENABLED = new ABProp(26280, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MM_1PD_POST_DC_DEPTH_LIMIT = new ABProp(26281, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MM_1PD_POST_DC_OLD_SCHEMA_DISABLED = new ABProp(26282, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_CROSSPOST_SETTINGS_SYNC = new ABProp(26296, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_BLOCK_IB_AR_FOR_WABAI = new ABProp(26302, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BUG_REPORTING_ATTACH_VIEW_DUMP_PRE_BUG_CREATION = new ABProp(26307, "true", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BUG_REPORTING_ATTACH_PATHFINDER_PRE_BUG_CREATION = new ABProp(26311, "true", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_QP_CONVERSION_TRACKING_INFRA = new ABProp(26331, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CALLING_RUST_MIGRATION_INCOMING_STANZA_ENABLED = new ABProp(26338, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AURA_STATUS_SEARCH_ENABLED = new ABProp(26346, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SCHEDULED_MESSAGES_WINDOW_DURATION_MAX_SECONDS = new ABProp(26347, "1209600", "1209600");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SCHEDULED_MESSAGES_WINDOW_DURATION_MIN_SECONDS = new ABProp(26348, "600", "600");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_ATTACH_ICON_VARIANT = new ABProp(26386, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp INAPP_SIGNUP_CONFIRMATION_MESSAGE_ENABLED = new ABProp(26390, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_PHOTO_POLLS_GENAI_ENABLED = new ABProp(26392, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_ASTERIA_ELIGIBILITY_SUBSCRIPTION_STATUS_CHECK_ENABLED = new ABProp(26399, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CALLING_E2E_KEYGEN_VIA_SELF_LID = new ABProp(26411, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp NATIVE_LIB_SANDBOXING_ENABLE_LIBWEBP = new ABProp(26414, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BUSINESS_BROADCAST_CAMPAIGN_SYNCD_ENABLED = new ABProp(26426, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_OFFER_V2_UPGRADE = new ABProp(26435, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_CORE_BIZ_PROFILE_PREVIEW = new ABProp(26441, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_HATCH_INTEGRATION_HISTORY_SYNC_PRE_CHATD_ENABLED = new ABProp(26445, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_HISTORY_SEND_AFTER_JOIN = new ABProp(26451, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp STICKERS_EMOJI_TAGGING_ENABLED = new ABProp(26465, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_AGM_SIGNUP_ENABLED = new ABProp(26467, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_STATUS_LIKES_SEND_V2_ENABLED = new ABProp(26470, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_FEATURE_PARITY_SMALL_WINS = new ABProp(26481, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AUTH_AGENTS_CONSUMER_EXP_ENABLED = new ABProp(26492, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_HATCH_INTEGRATION_HISTORY_SYNC_ENABLED = new ABProp(26517, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_LEAD_TAXONOMY = new ABProp(26531, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AURA_STATUS_SEARCH_MAX_VIEWERS = new ABProp(26545, "1000", "1000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AURA_STATUS_SEARCH_TIMEOUT_THRESHOLD = new ABProp(26546, "5", "5");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp IS_EXPAND_FMX_ACCOUNT_AGE_UI_ENABLED = new ABProp(26548, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp IS_EXPAND_FMX_ACCOUNT_AGE_BOLDED_NON_AUTO_EXPOSE = new ABProp(26549, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp IS_EXPAND_FMX_MEX_ENABLED = new ABProp(26550, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp IS_EXPAND_FMX_ENABLED_NON_AUTO_EXPOSE = new ABProp(26551, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_PRE_CHAT_DEVICE_ID_TEST = new ABProp(26553, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_DOWNLOAD_MIMETYPE_CHECK_BLOCK_ENABLED = new ABProp(26555, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AURA_STICKERS_PREVIEW_MAX_ANIMATION_COUNT = new ABProp(26602, "5", "5");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_SHOW_HD_PHOTO = new ABProp(26610, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_BIZAI_2WAY_INTEGRATION_ENABLED = new ABProp(26613, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_BIZAI_2WAY_INTEGRATION_HISTORY_SYNC_PRE_CHATD_ENABLED = new ABProp(26614, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_CHAT_THEMES = new ABProp(26629, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAWEB_STATUS_CLOSE_FRIENDS_VIEWER_SIDE_ENABLED = new ABProp(26659, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp NEWSLETTER_STATUS_CREATION_ENABLED = new ABProp(26669, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BIZ_AI_RESPONDING_LIST_ENABLED = new ABProp(26670, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp INTERACTIVE_BLOKS_WIDGET_WEB_ENABLED = new ABProp(26685, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_SMB_MULTISELECT_ENABLED = new ABProp(26719, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_CONTACT_AND_CHAT_FUZZY_SEARCH_ENABLED = new ABProp(26728, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_CONTACT_AND_CHAT_FUZZY_SEARCH_SIMILARITY_OPTIMIZATION_ENABLED = new ABProp(26729, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_CONTACT_AND_CHAT_FUZZY_SEARCH_DISTANCE_THRESHOLD = new ABProp(26731, "0.30000001192092896", "0.30000001192092896");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_CONTACT_AND_CHAT_FUZZY_SEARCH_TIMEOUT_THRESHOLD = new ABProp(26733, "5", "5");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp VOIP_ENABLE_WEBRTC_STATS_POLLING = new ABProp(26744, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_LEARNING_CLEAR_CHAT_DISABLE_EMPTY_CHATS = new ABProp(26745, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PARSE_ENCRYPTED_DSM_MSG_FIX = new ABProp(26772, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WDS_WEB_COMPOSER_TOOLBAR_V2 = new ABProp(26773, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_CHAT_THREADS_INFRA_WEB_ENABLED = new ABProp(26776, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MM_SIGNAL_SHARING_VERIFICATION_NEW_SIGNAL_TYPE_ORIGIN = new ABProp(26784, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp LARGE_SCREENS_NEW_CHAT_BUTTON_VARIANTS = new ABProp(26788, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_CHAT_THREADS_WEB_KILLSWITCH_ENABLED = new ABProp(26806, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_DISCUSS_PRIVATELY = new ABProp(26815, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_WEB_VOIP_VIRTUAL_VIDEO_CAPTURE_DRIVER = new ABProp(26817, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PRIVACY_SCREEN_ENABLED = new ABProp(26820, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp FEATURE_KEY_STORE_INFRA_ENABLED = new ABProp(26829, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_WEB_VOIP_VIRTUAL_AUDIO_CAPTURE_DRIVER = new ABProp(26838, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENTS_BR_P2P_PIX_COPY_KEY_BUYER_LOGGING = new ABProp(26847, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_MENU_SHARE_GROUP = new ABProp(26850, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CALLING_RUST_MIGRATION_INCOMING_STANZA_BITMAP = new ABProp(26876, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp REMOVE_PN_DEPENDENCIES = new ABProp(26888, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ADD_CONTACT = new ABProp(26892, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_BB_WEB_AUDIENCE_EXPRESSION_SYNC_READ = new ABProp(26894, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAVOIP_ENABLE_ML_NAMESPACE_V2 = new ABProp(26947, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp INTEGRITY_CHECKPOINTS_ENABLED = new ABProp(26961, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp KS_USE_COMPONENT_MODEL = new ABProp(26966, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_WDS_CALLING_DROPDOWN = new ABProp(26974, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_ASTERIA_ROLLOUT_ENABLED = new ABProp(26996, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PIX_PAYMENT_REQUEST_UPDATE_STATUS_ENABLED = new ABProp(27006, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENTS_BR_P2M_ORDER_DETAILS_BUYER_LOGGING = new ABProp(27008, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENTS_BR_P2M_PIX_COPY_KEY_BUYER_LOGGING = new ABProp(27026, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENTS_BR_PAYMENT_LINKS_BUYER_LOGGING = new ABProp(27027, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENTS_BR_P2M_PIX_COPY_CODE_BUYER_LOGGING = new ABProp(27028, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENTS_BR_P2M_PIX_IN_GROUPS_BUYER_LOGGING = new ABProp(27029, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp STATUS_LIKES_FIFA_LOTTIE_FULL_SCREEN_ANIMATION_ENABLED = new ABProp(27054, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_CONSUMER_NOVA_SUBSCRIPTION_NOTIFICATIONS_ENABLED = new ABProp(27068, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_ENABLE_SYNCD_KEY_PERSISTENCE_ONLY_AFTER_SERVER_ACK = new ABProp(27069, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_PAYMENT_REQUEST_STATUS_UPDATE = new ABProp(27077, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BUSINESS_BROADCAST_INSIGHTS_SYNC_PAST_X_DAYS = new ABProp(27082, "30", "30");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_MAIBA_WASS_MIGRATION_RECEIVING = new ABProp(27083, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_MAIBA_WASS_MIGRATION_SENDING = new ABProp(27084, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENTS_BR_P2M_PAY_NOW_BUYER_LOGGING = new ABProp(27092, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENTS_BR_P2M_VIEW_ORDER_BUYER_LOGGING = new ABProp(27093, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENTS_BR_P2M_PIX_MORE_WAYS_TO_PAY_BUYER_LOGGING = new ABProp(27094, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENTS_BR_P2M_COMPLETED_PAYMENT_INTENT_BUYER_LOGGING = new ABProp(27095, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENTS_BR_P2M_COPY_BOLETO_CODE_BUYER_LOGGING = new ABProp(27096, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAVOIP_ML_BWE_QUICKHD_MODEL_DOWNLOAD_VERSIONS = new ABProp(27109, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENTS_BR_P2P_PIX_COPY_CODE_BUYER_LOGGING = new ABProp(27114, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_QP_EMERGENCY_FORCE_FETCH_NONCE = new ABProp(27115, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_ASTERIA_META_AI_SETTINGS_TAB_ENTRYPOINT_ENABLED = new ABProp(27118, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_CHANGE_LIST_WDS_SUBMENU = new ABProp(27123, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MD_SYNCD_MUTATION_LOGGING = new ABProp(27124, "{\"allowlist\": []}", "{\"allowlist\": []}");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MD_SYNCD_MUTATION_SUMMARY_LOGGING = new ABProp(27125, "{\"allowlist\": []}", "{\"allowlist\": []}");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MD_SYNCD_BUNDLE_LOGGING = new ABProp(27126, "{\"allowlist\": []}", "{\"allowlist\": []}");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AURA_PINNED_CHATS_TARGETED_NUX_FORCE = new ABProp(27135, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEBTP_THUMBNAIL_RENDERER_TIMEOUT_MS = new ABProp(27148, "3000", "3000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_FORWARD_TO_SMALL_GROUPS = new ABProp(27157, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_PAYMENTS_SMB_LABELS_CONVENTION_ENABLED = new ABProp(27172, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_PAYMENTS_SMB_ENABLED = new ABProp(27173, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DISABLE_RAISE_HAND_1ON1 = new ABProp(27177, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_CHAT_THREADS_FUZZY_SEARCH_ENABLED = new ABProp(27199, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AURA_SETTINGS_ROW_ENABLED = new ABProp(27210, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BUSINESS_BROADCAST_INSIGHTS_CAMPAIGN_TTL_DAYS = new ABProp(27218, "30", "30");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ACS_USE_GRAPHQL_ISSUANCE = new ABProp(27219, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WMI_WORKER_SCHEDULER_WEB = new ABProp(27237, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAWEB_ENABLE_LEGACY_IMAGE_ZOOM = new ABProp(27239, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_STATUS_CONSUMPTION_ENTRYPOINTS = new ABProp(27240, "0", "3");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ANR_ASYNC_MSG_SEND_HANDLER = new ABProp(27249, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WDS_WEB_RICH_TEXT_FIELD = new ABProp(27264, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_WEB_VOIP_ANR_OPTIMIZATIONS = new ABProp(27268, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_TEST_ABPROP_DELETE_ME = new ABProp(27274, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp OPUS_TIME = new ABProp(27277, "1784516400", "1784516400");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp OPUS_ENABLED = new ABProp(27278, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BR_PAYMENTS_PAYMENT_DETECTION_ENHANCEMENT = new ABProp(27309, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_CHAT_THREADS_HISTORY_ICON_VARIANT = new ABProp(27316, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_STARRED_MSGS_SEARCH = new ABProp(27353, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_RICH_RESPONSE_UNKNOWN_SENDER_PREVIEW_ENABLED = new ABProp(27355, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_HATCH_INTEGRATION_TAB_ENABLED = new ABProp(27356, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USERNAME_1ON1_SYS_MSG_CREATION_UPSELL_ENABLED = new ABProp(27359, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_COMPOSER_HEIGHT_INCREASE_ENABLED = new ABProp(27441, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_CONTEXT_CARD_INVITE_FOLLOWERS_ENABLED = new ABProp(27449, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_MSG_INFRA_REMOVE_DEVICES_ON_406_ERROR_ENABLED = new ABProp(27463, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_HATCH_VIDEO_UPLOAD_ENABLED = new ABProp(27470, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CALL_INFO_OPTIMIZATIONS_VERSION = new ABProp(27483, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_BUSINESS_BROADCAST_SEND_WEB_SMBA = new ABProp(27486, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_HQ_IMAGE_THUMBNAIL_IN_CHAT_SCANS = new ABProp(27512, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_AE_MODEL_META_DATA_ENABLED = new ABProp(27515, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_AE_MODEL_META_DATA_SIGNAL_ENABLED = new ABProp(27516, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_CHAT_THREADS_IMPLICIT_ROUTING_STRATEGY = new ABProp(27519, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEBTP_PRELOAD_THUMBNAIL_RENDERER_NO_EXPOSURE = new ABProp(27534, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEBTP_THUMBNAIL_RENDERER_MODE = new ABProp(27535, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_CORE_REC_CARD = new ABProp(27568, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_AUTH_AGENTS_FEATURE_CONTROL_ENABLED = new ABProp(27585, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_WEBRTC_VIDEO_JB = new ABProp(27591, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_IMPORTANT_MSG_NOTIFICATION = new ABProp(27614, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_EDIT_BEFORE_FORWARDING_TO_STATUS = new ABProp(27616, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp STATUS_E2EE_SEND_OVER_STATUS_STANZA = new ABProp(27620, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp STATUS_E2EE_RECV_OVER_STATUS_STANZA = new ABProp(27622, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_PATHFINDER_LOGGING = new ABProp(27628, "0", "3");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_RICH_RESPONSE_UNKNOWN_SENDER_VERIFICATION_MASKING_ENABLED = new ABProp(27635, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_3PD_AGGREGATED_CONVERSION_ENABLED = new ABProp(27640, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_COPY_PASTE_P2P = new ABProp(27642, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_ORDER_DETAILS_FOR_PAYMENT_KEY = new ABProp(27643, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_HATCH_COMMANDS_ENABLED = new ABProp(27660, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp EXPAND_FMX_MEX_SHOULD_USE_FMX_USE_CASE = new ABProp(27662, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp INTEGRITY_CHECKPOINTS_DEFAULT_ENABLED = new ABProp(27663, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_DRAWER_DESCRIPTOR_ENABLED = new ABProp(27677, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_VOIP_SCTP_WORKER_SAFARI_EXP = new ABProp(27695, "1", "1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_SCROLLABLE_REACTION_TRAY_ENABLED = new ABProp(27709, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_FREQUENT_REACTIONS_STORE_ENABLED = new ABProp(27710, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_FREQUENT_REACTIONS_WEIGHT_REDUCER = new ABProp(27711, "90", "90");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_FREQUENT_REACTIONS_REACTS_AGO_THRESHOLD = new ABProp(27712, "10", "10");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_ENABLE_MENTION_MESSAGE = new ABProp(27714, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_FOCUS_MANAGEMENT_FOR_STATUS_AUDIENCE = new ABProp(27719, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ANIMATED_SOCCER_BALL_TEST_ENABLED = new ABProp(27750, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ANIMATED_SOCCER_BALL_PROD_ENABLED = new ABProp(27751, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_MEDIA_WORKER_SPLIT_ENABLED = new ABProp(27753, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_LOADER_BUTTON_UIX_IMPROVEMENT = new ABProp(27768, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ANR_ASYNC_CONTACTS_RESTORE_FROM_DB_ENABLED = new ABProp(27775, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_MEDIA_UPLOAD_RETRY_RETRIES_COUNT = new ABProp(27782, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp REMOVE_DEVICE_PN_DEPENDENCIES = new ABProp(27791, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp OPUS_T = new ABProp(27803, "2147483647", "2147483647");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USE_CUSTOM_SOCCER_BALL_FOR_REACTION_ENABLED = new ABProp(27807, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_CHAT_META_AI_HOME_WEB_ENABLED = new ABProp(27817, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp LIGHTWEIGHT_GROUP_CREATION = new ABProp(27819, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SOCCER_REACTION_IN_TRAY_ENABLED = new ABProp(27833, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SOCCER_BALL_REACTION_FULL_ANIMATION_ENABLED = new ABProp(27834, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp COEXV2_SEND_ENABLED = new ABProp(27839, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_BIZ_QUALITY_TELEMETRY_MESSAGE_CLICKS_ENABLED = new ABProp(27854, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_BIZ_QUALITY_TELEMETRY_ENABLED = new ABProp(27855, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WDS_WEB_BADGE = new ABProp(27856, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_SEARCH_EMOJI_PICKER = new ABProp(27857, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp INAPP_SIGNUP_AGM_CTA_EXPERIMENT = new ABProp(27860, "1", "1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp TIMEOUT_MEX_CALL_EXPAND_FMX_TRUST_SIGNALS = new ABProp(27862, "600", "600");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_HATCH_DOCUMENT_UPLOAD_SIZE_LIMIT_MB = new ABProp(27873, "20", "20");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_HATCH_FORWARDING_HTML_ENABLED = new ABProp(27876, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SENDER_SECRET_ENCRYPTED_MESSAGE_REMOVE_MESSAGE_SECRET = new ABProp(27913, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_JUMP_TO_CART = new ABProp(27939, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEBTP_PDF_RENDERER_MODE_NO_EXPOSURE = new ABProp(27941, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GIPHY_PMA_SHUTOFF_ENABLED = new ABProp(27942, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AURA_PREMIUM_STICKERS_KILLSWITCH = new ABProp(27946, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_CHATLIST_RENDER_CHAT_OPEN = new ABProp(27947, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WDS_WEB_PROFILE_PHOTO = new ABProp(27954, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_SHOW_TO_HIDE_ENABLED = new ABProp(27958, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp P2P_PILLS_ENABLED = new ABProp(27959, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_VOIP_CAPTURE_VIDEO_ROTATION_TYPE = new ABProp(27973, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BACKFILL_SUPPORTS_COEX_COMPANION = new ABProp(27975, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp HOSTED_MESSAGE_FLAG_ENABLED = new ABProp(27979, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAVOIP_ML_BWE_HD_TARGET_MODEL_DOWNLOAD_VERSIONS_V2 = new ABProp(27990, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAVOIP_ML_BWE_CONG_MODEL_DOWNLOAD_VERSIONS_V2 = new ABProp(27991, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAVOIP_ML_BWE_TR_MODEL_DOWNLOAD_VERSIONS_V2 = new ABProp(27996, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAVOIP_ML_BWE_PLC_MODEL_DOWNLOAD_VERSIONS_V2 = new ABProp(27998, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMBW_BUSINESS_BROADCAST_SMART_COLUMN_DETECTION_ENABLED = new ABProp(27999, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAVOIP_ML_NADL_MODEL_DOWNLOAD_VERSIONS_V2 = new ABProp(28015, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAVOIP_ML_BWE_GC_UNDERSHOOT_MODEL_DOWNLOAD_VERSIONS_V2 = new ABProp(28019, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WAVOIP_ML_BWE_GC_HD_TARGET_MODEL_DOWNLOAD_VERSIONS_V2 = new ABProp(28021, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_CHAT_META_AI_HOME_DEFAULT_LANDING_ENABLED = new ABProp(28033, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_VOIP_VIDEO_LOW_CAP_WIDTH = new ABProp(28041, "480", "480");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_VOIP_VIDEO_LOW_CAP_HEIGHT = new ABProp(28042, "270", "270");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_VOIP_VIDEO_MID_CAP_WIDTH = new ABProp(28043, "640", "640");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_VOIP_VIDEO_MID_CAP_HEIGHT = new ABProp(28044, "360", "360");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_CALLING_AUTO_POPOUT_VIDEO = new ABProp(28046, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_FALCO_CONSOLE_LOGGER = new ABProp(28054, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BLOCKLIST_SYSTEM_MSG_ON_FULL_REFETCH = new ABProp(28070, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_MEMBER_UPDATES_USERNAME_DESCRIPTION_ENABLED = new ABProp(28087, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENHANCED_MENTION_SUGGESTIONS_MIN_MENTION_CHAR_COUNT = new ABProp(28089, "-1", "-1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp M2_AUDIENCE_DYNAMIC_RULES = new ABProp(28099, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp COEXV2_RECV_ENABLED = new ABProp(28110, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ALWAYS_BACKFILL_TO_COEX_COMPANION = new ABProp(28124, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CONSUMER_GRAPHQL_ENABLE_DOUBLE_LOG_FOR_SURVEY = new ABProp(28129, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_BUSINESS_BROADCAST_SEND_WEB_NO_EXP = new ABProp(28138, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_BUSINESS_BROADCAST_SEND_WEB_SMBA_NO_EXP = new ABProp(28139, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp INAPP_SIGNUP_M1_LOGGING_ENABLED = new ABProp(28142, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SYNCD_USE_INDEX_FOR_LTHASH_LOOKUP = new ABProp(28144, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp APPOINTMENT_BOOKING_BLOKS_ENABLED = new ABProp(28146, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_VISIBILITY_LOGGING_FULLSCREEN_MEDIA_ENABLED = new ABProp(28148, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_CHAT_THEME_DRAWER_TITLE = new ABProp(28157, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp FETCH_QP_VIA_GRAPHQL_WEB_ENABLED = new ABProp(28158, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CONSUMER_GRAPHQL_WEB_TO_FETCH_QP_SURFACE_IDS = new ABProp(28159, "{}", "{}");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp OUT_CONTACT_INVITES_ENABLED = new ABProp(28170, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_VOIP_LOW_RESOURCE_DEVICE = new ABProp(28203, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_PULSE_ON_UNREAD_BADGE_ENABLED = new ABProp(28224, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_WEB_LOG_DOWNLOAD = new ABProp(28226, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_ASSET_REPLACEMENT_ENABLED = new ABProp(28265, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_GENAI_STRAW_HAT = new ABProp(28268, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BUSINESS_BROADCASTS_SYNCD_WAM_LOGGING = new ABProp(28277, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_GROUP_TEE_HISTORY_SHARE_ENABLED = new ABProp(28278, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ENABLE_CAMERA_CAPTURE_REFRESH = new ABProp(28316, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CROSS_DEVICE_MESSAGE_EDITING = new ABProp(28340, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AURA_KILL_SWITCH = new ABProp(28345, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp COEX_IICON_BACKFILL = new ABProp(28349, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_SUSPENSION_APPEALS_REDESIGN_VARIANT_ENABLE = new ABProp(28376, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CANONICAL_ENT_COMPANION_SERVER_CACHED_NONCE_ENABLED = new ABProp(28399, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_MENTION_SEARCH = new ABProp(28455, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_BIZ_BROADCASTS_CATALOG_ATTACHMENT = new ABProp(28471, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MM_TAP_TARGET_BLOKS_CLIENT_HYDRATION_ENABLED = new ABProp(28473, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNEL_STATUS_FORWARDING_ENABLED = new ABProp(28479, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNEL_STATUS_DEEPLINK_ENABLED = new ABProp(28500, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_PUSH_NAME_IN_GLOBAL_SEARCH_NON_CONTACTS_ENABLED = new ABProp(28506, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp RELAX_INTEGRITY_CONSTRAINTS_FOR_BB_WA_TENURED_ACCOUNTS = new ABProp(28516, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_WEB_CATEGORY_SEARCH_VIA_GRAPH_ENABLED = new ABProp(28519, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CONSUMER_WEB_QP_GRAPHQL_TO_FETCH_QP_FREQUENCY_MINS = new ABProp(28529, "1320", "1320");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BIZ_AI_TOOLS_SETTINGS = new ABProp(28552, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WDS_WEB_DIALOG = new ABProp(28557, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MM_OPTIMIZED_DELIVERY_ARCHIVE_SIGNAL_SHARING_ENABLED = new ABProp(28558, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WDS_WEB_ACTION_TILE_REFRESH = new ABProp(28564, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MM_DISCLOSURE_HANDLE_TOS_FAILURES_ENABLED = new ABProp(28572, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_BIZ_SIMPLE_SIGNAL_ENABLED = new ABProp(28573, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_BIZ_QUALITY_TELEMETRY_MESSAGE_READS_ENABLED = new ABProp(28574, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_GIZMO_INTEGRATION_ENABLED = new ABProp(28584, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AI_SUBSCRIPTION_IMAGINE_INTENT_ENABLED = new ABProp(28585, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_BIZ_QUALITY_TELEMETRY_MESSAGE_LEVEL_ACTIONS_ENABLED = new ABProp(28590, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_META_ONE_ENABLED = new ABProp(28611, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_META_ONE_ROLLOUT_ENABLED = new ABProp(28612, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_META_ONE_ELIGIBILITY_SUBSCRIPTION_STATUS_CHECK_ENABLED = new ABProp(28613, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_QUICK_REACTIONS = new ABProp(28621, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PNH_HISTORY_SYNC_FORCE_GENERAL = new ABProp(28664, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USERNAME_API_RATE_LIMIT_ENABLED = new ABProp(28678, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_BIZ_SIMPLE_SIGNAL_GROUP_ENABLED = new ABProp(28679, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_SETUP_ERROR_RESULT_CHECK = new ABProp(28689, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_RESHARE_POSTER_SIDE_ENABLED = new ABProp(28732, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_HISTORY_AFTER_JOIN_PREREQUISITES = new ABProp(28787, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AUTH_AGENT_SOFT_OFFBOARDING_ENABLED = new ABProp(28802, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp INAPP_SIGNUP_QPL_LOGGING_ENABLED = new ABProp(28806, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_STATUS_RESHARER_FLOW_ENABLED = new ABProp(28812, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_STATUS_RESHARE_ATTRIBUTION_ENABLED = new ABProp(28813, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_CALLING_FULL_SCREEN_TOGGLE_ENABLED = new ABProp(28830, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ANR_THROTTLE_SIGNAL_SNAPSHOT_ENABLED = new ABProp(28890, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp IM_NFM_MULTI_STEP_FORM_KILLSWITCH = new ABProp(28891, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_BOT_TOS_CHECK_REFINIEMENT = new ABProp(28897, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_VOIP_ADAPTIVE_GRID_PAGE_SIZE = new ABProp(28909, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_BOT_PROFILE_GQL_MIGRATION_ENABLED = new ABProp(28941, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_CONTACT_SORT_LETTERS_FIRST = new ABProp(28962, "-1", "-1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_NATIVE_WEB_DRAFT_AD_ENABLED = new ABProp(28989, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MM_OPTIMIZED_DELIVERY_TOKEN_FALLBACK_DISABLED = new ABProp(29002, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMBW_BUSINESS_BROADCAST_DUPLICATE_ENABLED = new ABProp(29021, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp USERNAME_KEY_REDESIGN_ENABLED = new ABProp(29026, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_BUSINESS_BROADCAST_PRO_ENABLED = new ABProp(29033, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MM_OPTIMIZED_DELIVERY_UNIQUE_TOKEN_PER_MESSAGE_ID_ENABLED = new ABProp(29037, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_BLOCKED_PARTICIPANT_CHAT_WARNING = new ABProp(29038, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_BLOCKED_PARTICIPANT_CALL_WARNING = new ABProp(29039, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ANR_PRUNE_CMC = new ABProp(29060, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_FREQUENTLY_CONTACTED_ENABLED = new ABProp(29063, "-1", "-1");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ANR_BATCH_PROFILE_PICTURE_BRIDGE_OPERATIONS = new ABProp(29122, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_IN_APP_POLICY_DETAIL_ENABLED = new ABProp(29132, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ANIMATED_EMOJI_USE_LAZY_PARSING = new ABProp(29140, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_CALLING_WAVE_RECEIVING_ENABLED = new ABProp(29161, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp NO_LARGE_EMOJI_REGEX = new ABProp(29172, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WMI_ASYNC_AWAIT_PREP = new ABProp(29197, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SHORTCAKE_COMPANION_PROLOGUE__PASSKEYS__HANDOFF_ENABLED = new ABProp(29204, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SHORTCAKE_COMPANION_PROLOGUE__PASSKEYS__ENABLED = new ABProp(29206, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNELS_QUESTIONS_RESPONSES_DRAWER_LOADING_SHIMMER_ENABLED = new ABProp(29209, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp INFO_DRAWER_REFRESH = new ABProp(29210, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp VID_PORT_ENABLE_CAPTURE_FPS_MEDIAN_FILTER = new ABProp(29214, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ACS_USE_GRAPHQL_FOR_MIGRATION_TEST = new ABProp(29217, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ACS_USE_GRAPHQL_FOR_FORWARD_COUNTER = new ABProp(29218, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_CALL_TRANSFER_NOTIFICATION = new ABProp(29242, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_CALLING_WAVE_SENDING_ENABLED = new ABProp(29247, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_LARGE_GROUP_PRESENCE_ENABLED = new ABProp(29279, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_SMALL_GROUP_PRESENCE_ENABLED = new ABProp(29280, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_META_ONE_LAUNCH_FREE_TRIAL_ENABLED = new ABProp(29290, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_MATCH_PRIMARY_ICONS = new ABProp(29293, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ANR_GROUP_METADATA_YIELD = new ABProp(29294, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BIZ_AI_WEB_ONBOARDING_HANDOFF = new ABProp(29298, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AURA_MEDIA_OFFLOAD_BENEFIT_ACTIVE = new ABProp(29308, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_SYNC_FOR_DRAFT_MESSAGES = new ABProp(29314, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_3PD_DATA_SHARING_TITLE_CHANGE = new ABProp(29332, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_3PD_DATA_SHARING_ADDITIONAL_LOGGING = new ABProp(29333, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENTS_UPR_BUBBLE_COUNTRIES = new ABProp(29342, "", "");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MARK_AS_VERIFIED_ENABLED = new ABProp(29343, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BIZ_AI_TOOLS_SYNC = new ABProp(29383, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp AURA_MEDIA_OFFLOAD_ENABLED = new ABProp(29391, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_READ_SELF_WATERMARK_RECEIVE_STORE_TS = new ABProp(29396, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ANR_SPINNER_GPU_ANIMATION = new ABProp(29405, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_CHAT_THEMES_LOGGING = new ABProp(29457, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BUG_REPORTING_NOT_SHIPPED_YET_ENABLED = new ABProp(29458, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ANR_ASYNC_SQLITE_BRIDGE_OPERATIONS = new ABProp(29460, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_CANONICAL_REG_RELOAD_ENABLED = new ABProp(29472, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_REMOVE_MESSAGE_SECRET_FROM_QUOTED_ENABLED = new ABProp(29491, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_MOVE_MESSAGE_SECRET_TOP_LEVEL_ENABLED = new ABProp(29492, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_WEBCODEC_REQUIRE_KEYFRAME = new ABProp(29510, "true", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CTWA_FAVORITES_LIST_SENDS_SIGNALS = new ABProp(29529, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_EXPANSION_COUNTRIES_BONSAI_ENABLED = new ABProp(29543, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_READ_SELF_WATERMARK_SEND_STORE_TS = new ABProp(29546, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WIN_PDF_RENDERING_ENABLED = new ABProp(29548, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ANR_ASYNC_NATIVE_APP_STATE_BRIDGE_ENABLED = new ABProp(29551, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_CALLING_OFFLINE_RESUME_ORDERING = new ABProp(29564, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_WHATS_NEW_CAROUSEL = new ABProp(29618, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_WHATS_NEW_BANNER = new ABProp(29619, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_WHATS_NEW_BANNER_SHORT_COOLDOWN = new ABProp(29620, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_WHATS_NEW_AUTO_MODAL = new ABProp(29621, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_WHATS_NEW_AUTO_MODAL_SHORT_COOLDOWN = new ABProp(29622, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PREMIUM_MSG_BB_CAMPAIGN_SYNC_ENABLED = new ABProp(29650, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp P2P_PILLS_ALLOWLIST_ENTRIES = new ABProp(29708, "{ \"entries\": [{ \"business_id\": \"34666845417\", \"pills\": [\"CHAT\", \"PROFILE\", \"ABOUT_US\"] }]}", "{ \"entries\": [{ \"business_id\": \"34666845417\", \"pills\": [\"CHAT\", \"PROFILE\", \"ABOUT_US\"] }]}");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_WHATS_NEW_BANNER_SHORT_COOLDOWN_V2 = new ABProp(29709, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp P2P_PILLS_ENABLED_FOR_INELIGIBLE_CONTACTS = new ABProp(29715, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_BOT_ORPHAN_LOGIC_ENABLED = new ABProp(29753, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_WEB_VOIP_WEBTRANSPORT = new ABProp(29764, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp PAYMENTS_BR_P2M_BUYER_LOGGING_PHASE_2 = new ABProp(29803, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp ENABLE_WEB_VOIP_EAGER_MIC_ACQUIRE = new ABProp(29836, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_META_ONE_SUBSCRIPTION_NOTIFICATIONS_ENABLED = new ABProp(29866, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_CONFIGURABLE_QUICK_ACTIONS_M1 = new ABProp(29874, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_GET_MSG_EXIST_OPTMISE = new ABProp(29880, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_AUTH_AGENT_OFFBOARDING_ENABLED = new ABProp(29923, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_BIZ_PROFILE_GRAPHQL_MIGRATION_BYPASS_LID_CHECK_DOGFOODING = new ABProp(29965, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp GROUP_HISTORY_SETTING_DECOUPLE_ENABLED = new ABProp(29973, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp UNIFIED_RESPONSE_AI_CONTENT_SEARCH_ENABLED = new ABProp(30000, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_ENABLE_CHAT_THREAD_AND_INFO_STATUS_RING = new ABProp(30026, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_SMB_FORWARD_BB_WEB_ENABLED = new ABProp(30028, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_SELECT_ALL_CHATS_ENABLED = new ABProp(30040, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WIN_HYBRID_BT_ENABLED = new ABProp(30041, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ANR_SKIP_UNUSED_CONTACTS_DB_UPDATES_ENABLED = new ABProp(30043, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp RECEIPT_MODE_BITMASK_ENABLED = new ABProp(30084, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp SMB_WEB_ENABLE_FB_LINKING = new ABProp(30112, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_ADAPTIVE_LAYOUT_ENABLED = new ABProp(30140, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CHANNEL_STATUS_RESHARING_ENABLED = new ABProp(30155, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp CALLING_VOICEMAIL_QUOTED_REPLIES_ENABLED = new ABProp(30165, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp DM_AFTER_READ_TIMER_SENDER_OPTIONS_SECONDS = new ABProp(30176, "{\"timers\": [0, 300, 3600, 43200]}", "{\"timers\": [0, 300, 3600, 43200]}");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp P2P_PILLS_AUTO_SEND_MESSAGES = new ABProp(30208, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_CANONICAL_WAM_FALCO_BUFFER_ENABLED = new ABProp(30212, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEBTP_USE_ASYNC_PDF_SEND = new ABProp(30214, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_CANONICAL_WAM_FALCO_BUFFER_SIZE = new ABProp(30219, "2000", "2000");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_ANR_OPTIMIZED_INITIAL_CONTACTS_SYNC_ENABLED = new ABProp(30227, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp MEDIA_FORCE_TRANSCODE_ON_ELST = new ABProp(30235, "false", "true");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WEB_GROUP_HOVER_CARD_VARIANT = new ABProp(30260, "0", "0");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_VOIP_STACK_LOG_LEVEL = new ABProp(30261, "3", "3");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp BIZ_VPV_DIMENSIONS_LOGGING_ENABLED = new ABProp(30266, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WA_WEB_BIZ_BROADCASTS_CONTEXTUAL_ENTRYPOINTS = new ABProp(30270, "false", "false");

    /**
     * This constant was generated automatically by {@code tools/web/ab-props-codegen}.
     */
    public static final ABProp WMI_TASK_SCHEDULER_SECOND_STEP = new ABProp(30276, "false", "false");

    /**
     * Constructs a new {@code ABProp} definition.
     *
     * @throws NullPointerException if {@code defaultValue} or {@code debugDefaultValue}
     *         is {@code null}
     */
    public ABProp {
        Objects.requireNonNull(defaultValue, "defaultValue cannot be null");
        Objects.requireNonNull(debugDefaultValue, "debugDefaultValue cannot be null");
    }

    /**
     * Converts a string value to a boolean.
     *
     * <p>The following values are considered {@code true}: {@code "1"}, {@code "True"},
     * and {@code "true"}. All other values are considered {@code false}.
     *
     * @param value the string value to convert
     * @return the boolean representation of the given value
     */
    public static boolean toBoolean(String value) {
        return "1".equals(value)
                || "True".equals(value)
                || "true".equals(value);
    }

    /**
     * Attempts to convert a string value to an integer.
     *
     * @param value the string value to parse
     * @return an {@link OptionalInt} containing the integer value if parsing succeeds,
     *         or empty if it fails
     */
    public static OptionalInt toInt(String value) {
        try {
            return OptionalInt.of(Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return OptionalInt.empty();
        }
    }

    /**
     * Attempts to convert a string value to a long.
     *
     * @param value the string value to parse
     * @return an {@link OptionalLong} containing the long value if parsing succeeds,
     *         or empty if it fails
     */
    public static OptionalLong toLong(String value) {
        try {
            return OptionalLong.of(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }

    /**
     * Attempts to convert a string value to a double (floating-point).
     *
     * @param value the string value to parse
     * @return an {@link OptionalDouble} containing the double value if parsing succeeds,
     *         or empty if it fails
     */
    public static OptionalDouble toDouble(String value) {
        try {
            return OptionalDouble.of(Double.parseDouble(value));
        } catch (NumberFormatException exception) {
            return OptionalDouble.empty();
        }
    }

    @Override
    public String toString() {
        return "ABProp[code=%d, defaultValue='%s', debugDefaultValue='%s']"
                .formatted(code, defaultValue, debugDefaultValue);
    }
}
