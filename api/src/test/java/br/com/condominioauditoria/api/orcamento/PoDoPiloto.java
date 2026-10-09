package br.com.condominioauditoria.api.orcamento;

import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.BudgetData;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.BudgetLineData;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.BudgetLineMark;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.BudgetLineType;
import br.com.condominioauditoria.api.messaging.ProcessingResultMessage.TotalsCheckData;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * PO 2026/2027 do piloto, reduzida: subtotais, total e linhas citadas no RF-03.1.1 a RF-03.1.3 são os reais; as demais
 * linhas de cada grupo foram somadas numa linha só ("demais"). Como no PDF, as linhas de 1.3 somam 336.274,18
 * (impresso 336.274,17) e as de 1.9 somam 22.581,00 (impresso 22.581,01). As conferências imitam as do rag.
 */
public final class PoDoPiloto {

    private final List<BudgetLineData> linhas = new ArrayList<>();
    private String subtotalPessoal = "69193.86";
    private String fundoReserva = null;
    private boolean aparelhosDeGinastica = false;
    private boolean colunaAnterior = false;
    private String totalAnterior = null;

    /**
     * Coluna "Orçado anterior" (2025/2026) com os valores reais do PDF nos grupos e nas linhas citadas (RF-11.5): total
     * 441.304,38 sem os fundos, fundos 22.065,22, 1.3.20 17.195,00. Como no PDF, o grupo 1.6 imprime 18.525,42 e deixa
     * de fora a linha 1.6.21 (220,83). As demais linhas de cada grupo foram somadas na linha "demais".
     */
    private static final java.util.Map<String, String> ANTERIOR = java.util.Map.ofEntries(
            java.util.Map.entry("1|null", "441304.38"), java.util.Map.entry("1.1|null", "37661.43"),
            java.util.Map.entry("1.1.5|1553 - Férias", "339.45"), java.util.Map.entry("1.1.1|1500 - Demais", "37321.98"),
            java.util.Map.entry("1.2|null", "565.00"), java.util.Map.entry("1.2.1|1560 - Consumo", "565.00"),
            java.util.Map.entry("1.3|null", "348631.55"), java.util.Map.entry("1.3.2|1598 - Bombas", "3200.00"),
            java.util.Map.entry("1.3.20|1682 - Sindicatura Profissional", "17195.00"),
            java.util.Map.entry("1.3.1|1692 - Demais", "328236.55"), java.util.Map.entry("1.5|null", "2350.00"),
            java.util.Map.entry("1.5.1|1700 - Bens", "2350.00"), java.util.Map.entry("1.6|null", "18525.42"),
            java.util.Map.entry("1.6.1|1710 - Demais", "18525.42"),
            java.util.Map.entry("1.6.21|1346 - Outros Serviços Contratados", "220.83"),
            java.util.Map.entry("1.7|null", "19300.00"), java.util.Map.entry("1.7.8|1606 - Material Hidráulico", "19300.00"),
            java.util.Map.entry("1.8|null", "14270.99"), java.util.Map.entry("1.8.1|1693 - Serviços", "14270.99"),
            java.util.Map.entry("1.9|null", "22065.22"), java.util.Map.entry("1.9.1|null", "13239.13"),
            java.util.Map.entry("1.9.2|null", "8826.09"));

    public static PoDoPiloto padrao() {
        return new PoDoPiloto();
    }

    /** Cópia de teste com o subtotal de Pessoal impresso diferente (RF-03.1.2). */
    public PoDoPiloto comSubtotalPessoal(String impresso) {
        this.subtotalPessoal = impresso;
        return this;
    }

    /** Cópia de teste com outro fundo de reserva; o subtotal de fundos e o total acompanham (somas batem). */
    public PoDoPiloto comFundoReserva(String valor) {
        this.fundoReserva = valor;
        return this;
    }

    /**
     * Cópia de teste com a conta 1606 "Aparelhos de Ginástica" em 1.3.5 e 1.7.2, como na PO real (RF-11.7), com
     * valor zero para as somas continuarem batendo.
     */
    public PoDoPiloto comAparelhosDeGinastica() {
        this.aparelhosDeGinastica = true;
        return this;
    }

    /** Coluna "Orçado anterior" com os valores reais do PDF (ver {@link #ANTERIOR}). */
    public PoDoPiloto comColunaAnterior() {
        this.colunaAnterior = true;
        return this;
    }

    /** Coluna "Orçado anterior" com outro total impresso (ex.: incluindo os fundos). */
    public PoDoPiloto comTotalAnterior(String total) {
        this.colunaAnterior = true;
        this.totalAnterior = total;
        return this;
    }

