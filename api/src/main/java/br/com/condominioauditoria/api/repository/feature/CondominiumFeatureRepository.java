package br.com.condominioauditoria.api.repository.feature;

import br.com.condominioauditoria.api.model.feature.CondominiumFeature;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CondominiumFeatureRepository extends JpaRepository<CondominiumFeature, CondominiumFeature.Key> {

    @Query("select f from CondominiumFeature f where f.key.condominiumId = :condominiumId")
    List<CondominiumFeature> findByCondominium(UUID condominiumId);

    /**
     * Serializes the changes of a feature in a condominium until the end of the transaction (PostgreSQL advisory lock).
     * Works even when the row does not exist yet: on the first enable, two simultaneous requests do not both try to
     * insert the same row (the second waits, reads the saved row and changes nothing).
     */
    @Query(nativeQuery = true, value = """
            select 1 from (select pg_advisory_xact_lock(
                hashtextextended('condominium_feature:' || :condominiumId || ':' || :feature, 0))) t
            """)
    Integer serializeChange(String condominiumId, String feature);

    /** Locks the row during the change: two simultaneous requests do not save two identical events. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from CondominiumFeature f where f.key = :key")
    Optional<CondominiumFeature> lockByKey(CondominiumFeature.Key key);
}
