package br.com.condominioauditoria.backend.orcamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import org.springframework.web.server.ResponseStatusException;

/** RF-03.1.13: o previsto × realizado é consultado por todos os perfis do condomínio, e só do próprio condomínio. */
class PrevistoRealizadoControllerPermissaoTest {

    private static final UUID CONDOMINIO = UUID.randomUUID();

    private AnnotationConfigApplicationContext contexto;
    private PrevistoRealizadoController controller;
    private ConsultaPrevistoRealizado consulta;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        AcessoCondominio acesso() {
            return new AcessoCondominio();
        }

        @Bean
        ConsultaPrevistoRealizado consulta() {
            return mock(ConsultaPrevistoRealizado.class);
        }

        @Bean
        PrevistoRealizadoController controller(AcessoCondominio acesso, ConsultaPrevistoRealizado consulta) {
            return new PrevistoRealizadoController(acesso, consulta);
        }
    }

    @BeforeEach
    void subir() {
        contexto = new AnnotationConfigApplicationContext(Config.class);
        controller = contexto.getBean(PrevistoRealizadoController.class);
        consulta = contexto.getBean(ConsultaPrevistoRealizado.class);
    }

    @AfterEach
    void descer() {
        SecurityContextHolder.clearContext();
        contexto.close();
    }

    @Test
    void todosOsPerfisDoCondominioConsultam() {
        for (String perfil : List.of("USUARIO", "GESTOR", "ADMIN")) {
            logar(CONDOMINIO, "pessoa-" + perfil, perfil);
            controller.consultar(CONDOMINIO, "2026-09", null);
            controller.evidencia(CONDOMINIO, "2026-09", null, "AJUSTES");
        }
        verify(consulta, org.mockito.Mockito.times(3)).consultar(CONDOMINIO, "2026-09", null);
    }

    @Test
    void outroCondominioNaoConsulta() {
        logar(UUID.randomUUID(), "gestor-de-outro", "GESTOR");

        assertThatThrownBy(() -> controller.consultar(CONDOMINIO, "2026-09", null))
                .isInstanceOf(AccessDeniedException.class);
        verify(consulta, never()).consultar(any(), any(), any());
    }

    @Test
    void periodoInvalidoEhRecusado() {
        assertThat(ConsultaPrevistoRealizado.periodo("2026-09")).isEqualTo(new CalculoPrevistoRealizado.Mes(
                java.time.YearMonth.of(2026, 9)));
        assertThat(ConsultaPrevistoRealizado.periodo("ACUMULADO")).isInstanceOf(CalculoPrevistoRealizado.Acumulado.class);
        assertThatThrownBy(() -> ConsultaPrevistoRealizado.periodo("09/2026")).isInstanceOf(ResponseStatusException.class);
    }

    private static void logar(UUID condominio, String usuario, String perfil) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(usuario).claim("preferred_username", usuario)
                .claim("condominios", List.of(condominio.toString())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + perfil)), usuario));
    }
}
