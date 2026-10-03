package br.com.condominioauditoria.backend.processamento;

import br.com.condominioauditoria.backend.arquivo.Arquivo;
import br.com.condominioauditoria.backend.arquivo.ArquivoRepository;
import br.com.condominioauditoria.backend.arquivo.StatusArquivo;
import br.com.condominioauditoria.backend.contabil.Conferencia;
import br.com.condominioauditoria.backend.contabil.ConferenciaRepository;
import br.com.condominioauditoria.backend.contabil.Fundo;
import br.com.condominioauditoria.backend.contabil.FundoRepository;
import br.com.condominioauditoria.backend.contabil.Lancamento;
import br.com.condominioauditoria.backend.contabil.LancamentoRepository;
import br.com.condominioauditoria.backend.contabil.SaldoFundo;
import br.com.condominioauditoria.backend.contabil.SaldoFundoRepository;
import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento;
import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento.ConferenciaLida;
import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento.Fluxo;
import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento.Posicao;
import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento.Secao;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava tudo o que o rag extraiu de um arquivo numa transação só: ou entra tudo, ou nada (rollback).
 * Antes de inserir, apaga a extração anterior do mesmo arquivo: reprocessar nunca duplica lançamento.
 */
@Service
public class GravacaoResultado {

    private static final Logger log = LoggerFactory.getLogger(GravacaoResultado.class);

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
    public void gravar(ResultadoProcessamento resultado) {
        Arquivo arquivo = arquivos.findById(resultado.arquivoId()).orElse(null);
        if (arquivo == null || !arquivo.ehDoProcessamento(resultado.processamentoId())) {
            log.info("Resultado descartado: arquivo {} foi apagado ou reprocessado depois desta leitura",
                    resultado.arquivoId());
            return;
        }
        lancamentos.apagarDoArquivo(arquivo.getId());
        saldos.apagarDoArquivo(arquivo.getId());
        conferencias.apagarDoArquivo(arquivo.getId());

        Fluxo fluxo = resultado.fluxoDeCaixa();
        if (fluxo == null) {
            arquivo.concluir(StatusArquivo.CONCLUIDO,
                    "Arquivo guardado. A leitura dos dados deste tipo de documento ainda vai ser construída.",
                    null, null, null, null);
            return;
        }
        List<ConferenciaLida> verificacoes = resultado.conferencias() == null ? List.of() : resultado.conferencias();
        gravarFluxo(arquivo, fluxo, verificacoes);
        long falhas = verificacoes.stream().filter(v -> !v.ok()).count();
        arquivo.concluir(falhas == 0 ? StatusArquivo.CONCLUIDO : StatusArquivo.PRECISA_REVISAO,
                falhas == 0 ? "Todas as conferências passaram" : falhas + " conferência(s) não bateram",
                resultado.interpretador(), fluxo.periodoInicio(), fluxo.periodoFim(), fluxo.totalLancamentos());
        log.info("Arquivo {} gravado: {} lançamentos", arquivo.getNomeOriginal(), fluxo.totalLancamentos());
    }

    private void gravarFluxo(Arquivo arquivo, Fluxo fluxo, List<ConferenciaLida> verificacoes) {
        UUID condominioId = arquivo.getCondominioId();
        Map<String, UUID> fundoPorNome = new HashMap<>();
        List<Lancamento> novos = new ArrayList<>();
        for (Secao secao : fluxo.secoes()) {
            UUID fundoId = fundoPorNome.computeIfAbsent(secao.fundo(), nome -> fundo(condominioId, nome));
            secao.lancamentos().forEach(l -> novos.add(new Lancamento(condominioId, arquivo.getId(), fundoId, l)));
        }
        lancamentos.saveAll(novos);

        List<SaldoFundo> posicoes = new ArrayList<>();
        for (Posicao p : fluxo.posicaoFinanceira()) {
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
