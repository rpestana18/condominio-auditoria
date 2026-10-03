package br.com.condominioauditoria.backend.arquivo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArquivoRepository extends JpaRepository<Arquivo, UUID> {

    List<Arquivo> findByCondominioIdOrderByEnviadoEmDesc(UUID condominioId);

    List<Arquivo> findByCondominioIdAndCategoriaOrderByEnviadoEmDesc(UUID condominioId, Categoria categoria);

    Optional<Arquivo> findFirstByCondominioIdOrderByEnviadoEmDesc(UUID condominioId);

    Optional<Arquivo> findByCondominioIdAndSha256(UUID condominioId, String sha256);

    Optional<Arquivo> findByIdAndCondominioId(UUID id, UUID condominioId);

    List<Arquivo> findByStatusInOrderByEnviadoEm(Collection<StatusArquivo> status);

    /** Parados na fila há mais tempo que o limite (mensagem perdida, serviço fora do ar). */
    List<Arquivo> findByStatusInAndEnfileiradoEmBeforeOrderByEnviadoEm(Collection<StatusArquivo> status, java.time.Instant limite);

    /** Último fluxo de caixa lido (com ou sem pendência de revisão), pelo período mais recente. */
    Optional<Arquivo> findFirstByCondominioIdAndInterpretadorAndStatusInOrderByPeriodoFimDescEnviadoEmDesc(
            UUID condominioId, String interpretador, Collection<StatusArquivo> status);
}
