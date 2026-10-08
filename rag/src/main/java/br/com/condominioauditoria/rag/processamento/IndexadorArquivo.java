package br.com.condominioauditoria.rag.processamento;

import br.com.condominioauditoria.storage.Storage;
import br.com.condominioauditoria.rag.indice.CortadorTrechos;
import br.com.condominioauditoria.rag.indice.DocumentoCortado;
import br.com.condominioauditoria.rag.indice.EmbeddingsIndisponiveisException;
import br.com.condominioauditoria.rag.indice.GeradorEmbeddings;
import br.com.condominioauditoria.rag.indice.RepositorioIndice;
import br.com.condominioauditoria.rag.indice.RepositorioIndice.DocumentoIndexado;
import br.com.condominioauditoria.rag.indice.TrechoCortado;
import br.com.condominioauditoria.rag.leitura.contrato.DocumentoLido;
import br.com.condominioauditoria.rag.mensagens.ContratoMensagens;
import br.com.condominioauditoria.rag.mensagens.Filas;
import br.com.condominioauditoria.rag.mensagens.IndexarArquivo;
import br.com.condominioauditoria.rag.mensagens.ResultadoIndexacao;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

/**
 * Consome a fila rag.indexacao (ADR 0003, Decisão 5.1). INDEXAR: avisa que começou, lê o original, chama o leitor,
 * corta em trechos pela localização, gera os vetores no Ollama e grava tudo numa transação, substituindo o índice
 * anterior do arquivo. RETIRAR: exclusão lógica (nada é apagado).
 *
 * Fila própria, com paralelismo próprio: indexação lenta ou Ollama fora do ar não atrasam a leitura contábil. Ack só
 * depois de publicar o resultado; se o rag cair no meio, o RabbitMQ entrega de novo e o backend descarta resultado de
 * indexacaoId velho.
 */
@Service
class IndexadorArquivo {

    private static final Logger log = LoggerFactory.getLogger(IndexadorArquivo.class);

    private final ContratoMensagens contrato;
    private final RabbitTemplate rabbit;
    private final Storage armazenamento;
    private final LeitorDocumentosHttp leitor;
    private final GeradorEmbeddings embeddings;
    private final RepositorioIndice repositorio;

    IndexadorArquivo(ContratoMensagens contrato, RabbitTemplate rabbit, Storage armazenamento,
            LeitorDocumentosHttp leitor, GeradorEmbeddings embeddings, RepositorioIndice repositorio) {
        this.contrato = contrato;
        this.rabbit = rabbit;
        this.armazenamento = armazenamento;
        this.leitor = leitor;
        this.embeddings = embeddings;
        this.repositorio = repositorio;
    }

    @RabbitListener(queues = Filas.INDEXACAO, concurrency = "${rag.indexacao.paralelismo:1}")
    void aoReceber(Message mensagem) {
        IndexarArquivo pedido = contrato.lerIndexarArquivo(mensagem.getBody());
        if (pedido.operacao() == IndexarArquivo.Operacao.RETIRAR) {
            boolean existia = repositorio.retirar(pedido.arquivoId(), pedido.indexacaoId());
            log.info("Arquivo {} retirado do índice{}", pedido.nomeOriginal(), existia ? "" : " (não estava indexado)");
            publicar(ResultadoIndexacao.retirado(pedido));
            return;
        }
        Optional<ResultadoIndexacao> jaFeito = jaIndexado(pedido);
        if (jaFeito.isPresent()) {
            repositorio.confirmarSemReindexar(pedido);
            log.info("Arquivo {} já indexado com o mesmo conteúdo, modelo e versão; só confirmado",
                    pedido.nomeOriginal());
            publicar(jaFeito.get());
            return;
        }
        repositorio.marcarIndexando(pedido);
        publicar(ResultadoIndexacao.indexando(pedido));
        ResultadoIndexacao resultado;
        try {
            resultado = indexar(pedido);
            log.info("Arquivo {} indexado: {} ({} trechos)", pedido.nomeOriginal(), resultado.situacao(),
                    resultado.trechos());
        } catch (Exception erro) {
            log.warn("Falha ao indexar {}: {}", pedido.nomeOriginal(), erro.getMessage(), erro);
            String motivo = motivoLegivel(erro);
            repositorio.marcarErro(pedido.arquivoId(), pedido.indexacaoId(), motivo);
            resultado = ResultadoIndexacao.erro(pedido, motivo);
        }
        publicar(resultado);
    }