    public BudgetData previsao() {
        linhas.clear();
        String reserva = fundoReserva == null ? "13548.60" : fundoReserva;
        String fundos = fundoReserva == null ? "22581.01" : new BigDecimal(fundoReserva).add(new BigDecimal("9032.40")).toPlainString();
        String total = fundoReserva == null ? "474201.13" : new BigDecimal("451620.12").add(new BigDecimal(fundos)).toPlainString();
        add(BudgetLineType.TOTAL, "1", null, "Soma das seções 1.1 a 1.9", null, "TOTAL DAS DESPESAS", total, null);
        add(BudgetLineType.GRUPO, "1.1", null, "Subtotal (soma linhas 5 a 18)", null, "PESSOAL", subtotalPessoal, null);
        add(BudgetLineType.LINHA, "1.1.5", "1553 - Férias", null, null, "Provisão de Férias", "1585.14", "366,97%");
        add(BudgetLineType.LINHA, "1.1.1", "1500 - Demais", null, null, "Demais linhas de pessoal", "67608.72", null);
        add(BudgetLineType.GRUPO, "1.2", null, "Subtotal (soma linhas 20 a 21)", null, "CONSUMO/UTILIDADES", "694.05", null);
        add(BudgetLineType.LINHA, "1.2.1", "1560 - Consumo", null, null, "Consumo", "694.05", null);
        add(BudgetLineType.GRUPO, "1.3", null, "Subtotal (soma linhas 23 a 46)", null, "SERVIÇOS - CONTRATOS EFETIVOS", "336274.17", null);
        add(BudgetLineType.LINHA, "1.3.2", "1598 - Bombas", null, null, "Servirio Soluções Tecnicas Ltda", "3000.00", "-6,25%");
        if (aparelhosDeGinastica) {
            add(BudgetLineType.LINHA, "1.3.5", "1606 - Aparelhos de Ginástica", null, null, "Manutenção Academia", "0.00", null);
        }
        add(BudgetLineType.LINHA, "1.3.20", "1682 - Sindicatura Profissional", null, null, "Obm - Sergio Diniz", "8000.00", "-53,47%");
        add(BudgetLineType.LINHA, "1.3.23", "1621 - Interfones", null, null, "Manutenção Preventiva De Interfones/Cftv", "0.00", null);
        add(BudgetLineType.LINHA, "1.3.1", "1692 - Demais", null, null, "Demais contratos", "323755.25", null);
        add(BudgetLineType.LINHA, "1.3.2", "1624 - Caixa D'água", null, null, "Caixa D'água", "1518.93", null);
        add(BudgetLineType.GRUPO, "1.4", null, "Subtotal (soma linhas 49 a 51)", null, "TARIFAS PÚBLICAS", "0.00", null);
        add(BudgetLineType.LINHA, "1.4.1", null, null, BudgetLineMark.RATEIO_A_PARTE, "Força e Luz", "0.00", null);
        add(BudgetLineType.LINHA, "1.4.2", null, null, BudgetLineMark.RATEIO_A_PARTE, "Água e Esgoto", "0.00", null);
        add(BudgetLineType.LINHA, "1.4.3", null, "Débito em receitas eventuais", BudgetLineMark.RATEIO_A_PARTE, "Gás", "0.00", null);
        add(BudgetLineType.GRUPO, "1.5", null, "Subtotal (soma linhas 53 a 55)", null, "AQUISIÇÃO DE BENS", "2850.00", null);
        add(BudgetLineType.LINHA, "1.5.1", "1700 - Bens", null, null, "Aquisição de bens", "2850.00", null);
        add(BudgetLineType.GRUPO, "1.6", null, "Subtotal (soma linhas 57 a 77)", null, "DESPESAS ADMINISTRATIVAS", "17388.04", null);
        add(BudgetLineType.LINHA, "1.6.15", null, null, BudgetLineMark.RATEIO_A_PARTE, "Seguro predial", "0.00", null);
        add(BudgetLineType.LINHA, "1.6.1", "1710 - Demais", null, null, "Demais administrativas", "17388.04", null);
        if (colunaAnterior) {
            add(BudgetLineType.LINHA, "1.6.21", "1346 - Outros Serviços Contratados", null, null, "E-mail GoDaddy (anual)",
                    "0.00", "-32,07%");
        }
        add(BudgetLineType.GRUPO, "1.7", null, "Subtotal (soma linhas 79 a 90)", null, "MATERIAIS/SUPRIMENTOS", "15200.00", null);
        if (aparelhosDeGinastica) {
            add(BudgetLineType.LINHA, "1.7.2", "1606 - Aparelhos de Ginástica", null, null, "Peças de Ginástica", "0.00", null);
        }
        add(BudgetLineType.LINHA, "1.7.8", "1606 - Material Hidráulico", null, null, "Material Hidráulico", "15200.00", null);
        add(BudgetLineType.GRUPO, "1.8", null, "Subtotal (soma linhas 92 a 99)", null, "SERVIÇOS", "10020.00", null);
        add(BudgetLineType.LINHA, "1.8.1", "1693 - Serviços", null, null, "Serviços", "10020.00", null);
        add(BudgetLineType.GRUPO, "1.9", null, "Fundos", null, "Fundos do Condomínio", fundos, null);
        add(BudgetLineType.LINHA, "1.9.1", null, "Fundo de Reserva", null, "Fundo de Reserva", reserva, "3,00%");
        add(BudgetLineType.LINHA, "1.9.2", null, "Obras Reformas e Infraestrutura", null, "Fundo de Obras", "9032.40", "2,00%");
        return new BudgetData("PROPOSTA ORÇAMENTÁRIA 2026 / 2027", "2026 / 2027", List.of("2025/2026", "2026/2027"),
                List.copyOf(linhas));
    }

