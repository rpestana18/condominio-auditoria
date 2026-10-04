package br.com.condominioauditoria.rag.leitura.po;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import br.com.condominioauditoria.rag.dominio.fluxo.Verificacao;
import br.com.condominioauditoria.rag.dominio.po.ConferenciaPo;
import br.com.condominioauditoria.rag.dominio.po.PrevisaoOrcamentaria;
import br.com.condominioauditoria.rag.dominio.po.PrevisaoOrcamentaria.LinhaPo;
import br.com.condominioauditoria.rag.dominio.po.PrevisaoOrcamentaria.Marca;
import br.com.condominioauditoria.rag.dominio.po.PrevisaoOrcamentaria.TipoLinha;
import br.com.condominioauditoria.rag.leitura.contrato.ContratoLeitor;
import br.com.condominioauditoria.rag.leitura.contrato.DocumentoLido;
import br.com.condominioauditoria.rag.leitura.contrato.DocumentoLido.Pagina;
import br.com.condominioauditoria.rag.leitura.contrato.DocumentoLido.Palavra;
import br.com.condominioauditoria.rag.leitura.fluxo.InterpretadorFluxoCaixa;
import br.com.condominioauditoria.rag.mensagens.ArquivoRecebido;
import br.com.condominioauditoria.rag.mensagens.ContratoMensagens;
import br.com.condominioauditoria.rag.mensagens.ResultadoProcessamento;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * PO 2026/2027 real do piloto (RF-03.1.1, RF-03.1.2 e RF-03.1.15). A saída do leitor para o PDF é dado real e fica
 * fora do git (data/golden/privado/po-2026-2027.documento-lido.json); sem ela, o teste é pulado.
 */
class InterpretadorPoProtestGoldenTest {

    private static DocumentoLido documento;
    private static PrevisaoOrcamentaria po;
    private static List<Verificacao> conferencias;

    @BeforeAll
    static void ler() throws Exception {
        Path json = Path.of(System.getProperty("golden.dir"), "privado/po-2026-2027.documento-lido.json");
        assumeTrue(Files.exists(json), "golden privado ausente");
        documento = new ContratoLeitor().converter(Files.readString(json));
        var interpretador = new InterpretadorPoProtest();
        assertThat(interpretador.reconhece(documento)).isTrue();
        po = interpretador.interpretar(documento);
        conferencias = ConferenciaPo.conferir(po);
    }

    @Test
    void reconhecimentoPeloConteudo() throws Exception {
        assertThat(new InterpretadorFluxoCaixa().reconhece(documento)).isFalse();
        Path fluxo = Path.of(System.getProperty("golden.dir"), "privado/fluxo-caixa-2026-09.documento-lido.json");
        if (Files.exists(fluxo)) {
            assertThat(new InterpretadorPoProtest().reconhece(new ContratoLeitor().converter(Files.readString(fluxo))))
                    .isFalse();
        }
    }

    @Test
    void cabecalho() {
        assertThat(po.titulo()).isEqualTo("PROPOSTA ORÇAMENTÁRIA 2026 / 2027");
        assertThat(po.exercicioImpresso()).isEqualTo("2026 / 2027");
        assertThat(po.colunasOrcado()).containsExactly("2025/2026", "2026/2027");
    }

