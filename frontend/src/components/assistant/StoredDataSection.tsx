import { Link } from "react-router";
import type { StoredData } from "../../api/types";

/** Readable name of each numeric query (the tools that rag calls on the backend). */
const queryNames: Record<string, string> = {
  fund_summary: "Resumo dos fundos",
  find_entries: "Lançamentos",
  list_files: "Arquivos enviados",
  file_checks: "Conferências do arquivo",
};

/** Readable name of each query filter (rag sends the contract names, in English). */
const parameterNames: Record<string, string> = {
  dateFrom: "data inicial",
  dateTo: "data final",
  fund: "fundo",
  text: "texto",
  outflowsOnly: "só saídas",
  limit: "limite",
  category: "categoria",
  fileId: "arquivo",
};

const parameterValue = (data: StoredData, ...names: string[]) =>
  data.parameters.find((p) => names.includes(p.name))?.value;

/**
 * Screen where the person can check the number (RF-04.14). Only routes that exist today; no screen, no link.
 * Entries do not have their own screen yet: only the query name shows up.
 */
function queryLink(data: StoredData): { to: string; label: string } | null {
  switch (data.query) {
    case "fund_summary":
      return { to: "/", label: "Ver saldo por fundo no Início" };
    case "list_files":
      return { to: "/arquivos", label: "Ver em Arquivos" };
    case "file_checks": {
      const fileId = parameterValue(data, "fileId");
      return { to: fileId ? `/arquivos?arquivo=${encodeURIComponent(fileId)}` : "/arquivos", label: "Ver conferências do arquivo" };
    }
    default:
      return null;
  }
}

/** "Nos dados gravados" block: numbers from the database queries, already formatted by the API (nothing is calculated here). */
export function StoredDataSection({ items }: { items: StoredData[] }) {
  return (
    <section className="answer-block" aria-label="Nos dados gravados">
      <h3>Nos dados gravados</h3>
      {items.map((data, i) => {
        const link = queryLink(data);
        return (
          <div key={i} className="stored-data">
            <p className="muted">
              Consulta: <strong>{queryNames[data.query] ?? data.query}</strong>
              {data.parameters.length > 0 && ` (${data.parameters.map((p) => `${parameterNames[p.name] ?? p.name}: ${p.value}`).join("; ")})`}
            </p>
            {data.rows.length > 0 && (
              <table className="table compact">
                <tbody>
                  {data.rows.map((r, j) => (
                    <tr key={j}>
                      <td>{r.label}</td>
                      <td className="number">{r.value}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            {data.comment && <p>{data.comment}</p>}
            {link && (
              <Link to={link.to} className="link-button">
                {link.label}
              </Link>
            )}
          </div>
        );
      })}
    </section>
  );
}
