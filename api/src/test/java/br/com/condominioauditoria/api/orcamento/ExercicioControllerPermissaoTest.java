package br.com.condominioauditoria.api.orcamento;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.condominioauditoria.api.security.CondominiumAccess;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * RF-11.4 a RF-11.12: todos os perfis do condomínio consultam exercícios, coluna impressa, comparação e
 * indicadores; outro condomínio não.
 */
class ExercicioControllerPermissaoTest {

    private static final UUID CONDOMINIO = UUID.randomUUID();
    private static final UUID PO = UUID.randomUUID();

    private AnnotationConfigApplicationContext contexto;
    private ExercicioController controller;
    private ServicoExercicios servico;
    private ServicoComparacao comparacao;
    private ServicoIndicadores indicadores;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        CondominiumAccess acesso() {
            return new CondominiumAccess();
        }

        @Bean
        ServicoExercicios servico() {
            return mock(ServicoExercicios.class);
        }

        @Bean
        ServicoComparacao comparacao() {
            return mock(ServicoComparacao.class);
        }

        @Bean
        ServicoIndicadores indicadores() {
            return mock(ServicoIndicadores.class);
        }

        @Bean
        ExercicioController controller(CondominiumAccess acesso, ServicoExercicios servico,
                ServicoComparacao comparacao, ServicoIndicadores indicadores) {
            return new ExercicioController(acesso, servico, comparacao, indicadores);
        }
    }

    @BeforeEach
    void subir() {
        contexto = new AnnotationConfigApplicationContext(Config.class);
        controller = contexto.getBean(ExercicioController.class);
        servico = contexto.getBean(ServicoExercicios.class);
        comparacao = contexto.getBean(ServicoComparacao.class);
        indicadores = contexto.getBean(ServicoIndicadores.class);
    }

    @AfterEach
    void descer() {
        SecurityContextHolder.clearContext();
        contexto.close();
    }

    @Test
    void todosOsPerfisConsultam() {
        for (String perfil : List.of("USUARIO", "GESTOR", "ADMIN")) {
            logar(CONDOMINIO, "pessoa-" + perfil, perfil);
            controller.listar(CONDOMINIO);
            controller.colunaImpressa(CONDOMINIO, PO);
            controller.comparar(CONDOMINIO, null, null, false);
            controller.indicadores(CONDOMINIO, PO, null);
        }
        verify(indicadores, times(3)).indicadores(CONDOMINIO, PO, null);
        verify(comparacao, times(3)).comparar(CONDOMINIO, null, null, false);
        verify(servico, times(3)).listar(CONDOMINIO);
        verify(servico, times(3)).colunaImpressa(CONDOMINIO, PO);
    }

    @Test
    void gestorDeOutroCondominioNaoConsulta() {
        logar(UUID.randomUUID(), "gestor", "GESTOR");
        assertThatThrownBy(() -> controller.listar(CONDOMINIO)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.colunaImpressa(CONDOMINIO, PO)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.comparar(CONDOMINIO, null, null, false))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.indicadores(CONDOMINIO, PO, null))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(servico, comparacao, indicadores);
    }

    private static void logar(UUID condominio, String usuario, String perfil) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(usuario).claim("preferred_username", usuario)
                .claim("condominios", List.of(condominio.toString())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + perfil)), usuario));
    }
}
