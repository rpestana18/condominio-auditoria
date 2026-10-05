package br.com.condominioauditoria.backend.auditoria;

import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Achados com a evidência original e o histórico (RF-03.1.12): achado nunca some, só muda de estado. */
@Service
public class ConsultaAchados {

    public record EvidenciaDto(int ordem, UUID arquivoId, String sha256, Integer pagina, String referencia,
            UUID linhaPoId) {
    }

    public record EventoDto(EstadoAchado estadoAnterior, EstadoAchado estadoNovo, boolean condicaoPresente,
            String motivo, String usuario, Instant em) {
    }

    public record AchadoDetalhe(UUID id, String regra, String versaoRegra, Severidade severidade, String competencia,
            String alvo, String descricao, EstadoAchado estado, String estadoMotivo, Instant estadoEm,
            boolean condicaoPresente, Instant criadoEm, List<EvidenciaDto> evidencias, List<EventoDto> historico) {
    }

    private final AchadoRepository achados;
    private final AchadoEvidenciaRepository evidencias;
    private final EventoAchadoRepository eventos;

    ConsultaAchados(AchadoRepository achados, AchadoEvidenciaRepository evidencias, EventoAchadoRepository eventos) {
        this.achados = achados;
        this.evidencias = evidencias;
        this.eventos = eventos;
    }

    /** {@code competencia}: AAAA-MM, ou nulo para todos os meses. */
    @Transactional(readOnly = true)
    public List<AchadoDetalhe> listar(UUID condominioId, String competencia) {
        List<Achado> lista;
        if (competencia == null || competencia.isBlank()) {
            lista = achados.findByCondominioIdOrderByCompetenciaDescCriadoEmAsc(condominioId);
        } else {
            YearMonth mes;
            try {
                mes = YearMonth.parse(competencia.trim());
            } catch (DateTimeParseException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Competência deve ser AAAA-MM: " + competencia);
            }
            lista = achados.findByCondominioIdAndCompetenciaOrderByCriadoEm(condominioId, mes.atDay(1));
        }
        if (lista.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = lista.stream().map(Achado::getId).toList();
        Map<UUID, List<EvidenciaDto>> provas = evidencias.findByAchadoIdInOrderByOrdemAsc(ids).stream()
                .collect(Collectors.groupingBy(AchadoEvidencia::getAchadoId, Collectors.mapping(e -> new EvidenciaDto(
                        e.getOrdem(), e.getArquivoId(), e.getSha256(), e.getPagina(), e.getReferencia(),
                        e.getLinhaPoId()), Collectors.toList())));
        Map<UUID, List<EventoDto>> historico = eventos.findByAchadoIdInOrderByEmAsc(ids).stream()
                .collect(Collectors.groupingBy(EventoAchado::getAchadoId, Collectors.mapping(e -> new EventoDto(
                        e.getEstadoAnterior(), e.getEstadoNovo(), e.isCondicaoPresente(), e.getMotivo(),
                        e.getUsuario(), e.getEm()), Collectors.toList())));
        return lista.stream().map(a -> new AchadoDetalhe(a.getId(), a.getRegra(), a.getVersaoRegra(),
                a.getSeveridade(), a.getCompetencia().toString(), a.getAlvo(), a.getDescricao(), a.getEstado(),
                a.getEstadoMotivo(), a.getEstadoEm(), a.isCondicaoPresente(), a.getCriadoEm(),
                provas.getOrDefault(a.getId(), List.of()), historico.getOrDefault(a.getId(), List.of()))).toList();
    }
}
