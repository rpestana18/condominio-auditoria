package br.com.condominioauditoria.api.repository.file;

import br.com.condominioauditoria.api.model.file.CategoryChange;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryChangeRepository extends JpaRepository<CategoryChange, UUID> {

    List<CategoryChange> findByFileIdOrderByChangedAt(UUID fileId);
}
