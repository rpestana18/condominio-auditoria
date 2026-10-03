package br.com.condominioauditoria.backend.condominio;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CondominioRepository extends JpaRepository<Condominio, UUID> {
}
