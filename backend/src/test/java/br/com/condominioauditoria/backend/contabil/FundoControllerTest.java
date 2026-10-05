package br.com.condominioauditoria.backend.contabil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.backend.condominio.Condominio;
import br.com.condominioauditoria.backend.condominio.CondominioRepository;
import br.com.condominioauditoria.backend.seguranca.AcessoCondominio;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** RF-03.1.9: os fundos aparecem pelo nome impresso, "OBRAS" e "OBRAS / REFORMAS / INFRA" como itens separados. */
class FundoControllerTest {

    private final UUID condominioId = UUID.randomUUID();
    private final Fundo ordinario = new Fundo(condominioId, "CONDOMÍNIO");
    private final FundoController controller;

    FundoControllerTest() {
        Condominio condominio = mock(Condominio.class);
        when(condominio.getFundoOrdinarioId()).thenReturn(ordinario.getId());
        CondominioRepository condominios = mock(CondominioRepository.class);
        when(condominios.findById(condominioId)).thenReturn(Optional.of(condominio));
        FundoRepository fundos = mock(FundoRepository.class);
        when(fundos.findByCondominioId(condominioId)).thenReturn(List.of(new Fundo(condominioId,
                "OBRAS / REFORMAS / INFRA"), ordinario, new Fundo(condominioId, "OBRAS"),
                new Fundo(condominioId, "FUNDO DE RESERVA")));
        controller = new FundoController(new AcessoCondominio(), condominios, fundos);
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listaPeloNomeImpressoComOFundoOrdinarioMarcado() {
        logar(condominioId, "USUARIO");

        var lista = controller.listar(condominioId);

        assertThat(lista).extracting(FundoController.FundoDto::nome).containsExactly("CONDOMÍNIO", "FUNDO DE RESERVA",
                "OBRAS", "OBRAS / REFORMAS / INFRA");
        assertThat(lista).filteredOn(FundoController.FundoDto::ordinario).singleElement()
                .satisfies(f -> assertThat(f.id()).isEqualTo(ordinario.getId()));
    }

    @Test
    void semFundoOrdinarioConfirmadoListaTodosSemMarca() {
        Condominio semOrdinario = mock(Condominio.class);
        CondominioRepository condominios = mock(CondominioRepository.class);
        when(condominios.findById(condominioId)).thenReturn(Optional.of(semOrdinario));
        FundoRepository fundos = mock(FundoRepository.class);
        when(fundos.findByCondominioId(condominioId)).thenReturn(List.of(ordinario, new Fundo(condominioId, "OBRAS")));
        logar(condominioId, "GESTOR");

        var lista = new FundoController(new AcessoCondominio(), condominios, fundos).listar(condominioId);

        assertThat(lista).extracting(FundoController.FundoDto::nome).containsExactly("CONDOMÍNIO", "OBRAS");
        assertThat(lista).noneMatch(FundoController.FundoDto::ordinario);
    }

    @Test
    void outroCondominioNaoLista() {
        logar(UUID.randomUUID(), "GESTOR");

        assertThatThrownBy(() -> controller.listar(condominioId)).isInstanceOf(AccessDeniedException.class);
    }

    private static void logar(UUID condominio, String perfil) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("p").claim("condominios",
                List.of(condominio.toString())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + perfil)), "p"));
    }
}
