package br.com.condominioauditoria.rag.processamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.condominioauditoria.rag.leitura.contrato.ContratoLeitor;
import br.com.condominioauditoria.rag.leitura.fluxo.InterpretadorFluxoCaixa;
import br.com.condominioauditoria.rag.leitura.po.InterpretadorPoProtest;
import br.com.condominioauditoria.rag.mensagens.ArquivoRecebido;
import br.com.condominioauditoria.rag.mensagens.ContratoMensagens;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Mensagens ResultadoProcessamento v2 do golden privado (fluxo de setembro/2026 e PO 2026/2027), na forma em que o
 * rag as publica. São a entrada do golden do backend (ADR 0004, passos 6 e 7): o backend nunca importa classe do rag,
 * só lê a mensagem pelo contrato (contracts/mensagens/v2). Os ids são fixos para a saída ser sempre a mesma.
 *
 * Sem o arquivo da mensagem, o teste o grava; com ele, confere que a saída atual do rag é idêntica (mudou a leitura?
 * apague o arquivo, rode de novo e rode o golden do backend). Sem o golden privado, o teste é pulado.
 */
class ResultadoGoldenTest {

    static final UUID CONDOMINIO = UUID.fromString("00000000-0000-0000-0000-00000000c0d0");
    static final UUID ARQUIVO_FLUXO = UUID.fromString("00000000-0000-0000-0000-0000000f1009");
    static final UUID ARQUIVO_PO = UUID.fromString("00000000-0000-0000-0000-000000002627");

    private final ProcessadorArquivo processador = new ProcessadorArquivo(new ContratoMensagens(), null, null, null,
            new InterpretadorFluxoCaixa(), new InterpretadorPoProtest());

    @Test
    void fluxoDeSetembro() throws Exception {
        conferirOuGravar("fluxo-caixa-2026-09", ARQUIVO_FLUXO, "BALANCETE");
    }

    @Test
    void poDoExercicio() throws Exception {
        conferirOuGravar("po-2026-2027", ARQUIVO_PO, "PO");
    }

    private void conferirOuGravar(String nome, UUID arquivoId, String categoria) throws Exception {
        Path privado = Path.of(System.getProperty("golden.dir"), "privado");
        Path lido = privado.resolve(nome + ".documento-lido.json");
        assumeTrue(Files.exists(lido), "golden privado ausente");
        String sha = "0".repeat(64);
        var arquivo = new ArquivoRecebido(1, arquivoId, arquivoId, CONDOMINIO, categoria, nome + ".pdf",
                "golden/" + nome + ".pdf", sha);
        var resultado = processador.interpretar(arquivo, new ContratoLeitor().converter(Files.readString(lido)));
        String json = new String(new ContratoMensagens().escrever(resultado), StandardCharsets.UTF_8);

        Path mensagem = privado.resolve(nome + ".resultado-v2.json");
        if (!Files.exists(mensagem)) {
            Files.writeString(mensagem, json);
        }
        assertThat(Files.readString(mensagem)).as("saída do rag para " + nome).isEqualTo(json);
    }
}
