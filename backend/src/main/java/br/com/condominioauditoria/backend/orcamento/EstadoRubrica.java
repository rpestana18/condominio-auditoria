package br.com.condominioauditoria.backend.orcamento;

/** Estado da rubrica de uma linha da PO (RF-11.7): só CONFIRMADO entra na comparação por linha. */
public enum EstadoRubrica {
    SUGERIDO, CONFIRMADO, RECUSADO
}
