package com.github.auties00.cobalt.model.message.interactive;

public sealed interface InteractiveMessageSpec permits InteractiveMessage.ShopMessage, InteractiveMessage.CollectionMessage, InteractiveMessage.NativeFlowMessage, InteractiveMessage.CarouselMessage {
}
