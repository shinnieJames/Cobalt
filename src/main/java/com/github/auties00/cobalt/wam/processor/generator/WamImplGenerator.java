package com.github.auties00.cobalt.wam.processor.generator;

import com.github.auties00.cobalt.wam.binary.WamEventEncoder;
import com.github.auties00.cobalt.wam.processor.element.WamEnumElement;
import com.github.auties00.cobalt.wam.processor.element.WamEventElement;
import com.github.auties00.cobalt.wam.processor.element.WamPropertyElement;
import com.github.auties00.cobalt.wam.type.WamChannel;
import com.github.auties00.cobalt.wam.type.WamType;
import com.palantir.javapoet.*;

import javax.lang.model.element.Modifier;
import java.util.HashSet;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Generates the companion {@code *Impl} class for a {@code @WamEvent}-annotated
 * interface, implementing the interface methods and providing high-performance
 * {@code sizeOf} and {@code encode} methods.
 *
 * <p>The generated code makes direct calls to
 * {@link com.github.auties00.cobalt.wam.binary.WamEncoder WamEncoder} with
 * hardcoded field identifiers and pre-determined types. No reflection is
 * involved at runtime.
 */
public final class WamImplGenerator {
    private static final ClassName WAM_EVENT_ENCODER = ClassName.get(WamEventEncoder.class);
    private static final ClassName WAM_CHANNEL = ClassName.get(WamChannel.class);
    private static final ClassName OPTIONAL = ClassName.get(Optional.class);
    private static final ClassName OPTIONAL_INT = ClassName.get(OptionalInt.class);
    private static final ClassName OPTIONAL_LONG = ClassName.get(OptionalLong.class);
    private static final ClassName OPTIONAL_DOUBLE = ClassName.get(OptionalDouble.class);

    private WamImplGenerator() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Generates a {@code JavaFile} containing the {@code *Impl} class for the
     * given event element.
     *
     * @param event the parsed event metadata
     * @return the generated Java file ready to be written
     */
    public static JavaFile generate(WamEventElement event) {
        var implName = event.className().simpleName() + "Impl";
        var implBuilder = TypeSpec.classBuilder(implName)
                .addModifiers(Modifier.FINAL)
                .addSuperinterface(event.className());

        addCommittedField(implBuilder);
        addMetadataAccessors(implBuilder, event);
        addFields(implBuilder, event);
        addConstructor(implBuilder, event);
        addGetterOverrides(implBuilder, event);
        addMarkCommitted(implBuilder);
        addSizeOf(implBuilder, event);
        addEncode(implBuilder, event);
        addEnumEncoders(implBuilder, event);

        return JavaFile.builder(event.packageName(), implBuilder.build())
                .indent("    ")
                .skipJavaLangImports(true)
                .build();
    }

