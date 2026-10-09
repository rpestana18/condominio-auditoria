package br.com.condominioauditoria.api.orcamento;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.condominioauditoria.api.orcamento.DeparaDtos.AcaoLote;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.PedidoDestino;
import br.com.condominioauditoria.api.orcamento.DeparaDtos.PedidoLote;
import br.com.condominioauditoria.api.seguranca.AcessoCondominio;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** RF-03.1.4 e RF-03.1.13: só o Admin cria, altera, confirma e recusa o de-para; todos os perfis consultam. */
class DeparaControllerPermissaoTest {

    private static final UUID CONDOMINIO = UUID.randomUUID();
    private static final UUID PO = UUID.randomUUID();

    private AnnotationConfigApplicationContext contexto;
    private DeparaController controller;
    private ServicoDepara servico;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        AcessoCondominio acesso() {
            return new AcessoCondominio();
        }

        @Bean
        ServicoDepara servico() {
            return mock(ServicoDepara.class);
        }

        @Bean
        DeparaController controller(AcessoCondominio acesso, ServicoDepara servico) {
            return new DeparaController(acesso, servico);
        }
    }

    @BeforeEach
    void subir() {
        contexto = new AnnotationConfigApplicationContext(Config.class);
        controller = contexto.getBean(DeparaController.class);
        servico = contexto.getBean(ServicoDepara.class);
    }

    @AfterEach
    void descer() {
        SecurityContextHolder.clearContext();
        contexto.close();
    }

    @Test
    void gestorEUsuarioNaoEscrevem() {
        for (String perfil : List.of("GESTOR", "USUARIO")) {
            logar(CONDOMINIO, "pessoa-" + perfil, perfil);
            for (Executable escrita : escritas()) {
                assertThatThrownBy(escrita::execute).isInstanceOf(AccessDeniedException.class);
            }
        }
        verifyNoInteractions(servico);
    }

    @Test
    void adminEscreve() throws Throwable {
        logar(CONDOMINIO, "admin", "ADMIN");
        for (Executable escrita : escritas()) {
            escrita.execute();
        }
        verify(servico).sugerir(CONDOMINIO, PO, "admin");
        verify(servico).definir(eq(CONDOMINIO), eq(PO), eq("1621"), any(), eq("admin"));
        verify(servico).lote(eq(CONDOMINIO), eq(PO), any(), eq("admin"));
        verify(servico).carregarPlanilha(eq(CONDOMINIO), eq(PO), eq("mapa.csv"), eq("1621;1.7.8\n"), eq("admin"));
    }

    @Test
    void todosOsPerfisConsultam() {
        for (String perfil : List.of("USUARIO", "GESTOR", "ADMIN")) {
            logar(CONDOMINIO, "pessoa-" + perfil, perfil);
            controller.listar(CONDOMINIO, PO, null);
            controller.eventos(CONDOMINIO, PO);
        }
    }

    @Test
    void outroCondominioNaoConsulta() {
        logar(UUID.randomUUID(), "gestor-de-outro", "GESTOR");

        assertThatThrownBy(() -> controller.listar(CONDOMINIO, PO, null)).isInstanceOf(AccessDeniedException.class);
        verify(servico, never()).listar(any(), any(), any());
    }

    private List<Executable> escritas() {
        return List.of(
                () -> controller.sugerir(CONDOMINIO, PO),
                () -> controller.definir(CONDOMINIO, PO, "1621", new PedidoDestino(TipoDestino.AJUSTE, null, null, null)),
                () -> controller.lote(CONDOMINIO, PO, new PedidoLote(AcaoLote.CONFIRMAR, List.of("1621"))),
                () -> controller.planilha(CONDOMINIO, PO, new MockMultipartFile("arquivo", "mapa.csv", "text/csv",
                        "1621;1.7.8\n".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }

    private static void logar(UUID condominio, String usuario, String perfil) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(usuario).claim("preferred_username", usuario)
                .claim("condominios", List.of(condominio.toString())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + perfil)), usuario));
    }
}
