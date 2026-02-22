package com.github.auties00.cobalt.props;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Represents an A/B testing property (AB prop) definition with its configuration code and default value.
 *
 * <p>AB props are feature flags and configuration values that WhatsApp uses to control client behavior,
 * enable/disable features, and conduct A/B testing experiments. Each prop definition consists of:
 * <ul>
 * <li>A numeric {@code code} that uniquely identifies the property
 * <li>A {@code defaultValue} string used when the server has not sent a value for this prop
 * </ul>
 *
 * <p>The default value is always a string, matching the format in which values are received from
 * the server. Static conversion methods are provided to parse the string into typed values
 * (boolean, int, long, double).
 *
 * @param code         the unique numeric identifier for this configuration property
 * @param defaultValue the default value to use when the server has not provided a value for
 *                     this property, must not be {@code null}
 */
public record ABProp(int code, String defaultValue) {

    /**
     * A/B prop {@code qpl_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: qpl_enabled:[212,"bool"]
     */
    public static final ABProp QPL_ENABLED = new ABProp(212, "false");

    /**
     * A/B prop {@code qpl_upload_delay} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: qpl_upload_delay:[215,"int"]
     */
    public static final ABProp QPL_UPLOAD_DELAY = new ABProp(215, "1440");

    /**
     * A/B prop {@code upload_document_thumb_mms_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: upload_document_thumb_mms_enabled:[247,"bool"]
     */
    public static final ABProp UPLOAD_DOCUMENT_THUMB_MMS_ENABLED = new ABProp(247, "false");

    /**
     * A/B prop {@code download_status_thumb_mms_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: download_status_thumb_mms_enabled:[249,"bool"]
     */
    public static final ABProp DOWNLOAD_STATUS_THUMB_MMS_ENABLED = new ABProp(249, "false");

    /**
     * A/B prop {@code download_document_thumb_mms_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: download_document_thumb_mms_enabled:[250,"bool"]
     */
    public static final ABProp DOWNLOAD_DOCUMENT_THUMB_MMS_ENABLED = new ABProp(250, "false");

    /**
     * A/B prop {@code md_icdc_hash_length} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: md_icdc_hash_length:[310,"int"]
     */
    public static final ABProp MD_ICDC_HASH_LENGTH = new ABProp(310, "10");

    /**
     * A/B prop {@code smb_collections_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_collections_enabled:[451,"bool"]
     */
    public static final ABProp SMB_COLLECTIONS_ENABLED = new ABProp(451, "false");

    /**
     * A/B prop {@code qpl_sampling_as_string} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: qpl_sampling_as_string:[466,"string"]
     */
    public static final ABProp QPL_SAMPLING_AS_STRING = new ABProp(466, "json:{\"sampling\":[]}");

    /**
     * A/B prop {@code smb_collections_appeal_flow_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_collections_appeal_flow_enabled:[724,"bool"]
     */
    public static final ABProp SMB_COLLECTIONS_APPEAL_FLOW_ENABLED = new ABProp(724, "false");

    /**
     * A/B prop {@code drop_last_name} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: drop_last_name:[726,"bool"]
     */
    public static final ABProp DROP_LAST_NAME = new ABProp(726, "false");

    /**
     * A/B prop {@code num_days_key_index_list_expiration} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: num_days_key_index_list_expiration:[730,"int"]
     */
    public static final ABProp NUM_DAYS_KEY_INDEX_LIST_EXPIRATION = new ABProp(730, "35");

    /**
     * A/B prop {@code num_days_before_device_expiry_check} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: num_days_before_device_expiry_check:[731,"int"]
     */
    public static final ABProp NUM_DAYS_BEFORE_DEVICE_EXPIRY_CHECK = new ABProp(731, "7");

    /**
     * A/B prop {@code nfm_rendering_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: nfm_rendering_enabled:[760,"bool"]
     */
    public static final ABProp NFM_RENDERING_ENABLED = new ABProp(760, "false");

    /**
     * A/B prop {@code web_abprop_business_profile_refresh_linked_account_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_abprop_business_profile_refresh_linked_account_enabled:[764,"bool"]
     */
    public static final ABProp WEB_ABPROP_BUSINESS_PROFILE_REFRESH_LINKED_ACCOUNT_ENABLED = new ABProp(764, "false");

    /**
     * A/B prop {@code tos_3_client_gating_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: tos_3_client_gating_enabled:[791,"bool"]
     */
    public static final ABProp TOS_3_CLIENT_GATING_ENABLED = new ABProp(791, "false");

    /**
     * A/B prop {@code web_abprop_direct_connection_md} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_abprop_direct_connection_md:[869,"bool"]
     */
    public static final ABProp WEB_ABPROP_DIRECT_CONNECTION_MD = new ABProp(869, "false");

    /**
     * A/B prop {@code tos_client_state_fetch_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: tos_client_state_fetch_enabled:[877,"bool"]
     */
    public static final ABProp TOS_CLIENT_STATE_FETCH_ENABLED = new ABProp(877, "false");

    /**
     * A/B prop {@code web_abprop_block_catalog_creation_ecommerce_compliance_india} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_abprop_block_catalog_creation_ecommerce_compliance_india:[894,"bool"]
     */
    public static final ABProp WEB_ABPROP_BLOCK_CATALOG_CREATION_ECOMMERCE_COMPLIANCE_INDIA = new ABProp(894, "false");

    /**
     * A/B prop {@code tos_client_state_fetch_iteration} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: tos_client_state_fetch_iteration:[908,"int"]
     */
    public static final ABProp TOS_CLIENT_STATE_FETCH_ITERATION = new ABProp(908, "0");

    /**
     * A/B prop {@code banned_shops_ux_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: banned_shops_ux_enabled:[957,"bool"]
     */
    public static final ABProp BANNED_SHOPS_UX_ENABLED = new ABProp(957, "false");

    /**
     * A/B prop {@code ctwa_tos_filtering_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_tos_filtering_enabled:[976,"bool"]
     */
    public static final ABProp CTWA_TOS_FILTERING_ENABLED = new ABProp(976, "false");

    /**
     * A/B prop {@code tctoken_duration_sender} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: tctoken_duration_sender:[996,"int"]
     */
    public static final ABProp TCTOKEN_DURATION_SENDER = new ABProp(996, "604800");

    /**
     * A/B prop {@code smb_ecommerce_compliance_india_m4} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_ecommerce_compliance_india_m4:[1003,"bool"]
     */
    public static final ABProp SMB_ECOMMERCE_COMPLIANCE_INDIA_M4 = new ABProp(1003, "false");

    /**
     * A/B prop {@code smart_filters_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smart_filters_enabled:[1015,"bool"]
     */
    public static final ABProp SMART_FILTERS_ENABLED = new ABProp(1015, "false");

    /**
     * A/B prop {@code btm_threads_logging_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: btm_threads_logging_enabled:[1022,"bool"]
     */
    public static final ABProp BTM_THREADS_LOGGING_ENABLED = new ABProp(1022, "false");

    /**
     * A/B prop {@code in_app_support_v2_number_prefixes} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: in_app_support_v2_number_prefixes:[1031,"string"]
     */
    public static final ABProp IN_APP_SUPPORT_V2_NUMBER_PREFIXES = new ABProp(1031, "15517868");

    /**
     * A/B prop {@code native_commerce_threads_logging_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: native_commerce_threads_logging_enabled:[1034,"bool"]
     */
    public static final ABProp NATIVE_COMMERCE_THREADS_LOGGING_ENABLED = new ABProp(1034, "false");

    /**
     * A/B prop {@code system_msg_numbers_fb_branded} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: system_msg_numbers_fb_branded:[1035,"string"]
     */
    public static final ABProp SYSTEM_MSG_NUMBERS_FB_BRANDED = new ABProp(1035, "16325551023,16505434800,16503130062,16507885324,16508620604,16504228206,447710173736,16315551023,16505361212,16508129150,16315555102,16315558723,16505212669,16507885280,19032707825,0");

    /**
     * A/B prop {@code system_msg_numbers_fb_inc} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: system_msg_numbers_fb_inc:[1036,"string"]
     */
    public static final ABProp SYSTEM_MSG_NUMBERS_FB_INC = new ABProp(1036, "");

    /**
     * A/B prop {@code web_shop_storefront_message} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_shop_storefront_message:[1053,"bool"]
     */
    public static final ABProp WEB_SHOP_STOREFRONT_MESSAGE = new ABProp(1053, "false");

    /**
     * A/B prop {@code web_send_invisible_msg_to_new_groups} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_send_invisible_msg_to_new_groups:[1099,"bool"]
     */
    public static final ABProp WEB_SEND_INVISIBLE_MSG_TO_NEW_GROUPS = new ABProp(1099, "false");

    /**
     * A/B prop {@code web_send_invisible_msg_min_group_size} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_send_invisible_msg_min_group_size:[1100,"int"]
     */
    public static final ABProp WEB_SEND_INVISIBLE_MSG_MIN_GROUP_SIZE = new ABProp(1100, "128");

    /**
     * A/B prop {@code lthash_check_hours} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: lthash_check_hours:[1104,"int"]
     */
    public static final ABProp LTHASH_CHECK_HOURS = new ABProp(1104, "0");

    /**
     * A/B prop {@code country_client_gating_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: country_client_gating_enabled:[1105,"bool"]
     */
    public static final ABProp COUNTRY_CLIENT_GATING_ENABLED = new ABProp(1105, "false");

    /**
     * A/B prop {@code order_details_from_cart_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: order_details_from_cart_enabled:[1107,"bool"]
     */
    public static final ABProp ORDER_DETAILS_FROM_CART_ENABLED = new ABProp(1107, "false");

    /**
     * A/B prop {@code interactive_message_native_flow_killswitch} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: interactive_message_native_flow_killswitch:[1133,"bool"]
     */
    public static final ABProp INTERACTIVE_MESSAGE_NATIVE_FLOW_KILLSWITCH = new ABProp(1133, "false");

    /**
     * A/B prop {@code message_count_logging_md_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: message_count_logging_md_enabled:[1135,"bool"]
     */
    public static final ABProp MESSAGE_COUNT_LOGGING_MD_ENABLED = new ABProp(1135, "false");

    /**
     * A/B prop {@code web_init_chat_max_unread_message_count} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_init_chat_max_unread_message_count:[1172,"int"]
     */
    public static final ABProp WEB_INIT_CHAT_MAX_UNREAD_MESSAGE_COUNT = new ABProp(1172, "0");

    /**
     * A/B prop {@code order_details_custom_item_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: order_details_custom_item_enabled:[1176,"bool"]
     */
    public static final ABProp ORDER_DETAILS_CUSTOM_ITEM_ENABLED = new ABProp(1176, "false");

    /**
     * A/B prop {@code order_management_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: order_management_enabled:[1188,"bool"]
     */
    public static final ABProp ORDER_MANAGEMENT_ENABLED = new ABProp(1188, "false");

    /**
     * A/B prop {@code smb_ecommerce_compliance_india_m4_5} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_ecommerce_compliance_india_m4_5:[1192,"bool"]
     */
    public static final ABProp SMB_ECOMMERCE_COMPLIANCE_INDIA_M4_5 = new ABProp(1192, "false");

    /**
     * A/B prop {@code smb_hide_unsupported_currency_price} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_hide_unsupported_currency_price:[1203,"bool"]
     */
    public static final ABProp SMB_HIDE_UNSUPPORTED_CURRENCY_PRICE = new ABProp(1203, "false");

    /**
     * A/B prop {@code order_details_from_catalog_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: order_details_from_catalog_enabled:[1212,"bool"]
     */
    public static final ABProp ORDER_DETAILS_FROM_CATALOG_ENABLED = new ABProp(1212, "false");

    /**
     * A/B prop {@code qpl_initial_upload_delay} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: qpl_initial_upload_delay:[1223,"int"]
     */
    public static final ABProp QPL_INITIAL_UPLOAD_DELAY = new ABProp(1223, "5");

    /**
     * A/B prop {@code parent_group_link_limit} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: parent_group_link_limit:[1238,"int"]
     */
    public static final ABProp PARENT_GROUP_LINK_LIMIT = new ABProp(1238, "100");

    /**
     * A/B prop {@code smb_click_to_chat_logging_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_click_to_chat_logging_enabled:[1252,"bool"]
     */
    public static final ABProp SMB_CLICK_TO_CHAT_LOGGING_ENABLED = new ABProp(1252, "false");

    /**
     * A/B prop {@code smb_biz_profile_logging_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_biz_profile_logging_enabled:[1255,"bool"]
     */
    public static final ABProp SMB_BIZ_PROFILE_LOGGING_ENABLED = new ABProp(1255, "false");

    /**
     * A/B prop {@code smart_filters_enabled_consumer} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smart_filters_enabled_consumer:[1287,"bool"]
     */
    public static final ABProp SMART_FILTERS_ENABLED_CONSUMER = new ABProp(1287, "false");

    /**
     * A/B prop {@code group_size_limit} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: group_size_limit:[1304,"int"]
     */
    public static final ABProp GROUP_SIZE_LIMIT = new ABProp(1304, "257");

    /**
     * A/B prop {@code commerce_sanctioned} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: commerce_sanctioned:[1319,"bool"]
     */
    public static final ABProp COMMERCE_SANCTIONED = new ABProp(1319, "false");

    /**
     * A/B prop {@code graphql_privacy_imp_m2} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: graphql_privacy_imp_m2:[1327,"bool"]
     */
    public static final ABProp GRAPHQL_PRIVACY_IMP_M2 = new ABProp(1327, "false");

    /**
     * A/B prop {@code web_abprop_business_profile_refresh_linked_accounts_killswitch} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_abprop_business_profile_refresh_linked_accounts_killswitch:[1351,"bool"]
     */
    public static final ABProp WEB_ABPROP_BUSINESS_PROFILE_REFRESH_LINKED_ACCOUNTS_KILLSWITCH = new ABProp(1351, "false");

    /**
     * A/B prop {@code syncd_periodic_sync_days} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: syncd_periodic_sync_days:[1400,"int"]
     */
    public static final ABProp SYNCD_PERIODIC_SYNC_DAYS = new ABProp(1400, "0");

    /**
     * A/B prop {@code poll_name_length} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: poll_name_length:[1406,"int"]
     */
    public static final ABProp POLL_NAME_LENGTH = new ABProp(1406, "255");

    /**
     * A/B prop {@code poll_option_length} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: poll_option_length:[1407,"int"]
     */
    public static final ABProp POLL_OPTION_LENGTH = new ABProp(1407, "100");

    /**
     * A/B prop {@code poll_option_count} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: poll_option_count:[1408,"int"]
     */
    public static final ABProp POLL_OPTION_COUNT = new ABProp(1408, "12");

    /**
     * A/B prop {@code interactive_response_message_killswitch} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: interactive_response_message_killswitch:[1435,"bool"]
     */
    public static final ABProp INTERACTIVE_RESPONSE_MESSAGE_KILLSWITCH = new ABProp(1435, "false");

    /**
     * A/B prop {@code interactive_response_message_native_flow_killswitch} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: interactive_response_message_native_flow_killswitch:[1436,"bool"]
     */
    public static final ABProp INTERACTIVE_RESPONSE_MESSAGE_NATIVE_FLOW_KILLSWITCH = new ABProp(1436, "false");

    /**
     * A/B prop {@code web_syncd_max_mutations_to_process_during_resume} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_syncd_max_mutations_to_process_during_resume:[1513,"int"]
     */
    public static final ABProp WEB_SYNCD_MAX_MUTATIONS_TO_PROCESS_DURING_RESUME = new ABProp(1513, "1e3");

    /**
     * A/B prop {@code catalog_categories_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: catalog_categories_enabled:[1514,"bool"]
     */
    public static final ABProp CATALOG_CATEGORIES_ENABLED = new ABProp(1514, "false");

    /**
     * A/B prop {@code smb_billing_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_billing_enabled:[1583,"bool"]
     */
    public static final ABProp SMB_BILLING_ENABLED = new ABProp(1583, "false");

    /**
     * A/B prop {@code order_details_quick_pay} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: order_details_quick_pay:[1600,"string"]
     */
    public static final ABProp ORDER_DETAILS_QUICK_PAY = new ABProp(1600, "{\"allowed_product_type\":\"none\"}");

    /**
     * A/B prop {@code smb_billing_premium_access_config} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_billing_premium_access_config:[1619,"string"]
     */
    public static final ABProp SMB_BILLING_PREMIUM_ACCESS_CONFIG = new ABProp(1619, "");

    /**
     * A/B prop {@code web_push_notifications} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_push_notifications:[1643,"bool"]
     */
    public static final ABProp WEB_PUSH_NOTIFICATIONS = new ABProp(1643, "false");

    /**
     * A/B prop {@code web_quantity_controls_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_quantity_controls_enabled:[1659,"bool"]
     */
    public static final ABProp WEB_QUANTITY_CONTROLS_ENABLED = new ABProp(1659, "false");

    /**
     * A/B prop {@code dm_updated_system_message} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: dm_updated_system_message:[1670,"bool"]
     */
    public static final ABProp DM_UPDATED_SYSTEM_MESSAGE = new ABProp(1670, "false");

    /**
     * A/B prop {@code wa_ctwa_log_user_journey_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_ctwa_log_user_journey_enabled:[1681,"bool"]
     */
    public static final ABProp WA_CTWA_LOG_USER_JOURNEY_ENABLED = new ABProp(1681, "false");

    /**
     * A/B prop {@code order_details_total_maximum_value} of floating-point type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: order_details_total_maximum_value:[1684,"float"]
     */
    public static final ABProp ORDER_DETAILS_TOTAL_MAXIMUM_VALUE = new ABProp(1684, "5e8");

    /**
     * A/B prop {@code keep_in_chat_undo_duration_limit} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: keep_in_chat_undo_duration_limit:[1698,"int"]
     */
    public static final ABProp KEEP_IN_CHAT_UNDO_DURATION_LIMIT = new ABProp(1698, "2592e3");

    /**
     * A/B prop {@code view_once_sp_receiver} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: view_once_sp_receiver:[1710,"bool"]
     */
    public static final ABProp VIEW_ONCE_SP_RECEIVER = new ABProp(1710, "false");

    /**
     * A/B prop {@code order_details_total_order_minimum_value} of floating-point type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: order_details_total_order_minimum_value:[1719,"float"]
     */
    public static final ABProp ORDER_DETAILS_TOTAL_ORDER_MINIMUM_VALUE = new ABProp(1719, "1");

    /**
     * A/B prop {@code web_group_profile_editor} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_group_profile_editor:[1745,"bool"]
     */
    public static final ABProp WEB_GROUP_PROFILE_EDITOR = new ABProp(1745, "true");

    /**
     * A/B prop {@code is_meta_employee_or_internal_tester} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: is_meta_employee_or_internal_tester:[1777,"bool"]
     */
    public static final ABProp IS_META_EMPLOYEE_OR_INTERNAL_TESTER = new ABProp(1777, "false");

    /**
     * A/B prop {@code smb_md_agent_chat_assignment_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_md_agent_chat_assignment_enabled:[1798,"bool"]
     */
    public static final ABProp SMB_MD_AGENT_CHAT_ASSIGNMENT_ENABLED = new ABProp(1798, "false");

    /**
     * A/B prop {@code web_syncd_fatal_fields_from_L1104589PRV2} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_syncd_fatal_fields_from_L1104589PRV2:[1808,"bool"]
     */
    public static final ABProp WEB_SYNCD_FATAL_FIELDS_FROM_L1104589PRV2 = new ABProp(1808, "false");

    /**
     * A/B prop {@code disable_auto_download} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: disable_auto_download:[1838,"bool"]
     */
    public static final ABProp DISABLE_AUTO_DOWNLOAD = new ABProp(1838, "false");

    /**
     * A/B prop {@code ctwa_data_max_length} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_data_max_length:[1841,"int"]
     */
    public static final ABProp CTWA_DATA_MAX_LENGTH = new ABProp(1841, "768");

    /**
     * A/B prop {@code direct_connection_business_numbers} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: direct_connection_business_numbers:[1846,"string"]
     */
    public static final ABProp DIRECT_CONNECTION_BUSINESS_NUMBERS = new ABProp(1846, "16005554444,918591749310,917977079770");

    /**
     * A/B prop {@code web_multi_skin_toned_emoji_picker} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_multi_skin_toned_emoji_picker:[1850,"bool"]
     */
    public static final ABProp WEB_MULTI_SKIN_TONED_EMOJI_PICKER = new ABProp(1850, "false");

    /**
     * A/B prop {@code status_reaction_emojis} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: status_reaction_emojis:[1852,"string"]
     */
    public static final ABProp STATUS_REACTION_EMOJIS = new ABProp(1852, "[128525, 128514, 128558, 128546, 128591, 128079, 127881, 128175]");

    /**
     * A/B prop {@code group_size_bypassing_sampling} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: group_size_bypassing_sampling:[1861,"int"]
     */
    public static final ABProp GROUP_SIZE_BYPASSING_SAMPLING = new ABProp(1861, "1e5");

    /**
     * A/B prop {@code share_phone_number_on_cart_send_to_direct_connection_biz_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: share_phone_number_on_cart_send_to_direct_connection_biz_enabled:[1867,"bool"]
     */
    public static final ABProp SHARE_PHONE_NUMBER_ON_CART_SEND_TO_DIRECT_CONNECTION_BIZ_ENABLED = new ABProp(1867, "true");

    /**
     * A/B prop {@code smb_multi_device_agents_logging_V2_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_multi_device_agents_logging_V2_enabled:[1897,"bool"]
     */
    public static final ABProp SMB_MULTI_DEVICE_AGENTS_LOGGING_V2_ENABLED = new ABProp(1897, "false");

    /**
     * A/B prop {@code smb_temp_cover_photo_privacy_messaging} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_temp_cover_photo_privacy_messaging:[1913,"bool"]
     */
    public static final ABProp SMB_TEMP_COVER_PHOTO_PRIVACY_MESSAGING = new ABProp(1913, "false");

    /**
     * A/B prop {@code web_send_invisible_msg_max_group_size} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_send_invisible_msg_max_group_size:[1945,"int"]
     */
    public static final ABProp WEB_SEND_INVISIBLE_MSG_MAX_GROUP_SIZE = new ABProp(1945, "1024");

    /**
     * A/B prop {@code poll_result_details_view_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: poll_result_details_view_enabled:[1948,"bool"]
     */
    public static final ABProp POLL_RESULT_DETAILS_VIEW_ENABLED = new ABProp(1948, "true");

    /**
     * A/B prop {@code smb_multi_device_message_attribution_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_multi_device_message_attribution_enabled:[1981,"bool"]
     */
    public static final ABProp SMB_MULTI_DEVICE_MESSAGE_ATTRIBUTION_ENABLED = new ABProp(1981, "false");

    /**
     * A/B prop {@code parent_group_link_limit_community_creation} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: parent_group_link_limit_community_creation:[1990,"int"]
     */
    public static final ABProp PARENT_GROUP_LINK_LIMIT_COMMUNITY_CREATION = new ABProp(1990, "10");

    /**
     * A/B prop {@code graphql_locale_remapping} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: graphql_locale_remapping:[2014,"string"]
     */
    public static final ABProp GRAPHQL_LOCALE_REMAPPING = new ABProp(2014, "{}");

    /**
     * A/B prop {@code web_message_list_a11y_redesign} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_message_list_a11y_redesign:[2016,"bool"]
     */
    public static final ABProp WEB_MESSAGE_LIST_A11Y_REDESIGN = new ABProp(2016, "true");

    /**
     * A/B prop {@code web_enable_biz_catalog_view_ps_logging} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_enable_biz_catalog_view_ps_logging:[2056,"bool"]
     */
    public static final ABProp WEB_ENABLE_BIZ_CATALOG_VIEW_PS_LOGGING = new ABProp(2056, "true");

    /**
     * A/B prop {@code group_suspend_appeal_include_entity_id_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: group_suspend_appeal_include_entity_id_enabled:[2057,"bool"]
     */
    public static final ABProp GROUP_SUSPEND_APPEAL_INCLUDE_ENTITY_ID_ENABLED = new ABProp(2057, "false");

    /**
     * A/B prop {@code web_abprop_media_links_docs_search} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_abprop_media_links_docs_search:[2063,"bool"]
     */
    public static final ABProp WEB_ABPROP_MEDIA_LINKS_DOCS_SEARCH = new ABProp(2063, "false");

    /**
     * A/B prop {@code mms_vcache_aggregation_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: mms_vcache_aggregation_enabled:[2134,"bool"]
     */
    public static final ABProp MMS_VCACHE_AGGREGATION_ENABLED = new ABProp(2134, "false");

    /**
     * A/B prop {@code smb_md_agent_chat_assignment_system_messages_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_md_agent_chat_assignment_system_messages_enabled:[2157,"bool"]
     */
    public static final ABProp SMB_MD_AGENT_CHAT_ASSIGNMENT_SYSTEM_MESSAGES_ENABLED = new ABProp(2157, "false");

    /**
     * A/B prop {@code smb_ctwa_billing_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_ctwa_billing_enabled:[2158,"bool"]
     */
    public static final ABProp SMB_CTWA_BILLING_ENABLED = new ABProp(2158, "false");

    /**
     * A/B prop {@code parent_group_view_enabled_for_smb_on_web} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: parent_group_view_enabled_for_smb_on_web:[2205,"bool"]
     */
    public static final ABProp PARENT_GROUP_VIEW_ENABLED_FOR_SMB_ON_WEB = new ABProp(2205, "false");

    /**
     * A/B prop {@code parent_group_create_enabled_for_smb_on_web} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: parent_group_create_enabled_for_smb_on_web:[2206,"bool"]
     */
    public static final ABProp PARENT_GROUP_CREATE_ENABLED_FOR_SMB_ON_WEB = new ABProp(2206, "false");

    /**
     * A/B prop {@code smb_md_agent_chat_assignment_nux_impressions} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_md_agent_chat_assignment_nux_impressions:[2207,"int"]
     */
    public static final ABProp SMB_MD_AGENT_CHAT_ASSIGNMENT_NUX_IMPRESSIONS = new ABProp(2207, "0");

    /**
     * A/B prop {@code mex_phase3_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: mex_phase3_enabled:[2249,"bool"]
     */
    public static final ABProp MEX_PHASE3_ENABLED = new ABProp(2249, "false");

    /**
     * A/B prop {@code mex_phase3_status_flags} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: mex_phase3_status_flags:[2250,"int"]
     */
    public static final ABProp MEX_PHASE3_STATUS_FLAGS = new ABProp(2250, "0");

    /**
     * A/B prop {@code web_message_custom_aria_label} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_message_custom_aria_label:[2280,"bool"]
     */
    public static final ABProp WEB_MESSAGE_CUSTOM_ARIA_LABEL = new ABProp(2280, "false");

    /**
     * A/B prop {@code parent_group_create_privacy} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: parent_group_create_privacy:[2356,"bool"]
     */
    public static final ABProp PARENT_GROUP_CREATE_PRIVACY = new ABProp(2356, "false");

    /**
     * A/B prop {@code block_from_notification} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: block_from_notification:[2374,"bool"]
     */
    public static final ABProp BLOCK_FROM_NOTIFICATION = new ABProp(2374, "false");

    /**
     * A/B prop {@code four_reactions_in_bubble_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: four_reactions_in_bubble_enabled:[2378,"bool"]
     */
    public static final ABProp FOUR_REACTIONS_IN_BUBBLE_ENABLED = new ABProp(2378, "false");

    /**
     * A/B prop {@code web_non_blocking_offline_resume_max_message_count} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_non_blocking_offline_resume_max_message_count:[2508,"int"]
     */
    public static final ABProp WEB_NON_BLOCKING_OFFLINE_RESUME_MAX_MESSAGE_COUNT = new ABProp(2508, "1e3");

