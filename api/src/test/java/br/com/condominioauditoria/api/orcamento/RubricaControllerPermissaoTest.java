package br.com.condominioauditoria.api.orcamento;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import br.com.condominioauditoria.api.orcamento.RubricaDtos.AcaoLoteRubrica;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.PedidoLoteRubrica;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.PedidoNovaRubrica;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.PedidoRenomear;
import br.com.condominioauditoria.api.orcamento.RubricaDtos.PedidoRubricaLinha;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** RF-11.7: só o Admin cria, renomeia, escolhe, confirma, recusa e sugere rubricas; todos os perfis consultam. */
class RubricaControllerPermissaoTest {

    private static final UUID CONDOMINIO = UUID.randomUUID();
    private static final UUID PO = UUID.randomUUID();
    private static final UUID LINHA = UUID.randomUUID();
    private static final UUID RUBRICA = UUID.randomUUID();

    private AnnotationConfigApplicationContext contexto;
    private RubricaController controller;
    private ServicoRubricas servico;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        CondominiumAccess acesso() {
            return new CondominiumAccess();
        }

        @Bean
        ServicoRubricas servico() {
            return mock(ServicoRubricas.class);
        }

        @Bean
        RubricaController controller(CondominiumAccess acesso, ServicoRubricas servico) {
            return new RubricaController(acesso, servico);
        }
    }

    @BeforeEach
    void subir() {
        contexto = new AnnotationConfigApplicationContext(Config.class);
        controller = contexto.getBean(RubricaController.class);
        servico = contexto.getBean(ServicoRubricas.class);
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
        verify(servico).criar(eq(CONDOMINIO), any(), eq("admin"));
        verify(servico).renomear(eq(CONDOMINIO), eq(RUBRICA), any(), eq("admin"));
        verify(servico).definir(eq(CONDOMINIO), eq(PO), eq(LINHA), any(), eq("admin"));
        verify(servico).lote(eq(CONDOMINIO), eq(PO), any(), eq("admin"));
        verify(servico).sugerir(CONDOMINIO, PO, "admin");
    }

    @Test
    void todosOsPerfisConsultam() {
        for (String perfil : List.of("USUARIO", "GESTOR", "ADMIN")) {
            logar(CONDOMINIO, "pessoa-" + perfil, perfil);
            controller.catalogo(CONDOMINIO);
            controller.listar(CONDOMINIO, PO, null);
            controller.eventos(CONDOMINIO, PO);
        }
    }

    @Test
    void outroCondominioNaoConsulta() {
        logar(UUID.randomUUID(), "gestor-de-outro", "GESTOR");

        assertThatThrownBy(() -> controller.listar(CONDOMINIO, PO, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.catalogo(CONDOMINIO)).isInstanceOf(AccessDeniedException.class);
        verify(servico, never()).listar(any(), any(), any());
    }

    private List<Executable> escritas() {
        return List.of(
                () -> controller.criar(CONDOMINIO, new PedidoNovaRubrica("Academia", "1.3")),
                () -> controller.renomear(CONDOMINIO, RUBRICA, new PedidoRenomear("Academia e ginástica")),
                () -> controller.definir(CONDOMINIO, PO, LINHA, new PedidoRubricaLinha(RUBRICA, null, null)),
                () -> controller.lote(CONDOMINIO, PO, new PedidoLoteRubrica(AcaoLoteRubrica.CONFIRMAR, List.of(LINHA))),
                () -> controller.sugerir(CONDOMINIO, PO));
    }

    private static void logar(UUID condominio, String usuario, String perfil) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(usuario).claim("preferred_username", usuario)
                .claim("condominios", List.of(condominio.toString())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + perfil)), usuario));
    }
}
