package br.com.condominioauditoria.api.repository.budget;

import br.com.condominioauditoria.api.model.budget.AccountMappingSheetItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AccountMappingSheetItemRepository extends JpaRepository<AccountMappingSheetItem, UUID> {

    List<AccountMappingSheetItem> findByCondominiumIdOrderByAccountCode(UUID condominiumId);

    /** The new sheet replaces the previous one: only the last loaded sheet is kept (Q51). */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from AccountMappingSheetItem i where i.condominiumId = :condominiumId")
    int deleteByCondominiumId(UUID condominiumId);
}
