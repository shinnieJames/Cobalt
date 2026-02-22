package com.github.auties00.cobalt.model.message;

/**
 * A model class that represents an empty message. Used to prevent NPEs from empty messages sent by
 * Whatsapp. Consider this a stub type.
 */
public final class EmptyMessage implements Message {
    static final EmptyMessage INSTANCE = new EmptyMessage();

    EmptyMessage() {

    }
}