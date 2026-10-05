package br.com.condominioauditoria.backend.orcamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.backend.arquivo.Categoria;
import br.com.condominioauditoria.backend.auditoria.RegraTetoFundoReserva;
import br.com.condominioauditoria.backend.auditoria.Severidade;
import br.com.condominioauditoria.backend.orcamento.PedidoConfirmacao.LigacaoFundo;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.AvisoDto;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.CodigoAviso;
import br.com.condominioauditoria.backend.orcamento.PrevisaoDtos.FundoPoDto;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** RF-03.1.3, RF-03.1.2 e Q29: confirmação da PO pelo Admin. */
class ConfirmacaoPrevisaoTest {

    private final CenarioPo cenario = new CenarioPo();

    @Test
    void confirmaAPoDoPilotoComExercicioAtaCodigoEfetivoEFundos() {
        PrevisaoOrcamentaria po = cenario.lerPo(PoDoPiloto.padrao());

        var detalhe = cenario.confirmacao.confirmar(cenario.condominioId, po.getId(), cenario.pedidoDoPiloto(po), "admin");

        assertThat(po.getEstado()).isEqualTo(EstadoPrevisao.CONFIRMADA);
        assertThat(po.getVersao()).isEqualTo(1);
        assertThat(po.getExercicioInicio()).isEqualTo(YearMonth.of(2026, 5));
        assertThat(po.getExercicioFim()).isEqualTo(YearMonth.of(2027, 4));
        assertThat(po.getAtaArquivoId()).isEqualTo(cenario.ata.getId());
        assertThat(po.isSemAta()).isFalse();
        assertThat(po.getConfirmadaPor()).isEqualTo("admin");
        assertThat(po.isCienteDivergencia()).isFalse();
        // Nenhuma das duas linhas 1.3.2 é descartada; a segunda recebe o código efetivo
        assertThat(cenario.linha(po, "1.3.2", 0).getCodigoEfetivo()).isEqualTo("1.3.2");
        assertThat(cenario.linha(po, "1.3.2", 1).getCodigoEfetivo()).isEqualTo("1.3.25");
        assertThat(cenario.linha(po, "1.3.2", 1).getOrcado()).isEqualByComparingTo("1518.93");
        assertThat(detalhe.codigosRepetidos()).singleElement().satisfies(r -> assertThat(r.resolvido()).isTrue());

        assertThat(detalhe.fundos()).extracting(FundoPoDto::codigoEfetivo, FundoPoDto::fundo).containsExactly(
                org.assertj.core.groups.Tuple.tuple("1.9.1", "FUNDO DE RESERVA"),
                org.assertj.core.groups.Tuple.tuple("1.9.2", "OBRAS / REFORMAS / INFRA"));
        assertThat(detalhe.fundos()).extracting(FundoPoDto::orcado)
                .usingElementComparator(java.math.BigDecimal::compareTo)
                .containsExactly(new java.math.BigDecimal("13548.60"), new java.math.BigDecimal("9032.40"));

        assertThat(cenario.eventos).singleElement().satisfies(e -> {
            assertThat(e.getTipo()).isEqualTo(EventoPrevisao.CONFIRMADA);
            assertThat(e.getUsuario()).isEqualTo("admin");
            assertThat(e.getEm()).isNotNull();
            assertThat(e.getDetalhe()).contains("Exercício 2026-05 a 2027-04", "1.3.2 (ordem 12, Caixa D'água) → 1.3.25",
                    "1.9.1 Fundo de Reserva → FUNDO DE RESERVA", "1.9.2 Fundo de Obras → OBRAS / REFORMAS / INFRA",
                    "Diferenças tratadas como arredondamento");
        });
    }

    @Test
    void poAprovadaEmMaioGeraSoAvisoSemAchado() {
        PrevisaoOrcamentaria po = cenario.lerPo(PoDoPiloto.padrao());

        var detalhe = cenario.confirmacao.confirmar(cenario.condominioId, po.getId(), cenario.pedidoDoPiloto(po), "admin");

        assertThat(detalhe.avisos()).extracting(AvisoDto::texto)
                .contains("PO aprovada fora do 1º trimestre (Conv. 10.2)");
        assertThat(detalhe.achados()).isEmpty();
        assertThat(cenario.achados).isEmpty();
        // Os arredondamentos de 1.3 e 1.9 continuam visíveis como aviso
        assertThat(detalhe.avisos()).filteredOn(a -> a.codigo() == CodigoAviso.ARREDONDAMENTO).hasSize(2);
    }

