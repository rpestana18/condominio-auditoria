package br.com.condominioauditoria.api.auditoria;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ParametroRegraRepository extends JpaRepository<ParametroRegra, UUID> {

    @Query("""
            select p from ParametroRegra p
            where p.condominioId = :condominioId and p.codigo = :codigo
              and p.vigenteDesde <= :data and (p.vigenteAte is null or p.vigenteAte >= :data)
            order by p.vigenteDesde desc""")
    List<ParametroRegra> vigentes(UUID condominioId, String codigo, LocalDate data);

    /** Valor que vale na data; o de início mais recente, se houver sobreposição. */
    default Optional<ParametroRegra> vigente(UUID condominioId, String codigo, LocalDate data) {
        return vigentes(condominioId, codigo, data).stream().findFirst();
    }
}
