package br.com.condominioauditoria.backend.orcamento;

import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento.ConferenciaLida;
import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento.LinhaPoLida;
import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento.MarcaPo;
import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento.PrevisaoLida;
import br.com.condominioauditoria.backend.mensagens.ResultadoProcessamento.TipoLinhaPo;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * PO 2026/2027 do piloto, reduzida: subtotais, total e linhas citadas no RF-03.1.1 a RF-03.1.3 são os reais; as demais
 * linhas de cada grupo foram somadas numa linha só ("demais"). Como no PDF, as linhas de 1.3 somam 336.274,18
 * (impresso 336.274,17) e as de 1.9 somam 22.581,00 (impresso 22.581,01). As conferências imitam as do rag.
 */
public final class PoDoPiloto {

    private final List<LinhaPoLida> linhas = new ArrayList<>();
    private String subtotalPessoal = "69193.86";

    public static PoDoPiloto padrao() {
        return new PoDoPiloto();
    }

    /** Cópia de teste com o subtotal de Pessoal impresso diferente (RF-03.1.2). */
    public PoDoPiloto comSubtotalPessoal(String impresso) {
        this.subtotalPessoal = impresso;
        return this;
    }

    public PrevisaoLida previsao() {
        linhas.clear();
        add(TipoLinhaPo.TOTAL, "1", null, "Soma das seções 1.1 a 1.9", null, "TOTAL DAS DESPESAS", "474201.13", null);
        add(TipoLinhaPo.GRUPO, "1.1", null, "Subtotal (soma linhas 5 a 18)", null, "PESSOAL", subtotalPessoal, null);
        add(TipoLinhaPo.LINHA, "1.1.5", "1553 - Férias", null, null, "Provisão de Férias", "1585.14", "366,97%");
        add(TipoLinhaPo.LINHA, "1.1.1", "1500 - Demais", null, null, "Demais linhas de pessoal", "67608.72", null);
        add(TipoLinhaPo.GRUPO, "1.2", null, "Subtotal (soma linhas 20 a 21)", null, "CONSUMO/UTILIDADES", "694.05", null);
        add(TipoLinhaPo.LINHA, "1.2.1", "1560 - Consumo", null, null, "Consumo", "694.05", null);
        add(TipoLinhaPo.GRUPO, "1.3", null, "Subtotal (soma linhas 23 a 46)", null, "SERVIÇOS - CONTRATOS EFETIVOS", "336274.17", null);
        add(TipoLinhaPo.LINHA, "1.3.2", "1598 - Bombas", null, null, "Servirio Soluções Tecnicas Ltda", "3000.00", "-6,25%");
        add(TipoLinhaPo.LINHA, "1.3.20", "1682 - Sindicatura Profissional", null, null, "Obm - Sergio Diniz", "8000.00", "-53,47%");
        add(TipoLinhaPo.LINHA, "1.3.23", "1621 - Interfones", null, null, "Manutenção Preventiva De Interfones/Cftv", "0.00", null);
        add(TipoLinhaPo.LINHA, "1.3.1", "1692 - Demais", null, null, "Demais contratos", "323755.25", null);
        add(TipoLinhaPo.LINHA, "1.3.2", "1624 - Caixa D'água", null, null, "Caixa D'água", "1518.93", null);
        add(TipoLinhaPo.GRUPO, "1.4", null, "Subtotal (soma linhas 49 a 51)", null, "TARIFAS PÚBLICAS", "0.00", null);
        add(TipoLinhaPo.LINHA, "1.4.1", null, null, MarcaPo.RATEIO_A_PARTE, "Força e Luz", "0.00", null);
        add(TipoLinhaPo.LINHA, "1.4.2", null, null, MarcaPo.RATEIO_A_PARTE, "Água e Esgoto", "0.00", null);
        add(TipoLinhaPo.LINHA, "1.4.3", null, "Débito em receitas eventuais", MarcaPo.RATEIO_A_PARTE, "Gás", "0.00", null);
        add(TipoLinhaPo.GRUPO, "1.5", null, "Subtotal (soma linhas 53 a 55)", null, "AQUISIÇÃO DE BENS", "2850.00", null);
        add(TipoLinhaPo.LINHA, "1.5.1", "1700 - Bens", null, null, "Aquisição de bens", "2850.00", null);
        add(TipoLinhaPo.GRUPO, "1.6", null, "Subtotal (soma linhas 57 a 77)", null, "DESPESAS ADMINISTRATIVAS", "17388.04", null);
        add(TipoLinhaPo.LINHA, "1.6.15", null, null, MarcaPo.RATEIO_A_PARTE, "Seguro predial", "0.00", null);
        add(TipoLinhaPo.LINHA, "1.6.1", "1710 - Demais", null, null, "Demais administrativas", "17388.04", null);
        add(TipoLinhaPo.GRUPO, "1.7", null, "Subtotal (soma linhas 79 a 90)", null, "MATERIAIS/SUPRIMENTOS", "15200.00", null);
        add(TipoLinhaPo.LINHA, "1.7.8", "1606 - Material Hidráulico", null, null, "Material Hidráulico", "15200.00", null);
        add(TipoLinhaPo.GRUPO, "1.8", null, "Subtotal (soma linhas 92 a 99)", null, "SERVIÇOS", "10020.00", null);
        add(TipoLinhaPo.LINHA, "1.8.1", "1693 - Serviços", null, null, "Serviços", "10020.00", null);
        add(TipoLinhaPo.GRUPO, "1.9", null, "Fundos", null, "Fundos do Condomínio", "22581.01", null);
        add(TipoLinhaPo.LINHA, "1.9.1", null, "Fundo de Reserva", null, "Fundo de Reserva", "13548.60", "3,00%");
        add(TipoLinhaPo.LINHA, "1.9.2", null, "Obras Reformas e Infraestrutura", null, "Fundo de Obras", "9032.40", "2,00%");
        return new PrevisaoLida("PROPOSTA ORÇAMENTÁRIA 2026 / 2027", "2026 / 2027", List.of("2025/2026", "2026/2027"),
                List.copyOf(linhas));
    }

