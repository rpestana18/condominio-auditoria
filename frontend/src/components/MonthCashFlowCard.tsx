import { Link } from "react-router";
import { useCashFlowMonthly } from "../api/cashFlowQueries";
import type { Dashboard } from "../api/types";
import { useSession } from "../context";
import { formatDifference, formatMoneyOrDash, formatMonth } from "../format";
import { cashFlowMonthNote } from "./cashFlow/CashFlowTable";

/**
 * Home screen (revised RF-13.7, N2): the inflows and outflows of the month of the operating fund and, side by side, the
 * month balance and the accumulated balance. Same numbers as "Entradas e saídas" (same API call).
 */
export function MonthCashFlowCard({ dashboard }: { dashboard: Dashboard }) {
  const { condominiumId } = useSession();
  const { data } = useCashFlowMonthly(condominiumId, null);
  if (!dashboard.operatingFund?.confirmed || !data) return null;
  const reportMonth = dashboard.periodStart?.slice(0, 7);
  const withNumbers = data.months.filter((m) => m.status === "WITH_CASH_FLOW");
  const month = withNumbers.find((m) => m.month === reportMonth) ?? withNumbers.at(-1);
  if (!month) return null;
  const note = cashFlowMonthNote(month);
  return (
    <section className="number-card wide-card">
      <Link to="/entradas-e-saidas" className="card-title card-title-link" title="Abrir Entradas e saídas">
        Entradas e saídas · {formatMonth(month.month)}
        {note ? ` · ${note}` : ""}
      </Link>
      <ul className="bridge-card-list">
        <li>
          <span>Entradas</span>
          <span className="number">{formatMoneyOrDash(month.inflows)}</span>
        </li>
        <li>
          <span>Saídas</span>
          <span className="number">{formatMoneyOrDash(month.outflows)}</span>
        </li>
      </ul>
      <ul className="bridge-card-list">
        <li>
          <span>Saldo do mês</span>
          <span className="number">{month.monthBalance == null ? "—" : formatDifference(month.monthBalance)}</span>
        </li>
        <li>
          <span>Saldo acumulado</span>
          <span className="number">{formatMoneyOrDash(month.closingBalance)}</span>
        </li>
      </ul>
      <Link to="/entradas-e-saidas">Ver entradas e saídas</Link>
    </section>
  );
}
