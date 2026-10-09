package br.com.condominioauditoria.api.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.grpc.ClienteAssistente;
import br.com.condominioauditoria.api.ia.IaController.PedidoAssistenteDto;
import br.com.condominioauditoria.api.ia.IaController.PedidoDto;
import br.com.condominioauditoria.api.ia.IaController.PedidoEmbeddingsDto;
import br.com.condominioauditoria.api.ia.IaController.PedidoRespostasDto;
import br.com.condominioauditoria.api.modulo.ModoIa;
import br.com.condominioauditoria.api.modulo.Modulos;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
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
import org.springframework.transaction.support.TransactionOperations;
import tools.jackson.databind.json.JsonMapper;

/**
 * Configuração de IA na API (RF-09.6): só ADMIN lê e grava, inclusive o catálogo; GESTOR e USUARIO não alteram nem
 * veem. A chave nunca aparece no JSON de resposta (nem aberta nem cifrada) e o catálogo sai sem a chave pública.
 */
class IaControllerTest {

    private static final UUID A = UUID.randomUUID();
    private static final String CHAVE = "sk-ant-api03-ChaveDeTesteNaoReal-x9Qa";

    private final ConfiguracaoIaRepository configuracoes = mock(ConfiguracaoIaRepository.class);
    private final EventoConfiguracaoIaRepository eventos = mock(EventoConfiguracaoIaRepository.class);
    private final ClienteAssistente rag = mock(ClienteAssistente.class);
    private final CondominiumRepository condominios = mock(CondominiumRepository.class);
    private final List<ConfiguracaoIa> linhas = new ArrayList<>();
    private AnnotationConfigApplicationContext contexto;
    private IaController controller;

    @Configuration
    @EnableMethodSecurity
    static class Config {

        @Bean
        IaController iaController(ConfiguracaoIaServico servico, CatalogoIa catalogo, CondominiumAccess acesso,
                CondominiumRepository condominios) {
            return new IaController(servico, catalogo, acesso, condominios);
        }
    }

    @BeforeEach
    void subir() {
        when(condominios.existsById(A)).thenReturn(true);
        when(configuracoes.findByCondominioId(any())).thenAnswer(i -> List.copyOf(linhas));
        when(configuracoes.save(any())).thenAnswer(i -> {
            ConfiguracaoIa c = i.getArgument(0);
            linhas.removeIf(l -> l.getId().equals(c.getId()));
            linhas.add(c);
            return c;
        });
        when(rag.listarProvedores(anyString()))
                .thenReturn(CatalogoTeste.resposta(ParDeChavesTeste.pemPublica(ParDeChavesTeste.par())));
        var catalogo = new CatalogoIa(rag, Duration.ofMinutes(5), java.time.Clock.systemUTC());
        var servico = new ConfiguracaoIaServico(configuracoes, eventos, catalogo, mock(Modulos.class),
                TransactionOperations.withoutTransaction());
        contexto = new AnnotationConfigApplicationContext();
        contexto.registerBean(ConfiguracaoIaServico.class, () -> servico);
        contexto.registerBean(CatalogoIa.class, () -> catalogo);
        contexto.registerBean(CondominiumAccess.class, CondominiumAccess::new);
        contexto.registerBean(CondominiumRepository.class, () -> condominios);
        contexto.register(Config.class);
        contexto.refresh();
        controller = contexto.getBean(IaController.class);
    }

    @AfterEach
    void descer() {
        contexto.close();
        SecurityContextHolder.clearContext();
    }

    @Test
    void gestorEUsuarioNaoVeemNemAlteramAIa() {
        for (String perfil : List.of("USUARIO", "GESTOR")) {
            logar(perfil);
            assertThatThrownBy(() -> controller.ler(A)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.gravar(A, pedido(CHAVE))).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.provedores()).isInstanceOf(AccessDeniedException.class);
        }
        verify(configuracoes, never()).save(any());
        verify(rag, never()).listarProvedores(anyString());
    }

    @Test
    void adminGravaEAChaveNuncaVoltaNoJson() throws Exception {
        logar("ADMIN");

        var gravada = controller.gravar(A, pedido(CHAVE));
        var lida = controller.ler(A);

        var json = JsonMapper.builder().build();
        for (Object resposta : List.of(gravada, lida)) {
            String texto = json.writeValueAsString(resposta);
            assertThat(texto).doesNotContain(CHAVE).doesNotContain("sk-ant")
                    .doesNotContain(Base64.getEncoder().encodeToString(linhas.stream()
                            .filter(ConfiguracaoIa::temChave).findFirst().orElseThrow().getChaveCifrada())
                            .substring(0, 20))
                    .contains("\"chaveCadastrada\":true").contains("\"chaveFinal\":\"x9Qa\"")
                    .contains("\"modoEfetivo\":\"API_KEY\"").contains("\"atualizadoPor\":\"pessoa.admin\"");
        }
    }

    @Test
    void catalogoSaiSemAChavePublica() throws Exception {
        logar("ADMIN");

        String texto = JsonMapper.builder().build().writeValueAsString(controller.provedores());

        assertThat(texto).contains("\"codigo\":\"anthropic\"").contains("\"precoEntradaMilhaoUsd\":\"2.00\"")
                .contains("\"uso\":\"EMBEDDINGS\"").doesNotContain("BEGIN PUBLIC KEY").doesNotContain("chavePublica");
    }

    private static PedidoDto pedido(String chave) {
        return new PedidoDto(ModoIa.MCP_EXTERNO, new PedidoAssistenteDto(
                new PedidoRespostasDto(ModoIa.API_KEY, "anthropic", null, chave, null),
                new PedidoEmbeddingsDto(ModoIa.LOCAL, "ollama-local", null)));
    }

    private static void logar(String perfil) {
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "none"),
                Map.of("preferred_username", "pessoa." + perfil.toLowerCase(), "condominios", List.of(A.toString())));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + perfil)), "pessoa." + perfil.toLowerCase()));
    }
}
