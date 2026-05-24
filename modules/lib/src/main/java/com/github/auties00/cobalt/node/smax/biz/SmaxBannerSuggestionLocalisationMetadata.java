package com.github.auties00.cobalt.node.smax.biz;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.node.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code <localisation_metadata/>} child of any CTWA banner-suggestion
 * {@code <localised_*>} parallel, carrying the translation identifiers and
 * up to twenty placeholder substitutions.
 *
 * @apiNote
 * Carries the data downstream translation telemetry needs to attribute
 * the localised copy back to its translation-system entry: the
 * {@code uid} is the translation unit identifier and
 * {@code translation_project} is the owning translation project. The
 * {@code <parameter/>} children carry the runtime placeholder
 * substitutions inserted into the translated string (cardinality
 * bound 0..20 enforced server-side).
 */
@WhatsAppWebModule(moduleName = "WASmaxInBizCtwaActionLocalisationMetadataMixin")
public final class SmaxBannerSuggestionLocalisationMetadata {
    /**
     * The mandatory {@code uid} attribute (translation-unit identifier).
     */
    private final String uid;

    /**
     * The mandatory {@code translation_project} attribute (owning
     * translation project).
     */
    private final String translationProject;

    /**
     * The list of {@code <parameter name value/>} entries (0..20).
     */
    private final List<Parameter> parameters;

    /**
     * Constructs a projection from already-validated wire values.
     *
     * @apiNote
     * Cobalt callers normally obtain a projection by parsing a node via
     * {@link #of(Node)}; this constructor is exposed for tests and for
     * hand-built fixtures.
     *
     * @param uid                the translation-unit identifier; never {@code null}
     * @param translationProject the owning translation project; never {@code null}
     * @param parameters         the placeholder list (0..20 entries); may be {@code null} (treated as empty)
     * @throws NullPointerException if {@code uid} or {@code translationProject} is {@code null}
     */
    public SmaxBannerSuggestionLocalisationMetadata(String uid, String translationProject, List<Parameter> parameters) {
        this.uid = Objects.requireNonNull(uid, "uid cannot be null");
        this.translationProject = Objects.requireNonNull(translationProject,
                "translationProject cannot be null");
        this.parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }

    /**
     * Returns the translation-unit identifier.
     *
     * @apiNote
     * Used by downstream telemetry to tie the localised copy back to its
     * translation-system entry.
     *
     * @return the uid; never {@code null}
     */
    public String uid() {
        return uid;
    }

    /**
     * Returns the owning translation project.
     *
     * @apiNote
     * Identifies the translation-system project that authored the
     * localised copy.
     *
     * @return the project name; never {@code null}
     */
    public String translationProject() {
        return translationProject;
    }

    /**
     * Returns the placeholder substitutions list.
     *
     * @apiNote
     * Each entry pairs a placeholder name with its runtime value. The
     * list is bounded to 0..20 entries server-side; an empty list is
     * legal.
     *
     * @return an unmodifiable list of 0..20 entries; never {@code null}
     */
    public List<Parameter> parameters() {
        return parameters;
    }