    /** Como o rag confere (exato, ao centavo): 1.3 e 1.9 falham por 0,01; o 1.3.2 aparece duas vezes. */
    public List<TotalsCheckData> conferencias() {
        boolean pessoalOk = subtotalPessoal.equals("69193.86");
        return List.of(
                new TotalsCheckData("SUBTOTAL_GRUPO", "Soma das linhas do grupo 1.1 = subtotal impresso", pessoalOk,
                        "1.1 PESSOAL: soma das linhas 69.193,86; impresso " + subtotalPessoal),
                ok("1.2"), falha("1.3", "336.274,18", "336.274,17"), ok("1.4"), ok("1.5"), ok("1.6"), ok("1.7"),
                ok("1.8"), fundoReserva == null ? falha("1.9", "22.581,00", "22.581,01") : ok("1.9"),
                new TotalsCheckData("TOTAL", "Soma dos grupos = total impresso", pessoalOk, "soma dos grupos x impresso"),
                new TotalsCheckData("PREVISTO_MES", "Previsto do mês = total menos os fundos", pessoalOk,
                        "474.201,13 - 22.581,01 = 451.620,12"),
                new TotalsCheckData("FUNDO_TAXA", "Cada fundo = taxa da coluna % sobre o previsto do mês", true,
                        "1.9.1: 3,00% de 451.620,12 = 13.548,60"),
                new TotalsCheckData("CODIGO_REPETIDO", "Nenhum código de linha impresso mais de uma vez", false,
                        "1.3.2 aparece 2 vezes (ordens 8 e 12)"));
    }

    /** Linhas como o backend grava, para testar as funções puras sem banco. */
    public List<LinhaPo> linhasGravadas(PrevisaoOrcamentaria previsao) {
        return previsao().lines().stream().map(l -> GravacaoPrevisao.linha(previsao, l)).toList();
    }

    public List<AvaliacaoLeituraPo.ConferenciaPo> conferenciasParaAvaliacao() {
        return GravacaoPrevisao.paraAvaliacao(conferencias());
    }

    private static TotalsCheckData ok(String grupo) {
        return new TotalsCheckData("SUBTOTAL_GRUPO", "Soma das linhas do grupo " + grupo + " = subtotal impresso", true,
                grupo + ": confere");
    }

    private static TotalsCheckData falha(String grupo, String soma, String impresso) {
        return new TotalsCheckData("SUBTOTAL_GRUPO", "Soma das linhas do grupo " + grupo + " = subtotal impresso", false,
                grupo + ": soma das linhas " + soma + "; impresso " + impresso + "; diferença 0,01");
    }

    private void add(BudgetLineType tipo, String codigo, String conta, String contaTexto, BudgetLineMark marca, String descricao,
            String orcado, String percentual) {
        linhas.add(new BudgetLineData(linhas.size() + 1, 1, tipo, codigo, conta, contaTexto, marca, descricao,
                new BigDecimal(anterior(codigo, conta)), new BigDecimal(orcado), percentual, null));
    }

    private String anterior(String codigo, String conta) {
        if (!colunaAnterior) {
            return "0.00";
        }
        if (totalAnterior != null && codigo.equals("1") && conta == null) {
            return totalAnterior;
        }
        return ANTERIOR.getOrDefault(codigo + "|" + conta, "0.00");
    }
}