    @Test
    void poAprovadaNoPrimeiroTrimestreNaoTemAviso() {
        PrevisaoOrcamentaria po = cenario.lerPo(PoDoPiloto.padrao());
        var p = cenario.pedidoDoPiloto(po);
        var pedido = new PedidoConfirmacao("2026-03", "2027-02", p.ataArquivoId(), false, LocalDate.of(2026, 3, 15),
                p.codigosEfetivos(), p.fundos(), false, false, null);

        var detalhe = cenario.confirmacao.confirmar(cenario.condominioId, po.getId(), pedido, "admin");

        assertThat(detalhe.avisos()).extracting(AvisoDto::codigo).doesNotContain(CodigoAviso.FORA_PRIMEIRO_TRIMESTRE);
    }

    @Test
    void fundosDoPilotoDe3e2PorCentoNaoGeramAchado() {
        PrevisaoOrcamentaria po = cenario.lerPo(PoDoPiloto.padrao());

        cenario.confirmacao.confirmar(cenario.condominioId, po.getId(), cenario.pedidoDoPiloto(po), "admin");

        assertThat(cenario.achados).isEmpty();
    }

    @Test
    void fundoDeReservaAcimaDe5PorCentoGeraAchadoDeAtencaoComEvidencia() {
        PrevisaoOrcamentaria po = cenario.lerPo(PoDoPiloto.padrao().comFundoReserva("25000.00"));
        assertThat(po.getEstado()).isEqualTo(EstadoPrevisao.LIDA);

        var detalhe = cenario.confirmacao.confirmar(cenario.condominioId, po.getId(), cenario.pedidoDoPiloto(po), "admin");

        assertThat(cenario.achados).singleElement().satisfies(a -> {
            assertThat(a.getRegra()).isEqualTo(RegraTetoFundoReserva.CODIGO);
            assertThat(a.getVersaoRegra()).isEqualTo(RegraTetoFundoReserva.VERSAO);
            assertThat(a.getSeveridade()).isEqualTo(Severidade.ATENCAO);
            assertThat(a.getCompetencia()).isEqualTo(YearMonth.of(2026, 5));
            assertThat(a.getDescricao()).isEqualTo("Fundo de reserva previsto na PO (linha 1.9.1): 25.000,00 por mês,"
                    + " 5,5% do previsto do mês (451.620,13). O teto da Conv. 20.1 é 5%. Verificar a ata que aprovou a PO.");
        });
        assertThat(cenario.evidencias).singleElement().satisfies(e -> {
            assertThat(e.getArquivoId()).isEqualTo(po.getArquivoId());
            assertThat(e.getSha256()).isEqualTo(po.getSha256());
            assertThat(e.getPagina()).isEqualTo(1);
            assertThat(e.getLinhaPoId()).isEqualTo(cenario.linha(po, "1.9.1", 0).getId());
        });
        assertThat(detalhe.achados()).singleElement().satisfies(a -> assertThat(a.severidade()).isEqualTo("ATENCAO"));
    }

    @Test
    void semTetoCadastradoARegraNaoEhAvaliadaEApareceComoAviso() {
        cenario.parametros.clear();
        PrevisaoOrcamentaria po = cenario.lerPo(PoDoPiloto.padrao().comFundoReserva("25000.00"));

        var detalhe = cenario.confirmacao.confirmar(cenario.condominioId, po.getId(), cenario.pedidoDoPiloto(po), "admin");

        assertThat(cenario.achados).isEmpty();
        assertThat(detalhe.avisos()).filteredOn(a -> a.codigo() == CodigoAviso.REGRA_NAO_AVALIADA).singleElement()
                .satisfies(a -> assertThat(a.texto()).contains("teto não cadastrado"));
    }

