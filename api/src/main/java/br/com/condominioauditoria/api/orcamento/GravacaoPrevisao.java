package br.com.condominioauditoria.api.orcamento;

import br.com.condominioauditoria.api.arquivo.Arquivo;
import br.com.condominioauditoria.api.mensagens.ResultadoProcessamento.ConferenciaLida;
import br.com.condominioauditoria.api.mensagens.ResultadoProcessamento.LinhaPoLida;
import br.com.condominioauditoria.api.mensagens.ResultadoProcessamento.PrevisaoLida;
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
    public Optional<PrevisaoOrcamentaria> travada(Arquivo arquivo) {
        return previsoes.findByArquivoId(arquivo.getId()).filter(p -> p.getEstado().travada());
    }

    /** O arquivo deixou de gerar PO (outra categoria ou outro conteúdo): a PO não confirmada sai junto. */
    public void removerNaoConfirmada(Arquivo arquivo) {
        previsoes.findByArquivoId(arquivo.getId()).filter(p -> !p.getEstado().travada()).ifPresent(p -> {
            linhas.apagarDaPrevisao(p.getId());
            previsoes.delete(p);
        });
    }

    public PrevisaoOrcamentaria gravar(Arquivo arquivo, String interpretador, PrevisaoLida lida,
            List<ConferenciaLida> conferencias) {
        PrevisaoOrcamentaria previsao = previsoes.findByArquivoId(arquivo.getId())
                .orElseGet(() -> new PrevisaoOrcamentaria(arquivo.getCondominioId(), arquivo.getId(),
                        arquivo.getSha256()));
        linhas.apagarDaPrevisao(previsao.getId());
        List<LinhaPo> novas = lida.linhas().stream().map(l -> linha(previsao, l)).toList();

        EstruturaPo estrutura = EstruturaPo.de(novas);
        var avaliacao = AvaliacaoLeituraPo.avaliar(estrutura, paraAvaliacao(conferencias),
                propriedades.toleranciaArredondamento());
        List<String> colunas = lida.colunasOrcado() == null ? List.of() : lida.colunasOrcado();
        previsao.registrarLeitura(interpretador, lida.titulo(), lida.exercicioImpresso(),
                colunas.size() > 1 ? colunas.get(0) : null, colunas.isEmpty() ? null : colunas.getLast(),
                avaliacao.estado(), estrutura.total() == null ? null : estrutura.total().getOrcado(),
                estrutura.previstoMesImpresso().orElse(null), estrutura.previstoMesPelasLinhas(),
                propriedades.toleranciaArredondamento(), Instant.now());
        previsoes.save(previsao);
        linhas.saveAll(novas);
        return previsao;
    }

    static List<ConferenciaPo> paraAvaliacao(List<ConferenciaLida> conferencias) {
        return conferencias.stream().map(c -> new ConferenciaPo(c.codigo(), c.descricao(), c.ok(), c.detalhe())).toList();
    }

    static LinhaPo linha(PrevisaoOrcamentaria previsao, LinhaPoLida l) {
        return new LinhaPo(previsao, l.ordem(), l.pagina(), TipoLinhaPo.valueOf(l.tipo().name()), l.codigoImpresso(),
                l.conta(), l.contaTexto(), l.marca() == null ? null : MarcaPo.valueOf(l.marca().name()), l.descricao(),
                l.orcadoAnterior(), l.orcado(), l.percentualTexto(), l.observacoes());
    }
}
