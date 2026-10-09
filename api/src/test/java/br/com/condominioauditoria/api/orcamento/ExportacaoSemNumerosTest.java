package br.com.condominioauditoria.api.orcamento;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.service.audit.ConductTerms;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-03.1.14 sem golden: mês sem PO exporta a mensagem no lugar dos números, sem "PROVISÓRIO". */
class ExportacaoSemNumerosTest {

    @Test
    void mesSemPoExportaAMensagem() throws Exception {
        var calculo = CalculoPrevistoRealizado.calcular(new CalculoPrevistoRealizado.Entrada(null, null, null, null,
                null, null, UUID.randomUUID(), null, null, null, null, null,
                new CalculoPrevistoRealizado.Mes(YearMonth.of(2026, 4))));
        var rel = RelatorioPrevistoRealizado.montar("Condomínio Piloto", null, calculo, "usuario",
                Instant.parse("2026-10-04T15:30:00Z"));

        String pdf = ExportacaoPrevistoRealizadoGoldenTest.textoDoPdf(new RelatorioPdf().gerar(rel));
        var excel = ExportacaoPrevistoRealizadoGoldenTest.textosDoExcel(new RelatorioExcel().gerar(rel));

        assertThat(pdf).contains("Sem PO aprovada para 04/2026", "Período 04/2026", "Gerado por usuario")
                .doesNotContain("PROVISÓRIO");
        assertThat(excel).contains("Sem PO aprovada para 04/2026", "Resumo", "Evidência").doesNotContain("PROVISÓRIO");
        assertThat(ConductTerms.find(pdf)).isEmpty();
    }
}
