package com.github.auties00.cobalt.wam.processor;

import com.github.auties00.cobalt.wam.processor.element.WamEventElement;
import com.github.auties00.cobalt.wam.processor.generator.WamBuilderGenerator;
import com.github.auties00.cobalt.wam.processor.generator.WamImplGenerator;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Set;

/**
 * An annotation processor that generates companion {@code *Impl} and
 * {@code *Builder} classes for every {@code @WamEvent}-annotated interface.
 *
 * <p>The generated {@code *Impl} class implements the event interface and
 * contains zero-reflection, high-performance {@code sizeOf} and
 * {@code encode} methods that serialize the event into the WAM custom
 * binary protocol using {@code WamEncoder} primitives.
 *
 * <p>The generated {@code *Builder} class provides a fluent API for
 * constructing event instances.
 *
 * @see com.github.auties00.cobalt.wam.annotation.WamEvent
 */
@SupportedAnnotationTypes("com.github.auties00.cobalt.wam.annotation.WamEvent")
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public final class WamEventProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (var annotation : annotations) {
            for (var element : roundEnv.getElementsAnnotatedWith(annotation)) {
                try {
                    var eventElement = WamEventElement.create(element, processingEnv);
                    WamImplGenerator.generate(eventElement)
                            .writeTo(processingEnv.getFiler());
                    WamBuilderGenerator.generate(eventElement)
                            .writeTo(processingEnv.getFiler());
                } catch (Throwable e) {
                    processingEnv.getMessager().printMessage(
                            Diagnostic.Kind.ERROR,
                            "Failed to generate WAM sources: " + e.getMessage(),
                            element
                    );
                }
            }
        }
        return true;
    }
}
