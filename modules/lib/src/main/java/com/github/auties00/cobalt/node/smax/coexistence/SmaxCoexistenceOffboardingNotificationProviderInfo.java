package com.github.auties00.cobalt.node.smax.coexistence;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * Projection of the {@code <provider_info/>} child carried by a
 * coexistence notification's payload child.
 */
@WhatsAppWebModule(moduleName = "WASmaxInCoexistenceProviderInfoMixin")
public final class SmaxCoexistenceOffboardingNotificationProviderInfo {
    /**
     * The optional logo-URL content bytes.
     */
    private final byte[] logoUrl;

    /**
     * The optional name content bytes.
     */
    private final byte[] name;

    /**
     * The optional provider id (parsed from {@code <id/>} content
     * as a base-10 integer).
     */
    private final Integer id;

    /**
     * Constructs a new provider-info projection.
     *
     * @param logoUrl the optional logo-URL bytes. May be {@code null}
     * @param name    the optional name bytes. May be {@code null}
     * @param id      the optional provider id. May be {@code null}
     */
    public SmaxCoexistenceOffboardingNotificationProviderInfo(byte[] logoUrl, byte[] name, Integer id) {
        this.logoUrl = logoUrl;
        this.name = name;
        this.id = id;
    }

    /**
     * Returns the optional logo-URL bytes.
     *
     * @return an {@link Optional} carrying the bytes
     */
    public Optional<byte[]> logoUrl() {
        return Optional.ofNullable(logoUrl);
    }

    /**
     * Returns the optional name bytes.
     *
     * @return an {@link Optional} carrying the bytes
     */
    public Optional<byte[]> name() {
        return Optional.ofNullable(name);
    }

    /**
     * Returns the optional provider id.
     *
     * @return an {@link Optional} carrying the id
     */
    public Optional<Integer> id() {
        return Optional.ofNullable(id);
    }

    /**
     * Tries to parse a {@code <provider_info/>} child from the given
     * parent node.
     *
     * @param parent the parent node carrying the {@code <provider_info/>}
     *               child. Never {@code null}
     * @return an {@link Optional} carrying the parsed projection, or
     *         empty when the child is missing or when {@code <id/>}
     *         content cannot be parsed as a base-10 integer
     * @throws NullPointerException if {@code parent} is {@code null}
     */
    @WhatsAppWebExport(moduleName = "WASmaxInCoexistenceProviderInfoMixin",
            exports = {
                    "parseProviderInfoMixin",
                    "parseProviderInfoProviderInfoLogoUrl",
                    "parseProviderInfoProviderInfoName",
                    "parseProviderInfoProviderInfoId"
            },
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<SmaxCoexistenceOffboardingNotificationProviderInfo> of(Node parent) {
        Objects.requireNonNull(parent, "parent cannot be null");
        // WASmaxInCoexistenceProviderInfoMixin.parseProviderInfoMixin: flattenedChildWithTag(t, "provider_info")
        var providerInfoNode = parent.getChild("provider_info").orElse(null);
        if (providerInfoNode == null) {
            return Optional.empty();
        }
        // WASmaxInCoexistenceProviderInfoMixin.parseProviderInfoProviderInfoLogoUrl: assertTag("logo_url") + contentBytes
        var logoUrl = providerInfoNode.getChild("logo_url")
                .flatMap(Node::toContentBytes)
                .orElse(null);
        // WASmaxInCoexistenceProviderInfoMixin.parseProviderInfoProviderInfoName: assertTag("name") + contentBytes
        var name = providerInfoNode.getChild("name")
                .flatMap(Node::toContentBytes)
                .orElse(null);
        // WASmaxInCoexistenceProviderInfoMixin.parseProviderInfoProviderInfoId: assertTag("id") + contentInt
        var idBytes = providerInfoNode.getChild("id")
                .flatMap(Node::toContentString)
                .orElse(null);
        Integer id = null;
        if (idBytes != null) {
            try {
                id = Integer.parseInt(idBytes);
            } catch (NumberFormatException _) {
                // ADAPTED: WASmaxParseUtils.contentInt: parseInt(e,10) NaN -> error;
                // here a malformed id collapses the whole parse to Optional.empty().
                return Optional.empty();
            }
        }
        return Optional.of(new SmaxCoexistenceOffboardingNotificationProviderInfo(logoUrl, name, id));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxCoexistenceOffboardingNotificationProviderInfo) obj;
        return Arrays.equals(this.logoUrl, that.logoUrl)
                && Arrays.equals(this.name, that.name)
                && Objects.equals(this.id, that.id);
    }

    @Override
    public int hashCode() {
        var result = Objects.hash(id);
        result = 31 * result + Arrays.hashCode(logoUrl);
        result = 31 * result + Arrays.hashCode(name);
        return result;
    }

    @Override
    public String toString() {
        return "SmaxCoexistenceOffboardingNotificationProviderInfo[logoUrl=" + Arrays.toString(logoUrl)
                + ", name=" + Arrays.toString(name)
                + ", id=" + id + ']';
    }
}
