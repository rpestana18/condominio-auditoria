package br.com.condominioauditoria.backend.orcamento;

/** Ciclo de vida da PO (ADR 0004, Decisão 3). */
public enum EstadoPrevisao {
    /** Lida e conferida; aguarda a confirmação do Admin. */
    LIDA,
    /** Alguma conferência de soma não bateu além da tolerância de arredondamento (RF-03.1.2). */
    LIDA_COM_DIVERGENCIA,
    /** Confirmada pelo Admin; vale para os meses do exercício. Não muda mais com reprocesso. */
    CONFIRMADA,
    /** Trocada por uma reaprovação a partir de um mês; continua valendo para os meses anteriores. */
    SUBSTITUIDA;

    /** PO confirmada (ou substituída) não aceita nova leitura: os números já podem ter sido exportados. */
    public boolean travada() {
        return this == CONFIRMADA || this == SUBSTITUIDA;
    }
}
