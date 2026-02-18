package com.github.auties00.cobalt.model.device.info;

import com.github.auties00.cobalt.model.jid.Jid;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Report of changes between two device lists.
 *
 */
@ProtobufMessage
public final class DeviceChanges {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    final Set<Jid> addedDevices;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    final Set<Jid> removedDevices;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    final Set<Jid> identityChangedDevices;

    DeviceChanges(Set<Jid> addedDevices, Set<Jid> removedDevices, Set<Jid> identityChangedDevices) {
        this.addedDevices = addedDevices;
        this.removedDevices = removedDevices;
        this.identityChangedDevices = identityChangedDevices;
    }

    public boolean hasChanges() {
        return !addedDevices.isEmpty() || !removedDevices.isEmpty() || !identityChangedDevices.isEmpty();
    }

    public boolean hasIdentityChanges() {
        return !identityChangedDevices.isEmpty();
    }

    public Set<Jid> addedDevices() {
        return Collections.unmodifiableSet(addedDevices);
    }

    public Set<Jid> removedDevices() {
        return Collections.unmodifiableSet(removedDevices);
    }

    public Set<Jid> identityChangedDevices() {
        return Collections.unmodifiableSet(identityChangedDevices);
    }

    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof DeviceChanges that
               && Objects.equals(addedDevices, that.addedDevices)
               && Objects.equals(removedDevices, that.removedDevices)
               && Objects.equals(identityChangedDevices, that.identityChangedDevices);
    }

    @Override
    public int hashCode() {
        return Objects.hash(addedDevices, removedDevices, identityChangedDevices);
    }

    @Override
    public String toString() {
        return "DeviceChanges[" +
               "addedDevices=" + addedDevices + ", " +
               "removedDevices=" + removedDevices + ", " +
               "identityChangedDevices=" + identityChangedDevices + ']';
    }

}
