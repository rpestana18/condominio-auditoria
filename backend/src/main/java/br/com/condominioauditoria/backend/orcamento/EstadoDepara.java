package br.com.condominioauditoria.backend.orcamento;

/** Estado do de-para de uma conta. Só CONFIRMADO entra no previsto × realizado; sugestão nunca se confirma sozinha. */
public enum EstadoDepara {
    SUGERIDO, CONFIRMADO, RECUSADO
}
