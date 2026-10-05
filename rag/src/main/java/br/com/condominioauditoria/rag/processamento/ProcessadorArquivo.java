package br.com.condominioauditoria.rag.processamento;

import br.com.condominioauditoria.armazenamento.Armazenamento;
import br.com.condominioauditoria.rag.dominio.fluxo.ConferenciaFluxo;
import br.com.condominioauditoria.rag.dominio.fluxo.FluxoDeCaixa;
import br.com.condominioauditoria.rag.dominio.po.ConferenciaPo;
import br.com.condominioauditoria.rag.dominio.po.PrevisaoOrcamentaria;
import br.com.condominioauditoria.rag.leitura.contrato.DocumentoLido;
import br.com.condominioauditoria.rag.leitura.fluxo.InterpretadorFluxoCaixa;
import br.com.condominioauditoria.rag.leitura.po.InterpretadorPoProtest;
import br.com.condominioauditoria.rag.mensagens.ArquivoRecebido;
import br.com.condominioauditoria.rag.mensagens.ContratoMensagens;
import br.com.condominioauditoria.rag.mensagens.Filas;
import br.com.condominioauditoria.rag.mensagens.ResultadoProcessamento;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

/**
 * Consome a fila de arquivos recebidos. Para cada arquivo: avisa que começou, lê o original, chama o leitor Python,
 * interpreta o layout (fluxo de caixa ou PO da Protest, escolhido pelo conteúdo), confere as somas e devolve tudo
 * numa mensagem só. Quem decide se a PO lida vale é o backend, pela categoria do arquivo (ADR 0004, Decisão 3).
 *
 * A mensagem só sai da fila (ack) quando o resultado foi publicado. Se o rag cair no meio, o RabbitMQ entrega o
 * arquivo de novo; o backend descarta resultado repetido ou velho pelo processamentoId.
 */
@Service
class ProcessadorArquivo {

    static final String INTERPRETADOR_FLUXO = "fluxo-caixa-protest";
    static final String INTERPRETADOR_PO = "po-protest";
    /** Categoria do arquivo de PO no backend; só usada para explicar a falha da PO escaneada. */
    static final String CATEGORIA_PO = "PO";

    private static final Logger log = LoggerFactory.getLogger(ProcessadorArquivo.class);

    private final ContratoMensagens contrato;
    private final RabbitTemplate rabbit;
    private final Armazenamento armazenamento;
    private final LeitorDocumentosHttp leitor;
    private final InterpretadorFluxoCaixa interpretadorFluxo;
    private final InterpretadorPoProtest interpretadorPo;

    ProcessadorArquivo(ContratoMensagens contrato, RabbitTemplate rabbit, Armazenamento armazenamento,
            LeitorDocumentosHttp leitor, InterpretadorFluxoCaixa interpretadorFluxo,
            InterpretadorPoProtest interpretadorPo) {
        this.contrato = contrato;
        this.rabbit = rabbit;
        this.armazenamento = armazenamento;
        this.leitor = leitor;
        this.interpretadorFluxo = interpretadorFluxo;
        this.interpretadorPo = interpretadorPo;
    }

    @RabbitListener(queues = Filas.ARQUIVOS_RECEBIDOS)
    void aoReceber(Message mensagem) {
        ArquivoRecebido arquivo = contrato.lerArquivoRecebido(mensagem.getBody());
        publicar(ResultadoProcessamento.iniciado(arquivo));
        ResultadoProcessamento resultado;
        try {
            resultado = processar(arquivo);
            log.info("Arquivo {} lido ({})", arquivo.nomeOriginal(),
                    resultado.interpretador() == null ? "layout ainda sem leitor" : resultado.interpretador());
        } catch (Exception erro) {
            log.warn("Falha ao ler {}: {}", arquivo.nomeOriginal(), erro.getMessage(), erro);
            resultado = ResultadoProcessamento.falhou(arquivo, motivoLegivel(erro));
        }
        publicar(resultado);
    }

    private ResultadoProcessamento processar(ArquivoRecebido arquivo) throws Exception {
        byte[] conteudo;
        try (InputStream entrada = armazenamento.abrir(arquivo.caminho())) {
            conteudo = entrada.readAllBytes();
        }
        return interpretar(arquivo, leitor.ler(arquivo.nomeOriginal(), conteudo));
    }

    ResultadoProcessamento interpretar(ArquivoRecebido arquivo, DocumentoLido documento) {
        int paginas = documento.paginas().size();
        if (interpretadorFluxo.reconhece(documento)) {
            FluxoDeCaixa fluxo = interpretadorFluxo.interpretar(documento);
            return ResultadoProcessamento.concluido(arquivo, INTERPRETADOR_FLUXO, paginas, fluxo,
                    ConferenciaFluxo.conferir(fluxo));
        }
        if (interpretadorPo.reconhece(documento)) {
            PrevisaoOrcamentaria po = interpretadorPo.interpretar(documento);
            return ResultadoProcessamento.concluidoPo(arquivo, INTERPRETADOR_PO, paginas, po, ConferenciaPo.conferir(po));
        }
        if (CATEGORIA_PO.equals(arquivo.categoria()) && InterpretadorPoProtest.semTexto(documento)) {
            return ResultadoProcessamento.falhou(arquivo, InterpretadorPoProtest.SEM_TEXTO);
        }
        return ResultadoProcessamento.concluido(arquivo, null, paginas, null, null);
    }

    private void publicar(ResultadoProcessamento resultado) {
        var propriedades = new MessageProperties();
        propriedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propriedades.setContentEncoding("UTF-8");
        rabbit.send("", Filas.RESULTADOS, new Message(contrato.escrever(resultado), propriedades));
    }

    private static String motivoLegivel(Exception erro) {
        String mensagem = erro.getMessage() == null ? erro.getClass().getSimpleName() : erro.getMessage();
        if (erro instanceof ResourceAccessException) {
            return "O leitor de documentos não respondeu. Ele está rodando? (" + mensagem + ")";
        }
        return mensagem.length() > 1000 ? mensagem.substring(0, 1000) + "…" : mensagem;
    }
}
