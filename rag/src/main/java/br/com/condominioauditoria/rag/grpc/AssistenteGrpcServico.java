package br.com.condominioauditoria.rag.grpc;

import br.com.condominioauditoria.contratos.assistente.v1.AssistenteGrpc;
import br.com.condominioauditoria.contratos.assistente.v1.BuscarRequest;
import br.com.condominioauditoria.contratos.assistente.v1.BuscarResponse;
import br.com.condominioauditoria.contratos.assistente.v1.FiltrosBusca;
import br.com.condominioauditoria.contratos.assistente.v1.LocalPagina;
import br.com.condominioauditoria.contratos.assistente.v1.LocalParagrafos;
import br.com.condominioauditoria.contratos.assistente.v1.LocalPlanilha;
import br.com.condominioauditoria.contratos.assistente.v1.ModoBusca;
import br.com.condominioauditoria.contratos.assistente.v1.Trecho;
import br.com.condominioauditoria.rag.indice.BuscaDocumentos;
import br.com.condominioauditoria.rag.indice.GeradorEmbeddings;
import br.com.condominioauditoria.rag.indice.Localizacao;
import br.com.condominioauditoria.rag.indice.RepositorioIndice;
import br.com.condominioauditoria.rag.indice.TrechoEncontrado;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementação de contracts/grpc/assistente/v1. Entrega 1: só Buscar. Valida a entrada (INVALID_ARGUMENT com motivo
 * legível), converte para a busca do índice e devolve os trechos citáveis na ordem de relevância.
 */
@Component
class AssistenteGrpcServico extends AssistenteGrpc.AssistenteImplBase {

    private static final Logger log = LoggerFactory.getLogger(AssistenteGrpcServico.class);

    private final BuscaDocumentos busca;
    private final GeradorEmbeddings embeddings;

    AssistenteGrpcServico(BuscaDocumentos busca, GeradorEmbeddings embeddings) {
        this.busca = busca;
        this.embeddings = embeddings;
    }

    @Override
    public void buscar(BuscarRequest pedido, StreamObserver<BuscarResponse> resposta) {
        RepositorioIndice.FiltrosBusca filtros;
        BuscaDocumentos.Modo modo;
        try {
            if (pedido.getTexto().isBlank()) {
                throw new IllegalArgumentException("Texto da busca é obrigatório");
            }
            if (pedido.getLimite() < 0) {
                throw new IllegalArgumentException("Limite não pode ser negativo");
            }
            if (!embeddings.aceita(pedido.getModeloEmbeddings())) {
                throw new IllegalArgumentException("Modelo de embeddings " + pedido.getModeloEmbeddings()
                        + " não está disponível neste rag (disponível: " + embeddings.modelo() + ")");
            }
            filtros = filtros(pedido);
            modo = pedido.getModo() == ModoBusca.MODO_BUSCA_PALAVRA ? BuscaDocumentos.Modo.PALAVRA
                    : BuscaDocumentos.Modo.HIBRIDA;
        } catch (IllegalArgumentException erro) {
            resposta.onError(Status.INVALID_ARGUMENT.withDescription(erro.getMessage()).asRuntimeException());
            return;
        }
        try {
            BuscaDocumentos.Resultado resultado = busca.buscar(filtros, pedido.getTexto(), modo, pedido.getLimite());
            var saida = BuscarResponse.newBuilder()
                    .setModoUsado(resultado.modoUsado() == BuscaDocumentos.Modo.PALAVRA ? ModoBusca.MODO_BUSCA_PALAVRA
                            : ModoBusca.MODO_BUSCA_HIBRIDA);
            resultado.trechos().forEach(t -> saida.addTrechos(trecho(t)));
            resposta.onNext(saida.build());
            resposta.onCompleted();
        } catch (RuntimeException erro) {
            log.warn("Falha na busca do condomínio {}: {}", pedido.getCondominioId(), erro.getMessage(), erro);
            resposta.onError(Status.INTERNAL.withDescription("Falha na busca nos documentos").asRuntimeException());
        }
    }

    private static RepositorioIndice.FiltrosBusca filtros(BuscarRequest pedido) {
        UUID condominio = uuid(pedido.getCondominioId(), "condominio_id");
        FiltrosBusca f = pedido.getFiltros();
        LocalDate inicio = data(f.getDataInicio(), "data_inicio");
        LocalDate fim = data(f.getDataFim(), "data_fim");
        if (inicio != null && fim != null && inicio.isAfter(fim)) {
            throw new IllegalArgumentException("data_inicio depois de data_fim");
        }
        List<UUID> arquivos = f.getArquivoIdsList().stream().map(id -> uuid(id, "arquivo_ids")).toList();
        List<String> categorias = f.getCategoriasList().stream().filter(c -> !c.isBlank()).toList();
        return new RepositorioIndice.FiltrosBusca(condominio, categorias, inicio, fim, arquivos);
    }

    private static UUID uuid(String valor, String campo) {
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException erro) {
            throw new IllegalArgumentException(campo + " inválido: \"" + valor + "\"");
        }
    }

    private static LocalDate data(String valor, String campo) {
        if (valor.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException erro) {
            throw new IllegalArgumentException(campo + " fora do formato AAAA-MM-DD: \"" + valor + "\"");
        }
    }

    static Trecho trecho(TrechoEncontrado t) {
        var local = br.com.condominioauditoria.contratos.assistente.v1.Localizacao.newBuilder();
        switch (t.localizacao()) {
            case Localizacao.Pagina l -> local.setPagina(LocalPagina.newBuilder().setPagina(l.numero()));
            case Localizacao.Planilha l -> local.setPlanilha(LocalPlanilha.newBuilder().setAba(l.aba())
                    .setLinhaInicio(l.linhaInicio()).setLinhaFim(l.linhaFim()));
            case Localizacao.Paragrafos l -> local.setParagrafos(LocalParagrafos.newBuilder()
                    .setParagrafoInicio(l.inicio()).setParagrafoFim(l.fim())
                    .setSecao(l.secao() == null ? "" : l.secao()));
        }
        return Trecho.newBuilder()
                .setTrechoId(t.trechoId().toString())
                .setArquivoId(t.arquivoId().toString())
                .setNomeArquivo(t.nomeArquivo())
                .setCategoria(t.categoria())
                .setLocalizacao(local)
                .setTexto(t.texto())
                .setPontuacao(t.pontuacao())
                .setSha256(t.sha256())
                .build();
    }
}
