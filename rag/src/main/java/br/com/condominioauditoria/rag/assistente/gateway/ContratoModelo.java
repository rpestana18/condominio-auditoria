package br.com.condominioauditoria.rag.assistente.gateway;

import java.util.List;
import java.util.Map;

/**
 * Tipos neutros do ai-gateway (RF-09.3): é por aqui que o resto do rag conversa com qualquer modelo. Nenhuma classe
 * de SDK de provedor atravessa esta fronteira, e só o gateway recebe a chave de API aberta.
 */
public final class ContratoModelo {

    private ContratoModelo() {
    }

    /**
     * Uma ferramenta oferecida ao modelo.
     *
     * @param esquemaEntrada esquema JSON dos argumentos ({@code properties}, {@code required})
     */
    public record DefinicaoFerramenta(String nome, String descricao, Map<String, Object> esquemaEntrada) {
    }

    /** O modelo pediu uma ferramenta. */
    public record ChamadaFerramenta(String id, String nome, Map<String, Object> argumentos) {
    }

    /** Resultado devolvido ao modelo. {@code erro} marca a chamada como falha (is_error). */
    public record ResultadoFerramenta(String id, String conteudo, boolean erro) {
    }

    /** Por que o modelo parou. */
    public enum Parada {
        /** Resposta final pronta (end_turn, stop_sequence). */
        FIM,
        /** Pediu ao menos uma ferramenta. */
        FERRAMENTA,
        /** Recusa de segurança do modelo (stop_reason = refusal): vira NAO_ENCONTRADA com aviso, sem nova tentativa. */
        RECUSA,
        /** Teto de tokens ou janela de contexto: tratado como falha de redação. */
        CORTADA
    }

    /**
     * Uma volta de conversa com o modelo.
     *
     * @param textoJson texto da resposta (JSON do esquema) quando {@link Parada#FIM}
     * @param chamadas ferramentas pedidas quando {@link Parada#FERRAMENTA}
     * @param explicacaoRecusa explicação da recusa, quando houver
     */
    public record Turno(Parada parada, String textoJson, List<ChamadaFerramenta> chamadas, String explicacaoRecusa) {
    }

    /**
     * Parâmetros de uma pergunta ao provedor. A chave de API fica só aqui e no cliente criado para esta pergunta.
     *
     * @param esforco {@code output_config.effort}; nulo ou vazio = não enviar (o Haiku 4.5 recusa effort)
     * @param esquemaSaida esquema JSON pedido em {@code output_config.format}
     */
    public record Parametros(String tipoProvedor, String modelo, String chaveApi, String instrucoes, int maxTokens,
            String esforco, Map<String, Object> esquemaSaida, List<DefinicaoFerramenta> ferramentas) {
    }

    /** Falha do provedor já classificada; o gRPC traduz em status da especificação. */
    public static class ErroProvedorException extends RuntimeException {

        public enum Tipo {
            /** HTTP 401 ou 403: a chave do condomínio foi recusada. */
            CHAVE_RECUSADA,
            /** HTTP 429. */
            LIMITE,
            /** 5xx, falha de conexão ou prazo estourado. */
            INDISPONIVEL,
            /** Pedido recusado pelo provedor (400): erro nosso, não do usuário. */
            PEDIDO_INVALIDO
        }

        private final Tipo tipo;

        public ErroProvedorException(Tipo tipo, String mensagem, Throwable causa) {
            super(mensagem, causa);
            this.tipo = tipo;
        }

        public Tipo tipo() {
            return tipo;
        }
    }
}
