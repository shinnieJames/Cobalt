package com.github.auties00.cobalt.util;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.wam.type.CtwaLabelType;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Holds the compile-time constants and pure lookup functions that WhatsApp
 * Business clients use to render and classify the built-in chat labels.
 *
 * <p>This class is a direct adaptation of the {@code WAWebLabelConstants}
 * JavaScript module: it exposes the two colour palettes (Android and iPhone)
 * that WhatsApp ships for the label picker, the integer identifiers of the
 * eight predefined labels ("New customer", "New order", and so on), the
 * display names WhatsApp assigns to those predefined identifiers, the
 * canonical subtype strings sent to the server, the maximum number of
 * characters a user may type into a label name, and the three pure mapping
 * helpers that convert between those representations.
 *
 * <p>The palette arrays are frozen in WhatsApp Web via {@code Object.freeze};
 * Cobalt exposes them as unmodifiable {@link List}s so the same immutability
 * guarantee is preserved. The predefined id and name maps are frozen objects
 * in WhatsApp Web; Cobalt flattens them into integer constants and
 * {@link String} constants because the names and ids are referenced by code
 * rather than by string key lookup.
 *
 * @implNote Adapts {@code WAWebLabelConstants}: four frozen tables
 *     (colour palettes, predefined ids, predefined label names, predefined
 *     label subtypes) plus three pure lookup functions
 *     ({@code mapLabelNameToPredefinedId},
 *     {@code mapCustomLabelSubtypeToCTWALabelType},
 *     {@code mapPredefinedIdToLabelName}) and the scalar
 *     {@code LABEL_NAME_MAX_LENGTH = 100}. The Cobalt helpers return
 *     {@link OptionalInt}/{@link Optional} instead of the
 *     {@code undefined}-or-value pattern the JavaScript source uses.
 */