    /**
     * Parses the projection from a {@code <localisation_metadata/>} node.
     *
     * @apiNote
     * Returns empty when the node tag is wrong, when either mandatory
     * attribute is missing, when the {@code <parameter/>} cardinality
     * exceeds the 0..20 bound, or when any individual parameter entry
     * fails to parse.
     *
     * @implNote
     * This implementation enforces the cardinality bound BEFORE walking
     * individual children, matching WA Web's
     * {@code mapChildrenWithTag(t, "parameter", 0, 20, e)} where the
     * bound check fires first and short-circuits before any
     * {@link Parameter#of(Node)} call.
     *
     * @param node the candidate {@code <localisation_metadata/>} node; never {@code null}
     * @return an {@link Optional} carrying the projection, or empty when
     *         parsing fails at any step
     * @throws NullPointerException if {@code node} is {@code null}
     */
    @WhatsAppWebExport(
            moduleName = "WASmaxInBizCtwaActionLocalisationMetadataMixin",
            exports = "parseLocalisationMetadataMixin",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public static Optional<SmaxBannerSuggestionLocalisationMetadata> of(Node node) {
        Objects.requireNonNull(node, "node cannot be null");
        if (!node.hasDescription("localisation_metadata")) {
            return Optional.empty();
        }
        var uid = node.getAttributeAsString("uid").orElse(null);
        if (uid == null) {
            return Optional.empty();
        }
        var translationProject = node.getAttributeAsString("translation_project").orElse(null);
        if (translationProject == null) {
            return Optional.empty();
        }
        var parameterNodes = node.streamChildren("parameter").toList();
        if (parameterNodes.size() > 20) {
            return Optional.empty();
        }
        var parameters = new ArrayList<Parameter>(parameterNodes.size());
        for (var parameterNode : parameterNodes) {
            var parsed = Parameter.of(parameterNode);
            if (parsed.isEmpty()) {
                return Optional.empty();
            }
            parameters.add(parsed.get());
        }
        return Optional.of(new SmaxBannerSuggestionLocalisationMetadata(uid, translationProject, parameters));
    }

    /**
     * Compares this projection to {@code obj} for structural equality on
     * uid, translation project, and the parameters list.
     *
     * @param obj the candidate; may be {@code null}
     * @return {@code true} when {@code obj} is a {@link SmaxBannerSuggestionLocalisationMetadata}
     *         with matching {@link #uid()}, {@link #translationProject()},
     *         and {@link #parameters()}
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        var that = (SmaxBannerSuggestionLocalisationMetadata) obj;
        return Objects.equals(this.uid, that.uid)
                && Objects.equals(this.translationProject, that.translationProject)
                && Objects.equals(this.parameters, that.parameters);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return the hash of uid, translation project, and the parameters list
     */
    @Override
    public int hashCode() {
        return Objects.hash(uid, translationProject, parameters);
    }

    /**
     * Returns a debug-friendly rendering naming the three slots.
     *
     * @return a record-style string with uid, translation project, and parameters
     */
    @Override
    public String toString() {
        return "SmaxBannerSuggestionLocalisationMetadata[uid=" + uid
                + ", translationProject=" + translationProject
                + ", parameters=" + parameters + ']';
    }

    /**
     * A single placeholder substitution carried by the surrounding
     * {@link SmaxBannerSuggestionLocalisationMetadata} as a
     * {@code <parameter name value/>} child.
     *
     * @apiNote
     * Each entry binds a translation-string placeholder name to its
     * runtime value (for example the merchant name or product price
     * spliced into the localised copy).
     */
    @WhatsAppWebModule(moduleName = "WASmaxInBizCtwaActionLocalisationMetadataMixin")
    public static final class Parameter {
        /**
         * The mandatory {@code name} attribute (placeholder identifier).
         */
        private final String name;

        /**
         * The mandatory {@code value} attribute (placeholder value).
         */
        private final String value;

        /**
         * Constructs a parameter from already-validated wire values.
         *
         * @apiNote
         * Cobalt callers normally obtain a parameter by parsing a node
         * via {@link #of(Node)}; this constructor is exposed for tests
         * and for hand-built fixtures.
         *
         * @param name  the placeholder identifier; never {@code null}
         * @param value the placeholder value; never {@code null}
         * @throws NullPointerException if either argument is {@code null}
         */
        public Parameter(String name, String value) {
            this.name = Objects.requireNonNull(name, "name cannot be null");
            this.value = Objects.requireNonNull(value, "value cannot be null");
        }

        /**
         * Returns the placeholder identifier.
         *
         * @apiNote
         * Matches the named token in the translation-system source string
         * (for example {@code "merchant_name"}).
         *
         * @return the name; never {@code null}
         */
        public String name() {
            return name;
        }

        /**
         * Returns the placeholder value.
         *
         * @apiNote
         * The runtime substitution spliced into the localised copy where
         * the {@link #name()} placeholder appears.
         *
         * @return the value; never {@code null}
         */
        public String value() {
            return value;
        }

        /**
         * Parses a parameter from a {@code <parameter/>} node.
         *
         * @apiNote
         * Returns empty when the node tag is wrong or either mandatory
         * attribute is missing.
         *
         * @param node the candidate {@code <parameter/>} node; never {@code null}
         * @return an {@link Optional} carrying the parsed entry, or empty
         *         when parsing fails
         * @throws NullPointerException if {@code node} is {@code null}
         */
        @WhatsAppWebExport(
                moduleName = "WASmaxInBizCtwaActionLocalisationMetadataMixin",
                exports = "parseLocalisationMetadataParameter",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public static Optional<Parameter> of(Node node) {
            Objects.requireNonNull(node, "node cannot be null");
            if (!node.hasDescription("parameter")) {
                return Optional.empty();
            }
            var name = node.getAttributeAsString("name").orElse(null);
            if (name == null) {
                return Optional.empty();
            }
            var value = node.getAttributeAsString("value").orElse(null);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(new Parameter(name, value));
        }

        /**
         * Compares this parameter to {@code obj} for structural equality
         * on name and value.
         *
         * @param obj the candidate; may be {@code null}
         * @return {@code true} when {@code obj} is a {@link Parameter}
         *         with matching {@link #name()} and {@link #value()}
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (Parameter) obj;
            return Objects.equals(this.name, that.name) && Objects.equals(this.value, that.value);
        }

        /**
         * Returns a hash code consistent with {@link #equals(Object)}.
         *
         * @return the hash of name and value
         */
        @Override
        public int hashCode() {
            return Objects.hash(name, value);
        }

        /**
         * Returns a debug-friendly rendering naming both slots.
         *
         * @return a record-style string with name and value
         */
        @Override
        public String toString() {
            return "SmaxBannerSuggestionLocalisationMetadata.Parameter[name=" + name
                    + ", value=" + value + ']';
        }
    }
}
