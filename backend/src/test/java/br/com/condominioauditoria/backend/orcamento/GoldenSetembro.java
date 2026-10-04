package br.com.condominioauditoria.backend.orcamento;

import br.com.condominioauditoria.backend.contabil.Fundo;
import br.com.condominioauditoria.backend.contabil.Lancamento;
import br.com.condominioauditoria.backend.mensagens.MensagensGolden;
import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento;
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
    final UUID arquivoFluxo = UUID.randomUUID();
    /** Fundos do fluxo pelo nome impresso (os quatro do cenário mais os demais, criados aqui). */
    final Map<String, Fundo> fundos = new LinkedHashMap<>();

    private GoldenSetembro(CenarioPo cenario, PrevisaoOrcamentaria po, ResultadoProcessamento fluxo) {
        this.cenario = cenario;
        this.po = po;
        this.fluxo = fluxo;
        for (Fundo f : java.util.List.of(cenario.ordinario, cenario.reserva, cenario.obrasInfra, cenario.obras)) {
            fundos.put(f.getNome(), f);
        }
        for (var secao : fluxo.fluxoDeCaixa().secoes()) {
            Fundo f = fundos.computeIfAbsent(secao.fundo(), n -> new Fundo(cenario.condominioId, n));
            secao.lancamentos().forEach(l -> cenario.lancamentos.add(new Lancamento(cenario.condominioId, arquivoFluxo,
                    f.getId(), l)));
        }
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

    LinhaPo linha(String codigoEfetivo) {
        return cenario.linhas.stream().filter(l -> l.getPrevisaoId().equals(po.getId())
                && l.getCodigoEfetivo().equals(codigoEfetivo)).findFirst().orElseThrow();
    }
}
