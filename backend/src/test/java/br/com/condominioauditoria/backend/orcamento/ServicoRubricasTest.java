package br.com.condominioauditoria.backend.orcamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.condominioauditoria.backend.orcamento.RubricaDtos.AcaoLoteRubrica;
import br.com.condominioauditoria.backend.orcamento.RubricaDtos.FiltroRubrica;
import br.com.condominioauditoria.backend.orcamento.RubricaDtos.PedidoLoteRubrica;
import br.com.condominioauditoria.backend.orcamento.RubricaDtos.PedidoNovaRubrica;
import br.com.condominioauditoria.backend.orcamento.RubricaDtos.PedidoRenomear;
import br.com.condominioauditoria.backend.orcamento.RubricaDtos.PedidoRubricaLinha;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** RF-11.7 e ADR 0005, Decisão 1: catálogo de rubricas, sugestão por conta da PO e grupo, confirmação e trilha. */
class ServicoRubricasTest {

    private final CenarioPo cenario = new CenarioPo();

    @Test
    void primeiraPoConfirmadaViraOCatalogoJaConfirmado() {
        PrevisaoOrcamentaria a = cenario.poConfirmada();

        long linhasDaPo = cenario.linhas.stream()
                .filter(l -> l.getPrevisaoId().equals(a.getId()) && l.getTipo() == TipoLinhaPo.LINHA).count();
        assertThat(cenario.rubricas).hasSize((int) linhasDaPo);
        assertThat(cenario.linhasRubrica).hasSize((int) linhasDaPo).allSatisfy(lr -> {
            assertThat(lr.getEstado()).isEqualTo(EstadoRubrica.CONFIRMADO);
            assertThat(lr.getOrigem()).isEqualTo(OrigemRubrica.PRIMEIRA_PO);
        });
        assertThat(cenario.eventosRubrica).hasSize((int) linhasDaPo);
        assertThat(rubricaDe(a, "1.3.20", 0).getNome()).isEqualTo("1682 - Sindicatura Profissional");
        assertThat(rubricaDe(a, "1.3.20", 0).getGrupoCodigo()).isEqualTo("1.3");
        // Fundo (1.9) também vira rubrica; linha sem conta usa o texto da coluna de conta, senão a descrição
        assertThat(rubricaDe(a, "1.9.1", 0).getNome()).isEqualTo("Fundo de Reserva");
        assertThat(rubricaDe(a, "1.4.1", 0).getNome()).isEqualTo("Força e Luz");
        var lista = cenario.servicoRubricas.listar(cenario.condominioId, a.getId(), null);
        assertThat(lista.resumo().confirmadas()).isEqualTo((int) linhasDaPo);
        assertThat(lista.resumo().semRubrica()).isZero();
    }

    @Test
    void sindicaturaDoOutroExercicioEhSugeridaComOMotivoESoValeDepoisDeConfirmada() {
        PrevisaoOrcamentaria a = cenario.poConfirmada();
        int rubricasDeA = cenario.rubricas.size();
        PrevisaoOrcamentaria b = proximoExercicio(PoDoPiloto.padrao());

        LinhaRubrica lr = ligacao(b, "1.3.20", 0);
        assertThat(lr.getEstado()).isEqualTo(EstadoRubrica.SUGERIDO);
        assertThat(lr.getOrigem()).isEqualTo(OrigemRubrica.CONTA_PO);
        assertThat(lr.getMotivo())
                .isEqualTo("mesma conta da PO e mesmo grupo: 1682 - Sindicatura Profissional, 1.3");
        assertThat(lr.getRubricaId()).isEqualTo(ligacao(a, "1.3.20", 0).getRubricaId());
        // Sugestão não cria rubrica nem confirma nada
        assertThat(cenario.rubricas).hasSize(rubricasDeA);
        var pendentes = cenario.servicoRubricas.listar(cenario.condominioId, b.getId(), FiltroRubrica.CONFIRMADO);
        assertThat(pendentes.linhas()).isEmpty();
    }