    @Test
    void todasAsLinhas() {
        assertThat(po.linhas()).hasSize(98);
        assertThat(po.linhas()).allSatisfy(l -> assertThat(l.pagina()).isEqualTo(1));
        assertThat(po.linhas()).extracting(LinhaPo::ordem)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 98).boxed().toList());
        Map<TipoLinha, Long> porTipo = po.linhas().stream()
                .collect(Collectors.groupingBy(LinhaPo::tipo, LinkedHashMap::new, Collectors.counting()));
        assertThat(porTipo).containsEntry(TipoLinha.TOTAL, 1L).containsEntry(TipoLinha.GRUPO, 9L)
                .containsEntry(TipoLinha.LINHA, 88L);
        assertThat(po.linhas()).allSatisfy(l -> {
            assertThat(l.orcado().scale()).isEqualTo(2);
            assertThat(l.orcadoAnterior().scale()).isEqualTo(2);
            assertThat(l.descricao()).isNotBlank();
        });
    }

    /** RF-03.1.1, primeiro critério. */
    @Test
    void linha1320SindicaturaProfissional() {
        LinhaPo l = linha("1.3.20");
        assertThat(l.tipo()).isEqualTo(TipoLinha.LINHA);
        assertThat(l.conta()).isEqualTo("1682 - Sindicatura Profissional");
        assertThat(l.contaTexto()).isNull();
        assertThat(l.marca()).isNull();
        assertThat(l.descricao()).isEqualTo("Obm - Sergio Diniz");
        assertThat(l.orcadoAnterior()).isEqualByComparingTo("17195.00");
        assertThat(l.orcado()).isEqualByComparingTo("8000.00");
        assertThat(l.percentualTexto()).isEqualTo("-53,47%");
        assertThat(l.observacoes()).isEqualTo("Pro-labore Síndico");
        assertThat(l.pagina()).isEqualTo(1);
    }

    /** RF-03.1.1, segundo critério. */
    @Test
    void linha1323Interfones() {
        LinhaPo l = linha("1.3.23");
        assertThat(l.conta()).isEqualTo("1621 - Interfones");
        assertThat(l.descricao()).isEqualTo("Manutenção Preventiva De Interfones/Cftv");
        assertThat(l.orcadoAnterior()).isEqualByComparingTo("0.00");
        assertThat(l.orcado()).isEqualByComparingTo("0.00");
        assertThat(l.percentualTexto()).isNull();
        assertThat(l.observacoes()).isEqualTo("Manutenção R$4.100,00 out/25");
    }

    /** RF-03.1.1, terceiro critério, e as demais marcas da coluna de conta. */
    @Test
    void marcas() {
        for (String codigo : List.of("1.4.1", "1.4.2", "1.4.3", "1.6.15")) {
            LinhaPo l = linha(codigo);
            assertThat(l.marca()).as(codigo).isEqualTo(Marca.RATEIO_A_PARTE);
            assertThat(l.conta()).as(codigo).isNull();
            assertThat(l.orcado()).as(codigo).isEqualByComparingTo("0.00");
        }
        assertThat(linha("1.4.1").descricao()).isEqualTo("Força e Luz");
        assertThat(linha("1.4.2").descricao()).isEqualTo("Água e Esgoto");
        assertThat(linha("1.4.3").descricao()).isEqualTo("Gás");
        assertThat(linha("1.6.15").descricao()).isEqualTo("Seguro predial");
        assertThat(linha("1.6.11").marca()).isEqualTo(Marca.SEM_VALOR);
        assertThat(linha("1.6.12").marca()).isEqualTo(Marca.NEGOCIADA_ISENCAO);
        assertThat(linha("1.6.13").marca()).isEqualTo(Marca.NEGOCIADA_ISENCAO);
        for (String codigo : List.of("1.5.3", "1.6.17", "1.7.6", "1.7.12")) {
            assertThat(linha(codigo).marca()).as(codigo).isEqualTo(Marca.VALOR_FIXO_SEM_REFERENCIA);
            assertThat(linha(codigo).conta()).as(codigo).isNull();
        }
        assertThat(linha("1.6.17").orcado()).isEqualByComparingTo("99.03");
        assertThat(po.linhas()).filteredOn(l -> l.marca() != null).hasSize(11);
    }

    /** Texto da coluna de conta que não é código nem marca nunca vira conta (ADR 0004, Decisão 1). */
    @Test
    void contaTexto() {
        LinhaPo gas = linha("1.4.3");
        assertThat(gas.contaTexto()).isEqualTo("Débito em receitas eventuais");
        assertThat(gas.observacoes()).isEqualTo("Rateio à parte");
        assertThat(linha("1").contaTexto()).isEqualTo("Soma das seções 1.1 a 1.9");
        assertThat(linha("1.1").contaTexto()).isEqualTo("Subtotal (soma linhas 5 a 18)");
        assertThat(linha("1.9").contaTexto()).isEqualTo("Fundos");
        assertThat(linha("1.9.1").contaTexto()).isEqualTo("Fundo de Reserva");
        assertThat(linha("1.9.2").contaTexto()).isEqualTo("Obras Reformas e Infraestrutura");
        assertThat(po.linhas()).filteredOn(l -> l.contaTexto() != null)
                .allSatisfy(l -> assertThat(l.conta()).isNull());
        assertThat(po.linhas()).filteredOn(l -> l.tipo() == TipoLinha.LINHA && l.marca() == null
                && !l.codigoImpresso().startsWith("1.9.")).allSatisfy(l -> {
                    assertThat(l.conta()).as(l.codigoImpresso()).matches("^\\d{4} - .+");
                    assertThat(l.contaTexto()).as(l.codigoImpresso()).isNull();
                });
    }

    /** Colunas que encostam na vizinha. */
    @Test
    void colunasLargas() {
        assertThat(linha("1.3.22").conta()).isEqualTo("4066 - MONITORAMENTO REMOTO PORTARIA");
        assertThat(linha("1.3.22").descricao()).isEqualTo("Câmeras Gabriel");
        assertThat(linha("1.3.24").conta()).isEqualTo("4069 - ASSESSORIA TECNICA ELEVADORES");
        assertThat(linha("1.8.2").conta()).isEqualTo("4071 - APOIO CENTRAL CORRESPONDENCIA");
        assertThat(linha("1.8.2").descricao()).isEqualTo("Apoio central de correspondência - meses nov à jan");
        assertThat(linha("1.2.1").conta()).isEqualTo("1591 - Telefone Fixo + 1592 - Telefone");
        assertThat(linha("1.3.5").descricao())
                .isEqualTo("Hilton Castro Magalhaes Locacao De Equipamentos Esportivos");
        assertThat(linha("1.3.10").observacoes()).isEqualTo("Média jan 26/abr 26 + VIGILANTES RJ");
        assertThat(linha("1.6.7").observacoes()).isEqualTo("-");
    }

    @Test
    void valoresSemSeparadorDeMilhar() {
        assertThat(linha("1.1.5").orcado()).isEqualByComparingTo("1585.14");
        assertThat(linha("1.1.5").orcadoAnterior()).isEqualByComparingTo("339.45");
        assertThat(linha("1.1.9").orcado()).isEqualByComparingTo("1189.16");
        assertThat(linha("1.6.19").orcado()).isEqualByComparingTo("1129.47");
        assertThat(linha("1.8.6").orcado()).isEqualByComparingTo("1000.00");
    }

    /** RF-03.1.2: o 1.3.2 impresso duas vezes. Nenhuma das linhas é descartada nem somada à outra. */
    @Test
    void codigo132RepetidoComAsDuasLinhas() {
        List<LinhaPo> repetidas = po.linhas().stream().filter(l -> l.codigoImpresso().equals("1.3.2")).toList();
        assertThat(repetidas).extracting(LinhaPo::ordem).containsExactly(20, 43);
        assertThat(repetidas.get(0).conta()).isEqualTo("1598 - Bombas");
        assertThat(repetidas.get(0).orcado()).isEqualByComparingTo("3000.00");
        assertThat(repetidas.get(1).conta()).isEqualTo("1624 - Caixa D'água");
        assertThat(repetidas.get(1).descricao()).isEqualTo("Caixa D'água");
        assertThat(repetidas.get(1).orcado()).isEqualByComparingTo("1518.93");

        Verificacao repetido = conferencia("CODIGO_REPETIDO");
        assertThat(repetido.ok()).isFalse();
        assertThat(repetido.detalhe()).isEqualTo("1.3.2 aparece 2 vezes (ordens 20 e 43)");
    }

    /**
     * RF-03.1.2, primeiro critério. Os subtotais impressos são os do requisito, mas no próprio PDF duas somas não
     * fecham ao centavo (arredondamento na planilha de origem): as linhas de 1.3 somam 336.274,18 (impresso
     * 336.274,17) e as de 1.9 somam 22.581,00 (impresso 22.581,01). A conferência é exata e aponta as duas.
     */
    @Test
    void subtotaisTotalEPrevistoDoMes() {
        Map<String, String> esperado = new LinkedHashMap<>();
        esperado.put("1.1", "69193.86");
        esperado.put("1.2", "694.05");
        esperado.put("1.3", "336274.17");
        esperado.put("1.4", "0.00");
        esperado.put("1.5", "2850.00");
        esperado.put("1.6", "17388.04");
        esperado.put("1.7", "15200.00");
        esperado.put("1.8", "10020.00");
        esperado.put("1.9", "22581.01");
        assertThat(po.linhas()).filteredOn(l -> l.tipo() == TipoLinha.GRUPO).extracting(LinhaPo::codigoImpresso)
                .containsExactlyElementsOf(esperado.keySet());
        esperado.forEach((codigo, valor) -> assertThat(linha(codigo).orcado()).as(codigo).isEqualByComparingTo(valor));

        List<Verificacao> subtotais = conferencias.stream().filter(v -> v.codigo().equals("SUBTOTAL_GRUPO")).toList();
        assertThat(subtotais).extracting(Verificacao::detalhe).containsExactly(
                "1.1 PESSOAL: soma das linhas 69.193,86; impresso 69.193,86",
                "1.2 CONSUMO/UTILIDADES: soma das linhas 694,05; impresso 694,05",
                "1.3 SERVIÇOS - CONTRATOS EFETIVOS: soma das linhas 336.274,18; impresso 336.274,17; diferença -0,01",
                "1.4 TARIFAS PÚBLICAS: soma das linhas 0,00; impresso 0,00",
                "1.5 AQUISIÇÃO DE BENS: soma das linhas 2.850,00; impresso 2.850,00",
                "1.6 DESPESAS ADMINISTRATIVAS: soma das linhas 17.388,04; impresso 17.388,04",
                "1.7 MATERIAIS/SUPRIMENTOS: soma das linhas 15.200,00; impresso 15.200,00",
                "1.8 SERVIÇOS: soma das linhas 10.020,00; impresso 10.020,00",
                "1.9 Fundos do Condomínio: soma das linhas 22.581,00; impresso 22.581,01; diferença 0,01");
        assertThat(subtotais).extracting(Verificacao::ok)
                .containsExactly(true, true, false, true, true, true, true, true, false);

        assertThat(linha("1").orcado()).isEqualByComparingTo("474201.13");
        assertThat(conferencia("TOTAL").ok()).isTrue();
        assertThat(conferencia("TOTAL").detalhe()).isEqualTo("soma dos grupos 474.201,13; impresso 474.201,13");
        assertThat(conferencia("PREVISTO_MES").ok()).isTrue();
        assertThat(conferencia("PREVISTO_MES").detalhe())
                .isEqualTo("474.201,13 - 22.581,01 = 451.620,12; soma das linhas dos demais grupos 451.620,13");
        assertThat(ConferenciaPo.previstoDoMes(po)).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("451620.12"));
        assertThat(conferencias).extracting(Verificacao::codigo).containsExactly("SUBTOTAL_GRUPO", "SUBTOTAL_GRUPO",
                "SUBTOTAL_GRUPO", "SUBTOTAL_GRUPO", "SUBTOTAL_GRUPO", "SUBTOTAL_GRUPO", "SUBTOTAL_GRUPO",
                "SUBTOTAL_GRUPO", "SUBTOTAL_GRUPO", "TOTAL", "PREVISTO_MES", "FUNDO_TAXA", "CODIGO_REPETIDO");
    }

    /** RF-03.1.3: reserva 3% e obras 2% de 451.620,12. */
    @Test
    void fundosPelaTaxa() {
        assertThat(linha("1.9.1").orcado()).isEqualByComparingTo("13548.60");
        assertThat(linha("1.9.1").percentualTexto()).isEqualTo("3,00%");
        assertThat(linha("1.9.2").orcado()).isEqualByComparingTo("9032.40");
        assertThat(linha("1.9.2").percentualTexto()).isEqualTo("2,00%");
        Verificacao taxa = conferencia("FUNDO_TAXA");
        assertThat(taxa.ok()).isTrue();
        assertThat(taxa.detalhe()).isEqualTo("1.9.1 Fundo de Reserva: 3,00% de 451.620,12 = 13.548,60; impresso 13.548,60; "
                + "1.9.2 Fundo de Obras: 2,00% de 451.620,12 = 9.032,40; impresso 9.032,40");
    }

    /** RF-03.1.2, segundo critério: cópia com o subtotal de Pessoal impresso 69.193,00. */
    @Test
    void copiaComSubtotalAlteradoGeraConferenciaFalha() {
        DocumentoLido copia = trocarPalavra(documento, "69.193,86", "69.193,00");
        PrevisaoOrcamentaria alterada = new InterpretadorPoProtest().interpretar(copia);
        assertThat(alterada.linhas().stream().filter(l -> l.codigoImpresso().equals("1.1")).findFirst().orElseThrow()
                .orcado()).isEqualByComparingTo("69193.00");

        List<Verificacao> resultado = ConferenciaPo.conferir(alterada);
        Verificacao pessoal = resultado.getFirst();
        assertThat(pessoal.codigo()).isEqualTo("SUBTOTAL_GRUPO");
        assertThat(pessoal.ok()).isFalse();
        assertThat(pessoal.detalhe()).isEqualTo("1.1 PESSOAL: soma das linhas 69.193,86; impresso 69.193,00; diferença -0,86");
        Verificacao total = resultado.stream().filter(v -> v.codigo().equals("TOTAL")).findFirst().orElseThrow();
        assertThat(total.ok()).isFalse();
        assertThat(total.detalhe()).isEqualTo("soma dos grupos 474.200,27; impresso 474.201,13; diferença 0,86");
        // Na PO original, Pessoal e o total batem: a falha vem só da alteração
        assertThat(conferencias.getFirst().ok()).isTrue();
        assertThat(conferencia("TOTAL").ok()).isTrue();
    }

    /** A leitura cabe no contrato v2 e confere, campo a campo, com as linhas do exemplo do contrato. */
    @Test
    void resultadoNoContratoV2IgualAoExemplo() throws Exception {
        var arquivo = new ArquivoRecebido(1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "PO",
                "po.pdf", "x/po.pdf", "a".repeat(64));
        byte[] json = new ContratoMensagens().escrever(ResultadoProcessamento.concluidoPo(arquivo, "po-protest", 1, po,
                conferencias));
        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode produzido = mapper.readTree(json).get("previsaoOrcamentaria");
        JsonNode exemplo = mapper.readTree(Files.readString(Path.of(System.getProperty("contratos.dir"),
                "mensagens/v2/exemplos/resultado-concluido-po.json"))).get("previsaoOrcamentaria");

        assertThat(produzido.get("titulo")).isEqualTo(exemplo.get("titulo"));
        assertThat(produzido.get("colunasOrcado")).isEqualTo(exemplo.get("colunasOrcado"));
        for (JsonNode linhaExemplo : exemplo.get("linhas")) {
            int ordem = linhaExemplo.get("ordem").asInt();
            assertThat(produzido.get("linhas").get(ordem - 1)).as("ordem " + ordem).isEqualTo(linhaExemplo);
        }
    }

    private static LinhaPo linha(String codigo) {
        return po.linhas().stream().filter(l -> l.codigoImpresso().equals(codigo)).findFirst().orElseThrow();
    }

    private static Verificacao conferencia(String codigo) {
        return conferencias.stream().filter(v -> v.codigo().equals(codigo)).findFirst().orElseThrow();
    }

    private static DocumentoLido trocarPalavra(DocumentoLido d, String de, String para) {
        List<Pagina> paginas = d.paginas().stream().map(p -> new Pagina(p.numero(), p.largura(), p.altura(), p.metodo(),
                p.palavras().stream().map(w -> w.texto().equals(de)
                        ? new Palavra(para, w.x0(), w.x1(), w.topo(), w.base()) : w).toList())).toList();
        return new DocumentoLido(d.versaoContrato(), d.leitor(), d.arquivo(), d.tipo(), paginas, d.planilhas(),
                d.paragrafos());
    }
}
