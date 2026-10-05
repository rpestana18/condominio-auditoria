package br.com.condominioauditoria.rag.assistente.pergunta;

import java.util.List;

/**
 * Resultado de uma ferramenta numérica já formatado pelo rag, do jeito que vai para o bloco "Nos dados gravados"
 * (RF-04.13, RF-04.14). O modelo nunca escreve nada daqui: só referencia {@link #chamadaId()}.
 *
 * @param chamadaId identificador da chamada nesta pergunta ("c1", "c2", ...)
 * @param consulta nome da ferramenta (resumo_fundos, buscar_lancamentos, listar_arquivos, conferencias_do_arquivo)
 * @param parametros filtros usados, na ordem em que foram passados
 * @param linhas linhas prontas para exibir; dinheiro em reais no padrão brasileiro
 */
public record DadoConsultado(String chamadaId, String consulta, List<Parametro> parametros, List<Linha> linhas) {

    public record Parametro(String nome, String valor) {
    }

    public record Linha(String rotulo, String valor) {
    }
}
