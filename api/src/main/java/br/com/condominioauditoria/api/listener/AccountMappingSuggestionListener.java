package br.com.condominioauditoria.api.listener;

import br.com.condominioauditoria.api.event.BudgetChanged;
import br.com.condominioauditoria.api.service.budget.AccountMappingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Applies the automatic account mapping by itself (Q51, Q54; ADR 0009, Decision 8) after a change is committed: budget
 * confirmed, cash flow or support sheet saved, mapping changed. Runs in its own transaction; a failure goes to the log
 * and does not undo the change. The generation is idempotent (it writes only what changed, never touches the Admin's
 * choice) and publishes nothing, so it never loops.
 */
@Component
class AccountMappingSuggestionListener {

    private static final Logger log = LoggerFactory.getLogger(AccountMappingSuggestionListener.class);

    private final AccountMappingService accountMappings;
    private final TransactionTemplate transaction;

    AccountMappingSuggestionListener(AccountMappingService accountMappings, PlatformTransactionManager transactions) {
        this.accountMappings = accountMappings;
        this.transaction = new TransactionTemplate(transactions);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onBudgetChanged(BudgetChanged change) {
        try {
            transaction.executeWithoutResult(s -> accountMappings.suggestPending(change.condominiumId()));
        } catch (RuntimeException e) {
            log.error("De-para automático do condomínio {} falhou ({}): {}", change.condominiumId(),
                    change.trigger().text(), e.getMessage(), e);
        }
    }
}
