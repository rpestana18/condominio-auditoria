package br.com.condominioauditoria.backend.orcamento;

import br.com.condominioauditoria.backend.auditoria.GatilhoRecalculo;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Mudança que afeta o previsto × realizado de um condomínio: fluxo gravado, PO confirmada, de-para alterado, fundos
 * ligados, realocação feita ou desfeita (ADR 0004, Decisão 5). Publicada dentro da transação da mudança; os achados
 * são recalculados depois do commit.
 */
public record MudancaOrcamento(UUID condominioId, GatilhoRecalculo gatilho) {

    public MudancaOrcamento {
        Objects.requireNonNull(condominioId, "condominioId");
        Objects.requireNonNull(gatilho, "gatilho");
    }

    public static MudancaOrcamento de(UUID condominioId, String descricao, String usuario, Instant em) {
        return new MudancaOrcamento(condominioId, new GatilhoRecalculo(descricao, usuario, em));
    }
}
