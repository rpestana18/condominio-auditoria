package br.com.condominioauditoria.rag.processamento;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.rag.leitura.contrato.ContratoLeitor;
import br.com.condominioauditoria.rag.leitura.contrato.DocumentoLido;
import br.com.condominioauditoria.rag.leitura.contrato.DocumentoLido.Pagina;
import br.com.condominioauditoria.rag.leitura.fluxo.InterpretadorFluxoCaixa;
import br.com.condominioauditoria.rag.leitura.po.InterpretadorPoProtest;
import br.com.condominioauditoria.rag.mensagens.ArquivoRecebido;
import br.com.condominioauditoria.rag.mensagens.ContratoMensagens;
import br.com.condominioauditoria.rag.mensagens.ResultadoProcessamento;
import br.com.condominioauditoria.rag.mensagens.ResultadoProcessamento.Situacao;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** O interpretador é escolhido pelo conteúdo do documento; a categoria só explica a PO escaneada. */
class ProcessadorArquivoTest {

    private final ProcessadorArquivo processador = new ProcessadorArquivo(new ContratoMensagens(), null, null, null,
            new InterpretadorFluxoCaixa(), new InterpretadorPoProtest());

    @Test
    void poEscaneadaFalhaComMotivoLegivel() {
        ResultadoProcessamento r = processador.interpretar(arquivo("PO"), escaneado());

        assertThat(r.situacao()).isEqualTo(Situacao.FALHOU);
        assertThat(r.motivo()).isEqualTo("PO sem texto; OCR ainda não disponível");
        assertThat(new ContratoMensagens().escrever(r)).isNotEmpty();
    }

    @Test
    void outroArquivoEscaneadoContinuaSemLeitor() {
        ResultadoProcessamento r = processador.interpretar(arquivo("CONTRATO"), escaneado());

        assertThat(r.situacao()).isEqualTo(Situacao.CONCLUIDO);
        assertThat(r.interpretador()).isNull();
        assertThat(r.previsaoOrcamentaria()).isNull();
        assertThat(r.fluxoDeCaixa()).isNull();
    }

    /** Com o golden privado: a PO real sai como PO na v2, e o fluxo real continua saindo como fluxo. */
    @Test
    void poEFluxoReaisPeloConteudo() throws Exception {
        Path privado = Path.of(System.getProperty("golden.dir"), "privado");
        Path po = privado.resolve("po-2026-2027.documento-lido.json");
        if (Files.exists(po)) {
            ResultadoProcessamento r = processador.interpretar(arquivo("PO"),
                    new ContratoLeitor().converter(Files.readString(po)));
            assertThat(r.interpretador()).isEqualTo("po-protest");
            assertThat(r.previsaoOrcamentaria().linhas()).hasSize(98);
            assertThat(r.fluxoDeCaixa()).isNull();
            assertThat(r.conferencias()).isNotEmpty();
            assertThat(new String(new ContratoMensagens().escrever(r), java.nio.charset.StandardCharsets.UTF_8))
                    .contains("\"versao\":2").contains("\"orcado\":\"1585.14\"");
        }
        Path fluxo = privado.resolve("fluxo-caixa-2026-09.documento-lido.json");
        if (Files.exists(fluxo)) {
            ResultadoProcessamento r = processador.interpretar(arquivo("BALANCETE"),
                    new ContratoLeitor().converter(Files.readString(fluxo)));
            assertThat(r.interpretador()).isEqualTo("fluxo-caixa-protest");
            assertThat(r.previsaoOrcamentaria()).isNull();
            assertThat(r.fluxoDeCaixa().totalLancamentos()).isEqualTo(423);
            assertThat(new String(new ContratoMensagens().escrever(r), java.nio.charset.StandardCharsets.UTF_8))
                    .contains("\"recebimentoCota\":true");
        }
    }

    private static DocumentoLido escaneado() {
        return new DocumentoLido("1", "leitor-py", new DocumentoLido.Arquivo("x.pdf", "a".repeat(64), 10), "pdf",
                List.of(new Pagina(1, 595, 842, "sem_texto", List.of())), List.of(), List.of());
    }

    private static ArquivoRecebido arquivo(String categoria) {
        return new ArquivoRecebido(1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), categoria, "x.pdf",
                "c/x.pdf", "a".repeat(64));
    }
}
