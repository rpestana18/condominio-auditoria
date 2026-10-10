package br.com.condominioauditoria.rag.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

/**
 * A component with more than one constructor must tell Spring which one to use (@Autowired); otherwise the rag does
 * not start ("No default constructor found"). No other rag test starts the whole context, hence this guard.
 */
class ComponentConstructorsTest {

    @Test
    void componentWithSeveralConstructorsMarksWhichSpringUses() throws Exception {
        var search = new ClassPathScanningCandidateComponentProvider(true);
        List<String> unmarked = new ArrayList<>();
        for (var definition : search.findCandidateComponents("br.com.condominioauditoria.rag")) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            Constructor<?>[] constructors = type.getDeclaredConstructors();
            boolean marked = Arrays.stream(constructors).anyMatch(c -> c.isAnnotationPresent(Autowired.class));
            if (constructors.length > 1 && !marked) {
                unmarked.add(type.getName());
            }
        }
        assertThat(unmarked).as("componentes com vários construtores e nenhum @Autowired").isEmpty();
    }
}