    /**
     * A/B prop {@code out_of_sync_disappearing_messages_logging} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: out_of_sync_disappearing_messages_logging:[2561,"bool"]
     */
    public static final ABProp OUT_OF_SYNC_DISAPPEARING_MESSAGES_LOGGING = new ABProp(2561, "false");

    /**
     * A/B prop {@code smb_biz_profile_custom_url} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_biz_profile_custom_url:[2582,"bool"]
     */
    public static final ABProp SMB_BIZ_PROFILE_CUSTOM_URL = new ABProp(2582, "true");

    /**
     * A/B prop {@code media_picker_select_limit} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: media_picker_select_limit:[2614,"int"]
     */
    public static final ABProp MEDIA_PICKER_SELECT_LIMIT = new ABProp(2614, "30");

    /**
     * A/B prop {@code placeholder_message_key_hash_logging} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: placeholder_message_key_hash_logging:[2639,"bool"]
     */
    public static final ABProp PLACEHOLDER_MESSAGE_KEY_HASH_LOGGING = new ABProp(2639, "false");

    /**
     * A/B prop {@code polls_fast_follow_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: polls_fast_follow_enabled:[2661,"bool"]
     */
    public static final ABProp POLLS_FAST_FOLLOW_ENABLED = new ABProp(2661, "true");

    /**
     * A/B prop {@code media_picker_select_limit_new} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: media_picker_select_limit_new:[2693,"int"]
     */
    public static final ABProp MEDIA_PICKER_SELECT_LIMIT_NEW = new ABProp(2693, "30");

    /**
     * A/B prop {@code smb_md_agent_chat_assignment_system_messages_logging_v2_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_md_agent_chat_assignment_system_messages_logging_v2_enabled:[2709,"bool"]
     */
    public static final ABProp SMB_MD_AGENT_CHAT_ASSIGNMENT_SYSTEM_MESSAGES_LOGGING_V2_ENABLED = new ABProp(2709, "false");

    /**
     * A/B prop {@code ephemeral_sync_response} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ephemeral_sync_response:[2714,"bool"]
     */
    public static final ABProp EPHEMERAL_SYNC_RESPONSE = new ABProp(2714, "false");

    /**
     * A/B prop {@code web_display_name_for_enterprise_biz_vlevel_low_killswitch} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_display_name_for_enterprise_biz_vlevel_low_killswitch:[2715,"bool"]
     */
    public static final ABProp WEB_DISPLAY_NAME_FOR_ENTERPRISE_BIZ_VLEVEL_LOW_KILLSWITCH = new ABProp(2715, "false");

    /**
     * A/B prop {@code web_display_name_for_biz_vlevel_low_killswitch} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_display_name_for_biz_vlevel_low_killswitch:[2716,"bool"]
     */
    public static final ABProp WEB_DISPLAY_NAME_FOR_BIZ_VLEVEL_LOW_KILLSWITCH = new ABProp(2716, "true");

    /**
     * A/B prop {@code poll_receiving_cag_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: poll_receiving_cag_enabled:[2737,"bool"]
     */
    public static final ABProp POLL_RECEIVING_CAG_ENABLED = new ABProp(2737, "false");

    /**
     * A/B prop {@code poll_creation_cag_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: poll_creation_cag_enabled:[2738,"bool"]
     */
    public static final ABProp POLL_CREATION_CAG_ENABLED = new ABProp(2738, "false");

    /**
     * A/B prop {@code community_announcement_group_size_limit} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: community_announcement_group_size_limit:[2774,"int"]
     */
    public static final ABProp COMMUNITY_ANNOUNCEMENT_GROUP_SIZE_LIMIT = new ABProp(2774, "5e3");

    /**
     * A/B prop {@code fullscreen_animation_for_keyword} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: fullscreen_animation_for_keyword:[2776,"bool"]
     */
    public static final ABProp FULLSCREEN_ANIMATION_FOR_KEYWORD = new ABProp(2776, "false");

    /**
     * A/B prop {@code syncd_additional_mutations_count} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: syncd_additional_mutations_count:[2777,"int"]
     */
    public static final ABProp SYNCD_ADDITIONAL_MUTATIONS_COUNT = new ABProp(2777, "1");

    /**
     * A/B prop {@code smb_md_agent_chat_assignment_chats_reorder_on_chat_assignment_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_md_agent_chat_assignment_chats_reorder_on_chat_assignment_enabled:[2787,"bool"]
     */
    public static final ABProp SMB_MD_AGENT_CHAT_ASSIGNMENT_CHATS_REORDER_ON_CHAT_ASSIGNMENT_ENABLED = new ABProp(2787, "false");

    /**
     * A/B prop {@code smb_md_agent_chat_assignment_chats_reorder_on_chat_unassignment_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_md_agent_chat_assignment_chats_reorder_on_chat_unassignment_enabled:[2788,"bool"]
     */
    public static final ABProp SMB_MD_AGENT_CHAT_ASSIGNMENT_CHATS_REORDER_ON_CHAT_UNASSIGNMENT_ENABLED = new ABProp(2788, "false");

    /**
     * A/B prop {@code web_message_plugin_frontend_registration_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_message_plugin_frontend_registration_enabled:[2793,"bool"]
     */
    public static final ABProp WEB_MESSAGE_PLUGIN_FRONTEND_REGISTRATION_ENABLED = new ABProp(2793, "false");

    /**
     * A/B prop {@code enable_soox_message_receiving} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_soox_message_receiving:[2802,"bool"]
     */
    public static final ABProp ENABLE_SOOX_MESSAGE_RECEIVING = new ABProp(2802, "true");

    /**
     * A/B prop {@code enable_soox_message_sending} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_soox_message_sending:[2832,"bool"]
     */
    public static final ABProp ENABLE_SOOX_MESSAGE_SENDING = new ABProp(2832, "false");

    /**
     * A/B prop {@code supports_keep_in_chat_in_cag} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: supports_keep_in_chat_in_cag:[2844,"bool"]
     */
    public static final ABProp SUPPORTS_KEEP_IN_CHAT_IN_CAG = new ABProp(2844, "true");

    /**
     * A/B prop {@code utm_tracking_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: utm_tracking_enabled:[2895,"bool"]
     */
    public static final ABProp UTM_TRACKING_ENABLED = new ABProp(2895, "false");

    /**
     * A/B prop {@code utm_tracking_expiration_hours} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: utm_tracking_expiration_hours:[2896,"int"]
     */
    public static final ABProp UTM_TRACKING_EXPIRATION_HOURS = new ABProp(2896, "24");

    /**
     * A/B prop {@code wa_ctwa_web_thread_ad_attribution_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_ctwa_web_thread_ad_attribution_enabled:[2898,"bool"]
     */
    public static final ABProp WA_CTWA_WEB_THREAD_AD_ATTRIBUTION_ENABLED = new ABProp(2898, "false");

    /**
     * A/B prop {@code elevated_push_names_v2_m2_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: elevated_push_names_v2_m2_enabled:[2904,"bool"]
     */
    public static final ABProp ELEVATED_PUSH_NAMES_V2_M2_ENABLED = new ABProp(2904, "false");

    /**
     * A/B prop {@code smb_md_agent_chat_assignment_notifications_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_md_agent_chat_assignment_notifications_enabled:[2908,"bool"]
     */
    public static final ABProp SMB_MD_AGENT_CHAT_ASSIGNMENT_NOTIFICATIONS_ENABLED = new ABProp(2908, "false");

    /**
     * A/B prop {@code maximum_group_size_for_rcat} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: maximum_group_size_for_rcat:[2915,"int"]
     */
    public static final ABProp MAXIMUM_GROUP_SIZE_FOR_RCAT = new ABProp(2915, "100");

    /**
     * A/B prop {@code ctwa_smb_data_sharing_consent} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_smb_data_sharing_consent:[2934,"bool"]
     */
    public static final ABProp CTWA_SMB_DATA_SHARING_CONSENT = new ABProp(2934, "false");

    /**
     * A/B prop {@code message_edit_window_duration_seconds} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: message_edit_window_duration_seconds:[2983,"int"]
     */
    public static final ABProp MESSAGE_EDIT_WINDOW_DURATION_SECONDS = new ABProp(2983, "1200");

    /**
     * A/B prop {@code web_native_fetch_media_download} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_native_fetch_media_download:[3031,"bool"]
     */
    public static final ABProp WEB_NATIVE_FETCH_MEDIA_DOWNLOAD = new ABProp(3031, "false");

    /**
     * A/B prop {@code web_image_max_edge} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_image_max_edge:[3042,"int"]
     */
    public static final ABProp WEB_IMAGE_MAX_EDGE = new ABProp(3042, "1600");

    /**
     * A/B prop {@code polls_single_option_control_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: polls_single_option_control_enabled:[3050,"bool"]
     */
    public static final ABProp POLLS_SINGLE_OPTION_CONTROL_ENABLED = new ABProp(3050, "false");

    /**
     * A/B prop {@code payments_link_to_lite_consumer_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: payments_link_to_lite_consumer_enabled:[3051,"bool"]
     */
    public static final ABProp PAYMENTS_LINK_TO_LITE_CONSUMER_ENABLED = new ABProp(3051, "false");

    /**
     * A/B prop {@code wa_ctwa_web_entrypoint_home_header_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_ctwa_web_entrypoint_home_header_enabled:[3058,"bool"]
     */
    public static final ABProp WA_CTWA_WEB_ENTRYPOINT_HOME_HEADER_ENABLED = new ABProp(3058, "false");

    /**
     * A/B prop {@code original_quality_image_min_edge} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: original_quality_image_min_edge:[3068,"int"]
     */
    public static final ABProp ORIGINAL_QUALITY_IMAGE_MIN_EDGE = new ABProp(3068, "2560");

    /**
     * A/B prop {@code send_cag_member_revokes_as_GDM} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: send_cag_member_revokes_as_GDM:[3069,"bool"]
     */
    public static final ABProp SEND_CAG_MEMBER_REVOKES_AS_GDM = new ABProp(3069, "true");

    /**
     * A/B prop {@code wa_ctwa_web_entrypoint_home_header_dropdown_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_ctwa_web_entrypoint_home_header_dropdown_enabled:[3095,"bool"]
     */
    public static final ABProp WA_CTWA_WEB_ENTRYPOINT_HOME_HEADER_DROPDOWN_ENABLED = new ABProp(3095, "false");

    /**
     * A/B prop {@code enable_receiving_hd_photo_quality} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_receiving_hd_photo_quality:[3116,"bool"]
     */
    public static final ABProp ENABLE_RECEIVING_HD_PHOTO_QUALITY = new ABProp(3116, "false");

    /**
     * A/B prop {@code smb_rambutan_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_rambutan_enabled:[3124,"bool"]
     */
    public static final ABProp SMB_RAMBUTAN_ENABLED = new ABProp(3124, "false");

    /**
     * A/B prop {@code web_browser_quota_threshold} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_browser_quota_threshold:[3134,"int"]
     */
    public static final ABProp WEB_BROWSER_QUOTA_THRESHOLD = new ABProp(3134, "100");

    /**
     * A/B prop {@code web_browser_min_storage_quota} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_browser_min_storage_quota:[3135,"int"]
     */
    public static final ABProp WEB_BROWSER_MIN_STORAGE_QUOTA = new ABProp(3135, "5");

    /**
     * A/B prop {@code web_original_photo_quality_upload_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_original_photo_quality_upload_enabled:[3136,"bool"]
     */
    public static final ABProp WEB_ORIGINAL_PHOTO_QUALITY_UPLOAD_ENABLED = new ABProp(3136, "false");

    /**
     * A/B prop {@code pinned_messages_m1_receiver} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: pinned_messages_m1_receiver:[3139,"bool"]
     */
    public static final ABProp PINNED_MESSAGES_M1_RECEIVER = new ABProp(3139, "true");

    /**
     * A/B prop {@code pinned_messages_m1_sender} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: pinned_messages_m1_sender:[3140,"bool"]
     */
    public static final ABProp PINNED_MESSAGES_M1_SENDER = new ABProp(3140, "true");

    /**
     * A/B prop {@code pinned_messages_m2} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: pinned_messages_m2:[3141,"bool"]
     */
    public static final ABProp PINNED_MESSAGES_M2 = new ABProp(3141, "false");

    /**
     * A/B prop {@code parent_group_subgroup_filter} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: parent_group_subgroup_filter:[3147,"bool"]
     */
    public static final ABProp PARENT_GROUP_SUBGROUP_FILTER = new ABProp(3147, "false");

    /**
     * A/B prop {@code web_deprecate_mms4_hash_based_download} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_deprecate_mms4_hash_based_download:[3152,"bool"]
     */
    public static final ABProp WEB_DEPRECATE_MMS4_HASH_BASED_DOWNLOAD = new ABProp(3152, "false");

    /**
     * A/B prop {@code polls_notification_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: polls_notification_enabled:[3158,"bool"]
     */
    public static final ABProp POLLS_NOTIFICATION_ENABLED = new ABProp(3158, "true");

    /**
     * A/B prop {@code group_suspend_v2_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: group_suspend_v2_enabled:[3180,"bool"]
     */
    public static final ABProp GROUP_SUSPEND_V2_ENABLED = new ABProp(3180, "false");

    /**
     * A/B prop {@code default_video_limit_mb} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: default_video_limit_mb:[3185,"int"]
     */
    public static final ABProp DEFAULT_VIDEO_LIMIT_MB = new ABProp(3185, "16");

    /**
     * A/B prop {@code web_e2e_backfill_expire_time} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_e2e_backfill_expire_time:[3234,"int"]
     */
    public static final ABProp WEB_E2E_BACKFILL_EXPIRE_TIME = new ABProp(3234, "5");

    /**
     * A/B prop {@code order_messages_ephemeral_exception_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: order_messages_ephemeral_exception_enabled:[3240,"bool"]
     */
    public static final ABProp ORDER_MESSAGES_EPHEMERAL_EXCEPTION_ENABLED = new ABProp(3240, "false");

    /**
     * A/B prop {@code group_chat_profile_pictures_v2_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: group_chat_profile_pictures_v2_enabled:[3261,"bool"]
     */
    public static final ABProp GROUP_CHAT_PROFILE_PICTURES_V2_ENABLED = new ABProp(3261, "false");

    /**
     * A/B prop {@code message_edit_client_entry_point_limit_seconds} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: message_edit_client_entry_point_limit_seconds:[3272,"int"]
     */
    public static final ABProp MESSAGE_EDIT_CLIENT_ENTRY_POINT_LIMIT_SECONDS = new ABProp(3272, "900");

    /**
     * A/B prop {@code wa_ctwa_web_fetch_linked_accounts_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_ctwa_web_fetch_linked_accounts_enabled:[3294,"bool"]
     */
    public static final ABProp WA_CTWA_WEB_FETCH_LINKED_ACCOUNTS_ENABLED = new ABProp(3294, "false");

    /**
     * A/B prop {@code enable_days_since_receive_logging} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_days_since_receive_logging:[3322,"bool"]
     */
    public static final ABProp ENABLE_DAYS_SINCE_RECEIVE_LOGGING = new ABProp(3322, "false");

    /**
     * A/B prop {@code ctwa_smb_data_sharing_opt_in_cool_off_period} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_smb_data_sharing_opt_in_cool_off_period:[3331,"int"]
     */
    public static final ABProp CTWA_SMB_DATA_SHARING_OPT_IN_COOL_OFF_PERIOD = new ABProp(3331, "259200");

    /**
     * A/B prop {@code history_sync_on_demand} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: history_sync_on_demand:[3337,"bool"]
     */
    public static final ABProp HISTORY_SYNC_ON_DEMAND = new ABProp(3337, "false");

    /**
     * A/B prop {@code hqp_log_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: hqp_log_enabled:[3349,"bool"]
     */
    public static final ABProp HQP_LOG_ENABLED = new ABProp(3349, "false");

    /**
     * A/B prop {@code ptv_receiving_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ptv_receiving_enabled:[3355,"bool"]
     */
    public static final ABProp PTV_RECEIVING_ENABLED = new ABProp(3355, "false");

    /**
     * A/B prop {@code ptv_max_duration_seconds} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ptv_max_duration_seconds:[3356,"int"]
     */
    public static final ABProp PTV_MAX_DURATION_SECONDS = new ABProp(3356, "60");

    /**
     * A/B prop {@code calling_lid_version} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: calling_lid_version:[3358,"int"]
     */
    public static final ABProp CALLING_LID_VERSION = new ABProp(3358, "0");

    /**
     * A/B prop {@code wa_ctwa_web_entrypoint_manage_ads_home_header_dropdown_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_ctwa_web_entrypoint_manage_ads_home_header_dropdown_enabled:[3376,"bool"]
     */
    public static final ABProp WA_CTWA_WEB_ENTRYPOINT_MANAGE_ADS_HOME_HEADER_DROPDOWN_ENABLED = new ABProp(3376, "false");

    /**
     * A/B prop {@code polls_single_option_sender_control_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: polls_single_option_sender_control_enabled:[3433,"bool"]
     */
    public static final ABProp POLLS_SINGLE_OPTION_SENDER_CONTROL_ENABLED = new ABProp(3433, "false");

    /**
     * A/B prop {@code polls_single_option_receiver_control_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: polls_single_option_receiver_control_enabled:[3437,"bool"]
     */
    public static final ABProp POLLS_SINGLE_OPTION_RECEIVER_CONTROL_ENABLED = new ABProp(3437, "true");

    /**
     * A/B prop {@code ptv_autoplay_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ptv_autoplay_enabled:[3482,"bool"]
     */
    public static final ABProp PTV_AUTOPLAY_ENABLED = new ABProp(3482, "true");

    /**
     * A/B prop {@code ptv_autoplay_loop_limit} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ptv_autoplay_loop_limit:[3483,"int"]
     */
    public static final ABProp PTV_AUTOPLAY_LOOP_LIMIT = new ABProp(3483, "3");

    /**
     * A/B prop {@code qp_campaign_client_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: qp_campaign_client_enabled:[3536,"bool"]
     */
    public static final ABProp QP_CAMPAIGN_CLIENT_ENABLED = new ABProp(3536, "false");

    /**
     * A/B prop {@code animated_emojis_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: animated_emojis_enabled:[3575,"bool"]
     */
    public static final ABProp ANIMATED_EMOJIS_ENABLED = new ABProp(3575, "false");

    /**
     * A/B prop {@code placeholder_message_resend} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: placeholder_message_resend:[3579,"bool"]
     */
    public static final ABProp PLACEHOLDER_MESSAGE_RESEND = new ABProp(3579, "false");

    /**
     * A/B prop {@code placeholder_message_resend_maximum_days_limit} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: placeholder_message_resend_maximum_days_limit:[3639,"int"]
     */
    public static final ABProp PLACEHOLDER_MESSAGE_RESEND_MAXIMUM_DAYS_LIMIT = new ABProp(3639, "14");

    /**
     * A/B prop {@code default_audio_limit_mb} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: default_audio_limit_mb:[3657,"int"]
     */
    public static final ABProp DEFAULT_AUDIO_LIMIT_MB = new ABProp(3657, "16");

    /**
     * A/B prop {@code default_status_media_limit_mb} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: default_status_media_limit_mb:[3659,"int"]
     */
    public static final ABProp DEFAULT_STATUS_MEDIA_LIMIT_MB = new ABProp(3659, "16");

    /**
     * A/B prop {@code default_media_limit_mb} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: default_media_limit_mb:[3660,"int"]
     */
    public static final ABProp DEFAULT_MEDIA_LIMIT_MB = new ABProp(3660, "16");

    /**
     * A/B prop {@code service_improvement_opt_out_flag} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: service_improvement_opt_out_flag:[3664,"bool"]
     */
    public static final ABProp SERVICE_IMPROVEMENT_OPT_OUT_FLAG = new ABProp(3664, "false");

    /**
     * A/B prop {@code report_to_admin_kill_switch} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: report_to_admin_kill_switch:[3695,"bool"]
     */
    public static final ABProp REPORT_TO_ADMIN_KILL_SWITCH = new ABProp(3695, "false");

    /**
     * A/B prop {@code web_message_processing_cache_size} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_message_processing_cache_size:[3728,"int"]
     */
    public static final ABProp WEB_MESSAGE_PROCESSING_CACHE_SIZE = new ABProp(3728, "400");

    /**
     * A/B prop {@code pinned_messages_m2_pin_max} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: pinned_messages_m2_pin_max:[3732,"int"]
     */
    public static final ABProp PINNED_MESSAGES_M2_PIN_MAX = new ABProp(3732, "1");

    /**
     * A/B prop {@code newsletter_tos_notice_id} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: newsletter_tos_notice_id:[3810,"string"]
     */
    public static final ABProp NEWSLETTER_TOS_NOTICE_ID = new ABProp(3810, "20601216");

    /**
     * A/B prop {@code history_sync_on_demand_message_count} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: history_sync_on_demand_message_count:[3811,"int"]
     */
    public static final ABProp HISTORY_SYNC_ON_DEMAND_MESSAGE_COUNT = new ABProp(3811, "50");

    /**
     * A/B prop {@code unified_otp_copy_code_url} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: unified_otp_copy_code_url:[3827,"string"]
     */
    public static final ABProp UNIFIED_OTP_COPY_CODE_URL = new ABProp(3827, "https://www.whatsapp.com/otp/copy/");

    /**
     * A/B prop {@code unified_otp_retriever_url} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: unified_otp_retriever_url:[3828,"string"]
     */
    public static final ABProp UNIFIED_OTP_RETRIEVER_URL = new ABProp(3828, "https://www.whatsapp.com/otp/code");

    /**
     * A/B prop {@code newsletter_creation_tos_id} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: newsletter_creation_tos_id:[3834,"string"]
     */
    public static final ABProp NEWSLETTER_CREATION_TOS_ID = new ABProp(3834, "20601217");

    /**
     * A/B prop {@code newsletter_creation_nux_id} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: newsletter_creation_nux_id:[3835,"string"]
     */
    public static final ABProp NEWSLETTER_CREATION_NUX_ID = new ABProp(3835, "20601218");

    /**
     * A/B prop {@code ts_session_duration_ms} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ts_session_duration_ms:[3860,"int"]
     */
    public static final ABProp TS_SESSION_DURATION_MS = new ABProp(3860, "6e5");

    /**
     * A/B prop {@code channels_enabled} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_enabled:[3877,"int"]
     */
    public static final ABProp CHANNELS_ENABLED = new ABProp(3877, "0");

    /**
     * A/B prop {@code channels_creation_enabled} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_creation_enabled:[3878,"int"]
     */
    public static final ABProp CHANNELS_CREATION_ENABLED = new ABProp(3878, "0");

    /**
     * A/B prop {@code channels_directory_enabled} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_directory_enabled:[3879,"int"]
     */
    public static final ABProp CHANNELS_DIRECTORY_ENABLED = new ABProp(3879, "0");

    /**
     * A/B prop {@code history_sync_on_demand_timeout_ms} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: history_sync_on_demand_timeout_ms:[3882,"int"]
     */
    public static final ABProp HISTORY_SYNC_ON_DEMAND_TIMEOUT_MS = new ABProp(3882, "1e4");

    /**
     * A/B prop {@code channel_supported_message_types} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channel_supported_message_types:[3919,"string"]
     */
    public static final ABProp CHANNEL_SUPPORTED_MESSAGE_TYPES = new ABProp(3919, "1, 2, 3, 5, 9, 10, 12, 15");

    /**
     * A/B prop {@code hd_video_label_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: hd_video_label_enabled:[3934,"bool"]
     */
    public static final ABProp HD_VIDEO_LABEL_ENABLED = new ABProp(3934, "false");

    /**
     * A/B prop {@code bonsai_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: bonsai_enabled:[4010,"bool"]
     */
    public static final ABProp BONSAI_ENABLED = new ABProp(4010, "false");

    /**
     * A/B prop {@code group_mentions_in_subgroups} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: group_mentions_in_subgroups:[4087,"bool"]
     */
    public static final ABProp GROUP_MENTIONS_IN_SUBGROUPS = new ABProp(4087, "false");

    /**
     * A/B prop {@code history_sync_on_demand_with_android_beta} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: history_sync_on_demand_with_android_beta:[4135,"bool"]
     */
    public static final ABProp HISTORY_SYNC_ON_DEMAND_WITH_ANDROID_BETA = new ABProp(4135, "false");

    /**
     * A/B prop {@code hd_video_definition_min_edge} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: hd_video_definition_min_edge:[4171,"int"]
     */
    public static final ABProp HD_VIDEO_DEFINITION_MIN_EDGE = new ABProp(4171, "720");

    /**
     * A/B prop {@code hd_video_definition_max_edge} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: hd_video_definition_max_edge:[4172,"int"]
     */
    public static final ABProp HD_VIDEO_DEFINITION_MAX_EDGE = new ABProp(4172, "864");

    /**
     * A/B prop {@code hd_video_definition_min_edge_with_max_edge} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: hd_video_definition_min_edge_with_max_edge:[4175,"int"]
     */
    public static final ABProp HD_VIDEO_DEFINITION_MIN_EDGE_WITH_MAX_EDGE = new ABProp(4175, "480");

    /**
     * A/B prop {@code payments_br_content_optimization_variant} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: payments_br_content_optimization_variant:[4248,"int"]
     */
    public static final ABProp PAYMENTS_BR_CONTENT_OPTIMIZATION_VARIANT = new ABProp(4248, "0");

    /**
     * A/B prop {@code recommended_channels_background_refresh} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: recommended_channels_background_refresh:[4309,"int"]
     */
    public static final ABProp RECOMMENDED_CHANNELS_BACKGROUND_REFRESH = new ABProp(4309, "144e5");

    /**
     * A/B prop {@code channel_pull_message_updates_threshold_seconds} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channel_pull_message_updates_threshold_seconds:[4326,"int"]
     */
    public static final ABProp CHANNEL_PULL_MESSAGE_UPDATES_THRESHOLD_SECONDS = new ABProp(4326, "120");

    /**
     * A/B prop {@code history_sync_on_demand_failure_limit} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: history_sync_on_demand_failure_limit:[4364,"int"]
     */
    public static final ABProp HISTORY_SYNC_ON_DEMAND_FAILURE_LIMIT = new ABProp(4364, "10");

    /**
     * A/B prop {@code history_sync_on_demand_cooldown_sec} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: history_sync_on_demand_cooldown_sec:[4365,"int"]
     */
    public static final ABProp HISTORY_SYNC_ON_DEMAND_COOLDOWN_SEC = new ABProp(4365, "7200");

    /**
     * A/B prop {@code history_sync_on_demand_request_send_killswitch} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: history_sync_on_demand_request_send_killswitch:[4366,"bool"]
     */
    public static final ABProp HISTORY_SYNC_ON_DEMAND_REQUEST_SEND_KILLSWITCH = new ABProp(4366, "true");

    /**
     * A/B prop {@code bonsai_ptt_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: bonsai_ptt_enabled:[4416,"bool"]
     */
    public static final ABProp BONSAI_PTT_ENABLED = new ABProp(4416, "false");

    /**
     * A/B prop {@code bonsai_update_interval} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: bonsai_update_interval:[4417,"int"]
     */
    public static final ABProp BONSAI_UPDATE_INTERVAL = new ABProp(4417, "86400");

    /**
     * A/B prop {@code business_tool_enhanced_logging} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: business_tool_enhanced_logging:[4427,"bool"]
     */
    public static final ABProp BUSINESS_TOOL_ENHANCED_LOGGING = new ABProp(4427, "false");

    /**
     * A/B prop {@code pnh_cag_disable_reactions_group_size} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: pnh_cag_disable_reactions_group_size:[4495,"int"]
     */
    public static final ABProp PNH_CAG_DISABLE_REACTIONS_GROUP_SIZE = new ABProp(4495, "1e4");

    /**
     * A/B prop {@code in_app_comms_manage_ads_web_banner_campaign_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: in_app_comms_manage_ads_web_banner_campaign_enabled:[4542,"bool"]
     */
    public static final ABProp IN_APP_COMMS_MANAGE_ADS_WEB_BANNER_CAMPAIGN_ENABLED = new ABProp(4542, "false");

