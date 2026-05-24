package com.github.auties00.cobalt.node.smax.coexistence;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code <provider_info/>} sub-child carried by a coexistence
 * notification.
 *
 * @apiNote
 * Identifies the third-party provider behind a hosted onboarding or
 * offboarding event ({@code AI from Meta}, the
 * {@code business_platform} surface, automation providers); embedders
 * surface the logo and display name when notifying users that the
 * coexistence link has been established or torn down.
 */
@WhatsAppWebModule(moduleName = "WASmaxInCoexistenceProviderInfoMixin")
public final class SmaxCoexistenceOffboardingNotificationProviderInfo {
    /**
     * The optional logo-URL bytes for the provider.
     *
     * @apiNote
     * Empty when the relay did not include the {@code <logo_url/>}
     * child; otherwise the raw bytes of the URL the embedder loads
     * into the notification.
     */
    private final byte[] logoUrl;

    /**
     * The optional human-readable name bytes for the provider.
     *
     * @apiNote
     * Empty when the relay omitted the {@code <name/>} child;
     * otherwise the raw bytes of the provider's display name.
     */
    private final byte[] name;

    /**
     * The optional provider id.
     *
     * @apiNote
     * Empty when the relay omitted the {@code <id/>} child or when the
     * content cannot be parsed as a base-10 integer; embedders use the
     * value as a stable provider key when cross-referencing the
     * coexistence link with their own records.
     */
    private final Integer id;

    /**
     * Constructs a new provider-info projection.
     *
     * @apiNote
     * Built by {@link #of(Node)} from the parsed children of the
     * {@code <provider_info/>} node; embedders rarely instantiate this
     * class directly outside tests.
     *
     * @param logoUrl the optional logo-URL bytes; may be {@code null}
     * @param name    the optional name bytes; may be {@code null}
     * @param id      the optional provider id; may be {@code null}
     */
    public SmaxCoexistenceOffboardingNotificationProviderInfo(byte[] logoUrl, byte[] name, Integer id) {
        this.logoUrl = logoUrl;
        this.name = name;
        this.id = id;
    }

    /**
     * Returns the optional logo-URL bytes.
     *
     * @apiNote
     * Returns the underlying mutable buffer; callers must not mutate
     * it.
     *
     * @return an {@link Optional} carrying the bytes
     */
    public Optional<byte[]> logoUrl() {
        return Optional.ofNullable(logoUrl);
    }

    /**
     * Returns the optional name bytes.
     *
     * @apiNote
     * Returns the underlying mutable buffer; callers must not mutate
     * it.
     *
     * @return an {@link Optional} carrying the bytes
     */
    public Optional<byte[]> name() {
        return Optional.ofNullable(name);
    }

    /**
     * Returns the optional provider id.
     *
     * @apiNote
     * Empty when the relay omitted it or when the content failed the
     * base-10 integer check.
     *
     * @return an {@link Optional} carrying the id
     */
    public Optional<Integer> id() {
        return Optional.ofNullable(id);
    }

    /**
     * Tries to parse a {@code <provider_info/>} sub-child from the
     * given parent node.
     *
     * @apiNote
     * Mirrors {@code WASmaxInCoexistenceProviderInfoMixin.parseProviderInfoMixin}
     * composed with the per-child accessors; consumed by
     * {@link SmaxCoexistenceOffboardingNotificationResponse#of(Node)}
     * and
     * {@link SmaxCoexistenceOnboardingStatusNotificationResponse#of(Node)}
     * to lift the nested provider block.
     *
     * @implNote
     * This implementation collapses a malformed {@code <id/>} content
     * (non-numeric or out-of-range for {@code int}) to
     * {@link Optional#empty()}, matching the {@code contentInt} parser
     * in {@code WASmaxParseUtils} which signals a parse error on
     * {@code NaN}; WA Web propagates the underlying error envelope
     * instead.
     *
     * @param parent the node carrying the {@code <provider_info/>}
     *               child; never {@code null}
     * @return an {@link Optional} carrying the projection
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
        var providerInfoNode = parent.getChild("provider_info").orElse(null);
        if (providerInfoNode == null) {
            return Optional.empty();
        }
        var logoUrl = providerInfoNode.getChild("logo_url")
                .flatMap(Node::toContentBytes)
                .orElse(null);
        var name = providerInfoNode.getChild("name")
                .flatMap(Node::toContentBytes)
                .orElse(null);
        var idBytes = providerInfoNode.getChild("id")
                .flatMap(Node::toContentString)
                .orElse(null);
        Integer id = null;
        if (idBytes != null) {
            try {
                id = Integer.parseInt(idBytes);
            } catch (NumberFormatException _) {
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
