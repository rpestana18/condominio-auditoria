package br.com.condominioauditoria.api.service.dashboard;

import br.com.condominioauditoria.api.dto.response.dashboard.UtilityAccountResponse;
import br.com.condominioauditoria.api.mapper.DashboardMapper;
import br.com.condominioauditoria.api.model.accounting.UtilityAccount;
import br.com.condominioauditoria.api.model.enums.UtilityAccountStatus;
import br.com.condominioauditoria.api.model.enums.UtilityType;
import br.com.condominioauditoria.api.repository.accounting.LedgerEntryRepository;
import br.com.condominioauditoria.api.repository.accounting.UtilityAccountRepository;
import br.com.condominioauditoria.api.service.calculator.UtilitySuggestion;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Utility accounts of a condominium (RF-12.1): the system suggests by the account name and the Manager or the Admin
 * confirms, changes or rejects. Only confirmed accounts enter the charts. Codes change from one property manager to
 * another, so each account of each layout is decided once.
 */
@Service
public class UtilityAccountService {

    private static final Logger log = LoggerFactory.getLogger(UtilityAccountService.class);

    private final LedgerEntryRepository entries;
    private final UtilityAccountRepository accounts;

    UtilityAccountService(LedgerEntryRepository entries, UtilityAccountRepository accounts) {
        this.entries = entries;
        this.accounts = accounts;
    }

    /** Every account with outflows; suggested and decided ones first, then by code. */
    @Transactional(readOnly = true)
    public List<UtilityAccountResponse> list(UUID condominiumId) {
        Map<String, UtilityAccount> decided = decisions(condominiumId);
        return entries.debitAccounts(condominiumId).stream()
                .map(a -> DashboardMapper.toUtilityAccount(a.getCode(), a.getName(), a.getDebits(),
                        UtilitySuggestion.suggest(a.getName()).orElse(null), decided.get(a.getCode())))
                .sorted(Comparator.comparing((UtilityAccountResponse r) -> r.status() == UtilityAccountStatus.NONE)
                        .thenComparing(UtilityAccountResponse::accountCode))
                .toList();
    }

    /** Confirmed accounts and their utility (input of the charts). */
    @Transactional(readOnly = true)
    public Map<String, UtilityType> confirmed(UUID condominiumId) {
        return accounts.findByCondominiumId(condominiumId).stream().filter(a -> a.getUtilityType() != null)
                .collect(Collectors.toMap(UtilityAccount::getAccountCode, UtilityAccount::getUtilityType));
    }

    /** Accounts suggested as utilities that nobody decided yet. */
    @Transactional(readOnly = true)
    public int pending(UUID condominiumId) {
        Map<String, UtilityAccount> decided = decisions(condominiumId);
        return (int) entries.debitAccounts(condominiumId).stream()
                .filter(a -> !decided.containsKey(a.getCode()) && UtilitySuggestion.suggest(a.getName()).isPresent())
                .count();
    }

    /** Confirms the account as {@code type}, or rejects it with null. */
    @Transactional
    public void decide(UUID condominiumId, String accountCode, UtilityType type, String username) {
        var account = entries.debitAccounts(condominiumId).stream().filter(a -> a.getCode().equals(accountCode))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada nos fluxos"));
        UtilityAccount decision = accounts.findByCondominiumIdAndAccountCode(condominiumId, accountCode)
                .orElseGet(() -> new UtilityAccount(condominiumId, accountCode, account.getName()));
        UtilityType previous = decision.getUtilityType();
        decision.decide(type, username, Instant.now());
        accounts.save(decision);
        // Until the audit trail (RF-07.4) exists, the record stays in the log and in the decision columns
        log.info("Conta de concessionária decidida: condominio={} conta={} ({}) tipo={} anterior={} por={}",
                condominiumId, accountCode, account.getName(), type, previous, username);
    }

    private Map<String, UtilityAccount> decisions(UUID condominiumId) {
        return accounts.findByCondominiumId(condominiumId).stream()
                .collect(Collectors.toMap(UtilityAccount::getAccountCode, Function.identity()));
    }
}