@WhatsAppWebModule(moduleName = "WAWebLabelConstants")
public final class BusinessLabelConstants {
    /**
     * The ordered palette of twenty hex colour swatches the Android WhatsApp
     * client uses to paint chat labels.
     *
     * <p>The colour at index {@code i} is the colour associated with palette
     * slot {@code i}; a {@link com.github.auties00.cobalt.model.preference.Label}
     * stores an index into this palette rather than a concrete colour so that
     * the same numeric value always produces the same visual colour on Android.
     *
     * @implNote WAWebLabelConstants.ANDROID_LABEL_COLOR_PALETTE (local {@code e}):
     *     a frozen twenty-element hex string array. Cobalt exposes an
     *     unmodifiable {@link List} to preserve immutability.
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelConstants",
            exports = "ANDROID_LABEL_COLOR_PALETTE",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final List<String> ANDROID_LABEL_COLOR_PALETTE = List.of(
            "#FF9485", "#64C4FF", "#FFD429", "#DFAEF0", "#99B6C1",
            "#55CCB3", "#FF9DFF", "#D3A91D", "#6D7CCE", "#D7E752",
            "#00D0E2", "#FFC5C7", "#93CEAC", "#F74848", "#00A0F2",
            "#83E422", "#FFAF04", "#B5EBFF", "#9BA6FF", "#9368CF"
    );

    /**
     * The ordered palette of twenty hex colour swatches the iPhone WhatsApp
     * client uses to paint chat labels.
     *
     * <p>The colour at index {@code i} is the colour associated with palette
     * slot {@code i}; a {@link com.github.auties00.cobalt.model.preference.Label}
     * stores an index into this palette rather than a concrete colour so that
     * the same numeric value always produces the same visual colour on iPhone.
     *
     * @implNote WAWebLabelConstants.IPHONE_LABEL_COLOR_PALETTE (local {@code s}):
     *     a frozen twenty-element hex string array. Cobalt exposes an
     *     unmodifiable {@link List} to preserve immutability.
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelConstants",
            exports = "IPHONE_LABEL_COLOR_PALETTE",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final List<String> IPHONE_LABEL_COLOR_PALETTE = List.of(
            "#A62C71", "#90A841", "#C1A03F", "#792138", "#AE8774",
            "#F0B330", "#B6B327", "#C69FCC", "#8B6990", "#FF8A8C",
            "#54C265", "#FF7B6B", "#26C4DC", "#57C9FF", "#74676A",
            "#7E90A3", "#5696FF", "#6E257E", "#7ACBA5", "#243640"
    );

    /**
     * The predefined-id integer assigned to the "New customer" label.
     *
     * @implNote WAWebLabelConstants.PREDEFINED_LABEL_IDS.NEW_CUSTOMER = 1.
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelConstants",
            exports = "PREDEFINED_LABEL_IDS",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int PREDEFINED_LABEL_ID_NEW_CUSTOMER = 1;

    /**
     * The predefined-id integer assigned to the "New order" label.
     *
     * @implNote WAWebLabelConstants.PREDEFINED_LABEL_IDS.NEW_ORDER = 2.
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelConstants",
            exports = "PREDEFINED_LABEL_IDS",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int PREDEFINED_LABEL_ID_NEW_ORDER = 2;

    /**
     * The predefined-id integer assigned to the "Pending payment" label.
     *
     * @implNote WAWebLabelConstants.PREDEFINED_LABEL_IDS.PENDING_PAYMENT = 3.
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelConstants",
            exports = "PREDEFINED_LABEL_IDS",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int PREDEFINED_LABEL_ID_PENDING_PAYMENT = 3;

    /**
     * The predefined-id integer assigned to the "Paid" label.
     *
     * @implNote WAWebLabelConstants.PREDEFINED_LABEL_IDS.PAID = 4.
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelConstants",
            exports = "PREDEFINED_LABEL_IDS",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int PREDEFINED_LABEL_ID_PAID = 4;

    /**
     * The predefined-id integer assigned to the "Order complete" label.
     *
     * @implNote WAWebLabelConstants.PREDEFINED_LABEL_IDS.ORDER_COMPLETE = 5.
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelConstants",
            exports = "PREDEFINED_LABEL_IDS",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int PREDEFINED_LABEL_ID_ORDER_COMPLETE = 5;

    /**
     * The predefined-id integer assigned to the "Important" label.
     *
     * @implNote WAWebLabelConstants.PREDEFINED_LABEL_IDS.IMPORTANT = 6.
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelConstants",
            exports = "PREDEFINED_LABEL_IDS",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int PREDEFINED_LABEL_ID_IMPORTANT = 6;

    /**
     * The predefined-id integer assigned to the "Follow up" label.
     *
     * @implNote WAWebLabelConstants.PREDEFINED_LABEL_IDS.FOLLOW_UP = 7.
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelConstants",
            exports = "PREDEFINED_LABEL_IDS",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int PREDEFINED_LABEL_ID_FOLLOW_UP = 7;

    /**
     * The predefined-id integer assigned to the "Lead" label.
     *
     * @implNote WAWebLabelConstants.PREDEFINED_LABEL_IDS.LEAD = 8.
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelConstants",
            exports = "PREDEFINED_LABEL_IDS",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int PREDEFINED_LABEL_ID_LEAD = 8;

    /**
     * The predefined-id integer reserved for the "Delivery-Order: new order"
     * derived label, which {@link #mapPredefinedIdToLabelName(int)} collapses
     * back to {@link #PREDEFINED_LABEL_NAME_NEW_ORDER}.
     *
     * @implNote WAWebLabelConstants.PREDEFINED_LABEL_IDS.DO_NEW_ORDER = 9.
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelConstants",
            exports = "PREDEFINED_LABEL_IDS",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int PREDEFINED_LABEL_ID_DO_NEW_ORDER = 9;

    /**
     * The predefined-id integer reserved for the "Delivery-Order: lead"
     * derived label, which {@link #mapPredefinedIdToLabelName(int)} collapses
     * back to {@link #PREDEFINED_LABEL_NAME_LEAD}.
     *
     * @implNote WAWebLabelConstants.PREDEFINED_LABEL_IDS.DO_LEAD = 10.
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelConstants",
            exports = "PREDEFINED_LABEL_IDS",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int PREDEFINED_LABEL_ID_DO_LEAD = 10;

    /**
     * The user-visible display name of the "New customer" predefined label.
     *
     * @implNote WAWebLabelConstants.PREDEFINED_LABEL_NAMES.NEW_CUSTOMER = "New customer".
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelConstants",
            exports = "PREDEFINED_LABEL_NAMES",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String PREDEFINED_LABEL_NAME_NEW_CUSTOMER = "New customer";

    /**
     * The user-visible display name of the "New order" predefined label.
     *
     * @implNote WAWebLabelConstants.PREDEFINED_LABEL_NAMES.NEW_ORDER = "New order".
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelConstants",
            exports = "PREDEFINED_LABEL_NAMES",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String PREDEFINED_LABEL_NAME_NEW_ORDER = "New order";

    /**
     * The user-visible display name of the "Pending payment" predefined label.
     *
     * @implNote WAWebLabelConstants.PREDEFINED_LABEL_NAMES.PENDING_PAYMENT = "Pending payment".
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelConstants",
            exports = "PREDEFINED_LABEL_NAMES",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String PREDEFINED_LABEL_NAME_PENDING_PAYMENT = "Pending payment";

    /**
     * The user-visible display name of the "Paid" predefined label.
     *
     * @implNote WAWebLabelConstants.PREDEFINED_LABEL_NAMES.PAID = "Paid".
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelConstants",
            exports = "PREDEFINED_LABEL_NAMES",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String PREDEFINED_LABEL_NAME_PAID = "Paid";

    /**
     * The user-visible display name of the "Order complete" predefined label.
     *
     * @implNote WAWebLabelConstants.PREDEFINED_LABEL_NAMES.ORDER_COMPLETE = "Order complete".
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelConstants",
            exports = "PREDEFINED_LABEL_NAMES",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String PREDEFINED_LABEL_NAME_ORDER_COMPLETE = "Order complete";

    /**
     * The user-visible display name of the "Important" predefined label.
     *
     * @implNote WAWebLabelConstants.PREDEFINED_LABEL_NAMES.IMPORTANT = "Important".
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelConstants",
            exports = "PREDEFINED_LABEL_NAMES",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String PREDEFINED_LABEL_NAME_IMPORTANT = "Important";

    /**
     * The user-visible display name of the "Follow up" predefined label.
     *
     * @implNote WAWebLabelConstants.PREDEFINED_LABEL_NAMES.FOLLOW_UP = "Follow up".
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelConstants",
            exports = "PREDEFINED_LABEL_NAMES",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String PREDEFINED_LABEL_NAME_FOLLOW_UP = "Follow up";

    /**
     * The user-visible display name of the "Lead" predefined label.
     *
     * @implNote WAWebLabelConstants.PREDEFINED_LABEL_NAMES.LEAD = "Lead".
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelConstants",
            exports = "PREDEFINED_LABEL_NAMES",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final String PREDEFINED_LABEL_NAME_LEAD = "Lead";

    /**
     * The canonical subtype string WhatsApp transmits on the wire for the
     * "New customer" custom-label subtype.
     *
     * @implNote WAWebLabelConstants local {@code d.NEW_CUSTOMER = "new_customer"}
     *     (the unexported {@code CUSTOM_LABEL_SUBTYPE} frozen map).
     */
    static final String CUSTOM_LABEL_SUBTYPE_NEW_CUSTOMER = "new_customer";

