package br.com.condominioauditoria.api.controller.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.dto.response.accounting.FundResponse;
import br.com.condominioauditoria.api.model.accounting.Fund;
import br.com.condominioauditoria.api.model.condominium.Condominium;
import br.com.condominioauditoria.api.repository.accounting.FundRepository;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import br.com.condominioauditoria.api.security.CondominiumAccess;
import br.com.condominioauditoria.api.service.accounting.FundService;
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

/** RF-03.1.9: funds show up by their printed name, "OBRAS" and "OBRAS / REFORMAS / INFRA" as separate items. */
class FundControllerTest {

    private final UUID condominiumId = UUID.randomUUID();
    private final Fund operatingFund = new Fund(condominiumId, "CONDOMÍNIO");
    private final FundController controller;

    FundControllerTest() {
        Condominium condominium = mock(Condominium.class);
        when(condominium.getOperatingFundId()).thenReturn(operatingFund.getId());
        CondominiumRepository condominiums = mock(CondominiumRepository.class);
        when(condominiums.findById(condominiumId)).thenReturn(Optional.of(condominium));
        FundRepository funds = mock(FundRepository.class);
        when(funds.findByCondominiumId(condominiumId)).thenReturn(List.of(new Fund(condominiumId,
                "OBRAS / REFORMAS / INFRA"), operatingFund, new Fund(condominiumId, "OBRAS"),
                new Fund(condominiumId, "FUNDO DE RESERVA")));
        controller = new FundController(new CondominiumAccess(), new FundService(condominiums, funds));
    }

    @AfterEach
    void logOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listsByPrintedNameWithTheOperatingFundFlagged() {
        logIn(condominiumId, "USUARIO");

        var list = controller.list(condominiumId);

        assertThat(list).extracting(FundResponse::name).containsExactly("CONDOMÍNIO", "FUNDO DE RESERVA",
                "OBRAS", "OBRAS / REFORMAS / INFRA");
        assertThat(list).filteredOn(FundResponse::operating).singleElement()
                .satisfies(f -> assertThat(f.id()).isEqualTo(operatingFund.getId()));
    }

    @Test
    void withoutConfirmedOperatingFundListsAllUnflagged() {
        Condominium withoutOperatingFund = mock(Condominium.class);
        CondominiumRepository condominiums = mock(CondominiumRepository.class);
        when(condominiums.findById(condominiumId)).thenReturn(Optional.of(withoutOperatingFund));
        FundRepository funds = mock(FundRepository.class);
        when(funds.findByCondominiumId(condominiumId)).thenReturn(List.of(operatingFund, new Fund(condominiumId, "OBRAS")));
        logIn(condominiumId, "GESTOR");

        var list = new FundController(new CondominiumAccess(), new FundService(condominiums, funds)).list(condominiumId);

        assertThat(list).extracting(FundResponse::name).containsExactly("CONDOMÍNIO", "OBRAS");
        assertThat(list).noneMatch(FundResponse::operating);
    }

    @Test
    void otherCondominiumIsNotListed() {
        logIn(UUID.randomUUID(), "GESTOR");

        assertThatThrownBy(() -> controller.list(condominiumId)).isInstanceOf(AccessDeniedException.class);
    }

    private static void logIn(UUID condominium, String role) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("p").claim("condominios",
                List.of(condominium.toString())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), "p"));
    }
}
