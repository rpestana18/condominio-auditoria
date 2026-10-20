import type { FundMonthSummary, FundMovement } from "../../api/types";
import { formatMoney, formatMoneyOrDash } from "../../format";
import { monthStatusText } from "../budgetTracking/MonthStatusNote";

interface Props {
  summary: FundMonthSummary;
  funds: FundMovement[];
}

/** Top of the "Fundos" screen (RF-13.5): sum of the balances of every fund (from the API) and the balance of each one. */
export function FundTotals({ summary, funds }: Props) {
  const note = monthStatusText(summary);
  const present = funds
    .map((f) => ({ fund: f, movement: f.months.find((m) => m.month === summary.month) }))
    .filter((p) => p.movement?.present);
  return (
    <section className="block">
      <div className="cards">
        <div className="number-card">
          <span className="card-title">Soma dos saldos de todos os fundos</span>
          <strong>{formatMoneyOrDash(summary.totalClosingBalance)}</strong>
          <span className="muted">
            {summary.funds} fundo(s) no extrato{note ? ` · ${note}` : ""}
          </span>
        </div>
      </div>
      <ul className="fund-balances">
        {present.map(({ fund, movement }) => (
          <li key={fund.fundId}>
            <span>{fund.name}</span>
            <span className={`number ${movement!.closingBalance != null && movement!.closingBalance < 0 ? "negative" : ""}`}>
              {movement!.closingBalance == null ? "—" : formatMoney(movement!.closingBalance)}
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}
