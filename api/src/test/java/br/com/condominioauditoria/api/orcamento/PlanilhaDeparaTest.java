package br.com.condominioauditoria.api.orcamento;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.api.model.budget.Budget;
import br.com.condominioauditoria.api.model.budget.BudgetLine;
import br.com.condominioauditoria.api.service.calculator.BudgetStructure;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RF-03.1.5 (planilha de sugestões): formato do mapa do piloto; linha inválida é listada e não entra. */
class PlanilhaDeparaTest {

    private final List<BudgetLine> linhas = PoDoPiloto.padrao().linhasGravadas(
            new Budget(UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64)));
    private final BudgetStructure estrutura = BudgetStructure.of(linhas);
    private final List<BudgetLine> destinos = ServicoDepara.destinosDeDebito(estrutura);
    private final List<BudgetLine> fundos = estrutura.funds().orElseThrow().lines();

    @Test
    void leOFormatoDoPiloto() {
        var leitura = PlanilhaDepara.ler("""
                conta_fluxo_administradora;linha_PO;status
                1621;1.7.8;sugerido - confirmar
                1324;AJUSTE (estorno);sugerido - confirmar
                1064;REALOCAR (cartão);sugerido - confirmar
                2133;TRANSFERÊNCIA (obras);sugerido - confirmar
                0028;1.6.1;sugerido - confirmar
                """, destinos, fundos);

        assertThat(leitura.recusas()).isEmpty();
        assertThat(leitura.itens()).extracting(PlanilhaDepara.Item::conta)
                .containsExactly("1621", "1324", "1064", "2133", "0028");
        assertThat(leitura.itens().get(0).destino().codigo()).isEqualTo("1.7.8");
        assertThat(leitura.itens().get(1).destino()).isEqualTo(Destino.especial(TipoDestino.AJUSTE, "estorno"));
        assertThat(leitura.itens().get(2).destino()).isEqualTo(Destino.especial(TipoDestino.A_REALOCAR, "cartão"));
        assertThat(leitura.itens().get(2).destino().texto()).isEqualTo("REALOCAR (cartão)");
        assertThat(leitura.itens().get(3).destino().tipo()).isEqualTo(TipoDestino.TRANSFERENCIA);
    }

    @Test
    void linhasInvalidasSaoListadasENaoEntram() {
        var leitura = PlanilhaDepara.ler("""
                1621;1.7.8
                1621;1.3.23
                abc;1.7.8
                1500;9.9.9
                1501;1.9.1
                1502;OUTRA COISA
                1503
                """, destinos, fundos);

        assertThat(leitura.itens()).hasSize(1);
        assertThat(leitura.recusas()).extracting(PlanilhaDepara.Recusa::linha).containsExactly(2, 3, 4, 5, 6, 7);
        assertThat(leitura.recusas().get(0).motivo()).contains("repetida");
        assertThat(leitura.recusas().get(2).motivo()).contains("não existe");
        assertThat(leitura.recusas().get(3).motivo()).contains("fundo");
    }

    @Test
    void semCabecalhoEComBomEFimDeLinhaWindows() {
        var leitura = PlanilhaDepara.ler("﻿1621;1.7.8\r\n1324;AJUSTE\r\n", destinos, fundos);

        assertThat(leitura.recusas()).isEmpty();
        assertThat(leitura.itens()).hasSize(2);
        assertThat(leitura.itens().get(1).destino().detalhe()).isNull();
    }
}