    /**
     * Idempotência (ADR 0003, Q14): mesmo arquivo, sha256, modelo e versão do indexador = não refaz. Religar o módulo
     * só indexa o que é novo ou mudou.
     */
    private Optional<ResultadoIndexacao> jaIndexado(IndexarArquivo pedido) {
        Optional<DocumentoIndexado> atual = repositorio.buscar(pedido.arquivoId());
        if (atual.isEmpty() || !pedido.sha256().equals(atual.get().sha256())
                || !CortadorTrechos.VERSAO.equals(atual.get().versaoIndexador())) {
            return Optional.empty();
        }
        DocumentoIndexado doc = atual.get();
        if ("sem_texto".equals(doc.estado())) { // sem texto não tem vetores: o modelo não importa
            return Optional.of(ResultadoIndexacao.semTexto(pedido, doc.motivo(), doc.paginas(), doc.versaoIndexador()));
        }
        if ("indexado".equals(doc.estado()) && Objects.equals(modeloDoPedido(pedido), doc.modeloEmbeddings())) {
            return Optional.of(ResultadoIndexacao.indexado(pedido, doc.paginas(), doc.trechos(),
                    doc.modeloEmbeddings(), doc.versaoIndexador()));
        }
        return Optional.empty();
    }

    /** Modelo que vai gerar os vetores; nulo quando os embeddings estão desligados para o condomínio. */
    private String modeloDoPedido(IndexarArquivo pedido) {
        return pedido.comVetores() ? embeddings.modelo() : null;
    }

    private ResultadoIndexacao indexar(IndexarArquivo pedido) throws Exception {
        if (pedido.comVetores() && !embeddings.aceita(pedido.modeloEmbeddings())) {
            throw new IllegalArgumentException("Modelo de embeddings " + pedido.modeloEmbeddings()
                    + " não está disponível neste rag (disponível: " + embeddings.modelo() + ")");
        }
        byte[] conteudo;
        try (InputStream entrada = armazenamento.open(pedido.caminho())) {
            conteudo = entrada.readAllBytes();
        }
        DocumentoLido documento = leitor.ler(pedido.nomeOriginal(), conteudo);
        DocumentoCortado cortado = CortadorTrechos.cortar(documento);
        if (cortado.semTexto()) {
            repositorio.substituir(pedido, cortado, null, null);
            return ResultadoIndexacao.semTexto(pedido, cortado.motivoSemTexto(), cortado.paginas(),
                    CortadorTrechos.VERSAO);
        }
        List<float[]> vetores = null;
        String modelo = modeloDoPedido(pedido);
        String aviso = null;
        if (modelo != null) {
            try {
                vetores = embeddings.gerar(cortado.trechos().stream().map(TrechoCortado::texto).toList());
            } catch (EmbeddingsIndisponiveisException erro) {
                // Sem vetores o arquivo ainda entra na busca por palavra. Fica gravado sem modelo, então o próximo
                // pedido com o Ollama de pé não é tratado como repetido e gera os vetores.
                aviso = limitar("Indexado só para a busca por palavra, sem busca por significado: "
                        + erro.getMessage());
                log.warn("Arquivo {} indexado sem vetores: {}", pedido.nomeOriginal(), erro.getMessage());
                modelo = null;
            }
        }
        repositorio.substituir(pedido, cortado, vetores, modelo, aviso);
        return ResultadoIndexacao.indexado(pedido, cortado.paginas(), cortado.trechos().size(), modelo,
                CortadorTrechos.VERSAO, aviso);
    }

    private void publicar(ResultadoIndexacao resultado) {
        var propriedades = new MessageProperties();
        propriedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propriedades.setContentEncoding("UTF-8");
        rabbit.send("", Filas.RESULTADOS_INDEXACAO, new Message(contrato.escrever(resultado), propriedades));
    }

    private static String motivoLegivel(Exception erro) {
        if (erro instanceof EmbeddingsIndisponiveisException) {
            return limitar(erro.getMessage());
        }
        String mensagem = erro.getMessage() == null ? erro.getClass().getSimpleName() : erro.getMessage();
        if (erro instanceof ResourceAccessException) {
            return limitar("O leitor de documentos não respondeu. Ele está rodando? (" + mensagem + ")");
        }
        return limitar(mensagem);
    }

    private static String limitar(String mensagem) {
        return mensagem.length() > 1000 ? mensagem.substring(0, 1000) + "…" : mensagem;
    }
}
