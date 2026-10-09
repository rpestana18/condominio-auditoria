package br.com.condominioauditoria.api.assistente;

import br.com.condominioauditoria.api.arquivo.Categoria;
import br.com.condominioauditoria.contratos.assistente.v1.Localizacao;
import br.com.condominioauditoria.contratos.assistente.v1.Trecho;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Formatos da API do assistente (contracts/openapi.yaml: PedidoPergunta, RespostaAssistente, TrechoDocumento...). */
final class DtosAssistente {

    private DtosAssistente() {
    }

    /** Filtros opcionais (RF-04.10), aplicados pelo rag antes da busca. */
    record FiltrosDocumentos(List<Categoria> categorias, LocalDate dataInicio, LocalDate dataFim,
            List<UUID> arquivoIds) {
    }

    record TrocaConversa(String pergunta, String resposta) {
    }

    record PedidoPergunta(String pergunta, List<TrocaConversa> historico, FiltrosDocumentos filtros) {
    }

    record PedidoBuscaDocumentos(String texto, FiltrosDocumentos filtros, Integer limite) {
    }

    enum SituacaoResposta {
        RESPONDIDA, NAO_ENCONTRADA
    }

    record ParagrafoDocumentos(String texto, List<Integer> citacoes) {
    }

    record ParametroConsulta(String nome, String valor) {
    }

    record LinhaDado(String rotulo, String valor) {
    }

    record DadoGravado(String consulta, List<ParametroConsulta> parametros, List<LinhaDado> linhas,
            String comentario) {
    }

    record LocalizacaoTrecho(String tipo, Integer pagina, String aba, Integer linhaInicio, Integer linhaFim,
            Integer paragrafoInicio, Integer paragrafoFim, String secao, String descricao) {

        static LocalizacaoTrecho de(Localizacao l) {
            return switch (l.getTipoCase()) {
                case PAGINA -> new LocalizacaoTrecho("PAGINA", l.getPagina().getPagina(), null, null, null, null, null,
                        null, "página " + l.getPagina().getPagina());
                case PLANILHA -> {
                    var p = l.getPlanilha();
                    yield new LocalizacaoTrecho("PLANILHA", null, p.getAba(), p.getLinhaInicio(), p.getLinhaFim(), null,
                            null, null, "aba " + p.getAba() + ", " + intervalo("linha", "linhas", p.getLinhaInicio(),
                                    p.getLinhaFim()));
                }
                case PARAGRAFOS -> {
                    var p = l.getParagrafos();
                    String secao = p.getSecao().isBlank() ? null : p.getSecao();
                    String descricao = intervalo("parágrafo", "parágrafos", p.getParagrafoInicio(),
                            p.getParagrafoFim());
                    yield new LocalizacaoTrecho("PARAGRAFOS", null, null, null, null, p.getParagrafoInicio(),
                            p.getParagrafoFim(), secao, secao == null ? descricao : "seção " + secao + ", " + descricao);
                }
                case TIPO_NOT_SET -> new LocalizacaoTrecho("PAGINA", null, null, null, null, null, null, null,
                        "localização não informada");
            };
        }

        private static String intervalo(String um, String varios, int inicio, int fim) {
            return fim <= inicio ? um + " " + inicio : varios + " " + inicio + " a " + fim;
        }
    }

    /** Trecho citável (TrechoDocumento). O texto é transcrição literal do documento, não conferida. */
    record TrechoDocumento(String trechoId, UUID arquivoId, String nomeArquivo, Categoria categoria,
            LocalizacaoTrecho localizacao, String texto, String sha256) {

        static TrechoDocumento de(Trecho t) {
            return new TrechoDocumento(t.getTrechoId(), UUID.fromString(t.getArquivoId().strip()), t.getNomeArquivo(),
                    DtosAssistente.categoria(t.getCategoria()), LocalizacaoTrecho.de(t.getLocalizacao()), t.getTexto(), t.getSha256());
        }
    }

    /** CitacaoDocumento: TrechoDocumento + numero (a partir de 1). */
    record CitacaoDocumento(int numero, String trechoId, UUID arquivoId, String nomeArquivo, Categoria categoria,
            LocalizacaoTrecho localizacao, String texto, String sha256) {

        static CitacaoDocumento de(int numero, Trecho t) {
            TrechoDocumento d = TrechoDocumento.de(t);
            return new CitacaoDocumento(numero, d.trechoId(), d.arquivoId(), d.nomeArquivo(), d.categoria(),
                    d.localizacao(), d.texto(), d.sha256());
        }
    }

    record RespostaAssistente(SituacaoResposta situacao, List<ParagrafoDocumentos> nosDocumentos,
            List<DadoGravado> nosDadosGravados, List<CitacaoDocumento> citacoes, String sugestao, String aviso,
            String modelo) {
    }

    /** Categoria do rag; fora da lista = OUTROS (o contrato só tem as categorias do backend). */
    static Categoria categoria(String valor) {
        try {
            return Categoria.valueOf(valor.strip().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            return Categoria.OUTROS;
        }
    }
}
