package br.com.condominioauditoria.backend.modulo;

import br.com.condominioauditoria.backend.modulo.RegistroUso.ResumoUso;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Períodos ativos e uso do período em CSV para o Excel em português (RF-10.6, RF-09.7): UTF-8 com BOM, separador
 * ";", linhas CRLF. Duas seções no mesmo arquivo. Célula que começa com = + - @ ganha um apóstrofo na frente, para o
 * Excel não tratar texto livre (ex.: motivo) como fórmula.
 *
 * O formato .xlsx (Apache POI) fica para quando a biblioteca entrar no backend: hoje ela não está no catálogo.
 */
public final class ExportacaoUsoCsv {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(RegistroUso.FUSO);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String BOM = "﻿";
    private static final String FIM_DE_LINHA = "\r\n";

    private ExportacaoUsoCsv() {
    }

    public static byte[] gerar(String nomeCondominio, ResumoUso uso, List<PeriodoAtivo> periodos) {
        var csv = new StringBuilder(BOM);
        linha(csv, "Condomínio", nomeCondominio);
        linha(csv, "Período", DATA.format(uso.inicio()) + " a " + DATA.format(uso.fim()));
        csv.append(FIM_DE_LINHA);

        linha(csv, "Períodos ativos");
        linha(csv, "Módulo", "Início", "Fim", "Ligado por", "Motivo ao ligar", "Desligado por", "Motivo ao desligar");
        for (PeriodoAtivo p : periodos) {
            linha(csv, p.modulo(), dataHora(p.inicio()), p.fim() == null ? "ainda ligado" : dataHora(p.fim()),
                    p.ligadoPor(), p.motivoLigar(), p.desligadoPor(), p.motivoDesligar());
        }
        csv.append(FIM_DE_LINHA);

        linha(csv, "Uso por mês");
        linha(csv, "Mês", "Módulo", "Função", "Quantidade", "Tokens de entrada", "Tokens de saída", "Arquivos",
                "Páginas");
        for (TotalUso t : uso.porMes()) {
            linha(csv, t.mes(), t.modulo(), t.funcao().codigo(), Long.toString(t.quantidade()),
                    Long.toString(t.tokensEntrada()), Long.toString(t.tokensSaida()), Long.toString(t.arquivos()),
                    Long.toString(t.paginas()));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String dataHora(Instant instante) {
        return instante == null ? "" : DATA_HORA.format(instante);
    }

    private static void linha(StringBuilder csv, String... celulas) {
        for (int i = 0; i < celulas.length; i++) {
            if (i > 0) {
                csv.append(';');
            }
            csv.append(celula(celulas[i]));
        }
        csv.append(FIM_DE_LINHA);
    }

    static String celula(String valor) {
        if (valor == null) {
            return "";
        }
        String texto = valor;
        if (!texto.isEmpty() && "=+-@".indexOf(texto.charAt(0)) >= 0) {
            texto = "'" + texto;
        }
        if (texto.contains(";") || texto.contains("\"") || texto.contains("\n") || texto.contains("\r")) {
            texto = "\"" + texto.replace("\"", "\"\"") + "\"";
        }
        return texto;
    }
}
