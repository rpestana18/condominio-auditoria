package br.com.condominioauditoria.api.orcamento;

/**
 * Destino de uma conta do fluxo no de-para (RF-03.1.4, Termos): uma linha da PO; "Ajuste (não é despesa)";
 * "Meio de pagamento (a realocar, RF-02B)"; ou "Transferência entre fundos".
 */
public enum TipoDestino {
    LINHA_PO, AJUSTE, A_REALOCAR, TRANSFERENCIA
}
