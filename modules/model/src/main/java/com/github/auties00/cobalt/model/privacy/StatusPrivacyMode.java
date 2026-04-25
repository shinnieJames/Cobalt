package com.github.auties00.cobalt.model.privacy;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

/**
 * Enumerates the distribution modes that can be configured for the user's
 * WhatsApp Status updates.
 *
 * <p>Each constant mirrors one of the three values declared by
 * {@code WAWebUserPrefsStatusType.StatusPrivacySettingType}
 * ({@code Contact:"contact"}, {@code AllowList:"allow-list"},
 * {@code DenyList:"deny-list"}). These WA Web constants are the UserPrefs
 * persistence keys used to track which Status privacy configuration the user
 * has selected; they are referenced (for example) by
 * {@code WAWebStatusPrivacySettingSync.applyMutations} when translating the
 * lower-level protobuf {@code StatusDistributionMode} into the UserPrefs
 * representation, and by {@code WAWebStatusPrivacySettingAction} /
 * {@code WAWebDebugStatus} as the {@code setting} argument to
 * {@code WAWebStatusSetAndSyncPrivacy.setAndSyncStatusPrivacy}.
 *
 * <p>Cobalt collapses the WA Web dual nomenclature — UserPrefs keys
 * ({@code "contact"} / {@code "allow-list"} / {@code "deny-list"}) versus IQ
 * attribute values ({@code "contacts"} / {@code "contact_whitelist"} /
 * {@code "contact_blacklist"}) — into a single caller-facing enum that maps
 * onto a combination of {@link PrivacySettingType#STATUS} and
 * {@link PrivacySettingValue}. The lower-level protobuf enum
 * {@code StatusPrivacyAction.StatusDistributionMode} is still used on the
 * sync wire (it carries additional {@code CLOSE_FRIENDS} and
 * {@code CUSTOM_LIST} values that are not representable at the UserPrefs
 * level).
 *
 * @implNote WAWebUserPrefsStatusType.StatusPrivacySettingType — the WA Web
 *           module defines a single export {@code StatusPrivacySettingType}
 *           built from {@code $InternalEnum({Contact:"contact",
 *           AllowList:"allow-list", DenyList:"deny-list"})}. Cobalt adapts
 *           this string-keyed enum to a Java enum whose constants share the
 *           same enumerated domain but serialize to the wire using the
 *           {@link PrivacySettingValue} identifiers.
 */
@WhatsAppWebModule(moduleName = "WAWebUserPrefsStatusType")
public enum StatusPrivacyMode {
    /**
     * Shares status updates with every contact in the address book.
     *
     * <p>Maps to {@link PrivacySettingValue#CONTACTS} with an empty jid list.
     * On the WA Web side this corresponds to the UserPrefs key value
     * {@code StatusPrivacySettingType.Contact} ({@code "contact"}).
     *
     * @implNote WAWebUserPrefsStatusType.StatusPrivacySettingType.Contact
     *           ({@code "contact"}) — the UserPrefs persistence key for the
     *           "share with all contacts" Status privacy setting. Cobalt
     *           serializes via {@link PrivacySettingValue#CONTACTS}
     *           ({@code "contacts"}).
     */
    @WhatsAppWebExport(moduleName = "WAWebUserPrefsStatusType", exports = "StatusPrivacySettingType", adaptation = WhatsAppAdaptation.ADAPTED)
    CONTACTS,

    /**
     * Shares status updates with every contact except for an explicit
     * blocklist of JIDs.
     *
     * <p>Maps to {@link PrivacySettingValue#CONTACTS_EXCEPT} with the
     * blocklist carried in {@link PrivacySettingEntry#excluded()}. On the WA
     * Web side this corresponds to the UserPrefs key value
     * {@code StatusPrivacySettingType.DenyList} ({@code "deny-list"}).
     *
     * @implNote WAWebUserPrefsStatusType.StatusPrivacySettingType.DenyList
     *           ({@code "deny-list"}) — the UserPrefs persistence key for the
     *           "share with all contacts except blocklist" Status privacy
     *           setting. Cobalt serializes via
     *           {@link PrivacySettingValue#CONTACTS_EXCEPT}
     *           ({@code "contact_blacklist"}).
     */
    @WhatsAppWebExport(moduleName = "WAWebUserPrefsStatusType", exports = "StatusPrivacySettingType", adaptation = WhatsAppAdaptation.ADAPTED)
    CONTACTS_EXCEPT,

    /**
     * Shares status updates with an explicit allowlist of JIDs and hides the
     * status from everyone else.
     *
     * <p>Maps to {@link PrivacySettingValue#CONTACTS_ONLY} with the allowlist
     * carried in {@link PrivacySettingEntry#excluded()}. On the WA Web side
     * this corresponds to the UserPrefs key value
     * {@code StatusPrivacySettingType.AllowList} ({@code "allow-list"}).
     *
     * @implNote WAWebUserPrefsStatusType.StatusPrivacySettingType.AllowList
     *           ({@code "allow-list"}) — the UserPrefs persistence key for
     *           the "share only with allowlist" Status privacy setting.
     *           Cobalt serializes via
     *           {@link PrivacySettingValue#CONTACTS_ONLY}
     *           ({@code "contact_whitelist"}).
     */
    @WhatsAppWebExport(moduleName = "WAWebUserPrefsStatusType", exports = "StatusPrivacySettingType", adaptation = WhatsAppAdaptation.ADAPTED)
    WHITELIST
}
