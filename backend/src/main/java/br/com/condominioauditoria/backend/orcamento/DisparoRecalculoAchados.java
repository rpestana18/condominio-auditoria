package br.com.condominioauditoria.backend.orcamento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Roda o recálculo dos achados depois do commit da mudança, numa transação própria. Falha no recálculo não desfaz a
 * mudança (já gravada) e não volta como erro para quem fez a mudança: fica no log, e a próxima mudança recalcula tudo
 * de novo (o recálculo é idempotente).
 */
@Component
class DisparoRecalculoAchados {

    private static final Logger log = LoggerFactory.getLogger(DisparoRecalculoAchados.class);

    private final RecalculoAchadosOrcamento recalculo;
    private final TransactionTemplate transacao;

    DisparoRecalculoAchados(RecalculoAchadosOrcamento recalculo, PlatformTransactionManager transacoes) {
        this.recalculo = recalculo;
        this.transacao = new TransactionTemplate(transacoes);
        this.transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void aoMudar(MudancaOrcamento mudanca) {
        try {
            transacao.executeWithoutResult(s -> recalculo.recalcular(mudanca));
        } catch (RuntimeException e) {
            log.error("Recálculo dos achados do condomínio {} falhou ({}): {}", mudanca.condominioId(),
                    mudanca.gatilho().texto(), e.getMessage(), e);
        }
    }
}
