package br.com.condominioauditoria.api.arquivo;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoricoCategoriaRepository extends JpaRepository<HistoricoCategoria, UUID> {

    List<HistoricoCategoria> findByArquivoIdOrderByAlteradoEm(UUID arquivoId);
}