    @Test
    void bombasECaixaDaguaSaoSugeridasSeparadas() {
        PrevisaoOrcamentaria a = cenario.poConfirmada();
        PrevisaoOrcamentaria b = proximoExercicio(PoDoPiloto.padrao());

        LinhaRubrica bombas = ligacao(b, "1.3.2", 0);
        LinhaRubrica caixa = ligacao(b, "1.3.25", 0);
        assertThat(bombas.getRubricaId()).isEqualTo(ligacao(a, "1.3.2", 0).getRubricaId());
        assertThat(caixa.getRubricaId()).isEqualTo(ligacao(a, "1.3.25", 0).getRubricaId());
        assertThat(bombas.getRubricaId()).isNotEqualTo(caixa.getRubricaId());
        assertThat(caixa.getMotivo()).isEqualTo("mesma conta da PO e mesmo grupo: 1624 - Caixa D'água, 1.3");
    }

    @Test
    void conta1606EmDoisGruposVaiParaRubricasDiferentes() {
        PrevisaoOrcamentaria a = cenario.lerPo(PoDoPiloto.padrao().comAparelhosDeGinastica());
        cenario.confirmacao.confirmar(cenario.condominioId, a.getId(), cenario.pedidoDoPiloto(a), "admin");
        PrevisaoOrcamentaria b = proximoExercicio(PoDoPiloto.padrao().comAparelhosDeGinastica());

        LinhaRubrica emContratos = ligacao(b, "1.3.5", 0);
        LinhaRubrica emMateriais = ligacao(b, "1.7.2", 0);
        assertThat(emContratos.getRubricaId()).isEqualTo(ligacao(a, "1.3.5", 0).getRubricaId());
        assertThat(emMateriais.getRubricaId()).isEqualTo(ligacao(a, "1.7.2", 0).getRubricaId());
        assertThat(emContratos.getRubricaId()).isNotEqualTo(emMateriais.getRubricaId());
        assertThat(emMateriais.getMotivo()).isEqualTo("mesma conta da PO e mesmo grupo: 1606 - Aparelhos de Ginástica, 1.7");
    }

    @Test
    void loteGravaUmEventoPorLinhaEIgnoraAsQueNaoMudam() {
        cenario.poConfirmada();
        PrevisaoOrcamentaria b = proximoExercicio(PoDoPiloto.padrao());
        List<UUID> sugeridas = cenario.linhasRubrica.stream()
                .filter(lr -> lr.getPrevisaoId().equals(b.getId()) && lr.getEstado() == EstadoRubrica.SUGERIDO)
                .map(LinhaRubrica::getLinhaPoId).toList();
        assertThat(sugeridas).isNotEmpty();
        int eventosAntes = cenario.eventosRubrica.size();

        var r = cenario.servicoRubricas.lote(cenario.condominioId, b.getId(),
                new PedidoLoteRubrica(AcaoLoteRubrica.CONFIRMAR, sugeridas), "admin");

        assertThat(r.alteradas()).isEqualTo(sugeridas.size());
        assertThat(r.ignoradas()).isEmpty();
        assertThat(cenario.eventosRubrica.subList(eventosAntes, cenario.eventosRubrica.size()))
                .hasSize(sugeridas.size())
                .allSatisfy(e -> {
                    assertThat(e.getAcao()).isEqualTo(EventoRubrica.Acao.CONFIRMADO);
                    assertThat(e.getEstadoAnterior()).isEqualTo(EstadoRubrica.SUGERIDO);
                    assertThat(e.getUsuario()).isEqualTo("admin");
                });
        var deNovo = cenario.servicoRubricas.lote(cenario.condominioId, b.getId(),
                new PedidoLoteRubrica(AcaoLoteRubrica.CONFIRMAR, sugeridas.subList(0, 1)), "admin");
        assertThat(deNovo.alteradas()).isZero();
        assertThat(deNovo.ignoradas().getFirst().motivo()).isEqualTo("já está confirmado");
    }