    /**
     * The canonical subtype string WhatsApp transmits on the wire for the
     * "New order" custom-label subtype.
     *
     * @implNote WAWebLabelConstants local {@code d.NEW_ORDER = "new_order"}.
     */
    static final String CUSTOM_LABEL_SUBTYPE_NEW_ORDER = "new_order";

    /**
     * The canonical subtype string WhatsApp transmits on the wire for the
     * "Pending payment" custom-label subtype.
     *
     * @implNote WAWebLabelConstants local {@code d.PENDING_PAYMENT = "pending_payment"}.
     */
    static final String CUSTOM_LABEL_SUBTYPE_PENDING_PAYMENT = "pending_payment";

    /**
     * The canonical subtype string WhatsApp transmits on the wire for the
     * "Paid" custom-label subtype.
     *
     * @implNote WAWebLabelConstants local {@code d.PAID = "paid"}.
     */
    static final String CUSTOM_LABEL_SUBTYPE_PAID = "paid";

    /**
     * The canonical subtype string WhatsApp transmits on the wire for the
     * "Order complete" custom-label subtype.
     *
     * @implNote WAWebLabelConstants local {@code d.ORDER_COMPLETE = "order_complete"}.
     */
    static final String CUSTOM_LABEL_SUBTYPE_ORDER_COMPLETE = "order_complete";