    /**
     * A/B prop {@code web_premium_messages_interactivity_rendering_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_premium_messages_interactivity_rendering_enabled:[4596,"bool"]
     */
    public static final ABProp WEB_PREMIUM_MESSAGES_INTERACTIVITY_RENDERING_ENABLED = new ABProp(4596, "false");

    /**
     * A/B prop {@code smb_premium_messages_click_logging_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_premium_messages_click_logging_enabled:[4657,"bool"]
     */
    public static final ABProp SMB_PREMIUM_MESSAGES_CLICK_LOGGING_ENABLED = new ABProp(4657, "false");

    /**
     * A/B prop {@code enable_clear_formatted_preview} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_clear_formatted_preview:[4659,"bool"]
     */
    public static final ABProp ENABLE_CLEAR_FORMATTED_PREVIEW = new ABProp(4659, "false");

    /**
     * A/B prop {@code carousel_message_client_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: carousel_message_client_enabled:[4668,"bool"]
     */
    public static final ABProp CAROUSEL_MESSAGE_CLIENT_ENABLED = new ABProp(4668, "false");

    /**
     * A/B prop {@code channel_view_counts_enabled} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channel_view_counts_enabled:[4721,"int"]
     */
    public static final ABProp CHANNEL_VIEW_COUNTS_ENABLED = new ABProp(4721, "0");

    /**
     * A/B prop {@code web_sticker_suggestions_enable} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_sticker_suggestions_enable:[4726,"bool"]
     */
    public static final ABProp WEB_STICKER_SUGGESTIONS_ENABLE = new ABProp(4726, "false");

    /**
     * A/B prop {@code bonsai_ti_timeout_duration_ms} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: bonsai_ti_timeout_duration_ms:[4736,"int"]
     */
    public static final ABProp BONSAI_TI_TIMEOUT_DURATION_MS = new ABProp(4736, "1e4");

    /**
     * A/B prop {@code username_contact_display} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: username_contact_display:[4746,"bool"]
     */
    public static final ABProp USERNAME_CONTACT_DISPLAY = new ABProp(4746, "false");

    /**
     * A/B prop {@code in_app_support_capi_number_prefixes} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: in_app_support_capi_number_prefixes:[4799,"string"]
     */
    public static final ABProp IN_APP_SUPPORT_CAPI_NUMBER_PREFIXES = new ABProp(4799, "155178684");

    /**
     * A/B prop {@code low_cache_hit_rate_media_types} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: low_cache_hit_rate_media_types:[4836,"string"]
     */
    public static final ABProp LOW_CACHE_HIT_RATE_MEDIA_TYPES = new ABProp(4836, "ptt,audio,document,ppic");

    /**
     * A/B prop {@code wabai_message_rendering_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wabai_message_rendering_enabled:[4873,"bool"]
     */
    public static final ABProp WABAI_MESSAGE_RENDERING_ENABLED = new ABProp(4873, "false");

    /**
     * A/B prop {@code row_buyer_order_revamp_m0_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: row_buyer_order_revamp_m0_enabled:[4893,"bool"]
     */
    public static final ABProp ROW_BUYER_ORDER_REVAMP_M0_ENABLED = new ABProp(4893, "false");

    /**
     * A/B prop {@code ts_surface_killswitch} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ts_surface_killswitch:[4929,"int"]
     */
    public static final ABProp TS_SURFACE_KILLSWITCH = new ABProp(4929, "0");

    /**
     * A/B prop {@code enable_spam_report_iq_with_privacy_token} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_spam_report_iq_with_privacy_token:[4991,"bool"]
     */
    public static final ABProp ENABLE_SPAM_REPORT_IQ_WITH_PRIVACY_TOKEN = new ABProp(4991, "false");

    /**
     * A/B prop {@code smb_labels_ctwa_data_sharing} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_labels_ctwa_data_sharing:[5009,"bool"]
     */
    public static final ABProp SMB_LABELS_CTWA_DATA_SHARING = new ABProp(5009, "false");

    /**
     * A/B prop {@code community_general_chat_UI_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: community_general_chat_UI_enabled:[5021,"bool"]
     */
    public static final ABProp COMMUNITY_GENERAL_CHAT_UI_ENABLED = new ABProp(5021, "false");

    /**
     * A/B prop {@code smb_premium_messages_url_cta_alert_dialog_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_premium_messages_url_cta_alert_dialog_enabled:[5044,"bool"]
     */
    public static final ABProp SMB_PREMIUM_MESSAGES_URL_CTA_ALERT_DIALOG_ENABLED = new ABProp(5044, "true");

    /**
     * A/B prop {@code pnh_cag_disable_polls_group_size} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: pnh_cag_disable_polls_group_size:[5056,"int"]
     */
    public static final ABProp PNH_CAG_DISABLE_POLLS_GROUP_SIZE = new ABProp(5056, "1e4");

    /**
     * A/B prop {@code parent_group_allow_member_suggest_existing_m3_sender} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: parent_group_allow_member_suggest_existing_m3_sender:[5077,"bool"]
     */
    public static final ABProp PARENT_GROUP_ALLOW_MEMBER_SUGGEST_EXISTING_M3_SENDER = new ABProp(5077, "false");

    /**
     * A/B prop {@code parent_group_allow_member_suggest_existing_m3_receiver} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: parent_group_allow_member_suggest_existing_m3_receiver:[5078,"bool"]
     */
    public static final ABProp PARENT_GROUP_ALLOW_MEMBER_SUGGEST_EXISTING_M3_RECEIVER = new ABProp(5078, "false");

    /**
     * A/B prop {@code web_noncritical_history_sync_message_processing_break_iteration} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_noncritical_history_sync_message_processing_break_iteration:[5106,"int"]
     */
    public static final ABProp WEB_NONCRITICAL_HISTORY_SYNC_MESSAGE_PROCESSING_BREAK_ITERATION = new ABProp(5106, "100");

    /**
     * A/B prop {@code buyer_initiated_order_request_variant_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: buyer_initiated_order_request_variant_enabled:[5114,"bool"]
     */
    public static final ABProp BUYER_INITIATED_ORDER_REQUEST_VARIANT_ENABLED = new ABProp(5114, "false");

    /**
     * A/B prop {@code channels_directory_v2_filter_types} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_directory_v2_filter_types:[5127,"string"]
     */
    public static final ABProp CHANNELS_DIRECTORY_V2_FILTER_TYPES = new ABProp(5127, "");

    /**
     * A/B prop {@code channel_reactions_sender_list_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channel_reactions_sender_list_enabled:[5185,"bool"]
     */
    public static final ABProp CHANNEL_REACTIONS_SENDER_LIST_ENABLED = new ABProp(5185, "true");

    /**
     * A/B prop {@code seller_orders_management_revamp} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: seller_orders_management_revamp:[5190,"bool"]
     */
    public static final ABProp SELLER_ORDERS_MANAGEMENT_REVAMP = new ABProp(5190, "false");

    /**
     * A/B prop {@code channels_directory_search_debounce_ms} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_directory_search_debounce_ms:[5204,"int"]
     */
    public static final ABProp CHANNELS_DIRECTORY_SEARCH_DEBOUNCE_MS = new ABProp(5204, "250");

    /**
     * A/B prop {@code wabai_message_feedback_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wabai_message_feedback_enabled:[5215,"bool"]
     */
    public static final ABProp WABAI_MESSAGE_FEEDBACK_ENABLED = new ABProp(5215, "false");

    /**
     * A/B prop {@code channels_followers_list_cache_refresh_milliseconds} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_followers_list_cache_refresh_milliseconds:[5217,"int"]
     */
    public static final ABProp CHANNELS_FOLLOWERS_LIST_CACHE_REFRESH_MILLISECONDS = new ABProp(5217, "6e4");

    /**
     * A/B prop {@code web_offline_dynamic_batch_size_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_offline_dynamic_batch_size_enabled:[5271,"bool"]
     */
    public static final ABProp WEB_OFFLINE_DYNAMIC_BATCH_SIZE_ENABLED = new ABProp(5271, "false");

    /**
     * A/B prop {@code blue_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: blue_enabled:[5276,"bool"]
     */
    public static final ABProp BLUE_ENABLED = new ABProp(5276, "false");

    /**
     * A/B prop {@code blue_education_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: blue_education_enabled:[5295,"bool"]
     */
    public static final ABProp BLUE_EDUCATION_ENABLED = new ABProp(5295, "false");

    /**
     * A/B prop {@code web_offline_dynamic_batch_config} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_offline_dynamic_batch_config:[5297,"string"]
     */
    public static final ABProp WEB_OFFLINE_DYNAMIC_BATCH_CONFIG = new ABProp(5297, "{\"version\": \"progressive\", \"multiplier\": 0.25}");

    /**
     * A/B prop {@code channels_directory_v2_cache_refresh_interval_ms} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_directory_v2_cache_refresh_interval_ms:[5304,"int"]
     */
    public static final ABProp CHANNELS_DIRECTORY_V2_CACHE_REFRESH_INTERVAL_MS = new ABProp(5304, "18e5");

    /**
     * A/B prop {@code dm_initiator_trigger} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: dm_initiator_trigger:[5309,"bool"]
     */
    public static final ABProp DM_INITIATOR_TRIGGER = new ABProp(5309, "false");

    /**
     * A/B prop {@code premium_blue_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: premium_blue_enabled:[5318,"bool"]
     */
    public static final ABProp PREMIUM_BLUE_ENABLED = new ABProp(5318, "false");

    /**
     * A/B prop {@code extensions_geoblocking_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: extensions_geoblocking_enabled:[5333,"bool"]
     */
    public static final ABProp EXTENSIONS_GEOBLOCKING_ENABLED = new ABProp(5333, "false");

    /**
     * A/B prop {@code web_evolve_about_send_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_evolve_about_send_enabled:[5347,"bool"]
     */
    public static final ABProp WEB_EVOLVE_ABOUT_SEND_ENABLED = new ABProp(5347, "false");

    /**
     * A/B prop {@code bonsai_entry_point_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: bonsai_entry_point_enabled:[5362,"bool"]
     */
    public static final ABProp BONSAI_ENTRY_POINT_ENABLED = new ABProp(5362, "false");

    /**
     * A/B prop {@code community_general_chat_create_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: community_general_chat_create_enabled:[5453,"bool"]
     */
    public static final ABProp COMMUNITY_GENERAL_CHAT_CREATE_ENABLED = new ABProp(5453, "false");

    /**
     * A/B prop {@code channels_max_messages_batch_pull} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_max_messages_batch_pull:[5494,"int"]
     */
    public static final ABProp CHANNELS_MAX_MESSAGES_BATCH_PULL = new ABProp(5494, "100");

    /**
     * A/B prop {@code web_resume_optimized_read_receipt_send_interval} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_resume_optimized_read_receipt_send_interval:[5502,"int"]
     */
    public static final ABProp WEB_RESUME_OPTIMIZED_READ_RECEIPT_SEND_INTERVAL = new ABProp(5502, "500");

    /**
     * A/B prop {@code web_pre_acks_m3_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_pre_acks_m3_enabled:[5521,"bool"]
     */
    public static final ABProp WEB_PRE_ACKS_M3_ENABLED = new ABProp(5521, "false");

    /**
     * A/B prop {@code ctwa_manage_ads_tab_web} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_manage_ads_tab_web:[5554,"bool"]
     */
    public static final ABProp CTWA_MANAGE_ADS_TAB_WEB = new ABProp(5554, "false");

    /**
     * A/B prop {@code dm_reliability_logging} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: dm_reliability_logging:[5580,"bool"]
     */
    public static final ABProp DM_RELIABILITY_LOGGING = new ABProp(5580, "false");

    /**
     * A/B prop {@code newsletter_tos_notice_id_smb_web} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: newsletter_tos_notice_id_smb_web:[5597,"string"]
     */
    public static final ABProp NEWSLETTER_TOS_NOTICE_ID_SMB_WEB = new ABProp(5597, "20601216");

    /**
     * A/B prop {@code newsletter_creation_tos_id_smb_web} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: newsletter_creation_tos_id_smb_web:[5598,"string"]
     */
    public static final ABProp NEWSLETTER_CREATION_TOS_ID_SMB_WEB = new ABProp(5598, "20601217");

    /**
     * A/B prop {@code ctwa_smb_data_sharing_settings_killswitch} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_smb_data_sharing_settings_killswitch:[5615,"bool"]
     */
    public static final ABProp CTWA_SMB_DATA_SHARING_SETTINGS_KILLSWITCH = new ABProp(5615, "false");

    /**
     * A/B prop {@code rt_receive_reporting_tag} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: rt_receive_reporting_tag:[5718,"bool"]
     */
    public static final ABProp RT_RECEIVE_REPORTING_TAG = new ABProp(5718, "true");

    /**
     * A/B prop {@code wabai_consent_cooldown} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wabai_consent_cooldown:[5746,"int"]
     */
    public static final ABProp WABAI_CONSENT_COOLDOWN = new ABProp(5746, "-1");

    /**
     * A/B prop {@code wabai_consent_required} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wabai_consent_required:[5747,"bool"]
     */
    public static final ABProp WABAI_CONSENT_REQUIRED = new ABProp(5747, "false");

    /**
     * A/B prop {@code order_statuses_revamp_m1_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: order_statuses_revamp_m1_enabled:[5770,"bool"]
     */
    public static final ABProp ORDER_STATUSES_REVAMP_M1_ENABLED = new ABProp(5770, "false");

    /**
     * A/B prop {@code bot_commands_1p_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: bot_commands_1p_enabled:[5811,"bool"]
     */
    public static final ABProp BOT_COMMANDS_1P_ENABLED = new ABProp(5811, "false");

    /**
     * A/B prop {@code evolve_about_m1_receiver_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: evolve_about_m1_receiver_enabled:[5839,"bool"]
     */
    public static final ABProp EVOLVE_ABOUT_M1_RECEIVER_ENABLED = new ABProp(5839, "false");

    /**
     * A/B prop {@code blue_strings_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: blue_strings_enabled:[5846,"bool"]
     */
    public static final ABProp BLUE_STRINGS_ENABLED = new ABProp(5846, "false");

    /**
     * A/B prop {@code channels_directory_page_size} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_directory_page_size:[5853,"int"]
     */
    public static final ABProp CHANNELS_DIRECTORY_PAGE_SIZE = new ABProp(5853, "50");

    /**
     * A/B prop {@code mm_data_sharing_disclosure_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: mm_data_sharing_disclosure_enabled:[5869,"bool"]
     */
    public static final ABProp MM_DATA_SHARING_DISCLOSURE_ENABLED = new ABProp(5869, "false");

    /**
     * A/B prop {@code channels_ptt_sender_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_ptt_sender_enabled:[5875,"bool"]
     */
    public static final ABProp CHANNELS_PTT_SENDER_ENABLED = new ABProp(5875, "false");

    /**
     * A/B prop {@code blue_client_p0_logging_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: blue_client_p0_logging_enabled:[5918,"bool"]
     */
    public static final ABProp BLUE_CLIENT_P0_LOGGING_ENABLED = new ABProp(5918, "false");

    /**
     * A/B prop {@code bot_3p_status} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: bot_3p_status:[5985,"int"]
     */
    public static final ABProp BOT_3P_STATUS = new ABProp(5985, "0");

    /**
     * A/B prop {@code data_sharing_transparency_indicator_duration} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: data_sharing_transparency_indicator_duration:[5990,"int"]
     */
    public static final ABProp DATA_SHARING_TRANSPARENCY_INDICATOR_DURATION = new ABProp(5990, "604800");

    /**
     * A/B prop {@code unified_poll_vote_addon_infra_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: unified_poll_vote_addon_infra_enabled:[6046,"bool"]
     */
    public static final ABProp UNIFIED_POLL_VOTE_ADDON_INFRA_ENABLED = new ABProp(6046, "false");

    /**
     * A/B prop {@code fmx_ctwa_kill_switch} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: fmx_ctwa_kill_switch:[6061,"bool"]
     */
    public static final ABProp FMX_CTWA_KILL_SWITCH = new ABProp(6061, "false");

    /**
     * A/B prop {@code smb_label_improvements_reordering} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_label_improvements_reordering:[6162,"bool"]
     */
    public static final ABProp SMB_LABEL_IMPROVEMENTS_REORDERING = new ABProp(6162, "false");

    /**
     * A/B prop {@code evolve_about_m1_receiver_for_new_surfaces_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: evolve_about_m1_receiver_for_new_surfaces_enabled:[6172,"bool"]
     */
    public static final ABProp EVOLVE_ABOUT_M1_RECEIVER_FOR_NEW_SURFACES_ENABLED = new ABProp(6172, "false");

    /**
     * A/B prop {@code event_name_length_limit} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: event_name_length_limit:[6207,"int"]
     */
    public static final ABProp EVENT_NAME_LENGTH_LIMIT = new ABProp(6207, "100");

    /**
     * A/B prop {@code event_description_length_limit} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: event_description_length_limit:[6208,"int"]
     */
    public static final ABProp EVENT_DESCRIPTION_LENGTH_LIMIT = new ABProp(6208, "2048");

    /**
     * A/B prop {@code ctwa_entry_point_config_fetch_threshhold} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_entry_point_config_fetch_threshhold:[6214,"int"]
     */
    public static final ABProp CTWA_ENTRY_POINT_CONFIG_FETCH_THRESHHOLD = new ABProp(6214, "432e5");

    /**
     * A/B prop {@code kill_switch_ctwa_ml_entry_point_config} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: kill_switch_ctwa_ml_entry_point_config:[6215,"bool"]
     */
    public static final ABProp KILL_SWITCH_CTWA_ML_ENTRY_POINT_CONFIG = new ABProp(6215, "true");

    /**
     * A/B prop {@code ctwa_manage_ads_tab_web_ad_actions_menu} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_manage_ads_tab_web_ad_actions_menu:[6237,"bool"]
     */
    public static final ABProp CTWA_MANAGE_ADS_TAB_WEB_AD_ACTIONS_MENU = new ABProp(6237, "false");

    /**
     * A/B prop {@code ctwa_manage_ads_tab_web_ad_metrics} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_manage_ads_tab_web_ad_metrics:[6238,"bool"]
     */
    public static final ABProp CTWA_MANAGE_ADS_TAB_WEB_AD_METRICS = new ABProp(6238, "false");

    /**
     * A/B prop {@code system_msg_text_styling} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: system_msg_text_styling:[6246,"bool"]
     */
    public static final ABProp SYSTEM_MSG_TEXT_STYLING = new ABProp(6246, "false");

    /**
     * A/B prop {@code system_msg_truncation} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: system_msg_truncation:[6247,"bool"]
     */
    public static final ABProp SYSTEM_MSG_TRUNCATION = new ABProp(6247, "false");

    /**
     * A/B prop {@code bonsai_chat_list_entry_point_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: bonsai_chat_list_entry_point_enabled:[6251,"bool"]
     */
    public static final ABProp BONSAI_CHAT_LIST_ENTRY_POINT_ENABLED = new ABProp(6251, "false");

    /**
     * A/B prop {@code blue_profile_locked_ui_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: blue_profile_locked_ui_enabled:[6337,"bool"]
     */
    public static final ABProp BLUE_PROFILE_LOCKED_UI_ENABLED = new ABProp(6337, "false");

    /**
     * A/B prop {@code web_skip_expired_status_error} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_skip_expired_status_error:[6391,"bool"]
     */
    public static final ABProp WEB_SKIP_EXPIRED_STATUS_ERROR = new ABProp(6391, "false");

    /**
     * A/B prop {@code channels_multi_admin_max_admin_count} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_multi_admin_max_admin_count:[6461,"int"]
     */
    public static final ABProp CHANNELS_MULTI_ADMIN_MAX_ADMIN_COUNT = new ABProp(6461, "16");

    /**
     * A/B prop {@code is_capi_groups_alpha_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: is_capi_groups_alpha_enabled:[6473,"bool"]
     */
    public static final ABProp IS_CAPI_GROUPS_ALPHA_ENABLED = new ABProp(6473, "false");

    /**
     * A/B prop {@code newsletter_admin_invite_tos_id} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: newsletter_admin_invite_tos_id:[6498,"string"]
     */
    public static final ABProp NEWSLETTER_ADMIN_INVITE_TOS_ID = new ABProp(6498, "20610101");

    /**
     * A/B prop {@code newsletter_admin_invite_tos_id_smb_web} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: newsletter_admin_invite_tos_id_smb_web:[6536,"string"]
     */
    public static final ABProp NEWSLETTER_ADMIN_INVITE_TOS_ID_SMB_WEB = new ABProp(6536, "20610104");

    /**
     * A/B prop {@code rt_sync_reporting_tag} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: rt_sync_reporting_tag:[6578,"bool"]
     */
    public static final ABProp RT_SYNC_REPORTING_TAG = new ABProp(6578, "true");

    /**
     * A/B prop {@code enable_syncd_debug_data_in_patch} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_syncd_debug_data_in_patch:[6614,"bool"]
     */
    public static final ABProp ENABLE_SYNCD_DEBUG_DATA_IN_PATCH = new ABProp(6614, "false");

    /**
     * A/B prop {@code web_jpeg_quality} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_jpeg_quality:[6619,"int"]
     */
    public static final ABProp WEB_JPEG_QUALITY = new ABProp(6619, "92");

    /**
     * A/B prop {@code smba_premium_messages_insights_v2_trackable_link_domain} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smba_premium_messages_insights_v2_trackable_link_domain:[6626,"string"]
     */
    public static final ABProp SMBA_PREMIUM_MESSAGES_INSIGHTS_V2_TRACKABLE_LINK_DOMAIN = new ABProp(6626, "w.meta.me");

    /**
     * A/B prop {@code order_details_payment_instructions_sync_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: order_details_payment_instructions_sync_enabled:[6670,"bool"]
     */
    public static final ABProp ORDER_DETAILS_PAYMENT_INSTRUCTIONS_SYNC_ENABLED = new ABProp(6670, "false");

    /**
     * A/B prop {@code smba_premium_messages_leaving_wa_content} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smba_premium_messages_leaving_wa_content:[6693,"bool"]
     */
    public static final ABProp SMBA_PREMIUM_MESSAGES_LEAVING_WA_CONTENT = new ABProp(6693, "true");

    /**
     * A/B prop {@code rt_clean_reporting_tag} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: rt_clean_reporting_tag:[6723,"int"]
     */
    public static final ABProp RT_CLEAN_REPORTING_TAG = new ABProp(6723, "31");

    /**
     * A/B prop {@code gimmick_phase_two_data_suffix} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: gimmick_phase_two_data_suffix:[6785,"string"]
     */
    public static final ABProp GIMMICK_PHASE_TWO_DATA_SUFFIX = new ABProp(6785, "");

    /**
     * A/B prop {@code lid_status_send_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: lid_status_send_enabled:[6791,"bool"]
     */
    public static final ABProp LID_STATUS_SEND_ENABLED = new ABProp(6791, "false");

    /**
     * A/B prop {@code web_business_tools_drawer_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_business_tools_drawer_enabled:[6803,"bool"]
     */
    public static final ABProp WEB_BUSINESS_TOOLS_DRAWER_ENABLED = new ABProp(6803, "false");

    /**
     * A/B prop {@code web_recent_sync_handling_loop_restart_v2_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_recent_sync_handling_loop_restart_v2_enabled:[6804,"bool"]
     */
    public static final ABProp WEB_RECENT_SYNC_HANDLING_LOOP_RESTART_V2_ENABLED = new ABProp(6804, "false");

    /**
     * A/B prop {@code web_smb_label_reordering_m2_two_way} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_smb_label_reordering_m2_two_way:[6805,"bool"]
     */
    public static final ABProp WEB_SMB_LABEL_REORDERING_M2_TWO_WAY = new ABProp(6805, "false");

    /**
     * A/B prop {@code data_privacy_phase_2_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: data_privacy_phase_2_enabled:[6843,"bool"]
     */
    public static final ABProp DATA_PRIVACY_PHASE_2_ENABLED = new ABProp(6843, "false");

    /**
     * A/B prop {@code adv_accept_hosted_devices} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: adv_accept_hosted_devices:[6939,"bool"]
     */
    public static final ABProp ADV_ACCEPT_HOSTED_DEVICES = new ABProp(6939, "false");

    /**
     * A/B prop {@code payments_br_pix_phase_1_seller_sync_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: payments_br_pix_phase_1_seller_sync_enabled:[7024,"bool"]
     */
    public static final ABProp PAYMENTS_BR_PIX_PHASE_1_SELLER_SYNC_ENABLED = new ABProp(7024, "false");

    /**
     * A/B prop {@code support_message_feedback_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: support_message_feedback_enabled:[7080,"bool"]
     */
    public static final ABProp SUPPORT_MESSAGE_FEEDBACK_ENABLED = new ABProp(7080, "false");

    /**
     * A/B prop {@code inbox_filters_smb_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: inbox_filters_smb_enabled:[7108,"bool"]
     */
    public static final ABProp INBOX_FILTERS_SMB_ENABLED = new ABProp(7108, "false");

    /**
     * A/B prop {@code data_privacy_phase_2_non_e2ee_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: data_privacy_phase_2_non_e2ee_enabled:[7131,"bool"]
     */
    public static final ABProp DATA_PRIVACY_PHASE_2_NON_E2EE_ENABLED = new ABProp(7131, "false");

    /**
     * A/B prop {@code dm_initiator_trigger_groups} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: dm_initiator_trigger_groups:[7141,"bool"]
     */
    public static final ABProp DM_INITIATOR_TRIGGER_GROUPS = new ABProp(7141, "false");

    /**
     * A/B prop {@code enable_product_carousel_message} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_product_carousel_message:[7177,"bool"]
     */
    public static final ABProp ENABLE_PRODUCT_CAROUSEL_MESSAGE = new ABProp(7177, "false");

    /**
     * A/B prop {@code ctwa_manage_ads_tab_web_recovery_flow} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_manage_ads_tab_web_recovery_flow:[7215,"bool"]
     */
    public static final ABProp CTWA_MANAGE_ADS_TAB_WEB_RECOVERY_FLOW = new ABProp(7215, "false");

    /**
     * A/B prop {@code channels_quick_forwarding_button_mode} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_quick_forwarding_button_mode:[7234,"int"]
     */
    public static final ABProp CHANNELS_QUICK_FORWARDING_BUTTON_MODE = new ABProp(7234, "0");

    /**
     * A/B prop {@code web_recent_sync_worker_compatible_handling} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_recent_sync_worker_compatible_handling:[7247,"bool"]
     */
    public static final ABProp WEB_RECENT_SYNC_WORKER_COMPATIBLE_HANDLING = new ABProp(7247, "false");

    /**
     * A/B prop {@code favorites_limit} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: favorites_limit:[7267,"int"]
     */
    public static final ABProp FAVORITES_LIMIT = new ABProp(7267, "100");

    /**
     * A/B prop {@code web_disable_sw_on_safari_pwa} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_disable_sw_on_safari_pwa:[7281,"bool"]
     */
    public static final ABProp WEB_DISABLE_SW_ON_SAFARI_PWA = new ABProp(7281, "false");

    /**
     * A/B prop {@code visible_message_drop_placeholder_enabled_internal_only} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: visible_message_drop_placeholder_enabled_internal_only:[7287,"bool"]
     */
    public static final ABProp VISIBLE_MESSAGE_DROP_PLACEHOLDER_ENABLED_INTERNAL_ONLY = new ABProp(7287, "false");

    /**
     * A/B prop {@code native_contact_companion_change_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: native_contact_companion_change_enabled:[7301,"bool"]
     */
    public static final ABProp NATIVE_CONTACT_COMPANION_CHANGE_ENABLED = new ABProp(7301, "false");

