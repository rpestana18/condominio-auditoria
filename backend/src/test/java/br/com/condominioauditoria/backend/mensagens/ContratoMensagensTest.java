package br.com.condominioauditoria.backend.mensagens;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.backend.arquivo.Arquivo;
import br.com.condominioauditoria.backend.arquivo.Categoria;
import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento.LancamentoLido;
import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento.LinhaPoLida;
import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento.MarcaPo;
import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento.TipoLinhaPo;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * O backend entende os exemplos do contrato v2 (os mesmos que o rag produz), recusa a v1 e só envia mensagens
 * válidas.
 */
class ContratoMensagensTest {

    private final ContratoMensagens contrato = new ContratoMensagens();

    static byte[] exemplo(String versao, String nome) throws Exception {
        return Files.readAllBytes(Path.of(System.getProperty("contratos.dir"), "mensagens", versao, "exemplos", nome));
    }

    @Test
    void leOExemploDeFluxoComRecebimentoDeCota() throws Exception {
        ResultadoProcessamento r = contrato.lerResultado(exemplo("v2", "resultado-concluido-fluxo.json"));

        assertThat(r.versao()).isEqualTo(2);
        assertThat(r.situacao()).isEqualTo(ResultadoProcessamento.Situacao.CONCLUIDO);
        assertThat(r.previsaoOrcamentaria()).isNull();
        assertThat(r.fluxoDeCaixa().totalLancamentos()).isEqualTo(2);
        var agua = r.fluxoDeCaixa().secoes().getFirst().lancamentos().getFirst();
        assertThat(agua.debito()).isEqualByComparingTo("1500.10");
        assertThat(agua.debito().scale()).isEqualTo(2);
        assertThat(agua.enriquecimento().fornecedor()).isEqualTo("SABESP");
        assertThat(agua.enriquecimento().recebimentoCota()).isFalse();
        LancamentoLido cota = r.fluxoDeCaixa().secoes().get(1).lancamentos().getFirst();
        assertThat(cota.historico()).isEqualTo("RECIBOS ACUMULADOS");
        assertThat(cota.credito()).isEqualByComparingTo("14260.79");
        assertThat(cota.enriquecimento().recebimentoCota()).isTrue();
    }

    @Test
    void leOExemploDaPoLida() throws Exception {
        ResultadoProcessamento r = contrato.lerResultado(exemplo("v2", "resultado-concluido-po.json"));

        assertThat(r.fluxoDeCaixa()).isNull();
        var po = r.previsaoOrcamentaria();
        assertThat(po.exercicioImpresso()).isEqualTo("2026 / 2027");
        assertThat(po.colunasOrcado()).containsExactly("2025/2026", "2026/2027");
        assertThat(po.linhas().getFirst().tipo()).isEqualTo(TipoLinhaPo.TOTAL);
        assertThat(po.linhas().getFirst().orcado()).isEqualByComparingTo("474201.13");
        LinhaPoLida sindico = linha(po, "1.3.20");
        assertThat(sindico.conta()).isEqualTo("1682 - Sindicatura Profissional");
        assertThat(sindico.orcadoAnterior()).isEqualByComparingTo("17195.00");
        assertThat(sindico.orcado()).isEqualByComparingTo("8000.00");
        assertThat(sindico.orcado().scale()).isEqualTo(2);
        assertThat(sindico.percentualTexto()).isEqualTo("-53,47%");
        assertThat(linha(po, "1.4.3").marca()).isEqualTo(MarcaPo.RATEIO_A_PARTE);
        assertThat(linha(po, "1.4.3").conta()).isNull();
        assertThat(linha(po, "1.4.3").contaTexto()).isEqualTo("Débito em receitas eventuais");
        assertThat(linha(po, "1.9.1").contaTexto()).isEqualTo("Fundo de Reserva");
        assertThat(po.linhas().stream().filter(l -> l.codigoImpresso().equals("1.3.2"))).hasSize(2);
        assertThat(r.conferencias()).anySatisfy(c -> {
            assertThat(c.codigo()).isEqualTo("CODIGO_REPETIDO");
            assertThat(c.ok()).isFalse();
        });
    }

    @Test
    void mensagemV1EhRecusada() throws Exception {
        assertThatThrownBy(() -> contrato.lerResultado(exemplo("v1", "resultado-concluido.json")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fora do contrato");
    }

    @Test
    void lancamentoSemRecebimentoCotaEhRecusado() throws Exception {
        String json = new String(exemplo("v2", "resultado-concluido-fluxo.json"), StandardCharsets.UTF_8)
                .replaceAll(",\\s*\"recebimentoCota\": false", "");
        assertThat(json).doesNotContain("\"recebimentoCota\": false");
        assertThatThrownBy(() -> contrato.lerResultado(json.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("fora do contrato");
    }

    @Test
    void marcaDesconhecidaNaPoEhRecusada() throws Exception {
        String json = new String(exemplo("v2", "resultado-concluido-po.json"), StandardCharsets.UTF_8)
                .replaceFirst("\"RATEIO_A_PARTE\"", "\"Rateio à parte\"");
        assertThatThrownBy(() -> contrato.lerResultado(json.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("fora do contrato");
    }

    @Test
    void falhouSemMotivoEhRecusado() {
        String json = """
                {"versao":2,"processamentoId":"%s","arquivoId":"%s","condominioId":"%s","situacao":"FALHOU"}"""
                .formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> contrato.lerResultado(json.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("fora do contrato");
    }

    @Test
    void dinheiroComoNumeroEhRecusado() throws Exception {
        String fluxo = new String(exemplo("v2", "resultado-concluido-fluxo.json"), StandardCharsets.UTF_8)
                .replace("\"1500.10\"", "1500.1");
        assertThatThrownBy(() -> contrato.lerResultado(fluxo.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("fora do contrato");
        String po = new String(exemplo("v2", "resultado-concluido-po.json"), StandardCharsets.UTF_8)
                .replace("\"1585.14\"", "1585.14");
        assertThatThrownBy(() -> contrato.lerResultado(po.getBytes(StandardCharsets.UTF_8)))
                .hasMessageContaining("fora do contrato");
    }

    @Test
    void pedidoDeLeituraSaiNoContrato() {
        var arquivo = new Arquivo(UUID.randomUUID(), Categoria.BALANCETE, "fluxo.pdf", "c/BALANCETE/2026/x-fluxo.pdf",
                "c".repeat(64), 10, "application/pdf", "gestor");
        String json = new String(contrato.escrever(ArquivoRecebido.de(arquivo)), StandardCharsets.UTF_8);
        assertThat(json).contains("\"versao\":1").contains("\"categoria\":\"BALANCETE\"")
                .contains(arquivo.getProcessamentoId().toString());
    }

    private static LinhaPoLida linha(ResultadoProcessamento.PrevisaoLida po, String codigo) {
        return po.linhas().stream().filter(l -> l.codigoImpresso().equals(codigo)).findFirst().orElseThrow();
    }
}
