package br.com.condominioauditoria.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.HttpEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

/**
 * Freezes the REST contract as the frontend sees it: for every endpoint, the HTTP method, the path, the request
 * parameter names (path variables by position) and the JSON shape (property names, nesting and enum values) of the request and response bodies.
 * Class and field names in Java can change freely (ADR 0006, phase 1); the JSON must not. The snapshot is written on
 * the first run; after that any difference fails. A deliberate API change (phase 2) regenerates it: delete the file
 * and run the test again.
 */
class ApiContractSnapshotTest {

    private static final Path SNAPSHOT = Path.of("src/test/resources/contract/api-json.snapshot");
    private static final String BASE_PACKAGE = "br.com.condominioauditoria.api";

    @Test
    void jsonContractDoesNotChange() throws IOException, ClassNotFoundException {
        String current = describeApi();
        if (!Files.exists(SNAPSHOT)) {
            Files.createDirectories(SNAPSHOT.getParent());
            Files.writeString(SNAPSHOT, current, StandardCharsets.UTF_8);
            return;
        }
        assertThat(current).isEqualTo(Files.readString(SNAPSHOT, StandardCharsets.UTF_8));
    }

    private static String describeApi() throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Map<String, String> endpoints = new TreeMap<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> controller = Class.forName(definition.getBeanClassName());
            RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            String prefix = classMapping == null || classMapping.path().length == 0 ? "" : classMapping.path()[0];
            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                // Path variables only by position: their names are Java, not part of the URL
                String path = (prefix + (mapping.path().length == 0 ? "" : mapping.path()[0]))
                        .replaceAll("\\{[^}/]+}", "{}");
                String verbs = Arrays.stream(mapping.method()).map(Enum::name).sorted().reduce((a, b) -> a + "," + b)
                        .orElse("ANY");
                endpoints.put(verbs + " " + path, describeEndpoint(method, mapping));
            }
        }
        StringBuilder out = new StringBuilder();
        endpoints.forEach((key, value) -> out.append(key).append('\n').append(value).append('\n'));
        return out.toString();
    }

    private static String describeEndpoint(Method method, RequestMapping mapping) {
        StringBuilder out = new StringBuilder();
        if (mapping.params().length > 0) {
            out.append("  params-condition: ").append(String.join(",", mapping.params())).append('\n');
        }
        if (mapping.consumes().length > 0) {
            out.append("  consumes: ").append(String.join(",", mapping.consumes())).append('\n');
        }
        if (mapping.produces().length > 0) {
            out.append("  produces: ").append(String.join(",", mapping.produces())).append('\n');
        }
        for (Parameter parameter : method.getParameters()) {
            RequestParam param = parameter.getAnnotation(RequestParam.class);
            PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);
            RequestPart part = parameter.getAnnotation(RequestPart.class);
            RequestHeader header = parameter.getAnnotation(RequestHeader.class);
            if (param != null) {
                out.append("  query ").append(bindingName(param.name(), param.value(), parameter))
                        .append(param.required() ? "" : "?").append(": ")
                        .append(shape(parameter.getParameterizedType(), new HashSet<>(), "    ")).append('\n');
            } else if (pathVariable != null) {
                out.append("  path: ").append(shape(parameter.getParameterizedType(), new HashSet<>(), "    "))
                        .append('\n');
            } else if (part != null) {
                out.append("  part ").append(bindingName(part.name(), part.value(), parameter)).append('\n');
            } else if (header != null) {
                out.append("  header ").append(bindingName(header.name(), header.value(), parameter)).append('\n');
            } else if (parameter.isAnnotationPresent(RequestBody.class)) {
                out.append("  body: ").append(shape(parameter.getParameterizedType(), new HashSet<>(), "    "))
                        .append('\n');
            }
        }
        out.append("  returns: ").append(shape(method.getGenericReturnType(), new HashSet<>(), "    ")).append('\n');
        return out.toString();
    }

    private static String bindingName(String name, String value, Parameter parameter) {
        if (!name.isEmpty()) {
            return name;
        }
        return value.isEmpty() ? parameter.getName() : value;
    }

    /** JSON shape of a Java type, following Jackson's defaults for records, beans, enums and collections. */
    private static String shape(Type type, Set<Class<?>> visiting, String indent) {
        if (type instanceof WildcardType wildcard) {
            return shape(wildcard.getUpperBounds()[0], visiting, indent);
        }
        if (type instanceof GenericArrayType array) {
            return "[" + shape(array.getGenericComponentType(), visiting, indent) + "]";
        }
        if (type instanceof ParameterizedType parameterized) {
            Class<?> raw = (Class<?>) parameterized.getRawType();
            Type[] args = parameterized.getActualTypeArguments();
            if (HttpEntity.class.isAssignableFrom(raw) || Optional.class.equals(raw)) {
                return shape(args[0], visiting, indent);
            }
            if (Collection.class.isAssignableFrom(raw)) {
                return "[" + shape(args[0], visiting, indent) + "]";
            }
            if (Map.class.isAssignableFrom(raw)) {
                return "map<" + shape(args[0], visiting, indent) + ", " + shape(args[1], visiting, indent) + ">";
            }
            return shape(raw, visiting, indent);
        }
        if (!(type instanceof Class<?> type1)) {
            return "?";
        }
        Class<?> clazz = type1;
        if (clazz.isArray()) {
            return clazz.getComponentType() == byte.class ? "bytes" : "[" + shape(clazz.getComponentType(), visiting,
                    indent) + "]";
        }
        if (clazz.isPrimitive() || Number.class.isAssignableFrom(clazz) || clazz == Boolean.class
                || clazz == String.class || clazz == Character.class || clazz == UUID.class
                || clazz == BigDecimal.class || clazz == BigInteger.class || Temporal.class.isAssignableFrom(clazz)
                || clazz == Object.class || clazz == void.class || clazz == Void.class
                || clazz.getName().startsWith("java.") || clazz.getName().startsWith("org.springframework.")) {
            return simpleName(clazz);
        }
        if (clazz.isEnum()) {
            return "enum" + enumValues(clazz);
        }
        if (!visiting.add(clazz)) {
            return "<recursive>";
        }
        try {
            StringBuilder out = new StringBuilder("{\n");
            for (String[] property : properties(clazz, visiting, indent + "  ")) {
                out.append(indent).append(property[0]).append(": ").append(property[1]).append('\n');
            }
            return out.append(indent, 0, indent.length() - 2).append('}').toString();
        } finally {
            visiting.remove(clazz);
        }
    }

    /** Property name and shape pairs, sorted by name (Jackson's order is not part of the contract). */
    private static List<String[]> properties(Class<?> clazz, Set<Class<?>> visiting, String nested) {
        List<String[]> properties = new ArrayList<>();
        if (clazz.isRecord()) {
            for (RecordComponent component : clazz.getRecordComponents()) {
                Field field = field(clazz, component.getName());
                if (ignored(component.getAccessor()) || (field != null && ignored(field))) {
                    continue;
                }
                properties.add(new String[] {jsonName(component.getName(), component.getAccessor(), field),
                        shape(component.getGenericType(), visiting, nested)});
            }
        } else {
            for (Method getter : clazz.getMethods()) {
                String name = propertyFromGetter(getter);
                if (name == null || ignored(getter)) {
                    continue;
                }
                properties.add(new String[] {jsonName(name, getter, field(clazz, name)),
                        shape(getter.getGenericReturnType(), visiting, nested)});
            }
        }
        properties.sort(Comparator.comparing(p -> p[0]));
        return properties;
    }

    private static String propertyFromGetter(Method method) {
        if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() > 0
                || method.getDeclaringClass() == Object.class) {
            return null;
        }
        String name = method.getName();
        if (name.startsWith("get") && name.length() > 3 && method.getReturnType() != void.class) {
            return Character.toLowerCase(name.charAt(3)) + name.substring(4);
        }
        if (name.startsWith("is") && name.length() > 2 && method.getReturnType() == boolean.class) {
            return Character.toLowerCase(name.charAt(2)) + name.substring(3);
        }
        return null;
    }

    private static Field field(Class<?> clazz, String name) {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static boolean ignored(java.lang.reflect.AnnotatedElement element) {
        JsonIgnore ignore = element.getAnnotation(JsonIgnore.class);
        return ignore != null && ignore.value();
    }

    private static String jsonName(String javaName, Method accessor, Field field) {
        for (var element : new java.lang.reflect.AnnotatedElement[] {accessor, field}) {
            if (element == null) {
                continue;
            }
            JsonProperty property = element.getAnnotation(JsonProperty.class);
            if (property != null && !property.value().isEmpty()) {
                return property.value();
            }
        }
        return javaName;
    }

    private static String enumValues(Class<?> enumClass) {
        Method jsonValue = Arrays.stream(enumClass.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(JsonValue.class)).findFirst().orElse(null);
        List<String> values = new ArrayList<>();
        for (Object constant : enumClass.getEnumConstants()) {
            try {
                values.add(jsonValue == null ? ((Enum<?>) constant).name() : String.valueOf(jsonValue.invoke(constant)));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        return values.toString();
    }

    private static String simpleName(Class<?> clazz) {
        if (clazz == int.class || clazz == Integer.class || clazz == long.class || clazz == Long.class
                || clazz == short.class || clazz == Short.class) {
            return "integer";
        }
        if (clazz == BigDecimal.class || clazz == double.class || clazz == Double.class || clazz == float.class
                || clazz == Float.class) {
            return "number";
        }
        if (clazz == boolean.class || clazz == Boolean.class) {
            return "boolean";
        }
        if (clazz == String.class || clazz == Character.class || clazz == char.class) {
            return "string";
        }
        return clazz.getSimpleName();
    }
}
