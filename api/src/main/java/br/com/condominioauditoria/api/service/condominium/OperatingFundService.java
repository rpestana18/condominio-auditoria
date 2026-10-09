package br.com.condominioauditoria.api.service.condominium;

import br.com.condominioauditoria.api.model.accounting.Fund;
import br.com.condominioauditoria.api.model.condominium.Condominium;
import br.com.condominioauditoria.api.repository.accounting.FundRepository;
import br.com.condominioauditoria.api.repository.condominium.CondominiumRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** The Manager or the Admin confirms which fund is the condominium's operating fund (RF-05.1b). */
@Service
public class OperatingFundService {

    private static final Logger log = LoggerFactory.getLogger(OperatingFundService.class);

    private final CondominiumRepository condominiums;
    private final FundRepository funds;

    OperatingFundService(CondominiumRepository condominiums, FundRepository funds) {
        this.condominiums = condominiums;
        this.funds = funds;
    }

    @Transactional
    public void confirm(UUID condominiumId, UUID fundId, String username) {
        Fund fund = funds.findById(fundId)
                .filter(f -> f.getCondominiumId().equals(condominiumId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fundo não encontrado"));
        Condominium condominium = condominiums.findById(condominiumId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Condomínio não encontrado"));
        UUID previous = condominium.getOperatingFundId();
        condominium.confirmOperatingFund(fund.getId(), username, Instant.now());
        // Until the audit trail (RF-07.4) exists, the record stays in the log and in the confirmation columns
        log.info("Fundo ordinário confirmado: condominio={} fundo={} ({}) anterior={} por={}",
                condominiumId, fund.getId(), fund.getName(), previous, username);
    }
}
