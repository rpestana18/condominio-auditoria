package br.com.condominioauditoria.api.painel;

import br.com.condominioauditoria.api.painel.PainelService.FundoNoPeriodo;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Saldo acumulado do fundo ordinário no cartão da tela inicial (RF-05.1a e RF-05.1b).
 *
 * <p>Com fundo confirmado, usa o saldo dele no relatório; se o fundo não está no relatório, o saldo vem nulo. Sem
 * confirmação, sugere o fundo com mais entradas no mês, que é onde caem as cotas ordinárias. A sugestão nunca vira
 * número na tela sem a confirmação do Gestor ou do Admin.
 *
 * @param saldoAtual nulo quando o fundo confirmado não aparece no relatório
 */
public record FundoOrdinario(UUID fundoId, String fundo, boolean confirmado, BigDecimal saldoAtual) {

    static Optional<FundoOrdinario> de(UUID confirmadoId, String nomeConfirmado, List<FundoNoPeriodo> fundos) {
        if (confirmadoId != null) {
            BigDecimal saldo = fundos.stream().filter(f -> confirmadoId.equals(f.fundoId())).findFirst()
                    .map(FundoNoPeriodo::saldoAtual).orElse(null);
            return Optional.of(new FundoOrdinario(confirmadoId, nomeConfirmado, true, saldo));
        }
        return fundos.stream()
                .filter(f -> f.entradas().signum() > 0)
                .max(Comparator.comparing(FundoNoPeriodo::entradas))
                .map(f -> new FundoOrdinario(f.fundoId(), f.fundo(), false, f.saldoAtual()));
    }
}
