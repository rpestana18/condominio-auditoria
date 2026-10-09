package br.com.condominioauditoria.api.repository.audit;

import br.com.condominioauditoria.api.model.audit.RuleParameter;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RuleParameterRepository extends JpaRepository<RuleParameter, UUID> {

    @Query("""
            select p from RuleParameter p
            where p.condominiumId = :condominiumId and p.code = :code
              and p.validFrom <= :date and (p.validTo is null or p.validTo >= :date)
            order by p.validFrom desc""")
    List<RuleParameter> findAllValidOn(UUID condominiumId, String code, LocalDate date);

    /** Value valid on the date; the one with the latest start, if they overlap. */
    default Optional<RuleParameter> findValidOn(UUID condominiumId, String code, LocalDate date) {
        return findAllValidOn(condominiumId, code, date).stream().findFirst();
    }
}
