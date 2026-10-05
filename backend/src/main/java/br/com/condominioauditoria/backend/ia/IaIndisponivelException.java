package br.com.condominioauditoria.backend.ia;

/** O rag não respondeu ao que a configuração de IA precisa (catálogo ou chave pública). Vira 503 na API. */
public class IaIndisponivelException extends RuntimeException {

    public IaIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
