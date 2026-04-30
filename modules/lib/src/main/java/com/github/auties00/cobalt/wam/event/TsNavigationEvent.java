package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.EntryPointConversationInitiated;
import com.github.auties00.cobalt.wam.type.ProductArea;
import com.github.auties00.cobalt.wam.type.RadioType;
import com.github.auties00.cobalt.wam.type.ReferrerAction;
import com.github.auties00.cobalt.wam.type.ThreadType;
import com.github.auties00.cobalt.wam.type.TsSurface;
import com.github.auties00.cobalt.wam.type.TypeOfGroupEnum;

import java.util.Optional;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebTsNavigationWamEvent")
@WamEvent(id = 4334)
public interface TsNavigationEvent extends WamEventSpec {
    @WamProperty(index = 39, type = WamType.STRING)
    Optional<String> aiSessionId();

    @WamProperty(index = 38, type = WamType.INTEGER)
    OptionalInt canonicalEntLastValidationTsMs();

    @WamProperty(index = 19, type = WamType.STRING)
    Optional<String> cid();

    @WamProperty(index = 23, type = WamType.ENUM)
    Optional<EntryPointConversationInitiated> entryPointConversationInitiated();

    @WamProperty(index = 24, type = WamType.STRING)
    Optional<String> entryPointConversionApp();

    @WamProperty(index = 25, type = WamType.STRING)
    Optional<String> entryPointConversionSource();

    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt groupSize();

    @WamProperty(index = 36, type = WamType.BOOLEAN)
    Optional<Boolean> isCanonicalEntPresent();

    @WamProperty(index = 29, type = WamType.BOOLEAN)
    Optional<Boolean> isCatalogVisible();

    @WamProperty(index = 26, type = WamType.BOOLEAN)
    Optional<Boolean> isCloudapi();

    @WamProperty(index = 30, type = WamType.BOOLEAN)
    Optional<Boolean> isMetaAiThread();

    @WamProperty(index = 27, type = WamType.BOOLEAN)
    Optional<Boolean> isOnpremises();

    @WamProperty(index = 28, type = WamType.BOOLEAN)
    Optional<Boolean> isSmb();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<TsSurface> navigationDestination();

    @WamProperty(index = 34, type = WamType.ENUM)
    Optional<ProductArea> navigationDestinationProductArea();

    @WamProperty(index = 35, type = WamType.STRING)
    Optional<String> navigationDestinationViewName();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<TsSurface> navigationSource();

    @WamProperty(index = 43, type = WamType.INTEGER)
    OptionalInt networkIsWifiCanonicalOpNumber();

    @WamProperty(index = 41, type = WamType.INTEGER)
    OptionalInt networkIsWifiOpNumber();

    @WamProperty(index = 44, type = WamType.INTEGER)
    OptionalInt networkRadioTypeCanonicalOpNumber();

    @WamProperty(index = 40, type = WamType.ENUM)
    Optional<RadioType> networkRadioTypeEventLevel();

    @WamProperty(index = 42, type = WamType.INTEGER)
    OptionalInt networkRadioTypeOpNumber();

    @WamProperty(index = 31, type = WamType.ENUM)
    Optional<ReferrerAction> referrerAction();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt relativeTimestampMs();

    @WamProperty(index = 20, type = WamType.STRING)
    Optional<String> smbCatalogBusinessVertical();

    @WamProperty(index = 21, type = WamType.BOOLEAN)
    Optional<Boolean> smbCatalogIsCatalogVisible();

    @WamProperty(index = 22, type = WamType.BOOLEAN)
    Optional<Boolean> smbCatalogIsToggleCart();

    @WamProperty(index = 7, type = WamType.ENUM)
    Optional<ThreadType> threadType();

    @WamProperty(index = 37, type = WamType.INTEGER)
    OptionalInt traceIdInt();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt tsSessionId();

    @WamProperty(index = 32, type = WamType.INTEGER)
    OptionalInt tsTimestampMs();

    @WamProperty(index = 6, type = WamType.ENUM)
    Optional<TypeOfGroupEnum> typeOfGroup();

    @WamProperty(index = 33, type = WamType.STRING)
    Optional<String> unifiedSessionId();
}
