package br.com.condominioauditoria.api.repository.accounting;

import br.com.condominioauditoria.api.model.accounting.TotalsCheck;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TotalsCheckRepository extends JpaRepository<TotalsCheck, UUID> {
    List<TotalsCheck> findByFileIdOrderBySequence(UUID fileId);

    @Modifying
    @Query("delete from TotalsCheck c where c.fileId = :fileId")
    void deleteByFileId(UUID fileId);
}
