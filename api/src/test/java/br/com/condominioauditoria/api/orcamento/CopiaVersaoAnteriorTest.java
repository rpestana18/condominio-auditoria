package br.com.condominioauditoria.api.orcamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** RF-03.1.4: nova versão da PO recebe o de-para anterior como sugestão; linha igual × linha mudou × não existe. */
class CopiaVersaoAnteriorTest {

    private final PrevisaoOrcamentaria v1 = new PrevisaoOrcamentaria(UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64));
    private final PrevisaoOrcamentaria v2 = new PrevisaoOrcamentaria(v1.getCondominioId(), UUID.randomUUID(), "b".repeat(64));
    private final LinhaPo sindico1 = linha(v1, "1.3.20", "1682 - Sindicatura Profissional", "Obm - Sergio Diniz", "8000.00");
    private final LinhaPo hidraulico1 = linha(v1, "1.7.8", "1659 - Material Hidráulico", "Material Hidráulico", "1800.00");
    private final LinhaPo pintura1 = linha(v1, "1.7.9", "1645 - Material de Pintura", "Material de pintura", "2300.00");
    private final Map<UUID, LinhaPo> anteriores = Map.of(sindico1.getId(), sindico1, hidraulico1.getId(), hidraulico1,
            pintura1.getId(), pintura1);
    // Versão 2: 1.3.20 só muda de valor; 1.7.8 muda de descrição; 1.7.9 deixa de existir
    private final Map<String, LinhaPo> novas = java.util.stream.Stream.of(
                    linha(v2, "1.3.20", "1682 - Sindicatura Profissional", "Obm - Sergio Diniz", "9000.00"),
                    linha(v2, "1.7.8", "1659 - Material Hidráulico", "Material hidráulico e elétrico", "1800.00"))
            .collect(Collectors.toMap(LinhaPo::getCodigoEfetivo, Function.identity()));

    @Test
    void linhaIgualMesmoComOutroValor() {
        var r = CopiaVersaoAnterior.copiar(confirmado("1108", Destino.linha(sindico1)), anteriores, novas);

        var c = (CopiaVersaoAnterior.Copiada) r;
        assertThat(c.igual()).isTrue();
        assertThat(c.destino().linhaPoId()).isEqualTo(novas.get("1.3.20").getId());
        assertThat(c.motivo()).startsWith("igual à versão anterior");
    }

    @Test
    void linhaQueMudouEntraComMotivo() {
        var r = CopiaVersaoAnterior.copiar(confirmado("1621", Destino.linha(hidraulico1)), anteriores, novas);

        var c = (CopiaVersaoAnterior.Copiada) r;
        assertThat(c.igual()).isFalse();
        assertThat(c.motivo()).startsWith("linha mudou").contains("Material Hidráulico")
                .contains("Material hidráulico e elétrico");
    }

    @Test
    void linhaQueNaoExisteFicaSemSugestao() {
        var r = CopiaVersaoAnterior.copiar(confirmado("1088", Destino.linha(pintura1)), anteriores, novas);

        assertThat(r).isInstanceOf(CopiaVersaoAnterior.NaoCopiada.class);
        assertThat(r.motivo()).isEqualTo("a linha 1.7.9 da versão anterior não existe nesta versão");
    }

    @Test
    void destinoEspecialSegueComoIgual() {
        var r = CopiaVersaoAnterior.copiar(confirmado("1324", Destino.especial(TipoDestino.AJUSTE, "estorno")),
                anteriores, novas);

        var c = (CopiaVersaoAnterior.Copiada) r;
        assertThat(c.igual()).isTrue();
        assertThat(c.destino().texto()).isEqualTo("AJUSTE (estorno)");
    }

    private DeparaConta confirmado(String conta, Destino destino) {
        return new DeparaConta(v1, conta, null, destino, EstadoDepara.CONFIRMADO, OrigemDepara.ADMIN, null, false, "admin",
                Instant.EPOCH);
    }

    private static LinhaPo linha(PrevisaoOrcamentaria po, String codigo, String conta, String descricao, String valor) {
        return new LinhaPo(po, 1, 1, TipoLinhaPo.LINHA, codigo, conta, null, null, descricao, new BigDecimal(valor),
                new BigDecimal(valor), null, null);
    }
}
