package br.com.condominioauditoria.api.orcamento;

import br.com.condominioauditoria.api.contabil.Fundo;
import br.com.condominioauditoria.api.contabil.Lancamento;
import br.com.condominioauditoria.api.mensagens.MensagensGolden;
import br.com.condominioauditoria.api.mensagens.ResultadoProcessamento;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Caso de aceite do RF-03.1.15 montado a partir do golden privado: PO 2026/2027 lida e confirmada como o Admin faria
 * (exercício 05/2026 a 04/2027, 1.3.2 repetido → 1.3.25, 1.9.1 → "FUNDO DE RESERVA", 1.9.2 → "OBRAS / REFORMAS /
 * INFRA") e os lançamentos do fluxo de setembro/2026, lidos das mensagens v2 que o rag publica. Sem o golden, vazio.
 */
final class GoldenSetembro {

    final CenarioPo cenario;
    final PrevisaoOrcamentaria po;
    final ResultadoProcessamento fluxo;
    final br.com.condominioauditoria.api.arquivo.Arquivo arquivo;
    final UUID arquivoFluxo;
    /** Fundos do fluxo pelo nome impresso (os quatro do cenário mais os demais, criados aqui). */
    final Map<String, Fundo> fundos = new LinkedHashMap<>();

    private GoldenSetembro(CenarioPo cenario, PrevisaoOrcamentaria po, ResultadoProcessamento fluxo) {
        this.cenario = cenario;
        this.po = po;
        this.fluxo = fluxo;
        for (Fundo f : java.util.List.of(cenario.ordinario, cenario.reserva, cenario.obrasInfra, cenario.obras)) {
            fundos.put(f.getNome(), f);
        }
        var lido = fluxo.fluxoDeCaixa();
        this.arquivo = cenario.fluxo("fluxo-caixa-2026-09.pdf", lido.periodoInicio(), lido.periodoFim(),
                lido.totalLancamentos());
        this.arquivoFluxo = arquivo.getId();
        for (var secao : lido.secoes()) {
            fundos.computeIfAbsent(secao.fundo(), n -> {
                Fundo novo = new Fundo(cenario.condominioId, n);
                cenario.fundos.add(novo);
                return novo;
            });
        }
        gravarLancamentos();
    }

    /** Grava os lançamentos do fluxo como a GravacaoResultado: cada gravação cria lançamentos com id novo. */
    private void gravarLancamentos() {
        for (var secao : fluxo.fluxoDeCaixa().secoes()) {
            Fundo f = fundos.get(secao.fundo());
            secao.lancamentos().forEach(l -> cenario.lancamentos.add(new Lancamento(cenario.condominioId, arquivoFluxo,
                    f.getId(), l)));
        }
    }

    /**
     * Reprocesso do mesmo fluxo (ArquivoService.reprocessar + GravacaoResultado): apaga os lançamentos do arquivo e
     * grava de novo a mesma leitura, com ids novos. Publica a mudança, como a gravação.
     */
    void reprocessarFluxo() {
        cenario.lancamentos.removeIf(l -> l.getArquivoId().equals(arquivoFluxo));
        gravarLancamentos();
        cenario.publicados.add(MudancaOrcamento.de(cenario.condominioId, "leitura do arquivo "
                + arquivo.getNomeOriginal() + " gravada", "sistema", java.time.Instant.now()));
    }

    static Optional<GoldenSetembro> carregar() {
        var poLida = MensagensGolden.ler(MensagensGolden.PO_2026_2027);
        var fluxo = MensagensGolden.ler(MensagensGolden.FLUXO_SETEMBRO);
        if (poLida.isEmpty() || fluxo.isEmpty()) {
            return Optional.empty();
        }
        CenarioPo cenario = new CenarioPo();
        // O cenário chama o fundo ordinário de "CONDOMÍNIO", como no fluxo do piloto
        PrevisaoOrcamentaria po = cenario.lerPo(poLida.get().previsaoOrcamentaria(), poLida.get().conferencias());
        cenario.confirmacao.confirmar(cenario.condominioId, po.getId(), cenario.pedidoDoPiloto(po), "admin");
        return Optional.of(new GoldenSetembro(cenario, po, fluxo.get()));
    }

    /** Planilha do piloto (mapa-contas-fluxo-para-PO.csv, copiada para o golden privado). */
    static Optional<String> mapa() {
        return MensagensGolden.texto("mapa-contas-fluxo-para-PO.csv");
    }

    /** Carrega o mapa das 73 contas e confirma todas, como o Admin faria sem mudança (RF-03.1.4). */
    void confirmarMapa() {
        String mapa = mapa().orElseThrow(() -> new IllegalStateException("mapa do piloto ausente no golden privado"));
        cenario.depara.carregarPlanilha(cenario.condominioId, po.getId(), "mapa-contas-fluxo-para-PO.csv", mapa, "admin");
        cenario.depara.lote(cenario.condominioId, po.getId(), new DeparaDtos.PedidoLote(DeparaDtos.AcaoLote.CONFIRMAR,
                cenario.deparas.stream().map(DeparaConta::getContaCodigo).toList()), "admin");
    }

    CalculoPrevistoRealizado.Fluxo fluxoDeSetembro() {
        return new CalculoPrevistoRealizado.Fluxo(arquivoFluxo, arquivo.getNomeOriginal(), arquivo.getSha256(),
                arquivo.getPeriodoInicio(), arquivo.getPeriodoFim(), arquivo.getEnviadoEm(), arquivo.getEnviadoPor());
    }

    /** Entrada da função pura para o período, com o limite da Conv. 16.2 (20%). */
    CalculoPrevistoRealizado.Entrada entrada(CalculoPrevistoRealizado.Periodo periodo,
            java.util.List<CalculoPrevistoRealizado.Fluxo> fluxos,
            java.util.List<CalculoPrevistoRealizado.Realocacao> realocacoes) {
        Map<UUID, UUID> fundoPorLinha = new LinkedHashMap<>();
        cenario.poFundos.stream().filter(f -> f.getPrevisaoId().equals(po.getId()))
                .forEach(f -> fundoPorLinha.put(f.getLinhaPoId(), f.getFundoId()));
        Map<UUID, String> nomes = new LinkedHashMap<>();
        fundos.values().forEach(f -> nomes.put(f.getId(), f.getNome()));
        return new CalculoPrevistoRealizado.Entrada(po, "PO-2026-2027-aprovada.pdf",
                cenario.linhas.stream().filter(l -> l.getPrevisaoId().equals(po.getId())).toList(),
                cenario.deparas.stream().filter(d -> d.getPrevisaoId().equals(po.getId())).toList(), fundoPorLinha, nomes,
                cenario.ordinario.getId(), fluxos, cenario.lancamentos, realocacoes, new java.math.BigDecimal("20.0000"),
                java.util.List.of(), periodo);
    }

    CalculoPrevistoRealizado.Calculo setembro() {
        return CalculoPrevistoRealizado.calcular(entrada(new CalculoPrevistoRealizado.Mes(java.time.YearMonth.of(2026, 9)),
                java.util.List.of(fluxoDeSetembro()), java.util.List.of()));
    }

    LinhaPo linha(String codigoEfetivo) {
        return cenario.linhas.stream().filter(l -> l.getPrevisaoId().equals(po.getId())
                && l.getCodigoEfetivo().equals(codigoEfetivo)).findFirst().orElseThrow();
    }
}
