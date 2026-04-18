import com.github.auties00.cobalt.client.WhatsAppClientListener;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;


private final Map<Class<?>, String> PRIMITIVE_TO_BOXED_MAP = Map.of(
        boolean.class, Boolean.class.getSimpleName(),
        byte.class, Byte.class.getSimpleName(),
        char.class, Character.class.getSimpleName(),
        double.class, Double.class.getSimpleName(),
        float.class, Float.class.getSimpleName(),
        int.class, Integer.class.getSimpleName(),
        long.class, Long.class.getSimpleName(),
        short.class, Short.class.getSimpleName(),
        void.class, Void.class.getSimpleName()
);

void main() {
    for(var method : WhatsAppClientListener.class.getMethods()) {
        printFunctionalMethod(method);
    }
}

private void printFunctionalMethod(Method method) {
    var originalMethodName = method.getName();
    var originalMethodParams = Arrays.stream(method.getParameters())
            .map(entry -> getParameterType(entry) + " " + entry.getName())
            .collect(Collectors.joining(", "));
    var originalMethodArgs = Arrays.stream(method.getParameters())
            .map(Parameter::getName)
            .collect(Collectors.joining(", "));

    var functionalMethodName = "add%sListener".formatted(originalMethodName.substring(2));
    var functionalMethodParameters = "WhatsappClientListenerConsumer." + switch (method.getParameters().length) {
        case 0 -> "Empty consumer";
        case 1 -> "Unary<%s> consumer".formatted(getParameterType(method.getParameters()[0]));
        case 2 -> "Binary<%s, %s> consumer".formatted(getParameterType(method.getParameters()[0]), getParameterType(method.getParameters()[1]));
        case 3 -> "Ternary<%s, %s, %s> consumer".formatted(getParameterType(method.getParameters()[0]), getParameterType(method.getParameters()[1]), getParameterType(method.getParameters()[2]));
        case 4 -> "Quaternary<%s, %s, %s, %s> consumer".formatted(getParameterType(method.getParameters()[0]), getParameterType(method.getParameters()[1]), getParameterType(method.getParameters()[2]), getParameterType(method.getParameters()[3]));
        default -> throw new IllegalStateException("Unexpected parameters count(" + method.getParameters().length + ") for listener " + method.getName());
    };
    System.out.printf("""
                public WhatsAppClient %s(%s) {
                    Objects.requireNonNull(consumer, "consumer cannot be null");
                    addListener(new WhatsAppListener() {
                        @Override
                          public void %s(%s) {
                              consumer.accept(%s);
                          }
                    });
                    return this;
                }%n%n""", functionalMethodName, functionalMethodParameters, originalMethodName, originalMethodParams, originalMethodArgs);
}

private String getParameterType(Parameter parameter) {
    var parameterizedType = parameter.getParameterizedType();
    return getParameterType(parameterizedType);
}

private String getParameterType(Type type) {
    if (!(type instanceof ParameterizedType pt)) {
        return getSimpleName((Class<?>) type);
    }

    var simpleName = ((Class<?>) pt.getRawType()).getSimpleName();
    var typeArguments = pt.getActualTypeArguments();
    if (typeArguments.length == 0) {
        return simpleName;
    }

    var builder = new StringBuilder(simpleName);
    builder.append("<");
    builder.append(getParameterType(typeArguments[0]));
    for (var i = 1; i < typeArguments.length; i++) {
        builder.append(", ");
        builder.append(getParameterType(typeArguments[i]));
    }
    builder.append(">");
    return builder.toString();
}

private String getSimpleName(Class<?> type) {
    if(type.isPrimitive()) {
        return Objects.requireNonNull(PRIMITIVE_TO_BOXED_MAP.get(type), "Unknown primitive");
    }else {
        return type.getSimpleName();
    }
}
