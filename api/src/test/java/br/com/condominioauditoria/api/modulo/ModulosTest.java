package br.com.condominioauditoria.api.modulo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** Ligar e desligar com trilha e motivo obrigatório (RF-10.2, RF-10.6) e a verificação central (RF-10.3). */
class ModulosTest {

    private static final UUID CONDOMINIO = UUID.randomUUID();
    private static final ModuloCondominio.Chave CHAVE = new ModuloCondominio.Chave(CONDOMINIO, Modulos.ASSISTENTE);

    private final ModuloCondominioRepository estados = mock(ModuloCondominioRepository.class);
    private final EventoModuloRepository eventos = mock(EventoModuloRepository.class);
    private final ApplicationEventPublisher publicador = mock(ApplicationEventPublisher.class);
    private Modulos modulos;

    @BeforeEach
    void preparar() throws Exception {
        modulos = new Modulos(CatalogoModulosTest.carregar(), estados, eventos, publicador);
        when(estados.save(any(ModuloCondominio.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void condominioNovoComecaComOAssistenteDesligado() {
        when(estados.findById(CHAVE)).thenReturn(Optional.empty());

        assertThat(modulos.ligado(CONDOMINIO, Modulos.ASSISTENTE)).isFalse();
        assertThatThrownBy(() -> modulos.exigir(CONDOMINIO, Modulos.ASSISTENTE))
                .isInstanceOf(ModuloNaoContratadoException.class)
                .hasMessage("Módulo Assistente não contratado para este condomínio.");
    }

    @Test
    void ligadoNoBancoPassaNaVerificacao() {
        when(estados.findById(CHAVE)).thenReturn(Optional.of(
                new ModuloCondominio(CONDOMINIO, Modulos.ASSISTENTE, true, Instant.now(), "admin")));

        assertThat(modulos.ligado(CONDOMINIO, Modulos.ASSISTENTE)).isTrue();
        modulos.exigir(CONDOMINIO, Modulos.ASSISTENTE);
    }

    @Test
    void moduloForaDoCatalogoNuncaEstaLigado() {
        assertThat(modulos.ligado(CONDOMINIO, "INEXISTENTE")).isFalse();
        assertThatThrownBy(() -> modulos.exigir(CONDOMINIO, "INEXISTENTE"))
                .isInstanceOf(ModuloDesconhecidoException.class);
    }

    @Test
    void ligarGravaEstadoEventoComMotivoEAvisaQuemReage() {
        when(estados.travar(CHAVE)).thenReturn(Optional.empty());

        var estado = modulos.alterar(CONDOMINIO, Modulos.ASSISTENTE, true, "  Contrato assinado em 01/11  ", "admin");

        assertThat(estado.ligado()).isTrue();
        assertThat(estado.desde()).isNotNull();
        var evento = ArgumentCaptor.forClass(EventoModulo.class);
        verify(eventos).save(evento.capture());
        assertThat(evento.getValue().isLigadoAntes()).isFalse();
        assertThat(evento.getValue().isLigadoDepois()).isTrue();
        assertThat(evento.getValue().getUsuario()).isEqualTo("admin");
        assertThat(evento.getValue().getMotivo()).isEqualTo("Contrato assinado em 01/11");
        assertThat(evento.getValue().getCondominioId()).isEqualTo(CONDOMINIO);
        verify(publicador).publishEvent(new ModuloAlterado(CONDOMINIO, Modulos.ASSISTENTE, true, "admin"));
    }

    @Test
    void desligarGravaEventoDeLigadoParaDesligado() {
        var linha = new ModuloCondominio(CONDOMINIO, Modulos.ASSISTENTE, true, Instant.parse("2026-01-01T12:00:00Z"),
                "admin");
        when(estados.travar(CHAVE)).thenReturn(Optional.of(linha));

        var estado = modulos.alterar(CONDOMINIO, Modulos.ASSISTENTE, false, "Fim do contrato", "admin2");

        assertThat(estado.ligado()).isFalse();
        assertThat(linha.getAlteradoPor()).isEqualTo("admin2");
        assertThat(linha.getDesde()).isAfter(Instant.parse("2026-01-01T12:00:00Z"));
        var evento = ArgumentCaptor.forClass(EventoModulo.class);
        verify(eventos).save(evento.capture());
        assertThat(evento.getValue().isLigadoAntes()).isTrue();
        assertThat(evento.getValue().isLigadoDepois()).isFalse();
        verify(publicador).publishEvent(new ModuloAlterado(CONDOMINIO, Modulos.ASSISTENTE, false, "admin2"));
    }

    @Test
    void motivoEhOpcionalEEmBrancoViraNulo() {
        when(estados.travar(CHAVE)).thenReturn(Optional.empty());

        modulos.alterar(CONDOMINIO, Modulos.ASSISTENTE, true, "   ", "admin");

        var evento = ArgumentCaptor.forClass(EventoModulo.class);
        verify(eventos).save(evento.capture());
        assertThat(evento.getValue().getMotivo()).isNull();
        assertThat(evento.getValue().isLigadoDepois()).isTrue();
    }

    @Test
    void motivoAcimaDe500CaracteresEhRecusado() {
        assertThatThrownBy(() -> modulos.alterar(CONDOMINIO, Modulos.ASSISTENTE, true, "x".repeat(501), "admin"))
                .isInstanceOf(PedidoInvalidoException.class).hasMessageContaining("500");

        verifyNoInteractions(estados, eventos, publicador);
    }

    @Test
    void alteracaoEhSerializadaAntesDeLerOEstado() {
        when(estados.travar(CHAVE)).thenReturn(Optional.empty());

        modulos.alterar(CONDOMINIO, Modulos.ASSISTENTE, true, null, "admin");

        var ordem = inOrder(estados);
        ordem.verify(estados).serializarAlteracao(CONDOMINIO.toString(), Modulos.ASSISTENTE);
        ordem.verify(estados).travar(CHAVE);
    }

    @Test
    void pedirOEstadoQueJaValeNaoGravaNada() {
        when(estados.travar(CHAVE)).thenReturn(Optional.empty()); // desligado por padrão

        var estado = modulos.alterar(CONDOMINIO, Modulos.ASSISTENTE, false, "conferência", "admin");

        assertThat(estado.ligado()).isFalse();
        assertThat(estado.desde()).isNull();
        verify(estados, never()).save(any());
        verify(eventos, never()).save(any());
        verify(publicador, never()).publishEvent(any(Object.class));
    }

    @Test
    void moduloDesconhecidoNaoAltera() {
        assertThatThrownBy(() -> modulos.alterar(CONDOMINIO, "RELATORIOS", true, "teste", "admin"))
                .isInstanceOf(ModuloDesconhecidoException.class);
        verifyNoInteractions(estados, eventos);
    }
}
