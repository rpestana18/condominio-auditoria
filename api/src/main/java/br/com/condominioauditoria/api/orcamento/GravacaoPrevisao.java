package br.com.condominioauditoria.api.orcamento;

import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.BudgetData;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.BudgetLineData;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.TotalsCheckData;
import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.orcamento.AvaliacaoLeituraPo.ConferenciaPo;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Grava a PO lida (ADR 0004, passo 4). Chamada pela {@code GravacaoResultado}, na mesma transação da leitura, e só
 * para arquivo da categoria PO. Reprocessar atualiza a mesma PO e troca as linhas; PO confirmada não muda.
 */
@Service
public class GravacaoPrevisao {

    private final PrevisaoOrcamentariaRepository previsoes;
    private final LinhaPoRepository linhas;
    private final PropriedadesOrcamento propriedades;

    public GravacaoPrevisao(PrevisaoOrcamentariaRepository previsoes, LinhaPoRepository linhas,
            PropriedadesOrcamento propriedades) {
        this.previsoes = previsoes;
        this.linhas = linhas;
        this.propriedades = propriedades;
    }

    /** PO já confirmada (ou substituída) para este arquivo: a nova leitura é recusada. */
    public Optional<PrevisaoOrcamentaria> travada(SourceFile arquivo) {
        return previsoes.findByArquivoId(arquivo.getId()).filter(p -> p.getEstado().travada());
    }

    /** O arquivo deixou de gerar PO (outra categoria ou outro conteúdo): a PO não confirmada sai junto. */
    public void removerNaoConfirmada(SourceFile arquivo) {
        previsoes.findByArquivoId(arquivo.getId()).filter(p -> !p.getEstado().travada()).ifPresent(p -> {
            linhas.apagarDaPrevisao(p.getId());
            previsoes.delete(p);
        });
    }

    public PrevisaoOrcamentaria gravar(SourceFile arquivo, String interpretador, BudgetData lida,
            List<TotalsCheckData> conferencias) {
        PrevisaoOrcamentaria previsao = previsoes.findByArquivoId(arquivo.getId())
                .orElseGet(() -> new PrevisaoOrcamentaria(arquivo.getCondominiumId(), arquivo.getId(),
                        arquivo.getSha256()));
        linhas.apagarDaPrevisao(previsao.getId());
        List<LinhaPo> novas = lida.lines().stream().map(l -> linha(previsao, l)).toList();

        EstruturaPo estrutura = EstruturaPo.de(novas);
        var avaliacao = AvaliacaoLeituraPo.avaliar(estrutura, paraAvaliacao(conferencias),
                propriedades.toleranciaArredondamento());
        List<String> colunas = lida.budgetColumns() == null ? List.of() : lida.budgetColumns();
        previsao.registrarLeitura(interpretador, lida.title(), lida.printedFiscalYear(),
                colunas.size() > 1 ? colunas.get(0) : null, colunas.isEmpty() ? null : colunas.getLast(),
                avaliacao.estado(), estrutura.total() == null ? null : estrutura.total().getOrcado(),
                estrutura.previstoMesImpresso().orElse(null), estrutura.previstoMesPelasLinhas(),
                propriedades.toleranciaArredondamento(), Instant.now());
        previsoes.save(previsao);
        linhas.saveAll(novas);
        return previsao;
    }

    static List<ConferenciaPo> paraAvaliacao(List<TotalsCheckData> conferencias) {
        return conferencias.stream().map(c -> new ConferenciaPo(c.code(), c.description(), c.ok(), c.detail())).toList();
    }

    static LinhaPo linha(PrevisaoOrcamentaria previsao, BudgetLineData l) {
        return new LinhaPo(previsao, l.sequence(), l.page(), TipoLinhaPo.valueOf(l.type().name()), l.printedCode(),
                l.account(), l.accountText(), l.mark() == null ? null : MarcaPo.valueOf(l.mark().name()), l.description(),
                l.previousBudgeted(), l.budgeted(), l.percentageText(), l.notes());
    }
}
