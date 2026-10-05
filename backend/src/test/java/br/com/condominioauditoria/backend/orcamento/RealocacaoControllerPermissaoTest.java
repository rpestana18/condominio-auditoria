package br.com.condominioauditoria.backend.orcamento;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import br.com.condominioauditoria.backend.orcamento.RealocacaoDtos.PedidoRealocacao;
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

/** RF-03.1.7 e ADR 0004, Decisão 8: realocar e desfazer só Gestor e Admin; consultar, todos os perfis. */
class RealocacaoControllerPermissaoTest {

    private static final UUID CONDOMINIO = UUID.randomUUID();
    private static final PedidoRealocacao PEDIDO = new PedidoRealocacao(UUID.randomUUID(), UUID.randomUUID());

    private AnnotationConfigApplicationContext contexto;
    private RealocacaoController controller;
    private ServicoRealocacao servico;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        AcessoCondominio acesso() {
            return new AcessoCondominio();
        }

        @Bean
        ServicoRealocacao servico() {
            return mock(ServicoRealocacao.class);
        }

        @Bean
        RealocacaoController controller(AcessoCondominio acesso, ServicoRealocacao servico) {
            return new RealocacaoController(acesso, servico);
        }
    }

    @BeforeEach
    void subir() {
        contexto = new AnnotationConfigApplicationContext(Config.class);
        controller = contexto.getBean(RealocacaoController.class);
        servico = contexto.getBean(ServicoRealocacao.class);
    }

    @AfterEach
    void descer() {
        SecurityContextHolder.clearContext();
        contexto.close();
    }

    @Test
    void usuarioConsultaMasNaoRealocaNemDesfaz() {
        logar(CONDOMINIO, "usuario", "USUARIO");

        controller.listar(CONDOMINIO, UUID.randomUUID());
        assertThatThrownBy(() -> controller.realocar(CONDOMINIO, PEDIDO)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.desfazer(CONDOMINIO, UUID.randomUUID()))
                .isInstanceOf(AccessDeniedException.class);
        verify(servico, never()).realocar(any(), any(), any());
        verify(servico, never()).desfazer(any(), any(), any());
    }

    @Test
    void gestorEAdminRealocamEDesfazem() {
        for (String perfil : List.of("GESTOR", "ADMIN")) {
            logar(CONDOMINIO, "pessoa-" + perfil, perfil);
            controller.realocar(CONDOMINIO, PEDIDO);
            controller.desfazer(CONDOMINIO, UUID.randomUUID());
        }
        verify(servico, times(2)).realocar(any(), any(), any());
        verify(servico, times(2)).desfazer(any(), any(), any());
    }

    @Test
    void gestorDeOutroCondominioNaoRealoca() {
        logar(UUID.randomUUID(), "gestor-de-outro", "GESTOR");

        assertThatThrownBy(() -> controller.realocar(CONDOMINIO, PEDIDO)).isInstanceOf(AccessDeniedException.class);
        verify(servico, never()).realocar(any(), any(), any());
    }

    private static void logar(UUID condominio, String usuario, String perfil) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(usuario).claim("preferred_username", usuario)
                .claim("condominios", List.of(condominio.toString())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + perfil)), usuario));
    }
}
