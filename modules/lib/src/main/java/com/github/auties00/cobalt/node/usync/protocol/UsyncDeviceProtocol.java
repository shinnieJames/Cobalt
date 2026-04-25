package com.github.auties00.cobalt.node.usync.protocol;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import com.github.auties00.cobalt.node.NodeBuilder;
import com.github.auties00.cobalt.node.usync.UsyncProtocol;
import com.github.auties00.cobalt.node.usync.UsyncProtocolResult;
import com.github.auties00.cobalt.node.usync.UsyncUser;
import com.github.auties00.cobalt.node.usync.result.DeviceResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * USync {@code devices} protocol.
 *
 * @implNote WAWebUsyncDevice.USyncDeviceProtocol.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncDevice")
public final class UsyncDeviceProtocol implements UsyncProtocol {
    /** Wire literal for the protocol tag name. */
    public static final String NAME = "devices";

    /**
     * Wire-protocol version emitted on the {@code version} attribute of
     * the {@code <devices>} query element.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncDevice",
            exports = "USyncDeviceProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public static final int PROTOCOL_VERSION = 2;

    /**
     * Constructs a default device-protocol descriptor.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncDevice",
            exports = "USyncDeviceProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncDeviceProtocol() {
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncDevice",
            exports = "USyncDeviceProtocol.getName", adaptation = WhatsAppAdaptation.DIRECT)
    public String name() {
        return NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncDevice",
            exports = "USyncDeviceProtocol.getQueryElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Node buildQueryElement() {
        return new NodeBuilder()
                .description(NAME)
                .attribute("version", String.valueOf(PROTOCOL_VERSION))
                .build();
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncDevice",
            exports = "USyncDeviceProtocol.getUserElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Optional<Node> buildUserElement(UsyncUser user) {
        var hash = user.deviceHash();
        var timestamp = user.timestamp();
        var expectedTimestamp = user.expectedTimestamp();
        if (hash.isEmpty() && timestamp.isEmpty() && expectedTimestamp.isEmpty()) {
            return Optional.empty();
        }
        var builder = new NodeBuilder().description(NAME);
        hash.ifPresent(h -> builder.attribute("device_hash", h));
        timestamp.ifPresent(t -> builder.attribute("ts", String.valueOf(t.getEpochSecond())));
        expectedTimestamp.ifPresent(t -> builder.attribute("expected_ts", String.valueOf(t.getEpochSecond())));
        return Optional.of(builder.build());
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncDevice",
            exports = "deviceParser", adaptation = WhatsAppAdaptation.ADAPTED)
    public UsyncProtocolResult parseUserResult(Node child) {
        if (!child.hasDescription(NAME)) {
            throw new IllegalStateException("expected <" + NAME + ">, got <" + child.description() + ">");
        }
        var error = UsyncContactProtocol.parseError(child);
        if (error.isPresent()) {
            return error.get();
        }

        DeviceResult.KeyIndex keyIndex = child.getChild("key-index-list").map(node -> {
            var timestamp = Instant.ofEpochSecond(node.getRequiredAttributeAsLong("ts"));
            var expected = node.getAttributeAsLong("expected_ts").stream()
                    .mapToObj(Instant::ofEpochSecond).findFirst().orElse(null);
            byte[] signed = node.toContentBytes().orElse(null);
            return new DeviceResult.KeyIndex(timestamp, signed, expected);
        }).orElse(null);

        List<DeviceResult.Device> devices = child.getChild("device-list")
                .map(list -> {
                    var out = new ArrayList<DeviceResult.Device>();
                    list.streamChildren("device").forEach(d -> {
                        int id = d.getRequiredAttributeAsInt("id");
                        var ki = d.getAttributeAsInt("key-index").stream().boxed().findFirst().orElse(null);
                        var hosted = d.getAttributeAsBool("is_hosted", false);
                        out.add(new DeviceResult.Device(id, ki, hosted));
                    });
                    return List.copyOf(out);
                })
                .orElse(List.of());

        return new DeviceResult(devices, keyIndex);
    }
}
