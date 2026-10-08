package br.com.condominioauditoria.api.orcamento;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RealocacaoLancamentoRepository extends JpaRepository<RealocacaoLancamento, UUID> {

    List<RealocacaoLancamento> findByPrevisaoIdOrderByDataAscRealocadaEmAsc(UUID previsaoId);

    List<RealocacaoLancamento> findByPrevisaoIdAndDesfeitaEmIsNull(UUID previsaoId);

    Optional<RealocacaoLancamento> findByPrevisaoIdAndChaveLancamentoAndDesfeitaEmIsNull(UUID previsaoId,
            String chaveLancamento);

    Optional<RealocacaoLancamento> findByIdAndCondominioId(UUID id, UUID condominioId);
}
