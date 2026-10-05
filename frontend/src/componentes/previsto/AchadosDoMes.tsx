import { abrirArquivo } from "../../api/cliente";
import { useAchados } from "../../api/consultasOrcamento";
import type { Achado } from "../../api/tipos";
import { useSessao } from "../../contexto";
import { formatarDataHora, formatarMes } from "../../formato";
import { rotuloEstadoAchado, rotuloSeveridade } from "./rotulos";

const classeSeveridade: Record<Achado["severidade"], string> = { INFORMATIVO: "neutro", ATENCAO: "alerta", CRITICO: "critico" };

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
                <span className={`selo ${classeSeveridade[a.severidade]}`}>{rotuloSeveridade[a.severidade]}</span>{" "}
                <span className="selo neutro">{rotuloEstadoAchado[a.estado]}</span>
              </span>
              <span className="discreto">criado em {formatarDataHora(a.criadoEm)}</span>
            </div>
            <span>{a.descricao}</span>
            {a.estadoMotivo && <span className="discreto">{a.estadoMotivo}</span>}
            <span className="discreto">
              Evidência:{" "}
              {a.evidencias.map((e, i) => (
                <span key={e.ordem}>
                  {i > 0 && " · "}
                  <button
                    className="botao-link"
                    title={`SHA-256 ${e.sha256}`}
                    onClick={() =>
                      void abrirArquivo(`/condominios/${condominioId}/arquivos/${e.arquivoId}/conteudo`, e.pagina ?? undefined)
                    }
                  >
                    {e.referencia}
                    {e.pagina ? `, pág. ${e.pagina}` : ""}
                  </button>
                </span>
              ))}
            </span>
            {a.historico.length > 1 && (
              <details>
                <summary className="discreto">Histórico ({a.historico.length})</summary>
                <ul className="lista-simples">
                  {a.historico.map((h, i) => (
                    <li key={i}>
                      {formatarDataHora(h.em)} · {h.usuario}: {h.estadoAnterior ? `${rotuloEstadoAchado[h.estadoAnterior]} → ` : ""}
                      {rotuloEstadoAchado[h.estadoNovo]} ({h.motivo})
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
