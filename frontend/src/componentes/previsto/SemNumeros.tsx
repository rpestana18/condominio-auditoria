import { Link } from "react-router";
import type { PrevistoRealizado } from "../../api/tipos";
import { useSessao } from "../../contexto";
import { formatarDataHora, formatarMes, hashCurto } from "../../formato";

/**
 * Quando o backend não calcula (sem PO, PO não confirmada, sem fundo ordinário, sem fluxo ou dois fluxos),
 * a tela mostra o porquê no lugar dos números. Nunca mostra zero.
 */
export function SemNumeros({ resultado }: { resultado: PrevistoRealizado }) {
  const { pode } = useSessao();
  const { status: situacao, budget: po } = resultado;
  const doisFluxos = resultado.months.filter((m) => m.status === "TWO_CASH_FLOWS");

  return (
    <section className="bloco sem-numeros">
      <h2>{titulos[situacao]}</h2>
      {resultado.message && <p>{resultado.message}</p>}

      {situacao === "BUDGET_NOT_CONFIRMED" && po && (
        <Link className={pode("ADMIN") ? "botao" : "botao-link"} to={`/previsoes/${po.id}`}>
          {pode("ADMIN") ? "Conferir e confirmar a PO" : "Ver a PO lida"}
        </Link>
      )}
      {situacao === "NO_BUDGET" && (
        <Link className="botao-link" to="/previsoes">
          Ver as POs enviadas
        </Link>
      )}
      {situacao === "NO_OPERATING_FUND" && pode("MANAGER", "ADMIN") && (
        <Link className="botao-link" to="/">
          Confirmar o fundo ordinário na tela inicial
        </Link>
      )}

      {doisFluxos.map((m) => (
        <div key={m.month}>
          <h3>Dois fluxos para {formatarMes(m.month)}</h3>
          <ul className="lista-simples">
            {m.cashFlows.map((f) => (
              <li key={f.fileId}>
                <strong>{f.name}</strong>{" "}
                <span className="discreto">
                  {f.uploadedAt && `enviado em ${formatarDataHora(f.uploadedAt)}`} {f.uploadedBy && `por ${f.uploadedBy}`} ·{" "}
                  <span title={f.sha256}>SHA-256 {hashCurto(f.sha256)}…</span>
                </span>
              </li>
            ))}
          </ul>
          <Link className="botao-link" to="/arquivos">
            Ir para Arquivos
          </Link>
        </div>
      ))}
    </section>
  );
}

const titulos: Record<PrevistoRealizado["status"], string> = {
  CALCULATED: "",
  NO_BUDGET: "Sem PO aprovada para este período",
  BUDGET_NOT_CONFIRMED: "PO não confirmada",
  NO_OPERATING_FUND: "Fundo ordinário ainda não confirmado",
  NO_CASH_FLOW: "Sem fluxo carregado",
  TWO_CASH_FLOWS: "Dois fluxos para o mesmo mês",
};
