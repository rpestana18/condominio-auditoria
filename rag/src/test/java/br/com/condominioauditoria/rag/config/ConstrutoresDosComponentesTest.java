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
 * Componente com mais de um construtor precisa dizer ao Spring qual usar (@Autowired); senão o rag não sobe
 * ("No default constructor found"). Nenhum outro teste do rag sobe o contexto inteiro, por isso esta guarda.
 */
class ConstrutoresDosComponentesTest {

    @Test
    void componenteComVariosConstrutoresMarcaQualOSpringUsa() throws Exception {
        var busca = new ClassPathScanningCandidateComponentProvider(true);
        List<String> semMarca = new ArrayList<>();
        for (var definicao : busca.findCandidateComponents("br.com.condominioauditoria.rag")) {
            Class<?> classe = Class.forName(definicao.getBeanClassName());
            Constructor<?>[] construtores = classe.getDeclaredConstructors();
            boolean marcado = Arrays.stream(construtores).anyMatch(c -> c.isAnnotationPresent(Autowired.class));
            if (construtores.length > 1 && !marcado) {
                semMarca.add(classe.getName());
            }
        }
        assertThat(semMarca).as("componentes com vários construtores e nenhum @Autowired").isEmpty();
    }
}
