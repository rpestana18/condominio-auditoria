package br.com.condominioauditoria.backend.modulo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.backend.modulo.RegistroUso.ResumoUso;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Exportação dos períodos ativos e do uso para o Excel (RF-10.6, RF-09.7), em CSV enquanto o POI não entra. */
class ExportacaoUsoCsvTest {

    @Test
    void geraAsDuasSecoesComBomSeparadorEHorarioDeBrasilia() {
        var uso = new ResumoUso(UUID.randomUUID(), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 31), List.of(),
                List.of(new TotalUso("2026-11", Modulos.ASSISTENTE, FuncaoUso.CHAMADA_MCP, 12, 0, 0, 0, 0)));
        var periodo = new PeriodoAtivo(Modulos.ASSISTENTE, Instant.parse("2026-11-01T13:00:00Z"),
                Instant.parse("2026-12-15T18:30:00Z"), "ana", "Contrato; assinado", "bruno", "=HYPERLINK(\"x\")");

        String csv = new String(ExportacaoUsoCsv.gerar("Mio Residencial Parque", uso, List.of(periodo)),
                StandardCharsets.UTF_8);

        assertThat(csv).startsWith("﻿Condomínio;Mio Residencial Parque\r\n");
        assertThat(csv).contains("Período;01/11/2026 a 31/12/2026\r\n");
        assertThat(csv).contains("Períodos ativos\r\n");
        assertThat(csv).contains("ASSISTENTE;01/11/2026 10:00:00;15/12/2026 15:30:00;ana;\"Contrato; assinado\";bruno;"
                + "\"'=HYPERLINK(\"\"x\"\")\"\r\n");
        assertThat(csv).contains("Uso por mês\r\n");
        assertThat(csv).contains("2026-11;ASSISTENTE;chamada_mcp;12;0;0;0;0\r\n");
    }

    @Test
    void periodoAbertoApareceComoAindaLigado() {
        var uso = new ResumoUso(UUID.randomUUID(), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), List.of(),
                List.of());
        var aberto = new PeriodoAtivo(Modulos.ASSISTENTE, Instant.parse("2026-11-01T13:00:00Z"), null, "ana", "m",
                null, null);

        String csv = new String(ExportacaoUsoCsv.gerar("C", uso, List.of(aberto)), StandardCharsets.UTF_8);

        assertThat(csv).contains("ASSISTENTE;01/11/2026 10:00:00;ainda ligado;ana;m;;\r\n");
    }

    @Test
    void celulaQueComecaComSinalNaoViraFormula() {
        assertThat(ExportacaoUsoCsv.celula("-1+1")).isEqualTo("'-1+1");
        assertThat(ExportacaoUsoCsv.celula("@SOMA")).isEqualTo("'@SOMA");
        assertThat(ExportacaoUsoCsv.celula("normal")).isEqualTo("normal");
        assertThat(ExportacaoUsoCsv.celula(null)).isEmpty();
    }
}
