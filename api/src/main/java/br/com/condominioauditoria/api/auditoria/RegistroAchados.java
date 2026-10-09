package br.com.condominioauditoria.api.auditoria;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Grava achados pela chave única (condomínio, regra, competência, alvo): registrar de novo não duplica. Achado nunca
 * é apagado; o recálculo só muda o estado do sistema (aberto, não se aplica mais) e grava o evento no histórico.
 */
@Service
public class RegistroAchados {

    /** Evidência de um achado; ordem = posição na lista. */
    public record Evidencia(UUID arquivoId, String sha256, Integer pagina, String referencia, UUID linhaPoId) {
    }

    /** Achado apurado por uma regra num recálculo (ainda não gravado). */
    public record Apurado(String regra, String versaoRegra, Severidade severidade, String alvo, String descricao,
            List<Evidencia> evidencias) {

        public Apurado {
            Objects.requireNonNull(regra, "regra");
            Objects.requireNonNull(alvo, "alvo");
            evidencias = evidencias == null ? List.of() : List.copyOf(evidencias);
        }
    }

    /** O que um recálculo mudou (para log e teste). */
    public record Sincronizacao(int abertos, int reabertos, int encerrados, int mantidos) {
    }

    private final AchadoRepository achados;
    private final AchadoEvidenciaRepository evidencias;
    private final EventoAchadoRepository eventos;

    public RegistroAchados(AchadoRepository achados, AchadoEvidenciaRepository evidencias,
            EventoAchadoRepository eventos) {
        this.achados = achados;
        this.evidencias = evidencias;
        this.eventos = eventos;
    }

    public Achado registrar(UUID condominioId, String regra, String versaoRegra, Severidade severidade,
            YearMonth competencia, String alvo, String descricao, List<Evidencia> provas) {
        return achados.findByCondominioIdAndRegraAndCompetenciaAndAlvo(condominioId, regra, competencia.atDay(1), alvo)
                .orElseGet(() -> criar(condominioId, regra, versaoRegra, severidade, competencia, alvo, descricao,
                        provas, "achado aberto pela regra " + regra + " (versão " + versaoRegra + ")", "sistema",
                        Instant.now()));
    }

    /**
     * Aplica o resultado do recálculo de um mês para as {@code regras} avaliadas (RF-03.1.12, Q27):
     * <ul>
     * <li>apurado sem achado: abre um novo, com a evidência;</li>
     * <li>apurado com achado cuja condição tinha deixado de existir: o <b>mesmo</b> achado volta a "aberto";</li>
     * <li>achado da regra não apurado agora: a condição deixou de existir; se estava aberto, vira "não se aplica
     * mais", com o motivo;</li>
     * <li>achado marcado por pessoa (justificado, resolvido, falso positivo) mantém o estado; a mudança da condição
     * só entra no histórico.</li>
     * </ul>
     * Rodar duas vezes com o mesmo insumo não muda nada na segunda: existe um achado só por regra, mês e alvo.
     */
    public Sincronizacao sincronizar(UUID condominioId, YearMonth competencia, Collection<String> regras,
            List<Apurado> apurados, GatilhoRecalculo gatilho) {
        Map<String, Achado> existentes = new HashMap<>();
        achados.findByCondominioIdAndCompetenciaAndRegraIn(condominioId, competencia.atDay(1), List.copyOf(regras))
                .forEach(a -> existentes.put(chave(a.getRegra(), a.getAlvo()), a));
        int abertos = 0;
        int reabertos = 0;
        int encerrados = 0;
        int mantidos = 0;
        java.util.Set<String> presentes = new java.util.HashSet<>();
        for (Apurado ap : apurados) {
            if (!regras.contains(ap.regra())) {
                throw new IllegalArgumentException("Regra " + ap.regra() + " fora das regras recalculadas");
            }
            String k = chave(ap.regra(), ap.alvo());
            if (!presentes.add(k)) {
                continue;
            }
            Achado a = existentes.get(k);
            if (a == null) {
                criar(condominioId, ap.regra(), ap.versaoRegra(), ap.severidade(), competencia, ap.alvo(),
                        ap.descricao(), ap.evidencias(), gatilho.texto(), gatilho.usuario(), gatilho.em());
                abertos++;
                continue;
            }
            EstadoAchado antes = a.getEstado();
            if (a.condicaoVoltou("a condição voltou: " + gatilho.texto(), gatilho.em())) {
                achados.save(a);
                eventos.save(new EventoAchado(a, antes, "a condição voltou: " + gatilho.texto(), gatilho.usuario(),
                        gatilho.em()));
                reabertos++;
            } else {
                mantidos++;
            }
        }
        for (var e : existentes.entrySet()) {
            if (presentes.contains(e.getKey())) {
                continue;
            }
            Achado a = e.getValue();
            EstadoAchado antes = a.getEstado();
            if (a.condicaoDeixouDeExistir(gatilho.texto(), gatilho.em())) {
                achados.save(a);
                eventos.save(new EventoAchado(a, antes, a.getEstado().doSistema() ? gatilho.texto()
                        : "a condição deixou de existir (estado marcado por pessoa mantido): " + gatilho.texto(),
                        gatilho.usuario(), gatilho.em()));
                encerrados++;
            }
        }
        return new Sincronizacao(abertos, reabertos, encerrados, mantidos);
    }

    private Achado criar(UUID condominioId, String regra, String versaoRegra, Severidade severidade,
            YearMonth competencia, String alvo, String descricao, List<Evidencia> provas, String motivo,
            String usuario, Instant em) {
        Achado novo = achados.save(new Achado(condominioId, regra, versaoRegra, severidade, competencia, alvo,
                descricao, em));
        for (int i = 0; i < provas.size(); i++) {
            Evidencia e = provas.get(i);
            evidencias.save(new AchadoEvidencia(novo.getId(), i + 1, e.arquivoId(), e.sha256(), e.pagina(),
                    e.referencia(), e.linhaPoId()));
        }
        eventos.save(new EventoAchado(novo, null, motivo, usuario, em));
        return novo;
    }

    private static String chave(String regra, String alvo) {
        return regra + "\u0000" + alvo;
    }
}
