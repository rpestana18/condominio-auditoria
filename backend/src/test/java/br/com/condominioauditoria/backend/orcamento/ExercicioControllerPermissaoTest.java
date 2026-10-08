package br.com.condominioauditoria.backend.orcamento;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.condominioauditoria.backend.seguranca.AcessoCondominio;
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

/** RF-11.4 e RF-11.5: todos os perfis do condomínio consultam os exercícios e a coluna impressa; outro condomínio não. */
class ExercicioControllerPermissaoTest {

    private static final UUID CONDOMINIO = UUID.randomUUID();
    private static final UUID PO = UUID.randomUUID();

    private AnnotationConfigApplicationContext contexto;
    private ExercicioController controller;
    private ServicoExercicios servico;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        AcessoCondominio acesso() {
            return new AcessoCondominio();
        }

        @Bean
        ServicoExercicios servico() {
            return mock(ServicoExercicios.class);
        }

        @Bean
        ExercicioController controller(AcessoCondominio acesso, ServicoExercicios servico) {
            return new ExercicioController(acesso, servico);
        }
    }

    @BeforeEach
    void subir() {
        contexto = new AnnotationConfigApplicationContext(Config.class);
        controller = contexto.getBean(ExercicioController.class);
        servico = contexto.getBean(ServicoExercicios.class);
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
        }
        verify(servico, times(3)).listar(CONDOMINIO);
        verify(servico, times(3)).colunaImpressa(CONDOMINIO, PO);
    }

    @Test
    void gestorDeOutroCondominioNaoConsulta() {
        logar(UUID.randomUUID(), "gestor", "GESTOR");
        assertThatThrownBy(() -> controller.listar(CONDOMINIO)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.colunaImpressa(CONDOMINIO, PO)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(servico);
    }

    private static void logar(UUID condominio, String usuario, String perfil) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(usuario).claim("preferred_username", usuario)
                .claim("condominios", List.of(condominio.toString())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + perfil)), usuario));
    }
}