    @Test
    void adminTrocaARubricaOuCriaUmaNovaAPartirDaLinha() {
        PrevisaoOrcamentaria a = cenario.poConfirmada();
        PrevisaoOrcamentaria b = proximoExercicio(PoDoPiloto.padrao());
        UUID linhaCaixa = cenario.linha(b, "1.3.2", 1).getId();
        UUID bombas = ligacao(a, "1.3.2", 0).getRubricaId();

        var trocada = cenario.servicoRubricas.definir(cenario.condominioId, b.getId(), linhaCaixa,
                new PedidoRubricaLinha(bombas, null, null), "admin");
        assertThat(trocada.estado()).isEqualTo(EstadoRubrica.CONFIRMADO);
        assertThat(trocada.origem()).isEqualTo(OrigemRubrica.MANUAL);
        assertThat(trocada.rubrica().id()).isEqualTo(bombas);
        assertThat(cenario.eventosRubrica.getLast().getAcao()).isEqualTo(EventoRubrica.Acao.ALTERADO);
        assertThat(cenario.eventosRubrica.getLast().getRubricaAnterior()).isEqualTo("1624 - Caixa D'água");

        int rubricasAntes = cenario.rubricas.size();
        var nova = cenario.servicoRubricas.definir(cenario.condominioId, b.getId(), linhaCaixa,
                new PedidoRubricaLinha(null, "Caixas d'água (limpeza)", true), "admin");
        assertThat(cenario.rubricas).hasSize(rubricasAntes + 1);
        assertThat(nova.rubrica().nome()).isEqualTo("Caixas d'água (limpeza)");
        assertThat(nova.rubrica().grupo()).isEqualTo("1.3");
        var ultimos = cenario.eventosRubrica.subList(cenario.eventosRubrica.size() - 2, cenario.eventosRubrica.size());
        assertThat(ultimos).extracting(EventoRubrica::getAcao)
                .containsExactly(EventoRubrica.Acao.CRIADA, EventoRubrica.Acao.ALTERADO);

        assertThatThrownBy(() -> cenario.servicoRubricas.definir(cenario.condominioId, b.getId(), linhaCaixa,
                new PedidoRubricaLinha(bombas, "outra", null), "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
    }

    @Test
    void reaprovacaoHerdaARubricaDaLinhaIgualDaVersaoAnterior() {
        PrevisaoOrcamentaria a = cenario.poConfirmada();
        PrevisaoOrcamentaria segunda = cenario.lerPo(PoDoPiloto.padrao());
        var p = cenario.pedidoDoPiloto(segunda);
        cenario.confirmacao.confirmar(cenario.condominioId, segunda.getId(), new PedidoConfirmacao("2026-09", "2027-04",
                p.ataArquivoId(), false, LocalDate.of(2026, 8, 30), p.codigosEfetivos(), p.fundos(), true, false, null),
                "admin");

        LinhaRubrica lr = ligacao(segunda, "1.3.20", 0);
        assertThat(lr.getOrigem()).isEqualTo(OrigemRubrica.VERSAO_ANTERIOR);
        assertThat(lr.getEstado()).isEqualTo(EstadoRubrica.SUGERIDO);
        assertThat(lr.getMotivo()).isEqualTo("linha igual na versão anterior: 1.3.20 1682 - Sindicatura Profissional");
        assertThat(lr.getRubricaId()).isEqualTo(ligacao(a, "1.3.20", 0).getRubricaId());
    }

    @Test
    void sugestoesNaoMexemEmLinhaQueJaTemRubrica() {
        cenario.poConfirmada();
        PrevisaoOrcamentaria b = proximoExercicio(PoDoPiloto.padrao());
        int ligadas = cenario.linhasRubrica.size();
        int eventos = cenario.eventosRubrica.size();

        var r = cenario.servicoRubricas.sugerir(cenario.condominioId, b.getId(), "admin");

        assertThat(r.primeiraPo()).isFalse();
        assertThat(r.sugeridas()).isZero();
        assertThat(cenario.linhasRubrica).hasSize(ligadas);
        assertThat(cenario.eventosRubrica).hasSize(eventos);
    }

    @Test
    void poConfirmadaAntesDasRubricasGeraOCatalogoPelasSugestoes() {
        PrevisaoOrcamentaria a = cenario.poConfirmada();
        cenario.rubricas.clear();
        cenario.linhasRubrica.clear();

        var r = cenario.servicoRubricas.sugerir(cenario.condominioId, a.getId(), "admin");

        assertThat(r.primeiraPo()).isTrue();
        assertThat(r.rubricasCriadas()).isEqualTo(cenario.rubricas.size()).isPositive();
    }

    @Test
    void poNaoConfirmadaNaoRecebeRubrica() {
        PrevisaoOrcamentaria lida = cenario.lerPo(PoDoPiloto.padrao());

        assertThatThrownBy(() -> cenario.servicoRubricas.sugerir(cenario.condominioId, lida.getId(), "admin"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(cenario.rubricas).isEmpty();
    }

    @Test
    void criarERenomearRubricaFicamNaTrilha() {
        var criada = cenario.servicoRubricas.criar(cenario.condominioId, new PedidoNovaRubrica(" Academia ", "1.3"),
                "admin");
        var renomeada = cenario.servicoRubricas.renomear(cenario.condominioId, criada.id(),
                new PedidoRenomear("Academia e ginástica"), "admin");

        assertThat(criada.nome()).isEqualTo("Academia");
        assertThat(renomeada.nome()).isEqualTo("Academia e ginástica");
        assertThat(cenario.eventosRubrica).extracting(EventoRubrica::getAcao)
                .containsExactly(EventoRubrica.Acao.CRIADA, EventoRubrica.Acao.RENOMEADA);
        assertThat(cenario.eventosRubrica.getLast().getRubricaAnterior()).isEqualTo("Academia");
        assertThatThrownBy(() -> cenario.servicoRubricas.criar(cenario.condominioId, new PedidoNovaRubrica(" ", null),
                "admin")).isInstanceOf(ResponseStatusException.class);
    }

    /** Lê e confirma a PO como se fosse do exercício seguinte (05/2027 a 04/2028), com 1.3.25 e os fundos. */
    private PrevisaoOrcamentaria proximoExercicio(PoDoPiloto po) {
        PrevisaoOrcamentaria b = cenario.lerPo(po);
        var p = cenario.pedidoDoPiloto(b);
        cenario.confirmacao.confirmar(cenario.condominioId, b.getId(), new PedidoConfirmacao("2027-05", "2028-04",
                p.ataArquivoId(), false, LocalDate.of(2027, 5, 20), p.codigosEfetivos(), p.fundos(), false, false, null),
                "admin");
        return b;
    }

    /** Rubrica da linha pelo código efetivo (o índice separa códigos repetidos). */
    private LinhaRubrica ligacao(PrevisaoOrcamentaria po, String codigoEfetivo, int indice) {
        UUID linha = cenario.linhas.stream()
                .filter(l -> l.getPrevisaoId().equals(po.getId()) && l.getCodigoEfetivo().equals(codigoEfetivo))
                .sorted(java.util.Comparator.comparingInt(LinhaPo::getOrdem)).toList().get(indice).getId();
        return cenario.linhasRubrica.stream().filter(lr -> lr.getLinhaPoId().equals(linha)).findFirst().orElseThrow();
    }

    private Rubrica rubricaDe(PrevisaoOrcamentaria po, String codigoEfetivo, int indice) {
        UUID id = ligacao(po, codigoEfetivo, indice).getRubricaId();
        return cenario.rubricas.stream().filter(r -> r.getId().equals(id)).findFirst().orElseThrow();
    }
}
