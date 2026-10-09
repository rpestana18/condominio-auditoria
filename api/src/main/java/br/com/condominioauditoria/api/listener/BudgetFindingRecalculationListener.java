package br.com.condominioauditoria.api.listener;

import br.com.condominioauditoria.api.event.BudgetChanged;
import br.com.condominioauditoria.api.service.audit.BudgetFindingRecalculationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs the findings recalculation after the change is committed, in its own transaction. A recalculation failure does
 * not undo the change (already saved) and does not come back as an error to whoever made it: it goes to the log, and
 * the next change recalculates everything again (the recalculation is idempotent).
 */
@Component
class BudgetFindingRecalculationListener {

    private static final Logger log = LoggerFactory.getLogger(BudgetFindingRecalculationListener.class);

    private final BudgetFindingRecalculationService recalculation;
    private final TransactionTemplate transaction;

    BudgetFindingRecalculationListener(BudgetFindingRecalculationService recalculation,
            PlatformTransactionManager transactions) {
        this.recalculation = recalculation;
        this.transaction = new TransactionTemplate(transactions);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onBudgetChanged(BudgetChanged change) {
        try {
            transaction.executeWithoutResult(s -> recalculation.recalculate(change));
        } catch (RuntimeException e) {
            log.error("Recálculo dos achados do condomínio {} falhou ({}): {}", change.condominiumId(),
                    change.trigger().text(), e.getMessage(), e);
        }
    }
}
