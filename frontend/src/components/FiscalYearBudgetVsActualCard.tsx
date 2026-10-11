import { Link } from "react-router";
import { useBudgetVsActual } from "../api/budgetQueries";
import { useSession } from "../context";
import { formatMonth, formatMoney, formatPercentage } from "../format";

/**
 * "Previsto × realizado no exercício" card on the home screen (RF-03.1.13 and RF-05.1).
 * Uses the cumulative figures of the budget's fiscal year, calculated by the backend, and links to the full screen.
 */
export function FiscalYearBudgetVsActualCard() {
  const { condominiumId } = useSession();
  const { data: result } = useBudgetVsActual(condominiumId, "cumulative");
  if (!result) return null;

  const totals = result.status === "CALCULATED" ? result.totals : null;
  return (
    <Link to="/previsto-realizado?periodo=acumulado" className="number-card card-link" title="Abrir o previsto × realizado">
      <span className="card-title">Previsto × realizado no exercício</span>
      {totals ? (
        <>
          <strong>{formatPercentage(totals.execution)}</strong>
          <span className="muted">
            {formatMoney(totals.actualExpense)} de {formatMoney(totals.planned)}
          </span>
          <span className="muted">
            {result.summedMonths.length} {result.summedMonths.length === 1 ? "mês" : "meses"} com fluxo
            {result.monthsWithoutCashFlow.length > 0 && ` · faltam ${result.monthsWithoutCashFlow.map(formatMonth).join(", ")}`}
            {result.provisional && " · provisório"}
          </span>
        </>
      ) : (
        <span className="muted">{result.message ?? "Sem números para o exercício"}</span>
      )}
    </Link>
  );
}
