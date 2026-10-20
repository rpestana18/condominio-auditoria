package br.com.condominioauditoria.api.repository.file;

import br.com.condominioauditoria.api.model.file.PartialMonthEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartialMonthEventRepository extends JpaRepository<PartialMonthEvent, UUID> {
    List<PartialMonthEvent> findByFileIdOrderByOccurredAt(UUID fileId);

    boolean existsByFileIdAndSupersededById(UUID fileId, UUID supersededById);
}