    /**
     * The canonical subtype string WhatsApp transmits on the wire for the
     * "Important" custom-label subtype.
     *
     * @implNote WAWebLabelConstants local {@code d.IMPORTANT = "important"}.
     */
    static final String CUSTOM_LABEL_SUBTYPE_IMPORTANT = "important";

    /**
     * The canonical subtype string WhatsApp transmits on the wire for the
     * "Follow up" custom-label subtype.
     *
     * @implNote WAWebLabelConstants local {@code d.FOLLOW_UP = "follow_up"}.
     */
    static final String CUSTOM_LABEL_SUBTYPE_FOLLOW_UP = "follow_up";

    /**
     * The canonical subtype string WhatsApp transmits on the wire for the
     * "Lead" custom-label subtype.
     *
     * @implNote WAWebLabelConstants local {@code d.LEAD = "lead"}.
     */
    static final String CUSTOM_LABEL_SUBTYPE_LEAD = "lead";

    /**
     * The maximum number of characters a user may type into a label name.
     *
     * <p>WhatsApp enforces this limit client-side so that labels always fit on
     * the chat list card. Cobalt exposes the same ceiling so that application
     * code can validate user-entered names before building a
     * {@link com.github.auties00.cobalt.model.preference.Label}.
     *
     * @implNote WAWebLabelConstants.LABEL_NAME_MAX_LENGTH (local {@code f}) = 100.
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelConstants",
            exports = "LABEL_NAME_MAX_LENGTH",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static final int LABEL_NAME_MAX_LENGTH = 100;

    /**
     * Prevents instantiation of this utility class.
     */
    private BusinessLabelConstants() {
        throw new AssertionError("BusinessLabelConstants is not instantiable");
    }

