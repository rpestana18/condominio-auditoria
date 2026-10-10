import { abrirArquivo } from "../../api/cliente";
import { useAchados } from "../../api/consultasOrcamento";
import type { Achado } from "../../api/tipos";
import { useSessao } from "../../contexto";
import { formatarDataHora, formatarMes } from "../../formato";
import { rotuloEstadoAchado, rotuloSeveridade } from "./rotulos";

const classeSeveridade: Record<Achado["severity"], string> = { INFO: "neutro", WARNING: "alerta", CRITICAL: "critico" };

/**
 * Achados do mês (só leitura nesta entrega). Cada um traz a evidência original, que abre o documento
 * na página, e o histórico de estados. Marcar justificado ou resolvido fica para a tela de achados.
 */
export function AchadosDoMes({ competencia }: { competencia: string }) {
  const { condominioId } = useSessao();
  const { data: achados = [] } = useAchados(condominioId, competencia);
  if (achados.length === 0) return null;

  return (
    <section className="bloco">
      <h2>Achados de {formatarMes(competencia)}</h2>
      <ul className="lista-achados">
        {achados.map((a) => (
          <li key={a.id}>
            <div className="evidencia-topo">
              <span>
                <span className={`selo ${classeSeveridade[a.severity]}`}>{rotuloSeveridade[a.severity]}</span>{" "}
                <span className="selo neutro">{rotuloEstadoAchado[a.status]}</span>
              </span>
              <span className="discreto">criado em {formatarDataHora(a.createdAt)}</span>
            </div>
            <span>{a.description}</span>
            {a.statusReason && <span className="discreto">{a.statusReason}</span>}
            <span className="discreto">
              Evidência:{" "}
              {a.evidence.map((e, i) => (
                <span key={e.position}>
                  {i > 0 && " · "}
                  <button
                    className="botao-link"
                    title={`SHA-256 ${e.sha256}`}
                    onClick={() =>
                      void abrirArquivo(`/condominiums/${condominioId}/files/${e.fileId}/content`, e.page ?? undefined)
                    }
                  >
                    {e.reference}
                    {e.page ? `, pág. ${e.page}` : ""}
                  </button>
                </span>
              ))}
            </span>
            {a.history.length > 1 && (
              <details>
                <summary className="discreto">Histórico ({a.history.length})</summary>
                <ul className="lista-simples">
                  {a.history.map((h, i) => (
                    <li key={i}>
                      {formatarDataHora(h.occurredAt)} · {h.username}: {h.previousStatus ? `${rotuloEstadoAchado[h.previousStatus]} → ` : ""}
                      {rotuloEstadoAchado[h.newStatus]} ({h.reason})
                    </li>
                  ))}
                </ul>
              </details>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}