    /**
     * A/B prop {@code smb_catalog_graphql_create_product_catalog} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_catalog_graphql_create_product_catalog:[7320,"bool"]
     */
    public static final ABProp SMB_CATALOG_GRAPHQL_CREATE_PRODUCT_CATALOG = new ABProp(7320, "false");

    /**
     * A/B prop {@code smb_catalog_graphql_get_product_catalog} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_catalog_graphql_get_product_catalog:[7321,"bool"]
     */
    public static final ABProp SMB_CATALOG_GRAPHQL_GET_PRODUCT_CATALOG = new ABProp(7321, "false");

    /**
     * A/B prop {@code smb_catalog_graphql_add_product} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_catalog_graphql_add_product:[7322,"bool"]
     */
    public static final ABProp SMB_CATALOG_GRAPHQL_ADD_PRODUCT = new ABProp(7322, "false");

    /**
     * A/B prop {@code smb_catalog_graphql_edit_product} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_catalog_graphql_edit_product:[7323,"bool"]
     */
    public static final ABProp SMB_CATALOG_GRAPHQL_EDIT_PRODUCT = new ABProp(7323, "false");

    /**
     * A/B prop {@code smb_catalog_graphql_get_product} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_catalog_graphql_get_product:[7324,"bool"]
     */
    public static final ABProp SMB_CATALOG_GRAPHQL_GET_PRODUCT = new ABProp(7324, "false");

    /**
     * A/B prop {@code smb_catalog_graphql_report_product} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_catalog_graphql_report_product:[7325,"bool"]
     */
    public static final ABProp SMB_CATALOG_GRAPHQL_REPORT_PRODUCT = new ABProp(7325, "false");

    /**
     * A/B prop {@code smb_catalog_graphql_appeal_product} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_catalog_graphql_appeal_product:[7326,"bool"]
     */
    public static final ABProp SMB_CATALOG_GRAPHQL_APPEAL_PRODUCT = new ABProp(7326, "false");

    /**
     * A/B prop {@code smb_catalog_graphql_update_collection_list} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_catalog_graphql_update_collection_list:[7328,"bool"]
     */
    public static final ABProp SMB_CATALOG_GRAPHQL_UPDATE_COLLECTION_LIST = new ABProp(7328, "false");

    /**
     * A/B prop {@code smb_catalog_graphql_delete_collection} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_catalog_graphql_delete_collection:[7329,"bool"]
     */
    public static final ABProp SMB_CATALOG_GRAPHQL_DELETE_COLLECTION = new ABProp(7329, "false");

    /**
     * A/B prop {@code smb_catalog_graphql_appeal_collection} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_catalog_graphql_appeal_collection:[7330,"bool"]
     */
    public static final ABProp SMB_CATALOG_GRAPHQL_APPEAL_COLLECTION = new ABProp(7330, "false");

    /**
     * A/B prop {@code smb_catalog_graphql_get_collection} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_catalog_graphql_get_collection:[7331,"bool"]
     */
    public static final ABProp SMB_CATALOG_GRAPHQL_GET_COLLECTION = new ABProp(7331, "false");

    /**
     * A/B prop {@code smb_catalog_graphql_get_single_collection} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_catalog_graphql_get_single_collection:[7332,"bool"]
     */
    public static final ABProp SMB_CATALOG_GRAPHQL_GET_SINGLE_COLLECTION = new ABProp(7332, "false");

    /**
     * A/B prop {@code web_recent_sync_chunk_download_optimization} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_recent_sync_chunk_download_optimization:[7356,"bool"]
     */
    public static final ABProp WEB_RECENT_SYNC_CHUNK_DOWNLOAD_OPTIMIZATION = new ABProp(7356, "false");

    /**
     * A/B prop {@code dm_initiator_trigger_daily_logs} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: dm_initiator_trigger_daily_logs:[7402,"bool"]
     */
    public static final ABProp DM_INITIATOR_TRIGGER_DAILY_LOGS = new ABProp(7402, "false");

    /**
     * A/B prop {@code web_autodownload_stickers} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_autodownload_stickers:[7422,"bool"]
     */
    public static final ABProp WEB_AUTODOWNLOAD_STICKERS = new ABProp(7422, "false");

    /**
     * A/B prop {@code custom_racing_emoji} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: custom_racing_emoji:[7463,"bool"]
     */
    public static final ABProp CUSTOM_RACING_EMOJI = new ABProp(7463, "false");

    /**
     * A/B prop {@code username_security_code_generation} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: username_security_code_generation:[7468,"bool"]
     */
    public static final ABProp USERNAME_SECURITY_CODE_GENERATION = new ABProp(7468, "false");

    /**
     * A/B prop {@code username_security_code_verification} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: username_security_code_verification:[7469,"bool"]
     */
    public static final ABProp USERNAME_SECURITY_CODE_VERIFICATION = new ABProp(7469, "false");

    /**
     * A/B prop {@code web_recent_sync_next_chunk_fetch_optimization} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_recent_sync_next_chunk_fetch_optimization:[7494,"bool"]
     */
    public static final ABProp WEB_RECENT_SYNC_NEXT_CHUNK_FETCH_OPTIMIZATION = new ABProp(7494, "false");

    /**
     * A/B prop {@code events_m3_cover_image_send} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: events_m3_cover_image_send:[7510,"bool"]
     */
    public static final ABProp EVENTS_M3_COVER_IMAGE_SEND = new ABProp(7510, "false");

    /**
     * A/B prop {@code events_m3_cover_image_receive} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: events_m3_cover_image_receive:[7511,"bool"]
     */
    public static final ABProp EVENTS_M3_COVER_IMAGE_RECEIVE = new ABProp(7511, "false");

    /**
     * A/B prop {@code similar_channels_max_limit} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: similar_channels_max_limit:[7559,"int"]
     */
    public static final ABProp SIMILAR_CHANNELS_MAX_LIMIT = new ABProp(7559, "10");

    /**
     * A/B prop {@code similar_channels_min_limit} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: similar_channels_min_limit:[7560,"int"]
     */
    public static final ABProp SIMILAR_CHANNELS_MIN_LIMIT = new ABProp(7560, "4");

    /**
     * A/B prop {@code addon_infra_enable_perf_logging} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: addon_infra_enable_perf_logging:[7567,"bool"]
     */
    public static final ABProp ADDON_INFRA_ENABLE_PERF_LOGGING = new ABProp(7567, "false");

    /**
     * A/B prop {@code inbox_filters_custom_smb_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: inbox_filters_custom_smb_enabled:[7637,"bool"]
     */
    public static final ABProp INBOX_FILTERS_CUSTOM_SMB_ENABLED = new ABProp(7637, "false");

    /**
     * A/B prop {@code smb_graphql_to_fetch_qp_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_graphql_to_fetch_qp_enabled:[7645,"bool"]
     */
    public static final ABProp SMB_GRAPHQL_TO_FETCH_QP_ENABLED = new ABProp(7645, "false");

    /**
     * A/B prop {@code smb_graphql_to_fetch_qp_frequency_mins} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_graphql_to_fetch_qp_frequency_mins:[7646,"int"]
     */
    public static final ABProp SMB_GRAPHQL_TO_FETCH_QP_FREQUENCY_MINS = new ABProp(7646, "1320");

    /**
     * A/B prop {@code smb_graphql_to_fetch_qp_surface_ids} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_graphql_to_fetch_qp_surface_ids:[7647,"string"]
     */
    public static final ABProp SMB_GRAPHQL_TO_FETCH_QP_SURFACE_IDS = new ABProp(7647, "");

    /**
     * A/B prop {@code smb_notes_v1_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_notes_v1_enabled:[7710,"bool"]
     */
    public static final ABProp SMB_NOTES_V1_ENABLED = new ABProp(7710, "false");

    /**
     * A/B prop {@code channels_directory_category_types} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_directory_category_types:[7734,"string"]
     */
    public static final ABProp CHANNELS_DIRECTORY_CATEGORY_TYPES = new ABProp(7734, "3,7,6,4,1,5,2");

    /**
     * A/B prop {@code single_e2ee_session_migration_state_outgoing} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: single_e2ee_session_migration_state_outgoing:[7820,"int"]
     */
    public static final ABProp SINGLE_E2EE_SESSION_MIGRATION_STATE_OUTGOING = new ABProp(7820, "2");

    /**
     * A/B prop {@code bonsai_supported_languages} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: bonsai_supported_languages:[7848,"string"]
     */
    public static final ABProp BONSAI_SUPPORTED_LANGUAGES = new ABProp(7848, "en");

    /**
     * A/B prop {@code web_comms_socket_reconnect_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_comms_socket_reconnect_enabled:[7854,"bool"]
     */
    public static final ABProp WEB_COMMS_SOCKET_RECONNECT_ENABLED = new ABProp(7854, "false");

    /**
     * A/B prop {@code payments_br_pix_quick_reply_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: payments_br_pix_quick_reply_enabled:[7857,"bool"]
     */
    public static final ABProp PAYMENTS_BR_PIX_QUICK_REPLY_ENABLED = new ABProp(7857, "false");

    /**
     * A/B prop {@code status_mentions_receiver} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: status_mentions_receiver:[7869,"bool"]
     */
    public static final ABProp STATUS_MENTIONS_RECEIVER = new ABProp(7869, "false");

    /**
     * A/B prop {@code enable_sticker_verification_for_gimmick} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_sticker_verification_for_gimmick:[7886,"bool"]
     */
    public static final ABProp ENABLE_STICKER_VERIFICATION_FOR_GIMMICK = new ABProp(7886, "true");

    /**
     * A/B prop {@code web_wasm_worker_enabled_www} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_wasm_worker_enabled_www:[7924,"bool"]
     */
    public static final ABProp WEB_WASM_WORKER_ENABLED_WWW = new ABProp(7924, "false");

    /**
     * A/B prop {@code status_deeplink_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: status_deeplink_enabled:[7965,"bool"]
     */
    public static final ABProp STATUS_DEEPLINK_ENABLED = new ABProp(7965, "false");

    /**
     * A/B prop {@code directory_categories_newsletters_per_category_limit} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: directory_categories_newsletters_per_category_limit:[7986,"int"]
     */
    public static final ABProp DIRECTORY_CATEGORIES_NEWSLETTERS_PER_CATEGORY_LIMIT = new ABProp(7986, "10");

    /**
     * A/B prop {@code ctwa_long_term_holdout_content_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_long_term_holdout_content_enabled:[8015,"bool"]
     */
    public static final ABProp CTWA_LONG_TERM_HOLDOUT_CONTENT_ENABLED = new ABProp(8015, "false");

    /**
     * A/B prop {@code ai_search_max_num_suggestions} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_search_max_num_suggestions:[8076,"int"]
     */
    public static final ABProp AI_SEARCH_MAX_NUM_SUGGESTIONS = new ABProp(8076, "5");

    /**
     * A/B prop {@code ai_search_null_state_update_interval} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_search_null_state_update_interval:[8100,"int"]
     */
    public static final ABProp AI_SEARCH_NULL_STATE_UPDATE_INTERVAL = new ABProp(8100, "86400");

    /**
     * A/B prop {@code web_sticky_hd_photo_setting_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_sticky_hd_photo_setting_enabled:[8115,"bool"]
     */
    public static final ABProp WEB_STICKY_HD_PHOTO_SETTING_ENABLED = new ABProp(8115, "false");

    /**
     * A/B prop {@code channels_directory_categories_cache_refresh_interval_ms} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_directory_categories_cache_refresh_interval_ms:[8151,"int"]
     */
    public static final ABProp CHANNELS_DIRECTORY_CATEGORIES_CACHE_REFRESH_INTERVAL_MS = new ABProp(8151, "864e5");

    /**
     * A/B prop {@code ctwa_ad_account_token_storage_kill_switch_web} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_ad_account_token_storage_kill_switch_web:[8166,"bool"]
     */
    public static final ABProp CTWA_AD_ACCOUNT_TOKEN_STORAGE_KILL_SWITCH_WEB = new ABProp(8166, "true");

    /**
     * A/B prop {@code channels_recommended_v3_ui_limit} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_recommended_v3_ui_limit:[8167,"int"]
     */
    public static final ABProp CHANNELS_RECOMMENDED_V3_UI_LIMIT = new ABProp(8167, "5");

    /**
     * A/B prop {@code web_larger_link_previews} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_larger_link_previews:[8172,"bool"]
     */
    public static final ABProp WEB_LARGER_LINK_PREVIEWS = new ABProp(8172, "false");

    /**
     * A/B prop {@code wa_web_business_tools_top_card_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_business_tools_top_card_enabled:[8191,"bool"]
     */
    public static final ABProp WA_WEB_BUSINESS_TOOLS_TOP_CARD_ENABLED = new ABProp(8191, "false");

    /**
     * A/B prop {@code web_recent_sync_chunk_data_handling_worker} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_recent_sync_chunk_data_handling_worker:[8270,"bool"]
     */
    public static final ABProp WEB_RECENT_SYNC_CHUNK_DATA_HANDLING_WORKER = new ABProp(8270, "false");

    /**
     * A/B prop {@code smb_meta_verified_context_card} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_meta_verified_context_card:[8313,"bool"]
     */
    public static final ABProp SMB_META_VERIFIED_CONTEXT_CARD = new ABProp(8313, "false");

    /**
     * A/B prop {@code web_pending_message_cache_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_pending_message_cache_enabled:[8353,"bool"]
     */
    public static final ABProp WEB_PENDING_MESSAGE_CACHE_ENABLED = new ABProp(8353, "false");

    /**
     * A/B prop {@code unified_pin_addon_table_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: unified_pin_addon_table_enabled:[8356,"bool"]
     */
    public static final ABProp UNIFIED_PIN_ADDON_TABLE_ENABLED = new ABProp(8356, "false");

    /**
     * A/B prop {@code web_offline_message_processor_timeout_seconds} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_offline_message_processor_timeout_seconds:[8406,"int"]
     */
    public static final ABProp WEB_OFFLINE_MESSAGE_PROCESSOR_TIMEOUT_SECONDS = new ABProp(8406, "0");

    /**
     * A/B prop {@code ai_search_null_state_row_count} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_search_null_state_row_count:[8407,"int"]
     */
    public static final ABProp AI_SEARCH_NULL_STATE_ROW_COUNT = new ABProp(8407, "3");

    /**
     * A/B prop {@code mex_usync_username_query} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: mex_usync_username_query:[8421,"bool"]
     */
    public static final ABProp MEX_USYNC_USERNAME_QUERY = new ABProp(8421, "false");

    /**
     * A/B prop {@code webc_page_load_early_commit_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: webc_page_load_early_commit_enabled:[8458,"bool"]
     */
    public static final ABProp WEBC_PAGE_LOAD_EARLY_COMMIT_ENABLED = new ABProp(8458, "false");

    /**
     * A/B prop {@code biz_ai_smb_agents_automatic_reply_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: biz_ai_smb_agents_automatic_reply_enabled:[8505,"bool"]
     */
    public static final ABProp BIZ_AI_SMB_AGENTS_AUTOMATIC_REPLY_ENABLED = new ABProp(8505, "false");

    /**
     * A/B prop {@code improve_subgroup_activation_subgroup_poll_interval} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: improve_subgroup_activation_subgroup_poll_interval:[8542,"int"]
     */
    public static final ABProp IMPROVE_SUBGROUP_ACTIVATION_SUBGROUP_POLL_INTERVAL = new ABProp(8542, "43200");

    /**
     * A/B prop {@code web_communities_general_chat_v_2} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_communities_general_chat_v_2:[8580,"bool"]
     */
    public static final ABProp WEB_COMMUNITIES_GENERAL_CHAT_V_2 = new ABProp(8580, "false");

    /**
     * A/B prop {@code web_status_likes_receive_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_status_likes_receive_enabled:[8611,"bool"]
     */
    public static final ABProp WEB_STATUS_LIKES_RECEIVE_ENABLED = new ABProp(8611, "false");

    /**
     * A/B prop {@code ctwa_ad_account_nonce_retries_max_web} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_ad_account_nonce_retries_max_web:[8663,"int"]
     */
    public static final ABProp CTWA_AD_ACCOUNT_NONCE_RETRIES_MAX_WEB = new ABProp(8663, "0");

    /**
     * A/B prop {@code ctwa_ad_account_nonce_push_wait_timeout_web} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_ad_account_nonce_push_wait_timeout_web:[8664,"int"]
     */
    public static final ABProp CTWA_AD_ACCOUNT_NONCE_PUSH_WAIT_TIMEOUT_WEB = new ABProp(8664, "20");

    /**
     * A/B prop {@code coex_status_reply_privacy_disclaimer_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: coex_status_reply_privacy_disclaimer_enabled:[8674,"bool"]
     */
    public static final ABProp COEX_STATUS_REPLY_PRIVACY_DISCLAIMER_ENABLED = new ABProp(8674, "false");

    /**
     * A/B prop {@code web_background_sync_v2} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_background_sync_v2:[8782,"bool"]
     */
    public static final ABProp WEB_BACKGROUND_SYNC_V2 = new ABProp(8782, "false");

    /**
     * A/B prop {@code message_association_infra_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: message_association_infra_enabled:[8783,"bool"]
     */
    public static final ABProp MESSAGE_ASSOCIATION_INFRA_ENABLED = new ABProp(8783, "true");

    /**
     * A/B prop {@code smb_catalog_graphql_get_product_list} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_catalog_graphql_get_product_list:[8799,"bool"]
     */
    public static final ABProp SMB_CATALOG_GRAPHQL_GET_PRODUCT_LIST = new ABProp(8799, "false");

    /**
     * A/B prop {@code wa_web_parse_always_show_ad_attribution} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_parse_always_show_ad_attribution:[8804,"bool"]
     */
    public static final ABProp WA_WEB_PARSE_ALWAYS_SHOW_AD_ATTRIBUTION = new ABProp(8804, "false");

    /**
     * A/B prop {@code rt_sender_reporting_token_version} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: rt_sender_reporting_token_version:[8860,"int"]
     */
    public static final ABProp RT_SENDER_REPORTING_TOKEN_VERSION = new ABProp(8860, "2");

    /**
     * A/B prop {@code payments_br_force_copy_pix_cta_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: payments_br_force_copy_pix_cta_enabled:[8953,"bool"]
     */
    public static final ABProp PAYMENTS_BR_FORCE_COPY_PIX_CTA_ENABLED = new ABProp(8953, "false");

    /**
     * A/B prop {@code smb_payment_links_url_regex_list} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_payment_links_url_regex_list:[8969,"string"]
     */
    public static final ABProp SMB_PAYMENT_LINKS_URL_REGEX_LIST = new ABProp(8969, "{}");

    /**
     * A/B prop {@code payments_br_copy_pix_code_api_merchant_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: payments_br_copy_pix_code_api_merchant_enabled:[9017,"bool"]
     */
    public static final ABProp PAYMENTS_BR_COPY_PIX_CODE_API_MERCHANT_ENABLED = new ABProp(9017, "false");

    /**
     * A/B prop {@code smb_orders_graphql_get_order_info} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_orders_graphql_get_order_info:[9030,"bool"]
     */
    public static final ABProp SMB_ORDERS_GRAPHQL_GET_ORDER_INFO = new ABProp(9030, "false");

    /**
     * A/B prop {@code smb_orders_graphql_place_order} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_orders_graphql_place_order:[9031,"bool"]
     */
    public static final ABProp SMB_ORDERS_GRAPHQL_PLACE_ORDER = new ABProp(9031, "false");

    /**
     * A/B prop {@code smb_orders_graphql_refresh_cart} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_orders_graphql_refresh_cart:[9032,"bool"]
     */
    public static final ABProp SMB_ORDERS_GRAPHQL_REFRESH_CART = new ABProp(9032, "false");

    /**
     * A/B prop {@code events_m3_pin_customization_receive} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: events_m3_pin_customization_receive:[9062,"bool"]
     */
    public static final ABProp EVENTS_M3_PIN_CUSTOMIZATION_RECEIVE = new ABProp(9062, "false");

    /**
     * A/B prop {@code events_m3_pin_customization_send} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: events_m3_pin_customization_send:[9063,"bool"]
     */
    public static final ABProp EVENTS_M3_PIN_CUSTOMIZATION_SEND = new ABProp(9063, "false");

    /**
     * A/B prop {@code lazy_system_message_insertion_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: lazy_system_message_insertion_enabled:[9077,"bool"]
     */
    public static final ABProp LAZY_SYSTEM_MESSAGE_INSERTION_ENABLED = new ABProp(9077, "false");

    /**
     * A/B prop {@code remove_single_emoji_bubble_background_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: remove_single_emoji_bubble_background_enabled:[9083,"bool"]
     */
    public static final ABProp REMOVE_SINGLE_EMOJI_BUBBLE_BACKGROUND_ENABLED = new ABProp(9083, "false");

    /**
     * A/B prop {@code smb_graphql_token_recovery_during_account_recovery_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_graphql_token_recovery_during_account_recovery_enabled:[9197,"bool"]
     */
    public static final ABProp SMB_GRAPHQL_TOKEN_RECOVERY_DURING_ACCOUNT_RECOVERY_ENABLED = new ABProp(9197, "false");

    /**
     * A/B prop {@code smb_payment_links_logging_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_payment_links_logging_enabled:[9213,"bool"]
     */
    public static final ABProp SMB_PAYMENT_LINKS_LOGGING_ENABLED = new ABProp(9213, "false");

    /**
     * A/B prop {@code smb_ctwa_chat_header_label_entry_point_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_ctwa_chat_header_label_entry_point_enabled:[9223,"bool"]
     */
    public static final ABProp SMB_CTWA_CHAT_HEADER_LABEL_ENTRY_POINT_ENABLED = new ABProp(9223, "false");

    /**
     * A/B prop {@code ctwa_new_customer_label_signals} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_new_customer_label_signals:[9302,"bool"]
     */
    public static final ABProp CTWA_NEW_CUSTOMER_LABEL_SIGNALS = new ABProp(9302, "false");

    /**
     * A/B prop {@code directory_categories_display_newsletters_per_category_limit} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: directory_categories_display_newsletters_per_category_limit:[9312,"int"]
     */
    public static final ABProp DIRECTORY_CATEGORIES_DISPLAY_NEWSLETTERS_PER_CATEGORY_LIMIT = new ABProp(9312, "4");

    /**
     * A/B prop {@code optimized_delivery_signal_collection_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: optimized_delivery_signal_collection_enabled:[9348,"bool"]
     */
    public static final ABProp OPTIMIZED_DELIVERY_SIGNAL_COLLECTION_ENABLED = new ABProp(9348, "false");

    /**
     * A/B prop {@code web_sticker_download_m1} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_sticker_download_m1:[9406,"bool"]
     */
    public static final ABProp WEB_STICKER_DOWNLOAD_M1 = new ABProp(9406, "false");

    /**
     * A/B prop {@code lid_one_on_one_migration_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: lid_one_on_one_migration_enabled:[9435,"bool"]
     */
    public static final ABProp LID_ONE_ON_ONE_MIGRATION_ENABLED = new ABProp(9435, "false");

    /**
     * A/B prop {@code channels_producer_insights_min_followers} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_producer_insights_min_followers:[9447,"int"]
     */
    public static final ABProp CHANNELS_PRODUCER_INSIGHTS_MIN_FOLLOWERS = new ABProp(9447, "100");

    /**
     * A/B prop {@code status_ranking_poster_side_gating_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: status_ranking_poster_side_gating_enabled:[9453,"bool"]
     */
    public static final ABProp STATUS_RANKING_POSTER_SIDE_GATING_ENABLED = new ABProp(9453, "false");

    /**
     * A/B prop {@code ai_pdfn_tos_shortcut_notice_id} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_pdfn_tos_shortcut_notice_id:[9482,"string"]
     */
    public static final ABProp AI_PDFN_TOS_SHORTCUT_NOTICE_ID = new ABProp(9482, " ");

    /**
     * A/B prop {@code ai_pdfn_tos_invoke_notice_id} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_pdfn_tos_invoke_notice_id:[9483,"string"]
     */
    public static final ABProp AI_PDFN_TOS_INVOKE_NOTICE_ID = new ABProp(9483, " ");

    /**
     * A/B prop {@code status_future_proofing} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: status_future_proofing:[9522,"bool"]
     */
    public static final ABProp STATUS_FUTURE_PROOFING = new ABProp(9522, "false");

    /**
     * A/B prop {@code mex_usync_about_status} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: mex_usync_about_status:[9524,"bool"]
     */
    public static final ABProp MEX_USYNC_ABOUT_STATUS = new ABProp(9524, "false");

    /**
     * A/B prop {@code bonsai_fp_ugc_sender} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: bonsai_fp_ugc_sender:[9541,"bool"]
     */
    public static final ABProp BONSAI_FP_UGC_SENDER = new ABProp(9541, "false");

    /**
     * A/B prop {@code rt_clean_reporting_token} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: rt_clean_reporting_token:[9567,"int"]
     */
    public static final ABProp RT_CLEAN_REPORTING_TOKEN = new ABProp(9567, "31");

    /**
     * A/B prop {@code ctwa_ad_creation_entry_point_catalog_web} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_ad_creation_entry_point_catalog_web:[9596,"bool"]
     */
    public static final ABProp CTWA_AD_CREATION_ENTRY_POINT_CATALOG_WEB = new ABProp(9596, "false");

    /**
     * A/B prop {@code ai_experiment_graphql_config} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_experiment_graphql_config:[9601,"string"]
     */
    public static final ABProp AI_EXPERIMENT_GRAPHQL_CONFIG = new ABProp(9601, " ");

    /**
     * A/B prop {@code unified_session_log_ts_event} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: unified_session_log_ts_event:[9611,"bool"]
     */
    public static final ABProp UNIFIED_SESSION_LOG_TS_EVENT = new ABProp(9611, "false");

    /**
     * A/B prop {@code channels_admin_insights_gizmos_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_admin_insights_gizmos_enabled:[9641,"bool"]
     */
    public static final ABProp CHANNELS_ADMIN_INSIGHTS_GIZMOS_ENABLED = new ABProp(9641, "false");

    /**
     * A/B prop {@code profile_scraping_privacy_token_in_photo_iq} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: profile_scraping_privacy_token_in_photo_iq:[9666,"bool"]
     */
    public static final ABProp PROFILE_SCRAPING_PRIVACY_TOKEN_IN_PHOTO_IQ = new ABProp(9666, "true");

    /**
     * A/B prop {@code profile_scraping_privacy_token_in_about_iq} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: profile_scraping_privacy_token_in_about_iq:[9668,"bool"]
     */
    public static final ABProp PROFILE_SCRAPING_PRIVACY_TOKEN_IN_ABOUT_IQ = new ABProp(9668, "false");

    /**
     * A/B prop {@code single_emoji_logging_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: single_emoji_logging_enabled:[9669,"bool"]
     */
    public static final ABProp SINGLE_EMOJI_LOGGING_ENABLED = new ABProp(9669, "false");

    /**
     * A/B prop {@code web_catalog_video_view_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_catalog_video_view_enabled:[9671,"bool"]
     */
    public static final ABProp WEB_CATALOG_VIDEO_VIEW_ENABLED = new ABProp(9671, "false");

    /**
     * A/B prop {@code web_catalog_video_view_fallback_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_catalog_video_view_fallback_enabled:[9672,"bool"]
     */
    public static final ABProp WEB_CATALOG_VIDEO_VIEW_FALLBACK_ENABLED = new ABProp(9672, "false");

    /**
     * A/B prop {@code ctwa_ad_creation_entry_point_catalog_product_web} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_ad_creation_entry_point_catalog_product_web:[9677,"bool"]
     */
    public static final ABProp CTWA_AD_CREATION_ENTRY_POINT_CATALOG_PRODUCT_WEB = new ABProp(9677, "false");

    /**
     * A/B prop {@code wa_ctwa_web_hide_ad_context_if_soft_dismissed_in_primary} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_ctwa_web_hide_ad_context_if_soft_dismissed_in_primary:[9729,"bool"]
     */
    public static final ABProp WA_CTWA_WEB_HIDE_AD_CONTEXT_IF_SOFT_DISMISSED_IN_PRIMARY = new ABProp(9729, "false");

