package br.com.condominioauditoria.api.repository.accounting;

import br.com.condominioauditoria.api.model.accounting.FundBalance;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FundBalanceRepository extends JpaRepository<FundBalance, UUID> {
    List<FundBalance> findByFileId(UUID fileId);

    @Modifying
    @Query("delete from FundBalance b where b.fileId = :fileId")
    void deleteByFileId(UUID fileId);
}
