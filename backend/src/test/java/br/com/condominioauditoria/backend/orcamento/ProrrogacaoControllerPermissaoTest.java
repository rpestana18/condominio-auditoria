package br.com.condominioauditoria.backend.orcamento;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.PedidoProrrogacao;
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

/** RF-11.3: só o Admin marca ou desfaz a prorrogação; Gestor e Usuário recebem 403. */
class ProrrogacaoControllerPermissaoTest {

    private static final UUID CONDOMINIO = UUID.randomUUID();
    private static final UUID PO = UUID.randomUUID();

    private AnnotationConfigApplicationContext contexto;
    private ProrrogacaoController controller;
    private ServicoProrrogacao servico;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        AcessoCondominio acesso() {
            return new AcessoCondominio();
        }

        @Bean
        ServicoProrrogacao servico() {
            return mock(ServicoProrrogacao.class);
        }

        @Bean
        ProrrogacaoController controller(AcessoCondominio acesso, ServicoProrrogacao servico) {
            return new ProrrogacaoController(acesso, servico);
        }
    }

    @BeforeEach
    void subir() {
        contexto = new AnnotationConfigApplicationContext(Config.class);
        controller = contexto.getBean(ProrrogacaoController.class);
        servico = contexto.getBean(ServicoProrrogacao.class);
    }

    @AfterEach
    void descer() {
        SecurityContextHolder.clearContext();
        contexto.close();
    }

    @Test
    void gestorEUsuarioNaoProrrogam() {
        for (String perfil : List.of("GESTOR", "USUARIO")) {
            logar(CONDOMINIO, "pessoa-" + perfil, perfil);
            assertThatThrownBy(() -> controller.prorrogar(CONDOMINIO, PO, new PedidoProrrogacao("2026-04", "atraso")))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.desfazer(CONDOMINIO, PO)).isInstanceOf(AccessDeniedException.class);
        }
        verifyNoInteractions(servico);
    }

    @Test
    void adminProrrogaEDesfaz() {
        logar(CONDOMINIO, "admin", "ADMIN");
        controller.prorrogar(CONDOMINIO, PO, new PedidoProrrogacao("2026-04", "atraso"));
        controller.desfazer(CONDOMINIO, PO);
        verify(servico).prorrogar(eq(CONDOMINIO), eq(PO), any(), eq("admin"));
        verify(servico).desfazer(CONDOMINIO, PO, "admin");
    }

    private static void logar(UUID condominio, String usuario, String perfil) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(usuario).claim("preferred_username", usuario)
                .claim("condominios", List.of(condominio.toString())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + perfil)), usuario));
    }
}
