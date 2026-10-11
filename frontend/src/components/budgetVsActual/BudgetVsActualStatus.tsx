import { Link } from "react-router";
import type { BudgetVsActual } from "../../api/types";
import { useSession } from "../../context";
import { budgetStatusLabel } from "./labels";

/**
 * Status of the budget and of the account mapping (RF-03.1.13). The "N contas sem linha da PO" warning shows for everyone;
 * for the Admin it leads to the account mapping screen already filtered on "pendentes".
 */
export function BudgetVsActualStatus({ result }: { result: BudgetVsActual }) {
  const { can } = useSession();
  const { budget, mapping } = result;
  const pending = mapping?.withoutConfirmedMapping ?? 0;
  const pendingLink = budget ? `/depara?po=${budget.id}&filtro=PENDING` : "/depara?filtro=PENDING";

  return (
    <div className="budget-vs-actual-status">
      {budget && (
        <Link to={`/previsoes/${budget.id}`} className={budget.status === "CONFIRMED" ? "badge ok" : "badge warning"}>
          PO {budget.version ? `versão ${budget.version}` : ""} · {budgetStatusLabel[budget.status]}
        </Link>
      )}
      {mapping && (
        <span className={pending === 0 ? "badge ok" : "badge warning"}>
          De-para: {mapping.confirmed} de {mapping.accounts} contas confirmadas
        </span>
      )}
      {result.provisional && (
        <span className="badge warning" title="Há valor a realocar ou conta sem linha da PO">
          Provisório
        </span>
      )}
      {pending > 0 &&
        (can("ADMIN") ? (
          <Link className="notice warning" to={pendingLink}>
            {pending} {pending === 1 ? "conta" : "contas"} sem linha da PO · revisar o de-para
          </Link>
        ) : (
          <span className="notice warning">
            {pending} {pending === 1 ? "conta" : "contas"} sem linha da PO
          </span>
        ))}
    </div>
  );
}
