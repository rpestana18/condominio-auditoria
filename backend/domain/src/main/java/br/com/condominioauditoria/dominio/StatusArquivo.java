package br.com.condominioauditoria.dominio;

/** Ciclo de vida do processamento de um arquivo enviado. */
public enum StatusArquivo {
    /** Recebido e aguardando a fila. */
    PENDENTE,
    /** Em leitura e gravação. */
    PROCESSANDO,
    /** Dados gravados e todas as conferências passaram. */
    CONCLUIDO,
    /** Dados gravados, mas alguma conferência falhou: precisa de olho humano. */
    PRECISA_REVISAO,
    /** Nada foi gravado (rollback). A mensagem de erro diz o motivo. */
    FALHOU
}
