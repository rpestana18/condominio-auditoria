package br.com.condominioauditoria.api.repository.accounting;

import br.com.condominioauditoria.api.model.accounting.UtilityAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UtilityAccountRepository extends JpaRepository<UtilityAccount, UUID> {
    List<UtilityAccount> findByCondominiumId(UUID condominiumId);

    Optional<UtilityAccount> findByCondominiumIdAndAccountCode(UUID condominiumId, String accountCode);
}
