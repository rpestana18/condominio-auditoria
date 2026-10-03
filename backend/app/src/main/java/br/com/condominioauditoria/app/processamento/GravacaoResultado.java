package br.com.condominioauditoria.app.processamento;

import br.com.condominioauditoria.app.arquivo.Arquivo;
import br.com.condominioauditoria.app.arquivo.ArquivoRepository;
import br.com.condominioauditoria.app.contabil.Conferencia;
import br.com.condominioauditoria.app.contabil.ConferenciaRepository;
import br.com.condominioauditoria.app.contabil.Fundo;
import br.com.condominioauditoria.app.contabil.FundoRepository;
import br.com.condominioauditoria.app.contabil.Lancamento;
import br.com.condominioauditoria.app.contabil.LancamentoRepository;
import br.com.condominioauditoria.app.contabil.SaldoFundo;
import br.com.condominioauditoria.app.contabil.SaldoFundoRepository;
import br.com.condominioauditoria.dominio.StatusArquivo;
import br.com.condominioauditoria.dominio.fluxo.FluxoDeCaixa;
import br.com.condominioauditoria.dominio.fluxo.PosicaoFundo;
import br.com.condominioauditoria.dominio.fluxo.SecaoFundo;
import br.com.condominioauditoria.dominio.fluxo.Verificacao;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava tudo de um arquivo numa transação só: ou entra tudo, ou nada (rollback).
 * Antes de inserir, apaga a extração anterior do mesmo arquivo: reprocessar nunca duplica lançamento.
 */
@Service
class GravacaoResultado {

    static final String INTERPRETADOR_FLUXO = "fluxo-caixa-protest";

    private final ArquivoRepository arquivos;
    private final FundoRepository fundos;
    private final LancamentoRepository lancamentos;
    private final SaldoFundoRepository saldos;
    private final ConferenciaRepository conferencias;

    GravacaoResultado(ArquivoRepository arquivos, FundoRepository fundos, LancamentoRepository lancamentos,
            SaldoFundoRepository saldos, ConferenciaRepository conferencias) {
        this.arquivos = arquivos;
        this.fundos = fundos;
        this.lancamentos = lancamentos;
        this.saldos = saldos;
        this.conferencias = conferencias;
    }

    @Transactional
    public void gravar(UUID arquivoId, ResultadoLeitura resultado) {
        Arquivo arquivo = arquivos.findById(arquivoId).orElseThrow();
        lancamentos.apagarDoArquivo(arquivoId);
        saldos.apagarDoArquivo(arquivoId);
        conferencias.apagarDoArquivo(arquivoId);

        switch (resultado) {
            case ResultadoLeitura.Fluxo(FluxoDeCaixa fluxo, List<Verificacao> verificacoes) -> {
                gravarFluxo(arquivo, fluxo, verificacoes);
                long falhas = verificacoes.stream().filter(v -> !v.ok()).count();
                arquivo.concluir(falhas == 0 ? StatusArquivo.CONCLUIDO : StatusArquivo.PRECISA_REVISAO,
                        falhas == 0 ? "Todas as conferências passaram" : falhas + " conferência(s) não bateram",
                        INTERPRETADOR_FLUXO, fluxo.periodoInicio(), fluxo.periodoFim(), fluxo.totalLancamentos());
            }
            case ResultadoLeitura.SemInterpretador(int paginas) -> arquivo.concluir(StatusArquivo.CONCLUIDO,
                    "Arquivo guardado. A leitura dos dados deste tipo de documento ainda vai ser construída.",
                    null, null, null, null);
        }
    }

    private void gravarFluxo(Arquivo arquivo, FluxoDeCaixa fluxo, List<Verificacao> verificacoes) {
        UUID condominioId = arquivo.getCondominioId();
        Map<String, UUID> fundoPorNome = new HashMap<>();
        List<Lancamento> novos = new ArrayList<>();
        for (SecaoFundo secao : fluxo.secoes()) {
            UUID fundoId = fundoPorNome.computeIfAbsent(secao.fundo(), nome -> fundo(condominioId, nome));
            secao.lancamentos().forEach(l -> novos.add(new Lancamento(condominioId, arquivo.getId(), fundoId, l)));
        }
        lancamentos.saveAll(novos);

        List<SaldoFundo> posicoes = new ArrayList<>();
        for (PosicaoFundo p : fluxo.posicaoFinanceira()) {
            UUID fundoId = fundoPorNome.computeIfAbsent(p.fundo(), nome -> fundo(condominioId, nome));
            posicoes.add(new SaldoFundo(condominioId, arquivo.getId(), fundoId, fluxo.periodoInicio(), fluxo.periodoFim(), p));
        }
        saldos.saveAll(posicoes);

        for (int i = 0; i < verificacoes.size(); i++) {
            conferencias.save(new Conferencia(arquivo.getId(), i + 1, verificacoes.get(i)));
        }
    }

    private UUID fundo(UUID condominioId, String nome) {
        return fundos.findByCondominioIdAndNome(condominioId, nome)
                .orElseGet(() -> fundos.save(new Fundo(condominioId, nome)))
                .getId();
    }
}
