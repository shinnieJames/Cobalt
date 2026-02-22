package com.github.auties00.cobalt.model.message.interactive;

public sealed interface InteractiveMessageContent permits InteractiveMessage.ShopMessage, InteractiveMessage.CollectionMessage, InteractiveMessage.NativeFlowMessage, InteractiveMessage.CarouselMessage {
}
