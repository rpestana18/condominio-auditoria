package br.com.condominioauditoria.backend.orcamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.backend.seguranca.AcessoCondominio;
import java.util.List;
import java.util.Optional;
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
 * RF-03.1.3 e RF-03.1.13: consultar a PO vale para todos os perfis do condomínio; confirmar, só o Admin. A negação
 * vira 403 na API (AccessDeniedException tratada pelo Spring Security). Usa a segurança de método real
 * ({@code @EnableMethodSecurity}, como na SegurancaConfig).
 */
class PrevisaoControllerPermissaoTest {

    private static final UUID CONDOMINIO = UUID.randomUUID();
    private static final UUID PO = UUID.randomUUID();

    private AnnotationConfigApplicationContext contexto;
    private PrevisaoController controller;
    private ConsultaPrevisao consulta;
    private ConfirmacaoPrevisao confirmacao;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        AcessoCondominio acesso() {
            return new AcessoCondominio();
        }

        @Bean
        ConsultaPrevisao consulta() {
            return mock(ConsultaPrevisao.class);
        }

        @Bean
        ConfirmacaoPrevisao confirmacao() {
            return mock(ConfirmacaoPrevisao.class);
        }

        @Bean
        PrevisaoController controller(AcessoCondominio acesso, ConsultaPrevisao consulta,
                ConfirmacaoPrevisao confirmacao) {
            return new PrevisaoController(acesso, consulta, confirmacao);
        }
    }

    @BeforeEach
    void subir() {
        contexto = new AnnotationConfigApplicationContext(Config.class);
        controller = contexto.getBean(PrevisaoController.class);
        consulta = contexto.getBean(ConsultaPrevisao.class);
        confirmacao = contexto.getBean(ConfirmacaoPrevisao.class);
        when(consulta.listar(CONDOMINIO)).thenReturn(List.of());
        when(consulta.detalhe(CONDOMINIO, PO)).thenReturn(Optional.of(mock(PrevisaoDtos.PrevisaoDetalhe.class)));
    }

    @AfterEach
    void descer() {
        SecurityContextHolder.clearContext();
        contexto.close();
    }

    @Test
    void gestorNaoConfirma() {
        logar("gestor", "GESTOR");

        assertThatThrownBy(() -> controller.confirmar(CONDOMINIO, PO, pedido()))
                .isInstanceOf(AccessDeniedException.class);
        verify(confirmacao, never()).confirmar(any(), any(), any(), any());
    }

    @Test
    void usuarioNaoConfirma() {
        logar("morador", "USUARIO");

        assertThatThrownBy(() -> controller.confirmar(CONDOMINIO, PO, pedido()))
                .isInstanceOf(AccessDeniedException.class);
        verify(confirmacao, never()).confirmar(any(), any(), any(), any());
    }

    @Test
    void adminConfirma() {
        logar("admin", "ADMIN");

        controller.confirmar(CONDOMINIO, PO, pedido());

        verify(confirmacao).confirmar(eq(CONDOMINIO), eq(PO), any(), eq("admin"));
    }

    @Test
    void todosOsPerfisDoCondominioConsultam() {
        for (String perfil : List.of("USUARIO", "GESTOR", "ADMIN")) {
            logar("pessoa-" + perfil, perfil);

            assertThat(controller.listar(CONDOMINIO)).isEmpty();
            assertThat(controller.detalhe(CONDOMINIO, PO)).isNotNull();
        }
    }

    @Test
    void quemNaoEhDoCondominioNaoConsulta() {
        logarEm(UUID.randomUUID(), "gestor-de-outro", "GESTOR");

        assertThatThrownBy(() -> controller.listar(CONDOMINIO)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.detalhe(CONDOMINIO, PO)).isInstanceOf(AccessDeniedException.class);
    }

    private static PedidoConfirmacao pedido() {
        return new PedidoConfirmacao("2026-05", "2027-04", null, true, null, List.of(), List.of(), false, false, null);
    }

    private static void logar(String usuario, String perfil) {
        logarEm(CONDOMINIO, usuario, perfil);
    }

    private static void logarEm(UUID condominio, String usuario, String perfil) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(usuario).claim("preferred_username", usuario)
                .claim("condominios", List.of(condominio.toString())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + perfil)), usuario));
    }
}
