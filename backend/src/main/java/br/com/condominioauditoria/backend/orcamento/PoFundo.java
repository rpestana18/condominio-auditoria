package br.com.condominioauditoria.backend.orcamento;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.UUID;

/** Linha de fundo da PO (1.9.x) ligada pelo Admin a um fundo do fluxo (ADR 0004, Decisão 7). */
@Entity
public class PoFundo {

    @Id
    private UUID id;
    private UUID previsaoId;
    private UUID linhaPoId;
    private UUID fundoId;

    protected PoFundo() {
    }

    public PoFundo(UUID previsaoId, UUID linhaPoId, UUID fundoId) {
        this.id = UUID.randomUUID();
        this.previsaoId = previsaoId;
        this.linhaPoId = linhaPoId;
        this.fundoId = fundoId;
    }

    public UUID getPrevisaoId() {
        return previsaoId;
    }

    public UUID getLinhaPoId() {
        return linhaPoId;
    }

    public UUID getFundoId() {
        return fundoId;
    }
}
