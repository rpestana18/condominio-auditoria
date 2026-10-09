package br.com.condominioauditoria.api.modulo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.ia.CatalogoIa;
import br.com.condominioauditoria.api.ia.ConfiguracaoIaServico;
import br.com.condominioauditoria.api.model.condominium.Condominium;
import br.com.condominioauditoria.api.modulo.ModuloController.AlteracaoModulo;
import br.com.condominioauditoria.api.modulo.Modulos.EstadoModulo;
import br.com.condominioauditoria.api.modulo.RegistroUso.ResumoUso;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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
 * Permissões dos módulos (§4, RF-10.2, Q17) com a segurança por método real do Spring: Usuário e Gestor veem o
 * contexto e a lista de módulos do próprio condomínio; só ADMIN liga, desliga, vê trilha, períodos, uso e exporta.
 */
class ModuloControllerPermissaoTest {

    private static final UUID PILOTO = UUID.fromString("6f1d2c1e-3b4a-4c8e-9a51-2815a0000001");
    private static final UUID OUTRO = UUID.randomUUID();
    private static final LocalDate INICIO = LocalDate.of(2026, 10, 1);
    private static final LocalDate FIM = LocalDate.of(2026, 10, 31);

    private static AnnotationConfigApplicationContext contexto;
    private static ModuloController controller;
    private static Modulos modulos;
    private static RegistroUso registroUso;
    private static CondominiumRepository condominios;

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        Modulos modulos() {
            return mock(Modulos.class);
        }

        @Bean
        RegistroUso registroUso() {
            return mock(RegistroUso.class);
        }

        @Bean
        CondominiumRepository condominios() {
            return mock(CondominiumRepository.class);
        }

        @Bean
        ConfiguracaoIaServico configuracaoIa() {
            return mock(ConfiguracaoIaServico.class);
        }

        @Bean
        CatalogoIa catalogoIa() {
            return mock(CatalogoIa.class);
        }

        @Bean
        CondominiumAccess acesso() {
            return new CondominiumAccess();
        }

        @Bean
        ModuloController moduloController(Modulos modulos, RegistroUso registroUso, CondominiumAccess acesso,
                CondominiumRepository condominios, ConfiguracaoIaServico configuracaoIa, CatalogoIa catalogoIa) {
            return new ModuloController(modulos, registroUso, acesso, condominios, configuracaoIa, catalogoIa);
        }
    }

    @BeforeAll
    static void subir() {
        contexto = new AnnotationConfigApplicationContext(Config.class);
        controller = contexto.getBean(ModuloController.class);
        modulos = contexto.getBean(Modulos.class);
        registroUso = contexto.getBean(RegistroUso.class);
        condominios = contexto.getBean(CondominiumRepository.class);
    }

    @AfterAll
    static void descer() {
        contexto.close();
    }

    @BeforeEach
    void preparar() throws Exception {
        reset(modulos, registroUso, condominios);
        var catalogo = CatalogoModulosTest.carregar();
        var estado = new EstadoModulo(catalogo.exigir(Modulos.ASSISTENTE), true, Instant.now(), 1);
        when(modulos.catalogo()).thenReturn(catalogo);
        when(modulos.estados(any())).thenReturn(List.of(estado));
        when(modulos.ligados(any())).thenReturn(List.of(Modulos.ASSISTENTE));
        when(modulos.alterar(any(), anyString(), anyBoolean(), any(), any())).thenReturn(estado);
        when(registroUso.resumo(any(), any(), any())).thenReturn(new ResumoUso(PILOTO, INICIO, FIM, List.of(), List.of()));
        Condominium condominio = mock(Condominium.class);
        when(condominio.getId()).thenReturn(PILOTO);
        when(condominio.getName()).thenReturn("Mio Residencial Parque");
        when(condominios.findById(PILOTO)).thenReturn(Optional.of(condominio));
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void usuarioEGestorVeemContextoEModulosDoProprioCondominio() {
        for (String perfil : List.of("USUARIO", "GESTOR")) {
            logar(perfil);
            assertThat(controller.contexto(PILOTO).modulosLigados()).containsExactly(Modulos.ASSISTENTE);
            assertThat(controller.listar(PILOTO)).extracting(ModuloController.ModuloDoCondominio::codigo)
                    .containsExactly(Modulos.ASSISTENTE);
        }
    }

    @Test
    void semAcessoAoCondominioNaoVeNemOContexto() {
        logar("GESTOR");
        assertThatThrownBy(() -> controller.contexto(OUTRO)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.listar(OUTRO)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void usuarioEGestorNaoLigamNemDesligam() {
        for (String perfil : List.of("USUARIO", "GESTOR")) {
            logar(perfil);
            assertThatThrownBy(() -> controller.alterar(PILOTO, Modulos.ASSISTENTE, new AlteracaoModulo(true, "x")))
                    .isInstanceOf(AccessDeniedException.class);
        }
        verify(modulos, never()).alterar(any(), anyString(), anyBoolean(), any(), any());
    }

    @Test
    void usuarioEGestorNaoVeemTrilhaPeriodosNemUso() {
        for (String perfil : List.of("USUARIO", "GESTOR")) {
            logar(perfil);
            assertThatThrownBy(() -> controller.eventos(PILOTO, Modulos.ASSISTENTE))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.periodos(PILOTO, Modulos.ASSISTENTE))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.uso(PILOTO, INICIO, FIM)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.exportar(PILOTO, INICIO, FIM)).isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void adminAlteraComOProprioUsuarioNaTrilha() {
        logar("ADMIN");

        controller.alterar(PILOTO, Modulos.ASSISTENTE, new AlteracaoModulo(false, "Fim do contrato"));

        verify(modulos).alterar(PILOTO, Modulos.ASSISTENTE, false, "Fim do contrato", "pessoa.admin");
    }

    @Test
    void adminVeTrilhaPeriodosUsoEExporta() {
        logar("ADMIN");

        controller.eventos(PILOTO, Modulos.ASSISTENTE);
        controller.periodos(PILOTO, Modulos.ASSISTENTE);
        assertThat(controller.uso(PILOTO, INICIO, FIM).condominioId()).isEqualTo(PILOTO);
        assertThat(controller.uso(PILOTO, INICIO, FIM).custoDisponivel()).isFalse(); // rag sem catálogo: não quebra
        var exportacao = controller.exportar(PILOTO, INICIO, FIM);

        assertThat(exportacao.getHeaders().getContentType().toString()).isEqualTo(ExportacaoUsoExcel.TIPO);
        assertThat(exportacao.getHeaders().getContentDisposition().getFilename())
                .isEqualTo("uso-modulos-2026-10-01-a-2026-10-31.xlsx");
    }

    /** Token como o do Keycloak: perfil no realm e lista de condomínios (o Admin vê todos). */
    private static void logar(String perfil) {
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "none"),
                Map.of("preferred_username", "pessoa." + perfil.toLowerCase(), "condominios",
                        List.of(PILOTO.toString())));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + perfil)), "pessoa." + perfil.toLowerCase()));
    }
}
