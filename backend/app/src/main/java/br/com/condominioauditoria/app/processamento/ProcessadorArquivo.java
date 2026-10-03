package br.com.condominioauditoria.app.processamento;

import br.com.condominioauditoria.app.arquivo.Arquivo;
import br.com.condominioauditoria.app.arquivo.ArquivoRepository;
import br.com.condominioauditoria.armazenamento.Armazenamento;
import br.com.condominioauditoria.dominio.fluxo.ConferenciaFluxo;
import br.com.condominioauditoria.dominio.fluxo.FluxoDeCaixa;
import br.com.condominioauditoria.ingestao.contrato.DocumentoLido;
import br.com.condominioauditoria.ingestao.fluxo.InterpretadorFluxoCaixa;
import java.io.InputStream;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Processa um arquivo em segundo plano. A parte demorada (leitura pelo Python e interpretação) acontece fora de
 * transação; só no fim os dados vão para o banco, de uma vez.
 */
@Service
public class ProcessadorArquivo {

    private static final Logger log = LoggerFactory.getLogger(ProcessadorArquivo.class);

    private final ArquivoRepository arquivos;
    private final Armazenamento armazenamento;
    private final LeitorDocumentosHttp leitor;
    private final InterpretadorFluxoCaixa interpretadorFluxo;
    private final StatusArquivoService status;
    private final GravacaoResultado gravacao;

    ProcessadorArquivo(ArquivoRepository arquivos, Armazenamento armazenamento, LeitorDocumentosHttp leitor,
            InterpretadorFluxoCaixa interpretadorFluxo, StatusArquivoService status, GravacaoResultado gravacao) {
        this.arquivos = arquivos;
        this.armazenamento = armazenamento;
        this.leitor = leitor;
        this.interpretadorFluxo = interpretadorFluxo;
        this.status = status;
        this.gravacao = gravacao;
    }

    @Async("processamentoExecutor")
    public void processar(UUID arquivoId) {
        Arquivo arquivo = arquivos.findById(arquivoId).orElse(null);
        if (arquivo == null) {
            return;
        }
        status.processando(arquivoId);
        try {
            byte[] conteudo;
            try (InputStream entrada = armazenamento.abrir(arquivo.getCaminho())) {
                conteudo = entrada.readAllBytes();
            }
            DocumentoLido documento = leitor.ler(arquivo.getNomeOriginal(), conteudo);
            ResultadoLeitura resultado = interpretar(documento);
            gravacao.gravar(arquivoId, resultado);
            log.info("Arquivo {} processado ({})", arquivo.getNomeOriginal(), resultado.getClass().getSimpleName());
        } catch (Exception erro) {
            log.warn("Falha ao processar {}: {}", arquivo.getNomeOriginal(), erro.getMessage(), erro);
            status.falhou(arquivoId, motivoLegivel(erro));
        }
    }

    private ResultadoLeitura interpretar(DocumentoLido documento) {
        if (interpretadorFluxo.reconhece(documento)) {
            FluxoDeCaixa fluxo = interpretadorFluxo.interpretar(documento);
            return new ResultadoLeitura.Fluxo(fluxo, ConferenciaFluxo.conferir(fluxo));
        }
        return new ResultadoLeitura.SemInterpretador(documento.paginas().size());
    }

    private static String motivoLegivel(Exception erro) {
        String mensagem = erro.getMessage() == null ? erro.getClass().getSimpleName() : erro.getMessage();
        if (erro instanceof org.springframework.web.client.ResourceAccessException) {
            return "O leitor de documentos não respondeu. Ele está rodando? (" + mensagem + ")";
        }
        return mensagem.length() > 1000 ? mensagem.substring(0, 1000) + "…" : mensagem;
    }
}
