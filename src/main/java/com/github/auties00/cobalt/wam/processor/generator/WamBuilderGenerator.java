package com.github.auties00.cobalt.wam.processor.generator;

import com.github.auties00.cobalt.wam.processor.element.WamEventElement;
import com.github.auties00.cobalt.wam.processor.element.WamPropertyElement;
import com.github.auties00.cobalt.wam.type.WamType;
import com.palantir.javapoet.*;

import javax.lang.model.element.Modifier;
import java.time.Instant;

/**
 * Generates the companion {@code *Builder} class for a {@code @WamEvent}-annotated
 * interface, providing a fluent API for constructing event instances.
 *
 * <p>For {@link WamType#TIMER TIMER} properties the builder additionally generates
 * {@code startXxx()} and {@code stopXxx()} methods that measure elapsed
 * milliseconds using {@link Instant#now()}.
 *
 * <p>The generated {@code build()} method returns the event interface type,
 * constructing the processor-generated {@code *Impl} class internally.
 */
public final class WamBuilderGenerator {
    private static final ClassName INSTANT = ClassName.get(Instant.class);

    private WamBuilderGenerator() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Generates a {@code JavaFile} containing the {@code *Builder} class for
     * the given event element.
     *
     * @param event the parsed event metadata
     * @return the generated Java file ready to be written
     */
    public static JavaFile generate(WamEventElement event) {
        var builderName = event.className().simpleName() + "Builder";
        var builderClassName = ClassName.get(event.packageName(), builderName);
        var builderBuilder = TypeSpec.classBuilder(builderName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        // Fields
        for (var prop : event.properties()) {
            var fieldType = resolveFieldType(prop);
            builderBuilder.addField(FieldSpec.builder(fieldType, prop.fieldName(), Modifier.PRIVATE).build());
        }

        // Transient start markers for TIMER fields
        for (var prop : event.properties()) {
            if (prop.wamType() == WamType.TIMER) {
                builderBuilder.addField(
                        FieldSpec.builder(INSTANT, prop.fieldName() + "Start", Modifier.PRIVATE).build()
                );
            }
        }

        // Fluent setter methods
        for (var prop : event.properties()) {
            var fieldType = resolveFieldType(prop);
            builderBuilder.addMethod(
                    MethodSpec.methodBuilder(prop.fieldName())
                            .addModifiers(Modifier.PUBLIC)
                            .returns(builderClassName)
                            .addParameter(fieldType, prop.fieldName())
                            .addStatement("this.$N = $N", prop.fieldName(), prop.fieldName())
                            .addStatement("return this")
                            .build()
            );

            // Timer start/stop helpers
            if (prop.wamType() == WamType.TIMER) {
                builderBuilder.addMethod(
                        MethodSpec.methodBuilder("start" + capitalize(prop.fieldName()))
                                .addModifiers(Modifier.PUBLIC)
                                .returns(builderClassName)
                                .addStatement("this.$NStart = $T.now()", prop.fieldName(), INSTANT)
                                .addStatement("return this")
                                .build()
                );
                builderBuilder.addMethod(
                        MethodSpec.methodBuilder("stop" + capitalize(prop.fieldName()))
                                .addModifiers(Modifier.PUBLIC)
                                .returns(builderClassName)
                                .beginControlFlow("if (this.$NStart != null)", prop.fieldName())
                                .addStatement("this.$N = this.$NStart", prop.fieldName(), prop.fieldName())
                                .addStatement("this.$NStart = null", prop.fieldName())
                                .endControlFlow()
                                .addStatement("return this")
                                .build()
                );
            }
        }

        // build() method — returns the interface type, constructs *Impl
        builderBuilder.addMethod(generateBuildMethod(event));

        return JavaFile.builder(event.packageName(), builderBuilder.build())
                .indent("    ")
                .skipJavaLangImports(true)
                .build();
    }

    private static MethodSpec generateBuildMethod(WamEventElement event) {
        var implClassName = ClassName.get(event.packageName(), event.className().simpleName() + "Impl");
        var method = MethodSpec.methodBuilder("build")
                .addModifiers(Modifier.PUBLIC)
                .returns(event.className());

        var args = new StringBuilder();
        for (var i = 0; i < event.properties().size(); i++) {
            if (i > 0) {
                args.append(",\n        ");
            }
            args.append(event.properties().get(i).fieldName());
        }

        method.addStatement("return new $T(\n        $L\n)", implClassName, args.toString());
        return method.build();
    }

    private static TypeName resolveFieldType(WamPropertyElement prop) {
        return switch (prop.wamType()) {
            case INTEGER -> ClassName.get(Integer.class);
            case BOOLEAN -> ClassName.get(Boolean.class);
            case STRING -> ClassName.get(String.class);
            case FLOAT -> ClassName.get(Double.class);
            case TIMER -> INSTANT;
            case ENUM -> {
                var enumElement = prop.enumElement();
                if (enumElement == null) {
                    throw new IllegalStateException("ENUM property missing enum element: " + prop.fieldName());
                }
                yield enumElement.className();
            }
        };
    }

    private static String capitalize(String name) {
        if (name.isEmpty()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
