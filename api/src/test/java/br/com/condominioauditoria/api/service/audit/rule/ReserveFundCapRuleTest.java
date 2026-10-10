package br.com.condominioauditoria.api.service.audit.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.condominioauditoria.api.model.audit.Finding;
import br.com.condominioauditoria.api.model.enums.FindingStatus;
import br.com.condominioauditoria.api.model.enums.Severity;
import br.com.condominioauditoria.api.repository.audit.FindingEventRepository;
import br.com.condominioauditoria.api.repository.audit.FindingEvidenceRepository;
import br.com.condominioauditoria.api.repository.audit.FindingRepository;
import br.com.condominioauditoria.api.service.audit.FindingSyncService;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Conv. 20.1 (RF-03.1.3): a reserve fund above the cap raises an "atenção" finding; exact comparison. */
class ReserveFundCapRuleTest {

    private static final BigDecimal FIVE = new BigDecimal("5.0000");

    @Test
    void pilotWithThreePercentStaysWithinTheCap() {
        var a = ReserveFundCapRule.assess(new BigDecimal("13548.60"), new BigDecimal("451620.12"), FIVE)
                .orElseThrow();

        assertThat(a.aboveCap()).isFalse();
        assertThat(a.displayPercentage()).isEqualTo("3,0%");
        assertThat(a.displayCap()).isEqualTo("5%");
    }

    @Test
    void exactlyFivePercentPassesAndOneCentMoreExceeds() {
        BigDecimal budgeted = new BigDecimal("451620.00");

        assertThat(ReserveFundCapRule.assess(new BigDecimal("22581.00"), budgeted, FIVE).orElseThrow()
                .aboveCap()).isFalse();
        assertThat(ReserveFundCapRule.assess(new BigDecimal("22581.01"), budgeted, FIVE).orElseThrow()
                .aboveCap()).isTrue();
    }

    @Test
    void withoutBudgetThereIsNoBaseForThePercentage() {
        assertThat(ReserveFundCapRule.assess(new BigDecimal("100.00"), BigDecimal.ZERO, FIVE)).isEmpty();
    }

    @Test
    void registeringAgainDoesNotDuplicateTheFinding() {
        FindingRepository findings = mock(FindingRepository.class);
        FindingEvidenceRepository evidence = mock(FindingEvidenceRepository.class);
        List<Finding> saved = new ArrayList<>();
        when(findings.save(any())).thenAnswer(i -> {
            saved.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(findings.findByCondominiumIdAndRuleAndReferenceMonthAndTarget(any(), any(), any(), any()))
                .thenAnswer(i -> saved.stream().findFirst());
        FindingSyncService registry = new FindingSyncService(findings, evidence, mock(FindingEventRepository.class));
        UUID condominium = UUID.randomUUID();
        var proof = List.of(new FindingSyncService.Evidence(UUID.randomUUID(), "a".repeat(64), 1, "PO, linha 1.9.1",
                null));

        Finding first = registry.register(condominium, ReserveFundCapRule.CODE, "1", Severity.WARNING,
                YearMonth.of(2026, 5), "budget:x:line:y", "texto", proof);
        Finding second = registry.register(condominium, ReserveFundCapRule.CODE, "1", Severity.WARNING,
                YearMonth.of(2026, 5), "budget:x:line:y", "texto", proof);

        assertThat(second).isSameAs(first);
        assertThat(first.getStatus()).isEqualTo(FindingStatus.OPEN);
        verify(findings, times(1)).save(any());
        verify(evidence, times(1)).save(any());
        assertThat(Optional.of(first.getReferenceMonth())).contains(YearMonth.of(2026, 5));
    }
}