    @Test
    void codigoRepetidoSemCodigoEfetivoRecusaAConfirmacao() {
        PrevisaoOrcamentaria po = cenario.lerPo(PoDoPiloto.padrao());
        var p = cenario.pedidoDoPiloto(po);
        var semCodigo = new PedidoConfirmacao(p.exercicioInicio(), p.exercicioFim(), p.ataArquivoId(), false,
                p.dataAprovacao(), List.of(), p.fundos(), false, false, null);

        assertThatThrownBy(() -> cenario.confirmacao.confirmar(cenario.condominioId, po.getId(), semCodigo, "admin"))
                .isInstanceOfSatisfying(ConfirmacaoRecusadaException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(e.motivos()).singleElement().asString()
                            .startsWith("Código repetido sem código efetivo distinto: 1.3.2 (ordens 8, 12)");
                });
        assertThat(po.getEstado()).isEqualTo(EstadoPrevisao.LIDA);
        assertThat(cenario.linha(po, "1.3.2", 1).getCodigoEfetivo()).isEqualTo("1.3.2");
        assertThat(cenario.eventos).isEmpty();
        assertThat(cenario.poFundos).isEmpty();
    }

    @Test
    void codigoEfetivoForaDoGrupoOuEmLinhaNaoRepetidaEhRecusado() {
        PrevisaoOrcamentaria po = cenario.lerPo(PoDoPiloto.padrao());
        var p = cenario.pedidoDoPiloto(po);
        var pedido = new PedidoConfirmacao(p.exercicioInicio(), p.exercicioFim(), p.ataArquivoId(), false,
                p.dataAprovacao(), List.of(
                        new PedidoConfirmacao.CodigoEfetivo(cenario.linha(po, "1.3.2", 1).getId(), "1.7.25"),
                        new PedidoConfirmacao.CodigoEfetivo(cenario.linha(po, "1.3.20", 0).getId(), "1.3.26")),
                p.fundos(), false, false, null);

        assertThatThrownBy(() -> cenario.confirmacao.confirmar(cenario.condominioId, po.getId(), pedido, "admin"))
                .isInstanceOfSatisfying(ConfirmacaoRecusadaException.class, e -> assertThat(e.motivos()).anySatisfy(
                        m -> assertThat(m).contains("\"1.7.25\" inválido")).anySatisfy(
                        m -> assertThat(m).contains("1.3.20 não se repete")));
    }

    @Test
    void codigoEfetivoIgualAOutroCodigoContinuaRepetido() {
        PrevisaoOrcamentaria po = cenario.lerPo(PoDoPiloto.padrao());
        var p = cenario.pedidoDoPiloto(po);
        var pedido = new PedidoConfirmacao(p.exercicioInicio(), p.exercicioFim(), p.ataArquivoId(), false,
                p.dataAprovacao(), List.of(new PedidoConfirmacao.CodigoEfetivo(cenario.linha(po, "1.3.2", 1).getId(),
                        "1.3.20")), p.fundos(), false, false, null);

        assertThatThrownBy(() -> cenario.confirmacao.confirmar(cenario.condominioId, po.getId(), pedido, "admin"))
                .isInstanceOfSatisfying(ConfirmacaoRecusadaException.class, e -> assertThat(e.motivos())
                        .anySatisfy(m -> assertThat(m).startsWith("Código repetido sem código efetivo distinto: 1.3.20")));
    }

    @Test
    void poComDivergenciaSoEhConfirmadaCienteEComJustificativa() {
        PrevisaoOrcamentaria po = cenario.lerPo(PoDoPiloto.padrao().comSubtotalPessoal("69193.00"));
        assertThat(po.getEstado()).isEqualTo(EstadoPrevisao.LIDA_COM_DIVERGENCIA);
        var p = cenario.pedidoDoPiloto(po);

        assertThatThrownBy(() -> cenario.confirmacao.confirmar(cenario.condominioId, po.getId(), p, "admin"))
                .isInstanceOfSatisfying(ConfirmacaoRecusadaException.class, e -> assertThat(e.motivos()).singleElement()
                        .asString().contains("1.1 PESSOAL impresso 69.193,00; soma das linhas 69.193,86")
                        .contains("ciente da divergência"));

        var semJustificativa = ciente(p, "  ");
        assertThatThrownBy(() -> cenario.confirmacao.confirmar(cenario.condominioId, po.getId(), semJustificativa, "admin"))
                .isInstanceOfSatisfying(ConfirmacaoRecusadaException.class, e -> assertThat(e.motivos())
                        .containsExactly("A confirmação ciente da divergência exige justificativa."));
        assertThat(po.getEstado()).isEqualTo(EstadoPrevisao.LIDA_COM_DIVERGENCIA);

        var detalhe = cenario.confirmacao.confirmar(cenario.condominioId, po.getId(),
                ciente(p, "Subtotal impresso errado no documento aprovado; linhas conferidas no PDF"), "admin");

        assertThat(po.getEstado()).isEqualTo(EstadoPrevisao.CONFIRMADA);
        assertThat(po.isCienteDivergencia()).isTrue();
        // Os cálculos usam a soma das linhas: o subtotal impresso errado não entra no previsto do mês
        assertThat(po.getPrevistoMes()).isEqualByComparingTo("451620.13");
        assertThat(detalhe.avisos()).filteredOn(a -> a.codigo() == CodigoAviso.CONFIRMADA_COM_DIVERGENCIA)
                .singleElement().satisfies(a -> assertThat(a.texto())
                        .startsWith("PO confirmada com divergência: 1.1 PESSOAL impresso 69.193,00; soma das linhas 69.193,86"));
        assertThat(detalhe.achados()).isEmpty();
        assertThat(cenario.eventos).singleElement().satisfies(e -> {
            assertThat(e.getUsuario()).isEqualTo("admin");
            assertThat(e.getJustificativa()).isEqualTo("Subtotal impresso errado no documento aprovado; linhas conferidas no PDF");
            assertThat(e.getDetalhe()).contains("Conferências que falharam (confirmada ciente da divergência): "
                    + "1.1 PESSOAL impresso 69.193,00; soma das linhas 69.193,86");
        });
    }

    @Test
    void cienteNaoDispensaOCodigoEfetivo() {
        PrevisaoOrcamentaria po = cenario.lerPo(PoDoPiloto.padrao().comSubtotalPessoal("69193.00"));
        var p = cenario.pedidoDoPiloto(po);
        var cienteSemCodigo = new PedidoConfirmacao(p.exercicioInicio(), p.exercicioFim(), p.ataArquivoId(), false,
                p.dataAprovacao(), List.of(), p.fundos(), false, true, "Erro de soma no documento");

        assertThatThrownBy(() -> cenario.confirmacao.confirmar(cenario.condominioId, po.getId(), cienteSemCodigo, "admin"))
                .isInstanceOfSatisfying(ConfirmacaoRecusadaException.class, e -> assertThat(e.motivos()).singleElement()
                        .asString().contains("código repetido não é divergência de soma"));
        assertThat(po.getEstado()).isEqualTo(EstadoPrevisao.LIDA_COM_DIVERGENCIA);
    }

    @Test
    void semAtaFicaMarcadoComoPendencia() {
        PrevisaoOrcamentaria po = cenario.lerPo(PoDoPiloto.padrao());
        var p = cenario.pedidoDoPiloto(po);
        var semAta = new PedidoConfirmacao("2026-05", "2027-04", null, true, null, p.codigosEfetivos(), p.fundos(),
                false, false, null);

        var detalhe = cenario.confirmacao.confirmar(cenario.condominioId, po.getId(), semAta, "admin");

        assertThat(po.isSemAta()).isTrue();
        assertThat(detalhe.avisos()).extracting(AvisoDto::codigo)
                .contains(CodigoAviso.SEM_ATA, CodigoAviso.FORA_PRIMEIRO_TRIMESTRE);
    }

    @Test
    void ataPrecisaSerDaCategoriaAtaEComData() {
        PrevisaoOrcamentaria po = cenario.lerPo(PoDoPiloto.padrao());
        var p = cenario.pedidoDoPiloto(po);
        var contrato = cenario.arquivo(Categoria.CONTRATO, "contrato.pdf");
        var pedido = new PedidoConfirmacao("2026-05", "2027-04", contrato.getId(), false, null, p.codigosEfetivos(),
                p.fundos(), false, false, null);

        assertThatThrownBy(() -> cenario.confirmacao.confirmar(cenario.condominioId, po.getId(), pedido, "admin"))
                .isInstanceOfSatisfying(ConfirmacaoRecusadaException.class, e -> assertThat(e.motivos()).containsExactly(
                        "O arquivo \"contrato.pdf\" não está na categoria \"Atas de assembleia\".",
                        "Informe a data da assembleia que aprovou a PO."));
    }

    @Test
    void exercicioEFundosObrigatorios() {
        PrevisaoOrcamentaria po = cenario.lerPo(PoDoPiloto.padrao());
        var p = cenario.pedidoDoPiloto(po);
        var pedido = new PedidoConfirmacao("2026-13", null, p.ataArquivoId(), false, p.dataAprovacao(),
                p.codigosEfetivos(), List.of(new LigacaoFundo(cenario.linha(po, "1.9.1", 0).getId(),
                        cenario.ordinario.getId())), false, false, null);

        assertThatThrownBy(() -> cenario.confirmacao.confirmar(cenario.condominioId, po.getId(), pedido, "admin"))
                .isInstanceOfSatisfying(ConfirmacaoRecusadaException.class, e -> assertThat(e.motivos()).containsExactly(
                        "O início do exercício deve estar no formato AAAA-MM: 2026-13",
                        "Informe o fim do exercício (AAAA-MM).",
                        "O fundo \"CONDOMÍNIO\" é o fundo ordinário e não pode ser ligado à linha 1.9.1.",
                        "Ligue a linha 1.9.2 Fundo de Obras a um fundo do fluxo."));
    }

    @Test
    void umaPoPorMesSemReaprovacaoRecusa() {
        PrevisaoOrcamentaria primeira = cenario.lerPo(PoDoPiloto.padrao());
        cenario.confirmacao.confirmar(cenario.condominioId, primeira.getId(), cenario.pedidoDoPiloto(primeira), "admin");
        PrevisaoOrcamentaria segunda = cenario.lerPo(PoDoPiloto.padrao());

        assertThatThrownBy(() -> cenario.confirmacao.confirmar(cenario.condominioId, segunda.getId(),
                cenario.pedidoDoPiloto(segunda), "admin"))
                .isInstanceOfSatisfying(ConfirmacaoRecusadaException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).contains("versão 1 (2026-05 a 2027-04)", "Só uma PO vale para cada mês");
                });
        assertThat(segunda.getEstado()).isEqualTo(EstadoPrevisao.LIDA);
    }

    @Test
    void reaprovacaoSubstituiAPartirDoInicioDaNovaVersao() {
        PrevisaoOrcamentaria primeira = cenario.lerPo(PoDoPiloto.padrao());
        cenario.confirmacao.confirmar(cenario.condominioId, primeira.getId(), cenario.pedidoDoPiloto(primeira), "admin");
        PrevisaoOrcamentaria segunda = cenario.lerPo(PoDoPiloto.padrao());
        var p = cenario.pedidoDoPiloto(segunda);
        var reaprovacao = new PedidoConfirmacao("2026-09", "2027-04", p.ataArquivoId(), false, LocalDate.of(2026, 8, 30),
                p.codigosEfetivos(), p.fundos(), true, false, null);

        cenario.confirmacao.confirmar(cenario.condominioId, segunda.getId(), reaprovacao, "admin");

        assertThat(primeira.getEstado()).isEqualTo(EstadoPrevisao.SUBSTITUIDA);
        assertThat(primeira.getSubstituidaDesde()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(segunda.getVersao()).isEqualTo(2);
        assertThat(cenario.consultaVigente(YearMonth.of(2026, 8))).contains(primeira);
        assertThat(cenario.consultaVigente(YearMonth.of(2026, 9))).contains(segunda);
        assertThat(cenario.eventos).extracting(EventoPrevisao::getTipo).containsExactly(EventoPrevisao.CONFIRMADA,
                EventoPrevisao.SUBSTITUIDA, EventoPrevisao.CONFIRMADA);
    }

    @Test
    void reaprovacaoQueTerminaAntesDaAnteriorEhRecusada() {
        PrevisaoOrcamentaria primeira = cenario.lerPo(PoDoPiloto.padrao());
        cenario.confirmacao.confirmar(cenario.condominioId, primeira.getId(), cenario.pedidoDoPiloto(primeira), "admin");
        PrevisaoOrcamentaria segunda = cenario.lerPo(PoDoPiloto.padrao());
        var p = cenario.pedidoDoPiloto(segunda);
        var curta = new PedidoConfirmacao("2026-09", "2026-12", p.ataArquivoId(), false, LocalDate.of(2026, 8, 30),
                p.codigosEfetivos(), p.fundos(), true, false, null);

        assertThatThrownBy(() -> cenario.confirmacao.confirmar(cenario.condominioId, segunda.getId(), curta, "admin"))
                .isInstanceOfSatisfying(ConfirmacaoRecusadaException.class,
                        e -> assertThat(e.getMessage()).contains("precisa cobrir até 2027-04"));
        assertThat(primeira.getEstado()).isEqualTo(EstadoPrevisao.CONFIRMADA);
    }

    @Test
    void poJaConfirmadaNaoEhConfirmadaDeNovo() {
        PrevisaoOrcamentaria po = cenario.lerPo(PoDoPiloto.padrao());
        cenario.confirmacao.confirmar(cenario.condominioId, po.getId(), cenario.pedidoDoPiloto(po), "admin");

        assertThatThrownBy(() -> cenario.confirmacao.confirmar(cenario.condominioId, po.getId(),
                cenario.pedidoDoPiloto(po), "admin"))
                .isInstanceOfSatisfying(ConfirmacaoRecusadaException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
    }

    private static PedidoConfirmacao ciente(PedidoConfirmacao p, String justificativa) {
        return new PedidoConfirmacao(p.exercicioInicio(), p.exercicioFim(), p.ataArquivoId(), p.semAta(),
                p.dataAprovacao(), p.codigosEfetivos(), p.fundos(), false, true, justificativa);
    }
}