    /**
     * A/B prop {@code animated_emoji_final_set_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: animated_emoji_final_set_enabled:[9757,"bool"]
     */
    public static final ABProp ANIMATED_EMOJI_FINAL_SET_ENABLED = new ABProp(9757, "false");

    /**
     * A/B prop {@code animated_emoji_set_1_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: animated_emoji_set_1_enabled:[9758,"bool"]
     */
    public static final ABProp ANIMATED_EMOJI_SET_1_ENABLED = new ABProp(9758, "false");

    /**
     * A/B prop {@code web_hd_media_global_setting_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_hd_media_global_setting_enabled:[9778,"bool"]
     */
    public static final ABProp WEB_HD_MEDIA_GLOBAL_SETTING_ENABLED = new ABProp(9778, "false");

    /**
     * A/B prop {@code channels_producer_insights_hide_deltas} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_producer_insights_hide_deltas:[9792,"bool"]
     */
    public static final ABProp CHANNELS_PRODUCER_INSIGHTS_HIDE_DELTAS = new ABProp(9792, "true");

    /**
     * A/B prop {@code rt_report_token_from_inclusion_list} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: rt_report_token_from_inclusion_list:[9818,"bool"]
     */
    public static final ABProp RT_REPORT_TOKEN_FROM_INCLUSION_LIST = new ABProp(9818, "false");

    /**
     * A/B prop {@code whatsapp_vpv_logging_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: whatsapp_vpv_logging_enabled:[9833,"bool"]
     */
    public static final ABProp WHATSAPP_VPV_LOGGING_ENABLED = new ABProp(9833, "true");

    /**
     * A/B prop {@code smb_notes_privacy_string} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_notes_privacy_string:[9843,"int"]
     */
    public static final ABProp SMB_NOTES_PRIVACY_STRING = new ABProp(9843, "2");

    /**
     * A/B prop {@code saga_v1_reengagement_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: saga_v1_reengagement_enabled:[9924,"bool"]
     */
    public static final ABProp SAGA_V1_REENGAGEMENT_ENABLED = new ABProp(9924, "true");

    /**
     * A/B prop {@code web_reaction_notification_via_add_on_api} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_reaction_notification_via_add_on_api:[9933,"bool"]
     */
    public static final ABProp WEB_REACTION_NOTIFICATION_VIA_ADD_ON_API = new ABProp(9933, "false");

    /**
     * A/B prop {@code saga_v1_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: saga_v1_enabled:[9942,"bool"]
     */
    public static final ABProp SAGA_V1_ENABLED = new ABProp(9942, "true");

    /**
     * A/B prop {@code saga_v1_nux_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: saga_v1_nux_enabled:[9944,"bool"]
     */
    public static final ABProp SAGA_V1_NUX_ENABLED = new ABProp(9944, "true");

    /**
     * A/B prop {@code mm_message_level_feedback_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: mm_message_level_feedback_enabled:[10011,"bool"]
     */
    public static final ABProp MM_MESSAGE_LEVEL_FEEDBACK_ENABLED = new ABProp(10011, "false");

    /**
     * A/B prop {@code wa_web_calling_deep_link_error} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_calling_deep_link_error:[10051,"bool"]
     */
    public static final ABProp WA_WEB_CALLING_DEEP_LINK_ERROR = new ABProp(10051, "true");

    /**
     * A/B prop {@code enable_wefr_client_expo_pulse} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_wefr_client_expo_pulse:[10230,"bool"]
     */
    public static final ABProp ENABLE_WEFR_CLIENT_EXPO_PULSE = new ABProp(10230, "false");

    /**
     * A/B prop {@code web_offline_stage_manager_singleton_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_offline_stage_manager_singleton_enabled:[10235,"bool"]
     */
    public static final ABProp WEB_OFFLINE_STAGE_MANAGER_SINGLETON_ENABLED = new ABProp(10235, "false");

    /**
     * A/B prop {@code optimized_delivery_signal_collection_config} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: optimized_delivery_signal_collection_config:[10302,"string"]
     */
    public static final ABProp OPTIMIZED_DELIVERY_SIGNAL_COLLECTION_CONFIG = new ABProp(10302, "{}");

    /**
     * A/B prop {@code optimized_delivery_tokens_storage_config} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: optimized_delivery_tokens_storage_config:[10303,"string"]
     */
    public static final ABProp OPTIMIZED_DELIVERY_TOKENS_STORAGE_CONFIG = new ABProp(10303, "{}");

    /**
     * A/B prop {@code channels_capabilities_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_capabilities_enabled:[10328,"bool"]
     */
    public static final ABProp CHANNELS_CAPABILITIES_ENABLED = new ABProp(10328, "true");

    /**
     * A/B prop {@code ai_home_graphql_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_home_graphql_enabled:[10344,"bool"]
     */
    public static final ABProp AI_HOME_GRAPHQL_ENABLED = new ABProp(10344, "false");

    /**
     * A/B prop {@code smb_payment_links_seller_logging_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_payment_links_seller_logging_enabled:[10389,"bool"]
     */
    public static final ABProp SMB_PAYMENT_LINKS_SELLER_LOGGING_ENABLED = new ABProp(10389, "false");

    /**
     * A/B prop {@code poll_result_snapshot_message_receiver_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: poll_result_snapshot_message_receiver_enabled:[10414,"bool"]
     */
    public static final ABProp POLL_RESULT_SNAPSHOT_MESSAGE_RECEIVER_ENABLED = new ABProp(10414, "false");

    /**
     * A/B prop {@code privacy_token_sending_on_all_1_on_1_messages} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: privacy_token_sending_on_all_1_on_1_messages:[10518,"bool"]
     */
    public static final ABProp PRIVACY_TOKEN_SENDING_ON_ALL_1_ON_1_MESSAGES = new ABProp(10518, "false");

    /**
     * A/B prop {@code smb_custom_url_get_user_graphql_migration_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_custom_url_get_user_graphql_migration_enabled:[10519,"bool"]
     */
    public static final ABProp SMB_CUSTOM_URL_GET_USER_GRAPHQL_MIGRATION_ENABLED = new ABProp(10519, "true");

    /**
     * A/B prop {@code enable_community_suspend_and_appeals} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_community_suspend_and_appeals:[10539,"bool"]
     */
    public static final ABProp ENABLE_COMMUNITY_SUSPEND_AND_APPEALS = new ABProp(10539, "false");

    /**
     * A/B prop {@code saga_v1_carousel} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: saga_v1_carousel:[10609,"bool"]
     */
    public static final ABProp SAGA_V1_CAROUSEL = new ABProp(10609, "true");

    /**
     * A/B prop {@code mm_message_level_feedback_analytics_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: mm_message_level_feedback_analytics_enabled:[10667,"bool"]
     */
    public static final ABProp MM_MESSAGE_LEVEL_FEEDBACK_ANALYTICS_ENABLED = new ABProp(10667, "false");

    /**
     * A/B prop {@code mm_message_level_feedback_not_interested_menu_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: mm_message_level_feedback_not_interested_menu_enabled:[10668,"bool"]
     */
    public static final ABProp MM_MESSAGE_LEVEL_FEEDBACK_NOT_INTERESTED_MENU_ENABLED = new ABProp(10668, "false");

    /**
     * A/B prop {@code web_history_sync_allow_duplicate_in_bulk_error} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_history_sync_allow_duplicate_in_bulk_error:[10842,"bool"]
     */
    public static final ABProp WEB_HISTORY_SYNC_ALLOW_DUPLICATE_IN_BULK_ERROR = new ABProp(10842, "false");

    /**
     * A/B prop {@code british_english_localization_enabled_web} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: british_english_localization_enabled_web:[10865,"bool"]
     */
    public static final ABProp BRITISH_ENGLISH_LOCALIZATION_ENABLED_WEB = new ABProp(10865, "false");

    /**
     * A/B prop {@code wabba_receiver_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wabba_receiver_enabled:[10970,"bool"]
     */
    public static final ABProp WABBA_RECEIVER_ENABLED = new ABProp(10970, "false");

    /**
     * A/B prop {@code web_adv_logout_on_self_device_list_expired} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_adv_logout_on_self_device_list_expired:[11011,"bool"]
     */
    public static final ABProp WEB_ADV_LOGOUT_ON_SELF_DEVICE_LIST_EXPIRED = new ABProp(11011, "false");

    /**
     * A/B prop {@code meta_catalog_linking_m2_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: meta_catalog_linking_m2_enabled:[11029,"bool"]
     */
    public static final ABProp META_CATALOG_LINKING_M2_ENABLED = new ABProp(11029, "true");

    /**
     * A/B prop {@code ctwa_signal_decoupling_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_signal_decoupling_enabled:[11035,"bool"]
     */
    public static final ABProp CTWA_SIGNAL_DECOUPLING_ENABLED = new ABProp(11035, "false");

    /**
     * A/B prop {@code lid_migration_for_vname_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: lid_migration_for_vname_enabled:[11049,"bool"]
     */
    public static final ABProp LID_MIGRATION_FOR_VNAME_ENABLED = new ABProp(11049, "false");

    /**
     * A/B prop {@code lid_one_on_one_migration_log_out_on_mismatch} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: lid_one_on_one_migration_log_out_on_mismatch:[11050,"bool"]
     */
    public static final ABProp LID_ONE_ON_ONE_MIGRATION_LOG_OUT_ON_MISMATCH = new ABProp(11050, "true");

    /**
     * A/B prop {@code web_biz_ai_chat_assignment_hiding_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_biz_ai_chat_assignment_hiding_enabled:[11084,"bool"]
     */
    public static final ABProp WEB_BIZ_AI_CHAT_ASSIGNMENT_HIDING_ENABLED = new ABProp(11084, "true");

    /**
     * A/B prop {@code ai_home_bot_profile_sync_interval_sec} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_home_bot_profile_sync_interval_sec:[11168,"int"]
     */
    public static final ABProp AI_HOME_BOT_PROFILE_SYNC_INTERVAL_SEC = new ABProp(11168, "86400");

    /**
     * A/B prop {@code enable_channel_image_server_thumbnail} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_channel_image_server_thumbnail:[11191,"bool"]
     */
    public static final ABProp ENABLE_CHANNEL_IMAGE_SERVER_THUMBNAIL = new ABProp(11191, "false");

    /**
     * A/B prop {@code enable_channel_video_server_thumbnail} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_channel_video_server_thumbnail:[11192,"bool"]
     */
    public static final ABProp ENABLE_CHANNEL_VIDEO_SERVER_THUMBNAIL = new ABProp(11192, "false");

    /**
     * A/B prop {@code ctwa_custom_label_signals_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_custom_label_signals_enabled:[11205,"bool"]
     */
    public static final ABProp CTWA_CUSTOM_LABEL_SIGNALS_ENABLED = new ABProp(11205, "false");

    /**
     * A/B prop {@code mm_opt_out_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: mm_opt_out_enabled:[11241,"bool"]
     */
    public static final ABProp MM_OPT_OUT_ENABLED = new ABProp(11241, "false");

    /**
     * A/B prop {@code catalog_lid_migration_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: catalog_lid_migration_enabled:[11342,"bool"]
     */
    public static final ABProp CATALOG_LID_MIGRATION_ENABLED = new ABProp(11342, "true");

    /**
     * A/B prop {@code catalog_product_sale_price_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: catalog_product_sale_price_enabled:[11343,"bool"]
     */
    public static final ABProp CATALOG_PRODUCT_SALE_PRICE_ENABLED = new ABProp(11343, "false");

    /**
     * A/B prop {@code default_profile_pics_m1} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: default_profile_pics_m1:[11482,"bool"]
     */
    public static final ABProp DEFAULT_PROFILE_PICS_M1 = new ABProp(11482, "false");

    /**
     * A/B prop {@code smb_catalog_graphql_verify_postcode} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_catalog_graphql_verify_postcode:[11624,"bool"]
     */
    public static final ABProp SMB_CATALOG_GRAPHQL_VERIFY_POSTCODE = new ABProp(11624, "false");

    /**
     * A/B prop {@code group_safety_check_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: group_safety_check_enabled:[11627,"bool"]
     */
    public static final ABProp GROUP_SAFETY_CHECK_ENABLED = new ABProp(11627, "false");

    /**
     * A/B prop {@code native_contact_companion_nux_learn_more_article_id} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: native_contact_companion_nux_learn_more_article_id:[11644,"string"]
     */
    public static final ABProp NATIVE_CONTACT_COMPANION_NUX_LEARN_MORE_ARTICLE_ID = new ABProp(11644, "1191526044909364");

    /**
     * A/B prop {@code smb_catalog_graphql_update_product_visibility} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_catalog_graphql_update_product_visibility:[11651,"bool"]
     */
    public static final ABProp SMB_CATALOG_GRAPHQL_UPDATE_PRODUCT_VISIBILITY = new ABProp(11651, "false");

    /**
     * A/B prop {@code external_ctx_authorise_wa_chat} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: external_ctx_authorise_wa_chat:[11655,"bool"]
     */
    public static final ABProp EXTERNAL_CTX_AUTHORISE_WA_CHAT = new ABProp(11655, "false");

    /**
     * A/B prop {@code ai_fbid_migration_receive_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_fbid_migration_receive_enabled:[11660,"bool"]
     */
    public static final ABProp AI_FBID_MIGRATION_RECEIVE_ENABLED = new ABProp(11660, "false");

    /**
     * A/B prop {@code smb_catalog_graphql_get_public_key} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_catalog_graphql_get_public_key:[11690,"bool"]
     */
    public static final ABProp SMB_CATALOG_GRAPHQL_GET_PUBLIC_KEY = new ABProp(11690, "false");

    /**
     * A/B prop {@code saga_protobuf_ai_stardust_web} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: saga_protobuf_ai_stardust_web:[11756,"bool"]
     */
    public static final ABProp SAGA_PROTOBUF_AI_STARDUST_WEB = new ABProp(11756, "false");

    /**
     * A/B prop {@code syncd_mutation_and_bundle_logging} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: syncd_mutation_and_bundle_logging:[11821,"string"]
     */
    public static final ABProp SYNCD_MUTATION_AND_BUNDLE_LOGGING = new ABProp(11821, "{\"allowlist\": []}");

    /**
     * A/B prop {@code saga_protobuf_show_sysmsg_web} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: saga_protobuf_show_sysmsg_web:[11832,"bool"]
     */
    public static final ABProp SAGA_PROTOBUF_SHOW_SYSMSG_WEB = new ABProp(11832, "false");

    /**
     * A/B prop {@code ai_fbid_migration_sending} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_fbid_migration_sending:[11965,"bool"]
     */
    public static final ABProp AI_FBID_MIGRATION_SENDING = new ABProp(11965, "false");

    /**
     * A/B prop {@code futureproof_associated_child_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: futureproof_associated_child_enabled:[11976,"bool"]
     */
    public static final ABProp FUTUREPROOF_ASSOCIATED_CHILD_ENABLED = new ABProp(11976, "false");

    /**
     * A/B prop {@code use_signed_shimmed_url_link} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: use_signed_shimmed_url_link:[11977,"bool"]
     */
    public static final ABProp USE_SIGNED_SHIMMED_URL_LINK = new ABProp(11977, "false");

    /**
     * A/B prop {@code revoke_edit_attribute_validation_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: revoke_edit_attribute_validation_enabled:[12055,"bool"]
     */
    public static final ABProp REVOKE_EDIT_ATTRIBUTE_VALIDATION_ENABLED = new ABProp(12055, "false");

    /**
     * A/B prop {@code smb_catalog_graphql_commerce_settings} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_catalog_graphql_commerce_settings:[12099,"bool"]
     */
    public static final ABProp SMB_CATALOG_GRAPHQL_COMMERCE_SETTINGS = new ABProp(12099, "false");

    /**
     * A/B prop {@code mm_opt_out_fmx_stop_for_high_trust} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: mm_opt_out_fmx_stop_for_high_trust:[12172,"bool"]
     */
    public static final ABProp MM_OPT_OUT_FMX_STOP_FOR_HIGH_TRUST = new ABProp(12172, "false");

    /**
     * A/B prop {@code status_mentions_group_mention_receiver} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: status_mentions_group_mention_receiver:[12254,"bool"]
     */
    public static final ABProp STATUS_MENTIONS_GROUP_MENTION_RECEIVER = new ABProp(12254, "false");

    /**
     * A/B prop {@code poll_result_snapshot_polltype_envelope_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: poll_result_snapshot_polltype_envelope_enabled:[12258,"bool"]
     */
    public static final ABProp POLL_RESULT_SNAPSHOT_POLLTYPE_ENVELOPE_ENABLED = new ABProp(12258, "false");

    /**
     * A/B prop {@code web_new_chat_flow_refresh_variant} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_new_chat_flow_refresh_variant:[12276,"int"]
     */
    public static final ABProp WEB_NEW_CHAT_FLOW_REFRESH_VARIANT = new ABProp(12276, "0");

    /**
     * A/B prop {@code wam_disable_abkey_attribute} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wam_disable_abkey_attribute:[12390,"bool"]
     */
    public static final ABProp WAM_DISABLE_ABKEY_ATTRIBUTE = new ABProp(12390, "false");

    /**
     * A/B prop {@code wam_disable_expokey_attribute} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wam_disable_expokey_attribute:[12391,"bool"]
     */
    public static final ABProp WAM_DISABLE_EXPOKEY_ATTRIBUTE = new ABProp(12391, "false");

    /**
     * A/B prop {@code web_signal_future_messages_max} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_signal_future_messages_max:[12509,"int"]
     */
    public static final ABProp WEB_SIGNAL_FUTURE_MESSAGES_MAX = new ABProp(12509, "2e4");

    /**
     * A/B prop {@code flows_wa_web} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: flows_wa_web:[12520,"bool"]
     */
    public static final ABProp FLOWS_WA_WEB = new ABProp(12520, "false");

    /**
     * A/B prop {@code ai_rich_response_main_gate_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_rich_response_main_gate_enabled:[12539,"bool"]
     */
    public static final ABProp AI_RICH_RESPONSE_MAIN_GATE_ENABLED = new ABProp(12539, "true");

    /**
     * A/B prop {@code smb_catalog_graphql_delete_product} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_catalog_graphql_delete_product:[12543,"bool"]
     */
    public static final ABProp SMB_CATALOG_GRAPHQL_DELETE_PRODUCT = new ABProp(12543, "false");

    /**
     * A/B prop {@code otp_lid_migration_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: otp_lid_migration_enabled:[12553,"bool"]
     */
    public static final ABProp OTP_LID_MIGRATION_ENABLED = new ABProp(12553, "false");

    /**
     * A/B prop {@code rt_sender_dual_encrypted_msg_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: rt_sender_dual_encrypted_msg_enabled:[12623,"bool"]
     */
    public static final ABProp RT_SENDER_DUAL_ENCRYPTED_MSG_ENABLED = new ABProp(12623, "true");

    /**
     * A/B prop {@code external_ctx_url_param_names} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: external_ctx_url_param_names:[12726,"string"]
     */
    public static final ABProp EXTERNAL_CTX_URL_PARAM_NAMES = new ABProp(12726, "partnertoken");

    /**
     * A/B prop {@code mm_opt_out_list_server_sync_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: mm_opt_out_list_server_sync_enabled:[12758,"bool"]
     */
    public static final ABProp MM_OPT_OUT_LIST_SERVER_SYNC_ENABLED = new ABProp(12758, "false");

    /**
     * A/B prop {@code external_ctx_authorise_existing_chats} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: external_ctx_authorise_existing_chats:[12761,"int"]
     */
    public static final ABProp EXTERNAL_CTX_AUTHORISE_EXISTING_CHATS = new ABProp(12761, "0");

    /**
     * A/B prop {@code ai_fbid_migration_invoke_receive_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_fbid_migration_invoke_receive_enabled:[12795,"bool"]
     */
    public static final ABProp AI_FBID_MIGRATION_INVOKE_RECEIVE_ENABLED = new ABProp(12795, "false");

    /**
     * A/B prop {@code media_viewer_accelerated_playback_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: media_viewer_accelerated_playback_enabled:[12813,"bool"]
     */
    public static final ABProp MEDIA_VIEWER_ACCELERATED_PLAYBACK_ENABLED = new ABProp(12813, "false");

    /**
     * A/B prop {@code web_dexie_hooks_support_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_dexie_hooks_support_enabled:[12831,"bool"]
     */
    public static final ABProp WEB_DEXIE_HOOKS_SUPPORT_ENABLED = new ABProp(12831, "false");

    /**
     * A/B prop {@code contact_and_chat_fuzzy_search_distance_threshold} of floating-point type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: contact_and_chat_fuzzy_search_distance_threshold:[12863,"float"]
     */
    public static final ABProp CONTACT_AND_CHAT_FUZZY_SEARCH_DISTANCE_THRESHOLD = new ABProp(12863, ".30000001192092896");

    /**
     * A/B prop {@code contact_and_chat_fuzzy_search_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: contact_and_chat_fuzzy_search_enabled:[12864,"bool"]
     */
    public static final ABProp CONTACT_AND_CHAT_FUZZY_SEARCH_ENABLED = new ABProp(12864, "false");

    /**
     * A/B prop {@code contact_and_chat_fuzzy_search_timeout_threshold} of floating-point type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: contact_and_chat_fuzzy_search_timeout_threshold:[12865,"float"]
     */
    public static final ABProp CONTACT_AND_CHAT_FUZZY_SEARCH_TIMEOUT_THRESHOLD = new ABProp(12865, "5");

    /**
     * A/B prop {@code web_cache_storage_config_disable_ignore_search} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_cache_storage_config_disable_ignore_search:[12878,"bool"]
     */
    public static final ABProp WEB_CACHE_STORAGE_CONFIG_DISABLE_IGNORE_SEARCH = new ABProp(12878, "false");

    /**
     * A/B prop {@code override_adv_account_signature_key_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: override_adv_account_signature_key_enabled:[12933,"bool"]
     */
    public static final ABProp OVERRIDE_ADV_ACCOUNT_SIGNATURE_KEY_ENABLED = new ABProp(12933, "false");

    /**
     * A/B prop {@code web_ui_refresh_m1} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_ui_refresh_m1:[12993,"bool"]
     */
    public static final ABProp WEB_UI_REFRESH_M1 = new ABProp(12993, "false");

    /**
     * A/B prop {@code disclosure_for_the_marketing_message_body_links_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: disclosure_for_the_marketing_message_body_links_enabled:[12994,"bool"]
     */
    public static final ABProp DISCLOSURE_FOR_THE_MARKETING_MESSAGE_BODY_LINKS_ENABLED = new ABProp(12994, "false");

    /**
     * A/B prop {@code ft_validation_failure_drop_placeholder} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ft_validation_failure_drop_placeholder:[13063,"bool"]
     */
    public static final ABProp FT_VALIDATION_FAILURE_DROP_PLACEHOLDER = new ABProp(13063, "false");

    /**
     * A/B prop {@code wabba_save_to_camera_roll_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wabba_save_to_camera_roll_enabled:[13114,"bool"]
     */
    public static final ABProp WABBA_SAVE_TO_CAMERA_ROLL_ENABLED = new ABProp(13114, "false");

    /**
     * A/B prop {@code lid_one_on_one_migration_compatible} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: lid_one_on_one_migration_compatible:[13161,"bool"]
     */
    public static final ABProp LID_ONE_ON_ONE_MIGRATION_COMPATIBLE = new ABProp(13161, "true");

    /**
     * A/B prop {@code ctwa_enable_biz_data_sharing_after_nux_dismiss} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_enable_biz_data_sharing_after_nux_dismiss:[13240,"bool"]
     */
    public static final ABProp CTWA_ENABLE_BIZ_DATA_SHARING_AFTER_NUX_DISMISS = new ABProp(13240, "false");

    /**
     * A/B prop {@code anyone_can_link_to_groups} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: anyone_can_link_to_groups:[13268,"bool"]
     */
    public static final ABProp ANYONE_CAN_LINK_TO_GROUPS = new ABProp(13268, "false");

    /**
     * A/B prop {@code status_save_to_camera_roll_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: status_save_to_camera_roll_enabled:[13280,"bool"]
     */
    public static final ABProp STATUS_SAVE_TO_CAMERA_ROLL_ENABLED = new ABProp(13280, "false");

    /**
     * A/B prop {@code custom_racing_emoji_feb2025} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: custom_racing_emoji_feb2025:[13322,"bool"]
     */
    public static final ABProp CUSTOM_RACING_EMOJI_FEB2025 = new ABProp(13322, "false");

    /**
     * A/B prop {@code emoji_search_cldr} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: emoji_search_cldr:[13323,"bool"]
     */
    public static final ABProp EMOJI_SEARCH_CLDR = new ABProp(13323, "false");

    /**
     * A/B prop {@code enable_calling_username} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_calling_username:[13359,"bool"]
     */
    public static final ABProp ENABLE_CALLING_USERNAME = new ABProp(13359, "false");

    /**
     * A/B prop {@code per_customer_data_sharing_controls_eligible} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: per_customer_data_sharing_controls_eligible:[13383,"bool"]
     */
    public static final ABProp PER_CUSTOMER_DATA_SHARING_CONTROLS_ELIGIBLE = new ABProp(13383, "false");

    /**
     * A/B prop {@code ctwa_download_3pd_signals} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_download_3pd_signals:[13385,"bool"]
     */
    public static final ABProp CTWA_DOWNLOAD_3PD_SIGNALS = new ABProp(13385, "false");

    /**
     * A/B prop {@code smb_ai_agents_web_chat_assignment_interop_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_ai_agents_web_chat_assignment_interop_enabled:[13387,"bool"]
     */
    public static final ABProp SMB_AI_AGENTS_WEB_CHAT_ASSIGNMENT_INTEROP_ENABLED = new ABProp(13387, "false");

    /**
     * A/B prop {@code smb_product_country_of_origin_m1} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_product_country_of_origin_m1:[13415,"bool"]
     */
    public static final ABProp SMB_PRODUCT_COUNTRY_OF_ORIGIN_M1 = new ABProp(13415, "false");

    /**
     * A/B prop {@code biz_ai_auto_save_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: biz_ai_auto_save_enabled:[13464,"bool"]
     */
    public static final ABProp BIZ_AI_AUTO_SAVE_ENABLED = new ABProp(13464, "false");

    /**
     * A/B prop {@code biz_ai_coaching_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: biz_ai_coaching_enabled:[13465,"bool"]
     */
    public static final ABProp BIZ_AI_COACHING_ENABLED = new ABProp(13465, "false");

    /**
     * A/B prop {@code animated_race_mercedes_car_emoji_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: animated_race_mercedes_car_emoji_enabled:[13490,"bool"]
     */
    public static final ABProp ANIMATED_RACE_MERCEDES_CAR_EMOJI_ENABLED = new ABProp(13490, "false");

    /**
     * A/B prop {@code mm_user_controls_exposure} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: mm_user_controls_exposure:[13510,"bool"]
     */
    public static final ABProp MM_USER_CONTROLS_EXPOSURE = new ABProp(13510, "false");

    /**
     * A/B prop {@code external_ctx_foa_logging} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: external_ctx_foa_logging:[13565,"int"]
     */
    public static final ABProp EXTERNAL_CTX_FOA_LOGGING = new ABProp(13565, "1");

    /**
     * A/B prop {@code ai_fbid_migration_invoke_send_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_fbid_migration_invoke_send_enabled:[13571,"bool"]
     */
    public static final ABProp AI_FBID_MIGRATION_INVOKE_SEND_ENABLED = new ABProp(13571, "false");

    /**
     * A/B prop {@code ai_rich_response_grid_image_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_rich_response_grid_image_enabled:[13578,"bool"]
     */
    public static final ABProp AI_RICH_RESPONSE_GRID_IMAGE_ENABLED = new ABProp(13578, "false");