    private static void addMetadataAccessors(TypeSpec.Builder implBuilder, WamEventElement event) {
        implBuilder.addMethod(MethodSpec.methodBuilder("id")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class)
                .addStatement("return $L", event.eventId())
                .build());
        implBuilder.addMethod(MethodSpec.methodBuilder("channel")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(WAM_CHANNEL)
                .addStatement("return $T.$N", WAM_CHANNEL, event.channel().name())
                .build());
        implBuilder.addMethod(MethodSpec.methodBuilder("alphaWeight")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class)
                .addStatement("return $L", event.alphaWeight())
                .build());
        implBuilder.addMethod(MethodSpec.methodBuilder("betaWeight")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class)
                .addStatement("return $L", event.betaWeight())
                .build());
        implBuilder.addMethod(MethodSpec.methodBuilder("releaseWeight")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class)
                .addStatement("return $L", event.releaseWeight())
                .build());
        implBuilder.addMethod(MethodSpec.methodBuilder("privateStatsId")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class)
                .addStatement("return $L", event.privateStatsId())
                .build());
    }

    private static void addCommittedField(TypeSpec.Builder implBuilder) {
        implBuilder.addField(FieldSpec.builder(boolean.class, "committed", Modifier.PRIVATE, Modifier.VOLATILE).build());
    }

    private static void addMarkCommitted(TypeSpec.Builder implBuilder) {
        implBuilder.addMethod(MethodSpec.methodBuilder("markCommitted")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(boolean.class)
                .beginControlFlow("if (this.committed)")
                .addStatement("return false")
                .endControlFlow()
                .addStatement("this.committed = true")
                .addStatement("return true")
                .build());
    }

    private static void addFields(TypeSpec.Builder implBuilder, WamEventElement event) {
        for (var prop : event.properties()) {
            var fieldType = rawFieldType(prop);
            implBuilder.addField(FieldSpec.builder(fieldType, prop.fieldName(), Modifier.PRIVATE, Modifier.FINAL).build());
        }
    }

    private static void addConstructor(TypeSpec.Builder implBuilder, WamEventElement event) {
        var ctor = MethodSpec.constructorBuilder();
        for (var prop : event.properties()) {
            var fieldType = rawFieldType(prop);
            ctor.addParameter(fieldType, prop.fieldName());
            ctor.addStatement("this.$N = $N", prop.fieldName(), prop.fieldName());
        }
        implBuilder.addMethod(ctor.build());
    }

    private static void addGetterOverrides(TypeSpec.Builder implBuilder, WamEventElement event) {
        for (var prop : event.properties()) {
            var method = MethodSpec.methodBuilder(prop.fieldName())
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(optionalReturnType(prop));
            addGetterBody(method, prop);
            implBuilder.addMethod(method.build());
        }
    }

    private static void addGetterBody(MethodSpec.Builder method, WamPropertyElement prop) {
        switch (prop.wamType()) {
            case INTEGER -> method.addStatement(
                    "return this.$N != null ? $T.of(this.$N) : $T.empty()",
                    prop.fieldName(), OPTIONAL_INT, prop.fieldName(), OPTIONAL_INT);
            case TIMER -> method.addStatement(
                    "return this.$N != null ? $T.of(this.$N) : $T.empty()",
                    prop.fieldName(), OPTIONAL_LONG, prop.fieldName(), OPTIONAL_LONG);
            case FLOAT -> method.addStatement(
                    "return this.$N != null ? $T.of(this.$N) : $T.empty()",
                    prop.fieldName(), OPTIONAL_DOUBLE, prop.fieldName(), OPTIONAL_DOUBLE);
            default -> method.addStatement(
                    "return $T.ofNullable(this.$N)",
                    OPTIONAL, prop.fieldName());
        }
    }

    private static void addSizeOf(TypeSpec.Builder implBuilder, WamEventElement event) {
        var method = MethodSpec.methodBuilder("sizeOf")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class)
                .addParameter(int.class, "weight");

        method.addStatement("int size = $T.eventMarkerSize($L, weight)",
                WAM_EVENT_ENCODER, event.eventId());

        for (var prop : event.properties()) {
            method.beginControlFlow("if (this.$N != null)", prop.fieldName());
            addSizeForProperty(method, prop);
            method.endControlFlow();
        }

        method.addStatement("return size");
        implBuilder.addMethod(method.build());
    }

    private static void addSizeForProperty(MethodSpec.Builder method, WamPropertyElement prop) {
        switch (prop.wamType()) {
            case INTEGER -> method.addStatement("size += $T.intFieldSize($L, this.$N)",
                    WAM_EVENT_ENCODER, prop.index(), prop.fieldName());
            case BOOLEAN -> method.addStatement("size += $T.boolFieldSize($L)",
                    WAM_EVENT_ENCODER, prop.index());
            case STRING -> method.addStatement("size += $T.stringFieldSize($L, this.$N)",
                    WAM_EVENT_ENCODER, prop.index(), prop.fieldName());
            case FLOAT -> method.addStatement("size += $T.floatFieldSize($L)",
                    WAM_EVENT_ENCODER, prop.index());
            case TIMER -> {
                method.beginControlFlow("if (this.$N <= $L)", prop.fieldName(), Integer.MAX_VALUE);
                method.addStatement("size += $T.intFieldSize($L, this.$N)",
                        WAM_EVENT_ENCODER, prop.index(), prop.fieldName());
                method.endControlFlow();
            }
            case ENUM -> method.addStatement("size += $T.intFieldSize($L, $N(this.$N))",
                    WAM_EVENT_ENCODER, prop.index(), encoderMethodName(prop), prop.fieldName());
        }
    }

    private static void addEncode(TypeSpec.Builder implBuilder, WamEventElement event) {
        var method = MethodSpec.methodBuilder("encode")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(int.class)
                .addParameter(byte[].class, "output")
                .addParameter(int.class, "offset")
                .addParameter(int.class, "weight");

        var properties = event.properties();

        // Find last non-null field index for LAST flag computation
        // Timer fields that exceed Integer.MAX_VALUE are skipped on the wire,
        // so they must also be excluded from the last-field determination.
        method.addStatement("int lastField = -1");
        for (var i = properties.size() - 1; i >= 0; i--) {
            var prop = properties.get(i);
            if (i == properties.size() - 1) {
                if (prop.wamType() == WamType.TIMER) {
                    method.beginControlFlow("if (this.$N != null && this.$N <= $L)",
                            prop.fieldName(), prop.fieldName(), Integer.MAX_VALUE);
                } else {
                    method.beginControlFlow("if (this.$N != null)", prop.fieldName());
                }
            } else {
                if (prop.wamType() == WamType.TIMER) {
                    method.nextControlFlow("else if (this.$N != null && this.$N <= $L)",
                            prop.fieldName(), prop.fieldName(), Integer.MAX_VALUE);
                } else {
                    method.nextControlFlow("else if (this.$N != null)", prop.fieldName());
                }
            }
            method.addStatement("lastField = $L", i);
        }
        if (!properties.isEmpty()) {
            method.endControlFlow();
        }

        // Event marker
        method.addStatement(
                "offset = $T.writeEventMarker($L, weight, lastField >= 0, output, offset)",
                WAM_EVENT_ENCODER, event.eventId()
        );

        // Fields
        for (var i = 0; i < properties.size(); i++) {
            var prop = properties.get(i);
            method.beginControlFlow("if (this.$N != null)", prop.fieldName());
            method.addStatement("boolean hasMore = $L < lastField", i);
            addWriteForProperty(method, prop);
            method.endControlFlow();
        }

        method.addStatement("return offset");
        implBuilder.addMethod(method.build());
    }

    private static void addWriteForProperty(MethodSpec.Builder method, WamPropertyElement prop) {
        switch (prop.wamType()) {
            case INTEGER -> method.addStatement(
                    "offset = $T.writeIntField($L, this.$N, hasMore, output, offset)",
                    WAM_EVENT_ENCODER, prop.index(), prop.fieldName());
            case BOOLEAN -> method.addStatement(
                    "offset = $T.writeBoolField($L, this.$N, hasMore, output, offset)",
                    WAM_EVENT_ENCODER, prop.index(), prop.fieldName());
            case STRING -> method.addStatement(
                    "offset = $T.writeStringField($L, this.$N, hasMore, output, offset)",
                    WAM_EVENT_ENCODER, prop.index(), prop.fieldName());
            case FLOAT -> method.addStatement(
                    "offset = $T.writeFloatField($L, this.$N, hasMore, output, offset)",
                    WAM_EVENT_ENCODER, prop.index(), prop.fieldName());
            case TIMER -> {
                method.beginControlFlow("if (this.$N <= $L)", prop.fieldName(), Integer.MAX_VALUE);
                method.addStatement("offset = $T.writeIntField($L, this.$N, hasMore, output, offset)",
                        WAM_EVENT_ENCODER, prop.index(), prop.fieldName());
                method.endControlFlow();
            }
            case ENUM -> method.addStatement(
                    "offset = $T.writeIntField($L, $N(this.$N), hasMore, output, offset)",
                    WAM_EVENT_ENCODER, prop.index(), encoderMethodName(prop), prop.fieldName());
        }
    }

    private static void addEnumEncoders(TypeSpec.Builder implBuilder, WamEventElement event) {
        var generated = new HashSet<String>();
        for (var prop : event.properties()) {
            if (prop.wamType() != WamType.ENUM || prop.enumElement() == null) {
                continue;
            }
            var methodName = encoderMethodName(prop);
            if (!generated.add(methodName)) {
                continue;
            }
            implBuilder.addMethod(generateEnumEncoder(prop.enumElement(), methodName));
        }
    }

    private static MethodSpec generateEnumEncoder(WamEnumElement enumElement, String methodName) {
        var method = MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .returns(long.class)
                .addParameter(enumElement.className(), "value");

        var switchBuilder = CodeBlock.builder()
                .add("return switch (value) {\n");
        for (var entry : enumElement.constants().entrySet()) {
            switchBuilder.add("    case $N -> $LL;\n", entry.getKey(), entry.getValue());
        }
        switchBuilder.add("};\n");
        method.addCode(switchBuilder.build());

        return method.build();
    }

    private static TypeName rawFieldType(WamPropertyElement prop) {
        return switch (prop.wamType()) {
            case INTEGER -> ClassName.get(Integer.class);
            case BOOLEAN -> ClassName.get(Boolean.class);
            case STRING -> ClassName.get(String.class);
            case FLOAT -> ClassName.get(Double.class);
            case TIMER -> ClassName.get(Long.class);
            case ENUM -> {
                var enumElement = prop.enumElement();
                if (enumElement == null) {
                    throw new IllegalStateException("ENUM property missing enum element: " + prop.fieldName());
                }
                yield enumElement.className();
            }
        };
    }

    private static TypeName optionalReturnType(WamPropertyElement prop) {
        return switch (prop.wamType()) {
            case INTEGER -> OPTIONAL_INT;
            case TIMER -> OPTIONAL_LONG;
            case FLOAT -> OPTIONAL_DOUBLE;
            default -> ParameterizedTypeName.get(OPTIONAL, rawFieldType(prop));
        };
    }

    private static String encoderMethodName(WamPropertyElement prop) {
        var enumElement = prop.enumElement();
        if (enumElement == null) {
            throw new IllegalStateException("No enum element for property " + prop.fieldName());
        }
        return "encode" + enumElement.className().simpleName();
    }
}