    /**
     * Returns the predefined-label identifier associated with the given
     * user-visible label name, when that name matches one of the eight
     * {@code PREDEFINED_LABEL_NAMES} WhatsApp ships.
     *
     * <p>The lookup is case-sensitive: only the exact display strings
     * ({@code "New customer"}, {@code "New order"}, {@code "Pending payment"},
     * {@code "Paid"}, {@code "Order complete"}, {@code "Important"},
     * {@code "Follow up"}, {@code "Lead"}) match. All other inputs, including
     * {@code null}, return an empty {@link OptionalInt}.
     *
     * @param labelName the user-visible label name to resolve
     * @return the predefined identifier of the matching built-in label, or an
     *         empty {@link OptionalInt} when {@code labelName} is not a
     *         predefined display name
     *
     * @implNote WAWebLabelConstants.mapLabelNameToPredefinedId (local
     *     {@code m}): an eight-case {@code switch(t)} over {@code c} keyed by
     *     the display name that returns the matching {@code u} constant, or
     *     returns {@code undefined} on the default branch. Cobalt returns an
     *     empty {@link OptionalInt} for the {@code undefined} branch.
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelConstants",
            exports = "mapLabelNameToPredefinedId",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static OptionalInt mapLabelNameToPredefinedId(String labelName) {
        if (labelName == null) { // ADAPTED: JS switch on undefined trivially falls through to default
            return OptionalInt.empty();
        }
        return switch (labelName) { // WAWebLabelConstants.m: switch(t)
            case PREDEFINED_LABEL_NAME_NEW_CUSTOMER -> OptionalInt.of(PREDEFINED_LABEL_ID_NEW_CUSTOMER); // case c.NEW_CUSTOMER: return u.NEW_CUSTOMER
            case PREDEFINED_LABEL_NAME_NEW_ORDER -> OptionalInt.of(PREDEFINED_LABEL_ID_NEW_ORDER); // case c.NEW_ORDER: return u.NEW_ORDER
            case PREDEFINED_LABEL_NAME_PENDING_PAYMENT -> OptionalInt.of(PREDEFINED_LABEL_ID_PENDING_PAYMENT); // case c.PENDING_PAYMENT: return u.PENDING_PAYMENT
            case PREDEFINED_LABEL_NAME_PAID -> OptionalInt.of(PREDEFINED_LABEL_ID_PAID); // case c.PAID: return u.PAID
            case PREDEFINED_LABEL_NAME_ORDER_COMPLETE -> OptionalInt.of(PREDEFINED_LABEL_ID_ORDER_COMPLETE); // case c.ORDER_COMPLETE: return u.ORDER_COMPLETE
            case PREDEFINED_LABEL_NAME_IMPORTANT -> OptionalInt.of(PREDEFINED_LABEL_ID_IMPORTANT); // case c.IMPORTANT: return u.IMPORTANT
            case PREDEFINED_LABEL_NAME_FOLLOW_UP -> OptionalInt.of(PREDEFINED_LABEL_ID_FOLLOW_UP); // case c.FOLLOW_UP: return u.FOLLOW_UP
            case PREDEFINED_LABEL_NAME_LEAD -> OptionalInt.of(PREDEFINED_LABEL_ID_LEAD); // case c.LEAD: return u.LEAD
            default -> OptionalInt.empty(); // default: return (undefined)
        };
    }

    /**
     * Returns the {@link CtwaLabelType} that corresponds to the given custom
     * label subtype string WhatsApp uses on the Click-to-WhatsApp ads
     * telemetry channel.
     *
     * <p>The JavaScript source handles the known seven branches with explicit
     * cases and silently maps the default (including unknown strings) to
     * {@code CTWA_LABEL_TYPE.LEAD}. Cobalt preserves that "unknown becomes
     * LEAD" behaviour because the CTWA sink must always receive a valid value.
     *
     * @param subtype the custom-label subtype string, typically sourced from a
     *                server-side sync; may be {@code null}
     * @return the matching {@link CtwaLabelType}; {@link CtwaLabelType#LEAD}
     *         when {@code subtype} is {@code null} or does not match any of
     *         the recognised subtypes
     *
     * @implNote WAWebLabelConstants.mapCustomLabelSubtypeToCTWALabelType
     *     (local {@code p}): a seven-case {@code switch(t)} over {@code d}
     *     keyed by the wire subtype string that returns the matching
     *     {@code WAWebWamEnumCtwaLabelType.CTWA_LABEL_TYPE.*} constant; the
     *     default branch returns {@code CTWA_LABEL_TYPE.LEAD}.
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelConstants",
            exports = "mapCustomLabelSubtypeToCTWALabelType",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static CtwaLabelType mapCustomLabelSubtypeToCTWALabelType(String subtype) {
        if (subtype == null) { // ADAPTED: JS switch on undefined falls through to default which returns LEAD
            return CtwaLabelType.LEAD;
        }
        return switch (subtype) { // WAWebLabelConstants.p: switch(t)
            case CUSTOM_LABEL_SUBTYPE_NEW_CUSTOMER -> CtwaLabelType.NEW_CUSTOMER; // case d.NEW_CUSTOMER: return CTWA_LABEL_TYPE.NEW_CUSTOMER
            case CUSTOM_LABEL_SUBTYPE_NEW_ORDER -> CtwaLabelType.NEW_ORDER; // case d.NEW_ORDER: return CTWA_LABEL_TYPE.NEW_ORDER
            case CUSTOM_LABEL_SUBTYPE_PENDING_PAYMENT -> CtwaLabelType.PENDING_PAYMENT; // case d.PENDING_PAYMENT: return CTWA_LABEL_TYPE.PENDING_PAYMENT
            case CUSTOM_LABEL_SUBTYPE_PAID -> CtwaLabelType.PAID; // case d.PAID: return CTWA_LABEL_TYPE.PAID
            case CUSTOM_LABEL_SUBTYPE_ORDER_COMPLETE -> CtwaLabelType.ORDER_COMPLETE; // case d.ORDER_COMPLETE: return CTWA_LABEL_TYPE.ORDER_COMPLETE
            case CUSTOM_LABEL_SUBTYPE_IMPORTANT -> CtwaLabelType.IMPORTANT; // case d.IMPORTANT: return CTWA_LABEL_TYPE.IMPORTANT
            case CUSTOM_LABEL_SUBTYPE_FOLLOW_UP -> CtwaLabelType.FOLLOW_UP; // case d.FOLLOW_UP: return CTWA_LABEL_TYPE.FOLLOW_UP
            default -> CtwaLabelType.LEAD; // default: return CTWA_LABEL_TYPE.LEAD
        };
    }

    /**
     * Returns the user-visible display name for the given predefined label
     * identifier.
     *
     * <p>Accepts the eight primary predefined ids ({@link #PREDEFINED_LABEL_ID_NEW_CUSTOMER}
     * through {@link #PREDEFINED_LABEL_ID_LEAD}) as well as the two derived
     * Delivery-Order identifiers ({@link #PREDEFINED_LABEL_ID_DO_NEW_ORDER}
     * folds back to {@link #PREDEFINED_LABEL_NAME_NEW_ORDER} and
     * {@link #PREDEFINED_LABEL_ID_DO_LEAD} folds back to
     * {@link #PREDEFINED_LABEL_NAME_LEAD}). All other inputs return an empty
     * {@link Optional}.
     *
     * @param predefinedId the predefined label identifier to resolve
     * @return the display name associated with {@code predefinedId}, or an
     *         empty {@link Optional} when {@code predefinedId} is not a known
     *         predefined label id
     *
     * @implNote WAWebLabelConstants.mapPredefinedIdToLabelName (local
     *     {@code _}): a {@code switch(t)} over {@code u} that fold-through
     *     groups {@code NEW_ORDER / DO_NEW_ORDER} and {@code LEAD / DO_LEAD}
     *     onto the same display name and returns {@code undefined} on the
     *     default branch. Cobalt returns an empty {@link Optional} for the
     *     {@code undefined} branch.
     */
    @WhatsAppWebExport(moduleName = "WAWebLabelConstants",
            exports = "mapPredefinedIdToLabelName",
            adaptation = WhatsAppAdaptation.DIRECT)
    public static Optional<String> mapPredefinedIdToLabelName(int predefinedId) {
        return switch (predefinedId) { // WAWebLabelConstants._: switch(t)
            case PREDEFINED_LABEL_ID_NEW_CUSTOMER -> Optional.of(PREDEFINED_LABEL_NAME_NEW_CUSTOMER); // case u.NEW_CUSTOMER: return c.NEW_CUSTOMER
            case PREDEFINED_LABEL_ID_NEW_ORDER, PREDEFINED_LABEL_ID_DO_NEW_ORDER
                    -> Optional.of(PREDEFINED_LABEL_NAME_NEW_ORDER); // case u.NEW_ORDER: case u.DO_NEW_ORDER: return c.NEW_ORDER
            case PREDEFINED_LABEL_ID_PENDING_PAYMENT -> Optional.of(PREDEFINED_LABEL_NAME_PENDING_PAYMENT); // case u.PENDING_PAYMENT: return c.PENDING_PAYMENT
            case PREDEFINED_LABEL_ID_PAID -> Optional.of(PREDEFINED_LABEL_NAME_PAID); // case u.PAID: return c.PAID
            case PREDEFINED_LABEL_ID_ORDER_COMPLETE -> Optional.of(PREDEFINED_LABEL_NAME_ORDER_COMPLETE); // case u.ORDER_COMPLETE: return c.ORDER_COMPLETE
            case PREDEFINED_LABEL_ID_IMPORTANT -> Optional.of(PREDEFINED_LABEL_NAME_IMPORTANT); // case u.IMPORTANT: return c.IMPORTANT
            case PREDEFINED_LABEL_ID_FOLLOW_UP -> Optional.of(PREDEFINED_LABEL_NAME_FOLLOW_UP); // case u.FOLLOW_UP: return c.FOLLOW_UP
            case PREDEFINED_LABEL_ID_LEAD, PREDEFINED_LABEL_ID_DO_LEAD
                    -> Optional.of(PREDEFINED_LABEL_NAME_LEAD); // case u.LEAD: case u.DO_LEAD: return c.LEAD
            default -> Optional.empty(); // default: return (undefined)
        };
    }
}