    /**
     * A/B prop {@code ctwa_show_ads_data_sharing_after_message} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_show_ads_data_sharing_after_message:[13579,"bool"]
     */
    public static final ABProp CTWA_SHOW_ADS_DATA_SHARING_AFTER_MESSAGE = new ABProp(13579, "false");

    /**
     * A/B prop {@code wa_web_fmx_agm_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_fmx_agm_enabled:[13597,"bool"]
     */
    public static final ABProp WA_WEB_FMX_AGM_ENABLED = new ABProp(13597, "false");

    /**
     * A/B prop {@code channels_ptv_forwarding_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_ptv_forwarding_enabled:[13776,"bool"]
     */
    public static final ABProp CHANNELS_PTV_FORWARDING_ENABLED = new ABProp(13776, "false");

    /**
     * A/B prop {@code premium_broadcast_smb_capping_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: premium_broadcast_smb_capping_enabled:[13808,"bool"]
     */
    public static final ABProp PREMIUM_BROADCAST_SMB_CAPPING_ENABLED = new ABProp(13808, "false");

    /**
     * A/B prop {@code is_group_chat_open_logging_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: is_group_chat_open_logging_enabled:[13864,"bool"]
     */
    public static final ABProp IS_GROUP_CHAT_OPEN_LOGGING_ENABLED = new ABProp(13864, "false");

    /**
     * A/B prop {@code ai_forward_flow_surface_meta_ai_as_contact_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_forward_flow_surface_meta_ai_as_contact_enabled:[13879,"bool"]
     */
    public static final ABProp AI_FORWARD_FLOW_SURFACE_META_AI_AS_CONTACT_ENABLED = new ABProp(13879, "false");

    /**
     * A/B prop {@code lid_one_on_one_migration_peer_sync_timeout_in_seconds} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: lid_one_on_one_migration_peer_sync_timeout_in_seconds:[13936,"int"]
     */
    public static final ABProp LID_ONE_ON_ONE_MIGRATION_PEER_SYNC_TIMEOUT_IN_SECONDS = new ABProp(13936, "300");

    /**
     * A/B prop {@code group_status_receiver_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: group_status_receiver_enabled:[13956,"bool"]
     */
    public static final ABProp GROUP_STATUS_RECEIVER_ENABLED = new ABProp(13956, "false");

    /**
     * A/B prop {@code ai_pdfn_tos_inline_notices} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_pdfn_tos_inline_notices:[13970,"string"]
     */
    public static final ABProp AI_PDFN_TOS_INLINE_NOTICES = new ABProp(13970, " ");

    /**
     * A/B prop {@code enable_group_exit_experience} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_group_exit_experience:[13996,"bool"]
     */
    public static final ABProp ENABLE_GROUP_EXIT_EXPERIENCE = new ABProp(13996, "false");

    /**
     * A/B prop {@code mm_user_controls_exception_number_prefixes} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: mm_user_controls_exception_number_prefixes:[13999,"string"]
     */
    public static final ABProp MM_USER_CONTROLS_EXCEPTION_NUMBER_PREFIXES = new ABProp(13999, "");

    /**
     * A/B prop {@code snapl_newsletter_logging_media_id_placeholder_string} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: snapl_newsletter_logging_media_id_placeholder_string:[14064,"string"]
     */
    public static final ABProp SNAPL_NEWSLETTER_LOGGING_MEDIA_ID_PLACEHOLDER_STRING = new ABProp(14064, "-1");

    /**
     * A/B prop {@code ai_rich_response_smb_web_structured_response_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_rich_response_smb_web_structured_response_enabled:[14140,"bool"]
     */
    public static final ABProp AI_RICH_RESPONSE_SMB_WEB_STRUCTURED_RESPONSE_ENABLED = new ABProp(14140, "false");

    /**
     * A/B prop {@code ai_rich_response_web_structured_response_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_rich_response_web_structured_response_enabled:[14141,"bool"]
     */
    public static final ABProp AI_RICH_RESPONSE_WEB_STRUCTURED_RESPONSE_ENABLED = new ABProp(14141, "false");

    /**
     * A/B prop {@code view_replies_infra_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: view_replies_infra_enabled:[14199,"bool"]
     */
    public static final ABProp VIEW_REPLIES_INFRA_ENABLED = new ABProp(14199, "false");

    /**
     * A/B prop {@code is_part_of_gsc_experiment} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: is_part_of_gsc_experiment:[14279,"bool"]
     */
    public static final ABProp IS_PART_OF_GSC_EXPERIMENT = new ABProp(14279, "false");

    /**
     * A/B prop {@code username_numeric_code_v4} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: username_numeric_code_v4:[14286,"int"]
     */
    public static final ABProp USERNAME_NUMERIC_CODE_V4 = new ABProp(14286, "0");

    /**
     * A/B prop {@code web_catalog_recovery_flow_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_catalog_recovery_flow_enabled:[14294,"bool"]
     */
    public static final ABProp WEB_CATALOG_RECOVERY_FLOW_ENABLED = new ABProp(14294, "false");

    /**
     * A/B prop {@code web_waffle} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_waffle:[14300,"bool"]
     */
    public static final ABProp WEB_WAFFLE = new ABProp(14300, "false");

    /**
     * A/B prop {@code lid_trusted_token_issue_to_lid} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: lid_trusted_token_issue_to_lid:[14303,"bool"]
     */
    public static final ABProp LID_TRUSTED_TOKEN_ISSUE_TO_LID = new ABProp(14303, "false");

    /**
     * A/B prop {@code support_lids} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: support_lids:[14317,"string"]
     */
    public static final ABProp SUPPORT_LIDS = new ABProp(14317, "4200746488034,30563255730192,70334669676777,19349129719984,66065505775654,133814269518032,243799792062487,7323238039569,269290422947912,261718412386336,4351103873168,12391299473616,92410801582180,277730033709185,36090878648473,79882365190287,94274800595104,117794058317863,115784047153172,179250745360524,7301780005088,166653589463190,94249030815912,198964645236955,198427807899653,23656948363422,255735573270728,106670109786240,130932396826763,18855208456329");

    /**
     * A/B prop {@code payment_support_lids} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: payment_support_lids:[14333,"string"]
     */
    public static final ABProp PAYMENT_SUPPORT_LIDS = new ABProp(14333, "116664750354676,128385682505839,46635358933114,26521959944357,200206125658243,179985503506636,187797998674170,228746200088715,117914552262794,10158134550607");

    /**
     * A/B prop {@code payment_br_holdout} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: payment_br_holdout:[14358,"bool"]
     */
    public static final ABProp PAYMENT_BR_HOLDOUT = new ABProp(14358, "false");

    /**
     * A/B prop {@code render_updated_disclosure} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: render_updated_disclosure:[14407,"bool"]
     */
    public static final ABProp RENDER_UPDATED_DISCLOSURE = new ABProp(14407, "false");

    /**
     * A/B prop {@code syncd_sentinel_timeout_seconds} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: syncd_sentinel_timeout_seconds:[14485,"int"]
     */
    public static final ABProp SYNCD_SENTINEL_TIMEOUT_SECONDS = new ABProp(14485, "3");

    /**
     * A/B prop {@code syncd_key_max_use_days} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: syncd_key_max_use_days:[14488,"int"]
     */
    public static final ABProp SYNCD_KEY_MAX_USE_DAYS = new ABProp(14488, "30");

    /**
     * A/B prop {@code syncd_wait_for_key_timeout_days} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: syncd_wait_for_key_timeout_days:[14492,"int"]
     */
    public static final ABProp SYNCD_WAIT_FOR_KEY_TIMEOUT_DAYS = new ABProp(14492, "7");

    /**
     * A/B prop {@code syncd_inline_mutations_max_count} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: syncd_inline_mutations_max_count:[14494,"int"]
     */
    public static final ABProp SYNCD_INLINE_MUTATIONS_MAX_COUNT = new ABProp(14494, "100");

    /**
     * A/B prop {@code syncd_patch_protobuf_max_size} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: syncd_patch_protobuf_max_size:[14495,"int"]
     */
    public static final ABProp SYNCD_PATCH_PROTOBUF_MAX_SIZE = new ABProp(14495, "10");

    /**
     * A/B prop {@code username_contact_usync_lid_based} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: username_contact_usync_lid_based:[14565,"bool"]
     */
    public static final ABProp USERNAME_CONTACT_USYNC_LID_BASED = new ABProp(14565, "false");

    /**
     * A/B prop {@code optimized_delivery_multiple_collection_windows_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: optimized_delivery_multiple_collection_windows_enabled:[14588,"bool"]
     */
    public static final ABProp OPTIMIZED_DELIVERY_MULTIPLE_COLLECTION_WINDOWS_ENABLED = new ABProp(14588, "false");

    /**
     * A/B prop {@code enable_group_exit_experience_logging} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_group_exit_experience_logging:[14589,"bool"]
     */
    public static final ABProp ENABLE_GROUP_EXIT_EXPERIENCE_LOGGING = new ABProp(14589, "false");

    /**
     * A/B prop {@code files_media_hub_web} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: files_media_hub_web:[14751,"bool"]
     */
    public static final ABProp FILES_MEDIA_HUB_WEB = new ABProp(14751, "false");

    /**
     * A/B prop {@code group_description_length} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: group_description_length:[14778,"int"]
     */
    public static final ABProp GROUP_DESCRIPTION_LENGTH = new ABProp(14778, "2048");

    /**
     * A/B prop {@code group_max_subject} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: group_max_subject:[14801,"int"]
     */
    public static final ABProp GROUP_MAX_SUBJECT = new ABProp(14801, "100");

    /**
     * A/B prop {@code web_biz_profile_options} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_biz_profile_options:[14881,"int"]
     */
    public static final ABProp WEB_BIZ_PROFILE_OPTIONS = new ABProp(14881, "116");

    /**
     * A/B prop {@code ctwa_custom_label_algorithm} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_custom_label_algorithm:[14887,"int"]
     */
    public static final ABProp CTWA_CUSTOM_LABEL_ALGORITHM = new ABProp(14887, "0");

    /**
     * A/B prop {@code smb_payment_links_cta_variant} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_payment_links_cta_variant:[14957,"int"]
     */
    public static final ABProp SMB_PAYMENT_LINKS_CTA_VARIANT = new ABProp(14957, "2");

    /**
     * A/B prop {@code smb_payment_links_cta_button_kill_switch} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_payment_links_cta_button_kill_switch:[14967,"bool"]
     */
    public static final ABProp SMB_PAYMENT_LINKS_CTA_BUTTON_KILL_SWITCH = new ABProp(14967, "false");

    /**
     * A/B prop {@code wamo_privacy_tos_linked_highlighted_notice_id} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wamo_privacy_tos_linked_highlighted_notice_id:[14985,"string"]
     */
    public static final ABProp WAMO_PRIVACY_TOS_LINKED_HIGHLIGHTED_NOTICE_ID = new ABProp(14985, "20610204");

    /**
     * A/B prop {@code wamo_privacy_tos_unlinked_highlighted_notice_id} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wamo_privacy_tos_unlinked_highlighted_notice_id:[14987,"string"]
     */
    public static final ABProp WAMO_PRIVACY_TOS_UNLINKED_HIGHLIGHTED_NOTICE_ID = new ABProp(14987, "20610203");

    /**
     * A/B prop {@code smb_payment_links_cta_psp_list} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_payment_links_cta_psp_list:[14998,"string"]
     */
    public static final ABProp SMB_PAYMENT_LINKS_CTA_PSP_LIST = new ABProp(14998, "{}");

    /**
     * A/B prop {@code rt_edit_receive} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: rt_edit_receive:[15016,"bool"]
     */
    public static final ABProp RT_EDIT_RECEIVE = new ABProp(15016, "true");

    /**
     * A/B prop {@code limit_sharing_enabled_for_1on1_chat} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: limit_sharing_enabled_for_1on1_chat:[15127,"bool"]
     */
    public static final ABProp LIMIT_SHARING_ENABLED_FOR_1ON1_CHAT = new ABProp(15127, "false");

    /**
     * A/B prop {@code limit_sharing_enabled_for_group_chat} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: limit_sharing_enabled_for_group_chat:[15128,"bool"]
     */
    public static final ABProp LIMIT_SHARING_ENABLED_FOR_GROUP_CHAT = new ABProp(15128, "false");

    /**
     * A/B prop {@code limit_sharing_protocol_message_receiver_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: limit_sharing_protocol_message_receiver_enabled:[15129,"bool"]
     */
    public static final ABProp LIMIT_SHARING_PROTOCOL_MESSAGE_RECEIVER_ENABLED = new ABProp(15129, "false");

    /**
     * A/B prop {@code rt_web_delay_processing} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: rt_web_delay_processing:[15181,"bool"]
     */
    public static final ABProp RT_WEB_DELAY_PROCESSING = new ABProp(15181, "false");

    /**
     * A/B prop {@code wamo_privacy_tos_show_channels_nux_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wamo_privacy_tos_show_channels_nux_enabled:[15254,"bool"]
     */
    public static final ABProp WAMO_PRIVACY_TOS_SHOW_CHANNELS_NUX_ENABLED = new ABProp(15254, "true");

    /**
     * A/B prop {@code newsletter_nux_notice_id} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: newsletter_nux_notice_id:[15255,"string"]
     */
    public static final ABProp NEWSLETTER_NUX_NOTICE_ID = new ABProp(15255, "20610210");

    /**
     * A/B prop {@code newsletter_admin_invite_nux_id} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: newsletter_admin_invite_nux_id:[15256,"string"]
     */
    public static final ABProp NEWSLETTER_ADMIN_INVITE_NUX_ID = new ABProp(15256, "20610220");

    /**
     * A/B prop {@code rt_receiver_dual_encrypted_msg_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: rt_receiver_dual_encrypted_msg_enabled:[15258,"bool"]
     */
    public static final ABProp RT_RECEIVER_DUAL_ENCRYPTED_MSG_ENABLED = new ABProp(15258, "true");

    /**
     * A/B prop {@code ai_rich_response_smb_web_structured_response_receiver_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_rich_response_smb_web_structured_response_receiver_enabled:[15266,"bool"]
     */
    public static final ABProp AI_RICH_RESPONSE_SMB_WEB_STRUCTURED_RESPONSE_RECEIVER_ENABLED = new ABProp(15266, "false");

    /**
     * A/B prop {@code ai_rich_response_web_structured_response_receiver_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_rich_response_web_structured_response_receiver_enabled:[15269,"bool"]
     */
    public static final ABProp AI_RICH_RESPONSE_WEB_STRUCTURED_RESPONSE_RECEIVER_ENABLED = new ABProp(15269, "false");

    /**
     * A/B prop {@code ctwa_important_label_sends_signals} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_important_label_sends_signals:[15271,"bool"]
     */
    public static final ABProp CTWA_IMPORTANT_LABEL_SENDS_SIGNALS = new ABProp(15271, "false");

    /**
     * A/B prop {@code ai_pdfn_tos_non_blocking_notices} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_pdfn_tos_non_blocking_notices:[15280,"string"]
     */
    public static final ABProp AI_PDFN_TOS_NON_BLOCKING_NOTICES = new ABProp(15280, "");

    /**
     * A/B prop {@code ai_pdfn_tos_master_notice_id} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_pdfn_tos_master_notice_id:[15295,"string"]
     */
    public static final ABProp AI_PDFN_TOS_MASTER_NOTICE_ID = new ABProp(15295, " ");

    /**
     * A/B prop {@code ctwa_smb_detected_outcome_labels_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_smb_detected_outcome_labels_enabled:[15307,"bool"]
     */
    public static final ABProp CTWA_SMB_DETECTED_OUTCOME_LABELS_ENABLED = new ABProp(15307, "false");

    /**
     * A/B prop {@code ctwa_smb_detected_outcome_labels_merger_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_smb_detected_outcome_labels_merger_enabled:[15308,"bool"]
     */
    public static final ABProp CTWA_SMB_DETECTED_OUTCOME_LABELS_MERGER_ENABLED = new ABProp(15308, "false");

    /**
     * A/B prop {@code group_history_receive} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: group_history_receive:[15311,"bool"]
     */
    public static final ABProp GROUP_HISTORY_RECEIVE = new ABProp(15311, "false");

    /**
     * A/B prop {@code group_history_send} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: group_history_send:[15313,"bool"]
     */
    public static final ABProp GROUP_HISTORY_SEND = new ABProp(15313, "false");

    /**
     * A/B prop {@code enable_web_calling} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_web_calling:[15461,"bool"]
     */
    public static final ABProp ENABLE_WEB_CALLING = new ABProp(15461, "false");

    /**
     * A/B prop {@code ctwa_smb_detected_outcome_labels_soak_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_smb_detected_outcome_labels_soak_enabled:[15472,"bool"]
     */
    public static final ABProp CTWA_SMB_DETECTED_OUTCOME_LABELS_SOAK_ENABLED = new ABProp(15472, "false");

    /**
     * A/B prop {@code biz_ai_in_thread_unmute_v2} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: biz_ai_in_thread_unmute_v2:[15523,"bool"]
     */
    public static final ABProp BIZ_AI_IN_THREAD_UNMUTE_V2 = new ABProp(15523, "false");

    /**
     * A/B prop {@code ctwa_3pd_data_sharing_cooldown_for_opted_out} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_3pd_data_sharing_cooldown_for_opted_out:[15530,"bool"]
     */
    public static final ABProp CTWA_3PD_DATA_SHARING_COOLDOWN_FOR_OPTED_OUT = new ABProp(15530, "false");

    /**
     * A/B prop {@code web_catalog_viewing_variants_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_catalog_viewing_variants_enabled:[15534,"bool"]
     */
    public static final ABProp WEB_CATALOG_VIEWING_VARIANTS_ENABLED = new ABProp(15534, "false");

    /**
     * A/B prop {@code support_use_dedicated_system_event} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: support_use_dedicated_system_event:[15537,"bool"]
     */
    public static final ABProp SUPPORT_USE_DEDICATED_SYSTEM_EVENT = new ABProp(15537, "false");

    /**
     * A/B prop {@code wa_web_growth_empty_state_upsell_variant_m1} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_growth_empty_state_upsell_variant_m1:[15557,"int"]
     */
    public static final ABProp WA_WEB_GROWTH_EMPTY_STATE_UPSELL_VARIANT_M1 = new ABProp(15557, "1");

    /**
     * A/B prop {@code ai_rich_response_reasoning_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_rich_response_reasoning_enabled:[15589,"bool"]
     */
    public static final ABProp AI_RICH_RESPONSE_REASONING_ENABLED = new ABProp(15589, "false");

    /**
     * A/B prop {@code ctwa_smb_detected_outcome_labels_banners_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_smb_detected_outcome_labels_banners_enabled:[15591,"bool"]
     */
    public static final ABProp CTWA_SMB_DETECTED_OUTCOME_LABELS_BANNERS_ENABLED = new ABProp(15591, "false");

    /**
     * A/B prop {@code biz_ai_consumer_tos_update_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: biz_ai_consumer_tos_update_enabled:[15643,"bool"]
     */
    public static final ABProp BIZ_AI_CONSUMER_TOS_UPDATE_ENABLED = new ABProp(15643, "false");

    /**
     * A/B prop {@code phone_number_sharing_flow} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: phone_number_sharing_flow:[15653,"bool"]
     */
    public static final ABProp PHONE_NUMBER_SHARING_FLOW = new ABProp(15653, "false");

    /**
     * A/B prop {@code wamo_agm_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wamo_agm_enabled:[15714,"bool"]
     */
    public static final ABProp WAMO_AGM_ENABLED = new ABProp(15714, "false");

    /**
     * A/B prop {@code group_history_notice_receive} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: group_history_notice_receive:[15722,"bool"]
     */
    public static final ABProp GROUP_HISTORY_NOTICE_RECEIVE = new ABProp(15722, "false");

    /**
     * A/B prop {@code web_fetch_privacy_list_my_contacts_except} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_fetch_privacy_list_my_contacts_except:[15788,"bool"]
     */
    public static final ABProp WEB_FETCH_PRIVACY_LIST_MY_CONTACTS_EXCEPT = new ABProp(15788, "false");

    /**
     * A/B prop {@code lid_one_to_one_migration_event_response_force_pn_jid} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: lid_one_to_one_migration_event_response_force_pn_jid:[15791,"bool"]
     */
    public static final ABProp LID_ONE_TO_ONE_MIGRATION_EVENT_RESPONSE_FORCE_PN_JID = new ABProp(15791, "false");

    /**
     * A/B prop {@code kmp_syncd_engine_crypto_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: kmp_syncd_engine_crypto_enabled:[15909,"bool"]
     */
    public static final ABProp KMP_SYNCD_ENGINE_CRYPTO_ENABLED = new ABProp(15909, "false");

    /**
     * A/B prop {@code username_contact_ui} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: username_contact_ui:[15916,"bool"]
     */
    public static final ABProp USERNAME_CONTACT_UI = new ABProp(15916, "false");

    /**
     * A/B prop {@code username_search} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: username_search:[15956,"bool"]
     */
    public static final ABProp USERNAME_SEARCH = new ABProp(15956, "false");

    /**
     * A/B prop {@code ai_chat_persistent_meta_ai_banner_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_chat_persistent_meta_ai_banner_enabled:[15967,"bool"]
     */
    public static final ABProp AI_CHAT_PERSISTENT_META_AI_BANNER_ENABLED = new ABProp(15967, "false");

    /**
     * A/B prop {@code ai_chat_persistent_meta_ai_banner_logging_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_chat_persistent_meta_ai_banner_logging_enabled:[15968,"bool"]
     */
    public static final ABProp AI_CHAT_PERSISTENT_META_AI_BANNER_LOGGING_ENABLED = new ABProp(15968, "false");

    /**
     * A/B prop {@code contact_and_chat_fuzzy_search_similarity_optimization_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: contact_and_chat_fuzzy_search_similarity_optimization_enabled:[16010,"bool"]
     */
    public static final ABProp CONTACT_AND_CHAT_FUZZY_SEARCH_SIMILARITY_OPTIMIZATION_ENABLED = new ABProp(16010, "false");

    /**
     * A/B prop {@code message_edit_to_message_secret_sender_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: message_edit_to_message_secret_sender_enabled:[16057,"bool"]
     */
    public static final ABProp MESSAGE_EDIT_TO_MESSAGE_SECRET_SENDER_ENABLED = new ABProp(16057, "false");

    /**
     * A/B prop {@code lid_group_migration_non_member_iq} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: lid_group_migration_non_member_iq:[16104,"bool"]
     */
    public static final ABProp LID_GROUP_MIGRATION_NON_MEMBER_IQ = new ABProp(16104, "false");

    /**
     * A/B prop {@code wa_web_debug_color_code_retry_messages} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_debug_color_code_retry_messages:[16138,"bool"]
     */
    public static final ABProp WA_WEB_DEBUG_COLOR_CODE_RETRY_MESSAGES = new ABProp(16138, "false");

    /**
     * A/B prop {@code username_group_mutation_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: username_group_mutation_enabled:[16148,"bool"]
     */
    public static final ABProp USERNAME_GROUP_MUTATION_ENABLED = new ABProp(16148, "false");

    /**
     * A/B prop {@code payments_br_pix_on_web} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: payments_br_pix_on_web:[16156,"bool"]
     */
    public static final ABProp PAYMENTS_BR_PIX_ON_WEB = new ABProp(16156, "false");

    /**
     * A/B prop {@code privacy_settings_profile_lid_migration_enable} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: privacy_settings_profile_lid_migration_enable:[16161,"bool"]
     */
    public static final ABProp PRIVACY_SETTINGS_PROFILE_LID_MIGRATION_ENABLE = new ABProp(16161, "false");

    /**
     * A/B prop {@code privacy_settings_about_lid_migration_enable} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: privacy_settings_about_lid_migration_enable:[16195,"bool"]
     */
    public static final ABProp PRIVACY_SETTINGS_ABOUT_LID_MIGRATION_ENABLE = new ABProp(16195, "false");

    /**
     * A/B prop {@code privacy_settings_group_add_lid_migration_enable} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: privacy_settings_group_add_lid_migration_enable:[16274,"bool"]
     */
    public static final ABProp PRIVACY_SETTINGS_GROUP_ADD_LID_MIGRATION_ENABLE = new ABProp(16274, "false");

    /**
     * A/B prop {@code privacy_settings_presence_lid_migration_enable} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: privacy_settings_presence_lid_migration_enable:[16275,"bool"]
     */
    public static final ABProp PRIVACY_SETTINGS_PRESENCE_LID_MIGRATION_ENABLE = new ABProp(16275, "false");

    /**
     * A/B prop {@code enable_peer_snapshot_recovery} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_peer_snapshot_recovery:[16329,"bool"]
     */
    public static final ABProp ENABLE_PEER_SNAPSHOT_RECOVERY = new ABProp(16329, "false");

    /**
     * A/B prop {@code group_history_bump_message_id} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: group_history_bump_message_id:[16346,"int"]
     */
    public static final ABProp GROUP_HISTORY_BUMP_MESSAGE_ID = new ABProp(16346, "200");

    /**
     * A/B prop {@code limit_sharing_update_enabled_web} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: limit_sharing_update_enabled_web:[16376,"bool"]
     */
    public static final ABProp LIMIT_SHARING_UPDATE_ENABLED_WEB = new ABProp(16376, "false");

    /**
     * A/B prop {@code web_offline_resume_wait_for_ping_response_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_offline_resume_wait_for_ping_response_enabled:[16488,"bool"]
     */
    public static final ABProp WEB_OFFLINE_RESUME_WAIT_FOR_PING_RESPONSE_ENABLED = new ABProp(16488, "false");

    /**
     * A/B prop {@code files_media_hub_web_variant} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: files_media_hub_web_variant:[16511,"int"]
     */
    public static final ABProp FILES_MEDIA_HUB_WEB_VARIANT = new ABProp(16511, "0");

    /**
     * A/B prop {@code enable_inactive_group_lid_migration} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_inactive_group_lid_migration:[16520,"bool"]
     */
    public static final ABProp ENABLE_INACTIVE_GROUP_LID_MIGRATION = new ABProp(16520, "false");

    /**
     * A/B prop {@code rich_order_status_wa_web} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: rich_order_status_wa_web:[16534,"bool"]
     */
    public static final ABProp RICH_ORDER_STATUS_WA_WEB = new ABProp(16534, "false");

    /**
     * A/B prop {@code member_name_tag_db_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: member_name_tag_db_enabled:[16551,"bool"]
     */
    public static final ABProp MEMBER_NAME_TAG_DB_ENABLED = new ABProp(16551, "true");

    /**
     * A/B prop {@code pnh_thread_promotion_to_general_lid} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: pnh_thread_promotion_to_general_lid:[16632,"bool"]
     */
    public static final ABProp PNH_THREAD_PROMOTION_TO_GENERAL_LID = new ABProp(16632, "false");

    /**
     * A/B prop {@code ai_rich_response_forward_receiving_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_rich_response_forward_receiving_enabled:[16682,"bool"]
     */
    public static final ABProp AI_RICH_RESPONSE_FORWARD_RECEIVING_ENABLED = new ABProp(16682, "false");

    /**
     * A/B prop {@code mm_signal_sharing_verification_system_lid_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: mm_signal_sharing_verification_system_lid_enabled:[16727,"bool"]
     */
    public static final ABProp MM_SIGNAL_SHARING_VERIFICATION_SYSTEM_LID_ENABLED = new ABProp(16727, "true");

    /**
     * A/B prop {@code wds_web_button} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wds_web_button:[16785,"bool"]
     */
    public static final ABProp WDS_WEB_BUTTON = new ABProp(16785, "false");

    /**
     * A/B prop {@code wa_web_console_log_level} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_console_log_level:[16806,"int"]
     */
    public static final ABProp WA_WEB_CONSOLE_LOG_LEVEL = new ABProp(16806, "3");

