package br.com.condominioauditoria.backend.auditoria;

import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Grava achados pela chave única (condomínio, regra, competência, alvo): registrar de novo não duplica. */
@Service
public class RegistroAchados {

    /** Evidência de um achado; ordem = posição na lista. */
    public record Evidencia(UUID arquivoId, String sha256, Integer pagina, String referencia, UUID linhaPoId) {
    }

    private final AchadoRepository achados;
    private final AchadoEvidenciaRepository evidencias;

    public RegistroAchados(AchadoRepository achados, AchadoEvidenciaRepository evidencias) {
        this.achados = achados;
        this.evidencias = evidencias;
    }

    public Achado registrar(UUID condominioId, String regra, String versaoRegra, Severidade severidade,
            YearMonth competencia, String alvo, String descricao, List<Evidencia> provas) {
        return achados.findByCondominioIdAndRegraAndCompetenciaAndAlvo(condominioId, regra, competencia.atDay(1), alvo)
                .orElseGet(() -> {
                    Achado novo = achados.save(new Achado(condominioId, regra, versaoRegra, severidade, competencia,
                            alvo, descricao, Instant.now()));
                    for (int i = 0; i < provas.size(); i++) {
                        Evidencia e = provas.get(i);
                        evidencias.save(new AchadoEvidencia(novo.getId(), i + 1, e.arquivoId(), e.sha256(), e.pagina(),
                                e.referencia(), e.linhaPoId()));
                    }
                    return novo;
                });
    }
}
