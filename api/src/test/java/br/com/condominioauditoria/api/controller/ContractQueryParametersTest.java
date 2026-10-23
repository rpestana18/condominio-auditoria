package br.com.condominioauditoria.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.Yaml;

/**
 * The query parameters of every endpoint have the same names in the controllers and in contracts/openapi.yaml. The
 * frontend builds its calls from the contract types: a name that only exists on one side (e.g. "filtro" in the contract
 * and "filter" in the controller) makes the filter silently ignored or the call fail with 400.
 */
class ContractQueryParametersTest {

    private static final String API_PREFIX = "/api";

    @Test
    void everyQueryParameterOfTheControllersIsInTheContractWithTheSameName() throws Exception {
        Map<String, Object> paths = contractPaths();
        Map<String, Set<String>> differences = new TreeMap<>();
        for (Endpoint endpoint : endpoints()) {
            String key = endpoint.method() + " " + endpoint.path();
            Map<?, ?> pathItem = (Map<?, ?>) paths.get(endpoint.path());
            Map<?, ?> operation = pathItem == null ? null
                    : (Map<?, ?>) pathItem.get(endpoint.method().toLowerCase(Locale.ROOT));
            if (operation == null) {
                differences.put(key, Set.of("endpoint missing from the contract"));
                continue;
            }
            Set<String> contract = contractQueryParameters(pathItem, operation);
            if (!contract.equals(endpoint.queryParameters())) {
                differences.put(key, Set.of("controller " + endpoint.queryParameters() + " · contract " + contract));
            }
        }
        assertThat(differences).as("query parameters: controller × contracts/openapi.yaml").isEmpty();
    }

    private record Endpoint(String method, String path, Set<String> queryParameters) {
    }

    private static List<Endpoint> endpoints() throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Endpoint> endpoints = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("br.com.condominioauditoria.api.controller")) {
            Class<?> controller = ClassUtils.forName(definition.getBeanClassName(), null);
            RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            String[] prefixes = classMapping == null || classMapping.path().length == 0 ? new String[] {""}
                    : classMapping.path();
            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                Set<String> parameters = new TreeSet<>();
                for (Parameter parameter : method.getParameters()) {
                    RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
                    if (requestParam == null || MultipartFile.class.isAssignableFrom(parameter.getType())) {
                        continue;
                    }
                    String name = !requestParam.name().isEmpty() ? requestParam.name() : requestParam.value();
                    parameters.add(name.isEmpty() ? parameter.getName() : name);
                }
                String[] suffixes = mapping.path().length == 0 ? new String[] {""} : mapping.path();
                for (String prefix : prefixes) {
                    for (String suffix : suffixes) {
                        String path = (prefix + suffix).replaceFirst("^" + API_PREFIX, "");
                        for (RequestMethod verb : mapping.method()) {
                            endpoints.add(new Endpoint(verb.name(), path, parameters));
                        }
                    }
                }
            }
        }
        assertThat(endpoints).as("controllers found").isNotEmpty();
        return endpoints;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> contractPaths() throws IOException {
        Path file = Path.of("..", "contracts", "openapi.yaml");
        try (InputStream in = Files.newInputStream(file)) {
            contract = new Yaml().load(in);
        }
        return (Map<String, Object>) contract.get("paths");
    }

    private Map<String, Object> contract;

    private Set<String> contractQueryParameters(Map<?, ?> pathItem, Map<?, ?> operation) {
        Set<String> names = new TreeSet<>();
        for (Object list : new Object[] {pathItem.get("parameters"), operation.get("parameters")}) {
            if (list == null) {
                continue;
            }
            for (Object item : (List<?>) list) {
                Map<?, ?> parameter = resolve((Map<?, ?>) item);
                if ("query".equals(parameter.get("in"))) {
                    names.add((String) parameter.get("name"));
                }
            }
        }
        return names;
    }

    /** Follows a local "$ref" ("#/components/parameters/X"). */
    private Map<?, ?> resolve(Map<?, ?> node) {
        Object ref = node.get("$ref");
        if (ref == null) {
            return node;
        }
        Object current = contract;
        for (String part : ((String) ref).substring(2).split("/")) {
            current = ((Map<?, ?>) current).get(part);
        }
        return resolve((Map<?, ?>) current);
    }
}