    /**
     * A/B prop {@code web_pdf_thumbnail_size_in_bytes} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_pdf_thumbnail_size_in_bytes:[16834,"int"]
     */
    public static final ABProp WEB_PDF_THUMBNAIL_SIZE_IN_BYTES = new ABProp(16834, "1300");

    /**
     * A/B prop {@code payment_links_trust_signals_metatag_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: payment_links_trust_signals_metatag_enabled:[16866,"bool"]
     */
    public static final ABProp PAYMENT_LINKS_TRUST_SIGNALS_METATAG_ENABLED = new ABProp(16866, "false");

    /**
     * A/B prop {@code mm_opt_out_lid_migration_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: mm_opt_out_lid_migration_enabled:[16952,"bool"]
     */
    public static final ABProp MM_OPT_OUT_LID_MIGRATION_ENABLED = new ABProp(16952, "false");

    /**
     * A/B prop {@code web_offline_resume_wait_for_ping_timeout_seconds} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_offline_resume_wait_for_ping_timeout_seconds:[16956,"int"]
     */
    public static final ABProp WEB_OFFLINE_RESUME_WAIT_FOR_PING_TIMEOUT_SECONDS = new ABProp(16956, "10");

    /**
     * A/B prop {@code biz_ai_web_ai_hub_tap_cta_show_alert} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: biz_ai_web_ai_hub_tap_cta_show_alert:[17093,"bool"]
     */
    public static final ABProp BIZ_AI_WEB_AI_HUB_TAP_CTA_SHOW_ALERT = new ABProp(17093, "false");

    /**
     * A/B prop {@code payment_links_trust_signals_metatag_psp_list} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: payment_links_trust_signals_metatag_psp_list:[17162,"string"]
     */
    public static final ABProp PAYMENT_LINKS_TRUST_SIGNALS_METATAG_PSP_LIST = new ABProp(17162, "{\"psp\":[\"mercadopago\"]} ");

    /**
     * A/B prop {@code history_sync_on_demand_companion} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: history_sync_on_demand_companion:[17198,"bool"]
     */
    public static final ABProp HISTORY_SYNC_ON_DEMAND_COMPANION = new ABProp(17198, "false");

    /**
     * A/B prop {@code ai_ugc_not_an_expert_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_ugc_not_an_expert_enabled:[17285,"bool"]
     */
    public static final ABProp AI_UGC_NOT_AN_EXPERT_ENABLED = new ABProp(17285, "false");

    /**
     * A/B prop {@code wa_web_unexpected_locale_reload_fix_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_unexpected_locale_reload_fix_enabled:[17328,"bool"]
     */
    public static final ABProp WA_WEB_UNEXPECTED_LOCALE_RELOAD_FIX_ENABLED = new ABProp(17328, "false");

    /**
     * A/B prop {@code wa_web_resume_timer_fix_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_resume_timer_fix_enabled:[17329,"bool"]
     */
    public static final ABProp WA_WEB_RESUME_TIMER_FIX_ENABLED = new ABProp(17329, "false");

    /**
     * A/B prop {@code web_dau_overreporting_fix} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_dau_overreporting_fix:[17350,"bool"]
     */
    public static final ABProp WEB_DAU_OVERREPORTING_FIX = new ABProp(17350, "false");

    /**
     * A/B prop {@code ai_rich_response_side_by_side_survey_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_rich_response_side_by_side_survey_enabled:[17408,"bool"]
     */
    public static final ABProp AI_RICH_RESPONSE_SIDE_BY_SIDE_SURVEY_ENABLED = new ABProp(17408, "false");

    /**
     * A/B prop {@code channels_question_follower_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_question_follower_enabled:[17425,"bool"]
     */
    public static final ABProp CHANNELS_QUESTION_FOLLOWER_ENABLED = new ABProp(17425, "false");

    /**
     * A/B prop {@code channels_question_admin_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_question_admin_enabled:[17426,"bool"]
     */
    public static final ABProp CHANNELS_QUESTION_ADMIN_ENABLED = new ABProp(17426, "false");

    /**
     * A/B prop {@code smb_business_broadcast_import_contact} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_business_broadcast_import_contact:[17433,"bool"]
     */
    public static final ABProp SMB_BUSINESS_BROADCAST_IMPORT_CONTACT = new ABProp(17433, "false");

    /**
     * A/B prop {@code web_linked_catalog_consumer_cart_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_linked_catalog_consumer_cart_enabled:[17466,"bool"]
     */
    public static final ABProp WEB_LINKED_CATALOG_CONSUMER_CART_ENABLED = new ABProp(17466, "false");

    /**
     * A/B prop {@code ctwa_suppress_message_via_ad_spam_web} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_suppress_message_via_ad_spam_web:[17580,"bool"]
     */
    public static final ABProp CTWA_SUPPRESS_MESSAGE_VIA_AD_SPAM_WEB = new ABProp(17580, "false");

    /**
     * A/B prop {@code username_contact_syncd_support_enable} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: username_contact_syncd_support_enable:[17614,"bool"]
     */
    public static final ABProp USERNAME_CONTACT_SYNCD_SUPPORT_ENABLE = new ABProp(17614, "false");

    /**
     * A/B prop {@code calls_tab_username_global_search_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: calls_tab_username_global_search_enabled:[17698,"bool"]
     */
    public static final ABProp CALLS_TAB_USERNAME_GLOBAL_SEARCH_ENABLED = new ABProp(17698, "false");

    /**
     * A/B prop {@code enable_calling_phone_number_privacy} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_calling_phone_number_privacy:[17731,"bool"]
     */
    public static final ABProp ENABLE_CALLING_PHONE_NUMBER_PRIVACY = new ABProp(17731, "false");

    /**
     * A/B prop {@code wds_web_checkbox} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wds_web_checkbox:[17790,"bool"]
     */
    public static final ABProp WDS_WEB_CHECKBOX = new ABProp(17790, "false");

    /**
     * A/B prop {@code message_edit_to_message_secret_receiver_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: message_edit_to_message_secret_receiver_enabled:[17811,"bool"]
     */
    public static final ABProp MESSAGE_EDIT_TO_MESSAGE_SECRET_RECEIVER_ENABLED = new ABProp(17811, "false");

    /**
     * A/B prop {@code wa_individual_new_chat_msg_capping_limit} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_individual_new_chat_msg_capping_limit:[17845,"int"]
     */
    public static final ABProp WA_INDIVIDUAL_NEW_CHAT_MSG_CAPPING_LIMIT = new ABProp(17845, "0");

    /**
     * A/B prop {@code smoothie_performance_msg_send} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smoothie_performance_msg_send:[17942,"bool"]
     */
    public static final ABProp SMOOTHIE_PERFORMANCE_MSG_SEND = new ABProp(17942, "true");

    /**
     * A/B prop {@code smoothie_performance_chatlist_search} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smoothie_performance_chatlist_search:[17946,"bool"]
     */
    public static final ABProp SMOOTHIE_PERFORMANCE_CHATLIST_SEARCH = new ABProp(17946, "false");

    /**
     * A/B prop {@code meta_ai_in_app_survey_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: meta_ai_in_app_survey_enabled:[17956,"bool"]
     */
    public static final ABProp META_AI_IN_APP_SURVEY_ENABLED = new ABProp(17956, "false");

    /**
     * A/B prop {@code smoothie_performance_msg_send_followup} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smoothie_performance_msg_send_followup:[17996,"bool"]
     */
    public static final ABProp SMOOTHIE_PERFORMANCE_MSG_SEND_FOLLOWUP = new ABProp(17996, "false");

    /**
     * A/B prop {@code smoothie_performance_command_palette} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smoothie_performance_command_palette:[18021,"bool"]
     */
    public static final ABProp SMOOTHIE_PERFORMANCE_COMMAND_PALETTE = new ABProp(18021, "false");

    /**
     * A/B prop {@code advanced_chat_privacy_content_update_july_25} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: advanced_chat_privacy_content_update_july_25:[18025,"bool"]
     */
    public static final ABProp ADVANCED_CHAT_PRIVACY_CONTENT_UPDATE_JULY_25 = new ABProp(18025, "false");

    /**
     * A/B prop {@code enable_avatars_on_web_companion} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_avatars_on_web_companion:[18081,"bool"]
     */
    public static final ABProp ENABLE_AVATARS_ON_WEB_COMPANION = new ABProp(18081, "false");

    /**
     * A/B prop {@code pushname_blocklist_starting_with_at} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: pushname_blocklist_starting_with_at:[18097,"bool"]
     */
    public static final ABProp PUSHNAME_BLOCKLIST_STARTING_WITH_AT = new ABProp(18097, "false");

    /**
     * A/B prop {@code username_contact_ui_vcard} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: username_contact_ui_vcard:[18204,"bool"]
     */
    public static final ABProp USERNAME_CONTACT_UI_VCARD = new ABProp(18204, "false");

    /**
     * A/B prop {@code username_global_search_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: username_global_search_enabled:[18251,"bool"]
     */
    public static final ABProp USERNAME_GLOBAL_SEARCH_ENABLED = new ABProp(18251, "false");

    /**
     * A/B prop {@code web_quota_exceeded_app_reload_flow_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_quota_exceeded_app_reload_flow_enabled:[18269,"bool"]
     */
    public static final ABProp WEB_QUOTA_EXCEEDED_APP_RELOAD_FLOW_ENABLED = new ABProp(18269, "false");

    /**
     * A/B prop {@code ai_forward_attribution_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_forward_attribution_enabled:[18286,"bool"]
     */
    public static final ABProp AI_FORWARD_ATTRIBUTION_ENABLED = new ABProp(18286, "false");

    /**
     * A/B prop {@code status_pog_id_rotation_window_days} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: status_pog_id_rotation_window_days:[18297,"int"]
     */
    public static final ABProp STATUS_POG_ID_ROTATION_WINDOW_DAYS = new ABProp(18297, "-1");

    /**
     * A/B prop {@code history_sync_on_demand_time_boundary_days_desktops} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: history_sync_on_demand_time_boundary_days_desktops:[18391,"int"]
     */
    public static final ABProp HISTORY_SYNC_ON_DEMAND_TIME_BOUNDARY_DAYS_DESKTOPS = new ABProp(18391, "1095");

    /**
     * A/B prop {@code channels_creation_entrypoint_in_directory_enabled} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_creation_entrypoint_in_directory_enabled:[18613,"int"]
     */
    public static final ABProp CHANNELS_CREATION_ENTRYPOINT_IN_DIRECTORY_ENABLED = new ABProp(18613, "0");

    /**
     * A/B prop {@code optimized_delivery_block_and_report_entry_points_allowlist_web} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: optimized_delivery_block_and_report_entry_points_allowlist_web:[18736,"string"]
     */
    public static final ABProp OPTIMIZED_DELIVERY_BLOCK_AND_REPORT_ENTRY_POINTS_ALLOWLIST_WEB = new ABProp(18736, "4,10,12,13,14,15,17,18,24,31,32,33,34,35,36,39,40,45");

    /**
     * A/B prop {@code ai_search_experience_web_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_search_experience_web_enabled:[18740,"bool"]
     */
    public static final ABProp AI_SEARCH_EXPERIENCE_WEB_ENABLED = new ABProp(18740, "false");

    /**
     * A/B prop {@code ai_rich_response_ur_media_grid_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_rich_response_ur_media_grid_enabled:[18746,"bool"]
     */
    public static final ABProp AI_RICH_RESPONSE_UR_MEDIA_GRID_ENABLED = new ABProp(18746, "false");

    /**
     * A/B prop {@code web_low_end_device_level} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_low_end_device_level:[18747,"int"]
     */
    public static final ABProp WEB_LOW_END_DEVICE_LEVEL = new ABProp(18747, "0");

    /**
     * A/B prop {@code snapshot_recovery_max_mutations_count_allowed} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: snapshot_recovery_max_mutations_count_allowed:[18786,"int"]
     */
    public static final ABProp SNAPSHOT_RECOVERY_MAX_MUTATIONS_COUNT_ALLOWED = new ABProp(18786, "2e3");

    /**
     * A/B prop {@code wa_web_falco_clear_local_storage_queue_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_falco_clear_local_storage_queue_enabled:[18835,"bool"]
     */
    public static final ABProp WA_WEB_FALCO_CLEAR_LOCAL_STORAGE_QUEUE_ENABLED = new ABProp(18835, "false");

    /**
     * A/B prop {@code ai_migrate_away_from_inline_tos_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_migrate_away_from_inline_tos_enabled:[18843,"bool"]
     */
    public static final ABProp AI_MIGRATE_AWAY_FROM_INLINE_TOS_ENABLED = new ABProp(18843, "false");

    /**
     * A/B prop {@code ctwa_native_ads_creation_web_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_native_ads_creation_web_enabled:[18857,"bool"]
     */
    public static final ABProp CTWA_NATIVE_ADS_CREATION_WEB_ENABLED = new ABProp(18857, "false");

    /**
     * A/B prop {@code channels_creation_entrypoint_in_updates_tab_enabled} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_creation_entrypoint_in_updates_tab_enabled:[18925,"int"]
     */
    public static final ABProp CHANNELS_CREATION_ENTRYPOINT_IN_UPDATES_TAB_ENABLED = new ABProp(18925, "0");

    /**
     * A/B prop {@code username_check_debounce_in_ms} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: username_check_debounce_in_ms:[18975,"int"]
     */
    public static final ABProp USERNAME_CHECK_DEBOUNCE_IN_MS = new ABProp(18975, "600");

    /**
     * A/B prop {@code channels_question_fetch_responses_page_size} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_question_fetch_responses_page_size:[18984,"int"]
     */
    public static final ABProp CHANNELS_QUESTION_FETCH_RESPONSES_PAGE_SIZE = new ABProp(18984, "30");

    /**
     * A/B prop {@code smoothie_performance_resize_followup} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smoothie_performance_resize_followup:[18992,"bool"]
     */
    public static final ABProp SMOOTHIE_PERFORMANCE_RESIZE_FOLLOWUP = new ABProp(18992, "false");

    /**
     * A/B prop {@code coex_edit_msg_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: coex_edit_msg_enabled:[19039,"bool"]
     */
    public static final ABProp COEX_EDIT_MSG_ENABLED = new ABProp(19039, "false");

    /**
     * A/B prop {@code utility_order_status_logging_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: utility_order_status_logging_enabled:[19059,"bool"]
     */
    public static final ABProp UTILITY_ORDER_STATUS_LOGGING_ENABLED = new ABProp(19059, "false");

    /**
     * A/B prop {@code wa_web_history_sync_dynamic_throttling} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_history_sync_dynamic_throttling:[19110,"bool"]
     */
    public static final ABProp WA_WEB_HISTORY_SYNC_DYNAMIC_THROTTLING = new ABProp(19110, "true");

    /**
     * A/B prop {@code public_bug_reporting_settings} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: public_bug_reporting_settings:[19127,"bool"]
     */
    public static final ABProp PUBLIC_BUG_REPORTING_SETTINGS = new ABProp(19127, "false");

    /**
     * A/B prop {@code payments_br_pix_web_attachment_tray} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: payments_br_pix_web_attachment_tray:[19276,"bool"]
     */
    public static final ABProp PAYMENTS_BR_PIX_WEB_ATTACHMENT_TRAY = new ABProp(19276, "false");

    /**
     * A/B prop {@code coex_revoke_message_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: coex_revoke_message_enabled:[19285,"bool"]
     */
    public static final ABProp COEX_REVOKE_MESSAGE_ENABLED = new ABProp(19285, "false");

    /**
     * A/B prop {@code chatlist_show_draft_for_empty_chat} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: chatlist_show_draft_for_empty_chat:[19287,"bool"]
     */
    public static final ABProp CHATLIST_SHOW_DRAFT_FOR_EMPTY_CHAT = new ABProp(19287, "false");

    /**
     * A/B prop {@code web_anr_throttle_history_sync_db_writes} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_anr_throttle_history_sync_db_writes:[19298,"bool"]
     */
    public static final ABProp WEB_ANR_THROTTLE_HISTORY_SYNC_DB_WRITES = new ABProp(19298, "false");

    /**
     * A/B prop {@code payment_link_trace_id_logging_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: payment_link_trace_id_logging_enabled:[19440,"bool"]
     */
    public static final ABProp PAYMENT_LINK_TRACE_ID_LOGGING_ENABLED = new ABProp(19440, "false");

    /**
     * A/B prop {@code channels_qpl_improvements_supported_types} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_qpl_improvements_supported_types:[19589,"string"]
     */
    public static final ABProp CHANNELS_QPL_IMPROVEMENTS_SUPPORTED_TYPES = new ABProp(19589, "");

    /**
     * A/B prop {@code ai_web_forward_flow_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_web_forward_flow_enabled:[19676,"bool"]
     */
    public static final ABProp AI_WEB_FORWARD_FLOW_ENABLED = new ABProp(19676, "false");

    /**
     * A/B prop {@code lid_status_non_soaked_client_support_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: lid_status_non_soaked_client_support_enabled:[19696,"bool"]
     */
    public static final ABProp LID_STATUS_NON_SOAKED_CLIENT_SUPPORT_ENABLED = new ABProp(19696, "true");

    /**
     * A/B prop {@code web_hybrid_getters_cache_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_hybrid_getters_cache_enabled:[19700,"bool"]
     */
    public static final ABProp WEB_HYBRID_GETTERS_CACHE_ENABLED = new ABProp(19700, "false");

    /**
     * A/B prop {@code ctwa_per_customer_data_sharing_controls_do_not_show_msg_until_chosen} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_per_customer_data_sharing_controls_do_not_show_msg_until_chosen:[19763,"bool"]
     */
    public static final ABProp CTWA_PER_CUSTOMER_DATA_SHARING_CONTROLS_DO_NOT_SHOW_MSG_UNTIL_CHOSEN = new ABProp(19763, "false");

    /**
     * A/B prop {@code web_enable_improved_bulk_merge} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_enable_improved_bulk_merge:[19854,"bool"]
     */
    public static final ABProp WEB_ENABLE_IMPROVED_BULK_MERGE = new ABProp(19854, "false");

    /**
     * A/B prop {@code newsletter_forward_counter_ui_enabled} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: newsletter_forward_counter_ui_enabled:[19888,"int"]
     */
    public static final ABProp NEWSLETTER_FORWARD_COUNTER_UI_ENABLED = new ABProp(19888, "0");

    /**
     * A/B prop {@code enable_fmx_logging} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_fmx_logging:[19893,"bool"]
     */
    public static final ABProp ENABLE_FMX_LOGGING = new ABProp(19893, "false");

    /**
     * A/B prop {@code web_channel_video_server_transcode_upload} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_channel_video_server_transcode_upload:[19920,"bool"]
     */
    public static final ABProp WEB_CHANNEL_VIDEO_SERVER_TRANSCODE_UPLOAD = new ABProp(19920, "false");

    /**
     * A/B prop {@code smb_core_biz_profile_ux_refreshed} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_core_biz_profile_ux_refreshed:[19929,"bool"]
     */
    public static final ABProp SMB_CORE_BIZ_PROFILE_UX_REFRESHED = new ABProp(19929, "false");

    /**
     * A/B prop {@code ctwa_web_custom_label_signals_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_web_custom_label_signals_enabled:[19985,"bool"]
     */
    public static final ABProp CTWA_WEB_CUSTOM_LABEL_SIGNALS_ENABLED = new ABProp(19985, "false");

    /**
     * A/B prop {@code channels_question_response_rate_limit_max_count_in_client_ui} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_question_response_rate_limit_max_count_in_client_ui:[19989,"int"]
     */
    public static final ABProp CHANNELS_QUESTION_RESPONSE_RATE_LIMIT_MAX_COUNT_IN_CLIENT_UI = new ABProp(19989, "5");

    /**
     * A/B prop {@code web_fix_duplicated_lids_history_sync} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_fix_duplicated_lids_history_sync:[19994,"bool"]
     */
    public static final ABProp WEB_FIX_DUPLICATED_LIDS_HISTORY_SYNC = new ABProp(19994, "false");

    /**
     * A/B prop {@code biz_ai_agent_thread_status_history_sync_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: biz_ai_agent_thread_status_history_sync_enabled:[20099,"bool"]
     */
    public static final ABProp BIZ_AI_AGENT_THREAD_STATUS_HISTORY_SYNC_ENABLED = new ABProp(20099, "false");

    /**
     * A/B prop {@code ctwa_web_3pd_data_sharing_cooldown_for_opted_out} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_web_3pd_data_sharing_cooldown_for_opted_out:[20131,"bool"]
     */
    public static final ABProp CTWA_WEB_3PD_DATA_SHARING_COOLDOWN_FOR_OPTED_OUT = new ABProp(20131, "false");

    /**
     * A/B prop {@code channels_music_receiver_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_music_receiver_enabled:[20266,"bool"]
     */
    public static final ABProp CHANNELS_MUSIC_RECEIVER_ENABLED = new ABProp(20266, "false");

    /**
     * A/B prop {@code web_use_kaleidoscope_media_check_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_use_kaleidoscope_media_check_enabled:[20375,"bool"]
     */
    public static final ABProp WEB_USE_KALEIDOSCOPE_MEDIA_CHECK_ENABLED = new ABProp(20375, "false");

    /**
     * A/B prop {@code ctwa_native_ads_creation_web_hawk_tool_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_native_ads_creation_web_hawk_tool_enabled:[20442,"bool"]
     */
    public static final ABProp CTWA_NATIVE_ADS_CREATION_WEB_HAWK_TOOL_ENABLED = new ABProp(20442, "false");

    /**
     * A/B prop {@code ai_web_meta_ai_image_input_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_web_meta_ai_image_input_enabled:[20522,"bool"]
     */
    public static final ABProp AI_WEB_META_AI_IMAGE_INPUT_ENABLED = new ABProp(20522, "false");

    /**
     * A/B prop {@code pending_group_requests_persistent_banner} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: pending_group_requests_persistent_banner:[20545,"bool"]
     */
    public static final ABProp PENDING_GROUP_REQUESTS_PERSISTENT_BANNER = new ABProp(20545, "false");

    /**
     * A/B prop {@code wa_webtp_use_thumbnail_renderer} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_webtp_use_thumbnail_renderer:[20555,"bool"]
     */
    public static final ABProp WA_WEBTP_USE_THUMBNAIL_RENDERER = new ABProp(20555, "false");

    /**
     * A/B prop {@code wa_individual_new_chat_msg_latest_rampup_date} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_individual_new_chat_msg_latest_rampup_date:[20601,"int"]
     */
    public static final ABProp WA_INDIVIDUAL_NEW_CHAT_MSG_LATEST_RAMPUP_DATE = new ABProp(20601, "0");

    /**
     * A/B prop {@code wa_webtp_use_pdf_renderer} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_webtp_use_pdf_renderer:[20607,"bool"]
     */
    public static final ABProp WA_WEBTP_USE_PDF_RENDERER = new ABProp(20607, "false");

    /**
     * A/B prop {@code ai_chat_threads_infra_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_chat_threads_infra_enabled:[20652,"bool"]
     */
    public static final ABProp AI_CHAT_THREADS_INFRA_ENABLED = new ABProp(20652, "false");

    /**
     * A/B prop {@code ctwa_native_ads_creation_web_targeting_modal_hawk_tool_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_native_ads_creation_web_targeting_modal_hawk_tool_enabled:[20731,"bool"]
     */
    public static final ABProp CTWA_NATIVE_ADS_CREATION_WEB_TARGETING_MODAL_HAWK_TOOL_ENABLED = new ABProp(20731, "false");

    /**
     * A/B prop {@code profile_scraping_privacy_token_in_about_usync} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: profile_scraping_privacy_token_in_about_usync:[20798,"bool"]
     */
    public static final ABProp PROFILE_SCRAPING_PRIVACY_TOKEN_IN_ABOUT_USYNC = new ABProp(20798, "false");

    /**
     * A/B prop {@code favorite_sticker_sync_after_pairing_enabled_web} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: favorite_sticker_sync_after_pairing_enabled_web:[20815,"bool"]
     */
    public static final ABProp FAVORITE_STICKER_SYNC_AFTER_PAIRING_ENABLED_WEB = new ABProp(20815, "false");

    /**
     * A/B prop {@code biz_ai_tos_variant} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: biz_ai_tos_variant:[20833,"int"]
     */
    public static final ABProp BIZ_AI_TOS_VARIANT = new ABProp(20833, "0");

    /**
     * A/B prop {@code hide_auto_quotes_on_web} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: hide_auto_quotes_on_web:[20892,"bool"]
     */
    public static final ABProp HIDE_AUTO_QUOTES_ON_WEB = new ABProp(20892, "false");

    /**
     * A/B prop {@code enable_web_group_calling} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_web_group_calling:[20924,"bool"]
     */
    public static final ABProp ENABLE_WEB_GROUP_CALLING = new ABProp(20924, "false");

    /**
     * A/B prop {@code wds_web_chip} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wds_web_chip:[20970,"bool"]
     */
    public static final ABProp WDS_WEB_CHIP = new ABProp(20970, "false");

    /**
     * A/B prop {@code history_sync_on_demand_complete_companion} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: history_sync_on_demand_complete_companion:[21024,"bool"]
     */
    public static final ABProp HISTORY_SYNC_ON_DEMAND_COMPLETE_COMPANION = new ABProp(21024, "false");

    /**
     * A/B prop {@code web_threads_infra_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_threads_infra_enabled:[21062,"bool"]
     */
    public static final ABProp WEB_THREADS_INFRA_ENABLED = new ABProp(21062, "true");

    /**
     * A/B prop {@code ai_dynamic_model_branding_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_dynamic_model_branding_enabled:[21086,"bool"]
     */
    public static final ABProp AI_DYNAMIC_MODEL_BRANDING_ENABLED = new ABProp(21086, "false");

    /**
     * A/B prop {@code paa_support_for_disabled_epehemerality} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: paa_support_for_disabled_epehemerality:[21235,"bool"]
     */
    public static final ABProp PAA_SUPPORT_FOR_DISABLED_EPEHEMERALITY = new ABProp(21235, "false");

    /**
     * A/B prop {@code web_navigation_bar_updates_tab} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_navigation_bar_updates_tab:[21250,"bool"]
     */
    public static final ABProp WEB_NAVIGATION_BAR_UPDATES_TAB = new ABProp(21250, "false");

    /**
     * A/B prop {@code group_history_settings} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: group_history_settings:[21261,"bool"]
     */
    public static final ABProp GROUP_HISTORY_SETTINGS = new ABProp(21261, "false");

    /**
     * A/B prop {@code mm_data_sharing_disclosure_enabled_companion_history_sync} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: mm_data_sharing_disclosure_enabled_companion_history_sync:[21288,"bool"]
     */
    public static final ABProp MM_DATA_SHARING_DISCLOSURE_ENABLED_COMPANION_HISTORY_SYNC = new ABProp(21288, "false");

    /**
     * A/B prop {@code wds_web_roboto} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wds_web_roboto:[21379,"int"]
     */
    public static final ABProp WDS_WEB_ROBOTO = new ABProp(21379, "0");

    /**
     * A/B prop {@code group_history_settings_toggle_ui} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: group_history_settings_toggle_ui:[21481,"bool"]
     */
    public static final ABProp GROUP_HISTORY_SETTINGS_TOGGLE_UI = new ABProp(21481, "false");

    /**
     * A/B prop {@code web_status_crossposting_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_status_crossposting_enabled:[21501,"bool"]
     */
    public static final ABProp WEB_STATUS_CROSSPOSTING_ENABLED = new ABProp(21501, "false");

    /**
     * A/B prop {@code smb_business_broadcast_send_web} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_business_broadcast_send_web:[21508,"bool"]
     */
    public static final ABProp SMB_BUSINESS_BROADCAST_SEND_WEB = new ABProp(21508, "false");

