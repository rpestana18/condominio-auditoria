package br.com.condominioauditoria.api.service.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.config.properties.FeatureCatalogTest;
import br.com.condominioauditoria.api.event.FeatureChanged;
import br.com.condominioauditoria.api.exception.FeatureNotEnabledException;
import br.com.condominioauditoria.api.exception.InvalidRequestException;
import br.com.condominioauditoria.api.exception.UnknownFeatureException;
import br.com.condominioauditoria.api.model.feature.CondominiumFeature;
import br.com.condominioauditoria.api.model.feature.FeatureEvent;
import br.com.condominioauditoria.api.repository.feature.CondominiumFeatureRepository;
import br.com.condominioauditoria.api.repository.feature.FeatureEventRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

/** Enabling and disabling with trail and reason (RF-10.2, RF-10.6) and the central check (RF-10.3). */
class FeatureServiceTest {

    private static final UUID CONDOMINIUM = UUID.randomUUID();
    private static final CondominiumFeature.Key KEY = new CondominiumFeature.Key(CONDOMINIUM, FeatureService.ASSISTANT);

    private final CondominiumFeatureRepository states = mock(CondominiumFeatureRepository.class);
    private final FeatureEventRepository events = mock(FeatureEventRepository.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private FeatureService features;

    @BeforeEach
    void setUp() throws Exception {
        features = new FeatureService(FeatureCatalogTest.load(), states, events, publisher);
        when(states.save(any(CondominiumFeature.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void newCondominiumStartsWithTheAssistantDisabled() {
        when(states.findById(KEY)).thenReturn(Optional.empty());

        assertThat(features.isEnabled(CONDOMINIUM, FeatureService.ASSISTANT)).isFalse();
        assertThatThrownBy(() -> features.require(CONDOMINIUM, FeatureService.ASSISTANT))
                .isInstanceOf(FeatureNotEnabledException.class)
                .hasMessage("Módulo Assistente não contratado para este condomínio.");
    }

    @Test
    void enabledInTheDatabasePassesTheCheck() {
        when(states.findById(KEY)).thenReturn(Optional.of(
                new CondominiumFeature(CONDOMINIUM, FeatureService.ASSISTANT, true, Instant.now(), "admin")));

        assertThat(features.isEnabled(CONDOMINIUM, FeatureService.ASSISTANT)).isTrue();
        features.require(CONDOMINIUM, FeatureService.ASSISTANT);
    }

    @Test
    void featureOutsideTheCatalogIsNeverEnabled() {
        assertThat(features.isEnabled(CONDOMINIUM, "INEXISTENTE")).isFalse();
        assertThatThrownBy(() -> features.require(CONDOMINIUM, "INEXISTENTE"))
                .isInstanceOf(UnknownFeatureException.class);
    }

    @Test
    void enablingSavesStateAndEventWithReasonAndNotifiesListeners() {
        when(states.lockByKey(KEY)).thenReturn(Optional.empty());

        var state = features.change(CONDOMINIUM, FeatureService.ASSISTANT, true, "  Contrato assinado em 01/11  ",
                "admin");

        assertThat(state.enabled()).isTrue();
        assertThat(state.since()).isNotNull();
        var event = ArgumentCaptor.forClass(FeatureEvent.class);
        verify(events).save(event.capture());
        assertThat(event.getValue().isEnabledBefore()).isFalse();
        assertThat(event.getValue().isEnabledAfter()).isTrue();
        assertThat(event.getValue().getUsername()).isEqualTo("admin");
        assertThat(event.getValue().getReason()).isEqualTo("Contrato assinado em 01/11");
        assertThat(event.getValue().getCondominiumId()).isEqualTo(CONDOMINIUM);
        verify(publisher).publishEvent(new FeatureChanged(CONDOMINIUM, FeatureService.ASSISTANT, true, "admin"));
    }

    @Test
    void disablingSavesAnEnabledToDisabledEvent() {
        var row = new CondominiumFeature(CONDOMINIUM, FeatureService.ASSISTANT, true,
                Instant.parse("2026-01-01T12:00:00Z"), "admin");
        when(states.lockByKey(KEY)).thenReturn(Optional.of(row));

        var state = features.change(CONDOMINIUM, FeatureService.ASSISTANT, false, "Fim do contrato", "admin2");

        assertThat(state.enabled()).isFalse();
        assertThat(row.getChangedBy()).isEqualTo("admin2");
        assertThat(row.getSince()).isAfter(Instant.parse("2026-01-01T12:00:00Z"));
        var event = ArgumentCaptor.forClass(FeatureEvent.class);
        verify(events).save(event.capture());
        assertThat(event.getValue().isEnabledBefore()).isTrue();
        assertThat(event.getValue().isEnabledAfter()).isFalse();
        verify(publisher).publishEvent(new FeatureChanged(CONDOMINIUM, FeatureService.ASSISTANT, false, "admin2"));
    }

    @Test
    void reasonIsOptionalAndBlankBecomesNull() {
        when(states.lockByKey(KEY)).thenReturn(Optional.empty());

        features.change(CONDOMINIUM, FeatureService.ASSISTANT, true, "   ", "admin");

        var event = ArgumentCaptor.forClass(FeatureEvent.class);
        verify(events).save(event.capture());
        assertThat(event.getValue().getReason()).isNull();
        assertThat(event.getValue().isEnabledAfter()).isTrue();
    }

    @Test
    void reasonOver500CharactersIsRejected() {
        assertThatThrownBy(() -> features.change(CONDOMINIUM, FeatureService.ASSISTANT, true, "x".repeat(501), "admin"))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("500");

        verifyNoInteractions(states, events, publisher);
    }

    @Test
    void changeIsSerializedBeforeReadingTheState() {
        when(states.lockByKey(KEY)).thenReturn(Optional.empty());

        features.change(CONDOMINIUM, FeatureService.ASSISTANT, true, null, "admin");

        var position = inOrder(states);
        position.verify(states).serializeChange(CONDOMINIUM.toString(), FeatureService.ASSISTANT);
        position.verify(states).lockByKey(KEY);
    }

    @Test
    void requestingTheCurrentStateSavesNothing() {
        when(states.lockByKey(KEY)).thenReturn(Optional.empty()); // disabled by default

        var state = features.change(CONDOMINIUM, FeatureService.ASSISTANT, false, "conferência", "admin");

        assertThat(state.enabled()).isFalse();
        assertThat(state.since()).isNull();
        verify(states, never()).save(any());
        verify(events, never()).save(any());
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void unknownFeatureIsNotChanged() {
        assertThatThrownBy(() -> features.change(CONDOMINIUM, "RELATORIOS", true, "teste", "admin"))
                .isInstanceOf(UnknownFeatureException.class);
        verifyNoInteractions(states, events);
    }
}
