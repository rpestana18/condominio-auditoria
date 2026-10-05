package br.com.condominioauditoria.rag.assistente.pergunta;

import br.com.condominioauditoria.rag.indice.TrechoEncontrado;
import java.util.List;

/**
 * Resposta pronta e já conferida, do jeito que vai para o último evento do fluxo {@code Perguntar}.
 *
 * @param trechosCitados sem repetição, na ordem da primeira citação (o backend numera as citações nessa ordem)
 * @param aviso vazio quando não há nada a avisar (recusa do modelo, busca só por palavra)
 */
public record ResultadoPergunta(Situacao situacao, List<Paragrafo> nosDocumentos, List<Dado> nosDadosGravados,
        List<TrechoEncontrado> trechosCitados, String sugestao, String aviso, Uso uso) {

    public enum Situacao {
        RESPONDIDA, NAO_ENCONTRADA
    }

    public record Paragrafo(String texto, List<String> trechoIds) {
    }

    /** Bloco montado pelo rag a partir da ferramenta, com o comentário (sem número) do modelo. */
    public record Dado(DadoConsultado consultado, String comentario) {
    }

    /** Uso desta pergunta, somado em todas as tentativas e chamadas ao provedor (RF-09.7). */
    public record Uso(long tokensEntrada, long tokensSaida, String provedor, String modelo, String versaoPrompt,
            int tentativas) {
    }

    /** Recusa padrão do RF-04.12. */
    public static final String NAO_ENCONTREI = "Não encontrei nos documentos.";
}