    /**
     * A/B prop {@code ai_continuous_session_transparency_notice_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_continuous_session_transparency_notice_enabled:[21510,"bool"]
     */
    public static final ABProp AI_CONTINUOUS_SESSION_TRANSPARENCY_NOTICE_ENABLED = new ABProp(21510, "false");

    /**
     * A/B prop {@code enable_tooltip_for_media_hub} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_tooltip_for_media_hub:[21535,"bool"]
     */
    public static final ABProp ENABLE_TOOLTIP_FOR_MEDIA_HUB = new ABProp(21535, "false");

    /**
     * A/B prop {@code rt_swapped_fallback_validation} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: rt_swapped_fallback_validation:[21718,"bool"]
     */
    public static final ABProp RT_SWAPPED_FALLBACK_VALIDATION = new ABProp(21718, "true");

    /**
     * A/B prop {@code br_payments_pix_groups_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: br_payments_pix_groups_enabled:[21741,"bool"]
     */
    public static final ABProp BR_PAYMENTS_PIX_GROUPS_ENABLED = new ABProp(21741, "false");

    /**
     * A/B prop {@code wa_web_wae_qpl_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_wae_qpl_enabled:[21742,"bool"]
     */
    public static final ABProp WA_WEB_WAE_QPL_ENABLED = new ABProp(21742, "true");

    /**
     * A/B prop {@code ctwa_suppress_message_with_external_ad_reply_consumer_db_level_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_suppress_message_with_external_ad_reply_consumer_db_level_enabled:[21819,"bool"]
     */
    public static final ABProp CTWA_SUPPRESS_MESSAGE_WITH_EXTERNAL_AD_REPLY_CONSUMER_DB_LEVEL_ENABLED = new ABProp(21819, "false");

    /**
     * A/B prop {@code wa_web_enable_granular_notifications} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_enable_granular_notifications:[21909,"bool"]
     */
    public static final ABProp WA_WEB_ENABLE_GRANULAR_NOTIFICATIONS = new ABProp(21909, "false");

    /**
     * A/B prop {@code enable_agm_flow_cta} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_agm_flow_cta:[22006,"bool"]
     */
    public static final ABProp ENABLE_AGM_FLOW_CTA = new ABProp(22006, "false");

    /**
     * A/B prop {@code ai_chat_thread_capability_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_chat_thread_capability_enabled:[22038,"bool"]
     */
    public static final ABProp AI_CHAT_THREAD_CAPABILITY_ENABLED = new ABProp(22038, "false");

    /**
     * A/B prop {@code ai_chat_threads_historical_messages_migration_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_chat_threads_historical_messages_migration_enabled:[22070,"bool"]
     */
    public static final ABProp AI_CHAT_THREADS_HISTORICAL_MESSAGES_MIGRATION_ENABLED = new ABProp(22070, "false");

    /**
     * A/B prop {@code wa_web_lists_m2_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_lists_m2_enabled:[22086,"bool"]
     */
    public static final ABProp WA_WEB_LISTS_M2_ENABLED = new ABProp(22086, "false");

    /**
     * A/B prop {@code channels_music_forwarding_disabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_music_forwarding_disabled:[22089,"bool"]
     */
    public static final ABProp CHANNELS_MUSIC_FORWARDING_DISABLED = new ABProp(22089, "false");

    /**
     * A/B prop {@code wa_web_lists_m1_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_lists_m1_enabled:[22090,"bool"]
     */
    public static final ABProp WA_WEB_LISTS_M1_ENABLED = new ABProp(22090, "false");

    /**
     * A/B prop {@code web_cache_open_failed_reload_flow_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_cache_open_failed_reload_flow_enabled:[22155,"bool"]
     */
    public static final ABProp WEB_CACHE_OPEN_FAILED_RELOAD_FLOW_ENABLED = new ABProp(22155, "false");

    /**
     * A/B prop {@code ai_group_participation_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_group_participation_enabled:[22171,"bool"]
     */
    public static final ABProp AI_GROUP_PARTICIPATION_ENABLED = new ABProp(22171, "false");

    /**
     * A/B prop {@code ai_group_participation_send_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_group_participation_send_enabled:[22184,"bool"]
     */
    public static final ABProp AI_GROUP_PARTICIPATION_SEND_ENABLED = new ABProp(22184, "false");

    /**
     * A/B prop {@code newsletter_forward_counter_max_send_after_random_time} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: newsletter_forward_counter_max_send_after_random_time:[22206,"int"]
     */
    public static final ABProp NEWSLETTER_FORWARD_COUNTER_MAX_SEND_AFTER_RANDOM_TIME = new ABProp(22206, "3600");

    /**
     * A/B prop {@code ai_group_participation_add_tee_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_group_participation_add_tee_enabled:[22236,"bool"]
     */
    public static final ABProp AI_GROUP_PARTICIPATION_ADD_TEE_ENABLED = new ABProp(22236, "false");

    /**
     * A/B prop {@code enable_futureproof_galaxy_flow_message_for_business_numbers} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_futureproof_galaxy_flow_message_for_business_numbers:[22311,"string"]
     */
    public static final ABProp ENABLE_FUTUREPROOF_GALAXY_FLOW_MESSAGE_FOR_BUSINESS_NUMBERS = new ABProp(22311, "");

    /**
     * A/B prop {@code utility_payment_reminder_m1_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: utility_payment_reminder_m1_enabled:[22434,"bool"]
     */
    public static final ABProp UTILITY_PAYMENT_REMINDER_M1_ENABLED = new ABProp(22434, "false");

    /**
     * A/B prop {@code media_hub_history_max_days} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: media_hub_history_max_days:[22518,"int"]
     */
    public static final ABProp MEDIA_HUB_HISTORY_MAX_DAYS = new ABProp(22518, "14");

    /**
     * A/B prop {@code smb_core_biz_profile_ux_refreshed_v2} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_core_biz_profile_ux_refreshed_v2:[22561,"bool"]
     */
    public static final ABProp SMB_CORE_BIZ_PROFILE_UX_REFRESHED_V2 = new ABProp(22561, "false");

    /**
     * A/B prop {@code member_name_tag_web_sender_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: member_name_tag_web_sender_enabled:[22654,"bool"]
     */
    public static final ABProp MEMBER_NAME_TAG_WEB_SENDER_ENABLED = new ABProp(22654, "false");

    /**
     * A/B prop {@code member_name_tag_web_receiver_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: member_name_tag_web_receiver_enabled:[22655,"bool"]
     */
    public static final ABProp MEMBER_NAME_TAG_WEB_RECEIVER_ENABLED = new ABProp(22655, "false");

    /**
     * A/B prop {@code ai_rich_response_post_citations_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_rich_response_post_citations_enabled:[22672,"bool"]
     */
    public static final ABProp AI_RICH_RESPONSE_POST_CITATIONS_ENABLED = new ABProp(22672, "false");

    /**
     * A/B prop {@code ai_rich_response_zeitgeist_carousel_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_rich_response_zeitgeist_carousel_enabled:[22750,"bool"]
     */
    public static final ABProp AI_RICH_RESPONSE_ZEITGEIST_CAROUSEL_ENABLED = new ABProp(22750, "false");

    /**
     * A/B prop {@code wa_media_image_upload_cache} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_media_image_upload_cache:[22784,"bool"]
     */
    public static final ABProp WA_MEDIA_IMAGE_UPLOAD_CACHE = new ABProp(22784, "false");

    /**
     * A/B prop {@code ai_imagine_loading_indicator_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_imagine_loading_indicator_enabled:[22795,"bool"]
     */
    public static final ABProp AI_IMAGINE_LOADING_INDICATOR_ENABLED = new ABProp(22795, "false");

    /**
     * A/B prop {@code message_keys_async_chunk_size} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: message_keys_async_chunk_size:[22815,"int"]
     */
    public static final ABProp MESSAGE_KEYS_ASYNC_CHUNK_SIZE = new ABProp(22815, "50");

    /**
     * A/B prop {@code synced_message_keys_processing_type} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: synced_message_keys_processing_type:[22825,"string"]
     */
    public static final ABProp SYNCED_MESSAGE_KEYS_PROCESSING_TYPE = new ABProp(22825, "control");

    /**
     * A/B prop {@code wa_web_favicon_badging_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_favicon_badging_enabled:[22924,"bool"]
     */
    public static final ABProp WA_WEB_FAVICON_BADGING_ENABLED = new ABProp(22924, "false");

    /**
     * A/B prop {@code web_anr_file_size_threshold_to_use_worker_mb} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_anr_file_size_threshold_to_use_worker_mb:[22930,"int"]
     */
    public static final ABProp WEB_ANR_FILE_SIZE_THRESHOLD_TO_USE_WORKER_MB = new ABProp(22930, "0");

    /**
     * A/B prop {@code web_anr_media_chunk_enc_delay_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_anr_media_chunk_enc_delay_enabled:[22931,"bool"]
     */
    public static final ABProp WEB_ANR_MEDIA_CHUNK_ENC_DELAY_ENABLED = new ABProp(22931, "false");

    /**
     * A/B prop {@code smb_graphql_merchant_info_set_compliance} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_graphql_merchant_info_set_compliance:[23026,"bool"]
     */
    public static final ABProp SMB_GRAPHQL_MERCHANT_INFO_SET_COMPLIANCE = new ABProp(23026, "false");

    /**
     * A/B prop {@code smb_graphql_merchant_info_get_compliance} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_graphql_merchant_info_get_compliance:[23027,"bool"]
     */
    public static final ABProp SMB_GRAPHQL_MERCHANT_INFO_GET_COMPLIANCE = new ABProp(23027, "false");

    /**
     * A/B prop {@code br_smb_paymentshome_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: br_smb_paymentshome_enabled:[23042,"bool"]
     */
    public static final ABProp BR_SMB_PAYMENTSHOME_ENABLED = new ABProp(23042, "false");

    /**
     * A/B prop {@code ai_chat_threads_web_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_chat_threads_web_enabled:[23169,"bool"]
     */
    public static final ABProp AI_CHAT_THREADS_WEB_ENABLED = new ABProp(23169, "false");

    /**
     * A/B prop {@code ai_session_transparency_meta_ai_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_session_transparency_meta_ai_enabled:[23188,"bool"]
     */
    public static final ABProp AI_SESSION_TRANSPARENCY_META_AI_ENABLED = new ABProp(23188, "false");

    /**
     * A/B prop {@code web_anr_async_media_decryption_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_anr_async_media_decryption_enabled:[23200,"bool"]
     */
    public static final ABProp WEB_ANR_ASYNC_MEDIA_DECRYPTION_ENABLED = new ABProp(23200, "false");

    /**
     * A/B prop {@code aura_pinned_chats_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: aura_pinned_chats_enabled:[23277,"bool"]
     */
    public static final ABProp AURA_PINNED_CHATS_ENABLED = new ABProp(23277, "false");

    /**
     * A/B prop {@code aura_pinned_chats_benefit_active} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: aura_pinned_chats_benefit_active:[23278,"bool"]
     */
    public static final ABProp AURA_PINNED_CHATS_BENEFIT_ACTIVE = new ABProp(23278, "false");

    /**
     * A/B prop {@code ai_unified_response_sender_web_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_unified_response_sender_web_enabled:[23347,"bool"]
     */
    public static final ABProp AI_UNIFIED_RESPONSE_SENDER_WEB_ENABLED = new ABProp(23347, "false");

    /**
     * A/B prop {@code ai_unified_response_receiver_web_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_unified_response_receiver_web_enabled:[23348,"bool"]
     */
    public static final ABProp AI_UNIFIED_RESPONSE_RECEIVER_WEB_ENABLED = new ABProp(23348, "false");

    /**
     * A/B prop {@code mex_get_privacy_settings_mode} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: mex_get_privacy_settings_mode:[23463,"int"]
     */
    public static final ABProp MEX_GET_PRIVACY_SETTINGS_MODE = new ABProp(23463, "0");

    /**
     * A/B prop {@code coex_calling_permissions_3p_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: coex_calling_permissions_3p_enabled:[23464,"bool"]
     */
    public static final ABProp COEX_CALLING_PERMISSIONS_3P_ENABLED = new ABProp(23464, "false");

    /**
     * A/B prop {@code wa_webtp_use_pdf_editor} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_webtp_use_pdf_editor:[23498,"bool"]
     */
    public static final ABProp WA_WEBTP_USE_PDF_EDITOR = new ABProp(23498, "false");

    /**
     * A/B prop {@code web_ai_group_open_support} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_ai_group_open_support:[23530,"bool"]
     */
    public static final ABProp WEB_AI_GROUP_OPEN_SUPPORT = new ABProp(23530, "false");

    /**
     * A/B prop {@code booking_confirmation_enabled_wa_web} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: booking_confirmation_enabled_wa_web:[23559,"bool"]
     */
    public static final ABProp BOOKING_CONFIRMATION_ENABLED_WA_WEB = new ABProp(23559, "false");

    /**
     * A/B prop {@code ctwa_native_ads_creation_web_enabled_no_exposure} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ctwa_native_ads_creation_web_enabled_no_exposure:[23655,"bool"]
     */
    public static final ABProp CTWA_NATIVE_ADS_CREATION_WEB_ENABLED_NO_EXPOSURE = new ABProp(23655, "false");

    /**
     * A/B prop {@code ai_chat_threads_web_msgs_load_limit} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_chat_threads_web_msgs_load_limit:[23694,"int"]
     */
    public static final ABProp AI_CHAT_THREADS_WEB_MSGS_LOAD_LIMIT = new ABProp(23694, "50");

    /**
     * A/B prop {@code web_chatpsa_forwarding} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_chatpsa_forwarding:[23695,"bool"]
     */
    public static final ABProp WEB_CHATPSA_FORWARDING = new ABProp(23695, "false");

    /**
     * A/B prop {@code ai_web_ask_meta_ai_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_web_ask_meta_ai_enabled:[23725,"bool"]
     */
    public static final ABProp AI_WEB_ASK_META_AI_ENABLED = new ABProp(23725, "false");

    /**
     * A/B prop {@code username_channels_pn_privacy_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: username_channels_pn_privacy_enabled:[23795,"bool"]
     */
    public static final ABProp USERNAME_CHANNELS_PN_PRIVACY_ENABLED = new ABProp(23795, "false");

    /**
     * A/B prop {@code top_level_message_secret_check} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: top_level_message_secret_check:[23796,"bool"]
     */
    public static final ABProp TOP_LEVEL_MESSAGE_SECRET_CHECK = new ABProp(23796, "false");

    /**
     * A/B prop {@code wa_web_device_id_test_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_device_id_test_enabled:[23810,"bool"]
     */
    public static final ABProp WA_WEB_DEVICE_ID_TEST_ENABLED = new ABProp(23810, "false");

    /**
     * A/B prop {@code username_enabled_on_companion} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: username_enabled_on_companion:[23817,"bool"]
     */
    public static final ABProp USERNAME_ENABLED_ON_COMPANION = new ABProp(23817, "false");

    /**
     * A/B prop {@code ai_rich_response_inline_links_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_rich_response_inline_links_enabled:[23819,"bool"]
     */
    public static final ABProp AI_RICH_RESPONSE_INLINE_LINKS_ENABLED = new ABProp(23819, "false");

    /**
     * A/B prop {@code biz_ai_consumer_tos_update_web} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: biz_ai_consumer_tos_update_web:[23880,"bool"]
     */
    public static final ABProp BIZ_AI_CONSUMER_TOS_UPDATE_WEB = new ABProp(23880, "false");

    /**
     * A/B prop {@code ai_mode_selector_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_mode_selector_enabled:[23885,"bool"]
     */
    public static final ABProp AI_MODE_SELECTOR_ENABLED = new ABProp(23885, "false");

    /**
     * A/B prop {@code web_skip_cache_for_large_media_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_skip_cache_for_large_media_enabled:[24051,"bool"]
     */
    public static final ABProp WEB_SKIP_CACHE_FOR_LARGE_MEDIA_ENABLED = new ABProp(24051, "false");

    /**
     * A/B prop {@code threads_logging_v2_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: threads_logging_v2_enabled:[24100,"bool"]
     */
    public static final ABProp THREADS_LOGGING_V2_ENABLED = new ABProp(24100, "false");

    /**
     * A/B prop {@code ai_unified_response_imagine_receiver_web_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_unified_response_imagine_receiver_web_enabled:[24109,"bool"]
     */
    public static final ABProp AI_UNIFIED_RESPONSE_IMAGINE_RECEIVER_WEB_ENABLED = new ABProp(24109, "false");

    /**
     * A/B prop {@code web_history_sync_worker_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_history_sync_worker_enabled:[24147,"bool"]
     */
    public static final ABProp WEB_HISTORY_SYNC_WORKER_ENABLED = new ABProp(24147, "false");

    /**
     * A/B prop {@code enable_web_call_link} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_web_call_link:[24201,"bool"]
     */
    public static final ABProp ENABLE_WEB_CALL_LINK = new ABProp(24201, "false");

    /**
     * A/B prop {@code enable_mention_everyone_syncd_sender} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_mention_everyone_syncd_sender:[24244,"bool"]
     */
    public static final ABProp ENABLE_MENTION_EVERYONE_SYNCD_SENDER = new ABProp(24244, "false");

    /**
     * A/B prop {@code web_display_lid_contacts} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_display_lid_contacts:[24280,"bool"]
     */
    public static final ABProp WEB_DISPLAY_LID_CONTACTS = new ABProp(24280, "false");

    /**
     * A/B prop {@code web_log_capacity_override} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_log_capacity_override:[24363,"int"]
     */
    public static final ABProp WEB_LOG_CAPACITY_OVERRIDE = new ABProp(24363, "0");

    /**
     * A/B prop {@code wa_web_enable_follow_up_reply_icon} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_enable_follow_up_reply_icon:[24429,"bool"]
     */
    public static final ABProp WA_WEB_ENABLE_FOLLOW_UP_REPLY_ICON = new ABProp(24429, "false");

    /**
     * A/B prop {@code wa_web_anyone_can_link_m2} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_anyone_can_link_m2:[24432,"bool"]
     */
    public static final ABProp WA_WEB_ANYONE_CAN_LINK_M2 = new ABProp(24432, "false");

    /**
     * A/B prop {@code ai_unified_response_qpl_logging} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_unified_response_qpl_logging:[24484,"bool"]
     */
    public static final ABProp AI_UNIFIED_RESPONSE_QPL_LOGGING = new ABProp(24484, "false");

    /**
     * A/B prop {@code is_ai_mode_selector_visible} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: is_ai_mode_selector_visible:[24489,"bool"]
     */
    public static final ABProp IS_AI_MODE_SELECTOR_VISIBLE = new ABProp(24489, "false");

    /**
     * A/B prop {@code wa_web_global_search_prefix_based} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_global_search_prefix_based:[24559,"bool"]
     */
    public static final ABProp WA_WEB_GLOBAL_SEARCH_PREFIX_BASED = new ABProp(24559, "false");

    /**
     * A/B prop {@code wa_web_multi_ppl_typing_indicator_for_chatlist_groups_variant} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_multi_ppl_typing_indicator_for_chatlist_groups_variant:[24560,"int"]
     */
    public static final ABProp WA_WEB_MULTI_PPL_TYPING_INDICATOR_FOR_CHATLIST_GROUPS_VARIANT = new ABProp(24560, "0");

    /**
     * A/B prop {@code lists_smb_web_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: lists_smb_web_enabled:[24732,"bool"]
     */
    public static final ABProp LISTS_SMB_WEB_ENABLED = new ABProp(24732, "false");

    /**
     * A/B prop {@code rt_ghs_sender_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: rt_ghs_sender_enabled:[24741,"bool"]
     */
    public static final ABProp RT_GHS_SENDER_ENABLED = new ABProp(24741, "false");

    /**
     * A/B prop {@code rt_ghs_receiver_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: rt_ghs_receiver_enabled:[24742,"bool"]
     */
    public static final ABProp RT_GHS_RECEIVER_ENABLED = new ABProp(24742, "false");

    /**
     * A/B prop {@code biz_ai_consumer_tos_notice_iq_web} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: biz_ai_consumer_tos_notice_iq_web:[24754,"bool"]
     */
    public static final ABProp BIZ_AI_CONSUMER_TOS_NOTICE_IQ_WEB = new ABProp(24754, "false");

    /**
     * A/B prop {@code wa_web_contact_search_tokenized_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_contact_search_tokenized_enabled:[24773,"bool"]
     */
    public static final ABProp WA_WEB_CONTACT_SEARCH_TOKENIZED_ENABLED = new ABProp(24773, "false");

    /**
     * A/B prop {@code wa_web_status_comet_video_player_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_status_comet_video_player_enabled:[24791,"bool"]
     */
    public static final ABProp WA_WEB_STATUS_COMET_VIDEO_PLAYER_ENABLED = new ABProp(24791, "false");

    /**
     * A/B prop {@code aura_stickers_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: aura_stickers_enabled:[24800,"bool"]
     */
    public static final ABProp AURA_STICKERS_ENABLED = new ABProp(24800, "false");

    /**
     * A/B prop {@code aura_stickers_benefit_active} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: aura_stickers_benefit_active:[24801,"bool"]
     */
    public static final ABProp AURA_STICKERS_BENEFIT_ACTIVE = new ABProp(24801, "false");

    /**
     * A/B prop {@code web_request_missing_keys_for_removes} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_request_missing_keys_for_removes:[24838,"bool"]
     */
    public static final ABProp WEB_REQUEST_MISSING_KEYS_FOR_REMOVES = new ABProp(24838, "false");

    /**
     * A/B prop {@code enable_mention_everyone_receiver_web} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_mention_everyone_receiver_web:[24843,"bool"]
     */
    public static final ABProp ENABLE_MENTION_EVERYONE_RECEIVER_WEB = new ABProp(24843, "false");

    /**
     * A/B prop {@code enhanced_mention_suggestions_non_group_members_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enhanced_mention_suggestions_non_group_members_enabled:[24852,"bool"]
     */
    public static final ABProp ENHANCED_MENTION_SUGGESTIONS_NON_GROUP_MEMBERS_ENABLED = new ABProp(24852, "false");

    /**
     * A/B prop {@code cci_compliance_mm} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: cci_compliance_mm:[24853,"bool"]
     */
    public static final ABProp CCI_COMPLIANCE_MM = new ABProp(24853, "false");

    /**
     * A/B prop {@code web_bulk_add_contacts_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_bulk_add_contacts_enabled:[24875,"bool"]
     */
    public static final ABProp WEB_BULK_ADD_CONTACTS_ENABLED = new ABProp(24875, "false");

    /**
     * A/B prop {@code poll_end_time_receiving_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: poll_end_time_receiving_enabled:[24884,"bool"]
     */
    public static final ABProp POLL_END_TIME_RECEIVING_ENABLED = new ABProp(24884, "false");

    /**
     * A/B prop {@code poll_hide_voters_receiving_enabled} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: poll_hide_voters_receiving_enabled:[24885,"int"]
     */
    public static final ABProp POLL_HIDE_VOTERS_RECEIVING_ENABLED = new ABProp(24885, "0");

    /**
     * A/B prop {@code poll_creator_edit_receiving_version} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: poll_creator_edit_receiving_version:[24886,"int"]
     */
    public static final ABProp POLL_CREATOR_EDIT_RECEIVING_VERSION = new ABProp(24886, "0");

    /**
     * A/B prop {@code wa_web_video_comet_video_player_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_video_comet_video_player_enabled:[24905,"bool"]
     */
    public static final ABProp WA_WEB_VIDEO_COMET_VIDEO_PLAYER_ENABLED = new ABProp(24905, "false");

    /**
     * A/B prop {@code web_worker_adv_processing_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: web_worker_adv_processing_enabled:[24924,"bool"]
     */
    public static final ABProp WEB_WORKER_ADV_PROCESSING_ENABLED = new ABProp(24924, "false");

    /**
     * A/B prop {@code wa_web_canonical_ent_web_reg_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_canonical_ent_web_reg_enabled:[24925,"bool"]
     */
    public static final ABProp WA_WEB_CANONICAL_ENT_WEB_REG_ENABLED = new ABProp(24925, "false");

    /**
     * A/B prop {@code wa_web_anyone_can_link_m2_flood_limit} of integer type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_anyone_can_link_m2_flood_limit:[25009,"int"]
     */
    public static final ABProp WA_WEB_ANYONE_CAN_LINK_M2_FLOOD_LIMIT = new ABProp(25009, "10");

    /**
     * A/B prop {@code wa_web_status_first_upload_fix_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_status_first_upload_fix_enabled:[25015,"bool"]
     */
    public static final ABProp WA_WEB_STATUS_FIRST_UPLOAD_FIX_ENABLED = new ABProp(25015, "false");

    /**
     * A/B prop {@code mm_disclosure_learn_more_article_id} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: mm_disclosure_learn_more_article_id:[25021,"string"]
     */
    public static final ABProp MM_DISCLOSURE_LEARN_MORE_ARTICLE_ID = new ABProp(25021, "263784176043634");

    /**
     * A/B prop {@code channels_t_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: channels_t_enabled:[25078,"bool"]
     */
    public static final ABProp CHANNELS_T_ENABLED = new ABProp(25078, "false");

    /**
     * A/B prop {@code wa_web_enable_status_hq_thumbnail} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_enable_status_hq_thumbnail:[25079,"bool"]
     */
    public static final ABProp WA_WEB_ENABLE_STATUS_HQ_THUMBNAIL = new ABProp(25079, "false");

    /**
     * A/B prop {@code smb_core_biz_profile_edit_address} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: smb_core_biz_profile_edit_address:[25118,"bool"]
     */
    public static final ABProp SMB_CORE_BIZ_PROFILE_EDIT_ADDRESS = new ABProp(25118, "false");

    /**
     * A/B prop {@code ai_bot_integration_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_bot_integration_enabled:[25119,"bool"]
     */
    public static final ABProp AI_BOT_INTEGRATION_ENABLED = new ABProp(25119, "false");

    /**
     * A/B prop {@code enable_logging_qbm_incoming_message} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: enable_logging_qbm_incoming_message:[25149,"bool"]
     */
    public static final ABProp ENABLE_LOGGING_QBM_INCOMING_MESSAGE = new ABProp(25149, "false");

    /**
     * A/B prop {@code wa_web_status_viewer_side_poster_identifiers_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_status_viewer_side_poster_identifiers_enabled:[25151,"bool"]
     */
    public static final ABProp WA_WEB_STATUS_VIEWER_SIDE_POSTER_IDENTIFIERS_ENABLED = new ABProp(25151, "false");

    /**
     * A/B prop {@code ai_bot_integration_bot_profile} of string type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: ai_bot_integration_bot_profile:[25268,"string"]
     */
    public static final ABProp AI_BOT_INTEGRATION_BOT_PROFILE = new ABProp(25268, "");

    /**
     * A/B prop {@code wa_web_ur_imagine_video_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_ur_imagine_video_enabled:[25329,"bool"]
     */
    public static final ABProp WA_WEB_UR_IMAGINE_VIDEO_ENABLED = new ABProp(25329, "false");

    /**
     * A/B prop {@code wa_web_imagine_ur_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_imagine_ur_enabled:[25331,"bool"]
     */
    public static final ABProp WA_WEB_IMAGINE_UR_ENABLED = new ABProp(25331, "false");

    /**
     * A/B prop {@code wa_web_ur_bloks_enabled} of boolean type.
     *
     * <p>This constant was generated automatically by {@code tooling/web-ab-props-extractor}.
     *
     * @apiNote WAWebABPropsConfigs: wa_web_ur_bloks_enabled:[25332,"bool"]
     */
    public static final ABProp WA_WEB_UR_BLOKS_ENABLED = new ABProp(25332, "false");

    /**
     * Constructs a new {@code ABProp} definition.
     *
     * @throws NullPointerException if {@code defaultValue} is {@code null}
     */
    public ABProp {
        Objects.requireNonNull(defaultValue, "defaultValue cannot be null");
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
        return "ABProp[code=%d, defaultValue='%s']"
                .formatted(code, defaultValue);
    }
}
