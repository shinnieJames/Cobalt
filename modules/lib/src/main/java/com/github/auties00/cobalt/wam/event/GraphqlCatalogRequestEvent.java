package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamChannel;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.BusinessType;
import com.github.auties00.cobalt.wam.type.GraphqlCatalogEndpoint;
import com.github.auties00.cobalt.wam.type.GraphqlRequestResult;

import java.util.Optional;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebGraphqlCatalogRequestWamEvent")
@WamEvent(id = 3206, channel = WamChannel.PRIVATE, privateStatsId = 0)
public interface GraphqlCatalogRequestEvent extends WamEventSpec {
    @WamProperty(index = 4, type = WamType.STRING)
    Optional<String> businessJid();

    @WamProperty(index = 5, type = WamType.ENUM)
    Optional<BusinessType> businessType();

    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<GraphqlCatalogEndpoint> graphqlCatalogEndpoint();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt graphqlErrorCode();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<GraphqlRequestResult> graphqlRequestResult();
}
