package br.com.condominioauditoria.api.orcamento;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Confirmação da PO pelo Admin (RF-03.1.3, RF-03.1.2 e Q29). Meses no formato AAAA-MM.
 *
 * @param ataArquivoId arquivo da categoria ATA que aprovou a PO; nulo com {@code semAta}
 * @param dataAprovacao data da assembleia (obrigatória com ata); sem ela, vale o início do exercício
 * @param codigosEfetivos código distinto para linhas cujo código impresso se repete
 * @param fundos ligação de cada linha de fundo (1.9.x) a um fundo do fluxo
 * @param reaprovacao substitui a PO confirmada que vale nos mesmos meses (nova versão)
 * @param cienteDivergencia confirma uma PO lida com divergência de soma, com {@code justificativa}
 */
public record PedidoConfirmacao(String exercicioInicio, String exercicioFim, UUID ataArquivoId, boolean semAta,
        LocalDate dataAprovacao, List<CodigoEfetivo> codigosEfetivos, List<LigacaoFundo> fundos, boolean reaprovacao,
        boolean cienteDivergencia, String justificativa) {

    public record CodigoEfetivo(UUID linhaId, String codigo) {
    }

    public record LigacaoFundo(UUID linhaId, UUID fundoId) {
    }

    public List<CodigoEfetivo> codigosEfetivos() {
        return codigosEfetivos == null ? List.of() : codigosEfetivos;
    }

    public List<LigacaoFundo> fundos() {
        return fundos == null ? List.of() : fundos;
    }
}
