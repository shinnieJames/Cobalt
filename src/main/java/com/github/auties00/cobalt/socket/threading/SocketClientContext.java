package com.github.auties00.cobalt.socket.threading;

import com.github.auties00.cobalt.socket.SocketClientLayer;
import com.github.auties00.cobalt.socket.transport.SocketClientTransportLayerContext;

import java.util.*;

/**
 * Per-connection context attached to a {@link java.nio.channels.SelectionKey}
 * as its attachment.
 *
 * <p>This context aggregates the transport-level state and a map of
 * per-layer processing contexts.  The layer contexts are inserted in
 * bottom-to-top order during stack construction, so that iteration
 * over {@link #layerContexts()} yields contexts from the bottommost
 * processing layer to the topmost.
 *
 * <p>The {@link #transportContext} is stored separately because it
 * does not implement {@link SocketClientLayerContext} — the transport
 * layer is the byte source/sink, not a processor.
 */
public final class SocketClientContext {
    /**
     * Transport-level state: connection lifecycle, pending writes.
     */
    private final SocketClientTransportLayerContext transportContext;

    /**
     * Layer processing contexts, keyed by layer class, in bottom-to-top
     * insertion order.
     */
    private final SequencedMap<Class<? extends SocketClientLayer>, SocketClientLayerContext> layerContextMap;

    /**
     * Creates a context with the given transport context.
     *
     * @param transportContext the transport-level state
     */
    public SocketClientContext(SocketClientTransportLayerContext transportContext) {
        this.transportContext = Objects.requireNonNull(transportContext);
        this.layerContextMap = new LinkedHashMap<>();
    }

    /**
     * Returns the transport-level context.
     *
     * @return the transport context, never {@code null}
     */
    public SocketClientTransportLayerContext transportContext() {
        return transportContext;
    }

    /**
     * Returns all layer processing contexts in bottom-to-top order.
     *
     * @return an unmodifiable sequenced collection of layer contexts
     */
    public SequencedCollection<? extends SocketClientLayerContext> layerContexts() {
        return Collections.unmodifiableSequencedCollection(layerContextMap.sequencedValues());
    }

    /**
     * Returns the bottommost processing layer context.
     *
     * <p>This is the first context in the chain — the one that the
     * selector reads bytes into and calls {@code processInbound()} on.
     *
     * @return the bottom layer context, or {@code null} if no layers
     *         are registered
     */
    public SocketClientLayerContext bottomProcessingContext() {
        return layerContextMap.isEmpty() ? null : layerContextMap.firstEntry().getValue();
    }

    /**
     * Retrieves a layer context by its layer class.
     *
     * @param <T>   the expected context type
     * @param clazz the layer class
     * @return an optional containing the context, or empty if not present
     */
    @SuppressWarnings("unchecked")
    public <T extends SocketClientLayerContext> Optional<T> getLayerContext(Class<? extends SocketClientLayer> clazz) {
        var result = layerContextMap.get(clazz);
        if (result == null) {
            return Optional.empty();
        }

        try {
            return Optional.of((T) result);
        } catch (ClassCastException e) {
            return Optional.empty();
        }
    }

    /**
     * Registers a layer context if one is not already registered for the
     * given layer class.
     *
     * <p>Contexts should be added in bottom-to-top order to ensure the
     * correct processing chain.
     *
     * @param clazz        the layer class
     * @param layerContext the context to register
     * @return {@code true} if the context was registered, {@code false}
     *         if one already existed for the given class
     */
    public boolean createLayerContext(Class<? extends SocketClientLayer> clazz, SocketClientLayerContext layerContext) {
        return layerContextMap.putIfAbsent(clazz, layerContext) == null;
    }
}