    /** Como o rag confere (exato, ao centavo): 1.3 e 1.9 falham por 0,01; o 1.3.2 aparece duas vezes. */
    public List<ConferenciaLida> conferencias() {
        boolean pessoalOk = subtotalPessoal.equals("69193.86");
        return List.of(
                new ConferenciaLida("SUBTOTAL_GRUPO", "Soma das linhas do grupo 1.1 = subtotal impresso", pessoalOk,
                        "1.1 PESSOAL: soma das linhas 69.193,86; impresso " + subtotalPessoal),
                ok("1.2"), falha("1.3", "336.274,18", "336.274,17"), ok("1.4"), ok("1.5"), ok("1.6"), ok("1.7"),
                ok("1.8"), falha("1.9", "22.581,00", "22.581,01"),
                new ConferenciaLida("TOTAL", "Soma dos grupos = total impresso", pessoalOk, "soma dos grupos x impresso"),
                new ConferenciaLida("PREVISTO_MES", "Previsto do mês = total menos os fundos", pessoalOk,
                        "474.201,13 - 22.581,01 = 451.620,12"),
                new ConferenciaLida("FUNDO_TAXA", "Cada fundo = taxa da coluna % sobre o previsto do mês", true,
                        "1.9.1: 3,00% de 451.620,12 = 13.548,60"),
                new ConferenciaLida("CODIGO_REPETIDO", "Nenhum código de linha impresso mais de uma vez", false,
                        "1.3.2 aparece 2 vezes (ordens 9 e 13)"));
    }

    /** Linhas como o backend grava, para testar as funções puras sem banco. */
    public List<LinhaPo> linhasGravadas(PrevisaoOrcamentaria previsao) {
        return previsao().linhas().stream().map(l -> GravacaoPrevisao.linha(previsao, l)).toList();
    }

    public List<AvaliacaoLeituraPo.ConferenciaPo> conferenciasParaAvaliacao() {
        return GravacaoPrevisao.paraAvaliacao(conferencias());
    }

    private static ConferenciaLida ok(String grupo) {
        return new ConferenciaLida("SUBTOTAL_GRUPO", "Soma das linhas do grupo " + grupo + " = subtotal impresso", true,
                grupo + ": confere");
    }

    private static ConferenciaLida falha(String grupo, String soma, String impresso) {
        return new ConferenciaLida("SUBTOTAL_GRUPO", "Soma das linhas do grupo " + grupo + " = subtotal impresso", false,
                grupo + ": soma das linhas " + soma + "; impresso " + impresso + "; diferença 0,01");
    }

    private void add(TipoLinhaPo tipo, String codigo, String conta, String contaTexto, MarcaPo marca, String descricao,
            String orcado, String percentual) {
        linhas.add(new LinhaPoLida(linhas.size() + 1, 1, tipo, codigo, conta, contaTexto, marca, descricao,
                new BigDecimal("0.00"), new BigDecimal(orcado), percentual, null));
    }
}
