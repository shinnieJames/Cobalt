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
 * USync {@code devices} protocol descriptor.
 *
 * @apiNote
 * Asks the relay for each peer's device list and the signed key-index
 * envelope; used by every device sync flow (see
 * {@code WAWebAdvSyncDeviceListApi.syncDeviceList} and
 * {@code WAWebContactSyncApi}). Pair each {@link UsyncUser} with the locally
 * cached device hash through
 * {@link UsyncUser#withDeviceHash(String)} so the relay can return an omit
 * response when the cache is still in sync.
 */
@WhatsAppWebModule(moduleName = "WAWebUsyncDevice")
public final class UsyncDeviceProtocol implements UsyncProtocol {
    /**
     * Wire literal for the protocol tag name.
     */
    public static final String NAME = "devices";

    /**
     * Wire-protocol version emitted on the {@code version} attribute of the
     * {@code <devices>} query element.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncDevice",
            exports = "USyncDeviceProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public static final int PROTOCOL_VERSION = 2;

    /**
     * Builds a default device-protocol descriptor.
     *
     * @apiNote
     * The descriptor is stateless; per-user state lives on each
     * {@link UsyncUser}.
     */
    @WhatsAppWebExport(moduleName = "WAWebUsyncDevice",
            exports = "USyncDeviceProtocol", adaptation = WhatsAppAdaptation.DIRECT)
    public UsyncDeviceProtocol() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncDevice",
            exports = "USyncDeviceProtocol.getName", adaptation = WhatsAppAdaptation.DIRECT)
    public String name() {
        return NAME;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation always emits {@code version="2"} on the
     * {@code <devices>} element, matching the {@code e=2} constant the JS
     * module ships with.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebUsyncDevice",
            exports = "USyncDeviceProtocol.getQueryElement", adaptation = WhatsAppAdaptation.DIRECT)
    public Node buildQueryElement() {
        return new NodeBuilder()
                .description(NAME)
                .attribute("version", String.valueOf(PROTOCOL_VERSION))
                .build();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation skips the per-user element when none of
     * {@code device_hash}, {@code ts}, or {@code expected_ts} is populated,
     * matching the JS {@code null} return in {@code USyncDeviceProtocol.getUserElement}.
     * The relay then assumes the local cache is empty and ships the full
     * device list back.
     */
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

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation reads both the optional {@code <key-index-list>}
     * (signed key-index envelope, with timestamp and optional
     * {@code expected_ts}) and the optional {@code <device-list>}
     * (per-device id, key-index, hosted flag), matching the JS
     * {@code deviceParser}. The hosted-device flag is parsed unconditionally;
     * the JS {@code WAWebBizCoexGatingUtils.bizHostedDevicesEnabled} gate
     * happens server-side, so an unwanted {@code true} is impossible.
     */
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

        var keyIndex = child.getChild("key-index-list").map(node -> {
            var timestamp = Instant.ofEpochSecond(node.getRequiredAttributeAsLong("ts"));
            var expected = node.getAttributeAsLong("expected_ts").stream()
                    .mapToObj(Instant::ofEpochSecond).findFirst().orElse(null);
            var signed = node.toContentBytes().orElse(null);
            return new DeviceResult.KeyIndex(timestamp, signed, expected);
        }).orElse(null);

        var devices = child.getChild("device-list")
                .map(list -> {
                    var out = new ArrayList<DeviceResult.Device>();
                    list.streamChildren("device").forEach(d -> {
                        var id = d.getRequiredAttributeAsInt("id");
                        var ki = d.getAttributeAsInt("key-index", null);
                        var hosted = d.getAttributeAsBool("is_hosted", false);
                        out.add(new DeviceResult.Device(id, ki, hosted));
                    });
                    return List.copyOf(out);
                })
                .orElse(List.of());

        return new DeviceResult(devices, keyIndex);
    }
}
