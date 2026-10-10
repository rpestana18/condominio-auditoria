import { Link } from "react-router";
import type { BudgetVsActual } from "../../api/types";
import { useSession } from "../../context";
import { formatDateTime, formatMonth, shortHash } from "../../format";

/**
 * When the backend does not calculate (no budget, budget not confirmed, no operating fund, no cash flow or two cash flows),
 * the screen shows why in place of the numbers. It never shows zero.
 */
export function NoNumbers({ result }: { result: BudgetVsActual }) {
  const { can } = useSession();
  const { status, budget } = result;
  const twoCashFlows = result.months.filter((m) => m.status === "TWO_CASH_FLOWS");

  return (
    <section className="block no-numbers">
      <h2>{titles[status]}</h2>
      {result.message && <p>{result.message}</p>}

      {status === "BUDGET_NOT_CONFIRMED" && budget && (
        <Link className={can("ADMIN") ? "button" : "link-button"} to={`/previsoes/${budget.id}`}>
          {can("ADMIN") ? "Conferir e confirmar a PO" : "Ver a PO lida"}
        </Link>
      )}
      {status === "NO_BUDGET" && (
        <Link className="link-button" to="/previsoes">
          Ver as POs enviadas
        </Link>
      )}
      {status === "NO_OPERATING_FUND" && can("MANAGER", "ADMIN") && (
        <Link className="link-button" to="/">
          Confirmar o fundo ordinário na tela inicial
        </Link>
      )}

      {twoCashFlows.map((m) => (
        <div key={m.month}>
          <h3>Dois fluxos para {formatMonth(m.month)}</h3>
          <ul className="simple-list">
            {m.cashFlows.map((f) => (
              <li key={f.fileId}>
                <strong>{f.name}</strong>{" "}
                <span className="muted">
                  {f.uploadedAt && `enviado em ${formatDateTime(f.uploadedAt)}`} {f.uploadedBy && `por ${f.uploadedBy}`} ·{" "}
                  <span title={f.sha256}>SHA-256 {shortHash(f.sha256)}…</span>
                </span>
              </li>
            ))}
          </ul>
          <Link className="link-button" to="/arquivos">
            Ir para Arquivos
          </Link>
        </div>
      ))}
    </section>
  );
}

const titles: Record<BudgetVsActual["status"], string> = {
  CALCULATED: "",
  NO_BUDGET: "Sem PO aprovada para este período",
  BUDGET_NOT_CONFIRMED: "PO não confirmada",
  NO_OPERATING_FUND: "Fundo ordinário ainda não confirmado",
  NO_CASH_FLOW: "Sem fluxo carregado",
  TWO_CASH_FLOWS: "Dois fluxos para o mesmo mês",
};
