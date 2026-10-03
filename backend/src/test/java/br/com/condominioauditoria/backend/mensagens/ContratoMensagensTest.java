package br.com.condominioauditoria.backend.mensagens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.backend.arquivo.Arquivo;
import br.com.condominioauditoria.backend.arquivo.Categoria;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** O backend entende o exemplo do contrato (o mesmo que o rag produz) e só envia mensagens válidas. */
class ContratoMensagensTest {

    private final ContratoMensagens contrato = new ContratoMensagens();

    @Test
    void leOExemploDoContrato() throws Exception {
        byte[] json = Files.readAllBytes(Path.of(System.getProperty("contratos.dir"),
                "mensagens/v1/exemplos/resultado-concluido.json"));

        ResultadoProcessamento r = contrato.lerResultado(json);

        assertThat(r.situacao()).isEqualTo(ResultadoProcessamento.Situacao.CONCLUIDO);
        assertThat(r.fluxoDeCaixa().totalLancamentos()).isEqualTo(1);
        var lancamento = r.fluxoDeCaixa().secoes().getFirst().lancamentos().getFirst();
        assertThat(lancamento.debito()).isEqualByComparingTo("1500.10");
        assertThat(lancamento.debito().scale()).isEqualTo(2);
        assertThat(lancamento.enriquecimento().fornecedor()).isEqualTo("SABESP");
    }

    @Test
    void falhouSemMotivoEhRecusado() {
        String json = """
                {"versao":1,"processamentoId":"%s","arquivoId":"%s","condominioId":"%s","situacao":"FALHOU"}"""
                .formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> contrato.lerResultado(json.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("fora do contrato");
    }

    @Test
    void dinheiroComoNumeroEhRecusado() throws Exception {
        String json = Files.readString(Path.of(System.getProperty("contratos.dir"),
                "mensagens/v1/exemplos/resultado-concluido.json")).replace("\"1500.10\"", "1500.1");
        assertThatThrownBy(() -> contrato.lerResultado(json.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("fora do contrato");
    }

    @Test
    void pedidoDeLeituraSaiNoContrato() {
        var arquivo = new Arquivo(UUID.randomUUID(), Categoria.BALANCETE, "fluxo.pdf", "c/BALANCETE/2026/x-fluxo.pdf",
                "c".repeat(64), 10, "application/pdf", "gestor");
        String json = new String(contrato.escrever(ArquivoRecebido.de(arquivo)), StandardCharsets.UTF_8);
        assertThat(json).contains("\"categoria\":\"BALANCETE\"").contains(arquivo.getProcessamentoId().toString());
    }
}
