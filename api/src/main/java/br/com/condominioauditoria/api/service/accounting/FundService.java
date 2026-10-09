package br.com.condominioauditoria.api.service.accounting;

import br.com.condominioauditoria.api.dto.response.accounting.FundResponse;
import br.com.condominioauditoria.api.mapper.FundMapper;
import br.com.condominioauditoria.api.model.accounting.Fund;
import br.com.condominioauditoria.api.repository.accounting.FundRepository;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The condominium's cash flow funds, by the exact name printed in the cash flow (RF-03.1.9): the list used to link the
 * 1.9 lines and for the fund filter. "OBRAS" and "OBRAS / REFORMAS / INFRA" are separate items.
 */
@Service
public class FundService {

    private final CondominiumRepository condominiums;
    private final FundRepository funds;

    public FundService(CondominiumRepository condominiums, FundRepository funds) {
        this.condominiums = condominiums;
        this.funds = funds;
    }

    /** Sorted by name, with the confirmed operating fund flagged. */
    @Transactional(readOnly = true)
    public List<FundResponse> list(UUID condominiumId) {
        // With no confirmed operating fund every item comes out unflagged (this is the list the Manager picks it from)
        UUID operatingFund = condominiums.findById(condominiumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"))
                .getOperatingFundId();
        return funds.findByCondominiumId(condominiumId).stream()
                .sorted(Comparator.comparing(Fund::getName))
                .map(f -> FundMapper.toResponse(f, f.getId().equals(operatingFund)))
                .toList();
    }
}
